package com.vividhyeok.igstoryplus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract

/** A real provider-owned URI grant, confined to the test APK. */
class GrantFixtureActivity:Activity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val uri=DocumentsContract.buildTreeDocumentUri("com.vividhyeok.igstoryplus.test.documents","root")
        grantUriPermission("com.vividhyeok.igstoryplus",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        finish()
    }
}
