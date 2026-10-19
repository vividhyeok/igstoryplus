package com.vividhyeok.igstoryplus

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*

/** Export is the only blocking normal action; keep its status compact and cancellable. */
class ExportProgressDialog(c:Context,cancel:()->Unit):Dialog(c){
    private fun dp(n:Int)=(n*c.resources.displayMetrics.density).toInt()
    private val label=TextView(c).apply{text="준비 중 · 초안은 보존됩니다";setTextColor(Color.LTGRAY);textSize=13f}
    private val bar=ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;isIndeterminate=true;progressTintList=ColorStateList.valueOf(Color.WHITE);indeterminateTintList=ColorStateList.valueOf(Color.WHITE);contentDescription="출력 진행 상태"}
    init{
        val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(24),dp(24),dp(12));background=GradientDrawable().apply{setColor(0xFF222226.toInt());cornerRadius=dp(20).toFloat()}}
        box.addView(TextView(c).apply{text="스토리 만드는 중";setTextColor(Color.WHITE);textSize=18f;minHeight=dp(40)})
        box.addView(label);box.addView(bar,LinearLayout.LayoutParams(-1,dp(48)))
        box.addView(TextView(c).apply{text="취소";contentDescription="출력 취소";textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;minHeight=dp(48);isFocusable=true;setOnClickListener{cancel();dismiss()}})
        setContentView(box);setCancelable(false);window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
    fun update(value:Int){if(value>0){bar.isIndeterminate=false;bar.progress=value;label.text="$value% · 초안은 보존됩니다"}}
    override fun show(){super.show();window?.setLayout(context.resources.displayMetrics.widthPixels-dp(48),-2)}
}
