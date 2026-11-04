package com.example.mobile3dstudio.processing

import android.net.Uri

/**
 * Contracts only. No AI model or cloud endpoint is bundled in this first scaffold.
 * Implementations can later be local/offline and can be replaced independently.
 */

data class NormalizedBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class SubjectCandidate(
    val id: String,
    val label: String,
    val confidence: Float,
    val bounds: NormalizedBounds
)

data class SegmentationArtifact(
    val maskUri: String,
    val width: Int,
    val height: Int
)

data class DepthArtifact(
    val depthUri: String,
    val width: Int,
    val height: Int,
    val isMetricDepth: Boolean
)

data class ReconstructedModel(
    val glbUri: String,
    val vertexCount: Int,
    val triangleCount: Int
)

interface SubjectRecognitionEngine {
    suspend fun recognize(image: Uri): List<SubjectCandidate>
}

interface SubjectSegmentationEngine {
    suspend fun segment(image: Uri, subject: SubjectCandidate): SegmentationArtifact
}

interface DepthEstimationEngine {
    suspend fun estimate(image: Uri, mask: SegmentationArtifact?): DepthArtifact
}

interface ImageTo3DReconstructionEngine {
    suspend fun reconstruct(
        image: Uri,
        mask: SegmentationArtifact?,
        depth: DepthArtifact?
    ): ReconstructedModel
}

enum class EngineReadiness {
    NOT_CONNECTED,
    READY,
    RUNNING,
    FAILED
}
