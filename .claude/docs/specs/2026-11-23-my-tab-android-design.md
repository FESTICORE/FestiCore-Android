# 마이 탭(Profile · 설정 · 계정 · 로그아웃 · 탈퇴) Android 이식 설계

- 날짜: 2026-11-23
- 이슈: #161
- 상태: 제안
- 기준: Android `develop` b6aac3e, iOS `09eda3b8`(로컬 `../FestiCore-iOS`, 읽기 전용), 백엔드 `ca96f72`(로컬 `../FestiCore-Backend`, 읽기 전용). iOS 행 번호는 `09eda3b8`에서 연 값이다. `~`가 붙은 근거는 파일 단위로만 확인했으므로 구현할 때 행을 다시 연다.
- 범위: `feature/profile`(신규, iOS `Feature/Profile`) · `domain/notice`(신규, iOS `Domain/NoticeDomain`) · `domain/auth` · `domain/festival`(받은 팩 · 팩 상세) · `service/apiendpoint` · `service/api` · `service/pack` · `service/device`(알림 설정, P0 게이트) · `core/storage` · `feature/web`(재사용) · `app`(`MainTabScreen` 마이 자리 · 계정 작업 · 화면 모드 적용)

## 목표

`MainTabScreen`의 마이 탭 빈 자리(`TabRoot`, `app/.../navigation/MainTabScreen.kt:54 · 64-71`)를 iOS 마이 탭으로 채운다. iOS 마이 탭 스택(`PSc:6-19`)의 화면은 아래가 전부다.

1. **마이(Profile, Figma More)** — 탭 루트. 비로그인 로그인 카드 · 기수/내 깃발 카드 · 내가 받은 팩 목록 · 오른쪽 위 설정 버튼.
2. **팩 상세(PackDetail, Figma Pack)** — 받은 팩 행 · 라이브 보드 "가는 길"(`PackDirections` 경계)에서 push. 가는 길 · 준비물 · 팩 관리 3탭.
3. **설정 홈(SettingsHome, S13-0)** — 앱(알림 · 잠금화면 · 화면 모드) · 계정 · 정보(공지사항 · 정보 출처 · 약관 3종) · 앱 버전.
4. **계정(AccountSettings, S13-D)** + **닉네임 변경 시트** — 내 정보 · 계정 삭제(탈퇴) · 로그아웃.
5. **화면 모드(Appearance, S13-J)** — 시스템 · 라이트 · 다크.
6. **알림(NotificationSettings, S13-A)** — 서버 기기 알림 설정 토글 7종(쟁점 M4: P0 게이트).
7. **잠금화면(LockScreen, S13-C)** + 대표 깃발 고르기 — Live Activity 설정(쟁점 M5: 라이브 액티비티 단계 전까지 숨김).
8. **공지사항 목록 · 상세**, **약관 · 정보 출처 문서(Web)**.
9. **로그아웃 · 탈퇴 실행**(iOS `AccountCoordinator`, 앱 계층).

이 문서가 만들지 않는 것: 기수 설정 흐름(iOS `Feature/Bearer` 5,138행, 마이에서 push — 별도 문서, 쟁점 M7), 준비물 안내 3단계 `PrepChecklist`(모듈은 `Feature/Profile`이지만 라이브 탭 PrepGuide에서 연다 — `2026-11-22-live-extras-android-design.md` 범위).

시각(수치 · 문구 · 토큰 · 에셋 · 접근성)은 iOS View와 같게, 플랫폼 관례(뒤로 · 시스템 설정 열기 · 외부 링크 · 앱 버전 · 권한)는 Android 방식을 따른다.

iOS 약어(`iOS` = `../FestiCore-iOS/Projects`, `P` = `iOS/Feature/Profile/Sources`, `N` = `iOS/App/Sources/Navigation`):

