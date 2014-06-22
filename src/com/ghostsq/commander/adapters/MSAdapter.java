package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.nio.channels.ClosedByInterruptException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.MediaFile;
import com.ghostsq.commander.utils.MediaScanTask;
import com.ghostsq.commander.utils.MnfUtils;
import com.ghostsq.commander.utils.Utils;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Thumbnails;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MSAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private final static String TAG    = "MSAdapter";    // MediaStore
    private final static String SCHEME = "ms:";
    private final static Uri baseContentUri = MediaStore.Files.getContentUri( "external" );
    
    private   String dirName;
    protected Item[] items;
    private   ThumbnailsThread tht = null;
    
    public MSAdapter( Context ctx_ ) {
        super( ctx_ );
        dirName = null;
        items = null;
    }

    @Override
    public String getScheme() {
        return "ms";
    }
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case FS:
        case LOCAL:
        case REAL:
        case SEND:
            return true;
        case SZ:
        case F2:
        case F6:
            return false;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        return getUri().toString();
    }

    /*
     * CommanderAdapter implementation
     */

    @Override
    public Uri getUri() {
        try {
            return Uri.parse( SCHEME + Utils.escapePath( dirName ) );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setUri( Uri uri ) {
        dirName = Utils.mbAddSl( uri.toString() );
    }
    
    private Uri getContentUri( String fullname ) {
        final String[] projection = {
             MediaStore.MediaColumns._ID,
             MediaStore.MediaColumns.DATA
        };
        Cursor cursor=null;
        ContentResolver cr = null;
      try {
         cr = ctx.getContentResolver();
         if( cr == null) return null;
         
         final String selection = MediaStore.MediaColumns.DATA + " = ? ";
         String[] selectionParams = new String[1];
         selectionParams[0] = fullname;
         cursor = cr.query( baseContentUri, projection, selection, selectionParams, null );
         if( cursor!=null ) {
            try {
               if( cursor.getCount() > 0 ) {
                  cursor.moveToFirst();
                  int  dci = cursor.getColumnIndex( MediaStore.MediaColumns.DATA );
                  String s = cursor.getString( dci );
                  if( !s.equals(fullname) )
                     return null;
                  int ici = cursor.getColumnIndex( MediaStore.MediaColumns._ID );
                  long id = cursor.getLong( ici );
                  return MediaStore.Files.getContentUri( "external", id );
               } 
            } catch( Throwable e ) {
               Log.e( TAG, "on result", e );
            }
            finally {
                cursor.close();
            }
         }
      } catch( Throwable e ) {
         Log.e( TAG, "on query", e );
      }
      return null;
   }     
    
    @Override
    public boolean readSource( Uri d, String pass_back_on_done ) {
        final String[] projection = {
                 MediaStore.MediaColumns._ID,
                 MediaStore.MediaColumns.DATA,
                 MediaStore.MediaColumns.DATE_MODIFIED,
                 MediaStore.MediaColumns.MIME_TYPE,
                 MediaStore.MediaColumns.SIZE,
                 MediaStore.MediaColumns.TITLE
        };
        try {
            if( d != null ) {
                dirName = Utils.mbAddSl( d.getPath() );
            } else {
                if( !Utils.str( dirName ))
                    dirName = Environment.getExternalStorageDirectory().getAbsolutePath();
            }
    	    parentLink = !Utils.str( dirName ) || SLS.equals( dirName ) ? SLS : PLS;
    	     ContentResolver cr = ctx.getContentResolver();
             if( cr == null ) return false;
             final String selection = MediaStore.MediaColumns.DATA + " like ? ";
             String[] selectionParams = new String[1];
             selectionParams[0] = dirName + "%";
             Cursor cursor = cr.query( baseContentUri, projection, selection, selectionParams, null );
             if( cursor != null ) {
                try {
                   if( cursor.getCount() > 0 ) {
                      cursor.moveToFirst();
                      ArrayList<Item>   tmp_list = new ArrayList<Item>();
                      ArrayList<String> subdirs  = new ArrayList<String>();
                      int ici = cursor.getColumnIndex( MediaStore.MediaColumns._ID );
                      int pci = cursor.getColumnIndex( MediaStore.MediaColumns.DATA );
                      int sci = cursor.getColumnIndex( MediaStore.MediaColumns.SIZE );
                      int mci = cursor.getColumnIndex( MediaStore.MediaColumns.MIME_TYPE );
                      int dci = cursor.getColumnIndex( MediaStore.MediaColumns.DATE_MODIFIED );
                      int cdl = Utils.mbAddSl( dirName ).length();
                      
                      do {
                          String path = cursor.getString( pci );
                          if( path == null || !path.startsWith( dirName ) ) continue;
                          int end_pos = path.indexOf( "/", cdl );
                          if( end_pos > 0 && path.length() > end_pos ) {
                              String subdir = path.substring( cdl-1, end_pos );
                              if( subdirs.indexOf( subdir ) < 0 )
                                  subdirs.add( subdir );
                              continue;
                          }
                          String name = path.substring( cdl );
                          if( !Utils.str( name ) ) continue;
                          File f = new File( dirName, name );
                          Item item = new Item();
                          item.dir = f.isDirectory();
                          item.origin = MediaStore.Files.getContentUri( "external", cursor.getLong( ici ) );
                          item.name = ( item.dir ? "/" : "" ) + name;
                          item.size = cursor.getLong( sci );
                          item.date = new Date( cursor.getLong( dci ) * 1000 );
                          item.attr = cursor.getString( mci );
                          if( item.dir ) item.size = -1;
                          tmp_list.add( item );
                      } while( cursor.moveToNext() );
                      cursor.close();
                      
                      for( String sd : subdirs ) {
                          boolean has = false;
                          for( Item item : tmp_list ) {
                              if( item.name.equals( sd ) ) {
                                  has = true;
                                  break;
                              }
                          }
                          if( !has ) {
                              Item item = new Item();
                              item.dir = true;
                              item.name = sd;
                              tmp_list.add( item );
                          }                          
                      }
                      
                      items = new Item[tmp_list.size()];
                      tmp_list.toArray( items );
                      reSort( items );
                   }
                   else
                       items = new Item[0];
                   super.setCount( items.length );
                } catch( Throwable e ) {
                    Log.e( TAG, "inner", e );
                }
            }     	    
    	    startThumbnailCreation();
            notify( pass_back_on_done );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "outer", e );
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
    
    @Override
    protected void reSort() {
        reSort( items );
    }
    public void reSort( Item[] items_ ) {
        if( items_ == null ) return;
        ItemComparator comp = new ItemComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items_, comp );
    }
   
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            if( parentLink == SLS || dirName == null ) 
                commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
            else {
                File cur_dir_file = new File( dirName );
                String parent_dir = cur_dir_file.getParent();
                commander.Navigate( Uri.parse( SCHEME + Utils.escapePath( parent_dir != null ? parent_dir : DEFAULT_DIR ) ), null,
                                    cur_dir_file.getName() );
            }
        }
        else {
            Item item = items[position - 1];
            if( item.dir )
                commander.Navigate( Uri.parse( SCHEME + Utils.escapePath( dirName + item.name.replaceAll( "/", "" ) ) ), null, null );
            else
                commander.Open( Uri.parse( Utils.escapePath( dirName + item.name ) ), null );
        }
    }

    @Override
    public Uri getItemUri( int position ) {
        try {
            String item_name = getItemName( position, true );
            return Uri.parse( SCHEME + Utils.escapePath( item_name ) );
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
            return position == 0 ? (new File( dirName )).getParent() : dirName + items[position - 1].name;
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
        	Item[] list = bitsToItems( cis );
    		notify( Commander.OPERATION_STARTED );
//    		commander.startEngine( new CalcSizesEngine( list ) );
		}
        catch(Exception e) {
		}
	}
	
	@Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length ) 
            return false;
        try {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put( MediaStore.MediaColumns.DATA, dirName + newName );
            final String selection = MediaStore.MediaColumns.DATA + " = ? ";
            String[] selectionParams = new String[1];
            Item item = items[position-1];
            selectionParams[0] = dirName + item.name.replaceAll( "/", "" );
            if( item.dir )
                selectionParams[0] = Utils.mbAddSl( selectionParams[0] );
            return 1 == cr.update( baseContentUri, cv, selection, selectionParams );
        }
        catch( Exception e ) {
            commander.showError( ctx.getString( R.string.sec_err, e.getMessage() ) );
        }
        return false;
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
            Uri c_uri = getContentUri( u.getPath() );
            ContentResolver cr = ctx.getContentResolver();
            InputStream is = cr.openInputStream( c_uri );
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
        try {
            Uri c_uri = getContentUri( u.getPath() );
            ContentResolver cr = ctx.getContentResolver();
            return cr.openOutputStream( c_uri );
        } catch( FileNotFoundException e ) {
            Log.e( TAG, u.getPath(), e );
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
            MediaFile mf = new MediaFile( ctx, new File( dirName, new_name ) );
            if( mf.mkdir() )
                notifyRefr( new_name );
            else {
                String err_str = ctx.getString( R.string.cant_md, new_name );
                if( android.os.Build.VERSION.SDK_INT >= 19 )
                    err_str += "\n" + ctx.getString( R.string.not_supported );
                notify( err_str, Commander.OPERATION_FAILED );
            }
        } catch( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public boolean createFolderAbs( String abs_new_name ) {
      if( android.os.Build.VERSION.SDK_INT >= 19 )
          return false;
      String fn;
      Uri uri;
      ContentResolver cr;
      try {
         cr = ctx.getContentResolver();
         fn = Utils.mbAddSl( abs_new_name ) + "/dummy.jpg";
         ContentValues cv = new ContentValues();
         cv.put( MediaStore.MediaColumns.DATA, fn );
         cv.put( MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE );
         uri = cr.insert( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv );
         if( uri != null ) {
             try {
                cr.delete( uri, null, null );
             } catch( Throwable e ) {
                 Log.e( TAG, "delete dummy file", e );
             }
             return true;
          } 
      } catch( Throwable e ) {
         Log.e( TAG, abs_new_name, e );
      }
      return false;
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
        private ArrayList<String> to_scan;
        ContentResolver cr;

        DeleteEngine( Item[] list ) {
            setName( ".DeleteEngine" );
            mList = list;
        }
        @Override
        public void run() {
            try {
                Init( null );
                cr = ctx.getContentResolver();
                int cnt = deleteFiles( MSAdapter.this.dirName, mList );
                sendResult( Utils.getOpReport( ctx, cnt, R.string.deleted ) );
                if( to_scan != null && to_scan.size() > 0 ) {
                    String[] to_scan_a = new String[to_scan.size()];
                    to_scan.toArray( to_scan_a );
                }
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        private final int deleteFiles( String base_path, Item[] l ) throws Exception {
    	    if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num; 
            ContentValues cv = new ContentValues();
            cv.put( MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE );
            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( s( R.string.canceled ) );
                Item f = l[i];
                sendProgress( ctx.getString( R.string.deleting, f.name ), (int)(cnt * conv) );
                if( f.dir ) {
                     final String selection = MediaStore.MediaColumns.DATA + " like ? ";
                     String[] selectionParams = new String[1];
                     selectionParams[0] = Utils.mbAddSl( base_path + l[i].name.replaceAll( "/", "" ) ) + "%";
                     cr.update( baseContentUri, cv, selection, selectionParams );
                     cnt += cr.delete( baseContentUri, selection, selectionParams );
                }
                {
                     Uri c_uri = (Uri)l[i].origin;
                     if( c_uri != null ) {
                         cr.update( c_uri, cv, null, null );                          
                         cnt += cr.delete( c_uri, null, null );
                     }
                }
                /*
                {
                    error( ctx.getString( R.string.cant_del, f.name ) );
                    break;
                }
                */
            }
            return cnt;
        }
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        boolean ok = to.receiveItems( bitsToNames( cis ), MODE_COPY );
        if( !ok ) {
            notify( Commander.OPERATION_FAILED );
            return ok;
        }
        return ok;
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
            if( uris == null || uris.length == 0 )
            	return false;
            File[] list = Utils.getListOfFiles( uris );
            if( list != null ) {
                notify( Commander.OPERATION_STARTED );
                commander.startEngine( new CopyEngine( list, dirName, move_mode ) );
	            return true;
            }
		} catch( Exception e ) {
		    e.printStackTrace();
		}
		return false;
    }

    class CopyEngine extends Engine {
        private String  mDest;
        private int     counter = 0, delerr_counter = 0, depth = 0;
        private long    totalBytes = 0;
        private double  conv;
        private File[]  fList = null;
        private ArrayList<String> to_scan;
        private boolean move, del_src_dir;
        private byte[]  buf;
        private static final int BUFSZ = 524288;
        private PowerManager.WakeLock wakeLock;

        CopyEngine( File[] list, String dest, int move_mode ) {
            super( null );
            setName( ".CopyEngine" );
            fList = list;
            mDest = dest;
            move = ( move_mode & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode & MODE_DEL_SRC_DIR ) != 0;
            buf = new byte[BUFSZ];
            to_scan = new ArrayList<String>();                        
            PowerManager pm = (PowerManager)ctx.getSystemService( Context.POWER_SERVICE );
            wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, TAG );
        }
        @Override
        public void run() {
            sendProgress( ctx.getString( R.string.preparing ), 0, 0 );
            try {
                int l = fList.length;
                Item[] x_list = new Item[l];
                wakeLock.acquire();
//                long sum = getSizes( x_list );
//                conv = 100 / (double)sum;
                int num = copyFiles( fList, Utils.mbAddSl( mDest ) );

                if( del_src_dir ) {
                    File src_dir = fList[0].getParentFile();
                    if( src_dir != null )
                        src_dir.delete();
                }

                String[] to_scan_a = new String[to_scan.size()];
                to_scan.toArray( to_scan_a );
                ForwardCompat.scanMedia( ctx, to_scan_a );
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
        private final int copyFiles( File[] list, String dest ) throws InterruptedException {
            File file = null;
            for( int i = 0; i < list.length; i++ ) {
                boolean existed = false;
                InputStream  is = null;
                OutputStream os = null;
                //File outFile = null;
                file = list[i];
                if( file == null ) {
                    error( ctx.getString( R.string.unkn_err ) );
                    break;
                }
                String out_full_name = null;
                try {
                    if( isStopReq() ) {
                        error( ctx.getString( R.string.canceled ) );
                        break;
                    }
                    String fn = file.getName();
                    out_full_name = dest + fn;
                    if( file.isDirectory() ) {
                        if( depth++ > 40 ) {
                            error( ctx.getString( R.string.too_deep_hierarchy ) );
                            break;
                        }
                        File out_dir_file = new File( out_full_name );
                        if( !out_dir_file.exists() ) {
                            MediaFile mf = new MediaFile( ctx, new File( out_full_name ) );
                            if( !mf.mkdir() ) {
                                error( ctx.getString( R.string.not_supported ) );
                                break;
                            }
                        }
                        copyFiles( file.listFiles(), Utils.mbAddSl( out_full_name ) );
                        if( errMsg != null )
                            break;
                        depth--;
                        counter++;
                    }
                    else {
                        ContentResolver cr = ctx.getContentResolver();
                        Uri content_uri = getContentUri( out_full_name );
                        if( content_uri != null ) {
                            int res = askOnFileExist( ctx.getString( R.string.file_exist, out_full_name ), commander );
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.REPLACE ) {
                                Log.v( TAG, "Overwritting file " + out_full_name );
                            }
                            if( res == Commander.ABORT ) break;
                        } else {
                            ContentValues cv = new ContentValues();
                            cv.put( MediaStore.MediaColumns.DATA, out_full_name );
                            content_uri = cr.insert( baseContentUri, cv );
                        }                        
                        
                        is = new FileInputStream( file );
                        os = cr.openOutputStream( content_uri );
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
                        if( i >= list.length-1 )
                            sendProgress( ctx.getString( R.string.copied_f, fn ) + sizeOfsize( copied, sz_s ), (int)(totalBytes * conv) );
                        to_scan.add( out_full_name );
                        counter++;
                    }
                    if( move ) {
                        if( !file.delete() ) {
                            sendProgress( ctx.getString( R.string.cant_del, fn ), -1 );
                            delerr_counter++;
                        }
                    }
                }
                catch( SecurityException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.sec_err, e.getMessage() ) );
                }
                catch( FileNotFoundException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.not_accs, e.getMessage() ) );
                }
                catch( ClosedByInterruptException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.canceled ) );
                }
                catch( IOException e ) {
                    Log.e( TAG, "", e );
                    String msg = e.getMessage();
                    error( ctx.getString( R.string.acc_err, out_full_name, msg != null ? msg : "" ) );
                }
                catch( RuntimeException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.rtexcept, out_full_name, e.getMessage() ) );
                }
                finally {
                    try {
                        if( is != null )
                            is.close();
                        if( os != null )
                            os.close();
                    }
                    catch( IOException e ) {
                        error( ctx.getString( R.string.acc_err, out_full_name, e.getMessage() ) );
                    }
                }
            }
            return counter;
        }
    }
        
    @Override
	public void prepareToDestroy() {
        super.prepareToDestroy();
		items = null;
	}

    public final Item[] bitsToItems( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            Item[] res = new Item[counter];
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
                return items[position - 1];
            }
            else {
                item = new Item();
                item.name = "???";
            }
        }
        return item;
    }

    @Override
    public IReciever getReceiver() {
        return this;
    }
}
