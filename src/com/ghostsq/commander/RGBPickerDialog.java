package com.ghostsq.commander;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;

public class RGBPickerDialog extends AlertDialog implements DialogInterface.OnClickListener,
                                                            SeekBar.OnSeekBarChangeListener {
    private final static String TAG = "RGB";
    public interface ColorChangeListener {
        void colorChanged(int color);
    }

    private ColorChangeListener colorChangeListener;
    private int curColor;
    private View preview;

    RGBPickerDialog( Context context, ColorChangeListener listener, int color ) {
        super(context);
        colorChangeListener = listener;
        curColor = color;
        Context c = getContext();
        setTitle( c.getString( R.string.pick_color ) );
        LayoutInflater factory = LayoutInflater.from( c );
        setView( factory.inflate( R.layout.rgb, null ) );
        setButton( BUTTON_POSITIVE, c.getString( R.string.dialog_ok ), this);
        setButton( BUTTON_NEGATIVE, c.getString( R.string.dialog_cancel ), this );
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SeekBar r_seek = (SeekBar)findViewById( R.id.r_seek );
        SeekBar g_seek = (SeekBar)findViewById( R.id.g_seek );
        SeekBar b_seek = (SeekBar)findViewById( R.id.b_seek );
        if( r_seek != null ) {
            r_seek.setOnSeekBarChangeListener( this );
            r_seek.setProgress( Color.red( curColor ) );
        }
        if( g_seek != null ) {
            g_seek.setOnSeekBarChangeListener( this );
            g_seek.setProgress( Color.green( curColor ) );
        }
        if( b_seek != null ) {
            b_seek.setOnSeekBarChangeListener( this );
            b_seek.setProgress( Color.blue( curColor ) );
        }
        preview = findViewById(R.id.preview);
        if( preview != null )
            preview.setBackgroundColor( curColor );
    }
    
    // SeekBar.OnSeekBarChangeListener methods
    @Override 
    public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
        if( !fromUser ) return;
        int id = seekBar.getId();
        switch( id ) {
        case R.id.r_seek: 
            curColor = Color.rgb( progress, Color.green( curColor ), Color.blue( curColor ) );
            break;
        case R.id.g_seek: 
            curColor = Color.rgb( Color.red( curColor ), progress, Color.blue( curColor ) );
            break;
        case R.id.b_seek: 
            curColor = Color.rgb( Color.red( curColor ), Color.green( curColor ), progress );
            break;
        }
        if( preview != null )
            preview.setBackgroundColor( curColor );
    }

    @Override
    public void onStartTrackingTouch( SeekBar seekBar ) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onStopTrackingTouch( SeekBar seekBar ) {
        // TODO Auto-generated method stub
        
    }
    
    @Override // DialogInterface.OnClickListener
    public void onClick( DialogInterface dialog, int which ) {
        if( which == BUTTON_POSITIVE && colorChangeListener != null )
            colorChangeListener.colorChanged( curColor );
        dismiss();
    }
}
