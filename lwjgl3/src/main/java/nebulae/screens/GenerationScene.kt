package nebulae.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy
import com.badlogic.gdx.graphics.g3d.decals.Decal
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch
import com.badlogic.gdx.graphics.g3d.decals.DecalMaterial
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.MathUtils.clamp
import com.badlogic.gdx.math.MathUtils.cos
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.app.use
import ktx.assets.disposeSafely
import ktx.graphics.use
import ktx.math.times
import nebulae.data.Octree
import nebulae.data.Star
import nebulae.generation.Settings
import nebulae.generation.UniverseGenerator
import nebulae.input.BoundedCameraInputController
import nebulae.kutils.BufferedList
import nebulae.kutils.minus
import nebulae.kutils.plus
import nebulae.rendering.BlurFx
import nebulae.rendering.FixedStarBatch
import nebulae.rendering.shaders.*
import nebulae.universe.Universe
import org.lwjgl.opengl.GL40
import space.earlygrey.shapedrawer.JoinType
import space.earlygrey.shapedrawer.ShapeDrawer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


class GenerationScene(private val generator: UniverseGenerator, val universe: Universe) : VisWindow("") {

    private val fontAndromeda = BitmapFont(Gdx.files.internal("fonts/andromeda.fnt"), true)
    private val fontCloseness = BitmapFont(Gdx.files.internal("fonts/closeness.fnt"), true)
    private val fontDiscognate = BitmapFont(Gdx.files.internal("fonts/discognate.fnt"), true)
    private val fontSpartakus = BitmapFont(Gdx.files.internal("fonts/spartakus.fnt"), true)
    private val textDecalsPool = object : Pool<Decal>(16) {
        override fun newObject(): Decal {
            val decal = Decal(DecalMaterial())
            val texture = Texture(128, 128, Pixmap.Format.RGBA8888)
            decal.textureRegion = TextureRegion(texture)
            return decal
        }
    }
    private lateinit var screenQuad: ModelInstance
    private lateinit var builder: ModelBuilder

    private val selectedStars = mutableListOf<Star>()
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
    private val polygonSpriteBatch = PolygonSpriteBatch()
    private val spriteBatch = SpriteBatch()
    private val shape = ShapeDrawer(polygonSpriteBatch, pixelTexture)
    private val cameraInputController = BoundedCameraInputController(camera, Rectangle())
    private val multiplexer: InputMultiplexer by lazy { InputMultiplexer(stage, cameraInputController) }
    private val stars = BufferedList<StarDecal>()

    private var blur = BlurFx(orthoCamera, SpriteBatch(), width.toInt(), height.toInt())
    private val originalScene = TextureRegion(blur.sceneBuffer!!.colorBufferTexture)
    private val boxes = mutableListOf<ModelInstance>()

    private val textDecals = mutableListOf<Decal>()
    private var focusedStartTime: Long = 0L
    private var focusedInfoDecal: Decal? = null
    private val starSelectedDecals = mutableListOf<Decal>()
    private val starFocusedDecal = Decal()
    private lateinit var skyBox: ModelInstance

    private val skyboxShader: SkyboxShader = SkyboxShader()
    private val nebulaes = mutableListOf<ModelInstance>()

    private val nebulaShader: NebulaeShader = NebulaeShader()
    private val nebulaShaderSDF: NebulaeShaderSDF = NebulaeShaderSDF()
    private val framebufferNebulaes = FrameBuffer(Pixmap.Format.RGBA8888, 512, 512, false)

    private val nebulaesRegion = TextureRegion(framebufferNebulaes.colorBufferTexture, 512, 512)

    private lateinit var starModelInstance: ModelInstance
    private val starShader = StarShader()
    private var focusedStar: Star? = null
    private val focusedNeighbors = mutableListOf<Star>()
    private val focusedNeighborsConnections = mutableListOf<ModelInstance>()
    private var selectionTimer = 0f

    private val orthoCam = OrthographicCamera()

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

        originalScene.flip(false, true)
        nebulaesRegion.flip(false, true)

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
            for (box in boxes) {
                box.model.disposeSafely()
            }

