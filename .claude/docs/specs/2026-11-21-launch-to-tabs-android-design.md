# 앱 시작 → 스플래시 → 온보딩 → Google 로그인 → 메인 탭 Android 이식 설계

## 목표

iOS 앱의 시작 흐름(스플래시 → 첫 설치 온보딩 → 첫 진입 로그인 · 회원가입 → 메인 탭 껍데기)을 Android로 옮긴다. Apple 로그인은 옮기지 않고 Google만 둔다. 화면은 stage 서버(`https://festicore-dev.up.railway.app/`, `BuildConfig.BASE_URL`) 실데이터로 동작한다. 샘플(목업) 데이터는 쓰지 않는다. 프리뷰와 단위 테스트의 가짜 값만 예외다.

iOS 원본(`../FestiCore-iOS/Projects`, 이하 iOS):

- 흐름: `App/Sources/Navigation/Main/AppCoordinator.swift:32-37 · 227-241`, `AppCoordinator+Route.swift:25-100`, `Splash/SplashFeature.swift:66-95`, `Shared/AuthFlowState.swift:17-94 · 216-217`, `Auth/AuthCoordinator.swift:187-345`, `Session/SessionCoordinator.swift:109-340`
- 화면: `Feature/Onboarding/Sources/Onboarding/**`, `Feature/Auth/Sources/{Login,SignUp}/**`, `Feature/Auth/Interface/Sources/{LoginDelegate,SignUpDelegate}.swift`
- 계약 · 구현: `Domain/AuthDomain/**`, `Service/API/Sources/{Base/FestiCoreDomain,Auth/AuthAPI}.swift`, `Service/APIEndpoint/Sources/Auth/**`, `Service/FestiCoreAuth/**`, `Core/FestiCoreNetwork/**`, `Core/FestiCoreStorage/Interface/Onboarding/FirstInstallOnboardingStore.swift`
- 탭: `UI/FestiCoreDesignKit/Sources/Component/Navigation/FCTabBar.swift:12-35`, `App/Sources/Navigation/MainTab/MainTabView.swift:228-258`, `MainTabCoordinator.swift:21-30 · 115-118 · 325-352`

## 범위

포함:

1. 스플래시, 첫 설치 온보딩 4페이지와 완료 저장
2. 첫 진입 로그인 화면(launchLogin, Google + "지금은 건너뛸게요")
3. Google 로그인 → 서버 로그인 → 토큰 저장 → `GET api/v1/me` → 가입 조건(`isEligible`) 판정
4. 회원가입 화면(닉네임 + 필수 약관 3종) → `PATCH api/v1/me`
5. 토큰 자동 갱신(401 → `POST api/v1/auth/refresh`), 갱신 실패 시 세션 만료 → 로그인 화면
6. 메인 탭 껍데기: `FCTabBar` 4탭(라이브 · 타임테이블 · 깃발 · 마이, 기본 라이브). 각 탭 루트는 빈 화면(탭 이름 제목만, 데이터 없음)이고 탭 내용은 이후 단계 문서에서 옮긴다
7. 로그인 상태의 앱 진입 · 포그라운드 진입 시 `GET api/v1/me`로 프로필 재확인(가입 조건 재평가 포함)

제외(이후 단계): Apple 로그인, 로그인 시트(문맥별 `LoginModalView`), 기수(bearer) 설정, 예약 migration, 딥링크, 기기 연동 · 푸시(`AccountDeviceSync`), 위젯, 로그아웃 · 탈퇴(마이 탭), 분석(Sentry · Mixpanel — `service/analytics` 모듈을 만드는 단계에서 아래 이벤트를 붙인다), 알림 권한 안내, 탭 내용.

## 수용 기준

