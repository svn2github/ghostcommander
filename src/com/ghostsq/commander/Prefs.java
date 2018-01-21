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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

public class Prefs extends PreferenceActivity implements Preference.OnPreferenceClickListener,
                                                         RGBPickerDialog.ResultSink,
                                                         SelZoneDialog.ResultSink, 
                                                         OnPreferenceChangeListener
{
    private static final String TAG = "GhostCommander.Prefs"; 
    public  static final String COLORS_PREFS = "colors"; 
    public  static final String TOOLBUTTONS = "toolbar_preference";
    public  static final String SEL_ZONE = "selection_zone";
//    private SharedPreferences color_pref = null;
    private ColorsKeeper      ck;
    private String pref_key = null;
    
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences( this );
            Utils.setTheme( this, sp.getString( "color_themes", "d" ) );
            Utils.changeLanguage( this );
            boolean ab = Utils.setActionBar( this );
            super.onCreate( savedInstanceState );
            ck = new ColorsKeeper( this );

            if( !sp.getBoolean( PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false ) ) {
                SharedPreferences.Editor ed = sp.edit();
                ed.putBoolean( "open_content", android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.M );
                ed.putBoolean( PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, true );
                try {
                    ed.apply();
                } catch( AbstractMethodError unused ) {
                    ed.commit();
                }
            }                
                
            // Load the preferences from an XML resource
            addPreferencesFromResource( R.xml.prefs );
            Preference color_picker_pref;
            color_picker_pref = (Preference)findPreference( ColorsKeeper.BGR_COLORS );
            if( color_picker_pref != null )
                color_picker_pref.setOnPreferenceClickListener( this );
            color_picker_pref = (Preference)findPreference( ColorsKeeper.FGR_COLORS );
            if( color_picker_pref != null )
                color_picker_pref.setOnPreferenceClickListener( this );
            color_picker_pref = (Preference)findPreference( ColorsKeeper.SEL_COLORS );
            if( color_picker_pref != null )
                color_picker_pref.setOnPreferenceClickListener( this );
            color_picker_pref = (Preference)findPreference( ColorsKeeper.SFG_COLORS );
            if( color_picker_pref != null )
                color_picker_pref.setOnPreferenceClickListener( this );
            color_picker_pref = (Preference)findPreference( ColorsKeeper.CUR_COLORS );
            if( color_picker_pref != null )
                color_picker_pref.setOnPreferenceClickListener( this );
            color_picker_pref = (Preference)findPreference( ColorsKeeper.TTL_COLORS );
            if( color_picker_pref != null )
                color_picker_pref.setOnPreferenceClickListener( this );
            color_picker_pref = (Preference)findPreference( ColorsKeeper.BTN_COLORS );
            if( color_picker_pref != null )
                color_picker_pref.setOnPreferenceClickListener( this );

            ListPreference l = (ListPreference)findPreference( "color_themes" );
            if( l != null ) {
                if( android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ) {
                    Preference color_pref = findPreference( "colors" );
                    if( color_pref instanceof PreferenceGroup ) {
                        PreferenceGroup colors_grp = (PreferenceGroup)color_pref;
                        colors_grp.removePreference( l );
                    }
                } else
                    l.setOnPreferenceChangeListener( this );
            }
            
            Preference tool_buttons_pref = (Preference)findPreference( TOOLBUTTONS );
            if( tool_buttons_pref != null )
                tool_buttons_pref.setOnPreferenceClickListener( this );

            Preference sel_zone_pref = (Preference)findPreference( SEL_ZONE );
            if( sel_zone_pref != null )
                sel_zone_pref.setOnPreferenceClickListener( this );
            
            Preference show_actionbar = findPreference( "show_actionbar" );
            if( show_actionbar != null ) {
                if( android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ) {
                    PreferenceCategory cat_inps = (PreferenceCategory)findPreference("input_settings");
                    cat_inps.removePreference( show_actionbar );
                }
            }
        }
        catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        ck.restore();        
    }
    @Override
    protected void onPause() {
        try {
            super.onPause();
            ck.store();
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    @Override
    public boolean onPreferenceChange( Preference p, Object v ) {
        if( !"color_themes".equals( p.getKey() ) ) return true;
        if( !(v instanceof String) ) return false;
        ck.setTheme( (String)v );
        Utils.setTheme( this, (String)v );
        return true;
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
            if( SEL_ZONE.equals( pref_key ) ) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences( this );
                new SelZoneDialog( this, this, sp.getBoolean( SEL_ZONE + "_right", true ), 
                                               sp.getInt( SEL_ZONE + "_width", 50 ),
                                               ck.selColor, ck.bgrColor ).show();
            }
            else
            if( ColorsKeeper.FGR_COLORS.equals( pref_key ) ) {
                Intent intent = new Intent( Intent.ACTION_MAIN );
                intent.setClass( this, FileTypes.class );
                startActivity( intent );
            }
            else {
                String title = preference.getTitle().toString();
                new RGBPickerDialog( this, this, ck.getColor( pref_key ), 
                                             getDefaultColor( pref_key, true ), title ).show();
            }
            return true;
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void selZoneChanged( boolean atRight, int width ) {
        SharedPreferences.Editor sp_edit = PreferenceManager.getDefaultSharedPreferences( this ).edit();
        sp_edit.putBoolean( SEL_ZONE + "_right", atRight );
        sp_edit.putInt( SEL_ZONE + "_width", width );
        sp_edit.commit();
    }
    
    @Override
    public void colorChanged( int color ) {
        if( pref_key != null ) {
            ck.setColor( pref_key, color );
            pref_key = null;
        }
    }
    
    public int getDefaultColor( String key, boolean alt ) {
        return ColorsKeeper.getDefaultColor( this, key, alt );
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
                ck.restore();
                setResult( RESULT_OK, new Intent( FileCommander.PREF_RESTORE_ACTION ) );
                finish();
                break;
            case R.id.save_log:
                if( !save_dir.exists() ) save_dir.mkdirs();
                final String f_save_dir = save_dir.getAbsolutePath();
                final Context ctx = this; 
                new Thread( new Runnable() {
                    @Override
                    public void run() {
                        String fn = f_save_dir + "/logcat.txt";
                        try {
                            File f = new File( fn );
                            if( f.exists() ) f.delete();
                            Process prc = Runtime.getRuntime().exec( "logcat -d -v time -f " + fn + " *:v\n" );
                            Toast.makeText( ctx, prc.waitFor() == 0 ? ctx.getString( R.string.saved, fn ) :
                                ctx.getString( R.string.fail ), Toast.LENGTH_LONG ).show();
                        } catch( Exception e ) {
                            Log.e( TAG, fn, e );
                        }
                    }
                } ).run();
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
        "Favorites.xml",
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
            //Favorites.clearPrefs( this );
            File[] ff = sp_dir.listFiles();
            for( File of : ff )
                of.delete();
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