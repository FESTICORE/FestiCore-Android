# 라이브 탭 나머지 화면(ZoneMap · PrepGuide · NotificationInbox) Android 이식 설계

- 날짜: 2026-11-22
- 이슈: 미정(구현 이슈를 만들 때 적는다)
- 상태: 제안
- 기준: Android `develop` ab74e4b, iOS `09eda3b8`(로컬 `../FestiCore-iOS`, 읽기 전용). 보드 설계(`2026-11-22-live-board-android-design.md`)는 iOS `fcf53272` 기준이었다. 아래 iOS 행 번호는 모두 `09eda3b8`에서 다시 열어 확인했다.
- 범위: `feature/festival` · `domain/festival` · `domain/usernotification`(신규) · `service/api` · `app`(`MainTabScreen` 탭 전환 콜백)

## 목표

라이브 보드 설계가 "경계만 표시"로 남긴 진입점 세 개의 대상 화면을 옮긴다.

1. **ZoneMap(S06 존 지도)** — 보드 크루 카드의 존 칩에서 push. 받아 둔 팩의 존 영역 위에 라이브 깃발 · 크루를 놓는다.
2. **NotificationInbox(알림함)** — 보드 상단 종 아이콘(로그인 시)과 24시간 새 알림 배너에서 push. 보드의 안 읽은 수 배지 · 배너도 이 문서에서 채운다.
3. **PrepGuide(S15 준비 안내)** — 로그인 사용자가 팩을 처음 받으면 3단계(내 일정 → 깃발 팔로우 → 준비물)를 push. 2단계만 `Feature/Festival/Sources/PrepGuide`에 있고 1 · 3단계와 2단계의 본체는 아직 옮기지 않은 Timetable · Flag · Profile 화면이다(아래 "PrepGuide 선행 조건"). 이 문서는 흐름 · 트리거 · 2단계 화면을 설계하고, 구현 PR은 선행 문서 뒤에 둔다(결정 E10).

시각(수치 · 문구 · 토큰 · 에셋 · 접근성 라벨)은 iOS View와 같게, 플랫폼 관례(뒤로 · 팝업 · 수명 주기)는 Android 방식을 따른다.

iOS 원본 약어(`iOS` = `../FestiCore-iOS/Projects`):

