package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import com.ghostsq.commander.CommanderAdapterBase.Item;
import com.ghostsq.commander.FSAdapter.FileEx;
import com.ghostsq.commander.FSAdapter.FilePropComparator;
import com.ghostsq.commander.FTPAdapter.ListEngine;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

public class FindAdapter extends CommanderAdapterBase {
    private Uri uri;
    private File[] items;

    public FindAdapter() {
        parentLink = "..";
    }

    public FindAdapter( Commander c, int mode ) {
        super( c, mode );
        parentLink = "..";
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public String toString() {
        return uri.toString();
    }

    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            commander.Navigate( Uri.parse( uri != null ? uri.getPath() : "/sdcard" ), null );
        }   
        else {
            if( uri == null ) return;
            File file = items[position - 1];
            if( file.isDirectory() ) {
                String dirName = uri.getPath();
                if( dirName.charAt( dirName.length() - 1 ) != File.separatorChar )
                    dirName += File.separatorChar;
                commander.Navigate( Uri.parse( dirName + file.getName() + File.separatorChar ), null );
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
    public boolean readSource( Uri uri_ ) {
        try {
            if( uri_ != null && uri_.getScheme().compareTo( "find" ) == 0 ) {
                String  path = uri_.getPath();
                String match = uri_.getQueryParameter( "q" );
                if( path != null && path.length() > 0 && match != null && match.length() > 0  ) {
                    uri = uri_;
                    worker = new SearchEngine( handler, match, path );
                    worker.start();
                    return true;
                }
            }
        } catch( Exception e ) {
            System.err.println( "FindAdapter.readSource() exception: " + e );
        }
        System.err.println( "FindAdapter unable to read by the URI '" + ( uri_ == null ? "null" : uri_.toString() ) + "'" );
        return false;
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
        // TODO Auto-generated method stub

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
        if( position == 0 ) {
            item.name = parentLink;
        }
        else {
            if( items != null && position-1 < items.length ) {
                synchronized( items ) {
                    File f = items[position - 1];
                    try {
                        item.name = f.getAbsolutePath();
                        item.dir  = f.isDirectory();
                        item.size = item.dir ? 0 : f.length();
                        ListView flv = (ListView)parent;
                        SparseBooleanArray cis = flv.getCheckedItemPositions();
                        item.sel = cis.get( position );
                        long msFileDate = f.lastModified();
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

    class SearchEngine extends Engine {
        private String match, path; 
        private ArrayList<File> result;
        private int depth = 0;
        
        SearchEngine( Handler h, String match_, String path_ ) {
            super( h );
            match = match_;
            path = path_;
        }
        
        public ArrayList<File> getItems() {
            return result;
        }       
        
        @Override
        public void run() {
            try {
                result = new ArrayList<File>();
                searchInFolder( new File( path ) );
                if( result.size() == 0 ) {
                    sendProgress( commander.getContext().getString( R.string.nothing_found ), 
                            Commander.OPERATION_FAILED );
                    return;
                }
                sendProgress( null, Commander.OPERATION_COMPLETED );
            } catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
            }
        }
        protected final void searchInFolder( File dir ) throws Exception {
            try {
                String dir_path = dir.getAbsolutePath();
                if( dir_path.compareTo( "/sys" ) == 0 ) return;
                if( dir_path.compareTo( "/dev" ) == 0 ) return;
                if( dir_path.compareTo( "/proc" ) == 0 ) return;
                File[] subfiles = dir.listFiles();
                for( int i = 0; i < subfiles.length; i++ ) {
                    if( stop || isInterrupted() ) 
                        throw new Exception( commander.getContext().getString( R.string.interrupted ) );
                    File f = subfiles[i];
                    if( f.getName().indexOf( match ) >= 0 ) { // TODO: support the wildcards
                        result.add( f );
                    }
                    if( f.isDirectory() ) {
                        if( depth++ > 30 )
                            throw new Exception( commander.getContext().getString( R.string.too_deep_hierarchy ) );
                        searchInFolder( f );
                        depth--;
                    }
                }
            } catch( Exception e ) {
                System.err.println( "exception: " + e );
            }
        }
    }
    protected void onComplete( Engine engine ) {
        try {
            if( engine instanceof SearchEngine ) {
                SearchEngine list_engine = (SearchEngine)engine;
                items = null;
                ArrayList<File> items_a = list_engine.getItems();
                if( ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                    int cnt = 0;
                    for( int i = 0; i < items_a.size(); i++ )
                        if( items_a.get(i).getName().charAt( 0 ) != '.' )
                            cnt++;
                    items = new File[cnt];
                    int j = 0;
                    for( int i = 0; i < items_a.size(); i++ ) {
                        File f = items_a.get(i);
                        if( f.getName().charAt( 0 ) != '.' )
                            items[j++] = f;
                    }
                }
                else {
                    items = new File[items_a.size()]; 
                    items_a.toArray( items );
                }
                notifyDataSetChanged();
                Context context = commander.getContext();
                Toast.makeText( context, context.getString( R.string.search_done ), 
                        Toast.LENGTH_LONG).show();
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
}
