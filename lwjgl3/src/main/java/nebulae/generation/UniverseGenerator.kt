package nebulae.generation

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.sudoplay.joise.Joise
import com.sudoplay.joise.module.ModuleBasisFunction
import com.sudoplay.joise.module.ModuleFractal
import com.sudoplay.joise.module.SeedableModule
import com.sudoplay.joise.noise.Noise
import kotlinx.event.event
import ktx.math.plus
import nebulae.data.*
import nebulae.generation.text.MarkovGenerator
import nebulae.kutils.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class UniverseGenerator(private val seed: Long = 123135464L) {

    private val stars = mutableListOf<Star>()
    val starCreated = event<List<Star>>()
    val galaxyUpdated = event<Star>()

    var farthestStarDistance = 0f

    var rand = Random(seed)
    var nameGenerator = MarkovGenerator(rand)

    val octree: Octree = Octree(BoundingBox(Vector3(-1000f, -1000f, -1000f), Vector3(1000f, 1000f, 1000f)), 0)
    var galaxy: Galaxy? = null

    fun generate() {
        farthestStarDistance = 0f
        octree.dispose()
        stars.clear()
        galaxy?.arms?.clear()
        generateGalaxy()
        generateSectors()
        generateStars()
    }

    private fun generateGalaxy() {
        galaxy = Galaxy("Milky Way", mutableListOf(), mutableListOf())

        val armCount = Settings.generation.armCount
        val iterationCount = Settings.generation.starCount / Settings.generation.starPerPoint / armCount
        for (i in 0 until armCount) {
            val armPoints = spiral(iterationCount, 10f, 4f, 0.05f)
            armPoints.forEach {
                it.rotate(i * 360f / armCount)
            }
            galaxy!!.arms.add(armPoints)
        }
    }

    private fun spiral(iterations: Int, a: Float, b: Float, w: Float): MutableList<Vector2> {
        val armPoints = mutableListOf<Vector2>();
        for (t in 0..iterations) {
            armPoints.add(Vector2((a + b * t) * cos(w * t), (a + b * t) * sin(w * t)))
        }
        return armPoints
    }

    private fun generateStars() {
        val wanderDistance = Settings.generation.wanderDistance
        val halfWander = wanderDistance * 0.5f
        val halfWindow = Settings.generation.window * 0.5f
        val candidates = mutableMapOf<Float, Vector3>()
        val boundingBox = BoundingBox()
        for (arm in galaxy!!.arms) {
            for (point in arm) {
                boundingBox.ext(point.toVector3(point.x))
            }
        }
        boundingBox.ext(boundingBox.min.cpy().plus(-wanderDistance - Settings.generation.window - 300f))
        boundingBox.ext(boundingBox.max.cpy().plus(wanderDistance + Settings.generation.window + 300f))
        octree.bounds.set(boundingBox)
        for (arm in galaxy!!.arms) {
            for ((idx, point) in arm.withIndex()) {
                val fromCenter = (idx + 1f) / arm.size
                val deviationChance = 0.8f * fromCenter
                val deviationDistance = 100.0f * fromCenter + rand.nextFloat() * 100f
                for (t in 0..Settings.generation.starPerPoint) {
                    val deviate = rand.nextFloat() < deviationChance
                    var target = Vector3(
                            rand.nextFloat() * wanderDistance - halfWander,
                            rand.nextFloat() * wanderDistance - halfWander,
                            0f) + point
                    if (deviate) {
                        target += Vector3(
                                rand.nextFloat() * deviationDistance - 0.5f * deviationDistance,
                                rand.nextFloat() * deviationDistance - 0.5f * deviationDistance,
                                0f)
                    }
                    for (i in 0..Settings.generation.sampleCount) {
                        val pos = Vector3(
                                rand.nextFloat() * Settings.generation.window - halfWindow + target.x,
                                rand.nextFloat() * Settings.generation.window - halfWindow + target.y,
                                rand.nextFloat() * wanderDistance - halfWander)
                        val dist = pos.dst2(target)
                        candidates[dist] = pos
                    }
                    val winner = candidates.minBy { it.key }!!.value
                    val toCenter = winner.dst(Vector3.Zero)
                    if (toCenter > farthestStarDistance) {
                        farthestStarDistance = toCenter
                    }
                    val spectralClass = SpectralClass.random(rand)
                    val temperature = rand.range(spectralClass.temperatureRange)
                    val luminosityClass = LuminosityClass.random(rand, spectralClass)
                    val type = StarType(spectralClass, luminosityClass, temperature, spectralClass.getSubClass(temperature))
                    val boundsSize = 0.0001f
                    val star = Star(
                            BoundingBox(winner.cpy().add(-boundsSize, -boundsSize, -boundsSize), winner.cpy().add(boundsSize, boundsSize, boundsSize)),
                            nameGenerator.generate(rand.nextInt(4, 8)),
                            type
                    )
                    stars.add(star)
                    octree.insert(star)
                    candidates.clear()
                }
            }
        }
        octree.printStats()
        starCreated(stars)
    }

    private fun generateSectors() {

    }


    private fun SpectralClass.Companion.random(random: Random): SpectralClass {
        while (true) {
            val rnd = random.nextFloat()
            for (klass in lowToHigh) {
                if (rnd < klass.probability)
                    return klass
            }
        }
    }

    private fun LuminosityClass.Companion.random(random: Random, spectralClass: SpectralClass): LuminosityClass {
        while (true) {
            val rnd = random.nextFloat()
            for (klass in lowToHigh) {
                if (rnd < klass.probability && !spectralClass.excludedLuminosityClasses.contains(klass))
                    return klass
            }
        }
    }

    fun resetRNG() {
        rand = Random(seed)
        nameGenerator = MarkovGenerator(rand)
    }

}

private fun Random.range(range: Vector2): Float {
    return this.nextFloat() * (range.y - range.x) + range.x
}