| 약어                                     | 경로                                                                                                                                                                                                       |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PF` · `PV`                              | `P/Main/Reducer/ProfileFeature.swift` · `P/Main/View/ProfileView.swift`                                                                                                                                    |
| `SF` · `SV`                              | `P/Settings/Reducer/SettingsHomeFeature.swift` · `View/SettingsHomeView.swift`                                                                                                                             |
| `AF` · `AV` · `NEF` · `NEV`              | `P/Account/Reducer/AccountSettingsFeature.swift` · `View/AccountSettingsView.swift` · `Reducer/NicknameEditFeature.swift` · `View/NicknameEditView.swift`                                                  |
| `ApM` · `ApF` · `ApV` · `AMK`            | `P/Appearance/Model/AppearanceMode.swift` · `Reducer/AppearanceSettingsFeature.swift` · `View/AppearanceSettingsView.swift` · `iOS/Core/FestiCoreStorage/Interface/Shared/AppearanceModeSharedKey.swift`   |
| `NoF` · `NoV`                            | `P/Notification/Reducer/NotificationSettingsFeature.swift` · `View/NotificationSettingsView.swift`                                                                                                         |
| `LSF` · `LSV` · `PPV`                    | `P/LockScreen/Reducer/LockScreenSettingsFeature.swift` · `View/LockScreenSettingsView.swift` · `View/PinnedFlagPickerView.swift`                                                                           |
| `NLF` · `NDF` · `NLV` · `NDV`            | `P/Notice/Reducer/NoticeListFeature.swift` · `NoticeDetailFeature.swift` · `View/NoticeListView.swift` · `NoticeDetailView.swift`                                                                          |
| `PDF` · `PDV`                            | `P/PackDetail/Reducer/PackDetailFeature.swift` · `View/PackDetailView.swift`                                                                                                                               |
| `PCo` · `PCoV` · `PRt` · `PSc`           | `N/Profile/ProfileCoordinator.swift` · `ProfileCoordinatorView.swift` · `ProfileRouting.swift` · `ProfileScreen.swift`                                                                                     |
| `AcC` · `ADS` · `AFS`                    | `N/Account/AccountCoordinator.swift` · `N/Shared/AccountDeviceSync.swift` · `N/Shared/AuthFlowState.swift`                                                                                                 |
| `MTC` · `MTCSc` · `MTAI` · `MTV` · `AuC` | `N/MainTab/MainTabCoordinator.swift` · `+Scope.swift` · `MainTabAuthInputs.swift` · `MainTabView.swift` · `N/Auth/AuthCoordinator.swift`                                                                   |
| `AlS`                                    | `iOS/UI/FestiCoreSharedUI/Sources/Alert/FCAlertState.swift`                                                                                                                                                |
| `WF` · `WV` · `LDU`                      | `iOS/Feature/Web/Sources/Reducer/WebFeature.swift` · `View/WebView.swift` · `Legal/LegalDocumentURL.swift`                                                                                                 |
| `ARI` · `AAPI` · `ASv`                   | `iOS/Domain/AuthDomain/Sources/Repository/AuthRepositoryImpl.swift` · `iOS/Service/API/Sources/Auth/AuthAPI.swift` · `iOS/Service/FestiCoreAuth/Interface/AuthService.swift`                               |
| `RPU` · `PDU` · `PLS`                    | `iOS/Domain/FestivalDomain/Sources/ReceivedPack/FestivalReceivedPackUseCaseImpl.swift` · `Sources/PackDetail/FestivalPackDetailUseCaseImpl.swift` · `Sources/PackDetail/FestivalPackLocalStore+Live.swift` |
| `NDS`                                    | `iOS/Service/FestiCoreDevice/Sources/NetworkDeviceNotificationSettingsService.swift`                                                                                                                       |

Android 약어: `MTS` = `app/src/main/kotlin/io/festicore/app/navigation/MainTabScreen.kt`, `MTCA` = `app/.../navigation/MainTabCoordinator.kt`, `ACo` = `app/.../navigation/AppCoordinator.kt`, `AR` = `domain/auth/interface/.../auth/AuthRepository.kt`, `AQ` = `service/apiendpoint/.../auth/AuthRequest.kt`, `SC` = `ui/designkit/.../tokens/FestiCoreSemanticColor.kt`. 백엔드 `BE` = `../FestiCore-Backend/src/modules`.

## 지금 Android 상태 (경계)

| 항목             | 지금                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 근거                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| 마이 탭          | `TabRoot`(탭 제목 글자만). 타임테이블도 같다                                                                                                                                                                                                                                                                                                                                                                                                                                                      | `MTS:54 · 64-71`                                                                                                  |
| 로그인 시트 문맥 | 마이 탭 기본 = `LoginContext.Profile`은 이미 있다                                                                                                                                                                                                                                                                                                                                                                                                                                                 | `MTCA:37-41`, `feature/auth/interface/.../LoginContext.kt`                                                           |
| 계정 상태 공유   | 없음. 현재 사용자 ID만 `CurrentUserProvider`(StateFlow)로 공유, 프로필 · 로딩 · 메시지는 화면이 볼 곳이 없다                                                                                                                                                                                                                                                                                                                                                                                      | `domain/auth/interface/.../CurrentUserProvider.kt`, `ACo:65-71`                                                      |
| 로그아웃 · 탈퇴  | 없음. `ACo` 주석 "로그아웃 · 탈퇴는 Android에 아직 없다"                                                                                                                                                                                                                                                                                                                                                                                                                                          | `ACo:70`                                                                                                          |
| 인증 API         | `auth/login` · `auth/refresh` · `GET/PATCH me`(가입 본문)만. `auth/logout` · `DELETE me` · 닉네임 PATCH 없음                                                                                                                                                                                                                                                                                                                                                                                      | `service/api/.../auth/AuthApi.kt:4-8`, `AQ:24-49`                                                                    |
| 인증 도메인      | `login` · `fetchProfile` · `signUp` · `clearSession(If)`. `AuthDomainError`에 `InvalidNickname` 없음                                                                                                                                                                                                                                                                                                                                                                                              | `AR:9-42`, `domain/auth/interface/.../AuthDomainError.kt`                                                            |
| 닉네임 규칙      | `AuthNicknameRule`(1~12자, 문자소 단위) 이미 있음                                                                                                                                                                                                                                                                                                                                                                                                                                                 | `domain/auth/interface/.../AuthNicknameRule.kt`                                                                      |
| 기기 등록        | 없음. `DeviceInstallationService`는 설치 ID만. 서버 기기 등록 · 알림 설정 · bind-user는 P0(푸시 · 기기 등록) 문서에서 보류                                                                                                                                                                                                                                                                                                                                                                        | `service/device/interface/.../DeviceInstallationService.kt:5`, `2026-11-22-timetable-android-design.md:404`          |
| 팩               | 다운로드 · 동기화 · 삭제 · 캐시 목록(`PackSyncService`)은 있음. 받은 팩 목록 · 팩 상세 · 변경 요약 · 준비물 저장 · 와이파이 자동 받기 · 30일 만료 삭제 없음. 팩 문서 `Directions`는 막차 · 정류장 이름 5개 키만                                                                                                                                                                                                                                                                                   | `service/pack/interface/.../PackSyncService.kt:59-74`, `domain/festival/interface/.../FestivalPackDocument.kt:57-64` |
| 웹 문서          | `feature/web`(`WebPage(url, title)` · 같은 호스트 https 정책) · 가입 약관용 `LegalDocumentUrls(BuildConfig.LEGAL_BASE_URL)` 있음                                                                                                                                                                                                                                                                                                                                                                  | `feature/web/interface/.../WebPage.kt`, `feature/auth/sources/.../LegalDocument.kt`                                  |
| 외부 링크        | Custom Tabs(`androidx.browser` 1.10.0)를 라이브 보드 · 축제 목록에서 이미 씀                                                                                                                                                                                                                                                                                                                                                                                                                      | `feature/festival/sources/.../LiveBoardScreen.kt:210`                                                                |
| 다크 모드        | 생성된 semantic 색이 `isSystemInDarkTheme()`을 읽는다. 앱 안 화면 모드 선택 없음. iOS 기본은 `dark`                                                                                                                                                                                                                                                                                                                                                                                               | `SC:22-25`, `AMK:13 · 37`                                                                                            |
| 분석             | `service/analytics` 없음                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | 모듈 목록                                                                                                            |
| designkit        | `FCTopBar` · `FCListRow`(값 · 선택 · 토글) · `FCPackRow` · `FCBearerPrompt` · `FCLoginButton` · `FCEmptyCard` · `FCButton` · `FCTextField` · `FCBanner` · `FCDialog` · `FCSheet` · `FCSkeletonView` · `FCTab` · `FCLastRideCard` · `FCSourceLine` · `FCChecklistProgress` · `FCChecklistRow` · `FCChecklistEditRow` · `FCAddItemRow` · `FCSectionHeader` · `FCTag` · `FCThumbnail` · `FCToggle` · `FCRemoteImage`(sharedui) 있음. 아이콘 `ic_gear_24` · `ic_ticket_24` · `ic_circle_info_16` 있음 | `ui/designkit` · `ui/sharedui` 파일 목록                                                                             |

## 수용 기준

1. 마이 탭을 누르면 "마이" 큰 제목과 오른쪽 위 설정(기어) 버튼, 받은 팩 목록이 보인다. 비로그인이면 맨 위에 로그인 카드(Google 버튼만, 쟁점 M1)가 보이고, Google 버튼은 시트 없이 바로 Google 로그인을 시작한다.
2. 받은 팩 목록이 iOS 규칙(로컬 캐시 먼저 → 서버 목록 갱신 → 새 버전 팩의 변경 공연 수, 실패 시 표시 유지)대로 갱신된다. iOS `ProfileFeatureTests`의 팩 · 로그인 · 설정 흐름을 같은 이름의 ViewModel 테스트로 옮긴다.
3. 팩 행 · 라이브 보드 "가는 길" 경계에서 팩 상세가 push되고 탭 바는 보인다. 가는 길 · 준비물(편집 · 추가 · 필수 유지) · 팩 관리(업데이트 · 확인 · 와이파이 자동 · 삭제 확인)가 iOS와 같다.
4. 설정 홈 · 계정 · 화면 모드 · 공지 · 약관 문서는 탭 바 없이 push되고 시스템 뒤로 · 상단 뒤로가 한 단계씩 돌아간다. 비로그인이면 계정 그룹이 없다.
5. 계정 화면에서 로그아웃 · 계정 삭제는 확인 팝업(iOS 문구) 뒤에만 실행된다. 성공하면 계정 화면 · 설정 홈이 닫히고 마이가 비로그인 상태가 된다. 실패하면 계정 화면에 iOS 오류 문구가 보인다. 탈퇴 중 세션이 만료되면 마이 위에 "로그인이 만료되어 회원 탈퇴 결과를…" 문구가 보인다.
6. 닉네임 시트가 가입과 같은 규칙(1~12자 · 카운터)으로 저장하고, 저장 중에는 시트를 닫을 수 없다. 저장 결과가 계정 줄 · 설정 홈 값에 바로 반영된다.
7. 화면 모드 선택이 앱 전체에 즉시 적용되고 재실행 뒤에도 유지된다.
8. `./gradlew testStageDebugUnitTest assembleStageDebug` 통과, `.claude/scripts/verify` 통과. 에뮬레이터 확인은 별도로 보고한다.

## 모듈과 책임

| Android 모듈                          | iOS 대응                                                                                                                     | 이번에 넣는 것                                                                                                                                                                            |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `service/api` · `service/apiendpoint` | `iOS/Service/API` · `APIEndpoint` (`AAPI`, `NoticeRequest`)                                                                  | `AuthApi.LOGOUT` · 탈퇴(`DELETE me`) · 닉네임(`PATCH me {nickname}`) 요청, `NoticeApi` · `NoticeRequest`, 팩 변경 요약 `GET festivals/{id}/pack/changes`                                  |
| `domain/auth`                         | `AuthDomain`(`ARI:89-210`)                                                                                                   | `signOut` · `withdraw` · `updateNickname` 계약 · 구현, `AuthDomainError.InvalidNickname`                                                                                                  |
| `domain/notice` (신규)                | `iOS/Domain/NoticeDomain`                                                                                                    | `NoticeSummary` · `NoticeDetail` · `NoticeUseCase` · Repository                                                                                                                           |
| `domain/festival`                     | `FestivalDomain/{ReceivedPack,PackDetail,Pack}` · `Entity/FestivalPackDetail` · `FestivalChecklist` · `NotificationSettings` | `FestivalReceivedPack` · `FestivalPackDetail` · `FestivalPackChanges` · `FestivalChecklist` · `ReceivedPackUseCase` · `PackDetailUseCase` · 만료 팩 삭제(쟁점 M8) · 알림 설정 UseCase(S6) |
| `service/pack`                        | `FestiCorePack`                                                                                                              | 팩 문서 매핑 확장(가는 길 섹션 · 지도 링크 · 메모 · 준비물 · 반입 금지 · 출처)                                                                                                            |
| `service/device`                      | `FestiCoreDevice`(`NDS`)                                                                                                     | 알림 설정 GET/PUT(S6, P0 머지 뒤)                                                                                                                                                         |
| `core/storage`                        | `FestiCoreStorage` `appearanceMode` · `FestivalPackLocalStore`                                                               | 화면 모드 · 축제별 준비물 · 와이파이 자동 받기 DataStore 저장소                                                                                                                           |
| `feature/profile` (신규)              | `iOS/Feature/Profile`                                                                                                        | 마이 · 팩 상세 · 설정 홈 · 계정 · 닉네임 · 화면 모드 · 공지 · 알림 화면, `ProfileCoordinator` · `ProfileFlow`                                                                             |
| `feature/web`                         | `iOS/Feature/Web`                                                                                                            | 재사용(약관 · 정보 출처 문서). 변경 없음                                                                                                                                                  |
| `app`                                 | `PCo` 소유자 `MTC` · `AcC` · `ADS` · `FestiCoreApp` 화면 모드                                                                | 마이 탭 자리 · 계정 상태 입력 · 로그아웃/탈퇴 실행 · 탭 바 규칙 · 화면 모드 루트 적용 · 라이브 `PackDirections` 경계 연결                                                                 |

새 모듈 이름은 iOS와 같게 둔다(CLAUDE.md 규칙 9): `Feature/Profile` ↔ `feature/profile`, `Domain/NoticeDomain` ↔ `domain/notice`. `feature/featureassembly`와 `architecture.md` 모듈 표를 S1 · D2 PR에서 같이 고친다.

## 화면 1: 마이 (Profile)

### 화면 구성 (`PV:31-66`)

- `FCTopBar(large, "마이")` + 오른쪽 겹친 설정 버튼(`PV:71-100`): `ic_gear_24` 24×24 · `labelNormal`, 누르는 영역 44×44, 오른쪽 여백 `marginM6`(Figma 오른쪽 16 − (44−24)/2), 접근성 라벨 "설정".
- 비로그인 · 메시지가 있으면 상단 바 아래 오류 문구(`PV:34-41`): `baseSubheadlineRegular` · `statusNegative` · 좌우 `paddingP20`.
- 세로 스크롤(간격 `paddingP24`, 좌우 `P16`, 위 `P8`, 스크롤 표시줄 숨김, 아래 탭 바 여백), 배경 `backgroundNormal`.
- 로그인 카드(`PV:104-142`, `showsLoginPrompt = profile == null && !hasSession && !isLoading` `PF:44-46`): 배경 `backgroundElevated` · r16, 패딩 상하 `P20` · 좌우 `P16`, 간격 `P12`. 제목 "로그인하고 더 함께 즐겨요"(`baseHeadlineBold`), 본문 "깃발 팔로우 · 크루 · 기수는 로그인이 필요해요. 타임테이블과 팩은 로그인 없이도 쓸 수 있어요."(`baseFootnoteRegular` · `labelNeutral`, 간격 `P4`). 버튼 `FCLoginButton(google)` — Apple 버튼은 빼고 문구는 그대로(쟁점 M1).
- 기수 · 내 깃발 카드(`PV:48-53 · 146-196`): 쟁점 M7(추천: 기수 문서 전까지 숨김).
- "내가 받은 팩"(`PV:198-247`): 그룹 제목 `baseFootnoteBold` · `labelAssistive`(좌우 `P4`), 오른쪽 "N개"(loaded일 때만). 카드 `backgroundElevated` · r16 안에 행 · 구분선(1, `lineNormal`, 좌우 `P16` 안쪽).
  - 불러오는 중: 스켈레톤 한 줄(44×44 원형 + 140×16 · 180×12 막대 r4, 패딩 상하 `P12` 좌우 `P16`, `PV:267-281`).
  - 실패: "받은 팩을 불러오지 못했어요"(`baseSubheadlineRegular` · `labelNeutral`) + `FCButton("다시 시도", secondary, medium)`(`PV:283-294`).
  - 빈 목록: `FCEmptyCard(icon = ic_ticket_24, "아직 받은 팩이 없어요", "페스티벌을 고르면 시간표 · 지도 · 가는 길을 오프라인으로 받아 둘 수 있어요.")` + 액션 "페스티벌 둘러보기"(primary) → 라이브 탭 축제 목록.
  - 행: `FCPackRow(name, meta, hasUpdate)` + 서버 썸네일 44×44 · r `12×44/56`, 왼쪽 `P16`에 겹쳐 그림(`PV:249-265 · 311-316`).
  - 행 meta(`PV:297-309`): 자동 받기 실패 "새 버전을 받지 못했어요 · v{latest}" → 새 버전 + 변경 수 "새 버전이 있어요 · 시간표 N개 변경" → 새 버전 "새 버전이 있어요 · v{latest}" → 확인 시각 없음 "최신 버전 · v{ver}" → "최신 버전 · {M/d HH:mm} 확인"(`monthDayTime`, 서울 시간대 — 구현 때 iOS 포맷 정의를 다시 연다).

### 상태 · 동작 (`ProfileContract.kt` · `ProfileViewModel`, iOS `PF`)

- State: `account(profile, hasSession, isLoading, message)` · `packs` · `packChangedSets: Map<id, Int>` · `packLoad(idle·loading·loaded·failed)`(`PF:25-41 · 76-81`).
- `onAppeared`: 이미 loading이면 무시, loaded면 표시 유지한 채 다시 읽기(`PF:225-237`). 읽기 = 로컬 캐시 목록 → 성공이면 서버 목록 갱신(`refreshReceivedPacks`, 실패하면 표시 유지) → `hasUpdate` 팩마다 변경 요약(실패 · 오프라인이면 아무것도 안 함), 갱신에 없는 팩의 변경 수는 지운다(`PF:325-338 · 379-399`). 로컬 실패는 이미 loaded면 failed로 바꾸지 않는다(`PF:401-404`).
- `onAccountChanged(account)`: 값을 바꾸고, 열린 설정 홈에 그대로 넘긴다. 세션을 잃었고 계정 화면이 열려 있으면 설정 홈째 닫는다(`PF:204-223`). Android는 이 입력을 `ProfileFlow`가 받는 `StateFlow<AccountState>`로 준다(쟁점 M13).
- `onPackTapped(id)` → `DetailRequested`. `onOpenPackDirections(festivalId, title)`(라이브 보드 경계) → 같은 팩 상세(`PF:276-289`).
- SideEffect: `LoginRequested` · `GoogleLoginRequested`(iOS `socialLoginRequested`, `MTCSc:214-216`) · `FestivalListRequested` · `SettingsRequested` · `PackDetailRequested(festivalId, title)`.
- 팩 상세의 삭제 → 목록 다시 읽기 + 상세 닫기, 업데이트 → 목록 다시 읽기(`PF:435-444`). 라이브 탭에도 `reloadCachedPacks`를 알린다(`MTCSc:263-265`).
- 옮기지 않음: `searchTapped` · `isSearchAvailable`(View에 진입점 없음), 분석 `screen_view`(Android `service/analytics` 없음 — 분석 모듈 단계에서 붙인다).

## 화면 2: 팩 상세 (PackDetail)

### 구성 (`PDV`)

- `FCTopBar(back, title = 팩 이름)` 아래 `FCTab(["가는 길", "준비물", "팩 관리"])`, 탭과 좌우 스와이프 페이지가 같은 선택을 공유한다(`PDV:27-29`, iOS 테스트 `tabBarIndexAndPageIndexShareTab` · `swipeChangesSameSelectedTab`). Android는 `HorizontalPager` + `FCTab`.
- 불러오는 중 스켈레톤 r16 3개(`PDV:~110-120`), 실패 "팩을 불러오지 못했어요" + "다시 시도", 기기에 팩 없음 "기기에 받은 팩이 없어요"(`PDV:60-62`).
- 가는 길(`PDV:~145-250`): 정보 없으면 "가는 길 정보가 아직 없어요". 막차 카드 `FCLastRideCard`(서울 24시간 표기), 섹션 카드 "셔틀" · "대중교통" · "주차"(본문 + 링크 버튼), 대중교통 본문 "가장 가까운 역은 {역}이에요. 걸어서 {N}분 정도 걸려요."(`PDV:507-511`), "팀 추천 메모", `FCSourceLine("출처: {이름} · 확인 {M/d HH:mm}")`(`PDV:523-524`). 지도 버튼 "네이버 지도" · "카카오맵" — 앱이 없으면 "{이름} 웹 ↗"(`PDV:214-246`, 쟁점 M10). 링크는 Custom Tabs로 연다.
- 준비물(`PDV:~255-330`): `FCChecklistProgress(checked, total)`, `FCSectionHeader("챙길 것")` + 액션 "편집"/"완료", 행 `FCChecklistRow` / 편집 중 `FCChecklistEditRow`, 편집 중 `FCAddItemRow`, "내 항목 추가"(아이콘 20 · 간격 10, `PDV:571-574`), 각주 "필수 항목은 운영팀이 정한 거라 지울 수 없어요.", `FCSectionHeader("반입 금지")` + 출처.
- 팩 관리(`PDV:340-420`): 팩 카드(썸네일 44 · 이름 `baseCalloutMedium` · 상태 `baseFootnoteRegular`, 새 버전이면 `FCTag("업데이트", warning)`), 새 버전이면 `FCButton("업데이트 받기 · {용량}" / "받는 중…", primary, 전체 폭)` + "바뀐 내용: 시간표 N개 변경 · 셔틀 막차 HH:mm → HH:mm · 막차 HH:mm → HH:mm", "팩 정보"(버전 "v{n} (새 버전 v{m})" · 마지막 확인 · 용량 "{x.x}MB"/"{n}KB" · 토글 "와이파이에서 자동으로 받기"), 새 버전이 아니면 `FCButton("지금 업데이트 확인", secondary)`, 결과 문구, "팩 삭제"(`baseBodyRegular` · `statusNegative`, 높이 56 · r16 카드), 안내 "페스티벌이 끝나고 30일 뒤에 자동으로 삭제돼요. 내 일정과 체크한 준비물은 남아 있어요."(`baseCaption1Regular`, 쟁점 M8).

### 상태 · 동작 (`PackDetailViewModel`, iOS `PDF`)

- 처음 보일 때만 읽는다(상세 · 준비물 · 자동 받기). 상세가 없으면 `missing`. 새 버전이면 변경 요약을 받고 받은 버전과 `fromVersion`이 같을 때만 반영(`PDF:~`).
- 준비물: 체크 토글 · 지우기(필수는 무시, 내 항목은 삭제, 팩 항목은 숨김) · 추가(`custom-<uuid 소문자>`) 마다 축제별로 저장. 행 = 숨기지 않은 팩 항목(필수는 항상) + 내 항목(`PDF:~ checklistRows`). 다른 탭으로 가면 편집을 끈다.
- 업데이트 · 확인: 진행 중이거나 삭제 중이면 무시. 결과 문구 "새 버전 v{n}을 받았어요" / "최신 버전이에요" / "지금은 받는 사람이 많아요. 잠시 후 다시 시도해 주세요." / "인터넷에 연결되어 있지 않아요. 연결을 확인한 뒤 다시 시도해 주세요." / "업데이트를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.". 업데이트 뒤 팩이 사라졌으면 `missing`.
- 삭제: `FCDialog("{이름} 팩을 삭제할까요?", "시간표·가는 길·준비물을 오프라인으로 볼 수 없게 돼요. 내 일정과 체크한 준비물은 남아 있어요.", 확인 "삭제")`(`AlS:111-117`). 확인하면 진행 중 업데이트를 취소하고 삭제, 실패 "팩을 삭제하지 못했어요. 다시 시도해 주세요.". 성공 → `PackDeleted`.
- 업데이트 실패 분류: `service/pack` 재시도 지연 · 재연결 → retryLater, 전송 실패(`PackSyncError.isTransportFailure`) → offline(`PDU:~`). 업데이트 뒤 서버 목록을 다시 받아 최신 여부를 다시 판단한다.

## 화면 3: 설정 홈 (SettingsHome)

- `FCTopBar(back, "설정")`, 스크롤(간격 `P24`, 좌우 `P16`, 위 `P16`)(`SV:23-56`). 그룹(`SV:121-144`): 제목 `baseFootnoteBold` · `labelAssistive`(좌우 `P4`) + `backgroundElevated` · r16 카드, 구분선 좌우 `P16` 안쪽.
- 앱(`SV:71-80`): "알림"(쟁점 M4) · "잠금화면"(쟁점 M5) · "화면 모드"(값 = `시스템 설정`/`라이트`/`다크`, `ApM:31-37`).
- 계정(로그인했을 때만, `SF:41-43`): "계정"(값 = 닉네임).
- 정보(`SV:89-101`): "공지사항" · "정보 출처" · "이용약관" · "개인정보 처리방침" · "위치기반서비스 이용약관". 문서는 `LEGAL_BASE_URL` + `sources` · `terms` · `privacy` · `location`을 `feature/web` `WebScreen`으로 앱 안에서 연다(iOS `SF:195-221 · 278-293`, 제목은 줄 이름 그대로). 기준 주소가 비어 있으면 `FCDialog("문서를 열 수 없어요", "잠시 후 다시 시도해 주세요.")`, 이미 떠 있으면 다시 띄우지 않는다(`AlS:102-107`).
- 버전(`SV:104-111 · 156-158`): "FESTICORE v{versionName}"(`baseCaption1Regular` · `labelAssistive`, 가운데). Android는 `PackageManager.getPackageInfo(...).versionName`(또는 `BuildConfig.VERSION_NAME`)을 쓴다.
- 동작: 알림 화면을 닫을 때(뒤로 · 스와이프 · 설정 홈째 닫힘) 저장하지 못한 변경을 화면 수명과 무관하게 끝까지 보낸다(`SF:256-276`, `PF:449-454`) — S6.

## 화면 4: 계정 · 닉네임 (AccountSettings · NicknameEdit)

### 계정 (`AV:24-193`, `AF`)

- `FCTopBar(back, "계정")`, 스크롤(간격 `P24`, 좌우 `P16`, 위 `P16`) + 아래 고정 `FCButton("로그아웃", ghost, medium)`(좌우 · 아래 `P16`, 처리 중 비활성).
- "내 정보"(`AV:73-105`): 프로필 있으면 카드[`FCListRow("닉네임", value = 닉네임 ?: "설정 안 됨")` · 구분선 · 정보 행 "로그인" / `Google`(높이 56, `baseBodyRegular`, 값 `labelNeutral`)]. 프로필 없고 로딩 중이면 스켈레톤 높이 113 · r16. 그 외 "계정 정보를 확인할 수 없습니다" + `FCButton("계정 정보 다시 확인", secondary, medium)` → 세션 프로필 재확인.
- 탈퇴(`AV:108-130`): "계정 삭제"(`baseBodyRegular` · `statusNegative`, 높이 56 · r16 카드, 처리 중 비활성) + "계정을 삭제하면 내 일정, 크루, 깃발 기록이 모두 지워지고 되돌릴 수 없어요."(`baseFootnoteRegular` · `labelAssistive`).
- 메시지(로딩 아닐 때): `baseSubheadlineRegular` · `statusNegative`.
- 확인 팝업(가운데 정렬, 딤 탭으로 닫힘, `AV:55-66`): 로그아웃 "로그아웃할까요?" / "받은 팩과 내 일정은 이 기기에 그대로 남아 있어요." / "로그아웃"(`AlS:129-135`), 탈퇴 "회원 탈퇴" / "계정과 로그인 정보가 삭제됩니다. 이 작업은 되돌릴 수 없습니다." / "탈퇴"(`AlS:93-99`). 처리 중이거나 같은 팝업이 떠 있으면 다시 띄우지 않는다(`AF:176-183`). 확인 → `LogoutRequested` · `WithdrawRequested`(`AF:144-152`).
- 닉네임 줄: 프로필이 있고 로딩 중이 아닐 때만 시트를 연다(`AF:113-116`). 저장 성공 → 화면의 프로필을 갱신하고 `NicknameUpdated(user)`(`AF:159-164`).

### 닉네임 시트 (`NEV:23-47`, `NEF`)

- `FCSheet` 안: "닉네임 변경"(`baseTitle2Bold`), `FCTextField(label "닉네임", placeholder "닉네임", counter "N / 12", helper "1~12자로 입력해 주세요", error)`, `FCButton("저장"/"저장하는 중", primary, 전체 폭)`. 좌우 `P20`, 아래 `P24`, 간격 `P24`. 저장 중에는 시트 닫기(끌기 · 딤 · 뒤로)를 막는다.
- 입력은 `AuthNicknameRule.limited`, 저장은 trim 후 검사 — 실패 "닉네임은 1~12자로 입력해 주세요.", 서버 `invalidNickname`도 같은 문구, 그 외 "닉네임을 바꾸지 못했어요. 다시 시도해 주세요."(`NEF:98-154`).

## 화면 5: 화면 모드 (Appearance)

- `FCTopBar(back, "화면 모드")`, 그룹 "화면 모드" 카드에 `FCListRow(title, isSelected)` 3행 "시스템 설정과 같게" · "라이트" · "다크", 각주 "시스템 설정과 같게를 고르면 기기의 라이트·다크 설정을 따라 자동으로 바뀌어요."(`ApV:22-103`, `ApM:22-28`).
- 저장 값 `system` · `light` · `dark`, 알 수 없는 값은 `dark`(`ApM:17-19`), 기본 `dark`(`AMK:37`). 고르면 즉시 저장 · 적용(`ApF:73-75`). 적용 방식 · 기본값은 쟁점 M6.

## 화면 6: 알림 (NotificationSettings) — P0 게이트

- `FCTopBar(back, "알림")`. 불러오는 중 스켈레톤 높이 112 · r16 3개, 실패 "알림 설정을 불러오지 못했어요" + "다시 시도"(`NoV:42-61`).
- 기기 알림이 꺼져 있으면 `FCBanner("기기 설정에서 알림이 꺼져 있어요 · 설정 열기")` → Android는 `Settings.ACTION_APP_NOTIFICATION_SETTINGS`(+ `EXTRA_APP_PACKAGE`). 허용 상태는 `NotificationManagerCompat.areNotificationsEnabled()`. 꺼져 있으면 토글을 모두 꺼진 채로 보이고 막는다(`NoV:65-67 · 133-155`).
- 그룹(`NoV:68-90`): "깃발 · 크루"[팔로우한 깃발 이동 · 크루원 상태 · 크루 모여! 요청] "크루원 상태는 소리 없이 와요. 모여! 요청만 소리로 알려 드려요." / "공연 · 귀가"[공연 10분 전 · 귀가 알림] "귀가 알림은 공연이 끝나기 1시간 전에 막차와 셔틀 시간을 알려 드려요." / "팩"[팩 업데이트] "시간표가 바뀌면 받은 팩을 새로 받으라고 알려 드려요." / 서버가 `officialNews`를 줄 때만 "축제 소식"[공식 인스타그램 새 게시물] "고정한 축제의 공식 인스타그램에 새 게시물이 올라오면 알려 드려요.".
- 동작(`NoF:127-231`): 보일 때마다(포그라운드 복귀 포함 — Android `ON_RESUME`) 허용 상태와 서버 값을 다시 읽는다. 토글은 즉시 반영하고 한 번에 한 요청만 보낸다. 보내는 중 바뀐 값은 모아 다음에 보낸다. 저장을 시작하면 진행 중 조회를 취소한다. 저장 실패 → 마지막 확인 값으로 되돌리고 "알림 설정을 저장하지 못했어요. 다시 시도해 주세요." + 재조회. 저장 · 보낼 변경이 있으면 조회 결과로 덮지 않는다.

## 화면 7: 잠금화면 (LockScreen) — 숨김

iOS는 Live Activity 목록 · "모두 끄기" · "당일 자동 시작" · 대표 깃발 고르기(`PUT me/pinned-flag`)를 둔다(`LSF`, `LSV:22-206`, `PPV`). Android는 Live Activity를 진행 중 알림(Live Updates)으로 옮기는 별도 단계에서 다룬다. 그 단계 전까지 설정 홈의 "잠금화면" 줄을 숨긴다(쟁점 M5). 깃발 탭 설계의 결정 F4-1(대표 깃발은 라이브 액티비티 단계)과 같은 원칙이다.

## 화면 8: 공지 · 문서

- 공지 목록(`NLV:36-89`, `NLF`): `FCTopBar(back, "공지사항")`. 처음 보일 때만 읽는다. 불러오는 중 스켈레톤 높이 56 · r16 4개(간격 `P8`, 라벨 "공지사항을 불러오는 중"), 실패 "공지사항을 불러오지 못했어요" + "다시 시도", 빈 목록 "등록된 공지사항이 없어요". 행 `FCListRow(제목, value = yyyy.MM.dd)`, 구분선 왼쪽 `P16`, 카드 r16 · 패딩 `P16`. 날짜는 서울 기준(`AppDateFormatTests` `noticeDay` — UTC 자정 경계).
- 공지 상세(`NDV:36-98`, `NDF`): 제목 `baseTitle3Bold`, 날짜 `baseCaption1Regular` · `labelAssistive`, 구분선, 본문 `baseBodyRegular`(좌우 · 위 `P20`, 간격 `P16`). 스켈레톤 24 · 90×14 · 180, 실패 문구 + "다시 시도". 상단 바 제목은 "공지사항".
- 문서: 기존 `feature/web` `WebScreen`(로딩 · "문서를 불러오지 못했어요" · 다시 시도, 같은 호스트 https만 — iOS `WF` · `WV:21-77`). 스테이지 `legal/{terms,privacy,location,sources}` 모두 200 HTML.

## 로그아웃 · 탈퇴 실행 (iOS `AcC`, 앱 계층)

iOS는 마이 Feature가 `logoutRequested` · `withdrawRequested`를 올리면 `MainTabCoordinator`가 `AccountCoordinator`로 넘긴다(`MTCSc:254-258`). Android는 `app/navigation/AccountCoordinator`(iOS와 같은 자리 · 이름)로 옮기고 `AppCoordinator`가 소유한다.

1. 시작 조건: 세션 있음 · 처리 중 아님. 세대(`generation`)를 올리고 메시지를 비우고 `isLoading = true`(`AcC:140-153`).
2. 순서(`AcC:155-194`): 기기 사용자 연결 해제(`DELETE devices/bind-user`, 실패해도 계속 — `ADS:30-41`, 쟁점 M3) → (로컬 Live Activity 종료 — Android 진행 중 알림 단계에서 진행 중 알림 취소로 잇는다) → `signOut()` / `withdraw()`. 취소되면 아무것도 보내지 않는다.
3. 실패 시: 이미 로그인이 풀렸으면 세션 만료 처리로 넘긴다. 아니면 해제했던 기기 연결을 복원(`linkDeviceToCurrentUser`)하고 실패 결과를 보낸다.
4. 결과(`AcC:196-228`): 세대가 다르면 버린다. 성공 → 계정 화면 데이터 비우기(현재 사용자 ID null, 마이 · 깃발 · 라이브 · 타임테이블 계정 데이터 정리 `MTC:527-536`) + 분석 초기화(Android 분석 모듈 생기면). `notAuthenticated` → 같은 정리 + 탈퇴면 메시지 "로그인이 만료되어 회원 탈퇴 결과를 확인할 수 없어요. 다시 로그인해 계정 상태를 확인해 주세요.". 그 외 실패 → "회원 탈퇴에 실패했습니다. 다시 시도해 주세요." / "로그아웃에 실패했습니다. 다시 시도해 주세요."(`AFS:207 · 218-219`). 로그인 시트 상태는 되돌리지 않는다(`AcC:231-242`).
5. 세션이 만료되면 진행 중 작업을 취소한다(`AcC` `cancelOperation`).
6. 도메인(`ARI:119-210`): iOS는 서버 요청 전 "되돌림 차단" 기록을 디스크에 남기고, 결과 불명 · 로컬 삭제 실패 · 재시작 뒤 정리를 상태 기계로 다룬다(`ASv:39-58`). Android 방식은 쟁점 M2.

## 서버 계약

모두 `api/v1`, 응답 `{code, message, data}`이고 `code == "SUCCESS"`를 본다(204 제외). stage(`https://festicore-dev.up.railway.app/`)에는 **인증 없는 GET만** 보냈다(2026-11-23). 쓰기 · 인증 요청은 보내지 않았고, 쓰기 계약은 백엔드 코드와 iOS 요청 정의를 대조한 값이다.

