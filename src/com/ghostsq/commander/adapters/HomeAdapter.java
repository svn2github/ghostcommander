package com.ghostsq.commander.adapters;

import java.util.Arrays;
import java.util.Comparator;

import com.ghostsq.commander.FileCommander;
import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

public class HomeAdapter extends CommanderAdapterBase {
    private final static String TAG = "HomeAdapter";
    public static final String DEFAULT_LOC = "home:";
    private boolean root = false;
    private static enum Mode {
        FAVS(   R.string.favs,   R.string.favs_descr,   R.drawable.favs    ),  //0
        LOCAL(  R.string.local,  R.string.local_descr,  R.drawable.sd      ),  //1 
        FTP(    R.string.ftp,    R.string.ftp_descr,    R.drawable.ftp     ),  //2
        PLUGINS(R.string.plugins,R.string.plugins_descr,R.drawable.plugins ),  //3
        ROOT(   R.string.root,   R.string.root_descr,   R.drawable.root    ),  //4+n 
        MOUNT(  R.string.mount,  R.string.mount_descr,  R.drawable.mount   ),  //5+n
        APPS(   R.string.apps,   R.string.apps_descr,   R.drawable.android ),  //6+n-(r?0:2)
        EXIT(   R.string.exit,   R.string.exit_descr,   R.drawable.exit    );  //7+n-(r?0:2)
        
        public final int pos, name_id, descr_id, icon_id;
        private Mode( int name_id_, int descr_id_, int icon_id_ ) {
            pos      = ordinal(); 
            name_id  = name_id_; 
            descr_id = descr_id_; 
            icon_id  = icon_id_;
        }
    }
    private Mode[] modes;
    private Item[] plugins;
    private final int offset = Mode.FTP.pos+1;
    
