package com.ghostsq.commander;

import java.lang.System;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;


import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;
import com.ghostsq.commander.Utils.Credentials;

import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class FTPAdapter extends CommanderAdapterBase {
    private final static String TAG = "FTPAdapter";
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  FTP ftp;
    public  Uri uri = null;
    public  FTPItem[] items = null;
    private Timer  heartBeat;

    
    public Credentials theUserPass;
    
    public FTPAdapter() {
        theUserPass = new Utils().new Credentials();
        ftp = new FTP();
        try {
            heartBeat = new Timer( "FTP Heartbeat", true );
            heartBeat.schedule( new Noop(), 100000, 120000 );
		} catch( Exception e ) {
		}
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
        private FTPItem[] items_tmp;
        ListEngine( Handler h, boolean need_reconnect_ ) {
        	super( h );
        	needReconnect = need_reconnect_; 
        }
        public FTPItem[] getItems() {
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
	                        if( theUserPass.isNotSet() )
	                            theUserPass.set( uri.getUserInfo() );
	                        if( !theUserPass.isNotSet() && ftp.login( theUserPass.userName, theUserPass.userPass ) ) 
	                            sendProgress( "Connected to\"" + host + "\",  Logged in as \"" + theUserPass.userName + "\"", 3 );
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
	                    		uri = uri.buildUpon().path( path ).build();
							}
	                    if( items_tmp != null ) {
                    		FTPItemPropComparator comp = new FTPItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
                            Arrays.sort( items_tmp, comp );
	                        parentLink = path == null || path.length() == 0 || path.equals( "/" ) ? "/" : "..";
	                        sendProgress( tooLong( 8 ) ? ftp.getLog() : null, Commander.OPERATION_COMPLETED );
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
            sendProgress( ftp.getLog(), Commander.OPERATION_FAILED );
        }
    }

    protected void onComplete( Engine engine ) {
        if( engine instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)engine;
            items = null;
            if( ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                FTPItem[] tmp_items = list_engine.getItems();
                if( tmp_items != null ) {
                    int cnt = 0;
                    for( int i = 0; i < tmp_items.length; i++ )
                        if( tmp_items[i].getName().charAt( 0 ) != '.' )
                            cnt++;
                    items = new FTPItem[cnt];
                    int j = 0;
                    for( int i = 0; i < tmp_items.length; i++ )
                        if( tmp_items[i].getName().charAt( 0 ) != '.' )
                            items[j++] = tmp_items[i];
                }
            }
            else
                items = list_engine.getItems();
            notifyDataSetChanged();
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
    public void setIdentities( String name, String pass ) {
        theUserPass.set( name, pass );
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            boolean need_reconnect = false;
            if( tmp_uri != null ) { 
                String new_user_info = tmp_uri.getUserInfo();
                if( !theUserPass.isSame( new_user_info ) || 
                        ( uri != null && 0 != tmp_uri.getHost().compareTo( uri.getHost() ) ) ) {
                    need_reconnect = true;
                    theUserPass.set( new_user_info );
                }
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
            			worker.interrupt();
	            	Thread.sleep( 500 );      // it has ended itself!
	            	if( worker.isAlive() ) 
	            		return false;      
            	}
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new ListEngine( handler, need_reconnect );
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
    	
        try {
        	if( to instanceof FSAdapter ) {
	        	File dest = new File( to.toString() );
	        	if( dest.exists() && dest.isDirectory() ) {
		        	if( !checkReadyness() ) return false;
		        	FTPItem[] subItems = bitsToItems( cis );
		        	if( subItems != null ) {
		        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
		                worker = new CopyFromEngine( handler, subItems, dest );
		                worker.start();
		                return true;
		        	}
	        	}
        	}
        	commander.notifyMe( new Commander.Notify( "Failed to proceed.", Commander.OPERATION_FAILED ) );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
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
	    private FTPItem[] mList;
	    private File      dest_folder;
	    CopyFromEngine( Handler h, FTPItem[] list, File dest ) {
	    	super( h );
	        mList = list;
	        dest_folder = dest;
	    }
	    @Override
	    public void run() {
	    	int total = copyFiles( mList, "" );
			sendResult( Utils.getCopyReport( total ) );
	        super.run();
	    }
	
	    private final int copyFiles( FTPItem[] list, String path ) {
	        int counter = 0;
	        try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        			errMsg = "Copy operation has been canceled";
	        			break;
	        		}
	        		FTPItem f = list[i];
	        		if( f != null ) {
	        			File dest = new File( dest_folder, path + f.getName() );
	        			String pathName = path + f.getName();
	        			if( f.isDirectory() ) {
	        				sendProgress( "Processing folder '" + pathName + "'...", 0 );
	        				if( !dest.mkdir() ) {
	        					if( !dest.exists() || !dest.isDirectory() ) {
		        					errMsg = "Can't create folder \"" + dest.getCanonicalPath() + "\"";
		        					break;
	        					}
	        				}
		                    FTPItem[] subItems = ftp.getDirList( pathName );
		                    if( subItems == null ) {
		                    	errMsg = "Failed to get the file list of the subfolder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
		                    	break;
		                    }
	        				counter += copyFiles( subItems, pathName + "/" );
	        				if( errMsg != null ) break;
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
	        	        	}
	        			}
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
    		ftp.makeDir( string );
		}
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	if( !checkReadyness() ) return false;
        	FTPItem[] subItems = bitsToItems( cis );
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
        FTPItem[] mList;
        
        DelEngine( Handler h, FTPItem[] list ) {
        	super( h );
            mList = list;
        }

        @Override
        public void run() {
        	int total = delFiles( mList, "" );
    		sendResult( total > 0 ? "Deleted files/folders: " + total : "Nothing was deleted" );
            super.run();
        }

        private final int delFiles( FTPItem[] list, String path ) {
            int counter = 0;
            try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        			errMsg = "Delete operation has been canceled";
	        			break;
	        		}
	        		FTPItem f = list[i];
	        		if( f != null ) {
	        			String pathName = path + f.getName();
	        			if( f.isDirectory() ) {
	        				sendProgress( "Removing folder '" + pathName + "'...", i * 100 / list.length );
		                    FTPItem[] subItems = ftp.getDirList( pathName );
	        				counter += delFiles( subItems, pathName + "/" );
	        				if( errMsg != null ) break;
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		if( !ftp.rmDir( pathName ) ) {
		        					errMsg = "Failed to remove folder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
		        					break;
	        	        		}
	        	        	}
	        			}
	        			else {
	        				sendProgress( "Deleting file '" + pathName + "'...", i * 100 / list.length );
	        	        	synchronized( ftp ) {
	        	        		ftp.clearLog();
	        	        		if( !ftp.delete( pathName ) ) {
		        					errMsg = "Failed to delete file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
		        					break;
	        	        		}
	        	        	}
	        			}
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
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                String path = toString();
                if( path != null && path.length() > 0 ) {
                    if( path.charAt( path.length() - 1 ) != '/' )
                        path += "/";
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
            if( uri != null && parentLink != "/" ) {
            	String path = uri.getPath();
                int len_ = path.length()-1;
                if( len_ > 0 ) {
	                if( path.charAt( len_ ) == '/' )
	                	path = path.substring( 0, len_ );
	                path = path.substring( 0, path.lastIndexOf( '/' ) );
	                if( path.length() == 0 )
	                	path = "/";
	                commander.Navigate( uri.buildUpon().path( path ).build(), uri.getLastPathSegment() );
                }
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        FTPItem item = items[position - 1];
        
        if( item.isDirectory() ) {
        	String cur = uri.getPath();
            if( cur == null || cur.length() == 0 ) 
                cur = "/";
            else
            	if( cur.charAt( cur.length()-1 ) != '/' )
            		cur += "/";
            commander.Navigate( uri.buildUpon().path( cur + item.getName() ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, boolean move ) {
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
            worker = new CopyToEngine( handler, list, move );
            worker.start();
            return true;
		} catch( Exception e ) {
			commander.notifyMe( new Commander.Notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED ) );
		}
		return false;
    }
    
    class CopyToEngine extends CopyEngine {
        File[] mList;
        int     basePathLen;
        
        CopyToEngine( Handler h, File[] list, boolean move ) { // TODO: delete the source on move
        	super( h );
            mList = list;
            basePathLen = list[0].getParent().length() + 1;
        }

        @Override
        public void run() {
    		sendResult( Utils.getCopyReport( copyFiles( mList ) ) );
            super.run();
        }

        private final int copyFiles( File[] list ) {
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
	        				String fn = f.getCanonicalPath().substring( basePathLen );
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
	        	        		String toCreate = f.getCanonicalPath().substring( basePathLen );
		        				if( !ftp.makeDir( toCreate ) ) {
		        					errMsg = "Failed to create folder  '" + toCreate + "'.\n FTP log:\n\n" + ftp.getLog();
		        					break;
		        				}
	        	        	}
	        				counter += copyFiles( f.listFiles() );
	        				if( errMsg != null ) break;
	        			}
    					counter++;
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
        if( ftp != null  && ftp.isLoggedIn() )
        	return ftp.rename( getItemName( position, false ), newName );
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
    public int getCount() {
   	    return items != null ? items.length + 1 : 1;
    }

    @Override
    public Object getItem( int position ) {
    	if( worker != null )
    		return null;
    	return items != null && position < items.length ? items[position] : null;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
    	Item item = new Item();
    	item.name = "???";
    	{
	        if( position == 0 ) {
	            item.name = parentLink;
	        }
	        else {
	        	if( items != null && position > 0 && position <= items.length ) {
	        		FTPItem curItem;
            		curItem = items[position - 1];
                    item.dir = curItem.isDirectory();
		            item.name = item.dir ? SLS + curItem.getName() : curItem.getName();
		            item.size = curItem.length();
		            ListView flv = (ListView)parent;
		            SparseBooleanArray cis = flv.getCheckedItemPositions();
		            item.sel = cis.get( position );
		            item.date = curItem.getDate();
	            }
	        }
    	}
        return getView( convertView, parent, item );
    }
    private final FTPItem[] bitsToItems( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            FTPItem[] subItems = new FTPItem[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                	subItems[j++] = items[ cis.keyAt( i ) - 1 ];
            return subItems;
		} catch( Exception e ) {
			commander.showError( "bitsToNames()'s Exception: " + e.getMessage() );
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
    public class FTPItemPropComparator implements Comparator<FTPItem> {
        int type;
        boolean case_ignore;
        public FTPItemPropComparator( int type_, boolean case_ignore_ ) {
            type = type_;
            case_ignore = case_ignore_;
        }
		@Override
		public int compare( FTPItem f1, FTPItem f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            int ext_cmp = 0;
            switch( type ) {
            case SORT_EXT:
                ext_cmp = Utils.getFileExt( f1.getName() ).compareTo( Utils.getFileExt( f2.getName() ) );
                break;
            case SORT_SIZE:
                ext_cmp = f1.length() - f2.length() < 0 ? -1 : 1;
                break;
            case SORT_DATE:
                ext_cmp = f1.getDate().compareTo( f2.getDate() );
                break;
            }
            if( ext_cmp != 0 )
                return ext_cmp;
            return f1.compareTo( f2 );
		}
    }
}
