package com.ghostsq.commander;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;

public class FSAdapter extends CommanderAdapterBase {
    private   final static String TAG = "FSAdapter";
    class FileItem extends Item {
        public File f = null;
        public long size = -1;
        public FileItem( String name ) {
            f = new File( name );
            origin = f;
        }
        public FileItem( File f_ ) {
            f = f_;
            origin = f;
        }
    }

    private String     dirName;
    private FileItem[] items;
    
    ThumbnailsThread tht = null;
    public static final Map<Integer, SoftReference<Drawable> > thumbnailCache = new HashMap<Integer, SoftReference<Drawable> >();

    public static int[] ext_h = { 
             ".jpg".hashCode(),  ".JPG".hashCode(), 
             ".jpeg".hashCode(), ".JPEG".hashCode(), 
             ".png".hashCode(),  ".PNG".hashCode(), 
             ".gif".hashCode(),  ".GIF".hashCode() 
         };
    
    public FSAdapter( Commander c ) {
        super( c, 0 );
        dirName = null;
        items = null;
    }
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
                    commander.notifyMe( new Commander.Notify( "invalid path", Commander.OPERATION_FAILED ) );
                    Log.e( TAG, "Unable to obtain folder of the folder name" );
                    return false;
                }
                dir = new File( dir_name );
                files_ = dir.listFiles();
                if( files_ != null ) break;
                if( err_msg == null )
                    err_msg = commander.getContext().getString( R.string.no_such_folder, dir_name );
                String parent_path;
                if( dir == null || ( parent_path = dir.getParent() ) == null || ( d = Uri.parse( parent_path ) ) == null ) {
                    commander.notifyMe( new Commander.Notify( "invalid path", Commander.OPERATION_FAILED ) );
                    Log.e( TAG, "Wrong folder '" + dir_name + "'" );
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
            items = new FileItem[num];
            int j = 0;
            for( int i = 0; i < num_files; i++ ) {
                if( !hide || !files_[i].isHidden() ) {
                    FileItem file_ex = new FileItem( files_[i] );
                    items[j++] = file_ex;
                }
            }
            reSort();
            parentLink = dir.getParent() == null ? SLS : PLS;
            notifyDataSetChanged();
            if( thumbnail_size_perc > 0 ) {
                if( tht != null )
                    tht.interrupt();
                tht = new ThumbnailsThread( new Handler() {
                    public void handleMessage( Message msg ) {
                        notifyDataSetChanged();
                    } }, items );
                tht.start();
            }
            commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pass_back_on_done ) );
            return true;
		} catch( Exception e ) {
			Log.e( TAG, "readSource() excception", e );
		}
		return false;
    }

    class ThumbnailsThread extends Thread {
        private final static int NOTIFY_THUMBNAIL_CHANGED = 653;
        private Handler thread_handler;
        private FileItem[] mList;
        private BitmapFactory.Options options;
        private Resources res;
        int thumb_sz;
        private int[] ext_h = { ".jpg".hashCode(),  ".JPG".hashCode(), 
                               ".jpeg".hashCode(), ".JPEG".hashCode(), 
                                ".png".hashCode(),  ".PNG".hashCode(), 
                                ".gif".hashCode(),  ".GIF".hashCode() };
        ThumbnailsThread( Handler h, FileItem[] list ) {
            setName( getClass().getName() );
            thread_handler = h;
            mList = list;
        }
        @Override
        public void run() {
            try {
                if( mList == null ) return;
                thumb_sz = getImgWidth();
                options = new BitmapFactory.Options();
                res = commander.getContext().getResources();
                for( int a = 0; a < 3; a++ ) {
                    boolean succeeded = true;
                    boolean proc = false, proc_visible = false, proc_invisible = false;
                    for( int i = 0; i < mList.length ; i++ ) {
                        int n = -1;
                        for( int j = 0; j < mList.length ; j++ ) {
                            if( mList[j].need_thumb ) {
                                n = j;
                                proc_visible = true;
                                //Log.v( TAG, "A thumbnail requested ahead of time!!! " + n + ", " + mList[n].f.getName() );
                                break;
                            }
                        }
                        proc_invisible = n < 0;
                        if( proc_invisible )
                            n = i;
                        else
                            i--;
                        if( !proc_visible )
                            sleep( 1 );
                        FileItem f = mList[n];
                        f.need_thumb = false;
                        if( f.thumbnail != null ) continue;
                        String fn = f.f.getAbsolutePath();
                        String ext = Utils.getFileExt( fn );
                        if( ext == null ) continue;
                        int ext_hash = ext.hashCode(), ht_sz = ext_h.length;
                        boolean not_img = true;
                        for( int j = 0; j < ht_sz; j++ ) {
                            if( ext_hash == ext_h[j] ) {
                                not_img = false;
                                break;
                            }
                        }
                        if( not_img ) {
                            f.no_thumb = true;
                            continue;
                        }
                        int fn_h = fn.hashCode();
                        SoftReference<Drawable> cached_soft = null;
                        synchronized( thumbnailCache ) {
                            cached_soft = thumbnailCache.get( fn_h );
                        }
                        if( cached_soft != null )
                            f.thumbnail = cached_soft.get();
                        if( f.thumbnail == null ) {
                            //Log.v( TAG, "Creating a thumbnail for " + n + ", " + fn );
                            if( createThubnail( fn, f ) )
                                synchronized( thumbnailCache ) {
                                    thumbnailCache.put( fn_h, new SoftReference<Drawable>( f.thumbnail ) );
                                }
                            else
                                succeeded = false;
                        }
                        proc = true;
                        if( f.thumbnail != null && proc_visible && proc_invisible ) {
                            //Log.v( TAG, "Time to refresh!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" );
                            Message msg = thread_handler.obtainMessage( NOTIFY_THUMBNAIL_CHANGED );
                            msg.sendToTarget();
                            yield();
                            proc_visible = false;
                            proc = false;
                        }
                    }
                    if( proc ) {
                        Message msg = thread_handler.obtainMessage( NOTIFY_THUMBNAIL_CHANGED );
                        msg.sendToTarget();
                    }
                    if( succeeded ) break;
                }
            } catch( Exception e ) {
                Log.e( TAG, "ThumbnailsThread.run()", e );
            }
        }
        
        private boolean createThubnailO( String fn, FileItem f ) {
            options.inSampleSize = 64;
            Bitmap bitmap = (f.f.length() > 100000) ?
                         BitmapFactory.decodeFile( fn, options ) : 
                         Bitmap.createScaledBitmap( BitmapFactory.decodeFile( fn ),
                              thumb_sz, thumb_sz, false );
                                
             BitmapDrawable drawable = new BitmapDrawable( res, bitmap );
             drawable.setGravity( Gravity.CENTER );
             drawable.setBounds( 0, 0, 60, 60 );
             f.thumbnail = drawable;
             return true;
        }
        
        private boolean createThubnail( String fn, FileItem f ) {
            try {
                options.inSampleSize = 1;
                options.inJustDecodeBounds = true;
                options.outWidth = 0;
                options.outHeight = 0;
                BitmapFactory.decodeFile( fn, options );
                if( options.outWidth > 0 && options.outHeight > 0 ) {
                    int greatest = Math.max( options.outWidth, options.outHeight );
                    int factor = greatest / thumb_sz;
                    int b;
                    for( b = 0x8000000; b > 0; b >>= 1 )
                        if( b < factor ) break;
                    options.inSampleSize = b;
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeFile( fn, options );
                    if( bitmap != null ) {
                        BitmapDrawable drawable = new BitmapDrawable( res, bitmap );
                        drawable.setGravity( Gravity.CENTER );
                        drawable.setBounds( 0, 0, 60, 60 );
                        f.thumbnail = drawable;
                        return true;
                    }
                }
                Log.e( TAG, "createThubnail() failed for " + fn );
            } catch( RuntimeException rte ) {
                Log.e( TAG, "createThubnail()", rte );
            } catch( Error err ) {
                Log.e( TAG, "createThubnail()", err );
            }
            return false;
        }
        
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            if( dirName == null ) return;
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
        	FileItem[] list = bitsToFilesEx( cis );
    		if( worker != null && worker.reqStop() )
   		        return;
    		commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
    		worker = new CalcSizesEngine( handler, list );
       		worker.start();
		}
        catch(Exception e) {
		}
	}
	class CalcSizesEngine extends Engine {
		private FileItem[] mList;
        protected int  num = 0, dirs = 0, depth = 0;

        CalcSizesEngine( Handler h, FileItem[] list ) {
        	super( h );
            mList = list;
        }
        @Override
        public void run() {
        	try {
                StringBuffer result = new StringBuffer( );
        	    if( mList != null && mList.length > 0 ) {
    				long sum = getSizes( mList );
    				if( sum < 0 ) {
    				    sendProgress( "Interrupted", Commander.OPERATION_FAILED );
    				    return;
    				}
    				if( (mode & MODE_SORTING) == SORT_SIZE )
        				synchronized( items ) {
            	            FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
            	            Arrays.sort( items, comp );
        				}
                    if( mList.length == 1 ) {
                        File f = mList[0].f;
                        if( f.isDirectory() )
                            result.append( "Folder \"" + f.getAbsolutePath() + "\":\n" + num + " files," );
                        else
                            result.append( "File \"" + f.getAbsolutePath() + "\":" );
                    } else
                        result.append( "" + num + " files:" );
                    result.append( "\n" + Utils.getHumanSize(sum) + "bytes" );
                    if( sum > 1024 )
                        result.append( "\n ( " + sum + " bytes )" );
                    if( dirs > 0 )
                        result.append( ",\nin " + dirs + " director" + ( dirs > 1 ? "ies." : "y.") );
                    if( mList.length == 1 ) {
                        result.append( "\nLast modified:\n  " );
                        result.append( (String)DateFormat.format( "MMM dd yyyy hh:mm:ss", new Date( mList[0].f.lastModified() ) ) );
                    }
                    result.append( "\n\n" );
        	    }
                StatFs stat = new StatFs( dirName );
                long block_size = stat.getBlockSize( );
                result.append( "total: " + Utils.getHumanSize( stat.getBlockCount() * block_size ) +
                 "bytes,\nfree: " + Utils.getHumanSize( stat.getAvailableBlocks() * block_size ) + "bytes." );
                
				sendProgress( result.toString(), Commander.OPERATION_COMPLETED );
			} catch( Exception e ) {
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
			}
        }
    	protected final long getSizes( FileItem[] list ) throws Exception {
    	    long count = 0;
    		for( int i = 0; i < list.length; i++ ) {
                if( stop || isInterrupted() ) return -1;
    			FileItem f = list[i];
    			if( f.f.isDirectory() ) {
    				dirs++;
    				if( depth++ > 20 )
    					throw new Exception( commander.getContext().getString( R.string.too_deep_hierarchy ) );
    				File[] subfiles = f.f.listFiles();
    				int l = subfiles.length;
    				FileItem[] subfiles_ex = new FileItem[l];
    				for( int j = 0; j < l; j++ )
    				    subfiles_ex[j] = new FileItem( subfiles[j] );
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
        	FileItem[] list = bitsToFilesEx( cis );
        	if( list != null ) {
        		if( worker != null && worker.reqStop() ) {
        		    commander.notifyMe( new Commander.Notify( commander.getContext().getString( R.string.wait ), 
        		            Commander.OPERATION_FAILED ) );
       		        return false;
        		}
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

        DeleteEngine( Handler h, FileItem[] list ) {
        	super( h );
            mList = new File[list.length];
            for( int i = 0; i < list.length; i++ )
                mList[i] = list[i].f;
        }
        @Override
        public void run() {
            try {
                int cnt = deleteFiles( mList );
                sendResult( Utils.getOpReport( commander.getContext(), cnt, R.string.deleted ) );
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
            return to.receiveItems( bitsToNames( cis ), move ? MODE_MOVE : MODE_COPY );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
            if( uris == null || uris.length == 0 )
            	return false;
            if( ( move_mode & MODE_MOVE ) != 0 ) {
                boolean res = moveFiles( uris, dirName );
                if( ( move_mode & MODE_DEL_SRC_DIR ) != 0 ) {
                    File src_dir = new File( uris[0] ).getParentFile();
                    if( src_dir != null )
                        src_dir.delete();
                }                    
            	return res;
            }
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
                if( worker != null && worker.reqStop() ) {
                    commander.notifyMe( new Commander.Notify( commander.getContext().getString( R.string.wait ), 
                            Commander.OPERATION_FAILED ) );
                    return false;
                }
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
        if( tht != null )
            tht.interrupt();
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
                FileItem[] x_list = new FileItem[l];
                for( int j = 0; j < l; j++ )
                    x_list[j] = new FileItem( fList[j] );
				long sum = getSizes( x_list );
				conv = 100 / (double)sum;
	            String report = Utils.getOpReport( commander.getContext(), copyFiles( fList, mDest ), R.string.copied );
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
                    long last_modified = file.lastModified();
                    outFile = new File( dest, file.getName() );
                    if( outFile.exists() ) {
                        errMsg = (outFile.isDirectory() ? "Directory '" : "File '") + outFile.getAbsolutePath() + "' already exist.";
                        break; // TODO: ask user about overwrite
                    }
                    if( file.isDirectory() ) {
                        if( depth++ > 40 )
                            throw new Exception( commander.getContext().getString( R.string.too_deep_hierarchy ) );
                        
                        if( outFile.mkdir() ) {
                            copyFiles( file.listFiles(), outFile.getAbsolutePath() );
                            if( errMsg != null )
                            	break;
                        }
                        else
                            errMsg = "Unable to create directory '" + outFile.getAbsolutePath() + "' ";
                        depth--;
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
                    outFile.setLastModified( last_modified );
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
            long last_modified = f.lastModified();
            File dest = new File( dest_folder, f.getName() );
            if( !f.renameTo( dest ) ) {
                commander.showError( "Unable to move file '" + f.getAbsolutePath() + "'" );
                return false;
            }
            dest.setLastModified( last_modified );
        }
        return true;
    }
    private final FileItem[] bitsToFilesEx( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            FileItem[] res = new FileItem[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        res[j++] = items[ k - 1 ];
                }
            return res;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToFilesEx()", e );
        }
        return null;
    }

    public final File[] bitsToFiles( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            File[] res = new File[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        res[j++] = items[ k - 1 ].f;
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
        Item item = null;
        if( position == 0 ) {
            item = new Item();
            item.name = parentLink;
            item.dir = true;
        }
        else {
            if( items != null && position-1 < items.length ) {
                synchronized( items ) {
                    try {
                        FileItem f = items[position - 1];
                        item = f;
                        //item.origin = f.f;
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
                        //Log.v( TAG, "getItem(" + (position-1) + ") for " + item.name ); // DEBUG!!!
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

    public class FilePropComparator implements Comparator<FileItem> {
        int     type;
        boolean case_ignore, ascending;

        public FilePropComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            type = type_;
            case_ignore = case_ignore_;
            ascending = ascending_;
        }
        public int compare( FileItem f1, FileItem f2 ) {
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
            if( ext_cmp == 0 )
                ext_cmp = case_ignore ? f1.f.getName().compareToIgnoreCase( f2.f.getName() ) : f1.f.compareTo( f2.f );
            return ascending ? ext_cmp : -ext_cmp;
        }
    }

    @Override
    protected void reSort() {
        if( items == null ) return;
        FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items, comp );
    }
}
