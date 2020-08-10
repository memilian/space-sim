package nebulae.rendering.renderers

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import ktx.assets.disposeSafely
import ktx.math.times
import nebulae.data.*
import nebulae.geom.BoxToSphereModifier
import nebulae.geom.NoiseModifier
import nebulae.geom.createQuadSphere
import nebulae.geom.getModelFromHeMesh
import nebulae.kutils.plus
import nebulae.kutils.smoothstep
import nebulae.rendering.shaders.PlanetShader
import nebulae.screens.ViewMode
import nebulae.selection.Selection
import kotlin.math.max
import kotlin.math.min
import java.lang.IllegalStateException
import java.lang.Float.max as floatMax


class PlanetRenderer(private val modelBatch: ModelBatch) : IRenderer {

    var viewMode: ViewMode = ViewMode.GALAXY

    private val planetMap = mutableMapOf<Planet, ModelInstance>()
    private val baseQuadSphere = createQuadSphere(5)
    private val boxToSphereModifier: BoxToSphereModifier = BoxToSphereModifier()
    private val noiseModifier: NoiseModifier = NoiseModifier()
    private fun buildPlanetModels() {
        if (selection == null) {
            throw IllegalStateException("selection cannot be null when switching to system view")
        }
        val sel = selection!!
        planetMap.forEach {
            it.value.model.disposeSafely()
        }
        if (sel.item.planets.isEmpty()) {
            return
        }
        if (planetMap.containsKey(sel.item.planets[0])) {
            return
        }
        planetMap.clear()
        for (planet in sel.item.planets) {
            val mesh = baseQuadSphere.copy()
            noiseModifier.seed = (planet.bodyInfos.seed * 100000000).toInt()
            noiseModifier.scale = planet.bodyInfos.radius * KM_TO_EARTH
            mesh.modify(noiseModifier)
            planetMap[planet] = ModelInstance(getModelFromHeMesh(mesh))
        }
    }

    var selection: Selection<System>? = null
        set(value) {
            field = value
//            if (value != null) {
//                buildPlanetModels()
//            }
        }

    private val planetShader = PlanetShader()
    private lateinit var sphereInstance: ModelInstance
    private val builder = ModelBuilder()
    private val tmp = Vector3()
    private val tmp2 = Vector3()
    private val tmp3 = Vector3()
    private val tmp4 = Vector3()
    private val tmpMat = Matrix4()

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        planetShader.init()
        val planetModel = builder.createSphere(1f, 1f, 1f, 90, 90, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())

        sphereInstance = ModelInstance(planetModel)
    }

    override fun renderToScreen(camera: Camera) {
        if (selection == null) {
            return
        }
        val planets = selection!!.item.planets.toMutableList()
        planets.sortByDescending { it.bodyInfos.radius }
        for (planet in planets) {
            val sphereModel = sphereInstance

            val orbitingBody = planet.bodyInfos.orbitalParameters.orbitingBody!!
            val isMoon = planet.isMoon()

            val systemPos = selection!!.item.position
            val dst = systemPos.dst(camera.position)
            val radius = planet.bodyInfos.radius * KM_TO_AU
            val minScale = 1.0
            val scale = when (viewMode) {
                ViewMode.GALAXY -> max(minScale * 0.1, min(radius * 0.1, 1.0))
                ViewMode.SYSTEM -> max(minScale, radius * AU_TO_SYSTEM)
            }.toFloat()
            sphereModel.userData = planet
            val parentPos = tmp3.set(orbitingBody.position)
            val pos = tmp4.set(parentPos).add(planet.position)
            if (viewMode == ViewMode.GALAXY) {
                tmp.set(selection!!.item.position)

                var sc = min(dst.smoothstep(10f, 5f), TimeUtils.timeSinceMillis(selection!!.selectionTimer).toFloat().smoothstep(0f, 100f))
                if (isMoon) {
                    sc *= 0.1f
                }
                sphereModel.transform = tmpMat.idt().translate(tmp + pos * 0.225f).scale(scale * sc, scale * sc, scale * sc)
            } else {
                var sc = floatMax(scale, tmp.set(camera.position).dst(planet.position) / 80f)
                if (isMoon) {
                    val parentScale = floatMax(scale, tmp.set(camera.position).dst(parentPos) / 80f)
                    val ratioToParent = planet.bodyInfos.radius / orbitingBody.bodyInfos.radius
                    sc = parentScale * ratioToParent.toFloat()
                }
                sphereModel.transform = tmpMat.idt().translate(parentPos.add(planet.position)).scale(sc, sc, sc)
            }
            sphereModel.userData = planet
            modelBatch.begin(camera)
            modelBatch.render(sphereModel, planetShader)
            modelBatch.end()

//            if (viewMode == ViewMode.SYSTEM) { // render upscaled sphere so that planets are visible when zoomed out
//                modelBatch.begin(camera)
//                val sc = (tmp.set(camera.position).dst(planet.position)) / 80
//                sphereModel.transform = tmpMat.idt().translate(planet.position).scale(sc, sc, sc)
//                modelBatch.render(sphereModel)
//                modelBatch.end()
//            }
        }
    }

    override fun dispose() {
        planetMap.forEach {
            it.value.model.disposeSafely()
        }
        planetMap.clear()
        sphereInstance.model.disposeSafely()
        planetShader.disposeSafely()
    }
}
