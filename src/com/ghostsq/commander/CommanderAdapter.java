package com.ghostsq.commander;

import android.net.Uri;
import android.util.SparseBooleanArray;

/**
 *		Interface to abstract list source
 *		It may be a FSAdapter - to browse files or any other network adapter and so on (to do)
 */
public interface CommanderAdapter {
	/**
	 * @param c since only the default constructor can be called, have to pass the commander reference here  
	 */
	public void Init( Commander c );
	
	/**
	 *  Output mode
	 */
	public final static int MODE_WIDTH = 0x01, NARROW_MODE = 0x0,     WIDE_MODE = 0x1, 
	                      MODE_DETAILS = 0x02, SIMPLE_MODE = 0x0, DETAILED_MODE = 0x2,
	                      MODE_FINGERF = 0x04,   SLIM_MODE = 0x0,      FAT_MODE = 0x4,
                          MODE_HIDDEN  = 0x08,   SHOW_MODE = 0x0,     HIDE_MODE = 0x8,
                          MODE_SORTING = 0x30,   SORT_NAME = 0x00,    SORT_SIZE = 0x10, SORT_DATE = 0x20;
    /**
     * @param mask - see bits above 
     * @param mode - see bits above 
     */
    public void   setMode( int mask, int mode );
    
    /**
     * @param name, pass 
     */
    public void setIdentities( String name, String pass );
    
    /**
     * @param uri - a folder's URI to initialize. If null passed, just refresh
     */
	public boolean readSource( Uri uri );
	/**
	 * @param position
	 * 		Tries to do something with the item (Outside of an adapter we don't know how to process it).
	 * 		But an adapter knows, is it a folder and can be opened (it calls Commander.Navigate() in this case)
	 * 		or processed as default action (then it calls Commander.Open() )
	 */
	/**
	 *  return current adapter's source URI
	 */
	public Uri getUri();
	public void   openItem( int position );
	
	/**
	 * @param position
	 * @param full       - true - to return the absolute path, false - only the local name
	 * @return string representation of the item
	 */
	public String getItemName( int position, boolean full );	

	/**
	 * @param  cis selected item (files or directories)
	 *         will call Commander.NotifyMe( "requested size info", Commander.OPERATION_COMPLETED ) when done  
	 */
	public void reqItemsSize( SparseBooleanArray cis );
	
	/**
	 * @param position in the list
	 * @param newName for the item
	 * @return true if success
	 */
	public boolean renameItem( int position, String newName );
	
	/**
	 * @param cis	booleans which internal items to copy
	 * @param to    an Adapter to be called {@link receiveItems()} and files to be passed
	 * @param move  move instead of copy
	 * @return      true if succeeded
	 */
	public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move );
	/**
	 * @param fileURIs  list of files as universal transport parcel. All kind of adapters (network, etc.)
	 * 					accepts data as files. It should be called from the current list's adapter
	 * @param move      move instead of copy
	 * @return          true if succeeded
	 */
	public boolean receiveItems( String[] fileURIs, boolean move );

	public boolean createFile( String fileURI );

	public void createFolder( String string );

	public boolean deleteItems( SparseBooleanArray cis );
	
	public void terminateOperation();
	public void prepareToDestroy();
}