| 용도             | 요청                                                                  | 인증                                                            | 근거 · stage 확인                                                                                                                                                                                                                              |
| ---------------- | --------------------------------------------------------------------- | --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 프로필           | `GET me`                                                              | Bearer                                                          | 이미 옮김. stage 익명 → `UNAUTHORIZED "인증 토큰이 없습니다."`                                                                                                                                                                                 |
| 닉네임           | `PATCH me {nickname}`(1~12자, `MinLength(1)` · `MaxLength(12)`)       | Bearer                                                          | `BE/auth/controller/me.controller.ts:29-41 · 84-105`. 200 사용자, 400 길이, 401. iOS는 trim 후 보냄(`ARI:89-106`)                                                                                                                              |
| 로그아웃         | `POST auth/logout`(본문 없음)                                         | Bearer                                                          | `BE/auth/controller/auth.controller.ts:110-126` → 200 `{success: true}`, 401. 서버는 계정의 refresh 토큰 전부 폐기 + 계정 기기들의 푸시 토큰 · 알림 구독 해제(`BE/auth/service/auth.service.ts:137-162`)                                       |
| 탈퇴             | `DELETE me`(본문 없음)                                                | Bearer                                                          | `me.controller.ts:107-121` → 204 본문 없음, 401. 마지막 owner 관리자 계정은 409 "마지막 owner 계정은 탈퇴할 수 없습니다."(`auth.service.ts:268-275`), 소유 크루 정리 포함                                                                      |
| 공지 목록        | `GET notices`                                                         | 없음                                                            | `BE/notices/notices.controller.ts` `no-store`, 최신 100건, `items[{id, title, publishedAt}]`. stage 200 · `items: []`(공지 없음)                                                                                                               |
| 공지 상세        | `GET notices/{id}`(UUID)                                              | 없음                                                            | `{id, title, content, publishedAt}`, 없거나 미게시 · 잘못된 ID 404. stage 없는 UUID → `NOT_FOUND "공지를 찾을 수 없습니다."`, UUID 아님 → 404                                                                                                  |
| 약관 · 출처      | `GET legal/{terms,privacy,location,sources}`                          | 없음                                                            | `BE/legal/legal.controller.ts:85-130`. stage 4개 모두 200 `text/html`                                                                                                                                                                          |
| 팩 변경 요약     | `GET festivals/{id}/pack/changes?from=`                               | 없음                                                            | `BE/public/public-festival.controller.ts:389`. stage `fst_00e0f4f1d8065a93` `from=1` → `{fromVersion, toVersion, sizeBytes, changedSets, lastShuttleAt{before, after}, lastTrainAt{…}, lastDepartures}`                                        |
| 팩 문서(확장 키) | `GET festivals/{id}/pack`                                             | 없음                                                            | 이미 받음. stage 키 `directions{lastDepartures, lastShuttleAt, lastTrainAt, location, mapLinks, memo, parking, shuttle, source, transit}` · `checklist[{id, label, note, required}]`(7) · `rules[String]`(6) · `source{name, url, verifiedAt}` |
| 알림 설정        | `GET` · `PUT devices/notification-settings`(보낸 항목만 변경)         | `X-Device-Id` + 기기 인증(`DeviceAuthGuard`), 로그인이면 Bearer | `BE/device/device.controller.ts:56-64 · 214-253`, iOS `NDS`(재시도 0). stage 익명 → `UNAUTHORIZED`. P0 뒤                                                                                                                                      |
| 기기 연결 해제   | `DELETE devices/bind-user` + `X-Device-Unbind-Key`(UUID v4 재시도 키) | 기기 인증                                                       | `device.controller.ts:142-155`. P0 뒤                                                                                                                                                                                                          |
| 내 깃발          | `GET me/bearer-flags`                                                 | Bearer                                                          | `BE/flags/flags.controller.ts:847`. 기수 문서(쟁점 M7)                                                                                                                                                                                         |

