package com.ghostsq.commander;

import com.ghostsq.commander.Panels.State;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class FTPform extends Activity implements View.OnClickListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.ftp);
            Button connectButton = (Button)findViewById( R.id.connect );
            connectButton.setOnClickListener( this );
            Button cancelButton = (Button)findViewById( R.id.cancel );
            cancelButton.setOnClickListener( this );
        }
        catch( Exception e ) {
            System.err.println("FTPform.onCreate() Exception: " + e );
        }       
    }
    
    @Override
    protected void onStart() {
        try {
            super.onStart();
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            server_edit.setText( prefs.getString( "SERV", "" ) );            
            path_edit.setText( prefs.getString( "PATH", "/" ) );            
            name_edit.setText( prefs.getString( "USER", "" ) );            
        }
        catch( Exception e ) {
            System.err.println("FTPform.onStart() exception: " + e);
        }
        
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
            SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            editor.putString( "SERV", server_edit.getText().toString() );            
            editor.putString( "PATH", path_edit.getText().toString() );            
            editor.putString( "USER", name_edit.getText().toString() );            
            editor.commit();
        }
        catch( Exception e ) {
            System.err.println("FTPform.onPause() exception: " + e);
        }
    }
        
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        try {
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            outState.putString( "SERV", server_edit.getText().toString() );            
            outState.putString( "PATH", path_edit.getText().toString() );            
            outState.putString( "USER", name_edit.getText().toString() );            
            super.onSaveInstanceState(outState);
        }
        catch( Exception e ) {
            System.err.println("FTPform.onSaveInstanceState() exception: " + e);
        }
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        try {
            EditText server_edit = (EditText)findViewById( R.id.server_edit );
            EditText path_edit = (EditText)findViewById( R.id.path_edit );
            EditText name_edit = (EditText)findViewById( R.id.username_edit );
            server_edit.setText( savedInstanceState.getString( "SERV" ) );            
            path_edit.setText( savedInstanceState.getString( "PATH" ) );            
            name_edit.setText( savedInstanceState.getString( "USER" ) );            
            super.onRestoreInstanceState(savedInstanceState);
        }
        catch( Exception e ) {
            System.err.println("FTPform.onRestoreInstanceState() exception: " + e);
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
                    auth += user;
                    if( pass.length() > 0 )
                        auth += ":" + pass;
                    auth += "@";
                }
                auth += server_edit.getText().toString();
                Uri.Builder uri_b = new Uri.Builder().scheme("ftp").encodedAuthority( auth );
                uri_b.path(path_edit.getText().toString());
                setResult( RESULT_OK, (new Intent()).setAction( uri_b.build().toString() ) );
            }
            else
                setResult( RESULT_CANCELED );
            finish();
        }
        catch( Exception e ) {
            System.err.println("FTPform.onClick() Exception: " + e );
        }       
    }
}
