package com.ghostsq.commander.adapters;

import java.lang.System;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.utils.LsItem.LsItemPropComparator;
import com.ghostsq.commander.utils.FTP;
import com.ghostsq.commander.utils.LsItem;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;

public class FTPAdapter extends CommanderAdapterBase {
    private final static String TAG = "FTPAdapter";
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  FTP ftp;
    public  Uri uri = null;
    public  LsItem[] items = null;
    private Timer  heartBeat;
    public  FTPCredentials theUserPass = null;
    
    public FTPAdapter( Context ctx_ ) {
        super( ctx_ );
        ftp = new FTP();
        try {
            heartBeat = new Timer( "FTP Heartbeat", true );
            heartBeat.schedule( new Noop(), 120000, 20000 );
		} 
        catch( Exception e ) {
		}
    }
    @Override
    public int getType() {
        return CA.FTP;
    }
    class Noop extends TimerTask {
		@Override
		public void run() {
			if( reader == null && worker == null && ftp.isLoggedIn() )
				synchronized( ftp ) {
					try {
                        ftp.heartBeat();
                    } catch( InterruptedException e ) {
                        e.printStackTrace();
                    }
				}
		}
    }

    @Override
    public void setIdentities( String name, String pass ) {
        theUserPass = new FTPCredentials( name, pass );
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            boolean need_reconnect = false;
            if( tmp_uri != null ) { 
                String new_user_info = tmp_uri.getUserInfo();
                if( uri == null ) 
                    need_reconnect = true;
                else if( !tmp_uri.getHost().equalsIgnoreCase( uri.getHost() ) ) {
                    need_reconnect = true;
                    theUserPass = null;
                }
                else if( new_user_info != null  ) {
                    if( theUserPass == null )
                        need_reconnect = true;
                    else if( theUserPass != null && !theUserPass.equals( new FTPCredentials( new_user_info ) ) )
                        need_reconnect = true;
                }
                else
                    if( theUserPass != null )
                        need_reconnect = theUserPass.dirty; 
                if( uri != null ) 
                    synchronized( uri ) {
                        uri = tmp_uri;
                    }
                else
                    uri = tmp_uri;
            }
            else
                if( uri == null )
                    return false;
            if( reader != null ) { // that's not good.
                Log.w( TAG, "reader's existed!" );
                if( reader.isAlive() ) {
                    Log.e( TAG, "reader's busy!" );
                        return false;      
                }
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            Log.v( TAG, "Creating and starting the reader..." );
            reader = new ListEngine( readerHandler, need_reconnect, pass_back_on_done );
            reader.start();
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        commander.notifyMe( new Commander.Notify( ftp.getLog(), Commander.OPERATION_FAILED ) );
        return false;
    }
 
    public final static int WAS_IN = 1;
    public final static int LOGGED_IN = 2;
    public final static int NO_CONNECT = -1;
    public final static int NO_LOGIN = -2;
    
    public final int connectAndLogin() 
                      throws UnknownHostException, IOException, InterruptedException {
        if( ftp.isLoggedIn() ) {
            String path = uri.getPath();
            if( path != null )
                ftp.setCurrentDir( path );
            return WAS_IN;
        }
        int port = uri.getPort();
        if( port == -1 ) port = 21;
        String host = uri.getHost();
        if( ftp.connect( host, port ) ) {
            if( theUserPass == null || theUserPass.isNotSet() )
                theUserPass = new FTPCredentials( uri.getUserInfo() );
            if( ftp.login( theUserPass.getUserName(), theUserPass.getPassword() ) ) {
                String path = uri.getPath();
                if( path != null )
                    ftp.setCurrentDir( path );
                return LOGGED_IN;
            }
            else {
                ftp.logout( true );
                ftp.disconnect();
                Log.w( TAG, "Invalid credentials." );
                return NO_LOGIN;
            }
        }
        return NO_CONNECT;
    }
    
    
    class ListEngine extends Engine {
        private boolean needReconnect;
        private LsItem[] items_tmp;
        public  String pass_back_on_done;
        ListEngine( Handler h, boolean need_reconnect_, String pass_back_on_done_ ) {
        	super( h );
        	needReconnect = need_reconnect_;
        	pass_back_on_done = pass_back_on_done_;
        }
        public LsItem[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
            	if( uri == null ) {
            		sendProgress( "Wrong URI", Commander.OPERATION_FAILED );
            		return;
            	}
            	Log.i( TAG, "ListEngine started" );
            	synchronized( ftp ) {
                    threadStartedAt = System.currentTimeMillis();
	                ftp.clearLog();
	                if( needReconnect  && ftp.isLoggedIn() ) {
	                    ftp.disconnect();
	                }
	                
	                int cl_res = connectAndLogin();
                    if( cl_res < 0 ) {
                        if( cl_res == NO_LOGIN ) 
                            sendProgress( uri.toString(), Commander.OPERATION_FAILED_LOGIN_REQUIRED );
                        return;
                    }
	                if( cl_res == LOGGED_IN )
	                    sendProgress( ctx.getString( R.string.ftp_connected,  
	                            uri.getHost(), theUserPass.getUserName() ), Commander.OPERATION_STARTED );

	                if( ftp.isLoggedIn() ) {
	                    //Log.v( TAG, "ftp is logged in" );
	                	try { // Uri.builder builds incorrect uri?
		                	String active = uri.getQueryParameter( "a" );
		                	ftp.setActiveMode( active != null && 
		                	                 ( 0 == active.compareTo("1")
	                 	                    || 0 == active.compareToIgnoreCase( "true" )  
	                 	                    || 0 == active.compareToIgnoreCase( "yes" ) ) );  
	                	}
	                	catch( Exception e ) {
	                	    Log.e( TAG, "Exception on setActiveMode()", e );
	                	}
                    	items_tmp = ftp.getDirList( null );
                    	String path = ftp.getCurrentDir();
	                    if( path != null ) 
	                    	synchronized( uri ) {
	                    		uri = uri.buildUpon().encodedPath( path ).build();
							}
	                    if( items_tmp != null  ) {
	                        //Log.v( TAG, "Got the items list" );
	                        if( items_tmp.length > 0 ) {
    	                        LsItem.LsItemPropComparator comp = 
    	                            items_tmp[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
                                Arrays.sort( items_tmp, comp );
	                        }
	                        parentLink = path == null || path.length() == 0 || path.equals( SLS ) ? SLS : PLS;
	                        //Log.v( TAG, "items list sorted" );
	                        sendProgress( tooLong( 8 ) ? ftp.getLog() : null, Commander.OPERATION_COMPLETED, pass_back_on_done );
	                        return;
	                    }
	                    else
	                        Log.e( TAG, "Can't get the items list" );
	                }
	                else
	                    Log.e( TAG, "Did not log in." );
            	}
            }
            catch( UnknownHostException e ) {
                ftp.debugPrint( "Unknown host:\n" + e.getMessage() );
            }
            catch( IOException e ) {
                ftp.debugPrint( "IO exception:\n" + e.getMessage() );
                e.printStackTrace();
            }
            catch( Exception e ) {
                ftp.debugPrint( "Exception:\n" + e );
                e.printStackTrace();
            }
            finally {
            	super.run();
            }
            ftp.disconnect();
            sendProgress( ftp.getLog(), Commander.OPERATION_FAILED, pass_back_on_done );
        }
    }
    @Override
    protected void onReadComplete() {
        Log.v( TAG, "UI thread finishes the items obtaining. reader=" + reader );
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            items = null;
            if( ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                LsItem[] tmp_items = list_engine.getItems();
                if( tmp_items != null ) {
                    int cnt = 0;
                    for( int i = 0; i < tmp_items.length; i++ )
                        if( tmp_items[i].getName().charAt( 0 ) != '.' )
                            cnt++;
                    items = new LsItem[cnt];
                    int j = 0;
                    for( int i = 0; i < tmp_items.length; i++ )
                        if( tmp_items[i].getName().charAt( 0 ) != '.' )
                            items[j++] = tmp_items[i];
                }
            }
            else
                items = list_engine.getItems();
            numItems = items != null ? items.length + 1 : 1;
            notifyDataSetChanged();
            if( theUserPass != null )
                theUserPass.dirty = false; 
        }
    }
    
