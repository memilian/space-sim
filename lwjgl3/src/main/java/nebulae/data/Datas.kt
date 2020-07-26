package nebulae.data

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import nebulae.data.LuminosityClass.*
import nebulae.kutils.component1
import nebulae.kutils.component2
import nebulae.kutils.v2


open class GameObject(open val boundingBox: BoundingBox, val position: Vector3, open val name: String = "") {
}

data class Galaxy(val name: String, var sectors: List<Sector>, var arms: MutableList<List<Vector2>>)

/** Game objects **/
data class Sector(override val boundingBox: BoundingBox, override val name: String) : GameObject(boundingBox, Vector3(boundingBox.centerX, boundingBox.centerY, boundingBox.centerZ))

data class System(override val boundingBox: BoundingBox, val barycenter: Vector3, val stars: List<Star>, val planets: List<Planet>, override val name: String) : GameObject(boundingBox, Vector3(boundingBox.centerX, boundingBox.centerY, boundingBox.centerZ))

data class Planet(override val boundingBox: BoundingBox, override val name: String, val descriptor: PlanetDescriptor, val orbitalParameters: OrbitalParameters) : GameObject(boundingBox, Vector3(boundingBox.centerX, boundingBox.centerY, boundingBox.centerZ))

data class Star(override val boundingBox: BoundingBox, override val name: String, val radius: Float, val mass: Float, val type: StarType, val orbitalParameters: OrbitalParameters? = null) : GameObject(boundingBox, Vector3(boundingBox.centerX, boundingBox.centerY, boundingBox.centerZ)) {
    override fun toString(): String {
        return """$name $type 
            |   radius : $radius sun radii  mass : $mass sun mass
        """.trimMargin()
    }
}

/** other **/
data class StarType(val spectralClass: SpectralClass, val luminosityClass: LuminosityClass, val temperature: Float, val luminosity: Float, val subClass: Int) {

    override fun toString(): String {
        return """${spectralClass.name}${subClass} ${luminosityClass.name} ${luminosityClass.className}
            |   luminosity : $luminosity  temperature : ${temperature}K
        """.trimMargin()
    }
}

data class OrbitalParameters(val eccentricity: Float, val semiMajorAxis: Float, val inclination: Float, val ascendingNode: Float, val periapsisArg: Float, var trueAnomaly: Float)

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

data class PlanetDescriptor(val type: PlanetType, val radius: Float)