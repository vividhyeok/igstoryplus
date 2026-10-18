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
    private fun editor(v:View):PhotoEditorView? {
        if(v is PhotoEditorView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)editor(v.getChildAt(i))?.let{return it}
        return null
    }
    @Test fun koreanTextDragUndoRedoDuplicateAndDelete(){
        val source=File(context.filesDir,"ui-source.jpg")
        val bitmap=Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888);Canvas(bitmap).drawColor(Color.rgb(75,100,92));source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,92,it)}
        Store(context).save(Project(source=Uri.fromFile(source).toString()))
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            onView(withText("Aa 텍스트")).perform(click())
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
            onView(withText("↶")).perform(click());scenario.onActivity{assertEquals(.5f,it.project.layers.single().x,.005f)}
            onView(withText("↷")).perform(click());scenario.onActivity{assertTrue(it.project.layers.single().x>.53f)}
            pinchAndRotate(x+64,y+40)
            scenario.onActivity{assertTrue("Pinch must scale",it.project.layers.single().scale>1.05f);assertTrue("Two fingers must rotate",kotlin.math.abs(it.project.layers.single().rotation)>3f)}
            // Explicit layer selection exposes the context toolbar even after history restoration.
            onView(withText("더보기")).perform(click());onView(withText("레이어 목록")).perform(click());onView(withText("오늘도 차분하게")).perform(click())
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
}
