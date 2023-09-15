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

import android.graphics.Color
import com.example.lowlatencysample.brush.toFloatArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTest {


    @Test
    fun Int_toFloatArray() {
        val red: Int = 0xFF0000
        val faRed = red.toFloatArray()

        assertEquals(1f, faRed[0])
        assertEquals(0f, faRed[1])
        assertEquals(0f, faRed[2])

        val green: Int = 0x00FF00
        val faGreen = green.toFloatArray()

        assertEquals(0f, faGreen[0])
        assertEquals(1f, faGreen[1])
        assertEquals(0f, faGreen[2])

        val blue: Int = 0x0000FF
        val faBlue = blue.toFloatArray()

        assertEquals(0f, faBlue[0])
        assertEquals(0f, faBlue[1])
        assertEquals(1f, faBlue[2])

    }


    @Test
    fun ColorInt_toFloatArray() {
        val red: Int = Color.RED
        val faRed = red.toFloatArray()

        assertEquals(1f, faRed[0])
        assertEquals(0f, faRed[1])
        assertEquals(0f, faRed[2])

        val green: Int = Color.GREEN
        val faGreen = green.toFloatArray()

        assertEquals(0f, faGreen[0])
        assertEquals(1f, faGreen[1])
        assertEquals(0f, faGreen[2])

        val blue: Int = Color.BLUE
        val faBlue = blue.toFloatArray()

        assertEquals(0f, faBlue[0])
        assertEquals(0f, faBlue[1])
        assertEquals(1f, faBlue[2])
    }

    @Test
    fun ColorInt_toFloatArrayWithParam() {

        val colorFA = FloatArray(4)

        val red: Int = Color.RED
        red.toFloatArray(colorFA)

        assertEquals(1f, colorFA[0])
        assertEquals(0f, colorFA[1])
        assertEquals(0f, colorFA[2])

        val green: Int = Color.GREEN
        green.toFloatArray(colorFA)
        assertEquals(0f, colorFA[0])
        assertEquals(1f, colorFA[1])
        assertEquals(0f, colorFA[2])

        val blue: Int = Color.BLUE
        blue.toFloatArray(colorFA)
        assertEquals(0f, colorFA[0])
        assertEquals(0f, colorFA[1])
        assertEquals(1f, colorFA[2])
    }

    @Test(expected = ArrayIndexOutOfBoundsException::class)
    fun ColorInt_toFloatArrayWithParamException() {
        val blue: Int = Color.BLUE

        val smallColorFA = FloatArray(2)
        blue.toFloatArray(smallColorFA)

    }
}