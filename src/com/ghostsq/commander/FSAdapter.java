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
    
    class FileEx  {
        public File f = null; 
        public long size = 0;
        public FileEx( String name ) {
            f = new File( name );
        }
        public FileEx( File f_ ) {
            f = f_;
        }
    }
    
    private String dirName, parentLink;
    private FileEx[] items;
    
    public FSAdapter( Commander c, Uri d, int mode_ ) {
    	super( c, mode_ );
    	dirName = d == null ? DEFAULT_DIR : d.getPath();
        items = null;
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
    	    if( worker != null ) worker.reqStop();
    	    if( d != null )
    	        dirName =  d.getPath();
    	    if( dirName == null ) return false;
            File dir = new File( dirName );
            File[] files_ = dir.listFiles();
            if( files_ == null ) {
            	commander.notifyMe( commander.getContext().getString( R.string.no_such_folder, dirName ), 
            			            Commander.OPERATION_FAILED, 0 );
                return false;
            }
            int num_files = files_.length;
            int n = 0, num = num_files;
            boolean hide = ( mode & MODE_HIDDEN ) == HIDE_MODE;
            if( hide ) {
                for( int i = 0; i < num_files; i++ ) {
                	if( !files_[i].isHidden() ) n++; 
                }
                num = n;
                n = 0;
            }
            items = new FileEx[num];
            for( int i = 0; i < num_files; i++ ) {
            	if( !hide || !files_[i].isHidden() )
            		items[n++] = new FileEx( files_[i] ); 
            }       

            FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING );
            Arrays.sort( items, comp );
                        
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
            File file = items[position - 1].f; 
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
        if( position < 0 || items == null || position > items.length )
            return position == 0 ? parentLink : null;
        if( full )
            return position == 0 ? (new File( dirName )).getParent() : items[position - 1].f.getAbsolutePath();
        else
            return position == 0 ? parentLink : items[position - 1].f.getName();
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
        try {
        	FileEx[] list = bitsToFiles( cis );
        	if( list != null ) {
        		if( worker != null && worker.reqStop() ) 
       		        return;
        		worker = new CalcSizesEngine( handler, list ); 
        		worker.start();
        	}
		} 
        catch(Exception e) {
		}
	}
	class CalcSizesEngine extends Engine {
		private FileEx[] mList;
        protected int  num = 0, dirs = 0, depth = 0;
        
        CalcSizesEngine( Handler h, FileEx[] list ) {
        	super( h );
            mList = list;
        }
        @Override
        public void run() {
        	try {
				long sum = getSizes( mList );
				if( sum < 0 ) {
				    sendProgress( "Interrupted", Commander.OPERATION_FAILED );
				    return;
				}
				if( (mode & MODE_SORTING) == SORT_SIZE )
    				synchronized( items ) {
        	            FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING );
        	            Arrays.sort( items, comp );
    				}				
				String result;
				if( mList.length == 1 ) {
					File f = mList[0].f;
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
    	protected final long getSizes( FileEx[] list ) throws Exception {
    	    long count = 0;
    		for( int i = 0; i < list.length; i++ ) {
                if( stop || isInterrupted() ) return -1;
    			FileEx f = list[i];
    			if( f.f.isDirectory() ) {
    				dirs++;
    				if( depth++ > 20 )
    					throw new Exception( commander.getContext().getString( R.string.too_deep_hierarchy ) );
    				File[] subfiles = f.f.listFiles();
    				int l = subfiles.length;
    				FileEx[] subfiles_ex = new FileEx[l];
    				for( int j = 0; j < l; j++ )
    				    subfiles_ex[j] = new FileEx( subfiles[j] ); 
    				long sz = getSizes( subfiles_ex );
    				if( sz < 0 ) return -1;
    				f.size = sz;
    				count += f.size;
    				depth--;
    			}
    			else {
    				num++;
    				count += f.f.length();
    			}
    		}
    		return count;
    	}
    }
    @Override
    public boolean renameItem( int position, String newName ) {
        if( position <= 0 || position > items.length )
            return false;
        try {
            return items[position - 1].f.renameTo( new File( dirName, newName ) );
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
                        File f = items[pos].f;
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
                if( worker != null && worker.reqStop() ) 
                    return false;
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
        if( worker != null ) {
        	//Toast.makeText( commander.getContext(), "Terminating...", Toast.LENGTH_SHORT ).show();
            worker.reqStop();
        }
    }

    @Override
	public void prepareToDestroy() {
		items = null;
	}

    class CopyEngine extends CalcSizesEngine {
        String  mDest;
        int     counter = 0;
        long    bytes = 0;
        double  conv;
        File[]  fList = null;
        
        CopyEngine( Handler h, File[] list, String dest ) {
        	super( h, null );
        	fList = list;
            mDest = dest;
        }
        @Override
        public void run() {
        	sendProgress( commander.getContext().getString( R.string.preparing ), 0, 0 );
        	try {
                int l = fList.length;
                FileEx[] x_list = new FileEx[l];
                for( int j = 0; j < l; j++ )
                    x_list[j] = new FileEx( fList[j] ); 
				long sum = getSizes( x_list );
				conv = 100 / (double)sum;
	            String report = Utils.getCopyReport( copyFiles( fList, mDest ) );
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
            commander.showError( "bitsToNames()'s : " + e );
        }
        return null;
    }
    private final FileEx[] bitsToFiles( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            FileEx[] res = new FileEx[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        res[j++] = items[ k - 1 ];
                }
            return res;
        } catch( Exception e ) {
            commander.showError( "bitsToFiles()'s : " + e );
        }
        return null;
    }

    /*
     *  ListAdapter implementation
     */
    
    @Override
    public int getCount() {
//        showMessage( "getCount called: " + getCountCounter++ );
        if( items == null )
            return 1;
        return items.length + 1;
    }

    @Override
    public Object getItem( int position ) {
        return items != null && position > 0 && position <= items.length ? items[position - 1].f : new Object();
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
        	if( items != null && position-1 < items.length ) {
        	    synchronized( items ) {
                    FileEx f = items[position - 1];
                    try {
                        item.name = f.f.getName();
                        item.dir  = f.f.isDirectory();
                        item.size = item.dir ? f.size : f.f.length();
                        ListView flv = (ListView)parent;
                        SparseBooleanArray cis = flv.getCheckedItemPositions();
                        item.sel = cis.get( position );
                        long msFileDate = f.f.lastModified();
                        if( msFileDate != 0 )
                            item.date = new Date( msFileDate );
                    } catch( Exception e ) {
                        System.err.print("getView() exception: " + e );
                    }
                }
            }
            else
            	item.name = "";
        }
        return getView( convertView, parent, item );
    }

    public class FilePropComparator implements Comparator<FileEx> {
        int type;

        public FilePropComparator( int type_ ) {
            type = type_;
        }
        public int compare( FileEx f1, FileEx f2 ) {
            boolean f1IsDir = f1.f.isDirectory();
            boolean f2IsDir = f2.f.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            if( type == SORT_NAME )
                return f1.f.compareTo( f2.f );
            if( type == SORT_SIZE ) {
                return f1IsDir ? (int)(f1.size - f2.size )
                               : (int)(f1.f.length() - f2.f.length());
            }
            if( type == SORT_DATE )
                return (int)(f1.f.lastModified() - f2.f.lastModified() );
            return 0;
        }
    }
}
