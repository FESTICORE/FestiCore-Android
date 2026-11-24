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
FESTICORE_MIXPANEL_TOKEN=
FESTICORE_GOOGLE_ROUTES_API_KEY=
```

### 계정 · 콘솔 작업

- **Firebase**: Android 앱 등록(`io.festiCore.co`, `io.festiCore.co.stage`) 후 위 Firebase 값 확인. 푸시(P0)에 필요.
- **Google Cloud Routes API**: API 사용 설정 · 결제 계정 등록 · Google Maps Platform 약관 동의 · 키 발급(Android 앱 제한: 패키지 이름 + 서명 SHA-1).
- **푸시 서버(P0)**: 백엔드 Railway develop · production에 `FCM_SERVICE_ACCOUNT_JSON` 등록, 백엔드 main 승격(사람만).
- **Play Console 데이터 보안 · 개인정보처리방침**: 위치(귀가 카드, Google Routes API로 좌표 전송 — 제3자 제공), 분석(Mixpanel, 개인정보 없이 이벤트 · 사용자 ID).
- **Sentry**: 사용하지 않는다(2026-11-24 결정). 값 필요 없음.
