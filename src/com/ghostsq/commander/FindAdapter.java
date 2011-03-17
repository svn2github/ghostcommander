package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import com.ghostsq.commander.FSAdapter.FilePropComparator;

import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;

public class FindAdapter extends FSAdapter {
    public final static String TAG = "FindAdapter";
    private Uri uri;

    public FindAdapter( Commander c ) {
        super( c );
        parentLink = PLS;
    }
    @Override
    public String getType() {
        return "find";
    }
    @Override
    public int setMode( int mask, int mode_ ) {
        mode_ &= ~MODE_WIDTH;
        return super.setMode( mask, mode_ );
    }

    @Override
    public boolean readSource( Uri uri_, String pass_back_on_done ) {
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
                    commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                    worker = new SearchEngine( handler, match, path, pass_back_on_done );
                    worker.start();
                    return true;
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "FindAdapter.readSource() exception: ", e );
        }
        Log.e( TAG, "FindAdapter unable to read by the URI '" + ( uri == null ? "null" : uri.toString() ) + "'" );
        uri = null;
        return false;
    }
    
    
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( move && to instanceof FSAdapter ) {
            return false;
        }
        else {
            return to.receiveItems( bitsToNames( cis ), move ? MODE_MOVE : MODE_COPY );
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
    public Uri getUri() {
        return uri;
    }
    @Override
    public String toString() {
        return uri != null ? Uri.decode( uri.toString() ) : "";
    }

    @Override
    public boolean receiveItems( String[] fileURIs, int move_mode ) {
        commander.notifyMe( new Commander.Notify( commander.getContext().getString( R.string.not_supported ), 
                                Commander.OPERATION_FAILED ) );
        return false;
    }

    @Override
    public void setIdentities( String name, String pass ) {
        commander.showError( commander.getContext().getString( R.string.not_supported ) );
    }

    class SearchEngine extends Engine {
        private String[] cards;
        private String path; 
        private ArrayList<File> result;
        private int depth = 0;
        private String pass_back_on_done;
        
        SearchEngine( Handler h, String match, String path_, String pass_back_on_done_ ) {
            super( h );
            cards = Utils.prepareWildcard( match );
            path = path_;
            pass_back_on_done = pass_back_on_done_;
        }
        @Override
        public void run() {
            try {
                result = new ArrayList<File>();
                searchInFolder( new File( path ) );
                sendProgress( tooLong( 8 ) ? commander.getContext().getString( R.string.search_done ) : null, 
                        Commander.OPERATION_COMPLETED, pass_back_on_done );
            } catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED, pass_back_on_done );
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
                    
                    if( Utils.match( f.getName(), cards ) ) {
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
                Log.e( TAG, "Exception on search: ", e );
            }
        }
        public final FileItem[] getItems( int mode ) {
            if( result == null ) return null;
            File[] files_ = new File[result.size()];
            result.toArray( files_ );
            return filesToItems( files_ );
        }       
    }
    protected void onComplete( Engine engine ) {
        try {
            if( engine instanceof SearchEngine ) {
                SearchEngine list_engine = (SearchEngine)engine;
                items = list_engine.getItems( mode );
                numItems = items != null ? items.length + 1 : 1;
                notifyDataSetChanged();
                startThumbnailCreation();
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    @Override
    public Object getItem( int position ) {
        try {
            Object o = super.getItem( position );
            if( o != null ) {
                if( o instanceof FileItem ) {
                    FileItem fi = (FileItem)o;
                    fi.name = fi.f.getAbsolutePath();
                }
                return o;
            }
        } catch( Exception e ) {
            Log.e( TAG, "getItem() Exception" );
        }
        return null;
    }    
    
    @Override
    public boolean isButtonActive( int brId ) {
        if( brId == R.id.F7 ) return false;
        return super.isButtonActive( brId );
    }
}
