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


import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color.YELLOW
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lowlatencysample.R
import com.example.lowlatencysample.brush.BitmapBrushShader
import com.example.lowlatencysample.brush.Brush
import com.example.lowlatencysample.brush.CanvasBitmapBrush
import com.example.lowlatencysample.brush.CanvasBrush
import com.example.lowlatencysample.brush.CanvasSimpleBrush
import com.example.lowlatencysample.brush.GLBrush
import com.example.lowlatencysample.brush.LineGLBrushRenderer
import com.example.lowlatencysample.data.SampleInkViewModel


enum class SurfaceType {
  OPENGL_LOWLATENCY_FRONT_BUFFER,
  CANVAS_LOWLATENCY_FRONT_BUFFER,
  CANVAS_REGULAR, // No-acceleration
  WRITE_IN_TEXTVIEW, // Write-in-textview custom
  WRITE_IN_TEXTVIEW_DELEGATE // Write-in-textview delegate
}

class SampleInkViewActivity : ComponentActivity() {

  private val viewModel: SampleInkViewModel by viewModels()

  // Low latency
  private lateinit var lowLatencyOpenGLRenderer: LowLatencyRendererOpenGL
  private lateinit var lowLatencyCanvasRenderer: CanvasLowLatencyRenderer


  // Note-role
  private var keyguardManager: KeyguardManager? = null

  // AppClip
  private var screenshotLauncher: ActivityResultLauncher<Intent>? = null

  private var isLocked = false
  private var useStylusMode = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    updateOrientation()

    /////// Low latency ///////
    val brushOpenGL: GLBrush = LineGLBrushRenderer()
    val brushCanvas: CanvasBrush = CanvasSimpleBrush()

    lowLatencyOpenGLRenderer = LowLatencyRendererOpenGL(
      brushOpenGL,
      viewModel
    )
    lowLatencyCanvasRenderer = CanvasLowLatencyRenderer(
      brushCanvas,
      viewModel
    )

    /////// Note role ///////
    keyguardManager = this.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    screenshotLauncher = this.registerForActivityResult(
      ActivityResultContracts.StartActivityForResult(),
      this::onScreenShotActivityOnResult
    )

