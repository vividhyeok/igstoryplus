package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

class CropPreview(c:Context,private val original:Bitmap,private val crop:Crop):View(c){
    private var bitmap=original
    private var lastRotation=Float.NaN
    private var lastFlip=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val border=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(130,255,255,255);style=Paint.Style.STROKE;strokeWidth=resources.displayMetrics.density}
    private var factor=1f
    private var previousX=0f
    private var previousY=0f
    private val scale=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(detector:ScaleGestureDetector):Boolean{crop.zoom=(crop.zoom*detector.scaleFactor).coerceIn(1f,6f);invalidate();return true}})
    init{contentDescription="드래그로 크롭 위치, 핀치로 확대";isClickable=true}
    override fun onDraw(canvas:Canvas){
        canvas.drawColor(Color.BLACK)
        if(crop.rotation!=lastRotation || crop.flip!=lastFlip){
            bitmap=Bitmap.createBitmap(original,0,0,original.width,original.height,Matrix().apply{postScale(if(crop.flip)-1f else 1f,1f);postRotate(crop.rotation)},true)
            lastRotation=crop.rotation;lastFlip=crop.flip
        }
        val ratio=if(crop.aspect>0)crop.aspect else bitmap.width.toFloat()/bitmap.height
        val cw=min(bitmap.width.toFloat(),bitmap.height*ratio)/crop.zoom;val ch=cw/ratio
        factor=min(width/cw,height/ch)
        val w=cw*factor;val h=ch*factor;val left=(width-w)/2;val top=(height-h)/2
        val x=(crop.x*bitmap.width-cw/2).coerceIn(0f,bitmap.width-cw);val y=(crop.y*bitmap.height-ch/2).coerceIn(0f,bitmap.height-ch)
        canvas.save();canvas.clipRect(left,top,left+w,top+h);canvas.translate(left-x*factor,top-y*factor);canvas.scale(factor,factor);canvas.drawBitmap(bitmap,0f,0f,paint);canvas.restore()
        canvas.drawRect(left,top,left+w,top+h,border)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{
        scale.onTouchEvent(event)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{previousX=event.x;previousY=event.y;parent.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_MOVE->{if(event.pointerCount==1 && !scale.isInProgress){crop.x=(crop.x-(event.x-previousX)/factor/bitmap.width).coerceIn(0f,1f);crop.y=(crop.y-(event.y-previousY)/factor/bitmap.height).coerceIn(0f,1f);invalidate()};previousX=event.x;previousY=event.y}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{parent.requestDisallowInterceptTouchEvent(false);performClick()}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
