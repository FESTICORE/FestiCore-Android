# 깃발 탭(Flag · FlagDetail · Crew) Android 이식 설계

- 날짜: 2026-11-23
- 이슈: 미정(구현 이슈를 만들 때 적는다)
- 상태: 제안
- 기준: Android `develop` cf2b687, iOS `09eda3b8`(로컬 `../FestiCore-iOS`, 읽기 전용). iOS 행 번호는 `09eda3b8`에서 연 값이다. `~`가 붙은 행은 범위 근처로만 확인했으므로 구현할 때 다시 연다.
- 범위: `feature/festival`(Flag · FlagDetail · Crew 화면, iOS와 같은 모듈) · `domain/festival` · `service/api` · `service/festivalcatalog` · `ui/designkit` · `ui/sharedui` · `feature/auth`(로그인 시트, 쟁점 F1) · `app`(`MainTabScreen` 깃발 탭 · `MainTabCoordinator` 딥링크)

## 목표

`MainTabScreen`의 깃발 탭 빈 자리(`TabRoot`, `app/.../navigation/MainTabScreen.kt:49 · 59-67`)를 iOS 깃발 탭으로 채우고, 라이브 보드가 받아 두기만 하던 크루 경계(`LiveBoardBoundary.CrewCreate · Crew · CrewGather · CrewLogin · FollowLogin · Login`, `feature/festival/.../liveboard/LiveBoardContract.kt:144-182`, `FestivalCoordinator.kt:84-99`)를 실제 화면으로 잇는다.

1. **Flag(깃발 목록, S04)** — 깃발 탭 루트. 선택 축제의 깃발을 태그 필터 · 정렬 · 팔로우로 보여 준다.
2. **FlagDetail(깃발 상세, S05)** — 목록 카드 · `festicore://flag/<id>` 딥링크에서 push. Hero · 지금 카드 · 오늘 계획 · 신고. (잠금화면 표시는 라이브 액티비티 단계 — 이번 범위 아님)
3. **Crew(S07 크루 · S08 참여 확인)** — 크루 메인 · 크루장 메뉴 · 모여 시트 · 크루 만들기 · 초대 참여. **iOS에서 이 화면들은 깃발 탭이 아니라 라이브 탭 스택에 쌓인다**(아래 "화면 전환").
4. **딥링크 연결** — `Flag(id)` · `CrewInvite(code)`(`MainTabCoordinator.kt:50-52`의 디버그 로그 자리).

시각(수치 · 문구 · 토큰 · 에셋 · 접근성)은 iOS View와 같게, 플랫폼 관례(뒤로 · 시트 · 공유 · 권한)는 Android 방식을 따른다.

iOS 약어(`iOS` = `../FestiCore-iOS/Projects`, `F` = `iOS/Feature/Festival/Sources`):

