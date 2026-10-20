package com.vividhyeok.igstoryplus

object EditorCatalog {
    val photos=listOf("Simple","Grainy","Gritty","Clean","Warm Film","Soft Fade","Mono","Cool Night","Lores","Handheld","Zoomblur","Flow Blur","Shutter Drag","Sensor Raw","Frame Echo")
    fun photoLabel(name:String)=when(name){"Simple"->"원본";"Clean"->"클린";"Warm Film"->"웜 필름";"Soft Fade"->"페이드";"Mono"->"모노";"Cool Night"->"쿨 나이트";else->name}
    fun photoCode(name:String)=listOf("Simple","Grainy","Gritty","Lores","Handheld","Zoomblur","Flow Blur","Shutter Drag","Sensor Raw","Frame Echo","Clean","Warm Film","Soft Fade","Mono","Cool Night").indexOf(name).coerceAtLeast(0)
    val storyText=listOf("클래식","모던","굵게","타자기","네온","외곽선")
    val text=storyText+listOf("기본","Soft Label","Offset Type","Whisper","Drift Type","Echo Type","Film Caption","Mono Stamp","Editorial Serif","Margin Note")
    val cards=listOf("스토리","커버","투명","기본 카드","작은 카드","앨범 아트","텍스트","Mini Credit","Cover Caption","Mono Tag","Progress Line","숨김")
}
