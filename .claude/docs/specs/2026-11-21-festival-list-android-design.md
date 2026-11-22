# 라이브 탭 축제 목록(FestivalList) Android 이식 설계

- 날짜: 2026-11-21
- 이슈: 미정(구현 이슈를 만들 때 적는다)
- 상태: 제안
- 범위: `feature/festival` · `domain/festival` · `service/api` · `service/festivalcatalog` · `core/datastore` · `ui/designkit`(작은 추가) · `ui/sharedui`(신규, 결정 D4) · `app`

## 목표

iOS 라이브 탭의 첫 화면 S00 페스티벌 목록과 그 화면이 쓰는 데이터 계층(카탈로그 조회 · 페이지 · 파일 캐시 · 이미지 주소 · 최근 검색)을 Android로 옮긴다. 화면은 stage 서버(`BuildConfig.BASE_URL`) 실데이터로 동작한다. 샘플 데이터는 프리뷰와 단위 테스트의 가짜 값만 쓴다.

시각 디자인(수치 · 문구 · 토큰 · 에셋 · 접근성 라벨)은 iOS View 코드와 같게 둔다. 플랫폼 관례(뒤로 가기 · 당겨서 새로고침 · 인셋 · 터치 피드백 · 외부 링크 · 날짜 처리)는 Android 방식을 따른다(아래 "iOS → Android 적응").

iOS 원본(`../FestiCore-iOS/Projects`, 이하 iOS):

- 화면: `Feature/Festival/Sources/FestivalList/View/FestivalView.swift:15-64`, `FestivalList/View/FestivalListView.swift:17-553`
- 화면 모델: `FestivalList/Model/FestivalListPresentation.swift:5-262`, `FestivalListInteractionState.swift:4-20`, `FestivalPackDownloadDisplay.swift:11-37`, `FestivalOverview+Mapping.swift:6-57`
- 하위 컴포넌트: `Feature/Festival/Sources/Components/FestivalPosterView.swift:4-27`, `FestivalList/Components/FestivalListSkeletonView.swift:4-61`, `FestivalList/Components/FestivalToastView.swift:7-35`, `Accessibility/FestivalAccessibilityID.swift:9-18`
- Reducer: `FestivalList/Reducer/FestivalFeature.swift` — State `19-171 · 286-320`, Toast `371-376`, View 액션 `709-884 · 1106-1125`, Async `1211-1262 · 1437-1445`, Inner `1454-1571 · 1899-1901`, 효과 `1909-1963 · 2125-2154`, `showsListSkeleton`·`uniqueCatalog`·이미지 값 `2284-2331`
- Interface: `Feature/Festival/Interface/Sources/FestivalDelegate.swift:11-32`
- 흐름: `App/Sources/Navigation/Festival/FestivalCoordinator.swift:15-20 · 99-121 · 482-500`, `FestivalCoordinatorView.swift:9-16`, `MainTab/MainTabCoordinator.swift:219-220 · 330-346`, `MainTab/MainTabCoordinator+Scope.swift:83-94`, `MainTab/MainTabView.swift:228-238`
- 도메인: `Domain/FestivalDomain/Interface/Sources/Entity/{FestivalOverview,FestivalCatalogItem,FestivalCatalogPage}.swift`, `Catalog/{FestivalCatalogRepositoryInterface,FestivalCatalogUseCaseInterface,FestivalCatalogService}.swift`, `Sources/Catalog/FestivalCatalogUseCaseImpl.swift`, `Sources/Repository/FestivalCatalogRepositoryImpl.swift`, `Sources/Model/DTO/FestivalListResponseDTO.swift:3-118`, `Sources/Model/Mapper/FestivalItemDTO+.swift:3-32`, `Sources/Library/FestivalLibraryUseCaseImpl.swift:12-42`, `Sources/RecentSearch/FestivalRecentSearchStore+Live.swift:19-55`, `Sources/Image/FestivalImageUseCaseImpl.swift`
- 서비스: `Service/FestiCoreFestivalCatalog/Sources/NetworkFestivalCatalogService.swift:6-81`, `URL/FestivalImageURLResolver.swift:3-16`, `Storage/FestivalCatalogCache.swift:4-16`, `Endpoint/FestivalListRequest.swift:5-22`, `Error/FestivalCatalogError.swift`, 조립 `Service/ServiceAssembly/Sources/NetworkContainer.swift:77-81 · 94-99`
- 팩(목록이 읽는 범위만): `Service/FestiCorePack/Interface/PackSyncService.swift:5-17 · 56`, `Sources/NetworkPackSyncService.swift:58-60`(SQLite `PackCache.loadAll`)
- 날짜: `Core/FestiCoreUtility/Sources/Date/AppDateFormat.swift:13 · 18-29`(`festivalDay = "M/d(E)"`, `ko_KR`, `Asia/Seoul`)

## 범위

iOS `FestivalFeature`는 목록과 라이브 보드 · 라이브 활동 · 알림 · 팩 받기를 한 Reducer(2,332줄)에 담는다. Android는 목록만 `FestivalListViewModel`로 떼어 옮기고, 라이브 보드는 다음 문서에서 별도 ViewModel로 옮긴다(결정 D1).

**1차(이 문서, PR F1~F4)**

1. 상단 바 "페스티벌"(large) + 검색 버튼, 탭 "전체" · "예매 완료", 안내 문구, 오프라인 배지
2. 축제 카드 목록: 포스터(원격 이미지) · 제목 · 메타(장소 · 날짜) · 상태 태그 · 팩 영역(표시만, 결정 D2) · 예매처 링크
3. 첫 진입: 파일 캐시를 상태에 읽어 두되 첫 서버 갱신이 끝날 때까지는 스켈레톤(iOS `showsListSkeleton`, `FestivalFeature.swift:2286-2288`) → 서버 첫 페이지(limit 10) → 끝에서 다음 페이지 · 당겨서 새로고침
4. 스켈레톤 · 빈 카드 · 실패 카드 · 다시 시도
5. 검색: 검색 바, 최근 검색(사용자별 10개), 다가오는 페스티벌, 페스티벌 · 아티스트 결과, 결과 없음
6. 예매처 링크 → Custom Tabs, 링크 없음 → 토스트 "예매처 정보가 없습니다"
7. 좌우 밀기로 탭 전환, 검색 중 시스템 뒤로 = 검색 닫기
8. 카드 · 결과 탭 → `FestivalCoordinator`에 축제 ID 전달까지. 라이브 보드 화면은 다음 문서
9. 라이브 탭 루트 연결(`MainTabScreen`)과 탭 상태 유지, 포그라운드 진입 시 목록 재조회

**미룸(이후 문서)**

| 항목                                                                                             | iOS 위치                                                                                                    | 미루는 이유                                              |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| 라이브 보드 · 축제 선택 저장(`saveSelectedFestivalID`) · 온라인 상세 `GET api/v1/festivals/{id}` | `FestivalFeature.swift:785-815 · 1250-1262`, `FestivalCoordinator.swift:101-117`                            | 다음 문서(LiveBoard)                                     |
| 북마크 토글 · 예매 완료 목록 채우기 · 토스트 담음/제외                                           | `FestivalFeature.swift:817-839`, `MainTabCoordinator+Scope.swift:86-94`, `SessionCoordinator.swift:468-484` | 로그인 · 가입 조건 · 대기 예약 흐름이 묶여 있음(결정 D3) |
| 팩 받기 · 팩 캐시(SQLite) · WebSocket                                                            | `FestivalFeature.swift:821-831 · 1265 · 1652-1725`, `Service/FestiCorePack/**`                              | `service/pack` 문서에서 함께 옮김                        |
| 라이브 활동 · 위젯 · 알림 배지 · 크루 · 준비 안내                                                | `FestivalFeature.swift:425-432 · 472-485` 등                                                                | Android 대응 별도 결정                                   |
| 분석 `screenView(festivalList)`                                                                  | `FestivalFeature.swift:880-885`                                                                             | `service/analytics` 단계                                 |
| 이미지 미리 받기(Kingfisher `ImagePrefetcher`, 100개 · 동시 3)                                   | `FestivalFeature.swift:2125-2139 · 2317-2330`                                                               | 화면에 보이는 것만 Coil로 불러도 동작은 같음(위험 4)     |
| 앱 안 예매 웹 화면(`WebFeature`, 제목 "티켓 예매", `.booking`)                                   | `FestivalFeature.swift:1106-1118`, `FestivalView.swift:19-24`                                               | `feature/web` 단계. 그 전에는 Custom Tabs                |

