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

# 분석(Mixpanel · Sentry) Spec

> 앱 시작 때 토큰이 있으면 Mixpanel을 한 번 시작하고, 화면 · 버튼 · 깃발 팔로우 · 타임테이블 · 팩 동기화 이벤트를 iOS와 같은 이름 · 속성으로 보낸다. 해당 SDK 설정이 없으면 그 SDK를 만들거나 호출하지 않는다. Mixpanel과 Sentry는 독립적으로 동작한다.

iOS 대응: `Projects/Service/FestiCoreAnalytics/{Interface/Event,Interface/UseCase,Sources/Configuration,Sources/UseCase}`, 호출처 `App/Sources/{Application/AppDelegate+Configuration.swift:16-18,Navigation/Auth/AuthCoordinator.swift:469-476,Navigation/Account/AccountCoordinator.swift:205,213}` 외 feature 16곳.
설계: `.claude/docs/specs/2026-11-24-ios-structure-alignment-r6-design.md`(4 · 5 · 6.1 · R6b).

**2026-11-25 사용자 결정: Sentry 초기화 추가.** Android 전용 `festicore-android` 프로젝트의 DSN이 유효하면 오류 수집과 20% 성능 추적을 시작한다. DEBUG와 stage flavor는 `stage`, prod release만 `production` 환경으로 보낸다. 개인정보 자동수집 · 스크린샷 · 뷰 계층 · Replay는 끈다. `setUser` · 수동 `user.action` breadcrumb · `user.action.count` · Logs sink를 연결한다. R8 매핑 업로드는 아직 구성하지 않았다. SDK 기본 자동 breadcrumb(생명주기 · 시스템 이벤트 등)는 활성 상태다.

## 동작 규칙

1. 시작 순서(`FestiCoreApplication.onCreate`): `FirebaseConfiguration.configure` → `FestiCoreAnalyticsConfiguration.configure`(DSN · DEBUG · IS_STAGE · 버전으로 Sentry 초기화 → 토큰으로 Mixpanel 초기화) → Koin 시작 → `AnalyticsUseCase.registerBaseProperties()`. iOS는 Koin이 없어 설정 직후 부른다.
2. 토큰(`FESTICORE_MIXPANEL_TOKEN`, 환경 변수 → `local.properties` → 빈 값)은 앞뒤 공백을 지운 값이 비어 있지 않을 때만 쓴다. 비면 `MixpanelAPI.getInstance`를 부르지 않는다. 이후 Mixpanel 호출만 무시한다(iOS `Mixpanel.safeMainInstance() == nil`). Sentry는 유효한 설정과 활성 SDK를 모두 확인한 뒤 독립적으로 호출한다.
3. Mixpanel은 `trackAutomaticEvents = false`로 한 번만 만든다.
4. 공통 슈퍼 프로퍼티: `os_type`(`android`) · `app_version`(versionName) · `build`(versionCode). 읽지 못하면 빈 문자열.
5. `identify(userId, method)`: 빈 ID는 무시. `identify(userId)` → method가 있으면 `people.set("provider", method)` → 슈퍼 프로퍼티 `is_logged_in = true`(+ `login_provider`). Sentry에는 같은 ID와 선택적 `data.login_provider`만 설정하며 Mixpanel 토큰이 없어도 동작한다.
6. `reset()`: Mixpanel `reset()` 후 공통 슈퍼 프로퍼티만 다시 등록한다(`is_logged_in` 없음). Sentry user도 null로 지운다(Mixpanel 토큰 유무와 독립).
   - 세션을 잃으면 사용자를 분리한다. `AppCoordinator`가 현재 사용자를 비우는 모든 경로(`forgetCurrentUser`)에서 reset한다: 로그아웃 · 탈퇴 성공, 계정 작업 중 로그인 잃음, 세션 만료, 재확인 401, 로그인 직후 계정 확인 401(첫 진입 · 로그인 시트). 다른 세션의 오래된 결과로 버리는 경우는 reset하지 않는다.
   - iOS와 다른 점: iOS는 `AccountCoordinator.swift:205,213`에서만 reset하고 `SessionCoordinator.swift:196-224`(세션 만료) · `:341-351`(프로필 401) · `:430-435`(약관 저장 401)에서는 하지 않는다. iOS도 Android도 계정 확인 전에 identify(`AuthCoordinator.swift:469`)하므로, 확인이 401로 끝나면 distinct ID · `is_logged_in`이 남는다. Android는 이것을 남기지 않는다.
7. `track(event)`: 이름 · 속성은 아래 표와 같고, 디버그 로그(`FestiCoreLogger.debug`, sink로 가지 않는 단계)에 이름 · 정렬된 속성 키만 남긴다. Sentry INFO breadcrumb(category=`user.action`)에는 이름과 같은 속성을, `user.action.count`에는 값 1과 `event` 이름 및 문자열 속성만 보낸다(Boolean 제외).
8. 앱 Logs sink는 같은 인스턴스를 한 번만 등록한다. NETWORK는 URL/응답이 포함될 수 있어 제외하고 INFO→INFO, NOTICE→WARN, ERROR→ERROR, FAULT→FATAL로 전달한다. 속성은 category만 보낸다. 기존 전역 최소 수준을 유지하므로 INFO는 debug에서만 외부로 전달하고 release는 NOTICE 이상이다. SDK 비활성/설정 없음이면 수동 SDK 호출을 하지 않는다.
9. 옵트아웃: iOS에 없어 Android도 두지 않는다.

