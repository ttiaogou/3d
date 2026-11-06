package com.example.mobile3dstudio.core.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.ArrayDeque
import java.util.Random

/** Small Compose state holder for the native scene and its undo history. */
class NativeSceneStateHolder {
    var models by mutableStateOf<List<NativeSceneModel>>(emptyList())
        private set

    var selectedModelId by mutableStateOf<Long?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set

    private data class Snapshot(
        val models: List<NativeSceneModel>,
        val selectedId: Long?
    )

    private val history = ArrayDeque<Snapshot>()
    private var nextId = 1L
    private var transformEditOpen = false

    fun add(kind: NativeModelKind, pointCount: Int = 0): NativeSceneModel {
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
        val default = when (kind) {
            NativeModelKind.IRREGULAR_DICE -> NativeTransform(
                x = column, y = row, rx = random.between(-24f, 24f),
                ry = random.between(-32f, 32f), rz = random.between(-18f, 18f)
            )
            NativeModelKind.DOUBLE_CUT_DICE -> NativeTransform(
                x = column, y = row, rx = random.between(-32f, -17f),
                ry = random.between(-28f, 28f), rz = random.between(-180f, 180f)
            )
            NativeModelKind.POKER_CARD -> NativeTransform(
                x = column, y = row, rx = random.between(-10f, 10f),
                ry = random.between(-22f, 22f), rz = random.between(-12f, 12f)
            )
        }
        val model = NativeSceneModel(
            id = id,
            kind = kind,
            pointCount = pointCount,
            transform = default,
            seed = seed,
            defaultTransform = default
        )
        models = models + model
        selectedModelId = id
        transformEditOpen = false
        return model
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
                else -> old
            }
            model.copy(transform = next)
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

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val previous = history.removeLast()
        models = previous.models
        selectedModelId = previous.selectedId?.takeIf { id -> models.any { it.id == id } }
        transformEditOpen = false
        canUndo = history.isNotEmpty()
        return true
    }

    fun selectedTransform(): NativeTransform? =
        selectedModelId?.let { id -> models.firstOrNull { it.id == id }?.transform }

    private fun pushSnapshot() {
        history.addLast(Snapshot(models, selectedModelId))
        while (history.size > MAX_UNDO_STEPS) history.removeFirst()
        canUndo = history.isNotEmpty()
    }

    private fun Random.between(minimum: Float, maximum: Float): Float =
        minimum + nextFloat() * (maximum - minimum)

    private companion object {
        const val MAX_UNDO_STEPS = 40
    }
}
