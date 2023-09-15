package com.example.lowlatencysample

import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.test.annotation.ExperimentalTestApi
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lowlatencysample.ui.CanvasRegularView
import junit.framework.TestCase.assertEquals

class CanvasDrawingComposeTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun sendTouchEvent(view: View, action: Int, x: Float, y: Float) {
    val downTime = SystemClock.uptimeMillis()
    val motionEvent1 = MotionEvent.obtain(downTime, downTime, action, x, y, 0)
    motionEvent1.source = InputDevice.SOURCE_STYLUS

    view.dispatchTouchEvent(motionEvent1)
    motionEvent1.recycle()
  }


  @OptIn(ExperimentalTestApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
  @ExperimentalComposeUiApi
  @Test
  fun canvasDrawingTest() {

    val testTag = "writeintextfield"

    var targetView: CanvasRegularView? = null

    composeTestRule.setContent {
      val context = LocalContext.current

      val canvas = remember(context) {
        CanvasRegularView(context).apply {
          layoutParams = ViewGroup.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT,
            LinearLayoutCompat.LayoutParams.MATCH_PARENT
          )
        }
      }

      AndroidView(factory = {
        canvas
      }, modifier = Modifier.testTag(testTag))

      targetView = canvas

    }

    val base = 60f

    composeTestRule.waitForIdle()

    // assert the canvas is blank/transparent
    assertEquals(Color.TRANSPARENT, targetView?.exportBitmap()?.getPixel(base.toInt() + 10, base.toInt() + 10))

    composeTestRule.runOnIdle {
      targetView?.let {

        sendTouchEvent(it, MotionEvent.ACTION_DOWN, base + 10.0f, base + 10.0f) // life the pen
        sendTouchEvent(it, MotionEvent.ACTION_MOVE, base + 10.0f, base + 200.0f) // move down
        sendTouchEvent(it, MotionEvent.ACTION_MOVE, base + 200.0f, base + 200.0f) // move right
        sendTouchEvent(it, MotionEvent.ACTION_MOVE, base + 200.0f, base + 10.0f) // move up
        sendTouchEvent(it, MotionEvent.ACTION_UP, base + 200.0f, base + 10.0f) // lift the pen
      }
    }

    composeTestRule.waitForIdle()
    // assert a black line has been drawn on the canvas
    assertEquals(Color.BLACK, targetView?.exportBitmap()?.getPixel(base.toInt() + 10, base.toInt() + 10))

  }
}