package com.ghostsq.commander;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class PanelsView extends LinearLayout {
    private boolean sxs = false;
    private int current = 0;
    
    public PanelsView( Context context ) {
        super( context );
    }

    public  PanelsView( Context context, AttributeSet attrs ) {
        super( context, attrs );
    }
    
    public void setMode( boolean sxs_, int which_current ) {
        sxs = sxs_;
        View lv = findViewById( R.id.left_list );
        View rv = findViewById( R.id.right_list );
        View dv = findViewById( R.id.divider );
        if( sxs ) {
            lv.setVisibility( VISIBLE );
            dv.setVisibility( VISIBLE );
            rv.setVisibility( VISIBLE );
        } else {
            dv.setVisibility( GONE );
            if( which_current == 0 ) {
                lv.setVisibility( VISIBLE );
                rv.setVisibility( GONE );
            } else {
                lv.setVisibility( GONE );
                rv.setVisibility( VISIBLE );
            }
        }
        requestLayout();
        current = which_current;
    }

    public final void setPanelCurrent(  ) {
    }
/*
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure( sxs ? widthMeasureSpec : widthMeasureSpec * 2, heightMeasureSpec );
    }
*/    
    @Override
    protected void onLayout( boolean changed, int left, int top, int right, int bottom ) {
        try {
            //if( !changed ) return;
            View lv = findViewById( R.id.left_list );
            View rv = findViewById( R.id.right_list );
            View dv = findViewById( R.id.divider );
            int w = right - left;
            int h = bottom - top;
            if( sxs ) {
                lv.layout(  left,         0, w/2-1, h );
                dv.layout(  left + w/2-1, 0, w/2,   h );
                rv.layout(  w/2,   0, w,     h );
            }
            else {
                if( current == 0 ) {
                    lv.layout( 0, 0, w, h );
                }
                else {
                    rv.layout( 0, 0, w, h );
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }

    }
}
