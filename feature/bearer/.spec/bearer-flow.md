---
spec: bearer-flow
modules: [feature/bearer, feature/featureassembly]
---

# 기수 화면 흐름 Spec

> 기수가 코드 인증 · 깃발 등록 · 프로필 · 오늘 계획 · 기수 보드 화면을 거쳐 깃발을 맡고 상태를 바꾼다. 이 문서는 화면이 들어올 때마다(BS1~BC2) 갱신한다.

iOS 대응: `Projects/Feature/Bearer/**`(`Interface/Sources/Bearer*Delegate.swift` · `Sources/{Claim,Register,Profile,Plan,Board}`).
설계: `.claude/docs/specs/2026-11-24-bearer-android-design.md`(화면 2 기수 인증 · 화면 3 깃발 프로필 · 화면 4 오늘 계획 고르기 · 화면 5 깃발 등록 · 화면 6 기수 여부 · 화면 전환 · iOS → Android 적응 · PR BS1 · BS2 · BS3).

## 개요

| 화면 | 패키지 | 상태 |
| --- | --- | --- |
| 기수 인증(S13 F~I) | `claim/` | BS1 |
| 깃발 프로필(S10 A~E) · 오늘 계획 고르기 | `profile/` · `plan/` | BS2 |
| 깃발 등록(S14 D · E) · 기수 여부(S14 C) | `register/` | BS3 |
| 보드 | `board/` | 아직(BS4) |

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

## 깃발 등록 (`register/`, iOS `BearerRegisterFeature` · `BearerRegisterView`)

경로 `BearerRoute.Register(preferredFestivalId)`. ViewModel은 `BearerUseCase` · `FestivalCatalogUseCase` · `FestivalReceivedPackUseCase`(`domain/festival/interface`)를 받는다.

### 동작 규칙

1. 화면이 보이면(이미 불러왔으면 건너뜀) 받은 팩(실패면 빈 목록) → 서버 축제 목록(실패면 기기 저장 목록 + 오프라인)을 읽는다. 받은 팩을 먼저, 받지 않은 축제는 마지막 날(없으면 첫날)이 오늘(서울) 이후인 것만 첫날 순. 둘 다 비면 실패(오프라인 여부), "다시 시도"는 다시 읽는다. 앞으로 열리는 축제는 3개만 보이고 "다른 페스티벌 찾기"로 모두 보인다.
2. 날짜 문구: `11/20-22`, 달이 다르면 `11/30-12/1`, 하루 · 마지막 날 없음은 `11/20`. 줄 문구는 "{이름} · {날짜}"(날짜 없으면 이름).
3. 처음 고르기: 이미 고른 축제가 목록에 있으면 유지, 아니면 라이브 탭에서 연 축제 → 첫 받은 팩. 고르면 존 · 주로 있는 존을 비우고, 받은 팩이면 그 팩 존을 읽는다(아니면 읽던 것을 멈춤). 존을 읽으면 목록에 없는 주로 있는 존을 지운다. 저장 중에는 축제를 바꾸지 못한다.
4. 이름 2~20 · 소개 60은 코드포인트로 센다. 태그 0~3(0개면 "초보 환영"으로 보냄), 존은 다시 누르면 해제. 이름을 바꾸면 이름 · 저장 오류를 지운다.
5. 저장(`canSave`: 고른 축제 · 이름 길이 · 소개 길이 · 태그 수 · 이름 오류 없음 · 저장 중 아님). 마지막으로 보낸 요청과 축제 · 내용이 같으면 그 요청(같은 멱등키)을 다시 보낸다. 실패: `NameTaken` → 이름 칸 오류 + 팝업, `BearerLimit` → 팝업, `Offline` → 저장 문구(요청을 남김), 그 외 → 저장 문구. 오프라인 외에는 요청을 지운다.
6. 성공: 실제로 보낸 요청의 축제로 저장 완료를 만들고 `Registered(flagId, festivalId)`, 고른 사진이 있으면 그때 올린다(실패면 완료 화면 안내).
7. 완료 버튼: 팩 있음 "완료" → `Finished` · "공동 기수 초대하기" → 초대 팝업, 팩 없음 "{이름} 팩 받기" → `PackRequested` · "나중에 받을게요" → `Finished`.
8. "공동 기수 초대 코드가 있어요"(축제를 골랐을 때) → `ClaimRequested(festivalId)`. 뒤로(상단 · 시스템) → `BackRequested`, "나중에 할게요" → `CloseRequested`. 둘 다 등록 요청을 취소한다.

