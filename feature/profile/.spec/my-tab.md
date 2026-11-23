---
spec: my-tab
modules: [feature/profile, feature/web, domain/festival, domain/auth, domain/notice]
---

# 마이 탭 Spec

> 마이 탭 루트(마이 화면)와 그 아래 화면들의 동작. 지금은 마이 화면(S1) · 설정 홈 · 화면 모드(S2) · 계정 · 닉네임 변경(S3) · 공지 목록 · 상세(S4)가 있다.

iOS 대응: `Projects/Feature/Profile/Sources/Main/{Reducer/ProfileFeature.swift,View/ProfileView.swift}`, `Interface/Sources/ProfileDelegate.swift`, 테스트 `Tests/Sources/ProfileFeatureTests.swift`.
설계: `.claude/docs/specs/2026-11-23-my-tab-android-design.md`(화면 1 · 쟁점 M1 · M7 · M12 · M13 · PR S1).

## 개요

`ProfileScreen`/`ProfileContent` + `ProfileViewModel`. 계정 상태는 앱이 소유하고(M13) 화면은 `AccountState`(`feature/profile/interface`)를 입력으로 받는다. 화면 이동은 SideEffect → Screen 콜백으로만 알린다. 경로 · `ProfileCoordinator` · `ProfileFlow` · 탭 연결은 T1, 계정 상태 공급 · Google 바로 로그인 실행은 A1이다.

## 동작 규칙

1. 화면: 배경 `backgroundNormal`. `FCTopBar(Large, "마이")` 위 오른쪽에 설정 버튼(`ic_gear_24` 24 · `labelNormal`, 누르는 영역 44, 오른쪽 `marginM6`, 접근성 "설정")을 겹친다.
2. 세션이 없고 계정 문구(`message`)가 비어 있지 않으면 상단 바 아래 문구(`baseSubheadlineRegular` · `statusNegative`, 좌우 `paddingP20`).
3. 세로 스크롤(간격 `paddingP24`, 좌우 `P16`, 위 `P8`): 로그인 카드 → 내가 받은 팩.
4. 로그인 카드: `profile == null && !hasSession && !isLoading`일 때만. 배경 `backgroundElevated` r16, 상하 `P20` · 좌우 `P16`, 간격 `P12`. 제목 "로그인하고 더 함께 즐겨요"(`baseHeadlineBold`), 본문 "깃발 팔로우 · 크루 · 기수는 로그인이 필요해요. 타임테이블과 팩은 로그인 없이도 쓸 수 있어요."(`baseFootnoteRegular` · `labelNeutral`, 간격 `P4`). 버튼은 `FCLoginButton(Google)` 하나(M1: Apple 버튼만 뺀다).
5. Google 버튼은 `GoogleLoginRequested`를 보낸다. 앱은 시트 없이 Google 계정 선택을 띄우고, 취소면 조용히 끝내고 실패면 로그인 시트(`LoginContext.Profile`)를 연다(M12, 실행은 A1).
6. 기수 카드 · 내 깃발 카드는 숨긴다(M7). 기수 흐름을 옮기는 문서에서 더한다.
7. "내가 받은 팩": 제목 `baseFootnoteBold` · `labelAssistive`(좌우 `P4`), 오른쪽 "N개"(`Loaded`일 때만). 카드 `backgroundElevated` r16 안에 행 · 구분선(1, `lineNormal`, 좌우 `P16` 안쪽).
   - `Idle` · `Loading`: 스켈레톤 한 줄(44 원형 + 140×16 · 180×12 막대 r4, 간격 `P12` · `P4`, 상하 `P12` 좌우 `P16`).
   - `Failed`: "받은 팩을 불러오지 못했어요"(`baseSubheadlineRegular` · `labelNeutral`) + "다시 시도"(Secondary · Medium), 상하 `P16`.
   - 빈 목록: `FCEmptyCard(ic_ticket_24, "아직 받은 팩이 없어요", "페스티벌을 고르면 시간표 · 지도 · 가는 길을 오프라인으로 받아 둘 수 있어요.")` + "페스티벌 둘러보기"(Primary) → `FestivalListRequested`.
   - 행: `FCPackRow(name, meta, hasUpdate)` + 서버 썸네일 44 · r `12×44/56`을 왼쪽 `P16`에 겹쳐 그린다(누르기 · 접근성 없음).
