package com.ghostsq.commander;

import com.ghostsq.commander.toolbuttons.ToolButtonsProps;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class Prefs extends PreferenceActivity implements Preference.OnPreferenceClickListener,
                                                       RGBPickerDialog.ColorChangeListener  
{
    public static final String COLORS_PREFS = "colors"; 
    public static final String BGR_COLORS = "bgr_color_picker"; 
    public static final String FGR_COLORS = "fgr_color_picker"; 
    public static final String SEL_COLORS = "sel_color_picker"; 
    public static final String TTL_COLORS = "ttl_color_picker";
    public static final String TOOLBUTTONS = "toolbar_preference";
    private SharedPreferences color_pref = null;
    private String color_pref_key = null;
    
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        Utils.changeLanguage( this, getResources() );
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
        color_picker_pref = (Preference)findPreference( TTL_COLORS );
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
            color_pref_key = preference.getKey();
            if( TOOLBUTTONS.equals( color_pref_key ) ) {
                Intent intent = new Intent( Intent.ACTION_MAIN );
                intent.setClass( this, ToolButtonsProps.class );
                startActivity( intent );
            }
            else {
                color_pref = getSharedPreferences( COLORS_PREFS, Activity.MODE_PRIVATE );
                int color = color_pref.getInt( color_pref_key, getDefaultColor( color_pref_key ) );
                new RGBPickerDialog( this, this, color ).show();
            }
            return true;
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void colorChanged( int color ) {
        if( color_pref != null && color_pref_key != null ) {
            SharedPreferences.Editor editor = color_pref.edit();
            editor.putInt( color_pref_key, color );
            editor.commit();
            color_pref = null;
            color_pref_key = null;
        }
    }
    public static int getDefaultColor( String key ) {
        if( key.equals( BGR_COLORS ) ) return 0xFF191919;
        if( key.equals( FGR_COLORS ) ) return 0xFFF0F0F0;
        if( key.equals( SEL_COLORS ) ) return 0xFF4169E1;
        if( key.equals( TTL_COLORS ) ) return 0xFF555555;
        return 0;
    }
}