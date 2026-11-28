# Android 분석·진단 수집표 (PR #240)

구현 `8c8e656`, UI/테스트 보완 `e35c704`, 디자인 후속 `33de338`, Stage Debug `1.0.0 (1)` 설치·verify 1,522 tests 성공. 빌드 상세와 실제 동의 OFF→ON→재실행→철회→재실행 증거는 [LOCAL-GAPS.md](LOCAL-GAPS.md)에 기록한다. SDK 호출·설정과 외부 대시보드 수신은 별도 증거다. 아래 항목은 코드 기준이며 운영 수신/보유기간/계약을 확인했다는 뜻이 아니다.

| 항목 | Firebase | Mixpanel | Sentry |
|---|---|---|---|
| SDK | Firebase BoM 35.2.0 / firebase-analytics 24.0.0 | mixpanel-android 8.12.0 | sentry-android 8.65.1 |
| 제품/기능 | Analytics만 추가. FCM·Crashlytics SDK/수집은 이 변경에 없음 | 수동 행동 이벤트·사용자 연결·공통 속성 | 오류/크래시·20% 성능 trace·수동 breadcrumb/count/Logs. Replay/스크린샷/뷰 계층 OFF |
| 시작 조건 | 기기 동의 + 해당 flavor 네 Firebase 값 + 기본 FirebaseApp | 기기 동의 + 기존 토큰 | 기기 동의 + 유효한 기존 DSN |
| 수동 이벤트 | sign_up, screen_view, ui_action, flag_follow, timetable_action, pack_sync | 동일 | 같은 이벤트 breadcrumb, user.action.count |
| 이벤트 속성 | method, screen_name(screen_view만), screen(기타), referrer, action, is_following(1/0), result | method, screen, referrer, action, is_following(Boolean), result | 같은 허용 속성; count는 문자열만 |
| 공통 속성/식별자 | os_type/app_version/build, app instance ID, 동의 후 로그인 시 내부 user ID·login_provider | os_type/app_version/build, SDK distinct/device ID, 내부 user ID, provider/is_logged_in/login_provider | 내부 user.id·선택적 login_provider, SDK 설치/세션 ID, scope 식별자가 Logs/metrics에 붙을 수 있음 |
| 자동 수집 | 자동 화면 보고 OFF, 광고 ID·광고 저장·광고 사용자 데이터·개인화 OFF. 동의 후 세션/참여 등 SDK 기본 이벤트·앱/기기 메타데이터 가능 | trackAutomaticEvents=false. SDK 기본 앱/OS/기기·네트워크 속성은 유지; IP 기반 위치 기본 동작을 변경하지 않았음. opt-in 시 SDK 자체 이벤트 가능 | sendDefaultPii=false, 사용자 상호작용/시스템 이벤트 breadcrumb OFF; 생명주기 breadcrumb·오류 stack/context·기기 정보는 유지. 이것이 개인정보 0을 뜻하지 않음 |
| endpoint/region 근거 | 앱에서 endpoint/보관 region을 지정하지 않음. Google 프로젝트 계약·콘솔 확인 필요 | 코드에 override 없음; 설치 SDK 기본 https://api.mixpanel.com. 실제 프로젝트 보관 region/계약 미확인 | 원본 Android 로컬 DSN은 EU ingest 호스트 형식임을 값 노출 없이 확인. 조직의 실제 보관/계약 지역은 미확인 |
| 철회 | 수집 OFF + ANALYTICS_STORAGE DENIED + 광고 동의 DENIED + resetAnalyticsData | optOutTracking으로 향후 추적 중지 및 SDK 로컬 대기 데이터 제거 | 전역 게이트 OFF, 오류/trace/log beforeSend 거부, Sentry.close |
| 로그아웃/탈퇴 | user ID·provider null, resetAnalyticsData 후 공통 속성 재등록 | reset 후 공통 속성 재등록 | user null |
| 서버 삭제 | reset은 서버 삭제 API가 아님. 관리자/사업자 삭제 절차 별도 확인 필요 | 계정 탈퇴가 Mixpanel 서버 데이터 삭제를 호출하지 않음 | 계정 탈퇴가 Sentry 서버 데이터 삭제를 호출하지 않음 |
| 실제 수신 | 미검증: Stage 키 없음 | 미검증: 원본 토큰 존재, 격리 작업 빌드에 미주입 | 미검증: 원본 DSN 존재, 격리 작업 빌드에 미주입 |

