package com.ghostsq.commander.utils;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.interfaces.RSAPublicKey;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.security.auth.x500.X500Principal;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class KeyStorage {
    private static final String TAG = "KeyStore";
    public static final String PROVIDER_ANDROID_KEYSTORE = "AndroidKeyStore";
    
    public static Key provideAESKey( Context ctx, String alias ) {
        try {

            KeyStore ks = KeyStore.getInstance( PROVIDER_ANDROID_KEYSTORE );
            ks.load( null );
    
            // Load the key pair from the Android Key Store
            KeyStore.Entry entry = ks.getEntry( alias, null );
            if(entry == null) {
                Log.w( TAG, "!!!Key not found for alias=" + alias );
                KeyGenerator kg = KeyGenerator.getInstance( KeyProperties.KEY_ALGORITHM_AES, PROVIDER_ANDROID_KEYSTORE );
/* API 23+ only !!!
                int purpose = KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT;      
                KeyGenParameterSpec kgps = new KeyGenParameterSpec.Builder(alias, purpose)
                     .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                     .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                     .build();                
                kg.init( kgps );
*/
                SecretKey skey = kg.generateKey();                
                entry = ks.getEntry( alias, null );
            }
            if( !(entry instanceof KeyStore.SecretKeyEntry) ) {
                Log.w(TAG, "Not an instance of a SecretKeyEntry");
                return null;
            }
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry)entry;
            return secretKeyEntry.getSecretKey();            
        } catch( Exception e ) {
            Log.e( TAG, "Alias=" + alias, e );
        }
        return null;
    }
    
    public static Key provideRSAKey( Context ctx, String alias, boolean private_key ) {
        try {

            KeyStore ks = KeyStore.getInstance( PROVIDER_ANDROID_KEYSTORE );
            ks.load( null );
            
            if( !ks.containsAlias( alias ) ) {
                Log.w( TAG, "!!!Keys not found for alias " + alias );
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M )
                    KeyGenNew.generateRSAKey( alias );
                else
                    generateRSAKey( ctx, alias );
            }
            // Load the key pair from the Android Key Store
            KeyStore.Entry entry = ks.getEntry( alias, null );
            if(entry == null) {
                Log.e(TAG, "No entry for alias " + alias );
                return null;
            }
            if( !(entry instanceof KeyStore.PrivateKeyEntry) ) {
                Log.e(TAG, "Not an instance of a PrivateKeyEntry");
                return null;
            }
            KeyStore.PrivateKeyEntry private_key_entry = (KeyStore.PrivateKeyEntry)entry;
            if( private_key )
                return private_key_entry.getPrivateKey(); // Cipher does not like it!!!! :(
            else
                return private_key_entry.getCertificate().getPublicKey();
        } catch( Exception e ) {
            Log.e( TAG, "Alias=" + alias, e );
        }
        return null;
    }

    private static boolean generateRSAKey( Context ctx, String alias ) {
        try {
            Calendar start = new GregorianCalendar();
            Calendar end = new GregorianCalendar();
            end.add( Calendar.YEAR, 20 );
            String cert_sbj = "CN=" + alias + ", O=Ghost Squared, OU=Ghost Commander";
            KeyPairGeneratorSpec params = new KeyPairGeneratorSpec.Builder( ctx )
                            .setAlias( alias )
                            .setSubject( new X500Principal( cert_sbj ) )
                            .setSerialNumber( BigInteger.valueOf(5382) )
                            .setStartDate(start.getTime())
                            .setEndDate(end.getTime())
                            .build();

            // Initialize a KeyPair generator using the the intended algorithm (in this example, RSA
            // and the KeyStore.  This example uses the AndroidKeyStore.
            KeyPairGenerator kp_gen = KeyPairGenerator.getInstance( KeyProperties.KEY_ALGORITHM_RSA, PROVIDER_ANDROID_KEYSTORE );
            kp_gen.initialize( params );
            KeyPair kp = kp_gen.generateKeyPair();
            return kp != null;
        } catch( Exception e ) {
            Log.e( TAG, alias, e );
        }
        return false;
    }
}
