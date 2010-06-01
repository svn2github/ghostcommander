package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;

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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
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

/*
 *  TODO:
 *  -1. Create a new file
 *  -2. FTP copy
 *  -3. FTP delete
 *  4. FTP move = copy + delete
 *  5. FTP tree size
 *  -6. Progress bar
 *  7. Localize the copy report and FTP
 *  -8. Add and delete favorites by name, not by current
 *  -9. Select/unselect all files 
 *  -10. Ask password or do not show it
 *  11. Do active or passive ftp by the user's choice
 *  -12. Close the app completely on exit - does not work
 *  13. Repeat and make case independed quick search
 *  -14. Make the ftp adapter to remember item selected and pass its name on the way up
 *  -15. store, sort and show calculated folders sizes
 * 
 */

public class FileCommander extends Activity implements Commander, View.OnClickListener {
    private final static String TAG = "GhostCommanderActivity";
    private final static int REQUEST_CODE_PREFERENCES = 1, REQUEST_CODE_FTPFORM = 2;
    public  final static int RENAME_ACT = 1002,  NEWF_ACT = 1014, EDIT_ACT = 1004, COPY_ACT = 1005, 
                               MOVE_ACT = 1006, MKDIR_ACT = 1007,  DEL_ACT = 1008, FIND_ACT = 1017,  DONATE = 3333;
    private final static int SHOW_SIZE = 12, CHANGE_LOCATION = 993, MAKE_SAME = 217, SEND_TO = 236;
    private ArrayList<Dialogs> dialogs;
    public  Panels  panels, panelsBak = null;
    private boolean exit = false, dont_restore = false;

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
        panels = new Panels(this, sharedPref.getBoolean( "panels_mode", false ) ? R.layout.alt : R.layout.main);
    }

    @Override
    protected void onStart() {
        System.err.print("Ghost Commander Startinging\n");
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
        System.err.print("Ghost Commander Pausing\n");
        super.onPause();
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        Panels.State s = panels.getState();
        s.store(editor);
        editor.commit();
    }

    @Override
    protected void onStop() {
        System.err.print("Ghost Commander Stopping\n");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        System.err.print("Ghost Commander Destroying\n");
        panels.Destroying();
        super.onDestroy();
        if( isFinishing() && exit )
            System.exit( 0 );  // does not work in API7
    }
/*
    //these two methods are not called on screen rotation in v1.5, so all the store/restore is called from pause/start 
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        System.err.print("Ghost Commander Saving\n");
        Panels.State s = panels.getState();
        s.store(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        System.err.print("Ghost Commander Restoring\n");
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
                System.err.print( "onCreateContextMenu() cast exception\n" );
            }
        }
        menu.setHeaderTitle("Operation");
        boolean fs_adapter = panels.getListAdapter( true ) instanceof FSAdapter;
        if( fs_adapter ) { 
            menu.add( 0, SHOW_SIZE, 0, R.string.show_size );
            menu.add( 0,   SEND_TO, 0, R.string.send_to );
        }
        if( num <= 1 ) {
            menu.add( 0, RENAME_ACT, 0, R.string.rename_title );
            menu.add( 0,   EDIT_ACT, 0, R.string.edit_title );
        }
        menu.add( 0, COPY_ACT, 0, R.string.copy_title );
        if( fs_adapter )
            menu.add( 0, MOVE_ACT, 0, R.string.move_title );
        menu.add( 0,  DEL_ACT, 0, R.string.delete_title );
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
        panels.setSelection(info.position);
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
        case R.id.ftp:
            Intent i = new Intent( this, FTPform.class );
            startActivityForResult( i, REQUEST_CODE_FTPFORM );
            break;
        case R.id.search: 
            showSearchDialog();
            break;
        case R.id.enter:
            panels.openGoPanel();
            break;
        case R.id.by_name:
            panels.changeSorting( CommanderAdapter.SORT_NAME );
            break;
        case R.id.by_size:
            panels.changeSorting( CommanderAdapter.SORT_SIZE );
            break;
        case R.id.by_date:
            panels.changeSorting( CommanderAdapter.SORT_DATE );
            break;
        case R.id.refresh:
            panels.refreshList(true);
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
            panels.applySettings(sharedPref);
            if( panelsBak != null )
                panelsBak.applySettings(sharedPref);
            setMode(sharedPref.getBoolean("panels_mode", false));
            panels.showOrHideToolbar(sharedPref.getBoolean("show_toolbar", true));
        }
        else
        if( requestCode == REQUEST_CODE_FTPFORM ) {
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
        case KeyEvent.KEYCODE_ENVELOPE:
            panels.refreshList(true);
            break;
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
    public void notifyMe( String string, int progress, int progressSec ) {
        if( progress == Commander.OPERATION_STARTED ) {
            setProgressBarIndeterminateVisibility( true );
            return;
        }
        boolean dialog_enabled = false;
        Dialogs dh = getDialogsInstance(Dialogs.PROGRESS_DIALOG);
        if( dh != null ) {
            Dialog d = dh.getDialog();
            if( d != null && d.isShowing() ) {
                dialog_enabled = true;
                if( progress < 0 )
                    d.cancel();
                else
                    dh.setMessage(string, progress, progressSec);
            }
        }
        if( progress >= 0 ) {
            if( !dialog_enabled && progressSec < 0 )
                showMessage(string);
            return;
        }
        setProgressBarIndeterminateVisibility( false );
        switch( progress ) {
        case OPERATION_FAILED:
            showError("Failed" + ( string != null && string.length() > 0 ? ":\n" + string : "." ));
            break;
        case OPERATION_FAILED_LOGIN_REQUIRED: 
            if( string != null ) {
                dh = obtainDialogsInstance(Dialogs.LOGIN_DIALOG);
                dh.setMessageToBeShown( null, string );
                showDialog( Dialogs.LOGIN_DIALOG );
                break;
            }
        case OPERATION_COMPLETED_REFRESH_REQUIRED:
        case OPERATION_COMPLETED:
            panels.refreshList( progress == OPERATION_COMPLETED_REFRESH_REQUIRED );
            if( string != null && string.length() > 0 )
                showInfo( string );
            break;
        }
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
        Dialogs dh = obtainDialogsInstance(Dialogs.INFO_DIALOG);
        dh.setMessageToBeShown(msg, null);
        showDialog( Dialogs.INFO_DIALOG );
    }
}
