package com.ghostsq.commander;

import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class ServerForm extends Activity implements View.OnClickListener {
    private static final String TAG = "ServerForm";
    private String schema;
    private enum Type { FTP, SMB, UNKNOWN };
    private Type type;
    private EditText server_edit;
    private EditText path_edit;
    private EditText domain_edit;
    private EditText name_edit;
    private CheckBox active_ftp_cb;
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        try {
            super.onCreate( savedInstanceState );
            
            schema = getIntent().getStringExtra( "schema" );
            if( schema.equals( "ftp" ) ) type = Type.FTP; else
            if( schema.equals( "smb" ) ) type = Type.SMB; else
                type = Type.UNKNOWN;
            
            setTitle( getString( R.string.connect ) + " " + ( type == Type.SMB ? "Windows PC" : schema ) );
            requestWindowFeature( Window.FEATURE_LEFT_ICON );
            setContentView( R.layout.server );
            getWindow().setLayout (LayoutParams.FILL_PARENT /* width */, LayoutParams.WRAP_CONTENT /* height */);
            
            getWindow().setFeatureDrawableResource( Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_dialer );
            
            server_edit = (EditText)findViewById( R.id.server_edit );
            path_edit = (EditText)findViewById( R.id.path_edit );
            domain_edit = (EditText)findViewById( R.id.domain_edit );
            name_edit = (EditText)findViewById( R.id.username_edit );
            active_ftp_cb = (CheckBox)findViewById( R.id.active );            

            if( type == Type.FTP ) {
                View domain_block = findViewById( R.id.domain_block );
                domain_block.setVisibility( View.GONE );
                active_ftp_cb.setVisibility( View.VISIBLE );
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
            server_edit.setText( prefs.getString( "SERV", "" ) );            
            path_edit.setText( prefs.getString( "PATH", "/" ) );            
            domain_edit.setText( prefs.getString( "DOMAIN", "" ) );            
            name_edit.setText( prefs.getString( "USER", "" ) );            
            active_ftp_cb.setChecked( prefs.getBoolean( "ACTIVE", false ) );            
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
            editor.putString( "SERV", server_edit.getText().toString() );            
            editor.putString( "PATH", path_edit.getText().toString() );            
            editor.putString( "DOMAIN", domain_edit.getText().toString() );            
            editor.putString( "USER", name_edit.getText().toString() );            
            editor.putBoolean( "ACTIVE", active_ftp_cb.isChecked() );            
            editor.commit();
        }
        catch( Exception e ) {
            Log.e( TAG, "onPause() Exception: ", e );
        }
    }
        
    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        try {
            outState.putString( "SERV", server_edit.getText().toString() );            
            outState.putString( "PATH", path_edit.getText().toString() );            
            outState.putString( "USER", name_edit.getText().toString() );            
            outState.putString( "DOMAIN", domain_edit.getText().toString() );            
            outState.putBoolean( "ACTIVE", active_ftp_cb.isChecked() );            
            super.onSaveInstanceState(outState);
        }
        catch( Exception e ) {
            Log.e( TAG, "onSaveInstanceState() Exception: ", e );
        }
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        try {
            server_edit.setText( savedInstanceState.getString( "SERV" ) );            
            path_edit.setText( savedInstanceState.getString( "PATH" ) );            
            name_edit.setText( savedInstanceState.getString( "USER" ) );            
            domain_edit.setText( savedInstanceState.getString( "DOMAIN" ) );            
            active_ftp_cb.setChecked( savedInstanceState.getBoolean( "ACTIVE", false ) );            
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
                EditText pass_edit = (EditText)findViewById( R.id.password_edit );
                String user = name_edit.getText().toString();
                String pass = pass_edit.getText().toString();
                String auth = "";
                
                if( user.length() > 0 ) {
                    if( type == Type.SMB ) {
                        EditText domain_edit = (EditText)findViewById( R.id.domain_edit );
                        String domain = domain_edit.getText().toString();
                        if( domain.length() > 0 )
                            auth += domain.trim() + ";";
                    }
                    auth += Uri.encode( user.trim() );
                    if( pass.length() > 0 )
                        auth += ":" + Uri.encode( pass.trim() );
                    auth += "@";
                }
                auth += Utils.encodeToAuthority( server_edit.getText().toString().trim() );
                Uri.Builder uri_b = new Uri.Builder().scheme( schema ).encodedAuthority( auth );
                uri_b.path( path_edit.getText().toString().trim() );
                if( type == Type.FTP && active_ftp_cb.isChecked() )
                    uri_b.appendQueryParameter( "a", "true" );
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
