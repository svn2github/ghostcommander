package com.ghostsq.commander;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;

public class PictureViewer extends Activity {
    private final static String TAG = "PictureViewerActivity";
    public  ImageView image_view;
    public  Dialog dialogObj; 
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
        new Loader( this ).execute( u );
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    
    @Override
    protected Dialog onCreateDialog( int id ) {
        LayoutInflater factory = LayoutInflater.from( this );
        final View progressView = factory.inflate( R.layout.progress, null );
        super.onCreateDialog( id );
        return dialogObj = new AlertDialog.Builder( this )
            .setView( progressView )
            .setTitle( R.string.progress )
            .setCancelable( false )
            .create();
    }
    public void setProgress( String string, int progress, int progressSec ) {
        if( dialogObj == null )
            return;
        try {
            if( string != null ) {
                TextView t = (TextView)dialogObj.findViewById( R.id.text );
                if( t != null )
                    t.setText( string );
            }
            ProgressBar p_bar = (ProgressBar)dialogObj.findViewById( R.id.progress_bar );
            TextView perc_t = (TextView)dialogObj.findViewById( R.id.percent );

            if( progress >= 0 )
                p_bar.setProgress( progress );
            if( progressSec >= 0 )
                p_bar.setSecondaryProgress( progressSec );
            if( perc_t != null ) {
                perc_t.setText( "" + ( progressSec > 0 ? progressSec : progress ) + "%" );
            }
            Thread.sleep( 100 );
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    private class Loader extends AsyncTask<Uri, Integer, Bitmap> {
        private Context ctx;
        private CommanderAdapter ca;
        private byte[] buf;
        private boolean itsLowMemory;
        private String msg;
        
        Loader( Context ctx_ ) {
            ctx = ctx_;
        }
        
        protected void onPreExecute() {
            PictureViewer.this.showDialog( 1 );
        }
        
        @Override
        protected Bitmap doInBackground( Uri... uu ) {
            Uri u = null;
            try {
                final int BUF_SIZE = 16*1024; 
                buf = new byte[BUF_SIZE];
                u = uu[0];
                String schema = u.getScheme();
                ca = CA.CreateAdapterInstance( CA.GetAdapterTypeId( schema ), ctx );            
                if( ca != null ) {
                    byte[] source = null;
                    boolean local = ( ca.getType() & CA.LOCAL ) != 0;
                    if( !local ) {   // let's try to pre-cache
                        try {
                            InputStream is = ca.getContent( u );
                            if( is == null ) return null;
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            /*
                            for( int sz = 5242880; sz > 81920; sz >>= 1 ) {
                                try {
                                    Log.v( "readStreamToBuffer", "Reserving byte stream as big as " + sz );
                                    baos = new ByteArrayOutputStream( sz );
                                    if( baos != null ) break;
                                }
                                catch( Error e ) {
                                    Log.w( TAG, "Can't reserve " + sz + "B", e );
                                }
                                itsLowMemory = true;
                            }
                            */
                            if( baos != null ) {
                                int n, tot = 0;
                                boolean available_supported = false && is.available() > 0;
                                while( ( n = is.read( buf, 0, buf.length ) ) != -1 ) {
                                    publishProgress( tot += n );
                                    //Log.v( "readStreamToBuffer", "Read " + n + " bytes" );
                                    Thread.sleep( 1 );
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
                        for( b = 0x8000000; b > 1; b >>= 1 )
                            if( b <= factor ) break;
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
        protected void onProgressUpdate( Integer... v ) {
            PictureViewer.this.setProgress( "test", v[0] / 45000, -1 );
       }
        
        @Override
        protected void onPostExecute( Bitmap bmp ) {
            dialogObj.cancel();
            dialogObj = null;
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
