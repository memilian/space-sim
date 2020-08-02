package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Gdx.gl20
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils.log
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import ktx.assets.disposeSafely
import ktx.math.div
import ktx.math.times
import nebulae.data.*
import nebulae.kutils.minus
import nebulae.kutils.plus
import nebulae.kutils.smoothstep
import nebulae.rendering.shaders.PlanetShader
import nebulae.rendering.shaders.StarShader
import nebulae.screens.ViewMode
import nebulae.selection.Selection
import java.lang.Float.max
import kotlin.math.max
import kotlin.math.min

class PlanetRenderer(private val modelBatch: ModelBatch) : IRenderer {

    lateinit var viewMode: ViewMode
    var selection: Selection<System>? = null

    private val planetShader = PlanetShader()
    private var modelInstances = mutableListOf<ModelInstance>()
    private val builder = ModelBuilder()
    private val tmp = Vector3()
    private val tmp2 = Vector3()
    private val tmpMat = Matrix4()

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        planetShader.init()
        val planetModel = builder.createSphere(1f, 1f, 1f, 90, 90, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
        for (i in 0 until 25) {
            modelInstances.add(ModelInstance(planetModel))
        }
    }

    override fun renderToScreen(camera: Camera) {
        if (selection == null || modelInstances.isEmpty()) {
            return
        }
        val planets = selection!!.item.planets.toMutableList()
        planets.sortByDescending { it.bodyInfos.radius }
        for ((index, planet) in planets.withIndex()) {
            val model = modelInstances[index]
            if (index >= planets.size) {
                println("[Error] not enough model instances");
                break
            }
            val systemPos = selection!!.item.position
            val dst = systemPos.dst(camera.position)
            val radius = planet.bodyInfos.radius * KM_TO_AU
            val scale = when (viewMode) {
                ViewMode.GALAXY -> max(0.2, min(radius * 0.1, 1.0))
                ViewMode.SYSTEM -> max(1.0, radius * AU_TO_SYSTEM)
            }.toFloat()
            model.userData = planet
            if (viewMode == ViewMode.GALAXY) {
                tmp.set(selection!!.item.position)
                tmp2.set(planet.position)

                val sc = min(dst.smoothstep(10f, 5f), TimeUtils.timeSinceMillis(selection!!.selectionTimer).toFloat().smoothstep(0f, 100f))
                model.transform = tmpMat.idt().translate(tmp + tmp2 * 0.125f).scale(scale * sc, scale * sc, scale * sc)
            } else {
                model.transform = tmpMat.idt().translate(planet.position).scale(scale, scale, scale)
            }
            model.userData = planet;
            modelBatch.begin(camera)
            modelBatch.render(model, planetShader)
            modelBatch.end()
            if (viewMode == ViewMode.SYSTEM) { // render upscaled sphere so that planets are visible when zoomed out
                modelBatch.begin(camera)
                val sc = (tmp.set(camera.position).dst(planet.position)) / 80
                model.transform = tmpMat.idt().translate(planet.position).scale(sc, sc, sc)
                modelBatch.render(model)
                modelBatch.end()
            }
        }
    }

    override fun dispose() {
        for (model in modelInstances) {
            model.model.disposeSafely()
        }
        modelInstances.clear()
        planetShader.disposeSafely()
    }
}
