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
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.assets.disposeSafely
import ktx.math.times
import nebulae.data.Octree
import nebulae.data.Star
import nebulae.generation.Settings
import nebulae.generation.UniverseGenerator
import nebulae.input.BoundedCameraInputController
import nebulae.kutils.BufferedList
import nebulae.kutils.minus
import nebulae.rendering.*
import nebulae.rendering.renderers.*
import nebulae.selection.Selection
import nebulae.universe.Universe
import space.earlygrey.shapedrawer.ShapeDrawer


class GenerationScene(private val generator: UniverseGenerator, val universe: Universe) : VisWindow("") {

    private val fontAndromeda = BitmapFont(Gdx.files.internal("fonts/andromeda.fnt"), true)
    private val fontCloseness = BitmapFont(Gdx.files.internal("fonts/closeness.fnt"), true)
    private val fontDiscognate = BitmapFont(Gdx.files.internal("fonts/discognate.fnt"), true)
    private val fontSpartakus = BitmapFont(Gdx.files.internal("fonts/spartakus.fnt"), true)

    private var needsResize = false
    private val camera = PerspectiveCamera(67f, 1366f, 768f)
    private val orthoCamera = OrthographicCamera(1366f, 768f)
    private val viewportScreen = ScreenViewport(camera)
    private val starTexture = TextureRegion(Texture(Gdx.files.internal("textures/star.png")))
    private val starTexturePre = TextureRegion(Texture(Gdx.files.internal("textures/star_pre.png")))
    private val pixelTexture = TextureRegion(Texture(Gdx.files.internal("textures/pixel.png")))

    private val ringTexture = TextureRegion(Texture(Gdx.files.internal("textures/ring_pre.png")))
    private val decalBatch = DecalBatch(100, CameraGroupStrategy(camera))
    private val fixedBatch = FixedStarBatch(camera)
    private val modelBatch = ModelBatch()

    private val builder = ModelBuilder()
    private val polygonSpriteBatch = PolygonSpriteBatch()
    private val spriteBatch = SpriteBatch()
    private val shape = ShapeDrawer(polygonSpriteBatch, pixelTexture)
    private val cameraInputController = BoundedCameraInputController(camera, Rectangle())
    private val multiplexer: InputMultiplexer by lazy { InputMultiplexer(stage, cameraInputController) }
    private val stars = BufferedList<StarDecal>()

    private var focusedSelection: Selection<Star>? = null
    private var hoveredSelection: Selection<Star>? = null

    private val focusedNeighbors = mutableListOf<Star>()
    private val focusedNeighborsConnections = mutableListOf<ModelInstance>()

    private val orthoCam = OrthographicCamera()

    private var nebulaeRenderer: NebulaeRenderer? = null
    private var skyboxRenderer: SkyboxRenderer? = null
    private var starfieldRenderer: StarFieldRenderer? = null
    private var octreeRenderer: OctreeRenderer? = null
    private var starRenderer: StarRenderer? = null
    private var selectionRenderer: SelectionRenderer? = null

    private var intersectingNodes: MutableList<Octree> = mutableListOf()

    private val tmp = Vector3()
    private val tmp2 = Vector3()

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
        generator.starCreated.clear()
        generator.starCreated += { starList ->

            stars.clear()
            starList.forEach() {
                val decal = StarDecal(it, 0.94f)
                stars.add(decal)
            }
            stars.update()
            fixedBatch.set(stars)

            nebulaeRenderer = NebulaeRenderer(spriteBatch, orthoCam, modelBatch, camera, generator.farthestStarDistance)
            skyboxRenderer = SkyboxRenderer(modelBatch, camera)
            starfieldRenderer = StarFieldRenderer(polygonSpriteBatch, orthoCamera, fixedBatch, width, height)
            octreeRenderer = OctreeRenderer(intersectingNodes, camera, modelBatch)
            starRenderer = StarRenderer(modelBatch, camera)
            selectionRenderer = SelectionRenderer(camera, orthoCam, decalBatch, modelBatch, fontAndromeda)
        }

        skyboxRenderer?.disposeSafely()
        skyboxRenderer?.init()
        nebulaeRenderer?.disposeSafely()
        nebulaeRenderer?.init()
        starfieldRenderer?.disposeSafely()
        starfieldRenderer?.init()
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

        starfieldRenderer!!.renderToFramebuffer()
        nebulaeRenderer!!.renderToFramebuffer()

        beginScene()

        val col = 0.0f
        Gdx.gl.glClearColor(col, col, col, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        skyboxRenderer!!.renderToScreen()
        starfieldRenderer!!.renderToScreen()
        nebulaeRenderer!!.renderToScreen()
        selectionRenderer!!.renderToScreen()

        starRenderer!!.starSelection = focusedSelection
        starRenderer!!.renderToScreen()

        octreeRenderer!!.renderToScreen()

        endScene(_batch)
    }

    private var ray: Ray? = null
    override fun act(delta: Float) {
        super.act(delta)

        if (focusedSelection != null) {
            focusedSelection!!.selectionTimer += delta.toLong()
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

            val mx = Gdx.input.x.toFloat()
            val my = Gdx.input.y.toFloat()
            val sx = viewportScreen.screenX
            val sy = viewportScreen.screenY
            ray = camera.getPickRay((mx), (my), sx.toFloat(), sy.toFloat(), viewportScreen.screenWidth.toFloat(), viewportScreen.screenHeight.toFloat())

            intersectingNodes.clear()
            generator.octree.intersect(ray!!, intersectingNodes)
            val intersection = Vector3()
            var selection: Star? = null
            var prevDistToIntersection = 1000f
            var prevDistToCam = 1000f
            for (node in intersectingNodes) {
                for (star in node.getObjects<Star>()) {
                    if (Intersector.intersectRaySphere(ray, star.position, 0.5f, intersection)) {
                        if (selection == null) {
                            selection = star
                            continue
                        }
                        viewportScreen.project(intersection)
                        viewportScreen.project(tmp.set(star.position))
                        val dst = intersection.dst2(tmp)
                        if (dst < prevDistToIntersection && dst < prevDistToCam) {
                            selection = star
                            prevDistToIntersection = dst
                            prevDistToCam = tmp.set(camera.position).dst2(star.position)
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
                    cameraInputController.desiredTarget = selection.position.cpy()
                    focusedSelection = Selection(selection)
                    starRenderer!!.starSelection = focusedSelection
                    selectionRenderer!!.focusedSelection = focusedSelection

                    focusedNeighbors.clear()
                    val radius = 5f
                    generator.octree.intersectSphere(selection.position, radius).forEach {
                        val stars = it.getObjects<Star>().filter { star ->
                            star.position.dst2(selection.position) < radius * radius && star != selection
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
    }

    override fun remove(): Boolean {
        nebulaeRenderer.disposeSafely()
        starfieldRenderer.disposeSafely()
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