package io.embodit.memorypalace.ui.palace.scene

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Vec3
import io.embodit.memorypalace.ui.theme.PalaceColors
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// domain <-> SDK types. The scene works in Stage metres: every entity hangs below the calibrated
// stage root, whose local space equals Stage space.
// ---------------------------------------------------------------------------------------------

internal fun Vec3.sdk(): Vector3 = Vector3(x, y, z)

internal fun Vector3.domain(): Vec3 = Vec3(x, y, z)

internal fun Quat4.sdk(): Quat = Quat(x, y, z, w)

internal fun Quat.domain(): Quat4 = Quat4(x, y, z, w)

internal fun Color.color4(): Color4 = Color4(red, green, blue, alpha)

internal val Entity.transformOrNull: TransformComponent?
    get() = components[TransformComponent::class.java]

internal fun Entity.setLocal(position: Vec3? = null, rotation: Quat4? = null, scale: Vec3? = null) {
    val t = transformOrNull ?: return
    if (position != null) t.setPosition(position.sdk())
    if (rotation != null) t.setQuaternion(rotation.sdk())
    if (scale != null) t.setScaleVector(scale.sdk())
}

internal fun Entity.setMaterial(material: Material) {
    components[ModelComponent::class.java]?.materials?.set(0, material)
}

/** Overall opacity of an entity and its children. */
internal fun Entity.setOpacity(opacity: Float) {
    val existing = components[OpacityControllerComponent::class.java]
    if (existing != null) existing.opacity = opacity.coerceIn(0f, 1f) else components.set(OpacityControllerComponent(opacity.coerceIn(0f, 1f)))
}

/** Metres per dp of an attachment panel at scale 1 (Spatial Ruler feasibility spike). */
internal const val DP_M = 0.0008f

/**
 * Resources shared by entities that come and go. The SDK releases a resource when its last user is
 * destroyed (Spatial Ruler: deleting one pin closed the materials of all pins), so shared ones are made
 * global and closed when the scene goes away.
 */
internal class SharedResources {
    private val owned = ArrayList<Resource>()

    fun <T : Resource> keep(resource: T): T {
        resource.toGlobal()
        owned += resource
        return resource
    }

    fun close() {
        owned.forEach { runCatching { it.close() } }
        owned.clear()
    }
}

/**
 * Scene-lifetime materials (design `palette`), one per look. Unlit colours are exact only with tone
 * mapping off (Spatial Ruler); the lantern dots are unlit so they glow over passthrough. The plaques
 * are lit porcelain so they sit in the room's light.
 */
internal class PalaceMaterials(private val shared: SharedResources) {
    val amber: Material = unlit(PalaceColors.Amber)
    val jade: Material = unlit(PalaceColors.Jade)
    val coral: Material = unlit(PalaceColors.Coral)
    val dim: Material = unlit(PalaceColors.Dim)
    val threadFuture: Material = sheer(PalaceColors.Amber, 0.25f)
    val threadDim: Material = sheer(PalaceColors.White, 0.18f)
    val halo: Material = sheer(PalaceColors.Amber, 0.35f)
    val porcelain: Material = lit(PalaceColors.Porcelain, 0.55f)
    val brass: Material = lit(PalaceColors.Brass, 0.35f, metallic = 0.8f)
    val shadow: Material = sheer(Color.Black, 0.22f)
    val deck: List<Material> = PalaceColors.Deck.map { lit(it, 0.6f) }

    fun deck(i: Int): Material = deck[((i % deck.size) + deck.size) % deck.size]

    private fun unlit(color: Color): Material =
        shared.keep(
            UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(color.color4())
                runCatching { setApplyToneMapping(false) }
            }
        )

    /** See-through: the colour is opaque, the material's opacity carries the share (Spatial Ruler). */
    private fun sheer(color: Color, opacity: Float): Material =
        shared.keep(
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(color.copy(alpha = 1f).color4())
                setOpacity(opacity)
                runCatching { setApplyToneMapping(false) }
                setCullingMode(MaterialCullingMode.NONE)
                setDepthWrite(false)
            }
        )

    private fun lit(color: Color, roughness: Float, metallic: Float = 0f): Material =
        shared.keep(
            PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(color.color4())
                setMetallic(metallic)
                setRoughness(roughness)
                // a share of its own colour keeps it from going dull under the room light
                setEmissiveColor(Color4(color.red * 0.3f, color.green * 0.3f, color.blue * 0.3f, 1f))
            }
        )
}

