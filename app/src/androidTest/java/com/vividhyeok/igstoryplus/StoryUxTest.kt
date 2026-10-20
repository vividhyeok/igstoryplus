package com.vividhyeok.igstoryplus

import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StoryUxTest{
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val c=instrumentation.targetContext
    private fun activity(action:(MainActivity)->Unit){instrumentation.runOnMainSync{action(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single())}}
    private fun chrome(a:MainActivity)=a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as EditorChrome
    private fun source():String{val file=File(c.filesDir,"story-real-photo.jpg");instrumentation.context.assets.open("scenes/nature.jpg").use{i->file.outputStream().use{i.copyTo(it)}};return Uri.fromFile(file).toString()}
    private fun capture(name:String){SystemClock.sleep(250);val file=File(c.getExternalFilesDir(null),"validation/$name.png").apply{parentFile!!.mkdirs()};instrumentation.uiAutomation.takeScreenshot().let{image->file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    private fun assertUncovered(){activity{a->val ui=chrome(a);fun editor(v:View):ja.burhanrashid52.photoeditor.PhotoEditorView?{if(v is ja.burhanrashid52.photoeditor.PhotoEditorView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)editor(v.getChildAt(i))?.let{return it};return null};val rect=Rect();editor(ui)!!.getGlobalVisibleRect(rect);val tray=Rect();ui.tray.getGlobalVisibleRect(tray);assertTrue("Controls must live below the complete story, not cover it",rect.bottom<=tray.top);assertTrue(rect.height()>180)}}
    @Test fun unicodeEmojiCursorBackgroundAndSingleTapReedit(){
        Store(c).clearTextEdit();Store(c).save(Project(source=source()))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            onView(withContentDescription("텍스트")).perform(click())
            onView(withContentDescription("캔버스 텍스트 입력")).perform(replaceText("오늘 😀 좋은 날"))
            activity{a->a.window.decorView.findViewWithTag<EditText>("canvas-text-input").setSelection(3,5)}
            onView(withContentDescription("텍스트 이모지")).perform(click());SystemClock.sleep(400)
            onView(withContentDescription("문자 이모지 😃")).perform(click())
            activity{a->val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input");assertEquals("오늘 😃 좋은 날",input.text.toString());assertEquals(5,input.selectionStart);assertTrue(a.project.layers.isEmpty())}
            onView(withContentDescription("텍스트 배경")).perform(click())
            onView(withContentDescription("텍스트 스타일: 굵게")).perform(click())
            assertUncovered();capture("story-inline-text-emoji")
            onView(withContentDescription("텍스트 완료")).perform(click());SystemClock.sleep(350)
            activity{a->val layer=a.project.layers.single();assertEquals("오늘 😃 좋은 날",layer.text);assertEquals("채움",layer.textBackground);assertEquals("굵게",layer.type)
                val bitmap=Artwork.text(c,layer);assertEquals(0,Color.alpha(bitmap.getPixel(0,0)));var colored=0;for(y in 0 until bitmap.height)for(x in 0 until bitmap.width){val p=bitmap.getPixel(x,y);if(Color.alpha(p)>100 && Color.red(p)>180 && Color.green(p)>80 && Color.blue(p)<120)colored++};assertTrue("Unicode emoji must keep its color glyph in output",colored>20)
                File(c.getExternalFilesDir(null),"validation/story-unicode-output.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            scenario.recreate();onView(withContentDescription("텍스트 레이어: 오늘 😃 좋은 날")).perform(click())
            onView(withContentDescription("캔버스 텍스트 입력")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("오늘 😃 좋은 날")))
            onView(withContentDescription("텍스트 취소")).perform(click());capture("story-main-text")
        }
    }
    @Test fun photoAndMusicControlsKeepTheWholeCanvasVisible(){
        Store(c).clearTextEdit();val source=source();val file=File(c.filesDir,"story-audio.wav");val rate=22050;val samples=rate*6;val bytes=java.nio.ByteBuffer.allocate(44+samples*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples*2)
        repeat(samples){bytes.putShort((kotlin.math.sin(it*2*kotlin.math.PI*220/rate)*4000).toInt().toShort())};file.writeBytes(bytes.array())
        val music=Audio.metadata(c,Uri.fromFile(file).toString()).apply{title="서로의 하루 · 아주 긴 한글 제목도 보여요";artist="작은 기록";art=source;card="스토리"}
        Store(c).save(Project(source=source,music=music,layers=mutableListOf(Layer(kind="music",width=.62f,y=.8f))))
        ActivityScenario.launch(MainActivity::class.java).use{
            onView(withContentDescription("음악")).perform(click());SystemClock.sleep(350);assertUncovered();capture("story-music-range-dock")
            onView(withContentDescription("음악 카드 디자인")).perform(scrollTo(),click())
            onView(withContentDescription("음악 카드: 스토리")).perform(click());assertUncovered();capture("story-music-card-dock")
            onView(withContentDescription("시트 완료")).perform(click())
            onView(withText("스타일")).perform(click());onView(withContentDescription("사진 스타일: Gritty")).perform(click())
            var thumbnailReady=false
            repeat(40){if(!thumbnailReady){SystemClock.sleep(100);onView(withContentDescription("사진 스타일: Gritty")).check{v,_->thumbnailReady=(v as ImageView).drawable!=null}}}
            assertTrue("Photo style choices must show image thumbnails",thumbnailReady)
            onView(withContentDescription("사진 스타일 강도")).perform(scrollTo(),click());assertUncovered();capture("story-photo-style-dock")
            onView(withContentDescription("시트 완료")).perform(click())
            activity{a->assertEquals(View.GONE,chrome(a).tray.visibility);assertEquals("스토리",a.project.music!!.card)}
        }
    }
    @Test fun layerPinchUsesPhysicalFingerAngleAndCanLevelInOneTap(){
        Store(c).clearTextEdit();val source=source();Store(c).save(Project(source=source,layers=mutableListOf(Layer(uri=source,width=.6f))))
        ActivityScenario.launch(MainActivity::class.java).use{
            SystemClock.sleep(500);var cx=0f;var cy=0f
            onView(withContentDescription("photo 레이어")).check{v,_->val r=Rect();v.getGlobalVisibleRect(r);cx=r.exactCenterX();cy=r.exactCenterY()}
            val start=SystemClock.uptimeMillis()
            fun send(action:Int,dx:Float,dy:Float,two:Boolean){val count=if(two)2 else 1;val props=Array(count){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}};val coords=Array(count){i->MotionEvent.PointerCoords().apply{x=cx+(if(i==0)-dx else dx);y=cy+(if(i==0)-dy else dy);pressure=1f;size=1f}};val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);instrumentation.sendPointerSync(e);e.recycle();SystemClock.sleep(25)}
            send(MotionEvent.ACTION_DOWN,30f,0f,false);send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),30f,0f,true)
            for(i in 1..8)send(MotionEvent.ACTION_MOVE,30f+i*3f,i*2f,true)
            capture("story-alignment-live")
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),54f,16f,true);send(MotionEvent.ACTION_UP,54f,16f,false)
            activity{a->assertEquals(16.5f,a.project.layers.single().rotation,3f);assertEquals(1.877f,a.project.layers.single().scale,.18f)}
            onView(withText("수평")).perform(click());activity{assertEquals(0f,it.project.layers.single().rotation,0f)}
            capture("story-layer-level")
        }
    }
}
