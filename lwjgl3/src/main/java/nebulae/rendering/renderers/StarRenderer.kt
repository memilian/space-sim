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
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import ktx.assets.disposeSafely
import nebulae.data.GameObject
import nebulae.data.Star
import nebulae.data.System
import nebulae.kutils.smoothstep
import nebulae.rendering.shaders.StarShader
import nebulae.screens.ViewMode
import nebulae.selection.Selection
import kotlin.math.min

class StarRenderer(private val modelBatch: ModelBatch) : IRenderer {

    lateinit var viewMode: ViewMode
    var selection: Selection<System>? = null

    private val starShader = StarShader()
    private var modelInstances = mutableListOf<ModelInstance>()
    private val builder = ModelBuilder()
    private val tmp = Vector3()
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
        for ((index, star) in selection!!.item.stars.withIndex()) {
            val model = modelInstances[index]

            modelBatch.begin(camera)
            val pos = star.position
            val dst = tmp.set(pos).dst(camera.position)
            val scale = when (viewMode) {
                ViewMode.GALAXY -> 0.82f * min(dst.smoothstep(10f, 5f), TimeUtils.timeSinceMillis(selection!!.selectionTimer).toFloat().smoothstep(0f, 500f))
                ViewMode.SYSTEM -> 1.0f
            }
            model.userData = star;
            model.transform = tmpMat.idt().translate(pos).scale(scale, scale, scale)
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
