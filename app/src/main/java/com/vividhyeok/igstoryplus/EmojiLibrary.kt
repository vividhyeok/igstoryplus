package com.vividhyeok.igstoryplus

import android.content.Context
import androidx.documentfile.provider.DocumentFile

data class EmojiItem(val uri:String,val name:String,val category:String="내 폴더",val keywords:String="",val id:String="",val modified:Long=0,val bytes:Long=0)
object EmojiLibrary {
    fun select(items:List<EmojiItem>,tab:String="내 이모지",category:String="전체",query:String="",favorites:Set<String> = emptySet(),recent:List<String> = emptyList()):List<EmojiItem> {
        val words=query.trim().split(Regex("\\s+")).filter{it.isNotBlank()}
        return items.filter{item->
            (when(tab){"내 폴더"->!bundled(item.uri);"최근 사용"->item.uri in recent;"즐겨찾기"->item.uri in favorites;else->true}) &&
                (category=="전체" || item.category==category) &&
                words.all{"${item.name} ${item.category} ${item.keywords}".contains(it,true)}
        }.sortedWith(if(tab=="최근 사용") compareBy{recent.indexOf(it.uri)} else
            compareByDescending<EmojiItem>{it.uri in favorites}.thenBy{recent.indexOf(it.uri).let{i->if(i<0)Int.MAX_VALUE else i}}.thenBy{it.name})
    }
    fun bundled(uri:String)=uri.startsWith("file:///android_asset/emoji/")
    fun defaults(c:Context):List<EmojiItem> = c.assets.open("emoji/index.json").bufferedReader().use{
        Store(c).gson.fromJson(it,Array<EmojiItem>::class.java).toList()
    }
    fun item(file:DocumentFile)=EmojiItem(file.uri.toString(),file.name?:"PNG",modified=file.lastModified(),bytes=file.length())
}
