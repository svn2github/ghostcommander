package com.ghostsq.commander;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

class ExecEngine extends Engine {
    public final static String TAG = "ExecEngine";
    public  String sh = "su";
    private Context conetxt;
    private String  where, command;
    private boolean use_busybox = false;
    private int     wait_timeout = 500;
    private StringBuilder result;
    ExecEngine( Context conetxt_, Handler h ) {
        super( h );
        conetxt = conetxt_;
        where = null;
        command = null;
        result = null;
    }
    ExecEngine( Context conetxt_, Handler h, String where_, String command_, boolean use_bb, int timeout ) {
        super( h );
        conetxt = conetxt_;
        where = where_;
        command = command_;
        use_busybox = use_bb; 
        wait_timeout = timeout;
        result = new StringBuilder( 1024 );
    }

    @Override
    public void run() {
        try {
            if( command == null ) return; 
            execute( command, use_busybox, wait_timeout );
        } catch( Exception e ) {
            error( "Exception: " + e );
        }
        sendResult( result != null && result.length() > 0 ? result.toString() : 
               ( errMsg != null ? "\nWere tried to execute '" + command + "'" : null ) );
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
            DataInputStream  is = result != null ? new DataInputStream( p.getInputStream() ) : null;
            
            if( where != null ) {
                os.writeBytes( "cd " + where + "\n" ); // execute the command
                os.flush();
            }
            String to_exec = bb + cmd + "\n";
            Log.v( TAG, "executing '" + to_exec + "'" );
            os.writeBytes( to_exec ); // execute the command
            os.flush();
            Thread.sleep( timeout );

            while( es.available() > 0 ) {
                if( isStopReq() ) 
                    throw new Exception();
                String ln = es.readLine();
                if( ln == null ) break;
                error( ln );
            }
            if( is != null && result != null )
                while( is.available() > 0 ) {
                    if( isStopReq() ) 
                        throw new Exception();
                    String ln = is.readLine();
                    if( ln == null ) break;
                    result.append( ln );
                    result.append( "\n" );
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
