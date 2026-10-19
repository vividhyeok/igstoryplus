package ja.burhanrashid52.photoeditor

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class MultiTouchListenerTest {
    @Test fun queuedDoubleTapOpensOnceAfterGestureEnds(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val editor=PhotoEditorView(context);val view=View(context)
        val listener=MultiTouchListener(null,editor,null,true,null,PhotoEditorViewState())
        var editing=0;var transforming=false
        editor.onLayerGesture={_,start->transforming=start}
        editor.onLayerDoubleTap={assertFalse(transforming);editing++}
        fun event(down:Long,time:Long,action:Int,x:Float=50f){
            MotionEvent.obtain(down,time,action,x,50f,0).also{listener.onTouch(view,it);it.recycle()}
        }
        event(1000,1000,MotionEvent.ACTION_DOWN);event(1000,1010,MotionEvent.ACTION_UP)
        // A queued input pair still belongs together after the main thread was busy.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        event(1100,1100,MotionEvent.ACTION_DOWN);event(1100,1110,MotionEvent.ACTION_UP)
        assertEquals(1,editing)
        event(1200,1200,MotionEvent.ACTION_DOWN);event(1200,1210,MotionEvent.ACTION_UP)
        assertEquals("Third tap must not open twice",1,editing)
        event(2000,2000,MotionEvent.ACTION_DOWN);event(2000,2010,MotionEvent.ACTION_MOVE,200f);event(2000,2020,MotionEvent.ACTION_UP,200f)
        event(2100,2100,MotionEvent.ACTION_DOWN);event(2100,2110,MotionEvent.ACTION_UP)
        assertEquals("Dragging must not count as a tap",1,editing)
    }
}
