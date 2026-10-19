package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.util.UUID

data class NamedDraft(val id:String,val name:String,val updated:Long,val project:Project)
/** Each draft has an independent immutable snapshot; the automatic working draft remains separate. */
class DraftLibrary(private val context:Context){
    private val root=File(context.filesDir,"drafts").apply{mkdirs()}
    private val store=Store(context)
    fun all():List<NamedDraft> = root.listFiles()?.filter{it.extension=="json"}?.mapNotNull{runCatching{store.gson.fromJson(it.readText(),NamedDraft::class.java)}.getOrNull()}?.sortedByDescending{it.updated}?:emptyList()
    fun thumbnail(id:String)=File(root,"${valid(id)}.jpg")
    private fun valid(id:String)=UUID.fromString(id).toString()
    fun save(name:String,project:Project,id:String=UUID.randomUUID().toString()):NamedDraft{
        val key=valid(id);val draft=NamedDraft(key,name,System.currentTimeMillis(),store.copy(project))
        val temp=File(root,"$key.tmp");temp.writeText(store.gson.toJson(draft));check(temp.renameTo(File(root,"$key.json")))
        runCatching{
            val bitmap=filteredBitmap(Artwork.background(context,draft.project,216),draft.project.style);val canvas=Canvas(bitmap)
            draft.project.layers.forEach{layer->runCatching{Exporter.drawLayer(canvas,layer,Artwork.layer(context,layer,draft.project.music),bitmap.width,bitmap.height)}}
            thumbnail(key).outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,82,it)}
        }
        return draft
    }
    fun rename(draft:NamedDraft,name:String){val temp=File(root,"${valid(draft.id)}.tmp");temp.writeText(store.gson.toJson(draft.copy(name=name)));check(temp.renameTo(File(root,"${valid(draft.id)}.json")))}
    fun delete(draft:NamedDraft){File(root,"${valid(draft.id)}.json").delete();thumbnail(draft.id).delete()}
}
