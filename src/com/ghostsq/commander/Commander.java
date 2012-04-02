/**
 *	This interface to abstract the commander's main executable from its utilities such as adapters 
 */
package com.ghostsq.commander;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;

/**
 * @author zc2
 *
 */
public interface Commander {
	final static int UNKNOWN = 0, 
	                 ABORT   = 1,
	                 REPLACE = 2, 
	                 SKIP    = 4,
	                 DECIDED = 6,
                     APPLY_ALL   = 8,
	                 REPLACE_ALL = 8|2,
	                 SKIP_ALL    = 8|4;
	
    /**
     *   notifyMe() "what" constants:
     *   OPERATION_STARTED                     is sent when an operation starts
     *   OPERATION_FAILED                      always show message (default if not provided)  
     *   OPERATION_COMPLETED                   show message if provided)
     *   OPERATION_COMPLETED_REFRESH_REQUIRED  also make the adapter reread
     *   OPERATION_FAILED_LOGIN_REQUIRED       show user/pass dialog and pass the identity to the adapter with 
     *                                         the string as passed in the first parameter
     */
	public final static int  OPERATION_IN_PROGRESS = 0,
	                         OPERATION_STARTED = -1, 
	                         OPERATION_FAILED = -2, 
	                         OPERATION_COMPLETED = -3, 
	                         OPERATION_COMPLETED_REFRESH_REQUIRED = -4,
                             OPERATION_FAILED_LOGIN_REQUIRED = -5,
                             OPERATION_SUSPENDED_FILE_EXIST = -6;
	
	public final static int  OPERATION_REPORT_IMPORTANT = 870;

    public final static int  OPEN = 903, OPEN_WITH = 902, SEND_TO = 236, COPY_NAME = 390, FAV_FLD = 414;
    
    public final static String NOTIFY_COOKIE = "cookie", NOTIFY_SPEED = "speed", NOTIFY_CRD = "crd";
	
    /**
     * @return current UI context
     */
    public Context getContext();

    /**
     * @param in  - an intent to launch
     * @param ret - if not zero,  startActivityForResult( in, ret ) will be called
     */
    public void issue( Intent in, int ret );

	/**
	 * @param msg - message to show in an alert dialog
	 */
	public void    showError( String msg );

	/**
	 * @param msg - message to show in an info dialog
	 */
	public void    showInfo( String msg );

    /**
     * @param id - the dialog id 
     */
    public void    showDialog( int dialog_id );
	
	/**
     * Navigate the current panel to the specified URI. 
     * @param uri         -  URI to navigate to  
     * @param positionTo  - Select an item with the given name
     */
	public void    Navigate( Uri uri, String positionTo );
	
	/**
	 * Try to execute a command as if it came from the UI
	 * @param id - command id to execute
	 */
	public void dispatchCommand( int id );	
	
	/**
	 * Execute (launch) the specified item.  
	 * @param uri to open by sending an Intent
	 */
	public void Open( Uri uri );

    /**
     * The waiting thread call after it sent the OPERATION_SUSPENDED_FILE_EXIST notification
     * @return one of ABORT, REPLACE, REPLACE_ALL, SKIP, SKIP_ALL
     */
    public int getResolution();

    /**
     * Procedure completion notification. 
     * @param Message object with the following fields:
     *          .obj  - the message string
     *          .what - the event type (see above the OPERATION_... constants)
     *          .arg1 - main progress value (0-100)
     *          .arg2 - secondary progress value (0-100)
     *          .getData() - a bundle with a string NOTIFY_COOKIE 
     * @return true if it's fine to destroy the working thread 
     */
    public boolean notifyMe( Message m );
}