8. 행 meta(위에서 먼저 맞는 것): 자동 받기 실패 ∧ 최신 버전 있음 "새 버전을 받지 못했어요 · v{latest}" → 새 버전 ∧ 바뀐 공연 수 > 0 "새 버전이 있어요 · 시간표 N개 변경" → 새 버전 "새 버전이 있어요 · v{latest}" → 확인 시각 없음 "최신 버전 · v{ver}" → "최신 버전 · {MM/dd HH:mm} 확인"(서울 시간대, iOS `AppDateFormat.monthDayTime`).
9. 나타날 때(`onAppear`): 이미 `Loading`이면 무시. `Loaded`가 아니면 `Loading`으로 바꾼다(보여 준 목록은 다시 읽는 동안 둔다). 읽기 = 로컬 받은 팩 → 성공이면 `Loaded` → 서버 목록 갱신(실패하면 표시 유지) → 새 버전 팩마다 바뀐 공연 수(실패 · 오프라인이면 아무것도 안 함). 갱신 결과에 새 버전이 아닌 팩의 바뀐 공연 수는 지운다. 서버가 수를 모르면(`null`) 그 팩의 수를 지운다.
10. 로컬 읽기 실패: `Loaded`가 아니면 `Failed`. "다시 시도"는 `Failed`일 때만 `Loading`으로 바꾸고 다시 읽는다.
11. 팩 행 → 목록에 있는 팩이면 `PackDetailRequested(festivalId, 이름)`. 라이브 보드 "가는 길" 경계(`onOpenPackDirections`)도 같은 요청을 보낸다. 팩 상세가 팩을 지웠거나(`onPackDeleted`) 바꿨으면(`onPackChanged`) 목록을 다시 읽는다(상태를 `Loading`으로 바꾸지 않는다). 상세를 닫는 것은 Coordinator다.
12. 설정 버튼 → `SettingsRequested`. 설정 홈은 같은 `AccountState` 입력을 받는다(S2 · T1).
13. 계정 입력(`onAccountChanged`)은 값을 그대로 바꾼다. 세션이 있다가 없어지면 `SessionLost`를 보낸다. Coordinator는 계정 화면이 열려 있으면 설정 홈째 닫는다(iOS `PF:211-216`, T1).

## 설정 홈 (S2)

iOS 대응: `Settings/{Reducer/SettingsHomeFeature.swift,View/SettingsHomeView.swift}`, 테스트 `SettingsHomeFeatureTests`. 설계 화면 3 · 쟁점 M4 · M5 · M11 · "문서 주소 규칙".

1. 화면: 배경 `backgroundNormal`, `FCTopBar(Back, "설정")`. 세로 스크롤(간격 `P24`, 좌우 `P16`, 위 `P16`). 그룹 = 제목(`baseFootnoteBold` · `labelAssistive`, 좌우 `P4`, 간격 `P8`) + `backgroundElevated` r16 카드, 줄 사이 구분선(1, `lineNormal`, 좌우 `P16` 안쪽).
2. 앱 그룹: "화면 모드"(값 = `시스템 설정` / `라이트` / `다크`, 저장소 값을 따른다). "알림"(M4, P0 전) · "잠금화면"(M5, 라이브 액티비티 단계 전) 줄은 숨긴다.
3. 계정 그룹: `profile != null || hasSession`일 때만. "계정"(값 = 닉네임). 그 외에는 계정 줄 입력을 무시한다.
4. 정보 그룹: "공지사항" · "정보 출처" · "이용약관" · "개인정보 처리방침" · "위치기반서비스 이용약관". 문서 4종은 `LegalDocumentUrls.url(sources | terms | privacy | location)`을 앱 안 `WebScreen`으로 연다(M11, 제목 = 줄 이름). 주소가 없으면(기준 주소가 https · host가 아님) 화면을 열지 않고 `FCDialog("문서를 열 수 없어요", "잠시 후 다시 시도해 주세요.")`, 이미 떠 있으면 다시 띄우지 않는다. 연 뒤 이동은 `WebScreen` 정책.
5. 버전: "FESTICORE v{versionName}"(`baseCaption1Regular` · `labelAssistive`, 가운데). `PackageManager.getPackageInfo(packageName).versionName`, 읽지 못하면 숨긴다.
6. 이동은 SideEffect → Screen 콜백: `BackRequested` · `AppearanceRequested` · `AccountRequested` · `NoticeListRequested` · `LegalDocumentRequested(WebPage)`. 계정 입력(`onAccountChanged`)에서 세션이 있다가 없어지면 `SessionLost`(계정 화면이 열려 있으면 Coordinator가 닫는다, T1).

