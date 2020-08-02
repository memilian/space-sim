package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.data.ModelData
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import ktx.assets.disposeSafely
import ktx.math.times
import nebulae.generation.Settings
import nebulae.rendering.shaders.SkyboxShader

class SkyboxRenderer(private val modelBatch: ModelBatch) : IRenderer {

    var textures: MutableList<TextureRegion>? = null
    private val skyboxShader: SkyboxShader = SkyboxShader()
    private val tmpMat = Matrix4()

    private val quads = mutableListOf<ModelInstance>()

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        skyboxShader.init()
        val modelBuilder = ModelBuilder()
        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.TextureCoordinates).toLong()
        val box = modelBuilder.createBox(2f, 2f, 2f, Material(), attributes)
        val boxMesh = box.meshes[0]
        val material = Material()
        for (i in 0 until 6) {
            modelBuilder.begin()
            val quad = Mesh(true, 4, 6, MeshBuilder.createAttributes(attributes))
            val vertices = FloatArray(20)
            quad.setVertices(boxMesh.getVertices(i * 20, 20, vertices))
            val indices = ShortArray(6)
            boxMesh.getIndices(0, 6, indices, 0)
            quad.setIndices(indices)
            modelBuilder.part("quad", quad, GL20.GL_TRIANGLES, material)
            quads.add(ModelInstance(modelBuilder.end(), Matrix4().idt()))
        }
        box.dispose()
    }

    private val directions = listOf(
            Vector3.Z.cpy().times(-1),
            Vector3.Z,
            Vector3.Y.cpy().times(-1),
            Vector3.Y,
            Vector3.X.cpy().times(-1),
            Vector3.X
    )

    override fun renderToScreen(camera: Camera) {
        if (Settings.debug.drawSkybox && !textures.isNullOrEmpty()) {
            modelBatch.begin(camera)

            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT or GL20.GL_COLOR_BUFFER_BIT)
            val pos = camera.position
            val world = tmpMat.idt().setToTranslationAndScaling(pos.x, pos.y, pos.z, 5500f, 5500f, 5500f)
            for (i in 0 until 6) {
                val quad = quads[i]
                val texture = textures!![i]
                skyboxShader.faceTexture = texture
                quad.transform.set(world)
                quad.transform.rotate(directions[i], when (i) {
                    1 -> 180f
                    2 -> -90f
                    3 -> 90f
                    5 -> 180f
                    else -> 0f
                })
                modelBatch.render(quad, skyboxShader)
                modelBatch.flush()
            }
            modelBatch.end()
        }
    }

    override fun dispose() {
        skyboxShader.disposeSafely()
        for (quad in quads) {
            quad.model.disposeSafely()
        }
        quads.clear()
    }

}