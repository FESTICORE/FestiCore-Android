package io.festicore.feature.profile.notification
import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.notificationsettings.FestivalNotificationSettingsUseCase
import io.festicore.domain.festival.notificationsettings.NotificationInvalidRegistration
import io.festicore.domain.festival.notificationsettings.NotificationRegistrationUnavailable
import io.festicore.domain.festival.notificationsettings.NotificationSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class NotificationSettingsState(
    val values: Map<NotificationSetting, Boolean> = emptyMap(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val terminal: Boolean = false,
    val error: String? = null,
    val errorRevision: Long = 0,
    val confirmed: Map<NotificationSetting, Boolean> = emptyMap(),
    val pending: Map<NotificationSetting, Boolean> = emptyMap(),
    val generation: Long = 0,
)
class NotificationSettingsViewModel(private val useCase: FestivalNotificationSettingsUseCase) :
    ViewModel(),
    OrbitContainerHost<NotificationSettingsState, NotificationSettingsState, Nothing> {
    override val container = orbitContainer<NotificationSettingsState, Nothing>(NotificationSettingsState())

    fun onResume() = intent {
        var request: Long? = null
        reduce {
            if (state.terminal || state.saving || state.loading) {
                state
            } else {
                request = state.generation + 1
                state.copy(loading = true, error = null, generation = state.generation + 1)
            }
        }
        if (request == null) return@intent
        try {
            val loaded = withTimeout(15_000) { useCase.load() }
            reduce {
                if (request != state.generation) state else state.copy(values = loaded, confirmed = loaded, loading = false)
            }
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            reduce {
                if (request !=
                    state.generation
                ) {
                    state
                } else {
                    state.copy(
                        loading = false,
                        terminal = isTerminal(error),
                        error = message(error),
                        errorRevision =
                            state.errorRevision + 1,
                    )
                }
            }
        }
    }

    fun onToggle(key: NotificationSetting, value: Boolean) = intent {
        var ownsSave = false
        reduce {
            if (state.terminal || key !in state.values) {
                state
            } else {
                ownsSave = !state.saving
                state.copy(
                    values = state.values + (key to value),
                    pending = state.pending + (key to value),
                    generation = state.generation + 1,
                    loading = false,
                    saving = true,
                    error = null,
                )
            }
        }
        if (!ownsSave) return@intent
        while (true) {
            var batch = emptyMap<NotificationSetting, Boolean>()
            reduce {
                batch = state.pending
                state.copy(pending = emptyMap(), saving = batch.isNotEmpty())
            }
            if (batch.isEmpty()) return@intent
            try {
                val saved = withTimeout(15_000) { useCase.save(batch) }
                reduce { state.copy(confirmed = saved, values = saved + state.pending) }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) {
                    reduce { state.copy(saving = false, pending = emptyMap(), values = state.confirmed) }
                    throw error
                }
                var failure = error
                var observed: Map<NotificationSetting, Boolean>? = null
                if (!isTerminal(error)) {
                    try {
                        observed = withTimeout(15_000) { useCase.load() }
                    } catch (reloadError: Exception) {
                        if (reloadError is CancellationException && reloadError !is TimeoutCancellationException) throw reloadError
                        if (isTerminal(reloadError)) failure = reloadError
                    }
                }
                val terminal = isTerminal(failure)
                reduce {
                    val pending = if (terminal) emptyMap() else state.pending
                    val confirmed = observed ?: state.confirmed
                    state.copy(
                        confirmed = confirmed,
                        values = confirmed + pending,
                        pending = pending,
                        terminal = terminal,
                        error = message(failure),
                        errorRevision = state.errorRevision + 1,
                    )
                }
            }
        }
    }

    private fun isTerminal(error: Exception) = error is NotificationRegistrationUnavailable || error is NotificationInvalidRegistration
    private fun message(error: Exception) = when (error) {
        is NotificationInvalidRegistration -> "기기 정보를 등록할 수 없어요. 앱 업데이트 후 다시 확인해 주세요."
        is NotificationRegistrationUnavailable -> "기기 설정을 확인할 수 없어요. 고객 지원에 문의해 주세요."
        else -> "알림 설정을 저장하거나 불러오지 못했어요. 다시 시도해 주세요."
    }
}
