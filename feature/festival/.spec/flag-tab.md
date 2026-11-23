---
spec: flag-tab
modules: [feature/festival]
---

# 깃발 탭 Spec

> 선택 축제의 깃발을 태그 필터 · 정렬 · 팔로우로 보여 주는 깃발 목록(S04)의 동작 규칙.

iOS 대응: `Projects/Feature/Festival/Sources/Flag/{Reducer/FestivalFlagFeature.swift, Reducer/FestivalFlagDirectory.swift, View/FestivalFlagView.swift}`, 테스트 `FestivalFlagFeatureTests` · `FestivalFlagDirectoryTests`. 설계: `.claude/docs/specs/2026-11-23-flag-tab-android-design.md` "화면 1: Flag".

## 개요

깃발 탭 루트. 라이브 보드와 같은 조회(`FestivalLiveBoardUseCase.liveBoard` = `GET festivals/{id}/live`(`X-Device-Id`) + `GET festivals/{id}/flags` 합성)의 `directoryFlags`를 목록으로 보인다. 팔로우는 `FestivalFlagUseCase.setFollowing`을 쓴다. 코드: `feature/festival/sources/.../flag/`(`FlagViewModel` · `FlagDirectory` · `FlagPresentation` · `FlagScreen`).

## 동작 규칙

### 목록 (Flag)

#### 조회 · 응답 반영

- 화면에 들어오면 `isVisible = true` 후 조회, 나가면 `isVisible = false`, 조회 취소, 진행 중 팔로우 · 토스트 · 정렬 메뉴를 비운다. 성공한 팔로우 값(override)은 남긴다.
- 조회는 하나만 돈다. 새로 시작하면 이전 것을 취소하고, 이전 조회가 남긴 "한 번 건너뛰기"(`skipsNextLoad`)를 모두 지운다.
- 축제가 없으면 조회하지 않고 `Idle`로 둔다("축제를 선택해 주세요" 카드).
- 응답은 `festivalId` · `generation` · `isVisible`이 모두 같을 때만 반영한다. 다르면 상태를 바꾸지 않는다.
- 축제가 바뀌면 스냅샷을 비우고 `Idle`, `generation + 1`, override를 모두 버린다. 보이는 중이면 다시 조회하고 아니면 조회를 취소한다. 같은 축제가 다시 와도 진행 중 팔로우 · 토스트 · 메뉴 · override는 비운다(iOS와 같음).
- 계정이 바뀌면 같은 방식으로 스냅샷 · override를 비우고 `generation + 1` 후 보이는 중이면 다시 조회한다.
- 실패: `InvalidFestivalId` → "축제 정보가 올바르지 않습니다", `InvalidResponse` → "깃발 정보 형식이 올바르지 않습니다", 그 외 → "깃발을 불러오지 못했습니다". 스냅샷이 있으면 본문 뒤에 " 이전 깃발을 표시합니다."를 붙이고 목록을 둔다. `Failed` + 스냅샷이면 오프라인 배지("오프라인 · N분 전 정보", 1분 미만 · 시각 없음은 "방금 전")를 60초마다 갱신하고 카드의 상태 · 존을 숨긴다.
- 429(`RateLimited(retryAfter)`)는 이전 목록을 둔 채 `retryAfter`만큼 기다렸다가 다시 조회한다.

#### 정렬 · 필터

- 태그는 단일 선택(`전체` · 슬램 · 떼창 · 기차놀이 · 차분 · 초보 환영, raw `slam · sing · train · calm · newbie`). 고르면 정렬 메뉴를 닫는다. 카드 태그는 알려진 값만 한글로 보인다.
- 정렬은 같으면 서버 순서를 지킨다(안정 정렬).
  - 활동 중 순: `active < resting < 모름 < scheduled < ended`, 같은 순위는 표시 팔로워 수 내림차순.
  - 팔로워 많은 순: 표시 팔로워 수 내림차순.
  - 최근 업데이트 순: `updatedAt` 내림차순, 시각 없는 깃발은 뒤.
- 표시 팔로워 수 = override 수 → 스냅샷 수 → 0. 문구 "팔로워 N"(ko_KR 천단위). 존 = `zoneId ?: homeZoneId`의 팩 존 이름, 없으면 "위치 미정".
- 정렬 메뉴가 열린 동안 아무 곳이나 누르면 닫힌다. 정렬을 고르면 닫힌다.

#### 팔로우 (비낙관)

