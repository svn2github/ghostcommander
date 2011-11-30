package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;

import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.FSAdapter;
import com.ghostsq.commander.adapters.FindAdapter;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.Utils;

import dalvik.system.DexClassLoader;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.view.ContextMenu;
import android.view.Display;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.KeyEvent;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.Toast;

public class FileCommander extends Activity implements Commander, View.OnClickListener {
    private final static String TAG = "GhostCommanderActivity";
    public  final static int REQUEST_CODE_PREFERENCES = 1, REQUEST_CODE_SRV_FORM = 2;
    public  final static int FIND_ACT = 1017, DBOX_APP = 3592, SMB_ACT = 2751, FTP_ACT = 4501;
    
    private ArrayList<Dialogs> dialogs;
    public  Panels  panels;
    private boolean on = false, exit = false, dont_restore = false, sxs_auto = true, show_confirm = true, back_exits = false;
    private String  lang = ""; // just need to issue a warning on change
    private int     file_exist_resolution = Commander.UNKNOWN;
    private NotificationManager notMan = null;
    private long    notLastTime = 0; 

    public final void showMemory( String s ) {
        final ActivityManager sys = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        sys.getMemoryInfo(mem);
        showMessage(s + "\n Memory: " + mem.availMem + ( mem.lowMemory ? " !!!" : "" ));
    }

    public final void showMessage( String s ) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    public int getWidth() {
        return panels.getWidth();
    }

    protected final Dialogs getDialogsInstance( int id ) {
        for( int i = 0; i < dialogs.size(); i++ )
            if( dialogs.get(i).getId() == id )
                return dialogs.get(i);
        return null;
    }

    protected final Dialogs obtainDialogsInstance( int id ) {
        Dialogs dh = getDialogsInstance( id );
        if( dh == null ) {
            dh = new Dialogs( this, id );
            dialogs.add( dh );
        }
        return dh;
    }

    protected final void addDialogsInstance( Dialogs dh ) {
        dialogs.add(dh);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        requestWindowFeature( Window.FEATURE_INDETERMINATE_PROGRESS );
        dialogs = new ArrayList<Dialogs>( Dialogs.numDialogTypes );
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        back_exits = sharedPref.getBoolean( "exit_on_back", false );
        lang = sharedPref.getString( "language", "" );
        Utils.changeLanguage( this );
        String panels_mode = sharedPref.getString( "panels_sxs_mode", "a" );
        sxs_auto = panels_mode.equals( "a" );
        boolean sxs = sxs_auto ? getRotMode() : panels_mode.equals( "y" );
        panels = new Panels( this, sxs );
        setConfirmMode( sharedPref );
        
        notMan = (NotificationManager)getSystemService( Context.NOTIFICATION_SERVICE );
    }

