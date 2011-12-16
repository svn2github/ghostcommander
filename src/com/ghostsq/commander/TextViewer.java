package com.ghostsq.commander;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.root.ExecEngine;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;

public class TextViewer extends Activity {
    private final static String TAG = "TextViewerActivity";
    private final static String SP_ENC = "encoding";
    final static int MENU_BOT = 595, MENU_TOP = 590, MENU_ENC = 363;
    private ScrollView scrollView;
    private Uri uri;
    public  String encoding;
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        boolean ct_enabled = requestWindowFeature( Window.FEATURE_CUSTOM_TITLE );
        setContentView( R.layout.textvw );
        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
        int fs = Integer.parseInt( shared_pref != null ? shared_pref.getString( "font_size", "12" ) : "12" );
        TextView text_view = (TextView)findViewById( R.id.text_view );
        text_view.setTextSize( fs );
        
        if( ct_enabled ) {
            getWindow().setFeatureInt( Window.FEATURE_CUSTOM_TITLE, R.layout.atitle );
            TextView act_name_tv = (TextView)findViewById( R.id.act_name );
            if( act_name_tv != null )
                act_name_tv.setText( R.string.textvw_label );
        }
        scrollView = (ScrollView)findViewById( R.id.scroll_view );
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences prefs = getPreferences( MODE_PRIVATE );
        if( prefs != null )
            encoding = prefs.getString( SP_ENC, "" );
        uri = getIntent().getData();
        if( !loadData() )
            finish();
        TextView file_name_tv = (TextView)findViewById( R.id.file_name );
        String path = uri.getPath();
        if( file_name_tv != null && path != null )
            file_name_tv.setText( " - " + path );
        
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        editor.putString( SP_ENC, encoding == null ? "" : encoding );
        editor.commit();
    }
    
    @Override
    protected void onSaveInstanceState( Bundle toSaveState ) {
        Log.i( TAG, "Saving State: " + encoding );
        toSaveState.putString( SP_ENC, encoding == null ? "" : encoding );
        super.onSaveInstanceState( toSaveState );
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        if( savedInstanceState != null )
            encoding = savedInstanceState.getString( SP_ENC );
        Log.i( TAG, "Restored State " + encoding );
        super.onRestoreInstanceState( savedInstanceState );
    }
    
    @Override
    public boolean onPrepareOptionsMenu( Menu menu ) {
        menu.clear();
        menu.add( Menu.NONE, MENU_TOP, Menu.NONE, getString( R.string.go_top   ) ).setIcon( android.R.drawable.ic_media_previous );
        menu.add( Menu.NONE, MENU_BOT, Menu.NONE, getString( R.string.go_end   ) ).setIcon( android.R.drawable.ic_media_next );
        menu.add( Menu.NONE, MENU_ENC, Menu.NONE, Utils.getEncodingDescr( this, encoding, 
                                                     Utils.ENC_DESC_MODE_BRIEF ) ).setIcon( android.R.drawable.ic_menu_sort_alphabetically );
        return true;
    }
    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        switch( item.getItemId() ) {
        case MENU_BOT:
            scrollView.fullScroll( View.FOCUS_DOWN );
            return true;
        case MENU_TOP:
            scrollView.fullScroll( View.FOCUS_UP );
            return true;
        case MENU_ENC: {
                int cen = Integer.parseInt( Utils.getEncodingDescr( this, encoding, Utils.ENC_DESC_MODE_NUMB ) );
                new AlertDialog.Builder( this )
                    .setTitle( R.string.encoding )
                    .setSingleChoiceItems( R.array.encoding, cen, new DialogInterface.OnClickListener() {
                        public void onClick( DialogInterface dialog, int i ) {
                            encoding = getResources().getStringArray( R.array.encoding_vals )[i];
                            Log.i( TAG, "Chosen encoding: " + encoding );
                            dialog.dismiss();
                            loadData();
                        }
                    }).show();
            }
            return true;
            /*
        case WRAP: 
            try {
                EditText te = (EditText)findViewById( R.id.editor );
                horScroll = horScroll ? false : true;
                te.setHorizontallyScrolling( horScroll ); 
            }
            catch( Exception e ) {
                System.err.println("Exception: " + e );
            } 
            */
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private final boolean loadData() {
        if( uri != null ) { 
            try {
                String scheme = uri.getScheme();
                if( "exec".equals( scheme ) ) {
                    
                    Intent i = getIntent();
                    if( i == null ) return false;
                    String cmd = i.getStringExtra( "cmd" );
                    
                    Context ctx = this;
                    ExecEngine ee = new ExecEngine( ctx, new Handler() {
                                @Override
                                public void handleMessage( Message msg ) {
                                    try {
                                        Bundle b = msg.getData();
                                        String r = b.getString( CommanderAdapterBase.NOTIFY_STR );
                                        if( r != null ) {
                                            TextView text_view = (TextView)findViewById( R.id.text_view );
                                            text_view.setText( r );
                                        }
                                    } catch( Exception e ) {
                                        e.printStackTrace();
                                    }
                                }
                            }, null, cmd, false,  500 ); 
                    ee.start();
                    return true;
                }               
                int type_id = CA.GetAdapterTypeId( scheme );
                CommanderAdapter ca = CA.CreateAdapterInstance( type_id, this );
                if( ca != null ) {
                    InputStream is = ca.getContent( uri );
                    if( is != null ) {
                        CharSequence cs = Utils.readStreamToBuffer( is, encoding );
                        ca.closeStream( is );
                        TextView text_view = (TextView)findViewById( R.id.text_view );
                        text_view.setText( cs );
                        return true;
                    }
                }
            } catch( OutOfMemoryError e ) {
                Log.e( TAG, uri.toString(), e );
                Toast.makeText(this, getString( R.string.too_big_file, uri.getPath() ), Toast.LENGTH_LONG).show();
            } catch( Throwable e ) {
                Log.e( TAG, uri.toString(), e );
                Toast.makeText(this, getString( R.string.failed ) + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
        return false;
    }
}