            //create octree box model pool
            boxes.clear()
            builder = ModelBuilder()
            val boxModel = builder.createBox(1f, 1f, 1f, GL40.GL_LINES, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong())

            for (i in 0..200) {
                val instance = ModelInstance(boxModel, Matrix4())
                boxes.add(instance)
            }

            //create skyBox
            val material = Material()
            val sphereModel = builder.createSphere(1f, 1f, 1f, 90, 90, material, (VertexAttributes.Usage.Position).toLong())
            skyBox = ModelInstance(sphereModel, Matrix4().scale(5000f, 5000f, 5000f))
            skyBox.userData = BoxData(Vector3(0f, 0f, 0f), 5000f)

            val starModel = builder.createSphere(1f, 1f, 1f, 90, 90, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong())
            starModelInstance = ModelInstance(starModel)

            val r = 1f;
            val quad = builder.createRect(-r, r, -1f, r, r, -1f, r, -r, -1f, -r, -r, -1f, 0f, 0f, 0f, material, (VertexAttributes.Usage.Position or VertexAttributes.Usage.TextureCoordinates).toLong())
            screenQuad = ModelInstance(quad)

            //create nebulae geometry
            val cubeModel = builder.createBox(1f, 1f, 1f, GL40.GL_TRIANGLES, Material(), (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong())
            val sphereModelLow = builder.createSphere(1f, 1f, 1f, 10, 10, material, (VertexAttributes.Usage.Position).toLong())
            for (oc in generator.octree.select { it.depth == 6 }) {
                if (generator.rand.nextFloat() > 0.95f) continue
                val x = oc.bounds.centerX + generator.rand.nextFloat() * 15f - 7f
                val y = oc.bounds.centerY + generator.rand.nextFloat() * 15f - 7f
                val z = oc.bounds.centerZ + generator.rand.nextFloat() * 3f - 1.5f
                val instance = ModelInstance(sphereModelLow)
                val size = oc.bounds.width * 2;
                instance.transform.setToTranslationAndScaling(x, y, z, size, size, size)
                instance.userData = NebulaData(Vector3(x, y, z), size * 0.5f)
                val blendingAttribute = BlendingAttribute()
                blendingAttribute.blended = true;
                instance.materials.get(0).set(blendingAttribute)
                nebulaes.add(instance)
            }

            //create a decal pool for selected stars
            for (i in 0..200) {
                val decal = Decal()
                decal.textureRegion = TextureRegion(starTexture)
                starSelectedDecals.add(decal)
            }

        }

