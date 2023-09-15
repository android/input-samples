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
package com.example.lowlatencysample.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import com.example.lowlatencysample.R
import com.example.lowlatencysample.brush.Brush
import com.example.lowlatencysample.brush.CanvasBitmapBrush
import com.example.lowlatencysample.brush.CanvasBrush
import com.example.lowlatencysample.brush.CanvasSimpleBrush

/**
 * Standard Canvas View, no acceleration
 */

class CanvasRegularView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr) {

  val tag = "Draw0"

  var brush: CanvasBrush? = null

  private var strokePaint: Paint = Paint()

  var bitmap: Bitmap? = null


  init {
    strokePaint.style = Paint.Style.STROKE
    strokePaint.color = Color.BLUE
    strokePaint.strokeWidth = 20f
    strokePaint.strokeCap = Paint.Cap.ROUND
  }

  private var previousX: Float = 0f
  private var previousY: Float = 0f
  private var currentX: Float = 0f
  private var currentY: Float = 0f

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    bitmap = Bitmap.createBitmap(
      resources.displayMetrics.widthPixels,
      resources.displayMetrics.heightPixels,
      Bitmap.Config.ARGB_8888
    )

    //brush = CanvasBitmapBrush(BitmapFactory.decodeResource(resources, R.drawable.spray_brush, BitmapFactory.Options().apply {  inScaled = false }), 50f)
    brush = CanvasSimpleBrush()

    val onTouchListener = OnTouchListener { view, event ->
      when (event?.action) {
        MotionEvent.ACTION_DOWN -> {
          currentX = event.x
          currentY = event.y

          val line = FloatArray(Brush.DATA_STRUCTURE_SIZE).apply {
            this[Brush.X1_INDEX] = currentX
            this[Brush.Y1_INDEX] = currentY
            this[Brush.X2_INDEX] = currentX
            this[Brush.Y2_INDEX] = currentY
            // Helps differentiate between User and Predicted events
            this[Brush.EVENT_TYPE] = Brush.IS_USER_EVENT
          }
          lines.add(line)
          invalidate()
        }

        MotionEvent.ACTION_MOVE -> {
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
          }
          lines.add(line)
          invalidate()
        }

        MotionEvent.ACTION_CANCEL -> {
          // no-op
        }

        MotionEvent.ACTION_UP -> {
          // No-op
        }
      }
      true
    }
    setOnTouchListener(onTouchListener)

  }

  override fun onResolvePointerIcon(event: MotionEvent?, pointerIndex: Int): PointerIcon {
    return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HANDWRITING)
  }

  var lines = mutableListOf<FloatArray>()

  fun exportBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
    // Create a new Canvas object using the Bitmap
    val canvas = Canvas(bitmap)
    // Draw the View into the Canvas
    this.draw(canvas)
    // Return the resulting Bitmap
    return bitmap
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (lines.isNotEmpty()) {
      for (line in lines) {
        brush?.render(canvas, line, Color.BLACK)
      }
    }
  }
}

