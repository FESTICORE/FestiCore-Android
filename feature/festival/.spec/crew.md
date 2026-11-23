---
spec: crew
modules: [feature/festival]
---

# 크루 Spec

> 라이브 탭 스택에 쌓는 크루 화면(S07) — 크루 만들기 · 크루 메인 · 크루장 메뉴 · 모여 시트 · 참여 확인(S08)의 동작 규칙.

iOS 대응: `Projects/Feature/Festival/Sources/Crew/{Reducer/FestivalCrewFeature.swift, Reducer/FestivalCrewMenuFeature.swift, Reducer/FestivalCrewJoinFeature.swift, View/FestivalCrewJoinView.swift, Model/FestivalCrewPresentation.swift, View/FestivalCrewView.swift, View/FestivalCrewMenuView.swift}` · `Sources/LiveBoard/CrewCreate/FestivalCrewCreate{Feature,View}.swift` · `Sources/LiveBoard/CrewGather/FestivalCrewGather{Feature,View}.swift` · `App/Sources/Navigation/Festival/FestivalCoordinator.swift`. 테스트 `FestivalCrewFeatureTests` · `FestivalCrewCreateFeatureTests` · `FestivalCrewGatherFeatureTests` · `FestivalCrewJoinFeatureTests` · `FestivalCrewCreateRoutingTests`. 설계: `.claude/docs/specs/2026-11-23-flag-tab-android-design.md` "화면 3: Crew".

## 개요

크루 API는 `FestivalCrewUseCase`(D2)만 쓴다. 코드: `feature/festival/sources/.../crewcreate/` · `crew/` · `crewgather/` · `crewjoin/`, 경로 `FestivalRoute.CrewCreate(festivalId)` · `FestivalRoute.Crew(festivalId, crewId)` · `FestivalRoute.CrewJoin(code)`. 크루 화면이 그리는 존 · 축제 이름 · 보관 시각은 보드 선택 공유 값(`FestivalBoardSelectionUseCase`)의 문서 중 ID가 같은 것만, 첫 크루원 값은 보드의 마지막 라이브 상태(`cachedLiveBoard(festivalId).crew`, ID가 같을 때)만 쓴다(iOS는 push할 때 보드 상태를 복사한다).

## 동작 규칙

### 화면 전환 (FestivalCoordinator)

- 보드 "크루 만들기" · "같이 귀가"(로그인 상태) → `CrewCreate(festivalId)`를 쌓는다. 이미 있으면 다시 쌓지 않는다.
- 보드 크루 카드 → `Crew(festivalId, crewId)`를 쌓는다. 이미 크루 화면이 있으면 무시한다.
- 만들기 "크루 보기" → 만들기 화면을 빼고 크루 화면을 쌓는다(크루에서 뒤로 가면 보드).
- 만들기 닫기 · 크루 뒤로 → pop. 만들기 중 세션이 끊김(401) → 만들기 화면을 빼고 `CrewLogin` 경계(로그인 시트 L1 자리)를 남긴다.
- 크루 해산 · 크루에서 빠짐(`CrewClosed`) → 크루 화면을 뺀다.
- 초대 코드(`onCrewInviteOpened(code)`) → 기존 참여 확인 · 크루 화면을 빼고 `CrewJoin(code)`를 쌓는다. 참여 확인 닫기 → pop. 참여 성공 → 참여 확인을 빼고 `Crew(festivalId, crewId)`를 쌓는다. 로그인 필요 → 화면을 두고 `CrewJoinLogin(crewName)` 경계를 남긴다.
- 크루 · 만들기 · 참여 확인이 맨 위면 탭 바를 숨긴다. 참여 확인은 계정이 바뀌어도 닫지 않는다(로그인 상태만 바꾼다). 계정이 바뀌면 크루 · 만들기 화면을 뺀다.
- 만든 뒤 · 크루가 닫힌 뒤 보드 크루 카드는 보드가 다시 보일 때(`onAppear` → 조회) 새로 받는다. iOS는 아래 화면 보드에 즉시 `retryLiveBoard`를 보내지만 Android 보드는 가려진 동안 조회를 멈추므로 돌아올 때 받는 것과 같다.

