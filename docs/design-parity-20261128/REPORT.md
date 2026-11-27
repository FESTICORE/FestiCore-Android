# Android 실제 에뮬레이터 디자인 대조 후속

## 판정

PR #240의 기존 변경을 유지하고 실제 Android 에뮬레이터 렌더 검증을 추가했다. **전체 디자인 일치 완료는 아니다.** 이 보고서는 261개 디자인 프레임 목록, 실제 앱 진입 캡처, 별도로 표시한 fixture 렌더, 확인한 결함 수정 및 미검증 경계를 전달한다. Claude 계획·최종 검토는 주인 명시 승인으로 생략한 `unreviewed-spec`이며 quota를 승인으로 간주하지 않는다.

## 원본과 범위

- 주 기준: `/Users/suhwonji/Downloads/FESTICORE (복사).fig`, SHA256 `7669295fdbc34cea802d937e1ecb990772d65ecdbb570ca0e54e4e9817b4f575`, export 2026-11-25.
- 비교 원본: `FESTICORE.fig`, SHA256 `7c093f507a97793aecef7379d68d580c9acc2a6779db650c0d5a0fa16fd4e6fe`, export 2026-11-09.
- 백엔드 담당 해독 JSON의 Design 페이지를 다시 순회했다. Sxx FRAME 204→230(+26); More/Pack/Lock Widget 화면을 합치면 226→261(+35). 문서·컴포넌트 예시는 화면 목록에서 제외했다. S11/S12 최신 화면은 `Pack` 이름이다.
- [COVERAGE.md](COVERAGE.md)에 261개 ID/상태/모듈/검증 경계를 모두 열거했다. 모든 프레임의 오버라이드와 픽셀을 검증했다는 뜻이 아니다.
- Android만 수정, 원본 Desktop 체크아웃 수정 없음. S10 축제 이동과 분석 연동 유지. 서버 추가/데이터 쓰기/키 발급/콘솔 변경/머지/배포 없음.

## 실제 실행과 fixture 구분

에뮬레이터 `emulator-5554`, API36 arm64, 1080×2400. 앱 ID `io.festiCore.co.stage`. 기본 실제 화면 폭은 360dp이며 Figma 393dp와 다르다.

1. **실제 MainActivity 경로:** 기존 Stage APK 업데이트 설치, S14 첫 로그인 → 건너뛰기 → S00 공개 축제 목록 → 검색/결과 없음. OAuth는 누르지 않았다. 서버 변경 호출은 수행하지 않았다. 앱에 저장된 다크 설정이 시스템 테마보다 우선하므로 최초 캡처를 라이트로 오기한 파일명을 바로잡았다.
2. **실제 기기 fixture 렌더:** 기존 110개 무인자 Preview를 Android Compose 런타임에서 라이트/다크로 호출. 기본 PreviewActivity가 액션바를 추가하는 것을 확인하여 debug 전용 `DesignCaptureActivity`로 앱 테마와 edge-to-edge를 적용했다. 기존 화면 Content와 가짜 상태를 재사용한다. 프로덕션 탐색, ViewModel, 실제 계정/서버 응답 검증이 아니다.
3. **추가 fixture:** S03의 기본/내 일정/공연 중/종료/시간 변경/겹침 실제 FCSheet, S15 깃발/준비물 프리뷰를 보완했다. 내 일정 선택은 기존 SchedulePickPreview를 재사용한다.
4. 프리뷰에 탭바/외부 시트 호스트가 없는 경우가 있다. 단독 시트 내용이 상태바 아래 겹치는 캡처를 프로덕션 인셋 결함으로 판정하지 않았다. 고정 샘플·원격 이미지 placeholder·부분 스크롤·오프라인 경과분은 실제 서버 상태와 다르다.

## 확인 및 수정

- **S02 변경 시간 잘림:** `TimetablePreviews.PublishedPreview`를 실제 360dp 에뮬레이터에서 렌더하면 `16:40 → 17:00–17:50` 끝이 숫자 중간에서 잘렸다. `FCSetBlock.TimeRow`의 변경된 시간에 기존 `TextOverflow.Ellipsis`를 적용했다. iOS `FCSetBlock.timeRow`의 한 줄 표시를 유지하며 전체 시간의 접근성 값은 그대로다. 전후 동일 fixture/기기 캡처에서 숫자 잘림이 생략표시로 바뀐 것을 확인했다.
- **인셋 소유권:** `FCTopBar`가 statusBars, `FCTabBar`가 navigationBars를 소유하고 `MainTabScreen`은 탭바 점유 공간을 소비한다. 화면별 중복 padding을 추가하지 않았다. debug 호스트는 MainActivity와 같이 edge-to-edge만 설정한다.
- **재사용/단순화:** 새 UI 컴포넌트·추상화·의존성·API 없이 기존 Preview, Content, FestiCoreTheme, Compose tooling을 재사용했다. debug 진입점은 release 소스셋에 포함되지 않는다.

## API 연결 판단

