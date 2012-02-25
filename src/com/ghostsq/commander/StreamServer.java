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
import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.favorites.Favorite;
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
    private final static String CRLF = "\r\n";
    private Context ctx;
    private Thread  thread = null;

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;  //getApplicationContext();
    }

    @Override
    public void onStart( Intent intent, int start_id ) {
        super.onStart( intent, start_id );
        Log.d( TAG, "onStart" );
        if( thread == null ) {
            Log.d( TAG, "Starting the server thread" );
            thread = new ListenThread();
            thread.start();
            getBaseContext();
        }
    }

    private class ListenThread extends Thread {
        private final static String TAG = "GCSS.ListenThread";
        private Thread stream_thread;
        public void run() {
            ServerSocket ss = null;
            try {
                Log.d( TAG, "Thread started" );
                setName( TAG );
                setPriority( Thread.MIN_PRIORITY );
                ss = new ServerSocket( 5322 );
                while( true ) {
                    Log.d( TAG, "Listening for a connection..." );
                    Socket data_socket = ss.accept();
                    Log.d( TAG, "Connection accepted" );
                    if( data_socket != null && data_socket.isConnected() ) {
                        stream_thread = new StreamingThread( data_socket );
                        stream_thread.start();
                    }
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "Exception", e );
            }
            finally {
                try {
                    if( ss != null ) ss.close();
                }
                catch( IOException e ) {
                    Log.e( TAG, "Exception on Closing", e );
                }
            }
            StreamServer.this.stopSelf();
        }
    };    

    private class StreamingThread extends Thread {
        private final static String TAG = "GCSS.StreamingThread";
        private Socket data_socket;

        public StreamingThread( Socket data_socket_ ) {
            data_socket = data_socket_;
        }
        
        public void run() {
            InputStream  is = null;
            OutputStream os = null;
            try {
                Log.d( TAG, "Thread started" );
                setName( TAG );
                setPriority( Thread.MAX_PRIORITY );
                if( data_socket != null && data_socket.isConnected() ) {
                    is = data_socket.getInputStream();
                    InputStreamReader isr = new InputStreamReader( is );
                    BufferedReader br = new BufferedReader( isr );
                    String cmd = br.readLine();
                    if( Utils.str( cmd ) ) {
                        String[] parts = cmd.split( " " );
                        if( parts.length > 1 ) {
                            String url = Uri.decode( parts[1].substring( 1 ) );
                            //Log.d( TAG, "Got URL: " + url );
                            Favorite fv = new Favorite( url );
                            long offset = 0;
                            while( br.ready() ) {
                                String hl = br.readLine();
                                if( hl != null ) {
                                    Log.v( TAG, hl );
                                    if( hl.startsWith( "Range: bytes=" ) ) {
                                        int end = hl.indexOf( '-', 13 );
                                        String range_s = hl.substring( 13, end );
                                        try {
                                            offset = Long.parseLong( range_s );
                                        } catch( NumberFormatException nfe ) {}
                                    }
                                }
                            }
                            os = data_socket.getOutputStream();
                            if( os != null ) {
                                String http = "HTTP/1.1 ";  
                                OutputStreamWriter osw = new OutputStreamWriter( os );
                                Uri uri = fv.getUri();
                                if( uri != null ) { 
                                    Log.d( TAG, "Got URI: " + uri.toString() );
                                    String scheme = uri.getScheme();
                                    int ca_type = CA.GetAdapterTypeId( scheme );
                                    CommanderAdapter ca = CA.CreateAdapterInstance( ca_type, ctx );
                                    if( ca != null ) {
                                        Log.d( TAG, "Adapter is created" );
                                        Uri auth_uri = fv.getUriWithAuth();
                                        Item item = ca.getItem( auth_uri );
                                        InputStream cs = ca.getContent( auth_uri );
                                        if( cs != null ) {
                                            if( offset > 0 && item != null ) {
                                                Log.d( TAG, "Going to skip " + offset );
                                                offset = cs.skip( offset );
                                                Log.d( TAG, "skipped " + offset );
                                            }
                                            if( offset > 0 && item != null ) {
                                                Log.d( TAG, "206" );
                                                osw.write( http + "206 Partial Content" + CRLF );
                                            } else {
                                                Log.d( TAG, "200" );
                                                osw.write( http + "200 OK" + CRLF );
                                            }
                                            String fn = "zip".equals( scheme ) ? uri.getFragment() : uri.getLastPathSegment();
                                            if( fn != null ) {
                                                String ext = Utils.getFileExt( fn );
                                                String mime = Utils.getMimeByExt( ext );
                                                Log.d( TAG, "Content-Type: " + mime );
                                                osw.write( "Content-Type: " + mime + CRLF );
                                            }
                                            else
                                                osw.write( "Content-Type: application/octet-stream" + CRLF );
                                            if( item != null ) {
                                                String content_range = null;
                                                if( offset == 0 ) {
                                                    content_range = "Content-Range: bytes 0-" + (item.size-1) + "/" + item.size; 
                                                    osw.write( "Content-Length: " + item.size + CRLF );
                                                    osw.write( content_range + CRLF );
                                                } else {
                                                    content_range = "Content-Range: bytes " + offset + "-" + (item.size-1) + "/" + item.size;
                                                    osw.write( "Content-Length: " + item.size + CRLF );
                                                    osw.write( content_range + CRLF );
                                                }
                                                Log.d( TAG, content_range );
                                            }
                                            osw.write( CRLF );
                                            osw.flush();
                                            
                                            ReaderThread rt = new ReaderThread( cs );
                                            rt.start();
                                            while( true ) {
                                                try {
                                                    byte[] out_buf = rt.getOutputBuffer();
                                                    int n = rt.GetDataSize();
                                                    if( n < 0 )
                                                        break;
                                                    Log.v( TAG, "Before write" );
                                                    os.write( out_buf, 0, n );
                                                    Log.v( TAG, "After write" );
                                                }
                                                catch( Exception e ) {
                                                    Log.d( TAG, "write exception", e );
                                                    rt.interrupt();
                                                    break;
                                                }
                                                finally {
                                                    rt.doneOutput();
                                                }
                                            }                                            
                                            ca.closeStream( cs );
                                        }
                                        else {
                                            osw.write( http + "404 Not found" + CRLF );
                                            Log.w( TAG, "404" );
                                        }
                                    }
                                    else {
                                        osw.write( http + "500 Server error" + CRLF );
                                        Log.e( TAG, "500" );
                                    }
                                } else {
                                    osw.write( http + "400 Invalid" + CRLF );
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
        }
    };
    
    class ReaderThread extends Thread {
        private final static String TAG = "GCSS.ReaderThread";
        private InputStream is;
        private int      roller = 0;
        private byte[][] bufs = { new byte[116384], new byte[116384] };
        private byte[]   out_buf = null;
        private int      data_size = 0;
        
        public ReaderThread( InputStream is_ ) {
            is = is_;
            setName( TAG );
        }
        
        public void run() {
            try {
                int count = 0;
                while( true ) {
                    byte[] inp_buf = bufs[roller++ % 2];
                    Log.v( TAG, "Before read" );
                    data_size = is.read( inp_buf );
                    Log.v( TAG, "After read " + data_size + " total " + ( count += data_size ) );
                    synchronized( this ) {
                        while( out_buf != null ) {
                            Log.v( TAG, "Waiting when the output buffer is released..." );
                            wait( 5000 );
                        }
                        out_buf = inp_buf;
                        Log.v( TAG, "The output buffer is ready!" );
                        notify();
                    }
                    if( data_size < 0 )
                        break;
                }
            } catch( Exception e ) {
                Log.e( TAG, "Exception: " + e );
            }
            Log.v( TAG, "The thread is done!" );
        }
        public synchronized byte[] getOutputBuffer() throws InterruptedException {
            while( out_buf == null ) {
                Log.v( TAG, "Waiting when the output buffer is ready" );
                wait( 5000 );
            }
            Log.v( TAG, "The output buffer is ready" );
            return out_buf;
        }
        public int GetDataSize() {
            return data_size;
        }
        public synchronized void doneOutput() {
            out_buf = null;
            Log.v( TAG, "!!! The output buffer is released!" );
            notify();
        }
    };
    
    
    @Override
    public IBinder onBind( Intent intent ) {
        return null;
    }
}
