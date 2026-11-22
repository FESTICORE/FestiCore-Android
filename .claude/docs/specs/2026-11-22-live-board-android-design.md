# 라이브 보드(LiveBoard) Android 이식 설계

- 날짜: 2026-11-22
- 이슈: 미정(구현 이슈를 만들 때 적는다)
- 상태: 제안
- 기준: Android `develop` a21cf53, iOS `fcf53272`(로컬 `../FestiCore-iOS`, 읽기 전용)
- 범위: `feature/festival` · `domain/festival` · `service/api` · `service/festivalcatalog` · `service/pack`(신규) · `service/device`(신규, 설치 ID만) · `core/network`(WebSocket) · `core/datastore` · `app`

## 목표

iOS 라이브 탭의 S01 라이브 보드(목록에서 축제를 고르면 push되는 화면)와 그 화면이 쓰는 데이터 계층(축제 상세 · 라이브 상태 폴링 · 공개 깃발 · 공식 인스타그램 · 팩 WebSocket 동기화 · 팩 저장)을 Android로 옮긴다. FestivalList 설계 D2에서 미룬 **팩 받기**를 이 문서에서 채운다(목록 카드의 "팩 받기" 버튼이 실제로 받는다).

시각(수치 · 문구 · 토큰 · 에셋 · 접근성 라벨)은 iOS View와 같게, 플랫폼 관례(뒤로 · 시트 · 수명 주기 · 외부 링크)는 Android 방식을 따른다(아래 "iOS → Android 적응").

iOS 원본(이하 `iOS` = `../FestiCore-iOS/Projects`, `LB` = `iOS/Feature/Festival/Sources/LiveBoard`, `FF` = `iOS/Feature/Festival/Sources/FestivalList/Reducer/FestivalFeature.swift`, `FCo` = `iOS/App/Sources/Navigation/Festival/FestivalCoordinator.swift`):

- 화면: `LB/View/FestivalLiveBoardRouteView.swift:7-97`, `LB/View/FestivalLiveBoardScreen.swift:10-523`
- 섹션 · 카드: `LB/Components/FestivalLiveBoardView.swift:6-106`(지금/다음 무대), `FestivalLiveBoardOfficialNewsSection.swift:18-132`, `FestivalLiveBoardCrewSection.swift:9-89`, `FestivalLiveBoardActiveFlagSection.swift:9-97`, `FestivalLiveBoardFollowedFlagSection.swift:9-122`, `FestivalLiveBoardCards.swift:6-263`, `FestivalLiveBoardSkeletonView.swift:5-23`
- 화면 모델: `LB/Model/FestivalLiveBoardPresentation.swift:13-570`, `FestivalLiveBoardScreenModel.swift:5-134`, `FestivalLiveBoardSchedule.swift:6-50`, `FestivalHomeDeparture.swift:12-123`, 진행 표시 `iOS/Feature/Festival/Sources/FestivalList/Model/FestivalPackDownloadDisplay.swift:11-37`
- 자식 Reducer: `LB/Reducer/FestivalLiveBoardFeature.swift:8-363`(폴링 · 공식 소식), 시트 `LB/Switcher/FestivalSwitcherFeature.swift:5-91` · `FestivalSwitcherView.swift:6-156`
- 부모 Reducer(보드 부분): `FF` State `19-171 · 285-320`, Connection `361-369`, Toast `371-376`, View `404-458`, 처리 `714-783 · 785-815 · 887-1105 · 1127-1202`, Async `1211-1331`, Inner `1454-1489 · 1573-1759`, 효과 `2157-2282`, 초기 선택 `2300-2307`, 자식 위임 `574-670`
- 흐름: `FCo:15-20 · 99-121 · 123-480 · 498-570 · 755-770`, `iOS/App/Sources/Navigation/Festival/FestivalCoordinatorView.swift:10-43`, 탭 바 `iOS/App/Sources/Navigation/MainTab/MainTabCoordinator.swift:64-113` · `MainTabView.swift:36-55 · 146-164`
- 도메인: `iOS/Domain/FestivalDomain/Interface/Sources/Entity/{FestivalLiveBoardSnapshot,FestivalPackDocument,FestivalLiveBoardSchedule,FestivalOfficialNews}.swift`, `Interface/Sources/LiveBoard/*.swift`, `Interface/Sources/Pack/FestivalPackUseCaseInterface.swift:12-27`, `Sources/LiveBoard/FestivalLiveBoardUseCaseImpl.swift:12-27`, `Sources/Pack/FestivalPackUseCaseImpl.swift:14-54`, DTO `Sources/Model/DTO/{FestivalLiveBoardResponseDTO,FestivalOfficialNewsResponseDTO}.swift`, 매퍼 `Sources/Model/Mapper/{FestivalLiveBoardDataDTO+,FestivalOfficialNewsDataDTO+}.swift`, 저장 `Sources/Selection/FestivalSelectionStore+Live.swift:5-11` · `Sources/Guide/FestivalGuideVisibilityStore+Live.swift:5-41`
- 서비스: `iOS/Service/FestiCoreFestivalCatalog/Sources/NetworkFestivalLiveBoardService.swift:8-135`, `NetworkFestivalOfficialNewsService.swift:14-38`, `Endpoint/{FestivalLiveBoardRequest,FestivalOfficialNewsRequest,FestivalDetailRequest}.swift`, `NetworkFestivalCatalogService.swift:26-36`(상세), 팩 `iOS/Service/FestiCorePack/{Interface/PackSyncService.swift,Interface/PackSyncError.swift,Sources/NetworkPackSyncService.swift,Sources/PackWire.swift,Sources/PackCache.swift,Sources/ReconnectBackoff.swift}`, WebSocket 경로 `iOS/Service/APIEndpoint/Sources/Festival/FestivalPackSocketRequest.swift:11-29` · `iOS/Service/API/Sources/Festival/FestivalAPI.swift:13-23` · `iOS/Core/FestiCoreNetwork/Sources/Client/NetworkClient.swift:177-194`, 조립 `iOS/Service/ServiceAssembly/Sources/NetworkContainer.swift:72-99`, 설치 ID `iOS/Service/FestiCoreDevice/Sources/NetworkDeviceRegistrationService.swift:86-95`
- 날짜 형식: `iOS/Core/FestiCoreUtility/Sources/Date/AppDateFormat.swift:11-30`(`time = "HH:mm"`, `liveBoardDay = "MM.dd (E)"` ko_KR, `monthDayTime = "MM/dd HH:mm"`, 모두 Asia/Seoul)

## 범위

iOS는 목록과 보드가 같은 `FestivalFeature`를 쓰지만 **보드 route는 별도 인스턴스**다(`FCo:101-117`이 새 `FestivalFeature.State`를 만들어 push, `FCo:755-757` `festivalList(FestivalFeature)` · `liveBoard(FestivalFeature)`). Android는 FestivalList D1대로 `LiveBoardViewModel`을 따로 두고, 목록과 보드가 함께 보는 값(팩 캐시 · 받는 중 진행률 · 선택 축제)은 Repository의 `StateFlow`로 공유한다(결정 D1).

**1차(이 문서, PR L1~L5)**

1. 데이터: 축제 상세(온라인 팩 문서), 라이브 상태 35초 폴링(ETag · 429 · 미공개), 공개 깃발 프로필, 공식 인스타그램 5분 폴링, 설치 기기 ID
2. 팩: WebSocket `pack.get` 동기화 · 청크 받기 · 진행률 · 검증(sha256) · 파일 저장 · 재접속 · 변경 알림 감시, 목록 카드 "팩 받기/업데이트" 동작과 진행 표시 · 실패 토스트
3. 보드 화면 S01: 상단 바(로고 + 알림 아이콘) · 오프라인 배지 · 헤더(축제 이름 + 바꾸기) · 팩 받는 중 카드 · 미공개 카드 · 개장 카운트다운 · 다음 공연 카드 · 지금/다음 무대 · 출처 줄 · 공식 인스타그램 · 내 크루(읽기) · 팔로우한 깃발(읽기) · 활성 깃발 · 종료 요약 · 귀가 카드(서버 도보 값만) · 스켈레톤 · 빈/오류 카드
4. 축제 바꾸기 시트, 팔로우 안내 숨김 확인 다이얼로그(로컬 저장)
5. 목록 → 보드 push, 보드 → 목록 pop(뒤로 · 시트 "전체 페스티벌 보기"), 탭 바 표시 규칙, 포그라운드/백그라운드에서 폴링 · 감시 시작/중지
6. 로그인 사용자의 내 일정 읽기 `GET api/v1/me/schedule/{id}`(다음 일정 · "내 일정" 태그 · 종료 요약에 필요, 결정 D5)

**경계만 표시(다음 문서)** — 보드는 콜백만 내보내고 Coordinator가 받아 둔다. 대상 화면이 없으면 아무 일도 하지 않는다.

| 진입점                                                                                          | iOS 위치                                                                                                      | 다음 문서                                                |
| ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| 알림 아이콘 → 알림함(로그인 시) / 로그인 요청(비로그인) · 안 읽은 수 배지 · 24시간 새 알림 배너 | `FestivalLiveBoardScreen.swift:72-88 · 223-291`, `FF:1085-1086`, `FCo:123-165`                                | NotificationInbox                                        |
| 크루 존 칩 · 활성 깃발 존 → 존 지도                                                             | `FestivalLiveBoardCards.swift:89-93`, `FF:997-1002`, `FCo:176-199`                                            | ZoneMap                                                  |
| 첫 팩 받기 완료(로그인) → 준비 안내 S15                                                         | `FF:1687-1690`, `FCo:281-412`                                                                                 | PrepGuide                                                |
| 크루 카드 열기 · 만들기 · 모여! 시트 · 같이 귀가                                                | `FF:1007-1032 · 1066-1067`, `LB/CrewGather/*`, `LB/CrewCreate/*`, `FCo:212-280`                               | Crew                                                     |
| 깃발 찾기 · 깃발 탭 · 기수 보드(LogoBearer)                                                     | `FF:1034-1035 · 1077-1090`, `FCo:131-135 · 171-174 · 464-468`                                                 | Flag · Bearer                                            |
| 크루 · 팔로우 로그인 카드 → 로그인 시트                                                         | `FestivalLiveBoardCrewSection.swift:42-43`, `FestivalLiveBoardFollowedFlagSection.swift:44-45`, `FCo:452-462` | 로그인 시트(북마크 문서와 함께, 결정 D4)                 |
| 귀가 "가는 길"(Apple 지도 · 도보 계산 · 위치 권한)                                              | `LB/Reducer/FestivalFeature+HomeWalk.swift:16-125`, `FF:1037-1064`                                            | `core/location` 문서                                     |
| Live Activity · 위젯 딥링크(`openWidgetFestival`) · 30일 지난 팩 자동 삭제                      | `FF:182-283 · 887-891 · 940-965 · 1375-1435`, `FCo:508-570`, `FestivalPackUseCaseInterface.swift:19-21`       | Android 대응 별도 결정(`ios-parity.md` "옮기지 않는 것") |
| 서버 콘텐츠 변경 알림(`remoteContentChanged`)                                                   | `FF:745-763`                                                                                                  | 푸시 문서                                                |
| 분석 `screenView(liveBoard)` · `packSync`                                                       | `FF:893-897 · 1676-1684 · 1719-1722`                                                                          | `service/analytics`                                      |

