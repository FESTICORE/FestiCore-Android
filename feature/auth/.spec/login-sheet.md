---
spec: login-sheet
modules: [feature/auth, app]
---

# 로그인 시트 Spec

> 비로그인 사용자가 로그인이 필요한 동작을 눌렀을 때 메인 탭 위에 띄우는 문맥별 로그인 시트와, 시트를 닫거나 로그인했을 때의 전이.

iOS 대응: `Projects/Feature/Auth/Sources/Login/View/LoginModalView.swift`(화면 · 문맥 문구), `Login/Reducer/LoginFeature.swift`(`Context` · `closeTapped` · 실패 문구), `Projects/App/Sources/Navigation/Auth/AuthCoordinator.swift`(`loginRequested` 151-159, `dismissFlow` 183-204, `acceptLogin` 455-477), `MainTab/MainTabAuthInputs.swift`(탭 기본 문맥 48-58).
설계: `.claude/docs/specs/2026-11-23-flag-tab-android-design.md` "로그인 시트 (쟁점 F1)", PR 묶음 L1. 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

첫 실행 전체 화면 로그인(`LaunchLogin`)과 별개로, 메인 탭을 쓰는 중에 로그인이 필요하면 바텀시트(`FCSheet`, 높이 자동 · 손잡이)로 로그인을 받는다.
문맥(`LoginContext`)에 따라 아이콘과 제목 · 본문이 바뀐다. 로그인 버튼은 Google만 둔다(Apple 로그인은 옮기지 않는다).

## 동작 규칙

1. 문맥과 문구(iOS `LoginModalView.copy` 그대로):

   | 문맥                 | 제목                                   | 본문                                                                                                      |
   | -------------------- | -------------------------------------- | --------------------------------------------------------------------------------------------------------- |
   | `Profile`            | 계정 정보를 보려면 로그인이 필요해요   | 로그인 후 프로필을 확인할 수 있어요.                                                                      |
   | `Flag` · `Follow`    | 깃발을 팔로우 하려면 로그인이 필요해요 | 내 취향의 깃발을 팔로우하고,\n함께 페스티벌을 즐길 사람들과 크루를 만들어봐요.                            |
   | `Crew`               | 크루와 함께 보려면 로그인이 필요해요   | 크루원끼리 지금 어느 존에 있는지 서로 볼 수 있어요.\n공연과 타임테이블은 로그인 없이 그대로 쓸 수 있어요. |
   | `CrewJoin(crewName)` | 크루에 들어가려면 로그인이 필요해요    | 로그인하면 바로 {crewName ?: "크루"}에 들어가요.\n크루원끼리만 서로의 존과 상태를 볼 수 있어요.           |
   | `Bookmark`           | 축제를 저장하려면 로그인이 필요해요    | 로그인 후 선택한 축제를 저장할 수 있어요.                                                                 |
   | `Report`             | 깃발을 신고하려면 로그인이 필요해요    | 로그인 후 문제가 있는 깃발을 신고할 수 있어요.                                                            |

2. 아이콘: 지름 56 `staticLime` 원 안에 24 아이콘(`staticInk`). `Crew`면 사람 아이콘(`ic_users_24`), 그 외는 깃발.
3. 탭 기본 문맥: 요청에 문맥이 없으면 고른 탭으로 정한다. 마이 = `Profile`, 깃발 = `Flag`, 라이브 · 타임테이블 = `Bookmark`.
4. 시트는 Home이고 덮개(첫 진입 로그인 · 가입)가 없을 때만 연다. 이미 떠 있으면 문맥만 바꾼다(iOS `loginRequested`가 `presentation = .login`을 다시 넣는다).
5. "나중에 할게요" · 딤 탭 · 아래로 끌기 · 뒤로: 시트를 닫는다(iOS `closeRequested` → `dismissFlow`). 세션은 건드리지 않고 프로필 재확인도 하지 않는다.
6. 로그인 중에는 버튼 · "나중에 할게요"가 동작하지 않고 시트도 닫히지 않는다(Android는 시트를 닫으면 로그인 흐름이 취소되므로 닫기를 막는다).
7. 로그인 → 계정 확인은 첫 진입 로그인과 같은 흐름(`LaunchLoginViewModel`)을 쓴다.
   - 조건 충족(또는 계정 확인이 오프라인 · 서버 오류): 그 세션이 현재 세션이면 현재 사용자로 두고 시트를 닫는다.
   - 신규 · 조건 미달 계정: 시트를 닫고 가입 정보 덮개(`AppCoordinatorState.signUp`, 재확인 가입과 같은 덮개)를 띄운다. 가입 화면 뒤로는 세션을 지우지 않고 덮개를 닫는다(`signup.md` 규칙 2 Recheck).
   - 계정 확인 401: 다른 세션의 결과가 아니면 현재 사용자를 비우고 시트를 닫는다. 첫 진입 로그인 덮개는 띄우지 않는다(iOS 시트 흐름은 `isLaunchFlow = false`).
   - 실패: 시트를 유지하고 문구 "로그인에 실패했습니다. 다시 시도해 주세요."(저장 실패는 "로그인 정보를 저장하지 못했어요. 다시 시도해 주세요."). 사용자 취소 · 중복 요청은 문구 없음.
8. 시트를 열 때마다 새 로그인 상태로 시작한다(이전 시트의 오류 문구를 남기지 않는다).

## 엣지 케이스

- 시트가 떠 있는 동안 세션 만료 이벤트: 시트는 그대로 둔다(로그인을 받으려고 떠 있다).
- 시트가 떠 있는 동안 딥링크: 시트는 덮개가 아니므로 딥링크 처리 조건(`canOpenDeeplink`)에 영향을 주지 않는다.
- 로그인 뒤 원래 동작 다시 하기: iOS는 축제 저장(`bookmark`)만 다시 한다. Android에는 축제 저장이 아직 없어 다시 하는 동작이 없다. 깃발 · 크루는 세션 변경으로 화면만 갱신된다.

## 제약 / 가정

- `LoginContext`는 `feature/auth/interface`에 둔다. 다른 기능의 로그인 요청(깃발 `FlagLoginContext` · 라이브 보드 `Login*` 경계)을 이 문맥으로 바꾸는 일은 app이 한다.
- 시트 표시 상태는 app `AppCoordinator`가 갖는다(가입 덮개 · 현재 사용자를 같은 자리에서 다루기 때문). iOS는 `MainTabCoordinator.authFlow`가 갖는다.
- 시트가 떠 있는 동안 탭 바를 숨긴다(iOS `MTC:93`). 라이브 보드 · 크루 · 깃발 탭의 로그인 요청은 메인 탭이 `LoginContext`로 바꿔 시트를 연다(문맥이 없으면 탭 기본).

## 작업 모듈

| 모듈                     | 계층    | 변경 내용                                                                                             |
| ------------------------ | ------- | ----------------------------------------------------------------------------------------------------- |
| `feature/auth/interface` | feature | `LoginContext`                                                                                        |
| `feature/auth/sources`   | feature | `LoginSheet`(시트 화면 · 문맥 문구), `LaunchLoginViewModel` 재사용                                    |
| `app`                    | app     | `AppCoordinator` 시트 상태 · 결과 처리, `MainTabCoordinatorState` 탭 기본 문맥, `AppFlow` 시트 그리기 |
