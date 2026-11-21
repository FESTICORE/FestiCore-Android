---
spec: festival-catalog
modules: [domain/festival, service/festivalcatalog, service/api]
---

# 축제 목록(카탈로그) Spec

> 라이브 탭 목록이 쓰는 축제 목록을 서버에서 쪽 단위 · 전체로 받고, 썸네일 주소를 정하고, 실패를 도메인 오류로 바꾼다.

iOS 대응: `Projects/Domain/FestivalDomain/Interface/Sources/{Catalog,Entity}/`, `Sources/Catalog/FestivalCatalogUseCaseImpl.swift`, `Sources/Model/Mapper/FestivalItemDTO+.swift`, `Projects/Service/FestiCoreFestivalCatalog/Sources/{NetworkFestivalCatalogService,URL/FestivalImageURLResolver,Endpoint/FestivalListRequest}.swift`.
설계: `.claude/docs/specs/2026-11-21-festival-list-android-design.md`(서버 계약 · 캐시 · 이미지 로딩 · PR F1). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

`FestivalCatalogUseCase`(domain/festival/interface)가 목록 화면의 유일한 입구다. 구현은 `FestivalCatalogRepositoryImpl` → `FestivalCatalogService`(service/festivalcatalog) → `FestivalApi`(service/api) 순서로 부른다. DTO는 domain/sources 밖으로 나가지 않는다.

## 동작 규칙

1. 요청: `GET api/v1/festivals?after={cursor}&limit={n}`, 인증 없음. 첫 쪽은 `after`를 보내지 않는다. 커서는 받은 문자열 그대로 보낸다(`~` 포함, 인코딩은 Ktor).
2. 쪽 조회 `festivalPage(after, limit)`: 첫 쪽 · 더 보기 크기는 `FestivalCatalogUseCase.PAGE_LIMIT`(10). 받은 쪽은 중복 제거 없이 그대로 돌려준다(화면이 첫 쪽이면 새로 시작, 다음 쪽은 이어 붙이고 같은 ID는 처음 것만 쓴다 — F4).
3. 쪽 조회에서 `after != null`인데 받은 `nextCursor == after`면 `RepeatedCursor`.
4. 전체 갱신 `refreshFestivals()`: 첫 쪽부터 limit 50(서버 상한)으로 `nextCursor == null`까지 반복한다. 같은 ID는 처음 것만 남긴다. 이미 받은 커서가 다시 오면 `RepeatedCursor`, 100쪽 안에 끝나지 않으면 `TooManyPages`.
5. 저장 시점(캐시 규칙은 `service/festivalcatalog/.spec/catalog-cache.md`): 전체 갱신이 끝났을 때, 또는 쪽 조회에서 마지막 쪽을 받았을 때 첫 쪽부터 모은 목록(중복 ID 제거). 첫 쪽만 받고 멈추면 저장하지 않는다. 첫 쪽(`after == null`)을 다시 받으면 모은 쪽을 버리고 새로 시작한다. 그보다 먼저 요청한 쪽의 늦은 응답은 모으지 않는다(`catalog-cache.md` 규칙 7).
6. DTO → 엔티티: 예매처는 이름을 trim해 비면 버리고, 스킴이 https가 아니거나 호스트가 없으면 버린다. `artists == null`(필드 없음)과 빈 목록을 구분해 둔다.
7. 썸네일 주소(`imageUrl`, 매핑 때 `thumbnailUrl`에 담음): 스킴이 있으면 https일 때만 그대로, 스킴이 없으면 `/`로 시작하고 `//`로 시작하지 않으며 `BASE_URL`이 https일 때만 `BASE_URL` 기준으로 합친다. 나머지(빈 값 · 잘못된 주소 포함)는 null.
8. 오류 매핑(`FestivalCatalogError`): `NetworkError.Offline` · `Timeout` → `Offline`, 커서 반복 → `RepeatedCursor`, 쪽 상한 → `TooManyPages`, 캐시 읽기 · 해석 실패 → `CacheUnreadable`, 그 외(`Http` · `InvalidResponse` · `Decoding` · `Unknown` · 캐시 쓰기 실패) → `Unavailable`. 취소는 그대로 던진다. 목록 화면은 종류를 구분하지 않는다(iOS `catalogFailed`).

## 엣지 케이스

- 응답 `items`의 원소 하나가 필수 필드(`id` · `name` · `packVersion`)를 빠뜨리면 그 쪽 전체가 `Decoding` → `Unavailable`(iOS와 같음).
- `ticketLinks` · `artists` · `performances`의 손상 원소 · `null` 원소는 건너뛴다. `ticketLinks`가 배열이 아니면 빈 목록.
- 서버가 주는 `flagsEnabled` · `artistNames`는 받지 않는다(iOS에도 없음).
- 잘못된 커서는 서버 400 `VALIDATION` → `Unavailable`.

## 제약 / 가정

- 의존 방향 때문에 DTO · 경로는 `service/api`에 둔다(설계 결정 D5).
- `FestivalCatalogUseCase`는 iOS처럼 함수 여러 개를 공개한다(설계 모듈 표). 선택 축제 저장 · 상세 조회는 라이브 보드 단계.
- `artists`가 배열이 아닌 값이면 iOS는 null, Android는 빈 목록이 된다(현재 서버는 항상 배열).

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| domain/festival/interface | interface | 엔티티 · `FestivalCatalogError` · `FestivalCatalogRepository` · `FestivalCatalogUseCase` |
| domain/festival/sources | sources | Repository · UseCase 구현, DTO 매핑, 오류 매핑, `domainFestivalModule` |
| service/api | service | `FestivalApiPath` · `FestivalApi` · 축제 DTO(손상 원소 건너뛰는 serializer) |
| service/festivalcatalog | service | `FestivalCatalogService` · `FestivalCatalogCache` · `FestivalImageUrlResolver` · `serviceFestivalCatalogModule` |
| app | app | Koin 목록에 두 모듈 추가 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| feature/festival(F4) | feature | 이 UseCase로 목록을 그린다 |

## 미해결 질문

(없음)

## 변경 이력

- F1 축제 목록 데이터 계층(쪽 · 전체 갱신 · 썸네일 주소 · 오류 매핑)
