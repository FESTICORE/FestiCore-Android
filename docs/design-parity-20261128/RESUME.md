# 디자인 후속 — 큰 글꼴·로그인 후 상태·시트 호스트

기준 PR240 `1500eab`, 표시 수정 `209a3ef`. 서버 쓰기 없이 실제 API36 arm64 에뮬레이터에서 기존 순수 UI 상태를 렌더했다. fixture는 가입·저장·권한·서버 오류 복구의 실서버 증거가 아니다. 이전 238장 및 [261프레임 대응표](COVERAGE.md)를 재사용한다.

## 수정 및 전후 증거

360dp/200%에서 확인한 차이만 수정했다. 기본 글꼴의 색·폰트·반경 토큰은 유지한다.

| 영역 | 원인/수정 | 수정 전 | 수정 후 |
|---|---|---|---|
| S07 내 상태 | 작은 버튼 한 줄 제한을 없애 전체 문구 줄바꿈 | [light](resume/before/CrewGatherReceivedPreview-light-end.png) | [light](resume/final/CrewGatherReceivedPreview-light-end.png), [dark](resume/final/CrewGatherReceivedPreview-dark-end.png) |
| S08 초대 만료 | 고정 행 높이·한 줄 값을 최소 높이·줄바꿈 값으로 변경 | [light](resume/before/CrewJoinPreview-light-end.png) | [light](resume/final/CrewJoinPreview-light-end.png), [dark](resume/final/CrewJoinPreview-dark-end.png) |
| S05 공개 계획 시각 | 48dp 시간 열이 글꼴 크기에 맞게 확장 | [light](resume/before/FlagDetailLoadedPreview-light-end.png) | [light](resume/final/FlagDetailLoadedPreview-light-end.png), [dark](resume/final/FlagDetailLoadedPreview-dark-end.png) |
| S10 계획 선택 시각 | 시간 열 확장·텍스트가 열 폭을 채우도록 정렬 | [light](resume/before/BearerPlanPickLimitPreview-light-end.png) | [light](resume/final/BearerPlanPickLimitPreview-light-end.png), [dark](resume/final/BearerPlanPickLimitPreview-dark-end.png) |
| S07 크루장 메뉴 | 단독 Content fixture를 실제 FCSheet 호스트로 교체(제품 코드 변경 아님) | [단독 Content의 부정확한 dark](resume/before/CrewMenuPreview-dark-end.png) | [실제 시트 light](resume/final/CrewMenuPreview-light-end.png), [dark](resume/final/CrewMenuPreview-dark-end.png) |

상태 버튼 아래 안전영역·본문 마지막 크루원·초대 만료 날짜와 시각·시트 마지막 설명을 확인했다. S05의 긴 아티스트/섹션 제목에는 기존 한 줄 말줄임 정책이 남아 있으며 전체 콘텐츠가 무조건 표시된다고 주장하지 않는다. 기수 인증은 200%와 키보드가 열린 상태를 확인했고 오류/잠김 안내의 전체 스크롤·모든 IME 상태는 별도 미검증이다. 계정/크루 생성 완료 상태도 light/dark 캡처했으나 계정 삭제·가입·초대 전송은 실행하지 않았다.

## S01 원본 프레임과 추가 실행 상태

원본 PNG는 Downloads의 11/25 export다. 최신 복사본 전체 native raster는 아니다. 이름/프레임 ID/상태 대응과 구조 대조를 기록하며 데이터·기기 크기 차이로 픽셀 동일성을 주장하지 않는다. 원본과 실제 화면의 전체 시점·문자열·이미지를 맞춘 비교는 남아 있다.

| 원본 light / dark ID | 추가 실행 fixture | 원본 PNG | 실행 light / dark |
|---|---|---|---|
| 166:3379 / 174:6384 | 오프라인 | [원본](local-gaps/reference-166-3379.png) | [light](resume/s01/LiveBoardOfflinePreview-light-top.png), [dark](resume/s01/LiveBoardOfflinePreview-dark-top.png) |
| 166:2901 / 174:5669 | 로그인 후 크루·팔로우 빈 상태 | [원본](local-gaps/reference-166-2901.png) | [light 끝](resume/s01/LiveBoardSignedInEmptyPreview-light-end.png), [dark 끝](resume/s01/LiveBoardSignedInEmptyPreview-dark-end.png) |
| 166:2511 / 174:5243 | 시작 전 | [원본](local-gaps/reference-166-2511.png) | [light](resume/s01/LiveBoardBeforeOpeningPreview-light-top.png), [dark](resume/s01/LiveBoardBeforeOpeningPreview-dark-top.png) |
| 166:3122 / 174:6165 | 종료 후 | [원본](local-gaps/reference-166-3122.png) | [light](resume/s01/LiveBoardEndedPreview-light-top.png), [dark](resume/s01/LiveBoardEndedPreview-dark-top.png) |
| 419:19174 / 419:19242 | 페스티벌 전환 시트 | [원본](local-gaps/reference-419-19174.png) | [light](resume/s01/LiveBoardSwitcherPreview-light-top.png), [dark](resume/s01/LiveBoardSwitcherPreview-dark-top.png) |
| 173:4342 / 174:7766 | 팔로우 안내 숨김 확인 | [원본](local-gaps/reference-173-4342.png) | [light](resume/s01/LiveBoardHideGuidePreview-light-top.png), [dark](resume/s01/LiveBoardHideGuidePreview-dark-top.png) |
| 별도 frame 없음 | 재연결 | 원본 직접 대응 없음 | [light](resume/s01/LiveBoardReconnectingPreview-light-top.png), [dark](resume/s01/LiveBoardReconnectingPreview-dark-top.png) |

