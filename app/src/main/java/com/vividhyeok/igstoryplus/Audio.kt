package com.vividhyeok.igstoryplus

import android.content.Context
import android.media.*
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.*

object Audio {
    fun reactionMusic(p:Project):Music?=when(p.reactionAudio){
        "원본 영상"->if(p.video)Music(uri=p.source,title="원본 영상 소리",durationMs=p.durationMs(),card="숨김")else null
        "추가 음악"->p.music
        else->p.music?:if(p.video)Music(uri=p.source,title="원본 영상 소리",durationMs=p.durationMs(),card="숨김")else null
    }
    fun hasAudio(c:Context,uri:String):Boolean{val e=MediaExtractor();return try{e.setDataSource(c,Uri.parse(uri),null);(0 until e.trackCount).any{e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}}finally{e.release()}}
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun volumeEffects(volume:Float):List<androidx.media3.common.audio.AudioProcessor> {
        if(volume==1f)return emptyList()
        val processor=androidx.media3.common.audio.ChannelMixingAudioProcessor()
        for(channels in 1..6)processor.putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(channels,channels,FloatArray(channels*channels){i->if(i/channels==i%channels)volume.coerceIn(0f,1f) else 0f}))
        return listOf(processor)
    }
    fun metadata(c: Context, uri: String): Music {
        val r=MediaMetadataRetriever()
        try {
            r.setDataSource(c,Uri.parse(uri))
            val filename=runCatching{c.contentResolver.query(Uri.parse(uri),arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null}}.getOrNull()?:Uri.parse(uri).lastPathSegment?.substringAfterLast('/')
            val m=Music(uri=uri,title=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?:filename?.substringBeforeLast('.')?:"제목 없음",artist=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?:"아티스트",album=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?:"",durationMs=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)
            r.embeddedPicture?.let { bytes -> val f=File(c.filesDir,"art-${key(uri)}.jpg"); f.writeBytes(bytes); m.art=Uri.fromFile(f).toString() }
            return m
        } finally { r.release() }
    }
    private fun key(s: String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    fun analyze(c:Context,m:Music,durationMs:Long):FloatArray=features(c,m,durationMs).onsets
    fun reaction(features:AudioFeatures,mode:String):FloatArray=when(mode){
        "Ambient Breathing"->FloatArray(features.low.size){max(features.onsets[it]*.7f,features.low[it]*.3f)}
        "Album Aura"->FloatArray(features.energy.size){features.fast[it]*.65f+features.slow[it]*.35f}
        else->features.onsets
    }
    @Synchronized fun features(c: Context, m: Music, durationMs: Long): AudioFeatures {
        val uri=Uri.parse(m.uri)
        val identity=runCatching{if(uri.scheme=="file")File(uri.path!!).let{"${it.length()}:${it.lastModified()}"} else c.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE,android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use{if(it.moveToFirst())"${it.getLong(0)}:${it.getLong(1)}" else ""}}.getOrNull()?:""
        val f=File(c.cacheDir,"features-v3-${key(m.uri+":"+identity+":"+m.startMs+":"+durationMs+":"+m.loop)}.json")
        if(f.exists())runCatching{Store(c).gson.fromJson(f.readText(),AudioFeatures::class.java)}.getOrNull()?.let{if(it.onsets.isNotEmpty())return it}
        val extractor=MediaExtractor(); var decoder: MediaCodec?=null
        try {
            extractor.setDataSource(c,Uri.parse(m.uri),null)
            val track=(0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }
            extractor.selectTrack(track); val format=extractor.getTrackFormat(track)
            extractor.seekTo(m.startMs*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!); decoder=codec; codec.configure(format,null,null,0); codec.start()
            val energy=DoubleArray((durationMs/50+1).toInt()); val counts=IntArray(energy.size)
            val signals=SignalAccumulator(energy.size)
            val info=MediaCodec.BufferInfo(); var inputDone=false; var outputDone=false
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE); var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT); var pcmFloat=false
            val deadline=System.nanoTime()+60_000_000_000L
            while(!outputDone) {
                check(System.nanoTime()<deadline){"음악 분석 시간 초과"}
                if(!inputDone) {
                    val i=codec.dequeueInputBuffer(1000)
                    if(i>=0) {
                        val buffer=codec.getInputBuffer(i)!!; val n=extractor.readSampleData(buffer,0); val time=extractor.sampleTime
                        if(n<0 || time> (m.startMs+durationMs)*1000) { codec.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true }
                        else { codec.queueInputBuffer(i,0,n,time,0); extractor.advance() }
                    }
                }
                val o=codec.dequeueOutputBuffer(info,1000)
                if(o==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val fmt=codec.outputFormat; rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmFloat=fmt.containsKey(MediaFormat.KEY_PCM_ENCODING) && fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)==AudioFormat.ENCODING_PCM_FLOAT
                } else if(o>=0) {
                    val buffer=codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()); buffer.position(info.offset); buffer.limit(info.offset+info.size)
                    var index=0
                    while(buffer.remaining()>=(if(pcmFloat)4 else 2)) {
                        val sample=if(pcmFloat)buffer.float.toDouble() else buffer.short/32768.0
                        val t=info.presentationTimeUs/1000.0-m.startMs+index.toDouble()/channels/rate*1000
                        val bin=(t/50).toInt(); if(t>=0 && bin in energy.indices){ energy[bin]+=sample*sample; counts[bin]++;signals.add(sample,bin,rate,channels) }; index++
                    }
                    outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0; codec.releaseOutputBuffer(o,false)
                }
            }
            val rms=DoubleArray(energy.size){sqrt(energy[it]/counts[it].coerceAtLeast(1))}
            if(m.loop){val span=(m.durationMs-m.startMs).coerceAtLeast(1);for(i in rms.indices)if(i*50L>=span)rms[i]=rms[((i*50L%span)/50).toInt().coerceIn(rms.indices)]}
            val result=signals.finish(rms,if(m.loop)(m.durationMs-m.startMs).coerceAtLeast(1) else null)
            val temp=File(f.parentFile,f.name+".tmp");temp.writeText(Store(c).gson.toJson(result));check(temp.renameTo(f));return result
        } finally { runCatching { decoder?.stop() }; decoder?.release(); extractor.release() }
    }
    /** Adaptive local-mean/std onset peak picking, adapted from the user's rhythm-mp3 onset.ts.
     * This mobile subset uses positive RMS novelty; it does not claim the original spectral/BPM classifier.
     * A 250ms refractory period and smooth decay limit visual flashes to a low-amplitude envelope. */
    fun onsets(rms: DoubleArray): FloatArray {
        val novelty=DoubleArray(rms.size){if(it==0)0.0 else max(0.0,rms[it]-rms[it-1])}
        val peaks=FloatArray(rms.size); var last=-10
        for(i in 1 until rms.size-1){
            val a=max(0,i-10); val b=min(rms.lastIndex,i+10)
            val mean=(a..b).sumOf{novelty[it]}/(b-a+1)
            val std=sqrt((a..b).sumOf{(novelty[it]-mean).pow(2)}/(b-a+1))
            if(novelty[i]>mean+1.35*std && novelty[i]>novelty[i-1] && novelty[i]>=novelty[i+1] && i-last>=5){ peaks[i]=1f; last=i }
        }
        for(i in 1 until peaks.size) peaks[i]=max(peaks[i],peaks[i-1]*.72f)
        return peaks
    }
}
