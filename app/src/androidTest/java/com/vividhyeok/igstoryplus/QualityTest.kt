package com.vividhyeok.igstoryplus

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class QualityTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val c=instrumentation.targetContext
    private val evidence=File(c.getExternalFilesDir(null),"validation/quality").apply{mkdirs()}
    private fun Bitmap.save(name:String){File(evidence,"$name.png").outputStream().use{compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun delta(a:Bitmap,b:Bitmap):Double{var sum=0.0;for(y in 0 until a.height step 2)for(x in 0 until a.width step 2){val p=a.getPixel(x,y);val q=b.getPixel(x,y);sum+=abs(Color.red(p)-Color.red(q))+abs(Color.green(p)-Color.green(q))+abs(Color.blue(p)-Color.blue(q))};return sum/((a.width+1)/2*((a.height+1)/2)*3*255.0)}
    @Test fun fiveRealScenesZeroIdentityDistinctStylesAndContinuousMotion(){
        val report=linkedMapOf<String,Any>()
        for(scene in listOf("nature","sea","portrait","night","cafe")){
            val original=instrumentation.context.assets.open("scenes/$scene.jpg").use{BitmapFactory.decodeStream(it)}
            val base=Bitmap.createScaledBitmap(original,270,480,true)
            val settings=EditorCatalog.photos.flatMap{name->listOf(0f,.25f,1f).map{Style(name=name,amount=it)}}
            val rendered=filteredBitmaps(base,settings)
            legacyFilteredBitmaps(base,EditorCatalog.photos.take(7).map{Style(name=it)}).forEachIndexed{index,bitmap->bitmap.save("before-$scene-${EditorCatalog.photos[index].replace(" ","-")}")}
            val identity=EditorCatalog.photos.mapIndexed{index,name->
                val zero=rendered[index*3];val difference=delta(base,zero);assertTrue("$scene $name 0% must be identity: $difference",difference<.0008)
                for(level in 0..2)rendered[index*3+level].save("$scene-${name.replace(" ","-")}-$level")
                difference
            }
            val grain=rendered[EditorCatalog.photos.indexOf("Grainy")*3+2];val grit=rendered[EditorCatalog.photos.indexOf("Gritty")*3+2]
            val zoom=rendered[EditorCatalog.photos.indexOf("Zoomblur")*3+2];val flow=rendered[EditorCatalog.photos.indexOf("Flow Blur")*3+2]
            assertTrue("Grainy and Gritty must differ",delta(grain,grit)>.002);assertTrue("Flow must differ from radial zoom",delta(zoom,flow)>.0001)
            report[scene]=identity
            if(scene=="portrait"){
                for(name in listOf("Handheld","Zoomblur","Flow Blur","Shutter Drag","Sensor Raw","Frame Echo")){
                    val style=Style(name=name,amount=.65f,motion=true)
                    val start=filteredBitmaps(base,listOf(style),phase=0f).single();val middle=filteredBitmaps(base,listOf(style),phase=1.7f).single();val end=filteredBitmaps(base,listOf(style),phase=(2*PI).toFloat()-.002f).single()
                    assertTrue("$name must move",delta(start,middle)>.00001);assertTrue("$name loop must join",delta(start,end)<.006)
                    start.save("motion-$name-start");middle.save("motion-$name-mid");end.save("motion-$name-end")
                }
            }
        }
        File(evidence,"styles.json").writeText(Store(c).gson.toJson(report))
    }
    @Test fun backgroundReactionsKeepForegroundAndLayerStylesStayIndependent(){
        val raw=instrumentation.context.assets.open("scenes/cafe.jpg").use{BitmapFactory.decodeStream(it)}
        val foreground=Bitmap.createScaledBitmap(raw,270,270,true);val background=Bitmap.createBitmap(270,480,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(45,65,90))}
        for(mode in listOf("Ambient Breathing","Album Aura","Music Dot")){
            val style=Style(beat=1f,reaction=mode)
            val off=filteredBitmaps(background,listOf(style),foreground=foreground).single();val on=filteredBitmaps(background,listOf(style),foreground=foreground,beat=1f).single()
            assertEquals("$mode must preserve center",off.getPixel(135,240),on.getPixel(135,240))
            if(mode!="Music Dot")assertTrue("$mode should change background",delta(off,on)>.0001) else assertEquals(0.0,delta(off,on),0.0)
            on.save("reaction-${mode.replace(" ","-")}")
        }
        val source=File(c.cacheDir,"quality-cafe.jpg");source.outputStream().use{raw.compress(Bitmap.CompressFormat.JPEG,90,it)}
        val layer=Layer(uri=android.net.Uri.fromFile(source).toString(),photoStyle=Style(name="Gritty",amount=.9f),motion=true,motionStyle="떠오르기",motionAmount=.65f)
        val project=Project(layers= mutableListOf(layer));val before=project.style.copy();val styled=Artwork.layer(c,layer,null);val plain=Artwork.layer(c,layer.copy(photoStyle=null),null)
        assertEquals(before,project.style);assertTrue(delta(styled,plain)>.002)
        val restored=Store(c).copy(project).layers.single();assertEquals(layer.photoStyle,restored.photoStyle);assertEquals(layer.motionStyle,restored.motionStyle);assertEquals(layer.motionAmount,restored.motionAmount)
    }
    @Test fun longKoreanCardsTextAndDynamicIndicatorShareDrawing(){
        val music=Music(title="오늘 하루를 천천히 기록하는 아주 긴 한글 노래 제목",artist="이름이 아주 긴 아티스트 · 특별한 오후",durationMs=5000)
        for(name in EditorCatalog.cards.filter{it!="숨김"}){
            val m=music.copy(card=name);val layer=Layer(kind="music");val base=Artwork.layer(c,layer,m);assertTrue(base.width>100 && base.height>50);base.save("card-${name.replace(" ","-")}")
            if(name=="Progress Line"){
                val preview=base.copy(Bitmap.Config.ARGB_8888,true);val drawable=MusicIndicatorDrawable(name,base.width,base.height);drawable.setBounds(0,0,base.width,base.height);drawable.update(.5f,.55f,true,Color.CYAN);drawable.draw(Canvas(preview))
                val exported=Exporter.overlay(layer,base,5000,m,FloatArray(101){1f},Style(beat=.55f,reaction="Music Dot",auraColor=Color.CYAN)).getBitmap(2500000)
                assertEquals("Preview/export indicator pixel parity",0.0,delta(preview,exported),0.0)
                val start=Exporter.overlay(layer,base,5000,m).getBitmap(0);val end=Exporter.overlay(layer,base,5000,m).getBitmap(4500000);assertTrue(delta(start,end)>.0001)
                preview.save("progress-dot-parity")
            }
        }
        for(name in EditorCatalog.text){val bitmap=Artwork.text(c,Layer(kind="text",type=name,text="기억해 둘 오늘의 순간\n아주 긴 한글 문장과 ABC 123",textLayoutVersion=1));assertEquals(960,bitmap.width);assertTrue(bitmap.height>120);bitmap.save("text-${name.replace(" ","-")}")}
    }
}
