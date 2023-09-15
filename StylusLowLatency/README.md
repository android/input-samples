# Low Latency App
## Introduction
The goal of this app is to implement Low Latency APIs from the Graphics Core 
and Motion Prediction Jetpack packages.

- Green line is the current line being drawn to the (front buffered layer)
- White line is the "committed" line (multi buffered layer)

The word *pointer* refers to anything that can act as a pointer, such as:

- a finger
- a stylus
- a mouse


# Low Latency Library Implementation

## Front buffered / Multi-buffered layer concept
### Front buffered layer is used for fast pixels rendering
- It operates when pointer is in contact with the screen (`ACTION_DOWN`, `ACTION_MOVE`)
- It is appropriate for small changes to the display (extending an ink stroke) but not large changes (panning, zooming).
- Rendering can be approximate (prediction)

### Multi buffered layer is used to persist the pixels
- It operates when pointer is lifted (`ACTION_UP`)
- Pixels are accurate, true to user input



## Prerequisites
Low Latency Graphics requires API Level 29+ (Android 10).

## Gradle import
We need to import `graphics-core` into our project

```
📄 app/build.gradle

dependencies {
    implementation 'androidx.graphics:graphics-core:1.0.0-alpha04'
}
```


## GLFrontBufferedRenderer and Callback

