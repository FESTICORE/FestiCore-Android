package io.festicore.core.logger

data class FestiCoreLogEntry(
    val level: FestiCoreLogLevel,
    val category: FestiCoreLogCategory,
    val message: String,
)

fun interface FestiCoreLogSink {
    fun send(entry: FestiCoreLogEntry)
}
