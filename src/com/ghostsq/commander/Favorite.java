package com.ghostsq.commander;

import java.security.KeyStore.PasswordProtection;
import java.util.regex.Pattern;

import android.net.Uri;

public class Favorite {
    // store/restore
    private static String  sep = "::";
    private static Pattern sep_re = Pattern.compile( sep );
    private static String  no_password = "(NP)";
    // fields
    private Uri     uri;
    private String  name;
    private String  username;
    private PasswordProtection password;
    
    public Favorite( String uri_str, String name_ ) {
        uri = Uri.parse( uri_str );
        name = name_ == null ? "" : name_;
    }
    public Favorite( String raw ) {
        fromString( raw );
    }
    public boolean fromString( String raw ) {
        if( raw.indexOf( sep ) >= 0 ) {
            String[] fields = sep_re.split( raw );
            if( fields.length > 0 ) name = fields[0];
            if( fields.length > 1 ) uri = Uri.parse( fields[1] );
            if( fields.length > 2 ) username = fields[2]; else username = null;
            if( fields.length > 3 ) decryptPassword( fields[3] ); else password = null;
        }
        else {            
            uri = Uri.parse( raw );
            name = "";
        }
        return true;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( name );
        buf.append( sep );
        buf.append( uri.toString() );
        if( username != null ) {
            buf.append( name );
            buf.append( sep );
        }
        if( password != null )
            buf.append( encryptPassword() );
        return buf.toString();    
    }
    
    public Uri getUri() {
        return uri;
    }
    public String getUriString() {
        return uri.toString();
    }
    
    private String encryptPassword() {
        if( password == null ) return no_password;
        return new String( password.getPassword() ); // TODO encrypt password!!!!!!!!!!!!!!!!       
    }

    private void decryptPassword( String stored ) {
        if( stored == null || no_password.equals( stored ) ) {
            password = null;
            return;
        }
        password = new PasswordProtection( stored.toCharArray() );   // TODO decrypt password!!!!!!!!!!!!!!!!
    }

    public final static String screenPwd( String uri_str ) {
        if( uri_str == null ) return null;
        return screenPwd( Uri.parse( uri_str ) );
    }
    public final static String screenPwd( Uri u ) {
        if( u == null ) return null;
        String ui = u.getUserInfo();
        if( ui == null || ui.length() == 0 ) return u.toString();
        int pw_pos = ui.indexOf( ':' );
        if( pw_pos < 0 ) return u.toString();
        ui = ui.substring( 0, pw_pos+1 ) + "***";
        String host = u.getHost();
        int port = u.getPort();
        String authority = ui + "@" + host + (port >= 0 ? port : ""); 
        return Uri.decode( u.buildUpon().encodedAuthority( authority ).build().toString() );
    }

}

