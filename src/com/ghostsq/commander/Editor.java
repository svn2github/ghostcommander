package com.ghostsq.commander;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Editor extends Activity implements TextWatcher {
    private final static String TAG = "EditorActivity";
    private final static String SP_ENC = "encoding";
	final static int MENU_SAVE = 214, MENU_SVAS = 212, MENU_RELD = 439, MENU_WRAP = 241, MENU_ENC = 363, MENU_EXIT = 323;
//	final static String URI = "URIfileForEdit";
	private EditText te;
	private boolean horScroll = true;
	private Uri uri;
	private CommanderAdapter ca;
	private boolean dirty = false;
	public  String encoding;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        try {
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            if( prefs != null )
                encoding = prefs.getString( SP_ENC, "" );
            boolean ct_enabled = requestWindowFeature( Window.FEATURE_CUSTOM_TITLE );
            setContentView(R.layout.editor);
            te = (EditText)findViewById( R.id.editor );
            te.addTextChangedListener( this );
            
            SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
            int fs = Integer.parseInt( shared_pref != null ? shared_pref.getString( "font_size", "12" ) : "12" );
            te.setTextSize( fs );
            
            SharedPreferences color_pref = getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
            int bg_color = Prefs.getDefaultColor( Prefs.BGR_COLORS );
            int fg_color = Prefs.getDefaultColor( Prefs.FGR_COLORS );
            bg_color = color_pref.getInt( Prefs.BGR_COLORS, bg_color );
            fg_color = color_pref.getInt( Prefs.FGR_COLORS, fg_color );
            te.setBackgroundColor( bg_color );
            te.setTextColor( fg_color );            
            
            if( ct_enabled ) {
                getWindow().setFeatureInt( Window.FEATURE_CUSTOM_TITLE, R.layout.atitle );
                TextView act_name_tv = (TextView)findViewById( R.id.act_name );
                if( act_name_tv != null )
                    act_name_tv.setText( R.string.editor_label );
            }
            uri = getIntent().getData();
            if( !loadData() )
                finish();
            TextView file_name_tv = (TextView)findViewById( R.id.file_name );
            if( file_name_tv!= null )
                file_name_tv.setText( " - " + uri.getPath() );
        }
        catch( Exception e ) {
            Log.e( TAG, "", e );
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        editor.putString( SP_ENC, encoding == null ? "" : encoding );
        editor.commit();
    }

    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        switch( keyCode ) {
        case KeyEvent.KEYCODE_BACK:
            if( dirty ) {
                DialogInterface.OnClickListener ocl = new DialogInterface.OnClickListener() {
                        public void onClick( DialogInterface dialog, int which_button ) {
                            if( which_button == DialogInterface.BUTTON_POSITIVE ) {
                                try {
                                    if( !Editor.this.Save( uri ) )
                                        Editor.this.showMessage( Editor.this.getString( R.string.cant_save ) );
                                } catch( Exception e ) {
                                    e.printStackTrace();
                                }
                            }
                            Editor.this.finish();
                        }
                    };
                new AlertDialog.Builder( this )
                        .setIcon( android.R.drawable.ic_dialog_alert )
                        .setTitle( R.string.save )
                        .setMessage( R.string.not_saved )
                        .setPositiveButton( R.string.save, ocl )
                        .setNegativeButton( R.string.dialog_cancel, ocl )
                        .show();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
        
    @Override
    public boolean onPrepareOptionsMenu( Menu menu ) {
        menu.clear();
        menu.add( Menu.NONE, MENU_SAVE, Menu.NONE, getString( R.string.save     ) ).setIcon( android.R.drawable.ic_menu_save );
        menu.add( Menu.NONE, MENU_SVAS, Menu.NONE, getString( R.string.save_as  ) ).setIcon( android.R.drawable.ic_menu_save );
        menu.add( Menu.NONE, MENU_RELD, Menu.NONE, getString( R.string.revert   ) ).setIcon( android.R.drawable.ic_menu_revert );
        menu.add( Menu.NONE, MENU_WRAP, Menu.NONE, getString( R.string.wrap     ) ).setIcon( R.drawable.wrap );
        menu.add( Menu.NONE, MENU_ENC,  Menu.NONE, Utils.getEncodingDescr( this, encoding, 
                                                      Utils.ENC_DESC_MODE_BRIEF ) ).setIcon(android.R.drawable.ic_menu_sort_alphabetically );
        menu.add( Menu.NONE, MENU_EXIT, Menu.NONE, getString( R.string.exit     ) ).setIcon( android.R.drawable.ic_notification_clear_all );
	    return true;
    }
    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        switch( item.getItemId() ) {
        case MENU_SAVE:
            if( !Save( uri ) )
                showMessage( getString( R.string.cant_save ) );
            return true;
        case MENU_SVAS: 
            try {
                LayoutInflater factory = LayoutInflater.from( this );
                View iv = factory.inflate( R.layout.input, null );
                if( iv != null ) {
                    TextView prompt = (TextView)iv.findViewById( R.id.prompt );
                    final EditText edit   = (EditText)iv.findViewById( R.id.edit_field );
                    prompt.setText( R.string.newf_prompt );
                    edit.setText( uri.toString() );
                    new AlertDialog.Builder( this )
                        .setTitle( R.string.save_as )
                        .setView( iv )
                        .setPositiveButton( R.string.save, new DialogInterface.OnClickListener() {
                            public void onClick( DialogInterface dialog, int i ) {
                                try {
                                    if( !Editor.this.Save( Uri.parse( edit.getText().toString() ) ) )
                                        Editor.this.showMessage( Editor.this.getString( R.string.cant_save ) );
                                } catch( Exception e ) {
                                    e.printStackTrace();
                                }
                            }
                        } ).setNegativeButton( R.string.dialog_cancel, null ).show();
                }
            } catch( Throwable e ) {
                Log.e( TAG, "", e );
            }
            return true;
        case MENU_RELD:
            loadData();
            return true;
        case MENU_ENC: {
                int cen = Integer.parseInt( Utils.getEncodingDescr( this, encoding, Utils.ENC_DESC_MODE_NUMB ) );
                new AlertDialog.Builder( this )
                    .setTitle( R.string.encoding )
                    .setSingleChoiceItems( R.array.encoding, cen, new DialogInterface.OnClickListener() {
                        public void onClick( DialogInterface dialog, int i ) {
                            dialog.dismiss();
                            encoding = getResources().getStringArray( R.array.encoding_vals )[i];
                            Log.i( TAG, "Chosen encoding: " + encoding );
                            Editor.this.showMessage( getString( R.string.encoding_set, encoding ) );
                        }
                    }).show();
            }
            return true;
        case MENU_WRAP: 
            try {
                EditText te = (EditText)findViewById( R.id.editor );
                horScroll = horScroll ? false : true;
                te.setHorizontallyScrolling( horScroll ); 
            }
            catch( Exception e ) {
                System.err.println("Exception: " + e );
            }
            return true;
        case MENU_EXIT:
            // TODO: warn if text was changed, but not saved!
            finish();
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private final void showMessage( String s ) {
    	Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
    
    private final boolean loadData() {
        if( uri != null ) { 
            try {
                int type_id = CA.GetAdapterTypeId( uri.getScheme() );
                ca = CA.CreateAdapterInstance( type_id, this );
                if( ca != null ) {
                    InputStream is = ca.getContent( uri );
                    if( is != null ) {
                        CharSequence cs = Utils.readStreamToBuffer( is, encoding );
                        ca.closeStream( is );
                        te.setText( cs );
                        dirty = false; 
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

    private final boolean Save( Uri save_uri )
    {
        if( save_uri == null || ca == null ) return false;
        OutputStream os = ca.saveContent( save_uri );
        if( os == null ) return false;
        try {
            final int BUF_SIZE = 16*1024;
            OutputStreamWriter osw = encoding != null && encoding.length() != 0 ?
                    new OutputStreamWriter( os, encoding ) :
                    new OutputStreamWriter( os );
                    
            Editable e = te.getText();
            int len = e.length();
            if( len < BUF_SIZE ) {
                osw.write( e.toString() );
                osw.flush();
            } else {
                char[] chars = new char[BUF_SIZE];
                int start = 0, end = BUF_SIZE;
                while( start < len-1 ) {
                    e.getChars( start, end, chars, 0 );
                    osw.write( chars, 0, end - start );
                    start = end;
                    end += BUF_SIZE;
                    if( end > len )
                        end = len-1;
                }
            }
            //Log.v( TAG, "Data is sent to the stream" );            
            ca.closeStream( os );
            dirty = false; 
            File f = new File( save_uri.getPath() );
            showMessage( getString( R.string.saved, f.getName() ) );
            return true;
        } catch( Throwable e ) {
            Log.e( TAG, Favorite.screenPwd( save_uri ), e );
        }
        return false;
    }
    @Override
    public void afterTextChanged( Editable s ) {
        dirty = true;
    }
    @Override
    public void beforeTextChanged( CharSequence s, int start, int count, int after ) {
    }
    @Override
    public void onTextChanged( CharSequence s, int start, int before, int count ) {
    }
}
