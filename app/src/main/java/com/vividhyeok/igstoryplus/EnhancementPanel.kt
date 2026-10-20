package com.vividhyeok.igstoryplus

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

/** Two short, explicit pages; the story remains visible above the dock. */
class EnhancementPanel(private val activity:Activity,private val project:Project,private val layer:Layer?,private val clock:PreviewClock,private val checkpoint:()->Unit,private val redraw:()->Unit,private val save:()->Unit){
    private var selectedLayer=layer?:project.layers.firstOrNull{it.kind!="music"}
    private fun dp(n:Int)=(activity.resources.displayMetrics.density*n).toInt()
    private fun column()=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),dp(6))}
    private fun layerNames(names:List<String>)=object:ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,names){
        override fun getView(position:Int,convert:View?,parent:ViewGroup):View=(super.getView(position,convert,parent) as TextView).apply{setTextColor(Color.WHITE);setBackgroundColor(0xFF303039.toInt());textSize=14f;minHeight=dp(48);setPadding(dp(12),0,dp(12),0)}
        override fun getDropDownView(position:Int,convert:View?,parent:ViewGroup):View=(super.getDropDownView(position,convert,parent) as TextView).apply{setTextColor(Color.WHITE);setBackgroundColor(0xFF303039.toInt());textSize=14f;minHeight=dp(48);setPadding(dp(12),0,dp(12),0)}
    }
    private fun label(value:String)=TextView(activity).apply{text=value;setTextColor(0xFFCCCCD5.toInt());textSize=12f;setPadding(dp(2),dp(8),dp(2),dp(6))}
    private fun button(value:String,action:()->Unit)=TextView(activity).apply{text=value;contentDescription=value;setTextColor(Color.WHITE);textSize=14f;gravity=Gravity.CENTER;minHeight=dp(44);setPadding(dp(10),0,dp(10),0);setOnClickListener{action()}}
    private fun decorate(v:TextView,selected:Boolean){v.isSelected=selected;v.setTextColor(if(selected)0xFF15151C.toInt()else 0xFFF4F4FA.toInt());v.typeface=if(selected)Typeface.DEFAULT_BOLD else Typeface.DEFAULT;v.background=GradientDrawable().apply{cornerRadius=dp(12).toFloat();setColor(if(selected)0xFFF0EEFF.toInt()else 0xFF303039.toInt());setStroke(dp(1),if(selected)0xFFF0EEFF.toInt()else 0xFF50505C.toInt())}}
    private fun choices(body:LinearLayout,items:List<Pair<String,String>>,value:()->String,choose:(String)->Unit){
        val row=LinearLayout(activity)
        items.forEach{(title,id)->row.addView(button(title){checkpoint();choose(id);for(i in items.indices)decorate(row.getChildAt(i) as TextView,items[i].second==value());clock.updateStyle();clock.seek(clock.positionMs);save()}.apply{decorate(this,id==value())},LinearLayout.LayoutParams(0,dp(44),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})};body.addView(row)
    }
    private fun slider(body:LinearLayout,title:String,value:Float,change:(Float)->Unit):SeekBar{
        val caption=label("$title  ${(value*100).toInt()}%");body.addView(caption)
        return SeekBar(activity).apply{contentDescription=title;max=100;progress=(value*100).toInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){checkpoint()};override fun onStopTrackingTouch(s:SeekBar){save()};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){caption.text="$title  $p%";if(user){change(p/100f);clock.seek(clock.positionMs)}}})}.also{body.addView(it,LinearLayout.LayoutParams(-1,dp(48)))}
    }
    fun show(){
        val body=column();val tabs=LinearLayout(activity);body.addView(tabs)
        val controls=LinearLayout(activity);val play=button(if(clock.playing)"일시정지"else "재생"){}
        play.contentDescription="플러스 미리보기";play.setOnClickListener{clock.toggle();play.text=if(clock.playing)"일시정지"else "재생"}
        controls.addView(play,LinearLayout.LayoutParams(0,dp(44),1f));controls.addView(button("처음부터"){clock.seek(0)},LinearLayout.LayoutParams(0,dp(44),1f));body.addView(controls)
        val content=column();body.addView(content)
        var page=if(layer!=null || (project.music==null && !project.video))0 else 1
        fun render(){content.removeAllViews();if(page==0)layers(content)else music(content);for(i in 0..1)decorate(tabs.getChildAt(i) as TextView,page==i)}
        listOf("레이어 효과","음악 반응").forEachIndexed{i,title->tabs.addView(button(title){page=i;render()},LinearLayout.LayoutParams(0,dp(44),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})}
        render()
        EditorDock(activity,"플러스",body,{clock.pausePlayback();save()}).apply{maxBodyHeightDp=300;setOnDismissListener{clock.pausePlayback();save()};show()}
    }
    private fun layers(body:LinearLayout){
        val layers=project.layers.filter{it.kind!="music"}
        if(layers.isEmpty()){body.addView(label("텍스트·사진·스티커를 추가하면 하트와 별빛을 붙일 수 있어요."));return}
        val effect=column()
        val names=layers.mapIndexed{i,l->"${i+1}. ${when(l.kind){"text"->l.text.replace(Regex("[\uE000-\uF8FF]"),"◈").take(18);"photo"->"사진";else->"내 이모지"}}"}
        body.addView(Spinner(activity).apply{contentDescription="효과 적용 레이어";adapter=layerNames(names);setSelection(layers.indexOf(selectedLayer).coerceAtLeast(0));onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){selectedLayer=layers[position];renderEffect(effect,layers[position])}}},LinearLayout.LayoutParams(-1,dp(48)))
        body.addView(effect);renderEffect(effect,selectedLayer?.takeIf{it in layers}?:layers.first())
    }
    private fun renderEffect(body:LinearLayout,l:Layer){
        body.removeAllViews()
        choices(body,listOf("없음" to "없음","하트" to "하트 글리터","별빛" to "별빛"),{l.accent}){l.accent=it;redraw()}
        slider(body,"반짝임 강도",l.accentAmount){l.accentAmount=it}
        body.addView(Switch(activity).apply{text="음악 박동으로 살짝 확대";contentDescription="선택 레이어 음악 박동";setTextColor(Color.WHITE);textSize=14f;minHeight=dp(48);isChecked=l.beatMotion;setOnCheckedChangeListener{_,on->checkpoint();l.beatMotion=on;save()}})
        body.addView(label("윤곽에만 반짝임 · 음악 없이도 부드럽게 반복"))
    }
    private fun music(body:LinearLayout){
        val available=project.video || project.music!=null
        var syncing=false
        val master=Switch(activity).apply{text="음악에 맞춰 반응";setTextColor(Color.WHITE);textSize=15f;minHeight=dp(48);isChecked=project.style.beat>0;isEnabled=available};body.addView(master)
        val strength=slider(body,"음악 반응 강도",project.style.beat){project.style.beat=it;syncing=true;master.isChecked=it>0;syncing=false;clock.updateStyle()}.apply{isEnabled=available}
        master.setOnCheckedChangeListener{_,on->if(!syncing){checkpoint();project.style.beat=if(on).55f else 0f;strength.progress=(project.style.beat*100).toInt();clock.updateStyle();save()}}
        if(!available){body.addView(label("음악 파일이나 소리가 있는 영상을 먼저 추가하세요."));return}
        val sources=listOf("자동")+(if(project.video)listOf("원본 영상")else emptyList())+(if(project.music!=null)listOf("추가 음악")else emptyList())
        body.addView(label("반응할 소리"));choices(body,sources.map{it to it},{project.reactionAudio}){clock.pausePlayback();project.reactionAudio=it}
        body.addView(label("빛·색 반응을 적용할 곳"))
        choices(body,if(project.video)listOf("영상 화면" to "원본 화면 전체")else listOf("배경만" to "배경만","사진까지" to "원본 화면 전체"),{if(project.style.reactionTarget=="기존")"원본 화면 전체"else project.style.reactionTarget}){project.style.reactionTarget=it}
        val modes=mutableListOf("은은한 빛" to "빛 반응","컬러 오라" to "Album Aura");if(project.music!=null && project.layers.any{it.kind=="music"})modes.add("카드 점" to "Music Dot")
        body.addView(label("반응 스타일"));choices(body,modes,{project.style.reaction}){project.style.reaction=it;if(project.video)project.style.reactionTarget="원본 화면 전체"}
        val status=label(if(project.video)"영상에 합쳐진 글자도 화면과 함께 반응합니다."else "배경만: 사진 뒤 여백 · 사진까지: 원본 사진도 함께");body.addView(status)
        if(project.video)Thread{val has=runCatching{Audio.hasAudio(activity.applicationContext,project.source)}.getOrDefault(false);activity.runOnUiThread{if(status.isAttachedToWindow && !has)status.text="이 영상에는 소리가 없어요. 음악 파일을 추가하세요."}}.start()
    }
}
