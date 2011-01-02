package com.ghostsq.commander;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;

import android.os.Handler;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ListView;
import android.widget.TextView;

public class RootAdapter extends CommanderAdapterBase {
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public final static String TAG = "RootAdapter";
    public final static int CHMOD_CMD = 36793;
    public  Uri uri = null;
    public  LsItem[] items = null;
    private int attempts = 0;

    public RootAdapter( Commander c, Uri d ) {
        super( c, SHOW_ATTR );
    }

    @Override
    public String getType() {
        return "root";
    }
    class ListEngine extends ExecEngine {
        private LsItem[] items_tmp;
        private String pass_back_on_done;
        private Uri src;
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
            try {
            	getList();
            }
            catch( Exception e ) {
                sh = "sh";
                // try again
                try {
                    getList();
                    sendProgress( commander.getContext().getString( R.string.no_root ), 
                            Commander.OPERATION_COMPLETED, pass_back_on_done );
                }
                catch( Exception e1 ) {
                    Log.e( TAG, "Exception even on 'sh' execution", e1 );
                    sendProgress( commander.getContext().getString( R.string.no_root ), 
                            Commander.OPERATION_FAILED, pass_back_on_done );
                }
            }
            catch( VerifyError e ) {
                sendProgress( "VerifyError " + e, Commander.OPERATION_FAILED, pass_back_on_done );
                Log.e( TAG, "VerifyError: ", e );
            }
            finally {
            	super.run();
            }
        }
        private void getList() throws Exception {
            String path = src.getPath();
            if( path == null ) path = SLS;
            parentLink = path == null || path.length() == 0 || path.equals( SLS ) ? SLS : "..";
            Process p = Runtime.getRuntime().exec( sh );
            DataOutputStream os = new DataOutputStream( p.getOutputStream() );
            DataInputStream  is = new DataInputStream( p.getInputStream() );
            DataInputStream  es = new DataInputStream( p.getErrorStream() );
            os.writeBytes( "ls -l " + path + "\n"); // execute command
            os.flush();
            for( int i=0; i< 10; i++ ) {
                if( isStopReq() ) 
                    throw new Exception();
                if( is.available() > 0 ) break;
                Thread.sleep( 50 );
            }
            if( is.available() <= 0 ) // may be an error may be not
                Log.w( TAG, "No output from the executed command" );
            ArrayList<LsItem>  array = new ArrayList<LsItem>();
            while( is.available() > 0 ) {
                if( isStopReq() ) 
                    throw new Exception();
                String ln = is.readLine();
                if( ln == null ) break;
                LsItem item = new LsItem( ln );
                if( item.isValid() ) {  // problem - if the item is a symlink - how to know its a dir or a file???
                        array.add( item );
                }
            }
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            if( p.exitValue() == 255 )
                Log.e( TAG, "Process.exitValue() returned 255" );
            int sz = array.size();
            items_tmp = new LsItem[sz];
            if( sz > 0 ) {
                array.toArray( items_tmp );
                LsItem.LsItemPropComparator comp = 
                    items_tmp[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
                Arrays.sort( items_tmp, comp );
            }
            String res_s = null;
            if( es.available() > 0 )
                res_s = es.readLine();
            sendProgress( res_s, Commander.OPERATION_COMPLETED, pass_back_on_done );
        }
    }
    @Override
    protected void onComplete( Engine engine ) {
        attempts = 0;
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
            uri = list_engine.getUri();
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
    public boolean isButtonActive( int brId ) {
        if( brId == R.id.F4 || brId == R.id.sz ) return false;
        return true;
    }

    @Override
    public void setIdentities( String name, String pass ) {
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri == null )
                tmp_uri = uri;
            if( tmp_uri == null )
                return false;
            if( worker != null ) {
                if( attempts++ < 2 ) {
                    commander.showInfo( "Busy..." );
                    return false;
                }
                if( worker.reqStop() ) { // that's not good.
                    Thread.sleep( 500 );      // will it end itself?
                    if( worker.isAlive() ) {
                        showMessage( "A worker thread is still alive and don't want to stop" );
                        return false;
                    }
                }
            }
            
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new ListEngine( commander.getContext(), handler, tmp_uri, pass_back_on_done );
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
        	if( to instanceof FSAdapter || to instanceof RootAdapter ) {
        	    Uri to_uri = to.getUri();
        	    if( to_uri != null ) {
        	        String to_path = to_uri.getPath(); 
    	        	if( to_path != null ) {
    		        	LsItem[] subItems = bitsToItems( cis );
    		        	if( subItems != null ) {
    		        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
    		                worker = new CopyFromEngine( commander.getContext(), handler, subItems, to_path, move );
    		                worker.start();
    		                return true;
    		        	}
    	        	}
        	    }
        	}
        	commander.notifyMe( new Commander.Notify( "Failed to proceed.", Commander.OPERATION_FAILED ) );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
        }
        return false;
    }

    
  class CopyFromEngine extends ExecEngine 
  {
	    private LsItem[] list;
	    private String   dest_folder;
	    private boolean  move;
	    private String   src_base_path;
	    CopyFromEngine( Context ctx, Handler h, LsItem[] list_, String dest, boolean move_ ) {
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
	    }
	    @Override
	    public void run() {
            int counter = 0;
            try {
                String bb = getBusyBox();
                Process p = Runtime.getRuntime().exec( sh );
                DataOutputStream os = new DataOutputStream( p.getOutputStream() );
                DataInputStream  es = new DataInputStream( p.getErrorStream() );
                int num = list.length;
                double conv = 100./(double)num;
                for( int i = 0; i < num; i++ ) {
                    LsItem f = list[i];
                    if( f == null ) continue;
                    String file_name = f.getName();
                    String full_name = src_base_path + file_name;
                    String to_exec;
                    String cmd = move ? " mv -f " : ( f.isDirectory() ? " cp -r " : " cp " );
                    to_exec = bb + cmd + full_name + " " + dest_folder + "\n";
                    Log.i( TAG, to_exec );
                    os.writeBytes( to_exec ); // execute command
                    os.flush();
                    Thread.sleep( 100 );
                    if( es.available() > 0 ) {
                        error( es.readLine() );
                        break;
                    }
                    if( stop || isInterrupted() ) {
                        error( "Canceled" );
                        break;
                    }
                    sendProgress( "'" + file_name + "'", (int)(i * conv) );
                    counter++;
                }
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
                if( p.exitValue() == 255 )
                    error( "Exit code 255" );
            }
            catch( Exception e ) {
                error( "Exception: " + e );
            }
	    	sendResult( Utils.getOpReport( counter, move ? "moved" : "copied" ) );
	        super.run();
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
            worker = new MkDirEngine( commander.getContext(), handler, new_name );
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
                String cmd = "mkdir " + full_name;
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
                worker = new DelEngine( commander.getContext(), handler, subItems );
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
            int counter = 0;
            try {
                Process p = Runtime.getRuntime().exec( sh );
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
                error( "Exception: " + e );
            }
    		sendResult( Utils.getOpReport( counter, "deleted" ) );
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
    public boolean receiveItems( String[] full_names, boolean move ) {
    	try {
            if( full_names == null || full_names.length == 0 ) {
            	commander.notifyMe( new Commander.Notify( "Nothing to copy", Commander.OPERATION_FAILED ) );
            	return false;
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyToEngine( commander.getContext(), handler, full_names, move, uri.getPath(), false );
            worker.start();
            return true;
		} catch( Exception e ) {
			commander.notifyMe( new Commander.Notify( "Exception: " + e, Commander.OPERATION_FAILED ) );
		}
		return false;
    }
    
    class CopyToEngine extends ExecEngine {
        String[] src_full_names;
        String   dest;
        boolean move = false;
        boolean quiet;
        
        CopyToEngine( Context ctx, Handler h, String[] list, boolean move_, String dest_, boolean quiet_ ) {
        	super( ctx, h );
        	src_full_names = list;
        	dest = dest_;
            move = move_;
            quiet = quiet_;
        }

        @Override
        public void run() {
            int counter = 0;
            try {
                String bb = getBusyBox();
                String cmd = move ? " mv " : " cp -r ";
                Process p = Runtime.getRuntime().exec( sh );
                DataOutputStream os = new DataOutputStream( p.getOutputStream() );
                DataInputStream  es = new DataInputStream( p.getErrorStream() );
                int num = src_full_names.length;
                double conv = 100./(double)num;
                for( int i = 0; i < num; i++ ) {
                    String full_name = src_full_names[i];
                    if( full_name == null ) continue;
                    String to_exec;
                    to_exec = bb + cmd + full_name + " " + dest + "\n";
                    Log.i( TAG, to_exec );
                    os.writeBytes( to_exec ); // execute command
                    os.flush();
                    Thread.sleep( 100 );
                    if( es.available() > 0 ) {
                        error( es.readLine() );
                        break;
                    }
                    if( stop || isInterrupted() ) {
                        error( "Canceled" );
                        break;
                    }
                    if( !quiet ) sendProgress( "'" + full_name + "'   ", (int)(i * conv) );
                    counter++;
                }
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
                if( p.exitValue() == 255 )
                    error( "Exit code 255" );
            }
            catch( Exception e ) {
                error( "Exception: " + e );
            }
            if( quiet )
                sendResult( null );
            else
                sendResult( Utils.getOpReport( counter, move ? "moved" : "copied" ) );
            super.run();
        }
    }
    
    @Override
    public boolean renameItem( int position, String newName ) {
        if( position <= 0 || position > items.length )
            return false;
        try {
            LsItem from = items[position - 1];
            String[] a = new String[1];
            a[0] = uri.getPath() + SLS + from.getName();
            String to = uri.getPath() + SLS + newName;
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyToEngine( commander.getContext(), handler, a, true, to, true );
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
    public int getCount() {
   	    return items != null ? items.length + 1 : 1;
    }

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
            menu.add( 0, CHMOD_CMD, 0, "chmod" );
        } catch( Exception e ) {
            Log.e( TAG, "populateContextMenu() " + e.getMessage(), e );
        }
    }    

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        if( CHMOD_CMD == command_id ) {
            if( isWorkerStillAlive() )
                return;
            LsItem[] items_todo = bitsToItems( cis );
            if( items_todo != null && items_todo.length > 0 && items_todo[0] != null )
                new ChmodDialog( commander.getContext(), items_todo[0] );
            else
                commander.showError( commander.getContext().getString( R.string.select_some ) );
        }
    }

    class ChmodDialog implements OnClickListener {
        private LsItem   item;
        private boolean  ur,  uw,  ux,  us,  gr,  gw,  gx,  gs,  or,  ow,  ox,  ot;
        private CheckBox urc, uwc, uxc, usc, grc, gwc, gxc, gsc, orc, owc, oxc, otc;
        private String   full_file_name;
        ChmodDialog( Context c, LsItem item_ ) {
            try {
                item = item_;
                String a = item.getAttr();
                if( a.length() < 10 ) return;
                full_file_name = (new File( uri.getPath(), item.getName() ) ).getAbsolutePath();                
                LayoutInflater factory = LayoutInflater.from( c );
                View cdv = factory.inflate( R.layout.chmod, null );
                if( cdv != null ) {
                    TextView fnv = (TextView)cdv.findViewById( R.id.file_name );
                    fnv.setText( full_file_name );
                    {
                        urc = (CheckBox)cdv.findViewById( R.id.UR );
                        ur = a.charAt( 1 ) == 'r';
                        if( ur ) urc.setChecked( true );
                        uwc = (CheckBox)cdv.findViewById( R.id.UW );
                        uw = a.charAt( 2 ) == 'w';
                        if( uw ) uwc.setChecked( true );
                        uxc = (CheckBox)cdv.findViewById( R.id.UX );
                        usc = (CheckBox)cdv.findViewById( R.id.US );
                        char uxl = a.charAt( 3 );
                        if( uxl == 'x' ) {
                            ux = true;
                            us = false;
                        } else 
                        if( uxl == 's' ) {
                            ux = true;
                            us = true;
                        } else if( uxl == 'S' ) {
                            ux = false;
                            us = true;
                        } else {
                            ux = false;
                            us = false;
                        }
                        if( ux ) uxc.setChecked( true );
                        if( us ) usc.setChecked( true );
                    } {
                        grc = (CheckBox)cdv.findViewById( R.id.GR );
                        gr = a.charAt( 4 ) == 'r';
                        if( gr ) grc.setChecked( true );
                        gwc = (CheckBox)cdv.findViewById( R.id.GW );
                        gw = a.charAt( 5 ) == 'w';
                        if( gw ) gwc.setChecked( true );
                        gxc = (CheckBox)cdv.findViewById( R.id.GX );
                        gsc = (CheckBox)cdv.findViewById( R.id.GS );
                        char gxl = a.charAt( 6 );
                        if( gxl == 'x' ) {
                            gx = true;
                            gs = false;
                        } else 
                        if( gxl == 's' ) {
                            gx = true;
                            gs = true;
                        } else 
                        if( gxl == 'S' ) {
                            gx = false;
                            gs = true;
                        } else {
                            gx = false;
                            gs = false;
                        }
                        if( gx ) gxc.setChecked( true );
                        if( gs ) gsc.setChecked( true );
                    } {
                        orc = (CheckBox)cdv.findViewById( R.id.OR );
                        or = a.charAt( 7 ) == 'r';
                        if( or ) orc.setChecked( true );
                        owc = (CheckBox)cdv.findViewById( R.id.OW );
                        ow = a.charAt( 8 ) == 'w';
                        if( ow ) owc.setChecked( true );
                        oxc = (CheckBox)cdv.findViewById( R.id.OX );
                        otc = (CheckBox)cdv.findViewById( R.id.OT );
                        char otl = a.charAt( 9 );
                        if( otl == 'x' ) {
                            ox = true;
                            ot = false;
                        } else 
                        if( otl == 't' ) {
                            ox = true;
                            ot = true;
                        } else 
                        if( otl == 'T' ) {
                            ox = false;
                            ot = true;
                        } else {
                            ox = false;
                            ot = false;
                        }
                        if( ox ) oxc.setChecked( true );
                        if( ot ) otc.setChecked( true );
                    }
                    new AlertDialog.Builder( c )
                        .setTitle( "chmod" )
                        .setView( cdv )
                        .setPositiveButton( R.string.dialog_ok, this )
                        .setNegativeButton( R.string.dialog_cancel, this )
                        .show();
                }
            } catch( Exception e ) {
                Log.e( TAG, "ChmodDialog()", e );
            }
        }
        @Override
        public void onClick( DialogInterface idialog, int whichButton ) {
            if( whichButton == DialogInterface.BUTTON_POSITIVE ) {
                if( isWorkerStillAlive() )
                    commander.notifyMe( new Commander.Notify( "Busy", Commander.OPERATION_FAILED ) );
                else {
                    StringBuilder a = new StringBuilder();
                    boolean nur = urc.isChecked(); 
                    boolean nuw = uwc.isChecked(); 
                    boolean nux = uxc.isChecked(); 
                    boolean nus = usc.isChecked(); 
                    boolean ngr = grc.isChecked(); 
                    boolean ngw = gwc.isChecked(); 
                    boolean ngx = gxc.isChecked(); 
                    boolean ngs = gsc.isChecked(); 
                    boolean nor = orc.isChecked(); 
                    boolean now = owc.isChecked(); 
                    boolean nox = oxc.isChecked(); 
                    boolean not = otc.isChecked();
                    
                    if( nur != ur ) {
                        a.append( 'u' );
                        a.append( nur ? '+' : '-' );
                        a.append( 'r' );  
                    }
                    if( nuw != uw ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'u' );
                        a.append( nuw ? '+' : '-' );
                        a.append( 'w' );  
                    }
                    if( nux != ux ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'u' );
                        a.append( nux ? '+' : '-' );
                        a.append( 'x' );  
                    }
                    if( nus != us ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'u' );
                        a.append( nus ? '+' : '-' );
                        a.append( 's' );  
                    }
                    
                    if( ngr != gr ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'g' );
                        a.append( ngr ? '+' : '-' );
                        a.append( 'r' );  
                    }
                    if( ngw != gw ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'g' );
                        a.append( ngw ? '+' : '-' );
                        a.append( 'w' );  
                    }
                    if( ngx != gx ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'g' );
                        a.append( ngx ? '+' : '-' );
                        a.append( 'x' );  
                    }
                    if( ngs != gs ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'g' );
                        a.append( ngs ? '+' : '-' );
                        a.append( 's' );  
                    }
                    
                    if( nor != or ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'o' );
                        a.append( nor ? '+' : '-' );
                        a.append( 'r' );  
                    }
                    if( now != ow ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'o' );
                        a.append( now ? '+' : '-' );
                        a.append( 'w' );  
                    }
                    if( nox != ox ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( 'o' );
                        a.append( nox ? '+' : '-' );
                        a.append( 'x' );  
                    }
                    if( not != ot ) {
                        if( a.length() > 0 )
                            a.append( ',' );
                        a.append( not ? '+' : '-' );
                        a.append( 't' );  
                    }
                    if( a.length() > 0 ) {
                        a.append( " " );
                        a.append( full_file_name ); 
                        worker = new ChmodEngine( commander.getContext(), handler, a.toString() );
                        worker.start();
                    }
                }
            }
            idialog.dismiss();
        }
    }

    class ChmodEngine extends ExecEngine {
        String params;
        ChmodEngine( Context ctx, Handler h, String params_ ) {
            super( ctx, h );
            params = params_;
        }
        @Override
        public void run() {
            try {
                String cmd = "chmod " + params;
                execute( cmd, true, 100 );
            } catch( Exception e ) {
                error( "Exception: " + e );
            }
            sendResult( errMsg != null ? "Were tried to 'chmod " + params + "'" : null );
        }
    }
}
