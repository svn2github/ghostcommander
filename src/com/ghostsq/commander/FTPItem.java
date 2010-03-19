package com.ghostsq.commander;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FTPItem {
// Debian FTP site    	
//	-rw-r--r--    1 1176     1176         1062 Sep 04 18:54 README    	
//Android FTP server
//	-rw-rw-rw- 1 system system 93578 Sep 26 00:26 Quote Pro 1.2.4.apk
//Win2K3 IIS    	
//	-rwxrwxrwx   1 owner    group          314800 Feb 10  2008 classic.jar
	private static Pattern unix = Pattern.compile( "^[\\-dlrwxs]{10}\\s+\\d+\\s+[^\\s]+\\s+[^\\s]+\\s+(\\d+)\\s+((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{2}\\s+(?:\\d{4}|\\d{2}:\\d{2}))\\s+(.+)" );
// inetutils-ftpd:
//	drwx------  3 zc2     80 2009-02-15 12:33 .adobe
	private static Pattern inet = Pattern.compile( "^[\\-dlrwxs]{10}\\s+.+\\s+(\\d+)\\s+(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2})\\s+(.+)" );
	// MSDOS style
//	02-10-08  02:08PM               314800 classic.jar
	private static Pattern msdos = Pattern.compile( "^(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}[AP]M)\\s+(\\d+)\\s+(.+)" );

	private static SimpleDateFormat format_date_time = new SimpleDateFormat( "MMM d HH:mm", Locale.ENGLISH );
	private static SimpleDateFormat format_date_year = new SimpleDateFormat( "MMM d yyyy",  Locale.ENGLISH );
	private static SimpleDateFormat format_full_date = new SimpleDateFormat( "yyyy-MM-dd HH:mm", Locale.ENGLISH );
	
    private String  name;
    private boolean directory = false;
    private long    size;
    private Date    date;
    public FTPItem( String ftp_string ) {
    	Matcher m = unix.matcher( ftp_string );
    	if( m.matches() ) {
	        if( ftp_string.charAt( 0 ) == 'd' )
	            directory = true;
	        name = m.group( 3 );
	        size = Long.parseLong( m.group( 1 ) );
	        String date_s = m.group( 2 ); 
        	boolean cur_year = date_s.indexOf( ':' ) > 0;
        	SimpleDateFormat df = cur_year ? format_date_time : format_date_year;
            try {
            	date = df.parse( date_s );
            	if( cur_year )
            		date.setYear( Calendar.getInstance().get( Calendar.YEAR ) - 1900 );
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
    	}
    	m = inet.matcher( ftp_string );
    	if( m.matches() ) {
	        if( ftp_string.charAt( 0 ) == 'd' )
	            directory = true;
	        name = m.group( 3 );
	        size = Long.parseLong( m.group( 1 ) );
	        String date_s = m.group( 2 ); 
        	SimpleDateFormat df = format_full_date;
            try {
            	date = df.parse( date_s );
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
    	}
    }

 /*   	
    	boolean unixStyle = ftp_string.matches(  );
    	if( !unixStyle ) {
    		name = ftp_string;
    		return;
    	}
    	
        StringTokenizer st = new StringTokenizer( ftp_string, " \t" );
        String rights = null, date_str = "", toy = null;
        name = "";
        try {
            for( int i = 0; i < 40; i++ ) {	// in the case of very long names with spaces
                String s = st.nextToken();
                switch( i ) {
                case 0: rights = s; break;
                case 1:
                case 2:
                case 3: break;
                case 4: size = Long.parseLong( s ); break;
                case 5: date_str  = s; break;
                case 6: date_str += " " + s; break;
                case 7: toy = s; break;
                default:
                	if( name.length() > 0 )
                		name += " ";
                	name += s;
                }
                if( !st.hasMoreTokens() ) break;
            }
		} catch( NumberFormatException e ) {
			e.printStackTrace();
		}
        if( name == null ) {
        	name = ftp_string;
        }
        else {
	        if( rights != null && rights.charAt( 0 ) == 'd' )
	            directory = true;
	        if( toy != null ) {
	        	boolean cur_year = toy.indexOf( ':' ) > 0;
	        	String format_s = cur_year ? "MMM d HH:mm" : "MMM d yyyy";
	            SimpleDateFormat df = new SimpleDateFormat( format_s, Locale.ENGLISH );
	            try {
	            	date = df.parse( date_str + " " + toy );
	            	if( cur_year )
	            		date.setYear( Calendar.getInstance().get( Calendar.YEAR ) - 1900 );
	            } catch( ParseException e ) {
	                e.printStackTrace();
	            }
	        }
        }
*/
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
    public int compareTo( FTPItem o ) {
    	return getName().compareTo( o.getName() );
    }
}
