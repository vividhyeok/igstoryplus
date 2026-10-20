package com.vividhyeok.igstoryplus

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** A real IME editing surface, not a dialog. No setText during composition or full-layer rebuild. */
class CanvasTextEditor(private val activity: Activity, private val canvas: FrameLayout,
                       val draft: Layer, private val changed:(Layer)->Unit, private val finished: (Boolean,Layer)->Unit) {
    val input = object : androidx.appcompat.widget.AppCompatEditText(activity) {
        override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);if(hasWindowFocus && hasFocus())post{showKeyboard()}}
        override fun onDraw(c: Canvas) {
            // Same 960px reference layout and decoration as Artwork.text().
            c.save();c.translate(-scrollX.toFloat(),-scrollY.toFloat());Artwork.textDecoration(c,draft,height,layout);c.restore()
            if(draft.type=="외곽선"){val ink=paint.color;paint.style=Paint.Style.STROKE;paint.strokeWidth=7f;paint.color=Color.BLACK;super.onDraw(c);paint.style=Paint.Style.FILL;paint.color=ink}
            super.onDraw(c)
        }
    }
    private val layoutListener=View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_-> position() }
    init {
        input.apply {
            tag="canvas-text-input"; contentDescription="캔버스 텍스트 입력"
            setBackgroundColor(Color.TRANSPARENT); setPadding(40,32,40,32)
            setTextSize(TypedValue.COMPLEX_UNIT_PX,Artwork.textSize(draft)); includeFontPadding=true
            breakStrategy=android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
            setFallbackLineSpacing(false)
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setSingleLine(false); setSelectAllOnFocus(false); minWidth=0; minimumWidth=0
            setText(draft.text); setSelection(text?.length?:0)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { draft.text=s.toString();changed(draft);post { position() } }
                override fun afterTextChanged(s:Editable?) {}
            })
        }
        canvas.addView(input,FrameLayout.LayoutParams(960,-2,Gravity.TOP or Gravity.START))
        canvas.addOnLayoutChangeListener(layoutListener)
        refresh(); input.requestFocus()
        input.post { showKeyboard() }
    }
    private fun showKeyboard(){if(input.isAttachedToWindow && input.hasWindowFocus())(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}
    fun refresh() {
        changed(draft)
        input.typeface=Artwork.typeface(draft)
        input.setTextColor(if(draft.type=="Whisper") (draft.color and 0x00ffffff) or (220 shl 24) else Artwork.textInk(draft))
        input.letterSpacing=Artwork.letterSpacing(draft)
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX,Artwork.textSize(draft))
        input.gravity=Gravity.TOP or when(draft.align){0->Gravity.START;2->Gravity.END;else->Gravity.CENTER_HORIZONTAL}
        input.setShadowLayer(if(draft.type=="Echo Type")2f else if(draft.type=="Offset Type").01f else 0f,
            if(draft.type=="Offset Type" || draft.type=="Echo Type")2+draft.emphasis*7 else 0f,
            if(draft.type=="Offset Type" || draft.type=="Echo Type")2+draft.emphasis*5 else 0f,
            Color.argb((draft.emphasis*100).toInt(),Color.red(draft.color),Color.green(draft.color),Color.blue(draft.color)))
        if(draft.type=="Film Caption")input.setShadowLayer(4f,0f,2f,Color.BLACK)
        if(draft.type=="네온")input.setShadowLayer(18f,0f,0f,draft.color)
        input.requestLayout();input.invalidate();input.post { position() }
    }
    private fun position() {
        if(canvas.width==0)return
        val s=draft.width*canvas.width/960f*draft.scale
        input.pivotX=480f;input.pivotY=input.height/2f
        input.scaleX=s;input.scaleY=s;input.rotation=draft.rotation
        // Focused viewport centers text when IME reduces the available canvas, without changing its saved position.
        val cy=(draft.y*canvas.height).coerceIn(input.height*s/2, (canvas.height-input.height*s/2).coerceAtLeast(input.height*s/2))
        input.translationX=draft.x*canvas.width-480f
        input.translationY=cy-input.height/2f
    }
    fun finish(commit:Boolean) {
        draft.text=input.text.toString()
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
        canvas.removeOnLayoutChangeListener(layoutListener);canvas.removeView(input);auxiliary.forEach{(it.parent as? ViewGroup)?.removeView(it)}
        finished(commit,draft)
    }
    private val auxiliary=mutableListOf<View>()
    fun auxiliary(view:View){auxiliary.add(view)}
    fun insertEmoji(emoji:String){
        val text=input.text?:return;val start=input.selectionStart.coerceAtLeast(0);val end=input.selectionEnd.coerceAtLeast(start)
        android.view.inputmethod.BaseInputConnection.removeComposingSpans(text)
        text.replace(start,end,emoji);input.setSelection(start+emoji.length)
    }
    fun keyboard(show:Boolean){val manager=activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;if(show)showKeyboard() else manager.hideSoftInputFromWindow(input.windowToken,0)}
}
