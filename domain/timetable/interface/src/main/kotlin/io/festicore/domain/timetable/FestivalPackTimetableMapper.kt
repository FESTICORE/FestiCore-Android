package io.festicore.domain.timetable

import io.festicore.domain.festival.FestivalPackDocument

/**
 * 팩 문서 → 시간표. 규칙은 `domain/timetable/.spec/timetable-mapping.md`.
 * iOS `App/Sources/Navigation/MainTab/FestivalPackTimetableMapper.swift`(Android는 app에 로직을 두지 않아 domain으로 옮겼다, 설계 T3).
 * 상대 사진 경로는 구현이 서버 주소로 바꾼다.
 */
interface FestivalPackTimetableMapper {
    /** 날짜별 무대 · 공연. [previousDays]는 같은 축제의 직전 결과이고, 같은 공연의 시작이 바뀌면 `previousStart`를 남긴다. */
    fun days(pack: FestivalPackDocument, previousDays: List<TimetableDay> = emptyList()): List<TimetableDay>

    /** 공개 전 라인업. 이름 있는 아티스트가 없으면 빈 목록. */
    fun lineup(pack: FestivalPackDocument): List<TimetableLineupDay>

    /** 격자 아래 출처 문구. 이름이 없으면 "". */
    fun source(source: FestivalPackDocument.Source?): String
}
