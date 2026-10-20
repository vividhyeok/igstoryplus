package com.vividhyeok.igstoryplus

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class EnhancementPanel(private val activity:Activity,private val project:Project,private val layer:Layer?,private val clock:PreviewClock,private val checkpoint:()->Unit,private val redraw:()->Unit,private val save:()->Unit){
    private fun dp(n:Int)=(activity.resources.displayMetrics.density*n).toInt()
    private fun label(text:String)=TextView(activity).apply{this.text=text;setTextColor(Color.LTGRAY);textSize=12f;setPadding(dp(12),dp(6),dp(12),dp(6))}
    private fun button(text:String,action:()->Unit)=TextView(activity).apply{this.text=text;contentDescription=text;gravity=Gravity.CENTER;setTextColor(Color.WHITE);minHeight=dp(48);setPadding(dp(12),0,dp(12),0);setOnClickListener{action()}}
    fun show(){
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        val preview=LinearLayout(activity);preview.addView(button("재생 / 일시정지"){clock.toggle()},LinearLayout.LayoutParams(0,dp(48),1f));preview.addView(button("처음부터"){clock.seek(0)},LinearLayout.LayoutParams(0,dp(48),1f));body.addView(preview)
        fun choices(title:String,values:List<String>,selected:()->String,choose:(String)->Unit){
            body.addView(label(title));val row=LinearLayout(activity)
            values.forEach{value->row.addView(button(value){checkpoint();choose(value);for(i in 0 until row.childCount)row.getChildAt(i).alpha=if((row.getChildAt(i) as TextView).text==selected())1f else .45f;clock.updateStyle();save()}.apply{alpha=if(value==selected())1f else .45f})}
            body.addView(HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;addView(row)})
        }
        if(layer!=null && layer.kind!="music"){
            choices("선택한 ${if(layer.kind=="text")"텍스트" else if(layer.kind=="photo")"사진" else "스티커"}에만 적용",listOf("없음","하트 글리터","별빛"),{layer.accent}){layer.accent=it;redraw()}
            body.addView(label("반짝임 강도"));body.addView(SeekBar(activity).apply{contentDescription="반짝임 강도";max=100;progress=(layer.accentAmount*100).toInt();setOnSeekBarChangeListener(listener({checkpoint()},{layer.accentAmount=it;clock.seek(clock.positionMs)},{save()}))},LinearLayout.LayoutParams(-1,dp(48)))
            body.addView(Switch(activity).apply{text="선택 레이어 음악 박동";setTextColor(Color.WHITE);minHeight=dp(48);isChecked=layer.beatMotion;setOnCheckedChangeListener{_,enabled->checkpoint();layer.beatMotion=enabled;save()}})
            body.addView(label("하트·별빛은 윤곽에 부드럽게 반짝입니다. 음악 반응을 켜면 박자도 함께 반영합니다."))
        }else{
            body.addView(label("글자·사진·스티커를 선택한 뒤 플러스를 열면 하트·별빛을 붙일 수 있습니다."))
            body.addView(button("모든 텍스트에 하트 글리터"){val text=project.layers.filter{it.kind=="text"};if(text.isEmpty())Toast.makeText(activity,"텍스트를 먼저 추가하세요",Toast.LENGTH_SHORT).show()else{checkpoint();text.forEach{it.accent="하트 글리터"};redraw();save()}})
        }
        body.addView(Switch(activity).apply{text="음악에 맞춰 반응";setTextColor(Color.WHITE);minHeight=dp(48);isChecked=project.style.beat>0;setOnCheckedChangeListener{_,on->checkpoint();project.style.beat=if(on).55f else 0f;clock.updateStyle();save()}})
        choices("반응 기준 소리",listOf("자동")+(if(project.video)listOf("원본 영상")else emptyList())+(if(project.music!=null)listOf("추가 음악")else emptyList()),{project.reactionAudio}){clock.pausePlayback();project.reactionAudio=it}
        val audioStatus=label("원본 영상의 저장된 소리 또는 추가한 음악을 분석합니다.");body.addView(audioStatus)
        if(project.video){val uri=project.source;Thread{val has=runCatching{Audio.hasAudio(activity.applicationContext,uri)}.getOrDefault(false);activity.runOnUiThread{audioStatus.text=if(has)"원본 영상에 소리가 있습니다 · 음악을 다시 고르지 않아도 반응 가능" else "원본 영상에 소리가 없습니다 · 음악 파일을 추가하세요"}}.start()}
        choices("빛·색 반응 대상 · 별도 레이어에는 영향 없음",listOf("배경만","원본 화면 전체"),{if(project.style.reactionTarget=="기존")if(project.style.reaction=="빛 반응")"원본 화면 전체" else "배경만" else project.style.reactionTarget}){project.style.reactionTarget=it}
        body.addView(label(if(project.video)"영상의 글자·스티커가 이미 합쳐져 있으면 원본 화면과 함께 반응합니다. 배경만은 영상에 적용되지 않습니다." else "배경만: 원본 사진 뒤 여백 / 원본 화면 전체: 배경과 원본 사진"))
        choices("빛·색 반응 종류",listOf("빛 반응","Album Aura","Music Dot"),{project.style.reaction}){project.style.reaction=it}
        body.addView(label("음악 반응 강도"));body.addView(SeekBar(activity).apply{contentDescription="플러스 음악 반응 강도";max=100;progress=(project.style.beat*100).toInt();setOnSeekBarChangeListener(listener({checkpoint()},{project.style.beat=it;clock.updateStyle()},{save()}))},LinearLayout.LayoutParams(-1,dp(48)))
        EditorDock(activity,"스토리 플러스",body,{clock.pausePlayback();save()}).show()
    }
    private fun listener(start:()->Unit,change:(Float)->Unit,end:()->Unit)=object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){start()};override fun onStopTrackingTouch(s:SeekBar){end()};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user)change(p/100f)}}
}