앱 코드가 이벤트/로그에 이메일·닉네임·사진·인증 토큰·인가 코드·GPS 좌표·URL을 추가하지 않는다. HTTP 전송 과정의 IP 처리, SDK 기본 메타데이터, 오류 내용의 우발적 개인정보 가능성은 앱의 PII 옵션만으로 부정할 수 없다. 실제 계약·처리 국가·보유 기간·삭제 요청 절차는 담당자의 별도 확인이 필요하다. 앱 동의 철회는 백엔드 오류 수집을 변경하지 않는다.

## 동의와 환경별 로그

- 설정 → 선택적 분석·진단 → 분석·진단 공유. 기본 OFF, 핵심 기능 이용과 무관. 기기 저장 Boolean이며 로그인 계정 간 자동 승계/서버 동의 API는 없다.
- 허용 저장이 실패하면 켜지지 않는다. 철회 저장 실패에도 현재 프로세스는 멈추며 화면에 실패를 알린다. 재시작 시 이전 저장값일 가능성이 있다.
- 동의 이전 이벤트/사용자 식별을 재전송하지 않는다. 이미 로그인한 상태에서 허용하면 다음 로그인까지 익명으로 처리한다.
- 이미 전송된 서버 데이터 삭제나 진행 중 네트워크 전송 취소를 보장하지 않는다.

| 환경 | 앱 로컬 최소 | 외부 Logs 최소 | SDK 원문 로그 |
|---|---|---|---|
| Stage debug | DEBUG; 이벤트 이름·속성 키만 | INFO | Mixpanel logging OFF, Sentry debug OFF(diagnosticLevel INFO) |
| Stage release | INFO | INFO | Mixpanel OFF, Sentry debug OFF(diagnosticLevel ERROR) |
| Prod debug/release | NOTICE | WARN | Mixpanel OFF, Sentry debug OFF(diagnosticLevel ERROR) |

Firebase Android에는 앱별 iOS식 logger-level API가 없다. VERBOSE 시스템 속성을 앱/Release에 넣지 않는다. 공식 ADB DebugView는 기존 Stage 키와 합성 사용자로 별도 검증할 때만 사용하며 실제 수신 증거가 있어야 완료로 기록한다.

## 코드 근거

- `service/analytics/.spec/analytics.md`: 이벤트 계약·공식 문서 링크.
- `service/analytics/sources/.../configuration/FestiCoreAnalyticsConfiguration.kt`: 동의 저장·시작/중단·로그 정책.
- `service/analytics/sources/.../usecase/{AnalyticsUseCaseImpl,FirebaseAnalyticsClient,MixpanelClient,SentryClient}.kt`: SDK 경계·식별/reset·이벤트 매핑·실패 격리.
- `service/analytics/sources/.../configuration/SentryConfiguration.kt`: 수집 기능·beforeSend·환경.
- `core/storage/sources/.../shared/SharedPreferencesAnalyticsConsentStore.kt`: 기기 동의 영구 저장.
- `feature/profile/sources/.../settings/SettingsHomeScreen.kt`: 사용자에게 보이는 선택과 설명.
- `app/src/main/AndroidManifest.xml`: 초기 수집/자동 화면/광고 차단.

## 운영 설정 접근 확인

원본 applicationId는 `io.festicore.co`, 현재 develop/PR은 `io.festiCore.co`다. 기존 Firebase APP_ID의 Android 형식과 sender/project명 대응은 확인했지만 등록 package 증거는 아니다. 원본/현재 Android 폴더에 google-services.json은 없고, 기존 Firebase CLI 읽기 조회는 authentication required였다. 브라우저 접근 host unavailable / Chrome cgWindowNotFound로 기존 콘솔도 확인할 수 없었다. 등록 없음이 아니라 접근 미확인이며 추가 인증 조회를 중단했다. 기존 `io.festiCore.co` 등록 정보가 확인되기 전 Prod 값 주입·전송을 보류한다. 키 없는 빌드 결과와 운영 설정 검증을 구별하며 applicationId를 임의 변경하지 않는다.
