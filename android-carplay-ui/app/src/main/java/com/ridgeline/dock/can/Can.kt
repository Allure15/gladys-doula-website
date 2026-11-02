package com.ridgeline.dock.can

import java.io.ByteArrayOutputStream

class CanFrame(val id: Int, val data: ByteArray, val tsMs: Long) {
    fun hex() = data.joinToString(" ") { "%02X".format(it) }
}

interface FrameParser { fun feed(buf: ByteArray, len: Int, out: (CanFrame) -> Unit) }

/**
 * ASSUMPTION - verify against raw capture: [header][type/id][len][data x len][checksum].
 * Checksum is not validated because its algorithm is unconfirmed. The sniffer's raw-hex
 * pane exists precisely to confirm or correct this framing.
 */
class McuFrameParser(private val header: Int = 0x2E) : FrameParser {
    private var buf = ByteArray(0)
    override fun feed(buf0: ByteArray, len: Int, out: (CanFrame) -> Unit) {
        buf += buf0.copyOf(len)
        while (true) {
            val h = buf.indexOf(header.toByte())
            if (h < 0) { buf = ByteArray(0); return }
            if (h > 0) buf = buf.copyOfRange(h, buf.size)
            if (buf.size < 3) return
            val n = buf[2].toInt() and 0xFF
            val total = 3 + n + 1
            if (n > 64) { buf = buf.copyOfRange(1, buf.size); continue }   // implausible: resync
            if (buf.size < total) return
            out(CanFrame(buf[1].toInt() and 0xFF, buf.copyOfRange(3, 3 + n), System.currentTimeMillis()))
            buf = buf.copyOfRange(total, buf.size)
        }
    }
}

/** ESP32 fallback format, one frame per line: "<hexid>:<hexbytes>\n"  e.g. "1A2:00FF10". */
class LineFrameParser : FrameParser {
    private val sb = ByteArrayOutputStream()
    override fun feed(buf: ByteArray, len: Int, out: (CanFrame) -> Unit) {
        for (i in 0 until len) {
            val b = buf[i].toInt()
            if (b != '\n'.code) { if (b != '\r'.code) sb.write(b); continue }
            val line = sb.toString(Charsets.US_ASCII); sb.reset()
            val p = line.split(':')
            val id = p.getOrNull(0)?.trim()?.toIntOrNull(16) ?: continue
            val hex = p.getOrNull(1)?.trim() ?: continue
            if (hex.length % 2 != 0) continue
            val data = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            out(CanFrame(id, data, System.currentTimeMillis()))
        }
    }
}
