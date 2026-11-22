# 백엔드 Android(FCM) 푸시 지원 설계

- 작성: 2026-11-22 · 대상 저장소: FestiCore-Backend (NestJS · TypeORM · Postgres · Railway)
- 근거 기준: 백엔드 `develop` 브랜치(8dc5279). 행 번호는 모두 `git show develop:<path>` 기준이다.
- 요구 출처: Android `.claude/docs/specs/2026-11-22-push-device-android-design.md`(이하 ADS)의 P1 · P3 · setReminders 질문(ADS:234, 248, 250, 262).
- 범위: 설계만 다룬다. 코드 · DB · 배포는 바꾸지 않았다.

## 0. 문서 위치

백엔드 규칙에서 설계 문서 전용 폴더는 없다. 엔드포인트 계약은 `.spec/<도메인>.md`(ARCHITECTURE.md:36, 41 · `.claude/rules/modules.md:10, 25`), 기능 명세는 `docs/api/*.spec.md`, 배포 변수는 `docs/deployment/RAILWAY.md`에 둔다. 그래서 본문은 이 파일에 두고, 구현 PR에서 아래 파일을 함께 고치기로 한다.

| 백엔드 파일                                    | 고칠 내용                                            | PR  |
| ---------------------------------------------- | ---------------------------------------------------- | --- |
| `.spec/devices.md`                             | `platform` · `fcmToken` 계약, Android 본문 예시      | PR1 |
| `.spec/notifications.md`                       | "APNs 토큰" 조건을 "푸시 토큰"으로, FCM data 키 규칙 | PR3 |
| `docs/deployment/RAILWAY.md` 3절               | `FCM_SERVICE_ACCOUNT_JSON` 변수                      | PR2 |
| (선택) `docs/api/android-push-payload.spec.md` | 종류별 FCM data 키 표(4절)                           | PR3 |

## 1. 현재 구조 (develop)

### 기기 등록

- `PUT /api/v1/devices`(device.controller.ts:68-124). 첫 등록은 Bearer 없이, 이후 기기 토큰 또는 계정 JWT(`.spec/devices.md:3, 9`).
- `RegisterDeviceDto`(device.dto.ts:17-63): `apnsEnvironment`는 `@IsIn(['sandbox','production'])` 필수(19-23). `apnsToken`은 키가 반드시 있어야 하고 값은 짝수 길이 hex 또는 `null`(30-33, 테스트 device.dto.spec.ts:23-26). `liveActivityPushToStartToken` 선택(35-42). `appVersion` · `osVersion` · `locale` · `timezone` 필수(44-62).
- 전역 `ValidationPipe`가 `whitelist` · `forbidNonWhitelisted: true`(main.ts:59-63)다. **지금 Android가 `platform` · `fcmToken`을 보내면 400이다.** 반대로 새 필드를 선택으로 더하면 iOS 요청은 그대로 통과한다.
- `DeviceService.register`(device.service.ts:59-151): `apnsEnvironment`를 항상 쓰고(79), `apnsToken`은 키가 있으면 쓴다(71). 응답은 `deviceId` · `userId` · (익명이면) `deviceAccessToken` · `subscriptions`(129-150).
- `GET /devices`는 `apnsEnvironment`를 돌려준다(device.service.ts:153-164).
- 토큰을 지우는 곳: 연결 해제 · 복구(device.service.ts:225-235, 280-290), 로그아웃(auth.service.ts:137-146), 탈퇴는 기기 행 삭제(auth.service.ts:469).
- 엔티티 `devices`(device.entity.ts:9-81): `apnsToken varchar(256) select:false`(20-21), `apnsEnvironment varchar(10)`(23-24), 알림 설정 boolean 7개(54-74). DB 제약 `CHK_devices_apnsEnvironment`(migration 1792500018000:10-11).

### 발송

- `ApnsService`(apns.service.ts) 하나가 모든 발송을 한다. HTTP/2 직접 호출 + `jose` JWT(9, 434-465). 설정 4개(`APNS_BUNDLE_ID` · `TEAM_ID` · `KEY_ID` · `PRIVATE_KEY`, 54-61). 결과 `{accepted, permanent, reason}`(12-17). 410 또는 400 + `BadDeviceToken`·`DeviceTokenNotForTopic`·`ExpiredToken`·`Unregistered`면 `permanent`(40-45, 529-535).
- 종류별 본문(최상위 userInfo 키):

| 메서드                                                                            | 키(aps 밖)                                                                                                                                                             | 우선순위 · 만료              |
| --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| `sendStartNotification`(63-90) — `festival.start` · `festival.official_news`      | `type` · `festivalId` · `festivalName` · `imageUrl` · `eventKey`                                                                                                       | alert 10                     |
| `sendReminderNotification`(92-120) — `festival.reminder`                          | `type` · `route` · `festivalId` · `festivalName` · `imageUrl` · `eventKey`                                                                                             | alert 10                     |
| `sendSetReminderNotification`(123-164) — `festival.set_reminder`                  | `type` · `festivalId` · `setId` · `deeplink` · `festivalName` · `imageUrl` · `eventKey`                                                                                | alert 10, 공연 시작까지 만료 |
| `sendPackUpdated`(166-188) — `pack.updated`(무음)                                 | `type` · `festivalId` · `packVersion`(숫자)                                                                                                                            | background 5, 24시간         |
| `sendSocialNotification`(282-317) — `flag.status` · `crew.status` · `crew.gather` | `type` · `route` · payload 펼침(`flagId` · `status` · `zoneId` · `activity` · `actorUserId` / `crewId` · `nickname` · `state` / `gatherId` / `dissolved`) · `eventKey` | gather 10/15분, 그 외 5/30분 |
| `sendTestNotification`(319-348)                                                   | `type` · `route`                                                                                                                                                       | 관리자 테스트                |
| `sendLiveActivity*`(190-280)                                                      | iOS 전용                                                                                                                                                               | —                            |

