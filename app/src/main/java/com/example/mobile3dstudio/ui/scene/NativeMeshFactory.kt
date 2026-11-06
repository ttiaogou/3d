package com.example.mobile3dstudio.ui.scene

import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.UvCoordinate
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Plain Kotlin mesh data. No Filament calls are made while these meshes are generated. */
internal data class NativeMeshData(
    val vertices: List<Geometry.Vertex>,
    val indices: List<Int>
) {
    fun isEmpty(): Boolean = vertices.isEmpty() || indices.isEmpty()
}

internal data class DiceMeshSet(
    val body: NativeMeshData,
    val pipOverlay: NativeMeshData,
    val cutFaces: NativeMeshData = NativeMeshData(emptyList(), emptyList()),
    val removedPercent: Float? = null
)

internal object NativeMeshFactory {
    private data class V3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(v: V3) = V3(x + v.x, y + v.y, z + v.z)
        operator fun minus(v: V3) = V3(x - v.x, y - v.y, z - v.z)
        operator fun times(s: Float) = V3(x * s, y * s, z * s)
        fun dot(v: V3) = x * v.x + y * v.y + z * v.z
        fun cross(v: V3) = V3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
        fun length() = sqrt(x * x + y * y + z * z)
        fun normalized(): V3 = (1f / max(length(), 1e-8f)).let { this * it }
        fun asPosition() = Position(x, y, z)
        fun asDirection() = Direction(x, y, z)
    }

    private data class Face(val n: V3, val u: V3, val v: V3, val value: Int)
    private data class TriangleVertex(val p: V3, val n: V3, val u: Float, val v: Float)
    private data class CutResult(val mesh: NativeMeshData, val cap: NativeMeshData)
    private data class DiceBaseSurface(
        val body: NativeMeshData,
        val pips: NativeMeshData,
        val topFacePoints: List<V3>
    )

    private const val HALF = 0.62f
    private const val ROUND_RADIUS = 0.17f
    private const val GRID = 32
    private const val PIP_DEPTH = 0.095f
    private const val MIN_CUT_NORMAL_DOT = 0.92f
    private const val MIN_VISIBLE_CUT_AREA = 0.02f

    private val layouts = mapOf(
        1 to listOf(0f to 0f),
        2 to listOf(-.36f to .36f, .36f to -.36f),
        3 to listOf(-.36f to .36f, 0f to 0f, .36f to -.36f),
        4 to listOf(-.36f to .36f, .36f to .36f, -.36f to -.36f, .36f to -.36f),
        5 to listOf(-.36f to .36f, .36f to .36f, 0f to 0f, -.36f to -.36f, .36f to -.36f),
        6 to listOf(-.36f to .40f, .36f to .40f, -.36f to 0f, .36f to 0f, -.36f to -.40f, .36f to -.40f),
        7 to listOf(-.36f to .40f, 0f to .40f, .36f to .40f, 0f to 0f, -.36f to -.40f, 0f to -.40f, .36f to -.40f),
        8 to listOf(-.36f to .40f, 0f to .40f, .36f to .40f, -.36f to 0f, .36f to 0f, -.36f to -.40f, 0f to -.40f, .36f to -.40f),
        9 to listOf(-.36f to .40f, 0f to .40f, .36f to .40f, -.36f to 0f, 0f to 0f, .36f to 0f, -.36f to -.40f, 0f to -.40f, .36f to -.40f)
    )

    /** Rounded, subtly deformed cube with genuine concave pip bowls in its surface mesh. */
    fun irregularDice(pointCount: Int, seed: Int): DiceMeshSet {
        require(pointCount in 0..9)
        val base = buildDiceSurface(pointCount, seed)
        return DiceMeshSet(base.body, offsetAlongNormals(base.pips, .0015f))
    }

    /**
     * Creates two sequential, independent plane cuts. Both cuts are closed with real cap meshes.
     * The second clipping pass also clips the first cap, so the first cut face remains correctly
     * sealed where the two planes intersect. The +Z pip face is kept wholly inside both cuts.
     */
    fun doubleCutDice(pointCount: Int, seed: Int): DiceMeshSet {
        require(pointCount in 0..9)
        val base = buildDiceSurface(
            pointCount = pointCount,
            seed = seed,
            singlePipFace = true,
            sizePipsByCount = true
        )
        val originalVolume = volume(base.body)
        require(originalVolume > 1e-6f) { "骰子初始网格体积无效" }

        val random = SeededRandom(seed xor 0x51ED270B)
        // A small safety margin keeps numerical volume error inside the requested 35%-90% band.
        val totalRemoval = random.between(.3505f, .8995f)
        val firstShare = random.between(.55f, .72f)
        val firstTarget = 1f - totalRemoval * firstShare
        val finalTarget = 1f - totalRemoval

        var selectedFirst: VolumeCut? = null
        var selectedSecond: VolumeCut? = null
        var selectedPlaneA: V3? = null
        var selectedPlaneB: V3? = null

        repeat(64) {
            if (selectedSecond != null) return@repeat

            // Both planes are sampled independently, including their azimuth and tilt angle.
            // Their normals point down so the complete +Z point face remains on the retained side.
            val planeA = randomCutNormal(random, .90f, .975f)
            val first = runCatching {
                clipAtVolumeFraction(base.body, planeA, firstTarget, base.topFacePoints)
            }.getOrNull() ?: return@repeat

            if (surfaceArea(first.cap) < MIN_VISIBLE_CUT_AREA) return@repeat
            val firstClosed = combine(first.mesh, first.cap)
            val remaining = (volume(firstClosed) / originalVolume).coerceIn(.001f, .999f)
            val relativeFinal = finalTarget / remaining
            if (!relativeFinal.isFinite() || relativeFinal <= 0f || relativeFinal >= 1f) return@repeat

            val planeB = randomCutNormal(random, .96f, .999f)
            // Reject near-parallel directions. Since both normals face downward, dot > .92
            // would mean the plane directions differ by less than about 23 degrees.
            if (planeA.dot(planeB) > MIN_CUT_NORMAL_DOT) return@repeat

            val second = runCatching {
                clipAtVolumeFraction(firstClosed, planeB, relativeFinal, base.topFacePoints)
            }.getOrNull() ?: return@repeat
            if (surfaceArea(second.cap) < MIN_VISIBLE_CUT_AREA) return@repeat

            // The second cut must leave a visible portion of the first cap; this is also the
            // actual geometric operation that trims/seals the first cut at the intersection.
            val firstCapAfterSecondCut = clipWithoutCap(first.cap, planeB, second.planeOffset)
            if (surfaceArea(firstCapAfterSecondCut) < MIN_VISIBLE_CUT_AREA) return@repeat

            val candidateBody = combine(second.mesh, second.cap)
            val candidateRemoved = (1f - volume(candidateBody) / originalVolume) * 100f
            if (!candidateRemoved.isFinite() || candidateRemoved !in 35f..90f) return@repeat

            selectedFirst = first
            selectedSecond = second
            selectedPlaneA = planeA
            selectedPlaneB = planeB
        }

        val first = requireNotNull(selectedFirst) {
            "无法在保留完整点数面的条件下生成两个有效切面，请重新生成"
        }
        val second = requireNotNull(selectedSecond) {
            "无法生成满足35%-90%切除比例的第二个独立切面，请重新生成"
        }
        val planeA = requireNotNull(selectedPlaneA)
        val planeB = requireNotNull(selectedPlaneB)
        require(planeA.dot(planeB) <= MIN_CUT_NORMAL_DOT) { "两个切割平面方向过于接近，请重新生成" }

        // second.mesh already contains the visible, clipped part of the first cap.
        // Add the first cap only to the overlay; the solid body receives only the second cap here.
        val firstCapVisible = clipWithoutCap(first.cap, planeB, second.planeOffset)
        val closedBody = combine(second.mesh, second.cap)
        val capOverlay = combine(
            offsetAlongNormals(firstCapVisible, .0015f),
            offsetAlongNormals(second.cap, .0015f)
        )
        val finalVolume = volume(closedBody)
        val actualRemoved = (1f - finalVolume / originalVolume).coerceIn(0f, 1f) * 100f
        require(actualRemoved in 35f..90f) { "实际切除比例超出35%-90%范围，请重新生成" }

        val clippedPipsA = clipWithoutCap(base.pips, planeA, first.planeOffset)
        val clippedPipsB = clipWithoutCap(clippedPipsA, planeB, second.planeOffset)
        return DiceMeshSet(
            body = closedBody,
            pipOverlay = offsetAlongNormals(clippedPipsB, .0012f),
            cutFaces = capOverlay,
            removedPercent = actualRemoved
        )
    }

    /** Rounded rectangular playing-card slab; the two face images are separate ImageNodes. */
    fun pokerCard(): NativeMeshData {
        val width = .92f
        val height = 1.34f
        val radius = .075f
        val thickness = .018f
        val outline = roundedRectangle(width, height, radius, 8)
        val center = V3(0f, 0f, 0f)
        val vertices = ArrayList<Geometry.Vertex>()
        val indices = ArrayList<Int>()

        fun addTriangle(a: V3, b: V3, c: V3, normal: V3, ua: Pair<Float, Float>, ub: Pair<Float, Float>, uc: Pair<Float, Float>) {
            val start = vertices.size
            vertices += vertex(a, normal, ua.first, ua.second)
            vertices += vertex(b, normal, ub.first, ub.second)
            vertices += vertex(c, normal, uc.first, uc.second)
            indices += listOf(start, start + 1, start + 2)
        }

        val topZ = thickness / 2f
        val bottomZ = -topZ
        val centerUv = .5f to .5f
        for (i in outline.indices) {
            val j = (i + 1) % outline.size
            val a = outline[i]
            val b = outline[j]
            addTriangle(center.copy(z = topZ), a.copy(z = topZ), b.copy(z = topZ), V3(0f, 0f, 1f), centerUv, uv(a, width, height), uv(b, width, height))
            addTriangle(center.copy(z = bottomZ), b.copy(z = bottomZ), a.copy(z = bottomZ), V3(0f, 0f, -1f), centerUv, uv(b, width, height), uv(a, width, height))
            val edge = (b - a).normalized()
            val sideNormal = V3(edge.y, -edge.x, 0f)
            val aTop = a.copy(z = topZ)
            val bTop = b.copy(z = topZ)
            val aBottom = a.copy(z = bottomZ)
            val bBottom = b.copy(z = bottomZ)
            addTriangle(aBottom, bBottom, bTop, sideNormal, 0f to 0f, 1f to 0f, 1f to 1f)
            addTriangle(aBottom, bTop, aTop, sideNormal, 0f to 0f, 1f to 1f, 0f to 1f)
        }
        return NativeMeshData(vertices, indices)
    }

    private fun buildDiceSurface(
        pointCount: Int,
        seed: Int,
        singlePipFace: Boolean = false,
        sizePipsByCount: Boolean = false
    ): DiceBaseSurface {
        val opposite = if (pointCount in 1..6) 7 - pointCount else 6
        val faces = listOf(
            Face(V3(0f, 0f, 1f), V3(1f, 0f, 0f), V3(0f, 1f, 0f), pointCount),
            Face(V3(0f, 0f, -1f), V3(-1f, 0f, 0f), V3(0f, 1f, 0f), if (singlePipFace) 0 else opposite),
            Face(V3(1f, 0f, 0f), V3(0f, 1f, 0f), V3(0f, 0f, 1f), if (singlePipFace) 0 else 3),
            Face(V3(-1f, 0f, 0f), V3(0f, -1f, 0f), V3(0f, 0f, 1f), if (singlePipFace) 0 else 4),
            Face(V3(0f, 1f, 0f), V3(-1f, 0f, 0f), V3(0f, 0f, 1f), if (singlePipFace) 0 else 2),
            Face(V3(0f, -1f, 0f), V3(1f, 0f, 0f), V3(0f, 0f, 1f), if (singlePipFace) 0 else 5)
        )
        val body = MeshWriter()
        val pips = MeshWriter()
        val topFacePoints = ArrayList<V3>()
        val noiseSeed = (seed and 0x7fff).toFloat() / 8192f

        for (face in faces) {
            val centers = layouts[face.value].orEmpty()
            val pipRadius = pipRadiusFor(face.value, sizePipsByCount)
            val count = GRID + 1
            val points = Array(count * count) { V3(0f, 0f, 0f) }
            val normals = Array(count * count) { face.n }
            fun idx(x: Int, y: Int) = y * count + x
            fun coord(i: Int) = -1f + 2f * i / GRID

            for (y in 0..GRID) for (x in 0..GRID) {
                val u = coord(x)
                val v = coord(y)
                points[idx(x, y)] = dicePoint(face, u, v, centers, noiseSeed, pipRadius)
                if (face.n.z > .99f) topFacePoints += points[idx(x, y)]
            }
            for (y in 0..GRID) for (x in 0..GRID) {
                val left = max(0, x - 1)
                val right = min(GRID, x + 1)
                val down = max(0, y - 1)
                val up = min(GRID, y + 1)
                var n = (points[idx(right, y)] - points[idx(left, y)]).cross(points[idx(x, up)] - points[idx(x, down)]).normalized()
                if (n.dot(face.n) < 0f) n = n * -1f
                normals[idx(x, y)] = n
            }

            for (y in 0 until GRID) for (x in 0 until GRID) {
                val u0 = coord(x)
                val u1 = coord(x + 1)
                val v0 = coord(y)
                val v1 = coord(y + 1)
                val centerU = (u0 + u1) * .5f
                val centerV = (v0 + v1) * .5f
                val isWell = pipRadius > 0f && centers.any { (px, py) ->
                    val dx = centerU - px
                    val dy = centerV - py
                    dx * dx + dy * dy < (pipRadius * .74f) * (pipRadius * .74f)
                }
                val a = idx(x, y)
                val b = idx(x + 1, y)
                val c = idx(x + 1, y + 1)
                val d = idx(x, y + 1)
                val verts = listOf(a, b, c, a, c, d)
                for (k in verts.indices step 3) {
                    val ids = verts.subList(k, k + 3)
                    var tri = ids.map { id ->
                        val px = id % count
                        val py = id / count
                        TriangleVertex(points[id], normals[id], coord(px) * .5f + .5f, coord(py) * .5f + .5f)
                    }
                    if ((tri[1].p - tri[0].p).cross(tri[2].p - tri[0].p).dot(face.n) < 0f) tri = listOf(tri[0], tri[2], tri[1])
                    body.triangle(tri[0], tri[1], tri[2])
                    if (isWell) pips.triangle(tri[0], tri[1], tri[2])
                }
            }
        }
        return DiceBaseSurface(body.build(), pips.build(), topFacePoints)
    }

    private fun pipRadiusFor(pointCount: Int, sizeByCount: Boolean): Float {
        if (pointCount !in 1..9) return 0f
        if (!sizeByCount) return if (pointCount >= 7) .155f else .175f
        // Fewer points get visibly larger bowls; the nine-point pattern keeps enough material
        // between neighboring wells so the concave shapes remain separate.
        return when (pointCount) {
            1 -> .245f
            2 -> .215f
            3 -> .195f
            4 -> .180f
            5 -> .165f
            6 -> .155f
            7 -> .148f
            8 -> .141f
            else -> .135f
        }
    }

    private fun dicePoint(
        face: Face,
        u: Float,
        v: Float,
        centers: List<Pair<Float, Float>>,
        noiseSeed: Float,
        pipRadius: Float
    ): V3 {
        val raw = face.n + face.u * u + face.v * v
        val core = V3(
            raw.x.coerceIn(-1f + ROUND_RADIUS, 1f - ROUND_RADIUS),
            raw.y.coerceIn(-1f + ROUND_RADIUS, 1f - ROUND_RADIUS),
            raw.z.coerceIn(-1f + ROUND_RADIUS, 1f - ROUND_RADIUS)
        )
        val delta = raw - core
        val roundedNormal = delta.normalized()
        var point = core + roundedNormal * ROUND_RADIUS
        val nearest = centers.minOfOrNull { (px, py) ->
            val dx = u - px
            val dy = v - py
            sqrt(dx * dx + dy * dy)
        } ?: Float.POSITIVE_INFINITY
        if (pipRadius > 0f && nearest < pipRadius) {
            val t = (nearest / pipRadius).coerceIn(0f, 1f)
            val profile = (1f - t * t).coerceAtLeast(0f).let { it * it }
            val rim = .007f * kotlin.math.exp(-(((t - .94f) / .06f) * ((t - .94f) / .06f)))
            point -= face.n * (PIP_DEPTH * profile - rim)
        }
        // A low-amplitude deterministic deformation gives the first die a slightly irregular
        // silhouette without breaking the watertight face seams.
        val wave = .040f * sin(point.x * 6.1f + noiseSeed) *
            sin(point.y * 5.3f + noiseSeed * 1.7f) *
            sin(point.z * 5.7f - noiseSeed)
        point += roundedNormal * wave
        return point * HALF
    }

    private fun randomCutNormal(random: SeededRandom, minAbsZ: Float, maxAbsZ: Float): V3 {
        // Sampling the azimuth inside this function makes the second plane independent of the
        // first plane's direction; the random |Z| component independently controls its tilt.
        val azimuth = random.between(0f, (2f * PI).toFloat())
        val z = -random.between(minAbsZ, maxAbsZ)
        return V3(
            cos(azimuth) * sqrt(1f - z * z),
            sin(azimuth) * sqrt(1f - z * z),
            z
        ).normalized()
    }

    private fun clipAtVolumeFraction(
        mesh: NativeMeshData,
        normal: V3,
        target: Float,
        preservedTopFace: List<V3>
    ): VolumeCut {
        val points = mesh.vertices.map { v -> V3(v.position.x, v.position.y, v.position.z) }
        val dots = points.map { it.dot(normal) }
        val topLimit = (preservedTopFace.maxOfOrNull { it.dot(normal) } ?: dots.minOrNull() ?: -1f) + .0005f
        var low = max(dots.minOrNull() ?: -1f, topLimit)
        var high = dots.maxOrNull() ?: 1f
        require(low < high) { "切割方向无法保留完整点数面" }

        val sourceVolume = max(volume(mesh), 1e-8f)
        val minimumCut = clipMesh(mesh, normal, low, buildCap = true)
        val minimumRetained = volume(combine(minimumCut.mesh, minimumCut.cap)) / sourceVolume
        require(minimumRetained <= target + .0015f) {
            "当前切割方向无法在保留完整点数面的同时达到目标体积"
        }

        repeat(20) {
            val mid = (low + high) * .5f
            val clipped = clipMesh(mesh, normal, mid, buildCap = true)
            val fraction = (volume(combine(clipped.mesh, clipped.cap)) / sourceVolume).coerceIn(0f, 1f)
            if (fraction < target) low = mid else high = mid
        }
        val offset = (low + high) * .5f
        val final = clipMesh(mesh, normal, offset, buildCap = true)
        return VolumeCut(final.mesh, final.cap, offset)
    }

    private data class VolumeCut(val mesh: NativeMeshData, val cap: NativeMeshData, val planeOffset: Float)

    private fun clipWithoutCap(mesh: NativeMeshData, normal: V3, offset: Float): NativeMeshData =
        clipMesh(mesh, normal, offset, buildCap = false).mesh

    private fun clipMesh(mesh: NativeMeshData, normal: V3, offset: Float, buildCap: Boolean): CutResult {
        if (mesh.isEmpty()) return CutResult(mesh, NativeMeshData(emptyList(), emptyList()))
        val out = MeshWriter()
        val boundary = LinkedHashMap<String, V3>()
        val eps = 1e-6f
        for (i in mesh.indices.indices step 3) {
            val tri = listOf(
                readVertex(mesh, mesh.indices[i]),
                readVertex(mesh, mesh.indices[i + 1]),
                readVertex(mesh, mesh.indices[i + 2])
            )
            val distances = tri.map { it.p.dot(normal) - offset }
            val polygon = ArrayList<TriangleVertex>(4)
            val crossings = ArrayList<TriangleVertex>(2)
            for (edge in 0..2) {
                val current = tri[edge]
                val next = tri[(edge + 1) % 3]
                val dc = distances[edge]
                val dn = distances[(edge + 1) % 3]
                val currentInside = dc <= eps
                val nextInside = dn <= eps
                if (currentInside && nextInside) {
                    polygon += next
                } else if (currentInside && !nextInside) {
                    val hit = interpolate(current, next, dc / (dc - dn))
                    polygon += hit
                    crossings += hit
                } else if (!currentInside && nextInside) {
                    val hit = interpolate(current, next, dc / (dc - dn))
                    polygon += hit
                    polygon += next
                    crossings += hit
                }
            }
            if (buildCap && crossings.size == 2) {
                crossings.forEach { v ->
                    val key = "${(v.p.x * 100000).toInt()},${(v.p.y * 100000).toInt()},${(v.p.z * 100000).toInt()}"
                    boundary.putIfAbsent(key, v.p)
                }
            }
            if (polygon.size >= 3) {
                for (k in 1 until polygon.lastIndex) out.triangle(polygon[0], polygon[k], polygon[k + 1])
            }
        }
        val cap = if (buildCap) buildCap(boundary.values.toList(), normal) else NativeMeshData(emptyList(), emptyList())
        return CutResult(out.build(), cap)
    }

    private fun buildCap(points: List<V3>, normal: V3): NativeMeshData {
        if (points.size < 3) return NativeMeshData(emptyList(), emptyList())
        val center = points.reduce { acc, p -> acc + p } * (1f / points.size)
        val reference = if (kotlin.math.abs(normal.z) < .85f) V3(0f, 0f, 1f) else V3(0f, 1f, 0f)
        val u = reference.cross(normal).normalized()
        val v = normal.cross(u).normalized()
        val sorted = points.sortedBy { p -> atan2((p - center).dot(v), (p - center).dot(u)) }
        val writer = MeshWriter()
        val centerVertex = TriangleVertex(center, normal, .5f, .5f)
        for (i in sorted.indices) {
            val a = sorted[i]
            val b = sorted[(i + 1) % sorted.size]
            val va = TriangleVertex(a, normal, .5f + (a - center).dot(u), .5f + (a - center).dot(v))
            val vb = TriangleVertex(b, normal, .5f + (b - center).dot(u), .5f + (b - center).dot(v))
            writer.triangle(centerVertex, va, vb)
        }
        return writer.build()
    }

    private fun offsetAlongNormals(mesh: NativeMeshData, amount: Float): NativeMeshData {
        if (mesh.isEmpty()) return mesh
        val verts = mesh.vertices.map { v ->
            val n = V3(v.normal?.x ?: 0f, v.normal?.y ?: 0f, v.normal?.z ?: 1f).normalized()
            v.copy(position = Position(v.position.x + n.x * amount, v.position.y + n.y * amount, v.position.z + n.z * amount))
        }
        return NativeMeshData(verts, mesh.indices)
    }

    private fun combine(a: NativeMeshData, b: NativeMeshData): NativeMeshData {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return NativeMeshData(a.vertices + b.vertices, a.indices + b.indices.map { it + a.vertices.size })
    }

    private fun volume(mesh: NativeMeshData): Float {
        var sixVolume = 0f
        for (i in mesh.indices.indices step 3) {
            val a = point(mesh, mesh.indices[i])
            val b = point(mesh, mesh.indices[i + 1])
            val c = point(mesh, mesh.indices[i + 2])
            sixVolume += a.dot(b.cross(c))
        }
        return kotlin.math.abs(sixVolume) / 6f
    }

    private fun surfaceArea(mesh: NativeMeshData): Float {
        var area = 0f
        for (i in mesh.indices.indices step 3) {
            val a = point(mesh, mesh.indices[i])
            val b = point(mesh, mesh.indices[i + 1])
            val c = point(mesh, mesh.indices[i + 2])
            area += (b - a).cross(c - a).length() * .5f
        }
        return area
    }

    private fun point(mesh: NativeMeshData, index: Int): V3 {
        val p = mesh.vertices[index].position
        return V3(p.x, p.y, p.z)
    }

    private fun readVertex(mesh: NativeMeshData, index: Int): TriangleVertex {
        val v = mesh.vertices[index]
        val n = v.normal ?: Direction(0f, 0f, 1f)
        val uv = v.uvCoordinate ?: UvCoordinate(0f, 0f)
        return TriangleVertex(V3(v.position.x, v.position.y, v.position.z), V3(n.x, n.y, n.z), uv.x, uv.y)
    }

    private fun interpolate(a: TriangleVertex, b: TriangleVertex, t: Float): TriangleVertex {
        val f = t.coerceIn(0f, 1f)
        return TriangleVertex(
            a.p + (b.p - a.p) * f,
            (a.n + (b.n - a.n) * f).normalized(),
            a.u + (b.u - a.u) * f,
            a.v + (b.v - a.v) * f
        )
    }

    private fun roundedRectangle(width: Float, height: Float, radius: Float, segments: Int): List<V3> {
        val result = ArrayList<V3>(segments * 4 + 4)
        val corners = listOf(
            Triple(width / 2f - radius, height / 2f - radius, 0f),
            Triple(-width / 2f + radius, height / 2f - radius, 90f),
            Triple(-width / 2f + radius, -height / 2f + radius, 180f),
            Triple(width / 2f - radius, -height / 2f + radius, 270f)
        )
        for ((cx, cy, startDegrees) in corners) {
            for (i in 0..segments) {
                val angle = (startDegrees + i * 90f / segments) * (PI / 180.0).toFloat()
                result += V3(cx + cos(angle) * radius, cy + sin(angle) * radius, 0f)
            }
        }
        return result
    }

    private fun uv(p: V3, width: Float, height: Float) = (p.x / width + .5f) to (p.y / height + .5f)

    private fun vertex(v: V3, n: V3, u: Float, w: Float) = Geometry.Vertex(
        position = v.asPosition(),
        normal = n.normalized().asDirection(),
        uvCoordinate = UvCoordinate(u, w)
    )

    private class MeshWriter {
        private val vertices = ArrayList<Geometry.Vertex>()
        private val indices = ArrayList<Int>()
        fun triangle(a: TriangleVertex, b: TriangleVertex, c: TriangleVertex) {
            val base = vertices.size
            vertices += vertex(a.p, a.n, a.u, a.v)
            vertices += vertex(b.p, b.n, b.u, b.v)
            vertices += vertex(c.p, c.n, c.u, c.v)
            indices += listOf(base, base + 1, base + 2)
        }
        fun build() = NativeMeshData(vertices, indices)
    }

    private class SeededRandom(seed: Int) {
        private var state = if (seed == 0) 0x6D2B79F5 else seed
        private fun nextInt(): Int {
            var x = state
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            state = x
            return x
        }
        fun unit(): Float = (nextInt().toLong() and 0x7fffffffL).toFloat() / Int.MAX_VALUE.toFloat()
        fun between(minimum: Float, maximum: Float) = minimum + (maximum - minimum) * unit()
    }
}
