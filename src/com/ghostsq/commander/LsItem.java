package com.ghostsq.commander;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class LsItem {
    private static String           TAG = "LsItem";
 // Debian FTP site      
//  -rw-r--r--    1 1176     1176         1062 Sep 04 18:54 README      
//Android FTP server
//  -rw-rw-rw- 1 system system 93578 Sep 26 00:26 Quote Pro 1.2.4.apk
//Win2K3 IIS        
//  -rwxrwxrwx   1 owner    group          314800 Feb 10  2008 classic.jar
    private static Pattern unix = Pattern.compile( "^[\\-dlrwxs]{10}\\s+\\d+\\s+[^\\s]+\\s+[^\\s]+\\s+(\\d+)\\s+((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{1,2}\\s+(?:\\d{4}|\\d{2}:\\d{2}))\\s+(.+)" );
// inetutils-ftpd:
//  drwx------  3 user     80 2009-02-15 12:33 .adobe
    private static Pattern inet = Pattern.compile( "^[\\-cdlrwxs]{10}\\s+.+\\s+(\\d*)\\s+(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2})\\s+(.+)" );
    // MSDOS style
//  02-10-08  02:08PM               314800 classic.jar
    private static Pattern msdos = Pattern.compile( "^(\\d{2,4}-\\d{2}-\\d{2,4}\\s+\\d{1,2}:\\d{2}[AP]M)\\s+(\\d+|<DIR>)\\s+(.+)" );
    private static SimpleDateFormat format_date_time  = new SimpleDateFormat( "MMM d HH:mm", Locale.ENGLISH );
    private static SimpleDateFormat format_date_year  = new SimpleDateFormat( "MMM d yyyy",  Locale.ENGLISH );
    private static SimpleDateFormat format_full_date  = new SimpleDateFormat( "yyyy-MM-dd HH:mm", Locale.ENGLISH );
    private static SimpleDateFormat format_msdos_date = new SimpleDateFormat( "MM-dd-yy  HH:mmaa", Locale.ENGLISH );
    
    private String  name;
    private boolean directory = false;
    private long    size = 0;
    private Date    date;
    public LsItem( String ls_string ) {
        Matcher m = unix.matcher( ls_string );
        if( m.matches() ) {
            try {
                if( ls_string.charAt( 0 ) == 'd' )
                    directory = true;
                name = m.group( 3 );
                size = Long.parseLong( m.group( 1 ) );
                String date_s = m.group( 2 ); 
                boolean cur_year = date_s.indexOf( ':' ) > 0;
                SimpleDateFormat df = cur_year ? format_date_time : format_date_year;
                date = df.parse( date_s );
                if( cur_year )
                    date.setYear( Calendar.getInstance().get( Calendar.YEAR ) - 1900 );
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
        }
        m = inet.matcher( ls_string );
        if( m.matches() ) {
            try {
                if( ls_string.charAt( 0 ) == 'd' )
                    directory = true;
                name = m.group( 3 );
                if( ls_string.charAt( 0 ) == 'l' ) {    // link
                    int arr_pos = name.indexOf( " ->" );
                    if( arr_pos > 0 )
                        name = name.substring( 0, arr_pos );
                }
                String sz_str = m.group( 1 );
                size = sz_str != null && sz_str.length() > 0 ? Long.parseLong( sz_str ) : -1;
                String date_s = m.group( 2 ); 
                SimpleDateFormat df = format_full_date;
                date = df.parse( date_s );
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
        }
        m = msdos.matcher( ls_string );
        if( m.matches() ) {
            try {
                name = m.group( 3 );
                if( m.group( 2 ).equals( "<DIR>" ) )
                    directory = true;
                else
                    size = Long.parseLong( m.group( 2 ) );
                
                String date_s = m.group( 1 ); 
                SimpleDateFormat df = format_msdos_date;
                date = df.parse( date_s );
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
        }
        Log.e( TAG, "\nUnmatched string: " + ls_string + "\n" );
    }

    public String getName() {
        return name;
    }
    public Date getDate() {
        return date;
    }
    public long length() {
        return size;
    }
    public boolean isValid() {
        return name != null;
    }
    public boolean isDirectory() {
        return directory;
    }
    public int compareTo( LsItem o ) {
        return getName().compareTo( o.getName() );
    }
}
