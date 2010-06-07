package com.ghostsq.commander;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.Toast;

public abstract class CommanderAdapterBase extends BaseAdapter implements CommanderAdapter {
    private final static String TAG = "CommanderAdapterBase";
	protected final static String NOTIFY_STR = "str", NOTIFY_PRG1 = "prg1", NOTIFY_PRG2 = "prg2"; 
	
    protected Commander commander = null;
    protected static final String SLS = File.separator;
    protected static final String DEFAULT_DIR = "/sdcard";
    protected LayoutInflater mInflater = null;
    private   int    parentWidth, imgWidth, nameWidth, sizeWidth, dateWidth;
    private   boolean dirty = true;
    protected int    mode = 0;
    protected String parentLink;
    protected Engine worker = null;
    Handler handler = new Handler() {
        public void handleMessage( Message msg ) {
            int perc1 = msg.getData().getInt( CommanderAdapterBase.NOTIFY_PRG1 );
            int perc2 = msg.getData().getInt( CommanderAdapterBase.NOTIFY_PRG2, -1 );
            String str = msg.getData().getString( CommanderAdapterBase.NOTIFY_STR );
            if( perc1 < 0 ) {
                onComplete( worker );
                worker = null;
            }
            commander.notifyMe( str, perc1, perc2 );
        }
    };
    
    protected CommanderAdapterBase() {
        mode = 0;
        parentWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        parentLink = "/";       
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
    
    protected void onComplete( Engine engine) { // to override who need do something in the UI thread on an engine completion
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
                row_view.setBackgroundColor( 0 );
            }
            boolean icons = ( mode & MODE_ICONS ) == ICON_MODE;
            boolean fat = wm && ( mode & MODE_FINGERF ) == FAT_MODE;
            int vpad = fat ? ( icons ? 2 : 8 ) : 0;
            row_view.setPadding( 0, vpad, 4, vpad );        
            
            String name = item.dir ? item.name : " " + item.name, size = "", date = "";
            if( dm ) {
            	if( item.size > 0  )
            		size = Utils.getHumanSize( item.size );
                if( item.date != null ) {
    	            String dateFormat;
	            	dateFormat = item.date.getYear() + 1900 == Calendar.getInstance().get( Calendar.YEAR ) ?
	                        "MMM dd hh:mm" : "MMM dd  yyyy";
    	            date = (String)DateFormat.format( dateFormat, item.date );
                }
            }
            int parent_width = parent.getWidth();
            if( dirty || parentWidth != parent_width ) {
                parentWidth = parent_width;
                imgWidth = icons ? parent_width / ( fat || !wm ? 8 : 16 ) : 0;
                nameWidth = ( wm && dm ? parent_width * 5 / 8 : parent_width ) - imgWidth;
                sizeWidth = parent_width / (wm ? 8 : 4);
                dateWidth = parent_width / (wm ? 4 : 2);
                dirty = false;
            }
            if( item.sel ) {
                row_view.setBackgroundColor( 0xFF4169E1 );
            }

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
            }
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
        catch( Exception e ) {
            System.err.print("\ngetView() exception: " + e ); 
        }
        return null; // is it safe?
    }
    
    protected int getIconId( String file ) {
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
    
    protected String[] bitsToNames( SparseBooleanArray cis ) {
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
