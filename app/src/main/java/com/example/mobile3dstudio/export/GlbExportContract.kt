package com.example.mobile3dstudio.export

import android.net.Uri
import com.example.mobile3dstudio.core.model.Transform3D

/** A scene object ready to be serialized to glTF/GLB. */
data class ExportableSceneObject(
    val id: String,
    val sourceModelUri: String,
    val transform: Transform3D
)

data class ExportableScene(val objects: List<ExportableSceneObject>)

sealed interface GlbExportResult {
    data class Success(val destination: Uri) : GlbExportResult
    data class Failure(val reason: String) : GlbExportResult
}

/** Implement this when the GLB writer is selected and integrated. */
interface GlbExportEngine {
    suspend fun export(scene: ExportableScene, destination: Uri): GlbExportResult
}
