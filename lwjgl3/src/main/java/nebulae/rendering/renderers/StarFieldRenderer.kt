package nebulae.rendering.renderers

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector3
import ktx.app.use
import ktx.assets.disposeSafely
import nebulae.generation.Settings
import nebulae.rendering.BlurFx
import nebulae.rendering.FixedStarBatch
import org.lwjgl.opengl.GL40

class StarFieldRenderer(private val polygonSpriteBatch: PolygonSpriteBatch, private val orthoCamera: OrthographicCamera, private val fixedBatch: FixedStarBatch, private val initialWidth: Float, private val initialHeight: Float) : IRenderer {

    lateinit var blur: BlurFx
    private var originalScene: TextureRegion? = null
    private var blurredScene: TextureRegion? = null

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        blur = BlurFx(orthoCamera, SpriteBatch(), initialWidth.toInt(), initialHeight.toInt())
        blur.initialize()
        originalScene = TextureRegion(blur.sceneBuffer!!.colorBufferTexture)
        originalScene!!.flip(false, true)
    }

    var scale = 1f
    var position = Vector3()
    override fun renderToFramebuffer(camera: Camera) {
        blurredScene = blur.renderToTexture {
            fixedBatch.render(camera, scale, position)
        }
    }

    override fun renderToScreen(camera: Camera) {
        if (Settings.debug.drawStars) {

            polygonSpriteBatch.projectionMatrix = orthoCamera.combined
            polygonSpriteBatch.use {
                it.enableBlending()
                it.setBlendFunction(GL40.GL_SRC_ALPHA, GL40.GL_DST_ALPHA)
                it.draw(originalScene, 0f, 0f)
                it.flush()

                it.setBlendFunction(GL40.GL_SRC_ALPHA, GL40.GL_DST_ALPHA)
                it.draw(blurredScene, 0f, 0f)
                it.flush()
                it.disableBlending()
            }
        }
    }

    override fun dispose() {
        blur.disposeSafely()
    }

    fun handleResize(width: Float, height: Float) {
        blur.disposeSafely()
        blur = BlurFx(orthoCamera, SpriteBatch(), width.toInt(), height.toInt())
        originalScene!!.texture = blur!!.sceneBuffer!!.colorBufferTexture
        originalScene!!.regionWidth = width.toInt()
        originalScene!!.regionHeight = height.toInt()
    }

}