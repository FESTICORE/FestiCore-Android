---
spec: pack-sync
modules: [service/pack, core/network, service/api, domain/festival]
---

# 축제 팩 동기화 · 저장 Spec

> 축제 팩을 WebSocket(`pack.get`)으로 받아 검증하고 축제별 JSON 파일에 저장하며, 변경 알림을 따라가고 끊기면 다시 연결한다. 받은 팩 · 받는 중 진행률 · 실패는 목록과 보드가 같은 `StateFlow`로 본다.

iOS 대응: `Projects/Service/FestiCorePack/{Interface/PackSyncService.swift,Interface/PackSyncError.swift,Sources/NetworkPackSyncService.swift,Sources/PackWire.swift,Sources/PackCache.swift,Sources/ReconnectBackoff.swift}`, `Tests/{PackSyncTests,SQLitePackCacheTests}.swift`, 경로 `Service/API/Sources/Festival/FestivalAPI.swift:13-23` · `Service/APIEndpoint/Sources/Festival/FestivalPackSocketRequest.swift`, 연결 `Core/FestiCoreNetwork/Sources/Client/NetworkClient.swift:177-194` · `Sources/WebSocket/*`, 도메인 `Domain/FestivalDomain/Interface/Sources/Pack/{FestivalPackSyncRepositoryInterface,FestivalPackUseCaseInterface}.swift`.
설계: `.claude/docs/specs/2026-11-22-live-board-android-design.md`(서버 계약 · 캐시 · 결정 D1 · D2 · D3 · PR L2). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

화면은 `FestivalPackUseCase`(`state` · `reloadCachedPacks` · `packUpdates` · `downloadPack`)만 본다. 구현은 `FestivalPackRepositoryImpl`(domain/festival/sources, 공유 상태 · 엔티티 변환 · 오류 변환) → `PackSyncService`(`NetworkPackSyncService`, service/pack) → `PackWire`(메시지) · `PackCache`(파일) · `FestiCoreWebSocketClient`(core/network, Ktor `WebSockets` + OkHttp 엔진) 순서다.

## 동작 규칙

1. 연결: `BASE_URL`의 `https`→`wss`(`http`→`ws`)에 `api/v1/festivals/updates`를 붙인다. 인증 헤더 없음, handshake 재시도 0(끊기면 서비스가 새로 연다). http(s) 절대 주소가 아니면 `Unconfigured`.
2. 요청: `{"event":"pack.get","requestId":<소문자 UUID>,"data":{"festivalId", "ifNoneMatch"?}}`. 저장된 팩이 있으면 그 ETag를 `ifNoneMatch`로 보낸다. 전체 1024바이트를 넘으면 보내지 않는다(`InvalidMessage`). 축제 ID는 `[A-Za-z0-9_-]` 1~128자, 아니면 `InvalidFestivalId`.
3. 수신 메시지는 텍스트 · UTF-8 4MB(4,194,304바이트) 이하 · JSON 객체 · 문자열 `event`. 아니면 `InvalidMessage`. 응답 대기는 메시지마다 15초, 넘으면 소켓을 닫고 `Timeout`.
4. 응답 해석(`requestId`가 다르면 `InvalidMessage`, `pack.updated`는 요청과 무관하게 언제든 올 수 있다):
   - `pack.snapshot{festivalId, packVersion>0, etag(1~128B), pack}`: 팩 JSON 8MB(8,388,608바이트) 이하, `pack.id == festivalId`, `pack.packVersion == packVersion`. 아니면 `InvalidPack`. 메시지 4MB 한도가 먼저라 4MB를 넘는 팩은 청크로만 받는다.
   - `pack.start{festivalId, packVersion, etag, totalBytes(1~8MB), chunkCount = ceil(total/48KB), sha256(64자 소문자 hex)}` → `pack.chunk{index, base64}` × N → `pack.complete`. 청크는 `index` 순서 · 같은 `requestId` · 크기 `min(49152, 남은 바이트)`이어야 하고, 끝에서 길이 · sha256 · 팩 검증을 한다. 하나라도 틀리면 `InvalidChunk`이고 저장 팩은 그대로다. 청크마다 누적 바이트 진행률을 낸다(스냅샷은 진행률 없음).
   - `pack.not_modified{festivalId, packVersion, etag}`: 저장 팩과 셋 다 같아야 한다(아니면 `InvalidMessage`). 확인 시각을 지금으로 바꾼다.
   - `pack.retry{festivalId, retryAfterMs>0}` → `RetryAfter`. `pack.error{code, retryAfterMs?}`: `PACK_BUSY` + 양수 ms → `RetryAfter`, 그 밖 → `Server(code)`.
