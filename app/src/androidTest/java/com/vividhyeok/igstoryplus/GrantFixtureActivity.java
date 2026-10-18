package com.vividhyeok.igstoryplus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.provider.DocumentsContract;

public class GrantFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Uri uri=DocumentsContract.buildTreeDocumentUri("com.vividhyeok.igstoryplus.test.documents","root");
        grantUriPermission("com.vividhyeok.igstoryplus",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
