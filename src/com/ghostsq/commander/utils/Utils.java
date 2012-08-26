package com.ghostsq.commander.utils;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.io.OutputStream;

import com.ghostsq.commander.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

public final class Utils {
    private final static String[][] mimes = { // should be sorted!
            { ".3gpp","audio/3gpp" }, 
            { ".aif", "audio/x-aiff" }, 
            { ".apk", "application/vnd.android.package-archive" },
            { ".avi", "video/x-msvideo" }, 
            { ".bmp", "image/bmp" }, 
            { ".csv", "text/csv" }, 
            { ".doc", "application/msword" },
            { ".epub","application/epub" }, 
            { ".fb2", "application/fb2" },
            { ".flac","audio/flac" },            
            { ".flv", "video/x-flv" },            
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
            { ".mkv", "video/x-matroska" },
            { ".mp2", "video/mpeg" },
            { ".mp3", "audio/mp3" },
            { ".mp4", "video/mp4" },
            { ".mpeg","video/mpeg" }, 
            { ".mpg", "video/mpeg" }, 
            { ".oga", "audio/ogg" }, 
            { ".ogg", "audio/ogg" },    // RFC 5334 
            { ".ogv", "video/ogg" },    // RFC 5334 
            { ".pdf", "application/pdf" }, 
            { ".php", "text/php" },
            { ".png", "image/png" }, 
            { ".ra",  "audio/x-pn-realaudio" }, 
            { ".ram", "audio/x-pn-realaudio" },
            { ".rar", "application/x-rar-compressed" }, 
            { ".rtf", "application/rtf" }, 
            { ".svg", "image/svg+xml" },
            { ".tgz", "application/gnutar" }, 
            { ".tif", "image/tiff" }, 
            { ".tiff","image/tiff" }, 
            { ".txt", "text/plain" },
            { ".vcf", "text/x-vcard" }, 
            { ".wav", "audio/wav" }, 
            { ".wma", "audio/x-ms-wma" }, 
            { ".wmv", "video/x-ms-wmv" }, 
            { ".xml", "text/xml" }, 
            { ".zip", "application/zip" } };

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
            for( cp = 1;; cp++ ) {
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
        if( file_name == null )
            return "";
        int dot = file_name.lastIndexOf( "." );
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
        String items = null;
        if( total > 1 ) {
            if( total < 5 )
                items = ctx.getString( R.string.items24 );
            if( items == null || items.length() == 0 )
                items = ctx.getString( R.string.items );
            if( items == null || items.length() == 0 )
                items = ctx.getString( R.string.item );
        }
        String verb = ctx.getString( verb_id );
        String report = ( total > 0 ? "" + total + " " + ( total > 1 ? items : ctx.getString( R.string.item ) ) : ctx
                .getString( R.string.nothing ) )
                + " "
                + ( total > 1 ? ctx.getString( R.string.were ) : ctx.getString( R.string.was ) )
                + " "
                + verb
                + ( total > 1 ? ctx.getString( R.string.verb_plural_sfx ) : "" ) + ".";
        return report;
    }

    public final static String getHumanSize( long sz ) {
        if( sz > 1073741824 )
            return "" + Math.round( sz * 10 / 1073741824. ) / 10. + "G";
        if( sz > 1048576 )
            return "" + Math.round( sz * 10 / 1048576. ) / 10. + "M";
        if( sz > 1024 )
            return "" + Math.round( sz * 10 / 1024. ) / 10. + "K";
        return "" + sz + " ";
    }

