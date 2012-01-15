package com.ghostsq.commander;

import org.apache.http.auth.UsernamePasswordCredentials;

import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.utils.Utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Dialogs implements DialogInterface.OnClickListener {
    private final static String TAG = "Dialogs";
    public final static int ARI_DIALOG = 148, ALERT_DIALOG = 193, CONFIRM_DIALOG = 396, INPUT_DIALOG = 860, PROGRESS_DIALOG = 493,
            INFO_DIALOG = 864, LOGIN_DIALOG = 995, SELECT_DIALOG = 239, UNSELECT_DIALOG = 762,
            FILE_EXIST_DIALOG = 328, SMB_PLG_DIALOG = 275;
    
    public final static int numDialogTypes = 5;
    protected String toShowInAlertDialog = null, cookie = null;
    private int dialogId;
    private Dialog dialogObj;
    private FileCommander owner;
    private boolean valid = true;

    Dialogs( FileCommander owner_, int id ) {
        owner = owner_;
        dialogId = id;
        dialogObj = null;
    }
    public final int getId() {
        return dialogId;
    }
    public final Dialog getDialog() {
        return dialogObj;
    }
    public final void showDialog() {
        owner.showDialog( dialogId );
    }
    protected final Dialog createDialog( int id ) {
        try {
            Utils.changeLanguage( owner );
            switch( id ) {
            case INPUT_DIALOG:
            case R.id.new_zip:
            case R.id.F2:
            case R.id.new_file:
            case R.id.SF4:
            case R.id.F5:
            case R.id.F6:
            case R.id.F7:
            case FileCommander.FIND_ACT: {
                LayoutInflater factory = LayoutInflater.from( owner );
                final View textEntryView = factory.inflate( R.layout.input, null );
                dialogObj = new AlertDialog.Builder( owner )
                    .setView( textEntryView )
                    .setTitle( " " )
                    .setPositiveButton( R.string.dialog_ok, this )
                    .setNegativeButton( R.string.dialog_cancel, this )
                    .create();
                if( dialogObj == null )
                    Log.e( TAG, "Can't create dialog " + id );
                return dialogObj; 
            }
            case SELECT_DIALOG:
            case UNSELECT_DIALOG: {
                LayoutInflater factory = LayoutInflater.from( owner );
                final View textEntryView = factory.inflate( R.layout.search, null );
                dialogObj = new AlertDialog.Builder( owner )
                    .setView( textEntryView )
                    .setTitle( " " )
                    .setPositiveButton( R.string.dialog_ok, this )
                    .setNegativeButton( R.string.dialog_cancel, this )
                    .create();
                if( dialogObj == null )
                    Log.e( TAG, "Can't create dialog " + id );
                return dialogObj; 
            }

            case LOGIN_DIALOG: {
                    LayoutInflater factory = LayoutInflater.from( owner );
                    final View textEntryView = factory.inflate( R.layout.login, null );
                    return dialogObj = new AlertDialog.Builder( owner )
                            .setView( textEntryView )
                            .setTitle( "Login" )
                            .setPositiveButton( R.string.dialog_ok, this )
                            .setNegativeButton( R.string.dialog_cancel, this )
                            .create();
                }
            /*
            case ARI_DIALOG: {
                return dialogObj = new AlertDialog.Builder( owner ).setIcon( android.R.drawable.ic_dialog_alert )
                        .setTitle( R.string.error )
                        .setMessage( R.string.error )
                        .setPositiveButton( R.string.dialog_abort, this )
                        .setNeutralButton( R.string.dialog_retry, this )
                        .setNegativeButton( R.string.dialog_ignore, this )
                        .create();
            }
            */
            case FILE_EXIST_DIALOG: {
                    return dialogObj = new AlertDialog.Builder( owner )
                            .setIcon( android.R.drawable.ic_dialog_alert )
                            .setTitle( R.string.error )
                            .setMessage( R.string.error )
                            .setPositiveButton( R.string.dialog_replace_all, this )
                            .setNeutralButton( R.string.dialog_skip_all, this )
                            .setNegativeButton( R.string.dialog_cancel, this )
                            .create();
                }
            case CONFIRM_DIALOG:
            case R.id.F8:
            case R.id.donate:
            case SMB_PLG_DIALOG:
            case FileCommander.DBOX_APP: {
                    return dialogObj = new AlertDialog.Builder( owner )
                        .setIcon( android.R.drawable.ic_dialog_alert )
                        .setTitle( R.string.confirm )
                        .setMessage( "" )
                        .setPositiveButton( R.string.dialog_ok, this )
                        .setNegativeButton( R.string.dialog_cancel, this )
                        .create();
                }
            case PROGRESS_DIALOG: {
                    LayoutInflater factory = LayoutInflater.from( owner );
                    final View progressView = factory.inflate( R.layout.progress, null );
                    return dialogObj = new AlertDialog.Builder( owner )
                        .setView( progressView )
                        .setTitle( R.string.progress )
                        .setNegativeButton( R.string.dialog_cancel, this )
                        .setCancelable( false )
                        .create();
                }
            case ALERT_DIALOG: {
                    return dialogObj = new AlertDialog.Builder( owner )
                        .setIcon( android.R.drawable.ic_dialog_alert )
                        .setTitle( R.string.alert )
                        .setMessage( "" )
                        .setPositiveButton( R.string.dialog_ok, this )
                        .create();
                }
            case R.id.about:
            case INFO_DIALOG: {
                    AlertDialog.Builder adb = new AlertDialog.Builder( owner )
                        .setIcon( android.R.drawable.ic_dialog_info )
                        .setTitle( R.string.info )
                        .setPositiveButton( R.string.dialog_ok, this );
                    LayoutInflater factory = LayoutInflater.from( owner );
                    View tvs = factory.inflate( R.layout.textvw, null );
                    if( tvs != null ) {
                        //TextView tv = (TextView)tvs.findViewById( R.id.text_view );                     
                        //tv.setPadding( 10, 10, 10, 10 );
                        adb.setView( tvs );
                    } else
                        adb.setMessage( "" );
                    return dialogObj = adb.create();
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    protected void prepareDialog( int id, Dialog dialog ) {
        if( dialog != dialogObj ) {
            Log.e( TAG, "Dialogs corrupted!" );
            return;
        }
        Utils.changeLanguage( owner );
        boolean move = false;
        try {
            TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
            EditText edit   = (EditText)dialog.findViewById( R.id.edit_field );
            switch( id ) {
            case PROGRESS_DIALOG: {
                TextView t = (TextView)dialogObj.findViewById( R.id.text );
                if( t != null )
                    t.setText( "" );
                break;
            }            
            case R.id.F2: 
            {
                final String op_title = owner.getString( R.string.rename_title );
                String op = owner.getString( R.string.to_rename );
                if( op == null || op.length() == 0 )
                    op = op_title;
                dialog.setTitle( op_title );
                String item_name = owner.panels.getSelectedItemName();
                if( item_name == null ) {
                    owner.showMessage( owner.getString( R.string.rename_err ) );
                    item_name = "";
                }
                if( prompt != null )
                    prompt.setText( owner.getString( R.string.oper_item_to, op, item_name ) );
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 80 );
                    edit.setText( item_name );
                }
                break;
            }
            case R.id.SF4:
            case R.id.new_file: {
                dialog.setTitle( R.string.edit_title );
                if( prompt != null )
                    prompt.setText( R.string.newf_prompt );
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 80 );
                    edit.setText( "" );
                }
                break;
            }
            case R.id.F6:
                move = true;
            case R.id.F5: 
            {
                final String op_title = owner.getString( move ? R.string.move_title : R.string.copy_title );
                String op = owner.getString( move ? R.string.to_move : R.string.to_copy );
                if( op == null || op.length() == 0 )
                    op = op_title;
                dialog.setTitle( op_title );
                if( prompt != null ) {
                    String summ = owner.panels.getActiveItemsSummary();
                    if( summ == null ) {
                        dialog.cancel();
                        owner.showMessage( owner.getString( R.string.op_not_alwd, op ) );
                        valid = false;
                        return;
                    }
                    else
                        valid = true;
                    prompt.setText( owner.getString( R.string.oper_item_to, op, summ ) );
                }
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 70 );
                    String cts = Favorite.screenPwd( owner.panels.getFolderUri( false ) );
                    edit.setText( cts != null ? cts : "" );
                    if( owner.panels.getNumItemsSelectedOrChecked() == 1 )
                        edit.selectAll();
                }
                break;
            }
            case R.id.new_zip: {
                final String op = owner.getString( R.string.create_zip_title );
                dialog.setTitle( op );
                if( prompt != null ) {
                    String summ = owner.panels.getActiveItemsSummary();
                    if( summ == null ) {
                        dialog.dismiss();
                        summ = owner.getString( R.string.no_items );
                        owner.showMessage( owner.getString( R.string.op_not_alwd, op ) );
                    }
                    prompt.setText( owner.getString( R.string.oper_item_to, op, summ ) );
                }
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 70 );
                    edit.setText( " .zip" );
                    edit.setSelection( 1 );
                }
                break;
            }
            case R.id.F7: 
            {
                dialog.setTitle( R.string.mkdir_title );
                if( prompt != null )
                    prompt.setText( R.string.mkdir_prompt );
                if( edit != null )
                    edit.setWidth( owner.getWidth() - 90 );
                break;
            }
            case FileCommander.FIND_ACT: {
                dialog.setTitle( R.string.search_title );
                if( prompt != null )
                    prompt.setText( R.string.search_prompt );
                break;
            }
            case UNSELECT_DIALOG:
            case SELECT_DIALOG: {
                dialog.setTitle( id == SELECT_DIALOG ? R.string.dialog_select : R.string.dialog_unselect );
                if( edit != null ) {
                    Editable edit_text = edit.getText();
                    if( edit_text.length() == 0 )
                        edit.setText( "*" );
                }
                break;
            }
            case LOGIN_DIALOG: {
                String host_name = "";
                if( cookie != null ) {
                    Uri uri = Uri.parse( cookie );
                    if( uri != null ) {
                        host_name = " - " + uri.getHost();
                        String user_info = uri.getUserInfo();
                        UsernamePasswordCredentials crd = new UsernamePasswordCredentials( user_info == null ? "" : user_info );
                        EditText n_v = (EditText)dialog.findViewById( R.id.username_edit );
                        EditText p_v = (EditText)dialog.findViewById( R.id.password_edit );
                        if( n_v != null )
                            n_v.setText( crd.getUserName() != null ? crd.getUserName() : "" );
                        if( p_v != null )
                            p_v.setText( crd.getPassword() != null ? crd.getPassword() : "" );
                    }
                }
                AlertDialog ad = (AlertDialog)dialog;
                ad.setTitle( owner.getString( R.string.login_title ) + host_name );
                break;
            }
            case R.id.F8: 
            {
                AlertDialog ad = (AlertDialog)dialog;
                ad.setTitle( R.string.delete_title );
                String str, summ = owner.panels.getActiveItemsSummary();
                if( summ == null ) {
                    str = owner.getString( R.string.no_items );
                    dialog.cancel();
                } else
                    str = owner.getString( R.string.delete_q, summ );
                ad.setMessage( str );
                break;
            }
            case R.id.donate:
                ( (AlertDialog)dialog ).setMessage( owner.getString( R.string.donation ) );
                break;
            case SMB_PLG_DIALOG:
                ( (AlertDialog)dialog ).setMessage( owner.getString( R.string.smb_missed ) );
                break;
            case FileCommander.DBOX_APP:
                ( (AlertDialog)dialog ).setMessage( owner.getString( R.string.dbox_missed ) );
                break;
                
            case R.id.about:
                PackageInfo pi = null;
                try {
                    pi = owner.getPackageManager().getPackageInfo( owner.getPackageName(), 0 );
                } catch( NameNotFoundException e ) {
                    Log.e( TAG, "Package name not found", e );
                }
                setMessageToBeShown( owner.getString(R.string.about_text, pi != null ? pi.versionName : "?" ), null );
            case INFO_DIALOG:
                if( toShowInAlertDialog != null ) {
                    TextView tv = (TextView )dialog.findViewById( R.id.text_view );
                    if( tv != null ) {
                        if( id == R.id.about ) tv.setAutoLinkMask( Linkify.EMAIL_ADDRESSES );
                        
                        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( owner );
                        int fnt_sz = Integer.parseInt( shared_pref != null ? shared_pref.getString( "font_size", "12" ) : "12" );
                        boolean reduce_size = toShowInAlertDialog.length() > 128;
                        if( !reduce_size ) {
                            String[] ss = toShowInAlertDialog.split( "\n" );
                            for( String s : ss ) {
                                if( s.length() > 31 ) {
                                    reduce_size = true;
                                    break;
                                }
                            }
                        }
                        int fs = ( reduce_size ? 14 : 18 ) + ( fnt_sz - 12 );
                        tv.setTextSize( fs > 12 ? fs : 12 );
                        tv.setText( toShowInAlertDialog );
                    } else 
                        ( (AlertDialog)dialog ).setMessage( toShowInAlertDialog ); 
                    toShowInAlertDialog = null;
                }
                break;
            case FILE_EXIST_DIALOG:
            case ALERT_DIALOG:
                if( toShowInAlertDialog != null ) {
                    AlertDialog ad = (AlertDialog)dialog;
                    ad.setMessage( toShowInAlertDialog );
                    toShowInAlertDialog = null;
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    public void setProgress( String string, int progress, int progressSec ) {
        if( dialogObj == null )
            return;
        try {
            if( string != null ) {
                TextView t = (TextView)dialogObj.findViewById( R.id.text );
                if( t != null )
                    t.setText( string );
            }
            ProgressBar p_bar = (ProgressBar)dialogObj.findViewById( R.id.progress_bar );
            TextView perc_t = (TextView)dialogObj.findViewById( R.id.percent );

            if( progress >= 0 )
                p_bar.setProgress( progress );
            if( progressSec >= 0 )
                p_bar.setSecondaryProgress( progressSec );
            if( perc_t != null ) {
                perc_t.setText( "" + ( progressSec > 0 ? progressSec : progress ) + "%" );
            }
        } catch( ClassCastException e ) {
            Log.e( TAG, null, e );
        }
    }

    public void setMessageToBeShown( String string, String cookie_ ) {
        toShowInAlertDialog = string;
        cookie = cookie_;
    }
    public void setCookie( String cookie_ ) {
        cookie = cookie_;
    }
    @Override
    public void onClick( DialogInterface idialog, int whichButton ) {
        if( dialogObj == null )
            return;
        try {
            if( valid && whichButton == DialogInterface.BUTTON_POSITIVE ) {
                switch( dialogId ) {
                case R.id.F2:
                case R.id.new_file:
                case R.id.SF4:
                case R.id.F5:
                case R.id.F6:
                case R.id.F7:
                case FileCommander.FIND_ACT:
                case R.id.new_zip:
                case UNSELECT_DIALOG:
                case SELECT_DIALOG:
                    EditText edit = (EditText)dialogObj.findViewById( R.id.edit_field );
                    if( edit != null ) {
                        String file_name = edit.getText().toString();
                        if( file_name == null ) return;
                        switch( dialogId ) {
                        case R.id.F2:
                            owner.panels.renameItem( file_name );
                            break;
                        case R.id.SF4:
                        case R.id.new_file:
                            owner.panels.createNewFile( file_name );
                            break;
                        case R.id.F6:
                        case R.id.F5:
                            owner.panels.copyFiles( file_name, dialogId == R.id.F6 );
                            break;
                        case R.id.F7:
                            owner.panels.createFolder( file_name );
                            break;
                        case R.id.new_zip:
                            owner.panels.createZip( file_name.trim() );
                            break;
                        case FileCommander.FIND_ACT: {
                                Uri.Builder uri_b = new Uri.Builder()
                                    .scheme( "find" )
                                    .path( cookie )
                                    .encodedQuery( "q=" + file_name );
                                owner.Navigate( uri_b.build(), null );
                            }
                            break;
                        case UNSELECT_DIALOG:
                        case SELECT_DIALOG: {
                                CheckBox for_dirs  = (CheckBox)dialogObj.findViewById( R.id.for_dirs );
                                CheckBox for_files = (CheckBox)dialogObj.findViewById( R.id.for_files );
                                owner.panels.checkItems( dialogId == SELECT_DIALOG, file_name, for_dirs.isChecked(), for_files.isChecked() );
                                break;
                            }
                        }
                    }
                    break;
                case R.id.F8:
                    owner.panels.deleteItems();
                    break;
                case LOGIN_DIALOG: {
                        EditText name_edit = (EditText)dialogObj.findViewById( R.id.username_edit );
                        EditText pass_edit = (EditText)dialogObj.findViewById( R.id.password_edit );
                        if( name_edit != null && pass_edit != null )
                            owner.panels.login( cookie, name_edit.getText().toString(), pass_edit.getText().toString() );
                    }
                    break;
                case R.id.donate:
                        owner.startViewURIActivity( R.string.donate_uri );
                        break;
                case SMB_PLG_DIALOG:
                        owner.startViewURIActivity( R.string.smb_app_uri );
                        break;
                case FILE_EXIST_DIALOG:
                    owner.setResolution( Commander.REPLACE_ALL );
                    break;
/*
                case FileCommander.DBOX_APP: {
                        owner.startViewURIActivity( R.string.dbox_app_uri );
                        break;
                    }
*/
                }
            } else if( whichButton == DialogInterface.BUTTON_NEGATIVE ) {
                if( dialogId == PROGRESS_DIALOG )
                    owner.panels.terminateOperation();
                else
                if( dialogId == FILE_EXIST_DIALOG )
                    owner.setResolution( Commander.ABORT );
            } else if( whichButton == DialogInterface.BUTTON_NEUTRAL ) {
                if( dialogId == FILE_EXIST_DIALOG )
                    owner.setResolution( Commander.SKIP_ALL );
            }
            owner.panels.focus();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
}
