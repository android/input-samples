package com.example.lowlatencysample

import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lowlatencysample.ui.CanvasRegularView
import com.example.lowlatencysample.ui.TestActivity
import junit.framework.TestCase
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class CanvasDrawingEspressoTest {

  private var scenario: ActivityScenario<TestActivity>? = null

  @Test
  fun canvasDrawingTest() {
    scenario = ActivityScenario.launch(TestActivity::class.java)
      .moveToState(Lifecycle.State.CREATED)
      .moveToState(Lifecycle.State.RESUMED)
      .onActivity {
        // assert the canvas is clean and transparent
        TestCase.assertEquals(
          Color.TRANSPARENT,
          it.canvas?.exportBitmap()?.getPixel(10, 10)
        )
      }

    Espresso.onView(withId(100098))
      .perform(clickXY(0, 0)) // help to focus the canvas
      .perform(drawUShapeWithStylus(10f, 10f)) // draw a U the canvas
      .check { view, noViewFoundException ->
        assert(view is CanvasRegularView)
        val bmp = (view as CanvasRegularView).exportBitmap()
        // Verify that the shape was drawn on the canvas by checking
        // the color of a set of pixels
        TestCase.assertEquals(Color.BLACK, bmp.getPixel(10, 10))
        TestCase.assertEquals(Color.BLACK, bmp.getPixel(10, 200))
        TestCase.assertEquals(Color.BLACK, bmp.getPixel(200, 200))
        TestCase.assertEquals(Color.BLACK, bmp.getPixel(200, 10))
      }
  }

  @After
  fun cleanup() {
    scenario?.close()
  }

  private fun drawUShapeWithStylus(x: Float, y: Float): ViewAction? {
    return object : ViewAction {
      override fun getConstraints(): Matcher<View> {
        return isDisplayed()
      }

      override fun getDescription(): String {
        return "Send stylus events to draw a U shape"
      }

      override fun perform(uiController: UiController, view: View) {
        // Get view absolute position
        val location = IntArray(2)
        view.getLocationOnScreen(location)

        // Offset coordinates by view position
        val coordinates = floatArrayOf(
          location[0] + x,
          location[1] + y
        )

        val precision = floatArrayOf(1f, 1f)
        val inputDevice = MotionEvent.TOOL_TYPE_STYLUS
        val buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY
        val uSize = 200f

        val moveCoordinates0 = floatArrayOf(
          location[0] + x,
          location[1] + y + uSize, // down
        )
        val moveCoordinates1 = floatArrayOf(
          location[0] + x + uSize,
          location[1] + y + uSize, // right
        )
        val moveCoordinates2 = floatArrayOf(
          location[0] + x + uSize,
          location[1] + y , // up
        )

        val pauseMs = 200L

        val down =
          MotionEvents.sendDown(uiController, coordinates, precision, inputDevice, buttonState).down
        uiController.loopMainThreadForAtLeast(pauseMs)
        MotionEvents.sendMovement(uiController, down, coordinates)
        uiController.loopMainThreadForAtLeast(pauseMs)
        MotionEvents.sendMovement(uiController, down, moveCoordinates0)
        uiController.loopMainThreadForAtLeast(pauseMs)
        MotionEvents.sendMovement(uiController, down, moveCoordinates1)
        uiController.loopMainThreadForAtLeast(pauseMs)
        MotionEvents.sendMovement(uiController, down, moveCoordinates2)
        uiController.loopMainThreadForAtLeast(pauseMs)
        MotionEvents.sendUp(uiController, down, moveCoordinates2)
        uiController.loopMainThreadForAtLeast(pauseMs)
      }
    }
  }

  private fun clickXY(x: Int, y: Int): ViewAction? {

    return GeneralClickAction(
      Tap.SINGLE,
      { view ->
        // Get view absolute position
        val location = IntArray(2)
        view.getLocationOnScreen(location)

        // Offset coordinates by view position
        val coordinates = floatArrayOf(
          x.toFloat() + location[0],
          y.toFloat() + location[1]
        )
        coordinates
      },
      Press.PINPOINT,
      MotionEvent.TOOL_TYPE_STYLUS,
      MotionEvent.BUTTON_PRIMARY
    )
  }
}