title · body는 `aps.alert` 안에 있다(79, 108, 141, 298). Android data 메시지에는 최상위로 꺼내야 한다.

- 발송 경로와 대상 조건(모두 `d."apnsToken" IS NOT NULL AND d."apnsEnvironment" IS NOT NULL`):

| 경로                             | 예약(enqueue)                                                                  | 발송 직전 확인                    | 거절(permanent) 처리                           |
| -------------------------------- | ------------------------------------------------------------------------------ | --------------------------------- | ---------------------------------------------- |
| D-1 리마인더                     | festival-start.service.ts:271-287                                              | 697-708                           | 작업만 failed(1195-1196)                       |
| 축제 시작                        | festival-start.service.ts:293-310                                              | 754-763                           | 같음                                           |
| 저장한 공연 알림(setReminders)   | festival-start.service.ts:511-549(532-533)                                     | 920-933                           | 같음                                           |
| 공식 소식                        | official-news.service.ts:359-386(372)                                          | festival-start.service.ts:831-841 | 같음                                           |
| 팩 업데이트                      | admin-festival.service.ts:933-950(946)                                         | festival-start.service.ts:674-686 | 같음                                           |
| 크루 모여                        | social-notification.service.ts:222-236(228)                                    | 414-443                           | **토큰 · 환경 NULL**(457-463)                  |
| 크루 해산                        | social-notification.service.ts:264-280(271)                                    | 같음                              | 같음                                           |
| 깃발 · 크루 상태                 | social-notification.service.ts:328-333                                         | 같음                              | 같음                                           |
| 관리자 테스트                    | —                                                                              | admin-push.service.ts:24-38       | 없음                                           |
| Live Activity 시작 · 갱신 · 종료 | festival-start.service.ts:318-343, 399-419, 558-585 · live-activity.service.ts | 1011-1027 · 483-491               | Live Activity 토큰만 NULL(1125-1130 · 591-597) |

- 스케줄러 게이트: `FestivalStartService.tick`은 APNs 설정이 없으면 예약만 하고 발송을 건너뛴다(festival-start.service.ts:218-227). `SocialNotificationService.tick`은 아예 돌지 않는다(375). Live Activity도 같다(live-activity.service.ts:359).
- 설정 없음 · 설정 오류(`APNS_NOT_CONFIGURED` · `APNS_CONFIGURATION_ERROR`)는 시도 횟수를 늘리지 않고 15분 뒤 재시도한다(festival-start.service.ts:1190-1194, 1217-1238).
- 알림함: 팩 업데이트 · 소셜은 예약할 때 토큰과 무관하게 기록한다(admin-festival.service.ts:951-962, social-notification.service.ts:99-116 등). 축제 시작 · D-1 · 저장한 공연 · 공식 소식은 **발송 작업 안에서** 기록한다(festival-start.service.ts:725, 774, 844, 957). 그래서 지금은 APNs 토큰이 없는 기기(Android 포함)의 로그인 사용자에게 이 4종은 알림함에도 남지 않는다.
- `firebase-admin`은 의존성에 없다(package.json, `jose`만 52행).

### setReminders 시점 (ADS:262 질문의 답)

**10분 전이다.** `SET_REMINDER_LEAD_MS = 10 * 60_000`(festival-start.service.ts:116-117), 본문 "10분 후 시작돼요"(172-176), 명세 `.spec/notifications.md:11-16` · `.spec/devices.md:34, 37`. "2시간 전"은 Live Activity 원격 시작(`PERFORMANCE_LEAD_MS`, festival-start.service.ts:114-115)이다. Swagger 설명만 옛 문구로 남아 있다: device.dto.ts:129 "저장한 공연 2시간 전 알림", device.controller.ts:233 "setReminders는 저장한 공연 2시간 전 알림입니다". PR1에서 Swagger 문구를 10분으로 고친다(설명 문자열만, 동작 변화 없음).

## 2. 기기 등록 계약 확장 (PR1)

### 요청

| 필드                                               | iOS(현재와 같음)            | Android                                     |
| -------------------------------------------------- | --------------------------- | ------------------------------------------- |
| `platform`                                         | 생략(= `ios`) 또는 `"ios"`  | `"android"` 필수                            |
| `apnsEnvironment`                                  | 필수 `sandbox`·`production` | 생략 또는 `null`                            |
| `apnsToken`                                        | 키 필수, hex 또는 `null`    | 생략 또는 `null`                            |
| `liveActivityPushToStartToken`                     | 선택                        | 생략 또는 `null`                            |
| `fcmToken`                                         | 보내면 400                  | 키 필수, 문자열 또는 `null`(알림 권한 없음) |
| `appVersion` · `osVersion` · `locale` · `timezone` | 필수(현재 규칙)             | 필수(같은 규칙)                             |

