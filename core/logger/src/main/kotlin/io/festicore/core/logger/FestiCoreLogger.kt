package io.festicore.core.logger

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/** iOS `FestiCoreLogger`와 같은 단계 · 분류를 쓴다. 토큰 · 개인정보는 메시지에 넣지 않는다. */
object FestiCoreLogger {
    /** 앱이 시작할 때 정한다. 디버그 빌드는 DEBUG, 릴리스는 NOTICE 이상만 남긴다. */
    @Volatile
    var minimumLevel: FestiCoreLogLevel = FestiCoreLogLevel.DEBUG

    /** 외부 출력 대상(Sentry 등)에는 INFO 이상만 보낸다. */
    val sinkMinimumLevel: FestiCoreLogLevel = FestiCoreLogLevel.INFO

    private val sinks = CopyOnWriteArrayList<FestiCoreLogSink>()

    fun addSink(sink: FestiCoreLogSink) {
        sinks.addIfAbsent(sink)
    }

    internal fun removeAllSinks() {
        sinks.clear()
    }

    fun debug(message: String, category: FestiCoreLogCategory) = emit(FestiCoreLogLevel.DEBUG, message, category)

    fun info(message: String, category: FestiCoreLogCategory) = emit(FestiCoreLogLevel.INFO, message, category)

    fun notice(message: String, category: FestiCoreLogCategory) = emit(FestiCoreLogLevel.NOTICE, message, category)

    fun error(message: String, category: FestiCoreLogCategory) = emit(FestiCoreLogLevel.ERROR, message, category)

    fun fault(message: String, category: FestiCoreLogCategory) = emit(FestiCoreLogLevel.FAULT, message, category)

    private fun emit(
        level: FestiCoreLogLevel,
        message: String,
        category: FestiCoreLogCategory,
    ) {
        if (level < minimumLevel) return
        Log.println(level.priority, "FestiCore/${category.name.lowercase()}", message)
        if (level < sinkMinimumLevel) return
        val entry = FestiCoreLogEntry(level, category, message)
        sinks.forEach { runCatching { it.send(entry) } }
    }
}
