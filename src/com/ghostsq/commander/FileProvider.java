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
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

public class FileProvider extends ContentProvider {
    private static final String TAG = "FileProvider";
//    public static final String URI_PREFIX = "content://com.ghostsq.commander";
    public static final String AUTHORITY = "com.ghostsq.commander";

    public final static Uri makeURI( String path ) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme( "content" ).authority( AUTHORITY ).path( path );
        return ub.build(); 
    }
    public final static Uri makeURI( String type, Uri u ) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme( "content" ).authority( AUTHORITY );
        String us = u.toString();
        ub.appendQueryParameter( type, Base64.encodeToString( us.getBytes(), Base64.URL_SAFE ) );
        return ub.build(); 
    }
    
    private final static Uri getEnclosedUri( Uri uri, String type ) {
        String bs = uri.getQueryParameter( type );
        if( !Utils.str( bs ) ) return null;
        byte[] ub = Base64.decode( bs, Base64.URL_SAFE );
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
    public Cursor query( Uri uri, String[] fields, String sel, String[] sel_args, String sort ) {
        try {
            Log.v( TAG, "query( " + uri + " )" );
            if( !AUTHORITY.equals( uri.getAuthority() ) )
                throw new RuntimeException( "Unsupported URI" );
            Uri saf_u = getEnclosedUri( uri, "SAF" );
            if( saf_u != null ) {
                return getContext().getContentResolver().query( saf_u, fields, sel, sel_args, sort );
            }
            if( fields == null || fields.length == 0) {
                fields = new String [] {
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE };
            } 
            MatrixCursor c = new MatrixCursor( fields );
            MatrixCursor.RowBuilder row = c.newRow();
            File f = new File( uri.getPath() );
            if( !f.exists() || !f.isFile() )
                throw new RuntimeException( "No file name specified: " + uri );
            
            for( String col : fields ) {
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
        } catch( Exception e ) {
            Log.e( TAG, "Can't provide for query " + uri, e );
        }
        return null;
    }
    
    @Override
    public ParcelFileDescriptor openFile( Uri uri, String mode ) throws FileNotFoundException {
        Log.v( TAG, "openFile( " + uri + " ) " + mode );
        Uri saf_u = getEnclosedUri( uri, "SAF" );
        if( saf_u != null ) {
            return getContext().getContentResolver().openFileDescriptor( saf_u, mode );
        }
        File file = new File( uri.getPath() );
        if( !file.exists() ) throw new FileNotFoundException();
        int pfd_mode = 0;
        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT )
            pfd_mode = ForwardCompat.parseFileDescriptorMode( mode );
        ParcelFileDescriptor parcel = ParcelFileDescriptor.open( file, pfd_mode );
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
