package nebulae.rendering.shaders

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.utils.TimeUtils
import nebulae.generation.Settings
import kotlin.math.tan


class NebulaeShaderSDF(zoneRadius: Float) : ShaderBase("shaders/sdfnebula.vert.glsl", "shaders/sdfnebula.frag.glsl", mapOf(
        "CAM_POS" to "u_CameraPosition",
        "CAM_DIR" to "u_CameraDirection",
        "CENTER" to "u_Center",
        "SIZE" to "u_Size",
        "ROTATION" to "u_Rotation",
        "FACTOR" to "u_Factor",
        "FREQUENCY" to "u_Frequency",
        "AMPLITUDE" to "u_Amplitude",
        "PRIMARY_COLOR" to "u_PrimaryColor",
        "SECONDARY_COLOR" to "u_SecondaryColor",
        "ZONE_RADIUS" to "u_ZoneRadius"
)) {

    var zoneRadius: Float = 500f

    private var startTime = TimeUtils.millis();

    override fun render(renderable: Renderable) {
        super.render(renderable)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        renderable.meshPart.render(program)
        renderable.meshPart.mesh.unbind(program)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    override fun begin(cam: Camera, context: RenderContext) {
        super.begin(cam, context)

        val camera = cam as PerspectiveCamera
//        context.setBlending(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        program.setUniformf(locations["CAM_DIR"]!!, camera.direction)
        program.setUniformf(locations["CAM_POS"]!!, camera.position)
        program.setUniformf(locations["FACTOR"]!!, Settings.debug.factor)
        program.setUniformf(locations["ZONE_RADIUS"]!!, zoneRadius)
        program.setUniformf(locations["AMPLITUDE"]!!, Settings.debug.amplitude)
        program.setUniformf(locations["FREQUENCY"]!!, Settings.debug.frequency)
        program.setUniformf(locations["PRIMARY_COLOR"]!!, Settings.graphics.primaryNebulaColor)
        program.setUniformf(locations["SECONDARY_COLOR"]!!, Settings.graphics.secondaryNebulaColor)
    }

}