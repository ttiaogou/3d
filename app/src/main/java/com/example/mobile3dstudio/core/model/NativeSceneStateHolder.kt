package com.example.mobile3dstudio.core.model

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.ArrayDeque
import java.util.Random

/** Small Compose state holder for the native scene and its undo/redo history. */
class NativeSceneStateHolder {
    var models by mutableStateOf<List<NativeSceneModel>>(emptyList())
        private set

    var selectedModelId by mutableStateOf<Long?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private data class Snapshot(
        val models: List<NativeSceneModel>,
        val selectedId: Long?
    )

    private val history = ArrayDeque<Snapshot>()
    private val redoHistory = ArrayDeque<Snapshot>()
    private var nextId = 1L
    private var transformEditOpen = false

    fun add(kind: NativeModelKind, pointCount: Int = 0): NativeSceneModel =
        addInternal(kind, pointCount, null)

    fun addImportedGlb(filePath: String): NativeSceneModel {
        require(filePath.isNotBlank()) { "GLB 文件路径无效。" }
        return addInternal(NativeModelKind.IRREGULAR_DICE, 0, filePath)
    }

    private fun addInternal(kind: NativeModelKind, pointCount: Int, sourceFilePath: String?): NativeSceneModel {
        require(pointCount in 0..9)
        pushSnapshot()
        val id = nextId++
        val seed = Random().nextInt()
        val random = Random(seed.toLong())
        val index = models.size
        val column = when (index % 3) {
            0 -> 0f
            1 -> 0.95f
            else -> -0.95f
        }
        val row = -(index / 3) * 0.78f
        val defaultScale = adaptiveDefaultScale(kind, sourceFilePath)
        val default = if (sourceFilePath != null) {
            NativeTransform(x = column, y = row, scale = defaultScale)
        } else when (kind) {
            NativeModelKind.IRREGULAR_DICE -> NativeTransform(
                x = column, y = row, rx = random.between(-24f, 24f),
                ry = random.between(-32f, 32f), rz = random.between(-18f, 18f),
                scale = defaultScale
            )
            NativeModelKind.DOUBLE_CUT_DICE -> NativeTransform(
                x = column, y = row, rx = random.between(-32f, -17f),
                ry = random.between(-28f, 28f), rz = random.between(-180f, 180f),
                scale = defaultScale
            )
            NativeModelKind.POKER_CARD -> NativeTransform(
                x = column, y = row, rx = random.between(-10f, 10f),
                ry = random.between(-22f, 22f), rz = random.between(-12f, 12f),
                scale = defaultScale
            )
        }
        val model = NativeSceneModel(
            id = id,
            kind = kind,
            pointCount = pointCount,
            transform = default,
            seed = seed,
            defaultTransform = default,
            sourceFilePath = sourceFilePath
        )
        models = models + model
        selectedModelId = id
        transformEditOpen = false
        return model
    }

    /**
     * Keeps newly-created models at a phone-friendly size. The editor viewport occupies about
     * 62.5% of the available app height; using its estimated aspect ratio avoids desktop-sized
     * defaults on narrow Android screens while allowing a modest increase on wider layouts.
     */
    private fun adaptiveDefaultScale(kind: NativeModelKind, sourceFilePath: String?): Float {
        val metrics = Resources.getSystem().displayMetrics
        val editHeight = metrics.heightPixels * EDITOR_AREA_HEIGHT_FRACTION
        val editorAspect = if (editHeight > 0f) metrics.widthPixels / editHeight else REFERENCE_EDITOR_ASPECT
        val fittedAspect = minOf(editorAspect, 1f)
        val aspectFactor = kotlin.math.sqrt(
            (fittedAspect / REFERENCE_EDITOR_ASPECT).coerceIn(MIN_ASPECT_FACTOR, MAX_ASPECT_FACTOR)
        )
        val baseScale = when {
            sourceFilePath != null -> IMPORTED_MODEL_BASE_SCALE
            kind == NativeModelKind.POKER_CARD -> POKER_BASE_SCALE
            else -> DICE_BASE_SCALE
        }
        return (baseScale * aspectFactor).coerceIn(MIN_DEFAULT_SCALE, MAX_DEFAULT_SCALE)
    }

    fun select(id: Long?) {
        selectedModelId = id?.takeIf { candidate -> models.any { it.id == candidate } }
        transformEditOpen = false
    }

