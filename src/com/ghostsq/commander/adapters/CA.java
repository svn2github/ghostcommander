package com.ghostsq.commander.adapters;

import java.io.File;
import java.lang.reflect.Constructor;

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

/**
 * <code>CA</code> class
 * @author Ghost Squared (ghost.sq2@gmail.com)
 * 
 * This class is a "database" of all implemented adapters.
 * It keeps all the properties, hashes etc, to let the right adapter be instatiated
 * 
 */
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
    public static final int DBOX  = 0x00040000;
    public static final int GDOCS = 0x00080000;
    public static final int SFTP  = 0x00100000;
    public static final int BOX   = 0x00200000;
    public static final int NET   = FTP | SMB | GDOCS | SFTP | BOX;
    public static final int REAL  = LOCAL | ARCH | ROOT | NET;
    public static final int CHKBL  = REAL | APPS | FAVS;
    public static final int ALL   = 0xFFFFFFFF;

    // URI schemes hash codes
    protected static final int  home_schema_h =  "home".hashCode();  
    protected static final int   zip_schema_h =   "zip".hashCode();  
    protected static final int   ftp_schema_h =   "ftp".hashCode();  
    protected static final int  sftp_schema_h =  "sftp".hashCode();  
    protected static final int  find_schema_h =  "find".hashCode();  
    protected static final int  root_schema_h =  "root".hashCode();  
    protected static final int   mnt_schema_h = "mount".hashCode();  
    protected static final int  apps_schema_h =  "apps".hashCode();
    protected static final int  favs_schema_h =  "favs".hashCode();
    protected static final int   smb_schema_h =   "smb".hashCode();
    protected static final int  dbox_schema_h =  "dbox".hashCode();
    protected static final int   box_schema_h =   "box".hashCode();
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
        if( sftp_schema_h == scheme_h )  return SFTP;
        if( find_schema_h == scheme_h )  return FIND;
        if( root_schema_h == scheme_h )  return ROOT;
        if(  mnt_schema_h == scheme_h )  return MNT;
        if( apps_schema_h == scheme_h )  return APPS;
        if( favs_schema_h == scheme_h )  return FAVS;
        if(  smb_schema_h == scheme_h )  return SMB;
        if( dbox_schema_h == scheme_h )  return DBOX;
        if(  box_schema_h == scheme_h )  return BOX;
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
            if( type_id == SFTP  )
                c.showDialog( Dialogs.SFTP_PLG_DIALOG );
        }
        return ca;
    }    

    public final static CommanderAdapter CreateAdapterInstance( int type_id, Context c ) {
        CommanderAdapter ca;
        if( type_id == FS   ) ca = new FSAdapter( c );    else
        if( type_id == HOME ) ca = new HomeAdapter( c );  else
        if( type_id == ZIP  ) ca = new ZipAdapter( c );   else
        if( type_id == FTP  ) ca = new FTPAdapter( c );   else
        if( type_id == FIND ) ca = new FindAdapter( c );  else
        if( type_id == ROOT ) ca = new RootAdapter( c );  else
        if( type_id == MNT  ) ca = new MountAdapter( c ); else
        if( type_id == APPS ) ca = new AppsAdapter( c );  else
        if( type_id == FAVS ) ca = new FavsAdapter( c );  else
        if( type_id == SMB  ) ca = CreateExternalAdapter( c, "samba",  "SMBAdapter" );  else
        if( type_id == DBOX ) ca = CreateExternalAdapter( c, "dropbox","DBoxAdapter" ); else
        if( type_id == BOX  ) ca = CreateExternalAdapter( c, "box",    "BoxAdapter" );  else
        if( type_id == SFTP ) ca = CreateExternalAdapter( c, "sftp",   "SFTPAdapter" ); else
            ca = null;
        return ca;
    }
    
    /**
     * CreateExternalAdapter Tries to load an adapter class from foreign package
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
            
            Log.i( TAG, type + " package is " + ai.sourceDir );
            
            ClassLoader pcl = ctx.getClass().getClassLoader();
            DexClassLoader cl = new DexClassLoader( ai.sourceDir, dex_f.getAbsolutePath(), null, pcl );
            //
            Class<?> adapterClass = cl.loadClass( "com.ghostsq.commander." + type + "." + class_name );
            Log.i( TAG, "Class has been loaded " + adapterClass.toString() );
            
            try {
                File[] list = dex_f.listFiles();
                for( int i = 0; i < list.length; i++ )
                    list[i].delete();
            }
            catch( Exception e ) {
                Log.w( TAG, "Can't remove the plugin's .dex: ", e );
            }
            if( adapterClass != null ) {
                Constructor<?> constr = null;
                try {
                    constr = adapterClass.getConstructor( Context.class );
                } catch( Exception e ) {
                    e.printStackTrace();
                }
                if( constr != null )
                    return (CommanderAdapter)constr.newInstance( ctx );
                else
                    return (CommanderAdapter)adapterClass.newInstance();
            }
        }
        catch( Throwable e ) {
            Log.e( TAG, "This class can't be created: " + type, e );
        }
        return null;
    }    

    public final static int getSuitableAdapter( int cmd_id ) {
        switch( cmd_id ) {
        case  R.id.F1:           return  CA.ALL;
        case  R.id.F2:           return  CA.REAL | CA.FAVS;
        case  R.id.F3:           return  CA.REAL;
        case  R.id.F4:           return  CA.REAL & ~CA.ARCH | CA.FAVS;
        case  R.id.SF4:          return  CA.FS;
        case  R.id.F5:           return  CA.REAL | CA.APPS;
        case  R.id.F6:           return  CA.REAL;
        case  R.id.F7:           return  CA.REAL & ~CA.FIND | CA.MNT;
        case  R.id.F8:           return  CA.REAL | CA.FAVS | CA.APPS;
        case  R.id.F9:           return  CA.ALL;
        case  R.id.F10:          return  CA.ALL;
        case  R.id.eq:           return  CA.ALL;
        case  R.id.tgl:          return  CA.ALL;
        case  R.id.sz:           return  CA.LOCAL | CA.ROOT | CA.APPS | CA.SMB | CA.SFTP;
        case  R.id.by_name:      return  CA.REAL | CA.APPS | CA.FAVS;
        case  R.id.by_ext:       return  CA.REAL | CA.APPS | CA.FAVS;
        case  R.id.by_size:      return  CA.REAL | CA.APPS | CA.FAVS;
        case  R.id.by_date:      return  CA.REAL | CA.APPS;
        case  R.id.sel_all:      return  CA.REAL | CA.APPS;
        case  R.id.uns_all:      return  CA.REAL | CA.APPS;
        case  R.id.enter:        return  CA.ALL;
        case  R.id.add_fav:      return  CA.ALL;
        case  R.id.remount:      return  CA.ROOT;
        case  R.id.home:         return  CA.ALL & ~CA.HOME;
        case  R.id.favs:         return  CA.ALL & ~CA.FAVS;
        case  R.id.sdcard:       return  CA.ALL;
        case  R.id.root:         return  CA.ALL;
        case  R.id.mount:        return  CA.ROOT | CA.NAV;
        case  R.id.hidden:       return  CA.REAL;
        case  R.id.refresh:      return  CA.REAL | CA.FAVS;
        case  R.id.softkbd:      return  CA.ALL;
        case  R.id.search:       return  CA.LOCAL;
        case  R.id.menu:         return  CA.ALL;
        }
        return 0;
    }
    public final static boolean suitable( int cmd_id, int type ) {
        int sa_type = getSuitableAdapter( cmd_id );
        return ( sa_type & type ) != 0;
    }
}
