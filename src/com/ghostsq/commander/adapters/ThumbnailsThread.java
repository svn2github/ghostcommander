package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.adapters.FSAdapter.FileItem;
import com.ghostsq.commander.utils.MnfUtils;
import com.ghostsq.commander.utils.Utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.BaseColumns;
import android.util.Log;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Thumbnails;

class ThumbnailsThread extends Thread {
    private final static String TAG = "ThumbnailsThread";
    private final static int NOTIFY_THUMBNAIL_CHANGED = 653;
    private CommanderAdapterBase owner;
    private ContentResolver cr;
    private Handler thread_handler;
    private String base_path;
    private Item[] list;
    private BitmapFactory.Options options;
    private Resources res;
    private byte[] buf;
    private int thumb_sz;
    private final int apk_h = ".apk".hashCode();
    private final int[] ext_h = { ".jpg".hashCode(), ".JPG".hashCode(), ".jpeg".hashCode(), ".JPEG".hashCode(), ".png".hashCode(),
            ".PNG".hashCode(), ".gif".hashCode(), ".GIF".hashCode(), apk_h };
    public static final Map<Integer, SoftReference<Drawable>> thumbnailCache = new HashMap<Integer, SoftReference<Drawable>>();

    ThumbnailsThread( CommanderAdapterBase owner, Handler thread_handler, String base_path, Item[] list ) {
        this.owner = owner;
        setName( getClass().getName() );
        this.thread_handler = thread_handler;
        this.base_path = base_path;
        this.list = list;
        buf = new byte[100 * 1024];
        cr = owner.ctx.getContentResolver();
    }

    @Override
    public void run() {
        try {
            if( list == null )
                return;
            setPriority( Thread.MIN_PRIORITY );
            thumb_sz = owner.getImgWidth();
            options = new BitmapFactory.Options();
            res = owner.ctx.getResources();
            int fails_count = 0;
            boolean visible_only = list.length > 100; // too many icons
            for( int a = 0; a < 2; a++ ) {
                boolean succeeded = true;
                boolean need_update = false, proc_visible = false, proc_invisible = false;
                int processed = 0;
                for( int i = 0; i < list.length; i++ ) {
                    visible_only = visible_only || fails_count > 1;
                    if( visible_only ) Log.v( TAG, "thumb on requests only" );
                    
                    int n = -1;
                    while( true ) {
                        for( int j = 0; j < list.length; j++ ) {
                            if( list[j].need_thumb ) {
                                n = j;
                                proc_visible = true;
                                //Log.v( TAG,"A thumbnail requested ahead of time!!! " + n + ", " + list[n].name );
                                break;
                            } else {
                                list[j].remThumbnailIfOld( visible_only ? 10000 : 60000 ); 
                                // to free some memory
                            }
                        }
                        if( !visible_only || proc_visible )
                            break;
                        // Log.v( TAG, "Tired. Waiting for a request to proceed" );
                        synchronized( owner ) {
                            owner.wait();
                        }
                    }
                    proc_invisible = n < 0;
                    if( proc_invisible )
                        n = i;
                    else
                        i--;
                    if( !proc_visible ) {
                        yield();
                        sleep( 10 );
                    }
                    Item f = list[n];
                    //Log.v( TAG,  " " + f.name );
                    f.need_thumb = false;

                    if( f.isThumbNail() )
                        continue; // already exist
                    String fn;
                    if( f.name.indexOf( '/' ) >= 0 )
                        fn = f.name;
                    else {
                        if( Utils.str( base_path ) )
                            fn = Utils.mbAddSl( base_path ) + f.name;
                        else {
                            if( f.origin instanceof File )
                                fn = ((File)f.origin).getAbsolutePath();
                            else
                                continue;
                        }
                    }
                    int fn_h = ( fn + " " + f.size ).hashCode();
                    SoftReference<Drawable> cached_soft = null;
                    synchronized( thumbnailCache ) {
                        cached_soft = thumbnailCache.get( fn_h );
                    }
                    if( cached_soft != null ) {
                        f.setThumbNail( cached_soft.get() );
                    }

                    String ext = Utils.getFileExt( fn );
                    if( ext == null )
                        continue;
                    if( ext.equals( ".apk" ) )
                        f.thumb_is_icon = true;
                    if( !f.isThumbNail() ) {
                        int ext_hash = ext.hashCode(), ht_sz = ext_h.length;
                        boolean not_img = true;
                        for( int j = 0; j < ht_sz; j++ ) {
                            if( ext_hash == ext_h[j] ) {
                                not_img = false;
                                break;
                            }
                        }
                        if( not_img ) {
                            f.no_thumb = true;
                            f.setThumbNail( null );
                            continue;
                        }
                        // Log.v( TAG, "Creating a thumbnail for " + n + ", " +
                        // fn );
                        if( createThumbnail( fn, f, ext_hash ) ) {
                            synchronized( thumbnailCache ) {
                                thumbnailCache.put( fn_h, new SoftReference<Drawable>( f.getThumbNail() ) );
                            }
                        } else {
                            succeeded = false;
                            if( fails_count++ > 10 ) {
                                Log.e( TAG, "To many fails, give up" );
                                return;
                            }
                        }
                    }
                    need_update = true;
                    if( f.isThumbNail() && ( processed++ > 3 || ( proc_visible && proc_invisible ) ) ) {
                        // Log.v( TAG, "Time to refresh!" );
                        Message msg = thread_handler.obtainMessage( NOTIFY_THUMBNAIL_CHANGED );
                        msg.sendToTarget();
                        yield();
                        proc_visible = false;
                        need_update = false;
                        processed = 0;
                    }
                }
                if( need_update ) {
                    Message msg = thread_handler.obtainMessage( NOTIFY_THUMBNAIL_CHANGED );
                    msg.sendToTarget();
                }
                if( succeeded )
                    break;
            }
        } catch( Exception e ) {
            // Log.e( TAG, "ThumbnailsThread.run()", e );
        }
    }

