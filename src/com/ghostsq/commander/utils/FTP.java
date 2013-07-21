package com.ghostsq.commander.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.StringTokenizer;

import android.net.Uri;
import android.util.Log;

public class FTP {
    private final static String TAG = "FTP";
    
	public interface ProgressSink {
		public boolean completed( long size, boolean done ) throws InterruptedException;
	};
	
    private final static int BLOCK_SIZE = 100000;
    private final static boolean PRINT_DEBUG_INFO = true;
    private StringBuffer debugBuf = new StringBuffer();
    private Socket cmndSocket = null;
    private OutputStream outputStream = null;
    private BufferedInputStream inputStream = null;
    private ServerSocket serverSocket = null;
    private Socket dataSocket = null;
    private InputStream inDataStream = null;
    private boolean loggedIn = false;
    private boolean allowActive = false;
    private Charset charset = null;
    
    private final boolean sendCommand( String cmd ) {
        try {
        	if( outputStream == null || cmndSocket == null || !cmndSocket.isConnected() )
        		return false;
        	String out = cmd.startsWith( "PASS" ) ? "PASS ***" : cmd;
        	debugPrint( ">>> " + out );
        	
        	String cmd_rn = cmd + "\r\n";        	
            byte[] bytes = null;
            if( charset != null )
                try {
                    bytes = cmd_rn.getBytes( charset.name() );
                } catch( Exception e ) {
                    Log.w( TAG, "Not supported: " + charset );
                }
            if( bytes == null )
                bytes = cmd_rn.getBytes();
            outputStream.write( bytes );
            return true;
        }
        catch( IOException e ) {
            debugPrint( "connection broken" );
            Log.e( TAG, "", e );
        }
        return false;
    }

    public final void debugPrint( String message ) {
        Log.v( TAG, message );
        if( PRINT_DEBUG_INFO ) {
            debugBuf.append( message );
            debugBuf.append( "\n" );
        }
    }
    private final boolean isPositivePreliminary( int response ) {
        return (response >= 100 && response < 200);
    }
    private final boolean isPositiveComplete( int response ) {
        return (response >= 200 && response < 300);
    }
    private final boolean isPositiveIntermediate( int response ) {
        return (response >= 300 && response < 400);
    }
    private final boolean isNegative( int response ) {
        return (response >= 400 );
    }
    private final boolean waitForPositiveResponse() throws InterruptedException {
        String response = null;
        try {
        	int code;
            do {
                Thread.sleep( 100 );
            	response = getReplyLine();
            	if( response == null ) return false;
                code = getReplyCode( response );
                if( isPositiveComplete( code ) )
                    return true;
                if( isNegative( code ) )
                    return false;
                if( isPositiveIntermediate( code ) )
                    return true; // when this occurred?
                Thread.sleep( 400 );
            } while( isPositivePreliminary( code ) );
		} catch( RuntimeException e ) {
            Log.e( TAG, "Exception " + ( response == null ? "" : (" on response '" + response + "'") + "\n" ), e );
		}
        return false;
    }
    private final int getReplyCode( String reply ) {
        try {
            return reply == null ? -1 : Integer.parseInt( reply.substring( 0, 3 ) );
        }
        catch( NumberFormatException e ) {
            return -1;
        }
    }
    private final void flushReply() {
        try {
            while( inputStream.available() > 0 )
                inputStream.read();
        }
        catch( IOException e ) {
            Log.e( TAG, "", e );
        }
    }
    private final String getReplyLine() {
        try {
        	if( inputStream == null ) {
        		debugPrint( "No Connection" );
        		return null;
        	}
            final int buf_sz = 1024;
            int i;
            byte[] buf = new byte[buf_sz];
            do {
            	int cnt = 0;
            	do
            		if( cnt++ < 200 ) 
            		    Thread.sleep( 100 ); 
            		else {
            		    Log.e( TAG, "The server did not respond. " + inputStream.toString() );
            		    return null;
            		}
            	while( inputStream != null && inputStream.available() == 0 );
                for( i = 0; i < buf_sz; i++ ) {
                    int b = inputStream.read();
                    if( b < 0 )
                        break;
                    if( b == '\r' || b == '\n' ) {
                        buf[i] = 0;
                        break;
                    }
                    buf[i] = (byte)b;
                }
//Log.v( TAG, "\nfrom FTP:" + new String( buf, 0, i ) + "\n" );
            } while( !(Character.isDigit( buf[0] ) &&
                       Character.isDigit( buf[1] ) &&
                       Character.isDigit( buf[2] ) && buf[3] == ' ' ) ); // read until a coded response be found
            String reply = charset != null ? new String( buf, 0, i, charset.name() ) : new String( buf, 0, i );
            
            debugPrint( "<<< " + reply );
            return reply;
        }
        catch( Exception e ) {
            Log.e( TAG, "", e );
            disconnect( true );
            return null;
		}
    }
    public final synchronized boolean connect( String host, int port ) throws UnknownHostException, IOException, InterruptedException {
        cmndSocket = new Socket( host, port );
        outputStream = cmndSocket.getOutputStream();
        inputStream = new BufferedInputStream( cmndSocket.getInputStream(), 256 );

        if( !waitForPositiveResponse() ) {
            disconnect( true );
            return false;
        }

        return true;
    }
    public final void disconnect( boolean brutal ) {
        //if( outputStream != null )  // ??? why? 
        {
            try {
                if( !brutal && loggedIn )
                    logout( true );
                if( outputStream != null ) outputStream.close();
                if(  inputStream != null )  inputStream.close();
                if(   cmndSocket != null )   cmndSocket.close();
                if( serverSocket != null ) serverSocket.close();
                if(   dataSocket != null )   dataSocket.close();
                if( inDataStream != null ) inDataStream.close();
            }
            catch( Exception e ) {
            	Log.e( TAG, "", e );
            }
            outputStream = null;
             inputStream = null;
              cmndSocket = null;
            serverSocket = null;
              dataSocket = null;
            inDataStream = null;
        }
    }

