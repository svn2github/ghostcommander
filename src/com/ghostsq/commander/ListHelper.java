package com.ghostsq.commander;

import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

public class ListHelper {
    private final String TAG;
    public  final int which, id;
    public  ListView   flv = null;
    private int        currentPosition = -1;
    private String[]   listOfItemsChecked = null;
    private Panels     p;
    
    ListHelper( int which_, Panels p_ ) {
        which = which_;
        TAG = "ListHelper" + which;
        p = p_;
        id = which == Panels.LEFT ? R.id.left_list : R.id.right_list;
        flv = (ListView)p.c.findViewById( id );
        if( flv != null ) {
            flv.setItemsCanFocus( false );
            flv.setFocusableInTouchMode( true );
            flv.setOnItemSelectedListener( p );
            flv.setChoiceMode( ListView.CHOICE_MODE_MULTIPLE );
            flv.setOnItemClickListener( p );
            flv.setOnFocusChangeListener( p );
            flv.setOnTouchListener( p );
            flv.setOnKeyListener( p );
            flv.setOnScrollListener( p );
            p.c.registerForContextMenu( flv );
        }
    }
    
    public final CommanderAdapter getListAdapter() {
        return (CommanderAdapter)flv.getAdapter();
    }

    public final void Navigate( Uri uri, String posTo ) {
        try {
            flv.clearChoices();
            flv.invalidateViews();
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            String scheme = uri.getScheme();
            int type_h = CommanderAdapterBase.GetAdapterTypeHash( scheme );
            if( ca == null || type_h != ca.getType().hashCode() ) {
                if( ca != null )
                    ca.prepareToDestroy();
                ca = null;
               ca = CommanderAdapterBase.CreateAdapter( type_h, p.c );
                if( ca == null )
                    Log.e( TAG, "Unknown adapter with type hash " + type_h );
                else {
                    flv.setAdapter( (ListAdapter)ca );
                    flv.setOnKeyListener( p );
                    ca.setMode( CommanderAdapter.MODE_WIDTH, 
                        p.sxs ? CommanderAdapter.NARROW_MODE : CommanderAdapter.WIDE_MODE );
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( p.c );
                    applySettings( sharedPref );
                    //flv.setSelection( 1 );
                }
            }
            if( ca == null ) return;
            p.applyColors();
            p.setPanelTitle( p.c.getString( R.string.wait ), which );
            p.setToolbarButtons( ca );
            
            ca.readSource( uri, "" + which + ( posTo == null ? "" : posTo ) );
        } catch( Exception e ) {
            Log.e( TAG, "NavigateInternal()", e );
        }
    }
    
    public final void focus() {
        
        boolean focusable    = flv.isFocusable();
        boolean focusable_tm = flv.isFocusableInTouchMode();
        boolean focused      = flv.isFocused();
        boolean item_focus   = flv.getItemsCanFocus();
        Log.v( TAG, "wants focus. " + focusable + ", " + focusable_tm + ", " + focused + ", " + item_focus );
        
        flv.requestFocus();
        flv.requestFocusFromTouch();  
    }

    public final void applyColors( int bg_color, int fgrColor, int selColor ) {
        flv.setBackgroundColor( bg_color );
        flv.setCacheColorHint( bg_color );
        CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        if( ca != null ) {
            ca.setMode( CommanderAdapter.SET_TXT_COLOR, fgrColor );
            ca.setMode( CommanderAdapter.SET_SEL_COLOR, selColor );
        }
        else
            Log.e( TAG, "CommanderAdapter is not defined" );
    }
    
