package com.ghostsq.commander;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.adapters.SAFAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

public class FileProvider extends ContentProvider {
    private static final String TAG = "FileProvider";
//    public static final String URI_PREFIX = "content://com.ghostsq.commander";
    public  static final String AUTHORITY = "com.ghostsq.commander";
    private static final String CA_MODE_SIG   = "CA";
    private static final int MODE_SEG = 0, MIME_SEG = 1, URI_SEG = 2, SEG_NUM = 3;

    public final static Uri makeURI( String path ) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme( "content" ).authority( AUTHORITY ).path( path );
        return ub.build(); 
    }
    public final static Uri makeURI( Uri u, String mime ) {
        Uri.Builder ub = new Uri.Builder();
        String us = u.toString();
        ub.scheme( "content" ).authority( AUTHORITY )
        .appendPath( CA_MODE_SIG )
        .appendPath( mime )
        .appendPath( Base64.encodeToString( us.getBytes(), Base64.URL_SAFE ) );
        return ub.build(); 
    }
    
    private final static List<String> isCAmode( Uri uri ) {
        List<String> ps = uri.getPathSegments();
        if( ps != null && ps.size() >= SEG_NUM && CA_MODE_SIG.equalsIgnoreCase( ps.get( MODE_SEG ) ) )
            return ps;
        return null;
    }
    
    private final static Uri getEnclosedUri( Uri uri ) {
        List<String> ps = uri.getPathSegments();
        if( ps == null || ps.size() < SEG_NUM ) return null;
        byte[] ub = Base64.decode( ps.get(URI_SEG), Base64.URL_SAFE );
        String us = new String( ub );
        Log.d( TAG, "Got enclosed URI: " + us );
        return Uri.parse( us );
    }

    public static void storeCredentials( Context ctx, Credentials crd, Uri uri ) {
        crd.storeCredentials( ctx, FileProvider.class.getSimpleName(), uri );
    }

    public static Credentials restoreCredentials( Context ctx, Uri uri ) {
        return Credentials.restoreCredentials( ctx, FileProvider.class.getSimpleName(), uri );
    }
    
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType( Uri uri ) {
        List<String> ps = isCAmode( uri );
        if( ps != null )
            return ps.get( MIME_SEG );
        String ext  = Utils.getFileExt( uri.getPath() );
        return Utils.getMimeByExt( ext );
    }

    @Override
    public Cursor query( Uri uri, String[] fields, String sel, String[] sel_args, String sort ) {
        try {
            Log.v( TAG, "query( " + uri + " )" );
            if( !AUTHORITY.equals( uri.getAuthority() ) )
                throw new RuntimeException( "Unsupported URI" );
            List<String> ps = isCAmode( uri );
            if( ps != null ) {
                Uri enclosed_uri = getEnclosedUri( uri );
                CommanderAdapter ca = null;
                MatrixCursor c = new MatrixCursor( fields );
                MatrixCursor.RowBuilder row = c.newRow();
                for( String col : fields ) {
                    if( MediaStore.MediaColumns.DATA.equals( col ) ) {
                        row.add( enclosed_uri );
                    } else if( MediaStore.MediaColumns.MIME_TYPE.equals( col ) ) {
                        row.add( getType( uri ) );
                    } else if( MediaStore.MediaColumns.DISPLAY_NAME.equals( col ) ) {
                        row.add( enclosed_uri.getLastPathSegment() );
                    } else if( MediaStore.MediaColumns.TITLE.equals( col ) ) {
                        row.add( enclosed_uri.getLastPathSegment() );
                    } else if( MediaStore.MediaColumns.WIDTH.equals( col ) ) {
                        row.add( 100 );
                    } else if( MediaStore.MediaColumns.HEIGHT.equals( col ) ) {
                        row.add( 100 );
                    } else if( MediaStore.MediaColumns.SIZE.equals( col ) ) {
                        if( ca == null ) ca = CreateCA( enclosed_uri );
                        Item item = ca.getItem( enclosed_uri );
                        row.add( item == null ? 0L : item.size );
                    } else {
                        // Unsupported or unknown columns are filled up with null
                        row.add(null);
                    }
                }            
                return c;
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

    static class TransferThread extends Thread {
        InputStream in;
        OutputStream out;
    
        TransferThread(InputStream in, OutputStream out) {
            this.in  = in;
            this.out = out;
        }
    
        @Override
        public void run() {
//            Utils.copyBytes( in, out );
            byte[] buf = new byte[1048576];
            int len = 0, has_read;
    
            try {
                while ((has_read = in.read(buf)) > 0) {
                    out.write( buf, 0, has_read );
                    len += has_read;
                }
                Log.d( FileProvider.TAG, "Bytes read: " + len );
                in.close();
                out.flush();
                out.close();
            } catch(IOException e) {
                Log.e( FileProvider.TAG, "Exception transferring file. Were able to read bytes " + len, e );
            }
        }
    }    

    private CommanderAdapter CreateCA( Uri u ) { 
        CommanderAdapter ca = CA.CreateAdapterInstance( u, getContext() );
        if( ca == null )
            return null;
        ca.Init( null );
        String ui = u.getUserInfo();
        if( ui != null ) {
            Credentials credentials = restoreCredentials( getContext(), u );
            if( credentials != null ) {
                ca.setCredentials( credentials );
                u = Utils.updateUserInfo( u, null );
            }
        }
        ca.setUri( u );
        return ca;
    }
    
    @SuppressLint("NewApi")
    @Override
    public ParcelFileDescriptor openFile( Uri uri, String access_mode ) throws FileNotFoundException {
        Log.v( TAG, "openFile( " + uri + " ) " + access_mode );
        List<String> ps = isCAmode( uri );
        if( ps == null ) {
            File file = new File( uri.getPath() );
            if( !file.exists() ) throw new FileNotFoundException();
            int pfd_mode = 0;
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT )
                pfd_mode = ForwardCompat.parseFileDescriptorMode( access_mode );
            ParcelFileDescriptor parcel = ParcelFileDescriptor.open( file, pfd_mode );
            return parcel;
        }
        Uri enclosed_uri = getEnclosedUri( uri );
        try {
            CommanderAdapter ca = CreateCA( enclosed_uri ); 
            if( ca == null ) throw new FileNotFoundException();
            InputStream is = ca.getContent( ca.getUri() );
            if( is == null ) return null;
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            OutputStream os = new AutoCloseOutputStream( pipe[1] );
            new TransferThread( is, os ).start();
            return pipe[0];
        }
        catch (IOException e) {
          Log.e(getClass().getSimpleName(), "Exception opening pipe to " + enclosed_uri.toString(), e);
        }
        return null;
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