| 약어                                    | 경로                                                                                                                                                                                                                                                                         |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FF` · `FD` · `FV`                      | `F/Flag/Reducer/FestivalFlagFeature.swift` · `Reducer/FestivalFlagDirectory.swift` · `View/FestivalFlagView.swift`                                                                                                                                                           |
| `DF` · `RF` · `DV` · `RV` · `DS` · `DC` | `F/FlagDetail/Reducer/FestivalFlagDetailFeature.swift` · `Reducer/FestivalFlagReportFeature.swift` · `View/FestivalFlagDetailView.swift` · `View/FestivalFlagReportView.swift` · `Components/FestivalFlagDetailSkeletonView.swift` · `Model/FestivalFlagDetailContext.swift` |
| `CF` · `CJF` · `CMF` · `CP`             | `F/Crew/Reducer/FestivalCrewFeature.swift` · `FestivalCrewJoinFeature.swift` · `FestivalCrewMenuFeature.swift` · `Model/FestivalCrewPresentation.swift`                                                                                                                      |
| `CV` · `CJV` · `CMV`                    | `F/Crew/View/FestivalCrewView.swift` · `FestivalCrewJoinView.swift` · `FestivalCrewMenuView.swift`                                                                                                                                                                           |
| `CCF` · `CCV` · `CGF` · `CGV`           | `F/LiveBoard/CrewCreate/FestivalCrewCreate{Feature,View}.swift` · `F/LiveBoard/CrewGather/FestivalCrewGather{Feature,View}.swift`                                                                                                                                            |
| `FCo` · `FCoV` · `FCtx`                 | `iOS/App/Sources/Navigation/Flag/FlagCoordinator.swift` · `FlagCoordinatorView.swift` · `FestivalFlagDetailContext+Board.swift`                                                                                                                                              |
| `MTC` · `MTCSc` · `MTCSy` · `MTV`       | `iOS/App/Sources/Navigation/MainTab/MainTabCoordinator.swift` · `+Scope.swift` · `+Sync.swift` · `MainTabView.swift`                                                                                                                                                         |
| `FeCo`                                  | `iOS/App/Sources/Navigation/Festival/FestivalCoordinator.swift`                                                                                                                                                                                                              |
| `LMV` · `LF` · `AuC`                    | `iOS/Feature/Auth/Sources/Login/View/LoginModalView.swift` · `Login/Reducer/LoginFeature.swift` · `iOS/App/Sources/Navigation/Auth/AuthCoordinator.swift`                                                                                                                    |
| `FFS`                                   | `iOS/Service/FestiCoreFestivalCatalog/Sources/NetworkFestivalFlagFollowService.swift`                                                                                                                                                                                        |
| `CRq` · `CRp` · `CUc`                   | `iOS/Service/APIEndpoint/Sources/Crew/CrewRequest.swift` · `iOS/Domain/FestivalDomain/Sources/.../FestivalCrewRepositoryImpl.swift` · `.../Crew/FestivalCrewUseCaseImpl.swift`                                                                                               |
| `DL`                                    | `iOS/Core/FestiCoreUtility/Sources/Deeplink/FestiCoreDeeplink.swift`                                                                                                                                                                                                         |

Android 약어: `MTS` = `app/src/main/kotlin/io/festicore/app/navigation/MainTabScreen.kt`, `MTCA` = `app/.../navigation/MainTabCoordinator.kt`, `AFl` = `app/.../navigation/AppFlow.kt`, `FCoA` = `feature/festival/sources/.../FestivalCoordinator.kt`, `FFl` = `.../FestivalFlow.kt`, `LBC` = `.../liveboard/LiveBoardContract.kt`, `FLBS` = `service/festivalcatalog/.../FestivalLiveBoardService.kt`, `FAP` · `FA` = `service/api/.../FestivalApiPath.kt` · `FestivalApi.kt`, `FDL` = `core/utility/.../deeplink/FestiCoreDeeplink.kt`.

## 지금 Android 상태 (경계)

| 항목            | 지금                                                                                                                                                                                                                                                                                                                                                                        | 근거                                                                                                   |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| 깃발 탭         | `TabRoot`(탭 제목 글자만)                                                                                                                                                                                                                                                                                                                                                   | `MTS:49 · 59-67`                                                                                       |
| 깃발 탭 전환    | 라이브 흐름이 `FlagTabRequested`를 보내면 `MainTabCoordinator.onFlagTabRequested()`                                                                                                                                                                                                                                                                                         | `FCoA:38-39 · 101-105`, `MTCA:36-37`, `AFl:127 · 141`                                                  |
| 딥링크          | `Flag(id)` · `CrewInvite(code)` 파싱만, 꺼낸 뒤 디버그 로그                                                                                                                                                                                                                                                                                                                 | `FDL:17 · 26 · 83 · 90-91`, `MTCA:50-52`                                                               |
| 보드 경계       | `Login` · `CrewLogin` · `FollowLogin` · `FlagDirectory` · `Bearer` · `CrewCreate` · `Crew` · `CrewGather` · `PackDirections`는 `lastBoundary`에 받아 두기만                                                                                                                                                                                                                 | `LBC:145-181`, `FCoA:84-99`                                                                            |
| 깃발 데이터     | `GET festivals/{id}/live` + `GET festivals/{id}/flags`를 합친 라이브 보드 스냅샷(크루 `live` 포함)                                                                                                                                                                                                                                                                          | `FLBS:75`, `FA:44-60 · 88-91`, `domain/festival/interface/.../FestivalLiveBoardSnapshot.kt:10 · 37-97` |
| 깃발 쓰기 API   | 없음(팔로우 · 신고 · 대표 깃발 · 상세 GET)                                                                                                                                                                                                                                                                                                                                  | `FAP` 전체                                                                                             |
| 크루 API        | 없음(보드 스냅샷의 `crew`만 읽음)                                                                                                                                                                                                                                                                                                                                           | 같은 곳                                                                                                |
| Idempotency-Key | 저장소 어디에도 없음                                                                                                                                                                                                                                                                                                                                                        | `grep -rl Idempotency` 0건                                                                             |
| 로그인 시트     | 없음. 첫 실행 전체 화면 로그인(`LaunchLogin*`)만 있음                                                                                                                                                                                                                                                                                                                       | `feature/auth/sources/.../LaunchLogin{Flow,Screen,ViewModel}.kt`                                       |
| 공유            | `Intent.ACTION_SEND` 사용처 없음                                                                                                                                                                                                                                                                                                                                            | `grep` 0건                                                                                             |
| designkit       | `FCFlagCard` · `FCFlagHero` · `FCFlagNowCard` · `FCHeroFollowButton` · `FCPlanRow` · `FCPlanPrivate` · `FCEmptyCard` · `FCSortButton` · `FCSortMenu` · `FCChip` · `FCStatusChip` · `FCAvatar` · `FCCrewRow` · `FCZoneChip` · `FCBigButton` · `FCChoiceTile` · `FCTextField` · `FCToast` · `FCDialog` · `FCSheet` · `FCBanner` · `FCSkeletonView` 있음. `FCReportSheet` 없음 | `ui/designkit` · `ui/sharedui` 파일 목록                                                               |

## 수용 기준

1. 깃발 탭을 누르면 라이브 보드에서 고른 축제의 깃발 목록이 보이고, 축제가 없으면 "축제를 선택해 주세요" 카드가 보인다.
2. 태그 칩 · 정렬 메뉴 · 팔로우가 iOS 규칙(정렬 순위 · 서버 순서 안정 정렬 · 응답 후 반영 · override 해제)대로 동작한다. iOS `FestivalFlagFeatureTests` · `FestivalFlagDirectoryTests`의 흐름을 같은 이름의 ViewModel 테스트로 옮긴다.
3. 카드 · 딥링크로 상세가 push되고 탭 바는 보인다. 상세는 한 번에 하나만 쌓인다.
4. 상세의 팔로우 · 신고가 로그인 · 약관 · 한도 · 오류를 iOS 문구로 나눈다. 비로그인 팔로우 · 신고는 로그인 시트(문맥 `follow` · `report`)를 연다.
5. 라이브 보드 크루 카드 → 크루 메인, "크루 만들기" → 만들기, 초대 링크 → 참여 확인이 라이브 탭 스택에 push되고, 이 화면들이 맨 위면 탭 바를 숨긴다.
6. 크루 메인의 상태 보내기(낙관 갱신 + 실패 시 재조회) · 모여 요청/응답 · 크루장 메뉴(이름 · 초대 재생성 · 이전 · 내보내기 · 해산) · 초대 공유가 iOS와 같다.
7. 비로그인으로 초대 링크를 열면 미리보기가 보이고 "참여하기"에서 로그인 시트(문맥 `crewJoin`)가 열린다. 로그인 후 자동 참여하지 않는다(iOS와 같음).
8. `./gradlew testStageDebugUnitTest assembleStageDebug` 통과, `.claude/scripts/verify` 통과. 에뮬레이터 확인은 별도로 보고한다.

## 모듈과 책임

| Android 모듈                 | iOS 대응                                                                 | 이번에 넣는 것                                                                                                         |
| ---------------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| `service/api`                | `iOS/Service/APIEndpoint` · `API/Sources/{Flag,Crew}`                    | 경로(`FlagApiPath` · `CrewApiPath`) · DTO · `FlagApi` · `CrewApi` · `Idempotency-Key` 헤더 도우미                      |
| `service/festivalcatalog`    | `FestiCoreFestivalCatalog`                                               | 팔로우 서비스(요청 전 로그인 확인 · 오류 매핑, `FFS:29-100`)                                                           |
| `domain/festival/interface`  | `FestivalDomain/Interface/{Flag,FlagDetail,FlagFollow,Crew,Entity}`      | `FestivalFlagDetail` · `FestivalFlagFollowResult` · 오류 sealed · `FestivalCrew` · `FestivalCrewInvite` · UseCase 계약 |
| `domain/festival/sources`    | `FestivalDomain/Sources`                                                 | Repository 구현 · 매핑 · 입력 검증(ID · 초대 코드 · 이름 1~12자)                                                       |
| `feature/festival/interface` | `Feature/Festival/Interface`                                             | `FlagRoute` · 깃발 탭 Coordinator 입력(문맥) · 크루 `FestivalRoute` 추가                                               |
| `feature/festival/sources`   | `F/Flag` · `F/FlagDetail` · `F/Crew` · `F/LiveBoard/Crew{Create,Gather}` | 화면 · ViewModel · Contract · Presentation · `FlagCoordinator` · `FlagFlow`                                            |
| `ui/sharedui`                | `iOS/UI/FestiCoreSharedUI/Sources/Sheet/FCReportSheet.swift`             | `FCReportSheet` 신규                                                                                                   |
| `feature/auth`               | `iOS/Feature/Auth/Sources/Login`                                         | 로그인 시트(쟁점 F1)                                                                                                   |
| `app`                        | `MTC` · `MTCSc` · `MTCSy` · `MTV`                                        | 깃발 탭 자리 · 문맥 동기화 · 딥링크 · 로그인 시트 표시                                                                 |

새 기능 모듈을 만들지 않는다. iOS에서 Flag · FlagDetail · Crew는 모두 `Feature/Festival` 안에 있다(CLAUDE.md 규칙 9).

## 화면 1: Flag (깃발 목록)

### 화면 구성 (`FV:23-57`)

- `FCTopBar(large, "깃발")`(`:24`) → 조건부 `FCOfflineBadge`(`:25-30`, 실패 + 스냅샷 있음, 60초마다 갱신 `:253`) → 세로 스크롤(간격 `paddingP16`, 좌우 · 위 `P16` `:41-44`, 당겨서 새로고침 `:48`, 배경 `backgroundNormal` `:54`).
- 머리(`:63-77`, 간격 P4): "만나보고 싶은 깃발이 있나요?"(`baseTitle3Bold` · `labelNormal`), "팔로우하지 않아도 괜찮아요. 깃발이 움직이면 조용히 알려 드려요."(`baseFootnoteRegular` · `labelNeutral`).
- 축제 없음: `FCEmptyCard("축제를 선택해 주세요", "축제를 선택하면 해당 축제의 깃발을 볼 수 있습니다.")`(`:35`).
- 태그 칩(`:85-99`): 가로 스크롤, 간격 P8, 좌우 P16을 −P16으로 상쇄. 순서 "전체" · 슬램 · 떼창 · 기차놀이 · 차분 · 초보 환영(`FD:25-33`, raw `slam · sing · train · calm · newbie`), 단일 선택 `FCChip`.
- 정렬(`:141-149 · 169-181`): 오른쪽 `FCSortButton`(높이 26 `:256`), 아래 P8에 `FCSortMenu`(너비 200, 그림자 .12 · r12 · y8, 테두리 1 — `iOS/UI/FestiCoreDesignKit/.../FCSortMenu.swift:58-63`). 항목 "활동 중 순" · "팔로워 많은 순" · "최근 업데이트 순"(`FD:40-46`). 메뉴가 열린 동안 아무 곳이나 누르면 닫힘(`FV:49-52`).
- 목록(`:158-162`): 간격 P12 `FCFlagCard`. 썸네일 56 · r12, 이미지 없을 때 `ic_flag_20`(`:197-205`). 카드 접근성 동작 "깃발 상세 보기"(`FCFlagCard.swift:140-142`).
- 오프라인이면 카드의 상태 · 존을 숨긴다(`FV:187 · 208`, Figma S04-E).
- 행 문구(`FD:~385-399`): "팔로워 N"(ko_KR 천단위), 알려진 태그만 한글, 존 없으면 "위치 미정".
- 스켈레톤(`FV:221-237`): 3행, 56×56 `r12` + 막대 140×20 · 100×16, 라벨 "깃발을 불러오는 중입니다".
- 토스트(`:239-248`): `FCToast(ic_circle_info_16)`, 아래 P16, 0.25초, 2초 뒤 사라짐.

### 빈 · 오류 문구 (`FV:105-129 · 151-156`)

| 상태                      | 제목 / 본문                                                                               |
| ------------------------- | ----------------------------------------------------------------------------------------- |
| 깃발 0개 · loaded 빈 목록 | "깃발이 없습니다" / "이 축제에 등록된 깃발이 없습니다."                                   |
| 필터 결과 0개             | "이 성향의 깃발이 아직 없어요" / "다른 성향을 골라 보세요."                               |
| failed                    | "깃발을 불러오지 못했습니다" / "네트워크 연결을 확인하고 아래로 당겨 다시 시도해 주세요." |
| deviceNotRegistered       | "기기 등록이 필요합니다" / "앱을 다시 실행해 기기를 등록한 뒤 새로고침해 주세요."         |
| invalidFestivalID         | "축제 정보가 올바르지 않습니다" / "앱을 업데이트한 뒤 다시 확인해 주세요."                |
| invalidResponse           | "깃발 정보 형식이 올바르지 않습니다" / "문제가 계속되면 문의해 주세요."                   |

스냅샷이 있으면 본문 뒤에 " 이전 깃발을 표시합니다."를 붙이고 목록을 그대로 둔다(`FV:105-110`).

### 상태 · 동작 (`FlagContract.kt` · `FlagViewModel`, iOS `FF` + `FD`)

- State: `festivalId?` · `snapshot?` · `viewState(idle·loading·loaded·failed·deviceNotRegistered·invalidFestivalId·invalidResponse)` · `isVisible` · `generation` · `tag?` · `sort(active)` · `isSortMenuPresented` · `zoneNames` · `toast?` · `pendingFollows: Map<id, Int?>` · `followOverrides: Map<id, FollowOverride>`(`FF:14-43`, `FD:64-77`).
- `onAppeared`: screen_view(`festivalFlag`) 후 `isVisible = true`, 불러오기(`FF:140-148`). `onDisappeared`: 요청 취소, 토스트 · 메뉴 닫기, override는 유지(`FF:150-152`, `FD:~459-490`).
- `onFestivalChanged` · `onAccountChanged`: 값이 바뀌면 스냅샷 · override를 비우고 `generation + 1`, 보이는 중이면 다시 불러온다(`FF:157-168`).
- 응답은 `festivalId` · `generation` · `isVisible`이 모두 같을 때만 반영(`FF:263`). `rateLimited(retryAfter)`는 이전 목록을 두고 그 뒤 재조회(`FF:220-248`).
- 데이터는 기존 라이브 보드 서비스(`FLBS`)의 `/live` + `/flags` 합성을 그대로 쓴다(iOS도 `liveBoardService.refresh`, `NetworkFestivalLiveBoardService.swift:57-78`).
- 정렬(`FD:~402-436`, 같으면 서버 순서): 활동 중 순 = `active < resting < 모름 < scheduled < endedToday`, 같은 순위는 팔로워 내림차순 / 팔로워 순 / 최근 업데이트 순(null 뒤).
- 팔로우는 **낙관 갱신이 아니다**(`FD:~172-178 · 258-284`): pending이면 무시, 요청 중 · 새로고침 실패 중 버튼 비활성(`FV:134-139`). 성공 → override 저장 + `flagFollow` 이벤트. 새 스냅샷에서 팔로우 여부가 같아지면, 팔로워 수는 서버 값이 기준 수와 달라졌을 때만 override를 지운다(공개 목록 캐시, `FD` reconcile `~437-457`).
- 실패: `loginRequired` → 로그인 시트(문맥 없음 = 깃발 탭 기본 `flag`), `termsRequired` → 세션 프로필 재확인, `limitReached` → "팔로우는 100개까지 할 수 있어요", 그 외 → "팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요".
- 상세에서 바꾼 팔로우는 `onFollowChangedElsewhere(result, baseCount)`로 같은 규칙에 넣는다(`FCo:147-162 · 198-213`).
- SideEffect: `DetailRequested(flagId)` · `LoginRequested` · `TermsAcceptanceRequested`(`FD:128-134`).

## 화면 2: FlagDetail (깃발 상세)

### 내용 (`DV`)

- 상단 바(`DV:75-91 · 161-167`): `FCTopBar(back | backOnImage, title = 이름)`. 스크롤이 상단 바 높이 이상 · Hero 높이 이하일 때만 `backOnImage`, 오프라인 배지가 보이면 `back` 고정. 상세를 받은 뒤에만 공유(`festicore://flag/<id>`, `DF:638-644`).
- 본문(`DV:112-158`): `FCFlagHero`(높이 360, `FCFlagHero.swift:135-139`) → 간격 P20으로 `FCFlagNowCard`(잠금화면 행은 이번 범위에서 숨김 — 결정 절) → "오늘 계획" → "신고하기"(`baseFootnoteMedium` · `labelAssistive`, 위 P8). 패딩 위 P20 · 아래 P4 · 좌우 P16.
- Hero: 사진 = `photoUrl ?: thumbUrl`, 없으면 `backgroundElevated`(`DV:247-257`), "팔로워 N", 소개는 “…”로 감쌈(`:204-207`), 팔로우는 오프라인 · 요청 중 비활성(`:214`).
- 지금 카드 문구(`DF:655-721`): ended → "오늘 활동을 마쳤어요". 존 = `zoneId ?: homeZoneId` 이름, 없으면 "위치를 아직 알리지 않았어요". 활동 = 슬램 · 떼창 · 기차놀이 · 차분 · 쉬는 중 · 예정 · (null) 활동 중. 출처 = 오프라인 "마지막으로 받은 정보예요" / 30분 넘음(`DF:605`) "{상대시간} 정보예요 · 지금은 다를 수 있어요" / "기수가 방금 알렸어요" / "기수가 {상대시간}에 알렸어요". 상대시간은 이미 옮긴 `LiveBoardPresentation.relativeTime`을 쓴다. muted = 30분 넘음 또는 오프라인(resting은 아님).
- 잠금화면 행(`DV:219-230`): **이번 범위에서 그리지 않는다**(결정 절 F4-1). 라이브 액티비티 단계에서 `FCListRow("잠금화면에 표시", toggle)`, 상세 받음 · 로그인 · 팔로잉일 때만(`DF:633-635`) 노출한다.
- 오늘 계획(`DV:294-320`): `FCSectionHeader("오늘 계획", action = "기수가 정한 순서예요")`. 비공개 `FCPlanPrivate()`("오늘 계획은 비공개예요" / "기수가 계획을 공개하면 여기에서 볼 수 있어요"), 비어 있음 "오늘 계획이 아직 없어요" / "기수가 계획을 올리면 여기에서 볼 수 있어요", 팩에 공연 없음 "공연 정보를 아직 받지 못했어요" / "축제 팩을 받으면 계획을 볼 수 있어요". 행 `FCPlanRow`(시각 `HH:mm` Asia/Seoul, "공연 중", `FCTag("내 일정")`), 기수 순서 유지 · 팩에 없는 공연은 건너뜀.
- 첫 조회 실패(`DV:170-198`): notFound `FCEmptyCard("깃발을 찾을 수 없어요", "숨겨졌거나 더 이상 공개하지 않는 깃발이에요.")`, unavailable "깃발 정보를 불러오지 못했어요" + `FCButton("다시 시도", secondary, medium)`.
- 스켈레톤(`DS:12-53`): Hero 360 → P24 → 106 → 286, 태그 `festivalFlagDetailSkeleton`(iOS `FestivalAccessibilityID.swift:13`).
- 토스트(`DV:356-369`, 2초): 팔로우 완료(`FCToast.Item.followed(name)`, 아이콘 `staticLime`) · "팔로우는 100개까지 할 수 있어요" · "팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요" · "신고를 접수했어요"(check) · "이미 신고한 깃발이에요" · "신고하지 못했어요. 잠시 후 다시 시도해 주세요" · "잠금화면 표시를 바꾸지 못했어요". (잠금화면 변경 실패 토스트는 라이브 액티비티 단계에서 넣는다 — 이번 범위 아님)

