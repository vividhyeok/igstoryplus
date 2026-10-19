package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

object Artwork {
    fun decode(context: Context, uri: String, max: Int = 2048): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val s = min(1f, max.toFloat() / max(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width*s).toInt().coerceAtLeast(1), (info.size.height*s).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    fun videoFrame(c: Context, uri: String): Bitmap = MediaMetadataRetriever().let { r ->
        try { r.setDataSource(c, Uri.parse(uri)); r.getFrameAtTime(0) ?: error("영상 프레임을 읽을 수 없습니다") } finally { r.release() }
    }
    fun cropped(b: Bitmap, crop: Crop): Bitmap {
        val matrix = Matrix().apply { postScale(if (crop.flip) -1f else 1f, 1f); postRotate(crop.rotation) }
        val rotated = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
        val aspect = if (crop.aspect > 0) crop.aspect else rotated.width.toFloat()/rotated.height
        var w = min(rotated.width.toFloat(), rotated.height*aspect) / crop.zoom.coerceIn(1f, 6f)
        var h = w/aspect
        w = w.coerceIn(1f, rotated.width.toFloat()); h = h.coerceIn(1f, rotated.height.toFloat())
        val x = (crop.x*rotated.width-w/2).coerceIn(0f, rotated.width-w)
        val y = (crop.y*rotated.height-h/2).coerceIn(0f, rotated.height-h)
        return Bitmap.createBitmap(rotated, x.toInt(), y.toInt(), w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    }
    // Small cached background: convolution is performed only when settings change, never per video frame.
    fun background(c: Context, p: Project, width: Int, drawPhoto:Boolean=true, bounds:(RectF)->Unit = {}): Bitmap {
        val out = Bitmap.createBitmap(width, width*16/9, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out); canvas.drawColor(Color.rgb(25,25,28))
        if (p.source.isBlank()) return out
        val raw = if (p.video) videoFrame(c, p.source) else decode(c, p.source,if(width>=1080)(2048*p.crop.zoom).toInt().coerceAtMost(4096) else 1536)
        val b = cropped(raw, p.crop)
        val small = Bitmap.createBitmap(54,96,Bitmap.Config.ARGB_8888)
        val bgScale=max(54f/b.width,96f/b.height)
        val bgWidth=b.width*bgScale;val bgHeight=b.height*bgScale
        Canvas(small).drawBitmap(b,null,RectF((54-bgWidth)/2,(96-bgHeight)/2,(54+bgWidth)/2,(96+bgHeight)/2),Paint(Paint.FILTER_BITMAP_FLAG))
        val pix = IntArray(54*96); small.getPixels(pix,0,54,0,0,54,96)
        val radius = (p.style.blur*8).toInt().coerceAtLeast(0)
        if (radius > 0) {
            repeat(2) {
                val tmp = pix.copyOf()
                for (y in 0 until 96) for (x in 0 until 54) {
                    var rr=0; var gg=0; var bb=0; var n=0
                    for (dy in -radius..radius) for (dx in -radius..radius) {
                        val color=tmp[(y+dy).coerceIn(0,95)*54+(x+dx).coerceIn(0,53)]
                        rr+=Color.red(color); gg+=Color.green(color); bb+=Color.blue(color); n++
                    }
                    pix[y*54+x]=Color.rgb(rr/n,gg/n,bb/n)
                }
            }
            small.setPixels(pix,0,54,0,0,54,96)
        }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val brightness=p.style.brightness
        paint.colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(brightness,0f,0f,0f,p.style.warmth*15, 0f,brightness,0f,0f,0f, 0f,0f,brightness,0f,-p.style.warmth*15, 0f,0f,0f,1f,0f)))
        canvas.drawBitmap(small,null,Rect(0,0,out.width,out.height),paint)
        paint.colorFilter=null
        val s=min(out.width.toFloat()/b.width,out.height.toFloat()/b.height)
        val w=b.width*s; val h=b.height*s
        bounds(RectF((out.width-w)/2/out.width,(out.height-h)/2/out.height,(out.width+w)/2/out.width,(out.height+h)/2/out.height))
        if(drawPhoto){canvas.save();canvas.translate(p.pose.x*out.width,p.pose.y*out.height);canvas.rotate(p.pose.rotation);canvas.scale(p.pose.scale,p.pose.scale);canvas.drawBitmap(b,null,RectF(-w/2,-h/2,w/2,h/2),paint);canvas.restore()}
        return out
    }
    fun trimAlpha(b: Bitmap): Bitmap {
        val pixels=IntArray(b.width*b.height); b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
        var l=b.width; var t=b.height; var r=0; var d=0
        for (y in 0 until b.height) for (x in 0 until b.width) if (Color.alpha(pixels[y*b.width+x])>8) { l=min(l,x); r=max(r,x); t=min(t,y); d=max(d,y) }
        return if(l<=r && t<=d) Bitmap.createBitmap(b,l,t,r-l+1,d-t+1) else b
    }
    fun layer(c: Context, l: Layer, music: Music?): Bitmap = when(l.kind) {
        "photo" -> cropped(decode(c,l.uri,(l.width*1080*l.scale*l.crop.zoom).toInt().coerceIn(512,4096)),l.crop)
        "png" -> trimAlpha(decode(c,l.uri,(l.width*1080*l.scale).toInt().coerceIn(256,2048)))
        "music" -> musicCard(c,music ?: Music(),l)
        else -> text(c,l)
    }
    fun typeface(l:Layer):Typeface = if(l.font.startsWith("system:"))Typeface.create(l.font.removePrefix("system:"),Typeface.NORMAL) else if(l.font.isNotBlank()) runCatching { Typeface.createFromFile(l.font) }.getOrDefault(Typeface.DEFAULT) else Typeface.create(if(l.type=="Whisper") "sans-serif-light" else "sans-serif-medium",Typeface.NORMAL)
    fun textSize(l:Layer)=if(l.textLayoutVersion>0)88f else 58f
    fun text(c: Context, l: Layer): Bitmap {
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color=l.color; textSize=textSize(l)
            typeface=typeface(l)
            letterSpacing=if(l.type=="Whisper") .13f else .015f
        }
        val layout=StaticLayout.Builder.obtain(l.text,0,l.text.length,p,880).setAlignment(when(l.align){0->Layout.Alignment.ALIGN_NORMAL;2->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_CENTER}).setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE).setIncludePad(true).build()
        val b=Bitmap.createBitmap(960,layout.height+64,Bitmap.Config.ARGB_8888); val canvas=Canvas(b)
        if(l.type=="Soft Label") canvas.drawRoundRect(12f,6f,948f,b.height-6f,28f,28f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb((l.emphasis*180).toInt(),20,20,25) })
        canvas.translate(40f,32f)
        if(l.textLayoutVersion>0 && (l.type=="Offset Type" || l.type=="Echo Type")) p.setShadowLayer(if(l.type=="Echo Type")2f else .01f,2+l.emphasis*7,2+l.emphasis*5,Color.argb((l.emphasis*100).toInt(),Color.red(l.color),Color.green(l.color),Color.blue(l.color)))
        if(l.textLayoutVersion==0 && (l.type=="Offset Type" || l.type=="Echo Type")) {
            canvas.save(); canvas.translate(2+l.emphasis*7,2+l.emphasis*5); p.alpha=(l.emphasis*100).toInt(); layout.draw(canvas); canvas.restore(); p.alpha=255
        }
        if(l.type=="Whisper") p.alpha=220
        layout.draw(canvas); return if(l.textLayoutVersion>0)b else trimAlpha(b)
    }
    fun musicCard(c: Context, m: Music, l: Layer): Bitmap {
        val artOnly=m.card=="앨범 아트"; val textOnly=m.card=="텍스트";val compact=m.card=="작은 카드";val size=if(artOnly)420 else if(compact)680 else 800
        val b=Bitmap.createBitmap(size,if(artOnly)420 else if(compact)150 else 180,Bitmap.Config.ARGB_8888); val cv=Canvas(b)
        cv.clipPath(Path().apply{addRoundRect(0f,0f,b.width.toFloat(),b.height.toFloat(),24f,24f,Path.Direction.CW)})
        cv.drawRoundRect(0f,0f,b.width.toFloat(),b.height.toFloat(),24f,24f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(220,245,245,240) })
        val rect=if(artOnly)Rect(0,0,420,420) else if(compact)Rect(16,16,134,134) else Rect(20,20,160,160)
        if(!textOnly){
            val art=if(m.art.isNotBlank())runCatching{decode(c,m.art,512)}.getOrNull() else null
            if(art!=null){val side=min(art.width,art.height);cv.drawBitmap(art,Rect((art.width-side)/2,(art.height-side)/2,(art.width+side)/2,(art.height+side)/2),rect,Paint(Paint.FILTER_BITMAP_FLAG))}
            else{cv.drawRect(rect,Paint().apply{color=0xFF45494A.toInt()});EditorIcons.drawable("음악",0xFFE8E8E2.toInt()).apply{val pad=rect.width()/4;setBounds(rect.left+pad,rect.top+pad,rect.right-pad,rect.bottom-pad);draw(cv)}}
        }
        if(!artOnly) {
            val x=if(textOnly)26f else if(compact)155f else 185f
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(25,25,25); textSize=if(compact)33f else 38f; typeface=Typeface.DEFAULT_BOLD }
            fun line(value:String)=android.text.TextUtils.ellipsize(value.replace('\n',' '),paint,b.width-x-26,android.text.TextUtils.TruncateAt.END).toString()
            cv.drawText(line(m.title),x,if(compact)65f else 78f,paint); paint.textSize=if(compact)26f else 29f; paint.typeface=Typeface.DEFAULT; paint.color=Color.DKGRAY
            cv.drawText(line(m.artist),x,if(compact)107f else 125f,paint)
        }; return b
    }
}
