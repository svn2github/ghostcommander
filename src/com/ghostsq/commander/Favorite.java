package com.ghostsq.commander;

import java.security.SecureRandom;
import java.security.KeyStore.PasswordProtection;
import java.util.regex.Pattern;
import java.net.URLEncoder;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.auth.UsernamePasswordCredentials;

import android.net.Uri;
import android.util.Log;

public class Favorite {
    private final static String TAG = "Favorite";
    // store/restore
    private static String  sep = ",";
    private static Pattern sep_re = Pattern.compile( sep );
    private static String  seed = "5hO@%#O7&!H3#R";
    
    // fields
    private Uri     uri;
    private String  comment;
    private String  username;
    private PasswordProtection password;
    
    public Favorite( String uri_str, String comment_ ) {
        try {
            uri = Uri.parse( uri_str );
            String user_info = uri.getUserInfo();
            if( user_info != null && user_info.length() > 0 ) {
                UsernamePasswordCredentials crd = new UsernamePasswordCredentials( user_info );
                setCredentials( crd.getUserName(), crd.getPassword() );
                uri = updateCredentials( uri, null );
            }
            comment = comment_;
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }
    public Favorite( String raw ) {
        fromString( raw );
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
        if( username == null ) return uri;
        String auth = URLEncoder.encode( username );
        if( password != null )
            auth += ":" + URLEncoder.encode( new String( password.getPassword() ) );
        auth += "@" + uri.getEncodedAuthority();
        return uri.buildUpon().encodedAuthority( auth ).build();
    }
    
    public String getUriString( boolean screen_pw ) {
        if( uri == null ) return null;
        if( username == null ) return uri.toString();
        String ui = Uri.encode( username );
        if( screen_pw )
            ui += ":***";
        else
            if( password != null )
                ui += ":" + Uri.encode( new String( password.getPassword() ) );
        return updateCredentials( uri, ui ).toString();
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
        ui = ui.substring( 0, pw_pos+1 ) + "***";
        return Uri.decode( updateCredentials( u, ui ).toString() );
    }
    private final static Uri updateCredentials( Uri u, String ui ) {
        String host = u.getHost();
        int port = u.getPort();
        String authority = ui != null ? ui + "@" : ""; 
        authority += host + ( port >= 0 ? ":" + port : "" ); 
        return u.buildUpon().encodedAuthority( authority ).build(); 
    }

    // ---------------------------
    
    public static String encrypt( String seed, String cleartext ) throws Exception {
        byte[] rawKey = getRawKey( seed.getBytes() );
        byte[] result = encrypt( rawKey, cleartext.getBytes() );
        return toHex( result );
    }

    public static String decrypt( String seed, String encrypted ) throws Exception {
        byte[] rawKey = getRawKey( seed.getBytes() );
        byte[] enc = toByte( encrypted );
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
        return toHex( txt.getBytes() );
    }

    public static String fromHex( String hex ) {
        return new String( toByte( hex ) );
    }

    public static byte[] toByte( String hexString ) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for( int i = 0; i < len; i++ )
            result[i] = Integer.valueOf( hexString.substring( 2 * i, 2 * i + 2 ), 16 ).byteValue();
        return result;
    }

    public static String toHex( byte[] buf ) {
        if( buf == null )
            return "";
        StringBuffer result = new StringBuffer( 2 * buf.length );
        for( int i = 0; i < buf.length; i++ ) {
            appendHex( result, buf[i] );
        }
        return result.toString();
    }

    private final static String HEX = "0123456789ABCDEF";

    private static void appendHex( StringBuffer sb, byte b ) {
        sb.append( HEX.charAt( ( b >> 4 ) & 0x0f ) ).append( HEX.charAt( b & 0x0f ) );
    }
    
}

