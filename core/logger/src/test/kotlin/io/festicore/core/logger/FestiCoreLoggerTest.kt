package io.festicore.core.logger

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FestiCoreLoggerTest {
    private val received = mutableListOf<LogEntry>()

    @After
    fun tearDown() {
        FestiCoreLogger.removeAllSinks()
        FestiCoreLogger.minimumLevel = LogLevel.DEBUG
        received.clear()
    }

    @Test
    fun `외부 출력 대상에는 INFO 이상만 보낸다`() {
        FestiCoreLogger.addSink { received += it }

        FestiCoreLogger.debug("디버그", LogCategory.APP)
        FestiCoreLogger.info("정보", LogCategory.NETWORK)
        FestiCoreLogger.error("오류", LogCategory.AUTH)

        assertEquals(listOf(LogLevel.INFO, LogLevel.ERROR), received.map { it.level })
        assertEquals(LogCategory.NETWORK, received.first().category)
    }

    @Test
    fun `최소 단계보다 낮은 로그는 버린다`() {
        FestiCoreLogger.minimumLevel = LogLevel.NOTICE
        FestiCoreLogger.addSink { received += it }

        FestiCoreLogger.info("정보", LogCategory.APP)
        FestiCoreLogger.fault("치명", LogCategory.APP)

        assertEquals(listOf(LogLevel.FAULT), received.map { it.level })
    }
}
