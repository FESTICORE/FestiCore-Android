---
spec: live-board-screen
modules: [feature/festival, domain/festival, core/datastore, service/api, app]
---

# 라이브 보드 화면(S01) Spec

> 목록에서 축제를 고르면 쌓이는 화면. 헤더(축제 바꾸기) · 팩 받는 중 · 미공개 · 개장 카운트다운 · 다음 공연 · 지금/다음 무대 · 출처 · 공식 인스타그램 · 내 크루 · 팔로우한 깃발 · 활성 깃발 · 종료 요약 · 귀가 · 스켈레톤 · 빈/실패 카드 · 오프라인 배지.

iOS 대응: `Projects/Feature/Festival/Sources/LiveBoard/`(`View/FestivalLiveBoardRouteView.swift` · `View/FestivalLiveBoardScreen.swift` · `Components/*` · `Model/FestivalLiveBoardPresentation.swift` · `Model/FestivalLiveBoardScreenModel.swift` · `Model/FestivalHomeDeparture.swift` · `Reducer/FestivalLiveBoardFeature.swift` · `Switcher/*`), `FestivalList/Reducer/FestivalFeature.swift`의 보드 부분, `App/Sources/Navigation/Festival/FestivalCoordinator.swift`, `MainTab/MainTabCoordinator.swift:92-113`. 설계: `.claude/docs/specs/2026-11-22-live-board-android-design.md`(L4 · L5). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

`FestivalCoordinator.onFestivalSelected(id)` → `FestivalRoute.LiveBoard(festivalId)` push → `LiveBoardScreen`(ViewModel 연결) / `LiveBoardContent`(순수 UI). 상태는 `LiveBoardViewModel`(Orbit, 목록과 별도, 설계 D1, 스택 항목 범위)이 갖고, 표시 규칙은 `LiveBoardPresentation`(단계 · 문구 · 정렬) · `LiveBoardScreenModel`(오프라인 · 귀가 · 종료 요약) · `HomeDeparture`(귀가 출발편)다. 받은 팩 · 받는 중 진행률은 팩 저장소 공유 상태(`FestivalPackUseCase.state`)를 따른다.

## 동작 규칙