S07 메뉴의 원본 ID는 457:21455 / 459:21640, S08 기본 초대는 461:22124 / 462:22209, S05 기본은 264:13581 / 268:14298다. 이 화면들은 최신 native raster가 없어 실제 실행의 글꼴/스크롤/상태 검증과 원본 픽셀 대조를 구분한다.

## 시간 열 회귀 원인과 검증

첫 두 실패 및 승인된 추가 재검증 실패를 숨기지 않는다. 1회 계측으로 실제 `BasicText`의 paragraphWidth와 size.width가 달라 폭 overflow가 발생함을 확인했다. 기본 배율 시작은 size47/paragraph48, 종료34/paragraph48; 200% 종료는 size70/paragraph82였다. 높이 overflow는 모두 false, lineCount1·문자끝5·ellipsisfalse·glyph right는 size 내부였다. 같은 style/density/constraints의 TextMeasurer는 paragraph=size로 false였다. Blocking Pretendard 리소스를 사용하며 부모 높이 제약도 충분했다.

실제 잘림은 최초 48dp 시간 열에서 발생했고, `48dp × fontScale`로 확장한 뒤의 반복 실패는 이 paragraph/size 차이였다. 추가한 TextMeasurer는 제거했다. 기존 열을 BasicText가 fillMaxWidth로 채우는 최소 수정 후 strict hasVisualOverflow/didOverflowWidth/ellipsis/문자끝 assertion 모두 통과했다. 기본·200%·280dp 좁은 폭에서 인접 내용 표시와 행 클릭도 검증했다. 폰트 축소·skip·assertion 완화 없음. 진단 원문은 [요약](resume/diagnostic-summary.txt), [테스트 집계](resume/test-counts.json)에 연결한다.

전체 verify: library1,338 + Stage 앱190 = 1,528개 테스트, 실패/오류/skip0; hooks·boundaries·format·Stage APK 통과. 이번 수정에 Firebase/Prod 설정·SDK 변경은 없으며 기존 Prod189/R8 결과를 이번 HEAD의 새 결과로 포장하지 않는다. 최신 원격 CI는 PR checks/최종 댓글로 별도 확인한다.

## 남은 경계

- 최신 `FESTICORE (복사).fig` SHA256 `7669295fdbc34cea802d937e1ecb990772d65ecdbb570ca0e54e4e9817b4f575` 유지. 현재 Figma GUI 접근은30초 timeout. 전체261 frame/theme native raster 확보와 전 화면 픽셀 대조는 미완료.
- 기존 대응표의 미검증 항목은 위에서 명시적으로 갱신한 상태만 보완한다. 알림·권한·사진·계정·실서버 저장/재연결 등 모든 조합을 확인한 것은 아니다.
- 실제 런처 위젯 설치/resize 증거는 [앞선 보고서](LOCAL-GAPS.md)를 재사용하며 이번에는 기존 호스트 연결을 재확인한다. deep link 완료·Doze·서버 갱신·계정 교체는 미검증.
- Firebase OAuth/정확한 Prod package 등록·키 주입·실수신은 별도 미확인 상태. 새 로그인·키 발급·운영 쓰기 없음.
- iOS chinook은 읽기 참고만, 메인 체크아웃과 미추적 firebase-debug.log 보존. Claude 교차검토는 승인된 생략이며 CI/자체 검증으로 구분한다.

재사용/단순화: 기존 Compose 레이아웃·Content·FCSheet·Preview·캡처 Activity를 재사용했다. 측정용 추상화와 임시 앱 로그는 남기지 않았고 새 의존성·서버 API·생성 토큰 변경은 없다.

## 좁은 폭과 위젯 증거

280dp [기본 light](resume/narrow/plan-280dp-light-1.0.png), [200% light](resume/narrow/plan-280dp-light-2.0.png), [200% dark](resume/narrow/plan-280dp-dark-2.0.png)에서 시각과 체크박스는 표시된다. 200%의 인접 아티스트·무대 이름에는 기존 한 줄 말줄임이 남는다. [실제 위젯](resume/narrow/launcher-widget-yabby.png)은 io.festiCore.co.stage의 기존 런처 호스트 연결이며 이번 서버 갱신 증거는 아니다.

S01 종료 카드·빈 크루/팔로우·전환 시트의 구조를 원본과 대조했다. fixture 이름·이미지·일정 값은 원본과 다르며 귀가 경로 카드는 미검증이다. 안내 숨김 확인에는 원본보다 설정 안내 문장이 추가되어 있어 문구가 완전히 같지는 않다. 스크린샷 전체의 픽셀 일치 판정은 하지 않았다.
