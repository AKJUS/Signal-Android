package org.thoughtcrime.securesms.ringrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper
import org.webrtc.CapturerObserver
import org.webrtc.EglBase
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import kotlin.math.max

/**
 * Captures the device screen via [MediaProjection] and forwards frames to a
 * [CapturerObserver] sink.
 */
class ScreenShareCapturer(
  private val context: Context,
  private val eglBase: EglBaseWrapper,
  private val sink: CapturerObserver
) {

  private var screenCapturer: ScreenCapturerAndroid? = null
  private var surfaceHelper: SurfaceTextureHelper? = null
  private var captureWidth: Int = 0
  private var captureHeight: Int = 0
  var isCapturing: Boolean = false
    private set

  fun start(mediaProjectionData: Intent) {
    if (isCapturing) {
      Log.w(TAG, "Already capturing")
      return
    }

    Log.i(TAG, "start()")
    isCapturing = true

    eglBase.performWithValidEglBase { base: EglBase? ->
      screenCapturer = ScreenCapturerAndroid(
        mediaProjectionData,
        object : MediaProjection.Callback() {
          override fun onStop() {
            Log.i(TAG, "MediaProjection stopped")
          }
        }
      )

      val (width, height) = computeCaptureDimensions()
      captureWidth = width
      captureHeight = height

      Log.i(TAG, "start(): capture dimensions " + width + "x" + height)

      surfaceHelper = SurfaceTextureHelper.create("WebRTC-ScreenShareHelper", base!!.getEglBaseContext())
      screenCapturer!!.initialize(surfaceHelper, context, sink)
      screenCapturer!!.startCapture(width, height, FRAME_RATE)
    }
  }

  fun onConfigurationChanged() {
    if (!isCapturing) return

    val (width, height) = computeCaptureDimensions()
    if (width == captureWidth && height == captureHeight) {
      return
    }

    Log.i(TAG, "onConfigurationChanged(): capture dimensions " + width + "x" + height)
    captureWidth = width
    captureHeight = height
    screenCapturer?.changeCaptureFormat(width, height, FRAME_RATE)
  }

  private fun computeCaptureDimensions(): Pair<Int, Int> {
    val metrics = context.resources.displayMetrics
    var width = metrics.widthPixels
    var height = metrics.heightPixels

    val maxDimension = max(width, height)
    if (maxDimension > MAX_DIMENSION) {
      val scale = MAX_DIMENSION.toFloat() / maxDimension
      width = (width * scale).toInt()
      height = (height * scale).toInt()
    }

    // Encoders require even dimensions
    width = width and 1.inv()
    height = height and 1.inv()

    return width to height
  }

  fun stop() {
    if (!isCapturing) {
      return
    }

    Log.i(TAG, "stop()")

    if (screenCapturer != null) {
      screenCapturer!!.stopCapture()
      screenCapturer!!.dispose()
      screenCapturer = null
    }

    if (surfaceHelper != null) {
      surfaceHelper!!.dispose()
      surfaceHelper = null
    }

    captureWidth = 0
    captureHeight = 0
    isCapturing = false
  }

  fun dispose() {
    stop()
  }

  companion object {
    private val TAG = tag(ScreenShareCapturer::class.java)

    private const val MAX_DIMENSION = 1280
    private const val FRAME_RATE = 15
  }
}
