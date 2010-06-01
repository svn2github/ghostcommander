package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Pattern;

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
    public void setMode( int mask, int mode_ ) {
        super.setMode( mask, mode_ );
        mode &= ~MODE_WIDTH;
    }
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( move && to instanceof FSAdapter ) {
/*
            try {
                String dest_folder = to.toString();
                String[] files_to_move = bitsToNames( cis );
                if( files_to_move == null )
                    return false;
                return moveFiles( files_to_move, dest_folder );
            }
            catch( SecurityException e ) {
                commander.showError( "Unable to move a file because of security reasons: " + e );
                return false;
            }
*/
            return false;
        }
        else {
            return to.receiveItems( bitsToNames( cis ), move );
        }
    }

    @Override
    public boolean createFile( String fileURI ) {
        commander.showError( commander.getContext().getString( R.string.not_supported ) );
        return false;
    }

    @Override
    public void createFolder( String string ) {
        commander.showError( commander.getContext().getString( R.string.not_supported ) );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
            int cnt = 0;
            for( int i = 0; i < cis.size(); i++ ) {
                if( cis.valueAt( i ) ) {
                    int pos = cis.keyAt( i ) - 1;
                    if( pos >= 0 ) {
                        File f = items[pos];
                        if( f.isDirectory() ) 
                            cnt += Utils.deleteDirContent( f );
                        if( f.delete() )
                            cnt++;
                    }
                }
            }
            commander.notifyMe( commander.getContext().getString( R.string.deleted, cnt ),
                    Commander.OPERATION_COMPLETED_REFRESH_REQUIRED, 0 );
            return true;
        }
        catch( SecurityException e ) {
            commander.showError( "Unable to delete: " + e );
        }
        return false;
    }

    @Override
    public String getItemName( int position, boolean full ) {
        if( position == 0 )
            return parentLink;
        if( position < 0 || position > items.length || items == null )
            return null;
        else
            return items[position - 1].getAbsolutePath();
    }

    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public String toString() {
        return uri != null ? Uri.decode( uri.toString() ) : "";
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
            if( worker != null ) worker.reqStop();
            if( uri_ != null )
                uri = uri_;
            if( uri == null )
                return false;
            if( uri.getScheme().compareTo( "find" ) == 0 ) {
                String  path = uri.getPath();
                String match = uri.getQueryParameter( "q" );
                if( path != null && path.length() > 0 && match != null && match.length() > 0  ) {
                    commander.notifyMe( null, Commander.OPERATION_STARTED, 0 );
                    worker = new SearchEngine( handler, match, path );
                    worker.start();
                    return true;
                }
            }
        } catch( Exception e ) {
            System.err.println( "FindAdapter.readSource() exception: " + e );
        }
        System.err.println( "FindAdapter unable to read by the URI '" + ( uri == null ? "null" : uri.toString() ) + "'" );
        uri = null;
        return false;
    }

    @Override
    public boolean receiveItems( String[] fileURIs, boolean move ) {
        commander.notifyMe( commander.getContext().getString( R.string.not_supported ), Commander.OPERATION_FAILED, 0 );
        return false;
    }

    @Override
    public boolean renameItem( int position, String newName ) {
        if( position <= 0 || position > items.length )
            return false;
        try {
            return items[position - 1].renameTo( new File( newName ) );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            return false;
        }
    }

    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        commander.notifyMe( commander.getContext().getString( R.string.not_supported ), Commander.OPERATION_FAILED, 0 );
    }

    @Override
    public void setIdentities( String name, String pass ) {
        commander.showError( commander.getContext().getString( R.string.not_supported ) );
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
        private String[] cards;
        private String path; 
        private ArrayList<File> result;
        private int depth = 0;
        
        SearchEngine( Handler h, String match, String path_ ) {
            super( h );
            cards = match.split( "\\*" );
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
                sendProgress( tooLong( 8 ) ? commander.getContext().getString( R.string.search_done ) : null, 
                        Commander.OPERATION_COMPLETED );
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
                if( subfiles == null )
                    return;
                for( int i = 0; i < subfiles.length; i++ ) {
                    if( stop || isInterrupted() ) 
                        throw new Exception( commander.getContext().getString( R.string.interrupted ) );
                    File f = subfiles[i];
                    
                    if( wildCardMatch( f.getName() ) ) {
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
        private boolean wildCardMatch( String text ) {
            int pos = 0;
            for( String card : cards ) {
                int idx = text.indexOf( card, pos );
                if( idx < 0 )
                    return false;
                pos = idx + card.length();
            }
            return true;
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
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
}
