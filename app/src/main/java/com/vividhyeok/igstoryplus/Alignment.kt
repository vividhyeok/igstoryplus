package com.vividhyeok.igstoryplus

import kotlin.math.*

/** Keep raw finger intent separate so a slow drag can leave a snapped guide. */
class AlignmentMagnet(private val enter:Float,private val leave:Float){
    var held=false;private set
    private var target=0f
    fun reset(){held=false}
    fun value(raw:Float,anchor:Float):Float{
        if(held && abs(raw-target)>leave)held=false
        if(!held && abs(raw-anchor)<=enter){held=true;target=anchor}
        return if(held)target else raw
    }
}
fun levelAngle(degrees:Float,tolerance:Float=4f):Float{val target=(degrees/90f).roundToInt()*90f;return if(abs(degrees-target)<=tolerance)target else degrees}