1. 첫 설치 실행: 스플래시(2초) → 온보딩. 온보딩을 마치거나 건너뛰면 완료가 저장되고, 이후 실행에서는 온보딩이 다시 뜨지 않는다.
2. 비로그인 실행: 스플래시 → 메인 탭 위에 launchLogin이 덮인다(전체 화면). "지금은 건너뛸게요"로 닫으면 메인 탭이 보인다. 건너뛰기는 저장하지 않아 다음 실행에 다시 뜬다(iOS와 같음).
3. Google 로그인 성공 + `isEligible`이면 덮개가 걷히고 메인 탭. 아니면 회원가입 화면으로 가고, 제출 성공 후 메인 탭. 덮개는 계정 확인 · 가입이 끝날 때까지 유지해 홈이 잠깐 비치지 않는다.
4. 로그인 실행: 스플래시 동안 로컬 세션을 확인해 바로 메인 탭(덮개 없음). 진입과 동시에, 그리고 이후 포그라운드 진입마다 `GET api/v1/me`로 프로필을 재확인한다(아래 "프로필 재확인"). 성공 프로필이 `isEligible == false`(약관 버전 상승 등)면 메인 탭 위에 SignUp 덮개를 띄운다. 첫 진입 흐름이 아닐 때 세션 만료 이벤트나 재확인 401이 오면 iOS와 같이 launchLogin을 띄우지 않고 비로그인 메인 탭에 둔다(`SessionCoordinator.swift:206-210,341-351`, 점검 문서 결정 7).
5. 오류: 사용자 취소 · 중복 요청은 문구 없음, 그 외 로그인 실패는 "로그인에 실패했습니다. 다시 시도해 주세요.", 토큰 저장 실패는 "로그인 정보를 저장하지 못했어요. 다시 시도해 주세요.". 가입: 닉네임 규칙 위반 "닉네임은 1~12자로 입력해 주세요.", `422 TERMS_VERSION_OUTDATED`는 체크를 지우고 "약관이 변경됐어요. 내용을 확인하고 다시 동의해 주세요." 후 프로필 재조회(1회만, 다시 바뀌면 "약관이 다시 변경됐어요. 나중에 다시 시도해 주세요."로 제출 막음), 401은 첫 진입 흐름이면 launchLogin으로 복귀하고 재확인으로 뜬 가입이면 덮개를 닫아 비로그인 메인 탭(iOS 로그인 시트는 옮기지 않음), 오프라인 "오프라인에서 저장하지 못했어요. 연결 후 다시 시도해 주세요.", 그 외 "저장하지 못했어요. 다시 시도해 주세요.".
6. 프로필 조회가 오프라인 · 서버 오류로 실패해도 로그인 상태를 유지한다. 401(갱신까지 실패)일 때만 세션을 지운다.
7. 단위 테스트(JVM): 각 ViewModel(Orbit `test`)의 상태 전이 · 부수효과, 닉네임 규칙, `isEligible`, 응답 래퍼 · 에러 매핑(Ktor `MockEngine`), 토큰 갱신(401 → refresh → 재요청, refresh 401 → 만료 이벤트), 토큰 저장소 왕복(Robolectric). 실서버를 부르는 테스트는 없다.
8. `.claude/scripts/verify --all`과 PR CI가 통과한다. stage 앱(`assembleStageDebug`)이 빌드된다.
9. stage 실데이터 확인: Google 로그인 실기기 · 에뮬레이터 확인은 Android OAuth 클라이언트 등록(아래 위험 1) 후 가능하다. 그 전에는 `/auth/login`을 뺀 stage 호출(예: 미인증 `GET api/v1/me` → 401 매핑)을 계측 테스트 없이 수동 `curl`로 응답 형식만 확인해 PR에 기록한다.

## 모듈과 책임

의존 방향은 `.claude/docs/architecture.md`를 따른다(feature는 다른 모듈의 `interface`만, `sources`는 app만 연결).

