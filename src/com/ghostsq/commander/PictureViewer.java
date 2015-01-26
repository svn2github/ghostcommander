package com.ghostsq.commander;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.View;
import android.widget.AnalogClock;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import com.example.touch.TouchImageView;
import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;

public class PictureViewer extends Activity implements View.OnTouchListener {
    private final static String TAG = "PictureViewerActivity";
    public  ImageView image_view;
    public  TextView  name_view;
    public  boolean   touch = false;
    public  CommanderAdapter  ca;
    public  int       ca_pos = -1;
    public  Handler   h = new Handler();
    public  ProgressDialog pd; 
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        try {
          requestWindowFeature( Window.FEATURE_NO_TITLE );
          touch = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO;
          FrameLayout fl = new FrameLayout( this );

          if( touch )
              image_view = new TouchImageView( this );
          else
              image_view = new ImageView( this );
          fl.addView( image_view );

          SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( this );          
            String fnt_sz_s = sharedPref.getString( "font_size", "12" );
            int fnt_sz = 12;
            try {
                fnt_sz = Integer.parseInt( fnt_sz_s );
            } catch( NumberFormatException e ) {
            }
          
          name_view = new TextView( this );
          name_view.setTextColor( Color.WHITE );
          name_view.setTextSize( fnt_sz );
          FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL );
          name_view.setLayoutParams( lp );
          fl.addView( name_view );

          setContentView( fl );
          image_view.setOnTouchListener( this );
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
        Intent intent = getIntent();
        Uri uri = intent.getData();
        if( uri == null ) return;
        Log.d( TAG, "uri=" + uri );
        ca_pos = intent.getIntExtra( "position", -1 );
        Log.d( TAG, "orig pos=" + ca_pos );
        
