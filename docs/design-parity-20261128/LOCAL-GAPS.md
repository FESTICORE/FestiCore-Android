# 로컬 잔여 검증과 분석 연동

분석 구현 커밋: `8c8e656`, 큰 글꼴·위젯 테스트 보완: `e35c704`. 기준 디자인 구현: `63ba141`. 전체 화면 픽셀 일치나 실제 분석 서버 수신을 완료했다고 주장하지 않는다. 기존 238장 캡처를 반복하지 않고 위험 높은 화면의 추가 제약과 런처 호스트를 검증했다.

## 실제 에뮬레이터 추가 검증

API 36 arm64 / 1080×2400 / 360dp. 글꼴 배율 1.0/2.0, 세로/가로. MainActivity는 회전을 잠그지 않으며 제품에 새로운 회전 기능을 추가하지 않았다. 검증 후 font_scale=1.0, user_rotation=0, accelerometer_rotation=1로 복구했다.

| 대상 | 추가로 확인한 내용 | 증거/제한 |
|---|---|---|
| S10 프로필 편집 | 큰 글꼴 세로·가로에서 콘텐츠 끝의 공동 기수/계획 설명과 고정 저장 버튼 도달 | [세로 하단](local-gaps/s10-large-end.png), [가로 200% 하단](local-gaps/s10-large-landscape-bottom.png). 콜백은 fixture; 서버 저장하지 않음 |
| S13 설정 | 큰 글꼴과 가로에서 약관/버전 하단 접근 | [세로](local-gaps/s13-large-end.png), [가로](local-gaps/s13-large-landscape-scroll-end.png). 분석 설정 추가 전 제약 캡처 |
| S15 준비물 | 큰 글꼴 가로에서도 마지막 항목/추가 행과 준비 완료·건너뛰기 표시 | [가로 하단](local-gaps/s15-large-landscape-scroll-end.png) |
| S03 셋 상세 | 큰 글꼴에서 겹침·메모·내 일정·출처 하단 접근 | [세로 하단](local-gaps/s03-large-end.png), [가로 하단](local-gaps/s03-large-landscape-scroll-end.png) |
| S02 타임테이블 | 큰 글꼴·가로의 스크롤, 축약 표시, 출처 footer | [세로 하단](local-gaps/s02-large-end.png), [가로 하단](local-gaps/s02-large-landscape-scroll-end.png). 시간/이름은 축약되며 전체 문구는 셋 상세로 확인 |
| 홈 위젯 | 실제 앱 설정→런처 pin 승인→host 설치→2×3에서 4×3으로 크기 변경에 따른 로컬 RemoteViews 갱신 | [설치 안내](local-gaps/widget-settings.png), [시스템 확인](local-gaps/widget-pin.png), [설치 완료](local-gaps/widget-installed.png), [작은 크기](local-gaps/launcher-ours.png), [갱신 후](local-gaps/widget-large-updated.png), [큰 글꼴](local-gaps/widget-font200.png) |

위젯은 `io.festiCore.co.stage`, host `com.google.android.apps.nexuslauncher`, widgetId=3으로 확인했다. 같은 에뮬레이터의 소문자 패키지 `io.festicore.co.stage` 위젯(id=2)은 다른 앱이므로 증거에서 제외했다. 공개 팩 조회/로컬 저장·크기 변경만 사용했고 인증·실서버 쓰기를 실행하지 않았다. 위젯의 서버 갱신/Doze/로그인 계정 교체 검증은 별도다.

가로 스크롤 첫 시도는 고정 CTA 위에서 제스처를 시작해 움직이지 않았다. 스크롤 영역 안으로 시작점을 옮겨 하단을 확인했다. S10 200% 가로는 긴 콘텐츠를 끝까지 이동한 `bottom` 캡처를 최종 증거로 사용한다. S03 단독 Content 프리뷰의 OS 상태바/시트 호스트 경계는 제품 인셋 검증에서 제외한다. 나머지 선택한 긴 콘텐츠 끝에서는 고정 버튼과의 겹침을 확인하지 않았다.

