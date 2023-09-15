/*
* Copyright (c) 2022 Google LLC
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.example.lowlatencysample.ui


import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import androidx.annotation.WorkerThread
import androidx.graphics.lowlatency.BufferInfo
import androidx.graphics.lowlatency.GLFrontBufferedRenderer
import androidx.graphics.opengl.egl.EGLManager
import androidx.input.motionprediction.MotionEventPredictor
import com.example.lowlatencysample.DrawingManager
import com.example.lowlatencysample.data.toFloatArray
import com.example.lowlatencysample.data.toFloatArrayLineClean
import com.example.lowlatencysample.brush.Brush.Companion.DATA_STRUCTURE_SIZE
import com.example.lowlatencysample.brush.Brush.Companion.EVENT_TYPE
import com.example.lowlatencysample.brush.Brush.Companion.IS_PREDICTED_EVENT
import com.example.lowlatencysample.brush.Brush.Companion.IS_USER_EVENT
import com.example.lowlatencysample.brush.Brush.Companion.PRESSURE
import com.example.lowlatencysample.brush.Brush.Companion.X1_INDEX
import com.example.lowlatencysample.brush.Brush.Companion.X2_INDEX
import com.example.lowlatencysample.brush.Brush.Companion.Y1_INDEX
import com.example.lowlatencysample.brush.Brush.Companion.Y2_INDEX
import com.example.lowlatencysample.brush.GLBrush
import com.example.lowlatencysample.data.getIntersectSublines
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * LowLatencyRenderer<Brush>
 * Brush: Provide the type of brush you want to use with the render
 */
interface LowLatencyRenderer<Brush> {
    val onTouchListener: View.OnTouchListener
    fun attachSurfaceView(surfaceView: SurfaceView)
    fun release()
    fun updateBrush(brush: Brush)
}