## 수용 기준

1. 첫 실행(캐시 없음): 스켈레톤 카드 3장 → stage 첫 페이지 10개가 진행 중 → 가까운 일정 → 날짜 없음 → 종료 순으로 보인다. 끝까지 내리면 다음 페이지를 불러 붙이고, 마지막 페이지까지 받으면 전체 목록을 파일에 저장한다.
2. 두 번째 실행(캐시 있음): iOS와 같이 첫 서버 갱신이 끝날 때까지 스켈레톤이 보이고, 갱신이 성공하면 서버 목록, 실패(오프라인 포함)하면 저장 목록이 오프라인 배지와 함께 보인다. 캐시는 "첫 갱신 실패 시 대체"와 "갱신 뒤 다시 실행할 때까지의 보관"에 쓰고, 첫 갱신 전 즉시 표시는 하지 않는다.
3. 비행기 모드: 저장 목록이 있으면 그대로 보이고 배지 "최신 페스티벌 목록을 불러오지 못했습니다 · 저장된 목록을 표시합니다". 없으면 배지 "페스티벌 목록을 불러오지 못했습니다 · 다시 시도해 주세요"와 빈 카드 "불러온 페스티벌이 없어요" + "다시 시도".
4. 썸네일: `thumbUrl`(stage는 모두 `/api/v1/festivals/images/{uuid}.webp` 상대 경로)을 `BASE_URL`과 합쳐 불러온다. 실패 · 없음은 `fillStrong` 바탕 + 음표 36 아이콘.
5. 상태 태그 · D-day · 팩 문구 · 메타 문자열이 iOS `FestivalListPresentation`과 같은 입력에서 같은 결과를 낸다(단위 테스트로 iOS `FestivalListPresentationTests` 14개 사례를 옮김).
6. 검색: 이름 · 장소 · 아티스트(대소문자 · 발음 구별 기호 · 전각/반각 무시)로 찾고, 제출한 검색어가 최근 검색 앞에 쌓인다(중복 제거, 10개). 첫 글자를 입력했을 때 다음 페이지가 남아 있으면 전체 목록을 한 번에 받는다.
7. 예매처 링크 탭: 첫 https 링크를 Custom Tabs로 연다. 없으면 상단 토스트 2초.
8. 검색 중 시스템 뒤로(제스처 포함) = 검색 닫기(검색어 지움). 검색이 아니면 뒤로는 앱 루트 동작(메인 탭은 앱 종료)을 그대로 둔다.
9. 다른 탭에 갔다 와도 탭 선택 · 검색 상태 · 스크롤 위치가 유지된다.
10. 단위 테스트(JVM): 프레젠테이션 규칙, ViewModel 상태 전이(Orbit `test`), DTO 해석(손상 원소 건너뛰기 포함), 카탈로그 서비스 페이지 · 커서 반복 · 캐시 저장(Ktor `MockEngine` + 임시 폴더), 이미지 주소 결합, 최근 검색 저장소. 실서버를 부르는 테스트는 없다.
11. `.claude/scripts/verify --all` · PR CI 통과, `assembleStageDebug` 빌드.

## 모듈과 책임

의존 방향은 `.claude/docs/architecture.md`를 따른다. iOS는 DTO가 `Domain/FestivalDomain/Sources`에 있고 서비스가 도메인을 import하지만, Android는 `domain/sources → service → core` 방향이라 DTO를 `service/api`로 내린다(결정 D5).

| 모듈                         | iOS 대응                                                                                                     | 내용                                                                                                                                                                                                                                                                                                                                        |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `feature/festival/interface` | `Feature/Festival/Interface`                                                                                 | `FestivalRoute.List`(NavKey). 1차에는 다른 기능이 쓸 진입 키만 둔다                                                                                                                                                                                                                                                                          |
| `feature/festival/sources`   | `Feature/Festival/Sources/FestivalList/**` · `Components/FestivalPosterView` · `App/.../FestivalCoordinator` | `FestivalCoordinator` + `FestivalFlow`(NavDisplay), `FestivalListScreen`/`Content`/`ViewModel`/`Contract`, `FestivalListPresentation`(순수 Kotlin), `FestivalListSkeleton`, 토스트 항목 정의, `di/`                                                                                                                                         |
| `domain/festival/interface`  | `Domain/FestivalDomain/Interface`                                                                            | `FestivalCatalogItem` · `FestivalCatalogArtist` · `FestivalCatalogPerformance` · `FestivalTicketLink` · `FestivalCatalogPage` · `FestivalOverview`, `FestivalCatalogRepository` · `FestivalCatalogUseCase`(cached · refresh · page · imageUrl), `FestivalLibraryUseCase`(최근 검색 4개 함수, 북마크는 이후 단계), 오류 `FestivalCatalogError` |
| `domain/festival/sources`    | `Domain/FestivalDomain/Sources`                                                                              | Repository · UseCase 구현, DTO → 도메인 매핑(`FestivalItemDTO+.swift`: 빈 라벨 · https 아닌 예매 링크 버림), 최근 검색 규칙(앞에 추가 · 중복 제거 · 10개)                                                                                                                                                                                   |
| `service/api`                | `Service/API` · `FestiCoreFestivalCatalog/Endpoint`                                                          | `FestivalApiPath.FESTIVALS = "api/v1/festivals"`, `FestivalApi.list(after, limit)`(인증 없음), `FestivalListDto` · `FestivalItemDto` · `FestivalTicketLinkDto` · `FestivalCatalogArtistDto` · `FestivalCatalogPerformanceDto`(손상 원소 건너뛰는 serializer)                                                                                |
| `service/festivalcatalog`    | `Service/FestiCoreFestivalCatalog`                                                                           | `FestivalCatalogService`: `cached()`, `refresh()`(limit 50 반복 · 최대 100쪽 · 커서 반복 오류 · 끝나면 저장), `page(after, limit)`(쪽 누적 · 마지막 쪽에서 저장 · 같은 커서 반복 오류), `FestivalCatalogCache`(JSON 파일 원자 저장), `FestivalImageUrlResolver(baseUrl)`                                                                    |
| `service/pack`               | `Service/FestiCorePack`                                                                                      | 1차에서 만들지 않는다. 목록의 `cachedPacks`는 빈 값으로 두고 팩 문서에서 붙인다                                                                                                                                                                                                                                                              |
| `core/network`               | `Core/FestiCoreNetwork`                                                                                      | 기존 `festiCoreRequest` · `NetworkError` 그대로. 변경 없음                                                                                                                                                                                                                                                                                  |
| `core/datastore`             | `Core/FestiCoreStorage` · `FestivalRecentSearchStore+Live`                                                   | `RecentSearchStore`(DataStore Preferences, 키 `FestiCore.festivalRecentSearches.user.{userId}` / `.anonymous`, 값은 `\n`으로 이은 문자열)                                                                                                                                                                                                   |
| `ui/designkit`               | `UI/FestiCoreDesignKit`                                                                                      | `FCTopBar` 검색 스타일에 `onSearchSubmit` 추가(현재 `ImeAction.Search`만 있고 `keyboardActions` 없음, `FCTopBar.kt:436`). 그 외 컴포넌트는 기존 것 사용                                                                                                                                                                                     |
| `ui/sharedui`(신규)          | `UI/FestiCoreSharedUI/RemoteImage/FCRemoteImage.swift`                                                       | `FCRemoteImage(url, contentScale, placeholderIcon, iconSize)` — Coil 3. 결정 D4                                                                                                                                                                                                                                                             |
| `app`                        | `App` · `MainTab`                                                                                            | 라이브 탭 루트를 `FestivalFlow`로 교체, Koin 목록 합치기, `BASE_URL`을 `service/festivalcatalog`에 전달, Coil `SingletonImageLoader` 설정                                                                                                                                                                                                   |

