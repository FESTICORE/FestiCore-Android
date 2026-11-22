---
spec: notification-inbox-screen
modules: [feature/festival, domain/usernotification]
---

# 알림함 화면 Spec

> 보드 종 아이콘(로그인) · 새 알림 배너에서 쌓이는 알림함. 날짜 구역 목록 · 다음 쪽 · 읽음 · 선택 삭제 · 라이브 보드 알림 열기.

iOS 대응: `Projects/Feature/Festival/Sources/NotificationInbox/**`, 전환 `Projects/App/Sources/Navigation/Festival/FestivalCoordinator.swift`(`notificationInboxRequested` · `rowOpened` · `clearAccountData`), 탭 바 `MainTab/MainTabCoordinator.swift:77-100`.
설계: `.claude/docs/specs/2026-11-22-live-extras-android-design.md`(화면 2 · 화면 전환 · 탭 바 표시 · 결정 E7 · E8 · E9 · PR N3 · N4).

## 개요

`FestivalRoute.NotificationInbox`(값 없음) → `NotificationInboxScreen`/`Content` + `NotificationInboxViewModel`(항목 범위, pop하면 진행 중 요청 취소). 표시 규칙은 `NotificationInboxPresentation`(보드 배너도 같이 쓴다).

## 동작 규칙

