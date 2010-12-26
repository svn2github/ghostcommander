package com.ghostsq.commander;

import android.net.Uri;
import android.util.SparseBooleanArray;

/**
 *		Interface to abstract list source
 *		It may be a FSAdapter - to browse files or any other network adapter and so on (to do)
 */
public interface CommanderAdapter {
    /**
     * @return the implementation type string  
     */
    public String getType();
    
	/**
	 * @param c since only the default constructor can be called, have to pass the commander reference here  
	 */
	public void Init( Commander c );
	
	/**
	 *  Output mode
	 */
	public final static int MODE_WIDTH = 0x0001, NARROW_MODE = 0x0000,     WIDE_MODE = 0x0001, 
	                      MODE_DETAILS = 0x0002, SIMPLE_MODE = 0x0000, DETAILED_MODE = 0x0002,
	                      MODE_FINGERF = 0x0004,   SLIM_MODE = 0x0000,      FAT_MODE = 0x0004,
                          MODE_HIDDEN  = 0x0008,   SHOW_MODE = 0x0000,     HIDE_MODE = 0x0008,
                          MODE_SORTING = 0x0030,   SORT_NAME = 0x0000,     SORT_SIZE = 0x0010, SORT_DATE = 0x0020, SORT_EXT = 0x0030,
                            MODE_CASE  = 0x0040,   CASE_SENS = 0x0000,   CASE_IGNORE = 0x0040,
                            MODE_ICONS = 0x0080,   ICON_MODE = 0x0000,     TEXT_MODE = 0x0080,
                             MODE_ATTR = 0x0300,     NO_ATTR = 0x0000,     SHOW_ATTR = 0x0100, ATTR_ONLY = 0x0200,
                       SET_MODE_COLORS = 0xF0000000, SET_TXT_COLOR = 0x10000000, SET_SEL_COLOR = 0x20000000;
    /**
     * @param mask - see bits above 
     * @param mode - see bits above 
     */
    public void setMode( int mask, int mode );
    
    /**
     * @param name, pass 
     */
    public void setIdentities( String name, String pass );
    
    /**
     * @param uri - a folder's URI to initialize. If null passed, just refresh
     * @param pass_back_on_done - the file name to select
     */
	public boolean readSource( Uri uri, String pass_back_on_done );

	/**
	 * @return current adapter's source URI
	 */
	public Uri getUri();

	/**
     *      Tries to do something with the item (Outside of an adapter we don't know how to process it).
     *      But an adapter knows, is it a folder and can be opened (it calls Commander.Navigate() in this case)
     *      or processed as default action (then it calls Commander.Open() )
	 * @param position index of the item to action
	 */
	public void openItem( int position );
	
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
