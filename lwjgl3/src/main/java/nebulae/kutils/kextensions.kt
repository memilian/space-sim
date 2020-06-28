package nebulae.kutils

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import java.util.*

infix fun Number.v2(other: Number): Vector2 {
    return Vector2(this.toFloat(), other.toFloat())
}

operator fun Vector2.component1(): Float = this.x
operator fun Vector2.component2(): Float = this.y

operator fun Vector3.minus(value: Float): Vector3 {
    this.x -= value
    this.y -= value
    this.z -= value
    return this
}

operator fun Vector3.minus(value: Vector3): Vector3 {
    this.x -= value.x
    this.y -= value.y
    this.z -= value.z
    return this
}

operator fun Vector2.plus(value: Float): Vector2 {
    this.x += value
    this.y += value
    return this
}


operator fun Vector3.plus(value: Float): Vector3 {
    this.x += value
    this.y += value
    this.z += value
    return this
}

operator fun Vector3.plus(value: Vector3): Vector3 {
    this.x += value.x
    this.y += value.y
    this.z += value.z
    return this
}

operator fun Vector2.minus(value: Float): Vector2 {
    this.x -= value
    this.y -= value
    return this
}

fun Vector3.toArray(): DoubleArray {
    return doubleArrayOf(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun Vector3.toXYArray(): DoubleArray {
    return doubleArrayOf(this.x.toDouble(), this.y.toDouble())
}

infix fun Vector2.v3(other: Number): Vector3 {
    return Vector3(this.x, this.y, other.toFloat())
}

fun Vector2.toVector3(other: Number): Vector3 {
    return Vector3(this.x, this.y, other.toFloat())
}

fun Vector3.setX(value: Float): Vector3 {
    this.x = value
    return this
}

fun Vector3.setY(value: Float): Vector3 {
    this.y = value
    return this
}

fun Vector3.setZ(value: Float): Vector3 {
    this.z = value
    return this
}

fun Vector3.xy(): Vector2 {
    return Vector2(x, y)
}


fun Float.smoothstep(vmin: Float, vmax: Float): Float {
    val x = MathUtils.clamp((this - vmin) / (vmax - vmin), 0f, 1f)
    return x * x * (3 - 2 * x)
}