| 모듈                                     | iOS 대응                        | 내용                                                                                                                                                                                                                                                                                                                          |
| ---------------------------------------- | ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `core/network`                           | `Core/FestiCoreNetwork`         | Ktor `HttpClient`(OkHttp 엔진) 팩토리, `BaseResponse<T>{code,message,data}`, `ErrorBody{code?,message?}`, `NetworkError`(`Offline` · `Timeout` · `Http(status, code, message)` · `Decoding` · `Unknown`), `code != "SUCCESS"` → `InvalidResponse`, 기본 헤더 `Accept: application/json`, JSON(camelCase, `ignoreUnknownKeys`) |
| `core/datastore`                         | `Core/FestiCoreStorage`         | `FirstInstallOnboardingStore`(DataStore Preferences 불리언), `SecureStorage`(DataStore + Android Keystore AES/GCM으로 암호화한 문자열 값)                                                                                                                                                                                     |
| `service/api`                            | `Service/API` · `APIEndpoint`   | 경로 상수(`api/v1/auth`, `api/v1/me`, 앞 `/` 없음 — `BASE_URL`이 `/`로 끝남), 인증 요청 정의와 요청 · 응답 DTO(`AuthLoginBody{provider,token,authorizationCode?,name?}` 등)                                                                                                                                                   |
| `service/auth`                           | `Service/FestiCoreAuth`         | `CredentialStore`(access · refresh 한 쌍을 한 값으로 원자 저장 → 반쪽 쌍 불가), Ktor `Auth` bearer 플러그인 연결(loadTokens · refreshTokens), `SessionExpiryEvents`(`SharedFlow`), `isLoggedIn`                                                                                                                               |
| `domain/auth/interface`                  | `Domain/AuthDomain/Interface`   | `AuthProfile` · `AuthUser`, `isEligible`, `AuthNicknameRule`(trim 1~12, 입력 중 12자 자르기), `AuthLoginResult`, `AuthDomainError`(`UserCancelled` · `RequestInProgress` · `Offline` · `NotAuthenticated` · `TermsVersionOutdated` · `Unavailable`), `AuthRepository` · `GoogleSignInClient` 계약, UseCase                    |
| `domain/auth/sources`                    | `Domain/AuthDomain/Sources`     | Repository 구현(service/api · service/auth 사용), 에러 매핑(iOS `AuthDomainError+Network.swift:13-33`과 같은 표), Credential Manager 기반 `GoogleSignInClient` 구현                                                                                                                                                           |
| `feature/onboarding/{interface,sources}` | `Feature/Onboarding`            | 진입 키 · 완료 결과 / Screen · ViewModel · Contract                                                                                                                                                                                                                                                                           |
| `feature/auth/{interface,sources}`       | `Feature/Auth`                  | launchLogin · SignUp 진입 키 · 결과 / Screen · ViewModel                                                                                                                                                                                                                                                                      |
| `app`                                    | `App` · `Feature/AppNavigation` | 스플래시, 루트 Coordinator(Navigation 3), 메인 탭 껍데기, 포그라운드 재확인, Koin 목록 합치기, `INTERNET` 권한                                                                                                                                                                                                                |

## 흐름(루트 상태 기계)

루트 상태는 iOS와 같이 `Splash` · `Onboarding` · `Home(launchLogin: LaunchLoginState?)` 세 개다. 로그인은 독립 라우트가 아니라 Home 위 덮개다.

```
Splash ── 2초 ──▶ onboardingStore.hasCompleted()
   ├─ false(읽기 실패는 true로 간주 · 로그) ─▶ Onboarding ── 완료/건너뛰기 ─▶ markCompleted()(실패는 로그) ─┐
   └─ true ────────────────────────────────────────────────────────────────────────────────────────┤
                                                       authSession.isLoggedIn (확인 중엔 Splash 유지) ◀┘
   ├─ true  ─▶ Home
   └─ false ─▶ Home + launchLogin
launchLogin ─ 건너뛰기 ─▶ Home
            ─ Google 성공 ─▶ (덮개 유지) 토큰 저장 ─▶ GET /me ─▶ isEligible ? Home : SignUp(덮개 안)
SignUp ─ 제출 성공 ─▶ Home     SignUp ─ 뒤로 ─▶ 세션 유지 → launchLogin(첫 진입) / Home(재확인 가입)
세션 만료 이벤트 ─▶ 토큰 삭제 → 첫 진입 가입 중이면 launchLogin, 그 밖에는 Home(비로그인)
```

