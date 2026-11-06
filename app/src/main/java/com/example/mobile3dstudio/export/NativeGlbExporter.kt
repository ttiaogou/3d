package com.example.mobile3dstudio.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.mobile3dstudio.core.model.NativeModelKind
import com.example.mobile3dstudio.core.model.NativeSceneModel
import com.example.mobile3dstudio.ui.scene.NativeMeshData
import com.example.mobile3dstudio.ui.scene.NativeMeshFactory
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.UvCoordinate
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Serializes the exact procedural meshes used by NativeSceneViewport into a glTF 2.0 binary file. */
internal object NativeGlbExporter {
    private data class Primitive(val mesh: NativeMeshData, val material: Int)
    private data class ModelGeometry(
        val primitives: List<Primitive>,
        val removedPercent: Float? = null
    )

    suspend fun export(
        context: Context,
        models: List<NativeSceneModel>,
        imageBitmap: Bitmap?,
        imageScale: Float,
        imageOffsetX: Float,
        imageOffsetY: Float,
        imageRotation: Float,
        viewportAspectRatio: Float
    ): File = withContext(Dispatchers.Default) {
            require(models.isNotEmpty() || imageBitmap != null) { "当前场景没有图片或 3D 模型，无法导出 GLB。" }
            val bin = BinaryBuilder()
            val bufferViews = JSONArray()
            val accessors = JSONArray()
            val materials = createMaterials()
            val images = JSONArray()
            val textures = JSONArray()
            val samplers = JSONArray()
            var frontMaterialIndex = -1
            var backMaterialIndex = -1
            if (models.any { it.kind == NativeModelKind.POKER_CARD }) {
                val frontTexture = addJpegTexture(context, "dice/queen-spades-front.jpg", bin, bufferViews, images, textures, samplers)
                val backTexture = addJpegTexture(context, "dice/playing-card-back.jpg", bin, bufferViews, images, textures, samplers)
                frontMaterialIndex = materials.length()
                materials.put(textureMaterial("黑桃 Q 正面", frontTexture))
                backMaterialIndex = materials.length()
                materials.put(textureMaterial("扑克牌背面", backTexture))
            }
            val previewMaterialIndex = if (imageBitmap != null) {
                val previewBitmap = createPreviewBitmap(
                    imageBitmap, imageScale, imageOffsetX, imageOffsetY, imageRotation, viewportAspectRatio
                )
                try {
                    val encoded = ByteArrayOutputStream().use { stream ->
                        if (!previewBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                            error("导出预览图片编码失败")
                        }
                        stream.toByteArray()
                    }
                    val texture = addTextureBytes(
                        encoded, "导入图片预览.jpg", bin, bufferViews, images, textures, samplers
                    )
                    materials.length().also { materials.put(textureMaterial("导入图片预览", texture)) }
                } finally {
                    previewBitmap.recycle()
                }
            } else null

            val meshes = JSONArray()
            val nodes = JSONArray()
            models.forEach { model ->
                val geometry = modelGeometry(model, frontMaterialIndex, backMaterialIndex)
                val primitives = JSONArray()
                geometry.primitives.filterNot { it.mesh.isEmpty() }.forEach { primitive ->
                    primitives.put(primitiveJson(primitive, bin, bufferViews, accessors))
                }
                require(primitives.length() > 0) { "模型 ${model.id} 没有有效网格，无法导出。" }
                val meshIndex = meshes.length()
                meshes.put(JSONObject()
                    .put("name", modelName(model.kind) + "-${model.id}")
                    .put("primitives", primitives))

                val t = model.transform
                val node = JSONObject()
                    .put("name", modelName(model.kind) + "-${model.id}")
                    .put("mesh", meshIndex)
                    .put("translation", JSONArray(listOf(t.x, t.y, t.z)))
                    .put("rotation", eulerQuaternion(t.rx, t.ry, t.rz))
                    .put("scale", JSONArray(listOf(t.scale, t.scale, t.scale)))
                geometry.removedPercent?.let { percent ->
                    node.put("extras", JSONObject().put("actualRemovedVolumePercent", percent))
                }
                nodes.put(node)
            }

            if (previewMaterialIndex != null) {
                val aspect = viewportAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
                val backgroundMesh = quad(width = 2f, height = 2f / aspect, z = -1.0f, backFace = false)
                val previewPrimitive = JSONArray().put(
                    primitiveJson(Primitive(backgroundMesh, previewMaterialIndex), bin, bufferViews, accessors)
                )
                val meshIndex = meshes.length()
                meshes.put(JSONObject().put("name", "ImportedImagePreview").put("primitives", previewPrimitive))
                nodes.put(JSONObject().put("name", "ImportedImagePreview").put("mesh", meshIndex))
            }

            val root = JSONObject()
                .put("asset", JSONObject().put("version", "2.0").put("generator", "Mobile3DStudio NativeScene/Filament"))
                .put("scene", 0)
                .put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray((0 until nodes.length()).toList()))))
                .put("nodes", nodes)
                .put("meshes", meshes)
                .put("materials", materials)
                .put("bufferViews", bufferViews)
                .put("accessors", accessors)
                .put("buffers", JSONArray().put(JSONObject().put("byteLength", bin.size())))
            if (images.length() > 0) {
                root.put("images", images)
                root.put("textures", textures)
                root.put("samplers", samplers)
            }

            val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
            val paddedJson = align(jsonBytes, 0x20)
            val binaryBytes = align(bin.toByteArray(), 0x00)
            val totalLength = 12 + 8 + paddedJson.size + 8 + binaryBytes.size
            val glb = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
            glb.putInt(0x46546C67) // glTF
            glb.putInt(2)
            glb.putInt(totalLength)
            glb.putInt(paddedJson.size)
            glb.putInt(0x4E4F534A) // JSON
            glb.put(paddedJson)
            glb.putInt(binaryBytes.size)
            glb.putInt(0x004E4942) // BIN\u0000
            glb.put(binaryBytes)

            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists() && !exportDir.mkdirs()) error("无法创建 GLB 导出缓存目录。")
            val output = File(exportDir, "Mobile3DStudio-${UUID.randomUUID()}.glb")
            FileOutputStream(output).use { stream ->
                stream.write(glb.array())
                stream.fd.sync()
            }
            output
        }

    private fun modelGeometry(
        model: NativeSceneModel,
        frontMaterialIndex: Int,
        backMaterialIndex: Int
    ): ModelGeometry = when (model.kind) {
        NativeModelKind.IRREGULAR_DICE -> {
            val result = NativeMeshFactory.irregularDice(model.pointCount, model.seed)
            ModelGeometry(listOf(Primitive(result.body, 0), Primitive(result.pipOverlay, 1)))
        }
        NativeModelKind.DOUBLE_CUT_DICE -> {
            val result = NativeMeshFactory.doubleCutDice(model.pointCount, model.seed)
            ModelGeometry(
                listOf(
                    Primitive(result.body, 0),
                    Primitive(result.pipOverlay, 1),
                    Primitive(result.cutFaces, 2)
                ),
                result.removedPercent
            )
        }
        NativeModelKind.POKER_CARD -> {
            val front = quad(width = .84f, height = 1.25f, z = .0102f, backFace = false)
            val back = quad(width = .84f, height = 1.25f, z = -.0102f, backFace = true)
            ModelGeometry(
                listOf(
                    Primitive(NativeMeshFactory.pokerCard(), 3),
                    Primitive(front, frontMaterialIndex),
                    Primitive(back, backMaterialIndex)
                )
            )
        }
    }

    private fun quad(width: Float, height: Float, z: Float, backFace: Boolean): NativeMeshData {
        val normal = Direction(0f, 0f, if (backFace) -1f else 1f)
        val vertices = listOf(
            Geometry.Vertex(position = Position(-width / 2f, -height / 2f, z), normal = normal, uvCoordinate = UvCoordinate(0f, 1f)),
            Geometry.Vertex(position = Position(width / 2f, -height / 2f, z), normal = normal, uvCoordinate = UvCoordinate(1f, 1f)),
            Geometry.Vertex(position = Position(width / 2f, height / 2f, z), normal = normal, uvCoordinate = UvCoordinate(1f, 0f)),
            Geometry.Vertex(position = Position(-width / 2f, height / 2f, z), normal = normal, uvCoordinate = UvCoordinate(0f, 0f))
        )
        val indices = if (backFace) listOf(0, 2, 1, 0, 3, 2) else listOf(0, 1, 2, 0, 2, 3)
        return NativeMeshData(vertices, indices)
    }

    private fun createPreviewBitmap(
        source: Bitmap,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        rotation: Float,
        viewportAspectRatio: Float
    ): Bitmap {
        val width = 1024
        val aspect = viewportAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        val height = (width / aspect).toInt().coerceIn(256, 2048)
        val preview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        canvas.drawColor(Color.rgb(23, 30, 41))
        val fit = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawScale = fit * scale.coerceIn(.1f, 8f)
        val drawWidth = source.width * drawScale
        val drawHeight = source.height * drawScale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.translate(width * .5f + offsetX * width, height * .5f + offsetY * height)
        canvas.rotate(rotation)
        canvas.drawBitmap(
            source,
            null,
            RectF(-drawWidth / 2f, -drawHeight / 2f, drawWidth / 2f, drawHeight / 2f),
            paint
        )
        canvas.restore()
        return preview
    }

    private fun createMaterials(): JSONArray = JSONArray()
        .put(pbrMaterial("骰子本体", listOf(.94, .92, .86, 1.0), .02, .38))
        .put(pbrMaterial("骰子凹点", listOf(.62, .09, .14, 1.0), 0.0, .34))
        .put(pbrMaterial("双切面", listOf(.71, .66, .56, 1.0), 0.0, .52))
        .put(pbrMaterial("卡牌边缘", listOf(.98, .97, .94, 1.0), 0.0, .42))

    private fun pbrMaterial(name: String, color: List<Double>, metallic: Double, roughness: Double) =
        JSONObject()
            .put("name", name)
            .put("pbrMetallicRoughness", JSONObject()
                .put("baseColorFactor", JSONArray(color))
                .put("metallicFactor", metallic)
                .put("roughnessFactor", roughness))
            .put("doubleSided", true)

    private fun textureMaterial(name: String, textureIndex: Int) =
        JSONObject()
            .put("name", name)
            .put("pbrMetallicRoughness", JSONObject()
                .put("baseColorFactor", JSONArray(listOf(1.0, 1.0, 1.0, 1.0)))
                .put("metallicFactor", 0.0)
                .put("roughnessFactor", .85)
                .put("baseColorTexture", JSONObject().put("index", textureIndex).put("texCoord", 0)))
            .put("doubleSided", true)

    private fun addJpegTexture(
        context: Context,
        assetPath: String,
        bin: BinaryBuilder,
        bufferViews: JSONArray,
        images: JSONArray,
        textures: JSONArray,
        samplers: JSONArray
    ): Int {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        require(bytes.isNotEmpty()) { "卡牌纹理为空：$assetPath" }
        return addTextureBytes(bytes, assetPath.substringAfterLast('/'), bin, bufferViews, images, textures, samplers)
    }

    private fun addTextureBytes(
        bytes: ByteArray,
        name: String,
        bin: BinaryBuilder,
        bufferViews: JSONArray,
        images: JSONArray,
        textures: JSONArray,
        samplers: JSONArray
    ): Int {
        require(bytes.isNotEmpty()) { "纹理数据为空：$name" }
        val viewIndex = bin.addView(bytes, target = null, bufferViews = bufferViews)
        val imageIndex = images.length()
        images.put(JSONObject().put("name", name).put("bufferView", viewIndex).put("mimeType", "image/jpeg"))
        if (samplers.length() == 0) {
            samplers.put(JSONObject()
                .put("magFilter", 9729)
                .put("minFilter", 9987)
                .put("wrapS", 10497)
                .put("wrapT", 10497))
        }
        val textureIndex = textures.length()
        textures.put(JSONObject().put("sampler", 0).put("source", imageIndex))
        return textureIndex
    }

    private fun primitiveJson(
        primitive: Primitive,
        bin: BinaryBuilder,
        bufferViews: JSONArray,
        accessors: JSONArray
    ): JSONObject {
        val mesh = primitive.mesh
        val positions = FloatArray(mesh.vertices.size * 3)
        val normals = FloatArray(mesh.vertices.size * 3)
        val uvs = FloatArray(mesh.vertices.size * 2)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        mesh.vertices.forEachIndexed { index, vertex ->
            val p = vertex.position
            val n = vertex.normal ?: Direction(0f, 0f, 1f)
            val uv = vertex.uvCoordinate ?: UvCoordinate(0f, 0f)
            val offset3 = index * 3
            positions[offset3] = p.x
            positions[offset3 + 1] = p.y
            positions[offset3 + 2] = p.z
            normals[offset3] = n.x
            normals[offset3 + 1] = n.y
            normals[offset3 + 2] = n.z
            val offset2 = index * 2
            uvs[offset2] = uv.x
            uvs[offset2 + 1] = uv.y
            minX = minOf(minX, p.x); minY = minOf(minY, p.y); minZ = minOf(minZ, p.z)
            maxX = maxOf(maxX, p.x); maxY = maxOf(maxY, p.y); maxZ = maxOf(maxZ, p.z)
        }
        val positionAccessor = addFloatAccessor(positions, "VEC3", mesh.vertices.size,
            listOf(minX, minY, minZ), listOf(maxX, maxY, maxZ), 34962, bin, bufferViews, accessors)
        val normalAccessor = addFloatAccessor(normals, "VEC3", mesh.vertices.size, null, null, 34962, bin, bufferViews, accessors)
        val uvAccessor = addFloatAccessor(uvs, "VEC2", mesh.vertices.size, null, null, 34962, bin, bufferViews, accessors)
        val indexAccessor = addIndexAccessor(mesh.indices, bin, bufferViews, accessors)
        return JSONObject()
            .put("attributes", JSONObject()
                .put("POSITION", positionAccessor)
                .put("NORMAL", normalAccessor)
                .put("TEXCOORD_0", uvAccessor))
            .put("indices", indexAccessor)
            .put("material", primitive.material)
            .put("mode", 4)
    }

    private fun addFloatAccessor(
        values: FloatArray,
        type: String,
        count: Int,
        minimum: List<Float>?,
        maximum: List<Float>?,
        target: Int,
        bin: BinaryBuilder,
        bufferViews: JSONArray,
        accessors: JSONArray
    ): Int {
        val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putFloat(it) }
        }.array()
        val view = bin.addView(bytes, target, bufferViews)
        val accessor = JSONObject()
            .put("bufferView", view)
            .put("componentType", 5126)
            .put("count", count)
            .put("type", type)
        minimum?.let { accessor.put("min", JSONArray(it)) }
        maximum?.let { accessor.put("max", JSONArray(it)) }
        val index = accessors.length()
        accessors.put(accessor)
        return index
    }

    private fun addIndexAccessor(
        values: List<Int>,
        bin: BinaryBuilder,
        bufferViews: JSONArray,
        accessors: JSONArray
    ): Int {
        val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putInt(it) }
        }.array()
        val view = bin.addView(bytes, 34963, bufferViews)
        val index = accessors.length()
        accessors.put(JSONObject()
            .put("bufferView", view)
            .put("componentType", 5125)
            .put("count", values.size)
            .put("type", "SCALAR"))
        return index
    }

    private fun eulerQuaternion(rx: Float, ry: Float, rz: Float): JSONArray {
        val x = Math.toRadians(rx.toDouble()) / 2.0
        val y = Math.toRadians(ry.toDouble()) / 2.0
        val z = Math.toRadians(rz.toDouble()) / 2.0
        val sx = kotlin.math.sin(x); val cx = kotlin.math.cos(x)
        val sy = kotlin.math.sin(y); val cy = kotlin.math.cos(y)
        val sz = kotlin.math.sin(z); val cz = kotlin.math.cos(z)
        // q = qz * qy * qx (XYZ Euler order)
        val qx = sx * cy * cz - cx * sy * sz
        val qy = cx * sy * cz + sx * cy * sz
        val qz = cx * cy * sz - sx * sy * cz
        val qw = cx * cy * cz + sx * sy * sz
        return JSONArray(listOf(qx, qy, qz, qw))
    }

    private fun modelName(kind: NativeModelKind): String = when (kind) {
        NativeModelKind.IRREGULAR_DICE -> "IrregularDie"
        NativeModelKind.DOUBLE_CUT_DICE -> "DoubleCutDie"
        NativeModelKind.POKER_CARD -> "QueenOfSpades"
    }

    private fun align(bytes: ByteArray, pad: Byte): ByteArray {
        val extra = (4 - bytes.size % 4) % 4
        if (extra == 0) return bytes
        return bytes + ByteArray(extra) { pad }
    }

    private class BinaryBuilder {
        private val output = ByteArrayOutputStream()
        fun size(): Int = output.size()
        fun toByteArray(): ByteArray = output.toByteArray()
        fun addView(bytes: ByteArray, target: Int?, bufferViews: JSONArray): Int {
            while (output.size() % 4 != 0) output.write(0)
            val offset = output.size()
            output.write(bytes)
            val view = JSONObject()
                .put("buffer", 0)
                .put("byteOffset", offset)
                .put("byteLength", bytes.size)
            target?.let { view.put("target", it) }
            val index = bufferViews.length()
            bufferViews.put(view)
            return index
        }
    }
}
