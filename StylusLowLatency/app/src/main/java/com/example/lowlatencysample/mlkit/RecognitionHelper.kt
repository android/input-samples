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
package com.example.lowlatencysample.mlkit

import android.util.Log
import com.example.lowlatencysample.brush.Brush
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*

class RecognitionHelper {

    private lateinit var modelIdentifier: DigitalInkRecognitionModelIdentifier
    private val remoteModelManager = RemoteModelManager.getInstance()
    lateinit var recognizer : DigitalInkRecognizer
    lateinit var inkModel: DigitalInkRecognitionModel

    fun initModel(): Task<Void> {
        try {
            modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!
        } catch (e: MlKitException) {
            Log.e(this.javaClass.name, "Language tag failed to parse, handle error", e)
        }
        if (modelIdentifier == null) {
            Log.d(this.javaClass.name, "No model was found, handle error")
        }
        inkModel =
            DigitalInkRecognitionModel.builder(modelIdentifier).build()

        return remoteModelManager.download(inkModel, DownloadConditions.Builder().build())
            .addOnSuccessListener {

                Log.i(this.javaClass.name, "Model downloaded")
                // Get a recognizer for the language
                recognizer =
                    DigitalInkRecognition.getClient(
                        DigitalInkRecognizerOptions.builder(inkModel).build())
            }
            .addOnFailureListener { e: Exception ->
                Log.e(this.javaClass.name, "Error while downloading a model: $e")
            }
    }

    fun recognizeInk(ink : Ink) : Task<RecognitionResult> = recognizer.recognize(ink)

    fun recognizeInkFromLines(lines: Collection<FloatArray>, callback: com.google.android.gms.tasks.OnSuccessListener<RecognitionResult>) {
        val inkBuilder = Ink.builder()
        val strokeBuilder = Ink.Stroke.builder()

        for (line in lines) {
            for (i in 0 until line.size step Brush.DATA_STRUCTURE_SIZE) {
                strokeBuilder.addPoint(
                    Ink.Point.create(line[i + Brush.X1_INDEX], line[i + Brush.Y1_INDEX])
                )
                strokeBuilder.addPoint(
                    Ink.Point.create(line[i + Brush.X2_INDEX], line[i + Brush.Y2_INDEX])
                )
            }
        }

        inkBuilder.addStroke(strokeBuilder.build())

        val ink = inkBuilder.build()

        this.recognizeInk(ink).addOnSuccessListener(callback)
    }

}