    public final static long parseHumanSize( String s ) {
        if( s == null )
            return 0;
        final char[] sxs = { 'K', 'M', 'G', 'T' };
        long m = 1024;
        int s_pos;
        s = s.toUpperCase();
        try {
            for( int i = 0; i < 4; i++ ) {
                s_pos = s.indexOf( sxs[i] );
                if( s_pos > 0 ) {
                    float v = Float.parseFloat( s.substring( 0, s_pos ) );
                    return (long)( v * m );
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

    public final static String mbAddSl( String path ) {
        if( !str( path ) )
            return "";
        return path.charAt( path.length() - 1 ) == '/' ? path : path + "/";
    }

    public final static String encodeToAuthority( String serv ) {
        String auth = null;
        int cp = serv.lastIndexOf( ':' );
        if( cp > 0 ) {
            String ps = serv.substring( cp + 1 );
            try {
                int port = Integer.parseInt( ps );
                if( port > 0 )
                    auth = Uri.encode( serv.substring( 0, cp ) ) + ":" + port;
            } catch( NumberFormatException e ) {
            }
        }
        if( auth == null )
            auth = Uri.encode( serv );
        return auth;
    }

    public static Uri getUriWithAuth( Uri u, Credentials crd ) {
        return crd != null ? getUriWithAuth( u, crd.getUserName(), crd.getPassword() ) : u;
    }

    public static Uri getUriWithAuth( Uri u, String un, String pw ) {
        if( un == null ) return u;
        String ui = Utils.escapeName( un );
        if( pw != null )
            ui += ":" + Utils.escapeName( pw );
        return updateUserInfo( u, ui );
    }
    
    public final static Uri updateUserInfo( Uri u, String encoded_ui ) {
        if( u == null ) return null;
        String ea = u.getEncodedAuthority();
        if( ea == null ) return u;
        int at_pos = ea.lastIndexOf( '@' );
        if( encoded_ui == null ) {
            if( at_pos < 0 ) return u;
            ea = ea.substring( at_pos + 1 );
        } else
            ea = encoded_ui + ( at_pos < 0 ? "@" + ea : ea.substring( at_pos ) );
        return u.buildUpon().encodedAuthority( ea ).build();
    }

    public final static Uri addTrailngSlash( Uri u ) {
        String alt_path, path = u.getEncodedPath();
        if( path == null )
            alt_path = "/";
        else {
            alt_path = Utils.mbAddSl( path );
            if( alt_path == null || path.equals( alt_path ) ) return u;
        }
        return u.buildUpon().encodedPath( alt_path ).build(); 
    }
    
    public final static boolean str( String s ) {
        return s != null && s.length() > 0;
    }
    
    public final static String join( String[] a, String sep ) {
        if( a == null )
            return "";
        StringBuffer buf = new StringBuffer( 256 );
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

    public final static void changeLanguage( Context c ) {
        changeLanguage( c, c.getResources() );
    }

    public final static void changeLanguage( Context c, Resources r ) {
        try {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
            String lang = sharedPref.getString( "language", "" );

            Locale locale;
            if( lang == null || lang.length() == 0 ) {
                locale = Locale.getDefault();
            } else {
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

    public final static CharSequence readStreamToBuffer( InputStream is, String encoding ) {
        if( is != null ) {
            try {
                int bytes = is.available();
                if( bytes < 1024 || bytes > 1048576 )
                    bytes = 10240;
                char[] chars = new char[bytes];
                InputStreamReader isr = null;
                if( str( encoding ) ) {
                    try {
                        isr = new InputStreamReader( is, encoding );
                    } catch( UnsupportedEncodingException e ) {
                        Log.w( "GC", encoding, e );
                        isr = new InputStreamReader( is );
                    }
                } else
                    isr = new InputStreamReader( is );
                StringBuffer sb = new StringBuffer( bytes );
                int n = -1;
                boolean available_supported = is.available() > 0;
                while( true ) {
                    n = isr.read( chars, 0, bytes );
                    // Log.v( "readStreamToBuffer", "Have read " + n + " chars"
                    // );
                    if( n < 0 )
                        break;
                    for( int i = 0; i < n; i++ ) {
                        if( chars[i] == 0x0D )
                            chars[i] = ' ';
                    }
                    sb.append( chars, 0, n );
                    if( available_supported ) {
                        for( int i = 0; i < 10; i++ ) {
                            if( is.available() > 0 )
                                break;
                            // Log.v( "readStreamToBuffer", "Waiting the rest "
                            // + i );
                            Thread.sleep( 20 );
                        }
                        if( is.available() == 0 ) {
                            // Log.v( "readStreamToBuffer", "No more data!" );
                            break;
                        }
                    }
                }
                return sb;
            } catch( Throwable e ) {
                Log.e( "Utils.readStreamToBuffer()", "Error on reading a stream", e );
            }
        }
        return null;
    }

    public final static boolean copyBytes( InputStream is, OutputStream os ) {
        try {
            byte[] buf = new byte[65536];
            int n;
            while( ( n = is.read( buf ) ) != -1 ) {
                os.write( buf, 0, n );
                Thread.sleep( 1 );
            }
            return true;
        } catch( Exception e ) {
            Log.e( "Utils.copyBytes", "Exception: " + e );
        }
        return false;
    }

    public final static int ENC_DESC_MODE_NUMB  = 0;
    public final static int ENC_DESC_MODE_BRIEF = 1;
    public final static int ENC_DESC_MODE_FULL  = 2;

    public final static String getEncodingDescr( Context ctx, String enc_name, int mode ) {
        if( enc_name == null )
            enc_name = "";
        Resources res = ctx.getResources();
        if( res == null )
            return null;
        String[] enc_dsc_arr = res.getStringArray( R.array.encoding );
        String[] enc_nms_arr = res.getStringArray( R.array.encoding_vals );
        try {
            for( int i = 0; i < enc_nms_arr.length; i++ ) {
                if( enc_name.equals( enc_nms_arr[i] ) ) {
                    if( mode == ENC_DESC_MODE_NUMB )
                        return "" + i;
                    String enc_desc = enc_dsc_arr[i];
                    if( mode == ENC_DESC_MODE_FULL )
                        return enc_desc;
                    else {
                        int nlp = enc_desc.indexOf( '\n' );
                        if( nlp < 0 )
                            return enc_desc;
                        return enc_desc.substring( 0, nlp );
                    }

                }
            }
        } catch( Exception e ) {
        }
        return null;
    }

    public final static String escapeRest( String s ) {
        if( !str( s ) )
            return s;
        return s.replaceAll( "%", "%25" )
                .replaceAll( "#", "%23" )
                .replaceAll( ":", "%3A" );
    }

    public final static String escapePath( String s ) {
        if( !str( s ) )
            return s;
        return escapeRest( s ).replaceAll( "@", "%40" );
    }

    public final static String escapeName( String s ) {
        if( !str( s ) )
            return s;
        return escapePath( s ).replaceAll( "/", "%2F" );
    }

    public static byte[] hexStringToBytes( String hexString ) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for( int i = 0; i < len; i++ )
            result[i] = Integer.valueOf( hexString.substring( 2 * i, 2 * i + 2 ), 16 ).byteValue();
        return result;
    }

    private final static String HEX = "0123456789abcdef";

    public static String toHexString( byte[] buf, String delim ) {
        if( buf == null )
            return "";
        StringBuffer result = new StringBuffer( 2 * buf.length );
        for( int i = 0; i < buf.length; i++ ) {
            if( i > 0 && str( delim ) )
                result.append( delim );
            result.append( HEX.charAt( ( buf[i] >> 4 ) & 0x0f ) ).append( HEX.charAt( buf[i] & 0x0f ) );
        }
        return result.toString();
    }

    public final static float getBrightness( int color ) {
        float[] hsv = new float[3];
        Color.colorToHSV( color, hsv );
        return hsv[2];
    }

    public final static int setBrightness( int color, float drop ) {
        float[] hsv = new float[3];
        Color.colorToHSV( color, hsv );
        hsv[2] *= drop;
        return Color.HSVToColor( hsv );
    }

    public final static GradientDrawable getShading( int color ) {
        return getShadingEx( color, 0.6f );
    }

    public final static GradientDrawable getShadingEx( int color, float drop ) {
        try {
            int[] cc = new int[2];
            cc[0] = color;
            cc[1] = setBrightness( color, drop );
            return new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, cc );
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }

    public enum RR {
        busy(R.string.busy), 
        copy_err(R.string.copy_err), 
        copied(R.string.copied), 
        moved(R.string.moved), 
        interrupted(R.string.interrupted), 
        uploading(R.string.uploading), 
        fail_del(R.string.fail_del), 
        cant_del(R.string.cant_del), 
        retrieving(R.string.retrieving), 
        deleting(R.string.deleting), 
        not_supported(R.string.not_supported), 
        file_exist(R.string.file_exist), 
        cant_md(R.string.cant_md),

        sz_folder( R.string.sz_folder),
        sz_dirnum( R.string.sz_dirnum),
        sz_dirsfx_p( R.string.sz_dirsfx_p),
        sz_dirsfx_s( R.string.sz_dirsfx_s),
        sz_file( R.string.sz_file),
        sz_files( R.string.sz_files),
        sz_Nbytes( R.string.sz_Nbytes),
        sz_bytes( R.string.sz_bytes),
        sz_lastmod( R.string.sz_lastmod),
        sz_total( R.string.sz_total),
        too_deep_hierarchy(R.string.too_deep_hierarchy),
        
        failed(R.string.failed),
        ftp_connected(R.string.ftp_connected)
        ;
        private int r;

        private RR(int r_) {
            r = r_;
        }

        public int r() {
            return r;
        }
    };
}