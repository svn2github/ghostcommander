package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.adapters.FSAdapter;
import com.ghostsq.commander.adapters.FavsAdapter;
import com.ghostsq.commander.adapters.ZipAdapter;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.favorites.Favorites;
import com.ghostsq.commander.favorites.LocationBar;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.toolbuttons.ToolButton;
import com.ghostsq.commander.toolbuttons.ToolButtons;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.HorizontalScrollView;
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
    private final static String     TAG = "Panels";
    public static final String      DEFAULT_LOC = Environment.getExternalStorageDirectory().getAbsolutePath();
    public  final static int        LEFT = 0, RIGHT = 1;
    private int                     current = LEFT;
    private final int               titlesIds[] = { R.id.left_dir,  R.id.right_dir };
    private ListHelper              list[] = { null, null };
    public  FileCommander           c;
    public  View                    mainView, toolbar = null;
    private HorizontalScrollView    hsv;
    public  PanelsView              panelsView = null;
    public  boolean                 sxs, fingerFriendly = false;
    private boolean                 arrowsLegacy = false, warnOnRoot = true, rootOnRoot = false, toolbarShown = false;
    public  boolean                 volumeLegacy = true;
    private boolean                 selAtRight = true, disableOpenSelectOnly = false, disableAllActions = false;
    private float                   selWidth = 0.5f, downX = 0, downY = 0, x_start = -1;
    public  int                     scroll_back = 50, fnt_sz = 12;
    private StringBuffer            quickSearchBuf = null;
    private Toast                   quickSearchTip = null;
    private Favorites               favorites;
    private LocationBar             locationBar;
    private CommanderAdapter        destAdapter = null;
    public  ColorsKeeper            ck;
    
    public Panels( FileCommander c_, boolean sxs_ ) {
        c = c_;
        ck = new ColorsKeeper( c );
        current = LEFT;
        c.setContentView( R.layout.alt );
        mainView = c.findViewById( R.id.main );
        
        hsv = (HorizontalScrollView)c.findViewById( R.id.hrz_scroll );
        hsv.setHorizontalScrollBarEnabled( false );
        hsv.setSmoothScrollingEnabled( true );
        hsv.setOnTouchListener( this );
        final int GINGERBREAD = 9;
        if( android.os.Build.VERSION.SDK_INT >= GINGERBREAD )
            ForwardCompat.disableOverScroll( hsv );
        
        panelsView = ((PanelsView)c.findViewById( R.id.panels ));
        panelsView.init( c.getWindowManager() );
        initList( LEFT );
        initList( RIGHT );

        favorites   = new Favorites( c );
        locationBar = new LocationBar( c, this, favorites );
        
        setLayoutMode( sxs_ );
//        highlightCurrentTitle();
        
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
        try{ 
	        quickSearchBuf = new StringBuffer();
	        quickSearchTip = Toast.makeText( c, "", Toast.LENGTH_SHORT );
        }
        catch( Exception e ) {
			c.showMessage( "Exception on creating quickSearchTip: " + e );
		}
        focus();
    }
    public final boolean  getLayoutMode() {
        return sxs;
    }
    public final void setLayoutMode( boolean sxs_ ) {
        sxs = sxs_;
        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( c );
        applySettings( shared_pref, false );
        scroll_back = (int)( c.getWindowManager().getDefaultDisplay().getWidth() * 2. / 10 );        
        if( panelsView != null ) panelsView.setMode( sxs_ );
    }
    public final int getCurrent() {
        return current;
    }
    
    public final void showToolbar( boolean show ) {
        toolbarShown = show;
    }

    private final Drawable createButtonStates() {
        try {
            float bbb = Utils.getBrightness( ck.btnColor );
            int sc = Utils.setBrightness( ck.btnColor, 0.2f );            
            StateListDrawable states = new StateListDrawable();
            GradientDrawable bpd = Utils.getShadingEx( ck.btnColor, 1f );
            bpd.setStroke( 1, sc );
            bpd.setCornerRadius( 2 );
            GradientDrawable bnd = Utils.getShadingEx( ck.btnColor, bbb < 0.4f ? 0f : 0.6f );
            bnd.setStroke( 1, sc );
            bnd.setCornerRadius( 2 );
            states.addState(new int[] { android.R.attr.state_pressed }, bpd );
            states.addState(new int[] { }, bnd );
            return states;
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    public final void setToolbarButtons( CommanderAdapter ca ) {
        try {
            if( ca == null ) return;
            if( toolbarShown ) {
                if( toolbar == null ) {
                    LayoutInflater inflater = (LayoutInflater)c.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                    toolbar = inflater.inflate( R.layout.toolbar, (ViewGroup)mainView, true ).findViewById( R.id.toolbar );
                }
                if( toolbar == null ) {
                    Log.e( TAG, "Toolbar inflation has failed!" );
                    return;
                }
                toolbar.setVisibility( View.INVISIBLE );
                
                ViewGroup tb_holder = (ViewGroup)toolbar; 
                tb_holder.removeAllViews();
                /*
                if( btnColor == 0x00000000 ) {
                    tb_holder.layout( l, t, r, b )
                }
                */
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                 
                boolean keyboard = sharedPref.getBoolean( "show_hotkeys", true ) || 
                                c.getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS ;

                Utils.changeLanguage( c );
                ToolButtons tba = new ToolButtons();
                tba.restore( sharedPref, c );
                int adapter_bit = ca.getType();
                int bfs = fnt_sz + ( fingerFriendly ? 2 : 1 );
                for( int i = 0; i < tba.size(); i++ ) {
                    ToolButton tb = tba.get(i);
                    int bid = tb.getId();
                    if( tb.isVisible() && ( adapter_bit & tb.getSuitableAdapter() ) != 0 ) {
                        LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams( 
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
                        Button b = null;
                        if( ck.btnColor != 0x00000000 ) {
                            b = new Button( c, null, android.R.attr.buttonStyleSmall );
                            int vp = fingerFriendly ? 10 : 6;
                            b.setPadding( 6, vp, 6, vp );
                            float bbb = Utils.getBrightness( ck.btnColor );
                            b.setTextColor( bbb > 0.8f ? 0xFF000000 : 0xFFFFFFFF );
                            b.setTextSize( bfs );
                            Drawable bd = createButtonStates();
                            if( bd != null )
                                b.setBackgroundDrawable( bd );
                            else
                                b.setBackgroundResource( R.drawable.tool_button );
                            lllp.rightMargin = 2;
                        }
                        else {
                            b = new Button( c, null, fingerFriendly ?         
                                      android.R.attr.buttonStyle :                     
                                      android.R.attr.buttonStyleSmall );
                            lllp.rightMargin = -2;  // a button has invisible background around it
                        }
                        b.setLayoutParams( lllp );
                        
                        b.setId( bid );
                        b.setFocusable( false );
                        String caption = "";
                        if( keyboard ) {
                            char ch = tb.getBoundKey();
                            if( ch != 0 )
                                caption = ch + " "; 
                        }
                        b.setText( caption += tb.getCaption() );
                        b.setOnClickListener( c );
                        tb_holder.addView( b );
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
    	list[current].focus();
    }
    // View.OnFocusChangeListener implementation
    @Override
    public void onFocusChange( View v, boolean f ) {
        ListView flv = list[opposite()].flv;
        boolean opp = flv == v; 
        if( f && opp ) {
            //Log.v( TAG, "focus has changed to " + ( opposite()==LEFT?"LEFT":"RIGHT" ) );
            setPanelCurrent( opposite(), true );
        }
    
    }
    
    public ArrayList<Favorite> getFavorites() {
        return favorites;
    }
    
    public final boolean isCurrent( int q ) {
        return ( current == LEFT  && q == LEFT ) ||
               ( current == RIGHT && q == RIGHT );
    }
    private final void initList( int which ) {
        list[which] = new ListHelper( which, this );
        setPanelTitle( "", which );
    }

    public final void setPanelTitle( String s, int which ) {
        try {
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
                    title.setText( urlqs.unescape( Favorite.screenPwd( s ) ) );
                }
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
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
        if( mainView == null ) return;
        View title_bar = mainView.findViewById( R.id.titles );
        if( title_bar != null ) {
            int h = title_bar.getHeight();
            if( h == 0 ) h = 30;
            Drawable d = Utils.getShading( ck.ttlColor );
            if( d != null )
                title_bar.setBackgroundDrawable( d );
            else
                title_bar.setBackgroundColor( ck.ttlColor );
        }
    	highlightTitle( opposite(), false );
    	highlightTitle( current, true );
    }
    private final void highlightTitle( int which, boolean on ) {
        TextView title = (TextView)mainView.findViewById( titlesIds[which] );
        if( title != null ) {
            if( on ) {
                Drawable d = Utils.getShading( ck.selColor );
                if( d != null )
                    title.setBackgroundDrawable( d );
                else
                    title.setBackgroundColor( ck.selColor );
                title.setTextColor( ck.sfgColor );
            }
            else {
                title.setBackgroundColor( ck.selColor & 0x0FFFFFFF );
                float[] fgr_hsv = new float[3];
                Color.colorToHSV( ck.fgrColor, fgr_hsv );
                float[] ttl_hsv = new float[3];
                Color.colorToHSV( ck.ttlColor, ttl_hsv );
                fgr_hsv[1] *= 0.5;
                fgr_hsv[2] = ( fgr_hsv[2] + ttl_hsv[2] ) / 2;
                title.setTextColor( Color.HSVToColor( fgr_hsv ) );
            }
        }
        else
            Log.e( TAG, "title view was not found!" );
    }
    public final int getSelection( boolean one_checked ) {
        return list[current].getSelection( one_checked );
    }
    public final void setSelection( int i ) {
        setSelection( current, i, 0 );
    }
    public final void setSelection( int which, int i, int y_ ) {
        list[which].setSelection( i, y_ );
    }
    public final void setSelection( int which, String name ) {
    	list[which].setSelection( name );
    }
    public final File getCurrentFile() {
        try {
            CommanderAdapter ca = getListAdapter( true );
            if( ( ca.getType() & ( CA.LOCAL | CA.APPS ) ) != 0 ) {
                int pos = getSelection( true );
                if( pos < 0 ) return null;
                CommanderAdapter.Item item = (CommanderAdapter.Item)((ListAdapter)ca).getItem( pos ); 
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
        return 1 - current;
    }
    public final CommanderAdapter getListAdapter( boolean forCurrent ) {
        return list[forCurrent ? current : opposite()].getListAdapter();
    }
    public final int getWidth() {
        return mainView.getWidth();
    }
    public final void applyColors() {
        ck.restore();
        if( sxs ) {
            View div = mainView.findViewById( R.id.divider );
            if( div != null)
                div.setBackgroundColor( ck.ttlColor );
        }
         list[LEFT].applyColors( ck );
        list[RIGHT].applyColors( ck );
        
        ck.restoreTypeColors();
        CommanderAdapterBase.setTypeMaskColors( ck );
        highlightCurrentTitle();
    }
    public final void applySettings( SharedPreferences sharedPref, boolean init ) {
        try {
            applyColors();
            String fnt_sz_s = sharedPref.getString( "font_size", "12" );
            try {
                fnt_sz = Integer.parseInt( fnt_sz_s );
            }
            catch( NumberFormatException e ) {}
            
            String ffs = sharedPref.getString( "finger_friendly_a", "y" );
            boolean ff = false;
            if( "a".equals( ffs ) ) {
                Display disp = c.getWindowManager().getDefaultDisplay();
                Configuration config = c.getResources().getConfiguration();
                ff = config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES || 
                     disp.getWidth() < disp.getHeight();
            }
            else
                ff = "y".equals( ffs );
            
        	setFingerFriendly( ff, fnt_sz );
        	warnOnRoot   = sharedPref.getBoolean( "prevent_root", true );
            rootOnRoot   = sharedPref.getBoolean( "root_root", false );
            arrowsLegacy = sharedPref.getBoolean( "arrow_legc", false );
            volumeLegacy = sharedPref.getBoolean( "volume_legc", true );            
            toolbarShown = sharedPref.getBoolean( "show_toolbar", true );
            
            selAtRight = sharedPref.getBoolean( Prefs.SEL_ZONE + "_right", true );
            selWidth   = sharedPref.getInt( Prefs.SEL_ZONE + "_width", 50 ) / 100f;

            if( !init ) {
                list[LEFT].applySettings( sharedPref );
                list[RIGHT].applySettings( sharedPref );
               // setPanelCurrent( current );
            }
        }
        catch( Exception e ) {
            Log.e( TAG, "applySettings()", e );
        }
    }
    public void changeSorting( int sort_mode ) {
        CommanderAdapter ca = getListAdapter( true );
        
        int cur_mode = ca.setMode( 0, 0 );
        boolean asc = ( cur_mode & CommanderAdapter.MODE_SORT_DIR ) == CommanderAdapter.SORT_ASC;
        int sorted = cur_mode & CommanderAdapter.MODE_SORTING; 
        storeChoosedItems();
        if( sorted == sort_mode ) 
            ca.setMode( CommanderAdapter.MODE_SORT_DIR, asc ? CommanderAdapter.SORT_DSC : CommanderAdapter.SORT_ASC );
        else
            ca.setMode( CommanderAdapter.MODE_SORTING | CommanderAdapter.MODE_SORT_DIR, 
                                            sort_mode | CommanderAdapter.SORT_ASC );
        reStoreChoosedItems();
    }
    public void toggleHidden() {
        CommanderAdapter ca = getListAdapter( true );
        
        int cur_mode = ca.setMode( 0, 0 );
        int new_mode = ( cur_mode & CommanderAdapter.MODE_HIDDEN ) == CommanderAdapter.SHOW_MODE ?
                                         CommanderAdapter.HIDE_MODE : CommanderAdapter.SHOW_MODE;
        ca.setMode( CommanderAdapter.MODE_HIDDEN, new_mode );
        refreshList( current, true );
    }
    public final void refreshLists() {
        int was_current = current, was_opp = 1 - was_current;
        refreshList( current, true );
        if( sxs )
            refreshList( was_opp, false );
        else
            list[was_opp].setNeedRefresh();
    }
    public final void refreshList( int which, boolean was_current ) {
        list[which].refreshList( was_current );
    }
    public final void redrawLists() {
        list[current].askRedrawList();
        if( sxs )
            list[opposite()].askRedrawList();
        list[current].focus();
    }
    
    public void setFingerFriendly( boolean finger_friendly, int font_size ) {
        fingerFriendly = finger_friendly;
        try {
            for( int p = LEFT; p <= RIGHT; p++ ) {
                TextView title = (TextView)c.findViewById( titlesIds[p] );
                if( title != null ) {
                    title.setTextSize( font_size );
                    if( finger_friendly )
                        title.setPadding( 8, 10, 8, 10 );
                    else
                        title.setPadding( 8, 4, 8, 4 );
                }
                if( list[p] != null )
                    list[p].setFingerFriendly( finger_friendly );
            }
            locationBar.setFingerFriendly( finger_friendly, font_size );
        }
        catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }
    public final void makeOtherAsCurrent() {
        NavigateInternal( opposite(), getListAdapter( true ).getUri(), null, null );
    }
    public final void togglePanelsMode() {
        setLayoutMode( !sxs );
    }
    public final void togglePanels( boolean refresh ) {
        //Log.v( TAG, "toggle" );
        setPanelCurrent( opposite() );
    }
    
    public final void setPanelCurrent( int which ) {
        setPanelCurrent( which, false );
    }
    public final void setPanelCurrent( int which, boolean dont_focus ) {
        //Log.v( TAG, "setPanelCurrent: " + which + " dnf:" + dont_focus );
        if( !dont_focus && panelsView != null ) {
            panelsView.setMode( sxs );
        }
        current = which;
        if( !sxs ) {
            final int dir = current == LEFT ? HorizontalScrollView.FOCUS_LEFT : HorizontalScrollView.FOCUS_RIGHT;
            //Log.v( TAG, "fullScroll: " + dir );
            if( dont_focus )
                hsv.fullScroll( dir );
            else {
                hsv.post( new Runnable() {
                    public void run() {
                        //Log.v( TAG, "fullScroll: " + dir );
                        hsv.fullScroll( dir );
                    }
                } );
            }
        }
        else
            if( !dont_focus )
                list[current].focus();
        highlightCurrentTitle();
        setToolbarButtons( getListAdapter( true ) );
        if( list[current].needRefresh() ) 
            refreshList( current, false );
    }
    public final void showSizes() {
        storeChoosedItems();
        getListAdapter( true ).reqItemsSize( getSelectedOrChecked() );
	}
    public final void checkItems( boolean set, String mask, boolean dir, boolean file ) {
        list[current].checkItems( set, mask, dir, file );
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
                NavigateInternal( which, uri, null, posTo );
            }
            else if( whichButton == DialogInterface.BUTTON_NEUTRAL ) {
                NavigateInternal( which, sdcard, null, null );                
            }
            else
                c.finish();
        	idialog.dismiss();
        }
    }
    protected final boolean isSafeLocation( String path ) {
        return path.startsWith( DEFAULT_LOC ) || path.startsWith( "/sdcard" ) || path.startsWith( "/mnt/" );
    }
    public final void Navigate( int which, Uri uri, Credentials crd, String posTo ) {
    	if( uri == null ) return;
    	String scheme = uri.getScheme(), path = uri.getPath();
    	
    	if( ( scheme == null || scheme.equals( "file") ) && 
    	      ( path == null || !isSafeLocation( path ) ) ) {
    	    if( warnOnRoot ) {
                CommanderAdapter ca = list[which].getListAdapter();
                if( ca != null && CA.FS == ca.getType() ) {
                    String cur_path = ca.toString();
                    if( cur_path != null && isSafeLocation( cur_path ) ) {
                		try {
                    		new NavDialog( c, which, uri, posTo );
            			} catch( Exception e ) {
            				Log.e( TAG, "Navigate()", e );
            			}
            			return;
                    }
                }
    	    }
    	    else if( rootOnRoot )
    	        uri = uri.buildUpon().scheme( "root" ).build();
    	}
    	NavigateInternal( which, uri, crd, posTo );
    }
    
    private final void NavigateInternal( int which, Uri uri, Credentials crd, String posTo ) {
        ListHelper list_h = list[which];
        list_h.Navigate( uri, crd, posTo, which == current );
    }

    public final void recoverAfterRefresh( String item_name, int which ) {
        try {
            if( which >= 0  )
                list[which].recoverAfterRefresh( item_name );
            else
                list[current].recoverAfterRefresh( which == current );
            refreshPanelTitles();
            //setPanelCurrent( current, true ); the current panel is set by set focus
        } catch( Exception e ) {
            Log.e( TAG, "refreshList()", e );
        }
    }
    
    public void login( Credentials crd ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca != null ) {
            ca.setCredentials( crd );
            list[current].refreshList( true );
        }
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
    
    public final void Destroy() {
        Log.i( TAG, "Destroing" );
        try {
            getListAdapter( false ).prepareToDestroy();
            getListAdapter( true  ).prepareToDestroy();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
    public final void tryToSend() {
        File f = getCurrentFile();
        if( f != null ) {
            String ext = Utils.getFileExt( f.getName() );
            String mime = Utils.getMimeByExt( ext );
            if( mime != null && !mime.startsWith( "image/" )
                             && !mime.startsWith( "audio/" )
                             && !mime.startsWith( "video/" ) )
                mime = null;
            Intent i = new Intent( Intent.ACTION_SEND );
            i.setType( mime == null ? "*/*" : mime );
            i.putExtra( Intent.EXTRA_SUBJECT, f.getName() );
            SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( c );
            String esc_fn = Utils.escapePath( f.getAbsolutePath() );
            boolean use_content = shared_pref.getBoolean( "send_content", true );
            Uri uri = Uri.parse( use_content ? FileProvider.URI_PREFIX + esc_fn :
                                                             "file://" + esc_fn );  
            i.putExtra( Intent.EXTRA_STREAM, uri );
            c.startActivity( Intent.createChooser( i, c.getString( R.string.send_title ) ) );            
        }        
    }    
    public final void tryToOpen() {
        File f = getCurrentFile();
        if( f != null ) {
            Intent intent = new Intent( Intent.ACTION_VIEW );
            intent.setDataAndType( Uri.fromFile( f ), "*/*" );
            c.startActivity( Intent.createChooser( intent, c.getString( R.string.open_title ) ) );            
        }        
    }    
    public final void copyName() {
        try {
            CommanderAdapter ca = getListAdapter( true );
            if( ca == null ) return;
            ClipboardManager clipboard = (ClipboardManager)c.getSystemService( Context.CLIPBOARD_SERVICE );
            int pos = getSelection( true );
            if( pos >= 0 ) {
                String in = ca.getItemName( pos, true );
                if( in != null ) {
                    if( in.startsWith( RootAdapter.DEFAULT_LOC ) )
                        in = Uri.parse( in ).getPath();
                    clipboard.setText( in );
                }
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }
    public final void addCurrentToFavorites() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        Uri u = ca.getUri();
        favorites.addToFavorites( u, ca.getCredentials() );
        c.showMessage( c.getString( R.string.fav_added, Favorite.screenPwd( u ) ) );
    }
    
    public final void faveSelectedFolder() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        Uri u = ca.getUri();
        if( u != null ) {
            int pos = getSelection( true );
            if( pos < 0 ) return;
            Uri to_add = u.buildUpon().appendEncodedPath( ca.getItemName( pos, false ) ).build();
            if( to_add != null ) {
                favorites.addToFavorites( to_add, ca.getCredentials() );
                c.showMessage( c.getString( R.string.fav_added, Favorite.screenPwd( to_add ) ) );
            }
        }
    }    

    public final void openForEdit( String file_name ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca instanceof FavsAdapter ) {
            FavsAdapter fa = (FavsAdapter)ca;
            int pos = getSelection( true );
            if( pos > 0 ) fa.editItem( pos );
            return;
        }
        try {
            Uri u;
            if( file_name == null || file_name.length() == 0 ) {
                int pos = getSelection( true );
                CommanderAdapter.Item item = (CommanderAdapter.Item)((ListAdapter)ca).getItem( pos );
                if( item == null ) {
                    c.showError( c.getString( R.string.cant_open ) );
                    return;
                }
                if( item.dir ) {
                    c.showError( c.getString( R.string.cant_open_dir, item.name ) );
                    return;
                }
                u = ca.getItemUri( pos );
            }
            else 
                u = Uri.parse( file_name );
            if( u == null ) return;
            final String GC_EDITOR = Editor.class.getName();
            String full_class_name = null;
            String scheme = u.getScheme();
            boolean local = CA.isLocal( scheme );
            if( local )
                u = u.buildUpon().scheme( "file" ).authority( "" ).build();
            else {
                if( "root".equals( scheme ) || "smb".equals( scheme ) || "ftp".equals( scheme ) ) 
                    full_class_name = GC_EDITOR;
                else {
                    c.showMessage( c.getString( R.string.edit_err ) );
                    return;
                }
            }
            Intent i = new Intent( Intent.ACTION_EDIT );
            if( full_class_name == null ) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                full_class_name = sharedPref.getString( "editor_activity", GC_EDITOR );
            }
            if( full_class_name.length() > 0 ) {
                int last_dot_pos = full_class_name.lastIndexOf('.');
                if( last_dot_pos < 0 ) {
                    c.showMessage( "Invalid class name: " + full_class_name );
                    full_class_name = GC_EDITOR;
                    last_dot_pos = full_class_name.lastIndexOf('.');
                }
                i.setClassName( full_class_name.substring( 0, last_dot_pos ), full_class_name );
            }
            i.setDataAndType( u, "text/plain" );
            Credentials crd = ca.getCredentials();
            if( crd != null )
                i.putExtra( Credentials.KEY, crd );
            c.startActivity( i );
        }
        catch( ActivityNotFoundException e ) {
            c.showMessage( "Activity Not Found: " + e );
        }
        catch( IndexOutOfBoundsException e ) {
            c.showMessage( "Bad activity class name: " + e );
        }
    }
    public final void openForView() {
        int pos = getSelection( true );
        if( pos < 0 ) return;
        String name = null;
        try {
            CommanderAdapter ca = getListAdapter( true );
            Uri uri = ca.getItemUri( pos );
            if( uri == null ) return;
            CommanderAdapter.Item item = (CommanderAdapter.Item)((ListAdapter)ca).getItem( pos );
            if( item.dir ) {
                showSizes();
                return;
            }
            String mime = Utils.getMimeByExt( Utils.getFileExt( item.name ) );
            if( mime == null ) mime = "application/octet-stream";                
            Intent i = new Intent( c, mime.startsWith( "image/" ) ? 
                    PictureViewer.class : TextViewer.class );
            i.setDataAndType( uri, mime );
            Credentials crd = ca.getCredentials();
            if( crd != null )
                i.putExtra( Credentials.KEY, crd );
            c.startActivity( i );
        }
        catch( Exception e ) {
            Log.e( TAG, "Can't view the file " + name, e );
        }
    }
    
    public final int getNumItemsChecked() {
        return list[current].getNumItemsChecked();
    }
    public final int getNumItemsSelectedOrChecked() {
        return list[current].getNumItemsSelectedOrChecked();
    }

    public final String getActiveItemsSummary() {
        return list[current].getActiveItemsSummary();
    }
    public final SparseBooleanArray getSelectedOrChecked() {
        return list[current].getSelectedOrChecked();
    }
    /**
     * @return 0 - nothing selected, 1 - a file, -1 - a folder, otherwise the number 
    public final int getNumItemsSelectedOrChecked() {
    	int checked = getNumItemsChecked();
    	return checked;
    }
     */
    public final Uri getFolderUriWithAuth( boolean active ) {
        CommanderAdapter ca = getListAdapter( active );
        Uri u = ca.getUri();
        if( u != null ) {
            Credentials crd = ca.getCredentials();
            if( crd != null )
                return Utils.getUriWithAuth( u, crd );
        }
        return u; 
    }
    public final String getSelectedItemName() {
        return getSelectedItemName( false );
    }
    public final String getSelectedItemName( boolean full ) {
        int pos = getSelection( true );
        return pos < 0 ? null : getListAdapter( true ).getItemName( pos, full );
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
		locationBar.openGoPanel( current, getFolderUriWithAuth( true ) );
	}
    public final void operationFinished() {
        if( null != destAdapter )
            destAdapter = null;
    }    
    
    public final void copyFiles( String dest, boolean move ) {
        try {
            if( dest == null ) return;
            SparseBooleanArray items = getSelectedOrChecked();
            CommanderAdapter cur_adapter = getListAdapter( true );
            Uri dest_uri = Uri.parse( dest );
            if( Favorite.isPwdScreened( dest_uri ) ) {
                dest_uri = Favorite.borrowPassword( dest_uri, getFolderUriWithAuth( false ) );
                if( dest_uri == null ) {
                    c.showError( c.getString( R.string.inv_dest ) );
                    return;
                }
            }
            if( getNumItemsSelectedOrChecked() == 1 ) {
                int pos = getSelection( true ); 
                if( pos <= 0 ) return;
                final char SLC = File.separator.charAt( 0 );
                final boolean COPY = true;
                if( dest.indexOf( SLC ) < 0 ) { // only the file name is specified 
                    cur_adapter.renameItem( pos, dest, COPY );
                    return;
                }
                if( dest.charAt( 0 ) == SLC ) { // local FS only 
                    File dest_file = new File( dest );
                    if( dest_file.isFile() && !dest_file.isDirectory() ) {
                        cur_adapter.renameItem( pos, dest, COPY );
                        return;
                    }
                }
            }

            CommanderAdapter oth_adapter = getListAdapter( false );
            if( oth_adapter == null || !dest.equals( Favorite.screenPwd( oth_adapter.toString() ) ) ) {
                if( dest_uri == null ) {
                    c.showError( c.getString( R.string.inv_dest ) );
                    return;
                }
                String scheme = dest_uri.getScheme();
                int type_id = CA.GetAdapterTypeId( scheme );
                oth_adapter = CA.CreateAdapter( type_id, c );
                if( oth_adapter == null ) {
                    c.showError( c.getString( R.string.inv_dest ) );
                    return;
                }
                oth_adapter.setUri( dest_uri );
            }
            //c.showDialog( Dialogs.PROGRESS_DIALOG );
            destAdapter = oth_adapter;
            cur_adapter.copyItems( items, destAdapter, move );
            // TODO: getCheckedItemPositions() returns an empty array after a failed operation. why? 
            list[current].flv.clearChoices();
        }
        catch( Exception e ) {
            Log.e( TAG, "copyFiles()", e );
        }
    }

    public final void renameItem( String new_name ) {
        CommanderAdapter adapter = getListAdapter( true );
        int pos = getSelection( true );
        if( pos >= 0 ) {
            adapter.renameItem( pos, new_name, false );
            list[current].setSelection( new_name );
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
        list[current].setSelection( new_name );
    }

    public final void createZip( String new_zip_name ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca instanceof FSAdapter ) {
            SparseBooleanArray cis = getSelectedOrChecked();
            if( cis == null || cis.size() == 0 ) {
                c.showError( c.getString( R.string.op_not_alwd ) );
                return;
            }
            FSAdapter fsa = (FSAdapter)ca;
            ZipAdapter z = new ZipAdapter( c );
            z.Init( c );
            destAdapter = z;
            File[] files = fsa.bitsToFiles( cis );
            z.createZip( files, Utils.mbAddSl( ca.toString() ) + new_zip_name );
        }
        else
            c.showError( c.getString( R.string.not_supported ) );
    }
    
    public final void deleteItems() {
    	//c.showDialog( Dialogs.PROGRESS_DIALOG );
        if( getListAdapter( true ).deleteItems( getSelectedOrChecked() ) )
            list[current].flv.clearChoices();
    }

    // /////////////////////////////////////////////////////////////////////////////////

    /**
     * An AdapterView.OnItemSelectedListener implementation
     */
    @Override
    public void onItemSelected( AdapterView<?> listView, View itemView, int pos, long id ) {
        //Log.v( TAG, "Selected item " + pos );
    	locationBar.closeGoPanel();
    	int which = list[current].id == listView.getId() ? current : opposite();
        list[which].setCurPos( pos );
    }
    @Override
    public void onNothingSelected( AdapterView<?> listView ) {
        //Log.v( TAG, "NothingSelected" );
    	resetQuickSearch();
    }
    /**
     * An AdapterView.OnItemClickListener implementation 
     */
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
        //Log.v( TAG, "onItemClick" );
        
    	locationBar.closeGoPanel();
    	resetQuickSearch();
    	ListView flv = list[current].flv;
        if( flv != parent ) {
            togglePanels( false );
          	Log.e( TAG, "onItemClick. current=" + current + ", parent=" + parent.getId() );
        }
        if( position == 0 )
            flv.setItemChecked( 0, false ); // parent item never selected
        list[current].setCurPos( position );
        if( disableAllActions ) {
            disableAllActions = false;
            disableOpenSelectOnly = false;
            SparseBooleanArray cis = flv.getCheckedItemPositions();
            flv.setItemChecked( position, !cis.get( position ) );
            return;
        }
        if( disableOpenSelectOnly && ( ((CommanderAdapter)flv.getAdapter()).getType() & CA.CHKBL ) != 0 )
            disableOpenSelectOnly = false;
        else { 
            openItem( position );
            flv.setItemChecked( position, false );
        }
    }
    public void openItem( int position ) {
        ListHelper l = list[current];
        l.setCurPos( position );
        ((CommanderAdapter)l.flv.getAdapter()).openItem( position );
    }
    public void goUp() {
        getListAdapter(true).openItem(0);
    }
    
    /**
     * View.OnTouchListener implementation 
     */
    @Override
    public boolean onTouch( View v, MotionEvent event ) {
    	resetQuickSearch();
        if( v == hsv ) {
            if( x_start < 0. && event.getAction() == MotionEvent.ACTION_MOVE )
                x_start = event.getX();
            else
            if( x_start >= 0. && event.getAction() == MotionEvent.ACTION_UP ) {
                float d = event.getX() - x_start;
                x_start = -1;
                final int to_which;
                if( Math.abs( d ) > scroll_back )
                    to_which = d > 0 ? LEFT : RIGHT;
                else
                    to_which = current ==  LEFT ? LEFT : RIGHT;
                setPanelCurrent( to_which );
                return true;
            }
        }
        else
	    if( v instanceof ListView ) {
            if( v == list[opposite()].flv)
                togglePanels( false );
	        
	    	locationBar.closeGoPanel();
	        switch( event.getAction() ) {
	        case MotionEvent.ACTION_DOWN: {
                    downX = event.getX();
                    downY = event.getY();
                    disableOpenSelectOnly = event.getX() > v.getWidth() * selWidth;
                    if( !selAtRight )
                        disableOpenSelectOnly = !disableOpenSelectOnly;
    	            break;
    	        }
	        case MotionEvent.ACTION_UP: {
                    int deltaX = (int)(event.getX() - downX);
                    int deltaY = (int)(event.getY() - downY);
                    int absDeltaX = Math.abs( deltaX );
                    int absDeltaY = Math.abs( deltaY );
                    
                    if( absDeltaY > 10 || absDeltaX > 10 )
                        disableOpenSelectOnly = false;
                    list[current].focus();
       	            break;
    	        }
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
    	
    	//Log.v( TAG, "panel key:" + keyCode + ", uchar:" + event.getUnicodeChar() + ", shift: " + event.isShiftPressed() );
    	
	    if( v instanceof ListView ) {
	    	locationBar.closeGoPanel();
	    	char ch = (char)event.getUnicodeChar();
	    	if( ch >= 'A' && ch <= 'z' || ch == '.' ) {
	    		quickSearch( ch );
	    		return true;
	    	}
	    	resetQuickSearch();
	        switch( ch ) {
	        case '(':
	        case ')': {
		        	int which = ch == '(' ? LEFT : RIGHT;
		            locationBar.openGoPanel( which, getFolderUriWithAuth( isCurrent( which ) ) );
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
                setLayoutMode( !sxs );
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
	            openForView();
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
            case ' ':
                list[current].checkItem( true );
                return true;
	        }
	    	switch( keyCode ) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DEL:
                if( !c.backExit() )
                    goUp();
                return true;
	        case KeyEvent.KEYCODE_DPAD_UP:
	        case KeyEvent.KEYCODE_DPAD_DOWN:	
		    	resetQuickSearch();
		    	if( event.isShiftPressed() ) {
                   list[current].checkItem(false);
                   // ListView will not move to next item on Shift+DPAD, so let's remove the Shift
                   // bit from meta state and re-dispatch the event.
                   KeyEvent shiftStrippedEvent = new KeyEvent(event.getDownTime(), event.getEventTime(),
                           KeyEvent.ACTION_DOWN, keyCode, event.getRepeatCount(),
                           event.getMetaState() & ~(KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON)); 
                   return v.onKeyDown(keyCode, shiftStrippedEvent);		    	
		    	}
		    	return false;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if( arrowsLegacy ) {
                    list[current].checkItem( true );
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if( volumeLegacy ) {
    	            list[current].checkItem( true );
    	            return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
	            if( arrowsLegacy ) { 
	                togglePanels( false );
	                return true;    
	            }
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
    		locationBar.closeGoPanel();
	        int which = view_id == titlesIds[LEFT] ? LEFT : RIGHT;
	        if( which == current ) {
	            focus();
	            refreshList( current, true );
	        }
	        else
	            togglePanels( true );
    	}
    }
    @Override
    public boolean onLongClick( View v ) {
    	int which = v.getId() == titlesIds[LEFT] ? LEFT : RIGHT;
        locationBar.openGoPanel( which, getFolderUriWithAuth( isCurrent( which ) ) );
    	return true;
    }

    /*
     * ListView.OnScrollListener implementation 
     */
    public void onScroll( AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount ) {
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
        list[current].storeChoosedItems();
    }
    
    public void reStoreChoosedItems() {
        list[current].reStoreChoosedItems();
    }
    
    class State {
        private final static String LU = "LEFT_URI",  RU = "RIGHT_URI";
    	private final static String LC = "LEFT_CRD",  RC = "RIGHT_CRD";
    	private final static String LI = "LEFT_ITEM", RI = "RIGHT_ITEM";
    	private final static String CP = "LAST_PANEL";
        private final static String FU = "FAV_URIS";
        private final static String FV = "FAVS";
        private int         current;
        private Credentials leftCrd, rightCrd;
        private Uri         leftUri, rightUri;
        private String      leftItem, rightItem;
        private String      favs, fav_uris;
                
        public void store( Bundle b ) {
            b.putInt( CP, current );
            b.putParcelable( LC, leftCrd );
            b.putParcelable( RC, rightCrd );
            b.putParcelable( LU, leftUri );
            b.putParcelable( RU, rightUri );
            b.putString( LI, leftItem );
            b.putString( RI, rightItem );
            b.putString( FV, favs );
        }
        public void restore( Bundle b ) {
            current   = b.getInt( CP );
            leftCrd   = b.getParcelable( LC ); 
            rightCrd  = b.getParcelable( RC ); 
            leftUri   = b.getParcelable( LU );
            rightUri  = b.getParcelable( RU );
            leftItem  = b.getString( LI );
            rightItem = b.getString( RI );
            favs      = b.getString( FV );
            if( favs == null || favs.length() == 0 )
                fav_uris = b.getString( FU );
        }
        public void store( SharedPreferences.Editor e ) {
            e.putInt( CP, current );
            e.putString( LU, leftUri  != null ?  leftUri.toString() : "" );
            e.putString( RU, rightUri != null ? rightUri.toString() : "" );
            e.putString( LC, leftCrd  != null ?  leftCrd.exportToEncriptedString() : "" );
            e.putString( RC, rightCrd != null ? rightCrd.exportToEncriptedString() : "" );
            e.putString( LI, leftItem );
            e.putString( RI, rightItem );
            e.putString( FV, favs );
        }
        public void restore( SharedPreferences p ) {
            String left_uri_s = p.getString( LU, null );
            if( Utils.str( left_uri_s ) )
                leftUri = Uri.parse( left_uri_s );
            String right_uri_s = p.getString( RU, null );
            if( Utils.str( right_uri_s ) )
                rightUri = Uri.parse( right_uri_s );
            
            String left_crd_s = p.getString( LC, null );
            if( Utils.str( left_crd_s ) )
                leftCrd = Credentials.createFromEncriptedString( left_crd_s );
            String right_crd_s = p.getString( RC, null );
            if( Utils.str( right_crd_s ) )
                rightCrd = Credentials.createFromEncriptedString( right_crd_s );
            leftItem  = p.getString( LI, null );
            rightItem = p.getString( RI, null );
            current   = p.getInt( CP, LEFT );
            favs      = p.getString( FV, "" );
            if( favs == null || favs.length() == 0 )
                fav_uris = p.getString( FU, "" );
        }
    }
    public State getState() {
        Log.v( TAG, "getState()");
        State s = new State();
        s.current = current;
        try {
            CommanderAdapter  left_adapter = (CommanderAdapter)list[LEFT].getListAdapter();
            s.leftUri = left_adapter.getUri();
            s.leftCrd = left_adapter.getCredentials();
            int pos = list[LEFT].getCurPos();
            s.leftItem = pos >= 0 ? left_adapter.getItemName( pos, false ) : "";

            CommanderAdapter right_adapter = (CommanderAdapter)list[RIGHT].getListAdapter();
            s.rightUri = right_adapter.getUri();
            s.rightCrd = right_adapter.getCredentials();
            pos = list[RIGHT].getCurPos();
            s.rightItem = pos >= 0 ? right_adapter.getItemName( pos, false ) : "";
            
            s.favs = favorites.getAsString();
        } catch( Exception e ) {
            Log.e( TAG, "getState()", e );
        }
        return s;
    }
	public void setState( State s, int dont_restore ) {
	    Log.v( TAG, "setState()" );
	    if( s == null ) return;
    	resetQuickSearch();
        if( s.favs != null && s.favs.length() > 0 )
            favorites.setFromString( s.favs );
        else
            if( s.fav_uris != null )
                favorites.setFromOldString( s.fav_uris );
        if( dont_restore != LEFT && dont_restore != RIGHT )
    	    current = s.current;
    	if( dont_restore != LEFT ) {
        	Uri lu = s.leftUri != null ? s.leftUri : Uri.parse( "home:" );
            ListHelper list_h = list[LEFT];
            list_h.Navigate( lu, s.leftCrd, s.leftItem, s.current == LEFT );
    	}
    	if( dont_restore != RIGHT ) {
            Uri ru = s.rightUri != null ? s.rightUri : Uri.parse( "home:" ); 
            ListHelper list_h = list[RIGHT];
            list_h.Navigate( ru, s.rightCrd, s.rightItem, s.current == RIGHT );
    	}
        applyColors();
    }
}
