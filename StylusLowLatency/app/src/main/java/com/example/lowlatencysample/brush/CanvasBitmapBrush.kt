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

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF


class CanvasBitmapBrush(var bitmap: Bitmap, size: Float) : CanvasBrush {

    override var size = size
    override var minSize = size
    override var maxSize = size * 1.5f

    private var strokePaint: Paint = Paint()
    private var bitmapPaint: Paint = Paint()

    init {
        bitmapPaint.style = Paint.Style.FILL
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = size / 3
    }

    override fun release() {
        // no-op
    }

    public fun getPaint(): Paint = bitmapPaint

    override fun render(canvas: Canvas, lines: FloatArray, color: Int) {

        val filter: ColorFilter =  PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        bitmapPaint.colorFilter = filter

        val blur = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        strokePaint.maskFilter = blur

        strokePaint.color = color

        val path = Path()

        val brushHalfSize = size / 2

        for (i in 0 until lines.size step Brush.DATA_STRUCTURE_SIZE) {
            val rect = RectF()
            rect.top = lines[i + Brush.Y1_INDEX] - brushHalfSize
            rect.bottom = lines[i + Brush.Y1_INDEX] + brushHalfSize
            rect.left = lines[i + Brush.X1_INDEX] - brushHalfSize
            rect.right = lines[i + Brush.X1_INDEX] + brushHalfSize

            if(i == 0) {
                path.moveTo(lines[i + Brush.X1_INDEX], lines[i + Brush.Y1_INDEX])
            }
            path.lineTo(lines[i + Brush.X2_INDEX], lines[i + Brush.Y2_INDEX])

            canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
            canvas.drawPath(path, strokePaint)
        }
    }

}