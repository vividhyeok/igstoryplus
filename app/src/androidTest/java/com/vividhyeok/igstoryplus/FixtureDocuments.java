package com.vividhyeok.igstoryplus;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsProvider;
import android.provider.DocumentsContract.Document;
import java.io.File;
import java.io.FileNotFoundException;

/** Only in test APK. Java keeps its separate provider process independent of target Kotlin runtime. */
public class FixtureDocuments extends DocumentsProvider {
    private final String[] columns={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS,Document.COLUMN_SIZE};
    private File file(String id){File root=new File(getContext().getFilesDir(),"png-fixture");root.mkdirs();return id.equals("root")?root:new File(root,id);}
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection){MatrixCursor c=new MatrixCursor(new String[]{"root_id","document_id","title","flags"});c.addRow(new Object[]{"fixture","root","Test PNG",0});return c;}
    private void add(MatrixCursor c,String id,File f){c.addRow(new Object[]{id,f.getName(),f.isDirectory()?Document.MIME_TYPE_DIR:"image/png",0,f.length()});}
    @Override public Cursor queryDocument(String id,String[] projection){MatrixCursor c=new MatrixCursor(columns);File f=file(id);if(f.exists())add(c,id,f);return c;}
    @Override public Cursor queryChildDocuments(String id,String[] projection,String order){MatrixCursor c=new MatrixCursor(columns);File[] fs=file(id).listFiles();if(fs!=null)for(File f:fs)add(c,f.getName(),f);return c;}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));}
    @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{try{if(!parent.equals("root"))throw new IllegalArgumentException();file(name).createNewFile();return name;}catch(java.io.IOException e){throw new FileNotFoundException(e.toString());}}
    @Override public void deleteDocument(String id)throws FileNotFoundException{if(!file(id).delete())throw new FileNotFoundException(id);}
    @Override public String renameDocument(String id,String name)throws FileNotFoundException{if(!file(id).renameTo(file(name)))throw new FileNotFoundException(id);return name;}
    @Override public boolean isChildDocument(String parent,String id){return parent.equals("root")&&!id.equals("root");}
}
