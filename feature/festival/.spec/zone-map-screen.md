---
spec: zone-map-screen
modules: [feature/festival, app]
---

# 존 지도 화면 Spec

> S06 존 지도: 받아 둔 팩의 존 영역 위에 라이브 깃발 · 크루를 놓고, 존을 고르면 카드로 보여 준다. 보드 크루 존 칩에서 push한다.

iOS 대응: `Projects/Feature/Festival/Sources/ZoneMap/**`(Reducer · Model · View · Skeleton · Canvas · Card), 전환 `Projects/App/Sources/Navigation/Festival/FestivalCoordinator.swift:29-37 · 176-206`, 탭 바 `MainTab/MainTabCoordinator.swift:77-100`.
설계: `.claude/docs/specs/2026-11-22-live-extras-android-design.md`(화면 1 · 화면 전환 · 탭 바 · PR Z2 · Z3). 수치 · 문구 · 토큰은 iOS View 값 그대로이고 아래는 동작만 적는다.

## 개요

`FestivalRoute.ZoneMap(festivalId, zoneId?)` → `ZoneMapScreen`(ViewModel 연결) · `ZoneMapContent`(순수 UI). 지도는 이미지 · 지도 SDK가 아니라 0~1 비율 사각형을 화면 너비에 맞춰 그린 Compose 레이아웃이고 세로 스크롤만 있다(E1).

## 동작 규칙

1. 진입: 보드 크루 카드의 존 칩만 존 지도를 연다(활성 깃발 타일 존 칩은 무동작). 이미 스택에 존 지도가 있으면 쌓지 않는다. 그 존이 선택된 채 열린다. 탭 바는 보인다.
2. 첫 화면: 처음 보일 때 보드가 마지막으로 받은 라이브 상태(`cachedLiveBoard`)가 있으면 그것으로 마커를 그리고, 곧바로 `liveBoard`로 새로 조회한다. 없으면 마커 없이 그린 뒤 조회 결과로 채운다.
3. 지도 상태: 읽는 중 스켈레톤 → 팩 없음 "이 페스티벌 팩을 받으면 존 지도를 볼 수 있어요" / 읽기 실패 "존 지도를 불러오지 못했어요"(+ "다시 시도" = 읽는 중으로 두고 다시 읽기) → `hasLayout`이면 지도, 아니면 존 목록. 읽힌 지도에 선택 존이 없으면 선택을 지운다.
4. 지도 버전: 지도를 읽을 때마다 서버 지도 버전을 비교해 다르면 위에 "지도가 바뀌었어요 · 팩을 업데이트해 주세요"(지도 화면에서만). 비교 실패는 같음으로 본다. 누르면(진행 중이면 무시) `syncPack` → `Updated` · `NotModified`면 배너를 내리고 지도를 다시 읽는다, 그 밖은 진행만 끝(문구 없음).
5. 라이브 감시: 화면이 보이고 포그라운드일 때만 즉시 조회 → 35초 대기 반복. 429면 `max(35초, retryAfter)` 대기하고 상태를 유지한다. 그 밖 실패는 오프라인 배지(마지막 스냅샷 유지). 성공하면 스냅샷 · `receivedAt = snapshot.receivedAt ?: 지금` · 온라인. 화면이 빠지거나 배경으로 가면 멈춘다.
6. 선택: 존을 누르면 고르고 같은 존을 다시 누르면 해제, 지도 빈 곳을 누르면 해제. 존 목록 행 탭도 선택만 바꾼다(목록 화면엔 카드가 없다, E11).
7. 아래 겹침(바깥 여백 16): 고른 존 카드 > 안내 띠 > 범례. 깃발 꺼짐이면 "지금은 깃발 위치를 볼 수 없어요 · 크루만 보여요"(무동작), 팔로우 깃발 · 크루가 둘 다 없으면 "깃발을 팔로우하면 지도에 크게 보여요"(누르면 보드로 pop + 깃발 탭 전환, E6). 범례 항목은 팔로우 깃발 있음 · 크루 있음 · 깃발 켜짐일 때만.
8. 마커: 깃발은 팩 `flagsEnabled`일 때만, 상태 `active` · `resting`이고 지도에 있는 존에 있는 것만. 존마다 팔로우 깃발 → 크루 → 다른 깃발. 크루 색은 크루 목록 순서로 Blue · Pink · Orange · Green · Purple · Yellow 순환. 30분 넘게 소식이 없으면 흐리게(시각 없으면 흐리지 않음). 영역 높이 < 72면 마커를 이름표 줄 끝에, 아니면 아래 줄에 두고 마커 총너비(팔로우 깃발 52 · 그 밖 40 · 간격 12)가 영역 너비 − 24를 넘으면 `+N` 묶음.
9. 고른 존 보이기: 처음은 바로, 선택이 바뀌면 애니메이션으로, 지도 위 여백 16 + (존 아래) × 지도 높이 + 34가 보이는 영역(뷰 높이 − 아래 겹침 높이) 바닥에 오도록 스크롤한다(0~최대로 자름).
10. 뒤로: 상단 바 뒤로 · 시스템 뒤로 = pop. 계정이 바뀌면(로그인 · 로그아웃 · 다른 계정) 스택에서 존 지도를 뺀다.
11. 시각: 오프라인 띠 · 오래됨 · 카드 상대 시각은 State가 바뀔 때 다시 계산한다(라이브 감시 35초 주기).

## 엣지 케이스

- 선택 존이 지도 밖(영역 없음)이면 카드만 보이고 지도는 스크롤하지 않는다.
- 비율이 없는데 영역 있는 존이 있으면 `hasLayout`이 거짓이라 목록으로 보인다.

## 제약 / 가정

- 지도 SDK · 확대 · 이동 없음. `mapImageUrl`은 그리지 않는다(E2).
- 접근성: 존 영역은 자식 합치기 + 버튼 + 선택됨, 마커 라벨 = 이름, 묶음 "N개", 스켈레톤 "존 지도를 불러오는 중". iOS에 접근성 식별자가 없어 `testTag`를 두지 않는다.
- 그림자 반경은 designkit 선례(`FCSortMenu`)처럼 iOS `shadow(radius)` 값을 Compose `Shadow.radius`에 그대로 쓴다.

## 작업 모듈

| 모듈 | 계층 | 변경 내용 |
| --- | --- | --- |
| `feature/festival/interface` | feature | `FestivalRoute.ZoneMap` |
| `feature/festival/sources` | feature | `zonemap/`(Model · Contract · ViewModel · Screen · Canvas · Card · 프리뷰), Coordinator push · pop · 깃발 탭 요청 · 계정 변경 정리, `FestivalFlow` 항목 · `onFlagTabRequested`, Koin |
| `app` | app | `MainTabScreen` 깃발 탭 전환 콜백, `AppFlow` 연결 |

## 영향 범위

| 모듈 | 계층 | 영향 이유 |
| --- | --- | --- |
| `feature/festival` 보드 | feature | `ForegroundEffect`를 존 지도와 함께 쓰도록 `internal`로 열었다 |

## 미해결 질문

- 깃발 탭은 아직 자리 화면이라 "깃발을 팔로우하면…" 띠가 빈 탭으로 보낸다(설계 위험 6, 깃발 단계에서 해소).

## 변경 이력

- 존 지도 화면 · 연결(PR Z2 · Z3) 추가
