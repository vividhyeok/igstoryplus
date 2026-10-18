package com.vividhyeok.igstoryplus

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsProvider
import android.provider.DocumentsContract.Document
import java.io.File

/** Only installed in the instrumentation APK; never bundled as user emoji. */
class FixtureDocuments:DocumentsProvider(){
    private val columns=arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS,Document.COLUMN_SIZE)
    private fun file(id:String):File { val root=File(context!!.filesDir,"png-fixture").apply{mkdirs()};return if(id=="root")root else File(root,id) }
    override fun onCreate()=true
    override fun queryRoots(projection:Array<out String>?):Cursor=MatrixCursor(arrayOf("root_id","document_id","title","flags")).apply{addRow(arrayOf("fixture","root","Test PNG",0))}
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor=MatrixCursor(columns).apply{val f=file(documentId);if(f.exists())addRow(arrayOf(documentId,f.name,if(f.isDirectory)Document.MIME_TYPE_DIR else "image/png",0,f.length()))}
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor=MatrixCursor(columns).apply{file(parentDocumentId).listFiles()?.forEach{addRow(arrayOf(it.name,it.name,if(it.isDirectory)Document.MIME_TYPE_DIR else "image/png",0,it.length()))}}
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?)=ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String { require(parentDocumentId=="root");file(displayName).createNewFile();return displayName }
    override fun deleteDocument(documentId:String){check(file(documentId).delete())}
    override fun renameDocument(documentId:String,displayName:String):String { check(file(documentId).renameTo(file(displayName)));return displayName }
    override fun isChildDocument(parentDocumentId:String,documentId:String)=parentDocumentId=="root" && documentId!="root"
}
