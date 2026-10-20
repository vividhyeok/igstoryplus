package com.vividhyeok.igstoryplus

/** Version 2 keeps physical font size independent from the text box's wrap width. */
object TextGeometry {
    fun migrate(l:Layer){if(l.textLayoutVersion<2){l.textSizeScale*=l.width/.8f*(if(l.textLayoutVersion==0)58f/88 else 1f);l.textLayoutVersion=2}}
    fun factor(l:Layer)=if(l.textLayoutVersion>=2)(l.textSizeScale*.8f/l.width.coerceAtLeast(.15f)).coerceIn(.12f,8f)else 1f
    fun horizontalPadding(l:Layer)=(40*factor(l)).toInt().coerceIn(8,160)
    fun verticalPadding(l:Layer)=(32*factor(l)).toInt().coerceIn(6,128)
}
