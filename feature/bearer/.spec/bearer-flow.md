---
spec: bearer-flow
modules: [feature/bearer, feature/featureassembly]
---

# 기수 화면 흐름 Spec

> 기수가 코드 인증 · 깃발 등록 · 프로필 · 오늘 계획 · 기수 보드 화면을 거쳐 깃발을 맡고 상태를 바꾼다. 이 문서는 화면이 들어올 때마다(BS1~BC2) 갱신한다.

iOS 대응: `Projects/Feature/Bearer/**`(`Interface/Sources/Bearer*Delegate.swift` · `Sources/{Claim,Register,Profile,Plan,Board}`).
설계: `.claude/docs/specs/2026-11-24-bearer-android-design.md`(화면 2 기수 인증 · 화면 3 깃발 프로필 · 화면 4 오늘 계획 고르기 · 화면 전환 · iOS → Android 적응 · PR BS1 · BS2).

## 개요

| 화면 | 패키지 | 상태 |
| --- | --- | --- |
| 기수 인증(S13 F~I) | `claim/` | BS1 |
| 깃발 프로필(S10 A~E) · 오늘 계획 고르기 | `profile/` · `plan/` | BS2 |
| 보드 · 등록 · 기수 여부 | `board/` · `register/` | 아직(BS3~BS4) |

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

## 깃발 프로필 (`profile/`, iOS `BearerProfileFeature` · `BearerProfileView` · `BearerZoneButton`)

경로 `BearerRoute.Profile(festivalId, flagId, isNewBearer)`. 코드 등록 응답의 프로필은 경로에 싣지 않고 Coordinator가 ViewModel 인자로 넘긴다(없으면 서버에서 읽는다).

### 동작 규칙

1. 새 기수(`isNewBearer`)가 받은 서버 임시 이름(`새 깃발 ` 접두)과 기본 태그는 빈 입력으로 보인다. 프로필을 받았으면 불러오는 중이 아니다.
2. 화면이 보이면 보드(공동 기수 수 · 사진)와 팩(축제 이름 · 존 · 공연)을 함께 읽는다. 팩 실패는 무시한다. 보드 실패는 프로필이 없을 때만 실패 화면("깃발 프로필을 불러오지 못했어요" + "다시 시도"), 있으면 입력을 계속한다. 읽기는 하나만 돈다.
3. 이름 2~20 · 소개 60은 코드포인트로 센다(넘는 입력은 코드포인트 단위로 자른다). 태그 1~3(다시 누르면 뺌, 3개면 더 안 받음), 존은 다시 누르면 해제. 이름을 바꾸면 이름 · 저장 오류를 지운다.
4. 저장(`canSave`: 이름 길이 · 소개 길이 · 태그 수 · 이름 오류 없음 · 업로드 중 아님 · 저장 중 아님 · 불러오는 중 아님). 이름은 앞뒤 공백을 지워 보내고, 계획은 이 화면에서 다시 골랐을 때만 보낸다. 성공 → 저장 완료 화면. `NameTaken` → 이름 칸 오류, 그 외 → 아래 저장 문구.
5. 사진: 고르면 바로 올린다(저장 중이면 무시). 진행률 반영, 완료면 고른 바이트 보관 · 주소를 썸네일(없으면 원본)로 바꿈. 업로드 중 사진 칸을 누르면 업로드만 취소하고 이전 사진을 둔다. 뒤로 가면 업로드를 취소한다.
6. 초대: 보내는 중이 아니면 초대 코드를 받아 팝업. 팝업의 확인 · 닫기는 팝업만 닫는다.
7. SideEffect: `Finished(flagId)`(완료) · `CloseRequested`(뒤로) · `PlanRequested(festivalId)`(오늘 계획). 계획 고르기 결과는 부모가 `onPlanChanged(setIds)`로 넘긴다.

### 문구

