package com.ghostsq.commander;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

class ExecEngine extends Engine {
    public final static String TAG = "ExecEngine";
    public    String sh = "su";
    protected Context context;
    private   String  where, command;
    private   boolean use_busybox = false;
    private   int wait_timeout = 500;
    private   StringBuilder result;
    ExecEngine( Context context_, Handler h ) {
        super( h );
        context = context_;
        where = null;
        command = null;
        result = null;
    }
    ExecEngine( Context context_, Handler h, String where_, String command_, boolean use_bb, int timeout ) {
        super( h );
        context = context_;
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
            Init( null );
            execute( command, use_busybox, wait_timeout );
        } catch( Exception e ) {
            error( "Exception: " + e );
        }
        sendResult( result != null && result.length() > 0 ? result.toString() : 
               ( errMsg != null ? "\nWere tried to execute '" + command + "'" : null ) );
    }
    
    protected String getBusyBox() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( context );
        return sharedPref.getString( "busybox_path", "busybox" );
    }
    
    protected void execute( String cmd, boolean use_bb, int timeout ) {
        try {
            String bb = use_bb ? getBusyBox() + " " : "";
            Process p = Runtime.getRuntime().exec( sh );
            OutputStreamWriter os = new OutputStreamWriter( p.getOutputStream() );
            BufferedReader is = result != null ? new BufferedReader( new InputStreamReader( p.getInputStream() ) ) : null;
            BufferedReader es = new BufferedReader( new InputStreamReader( p.getErrorStream() ) );
            
            if( where != null ) {
                os.write( "cd '" + where + "'\n" ); // execute the command
                os.flush();
            }
            String to_exec = bb + cmd + "\n";
            Log.v( TAG, "executing '" + to_exec + "'" );
            os.write( to_exec ); // execute the command
            os.flush();
            Thread.sleep( timeout );

            if( es.ready() ) {
                String err_str = es.readLine();
                if( err_str.trim().length() > 0 ) {
                    error( err_str );
                }
            }
            if( is != null && result != null )
                while( is.ready() ) {
                    if( isStopReq() ) 
                        throw new Exception();
                    String ln = is.readLine();
                    if( ln == null ) break;
                    result.append( ln );
                    result.append( "\n" );
                }
            os.write( "exit\n" );
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
