package com.ghostsq.commander;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class ServerForm extends Activity implements View.OnClickListener {
    private static final String TAG = "ServerForm";
    private String schema;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate( savedInstanceState );
            schema = getIntent().getStringExtra( "schema" );
            setContentView( R.layout.server );
            if( schema.equals( "ftp" ) ) {
                LinearLayout domain_block = (LinearLayout)findViewById( R.id.domain_block );
                domain_block.setVisibility( View.GONE );
            }
            
            Button connectButton = (Button)findViewById( R.id.connect );
            connectButton.setOnClickListener( this );
            Button cancelButton = (Button)findViewById( R.id.cancel );
            cancelButton.setOnClickListener( this );
        }
        catch( Exception e ) {
            Log.e( TAG, "onCreate() Exception: ", e );
        }       
    }
    
    @Override
    protected void onStart() {
        try {
            super.onStart();
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText domain_edit = (EditText)findViewById( R.id.domain_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            server_edit.setText( prefs.getString( "SERV", "" ) );            
            path_edit.setText( prefs.getString( "PATH", "/" ) );            
            domain_edit.setText( prefs.getString( "DOMAIN", "" ) );            
            name_edit.setText( prefs.getString( "USER", "" ) );            
        }
        catch( Exception e ) {
            Log.e( TAG, "onStart() Exception: ", e );
        }
        
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
            SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText domain_edit = (EditText)findViewById( R.id.domain_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            editor.putString( "SERV", server_edit.getText().toString() );            
            editor.putString( "PATH", path_edit.getText().toString() );            
            editor.putString( "DOMAIN", domain_edit.getText().toString() );            
            editor.putString( "USER", name_edit.getText().toString() );            
            editor.commit();
        }
        catch( Exception e ) {
            Log.e( TAG, "onPause() Exception: ", e );
        }
    }
        
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        try {
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            EditText domain_edit = (EditText)findViewById( R.id.domain_edit );
            outState.putString( "SERV", server_edit.getText().toString() );            
            outState.putString( "PATH", path_edit.getText().toString() );            
            outState.putString( "USER", name_edit.getText().toString() );            
            outState.putString( "DOMAIN", domain_edit.getText().toString() );            
            super.onSaveInstanceState(outState);
        }
        catch( Exception e ) {
            Log.e( TAG, "onSaveInstanceState() Exception: ", e );
        }
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        try {
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            EditText domain_edit = (EditText)findViewById( R.id.domain_edit );
            server_edit.setText( savedInstanceState.getString( "SERV" ) );            
            path_edit.setText( savedInstanceState.getString( "PATH" ) );            
            name_edit.setText( savedInstanceState.getString( "USER" ) );            
            domain_edit.setText( savedInstanceState.getString( "DOMAIN" ) );            
            super.onRestoreInstanceState(savedInstanceState);
        }
        catch( Exception e ) {
            Log.e( TAG, "onRestoreInstanceState() Exception: ", e );
        }
    }

    @Override
    public void onClick( View v ) {
        try{ 
            if( v.getId() == R.id.connect ) {
                EditText server_edit = (EditText)findViewById( R.id.server_edit );
                EditText path_edit = (EditText)findViewById( R.id.path_edit );
                EditText name_edit = (EditText)findViewById( R.id.username_edit );
                EditText pass_edit = (EditText)findViewById( R.id.password_edit );
                    
                String user = name_edit.getText().toString();
                String pass = pass_edit.getText().toString();
                String auth = "";
                
                if( user.length() > 0 ) {
                    if( schema.equals( "smb" ) ) {
                        EditText domain_edit = (EditText)findViewById( R.id.domain_edit );
                        String domain = domain_edit.getText().toString();
                        if( domain.length() > 0 )
                            auth += domain.trim() + ";";
                    }
                    auth += user.trim();
                    if( pass.length() > 0 )
                        auth += ":" + pass.trim();
                    auth += "@";
                }
                auth += server_edit.getText().toString().trim();
                Uri.Builder uri_b = new Uri.Builder().scheme( schema ).encodedAuthority( auth );
                uri_b.path( path_edit.getText().toString().trim() );
                setResult( RESULT_OK, (new Intent()).setAction( uri_b.build().toString() ) );
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
