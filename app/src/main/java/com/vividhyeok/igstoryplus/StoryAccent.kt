package com.vividhyeok.igstoryplus

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.*

/** Stable silhouette anchors and smooth envelopes; identical drawing in preview and export. */
class StoryAccent(private val source:Bitmap){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val points:List<PointF>
    init{
        val candidates=ArrayList<PointF>();val step=max(2,source.width/100)
        fun alpha(x:Int,y:Int)=if(x in 0 until source.width && y in 0 until source.height)Color.alpha(source.getPixel(x,y))else 0
        for(y in 0 until source.height step step)for(x in 0 until source.width step step){if(alpha(x,y)>100 && (alpha(x-step,y)<50 || alpha(x+step,y)<50 || alpha(x,y-step)<50 || alpha(x,y+step)<50))candidates.add(PointF(x.toFloat(),y.toFloat()))}
        points=if(candidates.isEmpty())emptyList()else List(min(24,candidates.size)){candidates[it*candidates.size/min(24,candidates.size)]}
    }
    fun draw(c:Canvas,layer:Layer,timeUs:Long,durationMs:Long,beat:Float){
        if(layer.accent=="없음" || layer.accentAmount<=0)return
        val phase=Motion.phase(timeUs,durationMs).toDouble();val amount=layer.accentAmount.coerceIn(0f,1f)
        points.forEachIndexed{i,p->
            val glow=((sin(phase*2+i*2.39996)+1)*.5).pow(3).toFloat()
            val energy=(glow*.65f+beat.coerceIn(0f,1f)*.35f).coerceIn(0f,1f)
            val radius=(min(source.width,source.height)*.035f).coerceIn(4f,13f)*(.6f+.4f*energy)
            val x=p.x.coerceIn(radius, (source.width-radius).coerceAtLeast(radius));val y=p.y.coerceIn(radius,(source.height-radius).coerceAtLeast(radius))
            paint.style=Paint.Style.FILL;paint.color=if(layer.accent=="하트 글리터")0xFFFFA7C9.toInt() else 0xFFFFE9AB.toInt();paint.alpha=(220*amount*energy).toInt()
            if(layer.accent=="하트 글리터"){
                path.reset();path.moveTo(x,y+radius*.8f);path.cubicTo(x-radius*1.8f,y-radius*.3f,x-radius*.7f,y-radius*1.4f,x,y-radius*.5f);path.cubicTo(x+radius*.7f,y-radius*1.4f,x+radius*1.8f,y-radius*.3f,x,y+radius*.8f);c.drawPath(path,paint)
            }else{
                path.reset();path.moveTo(x,y-radius);path.lineTo(x+radius*.23f,y-radius*.23f);path.lineTo(x+radius,y);path.lineTo(x+radius*.23f,y+radius*.23f);path.lineTo(x,y+radius);path.lineTo(x-radius*.23f,y+radius*.23f);path.lineTo(x-radius,y);path.lineTo(x-radius*.23f,y-radius*.23f);path.close();c.drawPath(path,paint)
            }
            paint.color=Color.WHITE;paint.alpha=(210*amount*energy).toInt();c.drawCircle(x,y,radius*.16f,paint)
        }
    }
}
class StoryAccentDrawable(private val source:Bitmap,private val layer:Layer):Drawable(){
    private val renderer=StoryAccent(source);private var time=0L;private var duration=5000L;private var beat=0f
    fun update(timeUs:Long,durationMs:Long,beat:Float){time=timeUs;duration=durationMs;this.beat=beat;if(layer.accent!="없음")invalidateSelf()}
    override fun draw(c:Canvas){c.save();c.translate(bounds.left.toFloat(),bounds.top.toFloat());c.scale(bounds.width().toFloat()/source.width,bounds.height().toFloat()/source.height);renderer.draw(c,layer,time,duration,beat);c.restore()}
    override fun setAlpha(alpha:Int){}
    override fun setColorFilter(filter:ColorFilter?){}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
