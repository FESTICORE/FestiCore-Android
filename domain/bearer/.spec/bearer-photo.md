---
spec: bearer-photo
modules: [domain/bearer, service/api, service/apiendpoint, core/network]
---

# 기수 깃발 사진 Spec

> 기수가 고른 깃발 사진을 서버 규격 JPEG로 다시 인코딩해(EXIF · GPS 제거) 진행률을 알리며 올린다.

iOS 대응: `Projects/Domain/BearerDomain/Sources/Photo/BearerPhotoEncoder.swift` · `BearerUseCaseImpl.uploadPhoto` · `BearerRepositoryImpl.uploadPhoto`, 요청 `Projects/Service/APIEndpoint/Sources/Flag/FlagPhotoUploadRequest.swift`.
설계: `.claude/docs/specs/2026-11-24-bearer-android-design.md`("사진 업로드" 절 · PR BD2 · 쟁점 B2 · B5).

## 개요

화면은 `BearerUseCase.uploadPhoto(flagId, imageData)`만 부르고 `Flow<BearerPhotoUploadEvent>`(`Progress(0~1)` 여러 번 → `Completed(BearerFlagPhoto)`)를 받는다. 실패는 `BearerError`로 Flow가 끝난다. 인코딩은 `BearerPhotoEncoder`(domain/bearer/sources `photo/`), 전송은 `FlagRequest.uploadPhoto` → `core/network/interface` `festiCoreUpload`.

## 동작 규칙

1. 인코딩
   - 크기를 못 읽거나 이미지가 아니면 `UnsupportedPhoto`. 짧은 변 < 256px이면 `PhotoTooSmall`(올리지 않는다).
   - 긴 변 = `min(긴 변, max(2048, ceil(256 × 긴 변 / 짧은 변)))`, 짧은 변은 비율대로 올림. 2의 거듭제곱 샘플링으로 줄여 디코딩한 뒤 정확한 크기로 맞춘다.
   - EXIF 방향은 픽셀에 굽는다. API 28+ `ImageDecoder`(자동 적용), 26-27 `BitmapFactory` + 플랫폼 `android.media.ExifInterface` 회전 · 뒤집기.
   - JPEG 품질 85 · 75 · 65 · 55 · 45 · 35 순으로 5MB 이하가 되는 첫 결과. 모두 넘으면 `PhotoTooLarge`.
   - 픽셀만 `Bitmap.compress`로 새로 쓴다. 결과에 EXIF(APP1)가 없어 위치 · 기기 · 시각이 나가지 않는다. 원본 바이트는 절대 그대로 올리지 않는다.
   - 사진 바이트 · 파일 이름은 로그 · 분석에 넣지 않는다.
2. 전송
   - `POST api/v1/flags/{id}/photo`, multipart 파트 하나 `photo`(`photo.jpg`, `image/jpeg`), 세션 Bearer, 재시도 0.
   - 요청 단위 제한 시간 60초(`HttpTimeout`은 기본값 없이 설치해 다른 요청은 엔진 기본 그대로).
   - 진행률은 보낸 바이트 / 전체 길이. UseCase가 0~1로 자른다.
   - 응답 `{photoUrl, thumbUrl}`은 다른 사진 주소와 같은 규칙으로 절대 주소로 바꾼다.
   - 실패: 413 `PhotoTooLarge`, 415 `UnsupportedPhoto`, 연결 실패 · 시간 초과 `Offline`, 세션 없음 `NotAuthenticated`(보내지 않음), 나머지는 `bearer-domain.md` 오류 표.

## 엣지 케이스

- API 26-27은 HEIF를 디코딩하지 못해 HEIC 사진이 `UnsupportedPhoto`다.
- 투명 PNG는 JPEG로 바꾸며 투명 부분이 검게 된다(iOS와 같음).

## 작업 모듈

`domain/bearer/{interface,sources}` · `service/api`(`FlagApi.photo`) · `service/apiendpoint`(`FlagRequest.uploadPhoto`) · `core/network/{interface,sources}`(`festiCoreUpload` · `HttpTimeout`).

## 테스트

- iOS 같은 이름: `uploadPhoto`(BearerDomainTest) · `uploadsWithProgress` · `rejectsSmallPhoto` · `tooSmall` · `downscales` · `notImage`(BearerPhotoTest).
- Android 추가: `stripsExifAndLocation` · `appliesExifOrientation`(두 디코딩 경로 모두) · `sizePlan` · `uploadPhotoFailures` · `FestiCoreUploadTest` 4건. Robolectric 네이티브 그래픽(SDK 35)으로 실제 디코딩 · 인코딩한다.

## 변경 이력

- #216 사진 인코딩 · 업로드를 옮겼다
