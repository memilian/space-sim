package nebulae.selection

import com.badlogic.gdx.graphics.g3d.decals.Decal
import com.badlogic.gdx.utils.TimeUtils
import nebulae.data.GameObject
import nebulae.data.Star

class Selection<T : GameObject>(val item: T) {
    var selectionTimer = TimeUtils.millis()
}