- 인증 요청은 재시도하지 않는다(Android `AQ` 주석과 같음).
- 공지 · 약관은 익명이라 비로그인도 본다. 계정 · 닉네임 · 로그아웃 · 탈퇴는 로그인 필요.

## 비로그인 · 기기 · 권한

- 비로그인: 마이(로그인 카드 + 받은 팩) · 팩 상세 · 설정 홈(계정 그룹 없음) · 화면 모드 · 공지 · 문서 가능. 알림은 기기 기준이라 비로그인도 쓴다(P0 뒤).
- 로그인 카드의 Google 버튼은 시트 없이 바로 로그인을 시작한다(iOS `startDirectLogin`, `AuC:161-172`). 사용자가 취소하면 아무 화면 없이 끝나고, 실패하면 오류 문구가 보이게 로그인 시트(문맥 `profile`)를 연다(`AuC:298-309`). 신규 계정이면 기존 가입 화면으로 이어진다.
- 권한: `POST_NOTIFICATIONS`는 요청하지 않고 상태만 읽는다(iOS와 같음). 와이파이 자동 받기는 `ACCESS_NETWORK_STATE`(일반 권한, 쟁점 M9). 지도 앱 확인은 Android 11+ 패키지 가시성 `<queries>`(쟁점 M10). 위치 · 카메라 없음.

