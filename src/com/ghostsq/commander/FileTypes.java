package com.ghostsq.commander;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class FileTypes extends Activity implements OnClickListener,
                                   RGBPickerDialog.ColorChangeListener   
{
    private static final String TAG = "FileTypes";
    public  static final String TYPES_pref = "types";
    public  static final String FGR_COL_pref = "fgr_color";
    private LayoutInflater    infl;
    private LinearLayout      ctr;
    private SharedPreferences color_pref = null;
    private int               bg_color;
    private String pref_key = null;

    public  static ArrayList<String>  tmasks;
    public  static ArrayList<Integer> colors;
    
    @Override
    public void onCreate( Bundle bundle ) {
        try {
            super.onCreate( bundle );
            color_pref = getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
            bg_color = color_pref.getInt( Prefs.BGR_COLORS, Prefs.getDefaultColor( this, Prefs.BGR_COLORS, false ) );
            setContentView( R.layout.types );
            View b0 = findViewById( R.id.b0 );
            b0.setOnClickListener( this );
            TextView s0 = (TextView)findViewById( R.id.s0 );
            s0.setTextColor( color_pref.getInt( Prefs.FGR_COLORS, getDefColor( this, 0 ) ) );
            s0.setBackgroundColor( bg_color );
            ctr = (LinearLayout)findViewById( R.id.types_container );
            infl = getLayoutInflater();

            int n = readStored( this, color_pref );
            
            for( int i = 1; i <= n; i++ ) {
                int    color = colors.get( i-1 );
                String masks = tmasks.get( i-1 );
                if( masks == null ) break;
                addView( i, color, masks );
            }
            View antb = findViewById( R.id.add_new_type );
            antb.setOnClickListener( this );
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }
    
    @Override
    protected void onPause() {
        try {
            super.onPause();
            SharedPreferences.Editor sp_edit = color_pref.edit();
            int n = ctr.getChildCount();
            for( int i = 1; i <= n; i++ ) {
                RelativeLayout tl = (RelativeLayout)ctr.getChildAt( i-1 );
                EditText t = (EditText)tl.findViewById( R.id.types );
                if( t != null ) {
                    String masks = t.getText().toString();
                    sp_edit.putString( TYPES_pref + i, masks );
                    tmasks.set( i-1, masks );
                }
            }
            sp_edit.commit();
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    @Override
    public void onClick( View b ) {
        try {
            pref_key = null;
            int i = 0;
            int bid = b.getId();
            if( bid == R.id.add_new_type ) {
                String mask = "";
                tmasks.add( mask );
                i = tmasks.size(); 
                int   color = getDefColor( this, i );
                colors.add( color );
                addView( i, color, mask );
                return;
            }
            if( bid == R.id.b0 )
                pref_key = Prefs.FGR_COLORS;
            else { 
                i = (Integer)b.getTag();
                if( i > 0 && i < 999 )
                    pref_key = FGR_COL_pref + i;
            }
            if( pref_key != null ) {
                int color = color_pref.getInt( pref_key, getDefColor( this, i ) );
                new RGBPickerDialog( this, this, color, 0 ).show();
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }        
    }

    @Override
    public void colorChanged( int color ) {
        try {
            if( color_pref != null && pref_key != null ) {
                SharedPreferences.Editor editor = color_pref.edit();
                editor.putInt( pref_key, color );
                editor.commit();
                TextView stv = null;
                if( Prefs.FGR_COLORS.equals( pref_key ) ) 
                     stv = (TextView)findViewById( R.id.s0 ); 
                else if( pref_key.startsWith( FGR_COL_pref ) ) {
                    Integer idx = Integer.parseInt( pref_key.substring( FGR_COL_pref.length() ) );
                    if( idx > 0 )
                        stv = (TextView)ctr.findViewWithTag( idx );
                }
                
                if( stv != null )
                    stv.setTextColor( color );
                pref_key = null;
            }
        } catch( Exception e ) {
            Log.e( TAG, pref_key, e );
        }
    }

    private final boolean addView( int i, int color, String masks ) {
        try {
            RelativeLayout tl = (RelativeLayout)infl.inflate( R.layout.type, ctr, false );
            View b = tl.findViewById( R.id.b );
            Integer idx = new Integer( i );
            b.setTag( idx );
            b.setOnClickListener( this );
            TextView s = (TextView)tl.findViewById( R.id.s );
            s.setTag( idx );
            s.setTextColor( color );
            s.setBackgroundColor( bg_color );
            EditText t = (EditText)tl.findViewById( R.id.types );
            t.setText( masks );
            ctr.addView( tl );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, masks, e );
        }
        return false;
    }
    
    
    public final static String getDefMasks( int i ) {
        switch( i ) {
        case 1: return "*.gif;*.jpg;*.png;*.bmp";
        case 2: return "*.avi;*.mov;*.mp4;*.mpeg";
        case 3: return "*.mp3;*.wav;*.ra;*.mid*";
        case 4: return "*.htm*;*.xml;*.pdf;*.csv;*.doc*;*.xls*";
        case 5: return "*.apk;*.zip;*.jar;*.rar;*.bz2;*.gz;*.tgz";
        }
        return null;
    }
    public static int getDefColor( Context ctx, int i ) {
        Resources r = ctx.getResources();
        switch( i ) {
        case 1: return r.getColor( R.color.fg1_def );
        case 2: return r.getColor( R.color.fg2_def );
        case 3: return r.getColor( R.color.fg3_def );
        case 4: return r.getColor( R.color.fg4_def );
        case 5: return r.getColor( R.color.fg5_def );
        default:return r.getColor( R.color.fgr_def );
        }
    }
    
    public static int readStored( Context ctx, SharedPreferences color_pref ) {
        try {
            if( tmasks == null || colors == null ) {
                tmasks = new ArrayList<String>( 5 );
                colors = new ArrayList<Integer>( 5 );
                for( int i = 1; i < 999; i++ ) {
                    int    color = color_pref.getInt(  FGR_COL_pref + i, getDefColor( ctx, i ) );
                    String masks  = color_pref.getString( TYPES_pref + i, getDefMasks( i ) );
                    if( masks == null ) break;
                    tmasks.add( new String( masks ) );
                    colors.add( new Integer( color ) );
                }
            }
            return tmasks.size();
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return 0;
    }    
}
