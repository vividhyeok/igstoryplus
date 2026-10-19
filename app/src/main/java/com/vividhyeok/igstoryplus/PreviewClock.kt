package com.vividhyeok.igstoryplus

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs
import kotlin.math.min

/** Decoder/GPU-only preview: never creates a movie or starts an encoder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PreviewClock(private val context:Context,private val project:()->Project,
                   private val photo:LivePhoto,private val video:()->ExoPlayer?,
                   private val views:()->Map<String,View>,private val state:(Boolean)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var audio:ExoPlayer?=null
    private var time=0L
    private var stamp=0L
    private var beats=floatArrayOf()
    private var beatKey=""
    private var generation=0
    private var transformed=false
    private var restartPending=false
    private val restart=Runnable{restartPending=false;time=0;video()?.seekTo(0);start()}
    var playing=false;private set
    val positionMs:Long get()=time
    var onPosition:((Long)->Unit)?=null
    private val tick=object:Runnable{override fun run(){
        if(!playing)return
        val p=project();val now=SystemClock.elapsedRealtime()
        time=if(p.video)video()?.currentPosition?:time else if(audio?.playbackState==Player.STATE_READY && p.music?.loop!=true)audio!!.currentPosition.coerceAtMost(p.durationMs()-1) else (time+now-stamp)%p.durationMs();stamp=now
        p.music?.let{m->val span=min(p.durationMs(),m.durationMs-m.startMs).coerceAtLeast(1)
            audio?.let{a->val expected=time%span;if(abs(a.currentPosition-expected)>180)a.seekTo(expected)}}
        photo.showTime(time*1000,beatAt(beats,time*1000))
        val phase=Motion.phase(time*1000,p.durationMs())
        transformed=true
        p.layers.forEach{l->views()[l.id]?.let{v->v.scaleX=baseScale(l,v)*Motion.scale(l,phase);v.scaleY=v.scaleX;v.translationY=(l.y+Motion.drift(l,phase))*(v.parent as View).height-v.top-v.height/2f}}
        onPosition?.invoke(time)
        handler.postDelayed(this,33)
    }}
    fun toggle(){if(playing)pause() else start()}
    private fun start(){
        if(playing)return
        handler.removeCallbacks(restart);restartPending=false
        val p=project();if(p.source.isBlank())return
        if(p.music?.let{!MusicRange.valid(it,p.durationMs())}==true)return
        photo.durationMs=p.durationMs()
        if(p.music==null){beats=floatArrayOf();beatKey="";generation++}
        playing=true;state(true);stamp=SystemClock.elapsedRealtime()
        video()?.apply{volume=p.sourceVolume.coerceIn(0f,1f);play()}
        p.music?.let{m->runCatching{
            audio=ExoPlayer.Builder(context).build().apply{
                volume=m.volume.coerceIn(0f,1f)
                setMediaItem(MediaItem.Builder().setUri(m.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(m.startMs).setEndPositionMs(min(m.startMs+p.durationMs(),m.durationMs)).build()).build())
                repeatMode=Player.REPEAT_MODE_ONE;prepare();seekTo(time%min(p.durationMs(),m.durationMs-m.startMs).coerceAtLeast(1));play()
            }
            analyzeBeats()
        }}
        handler.post(tick)
    }
    private fun analyzeBeats(){
        val p=project();val m=p.music?:return;val key="${m.uri}:${m.startMs}:${p.durationMs()}:${m.loop}"
        if(p.style.beat>0 && key!=beatKey){beatKey=key;beats=floatArrayOf();val token=++generation;val snapshot=m.copy();val duration=p.durationMs()
            Thread{val result=runCatching{Audio.analyze(context.applicationContext,snapshot,duration)};handler.post{if(token==generation){result.onSuccess{beats=it;if(project().video)video()?.setVideoEffects(Exporter.baseEffects(context,project(),beats,preview=true))}.onFailure{beatKey="";android.widget.Toast.makeText(context,"음악 반응을 분석하지 못했습니다. 음악 파일을 다시 선택해 주세요.",android.widget.Toast.LENGTH_LONG).show()}}}}.start()
        }
    }
    fun updateStyle(){val p=project();photo.durationMs=p.durationMs();photo.style=p.style.copy();analyzeBeats();if(p.video)video()?.setVideoEffects(Exporter.baseEffects(context,p,beats,preview=true))}
    fun setVolume(volume:Float){audio?.volume=volume.coerceIn(0f,1f)}
    fun setSourceVolume(volume:Float){video()?.volume=volume.coerceIn(0f,1f)}
    fun seek(position:Long){
        val p=project();time=position.coerceIn(0,(p.durationMs()-1).coerceAtLeast(0));stamp=SystemClock.elapsedRealtime()
        video()?.seekTo(time);audio?.seekTo(time%min(p.durationMs(),p.music?.let{it.durationMs-it.startMs}?:p.durationMs()).coerceAtLeast(1))
        photo.showTime(time*1000,beatAt(beats,time*1000));val phase=Motion.phase(time*1000,p.durationMs());transformed=true
        p.layers.forEach{l->views()[l.id]?.let{v->v.scaleX=baseScale(l,v)*Motion.scale(l,phase);v.scaleY=v.scaleX;v.translationY=(l.y+Motion.drift(l,phase))*(v.parent as View).height-v.top-v.height/2f}}
        onPosition?.invoke(time)
    }
    fun pausePlayback(){pause()}
    fun play(){start()}
    fun parametersChanged(){if(playing || restartPending){pause();restartPending=true;handler.removeCallbacks(restart);handler.postDelayed(restart,150)}}
    private fun pause(){
        playing=false;handler.removeCallbacks(tick);audio?.release();audio=null;video()?.pause();state(false)
    }
    fun stop(){
        restartPending=false;handler.removeCallbacks(restart)
        pause()
        if(!transformed)return
        transformed=false
        val p=project()
        p.layers.forEach{l->views()[l.id]?.let{v->v.scaleX=baseScale(l,v);v.scaleY=v.scaleX;v.translationY=l.y*(v.parent as View).height-v.top-v.height/2f}}
    }
    private fun baseScale(l:Layer,v:View):Float{val width=(v.parent as View).width;return l.scale*width/((v.getTag(R.id.layer_reference_width) as? Int)?:width).coerceAtLeast(1)}
    fun release(){stop();generation++}
}
