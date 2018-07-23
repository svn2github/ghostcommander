package com.ghostsq.commander;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapter.Feature;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

public class Dialogs implements DialogInterface.OnClickListener {
    private final static String TAG = "Dialogs";
    public final static int ARI_DIALOG = 148, ALERT_DIALOG = 193, CONFIRM_DIALOG = 396, INPUT_DIALOG = 860, PROGRESS_DIALOG = 493,
            INFO_DIALOG = 864, LOGIN_DIALOG = 995, SELECT_DIALOG = 239, UNSELECT_DIALOG = 762,
            FILE_EXIST_DIALOG = 328, SMB_PLG_DIALOG = 275, SFTP_PLG_DIALOG = 245;
    
    public final static int numDialogTypes = 7;
    protected String toShowInAlertDialog = null, cookie = null, activeFileName;
    private int dialogId; 
    private long  taskId = 0;
    public  Dialog dialogObj;
    private FileCommander owner;
    private boolean valid = true;
    private int  progressCounter = 0;
    private long progressAcSpeed = 0;
    private Credentials   crd = null;
    private int which_panel = -1;
    private boolean pw_only = false;

    Dialogs( FileCommander owner_, int id ) {
        owner = owner_;
        dialogObj = null;
        dialogId = id;
    }
    public final int getId() {
        return dialogId;
    }
    public final long getTaskId() {
        return taskId;
    }
    public final void setTaskId( long taskId_ ) {
        taskId = taskId_;
    }
    public final Dialog getDialog() {
        return dialogObj;
    }
    public final void showDialog() {
        if( dialogObj == null || !dialogObj.isShowing() )
            owner.showDialog( dialogId );
    }
    public final void cancelDialog() {
        if( dialogObj != null && dialogObj.isShowing() )
            dialogObj.cancel();
        progressCounter = 0;
        progressAcSpeed = 0;
        taskId = 0L;
    }
    private final AlertDialog build( View inner_view, String title ) {
        AlertDialog ad = new AlertDialog.Builder( owner )
            .setView( inner_view )
            .setTitle( title )
            .setPositiveButton( R.string.dialog_ok, this )
            .setNegativeButton( R.string.dialog_cancel, this )
            .create();
        ad.getWindow().setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN );
        return ad;
    }
    
    protected final Dialog createDialog( int id ) {
        try {
            Utils.changeLanguage( owner );
            LayoutInflater factory = LayoutInflater.from( owner );
            switch( id ) {
            case INPUT_DIALOG:
            case R.id.open_zip:
            {
                final View openArchiveView = factory.inflate( R.layout.open_archive, null );
                dialogObj = build( openArchiveView, " " );
                return dialogObj; 
            }
            case R.id.new_zip:
            case R.id.new_zipt:
            {
                final View newArchiveView = factory.inflate( R.layout.new_archive, null );
                dialogObj = build( newArchiveView, " " ); 
                return dialogObj; 
            }
            case R.id.F2:
            case R.id.F2t:
            case R.id.new_file:
            case R.id.SF4:
            case R.id.F5:
            case R.id.F6:
            case R.id.F5t:
            case R.id.F6t:
            case R.id.F7:
            {
                final View textEntryView = factory.inflate( R.layout.input, null );
                dialogObj = build( textEntryView, " " ); 
                return dialogObj; 
            }
            case R.id.find:
            case SELECT_DIALOG:
            case UNSELECT_DIALOG: {
                final View searchView = factory.inflate( R.layout.search, null );
                if( id == R.id.find ) {
                    View search_params = searchView.findViewById( R.id.search_params );
                    if( search_params != null )
                        search_params.setVisibility( View.VISIBLE );
                }
                dialogObj = build( searchView, " " ); 
                return dialogObj; 
            }
            case R.id.filter: {
                final View filterView = factory.inflate( R.layout.filter, null );
                dialogObj = build( filterView, " " ); 
                return dialogObj; 
            }
            case LOGIN_DIALOG: {
                    final View textEntryView = factory.inflate( R.layout.login, null );
                    dialogObj = build( textEntryView, "Login" ); 
                    return dialogObj;
                }
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
            case R.id.F8t:
            case R.id.donate:
            case SMB_PLG_DIALOG:
            case SFTP_PLG_DIALOG:
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
                    final View progressView = factory.inflate( R.layout.progress, null );
                    return dialogObj = new AlertDialog.Builder( owner )
                        .setView( progressView )
                        .setTitle( R.string.progress )
                        .setPositiveButton( R.string.dialog_close, this )
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
            Log.e( TAG, "id=" + id, e );
        } finally {
            if( dialogObj == null )
                Log.e( TAG, "Failed. id=" + id );
        }
        return null;
    }

    class DatePickerButton implements View.OnClickListener {
        java.text.DateFormat df;
        Calendar cal = Calendar.getInstance();
        Button   button;
        
        public DatePickerButton( Context ctx, Button button_ ) {
            df = DateFormat.getDateFormat( ctx );
            button = button_;
            CharSequence cs = button.getText();
            if( cs == null || cs.length() == 0 )
                button.setText( df.format( cal.getTime() ) );
            button.setOnClickListener( this );
        }
        @Override
        public void onClick( View v ) {
              Date d = null;
              try {
                   d = df.parse( button.getText().toString() );
              } catch( Exception e ) {}                                
              cal.setTime( d == null ? new Date() : d );
              new DatePickerDialog( owner, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet( DatePicker vw, int y, int m, int d ) {
                        Calendar cda = new GregorianCalendar( y, m, d );
                        button.setText( df.format( cda.getTime() ) );
                    }
              }, cal.get( Calendar.YEAR ) , 
                 cal.get( Calendar.MONTH ), 
                 cal.get( Calendar.DAY_OF_MONTH ) ).show();
        }
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
            case R.id.F2t: 
            {
                final String op_title = owner.getString( R.string.rename_title );
                String op = owner.getString( R.string.to_rename );
                if( op == null || op.length() <= 1 )
                    op = op_title;
                dialog.setTitle( op_title );
                
                String item_name = owner.panels.getSelectedItemName( R.id.F2t == id );
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
                dialog.setTitle( R.string.newf_title );
                if( prompt != null )
                    prompt.setText( R.string.newf_prompt );
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 80 );
                    edit.setText( "" );
                }
                break;
            }
            case R.id.F6:
            case R.id.F6t:
                move = true;
            case R.id.F5: 
            case R.id.F5t: 
            {
                final String op_title = owner.getString( move ? R.string.move_title : R.string.copy_title );
                String op = owner.getString( move ? R.string.to_move : R.string.to_copy );
                if( op == null || op.length() <= 1 )
                    op = op_title;
                dialog.setTitle( op_title );
                boolean touch = dialogId == R.id.F5t || dialogId == R.id.F6t;
                if( prompt != null ) {
                    String summ = owner.panels.getActiveItemsSummary( touch );
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
                    
                    CommanderAdapter ca = owner.panels.getListAdapter( false );

                    Uri u = ca != null ? ca.getUri() : null;
                    String cts = u != null ? u.toString() : "";
//                    String cts = Favorite.screenPwd( owner.panels.getFolderUriWithAuth( false ) );
                    if( !Utils.str( cts ) ) return;
                    if( cts.charAt( 0 ) == '/' )
                        cts = Utils.unEscape( cts ); 
                    int qm_pos = cts.indexOf( '?' );
                    if( qm_pos > 0 )
                        cts = cts.substring( 0, qm_pos );
                    edit.setText( Utils.mbAddSl( cts ) );
                    if( Utils.getCount( owner.panels.getMultiple( touch ) ) == 1 )
                        edit.selectAll();
                }
                break;
            }

            case R.id.open_zip: 
            {
                final String op = owner.getString( R.string.open );
                dialog.setTitle( op );
                prompt.setText( owner.getString( R.string.file_name ) );
                TextView file_path = (TextView)dialog.findViewById( R.id.file_path );
                file_path.setText( this.activeFileName );
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource( owner,
                        R.array.encoding, android.R.layout.simple_spinner_item );
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
                Spinner encoding_spin = (Spinner)dialog.findViewById( R.id.encoding );
                encoding_spin.setAdapter( adapter );            
                
                CheckBox encrypr_cb = (CheckBox)dialog.findViewById( R.id.encrypt );
                encrypr_cb.setOnClickListener( new View.OnClickListener() {
                    @Override
                    public void onClick( View v ) {
                        CheckBox encrypr_cb = (CheckBox)v;
                        View pwb = Dialogs.this.dialogObj.findViewById( R.id.password_block );
                        pwb.setVisibility( encrypr_cb.isChecked() ? View.VISIBLE : View.GONE );
                    }
                } );
                
                break;
            }
            
            case R.id.new_zip: 
            case R.id.new_zipt: 
            {
                final String op = owner.getString( R.string.create_zip_title );
                dialog.setTitle( op );
                if( prompt != null ) {
                    String summ = owner.panels.getActiveItemsSummary( R.id.new_zipt == id );
                    if( summ == null ) {
                        dialog.dismiss();
                        summ = owner.getString( R.string.no_items );
                        owner.showMessage( owner.getString( R.string.op_not_alwd, op ) );
                    }
                    prompt.setText( owner.getString( R.string.oper_item_to, 
                            owner.getString( R.string.copy_title ), summ ) );
                }
                if( edit != null ) {
                    edit.setWidth( owner.getWidth() - 70 );
                    CommanderAdapter oth_ca = owner.panels.getListAdapter( false );
                    String path = owner.panels.getFolderUriWithAuth( !oth_ca.hasFeature( Feature.FS ) ) + ".zip";
                    edit.setText( path );
                    edit.setSelection( path.length() - 4 );
                }
                
                CheckBox encrypr_cb = (CheckBox)dialog.findViewById( R.id.encrypt );
                encrypr_cb.setOnClickListener( new View.OnClickListener() {
                    @Override
                    public void onClick( View v ) {
                        CheckBox encrypr_cb = (CheckBox)v;
                        View pwb = Dialogs.this.dialogObj.findViewById( R.id.password_block );
                        pwb.setVisibility( encrypr_cb.isChecked() ? View.VISIBLE : View.GONE );
                    }
                } );
                
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource( owner,
                        R.array.encoding, android.R.layout.simple_spinner_item );
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
                Spinner encoding_spin = (Spinner)dialog.findViewById( R.id.encoding );
                encoding_spin.setAdapter( adapter );            
                
                
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
            case R.id.filter:
                dialog.setTitle( R.string.filter );
            case R.id.find: {
                if( id == R.id.find ) {
                    dialog.setTitle( R.string.search_title );
                    if( prompt != null )
                        prompt.setText( R.string.search_prompt );
                }                
                if( edit != null ) {
                    Editable edit_text = edit.getText();
                    if( edit_text.length() == 0 )
                        edit.setText( "*" );
                }
                Button mod_after_date = (Button)dialog.findViewById( R.id.mod_after_date );
                if( mod_after_date != null )
                    new DatePickerButton( owner, mod_after_date );

                Button mod_before_date = (Button)dialog.findViewById( R.id.mod_before_date );
                if( mod_before_date != null )
                    new DatePickerButton( owner, mod_before_date );
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
                EditText n_v = (EditText)dialog.findViewById( R.id.username_edit );
                EditText p_v = (EditText)dialog.findViewById( R.id.password_edit );
                if( crd != null ) {
                    String un = crd.getUserName();
                    n_v.setText( un );
                    p_v.setText( crd.getPassword() != null ? crd.getPassword() : "" );
                    crd = null;
                }
                if( pw_only ) {
                    dialog.findViewById( R.id.username_prompt ).setVisibility( View.GONE );
                    n_v.setVisibility( View.GONE );
                }
                AlertDialog ad = (AlertDialog)dialog;
                String title = Utils.str( toShowInAlertDialog ) ? toShowInAlertDialog : owner.getString( R.string.login_title );
                int nl_pos = title.indexOf( '\n' );
                if( nl_pos > 0 && prompt != null ) {
                    prompt.setText( title.substring( nl_pos+1 ) );
                    title = title.substring( 0, nl_pos );
                }
                ad.setTitle( title );
                toShowInAlertDialog = null;
                break;
            }
            case R.id.F8: 
            case R.id.F8t: 
            {
                AlertDialog ad = (AlertDialog)dialog;
                ad.setTitle( R.string.delete_title );
                String str, summ = owner.panels.getActiveItemsSummary( R.id.F8t == id );
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
            case SFTP_PLG_DIALOG:
                ( (AlertDialog)dialog ).setMessage( owner.getString( R.string.sftp_missed ) );
                break;
            case R.id.about: {
                    PackageInfo pi = null;
                    try {
                        pi = owner.getPackageManager().getPackageInfo( owner.getPackageName(), 0 );
                    } catch( NameNotFoundException e ) {
                        Log.e( TAG, "Package name not found", e );
                    }
                    String txt = owner.getString(R.string.about_text, pi != null ? pi.versionName : "?", owner.getString(R.string.donate_uri) );
                    TextView tv = (TextView )dialog.findViewById( R.id.text_view );
                    //if( id == R.id.about ) tv.setAutoLinkMask( Linkify.EMAIL_ADDRESSES );
                    tv.setMovementMethod( LinkMovementMethod.getInstance() );
                    tv.setText( Html.fromHtml( txt ) );
                    break;
                }
            case INFO_DIALOG:
                if( toShowInAlertDialog != null ) {
                    TextView tv = (TextView )dialog.findViewById( R.id.text_view );
                    if( tv != null ) {
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
                        int fs = ( reduce_size ? 15 : 18 ) + ( fnt_sz - 12 );
                        tv.setTextSize( fs > 12 ? fs : 12 );
                        if( Utils.isHTML( toShowInAlertDialog ) ) {
                            if( toShowInAlertDialog.indexOf( "</a>", 3 ) > 0 )
                                tv.setMovementMethod( LinkMovementMethod.getInstance() );                            
                            tv.setText( Html.fromHtml( toShowInAlertDialog.replaceAll( "\\n", "<br/>" ) ) );
                        } else
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

    public void setProgress( String string, int progress, int progressSec, int speed ) {
        if( dialogObj == null )
            return;
        try {
            if( string != null ) {
                TextView t = (TextView)dialogObj.findViewById( R.id.text );
                if( t != null ) {
                    t.setSingleLine( string.indexOf( '\u2026' ) < 0 );
                    t.setText( string );
                }
            }
            ProgressBar p_bar = (ProgressBar)dialogObj.findViewById( R.id.progress_bar );
            TextView perc_t = (TextView)dialogObj.findViewById( R.id.percent );

            if( progress >= 0 )
                p_bar.setProgress( progress );
            if( progressSec >= 0 )
                p_bar.setSecondaryProgress( progressSec );
            if( perc_t != null ) {
                perc_t.setTextSize( 14 );
                perc_t.setText( "" + ( progressSec > 0 ? progressSec : progress ) + "%" );
            }
            TextView speed_t = (TextView)dialogObj.findViewById( R.id.speed );
            if( speed > 0 ) {
                progressCounter++;
                progressAcSpeed += speed;
                long avgsp = progressAcSpeed / progressCounter;  
                String str = Utils.getHumanSize( speed ) + "/" + owner.getString( R.string.second ) + 
                      " (" + Utils.getHumanSize( avgsp ) + "/" + owner.getString( R.string.second ) + ")";
                speed_t.setText( str );
            } 
            else if( speed < 0 || progress == 0 ) { 
                speed_t.setText( "" );
                progressCounter = 0;
                progressAcSpeed = 0;
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    public void setActiveFile( String fn ) {
        this.activeFileName = fn;
    }
    public void setMessageToBeShown( String string, String cookie_ ) {
        toShowInAlertDialog = string;
        cookie = cookie_;
    }
    public void setCookie( String cookie_ ) {
        cookie = cookie_;
    }
    public void setCredentials( Credentials crd_, int which_panel_, boolean pw_only ) {
        this.crd = crd_;
        this.which_panel = which_panel_;
        this.pw_only = pw_only; 
    }
    @Override
    public void onClick( DialogInterface idialog, int whichButton ) {
        if( dialogObj == null )
            return;
        try {
            if( valid && whichButton == DialogInterface.BUTTON_POSITIVE ) {
                switch( dialogId ) {
                case R.id.F2:
                case R.id.F2t:
                case R.id.new_file:
                case R.id.SF4:
                case R.id.F5:
                case R.id.F6:
                case R.id.F5t:
                case R.id.F6t:
                case R.id.F7:
                case R.id.find:
                case R.id.new_zip:
                case R.id.new_zipt:
                case R.id.filter: 
                case UNSELECT_DIALOG:
                case SELECT_DIALOG:
                    EditText edit = (EditText)dialogObj.findViewById( R.id.edit_field );
                    if( edit != null ) {
                        String file_name = edit.getText().toString();
                        if( !Utils.str( file_name ) ) return;
                        switch( dialogId ) {
                        case R.id.F2:
                        case R.id.F2t:
                            owner.panels.renameItem( file_name, R.id.F2t == dialogId );
                            break;
                        case R.id.SF4:
                        case R.id.new_file:
                            owner.panels.createNewFile( file_name );
                            break;
                        case R.id.F6:
                        case R.id.F5:
                        case R.id.F6t:
                        case R.id.F5t:
                            if( file_name.charAt( 0 ) == '/' )
                                file_name = Utils.escapePath( file_name );
                            boolean touch = dialogId == R.id.F5t || dialogId == R.id.F6t;
                            boolean move  = dialogId == R.id.F6  || dialogId == R.id.F6t;
                            owner.panels.copyFiles( file_name, move, touch );
                            break;
                        case R.id.F7:
                            owner.panels.createFolder( file_name );
                            break;
                        case R.id.new_zip:
                        case R.id.new_zipt:
                            {
                                String password = null;
                                CheckBox encrypr_cb = (CheckBox)dialogObj.findViewById( R.id.encrypt );
                                if( encrypr_cb.isChecked() ) {
                                    EditText pw_edit = (EditText)dialogObj.findViewById( R.id.password_edit );
                                    password = pw_edit.getText().toString();
                                }
                                String encoding = null;
                                Spinner encoding_spin = (Spinner)dialogObj.findViewById( R.id.encoding );
                                int i = encoding_spin.getSelectedItemPosition();
                                if( i > 0 )
                                    encoding = owner.getResources().getStringArray( R.array.encoding_vals )[i];
                                owner.panels.createZip( file_name.trim(), R.id.new_zipt == dialogId, password, encoding );
                            }
                            break;
                        case R.id.filter: 
                            if( file_name.length() == 0 ) break;
                            try {
                                FilterProps filter = new FilterProps();
                                filter.file_mask = file_name;
                                filter.dirs  = ((CheckBox)dialogObj.findViewById( R.id.for_dirs  )).isChecked();
                                filter.files = ((CheckBox)dialogObj.findViewById( R.id.for_files )).isChecked();

                                String bts = ((EditText)dialogObj.findViewById( R.id.edit_bigger  )).getText().toString();
                                if( bts.length() > 0 )
                                    filter.larger_than  = Long.parseLong( bts );
                                String sts = ((EditText)dialogObj.findViewById( R.id.edit_smaller )).getText().toString();
                                if( sts.length() > 0 )
                                    filter.smaller_than = Long.parseLong( sts );

                                java.text.DateFormat df = DateFormat.getDateFormat( owner );
                                if( ((CheckBox)dialogObj.findViewById( R.id.mod_after )).isChecked() ) {
                                    CharSequence macs = ((Button)dialogObj.findViewById( R.id.mod_after_date )).getText();
                                    if( macs.length() > 0 )
                                        filter.mod_after = df.parse( macs.toString() ); 
                                }
                                if( ((CheckBox)dialogObj.findViewById( R.id.mod_before )).isChecked() ) {
                                    CharSequence mbcs = ((Button)dialogObj.findViewById( R.id.mod_before_date )).getText();
                                    if( mbcs.length() > 0 )
                                        filter.mod_before = df.parse( mbcs.toString() );
                                }
                                RadioButton rb = (RadioButton)dialogObj.findViewById( R.id.show_matched );
                                filter.include_matched = rb.isChecked();
                                
                                owner.panels.setFilter( filter );
                            } catch( Exception e ) {
                                Log.e( TAG, file_name, e );
                            }
                            break;
                        case R.id.find: 
                            if( file_name.length() > 0 ) {
                                StringBuilder sb = new StringBuilder( 128 );
                                sb.append( "q=" ).append( Utils.escapeRest( file_name ) );
                                try {
                                    boolean dirs  = ((CheckBox)dialogObj.findViewById( R.id.for_dirs  )).isChecked();
                                    boolean files = ((CheckBox)dialogObj.findViewById( R.id.for_files )).isChecked();
                                    if( dirs != files ) {
                                        sb.append( dirs ? "&d=1" : "&f=1" );
                                    } else 
                                        if( !dirs ) break;
                                    boolean one_level_only = !((CheckBox)dialogObj.findViewById( R.id.in_subf )).isChecked();
                                    if( one_level_only )
                                        sb.append( "&o=1" );
                                    String cs = ((EditText)dialogObj.findViewById( R.id.edit_content )).getText().toString();
                                    if( cs.length() > 0 )
                                        sb.append( "&c=" ).append( Utils.escapeRest( cs ) );

                                    String bts = ((EditText)dialogObj.findViewById( R.id.edit_bigger  )).getText().toString();
                                    if( bts.length() > 0 )
                                        sb.append( "&l=" ).append( bts );
                                    String sts = ((EditText)dialogObj.findViewById( R.id.edit_smaller )).getText().toString();
                                    if( sts.length() > 0 )
                                        sb.append( "&s=" ).append( sts );
                                    
                                    if( ((CheckBox)dialogObj.findViewById( R.id.mod_after )).isChecked() ) {
                                        CharSequence macs = ((Button)dialogObj.findViewById( R.id.mod_after_date )).getText();
                                        if( macs.length() > 0 )
                                            sb.append( "&a=" ).append( macs );
                                    }
                                    if( ((CheckBox)dialogObj.findViewById( R.id.mod_before )).isChecked() ) {
                                        CharSequence mbcs = ((Button)dialogObj.findViewById( R.id.mod_before_date )).getText();
                                        if( mbcs.length() > 0 )
                                            sb.append( "&b=" ).append( mbcs );
                                    }
                                } catch( Exception e ) {
                                    Log.e( TAG, file_name, e );
                                }
                                
                                Uri.Builder uri_b = new Uri.Builder()
                                    .scheme( "find" )
                                    .path( cookie )
                                    .encodedQuery( sb.toString() );
                                owner.Navigate( uri_b.build(), null, null );
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
                case R.id.open_zip:
                    {
                        Uri.Builder ub = Uri.parse( this.activeFileName ).buildUpon().scheme( "zip" );
                        Credentials crd = null;
                        CheckBox encrypr_cb = (CheckBox)dialogObj.findViewById( R.id.encrypt );
                        if( encrypr_cb.isChecked() ) {
                            EditText pw_edit = (EditText)dialogObj.findViewById( R.id.password_edit );
                            crd = new Credentials( null, pw_edit.getText().toString() );
                        }
                        Spinner encoding_spin = (Spinner)dialogObj.findViewById( R.id.encoding );
                        int i = encoding_spin.getSelectedItemPosition();
                        if( i > 0 )
                            ub.encodedQuery( "e=" + owner.getResources().getStringArray( R.array.encoding_vals )[i] );
                        owner.Navigate( ub.build(), crd, null );
                    }
                    break;
                case R.id.F8:
                case R.id.F8t:
                    owner.panels.deleteItems( R.id.F8t == dialogId );
                    break;
                case LOGIN_DIALOG: {
                        EditText name_edit = (EditText)dialogObj.findViewById( R.id.username_edit );
                        EditText pass_edit = (EditText)dialogObj.findViewById( R.id.password_edit );
                        if( name_edit != null && pass_edit != null )
                            owner.panels.login( new Credentials( name_edit.getText().toString(), pass_edit.getText().toString() ), which_panel );
                        which_panel = -1;
                    }
                    break;
                case R.id.donate:
                        owner.startViewURIActivity( R.string.donate_uri );
                        break;
                case SMB_PLG_DIALOG:
                        owner.startViewURIActivity( R.string.smb_app_uri );
                        break;
                case SFTP_PLG_DIALOG:
                        owner.startViewURIActivity( R.string.sftp_app_uri );
                        break;
                case FILE_EXIST_DIALOG:
                    owner.setResolution( Commander.REPLACE_ALL );
                    break;
                case PROGRESS_DIALOG:   // hide
                    owner.addBgNotifId( taskId );
                    taskId = 0L;
                    cancelDialog();
                    break;
                }
            } else if( whichButton == DialogInterface.BUTTON_NEGATIVE ) {
                if( dialogId == PROGRESS_DIALOG )
                    owner.stopEngine( taskId );
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
