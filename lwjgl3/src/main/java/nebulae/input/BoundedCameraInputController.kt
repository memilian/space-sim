package nebulae.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import ktx.math.minus
import ktx.math.plus
import ktx.math.times
import nebulae.kutils.setZ
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.*

class BoundedCameraInputController(private val camera: Camera, var bounds: Rectangle) : InputAdapter() {


    var followTarget = false
    private val restrictPitch = false
    var target = Vector3()
    var desiredTarget: Vector3? = null
        set(value) {
            timer = 0f
            field = value
        }

    private val translateAmount: Float
        get() = distanceFactor
    private val zoomAmount: Float
        get() = distanceFactor
    private val rotateAmount = 360f

    var targetDistance = 100f

    private var zoomVelocity = targetDistance
    private var desiredTranslation = Vector3()
    private var desiredYaw = 0f
    private var desiredPitch = 50f

    val upKey = Input.Keys.W
    val downKey = Input.Keys.S
    val leftKey = Input.Keys.A
    val rightKey = Input.Keys.D
    var autoRotate = true

    private var upPressed = false;
    private var downPressed = false;
    private var leftPressed = false;
    private var rightPressed = false;
    private var startX = 0f
    private var startY = 0f
    private var pressedButton = -1
    private val epsilon = 0.01f

    private val tmp1 = Vector3()
    private val tmp2 = Vector3()

    private var distanceFactor = 1.0f
    private var timer = 0f
    private val timeToFocusTarget = 2f;

    private val ray = Ray()

    fun isMovingToTarget(): Boolean {
        return timer < timeToFocusTarget;
    }

    fun update() {
        updateDistanceFactor()
        val right = tmp1.set(camera.direction).setZ(0f).crs(Vector3.Z)
        if (leftPressed) {
            desiredTranslation -= right.nor().scl(translateAmount)
        }
        if (rightPressed) {
            desiredTranslation += right.nor().scl(translateAmount)
        }
        if (upPressed) {
            desiredTranslation -= right.nor().crs(Vector3.Z).scl(translateAmount)
        }
        if (downPressed) {
            desiredTranslation += right.nor().crs(Vector3.Z).scl(translateAmount)
        }
        val delta = Gdx.graphics.deltaTime


        var localYaw = desiredYaw
        if (autoRotate && abs(desiredYaw) < 0.5f) {
            localYaw = 4f
        }
        val deltaYaw = delta * localYaw
        camera.rotateAround(target, Vector3.Z, deltaYaw)
        desiredYaw *= 0.95f

        if (abs(desiredPitch) > epsilon) {
            tmp2.set(camera.direction).nor()
            val pitch = MathUtils.radiansToDegrees * atan2(abs(tmp2.y), tmp2.z)
            var deltaRotation = desiredPitch * delta
            val futurePitch = deltaRotation + pitch
            if (futurePitch < 95 && restrictPitch) {
                if (pitch < 90) {
                    deltaRotation = pitch - 90.10f
                }
                desiredPitch = 0f
            }
            if (futurePitch > 170) {
                if (pitch > 176) {
                    deltaRotation = 180.0f - pitch
                }
                desiredPitch = 0f
            }
            camera.rotateAround(target, tmp1.set(camera.direction).crs(Vector3.Z), deltaRotation)

            desiredPitch *= 0.95f
        }
        if (!desiredTranslation.epsilonEquals(Vector3.Zero, 0.0001f)) {
            tmp1.set(desiredTranslation).scl(delta)
            target += tmp1
            camera.translate(tmp1)
            desiredTranslation.scl(0.905f)
        }
        if (abs(zoomVelocity) > epsilon) {
            var sc = zoomVelocity * delta
            tmp1.set(camera.direction).nor().scl(sc)
            ray.direction.set(camera.direction)
            ray.origin.set(camera.position)
            Intersector.intersectRaySphere(ray, target, 0.5f, tmp2)
            if (camera.position.dst(tmp2) < sc) {
                sc = camera.position.dst(tmp2) * 0.5f
                zoomVelocity = 0f
            } else {
                camera.translate(tmp1)
            }
            zoomVelocity *= 0.95f
        }

        if (desiredTarget != null) {
            val toTarget = (tmp1.set(desiredTarget) - target)
            val progress = Interpolation.smoother.apply(min(1f, timer / timeToFocusTarget))
            if (toTarget.len2() < 0.000001f) {
                desiredTarget = null
            }
            camera.translate(toTarget * progress)
            target += toTarget //TODO cleanup and correctly handle target tracking separately from this
        }

        if (followTarget) {
            setTargetDistanceImmediate(targetDistance)
        }

        camera.lookAt(target)
        camera.up.set(Vector3.Z)
        camera.update()
        timer += delta
    }

    fun setTargetDistanceImmediate(dist: Float) {
        this.targetDistance = dist
        val currentDistance = camera.position.dst(target)
        camera.lookAt(target)
        camera.translate(tmp1.set(camera.direction).nor().scl(currentDistance - dist))
    }

    private fun updateDistanceFactor() {
        val distanceToTarget = tmp2.set(camera.position).dst(target)
        distanceFactor = max(0.0f, distanceToTarget)
        distanceFactor = log10(distanceToTarget * distanceToTarget + 2.0).toFloat() * distanceToTarget * 0.1f
    }

    override fun keyDown(keycode: Int): Boolean {
        if (!isInBounds()) return false
        when (keycode) {
            leftKey -> leftPressed = true
            rightKey -> rightPressed = true
            upKey -> upPressed = true
            downKey -> downPressed = true
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            leftKey -> leftPressed = false
            rightKey -> rightPressed = false
            upKey -> upPressed = false
            downKey -> downPressed = false
            Input.Keys.F -> followTarget = !followTarget
        }
        return !isInBounds()
    }

    override fun scrolled(dir: Int): Boolean {
        if (isInBounds()) {
            var amount = dir
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
                amount *= 10
            }
            if (followTarget) {
                targetDistance += amount * zoomAmount * 0.1f
            }
            zoomVelocity -= amount * zoomAmount
        }
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val dx = (screenX - startX) / Gdx.graphics.width
        val dy = (screenY - startY) / Gdx.graphics.height
        startX = screenX.toFloat()
        startY = screenY.toFloat()
        desiredYaw -= dx * rotateAmount
        desiredPitch -= dy * rotateAmount
        return isInBounds()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        startX = screenX.toFloat()
        startY = screenY.toFloat()
        pressedButton = button
        return isInBounds() && super.touchDown(screenX, screenY, pointer, button)
    }

    fun isInBounds(): Boolean {
        return bounds.contains(Gdx.input.x.toFloat(), Gdx.graphics.height - Gdx.input.y.toFloat())
    }

    fun killScroll() {
        zoomVelocity = 0f
    }

    fun resetTimer() {
        timer = 10000f;
    }

}