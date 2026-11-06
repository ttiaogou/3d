package com.example.mobile3dstudio.ui.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.filament.LightManager
import com.google.android.filament.RenderableManager
import io.github.sceneview.SceneScope
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node as SceneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.safeDestroyGeometry
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.example.mobile3dstudio.core.model.NativeModelKind
import com.example.mobile3dstudio.core.model.NativeSceneModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native model viewport. All visible models are procedural Kotlin meshes rendered by
 * SceneView/Filament; no WebView, HTML, JavaScript or WebGL is used for this viewport.
 * The imported flat image remains in the Compose layer underneath this transparent view.
 */
@Composable
fun NativeSceneViewport(
    modifier: Modifier = Modifier,
    models: List<NativeSceneModel>,
    onModelSelected: (Long?) -> Unit,
    onRendererFrame: () -> Unit = {}
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = Position(2.55f, 2.05f, 3.25f),
        targetPosition = Position(0f, 0f, 0f)
    )
    val latestOnModelSelected = rememberUpdatedState(onModelSelected)
    val latestOnRendererFrame = rememberUpdatedState(onRendererFrame)
    val hasReportedFrame = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val gestureListener = remember {
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent, node: SceneNode?) {
                latestOnModelSelected.value(node?.name?.toLongOrNull())
            }
        }
    }

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
            // Child renderables leave the scene before these shared material instances are freed.
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

    SceneView(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        autoCenterContent = false,
        cameraManipulator = cameraManipulator,
        onGestureListener = gestureListener,
        onFrame = {
            if (hasReportedFrame.compareAndSet(false, true)) {
                mainHandler.post { latestOnRendererFrame.value.invoke() }
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
            androidx.compose.runtime.key(model.id) {
                val t = model.transform
                Node(
                position = Position(t.x, t.y, t.z),
                rotation = Rotation(t.rx, t.ry, t.rz),
                scale = Scale(t.scale)
            ) {
                when (model.kind) {
                    NativeModelKind.IRREGULAR_DICE -> {
                        val mesh = produceState<DiceMeshSet?>(null, model.id, model.seed, model.pointCount) {
                            value = runCatching {
                                withContext(Dispatchers.Default) {
                                    NativeMeshFactory.irregularDice(model.pointCount, model.seed)
                                }
                            }.onFailure {
                                android.util.Log.e("Mobile3DStudio", "Native irregular die mesh generation failed", it)
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
                            }.onFailure {
                                android.util.Log.e("Mobile3DStudio", "Native double-cut die mesh generation failed", it)
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
