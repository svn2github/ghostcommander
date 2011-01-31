package com.ghostsq.commander;

import org.apache.http.auth.UsernamePasswordCredentials;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Dialogs implements DialogInterface.OnClickListener {
    private final static String TAG = "Dialogs";
    public final static int ARI_DIALOG = 148, ALERT_DIALOG = 193, CONFIRM_DIALOG = 396, INPUT_DIALOG = 860, PROGRESS_DIALOG = 493,
            INFO_DIALOG = 864, ABOUT_DIALOG = 159, LOGIN_DIALOG = 995, SELECT_DIALOG = 239, UNSELECT_DIALOG = 762;
    
    public final static int numDialogTypes = 5;
    protected String toShowInAlertDialog = null, cookie = null;
    private int dialogId;
    private Dialog dialogObj;
    private FileCommander owner;

    Dialogs( FileCommander owner_, int id ) {
        owner = owner_;
        dialogId = id;
        dialogObj = null;
    }

    public int getId() {
        return dialogId;
    }

    public Dialog getDialog() {
        return dialogObj;
    }
    protected Dialog createDialog( int id ) {
        switch( id ) {
        case SELECT_DIALOG:
        case UNSELECT_DIALOG:
        case INPUT_DIALOG:
        case R.id.F2:
        case R.id.new_file:
        case R.id.new_zip:
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
        case LOGIN_DIALOG: {
            LayoutInflater factory = LayoutInflater.from( owner );
            final View textEntryView = factory.inflate( R.layout.login, null );
            return dialogObj = new AlertDialog.Builder( owner ).setView( textEntryView ).setTitle( "Login" )
                    .setPositiveButton( R.string.dialog_ok, this ).setNegativeButton( R.string.dialog_cancel, this ).create();
        }
        case ARI_DIALOG: {
            return dialogObj = new AlertDialog.Builder( owner ).setIcon( android.R.drawable.ic_dialog_alert )
                    .setTitle( R.string.error ).setMessage( R.string.error ).setPositiveButton( R.string.dialog_abort, this )
                    .setNeutralButton( R.string.dialog_retry, this ).setNegativeButton( R.string.dialog_ignore, this ).create();
        }
        case CONFIRM_DIALOG:
        case R.id.F8:
        case R.id.donate:
        case R.id.smb:
        case FileCommander.DBOX_APP:
        {
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
                .setPositiveButton( R.string.dialog_ok, null )
                .create();
        }
        case ABOUT_DIALOG:
        case INFO_DIALOG: {
            return dialogObj = new AlertDialog.Builder( owner )
                .setIcon( android.R.drawable.ic_dialog_info )
                .setTitle( R.string.info )
                .setMessage( toShowInAlertDialog == null ? "" : toShowInAlertDialog )
                .setPositiveButton( R.string.dialog_ok, null )
                .create();
        }
        }
        return null;
    }

    protected void prepareDialog( int id, Dialog dialog ) {
        if( dialog != dialogObj ) {
            owner.showMessage( "Dialogs corrupted!" );
            return;
        }
        boolean move = false;
        try {
            switch( id ) {
            case PROGRESS_DIALOG: {
            TextView t = (TextView)dialogObj.findViewById( R.id.text );
            if( t != null )
                t.setText( "" );
            }            
            case R.id.F2: {
                dialog.setTitle( R.string.rename_title );
                TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
                if( prompt != null )
                    prompt.setText( R.string.rename_prompt );
                String item_name = owner.panels.getSelectedItemName();
                if( item_name == null ) {
                    owner.showMessage( owner.getString( R.string.rename_err ) );
                    item_name = "";
                }
                EditText edit = (EditText)dialog.findViewById( R.id.edit_field );
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 80 );
                    edit.setText( item_name );
                }
                break;
            }
            case R.id.new_file: {
                dialog.setTitle( R.string.edit_title );
                TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
                if( prompt != null )
                    prompt.setText( R.string.newf_prompt );
                EditText edit = (EditText)dialog.findViewById( R.id.edit_field );
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 80 );
                    edit.setText( "" );
                }
                break;
            }
            case R.id.F6:
                move = true;
            case R.id.F5: {
                final String op = owner.getString( move ? R.string.move_title : R.string.copy_title );
                dialog.setTitle( op );
                TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
                if( prompt != null ) {
                    String summ = owner.panels.getActiveItemsSummary();
                    if( summ == null ) {
                        dialog.dismiss();
                        summ = owner.getString( R.string.no_items );
                        owner.showMessage( owner.getString( R.string.copy_move_not_alwd, op ) );
                    }
                    prompt.setText( owner.getString( R.string.copy_move_to, op, summ ) );
                }
                String copy_to = owner.panels.getFolderUri( false );
                if( copy_to != null ) {
                    EditText edit = (EditText)dialog.findViewById( R.id.edit_field );
                    if( edit != null ) {
                        edit.setWidth( owner.getWidth() - 70 );
                        edit.setText( copy_to );
                    }
                }
                break;
            }
            case R.id.F7: {
                dialog.setTitle( R.string.mkdir_title );
                TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
                if( prompt != null )
                    prompt.setText( R.string.mkdir_prompt );
                EditText edit = (EditText)dialog.findViewById( R.id.edit_field );
                if( edit != null )
                    edit.setWidth( owner.getWidth() - 90 );
                break;
            }
            case FileCommander.FIND_ACT: {
                dialog.setTitle( R.string.search_title );
                TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
                if( prompt != null )
                    prompt.setText( R.string.search_prompt );
                break;
            }
            case UNSELECT_DIALOG:
            case SELECT_DIALOG: {
                dialog.setTitle( id == SELECT_DIALOG ? R.string.dialog_select : R.string.dialog_unselect );
                TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
                if( prompt != null )
                    prompt.setText( "" );
                EditText edit = (EditText)dialog.findViewById( R.id.edit_field );
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
            case R.id.F8: {
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
            case R.id.smb:
                ( (AlertDialog)dialog ).setMessage( owner.getString( R.string.smb_missed ) );
                break;
            case FileCommander.DBOX_APP:
                ( (AlertDialog)dialog ).setMessage( owner.getString( R.string.dbox_missed ) );
                break;
                
            case ABOUT_DIALOG:
                try {
                    AlertDialog ad = (AlertDialog)dialog;
                    PackageInfo pi = owner.getPackageManager().getPackageInfo( owner.getPackageName(), 0);
                    ad.setMessage( owner.getString(R.string.about_text, pi.versionName) );
                } catch( NameNotFoundException e ) {
                    Log.e( TAG, "Package name not found", e);
                }
                break;
            case INFO_DIALOG:
            case ALERT_DIALOG: {
                if( toShowInAlertDialog != null ) {
                    AlertDialog ad = (AlertDialog)dialog;
                    ad.setMessage( toShowInAlertDialog );
                    toShowInAlertDialog = null;
                }
            }
            }
        } catch( ClassCastException e ) {
            owner.showMessage( "ClassCastException: " + e );
        }
    }

    public void setMessage( String string, int progress, int progressSec ) {
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
            if( progress >= 0 ) {
                p_bar.setProgress( progress );
                if( perc_t != null )
                    perc_t.setText( "" + progress + "%" );
            }
            if( progressSec >= 0 )
                p_bar.setSecondaryProgress( progressSec );
        } catch( ClassCastException e ) {
            owner.showMessage( "ClassCastException: " + e );
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
            if( whichButton == DialogInterface.BUTTON_POSITIVE ) {
                switch( dialogId ) {
                case R.id.F2:
                case R.id.new_file:
                case R.id.F5:
                case R.id.F6:
                case R.id.F7:
                case FileCommander.FIND_ACT:
                case UNSELECT_DIALOG:
                case SELECT_DIALOG:
                    EditText edit = (EditText)dialogObj.findViewById( R.id.edit_field );
                    if( edit != null ) {
                        String file_name = edit.getText().toString();
                        switch( dialogId ) {
                        case R.id.F2:
                            owner.panels.renameFile( file_name );
                            break;
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
                        case FileCommander.FIND_ACT: {
                                Uri.Builder uri_b = new Uri.Builder()
                                    .scheme( "find" )
                                    .path( cookie )
                                    .encodedQuery( "q=" + file_name );
                                owner.Navigate( uri_b.build(), null );
                            }
                            break;
                        case UNSELECT_DIALOG:
                        case SELECT_DIALOG:
                            owner.panels.checkItems( dialogId == SELECT_DIALOG, file_name );
                            break;
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
                case R.id.donate: {
                        owner.startViewURIActivity( R.string.donate_uri );
                        break;
                    }
                case R.id.smb: {
                        owner.startViewURIActivity( R.string.smb_app_uri );
                        break;
                    }
/*
                case FileCommander.DBOX_APP: {
                        owner.startViewURIActivity( R.string.dbox_app_uri );
                        break;
                    }
*/
                }
            } else if( whichButton == DialogInterface.BUTTON_NEGATIVE ) {
                if( dialogId == PROGRESS_DIALOG ) {
                    owner.panels.terminateOperation();
                }
            }
            owner.panels.focus();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
}