    /** Applies a small relative adjustment from the parameter panel, never an absolute reset. */
    fun adjustTransformByDelta(axis: String, delta: Float) {
        val id = selectedModelId ?: return
        if (!delta.isFinite() || delta == 0f) return
        if (!transformEditOpen) {
            pushSnapshot()
            transformEditOpen = true
        }
        models = models.map { model ->
            if (model.id != id) return@map model
            val old = model.transform
            val next = when (axis) {
                "x" -> old.copy(x = old.x + delta * 0.55f)
                "y" -> old.copy(y = old.y - delta * 0.55f)
                "z" -> old.copy(z = old.z + delta * 0.35f)
                "rx" -> old.copy(rx = old.rx + delta)
                "ry" -> old.copy(ry = old.ry + delta)
                "rz" -> old.copy(rz = old.rz + delta)
                "scale" -> old.copy(scale = (old.scale * (1f + delta)).coerceIn(0.05f, MAX_MODEL_SCALE))
                else -> old
            }
            model.copy(transform = next)
        }
    }

    /** Applies the current image-space transform to a model created after the image was edited. */
    fun attachNewModelToBackground(
        id: Long,
        imageScale: Float,
        imageOffsetX: Float,
        imageOffsetY: Float,
        imageRotation: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ) {
        val target = models.firstOrNull { it.id == id } ?: return
        val scale = imageScale.coerceAtLeast(0.1f)
        val width = viewportWidth.coerceAtLeast(1f)
        val height = viewportHeight.coerceAtLeast(1f)
        val angle = Math.toRadians(imageRotation.toDouble())
        val cosAngle = kotlin.math.cos(angle).toFloat()
        val sinAngle = kotlin.math.sin(angle).toFloat()
        val pixelsPerWorldUnit = (width / 2f).coerceAtLeast(1f)
        val tx = imageOffsetX * width / pixelsPerWorldUnit
        val ty = -imageOffsetY * height / pixelsPerWorldUnit
        val nextTransform = applyBackgroundTransformToModel(
            target.transform,
            oldTx = 0f,
            oldTy = 0f,
            newTx = tx,
            newTy = ty,
            oldScale = 1f,
            newScale = scale,
            oldCos = 1f,
            oldSin = 0f,
            newCos = cosAngle,
            newSin = sinAngle,
            rotationDelta = imageRotation,
            scaleRatio = scale
        )
        val nextDefault = applyBackgroundTransformToModel(
            target.defaultTransform,
            oldTx = 0f,
            oldTy = 0f,
            newTx = tx,
            newTy = ty,
            oldScale = 1f,
            newScale = scale,
            oldCos = 1f,
            oldSin = 0f,
            newCos = cosAngle,
            newSin = sinAngle,
            rotationDelta = imageRotation,
            scaleRatio = scale
        )
        models = models.map { model ->
            if (model.id == id) model.copy(transform = nextTransform, defaultTransform = nextDefault) else model
        }
    }

    /**
     * Keeps every model attached to the same image-space point while the background is panned,
     * zoomed or rotated. Model transforms remain ordinary scene transforms, so PNG/GLB export and
     * saved projects retain the synchronized placement without an extra hidden parent node.
     */
    fun followBackgroundTransform(
        oldScale: Float,
        oldOffsetX: Float,
        oldOffsetY: Float,
        oldRotation: Float,
        newScale: Float,
        newOffsetX: Float,
        newOffsetY: Float,
        newRotation: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ) {
        if (models.isEmpty()) return
        val safeOldScale = oldScale.coerceAtLeast(0.1f)
        val safeNewScale = newScale.coerceAtLeast(0.1f)
        val safeWidth = viewportWidth.coerceAtLeast(1f)
        val safeHeight = viewportHeight.coerceAtLeast(1f)
        val pixelsPerWorldUnit = (safeWidth / 2f).coerceAtLeast(1f)
        val oldTx = oldOffsetX * safeWidth / pixelsPerWorldUnit
        val oldTy = -oldOffsetY * safeHeight / pixelsPerWorldUnit
        val newTx = newOffsetX * safeWidth / pixelsPerWorldUnit
        val newTy = -newOffsetY * safeHeight / pixelsPerWorldUnit
        val oldAngle = Math.toRadians(oldRotation.toDouble())
        val newAngle = Math.toRadians(newRotation.toDouble())
        val oldCos = kotlin.math.cos(oldAngle).toFloat()
        val oldSin = kotlin.math.sin(oldAngle).toFloat()
        val newCos = kotlin.math.cos(newAngle).toFloat()
        val newSin = kotlin.math.sin(newAngle).toFloat()
        val scaleRatio = safeNewScale / safeOldScale
        val rotationDelta = newRotation - oldRotation

        val hasChange = kotlin.math.abs(scaleRatio - 1f) > 1e-6f ||
            kotlin.math.abs(newTx - oldTx) > 1e-6f ||
            kotlin.math.abs(newTy - oldTy) > 1e-6f ||
            kotlin.math.abs(rotationDelta) > 1e-5f
        if (!hasChange) return

        if (!transformEditOpen) {
            pushSnapshot()
            transformEditOpen = true
        }
        models = models.map { model ->
            val nextTransform = applyBackgroundTransformToModel(
                model.transform, oldTx, oldTy, newTx, newTy,
                safeOldScale, safeNewScale, oldCos, oldSin, newCos, newSin,
                rotationDelta, scaleRatio
            )
            val nextDefault = applyBackgroundTransformToModel(
                model.defaultTransform, oldTx, oldTy, newTx, newTy,
                safeOldScale, safeNewScale, oldCos, oldSin, newCos, newSin,
                rotationDelta, scaleRatio
            )
            model.copy(transform = nextTransform, defaultTransform = nextDefault)
        }
    }

