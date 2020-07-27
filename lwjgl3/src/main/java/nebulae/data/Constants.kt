package nebulae.data

/** Physical constants **/
const val G_CONSTANT = 6.67408e-11f

/** Mass units **/
const val SOLAR_MASS_TO_KG = 2e30f

/** Distance units **/
const val SOLAR_RADIUS_TO_KM = 6.957e5f
const val KM_TO_AU = 6.684587e-9f
const val AU_TO_KM = 149597900f
const val AU_TO_M = AU_TO_KM * 1000f

/** Game distance units **/
const val KM_TO_SYSTEM = 1e-4f
const val AU_TO_SYSTEM = AU_TO_KM * KM_TO_SYSTEM

/** Time units **/
const val SEC_TO_DAY = 1f / 86400f