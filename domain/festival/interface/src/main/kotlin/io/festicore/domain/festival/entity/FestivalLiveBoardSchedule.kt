package io.festicore.domain.festival.entity

import java.time.Instant

/**
 * 팩에서 검증한 공연 시각과 무대별 현재 · 이전 · 다음 공연. iOS `FestivalLiveBoardSchedule`.
 * 유효한 공연: 시작 시각이 [FestivalIsoTime] 형식, 아티스트가 공백이 아님, 무대(ID 또는 이름)가 있음, 종료가 있으면 시작보다 늦음.
 */
object FestivalLiveBoardSchedule {
    data class Slot(
        val id: String,
        val artist: String,
        val startsAt: Instant,
        val endsAt: Instant?,
    )

    data class Stage(
        val id: String,
        val name: String,
        val current: Slot?,
        val previous: Slot?,
        val next: Slot?,
    )

    data class Entry(
        val stageName: String,
        val slot: Slot,
    )

    private val slotOrder = compareBy<Slot>({ it.startsAt }, { it.id })

    /** 유효한 공연을 무대 이름과 함께 시작 시각 → 공연 ID 순으로. */
    fun entries(pack: FestivalPackDocument): List<Entry> {
        val stageNames = pack.stageNames()
        return pack.performances
            .mapNotNull { performance ->
                val parsed = performance.parsed() ?: return@mapNotNull null
                Entry(nonEmpty(performance.stageName) ?: stageNames[parsed.stageId] ?: parsed.stageId, parsed.slot)
            }
            .sortedWith(compareBy(slotOrder) { it.slot })
    }

    /**
     * 무대별 현재 · 이전 · 다음 공연. 무대 순서는 팩에 처음 나온 순서. 시작은 포함, 종료는 제외한다.
     * 종료가 없는 공연은 현재가 되지 않지만 이전은 될 수 있다. 현재도 다음도 없는 무대는 뺀다.
     */
    fun stages(pack: FestivalPackDocument, now: Instant): List<Stage> {
        val stageNames = pack.stageNames()
        val names = LinkedHashMap<String, String>()
        val slots = mutableMapOf<String, MutableList<Slot>>()
        for (performance in pack.performances) {
            val parsed = performance.parsed() ?: continue
            names.getOrPut(parsed.stageId) { nonEmpty(performance.stageName) ?: stageNames[parsed.stageId] ?: parsed.stageId }
            slots.getOrPut(parsed.stageId) { mutableListOf() } += parsed.slot
        }
        return names.mapNotNull { (stageId, name) ->
            val ordered = slots[stageId].orEmpty().sortedWith(slotOrder)
            val current = ordered.lastOrNull { slot -> slot.startsAt <= now && slot.endsAt?.let { now < it } == true }
            val previous = ordered.lastOrNull { it.startsAt <= now }
            val next = ordered.firstOrNull { it.startsAt > now }
            if (current == null && next == null) null else Stage(stageId, name, current, previous, next)
        }
    }

    private class Parsed(
        val stageId: String,
        val slot: Slot,
    )

    private fun FestivalPackDocument.Performance.parsed(): Parsed? {
        val start = FestivalIsoTime.parse(startsAt) ?: return null
        val artist = nonEmpty(artist) ?: return null
        val stageId = nonEmpty(stageId) ?: nonEmpty(stageName) ?: return null
        val end = FestivalIsoTime.parse(endsAt)
        if (end != null && end <= start) return null
        return Parsed(stageId, Slot(id, artist, start, end))
    }

    private fun FestivalPackDocument.stageNames(): Map<String, String> {
        val names = mutableMapOf<String, String>()
        stages.orEmpty().forEach { names.putIfAbsent(it.id, it.name) }
        return names
    }

    private fun nonEmpty(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
