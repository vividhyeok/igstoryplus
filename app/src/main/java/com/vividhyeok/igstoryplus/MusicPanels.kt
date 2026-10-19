package com.vividhyeok.igstoryplus

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.text.*
import android.view.*
import android.widget.*
import com.bumptech.glide.Glide
import java.util.Locale

/** Offline music flow: library → waveform → visual card, all sharing the canvas clock. */
class MusicPanels(private val activity:Activity,private val project:()->Project,private val clock:PreviewClock,
                  private val checkpoint:()->Unit,private val changed:()->Unit,private val save:()->Unit,
                  private val import:(String)->Unit,private val select:(Music)->Unit,private val remove:()->Unit){
    private val library=MusicLibrary(activity)
    private var clipDialog:Dialog?=null
    private fun dp(n:Int)=(activity.resources.displayMetrics.density*n).toInt()
    private fun column()=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(8))}
    private fun label(s:String,size:Float=14f)=TextView(activity).apply{text=s;textSize=size;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;minHeight=dp(32)}
    private fun button(s:String,action:()->Unit)=label(s,15f).apply{gravity=Gravity.CENTER;minHeight=dp(48);minimumWidth=dp(48);isFocusable=true;contentDescription=s;setOnClickListener{action()}}
    private fun sheet(title:String,body:View,done:(()->Unit)?=null,fraction:Float=.70f,canFinish:()->Boolean={true})=EditorSheet(activity,title,body,done,fraction,canFinish).also{it.show()}
    fun library(){
        clipDialog?.dismiss();clipDialog=null
        val body=column();var dialog:Dialog?=null
        val search=EditText(activity).apply{hint="곡명 또는 아티스트 검색";contentDescription="음악 검색";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setSingleLine(true)}
        body.addView(search,LinearLayout.LayoutParams(-1,dp(52)))
        body.addView(button("로컬 음악 가져오기"){dialog?.dismiss();import("music")})
        body.addView(label("최근 사용 · 내 음악",12f).apply{setTextColor(Color.LTGRAY)})
        val rows=column();body.addView(rows)
        val items=library.all()
        fun populate(query:String){
            rows.removeAllViews()
            items.filter{query.isBlank() || "${it.title} ${it.artist}".contains(query,true)}.forEach{m->
                val row=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;contentDescription="음악 선택: ${m.title}";minimumHeight=dp(72);isFocusable=true}
                row.addView(ImageView(activity).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(0xFF39393F.toInt());Glide.with(this).load(m.art).into(this)},LinearLayout.LayoutParams(dp(52),dp(52)))
                row.addView(column().apply{addView(label(m.title,15f).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END});addView(label("${m.artist} · ${m.durationMs/1000}초",12f).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END;setTextColor(Color.LTGRAY)})},LinearLayout.LayoutParams(0,-2,1f))
                row.setOnClickListener{row.isEnabled=false;Thread{val readable=library.readable(m);activity.runOnUiThread{if(!dialog!!.isShowing)return@runOnUiThread;if(readable){dialog?.dismiss();select(m.copy())}else{row.isEnabled=true;Toast.makeText(activity,"원본 음악이 없거나 권한이 만료되었습니다. 파일을 다시 가져오세요.",Toast.LENGTH_LONG).show()}}}.start()}
                rows.addView(row)
            }
            if(rows.childCount==0)rows.addView(label(if(items.isEmpty())"음악 파일을 한 번 가져오면 여기에 보관됩니다" else "검색 결과가 없습니다",13f))
        }
        search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){populate(s.toString())};override fun afterTextChanged(s:Editable?) {}})
        populate("");dialog=sheet("내 음악",body,fraction=.78f)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    fun clip(){
        val p=project();val m=p.music?:return library()
        clock.pausePlayback();MusicRange.clamp(m,p.durationMs());clock.seek(0)
        val body=column();val header=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(ImageView(activity).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(0xFF39393F.toInt());Glide.with(this).load(m.art).into(this)},LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(column().apply{addView(label(m.title,16f).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END});addView(label(m.artist,12f).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END;setTextColor(Color.LTGRAY)})},LinearLayout.LayoutParams(0,-2,1f))
        header.addView(EditorIcons.button(activity,"수정"){metadata()});body.addView(header)
        val range=label("").apply{gravity=Gravity.CENTER};body.addView(range)
        val waveform=AudioWaveformView(activity).apply{tag="music-waveform";durationMs=m.durationMs;clipMs=p.durationMs();startMs=m.startMs}
        body.addView(waveform,LinearLayout.LayoutParams(-1,dp(112)))
        val loading=label("실제 오디오 파형 분석 중…",12f).apply{gravity=Gravity.CENTER;setTextColor(Color.LTGRAY)};body.addView(loading)
        val controls=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
        val length=button(if(p.video)"${p.durationMs()/1000}초 · 영상 길이" else "${p.duration}초"){}
        val auditionHandler=android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var play:ImageButton
        play=EditorIcons.button(activity,"미리보기 재생"){if(clock.playing)clock.pausePlayback() else if(MusicRange.valid(m,p.durationMs()))clock.play() else Toast.makeText(activity,"음악이 짧습니다. 길이를 줄이거나 반복을 켜세요.",Toast.LENGTH_LONG).show();play.setImageDrawable(EditorIcons.drawable(if(clock.playing)"미리보기 일시정지" else "미리보기 재생"));play.contentDescription=if(clock.playing)"선택 구간 일시정지" else "선택 구간 재생"}
        controls.addView(length,LinearLayout.LayoutParams(0,dp(48),1f));controls.addView(play,LinearLayout.LayoutParams(dp(48),dp(48)))
        controls.addView(EditorIcons.button(activity,"처음부터"){clock.seek(0)},LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(controls)
        fun update(){
            MusicRange.clamp(m,p.durationMs());waveform.clipMs=p.durationMs();waveform.startMs=m.startMs
            range.text=String.format(Locale.ROOT,"%.1f — %.1f초",m.startMs/1000f,(m.startMs+p.durationMs()).coerceAtMost(m.durationMs)/1000f)
            length.text=if(p.video)"${p.durationMs()/1000}초 · 영상 길이" else "${p.duration}초"
            if(!MusicRange.valid(m,p.durationMs()))loading.text="음악이 짧습니다. 길이를 줄이거나 반복을 직접 켜세요."
            else if(waveform.peaks.isNotEmpty())loading.text="파형을 좌우로 드래그해 구간 선택"
            waveform.invalidate();save()
        }
        length.setOnClickListener{if(!p.video){val choices=column();var d:Dialog?=null;val grid=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL};for(chunk in (5..15).toList().chunked(4)){val row=LinearLayout(activity);chunk.forEach{seconds->row.addView(button("${seconds}초"){checkpoint();p.duration=seconds;activity.getSharedPreferences("settings",0).edit().putInt("musicDuration",seconds).apply();clock.pausePlayback();clock.seek(0);update();d?.dismiss()},LinearLayout.LayoutParams(0,dp(48),1f))};grid.addView(row)};choices.addView(grid);d=sheet("음악 길이",choices)}}
        var resumeAfterDrag=false
        val endAudition=Runnable{clock.pausePlayback();play.setImageDrawable(EditorIcons.drawable("미리보기 재생"));play.contentDescription="선택 구간 재생"}
        play.setOnTouchListener{_,event->if(event.actionMasked==MotionEvent.ACTION_DOWN)auditionHandler.removeCallbacks(endAudition);false}
        waveform.onStart={auditionHandler.removeCallbacks(endAudition);resumeAfterDrag=clock.playing;checkpoint();clock.pausePlayback();play.setImageDrawable(EditorIcons.drawable("미리보기 재생"));play.contentDescription="선택 구간 재생"}
        waveform.onRange={start,finished->m.startMs=start;range.text=String.format(Locale.ROOT,"%.1f — %.1f초",start/1000f,(start+p.durationMs()).coerceAtMost(m.durationMs)/1000f);if(finished){save();clock.seek(0);if(MusicRange.valid(m,p.durationMs())){clock.play();if(!resumeAfterDrag)auditionHandler.postDelayed(endAudition,800)};resumeAfterDrag=false;play.setImageDrawable(EditorIcons.drawable(if(clock.playing)"미리보기 일시정지" else "미리보기 재생"));play.contentDescription=if(clock.playing)"선택 구간 일시정지" else "선택 구간 재생"}}
        val loop=Switch(activity).apply{text="짧은 음악 반복 · 기본 OFF";setTextColor(Color.WHITE);isChecked=m.loop;minHeight=dp(48);setOnCheckedChangeListener{_,enabled->checkpoint();m.loop=enabled;clock.pausePlayback();clock.seek(0);update()}}
        body.addView(loop)
        val reaction=Switch(activity).apply{text="음악 반응";setTextColor(Color.WHITE);minHeight=dp(48);isChecked=p.style.beat>0}
        val strength=SeekBar(activity).apply{max=100;progress=(p.style.beat*100).toInt();contentDescription="음악 반응 강도";minimumHeight=dp(48);isEnabled=reaction.isChecked}
        reaction.setOnCheckedChangeListener{_,enabled->checkpoint();p.style.beat=if(enabled).55f else 0f;strength.isEnabled=enabled;strength.progress=(p.style.beat*100).toInt();clock.updateStyle();save()}
        strength.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){checkpoint()}
            override fun onStopTrackingTouch(s:SeekBar){save()}
            override fun onProgressChanged(s:SeekBar,value:Int,user:Boolean){if(user){p.style.beat=value/100f;clock.updateStyle()}}
        })
        body.addView(reaction);body.addView(strength)
        body.addView(button("음악 카드 디자인"){cards()})
        val extra=LinearLayout(activity)
        extra.addView(button("곡 교체"){library()},LinearLayout.LayoutParams(0,dp(48),1f))
        extra.addView(button("볼륨"){volume()},LinearLayout.LayoutParams(0,dp(48),1f));body.addView(extra)
        var dialog:Dialog?=null
        dialog=sheet("음악 구간",body,{clock.pausePlayback();save();changed()},.78f,{val valid=MusicRange.valid(m,p.durationMs());if(!valid)Toast.makeText(activity,"길이를 줄이거나 반복을 켜야 음악 구간을 완료할 수 있습니다.",Toast.LENGTH_LONG).show();valid})
        clipDialog=dialog
        clock.onPosition={position->if(dialog.isShowing)waveform.playheadMs=position}
        play.contentDescription="선택 구간 재생"
        dialog.setOnDismissListener{auditionHandler.removeCallbacks(endAudition);clock.pausePlayback();clock.onPosition=null;save()}
        val snapshot=m.copy();val app=activity.applicationContext
        WaveformCache.worker.execute{val result=runCatching{WaveformCache.peaks(app,snapshot)};activity.runOnUiThread{if(dialog.isShowing){result.onSuccess{waveform.peaks=it;loading.text=if(MusicRange.valid(m,p.durationMs()))"파형을 좌우로 드래그해 구간 선택" else "음악이 짧습니다. 길이를 줄이거나 반복을 켜세요."}.onFailure{loading.text="파형을 읽지 못했습니다. 음악 파일을 다시 선택해 주세요."}}}}
        update()
    }
    fun cards(){
        clipDialog?.dismiss();clipDialog=null
        val m=project().music?:return
        val body=column();val row=LinearLayout(activity);val previews=mutableListOf<View>()
        listOf("기본 카드","작은 카드","앨범 아트","텍스트","숨김").forEach{style->
            val tile=column();val image=ImageView(activity).apply{scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="음악 카드: $style";minimumHeight=dp(104)}
            if(style=="숨김")image.setImageDrawable(EditorIcons.drawable("닫기")) else image.setImageBitmap(Artwork.musicCard(activity,m.copy(card=style),Layer(kind="music")))
            tile.addView(image,LinearLayout.LayoutParams(dp(140),dp(110)));tile.addView(label(style,12f).apply{gravity=Gravity.CENTER})
            tile.alpha=if(m.card==style)1f else .6f;previews+=tile
            image.setOnClickListener{checkpoint();m.card=style;previews.forEach{it.alpha=.6f};tile.alpha=1f;changed();save()}
            row.addView(tile)
        }
        body.addView(HorizontalScrollView(activity).apply{isHorizontalScrollBarEnabled=false;addView(row)})
        sheet("음악 카드",body)
    }
    private fun metadata(){
        val m=project().music?:return;val body=column()
        val fields=listOf("곡명" to m.title,"아티스트" to m.artist,"앨범" to m.album).map{(name,value)->EditText(activity).apply{hint=name;contentDescription=name;setText(value);setTextColor(Color.WHITE);setSingleLine(true)}}.onEach{body.addView(it)}
        var dialog:Dialog?=null
        body.addView(button("앨범 아트 선택"){dialog?.dismiss();clipDialog?.dismiss();import("art")})
        body.addView(button("음악 삭제"){dialog?.dismiss();clipDialog?.dismiss();remove()})
        dialog=sheet("곡 정보",body,{(activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(fields[0].windowToken,0);checkpoint();m.title=fields[0].text.toString();m.artist=fields[1].text.toString();m.album=fields[2].text.toString();save();changed();clipDialog?.dismiss();clip()})
    }
    fun volume(){
        val p=project();val m=p.music?:return;val body=column()
        fun control(name:String,value:Float,change:(Float)->Unit){
            val title=label("$name · ${(value*100).toInt()}%");body.addView(title)
            body.addView(SeekBar(activity).apply{max=100;progress=(value*100).toInt();contentDescription=name;minimumHeight=dp(48)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onStartTrackingTouch(s:SeekBar){checkpoint()}
                    override fun onStopTrackingTouch(s:SeekBar){save()}
                    override fun onProgressChanged(s:SeekBar,value:Int,user:Boolean){if(user){change(value/100f);title.text="$name · $value%"}}
                })
            })
        }
        control("음악 볼륨",m.volume){m.volume=it;clock.setVolume(it)}
        if(p.video)control("영상 원본 볼륨",p.sourceVolume){p.sourceVolume=it;clock.setSourceVolume(it)}
        body.addView(button("소리 미리듣기 · 재생/일시정지"){clock.toggle()})
        sheet("오디오 믹서",body,{save()}).setOnDismissListener{clock.pausePlayback();save()}
    }
}
