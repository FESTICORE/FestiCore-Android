---
spec: festival-list-screen
modules: [feature/festival, app]
---

# 페스티벌 목록 화면(S00) Spec

> 라이브 탭 첫 화면. 전체 · 예매 완료 탭, 축제 카드, 검색(최근 검색 · 다가오는 페스티벌 · 결과), 스켈레톤 · 빈 카드 · 오프라인 배지.

iOS 대응: `Projects/Feature/Festival/Sources/FestivalList/`(`View/FestivalView.swift` · `View/FestivalListView.swift` · `Model/FestivalListPresentation.swift` · `Components/FestivalListSkeletonView.swift` · `Reducer/FestivalFeature.swift`의 목록 부분), `Components/FestivalPosterView.swift`, `Accessibility/FestivalAccessibilityID.swift`, `App/Sources/Navigation/Festival/FestivalCoordinator*.swift`, `MainTab/MainTabCoordinator.swift:65-100`. 설계: `.claude/docs/specs/2026-11-21-festival-list-android-design.md`. 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

`FestivalFlow`(라이브 탭 루트, `NavDisplay`) → `FestivalRoute.List` → `FestivalListScreen`(ViewModel 연결) / `FestivalListContent`(순수 UI). 상태는 `FestivalListViewModel`(Orbit)이 갖고, 표시 규칙(정렬 · 상태 태그 · 팩 문구 · 메타 · 검색)은 `FestivalListPresentation`이다. Coordinator와 목록 ViewModel은 메인 탭 항목 범위에 있어 탭을 바꿔도 남는다.

## 동작 규칙

