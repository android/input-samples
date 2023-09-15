package com.example.lowlatencysample

import com.example.lowlatencysample.ui.findLetterIndex
import junit.framework.TestCase.assertEquals
import org.junit.Test

class LetterTest {


  @Test
  fun findLetterIndexTest() {
    var index = findLetterIndex(60f, 63f)
    assertEquals(1, index)

    index = findLetterIndex(60f, 89f)
    assertEquals(1, index)

    index = findLetterIndex(60f, 90f)
    assertEquals(2, index)

    index = findLetterIndex(60f, 91f)
    assertEquals(2, index)
  }
}