@file:Suppress("LibGDXFlushInsideLoop")

package nebulae.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ai.utils.random.GaussianFloatDistribution
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import ktx.assets.disposeSafely
import ktx.graphics.use
import nebulae.generation.Settings
import org.lwjgl.opengl.GL40


class BlurFx(private val camera: OrthographicCamera, val batch: Batch, var baseWidth: Int, var baseHeight: Int) : Disposable {
    private val VERT = """
attribute vec4 ${ShaderProgram.POSITION_ATTRIBUTE};
attribute vec4 ${ShaderProgram.COLOR_ATTRIBUTE};
attribute vec2 ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
uniform mat4 u_projTrans;
 
varying vec4 vColor;
varying vec2 vTexCoord;
void main() {
	vColor = ${ShaderProgram.COLOR_ATTRIBUTE};
	vTexCoord = ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
	gl_Position =  u_projTrans * ${ShaderProgram.POSITION_ATTRIBUTE};
}
"""

    private val FRAG = """
        #version 130
#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP 
#endif
in LOWP vec4 vColor;
in vec2 vTexCoord;
out vec4 color;

uniform sampler2D u_texture;
uniform vec2 resolution;
uniform float radius;
uniform vec2 dir;

vec4 sampleBloom(sampler2D tex, vec2 uv, float lod){
    float treshold = 0.0;
    return max(textureLod(tex, uv, lod) - treshold, vec4(0.0));
}

uniform float weight[8];

void main() {
	vec4 sum = vec4(0.0);
	vec2 tc = vTexCoord;
	vec2 blur = radius/resolution; 
    
    float hstep = dir.x;
    float vstep = dir.y;
    
    float lodLevel = 2.0;
    vec4 s = vec4(0.0);
    s += sampleBloom(u_texture, vec2(tc.x, tc.y), lodLevel) * weight[0];
    float a = s.a;
    for(int j=1;j<8;j++){
        s += sampleBloom(u_texture, vec2(tc.x + j * blur.x*hstep, tc.y + j * blur.y*vstep), lodLevel) * weight[j];
        s += sampleBloom(u_texture, vec2(tc.x - j * blur.x*hstep, tc.y - j * blur.y*vstep), lodLevel) * weight[j];
    }

    sum += s;

	color = vec4(sum.rgb, 1.0);
}
"""
    var blurShader: ShaderProgram? = null
    var sceneBuffer: FrameBuffer? = null
    var pingBuffer: FrameBuffer? = null
    var pongBuffer: FrameBuffer? = null
    var fboRegion: TextureRegion? = null

    val FBO_WIDTH = baseWidth
    val FBO_HEIGHT = baseHeight

    init {
        initialize()
    }

    fun initialize() {
        ShaderProgram.pedantic = false

        blurShader = ShaderProgram(VERT, FRAG)
        if (!blurShader!!.isCompiled) System.err.println(blurShader!!.log)
        if (blurShader!!.log.isNotEmpty()) println(blurShader!!.log)

        sceneBuffer = FrameBuffer(Pixmap.Format.RGBA8888, FBO_WIDTH, FBO_HEIGHT, true)
        pingBuffer = FrameBuffer(Pixmap.Format.RGBA8888, FBO_WIDTH, FBO_HEIGHT, true)
        pongBuffer = FrameBuffer(Pixmap.Format.RGBA8888, FBO_WIDTH, FBO_HEIGHT, true)
        fboRegion = TextureRegion(sceneBuffer!!.colorBufferTexture)
        fboRegion!!.flip(false, true)
        println(FBO_WIDTH)
    }

    fun renderToTexture(renderFunction: () -> Unit): TextureRegion? {

        if (sceneBuffer != null && pingBuffer != null) {
            //Start rendering to an offscreen color buffer
            renderScene(renderFunction)
            batch.shader = null
            doPass(sceneBuffer!!, pingBuffer!!)

            //blur horizontally
            batch.shader = blurShader
            blurShader!!.setUniformf("radius", Settings.graphics.blurRadius)
            blurShader!!.setUniformf("resolution", FBO_WIDTH.toFloat(), FBO_HEIGHT.toFloat())
            blurShader!!.setUniformf("dir", 1f, 0f)
            val gauss = GaussianFloatDistribution(1.0f, 0.05f)

            val weights: FloatArray = FloatArray(8)
            weights.indices.forEach {
                weights[it] = 0.155f
            }

            blurShader!!.setUniform1fv("weight", weights, 0, 5)

            batch.shader = blurShader!!
            val cnt = 3
            pingPong(pingBuffer!!, pongBuffer!!, cnt)
            //blur verticaly
            blurShader!!.setUniformf("dir", 0f, 1f)

            // doPass(pingBuffer!!, pongBuffer!!)
            val result = pingPong(pongBuffer!!, pingBuffer!!, cnt)

//            val intbuf = ByteBuffer.allocateDirect(16 * Integer.SIZE / 8).order(ByteOrder.nativeOrder()).asIntBuffer()
//            Gdx.gl.glGetIntegerv(GL40.GL_DRAW_BUFFER, intbuf)
//            println("" + intbuf.get() + " " + Gdx.gl.glGetError())

            batch.end()

            fboRegion!!.texture = result.colorBufferTexture
            return fboRegion
        }
        return null
    }

    private fun pingPong(from: FrameBuffer, to: FrameBuffer, times: Int): FrameBuffer {
        var a = from
        var b = to
        for (i in 0..times) {
            doPass(a, b)
            val tmp = a
            a = b
            b = tmp
        }
        return a
    }

    private fun doPass(from: FrameBuffer, to: FrameBuffer) {
        to.begin()
        Gdx.gl.glClearColor(.0f, .0f, 0.0f, 0f)
        Gdx.gl.glClear(GL40.GL_COLOR_BUFFER_BIT or GL40.GL_DEPTH_BUFFER_BIT)
        fboRegion!!.texture = from.colorBufferTexture
        batch.draw(fboRegion, 0f, 0f)
        batch.flush()
        to.end()
    }

    private fun renderScene(renderFunction: () -> Unit) {
        sceneBuffer!!.use {

            //Clear the offscreen buffer with an opaque background

            Gdx.gl.glClearColor(.0f, .0f, 0.0f, 0f)
            Gdx.gl.glClear(GL40.GL_COLOR_BUFFER_BIT or GL40.GL_DEPTH_BUFFER_BIT)
            batch.begin()
            batch.shader = null
            //resize the batch projection matrix before drawing with it
            resizeBatch(FBO_WIDTH.toFloat(), FBO_HEIGHT.toFloat())
            renderFunction()
            batch.flush()
        }
    }

    private fun resizeBatch(width: Float, height: Float) {
        camera.setToOrtho(false, width, height)
        camera.update()
        batch.projectionMatrix = camera.combined
    }

    override fun dispose() {
        batch.disposeSafely()
        pongBuffer?.disposeSafely()
        sceneBuffer?.disposeSafely()
        pingBuffer?.disposeSafely()
        blurShader?.disposeSafely()
    }

}