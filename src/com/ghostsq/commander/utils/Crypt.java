package com.ghostsq.commander.utils;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import android.content.Context;
import android.util.Base64;

public class Crypt {
    private static final String  TAG = "Crypt";
    private static final String SEED = "5hO@%#O7&!H3#R";
    private static byte[] rawKey = null;

    public static byte[] getRawKey() throws Exception {
        if( Crypt.rawKey != null ) return Crypt.rawKey;
        Crypt.rawKey = getRawKey( SEED ); 
        return Crypt.rawKey;
    }
    private static byte[] getRawKey( String seed ) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance( "AES" );
        SecureRandom sr = SecureRandom.getInstance( "SHA1PRNG" );
        sr.setSeed( seed.getBytes() );
        kgen.init( 128, sr ); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        return raw;
    }
    
    // symmetric, simple key
    public static String encrypt( String cleartext, boolean base64out ) throws Exception {
        byte[] rawKey = getRawKey();
        byte[] result = encrypt( rawKey, cleartext.getBytes() );
        if( base64out )
            return Base64.encodeToString( result, Base64.DEFAULT );
        else
            return Utils.toHexString( result, null );
    }
    public static String decrypt( String encrypted, boolean base64in ) throws Exception {
        byte[] rawKey  = getRawKey();
        byte[] enc = base64in ? Base64.decode( encrypted, Base64.DEFAULT ) : Utils.hexStringToBytes( encrypted );
        byte[] result = decrypt( rawKey, enc );
        return new String( result );
    }
    
    // symmetric, custom key
    public static byte[] encrypt( byte[/*128*/] raw_key, byte[] clear ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw_key, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.ENCRYPT_MODE, skeySpec );
        byte[] encrypted = cipher.doFinal( clear );
        return encrypted;
    }
    public static byte[] decrypt( byte[/*128*/] raw_key, byte[] encrypted ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw_key, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.DECRYPT_MODE, skeySpec );
        byte[] decrypted = cipher.doFinal( encrypted );
        return decrypted;
    }

    // JellyBeans and up, symmetric, stored key
    public static String encryptAES( Context ctx, String cleartext, boolean base64out ) throws Exception {
        Cipher cipher = Cipher.getInstance( "AES" );
        Key k = KeyStorage.provideAESKey( ctx, TAG + "AES" );
        if( !(k instanceof SecretKey) )
            return null;
        cipher.init( Cipher.ENCRYPT_MODE, k );
        byte[] encrypted = cipher.doFinal( cleartext.getBytes() );
        if( base64out )
            return Base64.encodeToString( encrypted, Base64.DEFAULT );
        else
            return Utils.toHexString( encrypted, null );
    }
    
    public static String decryptAES( Context ctx, String encrypted, boolean base64in ) throws Exception {
        Cipher cipher = Cipher.getInstance( "AES" );
        Key k = KeyStorage.provideAESKey( ctx, TAG + "AES" );
        if( !(k instanceof SecretKey) )
            return null;
        cipher.init( Cipher.DECRYPT_MODE, k );
        byte[] encb = base64in ? Base64.decode( encrypted, Base64.DEFAULT ) : Utils.hexStringToBytes( encrypted );
        byte[] decb = cipher.doFinal( encb );
        return new String( decb );
    }

    // JellyBeans and up, asymmetric, stored key pair
    public static String encrypt( Context ctx, String cleartext, boolean base64out ) throws Exception {
        Cipher cipher = Cipher.getInstance( "RSA/ECB/PKCS1Padding" );
        Key k = KeyStorage.provideRSAKey( ctx, TAG, false );
        if( !(k instanceof PublicKey) )
            return null;
        cipher.init( Cipher.ENCRYPT_MODE, k );
        byte[] encrypted = cipher.doFinal( cleartext.getBytes() );
        if( base64out )
            return Base64.encodeToString( encrypted, Base64.DEFAULT );
        else
            return Utils.toHexString( encrypted, null );
    }
    
    public static String decrypt( Context ctx, String encrypted, boolean base64in ) throws Exception {
        // don't use the "AndroidOpenSSL" provider, it's not able to cast 
        // "AndroidKeyStoreRSAPrivateKey" to "RSAPrivateKey" 
        Cipher cipher = Cipher.getInstance( "RSA/ECB/PKCS1Padding" );
        Key k = KeyStorage.provideRSAKey( ctx, TAG, true );
        if( !(k instanceof PrivateKey) )
            return null;
        cipher.init( Cipher.DECRYPT_MODE, k );
        byte[] encb = base64in ? Base64.decode( encrypted, Base64.DEFAULT ) : Utils.hexStringToBytes( encrypted );
        byte[] decb = cipher.doFinal( encb );
        return new String( decb );
    }

}