| 상황 | 문구 |
| --- | --- |
| 머리글 | 새 기수 "{축제 이름 또는 '페스티벌'} 기수가 됐어요", 아니면 "깃발 프로필을 고쳐요" / "팔로워에게 보일 깃발 프로필이에요.\n언제든 고칠 수 있어요." |
| 저장 버튼 | "사진 올리는 중이에요" / "저장하는 중이에요" / 새 기수 "등록하고 기수 시작하기" / "저장하기" |
| 이름 중복 | 이미 있는 깃발 이름이에요. 다른 이름을 지어 주세요. |
| 사진 | 작음 "256×256보다 큰 사진을 골라 주세요" · 큼 "사진이 너무 커요. 다른 사진을 골라 주세요" · 형식(`UnsupportedPhoto` · `InvalidRequest`) "이 사진은 올릴 수 없어요. JPEG·PNG·HEIC 사진을 골라 주세요" · 오프라인 "인터넷에 연결되지 않아 사진을 올리지 못했어요" · 그 외 "사진을 올리지 못했어요. 다시 골라 주세요" |
| 저장 | 오프라인 "인터넷에 연결되지 않았어요. 연결 후 다시 눌러 주세요" · 기수 아님 "이 깃발의 기수가 아니에요" · 존 없음 "고른 존을 찾을 수 없어요. 다른 존을 골라 주세요" · 약관 "새 약관에 동의한 뒤에 저장해 주세요" · 로그인 "로그인한 뒤에 저장해 주세요" · 그 외 "저장하지 못했어요. 잠시 후 다시 시도해 주세요" |
| 초대 팝업 | "공동 기수 초대 코드" / "{코드}\n24시간 안에 함께할 기수가 기수 인증 코드에 입력하면 돼요."(확인 · 닫기) |
| 초대 실패 | "초대하지 못했어요" / 원래 기수 아님 "처음 깃발을 만든 기수만 초대할 수 있어요." · 정원 "공동 기수는 깃발마다 3명까지예요." · 오프라인 "인터넷에 연결되지 않았어요." · 그 외 "초대 코드를 만들지 못했어요. 잠시 후 다시 시도해 주세요." |

### 화면

- 배경 `backgroundElevated`, `FCTopBar(Back, "깃발 프로필")`, 섹션 간격 28 · 그룹 간격 12, 패딩 `P16`(아래 32), 아래 바(`materialGlass`, 버튼 간격 `P4`, 좌우 `P16` · 위 `P12` · 아래 `P8`).
- 머리글 · `FCPhotoPicker`(서버 사진만 있으면 버튼 "바꾸기") · 이름 · 소개 `FCTextField` · 성향 태그(`FCChip` 5개 가로 스크롤, "{n} / 3") · 주로 있는 존(`BearerZoneButton` 2열, 팩에 존이 없으면 숨김) · "오늘 계획 · 함께하는 기수" 카드(r16 · 1 테두리, `FCListRow` 3줄) · 저장 오류.
- 불러오는 중 스켈레톤 64 · 88×88 · 52 · 52(간격 `P24`).
- 저장 완료: `FCDoneHero` + "깃발 목록에서 이렇게 보여요" + `FCFlagCard`(디렉토리, 팔로우 버튼 없음, "팔로워 {n}", 예정 칩), 사이 40, 위아래 24 안에서 세로 가운데. 아래 `FCPrimaryButton("완료")` · `FCButton(Ghost, "공동 기수 초대하기")`.

### Android에서 다른 점

- 사진은 Android Photo Picker(`PickVisualMedia(ImageOnly)`)로 하나만 받는다. 권한(`READ_MEDIA_IMAGES` · `READ_EXTERNAL_STORAGE`)을 선언 · 요청하지 않는다. Photo Picker가 없는 기기에서는 androidx가 `ACTION_OPEN_DOCUMENT`로 대신한다(기기 확인 안 함). 바이트는 `ContentResolver.openInputStream`으로 IO에서 읽는다.
- 아래 바는 흐림 없이 `materialGlass` 단색(타임테이블 일정 고르기와 같음).
- 팝업은 `FCDialog`(State의 `alert`).

