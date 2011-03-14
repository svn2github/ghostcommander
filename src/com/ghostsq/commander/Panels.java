package com.ghostsq.commander;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.net.wifi.WifiConfiguration.GroupCipher;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.widget.AbsListView.OnScrollListener;

public class Panels   implements AdapterView.OnItemSelectedListener, 
                                 AdapterView.OnItemClickListener,
                                    ListView.OnScrollListener,
                                        View.OnClickListener, 
                                        View.OnLongClickListener, 
                                        View.OnTouchListener,
                                        View.OnFocusChangeListener,
                                        View.OnKeyListener
{
    private final static String TAG = "Panels";
    public  final static int LEFT = 0, RIGHT = 1;
    private final int titlesIds[] = { R.id.left_dir,  R.id.right_dir };
    private final int  listsIds[] = { R.id.left_list, R.id.right_list };
    protected static final String DEFAULT_LOC = "/sdcard";

    private int currentPositions[] = { -1, -1 };
    private ListView   listViews[] = { null, null };
    private String[] listOfItemsChecked = null;
    private int current = LEFT;
    private FileCommander c;
    public  View mainView, toolbar = null;
    public  ViewFlipper mFlipper_   = null;
    public  PanelsView  panelsView = null;
    private int titleColor = Prefs.getDefaultColor( Prefs.TTL_COLORS ), 
                  fgrColor = Prefs.getDefaultColor( Prefs.FGR_COLORS ),
                  selColor = Prefs.getDefaultColor( Prefs.SEL_COLORS );
    private boolean fingerFriendly = false, warnOnRoot = true, rootOnRoot = false, arrow_mode = false, toolbarShown = false;
    private boolean disableOpenSelectOnly = false, disableAllActions = false;
    private float downX = 0, downY = 0;
    private StringBuffer     quickSearchBuf = null;
    private Toast            quickSearchTip = null;
    private Shortcuts        shorcutsFoldersList;
    private CommanderAdapter destAdapter = null;
    private boolean sxs;
    
    public Panels( FileCommander c_, boolean sxs_ ) {
        c = c_;
        current = LEFT;
        c.setContentView( R.layout.alt );
        mainView = c.findViewById( R.id.main );
        
        //mFlipper = ((ViewFlipper)c.findViewById( R.id.flipper ));
        panelsView = ((PanelsView)c.findViewById( R.id.panels ));
        if( panelsView != null )
            panelsView.setMode( sxs_, current );
        
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
        fingerFriendly =  sharedPref.getBoolean( "finger_friendly", true );
        setFingerFriendly( fingerFriendly );
        warnOnRoot = sharedPref.getBoolean( "prevent_root", true );
        rootOnRoot = sharedPref.getBoolean( "root_root", false );
        arrow_mode = sharedPref.getBoolean( "arrow_mode", false );
        toolbarShown = sharedPref.getBoolean( "show_toolbar", true );        
        
        initList( LEFT );
        initList( RIGHT );
        highlightCurrentTitle();
        
        TextView left_title = (TextView)c.findViewById( titlesIds[LEFT] );
        if( left_title != null ) {
            left_title.setOnClickListener( this );
            left_title.setOnLongClickListener( this );
        }
        TextView right_title = (TextView)c.findViewById( titlesIds[RIGHT] );
        if( right_title != null ) {
            right_title.setOnClickListener( this );
            right_title.setOnLongClickListener( this );
        }
        shorcutsFoldersList = new Shortcuts( c, this );
        try{ 
	        quickSearchBuf = new StringBuffer();
	        quickSearchTip = Toast.makeText( c, "", Toast.LENGTH_SHORT );
        }
        catch( Exception e ) {
			c.showMessage( "Exception on creating quickSearchTip: " + e );
		}
        focus();
    }
    public final void setMode( boolean sxs_ ) {
        sxs = sxs_;
        if( panelsView != null ) panelsView.setMode( sxs_, current );
    }
    public int getCurrent() {
        return current;
    }
    
    public final void showToolbar( boolean show ) {
        toolbarShown = show;
    }
    public final void setToolbarButtons( CommanderAdapter ca ) {
        try {
            if( toolbarShown ) {
                if( toolbar == null ) {
                    LayoutInflater inflater = (LayoutInflater)c.getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                    toolbar = inflater.inflate( R.layout.toolbar, (ViewGroup)mainView, true ).findViewById( R.id.toolbar );
                }
                if( toolbar == null ) {
                    Log.e( TAG, "Toolbar infaltion has failed!" );
                    return;
                }
                toolbar.setVisibility( View.INVISIBLE );
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                Button b = null;
                ViewGroup buttonSet = (ViewGroup)toolbar;
                for( int i = 0; i < buttonSet.getChildCount(); i++ ) {
                    b = (Button)buttonSet.getChildAt( i );
                    if( b != null ) {
                        b.setOnClickListener( c );
                        boolean def = false;
                        final String pref_id;
                        final int id = b.getId();
                        switch( id ) {
                        case R.id.F1: pref_id = "show_F1";  def = true;  break;
                        case R.id.F2: pref_id = "show_F2";  def = true;  break;
                      //case R.id.F3: pref_id = "show_F3";  def = true;  break;
                        case R.id.F4: pref_id = "show_F4";  def = true;  break;
                        case R.id.SF4:pref_id = "show_SF4"; def = false; break;
                        case R.id.F5: pref_id = "show_F5";  def = true;  break;
                        case R.id.F6: pref_id = "show_F6";  def = true;  break;
                        case R.id.F7: pref_id = "show_F7";  def = true;  break;
                        case R.id.F8: pref_id = "show_F8";  def = true;  break;
                        case R.id.F9: pref_id = "show_F9";  def = true;  break;
                        case R.id.F10:pref_id = "show_F10"; def = true;  break;
                        case R.id.eq: pref_id = "show_eq";  def = false; break;
                        case R.id.tgl:pref_id = "show_tgl"; def = false; break;
                        case R.id.sz: pref_id = "show_sz";  def = true;  break;
                        case R.id.by_name: pref_id = "show_by_name"; def = true; break;
                        case R.id.by_ext:  pref_id = "show_by_ext";  def = false; break;
                        case R.id.by_size: pref_id = "show_by_size"; def = true; break;
                        case R.id.by_date: pref_id = "show_by_date"; def = true; break;
                        
                        case R.id.select_all:   pref_id = "show_sel_uns"; def = false; break;
                        case R.id.unselect_all: pref_id = "show_sel_uns"; def = false; break;

                        case R.id.enter:   pref_id = "show_enter"; def = false; break;
                        case R.id.add_fav: pref_id = "show_addfav"; def = false; break;
                        
                        default: pref_id = "";
                        }
                        boolean a = ca.isButtonActive( id );
                        b.setVisibility( a && sharedPref.getBoolean( pref_id, def ) ? View.VISIBLE : View.GONE );
                    }
                }
                toolbar.setVisibility( View.VISIBLE );
            }
            else {
                if( toolbar != null )
                    toolbar.setVisibility( View.GONE );
            }
        } catch( Exception e ) {
            Log.e( TAG, "setToolbarButtons() exception", e );
        }
    }
    public final void focus() {
    	ListView flv = listViews[current];
    	/*
    	boolean focusable    = flv.isFocusable();
    	boolean focusable_tm = flv.isFocusableInTouchMode();
    	boolean focused      = flv.isFocused();
    	boolean item_focus   = flv.getItemsCanFocus();
    	c.showMessage( "" + focusable + ", " + focusable_tm + ", " + focused + ", " + item_focus );
    	*/
        flv.requestFocus();
        flv.requestFocusFromTouch();  
    }
    // View.OnFocusChangeListener implementation
    @Override
    public void onFocusChange( View v, boolean f ) {
        if( sxs && f && listViews[current] != v ) {
            togglePanels( false );
        }
    }
    
    public final boolean isCurrent( int q ) {
        return ( current == LEFT  && q == LEFT ) ||
               ( current == RIGHT && q == RIGHT );
    }
    private final void initList( int which ) {
        ListView flv = (ListView)c.findViewById( listsIds[which] );
        listViews[which] = flv;
        if( flv != null ) {
            flv.setItemsCanFocus( false );
            flv.setFocusableInTouchMode( true );
            flv.setChoiceMode( ListView.CHOICE_MODE_MULTIPLE );
            flv.setOnItemSelectedListener( this );
            flv.setOnItemClickListener( this );
            flv.setOnFocusChangeListener( this );
            flv.setOnTouchListener( this );
            flv.setOnKeyListener( this );
            flv.setOnScrollListener(this);
            c.registerForContextMenu( flv );
            setPanelTitle( "", which );
        }
    }

    public final void setPanelTitle( String s, int which ) {
        TextView title = (TextView)c.findViewById( titlesIds[which] );
        if( title != null ) {
        	int p_width = mainView.getWidth();
        	if( p_width > 0 )
        		title.setMaxWidth( p_width / 2 );
            if( s == null ) {
                title.setText( c.getString( R.string.fail ) );
            }
            else {
                UrlQuerySanitizer urlqs = new UrlQuerySanitizer();
                title.setText( urlqs.unescape( Utils.screenPwd( s ) ) );
            }
        }
    }
    private final void refreshPanelTitles() {
        try {
            CommanderAdapter cur_ca = getListAdapter( true );
            CommanderAdapter opp_ca = getListAdapter( false );
            if( cur_ca != null )
                setPanelTitle( cur_ca.toString(), current );
            if( opp_ca != null )
            setPanelTitle( opp_ca.toString(), opposite() );
            highlightCurrentTitle();
        } catch( Exception e ) {
            Log.e( TAG, "refreshPanelTitle()", e );
        }
    }
    private final void highlightCurrentTitle() {
        View title_bar = mainView.findViewById( R.id.titles );
        if( title_bar != null )
            title_bar.setBackgroundColor( titleColor );
    	highlightTitle( opposite(), false );
    	highlightTitle( current, true );
    }
    private final void highlightTitle( int which, boolean on ) {
        TextView title = (TextView)mainView.findViewById( titlesIds[which] );
        if( title != null ) {
            title.setBackgroundColor( on ? selColor : selColor & 0x0FFFFFFF );
            if( on )
                title.setTextColor( fgrColor );
            else {
                float[] fgr_hsv = new float[3];
                Color.colorToHSV( fgrColor, fgr_hsv );
                float[] ttl_hsv = new float[3];
                Color.colorToHSV( titleColor, ttl_hsv );
                fgr_hsv[1] *= 0.5;
                fgr_hsv[2] = ( fgr_hsv[2] + ttl_hsv[2] ) / 2;
                title.setTextColor( Color.HSVToColor( fgr_hsv ) );
            }
        }
        else
            Log.e( TAG, "title view was not found!" );
    }
    public final void addCurrentToFavorites() {
        String cur_uri = getFolderUri( true );
        shorcutsFoldersList.addToFavorites( cur_uri );
        c.showMessage( c.getString( R.string.fav_added, cur_uri ) );
    }
    public final int getSelection() {
        int pos = listViews[current].getSelectedItemPosition();
        return pos < 0 ? currentPositions[current] : ( currentPositions[current] = pos );
    }
    public final void setSelection( int i ) {
        setSelection( current, i, 0 );
    }
    public final void setSelection( int which, int i, int y_ ) {
        final ListView final_flv = listViews[which];  
        final int position = i, y = y_;
        final_flv.post(new Runnable() {
            public void run()
            {
                final_flv.setSelectionFromTop( position, y );
            }
        });                     
        currentPositions[which] = i;
    }
    public final void setSelection( int which, String name ) {
    	ListView flv = listViews[which];
    	CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
    	if( ca != null ) {
            int i, num = ((ListAdapter)ca).getCount();
            for( i = 0; i < num; i++ ) {
            	String item_name = ca.getItemName( i, false );
                if( item_name != null && item_name.compareTo( name ) == 0 ) {
                    Log.v( TAG, "trying to set panel " + which + " selection to item '" + name + "', pos: " + i );
                    setSelection( which, i, flv.getHeight() / 2 );
                    if( !flv.requestFocusFromTouch() )
                        Log.w( TAG, "ListView does not take focus :(" );
                    break;
                }
            }
    	}
    }
    public final File getCurrentFile() {
        try {
            ListAdapter a = (ListAdapter)getListAdapter( true );
            if( a instanceof FSAdapter ) {
                CommanderAdapter.Item item = (CommanderAdapter.Item)a.getItem( getSelection() ); 
                if( item != null && item.origin != null )
                    return (File)item.origin; 
            }
        }
        catch( Exception e ) {
            Log.e( TAG, "getCurrentFile()", e );
        }
        return null;
    }
    private final int opposite() {
        return current == LEFT ? RIGHT : LEFT;
    }
    public final CommanderAdapter getListAdapter( boolean forCurrent ) {
        return getListAdapter( forCurrent ? current : opposite() );
    }
    public final CommanderAdapter getListAdapter( int which ) {
        ListView flv = (ListView)listViews[which];
        return flv != null ? (CommanderAdapter)flv.getAdapter() : null;
    }
    public final ListView getListView() {
        return listViews[current];
    }
    public final int getWidth() {
        return mainView.getWidth();
    }
    private final void applyColors() {
        SharedPreferences color_pref = c.getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
        int bg_color = Prefs.getDefaultColor( Prefs.BGR_COLORS );
            fgrColor = Prefs.getDefaultColor( Prefs.FGR_COLORS );
            selColor = Prefs.getDefaultColor( Prefs.SEL_COLORS );
          titleColor = Prefs.getDefaultColor( Prefs.TTL_COLORS );
        if( color_pref != null ) {
            bg_color = color_pref.getInt( Prefs.BGR_COLORS, bg_color );
            fgrColor = color_pref.getInt( Prefs.FGR_COLORS,  fgrColor );
            selColor = color_pref.getInt( Prefs.SEL_COLORS,  selColor );
          titleColor = color_pref.getInt( Prefs.TTL_COLORS,titleColor );
        }
        if( sxs ) {
            View div = mainView.findViewById( R.id.divider );
            if( div != null)
                div.setBackgroundColor( titleColor );
        }
        
        for( int i = LEFT; i <= RIGHT; i++ ) {
            ListView flv = listViews[i];
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
        highlightCurrentTitle();
    }
    public final void applySettings( SharedPreferences sharedPref ) {
        try {
            applyColors();
        	setFingerFriendly( sharedPref.getBoolean( "finger_friendly", false ) );
        	warnOnRoot =  sharedPref.getBoolean( "prevent_root", true );
            rootOnRoot = sharedPref.getBoolean( "root_root", false );
            for( int i = LEFT; i <= RIGHT; i++ ) {
                ListView flv = listViews[i];
                applySettings( sharedPref, (CommanderAdapter)flv.getAdapter(), i );
            }
            setPanelCurrent( current );
        }
        catch( Exception e ) {
            Log.e( TAG, "applySettings()", e );
        }
    }
    private final void applySettings( SharedPreferences sharedPref, CommanderAdapter ca, int which ) {
        try {
            arrow_mode = sharedPref.getBoolean( "arrow_mode", false );
            if( !sxs )
                ca.setMode( CommanderAdapter.MODE_WIDTH, sharedPref.getBoolean( "two_lines", false ) ? 
                		    CommanderAdapter.NARROW_MODE : CommanderAdapter.WIDE_MODE );

            ca.setMode( CommanderAdapter.MODE_ICONS, sharedPref.getBoolean( "show_icons", true ) ? 
                    CommanderAdapter.ICON_MODE : CommanderAdapter.TEXT_MODE );

            ca.setMode( CommanderAdapter.MODE_CASE, sharedPref.getBoolean( "case_ignore", true ) ? 
                    CommanderAdapter.CASE_IGNORE : CommanderAdapter.CASE_SENS );

            String sfx = sxs ? "_SbS" : "_Ovr";
            boolean detail_mode = sharedPref.getBoolean( which == LEFT ? "left_detailed" + sfx : "right_detailed" + sfx, true );        
            ca.setMode( CommanderAdapter.MODE_DETAILS, detail_mode ? 
                        CommanderAdapter.DETAILED_MODE : CommanderAdapter.SIMPLE_MODE );
            String sort = sharedPref.getString( which == LEFT ? "left_sorting" : "right_sorting", "n" );
            ca.setMode( CommanderAdapter.MODE_SORTING, sort.compareTo( "s" ) == 0 ? CommanderAdapter.SORT_SIZE : 
                                                       sort.compareTo( "e" ) == 0 ? CommanderAdapter.SORT_EXT : 
                                                       sort.compareTo( "d" ) == 0 ? CommanderAdapter.SORT_DATE : 
                                                                                    CommanderAdapter.SORT_NAME );
            ca.setMode( CommanderAdapter.MODE_FINGERF, fingerFriendly ? CommanderAdapter.FAT_MODE : CommanderAdapter.SLIM_MODE );

            boolean hidden_mode = sharedPref.getBoolean( ( which == LEFT ? "left" : "right" ) + "_show_hidden", true );
            ca.setMode( CommanderAdapter.MODE_HIDDEN, hidden_mode ? CommanderAdapter.SHOW_MODE : CommanderAdapter.HIDE_MODE );

            int thubnails_size = 0;
            if( sharedPref.getBoolean( "show_thumbnails", true ) )
                thubnails_size = Integer.parseInt( sharedPref.getString( "thumbnails_size", "100" ) );
            ca.setMode( CommanderAdapter.SET_TBN_SIZE, thubnails_size );

        } catch( Exception e ) {
            Log.e( TAG, "applySettings() inner", e );
        }
    }
    public void changeSorting( int sort_mode ) {
        CommanderAdapter ca = getListAdapter( true );
        storeChoosedItems();
        ca.setMode( CommanderAdapter.MODE_SORTING, sort_mode );
        reStoreChoosedItems();
    }
    public final void refreshLists() {
        refreshList( current );
        if( sxs )
            refreshList( opposite() );
    }
    public final void refreshList( int which ) {
        try {
            ListView flv = listViews[which];
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            if( ca == null ) return;
            if( which == current ) {
                storeChoosedItems();
                flv.clearChoices();
            }
            ca.readSource( null, null );
            flv.invalidateViews();
        } catch( Exception e ) {
            Log.e( TAG, "refreshList()", e );
        }
    }
    public final void recoverAfterRefresh( String item_name, int which_panel ) {
        try {
            if( which_panel >= 0 ) {
                if( item_name != null && item_name.length() > 0 )
                    setSelection( which_panel, item_name );
                else
                    setSelection( which_panel, 0, 0 );
            }
            else {
                ListView flv = listViews[current];
                reStoreChoosedItems();
                flv.invalidateViews();
                if( !flv.isInTouchMode() && currentPositions[current] > 0 ) {
                    Log.i( TAG, "stored pos: " + currentPositions[current] );
                    flv.setSelection( currentPositions[current] );
                }
            }
            refreshPanelTitles();
        } catch( Exception e ) {
            Log.e( TAG, "refreshList()", e );
        }
    }
    public void setFingerFriendly( boolean finger_friendly ) {
        fingerFriendly = finger_friendly;
        try {
            int mode = fingerFriendly ? CommanderAdapter.FAT_MODE : CommanderAdapter.SLIM_MODE;
            for( int p = LEFT; p <= RIGHT; p++ ) {
                TextView title = (TextView)c.findViewById( titlesIds[p] );
                if( title != null ) {
                    if( finger_friendly )
                        title.setPadding( 8, 6, 8, 6 );
                    else
                        title.setPadding( 8, 1, 8, 1 );
                }
                ListView flv = listViews[p];
                if( flv != null ) {
                    CommanderAdapter  ca = (CommanderAdapter)flv.getAdapter();
                    if( ca != null ) 
                        ca.setMode( CommanderAdapter.MODE_FINGERF, mode );
                    flv.invalidate();
                }
            }
        }
        catch( Exception e ) {
        }
    }
    public final void makeOtherAsCurrent() {
        NavigateInternal( opposite(), getListAdapter( true ).getUri(), null );
    }
    public final void togglePanelsMode() {
        setMode( !sxs );
    }
    public final void togglePanels( boolean refresh ) {
        Log.v( TAG, "toggle" );
        setPanelCurrent( opposite() );
        if( refresh && !sxs )
            refreshList( current );
    }
    
    public final void setPanelCurrent( int which ) {
        Log.v( TAG, "setPanelCurrent " + which );
        if( panelsView != null ) {
            panelsView.setMode( sxs, which );
            
/*
            if( mFlipper != null ) {
            	if( which == RIGHT ) {
                    mFlipper.setInAnimation(  AnimationUtils.loadAnimation( c, R.anim.left_in ) );
                    mFlipper.setOutAnimation( AnimationUtils.loadAnimation( c, R.anim.left_out ) );
    	        }
    	        else {
                    mFlipper.setInAnimation(  AnimationUtils.loadAnimation( c, R.anim.right_in ) );
                    mFlipper.setOutAnimation( AnimationUtils.loadAnimation( c, R.anim.right_out ) );
                }
            	mFlipper.setDisplayedChild( which );
            }
*/
        }
        current = which;
        focus();
        highlightCurrentTitle();
        setToolbarButtons( getListAdapter( true ) );
    }
    public final void showSizes() {
        storeChoosedItems();
        getListAdapter( true ).reqItemsSize( getSelectedOrChecked() );
	}
    public final void checkItem( boolean next ) {
        ListView flv = listViews[current];
        final int pos = getSelection();
        if( pos > 0 ) {
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            flv.setItemChecked( pos, !cis.get( pos ) );
            if( next )
                flv.setSelectionFromTop( pos + 1, flv.getHeight() / 2 );
        }
    }
    public final void checkItems( boolean set, String mask ) {
        String[] cards = Utils.prepareWildcard( mask );
        ListView flv = listViews[current];
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
    class NavDialog implements OnClickListener {
        private   final Uri sdcard = Uri.parse(DEFAULT_LOC);
    	protected int    which;
    	protected String posTo;
    	protected Uri    uri;
    	
    	NavDialog( Context c, int which_, Uri uri_, String posTo_ ) { 
    		which = which_;
    		uri   = uri_;
    		posTo = posTo_;
    		LayoutInflater factory = LayoutInflater.from( c );
    		new AlertDialog.Builder( c )
	            .setIcon( android.R.drawable.ic_dialog_alert )
	            .setTitle( R.string.confirm )
	            .setView( factory.inflate( R.layout.rootmpw, null ) )
	            //.setMessage( c.getString( R.string.nav_warn, uri ) )
	            .setPositiveButton( R.string.dialog_ok, this )
                .setNeutralButton( R.string.dialog_cancel, this )
	            .setNegativeButton( R.string.dialog_exit, this )
	            .show();
    	}
        @Override
    	public void onClick( DialogInterface idialog, int whichButton ) {
            if( whichButton == DialogInterface.BUTTON_POSITIVE ) {
                warnOnRoot = false;
                if( rootOnRoot )
                    uri = uri.buildUpon().scheme( "root" ).build();
                NavigateInternal( which, uri, posTo );
            }
            else if( whichButton == DialogInterface.BUTTON_NEUTRAL ) {
                NavigateInternal( which, sdcard, null );                
            }
            else
                c.finish();
        	idialog.dismiss();
        }
    }
    public final void Navigate( int which, Uri uri, String posTo ) {
    	if( uri == null ) return;
    	String scheme = uri.getScheme(), path = uri.getPath();
    	
    	if( ( scheme == null || scheme.compareTo("file") == 0 ) && 
    	      ( path == null || !path.startsWith( DEFAULT_LOC ) ) ) {
    	    if( warnOnRoot ) {
                CommanderAdapter ca = getListAdapter( which );
                if( ca != null && ca.getType().compareTo( "file" ) == 0 && ca.toString().startsWith( DEFAULT_LOC ) ) {
            		try {
                		new NavDialog( c, which, uri, posTo );
        			} catch( Exception e ) {
        				Log.e( TAG, "Navigate()", e );
        			}
                    return;
                }
    	    }
    	    else if( rootOnRoot )
    	        uri = uri.buildUpon().scheme( "root" ).build();
    	}
    	NavigateInternal( which, uri, posTo );
    }
    private final void NavigateInternal( int which, Uri uri, String posTo ) {
        try {
            ListView flv = listViews[which];
            flv.clearChoices();
            flv.invalidateViews();
            CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
            String scheme = uri.getScheme();
            int type_h = CommanderAdapterBase.GetAdapterTypeHash( scheme );
            if( ca == null || type_h != ca.getType().hashCode() ) {
                if( ca != null )
                    ca.prepareToDestroy();
                ca = null;
               ca = CommanderAdapterBase.CreateAdapter( type_h, c );
                if( ca == null )
                    Log.e( TAG, "Unknown adapter with type hash " + type_h );
                else {
                    ca.setMode( CommanderAdapter.WIDE_MODE, 
                      sxs ? CommanderAdapter.NARROW_MODE : CommanderAdapter.WIDE_MODE );
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                    applySettings( sharedPref, ca, which );
                    flv.setAdapter( (ListAdapter)ca );
                    flv.setOnKeyListener( this );
                }
            }
            if( ca == null ) return;
            applyColors();
            setPanelTitle( c.getString( R.string.wait ), which );
            setToolbarButtons( ca );
            
            ca.readSource( uri, "" + which + ( posTo == null ? "" : posTo ) );
        } catch( Exception e ) {
            Log.e( TAG, "NavigateInternal()", e );
        }
    }
    
    public void login( String to, String name, String pass ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca != null && ca.toString().compareTo( to ) == 0 ) {
            ca.setIdentities( name, pass );
            NavigateInternal( current, Uri.parse(to), null );
            return;
        }
        ca = getListAdapter( false );
        if( ca != null && ca.toString().compareTo( to ) == 0 ) {
            ca.setIdentities( name, pass );
            NavigateInternal( opposite(), Uri.parse(to), null );
            return;
        }
    }
            
    public final void Destroying() {
        getListAdapter( false ).prepareToDestroy();
        getListAdapter( true ).prepareToDestroy();
    }

    public final void tryToSend() {
        File f = getCurrentFile();
        if( f != null ) {
            String ext = Utils.getFileExt( f.getName() );
            String mime = ext.equalsIgnoreCase( ".apk" ) ? "*/*" : Utils.getMimeByExt( ext );
            Intent sendIntent = new Intent( Intent.ACTION_SEND );
            Log.i( TAG, "Type file to send: " + mime );
            sendIntent.setType( mime == null ? "*/*" : mime );
            sendIntent.putExtra( Intent.EXTRA_STREAM, Uri.fromFile( f ) );
            c.startActivity( Intent.createChooser( sendIntent, "Send:" ) );            
        }        
    }    
    public final void tryToOpen() {
        File f = getCurrentFile();
        if( f != null ) {
            Intent intent = new Intent( Intent.ACTION_VIEW );
            intent.setDataAndType( Uri.fromFile( f ), "*/*" );
            c.startActivity( Intent.createChooser( intent, "Open with..." ) );            
        }        
    }    
    public final void copyName() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        ClipboardManager clipboard = (ClipboardManager)c.getContext().getSystemService( Context.CLIPBOARD_SERVICE ); 
        String in = ca.getItemName( getSelection(), true );
        clipboard.setText( in );
    }    
    public final void favFolder() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        String fn = ca.getItemName( getSelection(), true );
        shorcutsFoldersList.addToFavorites( fn );
    }    

    public final void openForEdit( String file_name ) {
        File f = file_name == null ? getCurrentFile() : new File( file_name );
        if( f != null && f.isFile() ) {
        	try {
	            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
	            String full_class_name = sharedPref.getString( "editor_activity", c.getString( R.string.value_editor_activity ) );
	            if( full_class_name != null ) {
		            Intent i = new Intent( Intent.ACTION_EDIT );
		            i.setDataAndType( Uri.parse( "file://" + f.getAbsolutePath() ), "text/plain" );
		            
		            int last_dot_pos = full_class_name.lastIndexOf('.');
		            if( last_dot_pos < 0 )
		            	c.showMessage( "Invalid class name: " + full_class_name );
		            else {
			            i.setClassName( full_class_name.substring( 0, last_dot_pos ), full_class_name );
			            c.startActivity( i );
		            }
	            }
        	}
        	catch( ActivityNotFoundException e ) {
        		c.showMessage( "Activity Not Found: " + e );
        	}
        	catch( IndexOutOfBoundsException e ) {
        		c.showMessage( "Bad activity class name: " + e );
        	}
        }
        else
            c.showMessage( "Not editable" );
    }
    public final int getNumItemsChecked() {
        ListView flv = listViews[current];
        SparseBooleanArray cis = flv.getCheckedItemPositions();
        int counter = 0;
        for( int i = 0; i < cis.size(); i++ )
            if( cis.valueAt( i ) ) {
                counter++;
            }
        return counter;
    }
    public final String getActiveItemsSummary() {
        int counter = getNumItemsChecked();
        if( counter > 1 )
            return "" + counter + " " + c.getString( R.string.items );
        ListView flv = listViews[current];
        CommanderAdapter adapter = (CommanderAdapter)flv.getAdapter();
        if( counter == 1 ) {
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    return "'" + adapter.getItemName( cis.keyAt( i ), false ) + "'";
        }
        int cur_sel = getSelection();
        if( cur_sel <= 0 )
            return null; // the topmost item is also invalid
        return "'" + adapter.getItemName( cur_sel, false ) + "'";
    }
    public final SparseBooleanArray getSelectedOrChecked() {
        ListView flv = listViews[current];
        int num_checked = getNumItemsChecked();
        SparseBooleanArray cis;
        if( num_checked > 0 )
            cis = flv.getCheckedItemPositions();
        else {
            cis = new SparseBooleanArray( 1 );
            cis.put( getSelection(), true );
        }
        return cis;
    }
    /**
     * @return 0 - nothing selected, 1 - a file, -1 - a folder, otherwise the number 
     */
    public final int getNumItemsSelectedOrChecked() {
    	int checked = getNumItemsChecked();
    	return checked;
    }
    public final String getFolderUri( boolean active ) {
        CommanderAdapter a = getListAdapter( active );
        if( a == null ) return "";
        return a.toString();
    }
    public final String getSelectedItemName() {
        return getListAdapter( true ).getItemName( getSelection(), false );
    }
	public final void quickSearch( char ch ) {
		CommanderAdapter a = getListAdapter( true );
		if( a != null ) {
			quickSearchBuf.append( ch );
			String s = quickSearchBuf.toString();
			showTip( s );
			
			int n = ((ListAdapter)a).getCount();
			for( int i = 1; i < n; i++ ) {
				String name = a.getItemName( i , false );
				if( name == null ) continue;
				if( s.regionMatches( true, 0, name, 0, s.length() ) ) {
					setSelection( i );
					return;
				}
			}
		}
	}
    private final void showTip( String s ) {
    	try {
	        if( !sxs || current == LEFT )
	        	quickSearchTip.setGravity( Gravity.BOTTOM | Gravity.LEFT, 5, 10 );
	        else
	        	quickSearchTip.setGravity( Gravity.BOTTOM, 10, 10 );
	    	quickSearchTip.setText( s );
	    	quickSearchTip.show();
		}
    	catch( RuntimeException e ) {
    		c.showMessage( "RuntimeException: " + e );
		}
    }
	public final void resetQuickSearch() {
		quickSearchBuf.delete( 0, quickSearchBuf.length() );
	}
	public final void openGoPanel() {
		shorcutsFoldersList.openGoPanel( current, getFolderUri( true ) );
	}
	
    public final void terminateOperation() {
        CommanderAdapter a = getListAdapter( true );
        a.terminateOperation();
        if( a == destAdapter ) destAdapter = null;
        CommanderAdapter p = getListAdapter( false );
        p.terminateOperation();
        if( p == destAdapter ) destAdapter = null;
        if( null != destAdapter ) {
            destAdapter.terminateOperation();
            destAdapter = null;
        }
    }
    public final void operationFinished() {
        if( null != destAdapter )
            destAdapter = null;
    }    
    
    public final void copyFiles( String dest, boolean move ) {
        try {
            CommanderAdapter dest_adapter = getListAdapter( false );
            if( dest_adapter == null || !dest.equals( dest_adapter.toString() ) ) {
                Uri dest_uri = Uri.parse( dest );
                if( dest_uri == null ) {
                    c.showError( c.getString( R.string.inv_dest ) );
                    return;
                }
                String scheme = dest_uri.getScheme();
                int type_h = CommanderAdapterBase.GetAdapterTypeHash( scheme );
                dest_adapter = CommanderAdapterBase.CreateAdapter( type_h, c );
                if( dest_adapter == null ) {
                    c.showError( c.getString( R.string.inv_dest ) );
                    return;
                }
                dest_adapter.readSource( dest_uri, null ); // TODO: call Init() method to set the URI
            }
            c.showDialog( Dialogs.PROGRESS_DIALOG );
            destAdapter = dest_adapter;
            getListAdapter( true ).copyItems( getSelectedOrChecked(), destAdapter, move );
            // TODO: getCheckedItemPositions() returns an empty array after a failed operation. why? 
            listViews[current].clearChoices();
        }
        catch( Exception e ) {
            Log.e( TAG, "copyFiles()", e );
        }
    }

    public void createNewFile( String fileName ) {
		String local_name = fileName;
		CommanderAdapter ca = getListAdapter( true );
		if( fileName.charAt( 0 ) != '/' ) {
			String dirName = ca.toString();
			fileName = dirName + ( dirName.charAt( dirName.length()-1 ) == '/' ? "" : "/" ) + fileName;
		}
		if( ca.createFile( fileName ) ) {
			refreshLists();
			setSelection( current, local_name );
			openForEdit( fileName );
		}
	}

	public final void createFolder( String new_name ) {
        getListAdapter( true ).createFolder( new_name );
    }

    public final void createZip( String new_zip_name ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca instanceof FSAdapter ) {
            SparseBooleanArray cis = getSelectedOrChecked();
            if( cis == null || cis.size() == 0 ) return;
            c.showDialog( Dialogs.PROGRESS_DIALOG );
            FSAdapter fsa = (FSAdapter)ca;
            ZipAdapter z = new ZipAdapter( c );
            destAdapter = z;
            File[] files = fsa.bitsToFiles( cis );
            z.createZip( files, Utils.mbAddSl( ca.toString() ) + new_zip_name );
        }
    }
    
    public final void deleteItems() {
    	c.showDialog( Dialogs.PROGRESS_DIALOG );
        if( getListAdapter( true ).deleteItems( getSelectedOrChecked() ) )
            listViews[current].clearChoices();
    }

    public final void renameFile( String new_name ) {
        CommanderAdapter adapter = getListAdapter( true );
        int pos = getSelection();
        if( pos >= 0 )
            adapter.renameItem( pos, new_name );
    }

    // /////////////////////////////////////////////////////////////////////////////////

    /**
     * An AdapterView.OnItemSelectedListener implementation
     */
    @Override
    public void onItemSelected( AdapterView<?> listView, View itemView, int pos, long id ) {
    	shorcutsFoldersList.closeGoPanel();
        if( id == R.layout.alt && listsIds[current] != listView.getId() )
        	togglePanels( false );
        currentPositions[current] = pos;
    }
    @Override
    public void onNothingSelected( AdapterView<?> arg0 ) {
    	resetQuickSearch();
    }
    /**
     * An AdapterView.OnItemClickListener implementation 
     */
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
        
        Log.v( TAG, "onItemClick" );
        
    	shorcutsFoldersList.closeGoPanel();
    	resetQuickSearch();
        if( id == R.layout.alt && listViews[current] != parent ) {
            togglePanels( false );
            if( listViews[current] != parent )
            	Log.e( TAG, "onItemClick. current=" + current + ", parent=" + parent.getId() );
        }
        	
        ListView flv = listViews[current];
        if( position == 0 ) {
            flv.setItemChecked( 0, false ); // parent item never selected
            currentPositions[current] = 0;
        }
        
        if( disableAllActions ) {
            disableAllActions = false;
            disableOpenSelectOnly = false;
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            flv.setItemChecked( position, !cis.get( position ) );
            return;
        }
        if( disableOpenSelectOnly )
            disableOpenSelectOnly = false;
        else { 
            openItem( position );
            flv.setItemChecked( position, false );
        }
    }
    public void openItem( int position ) {
        ListView flv = listViews[current];
        ((CommanderAdapter)flv.getAdapter()).openItem( position );
    }
    
    /**
     * View.OnTouchListener implementation 
     */
    @Override
    public boolean onTouch( View v, MotionEvent event ) {
    	resetQuickSearch();
	    if( v instanceof ListView ) {
            if( v == listViews[opposite()])
                togglePanels( false );
	        
	    	shorcutsFoldersList.closeGoPanel();
	        switch( event.getAction() ) {
	        case MotionEvent.ACTION_DOWN: {
                    downX = event.getX();
                    downY = event.getY();
                    disableOpenSelectOnly = event.getX() > v.getWidth() / 2;
    	            break;
    	        }
	        case MotionEvent.ACTION_UP: {
                    int deltaX = (int)(event.getX() - downX);
                    int deltaY = (int)(event.getY() - downY);
                    int absDeltaX = Math.abs( deltaX );
                    int absDeltaY = Math.abs( deltaY );
                    
                    if( absDeltaY > 10 || absDeltaX > 10 )
                        disableOpenSelectOnly = false;
                    /*
                    if( id == R.layout.alt ) break;                      // side-by-side panels - no sweeps
                    if( absDeltaY > absDeltaX || absDeltaX < v.getWidth() / 2 ) break; // vertical or small movement not enough for toggle
                    if( deltaX > 0 && current == LEFT )  break;
                    if( deltaX < 0 && current == RIGHT ) break;
                    togglePanels( false );
                    disableAllActions = true;
                    */
       	            break;
    	        }
	        /*
	        case MotionEvent.ACTION_MOVE: {
	                disableOpenSelectOnly = true;
    	            break;
	            }
	        */
	        }
	    }
        return false;
    }

    /* 
     * View.OnKeyListener implementation 
     */
    @Override
	public boolean onKey( View v, int keyCode, KeyEvent event ) {
    	if( event.getAction() != KeyEvent.ACTION_DOWN ) return false;
	    if( v instanceof ListView ) {
	    	shorcutsFoldersList.closeGoPanel();
	    	char ch = (char) event.getUnicodeChar();
	    	if( ch >= 'A' && ch <= 'z' ) {
	    		quickSearch( ch );
	    		return true;
	    	}
	    	resetQuickSearch();
	        switch( ch ) {
	        case '(':
	        case ')': {
		        	int which = ch == '(' ? LEFT : RIGHT;
		            shorcutsFoldersList.openGoPanel( which, getFolderUri( isCurrent( which ) ) );
		        }
	        	return true;
            case '*':
                addCurrentToFavorites();
                return true;
	        case '{':
            case '}':
                setPanelCurrent( ch == '{' ? Panels.LEFT : Panels.RIGHT );
                return true;
            case '#':
                setMode( !sxs );
                return true;
	        case '+':
	        case '-':
	            c.showDialog( ch == '+' ? Dialogs.SELECT_DIALOG :  Dialogs.UNSELECT_DIALOG );
	            return true;
	        case '"':
	        	showSizes();
	            return true;
	        case '2':
	            c.showDialog( R.id.F2 );
	            return true;
	        case '3':
	        	return true;
	        case '4':
	            openForEdit( null );
	            return true;
	        case '5':
	        	c.showDialog( R.id.F5 );
	        	return true;
	        case '6':
	        	c.showDialog( R.id.F6 );
	        	return true;
	        case '7':
	        	c.showDialog( R.id.F7 );
	        	return true;
	        case '8':
	        	c.showDialog( R.id.F8 );
	        	return true;
	        }
	    	switch( keyCode ) {
	        case KeyEvent.KEYCODE_DPAD_UP:
	        case KeyEvent.KEYCODE_DPAD_DOWN:	
		    	resetQuickSearch();
		    	return false;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if( !arrow_mode ) return false;
            case KeyEvent.KEYCODE_VOLUME_UP:
	            checkItem( true );
	            return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
	            if( !arrow_mode ) return false;
	        case KeyEvent.KEYCODE_VOLUME_DOWN:
	        case KeyEvent.KEYCODE_TAB:
	            togglePanels( true );
	            return true;
	        default:
	        	return false;
	    	}
	    }
	    return false;
	}
	/*
     * View.OnClickListener and OnLongClickListener implementation for the titles and history Go
     */
    @Override
    public void onClick( View v ) {
    	resetQuickSearch();
    	int view_id = v.getId();
    	switch( view_id ) {
    	case R.id.left_dir:
    	case R.id.right_dir:
    		shorcutsFoldersList.closeGoPanel();
	        int which = view_id == titlesIds[LEFT] ? LEFT : RIGHT;
	        if( which == current ) {
	            focus();
	            refreshList( current );
	        }
	        else
	            togglePanels( true );
    	}
    }
    @Override
    public boolean onLongClick( View v ) {
    	int which = v.getId() == titlesIds[LEFT] ? LEFT : RIGHT;
        shorcutsFoldersList.openGoPanel( which, getFolderUri( isCurrent( which ) ) );
    	return true;
    }


    /*
     * ListView.OnScrollListener implementation 
     */
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }
    @Override
    public void onScrollStateChanged( AbsListView view, int scrollState ) {
        CommanderAdapter ca;
        try {
            ca = (CommanderAdapter)view.getAdapter();
        }
        catch( ClassCastException e ) {
            Log.e( TAG, "onScrollStateChanged()", e );
            return;
        }
        if( ca != null ) {
            switch( scrollState ) {
            case OnScrollListener.SCROLL_STATE_IDLE:
                ca.setMode( CommanderAdapter.LIST_STATE, CommanderAdapter.STATE_IDLE );
                view.invalidateViews();
                break;
            case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
            case OnScrollListener.SCROLL_STATE_FLING:
                ca.setMode( CommanderAdapter.LIST_STATE, CommanderAdapter.STATE_BUSY );
                break;
            }
        }
    }
    
    /*
     * Persistent state
     */
    
    public void storeChoosedItems() {
        try {
            ListView flv = listViews[current];
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
            ListView flv = listViews[current];
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
    
    class State {
    	private final static String LP = "LEFT_URI", RP = "RIGHT_URI";
    	private final static String LI = "LEFT_ITEM", RI = "RIGHT_ITEM";
    	private final static String CP = "CURRENT_PANEL";
    	private final static String FU = "FAV_URIS";
        int current;
        String left, right;
        String leftItem, rightItem;
        String favUris;	// comma separated
        public void store( Bundle b ) {
            b.putString( LP, left );
            b.putString( RP, right );
            b.putString( LI, leftItem );
            b.putString( RI, rightItem );
            b.putInt( CP, current );
            b.putString( FU, favUris );
        }
        public void restore( Bundle b ) {
            left      = b.getString( LP );
            right     = b.getString( RP );
            leftItem  = b.getString( LI );
            rightItem = b.getString( RI );
            current   = b.getInt( CP );
            favUris   = b.getString( FU );
        }
        public void store( SharedPreferences.Editor e ) {
            e.putString( LP, left );
            e.putString( RP, right );
            e.putString( LI,  leftItem );
            e.putString( RI, rightItem );
            e.putInt( CP, current );
            e.putString( FU, favUris );
        }
        public void restore( SharedPreferences p ) {
            left      = p.getString( LP, DEFAULT_LOC );
            right     = p.getString( RP, DEFAULT_LOC );
            leftItem  = p.getString( LI, null );
            rightItem = p.getString( RI, null );
            current   = p.getInt( CP, LEFT );
            favUris   = p.getString( FU, "" );
        }
    }
    public State getState() {
        State s = new State();
        s.current = current;
        try {
            CommanderAdapter  left_adapter = (CommanderAdapter)listViews[LEFT].getAdapter();
            s.left  =  left_adapter.toString();
            s.leftItem  =  left_adapter.getItemName( currentPositions[LEFT],  false );
            Log.v( TAG, "Saving left current item: " + s.leftItem );
            CommanderAdapter right_adapter = (CommanderAdapter)listViews[RIGHT].getAdapter();
            s.right = right_adapter.toString();
            s.rightItem = right_adapter.getItemName( currentPositions[RIGHT], false );
        } catch( Exception e ) {
            Log.e( TAG, "getState()", e );
        }
        s.favUris = shorcutsFoldersList.getAsString();
        return s;
    }
	public void setState( State s ) {
	    if( s == null ) return;
    	resetQuickSearch();
    	current = s.current;
    	Log.v( TAG, "Restoring left current item: " + s.leftItem );
        NavigateInternal( LEFT,  Uri.parse(s.left),  s.leftItem );
        NavigateInternal( RIGHT, Uri.parse(s.right), s.rightItem );
        applyColors();
        setPanelCurrent( s.current );
        shorcutsFoldersList.setFromString( s.favUris );
    }
}
