---
spec: top-bar-search
modules: [ui/designkit]
---

# 상단 바 검색 제출 Spec

> `FCTopBar` 검색 스타일에서 키보드 검색 키를 누르면 `onSearchSubmit`을 부르고 입력 초점을 놓는다.

iOS 대응: `Projects/UI/FestiCoreDesignKit/Sources/Component/Navigation/FCTopBar.swift` `SearchField`(`.submitLabel(.search)`), 사용처 `Projects/Feature/Festival/Sources/FestivalList/View/FestivalListView.swift` 261행 `.onSubmit { saveRecentSearch() }`.
설계: `.claude/docs/specs/2026-11-21-festival-list-android-design.md`(PR F3 · designkit 없는 것 `FCTopBar` 검색 제출).

## 동작 규칙

1. 키보드 동작 키는 검색(`ImeAction.Search`, iOS `.submitLabel(.search)`).
2. 검색 키를 누르면 `onSearchSubmit`을 한 번 부른다. 검색어가 비어도 부른다(iOS `onSubmit`과 같음. 빈 검색어 처리는 화면이 한다).
3. 그 뒤 입력 초점을 놓아 키보드를 닫는다(iOS 제출 기본 동작).
4. `onSearchSubmit` 기본값은 `null`: 부를 것이 없고 초점만 놓는다. 기존 사용처는 바꾸지 않아도 된다.
5. 검색 스타일이 아니면 쓰지 않는다.

## 엣지 케이스

(없음)

## 작업 모듈

| 모듈           | 계층 | 변경 내용                                                                 |
| -------------- | ---- | ------------------------------------------------------------------------- |
| `ui/designkit` | ui   | `FCTopBar(onSearchSubmit)` · 프리뷰 · Compose 테스트 `FCTopBarSearchTest` |
