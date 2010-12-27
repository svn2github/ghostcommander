package com.ghostsq.commander;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;
import com.ghostsq.commander.LsItem.LsItemPropComparator;
import com.ghostsq.commander.RootAdapter.MkDirEngine;

import android.os.Handler;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

public class MountAdapter extends CommanderAdapterBase {
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public final static String TAG = "MountAdapter";
    public String sh = "su";
    public  Uri uri = null;
    private int attempts = 0;
    
    public class MountItem {
        private String  dev = "", mntp = "", type = "", opts = "", r1 = "", r2 = "";
        public MountItem( String string ) {
            String[] flds = string.split( " " );
            if( flds.length < 4 ) {
                dev = "???";
            }
            if( flds[1].equals( "on" ) && flds[3].equals( "type" ) ) {
                dev  = flds.length > 0 ? flds[0] : "";
                mntp = flds.length > 2 ? flds[2] : ""; 
                type = flds.length > 4 ? flds[4] : ""; 
                opts = flds.length > 5 ? flds[5] : "";
                
                if( opts.length() > 1 && opts.charAt( 0 ) == '(' && opts.charAt( opts.length()-1 ) == ')' )
                    opts = opts.substring( 1, opts.length()-1 );
            } else {
                dev  = flds.length > 0 ? flds[0] : "";
                mntp = flds.length > 1 ? flds[1] : ""; 
                type = flds.length > 2 ? flds[2] : ""; 
                opts = flds.length > 3 ? flds[3] : ""; 
                r1   = flds.length > 4 ? flds[4] : "";
                r2   = flds.length > 5 ? flds[5] : "";
            }
        }
        public boolean isValid() {
            return dev.length() > 0 && mntp.length() > 0;
        }
        public String getName() {
            return dev + " " + mntp;
        }
        public String getOptions() {
            return opts;
        }
        public String getRest() {
            return type + " " + opts + " " + r1 + " " + r2;
        }
    }
    
    public  MountItem[] items = null;

    public MountAdapter( Commander c ) {
        super( c, DETAILED_MODE | NARROW_MODE | TEXT_MODE | SHOW_ATTR | ATTR_ONLY );
    }

    private String getBusyBox() {
        Context conetxt = commander.getContext();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( conetxt );
        return sharedPref.getString( "busybox_path", "busybox" );
    }
    
    @Override
    public String getType() {
        return "mount";
    }
    class ListEngine extends Engine {
        private MountItem[] items_tmp;
        public  String pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
        	super( h );
        	pass_back_on_done = pass_back_on_done_;
        }
        public MountItem[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
                if( uri == null )
                    return;
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
            finally {
                super.run();
            }
        }
        
