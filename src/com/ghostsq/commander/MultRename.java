package com.ghostsq.commander;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Replacer;
import com.ghostsq.commander.utils.Utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

public class MultRename extends Activity implements View.OnClickListener, TextWatcher {
    private static final String TAG = "MultRename";
    private ArrayList<String> names;
    private AutoCompleteTextView pattern, replace;
    private TextView preview;
    private RadioButton preview_b;

    @Override
    public void onCreate( Bundle savedInstanceState ) {
        try {
            SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
            Utils.setDialogTheme( this, shared_pref.getString( "color_themes", "d" ) );
            super.onCreate( savedInstanceState );
            setContentView( R.layout.mult_rename );
            Intent intent = getIntent();
            names = intent.getStringArrayListExtra( getPackageName() + ".TO_RENAME_LIST" );
            pattern = (AutoCompleteTextView)findViewById( R.id.pattern );
            replace = (AutoCompleteTextView)findViewById( R.id.replace_to );
            ArrayAdapter<String> pattern_history_adapter= new ArrayAdapter<String>( this, android.R.layout.simple_list_item_1 );
            pattern.setAdapter( pattern_history_adapter );
            ArrayAdapter<String> replace_history_adapter= new ArrayAdapter<String>( this, android.R.layout.simple_list_item_1 );
            replace.setAdapter( replace_history_adapter );
            preview = (TextView)findViewById( R.id.preview );
            preview_b = (RadioButton)findViewById( R.id.radio_preview );
            pattern.addTextChangedListener( this );
            replace.addTextChangedListener( this );
            Button connect_button = (Button)findViewById( R.id.ok );
            connect_button.setOnClickListener( this );
            Button cancel_button = (Button)findViewById( R.id.cancel );
            cancel_button.setOnClickListener( this );

            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            pattern.setText( prefs.getString( "PATTERN", "(.+)" ) );
            replace.setText( prefs.getString( "REPLACE", "$1" ) );

            Set<String> hist_set = getHistory( prefs,"PATTERN_HIST" );
            for( String s : hist_set )
                pattern_history_adapter.add( s );
            hist_set = getHistory( prefs,"REPLACE_HIST" );
            for( String s : hist_set )
                replace_history_adapter.add( s );
        }
        catch( Exception e ) {
            Log.e( TAG, "onCreate() Exception: ", e );
        }
    }

    private Set<String> getHistory( SharedPreferences prefs, String key ) {
        Set<String> hist_set = ForwardCompat.getStringSet( prefs, key );
        if( hist_set == null ) {
            hist_set = new HashSet<String>();
            if( "PATTERN_HIST".equals( key ) ) {
                hist_set.add( "(.+)" );
                hist_set.add( ".+" );
                hist_set.add( "[^.]+" );
            } else
            if( "REPLACE_HIST".equals( key ) ) {
                hist_set.add( "$1" );
                hist_set.add( "$2" );
                hist_set.add( "$3" );
            }
        }
        hist_set.add( pattern.getText().toString() );
        return hist_set;
    }

    private void saveStrings() {
        try {
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            SharedPreferences.Editor editor = prefs.edit();

            Set<String> hist_set = ForwardCompat.getStringSet( prefs, "PATTERN_HIST" );
            if( hist_set == null )
                hist_set = new HashSet<String>();
            String pattern_s = pattern.getText().toString();
            hist_set.add( pattern_s );
            editor.putString( "PATTERN", pattern_s );
            ForwardCompat.putStringSet( editor, "PATTERN_HIST", hist_set );
            hist_set = ForwardCompat.getStringSet( prefs, "REPLACE_HIST" );
            if( hist_set == null )
                hist_set = new HashSet<String>();
            String replace_s = replace.getText().toString();
            hist_set.add( replace_s );
            editor.putString( "REPLACE", replace_s );
            ForwardCompat.putStringSet( editor, "REPLACE_HIST", hist_set );
            editor.commit();
        }
        catch( Exception e ) {
            Log.e( TAG, "onPause() Exception: ", e );
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

    private void setHelp() {
        if( preview_b.isChecked() ) return;
        preview.setText( getString( R.string.mult_rename_help ) );
    }

    private void setPreview() {
        if( !preview_b.isChecked() ) return;
        if( names == null ) return;
        preview.setText( getPreview( pattern.getText().toString(), replace.getText().toString() ) );
    }

    private String getPreview( String pattern_str, String replace_to ) {
        final StringBuilder sb = new StringBuilder();
        Replacer r = new Replacer() {
            protected int getNumberOfOriginalStrings() {
                return names.size();
            }
            protected String getOriginalString( int i ) {
                String name = names.get( i );
                sb.append( name );
                sb.append( "\t->\t" );
                return name;
            }
            protected void setReplacedString( int i, String replaced ) {
                sb.append( replaced );
                sb.append( "\n" );
            }
        };
        r.replace( pattern_str, replace_to );
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
                saveStrings();
            }
            else
                setResult( RESULT_CANCELED );
            finish();
        }
        catch( Exception e ) {
            Log.e( TAG, "onClick() Exception: ", e );
        }       
    }
    
    public void onRadioButtonClicked( View v ) {
        if( v.getId() == R.id.radio_preview )
            setPreview();
        else
            setHelp();
    }
    
}
