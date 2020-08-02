package nebulae.rendering.shaders

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxRuntimeException
import nebulae.data.Star
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GLUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CoronaeShader : ShaderBase("shaders/coronae.vert.glsl", "shaders/coronae.frag.glsl", mapOf(
        "CENTER" to "u_Center",
        "SIZE" to "u_Size",
        "SEED" to "u_Seed",
        "ROTATION" to "u_Rotation",
        "TEMPERATURE" to "u_Temperature",
        "SPECTRUM" to "u_Spectrum"
)) {

    var center = Vector3()
    var size = Vector2()
    var seed = 0f
    var temperature = 0f
    private val temperatureColors = Texture(Gdx.files.internal("textures/star_spectrum.png"))

    override fun render(renderable: Renderable) {
        super.render(renderable)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_COLOR)
        renderable.meshPart.render(program)
        renderable.meshPart.mesh.unbind(program)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    private val tmpQuat = Quaternion()
    private val tmpVec = Vector3()
    override fun begin(camera: Camera, context: RenderContext) {
        super.begin(camera, context)
        context.setCullFace(GL20.GL_NONE)
        program.setUniformf(locations["CENTER"]!!, center)
        program.setUniformf(locations["SIZE"]!!, size)
        program.setUniformf(locations["SEED"]!!, seed)
        camera.view.getRotation(tmpQuat)
        tmpQuat.nor()
        tmpVec.set(abs(cos(tmpQuat.yawRad * 2)), abs(sin(tmpQuat.pitchRad * 2)), abs(cos(tmpQuat.rollRad * 2)))
        program.setUniformf(locations["ROTATION"]!!, tmpVec)
        program.setUniformf(locations["TEMPERATURE"]!!, temperature)
        temperatureColors.bind(0)
        program.setUniformi("SPECTRUM", 0)
    }
}