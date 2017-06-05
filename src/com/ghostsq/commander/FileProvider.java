package com.ghostsq.commander;

import java.io.File;
import java.io.FileNotFoundException;

import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.adapters.SAFAdapter;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

public class FileProvider extends ContentProvider {
    private static final String TAG = "FileProvider";
    public static final String URI_PREFIX = "content://com.ghostsq.commander";
    public static final String AUTHORITY_ = "com.ghostsq.commander";

    public final static Uri makeURI( String type, Uri u ) {
        Uri.Builder builder = Uri.parse( URI_PREFIX ).buildUpon();
        String us = u.toString();
        builder.appendQueryParameter( type, ForwardCompat.toBase64( us.getBytes(), Base64.URL_SAFE ) );
        return builder.build(); 
    }
    
    private final static Uri getEnclosedUri( Uri uri, String type ) {
        String bs = uri.getQueryParameter( type );
        if( !Utils.str( bs ) ) return null;
        byte[] ub = ForwardCompat.fromBase64( bs, Base64.URL_SAFE );
        if( ub == null ) {
            Log.e( TAG, "Bad base64 input string" );
            return null;
        }
        String us = new String( ub );
        Log.d( TAG, "Got enclosed (" + type + ") URI: " + us );
        return Uri.parse( us );
    }
    
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType( Uri uri ) {
        Uri saf_u = getEnclosedUri( uri, "SAF" );
        if( saf_u != null ) {
            return SAFAdapter.getMime( getContext(), saf_u );
        }
        String ext  = Utils.getFileExt( uri.getPath() );
        String mime = Utils.getMimeByExt( ext );
        return mime;
    }

    @Override
    public Cursor query( Uri uri, String[] as, String s, String[] sa, String so ) {
        Log.v( TAG, "query( " + uri + " )" );
        if( !uri.toString().startsWith( URI_PREFIX ) )
            throw new RuntimeException( "Unsupported URI" );
        Uri saf_u = getEnclosedUri( uri, "SAF" );
        if( saf_u != null ) {
            return getContext().getContentResolver().query( saf_u, as, s, sa, so );
        }
        if( as == null || as.length == 0) {
            as = new String [] {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE };
        } 
        MatrixCursor c = new MatrixCursor( as );
        MatrixCursor.RowBuilder row = c.newRow();
        File f = new File( uri.getPath() );
        if( !f.exists() || !f.isFile() )
            throw new RuntimeException( "No file name specified: " + uri );
        
        for( String col : as ) {
            if( MediaStore.MediaColumns.DATA.equals( col ) ) {
                row.add( f.getAbsolutePath() );
            } else if( MediaStore.MediaColumns.MIME_TYPE.equals( col ) ) {
                row.add( getType( uri ) );
            } else if( MediaStore.MediaColumns.DISPLAY_NAME.equals( col ) ) {
                row.add( f.getName() );
            } else if( MediaStore.MediaColumns.SIZE.equals( col ) ) {
                row.add( f.length() );
            } else {
                // Unsupported or unknown columns are filled up with null
                row.add(null);
            }
        }            
        return c;
    }
    
    @Override
    public ParcelFileDescriptor openFile( Uri uri, String mode ) throws FileNotFoundException {
        Log.v( TAG, "openFile( " + uri + " )" );
        Uri saf_u = getEnclosedUri( uri, "SAF" );
        if( saf_u != null ) {
            return getContext().getContentResolver().openFileDescriptor( saf_u, mode );
        }
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