### 문구

| 상황 | 문구 |
| --- | --- |
| 머리글 | "내 깃발을 만들어요" / "팔로워에게 보일 깃발 프로필이에요.\n지금 다 채우지 않아도 언제든 고칠 수 있어요." |
| 페스티벌 | "어느 페스티벌의 깃발인가요?" · 실패 "인터넷에 연결되지 않아 페스티벌을 불러오지 못했어요." / "깃발을 만들 수 있는 페스티벌을 불러오지 못했어요." + "다시 시도" · "내가 받은 팩" · "다른 페스티벌" · 안내 "로그인 전에 받은 팩·핀한 페스티벌이 먼저 보여요. 깃발은 페스티벌마다 따로 만들어요." / "앞으로 열리는 페스티벌이에요. 깃발은 페스티벌마다 따로 만들어요." |
| 설정 | "팔로워에게 오늘 계획 보여주기" / "오늘 계획과 공동 기수 초대는 깃발을 만든 뒤 깃발 프로필에서 고를 수 있어요." |
| 버튼 | "만드는 중이에요" / "등록하고 기수 시작하기" · "나중에 할게요 · 마이에서 언제든 만들 수 있어요" · "공동 기수 초대 코드가 있어요" |
| 저장 완료 | "깃발이 준비됐어요" / 팩 있음 "공연 당일 기수 보드에서 깃발을 올리면\n팔로워에게 알림이 가요." · 팩 없음 "현장에서 기수 보드를 쓰려면 {이름} 팩이 필요해요. 지금 받아 두면 통신이 끊겨도 쓸 수 있어요." · 사진 실패 "사진을 올리지 못했어요. 깃발 프로필에서 다시 골라 주세요." |
| 팝업 | 이름 중복 "이미 있는 깃발 이름이에요" / "이 페스티벌에서 하나뿐인 이름이어야 해요. 다른 이름을 지어 주세요." · 한도 "이미 이 페스티벌의 기수예요" / "{축제 이름 또는 '이 페스티벌'}에는 깃발을 하나만 만들 수 있어요. 마이의 내 깃발에서 확인해 주세요." · 초대 · 초대 실패는 프로필과 같다(확인 · 닫기) |

이름 · 소개 · 태그 · 존 문구와 저장 · 초대 실패 문구는 깃발 프로필과 같다.

### 화면

- 배경 `backgroundNormal`, `FCTopBar(Back, "깃발 프로필")`, 섹션 간격 28 · 그룹 간격 12, 패딩 `P16`, 아래 바는 프로필과 같다(`FCPrimaryButton` + `FCButton(Ghost, Medium)`).
- 순서: 머리글 · 페스티벌(불러오는 중 스켈레톤 높이 44 × 3, 줄은 `BearerZoneButton`, 더 보기 `FCButton(Ghost)` 가운데) · `FCPhotoPicker`(고른 사진 또는 빈 칸) · 이름 · 소개 · 태그 · 존(팩에 존이 없으면 숨김) · 설정 카드(r16 · 1 테두리, `FCListRow` 토글 1줄) · `FCButton(Ghost, Medium)` 가운데 · 저장 오류.
- 저장 완료는 프로필 완료와 같은 배치(`FCDoneHero` · 미리보기 `FCFlagCard`, 썸네일은 고른 사진 또는 `FCThumbnail`).