## 수용 기준

1. 목록에서 카드를 누르면 보드가 오른쪽에서 push되고(기존 `FestivalTransitions.push`), 탭 바가 보인다. 시스템 뒤로(예측 뒤로 포함) = 목록으로 pop.
2. stage `fst_00e0f4f1d8065a93`(WONDERLIVET 2026, 2026-11-20~22) 기준: 헤더 "WONDERLIVET 2026 …" + `DAY n · MM.dd (E) · 12:00–22:00`, 지금/다음 무대 2개(LIVET STAGE · WONDER STAGE), 활성 깃발 9개가 "최근 업데이트 순"으로 보인다. 공식 인스타그램은 `available:false`라 섹션이 없다.
3. 팩이 없는 축제: 상세 `GET api/v1/festivals/{id}`로 그린다(iOS `displayDocument = onlineDetail ?? selectedPack`, `FF:63`). 실패하면 "시간표를 불러오지 못했어요" + "다시 시도".
4. 목록 카드 "팩 받기" → "팩 받는 중 · N%" 진행 바 → "팩 받음 · 최신 버전". 앱을 다시 켜고 비행기 모드여도 그 축제 보드가 저장 팩으로 그려지고 배지 "오프라인 · N분 전 정보"(또는 수신 전이면 "오프라인 · 저장된 정보")가 보인다.
5. 라이브 상태: 보드가 보이고 앱이 포그라운드일 때만 35초마다 조회, 429면 `Retry-After`(없으면 35초) 뒤 재조회하고 보이던 보드를 유지, `FESTIVAL_NOT_FOUND`면 "라이브 보드 공개 전이에요" 카드. 백그라운드 · 화면 이탈 시 즉시 중지.
6. 화면 시각은 서버 시각으로 보정(`serverAt + (기기 now − 첫 수신 기기 시각)`)하고 30초마다 다시 계산한다.
7. 축제 바꾸기 시트: 기기에 받은 팩이 있는 축제만, 선택 축제에 체크, 고르면 같은 보드에서 축제가 바뀐다. "전체 페스티벌 보기" = 목록으로 pop. 시트가 열린 동안 탭 바 숨김.
8. 단위 테스트(JVM): `FestivalLiveBoardPresentationTests` · `FestivalLiveBoardScheduleTests`(Feature/Domain) · `FestivalLiveBoardEndCardsTests` · `FestivalLiveBoardOfficialNewsTests` · `FestivalLiveBoardSessionTests` · `FestivalOfflineTests`(보드 사례) · `FestivalPackDownloadFeatureTests` · `FestivalLiveBoardMapperTests` · `FestivalOfficialNewsMapperTests` · `PackSyncTests` · `SQLitePackCacheTests`(파일 캐시에 해당하는 사례)를 같은 이름으로 옮긴다. 실서버 · 실 WebSocket을 부르지 않는다(Ktor `MockEngine` · 가짜 소켓).
9. `.claude/scripts/verify --all` · PR CI · `assembleStageDebug` 통과, stage 수동 확인(스크린샷)은 L5 PR에 기록.

## 모듈과 책임

| 모듈                         | iOS 대응                                                                                                                  | 내용                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `feature/festival/interface` | `Feature/Festival/Interface`                                                                                              | `FestivalRoute.LiveBoard(festivalId: String)` 추가                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `feature/festival/sources`   | `LB/**` · `FestivalList`의 보드 부분 · `FCo`                                                                              | `LiveBoardViewModel` · `LiveBoardContract` · `LiveBoardScreen`/`Content` · `LiveBoardPresentation`(순수 Kotlin) · `LiveBoardScreenModel` · `HomeDeparture` · `FestivalSwitcherSheet` · 섹션 컴포저블, `FestivalCoordinator`에 push/pop · 경계 콜백, 목록 ViewModel의 팩 받기 연결                                                                                                                                                                                                                                             |
| `domain/festival/interface`  | `Domain/FestivalDomain/Interface`                                                                                         | `FestivalPackDocument` · `FestivalLiveBoardSnapshot` · `FestivalLiveBoardSchedule`(entries · stages) · `FestivalOfficialNews` · `CachedFestivalPack` · `PackSyncResult` · `PackDownloadEvent` · `FestivalLiveBoardError`, `FestivalLiveBoardUseCase`(liveBoard · officialNews · festivalDetail), `FestivalPackUseCase`(cachedPacks · packUpdates · downloadPack · `packs: StateFlow` · `downloads: StateFlow`), `FestivalSelectionUseCase`(선택 저장), `FestivalGuideVisibilityUseCase`, `FestivalScheduleUseCase.mySchedule` |
| `domain/festival/sources`    | `Domain/FestivalDomain/Sources`                                                                                           | 매퍼(라이브 + 깃발 프로필 병합, 공식 소식 https · instagram 호스트 검사, 팩 문서 손상 원소 건너뛰기), 일정 계산, UseCase 구현, 공유 상태 저장소                                                                                                                                                                                                                                                                                                                                                                               |
| `service/api`                | `Service/API` · `APIEndpoint` · `FestiCoreFestivalCatalog/Endpoint`                                                       | 경로 `api/v1/festivals/{id}` · `/live` · `/flags` · `/instagram` · `api/v1/me/schedule/{id}` · WebSocket `api/v1/festivals/updates`, DTO(`FestivalPackDocumentDto` · `FestivalLiveBoardDto` · `FestivalFlagsDto` · `FestivalOfficialNewsDto` · `FestivalScheduleDto`) — FestivalList D5와 같이 DTO는 여기                                                                                                                                                                                                                     |
| `service/festivalcatalog`    | `NetworkFestivalLiveBoardService` · `NetworkFestivalOfficialNewsService` · `NetworkFestivalCatalogService.festivalDetail` | 상세 조회, 라이브 조회(세션별 ETag 캐시 · 429 대기 · 404 미공개 · 세션 바뀌면 캐시 비움 · 응답 뒤 세션 재확인), 깃발 프로필 조회, 공식 소식 조회                                                                                                                                                                                                                                                                                                                                                                              |
| `service/pack`(신규)         | `Service/FestiCorePack`                                                                                                   | `PackWire`(메시지 조립 · 해석 · 청크) · `PackCache`(파일, 결정 D2) · `NetworkPackSyncService`(sync · download · updates · 재접속 backoff · 세대 관리)                                                                                                                                                                                                                                                                                                                                                                         |
| `service/device`(신규, 최소) | `Service/FestiCoreDevice` `installationDeviceID`                                                                          | 설치 UUID를 `SecureStorage`에 한 번 만들어 저장하고 돌려준다. 서버 등록(`register`)은 하지 않는다(`/live`는 UUID 형식만 검사 — 아래 서버 계약)                                                                                                                                                                                                                                                                                                                                                                                |
| `core/network`               | `Core/FestiCoreNetwork/Sources/WebSocket/*`                                                                               | `BASE_URL`의 https → wss(테스트 http → ws)로 바꿔 텍스트 WebSocket 연결(Ktor `WebSockets` 플러그인, 결정 D3). 인증 없음                                                                                                                                                                                                                                                                                                                                                                                                       |
| `core/datastore`             | `UserDefaults` 키                                                                                                         | `FestiCore.selectedFestivalID`(문자열), `FestiCore.hiddenFollowedFlagGuides`(축제 ID 집합), 설치 ID(SecureStorage 키 `deviceID`)                                                                                                                                                                                                                                                                                                                                                                                              |
| `ui/designkit`               | `UI/FestiCoreDesignKit`                                                                                                   | 새 컴포넌트 없음. 이미 있는 것 사용(아래 "화면")                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `app`                        | `MainTab`                                                                                                                 | 탭 바 규칙 확장, Koin 목록, 팩 저장 폴더 경로 · `BASE_URL` 전달                                                                                                                                                                                                                                                                                                                                                                                                                                                               |

`core/database`(Room)는 만들지 않는다(결정 D2).

## 화면 (S01 라이브 보드)

모든 색 · 글꼴 · 간격 · 반경은 designkit 토큰. 수치는 iOS 값(pt → dp 1:1).

**사용하는 designkit 컴포넌트(모두 존재 확인)**: `FCTopBar(Logo / LogoBearer / Back, badgeCount, onTrailingAction)` · `FCOfflineBadge` · `FCPackDownloadProgress` · `FCEmptyCard(icon, action, onClose)` + `FCCardAction` · `FCCountdownCard` · `FCNextSetCard(label)` · `FCSectionHeader(actionTitle)` · `FCLiveBadge` · `FCTag` · `FCSourceLine(isInset = false)` · `FCHomeCard` · `FCDoneCard` · `FCSkeletonView` · `FCAvatar` · `FCZoneChip` · `FCStatusChip` · `FCButton` · `FCListRow` · `FCSheet` · `FCDialog` · `FCToastHost`, `ui/sharedui` `FCRemoteImage`, `ScrollBottomInset`. 아이콘 `ic_chevron_right_20` · `ic_chevron_right_16` · `ic_check` · `ic_xmark_16` · `ic_flag` · `ic_flag_20` · `ic_music_24` 모두 `ui/designkit/src/main/res/drawable`에 있음.