    private fun applyBackgroundTransformToModel(
        source: NativeTransform,
        oldTx: Float,
        oldTy: Float,
        newTx: Float,
        newTy: Float,
        oldScale: Float,
        newScale: Float,
        oldCos: Float,
        oldSin: Float,
        newCos: Float,
        newSin: Float,
        rotationDelta: Float,
        scaleRatio: Float
    ): NativeTransform {
        val relativeX = source.x - oldTx
        val relativeY = source.y - oldTy
        val imageLocalX = (oldCos * relativeX + oldSin * relativeY) / oldScale
        val imageLocalY = (-oldSin * relativeX + oldCos * relativeY) / oldScale
        val nextX = newTx + newScale * (newCos * imageLocalX - newSin * imageLocalY)
        val nextY = newTy + newScale * (newSin * imageLocalX + newCos * imageLocalY)
        val composedRotation = rotateModelAroundImageZ(source.rx, source.ry, source.rz, rotationDelta)
        return source.copy(
            x = nextX,
            y = nextY,
            z = source.z * scaleRatio,
            rx = composedRotation.first,
            ry = composedRotation.second,
            rz = composedRotation.third,
            scale = (source.scale * scaleRatio).coerceIn(0.05f, MAX_MODEL_SCALE)
        )
    }

    /** Compose a background Z rotation with a model's Z-Y-X Euler rotation. */
    private fun rotateModelAroundImageZ(
        rx: Float,
        ry: Float,
        rz: Float,
        deltaDegrees: Float
    ): Triple<Float, Float, Float> {
        if (kotlin.math.abs(deltaDegrees) < 1e-6f) return Triple(rx, ry, rz)
        val halfX = Math.toRadians(rx.toDouble()) * 0.5
        val halfY = Math.toRadians(ry.toDouble()) * 0.5
        val halfZ = Math.toRadians(rz.toDouble()) * 0.5
        val halfDelta = Math.toRadians(deltaDegrees.toDouble()) * 0.5
        val sx = kotlin.math.sin(halfX); val cx = kotlin.math.cos(halfX)
        val sy = kotlin.math.sin(halfY); val cy = kotlin.math.cos(halfY)
        val sz = kotlin.math.sin(halfZ); val cz = kotlin.math.cos(halfZ)
        val sp = kotlin.math.sin(halfDelta); val cp = kotlin.math.cos(halfDelta)

        val modelX = sx * cy * cz - cx * sy * sz
        val modelY = cx * sy * cz + sx * cy * sz
        val modelZ = cx * cy * sz - sx * sy * cz
        val modelW = cx * cy * cz + sx * sy * sz

        // qImage * qModel, with qImage rotating around the scene Z axis.
        val qx = cp * modelX - sp * modelY
        val qy = cp * modelY + sp * modelX
        val qz = cp * modelZ + sp * modelW
        val qw = cp * modelW - sp * modelZ

        val sinRoll = 2.0 * (qw * qx + qy * qz)
        val cosRoll = 1.0 - 2.0 * (qx * qx + qy * qy)
        val sinPitch = (2.0 * (qw * qy - qz * qx)).coerceIn(-1.0, 1.0)
        val sinYaw = 2.0 * (qw * qz + qx * qy)
        val cosYaw = 1.0 - 2.0 * (qy * qy + qz * qz)
        val nextRx = Math.toDegrees(kotlin.math.atan2(sinRoll, cosRoll)).toFloat()
        val nextRy = Math.toDegrees(kotlin.math.asin(sinPitch)).toFloat()
        val nextRz = Math.toDegrees(kotlin.math.atan2(sinYaw, cosYaw)).toFloat()
        return Triple(nextRx, nextRy, nextRz)
    }

