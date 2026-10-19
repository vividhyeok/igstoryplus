package com.vividhyeok.igstoryplus

import android.app.AlertDialog
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
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
    private lateinit var chrome: EditorChrome
    private var textSession: CanvasTextEditor?=null
    private var cropSession: View?=null
    private var gestureBefore:Project?=null
    private var dragging=false
    private var selected: String?=null
    private var foregroundBounds=RectF(0f,0f,1f,1f)
    private val layerViews=linkedMapOf<String,View>()
    private val layerReferenceWidth=mutableMapOf<String,Int>()
    private var rebuilding=false
    private var importMode="base"
    private var cameraUri: Uri?=null
    private var activeExport: Exporter?=null
    private val handler=Handler(Looper.getMainLooper())
    private var backgroundGeneration=0
    private val backgroundWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var backgroundUpdate:Runnable?=null
    private lateinit var guides: AlignmentGuides
    private var editingPlayer:ExoPlayer?=null
    private lateinit var videoView:PlayerView
    private var playingSource=""
    private var latest: File?=null
    private var previewMode=false
    private var previewControls:View?=null
    private val openSheets=mutableListOf<android.app.Dialog>()
    private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    private val clock by lazy { PreviewClock(this,{project},live,{editingPlayer},{layerViews}){playing->chrome.playback(playing)} }
    private val musicPanels by lazy { MusicPanels(this,{project},clock,{syncTransforms();history.push(project)},{ensureMusicLayer();restoreLayers();contextTools()},{saveMusic()},{mode->pick(mode,if(mode=="music")arrayOf("audio/*") else arrayOf("image/*"))},{m->selectMusic(m)},{stopAudio();edit{project.music=null;project.layers.removeAll{it.kind=="music"}}}) }
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
        super.onCreate(savedInstanceState);androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);store=Store(this);project=store.load();buildUi()
        cameraUri=savedInstanceState?.getString("cameraUri")?.let{Uri.parse(it)}
        importMode=savedInstanceState?.getString("importMode")?:"base"
        Thread{
            val cutoff=System.currentTimeMillis()-2*24*60*60*1000L
            File(cacheDir,".").listFiles()?.filter{it.name.startsWith("render-source-") && it.lastModified()<cutoff}?.forEach{it.delete()}
        }.start()
        canvasBox.post { restore();store.textEdit()?.let{pending->textEditor(project.layers.firstOrNull{it.id==pending.existingId},pending.layer)} }
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){override fun handleOnBackPressed(){
            when { textSession!=null->textSession?.finish(true);cropSession!=null->closeCrop();previewMode->leavePreview();selected!=null->{selected=null;engine.clearHelperBox();contextTools()};else->{syncTransforms();persist();finish()} }
        }})
    }
    override fun onSaveInstanceState(outState:Bundle){outState.putString("cameraUri",cameraUri?.toString());outState.putString("importMode",importMode);super.onSaveInstanceState(outState)}
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun rounded(color: Int,radius: Float=16f)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius.toInt()).toFloat()}
    private fun text(value: String,size: Float=14f)=TextView(this).apply{text=value;setTextColor(Color.WHITE);textSize=size;gravity=Gravity.CENTER;setPadding(dp(8),dp(6),dp(8),dp(6))}
    private fun button(label: String,action:()->Unit)=text(label).apply{minimumHeight=dp(48);minWidth=dp(48);contentDescription=label;isClickable=true;isFocusable=true;setOnClickListener{safe(action)}}
    private fun toolButton(label:String,action:()->Unit)=button(label,action).apply{
        textSize=10f;minWidth=dp(56);setPadding(dp(6),dp(3),dp(6),dp(3))
        setCompoundDrawables(null,EditorIcons.drawable(label).apply{setBounds(0,0,dp(23),dp(23))},null,null);compoundDrawablePadding=dp(2)
    }
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(10),dp(18),dp(10))}
    private fun scrollRow(r:LinearLayout)=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(r)}
    private fun buildUi() {
        chrome=EditorChrome(this)
        val root=chrome
        ViewCompat.setOnApplyWindowInsetsListener(root){v,i->
            val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=i.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(b.left,b.top,b.right,max(b.bottom,ime.bottom));i
        }
        normalTop()
        val center=chrome.stage
        canvasBox=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);clipChildren=true}
        center.addView(canvasBox,0,FrameLayout.LayoutParams(1,1,Gravity.CENTER))
        chrome.play.setOnClickListener{preview()}
        live=LivePhoto(this); canvasBox.addView(live,FrameLayout.LayoutParams(-1,-1))
        videoView=layoutInflater.inflate(R.layout.video_preview,canvasBox,false) as PlayerView
        videoView.visibility=View.GONE;canvasBox.addView(videoView,FrameLayout.LayoutParams(-1,-1))
        editor=PhotoEditorView(this).apply{setBackgroundColor(Color.TRANSPARENT);clipChildren=true}
        canvasBox.addView(editor,FrameLayout.LayoutParams(-1,-1))
        guides=AlignmentGuides(this);canvasBox.addView(guides,FrameLayout.LayoutParams(-1,-1))
        editor.source.setImageBitmap(Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888))
        editor.source.contentDescription="배경 사진"
        engine=PhotoEditor.Builder(this,editor).setPinchTextScalable(true).build()
        editor.onLayerGesture={v,start->
            if(!rebuilding && textSession==null && cropSession==null){
                if(start){stopPreview();gestureBefore=store.copy(project);dragging=true;selected=layerViews.entries.firstOrNull{it.value===v}?.key;contextTools()}
                else { guides.clear();centerPivot(v);snap(v);syncTransforms();dragging=false;gestureBefore?.let{if(store.gson.toJson(it)!=store.gson.toJson(project))history.push(it)};gestureBefore=null;persist() }
            }
        }
        editor.onLayerTransform={v->guides.follow(v,editor)}
        editor.onLayerDoubleTap={v->project.layers.firstOrNull{layerViews[it.id]===v && it.kind=="text"}?.let{textEditor(it)}}
        editor.source.setOnTouchListener(BackgroundGestures(editor.source,{project},{foregroundBounds},
            {stopPreview();gestureBefore=store.copy(project);selected="background";engine.clearHelperBox();contextTools()},
            {live.pose=project.pose.copy()},
            {gestureBefore?.let{history.push(it)};gestureBefore=null;persist()},
            {hit->if(previewMode)return@BackgroundGestures;selected=if(hit && selected!="background")"background" else null;engine.clearHelperBox();contextTools()},
            {if(!previewMode)crop(null)}))
        center.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
            val h=center.height;val w=min(center.width,(h*9f/16).toInt()); val ch=(w*16f/9).toInt()
            if(w>0 && (canvasBox.width!=w || canvasBox.height!=ch)){canvasBox.layoutParams=FrameLayout.LayoutParams(w,ch,Gravity.CENTER);canvasBox.post{restoreLayers()}}
        }
        empty=column().apply{gravity=Gravity.CENTER;addView(text("오늘의 한 장에\n작은 움직임을.",27f));addView(text("사진을 고르고 바로 시작하세요",14f));addView(button("사진 · 영상 선택"){pick("base",arrayOf("image/*","video/*"))}.apply{background=rounded(Color.rgb(53,57,47))});addView(button("카메라"){camera()})}
        canvasBox.addView(empty,FrameLayout.LayoutParams(-1,-1))
        empty.setBackgroundColor(Color.BLACK)
        status=text("").apply{visibility=View.GONE}
        contextBar=chrome.bottom
        contextBar.setOnHierarchyChangeListener(object:ViewGroup.OnHierarchyChangeListener{
            override fun onChildViewRemoved(parent:View?,child:View?){}
            override fun onChildViewAdded(parent:View?,child:View?){
                if(child is TextView && child.contentDescription!="인스타 스토리로 공유"){
                    val name=child.text.toString();val icon=when(name){"⋯"->"더보기";"×"->"선택 해제";"음악 구간"->"음악 구간";else->name}
                    if(name in listOf("⋯","×","사진 추가","스타일","저장","자르기","교체","수정","글꼴","색상","정렬","복제","삭제","음악 구간","카드","볼륨")){
                        child.textSize=10f;child.minWidth=dp(52);child.setPadding(dp(6),dp(3),dp(6),dp(3));child.setCompoundDrawables(null,EditorIcons.drawable(icon).apply{setBounds(0,0,dp(23),dp(23))},null,null);child.compoundDrawablePadding=dp(2)
                    }
                }
            }
        })
        setContentView(root)
    }
    private fun normalTop(){
        chrome.top.removeAllViews()
        fun tool(g:String,n:String,a:()->Unit){chrome.top.addView(chrome.tool(g,n){safe(a)},LinearLayout.LayoutParams(0,-1,1f))}
        tool("×","닫기"){onBackPressedDispatcher.onBackPressed()}
        tool("↶","실행 취소"){stopPreview();history.undo(project)?.let{project=it;restore()}}
        tool("↷","다시 실행"){stopPreview();history.redo(project)?.let{project=it;restore()}}
        tool("Aa","텍스트"){textEditor(null)}
        tool("▧","내 이모지"){emojiLibrary()}
        tool("♫","음악"){musicMenu()}
        tool("⋯","더보기"){more()}
    }
    private fun safe(action:()->Unit){try{action()}catch(e:Exception){AlertDialog.Builder(this).setTitle("다시 시도해 주세요").setMessage(e.message?:"파일을 열 수 없습니다. 원본을 다시 선택해 주세요.").setPositiveButton("확인",null).show()}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun persist(){store.save(project)}
    private fun edit(change:()->Unit){syncTransforms();history.push(project);change();persist();restore()}
    private fun pick(mode:String,types:Array<String>){importMode=mode;picker.launch(types)}
    private fun importUri(uri:Uri) { safe {
        runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        if(importMode.startsWith("replace:")){edit{project.layers.firstOrNull{it.id==importMode.removePrefix("replace:")}?.let{it.uri=uri.toString();it.crop=Crop()}};return@safe}
        when(importMode){
            "music"->{
                status.text="음악 정보 읽는 중…"
                Thread{runCatching{
                    val cached=prefs.getString("music:$uri",null)
                    if(cached!=null)store.gson.fromJson(cached,Music::class.java) else Audio.metadata(this,uri.toString())
                }.also{result->runOnUiThread{result.onSuccess{m->selectMusic(m)}.onFailure{toast(it.message?:"지원하지 않는 오디오")}}}}.start()
            }
            "art"->{edit{project.music?.art=uri.toString()};saveMusic();musicMenu()}
            "font", "font-edit"->{
                val f=File(filesDir,"fonts/${System.currentTimeMillis()}.ttf").apply{parentFile!!.mkdirs()}
                contentResolver.openInputStream(uri)!!.use{i->f.outputStream().use{i.copyTo(it)}}
                Typeface.createFromFile(f)
                if(importMode=="font-edit")textSession?.let{it.draft.font=f.absolutePath;it.refresh()} else edit{project.layers.firstOrNull{it.id==selected}?.font=f.absolutePath}
            }
            "layer"->edit{project.layers.add(Layer(uri=uri.toString()));selected=project.layers.last().id}
            else->{
                val mime=contentResolver.getType(uri)?:if(uri.toString().endsWith(".mp4"))"video/mp4" else "image/jpeg"
                edit{project.source=uri.toString();project.video=mime.startsWith("video");project.crop=Crop();project.pose=PhotoPose();if(project.video){
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
        live.pose=project.pose.copy()
        if(project.video && project.source.isNotBlank())runCatching{
            videoView.visibility=View.VISIBLE
            val player=editingPlayer?:ExoPlayer.Builder(this).build().also{editingPlayer=it;videoView.player=it;it.volume=0f;it.repeatMode=androidx.media3.common.Player.REPEAT_MODE_ONE}
            if(playingSource!=project.source){playingSource=project.source;player.setMediaItem(MediaItem.Builder().setUri(project.source).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(project.durationMs()).build()).build());player.prepare()}
            updateLiveStyle();if(clock.playing)player.play() else player.pause()
        }.onFailure{videoView.visibility=View.GONE;status.text="영상 미리보기를 열 수 없습니다"}
        else{videoView.visibility=View.GONE;editingPlayer?.release();editingPlayer=null;playingSource=""}
        status.text=if(project.source.isBlank())"사진을 선택해 시작" else "${project.style.name}  ·  ${if(project.animated())"${project.durationMs()/1000}초 MP4" else "정지 사진"}"
        val generation=++backgroundGeneration
        if(project.source.isNotBlank()){
            val snapshot=store.copy(project)
            backgroundUpdate?.let{handler.removeCallbacks(it)}
            backgroundUpdate=Runnable{backgroundWorker.execute{
                var bounds=foregroundBounds
                if(generation==backgroundGeneration)runCatching{
                    val base=Artwork.background(this,snapshot,540,drawPhoto=false){bounds=it}
                    val photo=if(snapshot.video)null else Artwork.cropped(Artwork.decode(this,snapshot.source,1536),snapshot.crop)
                    base to photo
                }.also{r->runOnUiThread{if(generation==backgroundGeneration)r.onSuccess{foregroundBounds=bounds;live.setPhoto(it.first,it.second)}.onFailure{status.text="원본 접근 불가 · 사진에서 다시 선택";toast("원본이 삭제되었거나 접근 권한이 없습니다")}}}
            }}
            handler.postDelayed(backgroundUpdate!!,80)
        }
        restoreLayers();contextTools()
    }
    private fun updateLiveStyle(){clock.updateStyle()}
    private fun restoreLayers(){
        if(editor.width<=0)return
        if(textSession!=null){
            project.layers.forEach{l->layerViews[l.id]?.let{v->v.translationX=l.x*editor.width-v.left-v.width/2f;v.translationY=l.y*editor.height-v.top-v.height/2f;v.scaleX=l.scale*editor.width/(layerReferenceWidth[l.id]?:editor.width);v.scaleY=v.scaleX}}
            return
        }
        rebuilding=true;engine.clearAllViews();layerViews.clear();layerReferenceWidth.clear()
        project.layers.forEach{l->runCatching{
            val original=Artwork.layer(this,l,project.music)
            val w=(l.width*editor.width).toInt().coerceIn(8,editor.width*2);val h=(w.toFloat()/original.width*original.height).toInt().coerceAtLeast(1)
            engine.addImage(Bitmap.createScaledBitmap(original,w,h,true))
            val v=editor.getChildAt(editor.childCount-1);layerViews[l.id]=v;layerReferenceWidth[l.id]=editor.width
            v.contentDescription=if(l.kind=="text")"텍스트 레이어: ${l.text}" else "${l.kind} 레이어"
            v.isFocusable=true
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
        stopPreview()
        project.layers.forEach{l->layerViews[l.id]?.let{v->
            val center=floatArrayOf(v.width/2f,v.height/2f);v.matrix.mapPoints(center)
            l.x=(v.left+center[0])/editor.width;l.y=(v.top+center[1])/editor.height;l.scale=v.scaleX*(layerReferenceWidth[l.id]?:editor.width)/editor.width;l.rotation=v.rotation
        }}
        project.layers.sortBy{layerViews[it.id]?.let{v->editor.indexOfChild(v)}?:0}
    }
    private fun snap(v:View){
        val c=floatArrayOf(v.width/2f,v.height/2f);v.matrix.mapPoints(c)
        v.translationX+=(v.left+c[0]).coerceIn(0f,editor.width.toFloat())-v.left-c[0]
        v.translationY+=(v.top+c[1]).coerceIn(0f,editor.height.toFloat())-v.top-c[1]
        if(abs(v.left+c[0]-editor.width/2)<dp(8))v.translationX+=editor.width/2-v.left-c[0]
        if(abs(v.top+c[1]-editor.height/2)<dp(8))v.translationY+=editor.height/2-v.top-c[1]
        if(abs(v.rotation%90)<3)v.rotation=(v.rotation/90).roundToInt()*90f
    }
    private fun centerPivot(v:View){
        val center=floatArrayOf(v.width/2f,v.height/2f);v.matrix.mapPoints(center)
        v.pivotX=v.width/2f;v.pivotY=v.height/2f
        v.translationX=center[0]-v.width/2f;v.translationY=center[1]-v.height/2f
    }
    private fun contextTools(){
        contextBar.removeAllViews();val l=project.layers.firstOrNull{it.id==selected}
        if(textSession!=null || cropSession!=null)return
        if(selected=="background"){
            contextBar.addView(button("×"){selected=null;contextTools()}.apply{contentDescription="선택 해제"})
            contextBar.addView(toolButton("자르기"){crop(null)})
            contextBar.addView(toolButton("교체"){pick("base",arrayOf("image/*","video/*"))})
            contextBar.addView(toolButton("스타일"){styles()})
            return
        }
        if(l==null){
            contextBar.addView(toolButton("사진 추가"){if(project.source.isBlank())pick("base",arrayOf("image/*","video/*")) else photoMenu()})
            contextBar.addView(toolButton("스타일"){styles()})
            contextBar.addView(toolButton("저장"){render(false)})
            contextBar.addView(button("스토리로 공유"){render(true)}.apply{contentDescription="인스타 스토리로 공유";textSize=13f;setTextColor(Color.BLACK);setPadding(dp(16),0,dp(16),0);background=rounded(Color.rgb(242,242,239),25f)},LinearLayout.LayoutParams(-2,dp(48)).apply{leftMargin=dp(8)})
            return
        }
        contextBar.addView(button("×"){selected=null;engine.clearHelperBox();contextTools()}.apply{contentDescription="선택 해제"})
        if(l.kind=="photo"){contextBar.addView(button("자르기"){crop(l)});contextBar.addView(button("교체"){pick("replace:${l.id}",arrayOf("image/*"))});contextBar.addView(button("스타일"){styles()})}
        if(l.kind=="text"){
            contextBar.addView(button("수정"){textEditor(l)})
            listOf("글꼴","스타일","색상").forEach{label->contextBar.addView(button(label){textEditor(l);(0 until contextBar.childCount).map{contextBar.getChildAt(it)}.filterIsInstance<TextView>().firstOrNull{it.text==label}?.performClick()})}
        }
        if(l.kind=="png")contextBar.addView(button("크기·위치"){val box=column();slider(box,"크기",l.width){l.width=it.coerceIn(.02f,1f);restoreLayers()};slider(box,"가로 위치",l.x){l.x=it;restoreLayers()};slider(box,"세로 위치",l.y){l.y=it;restoreLayers()};sheet("스티커 조절",box)})
        if(l.kind=="music"){
            contextBar.addView(button("음악 구간"){musicMenu()})
            contextBar.addView(toolButton("카드"){musicPanels.cards()})
            contextBar.addView(button("볼륨"){project.music?.let{m->val box=column();slider(box,"볼륨",m.volume){m.volume=it;clock.setVolume(it);saveMusic()};sheet("음악 볼륨",box)}})
            contextBar.addView(button("카드 숨김"){edit{project.music?.card="숨김";ensureMusicLayer()}})
            contextBar.addView(button("⋯"){layerExtra(l)}.apply{contentDescription="레이어 추가 작업"})
            return
        }
        contextBar.addView(button("복제"){duplicate(l)})
        contextBar.addView(button("삭제"){edit{project.layers.remove(l);selected=null}})
        contextBar.addView(button("⋯"){layerExtra(l)}.apply{contentDescription="레이어 추가 작업"})
    }
    private fun duplicate(l:Layer){edit{val copy=store.gson.fromJson(store.gson.toJson(l),Layer::class.java);copy.id=java.util.UUID.randomUUID().toString();copy.x=(copy.x+.03f).coerceAtMost(.95f);project.layers.add(copy);selected=copy.id}}
    private fun layerExtra(l:Layer){choices("레이어",listOf("앞으로","뒤로","미세 모션 ${if(l.motion)"ON" else "OFF"}","복제")){i->if(i==3)duplicate(l) else edit{when(i){0->{project.layers.remove(l);project.layers.add(l)};1->{project.layers.remove(l);project.layers.add(0,l)};2->l.motion=!l.motion}}}}
    private fun choices(title:String,items:List<String>,onSelect:(Int)->Unit){
        val box=column();var d:android.app.Dialog?=null
        items.forEachIndexed{i,n->box.addView(button(n){d?.dismiss();safe{onSelect(i)}})}
        d=sheet(title,box)
    }
    private fun sheet(title:String,body:View,onDone:(()->Unit)?=null):android.app.Dialog {
        return EditorSheet(this,title,body,onDone).also{dialog->openSheets.add(dialog);dialog.setOnDismissListener{openSheets.remove(dialog)};dialog.show()}
    }
    private fun slider(box:LinearLayout,label:String,value:Float,trackHistory:Boolean=true,onChange:(Float)->Unit){
        val title=text(label,13f);box.addView(title)
        box.addView(SeekBar(this).apply{max=100;progress=(value*100).toInt().coerceIn(0,100);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){if(trackHistory)history.push(project)}
            override fun onStopTrackingTouch(s:SeekBar){persist()}
            override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user)onChange(p/100f)}
        })},LinearLayout.LayoutParams(-1,dp(48)))
    }
    private fun styles(){
        syncTransforms()
        StylePicker(this,{project},{history.push(project)},{updateLiveStyle()},{persist()},{clock.play()},{clock.pausePlayback()},{details()},{presets()}).show()
    }
    private fun details(){val box=column()
        slider(box,"배경 블러",project.style.blur){project.style.blur=it;restore()}
        slider(box,"배경 밝기",project.style.brightness){project.style.brightness=it;restore()}
        slider(box,"배경 색감 · 차갑게 ↔ 따뜻하게",(project.style.warmth+1)/2){project.style.warmth=it*2-1;restore()}
        slider(box,"가장자리 적용 범위",project.style.scope){project.style.scope=it;updateLiveStyle()}
        box.addView(button("효과 영역 · ${project.style.region}"){choices("효과 영역",listOf("전체","배경 중심","가장자리","중심점")){i->history.push(project);project.style.region=listOf("전체","배경 중심","가장자리","중심점")[i];updateLiveStyle();persist();if(i==3)effectCenter()}})
        slider(box,"루프 속도",(project.style.speed-1)/2){project.style.speed=(1+it*2).roundToInt().toFloat();updateLiveStyle()}
        slider(box,"Beat Glow · 기본 OFF",project.style.beat){project.style.beat=it;updateLiveStyle()}
        if(!project.video)slider(box,"사진 영상 길이 5–15초",(project.duration-5)/10f){project.duration=5+(it*10).roundToInt();prefs.edit().putInt("musicDuration",project.duration).apply();live.durationMs=project.durationMs();status.text="${project.duration}초"}
        sheet("세부 설정",box){persist();restore()}
    }
    private fun crop(layer:Layer?){
        if(project.source.isBlank() && layer==null){toast("사진을 먼저 선택해 주세요");return}
        if(project.video && layer==null){toast("영상 크롭은 회전·확대·반전을 지원합니다")}
        stopPreview();syncTransforms()
        chrome.play.visibility=View.GONE
        val current=layer?.crop?:project.crop;val draft=current.copy()
        val raw=if(layer!=null)Artwork.decode(this,layer.uri,1080) else if(project.video)Artwork.videoFrame(this,project.source) else Artwork.decode(this,project.source,1080)
        val image=CropPreview(this,raw,draft)
        cropSession=image;canvasBox.addView(image,FrameLayout.LayoutParams(-1,-1))
        chrome.top.removeAllViews();chrome.top.addView(button("취소"){closeCrop()});chrome.top.addView(text("자르기",16f),LinearLayout.LayoutParams(0,-1,1f));chrome.top.addView(button("완료"){closeCrop();edit{if(layer==null)project.crop=draft else layer.crop=draft}})
        contextBar.removeAllViews()
        fun update(){image.invalidate()}
        listOf("원본" to 0f,"1:1" to 1f,"4:5" to .8f,"9:16" to 9f/16).forEach{(n,a)->contextBar.addView(button(n){draft.aspect=a;update()})}
        contextBar.addView(button("자유"){val box=column();slider(box,"자유 비율",(if(draft.aspect>0)draft.aspect else 1f)/2,false){draft.aspect=(it*2).coerceAtLeast(.25f);update()};sheet("자유 크롭",box)})
        contextBar.addView(button("↻"){draft.rotation=(draft.rotation+90)%360;update()}.apply{contentDescription="90도 회전"})
        contextBar.addView(button("⇄"){draft.flip=!draft.flip;update()}.apply{contentDescription="좌우 반전"})
        contextBar.addView(button("초기화"){draft.x=.5f;draft.y=.5f;draft.zoom=1f;draft.aspect=0f;draft.rotation=0f;draft.flip=false;update()})
        update()
    }
    private fun closeCrop(){cropSession?.let{canvasBox.removeView(it)};cropSession=null;chrome.play.visibility=View.VISIBLE;normalTop();contextTools()}
    private fun effectCenter(){
        openSheets.toList().forEach{it.dismiss()}
        val handle=object:View(this){
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=dp(2).toFloat()}
            override fun onDraw(c:Canvas){val x=project.style.centerX*width;val y=project.style.centerY*height;c.drawCircle(x,y,dp(14).toFloat(),paint);c.drawLine(x-dp(22),y,x+dp(22),y,paint);c.drawLine(x,y-dp(22),x,y+dp(22),paint)}
            override fun onTouchEvent(e:MotionEvent):Boolean{if(e.actionMasked==MotionEvent.ACTION_DOWN)history.push(project);project.style.centerX=(e.x/width).coerceIn(0f,1f);project.style.centerY=(e.y/height).coerceIn(0f,1f);updateLiveStyle();invalidate();if(e.actionMasked==MotionEvent.ACTION_UP)persist();return true}
        }
        cropSession=handle;canvasBox.addView(handle,FrameLayout.LayoutParams(-1,-1));chrome.top.removeAllViews();chrome.top.addView(button("완료"){closeCrop()});contextBar.removeAllViews();contextBar.addView(text("드래그하여 효과 중심 이동"))
    }
    private fun textEditor(existing:Layer?,pending:Layer?=null){
        if(textSession!=null)return
        if(project.source.isBlank()){pick("base",arrayOf("image/*","video/*"));return}
        stopPreview();syncTransforms();engine.clearHelperBox()
        chrome.play.visibility=View.GONE
        val l=pending?:existing?.copy(crop=existing.crop.copy())?:Layer(kind="text",text="",width=.65f)
        // Legacy text keeps its original bitmap geometry until the user explicitly commits an edit.
        l.textLayoutVersion=1
        existing?.let{layerViews[it.id]?.visibility=View.INVISIBLE}
        textSession=CanvasTextEditor(this,canvasBox,l,{draft->store.saveTextEdit(existing?.id,draft)}){commit,draft->
            textSession=null
            store.clearTextEdit()
            chrome.play.visibility=View.VISIBLE
            if(commit && draft.text.isNotBlank())edit{if(existing!=null)project.layers[project.layers.indexOfFirst{it.id==existing.id}]=draft else project.layers.add(draft);selected=draft.id}
            else restoreLayers()
            normalTop();contextTools()
        }
        chrome.top.removeAllViews()
        chrome.top.addView(button("취소"){textSession?.finish(false)})
        chrome.top.addView(text("텍스트",16f),LinearLayout.LayoutParams(0,-1,1f))
        chrome.top.addView(button("완료"){textSession?.finish(true)})
        textTools()
    }
    private fun textTools(){
        val session=textSession?:return;val l=session.draft
        contextBar.removeAllViews()
        contextBar.addView(button("스타일"){
            val box=column();val strip=row();var d:android.app.Dialog?=null
            listOf("기본","Soft Label","Offset Type","Whisper","Drift Type","Echo Type").forEach{n->
                val example=l.copy(type=n,text=l.text.ifBlank{"오늘의 한 장"})
                val tile=column();tile.setPadding(dp(6),dp(4),dp(6),dp(4));tile.background=rounded(if(l.type==n)0xFF44444B.toInt() else 0xFF222226.toInt(),12f)
                tile.addView(ImageView(this).apply{contentDescription=n;setImageBitmap(Artwork.text(this@MainActivity,example));adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;minimumHeight=dp(48);setOnClickListener{l.type=n;l.motion=n=="Drift Type";session.refresh();d?.dismiss()}},LinearLayout.LayoutParams(dp(144),dp(92)))
                tile.addView(text(n,11f));strip.addView(tile,LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(8)})
            };box.addView(scrollRow(strip));d=sheet("텍스트 스타일",box)
        })
        contextBar.addView(button("글꼴"){val box=column();listOf("기본" to "","세리프" to "system:serif","모노" to "system:monospace").forEach{(n,f)->box.addView(button(n){l.font=f;session.refresh()}.apply{typeface=Typeface.create(f.removePrefix("system:"),Typeface.NORMAL)})};box.addView(button("개인 폰트"){pick("font-edit",arrayOf("font/*","application/octet-stream"))});sheet("글꼴",box)})
        contextBar.addView(button("색상"){val box=column();val r=row();listOf(Color.WHITE,Color.BLACK,0xFFD4FF83.toInt(),0xFFFFC5D6.toInt(),0xFF9DCEFF.toInt()).forEach{c->r.addView(button(""){l.color=c;session.refresh()}.apply{contentDescription="글자 색상 $c";background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(c);setStroke(dp(2),Color.GRAY)}},LinearLayout.LayoutParams(dp(48),dp(48)).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})};box.addView(scrollRow(r));sheet("색상",box)})
        contextBar.addView(button("정렬"){l.align=(l.align+1)%3;session.refresh()})
        contextBar.addView(button("크기·강도"){val box=column();slider(box,"크기",l.width,false){l.width=it.coerceIn(.1f,1f);session.refresh()};slider(box,"스타일 강도",l.emphasis,false){l.emphasis=it;session.refresh()};sheet("텍스트 조절",box)})
    }
    private fun emojiLibrary(filter:String="등록 폴더"){
        val path=prefs.getString("emojiFolder",null)
        if(path==null){var prompt:android.app.Dialog?=null;val box=column();box.addView(text("내 PNG를 이모지처럼\n원하는 폴더를 선택하세요.\n예: Pictures/igstoryplus/emoji"));box.addView(button("폴더 선택"){prompt?.dismiss();folderPicker.launch(null)});prompt=sheet("내 이모지",box);return}
        val box=column();val actions=row();var dialog:android.app.Dialog?=null
        val tabs=row();listOf("최근 사용","즐겨찾기","등록 폴더").forEach{tab->tabs.addView(button(tab){dialog?.dismiss();emojiLibrary(tab)}.apply{alpha=if(filter==tab)1f else .5f})};box.addView(tabs)
        actions.addView(button("새로고침"){dialog?.dismiss();emojiLibrary()});actions.addView(button("폴더 변경"){dialog?.dismiss();folderPicker.launch(null)});box.addView(actions)
        val grid=androidx.recyclerview.widget.RecyclerView(this).apply{layoutManager=androidx.recyclerview.widget.GridLayoutManager(this@MainActivity,4)};box.addView(grid,LinearLayout.LayoutParams(-1,dp(260)))
        val loading=text("PNG 불러오는 중…");box.addView(loading)
        dialog=sheet("내 이모지 · 길게 눌러 즐겨찾기",box)
        Thread{
            val result=runCatching { EmojiFolder.scan(this,Uri.parse(path)) }
            runOnUiThread{result.onSuccess{files->
                val fav=prefs.getStringSet("favorites",emptySet())?:emptySet();val recent=prefs.getString("recent","")!!.split("|")
                val sorted=files.filter{when(filter){"최근 사용"->it.uri.toString() in recent;"즐겨찾기"->it.uri.toString() in fav;else->true}}.sortedWith(compareByDescending<DocumentFile>{it.uri.toString() in fav}.thenBy{recent.indexOf(it.uri.toString()).let{i->if(i<0)999 else i}}.thenBy{it.name})
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
    private fun saveMusic(){project.music?.let{prefs.edit().putString("music:${it.uri}",store.gson.toJson(it)).apply();if(it.uri.isNotBlank())MusicLibrary(this).remember(it)};persist()}
    private fun selectMusic(m:Music){
        val desired=prefs.getInt("musicDuration",15).coerceIn(5,15)
        edit{project.music=m;if(!project.video){project.duration=prefs.getInt("musicDuration",15).coerceIn(5,15);if(m.durationMs in 5000 until project.durationMs())project.duration=(m.durationMs/1000).toInt()};MusicRange.clamp(m,project.durationMs());ensureMusicLayer()}
        saveMusic();musicPanels.clip()
        if(!project.video && project.duration<desired)toast("음악 길이에 맞춰 ${project.duration}초로 조정했습니다")
    }
    private fun musicMenu(){
        if(project.music==null)musicPanels.library() else musicPanels.clip()
    }
    private fun stopAudio(){stopPreview()}
    private fun presets(){
        val items=store.presets();choices("프리셋",listOf("현재 설정 저장")+items.map{it.name}){i->
            if(i==0)nameDialog("프리셋 이름",""){name->items.add(Preset(name,project.style.copy(),project.layers.firstOrNull{it.kind=="text"}?.type?:"Soft Label",project.music?.card?:"작은 카드",project.layers.firstOrNull{it.kind=="text"}?.copy(),project.layers.firstOrNull{it.kind=="music"}?.motion?:false,project.duration));store.savePresets(items)}
            else {val preset=items[i-1];choices(preset.name,listOf("적용","이름 변경","삭제")){a->when(a){
                0->edit{project.style=preset.style.copy();project.layers.filter{it.kind=="text"}.forEach{l->
                    l.type=preset.text;preset.template?.let{t->l.font=t.font;l.color=t.color;l.align=t.align;l.emphasis=t.emphasis;l.width=t.width;l.motion=t.motion;l.textLayoutVersion=t.textLayoutVersion}
                };project.music?.card=preset.card;ensureMusicLayer();project.layers.filter{it.kind=="music"}.forEach{it.motion=preset.musicMotion};if(!project.video)project.duration=preset.duration.coerceIn(5,15)}
                1->nameDialog("이름 변경",preset.name){preset.name=it;store.savePresets(items)}
                2->{items.remove(preset);store.savePresets(items)}
            }}}
        }
    }
    private fun nameDialog(title:String,value:String,onSave:(String)->Unit){val input=EditText(this).apply{setText(value)};AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("저장"){_,_->if(input.text.isNotBlank())onSave(input.text.toString())}.setNegativeButton("취소",null).show()}
    private fun more(){choices("더보기",listOf("사진·영상 선택","카메라","프리셋","세부 설정 / 영상 길이","레이어 목록","사용자 폰트 가져오기","새 스토리","오픈소스 라이선스")){when(it){
        0->pick("base",arrayOf("image/*","video/*"));1->camera();2->presets();3->details();4->choices("레이어",project.layers.map{if(it.kind=="text")it.text else it.kind}){i->selected=project.layers[i].id;contextTools()}
        5->{if(project.layers.none{it.id==selected && it.kind=="text"})toast("텍스트 레이어를 먼저 선택하세요") else pick("font",arrayOf("font/*","application/x-font-ttf","application/x-font-opentype","application/vnd.ms-opentype","application/octet-stream"))}
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
        if(project.music?.let{!MusicRange.valid(it,project.durationMs())}==true){toast("음악 구간을 조정하거나 짧은 음악 반복을 직접 켜 주세요");musicMenu();return}
        if(!previewMode){
            syncTransforms();selected=null;engine.clearHelperBox();previewMode=true
            chrome.top.visibility=View.GONE;(contextBar.parent as View).visibility=View.GONE
            val controls=FrameLayout(this).apply{isClickable=true;contentDescription="스토리 미리보기"}
            controls.addView(button("편집"){leavePreview()}.apply{background=rounded(0xAA242428.toInt(),24f)},FrameLayout.LayoutParams(dp(64),dp(48),Gravity.TOP or Gravity.START).apply{leftMargin=dp(8);topMargin=dp(8)})
            val seek=SeekBar(this).apply{contentDescription="미리보기 재생 위치";max=1000;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){if(user)clock.seek(project.durationMs()*p/1000)}})}
            controls.addView(seek,FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM).apply{leftMargin=dp(48);rightMargin=dp(56);bottomMargin=dp(8)})
            controls.addView(EditorIcons.button(this,"처음부터"){clock.seek(0)},FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.START).apply{bottomMargin=dp(8)})
            chrome.stage.addView(controls,FrameLayout.LayoutParams(-1,-1));previewControls=controls;chrome.play.bringToFront()
            clock.onPosition={seek.progress=(it*1000/project.durationMs()).toInt()}
            editor.visibility=View.VISIBLE;editor.isEnabled=false
        }
        clock.toggle()
    }
    private fun leavePreview(){clock.pausePlayback();clock.onPosition=null;previewControls?.let{chrome.stage.removeView(it)};previewControls=null;previewMode=false;editor.isEnabled=true;chrome.top.visibility=View.VISIBLE;(contextBar.parent as View).visibility=View.VISIBLE;stopPreview();normalTop();contextTools()}
    private fun stopPreview(){if(::live.isInitialized)clock.stop()}
    override fun onPause(){super.onPause();syncTransforms();persist();stopAudio();live.onPause();editingPlayer?.pause()}
    override fun onResume(){super.onResume();if(::live.isInitialized)live.onResume()}
    override fun onDestroy(){backgroundUpdate?.let{handler.removeCallbacks(it)};backgroundWorker.shutdownNow();activeExport?.cancel();stopAudio();clock.release();editingPlayer?.release();super.onDestroy()}
}
