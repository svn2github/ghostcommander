package com.ghostsq.commander.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import com.ghostsq.commander.R;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.VectorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.util.Log;
import android.view.ViewConfiguration;
import android.widget.Toast;
import android.media.ExifInterface;

public class ForwardCompat
{
    // to remove in the future. Left here to be compatible with old plugins
    public static void setFullPermissions( File file ) {
        file.setWritable( true, false );
        file.setReadable( true, false );
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void putStringSet( SharedPreferences.Editor ed, String key, Set<String> vs ) {
        ed.putStringSet( key, vs );
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static Set<String> getStringSet( SharedPreferences sp, String key ) {
        return sp.getStringSet( key, null );
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static SharedPreferences getDefaultSharedPreferences( Context ctx ) {
        return ctx.getSharedPreferences( ctx.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS );
    }    
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void getImageFileExtraInfo( ExifInterface exif, StringBuilder sb ) { 
        String ap = exif.getAttribute( ExifInterface.TAG_APERTURE );
        if( ap != null ) sb.append( "<br/><b>Aperture:</b> f" ).append( ap );
        String ex = exif.getAttribute( ExifInterface.TAG_EXPOSURE_TIME );
        if( ex != null ) sb.append( "<br/><b>Exposure:</b> " ).append( ex ).append( "s" );
        String fl = exif.getAttribute( ExifInterface.TAG_FOCAL_LENGTH );
        if( fl != null ) sb.append( "<br/><b>Focal length:</b> " ).append( fl );
        String is = exif.getAttribute( ExifInterface.TAG_ISO );
        if( is != null ) sb.append( "<br/><b>ISO level:</b> " ).append( is );
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setupActionBar( Activity a ) {
        ActionBar ab = a.getActionBar();
        if( ab == null ) return;
        final int size_class = ( a.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK );
        if( size_class <= Configuration.SCREENLAYOUT_SIZE_LARGE )
            ab.setDisplayShowTitleEnabled( false );
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static boolean hasPermanentMenuKey( Context ctx ) {
        return ViewConfiguration.get( ctx ).hasPermanentMenuKey();
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static Notification buildNotification( Context ctx, String str, PendingIntent pi ) {
         return new Notification.Builder( ctx )
                 .setContentTitle( str )
                 .setContentText( str )
                 .setSmallIcon( R.drawable.icon )
                 .setContentIntent( pi )
                 .build();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String[] getStorageDirs( Context ctx ) {
        File[] ff = ctx.getExternalFilesDirs( null );
        if( ff == null ) return null;
        String [] res = new String[ff.length];
        for( int i = 0; i < ff.length; i++ ) {
            if( ff[i] == null ) continue;
            String path = ff[i].getAbsolutePath();
            if( path == null ) continue;
            Log.d( "getStorageDirs", path );
            int pos = path.indexOf( "Android" );
            if( pos < 0 ) {
                Log.e( "getStorageDirs", "Unknown path " + path );
                continue;
            }
            res[i] = path.substring( 0, pos );
        }
        return res;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static int parseFileDescriptorMode( String mode ) {
        return ParcelFileDescriptor.parseMode( mode );
    }    
    
    @TargetApi(Build.VERSION_CODES.M)
    public static boolean requestPermission( Activity act, String[] perms, int rpc ) {
        ArrayList<String> al = new ArrayList<String>( perms.length ); 
        for( int i = 0; i < perms.length; i++ ) {
            int cp = act.checkPermission( perms[i], android.os.Process.myPid(), android.os.Process.myUid() );
            if( cp != PackageManager.PERMISSION_GRANTED )
                al.add( perms[i] );
        }
        if( al.size() > 0 ) {
            act.requestPermissions( al.toArray(perms), rpc );
            return false;
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static Parcelable createIcon( Context ctx, int icon_res_id ) {
        return Icon.createWithResource( ctx, icon_res_id );
    }
    
    @TargetApi(Build.VERSION_CODES.M)
    public static Parcelable createIcon( Bitmap bmp ) {
        return Icon.createWithBitmap( bmp );
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static Bitmap getBitmap( Drawable drawable ) {
        try {
            Drawable d2b = null;
            if( drawable instanceof BitmapDrawable ) {
                return ( (BitmapDrawable)drawable ).getBitmap();
            } else if( drawable instanceof VectorDrawable ) {
                d2b = drawable;
            } else if( drawable instanceof AdaptiveIconDrawable ) {
                Drawable backgroundDr = ( (AdaptiveIconDrawable)drawable ).getBackground();
                Drawable foregroundDr = ( (AdaptiveIconDrawable)drawable ).getForeground();
                Drawable[] drr = new Drawable[2];
                drr[0] = backgroundDr;
                drr[1] = foregroundDr;
                d2b = new LayerDrawable( drr );
            }
            int width  = d2b.getIntrinsicWidth();
            int height = d2b.getIntrinsicHeight();
            Bitmap bitmap = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
            Canvas canvas = new Canvas( bitmap );
            d2b.setBounds( 0, 0, canvas.getWidth(), canvas.getHeight() );
            d2b.draw( canvas );
            return bitmap;
        } catch( Exception e ) {
            Log.e( "getAppIcon()", "", e );
        }
        return null;
    }
    
    @TargetApi(Build.VERSION_CODES.O)
    public static boolean makeShortcut( Context ctx, Intent shortcut_intent, String label, Parcelable icon_p ) {
        try {
            ShortcutManager sm = ctx.getSystemService( ShortcutManager.class );
            if( !sm.isRequestPinShortcutSupported() ) {
                Toast.makeText( ctx, "Your home screen application does not support the Pin Shortcut feature.", Toast.LENGTH_LONG ).show();
                Log.w( "makeShortcut()", "ShortcutManager.isRequestPinShortcutSupported() returned false" );
            }
            Icon icon = (Icon)icon_p;
            ShortcutInfo si = new ShortcutInfo.Builder( ctx, label )
                .setIntent( shortcut_intent )
                .setShortLabel( label )
                .setIcon( icon )
                .build();
            return sm.requestPinShortcut( si, null );
        } catch( Exception e ) {
            Log.e( "GC.ForwardCompat", shortcut_intent.getDataString(), e );
        }
        return false;
    }
}
