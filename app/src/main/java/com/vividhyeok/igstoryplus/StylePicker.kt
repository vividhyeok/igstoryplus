package com.vividhyeok.igstoryplus

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

class StylePicker(private val activity:Activity,private val project:()->Project,
                  private val checkpoint:()->Unit,private val update:()->Unit,private val save:()->Unit,
                  private val motion:()->Unit,private val pause:()->Unit,private val details:()->Unit,private val presets:()->Unit){
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
    fun show(){
        val box=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(8),0)}
        val row=LinearLayout(activity);val images=linkedMapOf<String,ImageView>()
        val names=listOf("Simple","Grainy","Gritty","Lores","Handheld","Zoomblur","Flow Blur")
        fun border(name:String)=GradientDrawable().apply{setColor(0xFF343438.toInt());cornerRadius=dp(10).toFloat();setStroke(dp(2),if(project().style.name==name)Color.WHITE else Color.TRANSPARENT)}
        names.forEach{name->
            val tile=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(4),dp(4),dp(4));background=border(name)}
            val image=ImageView(activity).apply{scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="사진 스타일: $name"}
            images[name]=image;tile.addView(image,LinearLayout.LayoutParams(dp(82),dp(126)))
            tile.addView(TextView(activity).apply{text=name;textSize=11f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;minHeight=dp(32)})
            image.setOnClickListener{checkpoint();project().style.name=name;names.forEachIndexed{i,n->row.getChildAt(i).background=border(n)};update();save();if(project().style.motion)motion()}
            row.addView(tile,LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(6)})
        }
        box.addView(HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;addView(row)})
        val label=TextView(activity).apply{text="강도";setTextColor(Color.LTGRAY);minHeight=dp(32)};box.addView(label)
        box.addView(SeekBar(activity).apply{contentDescription="사진 스타일 강도";max=100;progress=(project().style.amount*100).toInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){checkpoint()};override fun onStopTrackingTouch(s:SeekBar){save()}
            override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user){project().style.amount=p/100f;label.text="강도 $p%";update()}}
        })},LinearLayout.LayoutParams(-1,dp(48)))
        box.addView(Switch(activity).apply{text="움직임";setTextColor(Color.WHITE);minHeight=dp(48);isChecked=project().style.motion;setOnCheckedChangeListener{_,enabled->checkpoint();project().style.motion=enabled;update();save();if(enabled)motion()}})
        val actions=LinearLayout(activity)
        fun action(text:String,run:()->Unit)=TextView(activity).apply{this.text=text;setTextColor(Color.WHITE);gravity=Gravity.CENTER;minHeight=dp(48);isFocusable=true;setOnClickListener{run()}}
        actions.addView(action("세부 설정",details),LinearLayout.LayoutParams(0,dp(48),1f));actions.addView(action("프리셋",presets),LinearLayout.LayoutParams(0,dp(48),1f));box.addView(actions)
        val dialog=EditorSheet(activity,"사진 스타일",box,{save()}).also{it.show()}
        dialog.setOnDismissListener{pause();save()}
        val snapshot=Store(activity).copy(project());val app=activity.applicationContext
        Thread{runCatching{Artwork.background(app,snapshot,144)}.onSuccess{base->names.forEach{name->
            if(!dialog.isShowing)return@Thread
            runCatching{filteredBitmap(base,snapshot.style.copy(name=name,motion=false,amount=.65f))}.onSuccess{bitmap->activity.runOnUiThread{if(dialog.isShowing)images[name]?.setImageBitmap(bitmap)}}
        }}}.start()
    }
}
