package com.ghostsq.commander;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

public class FSAdapter extends CommanderAdapterBase {
    private String dirName, parentLink;
    private File[] files;
    private  int[] toList;
    
    public FSAdapter( Commander c, Uri d, int mode_ ) {
    	super( c, mode_ );
    	dirName = d == null ? DEFAULT_DIR : d.getPath();
        files = null;
    }

    @Override
    public String toString() {
        return dirName;
    }

    /*
     * CommanderAdapter implementation
     */
    public Uri getUri() {
        return Uri.parse( dirName );
    }
    @Override
    public void setIdentities( String name, String pass ) {
    }
    @Override
    public boolean readSource( Uri d ) {
    	try {
    	    if( d != null )
    	        dirName =  d.getPath();
    	    if( dirName == null ) return false;
            File dir = new File( dirName );
            files = dir.listFiles();
            if( files == null ) {
            	commander.notifyMe( commander.getContext().getString( R.string.no_such_folder, dirName ), 
            			            Commander.OPERATION_FAILED, 0 );
                return false;
            }
            FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING );
            Arrays.sort( files, comp );
            int num_files = files.length;
            int n = 0, num = num_files;
            boolean hide = ( mode & MODE_HIDDEN ) == HIDE_MODE;
            if( hide ) {
                for( int i = 0; i < num_files; i++ ) {
                	if( !files[i].isHidden() ) n++; 
                }
                num = n;
                n = 0;
            }
            toList = new int[num];
            for( int i = 0; i < num_files; i++ ) {
            	if( !hide || !files[i].isHidden() )
            		toList[n++] = i; 
            }       
            parentLink = dir.getParent() == null ? SLS : "..";
            commander.notifyMe( null, Commander.OPERATION_COMPLETED, 0 );
            return true;
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return false;
    }

    @Override
    public void openItem( int position ) {
        if( position == 0 ) { 
            File cur_dir_file = new File( dirName );
            String parent_dir = cur_dir_file.getParent();
            commander.Navigate( Uri.parse( parentLink != SLS ? ( parent_dir != null ? parent_dir : DEFAULT_DIR ) : SLS ), 
                                cur_dir_file.getName() );
        }
        else {
            File file = files[toList[position - 1]]; 
            if( file.isDirectory() ) {
                if( dirName.charAt( dirName.length() - 1 ) != File.separatorChar )
                    dirName += File.separatorChar;
                commander.Navigate( Uri.parse( dirName + file.getName() + File.separatorChar ), null );
            }
            else {
                if( Utils.getFileExt( file.getName() ).compareToIgnoreCase( ".zip" ) == 0 ) 
                    commander.Navigate( (new Uri.Builder()).scheme( "zip" ).authority( "" ).path( file.getAbsolutePath() ).build(), null );            
                else
                    commander.Open( file.getAbsolutePath() );
            }
        }
    }

    @Override
    public String getItemName( int position, boolean full ) {
        if( position < 0 || toList == null || position > toList.length )
            return position == 0 ? parentLink : null;
        if( full )
            return position == 0 ? (new File( dirName )).getParent() : files[toList[position - 1]].getAbsolutePath();
        else
            return position == 0 ? parentLink : files[toList[position - 1]].getName();
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
        try {
        	File[] list = Utils.getListOfFiles( bitsToNames( cis ) );
        	if( list != null ) {
        		
        		CalcSizesEngine size_engine = new CalcSizesEngine( handler, list ); 
        		size_engine.start();
        	}
		} 
        catch(Exception e) {
		}
	}
	class CalcSizesEngine extends Engine {
		File[] mList;
        protected int  num = 0, dirs = 0, depth = 0;
        protected long sum = 0;
        
