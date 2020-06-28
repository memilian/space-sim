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
import com.badlogic.gdx.math.Vector3
import ktx.assets.disposeSafely
import nebulae.data.Octree
import nebulae.generation.Settings
import org.lwjgl.opengl.GL40

class OctreeRenderer(private val nodeList: MutableList<Octree>, private val camera: Camera, private val modelBatch: ModelBatch) : IRenderer {

    private val boxes = mutableListOf<ModelInstance>()

    init {
        init(true)
    }

    override fun init(firstInit: Boolean) {
        super.init(firstInit)
        val boxModel = ModelBuilder().createBox(1f, 1f, 1f, GL40.GL_LINES, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong())

        for (i in 0..200) {
            val instance = ModelInstance(boxModel, Matrix4())
            boxes.add(instance)
        }
    }

    private val tmp = Vector3()
    override fun renderToScreen() {
        if (!Settings.debug.drawIntersection) {
            return
        }
        if (nodeList.size >= boxes.size) {
            println("Too much nodes to draw: ${nodeList.size}")
            return
        }

        modelBatch.begin(camera)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL40.GL_LINE_SMOOTH)
        Gdx.gl.glLineWidth(1f)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE)
        for ((index, node) in nodeList.withIndex()) {
            val instance = boxes[index]
            node.bounds.getCenter(tmp)
            instance.transform = Matrix4().translate(tmp).scale(node.bounds.width, node.bounds.width, node.bounds.width)
            modelBatch.render(instance)
        }

        Gdx.gl.glDisable(GL40.GL_LINE_SMOOTH)
        modelBatch.end()
    }

    override fun dispose() {
        for (box in boxes) {
            box.model.disposeSafely()
        }
        boxes.clear()
    }
}