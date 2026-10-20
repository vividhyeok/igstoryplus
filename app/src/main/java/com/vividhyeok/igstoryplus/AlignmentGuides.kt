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
    private var angle:Float?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(180,212,255,131);strokeWidth=resources.displayMetrics.density}
    fun follow(v:View,parent:View){val center=floatArrayOf(v.width/2f,v.height/2f);v.matrix.mapPoints(center);val d=10*resources.displayMetrics.density;vertical=abs(v.left+center[0]-parent.width/2)<d;horizontal=abs(v.top+center[1]-parent.height/2)<d;angle=v.rotation;invalidate()}
    fun clear(){vertical=false;horizontal=false;angle=null;invalidate()}
    fun followPoint(x:Float,y:Float,rotation:Float=0f){vertical=abs(x-.5f)*width<10*resources.displayMetrics.density;horizontal=abs(y-.5f)*height<10*resources.displayMetrics.density;angle=rotation;invalidate()}
    override fun onDraw(c:Canvas){
        if(vertical)c.drawLine(width/2f,0f,width/2f,height.toFloat(),paint);if(horizontal)c.drawLine(0f,height/2f,width.toFloat(),height/2f,paint)
        angle?.let{value->val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xBB000000.toInt()};val d=resources.displayMetrics.density;val x=width/2f;val y=height*.13f;c.drawRoundRect(x-38*d,y-16*d,x+38*d,y+16*d,16*d,16*d,p);p.color=if(levelAngle(value)!=value || value%90f==0f)paint.color else Color.WHITE;p.textSize=13*d;p.textAlign=Paint.Align.CENTER;c.drawText(String.format(java.util.Locale.ROOT,"%.1f°",value),x,y+5*d,p)
            if(abs(value-kotlin.math.round(value/90)*90)<=4f){c.drawLine(x-48*d,height/2f,x+48*d,height/2f,paint)}
        }
    }
}
