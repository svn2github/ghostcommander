package com.ghostsq.commander.adapters;

import java.util.ArrayList;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.favorites.FavDialog;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

public class FavsAdapter extends CommanderAdapterBase {
    private final static String TAG = "FavsAdapter";
    private final static int SCUT_CMD = 26945;
    private ArrayList<Favorite> favs;
    
    public FavsAdapter( Commander c ) {
        super( c, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        numItems = 0;
        favs = null;
        numItems = 1;
    }

    public void setFavorites( ArrayList<Favorite> favs_ ) {
        favs = favs_;
        numItems = favs.size() + 1; 
    }
    
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
        return mode;
    }    
    
    @Override
    public int getType() {
        return CA.FAVS;
    }
    
    @Override
    public String toString() {
        return "favs:";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return Uri.parse( toString() );
    }

    @Override
    public boolean readSource( Uri tmp_uri, String pbod ) {
        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
        return true;
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        if( num <= 1 ) {
            menu.add( 0, Commander.OPEN, 0, s( R.string.go_button ) );
            menu.add( 0, R.id.F2,        0, s( R.string.rename_title ) );
            menu.add( 0, R.id.F4,        0, s( R.string.edit_title ) );
            menu.add( 0, R.id.F8,        0, s( R.string.delete_title ) );
            menu.add( 0, SCUT_CMD,       0, s( R.string.shortcut ) );
        }
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
        for( int i = 0; i < cis.size(); i++ )
            if( cis.valueAt( i ) ) {
                int k = cis.keyAt( i );
                if( k > 0 ) {
                    favs.remove( k - 1 );
                    numItems--;
                    notifyDataSetChanged();
                    commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, null ) );
                    return true;
                }
            }
        return false;
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            commander.Navigate( Uri.parse( "home:" ), null );
            return;
        }
        if( favs == null || position < 0 || position > numItems )
            return;
        commander.Navigate( favs.get( position - 1 ).getUriWithAuth(), null );
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }

    @Override
    public boolean renameItem( int p, String newName, boolean c  ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            favs.get( p-1 ).setComment( newName );
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        if( SCUT_CMD == command_id ) {
            int k = 0, n = favs.size();
            for( int i = 0; i < cis.size(); i++ ) {
                k = cis.keyAt( i );
                if( cis.valueAt( i ) && k > 0 && k <= n )
                    break;
            }
            if( k > 0 )
                createDesktopShortcut( favs.get( k - 1 ) );
        }
    }
    
    public void editItem( int p ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            new FavDialog( commander.getContext(), favs.get( p-1 ), this );
        }
    }    

    public void invalidate() {
        notifyDataSetChanged();
        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, null ) );
    }    

    private final void createDesktopShortcut( Favorite f ) {
        if( f == null ) return;
        Uri uri = f.getUriWithAuth();
        Intent shortcutIntent = new Intent();
        shortcutIntent.setClassName( commander.getContext(), commander.getClass().getName() );
        shortcutIntent.setAction( Intent.ACTION_VIEW );
        shortcutIntent.setData( uri );

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        String name = f.getComment();
        if( name == null || name.length() == 0 )
            name = f.getUriString( true );
        intent.putExtra( Intent.EXTRA_SHORTCUT_NAME, name );
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext( commander.getContext(), getDrawableIconId( uri ) );
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        intent.setAction( "com.android.launcher.action.INSTALL_SHORTCUT" ); //Intent.ACTION_CREATE_SHORTCUT
        commander.getContext().sendBroadcast( intent );
    }

    private final int getDrawableIconId( Uri uri ) {
        if( uri != null ) {
            String sch = uri.getScheme();
            if( sch != null && sch.length() != 0 ) {
                int t_id = CA.GetAdapterTypeId( sch );
                if( CA.ZIP  == t_id ) return R.drawable.zip;     else   
                if( CA.FTP  == t_id ) return R.drawable.server;  else   
                if( CA.ROOT == t_id ) return R.drawable.root;    else  
                if( CA.MNT  == t_id ) return R.drawable.mount;   else  
                if( CA.SMB  == t_id ) return R.drawable.smb;     else
                    return R.drawable.folder;
            }
        }
        return R.drawable.folder;
    }
    
    @Override
    public String getItemName( int p, boolean full ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            Favorite f = favs.get( p - 1 );
            String comm = f.getComment();
            return comm != null && comm.length() > 0 ? comm : full ? f.getUriString( true ) : "";
        }
        return null;
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
            if( favs != null && position > 0 && position <= favs.size() ) {
                Favorite f = favs.get( position - 1 );
                if( f != null ) {
                    item.dir = false;
                    item.name = f.getUriString( true );
                    item.size = -1;
                    item.sel = false;
                    item.date = null;
                    item.attr = f.getComment();
                    item.icon_id = getDrawableIconId( f.getUri() );
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
