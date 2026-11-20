---
paths:
  - "service/**"
  - "core/**"
  - "build-logic/**"
---

# service · core 경로 규칙

상세 문서: `.claude/docs/architecture.md`, `.claude/docs/gradle.md`, `.claude/docs/dependency-injection.md`

## Hard

- Keystore · SharedPreferences · DataStore는 `core/datastore`, Room은 `core/database`에서만 쓴다. 다른 모듈은 주입받아 쓴다(verify `boundaries`가 검사).
- 토큰 · 인가 코드 · 개인정보는 로그 · Sentry · Mixpanel로 보내지 않는다. Sentry · Mixpanel 호출은 `service/analytics`에만 둔다.
- 모듈 공통 Gradle 설정은 `build-logic/`의 convention plugin에만, 버전은 `gradle/libs.versions.toml`에만 쓴다.
- Koin 정의는 그 클래스가 있는 모듈의 `di/`에 둔다.

## Soft (기존 코드가 다르면 기존 코드를 따른다)

- API 경로 · 인증 정책은 iOS `Service/API` · `Service/APIEndpoint`와 같은 이름과 묶음으로 둔다.
