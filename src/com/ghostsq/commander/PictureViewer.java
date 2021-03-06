package com.ghostsq.commander;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Images;
import android.provider.OpenableColumns;
import android.text.Html;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.View;
import android.view.GestureDetector;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.List;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.MSAdapter;
import com.ghostsq.commander.adapters.SAFAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.ImageInfo;
import com.ghostsq.commander.utils.Utils;
import com.ortiz.touch.TouchImageView;

public class PictureViewer extends Activity implements View.OnTouchListener,
                                        GestureDetector.OnDoubleTapListener {
    private final static String TAG = "PictureViewerActivity";
    public  ImageView image_view;
    public  TextView  name_view;
    public  boolean   touch = false;
    public  CommanderAdapter  ca;
    private CommanderStub     stub;
    private Uri       uri = null;
    public  int       ca_pos = -1;
    public  Handler   h = new Handler();
    public  ProgressDialog pd; 
    private PointF    last;
    private String    temp_file_path, file_name;

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( this );
        Utils.setTheme( this, sharedPref.getString( "color_themes", "d" ) );
        super.onCreate( savedInstanceState );
        try {
          boolean ab = Utils.setActionBar( this );
          if( !ab )
              requestWindowFeature( Window.FEATURE_NO_TITLE );
          touch = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO;
          FrameLayout fl = new FrameLayout( this );
          fl.setBackgroundColor( 0xFF000000 );

          if( touch ) {
              image_view = new TouchImageView( this );
              ((TouchImageView)image_view).setOnDoubleTapListener( this );
          } else
              image_view = new ImageView( this );
          fl.addView( image_view );
          image_view.setVisibility( View.GONE );
          image_view.setOnTouchListener( this );

          try {
              if( !ab && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                  !ForwardCompat.hasPermanentMenuKey( this ) ) {
                    LayoutInflater li = (LayoutInflater)this.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                    View fbv = li.inflate( R.layout.fly_button, fl, true );
                    ImageButton mb = (ImageButton)fbv.findViewById( R.id.menu );
                    if( mb != null ) {
                        mb.setVisibility( View.VISIBLE );
                        mb.setOnClickListener( new View.OnClickListener() {
                            @Override
                            public void onClick( View v ) {
                                PictureViewer.this.openOptionsMenu();
                            }
                        });
                    }
               }
            } catch( Exception e1 ) {
                Log.e( TAG, "", e1 );
            }
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
          stub = new CommanderStub();
        }
        catch( Throwable e ) {
            Log.e( TAG, "", e );
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
        uri = intent.getData();
        if( uri == null ) return;
        Log.d( TAG, "uri=" + uri );
        String scheme = uri.getScheme();
        if( ContentResolver.SCHEME_CONTENT.equals( scheme ) && FileProvider.AUTHORITY.equals( uri.getHost() ) ) {
            if( !Utils.str( uri.getQuery() ) ) {
                Log.d( TAG, "Converting content to file" );
                scheme = "file";
                uri = new Uri.Builder().scheme( scheme ).path( uri.getPath() ).build();
            }
        }        
        ca_pos = intent.getIntExtra( "position", -1 );
        int mode = intent.getIntExtra( "mode", 0 );
        Log.d( TAG, "orig pos=" + ca_pos );
        ca = CA.CreateAdapterInstance( uri, this );            
        file_name = null; 
        
        Uri.Builder ub = uri.buildUpon();
        Uri parent_uri = null;
        if( "zip".equals( scheme ) ) {
            String cur = uri.getFragment();
            File cur_f = new File( cur );
            file_name = cur_f.getName();
            String parent_dir = cur_f.getParent();
            parent_uri = uri.buildUpon().fragment( parent_dir != null ? parent_dir : "" ).build();
        }
        else if( ca instanceof SAFAdapter ) {
            parent_uri = SAFAdapter.getParent( uri );
        }
        else if( "gdrive".equals( scheme ) ) {
            ca_pos = -1; // too complex parent folder calculation
        }
        else if( "box".equals( scheme ) ) {
            ca_pos = -1; // too complex parent folder calculation
        }
        else if( "content".equals( scheme ) ) {
            ca_pos = -1; // too complex parent folder calculation
        }
        else {
            ub.path( "/" );
            List<String> ps = uri.getPathSegments();
            int n = ps.size();
            if( n > 0 ) n--;
            for( int i = 0; i < n; i++ ) 
                ub.appendPath( ps.get( i ) );
            parent_uri = ub.build();
            file_name = ps.get( ps.size()-1 );
        }
        Log.d( TAG, "Parent dir: " + parent_uri );
        if( ca == null ) return;
        ca.Init( stub );
        ca.setMode( CommanderAdapter.MODE_SORTING | CommanderAdapter.MODE_SORT_DIR, mode );
        
        Credentials crd = null; 
        try {
            crd = (Credentials)intent.getParcelableExtra( Credentials.KEY );
            ca.setCredentials( crd );
        } catch( Exception e ) {
            Log.e( TAG, "on taking credentials from parcel", e );
        }

        image_view.invalidate();
        if( parent_uri != null && ca_pos > 0 ) {
            ca.setUri( parent_uri );
            Log.d( TAG, "do read list" );
            stub.reload_after_dir_read_done = true;
            ca.readSource( null, null );
        }
        else
            new LoaderThread( uri, file_name ).start();
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
            case R.id.info:
                showInfo();
                break;
            case R.id.send_to:
                sendTo();
                break;
            case R.id.open_with:
                openWith();
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
            //Log.v( TAG, "Bitmap is ready" );
            hideWait();
            if( bmp != null ) {
                image_view.setVisibility( View.VISIBLE );
                image_view.setImageBitmap( mbScaleDownBitmap( bmp ) );
                
                if( name != null ) {
                    name_view.setTextColor( Color.WHITE );
                    name_view.setText( name );
                    if( name != null ) file_name = name;
                }
                return;
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
    }

    private final void rotate( boolean clockwise ) {
        new RotateTask( image_view.getDrawable(), clockwise ).execute();
    }

    public class RotateTask extends AsyncTask<Void, Void, Void> {
        private Drawable from_view;
        private WeakReference<Bitmap> wrRotatedBitmap;
        private boolean clockwise;
    
        public RotateTask( Drawable from_view, boolean clockwise ) {
            this.from_view = from_view;
            this.clockwise = clockwise;
        }
    
        @Override
        protected void onPreExecute() {
        }
    
        @Override
        protected Void doInBackground(Void... params) {
            try {
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
                float degrees = clockwise ? 90 : 270;
                wrRotatedBitmap = new WeakReference<Bitmap>( PictureViewer.rotateBitmap( old_bmp, degrees ) );
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
                PictureViewer.this.setBitmapToView( bmp, null );
        }
    }    

    public static Bitmap rotateBitmap( Bitmap old_bmp, float degrees ) {
        final Matrix m = new Matrix();
        m.postRotate( degrees );
        final int old_w = old_bmp.getWidth(); 
        final int old_h = old_bmp.getHeight();
        for( int i = 1; i <= 8; i <<= 1 ) {
            try {
                if( i > 1 ) {
                    float scale = 1.f / i;
                    m.postScale( scale, scale );
                }
                Bitmap new_bmp = Bitmap.createBitmap( old_bmp, 0, 0, old_w, old_h, m, false );
                if( new_bmp != null ) {
                    old_bmp.recycle();
                    return new_bmp;
                }
            } catch( OutOfMemoryError e ) {}
        }
        return null;
    }

    public static Bitmap mbScaleDownBitmap( Bitmap old_bmp ) {
        final int MAX = 4096;
        final int old_w = old_bmp.getWidth(); 
        final int old_h = old_bmp.getHeight();
        
        if( old_w < MAX && old_h < MAX )
            return old_bmp;
        int max_d = Math.max( old_w, old_h );
        int div = max_d / MAX + 1;        
        int new_w = old_w / div; 
        int new_h = old_h / div; 
        
        return Bitmap.createScaledBitmap( old_bmp, new_w, new_h, true );
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
                if( PictureViewer.this.ca == null ) {
                    Log.e( TAG, "No adapter instance!" );
                    return;
                }
                if( ca.hasFeature( CommanderAdapter.Feature.LOCAL ) ) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inTempStorage = buf;
                    InputStream is = null;
                    ContentResolver cr = ( ca == null && ContentResolver.SCHEME_CONTENT.equals( scheme ) )
                            ? getContentResolver() : null;
                    for( int b = 1; b < 0x80000; b <<= 1 ) {
                        try {
                            options.inSampleSize = b;
                            if( ca != null )
                                is = PictureViewer.this.ca.getContent( u );
                            else if( cr != null )
                                is = cr.openInputStream( u );
                            if( is == null ) {
                                Log.e( TAG, "Failed to get the content stream for: " + u );
                                return;
                            }
                            bmp = BitmapFactory.decodeStream( is, null, options );
                            if( bmp == null ) continue;
                            if( !Utils.str( scheme ) || "file".equals( scheme ) ) {
                                String file_path = u.getPath();
                                float degrees = ImageInfo.getImageFileOrientationDegree( file_path );
                                if( degrees > 0 ) {
                                    Log.d( TAG, "Rotating " + degrees );
                                    Bitmap rbmp = PictureViewer.rotateBitmap( bmp, degrees );
                                    if( rbmp != null )
                                        bmp = rbmp;
                                }
                            }
                            PictureViewer.this.h.post( new Runnable() {
                                  @Override
                                  public void run() {
                                    PictureViewer.this.setBitmapToView( bmp, name_to_show );
                                  }
                              });                
                            return;
                        } catch( Throwable e ) {
                        } finally {
                            if( is != null )
                                PictureViewer.this.ca.closeStream( is );
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
                            
                            String fn = u.getLastPathSegment();
                            if( !Utils.str( fn ) )
                                fn = getIntent().getType().replace( '/', '_' );
                            f = new File( pictvw_f, fn );
                            PictureViewer.this.temp_file_path = f.getAbsolutePath();
                            fos = new FileOutputStream( f );
                            // input - the content from adapter
                            is = PictureViewer.this.ca.getContent( u );
                            if( is == null ) return;
                            int n;
                            boolean available_supported = is.available() > 0;
                            while( ( n = is.read( buf ) ) != -1 ) {
                                Thread.sleep( 1 );
                                fos.write( buf, 0, n );
                                if( available_supported ) {
                                    for( int i = 1; i <= 10; i++ ) {
                                        if( is.available() > 0 ) break;
                                        Thread.sleep( 20 * i );
                                    }
                                    if( is.available() == 0 ) {
                                        break;
                                    }
                                }
                            }
                        } catch( Throwable e ) {
                            throw e;
                        } finally {
                            if( PictureViewer.this.ca != null && is != null ) 
                                PictureViewer.this.ca.closeStream( is );
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
                            if( bmp == null ) continue;
                            {
                                float degrees = ImageInfo.getImageFileOrientationDegree( f.getAbsolutePath() );
                                Log.d( TAG, "Rotating " + degrees );
                                if( degrees > 0 ) {
                                    Bitmap rbmp = PictureViewer.rotateBitmap( bmp, degrees );
                                    if( rbmp != null )
                                        bmp = rbmp;
                                }
                            }
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
            int thldX = v.getWidth() / 50;
            int thldY = v.getHeight() / 50;

            if( thldY < 50 ) {
                float x = event.getX();
                float dx = x - last.x;
                float adx = Math.abs( dx );
                if( adx > thldX )
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
            .setIcon( android.R.drawable.ic_dialog_alert )
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

    private final void loadNext( boolean forward ) {
        loadNext( forward ? 1 : -1, false );
    }

    public final void loadNext( int dir, boolean exit_at_end ) {
        //Log.d( TAG, "pos=" + ca_pos + " forward=" + dir );
        if( ca_pos < 0 || ca == null ) {
            if( exit_at_end ) this.finish();
            return;
        }
        int orig_pos = ca_pos; 
        while( true ) {
            ca_pos += dir;
            if( ca_pos <= 0 ) {
                ca_pos = orig_pos;
                if( exit_at_end ) this.finish();
                return;
            }
            Uri pos_uri = ca.getItemUri( ca_pos );
            if( pos_uri == null ) {
                ca_pos = orig_pos;
                if( exit_at_end ) this.finish();
                return;
            }
            this.uri = pos_uri;
            Log.d( TAG, "Next uri: " + pos_uri ); 
            String name = ca.getItemName( ca_pos, false );
            if( name == null ) {
                Log.e( TAG, "Something is wrong, exiting" );
                return;
            }
            String mime = Utils.getMimeByExt( Utils.getFileExt( name ) );
            Log.d( TAG, "Next name: " + name + " mime: " + mime );
            if( mime.startsWith( "image/" ) ) {
                Log.d( TAG, "new pos=" + ca_pos );
                name_view.setTextColor( Color.GRAY );
                name_view.setText( getString( R.string.wait ) );
                new LoaderThread( pos_uri, name ).start();
                return;
            }
            if( dir == 0 )
                return;
        }
    }

    private final static String getFilePath( Uri uri, String temp_file_path ) {
        String file_path = null;
        if( temp_file_path != null )
            file_path = temp_file_path;
        else {
            String scheme = uri.getScheme();
            if( CA.isLocal( scheme ) || "ms".equals( scheme ) ||
              ( ContentResolver.SCHEME_CONTENT.equals( scheme ) && FileProvider.AUTHORITY.equals( uri.getHost() ) ) )
                file_path = uri.getPath();
        }
        return file_path;
    }
    
    @SuppressLint("InflateParams")
    private final void showInfo() {
        AlertDialog.Builder builder = new AlertDialog.Builder( this )
        .setTitle( getString( R.string.info ) )
        .setIcon( android.R.drawable.ic_dialog_info )
        .setPositiveButton( R.string.dialog_ok, null );
        LayoutInflater inflater = (LayoutInflater)this.getSystemService( LAYOUT_INFLATER_SERVICE );
        View layout = inflater.inflate( R.layout.textvw, null );
        TextView text_view = (TextView)layout.findViewById( R.id.text_view );
        String info_text = getInfo();
        if( info_text == null )
            info_text = getString( R.string.nothing );
        text_view.setText( Html.fromHtml( info_text ));
        builder.setView(layout);        
        builder.show();
    }

    private final String getInfo() {
        String info_text = null;
        String file_path = getFilePath( uri, temp_file_path );
        if( file_path != null ) {
            String path_to_show = temp_file_path != null ? uri.toString() : file_path;
            info_text = "<b>File:</b> <small>" + path_to_show + "</small><br/>";
            String exif_text = ImageInfo.getImageFileInfoHTML( file_path );
            if( exif_text != null )
                info_text += exif_text;
            return info_text;
        }
        if( uri != null && ContentResolver.SCHEME_CONTENT.equals( uri.getScheme() ) ) {
            String[] projection = {
                MediaColumns.DATA,
                MediaColumns.SIZE,
                MediaColumns.TITLE,
                MediaColumns.WIDTH,
                MediaColumns.HEIGHT,
                Images.ImageColumns.DATE_TAKEN,
                Images.ImageColumns.ORIENTATION,
                Images.ImageColumns.DESCRIPTION,
                OpenableColumns.DISPLAY_NAME
            };
            Cursor cursor = null;
            ContentResolver cr = null;
            try {
                cr = this.getContentResolver();
                cursor = cr.query( uri, projection, null, null, null );
                if( cursor != null && cursor.getCount() > 0 ) {
                    cursor.moveToFirst();
                    boolean no_m = false;
                    String exif_text = null;
                    String path = cursor.getString( cursor.getColumnIndex( MediaColumns.DATA ) );
                    String path_to_show = path != null ? path : file_name;
                    if( path_to_show == null ) {
                        path_to_show = cursor.getString( cursor.getColumnIndex( OpenableColumns.DISPLAY_NAME ) );
                    }
                    info_text = path_to_show != null ? "<b>File:</b> <small>" + path_to_show + "</small><br/>" : "";
                    if( path != null ) {
                        exif_text = ImageInfo.getImageFileInfoHTML( path );
                    } else {
                        InputStream is = ca.getContent( uri );
                        if( is != null )
                            exif_text = ImageInfo.getImageStreamInfoHTML( is );
                    }
                    if( exif_text != null ) {
                        info_text += exif_text;
                        no_m = true;
                    }
                    StringBuilder sb = new StringBuilder();
                    for( String col : projection ) {
                        String val = cursor.getString( cursor.getColumnIndex( col ) );
                        if( val == null ) continue;
                        if( col.equals( MediaColumns.DATA ) ) continue;
                        if( col.equals( OpenableColumns.DISPLAY_NAME ) ) continue;
                        sb.append( "<br/>" );
                        if( col.equals( MediaColumns.SIZE ) ) {
                            sb.append( "<b>Size" );
                            val = Utils.getHumanSize( Long.parseLong( val ), false ) + "B";
                        }
                        else if( col.equals( MediaColumns.TITLE ) ) 
                            sb.append( "<b>Title" );
                        else if( col.equals( MediaColumns.WIDTH ) ) {
                            if( no_m ) continue;
                            sb.append( "<b>Width" );
                        }
                        else if( col.equals( MediaColumns.HEIGHT) ) {
                            if( no_m ) continue;
                            sb.append( "<b>Height" );
                        }
                        else if( col.equals( Images.ImageColumns.ORIENTATION ) ) {
                            if( no_m ) continue;
                            sb.append( "<b>Orientation" );
                        }
                        else if( col.equals( Images.ImageColumns.DESCRIPTION ) ) 
                            sb.append( "<b>Description" );
                        else if( col.equals( Images.ImageColumns.DATE_TAKEN ) ) {
                            if( no_m ) continue;
                            sb.append( "<b>Date taken" );
                            val = (new Date(Long.parseLong( val ) )).toString();
                        }
                        sb.append( ":</b> " );
                        sb.append( val );
                    }
                    info_text += sb.toString();
                }
            } catch( Throwable e ) {
                Log.e( TAG, "on query", e );
            }
            finally {
                cursor.close();
            }
        }
        return info_text;
    }
    
    private final void sendTo() {
        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
        boolean use_content = shared_pref.getBoolean( "send_content", true );
        Uri mod_uri = changeUri( uri, use_content );
        if( mod_uri == null ) return;
        Intent out = new Intent( Intent.ACTION_SEND );
        out.setType( getIntent().getType() );
        out.putExtra( Intent.EXTRA_SUBJECT, file_name );
        out.putExtra( Intent.EXTRA_STREAM, mod_uri );
        out.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION );
        Log.d( TAG, "Intent to send: " + out.toString() );
        this.startActivity( Intent.createChooser( out, this.getString( R.string.send_title ) ) );
    }

    public final void openWith() {
        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
        boolean use_content = shared_pref.getBoolean( "open_content", true );
        Intent in = getIntent(), out = new Intent( Intent.ACTION_VIEW );
        Log.d( TAG, "Opening intent: " + in.toString() );
        Uri mod_uri = changeUri( uri, use_content );
        if( mod_uri == null ) return;
        out.setDataAndType( mod_uri, "image/*" );
        out.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                      Intent.FLAG_GRANT_WRITE_URI_PERMISSION );
        Log.d( TAG, "Intent to open: " + out.toString() );
        if( Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT ) {
            // This will open the "Complete action with" dialog if the user doesn't have a default app set.
            this.startActivity( out );
        } else {
            this.startActivity( Intent.createChooser( out, this.getString( R.string.open_title ) ) );
        }
    }

    private final Uri changeUri( Uri u, boolean use_content ) {
        if( u == null ) return null;
        if( use_content ) {
            if( !ContentResolver.SCHEME_CONTENT.equals( u.getScheme() ) ) {
                String file_path = getFilePath( u, temp_file_path );
                if( file_path == null ) {
                    Log.e( TAG, "No file path" );
                    return null;
                }
                Uri ms_uri = MSAdapter.getContentUri( u.getPath(), this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI );
                if( ms_uri != null )
                    return ms_uri;
                else
                    return FileProvider.makeURI( file_path );
            }
            return u;
        } else {
            String file_path = getFilePath( u, temp_file_path );
            if( file_path == null ) {
                if( ca instanceof SAFAdapter )
                    file_path = SAFAdapter.getPath( this, u, true );
            }
            if( file_path != null )
                return Uri.parse( "file://" + Utils.escapePath( file_path ) );
        }
        return null;
    }
    
    private class CommanderStub implements Commander {
        boolean reload_after_dir_read_done = false;

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
            if( m.what == OPERATION_COMPLETED ) {
                Log.d( TAG, "Completed" );
                if( reload_after_dir_read_done )
                    loadNext( 0, true );
                reload_after_dir_read_done = false;
            }
            if( m.obj != null ) {
                String s = null;
                if( m.obj instanceof Bundle )
                    s = ( (Bundle)m.obj ).getString( MESSAGE_STRING );
                else if( m.obj instanceof String ) {
                    s = (String)m.obj;
                }
                if( Utils.str( s ) ) {
                    boolean html = Utils.isHTML( s );
                    Toast.makeText( PictureViewer.this, html ? Html.fromHtml( s ) : s, Toast.LENGTH_LONG ).show();
                }
            }
            return false;
        }
        @Override
        public boolean startEngine( Engine e ) {
            e.setHandler( new Handler() {
                @Override
                public void handleMessage( Message msg ) {
                    if( msg.what == OPERATION_COMPLETED_REFRESH_REQUIRED ) {
                        Log.d( TAG, "Completed, need refresh" );
                        reload_after_dir_read_done = true;
                        ca.readSource( null, null );
                        notifyMe( msg );
                        return;
                    }
                }
            });
            e.start();
            return true;
        }
    }
}
