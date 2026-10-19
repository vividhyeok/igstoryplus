package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.*

object WaveformCache {
    val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    /** Full-track PCM peak envelope, bounded memory; called off the UI thread once per source. */
    @Synchronized fun peaks(c:Context,m:Music):FloatArray{
        val length=runCatching{c.contentResolver.openAssetFileDescriptor(Uri.parse(m.uri),"r")?.use{it.length}}.getOrNull()
        val uri=Uri.parse(m.uri)
        val modified=if(uri.scheme=="file")File(uri.path!!).lastModified() else runCatching{c.contentResolver.query(uri,arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use{if(it.moveToFirst())it.getLong(0) else 0L}}.getOrDefault(0L)
        val key=MessageDigest.getInstance("SHA-256").digest("${m.uri}:${m.durationMs}:$length:$modified".toByteArray()).joinToString(""){"%02x".format(it)}
        val cache=File(c.cacheDir,"waveform-$key.json");val gson=Store(c).gson
        if(cache.exists())runCatching{gson.fromJson(cache.readText(),FloatArray::class.java)}.getOrNull()?.let{if(it.size==2048)return it}
        require(m.durationMs>0){"음악 길이를 읽을 수 없습니다"}
        val extractor=MediaExtractor();var decoder:MediaCodec?=null
        try{
            extractor.setDataSource(c,Uri.parse(m.uri),null)
            val track=(0 until extractor.trackCount).first{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
            extractor.selectTrack(track);val format=extractor.getTrackFormat(track)
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!);decoder=codec;codec.configure(format,null,null,0);codec.start()
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);var floating=false
            val peaks=FloatArray(2048);val info=MediaCodec.BufferInfo();var inputDone=false;var done=false
            val deadline=System.nanoTime()+180_000_000_000L
            while(!done){
                check(!Thread.currentThread().isInterrupted && System.nanoTime()<deadline){"파형 분석이 중단되었습니다"}
                if(!inputDone){val i=codec.dequeueInputBuffer(1000);if(i>=0){val buffer=codec.getInputBuffer(i)!!;val n=extractor.readSampleData(buffer,0);if(n<0){codec.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}else{codec.queueInputBuffer(i,0,n,extractor.sampleTime,0);extractor.advance()}}}
                val o=codec.dequeueOutputBuffer(info,1000)
                if(o==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){val f=codec.outputFormat;rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);floating=f.containsKey(MediaFormat.KEY_PCM_ENCODING)&&f.getInteger(MediaFormat.KEY_PCM_ENCODING)==AudioFormat.ENCODING_PCM_FLOAT}
                else if(o>=0){
                    val b=codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder());b.position(info.offset);b.limit(info.offset+info.size);var sample=0
                    while(b.remaining()>=(if(floating)4 else 2)){
                        val value=abs(if(floating)b.float else b.short/32768f)
                        val ms=info.presentationTimeUs/1000.0+sample.toDouble()/channels/rate*1000
                        val bin=(ms/m.durationMs*peaks.size).toInt();if(bin in peaks.indices)peaks[bin]=max(peaks[bin],value);sample++
                    }
                    done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(o,false)
                }
            }
            val top=peaks.maxOrNull()?.coerceAtLeast(.001f)?:1f
            for(i in peaks.indices)peaks[i]=sqrt(peaks[i]/top).coerceIn(0f,1f)
            val tmp=File(c.cacheDir,"waveform-$key.tmp");tmp.writeText(gson.toJson(peaks));check(tmp.renameTo(cache));return peaks
        }finally{runCatching{decoder?.stop()};decoder?.release();extractor.release()}
    }
}

/** A real peak envelope with a draggable fixed-duration frame, not a restyled SeekBar. */
class AudioWaveformView(c:Context):View(c){
    var peaks=floatArrayOf();set(value){field=value;invalidate()}
    var durationMs=1L
    var clipMs=15000L
    var startMs=0L;set(value){field=value;invalidate();contentDescription="음악 파형, ${seconds(value)}부터 ${seconds(value+clipMs)}까지"}
    var playheadMs=-1L;set(value){field=value;invalidate()}
    var onStart:()->Unit={}
    var onRange:(Long,Boolean)->Unit={_,_->}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val density=resources.displayMetrics.density
    private var down=0f;private var initial=0L;private var moving=false
    init{isFocusable=true;isClickable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;contentDescription="음악 파형 분석 중"}
    private fun seconds(ms:Long)="%.1f초".format(java.util.Locale.ROOT,ms/1000f)
    private fun xpos(ms:Long)=16*density+(width-32*density)*ms/durationMs.coerceAtLeast(1)
    override fun onDraw(c:Canvas){
        val usable=width-32*density;val mid=height*.5f;val count=max(1,(usable/(4*density)).toInt())
        val left=xpos(startMs);val right=xpos((startMs+clipMs).coerceAtMost(durationMs))
        paint.color=0x18FFFFFF;c.drawRoundRect(left,8*density,right,height-8*density,9*density,9*density,paint)
        paint.strokeWidth=2*density;paint.strokeCap=Paint.Cap.ROUND
        for(i in 0 until count){val x=16*density+usable*(i+.5f)/count
            val from=i*peaks.size/count;val until=((i+1)*peaks.size/count).coerceAtMost(peaks.size)
            var amp=0f;for(j in from until until)amp=max(amp,peaks[j])
            val h=max(2*density,amp*(height/2-20*density));paint.color=if(x in left..right)Color.WHITE else 0xFF68686F.toInt();c.drawLine(x,mid-h,x,mid+h,paint)
        }
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=Color.WHITE;c.drawRoundRect(left,8*density,right,height-8*density,9*density,9*density,paint);paint.style=Paint.Style.FILL
        c.drawRoundRect(left-2*density,mid-10*density,left+2*density,mid+10*density,2*density,2*density,paint)
        if(playheadMs>=0){paint.color=0xFFD4FF83.toInt();c.drawLine(xpos(startMs+playheadMs),12*density,xpos(startMs+playheadMs),height-12*density,paint)}
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{parent.requestDisallowInterceptTouchEvent(true);down=e.x;initial=startMs;moving=false;onStart()}
            MotionEvent.ACTION_MOVE->{moving=true;startMs=(initial+(e.x-down)/(width-32*density)*durationMs).toLong().coerceIn(0,(durationMs-clipMs).coerceAtLeast(0));onRange(startMs,false)}
            MotionEvent.ACTION_UP->{if(!moving)startMs=((e.x-16*density)/(width-32*density)*durationMs-clipMs/2).toLong().coerceIn(0,(durationMs-clipMs).coerceAtLeast(0));onRange(startMs,true);parent.requestDisallowInterceptTouchEvent(false);performClick()}
            MotionEvent.ACTION_CANCEL->{onRange(startMs,true);parent.requestDisallowInterceptTouchEvent(false)}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)}
    override fun performAccessibilityAction(action:Int,args:android.os.Bundle?):Boolean{
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){onStart();startMs=(startMs+if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1000 else -1000).coerceIn(0,(durationMs-clipMs).coerceAtLeast(0));onRange(startMs,true);return true};return super.performAccessibilityAction(action,args)
    }
}
