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
import kotlin.math.cos
import kotlin.math.sin

private enum class ModelGestureMode { NONE, MOVE, ROTATE }

private class ViewportTouchState {
    var activeModelId: Long? = null
    var gestureMode = ModelGestureMode.NONE
    var pressDownX = 0f
    var pressDownY = 0f
    var lastX = 0f
    var lastY = 0f
    var didTransform = false
    var longPressTriggered = false
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

            // Single-finger movement and edge rotation are handled from the raw touch stream below.
            // Keep the library's scale callbacks so two-finger pinch-to-scale remains available.
            override fun onMoveBegin(detector: MoveGestureDetector, e: MotionEvent, node: SceneNode?) {}
            override fun onMove(detector: MoveGestureDetector, e: MotionEvent, node: SceneNode?) {}
            override fun onMoveEnd(detector: MoveGestureDetector, e: MotionEvent, node: SceneNode?) {}
            override fun onRotateBegin(detector: RotateGestureDetector, e: MotionEvent, node: SceneNode?) {}
            override fun onRotate(detector: RotateGestureDetector, e: MotionEvent, node: SceneNode?) {}
            override fun onRotateEnd(detector: RotateGestureDetector, e: MotionEvent, node: SceneNode?) {}

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
                    touchState.pendingLongPress = null
                    touchState.activeModelId = modelId(hitResult?.node)
                    touchState.pressDownX = event.x
                    touchState.pressDownY = event.y
                    touchState.lastX = event.x
                    touchState.lastY = event.y
                    touchState.didTransform = false
                    touchState.longPressTriggered = false

                    val id = touchState.activeModelId
                    if (id != null) {
                        val model = latestModels.value.firstOrNull { it.id == id }
                        val wasSelected = latestSelectedModelId.value == id
                        touchState.gestureMode = if (wasSelected && isModelEdgeHit(hitResult, model)) {
                            ModelGestureMode.ROTATE
                        } else {
                            ModelGestureMode.MOVE
                        }
                        latestOnModelSelected.value(id)
                        val task = Runnable {
                            touchState.longPressTriggered = true
                            touchState.gestureMode = ModelGestureMode.NONE
                            touchState.activeModelId = null
                            latestOnLongPress.value(id)
                        }
                        touchState.pendingLongPress = task
                        mainHandler.postDelayed(task, LONG_PRESS_TIMEOUT_MS)
                    } else {
                        touchState.gestureMode = ModelGestureMode.NONE
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    touchState.pendingLongPress?.let(mainHandler::removeCallbacks)
                    touchState.pendingLongPress = null
                    touchState.activeModelId?.takeIf { touchState.didTransform }?.let(::finishGesture)
                    touchState.activeModelId = null
                    touchState.gestureMode = ModelGestureMode.NONE
                    touchState.didTransform = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !touchState.longPressTriggered) {
                        val id = touchState.activeModelId
                        val dx = event.x - touchState.lastX
                        val dy = event.y - touchState.lastY
                        val crossedTouchSlop =
                            abs(event.x - touchState.pressDownX) > touchSlop ||
                            abs(event.y - touchState.pressDownY) > touchSlop

                        if (crossedTouchSlop) {
                            touchState.pendingLongPress?.let(mainHandler::removeCallbacks)
                            touchState.pendingLongPress = null
                            if (id != null && touchState.gestureMode != ModelGestureMode.NONE &&
                                (abs(dx) + abs(dy)) > 0.01f
                            ) {
                                val current = currentTransform(id)
                                if (current != null) {
                                    val next = when (touchState.gestureMode) {
                                        ModelGestureMode.MOVE -> current.copy(
                                            x = current.x + dx / MODEL_DRAG_PIXELS_PER_UNIT,
                                            y = current.y - dy / MODEL_DRAG_PIXELS_PER_UNIT
                                        )
                                        ModelGestureMode.ROTATE -> current.copy(
                                            rx = current.rx + dy * EDGE_ROTATION_DEGREES_PER_PIXEL,
                                            ry = current.ry + dx * EDGE_ROTATION_DEGREES_PER_PIXEL
                                        )
                                        ModelGestureMode.NONE -> current
                                    }
                                    submitTransform(id, next)
                                    touchState.didTransform = true
                                }
                            }
                        }
                        touchState.lastX = event.x
                        touchState.lastY = event.y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchState.pendingLongPress?.let(mainHandler::removeCallbacks)
                    touchState.pendingLongPress = null
                    touchState.activeModelId?.takeIf { touchState.didTransform }?.let(::finishGesture)
                    touchState.activeModelId = null
                    touchState.gestureMode = ModelGestureMode.NONE
                    touchState.didTransform = false
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
                    apply = { name = model.id.toString(); configureManualModelGestures() }
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
                                    apply = { name = model.id.toString(); configureManualModelGestures() }
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
                                    apply = { name = model.id.toString(); configureManualModelGestures() }
                                )
                            }
                            if (backBitmap != null) {
                                ImageNode(
                                    bitmap = backBitmap,
                                    size = Size(0.84f, 1.25f),
                                    position = Position(0f, 0f, -0.0102f),
                                    rotation = Rotation(y = 180f),
                                    apply = { name = model.id.toString(); configureManualModelGestures() }
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
        apply = { name = nodeName; configureManualModelGestures() }
    )
}

