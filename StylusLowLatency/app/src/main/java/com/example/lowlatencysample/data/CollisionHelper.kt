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
package com.example.lowlatencysample.data

import com.example.lowlatencysample.brush.Brush

/**
 * WARNING: this is not performance optimized
 * For demonstration only
 */


/**
 * lineLineIntersection
 * returns true if the 2 lines intersect
 *
 * @param line1_x1: Float,
 * @param line1_y1: Float,
 * @param line1_x2: Float,
 * @param line1_y2: Float,
 * @param line2_x1: Float,
 * @param line2_y1: Float,
 * @param line2_x2: Float,
 * @param line2_y2: Float
 * @return Boolean (true if lines intersect)
 */
fun lineLineIntersection(
  line1_x1: Float,
  line1_y1: Float,
  line1_x2: Float,
  line1_y2: Float,
  line2_x1: Float,
  line2_y1: Float,
  line2_x2: Float,
  line2_y2: Float
): Boolean {

    // calculate the direction of the lines
    val uA =
        ((line2_x2 - line2_x1) * (line1_y1 - line2_y1) - (line2_y2 - line2_y1) * (line1_x1 - line2_x1)) / ((line2_y2 - line2_y1) * (line1_x2 - line1_x1) - (line2_x2 - line2_x1) * (line1_y2 - line1_y1))

    if(uA >= 0f && uA <= 1f) { // uA must be between 0-1 to continue

        val uB =
            ((line1_x2 - line1_x1) * (line1_y1 - line2_y1) - (line1_y2 - line1_y1) * (line1_x1 - line2_x1)) / ((line2_y2 - line2_y1) * (line1_x2 - line1_x1) - (line2_x2 - line2_x1) * (line1_y2 - line1_y1))

        if(uB >= 0f && uB <= 1f) { // uB is also between 0-1
            return true
        }

    }

    return false

}


/**
 * lineRectIntersection
 * returns true if the line intersects the rectangle
 *
 * @param x1: Float,
 * @param y1: Float,
 * @param x2: Float,
 * @param y2: Float,
 * @param rx: Float,
 * @param ry: Float,
 * @param rw: Float,
 * @param rh: Float
 *
 * @return Boolean (true if line intersect the rectangle)
 */
fun lineRectIntersection(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    rx: Float,
    ry: Float,
    rw: Float,
    rh: Float
): Boolean {
    // check if the line has hit any of the rectangle's sides
    // uses the Line/Line function below
    val left = lineLineIntersection(x1, y1, x2, y2, rx, ry, rx, ry + rh)
    if(left) {
        return true
    }
    val right = lineLineIntersection(x1, y1, x2, y2, rx + rw, ry, rx + rw, ry + rh)
    if(right) {
        return true
    }
    val top = lineLineIntersection(x1, y1, x2, y2, rx, ry, rx + rw, ry)
    if(top) {
        return true
    }
    val bottom = lineLineIntersection(x1, y1, x2, y2, rx, ry + rh, rx + rw, ry + rh)
    if(bottom) {
        return true
    }

    return false
}

/**
 * pointInRect
 * returns true if the point is in the rectangle or false over wise
 *
 * @param x: Float
 * @param y: Float
 * @param rx: Float
 * @param ry: Float
 * @param rw: Float
 * @param rh: Float
 * @return Boolean
 */
fun pointInRect(x: Float, y: Float, rx: Float, ry: Float, rw: Float, rh: Float): Boolean {
    return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh
}

/**
 * lineInOrIntersectRect
 * returns true if the line is in or intersects the rectangle
 *
 * @param x1: Float,
 * @param y1: Float,
 * @param x2: Float,
 * @param y2: Float,
 * @param rx: Float,
 * @param ry: Float,
 * @param rw: Float,
 * @param rh: Float
 *
 * @return Boolean
 */
fun lineInOrIntersectRect(x1: Float,
                          y1: Float,
                          x2: Float,
                          y2: Float,
                          rx: Float,
                          ry: Float,
                          rw: Float,
                          rh: Float): Boolean {
    return pointInRect(x1,y1, rx, ry, rw, rh)
            || pointInRect(x2,y2, rx, ry, rw, rh)
            || lineRectIntersection(x1,y1, x2,y2, rx, ry, rw, rh)
}


/**
 * getIntersect
 * @param rect: Rect
 * returns a sub-line as a Float array for the lines that intersects the rect
 *
 * @return FloatArray (sub-line)
 */
fun FloatArray.getIntersect(rx: Float,
                            ry: Float,
                            rw: Float,
                            rh: Float): FloatArray {

    return this.getIntersectSublines(rx, ry, rw, rh).toFloatArrayLineClean()
}

fun FloatArray.getIntersectSublines(rx: Float,
                            ry: Float,
                            rw: Float,
                            rh: Float): List<FloatArray> {
    val floatArrayList = mutableListOf<FloatArray>()

    for (i in this.indices step Brush.DATA_STRUCTURE_SIZE) {
        val x1 = this[i + Brush.X1_INDEX]
        val y1 = this[i + Brush.Y1_INDEX]
        val x2 = this[i + Brush.X2_INDEX]
        val y2 = this[i + Brush.Y2_INDEX]
        val pressure = this[i + Brush.PRESSURE]
        val eventtype = this[i + Brush.EVENT_TYPE]

        if(lineInOrIntersectRect(x1,
                y1,
                x2,
                y2,
                rx,
                ry,
                rw,
                rh)) {

            floatArrayList.add(floatArrayOf(
                x1,
                y1,
                x2,
                y2,
                eventtype,
                pressure
            ))
        }
    }
    return floatArrayList
}
