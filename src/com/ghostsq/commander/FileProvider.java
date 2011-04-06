package com.ghostsq.commander;

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Images;

public class FileProvider extends ContentProvider {
    private static final String TAG = "FileProvider";
    public static final String URI_PREFIX = "content://com.ghostsq.commander.FileProvider";
    public static final String AUTHORITY = "com.ghostsq.commander.FileProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType( Uri uri ) {
        //Log.v( TAG, "getType( " + uri + " )" );
        String ext  = Utils.getFileExt( uri.getPath() );
        String mime = Utils.getMimeByExt( ext );
        return mime;
    }

    @Override
    public Cursor query(Uri uri, String[] as, String s, String[] as1, String s1) {
        //Log.v( TAG, "query( " + uri + " )" );
        if( uri.toString().startsWith( URI_PREFIX ) ) {
            MatrixCursor c = new MatrixCursor( new String[] 
                    { Images.Media.DATA, Images.Media.MIME_TYPE } );
            String path = uri.getPath();
            String mime = getType( uri );
            c.addRow( new String[] { path, mime } );
            return c;
        } else
            throw new RuntimeException( "Unsupported URI" );
    }
    
    @Override
    public ParcelFileDescriptor openFile( Uri uri, String mode ) throws FileNotFoundException {
        //Log.v( TAG, "openFile( " + uri + " )" );
        File file = new File( uri.getPath() );
        if( !file.exists() ) throw new FileNotFoundException();
        ParcelFileDescriptor parcel = ParcelFileDescriptor.open( file, ParcelFileDescriptor.MODE_READ_ONLY );
        return parcel;
    }

    @Override
    public int update( Uri uri, ContentValues contentvalues, String s, String[] as ) {
        return 0;
    }

    @Override
    public int delete( Uri uri, String s, String[] as ) {
        return 0;
    }

    @Override
    public Uri insert( Uri uri, ContentValues contentvalues ) {
        return null;
    }
}
