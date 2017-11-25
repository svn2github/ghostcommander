package com.ghostsq.commander.favorites;

import java.util.regex.Pattern;

import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Crypt;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

public class Favorite {
    private final static String TAG = "Favorite";
    // store/restore
    private static String  sep = ",";
    private static Pattern sep_re = Pattern.compile( sep );
    
    // fields
    private Uri         uri;
    private String      comment;
    private Credentials credentials;

    public Favorite( Uri u ) {
        init( u );
    }
    public Favorite( Uri u, Credentials c ) {
        this( u, c, null );
    }
    public Favorite( Uri u, Credentials c, String comment_ ) {
        if( c == null )
            init( u );
        else {
            uri = Utils.updateUserInfo( u, null );
            credentials = c;
        }
        this.comment = comment_;
    }
    public Favorite( String uri_str, String comment_ ) {
        try {
            init( Uri.parse( uri_str ) );
            this.comment = comment_;
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }

    public void init( Uri u ) {
        try {
            uri = u;
            String user_info = uri.getUserInfo();
            if( user_info != null && user_info.length() > 0 ) {
                credentials = new Credentials( user_info );
                String pw = credentials.getPassword();
                if( Credentials.pwScreen.equals( pw ) ) 
                    credentials = new Credentials( credentials.getUserName(), credentials.getPassword() );
                uri = Utils.updateUserInfo( uri, null );
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }    
    
    public static Favorite fromString( String raw, Context ctx ) {
        if( raw == null ) return null;
        try {
            String[] flds = sep_re.split( raw );
            if( flds == null ) return null;
            Uri uri = null;
            String comment = null;
            Credentials credentials = null;
            String username = null, pass_enc = null;
            for( int i = 0; i < flds.length; i++ ) {
                String s = flds[i];
                if( s == null || s.length() == 0 ) continue;
                
                String sv = unescape( s.substring( 4 ) );
                if( s.startsWith( "URI=" ) ) uri = Uri.parse( sv ); 
                else 
                if( s.startsWith( "CMT=" ) ) comment = sv;
                else
                if( s.startsWith( "CRD=" ) )  credentials = Credentials.fromEncriptedString( sv ); 
                else
                if( s.startsWith( "CRS=" ) ) {
                    credentials = Credentials.fromEncriptedString( sv, Crypt.makeSeed( ctx ) );
                }
            }
            return new Favorite( uri, credentials, comment );
        }
        catch( Exception e ) {
            Log.e( TAG, "can't restore " + raw, e );
        }
        return null;
    }
    
    public static String toString( Favorite fv, Context ctx ) {
        try {
            if( fv.uri == null ) return "";
            StringBuffer buf = new StringBuffer( 128 );
            buf.append( "URI=" );
            buf.append( escape( fv.uri.toString() ) );
            if( fv.comment != null ) {
                buf.append( sep );
                buf.append( "CMT=" );
                buf.append( escape( fv.comment ) );
            }
            if( fv.credentials != null ) {
                buf.append( sep );
                buf.append( "CRS=" );
                buf.append( escape( fv.credentials.toEncriptedString( Crypt.makeSeed( ctx ) ) ) );
            }
            return buf.toString();
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    public String getComment() {
        return comment;
    }
    public void setComment( String s ) {
        comment = s;
    }
    public void setUri( Uri u ) {
        uri = u;
    }
    public Uri getUri() {
        return uri;
    }
    public Uri getUriWithAuth() {
        if( credentials == null ) return uri; 
        return Utils.getUriWithAuth( uri, credentials.getUserName(), credentials.getPassword() );
    }
    public String getUriString( boolean screen_pw ) {
        try {
            if( uri == null ) return null;
            if( credentials == null ) return uri.toString();
            if( screen_pw )
                return Utils.getUriWithAuth( uri, credentials.getUserName(), Credentials.pwScreen ).toString();
            else
                return getUriWithAuth().toString();
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    public Credentials getCredentials() {
        return credentials;
    }

    public boolean equals( String test ) {
        String item = getUriString( false );
        if( item != null ) {
            String strip_item = item.trim();
            if( strip_item.length() == 0 || strip_item.charAt( strip_item.length()-1 ) != '/' )
                strip_item += "/";
            if( strip_item.compareTo( test ) == 0 )
                return true;
        }
        return false;
    }
    
    public String getUserName() {
        return credentials == null ? null : credentials.getUserName();
    }
    public String getPassword() {
        return credentials == null ? "" : credentials.getPassword();
    }
    public void setCredentials( String un, String pw ) {
        if( un == null || un.length() == 0 ) {
            credentials = null;
            return;
        }
        credentials = new Credentials( un, pw );
    }
    
    private static String unescape( String s ) {
        return s.replace( "%2C", sep );
    }
    private static String escape( String s ) {
        return s.replace( sep, "%2C" );
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
        ui = Uri.encode( ui.substring( 0, pw_pos ) ) + ":" + Credentials.pwScreen;
//        return Uri.decode( Utils.updateUserInfo( u, ui ).toString() );
        return Utils.updateUserInfo( u, ui ).toString();
    }
    public final static boolean isPwdScreened( Uri u ) {
        String user_info = u.getUserInfo();
        if( user_info != null && user_info.length() > 0 ) {
            Credentials crd = new Credentials( user_info );
            if( Credentials.pwScreen.equals( crd.getPassword() ) ) return true;
        }
        return false;
    }
    
    public final Credentials borrowPassword( Uri stranger_uri ) {
        if( credentials == null ) return null;
        String stranger_user_info = stranger_uri.getUserInfo();
        String username = credentials.getUserName(); 
        String password = credentials.getPassword(); 
        if( username != null && password != null && stranger_user_info != null && stranger_user_info.length() > 0 ) {
            Credentials stranger_crd = new Credentials( stranger_user_info );
            String stranger_username = stranger_crd.getUserName();
            if( username.equalsIgnoreCase( stranger_username ) )
                return new Credentials( stranger_username, password );
        }
        return null;
    }
    
    public static Uri borrowPassword( Uri us, Uri fu ) {
        String schm = us.getScheme();
        if( schm != null && schm.equals( fu.getScheme() ) ) {
            String host = us.getHost();
            if( host != null && host.equalsIgnoreCase( fu.getHost() ) ) {
                String uis = us.getUserInfo();
                String fui = fu.getUserInfo();
                if( fui != null && fui.length() > 0 ) {
                    Credentials crds = new Credentials( uis );
                    Credentials fcrd = new Credentials( fui );
                    String un = crds.getUserName();
                    if( un != null && un.equals( fcrd.getUserName() ) )
                        return Utils.getUriWithAuth( us, un, fcrd.getPassword() );
                }
            }
        }
        return null;
    }    
}