### 루트 (`FestivalLiveBoardRouteView.swift:15-46`)

- 선택 축제를 찾으면 보드. 못 찾으면 `FCTopBar(Back, title = "라이브 보드", onBack = 목록으로)` + 패딩 `paddingP16` 안에 로딩 중이면 보드 스켈레톤, 아니면 `FCEmptyCard(ic_music_24, "페스티벌 정보를 찾을 수 없어요", "목록으로 돌아가 다시 선택해 주세요.")`. 배경 `backgroundNormal`.
- 화면 위 토스트 오버레이(목록과 같은 `FCToastHost`), 토스트 `easeOut 0.2s`(`:47-62`).

### 보드 (`FestivalLiveBoardScreen.swift:18-219`)

세로 스택 간격 0, 배경 `backgroundNormal`:

1. 상단 바(`:220-235`): 기수 깃발이 있으면 `LogoBearer`(`onBearer` = 경계), 아니면 `Logo`. 배경 `backgroundNormal`, 오른쪽 = 알림(경계), `badgeCount = notificationUnreadCount`(1차는 0 고정, 결정 D11).
2. 오프라인 배지(`:69-74`): 조건 `(liveBoardUnavailable && !unpublished) || connection ∈ {offline, reconnecting}`(`FestivalLiveBoardScreenModel.swift:61-63`). 문구 수신 기록 없음 → "오프라인 · 저장된 정보", 있음 → "오프라인 · {방금|N분 전|N시간 전|N일 전} 정보"(`Presentation:178-187`).
3. (알림 배너 `:75-88` — 경계. 1차는 그리지 않음)
4. 스크롤(스크롤 막대 없음, 하단 `ScrollBottomInset`), 내용 세로 간격 `paddingP24`, 좌우 `paddingP16`, 위 `paddingP16`(배너 있으면 0).
   - 헤더(`:309-356`, 배너 없을 때만): 버튼 [축제 이름(2줄) + `ic_chevron_right_20` 20×20 90° 회전], 간격 `paddingP8`, `baseTitle2Bold` + 줄 박스, `labelNormal`. 접근성 라벨 "페스티벌 바꾸기, {이름}". 아래 간격 `paddingP4` dayLine `baseFootnoteRegular` `labelNeutral`.
   - 팩 받는 중이면(`:293-307`) `FCPackDownloadProgress(text, progress)` 패딩 `paddingP16`, 배경 `backgroundElevated` 반경 `r16`.
   - 미공개(`:103-108`): `FCEmptyCard(ic_music_24, "라이브 보드 공개 전이에요", "축제 라이브 보드가 아직 공개되지 않았어요. 공개 후 공연과 깃발 현황을 확인해 주세요.")` — 이후 섹션 없음.
   - 종료(`phase == ended`, `:109-129`): 종료 요약 → 귀가 → 공식 인스타그램 → 내 크루 → 팔로우한 깃발(`flagsEnabled != false`일 때).
   - 그 밖(`:130-203`): 마지막 1시간이면 귀가 → (팩 로딩 중이면 보드 스켈레톤, 아니면 일정 카드) → 내 일정 실패 줄 → 카운트다운이 없으면 "지금 / 다음 무대" → 공식 인스타그램 → 내 크루 → (`flagsEnabled != false`) 팔로우한 깃발 · 활성 깃발.
   - 내 일정 실패 줄(`:143-155`): "내 일정을 불러오지 못했습니다." + 버튼 "다시 시도"(`baseCaption1Bold`), 간격 `paddingP8`, `baseCaption1Regular` `labelNeutral`.

### 일정 카드 (`:358-416`)

- 카운트다운이 있으면 `FCCountdownCard(label = "개장까지", time, sub)`.
- 다음 공연 카드가 있으면 `FCNextSetCard(artist, meta, remain, updated, progress, label)`. 없고 팩 문서도 없고 상세 실패면 `FCEmptyCard(ic_music_24, "시간표를 불러오지 못했어요", "네트워크를 확인하고 다시 시도해 주세요.")` + 버튼 "다시 시도"(`baseCaption1Bold`, 간격 `paddingP8`). 그 밖은 `FCEmptyCard(ic_music_24, "다음 공연이 없습니다", 팩 없음 ? "시간표를 불러오면 다음 공연을 볼 수 있어요." : "공식 시간표에 예정된 공연이 없습니다.")`.

### 지금 / 다음 무대 (`FestivalLiveBoardView.swift:11-105`)

- 간격 `paddingP16`: `FCSectionHeader("지금 / 다음 무대", actionTitle = updated)`(수신 기준 상대 시각, 없으면 숨김).
- 행 없음: "현재 진행 중이거나 예정된 공연 정보가 없습니다." `baseSubheadlineRegular` `labelNeutral`, 패딩 `paddingP20`, 배경 `backgroundElevated` 반경 `r16`.
- 행: 한 카드(`backgroundElevated`, 반경 `r16`) 안에 행 사이 1dp `lineNormal`. 행 = 세로 간격 `paddingP12`, 패딩 상하 · 좌우 `paddingP16`, 내 일정이면 배경 `accentBackgroundLime`. 윗줄 [무대 이름 `baseFootnoteBold` `labelNeutral` · "내 일정" `FCTag`(lime)], 아랫줄 간격 `paddingP8` [지금 슬롯 · `ic_chevron_right_16` 16 `labelAssistive` · 다음 슬롯]. 슬롯 = `FCLiveBadge(Now|Next)` + (간격 `paddingP4`) 아티스트(지금 `baseBodyBold` / 다음 `baseCalloutMedium`, `labelNormal`) · 시각 `baseCaption1Regular` `labelAssistive`, 1줄.
- 출처 줄: `FCSourceLine(source, isInset = false)`. 문구 "출처: {이름} · 확인 MM/dd HH:mm"(확인 시각 없으면 "출처: {이름}", 이름 없으면 줄 없음, `Presentation:420-424`). stage 예: "출처: 공개 수집 카탈로그 · 확인 11/01 12:08"(`verifiedAt 2026-11-01T03:08:48Z` → Asia/Seoul).

### 공식 인스타그램 (`FestivalLiveBoardOfficialNewsSection.swift:22-131`)

- `available && posts` 있을 때만. 간격 `paddingP16`: `FCSectionHeader("공식 인스타그램")` + 가로 스크롤(막대 없음, 잘림 없음 `scrollClipDisabled`, 끝 `paddingP16`) 카드 간격 `paddingP12`.
- 카드 156 폭, 패딩 `paddingP12`, 배경 `backgroundElevated` 반경 `r16`, 간격 `paddingP8`: 이미지 높이 132 반경 `r12`(없으면 `fillStrong` + `ic_music_24` 24 `labelAssistive`, 실패 자리표시도 `ic_music_24` 24) · 캡션(없으면 "새 게시물") `baseFootnoteRegular` `labelNormal` 2줄 · 게시 시각(상대 시각, 없으면 `MM/dd HH:mm`) `baseCaption1Regular` `labelAssistive`. 접근성 힌트 "인스타그램에서 게시물을 열어요". 탭 = 원본 게시물 열기(Custom Tabs, 결정 D10).

### 내 크루 (`FestivalLiveBoardCrewSection.swift:41-88`, `FestivalLiveBoardCards.swift:6-122`)

- 비로그인: 로그인 카드 `FCListRow("크루와 함께 보려면 로그인")` 배경 `backgroundElevated` 반경 `r16`(탭 = 경계).
- 크루 있음: 카드(`backgroundElevated`, `r16`) — 제목 "내 크루 · {이름}" `baseHeadlineBold` · 부제 "{N}명 · {M}명이 {존} 근처"(가장 많은 존, 동수면 이름 앞선 쪽, 존 없으면 "{N}명") `baseFootnoteRegular` `labelAssistive`, 1줄, 패딩 위 16 · 좌우 16 · 아래 8, "모여!"가 보이면 오른쪽 76 비움. 오른쪽 위 `FCButton("모여!", style = Inverse, size = Medium)` 패딩 16, 행사 종료 전에만 보이고 오프라인이 아니고 `gatherAvailableAt <= now`일 때만 켬(탭 = 경계).
- 멤버 행: 아바타 색 순환 blue · pink · orange · green · purple · yellow(라이트 톤 고정), 간격 `paddingP16`, [닉네임 `baseCalloutBold` + "나" `FCTag`] / [존 칩(탭 = 경계) · 상태 `baseFootnoteBold`], 오른쪽 상대 시각 `baseCaption1Regular`(귀가 중이면 `labelDisable`, 아니면 `labelAssistive`). 패딩 상하 `paddingP12` 좌우 `paddingP16`, 귀가 중이면 불투명도 0.6, 이름 색 `labelAssistive`. 상태 문구 here "나 여기" · eating "밥" · toilet "화장실" · leaving "귀가 중"(`Presentation:494-502`).
- 카드 탭(모여! 밖) = 크루 화면(경계), 접근성 동작 "크루 화면 열기".
- 수신 전: 스켈레톤 `Round(r16)` 높이 228 "내 크루를 불러오는 중". 실패: `FCEmptyCard("내 크루를 불러오지 못했어요", 메시지)` + "다시 시도". 크루 없음: `FCEmptyCard("아직 크루가 없어요", "친구를 초대하면 서로 어느 존에 있는지 바로 볼 수 있어요.")` + `FCCardAction("크루 만들기", Primary)`(경계).
- 메시지: 실패 "실시간 정보를 불러올 수 없습니다. 잠시 후 다시 시도합니다.", 로딩 "실시간 정보를 불러오는 중입니다."(세 섹션 공통).

### 팔로우한 깃발 (`FestivalLiveBoardFollowedFlagSection.swift:36-121`)

