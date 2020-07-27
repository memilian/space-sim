package nebulae.generation

import com.badlogic.gdx.math.MathUtils.*
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import kotlinx.event.event
import ktx.math.div
import ktx.math.plus
import ktx.math.times
import nebulae.data.*
import nebulae.generation.text.MarkovGenerator
import nebulae.kutils.*
import java.lang.Float.min
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

import nebulae.data.SpectralClass.*;
import nebulae.data.LuminosityClass.*;
import nebulae.orbital.computePosition

class GalaxyGenerator(private val seed: Long = 123135464L) {

    private val systems = mutableListOf<System>()
    val generationFinished = event<List<System>>()

    var farthestStarDistance = 0f

    var rand = Random(seed)
    var nameGenerator = MarkovGenerator(rand)

    val octree: Octree = Octree(BoundingBox(Vector3(-1000f, -1000f, -1000f), Vector3(1000f, 1000f, 1000f)), 0)
    var galaxy: Galaxy? = null

    fun generate() {
        farthestStarDistance = 0f
        octree.dispose()
        systems.clear()
        galaxy?.arms?.clear()
        generateGalaxy()
        generateStarSystems()
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

    private fun generateStarSystems() {
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


                    val stars = mutableListOf<Star>()
                    val planets = mutableListOf<Planet>()

                    val boundsSize = 0.0001f
                    val system = System(
                            BoundingBox(winner.cpy().sub(boundsSize), winner.cpy().add(boundsSize)),
                            winner.cpy(),
                            "",
                            Vector3(),
                            stars,
                            planets)

                    val star = generateStar(system, Vector3(), 0f)
                    stars.add(star)
                    var moreStarProba = 0.45f
                    while (rand.nextFloat() < moreStarProba) { // multiple star systems
                        val parent = stars[stars.size - 1]
                        stars.add(generateStar(system, parent.position, parent.bodyInfos.radius))
                        moreStarProba *= 0.6f
                        if (stars.size > 6) {
                            break
                        }
                    }

                    if (stars.size > 1) {
                        val barycenter = computeBarycenter(stars.map {
                            Pair(it.position, it.bodyInfos.mass)
                        }.toMutableList())
                        system.barycenter.set(barycenter)
                        // TODO: in case of triple systems and more, assign a barycenter to each pair of component (in cases of c -> ab, a common barycenter for a and b then another for c and ab)
                        // TODO: adjust star names to denote a multi system configuration : append a letter to each star, A B C.. or use https://en.wikipedia.org/wiki/Bayer_designation
                        // TODO: rescale the galaxy so that distance units are represented in AU
                    }

                    val planetCount = rand.nextInt(0, 12)
                    var lastPlanetDist = stars[0].bodyInfos.orbitalParameters.semiMajorAxis + stars[0].bodyInfos.radius * KM_TO_AU
                    for (i in 0 until planetCount) {
                        val planet = generatePlanet(system, lastPlanetDist, stars[0])
                        lastPlanetDist = planet.bodyInfos.orbitalParameters.semiMajorAxis
                        planets.add(planet)
                        if (lastPlanetDist > 50f) {
                            break
                        }
                    }

                    system.name = stars[0].name + " (" + stars.size + ")";
                    octree.insert(system)
                    this.systems.add(system);
                    candidates.clear()
                }
            }
        }
        octree.printStats()
        generationFinished(systems)
    }

    private fun computeBarycenter(bodies: MutableList<Pair<Vector3, Float>>): Vector3 {
        bodies.sortByDescending { it.second }
        var weightedPosSum = Vector3()
        var totalMass = 0f
        val pos = bodies[0].first
        for (body in bodies) {
            weightedPosSum += pos.cpy().sub(body.first) * body.second
            totalMass += body.second
        }// TODO hierarchical systems : AB -> C -> D  or AB -> CD
        return weightedPosSum / totalMass
    }

    private fun generatePlanet(system: System, lastPlanetDist: Float, parent: ICelestialBody): Planet {

        val inclinationRange = rand.nextFloat()
        val inclination = when {
            inclinationRange > 0.2f -> rand.nextInt(-10, 10)
            inclinationRange < 0.1f -> rand.nextInt(-20, 20)
            else -> rand.nextInt(-45, 45)
        }.toFloat()

        val dist = lastPlanetDist + (0.1f + rand.nextFloat() * (1f + lastPlanetDist.smoothstep(0f, 10f) * 10f))
        val angle = (rand.nextFloat() * Math.PI * 2f).toFloat()

        //size based on https://en.wikipedia.org/wiki/Terrestrial_planet#/media/File:Size_of_Kepler_Planet_Candidates.jpg
        val sizeRange = rand.nextFloat()
        val radius = when {
            sizeRange < 0.03f -> rand.nextInt(98000, 130000)
            sizeRange < 0.1f -> rand.nextInt(39000, 98000)
            sizeRange < 0.3f -> rand.nextInt(2000, 8000)
            sizeRange < 0.6f -> rand.nextInt(8000, 13000)
            else -> rand.nextInt(13000, 39000)
        }.toFloat()

        val type = if (sizeRange > 0.6f || rand.nextFloat() < min(0.75f, 0.5f * log((dist + 0.5f) / 50f, 1f) + 1)) PlanetType.GASEOUS else PlanetType.ROCKY

        //TODO: ensure consistency with planet type
        val density = rand.range(0.1f, 20f) // in g/cm3
        val mass = (4 / 3) * PI * radius.pow(3) * density * 1e12f // in kg

        val eccentricityRng = rand.range(0.0001f, 1.0f);
        val eccentricity = distribute(eccentricityRng)
        val orbitalParameters = OrbitalParameters(
                eccentricity,
                dist,
                // tie high inclination to high eccentricity
                (eccentricity * PI - PI * 0.5f) * rand.range(0.5f, 0.9f),
                rand.range(0f, PI2),
                rand.range(PI * 0.25f, PI * 0.75f), // might need to revise this range
                rand.range(0f, PI2),
                0f,
                PI2 * sqrt((dist * AU_TO_M).pow(3) / (G_CONSTANT * (mass + parent.bodyInfos.mass))),
                parent)
        val bodyInfos = CelestialBodyInfo(system, orbitalParameters, radius, mass, density)
        val name = nameGenerator.generate(4)
        val pos = computePosition(orbitalParameters, 0f)
        return Planet(BoundingBox(pos.cpy().sub(radius), pos.cpy().add(radius)), pos, name, PlanetDescriptor(type), bodyInfos)
    }

    // create a distribution where 0.0001 < value < 0.1 for x < 0.9  then increase up to 0.93
    private fun distribute(x: Float): Float {
        return x * (cos((x + 0.2f)) * 0.2f) + sin((x + 0.004f).pow(8)).pow(8)
    }

    private fun generateStar(system: System, parentPos: Vector3, minDist: Float): Star {
        val spectralClass = SpectralClass.random(rand)
        val temperature = rand.range(spectralClass.temperatureRange)
        val luminosityClass = LuminosityClass.random(rand, spectralClass)
        val lum = when (luminosityClass) { // in solar luminosity
            Ia -> rand.range(1e5f, 1e7f)
            Ib -> rand.range(1e4f, 1e5f)
            II -> rand.range(100f, 1e4f)
            III -> rand.range(10f, 100f)
            IV -> rand.range(1f, 10f)
            VI -> rand.range(0.1f, 1f)
            VII -> rand.range(0.01f, 0.1f)
            V -> when (spectralClass) {
                O -> rand.range(100f, 1e7f)
                B -> rand.range(10f, 1000f)
                A -> rand.range(5f, 300f)
                F -> rand.range(3f, 50f)
                G -> rand.range(0.5f, 2f)
                K -> rand.range(0.1f, 0.8f)
                M -> rand.range(0.001f, 0.01f)
                else -> rand.range(0.001f, 0.01f)
            }
        }
        // in solar radius
        val radius = sqrt(lum) * 5778f.pow(2f) / temperature.pow(2) //in solar radius. https://sciencing.com/characteristics-star-5916715.html
        val type = StarType(spectralClass, luminosityClass, temperature, lum, spectralClass.getSubClass(temperature))

        val mass = when { // in solar mass
            lum > 1.76e6 -> { //approximation based on https://en.wikipedia.org/wiki/Mass%E2%80%93luminosity_relation
                lum / 32000f
            }
            lum > 15 -> {
                (lum / 1.4f).pow(1 / 3.5f)
            }
            lum > 0.03 -> {
                lum.pow(1 / 4f)
            }
            else -> {
                (lum / 0.23f).pow(1 / 2.3f)
            }
        }

        val radiusKm = radius * SOLAR_RADIUS_TO_KM
        val radiusAu = radiusKm * KM_TO_AU
        val massKg = mass * SOLAR_MASS_TO_KG
        val density = massKg / (4 * PI * radiusKm.pow(3) / 3) * 1e-12f // in g/cm3

        val semiMajorAxis = minDist * KM_TO_AU + rand.range(0.1f + radiusAu, 10f + radiusAu)
        val orbitalParameters = OrbitalParameters(
                rand.nextFloat() * 0.01f,
                semiMajorAxis,
                rand.nextFloat() * 0.1f,
                rand.range(0f, PI),
                rand.range(PI * 0.25f, PI * 0.75f), // might need to revise this range
                rand.range(0f, PI2),
                0f,
                PI2 * sqrt((semiMajorAxis * AU_TO_KM).pow(3) / (G_CONSTANT * (massKg))), //TODO: add the other stars mass
                null)

        val bodyInfos = CelestialBodyInfo(system, orbitalParameters, radiusKm, massKg, density)

        val position = parentPos + computePosition(orbitalParameters, 0f)
        val boundsSize = 0.0001f
        return Star(
                BoundingBox(
                        position.cpy().add(-boundsSize, -boundsSize, -boundsSize),
                        position.cpy().add(boundsSize, boundsSize, boundsSize)),
                position.cpy(),
                nameGenerator.generate(rand.nextInt(4, 8)),
                type, bodyInfos
        )
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

private fun Random.range(min: Float, max: Float): Float {
    return this.nextFloat() * (max - min) + min
}
