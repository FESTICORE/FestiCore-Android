---
paths:
  - "service/**"
  - "core/**"
  - "build-logic/**"
---

# service · core 경로 규칙

상세 문서: `.claude/docs/architecture.md`, `.claude/docs/gradle.md`, `.claude/docs/dependency-injection.md`

## Hard

- Keystore · SharedPreferences · DataStore · Room은 `core/storage/sources`(iOS `FestiCoreStorage/Sources`)에서만 쓴다. 다른 모듈은 주입받아 쓴다(verify `boundaries`가 검사).
- 토큰 · 인가 코드 · 개인정보는 로그 · Mixpanel로 보내지 않는다. Firebase Analytics · Mixpanel 호출은 `service/analytics/sources`에만 둔다(이벤트 속성은 `service/analytics/.spec/analytics.md` 허용 목록). Sentry SDK 호출도 `service/analytics/sources`에서만 하며 개인정보 자동수집은 끈다.
- 모듈 공통 Gradle 설정은 `build-logic/`의 convention plugin에만, 버전은 `gradle/libs.versions.toml`에만 쓴다.
- Koin 정의는 그 클래스가 있는 모듈의 `di/`에 둔다.
- 위치 API(`android.location` · `androidx.core.location`)는 `core/location/sources`에서만 쓴다. 위치 좌표는 로그 · 분석 이벤트에 넣지 않는다(verify `boundaries`가 검사).
- `service/{pack,device,auth,analytics}` · `core/{network,storage,location}`는 `interface`(계약 · 공개 타입) · `sources`(구현 · `di/`)로 나뉜다. 다른 모듈은 `:interface`만 main으로 의존하고, `:sources`는 `*assembly` · `app`, 그리고 `service/auth/sources` · `service/pack/sources`(→ `:core:network:sources`)만 의존한다. 테스트는 `testImplementation(...:sources)` 허용(verify `boundaries`가 검사).

## Soft (기존 코드가 다르면 기존 코드를 따른다)

- API 경로 · 인증 정책은 iOS `Service/API` · `Service/APIEndpoint`와 같은 이름과 묶음으로 둔다.
