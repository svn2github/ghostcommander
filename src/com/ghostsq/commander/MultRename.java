package com.ghostsq.commander;

import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MultRename extends Activity implements View.OnClickListener, TextWatcher {
    private static final String TAG = "MultRename";
    private ArrayList<String> names;
    private EditText pattern, replace;
    private TextView preview;

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        try {
            SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
            Utils.setDialogTheme( this, shared_pref.getString( "color_themes", "d" ) );
            super.onCreate( savedInstanceState );
            setContentView( R.layout.mult_rename );
            Intent intent = getIntent();
            names = intent.getStringArrayListExtra( getPackageName() + ".TO_RENAME_LIST" );
            pattern = (EditText)findViewById( R.id.pattern );
            replace = (EditText)findViewById( R.id.replace_to );
            preview = (TextView)findViewById( R.id.preview );
            pattern.addTextChangedListener( this );
            replace.addTextChangedListener( this );
            Button connect_button = (Button)findViewById( R.id.ok );
            connect_button.setOnClickListener( this );
            Button cancel_button = (Button)findViewById( R.id.cancel );
            cancel_button.setOnClickListener( this );
        }
        catch( Exception e ) {
            Log.e( TAG, "onCreate() Exception: ", e );
        }       
    }
    
    @Override
    protected void onStart() {
        try {
            super.onStart();
            setPreview();
        }
        catch( Exception e ) {
            Log.e( TAG, "onStart() Exception: ", e );
        }
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
        }
        catch( Exception e ) {
            Log.e( TAG, "onPause() Exception: ", e );
        }
    }
        
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        try {
//            outState.putString( schema + "_SERV", server_edit.getText().toString() );            
            super.onSaveInstanceState(outState);
        }
        catch( Exception e ) {
            Log.e( TAG, "onSaveInstanceState() Exception: ", e );
        }
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        try {
//            server_edit.setText( savedInstanceState.getString(  schema + "_SERV" ) );            
            super.onRestoreInstanceState(savedInstanceState);
        }
        catch( Exception e ) {
            Log.e( TAG, "onRestoreInstanceState() Exception: ", e );
        }
    }

    private void setPreview() {
        if( names == null ) return;
        preview.setText( getPreview( pattern.getText().toString(), replace.getText().toString() ) );
    }

    private String getPreview( String pattern_str, String replace_to ) {
        StringBuilder sb = new StringBuilder();
        Pattern pattern = null; 
        try {
            pattern = Pattern.compile( pattern_str );
        } catch( PatternSyntaxException e ) {}
        for( int i = 0; i < names.size(); i++ ) {
            String name = names.get( i );
            sb.append( name );
            sb.append( "\t->\t" );
            String replaced = null;
            if( pattern != null ) {
                try {
                    replaced = pattern.matcher( name ).replaceAll( replace_to );
                } catch( Exception e ) {}
            }
            if( replaced == null )
                replaced = name.replace( pattern_str, replace_to );
            /*
            if( replaced != null ) {
                Date d = new Date();
                replaced = replaced.replace( "$#", String.valueOf( i ) );
                String[] ff = { 
                   "yyyy", "yy", "MMM", "MM", "M", "dd", "d", "a", "hh", "h", "HH", "H", "mm", "ss" 
                };
                for( String f : ff )
                    replaced = replaced.replace( "$(" + f + ")", DateFormat.format( f, d ) );
            }
            */
            sb.append( replaced );
            sb.append( "\n" );
        }
        return sb.toString();
    }

    // --- TextWatcher ---
    
    @Override
    public void beforeTextChanged( CharSequence s, int start, int count, int after ) {
    }

    @Override
    public void onTextChanged( CharSequence s, int start, int before, int count ) {
    }

    @Override
    public void afterTextChanged( Editable s ) {
        setPreview();
    }
    
    
    // --- View.OnClickListener ---
    
    @Override
    public void onClick( View v ) {
        try{
            if( v.getId() == R.id.ok ) {
                Intent in = new Intent( Commander.RENAME_ACTION );
                in.putExtra( "PATTERN", pattern.getText().toString() );
                in.putExtra( "REPLACE", replace.getText().toString() );
                setResult( RESULT_OK, in );
            }
            else
                setResult( RESULT_CANCELED );
            finish();
        }
        catch( Exception e ) {
            Log.e( TAG, "onClick() Exception: ", e );
        }       
    }
}
