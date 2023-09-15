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
package com.example.lowlatencysample.brush

import android.graphics.Color
import android.opengl.GLES20
import android.util.Log
import com.example.lowlatencysample.brush.Brush.Companion.DATA_STRUCTURE_SIZE
import com.example.lowlatencysample.brush.Brush.Companion.X1_INDEX
import com.example.lowlatencysample.brush.Brush.Companion.X2_INDEX
import com.example.lowlatencysample.brush.Brush.Companion.Y1_INDEX
import com.example.lowlatencysample.brush.Brush.Companion.Y2_INDEX
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min


/**
 * OpenGL Renderer class responsible for drawing lines
 */
class LineGLBrushRenderer : GLBrush {

    override var isInitialized = false
    private var vertexShader: Int = -1
    private var fragmentShader: Int = -1
    private var glProgram: Int = -1
    private var positionHandle: Int = -1
    private var mvpMatrixHandle: Int = -1
    private var colorHandle: Int = -1
    private val colorArray = FloatArray(4)
    private var vertexBuffer: FloatBuffer? = null
    private val lineCoords = FloatArray(LINE_COORDS_SIZE)

    override var size: Float = 10f
    override var maxSize: Float = 10f
    override var minSize: Float = 10f

    override fun initialize() {
        release()
        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        glProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(glProgram, vertexShader)
        GLES20.glAttachShader(glProgram, fragmentShader)
        GLES20.glLinkProgram(glProgram)
        val bb: ByteBuffer =
            ByteBuffer.allocateDirect(
                DEFAULT_BUFFER_SIZE
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

    override fun release() {
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

    private var pointCount = 0

    private fun maximumPointToRenderPerBuffer(): Int {
        // Color is the biggest buffer, for each point we need to store 16 values
        val biggestBuffer = LINE_COORDS_SIZE * FLOAT_BYTE_COUNT
        return DEFAULT_BUFFER_SIZE / biggestBuffer
    }

    private fun getRawBuffer(buffer: FloatBuffer): FloatBuffer {
        // Slice the buffer from 0 to position.
        val limit = buffer.limit()
        val position = buffer.position()

        buffer.limit(position)
        buffer.position(0)
        val bufferSliced = buffer.slice()

        // Restore position and limit.
        buffer.limit(limit)
        buffer.position(position)
        return bufferSliced
    }

    override fun drawLines(
        mvpMatrix: FloatArray,
        lines: FloatArray,
        color: Int
    ) {
        GLES20.glUseProgram(glProgram)
        GLES20.glLineWidth(size)
        GLES20.glEnableVertexAttribArray(positionHandle)
        colorArray[0] = Color.red(color).toFloat()
        colorArray[1] = Color.green(color).toFloat()
        colorArray[2] = Color.blue(color).toFloat()
        colorArray[3] = Color.alpha(color).toFloat()

        // Set color for drawing the triangle
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        val maxPoint = maximumPointToRenderPerBuffer() - 1

        val bufferStepLength = maxPoint * DATA_STRUCTURE_SIZE

        if (vertexBuffer != null) {
            // Since the size of the buffer is limited, we fill up the size of the buffer
            // then render the subset of lines (with draw()),
            // then reset the buffers (resetBuffers()),
            // then repeat to render the next subset of lines
            // each subset is the size of the buffer max size
            for (i in 0 until lines.size - 1 step bufferStepLength) {
                vertexBuffer?.position(0)

                val maxLength = min(lines.size, i + bufferStepLength)

                pointCount = 0
                try {
                    for (j in i until maxLength step DATA_STRUCTURE_SIZE) {
                        lineCoords[0] = lines[j + X1_INDEX]
                        lineCoords[1] = lines[j + Y1_INDEX]
                        lineCoords[2] = 0f
                        lineCoords[3] = lines[j + X2_INDEX]
                        lineCoords[4] = lines[j + Y2_INDEX]
                        lineCoords[5] = 0f
                        vertexBuffer?.put(lineCoords)

                        pointCount++
                    }

                    // Prepare the triangle coordinate data
                    GLES20.glVertexAttribPointer(
                        positionHandle,
                        COORDS_PER_VERTEX,
                        GLES20.GL_FLOAT,
                        false,
                        VERTEX_STRIDE,
                        getRawBuffer(vertexBuffer!!)
                    )

                    vertexBuffer?.position(0)
                    GLES20.glDrawArrays(GLES20.GL_LINES, 0, VERTEX_COUNT * pointCount)


                } catch (e: Exception) {
                    Log.e(TAG, "ERROR with data", e)
                    e.printStackTrace()
                }
            }
            GLES20.glDisableVertexAttribArray(positionHandle)
        }
    }



    companion object {
        const val COORDS_PER_VERTEX = 3
        const val LINE_COORDS_SIZE = 6

        private const val TAG = "LineRenderer"

        private const val DEFAULT_BUFFER_SIZE = 1024

        private const val FLOAT_BYTE_COUNT = java.lang.Float.SIZE / java.lang.Byte.SIZE

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

        fun loadShader(shaderType: Int, shaderCode: String, scope: String = "OpenGL"): Int {
            val shader = GLES20.glCreateShader(shaderType)
            if (shader == 0) {
                checkGlError(scope, "Error loading shader: $shaderType")
            }

            // Add the source code to the shader and compile it.
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            // Get the compilation status.
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == GLES20.GL_FALSE) {
                val infoLog = GLES20.glGetShaderInfoLog(shader)
                Log.e(scope, infoLog)
                GLES20.glDeleteShader(shader)
                throw IllegalArgumentException("Shader compilation failed: $infoLog")
            }
            return shader
        }

        fun checkGlError(tag: String, message: String) {
            Log.e(tag, message)
        }
    }
}