| 약어           | 경로                                                                                                                                                                                                                                                                                   |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ZM`           | `iOS/Feature/Festival/Sources/ZoneMap` (`Reducer/FestivalZoneMapFeature.swift` · `Model/FestivalZoneMapModel.swift` · `View/FestivalZoneMapView.swift` · `View/FestivalZoneMapSkeletonView.swift` · `Components/FestivalZoneMapCanvas.swift` · `Components/FestivalZoneMapCard.swift`) |
| `NI`           | `iOS/Feature/Festival/Sources/NotificationInbox` (`Reducer/NotificationInboxFeature.swift` · `Reducer/FestivalFeature+NotificationUnreadCount.swift` · `Model/NotificationInboxPresentation.swift` · `View/NotificationInboxView.swift`)                                               |
| `PG`           | `iOS/Feature/Festival/Sources/PrepGuide` (`Reducer/FestivalPrepFlagsFeature.swift` · `View/FestivalPrepFlagsView.swift`)                                                                                                                                                               |
| `FD`           | `iOS/Domain/FestivalDomain`                                                                                                                                                                                                                                                            |
| `UND`          | `iOS/Domain/UserNotificationDomain`                                                                                                                                                                                                                                                    |
| `FF`           | `iOS/Feature/Festival/Sources/FestivalList/Reducer/FestivalFeature.swift`                                                                                                                                                                                                              |
| `LBS`          | `iOS/Feature/Festival/Sources/LiveBoard/View/FestivalLiveBoardScreen.swift`                                                                                                                                                                                                            |
| `FCo` · `FCoV` | `iOS/App/Sources/Navigation/Festival/FestivalCoordinator.swift` · `FestivalCoordinatorView.swift`                                                                                                                                                                                      |
| `MTC`          | `iOS/App/Sources/Navigation/MainTab/MainTabCoordinator.swift`                                                                                                                                                                                                                          |

Android 약어(`FS` = `feature/festival/sources/src/main/kotlin/io/festicore/feature/festival`): `FCoA` = `FS/FestivalCoordinator.kt`, `FFl` = `FS/FestivalFlow.kt`, `LBC` · `LBV` · `LBScr` · `LBSec` · `LBP` = `FS/liveboard/LiveBoard{Contract,ViewModel,Screen,Sections,Presentation}.kt`, `MTS` = `app/src/main/kotlin/io/festicore/app/navigation/MainTabScreen.kt`.

## 지금 Android 상태 (경계)

| 진입점                      | Android 현재                                                                                    | 근거                                      |
| --------------------------- | ----------------------------------------------------------------------------------------------- | ----------------------------------------- |
| 크루 존 칩 → 존 지도        | `LiveBoardBoundary.ZoneMap(festivalId, zoneId)`를 Coordinator가 `lastBoundary`에 받아 두기만 함 | `LBV:183`, `LBSec:470`, `FCoA:15-16 · 56` |
| 활성 깃발 타일의 존 칩      | 무동작(iOS도 무동작 `FestivalLiveBoardCards.swift:149-150`)                                     | `LBSec:568-569`                           |
| 종 아이콘                   | 로그인이면 `NotificationInbox`, 아니면 `Login` 경계. 배지 `badgeCount = 0` 고정, 배너 없음      | `LBV:167-169`, `LBScr:331`                |
| 첫 팩 받기 완료 → 준비 안내 | 경계 자체가 없음(`LiveBoardBoundary`에 PrepGuide 항목 없음)                                     | `LBC:120-156`                             |
| 탭 바                       | `routes.size == 1 ∨ (맨 위 LiveBoard ∧ 시트/다이얼로그)`                                        | `FCoA:22-23`                              |

보드 설계는 "크루 존 칩 · 활성 깃발 존 → 존 지도"라고 적었지만, iOS에서 존 지도를 여는 것은 크루 칩뿐이다(`FestivalLiveBoardCards.swift:89-93`, `FF:997-1002`). 이 문서는 iOS 코드를 따른다.

## 수용 기준

1. 보드 크루 카드의 존 칩을 누르면 존 지도가 오른쪽에서 push되고(`FestivalTransitions.push`) 그 존이 선택된 채 열린다. 탭 바는 보인다. 시스템 뒤로 = 보드로 pop.
2. stage `fst_00e0f4f1d8065a93`(팩 받은 상태): 지도 비율 `mapAspect 0.655`(높이 = 너비 / 0.655), 무대 머리글 2개(LIVET STAGE · WONDER STAGE), 존 10개가 `order` 순서로 그려지고 "TENT 앞"에 표지 "TENT 무대", "정문"에 "GATE"가 보인다. 공연 중이면 머리글에 "지금 {아티스트}" 태그.
3. 존을 누르면 존 카드가 아래에 뜨고 같은 존을 다시 누르거나 지도 빈 곳을 누르면 닫힌다. 고른 존이 카드에 가리면 지도가 올라간다.
4. 팩이 없는 축제의 존 지도는 "이 페스티벌 팩을 받으면 존 지도를 볼 수 있어요" + "다시 시도". 서버 `mapVersion`이 받은 팩과 다르면 위에 "지도가 바뀌었어요 · 팩을 업데이트해 주세요" 배너, 누르면 팩을 다시 받고 지도를 다시 읽는다.
5. 로그인 사용자에게 보드 종 아이콘에 안 읽은 수 배지, 24시간 안의 최신 안 읽은 알림 배너가 보인다. 종 · 배너를 누르면 알림함 push, 탭 바 숨김.
6. 알림함: 첫 쪽 로딩 스켈레톤 → 오늘/어제/날짜 구역 목록. 맨 끝 행이 보이면 다음 쪽. 휴지통 → 선택 모드("N개 선택", 아래 "전체 선택" · "삭제 (N)"), 삭제 후 배지 갱신. `festivals/{id}/live` 경로 알림을 누르면 보드로 돌아가 그 축제로 바뀌고 읽음 처리된다. 다른 경로는 "아직 열 수 없는 알림이에요" 팝업.
7. 계정이 바뀌면(로그아웃 · 다른 계정) 존 지도 · 알림함 · 준비 안내 화면이 스택에서 빠진다(iOS `FCo:29-37`).
8. 단위 테스트(JVM): `FestivalZoneMapFeatureTests`(6) · `FestivalZoneMapUseCaseTests`(3) · `NotificationInboxFeatureTests`(11) · `FestivalNotificationBadgeTests`(5) · `UserNotificationDomainTests`(3) · `UserNotificationRepositoryTests`(4)를 같은 이름으로 옮긴다. `FestivalPrepGuideTests`(6)는 PrepGuide PR에서. 실서버를 부르지 않는다(Ktor `MockEngine`).
9. `.claude/scripts/verify --all` · PR CI · `assembleStageDebug` 통과. stage 수동 확인 스크린샷은 Z3 · N4 PR에 기록. 알림함 실제 목록은 로그인이 필요해 stage에서 아직 못 봤다(아래 "확인 못 한 것").

## 모듈과 책임

| 모듈                            | iOS 대응                                                                                               | 내용                                                                                                                                                                                                                                                                                                                                                                                 |
| ------------------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `feature/festival/interface`    | `Feature/Festival/Interface`                                                                           | `FestivalRoute.ZoneMap(festivalId: String, zoneId: String?)` · `FestivalRoute.NotificationInbox` · (PrepGuide PR) `FestivalRoute.PrepFlags(festivalId)`                                                                                                                                                                                                                              |
| `feature/festival/sources`      | `ZM/**` · `NI/**` · `PG/**` · `FCo` 해당 부분                                                          | `zonemap/`(`ZoneMapViewModel` · `ZoneMapContract` · `ZoneMapScreen`/`Content` · `ZoneMapModel`(순수 Kotlin) · `ZoneMapCanvas` · `ZoneMapCard` · `ZoneMapList` · `ZoneMapSkeleton` · `ZoneMapLegend`), `inbox/`(`NotificationInboxViewModel` · Contract · Screen · `NotificationInboxPresentation`), 보드 배지 · 배너, `FestivalCoordinator` push/pop · 계정 변경 정리 · 탭 전환 알림 |
| `domain/festival/interface`     | `FD/Interface/Sources/{Entity/FestivalZoneMap,ZoneMap/*}`                                              | `FestivalZoneMap`(Zone · Area · StageHeader) · `FestivalZoneMapUseCase`(`cachedMap(festivalId, now)` · `isMapChanged(map)`), `FestivalPackUseCase.syncPack(festivalId)`(결정 E5)                                                                                                                                                                                                     |
| `domain/festival/sources`       | `FD/Sources/{ZoneMap,Repository/FestivalZoneMapRepositoryImpl,Model/Mapper/FestivalZoneMapDTO+}`       | 매퍼(존 `order` 정렬 · 비율 ≤ 0 버림 · 머리글은 무대 이름 있는 것만 · 지금 공연), UseCase · Repository 구현                                                                                                                                                                                                                                                                          |
| `domain/usernotification`(신규) | `UND`                                                                                                  | interface: `UserNotificationItem` · `UserNotificationPage` · `UserNotificationError` · `UserNotificationUseCase`(notifications · unreadCount · markRead · delete) + 공유 상태(결정 E7). sources: Repository(세션 필수 · `SUCCESS` 확인 · 100개씩 나눠 보내기) · 매퍼 · `di/`                                                                                                         |
| `service/api`                   | `Service/API/Sources/{Festival/FestivalAPI,UserNotification/UserNotificationAPI}` · `APIEndpoint` 요청 | 경로 `api/v1/festivals/{id}/pack/version` · `api/v1/me/notifications{,/unread-count,/read,/delete}`, DTO(`FestivalPackVersionDto` · `UserNotificationListDto` · `UserNotificationCountDto` · `UserNotificationIdsBody`), 팩 문서 DTO에 지도 필드 추가(결정 E4)                                                                                                                       |
| `app`                           | `MTC`                                                                                                  | 라이브 흐름이 요청하면 깃발 탭으로 전환(결정 E6), Koin 목록에 `domain/usernotification` 추가                                                                                                                                                                                                                                                                                         |
| `ui/designkit`                  | `UI/FestiCoreDesignKit`                                                                                | 새 컴포넌트 없음. 아래 "사용 컴포넌트"는 모두 존재 확인                                                                                                                                                                                                                                                                                                                              |

`architecture.md` 대응표에 `domain/usernotification`은 이미 이름이 있다(`Domain/{…,UserNotification}Domain` 행). 모듈은 N1 PR에서 만든다.

**사용하는 designkit 컴포넌트(시그니처 확인)**: `FCTopBar(style, title, backgroundColor, trailingIcon, trailingAccessibilityLabel, badgeCount, onBack, onTrailingAction)` · `FCOfflineBadge` · `FCBanner(text, icon = ic_triangle_exclamation_16, showsChevron, onClick)` · `FCTag(tone = FCTagTone.Inverse)` · `FCAvatar(tint, size)`(`FCAvatarTint` Blue · Pink · Orange · Green · Purple · Yellow 있음) · `FCActiveFlagRow` · `FCCrewRow(avatarTint, isMe, isStale)` · `FCListRow(value)` · `FCDivider` · `FCButton(style Secondary/Ghost/Accent, size Medium, isEnabled, isFullWidth)` · `FCEmptyCard(icon)` · `FCSkeletonView` · `FCNotificationRow(icon, isEditing, isSelected)` · `FCDialog` · `FCStepHeader(step, onLater)` · `FCChip` · `FCFlagCard` · `FCThumbnail`. 아이콘 `ic_triangle_exclamation_16` · `ic_circle_info_16` · `ic_location_dot_20` · `ic_flag_16` · `ic_xmark_16` · `ic_trash_can_20` · `ic_bell_16` · `ic_users_24` · `ic_music_24` · `nav_timetable_inactive` 모두 `ui/designkit/src/main/res/drawable`에 있다. 토큰 `accentBackgroundLime` · `staticLime` · `staticInk` · `staticWhite` · `staticBlack` · `staticalphaBlack20` · `inverseLabel` · `inverseBackground` · `accentPointBlue` · `lineStrong` · `lineNormal` · `fillNormal` · `materialGlass` · `primaryOnPrimary`, 글꼴 `baseCaption2Bold` · `baseFootnoteBold` · `baseHeadlineBold` · `baseTitle2Bold` · `baseSubheadlineRegular` · `baseCaption1Medium`, 반경 `r4 r8 r12 r16 r20` 모두 생성 토큰에 있다.

---

## 화면 1: ZoneMap (S06)

### 지도 렌더링 방식

iOS 존 지도는 **이미지 지도도 MapKit도 아니다.** 팩 문서의 0~1 비율 사각형(존 `area` · 무대 머리글 `mapStages`)을 화면 너비에 맞춰 SwiftUI 도형으로 그린다(`ZM/Components/FestivalZoneMapCanvas.swift:12-67`, 높이 = 너비 / `aspect` `:78-80`). 확대 · 이동 제스처는 없고 세로 `ScrollView`만 있다(`ZM/View/FestivalZoneMapView.swift:80-96`). `ZM` 파일의 import는 `FestiCoreDesignKit` · `FestivalDomainInterface` · `SwiftUI` · `ComposableArchitecture` · `Foundation`뿐이다. 엔티티에 `imageURL`(`FD/Interface/Sources/Entity/FestivalZoneMap.swift:15`)이 있지만 View에서 쓰지 않고, stage 응답도 `mapImageUrl: null`이다.

Android: `BoxWithConstraints`(너비) + `Column(Modifier.verticalScroll)` 안에 지도 `Box(size = width × width / aspect)`, 존 · 머리글은 `Modifier.offset(x * width, y * height).size(w * width, h * height)`. 지도 SDK · `transformable`은 쓰지 않는다(결정 E1 · E2).

### 화면 구성 (`ZM/View/FestivalZoneMapView.swift`)

| 요소               | iOS 값                                                                                                                                                                                                                            | 근거                                                      |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| 상단 바            | `FCTopBar(.back, title: "존 지도")`, 뒤로 = `backTapped`                                                                                                                                                                          | `:25-29`                                                  |
| 오프라인 배지      | 라이브 조회 실패 시 `FCOfflineBadge(offlineText(receivedAt, now))`(Android `LBP:159` 재사용)                                                                                                                                      | `:30-35`                                                  |
| 배경               | `backgroundNormal`                                                                                                                                                                                                                | `:42`                                                     |
| 상태 분기          | 로딩 → 스켈레톤, 팩 없음 · 실패 → 안내, 지도 있음(`hasLayout` = aspect 있음 ∧ 영역 있는 존 ≥ 1, `FD/…/FestivalZoneMap.swift:42-44`) → 지도, 아니면 존 목록                                                                        | `:50-73`                                                  |
| 지도 세로 여백     | 16                                                                                                                                                                                                                                | `:91-94 · 232`                                            |
| 아래 겹침          | 고른 존 카드 > 안내 띠 > 범례, 바깥 여백 `paddingP16`                                                                                                                                                                             | `:98-107 · 146-179`                                       |
| 지도 바뀜 배너     | 위에 `FCBanner("지도가 바뀌었어요 · 팩을 업데이트해 주세요", icon triangleExclamation16)`, 바깥 `paddingP16`, 누르면 `updatePackTapped`                                                                                           | `:124-130`                                                |
| 안내 띠            | 팔로우 깃발 · 크루 둘 다 없으면 `FCBanner("깃발을 팔로우하면 지도에 크게 보여요")` → `followGuideTapped`; 팩 `flagsEnabled == false`면 `FCBanner("지금은 깃발 위치를 볼 수 없어요 · 크루만 보여요", showsChevron = false)` 무동작 | `:158-167`, `ZM/Model/FestivalZoneMapModel.swift:158-162` |
| 범례               | 팔로우 깃발 있을 때 "팔로우 깃발", 크루 있을 때 "크루", 깃발 켜짐이면 "다른 깃발"                                                                                                                                                 | `:169-177`                                                |
| 존 목록(지도 없음) | `circleInfo16`(16) + "이 페스티벌은 지도가 없어서 존 목록으로 보여 드려요."(`baseFootnoteRegular`, `labelAssistive`, 간격 `marginM6`) 아래 `FestivalZoneMapList`, 세로 간격 · 바깥 `paddingP16`                                   | `:181-216 · 233`                                          |
| 사용 불가          | 팩 없음 "이 페스티벌 팩을 받으면 존 지도를 볼 수 있어요" / 실패 "존 지도를 불러오지 못했어요"(`baseBodyMedium`, `labelNeutral`, 가운데) + `FCButton("다시 시도", secondary, medium)`, 간격 `paddingP12`, 바깥 `paddingP16`        | `:218-229`                                                |
| 고른 존 보이기     | 처음 · 선택 바뀔 때 `scrollTo(revealID, anchor: .bottom)`, 바뀔 때만 애니메이션. 기준 높이 = (area.y + area.h) × height + 34                                                                                                      | `:108-121 · 134-143`, Canvas `:52-58 · 84`                |

### 지도 본문 (`ZM/Components/FestivalZoneMapCanvas.swift`)

- 빈 곳 탭 = `mapBackgroundTapped`(`:23-25`). 머리글 → 존 순서로 쌓는다(`:26-60`).
- **무대 머리글**(`:89-134`): 이름 대문자 `baseCaption2Bold` `labelAssistive` 1줄, 좁으면 0.85배까지 축소(Android: `autoSize` 또는 `TextMeasurer`로 축소, PR에서 Compose API 확인), 오른쪽에 공연 중이면 `FCTag("지금 {아티스트}", tone = Inverse)`(자르지 않음), 간격 `marginM8`, 왼쪽 `paddingP12` · 오른쪽 10, 높이 34, 배경 `fillNormal` `r8`.
- **존 영역**(`:137-309`): 반경 10, 선택 시 채움 `accentBackgroundLime` + 테두리 `staticLime` 1.5, 기본 채움 `backgroundElevated`. 머리줄 안쪽 4, 이름표 `baseFootnoteBold` 1줄 자르지 않음 · 좌우 `paddingP8` · 위아래 3 · 반경 6, 선택 시 `staticInk` 글자 + `staticLime` 바탕 + `staticInk` 테두리 1, 기본 `labelNormal` 글자 + `fillNormal` 바탕. 표지(`landmark`) `baseCaption2Bold` `labelAssistive` 좌우 `paddingP8` 높이 20 캡슐 테두리 `lineStrong` 1. 영역 높이 < 72면 마커를 이름표 줄 끝에(compact), 아니면 아래 줄(왼쪽 12, 마커 간격 12). 마커 총너비(팔로우 깃발 52, 그 밖 40, 간격 12)가 (영역 너비 − 24)를 넘으면 `+N` 묶음. 접근성: 자식 합치기 + 버튼, 선택이면 선택됨(`:175-176`) → Android `Modifier.semantics(mergeDescendants = true) { role = Button; selected = isSelected }`.
- **마커**(`:312-483`): 칸 40. 팔로우 깃발 = 지름 36 `staticLime` 원 + `staticWhite` 테두리 2 + `ic_flag_16`(16, `staticInk`); 다른 깃발 = 지름 24 `labelAssistive` 원 + `backgroundNormal` 테두리 2 + 깃발 12(`backgroundNormal`); 크루 = `FCAvatar(첫 글자, tint, small)` + 흰 2 패딩 · 테두리. 그림자 `staticalphaBlack20` iOS radius 3 · y 2(Figma blur 6). 팔로우 깃발 · 크루는 아래 8 간격으로 이름표(`baseCaption2Bold`, 깃발 `primaryNormal` · 크루 `labelNormal`, 높이 22, 좌우 6, 배경 `backgroundElevated` `r4`, 그림자 radius 2 · y 2). 오래됨(30분 초과) 투명도 0.4. 접근성 라벨 = 이름.
- **묶음**(`:486-527`): "+N" `baseCaption1Bold` `inverseLabel`, 지름 28 `inverseBackground` + 테두리 2 `backgroundElevated`, 칸 높이 40, 접근성 "N개".
- **범례**(`:530-611`): 항목 간격 `marginM12`, 점 8 + 글자 `baseCaption1Regular` `labelNeutral` 간격 `marginM4`; 팔로우 깃발 `staticLime` + `staticInk` 테두리 1, 크루 `accentPointBlue`, 다른 깃발 `labelAssistive`. 높이 28, 좌우 10, 캡슐 `backgroundElevated`, 그림자 radius 4 · y 2.
- 그림자: Android는 designkit 선례대로 `Modifier.dropShadow(shape, Shadow(radius, color, offset, alpha))`(`ui/designkit/.../action/FCSortMenu.kt:66-74`). iOS 주석상 SwiftUI radius = Figma blur ÷ 2라서 Compose `Shadow.radius`에 어느 값을 넣을지는 `FCSortMenu` · `FCSegmented`가 쓴 변환과 맞춘다(Z2에서 확인).

### 존 카드 · 목록 · 스켈레톤

- **카드**(`ZM/Components/FestivalZoneMapCard.swift:12-150`): 머리줄 = `ic_location_dot_20`(20, `labelNormal`)을 지름 36 `fillNormal` 원에, 오른쪽 존 이름 `baseHeadlineBold` `labelNormal` + 메타 `baseFootnoteRegular` `labelAssistive`(간격 2, 1줄), 간격 `marginM8`. 머리줄 아래 4 띄우고 행 간격 4. 비었으면 정보 줄 "지금 이 존에 있는 깃발·크루가 없어요.", 아니면 깃발 행 `FCActiveFlagRow(name, meta, time, status) {}`(무동작) → 크루 행 `FCCrewRow(avatarTint, isMe, isStale)`, 오래된 크루가 있으면 "30분 넘게 소식이 없어서 흐리게 보여요.". 정보 줄 = `ic_circle_info_16` + `baseFootnoteRegular` `labelAssistive` 간격 `marginM6`, 뒤에 8. 안쪽 위 `paddingP16` · 좌우 `paddingP16` · 아래 8, 배경 `backgroundElevated` `r20`, 그림자 `staticBlack` 0.12 · radius 2 · y 1(배경에만).
- **카드 값**(`ZM/Model/FestivalZoneMapModel.swift:173-231`): 깃발은 팔로우 → 나머지 순, 메타 = ["팔로우 중", 활동 태그(`LBP:381` `tagText`)] " · " 연결, 시각 = `relativeTime`(`LBP:146`), 상태 `resting`이면 휴식 칩 아니면 활동. 크루 상태 = `crewStateText`(`LBP:372`), `나` = 로그인 사용자 ID 일치. 메타 = [존 `kind`] + ("깃발 N" · "크루 N", 둘 다 없으면 "지금 아무도 없어요").
- **목록**(`:153-179`): 행 사이 `FCDivider`(좌우 `paddingP16`), `FCListRow(존 이름, value = countText)`, 배경 `backgroundElevated` `r16`. 값 = "깃발 N · 크루 N", 없으면 "–"(`ZM/Model/FestivalZoneMapModel.swift:165-171`). 행 탭 = `zoneTapped`(선택 토글만 하고 목록 화면엔 카드가 없다 — iOS 그대로).
- **스켈레톤**(`ZM/View/FestivalZoneMapSkeletonView.swift:12-44`): 간격 `marginM12`, 머리 26 높이 `r8` 2개, 행 높이 98 · 94 · 58 · 160에 반경 10 칸 2개씩, 바깥 `paddingP16`, 접근성 "존 지도를 불러오는 중".

### 마커 배치 규칙 (`ZM/Model/FestivalZoneMapModel.swift:89-156 · 234-250`)

- 깃발은 팩 `flagsEnabled`일 때만, 상태 `active` · `resting`이고 지도에 있는 존에 있는 것만.
- 존마다 순서: 팔로우 깃발 → 크루 → 다른 깃발. 크루 색은 크루 목록 순서로 `[Blue, Pink, Orange, Green, Purple, Yellow]` 순환(`:23`), 첫 글자 = 닉네임 첫 글자.
- 오래됨 = `now − updatedAt > 30분`(`:22`), `updatedAt` 없으면 오래되지 않음.

### 상태 (`ZoneMapContract.kt`, iOS `ZM/Reducer/FestivalZoneMapFeature.swift:18-61`)

| Android                                                                          | iOS           |
| -------------------------------------------------------------------------------- | ------------- |
| `festivalId` · `viewerUserId`(`CurrentUserProvider`) · `selectedZoneId`          | `:19-22`      |
| `map: ZoneMapLoad`(`Loading` · `Loaded(FestivalZoneMap)` · `Missing` · `Failed`) | `:47-53`      |
| `snapshot: FestivalLiveBoardSnapshot?` · `receivedAt: Instant?` · `isOffline`    | `Live :56-61` |
| `isMapChanged` · `isUpdatingPack`                                                | `:26-27`      |

### 동작 (`ZoneMapViewModel`)

| 함수                      | iOS                            | 규칙                                                                                                                                                                                                                                            |
| ------------------------- | ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `onAppear()`              | `:172-176`                     | 지도 읽기 + 라이브 감시 시작                                                                                                                                                                                                                    |
| `onBackTapped()`          | `:178-179`                     | `BackRequested`                                                                                                                                                                                                                                 |
| `onZoneTapped(id)`        | `:181-183`                     | 같은 존이면 해제, 아니면 선택                                                                                                                                                                                                                   |
| `onMapBackgroundTapped()` | `:185-187`                     | 해제                                                                                                                                                                                                                                            |
| `onUpdatePackTapped()`    | `:189-192 · 263-276 · 314-321` | 진행 중이면 무시. 성공 → `isMapChanged = false` + 지도 다시 읽기, 실패 → 진행만 끝(문구 없음)                                                                                                                                                   |
| `onFollowGuideTapped()`   | `:194-195`, `FCo:201-206`      | `FlagDirectoryRequested` → Coordinator pop + 깃발 탭 요청                                                                                                                                                                                       |
| `onRetryTapped()`         | `:197-199`                     | `Loading`으로 두고 다시 읽기                                                                                                                                                                                                                    |
| 지도 읽기                 | `:208-225 · 285-298`           | `cachedMap(id, now)`: null → `Missing`, 예외 → `Failed`. 읽힌 지도에 선택 존이 없으면 해제, 이어서 지도 버전 확인                                                                                                                               |
| 라이브 감시               | `:227-251 · 300-308`           | `liveBoard(id)` 즉시 조회 → 35초 대기 반복(`:129`). `RateLimited(retryAfter)` = 대기 `max(35, retryAfter)` · 상태 유지, 그 밖 실패 = `isOffline = true`(마지막 스냅샷 유지). 성공 = 스냅샷 · `receivedAt = snapshot.receivedAt ?: now` · 온라인 |
| 지도 버전 확인            | `:253-261 · 310-312`           | `isMapChanged(map)`, 실패는 `false`                                                                                                                                                                                                             |

감시는 보드처럼 화면이 보이고 포그라운드일 때만 돈다(보드 설계 수용 기준 5와 같은 수명 주기 규칙, iOS는 `cancellable`로 화면 해제 시 취소). 30초 시각 갱신은 iOS에 없다(`date.now`를 렌더 때 읽음, `FestivalZoneMapView.swift:56-61`). Android는 상태가 바뀔 때만 다시 계산되므로 오래됨 · 상대 시각이 35초 감시 주기에 따라 갱신된다(iOS와 사실상 같은 주기).

### 데이터

- `FestivalZoneMapUseCase.cachedMap(festivalId, now)`: 받은 팩(원본 바이트, `service/pack/.../PackSyncService.kt:9`)에서 지도 필드를 읽는다. iOS는 팩 원본을 지도 전용 DTO로 따로 해석한다(`FD/Sources/ZoneMap/FestivalZoneMapUseCaseImpl.swift:20-33`, `FD/Sources/Model/DTO/FestivalZoneMapDTO.swift:11-117`). 필드: `mapImageUrl` · `mapAspect` · `mapVersion` · `flagsEnabled`(없으면 true) · `zones[]{id,name,kind,order,area{x,y,w,h},landmark}` · `mapStages[]{stageId,x,y,w}` · `stages[]{id,name}` · `sets[]{stageId,artist,startsAt,endsAt}`, 배열은 원소 단위로 손상 원소만 버린다.
- 매퍼(`FD/Sources/Model/Mapper/FestivalZoneMapDTO+.swift:13-88`): 존 `order` 오름차순(없으면 맨 뒤, 같으면 원래 순서), `aspect ≤ 0`이면 null, 머리글은 `stages`에 이름이 있는 것만, 지금 공연 = `startsAt ≤ now < endsAt`인 첫 공연의 `artist`(ISO-8601).
- `isMapChanged(map)` = `GET api/v1/festivals/{id}/pack/version`의 `data.mapVersion != map.version`(`FD/Sources/Repository/FestivalZoneMapRepositoryImpl.swift:19-26`, `code != "SUCCESS"`면 오류). 인증 없음 · 재시도 1회(`iOS/Service/APIEndpoint/Sources/Festival/FestivalPackRequest.swift:16-27`).
- 팩 업데이트 = iOS `FestivalPackDetailUseCaseImpl.updatePack`(`FD/Sources/PackDetail/FestivalPackDetailUseCaseImpl.swift:35-57`): `packService.sync` → `updated`/`notModified`가 아니면 실패 → 목록 갱신 → 저장 팩 다시 읽기(결정 E5).

---

## 화면 2: NotificationInbox

### 푸시와의 경계

알림함 · 배지 · 배너는 **서버 조회(pull)만으로 동작하고 푸시(FCM/APNs)에 의존하지 않는다.**

- 배지 · 배너는 보드가 보일 때(`FF:904`)와 세션이 바뀔 때(`FF:1181-1184`) `unread-count`와 첫 쪽을 조회한다(`NI/Reducer/FestivalFeature+NotificationUnreadCount.swift:22-54`).
- iOS 푸시 수신 경로는 무음 푸시 → `remoteContentChanged`(`iOS/App/Sources/Application/FestiCoreApp.swift:71-77` → `FCo:624-632` → `FF:745-763`)이고 이것은 카탈로그 · 보드 · 홈 상세만 다시 읽는다. 배지를 다시 읽지 않는다.
- 알림 행의 목적지는 서버 `route` 문자열(`festivals/{id}/live`)이고 딥링크 경로 enum을 쓰지 않는다(`NI/Reducer/NotificationInboxFeature.swift:246-252`).

따라서 이 문서는 FCM · 알림 채널 · 무음 푸시 동기화를 다루지 않는다(푸시 문서, `ios-parity.md` "옮기지 않는 것"과 같이 Live Activity도 제외). 서버가 기기 등록 없이도 알림함 항목을 만드는지는 확인하지 못했다.

### 화면 구성 (`NI/View/NotificationInboxView.swift`)

| 요소          | iOS 값                                                                                                                                                                                                                                                          | 근거               |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| 배경          | `backgroundElevated`, 시스템 내비 바 숨김                                                                                                                                                                                                                       | `:23-25`           |
| 상단 바(일반) | `FCTopBar(.back, title: "알림", backgroundColor: backgroundNormal)`. 목록이 있으면 오른쪽 `trashCan20` 접근성 "알림 선택 삭제" → `editTapped`                                                                                                                   | `:51-68`           |
| 상단 바(선택) | 제목 "{N}개 선택", 오른쪽 `xmark16` 접근성 "선택 취소" → `cancelSelectionTapped`, 뒤로는 그대로 `backTapped`                                                                                                                                                    | `:39-49`           |
| 시각 갱신     | 60초마다 다시 그림(`TimelineView(.periodic(by: 60))`)                                                                                                                                                                                                           | `:19-21`           |
| 로딩          | 5행: 원 40 + (높이 18 `r4` / 180×14 `r4`, 간격 `paddingP8`), 행 간격 `paddingP12` · 안쪽 `paddingP16`, 접근성 하나로 "알림을 불러오는 중"                                                                                                                       | `:140-156`         |
| 실패          | `FCEmptyCard(icon bell16, "알림을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.")` + `FCButton("다시 시도", secondary, medium)`                                                                                                                      | `:78 · 158-173`    |
| 빈 목록       | `FCEmptyCard(bell16, "새로운 알림이 없어요", "새 소식이 오면 이곳에서 확인할 수 있어요.")`, 간격 `paddingP12`, 바깥 `paddingP16`, 가운데                                                                                                                        | `:79-80 · 158-173` |
| 구역 머리     | `baseCaption1Bold` `labelNeutral`, 좌우 `paddingP16` · 위 `paddingP20` · 아래 `paddingP8`, 머리글 의미                                                                                                                                                          | `:89-98`           |
| 행            | `FCNotificationRow(title, message = body, time, isRead, icon, isEditing, isSelected)`. 읽음 = 서버 `readAt` ∨ 이번 화면에서 읽음 처리                                                                                                                           | `:99-111`          |
| 행 접근성     | 라벨 "{제목}, {본문}, {시각}", 값 "읽음"/"안 읽음", 선택 모드에서 선택됨, 힌트 "선택 상태를 바꿉니다"/"알림을 엽니다"                                                                                                                                           | `:112-117`         |
| 다음 쪽       | 마지막 행이 나타나면 `loadMoreTapped`. 불러오는 중 `ProgressView`(`labelNeutral`, `paddingP16`, "알림을 더 불러오는 중"), 아니고 커서 있고 선택 모드 아니면 `FCButton("알림 더 보기", ghost, medium)` `paddingP16`                                              | `:118-133`         |
| 선택 바       | 선택 모드일 때 아래 고정: `FCButton(전체 선택됨 ? "선택 해제" : "전체 선택", secondary, 가득)` + `FCButton("삭제 ({N})", accent, 가득, 선택 ≥ 1 ∧ 삭제 중 아님)`, 간격 `paddingP8`, 좌우 · 위아래 `paddingP16`, 위 1 `lineNormal` 선, 배경 `backgroundElevated` | `:26-28 · 175-194` |
| 애니메이션    | 선택 모드 · 선택 집합 변화 `easeInOut 0.2s`                                                                                                                                                                                                                     | `:31-32`           |
| 아래 여백     | `fcScrollBottomInset()` → Android `Modifier.fcScrollBottomInset()`(`LBScr:347`)                                                                                                                                                                                         | `:137`             |

### 표시 규칙 (`NI/Model/NotificationInboxPresentation.swift`)

- 구역(`:13-32`): `createdAt`의 Asia/Seoul 날짜로 묶고 받은 순서 유지. 오늘 "오늘", 어제 "어제", 그 밖 `yyyy-MM-dd`(`AppDateFormat.day`, `iOS/Core/FestiCoreUtility/Sources/Date/AppDateFormat.swift:11 · 18-24 · 30`).
- 시각(`:34-43`): 오늘 = `relativeTime`(`LBP:146`, null이면 "방금"), 어제 = "어제 HH:mm", 그 밖 `MM/dd HH:mm`(en_US_POSIX · 서울).
- 아이콘(`:45-52`): `crew.` → `ic_users_24`, `flag.` → `ic_flag_16`, `festival.` → `ic_music_24`, `pack.` → `nav_timetable_inactive`, 그 밖 `ic_bell_16`.

### 상태 (`NotificationInboxContract.kt`, iOS `NI/Reducer/NotificationInboxFeature.swift:10-33`)

`load: Idle | Loading | Loaded | Failed` · `items` · `nextCursor` · `isLoadingMore` · `isEditing` · `isDeleting` · `selectedIds` · `locallyReadIds` · `attemptedReadIds` · `dialog: InboxDialog?`(`DestinationUnavailable` · `LoadMoreFailed` · `DeleteFailed`). `allLoadedSelected = items 있음 ∧ 선택 수 == items 수`. iOS `instanceID`(`:14`)는 이전 알림함의 늦은 응답을 버리려는 값이다. Android는 화면마다 항목 범위 ViewModel이라 pop 때 작업이 취소되므로 두지 않는다(테스트 `staleInstanceResponsesAreIgnored`는 "pop 뒤 응답은 반영되지 않음"으로 옮긴다).

### 동작 (`NotificationInboxViewModel`, 행 번호는 `NotificationInboxFeature.swift`)

| 함수                        | 규칙                                                                                                                                                                                                                                      | 근거                 |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------- |
| `onAppear()`                | `Idle`일 때만 `Loading` + 첫 쪽                                                                                                                                                                                                           | `:86-89`             |
| `onRetryTapped()`           | `Failed`일 때만                                                                                                                                                                                                                           | `:94-97`             |
| `onEditTapped()`            | `Loaded` ∧ 항목 있음 ∧ 다음 쪽 로딩 아님                                                                                                                                                                                                  | `:99-102`            |
| `onCancelSelectionTapped()` | 선택 모드 끝 · 선택 비움                                                                                                                                                                                                                  | `:104-107`           |
| `onSelectAllTapped()`       | 전체 선택이면 비우고 아니면 전부                                                                                                                                                                                                          | `:109-112`           |
| `onDeleteTapped()`          | `Loaded` ∧ 선택 모드 ∧ 선택 ≥ 1 ∧ 삭제 중 아님 → 목록 순서대로 ID 삭제                                                                                                                                                                    | `:114-120`           |
| `onRowTapped(id)`           | 선택 모드면 토글. 아니면 미읽음이고 아직 시도 안 했으면 로컬 읽음 + 읽음 위임. 경로가 `festivals/{festivalId}/live`(세 조각 · 항목 `festivalId`와 같음)면 열기 위임, 아니면 목적지 없음 팝업. 읽음도 목적지도 없으면 아무것도 보내지 않음 | `:122-136 · 246-252` |
| `onLoadMoreTapped()`        | `Loaded` ∧ 선택 모드 아님 ∧ 커서 있음 ∧ 로딩 아님                                                                                                                                                                                         | `:138-142`           |
| 첫 쪽 응답                  | 항목 교체 · 로컬 읽음 비움. 다음 쪽 응답은 이미 있는 ID 제외하고 붙임. 선택은 남은 ID로 줄임, 비면 선택 모드 끝. 다음 커서가 요청 커서와 같으면 null                                                                                      | `:179-193`           |
| 실패                        | 첫 쪽 → `Failed`, 다음 쪽 → 팝업 "알림을 더 불러오지 못했어요"                                                                                                                                                                            | `:195-199`           |
| 삭제 성공                   | 지운 항목 제거 · 선택 모드 끝. 다 지웠는데 커서가 있으면 첫 쪽 다시 + 배지 갱신, 아니면 배지 갱신만                                                                                                                                       | `:201-215`           |
| 삭제 실패                   | 선택 모드 끝 · `Loading` · 커서 비움 · 팝업 "알림을 삭제하지 못했어요" · 첫 쪽 다시 + 배지 갱신                                                                                                                                           | `:217-225`           |

팝업 문구(`:255-267`, 버튼 "닫기"): "아직 열 수 없는 알림이에요" / "해당 화면을 준비 중입니다.", "알림을 더 불러오지 못했어요" / "잠시 후 다시 시도해 주세요.", "알림을 삭제하지 못했어요" / "연결을 확인하고 다시 시도해 주세요." → `FCDialog`.

### 보드 배지 · 배너 (보드 쪽 변경)

- 배지: `FCTopBar(badgeCount = unreadCount)`(`LBS:220-235`). 지금 `LBScr:331`의 0을 바꾼다.
- 조회(`NI/Reducer/FestivalFeature+NotificationUnreadCount.swift:22-54`): 로그인일 때만. `unreadCount` → 배지, 0보다 크면 첫 쪽에서 `readAt == null ∧ now − createdAt < 24시간`(미래 시각 포함) 중 가장 최근 하나를 배너로. 실패는 마지막 값 유지. 계정 세대가 바뀐 응답은 버린다(`FF:1800-1818`). 0이면 배너 제거.
- 배너(`LBS:75-88 · 237-291`): 상단 바 · 오프라인 배지 아래, 좌우 `paddingP16` · 위아래 `paddingP8`. 아이콘(16, `primaryOnPrimary`)을 40×40 `primaryNormal` `r12` 칸에, 제목 `baseCalloutBold` `labelNormal` + 오른쪽 시각 `baseCaption1Regular` `labelAssistive`, 본문 `baseFootnoteRegular` `labelNeutral` 2줄, 간격 `paddingP12`/`paddingP4`, 안쪽 `paddingP16`, 배경 `backgroundElevated` `r20`. 탭 = 종과 같음, 힌트 "알림함으로 이동합니다".
- 읽음 요청(`:5-16`): 보드가 소유해 알림함이 닫혀도 끝까지 보내고, 성공 · 실패와 관계없이 배지를 다시 조회한다(테스트 `failedReadRefreshesUnreadCount`). Android는 결정 E7.
- 로그아웃 · 계정 변경: 배지 0 · 배너 제거 · 조회 취소(`FF:334-335 · 1147-1148 · 1166-1167`).

---

## 화면 3: PrepGuide (S15)

### 흐름 (`FCo:281-412`)

1. 트리거(`FF:1687-1690`): 팩 받기 결과가 `updated`/`notModified`이고 **그 축제 팩을 처음 받았고**(`cachedPacks[id] == nil`) 로그인 상태면 `prepGuideRequested(id)`. 목록 · 보드 둘 다 보낸다(`FCo:281-288`). 이미 안내가 열려 있으면 무시(`:289`).
2. 서버 내 일정 조회(`:290-303`) → 실패하거나 팩 일정(`FestivalPackTimetableMapper.days`)이 비면 1단계를 건너뛰고 2단계로(`:321-328`), 아니면 1단계 `prepSchedule(SchedulePickFeature)` push(`:329-333`).
3. 1단계 저장 → 2단계 push + 상위에 저장 알림(`:336-350`), 건너뛰기 → 2단계(`:352-361`).
4. 2단계 `prepFlags` 끝(다음 · 건너뛰기) → 3단계 `prepChecklist(festivalID, title = 팩 이름)` push(`:363-379`).
5. 어느 단계든 "나중에", 3단계 끝 → 세 단계 모두 스택에서 제거(`:381-400`).
6. 2단계 로그인 필요 → `followLoginRequested`, 약관 동의 필요 → `termsAcceptanceRequested`(`:402-412`).
7. 2단계 존 이름 = 팩 `zones`의 `id → name`(첫 값 우선, `:685-701`).

### 2단계 화면 (`PG/View/FestivalPrepFlagsView.swift`)

| 요소        | iOS 값                                                                                                                                                                                                                                                                                                                    | 근거                 |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------- |
| 상단        | `FCStepHeader(step: 2)` + "나중에"(`laterTapped`)                                                                                                                                                                                                                                                                         | `:25-26`             |
| 머리        | "Step 2. 깃발 팔로우 하기"(`baseCaption1Medium` `labelAssistive`) / "만나보고 싶은 깃발이 있나요?"(`baseTitle2Bold` `labelNormal`, 머리글) / "팔로우 하면 깃발이 움직일 때 조용히 알려 드려요.\n없어도 공연은 그대로 즐길 수 있어요."(`baseSubheadlineRegular` `labelNeutral`), 간격 `paddingP8`                          | `:53-72`             |
| 칩          | 가로 스크롤 "전체" + `FestivalFlagDirectory.Tag` 전부, 화면 끝까지(좌우 `paddingP16` 상쇄), 간격 `paddingP8`                                                                                                                                                                                                              | `:75-98`             |
| 목록        | 오른쪽 "{N}개 팔로우"(`baseFootnoteBold`, 칩과 무관한 전체 팔로우 수 `PG/Reducer/FestivalPrepFlagsFeature.swift:31-39`), 카드 `FCFlagCard(directory context)` 간격 `paddingP12`; 비면 "아직 깃발이 없어요"/"깃발이 생기면 깃발 탭에서 팔로우할 수 있어요." 또는 "이 성향의 깃발이 아직 없어요"/"다른 성향을 골라 보세요." | `:101-126 · 139-167` |
| 로딩 · 실패 | 스켈레톤 높이 136 `r16` "깃발을 불러오는 중" / "깃발을 불러오지 못했어요"·"건너뛰고 나중에 깃발 탭에서 팔로우할 수 있어요."                                                                                                                                                                                               | `:127-136 · 202`     |
| 아래 바     | `FCButton("건너뛰기", ghost)` + `FCButton(N > 0 ? "다음 · {N}개 팔로우" : "다음", 가득)`, 간격 `paddingP8`, 좌우 `paddingP16` · 위 `paddingP12` · 아래 `paddingP8`, 위 1 `lineNormal`, 배경 `ultraThinMaterial` + `materialGlass`                                                                                         | `:169-199`           |
| 배경        | `backgroundElevated`                                                                                                                                                                                                                                                                                                      | `:46`                |

Android 배경 흐림: Compose에 `ultraThinMaterial` 대응이 없다. `materialGlass` 토큰 단색(+ 필요 시 반투명)으로 두고 차이를 PR에 적는다.

### PrepGuide 선행 조건

2단계 Reducer는 깃발 탭 Reducer를 그대로 품는다(`Scope(state: \.flag) { FestivalFlagFeature() }`, `PG/Reducer/FestivalPrepFlagsFeature.swift:79-84`). 필요한 아직 없는 Android 부분:

| 단계 | iOS 본체                                                                                                        | 의존                                                                                                                                                  | Android                                                                      |
| ---- | --------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 1    | `iOS/Feature/Timetable/Sources/SchedulePick/*`(216 + 164줄)                                                     | `festivalScheduleUseCase.saveMySchedule`(`SchedulePickFeature.swift:179`)                                                                             | `feature/timetable` 없음, 저장 API 없음(읽기만 `FestivalApiPath.mySchedule`) |
| 2    | `iOS/Feature/Festival/Sources/Flag/Reducer/FestivalFlagFeature.swift`(279) · `FestivalFlagDirectory.swift`(507) | `festivalLiveBoardService.refresh`(`FestivalFlagFeature.swift:218`), `festivalFlagFollowService.setFollowing`(`FestivalFlagDirectory.swift:16 · 221`) | 깃발 탭 · 팔로우 API 없음(`MTS:54-61` 자리 화면)                             |
| 3    | `iOS/Feature/Profile/Sources/PrepChecklist/*`(106 + 233)                                                        | 팩 `checklist`(stage 상세에 키 있음)                                                                                                                  | `feature/profile` 없음                                                       |

그래서 PrepGuide 구현은 Flag 문서(디렉터리 · 팔로우) 뒤로 미룬다(결정 E10). 이 문서에서는 트리거 경계만 추가할지 결정한다(E10 추천안).

---

## 오류 · 빈 · 로딩 상태 요약

| 화면      | 로딩              | 빈                                             | 오류                                             | 오프라인                                                              |
| --------- | ----------------- | ---------------------------------------------- | ------------------------------------------------ | --------------------------------------------------------------------- |
| 존 지도   | `ZoneMapSkeleton` | 팩 없음 안내 + 다시 시도 / 지도 없음 → 존 목록 | "존 지도를 불러오지 못했어요" + 다시 시도        | 라이브 실패 시 배지, 마지막 스냅샷 유지. 지도는 저장 팩이라 계속 보임 |
| 알림함    | 5행 스켈레톤      | "새로운 알림이 없어요"                         | 첫 쪽: 빈 카드 + 다시 시도, 다음 쪽 · 삭제: 팝업 | 별도 표시 없음(iOS 같음)                                              |
| 보드 배지 | —                 | 0이면 배지 · 배너 없음                         | 마지막 값 유지                                   | 같음                                                                  |

## 서버 계약

stage `https://festicore-dev.up.railway.app/`에서 인증 없이 GET만 확인했다(2026-11-22).

| 요청                                                     | 인증   | 확인 결과                                                                                                                                                                                                                                                                                                             |
| -------------------------------------------------------- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET api/v1/festivals/fst_00e0f4f1d8065a93/pack/version` | 없음   | 200 `{"code":"SUCCESS","data":{"packVersion":56,"packUpdatedAt":"2026-11-12T15:01:26Z","etag":"\"b5fe…a148\"","mapVersion":"zm-7fc315a33960"}}`. iOS DTO는 `packVersion` · `mapVersion`만 읽는다(`FD/Sources/Model/DTO/FestivalZoneMapDTO.swift:120-128`)                                                             |
| `GET api/v1/festivals/fst_00e0f4f1d8065a93`              | 없음   | 200(35,041B). 키에 `mapAspect 0.655` · `mapImageUrl null` · `mapVersion "zm-7fc315a33960"` · `flagsEnabled true` · `zones` 10개(각 `id,name,kind,x,y,order,area{x,y,w,h},landmark`) · `mapStages` 2개(`stageId,x,y,w`) · `stages` 2개 · `sets` 42개 · `checklist` 있음. 존 `x,y`(중심점으로 보임)는 iOS가 읽지 않는다 |
| `GET api/v1/me/notifications`                            | Bearer | 비로그인 401 `{"code":"UNAUTHORIZED","data":null,"message":"인증 토큰이 없습니다."}`                                                                                                                                                                                                                                  |
| `GET api/v1/me/notifications/unread-count`               | Bearer | 비로그인 401, 같은 본문                                                                                                                                                                                                                                                                                               |
| `POST api/v1/me/notifications/read` · `/delete`          | Bearer | 쓰기 요청이라 호출하지 않음                                                                                                                                                                                                                                                                                           |

iOS 요청 정의(`iOS/Service/API/Sources/UserNotification/UserNotificationAPI.swift:7-15`, `iOS/Service/APIEndpoint/Sources/UserNotification/UserNotificationRequest.swift:12-60`):

- 목록 `GET …/notifications?before={cursor}`(첫 쪽은 쿼리 없음), 응답 `BaseResponse{items[{id,type,festivalId?,title,body,createdAt,readAt?,route?}], nextCursor?}`(`UND/Sources/Model/DTO/UserNotificationResponseDTO.swift:3-24`). 커서는 받은 값 그대로.
- 안 읽은 수 `GET …/unread-count` → `{unreadCount}`, 음수면 오류(`UND/Sources/Repository/UserNotificationRepositoryImpl.swift:23-32`).
- 읽음 · 삭제 `POST …/read` · `…/delete` 본문 `{"ids":[…]}`, 100개씩 나눠 보냄(`:34-54 · 62-66`), 빈 목록이면 보내지 않음.
- 모두 세션 고정(`sessionBound`) · 캐시 무시 · 재시도 0(`UserNotificationRequest.swift:34-43`). 세션 없으면 `unauthenticated`(`UserNotificationRepositoryImpl.swift:56-59`), `code != "SUCCESS"`면 `invalidResponse`.
- 매퍼: `createdAt`이 ISO-8601이 아니거나 `readAt`이 있는데 형식이 틀리면 쪽 전체 실패(`UND/Sources/Model/Mapper/UserNotificationDTO+Mapper.swift:10-24`).

Android: `service/api`에 `UserNotificationApiPath` · `UserNotificationApi`(`bearerAuthorization(expectedSessionId)`, `ApiAuthorization.kt`), 축제 `FestivalApiPath.packVersion(id)` 추가. 세션 없음은 `AuthSessionError.NotAuthenticated` → domain `UserNotificationError.Unauthenticated`.

## 캐시 · 저장

- 존 지도: 새 저장 없음. 받은 팩(`PackCache` 파일)에서 읽는다. 라이브 스냅샷은 메모리(보드와 같은 `FestivalLiveBoardUseCase`).
- 알림함: 로컬 저장 없음(iOS도 없음 — `UND` 전체가 네트워크 Repository뿐). 배지 · 배너는 메모리 공유 상태(E7).
- PrepGuide: 내 일정 · 팔로우는 서버, 선행 문서 소관.

## 화면 전환

| 전환              | iOS                                                                                                                  | 근거                             | Android                                                                                    |
| ----------------- | -------------------------------------------------------------------------------------------------------------------- | -------------------------------- | ------------------------------------------------------------------------------------------ |
| 보드 → 존 지도    | push, 이미 있으면 무시. 보드의 사용자 ID · 스냅샷 · 수신 시각을 복사                                                 | `FCo:176-192`                    | `push(FestivalRoute.ZoneMap(festivalId, zoneId))`, 중복 방지. 스냅샷은 넘기지 않는다(E3)   |
| 존 지도 뒤로      | `goBack`. 시스템 스와이프 뒤로 허용(`swipeBackButtonHidden` 없음)                                                    | `FCo:194-199`, `FCoV:22-23`      | 상단 바 뒤로 · 시스템 뒤로 = `goBack`                                                      |
| 존 지도 → 깃발 탭 | pop 후 상위에 `flagTabRequested`                                                                                     | `FCo:201-206`                    | pop + `FestivalFlow(onFlagTabRequested)` → `MainTabScreen` 탭 전환(E6)                     |
| 보드 → 알림함     | 보드 세션 없으면 `loginRequested`, 이미 있으면 무시, push                                                            | `FCo:123-129`                    | 보드가 이미 세션으로 분기(`LBV:167-169`). Coordinator push + 중복 방지                     |
| 알림함 뒤로       | `goBack`, 스와이프 뒤로 숨김                                                                                         | `FCo:137-139`, `FCoV:19-21`      | 상단 바 뒤로 · 시스템 뒤로                                                                 |
| 알림 열기         | 보드에 읽음 위임 → 목적지 없음이면 끝, 보드 카탈로그에 없으면 알림함에 팝업, 있으면 pop 후 다른 축제면 보드에서 선택 | `FCo:141-158`                    | 읽음은 공유 저장소(E7), 축제 전환은 Coordinator 대기 명령(E8)                              |
| 알림함 → 배지     | 삭제 후 받은 수를 보드에 전달                                                                                        | `FCo:160-165`                    | 공유 상태라 별도 전달 없음(E7)                                                             |
| 준비 안내 단계    | push 3번, 끝/나중에 = 셋 다 제거, 스와이프 뒤로 숨김                                                                 | `FCo:281-400`, `FCoV:33-41`      | PrepGuide PR에서. 시스템 뒤로 = 한 단계 pop(iOS는 스와이프 뒤로가 없음 — 차이를 PR에 적음) |
| 계정 변경         | 기수 보드 · 알림함 · 존 지도 · 크루 · 크루 만들기 · 준비 안내 제거                                                   | `FCo:29-37`                      | Coordinator가 `CurrentUserProvider.currentUserId` 변화를 받아 해당 경로 제거(E9)           |
| 팝업              | `fcAlert`                                                                                                            | `NotificationInboxView.swift:29` | `FCDialog`                                                                                 |

전환 애니메이션은 기존 `FestivalTransitions.push/pop`(`FFl:46-49`). 화면은 자기 `BackHandler`를 두지 않는다(`navigation.md:105`). 알림함 선택 모드에서 시스템 뒤로는 iOS처럼 pop이다(iOS 선택 모드의 뒤로 버튼도 `backTapped`, `NotificationInboxView.swift:45`).

## 탭 바 표시 (보드 D9 연장)

iOS(`MTC:77-100`): 라이브 탭은 스택이 1개, 알림함이 맨 위, 크루 · 크루 참여 · 크루 만들기 · 준비 안내 세 단계가 맨 위, 보드에 알림창 · 바꾸기 시트가 있으면 숨김. **존 지도는 보인다.** 자리 예약(`MTC:64-75`)은 Android `ModalBottomSheet` · 전체 화면 전환에 해당 문제가 없어 옮기지 않는다(보드 설계 위험 5와 같음).

Android `FestivalCoordinatorState.hidesTabBar`(`FCoA:22-23`)를 다음으로 바꾼다.

```
routes.size == 1
  || top is LiveBoard && isLiveBoardPresentingModal
  || top is NotificationInbox
  || top is PrepSchedule/PrepFlags/PrepChecklist   // PrepGuide PR에서 추가
```

알림함 선택 바는 탭 바가 숨은 상태에서 화면 맨 아래에 붙는다(내비게이션 바 인셋 적용).

## iOS → Android 적응

| 항목                                   | iOS                                                                | Android                                                                                                                        | 이유                                                        |
| -------------------------------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------- |
| 존 지도 그리기                         | SwiftUI `ZStack` + `.offset` + `GeometryReader`                    | `BoxWithConstraints` + `Modifier.offset/size` + `verticalScroll`                                                               | 같은 비율 좌표                                              |
| 고른 존 보이기                         | `ScrollViewReader.scrollTo(anchor: .bottom)` + 보이지 않는 기준 뷰 | `ScrollState.animateScrollTo(max(0, 존 아래 + 34 + 16 − (뷰 높이 − 아래 겹침 높이)))`, 아래 겹침 높이는 `onSizeChanged`로 잰다 | Compose에 앵커 스크롤이 없음. 식은 Z2에서 iOS와 나란히 확인 |
| 마커 줄 너비 판단                      | `GeometryReader`                                                   | `BoxWithConstraints`(영역 너비)                                                                                                |                                                             |
| 이름 축소 0.85                         | `minimumScaleFactor`                                               | `BasicText(autoSize = …)` 또는 측정 후 글자 크기 조정                                                                          | API는 Z2에서 Compose 문서로 확인                            |
| 그림자                                 | `.shadow(radius)`                                                  | `Modifier.dropShadow`(designkit 선례)                                                                                          |                                                             |
| 알림 목록                              | `ScrollView` + `LazyVStack`, 행 `onAppear`로 다음 쪽               | `LazyColumn`, 마지막 항목 표시를 `snapshotFlow(layoutInfo)`로 감지                                                             |                                                             |
| 60초 시각 갱신                         | `TimelineView(.periodic(60))`                                      | `LaunchedEffect` 60초 루프 + 주입 `Clock`                                                                                      | 보드 30초와 같은 방식                                       |
| 팝업                                   | `fcAlert`                                                          | `FCDialog`                                                                                                                     | designkit 기존                                              |
| 응답 세대                              | `instanceID`                                                       | 항목 범위 ViewModel(pop 시 취소)                                                                                               | 구조로 보장                                                 |
| 읽음 요청 소유                         | 보드 Reducer effect                                                | 공유 저장소 + 앱 범위 코루틴(E7)                                                                                               | 항목 ViewModel이 서로 부르지 못함                           |
| 보드 축제 전환 명령                    | Coordinator가 보드 액션 전송                                       | Coordinator 대기 값 → 보드 Screen이 소비(E8)                                                                                   | 같은 이유                                                   |
| 아래 바 흐림                           | `.ultraThinMaterial`                                               | `materialGlass` 단색                                                                                                           | Compose 표준 흐림 없음                                      |
| 스와이프 뒤로 숨김(알림함 · 준비 안내) | `swipeBackButtonHidden()`                                          | 시스템 뒤로 그대로                                                                                                             | Android 표준 뒤로(보드 D7과 같음)                           |

## PR 묶음 (화면 순서)

| PR                  | 내용                                                                                                                                                                           | 검증                                                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| Z1 존 지도 데이터   | `service/api` `packVersion` 경로 · DTO, 팩 문서 DTO에 지도 필드(lossy), `domain/festival` `FestivalZoneMap` · 매퍼 · `FestivalZoneMapUseCase` · `FestivalPackUseCase.syncPack` | `FestivalZoneMapUseCaseTests` 3건, 매퍼(정렬 · 비율 · 머리글 · 지금 공연), MockEngine 버전 응답(stage 축약 자료)      |
| Z2 존 지도 화면     | `ZoneMapModel` · Contract · ViewModel · Screen/Content · Canvas · Card · List · Legend · Skeleton · 프리뷰                                                                     | `FestivalZoneMapFeatureTests` 6건(모델 사례 포함) Orbit 테스트, 프리뷰                                                |
| Z3 존 지도 연결     | `FestivalRoute.ZoneMap`, Coordinator push(중복 방지)/pop/깃발 탭 요청, `FestivalFlow` 항목, `MainTabScreen` 탭 전환 콜백, 계정 변경 시 제거                                    | `FestivalCoordinatorTest`(push · 중복 · pop · 탭 바 보임 · 계정 변경), stage 수동 확인(수용 기준 1~4)                 |
| N1 알림 데이터      | `domain/usernotification`(신규) interface · sources · `di/`, `service/api` 경로 · DTO · Api, 공유 배지 상태                                                                    | `UserNotificationDomainTests` 3건 · `UserNotificationRepositoryTests` 4건(MockEngine: 세션 · 봉투 · 100개 분할 · 401) |
| N2 보드 배지 · 배너 | `LiveBoardState` 배지 · 배너, 조회 시점(보드 표시 · 세션 변경), 배너 UI                                                                                                        | `FestivalNotificationBadgeTests` 5건                                                                                  |
| N3 알림함 화면      | Presentation · Contract · ViewModel · Screen(목록 · 선택 바 · 스켈레톤 · 빈/오류 · 팝업)                                                                                       | `NotificationInboxFeatureTests` 11건(`sectionsUseSeoulCalendar` 포함)                                                     |
| N4 알림함 연결      | `FestivalRoute.NotificationInbox`, push/pop, 열기 → 보드 축제 전환(E8), 탭 바 숨김, 계정 변경 시 제거                                                                          | `FestivalCoordinatorTest` 추가, stage 로그인 수동 확인(수용 기준 5 · 6)                                               |
| P1~P3 준비 안내     | 선행(Flag · Timetable · Profile 문서) 뒤. P1 트리거 + 흐름 + 2단계, P2 1단계 연결, P3 3단계 연결                                                                               | `FestivalPrepGuideTests` 6건                                                                                          |

Z와 N은 서로 독립이다. 각 묶음 안에서는 순서대로(Z1 → Z2 → Z3, N1 → N2 → N3 → N4). 각 PR은 `verify --all` · CI 통과 후 squash merge.

### 기능 spec (`.spec/`)

| PR      | spec 파일                                                                                                 | 담는 내용                             |
| ------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| Z1      | `domain/festival/.spec/zone-map.md`                                                                       | 지도 필드 · 정렬 · 머리글 · 버전 비교 |
| Z2 · Z3 | `feature/festival/.spec/zone-map-screen.md`                                                               | 배치 · 마커 · 카드 · 띠 · 감시 · 전환 |
| N1      | `domain/usernotification/.spec/user-notification.md`                                                      | 요청 · 매핑 · 분할 · 오류 · 공유 상태 |
| N2~N4   | `feature/festival/.spec/notification-inbox-screen.md` · `live-board-screen.md`(갱신: 배지 · 배너 · 탭 바) |                                       |

## 새 라이브러리 후보

| 후보                                                                                                                              | 용도             | 라이선스             | 판단                                                    |
| --------------------------------------------------------------------------------------------------------------------------------- | ---------------- | -------------------- | ------------------------------------------------------- |
| 없음(Compose foundation `BoxWithConstraints` · `verticalScroll` · `LazyColumn`, ui `dropShadow` — 이미 사용 중, BOM `2026.09.00`) | 존 지도 · 알림함 | Apache-2.0(AndroidX) | 추천                                                    |
| Compose `Modifier.transformable`(foundation, 이미 포함)                                                                           | 지도 확대 · 이동 | Apache-2.0           | 쓰지 않음. iOS에 확대 · 이동이 없다(E1)                 |
| 지도 SDK(Google Maps Compose · MapLibre 등)                                                                                       | 실제 지도        | 확인 안 함           | 불필요. iOS가 MapKit을 쓰지 않으므로 검토 대상에서 뺐다 |

새 의존성 없음. 라이선스 승인 대상 없음.

## 위험

1. **팩에 지도 필드가 있는지 미확인**: 존 지도는 WebSocket으로 받은 팩을 읽는다. stage 상세(HTTP)에는 지도 필드가 있음을 확인했지만 팩 본문(WebSocket)을 열어 보지는 않았다. iOS가 같은 키로 팩을 읽으므로(`FestivalZoneMapUseCaseImpl.swift:24-28`) 같다고 보지만 Z3 수동 확인에서 처음 맞춘다.
2. **고른 존 보이기 계산**: iOS는 앵커 스크롤이라 Android 식이 다를 수 있다. 스크린샷으로 대조한다.
3. **알림함 미검증**: 로그인 응답(목록 · 커서 형식 · `route` 값 종류 · 읽음/삭제 응답 본문)을 stage에서 보지 못했다. N4 수동 확인 전까지 iOS 코드만 근거다.
4. **읽음 요청 수명**: 앱 범위 코루틴은 프로세스가 죽으면 끝난다. iOS도 앱 종료 시 같다. 실패해도 다음 목록 조회가 서버 상태로 맞춘다(`NotificationInboxFeature.swift:22-25`).
5. **PrepGuide 지연**: 선행 화면이 오기 전까지 첫 팩 받기 후 준비 안내가 열리지 않는다(지금과 같음).
6. **깃발 탭 자리 화면**: E6을 따르면 존 지도 안내 띠가 빈 깃발 탭으로 보낸다. 사용자에게 막힌 길로 보일 수 있다.

## 결정

**결정 필요(추천안 포함)**

- E1. 존 지도 렌더링: (a) 비율 좌표 Compose 레이아웃 + 세로 스크롤, 확대 · 이동 없음(추천, iOS와 같음). (b) `transformable`로 확대 · 이동 추가 — iOS에 없는 동작.
- E2. `mapImageUrl`: (a) 읽어 엔티티에만 두고 그리지 않음(추천, iOS View 미사용 · stage null). (b) 배경 이미지로 그림 — iOS와 달라짐.
- E3. 존 지도 첫 스냅샷: (a) 경로는 ID만, 화면이 즉시 `liveBoard(id)` 조회(추천, `navigation.md` "경로 값은 작은 값"). 첫 응답 전까지 마커 없이 지도만 보인다. (b) 보드 스냅샷을 경계 값 → Coordinator → `parametersOf`로 넘김(iOS `FCo:184-190`과 같음, 깜빡임 없음) — 보드 목록 넘기기(`FFl:64`) 선례가 있다.
- E4. 지도 필드 해석: (a) 기존 `FestivalPackDocumentDto`(`service/api`)에 지도 필드를 lossy로 추가하고 `FestivalPackDocument.Zone`을 넓힘(추천, Android는 DTO를 `service/api`에 두고 팩을 한 번만 해석, `FestivalPackRepositoryImpl.kt:101-107`). (b) iOS처럼 지도 전용 DTO로 원본을 따로 해석.
- E5. 팩 업데이트: (a) `FestivalPackUseCase.syncPack(id)`를 열어 `PackSyncService.sync`(`service/pack/.../PackSyncService.kt:65`) + 목록 갱신(iOS `updatePack`과 같음, 추천). (b) 기존 `downloadPack` Flow 재사용 — 진행률 이벤트가 섞이고 iOS와 호출 경로가 다르다.
- E6. "깃발을 팔로우하면…" 띠 · 보드 `FlagDirectory`: (a) pop 후 `MainTabScreen`이 깃발 탭으로 전환(추천, iOS 이동과 같고 탭 내용은 이후 문서). (b) 경계로 두고 무동작.
- E7. 배지 · 배너 · 읽음 요청 소유: (a) `domain/usernotification`에 `StateFlow`(안 읽은 수 · 배너 후보) + 읽음 · 삭제 뒤 재조회, 읽음은 앱 범위 `CoroutineScope`에서 실행(추천, 보드 D1과 같은 공유 저장소 방식). (b) 보드 ViewModel이 소유하고 Coordinator가 명령을 중계 — 항목 범위 ViewModel끼리 호출 경로가 없다.
- E8. 알림 → 다른 축제: (a) Coordinator가 `pendingBoardFestivalId`를 두고 pop, `FestivalFlow`가 보드 Screen에 넘겨 보드가 `selectFestival` 후 소비 알림(추천, 보드 상태 유지 — iOS도 같은 보드에서 선택). (b) 보드 경로를 새 ID로 교체 — 보드 상태가 처음부터 다시 시작.
- E9. 계정 변경 정리: Coordinator가 `CurrentUserProvider`를 받아 알림함 · 존 지도 · 준비 안내 경로를 제거(추천, iOS `FCo:29-37`). 대안: 각 화면이 세션 변화에 스스로 뒤로.
- E10. PrepGuide: (a) 구현은 Flag · Timetable · Profile 문서 뒤로 미루고, 지금은 이 문서의 흐름 설계만 둔다(추천). 트리거 경계(`LiveBoardBoundary.PrepGuide` · 목록 대응)도 그때 함께 추가한다. (b) 2단계만 먼저 — 깃발 디렉터리(507줄) · 팔로우 API를 이 문서로 끌어와 범위가 두 배 넘게 커진다.
- E11. 존 목록 행 탭: iOS는 선택만 바꾸고 목록 화면에 카드가 없다(`FestivalZoneMapView.swift:181-216`). (a) iOS 그대로 옮기고 이슈로 남김(추천). (b) 목록에서도 카드 표시.

**결정 사항**

- 감시 주기 35초 · `RateLimited`는 `max(35, retryAfter)` · 오래됨 30분 · 배너 24시간 · 알림 분할 100개 · 알림 시각 갱신 60초는 iOS 값 그대로.
- 활성 깃발 타일의 존 칩은 iOS처럼 계속 무동작.
- 경로 값: `ZoneMap(festivalId, zoneId?)` · `NotificationInbox`(값 없음).
- 접근성 식별자: iOS 세 화면에 `accessibilityIdentifier`가 없다(`ZM` · `PG` · `NI` 검색 0건). 스켈레톤만 보드 선례(`FestivalAccessibilityId.kt`)처럼 `testTag`를 추가할지는 PR에서 정한다. 접근성 라벨은 위 표의 iOS 문구 그대로.

## 확인 못 한 것

- WebSocket 팩 본문에 지도 필드가 있는지(위험 1). stage HTTP 상세만 봤다.
- 로그인 응답: 알림 목록 · 커서 · `route` 종류 · `type` 종류 · 읽음/삭제 응답, 서버가 기기 등록(푸시 토큰) 없이 알림함 항목을 만드는지.
- 존 크루 · 팔로우 깃발이 있는 라이브 응답(로그인 필요) — 크루 마커 · 팔로우 마커를 실데이터로 못 봤다.
- Compose 글자 축소(`autoSize`) · `dropShadow` 반경 변환 · `ScrollState` 앵커 계산 — Z2에서 공식 문서 · designkit 선례로 확인.
- iOS 존 지도 스냅샷 시각(`App/Tests/Sources/ZoneMapVisualCaptureTests.swift`)을 열어 보지 않았다. Z2 스크린샷 대조 때 기준으로 쓸 수 있는지 확인한다.
- PrepGuide 1 · 3단계(`SchedulePick` · `PrepChecklist`)의 화면 수치 · 문구 — 각 선행 문서에서 조사한다.
- Android 로그인 시트(종 아이콘 비로그인 분기 대상) 유무 — 보드 설계와 같이 미정.

## 결정 (호스트, 2026-11-22 — 사용자 지시 "묻지 말고 iOS와 같게")

- E1 · E2 · E4 · E5 · E7 · E8 · E9 · E11: 추천안대로.
- E3: 경로에는 ID만 넘기되 첫 화면은 보드의 마지막 스냅샷으로 채운다(iOS처럼 첫 응답 전 깜빡임 없음). 구현(Z1): `FestivalLiveBoardService`의 세션별 캐시를 `cachedFetch(festivalId): FestivalLiveBoardFetch?`(보드 DTO · 깃발 프로필 목록 · 수신 시각 전부 — `refresh()`가 돌려주는 것과 같은 값)로 공개하고(현재 세션 ID가 캐시 키와 같을 때만 반환), Repository는 기존 `liveBoard()`와 같은 `toDomain(fetch.flags, …)` 매핑을 그대로 적용한다, `FestivalLiveBoardRepository.cachedLiveBoard(festivalId): FestivalLiveBoardSnapshot?`와 `FestivalLiveBoardUseCase.cachedLiveBoard`로 올린다. 존 지도 ViewModel은 진입 때 이 값을 먼저 그리고 곧바로 `liveBoard(festivalId)`로 새로 조회한다. 캐시가 없으면(보드를 거치지 않은 진입 · 첫 조회 실패) 마커 없이 그린 뒤 조회 결과로 채우고, 세션이 바뀌면 캐시는 null(서비스가 이미 세션별로 나눔)이라 이전 계정 데이터가 보이지 않는다. 테스트: 캐시 있음(스냅샷 전체 필드 — 팔로우 깃발 이름 · 이미지 · 태그 포함 — 가 새 조회와 같은 매핑) · 없음 · 세션 변경 · 캐시 초기화.
- E6: 추천안대로 보드로 돌아가 깃발 탭으로 전환한다. 깃발 탭이 채워지기 전까지는 빈 자리 화면이 보이는 것을 위험에 둔다(깃발 단계에서 해소).
- E10: PrepGuide는 깃발 · 시간표 · 마이 단계 뒤로 미룬다. 진입 콜백은 그때까지 무동작(보드 L5와 같음).
