package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.decals.Decal
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch
import com.badlogic.gdx.graphics.g3d.decals.DecalMaterial
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Pool
import ktx.app.use
import ktx.assets.disposeSafely
import ktx.graphics.use
import ktx.math.times
import nebulae.data.GameObject
import nebulae.data.Star
import nebulae.generation.Settings
import nebulae.kutils.minus
import nebulae.kutils.plus
import nebulae.selection.Selection
import org.lwjgl.opengl.GL40
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SelectionRenderer(private val camera: Camera, private val orthoCam: OrthographicCamera, private val decalBatch: DecalBatch, private val modelBatch: ModelBatch, private val font: BitmapFont) : IRenderer {

    var hoveredSelection: Selection<Star>? = null
        set(value) {
            field = value
            if (value != null) {
                hoveredTextDecal = createTextDecal(hoveredFb, hoveredTextDecal, value.item.name)
            }
        }
    var focusedSelection: Selection<Star>? = null
        set(value) {
            field = value
            if (value != null) {
                focusedTextDecal = createTextDecal(focusedFb, focusedTextDecal, value.item.name)
            }
        }

    private lateinit var focusedDecal: Decal
    private lateinit var hoveredDecal: Decal
    private lateinit var focusedTextDecal: Decal
    private lateinit var hoveredTextDecal: Decal
    private lateinit var ringTexture: TextureRegion
    private val focusedFb = FrameBuffer(Pixmap.Format.RGBA8888, 1024, 256, false)
    private val hoveredFb = FrameBuffer(Pixmap.Format.RGBA8888, 1024, 256, false)
    private val tmp2 = Vector3()
    private val tmp = Vector3()

    init {
        init(true);
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        focusedDecal = Decal()
        hoveredDecal = Decal()
        focusedTextDecal = createTextDecal(focusedFb, Decal(DecalMaterial()), "")
        hoveredTextDecal = createTextDecal(hoveredFb, Decal(DecalMaterial()), "")
        ringTexture = TextureRegion(Texture(Gdx.files.internal("textures/ring_pre.png")))
    }

    override fun renderToScreen() {
        if (hoveredSelection != null) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
            val decal = hoveredDecal
            val starPosition = hoveredSelection!!.item.position
            decal.position = starPosition
            //slightly move the decal along the view axis to avoid z fighting
            decal.position = decal.position + (tmp2.set(camera.position) - decal.position).nor() * -2f
            decal.textureRegion = ringTexture
            val toCam = max(1f, min(camera.position.dst2(decal.position), 1000f))
            val size = max(0.3f, toCam * 3f / 1000f)
            decal.setDimensions(size, size)
            decal.lookAt(camera.position, camera.up)
            decal.setBlending(GL40.GL_ONE, GL40.GL_ONE)
            decal.color = Color(0.48f, 0.78f, 1.0f, 0.5f)
            decalBatch.add(decal)
            hoveredTextDecal.position.set(starPosition)
            drawTextDecal(hoveredTextDecal, computeScale(starPosition))
        }
        if (focusedSelection != null) {
            val decal = focusedDecal
            decal.position = focusedSelection!!.item.position
            decal.textureRegion = ringTexture
            val toCam = max(1f, min(camera.position.dst2(decal.position), 1000f))
            //slightly move the decal along the view axis to avoid z fighting
            decal.position = (decal.position + (tmp2.set(camera.position) - decal.position).nor() * 3f)
            val size = max(0.3f, toCam * 3f / 1000f) * (1f + 0.3f * (1f + 0.5f * MathUtils.cos(focusedSelection!!.selectionTimer * 2f).pow(2f)))
            decal.setDimensions(size, size)
            decal.lookAt(camera.position, camera.up)
            decal.setBlending(GL40.GL_SRC_ALPHA, GL40.GL_ONE_MINUS_SRC_ALPHA)
            decal.color = Color(0.68f, 0.88f, 1.0f, 0.15f)
            decalBatch.add(decal)

            focusedTextDecal.position.set(focusedSelection!!.item.position)
            focusedTextDecal.position = focusedTextDecal.position + tmp.set(camera.up) * 0.5f
            drawTextDecal(focusedTextDecal, computeScale(decal.position))

            /*   if (focusedNeighborsConnections.isNotEmpty() && Settings.debug.drawNeighbors) {
                   modelBatch.begin(camera)
                   Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
                   Gdx.gl.glEnable(GL40.GL_LINE_SMOOTH)
                   Gdx.gl.glLineWidth(1f)
                   focusedNeighborsConnections.forEach {
                       it.materials[0].set(BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.155f))
                       modelBatch.render(it);
                   }
                   modelBatch.end()
                   Gdx.gl.glDisable(GL40.GL_LINE_SMOOTH)
               }*/
        }
        decalBatch.flush()
    }

    private fun drawTextDecal(it: Decal, scale: Float) {
        it.lookAt(camera.position, camera.up)
        it.setBlending(GL40.GL_ONE, GL40.GL_ONE)
        it.color = Color.WHITE
        it.setDimensions(8f * scale, 2f * scale)
        decalBatch.add(it)
    }

    private fun computeScale(pos: Vector3): Float {
        val evenPoint = 20f
        var minScale = 2f
        var maxScale = 100f
        var minD = evenPoint
        var maxD = 1000f
        val d = camera.position.dst(pos)
        if (d < evenPoint) {
            minD = 0f
            maxD = evenPoint
            maxScale = 2f
            minScale = 0.2f
        }
        return ((d - minD) / (maxD - minD)) * (maxScale - minScale) + minScale
    }

    private fun createTextDecal(fb: FrameBuffer, decal: Decal, vararg lines: String): Decal {
        //create info texture

        fb.use {
            orthoCam.setToOrtho(false, fb.width.toFloat(), fb.height.toFloat())
            orthoCam.update()
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL40.GL_COLOR_BUFFER_BIT or GL40.GL_DEPTH_BUFFER_BIT)

            val spriteBatch = SpriteBatch()
            spriteBatch.projectionMatrix = orthoCam.combined
            spriteBatch.use {
                spriteBatch.color = Color.WHITE
                val scale = 0.35f
                font.data.scaleX = scale
                font.data.scaleY = scale
                for ((i, line) in lines.withIndex()) {
                    font.draw(it, line, 0f, 80f + i * font.lineHeight * scale, 1024f, Align.center, true)
                }
            }
            spriteBatch.flush()
            spriteBatch.color = Color.WHITE
        }

        decal.textureRegion = TextureRegion(fb.colorBufferTexture)
        return decal
    }

    override fun dispose() {
        ringTexture.texture.disposeSafely()
    }

}