## 화면 전환

| 출발                                        | 동작                                               | iOS                            | Android                                                                                                               |
| ------------------------------------------- | -------------------------------------------------- | ------------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| 마이 탭 루트                                | 마이                                               | `PRt:31-37`                    | `ProfileCoordinator` routes = `[ProfileRoute.Profile]`                                                                |
| 팩 행 · 라이브 보드 "가는 길"               | 팩 상세 push(마이 탭 선택)                         | `PF:276-289`, `MTCSc:125-134`  | `ProfileRoute.PackDetail(festivalId, title)`. 보드 `PackDirections` 경계 → `MainTabCoordinator`가 마이 탭 선택 + 요청 |
| 설정 버튼                                   | 설정 홈 push                                       | `PF:291-298`                   | `ProfileRoute.Settings`                                                                                               |
| 설정 홈 줄                                  | 계정 · 화면 모드 · 공지 · 문서 · 알림 push(하나만) | `PRt:68-92`                    | `ProfileRoute.{Account, Appearance, NoticeList, LegalDocument(WebPage), Notifications}`                               |
| 공지 행                                     | 공지 상세 push                                     | `PRt:83-88`                    | `ProfileRoute.NoticeDetail(id, title)`                                                                                |
| 닉네임 줄                                   | 시트                                               | `AV:52-54`                     | `FCSheet`(다이얼로그 State)                                                                                           |
| 로그아웃 · 탈퇴 · 팩 삭제 · 문서 없음       | 확인 팝업                                          | `AlS`                          | `FCDialog`                                                                                                            |
| 상단 뒤로 · 시스템 뒤로                     | 한 단계 pop                                        | `PCo:162-180`                  | `NavDisplay` `onBack`                                                                                                 |
| 로그아웃 · 탈퇴 성공(계정 화면이 열려 있음) | 설정 홈째 닫고 마이                                | `PF:211-216`                   | 계정 입력에서 세션을 잃으면 `[Profile]`로 되돌림                                                                      |
| 다른 계정으로 바뀜                          | 마이 아래 화면 모두 닫기                           | `MTC:527-536`                  | 같음                                                                                                                  |
| 빈 팩 "페스티벌 둘러보기"                   | 라이브 탭 축제 목록                                | `MTCSc:245-249`                | `LiveTabRequest.FestivalList`(이미 있음)                                                                              |
| 로그인 카드                                 | Google 바로 로그인                                 | `MTCSc:214-216`                | `AppCoordinator`에 바로 로그인 진입 추가(L1 시트 흐름 재사용)                                                         |
| 기수 카드 · 내 깃발                         | 기수 흐름 push                                     | `PCo:148-160`, `MTCSc:218-243` | 쟁점 M7                                                                                                               |

