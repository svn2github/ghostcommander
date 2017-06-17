package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.FileProvider;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;
import android.system.Os;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

@SuppressLint("NewApi")
public class SAFAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private final static String TAG = "SAFAdapter";
    public  final static String ORG_SCHEME = "saf";
    public  final static int    OPEN_SAF = 9036;
    private boolean primary = false;

    class SAFItem extends CommanderAdapter.Item  implements FSEngines.IFileItem {
        @Override
        public File f() {
            Uri u = (Uri)origin;
            String path = SAFAdapter.this.getPath( u, this.dir );
            return new File( path );
        }
    }
    
    private   Uri    uri;
    protected SAFItem[] items;
    
    ThumbnailsThread tht = null;
    
    public SAFAdapter( Context ctx_ ) {
        super( ctx_ );
    }

    @Override
    public String getScheme() {
        return ContentResolver.SCHEME_CONTENT;
    }
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case FS:
        case LOCAL:
        case REAL:
        case SF4:
        case SEND:
            return true;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        return "saf:" + getPath( uri, true );
    }

    private static final String PATH_TREE = "tree";
    
    private static boolean isTreeUri( Uri uri ) {
        final List<String> paths = uri.getPathSegments();
        return paths.size() == 2 && PATH_TREE.equals(paths.get(0));
    }
    
    private static boolean isRootDoc( Uri uri ) {
        final List<String> paths = uri.getPathSegments();
        if( paths.size() < 4 ) return true;
        String last = paths.get(paths.size()-1); 
        return last.lastIndexOf( ':' ) == last.length()-1;
    }

    public static boolean isExternalStorageDocument( Uri uri ) {
        return "com.android.externalstorage.documents".equals( uri.getAuthority() );
    }

    private static boolean isPrimary( Uri u ) {
        final List<String> paths = u.getPathSegments();
        if( paths.size() < 4 ) return false;
        String path_part = paths.get( 3 );
        int col_pos = path_part.lastIndexOf( ':' );
        String volume = paths.get( 1 );
        return volume != null && volume.startsWith( "primary" );
    }

    public static String getMime( Context ctx, Uri u ) {
        Cursor c = null;
        try {
            final String[] projection = { Document.COLUMN_MIME_TYPE };
            c = ctx.getContentResolver().query( u, projection, null, null, null );
            if( c.getCount() > 0 ) {
                c.moveToFirst();
                return c.getString( 0 );
            }
        } catch( Exception e ) {
        } finally {
            if( c != null ) c.close();
        }
        return null;
    }
    
    // see https://stackoverflow.com/questions/30546441/android-open-file-with-intent-chooser-from-uri-obtained-by-storage-access-frame/31283751#31283751
    public static String getFdPath( ParcelFileDescriptor fd ) {
        final String resolved;

        try {
            final File procfsFdFile = new File( "/proc/self/fd/" + fd.getFd() );

            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
                // Returned name may be empty or "pipe:", "socket:", "(deleted)"
                // etc.
                resolved = Os.readlink( procfsFdFile.getAbsolutePath() );
            } else {
                // Returned name is usually valid or empty, but may start from
                // funny prefix if the file does not have a name
                resolved = procfsFdFile.getCanonicalPath();
            }

            if( !Utils.str( resolved ) || resolved.charAt( 0 ) != '/' || 
                    resolved.startsWith( "/proc/" ) || resolved.startsWith( "/fd/" ) )
                return null;
        } catch( IOException ioe ) {
            // This exception means, that given file DID have some name, but it is
            // too long, some of symlinks in the path were broken or, most
            // likely, one of it's directories is inaccessible for reading.
            // Either way, it is almost certainly not a pipe.
            return "";
        } catch( Exception errnoe ) {
            // Actually ErrnoException, but base type avoids VerifyError on old
            // versions
            // This exception should be VERY rare and means, that the descriptor
            // was made unavailable by some Unix magic.
            return null;
        }

        return resolved;
    }
    
    public final String getPath( Uri u, boolean dir ) {
        try {
            String fd_path = null;
            ContentResolver cr = ctx.getContentResolver();
            ParcelFileDescriptor pfd = cr.openFileDescriptor( u, "r" );
            if( pfd != null ) {
                fd_path = getFdPath( pfd );
                Log.d( TAG, "Got path: " + fd_path );
                if( Utils.str( fd_path ) && fd_path.indexOf( "media_rw" ) < 0 )
                    return fd_path;
            }
            
            final List<String> paths = u.getPathSegments();
            if( paths.size() < 4 ) return null;
            String path_part = paths.get( 3 );
            int col_pos = path_part.lastIndexOf( ':' );
            String volume, path_root = null, sub_path, full_path;
            volume = paths.get( 1 );
            sub_path = path_part.substring( col_pos+1 );
            
            if( volume.startsWith( "primary" ) )
                return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + sub_path;
            else {
                try {
                    File probe;
                    volume = volume.substring( 0, volume.length()-1 );
                    if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                        full_path = "/storage/" + volume + "/" + sub_path;
                        probe = new File( full_path );
                        if( dir ? probe.isDirectory() : probe.isFile() ) return full_path;  
                        
                        full_path = "/mnt/media_rw/" + volume + "/" + sub_path;
                        probe = new File( full_path );
                        if( dir ? probe.isDirectory() : probe.isFile() ) return full_path;  
                    } else {
                        path_root = Utils.getSecondaryStorage();
                        if( path_root != null ) {
                            full_path = Utils.mbAddSl( path_root ) + sub_path;
                            probe = new File( full_path );
                            if( dir ? probe.isDirectory() : probe.isFile() ) return full_path;
                        }
                    }
                } catch( Exception e ) {
                    Log.w( TAG, "Can't resolve uri to a path: " + u );
                }
                if( Utils.str( fd_path ) )
                    return fd_path;
                
                if( path_root == null )
                    path_root = volume; // better than nothing
            }
            return path_root + "/" + sub_path;
        } catch( Exception e ) {
            Log.e( TAG, "Can't get the real location of " + u, e );
        }
        return null;
    }

    public static Uri getParent( Uri u ) {
        if( u == null ) return null;
        final List<String> paths = u.getPathSegments();
        final int n = paths.size();
        if( n < 4 ) return null;
        StringBuffer sb = new StringBuffer();
        for( int i = 0; i < n-1; i++ ) {
            sb.append( "/" );
            sb.append( paths.get( i ) );
        }
        if( n == 4 ) {
            String last = paths.get( n-1 ); 
            int col_pos = last.lastIndexOf( ':' );
            if( !(col_pos <= 0 || col_pos == last.length()-1) ) {
                sb.append( "/" );
                sb.append( last.substring( 0, col_pos+1 ) );
                String subpath = last.substring( col_pos+1 );
                subpath = Uri.decode( subpath );
                int sl_pos = subpath.lastIndexOf( SLC );
                if( sl_pos > 0 ) {
                    subpath = subpath.substring( 0, sl_pos );
                    sb.append( Uri.encode( subpath ) );
                }
            }
            return u.buildUpon().encodedPath( sb.toString() ).build();
        }
        return null;
    }    
    
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void setUri( Uri uri_ ) {
        if( this.uri == null && isTreeUri( uri_ ) ) {
            try {
                ctx.getContentResolver().takePersistableUriPermission( uri_,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION );
            } catch( Exception e ) {
                Log.e( TAG, uri_.toString() );
            }
            this.uri = DocumentsContract.buildDocumentUriUsingTree( uri_, 
                       DocumentsContract.getTreeDocumentId( uri_ ));        
        }
        else {
            this.uri = uri_;
            this.primary = isPrimary( uri_ );
        }
    }
 
    public final ArrayList<SAFItem> getChildren( Uri u ) {
        Cursor c = null;
        try {
            try {
                ContentResolver cr = ctx.getContentResolver();
                String document_id = DocumentsContract.getDocumentId( u );
                Uri   children_uri = DocumentsContract.buildChildDocumentsUriUsingTree( u, document_id );
                //Log.d( TAG, "Children URI:" + children_uri );
                String[] projection = colIds(); 
                c = cr.query( children_uri, projection, null, null, null);
            } catch( SecurityException e ) {
                Log.w( TAG, "Security error on " + u.toString(), e );
                return null;
            } catch( Exception e ) {
                Log.e( TAG, u.toString(), e);
            }
            if( c != null ) {
              ArrayList<SAFItem>  tmp_list = new ArrayList<SAFItem>();
              if( c.getCount() == 0 ) return tmp_list; 
              int[] ii = colInds( c );
              c.moveToFirst();
              do {
                  SAFItem item = rowToItem( c, u, ii );
                  tmp_list.add( item );
              } while( c.moveToNext() );
              return tmp_list;
            }
        } catch(Exception e) {
            Log.e( TAG, "Failed cursor processing for " + u.toString(), e );
        } finally {
            if( c != null ) c.close();
        }
        return null;
    }

    private final String[] colIds() {
      final String[] projection = {
         Document.COLUMN_DOCUMENT_ID,
         Document.COLUMN_DISPLAY_NAME,
         Document.COLUMN_LAST_MODIFIED,
         Document.COLUMN_MIME_TYPE,
         Document.COLUMN_SIZE
      };
      return projection;
   }

    private final int[] colInds( Cursor c ) {
      int[] ii = new int[5];
      ii[0] = c.getColumnIndex( Document.COLUMN_DOCUMENT_ID );
      ii[1] = c.getColumnIndex( Document.COLUMN_DISPLAY_NAME );
      ii[2] = c.getColumnIndex( Document.COLUMN_SIZE );
      ii[3] = c.getColumnIndex( Document.COLUMN_MIME_TYPE );
      ii[4] = c.getColumnIndex( Document.COLUMN_LAST_MODIFIED );
      return ii;
    }
    
    private final SAFItem rowToItem( Cursor c, Uri u, int[] ii ) {
      int ici = ii[0];
      int nci = ii[1]; 
      int sci = ii[2]; 
      int mci = ii[3];
      int dci = ii[4];
        
      SAFItem item = new SAFItem();
      String id = c.getString( ici );
      item.origin = DocumentsContract.buildDocumentUriUsingTree( u, id );
      item.mime = c.getString( mci );
      item.attr = item.mime; 
      item.dir = Document.MIME_TYPE_DIR.equals( item.attr ); 
      item.name = ( item.dir ? "/" : "" ) + c.getString( nci );
      item.size = c.getLong( sci );
      item.date = new Date( c.getLong( dci ) );
      if( item.dir ) item.size = -1;
      return item;
   }
    
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
    	try {
    	    //if( worker != null ) worker.reqStop();
            if( tmp_uri != null ) {
                //Log.d( TAG, "New URI: " + tmp_uri.toString() );
                setUri( tmp_uri );
            }
            if( uri == null ) {
                Log.e( TAG, "No URI" );
                return false;
            }
            ArrayList<SAFItem> tmp_list = getChildren( uri );
            if( tmp_list == null ) {
                SAFAdapter.saveURI( ctx, null );
                commander.showError( s( R.string.nothing_to_open ) );
                commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
                return false;
            }
            items = new SAFItem[tmp_list.size()];
            tmp_list.toArray( items );
            reSort( items );
            super.setCount( items.length );
            parentLink = isRootDoc( uri ) ? SLS : PLS;
            startThumbnailCreation();
            notifyDataSetChanged();
            notify( pass_back_on_done );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "readSource() exception", e );
        } catch( OutOfMemoryError err ) {
            Log.e( TAG, "Out Of Memory", err );
            notify( s( R.string.oom_err ), Commander.OPERATION_FAILED );
		}
		return false;
    }

    protected void startThumbnailCreation() {
        if( thumbnail_size_perc <= 0 ) return;
        //Log.i( TAG, "thumbnails " + thumbnail_size_perc );
        String path = getPath( uri, true );
        if( path == null || path.charAt( 0 ) != '/' ) return;
        
        if( tht != null )
            tht.interrupt();
        
        Handler h = new Handler() {
            public void handleMessage( Message msg ) {
                notifyDataSetChanged();
            } };            
        tht = new ThumbnailsThread( this, h, Utils.mbAddSl( path ), items );
        tht.start();
    }
    
    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            if( acmi.position != 0 ) {
                Item item = (Item)getItem( acmi.position );
                if( !item.dir && ".zip".equals( Utils.getFileExt( item.name ) ) ) {
                    menu.add( 0, R.id.open, 0, R.string.open );
                    menu.add( 0, R.id.extract, 0, R.string.extract_zip );
                }
                if( item.dir && num == 1 && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
                    menu.add( 0, R.id.rescan_dir, 0, R.string.rescan );
            }
            super.populateContextMenu( menu, acmi, num );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            Uri uri_to_go = null;
            if( parentLink == SLS ) 
                uri_to_go = Uri.parse( HomeAdapter.DEFAULT_LOC );
            else {
                //0000-0000:folder%2Fsubfolder
                uri_to_go = getParent( uri );
                if( uri_to_go == null )
                    uri_to_go = Uri.parse( HomeAdapter.DEFAULT_LOC );
            }
            String pos_to = null;
            String cur_path = getPath( uri, true );
            if( cur_path != null )
                pos_to = cur_path.substring( cur_path.lastIndexOf( '/' ) ); 
            commander.Navigate( uri_to_go, null, pos_to );
        }
        else {
            Item item = items[position - 1];
            if( item.dir )
                commander.Navigate( (Uri)item.origin, null, null );
            else {
                Uri to_open = getItemOpenableUri( position );
                if( to_open == null ) {
                    Log.w( TAG, "No URI to open item " + item );
                    return;
                }
                if( "content".equals( to_open.getScheme() ) ) {
                    Intent in = new Intent( Intent.ACTION_VIEW );
                    in.setDataAndType( to_open, item.mime );
                    in.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION
                               | Intent.FLAG_GRANT_WRITE_URI_PERMISSION );
                    commander.issue( in, 0 );
                } else {
                    Log.v( TAG, "Uri:" + to_open.toString() );
                    commander.Open( to_open, null );
                }
            }
        }
    }

    public Uri getItemOpenableUri( int position ) {
        try {
            Item item = items[position - 1];
            String full_name = getItemName( position, true );
            if( full_name != null && full_name.charAt( 0 ) == '/' && full_name.indexOf( "media_rw" ) < 0 ) {
                Uri.Builder ub = new Uri.Builder();
                ub.scheme( "file" ).encodedPath( full_name );
                return ub.build();
            } else {
                Uri u = (Uri)item.origin;
                if( u == null ) return null;
                return u;
                /*
                    return FileProvider.makeURI( "SAF", (Uri)item.origin );
                */
            }
        } catch( Exception e ) {
            Log.e( TAG, "pos:" + position, e );
        }
        return null;
    }    
    
    @Override
    public Uri getItemUri( int position ) {
        try {
            return (Uri)items[position - 1].origin;
        } catch( Exception e ) {
            Log.e( TAG, "No item in the position " + position );
        }
        return null;
    }
    @Override
    public String getItemName( int position, boolean full ) {
        if( position == 0 ) return parentLink; 
        if( position < 0 || items == null || position > items.length )
            return null;
        SAFItem item = items[position - 1];
        if( full ) {
            Uri item_uri = (Uri)item.origin;
            return getPath( item_uri, item.dir );
        } else
            return item.name;
    }

    @Override
	public void reqItemsSize( SparseBooleanArray cis ) {
        try {
            SAFItem[] list = bitsToItems( cis );
            if( list != null ) {
                notify( Commander.OPERATION_STARTED );
                commander.startEngine( new FSEngines.CalcSizesEngine( this, list ) );
            }
		}
        catch(Exception e) {
		}
	}
	
	@Override
    public boolean renameItem( int position, String newName, boolean copy ) {
	    //FIXME: in what cases the copy==true?
	    ContentResolver cr = ctx.getContentResolver();
	    Item item = items[position - 1];
	    Uri new_uri = DocumentsContract.renameDocument( cr, (Uri)item.origin, newName );
	    if( new_uri == null ) return false;
	    item.origin = new_uri;
	    notifyRefr( newName );
	    return true;
    }
	
    @Override
    public Item getItem( Uri u ) {
        Cursor c = null;
        try {
            final String[] projection = colIds();
            ContentResolver cr = ctx.getContentResolver();
            c = cr.query( u, projection, null, null, null );
            if( c.getCount() == 0 ) {
                Log.e( TAG, "Can't query uri " + u );
                return null;
            }
            c.moveToFirst();
            int[] ii = colInds( c );
            SAFItem item = rowToItem( c, u, ii );
            return item;
        } catch( Exception e ) {
        } finally {
            if( c != null ) c.close();
        }
        return null;
    }
    
    public Item getItem_( Uri u ) {
        try {
            // FIXME: found the cases where this method is called from
            // should it return a real path on the content path?
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
            ContentResolver cr = ctx.getContentResolver();
            InputStream is = cr.openInputStream( u );
            if( is == null ) return null;
            if( skip > 0 )
                is.skip( skip );
            return is;
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        if( u != null ) {
            try {
                ContentResolver cr = ctx.getContentResolver();
                return cr.openOutputStream( u );
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
            Uri new_uri = DocumentsContract.createDocument( ctx.getContentResolver(), uri, Document.MIME_TYPE_DIR, new_name );
            if( new_uri != null ) {
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
        	Item[] list = bitsToItems( cis );
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
        private Item[] mList;
        private Uri dirUri;
        private ContentResolver cr;

        DeleteEngine( Item[] list ) {
            setName( ".DeleteEngine" );
            mList = list;
            dirUri = SAFAdapter.this.getUri();
        }
        @Override
        public void run() {
            try {
                Init( null );
                cr = ctx.getContentResolver();
                int cnt = deleteFiles( dirUri, mList );
                sendResult( Utils.getOpReport( ctx, cnt, R.string.deleted ) );
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        
        private final int deleteFiles( Uri dir_uri, Item[] l ) throws Exception {
            if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num;
            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( s( R.string.canceled ) );
                Item item = l[i];
                sendProgress( ctx.getString( R.string.deleting, item.name ), (int)(cnt * conv) );
                DocumentsContract.deleteDocument( cr, (Uri)item.origin );
                cnt++;
            }
            return cnt;
        }
    }
    
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( to instanceof SAFAdapter ) {
            notify( Commander.OPERATION_STARTED );
            SAFItem[] to_copy = bitsToItems( cis );
            if( to_copy == null ) return false;
            CopyBetweenEngine cbe = new CopyBetweenEngine( to_copy, to.getUri(), move );
            commander.startEngine( cbe );
            return true;
        }
        if( this.primary && !move ) {
            boolean ok = to.receiveItems( bitsToNames( cis ), move ? MODE_MOVE : MODE_COPY );
            if( !ok ) notify( Commander.OPERATION_FAILED );
            return ok;
        }
        String err_msg = null;
        try {
            SAFItem[] to_copy = bitsToItems( cis );
            if( to_copy == null ) return false;
            java.io.File dest = null;
            Engines.IReciever recipient = null;
            if( to instanceof FSAdapter ) {
                String dest_fn = to.toString();
                dest = new java.io.File( dest_fn );
                if( !dest.exists() ) dest.mkdirs();
                if( !dest.isDirectory() )
                    throw new RuntimeException( ctx.getString( Utils.RR.file_exist.r(), dest_fn ) );
            } else {
                dest = new File( createTempDir() );
                recipient = to.getReceiver();
            }
            notify( Commander.OPERATION_STARTED );
            CopyFromEngine mfe = new CopyFromEngine( to_copy, move, dest, recipient );
            commander.startEngine( mfe );
            return true;
        }
        catch( Exception e ) {
            err_msg = "Exception: " + e.getMessage();
        }
        notify( err_msg, Commander.OPERATION_FAILED );
        return false;
    }

    class CopyBetweenEngine extends Engine {  
        private Uri     mDest;
        private ContentResolver cr;
        private int     counter = 0, delerr_counter = 0, depth = 0;
        private long    totalBytes = 0;
        private double  conv;
        private SAFItem[] iList = null;
        private boolean move;
        private byte[]  buf;
        private static final int BUFSZ = 524288;
        private PowerManager.WakeLock wakeLock;

        CopyBetweenEngine( SAFItem[] to_copy, Uri dest, boolean move ) {
            setName( ".CopyBetweenEngine" );
            iList = to_copy;
            mDest = dest;
            cr = SAFAdapter.this.ctx.getContentResolver();
            this.move = move;
            buf = new byte[BUFSZ];
            PowerManager pm = (PowerManager)ctx.getSystemService( Context.POWER_SERVICE );
            wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, TAG );
        }
        @Override
        public void run() {
            sendProgress( ctx.getString( R.string.preparing ), 0, 0 );
            try {
                int l = iList.length;
                wakeLock.acquire();
                int num = copyFiles( iList, mDest );

                wakeLock.release();
                String report = Utils.getOpReport( ctx, num, move ? R.string.moved : R.string.copied );
                sendResult( report );
            } catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
                return;
            }
        }
        
        private final int copyFiles( SAFItem[] list, Uri dest ) throws InterruptedException {
            SAFItem item = null;
            for( int i = 0; i < list.length; i++ ) {
                InputStream  is = null;
                OutputStream os = null;
                item = list[i];
                if( item == null ) {
                    error( ctx.getString( R.string.unkn_err ) );
                    break;
                }
                Uri dest_uri = null;
                try {
                    if( isStopReq() ) {
                        error( ctx.getString( R.string.canceled ) );
                        break;
                    }
                    String fn = item.name;
                    String to_append = "%2f" + Utils.escapePath( fn );
                    dest_uri = dest.buildUpon().encodedPath( dest.getEncodedPath() + to_append ).build();
                    String mime = SAFAdapter.getMime( SAFAdapter.this.ctx, dest_uri );
                    Uri item_uri = (Uri)item.origin;
                    if( item.dir ) {
                        if( depth++ > 40 ) {
                            error( ctx.getString( R.string.too_deep_hierarchy ) );
                            break;
                        }
                        if( mime != null ) {
                          if( !Document.MIME_TYPE_DIR.equals( mime ) ) {
                            error( ctx.getString( R.string.cant_md ) );
                            break;
                          }
                        } else {
                            DocumentsContract.createDocument( cr, dest, Document.MIME_TYPE_DIR, fn );                            
                        }
                        ArrayList<SAFItem> tmp_list = getChildren( item_uri );
                        SAFItem[] sub_items = new SAFItem[tmp_list.size()];
                        tmp_list.toArray( sub_items );
                        copyFiles( sub_items, dest_uri );
                        if( errMsg != null )
                            break;
                        depth--;
                        counter++;
                    }
                    else {
                        if( mime != null ) {
                            int res = askOnFileExist( ctx.getString( R.string.file_exist, fn ), commander );
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.ABORT ) break;
                            if( res == Commander.REPLACE ) {
                                if( item_uri.equals( dest_uri ) ) {
                                    Log.w( TAG, "Not going to copy file to itself" );
                                    continue;
                                }
                                Log.v( TAG, "Overwritting file " + fn );
                                DocumentsContract.deleteDocument( cr, dest_uri );
                            }
                        } else
                            mime = Utils.getMimeByExt( Utils.getFileExt( fn ) );
                        dest_uri = DocumentsContract.createDocument( cr, dest, mime, fn );
                        String dest_path = dest_uri.getPath();
                        if( dest_path.indexOf( fn, dest_path.length() - fn.length() - 1 ) < 0 )  // SAF suxx
                            dest_uri = DocumentsContract.renameDocument( cr, dest_uri, fn );
                        is = cr.openInputStream( item_uri );
                        os = cr.openOutputStream( dest_uri );
                        long copied = 0, size = item.size;
                        
                        long start_time = 0;
                        int  speed = 0;
                        int  so_far = (int)(totalBytes * conv);
                        
                        String sz_s = Utils.getHumanSize( size );
                        int fnl = fn.length();
                        String rep_s = ctx.getString( R.string.copying, 
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
                                error( ctx.getString( R.string.canceled ) );
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
                            sendProgress( ctx.getString( R.string.copied_f, fn ) + sizeOfsize( copied, sz_s ), (int)(totalBytes * conv) );
                        counter++;
                    }
                    if( move ) {
                        if( !DocumentsContract.deleteDocument( cr, item_uri ) ) {
                            sendProgress( ctx.getString( R.string.cant_del, fn ), -1 );
                            delerr_counter++;
                        }
                    }
                }
                catch( Exception e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.rtexcept, item.name, e.getMessage() ) );
                }
                finally {
                    try {
                        if( is != null )
                            is.close();
                        if( os != null )
                            os.close();
                    }
                    catch( IOException e ) {
                        error( ctx.getString( R.string.acc_err, item.name, e.getMessage() ) );
                    }
                }
            }
            return counter;
        }
    }
    
    class CopyFromEngine extends Engine {  
        private SAFItem[] mList;
        private SAFAdapter owner;
        private ContentResolver cr;
        private File destFolder;
        private byte[] buf = new byte[65536];
        private boolean move;

        CopyFromEngine( SAFItem[] list, boolean move, File dest, Engines.IReciever recipient_ ) {
            super( recipient_ );
            setName( ".CopyFromEngine" );
            owner = SAFAdapter.this;
            mList = list;
            destFolder = dest;
            this.move = move;
        }
        @Override
        public void run() {
            try {
                Init( null );
                cr = ctx.getContentResolver();
                int cnt = copyFiles( mList, destFolder );
                if( recipient != null ) {
                      sendReceiveReq( destFolder );
                      return;
                }
                sendResult( Utils.getOpReport( owner.ctx, cnt, move ? R.string.moved : R.string.copied ) );
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        
        private final int copyFiles( Item[] l, File dest ) throws Exception {
            if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( s( R.string.canceled ) );
                Item item = l[i];
                String fn = item.name;
                sendProgress( ctx.getString( R.string.copying, fn ), 0 );
                File dest_file = new File( dest, fn );
                Uri u = (Uri)item.origin;
                boolean ok = false;
                if( item.dir ) {
                    if( !dest_file.exists() )
                        dest_file.mkdir();
                    ArrayList<SAFItem> tmp_list = getChildren( u );
                    SAFItem[] sub_items = new SAFItem[tmp_list.size()];
                    tmp_list.toArray( sub_items );
                    cnt += copyFiles( sub_items, dest_file );
                    if( errMsg != null ) break;
                    ok = true; 
                } else {
                    if( dest_file.exists()  ) {
                        int res = askOnFileExist( owner.ctx.getString( R.string.file_exist, dest_file.getAbsolutePath() ), owner.commander );
                        if( res == Commander.ABORT ) {
                            error( owner.ctx.getString( R.string.interrupted ) );
                            break;
                        }
                        if( res == Commander.SKIP )  continue;
                        if( res == Commander.REPLACE ) {
                            if( !dest_file.delete() ) {
                                error( owner.ctx.getString( R.string.cant_del, dest_file.getAbsoluteFile() ) );
                                break;
                            }
                        }
                    }
                    int fnl = fn.length();
                    String rep_s = owner.ctx.getString( R.string.copying, 
                           fnl > CUT_LEN ? "\u2026" + fn.substring( fnl - CUT_LEN ) : fn );

                    long copied = 0, size = item.size;
                    String sz_s = Utils.getHumanSize( size, false );
                    double conv = 100./size;
                    int n;
                    OutputStream os = null;
                    InputStream  is = null;
                    try {
                        os = new FileOutputStream( dest_file );
                        is = cr.openInputStream( u );
                        while( ( n = is.read( buf ) ) != -1 ) {
                            os.write( buf, 0, n );
                            copied += n;
                            sendProgress( rep_s + sizeOfsize( copied, sz_s ), (int)(copied * conv) );
                            Thread.sleep( 1 );
                        }
                        ok = true;
                    } catch( Exception e ) {
                        e.printStackTrace();
                    }
                    finally {
                        if( is != null ) is.close();
                        if( os != null ) os.close();
                    }
                    try {
                        dest_file.setLastModified( item.date.getTime() );
                    } catch( Exception e ) {}
                }
                if( move && ok )
                    DocumentsContract.deleteDocument( cr, u );
                cnt++;
            }
            return cnt;
        }
    }
    
    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
        try {
            if( uris == null || uris.length == 0 )
                return false;
            File[] list = Utils.getListOfFiles( uris );
            if( list != null ) {
                notify( Commander.OPERATION_STARTED );
                commander.startEngine( new CopyToEngine( list, move_mode ) );
                return true;
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return false;
    }

    class CopyToEngine extends Engine {
        private Uri     mDest;
        private ContentResolver cr;
        private int     counter = 0, delerr_counter = 0, depth = 0;
        private long    totalBytes = 0;
        private double  conv;
        private File[]  fList = null;
        private boolean move, del_src_dir;
        private byte[]  buf;
        private static final int BUFSZ = 524288;
        private PowerManager.WakeLock wakeLock;

        CopyToEngine( File[] list, int move_mode ) {
            super( null );
            setName( ".CopyToEngine" );
            fList = list;
            mDest = SAFAdapter.this.getUri();
            cr = SAFAdapter.this.ctx.getContentResolver();
            move = ( move_mode & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode & MODE_DEL_SRC_DIR ) != 0;
            buf = new byte[BUFSZ];
            PowerManager pm = (PowerManager)ctx.getSystemService( Context.POWER_SERVICE );
            wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, TAG );
        }
        @Override
        public void run() {
            sendProgress( ctx.getString( R.string.preparing ), 0, 0 );
            try {
                int l = fList.length;
                wakeLock.acquire();
                int num = copyFiles( fList, mDest );

                if( del_src_dir ) {
                    File src_dir = fList[0].getParentFile();
                    if( src_dir != null )
                        src_dir.delete();
                }
                wakeLock.release();
                // XXX: assume (move && !del_src_dir)==true when copy from app: to the FS
                if( delerr_counter == counter ) move = false;  // report as copy
                String report = Utils.getOpReport( ctx, num, move && !del_src_dir ? R.string.moved : R.string.copied );
                sendResult( report );
            } catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
                return;
            }
        }
        
        private final int copyFiles( File[] list, Uri dest ) throws InterruptedException {
            File file = null;
            for( int i = 0; i < list.length; i++ ) {
                InputStream  is = null;
                OutputStream os = null;
                file = list[i];
                if( file == null ) {
                    error( ctx.getString( R.string.unkn_err ) );
                    break;
                }
                Uri dest_uri = null;
                try {
                    if( isStopReq() ) {
                        error( ctx.getString( R.string.canceled ) );
                        break;
                    }
                    String fn = file.getName();
                    String to_append = "%2f" + Utils.escapePath( fn );
                    dest_uri = dest.buildUpon().encodedPath( dest.getEncodedPath() + to_append ).build();
                    String mime = SAFAdapter.getMime( SAFAdapter.this.ctx, dest_uri );
                    if( file.isDirectory() ) {
                        if( depth++ > 40 ) {
                            error( ctx.getString( R.string.too_deep_hierarchy ) );
                            break;
                        }
                        if( mime != null ) {
                          if( !Document.MIME_TYPE_DIR.equals( mime ) ) {
                            error( ctx.getString( R.string.cant_md ) );
                            break;
                          }
                        } else {
                            DocumentsContract.createDocument( cr, dest, Document.MIME_TYPE_DIR, fn );                            
                        }
                        copyFiles( file.listFiles(), dest_uri );
                        if( errMsg != null )
                            break;
                        depth--;
                        counter++;
                    }
                    else {
                        if( mime != null ) {
                            int res = askOnFileExist( ctx.getString( R.string.file_exist, fn ), commander );
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.ABORT ) break;
                            if( res == Commander.REPLACE ) {
                                File dest_file = new File( getPath( dest_uri, false ) );
                                if( dest_file.equals( file ) ) {
                                    Log.w( TAG, "Not going to copy file to itself" );
                                    continue;
                                }
                                Log.v( TAG, "Overwritting file " + fn );
                                DocumentsContract.deleteDocument( cr, dest_uri );
                            }
                        } else
                            mime = Utils.getMimeByExt( Utils.getFileExt( fn ) );
                        dest_uri = DocumentsContract.createDocument( cr, dest, mime, fn );
                        if( dest_uri == null ) {
                            error( ctx.getString( R.string.cant_create, fn, "" ) );
                            break;
                        }
                        String dest_path = dest_uri.getPath();
                        if( dest_path.indexOf( fn, dest_path.length() - fn.length() - 1 ) < 0 )  // SAF suxx
                            dest_uri = DocumentsContract.renameDocument( cr, dest_uri, fn );
                        
                        is = new FileInputStream( file );
                        os = cr.openOutputStream( dest_uri );
                        long copied = 0, size  = file.length();
                        
                        long start_time = 0;
                        int  speed = 0;
                        int  so_far = (int)(totalBytes * conv);
                        
                        String sz_s = Utils.getHumanSize( size );
                        int fnl = fn.length();
                        String rep_s = ctx.getString( R.string.copying, 
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
                                error( ctx.getString( R.string.canceled ) );
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
                        /*
                        ContentValues cv = new ContentValues();
                        cv.put( Document.COLUMN_LAST_MODIFIED, file.lastModified() );
                        cr.update( dest_uri, cv, null, null ); //throws..
                        */
                        if( i >= list.length-1 )
                            sendProgress( ctx.getString( R.string.copied_f, fn ) + sizeOfsize( copied, sz_s ), (int)(totalBytes * conv) );
                        counter++;
                    }
                    if( move ) {
                        if( !file.delete() ) {
                            sendProgress( ctx.getString( R.string.cant_del, fn ), -1 );
                            delerr_counter++;
                        }
                    }
                }
                catch( Exception e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.rtexcept, file.getAbsolutePath(), e.getMessage() ) );
                }
                finally {
                    try {
                        if( is != null )
                            is.close();
                        if( os != null )
                            os.close();
                    }
                    catch( IOException e ) {
                        error( ctx.getString( R.string.acc_err, file.getAbsolutePath(), e.getMessage() ) );
                    }
                }
            }
            return counter;
        }
    }
    
    
    @Override
	public void prepareToDestroy() {
        super.prepareToDestroy();
        if( tht != null )
            tht.interrupt();
	}


    @Override
    protected int getPredictedAttributesLength() {
        return 24;   // "application/octet-stream"
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
                return items[position - 1];
            }
            else {
                item = new Item();
                item.name = "???";
            }
        }
        return item;
    }
    public final SAFItem[] bitsToItems( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            SAFItem[] res = new SAFItem[counter];
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

    @Override
    protected void reSort() {
        if( items == null ) return;
        synchronized( items ) {
            reSort( items );
        }
    }
    public void reSort( Item[] items_ ) {
        if( items_ == null ) return;
        ItemComparator comp = new ItemComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items_, comp );
    }
    @Override
    public IReciever getReceiver() {
        return this;
    }
    public static void saveURI( Context ctx, Uri uri ) {
        SharedPreferences saf_sp = ctx.getSharedPreferences( SAFAdapter.ORG_SCHEME, Activity.MODE_PRIVATE );
        SharedPreferences.Editor editor = saf_sp.edit();
        editor.putString( "tree_root_uri", uri != null ? uri.toString() : null );
        editor.commit();
    }
}
