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
    private final static int    FAVS = 0, LOCAL = 1, FTP = 2, SMB = 3, ROOT = 4, MOUNT = 5, APPS = 6, EXIT = 7, LAST = EXIT;
    private boolean root = false;
    
    public HomeAdapter( Commander c ) {
        super( c, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        numItems = getNumItems();
    }

    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
        if( ( mask & MODE_ROOT ) != 0 ) {
            root = ( mode & MODE_ROOT ) != 0;
            numItems = getNumItems();
            notifyDataSetChanged();
        }
        return mode;
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
    public void openItem( int position ) {
        position = translatePosition( position );
        if( position < 0 || position > LAST )
            return;
        String uri_s = null;
        switch( position ) {
        case FAVS:  uri_s = "favs:";    break; 
        case LOCAL: uri_s = "/sdcard";  break; 
        case ROOT:  uri_s = "root:///"; break;
        case MOUNT: uri_s = "mount:";   break;
        case APPS:  uri_s = "apps:";    break;
        case FTP:   commander.dispatchCommand( FileCommander.FTP_ACT ); return;
        case SMB:   commander.dispatchCommand( FileCommander.SMB_ACT ); return;
        case EXIT:  commander.dispatchCommand( R.id.exit ); return;
        }
        commander.Navigate( Uri.parse(  uri_s ), null );
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

    private int getNumItems() {
        int num = LAST + 1;
        if( !root ) num -= 2;
        num--; // skip also apps
        return num;
    }
    
    private int translatePosition( int p ) {
        if( !root && p >= ROOT )
            p += 2;
        if( p >= APPS ) // temporary
            p += 1;
        return p;
    }
   
    @Override
    public String getItemName( int p, boolean full ) {
        switch( p ) {
        case FAVS:  return s( R.string.favs ); 
        case LOCAL: return s( R.string.local ); 
        case FTP:   return s( R.string.ftp );
        case SMB:   return s( R.string.smb );
        case ROOT:  return s( R.string.root );
        case MOUNT: return s( R.string.mount );
        //case APPS:  return s( R.string.apps );
        case EXIT:  return s( R.string.exit );
        }
        return null;
    }
    
    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        position = translatePosition( position );
        Item item = new Item();
        item.name = "???";
        if( position >= 0 && position <= LAST ) {
            item.name = getItemName( position, false );
             
            switch( position ) {
            case FAVS:  item.icon_id = R.drawable.favs;     break;  
            case LOCAL: item.icon_id = R.drawable.sd;       break;  
            case FTP:   item.icon_id = R.drawable.server;   break;
            case SMB:   item.icon_id = R.drawable.smb;      break;
            case ROOT:  item.icon_id = R.drawable.root;     break;
            case MOUNT: item.icon_id = R.drawable.mount;    break;
            case EXIT:  item.icon_id = R.drawable.exit;     break;
            //case APPS:  item.icon_id = R.drawable.apps; break;
            }
            switch( position ) {
            case FAVS:  item.attr = s( R.string.favs_descr );  break;  
            case LOCAL: item.attr = s( R.string.local_descr ); break;  
            case FTP:   item.attr = s( R.string.ftp_descr );   break;
            case SMB:   item.attr = s( R.string.smb_descr );   break;
            case ROOT:  item.attr = s( R.string.root_descr );  break;
            case MOUNT: item.attr = s( R.string.mount_descr ); break;
            case EXIT:  item.attr = s( R.string.exit_descr );  break;
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
