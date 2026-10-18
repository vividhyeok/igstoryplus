package com.vividhyeok.igstoryplus

import android.content.Intent
import android.graphics.*
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PipelineTest {
    private val c=InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext=InstrumentationRegistry.getInstrumentation().context
    private val evidence=File(c.getExternalFilesDir(null),"validation").apply{mkdirs()}
    private fun photo():String {
        val b=Bitmap.createBitmap(720,1080,Bitmap.Config.ARGB_8888);val canvas=Canvas(b)
        canvas.drawColor(Color.rgb(85,112,135));val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,720f,1080f,Color.rgb(237,196,137),Color.rgb(45,75,105),Shader.TileMode.CLAMP);canvas.drawRect(0f,0f,720f,1080f,p)
        p.shader=null;p.color=Color.rgb(30,45,40);canvas.drawCircle(360f,550f,190f,p)
        p.color=Color.WHITE;p.textSize=60f;canvas.drawText("igstoryplus",110f,900f,p)
        val f=File(c.filesDir,"test-source.jpg");f.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,95,it)};return Uri.fromFile(f).toString()
    }
    private fun wav():String {
        val rate=44100;val duration=16;val count=rate*duration;val data=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray());data.putInt(36+count*2);data.put("WAVEfmt ".toByteArray());data.putInt(16);data.putShort(1);data.putShort(1);data.putInt(rate);data.putInt(rate*2);data.putShort(2);data.putShort(16);data.put("data".toByteArray());data.putInt(count*2)
        for(i in 0 until count){val t=i.toDouble()/rate;val env=if(t%.5<.08).6 else .08;data.putShort((sin(t*2*PI*220)*env*32767).toInt().toShort())}
        val f=File(c.filesDir,"test-audio.wav");f.writeBytes(data.array());return Uri.fromFile(f).toString()
    }
    private fun export(p:Project,name:String):File {
        val latch=CountDownLatch(1);var result:Result<File>?=null
        Handler(Looper.getMainLooper()).post{Exporter(c).export(p,{}){result=it;latch.countDown()}}
        assertTrue("Export timed out: $name",latch.await(600,TimeUnit.SECONDS));val f=result!!.getOrThrow()
        val target=File(evidence,"$name.${f.extension}");f.copyTo(target,true);return target
    }
    private fun tracks(file:File):List<MediaFormat>{val e=MediaExtractor();return try{e.setDataSource(file.absolutePath);(0 until e.trackCount).map{e.getTrackFormat(it)}}finally{e.release()}}
    @Test fun aStillCropTextAndAllShaders(){
        val source=photo();val p=Project(source=source)
        p.layers.add(Layer(kind="text",text="오늘의 작은 움직임",type="Soft Label",width=.7f,y=.7f))
        p.layers.add(Layer(kind="photo",uri=source,width=.23f,x=.77f,y=.3f,crop=Crop(aspect=1f,zoom=1.5f,rotation=90f)))
        val f=export(p,"still-korean");val b=BitmapFactory.decodeFile(f.absolutePath);assertEquals(1080,b.width);assertEquals(1920,b.height)
        val raw=Artwork.decode(c,source);val crop=Artwork.cropped(raw,Crop(aspect=1f,zoom=2f,rotation=90f,flip=true));assertEquals(crop.width,crop.height);assertTrue(crop.width<raw.width)
        val base=Artwork.background(c,p,270)
        listOf("Simple","Grainy","Gritty","Lores","Handheld","Zoomblur","Flow Blur").forEach{n->val out=filteredBitmap(base,Style(name=n));assertEquals(270,out.width);out.outputStream(File(evidence,"style-${n.replace(" ","-")}.png"))}
    }
    private fun Bitmap.outputStream(file:File){file.outputStream().use{compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun bPngFolderRefreshAndAlpha(){
        val uri=DocumentsContract.buildTreeDocumentUri("com.vividhyeok.igstoryplus.test.documents","root")
        testContext.startActivity(Intent().setClassName(testContext.packageName,GrantFixtureActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(50){if(c.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)Thread.sleep(100)}
        c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(c.contentResolver.persistedUriPermissions.any{it.uri==uri && it.isReadPermission})
        EmojiFolder.scan(c,uri).forEach{DocumentsContract.deleteDocument(c.contentResolver,it.uri)}
        assertTrue(EmojiFolder.scan(c,uri).isEmpty())
        val b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas(b).drawCircle(256f,256f,100f,Paint().apply{color=Color.RED})
        val document=DocumentsContract.createDocument(c.contentResolver,DocumentsContract.buildDocumentUriUsingTree(uri,"root"),"image/png","mine.png")!!
        c.contentResolver.openOutputStream(document)!!.use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        val docs=EmojiFolder.scan(c,uri);assertEquals(1,docs.size);val loaded=Artwork.decode(c,docs.single().uri.toString());assertEquals(0,Color.alpha(loaded.getPixel(0,0)))
        val trim=Artwork.trimAlpha(loaded);assertTrue(trim.width in 198..202)
        val renamed=DocumentsContract.renameDocument(c.contentResolver,document,"renamed.png")!!;assertEquals("renamed.png",EmojiFolder.scan(c,uri).single().name)
        assertTrue(DocumentsContract.deleteDocument(c.contentResolver,renamed));assertTrue(EmojiFolder.scan(c,uri).isEmpty())
        assertTrue(runCatching{EmojiFolder.scan(c,DocumentsContract.buildTreeDocumentUri("com.vividhyeok.igstoryplus.test.documents","missing"))}.isFailure)
        c.contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    @Test fun cMusicAndMotion15Seconds(){
        val p=Project(source=photo(),duration=15,style=Style(name="Flow Blur",motion=true,beat=.25f))
        val m=Audio.metadata(c,wav());m.title="Demo";m.artist="vividhyeok";m.startMs=1000;p.music=m
        p.layers.add(Layer(kind="music",width=.55f,y=.8f,motion=true));p.layers.add(Layer(kind="text",text="조금 다르게",type="Drift Type",motion=true,y=.3f))
        val a=Audio.analyze(c,m,p.durationMs());val cached=Audio.analyze(c,m,p.durationMs());assertArrayEquals(a,cached,0f);assertTrue(a.any{it>0})
        val f=export(p,"music-flow-15s");Exporter.inspect(f,p)
        val tracks=tracks(f);assertTrue(tracks.any{it.getString(MediaFormat.KEY_MIME)=="video/avc"});assertTrue(tracks.any{it.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm"})
        val intent=Sharing.intent(c,File(c.filesDir,"exports").listFiles()!!.first{it.extension=="mp4"},true)
        assertEquals("com.instagram.share.ADD_TO_STORY",intent.action);assertEquals("content",intent.data!!.scheme);assertNotNull(intent.clipData);assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
    }
    @Test fun dFiveSecondMotionThen30And60SecondVideo(){
        val p=Project(source=photo(),style=Style(name="Handheld",motion=true));val f=export(p,"motion-5s");Exporter.inspect(f,p)
        for(duration in listOf(30000L,60000L)){
            // Test real input-video decode -> compositing -> H.264 encode, not a model-only duration assertion.
            val source=File(c.cacheDir,"source-${duration/1000}s.mp4")
            testContext.assets.open("source-${duration/1000}s.mp4").use{i->source.outputStream().use{i.copyTo(it)}}
            val v=Project(source=Uri.fromFile(source).toString(),video=true,sourceDurationMs=duration,music=Audio.metadata(c,wav()))
            v.layers.add(Layer(kind="text",text="${duration/1000}초",type="Whisper"))
            val out=export(v,"video-${duration/1000}s");Exporter.inspect(out,v)
        }
    }
    @Test fun eDraftPresetsMissingSourceAndCancellation(){
        val store=Store(c);val p=Project(source=photo(),crop=Crop(zoom=2f));p.layers.add(Layer(kind="text",text="초안"));store.save(p)
        assertEquals("초안",store.load().layers.single().text);assertEquals(2f,store.load().crop.zoom)
        store.savePresets(listOf(Preset("기록",Style(name="Flow Blur"),"Whisper","텍스트")));assertEquals("Flow Blur",store.presets().single().style.name)
        val latch=CountDownLatch(1)
        Handler(Looper.getMainLooper()).post{val e=Exporter(c);e.export(Project(source="content://missing/file"),{}){assertTrue(it.isFailure);latch.countDown()}}
        assertTrue(latch.await(15,TimeUnit.SECONDS))
        Handler(Looper.getMainLooper()).post{val e=Exporter(c);e.export(Project(source=p.source,style=Style(motion=true)),{}){};e.cancel()}
        Thread.sleep(1500);assertEquals("초안",store.load().layers.single().text)
        val before=File(c.filesDir,"exports").listFiles()!!.map{it.name}.toSet()
        val cancelled=CountDownLatch(1);var earlyFailure:Throwable?=null
        Handler(Looper.getMainLooper()).post{
            val e=Exporter(c)
            e.export(Project(source=p.source,style=Style(motion=true)),{e.cancel();cancelled.countDown()}){r->earlyFailure=r.exceptionOrNull()?:IllegalStateException("Completed before cancellation");cancelled.countDown()}
        }
        assertTrue("Started renderer cancellation timed out",cancelled.await(30,TimeUnit.SECONDS));assertNull(earlyFailure)
        Thread.sleep(300);assertTrue(File(c.filesDir,"exports").listFiles()!!.all{it.name in before})
        store.save(Project());store.savePresets(emptyList())
    }
    @Test fun fCommonAudioFormatsAndMetadata(){
        for(ext in listOf("mp3","m4a","flac")){
            val f=File(c.cacheDir,"fixture.$ext");testContext.assets.open("audio.$ext").use{i->f.outputStream().use{i.copyTo(it)}}
            val music=Audio.metadata(c,Uri.fromFile(f).toString());assertEquals("Fixture Song",music.title);assertEquals("Fixture Artist",music.artist)
            assertTrue(music.durationMs in 1900..2200);assertTrue(Audio.analyze(c,music,2000).size>=40)
        }
    }
}
