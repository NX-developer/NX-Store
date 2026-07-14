package com.nxteam.nxstore.util

import java.util.Locale

object Format {
    fun size(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }
}
