---
spec: web
modules: [feature/web]
---

# 앱 안 웹 화면 Spec

> 약관 같은 문서를 앱 안에서 보여 주는 화면. 상단 back · 제목, 로딩 · 실패 · 다시 시도.

iOS 대응: `Projects/Feature/Web/Sources/`(`Reducer/WebFeature.swift` · `View/WebView.swift` · `View/WebRepresentableView.swift` · `Policy/WebNavigationPolicy.swift`), `Interface/Sources/WebDelegate.swift`. 아래는 Android에서 참이어야 하는 것과 iOS와 다른 점만 적는다.

## 개요

진입 인자는 `WebPage(url, title)`(`interface`). 화면은 `WebScreen(page, onBackToRoot)`이고 닫기는 부모 Coordinator가 정한다(iOS `WebDelegate.backToRoot`). 지금 쓰는 곳은 가입 정보의 약관 상세(`AuthRoute.LegalDocument`)와 설정 홈 문서(S2, 경로 연결은 T1)다.

## 동작 규칙

1. 상단 바: `FCTopBar(Back, title = page.title)`. back은 `onBackToRoot`. 배경 `backgroundNormal`.
2. 로드 시도: 처음 나타날 때 한 번 `loadId = 1`로 시작한다. 다시 시도는 `loadId`를 1 올리고 로딩으로 간다. 시도마다 웹뷰와 클라이언트를 새로 만들어, 결과 콜백은 그 웹뷰를 연 시도의 `loadId`를 함께 보낸다. 지금 `loadId`와 다르면 무시한다.
3. 단계: 로딩 = 배경 위 가운데 원형 진행 표시(접근성 "문서를 불러오는 중"). 실패 = 위쪽 정렬 `FCEmptyCard`(아이콘 `circle_info_16`, "문서를 불러오지 못했어요" / "연결을 확인하고 다시 시도해 주세요.", 버튼 "다시 시도"), 바깥 여백 16. 로딩 · 실패 중에는 웹뷰를 덮개로 가리고 터치 · 접근성을 막는다.
4. 이동 정책(iOS `document` 범위): 처음 연 주소와 같은 호스트(대소문자 무시)의 https만 연다. 다른 호스트 · http · 앱 스킴(`mailto` 등) · `file`은 열지 않고 외부로도 넘기지 않는다. 첫 페이지 전에 main frame이 막히면 실패다. 처음 주소도 같은 정책을 거친다.
5. 실패 판정: main frame 응답 상태가 200..<300이 아니면, main frame 로드 오류, 첫 페이지 전 인증서 오류(항상 거절), 렌더 프로세스 종료.
6. 렌더 프로세스가 끝나면 실패를 보이고 웹뷰를 새로 만든다. 새 웹뷰는 다시 시도 전까지 로드하지 않는다.
7. 웹뷰 설정은 iOS `WKWebViewConfiguration()` 기본값에 맞춘다: JavaScript · DOM 저장소 켜짐, 새 창 · 자동 팝업 없음(새 창 링크는 지금 웹뷰에서 정책을 거친다). 파일 · content 주소 접근, 혼합 콘텐츠, 위치 권한은 끈다. JavaScript 인터페이스 · 다운로드 처리는 두지 않는다.
8. 문서 주소: `LegalDocumentUrls(baseUrl)`(`interface`, iOS `LegalDocumentURL`)가 가입 화면 · 설정 홈 공용이다. 기준 주소가 주소로 읽히고 scheme `https` · host 있음일 때만 `url(path)` = 기준 주소 + 경로 한 칸, 아니면 null. Koin 정의는 `webFeatureModule(legalBaseUrl)`.

## 엣지 케이스

- 재시도 직후 이전 시도의 늦은 완료 · 실패(멈춘 웹뷰의 콜백 포함)는 이전 `loadId`로 와서 무시된다.
- 첫 페이지 뒤 같은 호스트 링크가 main frame 오류를 내면 실패로 바뀐다(iOS와 같음).
- 시스템 뒤로는 웹 기록을 거슬러 가지 않고 화면을 닫는다(iOS에 웹 안 뒤로 없음).

## 제약 / 가정

- 새 라이브러리 없이 `android.webkit.WebView`를 `AndroidView`로 감싼다.
- feature끼리 sources를 보지 않으므로 다른 기능 스택에 넣을 때는 app이 `WebScreen`을 슬롯으로 넘긴다.

## iOS와 다른 점

- 예매(`booking`) 범위와 외부 앱 넘기기(`externalURLRequested`)는 쓰는 화면이 없어 옮기지 않았다.
- 화면 조회 분석(`screenView(.web)`)은 Android 분석 연결 전이라 보내지 않는다.
- WebView는 탐색마다 식별자가 없어 시도마다 웹뷰 · 클라이언트를 새로 만들어 콜백을 그 시도에 묶는다(iOS는 `WKNavigation`별 시도를 추적).
- 접근성 식별자(`web.root` · `web.loading` · `web.failure`)는 두지 않았다.
- 상태가 없는 응답 판정(iOS `statusCode == nil`)은 Android가 HTTP 오류(400 이상)만 알려 주어 확인하지 않는다.

## 작업 모듈

| 모듈                  | 계층      | 변경 내용                                                     |
| --------------------- | --------- | ------------------------------------------------------------- |
| feature/web/interface | interface | `WebPage` · `LegalDocumentUrls`                               |
| feature/web/sources   | sources   | `WebScreen` · `WebViewModel` · `WebDocumentView` · 이동 정책 |

## 영향 범위

| 모듈         | 계층    | 영향 이유                            |
| ------------ | ------- | ------------------------------------ |
| feature/auth | feature | 약관 상세를 `AuthRoute.LegalDocument`로 연다. 주소가 없으면 '보기'를 무시 |
| feature/profile | feature | 설정 홈 문서 4종. 주소가 없으면 "문서를 열 수 없어요" 팝업 |
| app          | app     | `webFeatureModule` · 약관 상세 슬롯 |

## 미해결 질문

(없음)

## 변경 이력

- P4b 신설: 문서 범위 웹 화면(로딩 · 실패 · 다시 시도)
- 다시 시도 경합: 콜백을 지금 `loadId`가 아니라 시도를 시작한 `loadId`에 묶는다(시도마다 웹뷰 새로 만듦)
