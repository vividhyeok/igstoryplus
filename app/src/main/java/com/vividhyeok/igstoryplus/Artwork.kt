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
        val src = if(EmojiLibrary.bundled(uri))ImageDecoder.createSource(context.assets,uri.removePrefix("file:///android_asset/")) else ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
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
        "photo" -> cropped(decode(c,l.uri,(l.width*1080*l.scale*l.crop.zoom).toInt().coerceIn(512,4096)),l.crop).let{b->l.photoStyle?.let{filteredBitmap(b,it.copy(motion=false,beat=0f))}?:b}
        "png" -> decode(c,l.uri,(l.width*1080*l.scale).toInt().coerceIn(256,2048)).let{if(EmojiLibrary.bundled(l.uri))it else trimAlpha(it)}
        "music" -> musicCard(c,music ?: Music(),l)
        else -> text(c,l)
    }
    fun typeface(l:Layer):Typeface = if(l.font.startsWith("system:"))Typeface.create(l.font.removePrefix("system:"),Typeface.NORMAL) else if(l.font.isNotBlank()) runCatching { Typeface.createFromFile(l.font) }.getOrDefault(Typeface.DEFAULT) else Typeface.create(when(l.type){"Whisper","Margin Note","네온"->"sans-serif-light";"Mono Stamp","타자기"->"monospace";"Editorial Serif"->"serif";"모던"->"sans-serif-condensed";"굵게"->"sans-serif-black";else->"sans-serif-medium"},if(l.type=="굵게")Typeface.BOLD_ITALIC else Typeface.NORMAL)
    fun textSize(l:Layer)=(if(l.textLayoutVersion>0)88f else 58f)*when(l.type){"Film Caption"->.73f;"Mono Stamp"->.86f;"Editorial Serif"->1.18f;"Margin Note"->.82f;else->1f}
    fun letterSpacing(l:Layer)=when(l.type){"Whisper"->.13f;"Mono Stamp"->.09f;"Film Caption"->.035f;"Editorial Serif"->.002f;else->.015f}
    fun textInk(l:Layer):Int=if(l.textBackground=="채움"){if(Color.red(l.color)*.2126+Color.green(l.color)*.7152+Color.blue(l.color)*.0722>150)Color.BLACK else Color.WHITE}else l.color
    fun textDecoration(canvas:Canvas,l:Layer,height:Int,layout:Layout?=null){
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        if(l.textBackground!="기존"){
            if(l.textBackground=="없음" || layout==null)return
            paint.color=if(l.textBackground=="반투명")0xA6000000.toInt() else l.color
            if(l.textBackground=="라인"){paint.style=Paint.Style.STROKE;paint.strokeWidth=3f}
            for(line in 0 until layout.lineCount){
                if(layout.getLineRight(line)-layout.getLineLeft(line)<1f)continue
                val rect=RectF(40+layout.getLineLeft(line)-16,32+layout.getLineTop(line)-3f,40+layout.getLineRight(line)+16,32+layout.getLineBottom(line)+3f)
                canvas.drawRoundRect(rect,14f,14f,paint)
            };return
        }
        if(l.type=="Soft Label" || l.type=="Mono Stamp"){
            paint.color=if(l.type=="Mono Stamp")Color.argb(185,20,24,27) else Color.argb((l.emphasis*180).toInt(),20,20,25)
            canvas.drawRoundRect(12f,6f,948f,height-6f,if(l.type=="Mono Stamp")4f else 28f,if(l.type=="Mono Stamp")4f else 28f,paint)
        }
        if(l.type=="Margin Note"){paint.color=l.color;paint.alpha=150;paint.strokeWidth=2f;canvas.drawLine(22f,32f,22f,height-32f,paint)}
        if(l.type=="Editorial Serif"){paint.color=l.color;paint.alpha=100;paint.strokeWidth=1.5f;canvas.drawLine(40f,height-14f,920f,height-14f,paint)}
    }
    fun text(c: Context, l: Layer): Bitmap {
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color=textInk(l); textSize=textSize(l)
            typeface=typeface(l)
            letterSpacing=letterSpacing(l)
            if(l.type=="Film Caption")setShadowLayer(4f,0f,2f,Color.BLACK)
            if(l.type=="네온")setShadowLayer(18f,0f,0f,l.color)
        }
        val layout=StaticLayout.Builder.obtain(l.text,0,l.text.length,p,880).setAlignment(when(l.align){0->Layout.Alignment.ALIGN_NORMAL;2->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_CENTER}).setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE).setIncludePad(true).build()
        val b=Bitmap.createBitmap(960,layout.height+64,Bitmap.Config.ARGB_8888); val canvas=Canvas(b)
        textDecoration(canvas,l,b.height,layout)
        canvas.translate(40f,32f)
        if(l.textLayoutVersion>0 && (l.type=="Offset Type" || l.type=="Echo Type")) p.setShadowLayer(if(l.type=="Echo Type")2f else .01f,2+l.emphasis*7,2+l.emphasis*5,Color.argb((l.emphasis*100).toInt(),Color.red(l.color),Color.green(l.color),Color.blue(l.color)))
        if(l.textLayoutVersion==0 && (l.type=="Offset Type" || l.type=="Echo Type")) {
            canvas.save(); canvas.translate(2+l.emphasis*7,2+l.emphasis*5); p.alpha=(l.emphasis*100).toInt(); layout.draw(canvas); canvas.restore(); p.alpha=255
        }
        if(l.type=="Whisper") p.alpha=220
        if(l.type=="외곽선"){val ink=p.color;p.style=Paint.Style.STROKE;p.strokeWidth=7f;p.color=Color.BLACK;layout.draw(canvas);p.style=Paint.Style.FILL;p.color=ink}
        layout.draw(canvas); return if(l.textLayoutVersion>0)b else trimAlpha(b)
    }
    fun musicCard(c: Context, m: Music, l: Layer): Bitmap {
        if(m.card in listOf("스토리","커버","투명"))return storyMusicCard(c,m)
        if(m.card in listOf("Mini Credit","Cover Caption","Mono Tag","Progress Line"))return newMusicCard(c,m)
        val artOnly=m.card=="앨범 아트"; val textOnly=m.card=="텍스트";val full=m.card=="기본 카드";val compact=m.card=="작은 카드";val size=if(artOnly)420 else if(full)480 else if(compact)680 else 800
        val b=Bitmap.createBitmap(size,if(artOnly)420 else if(full)600 else if(compact)150 else 180,Bitmap.Config.ARGB_8888); val cv=Canvas(b)
        cv.clipPath(Path().apply{addRoundRect(0f,0f,b.width.toFloat(),b.height.toFloat(),24f,24f,Path.Direction.CW)})
        cv.drawRoundRect(0f,0f,b.width.toFloat(),b.height.toFloat(),24f,24f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(220,245,245,240) })
        val rect=if(artOnly)Rect(0,0,420,420) else if(full)Rect(30,30,450,450) else if(compact)Rect(16,16,134,134) else Rect(20,20,160,160)
        if(!textOnly){
            val art=if(m.art.isNotBlank())runCatching{decode(c,m.art,512)}.getOrNull() else null
            if(art!=null){val side=min(art.width,art.height);cv.drawBitmap(art,Rect((art.width-side)/2,(art.height-side)/2,(art.width+side)/2,(art.height+side)/2),rect,Paint(Paint.FILTER_BITMAP_FLAG))}
            else{cv.drawRect(rect,Paint().apply{color=0xFF45494A.toInt()});EditorIcons.drawable("음악",0xFFE8E8E2.toInt()).apply{val pad=rect.width()/4;setBounds(rect.left+pad,rect.top+pad,rect.right-pad,rect.bottom-pad);draw(cv)}}
        }
        if(!artOnly) {
            val x=if(full)30f else if(textOnly)26f else if(compact)155f else 185f
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(25,25,25); textSize=if(compact)33f else 38f; typeface=Typeface.DEFAULT_BOLD }
            fun line(value:String)=android.text.TextUtils.ellipsize(value.replace('\n',' '),paint,b.width-x-26,android.text.TextUtils.TruncateAt.END).toString()
            cv.drawText(line(m.title),x,if(full)506f else if(compact)65f else 78f,paint); paint.textSize=if(compact)26f else 29f; paint.typeface=Typeface.DEFAULT; paint.color=Color.DKGRAY
            cv.drawText(line(m.artist),x,if(full)555f else if(compact)107f else 125f,paint)
        }; return b
    }
    private fun storyMusicCard(c:Context,m:Music):Bitmap{
        val cover=m.card=="커버";val transparent=m.card=="투명";val width=if(cover)480 else 640;val height=if(cover)558 else 180
        val b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);val paint=TextPaint(Paint.ANTI_ALIAS_FLAG)
        if(!transparent){paint.color=Color.WHITE;canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),18f,18f,paint)}
        if(!transparent){
            val target=if(cover)RectF(16f,16f,464f,464f) else RectF(22f,22f,158f,158f)
            canvas.save();canvas.clipPath(Path().apply{addRoundRect(target,10f,10f,Path.Direction.CW)})
            val art=runCatching{decode(c,m.art,512)}.getOrNull()
            if(art!=null){val side=min(art.width,art.height);canvas.drawBitmap(art,Rect((art.width-side)/2,(art.height-side)/2,(art.width+side)/2,(art.height+side)/2),target,Paint(Paint.FILTER_BITMAP_FLAG))}
            else {paint.color=0xFFDDDDE3.toInt();canvas.drawRect(target,paint);EditorIcons.drawable("음악",0xFF73737C.toInt()).apply{setBounds((target.left+target.width()*.28f).toInt(),(target.top+target.height()*.28f).toInt(),(target.right-target.width()*.28f).toInt(),(target.bottom-target.height()*.28f).toInt());draw(canvas)}}
            canvas.restore()
        }
        val x=if(cover)24f else if(transparent)26f else 184f
        paint.color=if(transparent)Color.WHITE else Color.BLACK;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=if(cover)30f else 38f
        if(transparent)paint.setShadowLayer(3f,0f,1f,Color.BLACK)
        fun line(value:String)=android.text.TextUtils.ellipsize(value.replace('\n',' '),paint,width-x-24,android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(line(m.title),x,if(cover)505f else 79f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=if(cover)25f else 30f;paint.color=if(transparent)0xEEFFFFFF.toInt() else 0xFF55555D.toInt()
        canvas.drawText(line(m.artist),x,if(cover)540f else 124f,paint)
        return b
    }
    fun palette(c:Context,uri:String):Int{
        if(uri.isBlank())return 0xFF98B8CF.toInt()
        val image=decode(c,uri,32);var r=0;var g=0;var b=0;var count=0
        for(y in 0 until image.height)for(x in 0 until image.width){val color=image.getPixel(x,y);if(Color.alpha(color)>128){r+=Color.red(color);g+=Color.green(color);b+=Color.blue(color);count++}}
        if(count==0)return 0xFF98B8CF.toInt()
        return Color.rgb((r/count+40).coerceAtMost(230),(g/count+40).coerceAtMost(230),(b/count+40).coerceAtMost(230))
    }
    private fun newMusicCard(c:Context,m:Music):Bitmap{
        val cover=m.card=="Cover Caption";val mono=m.card=="Mono Tag";val mini=m.card=="Mini Credit"
        val width=if(cover)480 else if(mini)600 else 680;val height=if(cover)558 else if(mini)104 else if(mono)118 else 172
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG)
        if(!cover){paint.color=if(mono)0xD5222529.toInt() else 0xEF24272B.toInt();canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),if(mono)5f else 18f,if(mono)5f else 18f,paint)}
        val artRect=if(cover)Rect(24,0,456,432) else if(mini)Rect(12,12,92,92) else Rect(16,16,136,136)
        if(!mono){val art=if(m.art.isBlank())null else runCatching{decode(c,m.art,512)}.getOrNull()
            if(art!=null){val side=min(art.width,art.height);canvas.drawBitmap(art,Rect((art.width-side)/2,(art.height-side)/2,(art.width+side)/2,(art.height+side)/2),artRect,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))}
            else{paint.color=0xFF59636B.toInt();canvas.drawRect(artRect,paint);EditorIcons.drawable("음악").apply{setBounds(artRect.left+18,artRect.top+18,artRect.right-18,artRect.bottom-18);draw(canvas)}}
        }
        val x=if(cover)24f else if(mono)26f else if(mini)108f else 154f
        paint.color=Color.WHITE;paint.textSize=if(cover)29f else if(mini)28f else if(mono)28f else 34f;paint.typeface=if(mono)Typeface.MONOSPACE else Typeface.create("sans-serif-medium",Typeface.NORMAL)
        if(cover)paint.setShadowLayer(3f,0f,1f,Color.BLACK)
        fun line(value:String)=android.text.TextUtils.ellipsize(value.replace('\n',' '),paint,width-x-30,android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(line(m.title),x,if(cover)479f else if(mini)43f else 54f,paint)
        paint.textSize=if(mini)22f else if(mono)23f else 25f;paint.color=0xFFD0D3D6.toInt();paint.typeface=if(mono)Typeface.MONOSPACE else Typeface.DEFAULT
        canvas.drawText(line(m.artist),x,if(cover)524f else if(mini)77f else 96f,paint)
        if(mono){paint.color=0xFF98B8CF.toInt();canvas.drawRect(12f,24f,15f,96f,paint)}
        if(m.card=="Progress Line"){paint.color=0xFF666A6D.toInt();paint.strokeWidth=3f;canvas.drawLine(16f,height-10f,width-16f,height-10f,paint)}
        return bitmap
    }

}
