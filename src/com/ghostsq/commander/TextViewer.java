package com.ghostsq.commander;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;

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
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.CharBuffer;

public class TextViewer extends Activity {
    private final static String TAG = "TextViewerActivity";
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        Log.v( TAG, "onCreate" );
        super.onCreate( savedInstanceState );
        setContentView( R.layout.textvw );
    }

    @Override
    protected void onStart() {
        Log.v( TAG, "onStart" );
        super.onStart();
        Uri u = getIntent().getData();
        if( u != null ) { 
            try {
                int type_id = CA.GetAdapterTypeId( u.getScheme() );
                CommanderAdapter ca = CA.CreateAdapterInstance( type_id, this );
                if( ca != null ) {
                    CharSequence cs = ca.getFileContent( u );
                    if( cs != null ) {
                        TextView text_view = (TextView)findViewById( R.id.text_view );
    //                  text_view.setHorizontallyScrolling( true );
                      text_view.setText( cs );
                        return;
                    }
                }
                
            } catch( OutOfMemoryError e ) {
                Log.e( TAG, u.toString(), e );
                Toast.makeText(this, getString( R.string.too_big_file, u.getPath() ), Toast.LENGTH_LONG).show();
            } catch( Throwable e ) {
                Log.e( TAG, u.toString(), e );
                Toast.makeText(this, getString( R.string.failed ) + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
    
}
