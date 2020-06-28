package nebulae.rendering.shaders

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
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

data class BoxData(val center: Vector3, val size: Float)

class SkyboxShader : Shader {

    lateinit var program: ShaderProgram
    private var worldLoc = 0
    private var viewProjLoc = 0

    private val WORLD = "u_World"
    private val VIEW_PROJECTION = "u_ViewProjection"

    private lateinit var camera: Camera

    override fun init() {

        val vert = """
#version 140
in vec4 ${ShaderProgram.POSITION_ATTRIBUTE};
uniform mat4 $WORLD;
uniform mat4 $VIEW_PROJECTION;

out vec4 near;
out vec4 far;
out mat4 mvp;
out vec4 vertexPos;

void main() {
    vertexPos = ${ShaderProgram.POSITION_ATTRIBUTE};
	mvp  = $VIEW_PROJECTION * $WORLD;
    gl_Position = mvp * ${ShaderProgram.POSITION_ATTRIBUTE};

    mat4 mvpi = inverse(mvp);
    //2D projection of vertex in NDC
    vec2 pos = gl_Position.xy / gl_Position.w;
    //Ray start and end
    near =  mvpi * vec4(pos, -1.0, 1.0);
    far = mvpi * vec4(pos, 1.0, 1.0);
}
"""
        val frag = """
#version 140

in vec4 near;
in vec4 far;
in mat4 mvp;
in vec4 vertexPos;
out vec4 fragColor;

$noiseFuncs
$utils
$intersectionFuncs

#define STEPS 5
#define FAR 20 
float sphereTrace( vec3 ro, vec3 rd, out float den )
{
    float t = 0.5, maxD = 0.0, d = 1.0; den = 0.0;

    // The more STEPS the more accurate the marching.
    for( int i = 0; i < STEPS; ++i )
    {

        // Here we compute our Position p by the formula RayOrigin + RayDirection * our RayMarchStep.
        vec3 p = ro + rd * t;

        // This is our density, it is simply calling the Fractional Brownian Motion fbm function.
        den = fbm( p );

        // This allows us to put a limit in our accumulation of density.
        maxD = maxD < den ? den : maxD;

        // Here we bail on our marching according to MaximumDensity or our FAR threshold.
        if( maxD > 1.0 || t > FAR ) break;

       // We increment our RayMarchingSteps.
        t += 0.05;
    }

    den = maxD;

    return t;
}

void main()
{
    vec3 ro = near.xyz/near.w;  //ray's origin
    vec3 f = far.xyz/far.w;
    vec3 rd = f - ro;
    rd = normalize(rd);        //ray's direction
    
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);

    float density = 0.0;
    float t = sphereTrace(vertexPos.xyz, rd, density);
    
    density = clamp(density, 0.0,  1.0);
    
    fragColor = vec4(density);
    fragColor.a = clamp(density, 0.0, 1.0);

}
"""
        ShaderProgram.pedantic = false
        program = ShaderProgram(vert, frag)
        if (!program.isCompiled) {
            throw GdxRuntimeException(program.log)
        }

        viewProjLoc = program.getUniformLocation(VIEW_PROJECTION)
        worldLoc = program.getUniformLocation(WORLD)
    }

    override fun render(renderable: Renderable) {
        renderable.meshPart.mesh.bind(program)
        program.setUniformMatrix(worldLoc, renderable.worldTransform)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)

        renderable.meshPart.render(program)
        renderable.meshPart.mesh.unbind(program)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    override fun begin(camera: Camera, context: RenderContext) {
        this.camera = camera
        context.setBlending(true, GL40.GL_SRC_ALPHA, GL40.GL_ONE_MINUS_SRC_ALPHA)
        context.setCullFace(GL20.GL_FRONT)
        program.begin()
        program.setUniformMatrix(viewProjLoc, camera.combined)
    }

    override fun end() {
        program.end()
    }

    override fun canRender(instance: Renderable?): Boolean {
        return true
    }

    override fun dispose() {
        program.dispose()
    }

    override fun compareTo(other: Shader?): Int {
        return 0
    }

}