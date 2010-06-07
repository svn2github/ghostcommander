package com.ghostsq.commander;

import java.io.File;

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
	public final static String getCopyReport( int total ) {
		String report = ( total > 0 ? "" + total + " file" +
			            ( total > 1 ? "s" : "" ) : "Nothing" ) +
			            ( total > 1 ? " were" : " was" ) +" copied.";
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

	public class Credentials {
        public String userName = null, userPass = null, userInfo = null;
        public final void set( String name, String pass ) {
            userName = name;
            userPass = pass;
        }
        public final void set( String user_info ) {
            userInfo = user_info;
            if( user_info == null || user_info.length() == 0 ) {
                userName = "anonymous";
                userPass = "user@host.com";
            }
            else {
                int col_pos = user_info.indexOf( ':' );
                if( col_pos >= 0 ) {
                    userPass = user_info.substring( col_pos + 1 );
                    userName = user_info.substring( 0, col_pos );
                }
                else {
                    userName = user_info;
                    userPass = null;
                }
            }
        }
        public final boolean isNotSet() {
            return userName == null || userPass == null;
        }
        public final boolean isSame( String user_info ) {
            if( user_info == null && userInfo == null ) return true;
            if( user_info != null && userInfo != null && user_info.compareTo( userInfo ) == 0 ) return true;
            return false;
        }
    }
}