    /** Updates one model from the viewport's direct-manipulation gestures. */
    fun updateTransformFromGesture(id: Long, transform: NativeTransform) {
        if (models.none { it.id == id }) return
        if (!transformEditOpen) {
            pushSnapshot()
            transformEditOpen = true
        }
        selectedModelId = id
        models = models.map { model ->
            if (model.id == id) model.copy(transform = transform.copy(scale = transform.scale.coerceIn(0.05f, MAX_MODEL_SCALE))) else model
        }
    }

    fun finishTransformEdit() {
        transformEditOpen = false
    }

    fun resetSelected() {
        val id = selectedModelId ?: return
        if (models.none { it.id == id }) return
        pushSnapshot()
        models = models.map { model ->
            if (model.id == id) model.copy(transform = model.defaultTransform) else model
        }
        transformEditOpen = false
    }

    fun removeSelected(): Boolean {
        val id = selectedModelId ?: return false
        return remove(id)
    }

    fun remove(id: Long): Boolean {
        if (models.none { it.id == id }) return false
        pushSnapshot()
        models = models.filterNot { it.id == id }
        if (selectedModelId == id) selectedModelId = null
        transformEditOpen = false
        return true
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        transformEditOpen = false
        redoHistory.addLast(Snapshot(models, selectedModelId))
        trim(redoHistory)
        restore(history.removeLast())
        refreshHistoryFlags()
        return true
    }

    fun redo(): Boolean {
        if (redoHistory.isEmpty()) return false
        transformEditOpen = false
        history.addLast(Snapshot(models, selectedModelId))
        trim(history)
        restore(redoHistory.removeLast())
        refreshHistoryFlags()
        return true
    }

    fun restoreScene(restoredModels: List<NativeSceneModel>, restoredSelectedId: Long?) {
        models = restoredModels
        selectedModelId = restoredSelectedId?.takeIf { id -> restoredModels.any { it.id == id } }
        nextId = (restoredModels.maxOfOrNull { it.id } ?: 0L) + 1L
        history.clear()
        redoHistory.clear()
        transformEditOpen = false
        refreshHistoryFlags()
    }

    fun selectedTransform(): NativeTransform? =
        selectedModelId?.let { id -> models.firstOrNull { it.id == id }?.transform }

    private fun restore(snapshot: Snapshot) {
        models = snapshot.models
        selectedModelId = snapshot.selectedId?.takeIf { id -> models.any { it.id == id } }
        nextId = maxOf(nextId, (models.maxOfOrNull { it.id } ?: 0L) + 1L)
    }

    private fun pushSnapshot() {
        history.addLast(Snapshot(models, selectedModelId))
        trim(history)
        redoHistory.clear()
        refreshHistoryFlags()
    }

    private fun trim(stack: ArrayDeque<Snapshot>) {
        while (stack.size > MAX_UNDO_STEPS) stack.removeFirst()
    }

    private fun refreshHistoryFlags() {
        canUndo = history.isNotEmpty()
        canRedo = redoHistory.isNotEmpty()
    }

    private fun Random.between(minimum: Float, maximum: Float): Float =
        minimum + nextFloat() * (maximum - minimum)

    private companion object {
        const val MAX_UNDO_STEPS = 40
        const val MAX_MODEL_SCALE = 80f
        const val EDITOR_AREA_HEIGHT_FRACTION = 0.625f
        const val REFERENCE_EDITOR_ASPECT = 0.74f
        const val MIN_ASPECT_FACTOR = 0.75f
        const val MAX_ASPECT_FACTOR = 1.35f
        const val DICE_BASE_SCALE = 0.46f
        const val POKER_BASE_SCALE = 0.52f
        const val IMPORTED_MODEL_BASE_SCALE = 0.48f
        const val MIN_DEFAULT_SCALE = 0.34f
        const val MAX_DEFAULT_SCALE = 0.62f
    }
}
