package io.embodit.memorypalace.ui.palace.scene

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Vec3
import io.embodit.memorypalace.platform.PalaceLog
import io.embodit.memorypalace.ui.palace.ModelUi
import io.embodit.memorypalace.ui.palace.PalaceEvent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where a memory object stands: the centre of its [size] cube, facing [yaw] (radians) plus its own turn. */
internal data class ModelSpot(val at: Vec3, val yaw: Float, val size: Float)

/**
 * The 3D memory objects of the current palace (design `userContent.itemTypes.model`): each picked .glb /
 * .usdz is loaded from the palace's media folder (`Entity.loadSuspend("file://…")`, SDK resource-management
 * doc, main thread), measured at run time (its visual bounds; skinned models need it) and scaled to fit a
 * 0.30 m cube beside its plaque, or a smaller one over its card. A pinch on it selects it; the stick turns
 * the selected one about the vertical (design D1) and its yaw goes back to the ViewModel when the stick
 * rests. A freshly picked model is loaded once as a preview to tell S37 from S38. Main thread only.
 */
internal class ModelViews(
    private val root: Entity,
    private val meshes: PalaceMeshes,
    private val m: PalaceMaterials,
    private val scope: CoroutineScope,
    private val sink: (PalaceEvent) -> Unit,
) {
    private inner class View(val key: String, val file: String) {
        val pivot = Entity()
        val ring = Box(root, meshes.ring, m.amber, "modelRing:$key").also { it.show(false) }
        private val collider = Entity()
        var fit = 0f
        var yawDeg = 0f
        var turned = false

        /** The yaw sent to the ViewModel, kept until the state carries it back. */
        var sent: Float? = null
        var failed = false

        init {
            runCatching { pivot.setName("model:$key") }
            root.addChild(pivot)
            pivot.enabled = false
            scope.launch { load() }
        }

        private suspend fun load() {
            val result = runCatching { Entity.loadSuspend("file://$file") }
            val body = result.getOrNull()
            if (body == null || !alive) {
                failed = true
                PalaceLog.w("MODEL load failed $file: ${result.exceptionOrNull()}")
                onLoaded(this, false)
                return
            }
            val box = runCatching { body.getVisualBounds(body) }.getOrNull()
            val half = box?.halfExtent?.domain()
            val maxDim = half?.let { 2f * maxOf(it.x, it.y, it.z) }?.takeIf { it.isFinite() && it > 1e-5f } ?: 1f
            val center = box?.center?.domain() ?: Vec3.ZERO
            // body inside an offset entity so the pivot is the centre of the visual bounds
            val inner = Entity()
            pivot.addChild(inner)
            inner.setLocal(position = center * -1f)
            inner.addChild(body)
            runCatching { collider.setName("modelHit:$key") }
            collider.components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createBox(Vector3(maxDim, maxDim, maxDim))), physicsMaterial = PhysicsMaterialResource()))
            collider.components.set(InteractableComponent())
            runCatching { collider.components.set(HoverEffectComponent()) }
            pivot.addChild(collider)
            fit = 1f / maxDim
            PalaceLog.i("MODEL loaded $key size=${PalaceLog.pos(half?.let { it * 2f })} fit=${"%.3f".format(fit)}")
            onLoaded(this, true)
        }

        fun place(spot: ModelSpot?, selected: Boolean) {
            if (spot == null || fit == 0f) {
                pivot.enabled = false
                ring.show(false)
                return
            }
            if (!pivot.enabled) PalaceLog.i("MODEL show $key at=${PalaceLog.pos(spot.at)} size=${"%.2f".format(spot.size)} world=${PalaceLog.pos(runCatching { pivot.convertPositionTo(Vector3.ZERO, null).domain() }.getOrNull())}")
            pivot.enabled = true
            val rot = Quat4.axisAngle(Vec3.UP, spot.yaw + Math.toRadians(yawDeg.toDouble()).toFloat())
            pivot.setLocal(position = spot.at, rotation = rot, scale = Vec3(spot.size * fit, spot.size * fit, spot.size * fit))
            if (selected) ring.place(spot.at - Vec3.UP * (spot.size / 2f - 0.004f), Vec3(spot.size * 1.4f, spot.size * 1.4f, 1f), Quat4.fromTo(Vec3(0f, 0f, 1f), Vec3.UP)) else ring.show(false)
        }

        fun owns(e: Entity): Boolean = e.id == collider.id || e.id == pivot.id

        fun destroy() {
            runCatching { pivot.destroy(true) }
            ring.destroy()
        }
    }

    private var alive = true
    private val views = HashMap<String, View>()
    private var preview: View? = null

    private fun onLoaded(v: View, ok: Boolean) {
        if (!alive) return
        if (v === preview) sink(PalaceEvent.ModelLoaded(v.file, ok, heavy = File(v.file).length() > HEAVY_BYTES))
    }

    /** Syncs the objects with [list]; [spotOf] says where each stands (null: not now). */
    fun frame(list: List<ModelUi>, selected: String?, pending: String?, spotOf: (ModelUi) -> ModelSpot?) {
        val ids = list.map { it.nodeId }.toSet()
        for (k in views.keys.filter { it !in ids }) views.remove(k)?.destroy()
        for (mu in list) {
            var v = views[mu.nodeId]
            if (v != null && v.file != mu.file) {
                v.destroy()
                v = null
            }
            if (v == null) v = View(mu.nodeId, mu.file).also { views[mu.nodeId] = it }
            if (v.sent != null && kotlin.math.abs(mu.yawDeg - v.sent!!) < 0.01f) v.sent = null
            if (!v.turned && v.sent == null) v.yawDeg = mu.yawDeg
            v.place(if (mu.shown) spotOf(mu) else null, selected == mu.nodeId)
        }
    }

    /** The S37 preview of a picked model (null: none any more). */
    fun preview(file: String?, spotOf: () -> ModelSpot?) {
        val p = preview
        if (file == null) {
            if (p != null) {
                p.destroy()
                preview = null
            }
            return
        }
        val v = if (p == null || p.file != file) View("preview", file).also { p?.destroy(); preview = it } else p
        v.place(spotOf(), selected = true)
    }

    /** The stick turns the selected object by [deltaDeg]; at rest a changed yaw is sent once. */
    fun turn(nodeId: String, deltaDeg: Float) {
        val v = views[nodeId] ?: return
        if (deltaDeg != 0f) {
            v.yawDeg = ((v.yawDeg + deltaDeg) % 360f + 360f) % 360f
            v.turned = true
        } else if (v.turned) {
            v.turned = false
            v.sent = v.yawDeg
            sink(PalaceEvent.ModelTurned(nodeId, v.yawDeg))
        }
    }

    fun owner(e: Entity): String? = views.entries.firstOrNull { it.value.owns(e) }?.key

    fun yawOf(nodeId: String): Float? = views[nodeId]?.yawDeg

    fun destroy() {
        alive = false
        views.values.forEach { it.destroy() }
        views.clear()
        preview?.destroy()
        preview = null
    }

    companion object {
        /** Beside a plaque (design: 0.30 m cube); over a card. */
        const val BESIDE_M = 0.30f
        const val ON_CARD_M = 0.12f
        const val HEAVY_BYTES = 10L * 1024 * 1024
    }
}