- S07 크루 메뉴/S08 참여 화면과 iOS `FestivalCrewJoinView`/`FestivalCrewMenuView`를 확인했다. 현재 화면에는 크루 전용 닉네임 입력/편집 동작이 없다. join에서 선택 nickname을 생략하면 계정 닉네임을 쓰는 기존 서버 계약과 호환된다. `PATCH crews/:id/members/me`가 서버에 있더라도 입력 요구 없이 새 화면/호출을 임의 추가하지 않았다. 컴포넌트 오버라이드 전체 해독은 미검증이다.
- `BearerProfileViewModel.onPlanChanged` 및 `BearerRequestBodyMapper`가 선택한 plan을 기존 flag PATCH에 전달한다. 내 일정 수정마다 자동으로 plan을 동기화하는 새 정책은 추가하지 않았다.
- S10 잠금 문구와 별개로 기존 사용자 요청인 축제 이동을 유지한다.
- GA SDK 미연동·키 누락·실제 Mixpanel/Sentry/FCM 수신 미검증은 이전 보고서의 상태를 유지하며 디자인 작업을 막는 게이트로 사용하지 않았다.

## 남은 검증과 장애

- native Figma PNG가 없어 참조와 픽셀 diff/90점 통과 판정을 하지 않았다. 해독된 text/layout 필드는 확인했으나 인스턴스 오버라이드·폰트 렌더 결과 전체를 복원한 것은 아니다.
- 실제 앱 인증 경로 S07 가입/초대/권한, S08 만료/정원/다른 크루/로그인, S09 상태 전이·공동기수, S10 사진 권한·업로드·저장·이동, S13 계정/삭제/권한, S14 OAuth/가입·취소, S15 저장 실패·복구는 미검증. 인증 계정 없이 서버 쓰기를 하지 않는 조건에서 fixture만 확인했다.
- S01 개장 전/후·기수·배지, S04 전체 필터 조합, S05 전체 신고/팔로우/대표 상태, S06 GPS와 실지도, S11 외부 지도앱, S12 편집·전부 체크·삭제, 실제 위젯 호스트/잠금화면·갱신, 모든 화면 하단까지 스크롤·큰 글꼴·회전은 별도 검증이 남는다.
- iOS에는 Apple 로그인 버튼이 있으나 Android는 저장소 규칙에 따라 Google만 제공한다. 시스템바·위젯 플랫폼 차이를 그대로 표시한다.

## 재현

Stage debug 설치 후:

```sh
adb -s emulator-5554 shell cmd uimode night no
adb -s emulator-5554 shell am start --activity-clear-top \
  -n io.festiCore.co.stage/io.festicore.app.navigation.DesignCaptureActivity \
  --es preview io.festicore.feature.timetable.main.TimetablePreviewsKt.PublishedPreview
adb -s emulator-5554 exec-out screencap -p > s02.png
```

다크는 `night yes`. 캡처 후 기존 시스템 설정으로 복원한다. fixture 클릭은 기본 no-op이며 실제 동작 성공으로 판단하지 않는다.

로컬 전체 PNG/캡처 로그/JSON/검증 로그: `/Users/suhwonji/orca/reports/android-emulator-design-20261128/`. 리뷰용 contact sheet와 전후 핵심 이미지는 PR 문서에 첨부한다.

## 캡처 및 검증 결과

- 기존 110개 Preview의 라이트/다크 220장, 추가 8개 Preview와 S02 수정 후 재캡처 18장으로 **238장**을 수집했다. 118개 고유 fixture 상태이며 Figma 261개 프레임과 일대일 대응하거나 238장을 모두 상세 시각 검수했다는 뜻이 아니다. 전경 Activity 확인은 화면 내용의 정확성 증거가 아니다.
- 추가 시트 첫 18장은 일부 진입 애니메이션/스플래시가 있어 제외했다. 대기 후 안정 캡처 18장만 첨부 대상으로 사용했다. S03 6상태·S15 2단계·S02 수정 후 라이트/다크 contact sheet를 직접 확인했다.
- [캡처 인덱스 및 전후 이미지](CAPTURES.md). 전체 220장 축소 contact sheet는 수집 목록이며 픽셀 일치 판정서가 아니다. 전체 원본 PNG는 위 로컬 보고서 경로에 보관했다.
- 수정 후 `.claude/scripts/verify` 성공: hooks 32개, 경계·포맷, 196 suites / 1,511 tests, 실패·오류·skip 0, Stage APK 성공. `FCSetBlockTest` 3개 성공. Prod 검증은 앞선 PR 검증이며 이번에 반복하지 않았다.
- 단순화 점검: 기존 Preview/Content 재사용, release에 없는 debug 호스트와 1줄 ellipsis 수정, 신규 의존성/API 없음. 원본 체크아웃 및 다른 담당 변경을 되돌리지 않았다.
- 시스템 night mode는 기존 다크로 복원했다. remote CI 상태는 PR checks에서 최신 HEAD 기준으로 확인한다.

### 다크 이미지 수집 후 점검

기존 220장 contact sheet 검토에서 전환 중 빈 화면/흰색 잔상이 발견되어 18개 다크 fixture를 6초 대기 후 재촬영하고 수집 JSON과 contact sheet의 해당 항목을 교체했다. 선택 이미지 수는 238장으로 같으며 재촬영 원본은 별도 보관했다. 깃발 없음·팩 없음·마이·공지·기수 보드의 빈 화면은 안정 후 실제 내용이 보인다. CrewMenu/Gather, NicknameEdit, BearerZone/ActivityPicker, LoginSheet는 단독 시트 내용 프리뷰여서 외부 시트 배경 없이 흰 Activity 배경이 남는다; 해당 다크 이미지의 배경·인셋·대비는 제품 검증에서 제외한다. 이 현상을 수정된 제품 화면으로 주장하지 않는다.
