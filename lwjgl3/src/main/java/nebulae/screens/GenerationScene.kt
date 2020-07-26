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
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.assets.disposeSafely
import ktx.math.times
import nebulae.data.GameObject
import nebulae.data.Octree
import nebulae.data.Star
import nebulae.data.System
import nebulae.generation.Settings
import nebulae.generation.GalaxyGenerator
import nebulae.input.BoundedCameraInputController
import nebulae.kutils.BufferedList
import nebulae.kutils.minus
import nebulae.rendering.*
import nebulae.rendering.renderers.*
import nebulae.selection.Selection
import nebulae.universe.Universe

const val SKYBOX_SIZE = 2048f;

class GenerationScene(private val generator: GalaxyGenerator, val universe: Universe) : VisWindow("") {

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
    private val cameraInputController = BoundedCameraInputController(camera, Rectangle())
    private val multiplexer: InputMultiplexer by lazy { InputMultiplexer(stage, cameraInputController) }
    private val stars = BufferedList<StarDecal>()

    private var focusedSelection: Selection<GameObject>? = null
        set(value) {
            cameraInputController.desiredTarget = value?.item!!.position.cpy()
            println("Selected " + value.item.name)
            if (value.item is System) {
                for (star in value.item.stars) {
                    println("""     $star""")
                }
            }
            field = value
        }
    private var hoveredSelection: Selection<GameObject>? = null

    private val focusedNeighbors = mutableListOf<System>()
    private val focusedNeighborsConnections = mutableListOf<ModelInstance>()

    private var nebulaeRenderer: NebulaeRenderer? = null
    private var skyboxRenderer: SkyboxRenderer? = null
    private var starfieldRenderer: StarFieldRenderer? = null
    private var starfieldRendererForSkybox: StarFieldRenderer? = null
    private var octreeRenderer: OctreeRenderer? = null
    private var starRenderer: StarRenderer? = null
    private var selectionRenderer: SelectionRenderer? = null

    private var intersectingNodes: MutableList<Octree> = mutableListOf()

    private val tmp = Vector3()
    private val tmp2 = Vector3()

    private var viewMode = ViewMode.GALAXY

    init {
        initialize()
    }

    private fun initialize() {

        camera.position.set(150f, 150f, 150f)
        camera.up.set(0f, 0f, 1f)
        camera.near = 1f
        camera.far = 10000f
        camera.lookAt(0f, 0f, 0f)
        camera.update()

        setResizeBorder(10)
        isResizable = true
        generator.generationFinished.clear()
        generator.generationFinished += { systemList ->

            stars.clear()
            systemList.forEach() {
                val decal = StarDecal(it.stars[0], 0.94f)
                stars.add(decal)
            }
            stars.update()
            fixedBatch.set(stars)

            nebulaeRenderer = NebulaeRenderer(spriteBatch, orthoCam, modelBatch, camera, generator.farthestStarDistance)
            skyboxRenderer = SkyboxRenderer(modelBatch)
            starfieldRenderer = StarFieldRenderer(polygonSpriteBatch, orthoCamera, fixedBatch, width, height)
            starfieldRendererForSkybox = StarFieldRenderer(polygonSpriteBatch, orthoCamera, fixedBatch, 1024f, 1024f)
            octreeRenderer = OctreeRenderer(intersectingNodes, modelBatch)
            starRenderer = StarRenderer(modelBatch)
            selectionRenderer = SelectionRenderer(camera, orthoCam, decalBatch, modelBatch, fontAndromeda)
        }

        skyboxRenderer?.disposeSafely()
        skyboxRenderer?.init()
        nebulaeRenderer?.disposeSafely()
        nebulaeRenderer?.init()
        starfieldRenderer?.disposeSafely()
        starfieldRenderer?.init()
        starfieldRendererForSkybox?.disposeSafely()
        starfieldRendererForSkybox?.init()
        octreeRenderer?.disposeSafely()
        octreeRenderer?.init()
        selectionRenderer?.disposeSafely()
        selectionRenderer?.init()

    }