### 크루 만들기 (CrewCreate)

- 이름은 12자에서 자른다. 이름을 바꾸면 입력란 오류를 지운다. trim 1~12자이고 제출 중이 아닐 때만 제출한다(trim한 이름으로 요청, 닉네임은 보내지 않는다).
- 제출 중에는 닫기(상단 뒤로 · 시스템 뒤로)를 막는다.
- 성공 → 초대 단계("{이름} 크루를 만들었어요"). 초대 링크 `festicore://crew/<코드>`, 공유 문구 "FestiCore 크루에 초대해요" + 줄바꿈 + 링크. 코드가 없으면 공유 버튼 없이 "크루 보기"만 primary로 보인다.
- 실패: 401 → 로그인 요청. alreadyInCrew · festivalEnded · invalidInput · termsRequired → 입력란 오류 문구. 그 외 → 다이얼로그 "크루를 만들지 못했어요" / "연결을 확인하고 다시 시도해 주세요." / "다시 시도"(같은 이름으로 다시 제출) · "닫기"(이름 유지, 재요청 없음).
- 안내 마지막 줄 "· {축제명+이/가} 끝나면 기록만 남아요"(이름 없으면 "페스티벌이").

### 크루 메인 (Crew)

- 들어오면 크루 상세가 없을 때만 상세를 조회한다. 그 전에는 보드 라이브 값으로 그린다. 상세를 받으면 `live`를 상세 값으로 바꾼다.
- 조회 실패: forbidden · notFound → `CrewClosed`. 그 외 → 라이브 값이 없으면 실패 카드("크루를 불러오지 못했어요" + "다시 시도"), 있으면 그대로 둔다.
- 30초마다 `now`만 갱신한다(화면이 보이는 동안). 네트워크 폴링은 없다.
- 크루장(= `crew.ownerId ?: live.ownerId`가 내 ID)만 톱니(크루장 메뉴)와 초대 링크 공유를 본다.
- 상태 보내기: 보내는 중이 아니고 지금 상태와 다르며 내 줄이 있을 때만. 내 줄을 바로 바꾸고(귀가 중이면 존을 비움, `updatedAt = now`) 바꾸기 전 존을 보낸다. 실패 → 토스트 "상태를 보내지 못했어요. 다시 눌러 주세요" + 상세 재조회.
- "모여!": 쿨다운(`gatherAvailableAt > now`)이면 "모여! · {올림 분}분 뒤 다시 가능" 비활성, 보관이면 비활성. 누르면 모여 시트(받는 사람 수 = 나를 뺀 크루원).
- 모여 응답: 응답 중 아님 · 보관 아님 · 로그인 · 모여 ID 있음 · 만료 처리 ID 아님 · 보낸 사람 아님 · 이미 고른 응답과 다를 때만 보낸다. 성공 → 응답을 저장해 버튼에 반영. 409 `GATHER_EXPIRED` → 그 모여를 만료 처리(배너 내림) + 토스트 "모여 요청이 끝나서 응답하지 못했어요" + 재조회. 그 외 → 다이얼로그 "모여에 응답하지 못했어요".
- 모여 배너 노출: 보관 아님 · 만료 처리 ID 아님 · `now < expiresAt ?: at + 10분`. 보낸 사람은 응답 요약("가는 중 n명 · 확인 m명", 0명이면 "아직 응답한 크루원이 없어요"), 받은 사람은 "{존+으로/로} 가는 중" · "확인".
- 요약: "{N}명 · {M/d(E)} 자정까지 유지"(보관 시각 1초 전 날짜), 보관이면 "{N}명 · {M/d(E)}에 끝남". 보관 시각 = 축제 마지막 `endsAt`이 KST 자정이면 그대로, 아니면 다음 날 0시 KST.
- 크루원 행: 귀가 중 · 60분 이상 미갱신이면 흐림. 시간은 상대 시간, 보관이면 `MM/dd HH:mm`. 내 줄은 "나" 태그.
- 토스트는 2초 뒤 사라지고 새 토스트가 이전 타이머를 끊는다.

