package com.ghostsq.commander;

import com.ghostsq.commander.utils.Credentials;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

public class ServerForm extends Activity implements View.OnClickListener {
    private static final String TAG = "ServerForm";
    private enum Type { 
        FTP(   "ftp", "FTP" ), 
        SFTP( "sftp", "SSH FTP" ), 
        SMB(   "smb", "Windows PC" ), 
        UNKNOWN( "?", "" );
        
        public String schema, title;
        
        private Type( String schema_, String title_ ) {
            schema = schema_;
            title  = title_;
        }
        public static Type getInstance( String s ) {
            if( s.equals(  FTP.schema ) ) return  FTP; 
            if( s.equals( SFTP.schema ) ) return SFTP; 
            if( s.equals(  SMB.schema ) ) return  SMB; 
            return UNKNOWN;
        }
    };
    private Type type;
    
    private EditText server_edit;
    private EditText path_edit;
    private EditText domain_edit;
    private EditText name_edit;
    private CheckBox active_ftp_cb;
    private Spinner  encoding_spin;
    private View     domain_block;
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        try {
            super.onCreate( savedInstanceState );
            type = Type.getInstance( getIntent().getStringExtra( "schema" ) );            

            // "Leave fields blank to see a list of nodes"
            
            setTitle( getString( R.string.connect ) + " " + type.title );
            requestWindowFeature( Window.FEATURE_LEFT_ICON );
            setContentView( R.layout.server );
            getWindow().setLayout(LayoutParams.FILL_PARENT /* width */, LayoutParams.WRAP_CONTENT /* height */);            
            getWindow().setFeatureDrawableResource( Window.FEATURE_LEFT_ICON, 
             type == Type.SMB ? R.drawable.smb : R.drawable.server );
            
            server_edit = (EditText)findViewById( R.id.server_edit );
            path_edit = (EditText)findViewById( R.id.path_edit );
            domain_edit = (EditText)findViewById( R.id.domain_edit );
            domain_block = findViewById( R.id.domain_block );
            name_edit = (EditText)findViewById( R.id.username_edit );
            active_ftp_cb = (CheckBox)findViewById( R.id.active );
            encoding_spin = (Spinner)findViewById( R.id.encoding );
            View encoding_block = findViewById( R.id.encoding_block );
            if( type == Type.FTP ) {
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource( this,
                        R.array.encoding, android.R.layout.simple_spinner_item );
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                encoding_spin.setAdapter( adapter );            
            }
            Button connect_button = (Button)findViewById( R.id.connect );
            connect_button.setOnClickListener( this );
            Button browse_button = (Button)findViewById( R.id.browse );
            browse_button.setOnClickListener( this );
            Button cancel_button = (Button)findViewById( R.id.cancel );
            cancel_button.setOnClickListener( this );

           encoding_block.setVisibility( type == Type.FTP ? View.VISIBLE : View.GONE );
            active_ftp_cb.setVisibility( type == Type.FTP ? View.VISIBLE : View.GONE );
            browse_button.setVisibility( type == Type.SMB ? View.VISIBLE : View.GONE );
             domain_block.setVisibility( type == Type.SMB ? View.VISIBLE : View.GONE );
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
            encoding_spin.setSelection( prefs.getInt( "ENCODING", 0 ) );            
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
            editor.putInt( "ENCODING", encoding_spin.getSelectedItemPosition() );            
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
            if( v.getId() == R.id.browse ) {
                if( type == Type.SMB )
                    setResult( RESULT_OK, new Intent( Commander.NAVIGATE_ACTION, Uri.parse( "smb://" ) ) );
            }
            else if( v.getId() == R.id.connect ) {
                EditText pass_edit = (EditText)findViewById( R.id.password_edit );
                String user = name_edit.getText().toString().trim();
                String pass = pass_edit.getText().toString().trim();
                Credentials crd = null;
                if( user.length() > 0 ) {
                    if( type == Type.SMB ) {
                        EditText domain_edit = (EditText)findViewById( R.id.domain_edit );
                        String domain = domain_edit.getText().toString().trim();
                        if( domain.length() > 0 )
                            user = domain + ";" + user;
                    }
                    crd = new Credentials( user, pass ); 
                }
                Uri.Builder uri_b = new Uri.Builder()
                    .scheme( type.schema )
                    .encodedAuthority( Utils.encodeToAuthority( server_edit.getText().toString().trim() ) )
                    .path( path_edit.getText().toString().trim() );
                if( type == Type.FTP ) {
                    if( active_ftp_cb.isChecked() )
                        uri_b.appendQueryParameter( "a", "true" );
                    Object esio = encoding_spin.getSelectedItem();
                    if( esio instanceof String ) {
                        String enc_s = (String)esio; 
                        if( Utils.str( enc_s ) && !"Default".equals( enc_s ) ) {
                            enc_s = enc_s.substring( 0, enc_s.indexOf( "\n" ) );
                            uri_b.appendQueryParameter( "e", enc_s );
                        }
                    }
                }                
                Intent in = new Intent( Commander.NAVIGATE_ACTION, uri_b.build() );
                if( crd != null )
                    in.putExtra( Credentials.KEY, crd );
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