    // Check if the app is the default note app.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
      if (roleManager.isRoleAvailable(RoleManager.ROLE_NOTES)) {
        if (!roleManager.isRoleHeld(RoleManager.ROLE_NOTES)) {
          // invite user to set the app as default in settings
          viewModel.showDefaultNoteAppDialog()
        }
      }
    }

    if ("android.intent.action.CREATE_NOTE" == intent.action) {
      isLocked = keyguardManager?.isKeyguardLocked ?: false
      // when stylus mode is enable, make sure you display a stylus friendly UI
      // like a canvas
      useStylusMode = intent.getBooleanExtra("android.intent.extra.USE_STYLUS_MODE", false)
    }


    setContent {
      SurfaceSelector()
      DefaultNoteAppDialog()
      Box(modifier = Modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

          when (viewModel.canvasSurface.value) {
            SurfaceType.CANVAS_REGULAR -> {
              RegularCanvasDrawing()
            }

            SurfaceType.CANVAS_LOWLATENCY_FRONT_BUFFER -> {
              LowLatencyCanvasDrawing()
            }

            SurfaceType.OPENGL_LOWLATENCY_FRONT_BUFFER -> {
              LowLatencyOpenGLDrawing()
            }

            SurfaceType.WRITE_IN_TEXTVIEW -> {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                WriteToCustomView()
              } else {
                Text("Write-in-textview requires Android 14 (U)",
                  color = MaterialTheme.colors.secondary)
              }
            }

            SurfaceType.WRITE_IN_TEXTVIEW_DELEGATE -> {
              if (Build.VERSION.SDK_INT >= 34) {
                WriteInTextViewDelegate()
              } else {
                Text("Write-in-textview requires Android 14 (U)",
                  color = MaterialTheme.colors.secondary)
              }
            }
          }
        } else {
          DeviceNotSupported()
        }
        Row(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val textButton =
            if (viewModel.canvasSurface.value == SurfaceType.OPENGL_LOWLATENCY_FRONT_BUFFER) {
              "OpenGL"
            } else {
              "Canvas"
            }
          val textBottom = currentRendererName()
          Text(
            "Version: 2023.11.14 - $textBottom",
            color = MaterialTheme.colors.secondary,
            modifier = Modifier.padding(end = 8.dp)
          )
          Button(onClick = {
            viewModel.showSurfaceSelector.value = true
          }) {
            Text("Select Surface")
          }
        }

        ButtonBar(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(end = 8.dp)
        )
      }
    }
  }

  private fun currentRendererName(): String {
    return when (viewModel.canvasSurface.value) {
      SurfaceType.OPENGL_LOWLATENCY_FRONT_BUFFER -> "OpenGL LowLatency"
      SurfaceType.CANVAS_LOWLATENCY_FRONT_BUFFER -> "Canvas LowLatency FB"
      SurfaceType.CANVAS_REGULAR -> "Canvas Regular"
      SurfaceType.WRITE_IN_TEXTVIEW -> "Write-in-Textfield custom"
      SurfaceType.WRITE_IN_TEXTVIEW_DELEGATE -> "Write-in-Textfield delegate"
    }
  }

  private fun updateOrientation() {
    val display = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay

    viewModel.orientation = this.resources.configuration.orientation //display.rotation
    viewModel.displayRotation = display.rotation
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    updateOrientation()
  }

  @Composable
  fun SurfaceSelector() {
    if (viewModel.showSurfaceSelector.value) {
      AlertDialog(onDismissRequest = { viewModel.showSurfaceSelector.value = false },
        buttons = { },
        text = {
          Column {
            Button(onClick = {
              viewModel.switchSurfaceTo(SurfaceType.OPENGL_LOWLATENCY_FRONT_BUFFER)
            }) {
              Text(text = "OpenGL")
            }
            Button(onClick = {
              viewModel.switchSurfaceTo(SurfaceType.CANVAS_LOWLATENCY_FRONT_BUFFER)
            }) {
              Text(text = "Canvas Low Latency FB")
            }
            Button(onClick = {
              viewModel.switchSurfaceTo(SurfaceType.CANVAS_REGULAR)
            }) {
              Text(text = "Canvas Regular")
            }
            Button(onClick = {
              viewModel.switchSurfaceTo(SurfaceType.WRITE_IN_TEXTVIEW)
            }) {
              Text(text = "Write in Textview custom")
            }
            Button(onClick = {
              viewModel.switchSurfaceTo(SurfaceType.WRITE_IN_TEXTVIEW_DELEGATE)
            }) {
              Text(text = "Write in Textview Delegate")
            }
          }
        })
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  @Composable
  fun LowLatencyOpenGLDrawing() {
    val context = LocalContext.current
    val GLLowLatencySurfaceView =
      remember(context) {
        GLLowLatencySurfaceView(
          context,
          lowLatencyOpenGLRenderer as LowLatencyRenderer<Brush>
        )
      }

    AndroidView(factory = {
      GLLowLatencySurfaceView
    })
  }

  @Composable
  fun ButtonBar(modifier: Modifier) {
    val showInkToText = viewModel.showInkToText.collectAsState(initial = false)
    val showLineStyles = viewModel.showLineStyles.collectAsState(initial = false)
    Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (showLineStyles.value) {
        Button(onClick = {
          val options = BitmapFactory.Options().apply {
            inScaled = false
          }
          if (viewModel.canvasSurface.value == SurfaceType.CANVAS_LOWLATENCY_FRONT_BUFFER) {
            lowLatencyCanvasRenderer.updateBrush(
              CanvasBitmapBrush(
                BitmapFactory.decodeResource(
                  resources,
                  R.drawable.spray_brush,
                  options
                ), 50f
              )
            )
          } else if (viewModel.canvasSurface.value == SurfaceType.OPENGL_LOWLATENCY_FRONT_BUFFER) {
            val brushOpenGL =
              BitmapBrushShader(
                BitmapFactory.decodeResource(
                  resources,
                  R.drawable.spray_brush,
                  options
                )
              ).apply { size = 25f }
            lowLatencyOpenGLRenderer.updateBrush(brushOpenGL)
          }
        }) {
          Text("Spray")
        }
        Button(onClick = {
          if (viewModel.canvasSurface.value == SurfaceType.CANVAS_LOWLATENCY_FRONT_BUFFER) {
            lowLatencyCanvasRenderer.updateBrush(CanvasSimpleBrush())
          } else if (viewModel.canvasSurface.value == SurfaceType.OPENGL_LOWLATENCY_FRONT_BUFFER) {
            lowLatencyOpenGLRenderer.updateBrush(LineGLBrushRenderer())
          }
        }) {
          Text("Line")
        }
      }

      if (showInkToText.value) {
        Button(onClick = {
          viewModel.inkToText()
        }) {
          Text("Ink to Text")
        }
        Text(
          viewModel.inkToText.value,
          modifier = Modifier.padding(start = 8.dp),
          color = MaterialTheme.colors.secondary
        )
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  @Composable
  fun LowLatencyCanvasDrawing() {
    val context = LocalContext.current
    val GLLowLatencySurfaceView =
      remember(context) {
        GLLowLatencySurfaceView(
          context,
          lowLatencyCanvasRenderer as LowLatencyRenderer<Brush>
        )
      }

    AndroidView(factory = {
      GLLowLatencySurfaceView
    })
  }

  @Composable
  fun RegularCanvasDrawing() {
    val context = LocalContext.current
    val canvasRegularView =
      remember(context) {
        CanvasRegularView(
          context
        )
      }

    AndroidView(factory = {
      canvasRegularView
    })
  }

  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  @Composable
  fun WriteToCustomView() {
    val context = LocalContext.current
    val writeToCustomView =
      remember(context) {
        WriteToCustomView(
          context
        )
      }

    AndroidView(
      factory = {
        writeToCustomView
      }, modifier = Modifier
        .padding(top = 50.dp)
        .width(400.dp)
        .height(100.dp)
    )
  }

  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  @Composable
  fun WriteInTextViewExtendedBounds() {
    val context = LocalContext.current
    val editTextBounds = remember(context) {
      EditText(context).apply {
        layoutParams = ViewGroup.LayoutParams(300, 100)
        //setBackgroundColor(LTGRAY)
        setHandwritingBoundsOffsets(30f, 20f, 30f, 20f)
      }
    }

    AndroidView(factory = {
      editTextBounds
    })
  }

  @Composable
  fun DeviceNotSupported() {
    Text("Low latency requires API 29 (Android 10) or higher")
  }

  @Composable
  private fun DefaultNoteAppDialog() {
    if (viewModel.showAskDefaultDialog.value) {
      AlertDialog(
        onDismissRequest = { viewModel.hideAskDefaultDialog() },
        title = { Text("Default note app") },
        text = { Text("You can set NoteTakingRoleSample to be your default note app.\nYou will be able to launch NoteTakingRoleSample from the lock screen.") },
        confirmButton = {
          TextButton(onClick = {
            viewModel.hideAskDefaultDialog()
            openDefaultSettings()
          }) {
            Text("Open Settings")
          }
        },
        dismissButton = {
          TextButton(onClick = { viewModel.hideAskDefaultDialog() }) {
            Text("Cancel")
          }
        },
      )
    }
  }

  /**
   * launch the default app setting panel
   */
  private fun openDefaultSettings() {
    val intent = Intent()
    intent.setAction(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    startActivity(intent)
  }

  /**
   * when the system has taken the screenshot, it will provide a response
   * the response if successful will provide a URI to the bitmap file of the screenshot
   */
  private fun onScreenShotActivityOnResult(result: ActivityResult) {
    val data = result.data
    if (result.resultCode !== RESULT_OK) {
      return
    }

    val statusCode =
      data?.getIntExtra("android.intent.extra.CAPTURE_CONTENT_FOR_NOTE_STATUS_CODE", -1)

    when (statusCode) {
      Intent.CAPTURE_CONTENT_FOR_NOTE_WINDOW_MODE_UNSUPPORTED -> ""
      Intent.CAPTURE_CONTENT_FOR_NOTE_USER_CANCELED -> ""
      Intent.CAPTURE_CONTENT_FOR_NOTE_SUCCESS -> {
        val uri = data.data ?: return
        // do something with the URI
        Log.d("APP CLIP", "URI: $uri statusCode: $statusCode")
      }

      Intent.CAPTURE_CONTENT_FOR_NOTE_FAILED -> ""
      Intent.CAPTURE_CONTENT_FOR_NOTE_BLOCKED_BY_ADMIN -> ""
    }
  }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
fun WriteInTextViewDelegate() {
  val context = LocalContext.current
  val editTextDelegate = remember(context) {
    EditText(context).apply {
      visibility = View.GONE
      setBackgroundColor(YELLOW)
    }
  }
  val fakeTextView =
    remember(context) {
      View(
        context
      ).apply {
        layoutParams = ViewGroup.LayoutParams(300, 100)
        setBackgroundColor(YELLOW)
        try {
          setHandwritingDelegatorCallback {
            editTextDelegate.visibility = View.VISIBLE
            editTextDelegate.requestFocus()
            this.visibility = View.GONE
          }
          editTextDelegate.setIsHandwritingDelegate(true)
        } catch (e: LinkageError) {
          // Running on a device with an older build of Android U
          e.printStackTrace()
        }
      }
    }

  editTextDelegate.setOnFocusChangeListener { v, hasFocus ->
    if(!hasFocus) {
      // When Edit text loose focus, we revert to FakeTextView
      v.visibility = View.GONE
      fakeTextView.visibility = View.VISIBLE
    }
  }

  val linearLayout = remember(context) {
    LinearLayout(context).apply {
      orientation = HORIZONTAL
      addView(fakeTextView)
      addView(editTextDelegate)
    }
  }

  AndroidView(factory = {
    linearLayout
  })
}