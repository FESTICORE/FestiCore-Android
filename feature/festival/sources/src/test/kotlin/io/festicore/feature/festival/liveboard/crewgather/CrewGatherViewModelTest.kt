package io.festicore.feature.festival.liveboard.crewgather

import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.crew.FakeCrewUseCase
import io.festicore.feature.festival.liveboard.MutableClock
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FestivalCrewGatherFeatureTests`. */
class CrewGatherViewModelTest {
    private val now: Instant = Instant.ofEpochSecond(1000)
    private val zones = listOf(FestivalPackDocument.Zone("zone_a", "메인"), FestivalPackDocument.Zone("zone_b", "서브"))

    private fun viewModel(crewUseCase: FakeCrewUseCase = FakeCrewUseCase(), availableAt: Instant? = null) = CrewGatherViewModel(
        input = CrewGatherInput(presentation = 1, crewId = "crew_a", zones = zones, availableAt = availableAt, recipientCount = null),
        crewUseCase = crewUseCase,
        clock = MutableClock(now),
    )

    private val initial = CrewGatherState(crewId = "crew_a", zones = zones)

    @Test
    fun tappingOfficialZoneSendsImmediately() = runTest {
        val requests = mutableListOf<String>()
        viewModel(FakeCrewUseCase(gather = { crew, zone -> requests += "$crew/$zone" })).test(this, initialState = initial) {
            // Figma S07 E: 공식 존이 아닌 값은 무시하고, 존 타일을 누르면 바로 보낸다.
            containerHost.onZoneTapped("unknown")
            containerHost.onZoneTapped("zone_a")
            expectState { copy(selectedZoneId = "zone_a", isSubmitting = true) }
            expectState { copy(isSubmitting = false) }
            expectSideEffect(CrewGatherSideEffect.Gathered)
        }
        assertEquals(listOf("crew_a/zone_a"), requests)
    }

    @Test
    fun submittingPreventsDuplicateRequestAndZoneChange() = runTest {
        viewModel().test(this, initialState = initial.copy(selectedZoneId = "zone_a", isSubmitting = true)) {
            containerHost.onZoneTapped("zone_a")
            containerHost.onZoneTapped("zone_b")
        }
    }

    @Test
    fun failureKeepsSheetAndAllowsRetry() = runTest {
        var attempts = 0
        val useCase =
            FakeCrewUseCase(gather = { _, _ ->
                attempts += 1
                throw FestivalCrewError.Cooldown()
            })
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onZoneTapped("zone_a")
            expectState { copy(selectedZoneId = "zone_a", isSubmitting = true) }
            expectState {
                copy(
                    isSubmitting = false,
                    alert = CrewGatherAlert("집결을 요청하지 못했어요", "크루 집결은 10분에 한 번 요청할 수 있어요. 잠시 후 다시 시도해 주세요."),
                )
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun cooldownPreventsRequest() = runTest {
        val availableAt = now.plusSeconds(60)
        viewModel(availableAt = availableAt).test(this, initialState = initial.copy(availableAt = availableAt)) {
            containerHost.onZoneTapped("zone_a")
            expectState { copy(alert = CrewGatherAlert("잠시 후 다시 모일 수 있어요", "크루 집결은 10분에 한 번 요청할 수 있어요.")) }
        }
    }

    /** 받는 사람 수를 모르거나 0이면 숫자 없이 안내한다(iOS `subtitle`). */
    @Test
    fun subtitleShowsRecipientCountOnlyWhenKnown() {
        assertEquals("누르면 바로 크루원에게 소리 알림이 가요", initial.subtitle)
        assertEquals("누르면 바로 크루원에게 소리 알림이 가요", initial.copy(recipientCount = 0).subtitle)
        assertEquals("누르면 바로 크루원 3명에게 소리 알림이 가요", initial.copy(recipientCount = 3).subtitle)
    }
}