1. 첫 진입(`onAppear`): 현재 사용자의 최근 검색을 읽고, 처음 한 번만 저장 목록을 읽어 State에 넣은 뒤 첫 쪽(limit 10)을 요청한다. 첫 서버 응답(성공 · 실패) 전에는 저장 목록이 있어도 스켈레톤 카드 3장을 보인다.
2. 첫 쪽: 다음 커서가 없으면 그 목록으로 갱신 완료. 있으면 저장 목록을 버리고 첫 쪽으로 새로 시작하고 커서를 둔다. 다음 쪽은 서버 목록 뒤에 이어 붙이고 같은 ID는 처음 것만 남긴다. 현재 커서와 다른 쪽 응답은 버린다.
3. 더 보기: 전체 탭 · 커서 있음 · 갱신 실패 아님일 때 목록 끝에 인디케이터를 두고, 보이면 커서마다 한 번 다음 쪽을 부른다. 진행 중 · 검색 중 · 예매 완료 탭이면 부르지 않는다.
4. 전체 갱신(limit 50 반복): 검색어 첫 글자를 넣었을 때, 예매 완료 탭으로 바꿨을 때, 다음 쪽이 남은 채 첫 쪽을 받았는데 검색어가 있거나 예매 완료 탭일 때.
5. 카탈로그 요청은 한 번에 하나다. 새 요청(첫 쪽 · 다음 쪽 · 전체)은 진행 중인 것을 취소한다.
6. 당겨서 새로고침 · 빈 카드 "다시 시도": Loading + 커서 비움 → 첫 쪽. 표시는 첫 갱신 뒤 Loading인 동안(첫 갱신 전은 스켈레톤).
7. 포그라운드: 앱이 배경에서 돌아오면(`ON_STOP` 뒤 `ON_START`) 첫 쪽을 다시 받는다. 화면이 처음 붙을 때의 시작 알림은 넘긴다(iOS `scenePhase` 변화와 같음).
8. 오프라인 배지(목록 · 검색 모두 상단 바 아래): 캐시 읽기 실패 "저장된 페스티벌 목록을 읽지 못했습니다 · 연결 후 다시 시도해 주세요" > 갱신 실패 + 목록 없음 "페스티벌 목록을 불러오지 못했습니다 · 다시 시도해 주세요" > 갱신 실패 + 목록 있음 "최신 페스티벌 목록을 불러오지 못했습니다 · 저장된 목록을 표시합니다". 서버 성공은 두 실패 표시를 모두 지운다.
9. 빈 카드: 목록이 비면 `ic_bell` "불러온 페스티벌이 없어요"(실패면 "연결을 확인하고 다시 시도해 주세요." + "다시 시도", 아니면 "새 페스티벌이 등록되면 여기에 표시해요."). 목록은 있는데 예매 완료가 비면 `ic_bookmark_20` "북마크한 페스티벌이 없어요" + "전체 보기".
10. 카드: 포스터(채움 · 반경 8 · 없음/실패 `fillStrong` + 음표 36) · 이름 · 메타(`장소 · M/d(E) – M/d(E)`) · 상태 태그 · 팩 영역 · 예매처. 카드 탭 = 축제 선택(목록에 있는 ID만) → `FestivalCoordinator.onFestivalSelected(id)`가 라이브 보드를 쌓는다(`live-board-screen.md`). 테스트 태그 `festivalCard_{id}`.
11. 정렬: 진행 중 → 가까운 일정 → 날짜 없음 → 종료(종료일 늦은 순, 종료일이 잘못되면 시작일). 같으면 ID 순. 날짜는 Asia/Seoul 달력일.
12. 예매처: 첫 https 링크를 Custom Tabs로 연다(못 열면 `ACTION_VIEW`). 링크가 없으면 상단 토스트 "예매처 정보가 없습니다" 2초. 새 토스트는 이전 것을 바로 바꾼다.
13. 검색: 상단 바 검색 버튼으로 연다. 검색어가 없으면 최근 검색(전체 삭제 · 탭 = 그 검색어 입력 · 삭제)과 다가오는 페스티벌(진행 중 · 날짜 있는 예정). 검색어가 있으면 불러오는 중 · 목록 없음이면 검색 스켈레톤, 목록 없음 · 실패면 실패 카드, 결과가 없으면 `FCSearchEmpty`, 아니면 "페스티벌 N" · "아티스트 N" 섹션. 이름 · 장소 · 아티스트는 대소문자 · 라틴 발음 기호 · 전각을 무시하고 찾는다(한글은 음절 단위).
14. 최근 검색 저장: 키보드 검색 키(뒤에 초점을 놓아 키보드를 닫는다)와 결과 행 탭. 공백뿐이면 저장하지 않는다. 사용자 ID는 `CurrentUserProvider`를 구독해 정하고, 바뀌면 검색 · 탭을 처음으로 돌리고 이전 사용자의 최근 검색을 지운 뒤 새 사용자 것을 읽는다.
15. 검색 닫기: "취소" 또는 시스템 뒤로(검색 중일 때만 `BackHandler`). 검색어도 지운다. 목록 ↔ 검색은 같은 화면 안 페이드 200ms `EaseOut`. 스크롤을 시작하면 키보드를 닫는다.
16. 좌우 밀기: 30 이상 움직이고 가로가 80 넘고 세로의 1.5배를 넘으면 왼쪽 = 예매 완료, 오른쪽 = 전체. 세로 스크롤과 함께 받는다.
17. 탭 바: 라이브 스택이 목록뿐이면 메인 탭 바를 숨기고 자리도 비운다(iOS `shouldHideTabBar` · `shouldReserveTabBarSpace`). 목록 아래 여백은 20 + 내비게이션 바 높이.
18. 갱신 실패 뒤 서버 성공으로 복구되면 포스터를 다시 받는다(`imageRevision`).
19. 받은 팩 병합: 첫 진입에서 저장된 팩을 읽고(`reloadCachedPacks`), 이후 팩 저장소의 공유 상태(설계 D1)를 따른다. 라이브 탭을 다시 고르면 저장된 팩을 다시 읽는다(읽기 실패면 보이던 팩 유지). 카탈로그 항목에는 받은 팩 버전(`cachedPackVersion`)을 합치고, 카탈로그에 없는 받은 팩은 문서를 읽을 수 있는 것만 이름순(`Collator(KOREAN)`)으로 뒤에 붙인다(날짜는 팩 일자, 없으면 행사 기간; 예매처는 이름 있는 https 링크). 빈 카드 · 스켈레톤 · 오프라인 배지의 "목록 없음"은 합친 목록 기준이다.
20. 팩 영역: 받는 중이면 `Downloading`("팩 받는 중 · N%", 전체 크기를 모르면 진행 바 없이 "팩 받는 중"). 아니면 발행 전 "팩 발행을 기다리고 있어요", 받은 버전 ≥ 발행 버전 "팩 받음 · 최신 버전", 그 외 "팩 받기"(받은 적 있으면 "팩 업데이트").
21. 팩 받기: 포그라운드 ∧ 그 축제를 받는 중 아님 ∧ 발행 버전 > 0 ∧ 받은 버전 < 발행 버전일 때만 받는다(일정 상태 · 예매 여부 무관). 축제마다 하나만 돈다. Reset이면 진행률을 지우고, 저장 완료(Updated · NotModified)면 팩을 합친다. 재시도 대기 · 재접속으로 끝나거나 실패하면 상단 토스트 `ic_circle_info_16` "팩을 받지 못했어요. 다시 시도해 주세요" 2초.
22. 팩 받기 취소: 앱이 배경으로 가면(`ON_STOP`) 받는 중인 팩을 모두 취소한다. 포그라운드 · 배경은 스택 항목이 아닌 라이브 흐름(`FestivalFlow`) 수명 주기로 본다. 목록 위에 보드를 쌓아도 목록 항목 수명 주기 때문에 받기가 취소되지 않는다. 사용자 ID가 바뀌면 취소하고 세대를 올려 이전 받기의 늦은 이벤트를 버린다. 같은 사용자로 최근 검색을 다시 읽는 것만으로는 취소하지 않는다.

