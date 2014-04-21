package com.ghostsq.commander.adapters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.ghostsq.commander.FileCommander;
import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.StorageUtils;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

public class HomeAdapter extends CommanderAdapterBase {
    private final static String TAG = "HomeAdapter";
    public static final String DEFAULT_LOC = "home:";
    private boolean root = false;
    private final int[] FAVS     = { R.string.favs,    R.string.favs_descr,    R.drawable.favs    };
    private final int[] LOCAL    = { R.string.local,   R.string.local_descr,   R.drawable.sd      }; 
    private final int[] EXTERNAL = { R.string.external,R.string.external_descr,R.drawable.sd      }; 
    private final int[] MEDIA    = { R.string.media,   R.string.media_descr,   R.drawable.sd      }; 
    private final int[] FTP      = { R.string.ftp,     R.string.ftp_descr,     R.drawable.ftp     };
    private final int[] PLUGINS  = { R.string.plugins, R.string.plugins_descr, R.drawable.plugins };
    private final int[] ROOT     = { R.string.root,    R.string.root_descr,    R.drawable.root    }; 
    private final int[] MOUNT    = { R.string.mount,   R.string.mount_descr,   R.drawable.mount   };
    private final int[] APPS     = { R.string.apps,    R.string.apps_descr,    R.drawable.android };
    private final int[] EXIT     = { R.string.exit,    R.string.exit_descr,    R.drawable.exit    };
        
    private Item[] items = null;

    private Item makeItem( int[] mode, String scheme ) {
        Item item = new Item();
        item.name = s( mode[0] );
        item.attr = s( mode[1] );
        item.icon_id = mode[2];
        item.origin = scheme;
        return item;
    }
    
    public HomeAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        setCount( getNumItems() );
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
            setCount( getNumItems() );
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
        case SORTING:
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
    
    @Override
    public boolean readSource( Uri tmp_uri, String pbod ) {
        try {
            items = null;
            ArrayList<Item> ia = new ArrayList<Item>();
            Utils.changeLanguage( ctx );

            ia.add( makeItem( FAVS, "favs" ) );
            ia.add( makeItem( LOCAL, "fs" ) );

            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
                List<StorageUtils.StorageInfo> sil = StorageUtils.getStorageList();
                if( sil != null && sil.size() > 0 ) {
                    for( int i = 0; i < sil.size(); i++ ) {
                        StorageUtils.StorageInfo si = sil.get( i );
                        if( !si.internal ) {
                            ia.add( makeItem( EXTERNAL, si.path ) );
                            if( android.os.Build.VERSION.SDK_INT >= 18 )
                                ia.add( makeItem( MEDIA, "ms:" + si.path ) );
                        }
                    }
                }
            }            
            ia.add( makeItem( FTP, "ftp" ) );
            
            Utils.changeLanguage( ctx );            
            final String ghost_commander = "com.ghostsq.commander";
            final int scheme_pos = ghost_commander.length() + 1;
            PackageManager  pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo( ghost_commander, 0 );
            String[]    ghosts = pm.getPackagesForUid( ai.uid );
            if( ghosts != null &&  ghosts.length > 1 ) {
                Arrays.sort( ghosts, new Comparator<String>() {
                    public int compare( String i1, String i2 ) {
                        if( i1.indexOf( "sftp"  ) > 0 ) return -1;
                        if( i1.indexOf( "samba" ) > 0 ) return i2.indexOf( "sftp" ) > 0 ? 1 : -1;
                        return 0;
                    }
                } );
                
                for( String pkgn : ghosts ) {
                    if( ghost_commander.equals( pkgn ) ) continue;
                    ApplicationInfo pai = null;
                    Resources pre = null;
                    try {
                        pai = pm.getApplicationInfo( pkgn, 0 );
                        if( pai == null ) continue;
                        pre = pm.getResourcesForApplication( pai );
                        if( pre == null ) continue;
                    } catch( Exception e ) { 
                        continue; //  v4.4
                    }
                    Log.d( TAG, pkgn );
                    Item item = new Item();
                    ia.add( item );
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
                }
            }
            ia.add( makeItem( PLUGINS, "pl" ) );
            if( root ) {
                ia.add( makeItem( ROOT, "root" ) );
                ia.add( makeItem( MOUNT, "mount" ) );
            }
            ia.add( makeItem( APPS, "apps" ) );
            ia.add( makeItem( EXIT, "exit" ) );
            
            items = new Item[ia.size()];
            ia.toArray( items );
            
            setCount( getNumItems() );
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
        Item item = (Item)getItem( position );
        String uri_s = null;
        if( "ftp".equals( item.origin ) ) { 
            commander.dispatchCommand( FileCommander.FTP_ACT); 
            return; 
        }
        if( "sftp".equals( item.origin ) ) { 
            commander.dispatchCommand( FileCommander.SFTP_ACT); 
            return; 
        }
        if( "samba".equals( item.origin ) ) { 
            commander.dispatchCommand( FileCommander.SMB_ACT ); 
            return; 
        }
        if( "pl".equals( item.origin ) ) {
            Intent i = new Intent( Intent.ACTION_VIEW );
            i.setData( Uri.parse( s( R.string.plugins_uri ) ) );
            commander.issue( i, 0 );
            return; 
        }
        if( "exit".equals( item.origin ) ) { 
            commander.dispatchCommand( R.id.exit ); 
            return; 
        }
        if( "fs".equals( item.origin ) ) 
            uri_s = Environment.getExternalStorageDirectory().getAbsolutePath(); 
        else {
            String scheme = (String)item.origin;
            if( scheme.indexOf( '/' ) >= 0 )
                uri_s = scheme;
            else
                uri_s = item.origin + ":";
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
        return items == null ? 0 : items.length;
    }
   
    @Override
    public String getItemName( int position, boolean full ) {
        return items != null ? "" : items[position].name;
    }
    
    /*
     * BaseAdapter implementation
     */
    @Override
    public Object getItem( int position ) {
        return items[position];
    }
    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = (Item)getItem( position );
        if( item == null ) return null;
        return getView( convertView, parent, item );
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
    }
}
