package com.ghostsq.commander;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.Toast;

public abstract class CommanderAdapterBase extends BaseAdapter implements CommanderAdapter {
    private final static String TAG = "CommanderAdapterBase";
	protected final static String NOTIFY_STR = "str", NOTIFY_PRG1 = "prg1", NOTIFY_PRG2 = "prg2", NOTIFY_COOKIE = "cookie"; 
    protected Commander commander = null;
    protected static final String SLS = File.separator;
    protected static final char SLC = File.separator.charAt( 0 );
    protected static final String DEFAULT_DIR = "/sdcard";
    private   static boolean long_date = Locale.getDefault().getLanguage().compareTo( "en" ) != 0;
    protected LayoutInflater mInflater = null;
    private   int    parentWidth, imgWidth, nameWidth, sizeWidth, dateWidth;
    private   int    fg_color, sl_color;
    private   boolean dirty = true;
    protected int    mode = 0;
    protected String parentLink;
    protected Engine worker = null;
    
    Handler handler = new Handler() {
        public void handleMessage( Message msg ) {
            int perc1 = msg.getData().getInt( CommanderAdapterBase.NOTIFY_PRG1 );
            int perc2 = msg.getData().getInt( CommanderAdapterBase.NOTIFY_PRG2, -1 );
            String str = msg.getData().getString( CommanderAdapterBase.NOTIFY_STR );
            String cookie = msg.getData().getString( CommanderAdapterBase.NOTIFY_COOKIE );
            if( perc1 < 0 ) {
                onComplete( worker );
                worker = null;
            }
            Commander.Notify n_obj = cookie == null || cookie.length() == 0 ?
                 new Commander.Notify( str, perc1, perc2 ) :
                 new Commander.Notify( str, perc1, cookie );
            commander.notifyMe( n_obj );
        }
    };
    
    protected CommanderAdapterBase() {
        mode = 0;
        parentWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        parentLink = SLS;       
    }
    protected CommanderAdapterBase( Commander c, int mode_ ) {
    	Init( c );
        mode = mode_;
        parentWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        parentLink = SLS;    	
    }

    @Override
	public void Init( Commander c ) {
    	commander = c;		
    	mInflater = (LayoutInflater)c.getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
	}
    
    @Override
    public void setMode( int mask, int val ) {
        if( ( mask & SET_MODE_COLORS ) != 0 ) {
            switch( mask & SET_MODE_COLORS ) {
            case SET_TXT_COLOR: fg_color = val; break;
            case SET_SEL_COLOR: sl_color = val; break;
            }
            return;
        }
        mode &= ~mask;
        mode |= val;
        dirty = true;
    }
    @Override
    public void terminateOperation() {
        if( worker != null ) {
            //Toast.makeText( commander.getContext(), "Terminating...", Toast.LENGTH_SHORT ).show();
            worker.reqStop();
        }
    }
    @Override
    public void prepareToDestroy() {
        if( worker != null ) {
            worker.reqStop();
            worker = null;
        }
    }
    
    protected void onComplete( Engine engine ) { // to override who need do something in the UI thread on an engine completion
    }
    
    public class Item {
    	public String  name = "";
    	public Date    date = null;
    	public long    size = -1;
    	public boolean dir, sel;
    }
    
    @Override
    public long getItemId( int position ) {
        return position;
    }

