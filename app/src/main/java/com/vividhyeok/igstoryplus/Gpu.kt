package com.vividhyeok.igstoryplus

import android.content.Context
import android.graphics.Bitmap
import android.opengl.*
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

object Motion {
    fun phase(timeUs: Long, durationMs: Long) = (timeUs % (durationMs*1000)).toFloat()/(durationMs*1000)*2f*PI.toFloat()
    fun scale(l: Layer, phase: Float,beat:Float=0f) = (if(l.motion && (l.kind!="text" || l.motionStyle=="숨쉬기")) 1f+.025f*l.motionAmount.coerceIn(0f,1f)*sin(phase) else 1f)*(1+if(l.beatMotion).035f*beat.coerceIn(0f,1f)else 0f)
    fun drift(l: Layer, phase: Float) = if(l.motion && (l.kind=="text" || l.motionStyle=="떠오르기")) .01f*l.motionAmount.coerceIn(0f,1f)*sin(phase) else 0f
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StyleEffect(private val style: Style, private val durationMs: Long, private val beats: FloatArray = floatArrayOf(),private val foreground:Bitmap?=null,private val pose:PhotoPose=PhotoPose(),private val videoPose:Boolean=false) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : BaseGlShaderProgram(false, 1) {
        val program=GpuProgram()
        private var photo=0;private var pw=1f;private var ph=1f
        override fun configure(inputWidth: Int, inputHeight: Int):Size {
            if(foreground!=null && photo==0)photo=upload(foreground)
            foreground?.let{val scale=min(inputWidth.toFloat()/it.width,inputHeight.toFloat()/it.height);pw=it.width*scale/inputWidth;ph=it.height*scale/inputHeight}
            return Size(inputWidth,inputHeight)
        }
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) { program.draw(inputTexId,style,Motion.phase(presentationTimeUs,durationMs),false,beatAt(beats,presentationTimeUs),photo=photo,pose=pose,photoWidth=pw,photoHeight=ph,framePose=videoPose) }
        override fun release() { if(photo!=0)GLES20.glDeleteTextures(1,intArrayOf(photo),0);program.release(); super.release() }
    }
}
fun beatAt(beats: FloatArray, timeUs: Long): Float {
    if(timeUs<0)return 0f
    val index=(timeUs/50000).toInt()
    val a=beats.getOrElse(index){0f};val b=beats.getOrElse(index+1){a}
    return a+(b-a)*(timeUs%50000)/50000f
}
class GpuProgram {
    private val vertex="""attribute vec2 aPos; varying vec2 vTex; void main(){gl_Position=vec4(aPos,0.,1.);vTex=(aPos+1.)*.5;}"""
    private val fragment="""
        precision highp float;
        uniform sampler2D uTex; uniform sampler2D uPhoto;
        uniform float uCompose; uniform float uFramePose; uniform vec2 uPhotoSize; uniform vec2 uPosition; uniform float uScale; uniform float uAngle;
        uniform vec2 uCenter; uniform float uRegion;
        uniform float uReaction; uniform vec3 uAura;uniform float uReactionTarget;
        uniform float uPhase; uniform float uAmount; uniform float uMotion; uniform float uStyle; uniform float uFlip; uniform float uScope; uniform float uGlow;
        varying vec2 vTex;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        vec2 localPhoto(vec2 uv){vec2 q=(uv-uPosition)*vec2(1.,1.7777778);float a=-uAngle;q=mat2(cos(a),sin(a),-sin(a),cos(a))*q;return q/(uPhotoSize*vec2(1.,1.7777778)*uScale)+.5;}
        vec4 readAt(vec2 uv){
            if(uFramePose>.5){uv=localPhoto(uv);if(uv.x<0.||uv.x>1.||uv.y<0.||uv.y>1.)return vec4(0.,0.,0.,1.);}
            vec2 bgUv=(uReaction>.5&&uReaction<1.5&&uFramePose<.5)?(uv-.5)/(1.+uGlow*.016)+.5:uv;
            vec4 bg=texture2D(uTex,clamp(bgUv,0.,1.));
            if(uReaction>.5&&uReaction<1.5&&uFramePose<.5)bg=mix(bg,(texture2D(uTex,clamp(bgUv+vec2(.004,0.),0.,1.))+texture2D(uTex,clamp(bgUv-vec2(.004,0.),0.,1.)))*.5,uGlow*.4);
            if(uCompose>.5){vec2 q=localPhoto(uv);if(q.x>=0.&&q.x<=1.&&q.y>=0.&&q.y<=1.)bg=texture2D(uPhoto,vec2(q.x,uFlip>.5?q.y:1.-q.y));}
            return bg;
        }
        void main(){
            vec2 uv=vTex;if(uFlip>.5)uv.y=1.-uv.y;
            float edge=(uRegion<.5?1.:smoothstep(uRegion>1.5?.08:.16,uRegion>1.5?.48:.65,length((uv-uCenter)*vec2(1.,.8))))*uScope;
            float phase=uPhase;
            if(uStyle>3.5&&uStyle<4.5&&uMotion>.5){
                float angle=.006*uAmount*(.62*sin(phase)+.25*sin(3.*phase)+.13*sin(5.*phase));
                vec2 offset=vec2(.006*(.55*sin(phase)+.3*sin(3.*phase)+.15*sin(7.*phase)),.004*(.7*cos(2.*phase)+.3*sin(5.*phase)))*uAmount;
                uv=mat2(cos(angle),sin(angle),-sin(angle),cos(angle))*(uv-.5)+.5+offset;
            }
            vec4 col=readAt(uv);float lum=dot(col.rgb,vec3(.2126,.7152,.0722));
            float fine=0.;float coarse=0.;
            if((uStyle>.5&&uStyle<2.5)||(uStyle>7.5&&uStyle<8.5)){
                float grainTime=uMotion>.5?.5+.5*sin(phase):0.;
                vec2 fineCell=floor(uv*vec2(1080.,1920.));vec2 coarseCell=floor(uv*vec2(540.,960.));
                fine=mix(hash(fineCell),hash(fineCell+vec2(31.,71.)),grainTime)-.5;
                coarse=mix(hash(coarseCell),hash(coarseCell+vec2(59.,17.)),grainTime)-.5;
            }
            if(uStyle>.5&&uStyle<1.5)col.rgb+=fine*uAmount*.09*(.35+1.4*sqrt(max(0.,lum*(1.-lum))));
            if(uStyle>1.5&&uStyle<2.5){
                vec3 local=(readAt(uv+vec2(.0018,0.)).rgb+readAt(uv-vec2(.0018,0.)).rgb+readAt(uv+vec2(0.,.001)).rgb+readAt(uv-vec2(0.,.001)).rgb)*.25;
                col.rgb+=(col.rgb-local)*uAmount*.55;
                col.rgb=(col.rgb-.5)*(1.+.25*uAmount)+.5+(fine*.045+coarse*.065)*uAmount*(1.-.55*lum);
            }
            if(uStyle>2.5&&uStyle<3.5){
                vec2 grid=vec2(240.,426.);vec2 pixel=(floor(uv*grid)+.5)/grid;
                vec3 low=readAt(pixel).rgb;low.r=readAt(pixel+vec2(.0022,0.)*uAmount).r;low.b=readAt(pixel-vec2(.0012,0.)*uAmount).b;
                low=floor(low*56.+.5)/56.;col.rgb=mix(col.rgb,low,uAmount*.8);
            }
            if(uStyle>3.5&&uStyle<4.5)col.rgb=mix(col.rgb,readAt(uv+vec2(.002,.0007)*uAmount).rgb,.22*uAmount*edge);
            if(uStyle>4.5&&uStyle<5.5){
                vec4 sum=col*2.;float weight=2.;
                for(int i=1;i<=5;i++){float k=float(i)/5.;float w=1.-k*.6;sum+=readAt(uv-(uv-uCenter)*(.065*uAmount*edge)*(1.+.12*sin(phase))*k)*w;weight+=w;}
                col=sum/weight;
            }
            if(uStyle>5.5&&uStyle<6.5){
                vec2 flow=normalize(vec2(.85+.15*cos(uv.y*6.+phase),.32*sin(uv.x*4.+phase)))*.028*uAmount*edge;
                vec4 sum=col*2.;for(int i=0;i<5;i++)sum+=readAt(uv+flow*(float(i)-2.)/2.);col=sum/7.;
            }
            if(uStyle>6.5&&uStyle<7.5){
                vec2 drag=vec2(.94,.34)*.042*uAmount*(.35+.65*edge)*(1.+.15*sin(phase));vec4 sum=col*3.;float weight=3.;
                for(int i=1;i<=5;i++){float w=1.-float(i)*.12;sum+=readAt(uv-drag*float(i)/5.)*w;weight+=w;}col=sum/weight;
            }
            if(uStyle>7.5&&uStyle<8.5){
                vec3 soft=(readAt(uv+vec2(.001,0.)).rgb+readAt(uv-vec2(.001,0.)).rgb)*.5;
                col.rgb=mix(col.rgb,soft,.22*uAmount);
                float dose=uAmount*(.035+.08*(1.-lum)*(1.-lum));
                col.rgb+=fine*dose+vec3(coarse,-coarse*.3,coarse*.6)*dose*.35;
            }
            if(uStyle>8.5&&uStyle<9.5){vec2 offset=vec2(.006,.0018)*uAmount*(1.+.2*sin(phase));col.rgb=mix(col.rgb,readAt(uv-offset).rgb,.21*uAmount*(.3+.7*edge));}
            if(uStyle>9.5&&uStyle<10.5){vec3 clean=(col.rgb-.5)*1.09+.52;col.rgb=mix(col.rgb,clean,uAmount);}
            if(uStyle>10.5&&uStyle<11.5){vec3 warm=pow(max(col.rgb,vec3(0.)),vec3(.94,1.01,1.10));warm=(warm-.5)*1.06+.51;col.rgb=mix(col.rgb,warm,uAmount);}
            if(uStyle>11.5&&uStyle<12.5){vec3 fade=col.rgb*.84+vec3(.09,.075,.07);col.rgb=mix(col.rgb,fade,uAmount);}
            if(uStyle>12.5&&uStyle<13.5){vec3 mono=vec3((lum-.5)*1.14+.5);col.rgb=mix(col.rgb,mono,uAmount);}
            if(uStyle>13.5){vec3 cool=col.rgb*vec3(.94,1.01,1.09)+vec3(-.005,0.,.015);cool=(cool-.5)*1.05+.5;col.rgb=mix(col.rgb,cool,uAmount);}
            float background=1.;if(uCompose>.5){vec2 q=localPhoto(uv);if(q.x>=0.&&q.x<=1.&&q.y>=0.&&q.y<=1.)background=0.;}
            if(uFramePose>.5)background=0.;
            float target=(uReactionTarget>.5&&uReactionTarget<1.5)?background:1.;
            if(uReactionTarget>1.5)background=1.;
            if(uReaction<.5)col.rgb+=uGlow*edge*target*.10*(vec3(1.)-col.rgb);
            if(uReaction>.5&&uReaction<1.5)col.rgb+=uGlow*edge*background*.09*(vec3(1.)-col.rgb);
            if(uReaction>1.5&&uReaction<2.5)col.rgb=mix(col.rgb,uAura,uGlow*edge*background*.13);
            gl_FragColor=vec4(clamp(col.rgb,0.,1.),col.a);
        }
    """.trimIndent()
    private fun compile(type: Int, src: String): Int {
        val s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,src); GLES20.glCompileShader(s)
        val ok=IntArray(1); GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0); check(ok[0]!=0){GLES20.glGetShaderInfoLog(s)}; return s
    }
    private val program=GLES20.glCreateProgram().also {
        val v=compile(GLES20.GL_VERTEX_SHADER,vertex); val f=compile(GLES20.GL_FRAGMENT_SHADER,fragment)
        GLES20.glAttachShader(it,v); GLES20.glAttachShader(it,f); GLES20.glLinkProgram(it); GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        val ok=IntArray(1); GLES20.glGetProgramiv(it,GLES20.GL_LINK_STATUS,ok,0); check(ok[0]!=0){GLES20.glGetProgramInfoLog(it)}
    }
    private val vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)); position(0) }
    fun draw(texture: Int, s: Style, phase: Float, flip: Boolean, beat: Float=0f,photo:Int=0,pose:PhotoPose=PhotoPose(),photoWidth:Float=1f,photoHeight:Float=1f,framePose:Boolean=false) {
        val applied=if(framePose && !s.videoFilters)s.copy(name="Simple",amount=0f,motion=false)else s
        GLES20.glUseProgram(program); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTex"),0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,if(photo!=0)photo else texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uPhoto"),1)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uPhotoSize"),photoWidth,photoHeight)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uPosition"),pose.x,if(flip)pose.y else 1-pose.y)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uCenter"),applied.centerX,if(flip)applied.centerY else 1-applied.centerY)
        fun uniform(n: String,v: Float)=GLES20.glUniform1f(GLES20.glGetUniformLocation(program,n),v)
        uniform("uPhase",if(applied.motion)phase*applied.speed.roundToInt().coerceIn(1,3) else 0f); uniform("uMotion",if(applied.motion)1f else 0f)
        uniform("uReaction",when(applied.reaction){"Ambient Breathing"->1f;"Album Aura"->2f;"Music Dot"->3f;else->0f})
        uniform("uReactionTarget",when(applied.reactionTarget){"배경만"->1f;"원본 화면 전체"->2f;else->0f})
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program,"uAura"),android.graphics.Color.red(applied.auraColor)/255f,android.graphics.Color.green(applied.auraColor)/255f,android.graphics.Color.blue(applied.auraColor)/255f)
        uniform("uAmount",applied.amount); uniform("uScope",applied.scope); uniform("uFlip",if(flip)1f else 0f); uniform("uGlow",applied.beat*beat)
        uniform("uFramePose",if(framePose)1f else 0f);uniform("uCompose",if(photo!=0)1f else 0f);uniform("uScale",pose.scale);uniform("uAngle",pose.rotation*PI.toFloat()/180*(if(flip)1 else -1));uniform("uRegion",when(applied.region){"전체"->0f;"배경 중심"->2f;else->1f})
        uniform("uStyle",EditorCatalog.photoCode(applied.name).toFloat())
        val a=GLES20.glGetAttribLocation(program,"aPos"); GLES20.glEnableVertexAttribArray(a); GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,0,vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GLES20.glDisableVertexAttribArray(a)
    }
    fun release()=GLES20.glDeleteProgram(program)
}
fun upload(b: Bitmap): Int {
    val ids=IntArray(1); GLES20.glGenTextures(1,ids,0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0])
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,b,0); return ids[0]
}
class LivePhoto(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    @Volatile var frameReady=false;private set
    @Volatile private var awaitingPhoto=false
    var onPhotoReady:(()->Unit)?=null
    fun expectPhoto(){awaitingPhoto=true;frameReady=false}
    @Volatile var pose=PhotoPose()
        set(value){field=value;requestRender()}
    private var foreground:Bitmap?=null
    private var photoTexture=0
    private var photoWidth=1f
    private var photoHeight=1f
    @Volatile var style=Style()
        set(value){field=value;requestRender()}
    @Volatile var durationMs=5000L
    @Volatile var beat=0f
    @Volatile private var timeUs=0L
    fun showTime(time:Long,glow:Float){timeUs=time;beat=glow;requestRender()}
    private var bitmap: Bitmap?=null
    private var texture=0
    private var program: GpuProgram?=null
    init { setEGLContextClientVersion(2); setRenderer(this);renderMode=RENDERMODE_WHEN_DIRTY;holder.setFixedSize(540,960) }
    override fun onResume(){super.onResume();requestRender()}
    override fun onPause(){super.onPause()}
    fun setPhoto(b: Bitmap,photo:Bitmap?=null) { queueEvent { bitmap=b;foreground=photo;if(texture!=0) GLES20.glDeleteTextures(1,intArrayOf(texture),0); texture=upload(b);if(photoTexture!=0)GLES20.glDeleteTextures(1,intArrayOf(photoTexture),0);photoTexture=photo?.let{upload(it)}?:0;photo?.let{val s=min(b.width.toFloat()/it.width,b.height.toFloat()/it.height);photoWidth=it.width*s/b.width;photoHeight=it.height*s/b.height};awaitingPhoto=false;frameReady=false }; requestRender() }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) { program=GpuProgram(); texture=bitmap?.let{upload(it)}?:0;photoTexture=foreground?.let{upload(it)}?:0 }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { GLES20.glViewport(0,0,width,height) }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(.06f,.06f,.07f,1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if(texture!=0){program?.draw(texture,style,Motion.phase(timeUs,durationMs),true,beat,photoTexture,pose,photoWidth,photoHeight);if(!frameReady && !awaitingPhoto){GLES20.glFinish();frameReady=true;post{onPhotoReady?.invoke()}}}
    }
}
// A pbuffer runs exactly the same fragment shader for still exports without a visible surface.
fun filteredBitmap(b: Bitmap, style: Style, foreground:Bitmap?=null,pose:PhotoPose=PhotoPose()): Bitmap {
    return filteredBitmaps(b,listOf(style),foreground,pose).single()
}
fun filteredBitmaps(b:Bitmap,styles:List<Style>,foreground:Bitmap?=null,pose:PhotoPose=PhotoPose(),beat:Float=0f,phase:Float=0f,video:Boolean=false,ready:(Int,Bitmap)->Unit={_,_->}):List<Bitmap>{
    val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY); val versions=IntArray(2); check(EGL14.eglInitialize(display,versions,0,versions,1))
    val configs=arrayOfNulls<android.opengl.EGLConfig>(1); val count=IntArray(1)
    EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,count,0)
    val ctx=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
    val surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,b.width,EGL14.EGL_HEIGHT,b.height,EGL14.EGL_NONE),0)
    try {
        check(EGL14.eglMakeCurrent(display,surface,surface,ctx)); GLES20.glViewport(0,0,b.width,b.height)
        val tex=upload(b); val photo=foreground?.let{upload(it)}?:0;val scale=foreground?.let{min(b.width.toFloat()/it.width,b.height.toFloat()/it.height)}?:1f
        val program=GpuProgram()
        val results=styles.mapIndexed{index,style->
        check(!Thread.currentThread().isInterrupted)
        program.draw(tex,style,phase,true,beat=beat,photo=photo,pose=pose,photoWidth=foreground?.let{it.width*scale/b.width}?:1f,photoHeight=foreground?.let{it.height*scale/b.height}?:1f,framePose=video)
        val buffer=ByteBuffer.allocateDirect(b.width*b.height*4); GLES20.glReadPixels(0,0,b.width,b.height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer)
        val raw=Bitmap.createBitmap(b.width,b.height,Bitmap.Config.ARGB_8888); buffer.rewind(); raw.copyPixelsFromBuffer(buffer)
        val out=Bitmap.createBitmap(raw,0,0,b.width,b.height,android.graphics.Matrix().apply{postScale(1f,-1f)},true)
        ready(index,out);out
        }
        program.release(); GLES20.glDeleteTextures(1,intArrayOf(tex),0);if(photo!=0)GLES20.glDeleteTextures(1,intArrayOf(photo),0); return results
    } finally {
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,ctx); EGL14.eglTerminate(display)
    }
}
