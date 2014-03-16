package com.ghostsq.commander;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.FSAdapter;
import com.ghostsq.commander.adapters.FindAdapter;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.MediaScanTask;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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

public class FileCommander extends Activity implements Commander, ServiceConnection, View.OnClickListener {
    private final static String TAG = "GhostCommanderActivity";
    public final static int REQUEST_CODE_PREFERENCES = 1, REQUEST_CODE_SRV_FORM = 2;
    public final static int FIND_ACT = 1017, DBOX_APP = 3592, SMB_ACT = 2751, FTP_ACT = 4501, SFTP_ACT = 2450;

    private ArrayList<Dialogs> dialogs;
    private ProgressDialog waitPopup;
    public Panels panels;
    private boolean on = false, exit = false, dont_restore = false, sxs_auto = true, show_confirm = true, back_exits = false,
            ab = false;
    private String lang = ""; // just need to issue a warning on change
    private int file_exist_resolution = Commander.UNKNOWN;
    private IBackgroundWork background_work;
    private NotificationManager notMan = null;
    private ArrayList<NotificationId> bg_ids = new ArrayList<NotificationId>();
    private final static String PARCEL = "parcel", TASK_ID = "task_id";

    private class NotificationId {
        public long id;
        public long started, last;

        public NotificationId(long id_) {
            id = id_;
            started = System.currentTimeMillis();
            last = started;
        }

        public final boolean is( long fid ) {
            return id == fid;
        }
    }

    public final void showMemory( String s ) {
        final ActivityManager sys = (ActivityManager)getSystemService( Context.ACTIVITY_SERVICE );
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        sys.getMemoryInfo( mem );
        showMessage( s + "\n Memory: " + mem.availMem + ( mem.lowMemory ? " !!!" : "" ) );
    }

    public final void showMessage( String s ) {
        Toast.makeText( this, s, Toast.LENGTH_LONG ).show();
    }

    public int getWidth() {
        return panels.getWidth();
    }

    public boolean isActionBar() {
        return ab;
    }

    protected final Dialogs getDialogsInstance( int id ) {
        for( int i = 0; i < dialogs.size(); i++ )
            if( dialogs.get( i ).getId() == id )
                return dialogs.get( i );
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
        dialogs.add( dh );
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
            if( ( getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK ) >= Configuration.SCREENLAYOUT_SIZE_LARGE )

                /*
                 * Display display = getWindowManager().getDefaultDisplay();
                 * DisplayMetrics displayMetrics = new DisplayMetrics();
                 * display.getMetrics(displayMetrics);
                 * 
                 * if( displayMetrics.heightPixels / displayMetrics.densityDpi >
                 * 1000 )
                 */
                ab = getWindow().requestFeature( Window.FEATURE_ACTION_BAR );
        }
        if( !ab )
            requestWindowFeature( Window.FEATURE_NO_TITLE );

        // TODO: show progress when there is no title
        // requestWindowFeature( Window.FEATURE_INDETERMINATE_PROGRESS );
        dialogs = new ArrayList<Dialogs>( Dialogs.numDialogTypes );
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( this );
        back_exits = sharedPref.getBoolean( "exit_on_back", false );
        lang = sharedPref.getString( "language", "" );
        Utils.changeLanguage( this );
        String panels_mode = sharedPref.getString( "panels_sxs_mode", "a" );
        sxs_auto = panels_mode.equals( "a" );
        boolean sxs = sxs_auto ? getRotMode() : panels_mode.equals( "y" );
        panels = new Panels( this, sxs );
        setConfirmMode( sharedPref );