    protected View getView( View convertView, ViewGroup parent, Item item ) {
        try {
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
                row_view.setBackgroundColor( 0 ); // transparent
            }
            boolean icons = ( mode & MODE_ICONS ) == ICON_MODE;
            boolean fat = wm && ( mode & MODE_FINGERF ) == FAT_MODE;
            int vpad = fat ? ( icons ? 2 : 8 ) : 0;
            row_view.setPadding( 0, vpad, 4, vpad );        
            
            String name = item.dir ? item.name : " " + item.name, size = "", date = "";
            if( dm ) {
            	if( !item.dir && item.size >= 0 )
            		size = Utils.getHumanSize( item.size );
                if( item.date != null ) {
                    if( long_date ) {
                        java.text.DateFormat locale_date_format = DateFormat.getDateFormat( commander.getContext() );
                        java.text.DateFormat locale_time_format = DateFormat.getTimeFormat( commander.getContext() );
                        date = locale_date_format.format( item.date ) + " " + locale_time_format.format( item.date );
                    } else {
        	            String dateFormat;
    	            	dateFormat = item.date.getYear() + 1900 == Calendar.getInstance().get( Calendar.YEAR ) ?
    	                        "MMM dd hh:mm" : "MMM dd  yyyy";
        	            date = (String)DateFormat.format( dateFormat, item.date );
                    }
                }
            }
            int parent_width = parent.getWidth();
            if( dirty || parentWidth != parent_width ) {
                parentWidth = parent_width;
                imgWidth = icons ? parent_width / ( fat || !wm ? 8 : 16 ) : 0;
                nameWidth = ( wm && dm ? parent_width * ( long_date ? 9 : 10 ) / 16 : parent_width ) - imgWidth;
                sizeWidth = parent_width / (wm ? ( long_date ? 9 : 8 ) : 4);
                dateWidth = wm ? (parent_width - ( imgWidth + nameWidth + sizeWidth )) : parent_width / 2;
                dirty = false;
            }
            if( item.sel )
                row_view.setBackgroundColor( sl_color & 0xCFFFFFFF  );

            ImageView imgView = (ImageView)row_view.findViewById( R.id.fld_icon );
            if( imgView != null ) {
                if( icons ) {
                    imgView.setVisibility( View.VISIBLE );
                    imgView.setAdjustViewBounds( true );
                    imgView.setMaxWidth( imgWidth );
                    imgView.setImageResource( item.dir ? R.drawable.folder : getIconId( name ) );
                }
                else
                    imgView.setVisibility( View.GONE );
            }
            TextView nameView = (TextView)row_view.findViewById( R.id.fld_name );
            if( nameView != null ) {
                nameView.setWidth( nameWidth );
                nameView.setText( name != null ? name : "???" );
                nameView.setTextColor( fg_color );
            }
            TextView sizeView = (TextView)row_view.findViewById( R.id.fld_size );
            if( sizeView != null ) {
                sizeView.setVisibility( dm ? View.VISIBLE : View.GONE );
                sizeView.setWidth( sizeWidth );
                sizeView.setText( size );
                sizeView.setTextColor( fg_color );
            }
            TextView dateView = (TextView)row_view.findViewById( R.id.fld_date );
            if( dateView != null ) {
                sizeView.setVisibility( dm ? View.VISIBLE : View.GONE );
                dateView.setWidth( dateWidth );
                dateView.setText( date );
                dateView.setTextColor( fg_color );
            }
            row_view.setTag( null );
    
            return row_view;
        }
        catch( Exception e ) {
            System.err.print("\ngetView() exception: " + e ); 
        }
        return null; // is it safe?
    }
    
    protected final static int getIconId( String file ) {
        MimeTypeMap mime_map = MimeTypeMap.getSingleton();
        if( mime_map == null )
            return R.drawable.unkn;
        String mime = null;
        String ext = Utils.getFileExt( file );
        if( ext != null && ext.length() > 0 )
            mime = mime_map.getMimeTypeFromExtension( ext.substring( 1 ) );
        if( mime == null )
            return R.drawable.unkn; 
        String type = mime.substring( 0, mime.indexOf( '/' ) );
        if( type.compareTo( "text" ) == 0 )  return R.drawable.text; 
        if( type.compareTo( "image" ) == 0 ) return R.drawable.image; 
        if( type.compareTo( "audio" ) == 0 ) return R.drawable.audio; 
        if( type.compareTo( "video" ) == 0 ) return R.drawable.video; 
        if( type.compareTo( "application" ) == 0 ) return R.drawable.application; 
        return R.drawable.unkn;
    }
    
    protected final String[] bitsToNames( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            String[] uris = new String[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    uris[j++] = getItemName( cis.keyAt( i ), true );
            return uris;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToNames()", e );
        }
        return null;
    }
    public final void showMessage( String s ) {
        Toast.makeText( commander.getContext(), s, Toast.LENGTH_LONG ).show();
    }
}
