package io.festicore.core.logger

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/** iOS `FestiCoreLogger`와 같은 단계 · 분류를 쓴다. 토큰 · 개인정보는 메시지에 넣지 않는다. */
object FestiCoreLogger {
    /** 앱이 시작할 때 정한다. 디버그 빌드는 DEBUG, 릴리스는 NOTICE 이상만 남긴다. */
    @Volatile
    var minimumLevel: LogLevel = LogLevel.DEBUG

    /** 외부 출력 대상(Sentry 등)에는 INFO 이상만 보낸다. */
    val sinkMinimumLevel: LogLevel = LogLevel.INFO

    private val sinks = CopyOnWriteArrayList<LogSink>()

    fun addSink(sink: LogSink) {
        sinks += sink
    }

    internal fun removeAllSinks() {
        sinks.clear()
    }

    fun debug(message: String, category: LogCategory) = emit(LogLevel.DEBUG, message, category)

    fun info(message: String, category: LogCategory) = emit(LogLevel.INFO, message, category)

    fun notice(message: String, category: LogCategory) = emit(LogLevel.NOTICE, message, category)

    fun error(message: String, category: LogCategory) = emit(LogLevel.ERROR, message, category)

    fun fault(message: String, category: LogCategory) = emit(LogLevel.FAULT, message, category)

    private fun emit(
        level: LogLevel,
        message: String,
        category: LogCategory,
    ) {
        if (level < minimumLevel) return
        Log.println(level.priority, "FestiCore/${category.name.lowercase()}", message)
        if (level < sinkMinimumLevel) return
        val entry = LogEntry(level, category, message)
        sinks.forEach { it.send(entry) }
    }
}
