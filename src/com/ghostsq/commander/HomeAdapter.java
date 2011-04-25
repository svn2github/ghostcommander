package com.ghostsq.commander;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;

import android.net.Uri;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

public class HomeAdapter extends CommanderAdapterBase {
    private final static String TAG = "HomeAdapter";
    private final static int    LOCAL = 0, FTP = 1, SMB = 2, ROOT = 3, MOUNT = 4, APPS = 5, LAST = 5;
    
    public HomeAdapter( Commander c ) {
        super( c, DETAILED_MODE | NARROW_MODE | SHOW_ATTR );
        numItems = 5;
    }
    @Override
    public String getType() {
        return "home";
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
    public boolean readSource( Uri tmp_uri, String pbod ) {
        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
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
    public String getItemName( int p, boolean full ) {
        switch( p ) {
        case LOCAL: return s( R.string.local ); 
        case FTP:   return s( R.string.ftp );
        case SMB:   return s( R.string.smb );
        case ROOT:  return s( R.string.root );
        case MOUNT: return s( R.string.mount );
        case APPS:  return s( R.string.apps );
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( position < 0 || position > LAST )
            return;
        // TODO
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }
    
    @Override
    public boolean renameItem( int position, String newName ) {
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
        if( position >= 0 && position <= LAST ) {
            item.name = getItemName( position, false );
             
            switch( position ) {
            case LOCAL: item.icon_id = R.drawable.sd; break;  
            case FTP:   item.icon_id = R.drawable.server; break;
            case SMB:   item.icon_id = R.drawable.smb; break;
            case ROOT:  item.icon_id = R.drawable.root; break;
            case MOUNT: item.icon_id = R.drawable.mount; break;
            //case APPS:  item.icon_id = R.drawable.apps; break;
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
