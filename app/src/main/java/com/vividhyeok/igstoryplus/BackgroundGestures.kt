package com.vividhyeok.igstoryplus

import android.graphics.RectF
import android.view.*
import kotlin.math.*

/** Attached only to PhotoEditor's source: children keep first refusal for every gesture. */
class BackgroundGestures(private val view:View,private val project:()->Project,private val bounds:()->RectF,
                         private val begin:()->Unit,private val changed:()->Unit,private val end:()->Unit,
                         private val tap:(Boolean)->Unit,private val doubleTap:()->Unit):View.OnTouchListener {
    private var active=false;private var moving=false;private var x=0f;private var y=0f;private var distance=0f;private var angle=0f
    private var downX=0f;private var downY=0f
    private val slop=ViewConfiguration.get(view.context).scaledTouchSlop
    private val detector=GestureDetector(view.context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent)=true
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean{if(!moving)tap(hit(e.x,e.y));return true}
        override fun onDoubleTap(e:MotionEvent):Boolean{doubleTap();return true}
    })
    private fun hit(px:Float,py:Float):Boolean{
        val pose=project().pose;val r=bounds();val a=-pose.rotation*PI/180
        val dx=px-pose.x*view.width;val dy=py-pose.y*view.height
        val qx=(dx*cos(a)-dy*sin(a))/pose.scale;val qy=(dx*sin(a)+dy*cos(a))/pose.scale
        return abs(qx)<r.width()*view.width/2 && abs(qy)<r.height()*view.height/2
    }
    override fun onTouch(v:View,e:MotionEvent):Boolean{
        detector.onTouchEvent(e)
        val n=e.pointerCount
        val cx=if(n>1)(e.getX(0)+e.getX(1))/2 else e.x
        val cy=if(n>1)(e.getY(0)+e.getY(1))/2 else e.y
        val d=if(n>1)hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0)) else 0f
        val a=if(n>1)atan2(e.getY(1)-e.getY(0),e.getX(1)-e.getX(0))*180f/PI.toFloat() else 0f
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{active=project().source.isNotBlank() && hit(cx,cy);moving=false;downX=cx;downY=cy;x=cx;y=cy}
            MotionEvent.ACTION_POINTER_DOWN->{x=cx;y=cy;distance=d;angle=a}
            MotionEvent.ACTION_MOVE->if(active){
                if(!moving && (hypot(cx-downX,cy-downY)>slop || n>1)){moving=true;begin();v.parent.requestDisallowInterceptTouchEvent(true)}
                if(moving){val pose=project().pose;pose.x=(pose.x+(cx-x)/v.width).coerceIn(0f,1f);pose.y=(pose.y+(cy-y)/v.height).coerceIn(0f,1f)
                    if(n>1 && distance>0){pose.scale=(pose.scale*d/distance).coerceIn(.25f,6f);val delta=(a-angle+540)%360-180;pose.rotation=(pose.rotation+delta)%360}
                    changed()
                };x=cx;y=cy;distance=d;angle=a
            }
            MotionEvent.ACTION_POINTER_UP->{val remaining=if(e.actionIndex==0)1 else 0;x=e.getX(remaining);y=e.getY(remaining);distance=0f}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(moving){val p=project().pose;if(abs(p.x-.5f)<.015f)p.x=.5f;if(abs(p.y-.5f)<.015f)p.y=.5f;if(abs(p.rotation%90)<3)p.rotation=(p.rotation/90).roundToInt()*90f;changed();end()};active=false;v.parent.requestDisallowInterceptTouchEvent(false)}
        }
        return true
    }
}
