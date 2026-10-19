package com.vividhyeok.igstoryplus

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MusicLibraryTest{
    @Test fun legacyRegistrationsAndUserMetadataSurviveLibraryMigration(){
        val c=RuntimeEnvironment.getApplication();val old=Music(uri="content://old/song",title="기존 곡",artist="나",durationMs=30000)
        c.getSharedPreferences("settings",0).edit().putString("music:${old.uri}",Store(c).gson.toJson(old)).commit()
        val library=MusicLibrary(c);assertEquals("기존 곡",library.all().single().title)
        library.remember(old.copy(title="수정한 곡"));library.remember(Music(uri="content://new/song",title="새 곡"))
        assertEquals(listOf("새 곡","수정한 곡"),library.all().map{it.title})
        assertTrue(c.getSharedPreferences("settings",0).contains("music:${old.uri}"))
    }
}
