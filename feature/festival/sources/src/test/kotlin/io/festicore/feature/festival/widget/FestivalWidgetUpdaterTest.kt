package io.festicore.feature.festival.widget

import io.festicore.domain.festival.entity.FestivalWidgetSnapshot
import io.festicore.domain.festival.widget.FestivalWidgetRead
import io.festicore.domain.festival.widget.FestivalWidgetUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FestivalWidgetUpdaterTest {
    private fun state(revision: Long, personal: Boolean = true) = FestivalWidgetRead(
        FestivalWidgetSnapshot("a", "축제", 1, "v1", emptyList(), emptyList(), crewSummary = if (personal) "개인 크루" else null),
        revision,
    )
    private class Fake(var current: FestivalWidgetRead) : FestivalWidgetUseCase {
        override val changes = MutableSharedFlow<Unit>()
        var observed = false
        var reads = 0
        var nextRead: (suspend () -> FestivalWidgetRead)? = null
        override suspend fun read(): FestivalWidgetRead {
            reads++
            return nextRead?.also { nextRead = null }?.invoke() ?: current
        }
        override suspend fun observeSessions() {
            observed = true
        }
    }

    @Test fun `startup renders local snapshot and observes expiry without UI`() = runTest {
        val fake = Fake(state(1))
        val published = mutableListOf<FestivalWidgetRead>()
        val updater = FestivalWidgetUpdater(fake, published::add, backgroundScope)
        updater.start()
        runCurrent()
        assertTrue(fake.observed)
        assertEquals(listOf(fake.current), published)
    }

    @Test fun `queued old read cannot overwrite newer logout render`() = runTest {
        val old = state(1)
        val fake = Fake(old)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        fake.nextRead = {
            entered.complete(Unit)
            resume.await()
            old
        }
        val published = mutableListOf<FestivalWidgetRead>()
        val updater = FestivalWidgetUpdater(fake, published::add, backgroundScope)
        val first = launch { updater.renderAll() }
        runCurrent()
        // The stub never reads; fail promptly instead of hanging on a deferred.
        assertTrue(entered.isCompleted)
        fake.current = state(2, personal = false)
        val second = launch { updater.renderAll() }
        resume.complete(Unit)
        first.join()
        second.join()
        assertTrue(published.isNotEmpty())
        assertTrue(published.all { it.snapshot?.crewSummary == null })
        assertEquals(2L, published.last().revision)
    }

    @Test fun `local read failure publishes unavailable and next render recovers`() = runTest {
        val fake = Fake(state(1))
        fake.nextRead = { error("disk unavailable") }
        val published = mutableListOf<FestivalWidgetRead>()
        val updater = FestivalWidgetUpdater(fake, published::add, backgroundScope)
        assertTrue(runCatching { updater.renderAll() }.isSuccess)
        assertTrue(published.single().unavailable)
        updater.renderAll()
        assertEquals(fake.current, published.last())
    }
}