## 화면 (S00 목록)

모든 색 · 글꼴 · 간격 · 반경은 designkit 토큰을 쓴다. 수치는 iOS 코드 값(pt → dp 1:1).

**사용하는 designkit 컴포넌트**: `FCTopBar(Large / Search)` · `FCTab` · `FCOfflineBadge` · `FCFestivalCard` · `FCEmptyCard` + `FCCardAction` · `FCSkeletonView(Rect / Round)` · `FCSectionHeader(isCompact)` · `FCFlowLayout` · `FCRecentKeyword` · `FCSearchResultRow(Festival / Artist)` · `FCSearchEmpty` · `FCToastHost` · `FCToast`, 레이아웃 `ScrollBottomInset`. 없는 것: 원격 이미지(`FCRemoteImage` → `ui/sharedui` 신규), 토스트 프리셋(`booked` · `unbooked` · `noLink`, iOS는 SharedUI 소속 → `feature/festival` 안에 같은 문구로 정의), 로딩 더 보기 인디케이터(iOS 시스템 `ProgressView` → Material3 `CircularProgressIndicator`), `FCTopBar` 검색 제출.

### 목록 모드 (`FestivalListView.swift:28-61 · 173-196`)

- 세로 스택 간격 0: `FCTopBar(Large, title = "페스티벌", onTrailingAction = 검색 열기)`(오른쪽 아이콘 · 라벨은 스타일 기본 = 검색) → `FCTab(items = ["전체", "예매 완료"], indicatorColor = staticLime)` → 오프라인 배지(있을 때) → 목록.
- 목록: `LazyColumn`, 항목 간격 `paddingP16`, 좌우 `paddingP16`, 목록 위 `paddingP16`(Figma S00 Contents 위 여백 16), 아래 `ScrollBottomInset`(iOS `fcScrollBottomInset` = 하단 `paddingP20`), 스크롤 막대 없음.
- 첫 항목 안내 문구: 전체 "가고 싶은 페스티벌을 찾아보아요.\n이미 예매한 페스티벌은 북마크 해서 따로 모아 볼 수 있어요.", 예매 완료 "북마크한 페스티벌만 모아 봤어요.\n팩을 미리 받아 두면 현장에서 통신이 약해도 볼 수 있어요." — `baseSubheadlineMedium`, 줄 박스 22 × 2(`festiCoreLineBox`), `labelNeutral`.
- 이어서 스켈레톤 / 빈 카드 / 카드들. 전체 탭이고 다음 커서가 있고 갱신 실패가 아니면 맨 끝에 로딩 인디케이터, 보이면 `loadMoreFestivals`(커서를 key로).
- 좌우 밀기(`Metric`): 최소 거리 30, 가로 이동 절댓값 > 80 이고 > 세로 × 1.5일 때 왼쪽 = 예매 완료, 오른쪽 = 전체.
- 검색 ↔ 목록 전환은 `easeOut 0.2s`(Metric.duration).

### 카드 (`FestivalListView.swift:227-246 · 487-538`)

- `FCFestivalCard(title = name, meta, status, media = FestivalPoster, pack, isBooked, onBookmark, onTicketLink)`, 카드 탭 = 축제 선택, 테스트 태그 `festivalCard_{id}`.
- 포스터(`FestivalPosterView.swift`): 원격 이미지 fill · 넘친 부분 자름 · 터치 받지 않음 · 반경 8 · 실패/없음 `fillStrong` + `ic_music_24` 36.
- 메타: `[venue, "M/d(E) – M/d(E)"]`에서 없는 값을 빼고 " · "로 잇는다(날짜 사이는 en dash " – ", 같은 날도 두 번 표기 — iOS 그대로). 예: `KINTEX HALL 7/8/9/10 · 11/20(금) – 11/22(일)`.
- 상태(`cardStatus`, 우선순위 종료 > 오픈 예정 > 진행 중 > D-3~D-1 > D-4 이상 > 날짜 없음): 종료 → `Ended`("마감"), `packVersion == 0` → `Upcoming`("오픈 예정"), 진행 중 → `DDay("진행 중")`, 1~3일 → `Closing("D-N")`, 4일 이상 → `DDay("D-N")`, 날짜 없음 → `DDay("일정 확인")`.
- 팩(`packCopy`): `packVersion <= 0` → `Unavailable("팩 발행을 기다리고 있어요")`, 받은 버전 ≥ 발행 버전 → `Received("팩 받음 · 최신 버전")`, 그 외 → `Ready("팩 받기" | 받은 적 있으면 "팩 업데이트")`. 받는 중 `Downloading(progress, "팩 받는 중 · N%" | "팩 받는 중")`. 1차의 동작은 결정 D2.
- 북마크: 1차은 `onBookmark = null`로 숨긴다(결정 D3). iOS 접근성 라벨 "예매한 페스티벌로 등록" / "예매한 페스티벌 해제"는 designkit에 이미 있다.
- 예매처: `onTicketLink` 항상 전달(iOS `.ticketLink {}`), 제목은 컴포넌트 기본 "예매처 바로가기".

### 스켈레톤 (`FestivalListSkeletonView.swift`)

카드 3장, 간격 `paddingP16`, 테스트 태그 `festivalListSkeleton`. 카드: 배경 `backgroundElevated`, 반경 `r16`, 테두리 1 `lineNormal`. 위에서부터 `Rect` 높이 140 → 패딩 `paddingP12` 안에 간격 `paddingP8`로 [제목 `Round` 200×24 · 빈칸 · 태그 `Round(r4)` 40×22] · 메타 240×18 · 안내 260×16 → 패딩 `paddingP12` 링크 84×18.

### 빈 카드 (`FestivalListView.swift:199-225`)

- 목록 자체가 비었을 때: 아이콘 `ic_bell`, "불러온 페스티벌이 없어요", 실패면 "연결을 확인하고 다시 시도해 주세요." + 버튼 "다시 시도"(새로고침), 아니면 "새 페스티벌이 등록되면 여기에 표시해요."
- 목록은 있는데 예매 완료가 비었을 때: `ic_bookmark_20`, "북마크한 페스티벌이 없어요", "예매한 페스티벌을 북마크하면 여기에 모아 볼 수 있어요." + "전체 보기"(탭을 전체로).

### 오프라인 배지 (`FestivalListView.swift:99-113 · 249-253`)

`FCOfflineBadge(message)`: 캐시 읽기 실패 "저장된 페스티벌 목록을 읽지 못했습니다 · 연결 후 다시 시도해 주세요" > 갱신 실패 + 목록 없음 "페스티벌 목록을 불러오지 못했습니다 · 다시 시도해 주세요" > 갱신 실패 + 목록 있음 "최신 페스티벌 목록을 불러오지 못했습니다 · 저장된 목록을 표시합니다". 목록 · 검색 두 모드 모두 상단 바 아래.

### 검색 모드 (`FestivalListView.swift:19-27 · 255-480`)

