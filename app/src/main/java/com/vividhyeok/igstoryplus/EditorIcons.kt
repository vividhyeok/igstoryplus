package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageButton
import androidx.core.graphics.PathParser

/** Original 24-unit vector paths. No font glyphs, raster assets or brand artwork. */
object EditorIcons {
    private val paths=mapOf(
        "닫기" to "M6,6 L18,18 M18,6 L6,18",
        "선택 해제" to "M6,6 L18,18 M18,6 L6,18",
        "실행 취소" to "M8,5 L3,10 L8,15 M3,10 L14,10 C18,10 21,12 21,16 C21,19 18,21 14,21",
        "다시 실행" to "M16,5 L21,10 L16,15 M21,10 L10,10 C6,10 3,12 3,16 C3,19 6,21 10,21",
        "텍스트" to "M3,19 L8,5 L13,19 M5,14 L11,14 M21,19 L21,11 M21,15 C21,9 15,9 15,15 C15,21 21,20 21,15",
        "내 이모지" to "M5,3 L19,3 Q21,3 21,5 L21,15 L15,21 L5,21 Q3,21 3,19 L3,5 Q3,3 5,3 M15,21 L15,15 L21,15 M8,9 L8,10 M16,9 L16,10 M8,13 Q12,17 16,13",
        "음악" to "M9,17 L9,5 L20,3 L20,15 M9,8 L20,6 M9,17 C9,21 3,21 3,18 C3,15 9,14 9,17 M20,15 C20,19 14,19 14,16 C14,13 20,12 20,15",
        "더보기" to "M5,11 L5,13 M12,11 L12,13 M19,11 L19,13",
        "레이어 추가 작업" to "M5,11 L5,13 M12,11 L12,13 M19,11 L19,13",
        "사진 추가" to "M14,3 L4,3 Q2,3 2,5 L2,19 Q2,21 4,21 L20,21 Q22,21 22,19 L22,12 M3,17 L8,12 L13,17 L16,14 L22,20 M16,6 L22,6 M19,3 L19,9 M7,7 L8,7",
        "스타일" to "M4,4 L20,4 Q21,4 21,5 L21,19 Q21,20 20,20 L4,20 Q3,20 3,19 L3,5 Q3,4 4,4 M8,4 L8,20 M16,4 L16,20 M3,9 L8,9 M3,15 L8,15 M16,9 L21,9 M16,15 L21,15",
        "플러스" to "M10,3 L12.1,9.9 L19,12 L12.1,14.1 L10,21 L7.9,14.1 L1,12 L7.9,9.9 Z M20,3 L20,7 M18,5 L22,5 M20,17 L20,21 M18,19 L22,19",
        "배치" to "M3,5 L21,5 M3,19 L21,19 M7,9 L17,9 L17,15 L7,15 Z M12,2 L12,5 M12,19 L12,22",
        "가로 중앙" to "M12,2 L12,22 M4,6 L20,6 L20,10 L4,10 Z M7,14 L17,14 L17,18 L7,18 Z",
        "세로 중앙" to "M2,12 L22,12 M6,4 L10,4 L10,20 L6,20 Z M14,7 L18,7 L18,17 L14,17 Z",
        "수평" to "M3,12 L21,12 M7,8 L12,3 L17,8 M7,16 L12,21 L17,16",
        "잠금" to "M7,10 L7,7 C7,1 17,1 17,7 L17,10 M5,10 L19,10 Q20,10 20,11 L20,20 Q20,21 19,21 L5,21 Q4,21 4,20 L4,11 Q4,10 5,10 M12,14 L12,17",
        "텍스트 배경" to "M5,3 L19,3 Q21,3 21,5 L21,19 Q21,21 19,21 L5,21 Q3,21 3,19 L3,5 Q3,3 5,3 M8,17 L12,7 L16,17 M9.5,13 L14.5,13",
        "하트" to "M12,20 C9,18 3,13 3,8 C3,2 10,2 12,7 C14,2 21,2 21,8 C21,13 15,18 12,20 Z",
        "저장" to "M12,2 L12,16 M7,11 L12,16 L17,11 M4,16 L4,21 L20,21 L20,16",
        "미리보기 재생" to "M9,5 L19,12 L9,19 Z",
        "미리보기 일시정지" to "M8,5 L8,19 M16,5 L16,19",
        "처음부터" to "M4,5 L4,19 M19,5 L8,12 L19,19 Z",
        "자르기" to "M7,2 L7,17 L22,17 M2,7 L17,7 L17,22",
        "90도 회전" to "M4,9 A8,8 0,1 1,5,18 M4,3 L4,9 L10,9",
        "좌우 반전" to "M12,3 L12,21 M9,6 L3,18 L9,18 Z M15,6 L21,18 L15,18 Z",
        "교체" to "M3,7 L20,7 L16,3 M21,17 L4,17 L8,21",
        "복제" to "M8,8 L21,8 L21,21 L8,21 Z M4,16 L3,16 L3,3 L16,3 L16,4",
        "삭제" to "M3,6 L21,6 M9,6 L9,3 L15,3 L15,6 M6,6 L7,21 L17,21 L18,6 M10,10 L10,17 M14,10 L14,17",
        "수정" to "M4,16 L16,4 L20,8 L8,20 L3,21 Z M13,7 L17,11",
        "정렬" to "M3,5 L21,5 M6,10 L18,10 M3,15 L21,15 M6,20 L18,20",
        "색상" to "M12,3 C3,3 0,15 7,20 C11,23 11,18 14,18 C21,18 24,13 20,7 C18,4 15,3 12,3 M7,10 L7,11 M12,7 L12,8 M17,10 L17,11",
        "글꼴" to "M3,5 L15,5 M9,5 L9,21 M14,11 L22,11 M18,11 L18,21",
        "음악 구간" to "M3,9 L3,15 M7,5 L7,19 M12,8 L12,16 M17,3 L17,21 M21,7 L21,17",
        "카드" to "M3,5 L21,5 L21,19 L3,19 Z M6,8 L12,8 L12,16 L6,16 Z M15,9 L18,9 M15,14 L18,14",
        "볼륨" to "M3,9 L7,9 L11,5 L11,19 L7,15 L3,15 Z M15,8 C18,10 18,14 15,16 M18,5 C23,9 23,15 18,19"
    )
    fun drawable(name:String,color:Int=Color.WHITE):Drawable = object:Drawable(){
        val path=PathParser.createPathFromPathData(paths[if(name=="스티커")"내 이모지" else name]?:paths["더보기"]!!)!!
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        override fun draw(c:Canvas){val side=kotlin.math.min(bounds.width(),bounds.height()).toFloat();c.save();c.translate(bounds.left+(bounds.width()-side)/2,bounds.top+(bounds.height()-side)/2);c.scale(side/24f,side/24f);c.drawPath(path,paint);c.restore()}
        override fun setAlpha(alpha:Int){paint.alpha=alpha}
        override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter}
        @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
    fun button(c:Context,name:String,action:()->Unit)=ImageButton(c).apply{
        val d=c.resources.displayMetrics.density
        contentDescription=name;minimumWidth=(48*d).toInt();minimumHeight=(48*d).toInt()
        setPadding((12*d).toInt(),(12*d).toInt(),(12*d).toInt(),(12*d).toInt())
        setImageDrawable(drawable(name));background=GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=24*d}
        val value=android.util.TypedValue();c.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,value,true);setBackgroundResource(value.resourceId)
        setOnClickListener{action()}
    }
}
