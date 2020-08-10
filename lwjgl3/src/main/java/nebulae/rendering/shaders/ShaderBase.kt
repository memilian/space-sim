package nebulae.rendering.shaders

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.TimeUtils
import ktx.assets.disposeSafely
import nebulae.ShaderWatcher
import java.io.IOException
import java.lang.Integer.max
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.log

abstract class ShaderBase(vertPath: String, fragPath: String, _uniforms: Map<String, String>, _customAttributes: Map<String, String>? = null) : Shader {
    private val vertPath: Path = Path.of(vertPath)
    private val fragPath: Path = Path.of(fragPath)
    private val referencedVertPath = mutableListOf<Path>();
    private val referencedFragPath = mutableListOf<Path>();
    private var startTime = TimeUtils.millis();
    lateinit var program: ShaderProgram

    private val uniforms: MutableMap<String, String> = mutableMapOf(
            "WORLD" to "u_World",
            "VIEW_PROJECTION" to "u_ViewProjection",
            "VIEW" to "u_View",
            "PROJECTION" to "u_Projection",
            "RESOLUTION" to "u_Resolution",
            "TIME" to "u_Time",
            "FCOEF" to "u_FCoef"
    )
    private val attributes: MutableMap<String, String>
    protected val locations = mutableMapOf<String, Int>()

    init {
        uniforms.putAll(_uniforms)
        this.attributes = mutableMapOf(
                "POSITION_ATTRIBUTE" to ShaderProgram.POSITION_ATTRIBUTE,
                "NORMAL_ATTRIBUTE" to ShaderProgram.NORMAL_ATTRIBUTE,
                "COLOR_ATTRIBUTE" to ShaderProgram.COLOR_ATTRIBUTE,
                "TEXCOORD_ATTRIBUTE" to ShaderProgram.TEXCOORD_ATTRIBUTE,
                "TANGENT_ATTRIBUTE" to ShaderProgram.TANGENT_ATTRIBUTE,
                "BINORMAL_ATTRIBUTE" to ShaderProgram.BINORMAL_ATTRIBUTE,
                "BONEWEIGHT_ATTRIBUTE" to ShaderProgram.BONEWEIGHT_ATTRIBUTE
        )
        if (_customAttributes != null) {
            this.attributes.putAll(_customAttributes)
        }
    }


    override fun init() {
        var vert = Gdx.files.internal(vertPath.toString()).readString()
        var frag = Gdx.files.internal(fragPath.toString()).readString()

        uniforms.forEach { (key, value) ->
            vert = vert.replace(key, value)
            frag = frag.replace(key, value)
        }

        attributes.forEach { (key, value) ->
            vert = vert.replace(key, value)
        }

        val includeRx = Regex("""#include "(.+)"""");
        referencedFragPath.clear()
        includeRx.findAll(frag).forEach {
            val s = it.groupValues[1]
            referencedFragPath.add(Path.of(s))
            val content = Gdx.files.internal(s).readString()
            frag = frag.replace(it.value, content)
        }
        referencedVertPath.clear()
        includeRx.findAll(vert).forEach {
            val s = it.groupValues[1]
            referencedVertPath.add(Path.of(s))
            val content = Gdx.files.internal(s).readString()
            vert = vert.replace(it.value, content)
        }
//        println(vert)
//        println(frag)

        ShaderProgram.pedantic = false
        program = ShaderProgram(vert, frag)
        if (!program.isCompiled) {
            var context = "";
            val logLines = program.log.split("\n").toMutableList();
            val lineRx = Regex("""\((\d+)\)""")
            for ((index, line) in logLines.withIndex()) {
                if (line.contains("Fragment")) {
                    context = frag
                } else if (line.contains("Vertex")) {
                    context = vert
                }
                assert(context.isNotEmpty())
                Regex("""\d\((\d+)\)""").findAll(frag)
                if (lineRx.containsMatchIn(line)) {
                    val match = lineRx.find(line)
                    val lineNb = match!!.groups[1]!!.value.toInt()
                    logLines[index] = """$line
                        ${context.split("\n")[max(lineNb - 1, 0)]}
                    """
                }
            }
            throw GdxRuntimeException(logLines.joinToString {
                it
            })
        }

        uniforms.forEach { (key, value) -> locations[key] = program.getUniformLocation(value) }
    }

    override fun begin(camera: Camera, context: RenderContext) {
        recompileIfNeeded()
        program.begin()
        program.setUniformMatrix(locations["PROJECTION"]!!, camera.projection)
        program.setUniformMatrix(locations["VIEW"]!!, camera.view)
        program.setUniformMatrix(locations["VIEW_PROJECTION"]!!, camera.combined)
        program.setUniformi(locations["TIME"]!!, TimeUtils.timeSinceMillis(startTime).toInt())
        program.setUniformf(locations["RESOLUTION"]!!, Vector2(camera.viewportWidth, camera.viewportHeight))
        program.setUniformf(locations["FCOEF"]!!, 2.0f / log(camera.far + 1.0f, 2.0f))
    }

    override fun render(renderable: Renderable) {
        renderable.meshPart.mesh.bind(program)
        program.setUniformMatrix(locations["WORLD"]!!, renderable.worldTransform)
    }


    private fun recompileIfNeeded() {
        val changedVert = ShaderWatcher.changedPaths.firstOrNull { changed ->
            changed.endsWith(vertPath.fileName.toString()) || referencedVertPath.any { changed.endsWith(it.fileName.toString()) }
        }
        val changedFrag = ShaderWatcher.changedPaths.firstOrNull { changed ->
            changed.endsWith(fragPath.fileName.toString()) || referencedFragPath.any { changed.endsWith(it.fileName.toString()) }
        }

        if (changedVert != null || changedFrag != null) {
            ShaderWatcher.changedPaths.remove(changedFrag)
            ShaderWatcher.changedPaths.remove(changedVert)
            val oldProgram = program
            fun replaceOrRevert() {
                try {
                    init()
                } catch (e: GdxRuntimeException) {
                    println(e)
                } finally {
                    if (!program.isCompiled) {

                        program.disposeSafely()
                        program = oldProgram
                    } else {
                        oldProgram.disposeSafely()
                    }
                }
            }
            try {
                replaceOrRevert()
            } catch (e: IOException) {
                Thread.sleep(200)
                replaceOrRevert()
            }
        }
    }

    override fun end() {
//        val intbuf = ByteBuffer.allocateDirect(16 * Integer.SIZE / 8).order(ByteOrder.nativeOrder()).asIntBuffer()
//        Gdx.gl.glGetIntegerv(GL40.GL_MAX_VERTEX_UNIFORM_COMPONENTS, intbuf)
//        println("" + intbuf.get() + " " + Gdx.gl.glGetError())
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