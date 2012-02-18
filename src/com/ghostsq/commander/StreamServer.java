package com.ghostsq.commander;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.utils.Utils;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;


public class StreamServer extends Service {
    private final static String TAG = "StreamServer";
    private Context ctx;
    private Thread  thread;
    private NotificationManager notMan;

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = getApplicationContext();
        notMan = (NotificationManager)getSystemService( Context.NOTIFICATION_SERVICE );
    }

    @Override
    public void onStart( Intent intent, int start_id ) {
        super.onStart( intent, start_id );
        
        thread = new Thread( null, runnable, TAG );
        thread.start();
        getBaseContext();
    }

    Runnable runnable = new Runnable() {
        public void run() {
            InputStream  is = null;
            OutputStream os = null;
            try {
                ServerSocket ss = new ServerSocket( 5322 );
                Socket data_socket = ss.accept();
                ss.close();
                if( data_socket != null && data_socket.isConnected() ) {
                    is = data_socket.getInputStream();
                    InputStreamReader isr = new InputStreamReader( is );
                    BufferedReader br = new BufferedReader( isr );
                    String cmd = br.readLine();
                    if( Utils.str( cmd ) ) {
                        String[] parts = cmd.split( " " );
                        if( parts.length > 1 ) {
                            String uri_s = Uri.decode( parts[1].substring( 1 ) );
                            Log.d( TAG, "Got URI: " + uri_s );
                            while( br.ready() ) {
                                String hl = br.readLine(); 
                                Log.v( TAG, hl );
                            }
                            os = data_socket.getOutputStream();
                            if( os != null ) {
                                String http = "HTTP/1.1 ";  
                                OutputStreamWriter osw = new OutputStreamWriter( os );
                                
                                Uri uri = Uri.parse( uri_s );
                                if( uri != null ) { 
                                    int ca_type = CA.GetAdapterTypeId( uri.getScheme() );
                                    CommanderAdapter ca = CA.CreateAdapterInstance( ca_type, ctx );
                                    if( ca != null ) {
                                        InputStream cs = ca.getContent( Uri.parse( uri_s ) );
                                        if( cs != null ) {
                                            Log.d( TAG, "200" );
                                            osw.write( http + "200 OK\n\r" );
                                            osw.write( "Content-Type: audio/mp3\n\r\n\r" );
                                            osw.flush();
                                            Utils.copyBytes( cs, os );
                                            ca.closeStream( cs );
                                        }
                                        else {
                                            osw.write( http + "404 Not found\n\r" );
                                            Log.w( TAG, "404" );
                                        }
                                    }
                                    else {
                                        osw.write( http + "500 Server error\n\r" );
                                        Log.e( TAG, "500" );
                                    }
                                } else {
                                    osw.write( http + "400 Invalid\n\r" );
                                    Log.w( TAG, "400" );
                                }
                            }
                        }
                    }
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "Exception", e );
            }
            finally {
                try {
                    if( is != null ) is.close();
                    if( os != null ) os.close();
                }
                catch( IOException e ) {
                    Log.e( TAG, "Exception on Closing", e );
                }
            }
            StreamServer.this.stopSelf();
        }
    };    
    
    @Override
    public IBinder onBind( Intent intent ) {
        return null;
    }
}
