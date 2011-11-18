package com.ghostsq.commander;

import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;
import android.view.Display;
import android.widget.ImageView;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class PictureViewer extends Activity {
    private final static String TAG = "PictureViewerActivity";
    private byte[] buf;
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        Log.v( TAG, "onCreate" );
        super.onCreate( savedInstanceState );
        try {
            setContentView( R.layout.pictvw );
            buf = new byte[100*1024];
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        Log.v( TAG, "onStart" );
        super.onResume();
        ImageView image_view = (ImageView)findViewById( R.id.image_view );
        Uri u = getIntent().getData();
        if( u == null ) return;
        String path = u.getPath();
        if( path == null ) return;
        try {
            Display display = getWindowManager().getDefaultDisplay(); 
            int width = display.getWidth();
            int height = display.getHeight();
            boolean by_height = height < width;
            
            Log.v( TAG, "w=" + width + ", h=" + height );
            
            FileInputStream in = new FileInputStream( path );
            BitmapFactory.Options options = new BitmapFactory.Options();
            
            options.inSampleSize = 1;
            options.inJustDecodeBounds = true;
            options.outWidth = 0;
            options.outHeight = 0;
            options.inTempStorage = buf;
            
            BitmapFactory.decodeStream( in, null, options);
            in.close();
            if( options.outWidth > 0 && options.outHeight > 0 ) {
                int factor = by_height ? options.outHeight / height : options.outWidth / width;
                int b;
                for( b = 0x8000000; b > 0; b >>= 1 )
                    if( b < factor ) break;
                options.inSampleSize = b;
                options.inJustDecodeBounds = false;
                image_view.setImageBitmap( BitmapFactory.decodeFile( path, options ) );
            }
            else
                image_view.setImageBitmap( BitmapFactory.decodeFile( path ) );
        } catch( Exception e ) {
            Log.e( TAG, u.toString(), e );
        }
    }
    
}
