package com.med.sleepmanager.protection

import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PmicPowerButtonMonitor(
    private val onPressed: () -> Unit,
    private val onError: (Throwable) -> Unit = {}
) {
    @Volatile
    private var running = false

    private var stream: FileInputStream? = null
    private var thread: Thread? = null

    fun start(): Boolean {
        if (running) return true
        val devicePath = findPowerButtonDevicePath() ?: return false

        running = true
        thread = Thread {
            try {
                val input = FileInputStream(devicePath)
                stream = input

                val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
                val eventSize = if (is64Bit) 24 else 16
                val payloadOffset = if (is64Bit) 16 else 8
                val buffer = ByteArray(eventSize)

                while (running) {
                    var offset = 0
                    while (offset < eventSize && running) {
                        val count = input.read(buffer, offset, eventSize - offset)
                        if (count < 0) throw IllegalStateException("Unexpected EOF")
                        offset += count
                    }
                    if (!running) break

                    val event = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                    event.position(payloadOffset)

                    val type = event.short.toInt() and 0xffff
                    val code = event.short.toInt() and 0xffff
                    val value = event.int

                    if (type == EV_KEY && code == KEY_POWER && value == KEY_DOWN) {
                        onPressed()
                    }
                }
            } catch (t: Throwable) {
                if (running) onError(t)
            } finally {
                try {
                    stream?.close()
                } catch (_: Throwable) {
                }
                stream = null
                running = false
            }
        }.apply {
            name = "SleepManagerPmicPower"
            isDaemon = true
            start()
        }

        return true
    }

    fun stop() {
        running = false
        try {
            stream?.close()
        } catch (_: Throwable) {
        }
        stream = null
        thread = null
    }

    companion object {
        private const val EV_KEY = 0x01
        private const val KEY_POWER = 116
        private const val KEY_DOWN = 1

        fun findPowerButtonDevicePath(): String? {
            return try {
                File("/sys/class/input")
                    .listFiles()
                    ?.asSequence()
                    ?.filter { eventDir -> eventDir.name.startsWith("event") }
                    ?.firstOrNull { eventDir ->
                        runCatching {
                            File(eventDir, "device/name").readText().trim() == "pmic_pwrkey"
                        }.getOrDefault(false)
                    }
                    ?.let { eventDir -> "/dev/input/" + eventDir.name }
            } catch (_: Throwable) {
                null
            }
        }

        fun isSupported(): Boolean = findPowerButtonDevicePath() != null
    }
}
