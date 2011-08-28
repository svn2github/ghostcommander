/**
 *	This interface to abstract the commander's main executable from its utilities such as adapters 
 */
package com.ghostsq.commander;

import android.content.Context;
import android.net.Uri;

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
     *   notifyMe() constants:
     *   OPERATION_FAILED                      always show message (default if not provided)  
     *   OPERATION_COMPLETED                   show message if provided)
     *   OPERATION_COMPLETED_REFRESH_REQUIRED  also make the adapter reread
     *   OPERATION_FAILED_LOGIN_REQUIRED       show user/pass dialog and pass the identity to the adapter with 
     *                                         the string as passed in the first parameter
     */
	public final static int  OPERATION_STARTED = -1, 
	                         OPERATION_FAILED = -2, 
	                         OPERATION_COMPLETED = -3, 
	                         OPERATION_COMPLETED_REFRESH_REQUIRED = -4,
                             OPERATION_FAILED_LOGIN_REQUIRED = -5,
                             OPERATION_SUSPENDED_FILE_EXIST = -6;
	
	public final static int  OPERATION_REPORT_IMPORTANT = 870;

    public final static int  OPEN = 903, OPEN_WITH = 902, SEND_TO = 236, COPY_NAME = 390, FAV_FLD = 414;
	
	/**
	 * @return current UI context
	 */
	public Context getContext();

	/**
	 * @param err_msg message to show in an alert dialog
	 */
	public void    showError( String err_msg );

	/**
	 * @param msg message to show in an info dialog
	 */
	public void    showInfo( String msg );

	/**
     * Navigate the current panel to the specified URI. 
     * @param uri         -  URI to navigate to  
     * @param positionTo  - Select an item with the given name
     */
	public void    Navigate( Uri uri, String positionTo );
	
    /**
     * Tries to load an adapter class from foreign package
     * @param String type       - adapter type, also the suffix of the plugin application 
     * @param String class_name - the adapter class name to be loaded
     * @param int    dialog_id  - resource ID to show dialog if the class can't be loaded
     */
	public CommanderAdapter CreateExternalAdapter( String type, String class_name, int dialog_id );

	
	/**
	 * Try to execute a command as if it came from the UI
	 * @param id - command id to execute
	 */
	public void dispatchCommand( int id );	
	
	/**
	 * Execute (launch) the specified item.  
	 * @param uri to open by sending an Intent
	 */
	public void Open( String uri );

    /**
     * The waiting thread call after it sent the OPERATION_SUSPENDED_FILE_EXIST notification
     * @return one of ABORT, REPLACE, REPLACE_ALL, SKIP, SKIP_ALL
     */
    public int getResolution();
	
	/**
     * Procedure completion notification. 
     * @param Notify object - see below
     * @return true if finishing 
     */
	public boolean notifyMe( Notify obj );

	/**
     * Adapter's working procedures (both in this thread and the candler of worker threads) should pass this
     * @param string current status description
     * @param prg1 of MAX, or OPERATION_xxx (see above). To set MAX, call with SET_MAX as the first param 
     * @param prg2 of MAX, pass -1 to make it not changed
     * @param cookie 
     */
	class Notify {
	    public String string, cookie;
	    public int status = 0, substat = 0;
        public Notify( int status_ ) {
            status = status_;
        }
        public Notify( String string_, int status_ ) {
            string = string_;
            status = status_;
        }
        public Notify( String string_, int status_, int substat_ ) {
            string = string_;
            status = status_;
            substat = substat_;
        }
        public Notify( String string_, int status_, String cookie_ ) {
            string = string_;
            status = status_;
            cookie = cookie_;
        }
	}
}
