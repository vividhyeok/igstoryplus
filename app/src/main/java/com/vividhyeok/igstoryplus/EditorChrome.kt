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
    val tray = LinearLayout(context).apply{orientation=VERTICAL;visibility=GONE}
    var dismissPanel:(()->Unit)?=null
    val play = EditorIcons.button(context,"미리보기 재생"){}
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(12,12,15))
        top.gravity = Gravity.CENTER_VERTICAL
        addView(top, LayoutParams(-1,dp(52)))
        addView(stage, LayoutParams(-1,0,1f))
        addView(tray, LayoutParams(-1,-2))
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(this@EditorChrome.bottom)
        }
        bottom.gravity = Gravity.CENTER
        addView(scroll, LayoutParams(-1,dp(60)))
        play.isFocusable = true
        stage.addView(play, FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.END).apply { rightMargin=dp(8);bottomMargin=dp(8) })
    }
    fun tool(glyph: String, label: String, action: () -> Unit): ImageButton = EditorIcons.button(context,label,action)
    fun playback(playing:Boolean){val label=if(playing)"미리보기 일시정지" else "미리보기 재생";play.contentDescription=label;play.setImageDrawable(EditorIcons.drawable(label))}
}
