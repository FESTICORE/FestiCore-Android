package io.festicore.core.logger

import android.util.Log

enum class LogLevel(val priority: Int) {
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    NOTICE(Log.WARN),
    ERROR(Log.ERROR),
    FAULT(Log.ASSERT),
}
