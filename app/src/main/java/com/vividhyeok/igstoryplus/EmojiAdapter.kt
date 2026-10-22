package com.vividhyeok.igstoryplus

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class EmojiAdapter(private val files:List<EmojiItem>,private val favorites:Set<String>,private val click:(EmojiItem)->Unit,private val favorite:(EmojiItem)->Unit):RecyclerView.Adapter<EmojiAdapter.Holder>(){
    class Holder(val box:LinearLayout,val image:ImageView,val title:TextView,val star:TextView):RecyclerView.ViewHolder(box)
    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):Holder {
        val c=parent.context;val density=c.resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(3),dp(3),dp(3),dp(3));layoutParams=RecyclerView.LayoutParams(-1,dp(144)).apply{setMargins(dp(2),dp(2),dp(2),dp(2))};isFocusable=true;background=GradientDrawable().apply{setColor(0xFF27272D.toInt());cornerRadius=dp(12).toFloat()}}
        val image=ImageView(c).apply{scaleType=ImageView.ScaleType.FIT_CENTER;importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO};box.addView(image,LinearLayout.LayoutParams(-1,dp(64)))
        val title=TextView(c).apply{setTextColor(Color.WHITE);textSize=12f;gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};box.addView(title,LinearLayout.LayoutParams(-1,dp(24)))
        val star=TextView(c).apply{textSize=20f;gravity=Gravity.CENTER;isFocusable=true;minWidth=dp(48);minHeight=dp(48)};box.addView(star,LinearLayout.LayoutParams(-1,dp(48)))
        return Holder(box,image,title,star)
    }
    override fun getItemCount()=files.size
    override fun onBindViewHolder(h:Holder,position:Int){val f=files[position];val saved=f.uri in favorites
        h.title.text=f.name;h.box.contentDescription="스티커 추가: ${f.name}";h.star.text=if(saved)"★" else "☆";h.star.setTextColor(if(saved)0xFFD4FF83.toInt() else Color.LTGRAY);h.star.isSelected=saved
        h.star.contentDescription="${f.name} 즐겨찾기 ${if(saved)"해제" else "추가"}";h.star.setOnClickListener{favorite(f)}
        Glide.with(h.image).load(f.uri).signature(com.bumptech.glide.signature.ObjectKey("${f.modified}:${f.bytes}")).override(160,160).into(h.image);h.box.setOnClickListener{click(f)};h.box.setOnLongClickListener{favorite(f);true}
    }
    override fun onViewRecycled(h:Holder){Glide.with(h.image).clear(h.image)}
}
