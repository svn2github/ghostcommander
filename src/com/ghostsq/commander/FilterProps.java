package com.ghostsq.commander;

import java.util.Date;

import android.content.Context;
import android.text.format.DateFormat;

import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.utils.Utils;

public class FilterProps {
    public String  file_mask;
    public boolean dirs, files, include_matched; 
    public long    larger_than, smaller_than = Long.MAX_VALUE; 
    public Date    mod_after, mod_before;
    
    protected String[] cards; 
    
    private boolean isItMatched( Item item ) {
        String item_name = item.name;
        if( item.dir ) {
            if( !dirs )
                return false;
            item_name = item.name.replace( "/", "" );
        } else {
            if( !files )
                return false;
            if( item.size < larger_than || item.size > smaller_than ) 
                return false;
        }
        if( Utils.str( file_mask ) ) {
            if( cards == null )
                cards = Utils.prepareWildcard( file_mask );
            if( !Utils.match( item_name, cards ) )
                return false;
        }
        long modified = item.date.getTime();
        if( mod_after  != null && modified <  mod_after.getTime() ) 
            return false;  
        if( mod_before != null && modified > mod_before.getTime() ) 
            return false;  
        return true;
    }
    
    public boolean isMatched( Item item ) {
        return !(isItMatched( item ) ^ include_matched);
    }
    
    public String getString( Context ctx ) {
        StringBuilder sb = new StringBuilder();
        if( !( dirs && files ) ) {
            if( dirs )  sb.append( ctx.getString( R.string.for_dirs ) );  else
            if( files ) sb.append( ctx.getString( R.string.for_files) ); else
            sb.append( "Nothing!!!" );
        }
        if( Utils.str( file_mask ) && !"*".equals( file_mask ) && !"*.*".equals( file_mask ) ) {
            if( sb.length() > 0 ) sb.append( ", " );
            sb.append( file_mask );
        }
        java.text.DateFormat df = null;
        if( mod_after != null ) {
            df = DateFormat.getDateFormat( ctx );
            if( sb.length() > 0 ) sb.append( ", " );
            sb.append( ctx.getString( R.string.mod_after ) );
            sb.append( " " );
            sb.append( df.format( mod_after ) );
        }
        if( mod_before != null ) {
            if( df == null ) df = DateFormat.getDateFormat( ctx );
            if( sb.length() > 0 ) sb.append( ", " );
            sb.append( ctx.getString( R.string.mod_before ) );
            sb.append( " " );
            sb.append( df.format( mod_before ) );
        }
        if( larger_than > 0 ) {
            if( sb.length() > 0 ) sb.append( ", " );
            String pr = ctx.getString( R.string.bigger_than );
            sb.append( pr.replace( '\n', ' ' ).replace( ':', ' ' ) );
            sb.append( Utils.getHumanSize( larger_than ) );
        }
        if( smaller_than < Long.MAX_VALUE ) {
            if( sb.length() > 0 ) sb.append( ", " );
            String pr = ctx.getString( R.string.smaller_than );
            sb.append( pr.replace( '\n', ' ' ).replace( ':', ' ' ) );
            sb.append( Utils.getHumanSize( smaller_than ) );
        }
        if( sb.length() == 0 )
            return null;
        sb.append( " " );
        return ctx.getString( include_matched ? R.string.show_matched : R.string.hide_matched) + 
                ": " + sb.toString();
    }
}
