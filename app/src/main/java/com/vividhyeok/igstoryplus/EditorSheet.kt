package com.vividhyeok.igstoryplus

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

/** Small native bottom sheet. Only its handle/header intercepts swipe-to-dismiss. */
class EditorSheet(context:Context,title:String,private val body:View,onDone:(()->Unit)?,private val fraction:Float=.68f):Dialog(context) {
    private fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
    init {
        val box=LinearLayout(context).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(8))
            background=GradientDrawable().apply{setColor(Color.rgb(27,27,31));cornerRadius=dp(20).toFloat()}
        }
        val heading=TextView(context).apply{text=title;textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;minHeight=dp(48);contentDescription="$title, 아래로 밀어 닫기"}
        var down=0f
        heading.setOnTouchListener{v,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{down=e.rawY;true};MotionEvent.ACTION_UP->{if(e.rawY-down>dp(32))dismiss() else v.performClick();true};else->true}}
        box.addView(heading)
        box.addView(ScrollView(context).apply{isFillViewport=false;addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        box.addView(TextView(context).apply{text="완료";contentDescription="시트 완료";textSize=16f;gravity=Gravity.CENTER;setTextColor(0xFFD4FF83.toInt());minHeight=dp(48);isFocusable=true;setOnClickListener{onDone?.invoke();dismiss()}})
        setContentView(box,ViewGroup.LayoutParams(-1,-1))
        window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setDimAmount(.12f);setGravity(Gravity.BOTTOM)}
    }
    override fun show(){
        super.show()
        val metrics=context.resources.displayMetrics
        body.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels-dp(24),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val height=(body.measuredHeight+dp(112)).coerceIn(dp(180),(metrics.heightPixels*fraction).toInt())
        window?.apply{setLayout(-1,height);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
    }
}