- 섹션 숨김: 로그인 ∧ 수신 ∧ 팔로우 없음 ∧ 안내 숨김. 그 밖은 `FCSectionHeader("팔로우한 깃발")` + 간격 `paddingP16` 아래:
  - 비로그인: 로그인 카드 "깃발을 팔로우하려면 로그인"(경계)
  - 팔로우 있음: 가로 스크롤 타일 간격 `paddingP12` — 타일 156 폭 · 최소 높이 204 · 패딩 `paddingP12` · `backgroundElevated` `r16` · 간격 `paddingP8`: 썸네일 높이 88 `r12`(없으면 `fillStrong` + `ic_flag_20` 20) · [이름 `baseCalloutBold` 1줄 · 상태 칩] · 존 칩(터치 없음) · [성향 태그 `baseFootnoteRegular` `labelNeutral` · 시각 `baseCaption1Regular`]
  - 수신 전 스켈레톤 높이 204 "팔로우한 깃발을 불러오는 중", 실패 `FCEmptyCard(ic_flag_20, "팔로우한 깃발을 불러오지 못했어요")` + "다시 시도"
  - 안내: `FCEmptyCard(ic_flag, "만나보고 싶은 깃발이 있나요?", "팔로우하면 깃발이 움직일 때 조용히 알려 드려요.")` + `FCCardAction("깃발 찾기", Primary)`(경계) + 오른쪽 위 닫기 `ic_xmark_16` 16 `labelAssistive` 터치 44×44 라벨 "깃발 안내 숨기기"(`FCEmptyCard.onClose` 사용).
- 종료 단계면 모든 상태 칩 = `EndedToday`.

### 활성 깃발 (`FestivalLiveBoardActiveFlagSection.swift:29-96`, `FestivalLiveBoardCards.swift:177-219`)

- `FCSectionHeader("활성 깃발", actionTitle = 개장 전 ∧ 팩 있음 ? "도착 예정 순" : "최근 업데이트 순")`.
- 행 카드(`backgroundElevated` `r16`): 썸네일 40×40 `r12` · [이름 `baseCalloutBold` · 메타 "{존} · {활동}" `baseFootnoteRegular` `labelAssistive`] · 오른쪽 [상태 칩 · 시각]. 패딩 상하 `paddingP12` 좌우 `paddingP16`.
- 정렬: 최근 업데이트 내림차순(같으면 ID 오름차순, `Presentation:92-100`), 개장 전은 공개 계획 중 `now` 이후 가장 이른 공연 시작 순 → 계획 없는 깃발은 뒤에 최근 순(`:104-139`).
- 빈: `FCEmptyCard(ic_flag_20, "활동 중인 깃발이 없어요", "깃발이 움직이면 여기에서 바로 보여 드려요.")`, 수신 전 스켈레톤 228 "활성 깃발을 불러오는 중", 실패 "활성 깃발을 불러오지 못했어요" + "다시 시도".
- 태그 · 활동 문구: slam "슬램" · sing "떼창" · train "기차놀이" · calm "차분" · newbie "초보 환영", 그 외(`resting` 등) 표시 안 함(`:505-514`). 상태: active · resting · ended → 칩 Active · Resting · EndedToday, 그 외 칩 없음.

### 종료 요약 · 귀가 (`FestivalLiveBoardScreen.swift:418-490`)

- 귀가: 귀가 정보가 있으면 `FCHomeCard(remain = "{막차|셔틀}까지 N분", shuttle = "마지막 셔틀 HH:mm · {정류장}(도보 N분)", train = "막차 HH:mm · {역}(도보 N분)", actions = ["가는 길"(Ink), "같이 귀가"(InkOutline)])`(두 동작 모두 경계, 결정 D6). 불러오는 중이면 스켈레톤 `Round(r16)` 높이 204 "귀가 정보를 불러오는 중". 출발편 선택 · 시각 해석은 `FestivalHomeDeparture.swift:21-123`(지금 걸어 닿는 막차 → 셔틀 → 첫 미출발편, `HH:mm`은 18시 이후 06시 전 값이면 다음 날).
- 종료 요약: 로그인 ∧ 내 일정 ∧ 라이브 ∧ 팩이면 `FCDoneCard("오늘 재미있게 즐겼나요?", sub = "{축제} DAY n · HH:mm–HH:mm", stats = [본 공연, "{N}명" 함께한 크루, 팔로우한 깃발])`. 본 공연 = 마지막 공연 날과 같은 날 · 끝난 · 내 공연 수(`ScreenModel:91-111`). 로그인 + 내 일정 실패 / 라이브 실패면 `FCEmptyCard("오늘의 기록을 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.")` + "다시 시도", 로그인 + 대기면 스켈레톤 164 "오늘의 공연 요약을 불러오는 중". 비로그인이면 없음.

### 보드 스켈레톤 (`FestivalLiveBoardSkeletonView.swift:5-22`)

간격 `paddingP24`: `Round(r16)` 높이 144 → 간격 `paddingP12` [`Round` 120×22, `Round(r16)` 높이 88 × 2]. 자식 접근성 무시, 라벨 "라이브 보드를 불러오는 중", 테스트 태그 `festivalLiveBoardSkeleton`(`iOS/Feature/Festival/Sources/Accessibility/FestivalAccessibilityID.swift:12`).

### 축제 바꾸기 시트 (`FestivalSwitcherView.swift:13-155`)

- `FCSheet`(Auto, grabber) 안 배경 `backgroundElevated`, 좌우 `paddingP20`, 아래 `paddingP8`.
- 머리: "페스티벌 바꾸기" `baseTitle3Bold`(헤딩) · 간격 `paddingP4` "내가 예매한 페스티벌" `baseFootnoteRegular` `labelAssistive`, 아래 `paddingP12`.
- 목록 높이 `min(개수 × 68, 204)` 스크롤: 행 높이 68, 간격 `paddingP12` [포스터 44×44 `r12` · (간격 `marginM2`) 이름 `baseCalloutMedium` 1줄 · 부제 `baseFootnoteRegular` `labelAssistive` 1줄 · 선택이면 `ic_check` 20]. 라벨 "{이름}, {부제}", 선택 상태, 힌트 "페스티벌 라이브 보드로 이동합니다".
- 구분선 1dp `lineNormal` 상하 `paddingP8`, "전체 페스티벌 보기" `baseBodyRegular` + `ic_chevron_right_16` `labelNeutral`, 높이 56, 힌트 "페스티벌 목록으로 이동합니다".
- 항목 = 받은 팩이 있는 축제만, 목록 정렬 규칙(`FestivalListPresentation.sorted`)(`FF:970-992`). 부제: 오늘이 팩의 날이면 "오늘 · DAY n {시작 전|진행 중|종료}", 아니면 `[searchBadge, venue]`를 " · "로(`Presentation:250-270`). 제목이 "내가 예매한 페스티벌"이지만 iOS 실제 항목은 북마크가 아닌 받은 팩 기준이다(iOS 그대로 옮김, 위험 6).

### 팔로우 안내 숨김 다이얼로그 (`FF:1069-1075 · 660-667`)

`FCDialog(title = "이 안내를 숨길까요?", message = "이번 페스티벌 동안 라이브 보드에서 보이지 않아요.\n다시 보고 싶으면 마이 > 설정 > 라이브 보드에서 켜거나, 깃발 탭에서 언제든 깃발을 찾을 수 있어요.", confirm = "숨기기", textAlign = Center)`. 확인 = 그 축제 ID를 `FestiCore.hiddenFollowedFlagGuides`에 넣고 안내를 숨긴다.

### 토스트

보드 · 목록 공통: 팩 실패 `ic_circle_info_16` "팩을 받지 못했어요. 다시 시도해 주세요"(`FF:1708-1710 · 1723-1725`, 문구는 FestivalList 설계 토스트 절과 같음), 2초.

## 상태 (`LiveBoardContract.kt`)

| 필드                                                                                                  | iOS                                                             | 뜻                                         |
| ----------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- | ------------------------------------------ |
| `festivalId` · `festival: FestivalOverview?`                                                          | `selectedFestivalID` · `selectedFestival`(`FF:46-48 · 172-174`) | 보드에서 바꿀 수 있음(시트)                |
| `selectedPack: FestivalPackDocument?` · `onlineDetail` · `onlineDetailLoading` · `onlineDetailFailed` | `FF:56 · 60-63`                                                 | 표시 문서 = `onlineDetail ?: selectedPack` |
| `connection: Loading/Cached/Connecting/Reconnecting/Live/Offline/Unavailable` · `packWatchFailed`     | `FF:128 · 45 · 361-369`                                         | 팩 감시 상태                               |
| `snapshot` · `isUnavailable` · `isUnpublished` · `clockAnchorAt` · `receivedAt`                       | `FestivalLiveBoardFeature.State:13-19`                          | 라이브 상태                                |
| `officialNews` · `officialNewsFestivalId`                                                             | `:25-34`                                                        | 다른 축제 것이면 숨김                      |
| `hasSession` · `sessionUserId` · `accountGeneration`                                                  | `FF:107 · 113-115`                                              | 응답 세대 검사                             |
| `mySchedule: Set<String>?` · `myScheduleUnavailable`                                                  | `FF:116-117`                                                    | 결정 D5                                    |
| `homeDirections` 로딩 상태                                                                            | `FF:57 · 346-350`                                               | 받은 팩의 `directions`                     |
| `isFollowedFlagGuideHidden`                                                                           | `FF:103 · 123-126`                                              |                                            |
| `switcher: List<SwitcherItem>?` · `followGuideDialog: Boolean`                                        | `FF:118 · 120`                                                  | 시트 · 다이얼로그(탭 바 숨김 판단에 씀)    |
| `isVisible` · `isForeground`                                                                          | `FF:105 · 130`                                                  | 폴링 시작 조건                             |
| `toast`                                                                                               | `FF:54-55`                                                      |                                            |

화면 계산 값(`LiveBoardScreenModel`, `ScreenModel:40-111`): `showsOfflineStatus`, `isPackLoading`(`FF:287-295`: 표시 문서 없고 상세 로딩 중 → true, 상세 실패 → false, 저장 팩은 있는데 문서 아직 없고 감시 실패 아님 ∧ connection ∈ {Loading, Connecting}), `packDownload`, `homeRide(now)`, `doneSummary(now)`, `bearerFlagId`(`FF:177-180`).

공유 상태(Repository `StateFlow`, 결정 D1): 받은 팩 `Map<id, CachedFestivalPack>`, 받는 중 `Map<id, Progress?>`, 선택 축제 ID.

## 동작 (`LiveBoardViewModel`)

