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
package com.example.lowlatencysample

import com.example.lowlatencysample.data.toFloatArrayLineClean
import com.example.lowlatencysample.brush.Brush.Companion.DATA_STRUCTURE_SIZE
import com.example.lowlatencysample.brush.Brush.Companion.IS_PREDICTED_EVENT
import com.example.lowlatencysample.brush.Brush.Companion.IS_USER_EVENT
import com.example.lowlatencysample.ui.rotatePoint
import org.junit.Assert
import org.junit.Test

class Collection_toFloatArray {

    @Test
    fun test_rotatePoint() {
        val point = floatArrayOf(1f, 3f)
        val origin = floatArrayOf(2f, 2f)

        val result = rotatePoint(point, origin, 90.0)

        Assert.assertEquals("x = 1",1f, result[0])
        Assert.assertEquals("y = 1",1f, result[1])

        val result1 = rotatePoint(point, origin, -90.0)

        Assert.assertEquals("x = 3",3f, result1[0])
        Assert.assertEquals("y = 3",3f, result1[1])
    }

    @Test
    fun Collection_toFloatArray() {
        val floatArrayList = mutableListOf<FloatArray>()

        // #1
        floatArrayList.add(FloatArray(DATA_STRUCTURE_SIZE).apply {
            this[0] = 0f
            this[1] = 1f
            this[2] = 2f
            this[3] = 3f
            this[4] = IS_USER_EVENT
        })

        // #2
        floatArrayList.add(FloatArray(DATA_STRUCTURE_SIZE).apply {
            this[0] = 33f
            this[1] = 44f
            this[2] = 55f
            this[3] = 11f
            this[4] = IS_PREDICTED_EVENT
        })

        // #3
        floatArrayList.add(FloatArray(DATA_STRUCTURE_SIZE).apply {
            this[0] = 6f
            this[1] = 7f
            this[2] = 8f
            this[3] = 9f
            this[4] = IS_USER_EVENT
        })


        val floatArrayFromList = floatArrayList.toFloatArrayLineClean()

        Assert.assertEquals(floatArrayFromList[0], 0f)
        Assert.assertEquals(floatArrayFromList[1], 1f)
        Assert.assertEquals(floatArrayFromList[2], 2f)
        Assert.assertEquals(floatArrayFromList[3], 3f)
        Assert.assertEquals(floatArrayFromList[4], IS_USER_EVENT)

        // FloatArray #2 is skipped because it is a predicted


        Assert.assertEquals(floatArrayFromList[5], 6f)
        Assert.assertEquals(floatArrayFromList[6], 7f)
        Assert.assertEquals(floatArrayFromList[7], 8f)
        Assert.assertEquals(floatArrayFromList[8], 9f)
        Assert.assertEquals(floatArrayFromList[9], IS_USER_EVENT)
    }
}