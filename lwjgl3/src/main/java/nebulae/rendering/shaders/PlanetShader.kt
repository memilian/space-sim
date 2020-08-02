package nebulae.rendering.shaders

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import nebulae.data.Planet
import nebulae.data.Star
import nebulae.generation.Settings


class PlanetShader : ShaderBase("shaders/planet.vert.glsl", "shaders/planet.frag.glsl", mapOf(
        "CAM_POS" to "u_CameraPosition",
        "CAM_DIR" to "u_CameraDirection",
        "FACTOR" to "u_Factor",
        "FREQUENCY" to "u_Frequency",
        "AMPLITUDE" to "u_Amplitude",
        "SEED" to "u_Seed",
        "TEMPERATURE" to "u_Temperature",
        "SPECTRUM" to "u_Spectrum"
)) {

    private val temperatureColors = Texture(Gdx.files.internal("textures/star_spectrum.png"))

    override fun render(renderable: Renderable) {
        super.render(renderable)
        val planet = renderable.userData as Planet
        program.setUniformf(locations["SEED"]!!, planet.bodyInfos.seed)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        renderable.meshPart.render(program)
        renderable.meshPart.mesh.unbind(program)
        Gdx.gl.glCullFace(GL20.GL_FRONT)
    }

    override fun begin(camera: Camera, context: RenderContext) {
        super.begin(camera, context)

        program.setUniformf(locations["FACTOR"]!!, Settings.debug.factor)
        program.setUniformf(locations["AMPLITUDE"]!!, Settings.debug.amplitude)
        program.setUniformf(locations["FREQUENCY"]!!, Settings.debug.frequency)
        program.setUniformf(locations["CAM_DIR"]!!, camera.direction)
        program.setUniformf(locations["CAM_POS"]!!, camera.position)
        temperatureColors.bind(0)
        program.setUniformi("SPECTRUM", 0)
    }
}