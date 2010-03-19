package com.ghostsq.commander;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class FTP {
	
	interface ProgressSink {
		public boolean completed( long size );
	};
	
    private final static int BLOCK_SIZE = 100000;
    private final static boolean PRINT_DEBUG_INFO = true;
    private String debugBuf = "";
    private Socket cmndSocket = null;
    private OutputStream outputStream = null;
    private BufferedInputStream inputStream = null;
    private ServerSocket serverSocket = null;
    private Socket dataSocket = null;
    private InputStream inDataStream = null;
    private boolean loggedIn = false;
    private boolean allowActive = false;
    
    private final boolean sendCommand( String cmd ) {
        try {
        	if( outputStream == null || cmndSocket == null || !cmndSocket.isConnected() )
        		return false;
        	debugPrint( ">>> " + cmd );
//System.err.print( "\nto FTP:" + cmd + "\n" );
            byte[] bytes = cmd.getBytes();
            outputStream.write( bytes );
            outputStream.write( '\n' );
            return true;
        }
        catch( IOException e ) {
            debugPrint( "connection broken" );
        }
        return false;
    }

    public final void debugPrint( String message ) {
        if( PRINT_DEBUG_INFO )
            debugBuf += message + "\n";
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
    private final boolean waitForPositiveResponse() {
        String response = null;
        try {
        	int code;
            do {
            	response = getReplyLine();
            	if( response == null ) return false;
                code = getReplyCode( response );
                if( isPositiveComplete( code ) )
                    return true;
                if( isPositiveIntermediate( code ) )
                    return true; // when this occurred?
                Thread.sleep( 50 );
            } while( isPositivePreliminary( code ) );
		} catch( InterruptedException e ) {
            System.err.print( "Exception: " + e + ( response == null ? "" : (" on response '" + response + "'") + "\n" ) );
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
            System.err.print( "Exception: " + e + " on flushReply()\n" );
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
            		if( cnt++ < 200 ) Thread.sleep( 10 ); else return null;
            	while( inputStream.available() == 0 );
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
//System.err.print( "\nfrom FTP:" + new String( buf, 0, i ) + "\n" );
            } while( !(Character.isDigit( buf[0] ) &&
                       Character.isDigit( buf[1] ) &&
                       Character.isDigit( buf[2] ) && buf[3] == ' ' ) ); // read until a coded response be found
            String reply = new String( buf, 0, i );
            debugPrint( "<<< " + reply );
//            System.err.print( "Accepted from server: '" + reply + "'\n" );
            return reply;
        }
        catch( Exception e ) {
            System.err.print( "Exception: " + e + " in getReplyLine()\n" );
            disconnect();
            return null;
		}
    }
    public final boolean connect( String host, int port ) throws UnknownHostException, IOException {
        cmndSocket = new Socket( host, port );
        outputStream = cmndSocket.getOutputStream();
        inputStream = new BufferedInputStream( cmndSocket.getInputStream(), 256 );

        if( !waitForPositiveResponse() ) {
            disconnect();
            return false;
        }

        return true;
    }
    public final void disconnect() {
        if( outputStream != null ) {
            try {
                if( loggedIn )
                    logout( true );
                if( outputStream != null ) outputStream.close();
                if(  inputStream != null )  inputStream.close();
                if(   cmndSocket != null )   cmndSocket.close();
                if( serverSocket != null ) serverSocket.close();
                if(   dataSocket != null )   dataSocket.close();
                if( inDataStream != null ) inDataStream.close();
            }
            catch( Exception e ) {
            	e.printStackTrace();
            }
            outputStream = null;
             inputStream = null;
              cmndSocket = null;
            serverSocket = null;
              dataSocket = null;
            inDataStream = null;
        }
    }
    public void setActiveMode( boolean a ) {
        allowActive = a;
    }
    private final boolean executeCommand( String command ) {
        sendCommand( command );
        return waitForPositiveResponse();
    }

    private boolean announcePort( ServerSocket serverSocket )
            throws IOException {
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
        return executeCommand( port_command );
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
        catch( Exception e ) {
            System.err.print( "Exception: " + e + " while parsing the string '" + s + "'\n" );
        }
        return -1;
    }

    private final Socket executeDataCommand( String command ) {
    	try {
    	    Socket data_socket = null;
            serverSocket = new ServerSocket( 0 );
            /* 
            	Emulator just ceased to do the PORT translation.
            */ 
            if( !allowActive || !announcePort( serverSocket ) ) {
                flushReply();   // emulator has a bug, it adds \n\r in the end of translated PORT
                serverSocket = null;
                // active mode failed. let's try passive
                final String pasv_command = "PASV";
                sendCommand( pasv_command );
                byte[] addr = new byte[4];
                int server_port = parsePassiveResponse( getReplyLine(), addr );
                if( server_port < 0 ) {
                    debugPrint( "Passive mode failed" );
                    return null;
                }
                data_socket = new Socket( InetAddress.getByAddress( addr ), server_port );
            }

            /*
              // Set binary type transfer final String bin_command = "TYPE I";
              sendCommand( bin_command ); debugPrint( ">>> " + bin_command ); if(
              !isPositiveCompleteResponse( getServerReply() ) ) { debugPrint(
              "Could not set transfer type" ); return false; }
              
              // If we have a restart point, send that information if( restartPoint
              != 0 ) { sendCommand( "REST " + restartPoint ); restartPoint = 0; //
              TODO: Interpret server response here getServerReply(); }
             */

            sendCommand( command );
            if( data_socket == null && serverSocket != null ) // active mode
            	data_socket = serverSocket.accept(); // will block

            if( data_socket == null || !data_socket.isConnected() ) {
                debugPrint( "Unable to establish data connection for " + command );
                return null;
            }
            return data_socket;
		} catch( Exception e ) {
		    System.err.print( "Exception: " + e + " on executing data command '" + command + "'\n" );
		}
		return null;
    }
    private final boolean cleanUpDataCommand() {
    	
        // Clean up the data structures
        try {
	        if( dataSocket != null )
	        	dataSocket.close();
	        dataSocket = null;
	        if( serverSocket != null )
	            serverSocket.close();
		    serverSocket = null;
		} catch( IOException e ) {
		    System.err.print( "Exception: " + e + " in cleanUpDataCommand()\n" );
		}
        return waitForPositiveResponse();
    }
    
    /*
     *    public methods
     */
    
    public final void clearLog() {
        debugBuf = "";
    }
    public final String getLog() {
        return debugBuf;
    }

    public final boolean isLoggedIn() {
        if( cmndSocket == null || !cmndSocket.isConnected() )
        	loggedIn = false;
        return loggedIn;
    }
    
    public final boolean login( String username, String password ) throws IOException {
        if( !executeCommand( "USER " + username ) )
            return false;
        loggedIn = executeCommand( "PASS " + password );
        return loggedIn;
    }

    public final boolean logout( boolean quit ) throws IOException {
        boolean quit_res = quit ? executeCommand( "QUIT" ) : false;
        loggedIn = false; 
        return quit_res;
    }
    public final void heartBeat() {
    	executeCommand( "NOOP" );
    }
    public final boolean rename( String from, String to ) {
    	if( !executeCommand( "RNFR " + from ) )
    		return false;
    	return executeCommand( "RNTO " + to );
    }
    public final boolean store( String fn, InputStream in, FTP.ProgressSink report_to ) {
    	
    	dataSocket = null;
        try {
        	if( !isLoggedIn() )
        		return false;
        	executeCommand( "TYPE I" );
        	dataSocket = executeDataCommand( "STOR " + fn );
            if( dataSocket == null )
            	return false;
            OutputStream out = dataSocket.getOutputStream();
            if( out == null ) {
                debugPrint( "data socket does not give up the output stream to upload a file" );
                return false;
            }
            byte buf[] = new byte[BLOCK_SIZE];
            int  n = 0;
            long done = 0;
        	while( true ) {
        		n = in.read( buf );
        		if( n < 0 ) break;
        		out.write( buf, 0, n );
        		if( report_to != null )
        			if( !report_to.completed( done += n ) ) {
        				delete( fn );
        				return false;
        			}
        	}
        	out.close();
        	return true;
        }
        catch( Exception e ) {
        	debugPrint( "Exception: " + e );
        	System.err.print( "Exception: " + e + " while storing the file '" + fn + "'\n" );
        }
        finally {
        	cleanUpDataCommand();
        }
        return false;
    }
    public final boolean retrieve( String fn, OutputStream out, FTP.ProgressSink report_to ) {
    	
    	dataSocket = null;
    	InputStream in = null;
        try {
        	if( !isLoggedIn() )
        		return false;
        	executeCommand( "TYPE I" );
        	dataSocket = executeDataCommand( "RETR " + fn );
            if( dataSocket == null )
            	return false;
            in = dataSocket.getInputStream();
            if( in == null ) {
                debugPrint( "data socket does not give up the input stream to download a file" );
                return false;
            }
            byte buf[] = new byte[BLOCK_SIZE];
            int  n = 0;
            long done = 0;
        	while( true ) {
        		n = in.read( buf );
        		if( n < 0 ) break;
        		out.write( buf, 0, n );
        		if( report_to != null )
        			if( !report_to.completed( done += n ) )
        				return false;
        	}
        	return true;
        }
        catch( Exception e ) {
        	debugPrint( "Exception: " + e );
        	System.err.print( "Exception: " + e + " while retrieving the file '" + fn + "'\n" );
        }
        finally {
        	try {
				if( in  != null )  in.close();
				if( out != null ) out.close();
			} catch( IOException e ) {
			    System.err.print( "Exception: " + e + " on streams closing (finnaly section)\n" );
			}
        	cleanUpDataCommand();
        }
        return false;
    }
    public final void setCurrentDir( String dir ) {
    	executeCommand( dir.compareTo( ".." ) == 0 ? "CDUP" : "CWD " + dir );
    }
    public final String getCurrentDir() {
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
    public final boolean makeDir( String dir ) {
    	return executeCommand( "MKD " + dir );
    }
    public final boolean rmDir( String dir ) {
    	return executeCommand( "RMD " + dir );
    }
    public final boolean delete( String name ) {
    	return executeCommand( "DELE " + name );
    }
    
    public final FTPItem[] getDirList( String path ) {
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
    	ArrayList<FTPItem> array = null;
        try {
            dataSocket = executeDataCommand( "LIST" );
            if( dataSocket == null )
            	return null;
            inDataStream = dataSocket.getInputStream();
            if( inDataStream == null ) {
                debugPrint( "data socket does not give up the input stream" );
                return null;
            }
            BufferedReader dataReader = new BufferedReader( new InputStreamReader( inDataStream ), 4096 );
        	array = new ArrayList<FTPItem>(); 

        	while( true ) {
        		String dir_line = dataReader.readLine();
        		if( dir_line == null ) break;
        		FTPItem item = new FTPItem( dir_line );
        		if( item.isValid() )
        			array.add( item );
        	}
        	inDataStream.close();
        	if( cur_dir != null )
        		setCurrentDir( cur_dir );
        }
        catch( Exception e ) {
        	debugPrint( "Exception: " + e );
        	System.err.print( "Exception: " + e + " while processing the directory list '" + path + "'\n" );
        }
        finally {
        	cleanUpDataCommand();
        }
        if( array != null ) {
	        FTPItem[] result = new FTPItem[array.size()]; 
	        return array.toArray( result );
        }
        return null;
    }
}
