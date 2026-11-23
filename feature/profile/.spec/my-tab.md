---
spec: my-tab
modules: [feature/profile, feature/web, domain/festival, domain/auth, domain/notice, ui/sharedui]
---

# 마이 탭 Spec

> 마이 탭 루트(마이 화면)와 그 아래 화면들의 동작. 지금은 마이 화면(S1) · 설정 홈 · 화면 모드(S2) · 계정 · 닉네임 변경(S3) · 공지 목록 · 상세(S4) · 팩 상세(S5)가 있다.

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

## 계정 상태 공급 · 로그아웃 · 탈퇴 실행 (A1)

iOS 대응: `Projects/App/Sources/Navigation/Account/AccountCoordinator.swift`, `Shared/AccountDeviceSync.swift`, `Session/SessionCoordinator.swift`(`handleSessionExpired`), 테스트 `AppCoordinatorTests` 계정 사례. 설계 "로그아웃 · 탈퇴 실행" · 쟁점 M2 · M3 · M12 · M13. 앱(`app/navigation`)에 있고 마이 탭 연결은 T1이다.

1. `AppCoordinator.account: StateFlow<AccountState>`가 계정 상태를 공급한다. 루트 화면 상태(`AppCoordinatorState`)와 따로 둔다.
   - 세션 확인(스플래시 · 온보딩 뒤) → `hasSession`. 프로필 재확인 중 `isLoading`, 성공이면 `profile`, 오프라인 · 서버 오류면 값 유지. 401 · 세션 만료 · 로그인 직후 401이면 비운다.
   - 로그인 · 가입 완료는 결과의 프로필로 채운다(확인 실패면 같은 사용자의 이전 프로필만 남긴다). 다시 읽지 않는다.
   - `onNicknameUpdated(user)`: 지금 계정과 같은 사용자일 때만 사용자 값을 바꾼다. `onProfileRefreshRequested()`: 홈이면 프로필을 다시 읽는다.
2. `onLogoutRequested()` · `onWithdrawRequested()`(확인 팝업 뒤): 세션이 있고 처리 중이 아닐 때만 세대를 올리고 문구를 비우고 `isLoading = true`. `AccountCoordinator`가 기기 연결 해제(실패해도 계속) → `signOut()` · `withdraw()` 순서로 부른다.
3. 결과: 성공이면 현재 사용자 · 계정 상태를 비우고 가입 정보 덮개가 떠 있으면 닫는다(로그인 시트는 그대로). 작업 중 로그인을 잃었으면(탈퇴 401 · 세션이 지워짐) 같이 비우고, 탈퇴면 "로그인이 만료되어 회원 탈퇴 결과를 확인할 수 없어요. 다시 로그인해 계정 상태를 확인해 주세요.". 그 외 실패면 기기 연결을 복원하고 계정을 두며 "로그아웃에 실패했습니다. 다시 시도해 주세요." / "회원 탈퇴에 실패했습니다. 다시 시도해 주세요.".
4. 세션 만료(현재 세션 또는 세션 없음)는 진행 중 작업을 취소하고 세대를 올려 늦은 결과를 버린다. 탈퇴 중이었으면 결과 불명 문구를 둔다. 작업 중에는 프로필 재확인을 하지 않는다.
5. 계정 화면 닫기(설정 홈째 마이로)는 마이 화면이 `AccountState`의 세션 잃음으로 정한다(S1 · T1).
6. 기기 연결 해제 · 복원은 P0 전까지 아무 일도 하지 않는다(M3). Live Activity 종료 · 분석 초기화는 해당 모듈이 생기면 잇는다.

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

## 팩 상세 (S5)

iOS 대응: `PackDetail/{Reducer/PackDetailFeature.swift,View/PackDetailView.swift,Components/PackRuleTags.swift}`, `Interface/Sources/PackDetailDelegate.swift`, `Core/FestiCoreLocation/Interface/Client/MapAppClient.swift`, 테스트 `PackDetailFeatureTests`. 설계 화면 2 · 팩 상세 데이터(D3) · 검토 반영 P2 · 쟁점 M8~M11.

