package com.vividhyeok.igstoryplus

import android.content.Context
import androidx.documentfile.provider.DocumentFile

data class EmojiItem(val uri:String,val name:String,val category:String="내 폴더",val keywords:String="",val id:String="",val modified:Long=0,val bytes:Long=0)
object EmojiLibrary {
    fun bundled(uri:String)=uri.startsWith("file:///android_asset/emoji/")
    fun defaults(c:Context):List<EmojiItem> = c.assets.open("emoji/index.json").bufferedReader().use{
        Store(c).gson.fromJson(it,Array<EmojiItem>::class.java).toList()
    }
    fun item(file:DocumentFile)=EmojiItem(file.uri.toString(),file.name?:"PNG",modified=file.lastModified(),bytes=file.length())
}