    private final boolean createThumbnail( String fn, Item f, int h ) {
        final String func_name = "createThubnail()";
        try {
            if( h == apk_h )
                return getApkIcon( fn, f );
            // let's try to take it from the mediastore
            try {
                final String[] th_proj = new String[] { 
                        BaseColumns._ID,    // 0
                        Thumbnails.WIDTH,   // 1
                        Thumbnails.HEIGHT,  // 2
                        Thumbnails.IMAGE_ID // 3
                };
                Cursor cursor = null;
                if( f.origin instanceof Uri ) {
                    cursor = Thumbnails.queryMiniThumbnails( cr, (Uri)f.origin, Thumbnails.MINI_KIND, th_proj );
                } else {
                    String[] proj = { BaseColumns._ID };
                    String where = Media.DATA + " = '" + fn + "'";
                    cursor = cr.query( Media.EXTERNAL_CONTENT_URI, proj, where, null, null );
                    if( cursor != null && cursor.getCount() > 0 ) {
                        cursor.moveToPosition( 0 );
                        long id = cursor.getLong( 0 );
                        cursor.close();
                        cursor = Thumbnails.queryMiniThumbnail( cr, id, Thumbnails.MINI_KIND, th_proj );
                    }                    
                }
                if( cursor != null && cursor.getCount() > 0 ) {
                    cursor.moveToPosition( 0 );
                    Uri tcu = ContentUris.withAppendedId( Thumbnails.EXTERNAL_CONTENT_URI, cursor.getLong( 0 ) );
                    int tw = cursor.getInt( 1 );
                    int th = cursor.getInt( 2 );
                    // Log.v( TAG, "th id: " + cursor.getLong(0) +
                    // ", org id: " + cursor.getLong(3) + ", w: " + tw +
                    // ", h: " + th );
                    cursor.close();
                    InputStream in = cr.openInputStream( tcu );

                    if( tw > 0 && th > 0 ) {
                        int greatest = Math.max( tw, th );
                        int factor = greatest / thumb_sz;
                        int b;
                        for( b = 0x8000000; b > 0; b >>= 1 )
                            if( b <= factor )
                                break;
                        options.inSampleSize = b;
                    } else
                        options.inSampleSize = 4;
                    options.inJustDecodeBounds = false;
                    options.inTempStorage = buf;
                    Bitmap bitmap = BitmapFactory.decodeStream( in, null, options );
                    if( bitmap != null ) {
                        BitmapDrawable drawable = new BitmapDrawable( res, bitmap );
                        f.setThumbNail( drawable );
                        in.close();
                        // Log.v( TAG, "a thumbnail was stolen from " + tcu
                        // );
                        return true;
                    }
                }
            } catch( Exception e ) {
                // Log.e( TAG, fn, e );
            }
            options.inSampleSize = 1;
            options.inJustDecodeBounds = true;
            options.outWidth = 0;
            options.outHeight = 0;
            options.inTempStorage = buf;

            FileInputStream fis = new FileInputStream( fn );
            BitmapFactory.decodeStream( fis, null, options );
            // BitmapFactory.decodeFile( fn, options );
            if( options.outWidth > 0 && options.outHeight > 0 ) {
                f.attr = "" + options.outWidth + "x" + options.outHeight;
                int greatest = Math.max( options.outWidth, options.outHeight );
                int factor = greatest / thumb_sz;
                int b;
                for( b = 0x8000000; b > 0; b >>= 1 )
                    if( b < factor )
                        break;
                options.inSampleSize = b;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeFile( fn, options );
                if( bitmap != null ) {
                    BitmapDrawable drawable = new BitmapDrawable( res, bitmap );
                    // drawable.setGravity( Gravity.CENTER );
                    // drawable.setBounds( 0, 0, 60, 60 );
                    f.setThumbNail( drawable );
                    fis.close();
                    //Log.v( TAG, fn + " - OK" );
                    return true;
                }
            } else
                Log.w( TAG, "failed to get an image bounds" );
            fis.close();
            Log.e( TAG, func_name + " failed for " + fn );
        } catch( RuntimeException rte ) {
            Log.e( TAG, func_name, rte );
        } catch( FileNotFoundException fne ) {
            Log.e( TAG, func_name, fne );
        } catch( IOException ioe ) {
            Log.e( TAG, func_name, ioe );
        } catch( Error err ) {
            Log.e( TAG, func_name, err );
        }
        return false;
    }

