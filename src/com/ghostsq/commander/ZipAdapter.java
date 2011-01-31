package com.ghostsq.commander;

import java.lang.System;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;

public class ZipAdapter extends CommanderAdapterBase {
    public final static String TAG = "ZipAdapter";
    private static final char   SLC = File.separatorChar;
    private static final String SLS = File.separator;
    protected final static int BLOCK_SIZE = 100000;
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  Uri          uri = null;
    public  ZipFile      zip = null;
    public  ZipEntry[] items = null;

    public ZipAdapter( Commander c ) {
        super( c, 0 );
        parentLink = PLS;
    }
    @Override
    public String getType() {
        return "zip";
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri != null )
                uri = tmp_uri;
            if( uri == null )
                return false;
            if( worker != null ) { // that's not good.
                if( worker.isAlive() ) {
                    showMessage( "Another worker thread still alive" );
                    worker.interrupt();
                    Thread.sleep( 500 );      // it has ended itself!
                    if( worker.isAlive() ) 
                        return false;      
                }
            }
            Log.v( TAG, "reading " + uri );
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new ListEngine( handler, pass_back_on_done );
            worker.start();
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        commander.notifyMe( new Commander.Notify( "Fail", Commander.OPERATION_FAILED ) );
        return false;
    }

    private final ZipEntry[] GetFolderList( String fld_path ) {
        if( zip == null ) return null;
        if( fld_path == null ) fld_path = ""; 
        else
            if( fld_path.length() > 0 && fld_path.charAt( 0 ) == SLC ) 
                fld_path = fld_path.substring( 1 );                                 
        int fld_path_len = fld_path.length();
        if( fld_path_len > 0 && fld_path.charAt( fld_path_len - 1 ) != SLC ) { 
            fld_path = fld_path + SLC;
            fld_path_len++;
        }
        Enumeration<? extends ZipEntry> entries = zip.entries();
        if( entries == null )
            return null;
        ArrayList<ZipEntry> array = new ArrayList<ZipEntry>();
        while( entries.hasMoreElements() ) {
            ZipEntry e = entries.nextElement();
            if( e != null ) {
                String entry_name = e.getName();
                if( entry_name == null || fld_path.compareToIgnoreCase(entry_name) == 0 ) 
                    continue;
                /* There are at least two kinds of zips - with dedicated folder entry and without one.
                 * The code below should process both.
                 * Do not change until you fully understand how it works.
                 */
                if( fld_path.regionMatches( true, 0, entry_name, 0, fld_path_len ) ) {
                    int sl_pos = entry_name.indexOf( SLC, fld_path_len );
                    if( sl_pos > 0 ) {
                        String sub_dir = entry_name.substring( fld_path_len, sl_pos );
                        int    sub_dir_len = sub_dir.length();
                        boolean not_yet = true;
                        for( int i = 0; i < array.size(); i++ ) {
                            String a_name = array.get( i ).getName();
                            if( a_name.regionMatches( fld_path_len, sub_dir, 0, sub_dir_len ) ) {
                                not_yet = false;
                                break;
                            }
                        }
                        if( not_yet )   // a folder
                            array.add( new ZipEntry( entry_name.substring( 0, sl_pos+1 ) ) );
                    }
                    else
                        array.add( e ); // a leaf
                }
            }
        }
        return array.toArray( new ZipEntry[array.size()] );
    }
    
    class ListEngine extends Engine {
        private ZipEntry[] items_tmp = null;
        public  String pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
        	super( h );
        	pass_back_on_done = pass_back_on_done_;
        }
        public ZipEntry[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
            	if( uri != null ) {
            	    String zip_path = uri.getPath(); 
                	if( zip_path != null ) {
                    	if( zip == null || zip_path.compareTo( zip.getName() ) != 0 ) 
                    	    zip = new ZipFile( zip_path );
                    	
                    	String cur_path = null;
                    	try {
                    	    cur_path = uri.getFragment();
                    	}
                    	catch( NullPointerException e ) {
                    	    // it happens only when the Uri is built by Uri.Builder
                    	    Log.e( TAG, "uri.getFragment()", e );
                    	}
                	    items_tmp = GetFolderList( cur_path );
                	    if( items_tmp != null ) { 
                            ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
                            Arrays.sort( items_tmp, comp );
                            sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
                            return;
                	    }
                	}
                }
            }
            catch( Exception e ) {
                System.err.print( "Exception:\n" + e );
                e.printStackTrace();
            }
            finally {
            	super.run();
            }
            sendProgress( "Can't open this ZIP file", Commander.OPERATION_FAILED, pass_back_on_done );
        }
    }
    @Override
    protected void onComplete( Engine engine ) {
        if( engine instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)engine;
            ZipEntry[] tmp_items = list_engine.getItems();
            if( tmp_items != null && ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                int cnt = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        cnt++;
                items = new ZipEntry[cnt];
                int j = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        items[j++] = tmp_items[i]; 
            }
            else
                items = tmp_items;
            notifyDataSetChanged();
        }
    }
    
    @Override
    public String toString() {
        return uri != null ? Uri.decode( uri.toString() ) : "";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public boolean isButtonActive( int brId ) {
        if( brId == R.id.F1 ||
            brId == R.id.F5 ||
            brId == R.id.F8 ||
            brId == R.id.F9 ||
            brId == R.id.F10 ) return true;
        return false;
    }

    @Override
    public void setIdentities( String name, String pass ) {
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
		commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
	}
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
    	
        try {
        	if( zip == null || to instanceof FSAdapter ) {
	        	File dest = new File( to.toString() );
	        	if( dest.exists() && dest.isDirectory() ) {
		        	if( !checkReadyness() ) return false;
		        	ZipEntry[] subItems = bitsToItems( cis );
		        	if( subItems != null ) {
		        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
		                worker = new CopyFromEngine( handler, subItems, dest );
		                worker.start();
		                return true;
		        	}
	        	}
        	}
        	commander.notifyMe( new Commander.Notify( "Failed to proceed.", Commander.OPERATION_FAILED ) );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }

    class CopyFromEngine extends Engine 
    {
	    private File       dest_folder;
	    private ZipEntry[] mList = null;
	    private String     base_pfx;
	    private int        base_len; 
	    CopyFromEngine( Handler h, ZipEntry[] list, File dest ) {
	    	super( h );
	    	mList = list;
	        dest_folder = dest;
            try {
                base_pfx = uri.getFragment();
                if( base_pfx == null )
                    base_pfx = "";
                base_len = base_pfx.length(); 
            }
            catch( NullPointerException e ) {
                System.err.print( "Exception: " + e + " on uri.getFragment()" );
            }
	        
	    }
	    @Override
	    public void run() {
	    	int total = copyFiles( mList, "" );
			sendResult( Utils.getOpReport( total, "unpacked" ) );
	        super.run();
	    }
	    private final int copyFiles( ZipEntry[] list, String path ) {
	        int counter = 0;
	        try {
	            long dir_size = 0, byte_count = 0;
	            for( int i = 0; i < list.length; i++ ) {
                    ZipEntry f = list[i];	            
                    if( !f.isDirectory() )
                        dir_size += f.getSize();
	            }
	            
	            double conv = 100./(double)dir_size;
	        	for( int i = 0; i < list.length; i++ ) {
	        		ZipEntry f = list[i];
	        		if( f == null ) continue;
	        		String full_name = f.getName();
	        		if( full_name == null ) continue;
        		    String file_name = new File( full_name ).getName();
        		    File   dest_file = new File( dest_folder, path + file_name );
        			String rel_name = full_name.substring( base_len );
        			
        			if( f.isDirectory() ) {
        				sendProgress( "Processing folder '" + rel_name + "'...", 0 );
        				if( !dest_file.mkdir() ) {
        					if( !dest_file.exists() || !dest_file.isDirectory() ) {
	        					errMsg = "Can't create folder \"" + dest_file.getCanonicalPath() + "\"";
	        					break;
        					}
        				}
        				ZipEntry[] subItems = GetFolderList( full_name );
	                    if( subItems == null ) {
	                    	errMsg = "Failed to get the file list of the subfolder '" + rel_name + "'.\n";
	                    	break;
	                    }
        				counter += copyFiles( subItems, rel_name );
        				if( errMsg != null ) break;
        			}
        			else {
        				if( dest_file.exists() && !dest_file.delete() ) {
    	        			errMsg = "Please make sure the folder '" + dest_folder.getCanonicalPath() + "' is writable";
    	        			break;
        				}
        				InputStream in = zip.getInputStream( f );
        				FileOutputStream out = new FileOutputStream( dest_file );
        	            byte buf[] = new byte[BLOCK_SIZE];
        	            int  n = 0;
        	            int  so_far = (int)(byte_count * conv);
        	            while( true ) {
        	                n = in.read( buf );
        	                if( n < 0 ) break;
        	                out.write( buf, 0, n );
        	                byte_count += n;
        	                sendProgress( "Unpacking \n'" + rel_name + "'...", so_far, (int)(byte_count * conv) );
                            if( stop || isInterrupted() ) {
                                in.close();
                                out.close();
                                dest_file.delete();
                                errMsg = "File '" + dest_file.getName() + "' was not completed, delete.";
                                break;
                            }
        	            }
        			}
                    if( stop || isInterrupted() ) {
                        error( "Canceled by a request." );
                        break;
                    }
                    if( i >= list.length-1 )
                        sendProgress( "Unpacked \n'" + rel_name + "'   ", (int)(byte_count * conv) );
        			counter++;
	        	}
	    	}
			catch( Exception e ) {
				e.printStackTrace();
				errMsg = "Exception: " + e.getMessage();
			}
	        return counter;
	    }
	}
	    
	@Override
	public boolean createFile( String fileURI ) {
		commander.notifyMe( new Commander.Notify( "Operation not supported", Commander.OPERATION_FAILED ) );
		return false;
	}
    @Override
    public void createFolder( String string ) {
        commander.notifyMe( new Commander.Notify( "Not supported", Commander.OPERATION_FAILED ) );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	if( !checkReadyness() ) return false;
        	ZipEntry[] to_delete = bitsToItems( cis );
        	if( to_delete != null && zip != null && uri != null ) {
        	    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                worker = new DelEngine( handler, new File( uri.getPath() ), to_delete, zip.size() );
                worker.start();
	            return true;
        	}
        }
        catch( Exception e ) {
            Log.e( TAG, "deleteItems()", e );
        }
        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_FAILED ) );
        return false;
    }

    class DelEngine extends Engine {
        private ZipEntry[] mList = null;
        private File       zipFile;
        private int        of = 0;
        DelEngine( Handler h, File zipFile_, ZipEntry[] list, int of_ ) {
            super( h );
            zipFile = zipFile_;
            mList = list;
            of = of_;
        }
        @Override
        public void run() {
            int processed = 0;
            try {
                File old_file = new File( zipFile.getAbsolutePath() + "_tmp_" + (new Date()).getSeconds() + ".zip" );
                if( !zipFile.renameTo(old_file) )
                    throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + old_file.getAbsolutePath() );
                ZipInputStream  zin = new ZipInputStream(  new FileInputStream( old_file ) );
                ZipOutputStream out = new ZipOutputStream( new FileOutputStream( zipFile ) );
                
                byte[] buf = new byte[BLOCK_SIZE];
                ZipEntry entry = zin.getNextEntry();
                while( entry != null ) {
                    if( isStopReq() ) break;
                    String name = entry.getName();
                    boolean spare_this = true;
                    for( ZipEntry z : mList ) {
                        if( isStopReq() ) break;
                        String name_to_delete = z.getName();
                        if( name.startsWith( name_to_delete ) ) {
                            spare_this = false;
                            sendProgress( "Deleting...", ++processed * 100 / of, 0 );
                            break;
                        }
                    }
                    if( spare_this ) {                        
                        // Add ZIP entry to output stream.
                        out.putNextEntry(new ZipEntry( name ));
                        // Transfer bytes from the ZIP file to the output file
                        int len;
                        while( (len = zin.read( buf )) > 0 ) {
                            if( isStopReq() ) break; 
                            out.write(buf, 0, len);
                        }
                    }
                    entry = zin.getNextEntry();
                }
                // Close the streams        
                zin.close();
                try {
                    out.close();
                } catch( Exception e ) {
                    Log.e( TAG, "DelEngine.run()->out.close()", e );
                }
                if( isStopReq() ) {
                    zipFile.delete();
                    old_file.renameTo( zipFile );
                    processed = 0;
                    error("Cancelled");
                }
                else {
                    old_file.delete();
                    zip = null;
                }
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            sendResult( Utils.getOpReport( processed, "removed" ) );
            super.run();
        }
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                String path = toString();
                if( path != null && path.length() > 0 ) {
                    if( path.charAt( path.length() - 1 ) != SLC )
                        path += SLS;
                    return path + items[position-1].getName();
                }
            }
            return new File( items[position-1].getName() ).getName();
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri != null ) {
            	String cur = null; 
            	try {
                    cur = uri.getFragment();
                } catch( Exception e ) {
                }
            	if( cur == null || cur.length() == 0 ||
            	                 ( cur.length() == 1 && cur.charAt( 0 ) == SLC ) ) {
            	    File zip_file = new File( uri.getPath() );
            	    String parent_dir = zip_file.getParent();
            	    commander.Navigate( Uri.parse( parent_dir != null ? parent_dir : Panels.DEFAULT_LOC ), 
            	            zip_file.getName() );
            	}
            	else {
            	    File cur_f = new File( cur );
            	    String parent_dir = cur_f.getParent();
            	    commander.Navigate( uri.buildUpon().fragment( parent_dir != null ? parent_dir : "" ).build(), cur_f.getName() );
            	}
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        ZipEntry item = items[position - 1];
        
        if( item.isDirectory() ) {
            String cur = null;    
            try {
                cur = uri.getFragment();
            }
            catch( NullPointerException e ) {}
        	if( cur == null ) 
        	    cur = "";
        	else
        	    if( cur.length() == 0 || cur.charAt( cur.length()-1 ) != SLC )
        	        cur += SLS;
            commander.Navigate( uri.buildUpon().fragment( item.getName() ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, boolean move ) {
    	try {
    		if( !checkReadyness() ) return false;
            if( uris == null || uris.length == 0 ) {
            	commander.notifyMe( new Commander.Notify( "Nothing to copy", Commander.OPERATION_FAILED ) );
            	return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null ) {
            	commander.notifyMe( new Commander.Notify( "Something wrong with the files", Commander.OPERATION_FAILED ) );
            	return false;
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            
            zip = null;
            items = null;
            
            worker = new CopyToEngine( handler, list, uri.getFragment(), move );
            worker.start();
            return true;
		} catch( Exception e ) {
			commander.notifyMe( new Commander.Notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED ) );
		}
		return false;
    }
    
    class CopyToEngine extends Engine {
        private File[]     top_list; 
        private int     basePathLen;
        private String  destPath;
        private long    totalSize = 0;
        CopyToEngine( Handler h, File[] list, String dest, boolean move ) { // TODO: delete the source on move
        	super( h );
            top_list = list;
            destPath = dest.endsWith( SLS ) ? dest : dest + SLS;
            basePathLen = list.length > 0 ? list[0].getParent().length() + 1 : 0;
        }
        @Override
        public void run() {
            int num_files = 0;
            try {
                sendProgress( "Preparing...", 1, 1 );
                ArrayList<File> full_list = new ArrayList<File>( top_list.length );
                totalSize = addToList( top_list, full_list );
                File zipFile = new File( uri.getPath() );
                num_files = addFilesToZip( zipFile, full_list );
            } catch( Exception e ) {
                error( "Exception: " + e.getMessage() );
            }
    		sendResult( Utils.getOpReport( num_files, "packed" ) );
            super.run();
        }
        // adds files to the global full_list, and returns the total size 
        private final long addToList( File[] sub_list, ArrayList<File> full_list ) {
            long total_size = 0;
            try {
                for( int i = 0; i < sub_list.length; i++ ) {
                    if( stop || isInterrupted() ) {
                        errMsg = "Canceled";
                        break;
                    }
                    File f = sub_list[i];
                    if( f != null && f.exists() ) {
                        if( f.isFile() ) {
                            total_size += f.length();
                            full_list.add( f );
                        }
                        else
                        if( f.isDirectory() ) {
                            total_size += addToList( f.listFiles(), full_list );
                            if( errMsg != null ) break;
                        }
                    }
                }
            }
            catch( Exception e ) {
                e.printStackTrace();
                errMsg = "Exception: " + e.getMessage();
            }
            return total_size;
        }
                
        // the following method was based on the one from http://snippets.dzone.com/posts/show/3468
        private final int addFilesToZip( File zipFile, ArrayList<File> files ) throws IOException {
           File old_file = new File( zipFile.getAbsolutePath() + "_tmp_" + (new Date()).getSeconds() + ".zip" );
           if( !zipFile.renameTo(old_file) )
               throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + old_file.getAbsolutePath() );
           ZipInputStream  zin = new ZipInputStream(  new FileInputStream( old_file ) );
           ZipOutputStream out = new ZipOutputStream( new FileOutputStream( zipFile ) );
           
           byte[] buf = new byte[BLOCK_SIZE];
           ZipEntry entry = zin.getNextEntry();
           while( entry != null ) {
               if( isStopReq() ) break;
               String name = entry.getName();
               boolean notInFiles = true;
               for( File f : files ) {
                   if( isStopReq() ) break;
                   String f_path = f.getCanonicalPath();
                   if( f_path.regionMatches( true, basePathLen, name, 0, name.length() ) ) {
                       notInFiles = false;
                       break;
                   }
               }
               if( notInFiles ) {
                   // Add ZIP entry to output stream.
                   out.putNextEntry(new ZipEntry( name ));
                   // Transfer bytes from the ZIP file to the output file
                   int len;
                   while( (len = zin.read( buf )) > 0 ) {
                       if( isStopReq() ) break; 
                       out.write(buf, 0, len);
                   }
               }
               entry = zin.getNextEntry();
           }
           // Close the streams        
           zin.close();
           
           if( isStopReq() ) {
               out.close();
               zipFile.delete();
               old_file.renameTo( zipFile );
               return 0;
           }
           
           double conv = 100./(double)totalSize;
           long   byte_count = 0;
           // Compress the files
           int i;
           for( i = 0; i < files.size(); i++ ) {
               if( isStopReq() ) break;
               InputStream in = new FileInputStream( files.get( i ) );
               // Add ZIP entry to output stream.
               String fn = files.get( i ).getCanonicalPath();
               String rfn = destPath + fn.substring( basePathLen );
               out.putNextEntry( new ZipEntry( rfn ) );
               // Transfer bytes from the file to the ZIP file
               int len;
               int  so_far = (int)(byte_count * conv);
               while( (len = in.read(buf)) > 0 ) {
                   if( isStopReq() ) break;
                   out.write(buf, 0, len);
                   byte_count += len;
                   sendProgress( "Packing \n'" + fn + "'...", so_far, (int)(byte_count * conv) );
               }
               // Complete the entry
               out.closeEntry();
               in.close();
           }
           // Complete the ZIP file
           out.close();
           if( isStopReq() ) {
               zipFile.delete();
               old_file.renameTo( zipFile );
               return 0;
           }
           old_file.delete();
           return i;
       }
    }
    @Override
    public boolean renameItem( int position, String newName ) {
     // TODO
        return false;
    }

	@Override
	public void prepareToDestroy() {
	    super.prepareToDestroy();
		items = null;
	}

    /*
     * BaseAdapter implementation
     */
    @Override
    public int getCount() {
   	    return items != null ? items.length + 1 : 1;
    }

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "";
        {
            if( position == 0 ) {
                item.name = parentLink;
            }
            else {
                if( items != null && position > 0 && position <= items.length ) {
                    ZipEntry curItem;
                    curItem = items[position - 1];
                    item.dir = curItem.isDirectory();
                    /*
                    byte name_bytes[] = EncodingUtils.getBytes( curItem.getName(), "cp437"); // iso-8859-1
                    String UTF8_name = new String( name_bytes );
                    item.name = item.dir ? SLS + UTF8_name : UTF8_name;
                    */
                    // item.name = item.dir ? SLS + getLocalName( curItem ) : getLocalName( curItem );

                    String entry_name = curItem.getName();
                    int lsp = entry_name.lastIndexOf( SLC, item.dir ? entry_name.length() - 2 : entry_name.length() );
                    
                    item.name = lsp > 0 ? entry_name.substring( lsp + 1 ) : entry_name;
                    item.size = curItem.getSize();
                    long item_time = curItem.getTime();
                    item.date = item_time > 0 ? new Date( item_time ) : null;
                }
            }
        }
        return item;
    }
    private final ZipEntry[] bitsToItems( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            ZipEntry[] subItems = new ZipEntry[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                	subItems[j++] = items[ cis.keyAt( i ) - 1 ];
            return subItems;
		} catch( Exception e ) {
			commander.showError( "bitsToNames()'s Exception: " + e.getMessage() );
		}
		return null;
    }
    private final boolean checkReadyness()   
    {
        if( worker != null ) {
        	commander.notifyMe( new Commander.Notify( "busy!", Commander.OPERATION_FAILED ) );
        	return false;
        }
    	return true;
    }
    private final String getLocalName( ZipEntry e ) {
        return new File( e.getName() ).getName();
    }
    public class ZipItemPropComparator implements Comparator<ZipEntry> {
        int type;
        boolean case_ignore;
        public ZipItemPropComparator( int type_, boolean case_ignore_ ) {
            type = type_;
            case_ignore = case_ignore_;
        }
		@Override
		public int compare( ZipEntry f1, ZipEntry f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            int ext_cmp = 0;
            switch( type ) {
            case SORT_EXT:
                ext_cmp = Utils.getFileExt( f1.getName() ).compareTo( Utils.getFileExt( f2.getName() ) );
                break;
            case SORT_SIZE:
                ext_cmp = f1.getSize() - f2.getSize() < 0 ? -1 : 1;
                break;
            case SORT_DATE:
                ext_cmp = f1.getTime() - f2.getTime() < 0 ? -1 : 1;
                break;
            }
            if( ext_cmp != 0 )
                return ext_cmp;
            return f1.getName().compareTo( f2.getName() );
		}
    }
}
