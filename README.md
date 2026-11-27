# FestiCore Android

축제 목록, 공연 시간표와 현장 정보를 한곳에서 확인하는 FestiCore의 Android 앱입니다.
## 사람이 준비할 값과 할 일

비밀 값은 저장소에 넣지 않는다. 로컬은 `local.properties`(추적 안 함), CI는 GitHub Actions 비밀(환경 변수)로 넣는다. 환경 변수가 있으면 먼저 쓰고 없으면 local.properties를 읽는다. 값이 없으면 빌드는 되고 해당 기능만 꺼진다(`build-logic/src/main/kotlin/festicore.android.application.gradle.kts`의 `secretValue`).

### 키 · 값

| 이름                                                                          | 쓰는 곳                                                                     | 없을 때                                            |
| ----------------------------------------------------------------------------- | --------------------------------------------------------------------------- | -------------------------------------------------- |
| `FESTICORE_FIREBASE_STAGE_API_KEY` · `_APP_ID` · `_PROJECT_ID` · `_SENDER_ID` | `service/config` Firebase 초기화(stage)                                     | Firebase를 켜지 않음(로그 한 줄)                   |
| `FESTICORE_FIREBASE_PROD_API_KEY` · `_APP_ID` · `_PROJECT_ID` · `_SENDER_ID`  | 같은 것(prod)                                                               | 같음                                               |
| `FESTICORE_MIXPANEL_TOKEN`                                                    | `service/analytics` Mixpanel(stage · prod 같은 이름)                        | Mixpanel을 만들지 않고 아무것도 보내지 않음        |
| `FESTICORE_SENTRY_DSN` | `service/analytics` 오류 · 성능 수집 | 유효한 HTTPS DSN이 없으면 시작하지 않음 |
| `FESTICORE_GOOGLE_ROUTES_API_KEY`                                             | `core/location` 라이브 보드 귀가 카드 도보 · 자동차 시간(Google Routes API) | 직선거리로 도보 시간만 어림, 자동차 시간은 안 보임 |

`local.properties` 예시(값은 직접 채운다):

```properties
FESTICORE_FIREBASE_STAGE_API_KEY=
FESTICORE_FIREBASE_STAGE_APP_ID=
FESTICORE_FIREBASE_STAGE_PROJECT_ID=
FESTICORE_FIREBASE_STAGE_SENDER_ID=
FESTICORE_FIREBASE_PROD_API_KEY=
FESTICORE_FIREBASE_PROD_APP_ID=
FESTICORE_FIREBASE_PROD_PROJECT_ID=
FESTICORE_FIREBASE_PROD_SENDER_ID=
FESTICORE_SENTRY_DSN=
FESTICORE_MIXPANEL_TOKEN=
FESTICORE_GOOGLE_ROUTES_API_KEY=
```

### 계정 · 콘솔 작업

- **Firebase**: Stage/Prod별 등록과 패키지 일치 여부를 확인한다. 설정 네 값이 모두 있을 때만 초기화한다. 푸시(P0) 수신 구현은 아직 별도 작업이다.
- **Google Cloud Routes API**: API 사용 설정 · 결제 계정 등록 · Google Maps Platform 약관 동의 · 키 발급(Android 앱 제한: 패키지 이름 + 서명 SHA-1).
- **푸시 서버(P0)**: 백엔드 Railway develop · production에 `FCM_SERVICE_ACCOUNT_JSON` 등록, 백엔드 main 승격(사람만).
- **Play Console 데이터 보안 · 개인정보처리방침**: 위치(귀가 카드, Google Routes API로 좌표 전송 — 제3자 제공), 분석(Mixpanel, 이벤트 · 사용자 ID), 오류 · 진단 · 성능(Sentry, Logs/metrics에 사용자 ID 또는 설치 식별자가 자동 첨부될 수 있음).
- **Sentry**: `FESTICORE_SENTRY_DSN`으로 초기화한다. 비거나 잘못된 HTTPS DSN이면 시작하지 않는다. DEBUG는 `stage`, prod release는 `production`으로 구분한다. 사용자 식별·로그아웃 초기화, `user.action` breadcrumb와 `user.action.count`, 앱 로그를 iOS와 같은 방식으로 연결한다. NETWORK 로그와 분석 이벤트의 서버 ID는 보내지 않는다. 앱 로그는 디버그에서 INFO 이상, 릴리스에서 NOTICE 이상을 전달한다. R8 매핑 업로드는 아직 구성하지 않아 난독화 릴리스의 원본 스택 복원은 미검증이다.

### Firebase · Sentry 설정

앱 ID는 기존 `io.festiCore.co`를 유지한다. Firebase 등록과 OAuth/Routes의 패키지·서명 제한은 이 ID에 맞는지 별도로 확인해야 한다. 키를 새로 발급하거나 등록을 변경하지 않는다.

`FESTICORE_SENTRY_DSN`은 환경 변수 또는 추적하지 않는 `local.properties`에 완전한 `https://public-key@host/project-id` 형식으로 넣는다. Android 전용 `festicore-android` 프로젝트를 사용하며 디버그 이벤트는 `stage`로 구분한다. iOS 프로젝트의 DSN과 혼용하지 않는다. 크래시 로그 · 기기 진단 · 성능 추적을 Sentry로 전송하므로 Play Console 데이터 보안 신고에도 반영해야 한다. 개인정보 자동수집은 끄며 SDK 설정만으로 오류 메시지 속 개인정보까지 제거되지는 않는다.

런타임 Sentry DSN과 Mixpanel 토큰은 심볼 업로드용 `SENTRY_AUTH_TOKEN`과 별개이며, 업로드 토큰을 APK에 넣지 않는다.
