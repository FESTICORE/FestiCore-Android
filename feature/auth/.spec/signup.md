---
spec: signup
modules: [feature/auth, app]
---

# 회원가입(가입 정보) Spec

> 가입 조건을 채우지 못한 계정에 닉네임 · 필수 약관 3종을 받는 화면과, 그 화면을 닫거나 세션을 잃었을 때의 전이.

iOS 대응: `Projects/Feature/Auth/Sources/SignUp/`(`SignUpFeature` · `SignUpView`), `Projects/App/Sources/Navigation/Auth/AuthCoordinator.swift`(`dismissFlow` 184-204, 가입 제출 487-520), `Session/SessionCoordinator.swift`(`foregrounded` 122-128, `handleSessionExpired` 196-223, `handleProfileLoaded` 304-351, `handleProfileUpdated` 380-445).
전이 근거와 시나리오 번호는 `.claude/docs/specs/2026-11-21-launch-flow-parity-audit.md` "덮개 결과와 재확인 전이"를 따른다. 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

가입 화면은 두 경로로 뜬다.

- 첫 진입 흐름(Launch): launchLogin 덮개 안에서 로그인 직후 계정 확인이 조건 미달일 때. auth 스택이 LaunchLogin을 SignUp으로 바꾼다.
- 재확인(Recheck): 로그인 상태로 Home에 있을 때 프로필 재확인이 조건 미달이면 Home 위 가입 덮개(`AppCoordinatorState.signUp`).

## 동작 규칙

1. 진입 문구: 예전 약관에 동의한 기존 회원(`termsAcceptedAt != null`)이면 처음부터 "변경된 약관을 확인하고 다시 동의해 주세요."를 보인다(`AuthRoute.SignUp.isReconsent`). 신규 회원은 문구 없음.
2. 뒤로(시스템 뒤로 포함): 세션을 지우지 않는다.
   - Launch: auth 스택에서 SignUp을 LaunchLogin으로 바꾼다. 상위에 결과를 올리지 않는다.
   - Recheck: `LaunchLoginResult.SignUpCancelled`로 덮개를 닫는다. 재확인은 하지 않고 다음 포그라운드에 한 번 한다.
3. 건너뛰기: Launch 덮개를 먼저 닫고(`launchLogin = false`) 그다음 프로필을 한 번 재확인한다. 세션이 남아 있고 조건 미달이면 Recheck 가입 덮개를 띄운다. 완료 · 가입 취소 · 401에서는 재확인하지 않는다.
4. 재확인은 launchLogin 로그인 화면이 보이는 동안 실행하지 않는다. 덮개 흐름이 맨 위 화면을 상위에 알리고(`onAuthCoverTopChanged`), 상위는 그것으로 가입 화면이 보이는지 판단한다.
5. 포그라운드 때 가입 화면이 보이면(Recheck 덮개 또는 Launch 덮개 안의 가입 화면) 재확인한다. 충족이면 덮개를 닫고, 미달이면 덮개 · 화면을 새로 만들지 않아 입력 초안이 남는다.
6. 세션 만료 이벤트: Launch 덮개 안에서 그 세션의 가입 화면이 보이면 덮개 세대를 올려 LaunchLogin부터 새로 만든다. 로그인 화면이 보이면(다른 세션의 로그인 진행 중일 수 있음) 그대로 둔다. Recheck 덮개면 닫아 비로그인 Home. 덮개가 없으면 launchLogin을 켜지 않는다.
7. 가입 제출 401(만료 이벤트 없음): Launch는 LaunchLogin으로, Recheck는 덮개를 닫는다.
8. 스크롤을 사용자가 끌기 시작하면 키보드를 숨긴다. 흐름 문구(오류 · 재동의)는 줄 박스 없는 글꼴(`textStyleWithoutLineBox`)을 쓴다.

## 엣지 케이스

- **가입하지 않은 토큰이 기기에 남는다.** 가입 화면에서 뒤로 나와도 로그인 세션(access · refresh 토큰)을 지우지 않는다(iOS와 같음, 점검 문서 결정 2). 사용자가 건너뛰기로 메인 탭에 가면 가입을 마치지 않은 계정의 토큰이 기기 저장소(`core/datastore`)에 남고, 재확인 · 포그라운드마다 가입 덮개가 다시 뜬다. 토큰은 세션 만료(갱신 401) · 재확인 401 · 다음 로그인 교체 때만 지워진다. 앱 안 로그아웃이 생기면 그 경로에서도 지운다.
- 포그라운드 재확인이 미달이어도 iOS는 서버 닉네임 · 약관 버전을 초안에 다시 적용하고 문구를 다시 정한다. Android는 열린 가입 화면을 그대로 둔다(아래 미해결 질문).
- 가입 중 다른 세션이 만료되면 진행 중인 화면을 바꾸지 않는다.

## 제약 / 가정

- 계정에 묶인 상태는 아직 세션 저장소 하나뿐이다. `CurrentUserProvider` 등 계정 상태가 생기면 401 경로에서 함께 비운다.
- 약관 상세는 외부 브라우저로 연다. 앱 안 화면은 P4b(`feature/web`).

## 작업 모듈

| 모듈                   | 계층      | 변경 내용                                                                                             |
| ---------------------- | --------- | ----------------------------------------------------------------------------------------------------- |
| feature/auth/interface | interface | `AuthRoute.SignUp.isReconsent`, `SignUpCancelled` 설명                                                |
| feature/auth/sources   | sources   | 뒤로 시 세션 유지, `AuthCoordinator.onSignUpClosed`, 맨 위 화면 알림, 재동의 문구, 키보드 숨김, C1    |
| app                    | app       | 건너뛰기 뒤 재확인, 포그라운드 가입 덮개 재확인, 만료 · 재확인 401 전이(결정 7), 첫 진입 가입 중 만료 |

## 영향 범위

| 모듈         | 계층    | 영향 이유                                           |
| ------------ | ------- | --------------------------------------------------- |
| service/auth | service | `SessionExpiry`(세대 · 세션 ID)를 app이 그대로 구독 |

## 미해결 질문

- 포그라운드 재확인 미달 때 열린 가입 화면에 새 약관 버전 · 문구를 반영할지(iOS `handleProfileLoaded`는 반영한다).

## 변경 이력

- P4 가입 iOS 대조: 재동의 문구 · 뒤로 세션 유지 · 건너뛰기 뒤 재확인 · 포그라운드 재확인 · 만료 전이 · 키보드 · C1
