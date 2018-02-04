package com.ghostsq.commander.utils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import android.annotation.TargetApi;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

@TargetApi(23)
public class KeyGenNew {
    private static final String TAG = "KeyGenNew";
    public static boolean generateRSAKey( String alias ) {
        try {
             KeyPairGenerator kp_gen = KeyPairGenerator.getInstance(
                     KeyProperties.KEY_ALGORITHM_RSA, KeyStorage.PROVIDER_ANDROID_KEYSTORE );
             KeyGenParameterSpec params = new KeyGenParameterSpec.Builder( alias, KeyProperties.PURPOSE_DECRYPT )
                             .setDigests( KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512 )
                             .setEncryptionPaddings( KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1 )
                             .build(); 
             kp_gen.initialize( params );
             KeyPair kp = kp_gen.generateKeyPair();
             return kp != null;
        } catch( Exception e ) {
            Log.e( TAG, alias, e );
        }
        return false;
    }
}