**프로필 재확인** (iOS `AppCoordinator.swift:243-250`, `SessionCoordinator.swift:109-117 · 267-292`): 로그인 상태로 Home에 들어갈 때와 `ON_START`(포그라운드)마다 `GET api/v1/me`를 한 번 부른다. 이미 진행 중이면 겹쳐 부르지 않는다.
- 성공 ∧ `isEligible` → 그대로. 성공 ∧ `!isEligible` → SignUp 덮개(받은 프로필로 초기화).
- 401(갱신까지 실패) → 세션 삭제 → 비로그인 Home(launchLogin 없음).
- 오프라인 · 5xx · 디코딩 오류 → 로그인 · 화면 유지, 자동 재시도 없음(다음 포그라운드에서 다시 확인).
- 응답 사용자 ID가 저장된 세션 계정과 다르면 무시한다(그 사이 계정이 바뀐 경우).

- Navigation 3: 루트 back stack에 `Splash` · `Onboarding` · `Home` 키. launchLogin · SignUp은 Home 위에 그리는 전체 화면 덮개(feature/auth Coordinator의 자체 back stack)로 둔다. 시스템 뒤로는 SignUp → launchLogin → 앱 종료 순(launchLogin에서 뒤로 = 건너뛰기와 같게 하지 않는다: iOS 첫 진입 화면은 닫기 제스처가 없다).
- 스플래시: 의존성을 늘리지 않기 위해 Lottie 대신 Android 12+ `SplashScreen` API(`core-splashscreen`, 배경 `staticInk`)와 Compose 스플래시 화면(`staticInk` 배경 + designkit `logo`, 상태바 숨김)을 2초 보인다. iOS Lottie 애니메이션은 `ui/animation` 모듈을 만드는 단계로 미룬다(차이로 기록).
- 임시 세션: iOS `stageSession`/`commitSession`은 "로그인 시트를 닫은 뒤 늦게 온 결과"를 버리기 위한 것이다. launchLogin에는 닫기가 없고 건너뛰기는 로그인 중 비활성(`isSigningIn` 동안 전체 disabled, iOS와 같음)이라 로그인 시도 ID로 늦은 결과만 버리고, 토큰은 서버 응답 직후 바로 저장한다. SignUp에서 뒤로 가도 iOS와 같이 확정된 세션은 유지한다(점검 문서 결정 2, `feature/auth/.spec/signup.md`).

## 화면

모든 색 · 글꼴은 designkit 토큰, 컴포넌트는 designkit 기존 것(`FCButton` ghost · `FCPrimaryButton` · `FCLoginButton(Google)` · `FCTextField` · `FCConsentAll` · `FCConsentRow` · `FCTabBar`)을 쓴다.

**온보딩** (iOS `OnboardingView.swift:13-186`, `OnboardingPage.swift:8-29`, `OnboardingFeature.swift:3-59`)

- 상태 `page`, 동작 `pageChanged(clamp)` · `nextTapped`(마지막이면 완료) · `skipTapped`(완료).
- 상단 우측 "건너뛰기" ghost(높이 52), 가로 `HorizontalPager`(이미지 · 제목 `baseTitle1Bold` · 설명 `baseBodyRegular`만 넘어감), 하단 점 8×8 간격 8(현재 `primaryNormal`, 나머지 `fillStrong`). 이미지 탭 = 다음, 마지막 이미지 접근성 라벨 "시작하기". 이미지 높이 `min(431, max(200, 화면높이 − 328))`.
- 문구 4쌍은 iOS `OnboardingPage.swift` 그대로. 이미지 `Visual-onboarding01~04` PNG를 `feature/onboarding/sources/src/main/res/drawable-nodpi/visual_onboarding_01~04.png`로 복사.

**launchLogin** (iOS `LoginView.swift:22-290`, `LoginFeature.swift:108-191`)

