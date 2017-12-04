package com.ghostsq.commander.favorites;

import java.util.regex.Pattern;

import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Crypt;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

public class Favorite {
    private final static String TAG = "Favorite";
    // store/restore
    private static String  sep = ",";
    private static Pattern sep_re = Pattern.compile( sep );
    
    // fields
    private String      id;
    private Uri         uri;
    private String      comment;
    private Credentials credentials;

    public Favorite( Uri u ) {
        this( u, null, null );
    }
    public Favorite( Uri u, Credentials c ) {
        this( u, c, null );
    }
    public Favorite( String uri_str, String comment_ ) {
        this( Uri.parse( uri_str ), null, comment_ );
    }
    public Favorite( Uri u, Credentials c, String comment_ ) {
        if( u == null ) u = (new Uri.Builder()).build();
        if( c == null )
            extractCredentialsFromUri( u );
        else {
            uri = Utils.updateUserInfo( u, null );
            credentials = c;
        }
        this.comment = comment_;
        this.setID( Integer.toHexString( u.hashCode() ) );
    }

    public final void extractCredentialsFromUri( Uri u ) {
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

    public final String getID() {
        return id;
    }
    
    private final void setID( String id ) {
        this.id = id;
    }
    
    public static Favorite fromString( String raw, Context ctx ) {
        if( raw == null ) return null;
        try {
            String[] flds = sep_re.split( raw );
            if( flds == null ) return null;
            Uri uri = null;
            String comment = null;
            Credentials credentials = null;
            for( int i = 0; i < flds.length; i++ ) {
                String s = flds[i];
                if( s == null || s.length() == 0 ) continue;
                
                String sv = unescape( s.substring( 4 ) );
                if( s.startsWith( "URI=" ) ) uri = Uri.parse( sv ); 
                else 
                if( s.startsWith( "CMT=" ) ) comment = sv;
                else
                if( s.startsWith( "CRD=" ) ) credentials = Credentials.fromOldEncriptedString( sv ); 
                else
                if( s.startsWith( "CRS=" ) ) credentials = Credentials.fromEncriptedString( sv, ctx );
            }
            return new Favorite( uri, credentials, comment );
        }
        catch( Exception e ) {
            Log.e( TAG, "can't restore " + raw, e );
        }
        return null;
    }
    
    public final void store( Context ctx, SharedPreferences.Editor ed ) {
        ed.putString( "URI_" + id, uri.toString() );
        if( comment != null )
            ed.putString( "CMT_" + id, comment );
        if( credentials != null ) {
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 )
                ed.putString( "CRS_" + id, credentials.toEncriptedString( ctx ) );
            else
                ed.putString( "CRD_" + id, credentials.toOldEncriptedString() );
        }
    }

    public final static void erasePrefs( String id, SharedPreferences.Editor ed ) {
        ed.remove( "URI_" + id );
        ed.remove( "CMT_" + id );
        ed.remove( "CRS_" + id );
        ed.remove( "CRD_" + id );
    }
    
    public final static Favorite restore( Context ctx, String id, SharedPreferences sp ) {
        String sv = sp.getString( "URI_" + id, null );
        if( sv == null ) return null;
        Uri uri = null;
        String comment = null;
        Credentials credentials = null;
        uri = Uri.parse( sv );
        comment = sp.getString( "CMT_" + id, null );
        String crs = null;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 )
            crs = sp.getString( "CRS_" + id, null );
        if( crs != null )
            credentials = Credentials.fromEncriptedString( crs, ctx );
        else {
            crs = sp.getString( "CRD_" + id, null );
            if( crs != null )
               credentials = Credentials.fromOldEncriptedString( crs ); 
        }
        Favorite f = new Favorite( uri, credentials, comment );
        f.setID( id );
        return f;
    }
    
    public final String getComment() {
        return comment;
    }
    public final void setComment( String s ) {
        comment = s;
    }
    public final void setUri( Uri u ) {
        uri = u;
    }
    public final Uri getUri() {
        return uri;
    }
    public final Uri getUriWithAuth() {
        if( credentials == null ) return uri; 
        return Utils.getUriWithAuth( uri, credentials.getUserName(), credentials.getPassword() );
    }
    public final String getUriString( boolean screen_pw ) {
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

    public final boolean equals( String test ) {
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
    
    public final String getUserName() {
        return credentials == null ? null : credentials.getUserName();
    }
    public final String getPassword() {
        return credentials == null ? "" : credentials.getPassword();
    }
    public final void setCredentials( String un, String pw ) {
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