### `FCReportSheet` (신규, `ui/sharedui`, iOS `FestiCoreSharedUI/Sources/Sheet/FCReportSheet.swift:16-18`)

"어떤 문제가 있나요?" · "신고는 익명으로 처리돼요" · 선택지 "가짜 깃발이에요"(fake) · "부적절한 사진이나 소개예요"(inappropriate) · "기타"(other) · "신고하기"(사유 없으면 꺼짐). 제목은 heading, 선택지는 selected 의미(`RV:31-35`).

### 상태 · 동작 (`FlagDetailViewModel`, iOS `DF:20-143`)

- State: `flagId` · `context` · `content(loading·loaded·failed(notFound|unavailable))` · `lastLoadedAt` · `isOffline` · `isFollowPending` · `isReportPending` · `toast` · `now` · `reportSheet` · `followOverride` · `loadGeneration`.
- 들어오면 `generation + 1`, 불러오기 + 60초 시계(`DF:220-228`). 나가면 모든 요청 · 시계 취소(`:230-242`).
- 다시 불러오기 실패: notFound → `failed(notFound)`, 받은 상세 있음 → `isOffline = true`, 없음 → `failed(unavailable)`(`:453-467`).
- 팔로우(`:249-253 · 523-554`): 비로그인 → `LoginRequested(follow)`. 성공 → override · 이벤트 · (pin 처리는 라이브 액티비티 단계) · 재조회 · `FollowChanged(flagId, isFollowing, followerCount)` · 팔로우했으면 완료 토스트. 실패는 목록과 같은 4갈래.
- 잠금화면(`:258-268 · 558-579`): **이번 범위의 화면 동작에서 뺀다.** D1은 `PUT me/pinned-flag` 도메인 API만 만들고 화면은 부르지 않는다. 라이브 액티비티 단계에서 낙관 갱신 후 PUT, 실패 시 되돌림, 표시값 규칙(누른 값 → 상세 `isPinned` → 문맥 `pinnedFlagId`, `:628-630`)을 구현한다.
- 신고(`:270-274 · 590-593`): 비로그인 → `ReportLoginRequested`. 시트 제출 → 시트 닫고 요청. `alreadyReported` · `loginRequired` · `termsRequired` · 그 외로 나눔.
- 문맥 변경(`:276-301`): `hasSession` · `accountId`가 바뀔 때만 계정별 값을 비우고 다시 불러온다.
- 문맥(`DC`, `FCtx:18-60`): `zoneNames` · `performances`(시작 · 끝이 ISO로 읽히는 공연만, 기본 "출연진 미정" · "무대 미정") · `mySetIds` · `hasSession` · `accountId` · `pinnedFlagId`. 선택 축제와 같은 팩 · 내 일정 · 보드 스냅샷만 쓴다.

