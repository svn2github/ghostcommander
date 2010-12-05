package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.ContextMenu;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.KeyEvent;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.Toast;

public class FileCommander extends Activity implements Commander, View.OnClickListener {
    private final static String TAG = "GhostCommanderActivity";
    private final static int REQUEST_CODE_PREFERENCES = 1, REQUEST_CODE_SRV_FORM = 2;
    public  final static int RENAME_ACT = 1002,  NEWF_ACT = 1014, EDIT_ACT = 1004, COPY_ACT = 1005, 
                               MOVE_ACT = 1006, MKDIR_ACT = 1007,  DEL_ACT = 1008, FIND_ACT = 1017, DONATE = 3333,
                               SMB_APP = 0275, DBOX_APP = 3592;
    private final static int SHOW_SIZE = 12, CHANGE_LOCATION = 993, MAKE_SAME = 217, SEND_TO = 236, OPEN_WITH = 903;
    private ArrayList<Dialogs> dialogs;
    public  Panels  panels, panelsBak = null;
    private boolean exit = false, dont_restore = false;
    private String lang = "";

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
        Dialogs dh = getDialogsInstance(id);
        if( dh == null ) {
            dh = new Dialogs(this, id);
            dialogs.add(dh);
        }
        return dh;
    }

    protected final void addDialogsInstance( Dialogs dh ) {
        dialogs.add(dh);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        dialogs = new ArrayList<Dialogs>(Dialogs.numDialogTypes);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        changeLanguage( sharedPref.getString( "language", "" ) );
        panels = new Panels(this, sharedPref.getBoolean( "panels_mode", false ) ? R.layout.alt : R.layout.main);
    }

    @Override
    protected void onStart() {
        Log.i( TAG, "Starting\n");
        super.onStart();
        if( dont_restore )
            dont_restore = false;
        else {
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            Panels.State s = panels.new State();
            s.restore(prefs);
            panels.setState(s);
        }
    }

    @Override
    protected void onPause() {
        Log.i( TAG, "Pausing\n");
        super.onPause();
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        Panels.State s = panels.getState();
        s.store(editor);
        editor.commit();
    }

    @Override
    protected void onStop() {
        Log.i( TAG, "Stopping\n");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i( TAG, "Destroying\n");
        panels.Destroying();
        super.onDestroy();
        if( isFinishing() && exit )
            System.exit( 0 );  // does not work in API7
    }