        private void getList() throws Exception {
            Process p = Runtime.getRuntime().exec( sh );
            DataOutputStream os = new DataOutputStream( p.getOutputStream() );
            DataInputStream  is = new DataInputStream( p.getInputStream() );
            DataInputStream  es = new DataInputStream( p.getErrorStream() );
            os.writeBytes( "mount\n"); // execute command
            os.flush();
            for( int i=0; i< 10; i++ ) {
                if( isStopReq() ) 
                    throw new Exception();
                if( is.available() > 0 ) break;
                Thread.sleep( 50 );
            }
            if( is.available() <= 0 ) // may be an error may be not
                Log.w( TAG, "No output from the executed command" );
            ArrayList<MountItem>  array = new ArrayList<MountItem>();
            while( is.available() > 0 ) {
                if( isStopReq() ) 
                    throw new Exception();
                String ln = is.readLine();
                if( ln == null ) break;
                MountItem item = new MountItem( ln );
                if( item.isValid() )
                    array.add( item );
            }
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            if( p.exitValue() == 255 )
                Log.e( TAG, "Process.exitValue() returned 255" );
            int sz = array.size();
            items_tmp = new MountItem[sz];
            if( sz > 0 )
                array.toArray( items_tmp );
            String res_s = null;
            if( es.available() > 0 )
                res_s = es.readLine();
            if( res_s != null && res_s.length() > 0 )
               Log.e( TAG, "Error on the 'ls' command: " + res_s );
            sendProgress( res_s, Commander.OPERATION_COMPLETED, pass_back_on_done );
        }        
        
    }
    @Override
    protected void onComplete( Engine engine ) {
        attempts = 0;
        if( engine instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)engine;
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
        return Uri.parse("mount://");
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        if( num <= 1 ) {
            menu.add( 0, Commander.OPEN, 0, "Remount" );
        }
    }    
    @Override
    public boolean isButtonActive( int brId ) {
        if( brId == R.id.F1 ||
            brId == R.id.F9 ||
            brId == R.id.F10 ) return true;
        return false;
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
            
            if( worker != null ) {
                Log.e( TAG, "Worker is busy" );
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
        commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
        return false;
    }
	    
	@Override
	public boolean createFile( String fileURI ) {
		commander.notifyMe( new Commander.Notify( "Operation is not supported.", 
		                        Commander.OPERATION_FAILED ) );
		return false;
	}

	@Override
    public void createFolder( String new_name ) {
	    commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
	}
    

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
        return false;
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position >= 0 && position <= items.length ) {
            return items[position].getName();
        }
        return null;
    }

    class RemountEngine extends Engine {
        MountItem mount;
        
        RemountEngine( Handler h, MountItem m ) {
            super( h );
            mount = m;
        }
        @Override
        public void run() {
            String cmd = null;
            try {
                String bb = getBusyBox();
                Process p = Runtime.getRuntime().exec( sh );
                DataOutputStream os = new DataOutputStream( p.getOutputStream() );
                DataInputStream  es = new DataInputStream( p.getErrorStream() );
                String o = mount.getOptions();
                if( o.indexOf( "rw" ) >= 0 )
                    o = o.replace( "rw", "ro" );
                else
                if( o.indexOf( "ro" ) >= 0 )
                    o = o.replace( "ro", "rw" );
                else {
                    error( "Don't know what to do." );
                    return;
                }
                cmd = " mount -o remount," + o + " " + mount.getName();
                String to_exec = bb + cmd + "\n";
                Log.i( TAG, to_exec );
                os.writeBytes( to_exec ); // execute the command
                os.flush();
                Thread.sleep( 500 );
                if( es.available() > 0 ) {
                    String err_msg = es.readLine(); 
                    error( err_msg );
                    Log.e( TAG, err_msg );
                }
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
                if( p.exitValue() == 255 )
                    Log.e( TAG, "Exit code 255" );
            }
            catch( Exception e ) {
                Log.e( TAG, "On remount, ", e );
                error( "Exception: " + e );
            }
            finally {
                super.run();
                sendResult( errMsg != null ? ( cmd == null ? "" : "Were tried to execute: '" + cmd + "'") : "Success!" );
            }
        }
    }
    
    @Override
    public void openItem( int position ) {
        if( items == null || position < 0 || position > items.length )
            return;
        MountItem item = items[position];
        if( isWorkerStillAlive() )
            commander.notifyMe( new Commander.Notify( "Busy", Commander.OPERATION_FAILED ) );
        else {
            worker = new RemountEngine( handler, item );
            worker.start();
        }
    }

    @Override
    public boolean receiveItems( String[] full_names, boolean move ) {
        commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
        return false;
    }
    
    @Override
    public boolean renameItem( int position, String newName ) {
        commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
        return false;
    }

    /*
     * BaseAdapter implementation
     */
    @Override
    public int getCount() {
   	    return items != null ? items.length : 0;
    }

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "???";
        if( items != null && position >= 0 && position <= items.length ) {
            MountItem curItem;
            curItem = items[position];
            item.dir = false;
            item.name = curItem.getName();
            item.size = -1;
            item.sel = false;
            item.date = null;
            item.attr = curItem.getRest();
        }
        return item;
    }
}