Android 예시:

```json
{
  "platform": "android",
  "fcmToken": "<FCM 등록 토큰>",
  "appVersion": "1.0.0",
  "osVersion": "15",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul"
}
```

### 검증 규칙 (class-validator)

- `platform`: `@IsOptional() @IsIn(['ios','android'])`. 생략은 `ios`로 본다. 그래서 지금 iOS 앱 요청은 바이트 하나 바뀌지 않고 통과한다.
- `apnsEnvironment` · `apnsToken`: 지금 데코레이터 앞에 `@ValidateIf(o => o.platform !== 'android')`를 단다. iOS 규칙(필수 · hex)은 그대로다. Android에서 값이 `null`이 아니면 400으로 막는다(FCM 토큰을 APNs로 보내는 사고 방지, ADS:248 (b)).
- `fcmToken`: `platform === 'android'`일 때 `@IsDefined()`(키 필수, `apnsToken`과 같은 의미) + `null`이 아니면 `@IsString() @MinLength(1) @MaxLength(4096)` + 공백 금지 정규식. iOS에서는 생략 또는 null만 허용하고 문자열 값이면 400(R2 결정).
- `liveActivityPushToStartToken`: Android에서 `null`이 아니면 400.
- 기존 기기의 `platform`이 요청과 다르면 거절한다(쟁점 9-d). 생략한 iOS 요청은 `ios`로 보므로 기존 iOS 행과 늘 같다.

### 서비스

- `register`: Android면 `apnsToken` · `apnsEnvironment` · `liveActivityPushToStartToken`을 `null`로, `fcmToken`을 요청 값으로 쓴다. iOS면 `fcmToken`을 건드리지 않는다(지금 `values` 구성 device.service.ts:70-80에 분기만 더한다).
- `get`: 응답에 `platform`을 더한다(추가 필드). iOS는 Swift `Decodable`로 받는다(iOS `NetworkDeviceRegistrationService.swift:317-324`). 합성 `Decodable`은 모르는 키를 무시하지만 커스텀 `init(from:)` 여부는 확인하지 않았다(10절). 위험을 없애려면 `get`에 더하지 않아도 Android 동작에는 지장이 없다.
- `fcmToken`을 지우는 곳을 기존 APNs 토큰과 같이 맞춘다: 연결 해제 · 복구(device.service.ts:228-234, 283-289), 로그아웃(auth.service.ts:140-145). 탈퇴는 행 삭제라 그대로다.
- 응답 `register`의 모양은 바꾸지 않는다(129-150).

## 3. DB 변경 (PR1)

### 엔티티 (device.entity.ts)

```ts
@Column({ type: 'varchar', length: 10, default: 'ios' })
platform!: 'ios' | 'android';

@Column({ type: 'varchar', length: 4096, nullable: true, select: false })
fcmToken!: string | null;
```

`select: false`는 `apnsToken`과 같다(20). 토큰은 로그 · 응답에 넣지 않는다(modules.md:19).

### 마이그레이션

- 파일: `src/database/migrations/1792500111000-device-platform.ts`, 클래스 `DevicePlatform1792500111000`, `name = 'DevicePlatform1792500111000'`. 규칙: 13자리 타임스탬프, 클래스 이름이 타임스탬프로 끝나고 `name`과 같고, develop 마지막(`1792500110000-official-instagram-news.ts`)보다 뒤(database.md:14). PR을 만들 때 develop 마지막 번호를 다시 본다. 현재 브랜치 feat/384에는 마이그레이션이 없다(`git diff --name-only develop...HEAD`).
- up:

```sql
ALTER TABLE "devices" ADD COLUMN IF NOT EXISTS "platform" varchar(10) NOT NULL DEFAULT 'ios';
ALTER TABLE "devices" ADD COLUMN IF NOT EXISTS "fcmToken" varchar(4096);
ALTER TABLE "devices" ADD CONSTRAINT "CHK_devices_platform"
  CHECK ("platform" IN ('ios', 'android'));
ALTER TABLE "devices" ADD CONSTRAINT "CHK_devices_push_token_platform"
  CHECK (("platform" = 'android' OR "fcmToken" IS NULL)
     AND ("platform" = 'ios' OR ("apnsToken" IS NULL AND "apnsEnvironment" IS NULL
          AND "liveActivityPushToStartToken" IS NULL)));
```

기존 행은 모두 `platform = 'ios'`, `fcmToken = NULL`이 되므로 두 제약을 바로 만족한다. 기존 마이그레이션도 `ADD COLUMN IF NOT EXISTS ... DEFAULT`를 쓴다(1792500110000:11-12, 1792500107000:8-10).

- down: 두 제약과 두 열을 지운다. Android 행은 `apnsToken` 등이 NULL인 행으로 남아 어떤 발송에도 걸리지 않는다(데이터 손실은 Android 토큰뿐이고 앱이 재등록하면 돌아온다).
- 검증: 로컬 Postgres에서 `npm run migration:run` 뒤 `migration:generate ... --dryrun --check`(harness.md:75), up → down → up.

