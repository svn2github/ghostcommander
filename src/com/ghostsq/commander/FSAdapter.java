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

import android.content.Context;
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
    protected FileItem[] items;
    
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
        notifyDataSetChanged();
        items = null;
    }
    @Override
    public String getType() {
        return "file";
    }

    @Override
    public String toString() {
        return Utils.escapeUriMarkup( dirName );
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
                    commander.notifyMe( new Commander.Notify( s( R.string.inv_path ) + ": " + ( d == null ? "null" : d.toString() ), Commander.OPERATION_FAILED ) );
                    Log.e( TAG, "Unable to obtain folder of the folder name" );
                    return false;
                }
                Log.v( TAG, "readSource() path=" + dir_name );                
                dir = new File( dir_name );
                files_ = dir.listFiles();
                if( files_ != null ) break;
                if( err_msg == null )
                    err_msg = commander.getContext().getString( R.string.no_such_folder, dir_name );
                String parent_path;
                if( dir == null || ( parent_path = dir.getParent() ) == null || ( d = Uri.parse( parent_path ) ) == null ) {
                    commander.notifyMe( new Commander.Notify( s( R.string.inv_path ), Commander.OPERATION_FAILED ) );
                    Log.e( TAG, "Wrong folder '" + dir_name + "'" );
                    return false;
                }
            }
            dirName = dir_name;
            items = filesToItems( files_ );
            parentLink = dir.getParent() == null ? SLS : PLS;
            notifyDataSetChanged();
            startThumbnailCreation();
            commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pass_back_on_done ) );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "readSource() excception", e );
        } catch( OutOfMemoryError err ) {
            Log.e( TAG, "Out Of Memory", err );
            commander.notifyMe( new Commander.Notify( s( R.string.oom_err ), Commander.OPERATION_FAILED ) );
		}
		return false;
    }

    protected void startThumbnailCreation() {
        if( thumbnail_size_perc > 0 ) {
            if( tht != null )
                tht.interrupt();
            tht = new ThumbnailsThread( new Handler() {
                public void handleMessage( Message msg ) {
                    notifyDataSetChanged();
                } }, items );
            tht.start();
        }
    }
    
    protected FileItem[] filesToItems( File[] files_ ) {
        int num_files = files_.length;
        int num = num_files;
        boolean hide = ( mode & MODE_HIDDEN ) == HIDE_MODE;
        if( hide ) {
            int cnt = 0;
            for( int i = 0; i < num_files; i++ )
                if( !files_[i].isHidden() ) cnt++;
            num = cnt;
        }
        FileItem[] items_ = new FileItem[num];
        int j = 0;
        for( int i = 0; i < num_files; i++ ) {
            if( !hide || !files_[i].isHidden() ) {
                FileItem file_ex = new FileItem( files_[i] );
                items_[j++] = file_ex;
            }
        }
        reSort( items_ );
        return items_;
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
                    boolean need_update = false, proc_visible = false, proc_invisible = false;
                    int processed = 0;
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
                        need_update = true;
                        if( f.thumbnail != null && ( processed++ > 3 || ( proc_visible && proc_invisible ) ) ) {
                            //Log.v( TAG, "Time to refresh!" );
                            Message msg = thread_handler.obtainMessage( NOTIFY_THUMBNAIL_CHANGED );
                            msg.sendToTarget();
                            yield();
                            proc_visible = false;
                            need_update = false;
                            processed = 0;
                        }
                    }
                    if( need_update ) {
                        Message msg = thread_handler.obtainMessage( NOTIFY_THUMBNAIL_CHANGED );
                        msg.sendToTarget();
                    }
                    if( succeeded ) break;
                }
            } catch( Exception e ) {
                Log.e( TAG, "ThumbnailsThread.run()", e );
            }
        }
        
        private boolean createThubnail( String fn, FileItem f ) {
            final String func_name = "createThubnail()"; 
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
                Log.e( TAG, func_name + " failed for " + fn );
            } catch( RuntimeException rte ) {
                Log.e( TAG, func_name, rte );
            } catch( Error err ) {
                Log.e( TAG, func_name, err );
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
            commander.Navigate( Uri.parse( Utils.escapeUriMarkup( parentLink != SLS ? ( parent_dir != null ? parent_dir : DEFAULT_DIR ) : SLS ) ),
                                cur_dir_file.getName() );
        }
        else {
            File file = items[position - 1].f;
            if( file == null ) return;
            if( file.isDirectory() ) {
                commander.Navigate( Uri.parse( Utils.escapeUriMarkup( file.getAbsolutePath() ) ), null );
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
    		worker = new CalcSizesEngine( workerHandler, list );
    		worker.setName( TAG + ".CalcSizesEngine" );
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
        	    Init( null );
        	    Context c = commander.getContext();
                StringBuffer result = new StringBuffer( );
        	    if( mList != null && mList.length > 0 ) {
    				long sum = getSizes( mList );
    				if( sum < 0 ) {
    				    sendProgress( "Interrupted", Commander.OPERATION_FAILED );
    				    return;
    				}
    				if( (mode & MODE_SORTING) == SORT_SIZE )
        				synchronized( items ) {
        				    reSort( items );
        				}
                    if( mList.length == 1 ) {
                        File f = mList[0].f;
                        if( f.isDirectory() )
                            result.append( c.getString( R.string.sz_folder, f.getName(), num ) );
                        else
                            result.append( c.getString( R.string.sz_file, f.getName() ) );
                    } else
                        result.append( c.getString( R.string.sz_files, num ) );
                    if( sum > 0 )
                        result.append( c.getString( R.string.sz_Nbytes, Utils.getHumanSize(sum).trim() ) );
                    if( sum > 1024 )
                        result.append( c.getString( R.string.sz_bytes, sum ) );
                    if( dirs > 0 )
                        result.append( c.getString( R.string.sz_dirnum, dirs, ( dirs > 1 ? c.getString( R.string.sz_dirsfx_p ) : c.getString( R.string.sz_dirsfx_s ) ) ) );
                    if( mList.length == 1 ) {
                        result.append( c.getString( R.string.sz_lastmod ) );
                        result.append( (String)DateFormat.format( "MMM dd yyyy hh:mm:ss", new Date( mList[0].f.lastModified() ) ) );
                    }
                    result.append( "\n\n" );
        	    }
                StatFs stat = new StatFs( dirName );
                long block_size = stat.getBlockSize( );
                result.append( c.getString( R.string.sz_total, Utils.getHumanSize( stat.getBlockCount() * block_size ), Utils.getHumanSize( stat.getAvailableBlocks() * block_size ) ) );
                
				sendProgress( result.toString(), Commander.OPERATION_COMPLETED, Commander.OPERATION_REPORT_IMPORTANT );
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
    					throw new Exception( s( R.string.too_deep_hierarchy ) );
    				File[] subfiles = f.f.listFiles();
    				if( subfiles != null ) {
        				int l = subfiles.length;
        				FileItem[] subfiles_ex = new FileItem[l];
        				for( int j = 0; j < l; j++ )
        				    subfiles_ex[j] = new FileItem( subfiles[j] );
        				long sz = getSizes( subfiles_ex );
        				if( sz < 0 ) return -1;
        				f.size = sz;
        				count += f.size;
    				}
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
            commander.showError( commander.getContext().getString( R.string.sec_err, e.getMessage() ) );
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
		    commander.showError( commander.getContext().getString( R.string.cant_create, fileURI, e.getMessage() ) );
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
        commander.notifyMe( new Commander.Notify( commander.getContext().getString( R.string.cant_md, new_name ), Commander.OPERATION_FAILED ) );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
    	try {
        	FileItem[] list = bitsToFilesEx( cis );
        	if( list != null ) {
        		if( worker != null && worker.reqStop() ) {
        		    commander.notifyMe( new Commander.Notify( s( R.string.wait ), 
        		            Commander.OPERATION_FAILED ) );
       		        return false;
        		}
        		commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
        		worker = new DeleteEngine( workerHandler, list );
        		worker.setName( TAG + ".DeleteEngine" );
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
                Init( null );
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
                sleep( 1 );
                if( stop || isInterrupted() )
                    throw new Exception( s( R.string.canceled ) );
                File f = l[i];
                sendProgress( commander.getContext().getString( R.string.deleting, f.getName() ), (int)(cnt * conv) );
                if( f.isDirectory() )
                    cnt += deleteFiles( f.listFiles() );
                if( f.delete() )
                    cnt++;
                else {
                    error( commander.getContext().getString( R.string.cant_del, f.getName() ) );
                    break;
                }
            }
            return cnt;
        }
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( move && to instanceof FSAdapter ) { // first try to move by renaming
            try {
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                String dest_folder = to.toString();
                String[] files_to_move = bitsToNames( cis );
                if( files_to_move == null )
                	return false;
                if( moveFiles( files_to_move, dest_folder ) ) {
                    commander.notifyMe( new Commander.Notify( Commander.OPERATION_COMPLETED_REFRESH_REQUIRED ) );
                    return true;
                }
                Log.e( TAG, "Moving by renaming failed" );
            }
            catch( SecurityException e ) {
                commander.showError( commander.getContext().getString( R.string.cant_move, e.getMessage() ) );
                return false;
            }
        }
        boolean ok = to.receiveItems( bitsToNames( cis ), move ? MODE_MOVE : MODE_COPY );
        if( !ok ) commander.notifyMe( new Commander.Notify( Commander.OPERATION_FAILED ) );
        return ok;
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
            if( uris == null || uris.length == 0 )
            	return false;
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
            if( list != null ) {
                if( worker != null && worker.reqStop() ) {
                    commander.notifyMe( new Commander.Notify( s( R.string.wait ), 
                            Commander.OPERATION_FAILED ) );
                    return false;
                }
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            	worker = new CopyEngine( workerHandler, list, dirName, ( move_mode & MODE_MOVE ) != 0 );
            	worker.setName( TAG + ".CopyEngine" );
            	worker.start();
	            return true;
            }
		} catch( Exception e ) {
		    e.printStackTrace();
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
        private String  mDest;
        private int     counter = 0;
        private long    bytes = 0;
        private double  conv;
        private File[]  fList = null;
        private boolean move;

        CopyEngine( Handler h, File[] list, String dest, boolean move_ ) {
        	super( h, null );
        	fList = list;
            mDest = dest;
            move = move_;
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
				int num = copyFiles( fList, mDest );
	            String report = Utils.getOpReport( commander.getContext(), num, move ? R.string.moved : R.string.copied );
	            sendResult( report );
			} catch( Exception e ) {
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
				return;
			}
        }
        private final int copyFiles( File[] list, String dest ) throws InterruptedException {
            Context c = commander.getContext();
            for( int i = 0; i < list.length; i++ ) {
                FileChannel  in = null;
                FileChannel out = null;
                File outFile = null;
                File file = list[i];
                if( file == null ) {
                    error( c.getString( R.string.unkn_err ) );
                    break;
                }
                String uri = file.getAbsolutePath();
                try {
                    if( stop || isInterrupted() ) {
                        error( c.getString( R.string.canceled ) );
                        break;
                    }
                    long last_modified = file.lastModified();
                    String fn = file.getName();
                    outFile = new File( dest, fn );
                    if( file.isDirectory() ) {
                        if( depth++ > 40 ) {
                            error( commander.getContext().getString( R.string.too_deep_hierarchy ) );
                            break;
                        }
                        else
                        if( outFile.exists() || outFile.mkdir() ) {
                            copyFiles( file.listFiles(), outFile.getAbsolutePath() );
                            if( errMsg != null )
                            	break;
                        }
                        else
                            error( c.getString( R.string.cant_md, outFile.getAbsolutePath() ) );
                        depth--;
                    }
                    else {
                        if( outFile.exists() ) {
                            int res = askOnFileExist( c.getString( R.string.file_exist, outFile.getAbsolutePath() ), commander );
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.REPLACE ) outFile.delete();
                            if( res == Commander.ABORT ) break;
                        }
                        in  = new FileInputStream( file ).getChannel();
                        out = new FileOutputStream( outFile ).getChannel();
                        long size  = in.size();
                        final long max_chunk = 524288;
                        long chunk = size > max_chunk ? max_chunk : size;
                        int  so_far = (int)(bytes * conv);
                        String rep_s = c.getString( R.string.copying, fn ); 
                        for( long start = 0; start < size; start += chunk ) {
// debug only, to remove!
//Log.v( TAG, rep_s + " " + (int)(bytes * conv) );
                        	sendProgress( rep_s, so_far, (int)(bytes * conv) );
                        	bytes += in.transferTo( start, chunk, out );
                            if( stop || isInterrupted() ) {
                                error( c.getString( R.string.canceled ) );
                                return counter;
                            }
                        }
                        in.close();
                        out.close();
                        in = null;
                        out = null;
                        if( i >= list.length-1 )
                        	sendProgress( c.getString( R.string.copied_f, fn ), (int)(bytes * conv) );
                        
// debug only, to remove!
//Log.v( TAG, c.getString( R.string.copied_f, fn ) );
                        counter++;
                    }
                    if( move )
                        file.delete();
                    outFile.setLastModified( last_modified );
                }
                catch( SecurityException e ) {
                    error( c.getString( R.string.sec_err, e.getMessage() ) );
                }
                catch( FileNotFoundException e ) {
                    error( c.getString( R.string.not_accs, e.getMessage() ) );
                }
                catch( IOException e ) {
                    String msg = e.getMessage();
                    error( c.getString( R.string.acc_err, uri, msg != null ? msg : "" ) );
                }
                catch( RuntimeException e ) {
                    error( c.getString( R.string.rtexcept, uri, e.getMessage() ) );
                }
                finally {
                    try {
                        if( in != null )
                            in.close();
                        if( out != null )
                            out.close();

                    }
                    catch( IOException e ) {
                        error( c.getString( R.string.acc_err, uri, e.getMessage() ) );
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
                            Log.e( TAG, "on deleting after failed copy", e );
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
                Log.e( TAG, commander.getContext().getString( R.string.cant_move, f.getAbsolutePath() ) );
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
            if( items != null && position <= items.length ) {
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
                        Log.e( TAG, "getItem(" + position + ")", e );
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
        reSort( items );
    }
    public void reSort( FileItem[] items_ ) {
        if( items_ == null ) return;
        FilePropComparator comp = new FilePropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items_, comp );
    }
}
