package com.ghostsq.commander;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.ghostsq.commander.MountsListEngine.MountItem;

public class RemountEngine extends ExecEngine {
    MountItem mount;
    RemountEngine( Context ctx, Handler h, MountItem m ) {
        super( ctx, h );
        mount = m;
    }
    @Override
    public void run() {
        String cmd = null;
        try {
            String o = mount.getOptions();
            if( o == null ) {
                error( "No Options found" );
                return;
            }
            String[] flds = o.split( "," );
            boolean found = false;
            for( int i = 0; i < flds.length; i++ ) {
                if( flds[i].equals( "rw" ) ) {
                    flds[i] = "ro";
                    found = true;
                    break;
                }
                if( flds[i].equals( "ro" ) ) {
                    flds[i] = "rw";
                    found = true;
                    break;
                }
            }
            if( !found ) {
                error( "No ro/rw options found" );
                return;
            }
            cmd = "mount -o remount," + Utils.join( flds, "," ) + " " + mount.getName();
            execute( cmd, false, 500 );
        }
        catch( Exception e ) {
            Log.e( TAG, "On remount, ", e );
            error( "Exception: " + e );
        }
        finally {
            super.run();
            sendResult( errMsg != null ? ( cmd == null ? "" : "Tried to execute: '" + cmd + "'") : null );
        }
    }
}
