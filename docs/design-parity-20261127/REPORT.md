# Android 디자인 이식 및 분석 점검

## 기준과 범위

- develop `a02dba8`에 원본 작업 브랜치 `238d63d`의 미커밋 변경을 3-way로 이식했다. 원본 체크아웃은 읽기만 했다.
- 원본 `~/Downloads/FESTICORE (복사).fig`: SHA256 `7669295fdbc34cea802d937e1ecb990772d65ecdbb570ca0e54e4e9817b4f575`, 71,482,122 bytes. 기존 오프라인 추출의 provenance와 이번 직접 계산이 일치한다.
- 참조 자료: iOS `DesignExports/FigmaLatest-20261125/Offline-20261126`, Android 원본 `.omc/design-parity-20261126/REPORT-S*.md`. `.omc`·실험 파일·키는 PR에 포함하지 않는다.
- 앱 ID 소문자 변경은 디자인 범위 밖이므로 제외했다. `io.festiCore.co`와 `.stage`를 유지한다. Firebase 등록의 패키지 일치 여부는 별도 확인이 필요하다.
- 기존 develop의 S10 축제 이동(PR #237·#238)을 유지했다. 원본 보고서의 “이동 API 부재”는 이번 develop에는 적용하지 않는다.

## 화면 대조

| 화면 | 반영/보존 내용 | 검증 경계 |
|---|---|---|
| S00 | 검색 미지원/빈 결과 문구, 준비 안내 진입 | 검색 회귀 테스트 |
| S01·S02·S04·S06·S08·S09·S11·S14 | 원본 구조 대조에서 새 확정 차이 없음, 기존 구현 보존 | 모든 상태의 렌더 동일성 미검증 |
| S03 | 공연 시트 하단 토스트, 작은 화면 스크롤·닫기, 토스트 표시 시 가시 영역 확보 | bounds·제스처 테스트, Android 하단 토스트는 의도적 차이 |
| S05 | Figma `267:14265`/`268:14315`의 히어로 위 고정 헤더, 오프라인 헤더, 홈 위젯 pin | 헤더 bounds·클릭 회귀, S10 관리 메뉴 함께 유지 |
| S07 | 하단 토스트, CTA·IME 여백 중복 제거 | 클릭·inset 회귀 |
| S10 | `645:24720`/`645:24849` 편집 제목·잠긴 축제·저장 후 닫기 | 라이트/다크 캡처와 기존 축제 이동 회귀 |
| S12 | 준비물 진행 카드 이중 패딩 제거 | Compose bounds 회귀 |
| S13 | 홈 위젯 설정·실제 설치 상태 구분, 알림 설정 서비스·화면 연결 | 로컬 가짜 응답/설정 테스트, 실제 FCM 수신 미검증 |
| S15 | 일정→깃발→준비물 3단계 안내, 저장 timeout·뒤로가기·계정 경합 보호 | 라이트/다크 캡처 및 Coordinator/VM 테스트 |
| Widget | 암호화 snapshot·세션 분리, RemoteViews/provider/pin/updater | native 렌더 테스트, 이번 작업에서 실제 런처 설치 미실행 |

오프라인 추출은 native Figma PNG가 아니다. 원본의 일부 instance/속성은 미해석이며 해당 값을 수정 근거로 삼지 않았다. 첨부 Android PNG는 Robolectric native 렌더이며 실기기·Figma 픽셀 동등성의 증거가 아니다. 전체 S00–S15 픽셀 일치 완료를 주장하지 않는다.

## 분석 연동 점검

| 항목 | 코드 점검 결과 | 외부 검증 |
|---|---|---|
| Firebase / GA | Stage/Prod의 API_KEY·APP_ID·PROJECT_ID·SENDER_ID 네 값이 모두 있을 때 FirebaseApp 초기화. `firebase-common`만 있고 `firebase-analytics` 및 GA 이벤트 전송 없음 | GA 수신 확인 불가·미연동 |
| Mixpanel | 토큰이 있을 때만 초기화, 자동 이벤트 비활성. sign_up/screen_view/ui_action/flag_follow/timetable_action/pack_sync, identify/reset 연결 | mock 테스트 범위, 대시보드 수신 미검증 |
| Sentry | 유효 DSN만 초기화, Debug/Stage는 stage·Prod release는 production, SDK 기본 크래시 수집 경로. 사용자 ID/reset, breadcrumb/count, NETWORK 제외 로그 sink | 설정·SDK callback은 로컬 테스트, 실제 크래시 전송·수신·R8 mapping 미검증 |

Sentry는 스크린샷·뷰 계층·Replay·기본 PII를 끈다. 자동 breadcrumb와 scope 식별자는 남을 수 있다. 이벤트 속성은 spec의 enum/Boolean 허용 목록만 사용하고 서버 ID 속성은 제거했다. 런타임 DSN과 업로드 인증 토큰을 혼용하지 않는다.

### 키 존재 여부(값 비공개)

이 워크스페이스에는 `local.properties`가 없고 아래 키는 환경 변수에도 없다. 원본 로컬 설정에서는 Mixpanel/Sentry 및 Firebase Prod 네 값이 존재하나 복사하지 않았다.

- 원본에도 없음: `FESTICORE_FIREBASE_STAGE_API_KEY`, `FESTICORE_FIREBASE_STAGE_APP_ID`, `FESTICORE_FIREBASE_STAGE_PROJECT_ID`, `FESTICORE_FIREBASE_STAGE_SENDER_ID`, `FESTICORE_GOOGLE_ROUTES_API_KEY`.
- 워크스페이스에서 추가로 없음: `FESTICORE_MIXPANEL_TOKEN`, `FESTICORE_SENTRY_DSN`, `FESTICORE_FIREBASE_PROD_API_KEY`, `FESTICORE_FIREBASE_PROD_APP_ID`, `FESTICORE_FIREBASE_PROD_PROJECT_ID`, `FESTICORE_FIREBASE_PROD_SENDER_ID`.

키 발급·콘솔 설정 변경·실서버 데이터 변경·배포·앱 제출은 수행하지 않았다.

## 재사용·단순화 점검

기존 FCToast/FCSetSheet/SettingsSection/FCListRow·일정/깃발/팩 VM·Navigation 3·Ktor·SecureStorage를 재사용했다. 위젯은 Android RemoteViews와 기존 토큰/폰트를 사용한다. 새 전역 상태 저장소나 디자인 프레임워크는 추가하지 않았다. Sentry SDK는 원본 분석 변경에서 도입한 의존성이며 디자인 변경의 새 의존성과 구분한다. 이번 이식의 충돌 해결은 헤더 내부 관리 메뉴 분기에 한정했다.

## iOS 대응

`Projects/Feature/Festival/Sources/FlagDetail/View/FestivalFlagDetailView.swift`, `Feature/Bearer/Sources/Profile/View/BearerProfileView.swift`, `Feature/Timetable/Sources/SetDetail/View/TimetableSetDetailView.swift`, `Feature/Festival/Sources/PrepGuide`, `Feature/Profile/Sources/Notification`, `Service/FestiCoreAnalytics`를 기준으로 한다. Android 홈 위젯은 iOS 잠금화면/Live Activity와 동일 지원이라고 표기하지 않는다.

## 검증 결과

`.claude/scripts/verify --all` 통과: hooks 32개, 경계·포맷, Android 196 suites / 1511 tests, 실패·오류·skip 0 및 Stage APK 빌드 성공. 최초 실행의 신규 위젯 테스트 생성자 누락을 수정한 뒤 두 번째 전체 실행이 통과했다.

[라이트·다크 캡처 24장](CAPTURES.md), [원본 구조 근거](SOURCE.md). 캡처는 이번 워크스페이스에서 새로 생성했다.


Prod 추가 검증: `mise exec -- ./gradlew --console=plain :app:testProdDebugUnitTest :app:assembleProdDebug` 성공, 24 suites / 188 tests, 실패·오류·skip 0. Prod **debug** 빌드이며 서명된 release/배포 검증과 구분한다.
