package nebulae.screens

import ktx.actors.centerPosition
import ktx.actors.onClick
import ktx.log.logger
import ktx.vis.window
import nebulae.Nebulae

private val log = logger<MainScreen>()

class MainScreen(game: Nebulae) : VisUIScreen() {

    init {
        stage.addActor(window("") {
            isMovable = false
            verticalFlowGroup {
                addActor(textButton("Generate") {
                    onClick {
                        game.setScreen<GenerationScreen>()
                    }
                })
            }
        })
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        stage.actors[0].centerPosition(stage.width, stage.height)
    }

    override fun render(delta: Float) {
        super.render(delta)
    }


}