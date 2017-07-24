package com.ghostsq.commander.adapters;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Utils;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MediaScanEngine extends Engine implements MediaScannerConnection.MediaScannerConnectionClient {
    private static final String TAG = "MediaScanEngine";
    private MediaScannerConnection msc;
    private Context ctx;
    private ContentResolver cr;
    private File    folder;
    private boolean all = false, rec = true;
    private FileItem[] to_scan_a;
    private int     count = 0, num = 0;

    class FileItem {
        public String path, mime;
        FileItem( String path, String mime ) {
            this.path = path; 
            this.mime = mime;
        }
    }
    
    public MediaScanEngine( Context ctx, File folder, boolean all, boolean rec ) {
        this.ctx = ctx;
        this.folder = folder;
        this.all = all;
        this.rec = rec;
        this.cr = ctx.getContentResolver();
    }

    @Override
    public void run() {
        sendProgress( "", Commander.OPERATION_IN_PROGRESS, -1 );
        deleteMissedEntries( folder.getAbsolutePath() );
        ArrayList<FileItem> to_scan = new ArrayList<FileItem>();
        collectFiles( folder, to_scan, 0 );
        num = to_scan.size();
        if( num > 0 ) {
            to_scan_a = new FileItem[num];
            to_scan.toArray( to_scan_a );
            msc = new MediaScannerConnection( ctx, this );
            msc.connect();
            synchronized( this ) {
                while( !stop ) {
                    try {
                        wait( 1000 );
                    } catch( InterruptedException e ) {
                        stop = true;
                    }
                }
            }
            sendReport( count + " files were scanned" );
        }
    }

    private void deleteMissedEntries( String dir ) {
        Uri ec_uri = MediaStore.Files.getContentUri( "external" );
        String selection = MediaStore.MediaColumns.DATA + " like ? ";
        String[] selectionParams = new String[1];
        selectionParams[0] = dir + "%";
        final String[] projection = {
                 MediaStore.MediaColumns._ID,
                 MediaStore.MediaColumns.DATA,
                 MediaStore.MediaColumns.DATE_MODIFIED,
                 MediaStore.MediaColumns.MIME_TYPE,
                 MediaStore.MediaColumns.SIZE,
                 MediaStore.MediaColumns.TITLE
        };
        
        Cursor cursor = cr.query( ec_uri, projection, selection, selectionParams, null );
        if( cursor == null ) return;
        final int num = cursor.getCount(); 
        if( num <= 0 ) return;
        int count = 0;
        final int ici = cursor.getColumnIndex( MediaStore.MediaColumns._ID );
        final int pci = cursor.getColumnIndex( MediaStore.MediaColumns.DATA );
        cursor.moveToFirst();
        do {
            count++;
            String path = null;
            Uri e_uri = null;
            try {
                path = cursor.getString( pci );
                if( !Utils.str( path ) ) continue;
                File f = new File( path );
                if( !this.rec && !f.getParentFile().equals( this.folder ) )
                    continue;
                if( f.exists() ) continue;
                e_uri = MediaStore.Files.getContentUri( "external", cursor.getLong( ici ) );
                if( e_uri == null ) continue;
                String rep = ctx.getString( R.string.deleting, f.getName() );
                sendProgress( rep, count * 100 / num );
                cr.delete( e_uri, null, null );
            } catch( Exception e ) {
                Log.e( TAG, "Can't delete content entry " + e_uri + ", file: " + path );
            }
        } while( cursor.moveToNext() );
        cursor.close();
    }    
    
    private void collectFiles( File folder, List<FileItem> to_scan, int lvl ) {
        if( folder == null ) return;
        File[] files = folder.listFiles();
        if( files == null ) return;
        int num = files.length;
        for( int fi = 0; fi < num; fi++ ) {
            File f = files[fi];
            if( f == null ) continue;
            try {
                if( f.isDirectory() ) {
                    if( this.rec )
                        collectFiles( f, to_scan, lvl+1 );
                }
                else {
                    String fn = f.getName();
                    if( MediaStore.MEDIA_IGNORE_FILENAME.equals( fn ) ) continue;
                    String ext  = Utils.getFileExt( fn );
                    String mime = Utils.getMimeByExt( ext );
                    if( all || ( mime != null && ( mime.startsWith( "image/" ) || 
                            mime.startsWith( "audio/" ) || mime.startsWith( "video/" ) ) ) ) {
                        to_scan.add( new FileItem( f.getAbsolutePath(), mime ) );
                    }
                }
                if( lvl == 0 ) sendProgress( f.getName(), fi * 100 / num );
            } catch( Exception e ) {}
        }
    }    

    private final boolean scanNextFile() {
        if( count < num ) {
            FileItem fi = to_scan_a[count++];
            msc.scanFile( fi.path, fi.mime );
            return true;
        } else
            return false;
    }

    @Override
    public void onMediaScannerConnected() {
        scanNextFile();
    } 

    @Override
    public void onScanCompleted( final String path, final Uri uri ) {
        if( uri == null )
            Log.w( TAG, "Uri is null for " + path );
        else {
            //Log.v( TAG, "Scan completed: " + path + " " + uri.toString() );
            
            sendProgress( path, count * 100 / num );
            File f = new File( path );
            if( f.isFile() && f.length() == 0 ) {
                if( cr.delete( uri, null, null ) > 0 ) {
                    Log.w( "scanMedia()", "Deleteing " + path );
                    f.delete();
                }
            }
        }
        if( stop || !scanNextFile() ) {
            msc.disconnect();
            synchronized( this ) {
                stop = true;
                notify();
            }
            return;
        }
    }
}

