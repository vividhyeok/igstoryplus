package ja.burhanrashid52.photoeditor

import android.graphics.Rect
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.View.OnTouchListener
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

/**
 * Created on 18/01/2017.
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 *
 *
 */
internal class MultiTouchListener(
    deleteView: View?,
    photoEditorView: PhotoEditorView,
    photoEditImageView: ImageView?,
    private val mIsPinchScalable: Boolean,
    onPhotoEditorListener: OnPhotoEditorListener?,
    viewState: PhotoEditorViewState
) : OnTouchListener {
    private val mGestureListener: GestureDetector
    private val isRotateEnabled = true
    private val isTranslateEnabled = true
    private val isScaleEnabled = true
    private val minimumScale = 0.1f
    private val maximumScale = 10.0f
    private var mActivePointerId = INVALID_POINTER_ID
    private var mPrevX = 0f
    private var mPrevY = 0f
    private var mPrevRawX = 0f
    private var mPrevRawY = 0f
    private var tapDownX=0f
    private var tapDownY=0f
    private var tapCandidate=false
    private var lastTapTime=0L
    private var lastTapX=0f
    private var lastTapY=0f
    private val mScaleGestureDetector: ScaleGestureDetector
    private val location = IntArray(2)
    private var outRect: Rect? = null
    private val deleteView: View?
    private val photoEditImageView: ImageView?
    private val photoEditorView: PhotoEditorView
    private var mOnGestureControl: OnGestureControl? = null
    private val mOnPhotoEditorListener: OnPhotoEditorListener?
    private val viewState: PhotoEditorViewState

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val config=ViewConfiguration.get(view.context)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{tapDownX=event.rawX;tapDownY=event.rawY;tapCandidate=true}
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL->{tapCandidate=false;lastTapTime=0}
            MotionEvent.ACTION_MOVE->{val dx=event.rawX-tapDownX;val dy=event.rawY-tapDownY;if(dx*dx+dy*dy>config.scaledTouchSlop*config.scaledTouchSlop)tapCandidate=false}
        }
        mScaleGestureDetector.onTouchEvent(view, event)
        mGestureListener.onTouchEvent(event)
        if (!isTranslateEnabled) {
            return true
        }
        val action = event.action
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        when (action and event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mPrevX = event.x
                mPrevY = event.y
                mPrevRawX = event.rawX
                mPrevRawY = event.rawY
                mActivePointerId = event.getPointerId(0)
                if (deleteView != null) {
                    deleteView.visibility = View.VISIBLE
                }
                // Selection must not silently change document stacking order. Explicit host actions do.
                viewState.currentSelectedView = view
                firePhotoEditorSDKListener(view, true)
            }
            MotionEvent.ACTION_MOVE ->
                // Only enable dragging on focused stickers.
                if (view === viewState.currentSelectedView) {
                    val pointerIndexMove = event.findPointerIndex(mActivePointerId)
                    if (pointerIndexMove != -1) {
                        val currX = event.getX(pointerIndexMove)
                        val currY = event.getY(pointerIndexMove)
                        if (!mScaleGestureDetector.isInProgress) {
                            adjustTranslation(view, currX - mPrevX, currY - mPrevY)
                        }
                        photoEditorView.onLayerTransform?.invoke(view)
                    }
                }
            MotionEvent.ACTION_CANCEL -> {
                mActivePointerId = INVALID_POINTER_ID
                deleteView?.visibility = View.GONE
                firePhotoEditorSDKListener(view, false)
            }
            MotionEvent.ACTION_UP -> {
                mActivePointerId = INVALID_POINTER_ID
                if (deleteView != null) {
                    deleteView.visibility = View.GONE
                }
                firePhotoEditorSDKListener(view, false)
                // Recognize completed taps using input timestamps. Main-thread/IME scheduling
                // must not turn a valid second tap into selection only. Open after transform
                // completion so the editor is not created in the middle of a gesture.
                val dx=event.rawX-lastTapX;val dy=event.rawY-lastTapY
                if(tapCandidate && event.eventTime-event.downTime<=ViewConfiguration.getLongPressTimeout()){
                    photoEditorView.onLayerTap?.invoke(view)
                    if(lastTapTime>0 && event.eventTime-lastTapTime<=ViewConfiguration.getDoubleTapTimeout() && dx*dx+dy*dy<=config.scaledDoubleTapSlop*config.scaledDoubleTapSlop){
                        lastTapTime=0
                        photoEditorView.onLayerDoubleTap?.invoke(view)
                    }else{lastTapTime=event.eventTime;lastTapX=event.rawX;lastTapY=event.rawY}
                }else lastTapTime=0
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndexPointerUp =
                    action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = event.getPointerId(pointerIndexPointerUp)
                if (pointerId == mActivePointerId) {
                    val newPointerIndex = if (pointerIndexPointerUp == 0) 1 else 0
                    mPrevX = event.getX(newPointerIndex)
                    mPrevY = event.getY(newPointerIndex)
                    mActivePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    private fun firePhotoEditorSDKListener(view: View, isStart: Boolean) {
        photoEditorView.onLayerGesture?.invoke(view, isStart)
        val viewTag = view.tag
        if (mOnPhotoEditorListener != null && viewTag != null && viewTag is ViewType) {
            if (isStart) mOnPhotoEditorListener.onStartViewChangeListener(view.tag as ViewType) else mOnPhotoEditorListener.onStopViewChangeListener(
                view.tag as ViewType
            )
        }
    }

    private fun isViewInBounds(view: View?, x: Int, y: Int): Boolean {
        return view?.run {
            getDrawingRect(outRect)
            getLocationOnScreen(location)
            outRect?.offset(location[0], location[1])
            outRect?.contains(x, y)
        } ?: false
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var mPivotX = 0f
        private var mPivotY = 0f
        private var initialSpan=1f
        private var initialAngle=0f
        private var initialRotation=0f
        private var initialScale=1f
        private var rotationHeld=false
        private var rotationAnchor=0f
        private fun worldSpan(view:View,detector:ScaleGestureDetector):FloatArray{
            val span=detector.getCurrentSpanVector();return floatArrayOf(span.x,span.y).also{view.matrix.mapVectors(it)}
        }
        override fun onScaleBegin(view: View, detector: ScaleGestureDetector): Boolean {
            mPivotX=detector.getFocusX();mPivotY=detector.getFocusY()
            val vector=worldSpan(view,detector)
            initialSpan=kotlin.math.hypot(vector[0],vector[1]).coerceAtLeast(1f)
            initialAngle=kotlin.math.atan2(vector[1],vector[0])*180f/kotlin.math.PI.toFloat()
            initialRotation=view.rotation;initialScale=view.scaleX;rotationHeld=false
            return mIsPinchScalable
        }
        override fun onScale(view: View, detector: ScaleGestureDetector): Boolean {
            // Local MotionEvent coordinates change as the layer rotates/scales. Recover
            // the screen-space span and apply an absolute gesture target, avoiding drift.
            val vector=worldSpan(view,detector)
            val span=kotlin.math.hypot(vector[0],vector[1])
            val angle=kotlin.math.atan2(vector[1],vector[0])*180f/kotlin.math.PI.toFloat()
            val rawRotation=initialRotation+(angle-initialAngle+540)%360-180
            val targetScale=(initialScale*span/initialSpan).coerceIn(minimumScale,maximumScale)
            val info=TransformInfo()
            info.deltaScale=if(isScaleEnabled)targetScale/view.scaleX else 1f
            info.deltaAngle=0f
            info.deltaX=if(isTranslateEnabled)detector.getFocusX()-mPivotX else 0f
            info.deltaY=if(isTranslateEnabled)detector.getFocusY()-mPivotY else 0f
            info.pivotX=mPivotX;info.pivotY=mPivotY;info.minimumScale=minimumScale;info.maximumScale=maximumScale
            move(view,info)
            if(isRotateEnabled){
                val anchor=kotlin.math.round(rawRotation/90)*90
                if(rotationHeld && kotlin.math.abs(rawRotation-rotationAnchor)>7f)rotationHeld=false
                if(!rotationHeld && kotlin.math.abs(rawRotation-anchor)<=4f){rotationHeld=true;rotationAnchor=anchor;view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)}
                view.rotation=if(rotationHeld)rotationAnchor else rawRotation
            }
            return false
        }
    }

    private inner class TransformInfo {
        var deltaX = 0f
        var deltaY = 0f
        var deltaScale = 0f
        var deltaAngle = 0f
        var pivotX = 0f
        var pivotY = 0f
        var minimumScale = 0f
        var maximumScale = 0f
    }

    internal interface OnGestureControl {
        fun onClick()
        fun onLongClick()
    }

    fun setOnGestureControl(onGestureControl: OnGestureControl?) {
        mOnGestureControl = onGestureControl
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // The completed-tap path above owns double taps; do not open twice.
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            mOnGestureControl?.onClick()

            return true
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)
            mOnGestureControl?.onLongClick()
        }
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
        private fun adjustAngle(degrees: Float): Float {
            return when {
                degrees > 180.0f -> {
                    degrees - 360.0f
                }
                degrees < -180.0f -> {
                    degrees + 360.0f
                }
                else -> degrees
            }
        }

        private fun move(view: View, info: TransformInfo) {
            computeRenderOffset(view, info.pivotX, info.pivotY)
            adjustTranslation(view, info.deltaX, info.deltaY)
            var scale = view.scaleX * info.deltaScale
            scale = max(info.minimumScale, min(info.maximumScale, scale))
            view.scaleX = scale
            view.scaleY = scale
            val rotation = adjustAngle(view.rotation + info.deltaAngle)
            view.rotation = rotation
        }

        private fun adjustTranslation(view: View, deltaX: Float, deltaY: Float) {
            val deltaVector = floatArrayOf(deltaX, deltaY)
            view.matrix.mapVectors(deltaVector)
            view.translationX = view.translationX + deltaVector[0]
            view.translationY = view.translationY + deltaVector[1]
        }

        private fun computeRenderOffset(view: View, pivotX: Float, pivotY: Float) {
            if (view.pivotX == pivotX && view.pivotY == pivotY) {
                return
            }
            val prevPoint = floatArrayOf(0.0f, 0.0f)
            view.matrix.mapPoints(prevPoint)
            view.pivotX = pivotX
            view.pivotY = pivotY
            val currPoint = floatArrayOf(0.0f, 0.0f)
            view.matrix.mapPoints(currPoint)
            val offsetX = currPoint[0] - prevPoint[0]
            val offsetY = currPoint[1] - prevPoint[1]
            view.translationX = view.translationX - offsetX
            view.translationY = view.translationY - offsetY
        }
    }

    init {
        mScaleGestureDetector = ScaleGestureDetector(ScaleGestureListener())
        mGestureListener = GestureDetector(photoEditorView.context,GestureListener())
        this.deleteView = deleteView
        this.photoEditorView = photoEditorView
        this.photoEditImageView = photoEditImageView
        mOnPhotoEditorListener = onPhotoEditorListener
        outRect = if (deleteView != null) {
            Rect(
                deleteView.left, deleteView.top,
                deleteView.right, deleteView.bottom
            )
        } else {
            Rect(0, 0, 0, 0)
        }
        this.viewState = viewState
    }
}
