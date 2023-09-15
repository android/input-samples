/*
* Copyright (c) 2023 Google LLC
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
package com.example.lowlatencysample.brush

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.Log


class CanvasSimpleBrush: CanvasBrush {

    override var size = 25f
    override var minSize = size
    override var maxSize = size * 1.1f

    override fun release() {
        // no-op
    }

    private var strokePaint: Paint = Paint()
    private var fadedPaint: Paint = Paint()

    init {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = Color.BLUE
        strokePaint.strokeWidth = size
        strokePaint.strokeCap = Paint.Cap.ROUND

        fadedPaint.style = Paint.Style.STROKE
        fadedPaint.color = Color.BLUE
        fadedPaint.strokeWidth = size
        fadedPaint.strokeCap = Paint.Cap.ROUND

    }

    private fun getPaint(): Paint = strokePaint

    override fun render(canvas: Canvas, lines: FloatArray, color: Int) {
        val path = Path()

        strokePaint.color = color
        for (i in 0 until lines.size step Brush.DATA_STRUCTURE_SIZE) {
            if(i == 0) {
                path.moveTo(lines[i + Brush.X1_INDEX], lines[i + Brush.Y1_INDEX])
            }
            path.lineTo(lines[i + Brush.X2_INDEX], lines[i + Brush.Y2_INDEX])

            // fade the last bit of the line
//            if(lines[i + Brush.EVENT_TYPE] == Brush.IS_PREDICTED_EVENT) {
//                fadedPaint.shader = LinearGradient(
//                    lines[i + Brush.X1_INDEX],
//                    lines[i + Brush.Y1_INDEX],
//                    lines[i + Brush.X2_INDEX],
//                    lines[i + Brush.Y2_INDEX],
//                    color,
//                    Color.TRANSPARENT,
//                    Shader.TileMode.MIRROR
//                )
//                canvas.drawPath(path, fadedPaint)
//                return
//            }
        }

        canvas.drawPath(path, getPaint())
    }

}