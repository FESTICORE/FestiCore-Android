# 네트워크 · 저장소

iOS `Core/FestiCoreNetwork` · `Core/FestiCoreStorage`에 대응한다. 설계: `.claude/docs/specs/2026-11-21-launch-to-tabs-android-design.md`.

## core/network

- 클라이언트는 `createFestiCoreHttpClient(baseUrl, engine, json, configure)`로 만든다. `baseUrl`은 app이 `BuildConfig.BASE_URL`(끝이 `/`)로 넘기고, 요청 경로는 앞 `/` 없이 쓴다(`api/v1/me`). 인증 플러그인은 `configure`로 붙인다.
- 요청은 `HttpClient.festiCoreRequest<T> { ... }`로 보낸다. 서버 응답 래퍼 `{code, message, data}`에서 `code == "SUCCESS"`일 때 `data`를 돌려준다.
- 실패는 `NetworkError`를 던진다.
- 상태 코드 · 헤더를 직접 봐야 하는 요청(ETag · 304 · 429)은 `HttpClient.festiCoreExchange(block, handle)`로 보내고 `handle`에서 응답을 읽는다. 전송 · 해석 실패는 같은 `NetworkError`로 바뀐다(예: `FestivalRequest.live`).

| 상황                              | 오류                                                              |
| --------------------------------- | ----------------------------------------------------------------- |
| 2xx가 아님                        | `Http(status, code, message)` (본문 `{code, data:null, message}`) |
| 2xx인데 `code != "SUCCESS"`       | `InvalidResponse`                                                 |
| 연결 실패 · IO                    | `Offline`                                                         |
| 시간 초과                         | `Timeout`                                                         |
| 본문 해석 실패 · 필수 `data` 없음 | `Decoding`                                                        |
| 그 외                             | `Unknown`                                                         |

- JSON은 `FestiCoreJson`(`ignoreUnknownKeys`, `explicitNulls = false`)을 쓴다. null 필드는 보내지 않는다(서버 검증이 `null`을 값으로 본다).
- 테스트는 Ktor `MockEngine`으로 한다. 단위 테스트에서 실서버를 부르지 않는다.

### WebSocket

- `createFestiCoreWebSocketClient(baseUrl)`이 `FestiCoreWebSocketClient`를 만든다(Ktor `WebSockets` 플러그인, `ktor-client-core`에 포함 · OkHttp 엔진). 앱 API `HttpClient`와 따로 둬 인증 · JSON 플러그인을 거치지 않는다. iOS `FestiCoreWebSocketClient`.
- 주소는 `webSocketUrl(baseUrl, path)`: `https`→`wss`, `http`→`ws`, 그 밖은 `WebSocketError.InvalidUrl`. 인증 헤더 없음, handshake 재시도 0(다시 여는 판단은 쓰는 쪽).
- `connect`가 handshake까지 마친 `FestiCoreWebSocket`(send · receive(텍스트) · cancel)을 돌려준다. 오류: 101이 아닌 응답 `HandshakeRejected`, 닫힌 연결 `Closed`, 텍스트 아님 `NonTextMessage`, 입출력 `NetworkError.Offline` · `Timeout`, 인증서(`SSLException`) `NetworkError.Unknown`.
- MockEngine은 WebSocket을 지원하지 않는다. 테스트는 `FestiCoreWebSocketClient` 가짜 구현을 쓴다. 쓰는 곳: `service/pack`(`service/pack/.spec/pack-sync.md`).

## core/datastore

- `FirstInstallOnboardingStore`: 첫 설치 온보딩 완료 여부(DataStore Preferences).
- `RecentSearchStore`: 사용자별 최근 검색어(DataStore Preferences, iOS와 같은 키 · `\n` 형식). 규칙은 `domain/festival/.spec/recent-search.md`.
- `SecureStorage`: 토큰 · 설치 기기 ID(키 `deviceID`, `service/device`) 같은 보안 값. Android Keystore AES/GCM으로 암호화해 DataStore에 저장한다. 복호화에 실패하면 `null`을 돌려주고 그 키를 지운다. 쓰기 실패는 `SecureStorageException`.
- 받은 축제 팩은 DataStore가 아닌 파일이다: `service/pack`의 `PackCache`가 `filesDir/festicore/packs/{id}.json`에 `AtomicFile`로 쓴다(공개 팩, 최대 8MB, 설계 D2). 목록 캐시(`service/festivalcatalog`)와 같은 방식.
- Keystore · DataStore · SharedPreferences는 이 모듈에서만 쓴다(`check-boundaries`가 막는다). 다른 모듈은 인터페이스를 주입받는다.
- Robolectric은 AndroidKeyStore를 지원하지 않으므로 테스트는 가짜 `ValueCipher`를 주입한다. 실제 Keystore 동작은 기기 · 에뮬레이터에서 확인한다.