    @Override
    public String toString() {
        Uri u = getUri();
        return u != null ? u.toString() : "";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        if( theUserPass != null && !theUserPass.isNotSet() )
            return Favorite.getUriWithAuth( uri, theUserPass.getUserName(), theUserPass.getPassword() );
        else
            return uri;
    }
    @Override
    public void setUri( Uri uri_ ) {
        uri = uri_;
    }

	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
		commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
	}
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        String err_msg = null;
        try {
            LsItem[] subItems = bitsToItems( cis );
            if( subItems == null ) {
                commander.notifyMe( new Commander.Notify( s( R.string.copy_err ), Commander.OPERATION_FAILED ) );
                return false;
            } 
            if( !checkReadyness() ) return false;
            File dest = null;
            int rec_h = 0;
            if( to instanceof FSAdapter  ) {
                dest = new File( to.toString() );
                if( !dest.exists() ) dest.mkdirs();
                if( !dest.isDirectory() )
                    throw new RuntimeException( s( R.string.dest_exist ) );
            } else {
                dest = new File( createTempDir() );
                rec_h = setRecipient( to ); 
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyFromEngine( workerHandler, subItems, dest, move, rec_h );
            worker.start();
            return true;
        }
        catch( Exception e ) {
            err_msg = "Exception: " + e.getMessage();
        }
        commander.notifyMe( new Commander.Notify( err_msg, Commander.OPERATION_FAILED ) );
        return false;
    }

    class CopyEngine extends Engine implements FTP.ProgressSink 
    {
        private long    curFileLen = 0;
        String progressMessage = null;
    	CopyEngine( Handler h ) {
    		super( h );
    	}
    	protected void setCurFileLength( long len ) {
    		curFileLen = len;
    	}
		@Override
		public boolean completed( long size ) throws InterruptedException {
			if( curFileLen > 0 )
				sendProgress( progressMessage, (int)(size * 100 / curFileLen) );
			//Log.v( TAG, progressMessage + " " + size );
    		if( isStopReq() ) {
    			error( ctx.getString( R.string.canceled ) );
    			return false;
    		}
            Thread.sleep( 1 );
    		return true;
		}
    }
    
  class CopyFromEngine extends CopyEngine 
  {
	    private LsItem[] mList;
	    private File     dest_folder;
	    private boolean  move;
	    private int      recipient_hash;
	    CopyFromEngine( Handler h, LsItem[] list, File dest, boolean move_, int rec_h ) {
	    	super( h );
	        mList = list;
	        dest_folder = dest;
	        move = move_;
	        recipient_hash = rec_h;
	    }
	    @Override
	    public void run() {
	    	try {
                int total = copyFiles( mList, "" );
                if( recipient_hash != 0 ) {
                      sendReceiveReq( recipient_hash, dest_folder );
                      return;
                }
                sendResult( Utils.getOpReport( ctx, total, R.string.downloaded ) );
            } catch( InterruptedException e ) {
                sendResult( ctx.getString( R.string.canceled ) );
            }
	        super.run();
	    }
	
	    private final int copyFiles( LsItem[] list, String path ) throws InterruptedException {
	        int counter = 0;
	        try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        		    error( ctx.getString( R.string.interrupted ) );
	        			break;
	        		}
	        		LsItem f = list[i];
	        		if( f != null ) {
                        String pathName = path + f.getName();
	        			File dest = new File( dest_folder, pathName );
	        			if( f.isDirectory() ) {
	        				if( !dest.mkdir() ) {
	        					if( !dest.exists() || !dest.isDirectory() ) {
		        					errMsg = "Can't create folder \"" + dest.getCanonicalPath() + "\"";
		        					break;
	        					}
	        				}
		                    LsItem[] subItems = ftp.getDirList( pathName );
		                    if( subItems == null ) {
		                    	errMsg = "Failed to get the file list of the subfolder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
		                    	break;
		                    }
	        				counter += copyFiles( subItems, pathName + SLS );
	        				if( errMsg != null ) break;
                            if( move && !ftp.rmDir( pathName ) ) {
                                errMsg = "Failed to remove folder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
                                break;
                            }
	        			}
	        			else {
	        				if( dest.exists()  ) {
                                int res = askOnFileExist( ctx.getString( R.string.file_exist, dest.getAbsolutePath() ), commander );
                                if( res == Commander.ABORT ) break;
                                if( res == Commander.SKIP )  continue;
                                if( res == Commander.REPLACE ) {
                                    if( !dest.delete() ) {
                                        error( ctx.getString( R.string.cant_del, dest.getAbsoluteFile() ) );
                                        break;
                                    }
                                }
	        				}
	        				progressMessage = ctx.getString( R.string.retrieving, pathName ); 
	        				sendProgress( progressMessage, 0 );
	        				setCurFileLength( f.length() );
	        				FileOutputStream out = new FileOutputStream( dest );
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		if( !ftp.retrieve( pathName, out, this ) ) {
		        					error( "Can't download file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
		        					dest.delete();
		        					break;
	        	        		}
	        	        		else if( move ) {
                                    if( !ftp.delete( pathName ) ) {
                                        error( "Can't delete file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
                                        break;
                                    }
        	        		    }
	        	        	}
	        	        	progressMessage = "";
	        			}
	        			Date ftp_file_date = f.getDate();
	        			if( ftp_file_date != null )
	        			    dest.setLastModified( ftp_file_date.getTime() );
	        			counter++;
	        		}
	        	}
	    	}
            catch( RuntimeException e ) {
                e.printStackTrace();
                error( "Runtime Exception: " + e.getMessage() );
            }
            catch( IOException e ) {
                e.printStackTrace();
                error( "Input-Output Exception: " + e.getMessage() );
            }
	        return counter;
	    }
	}
	    
	@Override
	public boolean createFile( String fileURI ) {
		commander.notifyMe( new Commander.Notify( "Operation not supported on a FTP folder.", 
		                        Commander.OPERATION_FAILED ) );
		return false;
	}
    @Override
    public void createFolder( String string ) {
    	synchronized( ftp ) {
    		ftp.clearLog();
    		try {
                if( ftp.makeDir( string ) )
                    commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED ) );
            } catch( InterruptedException e ) {
                e.printStackTrace();
            }
		}
    	commander.notifyMe( new Commander.Notify( "Unable to create directory '" + string + "': " + ftp.getLog(), Commander.OPERATION_FAILED ) );            
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	if( !checkReadyness() ) return false;
        	LsItem[] subItems = bitsToItems( cis );
        	if( subItems != null ) {
        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                worker = new DelEngine( workerHandler, subItems );
                worker.start();
	            return true;
        	}
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }

    class DelEngine extends Engine {
        LsItem[] mList;
        
        DelEngine( Handler h, LsItem[] list ) {
        	super( h );
            mList = list;
        }
        @Override
        public void run() {
        	int total = delFiles( mList, "" );
        	sendResult( Utils.getOpReport( ctx, total, R.string.deleted ) );
            super.run();
        }
        private final int delFiles( LsItem[] list, String path ) {
            int counter = 0;
            try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        		    error( ctx.getString( R.string.interrupted ) );
	        			break;
	        		}
	        		LsItem f = list[i];
	        		if( f != null ) {
	        			String pathName = path + f.getName();
	        			if( f.isDirectory() ) {
		                    LsItem[] subItems = ftp.getDirList( pathName );
	        				counter += delFiles( subItems, pathName + SLS );
	        				if( errMsg != null ) break;
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		if( !ftp.rmDir( pathName ) ) {
		        					error( "Failed to remove folder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
		        					break;
	        	        		}
	        	        	}
	        			}
	        			else {
	        				sendProgress( ctx.getString( R.string.deleting, pathName ), i * 100 / list.length );
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		if( !ftp.delete( pathName ) ) {
	        	        		    error( "Failed to delete file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
		        					break;
	        	        		}
	        	        	}
	        			}
	        			counter++;
	        		}
	        	}
        	}
			catch( Exception e ) {
				Log.e( TAG, "delFiles()", e );
				error( "Exception: " + e.getMessage() );
			}
            return counter;
        }
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                String path = toString();
                if( path != null && path.length() > 0 ) {
                    if( path.charAt( path.length() - 1 ) != SLC )
                        path += SLS;
                    return path + items[position-1].getName();
                }
            }
            return items[position-1].getName();
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri != null && parentLink != SLS ) {
            	String path = uri.getPath();
                int len_ = path.length()-1;
                if( len_ > 0 ) {
	                if( path.charAt( len_ ) == SLC )
	                	path = path.substring( 0, len_ );
	                path = path.substring( 0, path.lastIndexOf( SLC ) );
	                if( path.length() == 0 )
	                	path = SLS;
	                commander.Navigate( uri.buildUpon().path( path ).build(), uri.getLastPathSegment() );
                }
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        LsItem item = items[position - 1];
        
        if( item.isDirectory() ) {
        	String cur = uri.getPath();
            if( cur == null || cur.length() == 0 ) 
                cur = SLS;
            else
            	if( cur.charAt( cur.length()-1 ) != SLC )
            		cur += SLS;
            commander.Navigate( uri.buildUpon().appendEncodedPath( item.getName() ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
    		if( connectAndLogin() < 0 ) {
    		    commander.notifyMe( new Commander.Notify( s( R.string.ftp_nologin ), Commander.OPERATION_FAILED ) );
    		    return false;
    		}
            if( uris == null || uris.length == 0 ) {
            	commander.notifyMe( new Commander.Notify( s( R.string.copy_err ), Commander.OPERATION_FAILED ) );
            	return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null ) {
            	commander.notifyMe( new Commander.Notify( "Something wrong with the files", Commander.OPERATION_FAILED ) );
            	return false;
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyToEngine( workerHandler, list, move_mode );
            worker.start();
            return true;
		} catch( Exception e ) {
			commander.notifyMe( new Commander.Notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED ) );
		}
		return false;
    }
    
    class CopyToEngine extends CopyEngine {
        private   File[]  mList;
        private   int     basePathLen;
        private   boolean move = false;
        private   boolean del_src_dir = false;
        
        CopyToEngine( Handler h, File[] list, int move_mode_ ) {
        	super( h );
            mList = list;
            basePathLen = list[0].getParent().length();
            if( basePathLen > 1 ) basePathLen++;
            move = ( move_mode_ & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode_ & CommanderAdapter.MODE_DEL_SRC_DIR ) != 0;
        }

        @Override
        public void run() {
            try {
                int total = copyFiles( mList );
                if( del_src_dir ) {
                    File src_dir = mList[0].getParentFile();
                    if( src_dir != null )
                        src_dir.delete();
                }
                sendResult( Utils.getOpReport( ctx, total, R.string.uploaded ) );
                return;
            } catch( Exception e ) {
                error( "Exception: " + e.getMessage() );
            }
            finally {            
                super.run();
            }
            sendResult( "" );
        }
        private final int copyFiles( File[] list ) throws InterruptedException {
            if( list == null ) return 0;
            int counter = 0;
            try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        			error( ctx.getString( R.string.interrupted ) );
	        			break;
	        		}
	        		File f = list[i];
	        		if( f != null && f.exists() ) {
	        			if( f.isFile() ) {
	        				sendProgress( ctx.getString( R.string.uploading, f.getName() ), 0 );
	        				String fn = f.getAbsolutePath().substring( basePathLen );
	    					FileInputStream in = new FileInputStream( f );
	    					setCurFileLength( f.length() );
	    		        	synchronized( ftp ) {
	    		        		ftp.clearLog();
		    					if( !ftp.store( fn, in, this ) ) {
		    						error( ctx.getString( R.string.ftp_upload_failed, f.getName(), ftp.getLog() ) );
		    						break;
		    					}
	    		        	}
	        			}
	        			else
	        			if( f.isDirectory() ) {
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		String toCreate = f.getAbsolutePath().substring( basePathLen );
		        				if( !ftp.makeDir( toCreate ) ) {
		        				    error( ctx.getString( R.string.ftp_mkdir_failed, toCreate, ftp.getLog() ) );
		        					break;
		        				}
	        	        	}
	        				counter += copyFiles( f.listFiles() );
	        				if( errMsg != null ) break;
	        			}
    					counter++;
                        if( move && !f.delete() ) {
                            error( ctx.getString( R.string.cant_del, f.getCanonicalPath() ) );
                            break;
                        }
	        		}
	        	}
        	}
			catch( IOException e ) {
				e.printStackTrace();
				error( "IOException: " + e.getMessage() );
			}
            return counter;
        }
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean copy  ) {
        try {
            if( items == null || position <= 0 || position > items.length )
                return false;
            if( ftp != null  && ftp.isLoggedIn() ) {
            	boolean ok;
            	
            	if( copy) {
            	    commander.notifyMe( new Commander.Notify( s( R.string.not_supported ), Commander.OPERATION_FAILED ) );
            	    return false;
            	}
            	
                synchronized( ftp ) {
                    ok = ftp.rename( getItemName( position, false ), newName );
                }
                commander.notifyMe( new Commander.Notify( null, 
                    ok ? Commander.OPERATION_COMPLETED_REFRESH_REQUIRED : Commander.OPERATION_FAILED ) );
                return ok;
            }
        } catch( InterruptedException e ) {
            e.printStackTrace();
        }
        return false;
    }

	@Override
	public void prepareToDestroy() {
		heartBeat.cancel();
		heartBeat.purge();
        super.prepareToDestroy();
		ftp.disconnect();
		items = null;
	}

    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "???";
        {
            if( position == 0 ) {
                item.name = parentLink;
            }
            else {
                if( items != null && position > 0 && position <= items.length ) {
                    LsItem curItem;
                    curItem = items[position - 1];
                    item.dir = curItem.isDirectory();
                    item.name = item.dir ? SLS + curItem.getName() : curItem.getName();
                    item.size = !item.dir || curItem.length() > 0 ? curItem.length() : -1;
                    item.date = curItem.getDate();
                }
            }
        }
        return item;
    }

    private final LsItem[] bitsToItems( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            LsItem[] subItems = new LsItem[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                	subItems[j++] = items[ cis.keyAt( i ) - 1 ];
            return subItems;
		} catch( Exception e ) {
		    Log.e( TAG, "", e );
		}
		return null;
    }
    private final boolean checkReadyness()   
    {
        if( worker != null ) {
        	commander.notifyMe( new Commander.Notify( "ftp adapter is busy!", Commander.OPERATION_FAILED ) );
        	return false;
        }
        if( !ftp.isLoggedIn() ) {
        	commander.notifyMe( new Commander.Notify( s( R.string.ftp_nologin ), Commander.OPERATION_FAILED ) );
        	return false;
        }
    	return true;
    }

    public class FTPCredentials extends org.apache.http.auth.UsernamePasswordCredentials {
        public boolean dirty = true;
        public FTPCredentials( String userName, String password ) {
            super( userName, password );
        }
        public FTPCredentials( String newUserInfo ) {
            super( newUserInfo == null ? ":" : newUserInfo );
        }
        public String getUserName() {
            String u = super.getUserName();
            return u == null || u.length() == 0 ? "anonymous" : u;
        }
        public String getPassword() {
            String u = super.getUserName();
            return u == null || u.length() == 0 ? "user@host.com" : super.getPassword();
        }
        public final boolean isNotSet() {
            String u = super.getUserName();
            if( u == null || u.length() == 0 ) return true;
            String p = super.getPassword();
            if( p == null ) return true;
            return false;
        }
    }
    @Override
    protected void reSort() {
        if( items == null || items.length < 1 ) return;
        LsItemPropComparator comp = items[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items, comp );
    }
}
