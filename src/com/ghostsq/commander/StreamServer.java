package com.ghostsq.commander;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.utils.Utils;

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
    
    public  CommanderAdapter ca = null;

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
                int count = 0;
                while( true ) {
                    Log.d( TAG, "Listening for a connection..." );
                    Socket data_socket = ss.accept();
                    Log.d( TAG, "Connection accepted" );
                    if( data_socket != null && data_socket.isConnected() ) {
                        stream_thread = new StreamingThread( data_socket, count++ );
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
        private int num_id;

        public StreamingThread( Socket data_socket_, int num_id_ ) {
            data_socket = data_socket_;
            num_id = num_id_;
        }
        
        private void Log( String s ) {
            //Log.d( TAG, "" + num_id + ": " + s );
        }
        
        
        public void run() {
            InputStream  is = null;
            OutputStream os = null;
            try {
                Log.d( TAG, "Thread started" );
                setName( TAG );
                //setPriority( Thread.MAX_PRIORITY );
                setPriority( Thread.NORM_PRIORITY );
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
                                    Log( hl );
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
                                    Log( "Got URI: " + uri.toString() );
                                    String scheme = uri.getScheme();
                                    int ca_type = CA.GetAdapterTypeId( scheme );
                                    
                                    if( StreamServer.this.ca == null || StreamServer.this.ca.getType() != ca_type ) 
                                        ca = CA.CreateAdapterInstance( ca_type, ctx );  // kind of vandalism, but whatever...
                                    if( ca != null ) {
                                        Log( "Adapter is created" );
                                        Uri auth_uri = fv.getUriWithAuth();
                                        Item item = ca.getItem( auth_uri );
                                        InputStream cs = ca.getContent( auth_uri );
                                        if( cs != null ) {
                                            if( offset > 0 && item != null ) {
                                                Log( "Going to skip " + offset );
                                                offset = cs.skip( offset );
                                                Log( "skipped " + offset );
                                            }
                                            if( offset > 0 && item != null ) {
                                                Log( "206" );
                                                osw.write( http + "206 Partial Content" + CRLF );
                                            } else {
                                                Log( "200" );
                                                osw.write( http + "200 OK" + CRLF );
                                            }
                                            String fn = "zip".equals( scheme ) ? uri.getFragment() : uri.getLastPathSegment();
                                            if( fn != null ) {
                                                String ext = Utils.getFileExt( fn );
                                                String mime = Utils.getMimeByExt( ext );
                                                Log( "Content-Type: " + mime );
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
                                                Log( content_range );
                                            }
                                            Date date = new Date();
                                            osw.write( date + CRLF );
                                            osw.write( CRLF );
                                            osw.flush();
                                            
                                            ReaderThread rt = new ReaderThread( cs, num_id );
                                            rt.start();
                                            int count = 0;
                                            while( true ) {
                                                try {
                                                    byte[] out_buf = rt.getOutputBuffer();
                                                    if( out_buf == null ) break;
                                                    int n = rt.GetDataSize();
                                                    if( n < 0 )
                                                        break;
                                                    Log( "Before write" );
                                                    os.write( out_buf, 0, n );
                                                    Log( "After write, total " + ( count += n ) );
                                                }
                                                catch( Exception e ) {
                                                    Log( "write exception: " + e.getMessage() );
                                                    break;
                                                }
                                                finally {
                                                    rt.doneOutput();
                                                    Log( "final notified" );
                                                }
                                            }                                            
                                            ca.closeStream( cs );
                                            rt.interrupt();
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
        private int      roller = 0, chunk = 4096;
        private final static int MAX = 131072;
        private byte[][] bufs = { new byte[MAX], new byte[MAX] };
        private byte[]   out_buf = null;
        private int      data_size = 0;
        private int      num_id;
        
        public ReaderThread( InputStream is_, int num_id_ ) {
            is = is_;
            setName( TAG );
            num_id = num_id_;
        }
        
        private void Log( String s ) {
            //Log.d( TAG, "" + num_id + ": " + s );
        }
        public void run() {
            try {
                //setPriority( Thread.MAX_PRIORITY );
                setPriority( Thread.NORM_PRIORITY );
                int count = 0;
                while( true ) {
                    byte[] inp_buf = bufs[roller++ % 2];
                    Log( "Before read" );
                    data_size = is.read( inp_buf, 0, chunk );
                    if( data_size < 0 )
                        break;
                    if( chunk < MAX )
                        chunk <<= 1;
                    if( chunk > MAX )
                        chunk = MAX;                    
                    Log( "After read " + data_size + " total " + ( count += data_size ) );
                    synchronized( this ) {
                        int wcount = 0; 
                        while( out_buf != null ) {
                            //Log( "Waiting when the output buffer is released..." );
                            wait( 10 );
                            wcount += 10;
                        }
                        out_buf = inp_buf;
                        Log( "The output buffer is released after " + wcount + "ms and set ready. Notification is sent." );
                        notify();
                    }
                }
            } catch( Exception e ) {
                Log.e( TAG, "Exception: " + e );
            }
            Log( "The thread is done!" );
        }
        public synchronized byte[] getOutputBuffer() throws InterruptedException {
            int wcount = 0;
            while( out_buf == null && this.isAlive() ) {
                //Log( "Waiting when the output buffer is ready" );
                wait( 10 );
                wcount += 10;
            }
            if( out_buf != null ) 
                Log( "The output buffer is really ready after " + wcount + "ms" );
            else
                Log( "The reader thread is died!" );
            return out_buf;
        }
        public int GetDataSize() {
            int ds = data_size;
            data_size = 0;
            return ds;
        }
        public synchronized void doneOutput() {
            out_buf = null;
            Log( "The output buffer is released. Notification is sent." );
            notify();
        }
    };
    
    
    @Override
    public IBinder onBind( Intent intent ) {
        return null;
    }
}