### 무중단 순서

- 운영 · 개발 배포는 부팅 때 `migrationsRun`으로 자동 적용된다(typeorm.config.ts:24-28, database.md:13). 머지 = 해당 환경 DB 적용이다.
- 이 마이그레이션은 열 추가(기본값 있음) + 기존 행을 만족하는 CHECK라서 옛 코드와 함께 돌아도 안전하다. 옛 코드의 INSERT는 `platform`을 빼므로 기본값 `ios`가 들어간다. Postgres 11 이상에서 상수 기본값 열 추가는 테이블을 다시 쓰지 않는다(Railway 이미지 postgres-ssl:18, RAILWAY.md:20 · CI postgres:16, backend-ci.yml:21).
- 순서: PR1(열 · 계약) 배포 → PR2(발송기) 배포 → PR3(대상 조건 전환). PR1만 배포된 상태에서는 Android 기기가 등록되지만 모든 예약 SQL이 아직 APNs 조건이라 아무것도 보내지 않는다.

## 4. FCM 발송 (PR2 · PR3)

### 라이브러리 · 자격 증명

- `firebase-admin`: npm 메타데이터 `license: Apache-2.0`, 최신 14.5.1, `engines.node >=22`(`npm view firebase-admin`). Docker 이미지 node:24(Dockerfile:2, 13) · CI node 22(backend-ci.yml:38)와 맞는다. `@google-cloud/storage` · `firestore`는 optionalDependencies라 messaging만 쓰면 불필요하다. 서브패스 `firebase-admin/app` · `firebase-admin/messaging`만 import한다.
- HTTP v1: `getMessaging(app).send(message)`가 FCM HTTP v1으로 보낸다(레거시 서버 키 아님).
- 자격 증명: 환경 변수 `FCM_SERVICE_ACCOUNT_JSON`(서비스 계정 JSON 한 줄)만 받는다. `ConfigService`로 읽는다(ARCHITECTURE.md:34). 파일 경로 · 저장소 파일 · `GOOGLE_APPLICATION_CREDENTIALS`는 쓰지 않는다(`credentials*.json` 스테이징 금지 harness.md:33, 비밀 값 검사 81-85).
- 없거나 파싱 실패면 `FcmService.isConfigured() === false` → **Android 발송만 꺼진다.** APNs 경로는 그대로다. 앱 초기화는 첫 사용 때 한 번(이름 있는 앱 `initializeApp({ credential: cert(json) }, 'festicore-fcm')`), 실패해도 부팅을 막지 않는다. 로그에 JSON · 키를 남기지 않는다.

### 발송 구조 (플랫폼별 sender)

규칙상 미리 인터페이스를 만들지 않는다(ARCHITECTURE.md:3, 26 · modules.md:15). 그래서 구체 클래스 3개로 둔다.

```
NotificationModule
  ApnsService   (기존, 출력 불변)   ── build*(): { body, options } + send(token, env, built)
  FcmService    (신규)              ── send(fcmToken, built): ApnsSendResult와 같은 결과형
  PushService   (신규, 분배)        ── sendX(target: PushTarget, input) → platform으로 분기
PushTarget = { platform, apnsToken, apnsEnvironment, fcmToken }
```

- 페이로드 원천은 하나다. `ApnsService`의 각 `sendX`를 "본문 만들기(`buildX`) + 보내기"로 나눈다. APNs 본문 · 헤더는 바뀌지 않는다(기존 apns.service.spec.ts가 그대로 통과해야 한다).
- `FcmService`는 같은 `{ body, options }`를 FCM 메시지로 바꾼다. 그래서 키가 iOS userInfo와 어긋날 수 없다(P3 (a), ADS:250).
- 결과형은 `ApnsSendResult`(apns.service.ts:12-17)를 그대로 쓴다(`PushSendResult`로 이름만 넓혀도 된다). 호출하는 서비스의 재시도 · 종료 코드는 그대로 동작한다.

### data 전용 메시지 변환 규칙

```ts
{
  token: fcmToken,
  data: { ...flatten(body 중 aps 밖 키), title, body },   // aps.alert가 있을 때만 title·body
  android: {
    priority: options.pushType === 'background' ? 'normal' : 'high',
    ttl: options.expirationSeconds * 1000,            // firebase-admin은 ms를 받는다
    ...(options.pushType === 'background' ? { collapseKey: options.collapseKey } : {}),
  },
}
```

- `notification` 키를 넣지 않는다. 백그라운드에서도 `onMessageReceived`가 불려 iOS NSE와 같은 가공을 한다(ADS:140-143).
- FCM data 값은 문자열만 허용된다(firebase-admin `data: { [key]: string }`). 변환: 숫자 · boolean → `String()`(`packVersion: "3"`, `dissolved: "true"`), `null`/`undefined` → 키 생략(예: `zoneId`), 객체는 없다(현 본문 기준). Android는 숫자 키를 문자열로 받아 파싱한다.
- 크기: 지금 APNs와 같이 4096바이트 넘으면 보내지 않고 `PayloadTooLarge`(apns.service.ts:374-377).
- 무음(`pack.updated`): `title`·`body` 없음 → Android가 무음으로 구분한다(ADS:149). 구분 키는 `type`이다.
- 종류별 data 키(위 1절 표 + `title` · `body`): `festival.start` · `festival.official_news` · `festival.reminder` · `festival.set_reminder` · `flag.status` · `crew.status` · `crew.gather` · `pack.updated` · 관리자 테스트. Live Activity는 Android에 보내지 않는다.

