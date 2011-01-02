package com.ghostsq.commander;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

abstract class ExecEngine extends Engine {
    public final static String TAG = "ExecEngine";
    public       static String sh = "su";
    private Context conetxt;
    ExecEngine( Context conetxt_, Handler h ) {
        super( h );
        conetxt = conetxt_;
    }
    
    protected String getBusyBox() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( conetxt );
        return sharedPref.getString( "busybox_path", "busybox" );
    }
    
    protected void execute( String cmd, boolean use_bb, int timeout ) {
        try {
            String bb = use_bb ? getBusyBox() + " " : "";
            Process p = Runtime.getRuntime().exec( sh );
            DataOutputStream os = new DataOutputStream( p.getOutputStream() );
            DataInputStream  es = new DataInputStream( p.getErrorStream() );
            String to_exec = bb + cmd + "\n";
            Log.v( TAG, "executing '" + to_exec + "'" );
            os.writeBytes( to_exec ); // execute the command
            os.flush();
            Thread.sleep( timeout );
            if( es.available() > 0 ) {
                String err_msg = es.readLine(); 
                error( err_msg );
                Log.e( TAG, err_msg );
            }
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            if( p.exitValue() == 255 )
                Log.e( TAG, "Exit code 255" );
        }
        catch( Exception e ) {
            Log.e( TAG, "On execution '" + cmd + "'", e );
            error( "Exception: " + e );
        }
    }
}
