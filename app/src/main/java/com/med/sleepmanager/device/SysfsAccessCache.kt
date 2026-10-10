package com.med.sleepmanager.device

import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local cache for sysfs capabilities.
 *
 * Some handheld firmware exposes sysfs nodes to shell/root but denies the
 * untrusted app domain through SELinux. Re-probing those paths on every UI
 * refresh creates repeated AVCs and unnecessary I/O, so paths proven
 * unreadable are skipped until the app process restarts.
 */
object SysfsAccessCache {
    private const val INPUT_CLASS_PATH = "/sys/class/input"
    private const val BATTERY_FULL_PATH =
        "/sys/class/power_supply/battery/charge_full"
    private const val BATTERY_DESIGN_PATH =
        "/sys/class/power_supply/battery/charge_full_design"

    private val blockedPaths =
        ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var inputProbeComplete = false

    @Volatile
    private var cachedInputEventDirectories: List<File> = emptyList()

    fun readPositiveLong(path: String): Long? {
        if (blockedPaths.contains(path)) return null

        return try {
            val file = File(path)
            if (!file.canRead()) {
                blockedPaths.add(path)
                null
            } else {
                file.readText()
                    .trim()
                    .toLongOrNull()
                    ?.takeIf { it > 0L }
            }
        } catch (_: SecurityException) {
            blockedPaths.add(path)
            null
        } catch (_: FileNotFoundException) {
            blockedPaths.add(path)
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun inputEventDirectories(): List<File> {
        if (inputProbeComplete) {
            return cachedInputEventDirectories
        }

        synchronized(this) {
            if (inputProbeComplete) {
                return cachedInputEventDirectories
            }

            val directories =
                try {
                    File(INPUT_CLASS_PATH)
                        .listFiles()
                        ?.filter { it.name.startsWith("event") }
                } catch (_: SecurityException) {
                    null
                } catch (_: Throwable) {
                    null
                }

            if (directories == null) {
                blockedPaths.add(INPUT_CLASS_PATH)
                cachedInputEventDirectories = emptyList()
            } else {
                cachedInputEventDirectories = directories
            }
            inputProbeComplete = true
            return cachedInputEventDirectories
        }
    }

    fun batteryCapacityAccessSummary(): String {
        val blocked =
            listOf(BATTERY_FULL_PATH, BATTERY_DESIGN_PATH)
                .filter(blockedPaths::contains)

        return if (blocked.isEmpty()) {
            "available or not blocked"
        } else {
            "blocked/unreadable by app (firmware/SELinux restriction likely): " +
                blocked.joinToString { it.substringAfterLast('/') }
        }
    }

    fun inputAccessSummary(): String =
        if (blockedPaths.contains(INPUT_CLASS_PATH)) {
            "blocked/unreadable by app (firmware/SELinux restriction likely)"
        } else {
            "available or not blocked"
        }
}