| iOS Action                                                                                                                                                                  | Android                                                      | 내용                                                                                                                                                                                                                                                                          |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `liveBoardOnAppear` · `onlineDetailOnAppear`(`FF:893-911`)                                                                                                                  | `onAppear()`                                                 | 보이는 중 켬, 안내 숨김 다시 읽기, 폴링 시작 · 내 일정 · 귀가 정보(팩 있을 때) · 상세 조회                                                                                                                                                                                    |
| `liveBoardOnDisappear`(`:913-925`)                                                                                                                                          | `onDisappear()`                                              | 폴링 · 공식 소식 · 상세 취소                                                                                                                                                                                                                                                  |
| `foregroundChanged`(`:722-743`)                                                                                                                                             | `onForegroundChanged(Boolean)`                               | 백그라운드: 팩 감시 · 폴링 중지. 포그라운드: 감시 실패 해제 후 팩 감시 · 폴링 · 내 일정 재시작(`resumeForeground` `:1239-1247`)                                                                                                                                               |
| `selectFestival`(`:785-812`)                                                                                                                                                | `onFestivalSelected(id)`                                     | 축제 ID 교체, 보드 · 상세 · 내 일정 · 귀가 초기화 후 저장 팩 표시 → 선택 저장 · 상세 · 팩 감시 · 폴링 재시작(`:1252-1263`)                                                                                                                                                    |
| `backToFestivalList`(`:814`)                                                                                                                                                | `SideEffect.BackRequested`                                   | Coordinator `goBack`                                                                                                                                                                                                                                                          |
| `festivalSwitchTapped`(`:970-992`)                                                                                                                                          | `onSwitchTapped()`                                           | 받은 팩 축제로 시트 항목 생성                                                                                                                                                                                                                                                 |
| 시트 `festivalSelected` / `seeAllFestivals`(`:586-591`)                                                                                                                     | `onSwitcherFestivalChosen(id)` / `onSeeAllFestivals()`       | 시트 닫고 선택 변경 / 목록으로                                                                                                                                                                                                                                                |
| `retryLiveBoard`(`:930-938`) · `retried`(`FestivalLiveBoardFeature:179-181`)                                                                                                | `onRetryLiveBoard()`                                         | 보이는 중일 때만, 실패 해제 후 폴링 재시작                                                                                                                                                                                                                                    |
| `retryMySchedule`(`:927-928`)                                                                                                                                               | `onRetryMySchedule()`                                        |                                                                                                                                                                                                                                                                               |
| `watchRequested`(`FestivalLiveBoardFeature:273-311`)                                                                                                                        | `private watchLiveBoard()`                                   | 무한 반복: 조회 → 성공 `onLiveBoardLoaded` / 미공개 `onUnpublished` / 429 `max(35, retryAfter)` 대기(상태 유지) / 그 외 `onLiveBoardFailed`, 35초 대기. 새 시작은 이전 작업 취소                                                                                              |
| `watchOfficialNews`(`:315-337`)                                                                                                                                             | `private watchOfficialNews()`                                | 300초마다, 실패 무시, 바뀐 값만 반영(처음 기준 `unavailable`)                                                                                                                                                                                                                 |
| `liveBoardLoaded`(`:226-239`)                                                                                                                                               | `onLiveBoardLoaded`                                          | 축제 · 세대가 같을 때만. `serverAt`이 바뀌었거나 기준점이 없으면 기준점 = 지금, 실패 · 미공개 해제, 수신 시각 = 지금                                                                                                                                                          |
| `liveBoardUnpublished`(`:241-251`)                                                                                                                                          | `onUnpublished`                                              | 스냅샷 · 기준점 · 수신 시각 비움                                                                                                                                                                                                                                              |
| 팩 `packReceived`(`FF:1573-1617`)                                                                                                                                           | `onPackReceived`                                             | updated/notModified: 저장 · 문서 해석 성공이면 Live(실패면 문서 없음 → Unavailable, 있음 → Cached), 팩 내용이 바뀌었으면 라이브 즉시 재조회(`refreshLiveBoard` `:2189-2203`) · 귀가 다시 읽기. retryAfter → 문서 없으면 Connecting 있으면 Cached, reconnecting → Reconnecting |
| `packFailed`(`:1645-1650`)                                                                                                                                                  | `onPackFailed`                                               | Offline, 감시 실패                                                                                                                                                                                                                                                            |
| `onlineDetailLoaded/Failed`(`:1620-1643`)                                                                                                                                   | `onOnlineDetailLoaded/Failed`                                | 문서 ID가 다르면 실패 처리                                                                                                                                                                                                                                                    |
| `hideFollowGuideTapped` · 다이얼로그 확인(`:1069-1075 · 660-667`)                                                                                                           | `onHideFollowGuideTapped()` · `onHideFollowGuideConfirmed()` |                                                                                                                                                                                                                                                                               |
| `officialNewsPostTapped`(`FestivalLiveBoardFeature:214-217`)                                                                                                                | `SideEffect.OpenUrl(url)`                                    | Custom Tabs                                                                                                                                                                                                                                                                   |
| `sessionChanged`(`FF:1151-1185`) · `accountChanged`(`:1127-1149`)                                                                                                           | `onSessionChanged(hasSession, userId)`                       | 세대 +1, 보드 · 내 일정 비우고 폴링 재시작(세션 ETag 캐시도 서비스가 비움)                                                                                                                                                                                                    |
| 경계 액션 전부(`notificationTapped` · `zoneChipTapped` · `crew*` · `followLoginTapped` · `findFlagTapped` · `bearerTapped` · `homeDirectionsTapped` · `homeTogetherTapped`) | `SideEffect.Boundary(...)` → Coordinator 콜백                | 1차는 Coordinator가 받아 두기만                                                                                                                                                                                                                                               |

목록 ViewModel 추가(팩 받기, `FF:821-831 · 1265-1302 · 1652-1725`): `onDownloadPack(id)` — 포그라운드 ∧ 받는 중 아님 ∧ `packVersion > 0` ∧ 받은 버전 < 발행 버전일 때만, 진행 이벤트 reset → 진행률 null, progress → 비율, finished updated/notModified → 공유 팩 갱신, retryAfter/reconnecting/오류 → 실패 토스트. 세대가 바뀌면 이벤트 버림. 백그라운드 진입(`backgrounded` `:765-768`)이면 받는 중 작업 전부 취소. 라이브 탭 선택 시 저장 팩 다시 읽기(`reloadCachedPacks`, FestivalList 설계 동작 표 마지막 줄).

## 오류 · 빈 · 로딩 · 오프라인 상태

| 상황                       | 화면                                         |
| -------------------------- | -------------------------------------------- |
| 선택 축제를 못 찾음 · 로딩 | Back 상단 바 + 보드 스켈레톤                 |
| 선택 축제를 못 찾음 · 끝   | "페스티벌 정보를 찾을 수 없어요"             |
| 표시 문서 없음 · 상세 로딩 | 일정 자리 보드 스켈레톤                      |
| 문서 없음 · 상세 실패      | "시간표를 불러오지 못했어요" + 다시 시도     |
| 라이브 첫 수신 전          | 크루 · 팔로우 · 활성 깃발 스켈레톤           |
| 라이브 실패(미공개 아님)   | 세 섹션 실패 카드 + 다시 시도, 오프라인 배지 |
| 라이브 429                 | 보이던 값 유지, 배지 없음, 대기 후 재조회    |
| `FESTIVAL_NOT_FOUND`       | 미공개 카드만, 배지 없음                     |
| 팩 감시 재접속 · 오프라인  | 저장 팩으로 그림 + 오프라인 배지             |
| 팩 받기 실패               | 토스트, 버튼 원래 상태                       |

오류 매핑: `/live`의 404 본문 `code == "FESTIVAL_NOT_FOUND"` → 미공개(상태 코드보다 본문 우선, `NetworkFestivalLiveBoardService.swift:96-99`), 304 + 세션 캐시 → 이전 값, 401 → `NetworkError.Http`(토큰 갱신은 기존 `service/auth` 경로), 429 → `RateLimited(retryAfter)`, 그 외 → 실패. 공식 소식 실패는 화면에 알리지 않는다.

## 서버 계약

2026-11-22 stage(`https://festicore-dev.up.railway.app/`)에서 **인증 없는 GET만** 실행해 확인했다. WebSocket은 연결하지 않았다(지시 범위) — 주소 · 메시지는 iOS 코드 근거.

| 요청                                              | 인증 · 헤더                                                                       | stage 확인                                                                                                                                                                                                                          | iOS 근거                                                                                          |
| ------------------------------------------------- | --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `GET api/v1/festivals/{id}`                       | 없음, 캐시 무시, 재시도 0                                                         | ✓ 200 35KB, `cache-control: public, max-age=60`, 약한 ETag. 없는 ID → 404 `FESTIVAL_NOT_FOUND` "축제를 찾을 수 없습니다."                                                                                                           | `FestivalDetailRequest.swift:6-16`                                                                |
| `GET .../{id}/live`                               | `X-Device-Id`(UUID) 필수, 로그인 시 Bearer + `If-None-Match`, 캐시 무시, 재시도 0 | ✓ 헤더 없음 → 400 `VALIDATION` "X-Device-Id UUID가 필요합니다." 임의 UUID → 200, `cache-control: private, max-age=30`, 강한 ETag, `If-None-Match` → 304. 없는 축제 → 404 `FESTIVAL_NOT_FOUND` "공개된 페스티벌을 찾을 수 없습니다." | `FestivalLiveBoardRequest.swift:6-23`, `NetworkFestivalLiveBoardService.swift:37-129`             |
| `GET .../{id}/flags`                              | 없음, 캐시 무시                                                                   | ✓ 200 9건, `max-age=600`                                                                                                                                                                                                            | `FestivalLiveBoardRequest.swift:25-35`                                                            |
| `GET .../{id}/instagram`                          | 없음, 재시도 0                                                                    | ✓ 200 `available:false`, `account{username,url}`, `fetchedAt:null`, `posts:[]`. 게시물 있는 응답은 **확인 못 함**                                                                                                                   | `FestivalOfficialNewsRequest.swift:14-23`                                                         |
| `GET api/v1/me/schedule/{id}`                     | Bearer                                                                            | 인증 필요라 실행 안 함                                                                                                                                                                                                              | `NetworkFestivalScheduleService.swift:98-99`                                                      |
| WebSocket `wss://{host}/api/v1/festivals/updates` | 없음, handshake 재시도 0                                                          | 실행 안 함                                                                                                                                                                                                                          | `FestivalPackSocketRequest.swift:11-29`, `FestivalAPI.swift:22-23`, `NetworkClient.swift:177-194` |

