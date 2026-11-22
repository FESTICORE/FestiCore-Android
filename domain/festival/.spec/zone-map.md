---
spec: zone-map
modules: [domain/festival, service/api, service/festivalcatalog, service/pack]
---

# 존 지도 데이터 Spec

> 받아 둔 팩에서 존 지도(존 영역 · 무대 머리글 · 지금 공연)를 만들고, 서버 지도 버전과 비교하고, 팩을 한 번 다시 맞추고, 보드가 마지막으로 받은 라이브 상태를 돌려준다.

iOS 대응: `Projects/Domain/FestivalDomain/Interface/Sources/{Entity/FestivalZoneMap,ZoneMap/*}.swift`, `Sources/{ZoneMap/FestivalZoneMapUseCaseImpl,Repository/FestivalZoneMapRepositoryImpl,Model/DTO/FestivalZoneMapDTO,Model/Mapper/FestivalZoneMapDTO+}.swift`, 팩 업데이트 `Sources/PackDetail/FestivalPackDetailUseCaseImpl.swift:35-57`, 요청 `Projects/Service/APIEndpoint/Sources/Festival/FestivalPackRequest.swift`.
설계: `.claude/docs/specs/2026-11-22-live-extras-android-design.md`(화면 1 데이터 · 결정 E3 · E4 · E5 · PR Z1).

## 개요

화면은 `FestivalZoneMapUseCase`(cachedMap · isMapChanged), `FestivalPackUseCase.syncPack`, `FestivalLiveBoardUseCase.cachedLiveBoard`만 본다. 지도 값은 iOS처럼 지도 전용 DTO로 팩을 다시 해석하지 않고, 팩을 한 번 해석한 `FestivalPackDocumentDto`에 지도 필드를 더해 읽는다(E4).

## 동작 규칙

1. `cachedMap(id, now)`: 기기에 저장한 팩(`PackSyncService.cachedPack`)이 없으면 null. 팩 문서를 해석하지 못했으면 `FestivalPackError.InvalidPack`. 네트워크를 쓰지 않는다.
2. 지도 필드(팩 문서): `mapImageUrl` · `mapAspect` · `mapVersion`과 존 `kind` · `order` · `area{x,y,w,h}` · `landmark`는 형식이 틀리면 null. `mapStages[]{stageId,x,y,w}`는 손상 원소만 버린다. 존 원소 자체(`id` · `name`)가 틀리면 그 존만 버린다.
3. 매핑: 존은 `order` 오름차순(없으면 맨 뒤, 같으면 원래 순서). `mapAspect ≤ 0`이면 비율 없음. `flagsEnabled`가 없으면 참. 무대 머리글은 팩 `stages`에 이름이 있는 무대만(같은 ID는 첫 정의), 지금 공연 = 그 무대에서 `startsAt ≤ now < endsAt`인 첫 공연의 `artist`(ISO-8601, 해석 못 하면 건너뜀).
4. `hasLayout` = 비율 있음 ∧ 영역 있는 존 ≥ 1. 아니면 화면은 존 목록이다.
5. `isMapChanged(map)` = `GET api/v1/festivals/{id}/pack/version`의 `data.mapVersion != map.version`. 인증 없음. 응답에 `message`가 없어 공통 봉투 대신 `code` · `data`만 읽는다. `code != "SUCCESS"` · 해석 실패는 `InvalidResponse`, 그 밖은 `Unavailable`(`FestivalLiveBoardError`).
6. `syncPack(id)`: `PackSyncService.sync`로 한 번 맞추고 받은 팩을 공유 팩 상태에 반영한다. `Updated` · `NotModified`면 목록 전체를 새로 받는다(실패 무시). 결과는 그대로 돌려주고 `RetryAfter` · `Reconnecting`은 호출한 쪽이 실패로 본다. 오류는 `FestivalPackError`.
7. `cachedLiveBoard(id)`(E3): `FestivalLiveBoardService`가 마지막으로 성공한 `refresh` 결과(라이브 DTO · 깃발 프로필 · 수신 시각, `refresh`가 돌려준 값 그대로)를 세션별로 들고 있다가, 같은 `toDomain` 매핑으로 돌려준다. 받은 적 없음 · 현재 세션이 받은 세션과 다름 · 그 뒤 미공개 응답이면 null. 비로그인 조회 결과도 남긴다. 네트워크를 쓰지 않는다.

## 엣지 케이스

- 팩 문서의 `sets` · `stages` 원소가 틀리면 문서 전체가 실패해 지도도 실패(`InvalidPack`)다. iOS는 지도 DTO가 따로라 지도만은 읽힌다.
- 지도 값(`area` · `order`)이 틀린 존: Android는 존을 남기고 값만 null(보드 존 이름 찾기를 지키려고), iOS는 존을 버린다.
- 팩 문서 `id`가 요청 축제와 다르면 문서가 null이라 지도는 `InvalidPack`.

## 제약 / 가정

- 지도 버전 요청은 재시도하지 않는다(iOS `maxRetryAttempts 1`). Android 네트워크 계층에 재시도가 없다.
- WebSocket 팩 본문에 지도 필드가 있는지는 stage HTTP 상세로만 확인했다(설계 위험 1).

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `service/api` | service | `FestivalApiPath.packVersion`, `FestivalApi.packVersion`, `FestivalPackVersionDto`, 팩 문서 지도 필드 |
| `service/festivalcatalog` | service | `FestivalLiveBoardService.cachedFetch` |
| `domain/festival` | domain | `FestivalZoneMap` · `FestivalZoneMapRepository` · `FestivalZoneMapUseCase`, 팩 문서 지도 필드, `FestivalPackRepository.cachedPack` · `syncPack`, `FestivalPackUseCase.syncPack`, `cachedLiveBoard`, 매퍼 · Koin |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/festival` | feature | 존 지도 화면이 이 UseCase를 쓴다. 테스트 대역에 새 함수 추가 |

## 미해결 질문

(없음)

## 변경 이력

- 존 지도 데이터(PR Z1) 추가