### 무효 토큰 처리

| FCM 결과                                                                    | 의미                                            | 처리                                                                                                                                                                             |
| --------------------------------------------------------------------------- | ----------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 성공(메시지 ID)                                                             | 수락                                            | `accepted: true`                                                                                                                                                                 |
| `UNREGISTERED`(HTTP 404, SDK `messaging/registration-token-not-registered`) | 토큰 폐기                                       | `permanent: true` → `UPDATE devices SET "fcmToken" = NULL WHERE "id" = $1 AND "fcmToken" = $2` (APNs 410과 같은 의미, social-notification.service.ts:457-462와 같은 조건부 갱신) |
| `messaging/invalid-registration-token`                                      | 토큰 형식 잘못                                  | 위와 같다                                                                                                                                                                        |
| `SENDER_ID_MISMATCH`(`messaging/mismatched-credential`)                     | 다른 Firebase 프로젝트 토큰 또는 환경 변수 혼동 | **토큰을 지우지 않는다.** 설정 오류(`FCM_CONFIGURATION_ERROR`)로 보고 15분 재시도, 경고 로그                                                                                     |
| `INVALID_ARGUMENT`(페이로드)                                                | 서버 버그                                       | 영구 실패(작업 failed), 토큰 유지                                                                                                                                                |
| `UNAVAILABLE` · `INTERNAL` · `QUOTA_EXCEEDED` · 네트워크                    | 일시 장애                                       | 재시도. `FCM_HTTP_5xx` 계열은 APNs 5xx처럼 15분(festival-start.service.ts:1220)                                                                                                  |
| 설정 없음                                                                   | Android 꺼짐                                    | `FCM_NOT_CONFIGURED` → `APNS_NOT_CONFIGURED`와 같게 시도 횟수 없이 15분 재시도(1190-1194, 1217-1238)                                                                             |

- 축제 계열(`FestivalStartService.sendDelivery`)은 지금 APNs `permanent`에서 토큰을 지우지 않는다(1195-1196). FCM은 모든 경로에서 지운다(요구 "APNs 410 처리와 같은 의미"는 소셜 경로 기준). APNs 쪽 정리는 쟁점 9-j.

## 5. 모든 기존 발송 경로가 Android에 가는지 (PR3)

- 대상 조건을 한 곳에 둔다:

```ts
export const PUSH_TARGET_SQL = `((d."platform" = 'ios' AND d."apnsToken" IS NOT NULL AND d."apnsEnvironment" IS NOT NULL)
  OR (d."platform" = 'android' AND d."fcmToken" IS NOT NULL))`;
```

- 바꿀 곳(1절 표의 모든 예약 · 발송 확인): festival-start.service.ts:278, 301, 532 · official-news.service.ts:372 · admin-festival.service.ts:946 · social-notification.service.ts:228, 271, 332, 424. SELECT에 `d."platform", d."fcmToken"`을 더한다(637-638, 808, 895, 415). 발송 확인의 `!target?.apnsToken || !target.apnsEnvironment`(679-680, 700-701, 755-756, 832-833, 921-922, social 440)를 `hasPushTarget(target)`로 바꾸고, `this.apns.sendX(...)`를 `this.push.sendX(target, ...)`로 바꾼다.
- 게이트: festival-start.service.ts:219, social-notification.service.ts:375, admin-push.service.ts:24의 `apns.isConfigured()`를 `push.isConfigured()`(APNs 또는 FCM 하나라도)로 바꾼다. APNs만 설정된 지금 운영은 동작이 같다. Live Activity(live-activity.service.ts:359, festival-start.service.ts:326-327, 408-409, 566-567, 1012-1013)는 iOS 전용이라 그대로 둔다. Android는 `liveActivityPushToStartToken`이 늘 NULL(3절 제약)이라 걸리지 않는다.
- 결과:

| 종류                                           | Android 대상 | 설정 · 구독 조건                                            |
| ---------------------------------------------- | ------------ | ----------------------------------------------------------- |
| setReminders(`festival.set_reminder`, 10분 전) | 예           | `notifySetReminders` + 로그인 세션(532-539, 920-925) 그대로 |
| packUpdates(`pack.updated`, 무음)              | 예           | `notifyPackUpdates` + 구독 `packUpdates`(944-945)           |
| 축제 시작 · D-1 · 공식 소식                    | 예           | 핀 · 구독 · `notifyOfficialNews` 그대로                     |
| 깃발 · 크루 상태 · 크루 모여 · 해산            | 예           | `notify*` · `pushOptIn` 그대로(418-422)                     |
| 관리자 테스트                                  | 예           | `admin-push.service.ts` 대상 확인을 `PushTarget`으로        |
| Live Activity                                  | 아니오       | iOS 전용                                                    |

- 알림함 연계: 발송 작업 안에서 기록하는 4종(1절)은 PR3 뒤 Android 로그인 사용자에게도 남는다. 예약 때 기록하는 팩 · 소셜은 이미 플랫폼과 무관하다.
- 알림 설정 API(`GET`·`PUT /devices/notification-settings`, device.controller.ts:211-250)는 플랫폼과 무관해 바꾸지 않는다.

