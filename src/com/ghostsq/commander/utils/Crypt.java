package com.ghostsq.commander.utils;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.ghostsq.commander.R;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

public class Crypt {
    private static String SEED = "5hO@%#O7&!H3#R";
    private static byte[] rawKey = null;

    public static String makeSeed( Context ctx ) {
        return ctx.getString( R.string.crdseed ) + Settings.Secure.getString( ctx.getContentResolver(), Settings.Secure.ANDROID_ID );
    }
    
    public static byte[] getRawKey() throws Exception {
        if( Crypt.rawKey != null ) return Crypt.rawKey;
        Crypt.rawKey = getRawKey( null ); 
        return Crypt.rawKey;
    }
    private static byte[] getRawKey( String seed ) throws Exception {
        if( seed == null ) seed = SEED; 
        KeyGenerator kgen = KeyGenerator.getInstance( "AES" );
        SecureRandom sr = SecureRandom.getInstance( "SHA1PRNG", "Crypto" );
        sr.setSeed( seed.getBytes() );
        kgen.init( 128, sr ); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        return raw;
    }
    
    public static String encrypt( String cleartext, boolean base64out ) throws Exception {
        return encrypt( Crypt.SEED, cleartext, base64out );
    }
    
    public static String encrypt( String seed, String cleartext, boolean base64out ) throws Exception {
        if( seed == null ) seed = SEED;
        byte[] rawKey = getRawKey( seed );
        byte[] result = encrypt( rawKey, cleartext.getBytes() );
        if( base64out )
            return Base64.encodeToString( result, Base64.DEFAULT );
        else
            return Utils.toHexString( result, null );
    }

    public static String decrypt( String encrypted, boolean base64in ) throws Exception {
        return decrypt( Crypt.SEED, encrypted, base64in );
    }

    public static String decrypt( String seed, String encrypted, boolean base64in ) throws Exception {
        if( seed == null ) seed = SEED;
        byte[] rawKey  = getRawKey( seed );
        byte[] enc = base64in ? Base64.decode( encrypted, Base64.DEFAULT ) : Utils.hexStringToBytes( encrypted );
        byte[] result = decrypt( rawKey, enc );
        return new String( result );
    }

    public static byte[] encrypt( byte[] raw, byte[] clear ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.ENCRYPT_MODE, skeySpec );
        byte[] encrypted = cipher.doFinal( clear );
        return encrypted;
    }

    public static byte[] decrypt( byte[] raw, byte[] encrypted ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.DECRYPT_MODE, skeySpec );
        byte[] decrypted = cipher.doFinal( encrypted );
        return decrypted;
    }
}
