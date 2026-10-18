package com.vividhyeok.igstoryplus

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.UUID

data class Crop(var x: Float = .5f, var y: Float = .5f, var zoom: Float = 1f,
                var aspect: Float = 0f, var rotation: Float = 0f, var flip: Boolean = false)
data class Style(var name: String = "Simple", var amount: Float = .25f, var motion: Boolean = false,
                 var blur: Float = .55f, var brightness: Float = .75f, var warmth: Float = 0f,
                 var speed: Float = 1f, var scope: Float = .75f, var beat: Float = 0f)
data class Layer(var id: String = UUID.randomUUID().toString(), var kind: String = "photo", var uri: String = "",
                 var text: String = "", var type: String = "Soft Label", var font: String = "",
                 var color: Int = -1, var align: Int = 1, var emphasis: Float = .3f,
                 var x: Float = .5f, var y: Float = .5f, var scale: Float = 1f,
                 var rotation: Float = 0f, var width: Float = .45f, var motion: Boolean = false,
                 var crop: Crop = Crop(), var textLayoutVersion: Int = 0)
data class Music(var uri: String = "", var title: String = "", var artist: String = "", var album: String = "",
                 var art: String = "", var durationMs: Long = 0, var startMs: Long = 0, var card: String = "작은 카드", var volume: Float = 1f)
data class Project(var source: String = "", var video: Boolean = false, var sourceDurationMs: Long = 0,
                   var duration: Int = 5, var crop: Crop = Crop(), var style: Style = Style(),
                   var layers: MutableList<Layer> = mutableListOf(), var music: Music? = null) {
    fun animated() = video || music != null || style.motion || layers.any { it.motion }
    fun durationMs(): Long = if (video) sourceDurationMs.coerceIn(1, 60000) else duration.coerceIn(5, 15) * 1000L
}
data class Preset(var name: String, var style: Style, var text: String, var card: String,
                  var template: Layer?=null, var musicMotion: Boolean=false, var duration: Int=5)
class Store(val context: Context) {
    val gson = Gson()
    private val draft = File(context.filesDir, "draft.json")
    fun copy(p: Project): Project = gson.fromJson(gson.toJson(p), Project::class.java)
    fun save(p: Project) {
        val temp = File(context.filesDir, "draft.tmp")
        temp.writeText(gson.toJson(p)); check(temp.renameTo(draft)) { "초안 저장 실패" }
    }
    fun load(): Project = runCatching { gson.fromJson(draft.readText(), Project::class.java) }.getOrNull() ?: Project()
    private val pending=File(context.filesDir,"text-edit.json")
    fun saveTextEdit(id:String?,layer:Layer){val temp=File(context.filesDir,"text-edit.tmp");temp.writeText(gson.toJson(TextEditDraft(id,layer)));check(temp.renameTo(pending))}
    fun textEdit():TextEditDraft?=runCatching{gson.fromJson(pending.readText(),TextEditDraft::class.java)}.getOrNull()
    fun clearTextEdit(){pending.delete()}
    private val presets = File(context.filesDir, "presets.json")
    fun presets(): MutableList<Preset> = runCatching { gson.fromJson(presets.readText(), Array<Preset>::class.java).toMutableList() }.getOrDefault(mutableListOf())
    fun savePresets(list: List<Preset>) = presets.writeText(gson.toJson(list))
}
data class TextEditDraft(val existingId:String?,val layer:Layer)
class History {
    private val back = ArrayDeque<String>()
    private val forward = ArrayDeque<String>()
    private val gson = Gson()
    fun push(p: Project) { back.addLast(gson.toJson(p)); while (back.size > 40) back.removeFirst(); forward.clear() }
    fun undo(p: Project): Project? { if (back.isEmpty()) return null; forward.addLast(gson.toJson(p)); return gson.fromJson(back.removeLast(), Project::class.java) }
    fun redo(p: Project): Project? { if (forward.isEmpty()) return null; back.addLast(gson.toJson(p)); return gson.fromJson(forward.removeLast(), Project::class.java) }
}
