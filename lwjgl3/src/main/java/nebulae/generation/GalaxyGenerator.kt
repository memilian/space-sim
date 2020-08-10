package nebulae.generation

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import ktx.math.div
import ktx.math.plus
import ktx.math.times
import nebulae.data.*
import nebulae.generation.text.MarkovGenerator
import nebulae.kutils.*
import java.lang.Float.min
import kotlin.random.Random
import java.lang.Math.PI

import nebulae.data.SpectralClass.*
import nebulae.data.LuminosityClass.*
import nebulae.orbital.computePosition
import java.lang.IllegalStateException
import kotlin.math.*

class GalaxyGenerator(private val seed: Long = 123135464L) {

    private val systems = mutableListOf<System>()
    lateinit var systemsIt: Iterator<System>

    var farthestStarDistance = 0f

    var rand = Random(seed)
    var nameGenerator = MarkovGenerator(rand)

    val octree: Octree = Octree(BoundingBox(Vector3(-1000f, -1000f, -1000f), Vector3(1000f, 1000f, 1000f)), 0)
    var galaxy: Galaxy? = null

    fun initialize() {
        farthestStarDistance = 0f
        octree.dispose()
        systems.clear()
        galaxy?.arms?.clear()
        generateGalaxy()
        systemsIt = generateStarSystems()
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
        val armPoints = mutableListOf<Vector2>()
        for (t in 0..iterations) {
            armPoints.add(Vector2((a + b * t) * cos(w * t), ((a + b * t) * sin(w * t))))
        }
        return armPoints
    }

