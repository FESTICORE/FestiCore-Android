---
paths:
  - "ui/**"
---

# ui 경로 규칙

상세 문서: `.claude/docs/architecture.md`, `.claude/docs/code-convention.md`

## Hard

- 디자인 토큰은 `FestiCore-DesignSystem` 저장소의 원천에서 생성한다. 생성된 토큰 파일을 손으로 고치지 않는다.
- ui 모듈은 feature · domain · service를 의존하지 않는다.
- 색 · 글꼴 · 비활성 투명도는 생성 토큰(`FestiCoreSemanticColor` · `FestiCoreTextStyle` · `FestiCoreState`)만 쓴다. 간격 · 반경은 같은 값의 토큰(`FestiCoreSpacing` · `FestiCoreRadius`)이 있으면 반드시 토큰을 쓴다. 토큰에 없는 컴포넌트 고유 크기(컨트롤 높이 · 아이콘 크기 · 그랩버 등, iOS에서도 리터럴인 값)만 iOS와 같은 값을 그 컴포넌트 파일의 이름 있는 `private val` 상수(예: `private val Height = 36.dp`)로 둔다.

## Soft (기존 코드가 다르면 기존 코드를 따른다)

- 컴포넌트 이름은 iOS `FestiCoreDesignKit` · `FestiCoreSharedUI`의 같은 컴포넌트와 맞춘다.
- 컴포넌트마다 `@Preview`를 둔다.
