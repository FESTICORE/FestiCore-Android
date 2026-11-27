# 실제 에뮬레이터 캡처

API 36 arm64, 1080×2400, 360dp. **픽셀 일치 판정이 아닌 실제 렌더 증거**다. 전체 경계는 [보고서](REPORT.md), 디자인 프레임별 범위는 [대응표](COVERAGE.md)에 기록했다.

## S02 변경 시간 잘림 수정 전후

| 모드 | 수정 전 | 수정 후 |
|---|---|---|
| light | ![전](captures/TimetablePreviews-PublishedPreview-light.png) | ![후](captures/TimetablePreviews-PublishedPreview-light-stable.png) |
| dark | ![전](captures/TimetablePreviews-PublishedPreview-dark.png) | ![후](captures/TimetablePreviews-PublishedPreview-dark-stable.png) |

## 실제 앱 탐색

저장된 다크 테마. 로그인 건너뛰기와 공개 축제 목록·검색만 실행했다.

![s14-first-login-dark.png](captures/s14-first-login-dark.png)

![s00-dark.png](captures/s00-dark.png)

![s00-search-recent-stored-dark.png](captures/s00-search-recent-stored-dark.png)

![s00-search-empty-stored-dark.png](captures/s00-search-empty-stored-dark.png)

## 추가 fixture 안정 캡처

S03 기본/내 일정/진행/종료/변경/겹침, S15 깃발/준비물, S02 수정 후 순서. 기존 화면 Content를 가짜 상태로 호출하며 프로덕션 탐색·서버 저장 증거가 아니다.

![light](captures/extra-stable-light.jpg)

![dark](captures/extra-stable-dark.jpg)

## 기존 110개 fixture 수집 목록

전체 220장 축소 보기. 캡처 수가 대응 프레임의 검증 수를 뜻하지 않는다. 시트 단독 내용, 탭바 없는 화면, 부분 스크롤, placeholder를 포함한다. 개별 원본 경로·SHA256·전경 확인은 [수집 JSON](capture-results.json), 추가 18장은 [안정 캡처 JSON](capture-results-stable.json)에 있다. JSON 경로는 로컬 전체 보고서 폴더 기준이다.

![light 1](captures/overview-light-1.jpg)

![light 2](captures/overview-light-2.jpg)

![light 3](captures/overview-light-3.jpg)

![light 4](captures/overview-light-4.jpg)

![light 5](captures/overview-light-5.jpg)

![light 6](captures/overview-light-6.jpg)

![dark 1](captures/overview-dark-1.jpg)

![dark 2](captures/overview-dark-2.jpg)

![dark 3](captures/overview-dark-3.jpg)

![dark 4](captures/overview-dark-4.jpg)

![dark 5](captures/overview-dark-5.jpg)

![dark 6](captures/overview-dark-6.jpg)


## 다크 재확인

전환 중 빈 화면이 있던 항목을 포함한 18개를 6초 대기 후 재촬영하여 위 수집 모음에 교체했다. 단독 시트 내용은 외부 Surface/FCSheet가 없으므로 흰 배경과 인셋을 제품 동작으로 판단하지 않는다. 특히 CrewMenu/Gather, NicknameEdit, BearerZone/ActivityPicker, LoginSheet는 다크 배경·대비 검증에서 제외한다.

![다크 재확인](captures/recheck-dark.jpg)
