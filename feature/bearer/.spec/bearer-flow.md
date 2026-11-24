---
spec: bearer-flow
modules: [feature/bearer, feature/featureassembly]
---

# 기수 화면 흐름 Spec

> 기수가 코드 인증 · 깃발 등록 · 프로필 · 오늘 계획 · 기수 보드 화면을 거쳐 깃발을 맡고 상태를 바꾼다. 이 문서는 화면이 들어올 때마다(BS1~BC2) 갱신한다.

iOS 대응: `Projects/Feature/Bearer/**`(`Interface/Sources/Bearer*Delegate.swift` · `Sources/{Claim,Register,Profile,Plan,Board}`).
설계: `.claude/docs/specs/2026-11-24-bearer-android-design.md`(화면 2 기수 인증 · 화면 전환 · iOS → Android 적응 · PR BS1).

## 개요

| 화면 | 패키지 | 상태 |
| --- | --- | --- |
| 기수 인증(S13 F~I) | `claim/` | BS1 |
| 보드 · 프로필 · 계획 · 등록 · 기수 여부 | `board/` · `profile/` · `plan/` · `register/` | 아직(BS2~BS4) |

경로는 `feature/bearer/interface` `BearerRoute`(NavKey)에 둔다. 화면은 SideEffect를 받아 콜백만 부르고, 경로 연결 · Coordinator는 BC1(`app` `navigation/bearer/BearerEntryCoordinator`)에서 한다.

## 기수 인증 (`claim/`, iOS `BearerClaimFeature` · `BearerClaimView`)

### 동작 규칙

1. 입력은 대문자로 바꾸고 허용 글자(`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, I · O · 0 · 1 제외)만 앞에서 6자리까지 남긴다. 붙여넣기도 같은 규칙이다. 보내는 중 · 잠김이면 입력을 무시한다. 입력이 바뀌면 틀림 · 오류 문구를 지운다(잠김 문구는 두지 않음 — 잠김이면 입력을 받지 않는다).
2. "확인"은 6자리 · 보내는 중 아님 · 잠기지 않음일 때만 보낸다(`canSubmit`). 보낼 때 오류 문구를 지운다. 키보드 완료 키도 같다.
3. 성공 → 연속 실패를 0으로 두고 `Claimed(festivalId, claim)`을 알린다.
4. 422 `InvalidCode` → 연속 실패 +1, 남은 횟수는 서버 값, 없으면 `max(0, 3 − 연속 실패)`.
5. 423 `CodeLocked(retryAfter)` → 지금부터 `retryAfter`초 잠금, 코드 비움, 잠김 문구, 1초마다 남은 시간을 다시 센다. 풀리면 잠금 · 연속 실패 · 문구를 지우고 카운트다운을 멈춘다.
6. 그 밖의 오류는 문구 하나(아래 표). `BearerError`가 아닌 예외는 `Unavailable`로 본다.
7. 뒤로(상단 뒤로 · 시스템 뒤로) → `CloseRequested`. 성공 · 닫기 때 진행 중 요청과 카운트다운을 취소하고 보내는 중을 끈다.

### 문구

| 상태 | 안내 줄 |
| --- | --- |
| 기본 | (안내) "코드는 운영팀이 기수에게 직접 알려 드려요." |
| 틀림 | (경고) "코드가 맞지 않아요 · {N}번 남았어요"(N=0이면 "코드가 맞지 않아요") + (안내) "3번 틀리면 10분 동안 입력할 수 없어요." |
| 잠김 | (경고) "3번 틀려서 입력이 잠겼어요" + (안내) "{m:ss} 뒤에 다시 입력할 수 있어요." |
| 그 외 | (경고) 아래 오류 문구 |

| 오류 | 문구 |
| --- | --- |
| `InvalidRequest` | 코드 6자리를 다시 확인해 주세요 |
| `BearerLimit` | 이 페스티벌에서 이미 다른 깃발을 맡고 있어요 |
| `CoBearerFull` | 이 깃발의 공동 기수 자리가 다 찼어요 |
| `NotAuthenticated` | 로그인한 뒤에 입력해 주세요 |
| `TermsRequired` | 새 약관에 동의한 뒤에 입력해 주세요 |
| `NotFound` | 아직 공개되지 않은 페스티벌이에요 |
| `Offline` | 인터넷에 연결되지 않았어요. 연결 후 다시 눌러 주세요 |
| 그 외 | 코드를 확인하지 못했어요. 잠시 후 다시 시도해 주세요 |

### 화면

- 상단 `FCTopBar(Back, "기수 인증")`, 스크롤 내용(좌우 `P16` · 상하 `P24`, 제목과 코드 사이 32, 코드와 안내 사이 `M12`, 안내 줄 사이 6), 아래 `FCPrimaryButton("확인" / "확인하는 중")`(좌우 `P16` · 위 `P12` · 아래 `P8`), 배경 `backgroundNormal`.
- 코드 6칸(간격 `M8`, 높이 56 · r12 · `fillNormal`, 글자 `baseTitle3Bold`), 현재 칸 캐럿 1.5×22, 테두리 1.5(틀림 `statusNegative`, 현재 칸 `labelNormal`). 위에 투명 입력창(라벨 "기수 인증 코드"). 화면이 보이면 잠기지 않았을 때 포커스, 칸을 누르면 잠기지 않았을 때 포커스.
- 접근성: 칸 묶음 "코드 6자리", 값 "비어 있음" 또는 글자를 띄어 읽기.

### Android에서 다른 점

- 키보드: `KeyboardOptions(capitalization = Characters, keyboardType = Ascii, autoCorrectEnabled = false, imeAction = Done)`. iOS `.oneTimeCode` 자동 채우기는 두지 않는다(SMS 코드가 아님).
- 카운트다운은 ViewModel `delay(1초)` 반복 + 주입한 `Clock`(iOS `continuousClock.timer` · `\.date`). 테스트는 가상 시간.
- 시스템 뒤로는 상단 뒤로와 같다(`BackHandler` → `onBackTapped`).

## 엣지 케이스

- 잠김 중 입력 · 확인은 무시된다. 잠김이 풀리기 전에 화면을 닫으면 카운트다운도 멈춘다.
- 서버가 남은 횟수를 주면 그 값을 쓰고, 연속 실패 수는 그래도 센다.

## 테스트

iOS `BearerClaimFeatureTests` 7건을 같은 이름으로 `BearerClaimViewModelTest`에 옮긴다: `filtersCode` · `claims` · `invalidCode` · `locked` · `bearerLimit` · `closeWhileSubmitting` · `back`.
