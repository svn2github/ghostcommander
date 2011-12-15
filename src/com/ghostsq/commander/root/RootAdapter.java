package com.ghostsq.commander.root;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;

import android.os.Handler;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.TextViewer;
import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.adapters.FSAdapter;
import com.ghostsq.commander.utils.LsItem;
import com.ghostsq.commander.utils.Utils;
import com.ghostsq.commander.utils.LsItem.LsItemPropComparator;
import com.ghostsq.commander.root.MountsListEngine;
import com.ghostsq.commander.root.MountsListEngine.MountItem;

public class RootAdapter extends CommanderAdapterBase {
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    private final static String TAG = "RootAdapter";
    public static final String DEFAULT_LOC = "root:";
    private final static int CHMOD_CMD = 36793, CMD_CMD = 39716;
    private Uri uri = null;
    private LsItem[] items = null;
    private int attempts = 0;
    private MountsListEngine systemMountReader;
    private String systemMountMode;
    private final static String SYSTEM_PATH = "/system";
    private ContentEngine contentEngine;
    private File tmp_f, dst_f;

    public RootAdapter( Context ctx_ ) {
        super( ctx_, SHOW_ATTR );
    }
    @Override
    public int getType() {
        return CA.ROOT;
    }
    
    class ListEngine extends ExecEngine {
        private LsItem[] items_tmp;
        private String pass_back_on_done;
        private Uri src;
        private ArrayList<LsItem>  array;
        ListEngine( Context ctx, Handler h, Uri src_, String pass_back_on_done_ ) {
        	super( ctx, h );
            src = src_;
        	pass_back_on_done = pass_back_on_done_;
        }
        public LsItem[] getItems() {
            return items_tmp;
        }       
        public Uri getUri() {
            return src;
        }
        @Override
        public void run() {
            String msg = null;
            if(	!getList( true ) ) {
                Log.w( TAG, "su failed. let's try just sh" );
                errMsg = null;
                msg = commander.getContext().getString( R.string.no_root );
                if( !getList( false ) )
                    error( commander.getContext().getString( R.string.cant_cd, src.getPath() ) );
            }
            doneReading( msg, pass_back_on_done );
        }
        private boolean getList( boolean su ) {
            if( !su ) sh = "sh";
            String path = src.getPath();
            if( path == null ) {
                path = SLS;
                src = src.buildUpon().encodedPath( path ).build();
            }
            parentLink = path == null || path.length() == 0 || path.equals( SLS ) ? SLS : "..";
            array = new ArrayList<LsItem>();
            String to_execute = "ls " + ( ( mode & MODE_HIDDEN ) != HIDE_MODE ? "-a ":"" ) + "-l " + ExecEngine.prepFileName( path );
            if( !execute( to_execute, false ) ) // 'busybox -l' always outs UID/GID as numbers, not names!  
                return false;   

            if( !isStopReq() ) {
                int sz = array != null ? array.size() : 0;
                items_tmp = new LsItem[sz];
                if( sz > 0 ) {
                    array.toArray( items_tmp );
                    LsItem.LsItemPropComparator comp = 
                        items_tmp[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
                    Arrays.sort( items_tmp, comp );
                }
                return true;
            }
            return false;
        }
        @Override
        protected void procInput( BufferedReader br ) throws IOException, Exception {
            while( br.ready() ) {
                if( isStopReq() ) break; 
                String ln = br.readLine();
                if( ln == null ) break;
                LsItem item = new LsItem( ln );
                if( item.isValid() ) {
                    if( !"..".equals( item.getName() ) && !".".equals( item.getName() ) )
                        array.add( item ); // a problem - if the item is a symlink - how to know it's a dir or a file???
                }
            }
       }
    }
    @Override
    protected void onReadComplete() {
        try {
            attempts = 0;
            if( reader instanceof ListEngine ) {
                ListEngine list_engine = (ListEngine)reader;
                items = list_engine.getItems();
                uri = list_engine.getUri();
                numItems = items != null ? items.length + 1 : 1;
                notifyDataSetChanged();
                
                String path = uri.getPath();
                if( path != null && path.startsWith( SYSTEM_PATH ) ) {
                    // know the /system mount state
                    systemMountReader = new MountsListEngine( commander.getContext(), readerHandler, false );
                    systemMountReader.start();
                }
            } else
            if( systemMountReader != null ) {
                MountItem[] mounts = systemMountReader.getItems();
                if( mounts != null ) {
                    boolean remount = systemMountReader.toRemount();
                    systemMountReader = null;
                    for( MountItem m : mounts ) {
                        String mp = m.getMountPoint();
                        if( SYSTEM_PATH.equals( mp ) ) {
                            if( remount ) {
                                worker = new RemountEngine( commander.getContext(), workerHandler, m );
                                worker.start();
                            }
                            else
                                systemMountMode = m.getMode();
                            break;
                        }
                    }
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
    
    @Override
    public String toString() {
        if( uri != null ) {
            if( systemMountMode != null ) {
                String path = uri.getPath();
                try {
                    return uri.buildUpon().fragment( path != null && path.startsWith( SYSTEM_PATH ) ? systemMountMode : null ).build().toString();
                } catch( Exception e ) {}
            }            
            return uri.toString();
        }
        return "";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public void setUri( Uri uri_ ) {
        uri = uri_;
    }
    
    @Override
    public void setIdentities( String name, String pass ) {
        // TODO: may be some day we need to provide a password for su ?
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri == null )
                tmp_uri = uri;
            if( tmp_uri == null )
                return false;
            uri = tmp_uri;  // since the Superuser application can break the execution,
                            // it's important to keep the uri 
            if( reader != null ) {
                if( attempts++ < 2 ) {
                    commander.showInfo( "Busy..." );
                    return false;
                }
                if( reader.reqStop() ) { // that's not good.
                    Thread.sleep( 500 ); // will it end itself?
                    if( reader.isAlive() ) {
                        Log.e( TAG, "Busy!" );
                        return false;
                    }
                }
            }
            
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            reader = new ListEngine( commander.getContext(), readerHandler, tmp_uri, pass_back_on_done );
            reader.start();
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
        LsItem[] s_items = bitsToItems( cis );
        if( s_items != null && s_items.length > 0 ) {
            Intent in = new Intent( ctx, TextViewer.class );
            in.setData( Uri.parse( "exec:" ) );
            String s = "";
            String path = Utils.mbAddSl( uri.getPath() );
            for( int i = 0; i < s_items.length; i++ )
                s += " " + path + s_items[i].getName();
            in.putExtra( "cmd", "stat " + s );
    	    commander.issue( in, 0 );
        }
	}
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        try {
            LsItem[] subItems = bitsToItems( cis );
            if( subItems != null ) {
                String to_path = null;
                int rec_h = 0;
            	if( to instanceof FSAdapter || to instanceof RootAdapter ) {
            	    Uri to_uri = to.getUri();
            	    if( to_uri != null )
            	        to_path = to_uri.getPath();
            	    to = null;
            	} else {
                    to_path = createTempDir();
                    rec_h = setRecipient( to ); 
            	}
                if( to_path != null ) {
                    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                    worker = new CopyFromEngine( commander.getContext(), workerHandler, subItems, to_path, move, rec_h );
                    worker.start();
                    return true;
                }
            }
        	commander.notifyMe( new Commander.Notify( "Failed to proceed.", Commander.OPERATION_FAILED ) );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
        }
        return false;
    }
    
    class CopyFromEngine extends ExecEngine {
        private int counter = 0;
	    private LsItem[] list;
	    private String   dest_folder;
	    private boolean  move;
	    private String   src_base_path;
	    private int      recipient_hash;
	    CopyFromEngine( Context ctx, Handler h, LsItem[] list_, String dest, boolean move_, int recipient_h ) {
	    	super( ctx, h );
	        list = list_;
	        dest_folder = dest;
	        move = move_;
	        src_base_path = uri.getPath();
	        if( src_base_path == null || src_base_path.length() == 0 )
	            src_base_path = SLS;
	        else
	        if( src_base_path.charAt( src_base_path.length()-1 ) != SLC )
	            src_base_path += SLS;
	        recipient_hash = recipient_h;
	    }
	    @Override
	    public void run() {
	        try {
	            boolean ok = execute();
                if( counter > 0 && recipient_hash != 0 ) {
                    File temp_dir = new File( dest_folder );
                    File[] temp_content = temp_dir.listFiles();
                    String[] paths = new String[temp_content.length];
                    for( int i = 0; i < temp_content.length; i++ )
                        paths[i] = temp_content[i].getAbsolutePath();
                    sendReceiveReq( recipient_hash, paths );
                    return;
                }
                if( !ok )
                    counter = 0;
            }
            catch( Exception e ) {
                error( "Exception: " + e );
            }
            sendResult( counter > 0 ? Utils.getOpReport( commander.getContext(), counter, move ? R.string.moved : R.string.copied ) : "" );
        }
       
        @Override
        protected boolean cmdDialog( OutputStreamWriter os, BufferedReader is, BufferedReader es )  { 
            try {
                int num = list.length;
                double conv = 100./(double)num;
                String esc_dest = ExecEngine.prepFileName( dest_folder );
                for( int i = 0; i < num; i++ ) {
                    LsItem f = list[i];
                    if( f == null ) continue;
                    String file_name = f.getName();
                    String full_name = src_base_path + file_name;
                    String cmd = move ? " mv -f" : ( f.isDirectory() ? " cp -r" : " cp" );
                    String to_exec = cmd + " " + ExecEngine.prepFileName( full_name ) 
                                         + " " + esc_dest;
                    outCmd( true, to_exec, os );
                    if( procError( es ) ) return false;
                    
                    File dst_file = new File( dest_folder, f.getName() );
                    String dst_path = dst_file.getAbsolutePath(); 
                    
                    Permissions src_p = new Permissions( f.getAttr() );
                    String chown_cmd = "chown " + src_p.generateChownString().append(" ").append( dst_path ).toString();
                    outCmd( false, chown_cmd, os );
                    String chmod_cmd = "chmod " + src_p.generateChmodString().append(" ").append( dst_path ).toString();
                    outCmd( true, chmod_cmd, os );
                    
                    sendProgress( "'" + file_name + "'", (int)(i * conv) );
                    counter++;
                }
                return true;
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            return false;
	    }
	}
	    
	@Override
	public boolean createFile( String fileURI ) {
		commander.notifyMe( new Commander.Notify( "Operation is not supported.", 
		                        Commander.OPERATION_FAILED ) );
		return false;
	}
    @Override
    public void createFolder( String new_name ) {
        if( uri == null ) return;
        if( isWorkerStillAlive() )
            commander.notifyMe( new Commander.Notify( "Busy", Commander.OPERATION_FAILED ) );
        else {
            worker = new MkDirEngine( commander.getContext(), workerHandler, new_name );
            worker.start();
        }
    }
    
    class MkDirEngine extends ExecEngine {
        String full_name;
        MkDirEngine( Context ctx, Handler h, String new_name ) {
            super( ctx, h );
            full_name = uri.getPath() + SLS + new_name;
        }
        
        @Override
        public void run() {
            try {
                String cmd = "mkdir " + ExecEngine.prepFileName( full_name );
                execute( cmd, true, 100 );
            } catch( Exception e ) {
                error( "Exception: " + e );
            }
            sendResult( errMsg != null ? "Directory '" + full_name + "' was not created." : null );
        }
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
            if( isWorkerStillAlive() ) {
                commander.notifyMe( new Commander.Notify( "Busy", Commander.OPERATION_FAILED ) );
                return false;
            }
        	LsItem[] subItems = bitsToItems( cis );
        	if( subItems != null ) {
        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                worker = new DelEngine( commander.getContext(), workerHandler, subItems );
                worker.start();
	            return true;
        	}
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
        }
        return false;
    }

    class DelEngine extends ExecEngine {
        private String   src_base_path;
        private LsItem[] mList;
        private int counter = 0;
        
        DelEngine( Context ctx, Handler h, LsItem[] list ) {
        	super( ctx, h );
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
            if( !execute() )
                counter = 0;
            sendResult( counter > 0 ? Utils.getOpReport( commander.getContext(), counter, R.string.deleted ) : "" );
        }
       
        @Override
        protected boolean cmdDialog( OutputStreamWriter os, BufferedReader is, BufferedReader es ) { 
            try {
                int num = mList.length;
                double conv = 100./num;
                for( int i = 0; i < num; i++ ) {
                    LsItem f = mList[i];
                    String full_name = src_base_path + f.getName();
                    sendProgress( "Deleting " + full_name, (int)(counter * conv) );
                    String to_exec = "rm " + ( f.isDirectory() ? "-r " : "" ) + prepFileName( full_name );
                    outCmd( false, to_exec, os );
                    if( procError( es ) ) return false;
                    counter++;
                }
                return true;
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            return false;
        }
    }
    @Override
    public Uri getItemUri( int position ) {
        if( uri == null ) return null;
        return uri.buildUpon().appendEncodedPath( getItemName( position, false ) ).build();
    }
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                Uri item_uri = getItemUri( position );
                if( item_uri != null )
                    return item_uri.toString();
            }
            else return items[position-1].getName();
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
        else
            new CmdDialog( ctx, item, this );
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
    	try {
            if( full_names == null || full_names.length == 0 ) {
            	commander.notifyMe( new Commander.Notify( "Nothing to copy", Commander.OPERATION_FAILED ) );
            	return false;
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyToEngine( commander.getContext(), workerHandler, full_names, 
                                     ( move_mode & MODE_MOVE ) != 0, uri.getPath(), false );
            worker.start();
            return true;
		} catch( Exception e ) {
			commander.notifyMe( new Commander.Notify( "Exception: " + e, Commander.OPERATION_FAILED ) );
		}
		return false;
    }
    
    class CopyToEngine extends ExecEngine {
        private String[] src_full_names;
        private String   dest;
        private boolean move = false;
        private boolean quiet;
        private int counter = 0;
        
        CopyToEngine( Context ctx, Handler h, String[] list, boolean move_, String dest_, boolean quiet_ ) {
        	super( ctx, h );
        	src_full_names = list;
        	dest = dest_;
            move = move_;
            quiet = quiet_;
        }

        @Override
        public void run() {
            if( !execute() )
                counter = 0;
            if( quiet )
                sendResult( null );
            else
                sendResult( counter > 0 ? Utils.getOpReport( commander.getContext(), counter, move ? R.string.moved : R.string.copied ) : "" );
        }
       
        @Override
        protected boolean cmdDialog( OutputStreamWriter os, BufferedReader is, BufferedReader es ) { 
            try {
                String cmd = move ? " mv" : " cp -r";
                String esc_dest = prepFileName( dest );
                int num = src_full_names.length;
                double conv = 100./(double)num;
                for( int i = 0; i < num; i++ ) {
                    String full_name = src_full_names[i];
                    if( full_name == null ) continue;
                    String esc_fn = prepFileName( full_name );
                    String ls_cmd = "ls -l " + esc_fn;
                    outCmd( false, ls_cmd, os );
                    String str = null; 
                    while( is.ready() ) {
                        str = is.readLine();
                        if( str != null && str.trim().length() > 0 )
                            Log.v( TAG, ">>>" + str ); 
                    }
                    LsItem src_item = null;
                    if( str != null )
                        src_item = new LsItem( str ); 
                    
                    String to_exec = cmd + " " + esc_fn + " " + esc_dest;
                    outCmd( true, to_exec, os );
                    if( procError( es ) ) return false;
                    
                    if( src_item != null ) {
                        File src_file = new File( full_name );
                        File dst_file = new File( dest, src_file.getName() );
                        String dst_path = dst_file.getAbsolutePath(); 
                        
                        Permissions src_p = new Permissions( src_item.getAttr() );
                        String chown_cmd = "chown " + src_p.generateChownString().append(" ").append( dst_path ).toString();
                        outCmd( false, chown_cmd, os );
                        String chmod_cmd = "chmod " + src_p.generateChmodString().append(" ").append( dst_path ).toString();
                        outCmd( true, chmod_cmd, os );
                    }
                    
                    if( !quiet ) sendProgress( esc_fn + "   ", (int)(i * conv) );
                    counter++;
                }
                return true;
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            return false;
        }
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length )
            return false;
        try {
            LsItem from = items[position - 1];
            String[] a = new String[1];
            a[0] = uri.getPath() + SLS + from.getName();
            String to = uri.getPath() + SLS + newName;
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            if( copy ) {
                // TODO
                return false;
            }
            
            worker = new CopyToEngine( commander.getContext(), workerHandler, a, true, to, true );
            worker.start();
            return true;
        } catch( Exception e ) {
            commander.notifyMe( new Commander.Notify( "Exception: " + e, Commander.OPERATION_FAILED ) );
        }
        return false;
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
                    String lnk = curItem.getLinkTarget();
                    if( lnk != null ) 
                        item.name += " -> " + lnk; 
                    
                    item.size = curItem.isDirectory() ? -1 : curItem.length();
                    item.date = curItem.getDate();
                    item.attr = curItem.getAttr();
                }
            }
        }
        return item;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = (Item)getItem( position );
        if( items != null && position > 0 && position <= items.length ) {
            ListView flv = (ListView)parent;
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            item.sel = cis != null ? cis.get( position ) : false;
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
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        subItems[j++] = items[ k - 1 ];
                }
            return subItems;
		} catch( Exception e ) {
		    Log.e( TAG, "bitsToNames()'s Exception: " + e );
		}
		return null;
    }
    
    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        super.populateContextMenu( menu, acmi, num );
        try {
            if( acmi.position > 0 )
                menu.add( 0, CHMOD_CMD, 0, R.string.perms_label );
            menu.add( 0, CMD_CMD, 0, commander.getContext().getString( R.string.execute_command ) ); 
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }    

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        try {
            if( CHMOD_CMD == command_id || CMD_CMD == command_id ) {
                if( isWorkerStillAlive() )
                    return;
                LsItem[] items_todo = bitsToItems( cis );
                boolean selected_one = items_todo != null && items_todo.length > 0 && items_todo[0] != null;
                if( CHMOD_CMD == command_id ) {
                    if( selected_one ) {
                        Intent i = new Intent( ctx, EditPermissions.class );
                        i.putExtra( "perm", items_todo[0].getAttr() );
                        i.putExtra( "path", Utils.mbAddSl( uri.getPath() ) + items_todo[0].getName() );
                        commander.issue( i, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED );
                    }
                    else
                        commander.showError( commander.getContext().getString( R.string.select_some ) );
                }
                else if( CMD_CMD == command_id )
                    new CmdDialog( commander.getContext(), selected_one ? items_todo[0] : null, this );
            } else if( R.id.remount == command_id ) {
                if( reader != null && reader.isAlive() ) {
                    commander.showError( commander.getContext().getString( R.string.busy ) );
                    return;
                }
                systemMountReader = new MountsListEngine( commander.getContext(), readerHandler, true );
                systemMountReader.start();
            }
        } catch( Exception e ) {
            Log.e( TAG, "Can't do the command " + command_id, e );
        }
    }
    
    public void execute( String command, boolean bb ) {
        if( isWorkerStillAlive() )
            commander.notifyMe( new Commander.Notify( "Busy", Commander.OPERATION_FAILED ) );
        else {
            worker = new ExecEngine( commander.getContext(), workerHandler, uri.getPath(), command, bb, 500 );
            worker.start();
        }
    }

    public void executeToViewer( String command, boolean bb ) {
            Intent in = new Intent( ctx, TextViewer.class );
            in.setData( Uri.parse( "exec:" ) );
            in.putExtra( "cmd", "cd " + uri.getPath() + " ; " + ( bb ? "busybox " : "" ) + command );
            commander.issue( in, 0 );
    }    
    
    class CmdDialog implements OnClickListener {
        private LsItem   item;
        private RootAdapter owner;
        private EditText ctv;
        private CheckBox bbc;
        CmdDialog( Context c, LsItem item_, RootAdapter owner_ ) {
            try {
                if( uri == null  ) return;
                owner = owner_;
                item = item_;
                LayoutInflater factory = LayoutInflater.from( c );
                View cdv = factory.inflate( R.layout.command, null );
                if( cdv != null ) {
                    bbc = (CheckBox)cdv.findViewById( R.id.use_busybox );
                    ctv = (EditText)cdv.findViewById( R.id.command_text );
                    ctv.setText( item != null ? item.getName() : "" );
                    new AlertDialog.Builder( c )
                        .setTitle( "Run Command" )
                        .setView( cdv )
                        .setPositiveButton( R.string.dialog_ok, this )
                        .setNegativeButton( R.string.dialog_cancel, this )
                        .show();
                }
            } catch( Exception e ) {
                Log.e( TAG, "CmdDialog()", e );
            }
        }
        @Override
        public void onClick( DialogInterface idialog, int whichButton ) {
            if( whichButton == DialogInterface.BUTTON_POSITIVE )
                owner.executeToViewer( ctv.getText().toString(), bbc.isChecked() );
            idialog.dismiss();
        }
    }

    @Override
    protected void reSort() {
        if( items == null || items.length < 1 ) return;
        LsItemPropComparator comp = items[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items, comp );
    }
    
    /* --- ContentEngine --- */
    
    class ContentEngine extends Thread {
        private String file_path;
        private InputStream  is = null;
        private OutputStream os = null;
        private boolean mode_read = true;
        private boolean open_done = false;
        private boolean may_close = false;
        
        ContentEngine( String file_path_, boolean mode_read_ ) {
            file_path = file_path_;
            mode_read = mode_read_; 
        }

        @Override
        public void run() {
            setName( "ContentEngine " + ( mode_read ? "read" : "write" ) );
            OutputStreamWriter osw = null;
            BufferedReader     ebr = null;
            try {
                Process process = Runtime.getRuntime().exec( "su" );
                os = process.getOutputStream();
                ebr = new BufferedReader( new InputStreamReader( process.getErrorStream() ) );
                if( mode_read ) {
                    osw = new OutputStreamWriter( os );
                    is = process.getInputStream();
                    osw.write( "cat '" + file_path + "'\n" );
                    osw.flush();
                    for( int i = 0; i < 10; i++ ) {
                        if( is.available() > 0 ) break;
                        Thread.sleep( 100 );
                    }
                } else {
                    String cmd = "cat >'" + file_path + "'\n";
                    os.write( cmd.getBytes() );
                    os.flush();
                }
                synchronized( this ) {
                    open_done = true;
                }
                for( int i = 0; i < 20; i++ ) {
                    synchronized( this ) {
                        if( may_close ) break;
                        wait( 500 );
                    }
                }
                if( mode_read ) {
                    osw.write( "exit\n" );
                    osw.flush();
                } else {
Log.v( TAG, "Sending EOT byte" );
                    final int END_OF_TRANSMISSION = 4; 
                    os.write( '\n' );
                    os.write( END_OF_TRANSMISSION );
                    os.flush();
Log.v( TAG, "Sending the exit command" );
                    os.write( "\nexit\n".getBytes() );
                    os.flush();
                    Thread.sleep( 100 );
                }
Log.v( TAG, "Waitng the process exits" );
                process.waitFor();
Log.v( TAG, "The process has exited" );
                if( process.exitValue() != 0 ) {
                    Log.e( TAG, "Exit code " + process.exitValue() );
                }
                if( ebr.ready() ) {
                    String err_str = ebr.readLine();
                    if( err_str.trim().length() > 0 ) {
                        Log.e( TAG, "Error:\n" + err_str );
                    }
                }
            }
            catch( Exception e ) {
                Log.e( TAG, null, e );
            }
            finally {
                try {
                    if( osw != null ) osw.close();
                    if( ebr != null ) ebr.close();
                    if( is  != null ) is.close();
                } catch( IOException e ) {
                    e.printStackTrace();
                }
            }
        }
        
        public synchronized boolean waitUntilOpen() {
            try {
                for( int i = 0; i < 50; i++ ) {
                    if( open_done )
                        return true;
                    wait( 100 ); 
                }
            } catch( InterruptedException e ) {}
            return false;
        }
        public InputStream getInput() {
            return waitUntilOpen() ? is : null;
        }
        public OutputStream getOutput() {
            return waitUntilOpen() ? os : null;
        }
        
        public synchronized void close() {
            may_close = true;
            notify();
        }
        
    }    
    @Override
    public InputStream getContent( Uri u ) {
        try {
            if( u == null ) return null;
            String path = u.getPath();
            contentEngine = new ContentEngine( path, true );
            contentEngine.start();
            InputStream is = contentEngine.getInput();
            if( is == null ) 
                contentEngine.close();
            return is;
        } catch( Throwable e ) {
            Log.e( TAG, u.toString(), e );
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        try {
            if( u == null ) return null;
            String path = u.getPath();
            
            dst_f = new File( path );
            File root_f = ctx.getDir( "root", Context.MODE_PRIVATE );
            if( root_f == null )
                return null;
            tmp_f = new File( root_f, dst_f.getName() );
            return new FileOutputStream( tmp_f );
            
            
            
            /*
            contentEngine = new ContentEngine( path, false );
            contentEngine.start();
            OutputStream os = contentEngine.getOutput();
            if( os == null ) 
                contentEngine.close();
            return os;
            */
        } catch( Throwable e ) {
            Log.e( TAG, u.toString(), e );
        }
        return null;
    }
    
    
    @Override
    public void closeStream( Closeable s ) {
        if( s instanceof FileOutputStream ) {
            if( tmp_f == null || dst_f == null ) return;
            String command = "mv '" + tmp_f.getAbsolutePath() + "' '" + dst_f.getAbsolutePath() + "'"; 
            worker = new ExecEngine( ctx, workerHandler, null, command, true, 500 );
            worker.start();
            return;
        }
        if( contentEngine != null ) {
            contentEngine.close();
            contentEngine = null;
        }
    }
}