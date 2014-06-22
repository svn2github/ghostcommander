package com.ghostsq.commander.utils;

import java.io.File;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

public class ForwardCompat
{
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static void disableOverScroll( View view ) {
        view.setOverScrollMode( View.OVER_SCROLL_NEVER );
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static void setFullPermissions( File file ) {
        file.setWritable( true, false );
        file.setReadable( true, false );
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static void smoothScrollToPosition( ListView flv, int pos ) {
        flv.smoothScrollToPosition( pos );
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static Drawable getLogo( PackageManager pm, ApplicationInfo pai ) {
        return pai.logo == 0 ? null : pm.getApplicationLogo( pai );
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static void scanMedia( final Context ctx, String[] to_scan_a ) {
        MediaScannerConnection.scanFile( ctx, to_scan_a, null, 
             new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted( final String path, final Uri uri ) {
                    File f = new File( path );
                    if( f.isFile() && f.length() == 0 ) {
                        if( ctx.getContentResolver().delete( uri, null, null ) > 0 ) {
                            Log.w( "scanMedia()", "Deleteing " + path );
                            f.delete();
                        }
                    }
                } 
             } );                    
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static File getExternalFilesDir( Context ctx ) { 
        return ctx.getExternalFilesDir( null );
    }
}
