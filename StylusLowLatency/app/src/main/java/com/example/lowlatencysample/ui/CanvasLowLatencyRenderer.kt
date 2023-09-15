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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import androidx.input.motionprediction.MotionEventPredictor
import com.example.lowlatencysample.DrawingManager
import com.example.lowlatencysample.brush.Brush
import com.example.lowlatencysample.brush.CanvasBrush
import com.example.lowlatencysample.data.getIntersectSublines
import com.example.lowlatencysample.data.toFloatArray
import com.example.lowlatencysample.data.toFloatArrayLineClean
import kotlin.math.ceil



class CanvasLowLatencyRenderer(
    private var brushRenderer: CanvasBrush,
    private val drawingManager: DrawingManager
) : LowLatencyRenderer<CanvasBrush>, CanvasFrontBufferedRenderer.Callback<FloatArray> {

    private var canvasFrontBufferRenderer: CanvasFrontBufferedRenderer<FloatArray>? = null

    private val surfaceScissor = SurfaceScissor()
    private var surfaceView: SurfaceView? = null
    private var currentLine = mutableListOf<FloatArray>()

    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var currentX: Float = 0f
    private var currentY: Float = 0f

    private var motionEventPredictor: MotionEventPredictor? = null

    init {
        surfaceScissor.maxBrushSize = ceil(this.brushRenderer.maxSize).toInt()
    }

    override fun updateBrush(brush: CanvasBrush) {
        this.brushRenderer = brush
        surfaceScissor.maxBrushSize = ceil(this.brushRenderer.maxSize).toInt()
    }

    private var defaultStrokeColor = Color.WHITE
    private var currentStrokeColor = Color.RED
    private var previousStrokeColor = Color.YELLOW

    override fun onDrawFrontBufferedLayer(
        canvas: Canvas,
        bufferWidth: Int,
        bufferHeight: Int,
        param: FloatArray
    ) {

        // Canvas code to be executed on the Front buffer.
        // Front buffer should be used to modify/render small area of the screen

        if (drawingManager.isSurfaceScissorEnabled && !surfaceScissor.isEmpty) {

            val damagedArea = surfaceScissor.scissorBox


            val inflatedDamagedArea = Rect(surfaceScissor.scissorBox).apply {
                this.left -= surfaceScissor.maxBrushSize
                this.top -= surfaceScissor.maxBrushSize
                this.right += surfaceScissor.maxBrushSize
                this.bottom += surfaceScissor.maxBrushSize
            }

            val rx = inflatedDamagedArea.left
            val ry = inflatedDamagedArea.top
            val rw = inflatedDamagedArea.width()
            val rh = inflatedDamagedArea.height()

            // 1. create a Bitmap of the size of the damaged area
            val tempCanvasBitmap = Bitmap.createBitmap(damagedArea.width(), damagedArea.height(), Bitmap.Config.ARGB_8888)
            // 2. setup a canvas with this bitmap
            val damagedCanvas = Canvas(tempCanvasBitmap)

            val previousStrokesColor = if (drawingManager.isDebugColorEnabled) {
                previousStrokeColor
            } else {
                defaultStrokeColor
            }
            // 3. prepare the buffer
            val damagedRect = Rect(0, 0, inflatedDamagedArea.width(), inflatedDamagedArea.height())

            damagedCanvas.drawRect(damagedRect, Paint().apply {
                this.color = Color.BLACK
                this.style = Paint.Style.FILL
            })

            // TODO: when one line is like a U, if the pointer is inside, sub lines may be created lead to
            // TODO: connection between the two vertical lines of the U.

            // 4. write the modification into the damagedCanvas

            // 4a. draw older lines within the damaged area
            for (line in drawingManager.getLines()) {
                val sublines = line.getIntersectSublines(rx.toFloat(), ry.toFloat(), rw.toFloat(), rh.toFloat())
                if (sublines.isNotEmpty()) {
                    for(subline in sublines) {

                        // line coordinates need to be relative to the damaged area
                        subline[Brush.X1_INDEX] -= damagedArea.left.toFloat()
                        subline[Brush.Y1_INDEX] -= damagedArea.top.toFloat()
                        subline[Brush.X2_INDEX] -= damagedArea.left.toFloat()
                        subline[Brush.Y2_INDEX] -= damagedArea.top.toFloat()

                        if(subline[Brush.EVENT_TYPE] == Brush.IS_USER_EVENT) {
                            brushRenderer.render(damagedCanvas, subline, previousStrokesColor)
                        }
                    }
                }
            }

            // 4b. draw current line within the damaged area
            val currentLineExtended = currentLine.toMutableList()
            currentLineExtended.add(param)

            val currentSublines = currentLineExtended.toFloatArray().getIntersectSublines(rx.toFloat(), ry.toFloat(), rw.toFloat(), rh.toFloat())
            val currentLineColor = if (drawingManager.isDebugColorEnabled) {
                currentStrokeColor
            } else {
                defaultStrokeColor
            }
            if (currentSublines.isNotEmpty()) {
                for(subline in currentSublines) {
                    // line coordinates need to be relative to the damaged area
                    subline[Brush.X1_INDEX] -= damagedArea.left.toFloat()
                    subline[Brush.Y1_INDEX] -= damagedArea.top.toFloat()
                    subline[Brush.X2_INDEX] -= damagedArea.left.toFloat()
                    subline[Brush.Y2_INDEX] -= damagedArea.top.toFloat()

                    brushRenderer.render(
                        damagedCanvas,
                        subline,
                        currentLineColor
                    )
                }
            }

            // 5. copy the damaged Bitmap to the actual canvas
            canvas.drawBitmap(tempCanvasBitmap, null, damagedArea, null)

            // we want to keep only the user event for the current line as well.
            if (param[Brush.EVENT_TYPE] == Brush.IS_USER_EVENT) {
                currentLine.add(param)
            }
        } else {
            brushRenderer.render(canvas, param, defaultStrokeColor)
        }
    }

    override fun onDrawMultiBufferedLayer(
        canvas: Canvas,
        bufferWidth: Int,
        bufferHeight: Int,
        params: Collection<FloatArray>
    ) {

        currentLine.clear()
        if (params.isNotEmpty()) {
            val fla = params.toFloatArrayLineClean()
            drawingManager.saveLines(fla)
        }

        // Canvas code to redraw the entire scene (all the lines here)
        for (line in drawingManager.getLines()) {
            brushRenderer.render(canvas, line, defaultStrokeColor)
        }
    }

    override fun attachSurfaceView(surfaceView: SurfaceView) {
        canvasFrontBufferRenderer = CanvasFrontBufferedRenderer(surfaceView, this)
        motionEventPredictor = MotionEventPredictor.newInstance(surfaceView)
        this.surfaceView = surfaceView
    }

    override fun release() {
        canvasFrontBufferRenderer?.release(true)
        {
        }
    }

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

                val line = FloatArray(Brush.DATA_STRUCTURE_SIZE).apply {
                    this[Brush.X1_INDEX] = currentX
                    this[Brush.Y1_INDEX] = currentY
                    this[Brush.X2_INDEX] = currentX
                    this[Brush.Y2_INDEX] = currentY
                    // Helps differentiate between User and Predicted events
                    this[Brush.EVENT_TYPE] = Brush.IS_USER_EVENT
                    this[Brush.PRESSURE] = event.pressure
                }

                canvasFrontBufferRenderer?.renderFrontBufferedLayer(line)
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

                val line = FloatArray(Brush.DATA_STRUCTURE_SIZE).apply {
                    this[Brush.X1_INDEX] = previousX
                    this[Brush.Y1_INDEX] = previousY
                    this[Brush.X2_INDEX] = currentX
                    this[Brush.Y2_INDEX] = currentY
                    // Helps differentiate between User and Predicted events
                    this[Brush.EVENT_TYPE] = Brush.IS_USER_EVENT
                    this[Brush.PRESSURE] = event.pressure
                }
                // Send the short line to front buffered layer: fast rendering
                canvasFrontBufferRenderer?.renderFrontBufferedLayer(line)

                // add new points to surfaceScissor
                surfaceScissor.addPoint(currentX, currentY)

                if (drawingManager.isPredictionEnabled) {
                    val predictedMotionEvent = motionEventPredictor?.predict()
                    if (predictedMotionEvent != null) {
                        val predictedLine = FloatArray(Brush.DATA_STRUCTURE_SIZE).apply {
                            this[Brush.X1_INDEX] = currentX
                            this[Brush.Y1_INDEX] = currentY
                            this[Brush.X2_INDEX] = predictedMotionEvent.x
                            this[Brush.Y2_INDEX] = predictedMotionEvent.y
                            // Helps differentiate between User and Predicted events
                            this[Brush.EVENT_TYPE] = Brush.IS_PREDICTED_EVENT
                            this[Brush.PRESSURE] = event.pressure
                        }

                        surfaceScissor.addPoint(predictedMotionEvent.x, predictedMotionEvent.y)

                        // Send the predicted next line to front buffered layer: faster rendering
                        canvasFrontBufferRenderer?.renderFrontBufferedLayer(predictedLine)
                    }

                }
            }

            MotionEvent.ACTION_CANCEL -> {
                previousX = 0f
                previousY = 0f
                surfaceScissor.reset()
                canvasFrontBufferRenderer?.commit()
            }

            MotionEvent.ACTION_UP -> {
                previousX = 0f
                previousY = 0f
                surfaceScissor.reset()
                canvasFrontBufferRenderer?.commit()
            }
        }
        true
    }
}