## 오늘 계획 고르기 (`plan/`, iOS `BearerPlanPickFeature` · `BearerPlanPickView`)

경로 `BearerRoute.PlanPick(festivalId)`. 팩 공연과 고른 계획은 Coordinator가 프로필 State(`sets` · `plan`)에서 ViewModel 인자로 넘긴다.

### 동작 규칙

1. 공연을 서울 시작 날짜로 묶고(날짜 안은 시작 순), 이미 고른 공연이 있는 첫 날로 연다(없으면 첫 날).
2. 공연을 누르면 고르고, 다시 누르면 빼며 한도 문구를 지운다. 30개를 이미 골랐으면 더 고르지 않고 "오늘 계획은 30개까지 고를 수 있어요.".
3. 없는 날짜 번호는 무시한다. 완료 → `Picked(팩 시작 순 + 팩에 없는 예전 ID 사전순)`. 뒤로(상단 · 시스템) → `CloseRequested`.

### 화면

- `FCTopBar(Back, "오늘 계획")`, 배경 `backgroundNormal`, 스크롤 패딩 `P16` · 간격 `P20`. "오늘 갈 무대를 골라 주세요" / "고른 무대는 깃발의 오늘 계획으로 보여요.\n깃발 프로필을 저장하면 반영돼요.".
- 날짜 `FCSegmented`(`M/d(E)` ko · 서울), "{n}개 선택", 카드(r16 · 1 테두리 · 구분선 1) 안 `FCSetSelectRow(artist, stage ?: "무대 미정", HH:mm, HH:mm)`. 공연이 없으면 "받아 둔 팩에 공연 시간이 아직 없어요.".
- 아래 `FCButton("{n}개 선택 완료", 전체 폭)`, 위 구분선 1 · 위 `P12` · 아래 `P8` · 좌우 `P16` · `backgroundElevated`.

### Android에서 다른 점

- 날짜 · 시각 문구는 feature 안 `BearerPlanFormat`(iOS `AppDateFormat.festivalDay` · `.time`)이다. feature는 `core`를 의존하지 않는다(`feature.md`).

## 엣지 케이스

- 사진 업로드 중에는 저장할 수 없고, 저장 중에는 새 사진을 받지 않는다. 업로드가 끝난 뒤 온 진행 · 실패는 버린다.
- 잠김 중 입력 · 확인은 무시된다. 잠김이 풀리기 전에 화면을 닫으면 카운트다운도 멈춘다.
- 서버가 남은 횟수를 주면 그 값을 쓰고, 연속 실패 수는 그래도 센다.

## 테스트

iOS `BearerClaimFeatureTests` 7건을 같은 이름으로 `BearerClaimViewModelTest`에 옮긴다: `filtersCode` · `claims` · `invalidCode` · `locked` · `bearerLimit` · `closeWhileSubmitting` · `back`.

iOS `BearerProfileFeatureTests` 13건을 같은 이름으로 `BearerProfileViewModelTest`에 옮긴다: `placeholderCleared` · `planOpensMySchedule` · `coBearerText` · `loads` · `bioCountsScalars` · `tags` · `save` · `savesEditedPlan` · `nameTaken` · `uploadsPhoto` · `cancelsUpload` · `photoTooSmall` · `invites`.

iOS `BearerPlanPickFeatureTests` 4건을 같은 이름으로 `BearerPlanPickViewModelTest`에 옮긴다: `groupsDaysAndOpensPlannedDay` · `togglesAndReturnsOrderedPlan` · `limitsToThirty` · `backCloses`. 같은 파일 `festivalDay`는 iOS `AppDateFormatTests`의 `festivalDay` 줄이다.
