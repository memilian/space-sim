package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Gdx.gl20
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.MathUtils.log
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import ktx.app.use
import ktx.assets.disposeSafely
import ktx.math.times
import nebulae.data.*
import nebulae.kutils.minus
import nebulae.kutils.plus
import nebulae.kutils.smoothstep
import nebulae.rendering.BlurFx
import nebulae.rendering.shaders.CoronaeShader
import nebulae.rendering.shaders.StarShader
import nebulae.screens.ViewMode
import nebulae.selection.Selection
import org.lwjgl.opengl.GL40
import java.lang.Float.max
import kotlin.math.max
import kotlin.math.min

class StarRenderer(private val modelBatch: ModelBatch) : IRenderer {

    lateinit var viewMode: ViewMode
    var selection: Selection<System>? = null

    private val starShader = StarShader()
    private val coronaeShader = CoronaeShader()
    private var modelInstances = mutableListOf<ModelInstance>()
    private val builder = ModelBuilder()
    private val tmp = Vector3()
    private val tmp2 = Vector3()
    private val tmpMat = Matrix4()

    private val coronaes = mutableListOf<ModelInstance>()

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        starShader.init()
        coronaeShader.init()
        val starModel = builder.createSphere(1f, 1f, 1f, 90, 90, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
        val quadModel = builder.createRect(-0.5f, -0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f, 0f, 0f, 1f, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
        for (i in 0 until 5) {
            modelInstances.add(ModelInstance(starModel))
            coronaes.add(ModelInstance(quadModel))
        }
    }

    override fun renderToFramebuffer(camera: Camera) {

    }

    override fun renderToScreen(camera: Camera) {
        if (selection == null || modelInstances.isEmpty()) {
            return
        }
        val stars = selection!!.item.stars
        val largestStar = stars.maxBy { it.bodyInfos.radius }
        for ((index, star) in stars.withIndex()) {

            val cor = coronaes[0]
            val radius = star.bodyInfos.radius * KM_TO_AU
            val systemPos = selection!!.item.position
            val systemToCam = systemPos.dst(camera.position)
            var scale = 1.0f
            var zoomScale = 1.0f
            when (viewMode) {
                ViewMode.GALAXY -> {
                    scale = max(0.5, min(radius / (largestStar!!.bodyInfos.radius * KM_TO_AU), 10.0)).toFloat()
                    zoomScale = min(systemToCam.smoothstep(10f, 5f), TimeUtils.timeSinceMillis(selection!!.selectionTimer).toFloat().smoothstep(0f, 100f))
                }
                ViewMode.SYSTEM -> scale = kotlin.math.max((radius * AU_TO_SYSTEM).toFloat(), (tmp.set(camera.position).dst(star.position)) / 80f)
            }
            var pos = star.position
            if (viewMode == ViewMode.GALAXY) {
                pos = tmp.set(star.position) * 0.1f + selection!!.item.position
            }
            coronaeShader.center.set(pos)
            coronaeShader.size.set(scale * zoomScale * 2.0f, scale * zoomScale * 2.0f)
            coronaeShader.seed = star.bodyInfos.seed;
            coronaeShader.temperature = star.type.temperature

            modelBatch.begin(camera)
            modelBatch.render(cor, coronaeShader)
            modelBatch.end()

            val model = modelInstances[index]

            modelBatch.begin(camera)

            model.userData = star
            if (viewMode == ViewMode.GALAXY) {
                tmp.set(selection!!.item.position)
                tmp2.set(star.position)
                model.transform = tmpMat.idt().translate(tmp + tmp2 * 0.1f).scale(scale * zoomScale, scale * zoomScale, scale * zoomScale)
            } else {
                model.transform = tmpMat.idt().translate(star.position).scale(scale, scale, scale)
            }
            modelBatch.render(model, starShader)
            modelBatch.end()
        }
//        polygonSpriteBatch.projectionMatrix = orthoCamera.combined
//        polygonSpriteBatch.use {
////            gl20.glEnable(GL20.GL_DEPTH_TEST)
////            Gdx.gl20.glDepthMask(true)
//            it.enableBlending()
//            it.setBlendFunction(GL40.GL_ONE, GL40.GL_ONE_MINUS_SRC_ALPHA)
//            it.draw(originalScene, 0f, 0f)
//            it.flush()
//
////            it.setBlendFunction(GL40.GL_ONE, GL40.GL_SRC_ALPHA)
////            it.draw(blurredScene, 0f, 0f)
////            it.flush()
//
//            it.disableBlending()
//        }

    }

    override fun dispose() {
        for (model in modelInstances) {
            model.model.disposeSafely()
        }
        for (fb in coronaes) {
            fb.model.disposeSafely()
        }
        coronaes.clear()
        modelInstances.clear()
        starShader.disposeSafely()
    }

    fun handleResize(width: Float, height: Float) {

    }
}