/*
    //these two methods are not called on screen rotation in v1.5, so all the store/restore is called from pause/start 
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        Log.i( TAG, "Ghost Commander Saving\n");
        Panels.State s = panels.getState();
        s.store(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        Log.i( TAG, "Ghost Commander Restoring\n");
        if( savedInstanceState != null ) {
            Panels.State s = panels.new State();
            s.restore(savedInstanceState);
            panels.setState(s);
        }
        super.onRestoreInstanceState(savedInstanceState);
    }
*/
    @Override
    public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo ) {
        int num = panels.getNumItemsChecked();
        if( num <= 1 ) {
            try {
                AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo)menuInfo;
                if( acmi.position == 0 ) {
                    menu.add(0, CHANGE_LOCATION, 0, R.string.enter );
                    menu.add(0, MAKE_SAME, 0, R.string.oth_sh_this );
                    return;
                }
            }
            catch( ClassCastException e ) {
                Log.e( TAG, "onCreateContextMenu() cast exception\n", e );
            }
        }
        menu.setHeaderTitle("Operation");
        CommanderAdapter ca = panels.getListAdapter( true );
        boolean fs_adapter = ca instanceof FSAdapter || ca instanceof FindAdapter;
        if( fs_adapter ) { 
            menu.add( 0, SHOW_SIZE, 0, R.string.show_size );
            if( num <= 1 ) {
                menu.add( 0,   SEND_TO, 0, R.string.send_to );
            }
        }
        if( num <= 1 ) {
            menu.add( 0, RENAME_ACT, 0, R.string.rename_title );
            menu.add( 0,   EDIT_ACT, 0, R.string.edit_title );
        }
        menu.add( 0, COPY_ACT, 0, R.string.copy_title );
        if( fs_adapter )
            menu.add( 0, MOVE_ACT, 0, R.string.move_title );
        menu.add( 0,  DEL_ACT, 0, R.string.delete_title );
        if( fs_adapter ) { 
            if( num <= 1 ) {
                menu.add( 0, OPEN_WITH, 0, R.string.open_with );
            }
        }
    }

    @Override
    public boolean onContextItemSelected( MenuItem item ) {
        panels.resetQuickSearch();
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        } catch( ClassCastException e ) {
            Log.e(TAG, "Can't cast MenuInfo to AdapterView.AdapterContextMenuInfo", e);
            return true;
        }
        panels.setSelection( info.position );
        int item_id = item.getItemId();
        switch( item_id ) {
        case CHANGE_LOCATION:
            panels.openGoPanel();
            break;
        case COPY_ACT:
        case MOVE_ACT:
        case RENAME_ACT:
        case DEL_ACT:
            showDialog(item_id);
            break;
        case EDIT_ACT:
            panels.openForEdit(null);
            break;
        case SHOW_SIZE:
            panels.showSizes();
            break;
        case SEND_TO:
            panels.tryToSend();
            break;
        case OPEN_WITH:
            panels.tryToOpen();
            break;
        case MAKE_SAME:
            panels.makeOtherAsCurrent();
            break;
        }
        return true;
    }

    @Override
    protected Dialog onCreateDialog( int id ) {
        Dialogs dh = obtainDialogsInstance(id);
        Dialog d = dh.createDialog(id);
        return d != null ? d : super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog( int id, Dialog dialog ) {
        Dialogs dh = getDialogsInstance(id);
        if( dh != null )
            dh.prepareDialog(id, dialog);
        super.onPrepareDialog(id, dialog);
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        // Inflate the currently selected menu XML resource.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        panels.resetQuickSearch();
        switch( item.getItemId() ) {
        case R.id.F4:
            panels.openForEdit(null);
            break;
        case R.id.new_file:
            showDialog(NEWF_ACT);
            break;
        case R.id.F5:
            showDialog(COPY_ACT);
            break;
        case R.id.F6:
            showDialog(MOVE_ACT);
            break;
        case R.id.F7:
            showDialog(MKDIR_ACT);
            break;
        case R.id.F8:
            showDialog(DEL_ACT);
            break;
        case R.id.F10:
            exit = true;
            finish();
            break;
        case R.id.oth_sh_this:
            panels.makeOtherAsCurrent();
            break;
        case R.id.ftp: {
                Intent i = new Intent( this, ServerForm.class );
                i.putExtra( "schema", "ftp" );
                startActivityForResult( i, REQUEST_CODE_SRV_FORM );
            }
            break;
        case R.id.smb: {
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
        case R.id.select_all:
            panels.checkAllItems( true );
            break;
        case R.id.unselect_all:
            panels.checkAllItems( false );
            break;
        case R.id.prefs:
            openPrefs();
            break;
        case R.id.about:
            showDialog( Dialogs.ABOUT_DIALOG );
            break;
        case R.id.keys:
            showInfo( getString(R.string.keys_text) );
            break;
        case R.id.donate:
            showDialog(DONATE);
            break;
        case R.id.online: {
                Intent intent = new Intent( Intent.ACTION_VIEW );
                intent.setData( Uri.parse( getString( R.string.help_uri ) ) );
                startActivity( intent );
            }
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
        super.onActivityResult(requestCode, resultCode, data);
        if( requestCode == REQUEST_CODE_PREFERENCES ) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            if( !lang.equalsIgnoreCase( sharedPref.getString( "language", "" ) ) )
                showInfo( getString( R.string.restart_to_apply_lang ) );
            panels.applySettings( sharedPref );
            if( panelsBak != null )
                panelsBak.applySettings( sharedPref );
            setMode( sharedPref.getBoolean( "panels_mode", false ) );
            panels.showOrHideToolbar( sharedPref.getBoolean("show_toolbar", true ) );
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
        // showMessage( "key:" + keyCode + ", number:" + event.getNumber() +
        // ", uchar:" + event.getUnicodeChar() );
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
//            openOptionsMenu();
            return true;
        case '0':
            finish();
            return true;
        }
        switch( keyCode ) {
        case KeyEvent.KEYCODE_BACK:
        case KeyEvent.KEYCODE_DEL:
            panels.getListAdapter(true).openItem(0);
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
        switch( button.getId() ) {
        case R.id.F1:
            showInfo(getString(R.string.keys_text));
            break;
        case R.id.F2:
            showDialog(RENAME_ACT);
            break;
        case R.id.F4:
            panels.openForEdit(null);
            break;
        case R.id.SF4:
            showDialog(NEWF_ACT);
            break;
        case R.id.F5:
            showDialog(COPY_ACT);
            break;
        case R.id.F6:
            showDialog(MOVE_ACT);
            break;
        case R.id.F7:
            showDialog(MKDIR_ACT);
            break;
        case R.id.F8:
            showDialog(DEL_ACT);
            break;
        case R.id.F9:
            openPrefs();
            //openOptionsMenu();
            break;
        case R.id.F10:
            finish();
            break;
        case R.id.eq:
            panels.makeOtherAsCurrent();
            break;
        case R.id.tgl:
            panels.togglePanels(true);
            break;
        case R.id.sz:
            panels.showSizes();
            break;
        }
    }

    private final boolean setMode( boolean side_by_side_mode ) {
        if( ( side_by_side_mode && panels.getId() == R.layout.main ) || 
           ( !side_by_side_mode && panels.getId() == R.layout.alt ) ) {
            toggleMode();
            return true;
        }
        return false;
    }

    private final void toggleMode() {
        if( panelsBak == null ) {
            panelsBak = panels;
            panels = new Panels(this, panels.getId() == R.layout.alt ? R.layout.main : R.layout.alt);
        } else {
            Panels tmp = panels;
            panels = panelsBak;
            panelsBak = tmp;
            setContentView(panels.mainView);
        }
        panels.setState(panelsBak.getState());
    }

    private final void openPrefs() {
        Intent launchPreferencesIntent = new Intent().setClass(this, Prefs.class);
        startActivityForResult(launchPreferencesIntent, REQUEST_CODE_PREFERENCES);
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
            String mime = Utils.getMimeByExt( Utils.getFileExt( path ) );
            i.setDataAndType( Uri.fromFile( new File( path ) ), mime );
            startActivity(i);
        } catch( ActivityNotFoundException e ) {
            showMessage("Application for open '" + path + "' is not available, ");
        }
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void notifyMe( Notify progress ) {
        if( progress.status == Commander.OPERATION_STARTED ) {
            setProgressBarIndeterminateVisibility( true );
            return;
        }
        boolean dialog_enabled = false;
        Dialogs dh = getDialogsInstance(Dialogs.PROGRESS_DIALOG);
        if( dh != null ) {
            Dialog d = dh.getDialog();
            if( d != null && d.isShowing() ) {
                dialog_enabled = true;
                if( progress.status < 0 )
                    d.cancel();
                else
                    dh.setMessage( progress.string, progress.status, progress.substat );
            }
        }
        if( progress.status >= 0 ) {
            if( !dialog_enabled && progress.substat < 0 )
                showMessage( progress.string );
            return;
        }
        setProgressBarIndeterminateVisibility( false );
        switch( progress.status ) {
        case OPERATION_FAILED:
            if( progress.cookie != null && progress.cookie.length() > 0 ) {
                int which_panel = progress.cookie.charAt( 0 ) == '1' ? 1 : 0;
                panels.setPanelTitle( getString( R.string.fail ), which_panel );
            }
            showError("Failed" + ( progress.string != null && progress.string.length() > 0 ? ":\n" + progress.string : "." ));
            return;
        case OPERATION_FAILED_LOGIN_REQUIRED: 
            if( progress.string != null ) {
                dh = obtainDialogsInstance(Dialogs.LOGIN_DIALOG);
                dh.setMessageToBeShown( null, progress.string );
                showDialog( Dialogs.LOGIN_DIALOG );
            }
            return;
        case OPERATION_COMPLETED_REFRESH_REQUIRED:
            panels.refreshLists();
            break;
        case OPERATION_COMPLETED:
            if( progress.cookie != null && progress.cookie.length() > 0 ) {
                int which_panel = progress.cookie.charAt( 0 ) == '1' ? 1 : 0;
                String item_name = progress.cookie.substring( 1 );
                panels.recoverAfterRefresh( item_name, which_panel );
            }
            else
                panels.recoverAfterRefresh( null, -1 );
            break;
        }
        if( progress.string != null && progress.string.length() > 0 )
            showInfo( progress.string );
    }

    @Override
    public int askUser( String errMsg ) {
        // showDialog( ARI_DIALOG ); // TODO
        showMessage(errMsg);
        return Commander.ABORT;
    }

    @Override
    public void showError( String errMsg ) {
        Dialogs dh = obtainDialogsInstance(Dialogs.ALERT_DIALOG);
        dh.setMessageToBeShown(errMsg, null);
        showDialog( Dialogs.ALERT_DIALOG );
    }

    @Override
    public void showInfo( String msg ) {
        if( msg.length() < 50 )
            showMessage( msg );
        else {
            Dialogs dh = obtainDialogsInstance(Dialogs.INFO_DIALOG);
            dh.setMessageToBeShown(msg, null);
            showDialog( Dialogs.INFO_DIALOG );
        }
    }

    final void changeLanguage( String lang_ ) {
        if( !lang.equalsIgnoreCase( lang_ ) ) {
            Log.i( TAG, "Changing lang to " + lang_ );
            lang = lang_;
            Locale locale;
            String country = lang.length() > 3 ? lang.substring( 3 ) : null;
            if( country != null ) {
                Log.i( TAG, "Changing country to " + country );
                locale = new Locale( lang.substring( 0, 2 ), country );
            }
            else
                locale = new Locale( lang );
            Locale.setDefault( locale );
            Log.i( TAG, "Now the locale is " + Locale.getDefault().toString() );
            Configuration config = new Configuration();
            config.locale = locale;
            getResources().updateConfiguration( config, null );
        }
    }
    final void startViewURIActivity( int res_id ) {
        Intent i = new Intent( Intent.ACTION_VIEW );
        i.setData( Uri.parse( getString( res_id ) ) );
        startActivity( i );
    }
}
