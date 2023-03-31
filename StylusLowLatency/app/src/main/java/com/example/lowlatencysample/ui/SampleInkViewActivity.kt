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


import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lowlatencysample.data.SampleInkViewModel


class SampleInkViewActivity : ComponentActivity() {

    private val viewModel: SampleInkViewModel by viewModels()

    private lateinit var lowLatencyRenderer: LowLatencyRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lowLatencyRenderer = LowLatencyRenderer(
            LineRenderer(), viewModel
        )

        setContent {
            Box {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    LowLatencyDrawing()
                } else {
                    DeviceNotSupported()
                }
                Text("Version: 2023.03.24", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp), color = Color.White)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Composable
    fun LowLatencyDrawing() {
        val context = LocalContext.current
        val lowLatencySurfaceView =
            remember(context) { LowLatencySurfaceView(context, lowLatencyRenderer) }

        AndroidView(factory = { context ->
            lowLatencySurfaceView
        })
    }

    @Composable
    fun DeviceNotSupported() {
        Text("Low latency requires API 29 (Android 10) or higher")
    }
}