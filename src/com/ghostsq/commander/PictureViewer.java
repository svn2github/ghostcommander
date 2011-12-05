package com.ghostsq.commander;

import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
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
    private byte[] buf;
    private SoftReference<Bitmap> tmp_bmp;
    private CommanderAdapter ca;
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        Log.v( TAG, "onCreate" );
        super.onCreate( savedInstanceState );
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        try {
            setContentView( R.layout.pictvw );
            buf = new byte[16*1024];
        }
        catch( Throwable e ) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        Log.v( TAG, "onStart" );
        super.onStart();
        ImageView image_view = (ImageView)findViewById( R.id.image_view );
        Uri u = getIntent().getData();
        if( u == null ) return;
        String path = u.getPath();
        if( path == null ) return;
        try {
            String schema = u.getScheme();
            ca = CA.CreateAdapterInstance( CA.GetAdapterTypeId( schema ), this );            
            if( ca != null ) {
                byte[] source = null;
                boolean local = ( ca.getType() & CA.LOCAL ) != 0;
                if( !local ) {   // let's try to pre-cache
                    try {
                        InputStream is = ca.getContent( u );
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int n;
                        boolean available_supported = is.available() > 0;
                        while( ( n = is.read( buf, 0, buf.length ) ) != -1 ) {
                            baos.write( buf, 0, n );
                            if( available_supported ) {
                                for( int i = 0; i < 10; i++ ) {
                                    if( is.available() > 0 ) break;
                                    Log.v( "readStreamToBuffer", "Waiting the rest " + i );
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
                    if( !local && b > 1 )
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
                    if( bmp != null )
                        image_view.setImageBitmap( bmp );
                }
                else
                    Log.w( TAG, "failed to get the image bounds!" );
            }
        } catch( Throwable e ) {
            Log.e( TAG, u.toString(), e );
            Toast.makeText( this, e.getMessage(), Toast.LENGTH_LONG ).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if( tmp_bmp != null && tmp_bmp.get() != null ) {
            tmp_bmp.get().recycle();
            tmp_bmp = null;
        }
    }
}