**`/live` 응답**(stage, 비로그인): `{festivalId, setOverrides:[], activeFlags:[{flagId,status,zoneId,activity,updatedAt}], me:null, serverAt}`. `status`는 active · resting, `activity`는 resting · slam · train · calm · sing. `setOverrides`는 iOS DTO에 없다(무시, `FestivalLiveBoardResponseDTO.swift:6-11`). 로그인 시 `me{following[], crew?, pinnedFlagId?, bearerOf?}`는 **확인 못 함**(iOS DTO `:21-67` 기준). `serverAt`이 없으면 iOS는 응답을 버린다(`FestivalLiveBoardDataDTO+.swift:11-13`), 활성 깃발 `updatedAt`이 없어도 버린다(`:18-20`). 깃발 프로필과 병합: 이름 없으면 flagId, 썸네일은 이미지 주소 결합 규칙, 활성 목록이 50개 미만이면 빠진 깃발 상태 "scheduled", 아니면 "unknown"(`:27-33 · 49`).

**`/flags` 원소**: `{id,name,thumbUrl,photoUrl,tags[],bio,followerCount,planVisible,plan[],homeZoneId}`. iOS는 `photoUrl · bio · planVisible`을 읽지 않는다(`FestivalLiveBoardResponseDTO.swift:75-114`), `tags`의 문자열 아닌 원소 · `plan` 형식 오류는 버린다.

**상세(팩 문서)**: stage 키 `id,name,venue,timezone,eventPeriod,flagsEnabled,packVersion,packUpdatedAt,rules,mapImageUrl,mapAspect,timetableStatus,thumbUrl,ticketLinks,days,source,stages,sets,artists,timetable,zones,mapStages,mapVersion,flags,directions,checklist`. iOS `FestivalPackDocument`는 `id,name,venue,thumbUrl,eventPeriod,days,artists,timetable,sets,stages,flagsEnabled,zones,source,ticketLinks`만 읽는다(`FestivalPackDocument.swift:3-43`). `days[{date,startsAt,endsAt}]`(ISO +09:00), `timetable[{id,stageId,stageName,artist,startsAt,endsAt,imageUrl,note,date}]` 42건, `zones[{id,name,kind,x,y,order,area,landmark}]`, `directions{shuttle{stopName},transit{stationName,walkMinutes},lastShuttleAt,lastTrainAt,…}`. 공연 시각은 정규식 `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$`만 유효(`FestivalLiveBoardSchedule.swift:152-157`).

**팩 WebSocket 메시지**(iOS `PackWire.swift` · `NetworkPackSyncService.swift`):

| 방향 | event                                                            | data                                                                                                            | 규칙                                                                                                            |
| ---- | ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| →    | `pack.get`                                                       | `{festivalId, ifNoneMatch?}` + `requestId`(소문자 UUID)                                                         | 전체 1024바이트 이하(`PackWire.swift:17-26`)                                                                    |
| ←    | `pack.snapshot`                                                  | `{festivalId, packVersion>0, etag(1~128B), pack{…}}`                                                            | 팩 JSON 8MB 이하(`PackWire.maxPackBytes`)이지만 메시지 자체가 4MB 이하여야 하므로(`PackWire.swift:29`) 스냅샷은 사실상 봉투 포함 4MB 이하. 4MB를 넘는 팩은 아래 청크 전송으로만 받는다 — iOS와 같은 동작, Android도 같은 두 한도를 그대로 둔다, `pack.id == festivalId ∧ pack.packVersion == packVersion`(`:41-53 · PackCache.swift:247-253`) |
| ←    | `pack.start` → `pack.chunk{index, base64}` × N → `pack.complete` | start `{festivalId, packVersion, etag, totalBytes(≤8MB), chunkCount = ceil(total/48KB), sha256(64 hex 소문자)}` | 청크 크기 `min(48KB, 남은 바이트)`, 순서 · requestId 일치, 끝에서 sha256 · 팩 검증(`:55-98`)                    |
| ←    | `pack.not_modified`                                              | `{festivalId, packVersion, etag}` 저장 값과 같아야 함                                                           | 확인 시각 갱신(`NetworkPackSyncService.swift:276-289`)                                                          |
| ←    | `pack.retry`                                                     | `{festivalId, retryAfterMs>0}`                                                                                  | `retryAfter`                                                                                                    |
| ←    | `pack.error`                                                     | `{code, retryAfterMs?}`                                                                                         | `PACK_BUSY` + ms → `retryAfter`, 그 외 오류(`:295-301`)                                                         |
| ←    | `pack.updated`                                                   | `{festivalId}`                                                                                                  | 다른 응답을 기다리는 중 오면 표시만 하고 계속, 감시 중이면 즉시 다시 `pack.get`(`:173-185 · 261-264`)           |

- 수신 메시지 4MB 이하 텍스트만, 응답 대기 15초, 감시 유휴 60초(조용한 만료는 오류 표시 없이 재접속), 재접속 지연 `min(30, 2^attempt)`초 · 성공 시 초기화(`ReconnectBackoff.swift:2-12`), handshake 거부 3회 연속이면 300초 뒤 재시도, 업데이트 힌트가 계속 오면 5번까지 다시 요청 후 `retryAfter(1)`(`:229-240`).
- 다운로드는 `retryAfter`를 최대 2번 기다리고(3번째 시도에서 `PACK_BUSY` 실패), 대기 합계 30초 예산(`:91-116`).
- 감시 중 연결이 끊기면 팩을 한 번이라도 받은 경우에만 `reconnecting`을 알린다(`:210-212`). 전송 실패가 아닌 오류는 감시를 끝낸다(`:198-201`).
- 축제 ID 문자 `[A-Za-z0-9_-]`, 128자 이하(`:331-334`).

**`GET api/v1/festivals/{id}/pack/version`**(✓ 200 `{packVersion, packUpdatedAt, etag, mapVersion}`, `no-cache`)과 `pack/changes?from=`(✓ `from` 없으면 400 "from은 1 이상 정수여야 합니다.")은 iOS 팩 상세 · 변경 요약용(`FestivalAPI.swift:9-21`)이라 이 문서 범위가 아니다.

## 캐시 · 저장

- 팩(iOS `PackCache.swift:6-308`): SQLite 표 `cachedFestivalPacks(festivalID PK, packVersion, etag, pack BLOB, sha256, checkedAt)` + 같은 내용 JSON 파일(`Application Support/FestiCore/Packs/{id}.json`, `{festivalID, packVersion, etag, pack(base64), sha256}`, `.atomic`). 읽기 시 sha256 · 팩 검증, 손상 항목만 목록에서 빼고 DB · 파일 접근 오류는 드러낸다, 더 낮은 버전으로 덮어쓰지 않는다, 확인 시각은 파일 수정 시각/DB 열.
- Android(결정 D2 추천): `service/pack`의 `PackCache`가 `filesDir/festicore/packs/{id}.json`에 iOS 파일과 같은 JSON(`pack`은 base64)을 `AtomicFile`로 쓴다. 확인 시각은 같은 JSON에 `checkedAt`(epoch ms)로 둔다. 검증 · 버전 역행 금지 · 손상 항목 제외 규칙은 iOS와 같다. sha256은 `java.security.MessageDigest`.
- 공유 상태: 앱 시작 시 `cachedPacks()`로 `StateFlow`를 채우고 저장 · 삭제 때 갱신한다. 목록의 `FestivalOverview.cachedPackVersion` · `isCached`는 이 흐름에서 합친다(iOS `festivals` `FF:151-170`, 팩만 있는 축제는 이름순 `Collator(KOREAN)`으로 뒤에 붙임).
- 선택 축제: DataStore `FestiCore.selectedFestivalID`. 초기 선택 = 현재 → 저장 값 → 받은 팩 → `packVersion > 0` → 첫 항목(`FF:2300-2307`).
- 안내 숨김: DataStore `FestiCore.hiddenFollowedFlagGuides`(문자열 집합).
- 라이브 ETag 캐시는 메모리만(세션별, 세션이 바뀌면 비움). 상세 · 깃발 · 공식 소식은 저장하지 않는다(iOS와 같음).
- 이미지(포스터 · 깃발 썸네일 · 인스타그램)는 Coil 디스크 캐시.

## 오프라인 정책

- 팩이 있으면 팩으로 그린다. 서버 실패는 팩 · 보이던 라이브 값을 지우지 않는다.
- 자동 복구: 팩 감시는 backoff 재접속, 라이브는 35초 반복이 그대로 재시도다. 수동: 섹션 "다시 시도", 포그라운드 진입.
- 연결 상태 감시(`ConnectivityManager`)는 두지 않는다(iOS도 요청 결과만 봄).

## 이미지 로딩

- 기존 `FCRemoteImage`(Coil 3)와 `FestivalImageUrlResolver` 규칙을 그대로 쓴다. 깃발 썸네일 · 포스터는 상대 경로를 `BASE_URL`과 결합, 인스타그램 `imageUrl`은 https 절대 주소만(`FestivalOfficialNewsDataDTO+.swift:42-45`), 게시물 링크는 `www.instagram.com` · `instagram.com` 호스트만(`:47-50`).
- 새 라이브러리 없음.

## 화면 전환