### 크루장 메뉴 (시트)

- 크루장만 연다. 크루원 목록 = 라이브 크루원 중 나를 뺀 사람. 크루원이 없으면 넘기기 · 내보내기를 끈다.
- 이름 바꾸기: 현재 이름으로 시작, trim 1~12자이고 현재 이름과 다를 때만 저장 → 시트 닫고 요청(확인 없음) → "크루 이름을 바꿨어요".
- 초대 링크 다시 만들기: 시트 닫고 요청(확인 없음) → 새 코드로 바꾸고 "새 초대 링크를 만들었어요".
- 넘기기 · 내보내기 · 해산: 시트를 닫고 확인 다이얼로그("{닉네임}님에게 크루장을 넘길까요?" / "{닉네임}님을 내보낼까요?" / "크루를 해산할까요?"). 확인하면 요청, 취소하면 아무것도 하지 않는다.
- 넘기기 성공 → 크루 · 라이브의 이름 · 크루장을 바꾸고 "{닉네임}님이 크루장이 됐어요". 내보내기 성공 → 라이브에서 빼고 "{닉네임}님을 내보냈어요" + 재조회. 해산 성공 → `CrewClosed`.
- 크루장 동작 실패 → 다이얼로그 "요청하지 못했어요"(forbidden "크루장만 할 수 있어요." 등 iOS 문구).

### 모여 시트 (CrewGather)

- 공식 존 ID만 받는다. 제출 중이면 무시한다. 쿨다운이면 서버 호출 없이 "잠시 후 다시 모일 수 있어요" 다이얼로그.
- 누르면 그 존을 고르고 바로 요청한다. 성공 → 시트를 닫고 크루 메인은 상세 재조회, 보드는 라이브 재조회. 실패 → 시트를 유지하고 "집결을 요청하지 못했어요" 다이얼로그(cooldown · unauthenticated · termsRequired · forbidden · festivalEnded · 그 외 문구).
- 존이 없으면 "공개된 존이 없어요" 카드. 받는 사람 수가 0 · 모름이면 "누르면 바로 크루원에게 소리 알림이 가요".
- 보드 "모여!"와 크루 메인 "모여!"가 같은 시트를 쓴다. 보드 시트는 탭 바 숨김 대상이 아니다(iOS와 같음).

### 참여 확인 (CrewJoin, S08)

- 처음 보일 때만 초대를 불러온다(로그인 없이). 404 · 410 → "초대가 만료됐어요", 그 외 실패 → "초대를 불러오지 못했어요" + "다시 시도".
- 차단: 만료(`expiresAt <= 지금`)를 정원(`memberCount >= 12`)보다 먼저 본다. 차단이면 안내 줄을 보이고 버튼은 "닫기" 하나다.
- "참여하기"는 불러왔고 참여 중이 아닐 때만. 비로그인이면 요청 없이 로그인(크루 이름 문맥)을 요청한다. 로그인 상태가 바뀌면 `hasSession`만 바꾸고 자동 참여하지 않는다.
- 참여 실패: 401 → 로그인 요청, 409 alreadyInCrew → 참여 중 표시를 유지한 채 라이브 보드에서 지금 크루를 찾아 "기존 크루에서 나갈까요?"(못 찾으면 "크루에 참여하지 못했어요" 안내). 확인 → 나간 뒤 참여, 나가기 실패면 참여하지 않는다(403 → 크루장 안내). 그 밖은 "크루에 참여하지 못했어요" + 오류 문구.

## 경계

- 로그인 필요 지점(만들기 401)은 `LiveBoardBoundary.CrewLogin`, 참여 확인은 `LiveBoardBoundary.CrewJoinLogin(crewName)`으로 남긴다. 로그인 시트는 L1, 탭 연결은 T1.
- 약관 재동의는 화면 문구만 보인다(설계 "비로그인 · 기기 · 권한").
- 초대 딥링크(`CrewInvite(code)`) · 라이브 탭 선택은 T1에서 `FestivalCoordinator.onCrewInviteOpened`로 잇는다.
