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
import nebulae.data.Star
import nebulae.kutils.smoothstep
import nebulae.rendering.shaders.StarShader
import nebulae.screens.ViewMode
import nebulae.selection.Selection
import kotlin.math.min

class StarRenderer(private val modelBatch: ModelBatch) : IRenderer {

    lateinit var viewMode: ViewMode
    var starSelection: Selection<Star>? = null
        set(value) {
            field = value
            starModelInstance?.userData = value?.item
        }

    private val starShader = StarShader()
    private var starModelInstance: ModelInstance? = null
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
        starModelInstance = ModelInstance(starModel)
    }

    override fun renderToScreen(camera: Camera) {
        if (starSelection == null) {
            return
        }
        modelBatch.begin(camera)
        val starPos = starSelection!!.item.position
        val dst = tmp.set(starPos).dst(camera.position)
        val scale = when (viewMode) {
            ViewMode.GALAXY -> 0.82f * min(dst.smoothstep(10f, 5f), TimeUtils.timeSinceMillis(starSelection!!.selectionTimer).toFloat().smoothstep(0f, 500f))
            ViewMode.SYSTEM -> 2.0f
        }
        starModelInstance?.transform = tmpMat.idt().translate(starPos).scale(scale, scale, scale)
        modelBatch.render(starModelInstance, starShader)
        modelBatch.end()
    }

    override fun dispose() {
        starModelInstance?.model.disposeSafely()
        starShader.disposeSafely()
    }
}
