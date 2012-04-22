package com.ghostsq.commander.utils;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.auth.UsernamePasswordCredentials;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class Credentials extends UsernamePasswordCredentials implements Parcelable {
    private static String  TAG  = "GC.Credentials";
    private static String  seed = "5hO@%#O7&!H3#R";
    public  static String  pwScreen = "***";
    public  static String  KEY  = "CRD";

    public Credentials( String usernamePassword ) {
        super( usernamePassword );
    }
    public Credentials( String userName, String password ) {
        super( userName, password );
    }
    public Credentials( Credentials c ) {
        super( c.getUserName(), c.getPassword() );
    }

     public static final Parcelable.Creator<Credentials> CREATOR = new Parcelable.Creator<Credentials>() {
         public Credentials createFromParcel( Parcel in ) {
             String un = in.readString();
             String pw = "";
             try {
                 pw = new String( decrypt( getRawKey( seed.getBytes() ), in.createByteArray() ) );
             } catch( Exception e ) {
                 Log.e( TAG, "on password decryption", e );
             }
             return new Credentials( un, pw );
         }

         public Credentials[] newArray( int size ) {
             return new Credentials[size];
         }
     };    
    
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel( Parcel dest, int f ) {
        byte[] enc_pw = null;
        try {
            enc_pw = encrypt( getRawKey( seed.getBytes() ), getPassword().getBytes() );
        } catch( Exception e ) {
            Log.e( TAG, "on password encryption", e );
        }
        dest.writeString( getUserName() );
        dest.writeByteArray( enc_pw );
    }

    public static Credentials createFromEncriptedString( String s ) {
        try {
            return new Credentials( decrypt( seed, s ) );
        } catch( Exception e ) {
            Log.e( TAG, "on creating from an encrypted string", e );
        }
        return null;
    }
    public String exportToEncriptedString() {
        try {
            return encrypt( seed, getUserName() + ":" + getPassword() );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static String decrypt( String encrypted ) throws Exception {
        return decrypt( seed, encrypted );
    }
    
    public static String encrypt( String seed, String cleartext ) throws Exception {
        byte[] rawKey = getRawKey( seed.getBytes() );
        byte[] result = encrypt( rawKey, cleartext.getBytes() );
        return Utils.toHexString( result, null );
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
        return Utils.toHexString( txt.getBytes(), null );
    }

    public static String fromHex( String hex ) {
        return new String( Utils.hexStringToBytes( hex ) );
    }
    
    
}