        skyboxShader.disposeSafely()
        skyboxShader.init()
        starShader.disposeSafely()
        starShader.init()
        nebulaShader.disposeSafely()
        nebulaShader.init()
        nebulaShaderSDF.disposeSafely()
        nebulaShaderSDF.init()
    }


    private val identity = Matrix4().idt()
    override fun draw(_batch: Batch?, parentAlpha: Float) {
        super.draw(_batch, parentAlpha)
        handleResize()

        suspendBatch(_batch!!)

        var blurredScene: TextureRegion? = null

        stage.viewport.apply()
        if (generator.galaxy != null) {
            blurredScene = blur.renderToTexture {
                drawStars()
                if (Settings.debug.showArms) drawArms()
            }
        }

        //Draw nebulaes onto framebuffer
        if (Settings.debug.drawNebulaes) {
            framebufferNebulaes.use {
                spriteBatch.use {
                    orthoCam.setToOrtho(false, framebufferNebulaes.width.toFloat(), framebufferNebulaes.height.toFloat())
                    orthoCam.update()
                    spriteBatch.projectionMatrix = orthoCam.combined
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                    drawNebulaes()
                }
            }
        }
        if (Settings.debug.drawNebulaesSDF) {
            framebufferNebulaes.use {
                spriteBatch.use {

                    orthoCam.setToOrtho(false, framebufferNebulaes.width.toFloat(), framebufferNebulaes.height.toFloat())
                    orthoCam.update()
                    spriteBatch.projectionMatrix = orthoCam.combined
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                    drawNebulaesSDF()
                }
            }
        }


        beginScene()

        val col = 0.0f
        Gdx.gl.glClearColor(col, col, col, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        if (Settings.debug.drawSkybox) drawSkyBox()

        if (generator.galaxy != null && Settings.debug.drawStars) {

            polygonSpriteBatch.projectionMatrix = orthoCamera.combined
            polygonSpriteBatch.use {
                it.enableBlending()
                it.setBlendFunction(GL40.GL_SRC_ALPHA, GL40.GL_DST_ALPHA)
                it.draw(originalScene, 0f, 0f)
                it.flush()

                it.setBlendFunction(GL40.GL_SRC_ALPHA, GL40.GL_DST_ALPHA)
                it.draw(blurredScene, 0f, 0f)
                it.flush()
                it.disableBlending()
            }
        }

        if (Settings.debug.drawNebulaes || Settings.debug.drawNebulaesSDF) {
            spriteBatch.use {
                it.enableBlending()
                it.setBlendFunction(GL40.GL_SRC_ALPHA, GL40.GL_ONE)
                it.draw(nebulaesRegion, 0f, 0f)
            }
        }

        drawSelection()

        if (focusedStar != null) {
            modelBatch.begin(camera)
            val starPos = focusedStar!!.position
            val dst = tmp.set(starPos).dst(camera.position)
            val scale = 0.82f * min(dst.smoothstep(10f, 5f), TimeUtils.timeSinceMillis(focusedStartTime).toFloat().smoothstep(0f, 500f))
            starModelInstance.transform = Matrix4().translate(starPos).scale(scale, scale, scale)
            modelBatch.render(starModelInstance, starShader)
            modelBatch.end()
        }
        //drawDebug()

        if (Settings.debug.drawIntersection) drawOctree()

        endScene(_batch)
    }

    private fun drawNebulaes() {
        modelBatch.begin(camera)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
        for (n in nebulaes) {
            val data = n.userData as NebulaData
            val size = Settings.debug.nebulaSize
            data.radius = size * 0.5f
            n.transform.setToTranslationAndScaling(data.center.x, data.center.y, data.center.z, size, size, size)
        }
        modelBatch.render(nebulaes, nebulaShader)
        modelBatch.end()
    }

    private fun drawNebulaesSDF() {
        modelBatch.begin(camera)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
        modelBatch.render(screenQuad, nebulaShaderSDF)
        modelBatch.end()
    }

    private fun drawSkyBox() {
        modelBatch.begin(camera)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
        val pos = camera.position
        val world = Matrix4().setToTranslationAndScaling(pos.x, pos.y, pos.z, 5000f, 5000f, 5000f)
        skyBox.transform.set(world)
        modelBatch.render(skyBox, skyboxShader)
        modelBatch.end()
    }

    private fun drawSelection() {
        val camPos = tmp.set(camera.position)
        if (selectedStars.isNotEmpty()) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
            for (i in 0 until min(starSelectedDecals.size, selectedStars.size)) {
                val decal = starSelectedDecals[i]
                decal.position = selectedStars[i].position
                //slightly move the decal along the view axis to avoid z fighting
                decal.position = (decal.position + (tmp2.set(camPos) - decal.position).nor() * -2f)
                decal.textureRegion = ringTexture
                val toCam = max(1f, min(camPos.dst2(starSelectedDecals[i].position), 1000f))
                val size = max(0.3f, toCam * 3f / 1000f)
                decal.setDimensions(size, size)
                decal.lookAt(camera.position, camera.up)
                decal.setBlending(GL40.GL_ONE, GL40.GL_ONE)
                decal.color = Color(0.48f, 0.78f, 1.0f, 0.5f)
                decalBatch.add(decal)
            }
        }
        if (focusedStar != null) {
            val decal = starFocusedDecal
            decal.position = focusedStar!!.position
            decal.textureRegion = ringTexture
            val toCam = max(1f, min(camPos.dst2(decal.position), 1000f))
            //slightly move the decal along the view axis to avoid z fighting
            decal.position = (decal.position + (tmp2.set(camPos) - decal.position).nor() * 3f)
            val size = max(0.3f, toCam * 3f / 1000f) * (1f + 0.3f * (1f + 0.5f * cos(selectionTimer * 2f).pow(2f)))
            decal.setDimensions(size, size)
            decal.lookAt(camera.position, camera.up)
            decal.setBlending(GL40.GL_SRC_ALPHA, GL40.GL_ONE_MINUS_SRC_ALPHA)
            decal.color = Color(0.68f, 0.88f, 1.0f, 0.15f)
            decalBatch.add(decal)

            val computeScale = fun(pos: Vector3): Float {
                val evenPoint = 20f
                var minScale = 2f
                var maxScale = 100f
                var minD = evenPoint
                var maxD = 1000f
                val d = camera.position.dst(pos)
                if (d < evenPoint) {
                    minD = 0f
                    maxD = evenPoint
                    maxScale = 2f
                    minScale = 0.2f
                }
                return ((d - minD) / (maxD - minD)) * (maxScale - minScale) + minScale
            }
            textDecals.forEach {
                drawTextDecal(it, computeScale(it.position))
            }
            if (focusedInfoDecal != null) {
                val scale = computeScale(focusedInfoDecal!!.position)
                drawTextDecal(focusedInfoDecal!!, scale)
            }
            decalBatch.flush()

            if (focusedNeighborsConnections.isNotEmpty() && Settings.debug.drawNeighbors) {
                modelBatch.begin(camera)
                Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
                Gdx.gl.glEnable(GL40.GL_LINE_SMOOTH)
                Gdx.gl.glLineWidth(1f)
                focusedNeighborsConnections.forEach {
                    it.materials[0].set(BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.155f))
                    modelBatch.render(it);
                }
                modelBatch.end()
                Gdx.gl.glDisable(GL40.GL_LINE_SMOOTH)
            }
        }
        decalBatch.flush()
    }

    private fun drawTextDecal(it: Decal, scale: Float) {
        it.lookAt(camera.position, camera.up)
        it.setBlending(GL40.GL_ONE, GL40.GL_ONE)
        it.color = Color.WHITE
        it.setDimensions(8f * scale, 2f * scale)
        decalBatch.add(it)
    }

    private val tmp = Vector3()
    private val tmp2 = Vector3()
    private fun drawOctree() {
        if (intersectingNodes.size >= boxes.size) {
            println("Too much nodes to draw: ${intersectingNodes.size}")
            return
        }

        modelBatch.begin(camera)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL40.GL_LINE_SMOOTH)
        Gdx.gl.glLineWidth(1f)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE)
        for ((index, node) in intersectingNodes.withIndex()) {
            val instance = boxes[index]
            node.bounds.getCenter(tmp)
            instance.transform = Matrix4().translate(tmp).scale(node.bounds.width, node.bounds.width, node.bounds.width)
            modelBatch.render(instance)
        }

        Gdx.gl.glDisable(GL40.GL_LINE_SMOOTH)
        modelBatch.end()
    }

    private fun drawArms(lineWidth: Float = 2f) {
        polygonSpriteBatch.projectionMatrix = camera.combined
        polygonSpriteBatch.use {
            shape.setColor(Color.RED)
            for (arm in generator.galaxy?.arms!!) {
                for (i in 0 until arm.size - 1) {
                    shape.line(arm[i], arm[i + 1], lineWidth)
                }
            }
        }
    }

    private fun drawDebug() {
        polygonSpriteBatch.projectionMatrix = camera.combined
        polygonSpriteBatch.use {
            shape.setColor(Color.GOLDENROD)
            shape.circle(cameraInputController.target.x, cameraInputController.target.y, 5f, 0.5f, JoinType.SMOOTH)
        }
    }

    private fun drawStars() {
        fixedBatch.render()
    }

    private var ray: Ray? = null
    private var intersectingNodes: MutableList<Octree> = mutableListOf()
    override fun act(delta: Float) {
        super.act(delta)
        selectionTimer += delta
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
            val prevSelection = if (selectedStars.isEmpty()) null else selectedStars[0]
            selectedStars.clear()
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
                selectedStars.add(selection)
                if (selection != prevSelection) {
                    val dec = createTextDecal(selection.name)
                    dec.position = selection.position
                    textDecals.forEach {
                        textDecalsPool.free(it)
                    }
                    textDecals.clear()
                    textDecals.add(dec)
                }
            } else {
                textDecals.forEach {
                    textDecalsPool.free(it)
                }
                textDecals.clear()
            }

            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                if (selectedStars.isNotEmpty()) {
                    cameraInputController.desiredTarget = selectedStars[0].position.cpy()
                    focusedStar = selectedStars[0]
                    focusedStartTime = TimeUtils.millis()
                    starModelInstance.userData = focusedStar
                    val focused = focusedStar!!

                    val dec = createTextDecal(focused.name)
                    dec.position = focused.position
                    focusedInfoDecal = dec

                    focusedNeighbors.clear()
                    val radius = 5f
                    generator.octree.intersectSphere(focused.position, radius).forEach {
                        val stars = it.getObjects<Star>().filter { star ->
                            star.position.dst2(focused.position) < radius * radius && star != focusedStar
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
                            tmp2.set(focused.position)
                            tmp2.set(tmp2 - star.position)
                            val offset = tmp2.nor() * 0.2f
                            part.line(tmp.set(focused.position) - offset, star.position)
                        }
                        val model = builder.end()
                        focusedNeighborsConnections.add(ModelInstance(model))
                    }
                }
            }
        }
    }

    private fun createTextDecal(vararg lines: String): Decal {
        //create info texture
        val fb = FrameBuffer(Pixmap.Format.RGBA8888, 1024, 256, false)
        fb.use {
            orthoCam.setToOrtho(false, fb.width.toFloat(), fb.height.toFloat())
            orthoCam.update()
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL40.GL_COLOR_BUFFER_BIT or GL40.GL_DEPTH_BUFFER_BIT)

            val spriteBatch = SpriteBatch()
            spriteBatch.projectionMatrix = orthoCam.combined
            spriteBatch.use {
                spriteBatch.color = Color.WHITE
                val scale = 0.35f
                fontAndromeda.data.scaleX = scale
                fontAndromeda.data.scaleY = scale
                for ((i, line) in lines.withIndex()) {
                    fontAndromeda.draw(it, line, 0f, 80f + i * fontAndromeda.lineHeight * scale, 1024f, Align.center, true)
                }
            }
            spriteBatch.flush()
            spriteBatch.color = Color.WHITE
        }
        val dec = textDecalsPool.obtain()
        dec.textureRegion = TextureRegion(fb.colorBufferTexture)
        return dec
    }

    override fun remove(): Boolean {
        blur.dispose();
        decalBatch.disposeSafely();
        polygonSpriteBatch.disposeSafely()
        fixedBatch.disposeSafely()
        modelBatch.disposeSafely()
        starTexture.texture.dispose()
        starTexturePre.texture.dispose()
        pixelTexture.texture.dispose()
        generator.octree.disposeSafely()
        stars.clear()
        skyBox.model.disposeSafely()
        framebufferNebulaes.disposeSafely()

        Gdx.input.inputProcessor = stage
        return super.remove()
    }

    fun resize(width: Float, height: Float) {
        setSize(width, height)
        needsResize = true
    }

    private fun endScene(_batch: Batch) {
        ScissorStack.popScissors()
        stage.viewport.apply()

        restoreBatch(_batch);
    }

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
            blur.disposeSafely()
            blur = BlurFx(orthoCamera, SpriteBatch(), width.toInt(), height.toInt())
            needsResize = false
            originalScene.texture = blur.sceneBuffer!!.colorBufferTexture
            originalScene.regionWidth = width.toInt()
            originalScene.regionHeight = height.toInt()
        }
    }

}

private fun Float.smoothstep(vmin: Float, vmax: Float): Float {
    val x = clamp((this - vmin) / (vmax - vmin), 0f, 1f)
    return x * x * (3 - 2 * x)
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