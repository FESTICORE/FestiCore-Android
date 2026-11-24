---
spec: analytics
modules:
  [
    service/analytics/interface,
    service/analytics/sources,
    app,
    feature/auth,
    feature/festival,
    feature/profile,
    feature/timetable,
    feature/web,
  ]
---

# 분석(Mixpanel) Spec

> 앱 시작 때 토큰이 있으면 Mixpanel을 한 번 시작하고, 화면 · 버튼 · 깃발 팔로우 · 타임테이블 · 팩 동기화 이벤트를 iOS와 같은 이름 · 속성으로 보낸다. 토큰이 없으면 아무것도 만들거나 보내지 않는다.

iOS 대응: `Projects/Service/FestiCoreAnalytics/{Interface/Event,Interface/UseCase,Sources/Configuration,Sources/UseCase}`, 호출처 `App/Sources/{Application/AppDelegate+Configuration.swift:16-18,Navigation/Auth/AuthCoordinator.swift:469-476,Navigation/Account/AccountCoordinator.swift:205,213}` 외 feature 16곳.
설계: `.claude/docs/specs/2026-11-24-ios-structure-alignment-r6-design.md`(4 · 5 · 6.1 · R6b).

**사용자 결정: Sentry 제외.** iOS `SentryConfiguration` · `SentryLogSink` · `FestiCoreTracedView`와 `identify` · `track` · `reset` 안의 Sentry 호출(`setUser` · breadcrumb · `user.action.count`)은 옮기지 않는다. 계약(`AnalyticsUseCase` · 이벤트 타입)은 iOS와 같고, Android 구현은 그 자리에서 아무 데도 보내지 않는다. Sentry 라이브러리 · DSN · Gradle 플러그인(매핑 업로드)도 넣지 않는다.

## 동작 규칙

1. 시작 순서(`FestiCoreApplication.onCreate`): `FirebaseConfiguration.configure` → `FestiCoreAnalyticsConfiguration.configure(context, BuildConfig.MIXPANEL_TOKEN)` → Koin 시작 → `AnalyticsUseCase.registerBaseProperties()`. iOS는 Koin이 없어 설정 직후 부른다.
2. 토큰(`FESTICORE_MIXPANEL_TOKEN`, 환경 변수 → `local.properties` → 빈 값)은 앞뒤 공백을 지운 값이 비어 있지 않을 때만 쓴다. 비면 `MixpanelAPI.getInstance`를 부르지 않는다. 이후 모든 호출은 아무것도 하지 않는다(iOS `Mixpanel.safeMainInstance() == nil`).
3. Mixpanel은 `trackAutomaticEvents = false`로 한 번만 만든다.
4. 공통 슈퍼 프로퍼티: `os_type`(`android`) · `app_version`(versionName) · `build`(versionCode). 읽지 못하면 빈 문자열.
5. `identify(userId, method)`: 빈 ID는 무시. `identify(userId)` → method가 있으면 `people.set("provider", method)` → 슈퍼 프로퍼티 `is_logged_in = true`(+ `login_provider`).
6. `reset()`: `reset()` 후 공통 슈퍼 프로퍼티만 다시 등록한다(`is_logged_in` 없음).
7. `track(event)`: 이름 · 속성은 아래 표와 같고, 디버그 로그(`FestiCoreLogger.debug`, sink로 가지 않는 단계)에 이름 · 속성을 남긴다.
8. 옵트아웃: iOS에 없어 Android도 두지 않는다.

| 이벤트            | 이름               | 속성                                                           |
| ----------------- | ------------------ | -------------------------------------------------------------- |
| `SignUp`          | `sign_up`          | `method`                                                       |
| `ScreenView`      | `screen_view`      | `screen`, `referrer`(있을 때만)                                |
| `UiAction`        | `ui_action`        | `action`, `screen`                                             |
| `FlagFollow`      | `flag_follow`      | `flag_id`, `is_following`                                      |
| `TimetableAction` | `timetable_action` | `action`, `festival_id`, `set_id`(있을 때만)                   |
| `PackSync`        | `pack_sync`        | `festival_id`, `result`(`updated` · `not_modified` · `failed`) |

### 허용 속성 목록 (개인정보 · 토큰 금지)

- 이벤트 속성 키는 위 표의 9개(`method` · `screen` · `referrer` · `action` · `flag_id` · `is_following` · `festival_id` · `set_id` · `result`)만 쓴다. 값은 enum 문자열 · 서버 ID · Boolean뿐이다.
- 사용자 ID는 `identify` 인자로만 보낸다. 이벤트 속성 · 로그에 넣지 않는다.
- 이메일 · 닉네임 · 프로필 사진 · 토큰 · 인가 코드 · 위치 · URL은 어떤 호출에도 넣지 않는다. 새 속성은 이 목록을 먼저 고치고 리뷰(`REVIEW.md` 항상 확인)에서 본다.