        String name_to_show = null; 
        String scheme = uri.getScheme();
        if( !ContentResolver.SCHEME_CONTENT.equals( scheme ) ) {
            ca = CA.CreateAdapterInstance( scheme, this );            
            if( ca == null ) return;
            
            ca.Init( new CommanderStub() );
            
            Credentials crd = null; 
            try {
                crd = (Credentials)intent.getParcelableExtra( Credentials.KEY );
                ca.setCredentials( crd );
            } catch( Exception e ) {
                Log.e( TAG, "on taking credentials from parcel", e );
            }

            Uri.Builder ub = uri.buildUpon();
            Uri p_uri = null;
            if( "zip".equals( scheme ) ) {
                String cur = uri.getFragment();
                File cur_f = new File( cur );
                name_to_show = cur_f.getName();
                String parent_dir = cur_f.getParent();
                p_uri = uri.buildUpon().fragment( parent_dir != null ? parent_dir : "" ).build();
            }
            else {
                ub.path( "/" );
                List<String> ps = uri.getPathSegments();
                int n = ps.size();
                if( n > 0 ) n--;
                for( int i = 0; i < n; i++ ) ub.appendPath( ps.get( i ) );
                p_uri = ub.build();
                name_to_show = ps.get( ps.size()-1 );
            }
            if( p_uri != null ) {
                Log.d( TAG, "do read list" );
                ca.readSource( p_uri, null );
                Log.d( TAG, "end reading" );
            }
        }        
        new LoaderThread( uri, name_to_show ).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if( ca != null ) {
            ca.prepareToDestroy();
            ca = null;
        }
    }

    public void setBitmapToView( Bitmap bmp, String name ) {
        try {
            Log.v( TAG, "Bitmap is ready" );
            hideWait();
            if( bmp != null ) {
                if( touch )
                    ((TouchImageView)image_view).init();
                image_view.setImageBitmap( bmp );
                image_view.requestLayout();
                image_view.invalidate();
                
                if( touch )
                    ((TouchImageView)image_view).setMaxZoom( 4f );
                name_view.setTextColor( Color.WHITE );
                name_view.setText( name );
                return;
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
    }

    final public void showWait() {
        if( pd == null )
            pd = ProgressDialog.show( this, "", getString( R.string.loading ), true, true );
    }
    final public void hideWait() {
        if( pd != null )
            pd.cancel();
        pd = null;
    }
    
    private class LoaderThread extends Thread {
        private Context ctx;
        private byte[]  buf;
        private Uri     u;
        private Bitmap  bmp;
        private String  name_to_show = null;

        LoaderThread( Uri u_, String name_ ) {
            ctx = PictureViewer.this;
            u = u_;
            name_to_show = name_;
            setName( "PictureLoader" );
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
                    if( local ) {
                        f = new File( u.getPath() );
                    } else {
                        FileOutputStream fos = null;
                        InputStream is = null;
                        try {
                            PictureViewer.this.h.post(new Runnable() {
                                  @Override
                                  public void run() {
                                    PictureViewer.this.showWait();
                                  }
                              });                
                            // output - temporary file
                            File pictvw_f = ctx.getDir( "pictvw", Context.MODE_PRIVATE );
                            if( pictvw_f == null ) return;
                            f = new File( pictvw_f, "file.tmp" );
                            fos = new FileOutputStream( f );
                            // input - the content from adapter
                            is = ca.getContent( u );
                            if( is == null ) return;
                            int n;
                            boolean available_supported = is.available() > 0;
                            while( ( n = is.read( buf ) ) != -1 ) {
                                
                                //Log.v( "readStreamToBuffer", "Read " + n + " bytes" );
                                //sendProgress( tot += n );
                                Thread.sleep( 1 );
                                fos.write( buf, 0, n );
                                if( available_supported ) {
                                    for( int i = 1; i <= 10; i++ ) {
                                        if( is.available() > 0 ) break;
                                        //Log.v( "readStreamToBuffer", "Waiting the rest " + i );
                                        Thread.sleep( 20 * i );
                                    }
                                    if( is.available() == 0 ) {
                                        //Log.v( "readStreamToBuffer", "No more data!" );
                                        break;
                                    }
                                }
                            }
                        } catch( Throwable e ) {
                            throw e;
                        } finally {
                            if( ca != null ) {
                                if( is != null ) ca.closeStream( is );
                            }
                            if( fos != null ) fos.close();
                        }
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
                                final String name = name_to_show;    
                                PictureViewer.this.h.post(new Runnable() {
                                      @Override
                                      public void run() {
                                        PictureViewer.this.setBitmapToView( bmp, name );
                                      }
                                  });                
                                return;
                            }
                        }
                    }
                }
            } catch( Throwable e ) {
                Log.e( TAG, u != null ? u.toString() : null, e );
                final String msgText = e.getLocalizedMessage();
                PictureViewer.this.h.post(new Runnable() {
                      @Override
                      public void run() {
                        hideWait();
                        Toast.makeText( PictureViewer.this, msgText != null ? msgText : ctx.getString( R.string.error ), 
                               Toast.LENGTH_LONG ).show();
                      }
                  });                
            } finally {
                PictureViewer.this.h.post(new Runnable() {
                      @Override
                      public void run() {
                        hideWait();
                      }
                  });                
            }
        }
    }

    @Override
    public boolean onTouch( View v, MotionEvent event ) {
        if( touch && ((TouchImageView)image_view).onTouch( v, event ) ) return true;
        if( event.getAction() == MotionEvent.ACTION_UP ) {
            boolean to_next = event.getX() > image_view.getWidth() / 2;
            //Toast.makeText( this, to_next ? "Go next" : "Go prev", Toast.LENGTH_SHORT ).show();
            LoadNext( to_next );
            return true;
        }
        image_view.performClick();
        return false;
    }

    private void LoadNext( boolean forward ) {
        if( ca_pos < 0 || ca == null ) {
            Log.e( TAG, "ca=" + ca + ", pos=" + ca_pos );
            return;
        }
        int orig_pos = ca_pos; 
        while( true ) {
            if( forward ) ca_pos++; else ca_pos--;
            if( ca_pos <= 0 ) {
                ca_pos = orig_pos;
                return;
            } 
            Uri pos_uri = ca.getItemUri( ca_pos );
            if( pos_uri == null ) {
                ca_pos = orig_pos;
                return;
            } 
            String name = ca.getItemName( ca_pos, false );
            String mime = Utils.getMimeByExt( Utils.getFileExt( name ) );
            Log.d( TAG, "Next name: " + name + " mime: " + mime );
            if( mime.startsWith( "image/" ) ) {
                Log.d( TAG, "new pos=" + ca_pos );
                name_view.setTextColor( Color.GRAY );
                name_view.setText( getString( R.string.wait ) );
                new LoaderThread( pos_uri, name ).start();
                return;
            }
        }
    }

    private class CommanderStub implements Commander {

        @Override
        public Context getContext() {
            return PictureViewer.this;
        }
        @Override
        public void issue( Intent in, int ret ) {
        }
        @Override
        public void showError( String msg ) {
        }
        @Override
        public void showInfo( String msg ) {
        }
        @Override
        public void showDialog( int dialog_id ) {
        }
        @Override
        public void Navigate( Uri uri, Credentials crd, String positionTo ) {
        }
        @Override
        public void dispatchCommand( int id ) {
        }
        @Override
        public void Open( Uri uri, Credentials crd ) {
        }
        @Override
        public int getResolution() {
            return 0;
        }
        @Override
        public boolean notifyMe( Message m ) {
            return false;
        }
        @Override
        public boolean startEngine( Engine e ) {
            return false;
        }
    }    
}