## 6. 테스트

| 층           | 대상                                                                     | 내용                                                                                                                                                                                                                                         |
| ------------ | ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 단위         | `device.dto.spec.ts`                                                     | 기존 iOS 사례 그대로 통과 + Android: `fcmToken` 키 필수 · null 허용 · 길이, Android에 non-null APNs 필드 400, iOS에 `fcmToken` 문자열 400 · null 200, `platform` 생략 = ios                                                                                    |
| 단위         | `device.service.spec.ts`                                                 | Android 등록이 APNs 필드를 NULL로 쓰는지, 플랫폼 변경 거절, 연결 해제 · 로그아웃이 `fcmToken`도 지우는지                                                                                                                                     |
| 단위         | `apns.service.spec.ts`                                                   | **수정 없이 통과**(iOS 본문 불변 증거)                                                                                                                                                                                                       |
| 단위         | `fcm.service.spec.ts`(신규)                                              | `jest.mock('firebase-admin/messaging')`. 종류별 data 키 집합 = APNs userInfo 키 + title·body, 값 문자열, null 생략, 우선순위 · ttl(ms) · collapseKey, 오류 코드 → 결과 매핑, 설정 없음 → `FCM_NOT_CONFIGURED`                                |
| 단위         | `push.service.spec.ts`(신규)                                             | 플랫폼 분기, 한쪽만 설정됐을 때                                                                                                                                                                                                              |
| 단위         | `festival-start.service.spec.ts` · `social-notification.service.spec.ts` | 기존처럼 `jest.mock('./apns.service', ...)`(festival-start.service.spec.ts:3) + 가짜 `PushService`. Android 대상 → 가짜 FCM sender 호출, permanent → `fcmToken` NULL 갱신                                                                    |
| Postgres     | `set-reminder.postgres.spec.ts` 등                                       | `LIVE_ACTIVITY_PG_URL` 있을 때만 실행(set-reminder.postgres.spec.ts:10-11). 이 테스트는 자체 `devices` 테이블을 만든다(85, 118) → `platform` · `fcmToken` 열 추가, Android 기기 예약 사례                                                    |
| 마이그레이션 | `1792500111000-device-platform.spec.ts`(선택, database.md:21)            | 기존 행 기본값 ios, 제약 위반 행 거절                                                                                                                                                                                                        |
| e2e          | `test/devices.e2e-spec.ts`(신규)                                         | 지금 `test/`에 기기 e2e가 없다. `me-notifications.e2e-spec.ts` 방식(실제 `ValidationPipe` · 필터 · 인터셉터, 서비스 목)으로 `main.ts:59-63`과 같은 옵션을 걸고: 현재 iOS 본문(.spec/devices.md:13-22) 200, Android 본문 200, 잘못된 조합 400 |

- FCM은 실제로 호출하지 않는다. Nest 테스트 모듈에서 `FcmService`를 가짜로 바꾼다.
- 검증: `.claude/scripts/verify --e2e < /dev/null`(harness.md:56-58). CI는 develop 푸시와 수동 실행에서만 돈다(backend-ci.yml:3-6). **PR에서는 CI가 돌지 않으므로** 머지 전 로컬 verify(Postgres 포함)가 유일한 게이트다.

## 7. 배포

- develop 머지 → Railway `develop` 환경(`https://festicore-dev.up.railway.app`, "개발/스테이징")에 자동 배포(RAILWAY.md:8-11, 22-23 · railway.toml:27-32) + 부팅 때 마이그레이션 자동 적용. 이 문서의 "stage"는 이 환경이다. 자동 태그 `dev-v*`(auto-tag.yml:3-14).
- main = 운영. main 대상 PR 생성 · 머지는 훅이 막고 사람이 한다(harness.md:28-29).
- 단계별 stage 반영 범위:

| 머지 | stage에서 바뀌는 것                                        | iOS 영향                            |
| ---- | ---------------------------------------------------------- | ----------------------------------- |
| PR1  | 열 2개 · 제약, Android 등록 가능, Swagger 문구             | 없음(생략 = ios)                    |
| PR2  | `FcmService` · 분배기, 관리자 테스트 푸시가 Android 기기로 | APNs 본문 불변                      |
| PR3  | 모든 예약 · 발송이 Android 포함                            | 조건 SQL만 바뀜(iOS 행은 같은 결과) |

- iOS 호환 확인(각 PR stage 배포 뒤):
  1. 현재 스토어 · TestFlight iOS 앱으로 stage에서 첫 등록 → 재등록 → 로그인 연결 → 로그아웃 해제 → 알림 설정 조회 · 저장이 그대로인지.
  2. 관리자 테스트 푸시(`POST /admin/push/test`)를 iOS 기기로 보내 `accepted: true`와 실기기 표시.
  3. stage DB에서 새 iOS 행이 `platform = 'ios'`, `fcmToken IS NULL`인지(조회는 사람이 한다).
  4. PR3 뒤: 저장한 공연 10분 전 · 팩 업데이트 무음이 iOS에 오는지.
