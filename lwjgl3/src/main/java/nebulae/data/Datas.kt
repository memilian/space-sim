package nebulae.data

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import nebulae.data.LuminosityClass.*
import nebulae.kutils.component1
import nebulae.kutils.component2
import nebulae.kutils.v2


interface GameObject {
    val boundingBox: BoundingBox
    val position: Vector3
    val name: String
}

interface ICelestialBody : GameObject {
    val bodyInfos: CelestialBodyInfo

    fun isMoon(): Boolean {
        return this.bodyInfos.orbitalParameters.orbitingBody != null && this.bodyInfos.orbitalParameters.orbitingBody is Planet
    }
}


data class CelestialBodyInfo(
        val system: System,
        var orbitalParameters: OrbitalParameters,
        val radius: Double,
        val mass: Double,
        val density: Double,
        val seed: Float) {

    fun toString(prefix: String): String {
        return """
            |${prefix} radius: $radius km  mass: $mass kg
            |${prefix} orbit: ${orbitalParameters.toString("\t" + prefix)}
        """.trimMargin()
    }

    override fun toString(): String {
        return toString("")
    }
}

data class OrbitalParameters(val eccentricity: Double,
                             var semiMajorAxis: Double,
                             val inclination: Double,
                             val ascendingNode: Double,
                             val periapsisArg: Double,
                             val meanAnomalyAtEpoch: Double,
                             var trueAnomaly: Double,
                             var period: Double,
                             var orbitingBody: ICelestialBody?,
                             val soiRadius: Double) {
    fun toString(prefix: String): String {
        return """ orbiting ${when (orbitingBody == null) {
            true -> "none"
            false -> orbitingBody!!.name
        }}
            |${prefix} eccentricity: $eccentricity  semiMajorAxis: $semiMajorAxis  inclination: $inclination
            |${prefix} ascendingNode: $ascendingNode  periapsisArg: $periapsisArg  meanAnomalyAtEpoch: $meanAnomalyAtEpoch
            |${prefix} trueAnomaly: $trueAnomaly  period: ${period * SEC_TO_DAY} days  
        """.trimMargin()
    }

    override fun toString(): String {
        return toString("")
    }
}

data class Galaxy(val name: String, var sectors: List<Sector>, var arms: MutableList<List<Vector2>>)

/** Game objects **/

data class Sector(override val boundingBox: BoundingBox, override val position: Vector3, override val name: String) : GameObject

data class System(override val boundingBox: BoundingBox,
                  override val position: Vector3,
                  override var name: String,
                  val barycenter: Vector3,
                  val stars: List<Star>,
                  val planets: List<Planet>,
                  val seed: Float
) : GameObject {
    private fun toString(prefix: String): String {
        return """${prefix}System $name
            |${prefix} Barycenter : $barycenter
            |${prefix} stars :
            | ${stars.map { it.toString(prefix + "\t") + "\n" }}
            |${prefix} planets :
            | ${planets.map { it.toString(prefix + "\t") + "\n" }}
        """.trimMargin()
    }

    override fun toString(): String {
        return toString("")
    }

    override fun hashCode(): Int {
        return name.hashCode() + seed.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as System

        if (name != other.name) return false
        if (seed != other.seed) return false

        return true
    }
}

data class Planet(override val boundingBox: BoundingBox,
                  override val position: Vector3,
                  override val name: String,
                  val descriptor: PlanetDescriptor,
                  override val bodyInfos: CelestialBodyInfo) : ICelestialBody {
    fun toString(prefix: String): String {
        return """${prefix}Planet $name
            |${prefix} $position
            |${prefix} $descriptor
            |${prefix} ${bodyInfos.toString(prefix + "\t")}
        """.trimMargin()
    }

    override fun toString(): String {
        return toString("")
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as System

        if (name != other.name) return false

        return true
    }
}

data class Star(override val boundingBox: BoundingBox,
                override val position: Vector3,
                override val name: String,
                val type: StarType,
                override val bodyInfos: CelestialBodyInfo) : ICelestialBody {
    fun toString(prefix: String): String {
        return """${prefix}Star $name ${type.toString("\t" + prefix)}
             |${prefix} $position
             |$prefix   ${bodyInfos.toString(prefix + "\t")}
        """.trimMargin()
    }

    override fun toString(): String {
        return toString("")
    }
}

/** other **/

data class StarType(val spectralClass: SpectralClass, val luminosityClass: LuminosityClass, val temperature: Float, val luminosity: Double, val subClass: Int) {
    fun toString(prefix: String): String {
        return """$prefix${spectralClass.name}${subClass} ${luminosityClass.name} ${luminosityClass.className}
                 |$prefix   luminosity : $luminosity  temperature : ${temperature}K
        """.trimMargin()
    }

    override fun toString(): String {
        return toString("")
    }
}


enum class SpectralClass(val temperatureRange: Vector2, val probability: Float, vararg val excludedLuminosityClasses: LuminosityClass) {
    W(50000f v2 200000f, 0.01f, II, III, IV, V, VI, VII),

    O(30000f v2 50000f, 0.04f),
    B(10000f v2 30000f, 0.1f),
    A(7500f v2 10000f, 0.1f),
    F(6000f v2 7500f, 0.1f),
    G(5200f v2 6000f, 0.1f),
    K(3700f v2 5200f, 0.1f),
    M(2400f v2 3700f, 0.4f),

    L(1300f v2 2400f, 0.1f, Ia, Ib, II, III, IV, VII),
    T(550f v2 1300f, 0.1f, Ia, Ib, II, III, IV, VII),
    Y(250f v2 550f, 0.1f, Ia, Ib, II, III, IV, VII),
    C(3000f v2 10000f, 0.001f);    // Red Giants at the end of their lives

    companion object {
        val lowToHigh = SpectralClass.values().sortedBy { it.probability }
    }
}

fun SpectralClass.getSubClass(temperature: Float): Int {
    val (min, max) = this.temperatureRange
    assert(temperature in min..max);
    var type = 0
    val step = (max - min) / 10
    do {
        if (temperature in (min + step * type)..(min + step * (type + 1))) {
            return type
        }
        type++
    } while (true)
}

/** Resources
 * HR diagram
 * https://i.pinimg.com/originals/e5/a6/fb/e5a6fb5e0f84b921e4f9ce162cdd6f85.jpg
 */
enum class LuminosityClass(val className: String, val probability: Float) {
    Ia("Hypergiant", 0.001f),
    Ib("Supergiant", 0.02f),
    II("Bright Giant", 0.1f),
    III("Giant", 0.135f),
    IV("Subgiant", 0.12f),
    V("Main Sequence Star", 0.4f),
    VI("Subdwarf", 0.1f),
    VII("White Dwarf", 0.08f);

    companion object {
        val lowToHigh = LuminosityClass.values().sortedBy { it.probability }
    }

}

enum class PlanetType() {
    GASEOUS,
    ROCKY
}

data class PlanetDescriptor(val type: PlanetType)