1. 진입: 보드가 로그인일 때만 `LiveBoardBoundary.NotificationInbox`를 보내고(비로그인은 `Login`), Coordinator가 push한다. 이미 스택에 있으면 무시. 알림함이 맨 위면 탭 바를 숨긴다. 뒤로 버튼 · 시스템 뒤로 = pop(선택 모드에서도).
2. 화면: 배경 `backgroundElevated`. 상단 바 `FCTopBar(Back, "알림", backgroundNormal)`, 목록이 있으면 오른쪽 `ic_trash_can_20` "알림 선택 삭제". 선택 모드는 제목 "{N}개 선택" · 오른쪽 `ic_xmark_16` "선택 취소".
3. 첫 쪽(`Idle`일 때만): 로딩 5행 스켈레톤(원 40 + 높이 18 `r4` / 180×14 `r4`, 간격 8, 행 간격 12 · 안쪽 16, 접근성 "알림을 불러오는 중"). 실패 `FCEmptyCard(ic_bell_16, "알림을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.")` + "다시 시도"(Secondary · Medium, `Failed`일 때만). 빈 목록 "새로운 알림이 없어요" / "새 소식이 오면 이곳에서 확인할 수 있어요.".
4. 목록: `createdAt` 서울 날짜로 받은 순서대로 묶는다. 구역 머리 "오늘" · "어제" · `yyyy-MM-dd`(`baseCaption1Bold` `labelNeutral`, 좌우 16 · 위 20 · 아래 8, 머리글). 행 `FCNotificationRow`(아이콘: `crew.` 사람들 · `flag.` 깃발 · `festival.` 음악 · `pack.` 시간표 · 그 밖 종), 시각: 오늘 "방금"/"N분 전"/"N시간 전", 어제 "어제 HH:mm", 그 밖 `MM/dd HH:mm`. 시각은 60초마다 다시 계산한다. 읽음 = 서버 `readAt` ∨ 이 화면에서 읽음 처리.
5. 행 접근성: 라벨 "{제목}, {본문}, {시각}", 상태 "읽음"/"안 읽음", 선택 모드에서 선택됨, 동작 이름 "선택 상태를 바꿉니다"/"알림을 엽니다".
6. 다음 쪽: 마지막 행이 보이면 · "알림 더 보기"(Ghost · Medium, 커서 있음 ∧ 선택 모드 아님)를 누르면 불러온다(`Loaded` ∧ 선택 모드 아님 ∧ 커서 ∧ 로딩 아님). 로딩 중 진행 표시("알림을 더 불러오는 중"). 이미 있는 ID는 붙이지 않고, 받은 커서가 요청 커서와 같으면 끝으로 본다. 실패 팝업 "알림을 더 불러오지 못했어요" / "잠시 후 다시 시도해 주세요.".
7. 행 탭(선택 모드 아님): 안 읽은 알림을 처음 열 때만 화면에서 읽음 + 공유 배지에 읽음 요청(앱 범위, 실패해도 배지 재조회). 경로가 `festivals/{festivalId}/live`(앞뒤 `/` 무시, 세 조각, 항목 `festivalId`와 같음)면 열기, 아니면 "아직 열 수 없는 알림이에요" / "해당 화면을 준비 중입니다.".
8. 열기(E8): Coordinator가 보드 카탈로그(보드가 `onFestivalsChanged`로 알린 축제 ID)에 그 축제가 있으면 알림함을 pop하고 `pendingBoardFestivalId`로 보드에 넘긴다. 보드는 다른 축제면 같은 보드에서 바꾸고(같으면 그대로) 소비를 알린다. 없으면 알림함에 남고 "아직 열 수 없는 알림이에요".
9. 선택 모드: 휴지통(`Loaded` ∧ 목록 있음 ∧ 다음 쪽 로딩 아님) → 아래 고정 바(위 1 `lineNormal` 선, 배경 `backgroundElevated`, 안쪽 16 + 내비게이션 바 인셋, 간격 8): "전체 선택"/"선택 해제"(Secondary · 가득) · "삭제 ({N})"(Accent · 가득, 선택 ≥ 1 ∧ 삭제 중 아님). 행 탭은 선택 토글. 표시 · 숨김 0.2초.
10. 삭제: 목록 순서대로 선택 ID 삭제. 성공 → 지운 항목 제거 · 선택 모드 끝. 다 지웠는데 커서가 있으면 첫 쪽 다시. 그리고 안 읽은 수 재조회(공유 배지). 실패 → 선택 모드 끝 · 첫 쪽 다시(로딩) · 팝업 "알림을 삭제하지 못했어요" / "연결을 확인하고 다시 시도해 주세요." · 안 읽은 수 재조회.
11. 첫 쪽 응답은 목록 교체 · 로컬 읽음 비움(읽음 시도 기록은 남겨 같은 알림을 다시 보내지 않음). 선택은 남은 ID로 줄이고 비면 선택 모드 끝.
12. 팝업은 `FCDialog`(버튼 "확인" · "닫기", 둘 다 닫기). iOS `fcAlert`와 같은 두 버튼.
13. 계정이 바뀌면(로그아웃 · 다른 계정) Coordinator가 알림함을 스택에서 빼고 대기 축제를 비운다(E9).

## 엣지 케이스

- iOS `instanceID`(이전 알림함 응답 버림)는 두지 않는다. 항목 범위 ViewModel이 pop 때 정리돼 늦은 응답이 반영되지 않는다.
- 보드 카탈로그가 비어 있는 동안(보드가 아직 그려지지 않음) 연 알림은 "아직 열 수 없는 알림이에요"다.

## 제약 / 가정

- 로그인 응답을 stage에서 보지 못해 실제 목록 · 경로 종류는 확인하지 못했다(설계 위험 3).

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `feature/festival/interface` | feature | `FestivalRoute.NotificationInbox` |
| `feature/festival/sources` | feature | `inbox/`(Contract · ViewModel · Screen · Presentation · 프리뷰), Coordinator push/pop · 열기 · 계정 변경 · 탭 바, `FestivalFlow` 항목, 보드 축제 목록 알림 · 대기 축제 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `domain/usernotification` | domain | 목록 · 삭제 요청과 공유 배지 |
| `app` | app | `MainTabScreen`은 기존 `hidesTabBar`로 탭 바를 숨긴다(변경 없음) |

## 미해결 질문

(없음)

## 변경 이력

- 알림함 화면 · 연결(PR N3 · N4) 추가
