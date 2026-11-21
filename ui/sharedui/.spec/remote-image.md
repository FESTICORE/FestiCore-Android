---
spec: remote-image
modules: [ui/sharedui, app]
---

# 원격 이미지 Spec

> `FCRemoteImage`는 서버 이미지를 Coil 싱글톤 로더로 캐시해 그리고, 없음 · 실패는 Figma `image=empty` 모양으로 그린다.

iOS 대응: `Projects/UI/FestiCoreSharedUI/Sources/RemoteImage/FCRemoteImage.swift`, 사용 예 `Projects/Feature/Festival/Sources/Components/FestivalPosterView.swift`.
설계: `.claude/docs/specs/2026-11-21-festival-list-android-design.md`(이미지 로딩 · 결정 D4 · D7 · PR F3). 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

`FCRemoteImage(url, modifier, contentScale, placeholderIcon, placeholderIconSize)`(`ui/sharedui`). 크기 · 모서리는 호출하는 쪽이 `modifier`로 정한다(iOS `.frame` · `.clipShape`). 로더는 `installFestiCoreImageLoader(context)`로 앱 시작 때 한 번 설정한 Coil `SingletonImageLoader`다(Koin을 쓰지 않는다).

## 동작 규칙

1. `url == null`: 기본 모양만 그리고 요청하지 않는다.
2. 불러오는 중(Coil `Empty` · `Loading`): `fillNormal` 바탕 위에 `FCSkeletonView(Rect)`(iOS Kingfisher `placeholder`). 스켈레톤은 "불러오는 중"으로 읽힌다(iOS와 같음).
3. 성공: 이미지만 그린다. 0.2초 crossfade(iOS `fade(duration: 0.2)`).
4. 실패(네트워크 · 400 · 디코딩): 기본 모양을 그리고 이미지는 그리지 않는다.
5. 기본 모양: `fillStrong` 바탕 + 가운데 `placeholderIcon`(`labelAssistive` 색, 크기 기본 24). 아이콘이 `null`이면 바탕만. 접근성 내용 없음.
6. `contentScale`: 기본 `Crop`(iOS `.fill`), `Fit`(iOS `.fit`). 넘친 부분은 항상 자른다(iOS `.clipped()`).
7. `url`이 바뀌면 상태를 처음부터 다시 시작한다(iOS `.id(url)`).
8. 캐시: 메모리 앱 메모리의 25%, 디스크 `cacheDir/image_cache` 디스크의 2%(Coil이 10MB~250MB로 자름). 응답 `cache-control`(stage 이미지 `max-age=86400`)은 보지 않고 디스크에 있으면 다시 쓴다(iOS Kingfisher 기본값 · Coil 기본 `CacheStrategy`와 같음).
9. 네트워크: `coil-network-okhttp`(결정 D7). 인증 헤더를 붙이지 않는다(이미지 경로는 미인증 GET).

## 엣지 케이스

- 없는 이미지 이름은 서버가 400을 준다: 실패와 같게 기본 모양.
- 같은 화면에 머무는 동안 실패한 이미지는 다시 받지 않는다. 다시 받으려면 호출하는 쪽이 key를 바꾼다(설계 위험 4, F4 `imageRevision`).

## 제약 / 가정

- iOS와 다른 점: SVG 처리기(`SVGProcessor` · `SVGCacheSerializer`)는 옮기지 않았다(목록 썸네일은 webp). 디스크 캐시는 iOS(크기 제한 없음 · 7일 만료)와 달리 크기 상한 LRU이고 기간 만료가 없다.
- 터치: Compose `Image`는 클릭을 받지 않으므로 iOS `allowsHitTesting(false)`가 필요 없다.

## 작업 모듈

| 모듈                        | 계층 | 변경 내용                                                                                                  |
| --------------------------- | ---- | ---------------------------------------------------------------------------------------------------------- |
| `ui/sharedui`               | ui   | 신규. `FCRemoteImage` · `installFestiCoreImageLoader` · 프리뷰 · 상태 분기 테스트(`FakeImageLoaderEngine`) |
| `app`                       | 앱   | `FestiCoreApplication.onCreate`에서 `installFestiCoreImageLoader`                                          |
| `gradle/libs.versions.toml` | 빌드 | Coil 3.6.3(`coil-compose` · `coil-network-okhttp` · `coil-test`)                                           |
