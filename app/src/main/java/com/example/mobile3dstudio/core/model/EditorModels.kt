package com.example.mobile3dstudio.core.model

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
