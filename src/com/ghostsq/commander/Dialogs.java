package com.ghostsq.commander;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Dialogs implements DialogInterface.OnClickListener {
	public final static int ARI_DIALOG = 148, ALERT_DIALOG = 193, CONFIRM_DIALOG = 396, INPUT_DIALOG = 860, 
					   PROGRESS_DIALOG = 493,  INFO_DIALOG = 864,   ABOUT_DIALOG = 159, LOGIN_DIALOG = 995;
	public final static int numDialogTypes = 5;
	protected String toShowInAlertDialog = null, cookie = null;
	private int 		  dialogId;
	private Dialog 		  dialogObj;
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
    @Override
	public void onClick( DialogInterface idialog, int whichButton ) {
		if( dialogObj == null ) return;
    	if( whichButton == DialogInterface.BUTTON_POSITIVE ) {
    		switch( dialogId ) {
    		case FileCommander.NEWF_ACT:
        	case FileCommander.RENAME_ACT:
	    	case FileCommander.MOVE_ACT:
	    	case FileCommander.COPY_ACT:
	    	case FileCommander.MKDIR_ACT:
				EditText edit = (EditText)dialogObj.findViewById( R.id.edit_field );
				if( edit != null ) {
					String file_name = edit.getText().toString();
	            	switch( dialogId ) {
	            	case FileCommander.RENAME_ACT:
	            		owner.panels.renameFile( file_name );
       					break;
	            	case FileCommander.NEWF_ACT:
	            		owner.panels.createNewFile( file_name );
	            		break;
	    	    	case FileCommander.MOVE_ACT:
	    	    	case FileCommander.COPY_ACT:
	    	    		owner.panels.copyFiles( file_name, dialogId == FileCommander.MOVE_ACT );
	    	    		break;
	    	    	case FileCommander.MKDIR_ACT:
	    	    		owner.panels.createFolder( file_name );
	    	    		break;
	            	}
				}
				break;
            case FileCommander.DEL_ACT:
                owner.panels.deleteItems();
                break;
            case LOGIN_DIALOG: {
                    EditText name_edit = (EditText)dialogObj.findViewById( R.id.username_edit );
                    EditText pass_edit = (EditText)dialogObj.findViewById( R.id.password_edit );
                    if( name_edit != null && pass_edit != null )
                        owner.panels.login( cookie, name_edit.getText().toString(), pass_edit.getText().toString() );
                }
                break;
	    	case FileCommander.DONATE: 
	    		try {
		            Intent i = new Intent( Intent.ACTION_VIEW );
		            i.setData( Uri.parse( owner.getString( R.string.donate_uri ) ) );
		            owner.startActivity( i );
	    		}
	    		catch( Exception e) {
	    			e.printStackTrace();
				}
    		}
    	}
    	else
       	if( whichButton == DialogInterface.BUTTON_NEGATIVE ) {
       		if( dialogId == PROGRESS_DIALOG ) {
       			owner.panels.terminateOperation();
       		}
       	}
    	owner.panels.focus();
    }
	protected Dialog createDialog( int id ) {
		switch( id ) {
		case INPUT_DIALOG:
		case FileCommander.NEWF_ACT:
		case FileCommander.RENAME_ACT: 
		case FileCommander.COPY_ACT: 
		case FileCommander.MOVE_ACT: 
		case FileCommander.MKDIR_ACT: 
		    {
		        LayoutInflater factory = LayoutInflater.from( owner );
		        final View textEntryView = factory.inflate( R.layout.input, null );
		        return dialogObj = new AlertDialog.Builder( owner )
		            .setView(textEntryView)
		            .setTitle( " " )
		            .setPositiveButton( R.string.dialog_ok, this )
		            .setNegativeButton( R.string.dialog_cancel, this )
		            .create();
			}
		case LOGIN_DIALOG:
            {
                LayoutInflater factory = LayoutInflater.from( owner );
                final View textEntryView = factory.inflate( R.layout.login, null );
                return dialogObj = new AlertDialog.Builder( owner )
                    .setView(textEntryView)
                    .setTitle( "Login" )
                    .setPositiveButton( R.string.dialog_ok, this )
                    .setNegativeButton( R.string.dialog_cancel, this )
                    .create();
            }
        case ARI_DIALOG:
        	{
	            return dialogObj = new AlertDialog.Builder( owner )
	                .setIcon( android.R.drawable.ic_dialog_alert )
	                .setTitle(R.string.error)
	                .setMessage(R.string.error)
	                .setPositiveButton( R.string.dialog_abort, this )
	                .setNeutralButton(  R.string.dialog_retry, this )
	                .setNegativeButton( R.string.dialog_ignore,this )
	                .create();
        	}
        case CONFIRM_DIALOG:
        case FileCommander.DEL_ACT:
        case FileCommander.DONATE:
	    	{
	            return dialogObj = new AlertDialog.Builder( owner )
	                .setIcon( android.R.drawable.ic_dialog_alert )
	                .setTitle( R.string.confirm )
	                .setMessage( owner.getString( R.string.bug_warning ) )
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
	            .setMessage( "" )
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
				case FileCommander.RENAME_ACT: {
		        	dialog.setTitle( R.string.rename_title );
		        	TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
		        	if( prompt != null ) prompt.setText( R.string.rename_prompt );
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
				case FileCommander.NEWF_ACT: {
		        	dialog.setTitle( R.string.edit_title );
		        	TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
		        	if( prompt != null ) prompt.setText( R.string.newf_prompt );
					EditText edit = (EditText)dialog.findViewById( R.id.edit_field );
					if( edit != null ) {
						edit.setWidth( owner.getWidth() - 80 );
						edit.setText( "" );
					}
					break;
				}
				case FileCommander.MOVE_ACT:
					move = true;
				case FileCommander.COPY_ACT: {
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
                case FileCommander.MKDIR_ACT: {
                    dialog.setTitle( R.string.mkdir_title );
                    TextView prompt = (TextView)dialog.findViewById( R.id.prompt );
                    if( prompt != null )
                        prompt.setText(R.string.mkdir_prompt);
                    EditText edit = (EditText)dialog.findViewById( R.id.edit_field );
                    if( edit != null )
                        edit.setWidth( owner.getWidth() - 90 );
                    break;
                }
                case LOGIN_DIALOG: {
                        String host_name = "";
                        if( cookie != null ) {
                            Uri uri = Uri.parse( cookie );
                            if( uri != null ) {
                                host_name = " - " + uri.getHost();
                                Utils.Credentials crd = new Utils().new Credentials();
                                crd.set( uri.getUserInfo() );
                                EditText n_v = (EditText)dialog.findViewById( R.id.username_edit );
                                EditText p_v = (EditText)dialog.findViewById( R.id.password_edit );
                                if( n_v != null )
                                    n_v.setText( crd.userName != null ? crd.userName : "" );
                                if( p_v != null )
                                    p_v.setText( crd.userPass != null ? crd.userPass : "" );
                            }
                        }
                        AlertDialog ad = (AlertDialog)dialog;
                        ad.setTitle( owner.getString( R.string.login_title ) + host_name );
                        break;
                    }
				case FileCommander.DEL_ACT: {
					AlertDialog ad = (AlertDialog)dialog;
		        	ad.setTitle( R.string.delete_title );
					String str, summ = owner.panels.getActiveItemsSummary();
					if( summ == null ) {
						str = owner.getString( R.string.no_items );
						dialog.cancel();
					}
					else
						str = owner.getString( R.string.delete_q, summ );
		        	ad.setMessage( str );
					break;
				}
				case FileCommander.DONATE:
					((AlertDialog)dialog).setMessage( owner.getString( R.string.donation ) );
					break;
				case INFO_DIALOG:
				case ABOUT_DIALOG:
				case ALERT_DIALOG: {
		        	AlertDialog ad = (AlertDialog)dialog;
		        	if( toShowInAlertDialog != null ) {
		        		ad.setMessage( toShowInAlertDialog );
		        		toShowInAlertDialog = null;
		        	}
		        }
			}
		}
		catch( ClassCastException e ) {
			owner.showMessage( "ClassCastException: " + e );
		}
	}
	public void setMessage( String string, int progress, int progressSec ) {
		if( dialogObj == null ) return;
		try {
	    	AlertDialog pd = (AlertDialog)dialogObj;
    		if( string != null ) {
    			TextView t = (TextView)dialogObj.findViewById( R.id.text );
    			if( t != null ) t.setText( string );
    		}
    		ProgressBar p_bar = (ProgressBar)dialogObj.findViewById(R.id.progress_bar);
    		TextView perc_t = (TextView)dialogObj.findViewById( R.id.percent );
    		if( progress >= 0 ) {
    			p_bar.setProgress( progress );
    			if( perc_t != null ) perc_t.setText( "" + progress + "%" );
    		}
    		if( progressSec >= 0 )
    			p_bar.setSecondaryProgress( progressSec );
		}
		catch( ClassCastException e ) {
			owner.showMessage( "ClassCastException: " + e );
		}
	}
	public void setMessageToBeShown( String string, String cookie_ ) {
		toShowInAlertDialog = string;
		cookie = cookie_;
	}
}
