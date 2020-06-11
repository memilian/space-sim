package nebulae.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import ktx.math.minus
import ktx.math.plus
import ktx.math.times
import nebulae.kutils.setZ
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.*

class BoundedCameraInputController(private val camera: Camera, var bounds: Rectangle) : InputAdapter() {


    public var target = Vector3()
    var desiredTarget: Vector3? = null
        set(value) {
            timer = 0f
            field = value
        }

    val translateAmount: Float
        get() = 60f * distanceFactor
    val zoomAmount: Float
        get() = 330f * distanceFactor
    val rotateAmount = 360f

    var targetDistance = 100f

    private var desiredDistance = targetDistance
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
            if (futurePitch < 95) {
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
        if (abs(desiredDistance) > epsilon) {
            var sc = desiredDistance * delta
            val toTarget = tmp2.set(camera.position).dst(target)
            if (toTarget < 5) {
                if (toTarget < 1) {
                    sc = toTarget - 0.5f
                    if (sc.absoluteValue < 1.0 && sc != 0.0f) {
                        sc = sc.sign
                    }
                }
                if (desiredDistance > 0) {
                    desiredDistance = 0f
                }
            }
            camera.translate(tmp1.set(camera.direction).nor().scl(sc))
            desiredDistance *= 0.95f
        }

        if (desiredTarget != null) {
            val toTarget = (tmp1.set(desiredTarget) - target)
            val progress = Interpolation.smoother.apply(min(1f, timer / 2f))
            if (toTarget.len2() < 0.000001f) {
                desiredTarget = null
            }
            camera.translate(toTarget * progress)
            target += toTarget
        }

        camera.lookAt(target)
        camera.up.set(Vector3.Z)
        camera.update()
        timer += delta
    }

    private fun updateDistanceFactor() {
        val maxDistEffect = 500f
        val minDistEffect = 10f
        val distanceToTarget = tmp2.set(camera.position).dst(target)
        distanceFactor = max(minDistEffect, min(maxDistEffect, distanceToTarget)) / maxDistEffect
        distanceFactor = max(0.01f, distanceFactor * distanceFactor)
        distanceFactor = log10(distanceFactor * 5 + 1.0).toFloat()
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
        }
        return !isInBounds()
    }

    override fun scrolled(amount: Int): Boolean {
        if (isInBounds()) {
            desiredDistance -= amount * zoomAmount
        }
        return false
    }


    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val dx = (screenX - startX) / Gdx.graphics.width
        val dy = (screenY - startY) / Gdx.graphics.height
        startX = screenX.toFloat()
        startY = screenY.toFloat()
//        if (pressedButton == Input.Buttons.RIGHT) {
        desiredYaw -= dx * rotateAmount
        desiredPitch -= dy * rotateAmount
//        }
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
}