1. 화면: 배경 `backgroundNormal`, `FCTopBar(Back, 제목)` 아래 `FCTab(["가는 길", "준비물", "팩 관리"])`. 탭 바와 좌우로 넘기는 본문(`HorizontalPager`)이 같은 `tab`을 쓴다. 칸 · 페이지 번호가 범위 밖이면 가는 길로 둔다. 제목은 넘겨받은 축제 이름이고 팩을 읽으면 팩 이름으로 바꾼다.
2. 처음 나타날 때 상세가 없고 읽는 중이 아니면 `Loading`으로 바꾸고 기기에 받은 팩 · 준비물 · 와이파이 자동 받기를 읽는다(네트워크 없음, 진입 경로와 관계없음). 팩이 없으면 `Missing` "기기에 받은 팩이 없어요"(버튼 없음), 예외면 `Failed` "팩을 불러오지 못했어요" + "다시 시도"(`Failed`일 때만 다시 읽음). 문구 `baseBodyRegular` · `labelNeutral`, 버튼 Secondary · Medium, 간격 `P12`, 가운데.
   - `Idle` · `Loading`: 스켈레톤 r16 높이 96 · 160 · 120(간격 · 패딩 `P16`).
3. 읽은 팩이 새 버전(`latestPackVersion > packVersion`)이면 변경 요약(`packChanges(id, packVersion)`)을 묻고, 요약의 `fromVersion`이 지금 버전과 같을 때만 보인다. 실패하면 아무것도 안 한다. 자동으로 받지 않는다.
4. 탭 본문: 세로 스크롤(간격 `P16`, 좌우 · 위 `P16`, 아래 `fcScrollBottomInset`). 카드 = `backgroundElevated` r16 · 패딩 `P16`. 카드 제목 `baseBodyBold` · `labelNormal`, 본문 `baseCalloutRegular` · `labelNeutral`, 각주 `baseFootnoteRegular` · `labelAssistive`(좌우 `P4`), 구분선 1 · `lineNormal`(왼쪽 `P16` 안쪽).
5. 가는 길: 정보가 없으면 카드 "가는 길 정보가 아직 없어요"(최소 높이 56, 가운데). 막차 · 셔틀 중 하나라도 있으면 `FCLastRideCard`(ISO 시각은 서울 24시간 `HH:mm`, 아니면 서버 값, 없으면 "-"). 카드 "셔틀"(본문 + 링크 버튼들) · "대중교통"(본문 + 지도 버튼) · "주차" · "팀 추천 메모". 대중교통이 없고 지도 주소만 있으면 지도 버튼만 카드에. 섹션 본문 = "가장 가까운 역은 {역}이에요. 걸어서 {N}분 정도 걸려요."(분 없으면 앞 문장만) + 서버 본문. 출처 줄 `FCSourceLine("출처: {이름} · 확인 MM/dd HH:mm")`(가는 길 출처가 없으면 팩 출처, 확인 시각 없으면 이름만).
6. 지도 버튼 "네이버 지도" · "카카오맵"(Secondary · Medium, 같은 폭, 간격 `P8`). 버튼이 보이면 설치 여부를 확인하고(`MapAppClient`, 패키지 `com.nhn.android.nmap` · `net.daum.android.map`, app 매니페스트 `<queries>`), 확인 뒤 없는 앱은 "{이름} 웹 ↗". 설치된 앱의 버튼은 그 패키지로 링크를 보내고(받지 못하면 Custom Tabs), 그 밖의 링크는 Custom Tabs로 연다(`ui/sharedui` `openExternalUrl`, M10 · M11).
7. 준비물: 카드 안 `FCChecklistProgress(체크 수, 행 수)`, `FCSectionHeader("챙길 것", "편집"/"완료")`, 행 묶음 r16. 행 = 숨기지 않은 팩 항목(필수는 항상) + 내 항목. 편집 중이면 `FCChecklistEditRow`(필수는 삭제 대신 태그), 아니면 `FCChecklistRow`(필수 표시). 편집 중이거나 행이 없으면 `FCAddItemRow`, 아니면 "내 항목 추가"(plus 20 · 간격 10 · `baseCalloutMedium` · `labelNeutral`, 높이 56, 누르면 편집 시작). 편집 중이고 필수 행이 있으면 각주 "필수 항목은 운영팀이 정한 거라 지울 수 없어요.". 반입 금지가 있으면 `FCSectionHeader("반입 금지")` + 카드(태그 줄바꿈 간격 `marginM6` + 띠 없는 출처 줄, 간격 `P12`).
8. 준비물 동작: 체크 토글 · 지우기(필수는 무시, 내 항목은 삭제, 팩 항목은 숨김, 체크도 뺀다) · 추가(앞뒤 공백 뺀 이름이 있을 때만, id `custom-<uuid 소문자>`, 입력 비움)마다 축제별로 저장한다. 저장 실패는 알리지 않는다. "완료"는 편집을 끄고 입력을 비운다. 준비물이 아닌 탭으로 가면 편집을 끈다.
9. 팩 관리: 팩 카드(썸네일 44 r8, 없으면 `FCThumbnail` 44 · 이름 `baseCalloutMedium` · 상태 `baseFootnoteRegular` · `labelAssistive`, 새 버전이면 `FCTag("업데이트", Warning)`). 상태 = "새 버전이 있어요 · v{latest}" / "최신 버전 · MM/dd HH:mm 확인" / "최신 버전 · v{ver}". 새 버전이면 `FCButton("업데이트 받기 · {용량}"(요약 없으면 "업데이트 받기") / "받는 중…", Primary, 전체 폭)` + 각주 "바뀐 내용: 시간표 N개 변경 · 셔틀 막차 HH:mm → HH:mm · 막차 HH:mm → HH:mm". "팩 정보" 묶음: 버전 "v{n} (새 버전 v{m})" · 마지막 확인(없으면 "-") · 용량(0.1MB 이상 "x.xMB", 아니면 "nKB" 최소 1) · 토글 "와이파이에서 자동으로 받기". 새 버전이 아니면 `FCButton("지금 업데이트 확인", Secondary, 전체 폭)`. 결과 문구 각주. "팩 삭제"(`baseBodyRegular` · `statusNegative`, 최소 높이 56 · 좌우 `P16` · r16 카드). 안내 "페스티벌이 끝나고 30일 뒤에 자동으로 삭제돼요. 내 일정과 체크한 준비물은 남아 있어요."(`baseCaption1Regular` · `labelAssistive`, 좌우 `P4`, M8). 업데이트 · 삭제 중에는 두 버튼과 "팩 삭제"를 막는다.
10. 업데이트 · 확인: 받는 중 · 지우는 중 · 상세 없음이면 무시. 결과 "새 버전 v{n}을 받았어요" / "최신 버전이에요" 뒤 `PackChanged`, 새 상세가 또 새 버전이면 변경 요약을 다시 묻는다. 팩이 사라졌으면 `Missing` + `PackChanged`. 실패 `RetryLater` "지금은 받는 사람이 많아요. 잠시 후 다시 시도해 주세요." · `Offline` "인터넷에 연결되어 있지 않아요. 연결을 확인한 뒤 다시 시도해 주세요." · 그 밖 "업데이트를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.".
11. 삭제: 받는 중 · 지우는 중이 아니면 `FCDialog("{제목} 팩을 삭제할까요?", "시간표·가는 길·준비물을 오프라인으로 볼 수 없게 돼요. 내 일정과 체크한 준비물은 남아 있어요.", 확인 "삭제", 취소 "취소")`. 확인하면 진행 중 업데이트를 취소하고 지운다. 성공 `PackDeleted`, 실패 "팩을 삭제하지 못했어요. 다시 시도해 주세요.".
12. 와이파이 자동 받기 토글은 바로 화면에 반영하고 저장한다(실패는 알리지 않음).
13. 이동은 SideEffect → Screen 콜백: `BackRequested` · `PackChanged` · `PackDeleted`. 마이 목록 다시 읽기(`onPackChanged` · `onPackDeleted`) · 상세 닫기 · 경로 연결과 라이브 보드 "가는 길" 진입은 T1이다.

