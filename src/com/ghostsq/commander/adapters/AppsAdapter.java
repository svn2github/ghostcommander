package com.ghostsq.commander.adapters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringBufferInputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.TextViewer;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.utils.Utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.LogPrinter;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

public class AppsAdapter extends CommanderAdapterBase {
    private final static String TAG = "AppsAdapter";
    public static final String DEFAULT_LOC = "apps:";
    public static final int LAUNCH_CMD = 9176, MANAGE_CMD = 7161, SHRCT_CMD = 2694;
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public final PackageManager     pm = ctx.getPackageManager();
    public  ApplicationInfo[]       appInfos = null;
    private PackageInfo             packageInfo = null;
    private final String MANAGE = "Manage", ACTIVITIES = "Activities", PROVIDERS = "Providers", SERVICES = "Services",  
                         MANIFEST = "Manifest", SHORTCUTS = "Shortcuts";
    private Item[]                  compItems = null;
    private ActivityInfo[]          actInfos = null;
    private ProviderInfo[]          prvInfos = null;
    private ServiceInfo[]           srvInfos = null;
    private List<ResolveInfo>       byAllIntents;
    private ResolveInfo[]           resInfos = null;

    
    
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
        if( ( mask & ( MODE_WIDTH /*| MODE_DETAILS | MODE_ATTR*/ ) ) == 0 )
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
            reSort();
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
            notifyDataSetChanged();     // to prevent the invalid state exception
            dirty = true;
            numItems = 1;
            compItems = null;            
            appInfos = null;
            actInfos = null;
            prvInfos = null;
            srvInfos = null;
            resInfos = null;
            packageInfo = null;
            super.setMode( ATTR_ONLY, 0 );
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
                ArrayList<Item> ial = new ArrayList<Item>(); 
                numItems = 1;
                Item manage_item = new Item( MANAGE );
                manage_item.setIcon( pm.getApplicationIcon( "com.android.settings" ) );
                manage_item.icon_id = R.drawable.and;
                ial.add( manage_item );
                Item manifest_item = new Item( MANIFEST );
                manifest_item.icon_id = R.drawable.xml;
                ial.add( manifest_item );
                PackageInfo pi = pm.getPackageInfo( a, PackageManager.GET_ACTIVITIES | 
                                                       PackageManager.GET_PROVIDERS | 
                                                       PackageManager.GET_SERVICES );
                if( pi.activities != null && pi.activities.length > 0 ) {
                    Item activities_item = new Item( ACTIVITIES );
                    activities_item.dir = true;
                    activities_item.size = pi.activities.length;
                    ial.add( activities_item );
                }
                if( pi.providers != null && pi.providers.length > 0 ) {
                    Item providers_item = new Item( PROVIDERS );
                    providers_item.dir = true;
                    providers_item.size = pi.providers.length;
                    ial.add( providers_item );
                }
                if( pi.services != null && pi.services.length > 0 ) {
                    Item services_item = new Item( SERVICES );
                    services_item.dir = true;
                    services_item.size = pi.services.length;
                    ial.add( services_item );
                }
                Item shortcuts_item = new Item( SHORTCUTS );
                shortcuts_item.dir = true;
                ial.add( shortcuts_item );
                
                // all items were created
                
