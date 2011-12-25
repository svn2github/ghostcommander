package com.ghostsq.commander.adapters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;

public class AppsAdapter extends CommanderAdapterBase {
    private final static String TAG = "AppsAdapter";
    public static final String DEFAULT_LOC = "apps:";
// Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public final PackageManager     pm = ctx.getPackageManager();
    public  ApplicationInfo[]       appInfos = null;
    private final int MANAGE = 0, ACTIVITIES = 1, PROVIDERS = 2, MANIFEST = 3; 
    private final String[]          compTypes = { "Manage", "Activities", "Providers", "Manifest" };
    private ActivityInfo[]          actInfos = null;
    private ProviderInfo[]          prvInfos = null;
    
    
    private Uri uri;
    
    public AppsAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR );
        parentLink = PLS;
    }
    @Override
    public int getType() {
        return CA.APPS;
    }
    
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
        return mode;
    }    
    
    class ListEngine extends Engine {
        private ApplicationInfo[] items_tmp;
        public  String    pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
            super( h );
            pass_back_on_done = pass_back_on_done_;
        }
        public ApplicationInfo[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
                Init( null );
                List<ApplicationInfo> allApps = pm.getInstalledApplications( 0 );
                items_tmp = new ApplicationInfo[allApps.size()];
                allApps.toArray( items_tmp );
                if( ( mode & MODE_SORTING ) == SORT_NAME )
                    Arrays.sort( items_tmp, new ApplicationInfo.DisplayNameComparator( pm ) );
                sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
            }
            catch( Exception e ) {
                sendProgress( "Fail", Commander.OPERATION_FAILED, pass_back_on_done );
            }
            catch( OutOfMemoryError err ) {
                sendProgress( "Out Of Memory", Commander.OPERATION_FAILED, pass_back_on_done );
            }
            finally {
                super.run();
            }
        }
    }
    @Override
    protected void onReadComplete() {
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            appInfos = list_engine.getItems();
            reSort();
            numItems = appInfos != null ? appInfos.length : 0;
            notifyDataSetChanged();
        }
    }
    
    @Override
    public String toString() {
        return uri.toString();
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public void setUri( Uri uri_ ) {
        uri = uri_;
    }
    
    @Override
    public boolean readSource( Uri tmp_uri, String pbod ) {
        try {
            numItems = 1;
            appInfos = null;
            actInfos = null;
            prvInfos = null;
            mode &= ~ATTR_ONLY;
            if( reader != null ) {
                if( reader.reqStop() ) { // that's not good.
                    Thread.sleep( 500 );      // will it end itself?
                    if( reader.isAlive() ) {
                        Log.e( TAG, "Busy!" );
                        return false;
                    }
                }
            }
            if( tmp_uri != null )
                uri = tmp_uri;
            String a = uri.getAuthority(); 
            if( a == null || a.length() == 0 ) {    // enumerate the applications
                commander.notifyMe( new Commander.Notify( Commander.OPERATION_STARTED ) );
                reader = new ListEngine( readerHandler, pbod );
                reader.start();
                return true;
            }
            
            String path = uri.getPath();
            if( path == null || path.length() <= 1 ) {
                numItems = compTypes.length + 1;
                commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                return true;
            }
            else {
                List<String> ps = uri.getPathSegments();
                if( ps != null && ps.size() == 1 ) {
                    mode |= ATTR_ONLY;
                    PackageInfo pi = pm.getPackageInfo( a, PackageManager.GET_ACTIVITIES | 
                                                           PackageManager.GET_PROVIDERS );
                    if( compTypes[ACTIVITIES].equals( ps.get( 0 ) ) ) {
                        actInfos = pi.activities != null ? pi.activities : new ActivityInfo[0];
                        reSort();
                        numItems = actInfos.length + 1;
                        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                        return true;
                    } else if( compTypes[PROVIDERS].equals( ps.get( 0 ) ) ) {
                        prvInfos = pi.providers != null ? pi.providers : new ProviderInfo[0];
                        numItems = prvInfos.length + 1;
                        commander.notifyMe( new Commander.Notify( null, Commander.OPERATION_COMPLETED, pbod ) );
                        return true;
                    } 
                }
            }
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        commander.notifyMe( new Commander.Notify( "Fail", Commander.OPERATION_FAILED ) );
        return false;
    }
    
    @Override
    protected void reSort() {
        if( appInfos != null ) { 
            ApplicationInfoComparator comp = new ApplicationInfoComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
            Arrays.sort( appInfos, comp );
        }
        else if( actInfos != null ) {
            ActivityInfoComparator comp = new ActivityInfoComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
            Arrays.sort( actInfos, comp );
        }
        else if( prvInfos != null ) {
        }
        else {
        }
    }

    private static <T> ArrayList<T> bitsToInfos( SparseBooleanArray cis, T[] items ) {
        try {
            ArrayList<T> al = new ArrayList<T>();
            for( int i = 0; i < cis.size(); i++ ) {
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        al.add( items[ k - 1 ] );
                }
            }
            return al;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToNames()'s Exception: " + e );
        }
        return null;
    }
    
    private final ApplicationInfo[] bitsToApplicationInfos( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            ApplicationInfo[] subItems = new ApplicationInfo[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        subItems[j++] = appInfos[ k - 1 ];
                }
            return subItems;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToNames()'s Exception: " + e );
        }
        return null;
    }

    private String[] flagsStrs = {
        "SYSTEM",
        "DEBUGGABLE",
        "HAS_CODE",
        "PERSISTENT",
        "FACTORY_TEST",
        "ALLOW_TASK_REPARENTING",
        "ALLOW_CLEAR_USER_DATA",
        "UPDATED_SYSTEM_APP",
        "TEST_ONLY",
        "SUPPORTS_SMALL_SCREENS",
        "SUPPORTS_NORMAL_SCREENS",
        "SUPPORTS_LARGE_SCREENS",
        "RESIZEABLE_FOR_SCREENS",
        "SUPPORTS_SCREEN_DENSITIES",
        "VM_SAFE_MODE",
        "ALLOW_BACKUP",
        "KILL_AFTER_RESTORE",
        "RESTORE_ANY_VERSION",
        "EXTERNAL_STORAGE",
        "SUPPORTS_XLARGE_SCREENS",
        "NEVER_ENCRYPT",
        "FORWARD_LOCK",
        "CANT_SAVE_STATE" 
    };

    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        if( appInfos == null ) return;
        ArrayList<ApplicationInfo> al = bitsToInfos( cis, appInfos );
        if( al == null || al.size() == 0 ) {
            notErr();
            return;
        }
        final String cs = ": ";
        StringBuffer sb = new StringBuffer( 1024 );
        for( int i = 0; i < al.size(); i++ ) {
            ApplicationInfo ai = al.get( i );
            String v = null;
            int    vc = 0;
            String size = null;
            String date = null;
            String flags = null;
            try {
                PackageInfo pi = pm.getPackageInfo( ai.packageName, 0 );
                v = pi.versionName;
                vc = pi.versionCode;
                File asdf = new File( ai.sourceDir );
                date = getLocalDateTimeStr( new Date( asdf.lastModified() ) );
                size = Utils.getHumanSize( asdf.length() );
                StringBuffer fsb = new StringBuffer( 512 );
                int ff = ai.flags;
                for( int fi = 0; fi < flagsStrs.length; fi++ ) {
                    if( ( ( 1<<fi ) & ff ) != 0 ) {
                        if( fsb.length() > 0 )
                            fsb.append( " | " );
                        fsb.append( flagsStrs[fi] );
                    }
                }
                fsb.append( " (" ).append( Integer.toHexString( ff ) ).append( ")" );
                flags = fsb.toString();
            } catch( NameNotFoundException e ) {}
            sb.append( s( R.string.pkg_name ) ).append( cs ).append( ai.packageName );
            if( v != null ) 
              sb.append( "\n" ).append( s( R.string.version ) ).append( cs ).append( v );
            if( vc > 0 )
              sb.append( "\n" ).append( s( R.string.version_code ) ).append( cs ).append( vc );
            sb.append( "\n" ).append( s( R.string.target_sdk ) ).append( cs ).append( ai.targetSdkVersion );
            sb.append( "\n" ).append( "UID" ).append( cs ).append( ai.uid );
            sb.append( "\n" ).append( s( R.string.location ) ).append( cs ).append( ai.sourceDir );
            if( date != null )
              sb.append( "\n" ).append( s( R.string.inst_date ) ).append( cs ).append( date );
            if( size  != null )
              sb.append( "\n" ).append( s( R.string.pkg_size ) ).append( cs ).append( size );
            sb.append( "\n" ).append( s( R.string.process ) ).append( cs ).append( ai.processName );
            if( ai.className != null )
              sb.append( "\n" ).append( s( R.string.app_class ) ).append( cs ).append( ai.className );
            if( ai.taskAffinity != null )
              sb.append( "\n" ).append( s( R.string.affinity ) ).append( cs ).append( ai.taskAffinity );
            if( ai.permission != null )
              sb.append( "\n" ).append( s( R.string.permission ) ).append( cs ).append( ai.permission );
            if( flags != null )
              sb.append( "\n" ).append( s( R.string.flags ) ).append( cs ).append( flags );
            sb.append( "\n" );
        }
        commander.notifyMe( new Commander.Notify( sb.toString(), Commander.OPERATION_COMPLETED, Commander.OPERATION_REPORT_IMPORTANT ) );
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( appInfos == null ) return notErr();
        ArrayList<ApplicationInfo> al = bitsToInfos( cis, appInfos );
        if( al == null || al.size() == 0 ) {
            commander.notifyMe( new Commander.Notify( s( R.string.copy_err ), Commander.OPERATION_FAILED ) );
            return false;
        }
        String[] paths = new String[al.size()];
        for( int i = 0; i < al.size(); i++ )
            paths[i] = al.get( i ).sourceDir;
        
        boolean ok = to.receiveItems( paths, MODE_COPY );
        if( !ok ) commander.notifyMe( new Commander.Notify( Commander.OPERATION_FAILED ) );
        return ok;
    }
        
    @Override
    public boolean createFile( String fileURI ) {
        return notErr();
    }

    @Override
    public void createFolder( String new_name ) {
        notErr();
    }
    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        if( appInfos == null ) return notErr();
        ArrayList<ApplicationInfo> al = bitsToInfos( cis, appInfos );
        if( al == null ) return false;
        for( int i = 0; i < al.size(); i++ ) {
            Intent in = new Intent( Intent.ACTION_DELETE, Uri.parse( "package:" + al.get( i ).packageName ) );
            commander.issue( in, 0 );
        }
        return true;
    }
    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean c ) {
        return notErr();
    }
    
    @Override
    public void openItem( int position ) {
        try {
            if( appInfos != null  ) {
                if( position == 0 ) {
                    commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null );
                }
                else if( position <= appInfos.length ) {
                    ApplicationInfo ai = appInfos[position - 1];
                    commander.Navigate( uri.buildUpon().authority( ai.packageName ).build(), null );
                }
            } else if( actInfos != null ) {
                if( position == 0 ) {
                    commander.Navigate( uri.buildUpon().path( null ).build(), compTypes[ACTIVITIES] );
                }
                else if( position <= actInfos.length ) {
                    // ???
                }
            } else if( prvInfos != null ) {
                if( position == 0 ) {
                    commander.Navigate( uri.buildUpon().path( null ).build(), compTypes[PROVIDERS] );
                }
                else if( position <= prvInfos.length ) {
                    // ???
                }
            } else {
                if( position == 0 ) {
                    commander.Navigate( Uri.parse( DEFAULT_LOC ), uri.getAuthority() );
                }
                else if( position-1 == MANAGE ) {
                    String p = uri.getAuthority();
                    Intent in = new Intent( Intent.ACTION_VIEW );
                    in.setClassName( "com.android.settings", "com.android.settings.InstalledAppDetails" );
                    in.putExtra( "com.android.settings.ApplicationPkgName", p );
                    in.putExtra( "pkg", p );

                    List<ResolveInfo> acts = pm.queryIntentActivities( in, 0 );
                    if( acts.size( ) > 0 )
                        commander.issue( in, 0 );
                    else {
                        in = new Intent( "android.settings.APPLICATION_DETAILS_SETTINGS", Uri.fromParts( "package", p, null ) );
                        acts = pm.queryIntentActivities( in, 0 );
                        if( acts.size( ) > 0 )
                            commander.issue( in, 0 );
                        else {
                            Log.e( TAG, "Failed to resolve activity for InstalledAppDetails" );
                        }
                    }
                    
                }
                else if( position-1 == MANIFEST ) {
                    String p = uri.getAuthority();
                    ApplicationInfo ai = pm.getApplicationInfo( p, 0 );
                    String m = extractManifest( ai.publicSourceDir );
                    commander.showInfo( m );
                }
                else if( position <= compTypes.length ) {
                    commander.Navigate( uri.buildUpon().path( compTypes[position - 1] ).build(), null );
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, uri.toString() + " " + position, e );
        }
    }

    @Override
    public String getItemName( int position, boolean full ) {
        if( position == 0 )
            return parentLink;
        if( appInfos != null ) {
            return position <= appInfos.length ? appInfos[position - 1].packageName : null;
        }
        if( actInfos != null ) {
            return position <= actInfos.length ? actInfos[position - 1].name : null;
        }
        if( prvInfos != null ) {
            return position <= prvInfos.length ? prvInfos[position - 1].toString() : null;
        }
        return position <= compTypes.length ? compTypes[position - 1] : null;
    }

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        if( position == 0 ) {
            item.name = parentLink;
            item.dir = true;
            return item;
        }
        item.name = "???";
        if( appInfos != null ) { 
            if( position >= 0 && position <= appInfos.length ) {
                ApplicationInfo ai = appInfos[position - 1];
                item.dir = false;
                item.name = ai.loadLabel( pm ).toString();
                item.sel = false;
                
                File asdf = new File( ai.sourceDir );
                item.date = new Date( asdf.lastModified() );
                item.size = asdf.length();
                item.attr = ai.packageName;
                item.setThumbNail( ai.loadIcon( pm ) );
                item.thumb_is_icon = true;
            }
        }
        else if( actInfos != null ) {
            if( position <= actInfos.length ) {
                ActivityInfo ai = actInfos[position - 1];
                item.name = ai.loadLabel( pm ).toString(); 
                item.attr = ai.name;
                item.setThumbNail( ai.loadIcon( pm ) );
                item.thumb_is_icon = true;
            }
        }
        else if( prvInfos != null ) {
            if( position <= prvInfos.length ) {
                ProviderInfo pi = prvInfos[position - 1];
                item.name = pi.loadLabel( pm ).toString(); 
                item.attr = pi.name;
                item.setThumbNail( pi.loadIcon( pm ) );
                item.thumb_is_icon = true;
            }
        }
        else {
            if( position <= compTypes.length )
                item.name = compTypes[position - 1];
        }
        return item;
    }

    @Override
    protected int getPredictedAttributesLength() {
        return 36;   // "com.softwaremanufacturer.productname"
    }

    private class ApplicationInfoComparator implements Comparator<ApplicationInfo> {
        int     type;
        boolean ascending;
        ApplicationInfo.DisplayNameComparator aidnc;
        
        public ApplicationInfoComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            aidnc = new ApplicationInfo.DisplayNameComparator( pm );
            type = type_;
            ascending = ascending_;
        }
        @Override
        public int compare( ApplicationInfo ai1, ApplicationInfo ai2 ) {
            int ext_cmp = 0;
            try {
                switch( type ) {
                case CommanderAdapter.SORT_EXT:
                    if( ai1.packageName != null )
                        ext_cmp = ai1.packageName.compareTo( ai2.packageName );
                    break;
                case CommanderAdapter.SORT_SIZE:  {
                        File asdf1 = new File( ai1.sourceDir );
                        File asdf2 = new File( ai2.sourceDir );
                        ext_cmp = asdf1.length() - asdf2.length() < 0 ? -1 : 1;
                    }
                    break;
                case CommanderAdapter.SORT_DATE: {
                        File asdf1 = new File( ai1.sourceDir );
                        File asdf2 = new File( ai2.sourceDir );
                        ext_cmp = asdf1.lastModified() - asdf2.lastModified() < 0 ? -1 : 1;
                    }
                    break;
                }
                if( ext_cmp == 0 )
                    ext_cmp = aidnc.compare( ai1, ai2 );
            } catch( Exception e ) {
            }
            return ascending ? ext_cmp : -ext_cmp;
        }
    }

    private class ActivityInfoComparator implements Comparator<ActivityInfo> {
        private int     type;
        private boolean ascending;
        public  final PackageManager pm_;
        
        public ActivityInfoComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            pm_ = pm;
            type = type_;
            ascending = ascending_;
        }
        @Override
        public int compare( ActivityInfo ai1, ActivityInfo ai2 ) {
            int ext_cmp = 0;
            try {
                switch( type ) {
                case CommanderAdapter.SORT_EXT:
                    if( ai1.packageName != null )
                        ext_cmp = ai1.name.compareTo( ai2.name );
                    break;
                }
                if( ext_cmp == 0 ) {
                    String cn1 = ai1.loadLabel( pm_ ).toString();
                    String cn2 = ai2.loadLabel( pm_ ).toString();
                    ext_cmp = cn1.compareTo( cn2 );
                }
            } catch( Exception e ) {
            }
            return ascending ? ext_cmp : -ext_cmp;
        }
    }
    
    private final String extractManifest( String zip_path ) {
        try {
            if( zip_path == null ) return null;
            ZipFile  zip = new ZipFile( zip_path );
            ZipEntry entry = zip.getEntry( "AndroidManifest.xml" );
            if( entry != null ) {
                InputStream is = zip.getInputStream( entry );
                if( is != null ) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream( (int)entry.getSize() );
                    byte[] buf = new byte[4096];
                    int n;
                    while( ( n = is.read( buf ) ) != -1 )
                        baos.write( buf, 0, n );
                    is.close( );
                    return decompressXML( baos.toByteArray() );
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }

    // http://stackoverflow.com/questions/2097813/how-to-parse-the-androidmanifest-xml-file-inside-an-apk-package
    // decompressXML -- Parse the 'compressed' binary form of Android XML docs 
    // such as for AndroidManifest.xml in .apk files
    public static int endDocTag = 0x00100101;
    public static int startTag =  0x00100102;
    public static int endTag =    0x00100103;
    public String decompressXML( byte[] xml ) {
        StringBuffer xml_sb = new StringBuffer( 8192 ); 
        // Compressed XML file/bytes starts with 24x bytes of data,
        // 9 32 bit words in little endian order (LSB first):
        //   0th word is 03 00 08 00
        //   3rd word SEEMS TO BE:  Offset at then of StringTable
        //   4th word is: Number of strings in string table
        // WARNING: Sometime I indiscriminently display or refer to word in 
        //   little endian storage format, or in integer format (ie MSB first).
        int numbStrings = LEW(xml, 4*4);
        
        // StringIndexTable starts at offset 24x, an array of 32 bit LE offsets
        // of the length/string data in the StringTable.
        int sitOff = 0x24;  // Offset of start of StringIndexTable
        
        // StringTable, each string is represented with a 16 bit little endian 
        // character count, followed by that number of 16 bit (LE) (Unicode) chars.
        int stOff = sitOff + numbStrings*4;  // StringTable follows StrIndexTable
        
        // XMLTags, The XML tag tree starts after some unknown content after the
        // StringTable.  There is some unknown data after the StringTable, scan
        // forward from this point to the flag for the start of an XML start tag.
        int xmlTagOff = LEW(xml, 3*4);  // Start from the offset in the 3rd word.
        // Scan forward until we find the bytes: 0x02011000(x00100102 in normal int)
        for (int ii=xmlTagOff; ii<xml.length-4; ii+=4) {
          if (LEW(xml, ii) == startTag) { 
            xmlTagOff = ii;  break;
          }
        } // end of hack, scanning for start of first start tag
        
        // XML tags and attributes:
        // Every XML start and end tag consists of 6 32 bit words:
        //   0th word: 02011000 for startTag and 03011000 for endTag 
        //   1st word: a flag?, like 38000000
        //   2nd word: Line of where this tag appeared in the original source file
        //   3rd word: FFFFFFFF ??
        //   4th word: StringIndex of NameSpace name, or FFFFFFFF for default NS
        //   5th word: StringIndex of Element Name
        //   (Note: 01011000 in 0th word means end of XML document, endDocTag)
        
        // Start tags (not end tags) contain 3 more words:
        //   6th word: 14001400 meaning?? 
        //   7th word: Number of Attributes that follow this tag(follow word 8th)
        //   8th word: 00000000 meaning??
        
        // Attributes consist of 5 words: 
        //   0th word: StringIndex of Attribute Name's Namespace, or FFFFFFFF
        //   1st word: StringIndex of Attribute Name
        //   2nd word: StringIndex of Attribute Value, or FFFFFFF if ResourceId used
        //   3rd word: Flags?
        //   4th word: str ind of attr value again, or ResourceId of value
        
        // TMP, dump string table to tr for debugging
        //tr.addSelect("strings", null);
        //for (int ii=0; ii<numbStrings; ii++) {
        //  // Length of string starts at StringTable plus offset in StrIndTable
        //  String str = compXmlString(xml, sitOff, stOff, ii);
        //  tr.add(String.valueOf(ii), str);
        //}
        //tr.parent();
        
        // Step through the XML tree element tags and attributes
        int off = xmlTagOff;
        int indent = 0;
        int startTagLineNo = -2;
        while( off < xml.length ) {
          int tag0 = LEW(xml, off);
          //int tag1 = LEW(xml, off+1*4);
          int lineNo = LEW(xml, off+2*4);
          //int tag3 = LEW(xml, off+3*4);
          int nameNsSi = LEW(xml, off+4*4);
          int nameSi = LEW(xml, off+5*4);
        
          if (tag0 == startTag) { // XML START TAG
            int tag6 = LEW(xml, off+6*4);  // Expected to be 14001400
            int numbAttrs = LEW(xml, off+7*4);  // Number of Attributes to follow
            //int tag8 = LEW(xml, off+8*4);  // Expected to be 00000000
            off += 9*4;  // Skip over 6+3 words of startTag data
            String name = compXmlString(xml, sitOff, stOff, nameSi);
            //tr.addSelect(name, null);
            startTagLineNo = lineNo;
        
            // Look for the Attributes
            StringBuffer sb = new StringBuffer();
            for (int ii=0; ii<numbAttrs; ii++) {
              int attrNameNsSi = LEW(xml, off);  // AttrName Namespace Str Ind, or FFFFFFFF
              int attrNameSi = LEW(xml, off+1*4);  // AttrName String Index
              int attrValueSi = LEW(xml, off+2*4); // AttrValue Str Ind, or FFFFFFFF
              int attrFlags = LEW(xml, off+3*4);  
              int attrResId = LEW(xml, off+4*4);  // AttrValue ResourceId or dup AttrValue StrInd
              off += 5*4;  // Skip over the 5 words of an attribute
        
              String attrName = compXmlString(xml, sitOff, stOff, attrNameSi);
              String attrValue = attrValueSi!=-1
                ? compXmlString(xml, sitOff, stOff, attrValueSi)
                : "resourceID 0x"+Integer.toHexString(attrResId);
              sb.append(" "+attrName+"=\""+attrValue+"\"");
              //tr.add(attrName, attrValue);
            }
            xml_sb.append( "\n" ).append( spaces( indent ) ).append( "<" ).append( name );
            if( sb.length() > 0 )
                xml_sb.append( "\n" ).append( spaces( indent+1 ) ).append( sb );
            xml_sb.append( ">" );
            indent++;
        
          } else if (tag0 == endTag) { // XML END TAG
            indent--;
            off += 6*4;  // Skip over 6 words of endTag data
            String name = compXmlString(xml, sitOff, stOff, nameSi);
            xml_sb.append( "\n" ).append( spaces( indent ) ).append( "</" ).append( name ).append( ">" );
//            prtIndent(indent, "</"+name+">  (line "+startTagLineNo+"-"+lineNo+")");
            //tr.parent();  // Step back up the NobTree
        
          } else if (tag0 == endDocTag) {  // END OF XML DOC TAG
            break;
        
          } else {
            Log.e( TAG, "  Unrecognized tag code '"+Integer.toHexString(tag0) +"' at offset "+off);
            break;
          }
        } // end of while loop scanning tags and attributes of XML tree
        Log.v( TAG, "    end at offset "+off );
        return xml_sb.toString();
    } // end of decompressXML
    
    
    public String compXmlString(byte[] xml, int sitOff, int stOff, int strInd) {
      if (strInd < 0) return null;
      int strOff = stOff + LEW(xml, sitOff+strInd*4);
      return compXmlStringAt(xml, strOff);
    }
    
    public String spaces( int i ) {
        char[] dummy = new char[i*2];
        Arrays.fill( dummy, ' ' );
        return new String( dummy );
    }
    
    // compXmlStringAt -- Return the string stored in StringTable format at
    // offset strOff.  This offset points to the 16 bit string length, which 
    // is followed by that number of 16 bit (Unicode) chars.
    public String compXmlStringAt(byte[] arr, int strOff) {
      int strLen = arr[strOff+1]<<8&0xff00 | arr[strOff]&0xff;
      byte[] chars = new byte[strLen];
      for (int ii=0; ii<strLen; ii++) {
        chars[ii] = arr[strOff+2+ii*2];
      }
      return new String(chars);  // Hack, just use 8 byte chars
    } // end of compXmlStringAt
    
    
    // LEW -- Return value of a Little Endian 32 bit word from the byte array
    //   at offset off.
    public int LEW(byte[] arr, int off) {
      return arr[off+3]<<24&0xff000000 | arr[off+2]<<16&0xff0000
        | arr[off+1]<<8&0xff00 | arr[off]&0xFF;
    } // end of LEW

}
