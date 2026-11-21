# 디자인 시스템

토큰 원천은 `FestiCore-DesignSystem` 저장소다. Android는 그 저장소가 생성한 Kotlin을 그대로 가져와 쓴다(CLAUDE.md Hard 10). 설계: `.claude/docs/specs/2026-11-20-design-system-android-design.md`

## 구성 (`ui/designkit`)

| 위치 | 내용 | 손으로 고치나 |
|------|------|---------------|
| `src/main/kotlin/.../tokens/` | `FestiCoreSemanticColor` · `FestiCoreSpacing` · `FestiCoreRadius` · `FestiCoreState` · `FestiCoreTextStyle` (생성물) | 아니오. 동기화로만 바뀐다 |
| `tokens.source-sha` | 가져온 DesignSystem 커밋 SHA | 아니오 |
| `font/FestiCoreFontFamily` | Pretendard(가변, 400 · 500 · 700) · Bagel Fat One | 예 |
| `typography/FestiCoreTextStyles` | `FestiCoreTextStyle.textStyle` → Compose `TextStyle`(iOS `festiCoreFont` + `festiCoreLineBox`), `textStyleWithoutLineBox`(줄 박스 없음, iOS `festiCoreFont`만) | 예 |
| `theme/FestiCoreTheme` | Material 색 · 기본 본문 스타일을 토큰으로 맞춘다 | 예 |
| `res/font` · `licenses/` | iOS와 같은 폰트 파일과 OFL | 폰트 교체 시만 |
| `res/drawable` | iOS `ImageAssets.xcassets` 아이콘(`ic_*` · `nav_*` · `logo` · `google_logo`) | 아이콘 추가 시만 |

이름은 iOS와 같다: `FestiCoreSemanticColor.labelNormal.color`, `FestiCoreSpacing.marginM16`, `FestiCoreTextStyle.baseBodyBold.textStyle`.

```kotlin
Text(
    text = "축제",
    style = FestiCoreTextStyle.baseHeadlineBold.textStyle,
    color = FestiCoreSemanticColor.labelNormal.color,
)
```

## 규칙

- 화면 코드는 semantic 색만 쓴다. `Color(0x…)` 하드코딩은 `check-boundaries`가 막는다(생성 토큰 폴더 · 테스트만 허용). 새 색은 DesignSystem 토큰에 먼저 추가한다.
- 생성 토큰 파일은 고치지 않는다. 포맷 검사(ktlint)에서도 뺀다.
- 앱 루트는 `FestiCoreTheme`으로 감싼다.
- 가변 폰트 굵기 축은 API 26 이상에서만 적용된다(minSdk 24).

## 토큰 동기화

```bash
gradle/sync-design-tokens ../FestiCore-DesignSystem   # 원천의 커밋된 생성물 3개를 확인 후 교체하고 SHA를 남긴다(실패 시 기존 유지)
```

- `.github/workflows/design-token-sync.yml`이 6시간마다(또는 수동) DesignSystem `main`을 확인해, 바뀌었으면 원천 `npm run check` → 동기화 → check-boundaries · ktlint · 단위 테스트 · stage 빌드 → 검증된 브랜치 `feature/design-tokens-<sha7>`와 이슈를 만들고, 이슈에 PR 열기 링크와 절차를 댓글로 남긴다.
- FESTICORE 조직 정책상 Actions는 PR을 만들 수 없다. PR은 이슈의 링크나 `gh pr create`로 사람(또는 에이전트가 사용자 토큰으로)이 연다. 그래서 Android PR · PR Rules · Gemini · Codex가 보통 PR처럼 돈다. 결과를 확인하고 squash merge한다.
- 같은 SHA의 열린 PR, 또는 브랜치와 열린 동기화 이슈가 이미 있으면 다시 만들지 않는다. 중간에 실패해 브랜치나 이슈만 남았으면 같은 제목의 열린 이슈를 재사용하고 브랜치를 덮어쓴다.

## 아이콘 추가

iOS `ImageAssets.xcassets`에 추가된 SVG를 같은 이름 규칙으로 바꾼다. 변환기는 의미를 그대로 옮길 수 있는 형태(`path`의 `d` · `fill`, 효과 없는 전체 캔버스 clipPath)만 받고 `transform` · `opacity` · `stroke` · 다른 요소가 있으면 실패한다. 실패한 아이콘은 직접 VectorDrawable로 그리거나 원본 SVG를 단순화한다.

```bash
python3 -I gradle/svg-to-vector.py ../FestiCore-iOS/Projects/UI/FestiCoreDesignKit/Resources/ImageAssets.xcassets ui/designkit/src/main/res/drawable
```

## 컴포넌트

iOS `FestiCoreDesignKit/Sources/Component`(82개)는 화면을 옮길 때 그 화면이 쓰는 그룹부터 `ui/designkit`에 옮긴다. 이름은 iOS와 같게(`FCButton` ↔ `FCButton`) 두고 `@Preview`를 함께 둔다(`.claude/rules/ui.md`).
