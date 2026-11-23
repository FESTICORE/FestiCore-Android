---
spec: pack-detail
modules: [domain/festival, core/storage, service/api, service/apiendpoint, app]
---

# 받은 팩 · 팩 상세 데이터 Spec

> 기기에 받은 팩 목록(새 버전 · 와이파이 자동 받기), 팩 상세(가는 길 · 준비물 · 반입 금지 · 출처), 변경 요약, 축제별 준비물 · 자동 받기 설정, 30일 지난 팩 삭제를 엔티티로 돌려준다.

iOS 대응: `Projects/Domain/FestivalDomain/{Interface,Sources}/{ReceivedPack,PackDetail,Pack}/**`, `Entity/FestivalReceivedPack` · `FestivalPackDetail` · `FestivalPackChanges` · `FestivalChecklist`, `Model/DTO/FestivalPackDetailDTO.swift` · `Mapper/FestivalPackDetailDTO+.swift` · `CachedFestivalPack+{ReceivedPack,Expiry}.swift`, 요청 `Service/APIEndpoint/Sources/Festival/FestivalPackRequest.swift`(`changes`), 만료 호출 `App/Sources/Navigation/Main/AppCoordinator.swift:198-249`.
설계: `.claude/docs/specs/2026-11-23-my-tab-android-design.md`("팩 상세 데이터 (D3)" · 쟁점 M8 · M9 · PR D3).

## 개요

화면(S1 마이 · S5 팩 상세)은 `FestivalReceivedPackUseCase` · `FestivalPackDetailUseCase`만 본다. 팩 읽기 · 받기 · 지우기는 기존 `FestivalPackRepository`를 거쳐 라이브 탭과 같은 `FestivalPackState`에 반영된다. 변경 요약은 `FestivalPackChangesRepository`(`FestivalRequest.packChanges`). 준비물 · 자동 받기는 `core/storage` `FestivalPackLocalStore`(DataStore). 와이파이 판단은 `FestivalWiFiStatus`(`ConnectivityManager`).

## 동작 규칙

1. 받은 팩 = 기기 팩 + 축제 목록 항목. 이름 · 썸네일 · 최신 버전은 목록 값을 먼저 쓰고, 없으면 팩 문서 이름(없으면 축제 ID) · 팩 썸네일. `hasUpdate = (latestPackVersion ?: packVersion) > packVersion`. 정렬은 확인 시각 최근 순, 같으면 ID 순. `sizeBytes`는 팩 JSON 바이트 수.
2. `receivedPacks`는 저장된 목록만 쓴다. 목록을 읽지 못하면 빈 목록으로 보고 팩은 보인다. `refreshReceivedPacks`는 서버 목록을 받는다(실패는 그대로 던짐).
3. 와이파이 자동 받기(M9): 새 버전 팩이 있고, 설정이 켜져 있고(처음 켜짐), 기본 네트워크가 요금이 들지 않는 와이파이면 팩마다 한 번 맞춘다. 그 사이 지운 팩은 건너뛴다. 실패는 삼키고, 다시 읽은 뒤 시도했는데 아직 새 버전이면 `autoUpdateFailed`. 취소는 그대로 올린다.
4. 팩 상세는 로컬 팩만 읽는다(없으면 null). 같은 팩 JSON을 상세 전용 DTO로 따로 읽고, 라이브 보드의 `FestivalPackDocument.Directions`는 바꾸지 않는다. 상세 해석이 실패하면 빈 상세.
5. 상세 매핑: 문자열은 앞뒤 공백을 지우고 비면 null. 링크 · 지도 주소는 https · 호스트 있음만. 섹션은 본문 · 링크 · 역 · 정류장이 모두 없으면 null. 좌표는 숫자 · 유한 · 위도 −90~90 · 경도 −180~180만. 준비물은 id · label이 있는 원소만(`required` 기본 false). 반입 금지는 null · 공백 원소를 뺀다. 출처는 이름이 있어야 하고 `verifiedAt`은 ISO가 아니면 null.
6. 업데이트: 팩을 한 번 맞춘다. `RetryAfter` · `Reconnecting` → `FestivalPackError.RetryLater`, 전송 실패 → `Offline`, 그 밖은 기존 팩 오류. 성공하면 서버 목록을 받고(실패하면 저장 목록) 상세를 다시 만든다.
7. 변경 요약: `GET api/v1/festivals/{id}/pack/changes?from={버전}`, 인증 없음, `code` · `data` 봉투. `lastDepartures`는 읽지 않는다.
8. 준비물은 축제별 `FestiCore.pack.checklist.<id>`에 iOS Codable과 같은 JSON(`checkedIDs` · `customItems[{id,label}]` · `hiddenIDs`)으로 저장한다. 없거나 읽지 못하면 빈 기록. 자동 받기 키는 `FestiCore.pack.autoDownloadOnWiFi`. 팩을 지워도 둘 다 남는다.
9. 만료(M8): 팩 문서의 모든 `days[].endsAt`을 읽을 수 있을 때 가장 늦은 종료 + 30일이 지나면 지운다. 하나라도 못 읽거나 일자가 없으면 남긴다. 한 팩 삭제 실패는 건너뛴다. 앱은 홈에서 포그라운드(`ON_START`)마다 부르고, 이전 정리가 돌고 있으면 취소한다.

## 엣지 케이스

- 팩 문서가 해석되지 않으면(배열 원소 손상 등) 만료 시각을 몰라 남기고, 이름은 목록 값 또는 축제 ID가 된다.
- 와이파이 상태를 읽을 수 없으면 와이파이가 아닌 것으로 본다.

## 제약 / 가정

- iOS는 `directions` 안 키 하나가 형식이 틀리면 가는 길 전체를 버린다. Android는 설계 표대로 키마다 null로 둔다(더 관대함).
- iOS `verifiedAt`은 소수 초 없는 ISO만 읽는다. Android는 `FestivalIsoTime`(소수 초 허용)을 쓴다.
- iOS의 비싼 · 제한된 연결 제외는 Android `NOT_METERED`로만 맞췄다(데이터 절약 모드는 보지 않는다).

## 작업 모듈

| 모듈                                  | 계층    | 변경 내용                                                                                                                                                                            |
| ------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `domain/festival`                     | domain  | 엔티티 4종 · `receivedpack/` · `packdetail/` 계약과 구현, 상세 · 변경 요약 · 준비물 DTO와 매퍼, `deleteExpiredPacks`, `FestivalPackError.RetryLater`, `CachedFestivalPack.sizeBytes` |
| `core/storage`                        | core    | `FestivalPackLocalStore`(DataStore `festicore_festival_pack`)                                                                                                                        |
| `service/api` · `service/apiendpoint` | service | `FestivalApi.packChanges` · `FestivalRequest.packChanges`                                                                                                                            |
| `app`                                 | app     | `ACCESS_NETWORK_STATE`, `AppCoordinator` 포그라운드 만료 정리                                                                                                                        |

## 영향 범위

| 모듈                    | 계층    | 영향 이유                                                      |
| ----------------------- | ------- | -------------------------------------------------------------- |
| `feature/profile`(예정) | feature | 마이 받은 팩 · 팩 상세 화면이 두 UseCase를 쓴다                |
| `feature/festival`      | feature | 받은 팩 갱신 · 삭제 · 만료가 같은 `FestivalPackState`를 바꾼다 |

## 미해결 질문

(없음)

## 변경 이력

- #167 받은 팩 · 팩 상세 · 변경 요약 · 준비물 · 자동 받기 · 만료 데이터를 옮겼다
