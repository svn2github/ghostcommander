package com.ghostsq.commander.utils;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class Replacer {
    public void replace( String pattern_str, String replace_to ) {
        Pattern pattern = null; 
        try {
            pattern = Pattern.compile( pattern_str );
        } catch( PatternSyntaxException e ) {}
        int n = getNumberOfOriginalStrings();
        for( int i = 0; i < n; i++ ) {
            String name = getOriginalString( i );
            String replaced = null;
            if( pattern != null ) {
                try {
                    replaced = pattern.matcher( name ).replaceAll( replace_to );
                } catch( Exception e ) {}
            }
            if( replaced == null )
                replaced = name.replace( pattern_str, replace_to );
            if( replaced != null ) {
                int num = i+1;
                replaced = replaced.replace( "%####",String.format( "%04d", num ) );
                replaced = replaced.replace( "%###", String.format( "%03d", num ) );
                replaced = replaced.replace( "%##",  String.format( "%02d", num ) );
                replaced = replaced.replace( "%#",   String.valueOf( num ) );
            }
            setReplacedString( i, replaced );
        }
    }

    protected int getNumberOfOriginalStrings() {
        return 0;
    }

    protected String getOriginalString( int i ) {
        return null;
    }
    
    protected void setReplacedString( int i, String replaced ) {
    }
}