        CalcSizesEngine( Handler h, File[] list ) {
        	super( h );
            mList = list;
        }
        @Override
        public void run() {
        	try {
				getSizes(mList);
				String result;
				if( mList.length == 1 ) {
					File f = mList[0];
					if( f.isDirectory() )
						result = "Folder \"" + f.getAbsolutePath() + "\",\n "
								+ num + " files";
					else
						result = "File \"" + f.getAbsolutePath() + "\"";
				} else
					result = "" + num + " files";
				result += ",\n" + Utils.getHumanSize(sum) + "bytes,";
				if( dirs > 0 )
					result += "\nin " + dirs + " director" + ( dirs > 1 ? "ies." : "y.");
				sendProgress(result, Commander.OPERATION_COMPLETED);
			} catch( Exception e ) {
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
			}
        }
    	protected final void getSizes( File[] list ) throws Exception {
    		for( int i = 0; i < list.length; i++ ) {
    			File f = list[i];
    			if( f.isDirectory() ) {
    				dirs++;
    				if( depth++ > 20 )
    					throw new Exception( commander.getContext().getString( R.string.too_deep_hierarchy ) );
    				getSizes( f.listFiles() );
    				depth--;
    			}
    			else {
    				num++;
    				sum += f.length();
    			}
    		}
    	}
    }
    @Override
    public boolean renameItem( int position, String newName ) {
        if( position <= 0 || position > toList.length )
            return false;
        try {
            return files[toList[position - 1]].renameTo( new File( dirName, newName ) );
        }
        catch( SecurityException e ) {
            commander.showError( "Security Exception: " + e );
            return false;
        }
    }
	@Override
	public boolean createFile( String fileURI ) {
		try {
			File f = new File( fileURI );
			return f.createNewFile();
		} catch( Exception e ) {
			commander.showError( "Unable to create file \"" + fileURI + "\", " + e.getMessage() );
		}
		return false;
	}
    @Override
    public void createFolder( String new_name ) {
        if( !(new File( dirName, new_name )).mkdir() )
            commander.showError( "Unable to create directory '" + new_name + "' in '" + dirName + "'" );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
            for( int i = 0; i < cis.size(); i++ ) {
                if( cis.valueAt( i ) ) {
                    int pos = cis.keyAt( i ) - 1;
                    if( pos >= 0 ) {
                        File f = files[toList[pos]];
                        if( f.isDirectory() && !Utils.deleteDirContent( f ) )
                            return false;
                        if( !f.delete() )
                            return false;
                    }
                }
            }
            readSource( null );
            commander.notifyMe( "All files were deleted", Commander.OPERATION_COMPLETED_REFRESH_REQUIRED, 0 );
            return true;
        }
        catch( SecurityException e ) {
            commander.showError( "Unable to delete: " + e );
        }
        return false;
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( move && to instanceof FSAdapter ) {
            try {
                String dest_folder = to.toString();
                String[] files_to_move = bitsToNames( cis );
                if( files_to_move == null )
                	return false;
                return moveFiles( files_to_move, dest_folder );
            }
            catch( SecurityException e ) {
                commander.showError( "Unable to move a file because of security reasons: " + e );
                return false;
            }
        }
        else {
            return to.receiveItems( bitsToNames( cis ), move );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, boolean move ) {
    	try {
            if( uris == null )
            	return false;
            if( move )
            	return moveFiles( uris, dirName );
            File dest_file = new File( dirName );
            if( dest_file == null )
            	return false;
            if( dest_file.exists() ) {
                if( !dest_file.isDirectory() )
                    return false;
            }
            else {
                if( !dest_file.mkdirs() )
                    return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null )
            	commander.showError( "Something wrong with the files " );
            else {
            	worker = new CopyEngine( handler, list, dirName );
            	worker.start();
	            return true;
            }
		} catch( Exception e ) {
			commander.showError( "Exception: " + e );
		}
		return false;
    }

    @Override
    public void terminateOperation() {
        if( worker != null && worker.isAlive() ) {
        	Toast.makeText( commander.getContext(), "Terminating...", Toast.LENGTH_SHORT ).show();
            worker.reqStop();
        }
    }

    @Override
	public void prepareToDestroy() {
		files = null;
		toList = null;
	}

    class CopyEngine extends CalcSizesEngine {
        String  mDest;
        int     counter = 0;
        long    bytes = 0;
        double  conv;
        
        CopyEngine( Handler h, File[] list, String dest ) {
        	super( h, list );
            mDest = dest;
        }

