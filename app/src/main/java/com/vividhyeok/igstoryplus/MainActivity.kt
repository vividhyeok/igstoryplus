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
    private lateinit var photoLoading:ProgressBar
    private var requestedPhoto=""
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
    private val videoPoseRefresh=Runnable{clock.updateStyle();editingPlayer?.let{if(!clock.playing)it.seekTo(it.currentPosition)}}
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
    private val musicPanels by lazy { MusicPanels(this,{project},clock,{if(!clock.playing)syncTransforms();history.push(project)},{ensureMusicLayer();restoreLayers();contextTools()},{saveMusic()},{mode->pick(mode,if(mode=="music")arrayOf("audio/*") else arrayOf("image/*"))},{m->selectMusic(m)},{stopAudio();edit{project.music=null;project.layers.removeAll{it.kind=="music"}}}) }
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
            when { chrome.dismissPanel!=null->chrome.dismissPanel?.invoke();textSession!=null->textSession?.finish(true);cropSession!=null->closeCrop();previewMode->leavePreview();selected!=null->{selected=null;engine.clearHelperBox();contextTools()};else->{syncTransforms();persist();finish()} }
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
        guides=AlignmentGuides(this).apply{safeArea=prefs.getBoolean("safeArea",false)};canvasBox.addView(guides,FrameLayout.LayoutParams(-1,-1))
        photoLoading=ProgressBar(this).apply{contentDescription="사진 불러오는 중";visibility=View.GONE}
        canvasBox.addView(photoLoading,FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER))
        live.onPhotoReady={photoLoading.visibility=View.GONE}
        editor.source.setImageBitmap(Bitmap.createBitmap(540,960,Bitmap.Config.ARGB_8888))
        editor.source.contentDescription="배경 사진"
        engine=PhotoEditor.Builder(this,editor).setPinchTextScalable(true).build()
        editor.canTransformLayer={v->textSession==null && cropSession==null && !previewMode && project.layers.firstOrNull{layerViews[it.id]===v}?.locked!=true}
        editor.onLayerGesture={v,start->
            if(!rebuilding && textSession==null && cropSession==null){
                if(start){stopPreview();gestureBefore=store.copy(project);dragging=true;selected=layerViews.entries.firstOrNull{it.value===v}?.key;contextTools()}
                else { guides.clear();centerPivot(v);snap(v);syncTransforms();dragging=false;gestureBefore?.let{if(store.gson.toJson(it)!=store.gson.toJson(project))history.push(it)};gestureBefore=null;persist() }
            }
        }
        editor.onLayerTransform={v->guides.follow(v,editor)}
        editor.onLayerTap={v->project.layers.firstOrNull{layerViews[it.id]===v && it.kind=="text"}?.let{l->v.post{if(textSession==null && v.isAttachedToWindow)textEditor(l)}}}
        editor.onLayerDoubleTap={v->project.layers.firstOrNull{layerViews[it.id]===v && it.kind=="text"}?.let{textEditor(it)}}
        editor.source.setOnTouchListener(BackgroundGestures(editor.source,{project},{foregroundBounds},
            {stopPreview();gestureBefore=store.copy(project);selected="background";engine.clearHelperBox();contextTools()},
            {live.pose=project.pose.copy();if(project.video){handler.removeCallbacks(videoPoseRefresh);handler.postDelayed(videoPoseRefresh,80)};guides.followPoint(project.pose.x,project.pose.y,project.pose.rotation)},
            {guides.clear();gestureBefore?.let{history.push(it)};gestureBefore=null;persist()},
            {hit->if(previewMode)return@BackgroundGestures;selected=if(hit && selected!="background")"background" else null;engine.clearHelperBox();contextTools()},
            {if(!previewMode)crop(null)},{textSession==null && cropSession==null && !previewMode}))
        center.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
            val h=center.height;val w=min(center.width,(h*9f/16).toInt()); val ch=(w*16f/9).toInt()
            if(w>0 && (canvasBox.width!=w || canvasBox.height!=ch)){canvasBox.layoutParams=FrameLayout.LayoutParams(w,ch,Gravity.CENTER);canvasBox.post{relayoutLayers()}}
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
                    if(name in listOf("⋯","×","사진 추가","스타일","저장","자르기","교체","수정","글꼴","색상","정렬","복제","삭제","음악 구간","카드","볼륨","수평","플러스","배치")){
                        if(name=="×")child.text="선택 해제" else if(name=="⋯")child.text="더보기"
                        child.textSize=10f;child.minWidth=dp(52);child.setPadding(dp(6),dp(3),dp(6),dp(3));child.setCompoundDrawables(null,EditorIcons.drawable(icon).apply{setBounds(0,0,dp(23),dp(23))},null,null);child.compoundDrawablePadding=dp(2)
                    }
                }
            }
        })
        setContentView(root)
    }
    private fun normalTop(){
        chrome.top.removeAllViews()
        fun tool(g:String,n:String,a:()->Unit){chrome.top.addView(chrome.tool(g,n){safe(a)}.apply{isEnabled=when(n){"실행 취소"->history.canUndo;"다시 실행"->history.canRedo;else->true};alpha=if(isEnabled)1f else .3f},LinearLayout.LayoutParams(0,-1,1f))}
        tool("×","닫기"){onBackPressedDispatcher.onBackPressed()}
        tool("↶","실행 취소"){stopPreview();history.undo(project)?.let{project=it;restore()}}
        tool("↷","다시 실행"){stopPreview();history.redo(project)?.let{project=it;restore()}}
        tool("Aa","텍스트"){textEditor(null)}
        tool("▧","스티커"){emojiLibrary()}
        tool("♫","음악"){musicMenu()}
        tool("⋯","더보기"){more()}
    }
    private fun safe(action:()->Unit){try{action()}catch(e:Exception){AlertDialog.Builder(this).setTitle("다시 시도해 주세요").setMessage(e.message?:"파일을 열 수 없습니다. 원본을 다시 선택해 주세요.").setPositiveButton("확인",null).show()}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun persist(){store.save(project);if(textSession==null && cropSession==null && !previewMode){
        for(i in 0 until chrome.top.childCount){val v=chrome.top.getChildAt(i);when(v.contentDescription){"실행 취소"->v.isEnabled=history.canUndo;"다시 실행"->v.isEnabled=history.canRedo};v.alpha=if(v.isEnabled)1f else .3f}
    }}
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
            "art"->{edit{project.music?.art=uri.toString()};project.music?.let{refreshAura(it)};saveMusic();musicMenu()}
            "font", "font-edit"->{
                val f=File(filesDir,"fonts/${System.currentTimeMillis()}.ttf").apply{parentFile!!.mkdirs()}
                contentResolver.openInputStream(uri)!!.use{i->f.outputStream().use{i.copyTo(it)}}
                Typeface.createFromFile(f)
                if(importMode=="font-edit")textSession?.let{it.draft.font=f.absolutePath;it.refresh()} else edit{project.layers.firstOrNull{it.id==selected}?.font=f.absolutePath}
            }
            "layer"->edit{project.layers.add(Layer(uri=uri.toString()));selected=project.layers.last().id}
            else->{
                val mime=contentResolver.getType(uri)?:if(uri.toString().endsWith(".mp4"))"video/mp4" else "image/jpeg"
                edit{project.source=uri.toString();project.video=mime.startsWith("video");project.crop=Crop();project.pose=PhotoPose();if(project.video){project.style.videoFilters=false;project.style.name="Simple";project.style.motion=false;
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
        if(textSession==null && cropSession==null && !previewMode)normalTop()
        empty.visibility=if(project.source.isBlank())View.VISIBLE else View.GONE
        if(project.source.isBlank())photoLoading.visibility=View.GONE
        else if(project.source!=requestedPhoto){requestedPhoto=project.source;live.expectPhoto();photoLoading.visibility=if(project.video)View.GONE else View.VISIBLE}
        live.style=project.style.copy();live.durationMs=project.durationMs()
        live.pose=project.pose.copy()
        live.visibility=if(project.video)View.GONE else View.VISIBLE
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
                }.also{r->runOnUiThread{if(generation==backgroundGeneration)r.onSuccess{foregroundBounds=bounds;live.setPhoto(it.first,it.second)}.onFailure{photoLoading.visibility=View.GONE;status.text="원본 접근 불가 · 사진에서 다시 선택";toast("원본이 삭제되었거나 접근 권한이 없습니다")}}}
            }}
            handler.postDelayed(backgroundUpdate!!,80)
        }
        restoreLayers();contextTools()
    }
    private fun updateLiveStyle(){clock.updateStyle()}
    private fun relayoutLayers(){
        if(editor.width<=0)return
        project.layers.forEach{l->layerViews[l.id]?.let{v->v.translationX=l.x*editor.width-v.left-v.width/2f;v.translationY=l.y*editor.height-v.top-v.height/2f;v.scaleX=l.scale*editor.width/(layerReferenceWidth[l.id]?:editor.width);v.scaleY=v.scaleX}}
    }
    private fun restoreLayers(){
        if(editor.width<=0)return
        if(textSession!=null){
            relayoutLayers()
            return
        }
        rebuilding=true;engine.clearAllViews();layerViews.clear();layerReferenceWidth.clear()
        project.layers.forEach{l->runCatching{
            val original=Artwork.layer(this,l,project.music)
            val w=(l.width*editor.width).toInt().coerceIn(8,editor.width*2);val h=(w.toFloat()/original.width*original.height).toInt().coerceAtLeast(1)
            engine.addImage(Bitmap.createScaledBitmap(original,w,h,true))
            val v=editor.getChildAt(editor.childCount-1);layerViews[l.id]=v;layerReferenceWidth[l.id]=editor.width
            v.setTag(R.id.layer_reference_width,editor.width)
            v.contentDescription=if(l.kind=="text")"텍스트 레이어: ${l.text}" else "${l.kind} 레이어"
            v.isFocusable=true
            if(l.kind=="music")v.findViewById<ImageView>(ja.burhanrashid52.photoeditor.R.id.imgPhotoEditorImage)?.foreground=MusicIndicatorDrawable(project.music?.card?:"작은 카드",original.width,original.height).apply{update(0f,0f,project.style.reaction=="Music Dot" && project.style.beat>0,project.style.auraColor)}
            else if(l.accent!="없음")v.findViewById<ImageView>(ja.burhanrashid52.photoeditor.R.id.imgPhotoEditorImage)?.foreground=StoryAccentDrawable(original,l).apply{update(0,project.durationMs(),0f)}
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
        if(abs(v.rotation-(v.rotation/90).roundToInt()*90)<4)v.rotation=(v.rotation/90).roundToInt()*90f
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
            contextBar.addView(button("수평"){edit{project.pose.rotation=0f}})
            contextBar.addView(button("원본 구도"){edit{project.pose=PhotoPose();project.crop=Crop()}})
            contextBar.addView(toolButton("교체"){pick("base",arrayOf("image/*","video/*"))})
            contextBar.addView(toolButton("스타일"){styles()})
            return
        }
        if(l==null){
            contextBar.addView(toolButton("사진 추가"){if(project.source.isBlank())pick("base",arrayOf("image/*","video/*")) else photoMenu()})
            contextBar.addView(toolButton("스타일"){styles()})
            contextBar.addView(toolButton("플러스"){enhancements()})
            contextBar.addView(toolButton("저장"){render(false)})
            contextBar.addView(button("스토리로 공유"){render(true)}.apply{contentDescription="인스타 스토리로 공유";textSize=13f;setTextColor(Color.BLACK);setPadding(dp(16),0,dp(16),0);background=rounded(Color.rgb(242,242,239),25f)},LinearLayout.LayoutParams(-2,dp(48)).apply{leftMargin=dp(8)})
            return
        }
        contextBar.addView(button("×"){selected=null;engine.clearHelperBox();contextTools()}.apply{contentDescription="선택 해제"})
        if(l.kind=="photo"){contextBar.addView(button("수평"){edit{l.rotation=0f}});contextBar.addView(button("스타일"){layerStyles(l)})}
        if(l.kind=="text"){contextBar.addView(button("수정"){textEditor(l)})}
        if(l.kind=="png")contextBar.addView(button("크기·위치"){val box=column();slider(box,"크기",l.width){l.width=it.coerceIn(.02f,1f);restoreLayers()};slider(box,"가로 위치",l.x){l.x=it;restoreLayers()};slider(box,"세로 위치",l.y){l.y=it;restoreLayers()};sheet("스티커 조절",box)})
        if(l.kind=="music"){
            contextBar.addView(button("음악 구간"){musicMenu()})
            contextBar.addView(toolButton("카드"){musicPanels.cards()})
            contextBar.addView(button("볼륨"){musicPanels.volume()})
            contextBar.addView(button("카드 숨김"){edit{project.music?.card="숨김";ensureMusicLayer()}})
            contextBar.addView(button("⋯"){layerExtra(l)}.apply{contentDescription="레이어 추가 작업"})
            return
        }
        contextBar.addView(toolButton("플러스"){enhancements()})
        contextBar.addView(toolButton("배치"){arrange(l)})
        contextBar.addView(button("⋯"){layerExtra(l)}.apply{contentDescription="레이어 추가 작업"})
    }
    private fun duplicate(l:Layer){edit{val copy=store.gson.fromJson(store.gson.toJson(l),Layer::class.java);copy.id=java.util.UUID.randomUUID().toString();copy.x=(copy.x+.03f).coerceAtMost(.95f);project.layers.add(copy);selected=copy.id}}
    private fun enhancements(){syncTransforms();val l=project.layers.firstOrNull{it.id==selected};EnhancementPanel(this,project,l,clock,{history.push(project)},{restoreLayers()},{persist()}).show()}
    private fun arrange(l:Layer){
        syncTransforms();val body=column()
        val positions=row();positions.addView(toolButton("가로 중앙"){edit{l.x=.5f}});positions.addView(toolButton("세로 중앙"){edit{l.y=.5f}});positions.addView(toolButton("수평"){edit{l.rotation=0f}});body.addView(positions)
        body.addView(Switch(this).apply{text="위치 잠금 · 레이어 목록에서 해제 가능";setTextColor(Color.WHITE);minHeight=dp(48);isChecked=l.locked;setOnCheckedChangeListener{_,locked->history.push(project);l.locked=locked;persist()}})
        body.addView(Switch(this).apply{text="게시 UI 여백 안내 · 출력에는 포함 안 됨";setTextColor(Color.WHITE);minHeight=dp(48);isChecked=guides.safeArea;setOnCheckedChangeListener{_,on->guides.safeArea=on;prefs.edit().putBoolean("safeArea",on).apply();guides.invalidate()}})
        EditorDock(this,"배치",body,{persist()}).show()
    }
    private fun layerExtra(l:Layer){
        val items=listOf("앞으로","뒤로","미세 모션 ${if(l.motion)"ON" else "OFF"}","복제","모션 종류·강도","삭제")+(if(l.kind=="photo")listOf("자르기","교체")else emptyList())
        choices("레이어",items){i->when(i){3->duplicate(l);4->layerMotion(l);5->edit{project.layers.remove(l);selected=null};6->crop(l);7->pick("replace:${l.id}",arrayOf("image/*"));else->edit{when(i){0->{project.layers.remove(l);project.layers.add(l)};1->{project.layers.remove(l);project.layers.add(0,l)};2->l.motion=!l.motion}}}}
    }
    private fun layerMotion(l:Layer){
        val box=column();listOf("기본","숨쉬기","떠오르기").forEach{mode->box.addView(button(mode){edit{l.motion=true;l.motionStyle=mode};clock.play()})}
        slider(box,"미세 모션 강도",l.motionAmount){l.motionAmount=it;l.motion=true}
        box.addView(button("모션 끄기"){edit{l.motion=false}});sheet("레이어 모션",box){stopPreview();persist();restore()}
    }
    private fun layerStyles(l:Layer){
        stopPreview();syncTransforms();val target=Project(source=l.uri,crop=l.crop.copy(),style=(l.photoStyle?:Style()).copy(motion=l.motion))
        fun apply(){l.photoStyle=target.style.copy(motion=false,beat=0f);l.motion=target.style.motion;restoreLayers()}
        StylePicker(this,{target},{history.push(project)},{apply()},{apply();persist()},{clock.play()},{clock.pausePlayback()},{layerMotion(l)},{presets()}).show()
    }
    private fun choices(title:String,items:List<String>,thumbnail:((Int)->Bitmap?)?=null,onSelect:(Int)->Unit){
        val box=column();var d:android.app.Dialog?=null
        items.forEachIndexed{i,n->
            if(thumbnail==null)box.addView(button(n){d?.dismiss();safe{onSelect(i)}})
            else {val tile=row();val image=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP};tile.addView(image,LinearLayout.LayoutParams(dp(54),dp(88)));tile.addView(button(n){d?.dismiss();safe{onSelect(i)}},LinearLayout.LayoutParams(0,dp(88),1f));box.addView(tile)
                backgroundWorker.execute{runCatching{thumbnail(i)}.onSuccess{bitmap->runOnUiThread{if(d?.isShowing==true)image.setImageBitmap(bitmap)}}}
            }
        }
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
        slider(box,"음악 반응",project.style.beat){project.style.beat=it;updateLiveStyle()}
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
        contextBar.addView(EditorIcons.button(this,"90도 회전"){draft.rotation=(draft.rotation+90)%360;update()})
        contextBar.addView(EditorIcons.button(this,"좌우 반전"){draft.flip=!draft.flip;update()})
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
        chrome.dismissPanel?.invoke();stopPreview();syncTransforms();engine.clearHelperBox()
        chrome.play.visibility=View.GONE
        val l=pending?:existing?.copy(crop=existing.crop.copy(),inlineEmoji=existing.inlineEmoji.toMutableMap())?:run{val y=listOf(.5f,.32f,.68f).firstOrNull{candidate->project.layers.none{it.kind=="text" && abs(it.y-candidate)<.13f}}?:.5f;Layer(kind="text",text="",type="클래식",textBackground="없음",width=.8f,y=y)}
        // Legacy text keeps its original bitmap geometry until the user explicitly commits an edit.
        if(existing==null && pending==null)l.textLayoutVersion=2 else {TextGeometry.migrate(l);if(l.textBackground=="기존")l.textBackground=if(l.type in listOf("Soft Label","Mono Stamp"))"반투명" else "없음"}
        existing?.let{layerViews[it.id]?.visibility=View.INVISIBLE}
        textSession=CanvasTextEditor(this,canvasBox,l,{draft->store.saveTextEdit(existing?.id,draft)}){commit,draft->
            textSession=null
            chrome.tray.removeAllViews();chrome.tray.visibility=View.GONE
            store.clearTextEdit()
            chrome.play.visibility=View.VISIBLE
            if(commit && draft.text.isNotBlank())edit{if(existing!=null)project.layers[project.layers.indexOfFirst{it.id==existing.id}]=draft else project.layers.add(draft);selected=draft.id}
            else {if(existing!=null)selected=existing.id;restoreLayers()}
            normalTop();contextTools()
        }
        chrome.top.removeAllViews()
        chrome.top.addView(button("취소"){textSession?.finish(false)})
        chrome.top.addView(text("텍스트",16f),LinearLayout.LayoutParams(0,-1,1f))
        chrome.top.addView(button("완료"){textSession?.finish(true)})
        textTools()
    }
    private fun textTools(){textSession?.let{StoryTextControls(this,chrome,it){pick("font-edit",arrayOf("font/*","application/octet-stream"))}.show()}}
    private fun emojiLibrary(filter:String="내 이모지"){
        val path=prefs.getString("emojiFolder",null)
        val box=column();var dialog:android.app.Dialog?=null;var all=emptyList<EmojiItem>();var category="전체"
        val tabs=row();listOf("내 이모지","내 폴더","최근 사용","즐겨찾기").forEach{tab->tabs.addView(button(tab){dialog?.dismiss();emojiLibrary(tab)}.apply{alpha=if(filter==tab)1f else .5f})};box.addView(scrollRow(tabs))
        val search=EditText(this).apply{hint="이모지 검색";contentDescription="이모지 검색";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)};box.addView(search)
        val categories=row();box.addView(scrollRow(categories))
        val actions=row();actions.addView(button("새로고침"){dialog?.dismiss();emojiLibrary(filter)});actions.addView(button("폴더 변경"){dialog?.dismiss();folderPicker.launch(null)});box.addView(actions)
        val grid=androidx.recyclerview.widget.RecyclerView(this).apply{layoutManager=androidx.recyclerview.widget.GridLayoutManager(this@MainActivity,4)};box.addView(grid,LinearLayout.LayoutParams(-1,dp(260)))
        val loading=text("이모지 불러오는 중…");box.addView(loading)
        fun populate(){
            val fav=prefs.getStringSet("favorites",emptySet())?:emptySet();val recent=prefs.getString("recent","")!!.split("|")
            val query=search.text.toString().trim()
            val shown=all.filter{when(filter){"내 이모지"->true;"내 폴더"->!EmojiLibrary.bundled(it.uri);"최근 사용"->it.uri in recent;else->it.uri in fav}}
                .filter{(category=="전체" || it.category==category) && (query.isBlank() || "${it.name} ${it.category} ${it.keywords}".contains(query,true))}
                .sortedWith(compareByDescending<EmojiItem>{it.uri in fav}.thenBy{recent.indexOf(it.uri).let{i->if(i<0)999 else i}}.thenBy{it.name})
            loading.text=if(shown.isEmpty())if(filter=="내 폴더" && path==null)"폴더 변경을 눌러 내 PNG 폴더를 연결하세요" else "검색 결과가 없습니다 · 길게 눌러 즐겨찾기" else "${shown.size}개 · 길게 눌러 즐겨찾기"
            grid.adapter=EmojiAdapter(shown,fav,{f->dialog?.dismiss();safe{edit{project.layers.add(Layer(kind="png",uri=f.uri,width=if(EmojiLibrary.bundled(f.uri)).11f else .089f));selected=project.layers.last().id};prefs.edit().putString("recent",(listOf(f.uri)+recent.filter{it!=f.uri}).take(30).joinToString("|")).apply()}},
                {f->val favorites=(prefs.getStringSet("favorites",emptySet())?:emptySet()).toMutableSet();if(!favorites.add(f.uri))favorites.remove(f.uri);prefs.edit().putStringSet("favorites",favorites).apply();populate()})
        }
        search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun afterTextChanged(s:android.text.Editable?){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){populate()}})
        dialog=sheet("이모지",box)
        Thread{
            val builtin=EmojiLibrary.defaults(applicationContext)
            val user=if(path==null)Result.success(emptyList()) else runCatching{EmojiFolder.scan(this,Uri.parse(path)).map{EmojiLibrary.item(it)}}
            runOnUiThread{if(dialog?.isShowing==true){all=builtin+user.getOrDefault(emptyList());
                (listOf("전체")+all.map{it.category}.distinct()).forEach{value->categories.addView(button(value){category=value;populate()})}
                populate();if(user.isFailure && filter=="내 폴더")loading.text="폴더 권한이 만료되었습니다. 폴더 변경으로 다시 연결하세요."
            }}
        }.start()
    }
    private fun ensureMusicLayer(){
        val music=project.music?:return
        if(music.card=="숨김")project.layers.removeAll{it.kind=="music"}
        else if(project.layers.none{it.kind=="music"})project.layers.add(Layer(kind="music",width=if(music.card=="스토리").62f else .55f,y=.8f))
    }
    private fun saveMusic(){project.music?.let{prefs.edit().putString("music:${it.uri}",store.gson.toJson(it)).apply();if(it.uri.isNotBlank())MusicLibrary(this).remember(it)};persist()}
    private fun selectMusic(m:Music){
        val desired=prefs.getInt("musicDuration",15).coerceIn(5,15)
        edit{if(project.music==null)project.style.beat=.55f;project.music=m;if(!project.video){project.duration=prefs.getInt("musicDuration",15).coerceIn(5,15);if(m.durationMs in 5000 until project.durationMs())project.duration=(m.durationMs/1000).toInt()};MusicRange.clamp(m,project.durationMs());ensureMusicLayer()}
        saveMusic();musicPanels.clip()
        refreshAura(m)
        if(!project.video && project.duration<desired)toast("음악 길이에 맞춰 ${project.duration}초로 조정했습니다")
    }
    private fun refreshAura(m:Music){
        val uri=m.uri;val art=m.art
        backgroundWorker.execute{val color=runCatching{Artwork.palette(applicationContext,art)}.getOrDefault(0xFF98B8CF.toInt());runOnUiThread{if(project.music?.uri==uri && project.music?.art==art){project.style.auraColor=color;updateLiveStyle();persist()}}}
    }
    private fun musicMenu(){
        if(project.music==null && project.video){choices("영상 소리와 음악",listOf("원본 영상 소리로 반응","새 음악 추가")){if(it==0){history.push(project);project.reactionAudio="원본 영상";if(project.style.beat==0f)project.style.beat=.55f;project.style.reactionTarget="원본 화면 전체";enhancements()}else musicPanels.library()}}else if(project.music==null)musicPanels.library() else musicPanels.clip()
    }
    private fun stopAudio(){stopPreview()}
    private fun presets(){
        val items=store.presets();val snapshot=store.copy(project)
        choices("프리셋",listOf("현재 설정 저장")+items.map{it.name},{index->
            if(index==0)null else {val preset=items[index-1];val p=store.copy(snapshot);p.style=preset.style.copy();val bitmap=filteredBitmap(Artwork.background(applicationContext,p,144),p.style)
                val text=preset.template?.copy(text="오늘의 기록")?:Layer(kind="text",text="오늘의 기록",type=preset.text,y=.3f);Exporter.drawLayer(Canvas(bitmap),text,Artwork.text(applicationContext,text),bitmap.width,bitmap.height)
                if(preset.card!="숨김"){val m=(p.music?:Music()).copy(card=preset.card);val l=Layer(kind="music",width=.7f,y=.8f);Exporter.drawLayer(Canvas(bitmap),l,Artwork.musicCard(applicationContext,m,l),bitmap.width,bitmap.height)};bitmap}
        }){i->
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
    private fun drafts(){
        stopPreview();syncTransforms();val library=DraftLibrary(this);val box=column();var dialog:android.app.Dialog?=null
        box.addView(button("현재 스토리 저장"){
            nameDialog("초안 이름",""){name->
                val snapshot=store.copy(project)
                backgroundWorker.execute{
                    val result=runCatching{library.save(name,snapshot)}
                    runOnUiThread{result.onSuccess{dialog?.dismiss();drafts()}.onFailure{toast("초안을 저장하지 못했습니다")}}
                }
            }
        })
        library.all().forEach{draft->val tile=row();tile.addView(ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;com.bumptech.glide.Glide.with(this@MainActivity).load(library.thumbnail(draft.id)).signature(com.bumptech.glide.signature.ObjectKey(draft.updated)).into(this)},LinearLayout.LayoutParams(dp(58),dp(96)))
            tile.addView(button(draft.name){choices(draft.name,listOf("열기","이름 변경","삭제")){action->when(action){0->{dialog?.dismiss();store.clearTextEdit();selected=null;edit{project=store.copy(draft.project)}};1->nameDialog("초안 이름",draft.name){library.rename(draft,it);dialog?.dismiss();drafts()};2->{library.delete(draft);dialog?.dismiss();drafts()}}}},LinearLayout.LayoutParams(0,dp(96),1f));box.addView(tile)}
        dialog=sheet("내 초안",box)
    }
    private fun more(){choices("더보기",listOf("사진·영상 선택","카메라","프리셋","세부 설정 / 영상 길이","레이어 목록","사용자 폰트 가져오기","새 스토리","오픈소스 라이선스","내 초안")){when(it){
        0->pick("base",arrayOf("image/*","video/*"));1->camera();2->presets();3->details();4->choices("레이어",project.layers.map{if(it.kind=="text")it.text else it.kind}){i->selected=project.layers[i].id;contextTools()}
        5->{if(project.layers.none{it.id==selected && it.kind=="text"})toast("텍스트 레이어를 먼저 선택하세요") else pick("font",arrayOf("font/*","application/x-font-ttf","application/x-font-opentype","application/vnd.ms-opentype","application/octet-stream"))}
        6->AlertDialog.Builder(this).setMessage("새 스토리를 시작할까요? 현재 초안은 교체됩니다.").setPositiveButton("새로 만들기"){_,_->edit{project=Project()}}.setNegativeButton("취소",null).show()
        7->AlertDialog.Builder(this).setTitle("오픈소스").setMessage(listOf("THIRD_PARTY_NOTICES.txt","Apache-2.0","Glide-LICENSE").joinToString("\n\n"){name->assets.open(name).bufferedReader().use{it.readText()}}).setPositiveButton("확인",null).show()
        8->drafts()
    }}}
    private fun render(share:Boolean){
        if(project.source.isBlank()){toast("사진 또는 영상을 선택해 주세요");return}
        syncTransforms();persist();stopAudio();engine.clearHelperBox()
        val snapshot=store.copy(project);val exporter=Exporter(this);activeExport=exporter
        val dialog=ExportProgressDialog(this){exporter.cancel();activeExport=null;toast("취소했습니다. 초안은 보존됩니다")}.also{it.show()}
        exporter.export(snapshot,{dialog.update(it)}){result->dialog.dismiss();activeExport=null
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
            chrome.dismissPanel?.invoke();guides.safeArea=false;guides.invalidate();syncTransforms();selected=null;engine.clearHelperBox();previewMode=true
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
    private fun leavePreview(){guides.safeArea=prefs.getBoolean("safeArea",false);guides.invalidate();clock.pausePlayback();clock.onPosition=null;previewControls?.let{chrome.stage.removeView(it)};previewControls=null;previewMode=false;editor.isEnabled=true;chrome.top.visibility=View.VISIBLE;(contextBar.parent as View).visibility=View.VISIBLE;stopPreview();normalTop();contextTools()}
    private fun stopPreview(){if(::live.isInitialized)clock.stop()}
    override fun onPause(){super.onPause();syncTransforms();persist();stopAudio();live.onPause();editingPlayer?.pause()}
    override fun onResume(){super.onResume();if(::live.isInitialized)live.onResume()}
    override fun onDestroy(){if(::chrome.isInitialized)chrome.dismissPanel?.invoke();backgroundUpdate?.let{handler.removeCallbacks(it)};backgroundWorker.shutdownNow();activeExport?.cancel();stopAudio();clock.release();editingPlayer?.release();super.onDestroy()}
}
