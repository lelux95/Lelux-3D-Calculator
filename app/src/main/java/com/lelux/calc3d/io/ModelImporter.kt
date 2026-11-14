package com.lelux.calc3d.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Xml
import com.lelux.calc3d.data.FilamentUsage
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import kotlin.math.PI

enum class FileKind { STL, THREE_MF, GCODE, UNKNOWN }

data class PlateInfo(
    val index: Int,
    val seconds: Long,
    val filaments: List<FilamentUsage>,
    val thumbnail: Bitmap?,
)

data class ImportResult(
    val displayName: String,
    val kind: FileKind,
    /** Copia del file nella memoria dell'app */
    val storedFile: File,
    /** Dati letti dallo slicer (un elemento per piatto). Vuoto se il file non e' stato elaborato da uno slicer. */
    val plates: List<PlateInfo>,
    val mesh: Mesh?,
    val thumbnail: Bitmap?,
    /** Tipi e colori dei filamenti dichiarati nel progetto, anche se non elaborato */
    val projectFilaments: List<Pair<String, String>> = emptyList(),
) {
    val isSliced: Boolean get() = plates.isNotEmpty()
    val baseName: String get() = displayName.substringBeforeLast('.').removeSuffix(".gcode")
}

class ImportException(message: String) : Exception(message)

