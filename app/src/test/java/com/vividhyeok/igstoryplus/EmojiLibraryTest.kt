package com.vividhyeok.igstoryplus

import org.junit.Assert.*
import org.junit.Test

class EmojiLibraryTest {
    private val tea=EmojiItem("file:///android_asset/emoji/tea.png","차 한 잔","음식·카페","녹차 tea 휴식")
    private val book=EmojiItem("file:///android_asset/emoji/book.png","펼친 책","독서","reading 책")
    private val code=EmojiItem("content://folder/code.png","코딩 노트북","내 폴더","개발 coding")
    private val items=listOf(tea,book,code)
    @Test fun recentRemainsChronologicalEvenWithFavorites(){
        assertEquals(listOf(code,tea,book),EmojiLibrary.select(items,"최근 사용",favorites=setOf(book.uri),recent=listOf(code.uri,tea.uri,book.uri)))
    }
    @Test fun searchMatchesAllWordsAcrossMetadataIgnoringCase(){
        assertEquals(listOf(tea),EmojiLibrary.select(items,query="  TEA  녹차 "))
        assertTrue(EmojiLibrary.select(items,query="tea 코딩").isEmpty())
        assertEquals(listOf(code),EmojiLibrary.select(items,"내 폴더"))
        assertEquals(listOf(book),EmojiLibrary.select(items,"즐겨찾기",favorites=setOf(book.uri)))
        assertTrue(EmojiLibrary.select(items,category="독서",query="녹차").isEmpty())
    }
}
