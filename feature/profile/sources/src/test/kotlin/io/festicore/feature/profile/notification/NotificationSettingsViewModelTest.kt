package io.festicore.feature.profile.notification
import io.festicore.domain.festival.notificationsettings.FestivalNotificationSettingsUseCase
import io.festicore.domain.festival.notificationsettings.NotificationRegistrationUnavailable
import io.festicore.domain.festival.notificationsettings.NotificationSetting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test
class NotificationSettingsViewModelTest {
    private class Fake : FestivalNotificationSettingsUseCase {
        var values = mapOf(NotificationSetting.FlagStatus to true, NotificationSetting.OfficialNews to true)
        var error: Exception? = null
        var loads = 0
        var loseSaveResponse = false
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        val writes = mutableListOf<Map<NotificationSetting, Boolean>>()
        override suspend fun load(): Map<NotificationSetting, Boolean> {
            loads++
            error?.let { throw it }
            return values
        }
        override suspend fun save(changes: Map<NotificationSetting, Boolean>): Map<NotificationSetting, Boolean> {
            writes += changes
            gate?.await()
            error?.let { throw it }
            values =
                values + changes
            if (loseSaveResponse) kotlinx.coroutines.awaitCancellation()
            return values
        }
    }

    @Test fun loadsConfirmedSettingsAndSendsOnlyChangedKey() = runTest {
        val fake = Fake()
        NotificationSettingsViewModel(fake).test(this) {
            containerHost.onResume().join()
            testScheduler.advanceUntilIdle()
            assertEquals(fake.values, containerHost.container.stateFlow.value.values)
            containerHost.onToggle(NotificationSetting.OfficialNews, false).join()
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(mapOf(NotificationSetting.OfficialNews to false)), fake.writes)
            assertEquals(false, containerHost.container.stateFlow.value.values[NotificationSetting.OfficialNews])
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test fun rapidReversalSurvivesInFlightResponseAndResumeDoesNotReload() = runTest {
        val fake = Fake()
        NotificationSettingsViewModel(fake).test(this) {
            containerHost.onResume().join()
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            fake.gate = gate
            val first = containerHost.onToggle(NotificationSetting.FlagStatus, false)
            testScheduler.runCurrent()
            containerHost.onToggle(NotificationSetting.FlagStatus, true).join()
            containerHost.onResume().join()
            assertEquals(1, fake.loads)
            assertEquals(true, containerHost.container.stateFlow.value.values[NotificationSetting.FlagStatus])
            gate.complete(Unit)
            first.join()
            assertEquals(listOf(mapOf(NotificationSetting.FlagStatus to false), mapOf(NotificationSetting.FlagStatus to true)), fake.writes)
            assertEquals(true, containerHost.container.stateFlow.value.values[NotificationSetting.FlagStatus])
            assertEquals(false, containerHost.container.stateFlow.value.saving)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test fun failedSaveRollsBackAndAllowsExplicitRetry() = runTest {
        val fake = Fake()
        NotificationSettingsViewModel(fake).test(this) {
            containerHost.onResume().join()
            fake.error = IllegalStateException("offline")
            containerHost.onToggle(NotificationSetting.FlagStatus, false).join()
            assertEquals(true, containerHost.container.stateFlow.value.values[NotificationSetting.FlagStatus])
            assertTrue(containerHost.container.stateFlow.value.error != null)
            fake.error = null
            containerHost.onToggle(NotificationSetting.FlagStatus, false).join()
            assertEquals(false, containerHost.container.stateFlow.value.values[NotificationSetting.FlagStatus])
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test fun registrationUnavailableDoesNotAutomaticallyRetry() = runTest {
        val fake = Fake().apply { error = NotificationRegistrationUnavailable() }
        NotificationSettingsViewModel(fake).test(this) {
            containerHost.onResume().join()
            testScheduler.advanceUntilIdle()
            assertTrue(containerHost.container.stateFlow.value.terminal)
            containerHost.onResume().join()
            assertEquals(1, fake.loads)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test fun repeatedFailureHasANewPresentationRevision() = runTest {
        val fake = Fake().apply { error = IllegalStateException("offline") }
        NotificationSettingsViewModel(fake).test(this) {
            containerHost.onResume().join()
            val first = containerHost.container.stateFlow.value
            containerHost.onResume().join()
            val second = containerHost.container.stateFlow.value
            assertEquals(first.error, second.error)
            assertEquals(first.errorRevision + 1, second.errorRevision)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test fun cancelledSaveDoesNotBecomeAnErrorOrRetry() = runTest {
        val fake = Fake()
        NotificationSettingsViewModel(fake).test(this) {
            containerHost.onResume().join()
            fake.error = kotlinx.coroutines.CancellationException("cancelled")
            val job = containerHost.onToggle(NotificationSetting.FlagStatus, false)
            job.join()
            assertEquals(false, containerHost.container.stateFlow.value.saving)
            assertEquals(true, containerHost.container.stateFlow.value.values[NotificationSetting.FlagStatus])
            assertEquals(1, fake.writes.size)
            assertEquals(1, fake.loads)
            assertEquals(null, containerHost.container.stateFlow.value.error)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test fun lostSaveResponseReloadsAppliedServerValue() = runTest {
        val fake = Fake()
        NotificationSettingsViewModel(fake).test(this) {
            containerHost.onResume().join()
            fake.loseSaveResponse = true
            val save = containerHost.onToggle(NotificationSetting.FlagStatus, false)
            testScheduler.advanceTimeBy(15_001)
            save.join()
            assertEquals(false, containerHost.container.stateFlow.value.values[NotificationSetting.FlagStatus])
            assertEquals(false, containerHost.container.stateFlow.value.saving)
            cancelAndIgnoreRemainingItems()
        }
    }
}
