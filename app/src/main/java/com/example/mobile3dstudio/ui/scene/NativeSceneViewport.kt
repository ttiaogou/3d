package com.example.mobile3dstudio.ui.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.mobile3dstudio.core.model.NativeModelKind
import com.example.mobile3dstudio.core.model.NativeSceneModel
import com.example.mobile3dstudio.core.model.NativeTransform
import com.google.android.filament.LightManager
import com.google.android.filament.RenderableManager
import io.github.sceneview.SceneScope
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.collision.HitResult
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.gesture.MoveGestureDetector
import io.github.sceneview.gesture.RotateGestureDetector
import io.github.sceneview.gesture.ScaleGestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node as SceneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.safeDestroyGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private class ViewportTouchState {
    var movingModelId: Long? = null
    var lastMoveX = 0f
    var lastMoveY = 0f
    var pressDownX = 0f
    var pressDownY = 0f
    var twoFingerX = 0f
    var twoFingerY = 0f
    var pendingLongPress: Runnable? = null
    val gestureTransforms = mutableMapOf<Long, NativeTransform>()
}

/** Native SceneView/Filament viewport for procedural and imported GLB models. */
@Composable
fun NativeSceneViewport(
    modifier: Modifier = Modifier,
    models: List<NativeSceneModel>,
    selectedModelId: Long?,
    onModelSelected: (Long?) -> Unit,
    onLongPressModel: (Long) -> Unit,
    onModelTransform: (Long, NativeTransform) -> Unit,
    onModelTransformFinished: () -> Unit,
    onRendererFrame: () -> Unit = {},
    onRendererError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val modelLoader = rememberModelLoader(engine, context)
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = Position(2.55f, 2.05f, 3.25f),
        targetPosition = Position(0f, 0f, 0f)
    )
    val latestOnModelSelected = rememberUpdatedState(onModelSelected)
    val latestOnLongPress = rememberUpdatedState(onLongPressModel)
    val latestOnTransform = rememberUpdatedState(onModelTransform)
    val latestOnTransformFinished = rememberUpdatedState(onModelTransformFinished)
    val latestOnRendererFrame = rememberUpdatedState(onRendererFrame)
    val latestOnRendererError = rememberUpdatedState(onRendererError)
    val latestSelectedModelId = rememberUpdatedState(selectedModelId)
    val latestModels = rememberUpdatedState(models)
    val hasReportedFrame = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop }

    val dieMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFF0EDE4), metallic = 0.02f, roughness = 0.38f)
    }
    val pipMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF9E2636), metallic = 0f, roughness = 0.34f)
    }
    val cutMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFB6A98F), metallic = 0f, roughness = 0.52f)
    }
    val cardEdgeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFF9F7F0), metallic = 0f, roughness = 0.42f)
    }
    DisposableEffect(materialLoader, dieMaterial, pipMaterial, cutMaterial, cardEdgeMaterial) {
        onDispose {
            materialLoader.destroyMaterialInstance(cardEdgeMaterial)
            materialLoader.destroyMaterialInstance(cutMaterial)
            materialLoader.destroyMaterialInstance(pipMaterial)
            materialLoader.destroyMaterialInstance(dieMaterial)
        }
    }

    val frontBitmap = produceState<Bitmap?>(null, context) {
        value = withContext(Dispatchers.IO) { decodeAsset(context, "dice/queen-spades-front.jpg") }
    }.value
    val backBitmap = produceState<Bitmap?>(null, context) {
        value = withContext(Dispatchers.IO) { decodeAsset(context, "dice/playing-card-back.jpg") }
    }.value

    val touchState = remember { ViewportTouchState() }
    fun modelId(node: SceneNode?): Long? =
        generateSequence(node) { it.parent }.firstNotNullOfOrNull { it.name?.toLongOrNull() }
    fun currentTransform(id: Long): NativeTransform? =
        touchState.gestureTransforms[id] ?: latestModels.value.firstOrNull { it.id == id }?.transform
    fun submitTransform(id: Long, transform: NativeTransform) {
        touchState.gestureTransforms[id] = transform
        latestOnTransform.value(id, transform)
    }
    fun finishGesture(id: Long? = null) {
        if (id == null) touchState.gestureTransforms.clear() else touchState.gestureTransforms.remove(id)
        latestOnTransformFinished.value()
    }

    val gestureListener = remember {
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent, node: SceneNode?) {
                latestOnModelSelected.value(modelId(node))
            }

            override fun onMoveBegin(detector: MoveGestureDetector, e: MotionEvent, node: SceneNode?) {
                touchState.movingModelId = modelId(node) ?: latestSelectedModelId.value
                touchState.lastMoveX = 0f
                touchState.lastMoveY = 0f
            }

            override fun onMove(detector: MoveGestureDetector, e: MotionEvent, node: SceneNode?) {
                val id = touchState.movingModelId ?: return
                val current = currentTransform(id) ?: return
                val totalX = detector.lastDistanceX ?: 0f
                val totalY = detector.lastDistanceY ?: 0f
                val dx = totalX - touchState.lastMoveX
                val dy = totalY - touchState.lastMoveY
                touchState.lastMoveX = totalX
                touchState.lastMoveY = totalY
                submitTransform(id, current.copy(x = current.x + dx / 260f, y = current.y - dy / 260f))
            }

            override fun onMoveEnd(detector: MoveGestureDetector, e: MotionEvent, node: SceneNode?) {
                touchState.movingModelId = null
                finishGesture(modelId(node) ?: latestSelectedModelId.value)
            }

            override fun onRotateBegin(detector: RotateGestureDetector, e: MotionEvent, node: SceneNode?) {}

            override fun onRotate(detector: RotateGestureDetector, e: MotionEvent, node: SceneNode?) {
                val id = modelId(node) ?: latestSelectedModelId.value ?: return
                val current = currentTransform(id) ?: return
                submitTransform(id, current.copy(rz = current.rz + detector.rotation))
            }

            override fun onRotateEnd(detector: RotateGestureDetector, e: MotionEvent, node: SceneNode?) {
                finishGesture(modelId(node) ?: latestSelectedModelId.value)
            }

            override fun onScaleBegin(detector: ScaleGestureDetector, e: MotionEvent, node: SceneNode?) {}

            override fun onScale(detector: ScaleGestureDetector, e: MotionEvent, node: SceneNode?) {
                val id = latestSelectedModelId.value ?: modelId(node) ?: return
                val current = currentTransform(id) ?: return
                submitTransform(id, current.copy(scale = (current.scale * detector.scaleFactor).coerceIn(0.05f, 10f)))
            }

            override fun onScaleEnd(detector: ScaleGestureDetector, e: MotionEvent, node: SceneNode?) {
                finishGesture(latestSelectedModelId.value ?: modelId(node))
            }
        }
    }

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        autoCenterContent = false,
        cameraManipulator = if (selectedModelId == null) cameraManipulator else null,
        onGestureListener = gestureListener,
        onTouchEvent = { event, hitResult: HitResult? ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchState.pendingLongPress?.let(mainHandler::removeCallbacks)
                    val id = modelId(hitResult?.node)
                    touchState.pressDownX = event.x
                    touchState.pressDownY = event.y
                    if (id != null) {
                        val task = Runnable { latestOnLongPress.value(id) }
                        touchState.pendingLongPress = task
                        mainHandler.postDelayed(task, 2_000L)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        touchState.twoFingerX = (event.getX(0) + event.getX(1)) * 0.5f
                        touchState.twoFingerY = (event.getY(0) + event.getY(1)) * 0.5f
                    }
                    touchState.pendingLongPress?.let(mainHandler::removeCallbacks)
                    touchState.pendingLongPress = null
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val id = latestSelectedModelId.value
                        if (id != null) {
                            val midX = (event.getX(0) + event.getX(1)) * 0.5f
                            val midY = (event.getY(0) + event.getY(1)) * 0.5f
                            val dx = midX - touchState.twoFingerX
                            val dy = midY - touchState.twoFingerY
                            if (abs(dx) + abs(dy) > 0.5f) {
                                val current = currentTransform(id)
                                if (current != null) {
                                    submitTransform(id, current.copy(z = current.z - dy / 850f, rx = current.rx + dy * 0.22f, ry = current.ry + dx * 0.22f))
                                }
                            }
                            touchState.twoFingerX = midX
                            touchState.twoFingerY = midY
                        }
                    }
                    if (abs(event.x - touchState.pressDownX) > touchSlop || abs(event.y - touchState.pressDownY) > touchSlop) {
                        touchState.pendingLongPress?.let(mainHandler::removeCallbacks)
                        touchState.pendingLongPress = null
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchState.pendingLongPress?.let(mainHandler::removeCallbacks)
                    touchState.pendingLongPress = null
                }
            }
            false
        },
        onFrame = {
            if (hasReportedFrame.compareAndSet(false, true)) {
                mainHandler.post { latestOnRendererFrame.value() }
            }
        }
    ) {
        LightNode(
            type = LightManager.Type.DIRECTIONAL,
            apply = {
                intensity(42_000f)
                direction(-0.45f, -0.65f, -0.62f)
                color(1f, 0.96f, 0.90f)
                castShadows(true)
            }
        )
        LightNode(
            type = LightManager.Type.DIRECTIONAL,
            apply = {
                intensity(15_000f)
                direction(0.55f, 0.35f, 0.70f)
                color(0.78f, 0.84f, 1f)
                castShadows(false)
            }
        )

        models.forEach { model ->
            key(model.id) {
                val t = model.transform
                Node(
                    position = Position(t.x, t.y, t.z),
                    rotation = Rotation(t.rx, t.ry, t.rz),
                    scale = Scale(t.scale),
                    apply = { name = model.id.toString() }
                ) {
                    if (!model.sourceFilePath.isNullOrBlank()) {
                        val source = model.sourceFilePath
                        if (File(source).isFile) {
                            val uri = remember(source) { android.net.Uri.fromFile(File(source)).toString() }
                            val instance = rememberModelInstance(modelLoader, fileLocation = uri)
                            instance?.let {
                                ModelNode(
                                    modelInstance = it,
                                    autoAnimate = false,
                                    scaleToUnits = 1.2f,
                                    centerOrigin = Position(0f, 0f, 0f),
                                    position = Position(0f, 0f, 0f),
                                    rotation = Rotation(0f, 0f, 0f),
                                    scale = Scale(1f),
                                    apply = { name = model.id.toString() }
                                )
                            }
                        } else {
                            android.util.Log.w("Mobile3DStudio", "Imported GLB is missing: $source")
                        }
                    } else when (model.kind) {
                        NativeModelKind.IRREGULAR_DICE -> {
                            val mesh = produceState<DiceMeshSet?>(null, model.id, model.seed, model.pointCount) {
                                value = runCatching {
                                    withContext(Dispatchers.Default) {
                                        NativeMeshFactory.irregularDice(model.pointCount, model.seed)
                                    }
                                }.onFailure { error ->
                                    android.util.Log.e("Mobile3DStudio", "Native irregular die mesh generation failed", error)
                                    mainHandler.post { latestOnRendererError.value(error.message ?: "不规则骰子网格生成失败") }
                                }.getOrNull()
                            }.value
                            mesh?.let { generated ->
                                ProceduralMesh("${model.id}-body", generated.body, dieMaterial, model.id.toString())
                                ProceduralMesh("${model.id}-pips", generated.pipOverlay, pipMaterial, model.id.toString())
                            }
                        }
                        NativeModelKind.DOUBLE_CUT_DICE -> {
                            val mesh = produceState<DiceMeshSet?>(null, model.id, model.seed, model.pointCount) {
                                value = runCatching {
                                    withContext(Dispatchers.Default) {
                                        NativeMeshFactory.doubleCutDice(model.pointCount, model.seed)
                                    }
                                }.onFailure { error ->
                                    android.util.Log.e("Mobile3DStudio", "Native double-cut die mesh generation failed", error)
                                    mainHandler.post { latestOnRendererError.value(error.message ?: "双切面骰子网格生成失败") }
                                }.getOrNull()
                            }.value
                            mesh?.let { generated ->
                                ProceduralMesh("${model.id}-body", generated.body, dieMaterial, model.id.toString())
                                ProceduralMesh("${model.id}-pips", generated.pipOverlay, pipMaterial, model.id.toString())
                                ProceduralMesh("${model.id}-cut-faces", generated.cutFaces, cutMaterial, model.id.toString())
                            }
                        }
                        NativeModelKind.POKER_CARD -> {
                            val cardMesh = remember(model.id) { NativeMeshFactory.pokerCard() }
                            ProceduralMesh("${model.id}-card-body", cardMesh, cardEdgeMaterial, model.id.toString())
                            if (frontBitmap != null) {
                                ImageNode(
                                    bitmap = frontBitmap,
                                    size = Size(0.84f, 1.25f),
                                    position = Position(0f, 0f, 0.0102f),
                                    apply = { name = model.id.toString() }
                                )
                            }
                            if (backBitmap != null) {
                                ImageNode(
                                    bitmap = backBitmap,
                                    size = Size(0.84f, 1.25f),
                                    position = Position(0f, 0f, -0.0102f),
                                    rotation = Rotation(y = 180f),
                                    apply = { name = model.id.toString() }
                                )
                            }
                        }

                    }
                }
            }
        }
    }
}

/** Builds a Filament Geometry once per model/layer and releases its GPU buffers with the node. */
@Composable
private fun SceneScope.ProceduralMesh(
    key: String,
    mesh: NativeMeshData,
    material: com.google.android.filament.MaterialInstance,
    nodeName: String
) {
    if (mesh.isEmpty()) return
    val geometry = remember(engine, key) {
        Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
            .vertices(mesh.vertices)
            .indices(mesh.indices)
            .build(engine)
    }
    DisposableEffect(geometry) {
        onDispose { engine.safeDestroyGeometry(geometry) }
    }
    MeshNode(
        primitiveType = RenderableManager.PrimitiveType.TRIANGLES,
        vertexBuffer = geometry.vertexBuffer,
        indexBuffer = geometry.indexBuffer,
        boundingBox = geometry.boundingBox,
        materialInstance = material,
        apply = { name = nodeName }
    )
}

private fun decodeAsset(context: Context, path: String): Bitmap? = runCatching {
    context.assets.open(path).use { BitmapFactory.decodeStream(it) }
}.getOrNull()
