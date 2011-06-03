package com.ghostsq.commander;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;

import android.net.Uri;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

public class FavsAdapter extends CommanderAdapterBase {
    private final static String TAG = "FavsAdapter";
    
    public FavsAdapter( Commander c ) {
        super( c, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        numItems = 0;
    }

    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
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
    public void openItem( int position ) {
        commander.Navigate( Uri.parse(  "" ), null );
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean c  ) {
        return notErr();
    }

    private boolean notErr() {
        commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
        return false;
    }

    @Override
    public String getItemName( int p, boolean full ) {
        return null;
    }
    
    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "???";
        return item;
    }
    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = (Item)getItem( position );
        if( item == null ) return null;
        return getView( convertView, parent, item );
    }
}
