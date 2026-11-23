---
spec: crew
modules: [domain/festival, service/festivalcatalog, service/api]
---

# 크루 데이터 Spec

> 크루 생성 · 상세 · 이름 · 해산 · 상태 · 초대 · 이전 · 내보내기/나가기 · 참여 · 모여 요청을 보내기 전에 입력을 검증하고, 응답과 오류를 도메인 값(`FestivalCrew` · `FestivalCrewInvite` · `FestivalCrewError`)으로 바꾼다.

iOS 대응: `Projects/Service/APIEndpoint/Sources/Crew/{CrewRequest,CrewGatherRequest}.swift` · `Projects/Domain/FestivalDomain/Sources/{Repository/FestivalCrewRepositoryImpl,Crew/FestivalCrewUseCaseImpl}.swift` · `Interface/Sources/Entity/FestivalCrew{,Invite,Gather}.swift`.
설계: `.claude/docs/specs/2026-11-23-flag-tab-android-design.md`(서버 계약 · PR D2). 화면 규칙(크루 메인 · 만들기 · 모여 · 참여)은 C1의 `feature/festival/.spec/crew.md`에서 다룬다. 아래는 데이터 층에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

화면은 `FestivalCrewUseCase`만 본다. 구현은 `FestivalCrewRepositoryImpl` → `FestivalCrewService`(세션 확인 · `clientAt`) → `CrewApi`다. 이번 단계에서 화면은 아직 이 UseCase를 부르지 않는다.

## 동작 규칙

1. 요청(모두 재시도 0, `api/v1` 아래). 초대 미리보기만 인증 없음, 나머지는 현재 세션 ID에 묶은 Bearer이고 세션이 없으면 보내지 않고 `Unauthenticated`.
   - `POST crews {festivalId, name}`(nickname은 보내지 않음) · `GET` · `PATCH {name}` · `DELETE crews/{id}`
   - `POST crews/{id}/status {zoneId?, state, clientAt}` + `Idempotency-Key`(요청마다 새 UUID). `clientAt`은 UTC 밀리초 ISO(`yyyy-MM-ddTHH:mm:ss.SSSZ`). `zoneId`가 없거나 상태가 `LEAVING`이면 존을 보내지 않는다. 다른 요청에는 키가 없다.
   - `POST crews/{id}/invite/rotate` · `POST crews/{id}/transfer {toUserId}` · `DELETE crews/{id}/members/{userId}`(내보내기 · 나가기. 나가기는 현재 사용자 ID)
   - `GET crews/invite/{code}`(인증 없음) · `POST crews/join {inviteCode}`
   - `POST crews/{id}/gather {zoneId}` · `POST crews/{id}/gather/{gatherId}/response {response}`
2. 입력 검증(틀리면 요청하지 않음):
   - 크루 이름: 앞뒤 공백을 뺀 뒤 1~12자(`FestivalCrew.MAX_NAME_LENGTH`). 정원은 `FestivalCrew.MAX_MEMBERS = 12`.
   - 크루 · 사용자 · 모여 · 존 ID: ASCII 영숫자 · `-` · `_` 1~64자. 축제 ID는 trim 후 1~32자.
   - 초대 코드: 앞뒤 공백을 빼고 대문자로 바꾼 뒤 6자, 문자 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`만(`0 O 1 I` 제외). 틀리면 `NotFound`(iOS와 같음).
   - 그 밖의 틀린 입력은 `InvalidInput`.
3. 응답 검증: 요청과 다른 크루 ID · 이름 · 크루장 · 존 · 상태, 다른 크루의 `live`, 해석할 수 없는 날짜는 `InvalidResponse`.
4. 공통 오류 매핑(iOS `FestivalCrewRepositoryImpl`): 400 → `InvalidInput`, 401 · 세션 없음 → `Unauthenticated`, 403 `TERMS_REQUIRED` → `TermsRequired`(다른 403 → `Forbidden`), 404 → `NotFound`, 409 `CREW_FULL` → `Full`(다른 409 → `AlreadyInCrew`), 410 → `FestivalEnded`, 429 → `Cooldown`, `code != SUCCESS` · 해석 실패 → `InvalidResponse`, 그 밖 → `Unavailable`. 취소는 그대로 전파한다.
5. 요청별 예외: 생성은 403 `TERMS_REQUIRED` · 409 `CREW_ALREADY_IN`만 고르고 나머지 403 · 409는 `Unavailable`, 모여는 400 · 401 · 403 · 410 · 429만 공통 매핑, 모여 응답 409 → `GatherExpired`, 나가기 409(`OWNER_CANNOT_LEAVE`) → `Forbidden`.

## iOS와 다른 점

- iOS가 원본 오류를 그대로 던지는 경우(매핑에 없는 상태 · 전송 실패)는 `Unavailable`로 바꾼다(깃발 데이터와 같은 방식).
- 크루 생성 함수에 `nickname` 인자를 두지 않는다(iOS도 보내지 않음).
- 나가기의 현재 사용자 ID는 `domain/auth`의 `CurrentUserProvider`에서 읽는다(`domain/festival/sources` → `domain/auth/interface`).
- 초대 48시간 · 모여 10분은 iOS 도메인에 상수가 없어(문구 · 주석에만 있음) 도메인 상수를 두지 않는다. 화면 단계(C1)에서 iOS 문구대로 다룬다.

## 테스트

`service/api` `CrewApiTest` · `domain/festival/sources` `FestivalCrewTest` · `FestivalCrewRepositoryTest`. 초대 미리보기 404 자료는 stage GET 원문(`service/api/src/test/resources/crew-invite-not-found-stage.json`), 인증 응답은 iOS DTO 형태 가짜 값.
