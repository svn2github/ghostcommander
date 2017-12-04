package com.ghostsq.commander.utils;


import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

public class Credentials implements Parcelable {
    private static String  TAG  = "GC.Credentials";
    public  static String  pwScreen = "***";
    public  static String  KEY  = "CRD";
    private String username, password;

    public Credentials( String usernamePassword ) { // ':' - separated
        int cp = usernamePassword.indexOf( ':' );
        if( cp < 0 ) {
            this.username = usernamePassword;
            return;
        }
        this.username = usernamePassword.substring( 0, cp );
        this.password = usernamePassword.substring( cp + 1 );
    }
    public Credentials( String userName, String password ) {
        this.username = userName;
        this.password = password;
    }
    public Credentials( Credentials c ) {
        this( c.getUserName(), c.getPassword() );
    }
    public String getUserName() {
        return this.username;
    }
    public String getPassword() {
        return this.password;
    }

     public static final Parcelable.Creator<Credentials> CREATOR = new Parcelable.Creator<Credentials>() {
         public Credentials createFromParcel( Parcel in ) {
             String un = in.readString();
             String pw = "";
             try {
                 pw = new String( Crypt.decrypt( Crypt.getRawKey(), in.createByteArray() ) );
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
        if( password != null )
            try {
                enc_pw = Crypt.encrypt( Crypt.getRawKey(), getPassword().getBytes() );
            } catch( Exception e ) {
                Log.e( TAG, "on password encryption", e );
            }
        dest.writeString( getUserName() );
        dest.writeByteArray( enc_pw );
    }

    public String toOldEncriptedString() {
        try {
            return Crypt.encrypt( getUserName() + ":" + getPassword(), false );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
        return null;
    }
    public static Credentials fromOldEncriptedString( String s ) {
        try {
            return new Credentials( Crypt.decrypt( s, false ) );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
        return null;
    }
    
    public String toEncriptedString( Context ctx ) {
        try {
            String toc = getUserName() + ":" + getPassword();
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 )
                return Crypt.encrypt( ctx, toc, false );
            else
                return Crypt.encrypt( toc, false );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
        return null;
    }
    public static Credentials fromEncriptedString( String s, Context ctx ) {
        try {
            String up = null;
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 )
                up = Crypt.decrypt( ctx, s, false );
            else
                up = Crypt.decrypt( s, false );
            return new Credentials( up );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
        return null;
    }
    
}