    public HomeAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        modes = Mode.values();
        numItems = getNumItems();
    }

    @Override
    public String getScheme() {
        return "home";
    }

    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
        mode &= ~ICON_TINY;
        if( ( mask & MODE_ROOT ) != 0 ) {
            root = ( mode & MODE_ROOT ) != 0;
            numItems = getNumItems();
            notifyDataSetChanged();
        }
        return mode;
    }    
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case MOUNT:
            return true;
        case HOME:
            return false;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        return "home:";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return Uri.parse( toString() );
    }
    @Override
    public void setUri( Uri uri ) {
    }
    
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public boolean readSource( Uri tmp_uri, String pbod ) {
        try {
            plugins = null;
            Utils.changeLanguage( ctx );            
            final String ghost_commander = "com.ghostsq.commander";
            final int scheme_pos = ghost_commander.length() + 1;
            PackageManager  pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo( ghost_commander, 0 );
            String[]    ghosts = pm.getPackagesForUid( ai.uid );
            if( ghosts != null &&  ghosts.length > 1 ) {
                plugins = new Item[ghosts.length - 1];
                int i = 0;
                for( String pkgn : ghosts ) {
                    if( ghost_commander.equals( pkgn ) ) continue;
                    Log.d( TAG, pkgn );
                    Item item = new Item();
                    String scheme = pkgn.substring( scheme_pos );
                    if( "samba".equals( scheme ) ) {
                        item.name = s( R.string.smb );
                        item.attr = s( R.string.smb_descr );
                        item.icon_id = R.drawable.smb;
                    } else
                    if( "sftp".equals( scheme ) ) {
                        item.name = s( R.string.sftp );
                        item.attr = s( R.string.sftp_descr );
                        item.icon_id = R.drawable.sftp;
                    } else {                        
                        ApplicationInfo pai = pm.getApplicationInfo( pkgn, 0 );
                        Resources pre = pm.getResourcesForApplication( pai );
                        Utils.changeLanguage( ctx, pre );
                        if( pai.descriptionRes != 0 ) {
                            String descr = pre.getString( pai.descriptionRes );
                            if( Utils.str( descr ) ) {
                                int cp = descr.indexOf( ':' );
                                if( cp > 0 ) {
                                    item.name = descr.substring( 0, cp ); 
                                    item.attr = descr.substring( cp+1 ); 
                                }
                            }
                        }
                        if( !Utils.str( item.name ) )
                            item.name = pre.getString( pai.labelRes );
                        Drawable logo = null;
                        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD )
                            logo = ForwardCompat.getLogo( pm, pai );
                        item.setIcon( logo != null ?  logo  :  pm.getApplicationIcon( pai ) );
                    }
                    item.origin = scheme; 
                    plugins[i++] = item;
                }
                
                Arrays.sort( plugins, new Comparator<Item>() {
                    public int compare( Item i1, Item i2 ) {
                        if( "sftp".equals( i1.origin ) ) return -1;
                        if("samba".equals( i1.origin ) ) return "sftp".equals( i2.origin ) ? 1 : -1;
                        return 0;
                    }
                } );
                
                numItems = getNumItems(); 
            }
        } catch( NameNotFoundException e ) {
            e.printStackTrace();
        } 
        notify( pbod );
        return true;
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
    public void openItem( int position ) {
        String uri_s = null;
        if( plugins != null && position >= offset && position < offset + plugins.length ) {
            Item plugin = plugins[position - offset];
            if( "sftp".equals( plugin.origin ) ) { 
                commander.dispatchCommand( FileCommander.SFTP_ACT); 
                return; 
            }
            if( "samba".equals( plugin.origin ) ) { 
                commander.dispatchCommand( FileCommander.SMB_ACT ); 
                return; 
            }
            uri_s = plugin.origin + ":";
        }
        else {
            int p = translatePosition( position );
            if( p < 0 || p >= modes.length ) return;
            if( p == Mode.FAVS.pos ) uri_s = "favs:";                    else 
            if( p == Mode.LOCAL.pos) uri_s = Panels.DEFAULT_LOC;         else 
            if( p == Mode.ROOT.pos ) uri_s = RootAdapter.DEFAULT_LOC;    else
            if( p == Mode.MOUNT.pos) uri_s = MountAdapter.DEFAULT_LOC;   else
            if( p == Mode.APPS.pos ) uri_s = "apps:";                    else
            if( p == Mode.FTP.pos  ) { commander.dispatchCommand( FileCommander.FTP_ACT ); return; }
            if( p == Mode.EXIT.pos ) { commander.dispatchCommand( R.id.exit );             return; }
            if( p == Mode.PLUGINS.pos ) {
                Intent i = new Intent( Intent.ACTION_VIEW );
                i.setData( Uri.parse( s( R.string.plugins_uri ) ) );
                commander.issue( i, 0 ); 
                return; 
            }
        }
        if( Utils.str( uri_s ) )
            commander.Navigate( Uri.parse( uri_s ), null, null );
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean c ) {
        return notErr();
    }

    private int getNumItems() {
        int num = modes.length;
        if( !root ) num -= 2;
        if( plugins != null )
            num += plugins.length;
        return num;
    }
    
    private int translatePosition( int p ) {
        if( plugins != null && p > Mode.FTP.pos )
            p -= plugins.length;
        if( !root && p >= Mode.ROOT.pos )
            p += 2;
        return p;
    }
   
    @Override
    public String getItemName( int position, boolean full ) {
        if( plugins != null && position >= offset && position < offset + plugins.length )
            return plugins[position - offset].name;
        int p = translatePosition( position );
        return s( modes[p].name_id );
    }
    
    /*
     * BaseAdapter implementation
     */
    @Override
    public Object getItem( int position ) {
        Utils.changeLanguage( ctx );
        if( plugins != null && position >= offset && position < offset + plugins.length ) {
            return plugins[position - offset];
        }
        position = translatePosition( position );
        Item item = new Item();
        item.name = "???";
        if( position >= 0 && position < modes.length ) {
            item.name = s( modes[position].name_id );
            item.icon_id = modes[position].icon_id;
            item.attr = s( modes[position].descr_id );
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
