# 네트워크 · 저장소

iOS `Core/FestiCoreNetwork` · `Core/FestiCoreStorage`에 대응한다. 설계: `.claude/docs/specs/2026-11-21-launch-to-tabs-android-design.md`.

## core/network

- 클라이언트는 `createFestiCoreHttpClient(baseUrl, engine, json, configure)`로 만든다. `baseUrl`은 app이 `BuildConfig.BASE_URL`(끝이 `/`)로 넘기고, 요청 경로는 앞 `/` 없이 쓴다(`api/v1/me`). 인증 플러그인은 `configure`로 붙인다.
- 요청은 `HttpClient.festiCoreRequest<T> { ... }`로 보낸다. 서버 응답 래퍼 `{code, message, data}`에서 `code == "SUCCESS"`일 때 `data`를 돌려준다.
- 실패는 `NetworkError`를 던진다.
- 상태 코드 · 헤더를 직접 봐야 하는 요청(ETag · 304 · 429)은 `HttpClient.festiCoreExchange(block, handle)`로 보내고 `handle`에서 응답을 읽는다. 전송 · 해석 실패는 같은 `NetworkError`로 바뀐다(예: `FestivalApi.live`).

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

## core/datastore

- `FirstInstallOnboardingStore`: 첫 설치 온보딩 완료 여부(DataStore Preferences).
- `RecentSearchStore`: 사용자별 최근 검색어(DataStore Preferences, iOS와 같은 키 · `\n` 형식). 규칙은 `domain/festival/.spec/recent-search.md`.
- `SecureStorage`: 토큰 · 설치 기기 ID(키 `deviceID`, `service/device`) 같은 보안 값. Android Keystore AES/GCM으로 암호화해 DataStore에 저장한다. 복호화에 실패하면 `null`을 돌려주고 그 키를 지운다. 쓰기 실패는 `SecureStorageException`.
- Keystore · DataStore · SharedPreferences는 이 모듈에서만 쓴다(`check-boundaries`가 막는다). 다른 모듈은 인터페이스를 주입받는다.
- Robolectric은 AndroidKeyStore를 지원하지 않으므로 테스트는 가짜 `ValueCipher`를 주입한다. 실제 Keystore 동작은 기기 · 에뮬레이터에서 확인한다.

## service/api · service/auth (인증 세션)

- 경로 상수는 `AuthApiPath`, 요청은 `AuthApi`(login · refresh · me · patchMe). Bearer가 필요한 요청은 `bearerAuthorization(expectedSessionId)` 속성으로 표시하고, 표시가 있는 요청에만 인증 플러그인이 토큰을 붙인다. refresh는 표시가 없어 플러그인을 거치지 않는다.
- 세션은 `{sessionId, revision, accessToken, refreshToken}` JSON 하나로 `SecureStorage` 한 키에 저장한다(`CredentialStore`). 저장 · 갱신 · 삭제는 `(sessionId, revision)` compare-and-set으로만 한다.
- 401은 `TokenRefresher`(Mutex) 한 경로로만 갱신한다. 세션이 없거나 sessionId가 바뀌면 재전송하지 않고 `AuthSessionError.NotAuthenticated`, revision만 바뀌었으면 refresh 없이 현재 토큰으로 한 번 재요청한다. refresh 401은 세션을 지우고 `SessionExpiryEvents`에 한 번 알린다. 오프라인 · 5xx는 세션을 유지하고 그 `NetworkError`로 실패한다.
- 세션이 없거나 기대 sessionId와 다른 Bearer 요청은 보내지 않는다. 공개 API는 `AuthSession`(isLoggedIn · currentSessionId · saveLogin · clear)과 `SessionExpiryEvents`다.
- HttpClient는 `serviceAuthModule(baseUrl)`이 인증 플러그인을 붙여 등록하고, app은 `BuildConfig.BASE_URL`만 넘긴다.
