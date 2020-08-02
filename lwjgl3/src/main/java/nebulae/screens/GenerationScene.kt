package nebulae.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.crashinvaders.vfx.VfxManager
import com.crashinvaders.vfx.effects.BloomEffect
import com.crashinvaders.vfx.effects.FxaaEffect
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.app.use
import ktx.assets.disposeSafely
import ktx.graphics.use
import ktx.math.times
import nebulae.data.*
import nebulae.generation.Settings
import nebulae.generation.GalaxyGenerator
import nebulae.input.BoundedCameraInputController
import nebulae.kutils.BufferedList
import nebulae.kutils.minus
import nebulae.kutils.smoothstep
import nebulae.orbital.computePosition
import nebulae.rendering.*
import nebulae.rendering.renderers.*
import nebulae.selection.Selection
import nebulae.universe.Universe
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB
import org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_SRGB
import org.lwjgl.opengl.GL40

const val SKYBOX_SIZE = 2048f

class GenerationScene(private val generator: GalaxyGenerator, val universe: Universe) : VisWindow("") {


    private var backBuffer = FrameBuffer(Pixmap.Format.RGBA8888, width.toInt(), height.toInt(), true)
    private var backBufferRegion = TextureRegion(backBuffer.colorBufferTexture)
    private val fontAndromeda = BitmapFont(Gdx.files.internal("fonts/andromeda.fnt"), true)
    private val fontCloseness = BitmapFont(Gdx.files.internal("fonts/closeness.fnt"), true)
    private val fontDiscognate = BitmapFont(Gdx.files.internal("fonts/discognate.fnt"), true)
    private val fontSpartakus = BitmapFont(Gdx.files.internal("fonts/spartakus.fnt"), true)
    private var needsResize = false
    private val camera = PerspectiveCamera(67f, 1366f, 768f)

    private val orthoCamera = OrthographicCamera(1366f, 768f)
    private val orthoCam = OrthographicCamera()
    private val viewportScreen = ScreenViewport(camera)
    private val starTexture = TextureRegion(Texture(Gdx.files.internal("textures/star.png")))
    private val starTexturePre = TextureRegion(Texture(Gdx.files.internal("textures/star_pre.png")))
    private val pixelTexture = TextureRegion(Texture(Gdx.files.internal("textures/pixel.png")))

    private var skyboxTextures = mutableListOf<TextureRegion>()
    private var skyboxNeedsUpdate = false

    private val decalBatch = DecalBatch(100, CameraGroupStrategy(camera))
    private val fixedBatch = FixedStarBatch()
    private val modelBatch = ModelBatch()

    private val builder = ModelBuilder()
    private val polygonSpriteBatch = PolygonSpriteBatch()
    private val spriteBatch = SpriteBatch()
    val cameraInputController = BoundedCameraInputController(camera, Rectangle())

    val vfx = VfxManager(Pixmap.Format.RGBA8888)
    val bloom = BloomEffect()

    private var focusedSelection: Selection<GameObject>? = null
        set(value) {
            cameraInputController.desiredTarget = value?.item!!.position.cpy()
            selectionRenderer.focusedSelection = value
            if (value.item is System) {
                orbitRenderer.createOrbits(value.item.let { it.stars + it.planets }.map { it.bodyInfos })
            }
            println("Selected " + value.item)
            field = value
        }
    private var hoveredSelection: Selection<GameObject>? = null

    private val focusedNeighbors = mutableListOf<System>()
    private val focusedNeighborsConnections = mutableListOf<ModelInstance>()

    private lateinit var nebulaeRenderer: NebulaeRenderer
    private lateinit var skyboxRenderer: SkyboxRenderer
    private lateinit var starfieldRenderer: StarFieldRenderer
    private lateinit var starfieldRendererForSkybox: StarFieldRenderer
    private lateinit var octreeRenderer: OctreeRenderer
    private lateinit var starRenderer: StarRenderer
    private lateinit var planetRenderer: PlanetRenderer
    private lateinit var selectionRenderer: SelectionRenderer
    private lateinit var orbitRenderer: OrbitRenderer
    private lateinit var debugRenderer: DebugRenderer

