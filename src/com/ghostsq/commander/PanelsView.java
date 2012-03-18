package com.ghostsq.commander;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

public class PanelsView extends LinearLayout {
    private boolean sxs = false;
    private int current = 0;
    private WindowManager wm;
    private int           panel_width;
    private View          lv,rv, dv;
    
    public PanelsView( Context context ) {
        super( context );
    }

    public  PanelsView( Context context, AttributeSet attrs ) {
        super( context, attrs );
    }

    public void init( WindowManager wm_ ) {
        wm = wm_;
        lv = findViewById( R.id.left_list );
        rv = findViewById( R.id.right_list );
        dv = findViewById( R.id.divider );
    }
    
    public void setMode( boolean sxs_ ) {
        sxs = sxs_;
        requestLayout();
    }

    public final void setPanelCurrent(  ) {
    }

    @Override
    protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
        panel_width = wm.getDefaultDisplay().getWidth();
        if( sxs ) {
            panel_width /= 2;
            panel_width -= 1;
        } else
            panel_width -= 5;
        int w_spec = MeasureSpec.makeMeasureSpec( panel_width, MeasureSpec.EXACTLY );
        lv.measure( w_spec, heightMeasureSpec );
        dv.measure( MeasureSpec.makeMeasureSpec( 1, MeasureSpec.EXACTLY ), heightMeasureSpec );
        rv.measure( w_spec, heightMeasureSpec );
        setMeasuredDimension( resolveSize( panel_width * 2 + 1, widthMeasureSpec ),
                              resolveSize( getSuggestedMinimumHeight(), heightMeasureSpec));
    }
    
    @Override
    protected void onLayout( boolean changed, int l, int t, int r, int b ) {
        try {
            //Log.v( "PanelsView", "l:" + l + " t:" + t + " r:" + r + " b:" + b + " ch:" + changed );
            //Log.v( "PanelsView", "rv mw:" + rv.getMeasuredWidth() );
            lv.layout(  l, t, panel_width, b );
            dv.layout(  l + panel_width, t, l + panel_width + 1, b );
            rv.layout(  l + panel_width + 1, t, r, b );
        } catch( Exception e ) {
            e.printStackTrace();
        } catch( Error e ) {
            e.printStackTrace();
        }
    }
}