## 기존 로컬 Figma PNG 발견

`~/Downloads/S*.png`에서 2026-11-25에 저장된 24개 PNG를 발견했다. 21개의 노드 ID가 최신 복사본의 화면 프레임 목록과 일치하고, 3개는 화면 묶음이다. [목록·크기·SHA256](local-gaps/reference-inventory.json)과 21개 원본 PNG를 첨부했다. 이 파일의 존재는 최신 `.fig`에서 다시 export한 사실을 뜻하지 않는다. 원본/복사본의 해당 root node JSON은 24개 중 11개만 동일하며, root 동일성도 하위 인스턴스·렌더 버전 동일성을 증명하지 않는다.

S02 변경 프레임(`215:8313`)과 기존 수정 후 Android dark 캡처를 대조했다. 검은 배경·라임 선택 카드·흰 변경 배너·주황 변경 시간 강조 구조는 대응하지만 393px Figma와 360dp Android의 폭, 공연 fixture/건수, OS 상태 표시 및 탭 호스트가 다르다. 따라서 동일 fixture/viewport의 픽셀 차이 수치는 산출하지 않았다. 기존 타임스탬프 잘림은 앞선 커밋에서 ellipsis로 수정되었고 이번 큰 글꼴 검증에서도 축약 상태가 유지된다.

최신 `.fig` ZIP에는 개별 화면 raster가 아니라 125×400 캔버스 thumbnail과 이미지 자산이 있다. 설치된 Figma Desktop 제어는 `screen_locked`로 차단됐다. 로그인·업로드·신규 유료 도구 없이 확인한 한계다. S03~S15 및 최신 S00~S02의 정확한 비교에는 기존 Mac 잠금 해제 후 해당 최신 파일에서 frame별 원본 export가 필요하다. 필요한 기준은 [전체 261 프레임 대응표](COVERAGE.md)의 ID, 각 테마, 1× 원본이며 폰트가 정상 적용돼야 한다.

## 분석·진단 구현과 검증

[최종 수집표](ANALYTICS-COLLECTION.md)에 SDK 버전·이벤트/속성·자동 수집·식별자·endpoint/region 확인 수준·삭제 경로·환경별 로그를 구분했다. 설정에서 선택적 분석·진단을 기본 OFF로 제공하고, 기존 분석 계약에 Firebase SDK만 추가했다. SDK 실패는 앱/다른 서비스로 전파하지 않는다.

- 필수 verify: hooks-test, boundaries, Kotlin format, 전체 library Debug + app Stage Debug tests 및 Stage APK 성공. XML 집계 1,521 tests / 199 suites / failures 0 / errors 0.
- 신규 테스트: 기본 거부, 허용/철회·저장 실패, 재생성 저장소 유지, 세 SDK 전달 게이트, SDK 실패 격리, Firebase Boolean 변환, 네 환경 로그, 설정 토글, Sentry 철회 callback, logger sink 실패.
- Stage Debug `1.0.0 (1)`을 실제 에뮬레이터에 설치했다. 모든 키가 없는 격리 빌드이며 실제 SDK 서버 수신 증거가 아니다.
- 처음 발견한 컴파일 오류(diagnosticLevel Kotlin property)는 공식 setter로 수정했다. 테스트·최종 verify는 통과했다.
- Release에 들어온 광고 서비스 권한까지 manifest merger에서 제거하고 최종 verify/빌드를 다시 실행했다. 기존 테스트는 Gradle up-to-date로 재사용했다.

## 남은 외부 조건

