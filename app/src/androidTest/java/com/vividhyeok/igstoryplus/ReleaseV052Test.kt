package com.vividhyeok.igstoryplus

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReleaseV052Test {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val c=ins.targetContext
    private fun capture(name:String){val dir=File(c.getExternalFilesDir(null),"validation").apply{mkdirs()};File(dir,"v052-$name.png").outputStream().use{ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun newDailyStickersHaveTransparentMarginsAndRestoreInline(){
        val items=EmojiLibrary.defaults(c);assertEquals(33,items.size)
        val layer=Layer(kind="text",text="오늘의 취향 ")
        listOf("tea-time","open-book","coding-laptop","headphones").forEach{id->
            val item=items.single{it.id==id};val b=Artwork.decode(c,item.uri)
            assertEquals(512,b.width);assertEquals(512,b.height);assertEquals(0,Color.alpha(b.getPixel(0,0)));var visible=0;for(y in 0 until 512 step 8)for(x in 0 until 512 step 8)if(Color.alpha(b.getPixel(x,y))>128)visible++;assertTrue("Sticker must contain visible artwork",visible>100)
            layer.text+=InlineEmoji.token(layer,item.uri)
        }
        val copy=Store(c).gson.fromJson(Store(c).gson.toJson(layer),Layer::class.java)
        assertEquals(layer.inlineEmoji,copy.inlineEmoji);assertEquals(4,copy.inlineEmoji.size)
        val rendered=Artwork.text(c,copy);assertTrue(rendered.width>0 && rendered.height>0)
    }
    @Test fun pickerSearchFavoriteRecentAndInsertionWork(){
        c.getSharedPreferences("settings",0).edit().remove("favorites").remove("recent").remove("emojiFolder").commit()
        Store(c).clearTextEdit();Store(c).save(Project())
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            var selected:EmojiItem?=null
            scenario.onActivity{a->EmojiPickerDialog(a,{selected=it},{}).show()}
            onView(withContentDescription("이모지 검색")).perform(click(),replaceText("녹차"))
            onView(withContentDescription("차 한 잔 즐겨찾기 추가")).check(matches(isDisplayed()))
            onView(withText("폴더 변경")).check(matches(isDisplayed()))
            android.os.SystemClock.sleep(700)
            capture("search-keyboard")
            onView(withContentDescription("이모지 검색")).perform(closeSoftKeyboard())
            onView(withContentDescription("차 한 잔 즐겨찾기 추가")).perform(click())
            onView(withContentDescription("이모지 검색 지우기")).perform(click())
            onView(withText("즐겨찾기")).perform(click()).check(matches(isSelected()))
            onView(withContentDescription("차 한 잔 즐겨찾기 해제")).check(matches(isDisplayed()))
            capture("favorites")
            onView(withContentDescription("이모지 검색")).perform(replaceText("없는검색어"),closeSoftKeyboard())
            onView(withText("검색 결과가 없어요 · 검색어나 분류를 바꿔보세요")).check(matches(isDisplayed()))
            onView(withContentDescription("이모지 검색 지우기")).perform(click())
            onView(withContentDescription("스티커 추가: 차 한 잔")).perform(click())
            ins.waitForIdleSync();assertEquals("tea-time",selected?.id)
            scenario.onActivity{a->EmojiPickerDialog(a,{},{}).show()}
            onView(withText("최근 사용")).perform(click()).check(matches(isSelected()))
            onView(withContentDescription("스티커 추가: 차 한 잔")).check(matches(isDisplayed()))
            capture("recent")
            onView(withText("닫기")).perform(click())
        }
    }
}