- `FCTopBar(Search(text, onTextChange), searchPlaceholder, onCancel = 검색 닫기, onSearchSubmit = 최근 검색 저장)`. placeholder는 아티스트 정보가 한 건이라도 있으면 "페스티벌, 아티스트, 장소 검색", 아니면 "페스티벌, 장소 검색". 스크롤하면 키보드를 바로 닫는다.
- 검색어 없음: 최근 검색이 있으면 `FCSectionHeader("최근 검색", isCompact, actionTitle = "전체 삭제")`(위 `paddingP20` · 아래 `paddingP8` · 좌우 `paddingP16`) + `FCFlowLayout(spacing = paddingP8)`(좌우 `paddingP16`)에 `FCRecentKeyword`(탭 = 그 검색어 입력, 삭제). 이어서 "다가오는 페스티벌"(진행 중 · 날짜 있는 예정만, 같은 헤더 여백) 결과 행.
- 불러오는 중이고 목록이 비었으면 검색 스켈레톤(전체 패딩 `paddingP16`, 간격 `paddingP16`): `Round` 120×20, 행 3개 [`Round(r12)` 48×48 · 간격 `paddingP12` · (간격 `paddingP8`) 180×18 · 230×16], 태그 `festivalSearchSkeleton`.
- 목록이 비었고 실패: `FCEmptyCard(ic_bell, "페스티벌을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.", "다시 시도")`, 패딩 `paddingP16`.
- 결과 없음: `FCSearchEmpty(query, message = "페스티벌, 아티스트 이름이나 장소로 다시 찾아보세요" | "페스티벌 이름이나 장소로 다시 찾아보세요")`.
- 결과: "페스티벌 {N}" 섹션 + `FCSearchResultRow(Festival(image = null, dDay = searchBadge), title, meta, highlightedQuery, badgeStyle, thumbnail = FestivalPoster)`, "아티스트 {N}" 섹션 + `FCSearchResultRow(Artist(initial = 이름 첫 글자), title = 아티스트, meta = artistMeta)`. 행 탭 = 최근 검색 저장 후 축제 선택.
- `searchBadge`: dDay · closing 라벨, 오픈 예정 "오픈 예정", 종료 없음. `badgeStyle`: closing → `Closing`, upcoming → `Upcoming`, 나머지 `Day`.
- `artistMeta`: 공연을 (시작 시각, 날짜, 스테이지 이름)으로 정렬해 지금 이후 첫 공연, 없으면 마지막 공연을 골라 "{축제} · {M/d(E)} {스테이지}"(스테이지 비면 생략).

### 토스트 (`FestivalView.swift:47-64`, `FestivalToastView.swift`)

`FCToastHost(topPadding = 30, duration = 2s)`로 화면 위에서 내려온다. 1차에서 쓰는 것은 `ticketLinkMissing` 하나: 아이콘 `ic_circle_info_16`, "예매처 정보가 없습니다", 아이콘 색 `staticWhite`. (북마크 · 팩 단계: 담음 `ic_bookmark_16` "내가 예매한 페스티벌에 담았어요" 아이콘 `staticLime`, 제외 "내가 예매한 페스티벌에서 제외했어요", 팩 실패 `ic_circle_info_16` "팩을 받지 못했어요. 다시 시도해 주세요".) 새 토스트는 이전 것을 바로 바꾼다(iOS `toastRevision` · `cancelInFlight`).

## 상태 (`FestivalListContract.kt`)

iOS State에서 목록에 필요한 것만 옮긴다.

| 필드                                                 | iOS                                          | 뜻                                   |
| ---------------------------------------------------- | -------------------------------------------- | ------------------------------------ |
| `viewState: Loading / Loaded`                        | `viewState`                                  | 새로고침 중 여부                     |
| `hasFinishedFirstCatalogRefresh`                     | 같음                                         | 첫 서버 응답(성공 · 실패) 전         |
| `catalog: List<FestivalCatalogItem>`                 | `catalog`(IdentifiedArray)                   | 같은 ID는 처음 것만(`uniqueCatalog`) |
| `nextCursor: String?` · `isLoadingMore`              | `catalogNextCursor` · `isLoadingMoreCatalog` | 페이지                               |
| `catalogCacheReadFailed` · `catalogRefreshFailed`    | 같음                                         | 배지 · 빈 카드 문구                  |
| `bookedFestivalIds: Set<String>` · `userId: String?` | `bookedFestivalIDs` · `bookedUserID`         | `bookedFestivalIds`는 1차 빈 값, `userId`는 `CurrentUserProvider`(결정 D3)                  |
| `recentSearches: List<String>`                       | 같음                                         |                                      |
| `filter: All / Booked` · `isSearching` · `query`     | `listInteraction`                            | 사용자가 바뀌면 초기화               |
| `toast: FestivalListToast?`                          | `toast` · `toastRevision`                    |                                      |
| `isForeground` · `hasStarted`                        | 같음                                         |                                      |

- 화면 값(계산): `festivals`(= catalog → `FestivalOverview`, 팩 병합은 팩 문서), `showsListSkeleton = !hasFinishedFirstCatalogRefresh || (viewState == Loading && festivals.isEmpty())`(`FestivalFeature.swift:2286-2288`), `visibleFestivals` · `searchResults` · `artistResults` · `upcomingFestivals`는 `FestivalListPresentation`으로 Content에서 계산한다(`now`는 주입 가능한 `Clock`).
- 이미지 주소는 State에 담지 않고 UseCase `imageUrl(thumbUrl)`이 매핑 시점에 결정한다(iOS `imageURLs` · `imageRevision`은 Kingfisher 재시도용, 위험 4).

## 동작 (`FestivalListViewModel`)

| iOS Action                                                                 | Android                                   | 내용                                                                                                                                                                                       |
| -------------------------------------------------------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `onAppear` (`709-720`)                                                     | `onAppear()`                              | 최근 검색 읽기, 처음 한 번만 캐시 읽기 → `onCachedLoaded`                                                                                                                                  |
| `cachedLoaded` (`1454-1478`)                                               | `onCachedLoaded`                          | catalog 교체, 목록이 있으면 Loaded, 읽기 실패 플래그, 이어서 첫 페이지 갱신                                                                                                                |
| `refreshCatalog` (`1909-1931`)                                             | `refreshCatalog()`                        | `page(after = null, limit = 10)`; 다음 커서 없으면 `catalogRefreshed`, 있으면 `catalogPageLoaded`. 진행 중인 카탈로그 작업은 취소하고 새로 시작(iOS `CancelID.catalog` + `cancelInFlight`) |
| `catalogRefreshed` (`1491-1539` 중 목록 부분)                              | `onCatalogRefreshed(items)`               | Loaded, 첫 갱신 완료, 실패 플래그 둘 다 끔, 커서 null, catalog 교체                                                                                                                        |
| `catalogPageLoaded` (`1541-1560`)                                          | `onCatalogPageLoaded(page, after)`        | `after`가 현재 커서와 다르면 버림. 첫 쪽(`after == null`)은 캐시 목록을 버리고 그 쪽 항목으로 **새로 시작**하고, 다음 쪽부터 서버 목록 뒤에 이어 붙인다(iOS `items = after == nil ? page.items : catalog + page.items`, `1546`). 그래서 중복 제거는 서버 항목끼리만 일어나고 서버에서 빠진 축제 · 바뀐 필드는 첫 쪽 성공으로 반영된다. 캐시는 첫 갱신 실패 시 대체로만 쓴다. 커서 갱신. 다음 쪽이 있는데 검색어가 있거나 예매 완료 탭이면 전체 갱신                                                                    |
| `catalogFailed` (`1562-1571`)                                              | `onCatalogFailed()`                       | Loaded, 첫 갱신 완료, 갱신 실패 켬, 더 보기 해제                                                                                                                                           |
| `refreshRequested` (`770-774`)                                             | `onRefresh()`                             | Loading, 커서 null, 첫 페이지 갱신                                                                                                                                                         |
| `loadMoreFestivals` (`776-780`)                                            | `onLoadMore()`                            | 커서 있음 ∧ 진행 중 아님 ∧ 검색 아님 ∧ 전체 탭일 때만 `page(cursor, 10)`                                                                                                                   |
| `searchOpened` · `searchClosed` (`860-867`)                                | `onSearchOpened()` · `onSearchClosed()`   | 닫기는 검색어도 지움                                                                                                                                                                       |
| `searchQueryChanged` (`869-873`)                                           | `onQueryChange(q)`                        | 비어 있다가 처음 글자가 생기고 커서가 남아 있으면 전체 갱신(`refresh()`, limit 50 반복)                                                                                                    |
| `listFilterChanged` (`875-878`)                                            | `onFilterChange(f)`                       | 예매 완료로 바꾸고 커서가 남아 있으면 전체 갱신                                                                                                                                            |
| `recentSearch*` (`841-858`)                                                | `onRecentSearchSubmitted/Removed/Cleared` | trim이 비면 저장 안 함                                                                                                                                                                     |
| `ticketLinkTapped` (`1106-1118`)                                           | `onTicketLinkTapped(id)`                  | 링크 있으면 `SideEffect.OpenTicket(url)` → Screen이 Custom Tabs, 없으면 토스트                                                                                                             |
| `selectFestival` (`785-786`)                                               | `onFestivalSelected(id)`                  | 목록에 있는 ID만 `SideEffect.FestivalSelected(id)` → Coordinator                                                                                                                           |
| `foregroundChanged` (`722-743`)                                            | `onForegroundChanged(Boolean)`            | 시작 전이면 `onAppear`, 이후 포그라운드면 첫 페이지 재조회(iOS `resumeForeground`의 목록 부분)                                                                                             |
| `reloadBookmarks(userID)` (`1120-1125`)                                    | `onUserChanged(userId)`                   | 사용자가 바뀌면 검색 · 탭 초기화, 최근 검색 다시 읽기                                                                                                                                      |
| 탭 선택 `.live` → `reloadCachedPacks` (`MainTabCoordinator.swift:344-345`) | —                                         | 팩 문서에서 붙임                                                                                                                                                                           |