object ModelImporter {

    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "modello"
    }

    /** Copia il file nella memoria dell'app, riconosce il formato e legge i dati. Da chiamare fuori dal thread principale. */
    fun import(context: Context, uri: Uri, modelsDir: File): ImportResult {
        val name = displayName(context, uri)
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw ImportException("Impossibile aprire il file")
        val tmp = File(modelsDir, "import-${System.currentTimeMillis()}")
        input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        if (tmp.length() == 0L) { tmp.delete(); throw ImportException("Il file e' vuoto") }

        val kind = detect(tmp, name)
        val ext = when (kind) {
            FileKind.STL -> "stl"; FileKind.THREE_MF -> "3mf"; FileKind.GCODE -> "gcode"; FileKind.UNKNOWN -> "bin"
        }
        val stored = File(modelsDir, "${java.util.UUID.randomUUID()}.$ext")
        tmp.renameTo(stored)

        return when (kind) {
            FileKind.STL -> {
                val mesh = readStl(stored)
                if (mesh.triangleCount == 0) throw ImportException("Nessun triangolo trovato nell'STL")
                ImportResult(name, kind, stored, emptyList(), mesh, null)
            }
            FileKind.THREE_MF -> read3mf(name, stored)
            FileKind.GCODE -> readGcode(name, stored)
            FileKind.UNKNOWN -> {
                stored.delete()
                throw ImportException("Formato non riconosciuto. Usa file .3mf, .gcode o .stl")
            }
        }
    }

    fun detect(file: File, name: String): FileKind {
        val head = ByteArray(512)
        val n = file.inputStream().use { it.read(head) }
        if (n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return FileKind.THREE_MF
        val lower = name.lowercase()
        if (lower.endsWith(".gcode") || lower.endsWith(".gco") || lower.endsWith(".g") || lower.endsWith(".bgcode")) return FileKind.GCODE
        if (lower.endsWith(".stl")) return FileKind.STL
        if (lower.endsWith(".3mf")) return FileKind.THREE_MF
        // Senza estensione: STL binario se la dimensione torna, STL ASCII se inizia con "solid" e contiene "facet"
        val len = file.length()
        if (len >= 84) {
            val count = ByteBuffer.wrap(head, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            if (84 + count * 50 == len) return FileKind.STL
        }
        val text = String(head, 0, maxOf(n, 0), Charsets.ISO_8859_1)
        if (text.trimStart().startsWith("solid") && text.contains("facet")) return FileKind.STL
        if (text.startsWith(";") || text.contains("\nG1 ") || text.contains("\nG28") || text.contains("\nM104")) return FileKind.GCODE
        return FileKind.UNKNOWN
    }

    // ---------- STL ----------

    fun readStl(file: File): Mesh {
        val len = file.length()
        val isBinary = len >= 84 && run {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(80)
                val b = ByteArray(4); raf.readFully(b)
                val count = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
                84 + count * 50 == len
            }
        }
        return if (isBinary) readBinaryStl(file) else readAsciiStl(file)
    }

    private fun readBinaryStl(file: File): Mesh {
        RandomAccessFile(file, "r").use { raf ->
            val buf = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.getInt(80)
            val out = FloatArray(count * 9)
            var pos = 84
            var o = 0
            for (t in 0 until count) {
                pos += 12 // normale
                for (k in 0 until 9) { out[o++] = buf.getFloat(pos); pos += 4 }
                pos += 2
            }
            return Mesh(out)
        }
    }

    private fun readAsciiStl(file: File): Mesh {
        val list = FloatList()
        file.bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.startsWith("vertex")) {
                    val p = line.split(Regex("\\s+"))
                    if (p.size >= 4) {
                        list.add(p[1].toFloatOrNull() ?: 0f)
                        list.add(p[2].toFloatOrNull() ?: 0f)
                        list.add(p[3].toFloatOrNull() ?: 0f)
                    }
                }
            }
        }
        val arr = list.toArray()
        return Mesh(arr.copyOf(arr.size - arr.size % 9))
    }

    // ---------- 3MF ----------

    private class Obj(val vertices: FloatArray, val triangles: IntArray, val components: List<Comp>)
    private class Comp(val path: String?, val objectId: String, val transform: FloatArray?)

    fun read3mf(name: String, file: File): ImportResult {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList().map { it.name }
            val mainModel = entries.firstOrNull { it.equals("3D/3dmodel.model", true) }
                ?: entries.firstOrNull { it.endsWith(".model", true) }

            // Geometria
            var mesh: Mesh? = null
            if (mainModel != null) {
                runCatching {
                    val cache = HashMap<String, Pair<Map<String, Obj>, List<Comp>>>()
                    fun load(path: String): Pair<Map<String, Obj>, List<Comp>> = cache.getOrPut(path) {
                        val e = zip.getEntry(path.removePrefix("/")) ?: return@getOrPut Pair(emptyMap(), emptyList())
                        zip.getInputStream(e).use { parseModel(it) }
                    }
                    val out = FloatList()
                    val (objects, items) = load(mainModel)
                    fun emit(obj: Obj?, currentPath: String, m: FloatArray?, depth: Int) {
                        if (obj == null || depth > 8) return
                        val v = obj.vertices
                        val t = obj.triangles
                        var i = 0
                        while (i + 2 < t.size) {
                            for (k in 0 until 3) {
                                val vi = t[i + k] * 3
                                if (vi + 2 >= v.size) { out.add(0f); out.add(0f); out.add(0f); continue }
                                val x = v[vi]; val y = v[vi + 1]; val z = v[vi + 2]
                                if (m == null) { out.add(x); out.add(y); out.add(z) }
                                else {
                                    out.add(x * m[0] + y * m[3] + z * m[6] + m[9])
                                    out.add(x * m[1] + y * m[4] + z * m[7] + m[10])
                                    out.add(x * m[2] + y * m[5] + z * m[8] + m[11])
                                }
                            }
                            i += 3
                        }
                        for (c in obj.components) {
                            val path = c.path ?: currentPath
                            val target = load(path).first[c.objectId]
                            emit(target, path, combine(c.transform, m), depth + 1)
                        }
                    }
                    for (item in items) emit(objects[item.objectId], mainModel, item.transform, 0)
                    val arr = out.toArray()
                    if (arr.size >= 9) mesh = Mesh(arr.copyOf(arr.size - arr.size % 9))
                }
            }

            // Miniature dei piatti
            fun png(path: String): Bitmap? = zip.getEntry(path)?.let { e ->
                runCatching { zip.getInputStream(e).use { BitmapFactory.decodeStream(it) } }.getOrNull()
            }
            val generalThumb = png("Metadata/plate_1.png") ?: png("Metadata/thumbnail.png")
                ?: entries.firstOrNull { it.startsWith("Metadata/") && it.endsWith(".png") && !it.contains("_small") && !it.contains("pick") && !it.contains("top") }?.let { png(it) }

            // Dati dello slicer (Bambu Studio / OrcaSlicer)
            val plates = mutableListOf<PlateInfo>()
            zip.getEntry("Metadata/slice_info.config")?.let { e ->
                runCatching {
                    zip.getInputStream(e).use { parseSliceInfo(it) }.forEach { p ->
                        val thumb = png("Metadata/plate_${p.index}.png")
                        if (p.seconds > 0 || p.filaments.any { it.grams > 0 }) plates += p.copy(thumbnail = thumb)
                    }
                }
            }

            // Filamenti dichiarati nel progetto
            val projectFilaments = zip.getEntry("Metadata/project_settings.config")?.let { e ->
                runCatching { zip.getInputStream(e).use { parseProjectFilaments(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull()
            } ?: emptyList()

            if (mesh == null && plates.isEmpty()) throw ImportException("Il 3MF non contiene ne' geometria ne' dati di stampa leggibili")
            return ImportResult(name, FileKind.THREE_MF, file, plates, mesh, generalThumb, projectFilaments)
        }
    }

    /** Matrice 3MF: 12 valori m00 m01 m02 m10 m11 m12 m20 m21 m22 m30 m31 m32 (righe). Restituisce child * parent. */
    private fun combine(child: FloatArray?, parent: FloatArray?): FloatArray? {
        if (child == null) return parent
        if (parent == null) return child
        val r = FloatArray(12)
        for (row in 0 until 4) {
            val a0 = child[row * 3]; val a1 = child[row * 3 + 1]; val a2 = child[row * 3 + 2]
            for (col in 0 until 3) {
                var v = a0 * parent[col] + a1 * parent[3 + col] + a2 * parent[6 + col]
                if (row == 3) v += parent[9 + col]
                r[row * 3 + col] = v
            }
        }
        return r
    }

    private fun parseTransform(s: String?): FloatArray? {
        if (s.isNullOrBlank()) return null
        val p = s.trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
        return if (p.size == 12) p.toFloatArray() else null
    }

    private fun XmlPullParser.attr(local: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfter(':') == local) return getAttributeValue(i)
        }
        return null
    }

    /** Restituisce gli oggetti del file e gli elementi di <build>. */
    private fun parseModel(input: InputStream): Pair<Map<String, Obj>, List<Comp>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val objects = HashMap<String, Obj>()
        val items = mutableListOf<Comp>()
        var currentId: String? = null
        var verts = FloatList(1024)
        var tris = IntList(1024)
        var comps = mutableListOf<Comp>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':')) {
                    "object" -> {
                        currentId = parser.attr("id")
                        verts = FloatList(1024); tris = IntList(1024); comps = mutableListOf()
                    }
                    "vertex" -> {
                        verts.add(parser.attr("x")?.toFloatOrNull() ?: 0f)
                        verts.add(parser.attr("y")?.toFloatOrNull() ?: 0f)
                        verts.add(parser.attr("z")?.toFloatOrNull() ?: 0f)
                    }
                    "triangle" -> {
                        tris.add(parser.attr("v1")?.toIntOrNull() ?: 0)
                        tris.add(parser.attr("v2")?.toIntOrNull() ?: 0)
                        tris.add(parser.attr("v3")?.toIntOrNull() ?: 0)
                    }
                    "component" -> {
                        val oid = parser.attr("objectid")
                        if (oid != null) comps += Comp(parser.attr("path"), oid, parseTransform(parser.attr("transform")))
                    }
                    "item" -> {
                        val oid = parser.attr("objectid")
                        if (oid != null) items += Comp(parser.attr("path"), oid, parseTransform(parser.attr("transform")))
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "object") {
                val id = currentId
                if (id != null) objects[id] = Obj(verts.data.copyOf(verts.size), tris.data.copyOf(tris.size), comps)
                currentId = null
            }
            ev = parser.next()
        }
        return Pair(objects, items)
    }

    private fun parseSliceInfo(input: InputStream): List<PlateInfo> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val plates = mutableListOf<PlateInfo>()
        var inPlate = false
        var index = 0
        var seconds = 0L
        var weight = 0.0
        var fils = mutableListOf<FilamentUsage>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "plate" -> { inPlate = true; index = plates.size + 1; seconds = 0; weight = 0.0; fils = mutableListOf() }
                    "metadata" -> if (inPlate) {
                        val v = parser.attr("value")
                        when (parser.attr("key")) {
                            "index" -> index = v?.toIntOrNull() ?: index
                            "prediction" -> seconds = v?.toDoubleOrNull()?.toLong() ?: 0
                            "weight" -> weight = v?.toDoubleOrNull() ?: 0.0
                        }
                    }
                    "filament" -> if (inPlate) {
                        val g = parser.attr("used_g")?.toDoubleOrNull() ?: 0.0
                        val type = parser.attr("type") ?: "PLA"
                        val color = normalizeColor(parser.attr("color"))
                        if (g > 0) fils += FilamentUsage(type = type, colorHex = color, grams = g)
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && parser.name == "plate") {
                if (fils.isEmpty() && weight > 0) fils += FilamentUsage(grams = weight)
                plates += PlateInfo(index, seconds, fils, null)
                inPlate = false
            }
            ev = parser.next()
        }
        return plates
    }

    private fun parseProjectFilaments(text: String): List<Pair<String, String>> {
        fun arr(key: String): List<String> {
            val m = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)]").find(text) ?: return emptyList()
            return Regex("\"([^\"]*)\"").findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
        }
        val types = arr("filament_type")
        val colors = arr("filament_colour")
        return types.mapIndexed { i, t -> t to normalizeColor(colors.getOrNull(i)) }
    }

    fun normalizeColor(c: String?): String {
        if (c.isNullOrBlank()) return "#FFFFFF"
        var s = c.trim()
        if (!s.startsWith("#")) s = "#$s"
        return when (s.length) {
            7 -> s.uppercase()
            9 -> ("#" + s.substring(1, 7)).uppercase() // RRGGBBAA
            else -> "#FFFFFF"
        }
    }

    // ---------- G-code ----------

    fun parseDuration(s: String): Long {
        var total = 0L
        Regex("(\\d+(?:\\.\\d+)?)\\s*([dhms])").findAll(s).forEach { m ->
            val v = m.groupValues[1].toDouble()
            total += when (m.groupValues[2]) {
                "d" -> (v * 86400).toLong(); "h" -> (v * 3600).toLong(); "m" -> (v * 60).toLong(); else -> v.toLong()
            }
        }
        return total
    }

    fun readGcode(name: String, file: File): ImportResult {
        var seconds = 0L
        var grams: List<Double> = emptyList()
        var meters: List<Double> = emptyList()
        var types: List<String> = emptyList()
        var colors: List<String> = emptyList()
        var thumbB64: StringBuilder? = null
        var bestThumb: String? = null
        var bestThumbSize = 0
        var currentThumbSize = 0

        fun nums(v: String) = v.split(',', ';').mapNotNull { it.trim().toDoubleOrNull() }
        fun strs(v: String) = v.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }

        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                if (!raw.startsWith(";")) return@forEach
                val line = raw.trimEnd()
                val body = line.removePrefix(";").trim()
                if (thumbB64 != null) {
                    if (body.startsWith("thumbnail end") || body.startsWith("thumbnail_JPG end") || body.startsWith("thumbnail_QOI end")) {
                        if (currentThumbSize > bestThumbSize) { bestThumbSize = currentThumbSize; bestThumb = thumbB64.toString() }
                        thumbB64 = null
                    } else thumbB64!!.append(body)
                    return@forEach
                }
                if (body.startsWith("thumbnail begin")) {
                    val dims = Regex("(\\d+)x(\\d+)").find(body)
                    currentThumbSize = dims?.let { it.groupValues[1].toInt() * it.groupValues[2].toInt() } ?: 0
                    thumbB64 = StringBuilder()
                    return@forEach
                }
                val sep = when {
                    body.contains(" = ") -> " = "
                    body.contains(" : ") -> " : "
                    body.contains(":") -> ":"
                    body.contains("=") -> "="
                    else -> null
                }
                val key = (if (sep != null) body.substringBefore(sep) else body).trim().lowercase()
                val value = (if (sep != null) body.substringAfter(sep) else "").trim()
                when {
                    key.startsWith("total estimated time") -> seconds = parseDuration(value)
                    key.startsWith("model printing time") -> {
                        // Bambu: "model printing time: 1h 2m; total estimated time: 1h 5m"
                        val total = body.substringAfter("total estimated time:", "")
                        seconds = if (total.isNotBlank()) parseDuration(total) else maxOf(seconds, parseDuration(value.substringBefore(';')))
                    }
                    key.startsWith("estimated printing time (normal mode)") -> if (seconds == 0L) seconds = parseDuration(value)
                    key == "time" -> if (seconds == 0L) seconds = value.toDoubleOrNull()?.toLong() ?: 0
                    key.startsWith("total filament weight [g]") || key.startsWith("filament used [g]") -> nums(value).let { if (it.isNotEmpty()) grams = it }
                    key.startsWith("filament used [mm]") -> nums(value).let { if (it.isNotEmpty() && meters.isEmpty()) meters = it.map { mm -> mm / 1000.0 } }
                    key.startsWith("total filament length [mm]") -> nums(value).let { if (it.isNotEmpty() && meters.isEmpty()) meters = it.map { mm -> mm / 1000.0 } }
                    key == "filament used" -> nums(value.removeSuffix("m")).let { if (it.isNotEmpty() && meters.isEmpty()) meters = it }
                    key == "filament_type" -> types = strs(value)
                    key == "filament_colour" || key == "filament_color" || key == "extruder_colour" -> if (colors.isEmpty()) colors = strs(value).map { normalizeColor(it) }
                    key == "material" -> if (types.isEmpty()) types = listOf(value)
                }
            }
        }

        if (grams.isEmpty() && meters.isNotEmpty()) {
            // Diametro 1,75 mm: g = m * 1000 * area(mm²) * densita' / 1000
            val areaMm2 = PI * 0.875 * 0.875
            grams = meters.mapIndexed { i, m -> m * areaMm2 * com.lelux.calc3d.data.Defaults.densityFor(types.getOrNull(i) ?: "PLA") }
        }
        if (seconds == 0L && grams.isEmpty()) throw ImportException("Nel G-code non ho trovato tempo di stampa ne' peso del filamento")

        val usages = grams.mapIndexedNotNull { i, g ->
            if (g <= 0.0) null else FilamentUsage(type = types.getOrNull(i) ?: types.firstOrNull() ?: "PLA", colorHex = colors.getOrNull(i) ?: "#FFFFFF", grams = g)
        }.ifEmpty { listOf(FilamentUsage(type = types.firstOrNull() ?: "PLA", grams = 0.0)) }

        val thumb = bestThumb?.let { b ->
            runCatching {
                val bytes = Base64.decode(b, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        return ImportResult(name, FileKind.GCODE, file, listOf(PlateInfo(1, seconds, usages, thumb)), null, thumb)
    }

    /** Carica solo la geometria (per il visualizzatore 3D). */
    fun loadMesh(file: File): Mesh? = runCatching {
        when (file.extension.lowercase()) {
            "stl" -> readStl(file)
            "3mf" -> read3mf(file.name, file).mesh
            else -> null
        }
    }.getOrNull()
}
