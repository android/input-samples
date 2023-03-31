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

package com.example.lowlatencysample.data

import androidx.lifecycle.ViewModel
import com.example.lowlatencysample.DrawingManager

class SampleInkViewModel : DrawingManager, ViewModel() {

    private val lines: MutableList<FloatArray> = mutableListOf()

    override var isPredictionEnabled: Boolean = true

    override fun saveLines(lines: Collection<FloatArray>) {
        this.lines.addAll(lines)
    }

    override fun getLines(): Collection<FloatArray> {
        return lines
    }
}