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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
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
    protected static final String DEFAULT_LOC = "/sdcard";
    public  final static int LEFT = 0, RIGHT = 1;
    private int     current = LEFT, navigated = -1;
    private final int titlesIds[] = { R.id.left_dir,  R.id.right_dir };
    private ListHelper list[] = { null, null };
    public  FileCommander c;
    public  View mainView, toolbar = null;
    public  ViewFlipper mFlipper_   = null;
    public  PanelsView  panelsView = null;
    private int titleColor = Prefs.getDefaultColor( Prefs.TTL_COLORS ), 
                  fgrColor = Prefs.getDefaultColor( Prefs.FGR_COLORS ),
                  selColor = Prefs.getDefaultColor( Prefs.SEL_COLORS );
    private boolean warnOnRoot = true, rootOnRoot = false, arrowsLegacy = false, toolbarShown = false;
    private boolean disableOpenSelectOnly = false, disableAllActions = false;
    private float downX = 0, downY = 0;
    private StringBuffer     quickSearchBuf = null;
    private Toast            quickSearchTip = null;
    private Shortcuts        shorcutsFoldersList;
    private CommanderAdapter destAdapter = null;
    public  boolean sxs, fingerFriendly = false;
    
    public Panels( FileCommander c_, boolean sxs_ ) {
        c = c_;
        current = LEFT;
        c.setContentView( R.layout.alt );
        mainView = c.findViewById( R.id.main );
        
        //mFlipper = ((ViewFlipper)c.findViewById( R.id.flipper ));
        panelsView = ((PanelsView)c.findViewById( R.id.panels ));
        
        setMode( sxs_ );
        
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
        fingerFriendly =  sharedPref.getBoolean( "finger_friendly", true );
        warnOnRoot = sharedPref.getBoolean( "prevent_root", true );
        rootOnRoot = sharedPref.getBoolean( "root_root", false );
        arrowsLegacy = sharedPref.getBoolean( "arrow_mode", false );
        toolbarShown = sharedPref.getBoolean( "show_toolbar", true );        
        
        initList( LEFT );
        initList( RIGHT );
        
        setFingerFriendly( fingerFriendly );
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

                        case R.id.remount: pref_id = ""; def = true; break;
                        
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
    	list[current].focus();
    }
    // View.OnFocusChangeListener implementation
    @Override
    public void onFocusChange( View v, boolean f ) {
        if( sxs && f && list[current].flv != v ) {
            togglePanels( false );
        }
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
        if( mainView == null ) return;
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
                CommanderAdapter.Item item = (CommanderAdapter.Item)a.getItem( getSelection( true ) ); 
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
        list[LEFT].applyColors( bg_color, fgrColor, selColor );
        list[RIGHT].applyColors( bg_color, fgrColor, selColor );
        highlightCurrentTitle();
    }
    public final void applySettings( SharedPreferences sharedPref ) {
        try {
            applyColors();
        	setFingerFriendly( sharedPref.getBoolean( "finger_friendly", false ) );
        	warnOnRoot =  sharedPref.getBoolean( "prevent_root", true );
            rootOnRoot = sharedPref.getBoolean( "root_root", false );
            arrowsLegacy = sharedPref.getBoolean( "arrow_mode", false );
            list[LEFT].applySettings( sharedPref );
            list[RIGHT].applySettings( sharedPref );
            setPanelCurrent( current );
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
    public final void refreshLists() {
        refreshList( current );
        if( sxs )
            refreshList( opposite() );
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
    
    public void setFingerFriendly( boolean finger_friendly ) {
        fingerFriendly = finger_friendly;
        try {
            for( int p = LEFT; p <= RIGHT; p++ ) {
                TextView title = (TextView)c.findViewById( titlesIds[p] );
                if( title != null ) {
                    if( finger_friendly )
                        title.setPadding( 8, 6, 8, 6 );
                    else
                        title.setPadding( 8, 1, 8, 1 );
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
        setMode( !sxs );
    }
    public final void togglePanels( boolean refresh ) {
        Log.v( TAG, "toggle" );
        setPanelCurrent( opposite() );
        if( refresh && !sxs )
            refreshList( current );
    }
    
    public final void setPanelCurrent( int which ) {
        Log.v( TAG, "setPanelCurrent: " + which );
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
    public final void Navigate( int which, Uri uri, String posTo ) {
    	if( uri == null ) return;
    	String scheme = uri.getScheme(), path = uri.getPath();
    	
    	if( ( scheme == null || scheme.equals( "file") ) && 
    	      ( path == null || !path.startsWith( DEFAULT_LOC ) ) ) {
    	    if( warnOnRoot ) {
                CommanderAdapter ca = list[which].getListAdapter();
                if( ca != null && "file".equals( ca.getType() ) ) {
                    String cur_path = ca.toString();
                    if( cur_path != null && cur_path.startsWith( DEFAULT_LOC ) ) {
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
        list[which].Navigate( uri, posTo );
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
        getListAdapter( false ).prepareToDestroy();
        getListAdapter( true  ).prepareToDestroy();
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
        ClipboardManager clipboard = (ClipboardManager)c.getContext().getSystemService( Context.CLIPBOARD_SERVICE ); 
        String in = ca.getItemName( getSelection( true ), true );
        clipboard.setText( in );
    }    
    public final void favFolder() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        Uri u = ca.getUri();
        if( u != null ) {
            String add = u.buildUpon().appendEncodedPath( ca.getItemName( getSelection( true ), false ) ).build().toString();
            if( add != null && add.length() > 0 ) {
                shorcutsFoldersList.addToFavorites( add );
                c.showMessage( c.getString( R.string.fav_added, add ) );
            }
        }
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
    public final String getFolderUri( boolean active ) {
        CommanderAdapter ca = getListAdapter( active );
        if( ca == null ) return "";
        return ca.toString();
    }
    public final String getSelectedItemName() {
        return getListAdapter( true ).getItemName( getSelection( true ), false );
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
            //c.showDialog( Dialogs.PROGRESS_DIALOG );
            destAdapter = dest_adapter;
            getListAdapter( true ).copyItems( getSelectedOrChecked(), destAdapter, move );
            // TODO: getCheckedItemPositions() returns an empty array after a failed operation. why? 
            list[current].flv.clearChoices();
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
        list[current].setSelection( new_name );
    }

    public final void createZip( String new_zip_name ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca instanceof FSAdapter ) {
            SparseBooleanArray cis = getSelectedOrChecked();
            if( cis == null || cis.size() == 0 ) return;
            //c.showDialog( Dialogs.PROGRESS_DIALOG );
            FSAdapter fsa = (FSAdapter)ca;
            ZipAdapter z = new ZipAdapter( c );
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

    public final void renameFile( String new_name ) {
        CommanderAdapter adapter = getListAdapter( true );
        int pos = getSelection( true );
        if( pos >= 0 ) {
            adapter.renameItem( pos, new_name );
            list[current].setSelection( new_name );
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////

    /**
     * An AdapterView.OnItemSelectedListener implementation
     */
    @Override
    public void onItemSelected( AdapterView<?> listView, View itemView, int pos, long id ) {
        //Log.v( TAG, "Selected item " + pos );
    	shorcutsFoldersList.closeGoPanel();
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
        
    	shorcutsFoldersList.closeGoPanel();
    	resetQuickSearch();
    	ListView flv = list[current].flv;
        if( flv != parent ) {
            togglePanels( false );
          	Log.e( TAG, "onItemClick. current=" + current + ", parent=" + parent.getId() );
        }
        	
        
        if( position == 0 ) {
            flv.setItemChecked( 0, false ); // parent item never selected
            list[current].setCurPos( 0 );
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
        ((CommanderAdapter)list[current].flv.getAdapter()).openItem( position );
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
	    	shorcutsFoldersList.closeGoPanel();
	    	char ch = (char)event.getUnicodeChar();
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
                if( !arrowsLegacy ) return false;
            case KeyEvent.KEYCODE_VOLUME_UP:
	            list[current].checkItem( true );
	            return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
	            if( arrowsLegacy ) { 
	                togglePanels( true );
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
        list[current].storeChoosedItems();
    }
    
    public void reStoreChoosedItems() {
        list[current].reStoreChoosedItems();
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
            left      = p.getString( LP, "home:" );
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
            CommanderAdapter  left_adapter = (CommanderAdapter)list[LEFT].getListAdapter();
            s.left  =  left_adapter.toString();
            s.leftItem  =  left_adapter.getItemName( list[LEFT].getCurPos(), false );
            //Log.v( TAG, "Saving left current item: " + s.leftItem );
            CommanderAdapter right_adapter = (CommanderAdapter)list[RIGHT].getListAdapter();
            s.right = right_adapter.toString();
            s.rightItem = right_adapter.getItemName( list[RIGHT].getCurPos(), false );
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
    	//Log.v( TAG, "Restoring left current item: " + s.leftItem );
        NavigateInternal( LEFT,  Uri.parse( s.left  ), s.leftItem );
        NavigateInternal( RIGHT, Uri.parse( s.right ), s.rightItem );
        applyColors();
        setPanelCurrent( s.current );
        shorcutsFoldersList.setFromString( s.favUris );
    }
}
