# 디자인 컴포넌트 Android 이식 설계

## 목표

iOS `../FestiCore-iOS/Projects/UI/FestiCoreDesignKit/Sources/Component`의 SwiftUI 컴포넌트 82개(약 9,000줄)를 `ui/designkit`의 Compose 컴포넌트로 옮긴다. 이름 · 매개변수 의미 · 토큰 사용은 iOS와 같게 두고, 화면 이식(온보딩부터)이 바로 쓸 수 있게 한다.

## 수용 기준

1. iOS 컴포넌트 파일마다 같은 이름의 Compose 컴포넌트가 `ui/designkit/src/main/kotlin/io/festicore/ui/designkit/component/{그룹}/{이름}.kt`에 있다(예: `FCPrimaryButton`). iOS 파일 하나에 여러 공개 View가 있으면 같은 파일에 같은 이름으로 둔다. `View+ScrollBottomInset`처럼 SwiftUI 확장은 Modifier 확장으로 옮긴다.
2. 색 · 글꼴 · 비활성 투명도는 생성 토큰(`FestiCoreSemanticColor` · `FestiCoreTextStyle` · `FestiCoreState`)만 쓴다. 간격 · 반경은 같은 값의 토큰(`FestiCoreSpacing` · `FestiCoreRadius`)이 있으면 반드시 토큰을 쓴다. 토큰에 없는 컴포넌트 고유 크기(컨트롤 높이 · 아이콘 크기 · 그랩버 등, iOS에서도 리터럴인 값)만 iOS와 같은 값을 그 컴포넌트 파일의 이름 있는 `private val` 상수(예: `private val Height = 36.dp`)로 둔다. C1에서 `.claude/rules/ui.md`의 "크기 하드코딩 금지"를 이 규칙으로 고친다. `check-boundaries`의 색 하드코딩 검사를 통과한다.
3. 모든 공개 컴포넌트에 `@Preview`가 있다(라이트 · 다크 각 1개 이상인 그룹 미리보기 파일 허용).
4. iOS `Tests/FestiCoreDesignKitFigmaSizeTests.swift`의 높이 · 크기 검사(`FigmaHeightCase.all` · 배지 16×16 · 존 칩 100×26)를 Robolectric + `compose-ui-test`로 같은 기대값으로 옮겨 통과한다. iOS `FestiCoreDesignKitTests.swift`(24개)의 로직 검사(상태 계산 · 포맷 등)는 해당 Kotlin 함수의 JVM 단위 테스트로 옮긴다. 그중 시트 시나리오(`FestiCoreDesignKitTests.swift` 16~246행: 교체 · 재열기 · 제거 중 dismiss 경합, 상위 모달 상태 전파)는 `FCSheet` Compose 테스트로 같은 시나리오 · 같은 기대 결과로 옮긴다(C7).
5. 그룹별 PR(아래 순서)이 develop에 squash merge되고, 각 PR에서 `.claude/scripts/verify --all`과 PR CI(Android PR · PR Rules)가 통과한다. Gemini 리뷰 결과(또는 실패 사유)를 PR에 적는다.
6. 그룹마다 iOS 대응 경로 · 옮기지 않은 것 · 다르게 둔 것을 PR 본문에 적는다.

## 결정

