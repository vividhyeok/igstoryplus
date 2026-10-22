package com.vividhyeok.igstoryplus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ja.burhanrashid52.photoeditor.PhotoEditorView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditorUiTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun liveActivity(action:(MainActivity)->Unit){
        instrumentation.runOnMainSync{
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
            action(activity)
        }
    }
    private fun screenshot(name:String){SystemClock.sleep(250);val f=File(context.getExternalFilesDir(null),"validation/$name.png").apply{parentFile!!.mkdirs()};instrumentation.uiAutomation.takeScreenshot().let{b->f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    private fun tapAt(x:Float,y:Float){val now=SystemClock.uptimeMillis();instrumentation.sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0));instrumentation.sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0))}
    private fun swipeAt(x:Float,y:Float,dx:Float){
        val down=SystemClock.uptimeMillis()
        fun send(action:Int,px:Float){MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,px,y,0).also{assertTrue(instrumentation.uiAutomation.injectInputEvent(it,false));it.recycle()}}
        send(MotionEvent.ACTION_DOWN,x)
        for(i in 1..8){SystemClock.sleep(16);send(MotionEvent.ACTION_MOVE,x+dx*i/8)}
        send(MotionEvent.ACTION_UP,x+dx)
    }
    private fun liveTap(x:Float,y:Float){
        val down=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).also{assertTrue(instrumentation.uiAutomation.injectInputEvent(it,false));it.recycle()}
            SystemClock.sleep(16)
        }
    }
    private fun point(description:String):Pair<Float,Float>{var p=0f to 0f;onView(withContentDescription(description)).check{v,_->val xy=IntArray(2);v.getLocationOnScreen(xy);p=xy[0]+v.width/2f to xy[1]+v.height/2f};return p}
    private fun tapLive(scenario:ActivityScenario<MainActivity>,description:String){
        var p:Pair<Float,Float>?=null
        for(attempt in 0 until 60){
            liveActivity{a->fun find(v:View):View?{if(v.contentDescription==description)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null};val v=find(a.window.decorView);val rect=android.graphics.Rect();if(v!=null && v.isLaidOut && a.window.decorView.hasWindowFocus() && v.getGlobalVisibleRect(rect) && rect.width()>0 && rect.height()>0){p=rect.exactCenterX() to rect.exactCenterY()}}
            if(p!=null)break;SystemClock.sleep(50)
        }
        assertNotNull("Live control must be focused and laid out: $description",p)
        tapAt(p!!.first,p!!.second)
    }
    private fun editor(v:View):PhotoEditorView? {
        if(v is PhotoEditorView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)editor(v.getChildAt(i))?.let{return it}
        return null
    }
    private fun awaitCanvasAfterIme(scenario:ActivityScenario<MainActivity>){
        var previous:Pair<Int,Int>?=null;var stable=0
        for(attempt in 0 until 80){
            liveActivity{a->
                val e=editor(a.window.decorView)!!
                val ime=androidx.core.view.ViewCompat.getRootWindowInsets(a.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true
                val size=e.width to e.height
                stable=if(!ime && !e.isLayoutRequested && size==previous && a.window.decorView.hasWindowFocus())stable+1 else 0
                previous=size
            }
            if(stable>=8)return
            SystemClock.sleep(50)
        }
        fail("IME must close and the actual canvas must settle before targeting a double tap")
    }
    private fun fixtureStory():String{
        val b=Bitmap.createBitmap(720,1000,Bitmap.Config.ARGB_8888);val c=Canvas(b);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader=android.graphics.LinearGradient(0f,0f,0f,1000f,intArrayOf(0xFFBDD2D0.toInt(),0xFFEACFA9.toInt(),0xFF8C9B82.toInt()),null,android.graphics.Shader.TileMode.CLAMP);c.drawRect(0f,0f,720f,1000f,p)
        p.shader=null;p.color=0xFFF9E5B8.toInt();c.drawCircle(490f,290f,78f,p)
        p.color=0xFF596F66.toInt();c.drawPath(android.graphics.Path().apply{moveTo(0f,690f);cubicTo(160f,450f,300f,720f,480f,550f);lineTo(720f,730f);lineTo(720f,1000f);lineTo(0f,1000f);close()},p)
        p.color=0xFF344E49.toInt();c.drawPath(android.graphics.Path().apply{moveTo(0f,830f);cubicTo(300f,650f,450f,970f,720f,740f);lineTo(720f,1000f);lineTo(0f,1000f);close()},p)
        val f=File(context.filesDir,"ux-fixture.jpg");f.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,95,it)};return Uri.fromFile(f).toString()
    }
    private fun fixtureMusic():Music{
        val rate=22050;val count=rate*24;val bytes=java.nio.ByteBuffer.allocate(44+count*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray());bytes.putInt(36+count*2);bytes.put("WAVEfmt ".toByteArray());bytes.putInt(16);bytes.putShort(1);bytes.putShort(1);bytes.putInt(rate);bytes.putInt(rate*2);bytes.putShort(2);bytes.putShort(16);bytes.put("data".toByteArray());bytes.putInt(count*2)
        for(i in 0 until count){val t=i.toDouble()/rate;val beat=if(t%.5<.08).65 else .08;bytes.putShort((kotlin.math.sin(t*2*kotlin.math.PI*(220+20*kotlin.math.sin(t)))*beat*32767).toInt().toShort())}
        val f=File(context.filesDir,"ux-fixture.wav");f.writeBytes(bytes.array());return Audio.metadata(context,Uri.fromFile(f).toString()).apply{title="느린 오후 — 나의 미발매 데모";artist="vividhyeok";art=fixtureStory()}
    }
    @Test fun backgroundPhotoIsDirectAndReversible(){
        Store(context).clearTextEdit();Store(context).save(Project(source=fixtureStory()))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            SystemClock.sleep(500)
            onView(withContentDescription("배경 사진")).perform(swipeRight())
            liveActivity{assertTrue(it.project.pose.x>.55f);assertEquals(0f,it.project.crop.aspect)}
            onView(withContentDescription("실행 취소")).perform(click());liveActivity{assertEquals(.5f,it.project.pose.x,.005f)}
            onView(withContentDescription("다시 실행")).perform(click())
            var cx=0f;var cy=0f;liveActivity{a->val e=editor(a.window.decorView)!!;val pos=IntArray(2);e.getLocationOnScreen(pos);cx=pos[0]+e.width*.75f;cy=pos[1]+e.height*.5f}
            pinchAndRotate(cx,cy)
            liveActivity{assertTrue(it.project.pose.scale>1.05f);assertTrue(kotlin.math.abs(it.project.pose.rotation)>3)}
            screenshot("ux-photo-gesture");scenario.recreate();liveActivity{assertTrue(it.project.pose.x>.55f);assertTrue(it.project.pose.scale>1.05f);it.project=Project();Store(context).save(it.project)}
        }
    }
    @Test fun independentPhotoStyleAndNamedDraftGallery(){
        Store(context).clearTextEdit();val source=fixtureStory();val project=Project(source=source,layers=mutableListOf(Layer(uri=source,width=.35f)))
        val library=DraftLibrary(context);val saved=library.save("UI 복원 초안",project);Store(context).save(project)
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            onView(withContentDescription("photo 레이어")).perform(click())
            onView(withText("스타일")).perform(click());onView(withContentDescription("사진 스타일: Gritty")).perform(click())
            liveActivity{assertEquals("Simple",it.project.style.name);assertEquals("Gritty",it.project.layers.single().photoStyle!!.name)}
            screenshot("ux-layer-style");onView(withContentDescription("시트 완료")).perform(click())
            onView(withContentDescription("더보기")).perform(click());onView(withText("내 초안")).perform(scrollTo(),click());screenshot("ux-named-drafts")
            onView(withText("UI 복원 초안")).perform(click());onView(withText("열기")).perform(click())
            liveActivity{assertNull(it.project.layers.single().photoStyle);assertEquals("Simple",it.project.style.name)}
            scenario.recreate();liveActivity{assertEquals(source,it.project.source)}
        }
        library.delete(saved)
    }
    @Test fun bundledEmojiWorkWithoutFolderGrantAndRestore(){
        Store(context).clearTextEdit();context.getSharedPreferences("settings",0).edit().remove("emojiFolder").commit()
        val items=EmojiLibrary.defaults(context);assertEquals(33,items.size);assertEquals(33,items.map{it.uri}.distinct().size)
        items.forEach{val bitmap=Artwork.decode(context,it.uri);assertEquals(512,bitmap.width);assertEquals(512,bitmap.height);assertEquals(0,Color.alpha(bitmap.getPixel(0,0)))}
        Store(context).save(Project(source=fixtureStory()))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            var ready=false
            for(attempt in 0 until 100){liveActivity{a->fun find(v:View):LivePhoto?{if(v is LivePhoto)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null};ready=find(a.window.decorView)?.frameReady==true};if(ready)break;SystemClock.sleep(100)}
            assertTrue("Bundled library capture needs the actual source photo",ready)
            onView(withContentDescription("스티커")).perform(click());SystemClock.sleep(400);screenshot("ux-bundled-emoji")
            onView(withContentDescription("이모지 검색")).perform(replaceText("커피"),closeSoftKeyboard())
            onView(withContentDescription("스티커 추가: 커피")).perform(click())
            liveActivity{assertTrue(EmojiLibrary.bundled(it.project.layers.single().uri))}
            onView(withContentDescription("png 레이어")).perform(swipeUp());scenario.recreate()
            liveActivity{assertTrue(it.project.layers.single().y<.5f);assertNotNull(Artwork.layer(context,it.project.layers.single(),null))}
            screenshot("ux-bundled-emoji-placed")
        }
    }
    @Test fun pngTrayUsesOnlyGrantedUserFolder(){
        Store(context).clearTextEdit()
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("com.vividhyeok.igstoryplus.test.documents","root")
        instrumentation.context.startActivity(android.content.Intent().setClassName(instrumentation.context.packageName,GrantFixtureActivity::class.java.name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(50){if(context.checkUriPermission(tree,android.os.Process.myPid(),android.os.Process.myUid(),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)SystemClock.sleep(100)}
        context.contentResolver.takePersistableUriPermission(tree,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        EmojiFolder.scan(context,tree).forEach{android.provider.DocumentsContract.deleteDocument(context.contentResolver,it.uri)}
        val doc=android.provider.DocumentsContract.createDocument(context.contentResolver,android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"root"),"image/png","test-only.png")!!
        val b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas(b).drawCircle(256f,256f,120f,android.graphics.Paint().apply{color=Color.rgb(245,210,110)})
        context.contentResolver.openOutputStream(doc)!!.use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        context.getSharedPreferences("settings",0).edit().putString("emojiFolder",tree.toString()).apply();Store(context).save(Project(source=fixtureStory()))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            onView(withContentDescription("스티커")).perform(click());SystemClock.sleep(400);screenshot("ux-png-library")
            onView(org.hamcrest.Matchers.allOf(withText("내 폴더"),isDisplayed())).perform(click());onView(withContentDescription("스티커 추가: test-only.png")).perform(click());liveActivity{assertEquals("png",it.project.layers.single().kind);assertEquals(.089f,it.project.layers.single().width)}
            onView(withContentDescription("png 레이어")).perform(swipeUp())
            scenario.recreate();liveActivity{assertEquals("png",it.project.layers.single().kind);assertTrue(it.project.layers.single().y<.5f);it.project=Project();Store(context).save(it.project)}
        }
        android.provider.DocumentsContract.deleteDocument(context.contentResolver,doc)
        context.getSharedPreferences("settings",0).edit().remove("emojiFolder").apply()
    }
    @Test fun waveformLibraryVisualStylesAndSharedPreview(){
        context.getSharedPreferences("settings",0).edit().putInt("musicDuration",15).commit()
        Store(context).clearTextEdit();val source=fixtureStory();val music=fixtureMusic();MusicLibrary(context).remember(music)
        val png=File(context.filesDir,"ux-test-only.png");val sticker=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas(sticker).drawCircle(256f,256f,150f,android.graphics.Paint().apply{color=Color.rgb(245,220,160)});png.outputStream().use{sticker.compress(Bitmap.CompressFormat.PNG,100,it)}
        Store(context).save(Project(source=source,style=Style(name="Flow Blur",motion=true,beat=.15f),layers=mutableListOf(Layer(kind="text",text="천천히 흐르는 오후",type="Drift Type",motion=true,y=.25f,textLayoutVersion=1),Layer(kind="png",uri=Uri.fromFile(png).toString(),width=.089f,x=.2f,y=.6f,motion=true))))
        fun outputs()=File(context.filesDir,"exports").listFiles()?.map{it.name}?.toSet()?:emptySet()
        val before=outputs()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            var photoReady=false
            for(attempt in 0 until 100){liveActivity{a->fun find(v:View):LivePhoto?{if(v is LivePhoto)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null};photoReady=find(a.window.decorView)?.frameReady==true};if(photoReady)break;SystemClock.sleep(100)}
            assertTrue("Initial photo must have actually rendered",photoReady);SystemClock.sleep(150);screenshot("ux-photo-main")
            onView(withContentDescription("음악")).perform(click());screenshot("ux-music-library")
            onView(withContentDescription("음악 선택: ${music.title}")).perform(click())
            var ready=false
            for(i in 0 until 100){
                try{onView(withTagValue(org.hamcrest.Matchers.`is`("music-waveform" as Any))).check{v,_->ready=(v as AudioWaveformView).peaks.any{it>0}}}catch(_:Exception){}
                if(ready)break;SystemClock.sleep(100)
            }
            assertTrue("Waveform must contain decoded, nonzero PCM peaks",ready)
            screenshot("ux-music-waveform")
            liveActivity{assertEquals(.55f,it.project.style.beat,.001f)}
            onView(withText("음악 반응")).perform(scrollTo(),click())
            liveActivity{assertEquals(0f,it.project.style.beat,0f)}
            onView(withText("음악 반응")).perform(click())
            onView(withContentDescription("음악 반응 강도")).perform(scrollTo())
            screenshot("ux-music-reaction")
            onView(withText("15초")).perform(scrollTo())
            onView(withText("15초")).perform(click());onView(withText("10초")).perform(click())
            onView(withTagValue(org.hamcrest.Matchers.`is`("music-waveform" as Any))).perform(swipeRight())
            liveActivity{assertEquals(10,it.project.duration);assertTrue(it.project.music!!.startMs>0);assertTrue(MusicRange.valid(it.project.music!!,10000))}
            SystemClock.sleep(1000) // The new range is auditioned once, then returns to paused.
            fun assertPlaying(expected:Boolean=true){
                var actual=false
                for(attempt in 0 until 40){liveActivity{a->
                fun playing(v:View):Boolean{if(v.contentDescription=="미리보기 일시정지")return true;if(v is ViewGroup)for(i in 0 until v.childCount)if(playing(v.getChildAt(i)))return true;return false}
                actual=playing(a.window.decorView)
                };if(actual==expected)return;SystemClock.sleep(50)}
                assertEquals("Live music changes must preserve the requested playback state",expected,actual)
            }
            onView(withContentDescription("음악 반응 강도")).perform(scrollTo())
            val strengthPoint=point("음악 반응 강도");val playback=point("선택 구간 재생")
            android.util.Log.i("TestRunner","live-control: reaction play")
            liveTap(playback.first,playback.second);SystemClock.sleep(600)
            swipeAt(strengthPoint.first,strengthPoint.second,120f);SystemClock.sleep(400);assertPlaying()
            liveActivity{assertTrue("Actual touch must change reaction strength",kotlin.math.abs(it.project.style.beat-.55f)>.01f)}
            android.util.Log.i("TestRunner","live-control: reaction changed; pausing")
            liveTap(playback.first,playback.second);SystemClock.sleep(400);assertPlaying(false)
            screenshot("ux-music-live-controls")
            onView(withText("볼륨")).perform(scrollTo(),click())
            val gain=point("음악 볼륨");val listen=point("소리 미리듣기 · 재생/일시정지")
            android.util.Log.i("TestRunner","live-control: mixer play")
            liveTap(listen.first,listen.second);SystemClock.sleep(400)
            swipeAt(gain.first,gain.second,-120f);SystemClock.sleep(400);assertPlaying()
            liveActivity{assertTrue(it.project.music!!.volume<1f)}
            android.util.Log.i("TestRunner","live-control: mixer changed; pausing")
            liveTap(listen.first,listen.second);SystemClock.sleep(400);assertPlaying(false)
            screenshot("ux-audio-mixer")
            onView(withContentDescription("시트 완료")).perform(click())
            onView(withText("음악 카드 디자인")).perform(scrollTo(),click());screenshot("ux-music-cards")
            onView(withContentDescription("음악 카드: 앨범 아트")).perform(scrollTo(),click())
            liveActivity{assertEquals("앨범 아트",it.project.music!!.card)}
            onView(withContentDescription("시트 완료")).perform(click())
            onView(withContentDescription("music 레이어")).perform(swipeUp());screenshot("ux-music-placed")
            liveActivity{assertTrue(it.project.layers.first{l->l.kind=="music"}.y<.8f)}
            androidx.test.espresso.Espresso.pressBack()
            onView(withText("스타일")).perform(click())
            var thumbnails=false
            for(attempt in 0 until 100){onView(withContentDescription("사진 스타일: Flow Blur")).check{v,_->thumbnails=(v as android.widget.ImageView).drawable!=null};if(thumbnails)break;SystemClock.sleep(100)}
            assertTrue("All seven real GPU thumbnails must finish",thumbnails);screenshot("ux-photo-styles")
            onView(withContentDescription("사진 스타일: Grainy")).perform(scrollTo(),click())
            onView(withContentDescription("사진 스타일: Flow Blur")).perform(scrollTo(),click())
            onView(withContentDescription("시트 완료")).perform(click())
            tapLive(scenario,"미리보기 재생");SystemClock.sleep(500);screenshot("ux-preview");tapLive(scenario,"미리보기 일시정지")
            onView(withContentDescription("미리보기 재생 위치")).perform(click());SystemClock.sleep(100)
            onView(withContentDescription("처음부터")).perform(click())
            onView(withText("편집")).perform(click());assertEquals(before,outputs())
            onView(withText("사진 추가")).perform(click());onView(withText("배경 자르기")).perform(click());onView(withContentDescription("드래그로 크롭 위치, 핀치로 확대")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));screenshot("ux-photo-crop")
            onView(withText("4:5")).perform(click());onView(withText("취소")).perform(click())
            liveActivity{assertEquals(0f,it.project.crop.aspect)}
            onView(withContentDescription("인스타 스토리로 공유")).perform(click());screenshot("ux-share-progress")
            onView(withText("취소")).perform(click())
            liveActivity{assertNotNull(Store(context).load().music);it.project=Project();Store(context).save(it.project)}
        }
    }
    @Test fun musicCardKeepsOrderMotionDuplicateAndHideControls(){
        Store(context).clearTextEdit()
        val source=File(context.filesDir,"music-ui-source.jpg")
        Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(70,90,85))}.let{b->source.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,90,it)}}
        Store(context).save(Project(source=Uri.fromFile(source).toString(),music=Music(title="나의 노래",artist="아티스트"),layers=mutableListOf(Layer(kind="music",width=.55f,y=.8f),Layer(kind="text",text="오늘",y=.3f))))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            onView(withContentDescription("music 레이어")).perform(click())
            onView(withContentDescription("레이어 추가 작업")).perform(scrollTo(),click())
            onView(withText("뒤로")).perform(click())
            liveActivity{assertEquals("music",it.project.layers.first().kind)}
            onView(withContentDescription("레이어 추가 작업")).perform(scrollTo(),click())
            onView(withText("미세 모션 OFF")).perform(click())
            liveActivity{assertTrue(it.project.layers.first{l->l.kind=="music"}.motion)}
            onView(withContentDescription("레이어 추가 작업")).perform(scrollTo(),click())
            onView(withContentDescription("레이어 추가 작업")).perform(click());onView(withText("복제")).perform(click())
            liveActivity{assertEquals(2,it.project.layers.count{l->l.kind=="music"})}
            onView(withText("카드 숨김")).perform(scrollTo(),click())
            liveActivity{assertNotNull(it.project.music);assertTrue(it.project.layers.none{l->l.kind=="music"});it.project=Project();Store(context).save(it.project)}
        }
    }
    @Test fun directTextIsLiveCancelableAndSurvivesRecreation(){
        Store(context).clearTextEdit()
        val source=File(context.filesDir,"text-source.jpg")
        Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(65,90,80))}.let{b->source.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,90,it)}}
        Store(context).save(Project(source=Uri.fromFile(source).toString(),layers=mutableListOf(Layer(kind="text",text="이전 문구"))))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            onView(withContentDescription("텍스트")).perform(click())
            var imeVisible=false
            for(i in 0 until 40){liveActivity{a->imeVisible=androidx.core.view.ViewCompat.getRootWindowInsets(a.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true};if(imeVisible)break;SystemClock.sleep(100)}
            assertTrue("The soft keyboard must actually be visible, not just an editable field",imeVisible)
            liveActivity{a->
                val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input")
                val connection=input.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!
                assertTrue(connection.setComposingText("ㅎ",1));assertTrue(connection.setComposingText("한",1))
                assertEquals("한",input.text.toString())
                assertTrue(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(input.text)>=0)
                connection.commitText("한",1)
            }
            onView(withContentDescription("캔버스 텍스트 입력")).perform(replaceText("한글 조합\n두 번째 줄"))
            instrumentation.waitForIdleSync();SystemClock.sleep(300)
            screenshot("editor-text-ime")
            liveActivity{a->
                val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input")
                assertNotNull(input);assertTrue(input.parent is android.widget.FrameLayout)
                assertEquals("한글 조합\n두 번째 줄",input.text.toString())
                assertEquals(1,a.project.layers.size)
                val e=editor(a.window.decorView)!!;assertEquals(9f/16,e.width.toFloat()/e.height,.01f)
                assertTrue("IME must leave a visible canvas",e.height>80)
                val visible=android.graphics.Rect();val inputRect=android.graphics.Rect()
                a.window.decorView.getWindowVisibleDisplayFrame(visible)
                assertTrue(input.getGlobalVisibleRect(inputRect));assertTrue("Text must stay above the keyboard",inputRect.bottom<=visible.bottom)
                assertEquals("한글 조합\n두 번째 줄",Store(context).textEdit()!!.layer.text)
            }
            onView(withContentDescription("캔버스 텍스트 입력")).perform(closeSoftKeyboard())
            screenshot("editor-text-styles")
            onView(withContentDescription("텍스트 스타일: Whisper")).perform(scrollTo(),click())
            liveActivity{a->assertEquals("Whisper",Store(context).textEdit()!!.layer.type);assertEquals(.13f,a.window.decorView.findViewWithTag<EditText>("canvas-text-input").letterSpacing,.001f)}
            scenario.recreate()
            onView(withContentDescription("캔버스 텍스트 입력")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("한글 조합\n두 번째 줄")))
            onView(withContentDescription("텍스트 취소")).perform(click())
            liveActivity{assertEquals("이전 문구",it.project.layers.single().text);assertNull(Store(context).textEdit())}
              awaitCanvasAfterIme(scenario)
            val textPoint=point("텍스트 레이어: 이전 문구")
            liveTap(textPoint.first,textPoint.second);SystemClock.sleep(80);liveTap(textPoint.first,textPoint.second)
            var reopened=false
            for(attempt in 0 until 40){liveActivity{reopened=it.window.decorView.findViewWithTag<EditText>("canvas-text-input")!=null};if(reopened)break;SystemClock.sleep(50)}
            assertTrue("Two actual taps must reopen text",reopened)
            onView(withContentDescription("캔버스 텍스트 입력")).perform(replaceText("수정 취소"))
            onView(withContentDescription("텍스트 취소")).perform(click())
            liveActivity{assertEquals("이전 문구",it.project.layers.single().text)}
              awaitCanvasAfterIme(scenario)
            onView(withText("수정")).perform(click())
            onView(withContentDescription("캔버스 텍스트 입력")).perform(replaceText("최종 문구"),closeSoftKeyboard())
            SystemClock.sleep(300) // Wait for the real IME surface to leave before tapping the moved toolbar.
            onView(withContentDescription("텍스트 정렬")).perform(click())
            onView(withText("완료")).perform(click())
            liveActivity{assertEquals("최종 문구",it.project.layers.single().text);assertEquals(2,it.project.layers.single().textLayoutVersion)}
            onView(withContentDescription("실행 취소")).perform(click())
            liveActivity{assertEquals("이전 문구",it.project.layers.single().text);it.project=Project();Store(context).save(it.project)}
        }
    }
    @Test fun motionPreviewNeverExportsAndCropIsOnCanvas(){
        Store(context).clearTextEdit()
        val source=File(context.filesDir,"preview-source.jpg")
        Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(80,90,100))}.let{b->source.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,90,it)}}
        Store(context).save(Project(source=Uri.fromFile(source).toString(),style=Style(name="Flow Blur",motion=true),layers=mutableListOf(Layer(kind="text",text="잔잔하게",motion=true))))
        fun outputs()=File(context.filesDir,"exports").listFiles()?.map{it.name}?.toSet()?:emptySet()
        val before=outputs()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            liveActivity{it.project.duration=10}
            tapLive(scenario,"미리보기 재생");SystemClock.sleep(350)
            liveActivity{a->fun photo(v:View):LivePhoto?{if(v is LivePhoto)return v;if(v is ViewGroup)for(i in 0 until v.childCount)photo(v.getChildAt(i))?.let{return it};return null};assertEquals(10000L,photo(a.window.decorView)!!.durationMs)}
            tapLive(scenario,"미리보기 일시정지")
            onView(withText("편집")).perform(click())
            assertEquals(before,outputs())
            onView(withText("사진 추가")).perform(click());onView(withText("배경 자르기")).perform(click())
            liveActivity{a->
                fun find(v:View):CropPreview?{if(v is CropPreview)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
                val crop=find(a.window.decorView)!!;assertTrue(crop.height>200);assertTrue(crop.parent is android.widget.FrameLayout)
            }
            onView(withText("1:1")).perform(click());onView(withText("취소")).perform(click())
            liveActivity{assertEquals(0f,it.project.crop.aspect);it.project=Project();Store(context).save(it.project)}
        }
    }
    @Test fun koreanTextDragUndoRedoDuplicateAndDelete(){
        Store(context).clearTextEdit()
        val source=File(context.filesDir,"ui-source.jpg")
        val bitmap=Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888);Canvas(bitmap).drawColor(Color.rgb(75,100,92));source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,92,it)}
        Store(context).save(Project(source=Uri.fromFile(source).toString()))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            onView(withContentDescription("텍스트")).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(replaceText("오늘도 차분하게"),closeSoftKeyboard())
            onView(withText("완료")).perform(click())
            instrumentation.waitForIdleSync()
            var x=0f;var y=0f
            liveActivity{a->assertEquals("오늘도 차분하게",a.project.layers.single().text);val e=editor(a.window.decorView)!!;val layer=e.getChildAt(e.childCount-1);val pos=IntArray(2);layer.getLocationOnScreen(pos);x=pos[0]+layer.width/2f;y=pos[1]+layer.height/2f}
            val down=SystemClock.uptimeMillis()
            fun pointer(action:Int,dx:Float,dy:Float){instrumentation.sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+dx,y+dy,0))}
            pointer(MotionEvent.ACTION_DOWN,0f,0f)
            for(i in 1..8){SystemClock.sleep(16);pointer(MotionEvent.ACTION_MOVE,i*8f,i*5f)}
            pointer(MotionEvent.ACTION_UP,64f,40f);instrumentation.waitForIdleSync()
            liveActivity{assertTrue("First drag must move selected layer",it.project.layers.single().x>.53f)}
            val shot=File(context.getExternalFilesDir(null),"validation/editor-ui.png").apply{parentFile!!.mkdirs()}
            instrumentation.uiAutomation.takeScreenshot().let{b->shot.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
            onView(withContentDescription("실행 취소")).perform(click());liveActivity{assertEquals(.5f,it.project.layers.single().x,.005f)}
            onView(withContentDescription("다시 실행")).perform(click());liveActivity{assertTrue(it.project.layers.single().x>.53f)}
            pinchAndRotate(x+64,y+40)
            liveActivity{assertTrue("Pinch must scale",it.project.layers.single().scale>1.05f);assertTrue("Two fingers must rotate",kotlin.math.abs(it.project.layers.single().rotation)>3f)}
            liveActivity{a->val e=editor(a.window.decorView)!!;val v=e.getChildAt(e.childCount-1);assertEquals(v.width/2f,v.pivotX,.01f);assertEquals(v.height/2f,v.pivotY,.01f)}
            // Explicit layer selection exposes the context toolbar even after history restoration.
            onView(withContentDescription("더보기")).perform(click());onView(withText("레이어 목록")).perform(scrollTo(),click());onView(withText("오늘도 차분하게")).perform(click())
            onView(withContentDescription("레이어 추가 작업")).perform(click());onView(withText("복제")).perform(click());liveActivity{assertEquals(2,it.project.layers.size)}
            onView(withContentDescription("레이어 추가 작업")).perform(click());onView(withText("삭제")).perform(scrollTo(),click());liveActivity{assertEquals(1,it.project.layers.size)}
            scenario.recreate();liveActivity{assertEquals("오늘도 차분하게",it.project.layers.single().text)}
            liveActivity{it.project=Project();Store(context).save(it.project)}
        }
    }
    private fun pinchAndRotate(cx:Float,cy:Float){
        val start=SystemClock.uptimeMillis()
        fun event(action:Int,dx:Float,dy:Float,two:Boolean){
            val count=if(two)2 else 1
            val properties=Array(count){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
            val coords=Array(count){i->MotionEvent.PointerCoords().apply{x=cx+(if(i==0)-dx else dx);y=cy+(if(i==0)-dy else dy);pressure=1f;size=1f}}
            val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            instrumentation.sendPointerSync(e);e.recycle();SystemClock.sleep(25)
        }
        event(MotionEvent.ACTION_DOWN,30f,0f,false)
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),30f,0f,true)
        for(i in 1..8)event(MotionEvent.ACTION_MOVE,30f+i*3f,i*2f,true)
        event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),54f,16f,true)
        event(MotionEvent.ACTION_UP,54f,16f,false);instrumentation.waitForIdleSync()
    }
    @Test fun videoPreviewDecodesWithoutExport(){
        Store(context).clearTextEdit()
        val source=File(context.cacheDir,"ui-video.mp4")
        instrumentation.context.assets.open("source-30s.mp4").use{i->source.outputStream().use{i.copyTo(it)}}
        Store(context).save(Project(source=Uri.fromFile(source).toString(),video=true,sourceDurationMs=30000))
        fun player(v:View):androidx.media3.ui.PlayerView?{
            if(v is androidx.media3.ui.PlayerView)return v
            if(v is ViewGroup)for(i in 0 until v.childCount)player(v.getChildAt(i))?.let{return it}
            return null
        }
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            var ready=false
            for(i in 0 until 100){liveActivity{ready=player(it.window.decorView)?.player?.playbackState==androidx.media3.common.Player.STATE_READY};if(ready)break;SystemClock.sleep(100)}
            assertTrue("Source video must play through the decoder/GPU preview",ready)
            onView(withContentDescription("배경 사진")).perform(swipeRight())
            liveActivity{assertTrue("Video source must support direct drag",it.project.pose.x>.55f)}
            screenshot("ux-video-direct")
            onView(withContentDescription("실행 취소")).perform(click())
            liveActivity{assertEquals(.5f,it.project.pose.x,.005f)}
            liveActivity{it.project=Project();Store(context).save(it.project)}
        }
    }
}
