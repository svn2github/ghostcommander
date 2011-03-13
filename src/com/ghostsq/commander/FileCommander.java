package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import dalvik.system.DexClassLoader;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
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
import android.util.Log;
import android.widget.AdapterView;
import android.widget.Toast;

public class FileCommander extends Activity implements Commander, View.OnClickListener {
    private final static String TAG = "GhostCommanderActivity";
    public  final static int REQUEST_CODE_PREFERENCES = 1, REQUEST_CODE_SRV_FORM = 2;
    public final static int FIND_ACT = 1017, DBOX_APP = 3592;
    
    private ArrayList<Dialogs> dialogs;
    public  Panels  panels;
    private boolean on = false, exit = false, dont_restore = false, sxs_auto = true;
    private String lang = ""; // just need to issue a warning on change

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
        
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        requestWindowFeature( Window.FEATURE_INDETERMINATE_PROGRESS );
        dialogs = new ArrayList<Dialogs>(Dialogs.numDialogTypes);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        lang = sharedPref.getString( "language", "" );
        Utils.changeLanguage( this, getResources() );
        String panels_mode = sharedPref.getString( "panels_sxs_mode", "a" );
        sxs_auto = panels_mode.equals( "a" );
        boolean sxs = sxs_auto ? getRotMode() : panels_mode.equals( "y" );
        panels = new Panels(this, sharedPref.getBoolean( "panels_mode", sxs ) );
    }

    @Override
    protected void onStart() {
        Log.i( TAG, "Starting\n");
        super.onStart();
        on = true;
        if( dont_restore )
            dont_restore = false;
        else {
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            Panels.State s = panels.new State();
            s.restore(prefs);
            panels.setState(s);
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
        Log.i( TAG, "Pausing\n");
        super.onPause();
        on = false;
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        Panels.State s = panels.getState();
        s.store(editor);
        editor.commit();
    }

    @Override
    protected void onResume() {
        //Log.i( TAG, "Resuming\n");
        super.onResume();
        on = true;
    }
    
    @Override
    protected void onStop() {
        //Log.i( TAG, "Stopping\n");
        super.onStop();
        on = false;
    }

    @Override
    protected void onDestroy() {
        //Log.i( TAG, "Destroying\n");
        on = false;
        panels.Destroying();
        super.onDestroy();
        if( isFinishing() && exit )
            System.exit( 0 );
    }

    @Override
    public void onConfigurationChanged( Configuration newConfig ) {
        super.onConfigurationChanged( newConfig );
        if( sxs_auto ) {
            if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                panels.setMode( true );
            } else if( newConfig.orientation == Configuration.ORIENTATION_PORTRAIT ){
                panels.setMode( false );
            }
        }
/*// TODO: hide the numbers from the virtual buttons if there is no physical keyboard
        // Checks whether a hardware keyboard is available
        if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            Toast.makeText(this, "keyboard visible", Toast.LENGTH_SHORT).show();
        } else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            Toast.makeText(this, "keyboard hidden", Toast.LENGTH_SHORT).show();
        }
*/
    }    
    
    //these two methods are not called on screen rotation in v1.5, so all the store/restore is called from pause/start 
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        Log.i( TAG, "Saving Instance State");
        Panels.State s = panels.getState();
        s.store(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        Log.i( TAG, "Restoring Instance State");
        if( savedInstanceState != null ) {
            Panels.State s = panels.new State();
            s.restore(savedInstanceState);
            panels.setState(s);
        }
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onCreateContextMenu( ContextMenu menu, View v, ContextMenuInfo menuInfo ) {
        try {
            int num = panels.getNumItemsChecked();
            AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo)menuInfo;
            menu.setHeaderTitle( getString( R.string.operation ) );
            CommanderAdapter ca = panels.getListAdapter( true );
            ca.populateContextMenu( menu, acmi, num );
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
                doIt( item_id );
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
        inflater.inflate( R.menu.menu, menu );
        return true;
    }

    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        panels.resetQuickSearch();
        boolean processed = super.onMenuItemSelected( featureId, item );
        if( !processed ) 
            doIt( item.getItemId() );
        return true; 
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
        super.onActivityResult(requestCode, resultCode, data);
        if( requestCode == REQUEST_CODE_PREFERENCES ) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String lang_ = sharedPref.getString( "language", "" );
            if( !lang.equalsIgnoreCase( lang_ ) ) {
                lang = lang_;
                Utils.changeLanguage( this, getResources() );
                showMessage( getString( R.string.restart_to_apply_lang ) );
                exit = true;
            }
            panels.applySettings( sharedPref );
            String panels_mode = sharedPref.getString( "panels_sxs_mode", "a" );
            sxs_auto = panels_mode.equals( "a" );
            boolean sxs = sxs_auto ? getRotMode() : panels_mode.equals( "y" );
            panels.setMode( sxs );
            panels.showToolbar( sharedPref.getBoolean("show_toolbar", true ) );
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
        doIt( button.getId() );
    }

    public final void doIt( int id ) {
        Utils.changeLanguage( this, getResources() );
        switch( id ) {
        case R.id.keys:
        case R.id.F1:
            showInfo( getString( R.string.keys_text ) );
            break;
        case R.id.F4:
            panels.openForEdit(null);
            break;
        case R.id.F2:
        case R.id.new_file:
        case R.id.new_zip:
        case R.id.SF4:
        case R.id.F5:
        case R.id.F6:
        case R.id.F7:
        case R.id.F8:
        case R.id.about:
        case R.id.donate:
            showDialog( id );
            break;
        case R.id.prefs:
        case R.id.F9:
            openPrefs();
            break;
        case R.id.exit:
            exit = true;
        case R.id.F10:
            finish();
            break;
        case R.id.oth_sh_this:
        case R.id.eq:
            panels.makeOtherAsCurrent();
            break;
        case R.id.tgl:
            panels.togglePanels(true);
            break;
        case R.id.sz:
            panels.showSizes();
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
            showDialog( Dialogs.SELECT_DIALOG );
            break;
        case R.id.unselect_all:
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
        default:
            CommanderAdapter ca = panels.getListAdapter( true );
            ca.doIt( id, panels.getSelectedOrChecked() );
        }
    }
    private final void openPrefs() {
        Intent launchPreferencesIntent = new Intent().setClass( this, Prefs.class );
        startActivityForResult( launchPreferencesIntent, REQUEST_CODE_PREFERENCES );
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
        Dialogs dh = getDialogsInstance( Dialogs.PROGRESS_DIALOG );
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
        panels.operationFinished();
        switch( progress.status ) {
        case OPERATION_FAILED:
            if( progress.cookie != null && progress.cookie.length() > 0 ) {
                int which_panel = progress.cookie.charAt( 0 ) == '1' ? 1 : 0;
                panels.setPanelTitle( getString( R.string.fail ), which_panel );
            }
            if( progress.string != null && progress.string.length() > 0 )
                showError( getString( R.string.failed ) + progress.string );
            return;
        case OPERATION_FAILED_LOGIN_REQUIRED: 
            if( progress.string != null ) {
                dh = obtainDialogsInstance( Dialogs.LOGIN_DIALOG );
                dh.setMessageToBeShown( null, progress.string );
                showDialog( Dialogs.LOGIN_DIALOG );
            }
            return;
        case OPERATION_COMPLETED_REFRESH_REQUIRED:
            panels.refreshLists();
            break;
        case OPERATION_COMPLETED:
            if( progress.cookie != null && progress.cookie.length() > 0 ) {
                Log.i( TAG, "notify with cookie: " + progress.cookie );
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
        if( !on ) return;
        Dialogs dh = obtainDialogsInstance(Dialogs.ALERT_DIALOG);
        dh.setMessageToBeShown(errMsg, null);
        showDialog( Dialogs.ALERT_DIALOG );
    }

    @Override
    public void showInfo( String msg ) {
        if( !on ) return;
        if( msg.length() < 20 )
            showMessage( msg );
        else {
            Dialogs dh = obtainDialogsInstance(Dialogs.INFO_DIALOG);
            dh.setMessageToBeShown(msg, null);
            showDialog( Dialogs.INFO_DIALOG );
        }
    }

    @Override
    public CommanderAdapter CreateExternalAdapter( String type, String class_name, int dialog_id ) {
        try {
            File dex_f = getDir( type, Context.MODE_PRIVATE );
            if( dex_f == null || !dex_f.exists() ) {
                Log.w( TAG, "app.data storage is not accessable, trying to use the SD card" );
                File sd = Environment.getExternalStorageDirectory();
                if( sd == null ) return null; // nowhere to store the dex :(
                dex_f = new File( sd, "temp" );
                if( !dex_f.exists() )
                    dex_f.mkdir();
            }
            ApplicationInfo ai = getPackageManager().getApplicationInfo( "com.ghostsq.commander." + type, 0 );
            
            Log.i( TAG, type + " package is " + ai.sourceDir );
            
            ClassLoader pcl = getClass().getClassLoader();
            DexClassLoader cl = new DexClassLoader( ai.sourceDir, dex_f.getAbsolutePath(), null, pcl );
            //
            Class<?> adapterClass = cl.loadClass( "com.ghostsq.commander." + type + "." + class_name );
            try {
                File[] list = dex_f.listFiles();
                for( int i = 0; i < list.length; i++ )
                    list[i].delete();
            }
            catch( Exception e ) {
                Log.w( TAG, "Can't remove the plugin's .dex: ", e );
            }
            if( adapterClass == null )
                showError( "Can not load the adapter class of " + type );
            else {
                CommanderAdapter ca = (CommanderAdapter)adapterClass.newInstance();
                ca.Init( this );
                return ca;
            }
        }
        catch( Exception e ) {
            showDialog( dialog_id );
            Log.e( TAG, "Load class failed: ", e );
        }
        catch( Error e ) {
            showError( "Can not load the " + type + " class - an Error was thrown: " + e + "\nPlease report to the application develeoper." );
            Log.e( TAG, "Loading " + type + " class failed: ", e );
        }
        return null;
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
}
