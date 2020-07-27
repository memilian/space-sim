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
import ktx.math.times
import nebulae.data.*
import nebulae.kutils.plus
import nebulae.kutils.smoothstep
import nebulae.rendering.shaders.StarShader
import nebulae.screens.ViewMode
import nebulae.selection.Selection
import java.lang.Float.max
import kotlin.math.min

class StarRenderer(private val modelBatch: ModelBatch) : IRenderer {

    lateinit var viewMode: ViewMode
    var selection: Selection<System>? = null

    private val starShader = StarShader()
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
        starShader.init()
        val starModel = builder.createSphere(1f, 1f, 1f, 90, 90, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
        for (i in 0 until 5) {
            modelInstances.add(ModelInstance(starModel))
        }
    }

    override fun renderToScreen(camera: Camera) {
        if (selection == null || modelInstances.isEmpty()) {
            return
        }
        val stars = selection!!.item.stars.toMutableList()
        stars.sortByDescending { it.bodyInfos.radius }
        for ((index, star) in stars.withIndex()) {
            val model = modelInstances[index]

            modelBatch.begin(camera)
            val systemPos = selection!!.item.position
            val dst = systemPos.dst(camera.position)
            val radius = star.bodyInfos.radius * KM_TO_AU
            val scale = when (viewMode) {
                ViewMode.GALAXY -> max(0.05f, min(radius / (stars[0].bodyInfos.radius * KM_TO_AU), 10f))
                ViewMode.SYSTEM -> radius * AU_TO_SYSTEM
            }
            model.userData = star
            if (viewMode == ViewMode.GALAXY) {
                tmp.set(selection!!.item.position)
                tmp2.set(star.position)

                val sc = min(dst.smoothstep(10f, 5f), TimeUtils.timeSinceMillis(selection!!.selectionTimer).toFloat().smoothstep(0f, 100f))
                model.transform = tmpMat.idt().translate(tmp + tmp2 * 0.1f).scale(scale * sc, scale * sc, scale * sc)
            } else {
                model.transform = tmpMat.idt().translate(star.position).scale(scale, scale, scale)
            }
            modelBatch.render(model, starShader)
            modelBatch.end()
        }
    }

    override fun dispose() {
        for (model in modelInstances) {
            model.model.disposeSafely()
        }
        modelInstances.clear()
        starShader.disposeSafely()
    }
}
