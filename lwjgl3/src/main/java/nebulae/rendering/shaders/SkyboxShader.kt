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
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxRuntimeException
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GLUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkyboxShader : ShaderBase("shaders/skybox.vert.glsl", "shaders/skybox.frag.glsl", mapOf(
        "FACE_TEXTURE" to "u_FaceTexture"
)) {

    override fun render(renderable: Renderable) {
        super.render(renderable)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthRangef(1f, 1f)
        renderable.meshPart.render(program)
        renderable.meshPart.mesh.unbind(program)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthRangef(0f, 1f)
    }

    var faceTexture: TextureRegion? = null;
    override fun begin(camera: Camera, context: RenderContext) {
        super.begin(camera, context)
//        context.setBlending(true, GL40.GL_SRC_ALPHA, GL40.GL_ONE_MINUS_SRC_ALPHA)
        context.setCullFace(GL20.GL_NONE)
        faceTexture!!.texture.bind(0)
        program.setUniformi(locations["FACE_TEXTURE"]!!, 0)
    }

}