    public final static int WAS_IN      =  1;
    public final static int LOGGED_IN   =  2;
    public final static int NO_CONNECT  = -1;
    public final static int NO_LOGIN    = -2;
    public final static int NO_WHERE    = -3;
    
    public synchronized final int connectAndLogin( Uri u, String user, String pass, boolean cwd ) 
                      throws UnknownHostException, IOException, InterruptedException {
        if( isLoggedIn() ) {
            if( cwd ) {
                String path = u.getPath();
                if( path != null )
                    setCurrentDir( path );
            }
            return WAS_IN;
        }
        int port = u.getPort();
        if( port == -1 ) port = 21;
        String host = u.getHost();
        if( connect( host, port ) ) {
            if( login( user, pass ) ) {
                if( cwd ) {
                    String path = u.getPath();
                    if( !Utils.str( path ) ) path = File.separator;
                    if( !setCurrentDir( path ) && !"..".equals( path ) ) {
                        if( !makeDir( path ) || !setCurrentDir( path ) )
                            return NO_WHERE;
                    }
                }
                return LOGGED_IN;
            }
            else {
                disconnect( false );
                Log.w( TAG, "Invalid credentials." );
                return NO_LOGIN;
            }
        }
        return NO_CONNECT;
    }
    
    
    public final void setActiveMode( boolean a ) {
        allowActive = a;
    }
    public final boolean getActiveMode() {
        return allowActive;
    }
    public void setCharset( String charset_ ) {
        try {
            charset = charset_ == null ? null : Charset.forName( charset_ );
        } catch( Exception e ) {
            Log.e( TAG, "invalid charset: " + charset_, e );
        }
    }
    private final synchronized boolean executeCommand( String command ) throws InterruptedException {
        sendCommand( command );
        return waitForPositiveResponse();
    }

