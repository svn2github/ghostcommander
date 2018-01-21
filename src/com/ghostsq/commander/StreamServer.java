package com.ghostsq.commander;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

public class StreamServer extends Service {
    private final static String TAG = "StreamServer";
    public  final static int server_port = 5322;
    public  final static boolean verbose_log = false;
    public  WifiLock wifiLock = null;
    private ListenThread thread = null;
    private CommanderAdapter ca = null;
    private Uri  prev_uri = null;
    private long prev_size = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        WifiManager manager = (WifiManager)getSystemService( Context.WIFI_SERVICE );
        wifiLock = manager.createWifiLock( TAG );
        wifiLock.setReferenceCounted( false );
    }

    @Override
    public int onStartCommand( Intent intent, int flags, int start_id ) {
        super.onStartCommand( intent, flags, start_id );
        Log.d( TAG, "onStart" );
        if( thread == null ) {
            Log.d( TAG, "Starting the server thread" );
            thread = new ListenThread();
            thread.start();
            getBaseContext();
        }
        return START_STICKY;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d( TAG, "onDestroy" );
        if( thread != null && thread.isAlive() ) {
            thread.close();
            thread.interrupt();
            setCA( null );
            try {
                thread.join( 10000 );
            } catch( InterruptedException e ) {
                e.printStackTrace();
            }
            if( thread.isAlive() )
                Log.e( TAG, "Listen tread has ignored the interruption" );
        }
    }

    public static void storeCredentials( Context ctx, Credentials crd, Uri uri ) {
        int hash = ( crd.getUserName() + uri.getHost() ).hashCode();
        SharedPreferences ssp = ctx.getSharedPreferences( StreamServer.class.getSimpleName(), MODE_PRIVATE );
        SharedPreferences.Editor edt = ssp.edit();
        edt.putString( "" + hash, crd.toEncriptedString( ctx ) );
        edt.commit();
    }

    public static Credentials restoreCredentials( Context ctx, Uri uri ) {
        int hash = ( uri.getUserInfo() + uri.getHost() ).hashCode();
        SharedPreferences ssp = ctx.getSharedPreferences( StreamServer.class.getSimpleName(), MODE_PRIVATE );
        String crd_enc_s = ssp.getString( "" + hash, null );
        if( crd_enc_s == null )
            return null;
        return Credentials.fromEncriptedString( crd_enc_s, ctx );
    }

    public synchronized void setCA( CommanderAdapter new_ca ) {
        if( ca != null )
            ca.prepareToDestroy();
        ca = new_ca;
    }

    public synchronized CommanderAdapter createCA( Uri uri ) {
        try {
            String scheme = uri.getScheme();
            if( scheme == null ) scheme = "";
            String host = uri.getHost();
            if( ca != null ) {
                Log.d( TAG, "Adapter was created before" );
                if( !scheme.equals( ca.getScheme() ) ) {
                    ca.prepareToDestroy();
                    ca = null; 
                }
                else {
                    prev_uri = ca.getUri();
                    if( prev_uri == null || (host != null && !host.equals( prev_uri.getHost() ) ) ) {
                        Log.d( TAG, "Requested a new resource");
                        ca.prepareToDestroy();
                        ca = null;
                    }
                }
            }
            if( ca == null ) {
                prev_size = -1;
                Log.d( TAG, "Creating new adapter instance" );
                ca = CA.CreateAdapterInstance( uri, this );
                if( ca == null ) {
                    return null;
                }
                ca.Init( null );
                String ui = uri.getUserInfo();
                if( ui != null ) {
                    Credentials credentials = StreamServer.restoreCredentials( this, uri );
                    if( credentials != null ) {
                        Log.d( TAG, "Found credentials for " + ui );
                        ca.setCredentials( credentials );
                        uri = Utils.updateUserInfo( uri, null );
                    } else
                        Log.w( TAG, "No credentials" );
                }
                Log.d( TAG, "Adapter is created" );
            } 
            ca.setUri( uri );
            prev_uri = uri;
            return ca;
        } catch( Exception e ) {
            Log.e( TAG, "Creating CA for URI " + uri.toString(), e );
        }
        return null;
    }

    public synchronized long getPrevSize() {
        return prev_size;
    }
    
    public synchronized void setPrevSize( long sz ) {
        prev_size = sz;
    }
    
    private class ListenThread extends Thread {
        private final static String TAG = "GCSS.ListenThread";
        private ArrayList<Thread> streamThreads = new ArrayList<Thread>();
        public  ServerSocket ss = null;
        public long lastUsed = System.currentTimeMillis();

        public void run() {
            try {
                Log.d( TAG, "started" );
                setName( TAG );
                setPriority( Thread.MIN_PRIORITY );
                new Thread( new Runnable() {
                    @Override
                    public void run() {
                        while( true ) {
                            try {
                                synchronized( ListenThread.this ) {
                                    final int max_idle = 1000000;
                                    ListenThread.this.wait( max_idle );
                                    Log.d( TAG, "Checking the idle time... last used: " + ( System.currentTimeMillis() - lastUsed )
                                            + "ms ago " );
                                    if( System.currentTimeMillis() - max_idle > lastUsed ) {
                                        Log.d( TAG, "Time to closer the listen thread" );
                                        ListenThread.this.close();
                                        break;
                                    }
                                }
                            } catch( InterruptedException e ) {
                                e.printStackTrace();
                            }
                        }
                        Log.d( TAG, "Closer thread stopped" );
                    }
                }, "Closer" ).start();

                StreamServer.this.wifiLock.acquire();
                Log.d( TAG, "WiFi lock" );
                synchronized( this ) {
                    ss = new ServerSocket( StreamServer.server_port );
                }
                int count = 0;
                while( !isInterrupted() ) {
                    Log.d( TAG, "Listening for a new connection..." );
                    Socket data_socket = ss.accept();
                    Log.d( TAG, "Connection accepted" );
                    if( data_socket != null && data_socket.isConnected() ) {
                        int tn = count++;//
                        Thread stream_thread = new StreamingThread( data_socket, tn );
                        stream_thread.start();
                        streamThreads.add( stream_thread );
                    }
                    touch();
                    removeDeadThreads();
                }
            } catch( Exception e ) {
                Log.w( TAG, "Exception", e );
            } finally {
                StreamServer.this.wifiLock.release();
                Log.d( TAG, "WiFi lock release" );
                this.close();
            }
            StreamServer.this.stopSelf();
        }
        
        public synchronized void touch() {
            lastUsed = System.currentTimeMillis();
        }

        public synchronized void removeDeadThreads() {
            try {
                List<Integer> to_rem = new ArrayList<Integer>(); 
                for( int i = 0; i < streamThreads.size(); i++ ) {
                    StreamingThread stream_thread = (StreamingThread)streamThreads.get( i );
                    if( !stream_thread.isAlive() ) {
                        Log.d( TAG, "Removing dead streaming thread object " + stream_thread.getNum() );
                        to_rem.add( i );
                    }
                    if( !stream_thread.isConnected() ) {
                        Log.d( TAG, "Interrupting disconnected thread " + stream_thread.getNum() );
                        stream_thread.interrupt();
                    }
                }
                for( Integer i : to_rem )
                    streamThreads.remove( i.intValue() );
            } catch( Exception e ) {
                Log.e( TAG, "Threads count: " + streamThreads.size(), e );
            }
        }

        public synchronized void close() {
            try {
                if( ss != null ) {
                    ss.close();
                    ss = null;
                }
                for( Thread stream_thread : streamThreads ) {
                    if( stream_thread.isAlive() )
                        stream_thread.interrupt();
                }
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
    };

    private class StreamingThread extends Thread {
        private final static String TAG = "GCSS.ST";
        private final static String CRLF = "\r\n";
        private Socket data_socket;
        private int num_id;
        private boolean l = true;//StreamServer.verbose_log;

        public StreamingThread(Socket data_socket_, int num_id_) {
            data_socket = data_socket_;
            num_id = num_id_;
        }

        public boolean isConnected() {
            return data_socket != null && data_socket.isConnected();
        }
        
        public int getNum() {
            return num_id;
        }

        @Override
        public void run() {
            InputStream  is = null;
            OutputStream os = null;
            try {
                if( l ) Log( "Thread started" );
                setName( TAG );
                if( !isConnected() ) {
                    Log.e( TAG, "Invalid data socked" );
                    return;
                }
                os = data_socket.getOutputStream();
                if( os == null ) {
                    Log.e( TAG, "Can't get the output stream" );
                    return;
                }
                OutputStreamWriter osw = new OutputStreamWriter( os );
                yield();
                is = data_socket.getInputStream();
                if( is == null ) {
                    Log.e( TAG, "Can't get the input stream" );
                    SendStatus( osw, 500 );
                    return;
                }
                InputStreamReader isr = new InputStreamReader( is );
                BufferedReader br = new BufferedReader( isr );
                String cmd = br.readLine();
                if( !Utils.str( cmd ) ) {
                    Log.e( TAG, "Invalid HTTP input" );
                    SendStatus( osw, 400 );
                    return;
                }
                                
                String[] parts = cmd.split( " " );
                if( l ) Log( cmd );
                if( parts.length <= 1 ) {
                    Log.e( TAG, "Invalid HTTP input" );
                    SendStatus( osw, 400 );
                    return;
                }
                String passed_uri_s = parts[1].substring( 1 );
                if( passed_uri_s.indexOf( "%2F" ) < 0 )
                    passed_uri_s = new String( Base64.decode( passed_uri_s, Base64.URL_SAFE ) );
                if( !Utils.str( passed_uri_s ) ) {
                    Log.w( TAG, "No URI passed in the request" );
                    SendStatus( osw, 404 );
                    return;
                } 
                Uri uri = Uri.parse( Uri.decode( passed_uri_s ) );
                if( uri == null || !Utils.str( uri.getPath() ) ) {
                    Log.w( TAG, "Wrong URI passed in the request" );
                    SendStatus( osw, 404 );
                    return;
                } 
                if( l ) Log( "Requested URI: " + uri );
                
                long offset = 0;
                while( br.ready() ) {
                    String hl = br.readLine();
                    if( !Utils.str( hl ) ) break;
                    if( l ) Log( hl );
                    if( hl.startsWith( "Range: bytes=" ) ) {
                        int end = hl.indexOf( '-', 13 );
                        String range_s = hl.substring( 13, end );
                        try {
                            offset = Long.parseLong( range_s );
                        } catch( NumberFormatException nfe ) {}
                    }
                }

                CommanderAdapter c_a = StreamServer.this.createCA( uri );
                if( c_a == null ) {
                    Log.e( TAG, "Can't get the CA for " + uri );
                    SendStatus( osw, 500 );
                    return;
                }
                long item_size = StreamServer.this.getPrevSize(); 
                if( item_size <= 0 ) {
                    Item item = c_a.getItem( uri );
                    if( item == null ) {
                        Log.e( TAG, "Can't get the item for " + uri );
                        SendStatus( osw, 404 );
                        return;
                    }
                    item_size = item.size;
                    StreamServer.this.setPrevSize( item_size );
                }
                
                InputStream cs = c_a.getContent( uri, offset );
                if( cs == null ) {
                    Log.e( TAG, "Can't get the content for " + uri );
                    SendStatus( osw, 404 );
                    return;
                } 
                if( offset > 0 ) {
                    SendStatus( osw, 206 );
                } else {
                    SendStatus( osw, 200 );
                }
                String fn = "zip".equals( uri.getScheme() ) ? uri.getFragment() : uri.getLastPathSegment();
                if( !writeHeader( osw, item_size, offset, fn ) ) return;
                pumpData( cs, os );
                c_a.closeStream( cs );
            }
            catch( Exception e ) {
                Log.e( TAG, "Exception in thread " + num_id, e );
            }
            finally {
                if( l ) Log( "Thread exits" );
                try {
                    if( is != null ) is.close();
                    if( os != null ) os.close();
                }
                catch( IOException e ) {
                    Log.e( TAG, "Exception on Closing", e );
                }
            }
        }                
        
        private final void Log( String s ) {
            Log.v( TAG, "" + num_id + ": " + s );
        }

        private final void SendStatus( OutputStreamWriter osw, int code ) throws IOException {
            final String http = "HTTP/1.0 ";
            String descr;
            switch( code ) {
            case 200:
                descr = "OK";
                break;
            case 206:
                descr = "Partial Content";
                break;
            case 400:
                descr = "Invalid";
                break;
            case 404:
                descr = "Not found";
                break;
            case 416:
                descr = "Bad Requested Range";
                break;
            case 500:
                descr = "Server error";
                break;
            default:
                descr = "";
            }
            String resp = http + code + " " + descr;
            osw.write( resp + CRLF );
            if( l )
                Log( resp );
        }

        private boolean writeHeader( OutputStreamWriter osw, long full_size, long offset, String fn ) {
            try {
                if( fn != null ) {
                    String ext = Utils.getFileExt( fn );
                    String mime = Utils.getMimeByExt( ext );
                    if( l ) Log( "Content-Type: " + mime );
                    osw.write( "Content-Type: " + mime + CRLF );
                }
                else
                    osw.write( "Content-Type: application/octet-stream" + CRLF );
                
                String ds = new SimpleDateFormat( "EEE, d MMM yyyy HH:mm:ss Z", Locale.US ).format( new Date() );
                osw.write( "Date: " + ds + CRLF );
                
                String content_range  = "Content-Range: bytes "; 
                String content_length = "Content-Length: ";
                if( offset == 0 ) {
                    content_length += full_size;
                    content_range  += "0-" + (full_size-1) + "/" + full_size;
                }
                else {
                    content_length += (full_size - offset);
                    content_range  += offset + "-" + (full_size-1) + "/" + full_size;
                }
                osw.write( content_range + CRLF );
// When Accept-Ranges is not sent VLC does not allow seeking
//                osw.write( "Accept-Ranges: bytes" + CRLF );
                osw.write( content_length + CRLF );
                if( l ) Log( content_length );
                if( l ) Log( content_range );
                // VLC fails when this is returned?
                osw.write( "Connection: close" + CRLF );
                osw.write( CRLF );
                osw.flush();
                return true;
            } catch( Exception e ) {
                Log.e( TAG, "Exception in thread " + num_id, e );
            }
            return false;
        }                

        private void pumpData( InputStream is, OutputStream os ) {
            final int MAX = 262144*8;
            int chunk = 4096;
            byte[] buf = new byte[MAX];
            int count = 0;
            while( true ) {
                try {
                    if( l ) Log( "Reading... " );
                    long before_r = System.currentTimeMillis();
                    int has_read = is.read( buf, 0, chunk );
                    long r_took = System.currentTimeMillis() - before_r;
                    if( has_read < 0 ) {
                        Log.e( TAG, "" + num_id + ": Failed to read" );
                        break;
                    }
                    count += has_read;
                    if( l ) Log( "Was read: " + has_read + "/" + count + " took " + r_took + "ms" );
                    
                    if( has_read == chunk && chunk < MAX ) {
                        chunk <<= 1;
                        Log( "Inc chunk to: " + chunk );
                    } else if( has_read < chunk && has_read > 128 ) {
                        chunk = has_read;
                        Log( "Dec chunk to: " + chunk );
                    }
                    if( chunk > MAX )
                        chunk = MAX;
                    long before_w = System.currentTimeMillis();
                    os.write( buf, 0, has_read );
                    long w_took = System.currentTimeMillis() - before_w;
                    if( l ) Log( "Writing took " + w_took + "ms" );
                }
                catch( Exception e ) {
                    Log.e( TAG, "" + num_id + ": exception: " + e.getMessage(), e );
                    break;
                }
            }
        }                
    }
    
    @Override
    public IBinder onBind( Intent intent ) {
        return null;
    }
}
