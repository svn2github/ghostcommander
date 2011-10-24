package com.ghostsq.commander.toolbuttons;

//import com.ghostsq.toolbuttons.R;

import com.ghostsq.commander.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

class ToolButton {
    public final static int  
        F1  = 101,
        F2  = 102,
        F3  = 103,
        F4  = 104, 
        F5  = 105, 
        F6  = 106,
        F7  = 107,
        F8  = 108,
        F9  = 109,
        F10 = 110;
    
    private int       id;
    private int       def_caption_r_id;
    private String    codename;
    private String    caption;
    private boolean   visible;
    private int       color;
    private Drawable  icon;

    public final static ToolButton createInstance( int id_ ) {
        switch( id_ ) {
        case F1:  return new ToolButton( id_, "F1", R.string.F1 );
        case F2:  return new ToolButton( id_, "F2", R.string.F2 );
        case F3:  return new ToolButton( id_, "F3", R.string.F3 );
        case F4:  return new ToolButton( id_, "F4", R.string.F4 );
        case F5:  return new ToolButton( id_, "F5", R.string.F5 );
        case F6:  return new ToolButton( id_, "F6", R.string.F6 );
        case F7:  return new ToolButton( id_, "F7", R.string.F7 );
        case F8:  return new ToolButton( id_, "F8", R.string.F8 );
        case F9:  return new ToolButton( id_, "F9", R.string.F9 );
        case F10: return new ToolButton( id_, "F10",R.string.F10);
        }
        return null;
    }
    
    
    ToolButton( int id_, String codename_, int def_caption_r_id_ ) {
        id = id_;
        codename = codename_;
        def_caption_r_id = def_caption_r_id_;
        caption = null;
        visible = true;
    }
    final int getId() {
        return id;
    }
    final String getName( Context c ) {
        return c.getString( def_caption_r_id );
    }
    private final String getVisiblePropertyName() {
        return "show_" + codename; 
    }
    private final String getCaptionPropertyName() {
        return "caption_" + codename; 
    }
    public final void restore( SharedPreferences shared_pref, Context context ) {
        visible = shared_pref.getBoolean( getVisiblePropertyName(), visible );
        caption = shared_pref.getString( getCaptionPropertyName(), getName( context ) );
    }
    public final void store( SharedPreferences.Editor editor ) {
        editor.putBoolean( getVisiblePropertyName(), visible );
        editor.putString( getCaptionPropertyName(), caption );
    }
    
    final String getCaption() {
        return caption;
    }
    final void setCaption( String caption_ ) {
        caption = caption_;
    }
    final boolean isVisible() {
        return visible;
    }
    final void setVisible( boolean v ) {
        visible = v;
    }
}