    private boolean announcePort( ServerSocket serverSocket )
            throws IOException, InterruptedException {
        int localport = serverSocket.getLocalPort();
        // get local ip address in high byte order
        byte[] addrbytes = cmndSocket.getLocalAddress().getAddress();

        // tell server what port we are listening on
        short addrshorts[] = new short[4];

        // problem: bytes greater than 127 are printed as negative numbers
        for( int i = 0; i <= 3; i++ ) {
            addrshorts[i] = addrbytes[i];
            if( addrshorts[i] < 0 )
                addrshorts[i] += 256;
        }
        String port_command = "PORT " + addrshorts[0] + "," + addrshorts[1] + "," + addrshorts[2] + "," + addrshorts[3] + "," +
                ((localport & 0xff00) >> 8) + "," + (localport & 0x00ff);
        if( executeCommand( port_command ) )
            return true;
        Log.e( TAG, "Active mode failed" );
        return false;
    }
    private final int parsePassiveResponse( String s, byte[] addr ) {
        try {
        	if( s == null || s.length() < 4 )
        		return -1;
            if( !isPositiveComplete( Integer.parseInt( s.substring( 0, 3 ) ) ) )
                return -1;
            // responses could be:
            // 227 Entering Passive Mode (10,0,0,4,134,65)
            // 227 Entering Passive Mode. 10,0,0,4,134,65
            int opt = s.indexOf( '(' );
            int cpt = s.indexOf( ')' );
            if( cpt < opt )
                return -1;
            StringTokenizer addr_tokenizer;
            if( opt == -1 && cpt == -1 ) { // no parentheses 
            	String addr_str = s.replaceFirst( "\\d{3}\\s[^\\d]+", "" );
            	addr_tokenizer = new StringTokenizer( addr_str, "," );
            }
            else
            	addr_tokenizer = new StringTokenizer( s.substring( opt + 1, cpt ), "," );
            int a = 0, b = 0;
            for( int i = 0; i < 6; i++ ) {
                short n = Short.parseShort( addr_tokenizer.nextToken() );
                if( i < 4 )
                    addr[i] = (byte)n;
                else {
                    if( i == 4 )
                        a = n;
                    if( i == 5 )
                        b = n;
                }
                if( !addr_tokenizer.hasMoreTokens() )
                    break;
            }
            return a * 256 + b;
        }
        catch( RuntimeException  e ) {
            Log.e( TAG, "Exception while parsing the string '" + s + "'", e );
        }
        return -1;
    }

    private final synchronized Socket executeDataCommand( String commands ) {
    	try {
    	    if( commands == null || commands.length() == 0 ) return null;
    	    Socket data_socket = null;
            serverSocket = new ServerSocket( 0 );
            if( !allowActive || !announcePort( serverSocket ) ) {
                flushReply();   // emulator has a bug, it adds \n\r in the end of translated PORT
                serverSocket = null;
                // active mode failed. let's try passive
                final String pasv_command = "PASV";
                sendCommand( pasv_command );
                byte[] addr = new byte[4];
                int server_port = parsePassiveResponse( getReplyLine(), addr );
                if( server_port < 0 ) {
                    debugPrint( "Can't negotiate the PASV" );
                    return null;
                }
                data_socket = new Socket( InetAddress.getByAddress( addr ), server_port );
                if( !data_socket.isConnected() ) {
                    Log.e( TAG, "Can't open PASV data socket" );
                    return null;
                }
            }

            String[] cmds = commands.split( "\n" );
            for( int i = 0; i < cmds.length; i++ ) {
                sendCommand( cmds[i] );
                if( isNegative( getReplyCode( getReplyLine() ) ) ) {
                    Log.e( TAG, "Executing " + cmds[i] + " failed" );
                    return null;
                }
            }            

            if( data_socket == null && serverSocket != null ) {// active mode
                Log.i( TAG, "Awaiting the data connection to PORT" );
            	data_socket = serverSocket.accept(); // will block
            }

            if( data_socket == null || !data_socket.isConnected() ) {
                debugPrint( "Can't establish data connection for " + commands );
                return null;
            }
            return data_socket;
		} catch( Exception e ) {
		    Log.e( TAG, "Exception on executing data command '" + commands + "'", e );
		}
		return null;
    }
    private final boolean cleanUpDataCommand( boolean wait_reps ) throws InterruptedException {
    	
        // Clean up the data structures
        try {
	        if( dataSocket != null )
	        	dataSocket.close();
	        dataSocket = null;
	        if( serverSocket != null )
	            serverSocket.close();
		    serverSocket = null;
		} catch( IOException e ) {
		    Log.e( TAG, "", e );
		}
        return wait_reps ? waitForPositiveResponse() : true;
    }
    