    private var intersectingNodes: MutableList<Octree> = mutableListOf()

    private val tmp = Vector3()
    private val tmp2 = Vector3()

    private var viewMode = ViewMode.GALAXY

    init {
        initialize()
    }

    private fun initialize() {

        vfx.addEffect(bloom)
        vfx.addEffect(FxaaEffect())

        backBufferRegion.flip(false, true)

        camera.position.set(150f, 150f, 150f)
        camera.up.set(0f, 0f, 1f)
        camera.near = 0.1f
        camera.far = 100000f
        camera.lookAt(0f, 0f, 0f)
        camera.update()


        nebulaeRenderer = NebulaeRenderer(spriteBatch, orthoCam, modelBatch, camera)
        skyboxRenderer = SkyboxRenderer(modelBatch)
        starfieldRenderer = StarFieldRenderer(polygonSpriteBatch, orthoCamera, fixedBatch, width, height)
        starfieldRendererForSkybox = StarFieldRenderer(polygonSpriteBatch, orthoCamera, fixedBatch, 1024f, 1024f)
        octreeRenderer = OctreeRenderer(intersectingNodes, modelBatch)
        starRenderer = StarRenderer(modelBatch)
        planetRenderer = PlanetRenderer(modelBatch)
        selectionRenderer = SelectionRenderer(camera, orthoCam, decalBatch, modelBatch, fontAndromeda)
        orbitRenderer = OrbitRenderer(modelBatch)
        debugRenderer = DebugRenderer(modelBatch)

    }

    private fun updateGeneration() {
        val it = generator.systemsIt
        for (i in 0 until 250) {
            if (it.hasNext()) {
                fixedBatch.add(StarDecal(it.next().stars[0], 0.94f))
                nebulaeRenderer.zoneRadius = generator.farthestStarDistance
            }
        }
    }