## 화면 3: Crew (라이브 탭 스택)

### 크루 만들기 (`CCV` · `CCF`, S07 B/D)

- `FCTopBar(back, "크루 만들기", backgroundNavigation)` + 스크롤(좌우 16 · 상하 24 · 섹션 24) + 하단 고정 푸터(`CCV:19-46`).
- 이름 단계(`CCV:80-125`): "크루 이름을 정해 주세요" / "같이 온 친구들끼리만 서로 어느 존에 있는지 볼 수 있어요", `FCTextField(label "크루 이름", placeholder "예: 락린이들", 12자, "N/12", helper "단톡방 이름처럼 다들 알아보는 이름이 좋아요")`, 안내 상자 "· 최대 12명 · 페스티벌마다 크루 1개" · "· 초대 링크는 48시간 동안 쓸 수 있어요" · "· {축제명+이/가} 끝나면 기록만 남아요".
- 푸터(`CCV:162-196`): "만들고 초대 링크 받기"(제출 중 "만드는 중"). 생성 후 "초대 링크 보내기"(공유 문구 "FestiCore 크루에 초대해요", `:228-242`) + "크루 보기".
- 생성 후(`CCV:129-132 · 199-225`): "{이름} 크루를 만들었어요" / "초대 링크를 받은 사람은 바로 크루에 참여할 수 있어요.", 코드(Title1 Bold, 라벨 "초대 코드 " + 글자 사이 공백), "{M/d(E)} {시각}까지 쓸 수 있어요".
- 규칙(`CCF`): 12자에서 자름(`:137`), 제출 중 뒤로 막음(`:149`). 입력란 오류 = alreadyInCrew · festivalEnded · invalidInput · termsRequired(`:188-201`), 그 외 = `FCDialog("크루를 만들지 못했어요", "연결을 확인하고 다시 시도해 주세요.", "다시 시도" / "닫기")`. unauthenticated → 화면 닫고 크루 로그인(`FeCo:258-263`).

### 크루 메인 (`CV` · `CF`, S07 A/C/F/I)

- 구조(`CV:25-37 · 70-114`): `FCTopBar(back, 크루 이름 ?: "크루")` + 크루장만 `ic_gear_24`("크루장 메뉴") → 스크롤(간격 16 · 패딩 16) → 보관이 아니면 하단 상태 패널.
- 순서: 받은 모여 배너 → 요약 줄(높이 40, 크루장이면 "초대 링크" 공유 "초대 링크 공유") → "모여!" → 크루원 카드. 보관 상태: `FCBanner("{축제명+이/가} 끝났어요 · 기록만 볼 수 있어요")` → 요약 → 크루원 카드(`:92-121`).
- 요약(`CP:55-66`): "{N}명 · {M/d(E)} 자정까지 유지" / 보관 "{N}명 · {M/d(E)}에 끝남". 보관 시각 = 마지막 날 `endsAt`이 자정이면 그대로, 아니면 다음 날 0시 KST(`CF:226-238`).
- "모여!"(`CP:94-102`): 쿨다운 남으면 "모여! · {올림 분}분 뒤 다시 가능" 비활성.
- 모여 배너(`CV:129-225`, `CP:85-139`): `staticLime` · r16, "{닉네임}: {존명+으로/로} 모여!", "{상대시간} · 크루원 모두에게 소리 알림을 보냈어요". 받은 사람은 "{존}으로 가는 중" · "확인" 두 필, 보낸 사람은 "가는 중 {n}명 · 확인 {m}명"(없으면 "아직 응답한 크루원이 없어요"). 노출 = 보관 아님 · 만료 처리 id 아님 · `now < expiresAt ?: at + 10분`.
- 크루원 카드(`CV:267-428`): 로딩 스켈레톤 322 "크루원을 불러오는 중", 실패 `FCEmptyCard("크루를 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", "다시 시도")`, 머리 "크루원" · "{n} / 12". 행은 `FCCrewRow`(내 줄 `FCTag("나")`, 존 칩, 상태, 상대 시간 · 보관이면 `monthDayTime`), 귀가 중 · 60분 미갱신이면 0.6. 혼자면 "아직 아무도 안 들어왔어요" / "초대 링크를 단톡방에 보내 보세요 · 48시간 유효"(`:325-369`).
- 상태 패널(`CV:430-469`): "내 상태 · 탭 한 번이면 크루원에게 공유돼요", `FCBigButton(small)` 4개 — 나 여기(`ic_location_dot_20`) · 밥(`ic_clock_20`) · 화장실(`ic_circle_info_20`) · 귀가 중(`ic_route_24`).
- 토스트(위쪽, 2초, `CV:494-504`): "새 초대 링크를 만들었어요" · "크루 이름을 바꿨어요" · "{닉네임}님이 크루장이 됐어요" · "{닉네임}님을 내보냈어요" · "상태를 보내지 못했어요. 다시 눌러 주세요" · "모여 요청이 끝나서 응답하지 못했어요".
- 30초마다 `now`만 갱신(`CV:16-24 · 542`). 네트워크 폴링은 없다.