- 운영 승격: PR1은 Android 출시와 무관하게 먼저 가도 된다. PR2 · PR3는 운영 `FCM_SERVICE_ACCOUNT_JSON`이 없어도 안전하다(Android만 꺼짐). 다만 그 상태로는 FCM 작업이 15분마다 재시도로 쌓이므로(4절 표 마지막 줄) 운영 변수 등록 뒤 승격을 권한다.

## 8. 사용자 조치

1. Firebase 콘솔: 프로젝트에 Android 앱(prod `io.festiCore.co` · stage `io.festiCore.co.stage`, Android CLAUDE.md 1행 기준)을 등록한다. stage와 prod를 다른 Firebase 프로젝트로 둘지 정한다(쟁점 9-m).
2. Google Cloud 콘솔에서 해당 프로젝트의 "Firebase Cloud Messaging API (V1)"이 켜져 있는지 확인한다.
3. 서비스 계정 키 생성: Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성(JSON). 저장소 · 공유 폴더에 두지 않는다(상위 폴더에 이미 키 파일들이 있다, 커밋 대상이 아닌지 확인). Railway 등록 후 내려받은 파일은 지운다.
4. Railway 변수 등록: `FestiCore-Backend` 서비스 → Variables → `FCM_SERVICE_ACCOUNT_JSON` = JSON 전체(한 줄). `develop` 환경에는 stage 프로젝트 키, `production` 환경에는 prod 프로젝트 키. 환경을 바꾸면 `SENDER_ID_MISMATCH`가 난다.
5. 이슈 번호 생성: PR1~3 각각(커밋 제목 끝 `#이슈번호`, harness.md:31).
6. main 승격 PR은 사람이 만들고 머지한다.

## 9. 쟁점과 추천

| #   | 쟁점                                                                              | 추천                                                                                                                                |
| --- | --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| a   | `platform` 필수 vs 생략 = ios                                                     | 생략 = ios. iOS 앱 무수정 호환                                                                                                      |
| b   | Android의 APNs 필드                                                               | 생략 또는 null만 허용, non-null 400                                                                                                 |
| c   | `fcmToken` 키 필수 여부                                                           | Android에서 키 필수 · null 허용(`apnsToken`과 같은 의미, .spec/devices.md:9)                                                        |
| d   | 기존 기기의 플랫폼 변경                                                           | 거절(403 "이미 등록된 기기", device.service.ts:98, 104와 같은 계열). 새 UUID로 등록                                                 |
| e   | 토큰 열                                                                           | `apnsToken` 재사용 대신 별도 `fcmToken` 열. FCM 토큰이 APNs로 가는 길을 구조적으로 막는다                                           |
| f   | `fcmToken` 길이 · 형식                                                            | `varchar(4096)` · 문자열 · 공백 금지만. FCM 문서에 고정 형식이 없다(10절)                                                           |
| g   | 발송 추상화                                                                       | 인터페이스 없이 `PushService` 분배기 + `FcmService`. 페이로드 원천은 APNs `build*` 하나                                             |
| h   | Android 우선순위                                                                  | 보이는 알림 전부 `high`, 무음 `normal`. iOS의 priority 5 소셜 알림도 Android는 표시 알림이라 `high`가 맞다(ADS:143 "높은 우선순위") |
| i   | `SENDER_ID_MISMATCH` 처리                                                         | 토큰을 지우지 않고 설정 오류로 재시도. 잘못된 키 등록 한 번에 전체 Android 토큰이 지워지는 것을 막는다                              |
| j   | APNs permanent 때 축제 계열이 토큰을 안 지움(festival-start.service.ts:1195-1196) | 이번 범위 밖. 별도 이슈로 소셜과 같게 맞춘다                                                                                        |
| k   | FCM 미설정일 때 작업 예약                                                         | 예약은 하고 발송만 재시도(APNs 미설정과 같은 규칙). 설정 시점 이전 알림도 유효 기간 안이면 나간다                                   |
| l   | Swagger setReminders 문구                                                         | PR1에서 "10분 전"으로 수정                                                                                                          |
| m   | Firebase 프로젝트 분리                                                            | stage · prod 분리 권장(Railway 환경 분리와 맞춤). 하나로 쓰면 stage 서버가 운영 앱에 보낼 수 있다                                   |
| n   | data 값 형식                                                                      | 전부 문자열, null 생략. 숫자(`packVersion`)는 Android가 파싱                                                                        |
| o   | `GET /devices`에 `platform` 추가                                                  | 추가(Android 확인용). iOS 커스텀 디코더가 있으면 빼도 된다                                                                          |

## 10. PR 묶음

| PR            | 브랜치(예)                     | 내용                                                                                                                                        | 검증                                                                                                           |
| ------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| PR1 계약 + DB | `feat/android-device-platform` | 마이그레이션 · 엔티티 · DTO · `register`/`get`/해제/로그아웃 · Swagger 문구 · `.spec/devices.md` · `test/devices.e2e-spec.ts`               | verify --e2e, migration up/down, iOS 호환 1~3                                                                  |
| PR2 발송기    | `feat/fcm-sender`              | `firebase-admin` 의존성 · `FcmService` · `PushService` · `ApnsService` build/send 분리(출력 불변) · 관리자 테스트 푸시 Android · RAILWAY.md | apns.service.spec 무수정 통과, `npm audit --omit=dev --audit-level=high`(backend-ci.yml:49), stage 테스트 푸시 |
| PR3 경로 전환 | `feat/android-push-delivery`   | `PUSH_TARGET_SQL` · 모든 예약/발송/게이트 · FCM permanent → `fcmToken` NULL · `.spec/notifications.md` · Postgres spec                      | verify --e2e(Postgres), stage에서 Android · iOS 실기기                                                         |

