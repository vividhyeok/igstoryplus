package com.vividhyeok.igstoryplus

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Fixed controls and an independently scrolling grid, including while searching with the IME. */
class EmojiPickerDialog(c:Context,private val choose:(EmojiItem)->Unit,private val connect:()->Unit):Dialog(c){
    private val prefs=c.getSharedPreferences("settings",0)
    private val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(12));background=GradientDrawable().apply{setColor(0xFF1B1B1F.toInt());cornerRadius=dp(20).toFloat()}}
    private val tabs=LinearLayout(c)
    private val categories=LinearLayout(c)
    private val search=EditText(c).apply{hint="차, 책, 개발… 취향을 찾아보세요";contentDescription="이모지 검색";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);minHeight=dp(48)}
    private val status=TextView(c).apply{setTextColor(Color.LTGRAY);textSize=12f;setPadding(dp(8),dp(4),dp(8),dp(4));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
    private val grid=RecyclerView(c).apply{contentDescription="스티커 이모지 목록";layoutManager=GridLayoutManager(c,(c.resources.configuration.screenWidthDp/100).coerceIn(3,6));clipToPadding=false}
    private var items=emptyList<EmojiItem>()
    private var tab="내 이모지"
    private var category="전체"
    private var folderError=false
    private fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
    private fun control(label:String,action:()->Unit)=TextView(context).apply{text=label;contentDescription=label;setTextColor(Color.WHITE);textSize=13f;gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0);minHeight=dp(48);minWidth=dp(48);isFocusable=true;setOnClickListener{action()}}
    private fun strip(row:LinearLayout)=HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false;addView(row)}
    private fun chips(row:LinearLayout,values:List<String>,current:String,change:(String)->Unit){
        row.removeAllViews()
        values.forEach{value->row.addView(control(value){change(value)}.apply{
            isSelected=value==current;setTextColor(if(isSelected)0xFF1B1B1F.toInt() else Color.WHITE)
            background=GradientDrawable().apply{setColor(if(isSelected)0xFFD4FF83.toInt() else 0xFF303036.toInt());cornerRadius=dp(24).toFloat()}
        },LinearLayout.LayoutParams(-2,dp(48)).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})}
    }
    init{
        val heading=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL}
        heading.addView(TextView(c).apply{text="내 취향 스티커";setTextColor(Color.WHITE);textSize=18f},LinearLayout.LayoutParams(0,dp(48),1f))
        heading.addView(control("닫기"){dismiss()});box.addView(heading)
        box.addView(strip(tabs))
        val searchRow=LinearLayout(c);searchRow.addView(search,LinearLayout.LayoutParams(0,dp(48),1f));searchRow.addView(control("지우기"){search.text.clear()}.apply{contentDescription="이모지 검색 지우기"});box.addView(searchRow)
        box.addView(strip(categories));box.addView(status)
        box.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
        val actions=LinearLayout(c);actions.addView(control("새로고침"){load()});actions.addView(control("폴더 변경"){dismiss();connect()});box.addView(actions)
        setContentView(box)
        window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setGravity(Gravity.BOTTOM);setDimAmount(.25f);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)}
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun afterTextChanged(s:android.text.Editable?){populate()}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){}
        })
    }
    override fun show(){super.show();window?.setLayout(-1,(context.resources.displayMetrics.heightPixels*.85f).toInt());load()}
    private fun populate(){
        chips(tabs,listOf("내 이모지","내 폴더","최근 사용","즐겨찾기"),tab){tab=it;category="전체";populate()}
        chips(categories,listOf("전체")+items.map{it.category}.distinct(),category){category=it;populate()}
        val favorites=prefs.getStringSet("favorites",emptySet())?:emptySet()
        val recent=prefs.getString("recent","").orEmpty().split("|").filter{it.isNotBlank()}
        val shown=EmojiLibrary.select(items,tab,category,search.text.toString(),favorites,recent)
        status.text=when{
            folderError && tab=="내 폴더"->"폴더를 읽을 수 없습니다 · 폴더 변경으로 다시 연결하세요"
            shown.isNotEmpty()->"${shown.size}개 · 별을 눌러 즐겨찾기에 보관하세요"
            search.text.isNotBlank()->"검색 결과가 없어요 · 검색어나 분류를 바꿔보세요"
            tab=="즐겨찾기"->"자주 쓰는 스티커의 별을 눌러 모아보세요"
            tab=="최근 사용"->"사용한 스티커가 여기에 순서대로 모여요"
            tab=="내 폴더"->"폴더 변경을 눌러 내 PNG 폴더를 연결하세요"
            else->"이 분류에는 스티커가 없어요"
        }
        grid.adapter=EmojiAdapter(shown,favorites,{item->
            prefs.edit().putString("recent",(listOf(item.uri)+recent.filter{it!=item.uri}).take(30).joinToString("|")).apply()
            dismiss();choose(item)
        },{item->
            val updated=favorites.toMutableSet();val added=updated.add(item.uri);if(!added)updated.remove(item.uri)
            prefs.edit().putStringSet("favorites",updated).apply();val state=grid.layoutManager?.onSaveInstanceState();populate();grid.layoutManager?.onRestoreInstanceState(state)
            Toast.makeText(context,if(added)"즐겨찾기에 보관했어요" else "즐겨찾기에서 해제했어요",Toast.LENGTH_SHORT).show()
        })
    }
    private fun load(){
        status.text="이모지 불러오는 중…"
        val path=prefs.getString("emojiFolder",null)
        Thread{
            val bundled=runCatching{EmojiLibrary.defaults(context.applicationContext)}
            val user=runCatching{if(path==null)emptyList() else EmojiFolder.scan(context.applicationContext,android.net.Uri.parse(path)).map{EmojiLibrary.item(it)}}
            box.post{if(isShowing){items=bundled.getOrDefault(emptyList())+user.getOrDefault(emptyList());folderError=user.isFailure;populate();if(bundled.isFailure)status.text="기본 이모지를 읽지 못했어요 · 새로고침을 눌러주세요"}}
        }.start()
    }
}
