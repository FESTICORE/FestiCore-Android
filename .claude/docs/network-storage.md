# 네트워크 · 저장소

iOS `Core/FestiCoreNetwork` · `Core/FestiCoreStorage`에 대응한다. 설계: `.claude/docs/specs/2026-11-21-launch-to-tabs-android-design.md`.

## core/network

- 클라이언트는 `createFestiCoreHttpClient(baseUrl, engine, json, configure)`로 만든다. `baseUrl`은 app이 `BuildConfig.BASE_URL`(끝이 `/`)로 넘기고, 요청 경로는 앞 `/` 없이 쓴다(`api/v1/me`). 인증 플러그인은 `configure`로 붙인다.
- 요청은 `HttpClient.festiCoreRequest<T> { ... }`로 보낸다. 서버 응답 래퍼 `{code, message, data}`에서 `code == "SUCCESS"`일 때 `data`를 돌려준다.
- 실패는 `NetworkError`를 던진다.

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
- `SecureStorage`: 토큰 같은 보안 값. Android Keystore AES/GCM으로 암호화해 DataStore에 저장한다. 복호화에 실패하면 `null`을 돌려주고 그 키를 지운다. 쓰기 실패는 `SecureStorageException`.
- Keystore · DataStore · SharedPreferences는 이 모듈에서만 쓴다(`check-boundaries`가 막는다). 다른 모듈은 인터페이스를 주입받는다.
- Robolectric은 AndroidKeyStore를 지원하지 않으므로 테스트는 가짜 `ValueCipher`를 주입한다. 실제 Keystore 동작은 기기 · 에뮬레이터에서 확인한다.
