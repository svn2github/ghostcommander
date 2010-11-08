package com.ghostsq.commander;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbFile;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;
import com.ghostsq.commander.FTPAdapter.CopyEngine;
import com.ghostsq.commander.FTPAdapter.CopyToEngine;
import com.ghostsq.commander.FTPAdapter.DelEngine;

import org.apache.http.auth.UsernamePasswordCredentials;

import jcifs.smb.SmbException;

public class SMBAdapter extends CommanderAdapterBase {
    static final String TAG = "SMB"; 
    private SmbFile[] items;
    public  Uri       uri = null;
    public  UsernamePasswordCredentials credentials = null;

    class ListEngine extends Engine {
        private String smb_url = null; 
        private SmbFile[] items_tmp = null;
        public  String pass_back_on_done;
        ListEngine( Handler h, String url, String pass_back_on_done_ ) {
            super( h );
            smb_url = url;
            pass_back_on_done = pass_back_on_done_;
        }
        public SmbFile[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            if( uri == null ) {
                sendProgress( "Nothing to read", Commander.OPERATION_FAILED, pass_back_on_done );
                return;
            }
            try {
                if( smb_url.charAt( smb_url.length() - 1 ) != SLC )
                    smb_url += SLS;
                SmbFile f = new SmbFile( smb_url );
                items_tmp = f.listFiles();
                SmbFilePropComparator comp = new SmbFilePropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
                Arrays.sort( items_tmp, comp );
                
                parentLink = uri.getPath().length() <= 1 ? SLS : "..";
                
                sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
                return;
            } catch( MalformedURLException e ) {
                sendProgress( "Malformed URL: " + e.getMessage(), Commander.OPERATION_FAILED, pass_back_on_done );
            } catch( SmbAuthException e ) {
                sendProgress( uri.toString(), Commander.OPERATION_FAILED_LOGIN_REQUIRED );
            } catch( SmbException e ) {
                sendProgress( "Samba Exception: " + e.getMessage(), Commander.OPERATION_FAILED, pass_back_on_done );
            } catch( Exception e ) {
                sendProgress( "Unknown Exception: " + e.getMessage(), Commander.OPERATION_FAILED, pass_back_on_done );
            } finally {
                super.run();
            }
            //sendProgress( "Can't open this Samba share", Commander.OPERATION_FAILED );
        }
    }
    protected void onComplete( Engine engine ) {
        if( engine instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)engine;
            SmbFile[] tmp_items = list_engine.getItems();
            
            if( tmp_items != null ) {
                int cnt = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( toShow( tmp_items[i] ) )
                        cnt++;
                items = new SmbFile[cnt];
                int j = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( toShow( tmp_items[i] ) )
                        items[j++] = tmp_items[i]; 
            }
            else
                items = tmp_items;
            notifyDataSetChanged();
        }
    }
    protected final boolean toShow( SmbFile f ) {
        try {
            if( ( mode & MODE_HIDDEN ) == HIDE_MODE ) { 
                if( f.getName().charAt( 0 ) == '.' ) return false;
                if( ( f.getAttributes() & SmbFile.ATTR_HIDDEN ) != 0 ) return false;
            }
            int type = f.getType();
            if( type == SmbFile.TYPE_PRINTER || type == SmbFile.TYPE_NAMED_PIPE ) return false;
        } catch( SmbException e ) {
            Log.e( TAG, "Exception", e );
        }
        return true;
    }
    private final SmbFile[] bitsToItems( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            SmbFile[] subItems = new SmbFile[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    subItems[j++] = items[ cis.keyAt( i ) - 1 ];
            return subItems;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToNames()'s Exception: " + e.getMessage() );
        }
        return null;
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
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            UrlQuerySanitizer urlqs = new UrlQuerySanitizer();
            String smb_url = urlqs.unescape( uri.toString() );
            if( credentials != null ) {
                String u = credentials.getUserName(); 
                String p = credentials.getPassword();
                int at_pos = smb_url.indexOf( '@' );
                if( at_pos > 0 )
                    smb_url = "smb://" + u + ":" + p + smb_url.substring( at_pos );
                else
                    smb_url = "smb://" + u + ":" + p + "@" + smb_url.substring( 6 );
                credentials = null;
                uri = Uri.parse( smb_url );
            }
            worker = new ListEngine( handler, smb_url, pass_back_on_done );
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
    
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        try {
            if( to instanceof FSAdapter ) {
                File dest = new File( to.toString() );
                if( dest.exists() && dest.isDirectory() ) {
                    if( worker == null ) {
                        SmbFile[] subItems = bitsToItems( cis );
                        if( subItems != null ) {
                            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                            worker = new CopyFromEngine( handler, subItems, dest, move );
                            worker.start();
                            return true;
                        }
                    }
                }
            } else {
                commander.notifyMe( new Commander.Notify( "Currently can copy only to the local storage", Commander.OPERATION_FAILED ) );
                return false;
            }
            commander.notifyMe( new Commander.Notify( "Failed to proceed.", Commander.OPERATION_FAILED ) );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }
   
  class CopyFromEngine extends Engine // From a SMB share to local
  {
        protected final static int BLOCK_SIZE = 100000;
        private SmbFile[] mList;
        private File      dest_folder;
        private boolean   move;
        CopyFromEngine( Handler h, SmbFile[] list, File dest, boolean move_ ) {
            super( h );
            mList = list;
            dest_folder = dest;
            move = move_;
        }
        @Override
        public void run() {
            int total = copyFiles( mList, "" );
            sendResult( Utils.getOpReport( total, "retrieved" ) );
            super.run();
        }
        private final int copyFiles( SmbFile[] list, String path ) {
            int counter = 0;
            try {
                long dir_size = 0, byte_count = 0;
                for( int i = 0; i < list.length; i++ ) {
                    SmbFile f = list[i];               
                    if( !f.isDirectory() )
                        dir_size += f.length();
                }
                double conv = 100./(double)dir_size;
                for( int i = 0; i < list.length; i++ ) {
                    SmbFile f = list[i];
                    if( f == null || !f.exists() ) continue;
                    String file_name = f.getName();
                    String rel_name = path + file_name;
                    File   dest_file = new File( dest_folder, rel_name );
                    
                    if( f.isDirectory() ) {
                        sendProgress( "Processing folder '" + rel_name + "'...", 0 );
                        if( !dest_file.mkdir() ) {
                            if( !dest_file.exists() || !dest_file.isDirectory() ) {
                                errMsg = "Can't create folder \"" + dest_file.getCanonicalPath() + "\"";
                                break;
                            }
                        }
                        SmbFile[] subItems = f.listFiles();
                        if( subItems == null ) {
                            errMsg = "Failed to get the file list of the subfolder '" + rel_name + "'.\n FTP log:\n\n";
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
                        InputStream in = f.getInputStream();
                        FileOutputStream out = new FileOutputStream( dest_file );
                        byte buf[] = new byte[BLOCK_SIZE];
                        int  n = 0;
                        int  so_far = (int)(byte_count * conv);
                        while( true ) {
                            n = in.read( buf );
                            if( n < 0 ) break;
                            out.write( buf, 0, n );
                            byte_count += n;
                            sendProgress( "Retrieving \n'" + rel_name + "'...", so_far, (int)(byte_count * conv) );
                            if( stop || isInterrupted() ) {
                                in.close();
                                out.close();
                                dest_file.delete();
                                errMsg = "File '" + dest_file.getName() + "' was not completed, delete.";
                                break;
                            }
                        }
                    }
                    counter++;
                    if( move )
                        f.delete();
                    if( stop || isInterrupted() ) {
                        error( "Canceled by a request." );
                        break;
                    }
                    if( i >= list.length-1 )
                        sendProgress( "Retrieved \n'" + rel_name + "'   ", (int)(byte_count * conv) );
                }
            }
            catch( SmbException e ) {
                errMsg = "Exception: " + e.getMessage();
                Throwable t = e.getCause();
                if( t != null )
                    errMsg += " (" + t.getMessage() + ")";
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
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void createFolder( String string ) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
            if( worker != null ) return false;
            SmbFile[] subItems = bitsToItems( cis );
            if( subItems != null ) {
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                worker = new DelEngine( handler, subItems );
                worker.start();
                return true;
            }
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }
    class DelEngine extends Engine {
        SmbFile[] mList;
        int so_far = 0;
        DelEngine( Handler h, SmbFile[] list ) {
            super( h );
            mList = list;
        }
        @Override
        public void run() {
            try {
                int total;
                total = deleteFiles( mList );
                sendResult( total > 0 ? "Deleted files/folders: " + total : "Nothing was deleted" );
                super.run();
            } catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
            }
        }
        private final int deleteFiles( SmbFile[] l ) throws Exception {
            if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./(double)num;
            for( int i = 0; i < num; i++ ) {
                if( stop || isInterrupted() )
                    throw new Exception( "Interrupted" );
                SmbFile f = l[i];
                if( f.isDirectory() )
                    cnt += deleteFiles( f.listFiles() );
                f.delete();
                cnt++;
                sendProgress( "Deleting \n'" + f.getName() + "'...", so_far, (int)(i * conv) );
            }
            so_far += cnt; 
            return cnt;
        }
    }

    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            return items[position-1].getName();
        }
        return null;
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return uri != null ? uri.toString() : "";
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri != null && parentLink != SLS ) {
                String path = uri.getPath();
                int len_ = path.length()-1;
                if( len_ > 0 ) {
                    if( path.charAt( len_ ) == SLC )
                        path = path.substring( 0, len_ );
                    path = path.substring( 0, path.lastIndexOf( SLC ) );
                    if( path.length() == 0 )
                        path = SLS;
                    commander.Navigate( uri.buildUpon().path( path ).build(), uri.getLastPathSegment() + SLS );
                }
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        SmbFile item = items[position - 1];
        
        try {
            if( item.isDirectory() ) {
                String cur = uri.getPath();
                if( cur == null || cur.length() == 0 ) 
                    cur = SLS;
                else
                    if( cur.charAt( cur.length()-1 ) != SLC )
                        cur += SLS;
                commander.Navigate( uri.buildUpon().path( cur + item.getName() ).build(), null );
            }
        } catch( SmbException e ) {
            Log.e( TAG, "Samba exception:", e );
        }
    }

    @Override
    public boolean receiveItems( String[] fileURIs, boolean move ) {
        try {
            if( worker != null ) return false;
            if( fileURIs == null || fileURIs.length == 0 ) {
                commander.notifyMe( new Commander.Notify( "Nothing to copy", Commander.OPERATION_FAILED ) );
                return false;
            }
            File[] list = Utils.getListOfFiles( fileURIs );
            if( list == null ) {
                commander.notifyMe( new Commander.Notify( "Something wrong with the files", Commander.OPERATION_FAILED ) );
                return false;
            }
            commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
            worker = new CopyToEngine( handler, list, move );
            worker.start();
            return true;
        } catch( Exception e ) {
            commander.notifyMe( new Commander.Notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED ) );
        }
        return false;
    }
    
    class CopyToEngine extends Engine {
        protected final static int BLOCK_SIZE = 100000;
        File[] mList;
        int     basePathLen;
        boolean move = false;
        
        CopyToEngine( Handler h, File[] list, boolean move_ ) {
            super( h );
            mList = list;
            basePathLen = list[0].getParent().length() + 1;
            move = move_;
        }

        @Override
        public void run() {
            int cnt = copyFiles( mList, uri.toString() );
            sendResult( Utils.getCopyReport( cnt ) );
            super.run();
        }

        private final int copyFiles( File[] list, String base_url ) {
            int counter = 0;
            try {
                long num = list.length;
                long dir_size = 0, byte_count = 0;
                for( int i = 0; i < num; i++ ) {
                    File f = list[i];               
                    if( !f.isDirectory() )
                        dir_size += f.length();
                }
                double conv = 100./(double)dir_size;
                for( int i = 0; i < num; i++ ) {
                    if( stop || isInterrupted() ) {
                        errMsg = "Canceled";
                        break;
                    }
                    File f = list[i];
                    if( f != null && f.exists() ) {
                        if( f.isFile() ) {
                            sendProgress( "Uploading file \n'" + f.getName() + "'", 0 );
                            FileInputStream in = new FileInputStream( f );
                            SmbFile new_file = new SmbFile( base_url + f.getName() );
                            new_file.createNewFile();
                            OutputStream out = new_file.getOutputStream();
                            byte buf[] = new byte[BLOCK_SIZE];
                            int  n = 0;
                            int  so_far = (int)(byte_count * conv);
                            while( true ) {
                                n = in.read( buf );
                                if( n < 0 ) break;
                                out.write( buf, 0, n );
                                byte_count += n;
                                sendProgress( "Uploading \n'" + f.getAbsolutePath() + 
                                          "' to '" + new_file.getName() + "'" , so_far, (int)(byte_count * conv) );
                                if( stop || isInterrupted() ) {
                                    in.close();
                                    out.close();
                                    errMsg = "File '" + new_file.getName() + "' was not completed, delete.";
                                    new_file.delete();
                                    break;
                                }
                            }
                        }
                        else
                        if( f.isDirectory() ) {
                            sendProgress( "Folder '" + f.getName() + "'...", 0 );
                            counter += copyFiles( f.listFiles(), base_url + f.getName() + "/" );
                            if( errMsg != null ) break;
                        }
                        counter++;
                        if( move && !f.delete() ) {
                            errMsg = "Unable to delete '" + f.getCanonicalPath() + "'.";
                            break;
                        }
                    }
                }
            }
            catch( Exception e ) {
                e.printStackTrace();
                errMsg = "IOException: " + e.getMessage();
            }
            return counter;
        }
    }

    @Override
    public boolean renameItem( int position, String newName ) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setIdentities( String name, String pass ) {
        credentials = new UsernamePasswordCredentials( name, pass ); 
    }

    @Override
    public int getCount() {
        return items != null ? items.length + 1 : 1;
    }

    @Override
    public Object getItem( int position ) {
        if( worker != null )
            return null;
        return items != null && position < items.length ? items[position] : null;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = new Item();
        item.name = "";
        {
            if( position == 0 ) {
                item.name = parentLink;
            }
            else {
                if( items != null && position > 0 && position <= items.length ) {
                    try {
                        SmbFile the_item;
                        the_item = items[position - 1];
                        item.dir = the_item.isDirectory();
                        item.name = the_item.getName();
                        item.size = item.dir ? 0 : the_item.length();
                        ListView flv = (ListView)parent;
                        SparseBooleanArray cis = flv.getCheckedItemPositions();
                        item.sel = cis.get( position );
                        long item_time = the_item.getDate();
                        item.date = item_time > 0 ? new Date( item_time ) : null;
                    } catch( SmbException e ) {
                        Log.e( TAG, "Samba exception", e );
                    }
                }
            }
        }
        return getView( convertView, parent, item );
    }

    public class SmbFilePropComparator implements Comparator<SmbFile> {
        int type;
        boolean case_ignore;
        public SmbFilePropComparator( int type_, boolean case_ignore_ ) {
            type = type_;
            case_ignore = case_ignore_;
        }
        @Override
        public int compare( SmbFile f1, SmbFile f2 ) {
            try {
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
                    ext_cmp = f1.length() - f2.length() < 0 ? -1 : 1;
                    break;
                case SORT_DATE:
                    ext_cmp = f1.getDate() - f2.getDate() < 0 ? -1 : 1;
                    break;
                }
                if( ext_cmp != 0 )
                    return ext_cmp;
            } catch( SmbException e ) {
                Log.e( TAG, "Samba exception", e );
            }
            return f1.getName().compareTo( f2.getName() );
        }
    }
}