1. 진입: 보드가 화면에 들어오면(`onAppear`) 보이는 중으로 두고 안내 숨김을 다시 읽는다. 처음이면 카탈로그(목록에서 넘겨받음, 비었으면 저장 목록) · 공유 팩으로 선택 축제를 그리고, 선택을 저장(`FestiCore.selectedFestivalID`)한 뒤 팩 감시 · 라이브 폴링 · 내 일정을 시작한다. 이어서 온라인 상세를 받는다(같은 축제 상세가 있거나 받는 중이면 건너뜀).
2. 화면에서 빠지면(`onDisappear`) 라이브 폴링 · 공식 소식 · 상세를 멈춘다. 팩 감시는 그대로 둔다(iOS와 같음). pop하면 ViewModel이 정리된다.
3. 포그라운드: 흐름 바깥 수명 주기의 `ON_STOP`이면 팩 감시 · 폴링을 먼저 멈추고 배경으로 표시한다. 그 뒤 `ON_START`면 팩 감시 실패를 지우고 팩 감시 · 폴링 · 내 일정을 다시 시작한다.
4. 라이브 폴링: 포그라운드 ∧ 보이는 중일 때만. 조회 → 성공 반영 / `FESTIVAL_NOT_FOUND` 미공개 / 429는 상태 유지 / 그 밖은 실패 → `nextPollDelay`(35초, 429면 `max(35초, Retry-After)`) 기다림을 반복한다. 새로 시작하면 이전 폴링을 취소한다.
5. 성공 반영: 축제 ID · 계정 세대가 같을 때만. `serverAt`이 바뀌었거나 기준점이 없으면 기준점 = 지금(304로 같은 `serverAt`이면 유지), 실패 · 미공개 해제, 수신 시각 = 지금. 미공개는 스냅샷 · 기준점 · 수신 시각을 비운다.
6. 공식 소식: 폴링과 함께 300초마다. 실패는 무시하고 바뀐 값만 반영한다(처음 기준 `Unavailable`). 다른 축제 소식은 보이지 않는다. 게시물 탭 = Custom Tabs(설계 D10).
7. 화면 시각: `serverAt + (기기 지금 − 기준점)`으로 보정하고 30초마다 다시 계산한다. "N분 전 업데이트" · 오프라인 배지 경과는 기기 시각으로 잰다.
8. 팩 감시: 선택 축제 팩이 기기에 있고 포그라운드일 때만. Updated · NotModified면 저장하고 문서가 있으면 Live(없으면 표시 문서 없음 Unavailable · 있음 Cached), 팩(etag · 버전 · 문서)이 바뀌었으면 라이브를 즉시 한 번 다시 조회한다. RetryAfter → 문서 없으면 Connecting 있으면 Cached, Reconnecting → Reconnecting, 흐름 실패 → Offline + 감시 실패.
9. 공유 팩 상태: 받는 중 진행률이 바뀌면 "팩 받는 중 · N%" 카드를 갱신한다. 선택 축제 팩이 새로 생기거나 사라지면 표시 팩과 감시를 다시 맞춘다.
10. 표시 문서 = 온라인 상세 ?: 받은 팩. 상세 응답의 ID가 다르면 실패로 본다. 상세 실패는 보이는 중일 때만 반영한다.
11. 팩 로딩 스켈레톤(`isPackLoading`): 표시 문서 없음 ∧ 상세 받는 중 → 참, 상세 실패 → 거짓, 받은 팩은 있는데 문서 아직 없음 ∧ 감시 실패 아님 ∧ Loading/Connecting → 참.
12. 오프라인 배지: (라이브 실패 ∧ 미공개 아님) ∨ Offline ∨ Reconnecting. 수신 기록 없음 "오프라인 · 저장된 정보", 있음 "오프라인 · {방금|N분 전|N시간 전|N일 전} 정보".
13. 섹션 순서: 미공개면 미공개 카드만. 종료 단계면 종료 요약 → 귀가 → 공식 인스타그램 → 내 크루 → 팔로우한 깃발(`flagsEnabled != false`). 그 밖은 (마지막 1시간이면 귀가) → (팩 로딩이면 보드 스켈레톤, 아니면 일정 카드) → 내 일정 실패 줄 → (카운트다운 없으면) 지금/다음 무대 → 공식 인스타그램 → 내 크루 → (`flagsEnabled != false`) 팔로우한 깃발 · 활성 깃발.
14. 단계: 팩 날짜 시각만으로 고른다. 카탈로그 마지막 날짜와 팩 마지막 날짜가 다르면 종료를 추정하지 않는다. 다음 개장 전이고 진행 중인 날이 없으면 개장 전(카운트다운), 최종 종료 1시간 전부터 마지막 1시간, 최종 종료부터 종료.
15. 내 일정: 로그인 ∧ 포그라운드일 때 요청마다 번호를 올리고, 축제 · 요청 번호 · 세대 · 세션이 같은 응답만 반영한다. 실패해도 이전 일정을 둔다. 실패 줄 "다시 시도"로 다시 부른다.
16. 세션: `CurrentUserProvider`가 바뀌면 세대를 올리고 스냅샷 · 공식 소식 · 내 일정을 비운 뒤 보이는 중이면 폴링 · 내 일정을 다시 시작한다. 같은 값이면 무시한다.
17. 축제 바꾸기 시트(`FCSheet`): 헤더 탭 → 기기에 받은 팩이 있는 축제만 목록 정렬 규칙으로. 부제는 오늘이 팩 날이면 "오늘 · DAY n {시작 전|진행 중|종료}", 아니면 `[검색 배지, 장소]`. 고르면 시트를 닫고 같은 보드에서 축제를 바꾼다(보드 · 상세 · 내 일정 초기화 → 선택 저장 → 상세 · 팩 감시 · 폴링 · 내 일정 재시작). "전체 페스티벌 보기" = 시트 닫고 목록으로 pop.
18. 팔로우 안내 닫기 → `FCDialog`(가운데 정렬) "이 안내를 숨길까요?" → "숨기기"면 그 축제 ID를 `FestiCore.hiddenFollowedFlagGuides`에 넣고 안내를 숨긴다.
19. 탭 바: 보드가 맨 위면 보인다. 시트나 다이얼로그가 떠 있으면 숨긴다(`LiveBoardState.isPresentingModal` → `FestivalCoordinator.hidesTabBar`, 설계 D9). 보드 위에 알림함이 쌓이면 숨기고 존 지도는 보인다. 표시 · 숨김은 애니메이션 없이 바로.
20. 뒤로: 보드에 뒤로 버튼이 없다. 시스템 뒤로 · 예측 뒤로는 `NavDisplay`가 pop한다(설계 D7). 시트 · 다이얼로그가 떠 있으면 뒤로는 그것을 닫는다.
21. 경계: 알림(로그인이면 알림함, 아니면 로그인) · 기수 · 존 칩 · 크루 열기 · 크루 만들기(비로그인이면 크루 로그인) · 모여!(로그인 ∧ 온라인 ∧ 크루 ∧ 종료 전) · 로그인 카드 · 깃발 찾기 · 가는 길 · 같이 귀가(= 크루 만들기)는 `LiveBoardSideEffect.Boundary`로 Coordinator에 알린다. 존 지도 · 알림함은 Coordinator가 쌓고, 나머지는 받아 두기만 한다(`lastBoundary`).
22. 귀가 카드: 받은 팩의 `directions`(마지막 셔틀 · 막차, 셔틀 정류장, 역 · 역 도보 분)만 쓴다. 출발편은 지금 걸어 닿는 막차 → 셔틀 → 첫 미출발편(`HH:mm`은 18시 이후에 본 06시 전 값이면 다음 날). 줄은 ISO 시각일 때만 보인다(iOS와 같음).
23. 알림 배지 · 배너(설계 E7, iOS `FestivalFeature+NotificationUnreadCount`): 로그인일 때 보드가 보일 때와 세션이 바뀔 때 공유 배지(`UserNotificationBadgeUseCase.refresh`)를 다시 조회한다(새 조회가 이전 조회를 취소). 종 아이콘 `FCTopBar(badgeCount = 안 읽은 수)`(0이면 없음, 99 초과 "99+"). 24시간 안의 최신 안 읽은 알림이 있으면 상단 바 · 오프라인 배지 아래(좌우 16 · 위아래 8)에 배너: 40×40 `primaryNormal` `r12` 칸의 아이콘 16(`primaryOnPrimary`), 제목 `baseCalloutBold` `labelNormal` + 오른쪽 시각 `baseCaption1Regular` `labelAssistive`, 본문 `baseFootnoteRegular` `labelNeutral` 2줄, 간격 12/4, 안쪽 16, 배경 `backgroundElevated` `r20`. 배너 탭 = 종 탭, 동작 이름 "알림함으로 이동합니다". 시각은 알림함과 같은 규칙(기기 시각). 계정이 바뀌면 배지 0 · 배너 제거 · 조회 취소(`clear`). 비로그인이면 공유 값이 있어도 보이지 않는다.
24. 알림함에서 다른 축제 알림을 열면(설계 E8) Coordinator가 넘긴 대기 축제로 같은 보드에서 바꾼다(같은 축제면 그대로). 보드는 바꿀 수 있는 축제 ID(`festivals`)를 Coordinator에 알린다.

