package com.ghostsq.commander.adapters;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.ghostsq.commander.R;
import com.ghostsq.commander.Commander;
import com.ghostsq.commander.Dialogs;
import com.ghostsq.commander.adapters.FavsAdapter;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public abstract class CommanderAdapterBase extends BaseAdapter implements CommanderAdapter {
    public    final static String NOTIFY_STR = "str", NOTIFY_PRG1 = "prg1", NOTIFY_PRG2 = "prg2", NOTIFY_COOKIE = "cookie"; 
    public    final static String NOTIFY_RECEIVER_HASH = "hash";
    protected final static String NOTIFY_ITEMS_TO_RECEIVE = "itms"; 
    protected final static String DEFAULT_DIR = "/sdcard";
    protected final String TAG = getClass().getName();
    protected Commander commander = null;
    public    static final String SLS = File.separator;
    public    static final char   SLC = File.separator.charAt( 0 );
    public    static final String PLS = "..";
    private   static final boolean long_date = Locale.getDefault().getLanguage().compareTo( "en" ) != 0;
    private   java.text.DateFormat localeDateFormat;
    private   java.text.DateFormat localeTimeFormat;
    
    protected static final int ICON_SIZE = 32;
    protected int     icoWidth = ICON_SIZE, imgWidth = ICON_SIZE;
    protected float   density = 1;
    protected LayoutInflater mInflater = null;
    private   int     parentWidth, nameWidth, sizeWidth, dateWidth, attrWidth;
    private   int     fg_color, sl_color;
    private   boolean dirty = true;
    protected int     thumbnail_size_perc = 100, font_size = 18;
    protected int     mode = 0;
    protected boolean ascending = true;
    protected String  parentLink;
    private   CommanderAdapter recipient = null;
    protected int     numItems = 0;
    public    int     shownFrom = 0, shownNum = 3;


    // Virtual method - to override!
    // derived adapter classes need to override this to take the obtained items array and notify the dataset change
    protected void onReadComplete() {  
    }
    
    protected class ReaderHandler extends Handler {
        public void handleMessage( Message msg ) {
            try {
                Bundle b = msg.getData();
                int code = b.getInt( CommanderAdapterBase.NOTIFY_PRG1 );
                String str = b.getString( CommanderAdapterBase.NOTIFY_STR );
                String cookie = b.getString( CommanderAdapterBase.NOTIFY_COOKIE );
                if( code <= Commander.OPERATION_FAILED ) {
                    onReadComplete();
                    reader = null;
                }
                Commander.Notify n_obj = new Commander.Notify( str, code, cookie );
                commander.notifyMe( n_obj );
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }
    };    

    protected class WorkerHandler extends Handler {
        public void handleMessage( Message msg ) {
            try {
                Bundle b = msg.getData();
                int rec_hash = b.getInt( CommanderAdapterBase.NOTIFY_RECEIVER_HASH );
                if( rec_hash != 0 ) {
                    if( recipient != null ) {
                        if( recipient.hashCode() == rec_hash ) {
                            String[] items = b.getStringArray( CommanderAdapterBase.NOTIFY_ITEMS_TO_RECEIVE );
                            if( items != null )
                                recipient.receiveItems( items, MODE_MOVE_DEL_SRC_DIR );
                        }
                        recipient = null;
                    }                   
                    return;
                }
                int perc1 = b.getInt( CommanderAdapterBase.NOTIFY_PRG1 );
                int perc2 = b.getInt( CommanderAdapterBase.NOTIFY_PRG2, -1 );
                String str = b.getString( CommanderAdapterBase.NOTIFY_STR );
                Commander.Notify n_obj = new Commander.Notify( str, perc1, perc2 );
                if( commander.notifyMe( n_obj ) )
                    worker = null;
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }
    };    

    protected Engine  reader = null, worker = null;
    protected WorkerHandler workerHandler = new WorkerHandler();
    protected ReaderHandler readerHandler = new ReaderHandler();
   
    protected CommanderAdapterBase() {
        // don't forget to call the Init( c ) method  if  the default constructor can be called!
    }
    protected CommanderAdapterBase( Commander c, int mode_ ) {
    	Init( c );
        mode = mode_;
    }

    @Override
	public void Init( Commander c ) {
        mode = 0;
        parentWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        attrWidth = 0;
        parentLink = SLS;       
    	commander = c;
    	Context ctx = c.getContext();
    	mInflater = (LayoutInflater)ctx.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
    	Utils.changeLanguage( ctx );
    	localeDateFormat = DateFormat.getDateFormat( ctx );
    	localeTimeFormat = DateFormat.getTimeFormat( ctx );
    	density = ctx.getResources().getDisplayMetrics().density;
	}
    
    private final void calcWidths() {
        try {
            icoWidth = ( mode & MODE_ICONS ) == ICON_MODE ? 
                    (int)( density * ( ( mode & MODE_FINGERF ) == FAT_MODE || 
                                       ( mode & WIDE_MODE ) != WIDE_MODE ? ICON_SIZE : ICON_SIZE / 2 ) ) : 0;
            imgWidth = thumbnail_size_perc > 0 && thumbnail_size_perc != 100 ? icoWidth * thumbnail_size_perc / 100 : icoWidth;
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
    
    public int getImgWidth() {
        return imgWidth;
    }
    
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & SET_MODE_COLORS ) != 0 ) {
            switch( mask & SET_MODE_COLORS ) {
            case SET_TXT_COLOR: fg_color = val; break;
            case SET_SEL_COLOR: sl_color = val; break;
            }
            return 0;
        }
        if( ( mask & SET_FONT_SIZE ) != 0 ) {
            font_size = val;
            return 0;
        }
        if( ( mask & SET_TBN_SIZE ) != 0 ) {
            thumbnail_size_perc = val;
            calcWidths();
            return 0;
        }
        if( ( mask & ( MODE_FINGERF | MODE_ICONS ) ) != 0 )
            calcWidths();
        
        mode &= ~mask;
        mode |= val;
        if( mask == LIST_STATE ) {
            /*
            Log.v( TAG, ( mode & LIST_STATE ) == STATE_IDLE ? 
                    "list    I D L E  !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" :
                    "list    B U S Y  !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" );
                    // Android v2.3.3 has a bug (again!)
            */
        }
        else
            dirty = true;
        if( ( mask & MODE_SORT_DIR ) != 0 ||
            ( mask & MODE_SORTING )  != 0 ) {
            if( ( mask & MODE_SORT_DIR ) != 0 )
                ascending = ( val & MODE_SORT_DIR ) == SORT_ASC;
            reSort();
            notifyDataSetChanged();
        }
        return mode;
    }
    @Override
    public void terminateOperation() {
        Log.i( TAG, "terminateOperation()" );
        if( worker != null )
            worker.reqStop();
    }
    @Override
    public void prepareToDestroy() {
        Log.i( TAG, "prepareToDestroy()" );
        terminateOperation();
        worker = null;
        if( reader != null )
            reader.reqStop();
        reader = null;
    }

    public final boolean isWorkerStillAlive() {
        if( worker == null ) return false;
        return worker.reqStop();
    }

    protected boolean notErr() {
        commander.notifyMe( new Commander.Notify( "Not supported.", Commander.OPERATION_FAILED ) );
        return false;
    }
    
    protected final String createTempDir() {
        Date d = new Date();
        File temp_dir = new File( DEFAULT_DIR + "/temp/gc_" + d.getHours() + d.getMinutes() + d.getSeconds() );
        temp_dir.mkdirs();
        return temp_dir.getAbsolutePath();
    }

    protected final int setRecipient( CommanderAdapter to ) {
        if( recipient != null )
            Log.e( TAG, "Recipient is not null!!!" );
        recipient = to;
        return recipient.hashCode(); 
    }    

    @Override
    public int getCount() {
        return numItems;
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

    private String getLocalDateTimeStr( Date date ) {
        try {
            return localeDateFormat.format( date ) + " " + localeTimeFormat.format( date );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return "(ERR)";
    }
    
    protected View getView( View convertView, ViewGroup parent, Item item ) {
        View row_view = null;
        try {
            int parent_width = parent.getWidth();
            boolean recalc = dirty || parentWidth != parent_width;
            parentWidth = parent_width;
            dirty = false;
            boolean wm = (mode & MODE_WIDTH) == WIDE_MODE;
            boolean dm = ( mode & MODE_DETAILS ) == DETAILED_MODE;
            boolean ao = ( ATTR_ONLY & mode ) != 0;
            boolean current_wide = convertView != null && convertView.getId() == R.id.row_layout;
            if( convertView == null || 
        		( (  wm && !current_wide ) || 
        		  ( !wm &&  current_wide ) ) ) {
                row_view = mInflater.inflate( wm ? R.layout.row : R.layout.narrow, parent, false );
            }
            else {
                row_view = convertView;
                row_view.setBackgroundColor( 0 ); // transparent
            }
            boolean fat = ( mode & MODE_FINGERF ) == FAT_MODE;
            final int  LEFT_P = 1;
            final int RIGHT_P = 2;
            
            ImageView imgView = (ImageView)row_view.findViewById( R.id.fld_icon );
            TextView nameView =  (TextView)row_view.findViewById( R.id.fld_name );
            TextView attrView =  (TextView)row_view.findViewById( R.id.fld_attr );
            TextView dateView =  (TextView)row_view.findViewById( R.id.fld_date );
            TextView sizeView =  (TextView)row_view.findViewById( R.id.fld_size );

            float fnt_sz_rdc = font_size - font_size/4;   // reduced font size
            String name = item.name, size = "", date = "";
            if( dm ) {
            	if( item.size >= 0 )
            		size = Utils.getHumanSize( item.size );
            	final String MDHM_date_frm = "MMM dd hh:mm";
                if( item.date != null ) {
                    if( long_date ) {
                        date = getLocalDateTimeStr( item.date );
                    } else {
        	            String dateFormat;
    	            	dateFormat = item.date.getYear() + 1900 == Calendar.getInstance().get( Calendar.YEAR ) ?
    	                        MDHM_date_frm : "MMM dd yyyy ";
        	            date = (String)DateFormat.format( dateFormat, item.date );
                    }
                }
                if( recalc ) {
                    //Log.v( TAG, "recalc" );
                    if( ao ) {
                        sizeWidth = 0;
                        dateWidth = 0;
                        attrWidth = wm ? ( parent_width - imgWidth ) / 2 : parent_width - LEFT_P - RIGHT_P;
                    }
                    else {
                        if( dateView != null ) {
                            dateView.setTextSize( fnt_sz_rdc );
                            // dateWidth is pixels, but what's the return of measureText() ???
                            String sample_date = long_date ? "M" + getLocalDateTimeStr( new Date( -1 ) ) : MDHM_date_frm;
                            if( wm ) sample_date += "M";
                            dateWidth = (int)dateView.getPaint().measureText( sample_date );
                        }
                        if( sizeView != null ) {
                            sizeView.setTextSize( fnt_sz_rdc );
                            // sizeWidth is pixels, but what's the return of measureText() ???
                            sizeWidth = (int)sizeView.getPaint().measureText( "9999.9M" );
                        }
                        if( attrView != null ) {
                            if( wm ) {
                                attrView.setTextSize( fnt_sz_rdc );
                                // sizeWidth is pixels, but what's the return of measureText() ???
                                attrWidth = (int)sizeView.getPaint().measureText( "---------- system system" );
                            }
                            else
                                attrWidth = parent_width - sizeWidth - dateWidth - icoWidth - LEFT_P - RIGHT_P;
                        }
                    }
                }
            }
            if( item.sel )
                row_view.setBackgroundColor( sl_color & 0xCFFFFFFF  );
            int img_width = icoWidth;
            if( imgView != null ) {
                if( icoWidth > 0 ) {
                    imgView.setVisibility( View.VISIBLE );
                    imgView.setAdjustViewBounds( true );
                    boolean th_ok = false;
                    if( item.isThumbNail() && thumbnail_size_perc > 0 ) {
                        Drawable th = item.getThumbNail();
                        if( th != null ) {
                            if( !item.thumb_is_icon )
                                img_width = imgWidth;
                            imgView.setMaxWidth( img_width );
                            imgView.setImageDrawable( th );
                            th_ok = true;
                        }
                    }
                    if( !th_ok ) {
                        // when list is on its end we don't receive the idle notification!
                        if( thumbnail_size_perc > 0 && !item.no_thumb && ( mode & LIST_STATE ) == STATE_IDLE ) {
                            synchronized( this ) {
                                item.need_thumb = true;
                                notifyAll();
                            }
                        }
                        try {
                            imgView.setMaxWidth( img_width );
                            imgView.setImageResource( item.icon_id != -1 ? item.icon_id : 
                               ( item.dir || item.name.equals( SLS ) || item.name.equals( PLS ) ? R.drawable.folder : getIconId( name ) ) );
                        }
                        catch( OutOfMemoryError e ) {
                            Log.e( TAG, "", e );
                        }
                    }
                }
                else
                    imgView.setVisibility( View.GONE );
            }
            if( nameView != null ) {
                nameView.setTextSize( font_size );
                if( wm ) {
                    nameWidth = parent_width - img_width - dateWidth - sizeWidth - attrWidth - LEFT_P - RIGHT_P;
                    if( nameWidth < 200 ) {
                        attrWidth = 0;
                        nameWidth = parent_width - img_width - dateWidth - sizeWidth - LEFT_P - RIGHT_P; 
                    }
                    nameView.setWidth( nameWidth );
                }
                nameView.setText( name != null ? name : "???" );
                nameView.setTextColor( fg_color );
//nameView.setBackgroundColor( 0xFFFF00FF );  // DEBUG!!!!!!
            }
            if( dateView != null ) {
                boolean vis = dm && !ao && ( dateWidth > 0 );
                dateView.setVisibility( vis ? View.VISIBLE : View.GONE );
                if( vis ) {
                    dateView.setTextSize( fnt_sz_rdc );
                    dateView.setWidth( dateWidth );
                    dateView.setText( date );
                    dateView.setTextColor( fg_color );
//dateView.setBackgroundColor( 0xFF00AA00 );  // DEBUG!!!!!!
                }
            }
            if( sizeView != null ) {
                boolean vis = dm && !ao && ( sizeWidth > 0 );
                sizeView.setVisibility( vis ? View.VISIBLE : View.GONE );
                if( vis ) {
                    sizeView.setTextSize( fnt_sz_rdc );
                    sizeView.setWidth( sizeWidth );
                    sizeView.setText( size );
                    sizeView.setTextColor( fg_color );
//sizeView.setBackgroundColor( 0xFF0000FF );  // DEBUG!!!!!!
                }
            }
            if( attrView != null ) {
                boolean vis = dm && ( attrWidth > 0 );
                attrView.setVisibility( vis ? View.VISIBLE : View.GONE );
                if( vis ) {
                    if( !wm )
                        attrView.setPadding( img_width + 2, 0, 4, 0 ); // not to overlap the icon
                    attrView.setTextSize( fnt_sz_rdc );
                    if( wm ) attrView.setWidth( attrWidth );
                    attrView.setVisibility( View.VISIBLE );
                    attrView.setText( item.attr == null ? "" : item.attr.trim() );
                    attrView.setTextColor( fg_color );
//attrView.setBackgroundColor( 0xFFFF0000 );  // DEBUG!!!!!!
                }
                else
                    attrView.setVisibility( View.GONE ); 
            }

            if( fat ) {
                int vp = (int)( 10 * density );
                row_view.setPadding( LEFT_P, vp, RIGHT_P, vp );
            }
            else 
                row_view.setPadding( LEFT_P, 2, RIGHT_P, 2 );
            
            row_view.setTag( null );
//Log.v( TAG, "p:" + parent_width + ",i:" + img_width + ",n:" + nameWidth + ",d:" + dateWidth + ",s:" + sizeWidth + ",a:" + attrWidth );            
        }
        catch( Exception e ) {
            Log.e( TAG, null, e ); 
        }
        return row_view;
    }
    
    protected final static int getIconId( String file ) {
        if( file.indexOf( " -> " ) > 0 )
            return R.drawable.link;
        String ext = Utils.getFileExt( file );
        if( ".apk".equalsIgnoreCase( ext ) || ".dex".equalsIgnoreCase( ext ) )
            return R.drawable.and;        
        if( ".zip".equalsIgnoreCase( ext ) || ".jar".equalsIgnoreCase( ext ) )
            return R.drawable.zip;        
        if( ".pdf".equalsIgnoreCase( ext ) )
            return R.drawable.pdf;        
        if( ".vcf".equalsIgnoreCase( ext ) )
            return R.drawable.book;        
        String mime = Utils.getMimeByExt( ext );
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
                menu.add(0, R.id.add_fav, 0, R.string.add_fav );
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
            if( num <= 1 )
                menu.add( 0, R.id.F2, 0, R.string.rename_title );
            menu.add( 0, R.id.F5, 0, R.string.copy_title );
            if( fs_adapter ) {
                menu.add( 0, R.id.F6, 0, R.string.move_title );
            }
            menu.add( 0, R.id.F8, 0, R.string.delete_title );
            if( fs_adapter ) { 
                if( file && num <= 1 ) 
                    menu.add( 0, Commander.OPEN_WITH, 0, R.string.open_with );
            }
            
            menu.add( 0, R.id.new_zip, 0, R.string.new_zip );
            if( num <= 1 )
                menu.add( 0, Commander.COPY_NAME, 0, R.string.copy_name );
            if( item.dir && acmi.position != 0 )
                menu.add( 0, Commander.FAV_FLD, 0, commander.getContext().getString( R.string.fav_fld, item.name ) );
        } catch( Exception e ) {
            Log.e( TAG, "populateContextMenu() " + e.getMessage(), e );
        }
    }    

    @Override
    public void setIdentities( String name, String pass ) {
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        // to be implemented in derived classes
    }
    
    protected void reSort() {
        // to override all the derives
    }
/*
    public final void showMessage( String s ) {
        Toast.makeText( commander.getContext(), s, Toast.LENGTH_LONG ).show();
    }
*/
    protected final String s( int r_id ) {
        return commander.getContext().getString( r_id ); 
    }
}