`GLFrontBufferedRenderer` will take care of Front and Multi buffered layers. It is in charge of optimizing 
a [SurfaceView](https://developer.android.com/reference/kotlin/android/view/SurfaceView) 
for fast rendering at first on the Front buffered layer (onDrawFrontBufferedLayer). It then will provide data 
for rendering Multi buffered layer (onDrawMultiBufferedLayer)


### Data points
Typically, data points are the coordinates of points that have been touched by the pointer. 
This is up-to-you to define the data type that works for your App.

The [Callbacks](https://developer.android.com/reference/androidx/graphics/lowlatency/GLFrontBufferedRenderer.Callback) 
will use this data type to process and render on the screen.

#### In this app
In this app we are using plain [FloatArray](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float-array/) 
to store our points.

```kotlin
floatarray[0] = x1
floatarray[1] = y1
floatarray[2] = x2
floatarray[3] = y2
...
```

#### Suggestions
There are many data structures that can be used such as:

- [Point](https://developer.android.com/reference/android/graphics/Point)
- [PointF](https://developer.android.com/reference/android/graphics/PointF)
- [FloatArray](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float-array/)
- Custom class. You can store the points coordinates but also other attributes, like color 
  for example. eg: `class MyPoint(x: Float, y: Float, color: Color)`



### Preparing the Callbacks
They are two callbacks that need to be setup.

The first one is for fast-immediate rendering, also called Front buffered layer.
This callback takes a single data point that will be rendered as fast as possible: 
`onDrawFrontBufferedLayer`

The second one is for persistent rendering also called Multi buffered layer.
This call back takes a Collection of data points that will be rendered accurately and for good.


```kotlin
val callbacks = object : GLFrontBufferedRenderer.Callback<DATA_TYPE> {

    override fun onDrawFrontBufferedLayer(
        eglManager: EGLManager,
        bufferInfo: BufferInfo,
        transform: FloatArray,
        param: DATA_TYPE
    ) {
        // OpenGL work for Wet layer, affecting a small area of the screen
    }

    override fun onDrawMultiBufferedLayer(
        eglManager: EGLManager,
        bufferInfo: BufferInfo,
        transform: FloatArray,
        params: Collection<DATA_TYPE>
    ) {
        // OpenGL work for Dry layer
    }
}
```


### Attaching GLFrontBufferedRenderer to SurfaceView
Now need to attach the `SurfaceView` and the callbacks to `GLFrontBufferedRenderer`.

```kotlin
frontBufferRenderer = GLFrontBufferedRenderer(mySurfaceView, callbacks)
```


### Setup setOnTouchListener to collect Data points
Now we need to collect the data points so we can send them to `GLFrontBufferedRenderer`, 
in order to do that we can leverage  `setOnTouchListener` from the `SurfaceView`.

This way whenever there is interactions with the SurfaceView, we can collect and send 
those interactions to  `GLFrontBufferedRenderer`.


Typically, we want to collect single data points when the pointer is down and moving, then the 
points are rendered on by calling  [renderFrontBufferedLayer(dataPoint)](https://developer.android.com/reference/androidx/graphics/lowlatency/GLFrontBufferedRenderer#renderFrontBufferedLayer(kotlin.Any))
We would like to persist those data points when the pointer is up, by calling  [commit()](https://developer.android.com/reference/androidx/graphics/lowlatency/GLFrontBufferedRenderer#commit())


```
mySurfaceView.setOnTouchListener { _, event ->
	when (event.action) {
		MotionEvent.ACTION_MOVE 
		MotionEvent.ACTION_DOWN -> {
			// feed GLFrontBufferedRenderer with single data point of type DATA_TYPE
			val dataPoint = event.toDataPoint()
			// triggers the callback onDrawFrontBufferedLayer
		    frontBufferRenderer?.renderFrontBufferedLayer(dataPoint)
		}
		    
		MotionEvent.ACTION_UP -> {
			// persist all data points
			// triggers the callback onDrawMultiBufferedLayer
			frontBufferRenderer?.commit()
		}
    }
}
```

## This App
This app is for demo and test purposes. You will find the above implementation 
in`LowLatencySurfaceView.kt` and `FastRendering.kt`
We are using `FloatArray` to share data between the `SurfaceView` and `GLFrontBufferedRenderer`
 


# Motion Prediction Library Implementation

The Motion Prediction library helps reduce the delay between touch and screen rendering 
by predicting where the next pixels will be.
It does that by learning about the user input and calculating the future coordinates of the next point.

The Motion Prediction library is using MotionEvent (UP, DOWN and MOVE), to perform the calculation.

## Prerequisites
Motion Prediction requires API Level 19+ (Android 4.4).

## Gradle import
We need to import input-motionprediction into our project. 

```
📄 app/build.gradle
dependencies {
...
implementation 'androidx.input:input-motionprediction:1.0.0-beta01'
}
```

## MotionEventPredictor
MotionEventPredictor is an Interface that will help recording the pointer inputs (MotionEvents) 
and then predict the next MotionEvent.

### Initialization
[MotionEventPredictor](https://developer.android.com/reference/androidx/input/motionprediction/MotionEventPredictor) 
provides the method `newInstance()` that is in charge of returning a `MotionEventPredictor` object.

`newInstance` function takes a `View` as a parameter.

```
class MyView: View {
    ...
    val motionEventPredicor: MotionEventPredicor = MotionEventPredictor.newInstance(this)
    ...
}
```



### Recording MotionEvents

`MotionEventPredictor` records `MotionEvent`, thanks to the [record(motionEvent)](https://developer.android.com/reference/androidx/input/motionprediction/MotionEventPredictor#record(android.view.MotionEvent)) 
method. 
It takes one parameter `MotionEvent` object.

Typically this will be called within an OnTouchListener.

Each MotionEvent is recorded by the predictor so that it can predict the next `MotionEvent`.

```
setOnTouchListener { _, event ->
    motionEventPredictor.record(event)
}
```


### Predicting next MotionEvent
`MotionEventPredictor` predicts the next `MotionEvent` with the [predict()](https://developer.android.com/reference/androidx/input/motionprediction/MotionEventPredictor#predict()) 
method.
It returns a `MotionEvent` that the developer can use to access x and y coordinates.

```
val event = motionEventPredictor.predict()

// event.x
// event.y
// Use the event with your own code
```

### ⚠️ Important note
Predicted events are by definition inaccurate, they do not represent the actual user input and 
therefore developers should differentiate between Predicted and User MotionEvent.
We recommend you “mark” those events so that they are only used for fast rendering.
In this sample app we use `IS_USER_EVENT` and `IS_PREDICTED_EVENT`, 


# Testing
## Simulate stylus
it is possible to set simulate stylus input on a **rooted** device/emulator with the following commands:
```agsl
adb root
adb shell setprop persist.debug.input.simulate_stylus_with_touch true
adb reboot
```
