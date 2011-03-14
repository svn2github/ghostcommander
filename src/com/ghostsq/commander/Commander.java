/**
 *	This interface to abstract the commander's main executable from its utilities such as adapters 
 */
package com.ghostsq.commander;

import android.content.Context;
import android.net.Uri;

public interface Commander {
	final static int ABORT = -1, RETRY = -2, IGNORE = -3;
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
        	                 OPERATION_FAILED_LOGIN_REQUIRED = -5;

    public final static int  OPEN = 903, OPEN_WITH = 902, SEND_TO = 236, COPY_NAME = 390, FAV_FLD = 414;
	
	/**
	 * try to avoid this call. The adapter should be as UI-free as possible
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
	 * @param err_msg text to show to the user
	 * @return ABORT or RETRY or IGNORE
	 */
	public int     askUser( String err_msg );
    /**
     * @param uri         - Navigate to the resource by this URI 
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
	 * @param uri to open by sending an Intent
	 */
	public void Open( String uri );
	
    /**
     * procedure completion notification. 
     * @param Notify obj - see below
     */
    public void notifyMe( Notify obj );
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
