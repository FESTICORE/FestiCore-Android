# 기기 알림 설정 (S13)

기존 설치 UUID와 SecureStorage를 재사용한다. `DeviceNotificationSettingsService`는 계정 Bearer가 아닌 기기 전용 인증으로 등록·설정 조회·저장을 수행한다. 앱 시작만으로 호출하지 않으며 설정 화면 진입에서 조회한다.

- 최초 등록 응답의 deviceId·deviceAccessToken을 검증하고 암호화 저장한다. 응답 유실 뒤 같은 설치 ID를 임의 회전하지 않는다.
- 최초 등록은 화면 취소와 분리하고 60초 제한을 둔다. 인증 실패는 반복 요청 대신 오류 상태로 남긴다.
- 설정 조회/저장은 mutex로 직렬화한다. 401이면 기존 기기 인증으로 재등록한 뒤 한 번 재시도한다. 불완전한 저장 응답은 GET으로 재확인한다.
- flagStatus·crewStatus·crewGather·setReminders·homecoming·packUpdates는 필수 Boolean이며 officialNews는 서버가 제공할 때만 노출한다.
- 토큰·설치 식별자·응답 본문을 로그/분석에 넣지 않는다. 저장값 문자열 표현은 redacted다.
- 이번 Android 구현은 FCM 수신을 지원하지 않는다. 화면에서 알림 설정만 저장함을 안내하고 시스템 설정 링크를 제공한다. 실제 알림 전달을 약속하지 않는다.

검증: `DeviceNotificationSettingsServiceTest`, `DeviceRequestTest`, `NotificationSettingsUseCaseTest`, `NotificationSettingsViewModelTest`, `NotificationSettingsDesignParityTest`. 실제 서버 변경 호출은 검증에 사용하지 않는다.
