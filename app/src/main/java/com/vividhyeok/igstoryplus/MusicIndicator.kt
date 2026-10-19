package com.vividhyeok.igstoryplus

import android.graphics.*
import android.graphics.drawable.Drawable

/** The very same indicator drawing is used on the editor ImageView and exported card bitmaps. */
fun drawMusicIndicator(canvas:Canvas,width:Float,height:Float,card:String,progress:Float,beat:Float,dot:Boolean,color:Int){
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color}
    if(card=="Progress Line"){
        val y=height-10;paint.strokeWidth=3f;paint.color=0xFF666A6D.toInt();canvas.drawLine(16f,y,width-16,y,paint)
        paint.color=color;val x=16+(width-32)*progress.coerceIn(0f,1f);canvas.drawLine(16f,y,x,y,paint);canvas.drawCircle(x,y,3.5f,paint)
    }
    if(dot){val radius=(height*.035f).coerceIn(4f,8f);val x=width-radius*2-6;val y=radius*2+6
        paint.color=color;paint.alpha=60;canvas.drawCircle(x,y,radius*(1.5f+beat),paint)
        paint.alpha=255;canvas.drawCircle(x,y,radius*(.8f+beat*.55f),paint)
    }
}
class MusicIndicatorDrawable(private val card:String,private val width:Int,private val height:Int):Drawable(){
    private var progress=0f;private var beat=0f;private var dot=false;private var color=Color.WHITE
    fun update(progress:Float,beat:Float,dot:Boolean,color:Int){if(!dot && !this.dot && card!="Progress Line")return;this.progress=progress;this.beat=beat;this.dot=dot;this.color=color;invalidateSelf()}
    override fun draw(canvas:Canvas){canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width().toFloat()/width,bounds.height().toFloat()/height);drawMusicIndicator(canvas,width.toFloat(),height.toFloat(),card,progress,beat,dot,color);canvas.restore()}
    override fun setAlpha(alpha:Int){}
    override fun setColorFilter(filter:ColorFilter?){}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
