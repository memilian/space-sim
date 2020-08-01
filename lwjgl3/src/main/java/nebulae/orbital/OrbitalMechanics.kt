package nebulae.orbital

import com.badlogic.gdx.math.Vector3
import ktx.log.logger
import ktx.math.times
import nebulae.Nebulae
import nebulae.data.OrbitalParameters
import kotlin.math.*

/**
 * Resources :
 * https://stjarnhimlen.se/comp/ppcomp.html#6
 */
val log = logger<Nebulae>()

fun solveEccentricAnomaly(ecc: Double, M: Double): Double {
    if (ecc >= 0.98) {
        log.error { "Cannot solve eccentric anomaly for eccentricity >= 0.98" }
        // If needed implement https://stjarnhimlen.se/comp/ppcomp.html#19
        return 0.0
    }
    var e1 = M + ecc * sin(M) * (1.0 + ecc * cos(M))
    if (ecc < 0.05) {
        return e1
    }
    var e0: Double
    do {
        e0 = e1
        e1 = e0 - (e0 - ecc * sin(e0) - M) / (1 - ecc * cos(e0))
    } while (abs(e0 - e1) > 0.001)
    return e0
}

fun computeDistanceAndTrueAnomaly(orbit: OrbitalParameters, meanAnomaly: Double): Pair<Double, Double> {

    val eccentricAnomaly = solveEccentricAnomaly(orbit.eccentricity, meanAnomaly)
    val xv = orbit.semiMajorAxis * (cos(eccentricAnomaly) - orbit.eccentricity)
    val yv = orbit.semiMajorAxis * (sqrt(1f - orbit.eccentricity * orbit.eccentricity) * sin(eccentricAnomaly))

    val trueAnomaly = atan2(yv, xv)
    val distance = sqrt(xv * xv + yv * yv)
    return trueAnomaly to distance
}

fun computePosition(orbit: OrbitalParameters, time: Double, convertFromAu: Double = 1.0): Vector3 {
    val meanAnomaly = orbit.meanAnomalyAtEpoch + 2f * PI * time / orbit.period
    val (trueAnomaly, distance) = computeDistanceAndTrueAnomaly(orbit, meanAnomaly)
    val x = distance * (cos(orbit.ascendingNode) * cos(trueAnomaly + orbit.periapsisArg) - sin(orbit.ascendingNode) * sin(trueAnomaly + orbit.periapsisArg) * cos(orbit.inclination))
    val y = distance * (sin(orbit.ascendingNode) * cos(trueAnomaly + orbit.periapsisArg) + cos(orbit.ascendingNode) * sin(trueAnomaly + orbit.periapsisArg) * cos(orbit.inclination))
    val z = distance * (sin(trueAnomaly + orbit.periapsisArg) * sin(orbit.inclination))
    return Vector3(x.toFloat(), y.toFloat(), z.toFloat()) * convertFromAu.toFloat()
}