    override fun draw(_batch: Batch?, parentAlpha: Float) {
        super.draw(_batch, parentAlpha)
        handleResize()

        if (generator.galaxy == null) {
            return
        }
        suspendBatch(_batch!!)

        stage.viewport.apply()

        if (skyboxNeedsUpdate) {
            renderSkybox(focusedSelection!!.item)
            skyboxRenderer.textures = skyboxTextures
            skyboxNeedsUpdate = false
        }

        if (viewMode == ViewMode.GALAXY) {
            if (focusedSelection != null) {
                starfieldRenderer.scale = 1f + 8 * camera.position.dst(focusedSelection!!.item.position).smoothstep(10f, 0f)
                starfieldRenderer.position = focusedSelection!!.item.position
            }
            starfieldRenderer.renderToFramebuffer(camera)
            nebulaeRenderer.renderToFramebuffer(camera)
        }

        beginScene()
        backBuffer.use {
            val col = 0.0f
            Gdx.gl.glClearColor(col, col, col, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

            if (viewMode == ViewMode.GALAXY) {
                starfieldRenderer.renderToScreen(camera)
                nebulaeRenderer.renderToScreen(camera)
            } else {
                skyboxRenderer.renderToScreen(camera)
            }

            if (focusedSelection?.item is System) {
                @Suppress("UNCHECKED_CAST")
                starRenderer.selection = focusedSelection as Selection<System>?
                starRenderer.viewMode = viewMode
                starRenderer.renderToScreen(camera)
                @Suppress("UNCHECKED_CAST")
                planetRenderer.selection = focusedSelection as Selection<System>?
                planetRenderer.viewMode = viewMode
                planetRenderer.renderToScreen(camera)
            }
            if (focusedSelection != null && focusedSelection!!.item is System) {
                orbitRenderer.renderToScreen(camera);
            }

            debugRenderer.renderToScreen(camera);
        }

        bloom.baseIntensity = Settings.graphics.baseIntensity
        bloom.baseSaturation = Settings.graphics.baseSaturation
        bloom.blurAmount = Settings.graphics.blurAmount
        bloom.blurPasses = Settings.graphics.blurPasses
        bloom.threshold = Settings.graphics.threshold
        bloom.bloomIntensity = Settings.graphics.bloomIntensity
        bloom.bloomSaturation = Settings.graphics.bloomSaturation
        vfx.useAsInput(backBufferRegion.texture)
        vfx.applyEffects()
        vfx.renderToScreen()
        if (viewMode == ViewMode.GALAXY) {
            octreeRenderer.renderToScreen(camera)
            selectionRenderer.renderToScreen(camera)
        }

//        Gdx.gl.glEnable(GL40.GL_FRAMEBUFFER_SRGB)
        orthoCamera.setToOrtho(false, width, height)
        orthoCamera.update()
//        polygonSpriteBatch.projectionMatrix = orthoCamera.combined
//        polygonSpriteBatch.use {
//            it.disableBlending()
//            it.draw(backBufferRegion, 0f, 0f)
//            it.flush()
//        }
//        Gdx.gl.glDisable(GL40.GL_FRAMEBUFFER_SRGB)
        endScene(_batch)
    }


    private var ray: Ray? = null
    var gameTime = 0.1
    override fun act(delta: Float) {
        super.act(delta)
        updateGeneration()
        gameTime += delta * Settings.game.timeSpeed
        if (viewMode == ViewMode.SYSTEM && focusedSelection?.item is System) {
            for (star in (focusedSelection!!.item as System).stars) {
                star.position.set(computePosition(star.bodyInfos.orbitalParameters, gameTime, AU_TO_SYSTEM))
            }
            for (planet in (focusedSelection!!.item as System).planets) {
                planet.position.set(computePosition(planet.bodyInfos.orbitalParameters, gameTime, AU_TO_SYSTEM))
            }
        } else if (viewMode == ViewMode.GALAXY && focusedSelection?.item is System) {
            for (star in (focusedSelection!!.item as System).stars) {
                star.position.set(computePosition(star.bodyInfos.orbitalParameters, gameTime, 2.5))
            }
            for (planet in (focusedSelection!!.item as System).planets) {
                planet.position.set(computePosition(planet.bodyInfos.orbitalParameters, gameTime, 2.5))
            }
        }

        if (focusedSelection != null) {
            focusedSelection!!.selectionTimer += delta.toLong()
            val dst = camera.position.dst(focusedSelection!!.item.position)
            if (dst < 4f && viewMode == ViewMode.GALAXY && !cameraInputController.isMovingToTarget()) {
                switchToSystemView()
            } else if (dst > 100000f && viewMode == ViewMode.SYSTEM) {
                switchToGalaxyView()
            }
        }
        if (hoveredSelection != null) {
            hoveredSelection!!.selectionTimer += delta.toLong()
        }

        cameraInputController.bounds.set(x, y, width, height)
        cameraInputController.autoRotate = Settings.debug.autoRotate
        cameraInputController.update()
        if (cameraInputController.isInBounds()) {
//            this.stage.unfocusAll()

            if (viewMode == ViewMode.GALAXY) {
                pickStarSelection()
                if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
                    if (focusedNeighbors.isNotEmpty()) {
                        focusedSelection = Selection(focusedNeighbors.random())
                    }
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) && focusedSelection != null) {
                    switchToSystemView()
                }
            } else if (viewMode == ViewMode.SYSTEM) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
                    switchToGalaxyView()
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
                    if (focusedSelection?.item is System) {
                        val system = focusedSelection!!.item as System
                        cameraInputController.target = system.planets.random().position
                    }
                }
            }
        }
    }

    private fun switchToSystemView() {
        println("switch view mode to SYSTEM")
        viewMode = ViewMode.SYSTEM
        skyboxNeedsUpdate = true
        cameraInputController.killScroll();
        cameraInputController.desiredTarget = Vector3()
        cameraInputController.resetTimer();
        camera.position.set(0f, 0f, 0f)
        cameraInputController.setTargetDistanceImmediate(5f);
    }

    private fun switchToGalaxyView() {
        cameraInputController.desiredTarget = focusedSelection!!.item.position.cpy()
        camera.position.set(focusedSelection!!.item.position.cpy())
        cameraInputController.setTargetDistanceImmediate(5f);
        cameraInputController.killScroll();
        cameraInputController.resetTimer();
        viewMode = ViewMode.GALAXY
        println("switch view mode to GALAXY")
    }

    private fun pickStarSelection() {
        val mx = Gdx.input.x.toFloat()
        val my = Gdx.input.y.toFloat()
        viewportScreen.setWorldSize(width, height)
        val sx = viewportScreen.screenX
        val sy = viewportScreen.screenY
        ray = camera.getPickRay((mx), (my), sx.toFloat(), sy.toFloat(), viewportScreen.worldWidth.toFloat(), viewportScreen.worldHeight.toFloat())

        intersectingNodes.clear()
        generator.octree.intersect(ray!!, intersectingNodes)
        val intersection = Vector3()
        var selection: System? = null
        var prevDistToIntersection = 1000f
        var prevDistToCam = 1000f

        // Ray pick candidates based on mouse ray and distance to camera
        for (node in intersectingNodes) {
            for (system in node.getObjects<System>()) {
                if (Intersector.intersectRaySphere(ray, system.position, 0.5f, intersection)) {
                    if (selection == null) {
                        selection = system
                        continue
                    }
                    viewportScreen.project(intersection)
                    viewportScreen.project(tmp.set(system.position))
                    val dst = intersection.dst2(tmp)
                    if (dst < prevDistToIntersection && dst < prevDistToCam) {
                        selection = system
                        prevDistToIntersection = dst
                        prevDistToCam = tmp.set(camera.position).dst2(system.position)
                    }
                }
            }
        }
        if (selection != null) {
            hoveredSelection = Selection(selection)
            selectionRenderer.hoveredSelection = hoveredSelection
        } else {
            selectionRenderer.hoveredSelection = null
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (selection != null) {
                focusedSelection = Selection(selection)

                focusedNeighbors.clear()
                val radius = 5f
                generator.octree.intersectSphere(selection.position, radius).forEach {
                    val stars = it.getObjects<System>().filter { system ->
                        system.position.dst2(selection.position) < radius * radius && system != selection
                    }
                    focusedNeighbors.addAll(stars)
                }
                focusedNeighborsConnections.forEach {
                    it.model.disposeSafely()
                }
                focusedNeighborsConnections.clear()
                if (focusedNeighbors.isNotEmpty()) {
                    builder.begin()
                    val material = Material()
                    material.set(BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.55f))
                    val part = builder.part("line", GL20.GL_LINES, (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong(), material)
                    part.setColor(1f, 1f, 1f, 1f)
                    for (star in focusedNeighbors) {
                        tmp2.set(selection.position)
                        tmp2.set(tmp2 - star.position)
                        val offset = tmp2.nor() * 0.2f
                        part.line(tmp.set(selection.position) - offset, star.position)
                    }
                    val model = builder.end()
                    focusedNeighborsConnections.add(ModelInstance(model))
                }
            }
        }
    }

    private val fbs = mutableListOf<FrameBuffer>()

    init {
        for (i in 0 until 6) {
            fbs.add(FrameBuffer(Pixmap.Format.RGBA8888, SKYBOX_SIZE.toInt(), SKYBOX_SIZE.toInt(), false))
        }
    }

    private fun renderSkybox(obj: GameObject) {
        val cam = PerspectiveCamera(90f, SKYBOX_SIZE, SKYBOX_SIZE)
        cam.position.set(obj.position)
        cam.update()
        val directions = listOf(
                Vector3.Z.cpy().times(-1),
                Vector3.Z,
                Vector3.Y.cpy().times(-1),
                Vector3.Y,
                Vector3.X.cpy().times(-1),
                Vector3.X
        )
///// uncomment the 3 blocks to draw buffer number on textures
//        val sb = SpriteBatch()
//        val oc = OrthographicCamera()
        for ((i, dir) in directions.withIndex()) {
            cam.direction.set(dir)
            cam.up.set(when (dir.z) {
                1f -> Vector3.X
                -1f -> Vector3.X.cpy().times(-1)
                else -> Vector3.Z
            })
            cam.update()
            starfieldRendererForSkybox.renderToFramebuffer(cam)
            nebulaeRenderer.renderToFramebuffer(cam)
//            oc.setToOrtho(true, fbs[i].width.toFloat(), fbs[i].height.toFloat())
//            oc.update()
//            sb.projectionMatrix = oc.combined
//            sb.color = Color.WHITE
            fbs[i].begin()
            Gdx.gl20.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            starfieldRendererForSkybox.renderToScreen(cam)
            nebulaeRenderer.renderToScreen(cam)

//            sb.use {
//                val scale = 0.5f
//                fontAndromeda.data.scaleX = scale
//                fontAndromeda.data.scaleY = scale
//                fontAndromeda.draw(sb, "buffer $i", 0f, 512f, 1024f, Align.center, true)
//            }
            fbs[i].end()
        }
        if (skyboxTextures.isEmpty()) {
            for (fb in fbs) {
                val region = TextureRegion(fb.colorBufferTexture)
                region.flip(true, true)
                skyboxTextures.add(region)
            }
        }
    }

    override fun remove(): Boolean {
        nebulaeRenderer.disposeSafely()
        starfieldRenderer.disposeSafely()
        starfieldRendererForSkybox.disposeSafely()
        skyboxRenderer.disposeSafely()
        selectionRenderer.disposeSafely()
        starRenderer.disposeSafely()
        planetRenderer.disposeSafely()
        debugRenderer.disposeSafely()
        orbitRenderer.disposeSafely()

        decalBatch.disposeSafely()
        polygonSpriteBatch.disposeSafely()
        fixedBatch.disposeSafely()
        modelBatch.disposeSafely()
        starTexture.texture.dispose()
        starTexturePre.texture.dispose()
        pixelTexture.texture.dispose()
        generator.octree.disposeSafely()

        Gdx.input.inputProcessor = stage
        return super.remove()
    }

    fun resize(width: Float, height: Float) {
        setSize(width, height)
        needsResize = true
    }

    /**
     * Restore viewport
     */
    private fun endScene(_batch: Batch) {
//        ScissorStack.popScissors()
//        stage.viewport.apply()

        restoreBatch(_batch)
    }

    /**
     * Update viewport dimensions to fit the VisWindow we are drawing in
     */
    private fun beginScene() {
        val w = width.toInt() - 2
        val h = height.toInt() - 2
//        viewportScreen.update(w, h, false)
//        viewportScreen.setScreenBounds(x.toInt(), y.toInt(), w, h)
//        viewportScreen.apply()

//        val scissors = Rectangle()
//        ScissorStack.calculateScissors(stage.camera, identity, Rectangle(x + 1, y + 1, w.toFloat(), h.toFloat()), scissors)
//        ScissorStack.pushScissors(scissors)
    }

    private fun suspendBatch(_batch: Batch) {
        if (_batch.isDrawing) {
            _batch.end()
        }
    }

    private fun restoreBatch(_batch: Batch) {
        if (!_batch.isDrawing) {
            _batch.begin()
        }
    }

    private fun handleResize() {
        if (needsResize) {
            starfieldRenderer.handleResize(width, height)
            starRenderer.handleResize(width, height)
            backBuffer.dispose()
            backBuffer = FrameBuffer(Pixmap.Format.RGBA8888, width.toInt(), height.toInt(), true)
            backBufferRegion.texture = backBuffer.colorBufferTexture
            backBufferRegion.regionWidth = width.toInt()
            backBufferRegion.regionHeight = height.toInt()
            needsResize = false
            println("resized backbuffer to $width  $height")
            for (camera in listOf<Camera>(camera, orthoCam, orthoCamera)) {
                camera.viewportWidth = stage.viewport.worldWidth
                camera.viewportHeight = stage.viewport.worldHeight
                camera.update()
            }
            vfx.resize(width.toInt(), height.toInt())
        }
    }
}

class StarDecal(private val star: Star, val size: Float) {
    companion object {
        const val SIZE = 4
    }

    val vertices = FloatArray(SIZE)

    init {
        vertices[0] = star.bodyInfos.system.position.x
        vertices[1] = star.bodyInfos.system.position.y
        vertices[2] = star.bodyInfos.system.position.z
        vertices[3] = star.type.temperature
    }
}

enum class ViewMode {
    GALAXY,
    SYSTEM
}