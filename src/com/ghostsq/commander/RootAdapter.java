package com.ghostsq.commander;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;

import android.os.Handler;
import android.net.Uri;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class RootAdapter extends CommanderAdapterBase {
    public final static String TAG = "RootAdapter";
    public final static String SC = "sh";
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  Uri uri = null;
    public  LsItem[] items = null;

    public RootAdapter() {
    }

    @Override
    public String getType() {
        return "root";
    }

    class ListEngine extends Engine {
        private LsItem[] items_tmp;
        public  String pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
        	super( h );
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
            	String  path = uri.getPath();
                parentLink = path == null || path.length() == 0 || path.equals( SLS ) ? SLS : "..";
                Process p = Runtime.getRuntime().exec( SC );
                DataOutputStream os = new DataOutputStream( p.getOutputStream() );
                DataInputStream  is = new DataInputStream( p.getInputStream() );
                DataInputStream  es = new DataInputStream( p.getErrorStream() );
                os.writeBytes("ls -l " + path + "\n"); // execute command
                os.flush();
                for( int i=0; i< 10; i++ ) {
                    if( isStopReq() ) 
                        throw new Exception();
                    if( is.available() > 0 ) break;
                    Thread.sleep( 50 );
                }
                if( is.available() <= 0 ) // may be an error may be not
                    Log.w( TAG, "No output from the executed command" );
//Log.v( TAG,  "start reading     " + System.currentTimeMillis() );
                ArrayList<LsItem>  array = new ArrayList<LsItem>();
                while( is.available() > 0 ) {
                    if( isStopReq() ) 
                        throw new Exception();
//Log.v( TAG,  "before readLine() " + System.currentTimeMillis() );
                    String ln = is.readLine();
//Log.v( TAG,  "after readLine()  " + System.currentTimeMillis() );
                    if( ln == null ) break;
                    LsItem item = new LsItem( ln );
//Log.v( TAG,  "after create item " + System.currentTimeMillis() );
                    if( item.isValid() )   // problem - if the item is a symlink - how to know its a dir or a file???
                        array.add( item );
                }
//Log.v( TAG,  "end reading       " + System.currentTimeMillis() );
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
                if( p.exitValue() == 255 )
                    Log.e( TAG, "Process.exitValue() returned 255" );
                items_tmp = new LsItem[array.size()];
                array.toArray( items_tmp );
                LsItemPropComparator comp = new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
                Arrays.sort( items_tmp, comp );
                String res_s = null;
                if( es.available() > 0 )
                    res_s = es.readLine();
                sendProgress( res_s, Commander.OPERATION_COMPLETED, pass_back_on_done );
            }
            catch( Exception e ) {
                sendProgress( commander.getContext().getString( R.string.no_root ), 
                        Commander.OPERATION_FAILED, pass_back_on_done );
                Log.e( TAG, "ls output is not valid", e );
            }
            finally {
            	super.run();
            }
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
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri != null )
                uri = tmp_uri;
            if( uri == null )
                return false;
            if( worker != null && worker.reqStop() ) { // that's not good.
                Thread.sleep( 1000 );      // will it end itself?
                if( worker.isAlive() ) {
                    showMessage( "Another worker thread still alive" );
                    return false;
                }
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new ListEngine( handler, pass_back_on_done );
            worker.start();
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        commander.notifyMe( new Commander.Notify( "Fail", Commander.OPERATION_FAILED ) );
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
		        	LsItem[] subItems = bitsToItems( cis );
		        	if( subItems != null ) {
		        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
		                worker = new CopyFromEngine( handler, subItems, dest, move );
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

    
  class CopyFromEngine extends Engine 
  {
	    private LsItem[] mList;
	    private File     dest_folder;
	    private boolean move;
	    private String   src_base_path;
	    CopyFromEngine( Handler h, LsItem[] list, File dest, boolean move_ ) {
	    	super( h );
	        mList = list;
	        dest_folder = dest;
	        move = move_;
	        src_base_path = uri.getPath();
	        if( src_base_path == null || src_base_path.length() == 0 )
	            src_base_path = SLS;
	        else
	        if( src_base_path.charAt( src_base_path.length()-1 ) != SLC )
	            src_base_path += SLS;
	    }
	    @Override
	    public void run() {
	    	int total = copyFiles( mList, "" );
			sendResult( Utils.getOpReport( total, move ? "moved" : "copied" ) );
	        super.run();
	    }
	
	    private final int copyFiles( LsItem[] list, String path ) {
	        int counter = 0;
	        try {
                long dir_size = 0, byte_count = 0;
                for( int i = 0; i < list.length; i++ ) {
                    LsItem f = list[i];               
                    if( !f.isDirectory() )
                        dir_size += f.length();
                }
                double conv = 100./(double)dir_size;
                for( int i = 0; i < list.length; i++ ) {
                    LsItem f = list[i];
                    if( f == null ) continue;
                    String file_name = f.getName();
                    String rel_name = path + file_name;
                    String full_name = src_base_path + rel_name;
                    File   dest_file = new File( dest_folder, path + file_name );
                    if( f.isDirectory() ) {
                        sendProgress( "Processing folder '" + rel_name + "'...", 0 );
                        if( !dest_file.mkdir() ) {
                            if( !dest_file.exists() || !dest_file.isDirectory() ) {
                                errMsg = "Can't create folder \"" + dest_file.getCanonicalPath() + "\"";
                                break;
                            }
                        }
                        LsItem[] subItems = null;//GetFolderList( full_name );
                        if( subItems == null ) {
                            errMsg = "Failed to get the file list of the subfolder '" + rel_name + "'.\n";
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
                        String to_exec = "cat " + full_name + " >" + dest_file.getAbsolutePath() + "\n";
                        Log.i( TAG, to_exec );
    
                        Process p = Runtime.getRuntime().exec( SC );
                        DataOutputStream os = new DataOutputStream( p.getOutputStream() );
                        os.writeBytes( to_exec ); // execute command
                        os.flush();
                        os.writeBytes("exit\n");
                        os.flush();
                        p.waitFor();
                        if( p.exitValue() == 255 ) {
                            error( "Coping of file '" + rel_name + "' failed" );
                            break;
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
		commander.notifyMe( new Commander.Notify( "Operation not supported.", 
		                        Commander.OPERATION_FAILED ) );
		return false;
	}
    @Override
    public void createFolder( String string ) {
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
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
        private String   src_base_path;
        private LsItem[] mList;
        
        DelEngine( Handler h, LsItem[] list ) {
        	super( h );
            mList = list;
            src_base_path = uri.getPath();
            if( src_base_path == null || src_base_path.length() == 0 )
                src_base_path = SLS;
            else
            if( src_base_path.charAt( src_base_path.length()-1 ) != SLC )
                src_base_path += SLS;
        }

        @Override
        public void run() {
            int counter = 0;
            try {
                Process p = Runtime.getRuntime().exec( SC );
                DataOutputStream os = new DataOutputStream( p.getOutputStream() );
                DataInputStream  es = new DataInputStream( p.getErrorStream() );
                int num = mList.length;
                double conv = 100./num;
                for( int i = 0; i < num; i++ ) {
                    if( stop || isInterrupted() )
                        throw new Exception( "Interrupted" );
                    LsItem f = mList[i];
                    String full_name = src_base_path + f.getName();
                    sendProgress( "Deleting " + full_name, (int)(counter * conv) );
                    String to_exec;
                    if( f.isDirectory() )
                        to_exec = "rm -r " + full_name + "\n";
                    else
                        to_exec = "rm " + full_name + "\n";
                    os.writeBytes( to_exec ); // execute command
                    os.flush();
                    Thread.sleep( 200 );
                    if( es.available() > 0 ) {
                        error( es.readLine() );
                        break;
                    }
                    counter++;
                }
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
                if( p.exitValue() == 255 )
                    Log.e( TAG, "Deleting batch failed" );
                if( es.available() > 0 )
                    error( "Late error detected:\n" + es.readLine() );
            }
            catch( Exception e ) {
                errMsg = "Exception: " + e;
            }
    		sendResult( counter > 0 ? "Deleted files/folders: " + counter : "Nothing was deleted" );
            super.run();
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
            commander.Navigate( uri.buildUpon().path( cur + item.getName() ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, boolean move ) {
    	try {
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
    
    class CopyToEngine extends Engine {
        File[] mList;
        int     basePathLen;
        boolean move = false;
        
        CopyToEngine( Handler h, File[] list, boolean move_ ) {
        	super( h );
            mList = list;
            basePathLen = list[0].getParent().length() + 1;
            move = move_;
        }

        @Override
        public void run() {
    		sendResult( Utils.getCopyReport( copyFiles( mList ) ) );
            super.run();
        }

        private final int copyFiles( File[] list ) {
            int counter = 0;
            try {
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
        return false;
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
	        		LsItem curItem;
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
    public class LsItemPropComparator implements Comparator<LsItem> {
        int type;
        boolean case_ignore;
        public LsItemPropComparator( int type_, boolean case_ignore_ ) {
            type = type_;
            case_ignore = case_ignore_;
        }
		@Override
		public int compare( LsItem f1, LsItem f2 ) {
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