## 탭 바 규칙

- 마이 루트 · 팩 상세는 탭 바를 보인다(Figma Pack A~H). 설정 홈과 그 아래(계정 · 화면 모드 · 잠금화면 · 알림 · 문서 · 공지 목록 · 상세)는 숨기고 자리도 비운다(`PRt:6-16`, `MTC:69-71`).
- 마이의 확인 팝업 · 팩 삭제 팝업 · 설정 홈 팝업 · 닉네임 시트 · 계정 확인 팝업이 떠 있는 동안 숨긴다(딤이 탭 바를 덮지 못함, `PRt:20-28`, `MTC:104-108`).
- 기수 흐름이 열려 있으면 숨긴다(`PRt:63-65`) — 기수 문서에서.
- 로그인 시트가 떠 있는 동안 숨긴다(이미 있음, `MTS:58`).
- 같은 탭을 다시 눌러도 루트로 pop하지 않는다(깃발 문서와 같음). 마이 탭을 고를 때 동기화할 것은 없다(`MTC:345-347`).
- 탭을 떠나도 마이 스택을 유지한다. `ProfileCoordinator`를 탭 밖(`AppFlow`)에서 소유한다(깃발 탭과 같은 방식).
- Android `MainTabScreen`은 지금 라이브 탭만 숨김 콜백을 받는다(`MTS:44-60`). 마이 흐름도 `onHidesTabBarChanged`를 받도록 넓힌다.

## iOS → Android 적응

| iOS                                                                          | Android                                                                                                      | 이유                                                    |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------- |
| `ProfileFeature.destination`을 경로로 투영하는 `ProfileCoordinator`(TCAFlow) | `ProfileCoordinator` ViewModel + `ProfileFlow`(`NavDisplay`), 화면별 ViewModel                               | `.claude/docs/navigation.md`. Orbit은 자식 Store가 없다 |
| `accountChanged`를 부모가 자식 Store로 전달                                  | `ProfileFlow`에 `StateFlow<AccountState>` 인자 → 각 ViewModel `onAccountChanged`                             | 계정 상태는 앱이 소유(쟁점 M13)                         |
| `AccountCoordinator` + `AuthFlowState` 세대                                  | `app/navigation/AccountCoordinator` + 세대 `Int`                                                             | 같은 동작, 같은 이름                                    |
| `UIApplication.openSettingsURLString`                                        | `Settings.ACTION_APP_NOTIFICATION_SETTINGS`                                                                  | 알림 설정으로 바로 간다                                 |
| `openURL`(가는 길 링크 · 지도 웹)                                            | Custom Tabs(`androidx.browser`, 이미 있음)                                                                   | 외부 링크 관례                                          |
| `WebFeature`(약관 · 출처)                                                    | 기존 `feature/web` `WebScreen`                                                                               | 가입 약관과 같은 앱 안 문서                             |
| `Bundle CFBundleShortVersionString`                                          | `PackageInfo.versionName` / `BuildConfig.VERSION_NAME`                                                       | 플랫폼 버전 표기                                        |
| `preferredColorScheme` + `@Shared(.appearanceMode)`                          | DataStore 값 + 앱 루트 `LocalConfiguration` uiMode 덮어쓰기(쟁점 M6)                                         | 생성된 `SC`가 `isSystemInDarkTheme()`을 읽음            |
| `scenePhase == .active` 재조회                                               | `LifecycleEventEffect(ON_RESUME)`                                                                            | 시스템 설정에서 돌아올 때                               |
| `fcModal` · `fcSheetDismissDisabled`                                         | `FCSheet` + 저장 중 닫기 막기                                                                                |                                                         |
| `fcAlert(textAlignment: .center)`                                            | `FCDialog`(가운데 정렬)                                                                                      |                                                         |
| `UserDefaults` 준비물 · 자동 받기                                            | `core/storage` DataStore(키 `FestiCore.pack.checklist.<id>` · `FestiCore.pack.autoDownloadOnWiFi` 같은 이름) |                                                         |
| `swipeBackButtonHidden()`                                                    | 시스템 뒤로 = pop                                                                                            | Android 관례                                            |
| `MapAppClient.isInstalled`(`nmap` · `kakaomap` 스킴)                         | `PackageManager` 패키지 조회 + `<queries>`(쟁점 M10)                                                         | 패키지 가시성                                           |
| `FCLoginButton(.apple)` · `.google`                                          | Google만                                                                                                     | `ios-parity.md`                                         |
| 분석 `screen_view` · `profile_logout` · `profile_withdraw`                   | 넣지 않음(분석 모듈 단계)                                                                                    | `service/analytics` 없음                                |
| Live Activity 종료(`ADS:49-53`)                                              | 없음                                                                                                         | 라이브 액티비티 단계에서 진행 중 알림 취소로 잇는다     |

## PR 묶음 (데이터 → 화면들 → 탭 연결)

