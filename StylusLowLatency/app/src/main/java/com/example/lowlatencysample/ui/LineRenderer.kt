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
package com.example.lowlatencysample.ui

import android.graphics.Color
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL Renderer class responsible for drawing lines
 */
class LineRenderer {

    var isInitialized = false

    private var vertexShader: Int = -1

    private var fragmentShader: Int = -1

    private var glProgram: Int = -1

    private var positionHandle: Int = -1

    private var mvpMatrixHandle: Int = -1

    private var colorHandle: Int = -1

    private val colorArray = FloatArray(4)

    private var vertexBuffer: FloatBuffer? = null

    private val lineCoords = FloatArray(6)

    fun initialize() {
        release()
        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        glProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(glProgram, vertexShader)
        GLES20.glAttachShader(glProgram, fragmentShader)
        GLES20.glLinkProgram(glProgram)
        val bb: ByteBuffer =
            ByteBuffer.allocateDirect( // (number of coordinate values * 4 bytes per float)
                LINE_COORDS_SIZE * 4
            )
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder())
        // create a floating point buffer from the ByteBuffer
        vertexBuffer = bb.asFloatBuffer().apply {
            put(lineCoords)
            position(0)
        }
        positionHandle = GLES20.glGetAttribLocation(glProgram, V_POSITION)
        mvpMatrixHandle = GLES20.glGetUniformLocation(glProgram, U_MVP_MATRIX)
        colorHandle = GLES20.glGetUniformLocation(glProgram, V_COLOR)

        isInitialized = true
    }

    fun release() {
        if (vertexShader != -1) {
            GLES20.glDeleteShader(vertexShader)
            vertexShader = -1
        }
        if (fragmentShader != -1) {
            GLES20.glDeleteShader(fragmentShader)
            fragmentShader = -1
        }
        if (glProgram != -1) {
            GLES20.glDeleteProgram(glProgram)
            glProgram = -1
        }
    }

    fun drawLines(
        mvpMatrix: FloatArray,
        lines: FloatArray,
        color: Int,
        ignorePredicted: Boolean = false
    ) {
        GLES20.glUseProgram(glProgram)
        GLES20.glLineWidth(10.0f)
        GLES20.glEnableVertexAttribArray(positionHandle)
        colorArray[0] = Color.red(color).toFloat()
        colorArray[1] = Color.green(color).toFloat()
        colorArray[2] = Color.blue(color).toFloat()
        colorArray[3] = Color.alpha(color).toFloat()
        // Set color for drawing the triangle
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        vertexBuffer?.let { buffer ->
            for (i in 0 until lines.size step DATA_STRUCTURE_SIZE) {
                if (!ignorePredicted || lines[i + EVENT_TYPE] == IS_USER_EVENT) {
                    lineCoords[0] = lines[i + X1_INDEX]
                    lineCoords[1] = lines[i + Y1_INDEX]
                    lineCoords[2] = 0f
                    lineCoords[3] = lines[i + X2_INDEX]
                    lineCoords[4] = lines[i + Y2_INDEX]
                    lineCoords[5] = 0f
                    buffer.put(lineCoords)
                    buffer.position(0)
                }
            }
            // Prepare the triangle coordinate data
            GLES20.glVertexAttribPointer(
                positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                VERTEX_STRIDE, buffer
            )

            GLES20.glDrawArrays(GLES20.GL_LINES, 0, VERTEX_COUNT)
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    companion object {
        const val COORDS_PER_VERTEX = 3
        const val LINE_COORDS_SIZE = 6
        const val IS_USER_EVENT = 0.0f
        const val IS_PREDICTED_EVENT = 1.0f
        const val DATA_STRUCTURE_SIZE = 5

        const val X1_INDEX = 0
        const val Y1_INDEX = 1
        const val X2_INDEX = 2
        const val Y2_INDEX = 3
        const val EVENT_TYPE = 4

        private const val VERTEX_COUNT: Int = LINE_COORDS_SIZE / COORDS_PER_VERTEX
        private const val VERTEX_STRIDE: Int = COORDS_PER_VERTEX * 4 // 4 bytes per vertex
        private const val U_MVP_MATRIX = "uMVPMatrix"
        private const val V_POSITION = "vPosition"
        private const val VERTEX_SHADER_CODE =
            """
            uniform mat4 $U_MVP_MATRIX;
            attribute vec4 $V_POSITION;
            void main() { // the matrix must be included as a modifier of gl_Position
              gl_Position = $U_MVP_MATRIX * $V_POSITION;
            }
            """
        private const val V_COLOR = "vColor"
        private const val FRAGMENT_SHADER_CODE =
            """
            precision mediump float;
            uniform vec4 $V_COLOR;
            void main() {
              gl_FragColor = $V_COLOR;
            }                
            """

        fun loadShader(type: Int, shaderCode: String?): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}