package com.ghostsq.commander;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.ghostsq.commander.toolbuttons.ToolButtonsProps;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

public class Prefs extends PreferenceActivity implements Preference.OnPreferenceClickListener,
                                                       RGBPickerDialog.ColorChangeListener  
{
    private static final String TAG = "GhostCommander.Prefs"; 
    public  static final String COLORS_PREFS = "colors"; 
    public  static final String BGR_COLORS = "bgr_color_picker"; 
    public  static final String FGR_COLORS = "fgr_color_picker"; 
    public  static final String SEL_COLORS = "sel_color_picker"; 
    public  static final String SFG_COLORS = "sfg_color_picker"; 
    public  static final String CUR_COLORS = "cur_color_picker";
    public  static final String TTL_COLORS = "ttl_color_picker";
    public  static final String BTN_COLORS = "btn_color_picker";
    public  static final String TOOLBUTTONS = "toolbar_preference";
    private SharedPreferences color_pref = null;
    private String pref_key = null;
    
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        Utils.changeLanguage( this );
        super.onCreate( savedInstanceState );
        
        // Load the preferences from an XML resource
        addPreferencesFromResource( R.xml.prefs );
        Preference color_picker_pref;
        color_picker_pref = (Preference)findPreference( BGR_COLORS );
        if( color_picker_pref != null )
            color_picker_pref.setOnPreferenceClickListener( this );
        color_picker_pref = (Preference)findPreference( FGR_COLORS );
        if( color_picker_pref != null )
            color_picker_pref.setOnPreferenceClickListener( this );
        color_picker_pref = (Preference)findPreference( SEL_COLORS );
        if( color_picker_pref != null )
            color_picker_pref.setOnPreferenceClickListener( this );
        color_picker_pref = (Preference)findPreference( SFG_COLORS );
        if( color_picker_pref != null )
            color_picker_pref.setOnPreferenceClickListener( this );
        color_picker_pref = (Preference)findPreference( CUR_COLORS );
        if( color_picker_pref != null )
            color_picker_pref.setOnPreferenceClickListener( this );
        color_picker_pref = (Preference)findPreference( TTL_COLORS );
        if( color_picker_pref != null )
            color_picker_pref.setOnPreferenceClickListener( this );
        color_picker_pref = (Preference)findPreference( BTN_COLORS );
        if( color_picker_pref != null )
            color_picker_pref.setOnPreferenceClickListener( this );

        Preference tool_buttons_pref;
        tool_buttons_pref = (Preference)findPreference( TOOLBUTTONS );
        if( tool_buttons_pref != null )
            tool_buttons_pref.setOnPreferenceClickListener( this );
    }


    @Override
    public boolean onPreferenceClick( Preference preference ) {
        try {
            pref_key = preference.getKey();
            if( TOOLBUTTONS.equals( pref_key ) ) {
                Intent intent = new Intent( Intent.ACTION_MAIN );
                intent.setClass( this, ToolButtonsProps.class );
                startActivity( intent );
            }
            else
            if( FGR_COLORS.equals( pref_key ) ) {
                Intent intent = new Intent( Intent.ACTION_MAIN );
                intent.setClass( this, FileTypes.class );
                startActivity( intent );
            }
            else {
                color_pref = getSharedPreferences( COLORS_PREFS, Activity.MODE_PRIVATE );
                int color = color_pref.getInt( pref_key, getDefaultColor( pref_key, false ) );
                new RGBPickerDialog( this, this, color, getDefaultColor( pref_key, true ) ).show();
            }
            return true;
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void colorChanged( int color ) {
        if( color_pref != null && pref_key != null ) {
            SharedPreferences.Editor editor = color_pref.edit();
            editor.putInt( pref_key, color );
            editor.commit();
            color_pref = null;
            pref_key = null;
        }
    }
    public int getDefaultColor( String key, boolean alt ) {
        return getDefaultColor( this, key, alt );
    }
    public static int getDefaultColor( Context ctx, String key, boolean alt ) {
        Resources r = ctx.getResources();
        if( key.equals( CUR_COLORS ) ) return alt ? r.getColor( R.color.cur_def ) : 0;
        if( key.equals( BTN_COLORS ) ) {
            final int GINGERBREAD = 9;
            if( android.os.Build.VERSION.SDK_INT >= GINGERBREAD )
                return r.getColor( R.color.btn_def );
            else
                return alt ? r.getColor( R.color.btn_odf ) : 0;
        }
        if( alt ) return 0;
        if( key.equals( BGR_COLORS ) ) return r.getColor( R.color.bgr_def );
        if( key.equals( SEL_COLORS ) ) return r.getColor( R.color.sel_def );
        if( key.equals( SFG_COLORS ) ) return r.getColor( R.color.fgr_def );
        if( key.equals( TTL_COLORS ) ) return r.getColor( R.color.ttl_def );
        if( key.equals( FGR_COLORS ) ) return r.getColor( R.color.fgr_def );
        return 0;
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        try {
            Utils.changeLanguage( this );
            // Inflate the currently selected menu XML resource.
            MenuInflater inflater = getMenuInflater();
            inflater.inflate( R.menu.pref_menu, menu );
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
            File save_dir = new File( Panels.DEFAULT_LOC, ".GhostCommander" );
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo( getPackageName(), 0 );
            File sp_dir = new File( ai.dataDir, "shared_prefs" );
            File f = new File( save_dir, "gc_prefs.zip" );
            switch( item.getItemId() ) {
            case R.id.save_prefs:
                if( !save_dir.exists() ) save_dir.mkdirs();
                savePrefs( sp_dir, f );
                break;
            case R.id.rest_prefs:
                restPrefs( f, sp_dir );
                finish();
                break;
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return true; 
    }

    public final void showMessage( String s ) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
    
    static final String[] prefFileNames = {
        "FileCommander.xml",                                                                                                                                        
        "com.ghostsq.commander_preferences.xml",                                                                                                                    
        "colors.xml",
        "ServerForm.xml",
        "Editor.xml",
        "TextViewer.xml"
    };    
    
    private final void savePrefs( File sp_dir, File f ) {
        try {
            if( f.exists() ) f.delete();
            ZipOutputStream zos = new ZipOutputStream( new FileOutputStream( f ) );
            for( int i = 0; i < prefFileNames.length; i++ ) {
                InputStream is = null; 
                try {
                    is = new FileInputStream( new File( sp_dir, prefFileNames[i] ) );
                } catch( Exception e ) {}
                if( is != null ) {
                    zos.putNextEntry( new ZipEntry( prefFileNames[i] ) );
                    Utils.copyBytes( is, zos );
                    is.close();
                    zos.closeEntry();
                }
            }
            zos.close();
            showMessage( getString( R.string.prefs_saved ) );
        } catch( Throwable e ) {
            e.printStackTrace();
        }
    }
    private final void restPrefs( File f, File sp_dir ) {
        try {
            ZipFile zf = new ZipFile( f );
            for( int i = 0; i < prefFileNames.length; i++ ) {
                ZipEntry ze = zf.getEntry( prefFileNames[i] );
                if( ze != null ) {
                    InputStream is = zf.getInputStream( ze );
                    OutputStream os = new FileOutputStream( new File( sp_dir, prefFileNames[i] ) );
                    Utils.copyBytes( is, os );
                    is.close();
                    os.close();
                }
            }
            showMessage( getString( R.string.prefs_restr ) );
        } catch( Throwable e ) {
            e.printStackTrace();
        }
    }
}