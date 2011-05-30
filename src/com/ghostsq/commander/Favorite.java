package com.ghostsq.commander;

import java.security.KeyStore.PasswordProtection;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import android.net.Uri;
import android.util.Log;

public class Favorite {
    private final static String TAG = "Favorite";
    // store/restore
    private static String  sep = "`US`";
    private static Pattern sep_re = Pattern.compile( sep );
    private static String  no_password = "(NP)";
    // fields
    private Uri     uri;
    private String  name;
    private String  username;
    private PasswordProtection password;
    
    public Favorite( String uri_str, String name_ ) {
        uri = Uri.parse( uri_str );
        name = name_;
    }
    public Favorite( String raw ) {
        fromString( raw );
    }
    public boolean fromString( String raw ) {
        if( raw == null ) return false;
        if( raw.indexOf( sep ) >= 0 ) {
            String[] flds = sep_re.split( raw );
            if( flds == null ) return false;
            try {
                name = "test";
                username = null;
                password = null;
                for( int i = 0; i < flds.length; i++ ) {
                    String s = flds[i];
                    if( i == 0 ) uri = Uri.parse( s );
                    if( i == 1 ) name = s;
                    if( i == 2 ) username = s;
                    if( i == 3 ) decryptPassword( s );
                    //Log.v( TAG, "Restored to: name=" + name + ", uri=" + uri + ", user=" + username + ", pass=" + ( password != null ? new String( password.getPassword() ) : "" ) );
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "can't restore " + raw, e );
            }
        }
        else {            
            uri = Uri.parse( raw );
            name = null;
        }
        return true;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( uri.toString() );
        if( name != null ) {
            buf.append( sep );
            buf.append( name );
        }
        if( username != null ) {
            buf.append( sep );
            buf.append( username );
        }
        if( password != null ) {
            buf.append( sep );
            buf.append( encryptPassword() );
        }
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

