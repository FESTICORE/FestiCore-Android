---
paths:
  - "domain/**"
---

# domain 경로 규칙

상세 문서: `.claude/docs/architecture.md`, `.claude/docs/dependency-injection.md`

## Hard

- domain은 `interface`(Entity · Repository 인터페이스 · UseCase 인터페이스)와 `sources`(구현 · `di/`)로 나눈다.
- `interface`는 Android 프레임워크 · Compose · Koin · Ktor를 의존하지 않는다. 순수 Kotlin만 쓴다.
- API 호출은 `sources`의 Repository 구현에서 `service/apiendpoint` 요청 · `sources/service`의 서비스 계약(구현은 `service/festivalcatalog`)을 주입받아 한다. 응답 DTO는 `sources/model/dto`, 매핑은 `sources/model/mapper`에 두고, `sources` 밖으로 내보내지 않고 Entity로 바꿔 돌려준다.
- 모듈 이름은 iOS `Projects/Domain/{기능}Domain`과 같은 기능 이름을 쓴다(`AuthDomain` ↔ `domain/auth`).

## Soft (기존 코드가 다르면 기존 코드를 따른다)

- UseCase는 `operator fun invoke` 하나만 공개한다.
- Repository · UseCase 테스트는 가짜 구현으로 쓰고 실제 네트워크를 부르지 않는다.
