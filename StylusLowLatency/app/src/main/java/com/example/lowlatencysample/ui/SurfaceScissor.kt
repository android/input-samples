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

import android.graphics.Rect

/**
 * Used to calculate the damaged area during an ink stroke. This allows for incremental rendering
 * and lower latency. Will be used as a glScissor.
 */
class SurfaceScissor(var maxBrushSize: Int = 70) {

    private val mScissorBox: Rect = Rect()
    private var mEmpty: Boolean = false


    init {
        reset()
    }

    /**
     * Clear the damage area
     */
    fun reset() {
        mEmpty = true
    }

    val isEmpty: Boolean
        get() = mEmpty

    /**
     * Extend the current damage area to include the given point.
     */
    fun addPoint(x: Float, y: Float) {
        if (mEmpty) {
            mScissorBox.set(x.toInt(), y.toInt(), x.toInt(), y.toInt())
            mEmpty = false
        } else {
            mScissorBox.union(x.toInt(), y.toInt())
        }
    }

    /**
     * Extend the current damage area to include the given Rect.
     */
    fun addRect(rectToAdd: Rect) {
        if (!rectToAdd.isEmpty) {
            mScissorBox.union(rectToAdd)
        }
    }

    /**
     * Return the damage rectangle for use as a glScissor.
     *
     * @return Rect of damage area with a slightly larger "border" to guarantee correctness
     */
    val scissorBox: Rect
        get() = Rect(mScissorBox.left - maxBrushSize, mScissorBox.top - maxBrushSize,
            mScissorBox.right + maxBrushSize, mScissorBox.bottom + maxBrushSize)

}