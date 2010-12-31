package com.ghostsq.commander;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TableLayout;
import android.widget.Toast;

public abstract class CommanderAdapterBase extends BaseAdapter implements CommanderAdapter {
    private final static String TAG = "CommanderAdapterBase";
	protected final static String NOTIFY_STR = "str", NOTIFY_PRG1 = "prg1", NOTIFY_PRG2 = "prg2", NOTIFY_COOKIE = "cookie"; 
    protected Commander commander = null;
    protected static final String SLS = File.separator;
    protected static final char SLC = File.separator.charAt( 0 );
    private   static final boolean long_date = Locale.getDefault().getLanguage().compareTo( "en" ) != 0;
    protected LayoutInflater mInflater = null;
    private   int    parentWidth, imgWidth, nameWidth, sizeWidth, dateWidth, attrWidth;
    private   int    fg_color, sl_color;
    private   boolean dirty = true;
    protected int    mode = 0;
    protected String parentLink;
    protected Engine worker = null;
    
    protected Handler handler = new Handler() {
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
        attrWidth = 0;
        parentLink = SLS;       
    }
    protected CommanderAdapterBase( Commander c, int mode_ ) {
    	Init( c );
        mode = mode_;
        parentWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        attrWidth = 0;
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

    public boolean isWorkerStillAlive() {
        if( worker == null ) return false;
        return worker.reqStop();
    }
    // Virtual
    protected void onComplete( Engine engine ) { // to override who need do something in the UI thread on an engine completion
    }
    
    @Override
    public long getItemId( int position ) {
        return position;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = (Item)getItem( position );
        if( item == null ) return null;
        ListView flv = (ListView)parent;
        SparseBooleanArray cis = flv.getCheckedItemPositions();
        item.sel = cis != null ? cis.get( position ) : false;
        return getView( convertView, parent, item );
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
            	if( item.size >= 0 )
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
                imgWidth = icons ? ( fat ? 60 : 20 ) : 0;
                if( wm ) { // single row
                    if( dm ) {
                        if( ( ATTR_ONLY & mode ) != 0 ) {
                            sizeWidth = 0;
                            dateWidth = 0;
                            attrWidth = 140;
                            nameWidth = parent_width - imgWidth - attrWidth; 
                        }
                        else {
                            sizeWidth = 50;
                            dateWidth = long_date ? 90 : 80;
                            attrWidth = SHOW_ATTR == ( MODE_ATTR & mode )  && parent_width >= 320 ? 140 : 0;
                            nameWidth = parent_width - imgWidth - sizeWidth - dateWidth - attrWidth;
                            if( nameWidth < parent_width / 3 ) {
                                Log.w( TAG, "too few space for item's name: " + nameWidth + " of " + parent_width );
                                sizeWidth = 50;
                                dateWidth = long_date ? 90 : 80;
                                attrWidth = 0;
                                nameWidth = parent_width - imgWidth - sizeWidth - dateWidth;
                            }
                        }
                    }
                    else {
                        nameWidth = parent_width - imgWidth;
                        sizeWidth = 0;
                        dateWidth = 0;
                        attrWidth = 0;
                    }
                }
                else {
                    nameWidth = parent_width - imgWidth;
                    if( ( ATTR_ONLY & mode ) != 0 ) {
                        sizeWidth = 0;
                        dateWidth = 0;
                        attrWidth = parent_width;
                    }
                    else {
                        if( parent_width >= 230 ) {
                            sizeWidth = 50;
                            dateWidth = long_date ? 90 : 70;
                            attrWidth = SHOW_ATTR == ( MODE_ATTR & mode ) ? 
                               ( parent_width >= 280 ? 140 : parent_width - dateWidth - sizeWidth ) : 0;
                        }
                        else {
                            attrWidth = 0;
                            sizeWidth = parent_width / 4;
                            dateWidth = parent_width / 2;
                        }
                    }
                }
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
            TextView attrView = (TextView)row_view.findViewById( R.id.fld_attr );
            if( attrView != null ) {
                if( dm ) { // must be to not ruin the layout
                    attrView.setWidth( attrWidth );
                    attrView.setVisibility( View.VISIBLE );
                    attrView.setText( attrWidth == 0 || item.attr == null ? "" : item.attr );
                    attrView.setTextColor( fg_color );
                    //attrView.setBackgroundColor( 0xFFFF0000 );  // DEBUG!!!!!!
                }
                else
                    attrView.setVisibility( View.GONE ); 
            }
            TextView dateView = (TextView)row_view.findViewById( R.id.fld_date );
            if( dateView != null ) {
                dateView.setVisibility( dm ? View.VISIBLE : View.GONE );
                dateView.setWidth( dateWidth );
                dateView.setText( date );
                dateView.setTextColor( fg_color );
                //dateView.setBackgroundColor( 0xFF00AA00 );  // DEBUG!!!!!!
            }
            TextView sizeView = (TextView)row_view.findViewById( R.id.fld_size );
            if( sizeView != null ) {
                sizeView.setVisibility( dm ? View.VISIBLE : View.GONE );
                sizeView.setWidth( sizeWidth );
                sizeView.setText( size );
                sizeView.setTextColor( fg_color );
                //sizeView.setBackgroundColor( 0xFF0000FF );  // DEBUG!!!!!!
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
        if( file.indexOf( " -> " ) > 0 )
            return R.drawable.link;
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

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            Item item = (Item)getItem( acmi.position );
            boolean file = !item.dir && acmi.position != 0; 
            if( acmi.position == 0 ) {
                menu.add(0, R.id.enter, 0, R.string.enter );
                menu.add(0, R.id.eq, 0, R.string.oth_sh_this );
                return;
            }
            boolean fs_adapter = this instanceof FSAdapter || this instanceof FindAdapter;
            if( fs_adapter ) { 
                menu.add( 0, R.id.sz, 0, R.string.show_size );
                if( num <= 1 && file ) {
                    menu.add( 0, Commander.SEND_TO, 0, R.string.send_to );
                    menu.add( 0, R.id.F4, 0, R.string.edit_title );
                }
            }
            if( num <= 1 ) {
                menu.add( 0, R.id.F2, 0, R.string.rename_title );
            }
            menu.add( 0, R.id.F5, 0, R.string.copy_title );
            if( fs_adapter )
                menu.add( 0, R.id.F6, 0, R.string.move_title );
            menu.add( 0, R.id.F8, 0, R.string.delete_title );
            if( fs_adapter && file && num <= 1 ) 
                menu.add( 0, Commander.OPEN_WITH, 0, R.string.open_with );
        } catch( Exception e ) {
            Log.e( TAG, "populateContextMenu() " + e.getMessage(), e );
        }
    }    

    @Override
    public boolean isButtonActive( int brId ) {
        return true;
    }
    
    public final void showMessage( String s ) {
        Toast.makeText( commander.getContext(), s, Toast.LENGTH_LONG ).show();
    }
}
