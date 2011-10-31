package com.ghostsq.commander.favorites;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class Favorites extends ArrayList<Favorite> 
{
    private static final long serialVersionUID = 1L;
    private final static String old_sep = ",", sep = ";";
    private final String TAG = getClass().getName();
    private final Context c;

    public Favorites( Context c_ ) {
        c = c_;
    }
    
    public final void addToFavorites( String uri_str ) {
        removeFromFavorites( uri_str );
        Uri u = Uri.parse( uri_str );
        if( Favorite.isPwdScreened( u ) )
            u = searchForNotScreenedURI( u );
        add( new Favorite( u ) );
    }
    public final void removeFromFavorites( String uri_s ) {
        int pos = findIgnoreAuth( uri_s );
        if( pos >= 0 )
            remove( pos );
    }

    public final int findIgnoreAuth( String uri_s ) {
        try {
            if( uri_s != null ) {
                Uri u = Uri.parse( uri_s );
                if( u != null ) {
                    u = Favorite.addTrailngSlash( Favorite.updateCredentials( u, null ) );
                    for( int i = 0; i < size(); i++ ) {
                        if( Favorite.addTrailngSlash( get( i ).getUri() ).equals( u ) )
                            return i;
                    }
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "Uri: " + Favorite.screenPwd( uri_s ), e );
        }
        return -1;
    }

    public final Uri searchForNotScreenedURI( Uri u ) {
        String host = u.getHost();
        String schm = u.getScheme();
        ArrayList<Integer> matches = new ArrayList<Integer>();
        for( int i = 0; i < size(); i++ ) {
            try {
                Favorite f = get( i );
                if( f.getUserName() != null ) {
                    Uri fu = f.getUri();
                    if( host.equalsIgnoreCase( fu.getHost() ) &&
                        schm.equals( fu.getScheme() ) )
                        matches.add( i );
                }
            } catch( Exception e ) {}
        }
        if( matches.size() > 0 ) {
            if( matches.size() == 1 ) {
                Favorite f = get( matches.get( 0 ) );
                Uri u_p = f.borrowPassword( u );
                if( u_p != null ) return u_p;
            } else {
                String path = u.getPath();
                if( path == null )
                    path = "/";
                else Utils.mbAddSl( path );
                for( int j = 0; j < matches.size(); j++ ) {
                    Favorite f = get( matches.get( j ) );
                    Uri fu = f.getUri();
                    String fp = fu.getPath();
                    if( fp == null )
                        fp = "/";
                    else Utils.mbAddSl( fp );
                    if( path.equalsIgnoreCase( fp ) ) {
                        Uri u_p = f.borrowPassword( u );
                        if( u_p != null ) return u_p;
                    }
                }
                Favorite f = get( matches.get( 0 ) );
                Uri u_p = f.borrowPassword( u );
                if( u_p != null ) return u_p;
            }
        }
        Log.w( TAG, "Faild to find a suitable Favorite with password!!!" );
        return u;
    }
       
    public final String getAsString() {
        int sz = size();
        String[] a = new String[sz]; 
        for( int i = 0; i < sz; i++ ) {
            String fav_str = get( i ).toString();
            if( fav_str == null ) continue;
            a[i] = escape( fav_str );
        }
        String s = Utils.join( a, sep );
        //Log.v( TAG, "Joined favs: " + s );
        return s;
    }
    
    public final void setFromOldString( String stored ) {
        if( stored == null ) return;
        try {
            clear();
            String use_sep = old_sep;
            String[] favs = stored.split( use_sep );
            for( int i = 0; i < favs.length; i++ ) {
                if( favs[i] != null && favs[i].length() > 0 )
                    add( new Favorite( favs[i], null ) );
            }
        } catch( NoSuchElementException e ) {
            Log.e( TAG, null, e );
        }
        if( isEmpty() )
            add( new Favorite( "/sdcard", c.getString( R.string.default_uri_cmnt ) ) );
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
                add( new Favorite( stored_fav ) );
            }
        } catch( NoSuchElementException e ) {
            Log.e( TAG, null, e );
        }
        if( isEmpty() )
            add( new Favorite( "home:", c.getString( R.string.default_uri_cmnt ) ) );
    }

    private final String unescape( String s ) {
        return s.replace( "%3B", sep );
    }
    private final String escape( String s ) {
        return s.replace( sep, "%3B" );
    }
}