- 배경 `backgroundNormal`, 장식 별 13개(393×852 기준 비율 위치 · 색은 iOS와 같은 토큰), 로고 `logo-3d`(280×42.44), 제목 "가장 뜨거운 현장 속으로"(`baseTitle2Bold`), 설명 2줄(`baseFootnoteRegular`), 오류 문구(`statusNegative`), `FCLoginButton(Google)`, 텍스트 버튼 "지금은 건너뛸게요". 에셋 `logo-3d` PNG · `sparkle-star4/8` SVG는 `feature/auth/sources` res로 옮긴다(SVG는 기존 `gradle/svg-to-vector.py`로 벡터 드로어블 변환).
- 상태 `isSigningIn`, `errorMessage`, `attemptId`.

**SignUp** (iOS `SignUpView.swift`, `SignUpFeature.swift:44-262`, `AuthNicknameRule.swift`, `SessionCoordinator.swift:293-340`)

- 제목 "반가워요!\n어떻게 불러 드릴까요?", 닉네임 필드(라벨 · placeholder "닉네임", 카운터 "N / 12", 도움말 "크루원과 깃발에 보이는 이름이에요. 나중에 마이에서 바꿀 수 있어요."), 전체 동의 + 필수 3행(서비스 이용약관 `terms` · 개인정보 수집 · 이용 `privacy` · 위치기반서비스 이용약관 `location`), 하단 "다음".
- 약관 상세 URL: `LEGAL_BASE_URL`은 끝이 `/`인 `https://{host}/api/v1/legal/`(stage `https://festicore-dev.up.railway.app/api/v1/legal/`, prod `https://festicore.up.railway.app/api/v1/legal/`), 상대 경로는 `terms` · `privacy` · `location`(서버 `GET /api/v1/legal/{terms,privacy,location}`). 문자열 이어 붙이기 대신 `Uri.parse(base).buildUpon().appendPath(path)`로 만들고 세 URL을 단위 테스트로 고정한다. 이 URL을 외부 브라우저 `Intent.ACTION_VIEW`로 연다(iOS 앱 안 Web 화면은 `feature/web` 단계로 미룸, 차이로 기록).
- `canSubmit`: 제출 중 아님 · 재변경 소진 아님 · 3종 체크 · 체크 시점 약관 버전 == 서버 `currentTermsVersion` · trim 닉네임 비어 있지 않음. 서버 닉네임이 있으면 처음 한 번 미리 채움. 약관 버전이 바뀌면 체크를 모두 지움. 본문 `{nickname, acceptTerms:{version}, pushOptIn:true}`. 응답 사용자 ID가 요청 계정과 다르면 실패로 처리.

**메인 탭 껍데기** — `FCTabBar` 선택 상태를 탭 Coordinator가 들고, 탭마다 루트 Composable(빈 화면)을 둔다. 탭 상태는 탭을 바꿔도 유지한다.

## 서버 계약 (stage에서 확인 대상)

| 용도    | 메서드 · 경로              | 인증   | 요청                                                                    | 응답 `data`                                                                                                                               |
| ------- | -------------------------- | ------ | ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 로그인  | `POST api/v1/auth/login`   | 없음   | `{provider:"google", token:idToken}`(`authorizationCode` · `name` 필드는 보내지 않음) | `{accessToken, refreshToken, newUser, user}`                                                                                              |
| 갱신    | `POST api/v1/auth/refresh` | 없음   | `{refreshToken}`                                                        | `{accessToken, refreshToken}`                                                                                                             |
| 내 정보 | `GET api/v1/me`            | Bearer |                                                                         | `{id, provider, email?, nickname?, nicknameConfirmed, termsAcceptedAt?, termsVersion?, pushOptIn, profileImageUrl?, currentTermsVersion}` |
| 가입    | `PATCH api/v1/me`          | Bearer | `{nickname, acceptTerms:{version}, pushOptIn}`                          | user 객체                                                                                                                                 |

