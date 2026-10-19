package com.vividhyeok.igstoryplus

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ProjectTest {
    @Test fun musicRangeDoesNotSilentlyLoopOrShortenVideo(){
        val m=Music(durationMs=18000,startMs=17000);MusicRange.clamp(m,10000);assertEquals(8000L,m.startMs);assertTrue(MusicRange.valid(m,10000))
        assertFalse(MusicRange.valid(Music(durationMs=2000),15000));m.loop=true;m.startMs=17000;assertTrue(MusicRange.valid(m,60000))
        val p=Project(video=true,sourceDurationMs=60000,music=m);assertEquals(60000L,p.durationMs())
    }
    @Test fun legacyDraftDefaultsAndNewTextHistoryStayCompatible(){
        val gson=com.google.gson.Gson()
        val old=gson.fromJson("""{"source":"old.jpg","layers":[{"kind":"text","text":"오래된 초안","font":"system:serif"}],"music":{"title":"노래"}}""",Project::class.java)
        assertEquals(0,old.layers.single().textLayoutVersion);assertEquals(1f,old.music!!.volume)
        assertFalse(old.music!!.loop);assertEquals(PhotoPose(),old.pose);assertEquals("가장자리",old.style.region)
        val h=History();h.push(old);old.layers.single().text="새 문구";old.layers.single().textLayoutVersion=1
        val restored=h.undo(old)!!;assertEquals("오래된 초안",restored.layers.single().text);assertEquals("system:serif",restored.layers.single().font)
    }
    @Test fun outputPolicyAndVideoDuration() {
        val p=Project();assertFalse(p.animated());p.style.motion=true;assertTrue(p.animated());assertEquals(5000L,p.durationMs())
        p.music=Music();p.duration=15;assertEquals(15000L,p.durationMs())
        p.video=true;p.sourceDurationMs=43000;assertEquals(43000L,p.durationMs())
        p.sourceDurationMs=95000;assertEquals(60000L,p.durationMs())
        p.sourceDurationMs=420;assertEquals(420L,p.durationMs())
        p.video=false;p.music=null;p.style.motion=false;p.layers.add(Layer(motion=true));assertTrue(p.animated())
    }
    @Test fun historyRestoresCropLayersAndMusicIndependently(){
        val h=History();val p=Project(source="original");h.push(p);p.crop.zoom=3f;p.layers.add(Layer(kind="text",text="한국어"));p.music=Music(startMs=30000)
        val before=h.undo(p)!!;assertEquals(1f,before.crop.zoom);assertTrue(before.layers.isEmpty());assertNull(before.music)
        val after=h.redo(before)!!;assertEquals("한국어",after.layers.single().text);assertEquals(30000L,after.music!!.startMs);assertEquals("original",after.source)
    }
    @Test fun motionLoopsAndDefaultsAreQuiet(){
        val l=Layer(motion=true);val a=Motion.phase(0,15000);val b=Motion.phase(15000000,15000)
        assertEquals(Motion.scale(l,a),Motion.scale(l,b),.00001f);assertTrue(abs(Motion.scale(l,1.5f)-1)<.011f)
        assertFalse(Style().motion);assertEquals(0f,Style().beat)
    }
    @Test fun onsetEnvelopeRejectsSilenceAndLimitsFlashes(){
        assertTrue(Audio.onsets(DoubleArray(200)).all{it==0f})
        val envelope=DoubleArray(200){if(it%10==0)1.0 else .01}
        val result=Audio.onsets(envelope);val peaks=result.indices.filter{result[it]==1f}
        assertTrue(peaks.isNotEmpty());assertTrue(peaks.zipWithNext().all{(a,b)->b-a>=5});assertTrue(result.all{it in 0f..1f})
    }
}