### 크루 메인 동작 (`CF`)

- 들어오면 `crew == null`일 때만 상세 GET, 그 전엔 보드의 `live`로 그린다(`:247-249 · 455-459`). forbidden · notFound → `CrewClosed`(`:460-464`).
- 상태 보내기 **낙관 갱신**(`:316-338`) → 실패 시 토스트 + 재조회(`:468-476`). 귀가 중이면 존을 보내지 않는다(`CUc:59`).
- 모여 응답 가드(`:299-313`) · 409 `GATHER_EXPIRED` → 배너 내림 + 토스트 + 재조회, 그 외 `FCDialog("모여에 응답하지 못했어요", …)`(`:481-499`).
- 크루장 동작 실패 `FCDialog("요청하지 못했어요", …)`(`:535-542 · 671-680`). 확인 다이얼로그: 이전 "{닉네임}님에게 크루장을 넘길까요?" / 내보내기 "{닉네임}님을 내보낼까요?" / 해산 "크루를 해산할까요?"(`:634-662`). 이름 · 초대 재생성은 확인 없음.

### 크루장 메뉴 (`CMV` · `CMF`, 시트)

"크루 관리" 단계 = 크루 이름 바꾸기(`ic_list_check_20`) · 초대 링크 다시 만들기(`ic_share_nodes`) · 크루장 넘기기(`ic_users_24`) · 크루원 내보내기(`ic_user_24`) · 크루 해산하기(`ic_circle_xmark`) + "초대 링크를 다시 만들면 이전 링크는 바로 막혀요. 크루장은 넘기거나 해산해야 나갈 수 있어요."(`CMV:60-100`). 이름 단계(`:148-172`, "N / 12", 현재 이름과 다르고 1~12자일 때 저장), 크루원 선택 단계(`:174-203`, 자신 제외). 크루장만 연다(`CF:255-264`).

### 모여 시트 (`CGV` · `CGF`)

"어디로 모일까요?" / "누르면 바로 크루원 {n}명에게 소리 알림이 가요". 2열 `FCChoiceTile`(44, 간격 12, 최대 높이 420). 누르면 바로 요청. 쿨다운이면 서버 호출 없이 "잠시 후 다시 모일 수 있어요". 실패해도 시트를 유지(`CGF:87-142`). 존 없음 `FCEmptyCard("공개된 존이 없어요", "공식 존 정보가 공개되면 집결을 요청할 수 있어요.")`.

### 참여 확인 (`CJV` · `CJF`, S08)

- 로딩 스켈레톤 154 "초대를 불러오는 중" / 만료 · 없음 "초대가 만료됐어요" / 실패 "초대를 불러오지 못했어요" + "다시 시도"(`CJV:47-74`).
- 본문(`CJV:82-170`): 아바타 스택(52, 겹침 14, 테두리 3, 최대 3명 + "+N", 라벨 "크루원 {n}명") 또는 72 원, "{초대한 사람}님이 크루에 초대했어요", 크루 이름, 차단 사유(만료 → 정원 순, `CJF:296-302`), 정보 카드 "페스티벌" · "인원 {n} / 12명" · "초대 만료", 안내 "참여하면 크루원에게 내 존과 상태가 보여요. 정확한 위치(GPS)는 공유하지 않아요."
- 푸터(`CJV:354-387`): 차단이면 "닫기", 아니면 "취소" + "참여하기"("참여하는 중").
- 동작: 비로그인 "참여하기" → `LoginRequested(crewName)`(`CJF:159-163`). 409 alreadyInCrew → 라이브 보드로 현재 크루를 찾아 "기존 크루에서 나갈까요?" → 나가기 성공 후 참여(`:201-269`). 로그인 뒤에는 `hasSession`만 바꾸고 자동 참여하지 않는다(`:164-166`).

## 서버 계약

모두 `api/v1`, 응답 `{code, message, data}`이고 `code == "SUCCESS"`를 본다. stage(`https://festicore-dev.up.railway.app/`)에서 **인증 없는 GET만** 직접 호출해 확인했다(2026-11-23). 쓰기 요청은 보내지 않았다. 인증 열은 `/api-docs-json`의 `security`와 iOS 요청 정의를 대조한 값이다.

| 용도                    | 요청                                                                                        | 인증                                  | 근거 · stage 확인                                                                                                                                          |
| ----------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 라이브 보드             | `GET festivals/{id}/live`                                                                   | `X-Device-Id` 필수, 로그인이면 Bearer | 이미 옮김 `FA:44-60`                                                                                                                                       |
| 공개 깃발               | `GET festivals/{id}/flags`                                                                  | 없음                                  | 이미 옮김. stage `fst_00e0f4f1d8065a93` 200 · 9개, 키 `bio · followerCount · homeZoneId · id · name · photoUrl · plan · planVisible · tags · thumbUrl`     |
| 상세                    | `GET flags/{flagId}`                                                                        | 선택(익명 허용)                       | iOS `PublicFlagRequest.swift`. stage 익명 200, `isFollowing · isBearer · isPinned = null`, `status{flagId, status, zoneId, activity, updatedAt}`, `hidden` |
| 팔로우                  | `PUT flags/{flagId}/follow` + `Idempotency-Key`                                             | Bearer                                | `FFS:29-100`. 응답 `flagId · following · followerCount`. 401 · 403 `TERMS_REQUIRED` · 404 · 409 `FOLLOW_LIMIT`                                             |
| 언팔로우                | `DELETE flags/{flagId}/follow`                                                              | Bearer                                | 같음, Idempotency-Key 없음                                                                                                                                 |
| 신고                    | `POST flags/{flagId}/report` `{reason}`                                                     | Bearer                                | `FlagUserRequest.swift`. 409 `ALREADY_REPORTED`                                                                                                            |
| 대표 깃발               | `PUT me/pinned-flag` `{flagId: String?}`(null 명시)                                         | Bearer                                | `FestivalFlagActionResponseDTO.swift:18-45`. 409 `PIN_NOT_FOLLOWING`                                                                                       |
| 크루 생성               | `POST crews` `{festivalId, name, nickname?}`                                                | Bearer                                | `CRq:6-20`. iOS는 `nickname`을 보내지 않음                                                                                                                 |
| 크루 상세 · 이름 · 해산 | `GET` · `PATCH {name}` · `DELETE crews/{id}`                                                | Bearer                                | stage `GET crews/abc` 익명 → `UNAUTHORIZED "인증 토큰이 없습니다."`                                                                                        |
| 상태                    | `POST crews/{id}/status` `{zoneId?, state, clientAt}` + `Idempotency-Key`                   | Bearer                                | `CRq:83`, `CRp:133 · 280-285`(clientAt 밀리초 ISO UTC)                                                                                                     |
| 초대 재생성 · 이전      | `POST crews/{id}/invite/rotate` · `POST crews/{id}/transfer {toUserId}`                     | Bearer                                | `CRq`                                                                                                                                                      |
| 내보내기 · 나가기       | `DELETE crews/{id}/members/{userId}`                                                        | Bearer                                | 409 `OWNER_CANNOT_LEAVE` → forbidden(`CRp:270-275`)                                                                                                        |
| 초대 미리보기           | `GET crews/invite/{code}`                                                                   | 없음                                  | stage `ZZZZZZ` → `NOT_FOUND "초대를 찾을 수 없습니다."`. OpenAPI 404 "없거나 만료된 코드" · 429 "분당 조회 제한 초과"                                      |
| 참여                    | `POST crews/join {inviteCode}`                                                              | Bearer                                |                                                                                                                                                            |
| 모여 · 응답             | `POST crews/{id}/gather {zoneId}` · `POST crews/{id}/gather/{gatherId}/response {response}` | Bearer                                | 429 cooldown, 409 `GATHER_EXPIRED`                                                                                                                         |