- 공통 래퍼 `{code, message, data}`, 성공 `code == "SUCCESS"`. 에러 본문 `{code?, message?}`. 인증 요청은 재시도하지 않는다.
- `isEligible` = `currentTermsVersion` 비어 있지 않음 ∧ `nicknameConfirmed` ∧ `termsAcceptedAt != null` ∧ `termsVersion == currentTermsVersion`(iOS `AuthProfile.swift:10-13`).
- `authorizationCode` · `name`: Android Credential Manager(`GetGoogleIdOption`)는 ID 토큰만 준다. 서버 DTO(`social-login.dto.ts`)는 `authorizationCode`가 `undefined`가 아니면(`null` 포함) 문자열 검증을 하므로 필드 자체를 생략한다. 요청 JSON은 `explicitNulls = false`로 null 필드를 직렬화하지 않고, `AuthLoginBody` 직렬화 결과에 두 키가 없음을 단위 테스트로 고정한다.

## Google 로그인

- `androidx.credentials` + `credentials-play-services-auth` + `googleid`로 `GetGoogleIdOption(serverClientId = GOOGLE_SERVER_CLIENT_ID, filterByAuthorizedAccounts = false)` → `GoogleIdTokenCredential.idToken`.
- `GOOGLE_SERVER_CLIENT_ID`는 iOS `Config/{Stage,Prod}.xcconfig`의 `GOOGLE_CLIENT_ID`(웹 클라이언트 ID, 공개 값)를 `app/config/{stage,prod}.properties`에 넣고 `BuildConfig`로 주입한다. `LEGAL_BASE_URL`도 같은 방식.
- `GetCredentialCancellationException` → `UserCancelled`, 진행 중 중복 호출 → `RequestInProgress`, 그 외 → 실패 문구. 로그인 후 Credential Manager 상태 정리(`clearCredentialState`)로 iOS의 Google `signOut()`과 맞춘다.
- `GoogleSignInClient`는 Activity 컨텍스트가 필요하므로 domain 계약은 `suspend fun signIn(): GoogleIdToken`, 구현은 app이 현재 Activity를 제공하는 `ActivityProvider`를 주입한다.

## 토큰 · 세션

- `CredentialStore`는 `{accessToken, refreshToken}`을 JSON 하나로 묶어 `SecureStorage` 한 키에 저장한다(iOS `CREDENTIAL_INTEGRITY`가 막는 반쪽 쌍 문제를 원자 저장으로 해결). 복호화 실패 · 손상은 비로그인으로 취급하고 지운다.
- 서버 `POST api/v1/auth/refresh`는 refresh 토큰을 회전하고 이전 토큰을 바로 폐기한다. 그래서 갱신은 `service/auth`의 `TokenRefresher` 한 경로로만 하고 `Mutex`로 직렬화한다(Ktor `Auth` 플러그인의 `refreshTokens`는 이 경로만 호출한다).
- `CredentialStore`는 저장 값마다 `sessionId`(로그인 저장 때만 새로 발급하는 UUID)와 `revision`(같은 세션 안 갱신마다 증가)을 둔다. 삭제는 세션을 없앤다. 인증 요청은 보낼 때 쓴 `(sessionId, revision)`을 기억한다(iOS `.sessionBound(UUID)` · `AuthorizingInterceptor`와 같은 의미).
- 401을 받은 요청은 Mutex 안에서: 현재 `sessionId`가 요청 때와 다르거나 세션이 없으면(계정 교체 · 로그아웃 · 만료) 재전송하지 않고 `NotAuthenticated`로 실패시킨다(이전 계정 요청이 새 계정 토큰으로 나가지 않는다). 같은 `sessionId`에서 `revision`만 올랐으면(다른 요청이 이미 갱신) refresh 없이 현재 토큰으로 한 번 재요청한다. 둘 다 같을 때만 refresh를 부른다.
- 요청을 보내기 전에도 호출자가 세션에 묶인 요청(가입 `PATCH /me` 등)은 기대 `sessionId`를 넘기고, 현재 세션과 다르면 보내지 않는다.
- refresh 결과 저장과 실패 시 삭제는 `compareAndSet(expectedSessionId, expectedRevision)`으로만 한다. 그 사이 로그인 교체 · 삭제가 있었으면 결과를 버린다(늦게 끝난 refresh가 새 세션을 덮지 않는다).
- refresh가 401이고 revision이 그대로면 저장소를 지우고 `SessionExpiryEvents`에 보낸다. refresh가 오프라인 · 5xx면 세션을 유지하고 원 요청은 오프라인 · 서버 오류로 실패한다.
- 테스트(MockEngine): 동시 401 두 요청 → refresh 1회 · 두 요청 모두 새 토큰으로 성공, refresh 중 새 로그인 저장 → refresh 결과 폐기, refresh 401 → 만료 이벤트 1회, refresh 오프라인 → 세션 유지, 계정 A 요청 401 뒤 계정 B 로그인 → A 요청은 재전송 없이 실패하고 B 토큰으로 나간 요청이 없음.
- 저장 계정과 응답 사용자 ID 비교(iOS `SESSION_USER_ID`)는 가입 응답 검증에만 쓴다.

