package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*

/** Only screen geometry lives here; project state and rendering remain independent. */
class EditorChrome(context: Context) : LinearLayout(context) {
    val top = LinearLayout(context)
    val stage = FrameLayout(context)
    val bottom = LinearLayout(context)
    val play = TextView(context)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(12,12,15))
        top.gravity = Gravity.CENTER_VERTICAL
        addView(top, LayoutParams(-1,dp(52)))
        addView(stage, LayoutParams(-1,0,1f))
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(this@EditorChrome.bottom)
        }
        bottom.gravity = Gravity.CENTER
        addView(scroll, LayoutParams(-1,dp(60)))
        play.apply {
            text = "▶"; textSize = 18f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setBackgroundColor(0x99303034.toInt())
            contentDescription = "미리보기 재생"; isFocusable = true
        }
        stage.addView(play, FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.END).apply { rightMargin=dp(8);bottomMargin=dp(8) })
    }
    fun tool(glyph: String, label: String, action: () -> Unit): TextView = TextView(context).apply {
        text=glyph; contentDescription=label; textSize=23f; setTextColor(Color.WHITE)
        gravity=Gravity.CENTER; minimumWidth=dp(48); minimumHeight=dp(48)
        isFocusable=true; isClickable=true; setOnClickListener { action() }
    }
}
