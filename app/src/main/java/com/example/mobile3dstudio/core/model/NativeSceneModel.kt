package com.example.mobile3dstudio.core.model

/** Model kinds rendered by the native SceneView / Filament viewport. */
enum class NativeModelKind {
    IRREGULAR_DICE,
    DOUBLE_CUT_DICE,
    POKER_CARD
}

data class NativeTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val rz: Float = 0f,
    val scale: Float = 1f
)

data class NativeSceneModel(
    val id: Long,
    val kind: NativeModelKind,
    /** Dot count on the +Z face. Accepted range: 0..9. */
    val pointCount: Int = 0,
    val transform: NativeTransform = NativeTransform(),
    /** Actual measured removed-volume percentage for a double-cut die. */
    val cutPercent: Float? = null,
    /** Stable per-object seed for the irregular surface and random cut planes. */
    val seed: Int = id.hashCode(),
    /** Transform restored by the workbench's Default button. */
    val defaultTransform: NativeTransform = transform,
    /** Private app-storage path for an imported GLB file. */
    val sourceFilePath: String? = null
)
