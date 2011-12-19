package com.ghostsq.commander.adapters;

import java.io.File;
import java.sql.Date;
import java.util.Arrays;
import java.util.List;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;

public class AppsAdapter extends CommanderAdapterBase {
    private final static String TAG = "AppsAdapter";
    public static final String DEFAULT_LOC = "apps:";
// Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public final PackageManager pm = ctx.getPackageManager();
    public  ApplicationInfo[] appInfos = null;
    private final int ACTIVITIES = 0, PROVIDERS = 1; 
    private final String[]          compTypes = { "Activities", "Providers" };
    private ActivityInfo[]          actInfos = null;
    private ProviderInfo[]          prvInfos = null;
    
    
    private Uri uri;
    
    public AppsAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR );
        parentLink = PLS;
    }
    @Override
    public int getType() {
        return CA.APPS;
    }
    
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
        return mode;
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
            appInfos = list_engine.getItems();
            numItems = appInfos != null ? appInfos.length : 0;
            notifyDataSetChanged();
        }
    }
    
    @Override
    public String toString() {
        return uri.toString();
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
    public boolean readSource( Uri tmp_uri, String pbod ) {
        try {
            appInfos = null;
            actInfos = null;
            prvInfos = null;
            if( reader != null ) {
                if( reader.reqStop() ) { // that's not good.
                    Thread.sleep( 500 );      // will it end itself?
                    if( reader.isAlive() ) {
                        Log.e( TAG, "Busy!" );
                        return false;
                    }
                }
            }
            if( tmp_uri != null )
                uri = tmp_uri;
            String a = uri.getAuthority(); 
            if( a == null || a.length() == 0 ) {    // enumerate the applications
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                reader = new ListEngine( readerHandler, pbod );
                reader.start();
                return true;
            }
            
            String path = uri.getPath();
            if( path == null || path.length() <= 1 ) {
                numItems = compTypes.length + 1;
                commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                return true;
            }
            else {
                List<String> ps = uri.getPathSegments();
                if( ps != null && ps.size() == 1 ) {
                    PackageInfo pi = pm.getPackageInfo( a, PackageManager.GET_ACTIVITIES | 
                                                           PackageManager.GET_PROVIDERS );
                    if( compTypes[ACTIVITIES].equals( ps.get( 0 ) ) ) {
                        actInfos = pi.activities != null ? pi.activities : new ActivityInfo[0];
                        numItems = actInfos.length + 1;
                        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                        return true;
                    } else if( compTypes[PROVIDERS].equals( ps.get( 0 ) ) ) {
                        prvInfos = pi.providers != null ? pi.providers : new ProviderInfo[0];
                        numItems = prvInfos.length + 1;
                        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                        return true;
                    } 
                }
            }
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
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean c ) {
        return notErr();
    }
    
    @Override
    public void openItem( int position ) {
        if( appInfos != null  ) {
            if( position == 0 ) {
                commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null );
            }
            else if( position <= appInfos.length ) {
                ApplicationInfo ai = appInfos[position - 1];
                commander.Navigate( uri.buildUpon().authority( ai.packageName ).build(), null );
            }
        } else if( actInfos != null ) {
            if( position == 0 ) {
                commander.Navigate( uri.buildUpon().path( null ).build(), null );
            }
            else if( position <= actInfos.length ) {
                // ???
            }
        } else if( prvInfos != null ) {
            if( position == 0 ) {
                commander.Navigate( uri.buildUpon().path( null ).build(), null );
            }
            else if( position <= prvInfos.length ) {
                // ???
            }
        } else {
            if( position == 0 ) {
                commander.Navigate( Uri.parse( DEFAULT_LOC ), null /* TODO */ );
            }
            else if( position <= compTypes.length ) {
                commander.Navigate( uri.buildUpon().path( compTypes[position - 1] ).build(), null );
            }
        }
    }

    @Override
    public String getItemName( int position, boolean full ) {
        if( position == 0 )
            return parentLink;
        if( appInfos != null ) {
            return position <= appInfos.length ? appInfos[position - 1].packageName : null;
        }
        if( actInfos != null ) {
            return position <= actInfos.length ? actInfos[position - 1].name : null;
        }
        if( prvInfos != null ) {
            return position <= prvInfos.length ? prvInfos[position - 1].toString() : null;
        }
        return position <= compTypes.length ? compTypes[position - 1] : null;
    }

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        if( position == 0 ) {
            item.name = parentLink;
            item.dir = true;
            return item;
        }
        item.name = "???";
        if( appInfos != null ) { 
            if( position >= 0 && position <= appInfos.length ) {
                ApplicationInfo ai = appInfos[position - 1];
                item.dir = false;
                item.name = ai.loadLabel( pm ).toString();
                item.size = -1;
                item.sel = false;
                
                File asdf = new File( ai.sourceDir );
                item.date = new Date( asdf.lastModified() );
                item.attr = ai.packageName;
                item.setThumbNail( ai.loadIcon( pm ) );
                item.thumb_is_icon = true;
            }
        }
        else if( actInfos != null ) {
            if( position <= actInfos.length ) {
                ActivityInfo ai = actInfos[position - 1];
                item.name = ai.loadLabel( pm ).toString(); 
                item.attr = ai.name;
                item.setThumbNail( ai.loadIcon( pm ) );
                item.thumb_is_icon = true;
            }
        }
        else if( prvInfos != null ) {
            if( position <= prvInfos.length ) {
                ProviderInfo pi = prvInfos[position - 1];
                item.name = pi.loadLabel( pm ).toString(); 
                item.attr = pi.name;
                item.setThumbNail( pi.loadIcon( pm ) );
                item.thumb_is_icon = true;
            }
        }
        else {
            if( position <= compTypes.length )
                item.name = compTypes[position - 1];
        }
        return item;
    }
}
