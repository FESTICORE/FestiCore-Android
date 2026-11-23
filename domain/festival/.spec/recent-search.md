---
spec: recent-search
modules: [core/storage, domain/festival, domain/auth, app]
---

# 최근 검색 Spec

> 페스티벌 검색 화면이 쓰는 최근 검색어를 사용자별로 기기에 최대 10개 저장한다.

iOS 대응: `Projects/Domain/FestivalDomain/Interface/Sources/RecentSearch/FestivalRecentSearchStore.swift`, `Sources/RecentSearch/FestivalRecentSearchStore+Live.swift`, `Interface/Sources/Library/FestivalLibraryUseCaseInterface.swift`, `Sources/Library/FestivalLibraryUseCaseImpl.swift`, 테스트 `Tests/Sources/FestivalRecentSearchStoreTests.swift`.
설계: `.claude/docs/specs/2026-11-21-festival-list-android-design.md`(결정 D3 · PR F2). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

`FestivalLibraryUseCase`(domain/festival/interface)가 화면의 입구다. 구현은 `RecentSearchStore`(core/storage, DataStore Preferences 파일 `festicore_recent_search`)에 그대로 넘긴다. 사용자 ID는 화면이 `CurrentUserProvider.currentUserId`(domain/auth, `domain/auth/.spec/current-user.md`)에서 받아 넘긴다.

## 동작 규칙

1. 키: 로그인 사용자 `FestiCore.festivalRecentSearches.user.{userId}`, `userId`가 null이거나 빈 문자열이면 `FestiCore.festivalRecentSearches.anonymous`(iOS와 같은 이름).
2. 값: 검색어를 `\n`으로 이은 문자열 하나. 읽을 때 빈 조각은 버린다(iOS `split(separator:)`).
3. 추가 `addRecentSearch(query, userId)`: 앞뒤 공백을 지우고 비면 저장하지 않고 기존 목록을 돌려준다. 아니면 맨 앞에 넣고 같은 값은 뒤에서 지운 뒤 앞 10개만 저장하고 돌려준다.
4. 삭제 `removeRecentSearch(query, userId)`: 같은 값(공백을 지우지 않음)을 지우고 남은 목록을 저장하고 돌려준다.
5. 전체 삭제 `clearRecentSearches(userId)`: 그 사용자 키만 지운다.
6. 읽기 · 쓰기는 사용자 키끼리 섞이지 않는다. 익명 기록은 로그인 시 옮기지 않고, 로그아웃 · 세션 만료 때도 지우지 않는다(iOS와 같음).
7. 표시 중인 사용자가 바뀌면(`currentUserId` 변경) 화면은 새 키에서 다시 읽고 이전 사용자의 목록을 남기지 않는다(F4 `onUserChanged`).

## 엣지 케이스

- 검색어 안의 줄바꿈은 iOS와 같이 다시 읽을 때 두 검색어로 나뉜다.
- 공백만 있는 검색어는 저장하지 않는다.

## 제약 / 가정

- DataStore는 core/storage에서만 쓴다. domain/festival/sources가 `RecentSearchStore`를 주입받는다.
- iOS의 예매 축제(`bookedFestivalIDs`)는 북마크 단계에서 같은 UseCase에 붙인다.

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `core/storage` | core | `RecentSearchStore` · `DataStoreRecentSearchStore` · Koin 등록 |
| `domain/festival/interface` | domain | `FestivalLibraryUseCase`(최근 검색 4개 함수) |
| `domain/festival/sources` | domain | `FestivalLibraryUseCaseImpl` · Koin 등록 · `core:storage` 의존 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/festival`(F4) | feature | 검색 모드에서 UseCase와 `currentUserId`를 쓴다 |

## 미해결 질문

(없음)

## 변경 이력

- F2 최근 검색 저장소 · UseCase 추가
