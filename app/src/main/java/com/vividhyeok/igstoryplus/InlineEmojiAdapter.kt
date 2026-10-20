package com.vividhyeok.igstoryplus

import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class InlineEmojiAdapter(private val items:List<EmojiItem>,private val choose:(EmojiItem)->Unit):RecyclerView.Adapter<InlineEmojiAdapter.Holder>(){
    class Holder(val box:LinearLayout,val image:ImageView,val title:TextView):RecyclerView.ViewHolder(box)
    override fun getItemCount()=items.size
    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):Holder{
        val c=parent.context;val d=c.resources.displayMetrics.density
        val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isFocusable=false;layoutParams=RecyclerView.LayoutParams((72*d).toInt(),(76*d).toInt())}
        val image=ImageView(c).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setPadding((8*d).toInt(),(4*d).toInt(),(8*d).toInt(),0)};box.addView(image,LinearLayout.LayoutParams(-1,(52*d).toInt()))
        val title=TextView(c).apply{setTextColor(Color.WHITE);textSize=10f;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER};box.addView(title)
        return Holder(box,image,title)
    }
    override fun onBindViewHolder(h:Holder,position:Int){val item=items[position];h.title.text=item.name;h.image.contentDescription="문장 이모지: ${item.name}";Glide.with(h.image).load(item.uri).override(144,144).into(h.image);h.image.setOnClickListener{choose(item)};h.box.setOnClickListener{choose(item)}}
    override fun onViewRecycled(h:Holder){Glide.with(h.image).clear(h.image)}
}
