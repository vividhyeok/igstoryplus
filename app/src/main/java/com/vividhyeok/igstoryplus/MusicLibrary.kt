package com.vividhyeok.igstoryplus

import android.content.Context
import android.net.Uri
import java.io.File

/** App-private metadata, not copies of a user's music. SAF grants continue to own access. */
class MusicLibrary(private val context:Context){
    private val file=File(context.filesDir,"music-library.json")
    private val gson=Store(context).gson
    fun all():List<Music> = runCatching{gson.fromJson(file.readText(),Array<Music>::class.java).toList()}.getOrDefault(emptyList())
    fun remember(m:Music){
        val list=(listOf(m.copy())+all().filter{it.uri!=m.uri}).take(100)
        val tmp=File(context.filesDir,"music-library.tmp");tmp.writeText(gson.toJson(list));check(tmp.renameTo(file))
    }
    fun readable(m:Music)=runCatching{context.contentResolver.openAssetFileDescriptor(Uri.parse(m.uri),"r")?.use{true}?:false}.getOrDefault(false)
}
