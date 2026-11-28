package io.festicore.feature.festival.widget

import io.festicore.domain.festival.widget.FestivalWidgetRead
import io.festicore.domain.festival.widget.FestivalWidgetUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Every trigger shares this queue and reads current disk/account state inside the lock. */
class FestivalWidgetUpdater internal constructor(
    private val useCase: FestivalWidgetUseCase,
    private val publish: (FestivalWidgetRead) -> Unit,
    private val scope: CoroutineScope,
) {
    private val renderMutex = Mutex()
    private var started = false

    @Synchronized fun start() {
        if (started) return
        started = true
        scope.launch { safely { renderAll() } }
        scope.launch { useCase.changes.collect { safely { renderAll() } } }
        scope.launch {
            while (true) {
                try {
                    useCase.observeSessions()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Initial identity read failure is not logout. Local read shows public information only.
                    safely { renderAll() }
                    delay(30_000)
                }
            }
        }
    }

    internal fun requestRender(onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                kotlinx.coroutines.withTimeout(8_000) { safely { renderAll() } }
            } finally {
                onFinished()
            }
        }
    }

    internal suspend fun renderAll() = renderMutex.withLock {
        try {
            repeat(8) {
                val candidate = useCase.read()
                val current = useCase.read()
                if (candidate == current) {
                    publish(current)
                    return@withLock
                }
            }
            // A continuously changing session never publishes a stale personal projection.
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Failure output shares the same queue, so it cannot race a newer successful render.
            publish(FestivalWidgetRead(null, -1, unavailable = true))
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Launcher failures are isolated from the application scope; a later trigger retries.
        }
    }
}
