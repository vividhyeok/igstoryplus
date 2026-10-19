package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs

class AlignmentGuides(c:Context):View(c){
    private var vertical=false
    private var horizontal=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(180,212,255,131);strokeWidth=resources.displayMetrics.density}
    fun follow(v:View,parent:View){val center=floatArrayOf(v.width/2f,v.height/2f);v.matrix.mapPoints(center);val d=8*resources.displayMetrics.density;vertical=abs(v.left+center[0]-parent.width/2)<d;horizontal=abs(v.top+center[1]-parent.height/2)<d;invalidate()}
    fun clear(){vertical=false;horizontal=false;invalidate()}
    fun followPoint(x:Float,y:Float){vertical=abs(x-.5f)<.015f;horizontal=abs(y-.5f)<.015f;invalidate()}
    override fun onDraw(c:Canvas){if(vertical)c.drawLine(width/2f,0f,width/2f,height.toFloat(),paint);if(horizontal)c.drawLine(0f,height/2f,width.toFloat(),height/2f,paint)}
}
