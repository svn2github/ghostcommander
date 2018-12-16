package com.ghostsq.commander.adapters;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.progress.ProgressMonitor;
import net.lingala.zip4j.unzip.Unzip;
import net.lingala.zip4j.util.Zip4jConstants;
import net.lingala.zip4j.util.Zip4jUtil;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

public class ZipAdapter extends CommanderAdapterBase implements Engines.IReciever {
    public static final String TAG = "ZipAdapter";
    protected static final int BLOCK_SIZE = 100000;
    // Java compiler creates a thunk function to access to the private owner
    // class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public Uri uri = null;
    public ZipFile zip = null;
    public FileHeader[] items = null;
    public boolean password_validated = false;
    public String  password = null;
    public String  encoding = null;

    public ZipAdapter(Context ctx_) {
        super( ctx_ );
        parentLink = PLS;
    }

    @Override
    public String getScheme() {
        return "zip";
    }

    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case REAL:
        case SZ:
            return true;
        case F4:
            return false;
        default:
            return super.hasFeature( feature );
        }
    }

    @Override
    public void setCredentials( Credentials crd ) {
        try {
            password = crd == null ? null : crd.getPassword();
            if( zip != null )
                zip.setPassword( password != null ? password.toCharArray() : null );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
    }
    @Override
    public Credentials getCredentials() {
        return password != null ? new Credentials( null, password ) : null;
    }
    
    public final ZipFile createZipFileInstance( Uri u ) {
        try {
            if( u == null ) return null;
            String zip_path = u.getPath();
            if( zip_path == null ) return null;
            ZipFile zip_file = new ZipFile( zip_path );

            String enc = encoding;
            if( enc == null )
                enc = u.getQueryParameter( "e" );
            if( enc == null )
                enc = "UTF-8";
            zip_file.setFileNameCharset( enc );
            if( password != null && Zip4jUtil.checkFileExists( zip_path ) )
                zip_file.setPassword( password );
            return zip_file;
        } catch( ZipException e ) {
            Log.e( TAG, "Can't create zip file instance for " + u.toString(), e );
        }
        return null;
    }

    public synchronized final void setZipFile( ZipFile zf ) {
        this.zip = zf;
    }

    public synchronized final ZipFile getZipFile() {
        return this.zip;
    }
        
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            zip = null;
            if( tmp_uri != null )
                uri = tmp_uri;
            if( uri == null )
                return false;
            if( reader != null ) { // that's not good.
                if( reader.isAlive() ) {
                    commander.showInfo( ctx.getString( R.string.busy ) );
                    reader.interrupt();
                    Thread.sleep( 500 );
                    if( reader.isAlive() )
                        return false;
                }
            }
            Log.v( TAG, "reading " + uri );
            notify( Commander.OPERATION_STARTED );
            reader = new ListEngine( readerHandler, pass_back_on_done );
            reader.start();
            return true;
        } catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        notify( "Fail", Commander.OPERATION_FAILED );
        return false;
    }

    class ZipEngine extends Engine {
        protected synchronized boolean waitCompletion() {
            return waitCompletion( 0 );
        }
        protected synchronized boolean waitCompletion( int op_res_id ) {
            try {
                ProgressMonitor pm = ZipAdapter.this.zip.getProgressMonitor();
                while( pm.getState() == ProgressMonitor.STATE_BUSY ) {
                    if( isStopReq() ) {
                        pm.cancelAllTasks();
                        error( ctx.getString( R.string.canceled ) );
                        return false;
                    }
                    //Log.v( TAG, "Waiting " + pm.getFileName() + " %" + pm.getPercentDone() );
                    String rep_str;
                    if( op_res_id != 0 )
                        rep_str = ctx.getString( op_res_id, pm.getFileName() );
                    else
                        rep_str = pm.getFileName();
                    sendProgress( rep_str, pm.getPercentDone(), 0 );
                    wait( 500 );
                }
                int res = pm.getResult();
                if( res == ProgressMonitor.RESULT_SUCCESS )
                    return true;
                else if( res == ProgressMonitor.RESULT_ERROR ) {
                    Throwable e = pm.getException();
                    if( e != null ) {
                        String msg = e.getMessage();
                        if( msg != null )
                            error( msg.replaceAll("^.+:", "") );
                        Log.e( TAG, pm.getFileName(), e );
                    }
                    else
                        Log.e( TAG, "zip error" );
                }
            } catch( Exception e ) {
                Log.e( TAG, "zip exception!", e );
            }
            return false;
        }
        
        // see https://stackoverflow.com/questions/19244137/check-password-correction-in-zip-file-using-zip4j
        boolean isValid( FileHeader fh ) {
            try {
                ZipInputStream is = null;
                if( fh == null ) return false;
                is = ZipAdapter.this.zip.getInputStream( fh );
                byte[] b = new byte[4096];
                boolean ok = is.read(b) != -1;
                is.close( true );
                return ok;
            } catch( Exception e ) {
                Log.w( TAG, fh.getFileName(), e );
            }
            return false;
        }
    }

    class EnumEngine extends ZipEngine {

        protected EnumEngine() {
        }

        protected EnumEngine(Handler h) {
            super.setHandler( h );
        }

        protected final FileHeader[] GetFolderList( String fld_path ) {
            if( zip == null )
                return null;
            List<FileHeader> headers = null;
            try {
                headers = zip.getFileHeaders();
            } catch( ZipException e1 ) {
                Log.e( TAG, "File: " + uri, e1 );
            }
            if( headers == null )
                return null;
            return GetFolderList( fld_path, headers );
        }

        protected final FileHeader[] GetFolderList( String fld_path, List<FileHeader> headers ) {
            if( headers == null ) return null;
            if( fld_path == null )
                fld_path = "";
            else if( fld_path.length() > 0 && fld_path.charAt( 0 ) == SLC )
                fld_path = fld_path.substring( 1 );
            int fld_path_len = fld_path.length();
            if( fld_path_len > 0 && fld_path.charAt( fld_path_len - 1 ) != SLC ) {
                fld_path = fld_path + SLC;
                fld_path_len++;
            }

            ArrayList<FileHeader> array = new ArrayList<FileHeader>();
            for( FileHeader e : headers ) {
                if( isStopReq() )
                    return null;
                if( e == null )
                    continue;
                String entry_name = fixName( e );
                // Log.v( TAG, "Found an Entry: " + entry_name );
                if( entry_name == null || fld_path.compareToIgnoreCase( entry_name ) == 0 )
                    continue;
                /*
                  There are at least two kinds of zips - with dedicated folder
                  entry and without one. The code below should process both. Do
                  not change until you fully understand how it works.
                 */
                if( !fld_path.regionMatches( true, 0, entry_name, 0, fld_path_len ) )
                    continue;

                int sl_pos = entry_name.indexOf( SLC, fld_path_len );
                if( sl_pos > 0 ) {
                    String sub_dir = entry_name.substring( fld_path_len, sl_pos + 1 );
                    int sub_dir_len = sub_dir.length();
                    boolean not_yet = true;
                    for( int i = 0; i < array.size(); i++ ) {
                        String a_name = fixName( array.get( i ) );
                        if( a_name.regionMatches( fld_path_len, sub_dir, 0, sub_dir_len ) ) {
                            not_yet = false;
                            break;
                        }
                    }
                    if( not_yet ) { // a folder
                        FileHeader sur_fld = new FileHeader();
                        sur_fld.setFileName( entry_name.substring( 0, sl_pos + 1 ) );
                        sur_fld.setDirectory( true );
                        /*
                         * TODO byte[] eb = { 1, 2 }; sur_fld.setExtra( eb );
                         */
                        array.add( sur_fld );
                    }
                } else
                    array.add( e ); // a leaf
            }
            return array.toArray( new FileHeader[array.size()] );
        }
    }

    class ListEngine extends EnumEngine {
        private FileHeader[] items_tmp = null;
        public String pass_back_on_done;

        ListEngine(Handler h, String pass_back_on_done_) {
            super( h );
            pass_back_on_done = pass_back_on_done_;
        }

        public FileHeader[] getItems() {
            return items_tmp;
        }

        @Override
        public void run() {
            try {
                setZipFile( createZipFileInstance( uri ) );
                if( zip.isEncrypted() && ZipAdapter.this.password == null ) {
                    sendLoginReq( ZipAdapter.this.uri.toString(), ZipAdapter.this.getCredentials(), pass_back_on_done, true );
                    return;
                }
                if( zip != null ) {
                    //Log.d( TAG, "Valid? " + zip.isValidZipFile() );                    
                    
                    String cur_path = null;
                    try {
                        cur_path = uri.getFragment();
                    } catch( NullPointerException e ) {
                        // it happens only when the Uri is built by Uri.Builder
                        Log.e( TAG, "uri.getFragment()", e );
                    }
                    
                    List<FileHeader> headers = null;
                    headers = zip.getFileHeaders();
                    if( headers == null ) {
                        sendProgress( ctx.getString( R.string.cant_open ), Commander.OPERATION_FAILED, pass_back_on_done );
                        return;
                    }
                    if( password != null && !password_validated ) {
                        for( FileHeader fh : headers ) {
                            if( !fh.isDirectory() ) {
                                if( !isValid( fh ) ) {
                                    sendLoginReq( ZipAdapter.this.uri.toString(), ZipAdapter.this.getCredentials(), pass_back_on_done, true );
                                    return;
                                }
                            }
                            ZipAdapter.this.password_validated = true;
                            break;
                        }
                    }
                    
                    items_tmp = GetFolderList( cur_path );
                    if( items_tmp != null ) {
                        ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING, ( mode & MODE_CASE ) != 0,
                                ascending );
                        Arrays.sort( items_tmp, comp );
                        sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
                        return;
                    }
                }
            } catch( Exception e ) {
                Log.e( TAG, uri.toString(), e );
                sendProgress( ctx.getString( R.string.error ), Commander.OPERATION_FAILED, pass_back_on_done );
                return;
            } finally {
                super.run();
            }
            sendProgress( ctx.getString( R.string.cant_open ), Commander.OPERATION_FAILED, pass_back_on_done );
        }
    }

    @Override
    protected void onReadComplete() {
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            FileHeader[] tmp_items = list_engine.getItems();
            if( tmp_items != null && ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                int cnt = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getFileName().charAt( 0 ) != '.' )
                        cnt++;
                items = new FileHeader[cnt];
                int j = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getFileName().charAt( 0 ) != '.' )
                        items[j++] = tmp_items[i];
            } else
                items = tmp_items;
            numItems = items != null ? items.length + 1 : 1;
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
    public void setUri( Uri uri_ ) {
        uri = uri_;
    }

    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        FileHeader[] fhs = bitsToItems( cis );
        if( fhs == null ) return;
        notify( Commander.OPERATION_STARTED );
        commander.startEngine( new CalcSizesEngine( fhs ) );
    }

    class CalcSizesEngine extends EnumEngine {
        private FileHeader[] mList = null;
        private long totalSize = 0, totalCompressed = 0;
        private int  dirs = 0;
        
        CalcSizesEngine( FileHeader[] list ) {
            mList = list;
        }

        @Override
        public void run() {
            Context c = ZipAdapter.this.ctx;
            sendProgress( c.getString( R.string.wait ), 0, 0 );
            int num = calcSizes( mList );
            StringBuilder result = new StringBuilder( 128 ); 
            if( mList.length == 1 ) {
                FileHeader f = mList[0];
                String name_fixed = fixName( f );
                if( f.isDirectory() ) {
                    result.append( c.getString( R.string.sz_folder, name_fixed, num ) );
                    if( dirs > 0 )
                        result.append( c.getString( R.string.sz_dirnum, dirs, ( dirs > 1 ? c.getString( R.string.sz_dirsfx_p ) : c.getString( R.string.sz_dirsfx_s ) ) ) );
                }
                else
                    result.append( c.getString( R.string.sz_file, name_fixed ) );
            } else
                result.append( c.getString( R.string.sz_files, num ) );
            if( totalSize > 0 )
                result.append( c.getString( R.string.sz_Nbytes, Formatter.formatFileSize( c, totalSize ).trim() ) );
            if( totalSize > 1024 )
                result.append( c.getString( R.string.sz_bytes, totalSize ) );
            result.append( "\n<b>Compressed: </b>" );
            result.append( Formatter.formatFileSize( c, totalCompressed ).trim() );
            if( mList.length == 1 ) {
                FileHeader f = mList[0];
                String name_fixed = fixName( f );
                result.append( c.getString( R.string.sz_lastmod ) );
                result.append( "&#xA0;" );
                String date_s = Utils.formatDate( new Date( Zip4jUtil.dosToJavaTme( f.getLastModFileTime() ) ), c );
                result.append( date_s );
                if( !f.isDirectory() ) {
                    String ext  = Utils.getFileExt( name_fixed );
                    String mime = Utils.getMimeByExt( ext );
                    if( mime != null && !"*/*".equals( mime ) ) {
                        result.append( "\n<b>MIME:</b>&#xA0;" );
                        result.append( mime );
                    }
                    result.append( "\n<b>CRC32:</b> " );
                    result.append( Long.toHexString( f.getCrc32() ) );
                    result.append( "\n" );
                    result.append( f.getFileComment() );
                }
            }
            sendReport( result.toString() );
        }

        private final int calcSizes( FileHeader[] list ) {
            int  counter = 0;
            try {
                for( int i = 0; i < list.length; i++ ) {
                    FileHeader f = list[i];
                    String name_fixed = fixName( f );
                    if( f.isDirectory() ) {
                        dirs++;
                        FileHeader[] subItems = GetFolderList( name_fixed );
                        if( subItems == null ) {
                            error( "Failed to get the file list of the subfolder '" + name_fixed + "'.\n" );
                            break;
                        }
                        counter += calcSizes( subItems );
                    } else {
                        totalSize += f.getUncompressedSize();
                        totalCompressed += f.getCompressedSize();
                        counter++;
                    }
                }
            } catch( Exception e ) {
                Log.e( TAG, "", e );
                error( e.getMessage() );
            }
            return counter;
        }
    }
    
    public boolean unpackZip( File zip_file ) { // to the same folder
        try {
            if( !checkReadyness() )
                return false;
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new ExtractAllEngine( zip_file, zip_file.getParentFile() ) );
            return true;
        } catch( Exception e ) {
            notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
        }
        return false;
    }

    class ExtractAllEngine extends ZipEngine {
        private File zipFile, destFolder;

        ExtractAllEngine( File zip_f, File dest ) {
            zipFile = zip_f;
            destFolder = dest;
        }

        @Override
        public void run() {
            try {
                sendProgress( ZipAdapter.this.ctx.getString( R.string.wait ), 0, 0 );
                synchronized( ZipAdapter.this ) {
                    if( ZipAdapter.this.zip == null )
                        ZipAdapter.this.zip = new ZipFile( zipFile.getAbsolutePath() );
                    zip.setRunInThread( true );
                    zip.extractAll( destFolder.getAbsolutePath() );
                    waitCompletion( R.string.unpacking ); 
                    zip.setRunInThread( false );
                }
                if( !noErrors() ) {
                    sendResult( ctx.getString( R.string.failed ) );
                    return;
                }
                sendResult( ctx.getString( R.string.unpacked, zipFile.getName() ) );
                if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences( ctx );
                    MediaScanEngine mse = new MediaScanEngine( ctx, destFolder, sp.getBoolean( "scan_all", true ), true );
                    mse.setHandler( thread_handler );
                    mse.run();
                }
            } catch( ZipException e ) {
                Log.e( TAG, "Can't extract " + zipFile.getAbsolutePath() );
            }
            super.run();
        }
    }    
    
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        try {
            if( zip == null )
                throw new RuntimeException( "Invalid ZIP" );
            FileHeader[] subItems = bitsToItems( cis );
            if( subItems == null )
                throw new RuntimeException( "Nothing to extract" );
            if( !checkReadyness() )
                return false;
            Engines.IReciever recipient = null;
            File dest = null;
            if( to instanceof FSAdapter ) {
                dest = new File( to.toString() );
                if( !dest.exists() )
                    dest.mkdirs();
                if( !dest.isDirectory() )
                    throw new RuntimeException( ctx.getString( R.string.inv_dest ) );
            } else {
                dest = new File( createTempDir() );
                recipient = to.getReceiver();
            }
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new CopyFromEngine( subItems, dest, recipient ) );
            return true;
        } catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }

    class CopyFromEngine extends EnumEngine {
        private File dest_folder;
        private FileHeader[] mList = null;
        private String base_pfx;
        private int base_len;
        private ArrayList<String> to_scan;

        CopyFromEngine(FileHeader[] list, File dest, Engines.IReciever recipient_) {
            recipient = recipient_; // member of a superclass
            mList = list;
            dest_folder = dest;
            try {
                base_pfx = uri.getFragment();
                if( base_pfx == null )
                    base_pfx = "";
                base_len = base_pfx.length();
                if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
                    to_scan = new ArrayList<String>(); 
            } catch( NullPointerException e ) {
                Log.e( TAG, "", e );
            }
        }

        @Override
        public void run() {
            sendProgress( ZipAdapter.this.ctx.getString( R.string.wait ), 0, 0 );
            synchronized( ZipAdapter.this ) {
                zip.setRunInThread( true );
                int total = copyFiles( mList, "" );
                zip.setRunInThread( false );
                if(  !noErrors() ) {
                    sendResult( ctx.getString( R.string.failed ) );
                    return;
                }
                if( recipient != null ) {
                    sendReceiveReq( dest_folder );
                    return;
                }
                sendResult( Utils.getOpReport( ctx, total, R.string.unpacked ) );
                if( to_scan != null && to_scan.size() > 0 ) {
                    String[] to_scan_a = new String[to_scan.size()];
                    to_scan.toArray( to_scan_a );
                    MediaScanEngine.scanMedia( ctx, to_scan_a );
                }
            }
            super.run();
        }

        private final int copyFiles( FileHeader[] list, String path ) {
            int counter = 0;
            try {
                long dir_size = 0, byte_count = 0;
                for( int i = 0; i < list.length; i++ ) {
                    FileHeader f = list[i];
                    if( !f.isDirectory() )
                        dir_size += f.getUncompressedSize();
                }
                double conv = 100. / (double)dir_size;
                for( int i = 0; i < list.length; i++ ) {
                    FileHeader entry = list[i];
                    if( entry == null )
                        continue;
                    String entry_name_fixed = fixName( entry );
                    if( entry_name_fixed == null )
                        continue;
                    String file_path = path + new File( entry_name_fixed ).getName();
                    File dest_file = new File( dest_folder, file_path );
                    String rel_name = entry_name_fixed.substring( base_len );

                    if( entry.isDirectory() ) {
                        if( !dest_file.mkdir() ) {
                            if( !dest_file.exists() || !dest_file.isDirectory() ) {
                                errMsg = "Can't create folder \"" + dest_file.getAbsolutePath() + "\"";
                                break;
                            }
                        }
                        FileHeader[] subItems = GetFolderList( entry_name_fixed );
                        if( subItems == null ) {
                            errMsg = "Failed to get the file list of the subfolder '" + rel_name + "'.\n";
                            break;
                        }
                        counter += copyFiles( subItems, rel_name );
                        if( !noErrors() )
                            break;
                    } else {
                        if( dest_file.exists() ) {
                            int res = askOnFileExist( ctx.getString( R.string.file_exist, dest_file.getAbsolutePath() ), commander );
                            if( res == Commander.ABORT )
                                break;
                            if( res == Commander.SKIP )
                                continue;
                            if( res == Commander.REPLACE ) {
                                if( !dest_file.delete() ) {
                                    error( ctx.getString( R.string.cant_del, dest_file.getAbsoluteFile() ) );
                                    break;
                                }
                            }
                        }
                        int n = 0;
                        int so_far = (int)( byte_count * conv );
                        int fnl = rel_name.length();
                        zip.extractFile( entry, dest_folder.getAbsolutePath(), null, rel_name );
                        if( !waitCompletion( R.string.unpacking ) ) {
                            File dest_f = new File( dest_folder.getAbsolutePath(), entry.getFileName() );
                            if( dest_f.exists() )
                                dest_f.delete();
                            return counter;
                        }
                        if( to_scan != null )
                            to_scan.add( dest_file.getAbsolutePath() );
                    }
                    Utils.setFullPermissions( dest_file );
                    long entry_time = entry.getLastModFileTime();
                    if( entry_time > 0 )
                        dest_file.setLastModified( entry_time );
                    if( stop || isInterrupted() ) {
                        error( ctx.getString( R.string.canceled ) );
                        break;
                    }
                    if( i >= list.length - 1 )
                        sendProgress( ctx.getString( R.string.unpacked_p, rel_name ), (int)( byte_count * conv ) );
                    counter++;
                }
            } catch( Exception e ) {
                Log.e( TAG, "copyFiles()", e );
                error( "Exception: " + e.getMessage() );
            }
            return counter;
        }
    }

    @Override
    public boolean createFile( String name ) {
        try {
            File tmp_ctr = Utils.getTempDir( ctx );
            File tmp_file = new File( tmp_ctr, name );
            tmp_file.createNewFile();
            if( createItem( tmp_file, tmp_ctr.getAbsolutePath() ) ) {
                notifyRefr( name );
                tmp_file.deleteOnExit();
                return true;
            }
        } catch( IOException e ) {
            Log.e( TAG, "Can't create " + name, e );
        }
        notify( ctx.getString( R.string.cant_create, name ), Commander.OPERATION_FAILED );
        return false;
    }

    @Override
    public void createFolder( String fld_name ) {
        File tmp_ctr = Utils.getTempDir( ctx );
        File tmp_fld = new File( tmp_ctr, fld_name );
        tmp_fld.mkdir();
        if( createItem( tmp_fld, tmp_ctr.getAbsolutePath() ) )
            notifyRefr( fld_name );
        else
            notify( ctx.getString( R.string.cant_md, fld_name ), Commander.OPERATION_FAILED );
        tmp_fld.deleteOnExit();
    }

    public boolean createItem( File item, String folder_path ) {
        try {
            ZipParameters parameters = new ZipParameters();
            parameters.setCompressionMethod( Zip4jConstants.COMP_DEFLATE );
            parameters.setCompressionLevel( Zip4jConstants.DEFLATE_LEVEL_MAXIMUM );
            parameters.setDefaultFolderPath( folder_path );
            String dest_path = uri.getFragment();
            if( Utils.str( dest_path ) && !"/".equals( dest_path ) )
                parameters.setRootFolderInZip( dest_path );
            if( ZipAdapter.this.password != null ) {
                parameters.setEncryptFiles( true );
                parameters.setEncryptionMethod( Zip4jConstants.ENC_METHOD_AES );
                parameters.setAesKeyStrength( Zip4jConstants.AES_STRENGTH_256 );
                parameters.setPassword( password.toCharArray() );
            }
            if( ZipAdapter.this.encoding != null ) {
            
            }
            if( ZipAdapter.this.zip == null )
                ZipAdapter.this.zip = createZipFileInstance( ZipAdapter.this.uri );
            ArrayList<File> f_list = new ArrayList<File>( 1 );
            f_list.add( item );
            zip.addFiles( f_list, parameters );
            return true;
        } catch( ZipException e ) {
            Log.e( TAG, "Creating folder " + item.getName(), e );
        }
        return false;
    }    
    
    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
            if( !checkReadyness() )
                return false;
            FileHeader[] to_delete = bitsToItems( cis );
            if( to_delete != null && zip != null && uri != null ) {
                notify( Commander.OPERATION_STARTED );
                commander.startEngine( new DelEngine( to_delete ) );
                return true;
            }
        } catch( Exception e ) {
            Log.e( TAG, "deleteItems()", e );
        }
        notify( null, Commander.OPERATION_FAILED );
        return false;
    }

    class DelEngine extends EnumEngine {
        private String[] flat_names_to_delete = null;
        private FileHeader[] list;

        DelEngine( FileHeader[] list ) {
            this.list = list;
        }

        @Override
        public void run() {
            if( zip == null )
                return;
            sendProgress( ZipAdapter.this.ctx.getString( R.string.wait ), 1, 1 );
            synchronized( ZipAdapter.this ) {
                Init( null );
                try {
                    zip.setRunInThread( true );
                    int num_deleted = deleteFiles( list );
                    zip.setRunInThread( false );
                    zip = null;
                    sendResult( Utils.getOpReport( ctx, num_deleted, R.string.deleted ) );
                    return;
                } catch( Exception e ) {
                    error( e.getMessage() );
                }
                super.run();
            }
        }

        int tot_pp = 0;
        private int deleteFiles( FileHeader[] to_delete ) throws ZipException {
            int e_i = 0, tot_i = 0;
            for( FileHeader h : to_delete ) {
                int pp = e_i++ * 100 / to_delete.length;
                if( to_delete == list )
                    tot_pp = pp;
                sendProgress( h.getFileName(), tot_pp, pp );
                String fn = h.getFileName();
                if( !h.isDirectory() || zip.getFileHeader( fn ) != null ) {
                    zip.removeFile( h );
                    if( !waitCompletion( R.string.deleting ) ) {
                        Log.e( TAG, "Breaking on file " + fn );
                        break;
                    }
                    tot_i++;
                } else {
                    tot_i += deleteFiles( GetFolderList( fn ) );
                    if( !noErrors() )
                        break;
                }
            }
            return tot_i;
        }
    }

    @Override
    public Uri getItemUri( int position ) {
        if( uri == null )
            return null;
        if( items == null || position > items.length )
            return null;
        String enc_path = Utils.escapeName( fixName( items[position - 1] ) );
        return uri.buildUpon().encodedFragment( enc_path ).build();
    }

    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                if( uri != null ) {
                    Uri item_uri = getItemUri( position );
                    if( item_uri != null )
                        return item_uri.toString();
                }
            } else
                return new File( fixName( items[position - 1] ) ).getName();
        }
        return null;
    }

    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri == null )
                return;
            String cur = null;
            try {
                cur = uri.getFragment();
            } catch( Exception e ) {
            }
            if( cur == null || cur.length() == 0 || ( cur.length() == 1 && cur.charAt( 0 ) == SLC ) ) {
                File zip_file = new File( uri.getPath() );
                String parent_dir = Utils.escapePath( zip_file.getParent() );
                commander.Navigate( Uri.parse( parent_dir != null ? parent_dir : Panels.DEFAULT_LOC ), null, zip_file.getName() );
            } else {
                File cur_f = new File( cur );
                String parent_dir = cur_f.getParent();
                commander
                        .Navigate( uri.buildUpon().fragment( parent_dir != null ? parent_dir : "" ).build(), null, cur_f.getName() );
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        FileHeader item = items[position - 1];

        if( item.isDirectory() ) {
            commander.Navigate( uri.buildUpon().fragment( fixName( item ) ).build(), null, null );
        } else {
            commander.Open( uri.buildUpon().fragment( fixName( item ) ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
        try {
            if( !checkReadyness() )
                return false;
            if( uris == null || uris.length == 0 ) {
                notify( s( R.string.copy_err ), Commander.OPERATION_FAILED );
                return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null ) {
                notify( "Something wrong with the files", Commander.OPERATION_FAILED );
                return false;
            }
            notify( Commander.OPERATION_STARTED );

            items = null;

            commander.startEngine( new CopyToEngine( list, uri.getFragment(), move_mode ) );
            return true;
        } catch( Exception e ) {
            notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
        }
        return false;
    }

    @Override
    public IReciever getReceiver() {
        return this;
    }
    
    public boolean createZip( File[] list, String zip_fn, String pw, String enc ) {
        try {
            if( !checkReadyness() )
                return false;
            Uri.Builder ub = new Uri.Builder();
            ub.scheme( getScheme() ).path( zip_fn );
            setUri( ub.build() );
            this.password = pw;
            this.encoding = enc;
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new CopyToEngine( list, new File( zip_fn ) ) );
            return true;
        } catch( Exception e ) {
            notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
        }
        return false;
    }

    class CopyToEngine extends ZipEngine {
        private File[] topList;
        private long totalSize = 0;
        private int basePathLen;
        private File zipFile;
        private String destPath;
        private boolean newZip = false;
        private boolean move = false;
        private boolean del_src_dir = false;
        private String prep;

        /**
         * Add files to existing zip
         */
        CopyToEngine( File[] list, String dest_sub, int move_mode_ ) {
            topList = list;
            if( dest_sub != null )
                destPath = dest_sub.endsWith( SLS ) ? dest_sub : dest_sub + SLS;
            else
                destPath = "";
            basePathLen = list.length > 0 ? list[0].getParent().length() + 1 : 0;
            move = ( move_mode_ & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode_ & CommanderAdapter.MODE_DEL_SRC_DIR ) != 0;
        }

        /**
         * Create a new shiny ZIP
         */
        CopyToEngine( File[] list, File zip_file ) {
            topList = list;
            zipFile = zip_file;
            destPath = "";
            basePathLen = list.length > 0 ? list[0].getParent().length() + 1 : 0;
            newZip = true;
            prep = ZipAdapter.this.ctx.getString( R.string.preparing );
        }

        @Override
        public void run() {
            int num_files = 0;
            try {
                if( topList.length == 0 ) return;
                sendProgress( prep, 1 );
                synchronized( ZipAdapter.this ) {
                    Init( null );
                    ArrayList<File> full_list = new ArrayList<File>( topList.length );
                    addToList( topList, full_list );
                    ZipParameters parameters = new ZipParameters();
                    parameters.setCompressionMethod( Zip4jConstants.COMP_DEFLATE );
                    parameters.setCompressionLevel( Zip4jConstants.DEFLATE_LEVEL_MAXIMUM );
                    parameters.setDefaultFolderPath( topList[0].getParent() );
                    if( Utils.str( destPath ) && !"/".equals( destPath ) )
                        parameters.setRootFolderInZip( destPath );
                    if( ZipAdapter.this.password != null ) {
                        parameters.setEncryptFiles( true );
                        parameters.setEncryptionMethod( Zip4jConstants.ENC_METHOD_AES );
                        parameters.setAesKeyStrength( Zip4jConstants.AES_STRENGTH_256 );
                        parameters.setPassword( password.toCharArray() );
                    }
                    if( ZipAdapter.this.encoding != null ) {
                    
                    }
                    if( ZipAdapter.this.zip == null )
                        ZipAdapter.this.zip = createZipFileInstance( ZipAdapter.this.uri );
                    zip.setRunInThread( true );
                    zip.addFiles( full_list, parameters );
                    if( !waitCompletion( R.string.packing ) ) {
                        sendResult( ctx.getString( R.string.failed ) );
                        return;
                    }
                    sendProgress( prep, 100 );
                    zip.setRunInThread( false );
                    num_files = full_list.size();
                    if( del_src_dir ) {
                        File src_dir = topList[0].getParentFile();
                        if( src_dir != null )
                            src_dir.delete();
                    }
                }
            } catch( Exception e ) {
                error( "Exception: " + e.getMessage() );
            }
            sendResult( Utils.getOpReport( ctx, num_files, R.string.packed ) );
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
                        } else if( f.isDirectory() ) {
                            long dir_sz = addToList( f.listFiles(), full_list );
                            if( errMsg != null )
                                break;
                            if( dir_sz == 0 )
                                full_list.add( f );
                            else
                                total_size += dir_sz;
                        }
                    }
                }
            } catch( Exception e ) {
                Log.e( TAG, "addToList()", e );
                errMsg = "Exception: " + e.getMessage();
            }
            return total_size;
        }
    }

    @Override
    public boolean renameItem( int position, String new_name, boolean copy ) {
        FileHeader to_rename = items[position - 1];
        if( to_rename != null && zip != null && Utils.str( new_name ) ) {
            notify( Commander.OPERATION_STARTED );
            Engine eng = new RenameEngine( to_rename, new_name, copy );
            commander.startEngine( eng );
            return true;
        }
        return false;
    }

    class RenameEngine extends ZipEngine {
        private FileHeader ren_entry;
        private String new_name;
        private boolean copy = false;

        RenameEngine(FileHeader ren_entry, String new_name, boolean copy) {
            this.ren_entry = ren_entry;
            this.new_name = new_name;
            this.copy = copy;
        }

        @Override
        public void run() {
            if( zip == null )
                return;
            sendProgress( ZipAdapter.this.ctx.getString( R.string.wait ), 1, 1 );
            synchronized( ZipAdapter.this ) {
                Init( null );
                try {
                    String inner_path = uri.getFragment();
                    if( Utils.str( inner_path ) )
                        inner_path = Utils.mbAddSl( inner_path );
                    if( inner_path != null )
                        inner_path += new_name;
                    else
                        inner_path = new_name;
                    ZipParameters parameters = new ZipParameters();
                    parameters.setCompressionMethod( Zip4jConstants.COMP_DEFLATE );
                    parameters.setCompressionLevel( Zip4jConstants.DEFLATE_LEVEL_MAXIMUM );
                    parameters.setFileNameInZip( inner_path );
                    parameters.setSourceExternalStream( true );
                    ZipInputStream zis = zip.getInputStream( ren_entry );
                    if( zis == null ) {
                        Log.e( TAG, "Can't get input stream of " + ren_entry.getFileName() );
                        sendResult( ctx.getString( R.string.fail ) );
                        return;
                    }
                    zip.addStream( zis, parameters );
                    zip.removeFile( ren_entry );
                    if( waitCompletion() ) {
                        sendResult( ctx.getString( R.string.done ) );
                        return;
                    }
                } catch( Exception e ) {
                    error( e.getMessage() );
                } finally {
                    ZipAdapter.this.zip = null;
                }
                sendResult( ctx.getString( R.string.fail ) );
                super.run();
            }
        }
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
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "";
        {
            if( position == 0 ) {
                item.name = parentLink;
            } else {
                if( items != null && position > 0 && position <= items.length ) {
                    FileHeader zip_entry = items[position - 1];
                    item.dir = zip_entry.isDirectory();
                    String name = fixName( zip_entry );

                    int lsp = name.lastIndexOf( SLC, item.dir ? name.length() - 2 : name.length() );
                    item.name = lsp > 0 ? name.substring( lsp + 1 ) : name;
                    if( !item.dir )
                        item.size = zip_entry.getUncompressedSize();
                    int item_time = zip_entry.getLastModFileTime();
                    item.date = item_time > 0 ? new Date( Zip4jUtil.dosToJavaTme( item_time ) ) : null;
                    item.attr = zip_entry.getFileComment();
                }
            }
        }
        return item;
    }

    private final String fixName( FileHeader entry ) {

        try {
            return entry.getFileName();
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    private final FileHeader[] bitsToItems( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            FileHeader[] subItems = new FileHeader[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    subItems[j++] = items[cis.keyAt( i ) - 1];
            return subItems;
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
        return null;
    }

    private final boolean checkReadyness() {
        // FIXME check that the zip file is processed by some other engine!!!!!!!!!!!!
        if( false /*How?*/ ) { 
            notify( ctx.getString( R.string.busy ), Commander.OPERATION_FAILED ); 
            return false; 
        }
        return true;
    }

    public class ZipItemPropComparator implements Comparator<FileHeader> {
        int type;
        boolean case_ignore, ascending;

        public ZipItemPropComparator(int type_, boolean case_ignore_, boolean ascending_) {
            type = type_;
            case_ignore = case_ignore_;
            ascending = ascending_;
        }

        @Override
        public int compare( FileHeader f1, FileHeader f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            int ext_cmp = 0;
            switch( type ) {
            case CommanderAdapter.SORT_EXT:
                ext_cmp = case_ignore ? Utils.getFileExt( f1.getFileName() ).compareToIgnoreCase(
                        Utils.getFileExt( f2.getFileName() ) ) : Utils.getFileExt( f1.getFileName() ).compareTo(
                        Utils.getFileExt( f2.getFileName() ) );
                break;
            case CommanderAdapter.SORT_SIZE:
                ext_cmp = f1.getUncompressedSize() - f2.getUncompressedSize() < 0 ? -1 : 1;
                break;
            case CommanderAdapter.SORT_DATE:
                ext_cmp = f1.getLastModFileTime() - f2.getLastModFileTime() < 0 ? -1 : 1;
                break;
            }
            if( ext_cmp == 0 )
                ext_cmp = case_ignore ? f1.getFileName().compareToIgnoreCase( f2.getFileName() ) : f1.getFileName().compareTo(
                        f2.getFileName() );
            return ascending ? ext_cmp : -ext_cmp;
        }
    }

    @Override
    protected void reSort() {
        if( items == null )
            return;
        ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING, ( mode & MODE_CASE ) != 0, ascending );
        Arrays.sort( items, comp );
    }

    @Override
    public Item getItem( Uri u ) {
        try {
            String zip_path = u.getPath();
            if( zip_path == null )
                return null;
            if( zip == null )
                zip = createZipFileInstance( u );
            String entry_name = u.getFragment();
            if( entry_name != null ) {
                FileHeader zip_entry = zip.getFileHeader( entry_name );
                if( zip_entry != null ) {
                    String name = fixName( zip_entry );
                    Item item = new Item();
                    item.dir = zip_entry.isDirectory();
                    int lsp = name.lastIndexOf( SLC, item.dir ? name.length() - 2 : name.length() );
                    item.name = lsp > 0 ? name.substring( lsp + 1 ) : name;
                    item.size = zip_entry.getUncompressedSize();
                    long item_time = zip_entry.getLastModFileTime();
                    item.date = item_time > 0 ? new Date( item_time ) : null;
                    return item;
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public InputStream getContent( Uri u, long offset ) {
        try {
            String zip_path = u.getPath();
            if( zip_path == null )
                return null;
            String opened_zip_path = uri != null ? uri.getPath() : null;
            if( opened_zip_path == null || zip == null )
                zip = createZipFileInstance( u );
            else if( !zip_path.equalsIgnoreCase( opened_zip_path ) )
                return null; // do not want to reopen the current zip to
                             // something else!
            String entry_name = u.getFragment();
            if( entry_name != null ) {
                FileHeader cachedEntry = zip.getFileHeader( entry_name );
                if( cachedEntry != null ) {
                    InputStream is = zip.getInputStream( cachedEntry );
                    if( offset > 0 )
                        is.skip( offset );
                    return is;
                }
            }
        } catch( Throwable e ) {
            Log.e( TAG, "Uri:" + u, e );
        }
        return null;
    }

    @Override
    public void closeStream( Closeable is ) {
        if( is != null ) {
            try {
                is.close();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
    }
}
