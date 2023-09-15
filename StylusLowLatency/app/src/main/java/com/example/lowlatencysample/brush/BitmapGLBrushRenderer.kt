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

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Color.HSVToColor
import android.graphics.Color.RGBToHSV
import android.opengl.GLES20
import android.opengl.GLException
import android.opengl.GLUtils
import android.util.Log
import com.example.lowlatencysample.brush.Brush.Companion.DATA_STRUCTURE_SIZE
import com.example.lowlatencysample.brush.Brush.Companion.PRESSURE
import com.example.lowlatencysample.brush.Brush.Companion.X1_INDEX
import com.example.lowlatencysample.brush.Brush.Companion.Y1_INDEX
import com.example.lowlatencysample.ui.colorToFloatArray
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

// Pressure curves
// https://www.youtube.com/watch?v=rbeIyZWfzXw
// https://www.youtube.com/watch?v=eAe9vLL5a48
/**
 * Handles the OpenGL rendering of a brush stroke
 *
 * Applies the matrix transformation and colours the stroke with the a_color information
 */
class BitmapBrushShader(
    private val bitmapBrush: Bitmap,
) : GLBrush {
    // Shader programs and variable handles

    override var isInitialized = false


    // increase the size of the brush with the pressure
    var enablePressureSize = true

    // darken the color with the pressure
    var enablePressureColor = false

    override var size = 25f
    override var minSize = size
    override var maxSize = size * 1.5f

    private var sprayPaintProgram: Int = -1
    private var sprayPaintVertexHandle: Int = -1 // a_position
    private var sprayPaintTextureHandle: Int = -1 // a_texture_coord
    private var sprayPaintColorHandle: Int = -1 // a_color
    private var sprayPaintMVPHandle: Int = -1 // u_mvp

    private var sprayVertexShader: Int = -1
    private var sprayFragmentShader: Int = -1

    // Spray paint bitmap texture
    private var isSprayPaintTextureInitialized = false
    private val sprayPaintTextures = IntArray(1)

    // Reference to the buffer for texture squares
    private var glSquareBufferHandle: Int = -1

    // Reference to the buffer for texture coordinates
    private var glTextureCoordinateBufferHandle: Int = -1

    // Reference to the buffer for texture squares
    private var glTextureColorBufferHandle: Int = -1

    // Reference to the buffer for texture square indices
    private var glSquareIndexBufferHandle: Int = -1


    private val squareIndexBuffer: IntBuffer = IntBuffer.allocate(DEFAULT_BUFFER_SIZE)
    private val textureColorBuffer: FloatBuffer = FloatBuffer.allocate(DEFAULT_BUFFER_SIZE)
    private val textureCoordinateBuffer: FloatBuffer = FloatBuffer.allocate(DEFAULT_BUFFER_SIZE)
    private val squareBuffer: FloatBuffer = FloatBuffer.allocate(DEFAULT_BUFFER_SIZE)


    private fun maximumPointToRenderPerBuffer(): Int {
        // Color is the biggest buffer, for each point we need to store 16 values
        val biggestBuffer = NUM_COLOR_VALUES_PER_SQUARE * FLOAT_SIZE
        return DEFAULT_BUFFER_SIZE / biggestBuffer
    }


    private fun initBuffers() {
        // Square buffer for bitmap shaders
        val tempSquareBuffer = IntBuffer.allocate(1)
        GLES20.glGenBuffers(1, tempSquareBuffer)
        glSquareBufferHandle = tempSquareBuffer.get()

        // Texture coordinate buffer for bitmap shaders
        val tempTextureCoordinateBuffer = IntBuffer.allocate(1)
        GLES20.glGenBuffers(1, tempTextureCoordinateBuffer)
        glTextureCoordinateBufferHandle = tempTextureCoordinateBuffer.get()

        // Texture color buffer for bitmap shaders
        val tempTextureColorBuffer = IntBuffer.allocate(1)
        GLES20.glGenBuffers(1, tempTextureColorBuffer)
        glTextureColorBufferHandle = tempTextureColorBuffer.get()

        // Square indices buffer for bitmap shaders
        val tempSquareIndexBuffer = IntBuffer.allocate(1)
        GLES20.glGenBuffers(1, tempSquareIndexBuffer)
        glSquareIndexBufferHandle = tempSquareIndexBuffer.get()

    }

    override fun initialize() {
        reset()
        initBuffers()
        try {
            // Create spray paint shader and connect shader variables to handles
            sprayPaintProgram = createSprayPaintShader()
            sprayPaintColorHandle = GLES20.glGetAttribLocation(sprayPaintProgram, "a_color")
            sprayPaintVertexHandle = GLES20.glGetAttribLocation(sprayPaintProgram, "a_position")
            sprayPaintTextureHandle =
                GLES20.glGetAttribLocation(sprayPaintProgram, "a_texture_coord")
            sprayPaintMVPHandle = GLES20.glGetUniformLocation(sprayPaintProgram, "u_mvp")
            checkGlError(TAG, "GL Get Locations")

            initSprayPaintTexture()
        } catch (e: Exception) {
            Log.e("BitmapBrushShader", "Unable to initialize brush BitmapBrushShader")
            e.printStackTrace()
        }
        isInitialized = true
    }

    fun reset() {
        if (sprayPaintProgram != -1) {
            GLES20.glDeleteProgram(sprayPaintProgram)
        }
        release()
    }

    override fun release() {
        if (sprayFragmentShader != -1) {
            GLES20.glDeleteShader(sprayVertexShader)
        }

        if (sprayFragmentShader != -1) {
            GLES20.glDeleteShader(sprayFragmentShader)
        }
    }

    private fun getRawBuffer(buffer: IntBuffer): IntBuffer {
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

    private val colorArray = IntArray(4)
    private val colorArrayWithPressure = FloatArray(4)
    private val colorArrayHSV = FloatArray(4)


    // use the entire square
    private val textureCoord = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f,
        0.0f, 1.0f
    )

    private fun resetBuffers() {
        // reset position buffers
        squareIndexBuffer.position(0)
        squareBuffer.position(0)
        textureCoordinateBuffer.position(0)
        textureColorBuffer.position(0)
    }

    // square data in which we going to draw the texture
    // pre-allocating so we avoid memory allocation in loops
    private val squarePoints = FloatArray(8)

    private var pointCount = 0

    /**
     * brushSize
     * @param pressure: Float (0f - 1f) make sure to normalize this value)
     * @param initialBrushSize: Float
     *
     * @return Float new size of the brush
     */
    private fun brushSize(pressure: Float, initialBrushSize: Float): Float {
        val adjustedPressureWithThreshold = pressure - THRESHOLD_PRESSURE_TO_INCREASE_BRUSH_SIZE

        return if (adjustedPressureWithThreshold > 0f) {
            // maximum value that can be added to the current pressure
            // max overall pressure is 1f
            // pressure below MINIMUM_PRESSURE_TO_INCREASE_BRUSH_SIZE isn't used
            // max pressure value is 1f - MINIMUM_PRESSURE_TO_INCREASE_BRUSH_SIZE
            // this prevents the brush to increase size when the min pressure threshold is reached
            val pressureMaxValue = MAXIMUM_PRESSURE_VALUE - THRESHOLD_PRESSURE_TO_INCREASE_BRUSH_SIZE

            // maximum value we can add to the brush size
            // maximum brush size - minimum brush size
            // remain size we can use for pressure upscale
            val maxBrushSize = maxSize - minSize

            // 1f is max pressure value
            val multiplicator = (MAXIMUM_PRESSURE_VALUE / pressureMaxValue)

            // calculate the additional size to add to the brush size
            val addToBrushSize = adjustedPressureWithThreshold * multiplicator * maxBrushSize

            // brush new size
            initialBrushSize + addToBrushSize
        } else {
            initialBrushSize // no change in size, pressure is not enough
        }
    }

    private fun colorForPoint(pressure: Float) {
        val colorPressure = pressure - THRESHOLD_PRESSURE_TO_DARKEN_COLOR
        if (colorPressure > 0) {

            RGBToHSV(colorArray[0], colorArray[1], colorArray[2], colorArrayHSV)

            //hsv[0] is Hue \([0..360[\)
            //hsv[1] is Saturation \([0...1]\)
            //hsv[2] is Value \([0...1]\)
            colorArrayHSV[2] = colorArrayHSV[2] - colorPressure

            val intColor = HSVToColor(colorArrayHSV)

            intColor.toFloatArray(colorArrayWithPressure)
        }
    }

    private fun setDataToBufferForOnePoint(index: Int, x: Float, y: Float, pressure: Float) {

        val normalizedPressure = if (pressure > 1.0) {
            1.0f
        } else {
            pressure
        }


        val distanceFromCenterWithPressureWidth = if(enablePressureSize) {
            brushSize(normalizedPressure, size)
        } else {
            size / 2
        }
        val distanceFromCenterWithPressureHeight = if(enablePressureSize) {
            brushSize(normalizedPressure, size)
        } else {
            size / 2
        }

        if(enablePressureColor) {
            colorForPoint(normalizedPressure)
        }


        // square data in which we are going to draw the texture
        // define the size of the brush
        // Vertex 1
        squarePoints[0] = x - distanceFromCenterWithPressureWidth
        squarePoints[1] = y - distanceFromCenterWithPressureHeight
        // Vertex 2
        squarePoints[2] = x + distanceFromCenterWithPressureWidth
        squarePoints[3] = y - distanceFromCenterWithPressureHeight
        // Vertex 3
        squarePoints[4] = x + distanceFromCenterWithPressureWidth
        squarePoints[5] = y + distanceFromCenterWithPressureHeight
        // Vertex 4
        squarePoints[6] = x - distanceFromCenterWithPressureWidth
        squarePoints[7] = y + distanceFromCenterWithPressureHeight

        squareBuffer.put(squarePoints)

        // square indices
        val start = index * 4
        // Two triangles that make up a square, starting at the current count of vertices
        val indices = intArrayOf(
            start, start + 1, start + 2, // triangle one /\
            start + 2, start + 3, start // triangle two  \/
        )
        squareIndexBuffer.put(indices)

        // set texture coordinates - use the entire square
        textureCoordinateBuffer.put(textureCoord)

        // set color for each 4 vertices
        for (j in 1..4) {
            textureColorBuffer.put(colorArrayWithPressure)
        }
    }

    /**
     * Perform the draw
     *
     * drawPoints contains all the completed brush strokes and, separately, the predicted strokes.
     * Before drawing, added predicted strokes to the draw list temporarily and removed the after
     * the draw is finished.
     *
     * @param matrix MVP matrix (Model-View-Projection) used for current buffer
     */
    override fun drawLines(mvpMatrix: FloatArray, lines: FloatArray, color: Int) {

        GLES20.glUseProgram(sprayPaintProgram)


        colorArray[0] = Color.red(color)
        colorArray[1] = Color.green(color)
        colorArray[2] = Color.blue(color)
        colorArray[3] = Color.alpha(color)

        color.colorToFloatArray(colorArrayWithPressure)

        val maxPoint = maximumPointToRenderPerBuffer() - 1


        val bufferStepLength = maxPoint * DATA_STRUCTURE_SIZE
        // Since the size of the buffer is limited, we fill up the size of the buffer
        // then render the subset of lines (with draw()),
        // then reset the buffers (resetBuffers()),
        // then repeat to render the next subset of lines
        // each subset is the size of the buffer max size
        for (i in 0 until lines.size-1 step bufferStepLength) {
            resetBuffers()

            val maxLength = min(lines.size, i + bufferStepLength)

            pointCount = 0
            try {
                for (j in i until maxLength step DATA_STRUCTURE_SIZE) {
                    // point to render
                    val x = lines[j + X1_INDEX]
                    val y = lines[j + Y1_INDEX]
                    val pressure = lines[j + PRESSURE]

                    setDataToBufferForOnePoint(pointCount, x, y, pressure)

                    pointCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "ERROR with data", e)
                e.printStackTrace()
            }

            setupSquareIndexBuffer()
            setupSquareBuffer()
            setupTextureBuffer()
            setupBrushColorBuffer(mvpMatrix)
            draw()
            disableVertexAttribArray()
        }
    }

    private fun disableVertexAttribArray() {
        GLES20.glDisableVertexAttribArray(sprayPaintVertexHandle)
        GLES20.glDisableVertexAttribArray(sprayPaintTextureHandle)
        GLES20.glDisableVertexAttribArray(sprayPaintColorHandle)
        GLES20.glDisableVertexAttribArray(sprayPaintMVPHandle)
    }


    private fun setupSquareIndexBuffer() {
        // indices of the points for the 2 triangle that creates the square
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, glSquareIndexBufferHandle)
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            pointCount * NUM_POINT_INDEX_PER_SQUARE * INT_SIZE,
            getRawBuffer(squareIndexBuffer),
            GLES20.GL_STATIC_DRAW
        )
    }

    private fun setupSquareBuffer() {
        // Vertices, actual coordinates of the square within which we will draw the texture
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glSquareBufferHandle)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            pointCount * NUM_POINT_PER_SQUARE * FLOAT_SIZE,
            getRawBuffer(squareBuffer),
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glVertexAttribPointer(
            sprayPaintVertexHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            0
        )
        GLES20.glEnableVertexAttribArray(sprayPaintVertexHandle)
    }

    private fun setupTextureBuffer() {
        // Texture coords
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glTextureCoordinateBufferHandle)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            pointCount * NUM_TEXTURE_COORDINATES_PER_SQUARE * FLOAT_SIZE,
            getRawBuffer(textureCoordinateBuffer),
            GLES20.GL_STATIC_DRAW
        )

        GLES20.glVertexAttribPointer(
            sprayPaintTextureHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            0
        )
        GLES20.glEnableVertexAttribArray(sprayPaintTextureHandle)
    }

    private fun setupBrushColorBuffer(matrix: FloatArray) {
        // Brush colors
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glTextureColorBufferHandle)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            pointCount * NUM_COLOR_VALUES_PER_SQUARE * FLOAT_SIZE,
            getRawBuffer(textureColorBuffer),
            GLES20.GL_STATIC_DRAW
        )

        GLES20.glVertexAttribPointer(
            sprayPaintColorHandle,
            4,
            GLES20.GL_FLOAT,
            false,
            0,
            0
        )
        GLES20.glEnableVertexAttribArray(sprayPaintColorHandle)

        // Enable the MVP matrix
        GLES20.glUniformMatrix4fv(sprayPaintMVPHandle, 1, false, matrix, 0)
    }

    private fun draw() {
        // Draw the bitmap textures
        GLES20.glActiveTexture(GL10.GL_TEXTURE0)
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, sprayPaintTextures[0])
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            pointCount * NUM_POINT_INDEX_PER_SQUARE,
            GLES20.GL_UNSIGNED_INT,
            0
        )
    }


    private fun initSprayPaintTexture() {
        GLES20.glGenTextures(1, sprayPaintTextures, 0)
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, sprayPaintTextures[0])
        GLES20.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_MIN_FILTER,
            GL10.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_MAG_FILTER,
            GL10.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_WRAP_S,
            GL10.GL_CLAMP_TO_EDGE.toFloat()
        )
        GLES20.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_WRAP_T,
            GL10.GL_CLAMP_TO_EDGE.toFloat()
        )

        GLES20.glEnable(GL10.GL_BLEND)
        GLES20.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA)

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmapBrush, 0)
        isSprayPaintTextureInitialized = true

        bitmapBrush.recycle()
    }


    private fun createSprayPaintShader(): Int {
        // Init spray paint shader
        // Create the spray paint shader program.
        val sprayPaintProgram = GLES20.glCreateProgram()
        if (sprayPaintProgram == 0) {
            checkGlError(TAG, "Error creating a spray paint shader program")
        }

        // Load the vertex shader.
        sprayVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, BITMAP_BRUSH_VERTEX_SHADER)
        if (sprayVertexShader == 0) {
            checkGlError(TAG, "Error creating a spray vertex shader ")
        }

        // Load the fragment shader.
        sprayFragmentShader =
            loadShader(GLES20.GL_FRAGMENT_SHADER, BITMAP_BRUSH_FRAG_SHADER)
        if (sprayVertexShader == 0) {
            checkGlError(TAG, "Error creating a spray fragment shader ")
        }

        // Attach the vertex shader to the shader program.
        GLES20.glAttachShader(sprayPaintProgram, sprayVertexShader)

        // Attach the fragment shader to the shader program.
        GLES20.glAttachShader(sprayPaintProgram, sprayFragmentShader)

        // Link the shader program.
        GLES20.glLinkProgram(sprayPaintProgram)
        return sprayPaintProgram
    }

    companion object {
        private const val TAG = "BitmapBrushShader"

        const val MAXIMUM_PRESSURE_VALUE = 1f
        const val THRESHOLD_PRESSURE_TO_INCREASE_BRUSH_SIZE = 0.25f
        const val THRESHOLD_PRESSURE_TO_DARKEN_COLOR = 0.25f
        const val FLOAT_SIZE = java.lang.Float.SIZE / java.lang.Byte.SIZE
        const val INT_SIZE = java.lang.Float.SIZE / java.lang.Byte.SIZE
        const val NUM_POINT_INDEX_PER_SQUARE = 6 // 2 triangles (3 points index per triangle)
        const val NUM_POINT_PER_SQUARE = 8 // 4 points, 1 for each corner, 2 coordinates per point
        const val NUM_TEXTURE_COORDINATES_PER_SQUARE = 8 // texture coordinates (textureCoord)
        const val NUM_COLOR_VALUES_PER_SQUARE = 4 * 4 // rgba * 4 vertices

        const val DEFAULT_BUFFER_SIZE = 1024 // 1Kb

        /**
         * The Bitmap-based Brush shaders take in a bitmap file. For a pure black color, the fragment
         * shader uses the selected brush color and bitmap transparency. All colors in the bitmap
         * are ignored.
         */
        private const val BITMAP_BRUSH_VERTEX_SHADER = ("attribute vec4 a_color;\n"
                + "attribute vec4 a_position;\n"
                + "uniform mat4 u_mvp;\n"
                + "varying vec4 v_color;\n"
                + "attribute vec2 a_texture_coord;\n"
                + "varying vec2 v_texture_coord_from_shader;\n"
                + "void main() {\n"
                + "  vec4 pointPos = u_mvp * a_position;\n"
                + "  v_color = a_color;\n"
                + "  gl_Position = vec4(pointPos.xy, 0.0, 1.0);\n"
                + "  v_texture_coord_from_shader = vec2(a_texture_coord.x, a_texture_coord.y);\n"
                + "}\n")

        private const val BITMAP_BRUSH_FRAG_SHADER = ("precision mediump float;\n"
                + "uniform sampler2D texture_sampler;\n"
                + "varying vec4 v_color;\n"
                + "varying vec2 v_texture_coord_from_shader;\n"
                + "void main() {\n"
                + "  vec4 tex_color = texture2D(texture_sampler, v_texture_coord_from_shader);\n"
                + "  if(tex_color.a < 0.1)\n"
                + "     discard;\n"
                + "  // Only use alpha value from bitmap to indicate draw area\n"
                + "  gl_FragColor = vec4(v_color.rgb, tex_color.a);\n"
                + "}\n")
    }

    /**
     * Loads and compiles the given `shaderType` and `shaderCode`.
     *
     * @param shaderType the type of shader to be created
     * @param shaderCode source code to be loaded into the shader
     * @return an integer reference to the created shader
     * @throws RuntimeException an error while compiling the shader
     */
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

    fun checkGlError(scope: String, msg: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            val exception = GLException(error, msg)
            Log.e(scope, "OpenGl error: $error", exception)
            throw exception
        }
    }


}

fun Int.toFloatArray(): FloatArray {
    val colorFA = FloatArray(4)
    return this.toFloatArray(colorFA)
}
fun Int.toFloatArray(colorFA: FloatArray): FloatArray {
    colorFA[0] = (this shr 16 and 0xFF) / 255.0f
    colorFA[1] = (this shr 8 and 0xFF) / 255.0f
    colorFA[2] = (this and 0xFF) / 255.0f
    colorFA[3] = (this shr 24 and 0xFF) / 255.0f
    return colorFA
}

fun FloatArray.print(): String {
    var string = ""
    for(i in 0 until this.size) {
        string += "$i ${this[i]}, "
    }
    return string
}