- 낙관 갱신하지 않는다. 요청 중인 깃발은 다시 눌러도 무시하고, 요청 중 · 새로고침 실패 중(`Failed` · `InvalidFestivalId` · `InvalidResponse` · `DeviceNotRegistered`)에는 버튼을 끈다.
- 요청 직전 기준 수(`baseCount`) = 직전 성공 override의 수 → 카드의 서버 수.
- 성공하면 override(`isFollowing` · `followerCount` · `baseCount`)를 저장한다. 그때 조회 중이었으면 그 조회 결과는 팔로우 전일 수 있어 한 번 건너뛴다(`skipsNextLoad`).
- 실패: `LoginRequired` → `LoginRequested` 경계, `TermsRequired` → `TermsAcceptanceRequested` 경계(세션 프로필 재확인), `LimitReached` → 토스트 "팔로우는 100개까지 할 수 있어요", 그 외 → "팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요". 토스트는 2초 뒤 사라진다.
- 탭 이탈 · 축제 · 계정 변경으로 비운 요청의 늦은 응답은 버린다.
- 상세에서 바꾼 팔로우는 `onFollowChangedElsewhere(result, baseCount)`로 같은 override 규칙에 넣는다.

#### reconcile (받아들인 새 스냅샷, 조회 중이었을 때만)

- `skipsNextLoad`면 이번에는 건너뛰고 표시만 끈다.
- 아니면 팔로우 여부가 스냅샷(`followedFlags`)과 같아지면 override의 팔로우 여부를 지운다(304 재사용 응답은 팔로우 전 상태일 수 있다).
- 팔로워 수는 서버 수가 `baseCount`와 달라졌을 때만 지운다(공개 목록은 최대 10분 캐시).
- 둘 다 지워지면 override를 없앤다.

### 상세 (FlagDetail)

iOS `FestivalFlagDetailFeature` · `FestivalFlagDetailView`. `FlagDetailViewModel` · `FlagDetailPresentation` · `FlagDetailContext`.

#### 조회 · 시계

- 들어오면 `loadGeneration + 1`, 지금 시각을 다시 읽고 불러온다. 받은 상세가 있으면 그대로 두고, 실패 화면이었으면 스켈레톤으로 바꾼다. 60초마다 `now`를 바꾼다.
- 나가면 조회 · 시계 · 팔로우 · 신고 요청을 모두 취소하고 `loadGeneration + 1`, 요청 중 표시를 끈다. 늦은 응답은 세대 · 요청 중 여부로 버린다.
- 다시 불러오기 실패: notFound → "깃발을 찾을 수 없어요", 받은 상세 있음 → 마지막 상세 + 오프라인 배지(`FlagPresentation.offlineText`), 없음 → "깃발 정보를 불러오지 못했어요" + "다시 시도".
- 문맥 변경은 `hasSession` · `accountId`가 바뀔 때만 팔로우 override · 요청 중 · 신고 시트를 비우고 스켈레톤으로 다시 불러온다. 그 밖에는 문맥만 바꾼다.

#### 지금 카드 문구

- ended → "오늘 활동을 마쳤어요".
- 존 = `zoneId ?: homeZoneId`의 팩 이름, 없으면 "위치를 아직 알리지 않았어요".
- 활동: resting → "쉬는 중", scheduled → "예정"(활동 값이 있어도), 그 밖에는 슬램 · 떼창 · 기차놀이 · 차분 · 쉬는 중, 값 없음 "활동 중".
- 출처: 오프라인 "마지막으로 받은 정보예요" → 30분 이상 "{상대시간} 정보예요 · 지금은 다를 수 있어요" → 1분 미만 "기수가 방금 알렸어요" → "기수가 {상대시간}에 알렸어요". 상대시간은 `LiveBoardPresentation.relativeTime`.
- 흐리게: 30분 이상 또는 오프라인. 쉬는 중은 흐리게 하지 않는다.
- 오늘 계획: 기수 순서 유지, 팩에 없는 공연은 건너뛴다. 시각 `HH:mm`(Asia/Seoul). 끝났거나 깃발이 ended면 지남, 시작했으면 공연 중, 아니면 예정.

#### 팔로우 · 신고 갈래

- 팔로우는 상세가 있고 요청 중 · 오프라인이 아닐 때만. 비로그인 → `LoginRequested`.
- 팔로우 성공 → override · `loadGeneration + 1` → `FollowChanged(flagId, isFollowing, followerCount)` → 재조회 → 팔로우했으면 "{이름}님을 팔로우했습니다" 토스트. 실패: loginRequired → `LoginRequested`, termsRequired → `TermsAcceptanceRequested`, limitReached → "팔로우는 100개까지 할 수 있어요", 그 외 → "팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요".
- 신고는 상세가 있고 요청 중이 아닐 때만. 비로그인 → `ReportLoginRequested`. 시트에서 사유를 골라 제출하면 시트를 닫고 요청한다(사유 없으면 제출하지 않음).
- 신고 성공 → "신고를 접수했어요". 실패: alreadyReported → "이미 신고한 깃발이에요", loginRequired → `ReportLoginRequested`, termsRequired → `TermsAcceptanceRequested`, 그 외 → "신고하지 못했어요. 잠시 후 다시 시도해 주세요".
- 토스트는 2초 뒤 사라진다. 공유는 상세를 받은 뒤에만 `festicore://flag/<id>`를 `ACTION_SEND` 공유 시트로 보낸다.