    public final void applySettings( SharedPreferences sharedPref ) {
        try {
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca == null ) {
                Log.e( TAG, "Adapter is null!" );
                return;
            }
            if( !p.sxs )
                ca.setMode( CommanderAdapter.MODE_WIDTH, sharedPref.getBoolean( "two_lines", false ) ? 
                            CommanderAdapter.NARROW_MODE : CommanderAdapter.WIDE_MODE );

            ca.setMode( CommanderAdapter.MODE_ICONS, sharedPref.getBoolean( "show_icons", true ) ? 
                    CommanderAdapter.ICON_MODE : CommanderAdapter.TEXT_MODE );

            ca.setMode( CommanderAdapter.MODE_CASE, sharedPref.getBoolean( "case_ignore", true ) ? 
                    CommanderAdapter.CASE_IGNORE : CommanderAdapter.CASE_SENS );

            String sfx = p.sxs ? "_SbS" : "_Ovr";
            boolean detail_mode = sharedPref.getBoolean( which == Panels.LEFT ? "left_detailed" + sfx : "right_detailed" + sfx, true );        
            ca.setMode( CommanderAdapter.MODE_DETAILS, detail_mode ? 
                        CommanderAdapter.DETAILED_MODE : CommanderAdapter.SIMPLE_MODE );
            String sort = sharedPref.getString( which == Panels.LEFT ? "left_sorting" : "right_sorting", "n" );
            ca.setMode( CommanderAdapter.MODE_SORTING, sort.compareTo( "s" ) == 0 ? CommanderAdapter.SORT_SIZE : 
                                                       sort.compareTo( "e" ) == 0 ? CommanderAdapter.SORT_EXT : 
                                                       sort.compareTo( "d" ) == 0 ? CommanderAdapter.SORT_DATE : 
                                                                                    CommanderAdapter.SORT_NAME );
            ca.setMode( CommanderAdapter.MODE_FINGERF, p.fingerFriendly ? CommanderAdapter.FAT_MODE : CommanderAdapter.SLIM_MODE );

            boolean hidden_mode = sharedPref.getBoolean( ( which == Panels.LEFT ? "left" : "right" ) + "_show_hidden", true );
            ca.setMode( CommanderAdapter.MODE_HIDDEN, hidden_mode ? CommanderAdapter.SHOW_MODE : CommanderAdapter.HIDE_MODE );

            int thubnails_size = 0;
            if( sharedPref.getBoolean( "show_thumbnails", true ) )
                thubnails_size = Integer.parseInt( sharedPref.getString( "thumbnails_size", "100" ) );
            /*
            int m = ca.setMode( CommanderAdapter.SET_TBN_SIZE, thubnails_size );
            Log.v( TAG, "Mode set: " + m );
            */
        } catch( Exception e ) {
            Log.e( TAG, "applySettings() inner", e );
        }
    }
    public void setFingerFriendly( boolean fat ) {
        try {
            CommanderAdapter  ca = (CommanderAdapter)flv.getAdapter();
            if( ca != null ) {
                int mode = fat ? CommanderAdapter.FAT_MODE : CommanderAdapter.SLIM_MODE;
                ca.setMode( CommanderAdapter.MODE_FINGERF, mode );
            }
            flv.invalidate();
        }
        catch( Exception e ) {
        }
    }
    
    public final void refreshList() {
        try {
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca == null ) return;
            storeChoosedItems();
            flv.clearChoices();
            ca.readSource( null, null );
            flv.invalidateViews();
        } catch( Exception e ) {
            Log.e( TAG, "refreshList()", e );
        }
    }
    public final void askRedrawList() {
        flv.invalidateViews();
    }
    
    // --- Selection and Items Checking ---

    public int getCurPos() {
        return currentPosition;
    }
    
    public void setCurPos( int pos ) {
        currentPosition = pos;
    }
    
    public final void checkItem( boolean next ) {
        final int pos = getSelection( false );
        if( pos > 0 ) {
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            flv.setItemChecked( pos, !cis.get( pos ) );
            if( next )
                flv.setSelectionFromTop( pos + 1, flv.getHeight() / 2 );
        }
    }

    public final void checkItems( boolean set, String mask ) {
        String[] cards = Utils.prepareWildcard( mask );
        CommanderAdapter ca =(CommanderAdapter)flv.getAdapter();
        for( int i = 1; i < flv.getCount(); i++ ) {
            if( cards == null )
                flv.setItemChecked( i, set );
            else {
                String i_n = ca.getItemName( i, false );
                if( i_n == null ) continue;
                if( Utils.match( i_n, cards ) )
                    flv.setItemChecked( i, set );
            }
        }
    }
    
    
    public final int getSelection( boolean one_checked ) {
        int pos = flv.getSelectedItemPosition();
        if( pos != AdapterView.INVALID_POSITION ) return currentPosition = pos;
        if( one_checked && getNumItemsChecked() == 1 ) {
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) 
                    return cis.keyAt( i );
        }
        return currentPosition; 
    }
    
    public final void setSelection( int i, int y_ ) {
        final ListView final_flv = flv;  
        final int position = i, y = y_;
        final_flv.post( new Runnable() {
            public void run()
            {
                final_flv.setSelectionFromTop( position, y );
            }
        });                     
        currentPosition = i;
    }

    public final void setSelection( String name ) {
        CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        if( ca != null ) {
            int i, num = ((ListAdapter)ca).getCount();
            for( i = 0; i < num; i++ ) {
                String item_name = ca.getItemName( i, false );
                if( item_name != null && item_name.compareTo( name ) == 0 ) {
                    //Log.v( TAG, "trying to set panel " + which + " selection to item '" + name + "', pos: " + i );
                    setSelection( i, flv.getHeight() / 2 );
                    if( !flv.requestFocusFromTouch() )
                        Log.w( TAG, "ListView does not take focus :(" );
                    break;
                }
            }
        }
    }

    public final int getNumItemsChecked() {
        SparseBooleanArray cis = flv.getCheckedItemPositions();
        int counter = 0;
        for( int i = 0; i < cis.size(); i++ )
            if( cis.valueAt( i ) ) {
                counter++;
            }
        return counter;
    }

    public final int getNumItemsSelectedOrChecked() {
        int checked = getNumItemsChecked();
        if( checked > 0 ) return checked;
        return getSelection( false ) >= 1 ? 1 : 0;  // excluding the parent (0) item
    }

    
    public final SparseBooleanArray getSelectedOrChecked() {
        int num_checked = getNumItemsChecked();
        SparseBooleanArray cis;
        if( num_checked > 0 )
            cis = flv.getCheckedItemPositions();
        else {
            cis = new SparseBooleanArray( 1 );
            cis.put( getSelection( false ), true );
        }
        return cis;
    }
    
    public final String getActiveItemsSummary() {
        int counter = getNumItemsChecked();
        if( counter > 1 ) {
            String items = null;
            if( counter < 5 )
                items = p.c.getString( R.string.items24 );
            if( items == null || items.length() == 0 )
                items = p.c.getString( R.string.items );
            return "" + counter + " " + items;
        }
        CommanderAdapter adapter = (CommanderAdapter)flv.getAdapter();
        if( counter == 1 ) {
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    return "'" + adapter.getItemName( cis.keyAt( i ), false ) + "'";
        }
        int cur_sel = getSelection( false );
        if( cur_sel <= 0 )
            return null; // the topmost item is also invalid
        return "'" + adapter.getItemName( cur_sel, false ) + "'";
    }
    
    
    public final void recoverAfterRefresh( String item_name, boolean this_current ) {
        try {
            //Log.v( TAG, "restoring panel " + which + " item: " + item_name );
            if( item_name != null && item_name.length() > 0 )
                setSelection( item_name );
            else
                setSelection( 0, 0 );
            if( this_current )
                focus();
        } catch( Exception e ) {
            Log.e( TAG, "recoverAfterRefresh()", e );
        }
    }

    public final void recoverAfterRefresh() { // to be called for the current panel
        try {
            reStoreChoosedItems();
            flv.invalidateViews();
            if( !flv.isInTouchMode() && currentPosition > 0 ) {
                //Log.v( TAG, "stored pos: " + currentPositions[current] );
                flv.setSelection( currentPosition );
            }
        } catch( Exception e ) {
            Log.e( TAG, "recoverAfterRefresh()", e );
        }
    }
    
    public void storeChoosedItems() {
        try {
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            listOfItemsChecked = null;
            if( counter > 0 ) {
                listOfItemsChecked = new String[counter];
                int j = 0;
                for( int i = 0; i < cis.size(); i++ )
                    if( cis.valueAt( i ) ) {
                        int k = cis.keyAt( i );
                        if( k > 0 )
                            listOfItemsChecked[j++] = ca.getItemName( k, true );
                    }
            }
        } catch( Exception e ) {
            Log.e( TAG, "storeChoosedItems()", e );
        }
    }
    
    public void reStoreChoosedItems() {
        try {
            if( listOfItemsChecked == null || listOfItemsChecked.length == 0 )
                return;
            ListAdapter      la = flv.getAdapter();
            if( la != null ) {
                CommanderAdapter ca = (CommanderAdapter)la;
                int n_items = la.getCount();
                for( int i = 1; i < n_items; i++ ) {
                    String item_name = ca.getItemName( i, true );
                    boolean set = false;
                    for( int j = 0; j < listOfItemsChecked.length; j++ ) {
                        if( listOfItemsChecked[j].compareTo( item_name ) == 0 ) {
                            set = true;
                            break;
                        }
                    }
                    flv.setItemChecked( i, set );
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "reStoreChoosedItems()", e );
        }
        listOfItemsChecked = null;
    }
    
}