    private fun generateStarSystems(): Iterator<System> {
        return iterator {
            val wanderDistance = Settings.generation.wanderDistance
            val halfWander = wanderDistance * 0.5f
            val halfWindow = Settings.generation.window * 0.5f
            val boundingBox = BoundingBox()
            val candidates = mutableMapOf<Float, Vector3>()
            val points = mutableListOf<Vector2>()
            for (arm in galaxy!!.arms) {
                for (point in arm) {
                    boundingBox.ext(point.toVector3(point.x))
                    points.add(point);
                }
            }
            points.sortBy { it.dst(0f, 0f) }
            boundingBox.ext(boundingBox.min.cpy().plus(-wanderDistance - Settings.generation.window - 300f))
            boundingBox.ext(boundingBox.max.cpy().plus(wanderDistance + Settings.generation.window + 300f))
            octree.bounds.set(boundingBox)
            val estimateMaxDist = points.last().dst(0f, 0f)
            for (point in points) {
                val fromCenter = point.dst(0f, 0f) / (estimateMaxDist + wanderDistance)
                val deviationChance = 0.5f * fromCenter
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
                            planets,
                            rand.nextFloat()
                    )

                    val star = generateStar(system, Vector3(), 0.0)
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
                    fixStarOrbitals(stars)

                    if (stars.size > 1) {
                        val barycenter = computeBarycenter(stars.map {
                            Pair(it.position, it.bodyInfos.mass)
                        }.toMutableList())
                        system.barycenter.set(barycenter)
                        // TODO: in case of triple systems and more, assign a barycenter to each pair of component (in cases of c -> ab, a common barycenter for a and b then another for c and ab)
                        // TODO: adjust star names to denote a multi system configuration : append a letter to each star, A B C.. or use https://en.wikipedia.org/wiki/Bayer_designation
                        // TODO: rescale the galaxy so that distance units are represented in AU ??
                    }

                    val planetCount = rand.nextInt(0, 12)
                    var lastPlanetDist = stars.last().bodyInfos.orbitalParameters.semiMajorAxis + stars.last().bodyInfos.radius * KM_TO_AU
                    val eclipticInclination = rand.range(-0.3, 0.3)
                    for (i in 0 until planetCount) {
                        val planetAndMoons = generatePlanetAndMoons(system, lastPlanetDist, stars[0], eclipticInclination, i)
                        val planet = planetAndMoons[0]
                        lastPlanetDist = planet.bodyInfos.orbitalParameters.semiMajorAxis
                        planets.addAll(planetAndMoons)
                        if (lastPlanetDist > 50f) {
                            break
                        }
                    }

                    system.name = stars[0].name + " (" + stars.size + ")"
                    octree.insert(system)
                    systems.add(system)
                    candidates.clear()
                    /// octree.printStats()
                    yield(system)
                }
            }
        }
    }

    private fun fixStarOrbitals(stars: MutableList<Star>) {
        val wStars = mutableListOf<Star>()
        wStars.addAll(stars)
        wStars.sortByDescending { it.bodyInfos.mass }
        stars.clear()
        wStars[0].bodyInfos.orbitalParameters.semiMajorAxis = 0.0
        stars.add(wStars.removeAt(0))
        var prevStar = stars[0]
        while (wStars.isNotEmpty()) {
            val star = wStars[0]
            val radius = star.bodyInfos.radius
            val minDist = radius * KM_TO_AU * 1.1f + prevStar.bodyInfos.radius * KM_TO_AU * 1.1f + prevStar.bodyInfos.orbitalParameters.semiMajorAxis
            val semiMajorAxis = minDist + rand.range(0.1f, 10f)
            val orbitalParameters = OrbitalParameters(
                    rand.nextFloat() * 0.01,
                    semiMajorAxis,
                    rand.nextFloat() * 0.1,
                    rand.range(.0, PI),
                    rand.range(PI * 0.25, PI * 0.75), // might need to revise this range
                    rand.range(0.0, PI * 2),
                    0.0,
                    PI * 2 * sqrt((semiMajorAxis * AU_TO_M).pow(3) / (G_CONSTANT * (star.bodyInfos.mass + prevStar.bodyInfos.mass))), //TODO: add the other stars mass
                    prevStar,
                    radius * (star.bodyInfos.mass / prevStar.bodyInfos.mass).pow(2.0 / 5.0)
            )
            star.bodyInfos.orbitalParameters = orbitalParameters
            stars.add(wStars.removeAt(0))
            prevStar = star
        }
    }

    private fun computeBarycenter(bodies: MutableList<Pair<Vector3, Double>>): Vector3 {
        bodies.sortByDescending { it.second }
        var weightedPosSum = Vector3()
        var totalMass = 0.0f
        val pos = bodies[0].first
        for (body in bodies) {
            weightedPosSum += pos.cpy().sub(body.first) * body.second.toFloat()
            totalMass += body.second.toFloat()
        }// TODO hierarchical systems : AB -> C -> D  or AB -> CD
        return weightedPosSum / totalMass
    }

    private fun generatePlanetAndMoons(system: System, lastPlanetDistAU: Double, parent: ICelestialBody, eclipticInclination: Double, planetNumber: Int, planetType: PlanetType? = null): List<Planet> {


        val distAU = lastPlanetDistAU + (0.1 + rand.nextFloat() * (lastPlanetDistAU.smoothstep(0.0, 10.0) * (planetNumber + 1)))

        val planet = generateBody(planetType, distAU, eclipticInclination, parent, system)
        val planets = mutableListOf<Planet>()
        planets.add(planet)

        var moonProba = 0.35f
        while (rand.nextFloat() < moonProba) {
            val maxRadius = planet.bodyInfos.radius * 0.3
            val minRadius = planet.bodyInfos.radius * 0.05
            val radius = rand.range(minRadius, maxRadius)
            val soi = planet.bodyInfos.orbitalParameters.soiRadius
            val distAu = rand.range((radius + planet.bodyInfos.radius) * KM_TO_AU + 0.1 * soi, soi)
            val moon = generateBody(PlanetType.ROCKY, distAu, eclipticInclination, planet, system, radius)
            planets.add(moon)
            moonProba *= 0.8f
        }
        return planets
    }

    private fun generateBody(planetType: PlanetType?, distAU: Double, eclipticInclination: Double, parent: ICelestialBody, system: System, definedRadius: Double? = null): Planet {
        //size based on https://en.wikipedia.org/wiki/Terrestrial_planet#/media/File:Size_of_Kepler_Planet_Candidates.jpg
        val sizeRange = rand.nextFloat()
        val radius = when {
            definedRadius != null -> definedRadius
            sizeRange < 0.03f -> round(rand.range(98000.0, 130000.0))
            sizeRange < 0.1f -> round(rand.range(39000.0, 98000.0))
            sizeRange < 0.3f -> round(rand.range(2000.0, 8000.0))
            sizeRange < 0.6f -> round(rand.range(8000.0, 13000.0))
            else -> round(rand.range(13000.0, 39000.0))
        }

        val type = when (planetType) {
            null -> if (sizeRange > 0.6f || rand.nextFloat() < min(0.75f, 0.5f * log((distAU.toFloat() + 0.5f) / 50f, 1f) + 1)) {
                PlanetType.GASEOUS
            } else {
                PlanetType.ROCKY
            }
            else -> planetType
        }


        //TODO add temperature https://astronomy.stackexchange.com/questions/10113/how-to-calculate-the-expected-surface-temperature-of-a-planet

        //TODO: ensure consistency with planet type
        val density = rand.range(0.1, 20.0) // in g/cm3

        val mass = (4 / 3) * PI * radius.pow(3) * density * 1e12 // in kg

        val eccentricityRng = rand.range(0.0001, 1.0)
        val eccentricity = distribute(eccentricityRng)
        val orbitalParameters = OrbitalParameters(
                eccentricity,
                distAU,
                rand.range(0.1f, 0.1f) + eclipticInclination,
                rand.range(0.0, PI * 2),
                rand.range(PI * 0.25, PI * 0.75), // might need to revise this range
                rand.range(0.0, PI * 2),
                0.0,
                PI * 2 * sqrt((distAU * AU_TO_M).pow(3) / (G_CONSTANT * (mass + parent.bodyInfos.mass))),
                parent,
                distAU * (mass / parent.bodyInfos.mass).pow(2.0 / 5.0)
        )
        if (orbitalParameters.period.isInfinite() || orbitalParameters.period.isNaN()) {
            println("beyond infinity :(")
        }
        val bodyInfos = CelestialBodyInfo(system, orbitalParameters, radius, mass, density, rand.nextFloat())
        val name = nameGenerator.generate(4)
        val pos = computePosition(orbitalParameters, 0.0)
        return Planet(BoundingBox(pos.cpy().sub(radius.toFloat()), pos.cpy().add(radius.toFloat())), pos, name, PlanetDescriptor(type), bodyInfos)
    }

    // create a distribution where 0.0001 < value < 0.1 for x < 0.9  then increase up to 0.93
    private fun distribute(x: Double): Double {
        return x * (cos((x + 0.2)) * 0.2) + kotlin.math.sin((x + 0.004).pow(8)).pow(8)
    }

    private fun generateStar(system: System, parentPos: Vector3, minDist: Double): Star {
        val spectralClass = SpectralClass.random(rand)
        val temperature = rand.range(spectralClass.temperatureRange)
        val luminosityClass = LuminosityClass.random(rand, spectralClass)
        val lum = when (luminosityClass) { // in solar luminosity
            Ia -> rand.range(1e5, 1e7)
            Ib -> rand.range(1e4, 1e5)
            II -> rand.range(100.0, 1e4)
            III -> rand.range(10.0, 100.0)
            IV -> rand.range(1.0, 10.0)
            VI -> rand.range(0.1, 1.0)
            VII -> rand.range(0.01, 0.1)
            V -> when (spectralClass) {
                O -> rand.range(100.0, 1e7)
                B -> rand.range(10.0, 1000.0)
                A -> rand.range(5.0, 300.0)
                F -> rand.range(3.0, 50.0)
                G -> rand.range(0.5, 2.0)
                K -> rand.range(0.1, 0.8)
                M -> rand.range(0.001, 0.01)
                else -> rand.range(0.001, 0.01)
            }
        }
        // in solar radius
        val radius = sqrt(lum) * 5778.0.pow(2) / temperature.pow(2) //in solar radius. https://sciencing.com/characteristics-star-5916715.html
        val type = StarType(spectralClass, luminosityClass, temperature, lum, spectralClass.getSubClass(temperature))

        val mass = when { // in solar mass
            lum > 1.76e6 -> { //approximation based on https://en.wikipedia.org/wiki/Mass%E2%80%93luminosity_relation
                lum / 32000
            }
            lum > 15 -> {
                (lum / 1.4).pow(1 / 3.5)
            }
            lum > 0.03 -> {
                lum.pow(1 / 4)
            }
            else -> {
                (lum / 0.23).pow(1 / 2.3)
            }
        }

        val radiusKm = radius * SOLAR_RADIUS_TO_KM
        val radiusAu = radiusKm * KM_TO_AU
        val massKg = mass * SOLAR_MASS_TO_KG
        val density = massKg / (4 * PI * radiusKm.pow(3) / 3) * 1e-12 // in g/cm3

        val semiMajorAxis = minDist * KM_TO_AU + rand.range(0.1 + radiusAu, 10 + radiusAu)
        val orbitalParameters = OrbitalParameters(
                rand.nextDouble() * 0.01,
                semiMajorAxis,
                rand.nextDouble() * 0.1,
                rand.range(0.0, Math.PI),
                rand.range(PI * 0.25, Math.PI * 0.75), // might need to revise this range
                rand.range(0.0, Math.PI * 2),
                0.0,
                PI * 2 * sqrt((semiMajorAxis * AU_TO_KM).pow(3) / (G_CONSTANT * (massKg))), //TODO: add the other stars mass
                null,
                1000.0
        )

        val bodyInfos = CelestialBodyInfo(system, orbitalParameters, radiusKm, massKg, density, rand.nextFloat())

        val position = parentPos + computePosition(orbitalParameters, 0.0)
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

private fun Random.range(min: Double, max: Double): Double {
    return this.nextDouble() * (max - min) + min
}