- 크루 요청은 모두 재시도 0(`CRq:96`). 공통 오류 매핑 `CRp:317-330`.
- 입력 검증(`CUc:19-22 · 144-159`): 이름 trim 1~12자, ID ASCII 영숫자 · `-` · `_` 1~64자, 초대 코드 6자 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`(대문자화). 깃발 ID도 같은 문자 규칙으로 요청 전에 막는다.
- OpenAPI에는 이번 범위 밖 경로도 있다: `PATCH flags/{id}` · `flags/{id}/{photo,status,bearer,co-bearers}` · `bearer/{claim,register}` · `me/bearer-flags`(기수, 범위 밖) · `GET me/follows` · `GET me/pinned-flag`(iOS 이 화면들에서 호출 안 함) · `PATCH crews/{id}/members/me`(닉네임, iOS에 화면 없음).
- stage에서 `flagsEnabled == true`인 축제는 목록 50개 중 `fst_00e0f4f1d8065a93`(ended) 하나였다. 에뮬레이터 확인은 이 축제로 한다.

## 비로그인 · 기기 · 권한

- 비로그인: 깃발 목록(`X-Device-Id`만) · 정렬 · 필터 · 상세(익명) · 공유 · 초대 미리보기 가능. 팔로우 · 신고 · 크루 생성 · 크루 메인 · 참여는 로그인 필요.
- 기기 인증: 깃발 목록만 `X-Device-Id`가 필요하다(라이브 보드 경유, 이미 옮김). 깃발 쓰기 · 크루 API는 Bearer만 쓴다.
- 약관 재동의(403 `TERMS_REQUIRED`): 팔로우 · 신고 · 대표 깃발은 세션 프로필 재확인(`MTCSc:195`, iOS `session.retryProfile`)으로 넘긴다. 크루는 화면 문구 "최신 이용약관에 동의한 뒤 다시 시도해 주세요."만 보인다. Android 세션 재확인 경로는 쟁점 F6.
- 권한: 위치 · 카메라 · 연락처 없음. `F/Flag` · `F/FlagDetail` · `F/Crew`에서 권한 코드를 찾지 못했다. iOS의 유일한 위치 권한은 귀가 카드용(`FestiCore-Info.plist:67-68`)이다.

## 화면 전환

| 출발                                            | 동작                                                                                     | iOS                                             | Android                                                                     |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------- | ----------------------------------------------- | --------------------------------------------------------------------------- |
| 깃발 탭 루트                                    | 목록                                                                                     | `FCo:23-28`                                     | `FlagCoordinator` routes = `[FlagRoute.Directory]`                          |
| 목록 카드 · `Flag(id)` 딥링크                   | 상세 push, 기존 상세는 지우고 하나만                                                     | `FCo:174-184`, `MTC:386-394`                    | `FlagRoute.Detail(flagId)`, `FestivalTransitions.push`(쟁점 F7)             |
| 상세 뒤로 · 시스템 뒤로                         | 목록으로                                                                                 | `FCo:120-125`                                   | `NavDisplay` `onBack` pop                                                   |
| 라이브 보드 크루 카드                           | 크루 메인 push(이미 있으면 무시)                                                         | `FeCo:211-219 · 713-725`                        | `FestivalRoute.Crew(crewId)`                                                |
| "크루 만들기"                                   | 만들기 push · "크루 보기"는 만들기를 크루로 교체                                         | `FeCo:222-257`                                  | `FestivalRoute.CrewCreate(festivalId)`                                      |
| `CrewInvite(code)` 딥링크                       | 라이브 탭 선택 → 기존 참여 · 크루 화면 지우고 참여 확인 push · 참여 성공이면 크루로 교체 | `MTC:379-384`, `FeCo:414-446`                   | `LiveTabRequest.CrewInvite(code)` → `FestivalRoute.CrewJoin(code)`          |
| 크루 "모여!" · 보드 "모여"                      | 모여 시트                                                                                | `CF:270-284`, `FestivalFeature.swift:1007-1016` | `FCSheet` 다이얼로그 State(경계 `CrewGather`)                               |
| 크루장 메뉴 · 신고                              | 시트                                                                                     | `CF` destination, `DV` `fcModal`                | `FCSheet`                                                                   |
| 해산 · 쫓겨남                                   | 크루 화면 닫고 보드 다시 받기                                                            | `CF:533-534`, `FeCo:267-282`                    | `CrewClosed` → pop + 보드 재시도                                            |
| 존 지도 "깃발을 팔로우하면…" · 보드 "깃발 찾기" | 깃발 탭 선택                                                                             | `FeCo:201-206 · 464-468`                        | 이미 있음(`FCoA:101-105`), 보드 `FlagDirectory` 경계도 같은 요청으로 바꾼다 |

## 탭 바 규칙

- 깃발 탭은 목록 · 상세 모두 탭 바를 보인다(`MTC:109-111`, Figma S05). 숨김 조건 없음.
- 라이브 탭은 `crew` · `crewJoin` · `crewCreate`(+ PrepGuide 3단계)가 맨 위면 숨긴다(`MTC:82-90`, 하단 고정 영역 때문). 지금 Android 라이브 흐름의 `onHidesTabBarChanged`(`MTS:39 · 48`)에 이 조건을 더한다.
- 로그인 시트가 떠 있는 동안 숨긴다(`MTC:93`).
- 같은 탭을 다시 눌러도 루트로 pop하지 않는다(`MTC:218-229`, `tabReselected`가 `.none`).
- 탭을 떠나도 깃발 스택을 유지한다(`MTV` 주석). Android는 `rememberSaveableStateHolder`(`MTS:43`) + `FlagCoordinator`를 탭 밖(`AppFlow`)에서 소유해 같은 효과를 낸다.
- 깃발 탭을 고를 때마다 존 이름 · 선택 축제 · 열린 상세 문맥을 동기화한다(`MTCSy:31-64`, 라이브 스택에서 뒤에서부터 찾은 첫 보드의 축제).
- 계정이 바뀌면 목록 `accountChanged`, 열린 상세는 새 문맥으로 다시 불러온다. 스택은 pop하지 않는다(`MTC:489-547`).

## 로그인 시트 (쟁점 F1)

iOS 로그인 시트는 `MainTabCoordinator`가 `AuthCoordinator`로 띄운다(`MTV:135-141`, `AuC`). 내용 높이 자동 · 손잡이 있음, 지름 56 `staticLime` 원 + 아이콘 24(`crew`면 `ic_users_24`, 그 외 깃발), 제목 `baseTitle3Bold` · 본문 `baseSubheadlineRegular` 가운데, 로그인 버튼(Android는 Google만), "나중에 할게요"(`LMV:44-108`). 실패 "로그인에 실패했습니다. 다시 시도해 주세요."(`LF:163-169`).

| 문맥           | 제목                                   | 본문 (`LMV:111-144`)                                                                                      |
| -------------- | -------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| profile        | 계정 정보를 보려면 로그인이 필요해요   | 로그인 후 프로필을 확인할 수 있어요.                                                                      |
| flag · follow  | 깃발을 팔로우 하려면 로그인이 필요해요 | 내 취향의 깃발을 팔로우하고,\n함께 페스티벌을 즐길 사람들과 크루를 만들어봐요.                            |
| crew           | 크루와 함께 보려면 로그인이 필요해요   | 크루원끼리 지금 어느 존에 있는지 서로 볼 수 있어요.\n공연과 타임테이블은 로그인 없이 그대로 쓸 수 있어요. |
| crewJoin(name) | 크루에 들어가려면 로그인이 필요해요    | 로그인하면 바로 {name ?: "크루"}에 들어가요.\n크루원끼리만 서로의 존과 상태를 볼 수 있어요.               |
| bookmark       | 축제를 저장하려면 로그인이 필요해요    | 로그인 후 선택한 축제를 저장할 수 있어요.                                                                 |
| report         | 깃발을 신고하려면 로그인이 필요해요    | 로그인 후 문제가 있는 깃발을 신고할 수 있어요.                                                            |

- 문맥이 없으면 탭 기본값: 마이 = profile, 깃발 = flag, 라이브 · 타임테이블 = bookmark(`MainTabAuthInputs.swift:48-58`).
- 로그인 뒤 원래 동작을 다시 하는 것은 축제 저장(bookmark)뿐이다(`MTC:257-263`, `AuC:154`). 깃발 · 크루는 세션 변경으로 화면만 갱신된다.
- 신규 계정이면 가입 화면으로 이어진다(`AuC:455-477`). Android는 기존 `SignUpScreen`을 재사용한다.

## iOS → Android 적응

| iOS                                                               | Android                                                                                                  | 이유                                                                                  |
| ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `FestivalFlagFeature` + 자식 `FestivalFlagDirectory`              | `FlagViewModel` 하나 + `FlagDirectory` 순수 함수(정렬 · 필터 · reconcile)                                | Orbit은 자식 Store가 없다. 순수 함수로 두면 iOS 디렉터리 테스트를 그대로 옮길 수 있다 |
| `FlagCoordinator`(TCAFlow, `.root(embedInNavigationView)`)        | `FlagCoordinator` ViewModel + `FlagFlow`(`NavDisplay`)                                                   | `.claude/docs/navigation.md`                                                          |
| `FestivalFlagDetailContext`를 부모가 주입 · `updateDetailContext` | `FlagFlow`에 `StateFlow<FlagDetailContext>` 인자 → ViewModel `onContextChanged`                          | 문맥은 MainTab이 보드 · 팩 · 내 일정 · 세션에서 만든다                                |
| `ShareLink(item: URL)`                                            | `Intent.ACTION_SEND` + `createChooser`, `EXTRA_TEXT` = 링크(만들기 화면은 문구 + 줄바꿈 + 링크, 쟁점 F5) | 플랫폼 공유 시트                                                                      |
| `fcModal(height: .auto)`                                          | `FCSheet`(이미 있음)                                                                                     |                                                                                       |
| `.alert` · confirmationDialog                                     | `FCDialog`                                                                                               |                                                                                       |
| `TimelineView(.periodic 30 · 60)`                                 | ViewModel 시계 `subIntent { while(true) { delay; reduce { now } } }`                                     | 테스트에서 시계 주입                                                                  |
| 잠금화면 대표 깃발(Live Activity)                                 | 이번 범위 아님 — D1에 `PUT me/pinned-flag` API만, 토글 · 표시는 라이브 액티비티 단계(결정 F4-1)                                                                                  | Live Activity는 옮기지 않는다(`ios-parity.md`)                                        |
| `UUID().uuidString` Idempotency-Key                               | `java.util.UUID.randomUUID().toString()`                                                                 | 표준 라이브러리                                                                       |
| `Apple` · `Google` 로그인 버튼                                    | Google 버튼만                                                                                            | `ios-parity.md` "옮기지 않는 것"                                                      |
| `swipeBackButtonHidden()`                                         | 시스템 뒤로 = pop                                                                                        | Android 관례                                                                          |

## PR 묶음 (데이터 → 깃발 목록 → 상세 → 크루 → 로그인 시트 → 탭 연결)

| #   | PR                                         | 모듈                                                                                                                                                    | 검증                                                                                                                                                          |
| --- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D1  | 깃발 쓰기 · 상세 API와 도메인              | `service/api`(`flags/{id}` · follow · report · `me/pinned-flag`(API만, 화면 미호출), Idempotency-Key 도우미) · `service/festivalcatalog`(팔로우 서비스) · `domain/festival` | Repository 테스트: iOS `FestivalFlagDetailRepositoryTests` · `FestivalFlagFollowTests` 이름 그대로, 익명 응답 null 유지, 오류 코드 매핑, 잘못된 ID 요청 안 함 |
| D2  | 크루 API와 도메인                          | `service/api`(crews 13개 경로) · `domain/festival`(`FestivalCrew` · `Invite` · UseCase 검증)                                                            | iOS `FestivalCrewTests` · `FestivalCrewRepositoryTests` · `CrewRequestTests` 이름 그대로, 상태 요청 Idempotency-Key, 초대 코드 검증                           |
| F1  | 깃발 목록                                  | `feature/festival`(`flag/` Contract · ViewModel · Screen · `FlagDirectory`)                                                                             | `FestivalFlagFeatureTests` · `FestivalFlagDirectoryTests` 흐름, Preview                                                                                       |
| F2  | 깃발 상세 + `FCReportSheet`                | `feature/festival`(`flagdetail/`, 잠금화면 행 제외) · `ui/sharedui`                                                                                                       | `FestivalFlagDetailFeatureTests` 22개 흐름                                                                                                                    |
| F3  | `FlagCoordinator` · `FlagFlow`             | `feature/festival`                                                                                                                                      | iOS `FlagRoutingTests` 중 Coordinator 내부 흐름(상세 하나만 · 뒤로 · followChanged 반영)                                                                      |
| C1  | 크루 만들기 · 크루 메인 · 메뉴 · 모여 시트 | `feature/festival`(`crew/` · `crewcreate/` · `crewgather/`) · `FestivalCoordinator` 경계 연결                                                           | `FestivalCrewFeatureTests` · `FestivalCrewCreateFeatureTests` · `FestivalCrewGatherFeatureTests` · `FestivalCrewCreateRoutingTests`                           |
| C2  | 참여 확인                                  | `feature/festival`(`crewjoin/`)                                                                                                                         | `FestivalCrewJoinFeatureTests` 8개                                                                                                                            |
| L1  | 로그인 시트(쟁점 F1 a)                     | `feature/auth` · `app`                                                                                                                                  | 문맥별 문구, 닫기, 성공 후 시트 닫힘, 신규 계정 → 가입                                                                                                        |
| T1  | 탭 연결 · 딥링크                           | `app`(`MTS` 깃발 자리, `MTCA` `Flag` · `CrewInvite`, 문맥 동기화, 탭 바 규칙, 보드 `Login*` 경계 → 로그인 시트)                                         | iOS `FlagRoutingTests` 7개 · `FestivalCrewCreateRoutingTests` 중 경로, `Flag` · `CrewInvite` 딥링크 테스트                                                    |

- D1 · D2는 서로 독립이다. 사용자 규칙대로 D1 → D2 순으로 하나씩 올린다.
- F1~F3은 화면만 있고 앱에 아직 안 보이므로 각 PR은 Preview와 단위 테스트로 검증한다. 사용자에게 보이는 것은 T1부터다.
- L1이 없으면 F1 · F2 · C의 로그인 요청은 경계로만 남는다. L1을 T1 앞에 둔다.

### 기능 spec (`.spec/`)

- `feature/festival/.spec/flag-tab.md` — 목록 · 상세 동작 규칙(정렬 · reconcile · 지금 카드 문구). 잠금화면 규칙은 라이브 액티비티 단계 spec에서. F1 첫 커밋.
- `feature/festival/.spec/crew.md` — 크루 메인 · 만들기 · 모여 · 참여 규칙. C1 첫 커밋.
- `feature/auth/.spec/login-sheet.md` — 문맥 · 탭 기본값. L1 첫 커밋.

## 새 라이브러리 후보

없다. 필요한 것은 모두 기존 의존성이나 플랫폼 API로 된다.

| 필요                          | 쓰는 것                                                                        | 라이선스                     |
| ----------------------------- | ------------------------------------------------------------------------------ | ---------------------------- |
| 썸네일 · Hero · 아바타 이미지 | 이미 있는 Coil 3.6.3(`gradle/libs.versions.toml:26 · 68-70`) + `FCRemoteImage` | Apache-2.0(추가 승인 불필요) |
| 공유                          | `android.content.Intent.ACTION_SEND`                                           | 플랫폼                       |
| Idempotency-Key               | `java.util.UUID`                                                               | 표준                         |
| 당겨서 새로고침               | Compose Material3 `PullToRefreshBox`(이미 쓰는지 구현 PR에서 확인)             | Apache-2.0                   |
| 초대 QR                       | iOS에 없음 → 넣지 않는다                                                       | —                            |

## 위험

- 공개 목록이 서버에서 최대 10분 캐시된다는 근거는 iOS 주석뿐이다(`FD` reconcile). reconcile 규칙을 그대로 옮기면 동작은 같다.
- 크루 메인이 열린 동안 라이브 보드 35초 폴링이 크루 화면 `live`를 갱신하는지 iOS 코드에서 확인하지 못했다. 확인 전까지는 iOS 크루 화면처럼 이벤트 때만 재조회한다.
- 커스텀 스킴 `festicore://crew/...`만 있다(https App Link 없음). 메신저에서 링크가 눌리지 않을 수 있다 — iOS도 같으므로 이번 범위 밖.

