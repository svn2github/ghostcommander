package com.ghostsq.commander.toolbuttons;

//import com.ghostsq.toolbuttons.R;

import com.ghostsq.commander.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

public class ToolButton {
    private int       id;
    private int       def_caption_r_id;
    private String    codename;
    private String    caption;
    private boolean   visible;
    private int       color;
    private Drawable  icon;

    public final static int getId( String cn ) {
        if( cn == null ) return 0;
        if( cn.equals( "F1"      ) ) return        R.id.F1;
        if( cn.equals( "F2"      ) ) return        R.id.F2;
        if( cn.equals( "F3"      ) ) return        R.id.F3;
        if( cn.equals( "F4"      ) ) return        R.id.F4;
        if( cn.equals( "SF4"     ) ) return        R.id.SF4;
        if( cn.equals( "F5"      ) ) return        R.id.F5;
        if( cn.equals( "F6"      ) ) return        R.id.F6;
        if( cn.equals( "F7"      ) ) return        R.id.F7;
        if( cn.equals( "F8"      ) ) return        R.id.F8;
        if( cn.equals( "F9"      ) ) return        R.id.F9;
        if( cn.equals( "F10"     ) ) return        R.id.F10;
        if( cn.equals( "eq"      ) ) return        R.id.eq;
        if( cn.equals( "tgl"     ) ) return        R.id.tgl;
        if( cn.equals( "sz"      ) ) return        R.id.sz;
        if( cn.equals( "by_name" ) ) return        R.id.by_name;
        if( cn.equals( "by_ext"  ) ) return        R.id.by_ext;
        if( cn.equals( "by_size" ) ) return        R.id.by_size;
        if( cn.equals( "by_date" ) ) return        R.id.by_date;
        if( cn.equals( "sel_all" ) ) return        R.id.sel_all;
        if( cn.equals( "uns_all" ) ) return        R.id.uns_all;
        if( cn.equals( "enter"   ) ) return        R.id.enter;
        if( cn.equals( "addfav"  ) ) return        R.id.addfav;
        if( cn.equals( "remount" ) ) return        R.id.remount;
        return 0;
    }

    public final static String getCodeName( int id_ ) {
        switch( id_ ) {
        case  R.id.F1:           return  "F1";
        case  R.id.F2:           return  "F2";
        case  R.id.F3:           return  "F3";
        case  R.id.F4:           return  "F4";
        case  R.id.SF4:          return "SF4";
        case  R.id.F5:           return  "F5";
        case  R.id.F6:           return  "F6";
        case  R.id.F7:           return  "F7";
        case  R.id.F8:           return  "F8";
        case  R.id.F9:           return  "F9";
        case  R.id.F10:          return  "F10";
        case  R.id.eq:           return  "eq";
        case  R.id.tgl:          return  "tgl";
        case  R.id.sz:           return  "sz";
        case  R.id.by_name:      return  "by_name";
        case  R.id.by_ext:       return  "by_ext";
        case  R.id.by_size:      return  "by_size";
        case  R.id.by_date:      return  "by_date";
        case  R.id.sel_all:      return  "sel_all";
        case  R.id.uns_all:      return  "uns_all";
        case  R.id.enter:        return  "enter";
        case  R.id.addfav:       return  "addfav";
        case  R.id.remount:      return  "remount";
        }
        return null;
    }

    public final static int getCaptionRId( int id_ ) {
        switch( id_ ) {
        case  R.id.F1:           return  R.string.F1;
        case  R.id.F2:           return  R.string.F2;
        case  R.id.F3:           return  R.string.F3;
        case  R.id.F4:           return  R.string.F4;
        case  R.id.SF4:          return  R.string.SF4;
        case  R.id.F5:           return  R.string.F5;
        case  R.id.F6:           return  R.string.F6;
        case  R.id.F7:           return  R.string.F7;
        case  R.id.F8:           return  R.string.F8;
        case  R.id.F9:           return  R.string.F9;
        case  R.id.F10:          return  R.string.F10;
        case  R.id.eq:           return  R.string.eq;
        case  R.id.tgl:          return  R.string.tgl;
        case  R.id.sz:           return  R.string.sz;
        case  R.id.by_name:      return  R.string.sort_by_name;
        case  R.id.by_ext:       return  R.string.sort_by_ext;
        case  R.id.by_size:      return  R.string.sort_by_size;
        case  R.id.by_date:      return  R.string.sort_by_date;
        case  R.id.sel_all:      return  R.string.select_b;
        case  R.id.uns_all:      return  R.string.unselect_b;
        case  R.id.enter:        return  R.string.enter_b;
        case  R.id.addfav:       return  R.string.add_fav_b;
        case  R.id.remount:      return  R.string.remount_b;
        }
        return 0;
    }

    public final static boolean getVisible( int id_ ) {
        switch( id_ ) {
        case  R.id.F1:           return  true;
        case  R.id.F2:           return  true;
        case  R.id.F3:           return  false;
        case  R.id.F4:           return  true;
        case  R.id.SF4:          return  true;
        case  R.id.F5:           return  true;
        case  R.id.F6:           return  true;
        case  R.id.F7:           return  true;
        case  R.id.F8:           return  true;
        case  R.id.F9:           return  true;
        case  R.id.F10:          return  true;
        case  R.id.eq:           return  true;
        case  R.id.tgl:          return  true;
        case  R.id.sz:           return  true;
        case  R.id.by_name:      return  true;
        case  R.id.by_ext:       return  false;
        case  R.id.by_size:      return  true;
        case  R.id.by_date:      return  true;
        case  R.id.sel_all:      return  false;
        case  R.id.uns_all:      return  false;
        case  R.id.enter:        return  false;
        case  R.id.addfav:       return  false;
        case  R.id.remount:      return  false;
        }
        return false;
    }

    ToolButton( int id_ ) {
        id = id_;
        codename = getCodeName( id_ );
        def_caption_r_id = getCaptionRId( id_ );
        caption = null;
        visible = getVisible( id_ );
    }
    public final int getId() {
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
    
    public final String getCaption() {
        return caption;
    }
    final void setCaption( String caption_ ) {
        caption = caption_;
    }
    public final boolean isVisible() {
        return visible;
    }
    final void setVisible( boolean v ) {
        visible = v;
    }
    public final String getCodeName() {
        return codename;
    }
}