- 커밋 제목은 한국어 + `#이슈번호`, `Co-Authored-By` 금지(harness.md:31-32).
- Android 순서와 맞물림: ADS:283 "P0c1 → (서버 P1 확정) → P0b → P0c2 → (P3 확정) → P0d". 서버 PR1이 P1, PR2+PR3가 P3에 해당한다.

## 11. 확인 못 한 것

- firebase-admin 14의 오류 코드 문자열(`messaging/registration-token-not-registered` · `invalid-registration-token` · `mismatched-credential`)과 HTTP v1 상태(UNREGISTERED=404 등)의 정확한 대응. context7에서 타입 · ttl(ms) 검증은 봤지만 오류 매핑 표는 찾지 못했다. 구현 때 SDK 소스로 확인한다.
- FCM 등록 토큰의 최대 길이 · 문자 집합(공식 고정값 미확인). 4096은 여유값이다.
- 서비스 계정에 필요한 최소 IAM 역할(콘솔 기본 키 대신 전용 계정을 만들 경우).
- data 전용 `high` 메시지가 알림을 띄우지 않을 때 FCM이 우선순위를 낮추는 정책의 현재 기준.
- data 메시지에 `collapseKey`를 여러 종류 쓸 때 기기당 보관 개수 제한. 그래서 무음(`pack.updated`)에만 쓰기로 했다.
- iOS `DeviceUserBindingResult` 등 디코더가 커스텀 `init(from:)`인지(추가 키 무시 여부).
- Railway 배포 중 옛 컨테이너와 새 컨테이너가 겹치는지(겹쳐도 3절 마이그레이션은 안전하도록 설계했다).
- stage · 운영 DB의 실제 기기 수와 `apnsToken` 보유 비율(DB 접근 금지).
- Firebase 프로젝트 구성(stage · prod 분리 여부). 앱 ID는 Android CLAUDE.md 기준이며 Gradle 설정과 대조하지 않았다.

## 결정 (호스트, 2026-11-23 — 사용자 지시 "서버 쪽도 건드려서 해")

- 쟁점은 추천안대로. firebase-admin(Apache-2.0)은 사용자 규칙(Apache-2.0/MIT 진행)에 따라 도입한다.
- 백엔드 develop 머지는 stage(iOS와 공유)에 바로 반영되므로, PR마다 머지 전 로컬 `verify --e2e`(Postgres) 통과와 iOS 기존 요청 호환 e2e를 필수로 한다. main(운영) 승격은 사용자가 한다.
- Firebase 서비스 계정 키 · Railway 환경 변수 등록은 사용자 조치다. 키가 없어도 iOS 발송은 그대로 동작한다.
- (Codex R1 PUSH-001 반영) 검증: APNs 필드 · `liveActivityPushToStartToken`에 `@ValidateIf(o => o.platform !== 'android')`만 두면 Android 요청에서 검증이 건너뛰어진다. 대신 기존 iOS 검증 데코레이터는 그대로 두고 조건을 `platform !== 'android'`일 때만 적용하는 분기와 별개로, Android용 클래스 수준 검증(커스텀 `@ValidatorConstraint` `AndroidForbidsApnsFields`)을 DTO에 붙여 `platform === 'android'`이면 `apnsToken` · `apnsEnvironment` · `liveActivityPushToStartToken`이 null/생략이 아닐 때 400(VALIDATION)을 낸다. `fcmToken`은 Android일 때 키 필수 · 값은 null 허용(알림 권한 거부 시, apnsToken과 같은 의미) · 문자열이면 빈 문자열 금지 · 최대 4096, iOS일 때 생략/null만 허용. e2e: Android + 각 APNs 필드 non-null 3건 → 400, Android fcmToken 키 누락 → 400 · null → 200, iOS 기존 요청(platform 생략) → 200 그대로.
- (Codex R2 PUSH-002 반영) iOS의 fcmToken 규칙은 하나로 정한다: **생략 또는 null 허용, 문자열 값이면 400**. 2절 필드 표와 검증 · e2e(iOS + fcmToken:null → 200, iOS + fcmToken:"x" → 400)를 이 규칙으로 맞춘다. 2절 문장과 테스트 표도 이 규칙으로 고쳤다.
- (Codex R3 PUSH-003 반영) PR2 모듈 배선: `PushService` · `FcmService` · `ApnsService`를 새 `PushModule`(providers · exports 셋 다)에 두고, `NotificationModule`과 `AdminPushModule`은 로컬 `ApnsService` provider를 지우고 `PushModule`을 import한다(ApnsService 인스턴스가 하나로 모임). 검증: `Test.createTestingModule({ imports: [AppModule] })`로 앱 전체 DI가 해석되는지 보는 부팅 테스트를 PR2에 추가(외부 접속 없이, APNs · FCM 미설정 상태).