- Stage Firebase: `FESTICORE_FIREBASE_STAGE_API_KEY`, `..._APP_ID`, `..._PROJECT_ID`, `..._SENDER_ID`가 원본에도 없다. 새 발급은 하지 않았다.
- 원본 Android local.properties에는 기존 `FESTICORE_MIXPANEL_TOKEN`, `FESTICORE_SENTRY_DSN`, `FESTICORE_FIREBASE_PROD_{API_KEY,APP_ID,PROJECT_ID,SENDER_ID}`가 있다. 격리 빌드에 복사하지 않았고 실제 수신/운영 설정을 확인하지 않았다. 키 원문은 보고서·커밋에 없다.
- `FESTICORE_GOOGLE_ROUTES_API_KEY`는 원본/작업공간 모두 없다. 지도/귀가의 실제 경로 응답 검증은 미완료다.
- OAuth·가입/탈퇴·크루/기수 nickname 가입·수정·plan 저장·실서버 권한 오류/재접속/업로드 상태는 인증/서버 쓰기 없이 검증할 수 없어 외부 목록으로 유지한다. S10 축제 이동 기능을 제거하지 않았다.
- 실제 Mixpanel/Firebase 대시보드 이벤트 및 Sentry 크래시/성능 수신, R8 mapping 업로드/심볼화, 서비스별 보유기간·계약·서버 데이터 삭제와 개인정보 처리방침 게시 확인은 별도다.
- Android 산출물은 APK/PR이다. 이번 작업에서 별도 운영 서버 배포 대상은 없고 Play 제출·공개 롤아웃을 하지 않았다.

Claude 교차 검토는 주인 지시로 생략(unreviewed-spec override)했다. 재사용 점검: 기존 AnalyticsUseCase·FCListRow·Koin·logger를 재사용하고, 영구 동의 Boolean은 core/storage 경계에만 추가했다. 신규 의존성은 요청된 공식 Firebase Analytics SDK 하나이며 이벤트/네트워크 계층을 중복 생성하지 않았다.

## 선택적 수집 실제 앱 동작

키가 없는 Stage Debug 실앱에서 [기본 OFF](local-gaps/consent-settings-off.png) → [ON](local-gaps/consent-settings-on.png) → 강제 종료·재실행 후 [ON 유지](local-gaps/consent-persisted-on.png) → [OFF 철회](local-gaps/consent-revoked-off.png)를 확인했다. 재실행 후 저장값은 [허용 true](local-gaps/consent-relaunch.xml), [철회 false](local-gaps/consent-off-relaunch.xml)다. 이 증거는 UI/저장과 앱 기능 지속을 검증하며 실제 SDK 전송을 검증하지 않는다. 이 캡처의 긴 항목명은 후속 큰 글꼴 수정 전이다.

200% 글꼴에서 [기존 긴 제목 잘림](local-gaps/consent-large-yes.png)을 확인해 제목을 ‘분석·진단 공유’로 줄였다. 전체 설명문·설정 의미·기본 OFF는 유지한다.

Prod Debug 전체 188 tests가 최초 실행에서 기존 위젯 초기 상태 비동기 대기 부족으로 1건 실패했다. ViewModel을 composition 밖에 유지하고 기존 패턴의 waitUntil로 실제 상태를 기다리도록 수정한 뒤 188 tests가 통과했다. Prod Release R8/lintVital/unsigned APK 빌드도 통과했다.

수정 후 [200% 다크](local-gaps/consent-large-fixed-yes.png)와 [200% 라이트](local-gaps/consent-large-fixed-no.png)에서 항목 제목·꺼짐 값·전체 설명이 표시된다. 글꼴 1.0/기존 다크로 복구했다. 최종 코드의 필수 verify도 통과했으며 [XML 집계](local-gaps/test-summary.json)는 library 1,333 + Stage app 188, Prod app 188 tests 모두 실패·오류·skip 0이다. Prod app 테스트 이후에는 제목 문구만 바뀌었고 Stage app 전체가 다시 통과했다.

최종 Stage Debug APK SHA256: `bce1dc0bc59956d845ca9d9f898f937ea89e689b7a45a591a6caeeee817e4cbd`. 빌드 `1.0.0 (1)`, 실제 설치 성공.
