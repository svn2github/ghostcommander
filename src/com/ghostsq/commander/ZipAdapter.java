package com.ghostsq.commander;

import java.lang.System;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;
import com.ghostsq.commander.FTPAdapter.ListEngine;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class ZipAdapter extends CommanderAdapterBase {
    private static final char   SLC = File.separatorChar;
    private static final String SLS = File.separator;
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  Uri          uri = null;
    public  ZipFile      zip = null;
    public  ZipEntry[] items = null;

    public ZipAdapter() {
        parentLink = "..";
    }

    public ZipEntry[] GetFolderList( String fld_path ) {
        if( zip == null ) return null;
        if( fld_path == null ) fld_path = ""; 
        else
            if( fld_path.length() > 0 && fld_path.charAt( 0 ) == SLC ) 
                fld_path = fld_path.substring( 1 );                                 
        int fld_path_len = fld_path.length();
        if( fld_path_len > 0 && fld_path.charAt( fld_path_len - 1 ) != SLC ) { 
            fld_path = fld_path + SLC;
            fld_path_len++;
        }
        Enumeration<? extends ZipEntry> entries = zip.entries();
        if( entries == null )
            return null;
        ArrayList<ZipEntry> array = new ArrayList<ZipEntry>();
        while( entries.hasMoreElements() ) {
            ZipEntry e = entries.nextElement();
            if( e != null ) {
                String entry_name = e.getName();
                if( entry_name == null || fld_path.compareToIgnoreCase(entry_name) == 0 ) 
                    continue;
                /* there are at least two kinds of zips:
                 * with dedicated folder entry and without
                 * the code below should process both
                 * do not do changes until you understand how does it works
                 */
                if( fld_path.regionMatches( true, 0, entry_name, 0, fld_path_len ) ) {
                    int sl_pos = entry_name.indexOf( SLC, fld_path_len );
                    if( sl_pos > 0 ) {
                        String sub_dir = entry_name.substring( fld_path_len, sl_pos );
                        int    sub_dir_len = sub_dir.length();
                        boolean not_yet = true;
                        for( int i = 0; i < array.size(); i++ ) {
                            String a_name = array.get( i ).getName();
                            if( a_name.regionMatches( fld_path_len, sub_dir, 0, sub_dir_len ) ) {
                                not_yet = false;
                                break;
                            }
                        }
                        if( not_yet )   // a folder
                            array.add( new ZipEntry( entry_name.substring( 0, sl_pos+1 ) ) );
                    }
                    else
                        array.add( e ); // a leaf
                }
            }
        }
        return array.toArray( new ZipEntry[array.size()] );
    }
    
    class ListEngine extends Engine {
        private ZipEntry[] items_tmp = null;
        ListEngine( Handler h ) {
        	super( h );
        }
        public ZipEntry[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
            	if( uri != null ) {
            	    String zip_path = uri.getPath(); 
                	if( zip_path != null ) {
                    	if( zip == null || zip_path.compareTo( zip.getName() ) != 0 ) 
                    	    zip = new ZipFile( zip_path );
                    	
                    	String cur_path = null;
                    	try {
                    	    cur_path = uri.getFragment();
                    	}
                    	catch( NullPointerException e ) {
                    	    // it happens only when the Uri is built by Uri.Builder
                    	    System.err.print( "Exception:\n" + e + " on uri.getFragment()" );
                    	}
                	    items_tmp = GetFolderList( cur_path );
                	    if( items_tmp != null ) { 
                            ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING );
                            Arrays.sort( items_tmp, comp );
                            sendProgress( null, Commander.OPERATION_COMPLETED );
                            return;
                	    }
                	}
                }
            }
            catch( Exception e ) {
                System.err.print( "Exception:\n" + e );
                e.printStackTrace();
            }
            finally {
            	super.run();
            }
            sendProgress( "Can't open this ZIP file", Commander.OPERATION_FAILED );
        }
    }

    protected void onComplete( Engine engine ) {
        if( engine instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)engine;
            ZipEntry[] tmp_items = list_engine.getItems();
            if( tmp_items != null && ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                int cnt = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        cnt++;
                items = new ZipEntry[cnt];
                int j = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        items[j++] = tmp_items[i]; 
            }
            else
                items = tmp_items;
            notifyDataSetChanged();
        }
    }
    
    @Override
    public String toString() {
        return uri != null ? Uri.decode( uri.toString() ) : "";
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
    }
    @Override
    public boolean readSource( Uri tmp_uri ) {
        try {
            if( tmp_uri != null )
           	    uri = tmp_uri;
            if( uri == null )
                return false;
            if( worker != null ) { // that's not good.
            	if( worker.isAlive() ) {
            	    showMessage( "Another worker thread still alive" );
           			worker.interrupt();
	            	Thread.sleep( 500 );      // it has ended itself!
	            	if( worker.isAlive() ) 
	            		return false;      
            	}
            }
            commander.notifyMe( null, Commander.OPERATION_STARTED, 0 );
            worker = new ListEngine( handler );
            worker.start();
            return true;
        }
        catch( Exception e ) {
        	commander.showError( "Exception: " + e );
        	e.printStackTrace();
        }
        commander.notifyMe( "Fail", Commander.OPERATION_FAILED, 0 );
        return false;
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
		commander.notifyMe( "Not supported.", Commander.OPERATION_FAILED, 0 );
	}
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
    	
        try {
        	if( zip == null || to instanceof FSAdapter ) {
	        	File dest = new File( to.toString() );
	        	if( dest.exists() && dest.isDirectory() ) {
		        	if( !checkReadyness() ) return false;
		        	ZipEntry[] subItems = bitsToItems( cis );
		        	if( subItems != null ) {
		        	    commander.notifyMe( null, Commander.OPERATION_STARTED, 0 );
		                worker = new CopyFromEngine( handler, subItems, dest );
		                worker.start();
		                return true;
		        	}
	        	}
        	}
        	commander.notifyMe( "Failed to proceed.", Commander.OPERATION_FAILED, 0 );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }

    class CopyEngine extends Engine 
    {
        protected final static int BLOCK_SIZE = 100000;        
    	CopyEngine( Handler h ) {
    		super( h );
    	}
    }
    
    class CopyFromEngine extends CopyEngine 
    {
	    private File       dest_folder;
	    private ZipEntry[] mList = null;
	    private String     base_pfx;
	    private int        base_len; 
	    CopyFromEngine( Handler h, ZipEntry[] list, File dest ) {
	    	super( h );
	    	mList = list;
	        dest_folder = dest;
            try {
                base_pfx = uri.getFragment();
                if( base_pfx == null )
                    base_pfx = "";
                base_len = base_pfx.length(); 
            }
            catch( NullPointerException e ) {
                System.err.print( "Exception: " + e + " on uri.getFragment()" );
            }
	        
	    }
	    @Override
	    public void run() {
	    	int total = copyFiles( mList, "" );
			sendResult( Utils.getCopyReport( total ) );
	        super.run();
	    }
	    private final int copyFiles( ZipEntry[] list, String path ) {
	        int counter = 0;
	        try {
	            long dir_size = 0, byte_count = 0;
	            for( int i = 0; i < list.length; i++ ) {
                    ZipEntry f = list[i];	            
                    if( !f.isDirectory() )
                        dir_size += f.getSize();
	            }
	            double conv = 100./(double)dir_size;
	        	for( int i = 0; i < list.length; i++ ) {
	        		ZipEntry f = list[i];
	        		if( f == null ) continue;
	        		String full_name = f.getName();
	        		if( full_name == null ) continue;
        		    String file_name = new File( full_name ).getName();
        		    File   dest_file = new File( dest_folder, path + file_name );
        			String rel_name = full_name.substring( base_len );
        			
        			if( f.isDirectory() ) {
        				sendProgress( "Processing folder '" + rel_name + "'...", 0 );
        				if( !dest_file.mkdir() ) {
        					if( !dest_file.exists() || !dest_file.isDirectory() ) {
	        					errMsg = "Can't create folder \"" + dest_file.getCanonicalPath() + "\"";
	        					break;
        					}
        				}
        				ZipEntry[] subItems = GetFolderList( full_name );
	                    if( subItems == null ) {
	                    	errMsg = "Failed to get the file list of the subfolder '" + rel_name + "'.\n FTP log:\n\n";
	                    	break;
	                    }
        				counter += copyFiles( subItems, rel_name );
        				if( errMsg != null ) break;
        			}
        			else {
        				if( dest_file.exists() && !dest_file.delete() ) {
    	        			errMsg = "Please make sure the folder '" + dest_folder.getCanonicalPath() + "' is writable";
    	        			break;
        				}
        				InputStream in = zip.getInputStream( f );
        				FileOutputStream out = new FileOutputStream( dest_file );
        	            byte buf[] = new byte[BLOCK_SIZE];
        	            int  n = 0;
        	            int  so_far = (int)(byte_count * conv);
        	            while( true ) {
        	                n = in.read( buf );
        	                if( n < 0 ) break;
        	                out.write( buf, 0, n );
        	                byte_count += n;
        	                sendProgress( "Unpacking \n'" + rel_name + "'...", so_far, (int)(byte_count * conv) );
                            if( stop || isInterrupted() ) {
                                in.close();
                                out.close();
                                dest_file.delete();
                                errMsg = "File '" + dest_file.getName() + "' was not completed, delete.";
                                break;
                            }
        	            }
        			}
                    if( stop || isInterrupted() ) {
                        error( "Canceled by a request." );
                        break;
                    }
                    if( i >= list.length-1 )
                        sendProgress( "Unpacked \n'" + rel_name + "'   ", (int)(byte_count * conv) );
        			counter++;
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
		commander.notifyMe( "Operation not supported", Commander.OPERATION_FAILED, 0 );
		return false;
	}
    @Override
    public void createFolder( String string ) {
        commander.notifyMe( "Not supported", Commander.OPERATION_FAILED, 0 );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        commander.notifyMe( "Not supported", Commander.OPERATION_FAILED, 0 );
/*
        try {
        	if( !checkReadyness() ) return false;
        	ZipEntry[] subItems = bitsToItems( cis );
        	if( subItems != null ) {
        	    commander.notifyMe( null, Commander.OPERATION_STARTED, 0 );
                worker = new DelEngine( handler, subItems );
                worker.start();
	            return true;
        	}
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
*/
        return false;
    }

    class DelEngine extends Engine {
        ZipEntry[] mList;
        
        DelEngine( Handler h, ZipEntry[] subItems ) {
        	super( h );
            mList = subItems;
        }

        @Override
        public void run() {
        	int total = delFiles( mList, "" );
    		sendResult( total > 0 ? "Deleted files/folders: " + total : "Nothing was deleted" );
            super.run();
        }

        private final int delFiles( ZipEntry[] list, String path ) {
            int counter = 0;
            try {
	        	for( int i = 0; i < list.length; i++ ) {
	        		if( stop || isInterrupted() ) {
	        			errMsg = "Delete operation has been canceled";
	        			break;
	        		}
	        		ZipEntry f = list[i];
	        		if( f != null ) {
	        			String pathName = path + f.getName();
	        			if( f.isDirectory() ) {
	        			    // TODO
	        			}
	        			else {
	        			    // TODO
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
                    if( path.charAt( path.length() - 1 ) != SLC )
                        path += SLS;
                    return path + items[position-1].getName();
                }
            }
            return new File( items[position-1].getName() ).getName();
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri != null ) {
            	String cur = null; 
            	try {
                    cur = uri.getFragment();
                } catch( Exception e ) {
                }
            	if( cur == null || cur.length() == 0 ||
            	                 ( cur.length() == 1 && cur.charAt( 0 ) == SLC ) ) {
            	    File zip_file = new File( uri.getPath() );
            	    String parent_dir = zip_file.getParent();
            	    commander.Navigate( Uri.parse( parent_dir != null ? parent_dir : DEFAULT_DIR ), zip_file.getName() );
            	}
            	else {
            	    File cur_f = new File( cur );
            	    String parent_dir = cur_f.getParent();
            	    commander.Navigate( uri.buildUpon().fragment( parent_dir != null ? parent_dir : "" ).build(), cur_f.getName() );
            	}
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        ZipEntry item = items[position - 1];
        
        if( item.isDirectory() ) {
            String cur = null;    
            try {
                cur = uri.getFragment();
            }
            catch( NullPointerException e ) {}
        	if( cur == null ) 
        	    cur = "";
        	else
        	    if( cur.length() == 0 || cur.charAt( cur.length()-1 ) != SLC )
        	        cur += SLS;
            commander.Navigate( uri.buildUpon().fragment( item.getName() ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, boolean move ) {
        commander.notifyMe( "Not supported", Commander.OPERATION_FAILED, 0 );

/*
    	try {
    		if( !checkReadyness() ) return false;
            if( uris == null || uris.length == 0 ) {
            	commander.notifyMe( "Nothing to copy", Commander.OPERATION_FAILED, 0 );
            	return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null ) {
            	commander.notifyMe( "Something wrong with the files", Commander.OPERATION_FAILED, 0 );
            	return false;
            }
            commander.notifyMe( null, Commander.OPERATION_STARTED, 0 );
            worker = new CopyToEngine( handler, list, move );
            worker.start();
            return true;
		} catch( Exception e ) {
			commander.notifyMe( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED, 0 );
		}
*/
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
	        			 // TODO
	        			}
	        			else
	        			if( f.isDirectory() ) {
	        			 // TODO
	        				counter += copyFiles( f.listFiles() );
	        				if( errMsg != null ) break;
	        			}
    					counter++;
	        		}
	        	}
        	}
			catch( Exception e ) {
				e.printStackTrace();
				errMsg = "IOException: " + e.getMessage();
			}
            return counter;
        }
    }
    
    @Override
    public boolean renameItem( int position, String newName ) {
     // TODO
        return false;
    }

	@Override
	public void prepareToDestroy() {
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
    	item.name = "";
    	{
	        if( position == 0 ) {
	            item.name = parentLink;
	        }
	        else {
	        	if( items != null && position > 0 && position <= items.length ) {
	        		ZipEntry curItem;
            		curItem = items[position - 1];
                    item.dir = curItem.isDirectory();
		            item.name = item.dir ? SLS + getLocalName( curItem ) : getLocalName( curItem );
		            item.size = curItem.getSize();
		            ListView flv = (ListView)parent;
		            SparseBooleanArray cis = flv.getCheckedItemPositions();
		            item.sel = cis.get( position );
		            long item_time = curItem.getTime();
		            item.date = item_time > 0 ? new Date( item_time ) : null;
	            }
	        }
    	}
        return getView( convertView, parent, item );
    }
    private final ZipEntry[] bitsToItems( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            ZipEntry[] subItems = new ZipEntry[counter];
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
        	commander.notifyMe( "busy!", Commander.OPERATION_FAILED, 0 );
        	return false;
        }
    	return true;
    }
    private final String getLocalName( ZipEntry e ) {
        return new File( e.getName() ).getName();
    }
    public class ZipItemPropComparator implements Comparator<ZipEntry> {
        int type;

        public ZipItemPropComparator( int type_ ) {
            type = type_;
        }
		@Override
		public int compare( ZipEntry f1, ZipEntry f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            if( type == SORT_NAME )
                return f1.getName().compareTo( f2.getName() );
            if( type == SORT_SIZE )
                return (int)(f1.getSize() - f2.getSize());
            if( type == SORT_DATE )
                return (int)(f1.getTime() - f2.getTime());
            return 0;
		}
    }
}