### Android에서 다른 점

- '오늘'은 주입한 `Clock`(기본 서울)의 날짜다. iOS는 기기 달력(`\.calendar`)을 쓴다.
- 사진은 프로필과 같은 Photo Picker. 바이트를 읽지 못하면 iOS처럼 아무것도 하지 않는다.

## 기수 여부 (`register/`, iOS `BearerRoleChoiceFeature` · `BearerRoleChoiceView`)

경로 `BearerRoute.RoleChoice`. 가입 직후에만 연다(쟁점 B1, BC2).

- 고르기 전 "다음"은 아무것도 하지 않는다. 고르면 "다음"으로 `AudienceChosen` · `BearerChosen`. 뒤로(상단 · 시스템) → `CloseRequested`.
- `FCTopBar(Back)`, 패딩 `P16` · 간격 `M24`, "페스티벌에서\n깃발을 드시나요?"(`baseTitle2Bold`) / "기수는 깃발을 들고 사람들을 이끄는 사람이에요. 나중에 마이에서도 바꿀 수 있어요."(`baseFootnoteRegular` · `labelNeutral`), 둘 다 줄 상자 없음.
- 카드(간격 `P12`): 아이콘 20 in 44 원(`backgroundAlternative`) · 제목 `baseCalloutBold` · 설명 `baseFootnoteRegular`(간격 `P4`) · `FCCheckCircle`, 패딩 `P16`, r16, 테두리 고름 `primaryNormal` 2 / `lineNormal` 1. "관객이에요" / "깃발을 팔로우하고 크루와 함께 놀아요"(`ic_users_24`), "기수예요" / "내 깃발 프로필을 만들고 팔로워를 모아요"(`ic_flag_20`).
- 아래 `FCPrimaryButton("다음", 고름)`(좌우 `P16` · 위아래 `P12`, 배경 `backgroundNormal`).

## 엣지 케이스

- 사진 업로드 중에는 저장할 수 없고, 저장 중에는 새 사진을 받지 않는다. 업로드가 끝난 뒤 온 진행 · 실패는 버린다.
- 잠김 중 입력 · 확인은 무시된다. 잠김이 풀리기 전에 화면을 닫으면 카운트다운도 멈춘다.
- 서버가 남은 횟수를 주면 그 값을 쓰고, 연속 실패 수는 그래도 센다.

## 테스트

iOS `BearerClaimFeatureTests` 7건을 같은 이름으로 `BearerClaimViewModelTest`에 옮긴다: `filtersCode` · `claims` · `invalidCode` · `locked` · `bearerLimit` · `closeWhileSubmitting` · `back`.

iOS `BearerProfileFeatureTests` 13건을 같은 이름으로 `BearerProfileViewModelTest`에 옮긴다: `placeholderCleared` · `planOpensMySchedule` · `coBearerText` · `loads` · `bioCountsScalars` · `tags` · `save` · `savesEditedPlan` · `nameTaken` · `uploadsPhoto` · `cancelsUpload` · `photoTooSmall` · `invites`.

iOS `BearerPlanPickFeatureTests` 4건을 같은 이름으로 `BearerPlanPickViewModelTest`에 옮긴다: `groupsDaysAndOpensPlannedDay` · `togglesAndReturnsOrderedPlan` · `limitsToThirty` · `backCloses`. 같은 파일 `festivalDay`는 iOS `AppDateFormatTests`의 `festivalDay` 줄이다.

iOS `BearerRegisterFeatureTests` 10건을 같은 이름으로 `BearerRegisterViewModelTest`에 옮긴다: `festivalOptions` · `loadsUpcomingAndSelectsPreferred` · `receivedPackLoadsZones` · `emptyFestivalsFail` · `registerSucceeds` · `offlineRetryReusesKey` · `conflictsShowAlerts` · `validation` · `roleChoice` · `savingKeepsRequestedFestival`.
