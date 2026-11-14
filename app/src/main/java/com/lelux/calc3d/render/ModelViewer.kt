package com.lelux.calc3d.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.lelux.calc3d.io.Mesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.sqrt

/** Visualizzatore 3D: un dito ruota, due dita ingrandiscono, doppio tocco reimposta la vista. */
@SuppressLint("ViewConstructor")
class ModelViewer(context: Context, mesh: Mesh, color: Int, background: Int) : GLSurfaceView(context) {

    private val renderer = ViewerRenderer(mesh, color, background)
    private var lastX = 0f
    private var lastY = 0f
    private var lastTap = 0L
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.zoom = (renderer.zoom / detector.scaleFactor).coerceIn(0.25f, 6f)
            requestRender()
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                val now = System.currentTimeMillis()
                if (now - lastTap < 280) { renderer.reset(); requestRender() }
                lastTap = now
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(idx); lastY = event.getY(idx)
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                val dx = event.x - lastX
                val dy = event.y - lastY
                renderer.yaw += dx * 0.4f
                renderer.pitch = (renderer.pitch + dy * 0.4f).coerceIn(-89f, 89f)
                lastX = event.x; lastY = event.y
                requestRender()
            }
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}

private class ViewerRenderer(mesh: Mesh, private val color: Int, private val background: Int) : GLSurfaceView.Renderer {

    @Volatile var yaw = -35f
    @Volatile var pitch = 25f
    @Volatile var zoom = 1f

    fun reset() { yaw = -35f; pitch = 25f; zoom = 1f }

    private val vertexCount: Int
    private val buffer: FloatBuffer
    private var program = 0
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private var aspect = 1f

    init {
        val t = mesh.tris
        val totalTris = t.size / 9
        // Oltre 800.000 triangoli si mostra un triangolo ogni N, per non esaurire la memoria
        val step = max(1, (totalTris + 799_999) / 800_000)
        val used = (totalTris + step - 1) / step
        val cx = (mesh.minX + mesh.maxX) / 2f
        val cy = (mesh.minY + mesh.maxY) / 2f
        val cz = (mesh.minZ + mesh.maxZ) / 2f
        val ext = max(mesh.sizeX, max(mesh.sizeY, mesh.sizeZ)).toFloat().coerceAtLeast(1e-3f)
        val s = 2f / ext
        val bb = ByteBuffer.allocateDirect(used * 3 * 6 * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        var count = 0
        var tri = 0
        while (tri < totalTris) {
            val i = tri * 9
            val ax = (t[i] - cx) * s; val ay = (t[i + 1] - cy) * s; val az = (t[i + 2] - cz) * s
            val bx = (t[i + 3] - cx) * s; val by = (t[i + 4] - cy) * s; val bz = (t[i + 5] - cz) * s
            val qx = (t[i + 6] - cx) * s; val qy = (t[i + 7] - cy) * s; val qz = (t[i + 8] - cz) * s
            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = qx - ax; val vy = qy - ay; val vz = qz - az
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l > 1e-12f) { nx /= l; ny /= l; nz /= l } else { nx = 0f; ny = 0f; nz = 1f }
            fb.put(ax).put(ay).put(az).put(nx).put(ny).put(nz)
            fb.put(bx).put(by).put(bz).put(nx).put(ny).put(nz)
            fb.put(qx).put(qy).put(qz).put(nx).put(ny).put(nz)
            count += 3
            tri += step
        }
        fb.position(0)
        buffer = fb
        vertexCount = count
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(
            ((background shr 16) and 0xFF) / 255f, ((background shr 8) and 0xFF) / 255f,
            (background and 0xFF) / 255f, 1f
        )
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = buildProgram()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height > 0) width.toFloat() / height else 1f
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0 || vertexCount == 0) return
        Matrix.perspectiveM(proj, 0, 35f, aspect, 0.1f, 100f)
        val dist = 4.2f * zoom
        Matrix.setLookAtM(view, 0, 0f, 0f, dist, 0f, 0f, 0f, 0f, 1f, 0f)
        // Modello con Z verso l'alto: lo si porta con l'alto verso Y, poi si applicano le rotazioni dell'utente
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, -90f, 1f, 0f, 0f)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)

        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aNor = GLES20.glGetAttribLocation(program, "aNor")
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        GLES20.glUniform3f(
            GLES20.glGetUniformLocation(program, "uColor"),
            ((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f
        )
        buffer.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glEnableVertexAttribArray(aPos)
        buffer.position(3)
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glEnableVertexAttribArray(aNor)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNor)
    }

    private fun buildProgram(): Int {
        val vs = """
            attribute vec3 aPos;
            attribute vec3 aNor;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            varying vec3 vNor;
            void main() {
                vNor = (uModel * vec4(aNor, 0.0)).xyz;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform vec3 uColor;
            varying vec3 vNor;
            void main() {
                vec3 n = normalize(vNor);
                vec3 l1 = normalize(vec3(-0.4, 0.7, 0.6));
                vec3 l2 = normalize(vec3(0.6, -0.2, 0.5));
                float d = abs(dot(n, l1)) * 0.75 + abs(dot(n, l2)) * 0.25;
                gl_FragColor = vec4(uColor * (0.28 + 0.72 * d), 1.0);
            }
        """.trimIndent()
        fun compile(type: Int, src: String): Int {
            val sh = GLES20.glCreateShader(type)
            GLES20.glShaderSource(sh, src)
            GLES20.glCompileShader(sh)
            return sh
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }
}
