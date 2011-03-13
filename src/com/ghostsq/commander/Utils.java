package com.ghostsq.commander;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.webkit.MimeTypeMap;

public final class Utils {
	private final static String[][] mimes = {	// should be sorted!
        { ".3gpp","audio/3gpp" },
        { ".aif", "audio/x-aiff" },
		{ ".apk", "application/vnd.android.package-archive" },
		{ ".avi", "video/x-msvideo" },
		{ ".bmp", "image/bmp" },
		{ ".csv", "text/csv" },
		{ ".gif", "image/gif" },
		{ ".gz",  "application/gzip" },
		{ ".htm", "text/html" },
		{ ".html","text/html" },
		{ ".jar", "application/java-archive" },
		{ ".java","application/octet-stream" },
		{ ".jpeg","image/jpeg" },
		{ ".jpg", "image/jpeg" },
        { ".m3u", "audio/x-mpegurl" },
        { ".mid", "audio/midi" },
		{ ".midi","audio/midi" },
		{ ".mp3", "audio/mp3" },
		{ ".mpeg","video/mpeg" },
		{ ".ogg", "audio/x-ogg" },
		{ ".php", "text/php " },
		{ ".png", "image/png" },
		{ ".rar", "application/x-rar-compressed" },
		{ ".txt", "text/plain" },
		{ ".wav", "audio/wav" },
		{ ".xml", "text/xml" },
		{ ".zip", "application/zip" }
	};

	public final static String getMimeByExt( String ext ) {
		if( ext == null || ext.length() == 0 || ext.compareTo( "." ) == 0 ) 
		    return "*/*";
		ext = ext.toLowerCase();
		int from = 0, to = mimes.length;
		for( int l = 0; l < mimes.length; l++ ) {
			int idx = ( to - from ) / 2 + from;
			String tmp = mimes[idx][0];
			if( tmp.compareTo( ext ) == 0 ) 
			    return mimes[idx][1];
			int cp;
			for( cp = 1; ; cp++ ) {
				if( cp >= ext.length() ) {
					to = idx;
					break;
				}
				if( cp >= tmp.length() ) {
					from = idx;
					break;
				}
				char c0 = ext.charAt( cp );
				char ct = tmp.charAt( cp );
				if( c0 < ct ) {
					to = idx;
					break;
				}
				if( c0 > ct ) {
					from = idx;
					break;
				}
			}
		}
        MimeTypeMap mime_map = MimeTypeMap.getSingleton();
        if( mime_map != null ) {
            String mime = mime_map.getMimeTypeFromExtension( ext.substring( 1 ) );
            if( mime != null && mime.length() > 0 )
                return mime;
        }
		return "*/*";
	}
	public final static String getFileExt( String file_name ) {
	    if( file_name == null ) return "";
		int dot = file_name.lastIndexOf(".");
		return dot >= 0 ? file_name.substring( dot ) : "";
	}
	
	public final static String[] prepareWildcard( String wc ) {
	    return ( "\02" + wc.toLowerCase() + "\03" ).split( "\\*" );
	}
	public final static boolean match( String text, String[] cards ) {
        int pos = 0;
        String lc_text = "\02" + text.toLowerCase() + "\03";
        for( String card : cards ) {
            int idx = lc_text.indexOf( card, pos );
            if( idx < 0 )
                return false;
            pos = idx + card.length();
        }
        return true;
    }
    public final static int deleteDirContent( File d ) {
        int cnt = 0;
        File[] fl = d.listFiles();
        if( fl != null ) {
            for( File f : fl ) {
                if( f.isDirectory() )
                    cnt += deleteDirContent( f );
                if( f.delete() )
                    cnt++;
            }
        }
        return cnt;
    }
	public final static File[] getListOfFiles( String[] uris ) {
	    File[] list = new File[uris.length];
	    for( int i = 0; i < uris.length; i++ ) {
	    	if( uris[i] == null )
	    		return null;
	        list[i] = new File( uris[i] );
	    }
	    return list;
	}
	// TODO: localize
    public final static String getOpReport( Context ctx, int total, int verb_id ) {
        String verb = ctx.getString( verb_id );
        String report = ( total > 0 ? "" + total + " " + 
                        ( total > 1 ? ctx.getString( R.string.items ) : ctx.getString( R.string.item ) ) 
                                    : ctx.getString( R.string.nothing ) ) + " " +
                        ( total > 1 ? ctx.getString( R.string.were  ) : ctx.getString( R.string.was ) ) 
                        + " " + verb + 
                        ( total > 1 ? ctx.getString( R.string.verb_plural_sfx ) : "" ) + ".";
        return report;
    }
    public final static String getHumanSize( long sz ) {
        if( sz > 1073741824 )
            return "" + Math.round(sz*10 / 1073741824.)/10. + "G";
        if( sz > 1048576 )
            return "" + Math.round(sz*10 / 1048576.)/10. + "M";
        if( sz > 1024 )
            return "" + Math.round(sz*10 / 1024.)/10. + "K";
        return "" + sz + " ";
    }
    public final static long parseHumanSize( String s ) {
        final char[] sxs = { 'K', 'M', 'G', 'T' };
        long m = 1024;
        int  s_pos;
        s = s.toUpperCase();
        try {
            for( int i=0; i < 4; i++ ) {
                s_pos = s.indexOf( sxs[i] );
                if( s_pos > 0 ) {
                    float v = Float.parseFloat( s.substring( 0, s_pos ) );
                    return (long)(v * m);
                }
                m *= 1024;
            }
            s_pos = s.indexOf( 'B' );
            return Long.parseLong( s_pos > 0 ? s.substring( 0, s_pos ) : s );
        } catch( NumberFormatException e ) {
            e.printStackTrace();
        }
        return 0; 
    }

    public final static String screenPwd( String uri_str ) {
        if( uri_str == null ) return null;
        return screenPwd( Uri.parse( uri_str ) );
    }
    public final static String screenPwd( Uri u ) {
        if( u == null ) return null;
        String ui = u.getUserInfo();
        if( ui == null || ui.length() == 0 ) return u.toString();
        int pw_pos = ui.indexOf( ':' );
        if( pw_pos < 0 ) return u.toString();
        ui = ui.substring( 0, pw_pos+1 ) + "***";
        String host = u.getHost();
        int port = u.getPort();
        String authority = ui + "@" + host + (port >= 0 ? port : ""); 
	    return u.buildUpon().encodedAuthority( authority ).build().toString();
	}
    public final static String mbAddSl( String path ) {
        if( path == null || path.length() == 0 ) return "";
        return path.charAt( path.length()-1 ) == '/' ? path : path + "/"; 
    }
    public final static String join( String[] a, String sep ) {
        if( a == null ) return "";
        StringBuffer buf = new StringBuffer();
        boolean first = true;
        for( int i = 0; i < a.length; i++ ) {
          if( first )
            first = false;
          else if( sep != null )
            buf.append( sep );
          buf.append( a[i] );
        }
        return buf.toString();
    }
    public final static void changeLanguage( Context c, Resources r ) {
        try {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
            String lang = sharedPref.getString( "language", "" );
            
            Locale locale;
            if( lang == null || lang.length() == 0 ) {
                locale = Locale.getDefault();
            }
            else {
                String country = lang.length() > 3 ? lang.substring( 3 ) : null;
                if( country != null )
                    locale = new Locale( lang.substring( 0, 2 ), country );
                else
                    locale = new Locale( lang );
            }
            Locale.setDefault( locale );
            Configuration config = new Configuration();
            config.locale = locale;
            r.updateConfiguration( config, null );
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
}