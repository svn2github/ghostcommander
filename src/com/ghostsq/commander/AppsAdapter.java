package com.ghostsq.commander;

import java.util.Arrays;
import java.util.List;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

public class AppsAdapter extends CommanderAdapterBase {
    private final static String TAG = "AppsAdapter";
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  ApplicationInfo[] items = null;
    public final PackageManager pm = commander.getContext().getPackageManager();
    
    public AppsAdapter( Commander c ) {
        super( c, DETAILED_MODE | NARROW_MODE | SHOW_ATTR );
    }
    @Override
    public String getType() {
        return "apps";
    }
    class ListEngine extends Engine {
        private ApplicationInfo[] items_tmp;
        public  String    pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
            super( h );
            pass_back_on_done = pass_back_on_done_;
        }
        public ApplicationInfo[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
                Init( null );
                List<ApplicationInfo> allApps = pm.getInstalledApplications( 0 );
                items_tmp = new ApplicationInfo[allApps.size()];
                allApps.toArray( items_tmp );
                if( ( mode & MODE_SORTING ) == SORT_NAME )
                    Arrays.sort( items_tmp, new ApplicationInfo.DisplayNameComparator( pm ) );
                sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
            }
            catch( Exception e ) {
                sendProgress( "Fail", Commander.OPERATION_FAILED, pass_back_on_done );
            }
            catch( OutOfMemoryError err ) {
                sendProgress( "Out Of Memory", Commander.OPERATION_FAILED, pass_back_on_done );
            }
            finally {
                super.run();
            }
        }
    }
    @Override
    protected void onReadComplete() {
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            items = list_engine.getItems();
            numItems = items != null ? items.length : 0;
            notifyDataSetChanged();
        }
    }
    
    @Override
    public String toString() {
        return "apps:";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return Uri.parse( toString() );
    }

    @Override
    public void setIdentities( String name, String pass ) {
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( reader != null ) {
                if( reader.reqStop() ) { // that's not good.
                    Thread.sleep( 500 );      // will it end itself?
                    if( reader.isAlive() ) {
                        Log.e( TAG, "Busy!" );
                        return false;
                    }
                }
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            reader = new ListEngine( readerHandler, pass_back_on_done );
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
        notErr();
    }
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        return notErr();
    }
        
    @Override
    public boolean createFile( String fileURI ) {
        return notErr();
    }

    @Override
    public void createFolder( String new_name ) {
        notErr();
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        return notErr();
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position >= 0 && position <= items.length ) {
            return items[position].packageName;
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( items == null || position < 0 || position > items.length )
            return;
        ApplicationInfo item = items[position];
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean c ) {
        return notErr();
    }

    private boolean notErr() {
        commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
        return false;
    }
    
    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "???";
        if( items != null && position >= 0 && position <= items.length ) {
            ApplicationInfo curItem;
            curItem = items[position];
            item.dir = false;
            item.name = curItem.loadLabel( pm ).toString();
            item.size = -1;
            item.sel = false;
            item.date = null;
            item.attr = curItem.packageName;
        }
        return item;
    }
}
