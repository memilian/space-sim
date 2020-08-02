package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import ktx.app.use
import ktx.assets.disposeSafely
import ktx.graphics.use
import nebulae.generation.Settings
import nebulae.kutils.PNG
import nebulae.rendering.shaders.NebulaeShaderSDF
import org.lwjgl.opengl.GL40


class NebulaeRenderer(private val spriteBatch: SpriteBatch, private val orthoCam: OrthographicCamera, private val modelBatch: ModelBatch, val camera: PerspectiveCamera) : IRenderer {

    var zoneRadius = 10f
    private lateinit var screenQuad: ModelInstance
    private val nebulaShader: NebulaeShaderSDF = NebulaeShaderSDF()
    private val framebufferNebulaes = FrameBuffer(Pixmap.Format.RGBA8888, 512, 512, false)

    private val nebulaesRegion = TextureRegion(framebufferNebulaes.colorBufferTexture, 512, 512)

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        nebulaesRegion.flip(false, true)
        nebulaShader.init()
        val r = 1f
        val quad = ModelBuilder().createRect(-r, r, -1f, r, r, -1f, r, -r, -1f, -r, -r, -1f, 0f, 0f, 0f, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.TextureCoordinates).toLong())
        screenQuad = ModelInstance(quad)
    }

    override fun renderToFramebuffer(camera: Camera) {
        if (Settings.debug.drawNebulaes) {
            framebufferNebulaes.use {
                orthoCam.setToOrtho(false, framebufferNebulaes.width.toFloat(), framebufferNebulaes.height.toFloat())
                orthoCam.update()
                spriteBatch.projectionMatrix = orthoCam.combined
                spriteBatch.use {

                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

                    modelBatch.begin(camera)
                    Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
                    nebulaShader.zoneRadius = zoneRadius
                    modelBatch.render(screenQuad, nebulaShader)
                    modelBatch.end()
                }
            }
        }
    }

    override fun renderToScreen(camera: Camera) {
        if (Settings.debug.drawNebulaes) {
            spriteBatch.use {
                it.enableBlending()
                it.setBlendFunction(GL40.GL_SRC_ALPHA, GL40.GL_ONE)
                it.draw(nebulaesRegion, 0f, 0f)
            }
        }
    }

    override fun dispose() {
        nebulaShader.disposeSafely()
        framebufferNebulaes.disposeSafely()
        nebulaesRegion.texture.disposeSafely()
        screenQuad.model.disposeSafely()
    }
}
