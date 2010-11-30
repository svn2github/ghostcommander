package com.ghostsq.commander;

import java.io.File;
import android.net.Uri;
import android.webkit.MimeTypeMap;

public class Utils {
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
		int from = 0, to = mimes.length;
		for( int l = 0; l < mimes.length; l++ ) {
			int idx = ( to - from ) / 2 + from;
			String tmp = mimes[idx][0];
			if( tmp.compareToIgnoreCase( ext ) == 0 ) 
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
		int dot = file_name.lastIndexOf(".");
		return dot >= 0 ? file_name.substring( dot ) : "";
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
    public final static String getOpReport( int total, String verb ) {
        String report = ( total > 0 ? "" + total + " file" +
                        ( total > 1 ? "s" : "" ) : "Nothing" ) +
                        ( total > 1 ? " were" : " was" ) +" " + verb + ".";
        return report;
    }
    public final static String getCopyReport( int total ) {
        return getOpReport( total, "copied" );
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
}
