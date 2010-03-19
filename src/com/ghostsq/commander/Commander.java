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
     *                                           the string as passed in the first parameter
     */
	final static int OPERATION_FAILED = -1, OPERATION_COMPLETED = -2, OPERATION_COMPLETED_REFRESH_REQUIRED = -3,
	                 OPERATION_FAILED_LOGIN_REQUIRED = -4;
	/**
	 * try to avoid this call. The adapter should be as UI-free as possible
	 * @return current UI context
	 */
	public Context getContext();
	/**
     * @param string current status description
	 * @param prg1 of MAX, or OPERATION_xxx (see above). To set MAX, call with SET_MAX as the first param 
	 * @param prg2 of MAX, pass -1 to make it not changed
	 */
	void notifyMe( String string, int prg1, int prg2 );
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
	 * @param uri to open by sending an Intent
	 */
	public void    Open( String uri );
}
