package com.ghostsq.commander;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbFile;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.CommanderAdapter;
import com.ghostsq.commander.CommanderAdapterBase;
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
        
        ListEngine( Handler h, String url ) {
            super( h );
            smb_url = url;
        }
        public SmbFile[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            if( uri == null ) {
                sendProgress( "Nothing to read", Commander.OPERATION_FAILED );
                return;
            }
            try {
                if( smb_url.charAt( smb_url.length() - 1 ) != '/' )
                    smb_url += SLS;
                SmbFile f = new SmbFile( smb_url );
                items_tmp = f.listFiles();
                SmbFilePropComparator comp = new SmbFilePropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0 );
                Arrays.sort( items_tmp, comp );
                
                parentLink = uri.getPath().length() <= 1 ? SLS : "..";
                
                Log.i( TAG, "got the files list" );
                sendProgress( null, Commander.OPERATION_COMPLETED );
                return;
            } catch( MalformedURLException e ) {
                sendProgress( "Malformed URL: " + e.getMessage(), Commander.OPERATION_FAILED );
            } catch( SmbAuthException e ) {
                sendProgress( uri.toString(), Commander.OPERATION_FAILED_LOGIN_REQUIRED );
            } catch( SmbException e ) {
                sendProgress( "Samba Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
            } catch( Exception e ) {
                sendProgress( "Unknown Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
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
            if( tmp_items != null && ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                int cnt = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        cnt++;
                items = new SmbFile[cnt];
                int j = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        items[j++] = tmp_items[i]; 
            }
            else
                items = tmp_items;
            notifyDataSetChanged();
        }
    }
    
    @Override
    public boolean readSource( Uri tmp_uri, String passBackOnDone ) {
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
            
            String smb_url = uri.toString();
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
            worker = new ListEngine( handler, smb_url );
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
        // TODO Auto-generated method stub
        return false;
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
        // TODO Auto-generated method stub
        return false;
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
            if( uri != null && parentLink != "/" ) {
                String path = uri.getPath();
                int len_ = path.length()-1;
                if( len_ > 0 ) {
                    if( path.charAt( len_ ) == '/' )
                        path = path.substring( 0, len_ );
                    path = path.substring( 0, path.lastIndexOf( '/' ) );
                    if( path.length() == 0 )
                        path = "/";
                    commander.Navigate( uri.buildUpon().path( path ).build(), uri.getLastPathSegment() );
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
                    cur = "/";
                else
                    if( cur.charAt( cur.length()-1 ) != '/' )
                        cur += "/";
                commander.Navigate( uri.buildUpon().path( cur + item.getName() ).build(), null );
            }
        } catch( SmbException e ) {
            Log.e( TAG, "Samba exception:", e );
        }
    }

    @Override
    public boolean receiveItems( String[] fileURIs, boolean move ) {
        // TODO Auto-generated method stub
        return false;
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
