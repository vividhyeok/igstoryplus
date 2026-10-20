package com.vividhyeok.igstoryplus

import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.text.*
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
class ReleaseV051Test{
    private val ins=InstrumentationRegistry.getInstrumentation();private val c=ins.targetContext
    private val evidence=File(c.getExternalFilesDir(null),"validation").apply{mkdirs()}
    private fun activity(action:(MainActivity)->Unit){ins.runOnMainSync{action(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single())}}
    private fun photo():String{val f=File(c.filesDir,"v051-source.jpg");ins.context.assets.open("scenes/portrait.jpg").use{i->f.outputStream().use{i.copyTo(it)}};return Uri.fromFile(f).toString()}
    private fun save(b:Bitmap,name:String){File(evidence,"v051-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun plusTabsAndSelectionAreClearOnOriginalVideo(){
        val file=File(c.filesDir,"v051-ui-video.mp4");ins.context.assets.open("source-mix-5s.mp4").use{i->file.outputStream().use{i.copyTo(it)}}
        val uri=photo();Store(c).clearTextEdit();Store(c).save(Project(source=Uri.fromFile(file).toString(),video=true,sourceDurationMs=5000,layers=mutableListOf(Layer(uri=uri,width=.4f))))
        ActivityScenario.launch(MainActivity::class.java).use{
            var visibleFrame=false
            repeat(60){if(!visibleFrame){SystemClock.sleep(100);activity{a->
                fun find(v:View):Bitmap?{if(v is ImageView && v.contentDescription=="동영상 첫 화면" && v.visibility==View.VISIBLE)return (v.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap;if(v is androidx.media3.ui.PlayerView){val p=a.window.decorView.findViewWithTag<ImageView>("video-poster");if(p?.visibility==View.VISIBLE)return (p.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap;return (v.videoSurfaceView as? TextureView)?.bitmap};if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
                val bitmap=find(a.window.decorView);if(bitmap!=null){var colored=0;for(y in 0 until bitmap.height step 20)for(x in 0 until bitmap.width step 20){val color=bitmap.getPixel(x,y);if(Color.red(color)+Color.green(color)+Color.blue(color)>60)colored++};visibleFrame=colored>100}
            }}}
            assertTrue("Paused video must show a sharp source frame, not a blank or blurred background",visibleFrame)
            onView(withContentDescription("플러스")).perform(click());onView(withText("레이어 효과")).perform(click());onView(withText("하트")).perform(click())
            onView(withText("하트")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isSelected()))
            onView(withContentDescription("효과 적용 레이어")).check{v,_->assertEquals(Color.WHITE,((v as Spinner).selectedView as TextView).currentTextColor)}
            save(ins.uiAutomation.takeScreenshot(),"plus-layer-ui")
            onView(withText("음악 반응")).perform(scrollTo(),click());onView(withText("컬러 오라")).perform(scrollTo(),click())
            activity{a->assertEquals("Album Aura",a.project.style.reaction);assertEquals("원본 화면 전체",a.project.style.reactionTarget);assertFalse(a.project.style.videoFilters);assertEquals("하트 글리터",a.project.layers.single().accent)}
            onView(withText("음악 반응")).perform(scrollTo());save(ins.uiAutomation.takeScreenshot(),"plus-music-ui")
            onView(withContentDescription("시트 완료")).perform(click())
        }
    }
    @Test fun smallTextReflowsAndLineWidthDoesNotRescaleFont(){
        Store(c).clearTextEdit();Store(c).save(Project(source=photo()))
        val l=Layer(kind="text",text="오늘의 작은 기록을 소중하게 간직하는 마음으로 함께 걷고 싶은 하루입니다. 오늘의 작은 기록을 소중하게 간직하는 마음으로 함께 걷고 싶은 하루입니다.",type="클래식",textLayoutVersion=2,textBackground="채움",width=.8f,textSizeScale=1.4f)
        Store(c).saveTextEdit(null,l)
        ActivityScenario.launch(MainActivity::class.java).use{
            var before=0;var initialSize=0f
            onView(withContentDescription("텍스트 개인 이모지")).perform(click())
            activity{a->val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input");before=input.lineCount;initialSize=input.textSize}
            onView(withContentDescription("텍스트 크기")).perform(swipeDown());SystemClock.sleep(200)
            activity{a->val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input");assertTrue(input.textSize<initialSize);assertTrue("Small type must reflow into fewer lines",input.lineCount<before)}
            onView(withContentDescription("텍스트 추가 설정")).perform(click())
            var physical=0f;var lines=0
            activity{a->val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input");physical=input.textSize*Store(c).textEdit()!!.layer.width;lines=input.lineCount}
            onView(withContentDescription("텍스트 줄 너비")).perform(swipeLeft());SystemClock.sleep(200)
            activity{a->val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input");val draft=Store(c).textEdit()!!.layer;assertEquals(physical,input.textSize*draft.width,.2f);assertTrue(input.lineCount>lines)}
            save(ins.uiAutomation.takeScreenshot(),"small-text-width")
            onView(withContentDescription("텍스트 완료")).perform(click())
            activity{a->save(Artwork.text(c,a.project.layers.single()),"small-text-output")}
        }
    }
    @Test fun all29CustomEmojiRenderAndOnlyCustomEmojiEntryRemains(){
        val items=EmojiLibrary.defaults(c);assertEquals(29,items.size);assertEquals(29,items.map{it.uri}.distinct().size)
        val l=Layer(kind="text",type="클래식",textLayoutVersion=2,textBackground="채움",width=.8f,textSizeScale=.6f)
        items.forEach{val b=Artwork.decode(c,it.uri);assertEquals(512,b.width);assertTrue(b.hasAlpha());l.text+=InlineEmoji.token(l,it.uri)+" "}
        val sp=InlineEmoji.styled(c,l) as Spanned;assertEquals(29,sp.getSpans(0,sp.length,InlineEmoji.ImageGlyph::class.java).size)
        val copy=Store(c).gson.fromJson(Store(c).gson.toJson(l),Layer::class.java);assertEquals(l.inlineEmoji,copy.inlineEmoji);save(Artwork.text(c,copy),"all29-inline")
        Store(c).clearTextEdit();Store(c).save(Project(source=photo()))
        ActivityScenario.launch(MainActivity::class.java).use{
            onView(withContentDescription("텍스트")).perform(click());onView(withContentDescription("텍스트 이모지")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            onView(withContentDescription("텍스트 개인 이모지")).perform(click())
            onView(withContentDescription("내 이모지 목록")).check{v,_->(v as androidx.recyclerview.widget.RecyclerView).scrollToPosition(28)};SystemClock.sleep(300)
            onView(withContentDescription("문장 이모지: ${items.last().name}")).perform(click());activity{a->val input=a.window.decorView.findViewWithTag<EditText>("canvas-text-input");assertEquals(1,input.text.getSpans(0,input.length(),InlineEmoji.ImageGlyph::class.java).size)}
            save(ins.uiAutomation.takeScreenshot(),"custom-picker")
            onView(withContentDescription("텍스트 완료")).perform(click())
        }
    }
    @Test fun smallCustomEmojiBackgroundIsJoinedWithoutClipping(){
        val item=EmojiLibrary.defaults(c).first{it.id=="jesus"}
        for(size in listOf(.25f,.4f,.7f,1.4f))for(mode in listOf("채움","반투명","라인")){
            val l=Layer(kind="text",type="클래식",text="가\uE000나\n가\uE000나",textLayoutVersion=2,textSizeScale=size,width=.8f,textBackground=mode,inlineEmoji=mutableMapOf("\uE000" to item.uri))
            val text=InlineEmoji.styled(c,l);val px=TextGeometry.horizontalPadding(l);val py=TextGeometry.verticalPadding(l)
            val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{textSize=Artwork.textSize(l);typeface=Artwork.typeface(l);letterSpacing=Artwork.letterSpacing(l)}
            val layout=StaticLayout.Builder.obtain(text,0,text.length,p,960-2*px).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
            val background=Bitmap.createBitmap(960,layout.height+2*py,Bitmap.Config.ARGB_8888);Artwork.textDecoration(Canvas(background),l,background.height,layout)
            val join=py+layout.getLineTop(1);assertEquals(if(mode=="채움")255 else if(mode=="반투명")166 else 0,Color.alpha(background.getPixel(480,join)))
            assertEquals(0,Color.alpha(background.getPixel(0,0)));assertEquals(0,Color.alpha(background.getPixel(959,background.height-1)))
            save(Artwork.text(c,l),"background-$size-$mode")
        }
    }
    @Test fun videoFiltersAreExplicitAndNewColorLooksAreDistinct(){
        val source=Artwork.decode(c,photo(),360)
        val off=listOf("Simple","Flow Blur","Zoomblur","Shutter Drag","Sensor Raw").map{Style(name=it,amount=1f,motion=true)}
        val outputs=filteredBitmaps(source,off,video=true)
        outputs.drop(1).forEach{assertTrue("Photo blur effects must not leak onto a video",outputs.first().sameAs(it))}
        val on=filteredBitmaps(source,listOf(Style(name="Flow Blur",amount=1f,videoFilters=true)),video=true).single();assertFalse(on.sameAs(outputs.first()))
        val looks=listOf("Clean","Warm Film","Soft Fade","Mono","Cool Night");val styled=filteredBitmaps(source,looks.map{Style(name=it,amount=.8f)})
        styled.forEachIndexed{i,b->assertFalse("${looks[i]} must visibly change the photo",source.sameAs(b));save(b,"look-${looks[i]}")}
        val mono=styled[3];var difference=0L;for(y in 0 until mono.height step 3)for(x in 0 until mono.width step 3){val p=mono.getPixel(x,y);difference+=kotlin.math.abs(Color.red(p)-Color.blue(p))};assertTrue(difference>0)
    }
}
