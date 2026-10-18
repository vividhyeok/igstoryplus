package com.vividhyeok.igstoryplus

import android.content.*
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.*
import androidx.media3.effect.*
import androidx.media3.transformer.*
import com.google.common.collect.ImmutableList
import java.io.File
import kotlin.math.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Exporter(private val context: Context) {
    private var transformer: Transformer?=null
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var cancelled=false
    @Volatile private var sourceTemp:File?=null
    private var output: File?=null
    fun cancel() { cancelled=true; transformer?.cancel();transformer=null;output?.delete();sourceTemp?.delete() }
    fun export(p: Project, progress: (Int)->Unit, complete: (Result<File>)->Unit) {
        cancelled=false
        val folder=File(context.filesDir,"exports").apply{mkdirs()}
        output=File(folder,"story-${System.currentTimeMillis()}.${if(p.animated())"mp4" else "jpg"}")
        val file=output!!
        Thread {
            try {
                val base=if(p.video) null else Artwork.background(context,p,1080)
                val layers=p.layers.map { it to Artwork.layer(context,it,p.music) }
                val beats=if(p.style.beat>0 && p.music!=null) runCatching{Audio.analyze(context,p.music!!,p.durationMs())}.getOrDefault(floatArrayOf()) else floatArrayOf()
                if(cancelled) return@Thread
                if(!p.animated()) {
                    val b=filteredBitmap(base!!,p.style); val canvas=Canvas(b)
                    layers.forEach { (l,bitmap)-> drawLayer(canvas,l,bitmap,1080,1920) }
                    file.outputStream().use{check(b.compress(Bitmap.CompressFormat.JPEG,92,it))}
                    if(cancelled){file.delete();return@Thread}
                    check(file.length()>0); handler.post{if(!cancelled)complete(Result.success(file))}
                } else {
                    val source=if(p.video)Uri.parse(p.source) else {
                        val temp=File(context.cacheDir,"render-source-${System.nanoTime()}.jpg")
                        sourceTemp=temp
                        temp.outputStream().use{base!!.compress(Bitmap.CompressFormat.JPEG,98,it)}; Uri.fromFile(temp)
                    }
                    handler.post {
                        if(cancelled){sourceTemp?.delete();return@post}
                        runCatching {
                            val effects=baseEffects(context,p,beats).toMutableList()
                            // GLES2 guarantees eight texture units, including the background sampler.
                            layers.chunked(6).forEach{batch->effects+=OverlayEffect(batch.map{(l,b)-> overlay(l,b,p.durationMs()) as TextureOverlay})}
                            val media=MediaItem.Builder().setUri(source).apply {
                                if(!p.video)setImageDurationMs(p.durationMs()) else setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(p.durationMs()).build())
                            }.build()
                            val item=EditedMediaItem.Builder(media).setFrameRate(30).setRemoveAudio(p.music!=null).setEffects(Effects(emptyList(),effects)).build()
                            val seq=mutableListOf(EditedMediaItemSequence.Builder(item).build())
                            p.music?.let { music ->
                                val audio=MediaItem.Builder().setUri(music.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(music.startMs).setEndPositionMs(min(music.startMs+p.durationMs(),music.durationMs)).build()).build()
                                seq+=EditedMediaItemSequence.Builder(EditedMediaItem.Builder(audio).setRemoveVideo(true).build()).setIsLooping(true).build()
                            }
                            val enc=DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(6_000_000).build()).setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(160_000).build()).build()
                            transformer=Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(enc).addListener(object: Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if(source.scheme=="file" && !p.video)File(source.path!!).delete()
                                    if(cancelled){file.delete();return}
                                    runCatching{inspect(file,p)}.also{r->complete(r.map{file})}; transformer=null
                                }
                                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                    if(source.scheme=="file" && !p.video)File(source.path!!).delete()
                                    file.delete(); transformer=null; if(!cancelled)complete(Result.failure(exportException))
                                }
                            }).build()
                            transformer!!.start(Composition.Builder(seq).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build(),file.absolutePath)
                            val poll=object:Runnable{override fun run(){ val t=transformer?:return; val holder=ProgressHolder(); t.getProgress(holder); progress(holder.progress); handler.postDelayed(this,250) }}; handler.post(poll)
                        }.onFailure{file.delete();sourceTemp?.delete();if(!cancelled)complete(Result.failure(it))}
                    }
                }
            }catch(e:Exception){file.delete();sourceTemp?.delete();handler.post{if(!cancelled)complete(Result.failure(e))}}
        }.start()
    }
    companion object {
        fun baseEffects(c:Context,p:Project,beats:FloatArray=floatArrayOf(),preview:Boolean=false):List<Effect> {
            val effects=mutableListOf<Effect>()
            if(p.video){effects+=ScaleAndRotateTransformation.Builder().setRotationDegrees(-p.crop.rotation).setScale(if(p.crop.flip)-1f else 1f,1f).build();effects+=videoCrop(c,p)}
            effects+=Presentation.createForWidthAndHeight(if(preview)540 else 1080,if(preview)960 else 1920,Presentation.LAYOUT_SCALE_TO_FIT)
            effects+=StyleEffect(p.style,p.durationMs(),beats)
            return effects
        }
        fun videoCrop(c:Context,p:Project):androidx.media3.effect.Crop {
            val r=MediaMetadataRetriever()
            try {
                r.setDataSource(c,Uri.parse(p.source))
                var w=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toFloat()
                var h=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toFloat()
                val rotation=(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?:0)+p.crop.rotation.toInt()
                if(abs(rotation)%180==90){val t=w;w=h;h=t}
                val aspect=if(p.crop.aspect>0)p.crop.aspect else w/h
                val cw=min(w,h*aspect)/p.crop.zoom.coerceIn(1f,6f);val ch=cw/aspect
                val x=(p.crop.x*w-cw/2).coerceIn(0f,w-cw);val y=(p.crop.y*h-ch/2).coerceIn(0f,h-ch)
                return androidx.media3.effect.Crop(x/w*2-1,(x+cw)/w*2-1,1-(y+ch)/h*2,1-y/h*2)
            } finally {r.release()}
        }
        fun drawLayer(canvas: Canvas,l: Layer,b: Bitmap,w: Int,h: Int) {
            val s=l.width*w/b.width*l.scale
            canvas.save(); canvas.translate(l.x*w,l.y*h); canvas.rotate(l.rotation); canvas.scale(s,s); canvas.drawBitmap(b,-b.width/2f,-b.height/2f,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); canvas.restore()
        }
        fun overlay(l: Layer,b: Bitmap,durationMs: Long): BitmapOverlay = object:BitmapOverlay(){
            override fun getBitmap(presentationTimeUs: Long)=b
            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                val phase=Motion.phase(presentationTimeUs,durationMs); val pulse=Motion.scale(l,phase)
                val s=l.width*1080/b.width*l.scale*pulse
                return StaticOverlaySettings.Builder().setBackgroundFrameAnchor((l.x*2-1).coerceIn(-1f,1f),((1-l.y-Motion.drift(l,phase))*2-1).coerceIn(-1f,1f)).setScale(s,s).setRotationDegrees(-l.rotation).build()
            }
        }
        fun inspect(f: File,p: Project) {
            check(f.length()>1000){"출력 파일이 비어 있습니다"}
            val r=MediaMetadataRetriever()
            try {
                r.setDataSource(f.absolutePath)
                var width=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                var height=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
                val rotation=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?:0
                if(abs(rotation)%180==90){val temp=width;width=height;height=temp}
                check(width==1080 && height==1920) { "출력 해상도 오류: ${width}×${height} (rotation $rotation)" }
                val ms=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                check(abs(ms-p.durationMs())<350) { "출력 길이 오류: $ms" }
                if(p.music!=null)check(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes"){"오디오 누락"}
            } finally { r.release() }
        }
    }
}
object Sharing {
    fun uri(c: Context,f: File):Uri=FileProvider.getUriForFile(c,"${c.packageName}.files",f)
    fun intent(c: Context,f: File,instagram: Boolean):Intent {
        val u=uri(c,f); val mime=if(f.extension=="mp4")"video/mp4" else "image/jpeg"
        return Intent(if(instagram)"com.instagram.share.ADD_TO_STORY" else Intent.ACTION_SEND).apply {
            setDataAndType(u,mime); putExtra(Intent.EXTRA_STREAM,u); clipData=ClipData.newUri(c.contentResolver,"story",u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if(instagram)setPackage("com.instagram.android")
        }
    }
    fun gallery(c: Context,f: File):Uri {
        val video=f.extension=="mp4"; val values=ContentValues().apply{
            put(MediaStore.MediaColumns.DISPLAY_NAME,f.name); put(MediaStore.MediaColumns.MIME_TYPE,if(video)"video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/igstoryplus" else "Pictures/igstoryplus"); put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val u=c.contentResolver.insert(if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("갤러리 저장 실패")
        try { c.contentResolver.openOutputStream(u)!!.use{o->f.inputStream().use{it.copyTo(o)}}; values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING,0); c.contentResolver.update(u,values,null,null); return u }
        catch(e:Exception){c.contentResolver.delete(u,null,null);throw e}
    }
}