## 쟁점 (추천안 포함)

- **F1 로그인 시트 범위.** (a) 이번 묶음에 L1로 넣는다(추천): 깃발 팔로우 · 신고 · 크루 생성 · 참여가 모두 로그인 시트로 끝나고, 보드의 `Login` · `CrewLogin` · `FollowLogin` 경계(`LBC:148-153`)도 같은 자리에 걸린다. `LaunchLoginViewModel`의 Google 흐름을 재사용하면 새 계약이 작다. (b) 기존 전체 화면 `LaunchLogin`으로 보낸다 — 빠르지만 iOS 문맥 문구 · "나중에 할게요"가 없고 탭 상태가 덮인다. (c) 빼고 경계만 남긴다 — 비로그인 사용자는 팔로우 · 크루를 눌러도 아무 일이 없다.
- **F2 크루 화면 위치.** (a) iOS대로 라이브 탭 스택(`FestivalRoute`)(추천, `FeCo:758-767`). (b) 깃발 탭에 둔다 — 요청 문구는 "깃발 탭 전체"지만 iOS 깃발 탭에는 크루 진입이 없다(`FCo:217-221`). 코드는 `feature/festival` 같은 모듈이라 위치만 다르다.
- **F3 크루 만들기 · 모여 시트 포함.** iOS에서는 `F/LiveBoard/` 아래지만 크루 메인이 모여 시트를 열고 보드가 만들기를 연다. (a) C1에 함께 넣는다(추천). (b) 별도 PR.
- **F4 잠금화면 표시 토글.** iOS는 대표 깃발을 Live Activity · 위젯에 쓴다. (a) 토글 · `PUT me/pinned-flag`는 iOS대로 두고 Android 표시는 별도 이슈(추천, 서버 값은 계정 공유라 iOS 사용자가 같은 계정이면 그대로 쓰인다). (b) 토글을 숨긴다 — 같은 계정의 iOS 값을 Android에서 끌 수 없다. 문구 "잠금화면에 표시"를 Android에서 유지할지는 사람의 확인이 필요하다.
- **F5 공유 내용.** (a) iOS대로 크루 메인 = 링크만, 만들기 = "FestiCore 크루에 초대해요" + 링크, 깃발 = 링크(+ 제목 이름)(추천). (b) 모든 공유에 문구를 붙인다.
- **F6 약관 재동의(`termsAcceptanceRequested`).** iOS는 `session.retryProfile`(프로필 재조회로 약관 화면 유도). Android 세션 쪽 같은 진입점이 있는지 이 문서에서 확인하지 못했다. (a) L1에서 `service/auth`의 프로필 재확인을 찾아 잇고, 없으면 경계 로그만(추천). (b) 토스트로만 안내.
- **F7 상세 전환 애니메이션.** iOS는 TCAFlow 기본 push(커스텀 없음, `FCoV:18-38`). (a) 라이브 탭과 같은 `FestivalTransitions.push`(추천). (b) Navigation 3 기본.
- **F8 `deviceNotRegistered` 회복.** iOS 문구는 "앱을 다시 실행해…". Android 기기 등록은 push-device 설계에서 보류 중이다. (a) 문구는 iOS대로, 회복 동작은 추가하지 않는다(추천).

