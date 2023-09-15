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

interface Brush {

    fun release()

    var size: Float
    var maxSize: Float
    var minSize: Float

    companion object {
        /**
         * DATA_STRUCTURE_SIZE
         * [0] x1
         * [1] y1
         * [2] x2
         * [3] y2
         * [4] event type: real (0f) or predicted (1f) point
         * [5] pressure (0f - 1f)
         */
        const val DATA_STRUCTURE_SIZE = 6
        const val X1_INDEX = 0
        const val Y1_INDEX = 1
        const val X2_INDEX = 2
        const val Y2_INDEX = 3
        const val EVENT_TYPE = 4
        const val PRESSURE = 5

        const val IS_USER_EVENT = 0.0f
        const val IS_PREDICTED_EVENT = 1.0f
    }
}

interface GLBrush: Brush {

    fun initialize()
    var isInitialized: Boolean
    fun drawLines(
        mvpMatrix: FloatArray,
        lines: FloatArray,
        color: Int
    )
}

interface CanvasBrush: Brush {
    fun render(canvas: Canvas, lines: FloatArray, color: Int)
}