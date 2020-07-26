package nebulae

import com.badlogic.gdx.Application.LOG_DEBUG
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.kotcrab.vis.ui.VisUI
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.async.enableKtxCoroutines
import ktx.inject.Context
import ktx.log.logger
import nebulae.generation.GalaxyGenerator
import nebulae.kutils.FrameRate
import nebulae.screens.GenerationScreen
import nebulae.screens.MainScreen
import nebulae.universe.Universe
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL43C
import org.lwjgl.opengl.GLDebugMessageCallback
import org.lwjgl.system.APIUtil
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.Executors

private val log = logger<Nebulae>()

class Nebulae : KtxGame<KtxScreen>() {
    private var context = Context()
    private lateinit var framerate: FrameRate

    private var initialized = false;
    private val shaderWatcher = ShaderWatcher()

    override fun create() {
        if (!VisUI.isLoaded()) {
            VisUI.load(VisUI.SkinScale.X1)
        }

        Gdx.app.logLevel = LOG_DEBUG
        enableKtxCoroutines()
        context.register {
            bindSingleton(this@Nebulae)
            bindSingleton<Batch>(SpriteBatch())
            bindSingleton(BitmapFont())
            bindSingleton(AssetManager())
            bindSingleton(Universe())
            bindSingleton(GalaxyGenerator())
            addScreen(MainScreen(inject()))
            addScreen(GenerationScreen(inject(), inject(), inject()))
        }
        this.setScreen<MainScreen>();
        initialized = true;
        framerate = FrameRate()
        setupGLDebug()
        Executors.newSingleThreadScheduledExecutor().execute(shaderWatcher)
    }

    private fun setupGLDebug() {
        val proc = GLDebugMessageCallback.create { _: Int, _: Int, id: Int, severity: Int, length: Int, message: Long, _: Long ->
            if (severity == GL43C.GL_DEBUG_SEVERITY_NOTIFICATION) {
                return@create
            }
            print("[GL ${when (severity) {
                GL43C.GL_DEBUG_SEVERITY_HIGH -> "HIGH"
                GL43C.GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM"
                GL43C.GL_DEBUG_SEVERITY_LOW -> "LOW"
                GL43C.GL_DEBUG_SEVERITY_NOTIFICATION -> "NOTIFICATION"
                else -> APIUtil.apiUnknownToken(severity)
            }}]")
            print(" ID ${String.format("0x%X", id)} -> ")
            println("Message ${GLDebugMessageCallback.getMessage(length, message)}")
        }
        GL43C.glDebugMessageCallback(proc, MemoryUtil.NULL)
        if (GL11C.glGetInteger(GL30C.GL_CONTEXT_FLAGS) and GL43C.GL_CONTEXT_FLAG_DEBUG_BIT == 0) {
            APIUtil.apiLog("[GL] Warning: A non-debug context may not produce any debug output.")
            GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT)
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        framerate.resize(width, height)
        for (screen in screens.values()) {
            screen.resize(width, height)
        }
        println(width)

    }

    override fun render() {
        super.render()
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            dispose()
            create()
            log.debug { "reloaded" }
        }
        framerate.update()
        framerate.render()
    }

    override fun dispose() {
        screens.forEach {
            it.value.dispose()
        }
        VisUI.dispose(false)
        screens.clear()
        context.dispose()
        super.dispose()
    }
}