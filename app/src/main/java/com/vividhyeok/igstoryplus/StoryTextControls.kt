package com.vividhyeok.igstoryplus

import android.app.Activity
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

/** One font strip, compact inline controls and native Unicode insertion at the current selection. */
class StoryTextControls(private val activity:Activity,private val chrome:EditorChrome,private val session:CanvasTextEditor,private val importFont:()->Unit){
    private val layer=session.draft
    private var panel=""
    private val colors=intArrayOf(Color.WHITE,Color.BLACK,0xFFFF4444.toInt(),0xFFFFA64D.toInt(),0xFFFFD955.toInt(),0xFFADDC87.toInt(),0xFF69CEEB.toInt(),0xFFC5A5FF.toInt(),0xFFFFB9D2.toInt())
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
    private fun row()=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
    private fun item(label:String,description:String=label,action:()->Unit)=TextView(activity).apply{
        text=label;contentDescription=description;textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(dp(10),0,dp(10),0);minHeight=dp(48);minWidth=dp(48);isClickable=true;isFocusable=false;setOnClickListener{action()}
    }
    fun show(){
        chrome.top.removeAllViews();chrome.bottom.removeAllViews();closeTray()
        chrome.top.addView(icon("닫기","텍스트 취소"){session.finish(false)},LinearLayout.LayoutParams(dp(48),-1))
        chrome.top.addView(icon("정렬","텍스트 정렬"){layer.align=(layer.align+1)%3;session.refresh()},LinearLayout.LayoutParams(0,-1,1f))
        chrome.top.addView(icon("텍스트 배경","텍스트 배경"){val modes=listOf("없음","채움","반투명","라인");layer.textBackground=modes[(modes.indexOf(layer.textBackground)+1)%modes.size];session.refresh()},LinearLayout.LayoutParams(0,-1,1f))
        chrome.top.addView(icon("색상","텍스트 색상"){palette()},LinearLayout.LayoutParams(0,-1,1f))
        chrome.top.addView(icon("더보기","텍스트 추가 설정"){advanced()},LinearLayout.LayoutParams(dp(48),-1))
        chrome.top.addView(item("완료","텍스트 완료"){session.finish(true)},LinearLayout.LayoutParams(dp(64),-1))
        chrome.bottom.addView(item("내 이모지","텍스트 개인 이모지"){customEmoji()}.apply{textSize=13f})
        val chips=mutableListOf<TextView>()
        EditorCatalog.text.forEach{name->
            val chip=item(if(name in EditorCatalog.storyText)when(name){"클래식"->"Aa";"모던"->"Modern";"굵게"->"Aa";"타자기"->"Aa";"네온"->"Neon";else->"Aa"} else name,"텍스트 스타일: $name"){
                layer.type=name;layer.font="";layer.motion=name=="Drift Type";session.refresh();chips.forEach{it.alpha=if(it.contentDescription=="텍스트 스타일: $name")1f else .55f}
            }.apply{typeface=Artwork.typeface(layer.copy(type=name,font=""));alpha=if(name==layer.type)1f else .55f;setPadding(dp(12),0,dp(12),0)}
            chips+=chip;chrome.bottom.addView(chip)
        }
        val size=object:View(activity){
            private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c:Canvas){val x=width/2f;paint.color=0x66FFFFFF;paint.strokeWidth=dp(2).toFloat();c.drawLine(x,dp(10).toFloat(),x,height-dp(10).toFloat(),paint);paint.color=Color.WHITE;c.drawCircle(x,dp(10)+(height-dp(20))*(1-(layer.textSizeScale-.25f)/1.55f).coerceIn(0f,1f),dp(5).toFloat(),paint)}
            override fun onTouchEvent(e:MotionEvent):Boolean{if(e.actionMasked==MotionEvent.ACTION_DOWN)parent.requestDisallowInterceptTouchEvent(true);layer.textSizeScale=(.25f+1.55f*(1-e.y/height)).coerceIn(.25f,1.8f);session.refresh();invalidate();if(e.actionMasked==MotionEvent.ACTION_UP)parent.requestDisallowInterceptTouchEvent(false);return true}
        }.apply{contentDescription="텍스트 크기"}
        chrome.stage.addView(size,FrameLayout.LayoutParams(dp(28),dp(160),Gravity.CENTER_VERTICAL or Gravity.START).apply{leftMargin=dp(2)})
        session.auxiliary(size)
    }
    private fun closeTray(){panel="";chrome.tray.removeAllViews();chrome.tray.visibility=View.GONE}
    private fun icon(name:String,description:String,action:()->Unit)=EditorIcons.button(activity,name,action).apply{contentDescription=description;isFocusable=false}
    private fun tray(name:String,body:View){
        if(panel==name){closeTray();session.keyboard(true);return}
        session.keyboard(false);panel=name;chrome.tray.removeAllViews();chrome.tray.visibility=View.VISIBLE;chrome.tray.addView(body)
    }
    private fun palette(){
        val strip=row();colors.forEach{color->strip.addView(item("●","텍스트 색상 $color"){layer.color=color;session.refresh()}.apply{setTextColor(color);textSize=30f})}
        tray("색상",HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;addView(strip)})
    }
    private fun customEmoji(){
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF202024.toInt())}
        val header=row();header.addView(item("⌨","키보드로 돌아가기"){closeTray();session.keyboard(true)})
        header.addView(TextView(activity).apply{text="내 이모지 · 문장 안에 삽입";setTextColor(Color.LTGRAY);textSize=12f});body.addView(header)
        val grid=androidx.recyclerview.widget.RecyclerView(activity).apply{contentDescription="내 이모지 목록";layoutManager=androidx.recyclerview.widget.GridLayoutManager(activity,2,androidx.recyclerview.widget.RecyclerView.HORIZONTAL,false);isNestedScrollingEnabled=false}
        body.addView(grid,LinearLayout.LayoutParams(-1,dp(152)))
        val preferences=activity.getSharedPreferences("settings",0)
        var all=EmojiLibrary.defaults(activity)
        var category="전체"
        val categories=row()
        body.addView(HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;addView(categories)},1)
        fun populate(){
            categories.removeAllViews()
            (listOf("전체","최근 사용","즐겨찾기")+all.map{it.category}.distinct()).forEach{value->
                categories.addView(item(value){category=value;populate()}.apply{isSelected=category==value;setTextColor(if(isSelected)0xFFD4FF83.toInt() else Color.LTGRAY);textSize=13f})
            }
            val recent=preferences.getString("recent","").orEmpty().split("|")
            val favorites=preferences.getStringSet("favorites",emptySet())?:emptySet()
            val tab=if(category in listOf("최근 사용","즐겨찾기"))category else "내 이모지"
            val shown=EmojiLibrary.select(all,tab,if(tab=="내 이모지")category else "전체",favorites=favorites,recent=recent)
            grid.adapter=InlineEmojiAdapter(shown){item->
                session.insertCustomEmoji(item.uri)
                val latest=preferences.getString("recent","").orEmpty().split("|")
                preferences.edit().putString("recent",(listOf(item.uri)+latest.filter{it.isNotBlank() && it!=item.uri}).take(30).joinToString("|")).apply()
            }
            header.getChildAt(1).let{(it as TextView).text=if(shown.isEmpty())"아직 없어요 · 전체에서 골라보세요" else "${shown.size}개 · 문장 안에 삽입"}
        }
        populate();tray("개인 이모지",body)
        val path=preferences.getString("emojiFolder",null)
        if(path!=null)Thread{val user=runCatching{EmojiFolder.scan(activity.applicationContext,android.net.Uri.parse(path)).map{EmojiLibrary.item(it)}}.getOrDefault(emptyList());activity.runOnUiThread{if(panel=="개인 이모지" && body.isAttachedToWindow){all=all+user;populate()}}}.start()
    }

    private fun advanced(){
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        val fonts=row();listOf("기본" to "","세리프" to "system:serif","모노" to "system:monospace").forEach{(label,font)->fonts.addView(item(label){layer.font=font;session.refresh()})};fonts.addView(item("개인 폰트"){importFont()});body.addView(fonts)
        val label=TextView(activity).apply{text="효과 강도";setTextColor(Color.LTGRAY);setPadding(dp(16),0,0,0)};body.addView(label)
        body.addView(SeekBar(activity).apply{max=100;progress=(layer.emphasis*100).toInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){if(user){layer.emphasis=v/100f;session.refresh()}}})},LinearLayout.LayoutParams(-1,dp(48)))
        body.addView(TextView(activity).apply{text="줄 너비 · 글자 크기는 유지";setTextColor(Color.WHITE);setPadding(dp(16),dp(8),0,0)})
        body.addView(SeekBar(activity).apply{contentDescription="텍스트 줄 너비";max=100;progress=((layer.width-.25f)/.7f*100).toInt().coerceIn(0,100);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){if(user){layer.width=.25f+.7f*v/100f;session.refresh()}}})},LinearLayout.LayoutParams(-1,dp(48)))
        tray("설정",body)
    }
}
