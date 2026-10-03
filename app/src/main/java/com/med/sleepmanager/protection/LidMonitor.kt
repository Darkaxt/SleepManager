package com.med.sleepmanager.protection

import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class LidMonitor(
    private val onClosed: () -> Unit,
    private val onOpened: () -> Unit,
    private val onError: (Throwable) -> Unit = {}
) {
    @Volatile
    private var running = false

    private var stream: FileInputStream? = null
    private var thread: Thread? = null

    fun start(): Boolean {
        if (running) return true
        val devicePath = findLidDevice()?.eventPath ?: return false

        running = true
        thread = Thread {
            try {
                val input = FileInputStream(devicePath)
                stream = input

                readCurrentLidClosed(devicePath)?.let { closed ->
                    if (closed) onClosed() else onOpened()
                }

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

                    if (type == 0x05 && code == 0x00) {
                        when (value) {
                            1 -> onClosed()
                            0 -> onOpened()
                        }
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
            name = "SleepManagerLid"
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

    data class LidDevice(
        val eventPath: String,
        val name: String,
        val detection: String
    )

    companion object {
        private const val SW_LID_MASK = 0x1L

        fun findLidDevice(): LidDevice? {
            val eventDirs =
                try {
                    File("/sys/class/input")
                        .listFiles()
                        ?.asSequence()
                        ?.filter { it.name.startsWith("event") }
                        ?.toList()
                        .orEmpty()
                } catch (_: Throwable) {
                    emptyList()
                }

            eventDirs.firstOrNull { eventDir ->
                readInputName(eventDir) == "hall_switch"
            }?.let { eventDir ->
                return LidDevice(
                    eventPath = "/dev/input/${eventDir.name}",
                    name = "hall_switch",
                    detection = "hall_switch"
                )
            }

            eventDirs.firstOrNull { eventDir ->
                hasSwLidCapability(eventDir)
            }?.let { eventDir ->
                return LidDevice(
                    eventPath = "/dev/input/${eventDir.name}",
                    name = readInputName(eventDir) ?: eventDir.name,
                    detection = "SW_LID"
                )
            }

            return null
        }

        fun findHallDevicePath(): String? = findLidDevice()?.eventPath

        fun readCurrentLidClosed(
            devicePath: String? = findLidDevice()?.eventPath
        ): Boolean? {
            val resolvedPath = devicePath ?: return null
            val getevent = File("/system/bin/getevent")
            if (!getevent.canExecute()) return null

            return runCatching {
                val process = ProcessBuilder(
                    getevent.absolutePath,
                    "-S",
                    resolvedPath
                )
                    .redirectErrorStream(true)
                    .start()

                if (!process.waitFor(750L, TimeUnit.MILLISECONDS)) {
                    process.destroy()
                    return@runCatching null
                }

                if (process.exitValue() != 0) {
                    return@runCatching null
                }

                val output = process.inputStream.bufferedReader().use { it.readText() }
                val token =
                    Regex("""(?i)\b[0-9a-f]{4,}\b""")
                        .find(output)
                        ?.value
                        ?: return@runCatching null

                val switchMask = token.toLong(16)
                (switchMask and SW_LID_MASK) != 0L
            }.getOrNull()
        }

        fun isSupported(): Boolean = findLidDevice() != null

        fun detectionDescription(): String {
            val device = findLidDevice() ?: return "none"
            return "${device.detection} • ${device.name} • ${device.eventPath}"
        }

        private fun readInputName(eventDir: File): String? =
            runCatching {
                File(eventDir, "device/name").readText().trim()
            }.getOrNull()

        private fun hasSwLidCapability(eventDir: File): Boolean =
            runCatching {
                val raw =
                    File(eventDir, "device/capabilities/sw")
                        .readText()
                        .trim()
                if (raw.isEmpty()) return@runCatching false

                val leastSignificantWord =
                    raw.split(Regex("""\s+"""))
                        .last()
                        .toLong(16)

                (leastSignificantWord and SW_LID_MASK) != 0L
            }.getOrDefault(false)
    }
}