## service/api · service/apiendpoint · service/auth (인증 세션)

- 경로는 `service/api`의 기능 폴더 `<X>Api`(예: `auth/AuthApi`), 요청은 `service/apiendpoint`의 같은 폴더 `<X>Request`(예: `auth/AuthRequest`: login · refresh · me · patchMe), 요청 본문은 `<X>RequestBody.kt`다. iOS `Service/API` · `Service/APIEndpoint`와 같다. `<X>Request`는 응답 타입을 모른다: 축제 · 크루 · 깃발 · 알림 요청은 `suspend inline fun <reified T>`로 응답 타입을 호출하는 쪽에서 받고(iOS `networkClient.request(_:as:)`처럼), 응답 DTO는 `domain/*/sources/model/dto`에 있다. 인증 응답 DTO(`AuthDto.kt`)만 `service/auth`가 토큰 갱신에 쓰므로 `service/api`에 남는다. Bearer가 필요한 요청은 `bearerAuthorization(expectedSessionId)` 속성으로 표시하고, 표시가 있는 요청에만 인증 플러그인이 토큰을 붙인다. refresh는 표시가 없어 플러그인을 거치지 않는다.
- 세션은 `{sessionId, revision, accessToken, refreshToken}` JSON 하나로 `SecureStorage` 한 키에 저장한다(`CredentialStore`). 저장 · 갱신 · 삭제는 `(sessionId, revision)` compare-and-set으로만 한다.
- 401은 `TokenRefresher`(Mutex) 한 경로로만 갱신한다. 세션이 없거나 sessionId가 바뀌면 재전송하지 않고 `AuthSessionError.NotAuthenticated`, revision만 바뀌었으면 refresh 없이 현재 토큰으로 한 번 재요청한다. refresh 401은 세션을 지우고 `SessionExpiryEvents`에 한 번 알린다. 오프라인 · 5xx는 세션을 유지하고 그 `NetworkError`로 실패한다.
- 세션이 없거나 기대 sessionId와 다른 Bearer 요청은 보내지 않는다. 공개 API는 `AuthSession`(isLoggedIn · currentSessionId · saveLogin · clear)과 `SessionExpiryEvents`다.
- HttpClient는 `serviceAuthModule(baseUrl)`이 인증 플러그인을 붙여 등록하고, app은 `BuildConfig.BASE_URL`만 넘긴다.
- 내 알림(`UserNotificationRequest`, `domain/usernotification`)은 모든 요청을 현재 세션 ID에 묶어 보낸다(`bearerAuthorization(expectedSessionId)`, 재시도 0). 읽음 · 삭제(`POST`)는 `festiCoreExchange`로 보내 2xx면 본문을 보지 않는다(204 빈 본문 포함). 로컬 저장은 없고 배지는 메모리 공유 상태다(`domain/usernotification/.spec/user-notification.md`).
- 깃발 쓰기 · 크루(`FlagRequest` · `CrewRequest`)는 재시도 0이다. 깃발 상세는 로그인 세션이 있을 때만 Bearer, 크루 초대 미리보기(`GET crews/invite/{code}`)는 인증 없음, 나머지는 현재 세션 ID에 묶은 Bearer이고 `FestivalCrewRepositoryImpl` · `FestivalFlagDetailRepositoryImpl`이 요청을 직접 부르며 세션이 없으면 요청 전에 `AuthSessionError.NotAuthenticated`를 던진다.
- 서버가 `Idempotency-Key`를 받는 쓰기(깃발 팔로우 `PUT` · 크루 상태 `POST`)는 `HttpRequestBuilder.idempotencyKey()`(`service/apiendpoint/.../common/IdempotencyKey.kt`, `UUID.randomUUID()`)로 요청마다 새 키를 붙인다. 재시도가 없으므로 키를 저장하지 않는다.
- `FestiCoreJson`은 `explicitNulls = false`라 null 필드를 빼고 보낸다. null을 보내야 하는 본문(`PUT me/pinned-flag {"flagId": null}`)은 그 DTO에 전용 serializer를 둔다(`PinnedFlagBody`).