        notMan = (NotificationManager)getSystemService( Context.NOTIFICATION_SERVICE );
        bindService( new Intent( this /* ? */, BackgroundWork.class ), this, Context.BIND_AUTO_CREATE );
    }

    @Override
    protected void onStart() {
        // Log.v( TAG, "Starting\n" );
        super.onStart();
        on = true;
        if( dont_restore )
            dont_restore = false;
        else {
            Utils.changeLanguage( this );
            Intent intent = getIntent();

            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            Panels.State s = panels.new State();
            s.restore( prefs );

            String action = intent.getAction();
            Log.i( TAG, "Action: " + action );

            if( Intent.ACTION_VIEW.equals( action ) ) {
                Log.d( TAG, "Not restoring " + s.getCurrent() );
                panels.setState( s, s.getCurrent() );
                Log.d( TAG, "VIEW opens in " + panels.getCurrent() );
                onNewIntent( intent );
                return;
            }

            panels.setState( s, -1 );
            final String FT = "first_time";
            if( prefs.getBoolean( FT, true ) ) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean( FT, false );
                editor.commit();
                showInfo( getString( R.string.keys_text ) );
            }

            // panels.setPanelCurrent( use_panel );

            if( Intent.ACTION_SEARCH_LONG_PRESS.equals( action ) ) {
                showSearchDialog();
                return;
            }
        }
    }

    @Override
    protected void onPause() {
        Log.v( TAG, "Pausing\n" );
        super.onPause();
        on = false;
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        Panels.State s = panels.getState();
        s.store( editor );
        editor.commit();
    }

    @Override
    protected void onResume() {
        Log.v( TAG, "Resuming\n" );
        super.onResume();
        on = true;
    }

    @Override
    protected void onStop() {
        Log.v( TAG, "Stopping\n" );
        super.onStop();
        on = false;
    }

    @Override
    protected void onDestroy() {
        Log.v( TAG, "Destroying\n" );
        on = false;
        super.onDestroy();
        if( notMan != null )
            notMan.cancelAll();
        panels.Destroy();
        unbindService( this );
        if( isFinishing() && exit ) {
            Log.i( TAG, "Good bye cruel world..." );
            System.exit( 0 );
        }
    }

    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        Log.i( TAG, "Saving Instance State" );
        Panels.State s = panels.getState();
        s.store( outState );
        super.onSaveInstanceState( outState );
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        Log.i( TAG, "Restoring Instance State" );
        if( savedInstanceState != null ) {
            Panels.State s = panels.new State();
            s.restore( savedInstanceState );
            panels.setState( s, -1 );
        }
        super.onRestoreInstanceState( savedInstanceState );
    }

    @Override
    public void onConfigurationChanged( Configuration newConfig ) {
        Utils.changeLanguage( this );
        super.onConfigurationChanged( newConfig );
        panels.setLayoutMode( sxs_auto ? newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE : panels.getLayoutMode() );
        /*
         * // Checks whether a hardware keyboard is available if
         * (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO)
         * { Toast.makeText(this, "keyboard visible",
         * Toast.LENGTH_SHORT).show(); } else if (newConfig.hardKeyboardHidden
         * == Configuration.HARDKEYBOARDHIDDEN_YES) { Toast.makeText(this,
         * "keyboard hidden", Toast.LENGTH_SHORT).show(); }
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
        } catch( Exception e ) {
            Log.e( TAG, "onCreateContextMenu()", e );
        }
    }

    @Override
    public boolean onContextItemSelected( MenuItem item ) {
        try {
            panels.resetQuickSearch();
            AdapterView.AdapterContextMenuInfo info;
            info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
            if( info == null )
                return false;
            panels.setSelection( info.position );
            int item_id = item.getItemId();
            if( OPEN == item_id )
                panels.openItem( info.position );
            else
                dispatchCommand( item_id );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "onContextItemSelected()", e );
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
            if( ab ) {
                inflater.inflate( R.menu.actions, menu );
                inflater.inflate( R.menu.menu, menu );
                MenuItem list_menu = menu.findItem( R.id.list );
                if( list_menu != null )
                    list_menu.setVisible( false );
            } else
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
    public void issue( Intent in, int ret ) {
        if( in == null )
            return;
        try {
            if( ret == 0 )
                startActivity( in );
            else
                startActivityForResult( in, ret );
        } catch( Exception e ) {
            Log.e( TAG, in.getDataString(), e );
        }
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
        super.onActivityResult( requestCode, resultCode, data );
        Log.d( TAG, "onActivityResult( " + requestCode + ", " + data + " )" );
        switch( requestCode ) {
        case REQUEST_CODE_PREFERENCES: {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( this );
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
            panels.showToolbar( sharedPref.getBoolean( "show_toolbar", true ) );
            setConfirmMode( sharedPref );
        }
            break;
        case REQUEST_CODE_SRV_FORM: {
            if( resultCode == RESULT_OK ) {
                dont_restore = true;
                Uri uri = data.getData();
                if( uri != null ) {
                    Credentials crd = null;
                    try {
                        crd = (Credentials)data.getParcelableExtra( Credentials.KEY );
                    } catch( Exception e ) {
                        Log.e( TAG, "on taking credentials from parcel", e );
                    }
                    panels.Navigate( panels.getCurrent(), uri, crd, null );
                }
            }
        }
            break;
        case R.id.create_shortcut:
            if( data != null ) {
                data.setAction( "com.android.launcher.action.INSTALL_SHORTCUT" );
                sendBroadcast( data );
            }
            break;
        case ACTIVITY_REQUEST_FOR_NOTIFY_RESULT:
            if( data != null ) {
                on = true;
                Message msg = data.getParcelableExtra( MESSAGE_EXTRA );
                if( msg != null )
                    notifyMe( msg );
                else
                    panels.refreshLists( null );
            }
            break;
        default:
            handleActivityResult( requestCode, resultCode, data );
        }
    }

    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        // Log.v( TAG, "global key:" + keyCode + ", number:" + event.getNumber()
        // + ", uchar:" + event.getUnicodeChar() );
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
            showInfo( getString( R.string.keys_text ) );
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
            panels.togglePanels( false );
            return true;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            if( panels.volumeLegacy ) {
                panels.togglePanels( false );
                return true;
            }
            break;
        case KeyEvent.KEYCODE_SEARCH:
            showSearchDialog();
            return false;
        }
        return super.onKeyDown( keyCode, event );
    }

    @Override
    public boolean onKeyUp( int keyCode, KeyEvent event ) {
        if( keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN )
            return true;

        return super.onKeyUp( keyCode, event );
    }

    /*
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    // ?????????????????????????????????????????????????
    // @Override
    public void onClick( View button ) {
        panels.resetQuickSearch();
        if( button == null )
            return;
        dispatchCommand( button.getId() );
    }

    public final boolean backExit() {
        if( back_exits ) {
            finish();
            return true;
        }
        return false;
    }

    public void dispatchCommand( int id ) {
        try {
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
                panels.openForEdit( null );
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
                showDialog( id );
                break;
            case R.id.donate:
                startViewURIActivity( R.string.donate_uri );
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
            case R.id.menu:
                openOptionsMenu();
            case R.id.oth_sh_this:
            case R.id.eq:
                panels.makeOtherAsCurrent();
                break;
            case R.id.toggle_panels_mode:
                panels.togglePanelsMode();
                break;
            case R.id.tgl:
                panels.togglePanels( true );
                break;
            case R.id.sz:
                panels.showSizes();
                break;
            case R.id.action_back:
                panels.goUp();
                break;
            case R.id.totop:
                panels.goTop();
                break;
            case R.id.tobot:
                panels.goBot();
                break;
            case R.id.home:
                Navigate( Uri.parse( "home:" ), null, null );
                break;
            case R.id.favs:
                Navigate( Uri.parse( "favs:" ), null, null );
                break;
            case R.id.sdcard:
                Navigate( Uri.parse( Panels.DEFAULT_LOC ), null, null );
                break;
            case R.id.root: {
                Uri cu = panels.getFolderUriWithAuth( true );
                String shm = cu != null ? cu.getScheme() : null;
                String to_go;
                if( "root".equals( shm ) )
                    to_go = cu.getPath();
                else
                    to_go = RootAdapter.DEFAULT_LOC + ( cu == null || Utils.str( shm ) ? "" : cu.getPath() );
                Navigate( Uri.parse( to_go ), null, null );
            }
                break;
            case R.id.mount:
                Navigate( Uri.parse( MountAdapter.DEFAULT_LOC ), null, null );
                break;

            case FTP_ACT: {
                Intent i = new Intent( this, ServerForm.class );
                i.putExtra( "schema", "ftp" );
                startActivityForResult( i, REQUEST_CODE_SRV_FORM );
            }
                break;
            case SFTP_ACT: {
                Intent i = new Intent( this, ServerForm.class );
                i.putExtra( "schema", "sftp" );
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
                panels.refreshLists( null );
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
            case SHRCT_CMD:
                panels.createDesktopShortcut();
                break;
            case FAV_FLD:
                panels.faveSelectedFolder();
                break;
            case R.id.softkbd:
                InputMethodManager imm = (InputMethodManager)getSystemService( Context.INPUT_METHOD_SERVICE );
                imm.toggleSoftInput( 0, 0 );
                break;
            case R.id.hidden:
                panels.toggleHidden();
                break;
            case R.id.rescan:
                if( android.os.Build.VERSION.SDK_INT >= 19 ) {
                    showInfo( getString( R.string.wait ) );
                    MediaScanTask.scanMedia( this, new File( Panels.DEFAULT_LOC ) );
                } else
                    sendBroadcast( new Intent( Intent.ACTION_MEDIA_MOUNTED, Uri.parse( "file://" + Panels.DEFAULT_LOC ) ) );
                break;
            default:
                CommanderAdapter ca = panels.getListAdapter( true );
                if( ca != null )
                    ca.doIt( id, panels.getSelectedOrChecked() );
            }
        } catch( Throwable e ) {
            e.printStackTrace();
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
        } else
            showError( getString( R.string.find_on_fs_only ) );
    }

    /*
     * Commander interface implementation
     */
    @Override
    public void Navigate( Uri uri, Credentials crd, String posTo ) {
        panels.Navigate( panels.getCurrent(), uri, crd, posTo );
    }

    @Override
    public void Open( Uri uri, Credentials crd ) {
        try {
            if( uri == null )
                return;
            String scheme = uri.getScheme();
            String path = uri.getPath();
            String ext = Utils.getFileExt( "zip".equals( scheme ) ? uri.getFragment() : path );
            if( !Utils.str( ext ) )
                ext = Utils.getFileExt( uri.getFragment() );
            String mime = Utils.getMimeByExt( ext );
            if( !Utils.str( scheme ) ) {
                if( ext != null && ext.compareToIgnoreCase( ".zip" ) == 0 ) {
                    Navigate( uri.buildUpon().scheme( "zip" ).build(), null, null );
                    return;
                }
                Intent i = new Intent( Intent.ACTION_VIEW );
                Intent op_intent = getIntent();
                if( op_intent != null ) {
                    String action = op_intent.getAction();
                    if( Intent.ACTION_PICK.equals( action ) ) {
                        i.setData( uri );
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
                i.setDataAndType( uri.buildUpon().scheme( "file" ).authority( "" ).build(), mime );
                i.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET );
                // | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                startActivity( i );
            } else if( mime != null && ( mime.startsWith( "audio" ) || mime.startsWith( "video" ) ) ) {
                startService( new Intent( this, StreamServer.class ) );
                Intent i = new Intent( Intent.ACTION_VIEW );

                String http_url = "http://127.0.0.1:5322/" + Uri.encode( uri.toString() );
                if( crd != null )
                    StreamServer.credentials = crd;
                // Log.d( TAG, "Stream " + mime + " from: " + http_url );
                i.setDataAndType( Uri.parse( http_url ), mime );
                i.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET );
                startActivity( i );
                return;
            }

        } catch( ActivityNotFoundException e ) {
            showMessage( "Application for open '" + uri.toString() + "' is not available, " );
        } catch( Exception e ) {
            Log.e( TAG, uri.toString(), e );
        }
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    protected void onNewIntent( Intent intent ) {
        on = true;
        super.onNewIntent( intent );
        try {
            Bundle extras = intent.getExtras();
            if( extras != null ) {
                long task_id = extras.getLong( TASK_ID );
                if( task_id > 0 ) {
                    // switch the current active task with the one which comes
                    // from the background
                    Dialogs dh = obtainDialogsInstance( Dialogs.PROGRESS_DIALOG );
                    if( dh != null ) {
                        long d_task_id = dh.getTaskId();
                        if( d_task_id != 0 )
                            bg_ids.add( new NotificationId( d_task_id ) );
                        dh.cancelDialog();
                    }
                    remBgNotifId( task_id );
                    dh.setTaskId( task_id );
                    dh.showDialog();

                    Parcelable pe = intent.getParcelableExtra( PARCEL );
                    if( pe != null && pe instanceof Message )
                        notifyMe( (Message)pe );
                    return;
                }
            }
            String action = intent.getAction();
            Uri uri = intent.getData();
            if( uri != null && Intent.ACTION_VIEW.equals( action ) ) {
                // || "org.openintents.action.VIEW_DIRECTORY".equals( action ) )
                // { // DiskUsage support
                Log.v( TAG, "Intent URI: " + uri );
                Credentials crd = null;
                try {
                    crd = (Credentials)intent.getParcelableExtra( Credentials.KEY );
                } catch( Throwable e ) {
                    Log.e( TAG, "on extracting credentials from an intent", e );
                }

                String file_name = null;
                String type = intent.getType();
                if( "application/x-zip-compressed".equals( type ) || "application/zip".equals( type ) )
                    uri = uri.buildUpon().scheme( "zip" ).build();
                else if( ContentResolver.SCHEME_CONTENT.equals( uri.getScheme() ) ) {
                    // unknown content is being passed. Let's just save it
                    try {
                        InputStream is = getContentResolver().openInputStream( uri );
                        if( is != null ) {
                            File dwf = new File( Panels.DEFAULT_LOC, "download" );
                            if( !dwf.exists() )
                                dwf.mkdirs();

                            String fn = uri.getLastPathSegment();
                            File f = null;
                            for( int i = 0; i < 99; i++ ) {
                                file_name = i == 0 ? fn : fn + "_" + i;
                                f = new File( dwf, file_name );
                                if( f.exists() )
                                    continue;
                                if( f.createNewFile() )
                                    break;
                                f = null;
                            }
                            if( f != null ) {
                                BufferedOutputStream bos = new BufferedOutputStream( new FileOutputStream( f ), 8192 );
                                byte[] buf = new byte[4096];
                                int n;
                                while( ( n = is.read( buf ) ) != -1 )
                                    bos.write( buf, 0, n );
                                bos.close();
                                is.close();
                                uri = Uri.fromFile( dwf );
                                showMessage( getString( R.string.copied_f, f.toString() ) );
                            } else
                                showError( getString( R.string.not_accs, fn ) );
                        }
                    } catch( Exception e ) {
                        showError( getString( R.string.not_accs, "" ) ); // TODO
                                                                         // more
                                                                         // verbose
                    }
                }
                panels.Navigate( panels.getCurrent(), uri, crd, file_name );
                dont_restore = true;
            } else
                handleActivityResult( -1, -1, intent );
        } catch( Exception e ) {
            Log.e( TAG, "Can't extract a parcel from intent", e );
        }
    }

    private final void handleActivityResult( int requestCode, int resultCode, Intent data ) {
        CommanderAdapter ca = panels.getListAdapter( true );
        if( ca != null && ca.handleActivityResult( requestCode, resultCode, data ) )
            return;
        ca = panels.getListAdapter( false );
        if( ca != null )
            ca.handleActivityResult( requestCode, resultCode, data );
    }

    @Override
    public boolean startEngine( Engine e ) {
        if( background_work != null ) {
            background_work.start( e );
            return true;
        }
        Log.e( TAG, "background work service is not available!" );
        return false;
    }

    public boolean stopEngine( long task_id ) {
        if( background_work == null )
            return false;
        return background_work.stopEngine( task_id );
    }

    @Override
    public boolean notifyMe( Message progress ) {
        final boolean TERMINATE = true, CONTINUE = false;
        String string = null;
        try {
            if( progress.obj != null ) {
                if( progress.obj instanceof Bundle )
                    string = ( (Bundle)progress.obj ).getString( MESSAGE_STRING );
                else if( progress.obj instanceof String ) {
                    string = (String)progress.obj;
                    Log.w( TAG, "Old version message type!" );
                }
            }
            Bundle b = progress.getData();
            String cookie = b != null ? b.getString( NOTIFY_COOKIE ) : null;
            if( progress.what == Commander.OPERATION_STARTED ) {
                setProgressBarIndeterminateVisibility( true );
                if( Utils.str( string ) )
                    showMessage( string );
                return CONTINUE;
            }
            long task_id = b.getLong( Commander.NOTIFY_TASK );
            // Log.v( TAG, "got message " + progress.what + " from task " +
            // task_id + " " + string );
            Dialogs dh = null;
            if( progress.what == OPERATION_IN_PROGRESS ) {
                if( progress.arg1 >= 0 ) {
                    boolean id_found = false;
                    if( on ) {
                        dh = obtainDialogsInstance( Dialogs.PROGRESS_DIALOG );
                        if( dh != null ) {
                            if( task_id == dh.getTaskId() ) {
                                dh.showDialog();
                                dh.setProgress( string, progress.arg1, progress.arg2, b != null ? b.getInt( NOTIFY_SPEED, -1 ) : 0 );
                                remBgNotifId( task_id );
                                return CONTINUE;
                            }
                            for( NotificationId bg_id : bg_ids ) {
                                if( bg_id.is( task_id ) ) {
                                    id_found = true;
                                    break;
                                }
                            }
                            if( !id_found ) {
                                Dialog dlg = dh.getDialog();
                                if( dlg == null || !dlg.isShowing() ) {
                                    dh.setTaskId( task_id );
                                    dh.showDialog();
                                    dh.setProgress( string, progress.arg1, progress.arg2, b != null ? b.getInt( NOTIFY_SPEED, -1 )
                                            : 0 );
                                    return CONTINUE;
                                }
                            }
                        }
                    }
                    if( !id_found )
                        addBgNotifId( task_id );
                    setSystemOngoingNotification( (int)task_id, string, progress.arg2 > 0 ? progress.arg2 : progress.arg1 );
                    return CONTINUE;
                } else {
                    if( waitPopup == null )
                        waitPopup = ProgressDialog.show( this, "", getString( R.string.wait ), true, true );
                }
                return CONTINUE;
            }
            dh = getDialogsInstance( Dialogs.PROGRESS_DIALOG );
            if( dh != null && task_id == dh.getTaskId() ) {
                Log.d( TAG, "Cancelling dialog " + task_id );
                dh.cancelDialog();
            } else {
                // Log.d( TAG, "No opened dialog found " + task_id );
                remBgNotifId( task_id );
            }

            if( notMan != null )
                notMan.cancel( (int)task_id );
            if( !on ) {
                setSystemNotification( (int)task_id, progress );
                return progress.what != OPERATION_SUSPENDED_FILE_EXIST;
            }
            if( waitPopup != null ) {
                waitPopup.cancel();
                waitPopup = null;
            }
            setProgressBarIndeterminateVisibility( false );
            panels.operationFinished();
            switch( progress.what ) {
            case OPERATION_SUSPENDED_FILE_EXIST: {
                dh = obtainDialogsInstance( Dialogs.FILE_EXIST_DIALOG );
                dh.setMessageToBeShown( string, null );
                dh.showDialog();
            }
                return CONTINUE;
            case OPERATION_FAILED:
            case OPERATION_FAILED_REFRESH_REQUIRED:
                if( Utils.str( cookie ) ) {
                    int which_panel = cookie.charAt( 0 ) == '1' ? 1 : 0;
                    panels.setPanelTitle( getString( R.string.fail ), which_panel );
                }
                if( Utils.str( string ) )
                    showError( string );
                if( progress.what == OPERATION_FAILED_REFRESH_REQUIRED ) {
                    String posto = b != null ? b.getString( NOTIFY_POSTO ) : null;
                    panels.refreshLists( posto );
                } else
                    panels.redrawLists();
                return TERMINATE;
            case OPERATION_FAILED_LOGIN_REQUIRED:
                if( string != null ) {
                    dh = obtainDialogsInstance( Dialogs.LOGIN_DIALOG );
                    if( b != null ) {
                        Parcelable crd_p = b.getParcelable( NOTIFY_CRD );
                        if( crd_p != null && crd_p instanceof Credentials )
                            dh.setCredentials( (Credentials)crd_p, Utils.str( cookie ) ? cookie.charAt( 0 ) == '1' ? 1 : 0 : -1 );
                    }
                    dh.setMessageToBeShown( string, cookie );
                    showDialog( Dialogs.LOGIN_DIALOG );
                }
                return TERMINATE;
            case OPERATION_COMPLETED_REFRESH_REQUIRED:
                String posto = b != null ? b.getString( NOTIFY_POSTO ) : null;
                panels.refreshLists( posto );
                break;
            case OPERATION_COMPLETED:
                if( Utils.str( cookie ) ) {
                    int which_panel = cookie.charAt( 0 ) == '1' ? 1 : 0;
                    String item_name = cookie.substring( 1 );
                    panels.recoverAfterRefresh( item_name, which_panel );
                } else
                    panels.recoverAfterRefresh( null, -1 );
                break;
            }
            if( ( show_confirm || progress.arg1 == OPERATION_REPORT_IMPORTANT ) && Utils.str( string ) )
                showInfo( string );
        } catch( Exception e ) {
            Log.e( TAG, string, e );
        }
        return TERMINATE;
    }

    public final void addBgNotifId( long id ) {
        for( NotificationId bg_id : bg_ids ) {
            if( bg_id.is( id ) )
                return;
        }
        bg_ids.add( new NotificationId( id ) );
    }

    private final boolean remBgNotifId( long id ) {
        for( NotificationId bg_id : bg_ids ) {
            if( bg_id.is( id ) ) {
                bg_ids.remove( bg_id );
                notMan.cancel( (int)id );
                return true;
            }
        }
        return false;
    }

    private PendingIntent getPendingIntent( long task_id, Parcelable parcel ) {
        Intent intent = new Intent( this, FileCommander.class );
        intent.setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP );
        intent.setAction( Intent.ACTION_MAIN );
        intent.putExtra( TASK_ID, task_id );
        if( parcel != null )
            intent.putExtra( PARCEL, parcel );
        return PendingIntent.getActivity( this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT );
    }

    private void setSystemOngoingNotification( int id, String str, int p ) {
        if( notMan == null || str == null )
            return;
        NotificationId n_id = null;
        for( NotificationId bg_id : bg_ids ) {
            if( bg_id.is( id ) )
                n_id = bg_id;
        }
        if( n_id == null )
            return;
        long cur_time = System.currentTimeMillis();
        if( n_id.last + 1000 > cur_time )
            return;
        n_id.last = cur_time;
        Notification notification = new Notification( R.drawable.icon, getString( R.string.inprogress ), n_id.started );
        notification.contentIntent = getPendingIntent( id, null );
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        RemoteViews not_view = new RemoteViews( getPackageName(), R.layout.progress );
        not_view.setTextViewText( R.id.text, str.replace( "\n", " " ) );
        not_view.setProgressBar( R.id.progress_bar, 100, p, false );
        not_view.setTextViewText( R.id.percent, "" + p + "%" );
        notification.contentView = not_view;
        notMan.notify( id, notification );
    }

    private void setSystemNotification( int id, Message msg ) {
        if( notMan == null || msg == null )
            return;
        String str = "";
        try {
            if( msg.obj instanceof Bundle )
                str = ( (Bundle)msg.obj ).getString( MESSAGE_STRING );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
        Notification notification = new Notification( R.drawable.icon, str, System.currentTimeMillis() );
        notification.setLatestEventInfo( this, getString( R.string.app_name ), str, getPendingIntent( id, msg ) );
        notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;

        if( msg.what == OPERATION_SUSPENDED_FILE_EXIST ) {
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
            notification.defaults |= Notification.DEFAULT_VIBRATE;
            notification.ledARGB = 0xFFFF0000;
            notification.ledOnMS = 300;
            notification.ledOffMS = 1000;
        }
        notMan.notify( id, notification );
    }

    public void setResolution( int r ) {
        synchronized( this ) {
            file_exist_resolution = r;
            notify();
        }
    }

    @Override
    public int getResolution() {
        int r = file_exist_resolution;
        file_exist_resolution = Commander.UNKNOWN;
        return r;
    }

    @Override
    public void showError( String errMsg ) {
        try {
            if( !on )
                return;
            Dialogs dh = obtainDialogsInstance( Dialogs.ALERT_DIALOG );
            dh.setMessageToBeShown( errMsg, null );
            dh.showDialog();
            return;
        } catch( Exception e ) {
            e.printStackTrace();
        }
        showMessage( errMsg );
    }

    @Override
    public void showInfo( String msg ) {
        if( !on )
            return;
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
        show_confirm = sharedPref.getBoolean( "show_confirm", true );
    }

    // ServiceConnection implementation

    @Override
    public void onServiceConnected( ComponentName name, IBinder service ) {
        background_work = ( (IBackgroundWork.IBackgroundWorkBinder)service ).init( this );
    }

    @Override
    public void onServiceDisconnected( ComponentName name ) {
        background_work = null;
    }
}
