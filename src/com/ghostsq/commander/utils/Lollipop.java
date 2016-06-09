package com.ghostsq.commander.utils;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Lollipop {
    
    public static Intent getDocTreeIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    }
    
    public static String readlink( String path ) {
        try {
            return Os.readlink( path );
        } catch( ErrnoException e ) {}
        return null;
    }

}
