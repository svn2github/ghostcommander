package com.ghostsq.commander;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.View;
import android.view.GestureDetector;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.SAFAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;
import com.ortiz.touch.TouchImageView;

public class PictureViewer extends Activity implements View.OnTouchListener,
                                        GestureDetector.OnDoubleTapListener {
    private final static String TAG = "PictureViewerActivity";
    public  ImageView image_view;
    public  TextView  name_view;
    public  boolean   touch = false;
    public  CommanderAdapter  ca;
    public  int       ca_pos = -1;
    public  Handler   h = new Handler();
    public  ProgressDialog pd; 
    private PointF    last;

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        try {
          requestWindowFeature( Window.FEATURE_NO_TITLE );
          touch = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO;
          FrameLayout fl = new FrameLayout( this );

          if( touch ) {
              image_view = new TouchImageView( this );
              ((TouchImageView)image_view).setOnDoubleTapListener( this );
          } else
              image_view = new ImageView( this );
          fl.addView( image_view );
          image_view.setVisibility( View.GONE );
          image_view.setOnTouchListener( this );
          
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
        int mode = intent.getIntExtra( "mode", 0 );
        Log.d( TAG, "orig pos=" + ca_pos );
        
        String name_to_show = null; 
        String scheme = uri.getScheme();

        ca = CA.CreateAdapterInstance( uri, this );            
        if( ca == null ) return;
        ca.Init( new CommanderStub() );
        ca.setMode( CommanderAdapter.MODE_SORTING | CommanderAdapter.MODE_SORT_DIR, mode );
        
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
        else if( ca instanceof SAFAdapter ) {
            p_uri = SAFAdapter.getParent( uri );
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
        image_view.invalidate();
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

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        try {
            Utils.changeLanguage( this );
            // Inflate the currently selected menu XML resource.
            MenuInflater inflater = getMenuInflater();
            inflater.inflate( R.menu.pict_vw, menu );
            return true;
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return false;
    }
    
    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        super.onMenuItemSelected( featureId, item );
        try {
            switch( item.getItemId() ) {
            case R.id.rot_left:
                rotate( false );
                break;
            case R.id.rot_right:
                rotate( true );
                break;
            case R.id.go_next:
                loadNext( true );
                break;
            case R.id.go_prev:
                loadNext( false );
                break;
            case R.id.delete:
                delete();
                break;
            case R.id.exit:
                finish();
                break;
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return true; 
    }
    
    public final void setBitmapToView( Bitmap bmp, String name ) {
        try {
            Log.v( TAG, "Bitmap is ready" );
            hideWait();
            if( bmp != null ) {
                image_view.setVisibility( View.VISIBLE );
                image_view.setImageBitmap( bmp );
                
                
                name_view.setTextColor( Color.WHITE );
                name_view.setText( name );
                return;
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
    }

    private final void rotate( boolean clockwise ) {
        new RotateTask( clockwise ).execute();
    }

    public class RotateTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<Bitmap> wrRotatedBitmap;
        private boolean clockwise;
    
        public RotateTask( boolean clockwise ) {
            this.clockwise = clockwise;
        }
    
        @Override
        protected void onPreExecute() {
        }
    
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Drawable from_view = image_view.getDrawable();
                if( from_view == null ) {
                    Log.e( TAG, "No drawable" );
                    return null;
                }
                
                if( !( from_view instanceof BitmapDrawable ) ) {
                    Log.e( TAG, "drawable is not a bitmap" );
                    return null;
                }
                BitmapDrawable bd = (BitmapDrawable)from_view;
                Bitmap old_bmp = bd.getBitmap();
                
                final Matrix m = new Matrix();
                m.postRotate( clockwise ? 90 : 270 );
                final int old_w = old_bmp.getWidth(); 
                final int old_h = old_bmp.getHeight();
                for( int i = 1; i <= 8; i <<= 1 ) {
                    try {
                        if( i > 1 ) {
                            float scale = 1.f / i;
                            m.postScale( scale, scale );
                        }
                        wrRotatedBitmap = new WeakReference<Bitmap>( Bitmap.createBitmap(
                                 old_bmp, 0, 0, old_w, old_h, m, false ) );
                        old_bmp.recycle();
                        break;
                    } catch( OutOfMemoryError e ) {}
                }
            } catch( Throwable e ) {
                Log.e( TAG, "", e );
            }
            return null;
        }
    
        @Override
        protected void onPostExecute(Void param) {
            if( wrRotatedBitmap == null ) return;
            Bitmap bmp = wrRotatedBitmap.get();
            if( bmp != null )
                setBitmapToView( bmp, name_view.getText().toString() );
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
                    ContentResolver cr = getContentResolver();
                    for( int b = 1; b < 0x80000; b <<= 1 ) {
                        try {
                            options.inSampleSize = b;
                            if( ca != null )
                                is = PictureViewer.this.ca.getContent( u );
                            else
                                is = cr.openInputStream( u );
                            if( is == null ) {
                                Log.e( TAG, "Failed to get the content stream for: " + u );
                                return;
                            }
                            bmp = BitmapFactory.decodeStream( is, null, options );
                            if( bmp != null ) {
                                /*
                                final String[] projection = {
                                     OpenableColumns.DISPLAY_NAME
                                };
                                Cursor c = cr.query( u, projection, null, null, null );
                                int nci = c.getColumnIndex( OpenableColumns.DISPLAY_NAME );
                                c.moveToFirst();
                                final String fn = c.getString( nci );
                                */
                                PictureViewer.this.h.post(new Runnable() {
                                      @Override
                                      public void run() {
                                        PictureViewer.this.setBitmapToView( bmp, name_to_show );
                                      }
                                  });                
                                return;
                            }
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
                            is = PictureViewer.this.ca.getContent( u );
                            if( is == null ) return;
                            int n;
                            boolean available_supported = is.available() > 0;
                            while( ( n = is.read( buf ) ) != -1 ) {
                                
                                Log.v( "readStreamToBuffer", "Read " + n + " bytes" );
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
                                        Log.v( "readStreamToBuffer", "No more data!" );
                                        break;
                                    }
                                }
                            }
                        } catch( Throwable e ) {
                            throw e;
                        } finally {
                            if( ca != null && is != null ) 
                                ca.closeStream( is );
                            if( fos != null ) fos.close();
                        }
                    }
                    if( f != null && f.exists() && f.isFile() ) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inTempStorage = buf;
                        for( int b = 1; b < 0x80000; b <<= 1 ) {
                            try {
                                options.inSampleSize = b;
                                bmp = BitmapFactory.decodeFile( f.getAbsolutePath(), options );
                            } catch( Throwable e ) {}
                            if( bmp != null ) {
                                if( !local )
                                    f.deleteOnExit();
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

    // --- View.OnTouchListener ---
    
    @Override
    public boolean onTouch( View v, MotionEvent event ) {
        if( touch && ((TouchImageView)image_view).isZoomed() ) return false;
        if( event.getAction() == MotionEvent.ACTION_DOWN ) {
            last = new PointF( event.getX(), event.getY() );
            return true;
        }
        if( event.getAction() == MotionEvent.ACTION_UP ) {
            if( last == null ) return false;
            float ady = Math.abs( event.getY() - last.y );
            if( ady < 50 ) {
                float x = event.getX();
                float dx = x - last.x;
                float adx = Math.abs( dx );
                if( adx > 20 )
                    loadNext( dx < 0 );
            }
            last = null;
            return true;
        }
//        image_view.performClick();
        return false;
    }

    // --- GestureDetector.OnDoubleTapListener ---
    
    @Override
    public boolean onDoubleTap( MotionEvent arg0 ) {
        return false;
    }

    @Override
    public boolean onDoubleTapEvent( MotionEvent arg0 ) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed( MotionEvent event ) {
        if( touch && ((TouchImageView)image_view).isZoomed() ) return false;
        float x = event.getX();
        loadNext( x > image_view.getWidth() / 2 );
        return true;
    }

    private final void delete() {
        if( ca_pos < 0 || ca == null ) return;
        String name = ca.getItemName( ca_pos, false );
        new AlertDialog.Builder( this )
            .setTitle( R.string.delete_title )
            .setMessage( getString( R.string.delete_q, name ) )
            .setPositiveButton( R.string.dialog_ok, new OnClickListener() {
                @Override
                public void onClick( DialogInterface dialog, int which ) {
                    SparseBooleanArray sba = new SparseBooleanArray();
                    sba.append( ca_pos, true );
                    ca.deleteItems( sba );
                }
            } )
            .setNegativeButton( R.string.dialog_cancel, null )
            .show();
    }

    private void loadNext( boolean forward ) {
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
            e.setHandler( new Handler() {
                @Override
                public void handleMessage( Message msg ) {
                    if( msg.what == OPERATION_COMPLETED_REFRESH_REQUIRED ||
                        msg.what == OPERATION_COMPLETED ) {
                        ca.readSource( null, null );
                        loadNext( true );
                    }
                }
            });
            e.start();
            return true;
        }
    }
}
