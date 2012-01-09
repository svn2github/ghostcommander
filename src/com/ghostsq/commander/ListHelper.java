package com.ghostsq.commander;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.FavsAdapter;
import com.ghostsq.commander.adapters.HomeAdapter;
import com.ghostsq.commander.utils.Utils;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;
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
    private boolean   needRefresh;
    
    ListHelper( int which_, Panels p_ ) {
        needRefresh = true;
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
            // Log.v( TAG, "Navigate to " + Favorite.screenPwd( uri ) );
            flv.clearChoices();
            flv.invalidateViews();
            CommanderAdapter ca_old = (CommanderAdapter)flv.getAdapter();
            CommanderAdapter ca_new = null;
            String scheme = uri.getScheme();
            int type_id = CA.GetAdapterTypeId( scheme );
            if( ca_old == null || type_id != ca_old.getType() ) {
                ca_new = CA.CreateAdapter( type_id, p.c );
                if( ca_new == null ) {
                    Log.e( TAG, "Can't create adapter of type '" + scheme + "'" );
                    if( ca_old != null )
                        return;
                    ca_new = CA.CreateAdapter( CA.GetAdapterTypeId( null ), p.c );
                }
                if( ca_old != null )
                    ca_old.prepareToDestroy();
                if( ca_new instanceof FavsAdapter ) {
                    FavsAdapter fav_a = (FavsAdapter)ca_new;
                    fav_a.setFavorites( p.getFavorites() );
                }
                flv.setAdapter( (ListAdapter)ca_new );
                flv.setOnKeyListener( p );
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( p.c );
                applySettings( sharedPref );
                ca_old = ca_new;
            }
            p.applyColors();
            p.setPanelTitle( p.c.getString( R.string.wait ), which );
            p.setToolbarButtons( ca_old );
            ca_old.readSource( uri, "" + which + ( posTo == null ? "" : posTo ) );
        } catch( Exception e ) {
            Log.e( TAG, "NavigateInternal()", e );
        }
    }

    public final void focus() {
        /*
         * boolean focusable = flv.isFocusable(); boolean focusable_tm =
         * flv.isFocusableInTouchMode(); boolean focused = flv.isFocused();
         * boolean item_focus = flv.getItemsCanFocus(); Log.v( TAG,
         * "wants focus. " + focusable + ", " + focusable_tm + ", " + focused +
         * ", " + item_focus );
         */
        if( flv == null ) return;
        flv.requestFocus();
        flv.requestFocusFromTouch();
    }

    public final void applyColors( ColorsKeeper ck ) {
        if( flv == null ) return;
        flv.setBackgroundColor( ck.bgrColor );
        flv.setCacheColorHint( ck.bgrColor );
        if( ck.curColor != 0 ) {
            Drawable d = Utils.getShadingEx( ck.curColor, 0.9f );
            if( d != null )
                flv.setSelector( d );
        }
        
        CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        if( ca != null ) {
            ca.setMode( CommanderAdapter.SET_TXT_COLOR, ck.fgrColor );
            ca.setMode( CommanderAdapter.SET_SEL_COLOR, ck.selColor );
        }
    }

    public final void applySettings( SharedPreferences sharedPref ) {
        try {
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca == null )
                return;
            Display disp = p.c.getWindowManager().getDefaultDisplay();
            int w = disp.getWidth();
            int h = disp.getHeight();
            if( p.sxs )
                w /= 2;
            final int WIDTH_THRESHOLD = 480;
            int m = ca.setMode( CommanderAdapter.MODE_WIDTH,
                    ( p.sxs && w < WIDTH_THRESHOLD ) || sharedPref.getBoolean( "two_lines", false ) ? CommanderAdapter.NARROW_MODE
                            : CommanderAdapter.WIDE_MODE );

            ca.setMode( CommanderAdapter.SET_FONT_SIZE, p.fnt_sz );

            String sfx = p.sxs ? "_SbS" : "_Ovr";
            boolean detail_mode = sharedPref.getBoolean( which == Panels.LEFT ? "left_detailed" + sfx : "right_detailed" + sfx, true );
            
            boolean show_icons = sharedPref.getBoolean( "show_icons", true );
            boolean same_line = ( m & CommanderAdapter.MODE_WIDTH ) == CommanderAdapter.WIDE_MODE;
            int icon_mode;
            if( show_icons ) {
                icon_mode = CommanderAdapter.ICON_MODE;
                if( p.fnt_sz < 18 && !p.fingerFriendly ) {
                    int sq = h * w;
                    //Log.v( TAG, "sq=" + sq );
                    if( sq <= 400 * 480 ) // old or small or sxs on medium 
                        icon_mode |= CommanderAdapter.ICON_TINY;
                    else 
                    if( sq <= 480 * 854 ) {   // medium
                        if( same_line )
                            icon_mode |= CommanderAdapter.ICON_TINY;
                    }
                }
            }
            else
                icon_mode = CommanderAdapter.TEXT_MODE;
            ca.setMode( CommanderAdapter.MODE_ICONS, icon_mode );

            ca.setMode( CommanderAdapter.MODE_CASE, sharedPref.getBoolean( "case_ignore", true ) ? CommanderAdapter.CASE_IGNORE
                    : CommanderAdapter.CASE_SENS );

            ca.setMode( CommanderAdapter.MODE_DETAILS, detail_mode ? CommanderAdapter.DETAILED_MODE : CommanderAdapter.SIMPLE_MODE );
            String sort = sharedPref.getString( which == Panels.LEFT ? "left_sorting" : "right_sorting", "n" );
            ca.setMode( CommanderAdapter.MODE_SORTING, sort.compareTo( "s" ) == 0 ? CommanderAdapter.SORT_SIZE : sort
                    .compareTo( "e" ) == 0 ? CommanderAdapter.SORT_EXT : sort.compareTo( "d" ) == 0 ? CommanderAdapter.SORT_DATE
                    : CommanderAdapter.SORT_NAME );
            ca.setMode( CommanderAdapter.MODE_FINGERF, p.fingerFriendly ? CommanderAdapter.FAT_MODE : CommanderAdapter.SLIM_MODE );

            boolean hidden_mode = sharedPref.getBoolean( ( which == Panels.LEFT ? "left" : "right" ) + "_show_hidden", true );
            ca.setMode( CommanderAdapter.MODE_HIDDEN, hidden_mode ? CommanderAdapter.SHOW_MODE : CommanderAdapter.HIDE_MODE );

            int thubnails_size = 0;
            if( show_icons && sharedPref.getBoolean( "show_thumbnails", true ) )
                thubnails_size = Integer.parseInt( sharedPref.getString( "thumbnails_size", "200" ) );
            ca.setMode( CommanderAdapter.SET_TBN_SIZE, thubnails_size );

            if( ca instanceof HomeAdapter )
                ca.setMode( CommanderAdapter.MODE_ROOT, sharedPref.getBoolean( "show_root", false ) ? CommanderAdapter.ROOT_MODE
                        : CommanderAdapter.BASIC_MODE );

        } catch( Exception e ) {
            Log.e( TAG, "applySettings() inner", e );
        }
    }

    public void setFingerFriendly( boolean fat ) {
        try {
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca != null ) {
                int mode = fat ? CommanderAdapter.FAT_MODE : CommanderAdapter.SLIM_MODE;
                ca.setMode( CommanderAdapter.MODE_FINGERF, mode );
                flv.invalidate();
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    public final void setNeedRefresh() {
        needRefresh = true;
    }
    public final boolean needRefresh() {
        return needRefresh;
    }
    public final void refreshList() {
        try {
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca == null )
                return;
            storeChoosedItems();
            flv.clearChoices();
            ca.readSource( null, null );
            flv.invalidateViews();
            needRefresh = false;
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
        CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        for( int i = 1; i < flv.getCount(); i++ ) {
            if( cards == null )
                flv.setItemChecked( i, set );
            else {
                String i_n = ca.getItemName( i, false );
                if( i_n == null )
                    continue;
                if( Utils.match( i_n, cards ) )
                    flv.setItemChecked( i, set );
            }
        }
    }

    public final int getSelection( boolean one_checked ) {
        int pos = flv.getSelectedItemPosition();
        if( pos != AdapterView.INVALID_POSITION )
            return currentPosition = pos;
        if( one_checked && getNumItemsChecked() == 1 ) {
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    return cis.keyAt( i );
        }
        return currentPosition;
    }

    public final void setSelection( int i, int y_ ) {
        final ListView flv$ = flv;
        final int position$ = i, y$ = y_;
        flv$.post( new Runnable() {
            public void run() {
                flv$.setSelectionFromTop( position$, y$ > 0 ? y$ : flv$.getHeight() / 2 );
            }
        } );
        currentPosition = i;
    }

    public final void setSelection( String name ) {
        CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        if( ca != null ) {
            int i, num = ( (ListAdapter)ca ).getCount();
            for( i = 0; i < num; i++ ) {
                String item_name = ca.getItemName( i, false );
                if( item_name != null && item_name.compareTo( name ) == 0 ) {
                    Log.v( TAG, "trying to set panel " + which + " selection to '" + name + "', pos: " + i + ", ph: " + flv.getHeight() );
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
        if( checked > 0 )
            return checked;
        return getSelection( false ) >= 1 ? 1 : 0; // excluding the parent (0)
                                                   // item
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

    public final void recoverAfterRefresh() { // to be called for the current
                                              // panel
        try {
            reStoreChoosedItems();
            flv.invalidateViews();
            if( !flv.isInTouchMode() && currentPosition > 0 ) {
                //Log.v( TAG, "restoring pos: " + currentPosition );
                setSelection( currentPosition, flv.getHeight() / 2 );
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
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0 )
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
            ListAdapter la = flv.getAdapter();
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
