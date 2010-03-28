package com.ghostsq.commander;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.Toast;

public abstract class CommanderAdapterBase extends BaseAdapter implements CommanderAdapter {
	protected final static String NOTIFY_STR = "str", NOTIFY_PRG1 = "prg1", NOTIFY_PRG2 = "prg2"; 
	
    protected Commander commander = null;
    protected static final String SLS = File.separator;
    protected static final String DEFAULT_DIR = "/sdcard";
    protected LayoutInflater mInflater = null;
    protected int    parentWidth, nameWidth, sizeWidth, dateWidth;
    protected int    mode = 0;
    protected String parentLink;
    protected Engine worker = null;
    /*
    protected Handler handler = new Handler() {
        public void handleMessage( Message msg ) {
            int p1 = msg.getData().getInt( NOTIFY_PRG1 );
            int p2 = msg.getData().getInt( NOTIFY_PRG2, -1 );
            String str = msg.getData().getString( NOTIFY_STR );
            commander.notifyMe( str, p1, p2 );
        }
    };
    */
    Handler handler = new Handler() {
        public void handleMessage( Message msg ) {
            int perc1 = msg.getData().getInt( CommanderAdapterBase.NOTIFY_PRG1 );
            int perc2 = msg.getData().getInt( CommanderAdapterBase.NOTIFY_PRG2, -1 );
            String str = msg.getData().getString( CommanderAdapterBase.NOTIFY_STR );
            if( perc1 < 0 ) {
                worker = null;
                notifyDataSetChanged();
            }
            commander.notifyMe( str, perc1, perc2 );
        }
    };
    
    protected CommanderAdapterBase() {
    }
    protected CommanderAdapterBase( Commander c, int mode_ ) {
    	Init( c );
        mode = mode_;
        parentWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        parentLink = "/";    	
    }

    @Override
	public void Init( Commander c ) {
    	commander = c;		
    	mInflater = (LayoutInflater)c.getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
	}
    
    @Override
    public void setMode( int mask, int mode_ ) {
        mode &= ~mask;
        mode |= mode_;
    }
    
    public class Item {
    	public String name = "";
    	public Date   date = null;
    	public long   size = -1;
    	public boolean dir, sel;
    }
    
    protected View getView( View convertView, ViewGroup parent, Item item ) {
        boolean wm = (mode & WIDE_MODE) == WIDE_MODE;
        boolean dm = ( mode & MODE_DETAILS ) == DETAILED_MODE;
        View row_view;
        boolean current_wide = convertView instanceof TableLayout;
        if( convertView == null || 
    		( (  wm && !current_wide ) || 
    		  ( !wm &&  current_wide ) ) ) {
            row_view = mInflater.inflate( wm ? R.layout.row : R.layout.narrow, parent, false );
        }
        else {
            row_view = convertView;
            row_view.setBackgroundColor( 0 );
        }
        int vpad = ( mode & MODE_FINGERF ) == FAT_MODE ? 8 : 0;
        row_view.setPadding( 0, vpad, 4, vpad );        
        
        String name = "?", size = "", date = "";
        name = (item.dir ? "/" : " ") + item.name;
        if( dm ) {
        	if( item.size > 0  )
        		size = Utils.getHumanSize( item.size );
            if( item.date != null ) {
	            String dateFormat;
	            if( wm )
	            	dateFormat = item.date.getYear() + 1900 == Calendar.getInstance().get( Calendar.YEAR ) ?
	                        "MMM dd hh:mm" : "MMM dd  yyyy";
	            else
	            	dateFormat = "yy-MM-dd";
	            date = (String)DateFormat.format( dateFormat, item.date );
            }
        }
        int parent_width = parent.getWidth();
        if( parentWidth != parent_width ) {
            parentWidth = parent_width;
            nameWidth = wm || dm ? parent_width * 5 / 8 : parent_width;
            sizeWidth = parent_width / (wm ? 8 : 4);
            dateWidth = parent_width / (wm ? 4 : 4);
        }
        if( item.sel ) {
            row_view.setBackgroundColor( 0xFF4169E1 );
        }
        /*
        CheckedTextView chkView = (CheckedTextView)row_view.findViewById( R.id.chk );
        if( chkView != null ) {
            chkView.setText( selected ? "+" : " " );
        }
        */
        TextView nameView = (TextView)row_view.findViewById( R.id.fld_name );
        nameView.setWidth( nameWidth );
        nameView.setText( nameView != null ? name : "???" );
        TextView sizeView = (TextView)row_view.findViewById( R.id.fld_size );
        if( sizeView != null ) {
            sizeView.setVisibility( dm ? View.VISIBLE : View.GONE );
            sizeView.setWidth( sizeWidth );
            sizeView.setText( size );
        }
        TextView dateView = (TextView)row_view.findViewById( R.id.fld_date );
        if( dateView != null ) {
            sizeView.setVisibility( dm ? View.VISIBLE : View.GONE );
            dateView.setWidth( dateWidth );
            dateView.setText( date );
        }
        row_view.setTag( null );

        return row_view;
    }
    public final void showMessage( String s ) {
        Toast.makeText( commander.getContext(), s, Toast.LENGTH_LONG ).show();
    }
}
