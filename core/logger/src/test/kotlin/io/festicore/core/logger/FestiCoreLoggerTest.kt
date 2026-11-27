package io.festicore.core.logger

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FestiCoreLoggerTest {
    private val received = mutableListOf<FestiCoreLogEntry>()

    @After
    fun tearDown() {
        FestiCoreLogger.removeAllSinks()
        FestiCoreLogger.minimumLevel = FestiCoreLogLevel.DEBUG
        received.clear()
    }

    @Test
    fun `같은 sink는 한번만 서로 다른 인스턴스는 각각 받는다`() {
        class RecordingSink : FestiCoreLogSink {
            override fun send(entry: FestiCoreLogEntry) {
                received += entry
            }
        }
        val first = RecordingSink()
        FestiCoreLogger.addSink(first)
        FestiCoreLogger.addSink(first)
        FestiCoreLogger.addSink(RecordingSink())
        FestiCoreLogger.info("정보", FestiCoreLogCategory.APP)
        assertEquals(2, received.size)
    }

    @Test
    fun `외부 출력 대상에는 INFO 이상만 보낸다`() {
        FestiCoreLogger.addSink { received += it }

        FestiCoreLogger.debug("디버그", FestiCoreLogCategory.APP)
        FestiCoreLogger.info("정보", FestiCoreLogCategory.NETWORK)
        FestiCoreLogger.error("오류", FestiCoreLogCategory.AUTH)

        assertEquals(listOf(FestiCoreLogLevel.INFO, FestiCoreLogLevel.ERROR), received.map { it.level })
        assertEquals(FestiCoreLogCategory.NETWORK, received.first().category)
    }

    @Test
    fun `최소 단계보다 낮은 로그는 버린다`() {
        FestiCoreLogger.minimumLevel = FestiCoreLogLevel.NOTICE
        FestiCoreLogger.addSink { received += it }

        FestiCoreLogger.info("정보", FestiCoreLogCategory.APP)
        FestiCoreLogger.fault("치명", FestiCoreLogCategory.APP)

        assertEquals(listOf(FestiCoreLogLevel.FAULT), received.map { it.level })
    }
}