## 엣지 케이스

- 진행 중 읽기를 취소하는 iOS `cancelInFlight`는 두지 않는다. `Loading` 중 `onAppear`는 무시하고, 팩 상세 결과로 다시 읽는 경우만 겹칠 수 있다(마지막 응답이 남는다).
- 바뀐 공연 수는 팩마다 차례로 묻는다(iOS는 동시에). 결과는 같다.
- 공지 읽기의 iOS `cancelInFlight`는 두지 않는다. `Idle` · `Failed`일 때만 읽어 요청이 겹치지 않는다.
- 닉네임 저장의 iOS `cancelInFlight`는 두지 않는다. 저장 중 다시 누르기를 무시해 요청이 겹치지 않는다.
- 팩 상세 읽기 · 변경 요약의 iOS `cancelInFlight`는 두지 않는다. 읽는 중 `onAppear`는 무시하고, 변경 요약은 받은 버전이 맞을 때만 반영한다. 진행 중 업데이트는 삭제 확인 때 Job을 취소한다(iOS `CancelID.update`).

## 제약 / 가정

- `feature`는 `core`를 의존하지 않으므로 `MM/dd HH:mm` 형식은 `ProfilePresentation`이 iOS `AppDateFormat.monthDayTime`과 같은 패턴 · 서울 시간대로 만든다.
- 설정 홈 팝업은 `FCDialogItem(cancelTitle = null)`로 iOS처럼 "확인" 하나다.
- 계정 화면은 iOS 자식 Store(`NicknameEditFeature`) 대신 `AccountSettingsViewModel` 하나가 시트 상태(`AccountSettingsDestination.Nickname`)를 가진다(Orbit은 자식 Store가 없다).
- 옮기지 않음: 분석 `profile_logout` · `profile_withdraw`(`service/analytics` 없음), `searchTapped` · `isSearchAvailable`(View 진입점 없음), 분석 `screen_view`(`service/analytics` 없음), 기수 카드 · 내 깃발(M7), 설정 홈 닫힘 때 알림 설정 보내기(알림 설정은 S6).
- `loginTapped`(시트 요청)는 iOS처럼 ViewModel에 두되 화면에는 진입점이 없다.
- 팩 상세는 iOS 첫 상태 `loading` 대신 `Idle`에서 시작해 처음 나타날 때 `Loading`으로 바꾼다(그리는 모양은 같다). 삭제 확인 팝업은 iOS `destination` 대신 `isDeleteConfirmationPresented`다.
- 지도 앱 설치 확인은 iOS URL 스킴(`nmap` · `kakaomap`) 대신 Android 패키지 이름이다. 네이버 지도는 NAVER Cloud Maps URL Scheme 문서의 인텐트 예제, 카카오맵은 Google Play 등록 id로 확인했다.

