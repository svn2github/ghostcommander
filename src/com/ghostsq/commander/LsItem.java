package com.ghostsq.commander;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LsItem { // TODO: make it the base class for FTPItem class
    private static Pattern          unix = Pattern.compile( "^[\\-dlrwxs]{10}\\s+\\d+\\s+[^\\s]+\\s+[^\\s]+\\s+(\\d+)\\s+((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{1,2}\\s+(?:\\d{4}|\\d{2}:\\d{2}))\\s+(.+)" );
    private static SimpleDateFormat format_date_time  = new SimpleDateFormat( "MMM d HH:mm", Locale.ENGLISH );
    private static SimpleDateFormat format_date_year  = new SimpleDateFormat( "MMM d yyyy",  Locale.ENGLISH );
    
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
