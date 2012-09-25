package com.ghostsq.commander.adapters;

import com.ghostsq.commander.FileCommander;
import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

public class HomeAdapter extends CommanderAdapterBase {
    private final static String TAG = "HomeAdapter";
    public static final String DEFAULT_LOC = "home:";
    private boolean root = false;
    private static enum Mode {
        FAVS(  R.string.favs,  R.string.favs_descr,   R.drawable.favs    ),  
        LOCAL( R.string.local, R.string.local_descr,  R.drawable.sd      ),   
        FTP(   R.string.ftp,   R.string.ftp_descr,    R.drawable.ftp     ),  
        SFTP(  R.string.sftp,  R.string.sftp_descr,   R.drawable.sftp    ), 
        SMB(   R.string.smb,   R.string.smb_descr,    R.drawable.smb     ), 
        ROOT(  R.string.root,  R.string.root_descr,   R.drawable.root    ), 
        MOUNT( R.string.mount, R.string.mount_descr,  R.drawable.mount   ),
        APPS(  R.string.apps,  R.string.apps_descr,   R.drawable.android ),
        EXIT(  R.string.exit,  R.string.exit_descr,   R.drawable.exit    );
        
        public final int pos, name_id, descr_id, icon_id;
        private Mode( int name_id_, int descr_id_, int icon_id_ ) {
            pos      = ordinal(); 
            name_id  = name_id_; 
            descr_id = descr_id_; 
            icon_id  = icon_id_;
        }
    }
    private Mode[] modes;
    
    public HomeAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        modes = Mode.values();
        numItems = getNumItems();
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
    public int getType() {
        return CA.HOME;
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
        int p = translatePosition( position );
        if( p < 0 || p >= modes.length ) return;
        String uri_s = null;
        if( p == Mode.FAVS.pos ) uri_s = "favs:";                    else 
        if( p == Mode.LOCAL.pos) uri_s = Panels.DEFAULT_LOC;         else 
        if( p == Mode.ROOT.pos ) uri_s = RootAdapter.DEFAULT_LOC;    else
        if( p == Mode.MOUNT.pos) uri_s = MountAdapter.DEFAULT_LOC;   else
        if( p == Mode.APPS.pos ) uri_s = "apps:";                    else
        if( p == Mode.FTP.pos  ) { commander.dispatchCommand( FileCommander.FTP_ACT ); return; }
        if( p == Mode.SFTP.pos ) { commander.dispatchCommand( FileCommander.SFTP_ACT );return; }
        if( p == Mode.SMB.pos  ) { commander.dispatchCommand( FileCommander.SMB_ACT ); return; }
        if( p == Mode.EXIT.pos ) { commander.dispatchCommand( R.id.exit );             return; } 
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
        return num;
    }
    
    private int translatePosition( int p ) {
        if( !root && p >= Mode.ROOT.pos )
            p += 2;
        return p;
    }
   
    @Override
    public String getItemName( int p, boolean full ) {
        return p >= 0 && p < modes.length ? s( modes[p].name_id ) : null;
    }
    
    /*
     * BaseAdapter implementation
     */
    @Override
    public Object getItem( int position ) {
        Utils.changeLanguage( ctx );
        position = translatePosition( position );
        Item item = new Item();
        item.name = "???";
        if( position >= 0 && position < modes.length ) {
            item.name = getItemName( position, false );
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
