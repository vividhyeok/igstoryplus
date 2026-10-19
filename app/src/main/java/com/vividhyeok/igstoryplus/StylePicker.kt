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
        var refreshThumbnails:()->Unit={}
        var dialog:EditorSheet?=null
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
            override fun onStartTrackingTouch(s:SeekBar){checkpoint()};override fun onStopTrackingTouch(s:SeekBar){save();refreshThumbnails()}
            override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user){project().style.amount=p/100f;label.text="강도 $p%";update()}}
        })},LinearLayout.LayoutParams(-1,dp(48)))
        box.addView(Switch(activity).apply{text="움직임";setTextColor(Color.WHITE);minHeight=dp(48);isChecked=project().style.motion;setOnCheckedChangeListener{_,enabled->checkpoint();project().style.motion=enabled;update();save();if(enabled)motion()}})
        val actions=LinearLayout(activity)
        fun action(text:String,run:()->Unit)=TextView(activity).apply{this.text=text;setTextColor(Color.WHITE);gravity=Gravity.CENTER;minHeight=dp(48);isFocusable=true;setOnClickListener{run()}}
        actions.addView(action("세부 설정"){dialog?.dismiss();details()},LinearLayout.LayoutParams(0,dp(48),1f));actions.addView(action("프리셋"){dialog?.dismiss();presets()},LinearLayout.LayoutParams(0,dp(48),1f));box.addView(actions)
        dialog=EditorSheet(activity,"사진 스타일",box,{save()}).also{it.show()}
        val snapshot=Store(activity).copy(project());val app=activity.applicationContext
        val worker=java.util.concurrent.Executors.newSingleThreadExecutor();val generation=java.util.concurrent.atomic.AtomicInteger();var cached:android.graphics.Bitmap?=null
        refreshThumbnails={val token=generation.incrementAndGet();val style=project().style.copy();worker.execute{
            runCatching{cached?:Artwork.background(app,snapshot,144).also{cached=it}}.onSuccess{base->
                if(token==generation.get())runCatching{filteredBitmaps(base,names.map{style.copy(name=it,motion=false)}){index,bitmap->activity.runOnUiThread{if(dialog.isShowing && token==generation.get())images[names[index]]?.setImageBitmap(bitmap)}}}
            }
        }}
        dialog.setOnDismissListener{generation.incrementAndGet();worker.shutdownNow();pause();save()}
        refreshThumbnails()
    }
}