## 화면 모드 (S2)

iOS 대응: `Appearance/{Reducer/AppearanceSettingsFeature.swift,View/AppearanceSettingsView.swift,Model/AppearanceMode.swift}`, 테스트 `AppearanceSettingsFeatureTests`. 설계 화면 5 · 쟁점 M6.

1. `FCTopBar(Back, "화면 모드")`, 그룹 "화면 모드" 카드에 `FCListRow(title, isSelected)` 3행 "시스템 설정과 같게" · "라이트" · "다크", 카드 아래 "시스템 설정과 같게를 고르면 기기의 라이트·다크 설정을 따라 자동으로 바뀌어요."(`baseFootnoteRegular` · `labelAssistive`, 좌우 `P4`).
2. 고르면 바로 저장한다. 앱 루트(`MainActivity`)가 같은 저장소를 읽어 즉시 적용한다(D4). 저장 값 `system` · `light` · `dark`, 기본 · 알 수 없는 값은 `dark`.
3. 저장소는 `AppearanceModeSettings`(`appearance/`). feature는 `core`를 의존하지 않으므로 app이 `core/storage` `AppearanceModeStore`를 감싸 등록한다(`domain/auth/sources` `ActivityProvider`와 같은 방식).

## 계정 · 닉네임 (S3)

iOS 대응: `Account/{Reducer/AccountSettingsFeature.swift,Reducer/NicknameEditFeature.swift,View/AccountSettingsView.swift,View/NicknameEditView.swift}`, `Interface/Sources/AccountSettingsDelegate.swift`, 테스트 `AccountSettingsFeatureTests`. 설계 화면 4 · "로그아웃 · 탈퇴 실행" · 쟁점 M1 · M2 · M13.

1. 화면: 배경 `backgroundNormal`, `FCTopBar(Back, "계정")`. 세로 스크롤(간격 `P24`, 좌우 `P16`, 위 `P16`) + 아래 고정 `FCButton("로그아웃", Ghost, Medium)`(가운데, 좌우 · 아래 `P16`, 처리 중 비활성).
2. "내 정보"(제목 `baseFootnoteBold` · `labelAssistive`, 좌우 `P4`, 간격 `P8`):
   - 프로필 있음: `backgroundElevated` r16 카드에 `FCListRow("닉네임", 값 = 닉네임 ?: "설정 안 됨")` · 구분선(1, `lineNormal`, 카드 너비) · 정보 행 "로그인" / 로그인 방식(높이 56 이상, `baseBodyRegular`, 값 `labelNeutral`, 좌우 `P16`).
   - 로그인 방식 표기는 iOS 매핑 그대로(M1): 대소문자 무시 `apple` → "Apple", `google` → "Google", 그 외 원문.
   - 프로필 없음 + 처리 중: 스켈레톤(r16, 높이 113). 그 외: "계정 정보를 확인할 수 없습니다"(`baseSubheadlineRegular` · `labelNeutral`) + `FCButton("계정 정보 다시 확인", Secondary, Medium)` → `RefreshRequested`.
