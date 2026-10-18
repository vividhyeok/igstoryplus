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
    fun scale(l: Layer, phase: Float) = if(l.motion && l.kind!="text") 1f+.01f*sin(phase) else 1f
    fun drift(l: Layer, phase: Float) = if(l.motion && l.kind=="text") .004f*sin(phase) else 0f
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StyleEffect(private val style: Style, private val durationMs: Long, private val beats: FloatArray = floatArrayOf()) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : BaseGlShaderProgram(false, 1) {
        val program=GpuProgram()
        override fun configure(inputWidth: Int, inputHeight: Int)=Size(inputWidth,inputHeight)
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) { program.draw(inputTexId,style,Motion.phase(presentationTimeUs,durationMs),false,beatAt(beats,presentationTimeUs)) }
        override fun release() { program.release(); super.release() }
    }
}
fun beatAt(beats: FloatArray, timeUs: Long): Float = beats.getOrElse((timeUs/50000).toInt()){0f}
class GpuProgram {
    private val vertex="""attribute vec2 aPos; varying vec2 vTex; void main(){gl_Position=vec4(aPos,0.,1.);vTex=(aPos+1.)*.5;}"""
    private val fragment="""
        precision highp float;
        uniform sampler2D uTex;
        uniform float uPhase; uniform float uAmount; uniform float uMotion; uniform float uStyle; uniform float uFlip; uniform float uScope; uniform float uGlow;
        varying vec2 vTex;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        void main(){
            vec2 uv=vTex; if(uFlip>.5) uv.y=1.-uv.y;
            float edge=smoothstep(.16,.65,length((uv-.5)*vec2(1.,.8)))*uScope;
            float wave=sin(uPhase); float wave2=cos(uPhase*2.);
            if(uStyle<.5 && uMotion>.5) uv=(uv-.5)/(1.+.012*uAmount*(1.-cos(uPhase)))+.5;
            if(uStyle>3.5 && uStyle<4.5 && uMotion>.5){
                float angle=.003*uAmount*wave; mat2 r=mat2(cos(angle),-sin(angle),sin(angle),cos(angle));
                uv=r*(uv-.5)/1.012+.5+vec2(.004*wave,.003*wave2)*uAmount;
            }
            if(uStyle>5.5) uv+=edge*uAmount*.009*vec2(sin(uv.y*8.+uPhase),cos(uv.x*7.+uPhase));
            vec4 col=texture2D(uTex,clamp(uv,0.,1.));
            if(uStyle>4.5){
                vec4 total=col; float strength=edge*uAmount*(.008+.005*(uMotion>.5?wave:0.));
                for(int i=1;i<8;i++){float k=float(i)/7.; vec2 offset=(uv-.5)*strength*k; total+=texture2D(uTex,clamp(uv-offset,0.,1.));}
                col=total/8.;
            }
            if(uStyle>.5 && uStyle<2.5){
                vec2 shift=uMotion>.5?vec2(sin(uPhase),cos(uPhase))*.35:vec2(0.);
                float noise=hash(floor(uv*vec2(1080.,1920.))+shift)-.5;
                col.rgb+=noise*uAmount*(uStyle>1.5?.075:.035);
                if(uStyle>1.5) col.rgb=(col.rgb-.5)*(1.+.18*uAmount)+.5;
            }
            if(uStyle>2.5 && uStyle<3.5){
                vec2 grid=vec2(360.,640.); vec2 pixel=(floor(uv*grid)+.5)/grid;
                vec3 lo=texture2D(uTex,pixel).rgb; lo.r=texture2D(uTex,pixel+vec2(.0015,0.)*uAmount).r;
                col.rgb=mix(col.rgb,lo,uAmount*.75);
            }
            col.rgb+=uGlow*edge*.045;
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
    fun draw(texture: Int, s: Style, phase: Float, flip: Boolean, beat: Float=0f) {
        GLES20.glUseProgram(program); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTex"),0)
        fun uniform(n: String,v: Float)=GLES20.glUniform1f(GLES20.glGetUniformLocation(program,n),v)
        uniform("uPhase",if(s.motion)phase*s.speed.roundToInt().coerceIn(1,3) else 0f); uniform("uMotion",if(s.motion)1f else 0f)
        uniform("uAmount",s.amount); uniform("uScope",s.scope); uniform("uFlip",if(flip)1f else 0f); uniform("uGlow",s.beat*beat)
        uniform("uStyle",listOf("Simple","Grainy","Gritty","Lores","Handheld","Zoomblur","Flow Blur").indexOf(s.name).toFloat())
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
    @Volatile var style=Style()
        set(value){field=value;requestRender()}
    @Volatile var durationMs=5000L
    @Volatile var paused=false
    @Volatile var beat=0f
    @Volatile private var timeUs=0L
    fun showTime(time:Long,glow:Float){timeUs=time;beat=glow;requestRender()}
    private var bitmap: Bitmap?=null
    private var texture=0
    private var program: GpuProgram?=null
    private val start=System.nanoTime()
    private val ticker=android.os.Handler(android.os.Looper.getMainLooper())
    private val tick=object:Runnable{override fun run(){if(style.motion && !paused)requestRender();ticker.postDelayed(this,33)}}
    init { setEGLContextClientVersion(2); setRenderer(this);renderMode=RENDERMODE_WHEN_DIRTY }
    override fun onResume(){super.onResume();requestRender()}
    override fun onPause(){ticker.removeCallbacks(tick);super.onPause()}
    fun setPhoto(b: Bitmap) { queueEvent { bitmap=b; if(texture!=0) GLES20.glDeleteTextures(1,intArrayOf(texture),0); texture=upload(b) }; requestRender() }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) { program=GpuProgram(); texture=bitmap?.let{upload(it)}?:0 }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { GLES20.glViewport(0,0,width,height) }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(.06f,.06f,.07f,1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if(texture!=0) program?.draw(texture,style,Motion.phase(timeUs,durationMs),true,beat)
    }
}
// A pbuffer runs exactly the same fragment shader for still exports without a visible surface.
fun filteredBitmap(b: Bitmap, style: Style): Bitmap {
    val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY); val versions=IntArray(2); check(EGL14.eglInitialize(display,versions,0,versions,1))
    val configs=arrayOfNulls<android.opengl.EGLConfig>(1); val count=IntArray(1)
    EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,count,0)
    val ctx=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
    val surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,b.width,EGL14.EGL_HEIGHT,b.height,EGL14.EGL_NONE),0)
    try {
        check(EGL14.eglMakeCurrent(display,surface,surface,ctx)); GLES20.glViewport(0,0,b.width,b.height)
        val tex=upload(b); val program=GpuProgram(); program.draw(tex,style,0f,true)
        val buffer=ByteBuffer.allocateDirect(b.width*b.height*4); GLES20.glReadPixels(0,0,b.width,b.height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer)
        val raw=Bitmap.createBitmap(b.width,b.height,Bitmap.Config.ARGB_8888); buffer.rewind(); raw.copyPixelsFromBuffer(buffer)
        val out=Bitmap.createBitmap(raw,0,0,b.width,b.height,android.graphics.Matrix().apply{postScale(1f,-1f)},true)
        program.release(); GLES20.glDeleteTextures(1,intArrayOf(tex),0); return out
    } finally {
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,ctx); EGL14.eglTerminate(display)
    }
}
