package com.ridgeline.dock.can

/** Finds bits that toggle at blinker cadence (half-period 200-1000 ms, steady) in recent frames. */
class CanAnalyzer {
    class Candidate(val id: Int, val byteIdx: Int, val mask: Int, val halfPeriodMs: Long) {
        override fun toString() = "ID 0x%02X  byte %d  mask 0x%02X  ~%d ms".format(id, byteIdx, mask, halfPeriodMs)
    }

    private class Stat {
        var count = 0; var changes = 0; var last = ByteArray(0)
        val toggles = HashMap<Int, ArrayDeque<Long>>()   // byte*8+bit -> recent toggle times
    }

    private val stats = java.util.TreeMap<Int, Stat>()

    @Synchronized fun clear() = stats.clear()

    @Synchronized fun onFrame(f: CanFrame) {
        val s = stats.getOrPut(f.id) { Stat() }
        s.count++
        if (s.last.size == f.data.size) {
            for (i in f.data.indices) {
                val diff = (s.last[i].toInt() xor f.data[i].toInt()) and 0xFF
                if (diff == 0) continue
                s.changes++
                for (bit in 0..7) if (diff and (1 shl bit) != 0) {
                    val q = s.toggles.getOrPut(i * 8 + bit) { ArrayDeque() }
                    q.addLast(f.tsMs); if (q.size > 16) q.removeFirst()
                }
            }
        }
        s.last = f.data
    }

    @Synchronized fun summary(max: Int = 14): List<String> =
        stats.entries.sortedByDescending { it.value.changes }.take(max).map { (id, s) ->
            "0x%02X n=%-6d chg=%-5d %s".format(id, s.count, s.changes, s.last.joinToString(" ") { "%02X".format(it) })
        }

    @Synchronized fun candidates(now: Long = System.currentTimeMillis()): List<Candidate> {
        val out = ArrayList<Candidate>()
        for ((id, s) in stats) for ((key, q) in s.toggles) {
            val t = q.filter { now - it <= 5000 }
            if (t.size < 5) continue
            val gaps = t.zipWithNext { a, b -> b - a }.sorted()
            val med = gaps[gaps.size / 2]
            if (med in 200..1000 && gaps.last() - gaps.first() <= med / 2)
                out += Candidate(id, key / 8, 1 shl (key % 8), med)
        }
        return out
    }
}
