package com.example.mobile3dstudio.core.model

/** The three editing modes exposed by the app. */
enum class EditorMode(val title: String, val subtitle: String) {
    TWO_D("2D", "图片处理"),
    TWO_POINT_FIVE_D("2.5D", "深度与视差"),
    THREE_D("3D", "三维模型")
}

data class ImageProject(
    val id: String,
    val displayName: String,
    val sourceUri: String,
    val createdAtEpochMillis: Long
)

data class Transform3D(
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val positionZ: Float = 0f,
    val rotationX: Float = 18f,
    val rotationY: Float = 25f,
    val rotationZ: Float = 0f,
    val scale: Float = 1f
)
