package nebulae.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.Mesh.VertexDataType
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import ktx.assets.disposeSafely
import nebulae.generation.Settings
import nebulae.screens.StarDecal
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL40
import java.lang.Integer.min
import kotlin.math.floor

class FixedStarBatch : Disposable {

    private var meshes = mutableListOf<Mesh>()
    private var shader: ShaderProgram? = null
    private val temperatureColors = Texture(Gdx.files.internal("textures/star_spectrum.png"))

    private var lastMeshDecalCount = 0
    private lateinit var lastMesh: Mesh

    private val maxVerticesPerMesh = 32000f
    private val maxDecalsPerMesh = floor(maxVerticesPerMesh / StarDecal.SIZE).toInt()

    init {
        createDefaultShader()
        createMesh()
    }


    private fun createMesh() {
        var vertexDataType = VertexDataType.VertexArray
        if (Gdx.gl30 != null) {
            vertexDataType = VertexDataType.VertexBufferObjectWithVAO
        }
        val mesh = Mesh(vertexDataType, false, maxDecalsPerMesh * StarDecal.SIZE, 0, VertexAttribute(
                VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE), VertexAttribute(
                VertexAttributes.Usage.Generic, 1, "temperature"))
        lastMesh = mesh
        lastMeshDecalCount = 0;
        meshes.add(mesh)
    }

    fun add(decal: StarDecal) {
//        for (decal in decals) {
        if (lastMeshDecalCount >= maxDecalsPerMesh) {
            createMesh()
        }
        lastMesh.verticesBuffer.position(lastMeshDecalCount * StarDecal.SIZE)
        BufferUtils.copy(decal.vertices, 0, lastMesh.verticesBuffer, StarDecal.SIZE)
        lastMeshDecalCount++;
//        }
//        println("Created ${meshes.size} meshes for ${decals.size} decals")
    }

    private var transform: Matrix4 = Matrix4().idt()
    var tmpmat: Matrix4 = Matrix4().idt()
    var tmpmat2: Matrix4 = Matrix4().idt()
    fun render(camera: Camera, scale: Float, position: Vector3) {
        if (shader != null && meshes.isNotEmpty()) {
            tmpmat.idt().setToTranslation(-position.x, -position.y, -position.z)
            tmpmat2.idt().setToScaling(scale, scale, scale)
            transform.idt().setToTranslation(position.x, position.y, position.z)
            transform.mul(tmpmat2).mul(tmpmat)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glEnable(GL20.GL_VERTEX_PROGRAM_POINT_SIZE)
            Gdx.gl.glHint(GL40.GL_POINT_SMOOTH_HINT, GL40.GL_NICEST)
            Gdx.gl.glEnable(GL32.GL_POINT_SMOOTH)
            shader!!.begin()
            shader!!.setUniformMatrix("u_projectionViewMatrix", camera.combined)
            shader!!.setUniformMatrix("u_model", transform)
            temperatureColors.bind(0)
            shader!!.setUniformi("spectrum", 0)
            shader!!.setUniformf("u_pointSizeNear", Settings.graphics.pointSizeNear)
            shader!!.setUniformf("u_pointSizeFar", Settings.graphics.pointSizeFar)
            shader!!.setUniformf("u_distancePower", Settings.graphics.distancePower)
            for ((index, mesh) in meshes.withIndex()) {
                val count = if (index == meshes.size - 1) {
                    lastMeshDecalCount * StarDecal.SIZE
                } else {
                    maxDecalsPerMesh
                }
                mesh.render(shader, GL20.GL_POINTS, 0, count)
            }
            shader!!.end()
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDisable(GL20.GL_BLEND)
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
        uniform mat4 u_model;
        uniform float u_pointSizeNear;
        uniform float u_pointSizeFar;
        uniform float u_distancePower;
        uniform sampler2D spectrum;
        varying vec4 v_color;
        
        void main()
        {
           v_color = texture2D(spectrum, vec2(temperature / 40000.0, 0.1));
           vec4 pos = ${ShaderProgram.POSITION_ATTRIBUTE} ;
           gl_Position =  u_projectionViewMatrix * u_model * pos;
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
        val shad = shader
        shader = null
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