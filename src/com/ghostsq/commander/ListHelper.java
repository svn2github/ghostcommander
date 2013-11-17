package com.ghostsq.commander;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.FavsAdapter;
import com.ghostsq.commander.adapters.HomeAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.TextView;

public class ListHelper {
    private final String TAG;
    public  final int  which, id;
    public  ListView   flv = null;
    private TextView   status = null;
    private int        currentPosition = -1;
    private String[]   listOfItemsChecked = null;
    private Panels     p;
    private boolean    needRefresh, was_current;
    
    ListHelper( int which_, Panels p_ ) {
        needRefresh = false;
        was_current = false;
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
        status = (TextView)p.c.findViewById( which == Panels.LEFT ? R.id.left_stat : R.id.right_stat );
    }

    public final CommanderAdapter getListAdapter() {
        return (CommanderAdapter)flv.getAdapter();
    }

    public final void mbNavigate( Uri uri, Credentials crd, String posTo, boolean was_current_ ) {
        if( "find".equals( uri.getScheme() ) ) {
            // closure
            final Uri         _uri = uri; 
            final Credentials _crd = crd;
            final String      _posTo = posTo;
            final boolean     _was_current_ = was_current_;
            Context c = p.c.getContext();
            new AlertDialog.Builder( c )
                .setTitle( R.string.refresh )
                .setMessage( R.string.rescan_q )
                .setPositiveButton( R.string.dialog_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            Navigate( _uri, _crd, _posTo, _was_current_ );
                        }
                    })
                .setNegativeButton( R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            Navigate( Uri.parse( "home:" ), null, null, _was_current_ );
                        }
                    } )
                .show();
        } else
            Navigate( uri, crd, posTo, was_current_ );
    }
    
    public final void Navigate( Uri uri, Credentials crd, String posTo, boolean was_current_ ) {
        try {
            // Log.v( TAG, "Navigate to " + Favorite.screenPwd( uri ) );
            was_current = was_current_;
            currentPosition = -1;
            flv.clearChoices();
            flv.invalidateViews();
            CommanderAdapter ca_new = null, ca = (CommanderAdapter)flv.getAdapter();
            String scheme = uri.getScheme();
            if( scheme == null ) scheme = "";
            if( ca == null || !scheme.equals( ca.getScheme() ) ) {
                ca_new = CA.CreateAdapter( scheme, p.c );
                if( ca_new == null ) {
                    Log.e( TAG, "Can't create adapter of type '" + scheme + "'" );
                    if( ca != null )
                        return;
                    ca_new = CA.CreateAdapter( null, p.c );
                }
                if( ca != null )
                    ca.prepareToDestroy();
                if( ca_new instanceof FavsAdapter ) {
                    FavsAdapter fav_a = (FavsAdapter)ca_new;
                    fav_a.setFavorites( p.getFavorites() );
                }
                flv.setAdapter( (ListAdapter)ca_new );
                flv.setOnKeyListener( p );
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( p.c );
                applySettings( sharedPref );
                ca = ca_new;
            }
            //p.applyColors();
            p.setPanelTitle( p.c.getString( R.string.wait ), which );
          //p.setToolbarButtons( ca );
            if( crd != null )
                ca.setCredentials( crd );
            ca.readSource( uri, "" + which + ( posTo == null ? "" : posTo ) );
        } catch( Exception e ) {
            Log.e( TAG, "NavigateInternal()", e );
        }
    }

    public final void focus() {
        //Log.v( TAG, "set focus for panel " + which );
        
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
        final float  pb = Utils.getBrightness( ck.bgrColor );
        final float  sb = pb < 0.2 ? pb + 0.05f : pb - 0.05f;
        status.setBackgroundColor( Utils.setBrightness( ck.bgrColor, sb ) );
        status.setTextColor( ck.fgrColor );
    }

    public final void applySettings( SharedPreferences sharedPref ) {
        try {
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca == null )
                return;
            Display disp = p.c.getWindowManager().getDefaultDisplay();
            int w = disp.getWidth();
            int h = disp.getHeight();
            final int WIDTH_THRESHOLD = 480;
            int m = ca.setMode( CommanderAdapter.MODE_WIDTH,
                    ( p.sxs && w/2 < WIDTH_THRESHOLD ) || sharedPref.getBoolean( "two_lines", false ) ? CommanderAdapter.NARROW_MODE
                            : CommanderAdapter.WIDE_MODE );

            ca.setMode( CommanderAdapter.SET_FONT_SIZE, p.fnt_sz );
            status.setTextSize( p.fnt_sz - 1 );

            String sfx = p.sxs ? "_SbS" : "_Ovr";
            boolean detail_mode = sharedPref.getBoolean( which == Panels.LEFT ? "left_detailed" + sfx : "right_detailed" + sfx, true );
            
            boolean show_icons = sharedPref.getBoolean( "show_icons", true );
            boolean same_line = ( m & CommanderAdapter.MODE_WIDTH ) == CommanderAdapter.WIDE_MODE;
            int icon_mode;
            if( show_icons ) {
                icon_mode = CommanderAdapter.ICON_MODE;
                if( p.fnt_sz < 18 && !p.fingerFriendly ) {
                    if( h * w <= 480 * 854 ) {   // medium
                        if( p.sxs || same_line )
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
    public final void refreshList( boolean was_current_, String posto ) {
        try {
            was_current = was_current_;
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca == null )
                return;
            storeChoosedItems();
            flv.clearChoices();
            String cookie = "" + which;
            if( posto != null )
                cookie += posto; 
            ca.readSource( null, cookie );
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

    public final void checkItems( boolean set, String mask, boolean dir, boolean file ) {
        try {
            if( false == dir && false == file ) return;   // should it issue a warning?
            String[] cards = Utils.prepareWildcard( mask );
            ListAdapter la = flv.getAdapter();
            CommanderAdapter ca = (CommanderAdapter)la;
            if( la != null && cards != null ) {
                for( int i = 1; i < flv.getCount(); i++ ) {
                    if( dir != file ) {
                        CommanderAdapter.Item cai = (CommanderAdapter.Item)la.getItem( i );
                        if( cai == null ) continue;
                        if( cai.dir ) {
                            if( !dir )  continue;
                        } else {
                            if( !file ) continue;
                        }
                    }
                    if( Utils.match( ca.getItemName( i, false ), cards ) )
                        flv.setItemChecked( i, set );
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, mask, e );
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
                    //Log.v( TAG, "trying to set panel " + which + " selection to '" + name + "', pos: " + i + ", ph: " + flv.getHeight() );
                    setSelection( i, flv.getHeight() / 2 );
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
                if( cis.valueAt( i ) ) {
                    String item_name = adapter.getItemName( cis.keyAt( i ), false );
                    if( item_name == null || item_name.length() == 0 )
                        item_name = adapter.getItemName( cis.keyAt( i ), true );
                    return item_name;
                }
        }
        int cur_sel = getSelection( false );
        if( cur_sel <= 0 )
            return null; // the topmost item is also invalid
        String item_name = adapter.getItemName( cur_sel, false ); 
        if( item_name == null || item_name.length() == 0 )
            item_name = adapter.getItemName( cur_sel, true );
        return item_name;
    }

    public final void recoverAfterRefresh( String item_name ) {
        try {
            //Log.v( TAG, "restoring panel " + which + " item: " + item_name );
            reStoreChoosedItems();
            if( Utils.str( item_name ) )
                setSelection( item_name );
            else
                setSelection( currentPosition > 0 ? currentPosition : 0, 0 );
            if( was_current ) {
                p.setPanelCurrent( which, false );
                was_current = false;
            }
        } catch( Exception e ) {
            Log.e( TAG, "recoverAfterRefresh()", e );
        }
    }

    public final void recoverAfterRefresh( boolean this_current ) { // to be called for the current panel
        try {
            reStoreChoosedItems();
            flv.invalidateViews();
            if( this_current && !flv.isInTouchMode() && currentPosition > 0 ) {
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
                if( currentPosition >= 0 )
                    setSelection( currentPosition, 0 );
            }
        } catch( Exception e ) {
            Log.e( TAG, "reStoreChoosedItems()", e );
        }
        finally {
            listOfItemsChecked = null;
            updateStatus();
        }
    }
    public void updateStatus() {
        if( status != null ) {
            status.setText( getActiveItemsSummary() );
        }
    }
}
