package com.ghostsq.commander.toolbuttons;

import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

class ToolButtonsArray extends ArrayList<ToolButton> 
{
    private static final long serialVersionUID = 1L;
    public final String TAG = getClass().getName();
    
    public final void restore( SharedPreferences shared_pref, Context context ) {
        String bics = "" +
            ToolButton.F1 + "," +
            ToolButton.F2 + "," +
            ToolButton.F3 + "," +
            ToolButton.F4 + "," +
            ToolButton.F5 + "," +
            ToolButton.F6 + "," +
            ToolButton.F7 + "," +
            ToolButton.F8 + "," +
            ToolButton.F9 + "," +
            ToolButton.F10;
        bics = shared_pref.getString( "buttons", bics );
        String[] bisa = bics.split( "," );
            for( String bis : bisa ) {
                try {
                    if( bis.length() == 0 ) continue;
                    int bi = Integer.parseInt( bis );
                    ToolButton tb = ToolButton.createInstance( bi );
                    if( tb != null ) {
Log.v( TAG, "creating button " + bi );                        
                    tb.restore( shared_pref, context );
                    add( tb );
                }
            } catch( NumberFormatException e ) {
                e.printStackTrace();
            }
        }
    }
    public final void store( SharedPreferences.Editor editor ) {
        StringBuffer bicsb = new StringBuffer();
        for( int i = 0; i < size(); i++ ) {
            ToolButton tb = get( i );
            if( i > 0 ) bicsb.append( "," );
            bicsb.append( tb.getId() );
            tb.store( editor );
        }
        editor.putString( "buttons", bicsb.toString() );
    }
}   
