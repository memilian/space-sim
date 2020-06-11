package nebulae.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.VisUI
import ktx.actors.onClick
import ktx.log.logger
import ktx.scene2d.scrollPane
import ktx.vis.KVisWindow
import ktx.vis.window
import nebulae.Nebulae
import nebulae.generation.Settings
import nebulae.generation.UniverseGenerator
import nebulae.screens.annotations.AnnotationProcessor
import nebulae.universe.Universe

private val log = logger<GenerationScreen>()

class GenerationScreen(private val game: Nebulae, private val generator: UniverseGenerator, private val universe: Universe) : VisUIScreen(game) {

    private var window: KVisWindow? = null
    private var scene: GenerationScene = GenerationScene(generator, universe)
    private val LEFT_PANE_WIDTH = 350f

    init {
        initialize()
    }

    fun initialize() {
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
                        scene = GenerationScene(generator, universe)
                        stage.addActor(scene)
                        resize(Gdx.graphics.width, Gdx.graphics.height)
                        generator.generate()
                    }
                }
            }
            val scrollPane = ScrollPane(t, VisUI.getSkin(), "default")
            scrollPane.setScrollingDisabled(true, false)
            this.add(scrollPane).expand().fill()

        })
        stage.addActor(scene)
        generator.generate()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        scene.resize(Gdx.graphics.width - LEFT_PANE_WIDTH, stage.height)
        scene.setPosition(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        window?.setSize(LEFT_PANE_WIDTH, Gdx.graphics.height.toFloat())
        window?.setPosition(0f, 0f)
    }

    override fun render(delta: Float) {
        super.render(delta)
        scene.align(Align.topRight)
        if (Gdx.input.isButtonJustPressed(Input.Keys.BACKSPACE)) {
            game.setScreen<MainScreen>()
        }
    }
}
