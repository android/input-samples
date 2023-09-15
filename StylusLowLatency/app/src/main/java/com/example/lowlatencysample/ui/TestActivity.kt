package com.example.lowlatencysample.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

class TestActivity : Activity() {

  var canvas: CanvasRegularView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
    addCanvas()
  }

  private fun addCanvas() {
    canvas = CanvasRegularView(this).apply {
      layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      id = canvasId
    }

    setContentView(canvas)
  }

  companion object {
    const val canvasId = 100098
  }
}