/**
 * Marks a hit-tested model node as an editing target so SceneView does not orbit the camera,
 * while disabling its built-in axis transforms; the editor applies its own single-finger rules.
 */
private fun SceneNode.configureManualModelGestures() {
    isEditable = true
    isPositionEditable = false
    isRotationEditable = false
    isScaleEditable = false
}

private const val LONG_PRESS_TIMEOUT_MS = 1_500L
private const val MODEL_DRAG_PIXELS_PER_UNIT = 180f
private const val EDGE_ROTATION_DEGREES_PER_PIXEL = 0.45f
private const val MODEL_EDGE_THRESHOLD = 0.74f
private const val CARD_EDGE_THRESHOLD = 0.78f

/** Returns true when the initial contact point lies near the model's silhouette edge. */
private fun isModelEdgeHit(hitResult: HitResult?, model: NativeSceneModel?): Boolean {
    if (hitResult == null || model == null) return false
    val hit = runCatching { hitResult.getPoint() }.getOrNull() ?: return false
    val transform = model.transform
    val dx = hit.x - transform.x
    val dy = hit.y - transform.y
    val dz = hit.z - transform.z
    val radians = (Math.PI / 180.0).toFloat()
    val rx = transform.rx * radians
    val ry = transform.ry * radians
    val rz = transform.rz * radians

    // Undo the node's Z-Y-X Euler rotation, then its uniform scale and translation.
    val cosZ = cos(rz)
    val sinZ = sin(rz)
    val x1 = cosZ * dx + sinZ * dy
    val y1 = -sinZ * dx + cosZ * dy
    val z1 = dz
    val cosY = cos(ry)
    val sinY = sin(ry)
    val x2 = cosY * x1 - sinY * z1
    val y2 = y1
    val z2 = sinY * x1 + cosY * z1
    val cosX = cos(rx)
    val sinX = sin(rx)
    val localX = x2 / transform.scale.coerceAtLeast(0.001f)
    val localY = (cosX * y2 + sinX * z2) / transform.scale.coerceAtLeast(0.001f)
    val localZ = (-sinX * y2 + cosX * z2) / transform.scale.coerceAtLeast(0.001f)

    val edgeRatio = if (model.kind == NativeModelKind.POKER_CARD && model.sourceFilePath == null) {
        maxOf(abs(localX) / 0.46f, abs(localY) / 0.67f)
    } else {
        val halfExtent = if (model.sourceFilePath != null) 0.60f else 0.62f
        listOf(abs(localX), abs(localY), abs(localZ))
            .map { it / halfExtent }
            .sortedDescending()
            .getOrElse(1) { 0f }
    }
    val threshold = if (model.kind == NativeModelKind.POKER_CARD && model.sourceFilePath == null) {
        CARD_EDGE_THRESHOLD
    } else {
        MODEL_EDGE_THRESHOLD
    }
    return edgeRatio >= threshold
}

private fun decodeAsset(context: Context, path: String): Bitmap? = runCatching {
    context.assets.open(path).use { BitmapFactory.decodeStream(it) }
}.getOrNull()
