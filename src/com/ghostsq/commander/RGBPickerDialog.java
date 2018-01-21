package com.ghostsq.commander;

import com.ghostsq.commander.utils.Utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class RGBPickerDialog extends AlertDialog 
    implements DialogInterface.OnClickListener,
               OnCheckedChangeListener, 
               SeekBar.OnSeekBarChangeListener,
               android.view.View.OnKeyListener {

    public interface ResultSink {
        void colorChanged(int color);
    }

    private final static String TAG = "RGB";
    private ResultSink colorChangeSink;
    private int curColor, defColor;
    private CheckBox dccb;
    private View     sliders;
    private SeekBar  r_seek;
    private SeekBar  g_seek;
    private SeekBar  b_seek;
    private EditText edit_rgb;
    private View preview;

    RGBPickerDialog( Context context, ResultSink sink, int color, int def_color, String title ) {
        super(context);
        colorChangeSink = sink;
        curColor = color;
        defColor = def_color;
        Context c = getContext();
        String dlg_title = c.getString( R.string.pick_color ); 
        if( Utils.str( title ) ) 
            dlg_title += ": " + title;
        setTitle( dlg_title );
        LayoutInflater factory = LayoutInflater.from( c );
        setView( factory.inflate( R.layout.rgb, null ) );
        setButton( BUTTON_POSITIVE, c.getString( R.string.dialog_ok ), this);
        setButton( BUTTON_NEGATIVE, c.getString( R.string.dialog_cancel ), this );
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sliders = findViewById( R.id.rgb_sliders ); 
        r_seek = (SeekBar)findViewById( R.id.r_seek );
        r_seek = (SeekBar)findViewById( R.id.r_seek );
        g_seek = (SeekBar)findViewById( R.id.g_seek );
        b_seek = (SeekBar)findViewById( R.id.b_seek );
        edit_rgb = (EditText)findViewById( R.id.edit_rgb );
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
        edit_rgb.setOnKeyListener( this );
        
        setEditorValue();
        preview = findViewById(R.id.preview);
        if( preview != null )
            preview.setBackgroundColor( curColor );

        if( defColor != 0 ) {
            View dcv = findViewById( R.id.default_color );
            if( dcv != null ) {
                dcv.setVisibility( View.VISIBLE );
                dccb = (CheckBox)dcv;
                dccb.setOnCheckedChangeListener( this );
                dccb.setChecked( curColor == 0 );
            }
        }
    }
    
    @Override
    public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {
        if( isChecked ) {
            curColor = 0;
            sliders.setVisibility( View.GONE );
        } else {
            if( curColor == 0 ) {
                curColor = defColor;
                setSeekBars();
            }
            preview.setBackgroundColor( curColor );
            sliders.setVisibility( View.VISIBLE );
        }
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
        setEditorValue();
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
        if( which == BUTTON_POSITIVE && colorChangeSink != null )
            colorChangeSink.colorChanged( curColor );
        dismiss();
    }

    // --- android.view.View.OnKeyListener ---
    
    @Override
    public boolean onKey( View v, int keyCode, KeyEvent event ) {
        if( edit_rgb == null ) return false;
        String s = edit_rgb.getText().toString();
        try {
            curColor = Color.parseColor( s );
            setSeekBars();
            preview.setBackgroundColor( curColor );
        } catch( IllegalArgumentException e ) {
        }
        return false;
    }

    void setSeekBars() {
        r_seek.setProgress( Color.red( curColor ) );
        g_seek.setProgress( Color.green( curColor ) );
        b_seek.setProgress( Color.blue( curColor ) );
    }
  
    void setEditorValue() {
        edit_rgb.setText( "#" + String.format( "%02X%02X%02X", Color.red( curColor ), Color.green( curColor ), Color.blue( curColor ) ) );
    }

}
