package io.embodit.memorypalace.platform

import android.os.SystemClock
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import io.embodit.memorypalace.domain.HeadPose
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Vec3
import kotlin.math.abs

/**
 * The controllers' thumbsticks (factory default D1), ported from Air Sketch / Furniture Fit: X turns the
 * selected 3D memory object about its vertical axis, Y pushes the placing spot out / pulls it in
 * (design `spatialLayout.placement.distance`). `ControllerTrackingProvider`'s action listener delivers in
 * Full Space (SDK doc "Obtain controller input actions"), which this Mixed Stage is. The listener runs on
 * the data source thread: it only stores values; the frame loop reads them. Started and stopped by the scene.
 */
class ControllerStick {
    private var provider: ControllerTrackingProvider? = null

    @Volatile private var x = 0f
    @Volatile private var y = 0f
    @Volatile private var atMs = 0L
    @Volatile var actions = 0L
        private set

    /** Debug: stick values that win over the controllers until [debugUntilMs] (emulator QA). */
    @Volatile private var debugX = 0f
    @Volatile private var debugY = 0f
    @Volatile private var debugUntilMs = 0L

    // the stick pushed further of the two controllers, per axis
    private val listener =
        ControllerTrackingProvider.ControllerActionListener { a ->
            val l = a.left.thumbstickValue
            val r = a.right.thumbstickValue
            if (a.left.triggerValue > 0.5f || abs(l.x) + abs(l.y) > 0.3f) hand = 0
            if (a.right.triggerValue > 0.5f || abs(r.x) + abs(r.y) > 0.3f) hand = 1
            x = if (abs(r.x) >= abs(l.x)) r.x else l.x
            y = if (abs(r.y) >= abs(l.y)) r.y else l.y
            atMs = SystemClock.elapsedRealtime()
            actions++
        }

    fun start() {
        if (provider != null) return
        val p = ControllerTrackingProvider()
        runCatching { p.addControllerActionListener(listener) }.onFailure { PalaceLog.w("STICK listener: $it") }
        val result = runCatching { p.start() }
        PalaceLog.i("STICK start=${result.getOrNull() ?: result.exceptionOrNull()} support=${runCatching { p.supportState }.getOrNull()}")
        provider = p
    }

    fun stop() {
        provider?.let { p ->
            runCatching { p.removeControllerActionListener(listener) }
            runCatching { p.stop() }
        }
        provider = null
        x = 0f
        y = 0f
        actions = 0L
    }

    @Volatile private var hand = 1 // 0 left, 1 right: the controller last used

    /**
     * The pose of the controller last used (stick or trigger), in the tracking frame the head pose uses too;
     * null without a tracked controller. Its forward (-Z) is the ray the reticle previews (design D1).
     */
    fun aim(): HeadPose? {
        val p = provider ?: return null
        if (runCatching { p.supportState }.getOrNull() != DataProvider.SupportState.SUPPORTED) return null
        val data = runCatching { p.latestData }.getOrNull() ?: return null
        val pose = (if (hand == 0) data.left ?: data.right else data.right ?: data.left) ?: return null
        val out = HeadPose(Vec3(pose.position.x, pose.position.y, pose.position.z), Quat4(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w))
        return out.takeIf { it.position.isFinite() && it.position.length > 1e-3f }
    }

    /** Thumbstick X in [-1, 1] (right +); 0 without a controller or when the last report is stale. */
    fun x(nowMs: Long): Float = read(nowMs, debugX) { x }

    /** Thumbstick Y in [-1, 1] (pushed forward +). */
    fun y(nowMs: Long): Float = read(nowMs, debugY) { y }

    private inline fun read(nowMs: Long, debug: Float, value: () -> Float): Float {
        if (nowMs < debugUntilMs) return debug
        val p = provider ?: return 0f
        if (runCatching { p.supportState }.getOrNull() != DataProvider.SupportState.SUPPORTED) return 0f
        if (nowMs - atMs > STALE_MS) return 0f
        return value().coerceIn(-1f, 1f)
    }

    /** Debug `stick`: holds ([dx], [dy]) for [ms]. */
    fun debugHold(dx: Float, dy: Float, ms: Long) {
        debugX = dx.coerceIn(-1f, 1f)
        debugY = dy.coerceIn(-1f, 1f)
        debugUntilMs = SystemClock.elapsedRealtime() + ms
    }

    fun status(): String = "support=${runCatching { provider?.supportState }.getOrNull()} actions=$actions x=${"%.2f".format(x)} y=${"%.2f".format(y)}"

    private companion object {
        const val STALE_MS = 500L
    }
}
