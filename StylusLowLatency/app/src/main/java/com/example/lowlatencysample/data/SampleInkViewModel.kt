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

package com.example.lowlatencysample.data

import androidx.annotation.WorkerThread
import androidx.graphics.opengl.GLRenderer
import androidx.lifecycle.ViewModel
import com.example.lowlatencysample.DrawingManager
import com.example.lowlatencysample.ui.Brush.Companion.DATA_STRUCTURE_SIZE
import com.example.lowlatencysample.ui.Brush.Companion.EVENT_TYPE
import com.example.lowlatencysample.ui.Brush.Companion.X1_INDEX
import com.example.lowlatencysample.ui.Brush.Companion.X2_INDEX
import com.example.lowlatencysample.ui.Brush.Companion.Y1_INDEX
import com.example.lowlatencysample.ui.Brush.Companion.Y2_INDEX
import com.example.lowlatencysample.ui.Brush.Companion.IS_USER_EVENT
import com.example.lowlatencysample.ui.Brush.Companion.PRESSURE

class SampleInkViewModel : DrawingManager, ViewModel() {

  private val lines: MutableList<FloatArray> = mutableListOf()

  override var isPredictionEnabled: Boolean = true
  override var isGLSurfaceScissorEnabled: Boolean = true
  override var isDebugColorEnabled: Boolean = false

  override var orientation: Int = 0
  override var displayRotation: Int = 0

  @get:WorkerThread // GLThread
  override val glRenderer = GLRenderer()

  init {
    glRenderer.start()
  }

  override fun saveLines(lines: FloatArray) {
    this.lines.add(lines)
  }

  override fun getLines(): Collection<FloatArray> {
    return lines
  }
}

/**
 * toFloatArrayLineClean
 * removes the predicted events from a FloatArray collection following
 * the IBrush data structure
 * @return FloatArray (flat)
 */
fun Collection<FloatArray>.toFloatArrayLineClean(): FloatArray {
  val size = this.sumOf {
    if (it[EVENT_TYPE] == IS_USER_EVENT) {
      it.size
    } else {
      0
    }
  }

  val fa = FloatArray(size)
  var counter = 0
  for (item in this) {
    for (i in item.indices step DATA_STRUCTURE_SIZE) {
      if (item[i + EVENT_TYPE] == IS_USER_EVENT) {
        fa[counter] = item[i + X1_INDEX]
        fa[counter + 1] = item[i + Y1_INDEX]
        fa[counter + 2] = item[i + X2_INDEX]
        fa[counter + 3] = item[i + Y2_INDEX]
        fa[counter + 4] = IS_USER_EVENT
        fa[counter + 5] = item[i + PRESSURE]
        counter += DATA_STRUCTURE_SIZE
      }
    }
  }
  return fa
}

fun Collection<FloatArray>.toFloatArray(): FloatArray {
  val size = this.sumOf {
    it.size
  }

  val fa = FloatArray(size)
  var counter = 0
  for (item in this) {
    for (i in item.indices step DATA_STRUCTURE_SIZE) {
      fa[counter] = item[i + X1_INDEX]
      fa[counter + 1] = item[i + Y1_INDEX]
      fa[counter + 2] = item[i + X2_INDEX]
      fa[counter + 3] = item[i + Y2_INDEX]
      fa[counter + 4] = IS_USER_EVENT
      fa[counter + 5] = item[i + PRESSURE]
      counter += DATA_STRUCTURE_SIZE
    }
  }
  return fa
}