---
paths:
  - "ui/**"
---

# ui 경로 규칙

상세 문서: `.claude/docs/architecture.md`, `.claude/docs/code-convention.md`

## Hard

- 디자인 토큰은 `FestiCore-DesignSystem` 저장소의 원천에서 생성한다. 생성된 토큰 파일을 손으로 고치지 않는다.
- ui 모듈은 feature · domain · service를 의존하지 않는다.
- 컴포넌트는 토큰만 쓴다. 색 · 크기 값을 하드코딩하지 않는다.

## Soft (기존 코드가 다르면 기존 코드를 따른다)

- 컴포넌트 이름은 iOS `FestiCoreDesignKit` · `FestiCoreSharedUI`의 같은 컴포넌트와 맞춘다.
- 컴포넌트마다 `@Preview`를 둔다.
