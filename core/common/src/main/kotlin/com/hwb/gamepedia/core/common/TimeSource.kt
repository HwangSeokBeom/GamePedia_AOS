package com.hwb.gamepedia.core.common

/** Injectable wall-clock so cache expiry is testable. */
fun interface TimeSource {
    fun nowMillis(): Long

    companion object {
        val SYSTEM: TimeSource = TimeSource { System.currentTimeMillis() }
    }
}