3. "계정 삭제"(`baseBodyRegular` · `statusNegative`, 높이 56 이상 · r16 `backgroundElevated` 카드, 처리 중 비활성) + "계정을 삭제하면 내 일정, 크루, 깃발 기록이 모두 지워지고 되돌릴 수 없어요."(`baseFootnoteRegular` · `labelAssistive`, 좌우 `P4`).
4. 결과 문구: `message`가 비어 있지 않고 처리 중이 아닐 때 `baseSubheadlineRegular` · `statusNegative`.
5. 확인 팝업(`FCDialog`, 제목 · 설명 가운데 정렬, 취소 "취소"): 로그아웃 "로그아웃할까요?" / "받은 팩과 내 일정은 이 기기에 그대로 남아 있어요." / "로그아웃", 탈퇴 "회원 탈퇴" / "계정과 로그인 정보가 삭제됩니다. 이 작업은 되돌릴 수 없습니다." / "탈퇴". 처리 중이거나 같은 팝업이 떠 있으면 띄우지 않는다. 확인 → `LogoutRequested` · `WithdrawRequested`, 취소 · 딤 · 뒤로 → 닫기만.
6. 닉네임 줄: 프로필이 있고 처리 중이 아닐 때만 시트(`FCSheet`)를 연다(처음 값 = 현재 닉네임, 없으면 빈 값).
7. 닉네임 시트: "닉네임 변경"(`baseTitle2Bold`), `FCTextField(label "닉네임", placeholder "닉네임", counter "N / 12", helper "1~12자로 입력해 주세요", error)`, `FCButton("저장" / 저장 중 "저장하는 중", Primary, 전체 폭, 저장 중 비활성)`. 좌우 `P20`, 아래 `P24`, 간격 `P24`.
   - 입력은 `AuthNicknameRule.limited`로 자르고 오류 문구를 지운다.
   - 저장: 저장 중이면 무시. 앞뒤 공백을 뺀 길이가 1~12자가 아니면 요청하지 않고 "닉네임은 1~12자로 입력해 주세요.". 맞으면 `AuthRepository.updateNickname(trim 값)`.
   - 성공: 시트를 닫고 화면 프로필의 사용자를 바꾼 뒤 `NicknameUpdated(user)`(앱이 계정 상태를 갱신 → 마이 · 설정 홈 값, A1 · T1).
   - 실패: `AuthDomainError.InvalidNickname`이면 길이 문구, 그 외(세션 없음 · 401 포함) "닉네임을 바꾸지 못했어요. 다시 시도해 주세요.".
   - 저장 중에는 딤 · 끌기 · 뒤로로 닫히지 않는다.
8. 계정 입력(`onAccountChanged`)은 `profile` · `isLoading` · `message`를 그대로 바꾼다. 세션을 잃었을 때 계정 화면을 닫는 것은 설정 홈 `SessionLost` → Coordinator(T1)다.
9. 이동 · 요청은 SideEffect → Screen 콜백: `BackRequested` · `LogoutRequested` · `WithdrawRequested` · `RefreshRequested` · `NicknameUpdated(AuthUser)`. 로그아웃 · 탈퇴 실행(기기 연결 해제 · 세션 정리 · 결과 문구 · 화면 정리)은 앱 `AccountCoordinator`(A1), 설정 홈을 거쳐 앱으로 넘기는 연결은 T1이다.

## 공지 (S4)

iOS 대응: `Notice/{Reducer/NoticeListFeature.swift,Reducer/NoticeDetailFeature.swift,View/NoticeListView.swift,View/NoticeDetailView.swift}`, 테스트 `NoticeFeatureTests`. 설계 화면 8.

1. 목록: 배경 `backgroundNormal`, `FCTopBar(Back, "공지사항")`. 처음 나타날 때(`Idle`일 때만) `Loading`으로 바꾸고 `NoticeUseCase.notices()`를 읽는다. 성공 → `Loaded`, 실패 → `Failed`.
   - `Idle` · `Loading`: 스켈레톤 높이 56 · r16 4개(간격 `P8`, 패딩 `P16`, 접근성 "공지사항을 불러오는 중").
   - `Failed`: "공지사항을 불러오지 못했어요"(`baseBodyRegular` · `labelNeutral`) + `FCButton("다시 시도", Secondary, Medium)`, 간격 `P12`, 가운데.
   - 빈 목록: "등록된 공지사항이 없어요"(같은 문구 스타일, 버튼 없음).
   - 행: r16로 자른 묶음(패딩 `P16`)에 `FCListRow(제목, value = yyyy.MM.dd)`, 첫 줄이 아닌 줄 위에 구분선(1, `lineNormal`, 왼쪽 `P16` 안쪽).
