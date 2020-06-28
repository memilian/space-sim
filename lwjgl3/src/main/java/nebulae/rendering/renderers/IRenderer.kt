package nebulae.rendering.renderers

import com.badlogic.gdx.utils.Disposable

interface IRenderer : Disposable {

    fun init(firstInit: Boolean = false) {
        if (!firstInit) {
            dispose()
        }
    }

    fun renderToFramebuffer() {}

    fun renderToScreen()
}