| 항목              | 결정                                                                                                                                                                                                                  | 근거                                                      |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| 위치              | `ui/designkit`의 `component/{그룹}` 패키지                                                                                                                                                                            | iOS `FestiCoreDesignKit/Sources/Component/{그룹}`과 1:1   |
| API               | iOS `init` 매개변수 순서 · 이름 · 기본값을 따르고, 클로저는 `onClick` 등 Compose 관례 이름(`action` → `onClick`), 첫 매개변수 앞에 `modifier: Modifier = Modifier`는 Compose 관례대로 기본값 있는 첫 선택 인자로 둔다 | iOS 대응 추적 + Compose API 가이드                        |
| 설정 메서드 · 확장 | iOS 컴포넌트의 모든 공개 설정 메서드(인자 개수 · 라벨 무관, 예: `FCTopBar.trailingAction(icon:accessibilityLabel:action:)` · `shareLink(_:title:)` · `onBearer(title:_:)`)와 반환형이 `Self`가 아닌 공개 View · Modifier 확장은 같은 의미의 Compose 매개변수(슬롯 · 콜백 포함, 기본값은 iOS 기본값) 또는 Modifier 확장으로 모두 보존한다. 각 그룹 PR에서 iOS 파일의 `public` 선언 목록과 1:1로 대조해 PR 본문에 대응표를 남긴다 | 후속 호출부 기능 보존 |
| 상태              | 컴포넌트는 상태를 들지 않는다(값 + 콜백). iOS `@Binding`은 `value` + `onValueChange`                                                                                                                                  | Compose state hoisting                                    |
| 비활성            | iOS와 같이 요소 전체 `alpha(FestiCoreState.disabledOpacity)`                                                                                                                                                          | 토큰 규칙                                                 |
| 다이얼로그 | `FCDialog` → Compose `Dialog` 래퍼. 같은 레이아웃 · 버튼 콜백에 iOS 공개 설정 `textAlignment(_:)` · `confirmStyle(_:)`를 `textAlign` · `confirmStyle` 매개변수(같은 선택지 enum)로 보존해, 공유 UI의 알림 상태가 정렬 · 확인 버튼 스타일을 그대로 넘길 수 있게 한다 | iOS `PopUp/FCDialog.swift` 106~119행, `FestiCoreSharedUI` Alert 호출부 |
| 시트 | `FCSheetModifier` → `FCSheet(isPresented, onDismissRequest, height, isDismissible, accessory, content)`(내부 `ModalBottomSheet`). 보존: 높이 지정(전체 높이 포함), 상단 accessory, `isDismissible=false`면 딤 탭 · 드래그 · 뒤로 가기로 닫히지 않음(예: 저장 중 닫기 차단), 내용 교체 시 닫았다 열지 않고 상태 유지, 닫힘 중 다시 열면 재열기가 이김(지연 dismiss 취소), 중첩 시트가 열려 있음을 상위에 알림(`LocalFCSheetPresentation`), 닫기 접근성 라벨. Android 차이: iOS의 탭바 아래까지 덮기는 `ModalBottomSheet`가 창 위에 그려져 기본으로 충족(PR에 기록) | iOS `Modal/FCSheetModifier.swift` 22~174행, 호출부 `NicknameEditView.swift:46-47` · `MainTabView.swift:67-78,135-142` |
| 세그먼트          | `FCSegmented`의 iOS 전용 효과(matchedGeometry 등)는 `animateDpAsState` 위치 이동으로 같은 결과                                                                                                                        | 플랫폼 차이                                               |
| 사진 선택         | `FCPhotoPicker`는 UI(그리드 · 추가 버튼)만 컴포넌트로 두고, 실제 선택은 화면이 `PickVisualMedia` 결과를 넘긴다                                                                                                        | 컴포넌트 무상태                                           |
| 테스트 의존       | `libs.versions.toml`에 `robolectric`, `compose-ui-test-junit4`, `compose-ui-test-manifest` 추가, `ui/designkit`만 사용                                                                                                | 크기 검사                                                 |
| Material          | Material3 컴포넌트는 내부 구현에만 쓰고 공개 API에 Material 타입을 노출하지 않는다                                                                                                                                    | 디자인 시스템 경계                                        |

## 그룹 순서 (PR 단위)

| PR  | 그룹                                                                      | iOS 파일 수 · 줄                |
| --- | ------------------------------------------------------------------------- | ------------------------------- |
| C1  | Button · Action · Divider · Layout · Media + Robolectric 크기 테스트 기반 | 1 · 11 · 1 · 1 · 2 / 약 1,420줄 |
| C2  | Label                                                                     | 11 / 767줄                      |
| C3  | Navigation(TabBar 포함)                                                   | 8 / 1,232줄                     |
| C4  | Card                                                                      | 8 / 1,734줄                     |
| C5  | List                                                                      | 10 / 1,279줄                    |
| C6  | Selection                                                                 | 15 / 1,540줄                    |
| C7  | Overlay · PopUp · Toast · Modal · Skeleton                                | 4 · 2 · 1 · 1 · 2 / 1,105줄     |
| C8  | Timetable                                                                 | 4 / 407줄                       |

각 PR은 그 그룹의 Figma 높이 케이스와 로직 테스트를 함께 옮긴다. 다른 그룹 컴포넌트에 의존하면 먼저 옮긴 그룹만 쓴다(순서가 그렇게 짜여 있다; 어기는 경우 PR에서 해당 컴포넌트를 앞당긴다).

## 비목표

- 화면(Feature) 이식, 데모 앱(iOS `Demo/`)
- 스크린샷(픽셀) 대조 테스트
- `FestiCoreSharedUI` · `FestiCoreAnimation`(Lottie) 모듈 — 화면 이식 때 필요한 것만

## 가정 · 출처

| 가정                                                                | 출처                                                |
| ------------------------------------------------------------------- | --------------------------------------------------- |
| 컴포넌트는 SwiftUI만 import하고 원격 이미지 로딩을 직접 하지 않는다 | `grep import` 결과 81개 모두 SwiftUI, URL 사용 없음 |
| 플랫폼 의존 파일은 `FCSegmented` · `FCSheetModifier` 두 개          | 소스 검색                                           |
| iOS 크기 기대값은 Figma 심볼 높이                                   | `Tests/FestiCoreDesignKitFigmaSizeTests.swift`      |

## 위험

- Compose와 SwiftUI의 글꼴 메트릭 차이로 텍스트 포함 컴포넌트 높이가 1dp 안팎 다를 수 있다 → 기대값은 iOS와 같게 두고, 차이가 나면 원인(행간 · 패딩)을 PR에 적고 컴포넌트를 맞춘다. 테스트 기대값을 바꾸지 않는다.
- 82개라 PR이 크다 → 그룹 단위로 나누고, 그룹 안에서 iOS 파일과 1:1로 대응시켜 리뷰한다.

## 검증

```bash
.claude/scripts/verify --all
./gradlew :ui:designkit:testDebugUnitTest      # Robolectric 크기 · 로직 테스트
```