## 작업 모듈

| 모듈                        | 계층    | 변경 내용                                                                                 |
| --------------------------- | ------- | ----------------------------------------------------------------------------------------- |
| `feature/profile/interface` | feature | `AccountState`(앱 → 마이 계정 입력)                                                       |
| `feature/profile/sources`   | feature | `main/`(Contract · ViewModel · Screen · Presentation · 프리뷰), `settings/` · `appearance/`(S2), `account/`(S3), `notice/`(S4, `domain/notice/interface` 의존), `di/ProfileFeatureModule` |
| `feature/featureassembly`   | feature | `profileFeatureModule` 등록                                                               |
| `feature/web`               | feature | `LegalDocumentUrls`를 `interface`로 옮기고 https · host 검증(S2)                          |
| `feature/profile/sources` `packdetail/` | feature | 팩 상세 Contract · ViewModel · Screen · Presentation · `MapApp` · 프리뷰(S5) |
| `ui/sharedui`               | ui      | 외부 링크 열기 `openExternalUrl`(Custom Tabs, festival 목록 · 라이브 보드도 함께 씀, S5)   |
| `app`                       | app     | 매니페스트 `<queries>` 지도 앱 패키지 2개(S5)                                             |
| `app`                       | app     | `AppearanceModeSettings` 등록(`StoredAppearanceModeSettings`, S2)                         |
| `app`                       | app     | `AccountCoordinator`, `AppCoordinator` 계정 상태 · 로그아웃 · 탈퇴 · Google 바로 로그인(A1) |
| `feature/auth`              | feature | `LaunchLoginResult.Completed.profile` · `SignInFailed` 알림 · `LoginSheet(startsSignIn)`(A1) |

## 영향 범위

앱에는 아직 보이지 않는다(T1에서 마이 탭에 연결). `domain/festival`은 마이가 `FestivalReceivedPackUseCase` · `FestivalPackDetailUseCase.packChanges`를 읽고 팩 상세가 `FestivalPackDetailUseCase`로 팩 업데이트 · 삭제 · 준비물 · 자동 받기 설정을 쓴다(테스트는 가짜 구현, 서버 쓰기 없음). `domain/auth`는 `AuthProfile`을 읽고 계정 화면이 `AuthRepository.updateNickname`으로 닉네임만 바꾼다. `domain/notice`(`NoticeUseCase`)는 읽기만 한다.
