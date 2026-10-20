package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.*
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ReplacementSpan
import android.util.LruCache

/** Private characters are document-local image references, persisted with the text layer. */
object InlineEmoji {
    private val images=LruCache<String,Bitmap>(48)
    @Synchronized fun bitmap(c:Context,uri:String):Bitmap?=images.get(uri)?:runCatching{
        Artwork.trimAlpha(Artwork.decode(c,uri,192)).also{images.put(uri,it)}
    }.getOrNull()
    fun token(layer:Layer,uri:String):String {
        layer.inlineEmoji.entries.firstOrNull{it.value==uri}?.let{return it.key}
        val key=(0xE000..0xF8FF).map{it.toChar().toString()}.firstOrNull{it !in layer.inlineEmoji && !layer.text.contains(it)}?:error("이 텍스트의 이모지 개수가 너무 많습니다")
        layer.inlineEmoji[key]=uri;return key
    }
    fun styled(c:Context,layer:Layer):CharSequence=SpannableString(layer.text).also{apply(c,layer,it)}
    fun apply(c:Context,layer:Layer,text:Spannable){
        text.getSpans(0,text.length,ImageGlyph::class.java).forEach{text.removeSpan(it)}
        text.forEachIndexed{index,ch->layer.inlineEmoji[ch.toString()]?.let{uri->bitmap(c,uri)?.let{image->text.setSpan(ImageGlyph(image),index,index+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)}}}
    }
    class ImageGlyph(private val bitmap:Bitmap):ReplacementSpan(){
        private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun getSize(paint:Paint,text:CharSequence,start:Int,end:Int,fm:Paint.FontMetricsInt?):Int {
            if(fm!=null)paint.getFontMetricsInt(fm)
            return (paint.textSize*1.08f).toInt().coerceAtLeast(1)
        }
        override fun draw(canvas:Canvas,text:CharSequence,start:Int,end:Int,x:Float,top:Int,y:Int,bottom:Int,paint:Paint){
            val size=paint.textSize*1.08f;val fm=paint.fontMetrics;val cy=y+(fm.ascent+fm.descent)/2
            val scale=size/maxOf(bitmap.width,bitmap.height);val w=bitmap.width*scale;val h=bitmap.height*scale
            imagePaint.alpha=paint.alpha;canvas.drawBitmap(bitmap,null,RectF(x+(size-w)/2,cy-h/2,x+(size+w)/2,cy+h/2),imagePaint)
        }
    }
}