## 경계 (다른 문서)

- **PrepGuide 2단계**(`2026-11-22-live-extras-android-design.md:15 · 238-243`): iOS `FestivalPrepFlagsFeature`가 깃발 팔로우를 품는다. 이 문서의 D1 팔로우 서비스 · `FlagDirectory`를 재사용하도록 PrepGuide 구현 PR이 가져간다. 이 문서는 PrepGuide 화면을 만들지 않는다.
- **푸시**(`2026-11-22-push-device-android-design.md`): 깃발 상태 · 크루 상태 · 모여 알림은 서버 푸시다. 수신 · 표시는 보류 중인 푸시 문서 범위이고, 이 문서는 `Flag(id)` · `CrewInvite(code)` 딥링크를 여는 것까지만 한다. `route` `/crews/<id>`는 iOS도 처리하지 않는다(`DL:90`).
- **기수(Bearer)**: 깃발 목록 · 상세에 진입점이 없다(`F/Flag` · `F/FlagDetail` grep 0건). 보드 `Bearer` 경계 · 마이 탭 · 가입 흐름은 별도 문서.

## 확인 못 한 것

- designkit 컴포넌트 일부의 iOS 내부 수치(`FCTopBar(large)` 높이, `FCChip` · `FCButton(medium)` · `FCListRow` · `FCSectionHeader` 패딩)와 Android 이식본이 같은지. 구현 PR에서 컴포넌트 파일을 대조한다.
- `FCToast.Item.followed(name)` 문구, 디렉터리 로그인(`loginRequested(nil)`)이 깃발 탭에서 `flag` 문맥으로 풀리는 근거는 `MainTabAuthInputs.swift:48-58` 한 곳뿐.
- iOS 인자화 테스트(`@Test(arguments:)`) 3건의 함수 이름.
- 크루 메인이 열린 동안 보드 폴링이 `live`를 갱신하는지, `crewClosed` 뒤 보드 재조회 코드의 정확한 위치.
- `swipeBackButtonHidden()` 구현(iOS 상세 스와이프 뒤로 여부). Android는 시스템 뒤로라 영향 없음.
- 서버가 크루 생성 `nickname` 생략 시 무엇을 쓰는지, 초대 48시간 · 모여 10분 · 정원 12명의 서버 값(iOS 상수 · 문구로만 확인).
- 인증이 필요한 응답 본문(팔로우 · 크루 상세 등) — stage에 쓰기 · 로그인 요청을 보내지 않아 실제 응답을 보지 못했다. iOS DTO 기준이다.
- `GET flags/{id}/thumbnail`은 확인한 깃발에서 404였다. iOS는 이 경로를 쓰지 않고 `thumbUrl`을 쓴다.
- 이 문서는 Codex 교차 검토를 받지 않았다.

## 결정 (호스트, 2026-11-23 — 사용자 지시 "묻지 말고 iOS와 같게")

- F1~F3 · F5~F8 추천안대로. F4는 아래 F4-1 결정으로 대체(토글 숨김, 라이브 액티비티 단계로 이관).
- (Codex R1 F4-1 반영, 위 F4 결정을 대체) 타임테이블 알림과 같은 원칙으로, 약속한 동작이 실제로 없으면 보이지 않게 한다. FlagDetail의 '잠금화면 표시' 토글은 **라이브 액티비티(진행 중 알림) 단계가 잠금화면 표시를 구현할 때까지 숨기고**, 이번 수용 기준 4에서 뺀다. 서버 PUT 도메인 API(D1)는 미리 만들어 두되 화면에서 부르지 않는다. 라이브 액티비티 단계 수용 기준에 "토글 노출 + 실제 잠금화면 표시"를 넣는다.
- (Codex R3 CREW-LOGIN-COPY-1 처분: iOS와 같게 유지) iOS도 `crewJoin(name)` 시트 문구는 "로그인하면 바로 {name}에 들어가요"(Feature/Auth/Sources/Login/View/LoginModalView.swift)이고, 로그인 뒤 참여 화면은 `sessionChanged`로 hasSession만 바꿀 뿐 자동 참여하지 않는다(FestivalCrewJoinFeature.swift:164-166). 사용자 지시(iOS와 같게)에 따라 문구 · 동작을 그대로 옮기고, 문구와 동작의 차이는 iOS와 함께 고칠 후속 항목으로 남긴다(Android 단독으로 바꾸지 않음). 로그인 뒤 사용자는 참여 확인 화면에서 '참여하기'를 한 번 더 누른다.

- (Codex R5 F4-3, 라운드 상한 뒤 호스트 반영) 상세 ViewModel 상태에서 `pin(override, isPending)`을 빼고 팔로우 성공 동작의 pin 초기화도 뺐다. pin 상태 · 동작 전체는 라이브 액티비티 단계 사양으로 옮긴다.
