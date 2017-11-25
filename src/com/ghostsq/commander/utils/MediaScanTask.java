package com.ghostsq.commander.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class MediaScanTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "MediaScanTask";
    private Context ctx; 
    private File    folder;
    private boolean all = false;
    MediaScanTask( Context ctx, File folder, boolean all ) {
        this.ctx = ctx;
        this.folder = folder;
        this.all = all;
    }
    @Override
    protected Void doInBackground( Void... params ) {
        ArrayList<String> to_scan = new ArrayList<String>();
        collectFiles( folder, to_scan );
        if( to_scan.size() > 0 ) {
            String[] to_scan_a = new String[to_scan.size()];
            to_scan.toArray( to_scan_a );
            scanMedia( ctx, to_scan_a );
            Log.d( TAG, "scanMedia() finished" ); 
        }
        return null;
    }

    public static void scanMedia( final Context ctx, String[] to_scan_a ) {
        MediaScannerConnection.scanFile( ctx, to_scan_a, null, 
             new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted( String path, final Uri uri ) {
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

    private void collectFiles( File folder, List<String> to_scan ) {
        if( folder == null ) return;
        for( File f : folder.listFiles() ) {
            if( f == null ) continue;
            try {
                if( f.isDirectory() )
                    collectFiles( f, to_scan );
                else {
                    String ext  = Utils.getFileExt( f.getName() );
                    String mime = Utils.getMimeByExt( ext );
                    if( all || ( mime != null && ( 
                        mime.startsWith( "image/" ) || 
                        mime.startsWith( "audio/" ) || 
                        mime.startsWith( "video/" ) ||
                        mime.equals( "application/x-mpegurl" ) ) ) )
                        to_scan.add( f.getAbsolutePath() );
                }
            } catch( Exception e ) {}
        }
    }    
    
    // entry point
    public static void scanMedia( Context ctx, File folder, boolean all ) {
        MediaScanTask bg_scan = new MediaScanTask( ctx, folder, all );
        bg_scan.execute();
    }    

}