## 엣지 케이스

- 첫 쪽 성공 뒤 다음 쪽이 실패하면 받은 쪽들을 보이면서 배지는 "저장된 목록을 표시합니다"다(iOS와 같음, 설계 위험 FESTIVAL-006).
- 사용자가 바뀐 뒤 늦게 끝난 최근 검색 읽기 · 저장 결과는 그때 사용자와 다르면 버린다.
- 같은 탭 재선택은 아무것도 하지 않는다.

## 제약 / 가정

- 날짜는 `java.time`(`Asia/Seoul`, `M/d(E)` `Locale.KOREAN`). 지금 시각은 `Clock`으로 주입한다.
- 예매처 열기는 `androidx.browser`(Apache-2.0) Custom Tabs.

## 작업 모듈

`feature/festival/{interface,sources}`, `app`(라이브 탭 연결 · Koin).

## iOS와 다른 점

- 북마크 버튼은 숨긴다(설계 D3). 예매 완료 탭은 항상 빈 카드다.
- 처음 받은 팩의 준비 안내(iOS `prepGuideRequested`, S15)와 팩 받기 분석 이벤트(`packSync`)는 해당 화면 · 분석 모듈 전이라 보내지 않는다.
- iOS는 포그라운드 해제(`inactive`)와 배경(`background`)을 나눠 받고 배경에서만 취소한다. Android는 `ON_STOP`을 배경으로 보고 포그라운드 해제 뒤 바로 취소한다(`ON_PAUSE`는 쓰지 않는다).
- iOS `reloadBookmarks`(취소 안 함)와 `sessionChanged` · `accountChanged`(취소함)를 Android는 `CurrentUserProvider` 하나로 받는다. 사용자 ID가 바뀌면 취소한다.
- 팩 감시(WebSocket) · 선택 팩 표시는 라이브 보드(L4) 몫이라 목록은 감시를 시작하지 않는다.
- 예매처는 앱 안 웹 화면 대신 Custom Tabs다(`feature/web` 예매 범위 전까지).
- 당겨서 새로고침 표시가 요청 동안 남는다(iOS는 즉시 끝남, 설계 D9). 더 보기 인디케이터는 Material 원형 20dp · 선 2dp · `labelAssistive`.
- 카드 · 행 탭에 Material 리플이 있다.
- 토스트 애니메이션은 designkit `FCToastHost` 250ms(iOS 200ms).
- 이미지 미리 받기 · 화면 조회 분석은 옮기지 않았다.

## 변경 이력

- 2026-11-22: 첫 작성(F4).
- 2026-11-22: 팩 받기 · 진행 · 실패 토스트 · 배경 취소 · 받은 팩 병합(L3, 설계 D2 (b)).
- 2026-11-22: 카드 탭이 라이브 보드를 쌓는다. 포그라운드 감지를 라이브 흐름 수명 주기로 옮겼다(L4 · L5).
