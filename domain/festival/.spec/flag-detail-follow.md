---
spec: flag-detail-follow
modules: [domain/festival, service/festivalcatalog, service/api]
---

# 깃발 상세 · 팔로우 · 신고 · 대표 깃발 데이터 Spec

> 깃발 상세(익명 허용)를 읽고, 팔로우 · 언팔로우 · 신고 · 대표 깃발 지정을 서버에 보내 도메인 값과 오류로 바꾼다.

iOS 대응: `Projects/Domain/FestivalDomain/{Interface/Sources/{FlagDetail,Flag,Entity},Sources/{FlagDetail,Flag,Repository/FestivalFlagDetailRepositoryImpl,Model}}` · `Projects/Service/FestiCoreFestivalCatalog/Sources/NetworkFestivalFlagFollowService.swift` · `Projects/Service/APIEndpoint/Sources/Flag`.
설계: `.claude/docs/specs/2026-11-23-flag-tab-android-design.md`(서버 계약 · PR D1). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

화면은 `FestivalFlagDetailUseCase`(detail · report · setPinnedFlag)와 `FestivalFlagUseCase.setFollowing`만 본다. 구현은 Repository(`FestivalFlagDetailRepositoryImpl` · `FestivalFlagRepositoryImpl`) → 서비스(`FestivalFlagDetailService` · `FestivalFlagFollowService`) → `FlagApi`다. 이번 단계에서 화면은 아직 이 UseCase를 부르지 않고, `setPinnedFlag`는 라이브 액티비티 단계 전까지 화면에서 부르지 않는다(설계 결정 F4-1).

## 동작 규칙

1. 요청(모두 재시도 0, 저장하지 않음):
   - 상세 `GET api/v1/flags/{id}`. 로그인 세션이 있을 때만 그 세션 ID에 묶은 Bearer, 없으면 익명으로 보낸다.
   - 팔로우 `PUT flags/{id}/follow`(Bearer + `Idempotency-Key`), 언팔로우 `DELETE flags/{id}/follow`(Bearer, 키 없음), 신고 `POST flags/{id}/report {reason}`(Bearer), 대표 깃발 `PUT api/v1/me/pinned-flag {flagId}`(Bearer, 해제는 `{"flagId":null}`을 명시해 보낸다).
   - `Idempotency-Key`는 요청마다 `UUID.randomUUID().toString()`(재시도가 없으므로 재사용하지 않음).
2. 깃발 ID는 ASCII 영숫자 · `-` · `_` 1~64자. 아니면 요청하지 않는다. 오류는 iOS와 같게 상세 `NotFound`, 팔로우 `InvalidResponse`, 신고 · 대표 깃발 `InvalidRequest`.
3. 팔로우는 요청 전에 현재 세션을 확인하고, 없으면 보내지 않고 `LoginRequired`.
4. 익명 상세의 `isFollowing` · `isBearer` · `isPinned`는 null 그대로 둔다(거짓으로 바꾸지 않음).
5. 응답 검증: 상세 · 팔로우의 `flagId`가 요청과 다르거나, 신고 `reportId`가 비었거나, 대표 깃발 응답 `pinnedFlagId`가 요청과 다르면 `InvalidResponse`.
6. 오류 매핑: 401 · 세션 없음 → `LoginRequired`, 403 `TERMS_REQUIRED` → `TermsRequired`, 404 → `NotFound`(대표 깃발은 해당 케이스 없음), 409 `FOLLOW_LIMIT` → `FestivalFlagFollowError.LimitReached` · `ALREADY_REPORTED` → `FestivalFlagReportError.AlreadyReported` · `PIN_NOT_FOLLOWING` → `FestivalFlagPinError.NotFollowing`, 429와 그 밖 → `Unavailable`. 상세(`FestivalFlagDetailError`)는 `NotFound` · `InvalidResponse` · `Unavailable`만 있다. 취소는 그대로 전파한다.

## iOS와 다른 점

- 상세 · 신고 · 대표 깃발 요청을 Repository가 직접 보내지 않고 `service/festivalcatalog`의 `FestivalFlagDetailService`가 보낸다(domain 규칙: DTO는 sources 밖으로 내보내지 않음).
- iOS `FestivalFlagUseCase`의 `liveBoard`는 이미 `FestivalLiveBoardUseCase`에 있어 Android `FestivalFlagUseCase`는 `setFollowing`만 둔다.
- 깃발 ID 길이 상한 64자를 둔다(iOS는 문자만 검사). 신고 본문의 선택 `note`는 보내지 않는다(iOS 화면도 사유만 보냄).

## 테스트

`service/api` `FlagApiTest` · `service/festivalcatalog` `FestivalFlagFollowTest` · `domain/festival/sources` `FestivalFlagDetailRepositoryTest` · `FestivalFlagUseCaseTest`. 익명 상세 자료는 stage GET 원문(`service/api/src/test/resources/flag-detail-stage.json`), 인증 응답은 iOS DTO 형태 가짜 값.