5. 업데이트 힌트: 응답을 기다리는 중 같은 축제의 `pack.updated`가 오면 응답을 받은 뒤 저장된 ETag로 다시 요청한다. 5번 연속이면 `RetryAfter(1초)`. 그사이 새 팩을 받았으면 마지막 `not_modified`도 `Updated`로 알린다.
6. 세대: 요청마다 축제별 세대를 올리고, 저장 · 확인 시각 갱신은 세대가 마지막 반영 세대 이상일 때만 한다. 늦게 온 옛 응답은 저장 팩을 `NotModified`로 돌려주고, 저장 팩이 지워졌으면 `PackDeleted`. 삭제는 세대를 올려 진행 중 응답이 팩을 되살리지 않게 한다.
7. 단발 받기(`download`): 연결 하나로 맞춘다. `RetryAfter`면 최대 2번 기다려 다시 요청하고(3번째도 바쁘면 `Server("PACK_BUSY")`), 기다릴 시간이 0 이하이거나 남은 예산(합계 30초)보다 길면 `Timeout`. 다시 요청할 때 `Reset`을 먼저 보낸다. 수집을 멈추면 소켓을 닫는다.
8. 감시(`updates`): 맞춘 뒤 같은 연결에서 `pack.updated`를 기다렸다가 다시 맞춘다. 결과마다 재접속 지연 · handshake 거부 횟수를 초기화한다. 유휴 60초가 지나면 오류 표시 없이 지연 후 다시 연다. 전송 실패(끊김 · 오프라인 · 시간 초과 · handshake 거부)면 팩을 한 번이라도 받은 경우에만 `Reconnecting`을 알리고 `min(30, 2^n)`초 뒤 다시 연다. handshake 거부가 3번 이어지면 300초 뒤 다시 시도한다(지연은 늘리지 않는다). 전송 실패가 아닌 오류(서버 오류 · 검증 실패 · 인증서 오류 · 서버 주소 없음)는 흐름을 끝낸다. 감시 시작 뒤 팩이 지워지면 조용히 끝낸다. `RetryAfter`는 0.1~30초로 잘라 기다린다.
9. 저장(`PackCache`): `filesDir/festicore/packs/{festivalId}.json` 한 파일에 `{festivalID, packVersion, etag, pack(base64), sha256, checkedAt(epoch ms)}`를 `android.util.AtomicFile`로 통째로 쓴다. 읽을 때 축제 ID · 8MB · 팩 · ETag · sha256을 다시 검증한다. 더 낮은 버전으로 덮어쓰지 않는다. 확인 시각은 저장 · `not_modified` 때 지금으로 둔다.
10. 손상 파일: 읽기는 `InvalidPack`, 목록은 그 파일만 뺀다(`.json`이 아닌 파일 · 이름이 틀린 파일 · 링크 · 16MB 넘는 파일도 뺀다). 동기화는 손상 파일을 없는 팩으로 보고 ETag 없이 다시 받아 덮어쓴다. 파일을 지우지는 않는다. 폴더 · 파일 접근 오류는 숨기지 않는다.
11. 공유 상태(`FestivalPackState`, 결정 D1): `packs`(축제 → 받은 팩, 문서 해석 결과 포함) · `downloads`(받는 중 축제 → 진행률, 첫 청크 전 · 스냅샷이면 null) · `failures`(마지막 받기 실패). `reloadCachedPacks`가 `packs`를 통째로 바꾸고, 받기 · 감시 결과(`Updated` · `NotModified`)가 그 축제를 바꾼다. 받기를 시작하면 `downloads`에 넣고 그 축제 실패를 지우며, 끝나면(성공 · 실패 · 취소) `downloads`에서 뺀다. 실패하면 `failures`에 남긴다.
12. 문서 해석: 팩 JSON을 상세와 같은 `FestivalPackDocumentDto`로 읽는다. 실패하거나 `id`가 다르면 `document = null`(팩은 유지).
13. 오류(도메인 `FestivalPackError`): 전송 실패 · `Timeout` → `Offline`, `InvalidFestivalId`, 메시지 · 팩 · 청크 검증 → `InvalidPack`, `PackTooLarge`, `PackDeleted`, `Server(code)`, 그 밖(서버 주소 없음 · 저장소 IO) → `Unavailable`.

## 엣지 케이스

- 청크 받는 중 `pack.updated`가 와도 끊지 않고 끝까지 받은 뒤 다시 요청한다.
- 단발 받기 도중 수집을 멈추면 연결을 닫고 `downloads`에서 뺀다.
- 감시 연결이 유휴로 닫히면 같은 소켓을 다시 쓰지 않는다(시간 초과 때 소켓을 닫는다).
- handshake 거부(stage 잘못된 경로 → 404/502)는 OkHttp `ProtocolException` · Ktor `WebSocketException`(401)으로 오고 `HandshakeRejected`로 분류한다.

## 제약 / 가정

