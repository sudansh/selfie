package com.sudansh.library.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import com.google.android.gms.vision.CameraSource
import java.io.IOException

/**
 * Generates camera preview.
 */
class CameraSourcePreview(private val mContext: Context, attrs: AttributeSet) : ViewGroup(mContext, attrs) {
    private val mSurfaceView: SurfaceView
    private var mStartRequested: Boolean = false
    private var mSurfaceAvailable: Boolean = false
    private var mCameraSource: CameraSource? = null
    private var mOverlay: GraphicOverlay? = null

    private val isPortraitMode: Boolean
        get() {
            val orientation = mContext.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return false
            }
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                return true
            }

            return false
        }

    init {
        mStartRequested = false
        mSurfaceAvailable = false

        mSurfaceView = SurfaceView(mContext)
        mSurfaceView.holder.addCallback(SurfaceCallback())
        addView(mSurfaceView)
    }

    @Throws(IOException::class)
    fun start(cameraSource: CameraSource?) {
        if (cameraSource == null) stop()

        mCameraSource = cameraSource

        if (mCameraSource != null) {
            mStartRequested = true
            startIfReady()
        }
    }

    @Throws(IOException::class)
    fun start(cameraSource: CameraSource, overlay: GraphicOverlay) {
        mOverlay = overlay
        start(cameraSource)
    }

    fun stop() {
        mCameraSource?.stop()
    }

    fun release() {
        if (mCameraSource != null) {
            mCameraSource!!.release()
            mCameraSource = null
        }
    }

    @Throws(IOException::class)
    @SuppressLint("MissingPermission")
    private fun startIfReady() {
        if (mStartRequested && mSurfaceAvailable) {
            mCameraSource?.apply {
                start(mSurfaceView.holder)
                mOverlay?.let { mOverlay ->
                    val size = mCameraSource!!.previewSize
                    val min = Math.min(size.width, size.height)
                    val max = Math.max(size.width, size.height)
                    if (isPortraitMode) {
                        // Swap width and height sizes when in portrait, since it will be rotated by
                        // 90 degrees
                        mOverlay.setCameraInfo(min, max, cameraFacing)
                    } else {
                        mOverlay.setCameraInfo(max, min, cameraFacing)
                    }
                    mOverlay.clear()
                }
            }
            mStartRequested = false
        }
    }

    private inner class SurfaceCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(surface: SurfaceHolder) {
            mSurfaceAvailable = true
            try {
                startIfReady()
            } catch (e: IOException) {
                Log.e(TAG, "Could not start camera source.", e)
            }
        }

        override fun surfaceDestroyed(surface: SurfaceHolder) {
            mSurfaceAvailable = false
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var previewWidth = 640
        var previewHeight = 480
        mCameraSource?.let { mCameraSource ->
            mCameraSource.previewSize?.let { size ->
                previewWidth = size.width
                previewHeight = size.height
            }
        }

        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode) {
            val tmp = previewWidth
            previewWidth = previewHeight
            previewHeight = tmp
        }

        val viewWidth = right - left
        val viewHeight = bottom - top

        val childWidth: Int
        val childHeight: Int
        var childXOffset = 0
        var childYOffset = 0
        val widthRatio = viewWidth.toFloat() / previewWidth.toFloat()
        val heightRatio = viewHeight.toFloat() / previewHeight.toFloat()

        // To fill the view with the camera preview, while also preserving the correct aspect ratio,
        // it is usually necessary to slightly oversize the child and to crop off portions along one
        // of the dimensions.  We scale up based on the dimension requiring the most correction, and
        // compute a crop offset for the other dimension.
        if (widthRatio > heightRatio) {
            childWidth = viewWidth
            childHeight = (previewHeight.toFloat() * widthRatio).toInt()
            childYOffset = (childHeight - viewHeight) / 2
        } else {
            childWidth = (previewWidth.toFloat() * heightRatio).toInt()
            childHeight = viewHeight
            childXOffset = (childWidth - viewWidth) / 2
        }

        (0 until childCount).forEach { i ->
            // One dimension will be cropped.  We shift child over or up by this offset and adjust
            // the size to maintain the proper aspect ratio.
            getChildAt(i).layout(
                    -1 * childXOffset, -1 * childYOffset,
                    childWidth - childXOffset, childHeight - childYOffset)
        }

        try {
            startIfReady()
        } catch (e: IOException) {
            Log.e(TAG, "Could not start camera source.", e)
        }

    }

    companion object {
        private const val TAG = "CameraSourcePreview"
    }
}
