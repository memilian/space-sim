package nebulae.screens

import com.badlogic.gdx.*
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.VisUI
import ktx.actors.onClick
import ktx.log.logger
import ktx.vis.KVisWindow
import ktx.vis.window
import nebulae.Nebulae
import nebulae.generation.Settings
import nebulae.generation.GalaxyGenerator
import nebulae.screens.annotations.AnnotationProcessor
import nebulae.universe.Universe

private val log = logger<GenerationScreen>()

class GenerationScreen(private val game: Nebulae, private val generator: GalaxyGenerator, private val universe: Universe) : VisUIScreen() {

    private var panelShown = false
    private lateinit var window: KVisWindow
    private val LEFT_PANE_WIDTH = 350f
    private val inputAdapter = object : InputAdapter() {
        override fun keyUp(keycode: Int): Boolean {
            if (keycode == Input.Keys.F1) {
                if (panelShown) {
                    window.zIndex = 0
                    multiplexer.addProcessor(scene.cameraInputController)
                    stage.unfocusAll()
                } else {
                    window.zIndex = 5
                    multiplexer.removeProcessor(scene.cameraInputController)
                    stage.unfocus(window)
                }
                panelShown = !panelShown
                return true
            }
            return false
        }
    }
    private val multiplexer: InputMultiplexer by lazy { InputMultiplexer(stage, inputAdapter) }
    private var scene: GenerationScene = GenerationScene(generator, universe)

    init {
        initialize()
    }

    private fun initialize() {
        generator.initialize()
        stage.addActor(scene)
        stage.addActor(window("Generation") {
            window = this
            setSize(LEFT_PANE_WIDTH, height)
            val t = table {
                setSize(LEFT_PANE_WIDTH, Gdx.graphics.height.toFloat())
                pad(4f, 4f, 4f, 150f)
                align(Align.left or Align.top)

                AnnotationProcessor(this, Settings).process()

                textButton("Generate") {
                    it.colspan(3)
                    onClick {
                        scene.remove()
                        generator.resetRNG()
                        multiplexer.removeProcessor(scene.cameraInputController)
                        scene = GenerationScene(generator, universe)
                        multiplexer.addProcessor(scene.cameraInputController)
                        stage.addActor(scene)
                        resize(Gdx.graphics.width, Gdx.graphics.height)
                        generator.initialize()
                    }
                }
            }
            val scrollPane = ScrollPane(t, VisUI.getSkin(), "default")
            scrollPane.setScrollingDisabled(true, false)
            this.add(scrollPane).expand().fill()
            multiplexer.addProcessor(scene.cameraInputController)
        })
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        scene.resize(Gdx.graphics.width.toFloat(), stage.height.toInt().toFloat())
        scene.setPosition(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        window.setSize(LEFT_PANE_WIDTH, Gdx.graphics.height.toFloat())
        window.setPosition(0f, 0f)
    }

    override fun render(delta: Float) {
        super.render(delta)
        Gdx.input.inputProcessor = multiplexer
        if (Gdx.input.isButtonJustPressed(Input.Keys.BACKSPACE)) {
            game.setScreen<MainScreen>()
        }
        if (Gdx.input.isButtonJustPressed(Input.Keys.NUMPAD_0)) {
            println(5456)
        }
    }
}
