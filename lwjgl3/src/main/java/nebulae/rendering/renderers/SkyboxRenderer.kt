package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import ktx.assets.disposeSafely
import nebulae.generation.Settings
import nebulae.rendering.shaders.SkyboxShader

class SkyboxRenderer(private val modelBatch: ModelBatch, val camera: Camera) : IRenderer {

    private var skyBox: ModelInstance? = null
    private val skyboxShader: SkyboxShader = SkyboxShader()
    private val tmpMat = Matrix4()

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        skyboxShader.init()
        val sphereModel = ModelBuilder().createSphere(1f, 1f, 1f, 90, 90, Material(), (VertexAttributes.Usage.Position).toLong())
        skyBox = ModelInstance(sphereModel, Matrix4().scale(5000f, 5000f, 5000f))
    }

    override fun renderToScreen() {
        if (Settings.debug.drawSkybox) {
            modelBatch.begin(camera)
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
            val pos = camera.position
            val world = tmpMat.setToTranslationAndScaling(pos.x, pos.y, pos.z, 5000f, 5000f, 5000f)
            skyBox!!.transform.set(world)
            modelBatch.render(skyBox, skyboxShader)
            modelBatch.end()
        }
    }

    override fun dispose() {
        skyboxShader.disposeSafely()
        skyBox?.model.disposeSafely()
    }

}