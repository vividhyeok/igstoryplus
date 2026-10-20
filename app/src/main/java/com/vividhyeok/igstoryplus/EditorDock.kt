package com.vividhyeok.igstoryplus

import android.app.Activity
import android.graphics.Color
import android.view.*
import android.widget.*

/** Controls occupy layout space below the entire story, never a window over the artwork. */
class EditorDock(private val activity:Activity,private val title:String,private val body:View,
                 private val done:(()->Unit)?=null,private val canFinish:()->Boolean={true}){
    private lateinit var chrome:EditorChrome
    private var listener:(()->Unit)?=null
    private var bottomVisibility=View.VISIBLE
    var isShowing=false;private set
    var maxBodyHeightDp=224
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
    fun show(){
        fun find(v:View):EditorChrome?{if(v is EditorChrome)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
        chrome=find(activity.window.decorView)?:error("Editor canvas unavailable")
        chrome.dismissPanel?.invoke();bottomVisibility=chrome.bottom.visibility;chrome.bottom.visibility=View.GONE;chrome.tray.removeAllViews();chrome.tray.visibility=View.VISIBLE;isShowing=true
        val header=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(0xFF202024.toInt())}
        header.addView(TextView(activity).apply{text=title;setTextColor(Color.WHITE);textSize=13f;setPadding(dp(16),0,0,0)},LinearLayout.LayoutParams(0,dp(44),1f))
        header.addView(TextView(activity).apply{text="완료";contentDescription="시트 완료";setTextColor(Color.WHITE);gravity=Gravity.CENTER;setOnClickListener{if(canFinish()){done?.invoke();dismiss()}}},LinearLayout.LayoutParams(dp(68),dp(44)))
        chrome.tray.addView(header)
        body.measure(View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val height=body.measuredHeight.coerceAtMost(dp(maxBodyHeightDp))
        chrome.tray.addView(ScrollView(activity).apply{setBackgroundColor(0xFF19191D.toInt());addView(body)},LinearLayout.LayoutParams(-1,height))
        chrome.dismissPanel={dismiss()}
    }
    fun setOnDismissListener(callback:()->Unit){listener=callback}
    fun dismiss(){if(!isShowing)return;isShowing=false;chrome.dismissPanel=null;chrome.tray.removeAllViews();chrome.tray.visibility=View.GONE;chrome.bottom.visibility=bottomVisibility;listener?.invoke()}
}