    /*
     *    public methods
     */
    
    public final synchronized void clearLog() {
        debugBuf.setLength( 0 );
    }
    public final synchronized String getLog() {
        return debugBuf.toString();
    }

    public final boolean isLoggedIn() {
        if( cmndSocket == null || !cmndSocket.isConnected() )
        	loggedIn = false;
        return loggedIn;
    }
    
    public final synchronized boolean login( String username, String password ) throws IOException, InterruptedException {
        if( !executeCommand( "USER " + username ) )
            return false;
        loggedIn = executeCommand( "PASS " + password );
        return loggedIn;
    }

    public final boolean logout( boolean quit ) throws IOException, InterruptedException {
        boolean quit_res = quit ? executeCommand( "QUIT" ) : false;
        loggedIn = false; 
        return quit_res;
    }
    public final void heartBeat() throws InterruptedException {
    	executeCommand( "NOOP" );
    }
    public final synchronized boolean rename( String from, String to ) throws InterruptedException {
        if( !executeCommand( "RNFR " + from ) )
            return false;
        return executeCommand( "RNTO " + to );
    }
    public final synchronized boolean site( String cmd ) throws InterruptedException {
        return executeCommand( "SITE " + cmd );
    }
    public final synchronized OutputStream prepStore( String fn ) {
    	
    	dataSocket = null;
        try {
        	if( !isLoggedIn() )
        		return null;
        	executeCommand( "TYPE I" );
        	dataSocket = executeDataCommand( "STOR " + fn );
            if( dataSocket != null )
                return dataSocket.getOutputStream();
        }
        catch( Exception e ) {
            debugPrint( e.getLocalizedMessage() );
            Log.e( TAG, "", e );
        }
        return null;
    }
    public final boolean store( String fn, InputStream in, FTP.ProgressSink report_to ) 
            throws InterruptedException {
        try {
            OutputStream out = prepStore( fn );
            if( out == null ) {
                Log.e( TAG, "data socket does not give up the output stream to upload a file" );
                return false;
            }
            byte buf[] = new byte[BLOCK_SIZE];
            int  n = 0, last_n = 0;
        	while( true ) {
        		n = in.read( buf );
        		if( n < 0 ) break;
        		out.write( buf, 0, n );
        		last_n = n;
        		if( report_to != null )
        			if( !report_to.completed( n, false ) ) {
        			    out.close();
        			    Log.w( TAG, "Deleting incompleted file" + fn );
        				delete( fn );
        				return false;
        			}
        	}
        	if( report_to != null ) report_to.completed( last_n, true );
        	out.close();
        	return true;
        }
        catch( Exception e ) {
        	debugPrint( e.getLocalizedMessage() );
        	Log.e( TAG, "", e );
        }
        finally {
        	cleanUpDataCommand( dataSocket != null );
        }
        return false;
    }
    public final synchronized InputStream prepRetr( String fn, long skip ) throws InterruptedException {
    	dataSocket = null;
        try {
        	if( !isLoggedIn() )
        		return null;
        	executeCommand( "TYPE I" );
        	String retr_cmd = ( skip > 0 ? "REST " + skip + "\n" : "" ) + "RETR " + fn;
        	dataSocket = executeDataCommand( retr_cmd );
            if( dataSocket != null )
                return dataSocket.getInputStream();
        }
        catch( IOException e ) {
            debugPrint( e.getLocalizedMessage() );
            Log.e( TAG, "", e );
        }
        cleanUpDataCommand( dataSocket != null );
        return null;
    }
    public final boolean retrieve( String fn, OutputStream out, FTP.ProgressSink report_to ) throws InterruptedException {
        InputStream in = prepRetr( fn, 0 );
        if( in == null )
            return false;
        try {
            byte buf[] = new byte[BLOCK_SIZE];
            int  n = 0, last_n = 0;
        	while( true ) {
        		n = in.read( buf );
        		//Log.v( TAG, "FTP has read " + n + "bytes" );
        		if( n < 0 ) break;
        		out.write( buf, 0, n );
        		last_n = n;
        		if( report_to != null ) {
        			if( !report_to.completed( n, false ) ) {
        			    executeCommand( "ABOR" );
        				return false;
        			}
        		}
        	}
        	if( report_to != null ) report_to.completed( last_n, true );
        	return true;
        }
        catch( IOException e ) {
        	debugPrint( e.getLocalizedMessage() );
        	Log.e( TAG, "", e );
        }
        finally {
        	try {
				if( in  != null )  in.close();
				if( out != null ) out.close();
			} catch( IOException e ) {
			    Log.e( TAG, "Exception on streams closing (finnaly section)", e );
			}
        	cleanUpDataCommand( dataSocket != null );
        }
        return false;
    }
    public final boolean setCurrentDir( String dir ) throws InterruptedException {
        if( !Utils.str(  dir ) ) dir = "/";
    	return executeCommand( dir.compareTo( ".." ) == 0 ? "CDUP" : "CWD " + dir );
    }
    public final synchronized String getCurrentDir() {
    	sendCommand( "PWD" );
    	// MS IIS responds as: 257 "/" is current directory.
    	// all the others respond as: 257 "/" 
    	String pwd_answer = getReplyLine();
    	if( !isPositiveComplete( getReplyCode( pwd_answer ) ) )
    		return null;
    	String[] parts = pwd_answer.split( "\"" );
    	if( parts.length < 2 )
    		return null;
    	return parts[1];
    }
    public final boolean makeDir( String dir ) throws InterruptedException {
    	return executeCommand( "MKD " + dir );
    }
    public final boolean rmDir( String dir ) throws InterruptedException {
    	return executeCommand( "RMD " + dir );
    }
    public final boolean delete( String name ) throws InterruptedException {
    	return executeCommand( "DELE " + name );
    }
    