## 오류 · 빈 · 로딩 · 오프라인 상태

| 상황                    | 화면                                                                                            |
| ----------------------- | ----------------------------------------------------------------------------------------------- |
| 첫 서버 응답 전         | 스켈레톤(캐시가 있어도 iOS는 첫 갱신 전 스켈레톤 — `listSkeletonShowsUntilFirstCatalogRefresh`) |
| 새로고침 중 · 목록 없음 | 스켈레톤                                                                                        |
| 새로고침 중 · 목록 있음 | 기존 목록 + 당겨서 새로고침 표시                                                                |
| 성공 · 0건              | 빈 카드(새 페스티벌 안내)                                                                       |
| 실패 · 저장 목록 있음   | 저장 목록 + 배지(저장된 목록 표시)                                                              |
| 실패 · 저장 목록 없음   | 배지 + 빈 카드 + 다시 시도                                                                      |
| 캐시 파일 손상          | 배지(읽지 못했습니다), 이후 서버 성공 시 해제                                                   |
| 더 보기 실패            | 갱신 실패 켬 → 끝 인디케이터 숨김, 배지 표시, 당겨서 새로고침으로 복구                          |

오류 매핑: `NetworkError.Offline` · `Timeout` · `Http` · `Decoding` · `InvalidResponse`와 `FestivalCatalogError.RepeatedCursor` · `TooManyPages`는 모두 `catalogFailed`(iOS는 종류를 구분하지 않는다).

## 서버 계약

`GET api/v1/festivals?after={cursor}&limit={n}` — 인증 없음(iOS `FestivalListRequest.swift:12` `.none`), 재시도 기본값.

2026-11-21 stage(`https://festicore-dev.up.railway.app/`)에서 GET만 실행해 확인했다.

| 필드                                                       | 형          | stage 확인                                                                        | iOS DTO                 | Android DTO                                         |
| ---------------------------------------------------------- | ----------- | --------------------------------------------------------------------------------- | ----------------------- | --------------------------------------------------- |
| `code` · `message` · `data`                                | 래퍼        | ✓ `code:"SUCCESS"`                                                                | `BaseResponseDTO`       | 기존 `BaseResponse`                                 |
| `data.items`                                               | 배열        | ✓                                                                                 | 필수                    | 필수                                                |
| `data.nextCursor`                                          | 문자열?     | ✓ 예 `2026-10-22~v2~2026-11-21~1~2026-12-18~fst_b671aed7598b2e50`, 마지막 쪽 null | 선택                    | 선택 · 그대로 `after`에 URL 인코딩해 보냄(`~` 포함) |
| `id` · `name`                                              | 문자열      | ✓ `fst_` 접두                                                                     | 필수                    | 필수                                                |
| `venue` · `firstDate` · `lastDate`                         | 문자열      | ✓ 31건 모두 있음, 날짜 `yyyy-MM-dd`                                               | 선택                    | 선택                                                |
| `thumbUrl`                                                 | 문자열      | ✓ 31건 모두 상대 경로 `/api/v1/festivals/images/{uuid}.webp`                      | 선택                    | 선택                                                |
| `packVersion`                                              | 정수        | ✓ 3~237, 0인 항목은 현재 없음                                                     | 필수                    | 필수                                                |
| `status`                                                   | 문자열      | ✓ `live` 2 · `upcoming` 15 · `ended` 14                                           | 선택(표시에 안 씀)      | 선택                                                |
| `ticketLinks[{label,url}]`                                 | 배열        | ✓ 빈 배열 1건, 모두 https                                                         | 선택 · 손상 원소 건너뜀 | 같음                                                |
| `artists[{name, performances[{startsAt,date,stageName}]}]` | 배열        | ✓ 빈 배열 2건, `startsAt` 예 `2026-11-20T12:00:00+09:00`                          | 선택 · 손상 원소 건너뜀 | 같음                                                |
| `flagsEnabled`                                             | 불리언      | ✓ 있음                                                                            | 없음(무시)              | 받지 않음(`ignoreUnknownKeys`)                      |
| `artistNames`                                              | 문자열 배열 | ✓ 있음                                                                            | 없음(무시)              | 받지 않음                                           |

- 쪽 크기: `limit` 생략 시 20건, `limit=10` 10건, `limit=50`이면 현재 31건 전부 한 쪽. `limit=500` → 400 `{"code":"VALIDATION","data":null,"message":"limit은 1..50이어야 합니다."}`. iOS 전체 갱신의 50은 상한과 같다.
- 잘못된 커서 `after=bogus` → 400 `VALIDATION` "after는 YYYY-MM-DD여야 합니다." → `NetworkError.Http`.
- 커서 연속성: `limit=10` 1쪽 커서로 2쪽 10건을 받았고 중복 없음. 31건 ID 모두 유일.
- 이미지: `GET {BASE_URL}api/v1/festivals/images/{uuid}.webp` → 200 `image/webp`, `cache-control: public, max-age=86400`, 약한 `ETag`. 없는 이미지 이름은 404가 아니라 400(디코딩 실패와 같게 자리 표시로 처리).
- 인증이 필요한 경로는 이 화면에 없다(목록 · 이미지 모두 미인증 GET). 축제 상세 `GET api/v1/festivals/{id}`는 라이브 보드 문서에서 확인한다.

## 캐시

