package com.ghostsq.commander.adapters;

import java.io.File;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.util.Log;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.Dialogs;
import com.ghostsq.commander.R;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;

import dalvik.system.DexClassLoader;

final public class CA {
    public static final String TAG = "CA";
    public static final int FS    = 0x00000001;
    public static final int FIND  = 0x00000002;
    public static final int LOCAL = FS | FIND;
    public static final int HOME  = 0x00000010;
    public static final int FAVS  = 0x00000020;
    public static final int NAV   = FAVS | HOME;
    public static final int ZIP   = 0x00000100;
    public static final int ARCH  = ZIP;
    public static final int ROOT  = 0x00001000;
    public static final int MNT   = 0x00002000;
    public static final int APPS  = 0x00004000;
    public static final int FTP   = 0x00010000;
    public static final int SMB   = 0x00020000;
    public static final int GDOCS = 0x00040000;
    public static final int NET   = FTP | SMB | GDOCS;
    public static final int ALL   = 0xFFFFFFFF;
    public static final int REAL  = LOCAL | ARCH | ROOT | NET;
    public static final int CHKBL  = REAL | APPS;

    // URI schemes hash codes
    protected static final int  home_schema_h =  "home".hashCode();  
    protected static final int   zip_schema_h =   "zip".hashCode();  
    protected static final int   ftp_schema_h =   "ftp".hashCode();  
    protected static final int  find_schema_h =  "find".hashCode();  
    protected static final int  root_schema_h =  "root".hashCode();  
    protected static final int   mnt_schema_h = "mount".hashCode();  
    protected static final int  apps_schema_h =  "apps".hashCode();
    protected static final int  favs_schema_h =  "favs".hashCode();
    protected static final int   smb_schema_h =   "smb".hashCode();
    protected static final int    gd_schema_h =    "gd".hashCode();
    protected static final int gdocs_schema_h = "gdocs".hashCode();

    /**
         the mapping between the scheme and the adapter type id 
         ("many-one" - because we could let the user to enter short aliases for the scheme instead)
    */
    public final static int GetAdapterTypeId( String scheme ) {
        if( scheme == null || scheme.length() == 0 ) return FS;
        final int scheme_h = scheme.hashCode();
        if( home_schema_h == scheme_h )  return HOME;
        if(  zip_schema_h == scheme_h )  return ZIP;
        if(  ftp_schema_h == scheme_h )  return FTP;
        if( find_schema_h == scheme_h )  return FIND;
        if( root_schema_h == scheme_h )  return ROOT;
        if(  mnt_schema_h == scheme_h )  return MNT;
        if( apps_schema_h == scheme_h )  return APPS;
        if( favs_schema_h == scheme_h )  return FAVS;
        if(  smb_schema_h == scheme_h )  return SMB;
        if(   gd_schema_h == scheme_h )  return GDOCS;
        if(gdocs_schema_h == scheme_h )  return GDOCS;
        return FS;
    }

    public final static boolean isLocal( String scheme ) {
        return scheme == null || scheme.length() == 0 || "file".equals( scheme ) || "find".equals( scheme );
    }
    
    public final static CommanderAdapter CreateAdapter( int type_id, Commander c ) {
        CommanderAdapter ca = CreateAdapterInstance( type_id, c.getContext() );
        if( ca != null )
            ca.Init( c );
        else {
            if( type_id == SMB  )
                c.showDialog( Dialogs.SMB_PLG_DIALOG );
        }
        return ca;
    }    

    public final static CommanderAdapter CreateAdapterInstance( int type_id, Context c ) {
        CommanderAdapter ca = null;
        if( type_id == FS   ) ca = new FSAdapter( c );    else
        if( type_id == HOME ) ca = new HomeAdapter( c );  else
        if( type_id == ZIP  ) ca = new ZipAdapter( c );   else
        if( type_id == FTP  ) ca = new FTPAdapter( c );   else
        if( type_id == FIND ) ca = new FindAdapter( c );  else
        if( type_id == ROOT ) ca = new RootAdapter( c );  else
        if( type_id == MNT  ) ca = new MountAdapter( c ); else
        if( type_id == APPS ) ca = new AppsAdapter( c );  else
        if( type_id == FAVS ) ca = new FavsAdapter( c );  else
        if( type_id == SMB  ) ca = CreateExternalAdapter( c, "samba", "SMBAdapter" );
        return ca;
    }
    
    /**
     * Tries to load an adapter class from foreign package
     * @param String type       - adapter type, also the suffix of the plugin application 
     * @param String class_name - the adapter class name to be loaded
     * @param int    dialog_id  - resource ID to show dialog if the class can't be loaded
     */
    public static CommanderAdapter CreateExternalAdapter( Context ctx, String type, String class_name ) {
        try {
            File dex_f = ctx.getDir( type, Context.MODE_PRIVATE );
            if( dex_f == null || !dex_f.exists() ) {
                Log.w( TAG, "app.data storage is not accessable, trying to use the SD card" );
                File sd = Environment.getExternalStorageDirectory();
                if( sd == null ) return null; // nowhere to store the dex :(
                dex_f = new File( sd, "temp" );
                if( !dex_f.exists() )
                    dex_f.mkdir();
            }
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo( "com.ghostsq.commander." + type, 0 );
            
            //Log.i( TAG, type + " package is " + ai.sourceDir );
            
            ClassLoader pcl = ctx.getClass().getClassLoader();
            DexClassLoader cl = new DexClassLoader( ai.sourceDir, dex_f.getAbsolutePath(), null, pcl );
            //
            Class<?> adapterClass = cl.loadClass( "com.ghostsq.commander." + type + "." + class_name );
            try {
                File[] list = dex_f.listFiles();
                for( int i = 0; i < list.length; i++ )
                    list[i].delete();
            }
            catch( Exception e ) {
                Log.w( TAG, "Can't remove the plugin's .dex: ", e );
            }
            if( adapterClass != null ) {
                CommanderAdapter ca = (CommanderAdapter)adapterClass.newInstance();
                return ca;
            }
        }
        catch( Throwable e ) {
            Log.e( TAG, type, e );
        }
        return null;
    }    
}