/** Programmatic meshes, built once per scene (creating a mesh costs about one rendered frame). */
internal object MeshFactory {
    /** A unit cube centred on the origin (scaled to any box). */
    fun unitCube(name: String): MeshResource {
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val indices = ArrayList<Int>()
        val faces = listOf(Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f))
        for (n in faces) {
            // two in-plane axes with u x v = n, so the quad winds counter-clockwise seen from outside
            val u = (if (kotlin.math.abs(n.y) > 0.5f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)).rejectFrom(n).normalized()
            val v = n.cross(u)
            val base = positions.size
            for ((a, b) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                val p = (n + u * a + v * b) * 0.5f
                positions += Vector3(p.x, p.y, p.z)
                normals += n.sdk()
            }
            indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
        }
        val model = MeshModel(positions = positions, triangleIndices = indices, normals = normals)
        return MeshResource.createWithMeshModel(model = model, bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(0.5f, 0.5f, 0.5f)), name = name)
    }

    /** A unit-diameter UV sphere. */
    fun unitSphere(name: String, rings: Int = 12, segments: Int = 18): MeshResource {
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val indices = ArrayList<Int>()
        for (r in 0..rings) {
            val phi = Math.PI * r / rings
            for (s in 0..segments) {
                val th = 2 * Math.PI * s / segments
                val n = Vec3((sin(phi) * cos(th)).toFloat(), cos(phi).toFloat(), (sin(phi) * sin(th)).toFloat())
                positions += (n * 0.5f).sdk()
                normals += n.sdk()
            }
        }
        val w = segments + 1
        for (r in 0 until rings) for (s in 0 until segments) {
            val a = r * w + s
            val b = a + w
            indices += listOf(a, a + 1, b, a + 1, b + 1, b)
        }
        val model = MeshModel(positions = positions, triangleIndices = indices, normals = normals)
        return MeshResource.createWithMeshModel(model = model, bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(0.5f, 0.5f, 0.5f)), name = name)
    }

    /** A flat ring in local XY facing +Z, outer diameter 1, two-sided by material. */
    fun unitRing(name: String, inner: Float = 0.38f, segments: Int = 40): MeshResource {
        val positions = ArrayList<Vector3>()
        val indices = ArrayList<Int>()
        for (s in 0..segments) {
            val th = 2 * Math.PI * s / segments
            val c = cos(th).toFloat()
            val sn = sin(th).toFloat()
            positions += Vector3(c * 0.5f, sn * 0.5f, 0f)
            positions += Vector3(c * inner, sn * inner, 0f)
        }
        for (s in 0 until segments) {
            val a = s * 2
            indices += listOf(a, a + 2, a + 1, a + 1, a + 2, a + 3)
        }
        val model = MeshModel(positions = positions, triangleIndices = indices, normals = List(positions.size) { Vector3(0f, 0f, 1f) })
        return MeshResource.createWithMeshModel(model = model, bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(0.5f, 0.5f, 0.002f)), name = name)
    }
}

/** The shared meshes of the scene. */
internal class PalaceMeshes(shared: SharedResources) {
    val cube: MeshResource = shared.keep(MeshFactory.unitCube("unitCube"))
    val sphere: MeshResource = shared.keep(MeshFactory.unitSphere("unitSphere"))
    val ring: MeshResource = shared.keep(MeshFactory.unitRing("unitRing"))
}

/** A model entity on a shared unit mesh: placed by centre, size and rotation. */
internal class Box(parent: Entity, mesh: MeshResource, material: Material, name: String) {
    val entity: Entity = ModelEntity(mesh, material)
    private var material: Material = material
    private var on = true

    init {
        runCatching { entity.setName(name) }
        parent.addChild(entity)
    }

    fun place(center: Vec3, size: Vec3, rotation: Quat4 = Quat4.IDENTITY) {
        entity.setLocal(position = center, rotation = rotation, scale = size)
        show(true)
    }

    fun material(m: Material) {
        if (m === material) return
        material = m
        entity.setMaterial(m)
    }

    fun show(visible: Boolean) {
        if (visible != on) {
            entity.enabled = visible
            on = visible
        }
    }

    /** A rod of square section [thick] from [a] to [b] (the thread). */
    fun rod(a: Vec3, b: Vec3, thick: Float) {
        val d = b - a
        val len = d.length
        if (len < 1e-4f) {
            show(false)
            return
        }
        place((a + b) * 0.5f, Vec3(thick, thick, len), Quat4.fromTo(Vec3(0f, 0f, 1f), d / len))
    }

    fun destroy() {
        runCatching { entity.destroy(true) }
    }
}