    private val identity = Matrix4().idt()
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
            skyboxRenderer!!.textures = skyboxTextures
            skyboxNeedsUpdate = false
        }

        if (viewMode == ViewMode.GALAXY) {
            starfieldRenderer!!.renderToFramebuffer(camera)
            nebulaeRenderer!!.renderToFramebuffer(camera)
        }
        beginScene()

        val col = 0.0f
        Gdx.gl.glClearColor(col, col, col, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        if (viewMode == ViewMode.GALAXY) {
            starfieldRenderer!!.renderToScreen(camera)
            nebulaeRenderer!!.renderToScreen(camera)
            selectionRenderer!!.renderToScreen(camera)
        } else {
            skyboxRenderer!!.renderToScreen(camera)
        }

        if (focusedSelection?.item is System) {
            @Suppress("UNCHECKED_CAST")
            starRenderer!!.selection = focusedSelection as Selection<System>?
            starRenderer!!.viewMode = viewMode
            starRenderer!!.renderToScreen(camera)
        }
        if (viewMode == ViewMode.GALAXY) {
            octreeRenderer!!.renderToScreen(camera)
        }
        endScene(_batch)
    }

    private var ray: Ray? = null
    override fun act(delta: Float) {
        super.act(delta)

        if (focusedSelection != null) {
            focusedSelection!!.selectionTimer += delta.toLong()
            val dst = camera.position.dst(focusedSelection!!.item.position)
            if (dst < 4f && viewMode == ViewMode.GALAXY && !cameraInputController.isMovingToTarget()) {
                println("switch view mode to SYSTEM")
                viewMode = ViewMode.SYSTEM
                skyboxNeedsUpdate = true
            } else if (dst > 100f && viewMode == ViewMode.SYSTEM) {
                viewMode = ViewMode.GALAXY
                println("switch view mode to GALAXY")
            }
        }
        if (hoveredSelection != null) {
            hoveredSelection!!.selectionTimer += delta.toLong()
        }

        Gdx.input.inputProcessor = multiplexer
        cameraInputController.bounds.set(x, y, width, height)
        cameraInputController.autoRotate = Settings.debug.autoRotate
        cameraInputController.update()
        if (cameraInputController.isInBounds()) {

            this.stage.unfocusAll()

            if (viewMode == ViewMode.GALAXY) {
                pickStarSelection()
                if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
                    if (focusedNeighbors.isNotEmpty()) {
                        focusedSelection = Selection(focusedNeighbors.random())
                        selectionRenderer!!.focusedSelection = focusedSelection
                    }
                }
            } else if (viewMode == ViewMode.SYSTEM) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
                    viewMode = ViewMode.GALAXY
                }
            }
        }
    }

    private fun pickStarSelection() {
        val mx = Gdx.input.x.toFloat()
        val my = Gdx.input.y.toFloat()
        val sx = viewportScreen.screenX
        val sy = viewportScreen.screenY
        ray = camera.getPickRay((mx), (my), sx.toFloat(), sy.toFloat(), viewportScreen.screenWidth.toFloat(), viewportScreen.screenHeight.toFloat())

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
            selectionRenderer!!.hoveredSelection = hoveredSelection
        } else {
            selectionRenderer!!.hoveredSelection = null
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (selection != null) {
                focusedSelection = Selection(selection)

                @Suppress("UNCHECKED_CAST")
                starRenderer!!.selection = focusedSelection as Selection<System>
                selectionRenderer!!.focusedSelection = focusedSelection

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
        val cam = PerspectiveCamera(90f, SKYBOX_SIZE, SKYBOX_SIZE);
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
            starfieldRendererForSkybox!!.renderToFramebuffer(cam)
            nebulaeRenderer!!.renderToFramebuffer(cam)
//            oc.setToOrtho(true, fbs[i].width.toFloat(), fbs[i].height.toFloat())
//            oc.update()
//            sb.projectionMatrix = oc.combined
//            sb.color = Color.WHITE
            fbs[i].begin()
            Gdx.gl20.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            starfieldRendererForSkybox!!.renderToScreen(cam)
            nebulaeRenderer!!.renderToScreen(cam)

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
                skyboxTextures.add(region);
            }
        }
    }

    override fun remove(): Boolean {
        nebulaeRenderer.disposeSafely()
        starfieldRenderer.disposeSafely()
        starfieldRendererForSkybox.disposeSafely()
        skyboxRenderer.disposeSafely()
        selectionRenderer.disposeSafely()

        decalBatch.disposeSafely();
        polygonSpriteBatch.disposeSafely()
        fixedBatch.disposeSafely()
        modelBatch.disposeSafely()
        starTexture.texture.dispose()
        starTexturePre.texture.dispose()
        pixelTexture.texture.dispose()
        generator.octree.disposeSafely()
        stars.clear()

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
        ScissorStack.popScissors()
        stage.viewport.apply()

        restoreBatch(_batch);
    }

    /**
     * Update viewport dimensions to fit the VisWindow we are drawing in
     */
    private fun beginScene() {
        val w = width.toInt() - 2
        val h = height.toInt() - 2
        viewportScreen.update(w, h, false)
        viewportScreen.setScreenBounds(x.toInt(), y.toInt(), w, h)
        viewportScreen.apply()

        val scissors = Rectangle()
        ScissorStack.calculateScissors(stage.camera, identity, Rectangle(x + 1, y + 1, w.toFloat(), h.toFloat()), scissors)
        ScissorStack.pushScissors(scissors)
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
            starfieldRenderer!!.handleResize(width, height)
            needsResize = false
        }
    }
}

class StarDecal(val star: Star, val size: Float) {
    companion object {
        val SIZE = 4
    }

    val vertices = FloatArray(SIZE)

    init {
        vertices[0] = star.position.x
        vertices[1] = star.position.y
        vertices[2] = star.position.z
        vertices[3] = star.type.temperature
    }
}

enum class ViewMode {
    GALAXY,
    SYSTEM
}