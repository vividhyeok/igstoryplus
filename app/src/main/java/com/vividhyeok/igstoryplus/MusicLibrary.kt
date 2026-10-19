package com.vividhyeok.igstoryplus

import android.content.Context
import android.net.Uri
import java.io.File

/** App-private metadata, not copies of a user's music. SAF grants continue to own access. */
class MusicLibrary(private val context:Context){
    private val file=File(context.filesDir,"music-library.json")
    private val gson=Store(context).gson
    fun all():List<Music> {
        runCatching{gson.fromJson(file.readText(),Array<Music>::class.java).toList()}.getOrNull()?.let{return it}
        // v0.2 stored per-URI metadata but had no library file. Preserve those registrations.
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE).all.entries.filter{it.key.startsWith("music:")}.mapNotNull{entry->
            runCatching{gson.fromJson(entry.value as String,Music::class.java)}.getOrNull()
        }.filter{it.uri.isNotBlank()}.distinctBy{it.uri}.sortedBy{it.title}.take(100)
    }
    fun remember(m:Music){
        val list=(listOf(m.copy())+all().filter{it.uri!=m.uri}).take(100)
        val tmp=File(context.filesDir,"music-library.tmp");tmp.writeText(gson.toJson(list))
        java.nio.file.Files.move(tmp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    fun readable(m:Music)=runCatching{context.contentResolver.openAssetFileDescriptor(Uri.parse(m.uri),"r")?.use{true}?:false}.getOrDefault(false)
}
