package com.ghostsq.commander;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.example.touch.TouchImageView;
import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.utils.Credentials;

public class PictureViewer extends Activity {
    private final static String TAG = "PictureViewerActivity";
    public  ImageView image_view;
    public  boolean   touch = false;
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        try {
          requestWindowFeature( Window.FEATURE_NO_TITLE );
          touch = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO;
          if( touch )
              image_view = new TouchImageView( this );
          else
              image_view = new ImageView( this );
          setContentView( image_view );
        }
        catch( Throwable e ) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLowMemory() {
        Log.w( TAG, "Low Memory!" );
        super.onLowMemory();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Uri u = getIntent().getData();
        if( u == null ) return;
        new LoaderThread( this, u ).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    
    private class LoaderThread extends Thread {
        private Context ctx;
        private byte[] buf;
        private String msgText;
        private Uri u;
        private Bitmap bmp;
        private DoneHandler h = new DoneHandler();
        private ProgressDialog pd;
        
        protected class DoneHandler extends Handler {
            @Override
            public void handleMessage( Message msg ) {
                try {
                    Bundle b = msg.getData();
                    int p = b.getInt( "p" );
                    if( p < 0 )
                        postExecute();
                    else
                        progressUpdate( p );
                } catch( Exception e ) {
                    e.printStackTrace();
                }
            }
        };    
        
        LoaderThread( Context ctx_, Uri u_ ) {
            ctx = ctx_;
            u = u_;
            setName( "PictureLoader" );
            //setPriority( Thread.MAX_PRIORITY );
            preExecute();
        }
        
        protected void preExecute() {
            pd = ProgressDialog.show( ctx, "", getString( R.string.loading ), true, true );
        }
        
        @Override
        public void run() {
            try {
                final int BUF_SIZE = 100*1024; 
                buf = new byte[BUF_SIZE];
                String scheme = u.getScheme();
                if( ContentResolver.SCHEME_CONTENT.equals( scheme ) ) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inTempStorage = buf;
                    InputStream is = null;
                    for( int b = 1; b < 0x80000; b <<= 1 ) {
                        try {
                            options.inSampleSize = b;
                            is = getContentResolver().openInputStream( u );
                            if( is == null ) {
                                Log.e( TAG, "Failed to get the content stream for: " + u );
                                return;
                            }
                            bmp = BitmapFactory.decodeStream( is, null, options );
                            if( bmp != null )
                                return;
                        } catch( Throwable e ) {
                        } finally {
                            if( is != null )
                                is.close();
                        }
                        Log.w( TAG, "Cant decode stream to bitmap. b=" + b );
                    }
                } else {
                    File f = null;
                    setPriority( Thread.MAX_PRIORITY );
                    boolean local = CA.isLocal( scheme );
                    if( local ) {   // pre-cache in a file
                        f = new File( u.getPath() );
                    } else {
                        CommanderAdapter ca = CA.CreateAdapterInstance( CA.GetAdapterTypeId( scheme ), ctx );            
                        if( ca == null ) return;
                        Credentials crd = null; 
                        try {
                            crd = (Credentials)getIntent().getParcelableExtra( Credentials.KEY );
                        } catch( Exception e ) {
                            Log.e( TAG, "on taking credentials from parcel", e );
                        }
                        ca.setCredentials( crd );
                        
                        // output - temporary file
                        File pictvw_f = ctx.getDir( "pictvw", Context.MODE_PRIVATE );
                        if( pictvw_f == null ) return;
                        f = new File( pictvw_f, "file.tmp" );
                        FileOutputStream fos = new FileOutputStream( f );
                        // input - the content from adapter
                        InputStream is = ca.getContent( u );
                        if( is == null ) return;
                        int n;
                        boolean available_supported = is.available() > 0;
                        while( ( n = is.read( buf ) ) != -1 ) {
                            //Log.v( "readStreamToBuffer", "Read " + n + " bytes" );
                            //sendProgress( tot += n );
                            Thread.sleep( 1 );
                            fos.write( buf, 0, n );
                            if( available_supported ) {
                                for( int i = 0; i < 10; i++ ) {
                                    if( is.available() > 0 ) break;
                                    //Log.v( "readStreamToBuffer", "Waiting the rest " + i );
                                    Thread.sleep( 20 );
                                }
                                if( is.available() == 0 ) {
                                    //Log.v( "readStreamToBuffer", "No more data!" );
                                    break;
                                }
                            }
                        }
                        ca.closeStream( is );
                        fos.close();
                    }
                    if( f != null ) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inTempStorage = buf;
                        for( int b = 1; b < 0x80000; b <<= 1 ) {
                            try {
                                options.inSampleSize = b;
                                bmp = BitmapFactory.decodeFile( f.getAbsolutePath(), options );
                            } catch( Throwable e ) {}
                            if( bmp != null ) {
                                if( !local )
                                    f.delete();
                                return;
                            }
                        }
                    }
                }
            } catch( Throwable e ) {
                Log.e( TAG, u != null ? u.toString() : null, e );
                msgText = e.getLocalizedMessage();
            } finally {
                sendProgress( -1 );
            }
        }
        
        protected void sendProgress( int v ) {
            Message msg = h.obtainMessage();
            Bundle b = new Bundle();
            b.putInt( "p", v );
            msg.setData( b );
            h.sendMessage( msg );
        }
        
        protected void progressUpdate( int v ) {
            Log.v( TAG, "progressUpdate" + v );
        }
        
        protected void postExecute() {
            try {
                Log.v( TAG, "postExecute" );
                pd.cancel();
                if( bmp != null ) {
                    image_view.setImageBitmap( bmp );
                    if( touch )
                        ((TouchImageView)image_view).setMaxZoom( 4f );
                    return;
                }
            } catch( Throwable e ) {
                e.printStackTrace();
            }
            Toast.makeText( ctx, msgText != null ? msgText : ctx.getString( R.string.error ), 
                   Toast.LENGTH_LONG ).show();
        }
 
    }


}