- iOS: `Application Support/FestiCore/festival-catalog.json`에 `[FestivalItemDTO]` JSON, `.atomic` 쓰기(`NetworkContainer.swift:77-81 · 94-99`, `FestivalCatalogCache.swift`). 없으면 빈 목록, 해석 실패는 오류.
- Android: `service/festivalcatalog`의 `FestivalCatalogCache(file)`이 `context.filesDir/festicore/festival-catalog.json`에 `List<FestivalItemDto>`를 `androidx.core.util.AtomicFile`로 쓴다. 파일 경로는 app이 Koin으로 넘긴다. DataStore가 아닌 이유: 목록 문서 하나를 통째로 바꾸는 쓰기이고 iOS도 파일이며 수십 KB 크기다(결정 D6).
- 저장 시점: 전체 갱신이 끝났을 때, 또는 쪽 조회에서 마지막 쪽(`nextCursor == null`)을 받았을 때 그때까지 받은 쪽 전체(첫 쪽부터 다시 모은 값, 중복 ID 제거). 첫 쪽만 받고 멈추면 저장하지 않는다(iOS와 같음 — 다음 실행 캐시는 이전 전체 목록).
- 썸네일은 Coil 디스크 캐시(서버 `max-age=86400`)에 맡긴다.

## 오프라인 정책

- 캐시 먼저, 서버 다음. 서버 실패는 저장 목록을 지우지 않는다.
- 자동 재시도 없음. 복구 경로는 당겨서 새로고침 · 빈 카드 "다시 시도" · 포그라운드 진입이다(iOS와 같음).
- 연결 상태 감시(ConnectivityManager)는 두지 않는다. iOS 목록도 요청 결과만 본다.

## 이미지 로딩

- 주소 결합(`FestivalImageURLResolver.swift:6-15`와 같은 규칙): 스킴이 있으면 https일 때만 그대로, 스킴이 없으면 `/`로 시작하고 `//`로 시작하지 않으며 `BASE_URL`이 https일 때만 `BASE_URL` 기준으로 결합, 나머지는 null. 구현은 `java.net.URI(baseUrl).resolve(value)`. 단위 테스트: 상대 경로 · `https://` 그대로 · `http://` 거부 · `//host` 거부 · `images/x`(앞 `/` 없음) 거부 · 빈 문자열.
- 라이브러리 후보:

| 후보                                                         | 라이선스                    | 장점                                                                                                                       | 단점                                      |
| ------------------------------------------------------------ | --------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| **Coil 3** (`io.coil-kt.coil3:coil-compose` + 네트워크 모듈) | Apache-2.0                  | Compose 우선 · 코루틴 · 메모리/디스크 캐시 · WebP는 플랫폼 디코더로 처리 · 네트워크 모듈을 OkHttp 또는 Ktor로 고를 수 있음 | 새 의존성                                 |
| Glide (Compose 통합)                                         | BSD · MIT · Apache-2.0 혼합 | 오래 쓰인 라이브러리                                                                                                       | Compose 통합이 아직 베타 · 주석 처리기    |
| 직접 구현(Ktor로 받아 `BitmapFactory`)                       | —                           | 의존성 없음                                                                                                                | 캐시 · 다운샘플 · 취소를 다시 만들어야 함 |

추천은 Coil 3. 네트워크 모듈은 결정 D7(`coil-network-okhttp` vs `coil-network-ktor3`). 버전은 도입 PR에서 공식 릴리스 노트로 확인해 `libs.versions.toml`에 고정한다(이 문서는 버전을 적지 않는다).

## 탭 진입

- `MainTabScreen`의 Live 탭 루트를 `FestivalFlow()`로 바꾼다. iOS도 라이브 탭 루트는 `FestivalCoordinatorView`(`MainTabView.swift:232-238`)이고 첫 화면은 `festivalList`(`FestivalCoordinator.swift:18-20`)다.
- 탭 상태 유지: iOS는 선택한 탭만 그리고 스토어는 남겨 둔다. Android는 `FestivalCoordinator` · `FestivalListViewModel`을 Home 항목 범위 ViewModelStore에 두고(탭을 바꿔도 살아 있음), 탭별 `SaveableStateHolder`로 `LazyListState`를 보존한다.
- 같은 탭 재선택: iOS `tabReselected`는 아무것도 안 한다(`MainTabCoordinator.swift:228-229`). Android도 같게 둔다(맨 위로 스크롤 같은 Android 관례는 넣지 않음 — 차이를 만들지 않는다).
- 사용자 ID: 최근 검색 키에 필요하다. 현재 Android 공개 API(`AuthSession`)에는 userId가 없다 → 결정 D3.

## 화면 전환

iOS 원본에서 확인한 것:

| 전환                          | iOS 방식                                                                         | 근거                                                                                                                                      |
| ----------------------------- | -------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 라이브 탭 루트                | `NavigationStack` 루트(`embedInNavigationView: true`), 내비게이션 바 숨김        | `FestivalCoordinator.swift:19`, `FestivalView.swift:25-28`                                                                                |
| 목록 → 라이브 보드            | push(`routes.push(.liveBoard)`) — 시스템 push 애니메이션(오른쪽에서 밀려 들어옴) | `FestivalCoordinator.swift:101-117`                                                                                                       |
| 라이브 보드 → 목록            | `routes.goBack()`(보드의 뒤로 버튼 `backToFestivalList`)                         | `FestivalCoordinator.swift:119-121`                                                                                                       |
| 라이브 보드의 스와이프 뒤로   | `.swipeBackButtonHidden()` 적용                                                  | `FestivalCoordinatorView.swift:14-16`. TCAFlow 외부 패키지라 로컬에 소스가 없어 정확한 동작(가장자리 스와이프 허용 여부)은 **확인 못 함** |
| 목록 ↔ 검색                   | 같은 화면 안 교체, `easeOut 0.2s`                                                | `FestivalListView.swift:18-28 · 68-71`                                                                                                    |
| 예매처 웹                     | `fullScreenCover`(아래에서 올라오는 전체 화면)                                   | `FestivalView.swift:19-24`                                                                                                                |
| 토스트                        | 위에서 내려오며 나타남(move top + opacity), `easeOut 0.2s`                       | `FestivalToastView.swift:19`, `FestivalView.swift:56-63`                                                                                  |
| 로그인 요청(북마크 단계) | `fcModal` 시트, 높이 auto, grabber 표시                                          | `MainTabView.swift:136-142`                                                                                                               |

Android 설계:

- `FestivalFlow`의 `NavDisplay(backStack = coordinator.routes, onBack = coordinator::goBack)`에 iOS push와 같은 수평 이동을 준다.
  - `transitionSpec`: 들어오는 화면 `slideInHorizontally { it }`(오른쪽 끝에서), 나가는 화면 `slideOutHorizontally { -it / 3 }`(iOS push처럼 살짝 왼쪽으로).
  - `popTransitionSpec`: 들어오는 화면 `slideInHorizontally { -it / 3 }`, 나가는 화면 `slideOutHorizontally { it }`.
  - `predictivePopTransitionSpec`: `popTransitionSpec`과 같은 값을 써서 예측 뒤로 제스처 진행률을 따라 화면이 끌려 나가게 한다. 제스처를 취소하면 원위치.
  - 시간 · 곡선: iOS 시스템 push 값은 코드에 없다(**확인 못 함**). Android 기본 Navigation 3 지속 시간을 쓰지 않고 `tween(350, FastOutSlowInEasing)`을 제안하며, 실기기에서 iOS와 나란히 보고 조정한다(결정 D8).
  - 정확한 API 이름 · 시그니처(`NavDisplay`의 세 spec 파라미터, `ContentTransform`)는 도입 PR에서 Navigation 3 공식 문서로 다시 확인한다.
