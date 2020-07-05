package nebulae.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.Mesh.VertexDataType
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import ktx.assets.disposeSafely
import nebulae.generation.Settings
import nebulae.screens.StarDecal
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL40
import java.lang.Integer.min
import kotlin.math.floor

class FixedStarBatch() : Disposable {
    private var meshes = mutableListOf<Mesh>()
    private var shader: ShaderProgram? = null
    private var size = 0;
    private val temperatureColors = Texture(Gdx.files.internal("textures/star_spectrum.png"))

    fun set(decals: List<StarDecal>) {
        disposeMeshes()
        disposeShader()
        createDefaultShader()
        size = decals.size
        var vertexDataType = VertexDataType.VertexArray
        if (Gdx.gl30 != null) {
            vertexDataType = VertexDataType.VertexBufferObjectWithVAO
        }
        val maxPerBatch = floor(32000f / StarDecal.SIZE).toInt()
        var pos = 0
        while (pos < decals.size) {
            val count = min(decals.size - pos, maxPerBatch)
            val list = decals.subList(pos, pos + count)
            pos += count
            val mesh = Mesh(vertexDataType, true, list.size * 4, 0, VertexAttribute(
                    VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE), VertexAttribute(
                    VertexAttributes.Usage.Generic, 1, "temperature"))
            val vertices = FloatArray(list.size * StarDecal.SIZE)
            for ((decalIndex, decal) in list.withIndex()) {
                System.arraycopy(decal.vertices, 0, vertices, decalIndex * StarDecal.SIZE, StarDecal.SIZE)
            }
            mesh.setVertices(vertices);
            meshes.add(mesh)
        }
        println("Created ${meshes.size} meshes for ${decals.size} decals")
    }

    fun render(camera: Camera) {
        if (shader != null && meshes.isNotEmpty()) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glEnable(GL20.GL_VERTEX_PROGRAM_POINT_SIZE);
            Gdx.gl.glHint(GL40.GL_POINT_SMOOTH_HINT, GL40.GL_NICEST)
            Gdx.gl.glEnable(GL32.GL_POINT_SMOOTH)
            shader!!.begin()
            shader!!.setUniformMatrix("u_projectionViewMatrix", camera.combined)
            temperatureColors.bind(0)
            shader!!.setUniformi("spectrum", 0)
            shader!!.setUniformf("u_pointSizeNear", Settings.graphics.pointSizeNear)
            shader!!.setUniformf("u_pointSizeFar", Settings.graphics.pointSizeFar)
            shader!!.setUniformf("u_distancePower", Settings.graphics.distancePower)
            for (mesh in meshes) {
                mesh.render(shader, GL20.GL_POINTS, 0, mesh.numVertices)
            }
            shader!!.end()
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDisable(GL20.GL_BLEND)
//            Gdx.gl.glDisable(GL20.GL_VERTEX_PROGRAM_POINT_SIZE);
            Gdx.gl.glDisable(GL40.GL_POINT_SMOOTH)

        }
    }

    private fun createDefaultShader() {
        ShaderProgram.pedantic = false // stop screaming please
        val vertexShader =
                """
        attribute vec4 ${ShaderProgram.POSITION_ATTRIBUTE};
        attribute float temperature;
        uniform mat4 u_projectionViewMatrix;
        uniform float u_pointSizeNear;
        uniform float u_pointSizeFar;
        uniform float u_distancePower;
        uniform sampler2D spectrum;
        varying vec4 v_color;
        
        void main()
        {
           v_color = texture2D(spectrum, vec2(temperature / 40000.0, 0.1));
           vec4 pos = ${ShaderProgram.POSITION_ATTRIBUTE};
           gl_Position =  u_projectionViewMatrix * pos;
           vec3 ndc = gl_Position.xyz / gl_Position.w ; // perspective divide.
           float d = (ndc.z);
           
           gl_PointSize = mix(u_pointSizeNear, u_pointSizeFar,  d);
        }
        """
        val fragmentShader =
                """#ifdef GL_ES
        precision mediump float;
        #endif
        varying vec4 v_color;
        void main()
        {
          gl_FragColor = v_color;
        }"""
        shader = ShaderProgram(vertexShader, fragmentShader)
        require(shader!!.isCompiled) { "couldn't compile shader: " + shader!!.log }
    }

    override fun dispose() {
        disposeMeshes()
        disposeShader()
    }

    private fun disposeShader() {
        val shad = shader;
        shader = null;
        shad.disposeSafely()
    }

    private fun disposeMeshes() {
        val iterator = meshes.iterator()
        meshes.clear()
        for (mesh in iterator) {
            mesh.disposeSafely()
        }
    }
}