class LowLatencyRendererOpenGL(
    private var brushRenderer: GLBrush,
    private val drawingManager: DrawingManager
) : LowLatencyRenderer<GLBrush>, GLFrontBufferedRenderer.Callback<FloatArray> {
    private val mvpMatrix = FloatArray(16)
    private val projection = FloatArray(16)

    private var frontBufferRenderer: GLFrontBufferedRenderer<FloatArray>? = null

    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var currentX: Float = 0f
    private var currentY: Float = 0f

    private var motionEventPredictor: MotionEventPredictor? = null

    private val surfaceScissor = SurfaceScissor()

    override fun updateBrush(brush: GLBrush) {
        this.brushRenderer.release()
        this.brushRenderer = brush
    }

    @WorkerThread // GLThread
    private fun obtainRenderer(): GLBrush =
        if (brushRenderer.isInitialized) {
            brushRenderer
        } else {
            brushRenderer
                .apply {
                    initialize()
                    val brushsize = ceil(this.maxSize).toInt()
                    surfaceScissor.maxBrushSize = brushsize
                }
        }

    private var currentLine = mutableListOf<FloatArray>()

    private val defaultStrokeColor = Color.WHITE

    override fun onDrawFrontBufferedLayer(
        eglManager: EGLManager,
        width: Int,
        height: Int,
        bufferInfo: BufferInfo,
        transform: FloatArray,
        param: FloatArray
    ) {
        GLES20.glViewport(0, 0, bufferInfo.width, bufferInfo.height)
        // Map Android coordinates to GL coordinates
        Matrix.orthoM(
            mvpMatrix,
            0,
            0f,
            bufferInfo.width.toFloat(),
            0f,
            bufferInfo.height.toFloat(),
            -1f,
            1f
        )

        Matrix.multiplyMM(projection, 0, mvpMatrix, 0, transform, 0)


        if (drawingManager.isSurfaceScissorEnabled && !surfaceScissor.isEmpty) {
            // Enable scissor test.
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)

            val damagedArea = RectF(surfaceScissor.scissorBox)

            var appyTransformation = false
            var angle = 0f
            val width = bufferInfo.width
            val height = bufferInfo.height

            when (drawingManager.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    when (drawingManager.displayRotation) {
                        Surface.ROTATION_0 -> {
                            angle = 0f
                        }

                        Surface.ROTATION_180 -> {
                            appyTransformation = true
                            angle = 180f

                        }

                        else -> {
                            throw Exception(
                                "Invalid display orientation. debug info(portrait: ${drawingManager.orientation == Configuration.ORIENTATION_PORTRAIT} / displayOrientation: ${drawingManager.displayRotation} / displayRotation: ${drawingManager.displayRotation} ${
                                    getAngle(
                                        drawingManager.displayRotation
                                    )
                                })"
                            )
                        }
                    }
                }

                Configuration.ORIENTATION_LANDSCAPE -> {
                    appyTransformation = true
                    angle = if (drawingManager.displayRotation == Surface.ROTATION_90) {
                        90f
                    } else {
                        if (drawingManager.displayRotation == Surface.ROTATION_270) {
                            -90f
                        } else {
                            90f
                        }
                    }
                }

                else -> Unit
            }

            val x = (width / 2f)
            val y = (height / 2f)

            if (appyTransformation) { // apply some transformation depending on the orientation of the device
                val matrixTransform = android.graphics.Matrix()
                matrixTransform.setRotate(angle, x, y)
                matrixTransform.mapRect(damagedArea)

                val matrixTransform2 = android.graphics.Matrix()
                val move = if (drawingManager.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    0f
                } else if (drawingManager.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (drawingManager.displayRotation == Surface.ROTATION_270) {
                        (height - width) / 2f
                    } else if (drawingManager.displayRotation == Surface.ROTATION_90) {
                        (width - height) / 2f
                    } else {
                        0f
                    }
                } else {
                    0f
                }

                matrixTransform2.setTranslate(move, move)
                matrixTransform2.mapRect(damagedArea)
            } else if (drawingManager.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                val matrixTransform = android.graphics.Matrix()
                matrixTransform.setRotate(angle, x, y)
                matrixTransform.mapRect(damagedArea)
            }

            val rect = Rect()
            rect.left = damagedArea.left.toInt()
            rect.top = damagedArea.top.toInt()
            rect.right = damagedArea.right.toInt()
            rect.bottom = damagedArea.bottom.toInt()


            GLES20.glScissor(
                rect.left,
                rect.top,
                rect.width(),
                rect.height(),
            )

            if (drawingManager.isDebugColorEnabled) {
                //Clear the color buffer with RED
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
            } else {
                //Clear the color buffer with BLACK
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            }
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // redraw all the lines within the work area
            val scissorRectInflated = Rect(surfaceScissor.scissorBox).apply {
//                this.left -= surfaceScissor.maxBrushSize
//                this.top -= surfaceScissor.maxBrushSize
//                this.right += surfaceScissor.maxBrushSize
//                this.bottom += surfaceScissor.maxBrushSize
            }

            val rx = scissorRectInflated.left.toFloat()
            val ry = scissorRectInflated.top.toFloat()
            val rw = scissorRectInflated.width().toFloat()
            val rh = scissorRectInflated.height().toFloat()
            val color = if (drawingManager.isDebugColorEnabled) {
                Color.YELLOW
            } else {
                defaultStrokeColor
            }
            for (line in drawingManager.getLines()) {
                val sublines = line.getIntersectSublines(rx, ry, rw, rh)
                if (sublines.isNotEmpty()) {
                    for(subline in sublines) {
                        obtainRenderer().drawLines(
                            projection,
                            subline,
                            color
                        )
                    }
                }
            }

            val currentLineExtended = currentLine.toMutableList()
            currentLineExtended.add(param)
            val previousStrokesColor = if (drawingManager.isDebugColorEnabled) {
                Color.YELLOW
            } else {
                defaultStrokeColor
            }

            val currentSublines = currentLineExtended.toFloatArray().getIntersectSublines(rx, ry, rw, rh)
            if (currentSublines.isNotEmpty()) {
                for(subline in currentSublines) {
                    obtainRenderer().drawLines(
                        projection, subline,
                        previousStrokesColor
                    )
                }
            }

            // Disable the scissors
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        } else {
            // render current line
            obtainRenderer().drawLines(projection, param, defaultStrokeColor)
        }

        // we want to keep only the user event for the current line as well.
        if (param[EVENT_TYPE] == IS_USER_EVENT) {
            currentLine.add(param)
        }

    }

    fun getAngle(rotation: Int): Float {
        return when (rotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
    }

    override fun onDrawMultiBufferedLayer(
        eglManager: EGLManager,
        width: Int,
        height: Int,
        bufferInfo: BufferInfo,
        transform: FloatArray,
        params: Collection<FloatArray>
    ) {
        currentLine.clear()
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glViewport(0, 0, bufferInfo.width, bufferInfo.height)
        Matrix.orthoM(
            mvpMatrix,
            0,
            0f,
            bufferInfo.width.toFloat(),
            0f,
            bufferInfo.height.toFloat(),
            -1f,
            1f
        )

        Matrix.multiplyMM(projection, 0, mvpMatrix, 0, transform, 0)

        val fla = params.toFloatArrayLineClean()
        drawingManager.saveLines(fla)

        for (line in drawingManager.getLines()) {
            obtainRenderer().drawLines(projection, line, defaultStrokeColor)
        }
    }

    override fun attachSurfaceView(surfaceView: SurfaceView) {
        frontBufferRenderer = GLFrontBufferedRenderer(surfaceView, this, drawingManager.glRenderer)
        motionEventPredictor = MotionEventPredictor.newInstance(surfaceView)
    }

    override fun release() {
        frontBufferRenderer?.release(true)
        {
            obtainRenderer().release()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override val onTouchListener = View.OnTouchListener { view, event ->
        motionEventPredictor?.record(event)

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                // Ask that the input system not batch MotionEvents
                // but instead deliver them as soon as they're available
                view.requestUnbufferedDispatch(event)
                surfaceScissor.reset()
                currentX = event.x
                currentY = event.y

                val line = FloatArray(DATA_STRUCTURE_SIZE).apply {
                    this[X1_INDEX] = currentX
                    this[Y1_INDEX] = currentY
                    this[X2_INDEX] = currentX
                    this[Y2_INDEX] = currentY
                    // Helps differentiate between User and Predicted events
                    this[EVENT_TYPE] = IS_USER_EVENT
                    this[PRESSURE] = event.pressure
                }

                // Send the short line to front buffered layer: fast rendering
                frontBufferRenderer?.renderFrontBufferedLayer(line)
            }

            MotionEvent.ACTION_MOVE -> {
                surfaceScissor.reset()
                if (previousX != 0f && previousY != 0f) {
                    surfaceScissor.addPoint(previousX, previousY)
                }
                // add new points to surfaceScissor
                surfaceScissor.addPoint(currentX, currentY)

                previousX = currentX
                previousY = currentY
                currentX = event.x
                currentY = event.y

                val line = FloatArray(DATA_STRUCTURE_SIZE).apply {
                    this[X1_INDEX] = previousX
                    this[Y1_INDEX] = previousY
                    this[X2_INDEX] = currentX
                    this[Y2_INDEX] = currentY
                    // Helps differentiate between User and Predicted events
                    this[EVENT_TYPE] = IS_USER_EVENT
                    this[PRESSURE] = event.pressure
                }

                // add new points to surfaceScissor
                surfaceScissor.addPoint(currentX, currentY)

                // Send the short line to front buffered layer: fast rendering
                frontBufferRenderer?.renderFrontBufferedLayer(line)

                if (drawingManager.isPredictionEnabled) {
                    val predictedMotionEvent = motionEventPredictor?.predict()
                    if (predictedMotionEvent != null) {
                        val predictedLine = FloatArray(DATA_STRUCTURE_SIZE).apply {
                            this[X1_INDEX] = currentX
                            this[Y1_INDEX] = currentY
                            this[X2_INDEX] = predictedMotionEvent.x
                            this[Y2_INDEX] = predictedMotionEvent.y
                            // Helps differentiate between User and Predicted events
                            this[EVENT_TYPE] = IS_PREDICTED_EVENT
                            this[PRESSURE] = event.pressure
                        }

                        surfaceScissor.addPoint(predictedMotionEvent.x, predictedMotionEvent.y)

                        // Send the predicted next line to front buffered layer: faster rendering
                        frontBufferRenderer?.renderFrontBufferedLayer(predictedLine)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelGesture()

                previousX = 0f
                previousY = 0f
                surfaceScissor.reset()
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP -> {
                val isCancel =
                    (event.flags and MotionEvent.FLAG_CANCELED) == MotionEvent.FLAG_CANCELED
                previousX = 0f
                previousY = 0f
                surfaceScissor.reset()
                if (isCancel) {
                    cancelGesture()
                } else {
                    frontBufferRenderer?.commit()
                }


            }
        }
        true
    }

    private fun cancelGesture() {
        frontBufferRenderer?.cancel()
        //drawingManager.removeLast()
    }
}

fun rotatePoint(point: FloatArray, origin: FloatArray, angle: Double): FloatArray {
    val radAngle = (angle * Math.PI) / 180f

    val cosAngle = cos(radAngle)
    val sinAngle = sin(radAngle)

    val x = point[0]
    val y = point[1]

    val orignX = origin[0]
    val orignY = origin[1]

    val nx = cosAngle * (x - orignX) - sinAngle * (y - orignY) + orignX
    val ny = sinAngle * (x - orignX) + cosAngle * (y - orignY) + orignY

    return floatArrayOf(nx.toFloat(), ny.toFloat())

}

fun Int.colorToFloatArray(): FloatArray {
    val colorFA = FloatArray(4)
    return this.colorToFloatArray(colorFA)
}

fun Int.colorToFloatArray(colorFA: FloatArray): FloatArray {
    if (colorFA.size < 4) {
        throw ArrayIndexOutOfBoundsException("colorFA must be size 4 (RGBA) or more")
    }
    colorFA[0] = (this shr 16 and 0xFF) / 255.0f
    colorFA[1] = (this shr 8 and 0xFF) / 255.0f
    colorFA[2] = (this and 0xFF) / 255.0f
    colorFA[3] = (this shr 24 and 0xFF) / 255.0f
    return colorFA
}