2. "다시 시도"는 `Failed`일 때만 `Loading`으로 바꾸고 다시 읽는다.
3. 행 → 목록에 있는 공지면 `NoticeDetailRequested(id, 제목)`. 없는 id는 무시한다. 뒤로 → `BackRequested`. 상세를 열고 닫는 것은 Coordinator다(T1, iOS `destination`).
4. 상세: `FCTopBar(Back, "공지사항")`. 처음 나타날 때(`Idle`일 때만) `NoticeUseCase.notice(id)`를 읽는다. "다시 시도"는 `Failed`일 때만.
   - `Idle` · `Loading`: 스켈레톤 r4 전체 폭 24 · 90×14 · 전체 폭 180(간격 `P16`, 패딩 `P20`, 접근성 "공지사항을 불러오는 중").
   - `Failed`: 목록과 같은 실패 문구 + "다시 시도".
   - `Loaded`: 세로 스크롤(좌우 · 위 `P20`, 간격 `P16`)에 제목 `baseTitle3Bold` · `labelNormal`, 날짜 `baseCaption1Regular` · `labelAssistive`, 구분선(1, `lineNormal`), 본문 `baseBodyRegular` · `labelNormal`. 본문은 iOS `Text(String)`처럼 일반 텍스트다(마크다운 · 링크 해석 없음).
5. 날짜 `yyyy.MM.dd`는 서울 기준(iOS `AppDateFormat.noticeDay`). feature는 `core`를 의존하지 않으므로 `NoticePresentation`이 같은 패턴을 둔다.
6. 이동은 SideEffect → Screen 콜백: 목록 `BackRequested` · `NoticeDetailRequested(id, title)`, 상세 `BackRequested`. 설정 홈 `NoticeListRequested`에서 목록을 여는 연결은 T1이다.

## 엣지 케이스

- 진행 중 읽기를 취소하는 iOS `cancelInFlight`는 두지 않는다. `Loading` 중 `onAppear`는 무시하고, 팩 상세 결과로 다시 읽는 경우만 겹칠 수 있다(마지막 응답이 남는다).
- 바뀐 공연 수는 팩마다 차례로 묻는다(iOS는 동시에). 결과는 같다.
- 공지 읽기의 iOS `cancelInFlight`는 두지 않는다. `Idle` · `Failed`일 때만 읽어 요청이 겹치지 않는다.
- 닉네임 저장의 iOS `cancelInFlight`는 두지 않는다. 저장 중 다시 누르기를 무시해 요청이 겹치지 않는다.

## 제약 / 가정

- `feature`는 `core`를 의존하지 않으므로 `MM/dd HH:mm` 형식은 `ProfilePresentation`이 iOS `AppDateFormat.monthDayTime`과 같은 패턴 · 서울 시간대로 만든다.
- 설정 홈 팝업은 `FCDialogItem(cancelTitle = null)`로 iOS처럼 "확인" 하나다.
- 계정 화면은 iOS 자식 Store(`NicknameEditFeature`) 대신 `AccountSettingsViewModel` 하나가 시트 상태(`AccountSettingsDestination.Nickname`)를 가진다(Orbit은 자식 Store가 없다).
- 옮기지 않음: 분석 `profile_logout` · `profile_withdraw`(`service/analytics` 없음), `searchTapped` · `isSearchAvailable`(View 진입점 없음), 분석 `screen_view`(`service/analytics` 없음), 기수 카드 · 내 깃발(M7), 설정 홈 닫힘 때 알림 설정 보내기(알림 설정은 S6).
- `loginTapped`(시트 요청)는 iOS처럼 ViewModel에 두되 화면에는 진입점이 없다.

## 작업 모듈

| 모듈                        | 계층    | 변경 내용                                                                                 |
| --------------------------- | ------- | ----------------------------------------------------------------------------------------- |
| `feature/profile/interface` | feature | `AccountState`(앱 → 마이 계정 입력)                                                       |
| `feature/profile/sources`   | feature | `main/`(Contract · ViewModel · Screen · Presentation · 프리뷰), `settings/` · `appearance/`(S2), `account/`(S3), `notice/`(S4, `domain/notice/interface` 의존), `di/ProfileFeatureModule` |
| `feature/featureassembly`   | feature | `profileFeatureModule` 등록                                                               |
| `feature/web`               | feature | `LegalDocumentUrls`를 `interface`로 옮기고 https · host 검증(S2)                          |
| `app`                       | app     | `AppearanceModeSettings` 등록(`StoredAppearanceModeSettings`, S2)                         |

## 영향 범위

앱에는 아직 보이지 않는다(T1에서 마이 탭에 연결). `domain/festival`(`FestivalReceivedPackUseCase` · `FestivalPackDetailUseCase.packChanges`)은 읽기만 하고, `domain/auth`는 `AuthProfile`을 읽고 계정 화면이 `AuthRepository.updateNickname`으로 닉네임만 바꾼다. `domain/notice`(`NoticeUseCase`)는 읽기만 한다.
