package nebulae.data

/** Physical constants **/
const val G_CONSTANT: Double = 6.67408e-11

/** Mass units **/
const val SOLAR_MASS_TO_KG: Double = 2e30

/** Distance units **/
const val SOLAR_RADIUS_TO_KM: Double = 6.957e5
const val KM_TO_AU: Double = 6.684587e-9
const val AU_TO_KM: Double = 149597900.0
const val AU_TO_M: Double = AU_TO_KM * 1000.0

/** Game distance units **/
const val KM_TO_SYSTEM: Double = 1e-6
const val AU_TO_SYSTEM: Double = AU_TO_KM * KM_TO_SYSTEM + 1

/** Time units **/
const val SEC_TO_DAY: Double = 1 / 86400.0