package com.ghostsq.commander.utils;

import java.io.File;

import android.view.View;

public class ForwardCompat
{
    public static void disableOverScroll( View view )
    {
        view.setOverScrollMode( View.OVER_SCROLL_NEVER );
    }
    public static void setFullPermissions( File file )
    {
        file.setWritable( true, false );
        file.setReadable( true, false );
    }
}
