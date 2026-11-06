package com.example.mobile3dstudio.core.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal data class NativeProjectSnapshot(
    val imagePath: String?,
    val imageScale: Float,
    val imageOffsetX: Float,
    val imageOffsetY: Float,
    val imageRotation: Float,
    val isModelWorkspace: Boolean,
    val selectedModelType: String,
    val pointInput: String,
    val models: List<NativeSceneModel>,
    val selectedModelId: Long?
)

/** Persists the complete local editing session without changing the exported GLB format. */
internal object NativeProjectStore {
    private const val FILE_NAME = "mobile3dstudio-project.json"

    fun load(context: Context): NativeProjectSnapshot? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val items = root.optJSONArray("models") ?: JSONArray()
            val models = buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.getLong("id")
                    add(
                        NativeSceneModel(
                            id = id,
                            kind = NativeModelKind.valueOf(item.getString("kind")),
                            pointCount = item.optInt("pointCount", 0),
                            transform = readTransform(item.optJSONObject("transform")),
                            cutPercent = if (item.isNull("cutPercent")) null else item.optDouble("cutPercent").toFloat(),
                            seed = item.optInt("seed", id.toInt()),
                            defaultTransform = readTransform(item.optJSONObject("defaultTransform")),
                            sourceFilePath = item.optString("sourceFilePath").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
            NativeProjectSnapshot(
                imagePath = root.optString("imagePath").takeIf(String::isNotBlank),
                imageScale = root.optDouble("imageScale", 1.0).toFloat(),
                imageOffsetX = root.optDouble("imageOffsetX", 0.0).toFloat(),
                imageOffsetY = root.optDouble("imageOffsetY", 0.0).toFloat(),
                imageRotation = root.optDouble("imageRotation", 0.0).toFloat(),
                isModelWorkspace = root.optBoolean("isModelWorkspace", false),
                selectedModelType = root.optString("selectedModelType", "fragment-dice"),
                pointInput = root.optString("pointInput", "9"),
                models = models,
                selectedModelId = root.optLong("selectedModelId", -1L).takeIf { it > 0L }
            )
        }.getOrNull()
    }

    fun save(context: Context, snapshot: NativeProjectSnapshot) {
        val items = JSONArray()
        snapshot.models.forEach { model ->
            val item = JSONObject()
                .put("id", model.id)
                .put("kind", model.kind.name)
                .put("pointCount", model.pointCount)
                .put("seed", model.seed)
                .put("transform", writeTransform(model.transform))
                .put("defaultTransform", writeTransform(model.defaultTransform))
            model.cutPercent?.let { item.put("cutPercent", it.toDouble()) }
            model.sourceFilePath?.let { item.put("sourceFilePath", it) }
            items.put(item)
        }
        val root = JSONObject()
            .put("imagePath", snapshot.imagePath ?: JSONObject.NULL)
            .put("imageScale", snapshot.imageScale.toDouble())
            .put("imageOffsetX", snapshot.imageOffsetX.toDouble())
            .put("imageOffsetY", snapshot.imageOffsetY.toDouble())
            .put("imageRotation", snapshot.imageRotation.toDouble())
            .put("isModelWorkspace", snapshot.isModelWorkspace)
            .put("selectedModelType", snapshot.selectedModelType)
            .put("pointInput", snapshot.pointInput)
            .put("selectedModelId", snapshot.selectedModelId ?: JSONObject.NULL)
            .put("models", items)

        val destination = File(context.filesDir, FILE_NAME)
        val temporary = File(context.filesDir, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(destination)) {
            destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                throw IOException("无法保存当前场景。")
            }
        }
    }

    private fun writeTransform(value: NativeTransform) = JSONObject()
        .put("x", value.x.toDouble())
        .put("y", value.y.toDouble())
        .put("z", value.z.toDouble())
        .put("rx", value.rx.toDouble())
        .put("ry", value.ry.toDouble())
        .put("rz", value.rz.toDouble())
        .put("scale", value.scale.toDouble())

    private fun readTransform(value: JSONObject?): NativeTransform {
        if (value == null) return NativeTransform()
        return NativeTransform(
            x = value.optDouble("x", 0.0).toFloat(),
            y = value.optDouble("y", 0.0).toFloat(),
            z = value.optDouble("z", 0.0).toFloat(),
            rx = value.optDouble("rx", 0.0).toFloat(),
            ry = value.optDouble("ry", 0.0).toFloat(),
            rz = value.optDouble("rz", 0.0).toFloat(),
            scale = value.optDouble("scale", 1.0).toFloat().coerceIn(0.05f, 10f)
        )
    }
}