- 1차에서 실제로 쌓이는 화면은 목록 하나뿐이다. 라이브 보드 push는 다음 문서에서 위 spec 그대로 쓴다. 목록에서 시스템 뒤로는 루트 동작(메인 탭 → 앱 종료)을 바꾸지 않는다.
- 검색 모드는 route가 아니라 State다(iOS도 같은 화면). `BackHandler(enabled = isSearching)`로 뒤로 = `onSearchClosed()`. 예측 뒤로 미리보기에서 홈으로 가는 애니메이션이 잘못 보이지 않도록 `enabled`가 검색 상태와 정확히 일치해야 한다(Android 14+ 예측 뒤로는 콜백이 등록되어 있으면 시스템 홈 미리보기를 보이지 않는다). 전환은 `AnimatedContent(targetState = isSearching, transitionSpec = fadeIn(tween(200, easeOut)) togetherWith fadeOut(...))`로 iOS 0.2s easeOut 교체를 맞춘다.
- 예매처: 1차은 Custom Tabs(`androidx.browser`)로 연다. Custom Tabs 자체 등장 애니메이션은 기본(아래에서 위) — iOS `fullScreenCover` 방향과 같아 별도 지정하지 않는다. 앱 안 웹 화면(`feature/web`)으로 바꾸면 iOS `fullScreenCover`는 route push가 아니라 덮개이므로 Coordinator의 별도 덮개 State + `AnimatedVisibility(slideInVertically { it })`로 재현한다.
- 시트(북마크 단계의 로그인 요청 등): iOS `fcModal` → Material3 `ModalBottomSheet`(designkit `FCSheet`가 이미 있음, `component/modal/FCSheet.kt`). 시트가 열려 있으면 시스템 뒤로 = 시트 닫기(`ModalBottomSheet` 기본 동작), 예측 뒤로 진행률에 맞춰 시트가 내려가는 것은 Material3 기본을 따른다.
- 토스트: 기존 `FCToastHost`(애니메이션 250ms). iOS는 200ms라 50ms 차이 — designkit 값이므로 이 PR에서 고치지 않고 위험에 기록한다.

## iOS → Android 적응

| 항목               | iOS                                                                        | Android                                                                                                                                      | 이유                                                                                      |
| ------------------ | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| 뒤로 가기          | 보드의 뒤로 버튼 · 스와이프, 검색은 "취소" 버튼만                          | Navigation 3 back stack + 검색 중 `BackHandler` = 검색 닫기, 예측 뒤로 지원                                                                  | Android 시스템 뒤로 제스처는 모든 화면에서 동작해야 한다                                  |
| 당겨서 새로고침    | `.refreshable`                                                             | Material3 `PullToRefreshBox(isRefreshing = viewState == Loading && hasFinishedFirstCatalogRefresh, onRefresh)`                               | 플랫폼 표준 인디케이터. iOS는 동기 send라 즉시 끝나지만 Android는 요청 동안 표시(결정 D9) |
| 이미지             | Kingfisher(`FCRemoteImage`)                                                | Coil 3 `AsyncImage`(`FCRemoteImage` in `ui/sharedui`)                                                                                        | Compose 표준 이미지 로더                                                                  |
| 더 보기 인디케이터 | 시스템 `ProgressView`                                                      | Material3 `CircularProgressIndicator` 20dp · 선 2dp · 색 `labelAssistive`                                                                    | iOS 기본 스피너 대응. 크기 · 색은 iOS 코드에 없어 제안값(결정 D9)                         |
| 인셋               | 상단 바가 상태 표시줄까지 칠함, 탭 바가 하단                               | edge-to-edge: `FCTopBar`가 이미 상태 표시줄 영역까지 칠함, 목록 하단은 `ScrollBottomInset` + 탭 바, 내비게이션 바 인셋은 `FCTabBar`가 담당   | Android 15+ edge-to-edge 기본                                                             |
| 터치 피드백        | 하이라이트 없음(SwiftUI 기본)                                              | 카드 · 행 · 버튼에 Material 리플(`clickable` 기본 indication)                                                                                | Android 관례. designkit 컴포넌트가 이미 리플을 쓰는지 PR에서 확인                         |
| 햅틱               | 목록에 없음(FestivalList · 카드 · 내비게이션 코드에 haptic 호출 없음 확인) | 넣지 않음. 북마크 단계에서 넣을지는 그때 `LocalHapticFeedback`으로 결정                                                                          | iOS와 같게                                                                                |
| 외부 링크          | 앱 안 `WebView` 전체 화면                                                  | Custom Tabs(`CustomTabsIntent`, 실패 시 `Intent.ACTION_VIEW`)                                                                                | `feature/web` 전까지. 결제 앱 전환은 브라우저가 처리                                      |
| 날짜               | `Calendar`(Asia/Seoul) · `M/d(E)` ko_KR                                    | `java.time.LocalDate.parse` · `ZoneId.of("Asia/Seoul")` · `DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)` · `ChronoUnit.DAYS.between` | 표준 API, `minSdk`에서 사용 가능                                                          |
| 문자열 비교        | `range(options: caseInsensitive, diacriticInsensitive, widthInsensitive)`  | `Normalizer.normalize(NFKD)` 후 결합 문자 제거 + `lowercase(Locale.ROOT)` + NFKC 폭 접기                                                     | 같은 일치 결과. 하이라이트(`fcSearchHighlightRanges`)와 기준이 같은지 테스트              |
| 정렬 동률          | `localizedStandardCompare`                                                 | `Collator.getInstance(Locale.KOREAN)`                                                                                                        | 팩만 있는 항목 이름 정렬(팩 문서) · 스테이지 정렬                                         |
| 키보드             | `scrollDismissesKeyboard(.immediately)`                                    | 스크롤 시작 시 `LocalSoftwareKeyboardController.hide()` + `clearFocus()`                                                                     |                                                                                           |
| 좌우 밀기          | `DragGesture` 30 / 80 / 1.5                                                | `pointerInput` + `detectDragGestures`로 같은 임계값(dp)                                                                                      | `HorizontalPager`는 iOS에 없는 페이지 애니메이션이 생겨 쓰지 않음                         |

## PR 구현 순서

| PR  | 내용                                                                                                                                                | 검증                                                                                                                                                                       |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F1  | `domain/festival/interface` + `service/api` 축제 경로 · DTO + `service/festivalcatalog`(쪽 · 전체 · 캐시 · 이미지 주소) + `domain/festival/sources` | DTO 해석(stage 응답 1쪽을 테스트 자료로 축약), MockEngine 쪽 누적 · 커서 반복 · 100쪽 초과 · 마지막 쪽 저장, 주소 결합, 매핑(https 아닌 링크 버림)                         |
| F2  | `core/datastore` `RecentSearchStore` + `FestivalLibraryUseCase`(최근 검색) + `domain/auth` `CurrentUserProvider` · `AppCoordinator` 연결                                                                          | Robolectric 저장소 왕복, 10개 · 중복 · 사용자 분리                                                                                                                         |
| F3  | `ui/designkit` `FCTopBar` 검색 제출 + `ui/sharedui` `FCRemoteImage`(Coil) + `libs.versions.toml`                                                    | 컴포넌트 프리뷰, 기존 designkit 테스트                                                                                                                                     |
| F4  | `feature/festival` 목록(Presentation · Contract · ViewModel · Screen · Coordinator · Flow) + `app` 라이브 탭 연결                                   | iOS `FestivalListPresentationTests`(14) · `FestivalCatalogPaginationTests` · `FestivalOfflineTests` 목록 사례를 같은 이름으로 Orbit 테스트, stage 수동 확인 기록(스크린샷) |

각 PR은 `verify --all` · CI 통과 후 squash merge.

### 기능 spec (`.spec/`)

`.claude/docs/spec-guide.md`에 따라 동작 규칙은 소유 모듈의 `.spec/`에 두고 해당 PR에서 함께 만든다.

