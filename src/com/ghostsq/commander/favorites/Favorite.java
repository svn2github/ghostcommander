package com.ghostsq.commander.favorites;

import java.security.SecureRandom;
import java.security.KeyStore.PasswordProtection;
import java.util.regex.Pattern;
import java.net.URLEncoder;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.auth.UsernamePasswordCredentials;

import com.ghostsq.commander.utils.Utils;

import android.net.Uri;
import android.util.Log;

public class Favorite {
    private final static String TAG = "Favorite";
    // store/restore
    private static String  sep = ",";
    private static Pattern sep_re = Pattern.compile( sep );
    private static String  seed = "5hO@%#O7&!H3#R";
    private static String  pwScreen = "***";
    
    // fields
    private Uri     uri;
    private String  comment;
    private String  username;
    private PasswordProtection password;

    public Favorite( Uri u ) {
        init( u );
    }
    public Favorite( String uri_str, String comment_ ) {
        try {
            init( Uri.parse( uri_str ) );
            comment = comment_;
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }
    public Favorite( String raw ) {
        fromString( raw );
    }

    public void init( Uri u ) {
        try {
            uri = u;
            String user_info = uri.getUserInfo();
            if( user_info != null && user_info.length() > 0 ) {
                UsernamePasswordCredentials crd = new UsernamePasswordCredentials( user_info );
                String pw = crd.getPassword();
                setCredentials( crd.getUserName(), pwScreen.equals( pw ) ? null : pw );
                uri = updateUserInfo( uri, null );
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }    
    
    public boolean fromString( String raw ) {
        if( raw == null ) return false;
        try {
            String[] flds = sep_re.split( raw );
            if( flds == null ) return false;
            comment = null;
            username = null;
            password = null;
            for( int i = 0; i < flds.length; i++ ) {
                String s = flds[i];
                if( s == null || s.length() == 0 ) continue;
                if( s.startsWith( "URI="  ) ) uri = Uri.parse( unescape( s.substring( 4 ) ) ); else 
                if( s.startsWith( "CMT="  ) ) comment = unescape( s.substring( 4 ) ); else
                if( s.startsWith( "USER=" ) ) username = unescape( s.substring( 5 ) ); else
                if( s.startsWith( "PASS=" ) ) decryptPassword( unescape( s.substring( 5 ) ) );
                //Log.v( TAG, "Restored to: cmt=" + comment + ", uri=" + uri + ", user=" + username + ", pass=" + ( password != null ? new String( password.getPassword() ) : "" ) );
            }
        }
        catch( Exception e ) {
            Log.e( TAG, "can't restore " + raw, e );
        }
        return true;
    }
    public String toString() {
        try {
            StringBuffer buf = new StringBuffer( 128 );
            buf.append( "URI=" );
            buf.append( escape( uri.toString() ) );
            if( comment != null ) {
                buf.append( sep );
                buf.append( "CMT=" );
                buf.append( escape( comment ) );
            }
            if( username != null ) {
                buf.append( sep );
                buf.append( "USER=" );
                buf.append( escape( username ) );
            }
            if( password != null ) {
                buf.append( sep );
                buf.append( "PASS=" );
                buf.append( escape( encryptPassword() ) );
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
        return getUriWithAuth( uri, username, password != null ? new String( password.getPassword() ) : null );
    }
    public String getUriString( boolean screen_pw ) {
        try {
            if( uri == null ) return null;
            if( username == null ) return uri.toString();
            if( screen_pw )
                return getUriWithAuth( uri, username, pwScreen ).toString();
            else
                return getUriWithAuth().toString();
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
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
        return username;
    }
    public String getPassword() { 
        return password != null ? new String( password.getPassword() ) : "";
    }
    public void setCredentials( String un, String pw ) {
        if( un == null || un.length() == 0 ) {
            username = null;
            password = null;
            return;
        }
        username = un;
        password = pw != null ? new PasswordProtection( pw.toCharArray() ) : null;
    }
    
    private String encryptPassword() {
        if( password != null )
            try {
                return encrypt( seed, new String( password.getPassword() ) );
            } catch( Exception e ) {
                e.printStackTrace();
            }
        return "";
    }
    private void decryptPassword( String stored ) {
        password = null;
        if( stored != null && stored.length() > 0 )
        try {
            String pw = decrypt( seed, stored );
            password = new PasswordProtection( pw.toCharArray() );
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
    private String unescape( String s ) {
        return s.replace( "%2C", sep );
    }
    private String escape( String s ) {
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
        ui = ui.substring( 0, pw_pos+1 ) + pwScreen;
        return Uri.decode( updateUserInfo( u, ui ).toString() );
    }
    public final static boolean isPwdScreened( Uri u ) {
        String user_info = u.getUserInfo();
        if( user_info != null && user_info.length() > 0 ) {
            UsernamePasswordCredentials crd = new UsernamePasswordCredentials( user_info );
            if( pwScreen.equals( crd.getPassword() ) ) return true;
        }
        return false;
    }
    
    public final Uri borrowPassword( Uri stranger_uri ) {
        String stranger_user_info = stranger_uri.getUserInfo();
        if( password != null && stranger_user_info != null && stranger_user_info.length() > 0 ) {
            UsernamePasswordCredentials stranger_crd = new UsernamePasswordCredentials( stranger_user_info );
            if( username != null && username.equalsIgnoreCase( stranger_crd.getUserName() ) )
                return getUriWithAuth( stranger_uri, stranger_crd.getUserName(), new String( password.getPassword() ) );
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
                    UsernamePasswordCredentials crds = new UsernamePasswordCredentials( uis );
                    UsernamePasswordCredentials fcrd = new UsernamePasswordCredentials( fui );
                    String un = crds.getUserName();
                    if( un != null && un.equals( fcrd.getUserName() ) )
                        return getUriWithAuth( us, un, fcrd.getPassword() );
                }
                
            }
        }
        return null;
    }    
    
    public static Uri getUriWithAuth( Uri u, String un, String pw ) {
        if( un == null ) return u;
        String ui = URLEncoder.encode( un );
        if( pw != null )
            ui += ":" + URLEncoder.encode( pw );
        return updateUserInfo( u, ui );
    }
    
    public final static Uri updateUserInfo( Uri u, String encoded_ui ) {
        String host = u.getHost();
        if( host == null ) return u;
        int port = u.getPort();
        String authority = encoded_ui != null ? encoded_ui + "@" : "";
        authority += host + ( port >= 0 ? ":" + port : "" );
        return u.buildUpon().encodedAuthority( authority ).build(); 
    }

    public final static Uri addTrailngSlash( Uri u ) {
        String path = u.getEncodedPath();
        if( path == null )
            path = "/";
        else {
            String alt_path = Utils.mbAddSl( path );
            if( path.equals( alt_path ) ) return u;
        }
        return u.buildUpon().encodedPath( path ).build(); 
    }

    // ---------------------------
    
    public static String encrypt( String seed, String cleartext ) throws Exception {
        byte[] rawKey = getRawKey( seed.getBytes() );
        byte[] result = encrypt( rawKey, cleartext.getBytes() );
        return Utils.toHexString( result );
    }

    public static String decrypt( String seed, String encrypted ) throws Exception {
        byte[] rawKey = getRawKey( seed.getBytes() );
        byte[] enc = Utils.hexStringToBytes( encrypted );
        byte[] result = decrypt( rawKey, enc );
        return new String( result );
    }

    private static byte[] getRawKey( byte[] seed ) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance( "AES" );
        SecureRandom sr = SecureRandom.getInstance( "SHA1PRNG" );
        sr.setSeed( seed );
        kgen.init( 128, sr ); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        return raw;
    }

    private static byte[] encrypt( byte[] raw, byte[] clear ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.ENCRYPT_MODE, skeySpec );
        byte[] encrypted = cipher.doFinal( clear );
        return encrypted;
    }

    private static byte[] decrypt( byte[] raw, byte[] encrypted ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.DECRYPT_MODE, skeySpec );
        byte[] decrypted = cipher.doFinal( encrypted );
        return decrypted;
    }

    public static String toHex( String txt ) {
        return Utils.toHexString( txt.getBytes() );
    }

    public static String fromHex( String hex ) {
        return new String( Utils.hexStringToBytes( hex ) );
    }
}

