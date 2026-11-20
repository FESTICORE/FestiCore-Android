# Gradle 가이드

Gradle 루트는 저장소 루트 하나다. 빌드 스크립트는 모두 Kotlin DSL(`.gradle.kts`)로 쓴다. 구조: `.claude/docs/architecture.md`

## 구성

```
settings.gradle.kts        모듈 자동 포함 · 저장소 · build-logic 연결
build.gradle.kts           플러그인 버전만 선언 (apply false)
gradle.properties          빌드 캐시 · configuration cache · parallel
gradle/libs.versions.toml  버전 카탈로그 (버전 문자열은 여기에만)
gradle/wrapper/            Gradle wrapper
gradle/format              ktlint 실행 (고치기 · --check)
build-logic/               convention plugin
```

## 기준 버전

처음 값은 이미 함께 빌드가 검증된 조합에서 시작한다. 올릴 때는 이슈 · PR로 한 번에 하나씩 올리고 verify · CI로 확인한다.

| 항목 | 버전 | 고정 위치 |
|------|------|-----------|
| JDK | temurin-21 | `.mise.toml` |
| ktlint | 1.8.0 | `.mise.toml` |
| Gradle | 9.9.1 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP · Kotlin · Compose BOM · Orbit · Koin · Ktor · Navigation 3 | 카탈로그 값 | `gradle/libs.versions.toml` |

## convention plugin

| 플러그인 | 적용 대상 | 하는 일 |
|----------|-----------|---------|
| `festicore.android.library` | `core/**` · `service/**` · domain · feature `interface` | Android 라이브러리, `io.festicore.{폴더 경로}` namespace(`interface`는 예약어라 `contract`), compileSdk · minSdk · JDK 21, 단위 테스트 |
| `festicore.android.compose` | `ui/**` | 위 라이브러리 + Compose 컴파일러 · `buildFeatures.compose` |
| `festicore.android.feature` | feature `sources` | 위 + Compose · Orbit · Koin · Navigation 3 · kotlinx.serialization, 테스트(JUnit · orbit-test · coroutines-test) |
| `festicore.android.application` | `app` | 앱(applicationId `io.festiCore.co`), flavor `stage`(`.stage` 접미사) · `prod`, Compose · Koin · Navigation 3 |

- 모듈 `build.gradle.kts`에는 `plugins { id("festicore.android.library") }`와 의존성만 쓴다. 공통 설정을 복사하지 않는다.
- SDK · JDK 값은 build-logic 한 곳에 둔다.

## 모듈 자동 포함

`settings.gradle.kts`는 `app` · `feature` · `domain` · `service` · `core` · `ui` 아래에서 `build.gradle.kts`가 있는 폴더를 모두 include한다(경로 = 모듈 이름). include를 손으로 적지 않는다. 모듈을 지우면 폴더째 지운다.

- `build` · `src` · `gradle` · `.`으로 시작하는 폴더는 찾지 않는다.
- 폴더 탐색은 스크립트에서 `listFiles()` · `isFile`을 직접 부른다. 표준 함수(`walkTopDown`) 안에서 부르면 Gradle이 추적하지 못해 configuration cache가 예전 모듈 목록을 쓴다.
- feature를 만든 뒤 `app`에 sources 의존과 `{이름}FeatureModule`(Koin 목록)을 추가한다.

## 자주 쓰는 명령

저장소 루트에서 `mise exec -- ./gradlew ...`로 실행한다(JDK를 mise 고정 버전으로 쓴다).

| 명령 | 하는 일 |
|------|---------|
| `./gradlew testStageDebugUnitTest assembleStageDebug` | 앱 단위 테스트 · stage 디버그 빌드 |
| `./gradlew testDebugUnitTest` | 라이브러리 모듈 단위 테스트 |
| `./gradlew :app:installStageDebug` | 연결된 기기 · 에뮬레이터에 설치 |
| `gradle/format` · `gradle/format --check` | ktlint 고치기 · 검사 |
| `./gradlew --stop` | 데몬 정리 (JDK를 바꿨을 때) |

## 금지

- Groovy(`.gradle`) 스크립트, 모듈 스크립트 안 버전 문자열
- 모듈마다 공통 설정 복사 (convention plugin을 고친다)
- 검증 명령 뒤에 `| tail` 같은 파이프로 종료 코드 가리기
