package io.embodit.memorypalace.platform

import android.util.Log
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Vec3
import java.util.Locale

/**
 * One line per event under the tag `MemoryPalace`, with stable prefixes the QA scripts grep for:
 * `STATE`, `PLANES`, `LOCUS`, `WAKE`, `TAP`, `ANCHOR`, `PANEL`, `RATE`, `RECALL`, `IMPORT`, `CMD`, `DUMP`, `POSE`, ...
 */
object PalaceLog {
    const val TAG = "MemoryPalace"

    fun i(line: String) {
        Log.i(TAG, line)
    }

    fun w(line: String, error: Throwable? = null) {
        Log.w(TAG, line, error)
    }

    fun e(line: String, error: Throwable? = null) {
        Log.e(TAG, line, error)
    }

    fun pos(v: Vec3?): String = if (v == null) "null" else String.format(Locale.US, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z)

    fun quat(q: Quat4?): String = if (q == null) "null" else String.format(Locale.US, "q(%.4f, %.4f, %.4f, %.4f)", q.x, q.y, q.z, q.w)
}
