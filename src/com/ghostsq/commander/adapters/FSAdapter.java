package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedByInterruptException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

public class FSAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private   final static String TAG = "FSAdapter";
    class FileItem extends Item {
        public FileItem( String name ) {
            this( new File( name ) );
        }
        public FileItem( File f ) {
            origin = f;
            
            dir  = f.isDirectory();
            if( dir ) {
                /*
                if( ( mode & ICON_MODE ) == ICON_MODE )  
                    item.name = f.f.getName() + SLS;
                else
                */
                name = SLS + f.getName();
            } else {
                name = f.getName();
                size = f.length();
            }
            long msFileDate = f.lastModified();
            if( msFileDate != 0 )
                date = new Date( msFileDate );
        }
        public final File f() {
            return origin != null ? (File)origin : null;
        }
    }

    private String     dirName;
    protected FileItem[] items;
    
    ThumbnailsThread tht = null;
    
    public FSAdapter( Context ctx_ ) {
        super( ctx_ );
        dirName = null;
        items = null;
    }

    @Override
    public String getScheme() {
        return "";
    }
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case FS:
        case LOCAL:
        case REAL:
        case SF4:
        case SEARCH:
        case SEND:
            return true;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        return Utils.mbAddSl( dirName );
    }

    /*
     * CommanderAdapter implementation
     */

    @Override
    public Uri getUri() {
        try {
            return Uri.parse( Utils.escapePath( toString() ) );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setUri( Uri uri ) {
        dirName = Utils.mbAddSl( uri.toString() );
    }
    
    @Override
    public boolean readSource( Uri d, String pass_back_on_done ) {
    	try {
    	    //if( worker != null ) worker.reqStop();
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
                    notify( s( R.string.inv_path ) + ": " + ( d == null ? "null" : d.toString() ), Commander.OPERATION_FAILED );
                    Log.e( TAG, "Unable to obtain folder of the folder name" );
                    return false;
                }
                //Log.v( TAG, "readSource() path=" + dir_name );                
                dir = new File( dir_name );
                files_ = dir.listFiles();
                if( files_ != null ) break;
                if( err_msg == null )
                    err_msg = ctx.getString( R.string.no_such_folder, dir_name );
                String parent_path;
                if( dir == null || ( parent_path = dir.getParent() ) == null || ( d = Uri.parse( parent_path ) ) == null ) {
                    notify( s( R.string.inv_path ), Commander.OPERATION_FAILED );
                    Log.e( TAG, "Wrong folder '" + dir_name + "'" );
                    return false;
                }
            }
            dirName = dir_name;
            items = filesToItems( files_ );
            parentLink = dir.getParent() == null ? SLS : PLS;
            notifyDataSetChanged();
            startThumbnailCreation();
            notify( pass_back_on_done );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "readSource() excception", e );
        } catch( OutOfMemoryError err ) {
            Log.e( TAG, "Out Of Memory", err );
            notify( s( R.string.oom_err ), Commander.OPERATION_FAILED );
		}
		return false;
    }

    protected void startThumbnailCreation() {
        if( thumbnail_size_perc > 0 ) {
            //Log.i( TAG, "thumbnails " + thumbnail_size_perc );
            if( tht != null )
                tht.interrupt();
            tht = new ThumbnailsThread( this, new Handler() {
                public void handleMessage( Message msg ) {
                    notifyDataSetChanged();
                } }, dirName, items );
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
    
    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            if( acmi.position != 0 ) {
                Item item = (Item)getItem( acmi.position );
                if( !item.dir && ".zip".equals( Utils.getFileExt( item.name ) ) )
                    menu.add( 0, R.id.open, 0, R.string.open );
            }
            super.populateContextMenu( menu, acmi, num );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
    }
        
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            if( parentLink == SLS ) 
                commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
            else {
                if( dirName == null ) return;
                File cur_dir_file = new File( dirName );
                String parent_dir = cur_dir_file.getParent();
                commander.Navigate( Uri.parse( Utils.escapePath( parent_dir != null ? parent_dir : DEFAULT_DIR ) ), null,
                                    cur_dir_file.getName() );
            }
        }
        else {
            File file = items[position - 1].f();
            if( file == null ) return;
            Uri open_uri = Uri.parse( Utils.escapePath( file.getAbsolutePath() ) );
            if( file.isDirectory() )
                commander.Navigate( open_uri, null, null );
            else
                commander.Open( open_uri, null );
        }
    }

    @Override
    public Uri getItemUri( int position ) {
        try {
            String item_name = getItemName( position, true );
            return Uri.parse( Utils.escapePath( item_name ) );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    @Override
    public String getItemName( int position, boolean full ) {
        if( position < 0 || items == null || position > items.length )
            return position == 0 ? parentLink : null;
        if( full )
            return position == 0 ? ( new File( dirName ) ).getParent() : items[position - 1].f().getAbsolutePath();
        else {
            if( position == 0 ) return parentLink; 
            String name = items[position - 1].name;
            if( name != null )
                return name.replace( "/", "" );
        }
        return null;
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
        try {
        	FileItem[] list = bitsToFilesEx( cis );
    		notify( Commander.OPERATION_STARTED );
    		commander.startEngine( new CalcSizesEngine( list ) );
		}
        catch(Exception e) {
		}
	}
	class CalcSizesEngine extends Engine {
		private FileItem[] mList;
        protected int  num = 0, dirs = 0, depth = 0;

        CalcSizesEngine( FileItem[] list ) {
            mList = list;
            setName( ".CalcSizesEngine" );
        }
        @Override
        public void run() {
        	try {
        	    Init( null );
        	    Context c = ctx;
                StringBuffer result = new StringBuffer( );
        	    if( mList != null && mList.length > 0 ) {
        	        sendProgress();
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
                        Item item = mList[0];
                        if( item.dir ) {
                            result.append( c.getString( R.string.sz_folder, item.name, num ) );
                            if( dirs > 0 )
                                result.append( c.getString( R.string.sz_dirnum, dirs, ( dirs > 1 ? c.getString( R.string.sz_dirsfx_p ) : c.getString( R.string.sz_dirsfx_s ) ) ) );
                        }
                        else
                            result.append( c.getString( R.string.sz_file, item.name ) );
                    } else
                        result.append( c.getString( R.string.sz_files, num ) );
                    if( sum > 0 )
                        result.append( c.getString( R.string.sz_Nbytes, Formatter.formatFileSize( ctx, sum ).trim() ) );
                    if( sum > 1024 )
                        result.append( c.getString( R.string.sz_bytes, sum ) );
                    if( mList.length == 1 ) {
                        FileItem item = mList[0];
                        result.append( c.getString( R.string.sz_lastmod ) );
                        result.append( "&#xA0;" );
                        String date_s = Utils.formatDate( item.date, ctx );
                        result.append( date_s );
                        File f = item.f(); 
                        if( f.isFile() ) {
                            String ext  = Utils.getFileExt( item.name );
                            String mime = Utils.getMimeByExt( ext );
                            result.append( "\n" );
                            if( mime != null && !"*/*".equals( mime ) )
                                result.append( "\n<b>MIME:</b>\n&#xA0;<small>" + mime + "</small>" );
                            String[] hashes = Utils.getHash( f, new String[] { "MD5", "SHA-1" } );
                            if( hashes != null ) {
                                result.append( "\n<b>MD5:</b>\n&#xA0;<small>"   + hashes[0] + "</small>" );
                                result.append( "\n<b>SHA-1:</b>\n&#xA0;<small>" + hashes[1] + "</small>" );
                            }
                        }
                    }
                    result.append( "\n\n<hr/>" );
        	    }
                StatFs stat = new StatFs( dirName );
                long block_size = stat.getBlockSize( );
                result.append( c.getString( R.string.sz_total, Formatter.formatFileSize( ctx, stat.getBlockCount() * block_size ), 
                                                               Formatter.formatFileSize( ctx, stat.getAvailableBlocks() * block_size ) ) );
                String str = result.toString();
				sendReport( str );
			} catch( Exception e ) {
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
			}
        }
    	protected final long getSizes( FileItem[] list ) throws Exception {
    	    long count = 0;
    		for( int i = 0; i < list.length; i++ ) {
                if( isStopReq() ) return -1;
    			FileItem f = list[i];
    			if( f.dir ) {
    				dirs++;
    				if( depth++ > 20 )
    					throw new Exception( s( R.string.too_deep_hierarchy ) );
    				File[] subfiles = f.f().listFiles();
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
    				count += f.size;
    			}
    		}
    		return count;
    	}
    }
	
	private class AskEngine extends Engine {
        private String msg;
        private File   from, to;
        
	    AskEngine( Handler h_, String msg_, File from_, File to_ ) {
	        super.setHandler( h_ );
	        msg = msg_;
	        from = from_;
	        to = to_;
	    }
	    @Override
        public void run() {
            try {
                int resolution = askOnFileExist( msg, commander );
                if( ( resolution & Commander.REPLACE ) != 0 ) {
                    if( to.delete() && from.renameTo( to ) )
                        sendResult( "ok" );
                }
            } catch( InterruptedException e ) {
                e.printStackTrace();
            }
        }
    }
	
	
	@Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length )
            return false;
        try {
            if( copy ) {
                // newName could be just name
                notify( Commander.OPERATION_STARTED );
                File[] list = { items[position - 1].f() };
                String dest_name;
                if( newName.indexOf( SLC ) < 0 ) {
                    dest_name = dirName;
                    if( dest_name.charAt( dest_name.length()-1 ) != SLC )
                        dest_name += SLS;
                    dest_name += newName;
                }
                else
                    dest_name = newName;
                commander.startEngine( new CopyEngine( list, dest_name, MODE_COPY, true ) );
                return true;
            }
            boolean ok = false;
            File f = items[position - 1].f();
            File new_file = new File( dirName, newName );
            if( new_file.exists() ) {
                if( f.equals( new_file ) ) {
                    commander.showError( s( R.string.rename_err ) );
                    return false;
                }
                String old_ap =        f.getAbsolutePath();
                String new_ap = new_file.getAbsolutePath();
                if( old_ap.equalsIgnoreCase( new_ap ) ) {
                    File tmp_file = new File( dirName, newName + "_TMP_" );
                    ok = f.renameTo( tmp_file );
                    ok = tmp_file.renameTo( new_file );
                } else {
                    AskEngine ae = new AskEngine( simpleHandler, ctx.getString( R.string.file_exist, newName ), f, new_file );
                    commander.startEngine( ae );
                    //commander.showError( s( R.string.rename_err ) );
                    return true;
                }
            }
            else
                ok = f.renameTo( new_file );
            if( ok )
                notifyRefr( newName );
            else
                notify( s( R.string.error ), Commander.OPERATION_FAILED );
            return ok;
        }
        catch( SecurityException e ) {
            commander.showError( ctx.getString( R.string.sec_err, e.getMessage() ) );
            return false;
        }
    }
	
    @Override
    public Item getItem( Uri u ) {
        try {
            File f = new File( u.getPath() );
            if( f.exists() ) {
                Item item = new Item( f.getName() );
                item.size = f.length();
                item.date = new Date( f.lastModified() );
                item.dir = f.isDirectory();
                return item;
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
	
    @Override
    public InputStream getContent( Uri u, long skip ) {
        try {
            String path = u.getPath();
            File f = new File( path );
            if( f.exists() && f.isFile() ) {
                FileInputStream fis = new FileInputStream( f );
                if( skip > 0 )
                    fis.skip( skip );
                return fis;
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        if( u != null ) {
            File f = new File( u.getPath() );
            try {
                return new FileOutputStream( f );
            } catch( FileNotFoundException e ) {
                Log.e( TAG, u.getPath(), e );
            }
        }
        return null;
    }
    
	@Override
	public boolean createFile( String fileURI ) {
		try {
			File f = new File( fileURI );
			boolean ok = f.createNewFile();
			notify( null, ok ? Commander.OPERATION_COMPLETED_REFRESH_REQUIRED : Commander.OPERATION_FAILED );
			return ok;     
		} catch( Exception e ) {
		    commander.showError( ctx.getString( R.string.cant_create, fileURI, e.getMessage() ) );
		}
		return false;
	}
    @Override
    public void createFolder( String new_name ) {
        
        try {
            if( (new File( dirName, new_name )).mkdir() ) {
                notifyRefr( new_name );
                return;
            }
        } catch( Exception e ) {
            Log.e( TAG, "createFolder", e );
        }
        notify( ctx.getString( R.string.cant_md, new_name ), Commander.OPERATION_FAILED );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
    	try {
        	FileItem[] list = bitsToFilesEx( cis );
        	if( list != null ) {
        		notify( Commander.OPERATION_STARTED );
        		commander.startEngine( new DeleteEngine( list ) );
        	}
		} catch( Exception e ) {
		    notify( e.getMessage(), Commander.OPERATION_FAILED );
		}
        return false;
    }

	class DeleteEngine extends Engine {
		private File[] mList;
        private ArrayList<String> to_scan;

        DeleteEngine( FileItem[] list ) {
            this( new File[list.length] );
            for( int i = 0; i < list.length; i++ )
                mList[i] = list[i].f();
        }
        DeleteEngine( File[] list ) {
            setName( ".DeleteEngine" );
            mList = list;
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
                to_scan = new ArrayList<String>(); 
        }
        @Override
        public void run() {
            try {
                Init( null );
                int cnt = deleteFiles( mList );
                sendResult( Utils.getOpReport( ctx, cnt, R.string.deleted ) );
                if( to_scan != null && to_scan.size() > 0 ) {
                    String[] to_scan_a = new String[to_scan.size()];
                    to_scan.toArray( to_scan_a );
                    ForwardCompat.scanMedia( ctx, to_scan_a );
                }
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        private final int deleteFiles( File[] l ) throws Exception {
    	    if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num; 
            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( s( R.string.canceled ) );
                File f = l[i];
                sendProgress( ctx.getString( R.string.deleting, f.getName() ), (int)(cnt * conv) );
                if( f.isDirectory() )
                    cnt += deleteFiles( f.listFiles() );
                if( f.delete() )
                    cnt++;
                else {
                    error( ctx.getString( R.string.cant_del, f.getName() ) );
                    break;
                }
                if( to_scan != null ) {
                    String ext = Utils.getFileExt( f.getName() );
                    String mime = Utils.getMimeByExt( ext );
                    if( mime != null && ( mime.startsWith( "image/" ) || mime.startsWith( "audio/" ) || mime.startsWith( "video/" ) ) )
                        to_scan.add( f.getAbsolutePath() );
                }
            }
            return cnt;
        }
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        boolean ok = to.receiveItems( bitsToNames( cis ), move ? MODE_MOVE : MODE_COPY );
        if( !ok ) notify( Commander.OPERATION_FAILED );
        return ok;
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
            if( uris == null || uris.length == 0 )
            	return false;
            File dest_file = new File( dirName );
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
                notify( Commander.OPERATION_STARTED );
                commander.startEngine( new CopyEngine( list, dirName, move_mode, false ) );
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
        private long    totalBytes = 0;
        private double  conv;
        private File[]  fList = null;
        private boolean move, del_src_dir, destIsFullName;
        private byte[]  buf;
        private static final int BUFSZ = 524288;
        private PowerManager.WakeLock wakeLock;
        private ArrayList<String> to_scan;

        CopyEngine( File[] list, String dest, int move_mode, boolean dest_is_full_name ) {
        	super( null );
       	    setName( ".CopyEngine" );
        	fList = list;
            mDest = dest;
            move = ( move_mode & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode & MODE_DEL_SRC_DIR ) != 0;
            destIsFullName = dest_is_full_name;
            buf = new byte[BUFSZ];
                        
            PowerManager pm = (PowerManager)ctx.getSystemService( Context.POWER_SERVICE );
            wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, TAG );
            
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
                to_scan = new ArrayList<String>(); 
        }
        @Override
        public void run() {
        	sendProgress( ctx.getString( R.string.preparing ), 0, 0 );
        	try {
                int l = fList.length;
                FileItem[] x_list = new FileItem[l];
                
                File src_dir_f = null;
                boolean in_same_src = true;
                for( int j = 0; j < l; j++ ) {
                    x_list[j] = new FileItem( fList[j] );
                    if( in_same_src ) {
                        File parent_f = fList[j].getParentFile();
                        if( src_dir_f == null )
                            src_dir_f = parent_f;
                        else
                            in_same_src = src_dir_f.equals( parent_f );
                    }
                }
                wakeLock.acquire();
				long sum = getSizes( x_list );
				conv = 100 / (double)sum;
				int num = copyFiles( fList, mDest, destIsFullName );
				if( del_src_dir && in_same_src && src_dir_f != null ) {
				    File[] to_delete = new File[1];
				    to_delete[0] = src_dir_f; 
				    DeleteEngine de = new DeleteEngine( to_delete );
				    de.start();
				}
				wakeLock.release();
				// XXX: assume (move && !del_src_dir)==true when copy from app: to the FS 
	            String report = Utils.getOpReport( ctx, num, move && !del_src_dir ? R.string.moved : R.string.copied );
	            sendResult( report );
	            
                if( to_scan != null && to_scan.size() > 0 ) {
                    String[] to_scan_a = new String[to_scan.size()];
                    to_scan.toArray( to_scan_a );
                    ForwardCompat.scanMedia( ctx, to_scan_a );
                }

			} catch( Exception e ) {
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
				return;
			}
        }
        private final int copyFiles( File[] list, String dest, boolean dest_is_full_name ) throws InterruptedException {
            Context c = ctx;
            File file = null;
            for( int i = 0; i < list.length; i++ ) {
                boolean existed = false;
                InputStream  is = null;
                OutputStream os = null;
                File outFile = null;
                file = list[i];
                if( file == null ) {
                    error( c.getString( R.string.unkn_err ) );
                    break;
                }
                String uri = file.getAbsolutePath();
                try {
                    if( isStopReq() ) {
                        error( c.getString( R.string.canceled ) );
                        break;
                    }
                    long last_modified = file.lastModified();
                    String fn = file.getName();
                    outFile = dest_is_full_name ? new File( dest ) : new File( dest, fn );
                    String out_dir_path = new File( dest ).getCanonicalPath();
                    if( file.isDirectory() ) {
                        if( out_dir_path.startsWith( file.getCanonicalPath() ) ) {
                            error( ctx.getString( R.string.cant_copy_to_itself, file.getName() ) );
                            break;
                        }
                        if( depth++ > 40 ) {
                            error( ctx.getString( R.string.too_deep_hierarchy ) );
                            break;
                        }
                        else
                        if( outFile.exists() || outFile.mkdir() ) {
                            copyFiles( file.listFiles(), outFile.getAbsolutePath(), false );
                            if( errMsg != null )
                            	break;
                        }
                        else
                            error( c.getString( R.string.cant_md, outFile.getAbsolutePath() ) );
                        depth--;
                        counter++;
                    }
                    else {
                        if( existed = outFile.exists() ) {
                            int res = askOnFileExist( c.getString( R.string.file_exist, outFile.getAbsolutePath() ), commander );
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.REPLACE ) {
                                if( outFile.equals( file ) )
                                    continue;
                                else
                                    outFile.delete();
                            }
                            if( res == Commander.ABORT ) break;
                        }
                        if( move ) {    // first try to move by renaming
                            long len = file.length();
                            if( file.renameTo( outFile ) ) {
                                counter++;
                                totalBytes += len;
                                int  so_far = (int)(totalBytes * conv);
                                sendProgress( outFile.getName() + " " + c.getString( R.string.moved ), so_far, 0 );
                                continue;
                            }
                        }
                        
                        is = new FileInputStream( file );
                        os = new FileOutputStream( outFile );
                        long copied = 0, size  = file.length();
                        
                        long start_time = 0;
                        int  speed = 0;
                        int  so_far = (int)(totalBytes * conv);
                        
                        String sz_s = Utils.getHumanSize( size );
                        int fnl = fn.length();
                        String rep_s = c.getString( R.string.copying, 
                               fnl > CUT_LEN ? "\u2026" + fn.substring( fnl - CUT_LEN ) : fn );
                        int  n  = 0; 
                        long nn = 0;
                        
                        while( true ) {
                            if( nn == 0 ) {
                                start_time = System.currentTimeMillis();
                                sendProgress( rep_s + sizeOfsize( copied, sz_s ), so_far, (int)(totalBytes * conv), speed );
                            }
                            n = is.read( buf );
                            if( n < 0 ) {
                                long time_delta = System.currentTimeMillis() - start_time;
                                if( time_delta > 0 ) {
                                    speed = (int)(MILLI * nn / time_delta );
                                    sendProgress( rep_s + sizeOfsize( copied, sz_s ), so_far, (int)(totalBytes * conv), speed );
                                }
                                break;
                            }
                            os.write( buf, 0, n );
                            nn += n;
                            copied += n;
                            totalBytes += n;
                            if( isStopReq() ) {
                                Log.d( TAG, "Interrupted!" );
                                error( c.getString( R.string.canceled ) );
                                return counter;
                            }
                            long time_delta = System.currentTimeMillis() - start_time;
                            if( time_delta > DELAY ) {
                                speed = (int)(MILLI * nn / time_delta);
                                //Log.v( TAG, "bytes: " + nn + " time: " + time_delta + " speed: " + speed );
                                nn = 0;
                            }
                        }
                        is.close();
                        os.close();
                        is = null;
                        os = null;
                        
                        if( i >= list.length-1 )
                        	sendProgress( c.getString( R.string.copied_f, fn ) + sizeOfsize( copied, sz_s ), (int)(totalBytes * conv) );
                        if( to_scan != null ) {
                            String ext = Utils.getFileExt( outFile.getName() );
                            String mime = Utils.getMimeByExt( ext );
                                if( mime != null && ( mime.startsWith( "image/" ) || mime.startsWith( "audio/" ) || mime.startsWith( "video/" ) ) )
                                to_scan.add( outFile.getAbsolutePath() );
                        }
// debug only, to remove!
//Log.v( TAG, c.getString( R.string.copied_f, fn ) );
                        counter++;
                    }
                    if( move )
                        file.delete();
                    outFile.setLastModified( last_modified );
                    final int GINGERBREAD = 9;
                    if( android.os.Build.VERSION.SDK_INT >= GINGERBREAD )
                        ForwardCompat.setFullPermissions( outFile );
                }
                catch( SecurityException e ) {
                    Log.e( TAG, "", e );
                    error( c.getString( R.string.sec_err, e.getMessage() ) );
                }
                catch( FileNotFoundException e ) {
                    Log.e( TAG, "", e );
                    error( c.getString( R.string.not_accs, e.getMessage() ) );
                }
                catch( ClosedByInterruptException e ) {
                    Log.e( TAG, "", e );
                    error( c.getString( R.string.canceled ) );
                }
                catch( IOException e ) {
                    Log.e( TAG, "", e );
                    String msg = e.getMessage();
                    error( c.getString( R.string.acc_err, uri, msg != null ? msg : "" ) );
                }
                catch( RuntimeException e ) {
                    Log.e( TAG, "", e );
                    error( c.getString( R.string.rtexcept, uri, e.getMessage() ) );
                }
                finally {
                    try {
                        if( is != null )
                            is.close();
                        if( os != null )
                            os.close();
                        if( !move && errMsg != null && outFile != null && !existed ) {
                            Log.i( TAG, "Deleting failed output file" );
                            outFile.delete();
                        }
                    }
                    catch( IOException e ) {
                        error( c.getString( R.string.acc_err, uri, e.getMessage() ) );
                    }
                }
            }
            return counter;
        }
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
                        res[j++] = items[ k - 1 ].f();
                }
            return res;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToFiles()", e );
        }
        return null;
    }

    @Override
    protected int getPredictedAttributesLength() {
        return 10;   // "1024x1024"
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
                        return items[position - 1];
                    } catch( Exception e ) {
                        Log.e( TAG, "getItem(" + position + ")", e );
                    }
                }
            }
            else {
                item = new Item();
                item.name = "???";
            }
        }
        return item;
    }

    @Override
    protected void reSort() {
        reSort( items );
    }
    public void reSort( FileItem[] items_ ) {
        if( items_ == null ) return;
        ItemComparator comp = new ItemComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items_, comp );
    }

    @Override
    public IReciever getReceiver() {
        return this;
    }
}