## 엣지 케이스

- 선택 축제를 카탈로그 · 받은 팩에서 못 찾으면 Back 상단 바 "라이브 보드" + (카탈로그 읽는 중이면 보드 스켈레톤, 아니면 "페스티벌 정보를 찾을 수 없어요").
- 라이브 429는 보이던 값을 두고 배지를 보이지 않는다. `FESTIVAL_NOT_FOUND`는 미공개 카드만, 배지 없음.
- 배경으로 가는 사이에 폴링이 한 번 더 나가지 않게 상태보다 작업을 먼저 멈춘다.
- 목록 위에 보드를 쌓아도 목록 화면의 팩 받기는 계속된다(포그라운드를 흐름 바깥 수명 주기로 본다).

## 제약 / 가정

- 날짜는 `java.time`(`Asia/Seoul`, `HH:mm` · `MM.dd (E)` · `MM/dd HH:mm`, `Locale.KOREAN`). 기기 시각은 `Clock`, 폴링 기다림은 `sleep`으로 주입한다.
- 설정 저장은 `core/datastore` `LiveBoardPreferenceStore`(DataStore `festicore_live_board`)를 `FestivalLiveBoardPreferenceUseCase`로 쓴다.

## 작업 모듈

`feature/festival/{interface,sources}`(`liveboard/`), `domain/festival`(`FestivalLiveBoardPreferenceUseCase` · `FestivalPackDocument.Directions`), `service/api`(`FestivalPackDirectionsDto`), `core/datastore`, `app`(탭 바).

## iOS와 다른 점

- 뒤로 버튼 · 왼쪽 가장자리 드래그 대신 시스템 뒤로(설계 D7).
- 귀가 도보 분은 서버 역 도보 분만 쓴다. Apple 지도 계산 · 위치 권한 · 길찾기는 없다(설계 D6). 받은 팩 문서에서 바로 읽어 귀가 로딩 스켈레톤이 없다(iOS는 팩 상세를 따로 읽는 동안 높이 204 스켈레톤).
- "모여!" 시트 · 크루 · 기수 보드 · 준비 안내는 콜백만(경계). 존 지도 · 알림함은 화면이 있다(`zone-map-screen.md` · `notification-inbox-screen.md`).
- 배지는 보드 State가 아닌 공유 저장소 하나에 있다(설계 E7). 알림함 삭제 뒤 배지는 Coordinator 전달 없이 같은 저장소로 바뀐다.
- 보드에서 받기를 시작하지 않으므로 보드 토스트(팩 실패)는 없다. 받는 중 진행률은 목록과 같은 공유 상태다.
- 세션은 `CurrentUserProvider` 하나로 본다. iOS의 "사용자 ID를 확인하지 못한 세션"은 Android에서 비로그인과 같다.
- 크루 아바타 라이트 톤 고정은 하지 않았다(`FCAvatar`가 테마를 따른다).
- 시트 포스터는 목록 포스터를 반경 12로 다시 자른다. 카드 · 행 탭에 Material 리플이 있다.
- 화면 조회 · 팩 동기화 분석, Live Activity · 위젯은 옮기지 않았다.

## 변경 이력

- 2026-11-22: 첫 작성(L4 보드 화면 · L5 연결).
- 2026-11-22: 알림 배지 · 24시간 배너 · 알림함 연결(N2 · N4).
