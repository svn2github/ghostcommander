package com.ghostsq.commander.adapters;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.Dialogs;
import com.ghostsq.commander.R;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;

final public class CA {
    public static final int FS    = 0x00000001;
    public static final int FIND  = 0x00000002;
    public static final int LOCAL = 0x00000003;
    public static final int HOME  = 0x00000010;
    public static final int FAVS  = 0x00000020;
    public static final int NAV   = 0x00000030;
    public static final int ZIP   = 0x00000100;
    public static final int ARCH  = 0x00000F00;
    public static final int ROOT  = 0x00001000;
    public static final int MNT   = 0x00002000;
    public static final int APPS  = 0x00004000;
    public static final int FTP   = 0x00010000;
    public static final int SMB   = 0x00020000;
    public static final int GDOCS = 0x00040000;
    public static final int NET   = 0x000F0000;
    public static final int ALL   = 0xFFFFFFFF;
    public static final int REAL  = LOCAL | ARCH | ROOT | NET;

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

    public final static CommanderAdapter CreateAdapter( int type_id, Commander c ) {
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
        if( type_id == SMB  ) ca = c.CreateExternalAdapter( "samba", "SMBAdapter", Dialogs.SMB_PLG_DIALOG );
        return ca;
    }
}
