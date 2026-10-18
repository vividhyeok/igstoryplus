package com.vividhyeok.igstoryplus

import android.app.AlertDialog
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import java.io.File
import kotlin.math.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity() {
    lateinit var project: Project
    private lateinit var store: Store
    private val history=History()
    private lateinit var editor: PhotoEditorView
    private lateinit var engine: PhotoEditor
    private lateinit var live: LivePhoto
    private lateinit var canvasBox: FrameLayout
    private lateinit var contextBar: LinearLayout
    private lateinit var status: TextView
    private lateinit var empty: LinearLayout
    private var selected: String?=null
    private val layerViews=linkedMapOf<String,View>()
    private var rebuilding=false
    private var importMode="base"
    private var cameraUri: Uri?=null
    private var activeExport: Exporter?=null
    private var audioPlayer: MediaPlayer?=null
    private val handler=Handler(Looper.getMainLooper())
    private var backgroundGeneration=0
    private val backgroundWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var backgroundUpdate:Runnable?=null
    private lateinit var guides: AlignmentGuides
    private var previewPlayer: ExoPlayer?=null
    private var latest: File?=null
    private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)importUri(uri)}
    private val folderPicker=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        if(uri!=null) safe {
            contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("emojiFolder",uri.toString()).apply(); emojiLibrary()
        }
    }
    private val capturePhoto=registerForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)cameraUri?.let{importMode="base";importUri(it)}}
    private val captureVideo=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==RESULT_OK)result.data?.data?.let{uri->safe{
            val f=File(filesDir,"imports/capture-${System.currentTimeMillis()}.mp4").apply{parentFile!!.mkdirs()}
            contentResolver.openInputStream(uri)!!.use{i->f.outputStream().use{i.copyTo(it)}}
            importMode="base";importUri(Uri.fromFile(f))
        }}
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store=Store(this); project=store.load(); buildUi()
        cameraUri=savedInstanceState?.getString("cameraUri")?.let{Uri.parse(it)}
        importMode=savedInstanceState?.getString("importMode")?:"base"
        Thread{
            val cutoff=System.currentTimeMillis()-2*24*60*60*1000L
            File(cacheDir,".").listFiles()?.filter{it.name.startsWith("render-source-") && it.lastModified()<cutoff}?.forEach{it.delete()}
        }.start()
        canvasBox.post { restore() }
    }
    override fun onSaveInstanceState(outState:Bundle){outState.putString("cameraUri",cameraUri?.toString());outState.putString("importMode",importMode);super.onSaveInstanceState(outState)}
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun rounded(color: Int,radius: Float=16f)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius.toInt()).toFloat()}
    private fun text(value: String,size: Float=14f)=TextView(this).apply{text=value;setTextColor(Color.WHITE);textSize=size;gravity=Gravity.CENTER;setPadding(dp(8),dp(6),dp(8),dp(6))}
    private fun button(label: String,action:()->Unit)=text(label).apply{minimumHeight=dp(48);minWidth=dp(48);contentDescription=label;isClickable=true;isFocusable=true;setOnClickListener{safe(action)}}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(10),dp(18),dp(10))}
    private fun scrollRow(r:LinearLayout)=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(r)}
    private fun buildUi() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(16,16,20))}
        ViewCompat.setOnApplyWindowInsetsListener(root){v,i->val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);i}
        val top=row().apply{setPadding(dp(6),0,dp(6),0)}
        top.addView(text("igstoryplus",19f),LinearLayout.LayoutParams(0,dp(52),1f))
        top.addView(button("↶"){history.undo(project)?.let{project=it;restore()}})
        top.addView(button("↷"){history.redo(project)?.let{project=it;restore()}})
        top.addView(button("더보기"){more()}); root.addView(top)
        val center=FrameLayout(this)
        canvasBox=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);clipChildren=true}
        center.addView(canvasBox,FrameLayout.LayoutParams(1,1,Gravity.CENTER))
        live=LivePhoto(this); canvasBox.addView(live,FrameLayout.LayoutParams(-1,-1))
        editor=PhotoEditorView(this).apply{setBackgroundColor(Color.TRANSPARENT);clipChildren=true}
        canvasBox.addView(editor,FrameLayout.LayoutParams(-1,-1))
        guides=AlignmentGuides(this);canvasBox.addView(guides,FrameLayout.LayoutParams(-1,-1))
        editor.source.setImageBitmap(Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888))
        engine=PhotoEditor.Builder(this,editor).setPinchTextScalable(true).build()
        editor.onLayerGesture={v,start->
            if(!rebuilding){
                if(start){history.push(project); selected=layerViews.entries.firstOrNull{it.value===v}?.key; contextTools()}
                else { guides.clear();snap(v); syncTransforms(); persist() }
            }
        }
        editor.onLayerTransform={v->guides.follow(v,editor)}
        center.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
            val h=center.height;val w=min(center.width,(h*9f/16).toInt()); val ch=(w*16f/9).toInt()
            if(w>0 && (canvasBox.width!=w || canvasBox.height!=ch)){canvasBox.layoutParams=FrameLayout.LayoutParams(w,ch,Gravity.CENTER);canvasBox.post{restoreLayers()}}
        }
        empty=column().apply{gravity=Gravity.CENTER;addView(text("오늘의 한 장에\n작은 움직임을.",27f));addView(text("사진을 고르고 바로 시작하세요",14f));addView(button("사진 · 영상 선택"){pick("base",arrayOf("image/*","video/*"))}.apply{background=rounded(Color.rgb(53,57,47))});addView(button("카메라"){camera()})}
        canvasBox.addView(empty,FrameLayout.LayoutParams(-1,-1))
        root.addView(center,LinearLayout.LayoutParams(-1,0,1f))
        status=text("사진을 선택해 시작",12f).apply{setTextColor(Color.LTGRAY)}; root.addView(status)
        contextBar=row();root.addView(scrollRow(contextBar))
        val tools=row()
        tools.addView(button("사진"){if(project.source.isBlank())pick("base",arrayOf("image/*","video/*")) else photoMenu()})
        tools.addView(button("스타일"){styles()});tools.addView(button("Aa 텍스트"){textEditor(null)})
        tools.addView(button("내 이모지"){emojiLibrary()});tools.addView(button("음악"){musicMenu()})
        root.addView(scrollRow(tools))
        val bottom=row().apply{setPadding(dp(10),dp(4),dp(10),dp(8))}
        bottom.addView(button("저장"){render(false)},LinearLayout.LayoutParams(dp(68),dp(50)))
        bottom.addView(button("인스타 스토리로 공유"){render(true)}.apply{background=rounded(Color.rgb(53,65,39));setTextColor(Color.rgb(216,255,171))},LinearLayout.LayoutParams(0,dp(50),1f))
        root.addView(bottom);setContentView(root)
    }
    private fun safe(action:()->Unit){try{action()}catch(e:Exception){AlertDialog.Builder(this).setTitle("다시 시도해 주세요").setMessage(e.message?:"파일을 열 수 없습니다. 원본을 다시 선택해 주세요.").setPositiveButton("확인",null).show()}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun persist(){store.save(project)}
    private fun edit(change:()->Unit){syncTransforms();history.push(project);change();persist();restore()}
    private fun pick(mode:String,types:Array<String>){importMode=mode;picker.launch(types)}
    private fun importUri(uri:Uri) { safe {
        runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        when(importMode){
            "music"->{
                status.text="음악 정보 읽는 중…"
                Thread{runCatching{
                    val cached=prefs.getString("music:$uri",null)
                    if(cached!=null)store.gson.fromJson(cached,Music::class.java) else Audio.metadata(this,uri.toString())
                }.also{result->runOnUiThread{result.onSuccess{m->edit{project.music=m;if(!project.video)project.duration=prefs.getInt("musicDuration",15);ensureMusicLayer()};musicMenu()}.onFailure{toast(it.message?:"지원하지 않는 오디오")}}}}.start()
            }
            "art"->{edit{project.music?.art=uri.toString()};saveMusic();musicMenu()}
            "font"->{
                val f=File(filesDir,"fonts/${System.currentTimeMillis()}.ttf").apply{parentFile!!.mkdirs()}
                contentResolver.openInputStream(uri)!!.use{i->f.outputStream().use{i.copyTo(it)}}
                Typeface.createFromFile(f)
                edit{project.layers.firstOrNull{it.id==selected}?.font=f.absolutePath}
            }
            "layer"->edit{project.layers.add(Layer(uri=uri.toString()));selected=project.layers.last().id}
            else->{
                val mime=contentResolver.getType(uri)?:if(uri.toString().endsWith(".mp4"))"video/mp4" else "image/jpeg"
                edit{project.source=uri.toString();project.video=mime.startsWith("video");project.crop=Crop();if(project.video){
                    val r=MediaMetadataRetriever();try{r.setDataSource(this,uri);project.sourceDurationMs=(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:5000).coerceAtMost(60000)}finally{r.release()}
                }}
            }
        }
    }}
    private fun camera(){ choices("카메라",listOf("사진 촬영","영상 촬영")){i->
        if(i==0){val f=File(filesDir,"imports/capture-${System.currentTimeMillis()}.jpg").apply{parentFile!!.mkdirs()};cameraUri=androidx.core.content.FileProvider.getUriForFile(this,"$packageName.files",f);capturePhoto.launch(cameraUri!!)}
        else captureVideo.launch(Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_DURATION_LIMIT,60).putExtra(MediaStore.EXTRA_VIDEO_QUALITY,1))
    }}
    private fun photoMenu()=choices("사진",listOf("사진 레이어 추가","배경 교체","카메라","배경 자르기")){when(it){0->pick("layer",arrayOf("image/*"));1->pick("base",arrayOf("image/*","video/*"));2->camera();3->crop(null)}}
    private fun restore(){
        if(!::editor.isInitialized)return
        empty.visibility=if(project.source.isBlank())View.VISIBLE else View.GONE
        live.style=project.style.copy();live.durationMs=project.durationMs()
        status.text=if(project.source.isBlank())"사진을 선택해 시작" else "${project.style.name}  ·  ${if(project.animated())"${project.durationMs()/1000}초 MP4" else "정지 사진"}"
        val generation=++backgroundGeneration
        if(project.source.isNotBlank()){
            val snapshot=store.copy(project)
            backgroundUpdate?.let{handler.removeCallbacks(it)}
            backgroundUpdate=Runnable{backgroundWorker.execute{
                if(generation==backgroundGeneration)runCatching{Artwork.background(this,snapshot,540)}.also{r->runOnUiThread{if(generation==backgroundGeneration)r.onSuccess{live.setPhoto(it)}.onFailure{status.text="원본 접근 불가 · 사진에서 다시 선택";toast("원본이 삭제되었거나 접근 권한이 없습니다")}}}
            }}
            handler.postDelayed(backgroundUpdate!!,80)
        }
        restoreLayers();contextTools()
    }
    private fun restoreLayers(){
        if(editor.width<=0)return
        rebuilding=true;engine.clearAllViews();layerViews.clear()
        project.layers.forEach{l->runCatching{
            val original=Artwork.layer(this,l,project.music)
            val w=(l.width*editor.width).toInt().coerceIn(8,editor.width*2);val h=(w.toFloat()/original.width*original.height).toInt().coerceAtLeast(1)
            engine.addImage(Bitmap.createScaledBitmap(original,w,h,true))
            val v=editor.getChildAt(editor.childCount-1);layerViews[l.id]=v
            v.post{
                v.pivotX=v.width/2f;v.pivotY=v.height/2f;v.translationX=l.x*editor.width-v.left-v.width/2f;v.translationY=l.y*editor.height-v.top-v.height/2f
                v.scaleX=l.scale;v.scaleY=l.scale;v.rotation=l.rotation
                v.findViewById<View>(ja.burhanrashid52.photoeditor.R.id.imgPhotoEditorClose)?.setOnClickListener{edit{project.layers.removeAll{it.id==l.id};selected=null}}
            }
        }.onFailure{status.text="일부 레이어 접근 불가 · 선택 후 다시 가져오세요"}}
        editor.post{engine.clearHelperBox();rebuilding=false}
    }
    private fun syncTransforms(){
        if(rebuilding || !::editor.isInitialized || editor.width==0)return
        project.layers.forEach{l->layerViews[l.id]?.let{v->
            val center=floatArrayOf(v.width/2f,v.height/2f);v.matrix.mapPoints(center)
            l.x=(v.left+center[0])/editor.width;l.y=(v.top+center[1])/editor.height;l.scale=v.scaleX;l.rotation=v.rotation
        }}
        project.layers.sortBy{layerViews[it.id]?.let{v->editor.indexOfChild(v)}?:0}
    }
    private fun snap(v:View){
        val c=floatArrayOf(v.width/2f,v.height/2f);v.matrix.mapPoints(c)
        if(abs(v.left+c[0]-editor.width/2)<dp(8))v.translationX+=editor.width/2-v.left-c[0]
        if(abs(v.top+c[1]-editor.height/2)<dp(8))v.translationY+=editor.height/2-v.top-c[1]
        if(abs(v.rotation%90)<3)v.rotation=(v.rotation/90).roundToInt()*90f
    }
    private fun contextTools(){
        contextBar.removeAllViews();val l=project.layers.firstOrNull{it.id==selected}
        if(l==null){contextBar.addView(button("자르기"){crop(null)});contextBar.addView(button("움직임 ${if(project.style.motion)"ON" else "OFF"}"){edit{project.style.motion=!project.style.motion}});contextBar.addView(button("미리보기"){preview()});return}
        contextBar.addView(button("선택 해제"){selected=null;engine.clearHelperBox();contextTools()})
        if(l.kind=="photo")contextBar.addView(button("자르기"){crop(l)})
        if(l.kind=="text")contextBar.addView(button("수정"){textEditor(l)})
        contextBar.addView(button("복제"){edit{val copy=store.gson.fromJson(store.gson.toJson(l),Layer::class.java);copy.id=java.util.UUID.randomUUID().toString();copy.x=(copy.x+.03f).coerceAtMost(.95f);project.layers.add(copy);selected=copy.id}})
        contextBar.addView(button("앞으로"){edit{project.layers.remove(l);project.layers.add(l)}})
        contextBar.addView(button("뒤로"){edit{project.layers.remove(l);project.layers.add(0,l)}})
        contextBar.addView(button("미세 모션 ${if(l.motion)"ON" else "OFF"}"){edit{l.motion=!l.motion}})
        contextBar.addView(button("삭제"){edit{project.layers.remove(l);selected=null}})
    }
    private fun choices(title:String,items:List<String>,onSelect:(Int)->Unit){AlertDialog.Builder(this).setTitle(title).setItems(items.toTypedArray()){_,i->safe{onSelect(i)}}.setNegativeButton("닫기",null).show()}
    private fun sheet(title:String,body:View,onDone:(()->Unit)?=null):AlertDialog {
        val scroll=ScrollView(this).apply{addView(body)}
        return AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("완료"){_,_->onDone?.invoke()}.create().also{it.show()}
    }
    private fun slider(box:LinearLayout,label:String,value:Float,onChange:(Float)->Unit){
        val title=text(label,13f);box.addView(title)
        box.addView(SeekBar(this).apply{max=100;progress=(value*100).toInt().coerceIn(0,100);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){history.push(project)}
            override fun onStopTrackingTouch(s:SeekBar){persist()}
            override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user)onChange(p/100f)}
        })},LinearLayout.LayoutParams(-1,dp(48)))
    }
    private fun styles(){
        val box=column();val r=row()
        listOf("Simple","Grainy","Gritty","Lores","Handheld","Zoomblur","Flow Blur").forEach{n->r.addView(button(n){edit{project.style.name=n}})}
        box.addView(scrollRow(r));slider(box,"강도",project.style.amount){project.style.amount=it;live.style=project.style.copy()}
        box.addView(Switch(this).apply{text="움직임";isChecked=project.style.motion;setOnCheckedChangeListener{_,v->edit{project.style.motion=v}}})
        box.addView(button("세부 설정"){details()});box.addView(button("프리셋"){presets()});sheet("사진 스타일",box){persist()}
    }
    private fun details(){val box=column()
        slider(box,"배경 블러",project.style.blur){project.style.blur=it;restore()}
        slider(box,"배경 밝기",project.style.brightness){project.style.brightness=it;restore()}
        slider(box,"배경 색감 · 차갑게 ↔ 따뜻하게",(project.style.warmth+1)/2){project.style.warmth=it*2-1;restore()}
        slider(box,"가장자리 적용 범위",project.style.scope){project.style.scope=it;live.style=project.style.copy()}
        slider(box,"루프 속도",(project.style.speed-1)/2){project.style.speed=(1+it*2).roundToInt().toFloat();live.style=project.style.copy()}
        slider(box,"Beat Glow · 기본 OFF",project.style.beat){project.style.beat=it;live.style=project.style.copy()}
        if(!project.video)slider(box,"사진 영상 길이 5–15초",(project.duration-5)/10f){project.duration=5+(it*10).roundToInt();prefs.edit().putInt("musicDuration",project.duration).apply();live.durationMs=project.durationMs();status.text="${project.duration}초"}
        sheet("세부 설정",box){persist();restore()}
    }
    private fun crop(layer:Layer?){
        if(project.source.isBlank() && layer==null){toast("사진을 먼저 선택해 주세요");return}
        if(project.video && layer==null){toast("영상 크롭은 회전·확대·반전을 지원합니다")}
        val current=layer?.crop?:project.crop;val draft=current.copy();val box=column()
        val raw=if(layer!=null)Artwork.decode(this,layer.uri,1080) else if(project.video)Artwork.videoFrame(this,project.source) else Artwork.decode(this,project.source,1080)
        val image=CropPreview(this,raw,draft)
        box.addView(image,LinearLayout.LayoutParams(-1,dp(230)))
        box.addView(text("드래그로 위치 · 핀치로 확대",12f))
        fun update(){image.invalidate()}
        val ratios=row();listOf("자유" to 0f,"1:1" to 1f,"4:5" to .8f,"9:16" to 9f/16).forEach{(n,a)->ratios.addView(button(n){draft.aspect=a;update()})};box.addView(scrollRow(ratios))
        slider(box,"확대",(draft.zoom-1)/5){draft.zoom=1+it*5;update()}
        slider(box,"가로 위치",draft.x){draft.x=it;update()};slider(box,"세로 위치",draft.y){draft.y=it;update()}
        slider(box,"자유 비율",(if(draft.aspect>0)draft.aspect else 1f)/2){draft.aspect=(it*2).coerceAtLeast(.25f);update()}
        val actions=row();actions.addView(button("90° 회전"){draft.rotation=(draft.rotation+90)%360;update()});actions.addView(button("좌우 반전"){draft.flip=!draft.flip;update()});actions.addView(button("원본"){draft.x=.5f;draft.y=.5f;draft.zoom=1f;draft.aspect=0f;draft.rotation=0f;draft.flip=false;update()});box.addView(actions)
        update();sheet("자르기 · 원본 보존",box){edit{if(layer==null)project.crop=draft else layer.crop=draft}}
    }
    private fun textEditor(existing:Layer?){
        val l=existing?.copy()?:Layer(kind="text",text="",width=.65f)
        val box=column();val input=EditText(this).apply{hint="문장을 입력하세요";setText(l.text);setTextColor(Color.WHITE);minLines=2;maxLines=6};box.addView(input)
        val styleRow=row();listOf("기본","Soft Label","Offset Type","Whisper","Drift Type","Echo Type").forEach{n->styleRow.addView(button(n){l.type=n;l.motion=n=="Drift Type";toast(n)})};box.addView(scrollRow(styleRow))
        val colors=row();listOf(Color.WHITE,Color.BLACK,0xFFD4FF83.toInt(),0xFFFFC5D6.toInt(),0xFF9DCEFF.toInt()).forEach{color->colors.addView(button("●"){l.color=color}.apply{setTextColor(color)})};box.addView(colors)
        val align=row();listOf("왼쪽","가운데","오른쪽").forEachIndexed{i,n->align.addView(button(n){l.align=i})};box.addView(align)
        slider(box,"크기",l.width){l.width=it.coerceIn(.1f,1f)};slider(box,"표현 강도",l.emphasis){l.emphasis=it}
        sheet("텍스트",box){if(input.text.isNotBlank())edit{l.text=input.text.toString();if(existing!=null)project.layers[project.layers.indexOf(existing)]=l else project.layers.add(l);selected=l.id}}
    }
    private fun emojiLibrary(){
        val path=prefs.getString("emojiFolder",null)
        if(path==null){val box=column();box.addView(text("내 PNG를 이모지처럼\n원하는 폴더를 선택하세요.\n예: Pictures/igstoryplus/emoji"));box.addView(button("폴더 선택"){folderPicker.launch(null)});sheet("내 이모지",box);return}
        val box=column();val actions=row();var dialog:AlertDialog?=null
        actions.addView(button("새로고침"){dialog?.dismiss();emojiLibrary()});actions.addView(button("폴더 변경"){dialog?.dismiss();folderPicker.launch(null)});box.addView(actions)
        val grid=androidx.recyclerview.widget.RecyclerView(this).apply{layoutManager=androidx.recyclerview.widget.GridLayoutManager(this@MainActivity,4)};box.addView(grid,LinearLayout.LayoutParams(-1,dp(380)))
        val loading=text("PNG 불러오는 중…");box.addView(loading)
        dialog=sheet("내 이모지 · 길게 눌러 즐겨찾기",box)
        Thread{
            val result=runCatching { EmojiFolder.scan(this,Uri.parse(path)) }
            runOnUiThread{result.onSuccess{files->
                val fav=prefs.getStringSet("favorites",emptySet())?:emptySet();val recent=prefs.getString("recent","")!!.split("|")
                val sorted=files.sortedWith(compareByDescending<DocumentFile>{it.uri.toString() in fav}.thenBy{recent.indexOf(it.uri.toString()).let{i->if(i<0)999 else i}}.thenBy{it.name})
                loading.text=if(files.isEmpty())"이 폴더에 PNG를 넣으면 여기에 표시됩니다" else "${files.size}개 · ★ 즐겨찾기 우선"
                grid.adapter=EmojiAdapter(sorted,fav,{f->val uri=f.uri.toString();dialog?.dismiss();safe{edit{project.layers.add(Layer(kind="png",uri=uri,width=.089f));selected=project.layers.last().id};prefs.edit().putString("recent",(listOf(uri)+recent.filter{it!=uri}).take(30).joinToString("|")).apply()}},
                    {f->val uri=f.uri.toString();val favorites=(prefs.getStringSet("favorites",emptySet())?:emptySet()).toMutableSet();if(!favorites.add(uri))favorites.remove(uri);prefs.edit().putStringSet("favorites",favorites).apply();dialog?.dismiss();emojiLibrary()})
            }.onFailure{loading.text="폴더가 없거나 권한이 만료되었습니다. 폴더를 다시 선택해 주세요."}}
        }.start()
    }
    private fun ensureMusicLayer(){
        val music=project.music?:return
        if(music.card=="숨김")project.layers.removeAll{it.kind=="music"}
        else if(project.layers.none{it.kind=="music"})project.layers.add(Layer(kind="music",width=.55f,y=.8f))
    }
    private fun saveMusic(){project.music?.let{prefs.edit().putString("music:${it.uri}",store.gson.toJson(it)).apply()};persist()}
    private fun musicMenu(){
        val m=project.music
        if(m==null){pick("music",arrayOf("audio/*"));return}
        val box=column()
        val title=EditText(this).apply{setText(m.title);hint="곡명"};box.addView(title)
        val artist=EditText(this).apply{setText(m.artist);hint="아티스트"};box.addView(artist)
        val album=EditText(this).apply{setText(m.album);hint="앨범"};box.addView(album)
        box.addView(text("${m.durationMs/1000}초 · MP4에 음악이 합성됩니다",12f))
        val position=text("시작 ${m.startMs/1000}초");box.addView(position)
        slider(box,"시작 위치",m.startMs.toFloat()/max(1L,m.durationMs-1000)){m.startMs=(it*max(0L,m.durationMs-1000)).toLong();position.text="시작 ${m.startMs/1000}초";saveMusic()}
        if(!project.video){val length=text("선택 길이 ${project.duration}초");box.addView(length);slider(box,"5–15초 · 1초 단위",(project.duration-5)/10f){project.duration=5+(it*10).roundToInt();length.text="선택 길이 ${project.duration}초";prefs.edit().putInt("musicDuration",project.duration).apply();persist()}}
        else box.addView(text("영상 길이 ${project.durationMs()/1000}초 유지",12f))
        val cards=row();listOf("작은 카드","앨범 아트","텍스트","숨김").forEach{n->cards.addView(button(n){edit{m.card=n;ensureMusicLayer()};saveMusic()})};box.addView(scrollRow(cards))
        box.addView(button("앨범 아트 선택"){pick("art",arrayOf("image/*"))})
        box.addView(button("선택 구간 미리듣기 / 정지"){
            if(audioPlayer!=null)stopAudio() else {
                audioPlayer=MediaPlayer().apply{setDataSource(this@MainActivity,Uri.parse(m.uri));setOnPreparedListener{it.seekTo(m.startMs,MediaPlayer.SEEK_CLOSEST);it.start();handler.postDelayed({stopAudio()},project.durationMs())};setOnCompletionListener{stopAudio()};setOnErrorListener{_,_,_->stopAudio();toast("재생할 수 없는 파일");true};prepareAsync()}
            }
        })
        box.addView(button("다른 음악"){pick("music",arrayOf("audio/*"))})
        box.addView(button("음악 삭제"){stopAudio();edit{project.music=null;project.layers.removeAll{it.kind=="music"}}})
        sheet("음악",box){stopAudio();edit{m.title=title.text.toString();m.artist=artist.text.toString();m.album=album.text.toString()};saveMusic()}
    }
    private fun stopAudio(){audioPlayer?.release();audioPlayer=null}
    private fun presets(){
        val items=store.presets();choices("프리셋",listOf("현재 설정 저장")+items.map{it.name}){i->
            if(i==0)nameDialog("프리셋 이름",""){name->items.add(Preset(name,project.style.copy(),project.layers.firstOrNull{it.kind=="text"}?.type?:"Soft Label",project.music?.card?:"작은 카드",project.layers.firstOrNull{it.kind=="text"}?.copy(),project.layers.firstOrNull{it.kind=="music"}?.motion?:false,project.duration));store.savePresets(items)}
            else {val preset=items[i-1];choices(preset.name,listOf("적용","이름 변경","삭제")){a->when(a){
                0->edit{project.style=preset.style.copy();project.layers.filter{it.kind=="text"}.forEach{l->
                    l.type=preset.text;preset.template?.let{t->l.font=t.font;l.color=t.color;l.align=t.align;l.emphasis=t.emphasis;l.width=t.width;l.motion=t.motion}
                };project.music?.card=preset.card;ensureMusicLayer();project.layers.filter{it.kind=="music"}.forEach{it.motion=preset.musicMotion};if(!project.video)project.duration=preset.duration.coerceIn(5,15)}
                1->nameDialog("이름 변경",preset.name){preset.name=it;store.savePresets(items)}
                2->{items.remove(preset);store.savePresets(items)}
            }}}
        }
    }
    private fun nameDialog(title:String,value:String,onSave:(String)->Unit){val input=EditText(this).apply{setText(value)};AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("저장"){_,_->if(input.text.isNotBlank())onSave(input.text.toString())}.setNegativeButton("취소",null).show()}
    private fun more(){choices("더보기",listOf("사진·영상 선택","카메라","프리셋","세부 설정 / 영상 길이","레이어 목록","사용자 폰트 가져오기","새 스토리","오픈소스 라이선스")){when(it){
        0->pick("base",arrayOf("image/*","video/*"));1->camera();2->presets();3->details();4->choices("레이어",project.layers.map{if(it.kind=="text")it.text else it.kind}){i->selected=project.layers[i].id;contextTools()}
        5->{if(project.layers.none{it.id==selected && it.kind=="text"})toast("텍스트 레이어를 먼저 선택하세요") else pick("font",arrayOf("font/*","application/x-font-ttf","application/octet-stream"))}
        6->AlertDialog.Builder(this).setMessage("새 스토리를 시작할까요? 현재 초안은 교체됩니다.").setPositiveButton("새로 만들기"){_,_->edit{project=Project()}}.setNegativeButton("취소",null).show()
        7->AlertDialog.Builder(this).setTitle("오픈소스").setMessage(listOf("THIRD_PARTY_NOTICES.txt","Apache-2.0","Glide-LICENSE").joinToString("\n\n"){name->assets.open(name).bufferedReader().use{it.readText()}}).setPositiveButton("확인",null).show()
    }}}
    private fun render(share:Boolean){
        if(project.source.isBlank()){toast("사진 또는 영상을 선택해 주세요");return}
        syncTransforms();persist();stopAudio();engine.clearHelperBox()
        val snapshot=store.copy(project);val exporter=Exporter(this);activeExport=exporter
        val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100}
        val dialog=AlertDialog.Builder(this).setTitle("스토리 만드는 중").setView(progress).setNegativeButton("취소"){_,_->exporter.cancel();activeExport=null;toast("취소했습니다. 초안은 보존됩니다")}.setCancelable(false).show()
        exporter.export(snapshot,{progress.progress=it}){result->dialog.dismiss();activeExport=null
            result.onSuccess{f->latest=f;if(share)share(f) else safe{Sharing.gallery(this,f);toast("갤러리에 저장했습니다")}}
                .onFailure{e->AlertDialog.Builder(this).setTitle("출력 실패").setMessage("${e.message}\n초안은 보존되었습니다.").setPositiveButton("다시 시도"){_,_->render(share)}.setNegativeButton("닫기",null).show()}
        }
    }
    private fun share(file:File){
        try{val intent=Sharing.intent(this,file,true);if(intent.resolveActivity(packageManager)==null)throw android.content.ActivityNotFoundException();grantUriPermission("com.instagram.android",Sharing.uri(this,file),Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);toast("Instagram에서 후처리하고 게시하세요")}
        catch(e:Exception){choices("Instagram 전용 공유를 열 수 없습니다",listOf("일반 공유","갤러리에 저장")){i->if(i==0)startActivity(Intent.createChooser(Sharing.intent(this,file,false),"스토리 공유")) else {Sharing.gallery(this,file);toast("갤러리에 저장했습니다. Instagram에서 선택하세요")}}}
    }
    private fun preview(){
        if(project.source.isBlank())return
        syncTransforms();engine.clearHelperBox()
        // Exact export preview also covers source video, audio, motion typography and Beat Glow.
        val exporter=Exporter(this);activeExport=exporter
        val wait=AlertDialog.Builder(this).setMessage("미리보기 준비 중…").setNegativeButton("취소"){_,_->exporter.cancel();activeExport=null}.setCancelable(false).show()
        exporter.export(store.copy(project),{}){r->wait.dismiss();activeExport=null;r.onSuccess{f->
            if(f.extension=="jpg"){
                val image=ImageView(this).apply{setImageURI(Uri.fromFile(f));adjustViewBounds=true};AlertDialog.Builder(this).setView(image).setPositiveButton("닫기",null).create().apply{setOnDismissListener{f.delete()};show()}
            }else{
                val player=ExoPlayer.Builder(this).build();previewPlayer=player
                val view=PlayerView(this).apply{this.player=player;layoutParams=ViewGroup.LayoutParams(-1,dp(440))}
                val d=AlertDialog.Builder(this).setView(view).setPositiveButton("닫기",null).create();d.setOnDismissListener{player.release();previewPlayer=null;f.delete()};d.show()
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)));player.repeatMode=androidx.media3.common.Player.REPEAT_MODE_ONE;player.prepare();player.play()
            }
        }.onFailure{toast("미리보기 실패: ${it.message}")}}
    }
    override fun onPause(){super.onPause();syncTransforms();persist();stopAudio();live.onPause();previewPlayer?.pause()}
    override fun onResume(){super.onResume();if(::live.isInitialized)live.onResume()}
    override fun onDestroy(){backgroundUpdate?.let{handler.removeCallbacks(it)};backgroundWorker.shutdownNow();activeExport?.cancel();stopAudio();previewPlayer?.release();super.onDestroy()}
}
