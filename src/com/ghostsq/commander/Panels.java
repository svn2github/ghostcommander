package com.ghostsq.commander;

import java.io.File;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
//import android.content.res.ColorStateList;	// TODO

/**
 * @author zc2
 *
 */
public class Panels implements AdapterView.OnItemSelectedListener, 
                                              AdapterView.OnItemClickListener, 
                                                     View.OnClickListener, 
                                                     View.OnLongClickListener, 
                                                     View.OnTouchListener,
                                                     View.OnKeyListener {
    public  final static int LEFT = 0, RIGHT = 1;
    private final int titlesIds[] = {R.id.left_dir, R.id.right_dir};
    private final int  listsIds[] = {R.id.left_list, R.id.right_list};
    private int currentPositions[] = {-1, -1};
    private ListView   listViews[] = {null, null};
    private int current = LEFT;
    private FileCommander c;
    public  View mainView, toolbar = null;
    private int id;
    private boolean touchedThere = false, fingerFriendly = false, warnOnRoot = true;
    private float downY = 0;
    private StringBuffer quickSearchBuf = null;
    private Toast        quickSearchTip = null;
    private Shortcuts shorcutsFoldersList;

    public Panels( FileCommander c_, int id_ ) {
        current = LEFT;
        c = c_;
        id = id_;
        c.setContentView( id );
        mainView = c.findViewById( R.id.main );
        
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
        fingerFriendly =  sharedPref.getBoolean( "finger_friendly", true );
        warnOnRoot =  sharedPref.getBoolean( "prevent_root", true );
        setFingerFriendly( fingerFriendly );
        if( sharedPref.getBoolean( "show_toolbar", true ) )
            showOrHideToolbar( true );
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
    public final void showOrHideToolbar( boolean show ) {
        if( show ) { 
            if( toolbar == null ) {
                LayoutInflater inflater = (LayoutInflater)c.getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                toolbar = inflater.inflate( R.layout.toolbar, (ViewGroup)mainView, true ).findViewById( R.id.toolbar );
            }
            else
                toolbar.setVisibility( View.VISIBLE );
            if( toolbar != null ) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                Button b = null;
                final int button_ids[] = {R.id.F1, R.id.F2, R.id.F4, R.id.SF4, R.id.F5, R.id.F6, R.id.F7, R.id.F8, R.id.F9, R.id.F10, R.id.eq, R.id.tgl, R.id.sz};
                for( int i = 0; i < button_ids.length; i++ ) {
                    b = (Button)toolbar.findViewById( button_ids[i] );
                    if( b != null ) {
                        b.setOnClickListener( c );
                        final String pref_id;
                        boolean def = false;
                        switch( button_ids[i] ) {
                        case R.id.F1: pref_id = "show_F1";  def = true;  break;
                        case R.id.F2: pref_id = "show_F2";  def = true;  break;
                      //case R.id.F3: pref_id = "show_F3";	def = true;  break;
                        case R.id.F4: pref_id = "show_F4";  def = true;	 break;
                        case R.id.SF4:pref_id = "show_SF4";	def = false; break;
                        case R.id.F5: pref_id = "show_F5";	def = true;  break;
                        case R.id.F6: pref_id = "show_F6";	def = true;  break;
                        case R.id.F7: pref_id = "show_F7";	def = true;  break;
                        case R.id.F8: pref_id = "show_F8";	def = true;  break;
                        case R.id.F9: pref_id = "show_F9";	def = true;  break;
                        case R.id.F10:pref_id = "show_F10";	def = true;  break;
                        case R.id.eq: pref_id = "show_eq";	def = false; break;
                        case R.id.tgl:pref_id = "show_tgl";	def = false; break;
                        case R.id.sz: pref_id = "show_sz";	def = false; break;
                        default: pref_id = "";
                        }
                        b.setVisibility( sharedPref.getBoolean( pref_id, def ) ? View.VISIBLE : View.GONE );
                    }
                }
            }
        }
        else {
            if( toolbar != null )
                toolbar.setVisibility( View.GONE );
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
    public final int getCurrent() {
        return current;
    }
    public final boolean isCurrent( int q ) {
        return ( current == LEFT  && q == LEFT ) ||
               ( current == RIGHT && q == RIGHT );
    }
    
    public final int getId() {
        return id;
    }
    private final void initList( int which ) {
        ListView flv = (ListView)c.findViewById( listsIds[which] );
        listViews[which] = flv;
        if( flv != null ) {
            flv.setItemsCanFocus( false );
            flv.setChoiceMode( ListView.CHOICE_MODE_MULTIPLE );
            flv.setOnItemSelectedListener( this );
            flv.setOnItemClickListener( this );
            flv.setOnTouchListener( this );
            flv.setOnKeyListener( this );
            c.registerForContextMenu( flv );
            FSAdapter la;
            la = new FSAdapter( c, null, id == R.layout.main ? CommanderAdapter.WIDE_MODE : CommanderAdapter.NARROW_MODE );
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
            applySettings( sharedPref, la, which );
            flv.setAdapter( la );
            setPanelTitle( "", which );
        }
    }

    private final void setPanelTitle( String s, int which ) {
        TextView title = (TextView)c.findViewById( titlesIds[which] );
        if( title != null ) {
        	int p_width = mainView.getWidth();
        	if( p_width > 0 )
        		title.setMaxWidth( p_width / 2 );
            title.setText( s );
        }
    }
    private final void highlightCurrentTitle() {
    	highlightTitle( opposite(), false );
    	highlightTitle( current, true );
    }
    private final void highlightTitle( int which, boolean on ) {
        TextView title = (TextView)c.findViewById( titlesIds[which] );
        if( title != null )
            title.setBackgroundColor( on ? 0xFF4169E1 : 0xFF555555 ); // TODO: use ColorStateList
        else
        	c.showMessage("title not found");
    }

    public final int getSelection() {
        int pos = listViews[current].getSelectedItemPosition();
        return pos < 0 ? currentPositions[current] : pos;
    }
    public final void setSelection( int i ) {
        listViews[current].setSelection( i ); // loses it anyway
        currentPositions[current] = i;
    }
    private final void setSelection( int which, String name ) {
    	ListView flv = listViews[which];
    	CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        int i, num = ((ListAdapter)ca).getCount();
        for( i = 0; i < num; i++ ) {
        	String item_name = ca.getItemName( i, false );
            if( item_name != null && item_name.compareTo( name ) == 0 ) {
                flv.setSelection( i );
                currentPositions[which] = i;
                break;
            }
        }
    }
    public final File getItemURI() {
        try {
            return (File)((ListAdapter)getListAdapter( true )).getItem( getSelection() );
        }
        catch( ClassCastException e ) {
            c.showMessage( "Can't cast to File " + e );
            return null;
        }
    }
    private final int opposite() {
        return current == LEFT ? RIGHT : LEFT;
    }
    public final CommanderAdapter getListAdapter( boolean forCurrent ) {
        ListView flv = (ListView)listViews[forCurrent ? current : opposite()];
        return flv != null ? (CommanderAdapter)flv.getAdapter() : null;
    }
    public final ListView getListView() {
        return listViews[current];
    }
    public final int getWidth() {
        return mainView.getWidth();
    }
    public final void applySettings( SharedPreferences sharedPref ) {
        try {
        	setFingerFriendly( sharedPref.getBoolean( "finger_friendly", false ) );
        	warnOnRoot =  sharedPref.getBoolean( "prevent_root", true );
            for( int i = LEFT; i <= RIGHT; i++ ) {
                ListView flv = listViews[i];
                applySettings( sharedPref, (CommanderAdapter)flv.getAdapter(), i );
            }
            setPanelCurrent( current );
        }
        catch( Exception e ) {
            c.showMessage( "Error: " + e );
        }
    }
    private final void applySettings( SharedPreferences sharedPref, CommanderAdapter ca, int which ) {
    	
    	if( id == R.layout.main )
	        ca.setMode( CommanderAdapter.MODE_WIDTH, sharedPref.getBoolean( "two_lines", false ) ? 
	        		    CommanderAdapter.NARROW_MODE : CommanderAdapter.WIDE_MODE );
    	
        String sfx = id == R.layout.main ? "_Ovr" : "_SbS";
        boolean detail_mode = sharedPref.getBoolean( which == LEFT ? "left_detailed" + sfx : "right_detailed" + sfx, true );        
        ca.setMode( CommanderAdapter.MODE_DETAILS, detail_mode ? 
                    CommanderAdapter.DETAILED_MODE : CommanderAdapter.SIMPLE_MODE );
        String sort = sharedPref.getString( which == LEFT ? "left_sorting" : "right_sorting", "n" );
        ca.setMode( CommanderAdapter.MODE_SORTING, sort.compareTo( "s" ) == 0 ? CommanderAdapter.SORT_SIZE : 
                                                   sort.compareTo( "d" ) == 0 ? CommanderAdapter.SORT_DATE : 
                                                                                CommanderAdapter.SORT_NAME );
        ca.setMode( CommanderAdapter.MODE_FINGERF, fingerFriendly ? CommanderAdapter.FAT_MODE : CommanderAdapter.SLIM_MODE );

        boolean hidden_mode = sharedPref.getBoolean( ( which == LEFT ? "left" : "right" ) + "_show_hidden", true );
        ca.setMode( CommanderAdapter.MODE_HIDDEN, hidden_mode ? CommanderAdapter.SHOW_MODE : CommanderAdapter.HIDE_MODE );
        refreshList( false );
    }
    public void changeSorting( int sort_mode ) {
        CommanderAdapter ca = getListAdapter( true );
        ca.setMode( CommanderAdapter.MODE_SORTING, sort_mode );
        listViews[current].invalidateViews();
    }
    public final void refreshList( boolean reread ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        setPanelTitle( ca.toString(), current );
        if( reread )
            ca.readSource( null );
        listViews[current].invalidateViews();
        if( id == R.layout.alt ) {
            ca = getListAdapter( false );
            if( ca == null ) return;
            setPanelTitle( ca.toString(), opposite() );
            if( reread )
                ca.readSource( null );
            listViews[opposite()].invalidateViews();
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
                        title.setPadding( 8, 8, 8, 8 );
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
    public final void togglePanels( boolean refresh ) {
    	setPanelCurrent( opposite() );
        if( refresh )
            refreshList( true );
    }
    public final void setPanelCurrent( int which ) {
        ViewFlipper mFlipper = ((ViewFlipper)c.findViewById( R.id.flipper ));
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
        current = which;
        focus();
        highlightCurrentTitle();
    }
    public final void showSizes() {
    	c.setProgressBarIndeterminateVisibility( true );
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
    public final void checkAllItems( boolean set ) {
        ListView flv = listViews[current];
        for( int i = 1; i < flv.getCount(); i++ ) 
            flv.setItemChecked( i, set );
    }
    class NavDialog implements OnClickListener {
        private   final Uri sdcard = Uri.parse("/sdcard");
    	protected int    which;
    	protected String posTo;
    	protected Uri    uri;
    	
    	NavDialog( Context c, int which_, Uri uri_, String posTo_ ) { 
    		which = which_;
    		uri   = uri_;
    		posTo = posTo_;
            new AlertDialog.Builder( c )
	            .setIcon( android.R.drawable.ic_dialog_alert )
	            .setTitle( R.string.confirm )
	            .setMessage( c.getString( R.string.nav_warn, uri ) )
	            .setPositiveButton( R.string.dialog_ok, this )
	            .setNegativeButton( R.string.dialog_cancel, this )
	            .show();
    	}
        @Override
    	public void onClick( DialogInterface idialog, int whichButton ) {
        	if( whichButton == DialogInterface.BUTTON_POSITIVE )
        		NavigateInternal(  which, uri, posTo );
        	else
        		NavigateInternal(  which, sdcard, null );
        	idialog.dismiss();
        }
    }
    public final void Navigate( int which, Uri uri, String posTo ) {
    	if( uri == null ) return;
    	String scheme = uri.getScheme(), path = uri.getPath();
    	
    	if( warnOnRoot && ( scheme == null || scheme.compareTo("file") == 0 ) && 
    	                    ( path == null || !path.startsWith( "/sdcard" ) ) ) {
    		try {
        		new NavDialog( c, which, uri, posTo );
			} catch( Exception e ) {
				e.printStackTrace();
			}
            return;
    	}
    	NavigateInternal( which, uri, posTo );
    }
    private final void NavigateInternal( int which, Uri uri, String posTo ) {
    	c.setProgressBarIndeterminateVisibility( true );
        setPanelTitle( uri.toString(), which );
        ListView flv = listViews[which];
        flv.clearChoices();
        CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        String scheme = uri.getScheme();
        if( scheme != null && scheme.compareTo( "ftp" ) == 0 ) {
        	try {
                if( !( ca instanceof FTPAdapter ) ) {
                	ca.prepareToDestroy();
                    ca = new FTPAdapter();
                    ca.Init( c );
                    ca.setMode( CommanderAdapter.WIDE_MODE, 
                      id == R.layout.main ? CommanderAdapter.WIDE_MODE : CommanderAdapter.NARROW_MODE );
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                    applySettings( sharedPref, ca, which );
                    flv.setAdapter( (ListAdapter)ca );
                    flv.setOnKeyListener( this );
                }
			} catch( Exception e ) {
				c.showError( "Unable to create com.ghostsq.ftp.FTPAdapter class" );
			}
        }
        else 
        if( scheme != null && scheme.compareTo( "zip" ) == 0 ) {
            try {
                if( !( ca instanceof ZipAdapter ) ) {
                    ca.prepareToDestroy();
                    ca = new ZipAdapter();
                    ca.Init( c );
                    ca.setMode( CommanderAdapter.WIDE_MODE, 
                      id == R.layout.main ? CommanderAdapter.WIDE_MODE : CommanderAdapter.NARROW_MODE );
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                    applySettings( sharedPref, ca, which );
                    flv.setAdapter( (ListAdapter)ca );
                    flv.setOnKeyListener( this );
                }
            } catch( Exception e ) {
                c.showError( "Unable to create com.ghostsq.ftp.FTPAdapter class" );
            }
        }
        else {
            if( !( ca instanceof FSAdapter ) ) {
            	ca.prepareToDestroy();
                ca = new FSAdapter( c, uri, id == R.layout.main ? CommanderAdapter.WIDE_MODE : CommanderAdapter.NARROW_MODE );
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                applySettings( sharedPref, ca, which );
                flv.setAdapter( (ListAdapter)ca );
            }
        }
        ca.readSource( uri );
        if( posTo != null ) {
        	setSelection( which, posTo );
        }
        else
            flv.setSelection( 0 );
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
    
    public final void openForEdit( String file_name ) {
        File f = file_name == null ? getItemURI() : new File( file_name );
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
            return "" + counter + " items";
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
    private final SparseBooleanArray getSelectedOrChecked() {
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
        return getListAdapter( active ).toString();
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
				if( name.startsWith( s ) ) {
					setSelection( i );
					return;
				}
			}
		}
	}
    private final void showTip( String s ) {
    	try {
	        if( R.layout.main == id || current == LEFT )
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
        getListAdapter( true ).terminateOperation();
        getListAdapter( false ).terminateOperation();
    }
    public final void copyFiles( String dest, boolean move ) {
    	c.setProgressBarIndeterminateVisibility( true );
        CommanderAdapter dest_adapter = getListAdapter( false );
        Uri dest_uri = Uri.parse( dest );
        if( dest_uri != null && dest_uri.compareTo( dest_adapter.getUri() ) != 0 )
            dest_adapter = new FSAdapter( c, dest_uri, 0 ); // TODO: user might enter a ftp or some other url to copy to
        if( !move ) {
            try {
                c.showDialog( Dialogs.PROGRESS_DIALOG );
            }
            catch( IllegalArgumentException e ) {
                c.showMessage( "showDialog() failed, " + e );
            }
        }
        if( getListAdapter( true ).copyItems( getSelectedOrChecked(), dest_adapter, move ) )
            listViews[current].clearChoices();
        if( move )
            refreshList( true );
    }
	public void createNewFile( String fileName ) {
		String local_name = fileName;
		CommanderAdapter ca = getListAdapter( true );
		if( fileName.charAt( 0 ) != '/' ) {
			String dirName = ca.toString();
			fileName = dirName + ( dirName.charAt( dirName.length()-1 ) == '/' ? "" : "/" ) + fileName;
		}
		if( ca.createFile( fileName ) ) {
			refreshList( true );
			setSelection( current, local_name );
			openForEdit( fileName );
		}
	}
    public final void createFolder( String new_name ) {
        getListAdapter( true ).createFolder( new_name );
        refreshList( true );
    }
    public final void deleteItems() {
    	c.showDialog( Dialogs.PROGRESS_DIALOG );
        if( getListAdapter( true ).deleteItems( getSelectedOrChecked() ) )
            listViews[current].clearChoices();
    }
    public final void renameFile( String new_name ) {
        CommanderAdapter adapter = getListAdapter( true );
        int pos = getSelection();
        if( pos >= 0 && adapter.renameItem( pos, new_name ) )
            refreshList( true );
        else
            c.showMessage( "Can't rename file" );
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
        // showMessage( "Nothing selected state" );
    }
    /**
     * An AdapterView.OnItemClickListener implementation 
     */
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
    	shorcutsFoldersList.closeGoPanel();
    	resetQuickSearch();
        if( listViews[current] != parent ) {
            togglePanels( false );
            if( listViews[current] != parent )
            	c.showError( "panels problem. current=" + current + ", parent=" + parent.getId() );
        }
        	
        ListView flv = listViews[current];
        if( position == 0 )
            flv.setItemChecked( 0, false );
        
        if( touchedThere ) {
            touchedThere = false;
        }
        else {
            ((CommanderAdapter)listViews[current].getAdapter()).openItem( position );
            flv.setItemChecked( position, false );
        }
    }

    /**
     * View.OnTouchListener implementation 
     */
    @Override
    public boolean onTouch( View v, MotionEvent event ) {
    	resetQuickSearch();
	    if( v instanceof ListView ) {
	    	shorcutsFoldersList.closeGoPanel();
	        final int act = event.getAction();
	        if( act == MotionEvent.ACTION_DOWN ) {
	            downY = event.getY();
	            touchedThere = event.getX() > v.getWidth() / 2;
	        }
	        else if( act == MotionEvent.ACTION_UP ) {
	            if( Math.abs( downY - event.getY() ) > 10. )
	                touchedThere = false;
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
	        case '{':
	        case '}':
	        	setPanelCurrent( ch == '{' ? Panels.LEFT : Panels.RIGHT );
	        	return true;
	        case '+':
	        case '-':
	        	checkAllItems( ch == '+' );
	            return true;
	        case '"':
	        	showSizes();
	            return true;
	        case '*':
	        	shorcutsFoldersList.addToFavorites( getFolderUri( true ) );
	            return true;
	        case '2':
	            c.showDialog( FileCommander.RENAME_ACT );
	            return true;
	        case '3':
	        	return true;
	        case '4':
	            openForEdit( null );
	            return true;
	        case '5':
	        	c.showDialog( FileCommander.COPY_ACT );
	        	return true;
	        case '6':
	        	c.showDialog( FileCommander.MOVE_ACT );
	        	return true;
	        case '7':
	        	c.showDialog( FileCommander.MKDIR_ACT );
	        	return true;
	        case '8':
	        	c.showDialog( FileCommander.DEL_ACT );
	        	return true;
	        }
	    	switch( keyCode ) {
	        case KeyEvent.KEYCODE_DPAD_UP:
	        case KeyEvent.KEYCODE_DPAD_DOWN:	
		    	resetQuickSearch();
		    	return false;
	        case KeyEvent.KEYCODE_VOLUME_UP:
	        case KeyEvent.KEYCODE_DPAD_RIGHT:
	            checkItem( true );
	            return true;
	        case KeyEvent.KEYCODE_VOLUME_DOWN:
	        case KeyEvent.KEYCODE_DPAD_LEFT:
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
	        if( which == current )
	            refreshList( true );
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
     * Persistent state
     */

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
            left      = p.getString( LP, "/sdcard" );
            right     = p.getString( RP, "/sdcard" );
            leftItem  = p.getString( LI, null );
            rightItem = p.getString( RI, null );
            current   = p.getInt( CP, LEFT );
            favUris   = p.getString( FU, "" );
        }
    }
    public State getState() {
        State s = new State();
        s.current = current;
        CommanderAdapter  left_adapter = (CommanderAdapter)listViews[LEFT].getAdapter();
        CommanderAdapter right_adapter = (CommanderAdapter)listViews[RIGHT].getAdapter();
        s.left  =  left_adapter.toString();
        s.right = right_adapter.toString();
        s.leftItem  =  left_adapter.getItemName( currentPositions[LEFT],  false );
        s.rightItem = right_adapter.getItemName( currentPositions[RIGHT], false );
        s.favUris = "";
        s.favUris = shorcutsFoldersList.getAsString();
        return s;
    }
	public void setState( State s ) {
    	resetQuickSearch();
        NavigateInternal( LEFT,  Uri.parse(s.left),  s.leftItem );
        NavigateInternal( RIGHT, Uri.parse(s.right), s.rightItem );
        setPanelCurrent( s.current );
        shorcutsFoldersList.setFromString( s.favUris );
    }
}