### 호출처 (iOS file:line → Android)

| iOS                                                         | Android                                                                                                               |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `AppDelegate+Configuration.swift:17-18`                     | `FestiCoreApplication.onCreate`                                                                                       |
| `AuthCoordinator.swift:469-476` identify · sign_up          | `LaunchLoginViewModel.acceptLogin`(로그인 성공 직후, 계정 확인 전)                                                    |
| `AccountCoordinator.swift:205,213` reset                    | `AppCoordinator.onAccountOperationFinished`(`Succeeded` · `SessionLost`)                                              |
| `LoginFeature.swift:119` ui_action login                    | `LaunchLoginViewModel.onSignInTapped`(바로 로그인 `onSignInRequested`는 남기지 않음, iOS `AuthCoordinator.swift:181`) |
| `WebFeature.swift:72`                                       | `WebViewModel.onAppear`(첫 로드만)                                                                                    |
| `AccountSettingsFeature.swift:122,130`                      | `AccountSettingsViewModel.onWithdrawTapped` · `onLogoutTapped`                                                        |
| `ProfileFeature.swift:226`                                  | `ProfileViewModel.onAppear`                                                                                           |
| `FestivalFlagFeature.swift:142`                             | `FlagViewModel.onAppeared`                                                                                            |
| `FestivalFlagDirectory.swift:270`                           | `FlagViewModel.onFollowResponse` 성공                                                                                 |
| `FestivalFlagDetailFeature.swift:528`                       | `FlagDetailViewModel` 팔로우 성공                                                                                     |
| `FestivalFeature.swift:882,895`                             | `FestivalListViewModel.onAppear` · `LiveBoardViewModel.onAppear`                                                      |
| `FestivalFeature.swift:1685,1723`                           | `FestivalListViewModel.onPackDownloadFinished` · `onPackDownloadFailed`                                               |
| `TimetableFeature.swift:355,376,394` · `+Schedule.swift:73` | `TimetableViewModel.onClockStarted` · `onDaySelected` · `onMineOnlyToggled` · `onScheduleToggled`                     |

## 엣지 케이스

- 토큰이 공백뿐이면 토큰 없음과 같다.
- `configure`를 두 번 불러도 Mixpanel은 하나다.
- 계정 작업 중 로그인을 잃은 경우: iOS는 `isLoggedIn == false`면 `sessionExpired`로 가 reset하지 않고, `.notAuthenticated`로 끝나면 reset한다. Android `AccountCoordinator`는 둘을 `SessionLost` 하나로 합쳐 두 경우 모두 reset한다(계정 분리 쪽).
- 날짜 선택은 다른 날을 고르고 축제가 있을 때만, 내 일정 토글은 실제로 넣거나 뺐고 축제가 있을 때만 남긴다(iOS와 같음).

## 제약 / 가정

- Mixpanel SDK import는 `service/analytics/sources`에서만(check-boundaries). feature는 `:service:analytics:interface`만 의존할 수 있다(설계 쟁점 F2).
- 토큰은 추적 파일에 두지 않는다. PR CI에는 넣지 않는다. 릴리스 CI 비밀 등록은 사람이 한다.

## 작업 모듈

| 모듈                                                    | 계층    | 변경 내용                                                    |
| ------------------------------------------------------- | ------- | ------------------------------------------------------------ |
| `service/analytics/interface`                           | service | 이벤트 타입 · `AnalyticsUseCase` · `PreviewAnalyticsUseCase` |
| `service/analytics/sources`                             | service | `configuration/` · `usecase/` · `di/serviceAnalyticsModule`  |
| `service/serviceassembly`                               | service | Koin 목록에 추가                                             |
| `app` · `build-logic`                                   | app     | 시작 순서 · `BuildConfig.MIXPANEL_TOKEN` · 계정 reset        |
| `feature/{auth,festival,profile,timetable,web}/sources` | feature | 호출처 이식                                                  |

## 영향 범위

| 모듈                               | 계층   | 영향 이유                                   |
| ---------------------------------- | ------ | ------------------------------------------- |
| `.claude/scripts/check-boundaries` | 하네스 | SDK 경계 · feature 예외 · sources 조립 규칙 |

## 미해결 질문

- 키를 넣은 stage 빌드에서 Mixpanel 대시보드 수신 확인(사람).

## 변경 이력

- #208 Mixpanel 분석 모듈과 iOS 호출처 이식(Sentry 제외)
