package com.ghostsq.commander;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.ref.SoftReference;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.utils.Utils;

public class PictureViewer extends Activity {
    private final static String TAG = "PictureViewerActivity";
    public  ImageView image_view;
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        Log.v( TAG, "onCreate" );
        super.onCreate( savedInstanceState );
        try {
            requestWindowFeature( Window.FEATURE_NO_TITLE );
            setContentView( R.layout.pictvw );
            image_view = (ImageView)findViewById( R.id.image_view );
        }
        catch( Throwable e ) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLowMemory() {
        Log.w( TAG, "Low Memory!" );
//        itsLowMemory = true;
        super.onLowMemory();
    }

    @Override
    protected void onStart() {
        Log.v( TAG, "onStart" );
        super.onStart();
        Uri u = getIntent().getData();
        if( u == null ) return;
        String path = u.getPath();
        if( path == null ) return;
        new Loader( this ).execute( u );
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    
    
    private class Loader extends AsyncTask<Uri, Void, Bitmap> {
        private Context ctx;
        private ProgressDialog dialog;
        private CommanderAdapter ca;
        private byte[] buf;
        private boolean itsLowMemory;
        private String msg;
        
        Loader( Context ctx_ ) {
            ctx = ctx_;
        }
        
        protected void onPreExecute(){
            dialog = ProgressDialog.show( ctx, "", "Loading...", true, true );
        }
        
        @Override
        protected Bitmap doInBackground( Uri... uu ) {
            Looper.prepare();
            Uri u = null;
            try {
                buf = new byte[16*1024];
                u = uu[0];
                String schema = u.getScheme();
                ca = CA.CreateAdapterInstance( CA.GetAdapterTypeId( schema ), ctx );            
                if( ca != null ) {
                    byte[] source = null;
                    boolean local = ( ca.getType() & CA.LOCAL ) != 0;
                    if( !local ) {   // let's try to pre-cache
                        try {
                            InputStream is = ca.getContent( u );
                            ByteArrayOutputStream baos = null;
                            for( int sz = 5242880; sz > 81920; sz >>= 1 ) {
                                try {
                                    baos = new ByteArrayOutputStream( sz );
                                    if( baos != null ) break;
                                }
                                catch( Error e ) {
                                    Log.w( TAG, "Can't reserve " + sz + "B", e );
                                }
                                itsLowMemory = true;
                            }
                            if( baos != null ) {
                                int n;
                                boolean available_supported = is.available() > 0;
                                while( ( n = is.read( buf, 0, buf.length ) ) != -1 ) {
                                    baos.write( buf, 0, n );
                                    if( available_supported ) {
                                        for( int i = 0; i < 10; i++ ) {
                                            if( is.available() > 0 ) break;
                                            //Log.v( "readStreamToBuffer", "Waiting the rest " + i );
                                            Thread.sleep( 20 );
                                        }
                                        if( is.available() == 0 ) {
                                            Log.v( "readStreamToBuffer", "No more data!" );
                                            break;
                                        }
                                    }
                                }
                                ca.closeStream( is );
                                baos.flush();
                                source = baos.toByteArray();
                                baos.close();
                            }
                        } catch( Throwable e ) {
                            Log.e( TAG, u.toString(), e );
                        }
                    }
                    
                    Display display = getWindowManager().getDefaultDisplay(); 
                    int width = display.getWidth();
                    int height = display.getHeight();
                    boolean by_height = height < width;
                    //Log.v( TAG, "w=" + width + ", h=" + height );
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 1;
                    options.inJustDecodeBounds = true;
                    options.outWidth = 0;
                    options.outHeight = 0;
                    options.inTempStorage = buf;
                    if( source != null )
                        BitmapFactory.decodeByteArray( source, 0, source.length, options );
                    else {
                        InputStream is = ca.getContent( u );
                        if( is != null ) {
                            BitmapFactory.decodeStream( is, null, options );
                            ca.closeStream( is );
                        }
                    }
                    Log.v( TAG, "w=" + options.outWidth + ", h=" + options.outHeight );
                    if( options.outWidth > 0 && options.outHeight > 0 ) {
                        int factor = by_height ? options.outHeight / height : options.outWidth / width;
                        int b;
                        for( b = 1; b < 0x8000000; b <<= 1 )
                            if( b >= factor ) break;
                        if( itsLowMemory && !local && b > 1 )
                            b <<= 1;    // is it better show a smaller picture then crash on out of memory?
                        Log.v( TAG, "aligned factor=" + b );
                        options.inSampleSize = b;
                        options.inJustDecodeBounds = false;
                        Bitmap bmp = null;
                        if( source != null )
                            bmp = BitmapFactory.decodeByteArray( source, 0, source.length, options );
                        else {
                            InputStream is = ca.getContent( u );
                            if( is != null ) {
                                bmp = BitmapFactory.decodeStream( is, null, options );
                                ca.closeStream( is );
                            }
                        }
                        return bmp;                            
                    }
                    else
                        Log.w( TAG, "failed to get the image bounds!" );
                }
            } catch( Throwable e ) {
                Log.e( TAG, u != null ? u.toString() : null, e );
                msg = e.getLocalizedMessage();
            }
            return null;
        }
         
        @Override
        protected void onPostExecute( Bitmap bmp ) {
            dialog.cancel();
            if( bmp == null ) {
                Toast.makeText( ctx, msg != null ? msg : ctx.getString( R.string.error ), 
                       Toast.LENGTH_LONG ).show();
            }
            else {
                image_view.setImageBitmap( bmp );
            }
        }
 
    }
}
