package com.lelux.calc3d.io

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Mesh a triangoli: 9 float per triangolo (3 vertici x,y,z), in millimetri. */
class Mesh(val tris: FloatArray) {
    val triangleCount: Int get() = tris.size / 9

    var minX = 0f; var minY = 0f; var minZ = 0f
    var maxX = 0f; var maxY = 0f; var maxZ = 0f
    /** Volume in mm³ (valore assoluto della somma dei tetraedri con segno) */
    var volume = 0.0
    /** Superficie in mm² */
    var area = 0.0

    init { computeStats() }

    private fun computeStats() {
        if (tris.isEmpty()) return
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var z0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        var vol = 0.0
        var ar = 0.0
        var i = 0
        while (i < tris.size) {
            val ax = tris[i].toDouble(); val ay = tris[i + 1].toDouble(); val az = tris[i + 2].toDouble()
            val bx = tris[i + 3].toDouble(); val by = tris[i + 4].toDouble(); val bz = tris[i + 5].toDouble()
            val cx = tris[i + 6].toDouble(); val cy = tris[i + 7].toDouble(); val cz = tris[i + 8].toDouble()
            vol += (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6.0
            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = cx - ax; val vy = cy - ay; val vz = cz - az
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            ar += sqrt(nx * nx + ny * ny + nz * nz) / 2.0
            for (k in 0 until 3) {
                val x = tris[i + k * 3]; val y = tris[i + k * 3 + 1]; val z = tris[i + k * 3 + 2]
                x0 = min(x0, x); y0 = min(y0, y); z0 = min(z0, z)
                x1 = max(x1, x); y1 = max(y1, y); z1 = max(z1, z)
            }
            i += 9
        }
        minX = x0; minY = y0; minZ = z0; maxX = x1; maxY = y1; maxZ = z1
        volume = abs(vol)
        area = ar
    }

    val sizeX: Double get() = (maxX - minX).toDouble()
    val sizeY: Double get() = (maxY - minY).toDouble()
    val sizeZ: Double get() = (maxZ - minZ).toDouble()
}

/** Array di float che cresce da solo, per leggere file di cui non si conosce la dimensione. */
class FloatList(initial: Int = 1 shl 16) {
    var data = FloatArray(initial)
    var size = 0
    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }
    fun toArray(): FloatArray = data.copyOf(size)
}

class IntList(initial: Int = 1 shl 16) {
    var data = IntArray(initial)
    var size = 0
    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }
}