        @Override
        public void run() {
        	sendProgress( commander.getContext().getString( R.string.preparing ), 0, 0 );
        	try {
				getSizes( mList );
				conv = 100 / (double)sum;
	            String report = Utils.getCopyReport( copyFiles( mList, mDest ) );
	            sendResult( report );
			} catch( Exception e ) {
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
				return;
			}
        }
        private final int copyFiles( File[] list, String dest ) {
            int i;
            for( i = 0; i < list.length; i++ ) {
                FileChannel  in = null;
                FileChannel out = null;
                File outFile = null;
                File file = list[i];
                if( file == null ) {
                    errMsg = "Unknown error";
                    break;
                }
                String uri = file.getAbsolutePath();
                try {
                    if( stop || isInterrupted() ) {
                        errMsg = "Canceled.";
                        break;
                    }
                    outFile = new File( dest, file.getName() );
                    if( outFile.exists() ) {
                        errMsg = (outFile.isDirectory() ? "Directory '" : "File '") + outFile.getAbsolutePath() + "' already exist.";
                        break; // TODO: ask user about overwrite
                    }
                    if( file.isDirectory() ) {
                        if( outFile.mkdir() ) {
                            copyFiles( file.listFiles(), outFile.getAbsolutePath() );
                            if( errMsg != null )
                            	break;
                        }
                        else 
                            errMsg = "Unable to create directory '" + outFile.getAbsolutePath() + "' ";
                    }
                    else {
                        in  = new FileInputStream( file ).getChannel();
                        out = new FileOutputStream( outFile ).getChannel();
                        long size  = in.size();
                        final long max_chunk = 524288;
                        long chunk = size > max_chunk ? max_chunk : size;
                        int  so_far = (int)(bytes * conv);
                        for( long start = 0; start < size; start += chunk ) {
                        	sendProgress( "Coping \n'" + uri + "'...", so_far, (int)(bytes * conv) );
                        	bytes += in.transferTo( start, chunk, out );
                            if( stop || isInterrupted() ) {
                                errMsg = "Canceled.";
                                return counter;
                            }
                        }
                        if( i >= list.length-1 )
                        	sendProgress( "Copied \n'" + uri + "'   ", (int)(bytes * conv) );
                        counter++;
                    }
                }
                catch( SecurityException e ) {
                    errMsg = "Security issue on file '" + uri + "'.\n" + e.getMessage();
                }
                catch( FileNotFoundException e ) {
                    errMsg = "File not accessible.\n" + e.getMessage();
                }
                catch( IOException e ) {
                    errMsg = "Access error on file: '" + uri + "'. :\n" + e.getMessage();
                }
                catch( Exception e ) {
                    errMsg = "Error on file: '" + uri + "':\n" + e.getMessage();
                }
                finally {
                    try {
                        if( in != null ) 
                            in.close(); 
                        if( out != null )
                            out.close();
                        
                    }
                    catch( IOException e ) {
                        errMsg = "Error on file: '" + uri + "'.";
                    }
                    {
                        /*
                         * // TODO ask user asynchronous from a different thread
                         * int res = commander.askUser( err_msg ); if( res == Commander.ABORT ) return false; if( res == Commander.RETRY ) i--;
                         */
                    }
                }
                if( errMsg != null ) {
                    if( outFile != null ) {
                        try {
                            outFile.delete();
                        }
                        catch( SecurityException e ) {
                        }
                    }
                    break;
                }
            }
            return counter;
        }
    }

    private final boolean moveFiles( String[] files_to_move, String dest_folder ) {
        for( int i = 0; i < files_to_move.length; i++ ) {
            File f = new File( files_to_move[i] );
            File dest = new File( dest_folder, f.getName() );
            if( !f.renameTo( dest ) ) {
                commander.showError( "Unable to move file '" + f.getAbsolutePath() + "'" );
                return false;
            }
        }
        return true;
    }
    private final String[] bitsToNames( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            String[] uris = new String[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    uris[j++] = getItemName( cis.keyAt( i ), true );
            return uris;
		} catch( Exception e ) {
			commander.showError( "bitsToNames()'s Exception: " + e );
		}
		return null;
    }

    /*
     *  ListAdapter implementation
     */
    
    @Override
    public int getCount() {
//        showMessage( "getCount called: " + getCountCounter++ );
        if( files == null || toList == null )
            return 1;
        return toList.length + 1;
    }

    @Override
    public Object getItem( int position ) {
        return toList != null && files != null && position > 0 && position <= toList.length ? files[toList[position - 1]] : new Object();
    }

    @Override
    public long getItemId( int position ) {
        return position;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
    	Item item = new Item();
        if( position == 0 ) {
            item.name = parentLink;
        }
        else {
        	if( files != null && toList != null && position-1 < toList.length ) {
	            File curFile = files[toList[position - 1]];
	            try {
		            curFile.isHidden();
		            item.name = curFile.getName();
		            item.size = curFile.length();
		            item.dir = curFile.isDirectory();
				} catch( Exception e ) {
					// TODO: handle exception
				}
	            ListView flv = (ListView)parent;
	            SparseBooleanArray cis = flv.getCheckedItemPositions();
	            item.sel = cis.get( position );
	            long msFileDate = curFile.lastModified();
	            if( msFileDate != 0 )
	                item.date = new Date( msFileDate );
            }
            else
            	item.name = "";
        }
        return getView( convertView, parent, item );
    }

    public class FilePropComparator implements Comparator<File> {
        int type;

        public FilePropComparator( int type_ ) {
            type = type_;
        }
        public int compare( File f1, File f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            if( type == SORT_NAME )
                return f1.compareTo( f2 );
            if( type == SORT_SIZE )
                return (int)(f1.length() - f2.length());
            if( type == SORT_DATE )
                return (int)(f1.lastModified() - f2.lastModified() );
            return 0;
        }
    }
}
