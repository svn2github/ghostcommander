package com.ghostsq.commander.favorites;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.HomeAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public class Favorites extends ArrayList<Favorite> 
{
    private static final long serialVersionUID = 1L;
    private final static String old_sep = ",", sep = ";";
    private final String TAG = getClass().getSimpleName();
    private final Context c;
    private ArrayList<String> idsToRemove = new ArrayList<String>(); 

    public Favorites( Context c_ ) {
        c = c_;
    }
    
    public final void addToFavorites( Uri u, Credentials crd, String comment ) {
        removeFromFavorites( u );
        if( crd == null && Favorite.isPwdScreened( u ) )
            crd = searchForPassword( u );
        Favorite f = new Favorite( u, crd );
        add( f );
        if( comment != null )
            f.setComment( comment ); 
    }
    public final void removeFromFavorites( Uri u ) {
        int pos = findIgnoreAuth( u );
        if( pos >= 0 )
            remove( pos );
        else
            Log.w( TAG, "Can't find in the list of favs:" + u );
    }

    @Override
    public Favorite remove( int i ) {
        Favorite f = get( i );
        idsToRemove.add( f.getID() );
        return super.remove( i );
    }
    
    @Override
    public boolean remove( Object o ) {
        if( !(o instanceof Favorite) ) return false; 
        Favorite f = (Favorite)o;
        idsToRemove.add( f.getID() );
        return super.remove( f );
    }
    
    public final int findIgnoreAuth( Uri u ) {
        try {
            if( u != null ) {
                u = Utils.addTrailngSlash( Utils.updateUserInfo( u, null ) );
                //Log.v( TAG, "looking for URI:" + u );
                for( int i = 0; i < size(); i++ ) {
                    Favorite f = get( i );
                    if( f == null ) {
                        Log.e( TAG, "A fave is null!" );
                        continue;
                    }
                    Uri fu = f.getUri(); 
                    if( fu == null ) {
                        Log.e( TAG, "A fave URI is null!" );
                        continue;
                    }
                    fu = Utils.addTrailngSlash( fu );
                    //Log.v( TAG, "probing URI:" + fu );
                    if( fu.equals( u ) )
                        return i;
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "Uri: " + Favorite.screenPwd( u ), e );
        }
        return -1;
    }
   
    public final Credentials searchForPassword( Uri u ) {
        try {
            String ui = u.getUserInfo(); 
            if( ui != null && ui.length() > 0 ) {
                String user = ui.substring( 0, ui.indexOf( ':' ) );
                String host = u.getHost();
                String schm = u.getScheme();
                String path = u.getPath();
                if( path == null || path.length() == 0 ) path = "/"; else Utils.mbAddSl( path );
                int best = -1;
                for( int i = 0; i < size(); i++ ) {
                    try {
                        Favorite f = get( i );
                        if( user.equalsIgnoreCase( f.getUserName() ) ) {
                            Uri fu = f.getUri();
                            if( schm.equals( fu.getScheme() ) ) {
                                if( host.equalsIgnoreCase( fu.getHost() ) ) {
                                    best = i;
                                    String fp = fu.getPath();
                                    if( fp == null || path.length() == 0 ) fp = "/"; else Utils.mbAddSl( fp );
                                    if( path.equalsIgnoreCase( fp ) )
                                        break;
                                }
                            }
                        }
                    } catch( Exception e ) {}
                }
                if( best >= 0 ) {
                    Favorite f = get( best );
                    return f.borrowPassword( u );
                }
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        Log.w( TAG, "Faild to find a suitable Favorite with password!!!" );
        return null;
    }

    public final void setDefaults() {
        try {
            add( new Favorite( HomeAdapter.DEFAULT_LOC, c.getString( R.string.home ) ) );
            add( new Favorite( Panels.DEFAULT_LOC, c.getString( R.string.default_uri_cmnt ) ) );
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ) {
                add( new Favorite( Utils.getPath( Utils.PubPathType.DOWNLOADS ),c.getString( R.string.df_dnl ) ) );
                add( new Favorite( Utils.getPath( Utils.PubPathType.DCIM ),     c.getString( R.string.df_cam ) ) );
                add( new Favorite( Utils.getPath( Utils.PubPathType.PICTURES ), c.getString( R.string.df_pic ) ) );
                add( new Favorite( Utils.getPath( Utils.PubPathType.MUSIC ),    c.getString( R.string.df_mus ) ) );
                add( new Favorite( Utils.getPath( Utils.PubPathType.MOVIES ),   c.getString( R.string.df_mov ) ) );
            }
        } catch( Throwable e ) {
            Log.e( TAG, null, e );
        }
    }
       
    public final void setFromString( String stored ) {
        if( stored == null ) return;
        clear();
        String use_sep = sep;
        String[] favs = stored.split( use_sep );
        try {
            for( int i = 0; i < favs.length; i++ ) {
                String stored_fav = unescape( favs[i] );
                //Log.v( TAG, "fav: " + stored_fav );
                add( Favorite.fromString( stored_fav, c ) );
            }
        } catch( NoSuchElementException e ) {
            Log.e( TAG, null, e );
        }
        if( isEmpty() )
            add( new Favorite( HomeAdapter.DEFAULT_LOC, c.getString( R.string.home ) ) );
    }

    private final String unescape( String s ) {
        return s.replace( "%3B", sep );
    }
    private final String escape( String s ) {
        return s.replace( sep, "%3B" );
    }

    public final void store() {
        SharedPreferences sp = c.getSharedPreferences( "Favorites", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS );
        SharedPreferences.Editor ed = sp.edit();
        HashSet<String> ids = new HashSet<String>(); 
        for( int i = 0; i < size(); i++ ) {
            get( i ).store( c, ed );
            ids.add( get( i ).getID() );
        }
        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
             ForwardCompat.putStringSet( ed, "IDS", ids );
         else {
            StringBuffer buf = new StringBuffer( 256 );
            for( String id : ids ) {
                if( buf.length() > 0 ) buf.append( "," );
                buf.append( id );
            }
            ed.putString( "IDScsv", buf.toString() );
        }
        for( String id : this.idsToRemove )
            Favorite.erasePrefs( id, ed );
        this.idsToRemove.clear();
        ed.commit();
    }

    public final void restore() {
        try {
            clear();
            SharedPreferences sp = c.getSharedPreferences( "Favorites", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS );
            if( sp == null ) return;
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
                Set<String> ids = ForwardCompat.getStringSet( sp, "IDS" );
                if( ids == null ) return;
                for( String id : ids )
                    restoreFav( id, sp );
            } else {
                String idcs = sp.getString( "IDScsv", null );
                if( idcs == null ) return;
                String[] ida = idcs.split( "," );
                for( String id : ida )
                    restoreFav( id, sp );
            }
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
    }

    private final void restoreFav( String id, SharedPreferences sp ) {
        Favorite fav = Favorite.restore( c, id, sp );
        if( fav != null )
            add( fav );
    }
    
    public static void clearPrefs( Context c ) {
        SharedPreferences sp = c.getSharedPreferences( "Favorites", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS );        
        SharedPreferences.Editor ed = sp.edit();    
        ed.clear();
        ed.commit();
    }
}
