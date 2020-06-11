package nebulae.generation

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import nebulae.screens.annotations.*
import kotlin.reflect.KClass

class Settings() {

    companion object {
        @Tab()
        public var generation = Generation()

        @Tab()
        public var graphics = Graphics()

        @Tab()
        public var debug = Debug()
    }

    class Graphics {
        @Range(from = 0f, to = 10f, step = 0.1f)
        var blurRadius = 1.3f

        @Range(from = 0f, to = 100f, step = 1f)
        var pointSizeNear = 7f

        @Range(from = 0f, to = 10f, step = 0.1f)
        var pointSizeFar = 1.0f

        @Range(from = 0f, to = 10f, step = 1f)
        var distancePower = 3f

        @ColorPicker
        var primaryNebulaColor = Color(0.2f, 0.2f, 0.76f, 1f)

        @ColorPicker
        var secondaryNebulaColor = Color(0.8f, 0.8f, 1f, 1f)
    }

    class Generation {
        @WithDescription("Number of stars to generate")
        @Range(from = 10000f, to = 10_000_000f, step = 10000f)
        var starCount = 100000

        @Range(from = 1f, to = 10f, step = 1f)
        var armCount = 3

        @Range(from = 1f, to = 100f, step = 1f)
        var window = 20f

        @Range(from = 1f, to = 10f, step = 1f)
        var sampleCount = 5

        @Range(from = 100f, to = 10_000f, step = 10f)
        var starPerPoint = 361

        @Range(from = 10f, to = 100f, step = 1f)
        var wanderDistance = 35f
    }

    class Debug {

        @Range(from = 10f, to = 200f, step = 1f)
        var nebulaSize = 20f

        @Range(from = 0f, to = 5f, step = 0.005f)
        var factor = 0.45f;

        @Range(from = 0f, to = 0.5f, step = 0.005f)
        var frequency = 0.20f;

        @Range(from = 0f, to = 4f, step = 0.05f)
        var amplitude = 1f;

        @Check
        var drawNeighbors = true

        @Check
        var drawStars = true

        @Check
        var drawNebulaes = false

        @Check
        var drawNebulaesSDF = true

        @Check()
        var showArms = false

        @Check()
        var drawIntersection = false

        @Check()
        var drawSkybox = false

        @Check()
        var autoRotate = false
/*
        @EnumList<BlendFactor>()
        var srcFactor = BlendFactor.SRC_ALPHA

        @EnumList<BlendFactor>()
        var dstFactor = BlendFactor.ONE_MINUS_SRC_ALPHA*/

    }
}


enum class BlendFactor(val data: Int) {
    ZERO(GL20.GL_ZERO),
    ONE(GL20.GL_ONE),
    SRC_COLOR(GL20.GL_SRC_COLOR),
    ONE_MINUS_SRC_COLOR(GL20.GL_ONE_MINUS_SRC_COLOR),
    DST_COLOR(GL20.GL_DST_COLOR),
    ONE_MINUS_DST_COLOR(GL20.GL_ONE_MINUS_DST_COLOR),
    SRC_ALPHA(GL20.GL_SRC_ALPHA),
    ONE_MINUS_SRC_ALPHA(GL20.GL_ONE_MINUS_SRC_ALPHA),
    DST_ALPHA(GL20.GL_DST_ALPHA),
    ONE_MINUS_DST_ALPHA(GL20.GL_ONE_MINUS_DST_ALPHA),
}