                compItems = new Item[ial.size()];
                ial.toArray( compItems );
                numItems = compItems.length + 1;
                commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                return true;
            }
            else { // the URI path contains something
                super.setMode( 0, ATTR_ONLY );
                List<String> ps = uri.getPathSegments();
                if( ps != null && ps.size() >= 1 ) {
                    if( SHORTCUTS.equals( ps.get( 0 ) ) ) {
                        Intent[] ins = new Intent[2];  
                        ins[0] = new Intent( Intent.ACTION_MAIN );
                        ins[1] = new Intent( Intent.ACTION_CREATE_SHORTCUT );
                        resInfos = getResolvers( ins, a );
                        numItems = resInfos.length + 1;
                    } else {
                        PackageInfo pi = pm.getPackageInfo( a, PackageManager.GET_ACTIVITIES | 
                                                               PackageManager.GET_PROVIDERS | 
                                                               PackageManager.GET_SERVICES );
                        if( ACTIVITIES.equals( ps.get( 0 ) ) ) {
                            if( ps.size() >= 2 ) {
                                resInfos = getResolvers( ps.get( 1 ) );
                                if( resInfos != null )
                                    numItems = resInfos.length + 1;
                            } else {
                                actInfos = pi.activities != null ? pi.activities : new ActivityInfo[0];
                                reSort();
                                numItems = actInfos.length + 1;
                            }
                        } else if( PROVIDERS.equals( ps.get( 0 ) ) ) {
                            prvInfos = pi.providers != null ? pi.providers : new ProviderInfo[0];
                            numItems = prvInfos.length + 1;
                        } else if( SERVICES.equals( ps.get( 0 ) ) ) {
                            srvInfos = pi.services != null ? pi.services : new ServiceInfo[0];
                            numItems = srvInfos.length + 1;
                        }
                    }
                    commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                    return true;
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
    protected void reSort() {
        if( appInfos != null ) { 
            ApplicationInfoComparator comp = new ApplicationInfoComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
            Arrays.sort( appInfos, comp );
        }
        else if( actInfos != null ) {
            ActivityInfoComparator comp = new ActivityInfoComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
            Arrays.sort( actInfos, comp );
        }
        else if( prvInfos != null ) {
        }
        else {
        }
    }
    
    private final ResolveInfo[] getResolvers( Intent[] ins, String package_name ) {
        try {
            final int fl = PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER;
            List<ResolveInfo> tmp_list = new ArrayList<ResolveInfo>();
            for( Intent in : ins ) {
                List<ResolveInfo> resolves = pm.queryIntentActivities( in, fl );
                for( ResolveInfo res : resolves ) {
                    if( package_name.equals( res.activityInfo.applicationInfo.packageName ) )
                        tmp_list.add( res );
                }
            }
            if( tmp_list.size() > 0 ) {
                ResolveInfo[] ret = new ResolveInfo[tmp_list.size()];
                return tmp_list.toArray( ret );
            }
        } catch( Exception e ) {
            Log.e( TAG, "For: " + package_name, e );
        }
        return null;
    }    
    
    private final ResolveInfo[] getResolvers( String act_name ) {
        if( act_name == null ) return null;
        if( byAllIntents == null ) {
            byAllIntents = new ArrayList<ResolveInfo>();
            List<ResolveInfo> tmp_list;
            Intent in;
            final int fl = PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER;
            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_MAIN ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_PICK ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_INSERT ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_SEND ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_EDIT ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_SEARCH ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_WEB_SEARCH ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_VIEW, Uri.parse( "http:" ) ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_VIEW, Uri.parse( "mailto:" ) ), fl );
            byAllIntents.addAll( tmp_list );

            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_VIEW, Uri.parse( "ftp:" ) ), fl );
            byAllIntents.addAll( tmp_list );
            
            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_VIEW, Uri.parse( "file:" ) ), fl );
            byAllIntents.addAll( tmp_list );
            
            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_VIEW, Uri.parse( "content:" ) ), fl );
            byAllIntents.addAll( tmp_list );
            
            tmp_list = pm.queryIntentActivities( new Intent( Intent.ACTION_VIEW, Uri.parse( "ftp:" ) ), fl );
            byAllIntents.addAll( tmp_list );
            
            in = new Intent( Intent.ACTION_VIEW );
            in.setType("*/*");
            tmp_list = pm.queryIntentActivities( in, fl );
            byAllIntents.addAll( tmp_list );
            
            in = new Intent( Intent.ACTION_EDIT );
            in.setType("*/*");
            tmp_list = pm.queryIntentActivities( in, fl );
            byAllIntents.addAll( tmp_list );

            in = new Intent( Intent.ACTION_GET_CONTENT );
            in.setType("*/*");
            tmp_list = pm.queryIntentActivities( in, fl );
            byAllIntents.addAll( tmp_list );
            
        }
        List<ResolveInfo> act_res = new ArrayList<ResolveInfo>();
        for( int i = 0; i < byAllIntents.size(); i++ ) {
            ResolveInfo r = byAllIntents.get(i);
            if( act_name.equals( r.activityInfo.name ) ) {
                boolean exist = false;
                IntentFilter inf = r.filter;
                if( inf != null ) {
                    for( int j = 0; j < act_res.size(); j++ ) {
                        exist = compareIntentFilters( inf, act_res.get( j ).filter );
                        if( exist ) break;
                    }
                }
                if( !exist ) 
                    act_res.add( r );
            }
        }

        LogPrinter lp = new LogPrinter(Log.INFO, TAG );
        for( int j = 0; j < act_res.size(); j++ ) 
            act_res.get( j ).dump( lp, "RI/");

        ResolveInfo[] a = new ResolveInfo[act_res.size()];
        return act_res.toArray( a );
    }
    
    private static boolean compareIntentFilters( IntentFilter if1, IntentFilter if2 ) {
        try {
            int ca1 = if1.countActions();
            int ca2 = if2.countActions();
            if( ca1 != ca2 ) return false;
            for( int i = 0; i< ca1; i++ )
                if( !if1.getAction( i ).equals( if2.getAction( i ) ) ) return false;
            
            int cc1 = if1.countCategories();
            int cc2 = if2.countCategories();
            if( cc1 != cc2 ) return false;
            for( int i = 0; i< cc1; i++ )
                if( !if1.getCategory( i ).equals( if2.getCategory( i ) ) ) return false;
            
            int cd1 = if1.countDataTypes();
            int cd2 = if2.countDataTypes();
            if( cd1 != cd2 ) return false;
            for( int i = 0; i< cd1; i++ )
                if( !if1.getDataType( i ).equals( if2.getDataType( i ) ) ) return false;
            
            int cs1 = if1.countDataSchemes();
            int cs2 = if2.countDataSchemes();
            if( cs1 != cs2 ) return false;
            for( int i = 0; i< cs1; i++ )
                if( !if1.getDataScheme( i ).equals( if2.getDataScheme( i ) ) ) return false;
            return true;
        }
        catch( Exception e ) {
        }
        return false;
    }
    
    private static <T> ArrayList<T> bitsToItems( SparseBooleanArray cis, T[] items ) {
        try {
            if( items == null ) return null;
            ArrayList<T> al = new ArrayList<T>();
            for( int i = 0; i < cis.size(); i++ ) {
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        al.add( items[ k - 1 ] );
                }
            }
            return al;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToNames()'s Exception: " + e );
        }
        return null;
    }
    
    private String[] flagsStrs = {
        "SYSTEM",
        "DEBUGGABLE",
        "HAS_CODE",
        "PERSISTENT",
        "FACTORY_TEST",
        "ALLOW_TASK_REPARENTING",
        "ALLOW_CLEAR_USER_DATA",
        "UPDATED_SYSTEM_APP",
        "TEST_ONLY",
        "SUPPORTS_SMALL_SCREENS",
        "SUPPORTS_NORMAL_SCREENS",
        "SUPPORTS_LARGE_SCREENS",
        "RESIZEABLE_FOR_SCREENS",
        "SUPPORTS_SCREEN_DENSITIES",
        "VM_SAFE_MODE",
        "ALLOW_BACKUP",
        "KILL_AFTER_RESTORE",
        "RESTORE_ANY_VERSION",
        "EXTERNAL_STORAGE",
        "SUPPORTS_XLARGE_SCREENS",
        "NEVER_ENCRYPT",
        "FORWARD_LOCK",
        "CANT_SAVE_STATE" 
    };

    private final String getGroupName( int gid ) {
        switch( gid ) {
        case     0: return "root";           /* traditional unix root user */
        case  1000: return "system";         /* system server */
        case  1001: return "radio";          /* telephony subsystem, RIL */
        case  1002: return "bluetooth";      /* bluetooth subsystem */
        case  1003: return "graphics";       /* graphics devices */
        case  1004: return "input";          /* input devices */
        case  1005: return "audio";          /* audio devices */
        case  1006: return "camera";         /* camera devices */
        case  1007: return "log";            /* log devices */
        case  1008: return "compass";        /* compass device */
        case  1009: return "mount";          /* mountd socket */
        case  1010: return "wifi";           /* wifi subsystem */
        case  1011: return "adb";            /* android debug bridge (adbd) */
        case  1012: return "install";        /* group for installing packages */
        case  1013: return "media";          /* mediaserver process */
        case  1014: return "dhcp";           /* dhcp client */
        case  1015: return "sdcard_rw";      /* external storage write access */
        case  1016: return "vpn";            /* vpn system */
        case  1017: return "keystore";       /* keystore subsystem */
        case  1018: return "usb";            /* USB devices */
        case  1019: return "drm";            /* DRM server */
        case  1020: return "available";      /* available for use */
        case  1021: return "gps";            /* GPS daemon */
        case  1023: return "media_rw";       /* internal media storage write access */
        case  1024: return "mtp";            /* MTP USB driver access */
        case  1025: return "nfc";            /* nfc subsystem */
        case  1026: return "drmrpc";         /* group for drm rpc */                    
        case  2000: return "shell";          /* adb and debug shell user */
        case  2001: return "cache";          /* cache access */
        case  2002: return "diag";           /* access to diagnostic resources */
        case  3001: return "net_bt_admin";   /* bluetooth: create any socket */
        case  3002: return "net_bt";         /* bluetooth: create sco, rfcomm or l2cap sockets */
        case  3003: return "inet";           /* can create AF_INET and AF_INET6 sockets */
        case  3004: return "net_raw";        /* can create raw INET sockets */
        case  3005: return "net_admin";
        case  9998: return "misc";           /* access to misc storage */
        case  9999: return "nobody";
        default:    return gid >= 10000 ? "app_" + ( gid - 10000 ) : "?";
        }
    }    
    
    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        if( appInfos == null ) return;
        ArrayList<ApplicationInfo> al = bitsToItems( cis, appInfos );
        if( al == null || al.size() == 0 ) {
            notErr();
            return;
        }
        final String cs = ": ";
        StringBuffer sb = new StringBuffer( 1024 );
        for( int i = 0; i < al.size(); i++ ) {
            ApplicationInfo ai = al.get( i );
            String v = null;
            int    vc = 0;
            String size = null;
            String date = null;
            String flags = null;
            String gids = null;            
            try {
                PackageInfo pi = pm.getPackageInfo( ai.packageName, PackageManager.GET_GIDS );
                v = pi.versionName;
                vc = pi.versionCode;
                File asdf = new File( ai.sourceDir );
                date = getLocalDateTimeStr( new Date( asdf.lastModified() ) );
                size = Utils.getHumanSize( asdf.length() );
                StringBuffer fsb = new StringBuffer( 512 );
                int ff = ai.flags;
                for( int fi = 0; fi < flagsStrs.length; fi++ ) {
                    if( ( ( 1<<fi ) & ff ) != 0 ) {
                        if( fsb.length() > 0 )
                            fsb.append( " | " );
                        fsb.append( flagsStrs[fi] );
                    }
                }
                fsb.append( " (" ).append( Integer.toHexString( ff ) ).append( ")" );
                flags = fsb.toString();
                if( pi.gids != null && pi.gids.length > 0 ) {
                    StringBuffer gsb = new StringBuffer( 128 );
                    for( int gi = 0; gi < pi.gids.length; gi++ ) {
                        if( gi > 0 )
                            gsb.append( ", " );
                        int g = pi.gids[gi];
                        gsb.append( g ).append( "(" ).append( getGroupName( g ) ).append( ")" );
                    }
                    gids = gsb.toString();
                }
                
            } catch( Exception e ) {}
            sb.append( s( R.string.pkg_name ) ).append( cs ).append( ai.packageName );
            if( v != null ) 
              sb.append( "\n" ).append( s( R.string.version ) ).append( cs ).append( v );
            if( vc > 0 )
              sb.append( "\n" ).append( s( R.string.version_code ) ).append( cs ).append( vc );
            sb.append( "\n" ).append( s( R.string.target_sdk ) ).append( cs ).append( ai.targetSdkVersion );
            sb.append( "\n" ).append( "UID" ).append( cs ).append( ai.uid );
            if( gids != null )
              sb.append( "\n" ).append( "GIDs" ).append( cs ).append( gids );
            sb.append( "\n" ).append( s( R.string.location ) ).append( cs ).append( ai.sourceDir );
            if( date != null )
              sb.append( "\n" ).append( s( R.string.inst_date ) ).append( cs ).append( date );
            if( size  != null )
              sb.append( "\n" ).append( s( R.string.pkg_size ) ).append( cs ).append( size );
            sb.append( "\n" ).append( s( R.string.process ) ).append( cs ).append( ai.processName );
            if( ai.className != null )
              sb.append( "\n" ).append( s( R.string.app_class ) ).append( cs ).append( ai.className );
            if( ai.taskAffinity != null )
              sb.append( "\n" ).append( s( R.string.affinity ) ).append( cs ).append( ai.taskAffinity );
            if( ai.permission != null )
              sb.append( "\n" ).append( s( R.string.permission ) ).append( cs ).append( ai.permission );
            if( flags != null )
              sb.append( "\n\n" ).append( s( R.string.flags ) ).append( cs ).append( flags );
            sb.append( "\n" );
        }
        commander.notifyMe( new Commander.Notify( sb.toString(), Commander.OPERATION_COMPLETED, Commander.OPERATION_REPORT_IMPORTANT ) );
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( appInfos != null ) { 
            ArrayList<ApplicationInfo> al = bitsToItems( cis, appInfos );
            if( al == null || al.size() == 0 ) {
                commander.notifyMe( new Commander.Notify( s( R.string.copy_err ), Commander.OPERATION_FAILED ) );
                return false;
            }
            String[] paths = new String[al.size()];
            for( int i = 0; i < al.size(); i++ )
                paths[i] = al.get( i ).sourceDir;
            
            boolean ok = to.receiveItems( paths, MODE_COPY );
            if( !ok ) commander.notifyMe( new Commander.Notify( Commander.OPERATION_FAILED ) );
            return ok;
        }
        if( compItems != null ) {
            ArrayList<Item> il = bitsToItems( cis, compItems );
            if( il != null ) {
                for( int i = 0; i < il.size(); i++ ) {
                    if( MANIFEST.equals( il.get( i ).name ) ) {
                        try {
                            ApplicationInfo ai = pm.getApplicationInfo( uri.getAuthority(), 0 );
                            String m = extractManifest( ai.publicSourceDir, ai );
                            if( m != null && m.length() > 0 ) {
                                String tmp_fn = ai.packageName + ".xml";
                                FileOutputStream fos = ctx.openFileOutput( tmp_fn, Context.MODE_WORLD_WRITEABLE|Context.MODE_WORLD_READABLE);
                                if( fos != null ) {
                                    OutputStreamWriter osw = new OutputStreamWriter( fos );
                                    osw.write( m );
                                    osw.close();
                                    String[] paths = new String[1];
                                    paths[0] = ctx.getFileStreamPath( tmp_fn ).getAbsolutePath();
                                    boolean ok = to.receiveItems( paths, MODE_COPY );
                                    if( !ok ) commander.notifyMe( new Commander.Notify( Commander.OPERATION_FAILED ) );
                                    return ok;
                                }
                            }
                        } catch( Exception e ) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
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
        if( appInfos == null ) return notErr();
        ArrayList<ApplicationInfo> al = bitsToItems( cis, appInfos );
        if( al == null ) return false;
        for( int i = 0; i < al.size(); i++ ) {
            Intent in = new Intent( Intent.ACTION_DELETE, Uri.parse( "package:" + al.get( i ).packageName ) );
            commander.issue( in, 0 );
        }
        return true;
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
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            if( acmi.position > 0 ) { 
                if( appInfos != null ) {
                    String name = appInfos[acmi.position-1].loadLabel( pm ).toString();
                    menu.add( 0, LAUNCH_CMD, 0, ctx.getString( R.string.launch ) + " \"" + name + "\"" );
                    menu.add( 0, MANAGE_CMD, 0, MANAGE );
                }
                else if( actInfos != null ) {
                    menu.add( 0, SHRCT_CMD, 0, ctx.getString( R.string.shortcut ) );
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
        super.populateContextMenu( menu, acmi, num );
    }    

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        try {
            if( appInfos != null ) {
                ArrayList<ApplicationInfo> al = bitsToItems( cis, appInfos );
                if( al == null || al.size() == 0 ) return;
                ApplicationInfo ai = al.get(0);
                if( ai == null ) return;
                if( MANAGE_CMD == command_id ) {
                    managePackage( ai.packageName );
                    return;
                }
                if( LAUNCH_CMD == command_id ) {
                    Intent in = pm.getLaunchIntentForPackage( ai.packageName );
                    commander.issue( in, 0 );
                    return;
                }
            }
            else if( actInfos != null ) {
                ArrayList<ActivityInfo> al = bitsToItems( cis, actInfos );
                if( al == null || al.size() == 0 ) return;
                if( SHRCT_CMD == command_id ) {
                    for( int i = 0; i < al.size(); i++ ) {
                        ActivityInfo ai = al.get( i );
                        if( ai != null ) {
                            Bitmap ico = null;
                            Drawable drawable = ai.loadIcon( pm );
                            if( drawable instanceof BitmapDrawable ) {
                                BitmapDrawable bd = (BitmapDrawable)drawable;
                                ico = bd.getBitmap();
                            }
                            createDesktopShortcut( new ComponentName( ai.applicationInfo.packageName, ai.name ), 
                                                                      ai.loadLabel( pm ).toString(), ico );
                        }
                    }
                    return;
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "Can't do the command " + command_id, e );
        }
    }
    
    @Override
    public void openItem( int position ) {
        try {
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
                    commander.Navigate( uri.buildUpon().path( null ).build(), ACTIVITIES );
                }
                else if( position <= actInfos.length ) {
                    ActivityInfo act = actInfos[position-1];
                    if( act.exported )
                        commander.Navigate( uri.buildUpon().appendPath( act.name ).build(), null );
                    else
                        commander.showInfo( s( R.string.not_exported ) );
                }
            } else if( prvInfos != null || srvInfos != null ) {
                if( position == 0 ) {
                    commander.Navigate( uri.buildUpon().path( null ).build(), PROVIDERS );
                }
                else if( position <= prvInfos.length ) {
                    // ???
                }
            } else if( resInfos != null ) {
                if( position == 0 ) {
                    List<String> paths = uri.getPathSegments();
                    if( paths == null )
                        commander.Navigate( uri.buildUpon().path( null ).build(), null );
                    String p = paths.size() > 1 ? paths.get( paths.size()-2 ) : null; 
                    String n = paths.get( paths.size()-1 );
                    commander.Navigate( uri.buildUpon().path( p ).build(), n );
                }
                else if( position <= resInfos.length ) {
                    ResolveInfo  ri = resInfos[position - 1];
                    ActivityInfo ai = ri.activityInfo;
                    if( ri.filter.hasAction( Intent.ACTION_CREATE_SHORTCUT ) ) {
                        Intent in = new Intent( Intent.ACTION_CREATE_SHORTCUT );
                        in.setComponent( new ComponentName( ai.packageName, ai.name ) );
                        commander.issue( in, R.id.create_shortcut );
                    }
                    else if( ri.filter.hasAction( Intent.ACTION_MAIN ) ) {
                        Intent in = new Intent(Intent.ACTION_MAIN );
                        in.setComponent( new ComponentName( ai.packageName, ai.name ) );
                        commander.issue( in, 0 );
                        /*
                        Bitmap ico = null;
                        Drawable drawable = ai.loadIcon( pm );
                        if( drawable instanceof BitmapDrawable ) {
                            BitmapDrawable bd = (BitmapDrawable)drawable;
                            ico = bd.getBitmap();
                        }
                        createDesktopShortcut( new ComponentName( ai.packageName, ai.name ), 
                                ai.loadLabel( pm ).toString(), ico );
                        */
                    }
                }
            } else {
                if( position == 0 ) {
                    commander.Navigate( Uri.parse( DEFAULT_LOC ), uri.getAuthority() );
                    return;
                }
                String name = getItemName( position, false );
                if( MANAGE.equals( name ) ) {
                    managePackage( uri.getAuthority() );    
                }
                else if( MANIFEST.equals( name ) ) {
                    String p = uri.getAuthority();
                    ApplicationInfo ai = pm.getApplicationInfo( p, 0 );
                    String m = extractManifest( ai.publicSourceDir, ai );
                    if( m != null ) {
                        Intent in = new Intent( ctx, TextViewer.class );
                        in.setData( Uri.parse( "string:" ) );
                        Bundle b = new Bundle();
                        b.putString( TextViewer.STRKEY, m );
                        in.putExtra( TextViewer.STRKEY, b );
                        commander.issue( in, 0 );
                    }
                }
                else 
                    commander.Navigate( uri.buildUpon().path( name ).build(), null );
            }
        } catch( Exception e ) {
            Log.e( TAG, uri.toString() + " " + position, e );
        }
    }

    private final void managePackage( String p ) {
        try {
            Intent in = new Intent( Intent.ACTION_VIEW );
            in.setClassName( "com.android.settings", "com.android.settings.InstalledAppDetails" );
            in.putExtra( "com.android.settings.ApplicationPkgName", p );
            in.putExtra( "pkg", p );

            List<ResolveInfo> acts = pm.queryIntentActivities( in, 0 );
            if( acts.size( ) > 0 )
                commander.issue( in, 0 );
            else {
                in = new Intent( "android.settings.APPLICATION_DETAILS_SETTINGS", Uri.fromParts( "package", p, null ) );
                acts = pm.queryIntentActivities( in, 0 );
                if( acts.size( ) > 0 )
                    commander.issue( in, 0 );
                else {
                    Log.e( TAG, "Failed to resolve activity for InstalledAppDetails" );
                }
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( position < 0 )
            return null;
        if( position == 0 )
            return parentLink;
        try {
            int idx = position - 1;
            if( appInfos != null ) {
                return position <= appInfos.length ? appInfos[idx].packageName : null;
            }
            if( compItems != null ) {
                return position <= compItems.length ? compItems[idx].name : null;
            }
            if( actInfos != null ) {
                return position <= actInfos.length ? actInfos[idx].name : null;
            }
            if( prvInfos != null ) {
                return position <= prvInfos.length ? prvInfos[idx].toString() : null;
            }
            if( srvInfos != null ) {
                return position <= srvInfos.length ? srvInfos[idx].toString() : null;
            }
            if( resInfos != null ) {
                return position <= resInfos.length ? resInfos[idx].toString() : null;
            }
        }
        catch( Exception e ) {
            Log.e( TAG, "pos=" + position, e );
        }
        return null;
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
                item.sel = false;
                
                File asdf = new File( ai.sourceDir );
                item.date = new Date( asdf.lastModified() );
                item.size = asdf.length();
                item.attr = ai.packageName;
                item.setIcon( ai.loadIcon( pm ) );
            }
        }
        else if( actInfos != null ) {
            if( position <= actInfos.length ) {
                ActivityInfo ai = actInfos[position - 1];
                item.name = ai.loadLabel( pm ).toString(); 
                item.attr = ai.name;
                item.setIcon( ai.loadIcon( pm ) );
            }
        }
        else if( prvInfos != null ) {
            if( position <= prvInfos.length ) {
                ProviderInfo pi = prvInfos[position - 1];
                item.name = pi.loadLabel( pm ).toString(); 
                item.attr = pi.name;
                item.setIcon( pi.loadIcon( pm ) );
            }
        }
        else if( srvInfos != null ) {
            if( position <= srvInfos.length ) {
                ServiceInfo si = srvInfos[position - 1];
                item.name = si.loadLabel( pm ).toString(); 
                item.attr = si.name;
                item.setIcon( si.loadIcon( pm ) );
            }
        }
        else if( resInfos != null ) {
            try {
                if( position <= resInfos.length ) {
                    ResolveInfo ri = resInfos[position - 1];
                    IntentFilter inf = ri.filter;
                    if( inf != null ) {
                        if( ri.filter.hasAction( Intent.ACTION_CREATE_SHORTCUT ) ||
                            ri.filter.hasAction( Intent.ACTION_MAIN ) ) {
                            ActivityInfo ai = ri.activityInfo;
                            item.name = ai.loadLabel( pm ).toString(); 
                            item.attr = ai.name;
                            item.setIcon( ai.loadIcon( pm ) );
                        }
                        else {
                            String action = inf.getAction( 0 );
                            item.name = action != null ? action : inf.toString();
                            StringBuilder sb = new StringBuilder( 128 );
                            int n = inf.countDataTypes();
                            if( n > 0 ) {
                                sb.append( "types=" );
                                for( int i = 0; i< n; i++ ) {
                                    if( i != 0 )
                                        sb.append( ", " );
                                    String dt = inf.getDataType( i ); 
                                    sb.append( dt );
                                }
                                sb.append( "; " );
                            }
                            n = inf.countCategories();
                            if( n > 0 ) {
                                sb.append( "categories=" );
                                for( int i = 0; i< n; i++ ) {
                                    if( i != 0 )
                                        sb.append( ", " );
                                    String ct = inf.getCategory( i ); 
                                    sb.append( ct );
                                }
                                sb.append( "; " );
                            }
                            
                            n = inf.countDataSchemes();
                            if( n > 0 ) {
                                sb.append( "schemes=" );
                                for( int i = 0; i< n; i++ ) {
                                    if( i != 0 )
                                        sb.append( ", " );
                                    String ds = inf.getDataScheme( i ); 
                                    sb.append( ds );
                                }
                            }
                            item.attr = sb.toString();
                        }
                    }
                    else {
                        item.name = ri.loadLabel( pm ).toString();
                        item.attr = ri.toString();
                    }
                    item.setIcon( ri.loadIcon( pm ) );
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "pos=" + position, e );
            }
        }
        else {
            if( position <= compItems.length )
                return compItems[position-1];
        }
        return item;
    }

    @Override
    protected int getPredictedAttributesLength() {
        return 36;   // "com.softwaremanufacturer.productname"
    }

    private class ApplicationInfoComparator implements Comparator<ApplicationInfo> {
        int     type;
        boolean ascending;
        ApplicationInfo.DisplayNameComparator aidnc;
        
        public ApplicationInfoComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            aidnc = new ApplicationInfo.DisplayNameComparator( pm );
            type = type_;
            ascending = ascending_;
        }
        @Override
        public int compare( ApplicationInfo ai1, ApplicationInfo ai2 ) {
            int ext_cmp = 0;
            try {
                switch( type ) {
                case CommanderAdapter.SORT_EXT:
                    if( ai1.packageName != null )
                        ext_cmp = ai1.packageName.compareTo( ai2.packageName );
                    break;
                case CommanderAdapter.SORT_SIZE:  {
                        File asdf1 = new File( ai1.sourceDir );
                        File asdf2 = new File( ai2.sourceDir );
                        ext_cmp = asdf1.length() - asdf2.length() < 0 ? -1 : 1;
                    }
                    break;
                case CommanderAdapter.SORT_DATE: {
                        File asdf1 = new File( ai1.sourceDir );
                        File asdf2 = new File( ai2.sourceDir );
                        ext_cmp = asdf1.lastModified() - asdf2.lastModified() < 0 ? -1 : 1;
                    }
                    break;
                }
                if( ext_cmp == 0 )
                    ext_cmp = aidnc.compare( ai1, ai2 );
            } catch( Exception e ) {
            }
            return ascending ? ext_cmp : -ext_cmp;
        }
    }

    private class ActivityInfoComparator implements Comparator<ActivityInfo> {
        private int     type;
        private boolean ascending;
        public  final PackageManager pm_;
        
        public ActivityInfoComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            pm_ = pm;
            type = type_;
            ascending = ascending_;
        }
        @Override
        public int compare( ActivityInfo ai1, ActivityInfo ai2 ) {
            int ext_cmp = 0;
            try {
                switch( type ) {
                case CommanderAdapter.SORT_EXT:
                    if( ai1.packageName != null )
                        ext_cmp = ai1.name.compareTo( ai2.name );
                    break;
                }
                if( ext_cmp == 0 ) {
                    String cn1 = ai1.loadLabel( pm_ ).toString();
                    String cn2 = ai2.loadLabel( pm_ ).toString();
                    ext_cmp = cn1.compareTo( cn2 );
                }
            } catch( Exception e ) {
            }
            return ascending ? ext_cmp : -ext_cmp;
        }
    }
    
    private final String extractManifest( String zip_path, ApplicationInfo ai ) {
        try {
            if( zip_path == null ) return null;
            ZipFile  zip = new ZipFile( zip_path );
            ZipEntry entry = zip.getEntry( "AndroidManifest.xml" );
            if( entry != null ) {
                InputStream is = zip.getInputStream( entry );
                if( is != null ) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream( (int)entry.getSize() );
                    byte[] buf = new byte[4096];
                    int n;
                    while( ( n = is.read( buf ) ) != -1 )
                        baos.write( buf, 0, n );
                    is.close();
                    return decompressXML( baos.toByteArray(), ai != null ? pm.getResourcesForApplication( ai ) : null );
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }

    // http://stackoverflow.com/questions/2097813/how-to-parse-the-androidmanifest-xml-file-inside-an-apk-package
    // decompressXML -- Parse the 'compressed' binary form of Android XML docs 
    // such as for AndroidManifest.xml in .apk files
    private final static int endDocTag = 0x00100101;
    private final static int startTag =  0x00100102;
    private final static int endTag =    0x00100103;
    private final String decompressXML( byte[] xml, Resources rr ) {
        StringBuffer xml_sb = new StringBuffer( 8192 ); 
        // Compressed XML file/bytes starts with 24x bytes of data,
        // 9 32 bit words in little endian order (LSB first):
        //   0th word is 03 00 08 00
        //   3rd word SEEMS TO BE:  Offset at then of StringTable
        //   4th word is: Number of strings in string table
        // WARNING: Sometime I indiscriminently display or refer to word in 
        //   little endian storage format, or in integer format (ie MSB first).
        int numbStrings = LEW(xml, 4*4);
        
        // StringIndexTable starts at offset 24x, an array of 32 bit LE offsets
        // of the length/string data in the StringTable.
        int sitOff = 0x24;  // Offset of start of StringIndexTable
        
        // StringTable, each string is represented with a 16 bit little endian 
        // character count, followed by that number of 16 bit (LE) (Unicode) chars.
        int stOff = sitOff + numbStrings*4;  // StringTable follows StrIndexTable
        
        // XMLTags, The XML tag tree starts after some unknown content after the
        // StringTable.  There is some unknown data after the StringTable, scan
        // forward from this point to the flag for the start of an XML start tag.
        int xmlTagOff = LEW(xml, 3*4);  // Start from the offset in the 3rd word.
        // Scan forward until we find the bytes: 0x02011000(x00100102 in normal int)
        for (int ii=xmlTagOff; ii<xml.length-4; ii+=4) {
          if (LEW(xml, ii) == startTag) { 
            xmlTagOff = ii;  break;
          }
        } // end of hack, scanning for start of first start tag
        
        // XML tags and attributes:
        // Every XML start and end tag consists of 6 32 bit words:
        //   0th word: 02011000 for startTag and 03011000 for endTag 
        //   1st word: a flag?, like 38000000
        //   2nd word: Line of where this tag appeared in the original source file
        //   3rd word: FFFFFFFF ??
        //   4th word: StringIndex of NameSpace name, or FFFFFFFF for default NS
        //   5th word: StringIndex of Element Name
        //   (Note: 01011000 in 0th word means end of XML document, endDocTag)
        
        // Start tags (not end tags) contain 3 more words:
        //   6th word: 14001400 meaning?? 
        //   7th word: Number of Attributes that follow this tag(follow word 8th)
        //   8th word: 00000000 meaning??
        
        // Attributes consist of 5 words: 
        //   0th word: StringIndex of Attribute Name's Namespace, or FFFFFFFF
        //   1st word: StringIndex of Attribute Name
        //   2nd word: StringIndex of Attribute Value, or FFFFFFF if ResourceId used
        //   3rd word: Flags?
        //   4th word: str ind of attr value again, or ResourceId of value
        
        // TMP, dump string table to tr for debugging
        //tr.addSelect("strings", null);
        //for (int ii=0; ii<numbStrings; ii++) {
        //  // Length of string starts at StringTable plus offset in StrIndTable
        //  String str = compXmlString(xml, sitOff, stOff, ii);
        //  tr.add(String.valueOf(ii), str);
        //}
        //tr.parent();
        
        // Step through the XML tree element tags and attributes
        int off = xmlTagOff;
        int indent = 0;
        int startTagLineNo = -2;
        while( off < xml.length ) {
          int tag0 = LEW(xml, off);
          //int tag1 = LEW(xml, off+1*4);
          int lineNo = LEW(xml, off+2*4);
          //int tag3 = LEW(xml, off+3*4);
          int nameNsSi = LEW(xml, off+4*4);
          int nameSi = LEW(xml, off+5*4);
        
          if (tag0 == startTag) { // XML START TAG
            int tag6 = LEW(xml, off+6*4);  // Expected to be 14001400
            int numbAttrs = LEW(xml, off+7*4);  // Number of Attributes to follow
            //int tag8 = LEW(xml, off+8*4);  // Expected to be 00000000
            off += 9*4;  // Skip over 6+3 words of startTag data
            String name = compXmlString(xml, sitOff, stOff, nameSi);
            //tr.addSelect(name, null);
            startTagLineNo = lineNo;
        
            // Look for the Attributes
            StringBuffer sb = new StringBuffer();
            for (int ii=0; ii<numbAttrs; ii++) {
              int attrNameNsSi = LEW(xml, off);  // AttrName Namespace Str Ind, or FFFFFFFF
              int attrNameSi = LEW(xml, off+1*4);  // AttrName String Index
              int attrValueSi = LEW(xml, off+2*4); // AttrValue Str Ind, or FFFFFFFF
              int attrFlags = LEW(xml, off+3*4);  
              int attrResId = LEW(xml, off+4*4);  // AttrValue ResourceId or dup AttrValue StrInd
              off += 5*4;  // Skip over the 5 words of an attribute
        
              String attrName = compXmlString(xml, sitOff, stOff, attrNameSi);
              String attrValue= null;
              if( attrValueSi != -1 )
                  attrValue = compXmlString(xml, sitOff, stOff, attrValueSi);
              else {
                  if( rr != null )
                    try {
                        attrValue = rr.getString( attrResId );
                    } catch( NotFoundException e ) {}
                  if( attrValue == null )
                      attrValue = "0x"+Integer.toHexString( attrResId );
              }
              sb.append( "\n" ).append( spaces( indent+1 ) ).append( attrName ).append( "=\"" ).append( attrValue ).append( "\"" );
              //tr.add(attrName, attrValue);
            }
            xml_sb.append( "\n" ).append( spaces( indent ) ).append( "<" ).append( name );
            if( sb.length() > 0 )
                xml_sb.append( sb );
            xml_sb.append( ">" );
            indent++;
        
          } else if (tag0 == endTag) { // XML END TAG
            indent--;
            off += 6*4;  // Skip over 6 words of endTag data
            String name = compXmlString(xml, sitOff, stOff, nameSi);
            xml_sb.append( "\n" ).append( spaces( indent ) ).append( "</" ).append( name ).append( ">" );
//            prtIndent(indent, "</"+name+">  (line "+startTagLineNo+"-"+lineNo+")");
            //tr.parent();  // Step back up the NobTree
        
          } else if (tag0 == endDocTag) {  // END OF XML DOC TAG
            break;
        
          } else {
            Log.e( TAG, "  Unrecognized tag code '"+Integer.toHexString(tag0) +"' at offset "+off);
            break;
          }
        } // end of while loop scanning tags and attributes of XML tree
        Log.v( TAG, "    end at offset "+off );
        return xml_sb.toString();
    } // end of decompressXML
    
    
    private final String compXmlString(byte[] xml, int sitOff, int stOff, int strInd) {
      if (strInd < 0) return null;
      int strOff = stOff + LEW(xml, sitOff+strInd*4);
      return compXmlStringAt(xml, strOff);
    }
    
    private final String spaces( int i ) {
        char[] dummy = new char[i*2];
        Arrays.fill( dummy, ' ' );
        return new String( dummy );
    }
    
    // compXmlStringAt -- Return the string stored in StringTable format at
    // offset strOff.  This offset points to the 16 bit string length, which 
    // is followed by that number of 16 bit (Unicode) chars.
    private final String compXmlStringAt(byte[] arr, int strOff) {
      int strLen = arr[strOff+1]<<8&0xff00 | arr[strOff]&0xff;
      byte[] chars = new byte[strLen];
      for (int ii=0; ii<strLen; ii++) {
        chars[ii] = arr[strOff+2+ii*2];
      }
      return new String(chars);  // Hack, just use 8 byte chars
    } // end of compXmlStringAt
    
    
    // LEW -- Return value of a Little Endian 32 bit word from the byte array
    //   at offset off.
    private final int LEW(byte[] arr, int off) {
      return arr[off+3]<<24&0xff000000 | arr[off+2]<<16&0xff0000
        | arr[off+1]<<8&0xff00 | arr[off]&0xFF;
    } // end of LEW

    
    private final void createDesktopShortcut( ComponentName cn, String name, Bitmap ico ) {
        Intent shortcutIntent = new Intent();
        shortcutIntent.setComponent( cn );
        shortcutIntent.setData( uri );
        Intent intent = new Intent();
        intent.putExtra( Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent );
        intent.putExtra( Intent.EXTRA_SHORTCUT_NAME, name );
        if( ico != null )
            intent.putExtra( Intent.EXTRA_SHORTCUT_ICON, ico );
        intent.setAction( "com.android.launcher.action.INSTALL_SHORTCUT" );
        ctx.sendBroadcast( intent );
    }
}
