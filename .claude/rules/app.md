---
paths:
  - "app/**"
---

# app 경로 규칙

상세 문서: `.claude/docs/architecture.md`, `.claude/docs/navigation.md`, `.claude/docs/dependency-injection.md`

## Hard

- app은 Application · MainActivity · 루트 Coordinator(스플래시 → 온보딩 → 로그인 → 메인 탭 전환) · Koin 모듈 목록 합치기만 둔다. 패키지는 iOS `App/Sources`처럼 `application/` · `deeplink/` · `navigation/`, 목록 합치기는 `di/`.
- core · service · feature Koin 목록은 `core/coreassembly` · `service/serviceassembly` · `feature/featureassembly`에 두고, app은 그 목록과 domain 목록 · `appModule`만 합친다.
- app에 `single { }` · `factory { }` 정의를 직접 쓰지 않는다. 정의는 그 클래스가 있는 모듈의 `di/`에 둔다.
- 화면 구현을 app에 두지 않는다. 화면은 `feature/{기능}/sources`에 둔다.
- 환경 값(API 주소 · 키)은 `stage` · `prod` flavor 설정에서만 읽는다. 비밀 값은 `local.properties` · CI 비밀로 넘기고 추적 파일에 넣지 않는다.

## Soft (기존 코드가 다르면 기존 코드를 따른다)

- 루트 흐름 이름은 iOS `AppCoordinator`와 맞춘다.