| #   | PR                                 | 모듈                                                                                                                                                                   | 검증 (iOS 테스트 대응)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| --- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| D1  | 계정 API와 도메인                  | `service/api`(`LOGOUT`) · `service/apiendpoint`(`logout` · `withdraw` · `patchNickname`) · `domain/auth`(`signOut` · `withdraw` · `updateNickname`, `InvalidNickname`) | iOS `AuthDomainTests` 중 로그아웃 · 탈퇴 · 닉네임 사례(26건 중 해당분, 쟁점 M2 결정에 맞춰), `AuthRequestTests` 2건. MockEngine: 204 빈 본문, 401 → 세션 정리, 잘못된 닉네임 요청 안 함                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| D2  | 공지 도메인                        | `domain/notice`(신규) · `service/apiendpoint` `NoticeRequest` · `core/utility` 날짜 형식                                                                               | `NoticeDomainTests` 4건(`listMapsPublishedItemsAndSendsPublicGet` · `detailMapsContentAndSendsPublicGet` · `invalidEnvelopeAndNullableFieldsDoNotProduceDomainContent` · `useCaseUsesRepositoryAndLiveDependenciesResolve`), `NoticeRequestTests` 2건, `AppDateFormatTests` `noticeDay` 2건                                                                                                                                                                                                                                                                                                                                                                                                |
| D3  | 받은 팩 · 팩 상세 도메인           | `service/pack`(문서 키 확장) · `service/apiendpoint`(`pack/changes`) · `domain/festival`(ReceivedPack · PackDetail · 만료 삭제) · `core/storage`(준비물 · 자동 받기)   | `FestivalReceivedPackUseCaseTests` 8 · `FestivalPackDetailUseCaseTests` 8 · `FestivalPackDetailLocationTests` 2 · `FestivalPackExpiryTests` 5(쟁점 M8 a)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| D4  | 화면 모드 저장 · 적용              | `core/storage` · `app`(루트 적용)                                                                                                                                      | `AppearanceModeSharedKeyTests` 3건 중 기본값 · 알 수 없는 값(이전 키 이관은 Android에 없음), 루트 적용 Compose 테스트 1건                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| S1  | 마이 화면 + `feature/profile` 모듈 | `feature/profile/{interface,sources}` · `featureassembly` · `architecture.md` 표                                                                                       | `ProfileFeatureTests` 19건 중 팩 · 로그인 · 설정 15건(기수 4건 `guestHidesBearerPrompt` · `loadsMyFlag` · `myFlagFailureKeepsPrompt` · `guestSkipsMyFlag`는 M7로 기수 문서 PR에), Preview                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| S2  | 설정 홈 · 화면 모드 · 문서         | `feature/profile`(`settings/` · `appearance/`) · `feature/web` 재사용                                                                                                  | `SettingsHomeFeatureTests` 11건 중 M4 · M5로 숨긴 줄 3건(`notificationsTapOpensNotificationSettings` · `closingNotificationSettingsStillSendsUnsavedChanges` · `lockScreenRowOpensScreenAndBackReturnsToSettings`) 제외 8건 → 알림 2건은 S6에서, `AppearanceSettingsFeatureTests` 3건                                                                                                                                                                                                                                                                                                                                                                                                      |
| S3  | 계정 · 닉네임 시트                 | `feature/profile`(`account/`)                                                                                                                                          | `AccountSettingsFeatureTests` 4건(`logoutAndWithdrawRequireConfirmation` · `busyAccountDoesNotOpenConfirmationOrNickname` · `savedNicknameUpdatesScreenAndNotifiesApp` · `nicknameRulesFollowSignUp`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| S4  | 공지 목록 · 상세                   | `feature/profile`(`notice/`)                                                                                                                                           | `NoticeFeatureTests` 5건                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| S5  | 팩 상세                            | `feature/profile`(`packdetail/`)                                                                                                                                       | `PackDetailFeatureTests` 15건(지도 앱 확인 포함)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| A1  | 로그아웃 · 탈퇴 실행 · 계정 상태   | `app`(`AccountCoordinator` · `AppCoordinator` 계정 상태 · 바로 로그인)                                                                                                 | `AppCoordinatorTests` 계정 사례: `myLogoutClearsVisibleAccountOnlyAfterSuccess` · `logoutContinuesWhenDeviceUnbindFails` · `withdrawalContinuesWhenDeviceUnbindFails` · `myWithdrawalFailureRetainsAccount` · `unauthenticatedWithdrawalClearsAccount` · `sessionExpiryCancelsInFlightAccountOperationThroughDelegate` · `sessionExpiryDuringAccountOperationDiscardsLateCompletionAfterNewLogin` · `withdrawalFailureAfterExpiryCannotRestorePreviousAccount` · `accountOperationFinishesAfterFlowDismissChangesGeneration` · `changedNicknameUpdatesSessionDisplayName` · `accountChangesReachProfileAsInputAction` · `signOutClearsPreviousAccountFlagsLiveBoardAndBookingsImmediately` |
| T1  | 탭 연결                            | `app`(`MTS` 마이 자리 · 탭 바 규칙 · `PackDirections` · 축제 목록 요청) · `feature/profile` `ProfileCoordinator` · `ProfileFlow`                                       | `ProfileRoutingTests` 12건(`liveBoardKeepsMainTabBar` 포함), 보드 "가는 길" → 마이 팩 상세                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| S6  | 알림 설정 (P0 머지 뒤)             | `service/device`(GET/PUT) · `domain/festival` 알림 설정 UseCase · `feature/profile`(`notification/`) · 설정 홈 줄 노출                                                 | `NotificationSettingsFeatureTests` 7건, `DeviceNotificationSettingsTests`, `FestivalNotificationSettingsUseCaseTests` 3건, S2에서 뺀 설정 홈 2건                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |

- 순서: D1 → D2 → D3 → D4 → S1 → S2 → S3 → S4 → S5 → A1 → T1. S6은 P0(푸시 · 기기 등록) 머지 뒤. 사용자 규칙대로 하나씩 올린다.
- S1~S5는 앱에 아직 안 보이므로 Preview와 단위 테스트로 검증한다. 사용자에게 보이는 것은 T1부터다.
- 기기 연결 해제(A1 2단계)는 P0 전까지 비어 있는 구현으로 두고(쟁점 M3), P0 PR이 실제 호출을 채운다.

### 기능 spec (`.spec/`)

- `domain/auth/.spec/account-operations.md` — 로그아웃 · 탈퇴 · 닉네임 규칙과 로컬 세션 정리(M2 결정). D1 첫 커밋.
- `feature/profile/.spec/my-tab.md` — 마이 · 설정 홈 · 계정 · 화면 모드 · 공지 동작, 숨긴 줄(M4 · M5 · M7). S1 첫 커밋.
- `feature/profile/.spec/pack-detail.md` — 팩 상세 · 준비물 · 업데이트 · 만료. D3 첫 커밋.

## 새 라이브러리 후보

없다. 모두 기존 의존성이나 플랫폼 API로 된다.

| 필요                                 | 쓰는 것                                                                  | 라이선스   |
| ------------------------------------ | ------------------------------------------------------------------------ | ---------- |
| 외부 링크                            | 이미 있는 `androidx.browser` 1.10.0(`gradle/libs.versions.toml:27 · 71`) | Apache-2.0 |
| 썸네일                               | 이미 있는 Coil 3.6.3 + `FCRemoteImage`                                   | Apache-2.0 |
| 저장(화면 모드 · 준비물 · 자동 받기) | 이미 있는 DataStore 1.3.0(`core/storage`)                                | Apache-2.0 |
| 탭 페이지                            | Compose Foundation `HorizontalPager`(구현 PR에서 사용 여부 확인)         | Apache-2.0 |
| 와이파이 판단                        | `ConnectivityManager.getNetworkCapabilities` + `TRANSPORT_WIFI`          | 플랫폼     |
| 앱 버전                              | `PackageManager` · `BuildConfig`                                         | 플랫폼     |

## 위험

- 화면 모드 기본값을 iOS처럼 `dark`로 두면 지금 시스템 설정을 따르던 Android 화면이 첫 실행부터 다크로 바뀐다(M6).
- `LocalConfiguration` 덮어쓰기가 시스템 바 아이콘 색 · WebView · Custom Tabs 색에는 적용되지 않는다. 각 표면을 따로 맞춰야 할 수 있다.
- 로그아웃 서버 처리 뒤 로컬 정리 전에 프로세스가 죽으면, Android 단순화(M2 b)에서는 다음 갱신이 401로 끝나 세션 만료로 정리될 것으로 본다. refresh 토큰 폐기 뒤 `auth/refresh`가 401을 주는지는 stage에 쓰기 요청을 보내지 않아 확인하지 못했다.
- P0 전에는 로그아웃해도 이 기기의 서버 기기 연결을 풀지 않는다. 서버 로그아웃이 계정 기기의 푸시 토큰을 모두 지우므로(`auth.service.ts:137-162`) 푸시가 남지는 않지만, Android는 아직 기기 등록 자체가 없다.
- 스테이지 공지가 0건이라 공지 목록 · 상세 실제 응답을 보지 못했다(백엔드 Swagger 예시 기준).

## 쟁점 (추천안 포함)

- **M1 Apple 로그인 표시.** Android는 Google 로그인만 있다(CLAUDE.md, `ios-parity.md`). (a) 로그인 카드에서 Apple 버튼만 빼고 제목 · 본문 · Google 버튼은 iOS 그대로(추천). 계정 화면 "로그인" 값은 iOS 매핑(`apple` → "Apple", `google` → "Google", 그 외 원문, `AV:187-193`)을 그대로 둔다 — Android에서는 Apple 계정으로 로그인할 수 없어 사실상 "Google"만 보인다. (b) 로그인 카드를 Android 전용 문구로 바꾼다 — iOS와 달라진다.
- **M2 로그아웃 · 탈퇴 로컬 정리.** iOS는 요청 전 "되돌림 차단" 기록을 디스크에 남기고 결과 불명 · 로컬 삭제 실패 · 재시작 뒤 정리를 상태 기계로 다룬다(`ARI:119-210`, `ASv:39-58`). (a) iOS 상태 기계를 `service/auth` `CredentialStore`에 그대로 옮긴다 — 동작은 같지만 `AuthSession`에 상태 7종이 늘고 테스트가 크다. (b) 단순화(추천): 요청 성공 또는 401이면 `clearSessionIf(sessionId)`로 지우고, 그 외 실패면 세션을 두고 오류 문구. 탈퇴 401은 `notAuthenticated`. Android `CredentialStore` 삭제는 한 번의 DataStore 쓰기이고, 서버가 refresh 토큰을 이미 폐기했으면 다음 갱신 401이 세션 만료로 정리한다(위험 절, 확인 필요). 결과 문구 · 화면 동작은 iOS와 같다.
- **M3 기기 연결 해제(`AccountDeviceSync`).** P0 전에는 기기 등록이 없다. (a) A1에 `unbindCurrentUser` · `linkDeviceToCurrentUser` 자리를 두고 P0 전까지 아무 일도 하지 않게 둔다(추천, iOS도 해제 실패를 막지 않음 `ADS:30-41`). (b) A1을 P0 뒤로 미룬다 — 로그아웃 · 탈퇴 출시가 늦어진다.
- **M4 알림 화면.** 서버 알림 설정은 기기 등록이 필요하다(`NDS`). (a) P0 머지 전까지 설정 홈 "알림" 줄을 숨기고, S6을 P0 뒤에 넣는다(추천, 타임테이블 문서의 P0 게이트와 같은 원칙). (b) 줄을 보이고 "알림 설정을 불러오지 못했어요"로 끝낸다 — 늘 실패하는 화면이 보인다.
- **M5 잠금화면.** (a) 라이브 액티비티(진행 중 알림) 단계 전까지 줄을 숨기고 그 단계에서 연다(추천, 깃발 문서 F4-1과 같은 원칙). (b) Android 진행 중 알림 설계와 함께 다시 정한다 — 라이브 액티비티 단계 문서에서.
- **M6 화면 모드 기본값 · 적용 방식.** 기본값: (a) iOS와 같게 `dark`(추천, "디자인은 iOS 그대로"), (b) Android는 `system` — 지금 동작 유지. 적용: (가) 앱 루트에서 `LocalConfiguration`의 `uiMode` 야간 비트를 덮어써 생성된 `SC`의 `isSystemInDarkTheme()`이 따르게 하고, 시스템 바는 `enableEdgeToEdge(SystemBarStyle)`로 맞춘다(추천, 생성 코드 · 토큰을 고치지 않음). (나) `AppCompatDelegate.setDefaultNightMode` — `AppCompatActivity` · appcompat 의존 추가가 필요(지금 `ComponentActivity`). (다) `UiModeManager.setApplicationNightMode` — API 31+만(minSdk 26). (가)가 WebView · Custom Tabs에 적용되는지는 구현 PR에서 공식 문서로 확인한다.
- **M7 기수 카드 · 내 깃발 카드.** iOS는 로그인 상태에서 기수 카드(`FCBearerPrompt`) 또는 내 깃발 카드(+ "기수 보드 열기" · "다른 페스티벌 깃발 만들기")를 보이고 기수 흐름을 push한다(`PV:48-53 · 146-196`, 5,138행). (a) 기수 문서가 기수 흐름을 옮길 때까지 두 카드를 숨기고, `ProfileContract`에 자리만 둔다(추천, 누를 곳이 없는 카드를 보이지 않음). (b) 카드만 보이고 누르면 아무 일 없음.
- **M8 팩 30일 자동 삭제 안내.** iOS는 앱 시작 때 마지막 날 끝 + 30일이 지난 팩을 지운다(`iOS/App/Sources/Navigation/Main/AppCoordinator.swift:198-212`, `FestivalPackExpiryTests` 5건). Android에는 없다. (a) D3에 만료 삭제를 옮기고 `AppCoordinator` 시작 때 부른다(추천, 안내 문구와 동작을 맞춤). (b) 안내 문구를 숨긴다.
- **M9 와이파이에서 자동으로 받기.** iOS는 서버 목록 갱신 때 켜져 있고 와이파이면 새 버전 팩을 받는다(`RPU`, 기본 켜짐 `PLS`). (a) 그대로 옮긴다(추천, `ACCESS_NETWORK_STATE` 일반 권한 추가). (b) 토글을 숨긴다.
- **M10 지도 앱 버튼.** iOS는 `nmap` · `kakaomap` 스킴 설치 여부로 "웹 ↗"를 붙인다. (a) Android 패키지 이름으로 설치 여부를 보고 `<queries>`에 선언, 버튼은 같은 https 링크를 `ACTION_VIEW`로 열어 앱이 있으면 앱이 받게 한다(추천). 패키지 이름(`com.nhn.android.nmap` · `net.daum.android.map`)은 확인하지 못했다. (b) 항상 Custom Tabs로 열고 "웹 ↗"를 붙이지 않는다 — iOS와 문구가 달라진다.
- **M11 약관 문서 열기.** (a) iOS처럼 앱 안 `WebScreen`(추천, 가입 약관과 같은 경로 · 같은 호스트 정책). (b) Custom Tabs — 외부 링크 관례지만 iOS · 가입 화면과 다르다. 가는 길 링크 · 지도 웹은 외부 링크라 Custom Tabs.
- **M12 바로 로그인.** (a) 로그인 카드 Google 버튼은 iOS처럼 시트 없이 Google 계정 선택을 띄우고, 취소면 조용히 끝내고 실패면 시트(문맥 `profile`)를 연다(추천). (b) 시트를 먼저 연다 — 한 번 더 눌러야 한다.
- **M13 계정 상태 공급.** (a) `AppCoordinatorState`에 `AccountState(profile, hasSession, isLoading, message)`를 두고 `ProfileFlow`에 StateFlow로 넘긴다(추천, iOS `MTAI` `account` 입력과 같은 방향 · 단일 소유자). (b) 마이 ViewModel이 `AuthRepository.fetchProfile`을 직접 부른다 — 재확인 · 만료 처리와 결과가 갈라질 수 있다.

## 경계 (다른 문서)

- **기수(Bearer)**: 마이의 기수 카드 · 내 깃발 · 기수 흐름(Board · Claim · Plan · Profile · Register)은 별도 문서. 이 문서는 카드 자리만 둔다(M7).
- **PrepGuide 3단계(`PrepChecklist`)**: `Feature/Profile` 모듈이지만 라이브 탭에서 연다(`2026-11-22-live-extras-android-design.md`). D3의 준비물 저장소(`FestiCore.pack.checklist.<id>`)를 같이 쓰게 한다.
- **푸시 · 기기 등록(P0)**: 알림 화면(S6) · 기기 연결 해제(M3)의 선행 조건.
- **라이브 액티비티**: 잠금화면 화면 · 대표 깃발 고르기(M5).
- **분석**: 마이 · 계정 이벤트는 분석 모듈 단계.

## 확인 못 한 것

- 인증이 필요한 응답 본문(`PATCH me` · `POST auth/logout` · `DELETE me` · 알림 설정) — stage에 쓰기 · 로그인 요청을 보내지 않아 실제 응답을 보지 못했다. 백엔드 코드 · iOS DTO 기준이다.
- 스테이지 공지가 0건이라 공지 목록 · 상세 응답 실물. refresh 토큰 폐기 뒤 `auth/refresh`의 상태 코드.
- iOS `monthDayTime` · 팩 상세 날짜 포맷의 정확한 정의와 시간대(공지 `noticeDay`만 서울 기준 테스트로 확인).
- `PackDetailFeature` · `NoticeListFeature` · `LockScreenSettingsFeature` · `PinnedFlagPickerFeature`의 행 번호(파일 단위로만 확인, `~` 표기). `PackDetailFeatureTests` 15건 중 이름을 확인한 것은 14건(나머지는 인자화 테스트로 보임).
- `FCPackRow` · `FCListRow` · `FCTopBar(large)` · `FCLastRideCard` 등 Android 이식본 내부 수치가 iOS와 같은지 — 구현 PR에서 컴포넌트 파일을 대조한다.
- 카카오맵 · 네이버 지도 Android 패키지 이름, `LocalConfiguration` 덮어쓰기의 WebView · Custom Tabs 적용 범위.
- `AuthDomainTests` 26건 중 Android 단순화(M2 b)로 옮길 사례와 버릴 사례(디스크 기록 상태 기계 전용)의 정확한 목록.
- 이 문서는 Codex 교차 검토를 받지 않았다.