- 저장은 JSON 파일 + `AtomicFile`(결정 D2 (a)). Room을 쓰지 않으므로 CursorWindow 2MB 위험(설계 위험 2)이 없다. iOS의 SQLite + JSON 이중 저장은 옛 JSON 이전 호환용이라 옮기지 않는다(`SQLitePackCacheTests`의 이전 · DB 복구 사례 제외).
- WebSocket은 Ktor `WebSockets` 플러그인(결정 D3 (a)). 플러그인은 `ktor-client-core` 3.6.1 안(`io.ktor.client.plugins.websocket`)에 있어 새 아티팩트가 없다(`ktor-client-websockets`는 core만 의존하는 빈 묶음). 엔진은 기존 `ktor-client-okhttp`. 앱 API `HttpClient`와 따로 만들어 인증 · JSON 플러그인을 거치지 않는다.
- MockEngine은 WebSocket을 지원하지 않는다. 단위 테스트는 가짜 `FestiCoreWebSocketClient` · 소켓(`ScriptSocket`)으로 하고 실서버를 부르지 않는다. 실제 Ktor 연결은 아래 stage 확인으로만 봤다.
- base64는 `java.util.Base64`(minSdk 26). 설계 표의 `android.util.Base64`(minSdk 24 기준)와 다르다. sha256은 `MessageDigest("SHA-256")`.
- 팩 파일은 공개 팩이라 `core/datastore`로 모으지 않는다(목록 캐시와 같은 판단, `service/festivalcatalog/.spec/catalog-cache.md`).

## stage 확인 (2026-11-22, 읽기 전용 `pack.get`만)

| 확인                                                                                                     | 결과                                                                                                                                                                                                                                                                   |
| -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| handshake `wss://festicore-dev.up.railway.app/api/v1/festivals/updates`                                  | 101, 인증 없이 열림(약 0.9초)                                                                                                                                                                                                                                          |
| `pack.get{festivalId: fst_00e0f4f1d8065a93}`(116B)                                                       | `pack.snapshot` 35,188B 한 메시지, 최상위 키 `event,requestId,data`, `requestId` 그대로 돌아옴. `data{festivalId, packVersion:56, etag:"\"b5fe…a148\""(따옴표 포함 66자), pack}`, 팩 키 26개 · `pack.id` · `pack.packVersion` 일치. 청크 전송은 나오지 않음(팩이 작음) |
| 같은 요청 + `ifNoneMatch`                                                                                | `pack.not_modified{festivalId, packVersion:56, etag}` 220B                                                                                                                                                                                                             |
| 없는 축제 ID                                                                                             | `pack.error{code:"FESTIVAL_NOT_FOUND"}`(`retryAfterMs` 없음) → `Server("FESTIVAL_NOT_FOUND")`                                                                                                                                                                          |
| 잘못된 경로                                                                                              | HTTP 404(curl) · Ktor+OkHttp에서 `ProtocolException: Expected HTTP 101 … '502 Bad Gateway'` → `HandshakeRejected`                                                                                                                                                      |
| Android 구현(`NetworkPackSyncService` + `createFestiCoreWebSocketClient`, 임시 Robolectric 실행 후 삭제) | `sync` → `Updated(v56, 34,964B)`, 다시 `sync` → `NotModified`, `download` → `Finished(NotModified)`, `cachedPacks` → 1건                                                                                                                                               |

## 작업 모듈

| 모듈               | 계층                | 변경 내용                                                                                                                                                                 |
| ------------------ | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| core/network       | core                | `FestiCoreWebSocket` · `FestiCoreWebSocketClient` · `WebSocketError` · `webSocketUrl` · `createFestiCoreWebSocketClient`(Ktor `WebSockets` + OkHttp)                      |
| service/api        | service             | `festival/FestivalApi.PACK_UPDATES`                                                                                                                                            |
| service/pack(신규) | service             | `PackSyncService` · `NetworkPackSyncService` · `PackWire` · `PackCache` · `ReconnectBackoff` · `PackSyncError` · `servicePackModule(baseUrl)`                             |
| domain/festival    | interface · sources | `CachedFestivalPack` · `PackSyncResult` · `PackDownloadEvent` · `FestivalPackState` · `FestivalPackError` · `FestivalPackRepository` · `FestivalPackUseCase`, 구현 · Koin |
| app                | app                 | `servicePackModule(BuildConfig.BASE_URL)` 등록                                                                                                                            |

## 영향 범위

| 모듈             | 계층    | 영향 이유                                                                                    |
| ---------------- | ------- | -------------------------------------------------------------------------------------------- |
| feature/festival | sources | L3(목록 팩 받기) · L4(보드)가 `FestivalPackUseCase`를 쓴다. 이 PR에서는 화면을 바꾸지 않는다 |

## 미해결 질문

- 4MB를 넘는 실제 팩의 청크 전송 · `pack.retry` · `PACK_BUSY` · 서버가 보내는 `pack.updated`는 stage에서 보지 못했다(팩 35KB, 변경 없음). 형식은 iOS 코드 근거.
- 앱 시작 때 `reloadCachedPacks`를 누가 부를지는 L3(목록)에서 정한다.
- 30일 지난 팩 자동 삭제(`deleteExpiredPacks`)는 옮기지 않았다(설계 경계 표).

## 변경 이력

- L2 팩 WebSocket 동기화 · 청크 검증 · 재접속 · 파일 저장 · 공유 상태
