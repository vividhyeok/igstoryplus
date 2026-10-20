package ja.burhanrashid52.photoeditor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import ja.burhanrashid52.photoeditor.FilterImageView.OnImageChangedListener

/**
 *
 *
 * This ViewGroup will have the [DrawingView] to draw paint on it with [ImageView]
 * which our source image
 *
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.1
 * @since 1/18/2018
 */
class PhotoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RelativeLayout(context, attrs, defStyle) {
    /** Host callback for non-destructive document transforms. Added by igstoryplus. */
    var onLayerGesture: ((android.view.View, Boolean) -> Unit)? = null
    var onLayerTransform: ((android.view.View) -> Unit)? = null
    var onLayerDoubleTap: ((android.view.View) -> Unit)? = null
    var onLayerTap: ((android.view.View) -> Unit)? = null
    private var expandedTarget:android.view.View?=null

    /** Give tiny layers a 48dp hit target, without stealing an actual hit on a higher layer.
     * Unlike TouchDelegate's centered events, preserve coordinates for drag/pinch/rotation. */
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean {
        if(!isEnabled)return true
        if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN){
            expandedTarget=null
            val candidates=(childCount-1 downTo 0).map{getChildAt(it)}.filter{it.tag is ViewType && it.visibility==VISIBLE}
            fun local(v:android.view.View):FloatArray{val point=floatArrayOf(event.x-v.left,event.y-v.top);val inverse=android.graphics.Matrix();if(v.matrix.invert(inverse))inverse.mapPoints(point);return point}
            val exact=candidates.any{v->val q=local(v);q[0]>=0 && q[0]<=v.width && q[1]>=0 && q[1]<=v.height}
            if(!exact)expandedTarget=candidates.firstOrNull{v->
                val q=local(v);val target=48*resources.displayMetrics.density
                val px=((target/kotlin.math.abs(v.scaleX).coerceAtLeast(.01f)-v.width)/2).coerceAtLeast(0f)
                val py=((target/kotlin.math.abs(v.scaleY).coerceAtLeast(.01f)-v.height)/2).coerceAtLeast(0f)
                q[0]>=-px && q[0]<=v.width+px && q[1]>=-py && q[1]<=v.height+py
            }
        }
        expandedTarget?.let{target->
            val transformed=android.view.MotionEvent.obtain(event);transformed.offsetLocation(-target.left.toFloat(),-target.top.toFloat())
            val inverse=android.graphics.Matrix();if(target.matrix.invert(inverse))transformed.transform(inverse)
            target.dispatchTouchEvent(transformed);transformed.recycle()
            if(event.actionMasked==android.view.MotionEvent.ACTION_UP || event.actionMasked==android.view.MotionEvent.ACTION_CANCEL)expandedTarget=null
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private var mImgSource: FilterImageView = FilterImageView(context)

    internal var drawingView: DrawingView
        private set

    private var mImageFilterView: ImageFilterView
    private var clipSourceImage = false

    init {
        //Setup image attributes
        val sourceParam = setupImageSource(attrs)
        //Setup GLSurface attributes
        mImageFilterView = ImageFilterView(context)
        val filterParam = setupFilterView()

        mImgSource.setOnImageChangedListener(object : OnImageChangedListener {
            override fun onBitmapLoaded(sourceBitmap: Bitmap?) {
                mImageFilterView.setFilterEffect(PhotoFilter.NONE)
                mImageFilterView.setSourceBitmap(sourceBitmap)
                Log.d(TAG, "onBitmapLoaded() called with: sourceBitmap = [$sourceBitmap]")
            }
        })


        //Setup drawing view
        drawingView = DrawingView(context)
        val brushParam = setupDrawingView()

        //Add image source
        addView(mImgSource, sourceParam)

        //Add Gl FilterView
        addView(mImageFilterView, filterParam)

        //Add brush view
        addView(drawingView, brushParam)
    }

    @SuppressLint("Recycle")
    private fun setupImageSource(attrs: AttributeSet?): LayoutParams {
        mImgSource.id = imgSrcId
        mImgSource.adjustViewBounds = true
        mImgSource.scaleType = ImageView.ScaleType.CENTER_INSIDE

        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.PhotoEditorView)
            val imgSrcDrawable = a.getDrawable(R.styleable.PhotoEditorView_photo_src)
            if (imgSrcDrawable != null) {
                mImgSource.setImageDrawable(imgSrcDrawable)
            }
        }

        var widthParam = ViewGroup.LayoutParams.MATCH_PARENT
        if (clipSourceImage) {
            widthParam = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val params = LayoutParams(
            widthParam, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(CENTER_IN_PARENT, TRUE)
        return params
    }

    private fun setupDrawingView(): LayoutParams {
        drawingView.visibility = GONE
        drawingView.id = shapeSrcId

        // Align drawing view to the size of image view
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(CENTER_IN_PARENT, TRUE)
        params.addRule(ALIGN_TOP, imgSrcId)
        params.addRule(ALIGN_BOTTOM, imgSrcId)
        params.addRule(ALIGN_LEFT, imgSrcId)
        params.addRule(ALIGN_RIGHT, imgSrcId)
        return params
    }

    private fun setupFilterView(): LayoutParams {
        mImageFilterView.visibility = GONE
        mImageFilterView.id = glFilterId

        //Align brush to the size of image view
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(CENTER_IN_PARENT, TRUE)
        params.addRule(ALIGN_TOP, imgSrcId)
        params.addRule(ALIGN_BOTTOM, imgSrcId)
        return params
    }

    /**
     * Source image which you want to edit
     *
     * @return source ImageView
     */
    val source: ImageView
        get() = mImgSource

    internal suspend fun saveFilter(): Bitmap {
        return if (mImageFilterView.visibility == VISIBLE) {
            val saveBitmap = try {
                mImageFilterView.saveBitmap()
            } catch (t: Throwable) {
                throw RuntimeException("Couldn't save bitmap with filter", t)
            }
            mImgSource.setImageBitmap(saveBitmap)
            mImageFilterView.visibility = GONE
            saveBitmap
        } else {
            mImgSource.bitmap!!
        }
    }

    internal fun setFilterEffect(filterType: PhotoFilter) {
        mImageFilterView.visibility = VISIBLE
        mImageFilterView.setFilterEffect(filterType)
    }

    internal fun setFilterEffect(customEffect: CustomEffect?) {
        mImageFilterView.visibility = VISIBLE
        mImageFilterView.setFilterEffect(customEffect)
    }

    internal fun setClipSourceImage(clip: Boolean) {
        clipSourceImage = clip
        val param = setupImageSource(null)
        mImgSource.layoutParams = param
    } // endregion

    companion object {
        private const val TAG = "PhotoEditorView"
        private const val imgSrcId = 1
        private const val shapeSrcId = 2
        private const val glFilterId = 3
    }
}
