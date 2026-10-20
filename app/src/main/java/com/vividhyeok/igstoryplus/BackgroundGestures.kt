package com.vividhyeok.igstoryplus

import android.graphics.RectF
import android.view.*
import kotlin.math.*

/** Attached only to PhotoEditor's source: children keep first refusal for every gesture. */
class BackgroundGestures(private val view:View,private val project:()->Project,private val bounds:()->RectF,
                         private val begin:()->Unit,private val changed:()->Unit,private val end:()->Unit,
                         private val tap:(Boolean)->Unit,private val doubleTap:()->Unit,private val enabled:()->Boolean={true}):View.OnTouchListener {
    private var active=false;private var moving=false;private var x=0f;private var y=0f;private var distance=0f;private var angle=0f
    private var downX=0f;private var downY=0f
    private val slop=ViewConfiguration.get(view.context).scaledTouchSlop
    private val density=view.resources.displayMetrics.density
    private val mx=AlignmentMagnet(9*density,16*density);private val my=AlignmentMagnet(9*density,16*density);private val mr=AlignmentMagnet(4f,7f)
    private var rawX=0f;private var rawY=0f;private var rawAngle=0f;private var originX=0f;private var originY=0f;private var axis=0
    private val detector=GestureDetector(view.context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent)=true
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean{if(enabled() && !moving)tap(hit(e.x,e.y));return true}
        override fun onDoubleTap(e:MotionEvent):Boolean{if(enabled())doubleTap();return true}
    })
    private fun hit(px:Float,py:Float):Boolean{
        val pose=project().pose;val r=bounds();val a=-pose.rotation*PI/180
        val dx=px-pose.x*view.width;val dy=py-pose.y*view.height
        val qx=(dx*cos(a)-dy*sin(a))/pose.scale;val qy=(dx*sin(a)+dy*cos(a))/pose.scale
        return abs(qx)<r.width()*view.width/2 && abs(qy)<r.height()*view.height/2
    }
    override fun onTouch(v:View,e:MotionEvent):Boolean{
        if(!enabled()){active=false;moving=false;val cancel=MotionEvent.obtain(e);cancel.action=MotionEvent.ACTION_CANCEL;detector.onTouchEvent(cancel);cancel.recycle();return true}
        detector.onTouchEvent(e)
        val n=e.pointerCount
        val cx=if(n>1)(e.getX(0)+e.getX(1))/2 else e.x
        val cy=if(n>1)(e.getY(0)+e.getY(1))/2 else e.y
        val d=if(n>1)hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0)) else 0f
        val a=if(n>1)atan2(e.getY(1)-e.getY(0),e.getX(1)-e.getX(0))*180f/PI.toFloat() else 0f
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{active=project().source.isNotBlank() && hit(cx,cy);moving=false;downX=cx;downY=cy;x=cx;y=cy;val p=project().pose;rawX=p.x*view.width;rawY=p.y*view.height;originX=rawX;originY=rawY;rawAngle=p.rotation;axis=0;mx.reset();my.reset();mr.reset()}
            MotionEvent.ACTION_POINTER_DOWN->{x=cx;y=cy;distance=d;angle=a;axis=3}
            MotionEvent.ACTION_MOVE->if(active){
                if(!moving && (hypot(cx-downX,cy-downY)>slop || n>1)){moving=true;begin();v.parent.requestDisallowInterceptTouchEvent(true)}
                if(moving){val pose=project().pose;rawX=(rawX+cx-x).coerceIn(0f,v.width.toFloat());rawY=(rawY+cy-y).coerceIn(0f,v.height.toFloat())
                    if(axis==0){val dx=abs(cx-downX);val dy=abs(cy-downY);axis=if(dx>dy*2.5f)1 else if(dy>dx*2.5f)2 else 3}
                    if(axis==1 && abs(rawY-originY)>24*density)axis=3
                    if(axis==2 && abs(rawX-originX)>24*density)axis=3
                    val was=mx.held||my.held||mr.held
                    pose.x=mx.value(if(axis==2)originX else rawX,v.width/2f)/v.width;pose.y=my.value(if(axis==1)originY else rawY,v.height/2f)/v.height
                    if(n>1 && distance>0){pose.scale=(pose.scale*d/distance).coerceIn(.25f,6f);val delta=(a-angle+540)%360-180;rawAngle+=delta;pose.rotation=mr.value(rawAngle,(rawAngle/90).roundToInt()*90f)}
                    if(!was && (mx.held||my.held||mr.held))v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    changed()
                };x=cx;y=cy;distance=d;angle=a
            }
            MotionEvent.ACTION_POINTER_UP->{val remaining=if(e.actionIndex==0)1 else 0;x=e.getX(remaining);y=e.getY(remaining);distance=0f}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(moving){project().pose.rotation=levelAngle(project().pose.rotation);changed();end()};active=false;v.parent.requestDisallowInterceptTouchEvent(false)}
        }
        return true
    }
}
