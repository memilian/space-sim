package nebulae.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import ktx.app.KtxScreen
import nebulae.Nebulae
import kotlin.math.min

open class VisUIScreen(game: Nebulae) : KtxScreen {
    protected var stage: Stage = Stage(ScreenViewport())

    override fun show() {
        Gdx.input.inputProcessor = stage
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        //Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(min(Gdx.graphics.deltaTime, 1 / 30f))
        stage.draw()
        super.render(delta)
    }

    override fun dispose() {
        stage.dispose()
    }
}