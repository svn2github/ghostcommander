package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Utils;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ContentAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private final static String TAG    = "ContentAdapter";    // MediaStore
    public  final static String SCHEME = "content:";

    public  final static int MEDIA     = 0; 
    public  final static int ALBUMS    = 1; 
    public  final static int ARTISTS   = 2; 
    public  final static int GENRES    = 3; 
    public  final static int PLAYLISTS = 4; 
    
    private   int    content_type;
    private   Uri    content_uri;
    private   Stack<Uri> parents = new Stack<Uri>();
    private   String name;
    protected Item[] items;
    private   ThumbnailsThread tht = null;
    
    public ContentAdapter( Context ctx_ ) {
        super( ctx_ );
        items = null;
    }

    @Override
    public String getScheme() {
        return "content";
    }
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case LOCAL:
        case F8:
            return true;
        case SEND:
        case FS:
        case F2:
        case F6:
            return false;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        if( Utils.str( name ) ) return name;
        Uri u = getUri();
        return u != null ? u.toString() : null;
    }

    @Override
    public int setMode( int mask, int val ) {
//        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
        if( ( mask & ( MODE_WIDTH ) ) == 0 )
            super.setMode( mask, val );
        return mode;
    }    
    
    @Override
    public Uri getUri() {
        return content_uri;
    }

    @Override
    public void setUri( Uri uri ) {
        content_uri = uri;
        content_type = getType( uri );
        if( uri != null && uri.equals( getBaseUri( content_type ) ) )
            parentLink = SLS;
    }

    public final static void populateHomeContextMenu( Context ctx, ContextMenu menu ) {
        final String vs = ctx.getString( R.string.view_title ); 
        menu.add( 0, ARTISTS,   0, vs + " \"" + getTypeName(ARTISTS) + "\"" );
        menu.add( 0, ALBUMS,    0, vs + " \"" + getTypeName(ALBUMS) + "\"" );
        menu.add( 0, GENRES,    0, vs + " \"" + getTypeName(GENRES) + "\"" );
        menu.add( 0, PLAYLISTS, 0, vs + " \"" + getTypeName(PLAYLISTS) + "\"" );
        return;
    }
    
    private final static String getTypeName( int id ) {
        if( id == ALBUMS )    return "Albums";
        if( id == ARTISTS )   return "Artists";
        if( id == GENRES )    return "Genres";
        if( id == PLAYLISTS ) return "Playlists";
        return null;
    }

    public final static Uri getBaseUri( int id ) {
        if( id == ALBUMS )    return MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        if( id == ARTISTS )   return MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
        if( id == GENRES )    return MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
        if( id == PLAYLISTS ) return MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
        return null;
    }
    
    private final static Uri getParentUri( Uri uri ) {
        String a_id = uri.getQueryParameter( MediaStore.Audio.AudioColumns.ALBUM_ID );
        if( Utils.str( a_id ) ) {
            return MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.buildUpon().appendEncodedPath( a_id ).build();            
        }
        List<String> ps = uri.getPathSegments();
        int ub = ps.size()-1;
        if( "albums".equalsIgnoreCase( ps.get( ub ) ) &&
           "artists".equalsIgnoreCase( ps.get( ub-2 ) ) )
            return MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.buildUpon().appendEncodedPath( ps.get( ub-1 ) ).build();
        if( "members".equalsIgnoreCase( ps.get( ub ) ) &&
             "genres".equalsIgnoreCase( ps.get( ub-2 ) ) )
            return MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI.buildUpon().appendEncodedPath( ps.get( ub-1 ) ).build();
        return null;
    }

    public final static int getType( Uri uri ) {
        if( uri == null ) return -1;
        if( Utils.str( uri.getQuery() ) ) return MEDIA;
        List<String> ps = uri.getPathSegments();
        for( int i = ps.size()-1; i > 0; i-- ) {
            String s = ps.get( i );
            if( "members".equalsIgnoreCase( s ) ) return MEDIA;
            if( "genres".equalsIgnoreCase( s ) ) return GENRES;
            if( "albums".equalsIgnoreCase( s ) ) return ALBUMS;
            if( "artists".equalsIgnoreCase( s ) ) return ARTISTS;
            if( "playlists".equalsIgnoreCase( s ) ) return PLAYLISTS;
        }
        return MEDIA;
    }

    private final static String[] getProjection( int id ) {
        if( id == ALBUMS ) return new String[] {
                              MediaStore.Audio.Albums._ID,
                              MediaStore.Audio.Albums.ALBUM,
                              MediaStore.Audio.Albums.NUMBER_OF_SONGS, 
                              MediaStore.Audio.Albums.ARTIST 
                            };
        if( id == ARTISTS ) return new String[] { 
                                MediaStore.Audio.Artists._ID,
                                MediaStore.Audio.Artists.ARTIST,
                                MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
                                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS
                            };
        if( id == GENRES )  return new String[] { 
                                MediaStore.Audio.Genres._ID,
                                MediaStore.Audio.Genres.NAME
                            };
        if( id == PLAYLISTS ) return new String[] { 
                                MediaStore.Audio.Playlists._ID,
                                MediaStore.Audio.Playlists.NAME
                            };
        if( id == MEDIA ) 
            return new String[] {
             MediaStore.MediaColumns._ID,
             MediaStore.MediaColumns.TITLE,
             MediaStore.MediaColumns.DATA,
             MediaStore.MediaColumns.DATE_MODIFIED,
             MediaStore.MediaColumns.SIZE,
             MediaStore.MediaColumns.MIME_TYPE
        };
        return null;
    }

    private final String getField( int id ) {
        if( id == content_type ) return BaseColumns._ID;
        if( id == ALBUMS )    return MediaStore.Audio.AudioColumns.ALBUM_ID;
        if( id == GENRES )    return MediaStore.Audio.Genres.Members.GENRE_ID;
        if( id == ARTISTS )   return MediaStore.Audio.AudioColumns.ARTIST_ID;
        if( id == PLAYLISTS ) return MediaStore.Audio.Playlists.Members.PLAYLIST_ID;
        return null;
    }
            
    @Override
    public boolean readSource( Uri new_uri, String pass_back_on_done ) {
        try {
            if( new_uri != null ) {
                setUri( new_uri );
            } else {
                if( content_uri == null )
                    return false;
            }
            ContentResolver cr = ctx.getContentResolver();
            name = null;
            Uri p_uri = getParentUri( content_uri );
            if( p_uri != null ) {
                try {
                    Log.d( TAG, " Parent Uri: " + p_uri );
                    Cursor cursor = cr.query( p_uri, getProjection( getType( p_uri ) ), null, null, null );
                    if( cursor != null && cursor.getCount() > 0 ) {
                        cursor.moveToFirst();
                        this.name = "\"" + cursor.getString( 1 ) + "\"";
                        cursor.close();
                    }
                } catch( Throwable e ) {
                    Log.e( TAG, "inner", e );
                }
            }
            if( name == null )
                name = getTypeName( content_type );
            
            String[] projection = getProjection( content_type );
            String   selection = null;
            String[] sel_args = null;
            if( content_type == MEDIA ) {
                String parent_id = content_uri.getQueryParameter( MediaStore.Audio.AudioColumns.ALBUM_ID );
                if( Utils.str( parent_id ) ) {
                    selection = MediaStore.Audio.AudioColumns.ALBUM_ID + " = ? ";
                    sel_args = new String[1];
                    sel_args[0] = parent_id;
                }
            }
            Log.d( TAG, "Quering type: " + content_type + " Uri: " + content_uri );
            Cursor cursor = cr.query( content_uri, projection, selection, sel_args, null );
            if( cursor != null ) {
                try {
                   if( cursor.getCount() > 0 ) {
                      cursor.moveToFirst();
                      ArrayList<Item> tmp_list = new ArrayList<Item>();
                      final int pci = cursor.getColumnIndex( MediaStore.MediaColumns.DATA );
                      final int sci = cursor.getColumnIndex( MediaStore.MediaColumns.SIZE );
                      final int dci = cursor.getColumnIndex( MediaStore.MediaColumns.DATE_MODIFIED );
                      final int mci = cursor.getColumnIndex( MediaStore.MediaColumns.MIME_TYPE );
                      final int aci = cursor.getColumnIndex( MediaStore.Audio.Albums.ARTIST );
                      final int nci = cursor.getColumnIndex( MediaStore.Audio.Albums.NUMBER_OF_SONGS );
                      boolean show_artists = content_type == ALBUMS && MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.equals( content_uri );  
                      do {
                          long item_id = cursor.getLong( 0 );
                          Item item = new Item();
                          if( content_type == ARTISTS )
                              item.origin = MediaStore.Audio.Artists.Albums.getContentUri( "external", item_id );
                          else
                          if( content_type == GENRES )
                              item.origin = MediaStore.Audio.Genres.Members.getContentUri( "external", item_id );
                          else
                          if( content_type == PLAYLISTS )
                              item.origin = MediaStore.Audio.Playlists.Members.getContentUri( "external", item_id );
                          else
                          if( content_type == ALBUMS ) {
                              item.origin = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                                  .encodedQuery( MediaStore.Audio.AudioColumns.ALBUM_ID + "=" + item_id ).build();
                              if( show_artists )
                                  item.attr = cursor.getString( aci );
                              item.size = cursor.getInt( nci );
                          } else
                              item.origin = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                                  .appendEncodedPath( String.valueOf( item_id ) ).build();
                          item.name = cursor.getString( 1 );
                          if( item.name == null ) {
                              //Log.e( TAG, "Item " + item_uri + " has no name" );
                              item.name = "(?)";
                          }
                          item.dir = content_type != MEDIA;
                          
                          if( MediaStore.MediaColumns.TITLE.equals( projection[1] ) ) {
                              item.attr = cursor.getString( pci );
                              item.size = cursor.getLong( sci );
                              item.date = new Date( cursor.getLong( dci ) * 1000 );

                              File f = new File( item.attr );
                              if( !f.exists() ) {
                                  item.colorCache = 0xFFFF0000;
                                  item.icon_id    = R.drawable.bad;
                              }
                              else
                                  item.icon_id = getIconId( item.attr );
                              item.mime = cursor.getString( mci );
                          }
                          
                          tmp_list.add( item );
                      } while( cursor.moveToNext() );
                      cursor.close();
                      
                      
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
    	    //startThumbnailCreation();
            notify( pass_back_on_done );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "outer", e );
            notify( e.getMessage(), Commander.OPERATION_FAILED );
        } catch( OutOfMemoryError err ) {
            Log.e( TAG, "Out Of Memory", err );
            notify( s( R.string.oom_err ), Commander.OPERATION_FAILED );
		}
		return false;
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
            if( !parents.isEmpty() ) {
                commander.Navigate( parents.pop(), null, name != null ? name.replace( "\"", "" ) : null );
                return;
            }
            commander.Navigate( Uri.parse( "home:" ), null, null );
            return;
        }
        
        Item item = items[position - 1];
        if( item.dir ) {
            parents.push( content_uri );
            parentLink = PLS;
            commander.Navigate( (Uri)item.origin, null, null );
            return;
        }
        else {
            Intent in = new Intent( Intent.ACTION_VIEW ).setDataAndType( (Uri)item.origin, item.mime );
            commander.issue( in, 0 );
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
        if( full ) {
            String dirName = Utils.mbAddSl( content_uri.getPath() );
            return position == 0 ? (new File( dirName )).getParent() : dirName + items[position - 1].name;
        }
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
    }
	
	@Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length ) 
            return false;
        try {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues cv = new ContentValues();
            // ????????????? cv.put( MediaStore.MediaColumns.DATA, newName );
            
            Item item = items[position-1];
            return 1 == cr.update( (Uri)item.origin, cv, null, null );
        }
        catch( Exception e ) {
            commander.showError( ctx.getString( R.string.sec_err, e.getMessage() ) );
        }
        return false;
    }
	
    @Override
    public Item getItem( Uri u ) {
        try {
            if( !"ms".equals( u.getScheme() ) ) return null; 
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
            is.skip( skip );
            return is;
        } catch( Exception e ) {
            Log.e( TAG, u.toString(), e );
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        return null;
    }
    
	@Override
	public boolean createFile( String fileURI ) {
		return false;
	}

	@Override
    public void createFolder( String new_name ) {
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
                String dirName = Utils.mbAddSl( ContentAdapter.this.content_uri.getPath() );
                int cnt = deleteItems( dirName, mList );
                sendResult( Utils.getOpReport( ctx, cnt, R.string.deleted ) );
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        private final int deleteItems( String base_path, Item[] l ) throws Exception {
    	    if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num;

            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( s( R.string.canceled ) );
                Item f = l[i];
                sendProgress( ctx.getString( R.string.deleting, f.name ), (int)(cnt * conv) );
                {
                     Uri c_uri = (Uri)f.origin;
                     if( c_uri != null ) {
                         cnt += cr.delete( c_uri, null, null );
                     }
                }
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
		return false;
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
