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

import com.example.lowlatencysample.data.lineInOrIntersectRect
import com.example.lowlatencysample.data.lineLineIntersection
import com.example.lowlatencysample.data.lineRectIntersection
import com.example.lowlatencysample.data.pointInRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class CollisionTest {


    @Test
    fun line_line_collisionTest() {
//      x,y3    x,y2
//         | /
//         /
//        /|
//   x,y1/ |
//         |
//        x,y4

        var x1 = 90f
        var y1 = 150f
        var x2 = 150f
        var y2 = 10f

        var x3 = 100f
        var y3 = 0f
        var x4 = 100f
        var y4 = 170f

        val result = lineLineIntersection(
            x1,
            y1,
            x2,
            y2,
            x3,
            y3,
            x4,
            y4
        )

        assertTrue("collision should return true", result)


//      x,y3            x,y2
//         |            /
//         |           /
//         |          /
//         |    x,y1/
//         |
//       x,y4

        x1 = 120f
        y1 = 150f
        x2 = 150f
        y2 = 10f

        x3 = 100f
        y3 = 0f
        x4 = 100f
        y4 = 170f

        val result1 = lineLineIntersection(
            x1,
            y1,
            x2,
            y2,
            x3,
            y3,
            x4,
            y4
        )

        assertFalse("collision should return false", result1)

    }

    @Test
    fun line_rect_collisionTest() {
//                       x,y2
//              rw      /
//     rx,y____________/
//        |         /|
//    rh  |    x,y1/ |
//        |__________|

        var x1 = 120f
        var y1 = 150f
        var x2 = 150f
        var y2 = 10f

        var rx = 80f
        var ry = 10f
        var rh = 100f
        var rw = 100f


        val result = lineRectIntersection(
            x1,
            y1,
            x2,
            y2,
            rx,
            ry,
            rw,
            rh
        )

        assertTrue("Line start inside rectangle, end outside", result)



//              rw
//     rx,y____________
//        |      x,y2 |
//        |       /   |
//        |      /    |
//    rh  | x,y1/     |
//        |___________|

        x1 = 90f
        y1 = 80f
        x2 = 100f
        y2 = 20f

        rx = 80f
        ry = 10f
        rh = 100f
        rw = 100f


        val result1 = lineInOrIntersectRect(
            x1,
            y1,
            x2,
            y2,
            rx,
            ry,
            rw,
            rh
        )

        assertTrue("Line inside rectangle", result1)





//              rw
//     rx,y____________         x,y2
//        |           |        /
//        |           |       /
//        |           |      /
//    rh  |           |     /
//        |___________|   x,y1

        x1 = 250f
        y1 = 80f
        x2 = 200f
        y2 = 20f

        rx = 80f
        ry = 10f
        rh = 100f
        rw = 100f


        val result2 = lineInOrIntersectRect(
            x1,
            y1,
            x2,
            y2,
            rx,
            ry,
            rw,
            rh
        )

        assertFalse("Line outside rectangle", result2)
    }

    @Test
    fun pointInRectTest() {
        assertTrue(pointInRect(10f, 10f, 10f, 10f, 30f, 30f))
        assertTrue(pointInRect(11f, 11f, 10f, 10f, 30f, 30f))
        assertTrue(pointInRect(30f, 30f, 10f, 10f, 30f, 30f))
        assertFalse(pointInRect(1f, 1f, 10f, 10f, 30f, 30f))
        assertFalse(pointInRect(41f, 41f, 10f, 10f, 30f, 30f))
        assertFalse(pointInRect(10f, 42f, 10f, 10f, 30f, 30f))
        assertFalse(pointInRect(42f, 10f, 10f, 10f, 30f, 30f))
    }
}