    private final boolean getApkIcon( String fn, Item f ) {
        try {
            f.thumb_is_icon = true;
            PackageManager pm = owner.ctx.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo( fn, 0 );
            Drawable icon = null;
            if( info != null ) {
                try {
                    icon = pm.getApplicationIcon( info.packageName );
                } catch( Exception e ) {
                }
                if( icon != null ) {
                    f.setIcon( icon );
                    return true;
                }
            }
            try {
                String filePath = base_path + f.name;
                PackageInfo packageInfo = owner.ctx.getPackageManager().getPackageArchiveInfo( filePath,
                        PackageManager.GET_ACTIVITIES );
                if( packageInfo != null ) {
                    ApplicationInfo appInfo = packageInfo.applicationInfo;
                    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ) {
                        appInfo.sourceDir = filePath;
                        appInfo.publicSourceDir = filePath;
                    }
                    icon = appInfo.loadIcon( owner.ctx.getPackageManager() );
                    // bmpIcon = ((BitmapDrawable) icon).getBitmap();
                }
            } catch( Exception e ) {
                Log.e( TAG, "File: " + fn, e );
            }
            if( icon != null ) {
                f.setIcon( icon );
                return true;
            }
            MnfUtils mnfu = new MnfUtils( fn );
            icon = mnfu.extractIcon();
            if( icon != null ) {
                f.setIcon( icon );
                return true;
            }
            f.setIcon( pm.getDefaultActivityIcon() );
            return true;
        } catch( Exception e ) {
        }
        return false;
        
    }
}