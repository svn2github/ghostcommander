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
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class FSAdapter extends CommanderAdapterBase {
    private   final static String TAG = "FSAdapter";
    protected final static String DEFAULT_DIR = "/sdcard";
    class FileEx  {
        public File f = null;
        public long size = -1;
        public FileEx( String name ) {
            f = new File( name );
        }
        public FileEx( File f_ ) {
            f = f_;
        }
    }

    private String dirName;
    private FileEx[] items;

    public FSAdapter( Commander c, Uri d, int mode_ ) {
    	super( c, mode_ );
    	dirName = d == null ? DEFAULT_DIR : d.getPath();
        items = null;
    }
    @Override
    public String getType() {
        return "file";
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
    public boolean readSource( Uri d, String pass_back_on_done ) {
    	try {
    	    if( worker != null ) worker.reqStop();
            File[] files_ = null; 
            String dir_name = null;
            File dir = null;
            String err_msg = null;
            
            while( true ) {
                if( d != null )
                    dir_name = d.getPath();
                else
                    dir_name = dirName;
                if( dir_name == null ) {
                    commander.notifyMe( new Commander.Notify( "Fatal error", Commander.OPERATION_FAILED ) );
                    Log.e( TAG, "Unable to obtain folder of the folder name" );
                    return false;
                }
                dir = new File( dir_name );
                files_ = dir.listFiles();
                if( files_ != null ) break;
                if( err_msg == null )
                    err_msg = commander.getContext().getString( R.string.no_such_folder, dir_name );
                d = Uri.parse( dir.getParent() );
                if( d == null ) {
                    commander.notifyMe( new Commander.Notify( "Fatal error", Commander.OPERATION_FAILED ) );
                    Log.e( TAG, "Unable to calculate the parent folder of the folder '" + dir_name + "'" );
                    return false;
                }
            }
            
            dirName = dir_name;
            int num_files = files_.length;
            int num = num_files;
            boolean hide = ( mode & MODE_HIDDEN ) == HIDE_MODE;
            if( hide ) {
                int cnt = 0;
                for( int i = 0; i < num_files; i++ )
                	if( !files_[i].isHidden() ) cnt++;
                num = cnt;
            }
            items = new FileEx[num];
            int j = 0;
            for( int i = 0; i < num_files; i++ ) {
            	if( !hide || !files_[i].isHidden() )
            		items[j++] = new FileEx( files_[i] );
            }
            parentLink = dir.getParent() == null ? SLS : "..";

            FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
            Arrays.sort( items, comp );

            if( err_msg != null )
                commander.notifyMe( new Commander.Notify( err_msg, Commander.OPERATION_FAILED ) );
            commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pass_back_on_done ) );
            return true;
		} catch( Exception e ) {
			Log.e( TAG, "readSource() excception", e );
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
                
                String full_path = ( dirName + file.getName() + File.separatorChar ).replaceAll( "#", "%23" );
                commander.Navigate( Uri.parse( full_path ), null );
            }
            else {
                String ext = Utils.getFileExt( file.getName() );
                if( ext != null && ext.compareToIgnoreCase( ".zip" ) == 0 )
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
        		commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
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
        	            FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
        	            Arrays.sort( items, comp );
    				}
				String result;
				if( mList.length == 1 ) {
					File f = mList[0].f;
					if( f.isDirectory() )
						result = "Folder \"" + f.getAbsolutePath() + "\":\n" + num + " files,";
					else
						result = "File \"" + f.getAbsolutePath() + "\":";
				} else
					result = "" + num + " files:";
				result += "\n" + Utils.getHumanSize(sum) + "bytes";
				if( sum > 1024 )
				    result += "\n ( " + sum + " bytes )";
				if( dirs > 0 )
					result += ",\nin " + dirs + " director" + ( dirs > 1 ? "ies." : "y.");
				if( mList.length == 1 ) {
				    result += "\nLast modified:\n  " +
				        (String)DateFormat.format( "MMM dd yyyy hh:mm:ss", new Date( mList[0].f.lastModified() ) );
				}
				sendProgress( result, Commander.OPERATION_COMPLETED );
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
            boolean ok = items[position - 1].f.renameTo( new File( dirName, newName ) );
            commander.notifyMe( new Commander.Notify( null, ok ? Commander.OPERATION_COMPLETED_REFRESH_REQUIRED : 
                                                                 Commander.OPERATION_FAILED ) );
            return ok;
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
			boolean ok = f.createNewFile();
			commander.notifyMe( new Commander.Notify( null, ok ? Commander.OPERATION_COMPLETED_REFRESH_REQUIRED :
			                                                     Commander.OPERATION_FAILED ) );
			return ok;     
		} catch( Exception e ) {
			commander.showError( "Unable to create file \"" + fileURI + "\", " + e.getMessage() );
		}
		return false;
	}
    @Override
    public void createFolder( String new_name ) {
        
        try {
            if( (new File( dirName, new_name )).mkdir() ) {
                commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED ) );
                return;
            }
        } catch( Exception e ) {
            Log.e( TAG, "createFolder", e );
        }
        commander.notifyMe( new Commander.Notify( "Unable to create directory '" + new_name + "' in '" + dirName + "'", Commander.OPERATION_FAILED ) );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
    	try {
        	FileEx[] list = bitsToFiles( cis );
        	if( list != null ) {
        		if( worker != null && worker.reqStop() )
       		        return false;
        		commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
        		worker = new DeleteEngine( handler, list );
        		worker.start();
        	}
		} catch( Exception e ) {
		    commander.notifyMe( new Commander.Notify( e.getMessage(), Commander.OPERATION_FAILED ) );
		}
        return false;
    }

	class DeleteEngine extends Engine {
		private File[] mList;

        DeleteEngine( Handler h, FileEx[] list ) {
        	super( h );
            mList = new File[list.length];
            for( int i = 0; i < list.length; i++ )
                mList[i] = list[i].f;
        }
        @Override
        public void run() {
            try {
                int cnt = deleteFiles( mList );
                sendResult( Utils.getOpReport( cnt, "deleted" ) );
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
            }
        }
        private final int deleteFiles( File[] l ) throws Exception {
    	    if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num; 
            for( int i = 0; i < num; i++ ) {
                if( stop || isInterrupted() )
                    throw new Exception( "Interrupted" );
                File f = l[i];
                sendProgress( "Deleting " + f.getAbsolutePath(), (int)(cnt * conv) );
                if( f.isDirectory() )
                    cnt += deleteFiles( f.listFiles() );
                if( f.delete() )
                    cnt++;
            }
            return cnt;
        }
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( move && to instanceof FSAdapter ) {
            try {
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                String dest_folder = to.toString();
                String[] files_to_move = bitsToNames( cis );
                if( files_to_move == null )
                	return false;
                boolean ok = moveFiles( files_to_move, dest_folder );
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_COMPLETED_REFRESH_REQUIRED ) );
                return ok;
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
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
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
	public void prepareToDestroy() {
        super.prepareToDestroy();
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
            Log.e( TAG, "bitsToFiles()", e );
        }
        return null;
    }

    /*
     *  ListAdapter implementation
     */

    @Override
    public int getCount() {
        if( items == null )
            return 1;
        return items.length + 1;
    }

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        if( position == 0 ) {
            item.name = parentLink;
            item.dir = true;
        }
        else {
            if( items != null && position-1 < items.length ) {
                synchronized( items ) {
                    try {
                        FileEx f = items[position - 1];
                        item.origin = f.f;
                        item.dir  = f.f.isDirectory();
                        if( item.dir ) {
                            if( ( mode & MODE_ICONS ) == ICON_MODE )  
                                item.name = f.f.getName() + SLS;
                            else
                                item.name = SLS + f.f.getName();
                        } else
                            item.name = f.f.getName();
                        item.size = item.dir ? f.size : f.f.length();
                        long msFileDate = f.f.lastModified();
                        if( msFileDate != 0 )
                            item.date = new Date( msFileDate );
                    } catch( Exception e ) {
                        Log.e( TAG, "getView() exception ", e );
                    }
                }
            }
            else
                item.name = "";
        }
        return item;
    }

    public class FilePropComparator implements Comparator<FileEx> {
        int     type;
        boolean case_ignore;

        public FilePropComparator( int type_, boolean case_ignore_ ) {
            type = type_;
            case_ignore = case_ignore_;
        }
        public int compare( FileEx f1, FileEx f2 ) {
            boolean f1IsDir = f1.f.isDirectory();
            boolean f2IsDir = f2.f.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            int ext_cmp = 0;
            switch( type ) { 
            case SORT_EXT:
                ext_cmp = case_ignore ? Utils.getFileExt( f1.f.getName() ).compareToIgnoreCase( Utils.getFileExt( f2.f.getName() ) ) 
                                      : Utils.getFileExt( f1.f.getName() ).compareTo( Utils.getFileExt( f2.f.getName() ) );
                break;
            case SORT_SIZE:
                ext_cmp = ( f1IsDir ? f1.size - f2.size
                                    : f1.f.length() - f2.f.length() ) < 0 ? -1 : 1;
                break;
            case SORT_DATE:
                ext_cmp = f1.f.lastModified() - f2.f.lastModified() < 0 ? -1 : 1;
                break;
            }
            if( ext_cmp != 0 )
                return ext_cmp;
            return case_ignore ? f1.f.getName().compareToIgnoreCase( f2.f.getName() ) : f1.f.compareTo( f2.f );
        }
    }
}
