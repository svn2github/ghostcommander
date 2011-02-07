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
	protected final static String NOTIFY_STR = "str", NOTIFY_PRG1 = "prg1", NOTIFY_PRG2 = "prg2", NOTIFY_COOKIE = "cookie"; 
    protected Commander commander = null;
    public    static final String SLS = File.separator;
    public    static final char   SLC = File.separator.charAt( 0 );
    public    static final String PLS = "..";
    private   static final boolean long_date = Locale.getDefault().getLanguage().compareTo( "en" ) != 0;
    protected LayoutInflater mInflater = null;
    private   int    parentWidth, imgWidth, icoWidth, nameWidth, sizeWidth, dateWidth, attrWidth;
    private   int    fg_color, sl_color;
    private   boolean dirty = true;
    protected int    thumbnail_size_perc = 100;
    protected int    mode = 0;
    protected String parentLink;
    protected Engine worker = null;
    protected int    numItems = 0;

    // URI shemes hash codes
    private static final int   zip_schema_h =   "zip".hashCode();  
    private static final int   ftp_schema_h =   "ftp".hashCode();  
    private static final int  find_schema_h =  "find".hashCode();  
    private static final int  root_schema_h =  "root".hashCode();  
    private static final int   mnt_schema_h = "mount".hashCode();  
    private static final int  apps_schema_h =  "apps".hashCode();  
    private static final int   smb_schema_h =   "smb".hashCode();
    private static final int    gd_schema_h =    "gd".hashCode();
    private static final int gdocs_schema_h = "gdocs".hashCode();
    // adapters names hash codes
    private static final int type_h_fs    = "file".hashCode();   
    private static final int type_h_zip   = zip_schema_h;  
    private static final int type_h_ftp   = ftp_schema_h; 
    private static final int type_h_find  = find_schema_h;
    private static final int type_h_root  = root_schema_h;
    private static final int type_h_mnt   = mnt_schema_h;
    private static final int type_h_apps  = apps_schema_h;
    private static final int type_h_smb   = smb_schema_h;
    private static final int type_h_gdocs = gdocs_schema_h;
    
    // the mapping between the scheme and the adapter type name 
    // because we could let the user to enter short aliases for the scheme instead
    final static int GetAdapterTypeHash( String scheme ) {
        if( scheme == null ) return type_h_fs; 
        final int scheme_h = scheme.hashCode();
        if(  zip_schema_h == scheme_h )  return type_h_zip;   
        if(  ftp_schema_h == scheme_h )  return type_h_ftp;  
        if( find_schema_h == scheme_h )  return type_h_find;  
        if( root_schema_h == scheme_h )  return type_h_root;  
        if(  mnt_schema_h == scheme_h )  return type_h_mnt;  
        if( apps_schema_h == scheme_h )  return type_h_apps;  
        if(  smb_schema_h == scheme_h )  return type_h_smb;
        if(   gd_schema_h == scheme_h )  return type_h_gdocs;
        if(gdocs_schema_h == scheme_h )  return type_h_gdocs;
        return type_h_fs;
    }

    final static CommanderAdapter CreateAdapter( int type_h, Commander c ) {
        CommanderAdapter ca = null;
        if( type_h == type_h_fs  )  ca = new FSAdapter( c );    else
        if( type_h == type_h_zip )  ca = new ZipAdapter( c );   else
        if( type_h == type_h_ftp )  ca = new FTPAdapter( c );   else
        if( type_h == type_h_find ) ca = new FindAdapter( c );  else
        if( type_h == type_h_root ) ca = new RootAdapter( c );  else
        if( type_h == type_h_mnt  ) ca = new MountAdapter( c ); else
        if( type_h == type_h_smb  ) ca = c.CreateExternalAdapter( "samba", "SMBAdapter", R.id.smb );
        return ca;
    }
    
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
        icoWidth = 0;
        imgWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        attrWidth = 0;
        parentLink = SLS;       
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
        if( ( mask & SET_TBN_SIZE ) != 0 ) {
            thumbnail_size_perc = val;
            return;
        }
        mode &= ~mask;
        mode |= val;
        dirty = true;
        
        if( mask == CommanderAdapter.MODE_SORTING ) {
            reSort();
            notifyDataSetChanged();
        }
    }
    @Override
    public void terminateOperation() {
        Log.i( getClass().getName(), "terminateOperation()" );
        if( worker != null ) {
            worker.reqStop();
        }
    }
    @Override
    public void prepareToDestroy() {
        Log.i( getClass().getName(), "prepareToDestroy()" );
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
                icoWidth = icons ? ( fat ? 60 : 40 ) : 0;
                imgWidth = thumbnail_size_perc > 0 && thumbnail_size_perc != 100 ? icoWidth * thumbnail_size_perc / 100 : icoWidth;
                if( wm ) { // single row
                    if( dm ) {
                        if( ( ATTR_ONLY & mode ) != 0 ) {
                            sizeWidth = 0;
                            dateWidth = 0;
                            attrWidth = ( parent_width - imgWidth ) / 2;
                            nameWidth = parent_width - imgWidth - attrWidth; 
                        }
                        else if( SHOW_ATTR == ( MODE_ATTR & mode ) && parent_width > 480 ) {
                            int rest = parent_width - imgWidth;
                            nameWidth = parent_width > 800 ? rest / 2 : rest / 3;
                            rest = parent_width - imgWidth - nameWidth; 
                            sizeWidth = rest / 4;
                            dateWidth = rest / 4;
                            attrWidth =  rest / 2;
                        }
                        else {
                            int rest = parent_width - imgWidth;
                            nameWidth = parent_width > 560 ? rest * 2 / 3 : rest / 2;
                            rest = parent_width - imgWidth - nameWidth; 
                            sizeWidth = rest / 3;
                            dateWidth = rest * 2 / 3;
                            attrWidth = 0;
                        }

                    } else {
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
                        if( parent_width >= 230 && SHOW_ATTR == ( MODE_ATTR & mode ) ) {
                            sizeWidth = parent_width / 4;
                            dateWidth = parent_width / 4;
                            attrWidth = parent_width / 2;
                        }
                        else {
                            attrWidth = 0;
                            sizeWidth = parent_width / 4;
                            dateWidth = parent_width / 4;
                        }
                    }
                }
                dirty = false;
            }
            
            //Log.v( TAG, "p:" + parent_width + ",i:" + imgWidth + ",n:" + nameWidth + ",d:" + dateWidth + ",s:" + sizeWidth + ",a:" + attrWidth );            
            
            if( item.sel )
                row_view.setBackgroundColor( sl_color & 0xCFFFFFFF  );

            ImageView imgView = (ImageView)row_view.findViewById( R.id.fld_icon );
            if( imgView != null ) {
                if( icons ) {
                    imgView.setVisibility( View.VISIBLE );
                    imgView.setAdjustViewBounds( true );
                    if( item.thumbnail != null && thumbnail_size_perc > 0 ) {
                        imgView.setMaxWidth( imgWidth );
                        imgView.setImageDrawable( item.thumbnail );
                    }
                    else {
                        imgView.setMaxWidth( icoWidth );
                        imgView.setImageResource( item.dir || item.name.equals( SLS ) || 
                               item.name.equals( PLS ) ? R.drawable.folder : getIconId( name ) );
                    }
                }
                else
                    imgView.setVisibility( View.GONE );
            }
            TextView nameView = (TextView)row_view.findViewById( R.id.fld_name );
            if( nameView != null ) {
                nameView.setWidth( nameWidth );
                nameView.setText( name != null ? name : "???" );
                nameView.setTextColor( fg_color );
                //nameView.setBackgroundColor( 0xFFFF00FF );  // DEBUG!!!!!!
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
        String ext = Utils.getFileExt( file );
        String mime = Utils.getMimeByExt( ext );
        String type = mime.substring( 0, mime.indexOf( '/' ) );
        if( type.compareTo( "text" ) == 0 )  return R.drawable.text; 
        if( type.compareTo( "image" ) == 0 ) return R.drawable.image; 
        if( type.compareTo( "audio" ) == 0 ) return R.drawable.audio; 
        if( type.compareTo( "video" ) == 0 ) return R.drawable.video; 
        if( type.compareTo( "application" ) == 0 ) return R.drawable.application; 
        return R.drawable.unkn;
    }
    
    protected final int getImgWidth() {
        return imgWidth == 0 ? ( thumbnail_size_perc > 0 ? thumbnail_size_perc * 40 / 100 : 0 ) : imgWidth;
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
            Log.e( getClass().getName(), "bitsToNames()", e );
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
            if( fs_adapter ) {
                menu.add( 0, R.id.F6, 0, R.string.move_title );
            }
            menu.add( 0, R.id.F8, 0, R.string.delete_title );
            if( fs_adapter ) { 
                if( file && num <= 1 ) 
                    menu.add( 0, Commander.OPEN_WITH, 0, R.string.open_with );
                menu.add( 0, R.id.new_zip, 0, R.string.create_zip );
            }
        } catch( Exception e ) {
            Log.e( getClass().getName(), "populateContextMenu() " + e.getMessage(), e );
        }
    }    

    @Override
    public boolean isButtonActive( int brId ) {
        return true;
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        // to be implemented in derived classes
    }
    
    protected void reSort() {
        // to override all the derives
    }

    public final void showMessage( String s ) {
        Toast.makeText( commander.getContext(), s, Toast.LENGTH_LONG ).show();
    }
}
