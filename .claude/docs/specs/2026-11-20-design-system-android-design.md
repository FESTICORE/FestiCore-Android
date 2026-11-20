# 디자인 시스템 Android 설계

## 목표

FestiCore-DesignSystem 토큰을 iOS와 같은 원천 · 같은 이름으로 Android에서 쓴다. 토큰 값은 손으로 옮기지 않고 생성기로 만든다(CLAUDE.md Hard 10). 이 문서의 범위는 토큰 · 폰트 · 이미지 · 테마 · 동기화까지다. iOS `FestiCoreDesignKit`의 컴포넌트(82개)는 화면을 옮길 때 그 화면이 쓰는 그룹부터 옮긴다.

## 수용 기준

1. DesignSystem 저장소에 `scripts/build-android.ts`가 있고 `npm run build`가 `generated/android/tokens/`에 Kotlin 3개(`FestiCoreSemanticColor.kt` · `FestiCoreLayout.kt` · `FestiCoreTypography.kt`)를 만든다. iOS 생성물은 바이트 단위로 그대로다(`npm run check` 통과, `generated/ios` diff 없음).
2. DesignSystem `npm run typecheck` · `npm test` · `npm run check` · `.claude/scripts/verify` 통과. 새 테스트가 색 hex → `0xAARRGGBB` 변환, 공개 색 이름 = iOS 스냅샷(`tests/fixtures/public-color-names.json`), sp · em 변환, 생성 결과 결정성을 검사한다. DesignSystem PR이 `main`에 squash merge된다.
3. Android 저장소에 `ui/designkit` 모듈이 있다. `ui/designkit/src/main/kotlin/io/festicore/ui/designkit/tokens/`의 Kotlin 3개는 DesignSystem 머지 커밋의 `generated/android/tokens/`와 바이트 단위로 같고, 그 커밋 SHA를 `ui/designkit/tokens.source-sha`에 남긴다.
4. Pretendard(가변) · Bagel Fat One 폰트와 OFL 라이선스, iOS 이미지 에셋(Apple 로고 제외 52개)이 Android 리소스로 들어간다.
5. `FestiCoreTheme`과 `FestiCoreTextStyle.textStyle`로 앱 화면이 토큰을 쓴다(MainActivity가 사용).
6. 토큰 밖 색 하드코딩(`Color(0x…)`)을 `check-boundaries`가 막는다(`ui/designkit` tokens 폴더만 허용). `android-pr` CI에도 `check-boundaries` 단계를 넣어 PR에서 같은 검사를 한다.
7. Android `.claude/scripts/verify --all` · PR CI(`android-pr` · `pr-rules`) 통과. Gemini 리뷰가 `.kt` 변경 PR에서 실행된다(Codex 리뷰는 OpenAI 할당량 초과로 실패 중 — 결과를 PR에 기록).
8. `.github/workflows/design-token-sync.yml`(일정 · 수동)이 DesignSystem `main` 최신 커밋을 고정해 `npm run check` 후 토큰을 동기화하고, 바뀌었으면 PR을 연다. 로컬에서는 `gradle/sync-design-tokens <DesignSystem 경로>`로 같은 동기화를 한다. 자동 PR은 `GITHUB_TOKEN`으로 만들어 다른 워크플로가 돌지 않으므로, 워크플로가 PR을 draft로 열고 본문에 "CI · AI 리뷰 미실행: Ready for review로 바꾼 뒤 닫았다 다시 열어 CI · Gemini · Codex를 실행하고 결과를 확인한 뒤 머지"를 적는다. 사람이 그 절차로 CI와 AI 리뷰를 돌린 뒤에만 머지한다(`.claude/docs/design-system.md`에 같은 절차).

## 결정

| 항목        | 결정                                                                                                                                                                                          | 근거                                                                                              |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 생성기 위치 | DesignSystem 저장소 `scripts/build-android.ts`                                                                                                                                                | 토큰 원천 저장소(Hard 10, iOS와 같은 방식)                                                        |
| 공통 코드   | `build-ios.ts`의 로딩 · 검증 함수(`loadTheme` · `samePathsAndValues` · `swiftName` 등)에 `export`만 붙여 Android 생성기가 가져다 쓴다                                                         | 가장 작은 diff. iOS 출력은 `npm run check`로 변하지 않음을 증명                                   |
| 명령        | `npm run build` = iOS + Android, `npm run check` = 두 생성물 모두 커밋본과 비교                                                                                                               | iOS · Android가 같은 토큰 커밋에서 갈라지지 않게                                                  |
| 패키지      | `io.festicore.ui.designkit.tokens`                                                                                                                                                            | Android namespace 규칙(`gradle.md`)                                                               |
| 이름        | iOS와 같은 이름(`FestiCoreSemanticColor.labelNormal`, `FestiCoreSpacing.marginM16`, `FestiCoreTextStyle.baseBodyBold`)                                                                        | CLAUDE.md Hard 9 · 코드 컨벤션. 생성 파일은 ktlint 검사 밖                                        |
| 색 API      | `class FestiCoreSemanticColor(val light: Color, val dark: Color)` + `val color: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) dark else light`                     | iOS `.color`가 시스템 다크 모드에 반응하는 것과 같다                                              |
| 레이아웃    | `object FestiCoreSpacing` · `FestiCoreRadius`(`Dp`), `object FestiCoreState { val disabledOpacity: Float }`                                                                                   | iOS `CGFloat` 상수와 대응                                                                         |
| 타이포      | `enum class FestiCoreTextStyle(size: TextUnit(sp), fontWeight: FontWeight, lineHeight: TextUnit(sp = 배수 × size), letterSpacing: TextUnit(em))`                                              | DesignSystem README의 Android 규칙(lineHeight × fontSize sp, letterSpacing em 그대로)             |
| 폰트        | iOS와 같은 `PretendardVariable.ttf`를 `res/font`에 두고 `FontVariation.weight(400/500/700)`로 굵기 지정. API 26 미만은 가변 축이 무시되어 기본 굵기로 보인다(minSdk 24, 위험에 기록)          | iOS와 같은 파일 · 같은 굵기                                                                       |
| 이미지      | iOS `ImageAssets.xcassets` SVG 52개를 `svg2vectordrawable`로 VectorDrawable XML로 한 번 변환해 `res/drawable`에 커밋(`ic_*` · `nav_*` · `logo`), PNG 1개는 그대로. Apple 로고 제외            | Figma 원본 export가 iOS에 있다. 변환 결과는 `assembleStageDebug`(aapt)로 검증                     |
| 동기화      | `gradle/sync-design-tokens` 스크립트 하나를 로컬 · CI가 같이 쓴다. CI PR은 `GITHUB_TOKEN`으로 만들어 다른 워크플로를 깨우지 않으므로, 워크플로 안에서 verify와 같은 Gradle 명령을 먼저 돌린다 | iOS `design-token-sync.yml`의 "SHA 고정 → check → 가져오기 → 빌드 → PR" 순서를 ubuntu 러너로 줄임 |

