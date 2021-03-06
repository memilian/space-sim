package nebulae.rendering.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.TimeUtils
import ktx.assets.disposeSafely
import ktx.math.times
import nebulae.data.*
import nebulae.generation.Settings
import nebulae.kutils.minus
import nebulae.kutils.smoothstep
import nebulae.orbital.computePosition
import nebulae.screens.ViewMode
import org.lwjgl.opengl.GL40
import java.lang.Math.PI
import kotlin.math.max
import kotlin.math.min

class OrbitRenderer(private val modelBatch: ModelBatch) : IRenderer {

    private val orbits = mutableListOf<ModelInstance>()

    init {
        init(true)
    }

    private val tmp = Vector3()
    private val tmp2 = Vector3()

    override fun renderToScreen(camera: Camera) {
        if (orbits.isNullOrEmpty()) {
            return
        }

        modelBatch.begin(camera)
        Gdx.gl.glEnable(GL40.GL_LINE_SMOOTH)
        Gdx.gl.glLineWidth(0.1f)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        for (node in orbits) {
            val celestialBodyInfo = node.userData as CelestialBodyInfo
            if (celestialBodyInfo.orbitalParameters.orbitingBody != null) {
                tmp.set(celestialBodyInfo.orbitalParameters.orbitingBody!!.position)
            } else {
                tmp.set(0f, 0f, 0f)
            }
            node.transform.idt().translate(tmp)
            modelBatch.render(node)
        }

        Gdx.gl.glDisable(GL40.GL_LINE_SMOOTH)
        modelBatch.end()
    }

    private val builder = ModelBuilder()
    fun createOrbits(bodies: List<CelestialBodyInfo>) {
        dispose()
        for (body in bodies) {
            val positions = mutableListOf<Vector3>()
            println("""#### Period : ${body.orbitalParameters.period}""")
            for (i in 0..60) {
                positions.add(computePosition(body.orbitalParameters, i * body.orbitalParameters.period / 60, AU_TO_SYSTEM))
            }
            builder.begin()
            val material = Material()
            material.set(BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.055f))
            val part = builder.part("line", GL20.GL_LINES, (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong(), material)
            part.setColor(1f, 1f, 1f, 1f)
            for (index in 1 until positions.size) {
                val pos1 = positions[index]
                val pos2 = positions[index - 1]
                part.line(pos1, pos2)
            }
            val model = builder.end()
            val modelInst = ModelInstance(model)
            modelInst.userData = body
            orbits.add(modelInst)
        }
    }

    override fun dispose() {
        for (orb in orbits) {
            orb.model.disposeSafely()
        }
        orbits.clear()
    }
}