| 전환                      | iOS 방식                                                                                                | 근거                                                                         |
| ------------------------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 목록 → 보드               | push(새 `FestivalFeature` 상태에 선택 ID · 목록 항목 · 팩 · 세션 복사)                                  | `FCo:101-117`                                                                |
| 보드 → 목록               | `goBack`                                                                                                | `FCo:119-121`                                                                |
| 보드 뒤로 버튼            | 없음(`swipeBackButtonHidden`), 대신 왼쪽 24pt 가장자리 드래그(최소 25, 가로 > 80 ∧ > 세로 × 1.5)로 목록 | `FestivalCoordinatorView.swift:14-16`, `FestivalLiveBoardScreen.swift:29-42` |
| 축제 바꾸기 · 모여!       | `fcModal` 시트                                                                                          | `FestivalLiveBoardRouteView.swift:63-74`                                     |
| 안내 숨김 · 기타 알림     | `fcAlert`(안내 숨김은 가운데 정렬)                                                                      | `:75-85`                                                                     |
| 시트 "전체 페스티벌 보기" | 시트 닫고 `backToFestivalList` → pop                                                                    | `FF:586-591`                                                                 |
| 알림함 · 존 지도 · 크루   | push(경계)                                                                                              | `FCo:128 · 185 · 228`                                                        |
| 기수 보드                 | `presentCover`(경계)                                                                                    | `FCo:173`                                                                    |
| 토스트                    | 위에서 내려옴, `easeOut 0.2s`                                                                           | `FestivalLiveBoardRouteView.swift:47-62`                                     |

Android 설계:

- `FestivalRoute.LiveBoard(festivalId)`를 `interface`에 추가. `FestivalCoordinator.onFestivalSelected(id)`가 `push(FestivalRoute.LiveBoard(id))`, 보드 `BackRequested` · `onSeeAllFestivals` → `goBack()`. 같은 보드가 이미 위에 있으면 다시 쌓지 않는다.
- `FestivalFlow`에 `entry<FestivalRoute.LiveBoard> { key -> LiveBoardScreen(viewModel = koinViewModel { parametersOf(key.festivalId) }, ...) }`. 보드 ViewModel은 `rememberViewModelStoreNavEntryDecorator`로 항목 범위(pop 때 정리). 전환은 기존 `FestivalTransitions.push/pop`(350ms, 결정 D8은 FestivalList 설계와 같음).
- 뒤로: 시스템 뒤로 · 예측 뒤로를 `NavDisplay`에 맡긴다. iOS의 왼쪽 가장자리 드래그는 옮기지 않는다(Android 제스처 내비게이션의 가장자리 뒤로와 같은 동작이고, 따로 두면 시스템 제스처와 겹친다 — 결정 D7). 보드는 자기 `BackHandler`를 두지 않는다(`navigation.md` "화면 전환 애니메이션" 마지막 항목).
- 시트: `FCSheet(isPresented = state.switcher != null, onDismissRequest = 닫기)`(Material3 `ModalBottomSheet`). 시트가 열려 있으면 시스템 뒤로 = 시트 닫기(`ModalBottomSheet` 기본). 다이얼로그는 `FCDialog`, 뒤로 = 닫기.
- 수명 주기: `LifecycleEventEffect`(또는 `repeatOnLifecycle(STARTED)`)로 `onForegroundChanged`, 항목이 컴포지션에 들어오고 나갈 때 `onAppear` · `onDisappear`(`DisposableEffect`). 정확한 API 이름은 도입 PR에서 `lifecycle-runtime-compose` 문서로 확인.
- 30초 갱신: Content에서 `LaunchedEffect`로 30초마다 `deviceNow`를 갱신(iOS `TimelineView(.periodic(by: 30))` `:53-56`). `Clock`을 주입해 테스트에서 고정.

## 탭 바 표시

iOS(`MainTabCoordinator.swift:92-113`): 라이브 탭은 스택이 1개(목록)면 숨김, 알림함 · 크루 · 크루 참여 · 크루 만들기 · 준비 안내가 맨 위면 숨김, **보드에 알림창이나 바꾸기 시트가 떠 있으면 숨김**. 그 밖(보드 · 존 지도)은 보인다. 표시/숨김은 애니메이션 없이 즉시(`MainTabView.swift:50-54`). 모달이 열려도 탭 바 자리는 남겨 뒤 화면이 움직이지 않게 하고, 모달이 탭 바 자리까지 덮는다(`:64-75 · 151-164`).

Android: 지금 `MainTabScreen`은 `isLiveAtRoot`만 본다(`app/.../MainTabScreen.kt:36 · 47`). `FestivalFlow(onAtRootChanged)`를 `onHidesTabBarChanged(Boolean)`로 바꾸고 값 = `routes.size == 1 || (맨 위가 LiveBoard ∧ (switcher != null ∨ dialog 표시))`로 계산한다(보드 ViewModel이 시트/다이얼로그 여부를 Coordinator에 알림). 이후 문서의 화면(알림함 · 크루 · 준비 안내)은 그 문서에서 같은 함수에 더한다. `ModalBottomSheet`는 창 위에 뜨므로 탭 바 자리 보존 문제는 Android에 없다(위험 5).

## iOS → Android 적응

| 항목                  | iOS                                                                                                     | Android                                                                                                         | 이유                                                                                                                            |
| --------------------- | ------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| 화면 상태 소유        | 목록 · 보드가 각각 `FestivalFeature` 인스턴스, 팩 · 카탈로그를 각자 다시 읽음(`FF:714-720 · 1211-1224`) | `LiveBoardViewModel` + 공유 Repository `StateFlow`(팩 · 받는 중 · 선택)                                         | 두 화면이 같은 팩 진행률을 같은 값으로 보이게(D1)                                                                               |
| 뒤로                  | 뒤로 버튼 없음 + 왼쪽 24pt 드래그                                                                       | 시스템 뒤로 · 예측 뒤로                                                                                         | Android 표준 제스처(D7)                                                                                                         |
| 시트                  | `fcModal`                                                                                               | `FCSheet`(`ModalBottomSheet`)                                                                                   | designkit 기존                                                                                                                  |
| 알림창                | `fcAlert`                                                                                               | `FCDialog(textAlign = Center)`                                                                                  | designkit 기존                                                                                                                  |
| 포그라운드            | `scenePhase == .active`                                                                                 | `Lifecycle.State.STARTED`                                                                                       | Android 수명 주기                                                                                                               |
| 주기 갱신             | `TimelineView` 30초                                                                                     | `LaunchedEffect` 30초 + 주입 `Clock`                                                                            |                                                                                                                                 |
| 폴링                  | TCA `.run` + `continuousClock.sleep` + `cancelInFlight`                                                 | `viewModelScope` Job 교체(이전 Job 취소) + 주입 가능한 지연                                                     | 같은 취소 규칙                                                                                                                  |
| WebSocket             | Alamofire `WebSocketRequest`                                                                            | Ktor `WebSockets` 플러그인 + 기존 OkHttp 엔진(D3)                                                               | 기존 Ktor 스택 재사용                                                                                                           |
| 팩 저장               | SQLite(SQLiteData) + JSON 파일 백업                                                                     | JSON 파일 + `AtomicFile`(D2)                                                                                    | iOS의 이중 저장은 JSON → SQLite 이전 호환용(`SQLitePackCacheTests` "기존 JSON 팩을 SQLite로 옮기고…"). Android는 옛 자료가 없다 |
| 설치 ID               | Keychain `deviceID` UUID 소문자                                                                         | `SecureStorage` 키 `deviceID`, `UUID.randomUUID().toString()`                                                   |                                                                                                                                 |
| 해시 · base64         | CryptoKit SHA256 · `Data(base64Encoded:)`                                                               | `MessageDigest("SHA-256")` · `android.util.Base64`(minSdk 24라 `java.util.Base64`(API 26) 대신)                 | minSdk                                                                                                                          |
| 시각                  | `Date` · `formatted(.time)` 등                                                                          | `java.time.Instant` · `ZoneId.of("Asia/Seoul")` · `DateTimeFormatter("HH:mm" / "MM.dd (E)" ko / "MM/dd HH:mm")` | FestivalList 날짜 규칙과 같음                                                                                                   |
| 외부 링크(인스타그램) | `openURL`                                                                                               | Custom Tabs(`androidx.browser`, 이미 있음)                                                                      | FestivalList 예매처와 같음                                                                                                      |
| 아바타 라이트 톤      | `.environment(\.colorScheme, .light)`                                                                   | 아바타만 라이트 색 토큰으로 그리기(`FCAvatar` 지원 여부 PR에서 확인)                                            |                                                                                                                                 |
| 터치 피드백           | 없음                                                                                                    | Material 리플                                                                                                   | FestivalList와 같음                                                                                                             |
| 위치 · 지도           | Apple 지도 도보 계산 · 길찾기                                                                           | 1차 없음(서버 `transit.walkMinutes`만)                                                                          | 권한 흐름은 `core/location` 문서(D6)                                                                                            |

## PR 구현 순서

| PR                  | 내용                                                                                                                                                                                                                                                                                                                      | 검증                                                                                                                                                                                                                |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| L1 데이터           | `service/api` 경로 · DTO(상세 · live · flags · instagram · me/schedule), `service/device` 설치 ID, `service/festivalcatalog` 상세 · 라이브(ETag · 429 · 404 · 세션 캐시) · 소식 서비스, `domain/festival` 엔티티 · 매퍼 · `FestivalLiveBoardSchedule` · `FestivalLiveBoardUseCase` · `FestivalScheduleUseCase.mySchedule` | MockEngine: stage 응답 축약 자료로 DTO 해석, `FestivalLiveBoardMapperTests` · `FestivalOfficialNewsMapperTests` · Domain `FestivalLiveBoardScheduleTests`, 304 재사용 · 429 대기 · 404 미공개 · 세션 바뀜 캐시 비움 |
| L2 팩 저장 · 동기화 | `core/network` WebSocket 연결(https→wss), `service/pack`(`PackWire` · `PackCache` · `NetworkPackSyncService` · backoff), `domain/festival` `FestivalPackUseCase` + 공유 `StateFlow`, `core/datastore` 선택 · 안내 숨김 키                                                                                                 | 가짜 소켓으로 `PackSyncTests` 37건 중 해당 사례(재접속 ETag · 유휴 만료 · 청크 검증 · retry 예산 · 삭제 세대), 임시 폴더 `PackCache`(손상 항목 제외 · 버전 역행 금지)                                               |
| L3 목록 팩 받기     | `FestivalListViewModel` 팩 받기 · 진행 · 실패 토스트 · 백그라운드 취소, 카드 `FCFestivalCardPack.Downloading`, 팩 병합(`cachedPackVersion`)                                                                                                                                                                               | `FestivalPackDownloadFeatureTests` 11건 이식                                                                                                                                                                        |
| L4 보드 화면        | `LiveBoardPresentation` · `ScreenModel` · `HomeDeparture` · Contract · ViewModel · Screen/Content · 섹션 · 스켈레톤 · 시트 · 다이얼로그                                                                                                                                                                                   | `FestivalLiveBoardPresentationTests` · `EndCardsTests` · `OfficialNewsTests` · `SessionTests` · `FestivalOfflineTests`(보드) · `FestivalFeatureStateTests`(보드 계산 값) Orbit 테스트, 프리뷰                       |
| L5 연결             | `FestivalRoute.LiveBoard`, Coordinator push/pop · 경계 콜백, `FestivalFlow` 항목 · 수명 주기, `MainTabScreen` 탭 바 규칙, Koin · 팩 폴더 · `BASE_URL` 전달                                                                                                                                                                | `FestivalCoordinatorTest`(push · 중복 방지 · pop · 탭 바 값), stage 수동 확인 스크린샷(수용 기준 2 · 4 · 7)                                                                                                         |

