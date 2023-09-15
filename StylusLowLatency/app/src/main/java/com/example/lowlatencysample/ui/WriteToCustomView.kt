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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Build.VERSION
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.MotionEvent.TOOL_TYPE_STYLUS
import android.view.PointerIcon
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.DeleteGesture
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.HandwritingGesture
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InsertGesture
import android.view.inputmethod.JoinOrSplitGesture
import android.view.inputmethod.SelectGesture
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.LinearLayoutCompat
import java.util.concurrent.Executor
import java.util.function.IntConsumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WriteToCustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var inputMethodManager: InputMethodManager? = null

    private val isWriteInTextviewEnabled = VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private var text: String = ""
    private var textSelection: String = ""

    private var selectionRectF: RectF? = null
    private var deletionRectF: RectF? = null

    private var monospace: Typeface = Typeface.createFromAsset(this.context.assets, "RobotoMono-Medium.ttf")

    private var paint: Paint = Paint().apply {
        this.style = Paint.Style.STROKE
        this.color = Color.BLUE
        this.strokeWidth = 10f
        this.strokeCap = Paint.Cap.ROUND;
        this.textSize = 100f
        this.typeface = monospace // using monospace font so it is easier to calculate letter width
    }

    private var highlightPaint: Paint = Paint().apply {
        this.style = Paint.Style.FILL
        this.color = Color.valueOf(1.0f, 1.0f, 0.1f, 0.5f).toArgb()
    }

    init {
        if (isWriteInTextviewEnabled) {
            inputMethodManager = context.getSystemService(
                InputMethodManager::class.java
            )
            this.isAutoHandwritingEnabled = inputMethodManager?.isStylusHandwritingAvailable == true
        }

        this.focusable = FOCUSABLE
        this.isFocusableInTouchMode = true

        setBackgroundColor(Color.LTGRAY)

        layoutParams = ViewGroup.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT,
            LinearLayoutCompat.LayoutParams.MATCH_PARENT
        )
    }


    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val inputConnection = object : BaseInputConnection(this, false) {
            @RequiresApi(34)
            override fun performHandwritingGesture(
                gesture: HandwritingGesture,
                executor: Executor?,
                consumer: IntConsumer?
            ) {
                super.performHandwritingGesture(gesture, executor, consumer)

                Log.d("Write-in-textfield", "${gesture}")

                when {
                    gesture is SelectGesture -> {
                        selectionRectF = (gesture as SelectGesture).selectionArea

                        // https://developer.android.com/reference/android/view/View#startActionMode(android.view.ActionMode.Callback)
                        // create a copy / paste contextual menu
                        this@WriteToCustomView.startActionMode(object : ActionMode.Callback2() {
                            override fun onCreateActionMode(
                                mode: ActionMode?,
                                menu: Menu?
                            ): Boolean {
                                mode?.menuInflater?.inflate(com.example.lowlatencysample.R.menu.copy_paste, menu)
                                return true
                            }

                            override fun onPrepareActionMode(
                                mode: ActionMode?,
                                menu: Menu?
                            ): Boolean {
                                return false
                            }

                            override fun onActionItemClicked(
                                mode: ActionMode?,
                                item: MenuItem?
                            ): Boolean {
                                return when (item?.itemId) {
                                    com.example.lowlatencysample.R.id.copy -> {
                                        val clipboardManager = this@WriteToCustomView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip: ClipData = ClipData.newPlainText("simple text", textSelection)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(this@WriteToCustomView.context, "Copied: $textSelection", Toast.LENGTH_SHORT).show()
                                        mode?.finish()
                                        true
                                    }
                                    com.example.lowlatencysample.R.id.close -> {
                                        mode?.finish()
                                        false
                                    }
                                    else -> {
                                        mode?.finish()
                                        false
                                    }
                                }
                            }

                            override fun onDestroyActionMode(mode: ActionMode?) {
                                // clear selection
                                textSelection = ""
                                selectionRectF = null
                                // request redraw (to remove selection box)
                                invalidate()
                            }

                            override fun onGetContentRect(
                                mode: ActionMode?,
                                view: View?,
                                outRect: Rect?
                            ) {
                                // position the action menu here
                                super.onGetContentRect(mode, view, outRect)
                            }
                        }, ActionMode.TYPE_FLOATING)

                        executor?.execute {
                            this@WriteToCustomView.invalidate()
                            consumer?.accept(HANDWRITING_GESTURE_RESULT_SUCCESS)
                        }
                    }

                    gesture is DeleteGesture -> {
                        deletionRectF = (gesture as DeleteGesture).deletionArea
                        executor?.execute {
                            this@WriteToCustomView.invalidate()
                            consumer?.accept(HANDWRITING_GESTURE_RESULT_SUCCESS)
                        }
                    }

                    gesture is JoinOrSplitGesture -> {
                        val point = gesture.joinOrSplitPoint
                        (gesture as JoinOrSplitGesture).joinOrSplitPoint
                    }

                    gesture is InsertGesture -> {
                        (gesture as InsertGesture).insertionPoint
                        (gesture as InsertGesture).textToInsert
                    }

                    else -> Log.w("Write-in-textfield", "Not recognized")
                }
            }
        }

        if (isWriteInTextviewEnabled) {
            val icw = object : InputConnectionWrapper(inputConnection, false) {
                override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
                    return super.requestCursorUpdates(cursorUpdateMode)
                }

                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    this@WriteToCustomView.text += if (text?.isNotEmpty() == true) {
                        text.toString()
                    } else {
                        ""
                    }
                    val result = super.commitText(text, newCursorPosition)
                    this@WriteToCustomView.invalidate() // trigger canvas-graphic rendering
                    return result
                }
            }


            if (VERSION.SDK_INT >= 34) {
                val supportedGestures: MutableList<Class<out HandwritingGesture?>> = ArrayList()
                supportedGestures.add(SelectGesture::class.java)
                supportedGestures.add(DeleteGesture::class.java)
//                supportedGestures.add(JoinOrSplitGesture::class.java)
//                supportedGestures.add(InsertGesture::class.java)
                outAttrs.supportedHandwritingGestures = supportedGestures
            } else {
                // no-op
            }
            this.post {
                inputMethodManager?.startStylusHandwriting(this)
            }
            return icw
        } else {
            return inputConnection
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()


        if (isWriteInTextviewEnabled) {
            val onTouchListener = OnTouchListener { view, event ->

                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!this.isFocused) {
                            this.post {
                                this.requestFocus()
                            }
                        }
                        inputMethodManager?.restartInput(this)
                    }
                }
                false
            }

            setOnTouchListener(onTouchListener)
        }
    }

    override fun onResolvePointerIcon(event: MotionEvent?, pointerIndex: Int): PointerIcon {
        val isStylus = event?.getToolType(event.actionIndex) == TOOL_TYPE_STYLUS
        return if (isStylus && isWriteInTextviewEnabled) {
            PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HANDWRITING)
        } else {
            PointerIcon.getSystemIcon(context, PointerIcon.TYPE_DEFAULT)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawARGB(0, 225, 225, 255) // White background

        canvas.drawText(this.text, 10f, 120f, paint)

        val letterWidth = 60f // TODO: convert to DPI


        val coordinates = IntArray(2)
        // find the location of the View on the screen
        this.getLocationOnScreen(coordinates)

        selectionRectF?.let {


            // RectF coordinates are relative to the 0,0 top-left corner
            // since the this component is set with a paddingTop,
            // we remove this padding for the vertical values (top and bottom)
            // and for horizontal values (left, right)
            // to obtain the relative value of the RectF to this View
            it.top -= coordinates[1]
            it.bottom -= coordinates[1]
            it.left -= coordinates[0]
            it.right -= coordinates[0]

            canvas.drawRect(it, highlightPaint)
            textSelection = ""

            val startLetterIndex = max(findLetterIndex(letterWidth, it.left), 0)
            val endLetterIndex = min(findLetterIndex(letterWidth, it.right), this.text.length-1)

            textSelection = this.text.substring(startLetterIndex, endLetterIndex)

//            for ((index, c) in this.text.withIndex()) {
//                if ((index + 0.5) * letterWidth >= it.left && (index + 0.5) * letterWidth <= it.right) {
//                    textSelection += c
//                }
//            }
        }

        deletionRectF?.let {
            // RectF coordinates are relative to the 0,0 top-left corner
            // since the this component is set with a paddingTop,
            // we remove this padding for the vertical values (top and bottom)
            // and for horizontal values (left, right)
            // to obtain the relative value of the RectF to this View
            it.top -= coordinates[1]
            it.bottom -= coordinates[1]
            it.left -= coordinates[0]
            it.right -= coordinates[0]

            var newText = ""

            val startLetterIndex = max(findLetterIndex(letterWidth, it.left), 0)
            val endLetterIndex = min(findLetterIndex(letterWidth, it.right), this.text.length-1)

//            for ((index, c) in this.text.withIndex()) {
//                if ((index + 0.5) * letterWidth >= it.left && (index + 0.5) * letterWidth <= it.right) {
//                    // no-op, skip/delete this character
//                } else {
//                    newText += c
//                }
//            }

            newText = this.text.substring(0, startLetterIndex)
            newText += this.text.substring(endLetterIndex, this.text.length-1)

            text = newText
            deletionRectF = null
            invalidate()
        }
    }
}

fun findLetterIndex(letterWidth: Float, pixelPosition: Float): Int {
    return (pixelPosition / letterWidth).roundToInt()
}


