package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.FSAdapter;
import com.ghostsq.commander.adapters.FTPAdapter;
import com.ghostsq.commander.adapters.FavsAdapter;
import com.ghostsq.commander.adapters.ZipAdapter;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.favorites.Favorites;
import com.ghostsq.commander.favorites.LocationBar;
import com.ghostsq.commander.toolbuttons.ToolButton;
import com.ghostsq.commander.toolbuttons.ToolButtons;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Bundle;
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
    public static final String DEFAULT_LOC = Environment.getExternalStorageDirectory().getAbsolutePath();
    public  final static int LEFT = 0, RIGHT = 1;
    private int     current = LEFT, navigated = -1;
    private final int titlesIds[] = { R.id.left_dir,  R.id.right_dir };
    private ListHelper list[] = { null, null };
    public  FileCommander c;
    public  View mainView, toolbar = null;
    public  ViewFlipper mFlipper_   = null;
    public  PanelsView  panelsView = null;
    private int ttlColor, fgrColor, selColor, curColor, btnColor;
    private boolean arrowsLegacy = false, warnOnRoot = true, rootOnRoot = false, toolbarShown = false;
    public  boolean volumeLegacy = true;
    private boolean disableOpenSelectOnly = false, disableAllActions = false;
    public  int fnt_sz = 12;
    private float downX = 0, downY = 0;
    private StringBuffer     quickSearchBuf = null;
    private Toast            quickSearchTip = null;
    private Favorites        favorites;
    private LocationBar      locationBar;
    private CommanderAdapter destAdapter = null;
    public  boolean sxs, fingerFriendly = false;
    
    public Panels( FileCommander c_, boolean sxs_ ) {
        c = c_;
        Resources  r = c.getResources();
        ttlColor = r.getColor( R.color.ttl_def ); 
        fgrColor = r.getColor( R.color.fgr_def );
        selColor = r.getColor( R.color.sel_def );
        btnColor = Prefs.getDefaultColor( c, Prefs.BTN_COLORS, false );
        curColor = 0;
        
        current = LEFT;
        c.setContentView( R.layout.alt );
        mainView = c.findViewById( R.id.main );
        
        //mFlipper = ((ViewFlipper)c.findViewById( R.id.flipper ));
        panelsView = ((PanelsView)c.findViewById( R.id.panels ));
        
        initList( LEFT );
        initList( RIGHT );

        setLayoutMode( sxs_ );
       
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
        favorites = new Favorites( c );
        locationBar = new LocationBar( c, this, favorites );
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
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
        applySettings( sharedPref, false );

        if( panelsView != null ) panelsView.setMode( sxs_, current );
    }
    public int getCurrent() {
        return current;
    }
    
    public final void showToolbar( boolean show ) {
        toolbarShown = show;
    }

    private final Drawable createButtonStates() {
        try {
            float bbb = Utils.getBrightness( btnColor );
            int sc = Utils.setBrightness( btnColor, 0.2f );            
            StateListDrawable states = new StateListDrawable();
            GradientDrawable bpd = Utils.getShadingEx( btnColor, 1f );
            bpd.setStroke( 1, sc );
            bpd.setCornerRadius( 2 );
            GradientDrawable bnd = Utils.getShadingEx( btnColor, bbb < 0.4f ? 0f : 0.6f );
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
                        Button b = null;
                        if( btnColor != 0x00000000 ) {
                            b = new Button( c, null, android.R.attr.buttonStyleSmall );
                            int vp = fingerFriendly ? 10 : 6;
                            b.setPadding( 6, vp, 6, vp );
                            float bbb = Utils.getBrightness( btnColor );
                            b.setTextColor( bbb > 0.8f ? 0xFF000000 : 0xFFFFFFFF );
                            b.setTextSize( bfs );
                            Drawable bd = createButtonStates();
                            if( bd != null )
                                b.setBackgroundDrawable( bd );
                            else
                                b.setBackgroundResource( R.drawable.tool_button );
                            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams( 
                                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
                            lllp.rightMargin = 2;
                            b.setLayoutParams( lllp );
                        }
                        else {
                            b = new Button( c, null, fingerFriendly ?         
                                      android.R.attr.buttonStyle :                     
                                      android.R.attr.buttonStyleSmall );                                  
                        }
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
        if( sxs && f && list[current].flv != v ) {
            togglePanels( false );
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
            Drawable d = Utils.getShading( ttlColor );
            if( d != null )
                title_bar.setBackgroundDrawable( d );
            else
                title_bar.setBackgroundColor( ttlColor );
        }
    	highlightTitle( opposite(), false );
    	highlightTitle( current, true );
    }
    private final void highlightTitle( int which, boolean on ) {
        TextView title = (TextView)mainView.findViewById( titlesIds[which] );
        if( title != null ) {
            if( on ) {
                int h = title.getHeight();
                if( h == 0 ) h = 30;
                Drawable d = Utils.getShading( selColor );
                if( d != null )
                    title.setBackgroundDrawable( d );
                else
                    title.setBackgroundColor( selColor );
                title.setTextColor( fgrColor );
            }
            else {
                title.setBackgroundColor( selColor & 0x0FFFFFFF );
                float[] fgr_hsv = new float[3];
                Color.colorToHSV( fgrColor, fgr_hsv );
                float[] ttl_hsv = new float[3];
                Color.colorToHSV( ttlColor, ttl_hsv );
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
            ListAdapter a = (ListAdapter)getListAdapter( true );
            if( a instanceof FSAdapter ) {
                int pos = getSelection( true );
                if( pos < 0 ) return null;
                CommanderAdapter.Item item = (CommanderAdapter.Item)a.getItem( pos ); 
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
        SharedPreferences color_pref = c.getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
        int bg_color = c.getResources().getColor( R.color.bgr_def );
        if( color_pref != null ) {
            bg_color = color_pref.getInt( Prefs.BGR_COLORS,  bg_color );
            fgrColor = color_pref.getInt( Prefs.FGR_COLORS,  fgrColor );
            curColor = color_pref.getInt( Prefs.CUR_COLORS,  curColor );
            selColor = color_pref.getInt( Prefs.SEL_COLORS,  selColor );
            ttlColor = color_pref.getInt( Prefs.TTL_COLORS,  ttlColor );
            btnColor = color_pref.getInt( Prefs.BTN_COLORS,  btnColor );
        }
        if( sxs ) {
            View div = mainView.findViewById( R.id.divider );
            if( div != null)
                div.setBackgroundColor( ttlColor );
        }
         list[LEFT].applyColors( bg_color, fgrColor, selColor, curColor );
        list[RIGHT].applyColors( bg_color, fgrColor, selColor, curColor );
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
        	warnOnRoot =  sharedPref.getBoolean( "prevent_root", true );
            rootOnRoot = sharedPref.getBoolean( "root_root", false );
            arrowsLegacy = sharedPref.getBoolean( "arrow_legc", false );
            volumeLegacy = sharedPref.getBoolean( "volume_legc", true );            
            toolbarShown = sharedPref.getBoolean( "show_toolbar", true );
            if( !init ) {
                list[LEFT].applySettings( sharedPref );
                list[RIGHT].applySettings( sharedPref );
                setPanelCurrent( current );
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
        refreshList( current );
    }
    public final void refreshLists() {
        refreshList( current );
        if( sxs )
            refreshList( opposite() );
        else
            list[opposite()].setNeedRefresh();
    }
    public final void refreshList( int which ) {
        list[which].refreshList();
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
        }
        catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }
    public final void makeOtherAsCurrent() {
        NavigateInternal( opposite(), getListAdapter( true ).getUri(), null );
    }
    public final void togglePanelsMode() {
        setLayoutMode( !sxs );
    }
    public final void togglePanels( boolean refresh ) {
        //Log.v( TAG, "toggle" );
        setPanelCurrent( opposite() );
        if( !sxs && ( refresh || list[current].needRefresh() ) ) 
            refreshList( current );
    }
    
    public final void setPanelCurrent( int which ) {
        //Log.v( TAG, "setPanelCurrent: " + which );
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
        list[current].focus();
        highlightCurrentTitle();
        setToolbarButtons( getListAdapter( true ) );
    }
    public final void showSizes() {
        storeChoosedItems();
        getListAdapter( true ).reqItemsSize( getSelectedOrChecked() );
	}
    public final void checkItems( boolean set, String mask ) {
        list[current].checkItems( set, mask );
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
    protected final boolean isSafeLocation( String path ) {
        return path.startsWith( DEFAULT_LOC ) || path.startsWith( "/sdcard" ) || path.startsWith( "/mnt/" );
    }
    public final void Navigate( int which, Uri uri, String posTo ) {
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
    	NavigateInternal( which, uri, posTo );
    }
    
    private final void NavigateInternal( int which, Uri uri, String posTo ) {
        ListHelper list_h = list[which];
        list_h.Navigate( uri, posTo );
        if( which == current )
            navigated = which; 
    }

    public final void recoverAfterRefresh( String item_name, int which ) {
        try {
            if( which >= 0 ) {
                list[which].recoverAfterRefresh( item_name, which == current );
                if( navigated >= 0 && which == navigated ) {
                    navigated = -1;
                    list[which].focus();
                }
            }
            else
                list[current].recoverAfterRefresh();
            refreshPanelTitles();
        } catch( Exception e ) {
            Log.e( TAG, "refreshList()", e );
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
            if( mime != null && !mime.startsWith( "image/" ) && !mime.startsWith( "audio/" ) )
                mime = null;
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
        ClipboardManager clipboard = (ClipboardManager)c.getSystemService( Context.CLIPBOARD_SERVICE );
        int pos = getSelection( true );
        if( pos >= 0 ) {
            String in = ca.getItemName( pos, true );
            clipboard.setText( in );
        }
    }
    public final void addCurrentToFavorites() {
        Uri uri = getFolderUri( true );
        /*
         *   Temporary!!!
         *   The adapter should return the credentials from the getUri()
         *   Since we don't update the SMB app now, here is a workaround    
         */
        String ui = uri.getUserInfo();
        if( ui == null || ui.length() == 0 ) {
            CommanderAdapter ca = getListAdapter( true );
            Uri uri_ = Uri.parse( ca.toString() );
            if( uri_ != null ) {
                String ui_ = uri_.getUserInfo();
                if( ui_ != null && ui_.length() > 0 )
                    uri = Favorite.updateUserInfo( uri, ui_ );
            }
        }
        /*
         * !!! end the temporary code block
         */
        
        favorites.addToFavorites( uri );
        c.showMessage( c.getString( R.string.fav_added, Favorite.screenPwd( uri ) ) );
    }
    public final void favFolder() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        Uri u = ca.getUri();
        if( u != null ) {
            int pos = getSelection( true );
            if( pos < 0 ) return;
            Uri to_add = u.buildUpon().appendEncodedPath( ca.getItemName( pos, false ) ).build();
            if( to_add != null ) {
                favorites.addToFavorites( to_add );
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
            final String GC_EDITOR = c.getString( R.string.value_editor_activity );
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
                }
                i.setClassName( full_class_name.substring( 0, last_dot_pos ), full_class_name );
            }
            i.setDataAndType( u, "text/plain" );
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
            name = ca.getItemName( pos, true );
            if( name == null ) return;
            Uri uri;
            if( ca instanceof FSAdapter ) {
                File f = new File( name );
                if( !f.exists() ) return;
                if( !f.isFile() ) {
                    showSizes();
                    return;
                }
                uri = Uri.parse( "file://" + f.getAbsolutePath() ); 
            }
            else
                uri = Uri.parse( name );
            CommanderAdapter.Item item = (CommanderAdapter.Item)((ListAdapter)ca).getItem( pos );
            if( item.dir ) {
                c.showError( c.getString( R.string.cant_open_dir, name ) );
                return;
            }
            String mime = Utils.getMimeByExt( Utils.getFileExt( name ) );
            if( mime == null ) return;                
            Intent i = new Intent( c, mime.startsWith( "image/" ) ? 
                    PictureViewer.class : TextViewer.class );
            i.setDataAndType( uri, mime );
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
    public final Uri getFolderUri( boolean active ) {
        CommanderAdapter ca = getListAdapter( active );
        return ca == null ? null : ca.getUri();
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
		locationBar.openGoPanel( current, getFolderUri( true ) );
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
                dest_uri = Favorite.borrowPassword( dest_uri, getFolderUri( false ) );
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
                    if( !dest_file.isDirectory() ) {
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
            if( cis == null || cis.size() == 0 ) return;
            FSAdapter fsa = (FSAdapter)ca;
            ZipAdapter z = new ZipAdapter( c );
            z.Init( c );
            destAdapter = z;
            File[] files = fsa.bitsToFiles( cis );
            z.createZip( files, Utils.mbAddSl( ca.toString() ) + new_zip_name );
        }
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
	    if( v instanceof ListView ) {
            if( v == list[opposite()].flv)
                togglePanels( false );
	        
	    	locationBar.closeGoPanel();
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
                    list[current].focus();
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
		            locationBar.openGoPanel( which, getFolderUri( isCurrent( which ) ) );
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
	            refreshList( current );
	        }
	        else
	            togglePanels( true );
    	}
    }
    @Override
    public boolean onLongClick( View v ) {
    	int which = v.getId() == titlesIds[LEFT] ? LEFT : RIGHT;
        locationBar.openGoPanel( which, getFolderUri( isCurrent( which ) ) );
    	return true;
    }

    /*
     * ListView.OnScrollListener implementation 
     */
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
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
        private final static String LU = "LEFT_URI", RU = "RIGHT_URI";
    	private final static String LF = "LEFT_FAV", RF = "RIGHT_FAV";
    	private final static String LI = "LEFT_ITEM", RI = "RIGHT_ITEM";
    	private final static String CP = "CURRENT_PANEL";
        private final static String FU = "FAV_URIS";
        private final static String FV = "FAVS";
        public int      current;
        public String   left, right;
        public String   leftItem, rightItem;
        public String   favs, fav_uris;
        
        public void store( Bundle b ) {
            b.putString( LF, left );
            b.putString( RF, right );
            b.putString( LI, leftItem );
            b.putString( RI, rightItem );
            b.putInt( CP, current );
            b.putString( FV, favs );
            b.putString( LU, "" );
            b.putString( RU, "" );
        }
        public void restore( Bundle b ) {
            left      = b.getString( LF );
            right     = b.getString( RF );
            leftItem  = b.getString( LI );
            rightItem = b.getString( RI );
            current   = b.getInt( CP );
            favs      = b.getString( FV );
            if( favs == null || favs.length() == 0 )
                fav_uris = b.getString( FU );
        }
        public void store( SharedPreferences.Editor e ) {
            e.putString( LF, left );
            e.putString( RF, right );
            e.putString( LI,  leftItem );
            e.putString( RI, rightItem );
            e.putInt( CP, current );
            e.putString( FV, favs );
            e.putString( LU, "" );
            e.putString( RU, "" );
        }
        public void restore( SharedPreferences p ) {
            left      = p.getString( LF, null );
            right     = p.getString( RF, null );
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
            Uri lu = left_adapter.getUri();
            if( lu == null )
                Log.e( TAG, "Left URI is null!" );
            else {
                Favorite lcf = new Favorite( lu );
                s.left = lcf.toString();
                int pos = list[LEFT].getCurPos();
                s.leftItem = pos >= 0 ? left_adapter.getItemName( pos, false ) : "";
            }
            CommanderAdapter right_adapter = (CommanderAdapter)list[RIGHT].getListAdapter();
            Uri ru = right_adapter.getUri();
            if( ru == null )
                Log.e( TAG, "Right URI is null!" );
            else {
                Favorite rcf = new Favorite( ru );
                s.right = rcf.toString();
                int pos = list[RIGHT].getCurPos();
                s.rightItem = pos >= 0 ? right_adapter.getItemName( pos, false ) : "";
            }
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
        	Uri lu = s.left == null ? Uri.parse( "home:" ) : (new Favorite( s.left )).getUriWithAuth(); 
        	NavigateInternal( LEFT, lu, s.leftItem );
    	}
    	if( dont_restore != RIGHT ) {
        	Uri ru = s.right == null ? Uri.parse( DEFAULT_LOC ) : (new Favorite( s.right )).getUriWithAuth();
            NavigateInternal( RIGHT, ru, s.rightItem );
    	}
        applyColors();
        setPanelCurrent( s.current );
    }
}
