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

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import androidx.annotation.WorkerThread
import androidx.graphics.lowlatency.BufferInfo
import androidx.graphics.lowlatency.GLFrontBufferedRenderer
import androidx.graphics.opengl.egl.EGLManager
import androidx.input.motionprediction.MotionEventPredictor
import com.example.lowlatencysample.DrawingManager

class LowLatencyRenderer(
    private var lineRenderer: LineRenderer,
    private val drawingManager: DrawingManager
) : GLFrontBufferedRenderer.Callback<FloatArray> {
    private val mvpMatrix = FloatArray(16)
    private val projection = FloatArray(16)

    private var frontBufferRenderer: GLFrontBufferedRenderer<FloatArray>? = null

    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var currentX: Float = 0f
    private var currentY: Float = 0f

    private var motionEventPredictor: MotionEventPredictor? = null


    @WorkerThread // GLThread
    private fun obtainRenderer(): LineRenderer =
        if (lineRenderer.isInitialized) {
            lineRenderer
        } else {
            lineRenderer
                .apply {
                    initialize()
                }
        }


    override fun onDrawFrontBufferedLayer(
        eglManager: EGLManager,
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

        obtainRenderer().drawLines(projection, param, Color.GREEN, false)
    }

    override fun onDrawDoubleBufferedLayer(
        eglManager: EGLManager,
        bufferInfo: BufferInfo,
        transform: FloatArray,
        params: Collection<FloatArray>
    ) {
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

        drawingManager.saveLines(params)

        for (line in drawingManager.getLines()) {
            obtainRenderer().drawLines(projection, line, Color.WHITE, true)
        }
    }

    fun attachSurfaceView(surfaceView: SurfaceView) {
        frontBufferRenderer = GLFrontBufferedRenderer(surfaceView, this)
        motionEventPredictor = MotionEventPredictor.newInstance(surfaceView)
    }

    fun release() {
        frontBufferRenderer?.release(true)
        {
            obtainRenderer().release()
        }
    }

    val onTouchListener = View.OnTouchListener { view, event ->
        motionEventPredictor?.record(event)

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                // Ask that the input system not batch MotionEvents
                // but instead deliver them as soon as they're available
                view.requestUnbufferedDispatch(event)

                currentX = event.x
                currentY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                previousX = currentX
                previousY = currentY
                currentX = event.x
                currentY = event.y

                val line = FloatArray(LineRenderer.DATA_STRUCTURE_SIZE).apply {
                    this[LineRenderer.X1_INDEX] = previousX
                    this[LineRenderer.Y1_INDEX] = previousY
                    this[LineRenderer.X2_INDEX] = currentX
                    this[LineRenderer.Y2_INDEX] = currentY
                    // Helps differentiate between User and Predicted events
                    this[LineRenderer.EVENT_TYPE] = LineRenderer.IS_USER_EVENT
                }
                // Send the short line to front buffered layer: fast rendering
                frontBufferRenderer?.renderFrontBufferedLayer(line)

                if (drawingManager.isPredictionEnabled) {
                    motionEventPredictor?.predict()?.let {
                        val predictedLine = FloatArray(LineRenderer.DATA_STRUCTURE_SIZE).apply {
                            this[LineRenderer.X1_INDEX] = currentX
                            this[LineRenderer.Y1_INDEX] = currentY
                            this[LineRenderer.X2_INDEX] = it.x
                            this[LineRenderer.Y2_INDEX] = it.y
                            // Helps differentiate between User and Predicted events
                            this[LineRenderer.EVENT_TYPE] = LineRenderer.IS_PREDICTED_EVENT
                        }

                        // Send the predicted next line to front buffered layer: faster rendering
                        frontBufferRenderer?.renderFrontBufferedLayer(predictedLine)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                frontBufferRenderer?.commit()
            }
            MotionEvent.ACTION_UP -> {
                frontBufferRenderer?.commit()
            }
        }
        true
    }
}