package com.ghostsq.commander;

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
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;

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
    
    public FTPAdapter( Commander c ) {
        super( c, 0 );
        ftp = new FTP();
        try {
            heartBeat = new Timer( "FTP Heartbeat", true );
            heartBeat.schedule( new Noop(), 100000, 120000 );
		} catch( Exception e ) {
		}
    }
    @Override
    public String getType() {
        return "ftp";
    }
    class Noop extends TimerTask {
		@Override
		public void run() {
			if( worker == null && ftp.isLoggedIn() )
				synchronized( ftp ) {
					ftp.heartBeat();
				}
		}
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
            	synchronized( ftp ) { // the Heartbeat thread could take ftp
                    threadStartedAt = System.currentTimeMillis();
	                ftp.clearLog();
	                if( needReconnect  && ftp.isLoggedIn() ) {
	                    ftp.disconnect();
	                }
	                if( !ftp.isLoggedIn() ) {
	                	int port = uri.getPort();
	                	if( port == -1 ) port = 21;
	                	String host = uri.getHost();
	                    if( ftp.connect( host, port ) ) {
	                        if( theUserPass == null || theUserPass.isNotSet() )
	                            theUserPass = new FTPCredentials( uri.getUserInfo() );
	                        if( ftp.login( theUserPass.getUserName(), theUserPass.getPassword() ) )
	                            sendProgress( commander.getContext().getString( R.string.ftp_connected,  host, theUserPass.getUserName() ), 3 );
	                        else {
	                            sendProgress( uri.toString(), Commander.OPERATION_FAILED_LOGIN_REQUIRED );
	                            return;
	                        }
	                    }
	                }
	                if( ftp.isLoggedIn() ) {
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
	                	String path = uri.getPath();
                    	if( path != null )
                    		ftp.setCurrentDir( path );
                    	items_tmp = ftp.getDirList( null );
                    	path = ftp.getCurrentDir();
	                    if( path != null ) 
	                    	synchronized( uri ) {
	                    		uri = uri.buildUpon().encodedPath( path ).build();
							}
	                    if( items_tmp != null  ) {
	                        if( items_tmp.length > 0 ) {
    	                        LsItem.LsItemPropComparator comp = 
    	                            items_tmp[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
                                Arrays.sort( items_tmp, comp );
	                        }
	                        parentLink = path == null || path.length() == 0 || path.equals( SLS ) ? SLS : PLS;
	                        sendProgress( tooLong( 8 ) ? ftp.getLog() : null, Commander.OPERATION_COMPLETED, pass_back_on_done );
	                        return;
	                    }
	                }
            	}
            }
            catch( UnknownHostException e ) {
                ftp.debugPrint( "Unknown host:\n" + e.getMessage() );
            }
            catch( IOException e ) {
                ftp.debugPrint( "IO exception:\n" + e.getMessage() );
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
    protected void onComplete( Engine engine ) {
        if( engine instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)engine;
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
        return uri != null ? uri.toString() : "";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public boolean isButtonActive( int brId ) {
        if( brId == R.id.F4 || brId == R.id.sz ) return false;
        return true;
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
                else if( 0 != tmp_uri.getHost().compareTo( uri.getHost() ) )
                    need_reconnect = true;
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
            if( worker != null ) { // that's not good.
            	if( worker.isAlive() ) {
            	    showMessage( "Busy..." );
            		if( worker.isInterrupted() ) {	// cruel force
		            	ftp.logout( false );
		            	ftp.disconnect();	
            		}
            		else
            			worker.reqStop();
	            	Thread.sleep( 500 );      // it has ended itself!
	            	if( worker.isAlive() ) 
	            		return false;      
            	}
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new ListEngine( handler, need_reconnect, pass_back_on_done );
            worker.start();
            return true;
        }
        catch( Exception e ) {
        	commander.showError( "Exception: " + e );
        	e.printStackTrace();
        }
        commander.notifyMe( new Commander.Notify( ftp.getLog(), Commander.OPERATION_FAILED ) );
        return false;
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
            if( subItems == null ) 
                throw new RuntimeException( "Nothing to copy" );
            if( !checkReadyness() ) return false;
            File dest = null;
            int rec_h = 0;
            if( to instanceof FSAdapter  ) {
                dest = new File( to.toString() );
                if( !dest.exists() ) dest.mkdirs();
                if( !dest.isDirectory() )
                    throw new RuntimeException( commander.getContext().getString( R.string.dest_exist ) );
            } else {
                dest = new File( createTempDir() );
                rec_h = setRecipient( to ); 
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyFromEngine( handler, subItems, dest, move, rec_h );
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
    	CopyEngine( Handler h ) {
    		super( h );
    	}
    	protected void setCurFileLength( long len ) {
    		curFileLen = len;
    	}
		@Override
		public boolean completed( long size ) {
			if( curFileLen > 0 )
				sendProgress( null, (int)(size * 100 / curFileLen) );
    		if( stop || isInterrupted() ) {
    			errMsg = "Canceled";
    			return false;
    		}
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
	    	int total = copyFiles( mList, "" );
            if( recipient_hash != 0 ) {
                  sendReceiveReq( recipient_hash, dest_folder );
                  return;
            }
			sendResult( Utils.getOpReport( commander.getContext(), total, R.string.downloaded ) );
	        super.run();
	    }
	
	    private final int copyFiles( LsItem[] list, String path ) {
	        int counter = 0;
	        try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        			errMsg = "Copy operation has been canceled";
	        			break;
	        		}
	        		LsItem f = list[i];
	        		if( f != null ) {
                        String pathName = path + f.getName();
	        			File dest = new File( dest_folder, pathName );
	        			if( f.isDirectory() ) {
	        				sendProgress( "Creating destination folder '" + pathName + "'...", 0 );
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
	        				if( dest.exists() && !dest.delete() ) {
	    	        			errMsg = "Please make sure the folder '" + dest_folder.getCanonicalPath() + "' is writable";
	    	        			break;
	        				}
	        				sendProgress( "Downloading file\n'" + pathName + "'", 0 );
	        				setCurFileLength( f.length() );
	        				FileOutputStream out = new FileOutputStream( dest );
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		if( !ftp.retrieve( pathName, out, this ) ) {
		        					errMsg = "Failed to download file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
		        					dest.delete();
		        					break;
	        	        		}
	        	        		else if( move ) {
                                    if( !ftp.delete( pathName ) ) {
                                        errMsg = "Failed to delete file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
                                        break;
                                    }
        	        		    }
	        	        	}
	        			}
	        			Date ftp_file_date = f.getDate();
	        			if( ftp_file_date != null )
	        			    dest.setLastModified( ftp_file_date.getTime() );
	        			counter++;
	        		}
	        	}
	    	}
			catch( Exception e ) {
				e.printStackTrace();
				errMsg = "Exception: " + e.getMessage();
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
    		if( ftp.makeDir( string ) )
    		    commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED ) );
    		else
    		    commander.notifyMe( new Commander.Notify( "Unable to create directory '" + string + "'", Commander.OPERATION_FAILED ) );
		}
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	if( !checkReadyness() ) return false;
        	LsItem[] subItems = bitsToItems( cis );
        	if( subItems != null ) {
        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                worker = new DelEngine( handler, subItems );
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
        	sendResult( Utils.getOpReport( commander.getContext(), total, R.string.deleted ) );
            super.run();
        }
        private final int delFiles( LsItem[] list, String path ) {
            int counter = 0;
            try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        			errMsg = "Delete operation has been canceled";
	        			break;
	        		}
	        		LsItem f = list[i];
	        		if( f != null ) {
	        			String pathName = path + f.getName();
	        			if( f.isDirectory() ) {
	        				sendProgress( "Removing folder '" + pathName + "'...", i * 100 / list.length );
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
	        				sendProgress( "Deleting file '" + pathName + "'...", i * 100 / list.length );
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
    		if( !checkReadyness() ) return false;
            if( uris == null || uris.length == 0 ) {
            	commander.notifyMe( new Commander.Notify( "Nothing to copy", Commander.OPERATION_FAILED ) );
            	return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null ) {
            	commander.notifyMe( new Commander.Notify( "Something wrong with the files", Commander.OPERATION_FAILED ) );
            	return false;
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyToEngine( handler, list, move_mode );
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
            basePathLen = list[0].getParent().length() + 1;
            move = ( move_mode_ & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode_ & CommanderAdapter.MODE_DEL_SRC_DIR ) != 0;
        }

        @Override
        public void run() {
            int total = copyFiles( mList );
            if( del_src_dir ) {
                File src_dir = mList[0].getParentFile();
                if( src_dir != null )
                    src_dir.delete();
            }
    		sendResult( Utils.getOpReport( commander.getContext(), total, R.string.uploaded ) );
            super.run();
        }
        private final int copyFiles( File[] list ) {
            if( list == null ) return 0;
            int counter = 0;
            try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        			errMsg = "Canceled";
	        			break;
	        		}
	        		File f = list[i];
	        		if( f != null && f.exists() ) {
	        			if( f.isFile() ) {
	        				sendProgress( "Uploading file \n'" + f.getName() + "'", 0 );
	        				String fn = f.getAbsolutePath().substring( basePathLen );
	    					FileInputStream in = new FileInputStream( f );
	    					setCurFileLength( f.length() );
	    		        	synchronized( ftp ) {
	    		        		ftp.clearLog();
		    					if( !ftp.store( fn, in, this ) ) {
		    						errMsg = "Upload '" + f.getName() + "' failed.\n FTP log:\n\n" + ftp.getLog();
		    						break;
		    					}
	    		        	}
	        			}
	        			else
	        			if( f.isDirectory() ) {
	        				sendProgress( "Folder '" + f.getName() + "'...", 0 );
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		String toCreate = f.getAbsolutePath().substring( basePathLen );
		        				if( !ftp.makeDir( toCreate ) ) {
		        					errMsg = "Failed to create folder  '" + toCreate + "'.\n FTP log:\n\n" + ftp.getLog();
		        					break;
		        				}
	        	        	}
	        				counter += copyFiles( f.listFiles() );
	        				if( errMsg != null ) break;
	        			}
    					counter++;
                        if( move && !f.delete() ) {
                            errMsg = "Unable to delete '" + f.getCanonicalPath() + "'.";
                            break;
                        }
	        		}
	        	}
        	}
			catch( IOException e ) {
				e.printStackTrace();
				errMsg = "IOException: " + e.getMessage();
			}
            return counter;
        }
    }
    
    @Override
    public boolean renameItem( int position, String newName ) {
        if( items == null || position <= 0 || position > items.length )
            return false;
        if( ftp != null  && ftp.isLoggedIn() ) {
        	boolean ok = ftp.rename( getItemName( position, false ), newName );
            commander.notifyMe( new Commander.Notify( null, 
                ok ? Commander.OPERATION_COMPLETED_REFRESH_REQUIRED : Commander.OPERATION_FAILED ) );
            return ok;
        }
        return false;
    }

	@Override
	public void prepareToDestroy() {
		heartBeat.cancel();
		heartBeat.purge();
		ftp.disconnect();
        super.prepareToDestroy();
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
		    Log.e( TAG, "bitsToNames()'s Exception: " + e.getMessage() );
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
        	commander.notifyMe( new Commander.Notify( "Not logged in!", Commander.OPERATION_FAILED ) );
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
}
