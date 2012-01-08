package com.ghostsq.commander;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;

public class FileTypes extends Activity implements OnClickListener,
                                   RGBPickerDialog.ColorChangeListener   
{
    private static final String TAG = "FileTypes";
    public  static final String TYPES1 = "types1";
    public  static final String TYPES2 = "types2";
    public  static final String TYPES3 = "types3";
    public  static final String TYPES4 = "types4";
    public  static final String TYPES5 = "types5";

    public  static final String FG1_COLORS = "fg1_color_picker"; 
    public  static final String FG2_COLORS = "fg2_color_picker"; 
    public  static final String FG3_COLORS = "fg3_color_picker"; 
    public  static final String FG4_COLORS = "fg4_color_picker"; 
    public  static final String FG5_COLORS = "fg5_color_picker"; 

    private TextView s0, s1, s2, s3, s4, s5;
    private EditText t1, t2, t3, t4, t5;
    private SharedPreferences color_pref = null;
    private String pref_key = null;
    
    @Override
    public void onCreate( Bundle bundle ) {
        try {
            super.onCreate( bundle );
            color_pref = getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
            setContentView( R.layout.types );
            View b0 = findViewById( R.id.b0 );
            b0.setOnClickListener( this );
            View b1 = findViewById( R.id.b1 );
            b1.setOnClickListener( this );
            View b2 = findViewById( R.id.b2 );
            b2.setOnClickListener( this );
            View b3 = findViewById( R.id.b3 );
            b3.setOnClickListener( this );
            View b4 = findViewById( R.id.b4 );
            b4.setOnClickListener( this );
            View b5 = findViewById( R.id.b5 );
            b5.setOnClickListener( this );
            s0 = (TextView)findViewById( R.id.s0 );
            s1 = (TextView)findViewById( R.id.s1 );
            s2 = (TextView)findViewById( R.id.s2 );
            s3 = (TextView)findViewById( R.id.s3 );
            s4 = (TextView)findViewById( R.id.s4 );
            s5 = (TextView)findViewById( R.id.s5 );
            s0.setTextColor( color_pref.getInt( Prefs.FGR_COLORS, Prefs.getDefaultColor( this, Prefs.FGR_COLORS, false ) ) );
            s1.setTextColor( color_pref.getInt( FG1_COLORS, Prefs.getDefaultColor( this, FG1_COLORS, false ) ) );
            s2.setTextColor( color_pref.getInt( FG2_COLORS, Prefs.getDefaultColor( this, FG2_COLORS, false ) ) );
            s3.setTextColor( color_pref.getInt( FG3_COLORS, Prefs.getDefaultColor( this, FG3_COLORS, false ) ) );
            s4.setTextColor( color_pref.getInt( FG4_COLORS, Prefs.getDefaultColor( this, FG4_COLORS, false ) ) );
            s5.setTextColor( color_pref.getInt( FG5_COLORS, Prefs.getDefaultColor( this, FG5_COLORS, false ) ) );
            int bg_color = color_pref.getInt( Prefs.BGR_COLORS, Prefs.getDefaultColor( this, Prefs.BGR_COLORS, false ) );
            s0.setBackgroundColor( bg_color );
            s1.setBackgroundColor( bg_color );
            s2.setBackgroundColor( bg_color );
            s3.setBackgroundColor( bg_color );
            s4.setBackgroundColor( bg_color );
            s5.setBackgroundColor( bg_color );
            t1 = (EditText)findViewById( R.id.types1 );
            t2 = (EditText)findViewById( R.id.types2 );
            t3 = (EditText)findViewById( R.id.types3 );
            t4 = (EditText)findViewById( R.id.types4 );
            t5 = (EditText)findViewById( R.id.types5 );
            t1.setText( color_pref.getString( TYPES1, getDefMasks( 1 ) ) );
            t2.setText( color_pref.getString( TYPES2, getDefMasks( 2 ) ) );
            t3.setText( color_pref.getString( TYPES3, getDefMasks( 3 ) ) );
            t4.setText( color_pref.getString( TYPES4, getDefMasks( 4 ) ) );
            t5.setText( color_pref.getString( TYPES5, getDefMasks( 5 ) ) );
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }
    
    @Override
    protected void onPause() {
        try {
            super.onPause();
            SharedPreferences.Editor sp_edit = color_pref.edit(); 
            sp_edit.putString( TYPES1, t1.getText().toString() );
            sp_edit.putString( TYPES2, t2.getText().toString() );
            sp_edit.putString( TYPES3, t3.getText().toString() );
            sp_edit.putString( TYPES4, t4.getText().toString() );
            sp_edit.putString( TYPES5, t5.getText().toString() );
            sp_edit.commit();
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    @Override
    public void onClick( View b ) {
        try {
            pref_key = null;
            switch( b.getId() ) {
            case R.id.b0:   pref_key = Prefs.FGR_COLORS; break; 
            case R.id.b1:   pref_key = FG1_COLORS; break;
            case R.id.b2:   pref_key = FG2_COLORS; break; 
            case R.id.b3:   pref_key = FG3_COLORS; break; 
            case R.id.b4:   pref_key = FG4_COLORS; break;
            case R.id.b5:   pref_key = FG5_COLORS; break;
            }
            if( pref_key != null ) {
                int color = color_pref.getInt( pref_key, Prefs.getDefaultColor( this, pref_key, false ) );
                new RGBPickerDialog( this, this, color, 0 ).show();
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }        
    }

    @Override
    public void colorChanged( int color ) {
        if( color_pref != null && pref_key != null ) {
            SharedPreferences.Editor editor = color_pref.edit();
            editor.putInt( pref_key, color );
            editor.commit();
            TextView stv = null;
            if( Prefs.FGR_COLORS.equals( pref_key ) ) stv = s0; else
            if( FG1_COLORS.equals( pref_key ) ) stv = s1; else
            if( FG2_COLORS.equals( pref_key ) ) stv = s2; else
            if( FG3_COLORS.equals( pref_key ) ) stv = s3; else
            if( FG4_COLORS.equals( pref_key ) ) stv = s4; else
            if( FG5_COLORS.equals( pref_key ) ) stv = s5;
            if( stv != null )
                stv.setTextColor( color );
            pref_key = null;
        }
    }

    public final static String getDefMasks( int i ) {
        switch( i ) {
        case 1: return "*.gif;*.jpg;*.png;*.bmp";
        case 2: return "*.avi;*.mov;*.mp4;*.mpeg";
        case 3: return "*.mp3;*.wav;*.ra*;*.mid*";
        case 4: return "*.htm*;*.xml;*.pdf;*.csv;*.doc*;*.xls*";
        case 5: return "*.apk;*.zip;*.jar;*.rar;*.bz2;*.gz;*.tgz";
        }
        return "";
    }

}