    @Override
    protected void onStart() {
        //Log.v( TAG, "Starting\n" );
        super.onStart();
        on = true;
        if( dont_restore )
            dont_restore = false;
        else {
            Utils.changeLanguage( this );
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            Panels.State s = panels.new State();
            s.restore( prefs );
            panels.setState( s );
            Intent intent = getIntent();
            String action = intent.getAction();
            Log.i( TAG, "Action: " + action );
            if( Intent.ACTION_VIEW.equals( action ) ) {
                Uri uri = intent.getData();
                if( "application/x-zip-compressed".equals( intent.getType() ) ||
                                 "application/zip".equals( intent.getType() ) )
                    uri = uri.buildUpon().scheme( "zip" ).build();
                panels.Navigate( 0, uri, null );
                panels.setPanelCurrent( 0 );
            }
            
            final String FT = "first_time";
            if( prefs.getBoolean( FT, true ) ) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean( FT, false );
                editor.commit();
                showInfo( getString( R.string.keys_text) );
            }
        }
    }

    @Override
    protected void onPause() {
        //Log.v( TAG, "Pausing\n");
        super.onPause();
        on = false;
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        Panels.State s = panels.getState();
        s.store( editor );
        editor.commit();
    }

    @Override
    protected void onResume() {
        //Log.v( TAG, "Resuming\n");
        super.onResume();
        on = true;
    }
    
    @Override
    protected void onStop() {
        //Log.v( TAG, "Stopping\n");
        super.onStop();
        on = false;
    }

    @Override
    protected void onDestroy() {
        //Log.v( TAG, "Destroying\n");
        on = false;
        super.onDestroy();
        if( isFinishing() && exit ) {
            if( notMan != null ) notMan.cancelAll();
            panels.Destroy();
            Log.i( TAG, "Good bye cruel world...");
            System.exit( 0 );
        }
    }
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        Log.i( TAG, "Saving Instance State");
        Panels.State s = panels.getState();
        s.store( outState );
        super.onSaveInstanceState( outState );
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        Log.i( TAG, "Restoring Instance State");
        if( savedInstanceState != null ) {
            Panels.State s = panels.new State();
            s.restore( savedInstanceState );
            panels.setState( s );
        }
        super.onRestoreInstanceState( savedInstanceState );
    }

    @Override
    public void onConfigurationChanged( Configuration newConfig ) {
        Utils.changeLanguage( this );
        super.onConfigurationChanged( newConfig );
        panels.setLayoutMode( sxs_auto ? newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE : panels.getLayoutMode() );
            
        
/*// TODO: hide the numbers from the virtual buttons if there is no physical keyboard
        // Checks whether a hardware keyboard is available
        if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            Toast.makeText(this, "keyboard visible", Toast.LENGTH_SHORT).show();
        } else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            Toast.makeText(this, "keyboard hidden", Toast.LENGTH_SHORT).show();
        }
*/
    }    
    
    @Override
    public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo ) {
        try {
            Utils.changeLanguage( this );
            AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo)menuInfo;
            menu.setHeaderTitle( getString( R.string.operation ) );
            CommanderAdapter ca = panels.getListAdapter( true );
            ca.populateContextMenu( menu, acmi, panels.getNumItemsChecked() );
        }
        catch( Exception e ) {
            Log.e( TAG, "onCreateContextMenu()", e );
        }
    }

    @Override
    public boolean onContextItemSelected( MenuItem item ) {
        try {
            panels.resetQuickSearch();
            AdapterView.AdapterContextMenuInfo info;
            info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
            if( info == null ) return false;
            panels.setSelection( info.position );
            int item_id = item.getItemId();
            if( OPEN == item_id ) 
                panels.openItem( info.position );
            else
                dispatchCommand( item_id );
            return true;
        } catch( Exception e ) {
            Log.e(TAG, "onContextItemSelected()", e);
            return false;
        }
    }

    @Override
    protected Dialog onCreateDialog( int id ) {
        if( !on ) {
            Log.e( TAG, "onCreateDialog() is called when the activity is down" );
        }
        Dialogs dh = obtainDialogsInstance( id );
        Dialog d = dh.createDialog( id );
        return d != null ? d : super.onCreateDialog( id );
    }

    @Override
    protected void onPrepareDialog( int id, Dialog dialog ) {
        Dialogs dh = getDialogsInstance( id );
        if( dh != null )
            dh.prepareDialog( id, dialog );
        super.onPrepareDialog( id, dialog );
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        try {
            Utils.changeLanguage( this );
            // Inflate the currently selected menu XML resource.
            MenuInflater inflater = getMenuInflater();
            inflater.inflate( R.menu.menu, menu );
            return true;
        } catch( Error e ) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        panels.resetQuickSearch();
        boolean processed = super.onMenuItemSelected( featureId, item );
        if( !processed ) 
            dispatchCommand( item.getItemId() );
        return true; 
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
        super.onActivityResult(requestCode, resultCode, data);
        if( requestCode == REQUEST_CODE_PREFERENCES ) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            back_exits = sharedPref.getBoolean( "exit_on_back", false );
            String lang_ = sharedPref.getString( "language", "" );
            if( !lang.equalsIgnoreCase( lang_ ) ) {
                lang = lang_;
                Utils.changeLanguage( this );
                showMessage( getString( R.string.restart_to_apply_lang ) );
                exit = true;
            }
            panels.applySettings( sharedPref, false );
            String panels_mode = sharedPref.getString( "panels_sxs_mode", "a" );
            sxs_auto = panels_mode.equals( "a" );
            boolean sxs = sxs_auto ? getRotMode() : panels_mode.equals( "y" );
            panels.setLayoutMode( sxs );
            panels.showToolbar( sharedPref.getBoolean("show_toolbar", true ) );
            setConfirmMode( sharedPref );
        }
        else
        if( requestCode == REQUEST_CODE_SRV_FORM ) {
            if( resultCode == RESULT_OK ) {
                dont_restore = true;
                Navigate( Uri.parse( data.getAction() ), null );
            }
        }
    }

    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        //Log.v( TAG, "global key:" + keyCode + ", number:" + event.getNumber() + ", uchar:" + event.getUnicodeChar() );
        char c = (char)event.getUnicodeChar();
        panels.resetQuickSearch();
        switch( c ) {
        case '=':
            panels.makeOtherAsCurrent();
            return true;
        case '&':
            openPrefs();
            return true;
        case '/':
            showSearchDialog();
            return true;
        case '1':
            showInfo(getString(R.string.keys_text));
            return true;
        case '9':
            openPrefs();
            return true;
        case '0':
            exit = true;
            finish();
            return true;
        }
        switch( keyCode ) {
        case KeyEvent.KEYCODE_TAB:
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            panels.togglePanels( false );
            return true;
        case KeyEvent.KEYCODE_BACK:
        case KeyEvent.KEYCODE_DEL:
            if( back_exits ){
                finish();
                return true;
            }
            panels.goUp();
            return false;
        case KeyEvent.KEYCODE_SEARCH:
            showSearchDialog();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp( int keyCode, KeyEvent event ) {
        if( keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN )
            return true;

        return super.onKeyUp(keyCode, event);
    }

    /*
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    @Override
    public void onClick( View button ) {
        panels.resetQuickSearch();
        if( button == null )
            return;
        dispatchCommand( button.getId() );
    }

    public void dispatchCommand( int id ) {
        Utils.changeLanguage( this );
        switch( id ) {
        case R.id.keys:
        case R.id.F1:
            showInfo( getString( R.string.keys_text ) );
            break;
        case R.id.F3:
            panels.openForView();
            break;
        case R.id.F4:
            panels.openForEdit(null);
            break;
        case R.id.F2:
        case R.id.new_zip:
        case R.id.F5:
        case R.id.F6:
        case R.id.F8:
            if( panels.getNumItemsSelectedOrChecked() > 0 )
                showDialog( id );
            else
                showMessage( getString( R.string.no_items ) );
            break;
        case R.id.new_file:
        case R.id.SF4:
        case R.id.F7:
        case R.id.about:
        case R.id.donate:
            showDialog( id );
            break;
        case R.id.prefs:
        case R.id.F9:
            openPrefs();
            break;
        case R.id.exit:
        case R.id.F10:
            exit = true;
            finish();
            break;
        case R.id.oth_sh_this:
        case R.id.eq:
            panels.makeOtherAsCurrent();
            break;
        case R.id.toggle_panels_mode:
            panels.togglePanelsMode();
            break;
        case R.id.tgl:
            panels.togglePanels(true);
            break;
        case R.id.sz:
            panels.showSizes();
            break;
        case R.id.home:
            Navigate( Uri.parse( "home:" ), null );
            break;
        case R.id.favs:
            Navigate( Uri.parse( "favs:" ), null );
            break;
        case  R.id.sdcard:
            Navigate( Uri.parse( Panels.DEFAULT_LOC ), null );
            break;
        case  R.id.root: {
                Uri cu = panels.getFolderUri( true );
                Navigate( Uri.parse( RootAdapter.DEFAULT_LOC + 
                  ( cu == null || cu.getScheme() != null && cu.getScheme().length() > 0 ? "" : cu.getPath() ) ), null );
            }
            break;
        case  R.id.mount:
            Navigate( Uri.parse( MountAdapter.DEFAULT_LOC ), null );
            break;
            
        case FTP_ACT: {
                Intent i = new Intent( this, ServerForm.class );
                i.putExtra( "schema", "ftp" );
                startActivityForResult( i, REQUEST_CODE_SRV_FORM );
            }
            break;
        case SMB_ACT: {
                Intent i = new Intent( this, ServerForm.class );
                i.putExtra( "schema", "smb" );
                startActivityForResult( i, REQUEST_CODE_SRV_FORM );
            }
            break;
        case R.id.search: 
            showSearchDialog();
            break;
        case R.id.enter:
            panels.openGoPanel();
            break;
        case R.id.add_fav:
            panels.addCurrentToFavorites();
            break;
        case R.id.by_name:
            panels.changeSorting( CommanderAdapter.SORT_NAME );
            break;
        case R.id.by_ext:
            panels.changeSorting( CommanderAdapter.SORT_EXT );
            break;
        case R.id.by_size:
            panels.changeSorting( CommanderAdapter.SORT_SIZE );
            break;
        case R.id.by_date:
            panels.changeSorting( CommanderAdapter.SORT_DATE );
            break;
        case R.id.refresh:
            panels.refreshLists();
            break;
        case R.id.sel_all:
            showDialog( Dialogs.SELECT_DIALOG );
            break;
        case R.id.uns_all:
            showDialog( Dialogs.UNSELECT_DIALOG );
            break;
        case R.id.online: {
                Intent intent = new Intent( Intent.ACTION_VIEW );
                intent.setData( Uri.parse( getString( R.string.help_uri ) ) );
                startActivity( intent );
            }
            break;
        case SEND_TO:
            panels.tryToSend();
            break;
        case OPEN_WITH:
            panels.tryToOpen();
            break;
        case COPY_NAME:
            panels.copyName();
            break;
        case FAV_FLD:
            panels.favFolder();
            break;
        case R.id.softkbd:
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(0, 0);
            break;
        case  R.id.hidden:
            panels.toggleHidden();
            break;
        case  R.id.rescan:
            sendBroadcast( new Intent( Intent.ACTION_MEDIA_MOUNTED, Uri.parse( "file://" + Panels.DEFAULT_LOC ) ) );
            break;
        default:
            CommanderAdapter ca = panels.getListAdapter( true );
            if( ca != null )
                ca.doIt( id, panels.getSelectedOrChecked() );
        }
    }
    private final void openPrefs() {
        try {
            Intent launchPreferencesIntent = new Intent().setClass( this, Prefs.class );
            startActivityForResult( launchPreferencesIntent, REQUEST_CODE_PREFERENCES );
        } catch( Error e ) {
            Log.e( TAG, "Preferences can't open", e );
        }
    }

    private final void showSearchDialog() {
        CommanderAdapter ca = panels.getListAdapter( true );
        if( ca instanceof FSAdapter || ca instanceof FindAdapter ) {
            String cur_s = ca.toString();
            if( cur_s != null ) {
                Uri cur_uri = Uri.parse( cur_s );
                if( cur_uri != null ) {
                    String cur_path = cur_uri.getPath();
                    if( cur_path != null ) {
                        Dialogs dh = obtainDialogsInstance( FIND_ACT );
                        dh.setCookie( cur_path );
                        showDialog( FIND_ACT );
                        return;
                    }
                }
            }
            showMessage( "Error" );
        }
        else
            showError( getString( R.string.find_on_fs_only ) );
    }    
    
    /*
     * Commander interface implementation
     */
    @Override
    public void Navigate( Uri uri, String posTo ) {
        panels.Navigate( panels.getCurrent(), uri, posTo );
    }

    @Override
    public void Open( String path ) {
        try {
            Intent i = new Intent( Intent.ACTION_VIEW );
            Intent op_intent = getIntent();
            if( op_intent != null ) {
                String action = op_intent.getAction();
                if( Intent.ACTION_PICK.equals( action ) ) {
                    i.setData( Uri.parse( path ) );
                    setResult( RESULT_OK, i );
                    finish();
                    return;
                }
                if( Intent.ACTION_GET_CONTENT.equals( action ) ) {
                    i.setData( Uri.parse( FileProvider.URI_PREFIX + path ) );
                    setResult( RESULT_OK, i );
                    finish();
                    return;
                }
            }
            String mime = Utils.getMimeByExt( Utils.getFileExt( path ) );
            i.setDataAndType( Uri.fromFile( new File( path ) ), mime );
            startActivity( i );
        } catch( ActivityNotFoundException e ) {
            showMessage("Application for open '" + path + "' is not available, ");
        }
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public boolean notifyMe( Notify progress ) {
        if( progress.status == Commander.OPERATION_STARTED ) {
            setProgressBarIndeterminateVisibility( true );
            if( progress.string != null && progress.string.length() > 0 )
                showMessage( progress.string );
            return false;
        }
        Dialogs dh = getDialogsInstance( Dialogs.PROGRESS_DIALOG );
        
        if( progress.status >= 0 ) {
            //dh = obtainDialogsInstance( Dialogs.PROGRESS_DIALOG );
            if( on ) {
                if( dh != null ) {
                    Dialog d = dh.getDialog();
                    if( d != null && d.isShowing() ) {
                        dh.setProgress( progress.string, progress.status, progress.substat );
                        return false;
                    }
                }
                showDialog( Dialogs.PROGRESS_DIALOG );
            }
            else {
                if( progress.string != null && progress.string.length() > 0 )
                    setSystemNotification( progress.string, progress.status );
            }
            return false;
        }
        else {
            if( dh != null ) {
                Dialog d = dh.getDialog();
                if( d != null && d.isShowing() ) {
                    //Log.v( TAG, "Trying to cancel the progress dialog..." );
                    d.cancel();
                }
            }
        }
        if( notMan != null ) notMan.cancel( 1 ); 
        setProgressBarIndeterminateVisibility( false );
        panels.operationFinished();
        switch( progress.status ) {
        case OPERATION_SUSPENDED_FILE_EXIST: {
                dh = obtainDialogsInstance( Dialogs.FILE_EXIST_DIALOG );
                dh.setMessageToBeShown( progress.string, null );
                dh.showDialog();
            }
            return false;
        case OPERATION_FAILED:
            if( progress.cookie != null && progress.cookie.length() > 0 ) {
                int which_panel = progress.cookie.charAt( 0 ) == '1' ? 1 : 0;
                panels.setPanelTitle( getString( R.string.fail ), which_panel );
            }
            if( progress.string != null && progress.string.length() > 0 )
                showError( progress.string );
            panels.redrawLists();
            return true;
        case OPERATION_FAILED_LOGIN_REQUIRED: 
            if( progress.string != null ) {
                dh = obtainDialogsInstance( Dialogs.LOGIN_DIALOG );
                dh.setMessageToBeShown( null, progress.string );
                showDialog( Dialogs.LOGIN_DIALOG );
            }
            return true;
        case OPERATION_COMPLETED_REFRESH_REQUIRED:
            panels.refreshLists();
            break;
        case OPERATION_COMPLETED:
            if( progress.cookie != null && progress.cookie.length() > 0 ) {
                //Log.v( TAG, "notify with cookie: " + progress.cookie );
                int which_panel = progress.cookie.charAt( 0 ) == '1' ? 1 : 0;
                String item_name = progress.cookie.substring( 1 );
                panels.recoverAfterRefresh( item_name, which_panel );
            }
            else
                panels.recoverAfterRefresh( null, -1 );
            break;
        }
        if( ( show_confirm || progress.substat == OPERATION_REPORT_IMPORTANT )  
                           && progress.string != null && progress.string.length() > 0 )
            showInfo( progress.string );
        return true;
    }

    private void setSystemNotification( String msg, int p ) {
        if( notMan == null ) return;
        long cur_time = System.currentTimeMillis();
        if( notLastTime + 1000 > cur_time ) return;
        notLastTime = cur_time;
        Notification notification = new Notification( R.drawable.icon, getString( R.string.inprogress ), cur_time );
        Intent intent = new Intent( this, FileCommander.class ).setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                                                          Intent.FLAG_ACTIVITY_SINGLE_TOP );
        notification.contentIntent = PendingIntent.getActivity( this, 0, intent, 
                                     PendingIntent.FLAG_CANCEL_CURRENT );
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        RemoteViews not_view = new RemoteViews( getPackageName(), R.layout.progress );
        not_view.setTextColor( R.id.text, 0xFF000000 );
        not_view.setTextViewText( R.id.text, msg );
        not_view.setProgressBar( R.id.progress_bar, 100, p, false );
        not_view.setTextColor( R.id.percent, 0xFF000000 );
        not_view.setTextViewText( R.id.percent, "" );
        notification.contentView = not_view;
        notMan.notify( 1, notification );
    }
    
    public void setResolution( int r ) {
        synchronized( this ) {
            file_exist_resolution = r;
            notify();
        }
        /*
        if( file_exist_resolution != Commander.ABORT )
            showDialog( Dialogs.PROGRESS_DIALOG );
        */
    }    
    @Override
    public int getResolution() {
        int r = file_exist_resolution;
        file_exist_resolution = Commander.UNKNOWN; 
        return r;
    }    
    
    @Override
    public void showError( String errMsg ) {
        if( !on ) return;
        Dialogs dh = obtainDialogsInstance( Dialogs.ALERT_DIALOG );
        dh.setMessageToBeShown( errMsg, null );
        dh.showDialog();
    }

    @Override
    public void showInfo( String msg ) {
        if( !on ) return;
        if( msg.length() < 32 )
            showMessage( msg );
        else {
            Dialogs dh = obtainDialogsInstance( Dialogs.INFO_DIALOG );
            dh.setMessageToBeShown( msg, null );
            dh.showDialog();
        }
    }

    public final void startViewURIActivity( int res_id ) {
        Intent i = new Intent( Intent.ACTION_VIEW );
        i.setData( Uri.parse( getString( res_id ) ) );
        startActivity( i );
    }
    private final boolean getRotMode() {
        boolean sideXside = false;
        try {
            Display disp = getWindowManager().getDefaultDisplay();
            sideXside = disp.getWidth() > disp.getHeight();
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
        return sideXside;
    }
    private final void setConfirmMode( SharedPreferences sharedPref ) {
        show_confirm = sharedPref.getBoolean("show_confirm", true );
    }
}
