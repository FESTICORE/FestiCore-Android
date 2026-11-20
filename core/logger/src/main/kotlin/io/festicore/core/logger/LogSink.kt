package io.festicore.core.logger

data class LogEntry(
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
)

fun interface LogSink {
    fun send(entry: LogEntry)
}
