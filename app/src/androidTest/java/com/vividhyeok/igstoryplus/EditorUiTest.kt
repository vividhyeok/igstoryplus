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
    private fun screenshot(name:String){val f=File(context.getExternalFilesDir(null),"validation/$name.png").apply{parentFile!!.mkdirs()};instrumentation.uiAutomation.takeScreenshot().let{b->f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    private fun editor(v:View):PhotoEditorView? {
        if(v is PhotoEditorView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)editor(v.getChildAt(i))?.let{return it}
        return null
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
            scenario.onActivity{assertEquals("music",it.project.layers.first().kind)}
            onView(withContentDescription("레이어 추가 작업")).perform(scrollTo(),click())
            onView(withText("미세 모션 OFF")).perform(click())
            scenario.onActivity{assertTrue(it.project.layers.first{l->l.kind=="music"}.motion)}
            onView(withContentDescription("레이어 추가 작업")).perform(scrollTo(),click())
            onView(withText("복제")).perform(click())
            scenario.onActivity{assertEquals(2,it.project.layers.count{l->l.kind=="music"})}
            onView(withText("카드 숨김")).perform(scrollTo(),click())
            scenario.onActivity{assertNotNull(it.project.music);assertTrue(it.project.layers.none{l->l.kind=="music"});it.project=Project();Store(context).save(it.project)}
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
            for(i in 0 until 40){scenario.onActivity{a->imeVisible=androidx.core.view.ViewCompat.getRootWindowInsets(a.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true};if(imeVisible)break;SystemClock.sleep(100)}
            assertTrue("The soft keyboard must actually be visible, not just an editable field",imeVisible)
            scenario.onActivity{a->
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
            scenario.onActivity{a->
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
            onView(withText("스타일")).perform(click())
            screenshot("editor-text-styles")
            onView(withContentDescription("Whisper")).perform(scrollTo(),click())
            scenario.onActivity{a->assertEquals("Whisper",Store(context).textEdit()!!.layer.type);assertEquals(.13f,a.window.decorView.findViewWithTag<EditText>("canvas-text-input").letterSpacing,.001f)}
            scenario.recreate()
            onView(withContentDescription("캔버스 텍스트 입력")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("한글 조합\n두 번째 줄")))
            onView(withText("취소")).perform(click())
            scenario.onActivity{assertEquals("이전 문구",it.project.layers.single().text);assertNull(Store(context).textEdit())}
            onView(withContentDescription("텍스트 레이어: 이전 문구")).perform(doubleClick())
            onView(withContentDescription("캔버스 텍스트 입력")).perform(replaceText("수정 취소"))
            onView(withText("취소")).perform(click())
            scenario.onActivity{assertEquals("이전 문구",it.project.layers.single().text)}
            onView(withText("수정")).perform(click())
            onView(withContentDescription("캔버스 텍스트 입력")).perform(replaceText("최종 문구"),closeSoftKeyboard())
            onView(withText("정렬")).perform(click())
            onView(withText("완료")).perform(click())
            scenario.onActivity{assertEquals("최종 문구",it.project.layers.single().text);assertEquals(1,it.project.layers.single().textLayoutVersion)}
            onView(withContentDescription("실행 취소")).perform(click())
            scenario.onActivity{assertEquals("이전 문구",it.project.layers.single().text);it.project=Project();Store(context).save(it.project)}
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
            scenario.onActivity{it.project.duration=10}
            onView(withContentDescription("미리보기 재생")).perform(click());SystemClock.sleep(350)
            scenario.onActivity{a->fun photo(v:View):LivePhoto?{if(v is LivePhoto)return v;if(v is ViewGroup)for(i in 0 until v.childCount)photo(v.getChildAt(i))?.let{return it};return null};assertEquals(10000L,photo(a.window.decorView)!!.durationMs)}
            onView(withContentDescription("미리보기 일시정지")).perform(click())
            assertEquals(before,outputs())
            onView(withText("사진 추가")).perform(click());onView(withText("배경 자르기")).perform(click())
            scenario.onActivity{a->
                fun find(v:View):CropPreview?{if(v is CropPreview)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
                val crop=find(a.window.decorView)!!;assertTrue(crop.height>200);assertTrue(crop.parent is android.widget.FrameLayout)
            }
            onView(withText("1:1")).perform(click());onView(withText("취소")).perform(click())
            scenario.onActivity{assertEquals(0f,it.project.crop.aspect);it.project=Project();Store(context).save(it.project)}
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
            scenario.onActivity{a->assertEquals("오늘도 차분하게",a.project.layers.single().text);val e=editor(a.window.decorView)!!;val layer=e.getChildAt(e.childCount-1);val pos=IntArray(2);layer.getLocationOnScreen(pos);x=pos[0]+layer.width/2f;y=pos[1]+layer.height/2f}
            val down=SystemClock.uptimeMillis()
            fun pointer(action:Int,dx:Float,dy:Float){instrumentation.sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+dx,y+dy,0))}
            pointer(MotionEvent.ACTION_DOWN,0f,0f)
            for(i in 1..8){SystemClock.sleep(16);pointer(MotionEvent.ACTION_MOVE,i*8f,i*5f)}
            pointer(MotionEvent.ACTION_UP,64f,40f);instrumentation.waitForIdleSync()
            scenario.onActivity{assertTrue("First drag must move selected layer",it.project.layers.single().x>.53f)}
            val shot=File(context.getExternalFilesDir(null),"validation/editor-ui.png").apply{parentFile!!.mkdirs()}
            instrumentation.uiAutomation.takeScreenshot().let{b->shot.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
            onView(withContentDescription("실행 취소")).perform(click());scenario.onActivity{assertEquals(.5f,it.project.layers.single().x,.005f)}
            onView(withContentDescription("다시 실행")).perform(click());scenario.onActivity{assertTrue(it.project.layers.single().x>.53f)}
            pinchAndRotate(x+64,y+40)
            scenario.onActivity{assertTrue("Pinch must scale",it.project.layers.single().scale>1.05f);assertTrue("Two fingers must rotate",kotlin.math.abs(it.project.layers.single().rotation)>3f)}
            scenario.onActivity{a->val e=editor(a.window.decorView)!!;val v=e.getChildAt(e.childCount-1);assertEquals(v.width/2f,v.pivotX,.01f);assertEquals(v.height/2f,v.pivotY,.01f)}
            // Explicit layer selection exposes the context toolbar even after history restoration.
            onView(withContentDescription("더보기")).perform(click());onView(withText("레이어 목록")).perform(scrollTo(),click());onView(withText("오늘도 차분하게")).perform(click())
            onView(withText("복제")).perform(click());scenario.onActivity{assertEquals(2,it.project.layers.size)}
            onView(withText("삭제")).perform(scrollTo(),click());scenario.onActivity{assertEquals(1,it.project.layers.size)}
            scenario.recreate();scenario.onActivity{assertEquals("오늘도 차분하게",it.project.layers.single().text)}
            scenario.onActivity{it.project=Project();Store(context).save(it.project)}
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
            for(i in 0 until 100){scenario.onActivity{ready=player(it.window.decorView)?.player?.playbackState==androidx.media3.common.Player.STATE_READY};if(ready)break;SystemClock.sleep(100)}
            assertTrue("Source video must play through the decoder/GPU preview",ready)
            scenario.onActivity{it.project=Project();Store(context).save(it.project)}
        }
    }
}
