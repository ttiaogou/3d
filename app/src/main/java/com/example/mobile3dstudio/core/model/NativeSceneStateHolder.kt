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

    fun updateTransform(axis: String, value: Float) {
        val id = selectedModelId ?: return
        if (!transformEditOpen) {
            pushSnapshot()
            transformEditOpen = true
        }
        models = models.map { model ->
            if (model.id != id) return@map model
            val old = model.transform
            val next = when (axis) {
                "x" -> old.copy(x = value * 0.55f)
                "y" -> old.copy(y = -value * 0.55f)
                "z" -> old.copy(z = value * 0.35f)
                "rx" -> old.copy(rx = value)
                "ry" -> old.copy(ry = value)
                "rz" -> old.copy(rz = value)
                "scale" -> old.copy(scale = value.coerceIn(0.05f, 10f))
                else -> old
            }
            model.copy(transform = next)
        }
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
            if (model.id == id) model.copy(transform = transform.copy(scale = transform.scale.coerceIn(0.05f, 10f))) else model
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