| 이벤트            | 이름               | 속성                                                           |
| ----------------- | ------------------ | -------------------------------------------------------------- |
| `SignUp`          | `sign_up`          | `method`                                                       |
| `ScreenView`      | `screen_view`      | `screen`, `referrer`(있을 때만)                                |
| `UiAction`        | `ui_action`        | `action`, `screen`                                             |
| `FlagFollow`      | `flag_follow`      | `is_following`                                      |
| `TimetableAction` | `timetable_action` | `action`                   |
| `PackSync`        | `pack_sync`        | `result`(`updated` · `not_modified` · `failed`) |

### 허용 속성 목록 (개인정보 · 토큰 금지)

- 이벤트 속성 키는 위 표의 6개(`method` · `screen` · `referrer` · `action` · `is_following` · `result`)만 쓴다. 값은 enum 문자열 · Boolean뿐이다. iOS와 맞추어 flag_id/festival_id/set_id를 Mixpanel과 Sentry 양쪽에서 제외한다. 기존 ID 기반 대시보드에는 더 이상 이 속성이 제공되지 않는다.
- 사용자 ID는 Mixpanel `identify`와 Sentry user.id로 설정한다. 앱이 만드는 이벤트 속성·로그 메시지에는 넣지 않는다. Sentry SDK는 Logs/metrics에 scope의 사용자 ID 또는 설치 식별자를 자동으로 붙일 수 있다. 이 동작은 iOS와 동일하게 유지하며 사용자 식별 진단 데이터 수집에 포함한다.
- 이메일 · 닉네임 · 프로필 사진 · 토큰 · 인가 코드 · 위치 · URL은 어떤 호출에도 넣지 않는다. 새 속성은 이 목록을 먼저 고치고 리뷰(`REVIEW.md` 항상 확인)에서 본다.

### 호출처 (iOS file:line → Android)

| iOS                                                         | Android                                                                                                               |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `AppDelegate+Configuration.swift:17-18`                     | `FestiCoreApplication.onCreate`                                                                                       |
| `AuthCoordinator.swift:469-476` identify · sign_up          | `LaunchLoginViewModel.acceptLogin`(로그인 성공 직후, 계정 확인 전)                                                    |
| `AccountCoordinator.swift:205,213` reset                    | `AppCoordinator.forgetCurrentUser`(계정 작업 끝 · 세션 만료 · 재확인 401 · 로그인 직후 401, 규칙 6)                                              |
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
- 계정 작업 중 로그인을 잃은 경우: iOS는 `isLoggedIn == false`면 `sessionExpired`로 가 reset하지 않고, `.notAuthenticated`로 끝나면 reset한다. Android는 `SessionLost` 하나로 합쳐 두 경우 모두 reset한다(규칙 6).
- 날짜 선택은 다른 날을 고르고 축제가 있을 때만, 내 일정 토글은 실제로 넣거나 뺐고 축제가 있을 때만 남긴다(iOS와 같음).

## 제약 / 가정

- Mixpanel · Sentry SDK import는 `service/analytics/sources`에서만(check-boundaries). feature는 `:service:analytics:interface`만 의존할 수 있다(설계 쟁점 F2).
- 토큰은 추적 파일에 두지 않는다. PR CI에는 넣지 않는다. 릴리스 CI 비밀 등록은 사람이 한다.

## 작업 모듈

| 모듈                                                    | 계층    | 변경 내용                                                    |
| ------------------------------------------------------- | ------- | ------------------------------------------------------------ |
| `service/analytics/interface`                           | service | 이벤트 타입 · `AnalyticsUseCase` · `PreviewAnalyticsUseCase` |
| `service/analytics/sources`                             | service | `configuration/` · `usecase/` · `di/serviceAnalyticsModule`  |
| `service/serviceassembly`                               | service | Koin 목록에 추가                                             |
| `app` · `build-logic`                                   | app     | 시작 순서 · `BuildConfig.MIXPANEL_TOKEN` · `BuildConfig.SENTRY_DSN` · 계정 reset        |
| `feature/{auth,festival,profile,timetable,web}/sources` | feature | 호출처 이식                                                  |

## 영향 범위

| 모듈                               | 계층   | 영향 이유                                   |
| ---------------------------------- | ------ | ------------------------------------------- |
| `.claude/scripts/check-boundaries` | 하네스 | SDK 경계 · feature 예외 · sources 조립 규칙 |

## 미해결 질문

- 키를 넣은 stage 빌드에서 Mixpanel 대시보드 수신 확인(사람).

## 변경 이력

- #235 Sentry 사용자 분리 · 동작 breadcrumb/count · NETWORK 제외 Logs sink 연결. 양쪽 SDK 이벤트의 서버 ID 속성을 iOS처럼 제외하고 debug 로그도 값 대신 키만 남긴다.

- #235 Android 전용 `festicore-android` 프로젝트 DSN 초기화와 오류 · 성능 수집 추가. HTTPS · 공개 키 · 호스트 · 숫자 프로젝트 경로를 검증하고 잘못된 DSN은 시작하지 않는다. DEBUG 또는 Stage는 stage, prod release는 production 환경이다.

- #208 세션을 잃는 모든 경로에서 분석 사용자 분리(리뷰 R6-REV-2)
- #208 Mixpanel 분석 모듈과 iOS 호출처 이식(Sentry 제외)
