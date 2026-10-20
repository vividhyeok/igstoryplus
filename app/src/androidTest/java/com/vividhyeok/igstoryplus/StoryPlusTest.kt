package com.vividhyeok.igstoryplus

import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.text.StaticLayout
import android.text.TextPaint
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StoryPlusTest{
    private val ins=InstrumentationRegistry.getInstrumentation();private val c=ins.targetContext
    private val evidence=File(c.getExternalFilesDir(null),"validation").apply{mkdirs()}
    private fun activity(action:(MainActivity)->Unit){ins.runOnMainSync{action(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single())}}
    private fun image(b:Bitmap,name:String){File(evidence,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun photo():String{val f=File(c.filesDir,"plus-photo.jpg");ins.context.assets.open("scenes/nature.jpg").use{i->f.outputStream().use{i.copyTo(it)}};return Uri.fromFile(f).toString()}
    @Test fun inlineImageBackgroundHasNoDoubleAlphaSeamOrInternalOutline(){
        val icons=EmojiLibrary.defaults(c);val layer=Layer(kind="text",type="클래식",textLayoutVersion=1,text="가\uE000나\n가\uE001나",textBackground="반투명",inlineEmoji=mutableMapOf("\uE000" to icons.first{it.id=="jesus"}.uri,"\uE001" to icons.first{it.id=="church"}.uri))
        val text=InlineEmoji.styled(c,layer);val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{textSize=Artwork.textSize(layer);typeface=Artwork.typeface(layer);letterSpacing=Artwork.letterSpacing(layer)}
        val layout=StaticLayout.Builder.obtain(text,0,text.length,paint,880).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
        val join=32+layout.getLineTop(1)
        for(mode in listOf("채움","반투명","라인")){
            layer.textBackground=mode;val b=Bitmap.createBitmap(960,layout.height+64,Bitmap.Config.ARGB_8888);Artwork.textDecoration(Canvas(b),layer,b.height,layout)
            assertEquals("Joined $mode must not redraw a seam",when(mode){"채움"->255;"반투명"->166;else->0},Color.alpha(b.getPixel(480,join)))
            image(Artwork.text(c,layer),"plus-inline-background-$mode")
        }
        val editable=android.text.SpannableStringBuilder(text);val span=editable.getSpans(0,editable.length,InlineEmoji.ImageGlyph::class.java).first();editable.append(" 추가");InlineEmoji.apply(c,layer,editable)
        assertTrue("Typing must retain existing image spans instead of reflowing every glyph",editable.getSpans(0,editable.length,InlineEmoji.ImageGlyph::class.java).any{it===span})
    }
    @Test fun originalVideoAudioDrivesActualHeartAndAuraMp4WithoutDuplicateMusic(){
        val input=File(c.filesDir,"plus-original-5s.mp4");ins.context.assets.open("source-mix-5s.mp4").use{i->input.outputStream().use{i.copyTo(it)}}
        val layer=Layer(kind="text",type="클래식",text="반짝이는 오늘",textLayoutVersion=1,textBackground="채움",width=.7f,accent="하트 글리터",accentAmount=1f,beatMotion=true,y=.5f)
        val project=Project(source=Uri.fromFile(input).toString(),video=true,sourceDurationMs=5000,reactionAudio="원본 영상",style=Style(beat=.7f,reaction="Album Aura",reactionTarget="원본 화면 전체"),layers=mutableListOf(layer))
        assertTrue(Audio.hasAudio(c,project.source));assertNull(project.music)
        val features=Audio.features(c,Audio.reactionMusic(project)!!,project.durationMs());assertTrue("Video soundtrack must produce nonzero reaction",features.energy.any{it>.1f})
        val b=Artwork.text(c,layer);val overlay=Exporter.overlay(layer,b,5000,null,features.onsets,project.style)
        val first=overlay.getBitmap(0).copy(Bitmap.Config.ARGB_8888,false);val later=overlay.getBitmap(900000).copy(Bitmap.Config.ARGB_8888,false)
        assertFalse("Hearts must animate",first.sameAs(later));image(first,"plus-heart-start");image(later,"plus-heart-later")
        val latch=CountDownLatch(1);var result:Result<File>?=null
        ins.runOnMainSync{Exporter(c).export(project,{}){result=it;latch.countDown()}}
        assertTrue("5s original-audio export timeout",latch.await(90,TimeUnit.SECONDS));val file=result!!.getOrThrow();Exporter.inspect(file,project,true)
        file.copyTo(File(evidence,"plus-original-audio-hearts.mp4"),true)
        val extractor=android.media.MediaExtractor();try{extractor.setDataSource(file.absolutePath);assertEquals(1,(0 until extractor.trackCount).count{extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/")==true})}finally{extractor.release()}
    }
    @Test fun reactionTargetsProtectPhotoAndIconsShareOneVectorStyle(){
        val p=Project(source=photo());val base=Artwork.background(c,p,270,drawPhoto=false);val fg=Artwork.cropped(Artwork.decode(c,p.source,540),p.crop)
        val styles=listOf(Style(beat=.8f,reactionTarget="배경만",region="전체",scope=1f),Style(beat=.8f,reactionTarget="원본 화면 전체",region="전체",scope=1f))
        val reference=filteredBitmaps(base,listOf(styles.first()),fg,beat=0f).single();val frames=filteredBitmaps(base,styles,fg,beat=1f)
        assertEquals("Background-only light must protect foreground",reference.getPixel(135,240),frames[0].getPixel(135,240));assertNotEquals(frames[0].getPixel(135,240),frames[1].getPixel(135,240));image(frames[0],"plus-background-only");image(frames[1],"plus-whole-source")
        val names=listOf("텍스트","스티커","음악","사진 추가","스타일","플러스","배치","잠금","가로 중앙","세로 중앙","수평","텍스트 배경","색상","복제","삭제","교체")
        val atlas=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);val canvas=Canvas(atlas);canvas.drawColor(0xFF16161B.toInt());val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=18f;textAlign=Paint.Align.CENTER}
        names.forEachIndexed{i,n->val x=(i%4)*160;val y=(i/4)*120;EditorIcons.drawable(n).apply{setBounds(x+52,y+12,x+108,y+68);draw(canvas)};canvas.drawText(n,x+80f,y+100f,paint)};image(atlas,"plus-vector-icons")
    }
    @Test fun arrangementLockAndSafeGuidesAreReversibleAndDoNotCoverCanvas(){
        Store(c).clearTextEdit();val uri=photo();Store(c).save(Project(source=uri,layers=mutableListOf(Layer(uri=uri,x=.32f,y=.42f,width=.45f))))
        ActivityScenario.launch(MainActivity::class.java).use{
            onView(withContentDescription("photo 레이어")).perform(click());onView(withContentDescription("배치")).perform(click())
            onView(withContentDescription("가로 중앙")).perform(click());activity{assertEquals(.5f,it.project.layers.single().x,.001f)}
            onView(withText("위치 잠금 · 레이어 목록에서 해제 가능")).perform(click());onView(withText("게시 UI 여백 안내 · 출력에는 포함 안 됨")).perform(click())
            onView(withContentDescription("시트 완료")).perform(click());onView(withContentDescription("photo 레이어")).perform(swipeLeft());activity{assertEquals(.5f,it.project.layers.single().x,.001f);assertTrue(it.project.layers.single().locked)}
            onView(withContentDescription("배치")).perform(click());onView(withText("위치 잠금 · 레이어 목록에서 해제 가능")).perform(click());onView(withContentDescription("시트 완료")).perform(click())
            onView(withContentDescription("플러스")).perform(click());onView(withText("하트 글리터")).perform(click());activity{assertEquals("하트 글리터",it.project.layers.single().accent)}
            onView(withContentDescription("시트 완료")).perform(click());SystemClock.sleep(500);image(ins.uiAutomation.takeScreenshot(),"plus-arrange-ui")
        }
    }
}