    public final LsItem[] getDirList( String path, boolean force_hidden ) throws InterruptedException {
    	if( !isLoggedIn() )
    		return null;        	
    	String cur_dir = null;
    	if( path != null && path.length() > 0 ) {
        	// some servers do not understand the LIST's parameter and always return the list of the current directory
    		cur_dir = getCurrentDir();
        	if( cur_dir == null )
        		return null;
        	setCurrentDir( path );
    	}
    	ArrayList<LsItem> array = null;
        try {
            dataSocket = executeDataCommand( "LIST" + ( force_hidden ? " -a" : "" ) );
            if( dataSocket == null )
            	return null;
            inDataStream = dataSocket.getInputStream();
            if( inDataStream == null ) {
                debugPrint( "data socket does not give up the input stream" );
                return null;
            }
            
            InputStreamReader isr = null; 
            if( charset != null )
                isr = new InputStreamReader( inDataStream, charset );
            if( isr == null )
                isr = new InputStreamReader( inDataStream );
            BufferedReader dataReader = new BufferedReader( isr, 4096 );
        	array = new ArrayList<LsItem>(); 
        	
            final String dot = ".";
            final String dotdot = "..";
        	while( true ) {
        		String dir_line = dataReader.readLine();
        		if( dir_line == null ) break;
        		LsItem item = new LsItem( dir_line );
        		String name = item.getName();
        		if( item.isValid() && !dotdot.equals( name ) && !dot.equals( name ) )
        			array.add( item );
        	}
        	inDataStream.close();
        	if( cur_dir != null )
        		setCurrentDir( cur_dir );
        }
        catch( Exception e ) {
        	debugPrint( e.getLocalizedMessage() );
        }
        finally {
        	cleanUpDataCommand( dataSocket != null );
        }
        if( array != null ) {
            LsItem[] result = new LsItem[array.size()]; 
	        return array.toArray( result );
        }
        return null;
    }
}
