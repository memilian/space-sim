package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import ktx.assets.disposeSafely
import nebulae.generation.Settings
import org.lwjgl.opengl.GL40

class DebugRenderer(private val modelBatch: ModelBatch) : IRenderer {

    init {
        init(true)
    }

    lateinit var axis: ModelInstance
    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        axis = ModelInstance(ModelBuilder().createXYZCoordinates(10f, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong()))

    }

    private val tmp = Vector3()
    override fun renderToScreen(camera: Camera) {
        if (Settings.debug.drawAxis) {
            modelBatch.begin(camera)
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glEnable(GL40.GL_LINE_SMOOTH)
            Gdx.gl.glLineWidth(1f)
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE)
            modelBatch.render(axis)
            modelBatch.end()
            Gdx.gl.glDisable(GL40.GL_LINE_SMOOTH)
        }
    }

    override fun dispose() {
        axis.model.disposeSafely()
    }
}