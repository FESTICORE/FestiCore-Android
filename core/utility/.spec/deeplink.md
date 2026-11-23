---
spec: deeplink
modules: [core/utility, core/storage, app, feature/festival]
---

# 딥링크 Spec

> `festicore://` URL · 푸시 data를 화면 경로로 바꾸고, 홈이 준비될 때까지 대기했다가 메인 탭에서 연다.

iOS 대응: `Projects/Core/FestiCoreUtility/Sources/Deeplink/FestiCoreDeeplink.swift`, `Projects/App/Sources/Deeplink/{PendingDeeplinkStore,AppDeeplinkBridge}.swift`, `Projects/App/Sources/Navigation/Main/AppCoordinator.swift`(`deeplinkReceived`), `Projects/App/Sources/Navigation/MainTab/MainTabCoordinator.swift`(`handleDeeplink`).
설계: `.claude/docs/specs/2026-11-22-push-device-android-design.md` 6절 · PR P0a · 쟁점 P7 · P8.

## 동작 규칙

1. 경로(`FestiCoreDeeplink`): `FestivalList`(`festicore://festival`) · `Festival(id)`(`festival/{id}`) · `Set(festivalId, setId)`(`festival/{id}/set/{setId}`) · `Flag(id)`(`flag/{id}`) · `CrewInvite(code)`(`crew/{code}`).
2. URL 해석: 스킴 `festicore`(대소문자 무관), 사용자 · 비밀번호 · 포트 · query · fragment가 있으면 거절. host는 소문자로 맞춰 자원으로 쓴다. 경로 조각은 퍼센트 디코딩 후 ASCII 영숫자 · `-` · `_`만 받는다(빈 조각 · `%2F` 거절).
3. 크루 초대 코드는 I · O · 0 · 1을 뺀 영숫자 6자리, 대문자로 맞춘다.
4. 푸시 data(`Map<String, String>`): `deeplink` URL → `route`(`/festivals/{id}...` → 축제, `/festivals` → 목록, `/flags/{id}` → 깃발) → `flagId` → `festivalId` 순으로 처음 맞는 것.
5. 대기(`PendingDeeplinkStore`, DataStore `festicore_deeplink`, 키 `FestiCore.pendingDeeplink`): `encoded` URL 문자열 하나만 둔다. 새 값은 덮어쓰고, 꺼내기는 한 `edit` 안에서 읽고 지운다.
6. 브리지(`AppDeeplinkBridge`, app): `MainActivity.onCreate`(저장 상태 복원이 아닐 때) · `onNewIntent`의 `ACTION_VIEW` data를 해석해 받은 순서대로 대기에 넣고 신호를 보낸다. 딥링크가 아닌 URL은 버린다.
7. 라우팅(`AppCoordinator`): 신호를 받거나 홈에 들어오거나 덮개(첫 진입 로그인 · 가입)가 닫히면, 루트가 `[Home]`이고 덮개가 없을 때만 대기 값을 꺼내 `OpenDeeplink`로 보낸다. 조건이 안 맞으면 대기 값을 남긴다.
8. 탭 분기(`MainTabCoordinator.open`): `FestivalList` · `Festival`은 라이브 탭을 고르고 라이브 흐름에 넘긴다. `FestivalCoordinator`는 목록이면 `[List]`, 축제면 `[List, LiveBoard(id)]`로 바꾼다(같은 보드가 이미 열려 있으면 대기 축제로 넘겨 보드가 바꾼다).
9. `Set`은 파싱 · 대기까지만 한다. 라우팅은 C1(타임테이블 탭 연결)에서 붙인다. `Flag` · `CrewInvite`는 해당 기능이 없어 꺼낸 뒤 디버그 로그만 남긴다.

## iOS와 다른 점

- 신호는 `NotificationCenter` 대신 `SharedFlow`, URL 진입은 `onOpenURL` 대신 인텐트 필터 + `singleTop` + `onNewIntent`.
- 딥링크가 아닌 URL을 OAuth로 넘기는 분기가 없다(Google 로그인은 Credential Manager).
- 축제 열기에서 저장된 팩 확인 · 캐시 오류 안내(iOS `openWidgetFestival` 세부)는 아직 없다(P0b).
- 로그에는 딥링크 종류만 남긴다(ID · 초대 코드는 남기지 않는다).

## 테스트

- `core/utility` `FestiCoreDeeplinkTest`(iOS `FestiCoreDeeplinkTests` 6건), `core/storage` `PendingDeeplinkStoreTest`, `app` `AppCoordinatorTest`(홈 전 · 덮개 중 대기 → 열기), `MainTabCoordinatorTest`, `feature/festival` `FestivalCoordinatorTest`(딥링크 열기).
