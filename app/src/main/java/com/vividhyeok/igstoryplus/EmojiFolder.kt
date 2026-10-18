package com.vividhyeok.igstoryplus

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object EmojiFolder {
    fun scan(c: Context,uri: Uri): List<DocumentFile> {
        val tree=DocumentFile.fromTreeUri(c,uri)?:error("폴더 접근 불가")
        check(tree.exists() && tree.canRead()){ "폴더가 없거나 접근 권한이 없습니다" }
        return tree.listFiles().filter{it.isFile && (it.type=="image/png" || it.name?.endsWith(".png",true)==true)}
    }
}
