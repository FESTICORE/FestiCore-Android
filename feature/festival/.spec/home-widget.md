# Android 홈 위젯

Android RemoteViews/provider/pin API를 사용한다. iOS 잠금화면·Live Activity 지원과 동일하다고 표기하지 않는다.

- 홈 위젯은 설치 지원 여부·요청 중·실제 설치 여부를 구분한다. 요청 성공을 설치 완료로 저장하지 않고 AppWidgetManager의 설치 상태를 다시 읽는다.
- 로컬 암호화 snapshot에 공개 팩과 현재 세션의 일정·대표 깃발·크루 요약을 저장한다. 요청 세션·generation/sequence 검사를 통과한 결과만 반영한다.
- 로그아웃·세션 만료·계정 전환은 개인 projection을 무효화한다. 저장 오류 시 개인 데이터 읽기를 먼저 차단한다.
- 모든 render trigger는 하나의 mutex에서 최신 snapshot을 재확인한다. 표시 오류는 앱 작업을 실패시키지 않는다.
- 위젯 클릭은 기존 딥링크로 선택 축제 또는 목록을 연다. 크기 변경과 설치 이벤트는 다시 렌더한다.
- 기존 폰트·semantic 토큰을 사용한다. Android 26–30은 31 이상의 동적 radius API와 동일 표현을 보장하지 않는다.

검증: WidgetSnapshotStoreTest, FestivalWidgetRepositoryTest, FestivalWidgetUpdaterTest, FestivalWidgetRendererTest, FestivalWidgetNavigationTest, HomeWidgetSettingsDesignParityTest. 실제 런처 호환성과 시스템 갱신은 로컬 렌더 테스트와 별도다.
