package com.ghostsq.commander.root;

import android.os.Handler;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.root.MountsListEngine;
import com.ghostsq.commander.root.MountsListEngine.MountItem;

public class MountAdapter extends CommanderAdapterBase {
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public final static String TAG = "MountAdapter";
    public static final String DEFAULT_LOC = "mount:";
    public  Uri uri = null;
    private int attempts = 0;
    
    
    public  MountItem[] items = null;

    public MountAdapter( Commander c ) {
        super( c, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
    }

    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            return super.setMode( mask, val );
        return mode;
    }
    
    @Override
    public int getType() {
        return CA.MNT;
    }
    
    @Override
    protected void onReadComplete() {
        attempts = 0;
        if( reader instanceof MountsListEngine ) {
            MountsListEngine list_engine = (MountsListEngine)reader;
            items = list_engine.getItems();
            numItems = items != null ? items.length + 1 : 0;
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
        return Uri.parse( DEFAULT_LOC );
    }
    @Override
    public void setUri( Uri uri ) {
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        if( num <= 1 ) {
            menu.add( 0, Commander.OPEN, 0, s( R.string.remount ) );
        }
    }    
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri != null )
                uri = tmp_uri;
            if( uri == null )
                return false;
            
            if( worker != null ) {
                Log.w( TAG, "Busy " + attempts );
                if( attempts++ < 2 ) {
                    commander.showInfo( s( R.string.busy ) );
                    return false;
                }
                if( reader.reqStop() ) { // that's not good.
                    Thread.sleep( 500 );      // will it end itself?
                    if( reader.isAlive() ) {
                        Log.e( TAG, "Busy!" );
                        return false;
                    }
                }
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            reader = new MountsListEngine( commander.getContext(), readerHandler, pass_back_on_done );
            reader.start();
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        commander.notifyMe( new Commander.Notify( s( R.string.fail ), Commander.OPERATION_FAILED ) );
        return false;
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
		commander.notifyMe( new Commander.Notify( s( R.string.not_supported ), Commander.OPERATION_FAILED ) );
	}
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        commander.notifyMe( new Commander.Notify( s( R.string.not_supported ), Commander.OPERATION_FAILED ) );
        return false;
    }
	    
	@Override
	public boolean createFile( String fileURI ) {
		commander.notifyMe( new Commander.Notify( s( R.string.not_supported ), Commander.OPERATION_FAILED ) );
		return false;
	}

    class CreateEngine extends ExecEngine {
        String pair;
        CreateEngine( Context ctx, Handler h, String pair_ ) {
            super( ctx, h );
            pair = pair_;
        }
        @Override
        public void run() {
            String cmd = null;
            try {
                cmd = "mount " + pair;
                execute( cmd, true, 500 );
            }
            catch( Exception e ) {
                Log.e( TAG, "mount, ", e );
                error( "Exception: " + e );
            }
            finally {
                super.run();
                sendResult( errMsg != null ? ( cmd == null ? "" : "Were tried to execute: '" + cmd + "'") : null );
            }
        }
    }
	
	@Override
    public void createFolder( String dev_mp_pair ) {
        if( isWorkerStillAlive() )
            commander.notifyMe( new Commander.Notify( s( R.string.busy ), Commander.OPERATION_FAILED ) );
        else {
            worker = new CreateEngine( commander.getContext(), workerHandler, dev_mp_pair );
            worker.start();
        }
	}

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        commander.notifyMe( new Commander.Notify( s( R.string.not_supported ), Commander.OPERATION_FAILED ) );
        return false;
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( position == 0 ) return SLS;
        if( items != null && position > 0 && position <= items.length ) {
            return items[position-1].getName();
        }
        return null;
    }

    @Override
    public void openItem( int position ) {
        try {
            if( position == 0 ) {
                commander.Navigate( Uri.parse( RootAdapter.DEFAULT_LOC ), null );
                return;
            }
            if( items == null || position < 0 || position > items.length )
                return;
            MountItem item = items[position-1];
            if( isWorkerStillAlive() )
                commander.notifyMe( new Commander.Notify( s( R.string.busy ), Commander.OPERATION_FAILED ) );
            else {
                worker = new RemountEngine( commander.getContext(), workerHandler, item );
                worker.start();
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        commander.notifyMe( new Commander.Notify( s( R.string.not_supported ), Commander.OPERATION_FAILED ) );
        return false;
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean c ) {
        commander.notifyMe( new Commander.Notify( s( R.string.not_supported ), Commander.OPERATION_FAILED ) );
        return false;
    }

    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        if( position == 0 ) {
            item = new Item();
            item.name = parentLink;
            item.dir = true;
        }
        else {
            item.name = "???";
            if( items != null && position > 0 && position <= items.length ) {
                MountItem curItem = items[position - 1];
                if( curItem != null ) {
                    String mp = curItem.getMountPoint();
                    if( mp != null ) {
                        if( "/system".equals( mp ) )
                            item.icon_id = R.drawable.application;
                        else if( mp.contains( "/sdcard" ) )
                            item.icon_id = R.drawable.sd;
                    }
                    item.dir = false;
                    item.name = curItem.getName();
                    item.size = -1;
                    item.sel = false;
                    item.date = null;
                    item.attr = curItem.getRest();
                }
            }
        }
        return item;
    }
    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = (Item)getItem( position );
        if( item == null ) return null;
        return getView( convertView, parent, item );
    }
}