## 단계

### DS1 — DesignSystem 저장소 (이슈 → `feature/android-tokens` → `main` PR)

- `scripts/build-ios.ts`: 공통 함수 `export`만 추가
- `scripts/build-android.ts`: Kotlin 작성기 3개, `generated/android/tokens/` 출력, 출력 폴더 안전 검사는 iOS와 같은 방식
- `scripts/check-android.ts` 또는 `check-ios.ts` 확장, `package.json` `build` · `check`
- `tests/build-android.test.mjs`, README · CHANGELOG에 Android 생성 절(사용 예 포함)
- 검증: `mise exec node@22.23.2 -- npm run typecheck && npm test && npm run check`, `.claude/scripts/verify`

### DS2 — Android 저장소 (이슈 → `feature/designkit` → `develop` PR)

- `ui/designkit`(`festicore.android.compose` + Compose BOM · foundation · material3): 동기화한 tokens, `PretendardFontFamily` · `BagelFatOneFontFamily`, `FestiCoreTextStyle.textStyle`, `FestiCoreTheme`
- `res/font`(폰트 2개), `ui/designkit/licenses/`(OFL 2개), `res/drawable`(이미지 52개 + PNG 1개)
- `gradle/sync-design-tokens`, `ui/designkit/tokens.source-sha`, `.github/workflows/design-token-sync.yml`
- `check-boundaries`: `Color(0x` 규칙 + 테스트, `gradle/format` 제외 경로 확인
- `.github/workflows/android-pr.yml`: `check-boundaries` 단계 추가
- `app`: `app/build.gradle.kts`에 `implementation(project(":ui:designkit"))` 추가, `FestiCoreTheme` 적용, 화면 문구에 토큰 사용
- 문서: `.claude/docs/design-system.md`(원천 · 동기화 · API · 컴포넌트 이식 방식), CLAUDE.md 색인 한 줄
- 단위 테스트: `textStyle` 변환(sp · em · 굵기), 색 light/dark 값 1개, 간격 1개
- 검증: `gradle/sync-design-tokens ../FestiCore-DesignSystem` 후 git diff 없음, `.claude/scripts/verify --all`, PR CI

## 비목표

- 컴포넌트 82개 이식(화면 이식 단계에서 그룹별)
- 스냅샷(스크린샷) 테스트 도입
- Live Activity · 위젯 관련 에셋
- iOS 저장소 변경(생성기 공통 함수 export는 DesignSystem 안의 변경)

## 가정 · 출처

참조 저장소는 이 저장소와 같은 상위 폴더에 있다: `../FestiCore-DesignSystem`, `../FestiCore-iOS`.

| 가정                                                                               | 출처                                                                                                          |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| iOS 생성 대상: semantic 색 · spacing · radius · disabled opacity · typography 37개 | DesignSystem `README.md` iOS 절, `scripts/build-ios.ts`                                                       |
| 색 hex는 `#RRGGBB` 또는 `#RRGGBBAA`                                                | `build-ios.ts` `hexColor`                                                                                     |
| Android 단위 규칙: 1px = 1dp/sp, lineHeight 배수 × fontSize, letterSpacing em      | DesignSystem `README.md` 27~30행                                                                              |
| 폰트 파일 · 라이선스                                                               | iOS `Projects/UI/FestiCoreDesignKit/Resources/FontAsset/` (Pretendard `LICENSE.txt`, Bagel Fat One `OFL.txt`) |
| 이미지                                                                             | iOS `Resources/ImageAssets.xcassets`(SVG 52 · PNG 1 · Apple 로고 1)                                           |
| DesignSystem은 `main`으로 PR, 브랜치 `feature/…`, 커밋 한국어 + `#이슈`            | DesignSystem `docs/agent/harness.md`                                                                          |
| Node 22.23.2 (`.nvmrc`), 로컬 기본 node는 24라 `mise exec node@22.23.2`로 실행     | 이 세션에서 확인                                                                                              |

## 위험

- 가변 폰트 굵기: API 24 · 25에서 굵기 축이 적용되지 않는다. 출시 기준 기기 비중을 보고 필요하면 정적 굵기 파일로 바꾼다.
- `svg2vectordrawable`가 지원하지 않는 SVG 기능(마스크 · 필터)이 있으면 변환이 틀릴 수 있다 → 변환 경고를 확인하고, 실패한 파일은 목록으로 남겨 해당 화면 이식 때 처리한다.
- 시각 동일성(iOS와 픽셀 대조)은 이번 범위에서 확인하지 않는다.