| PR | spec 파일 | 담는 내용 |
| --- | --- | --- |
| F1 | `domain/festival/.spec/festival-catalog.md` | 쪽 조회 · 전체 갱신 반복 · 상한 · 중복 제거 · 썸네일 주소 합치기 · 오류 매핑 |
| F1 | `service/festivalcatalog/.spec/catalog-cache.md` | JSON 파일 캐시 쓰기(AtomicFile) · 읽기 실패 처리 · 오프라인 시 캐시 표시 규칙 |
| F2 | `domain/festival/.spec/recent-search.md` | 사용자별 키 · 10개 · 중복 · 사용자 변경 시 다시 읽기 |
| F4 | `feature/festival/.spec/festival-list-screen.md` | 목록 · 검색 · 빈 · 스켈레톤 · 오프라인 · 새로고침 · 더 보기 · 예매처 · 전환 |

## 위험

- 다음 쪽 실패 배지(Codex R5 FESTIVAL-006, 사용자 결정 2026-11-21: iOS와 같게): 첫 쪽 성공 뒤 다음 쪽이 실패하면 화면은 서버에서 받은 쪽들을 보이지만 배지는 "최신 페스티벌 목록을 불러오지 못했습니다 · 저장된 목록을 표시합니다"다. iOS `FestivalListView.swift:110`과 같은 동작이라 그대로 옮긴다.

1. **iOS Reducer 분리**: iOS는 목록과 보드가 같은 State(`selectedFestivalID`, `cachedPacks`, 북마크)를 나눠 쓴다. 목록만 떼면 보드 문서에서 공유 값(선택 · 팩 캐시 · 북마크)을 Repository 흐름으로 다시 잇는 작업이 생긴다.
2. **팩 영역**: 팩 계층 없이 `packVersion > 0`이면 모두 "팩 받기" 버튼이 보인다(stage 31건 전부). 눌러도 받을 수 없다 → 결정 D2.
3. **예매 완료 탭**: 북마크 없이 탭만 두면 항상 빈 카드 → 결정 D3.
4. **이미지 재시도**: iOS는 갱신 실패 후 복구되면 `imageRevision`을 올려 실패한 포스터를 다시 받는다. Coil은 실패를 캐시하지 않으므로 재구성 시 다시 시도하지만, 같은 화면에 머물면 재시도하지 않는다. 갱신 성공 시 `imageRevision`을 Coil 요청 key에 넣어 맞춘다.
5. **서버 변화**: 응답에 iOS가 모르는 `flagsEnabled` · `artistNames`가 있다. 무시해도 되지만 서버가 `artists`를 빼고 `artistNames`만 남기면 아티스트 검색이 사라진다(iOS도 같음).
6. **없는 이미지가 400**: 404가 아니어서 서버 오류 로그와 구분이 어렵다. 앱 동작에는 영향 없음.
7. **토스트 애니메이션 200 vs 250ms**, **push 곡선 미확인**: 수치 차이가 생길 수 있다. 실기기 나란히 비교로 확인한다.
8. 새 의존성: Coil 3(Apache-2.0), `androidx.browser`(Apache-2.0). 라이선스 승인은 사람 확인 대상(CLAUDE.md 규칙 12).

## 결정

**결정 필요**

- D1. 목록을 iOS `FestivalFeature`에서 떼어 `FestivalListViewModel`로 둔다(추천). 대안: 보드까지 한 ViewModel — iOS와 같지만 2천 줄 단일 ViewModel이 되어 테스트 · 리뷰가 어렵다.
- D2. 팩 영역 1차 동작: (a) iOS 문구 그대로 `Ready("팩 받기")` + 탭 무동작(팩 PR 전까지 내부 빌드만), (b) 팩 PR을 이 범위에 넣음(WebSocket · SQLite · 진행률), (c) 팩 PR 전까지 `Unavailable` 문구로 표시. 추천 (a) — 시각은 iOS와 같고 잘못된 정보도 없다. 단 무동작 버튼은 출시 전 반드시 (b)로 채운다. **상태(2026-11-22)**: (a)로 1차를 낸 뒤 라이브 보드 설계 L3에서 (b)로 채웠다(팩 받기 · 진행률 · 실패 토스트 · 배경 취소 · 받은 팩 병합, `feature/festival/.spec/festival-list-screen.md` 19~22).
- D3. 북마크 · 사용자 ID — **결정(Codex R1 반영)**: 1차는 북마크 토글을 숨기고 예매 완료 탭은 표시(빈 카드)한다. 북마크는 별도 문서(로그인 시트 · 대기 예약 포함)로 다룬다. 최근 검색은 사용자별 키를 쓴다: `domain/auth`에 검증된 현재 사용자 흐름 `CurrentUserProvider.currentUserId: StateFlow<String?>`를 추가하고, 값은 `AppCoordinator`가 진입 · 포그라운드 프로필 재확인(`onProfileRechecked`의 `profile.user.id`), 로그인 완료, 가입 완료에서 채우며. 로그인 완료는 `LaunchLoginResult.Completed`가 검증된 프로필의 `userId`를 실어 보내고 `AppCoordinator.onLaunchLoginFinished`가 덮개를 닫으면서 그 값을 넣는다(가입 완료도 같은 방식). F2 테스트는 진입 재확인 · 로그인 완료 · 가입 완료 · 로그아웃 · 세션 만료 다섯 경로를 모두 덮고, 로그아웃 · 세션 만료 · 탈퇴 시 `null`로 비운다. 로그인 사용자는 `FestiCore.festivalRecentSearches.user.{userId}`(F2에서 iOS `FestivalRecentSearchStore+Live.swift` 근거로 고침), 미로그인은 익명 키 `FestiCore.festivalRecentSearches.anonymous`를 쓴다. `FestivalListViewModel`은 `currentUserId`를 구독해 값이 바뀌면 표시 중인 최근 검색을 그 키에서 다시 읽고, 이전 사용자의 목록을 화면에 남기지 않는다. 익명 키는 로그인 시 옮기지 않는다(iOS와 같음; 다르면 F2에서 iOS 근거로 고친다).
- D4. 원격 이미지 위치: (a) `ui/sharedui` 신규 모듈에 `FCRemoteImage`(iOS `FestiCoreSharedUI`와 같은 자리, 다른 기능 재사용), (b) `feature/festival` 안에 직접. 추천 (a).
- D6. 목록 캐시 저장 방식: (a) JSON 파일 + `AtomicFile`(iOS와 같음, 추천), (b) Room 테이블, (c) DataStore. 파일 IO를 `core/datastore`로 모을지(`check-boundaries` 규칙 확장)도 함께 정한다.
- D7. Coil 네트워크 모듈: (a) `coil-network-okhttp`(Ktor와 같은 OkHttp 엔진, 연결 풀 공유 가능), (b) `coil-network-ktor3`(기존 `HttpClient` 재사용). 추천 (a) — 이미지는 인증 · 응답 래퍼가 없어 Ktor 플러그인이 필요 없다.
- D8. push 전환 곡선 · 시간(제안 350ms `FastOutSlowInEasing`, 나가는 화면 1/3 이동)과 라이브 보드에서 가장자리 스와이프 뒤로 허용 여부(iOS `swipeBackButtonHidden` 동작 확인 필요). Android는 시스템 예측 뒤로를 막지 않는 것을 추천.
- D9. 당겨서 새로고침 표시 조건과 더 보기 인디케이터 크기 · 색(iOS 코드에 값 없음).

**결정 사항**

- DTO와 경로는 `service/api`에 둔다(D5, 의존 방향 때문. iOS는 DTO가 Domain에 있음).
- 쪽 크기는 iOS와 같게 첫 쪽 · 더 보기 10, 전체 갱신 50(서버 상한 50 확인).
- 예매처는 `feature/web` 전까지 Custom Tabs.
- 같은 탭 재선택은 아무것도 하지 않는다(iOS와 같음).
- 라이브 보드로 넘어가는 지점(`SideEffect.FestivalSelected(id)` → `FestivalCoordinator`)까지만 이 문서 범위이고, 보드 화면 · push · 선택 저장은 다음 문서(LiveBoard)로 미룬다.
