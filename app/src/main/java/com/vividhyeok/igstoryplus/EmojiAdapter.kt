package com.vividhyeok.igstoryplus

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class EmojiAdapter(private val files:List<DocumentFile>,private val favorites:Set<String>,private val click:(DocumentFile)->Unit,private val favorite:(DocumentFile)->Unit):RecyclerView.Adapter<EmojiAdapter.Holder>(){
    class Holder(val box:LinearLayout,val image:ImageView,val title:TextView):RecyclerView.ViewHolder(box)
    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):Holder {
        val c=parent.context;val density=c.resources.displayMetrics.density
        val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;layoutParams=RecyclerView.LayoutParams(-1,(96*density).toInt())}
        val image=ImageView(c).apply{scaleType=ImageView.ScaleType.FIT_CENTER};box.addView(image,LinearLayout.LayoutParams(-1,(64*density).toInt()))
        val title=TextView(c).apply{setTextColor(Color.WHITE);textSize=10f;gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};box.addView(title)
        return Holder(box,image,title)
    }
    override fun getItemCount()=files.size
    override fun onBindViewHolder(holder:Holder,position:Int){val f=files[position];holder.title.text=(if(f.uri.toString() in favorites)"★ " else "")+(f.name?:"PNG");holder.image.contentDescription=f.name
        Glide.with(holder.image).load(f.uri).signature(com.bumptech.glide.signature.ObjectKey("${f.lastModified()}:${f.length()}")).override(160,160).into(holder.image);holder.box.setOnClickListener{click(f)};holder.box.setOnLongClickListener{favorite(f);true}
    }
    override fun onViewRecycled(holder:Holder){Glide.with(holder.image).clear(holder.image)}
}
