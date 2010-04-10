package com.ghostsq.commander;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
//import android.view.MenuInflater; //to do: add Save as, Save and close items
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

public class Editor extends Activity {
	final static int MENU_SAVE = 1, WRAP_TEXT = 2;
	final static String URI = "URIfileForEdit";
	private boolean horScroll = true;
	private String path;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.editor);
        Uri file_to_edit_URI = getIntent().getData();
        if( file_to_edit_URI != null ) {
        	path = file_to_edit_URI.getEncodedPath();
        	if( !Load() )
        		finish();
        }
        else
        	showMessage( getString( R.string.nothing_to_open ) );
    }
    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        menu.add( Menu.NONE, MENU_SAVE, Menu.NONE, getString( R.string.save ) ).setIcon(android.R.drawable.ic_menu_save);
        menu.add( Menu.NONE, WRAP_TEXT, Menu.NONE, getString( R.string.wrap ) );
	    return true;
    }
    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        switch( item.getItemId() ) {
        case MENU_SAVE:
        	Save();
            return true;
        case WRAP_TEXT: 
            try {
                EditText te = (EditText)findViewById( R.id.editor );
                horScroll = horScroll ? false : true;
                te.setHorizontallyScrolling( horScroll ); 
            }
            catch( Exception e ) {
                System.err.println("Exception: " + e );
            } 
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private final void showMessage( String s ) {
    	Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
    private final boolean Load()
    {
         
        BufferedReader reader = null;
        try {
            EditText te = (EditText)findViewById( R.id.editor );
            
            File file = new File( path );
            if( !file.exists() ) {
                showMessage( getString( R.string.no_such_file, path ) );
                return false;
            }
            if( file.length() > 1000000 ) {
                showMessage( getString( R.string.too_big_file, path ) );
                return false;
            }
            
            reader = new BufferedReader(new FileReader(file));
            String text = null, sep = System.getProperty("line.separator");
            boolean binary = false;
            // repeat until all lines is read
            while( ( text = reader.readLine() ) != null ) {
            	if( text.indexOf( 0 ) >= 0 )
            		binary = true;
                te.append(text);
                te.append(sep);
            }
            if( binary )
            	showMessage( getString( R.string.binary_file, path ) );
            return true;
        } catch( Exception e ) {
        	showMessage( getString( R.string.cant_open ) + "\n" + e.getMessage() );
        } 
        finally {
            try {
                if(reader != null) {
                    reader.close();
                }
            } catch( IOException e ) {
            	showMessage( "IOException: " + e );
            }
        }
        return false;
    }
    private final void Save()
    {
    	FileWriter writer = null;
    	try {
            EditText te = (EditText)findViewById( R.id.editor );
            CharSequence text = te.getText();
    		writer = new FileWriter( path );
    		writer.write( text.toString() );	// to do: find a more optimal method to write data
        } catch( Exception e ) {
        	showMessage( getString( R.string.cant_save ) + "\n" + e.getMessage() );
        }finally {
            try {
                if( writer != null)
                	writer.close();
            } catch( IOException e ) {
            	showMessage( "IOException: " + e );
            }
        }    	
    }
}
