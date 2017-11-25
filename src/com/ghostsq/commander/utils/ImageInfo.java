package com.ghostsq.commander.utils;

import java.io.IOException;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;

public class ImageInfo {
    public static float getImageFileOrientationDegree( String path ) { 
        try {
            ExifInterface exif = new ExifInterface( path );
            int ov = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED );
            float degrees = 0;
            switch( ov ) {
            case ExifInterface.ORIENTATION_ROTATE_90:  degrees =  90; break;
            case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
            }
            return degrees;
        } catch( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0;
    }

    public static String getImageFileInfoHTML( String path ) { 
        try {
            StringBuilder sb = new StringBuilder( 100 );
            ExifInterface exif = new ExifInterface( path );
            int ov = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED );
            String os = null;
            switch( ov ) {
            case ExifInterface.ORIENTATION_NORMAL:          os = "Normal";      break;
            case ExifInterface.ORIENTATION_ROTATE_90:       os =  "90°";        break;
            case ExifInterface.ORIENTATION_ROTATE_270:      os = "270°";        break;
            case ExifInterface.ORIENTATION_ROTATE_180:      os = "180°";        break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: os = "Hor.flip";    break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:   os = "Ver.flip";    break;
            case ExifInterface.ORIENTATION_TRANSPOSE:       os = "Transposed";  break;
            case ExifInterface.ORIENTATION_TRANSVERSE:      os = "Transversed"; break;
            }
            final int INV = -1;
            if( os != null ) sb.append( "<b>Orientation:</b> " ).append( os );
            int wi = exif.getAttributeInt( ExifInterface.TAG_IMAGE_WIDTH, INV );
            if( wi > 0 ) sb.append( "<br/><b>Width:</b> " ).append( wi );
            int li = exif.getAttributeInt( ExifInterface.TAG_IMAGE_LENGTH, INV );
            if( li > 0 ) sb.append( "<br/><b>Height:</b> " ).append( li );
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
                ForwardCompat.getImageFileExtraInfo( exif, sb );
            int fe = exif.getAttributeInt( ExifInterface.TAG_FLASH, INV );
            if( fe > 0 ) {
                sb.append( "<br/><b>Flash:</b> " ).append( fe );
                int wb = exif.getAttributeInt( ExifInterface.TAG_WHITE_BALANCE, INV );
                if( wb != INV ) {
                    String ws = null; 
                    if( wb == ExifInterface.WHITEBALANCE_AUTO )   ws = "Auto";
                    if( wb == ExifInterface.WHITEBALANCE_MANUAL ) ws = "Manual";
                    if( ws != null ) sb.append( "<br/><b>WB:</b> " ).append( ws );
                }
            }
            String ma = exif.getAttribute( ExifInterface.TAG_MAKE );
            if( ma != null ) sb.append( "<br/><b>Make:</b> " ).append( ma );
            String mo = exif.getAttribute( ExifInterface.TAG_MODEL );
            if( mo != null ) sb.append( "<br/><b>Model:</b> " ).append( mo );
            String dt = exif.getAttribute( ExifInterface.TAG_DATETIME );
            if( dt != null ) sb.append( "<br/><b>Date:</b> " ).append( dt );
            return sb.toString();
        } catch( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static Bitmap createVideoThumbnail( String path ) {
        return ThumbnailUtils.createVideoThumbnail( path, MediaStore.Images.Thumbnails.MINI_KIND );        
    }

    public static Bitmap getVideoThumbnail( ContentResolver cr, long id, int sample_size ) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample_size;
        return Video.Thumbnails.getThumbnail( cr, id, Video.Thumbnails.MINI_KIND, options );        
    }

}
