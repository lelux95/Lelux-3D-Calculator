package com.lelux.calc3d.render

import android.graphics.Bitmap
import com.lelux.calc3d.io.Mesh
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Disegna una miniatura del modello (vista isometrica, ombreggiatura piatta) senza OpenGL. */
object SoftRenderer {

    fun render(mesh: Mesh, size: Int = 512, colorArgb: Int = 0xFFFF8A3D.toInt()): Bitmap {
        val w = size; val h = size
        val pixels = IntArray(w * h) // trasparente
        val depth = FloatArray(w * h) { Float.MAX_VALUE }

        val cx = (mesh.minX + mesh.maxX) / 2f
        val cy = (mesh.minY + mesh.maxY) / 2f
        val cz = (mesh.minZ + mesh.maxZ) / 2f

        // Rotazione di -35° attorno a Z, poi camera rialzata di 30° (vista frontale dall'alto)
        val yaw = Math.toRadians(-35.0)
        val elev = Math.toRadians(30.0)
        val cyw = cos(yaw).toFloat(); val syw = sin(yaw).toFloat()
        val ce = cos(elev).toFloat(); val se = sin(elev).toFloat()

        val t = mesh.tris
        val n = t.size / 3
        val px = FloatArray(n); val py = FloatArray(n); val pz = FloatArray(n)
        var minSx = Float.MAX_VALUE; var maxSx = -Float.MAX_VALUE
        var minSy = Float.MAX_VALUE; var maxSy = -Float.MAX_VALUE
        for (i in 0 until n) {
            val x = t[i * 3] - cx; val y = t[i * 3 + 1] - cy; val z = t[i * 3 + 2] - cz
            val x1 = x * cyw - y * syw
            val y1 = x * syw + y * cyw
            val up = y1 * se + z * ce
            val dep = y1 * ce - z * se
            px[i] = x1; py[i] = up; pz[i] = dep // schermo: x a destra, y in alto, profondita' (piu' piccola = piu' vicina)
            minSx = min(minSx, x1); maxSx = max(maxSx, x1)
            minSy = min(minSy, up); maxSy = max(maxSy, up)
        }
        val extent = max(maxSx - minSx, maxSy - minSy).coerceAtLeast(1e-3f)
        val scale = (size * 0.86f) / extent
        val offX = w / 2f - (minSx + maxSx) / 2f * scale
        val offY = h / 2f + (minSy + maxSy) / 2f * scale

        val baseR = (colorArgb shr 16) and 0xFF
        val baseG = (colorArgb shr 8) and 0xFF
        val baseB = colorArgb and 0xFF
        // Luce in coordinate vista
        val lx = -0.35f; val ly = 0.75f; val lz = -0.55f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)

        var tri = 0
        while (tri + 2 < n) {
            val a = tri; val b = tri + 1; val c = tri + 2
            tri += 3
            // Normale in coordinate vista (x, y=su, z=profondita')
            val ux = px[b] - px[a]; val uy = py[b] - py[a]; val uz = pz[b] - pz[a]
            val vx = px[c] - px[a]; val vy = py[c] - py[a]; val vz = pz[c] - pz[a]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val nl = sqrt(nx * nx + ny * ny + nz * nz)
            if (nl < 1e-12f) continue
            // Il cambio di coordinate e' speculare: si inverte il prodotto vettoriale per avere la normale esterna
            nx = -nx / nl; ny = -ny / nl; nz = -nz / nl
            var lambert = (nx * lx + ny * ly + nz * lz) / ll
            if (lambert < 0) lambert = -lambert * 0.6f // faccia girata: illumina comunque, piu' scura
            val shade = 0.35f + 0.65f * lambert
            val r = (baseR * shade).toInt().coerceIn(0, 255)
            val g = (baseG * shade).toInt().coerceIn(0, 255)
            val bl = (baseB * shade).toInt().coerceIn(0, 255)
            val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl

            val x0 = px[a] * scale + offX; val y0 = offY - py[a] * scale
            val x1 = px[b] * scale + offX; val y1 = offY - py[b] * scale
            val x2 = px[c] * scale + offX; val y2 = offY - py[c] * scale
            val z0 = pz[a]; val z1 = pz[b]; val z2 = pz[c]
            val minX = max(0, min(x0, min(x1, x2)).toInt())
            val maxX = min(w - 1, max(x0, max(x1, x2)).toInt() + 1)
            val minY = max(0, min(y0, min(y1, y2)).toInt())
            val maxY = min(h - 1, max(y0, max(y1, y2)).toInt() + 1)
            if (minX > maxX || minY > maxY) continue
            val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if (area > -1e-6f && area < 1e-6f) continue
            val inv = 1f / area
            for (yy in minY..maxY) {
                val fy = yy + 0.5f
                for (xx in minX..maxX) {
                    val fx = xx + 0.5f
                    val w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) * inv
                    val w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) * inv
                    val w2 = 1f - w0 - w1
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue
                    val z = w0 * z0 + w1 * z1 + w2 * z2
                    val idx = yy * w + xx
                    // La camera guarda verso +profondita': piu' piccolo = piu' vicino
                    if (z < depth[idx]) { depth[idx] = z; pixels[idx] = color }
                }
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