#### 잠금화면 표시 (이번 범위 아님)

- iOS의 "잠금화면에 표시" 행 · 대표 깃발(pin) 상태 · 동작 · "잠금화면 표시를 바꾸지 못했어요" 토스트는 라이브 액티비티(진행 중 알림) 단계에서 넣는다(설계 결정 F4-1~3). 상세 화면은 `setPinnedFlag`를 부르지 않는다. 문맥의 `pinnedFlagId`는 그 단계에서 쓴다.

### 전환 (FlagFlow · FlagCoordinator)

- 깃발 탭 루트는 목록(`FlagRoute.Directory`)이다. 목록 카드 · 깃발 링크(`FlagCoordinator.openDetail`)는 상세(`FlagRoute.Detail`)를 push한다. 기존 상세는 지우고 하나만 둔다.
- 상세 뒤로 · 시스템 뒤로는 목록으로 pop한다. 목록뿐이면 스택을 비우지 않는다. 전환은 라이브 탭과 같은 `FestivalTransitions`다(쟁점 F7).
- 상세에서 바꾼 팔로우는 목록 카드에 반영한다. 기준 수는 목록 스냅샷 `directoryFlags`의 같은 깃발 팔로워 수다.
- 계정이 바뀌면 목록에 알리고 스택은 그대로 둔다. 열린 상세는 새 문맥(`FlagDetailContext`)으로 다시 불러온다.
- 탭 바는 목록 · 상세 모두 보인다(숨김 콜백 없음). 같은 탭을 다시 눌러도 루트로 pop하지 않는다.
- 로그인 · 약관 요청은 `FlagFlow` 콜백으로 올린다. 메인 탭이 문맥을 정해(목록 = 깃발 탭 기본, 상세 = 팔로우 · 신고) 로그인 시트를 열고, 약관 요청은 프로필 재확인으로 넘긴다.
- 목록 입력 · 상세 문맥은 라이브 스택에서 뒤에서부터 찾은 보드(`FestivalCoordinatorState.flagBoard`)와 현재 사용자로 만든다. 보드가 없으면 축제 · 존 이름을 비운다. 깃발 링크는 깃발 탭을 고르고 상세를 연다.
- 로그인 시트가 떠 있는 동안 탭 바를 숨긴다.

## 엣지 케이스

- 빠르게 팔로우 → 언팔로우: 두 번째 요청의 기준 수는 첫 성공 수다. 중간 캐시(첫 성공 수와 같은 서버 수)가 와도 마지막 수를 유지한다.
- 다른 축제 · 이전 세대의 늦은 응답은 무시한다.
- 필터 결과가 0개면 "이 성향의 깃발이 아직 없어요", 깃발 자체가 0개면 "깃발이 없습니다".

## 제약 / 가정

- 로그인 시트 · 약관 재동의 화면은 만들지 않고 SideEffect(경계)만 보낸다.
- screen_view · flagFollow 분석 이벤트는 Android feature 모듈에 분석 경로가 없어 보내지 않는다.
- Android `FestivalLiveBoardError`에는 iOS `deviceNotRegistered`가 없어 `DeviceNotRegistered` 상태 · 문구는 두되 지금은 도달하지 않는다.

## 작업 모듈

| 모듈                       | 계층    | 변경 내용                                                                              |
| -------------------------- | ------- | -------------------------------------------------------------------------------------- |
| `feature/festival/sources` | feature | `flag/` Contract · ViewModel · Directory · Presentation · Screen · Previews, Koin 등록 |

## 영향 범위

| 모듈  | 계층   | 영향 이유                             |
| ----- | ------ | ------------------------------------- |
| `app` | 진입점 | 깃발 탭 자리 · 깃발 링크 · 로그인 시트 · 문맥 동기화 |

## 미해결 질문

- 기기 미등록 오류를 라이브 보드 도메인 오류로 둘지(지금은 `Unavailable`로 합쳐진다).

## 변경 이력

- 깃발 목록 동작 규칙 추가
- 깃발 상세 동작 규칙 추가(잠금화면 표시는 라이브 액티비티 단계로 제외)
- 목록 → 상세 전환 규칙 추가