## 구현 순서 (PR 단위)

| PR  | 내용                                                                                                        | 검증                                                 |
| --- | ----------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| L1  | `core/network` + `core/datastore` + Manifest `INTERNET`                                                     | MockEngine 래퍼 · 에러 매핑, Robolectric 저장소 왕복 |
| L2  | `service/api` + `service/auth`                                                                              | 갱신 · 만료 이벤트 MockEngine 테스트                 |
| L3  | `domain/auth` (interface · sources, Credential Manager 구현)                                                | 닉네임 규칙 · isEligible · Repository 매핑 테스트    |
| L4  | `feature/onboarding`                                                                                        | ViewModel 테스트, 프리뷰                             |
| L5  | `app` 스플래시 · 루트 Coordinator · 메인 탭 껍데기(Onboarding 연결, launchLogin 자리는 비로그인 시 빈 덮개) | Coordinator 상태 전이 테스트                         |
| L6  | `feature/auth` launchLogin · SignUp + app 연결 · 포그라운드 재확인                                          | ViewModel 테스트, stage 계약 수동 확인 기록          |

각 PR은 `verify --all` · CI 통과 후 squash merge. 끝나면 새 Codex 세션 inspect(수정 최대 2회).

## 위험 · 결정

1. **Android OAuth 클라이언트 미등록**: Google Cloud 프로젝트에 Android 클라이언트(패키지 `io.festiCore.co.stage`/`io.festiCore.co` + 서명 SHA-1)가 없으면 Credential Manager가 실패한다. 서버가 웹 클라이언트 ID를 `aud`로 검증하면 서버 쪽 변경은 필요 없을 것으로 보지만 확인 전이다. 코드는 이와 무관하게 완성하고, 등록은 사용자 조치로 보고한다.
2. **스플래시 Lottie 생략**: 새 의존성(라이선스 검토 대상)을 피하려고 정적 로고. `ui/animation` 단계에서 맞춘다.
3. **약관 상세 외부 브라우저**: `feature/web` 단계에서 앱 안 화면으로 바꾼다.
4. **분석 이벤트 미전송**: `uiAction(login_google, login)`, `identify(userId, google)`, `signUp(google)`(newUser일 때)는 `service/analytics` 단계에서 붙인다. 붙일 지점은 LoginViewModel 버튼 탭 · 로그인 확정 직후다.
5. **신규 가입 후 기수 설정 생략**: iOS는 가입 직후 bearer 설정으로 가지만 이 단계는 메인 탭으로 간다. `feature/bearer` 단계에서 잇는다.
6. 새 의존성: `androidx.credentials`, `credentials-play-services-auth`, `googleid`, `core-splashscreen`, Ktor `auth` · `okhttp` · `content-negotiation` · `mock`(테스트). 모두 Google · JetBrains Apache-2.0.
