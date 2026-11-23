---
spec: catalog-cache
modules: [service/festivalcatalog]
---

# 축제 목록 파일 캐시 Spec

> 마지막으로 다 받은 축제 목록을 앱 전용 JSON 파일 하나에 통째로 저장하고, 첫 서버 갱신이 실패할 때 대체로 읽는다.

iOS 대응: `Projects/Service/FestiCoreFestivalCatalog/Sources/Storage/FestivalCatalogCache.swift`, `NetworkContainer.swift`(캐시 경로), `Feature/Festival/.../FestivalFeature.swift`(`cachedLoaded` · `catalogFailed`).
설계: `.claude/docs/specs/2026-11-21-festival-list-android-design.md`(캐시 · 오프라인 정책 · 결정 D6).

## 개요

`FestivalCatalogCache`가 `context.filesDir/festicore/festival-catalog.json`에 `List<FestivalItemDto>`를 JSON(`FestiCoreJson`)으로 쓴다. 쓰기는 `android.util.AtomicFile`로 한 번에 바꾼다. 쓰는 쪽은 `FestivalCatalogService`뿐이다(저장 시점은 `domain/festival/.spec/festival-catalog.md` 규칙 5).

## 동작 규칙

1. 읽기: 파일이 없으면 빈 목록. 읽기 IO 실패 · JSON 해석 실패는 `CacheUnreadable`(도메인 `FestivalCatalogError.CacheUnreadable`). 손상 파일은 지우지 않고, 다음 저장이 덮어쓴다.
2. 쓰기: 상위 폴더를 만들고 `startWrite` → `finishWrite`. 실패하면 `failWrite`로 이전 파일을 살리고 `CacheWriteFailed`(도메인 `Unavailable`)를 던진다. 저장 실패는 그 갱신 · 쪽 조회 전체를 실패로 만든다(iOS `try cache.save`와 같음).
3. 쓰기는 늘 통째로 바꾼다. 이어 쓰지 않는다.
4. 서버 실패는 저장된 목록을 지우지 않는다.
5. 오프라인 표시(F4): 화면은 첫 서버 갱신이 끝날 때까지 스켈레톤이고, 실패하면 저장 목록 + 배지, 저장 목록이 없으면 배지 + 빈 카드. 캐시를 첫 갱신 전에 즉시 보이지 않는다(iOS `showsListSkeleton`).
6. 파일 IO는 `Dispatchers.IO`에서 한다.
7. 쪽 조회 순서: 첫 쪽(`after == null`) 요청마다 세대를 올린다. 응답은 요청 때 세대가 지금과 같고 `after`가 이어 붙일 커서(직전에 모은 쪽의 `nextCursor`)일 때만 모으고, 마지막 쪽이면 잠근 채로 저장한다. 아니면(더 보기 중 첫 쪽을 다시 받았거나 같은 쪽을 이미 붙였다) 받은 쪽만 돌려주고 모으지도 저장하지도 않는다.

## 엣지 케이스

- 더 보기 요청이 진행 중일 때 첫 쪽 새로고침이 시작되면 늦게 온 옛 쪽은 새 첫 쪽 뒤에 붙지 않고, 오염된 목록을 저장하지 않는다(규칙 7).
- 쓰기 도중 앱이 죽으면 `AtomicFile` 백업으로 이전 목록을 읽는다.
- 캐시에 없는 필드(옛 앱 버전 파일)는 기본값(null · 빈 목록)으로 읽는다.

## 제약 / 가정

- DataStore · Room이 아닌 파일이다(결정 D6 (a)): 목록 문서 하나를 통째로 바꾸고 iOS도 파일이며 수십 KB다.
- 파일 IO는 `core/storage`로 모으지 않는다. `check-boundaries`는 SharedPreferences · DataStore · Keystore · Room만 막고, 이 캐시는 토큰 · 개인정보가 아닌 공개 목록이다.
- `androidx.core.util.AtomicFile` 대신 플랫폼 `android.util.AtomicFile`(API 17+, minSdk 24)을 써서 새 의존성을 두지 않는다. JVM 단위 테스트는 Robolectric으로 한다.

## 작업 모듈

| 모듈                    | 계층    | 변경 내용                                 |
| ----------------------- | ------- | ----------------------------------------- |
| service/festivalcatalog | service | `FestivalCatalogCache` · 저장 시점 · 오류 |

## 영향 범위

| 모듈                    | 계층    | 영향 이유                                                                    |
| ----------------------- | ------- | ---------------------------------------------------------------------------- |
| domain/festival/sources | sources | 캐시 오류를 `FestivalCatalogError`로 바꾼다                                  |
| app                     | app     | `serviceFestivalCatalogModule`이 `androidContext().filesDir`로 경로를 정한다 |

## 미해결 질문

(없음)

## 변경 이력

- F1 JSON 파일 캐시(AtomicFile) · 읽기 실패 처리
- 쪽 순서 세대로 늦은 옛 쪽 응답의 누적 · 저장을 막음(규칙 7)