각 PR은 `verify --all` · CI 통과 후 squash merge. L3은 L2 뒤, L4는 L1 · L2 뒤, L5는 L4 뒤.

### 기능 spec (`.spec/`)

| PR      | spec 파일                                              | 담는 내용                                                                       |
| ------- | ------------------------------------------------------ | ------------------------------------------------------------------------------- |
| L1      | `domain/festival/.spec/live-board.md`                  | 라이브 조회 · 깃발 병합 · 폴링 간격 · 429 · 미공개 · 서버 시각 보정 · 공식 소식 |
| L2      | `service/pack/.spec/pack-sync.md`                      | 메시지 · 검증 · 청크 · 재접속 · 저장 규칙                                       |
| L3      | `feature/festival/.spec/festival-list-screen.md`(갱신) | 팩 받기 · 진행 · 실패                                                           |
| L4 · L5 | `feature/festival/.spec/live-board-screen.md`          | 화면 · 단계(phase) · 섹션 · 시트 · 전환 · 탭 바                                 |

## 새 라이브러리 후보

| 후보                                                         | 용도         | 라이선스   | 판단                                                                                                                                                                                                    |
| ------------------------------------------------------------ | ------------ | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ktor `WebSockets` 클라이언트 플러그인(Ktor 3.6.1, 기존 버전) | 팩 WebSocket | Apache-2.0 | 추천. OkHttp 엔진이 WebSockets를 지원함을 Ktor 공식 문서 엔진 표로 확인(context7 `ktor-documentation` `client-engines.md`). 플러그인이 `ktor-client-core`에 들어 있는지 별도 아티팩트인지는 L2에서 확인 |
| OkHttp `WebSocket` 직접 사용                                 | 같은 용도    | Apache-2.0 | 대안. 콜백 API라 코루틴 래핑이 필요                                                                                                                                                                     |
| Room 2.8.5(`libs.versions.toml`에 이미 있음)                 | 팩 저장      | Apache-2.0 | 1차 미사용(D2)                                                                                                                                                                                          |

새 의존성 추가 없음(Ktor 플러그인은 기존 Ktor 일부). 라이선스 승인은 사람 확인 대상(CLAUDE.md 규칙 12).

## 위험

1. **iOS 보드 상태가 넓다**: 보드 `FestivalFeature`는 Live Activity · 알림 · 귀가 위치 · 크루 · 깃발까지 한 State에 둔다. 경계 액션을 콜백으로만 두면 다음 문서마다 `LiveBoardViewModel`에 상태가 다시 붙는다. 섹션별 상태를 Contract 안에서 묶어(`crew` · `flags` · `home`) 나중 문서가 한 묶음씩 채우게 한다.
2. **Android CursorWindow 2MB**: 팩은 8MB까지 허용된다(`PackWire.swift:14`). Room/SQLite BLOB으로 2MB 넘는 행을 읽으면 실패할 수 있다(플랫폼 기본 CursorWindow 크기 — L2에서 재확인). 파일 저장(D2)으로 피한다. stage 최대 팩 크기는 확인 못 함(상세 응답 35KB 확인).
3. **WebSocket 미검증**: stage WebSocket을 열지 않았다. 메시지 형식은 iOS 코드만 근거다. L2 이후 수동 확인에서 처음 실서버와 맞춘다.
4. **`X-Device-Id`**: 서버는 UUID 형식만 검사했다(임의 UUID 200). iOS도 `/live` 전에 등록하지 않는다. 서버가 나중에 등록된 기기만 받으면 `DEVICE_NOT_REGISTERED`(iOS 오류 `FestivalLiveBoardSnapshot.swift:193`)가 생길 수 있다.
5. **탭 바 자리**: iOS는 시트 동안 탭 바 자리를 남기거나 비우는 규칙이 복잡하다(`MainTabView.swift:151-164`). Android `ModalBottomSheet`는 창 위에 떠 같은 문제가 없지만, 시트 등장 시 탭 바가 즉시 사라지는지 실기기에서 iOS와 나란히 본다.
6. **시트 제목과 내용 불일치**: "내가 예매한 페스티벌"이지만 항목은 받은 팩 기준(`FF:971`). iOS 그대로 옮기고 이슈로 남긴다.
7. **세션 의존 섹션 미검증**: `me` · 크루 · 팔로우 · 내 일정 응답은 로그인이 필요해 stage에서 보지 못했다.
8. **목록 D2 대체**: L3 전까지 목록 "팩 받기"는 여전히 무동작이다(FestivalList D2 (a)). L3을 L2 바로 뒤에 둔다.

## 결정

**결정 필요**

- D1. 상태 분리: (a) `LiveBoardViewModel` 별도 + 팩 · 받는 중 · 선택은 Repository `StateFlow` 공유(추천), (b) iOS처럼 보드도 목록과 같은 ViewModel 형태 인스턴스를 따로 띄우고 각자 캐시를 읽음 — iOS와 같지만 같은 팩 진행률을 두 곳에서 따로 셈.
- D2. 팩 저장: (a) JSON 파일 + `AtomicFile`(추천, iOS 파일 형식과 같음, CursorWindow 위험 없음, Room 모듈 불필요), (b) Room `core/database`(`architecture.md` 대응표 "SQLiteData → Room"과 같음, 메타 표 + 파일 본문 분리 필요), (c) 둘 다(iOS와 같음, 이전 호환 이유가 Android엔 없음).
- D3. WebSocket: (a) Ktor `WebSockets` + OkHttp 엔진(추천), (b) OkHttp 직접.
- D4. 북마크(예매 완료): 이 문서에서 다루지 않는다(추천). 보드는 북마크를 쓰지 않고(바꾸기 시트도 팩 기준), iOS 북마크는 사용자별 로컬 저장 + 옛 키 이전 + 로그인 조건 흐름이 묶여 있다(`FestivalBookedStore+Live.swift:20-70`, `FCo:167-169`). 로그인 시트와 함께 "북마크 · 로그인 요청" 문서로 둔다. 대안: L6으로 이 문서에 붙임.
- D5. 내 일정 읽기(`GET api/v1/me/schedule/{id}`)를 L1에 넣는다(추천). 없으면 로그인 사용자의 종료 요약이 계속 스켈레톤이고 "내 일정" 표시가 사라진다. 저장(쓰기)은 Timetable 문서.
- D6. 귀가 카드: 서버 값(`transit.walkMinutes`)으로만 보이고 "가는 길" · "같이 귀가"는 경계(추천). 대안: 카드 자체를 위치 문서로 미룸.
- D7. 왼쪽 가장자리 드래그 뒤로를 옮기지 않고 시스템 뒤로에 맡긴다(추천). FestivalList D8의 "가장자리 스와이프 허용 여부" 질문은 iOS가 직접 드래그를 구현한 것으로 확인됨(`FestivalLiveBoardScreen.swift:29-42`).
- D9. 탭 바: 보드에서 보이고 시트 · 다이얼로그 동안 숨김(iOS와 같음, 추천).
- D10. 인스타그램 게시물 열기: Custom Tabs(추천). 대안: 인스타그램 앱 우선 `ACTION_VIEW`.
- D11. 알림 아이콘: 1차는 배지 없이 보이고 탭은 경계 콜백(추천). 대안: NotificationInbox 전까지 아이콘 숨김 — iOS와 달라짐.

**결정 사항**

- 경로 값은 `festivalId` 하나만(`navigation.md` 경로 규칙). 목록 항목 · 팩은 Repository에서 다시 읽는다(iOS는 상태를 복사, `FCo:103-115`).
- 폴링 35초 · 429 기본 35초 · 공식 소식 300초 · 시각 갱신 30초 · 응답 대기 15초 · 감시 유휴 60초 · 재접속 최대 30초는 iOS 값 그대로.
- 라이브 ETag는 로그인 세션이 있을 때만 보낸다(iOS `NetworkFestivalLiveBoardService.swift:60`). 비로그인 304도 stage에서 동작하지만 iOS와 같게 둔다.
- 경계 목록의 화면은 각 다음 문서에서 만든다. 이 문서는 콜백 이름과 Coordinator 진입 함수까지만 정한다.

## 확인 못 한 것

- TCAFlow `swipeBackButtonHidden()`의 정확한 의미(외부 패키지, 로컬 소스 없음). 보드는 자체 드래그를 두므로 동작 판단에는 영향 없음.
- iOS 시스템 push 시간 · 곡선(FestivalList D8과 같음).
- stage WebSocket 메시지 실제 형식 · 청크 크기 · 최대 팩 크기(지시 범위상 미실행).
- 로그인 응답(`me` · 크루 · 팔로우 · 기수 · 내 일정), 429 `Retry-After` 실제 값, 게시물 있는 인스타그램 응답.
- Android에 메인 탭에서 띄울 로그인 시트가 있는지(경계 콜백의 비로그인 분기 연결 대상) — FestivalList D3에서 북마크와 함께 미룸.
- Ktor `WebSockets` 플러그인 아티팩트 위치, Compose 수명 주기 API 이름, `FCAvatar` 라이트 톤 고정 지원 — 각 PR에서 공식 문서 · 소스로 확인.
- Android CursorWindow 기본 크기(위험 2) — L2에서 확인(D2 (b)를 고를 때만 영향).
