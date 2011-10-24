package com.ghostsq.commander.favorites;

import java.lang.String;
import java.util.NoSuchElementException;
import java.util.ArrayList;

import com.ghostsq.commander.FileCommander;
import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Utils;

import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

public class Shortcuts extends BaseAdapter implements Filterable, OnKeyListener, OnClickListener, TextWatcher {
    private final static String TAG = "Shortcuts";
    private final static String old_sep = ",", sep = ";";
	private FileCommander c;
	private Panels        p;
	private int  toChange = -1;
	private View goPanel;
	private ArrayList<Favorite> shortcutsList;
    private float density = 1;
	
	public Shortcuts( FileCommander c_, Panels p_, ArrayList<Favorite> shortcuts_list ) {
		super();
		c = c_;
		p = p_;
		shortcutsList = shortcuts_list;
		goPanel = c.findViewById( R.id.uri_edit_panel );
		
        try {
            AutoCompleteTextView textView = (AutoCompleteTextView)goPanel.findViewById( R.id.uri_edit );
            if( textView != null ) {
	            textView.setAdapter( this );
	            textView.setOnKeyListener( this );
	            textView.addTextChangedListener( this );
            }
            Button go = (Button)goPanel.findViewById( R.id.go_button );
            if( go != null )
            	go.setOnClickListener( this );
            View star = goPanel.findViewById( R.id.star );
            if( star != null )
            	star.setOnClickListener( this );
            density = c.getContext().getResources().getDisplayMetrics().density;
        } catch( Exception e ) {
			c.showMessage( "Exception on setup history dropdown: " + e );
		}
	}

	@Override
	 public Filter getFilter() {
	  Filter nameFilter = new Filter() {
		   @Override
		   public String convertResultToString( Object resultValue ) {
		      return resultValue.toString();
		   }
	
		   @Override
		   protected FilterResults performFiltering(CharSequence constraint) {
			    FilterResults filterResults = new FilterResults();
				if(constraint != null) {
				   filterResults.values = new Object();
				   filterResults.count = 1;
				}
			    return filterResults;
		   }
		   @Override
		   protected void publishResults( CharSequence constraint, FilterResults results ) {
		    if( results != null && results.count > 0 )
		    	notifyDataSetChanged();
		   }
	   };
	   return nameFilter;
	 }

	@Override
	public int getCount() {
		return shortcutsList.size();
	}

	@Override
	public Object getItem( int position ) {
		return shortcutsList.get( position ).getUriString( false );
	}

	@Override
	public long getItemId( int position ) {
		return position;
	}

	@Override
	public View getView( int position, View convertView, ViewGroup parent ) {
		TextView tv = convertView != null ? (TextView)convertView : new TextView( c );
		int vp = p.fingerFriendly ? (int)( 10 * density ) : 4;
		tv.setPadding( 4, vp, 4, vp );
		String screened = shortcutsList.get( position ).getUriString( true );
		tv.setText( screened == null ? "" : screened );
		tv.setTextColor( 0xFF000000 );
		return tv;
	}

	// --- inner functions ---
	
    public final void openGoPanel( int which, String uri_s ) {
		try {
			goPanel.setVisibility( View.VISIBLE );
			toChange = which;
			AutoCompleteTextView edit = (AutoCompleteTextView)c.findViewById( R.id.uri_edit );
			if( edit != null ) {
				edit.setText( uri_s );
				edit.showDropDown();
				edit.requestFocus();
			}
			CheckBox star = (CheckBox)c.findViewById( R.id.star );
            if( star != null )
            	star.setChecked( find( uri_s ) >= 0 );
		}
		catch( Exception e ) {
			c.showMessage( "Error: " + e );
		}
    }
    public final void closeGoPanel() {
		View go_panel = c.findViewById( R.id.uri_edit_panel );
		if( go_panel != null )
			go_panel.setVisibility( View.GONE );
    }
    public final void applyGoPanel() {
    	closeGoPanel();
		TextView edit = (TextView)goPanel.findViewById( R.id.uri_edit );
		String new_dir = edit.getText().toString().trim();
		
		if( toChange >= 0 && new_dir.length() > 0 ) {
//		    new_dir = searchForNotScreenedURI( new_dir );
			if( toChange != p.getCurrent() )
				p.togglePanels( false );
			p.Navigate( toChange, Uri.parse( new_dir ), null );
		}
		toChange = -1;
		p.focus();
    }    
//    private final String searchForNotScreenedURI( String new_dir )
    
    
    public final void addToFavorites( String uri_str ) {
    	removeFromFavorites( uri_str );
    	shortcutsList.add( new Favorite( uri_str, null ) );
		notifyDataSetChanged();
    }
    public final void removeFromFavorites( String uri_s ) {
		int pos = find( uri_s );
		if( pos >= 0 )
			shortcutsList.remove( pos );
		notifyDataSetChanged();
    }
    public final int find( String uri_s ) {
		try {
		    if( uri_s != null ) {
        		String strip_uri = uri_s.trim();
        		int slen = strip_uri.length();
        		if( slen > 0 ) { 
            		if( strip_uri.charAt( slen -1 ) != '/' )
            			strip_uri += "/";
        	        for( int i = 0; i < shortcutsList.size(); i++ ) {
        	        	String item = shortcutsList.get( i ).getUriString( false );
        	        	if( item != null ) {
        	        		String strip_item = item.trim();
        	        		if( strip_item.length() == 0 || strip_item.charAt( strip_item.length()-1 ) != '/' )
        	        			strip_item += "/";
        	        		if( strip_item.compareTo( strip_uri ) == 0 )
        	        			return i;
        	        	}
        	        }
        		}
		    }
		} catch( Exception e ) {
		    Log.e( TAG, "", e );
		}
		return -1;
    }

   
    public final String getAsString() {
        int sz = shortcutsList.size();
        String[] a = new String[sz]; 
        for( int i = 0; i < sz; i++ ) {
            String fav_str = shortcutsList.get( i ).toString();
            if( fav_str == null ) continue;
            a[i] = escape( fav_str );
        }
        String s = Utils.join( a, sep );
        //Log.v( TAG, "Joined favs: " + s );
        return s;
    }
    
    public final void setFromOldString( String stored ) {
        if( stored == null ) return;
        try {
            shortcutsList.clear();
            String use_sep = old_sep;
            String[] favs = stored.split( use_sep );
            for( int i = 0; i < favs.length; i++ ) {
                if( favs[i] != null && favs[i].length() > 0 )
                    shortcutsList.add( new Favorite( favs[i], null ) );
            }
        } catch( NoSuchElementException e ) {
            c.showError( "Error: " + e );
        }
        if( shortcutsList.isEmpty() )
            shortcutsList.add( new Favorite( "/sdcard", c.getContext().getString( R.string.default_uri_cmnt ) ) );
    }

    public final void setFromString( String stored ) {
        if( stored == null ) return;
        shortcutsList.clear();
        String use_sep = sep;
        String[] favs = stored.split( use_sep );
        try {
            for( int i = 0; i < favs.length; i++ ) {
                String stored_fav = unescape( favs[i] );
                //Log.v( TAG, "fav: " + stored_fav );
                shortcutsList.add( new Favorite( stored_fav ) );
            }
        } catch( NoSuchElementException e ) {
            c.showError( "Error: " + e );
        }
        if( shortcutsList.isEmpty() )
            shortcutsList.add( new Favorite( "home:", c.getContext().getString( R.string.default_uri_cmnt ) ) );
    }

    private String unescape( String s ) {
        return s.replace( "%3B", sep );
    }
    private String escape( String s ) {
        return s.replace( sep, "%3B" );
    }
    
	@Override
	public boolean onKey( View v, int keyCode, KeyEvent event ) {
	    int v_id = v.getId();
	    if( v_id == R.id.uri_edit ) {
	    	switch( keyCode ) {
			case KeyEvent.KEYCODE_BACK:
				closeGoPanel();
	            return true;
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER:
				try {
					AutoCompleteTextView actv = (AutoCompleteTextView)v;
					if( actv.getListSelection() == ListView.INVALID_POSITION ) { // !actv.isPopupShowing()
						applyGoPanel();
						return true;
					}
				} catch( ClassCastException e ) {
				}
				return false;
/*				
			case KeyEvent.KEYCODE_DPAD_DOWN:
				try {
					AutoCompleteTextView actv = (AutoCompleteTextView)v;
					actv.showDropDown();
				} catch( ClassCastException e ) {
				}
				return false;
*/
			case KeyEvent.KEYCODE_TAB:
				return true;
			}
	    }
		return false;
	}

	@Override
	public void onClick( View v ) {
		switch( v.getId() ) {
		case R.id.star: 
			try {
				if( toChange < 0 ) break;
				TextView edit = (TextView)goPanel.findViewById( R.id.uri_edit );
	            String uri_s = edit.getText().toString().trim();
	            CheckBox star_cb = (CheckBox)v;
				if( star_cb.isChecked() )
					addToFavorites( uri_s );
				else 
					removeFromFavorites( uri_s );
				star_cb.setChecked( find( uri_s ) >= 0 );
				AutoCompleteTextView actv = (AutoCompleteTextView)goPanel.findViewById( R.id.uri_edit );
				actv.showDropDown();
				actv.requestFocus();
			}
			catch( Exception e ) {
			}
			break;
		case R.id.go_button:
			    applyGoPanel();
			    break;
			}
	}

	// TextWatcher implementation
	
	@Override
	public void afterTextChanged( Editable s ) {
		try {
			TextView edit = (TextView)goPanel.findViewById( R.id.uri_edit );
			CheckBox star = (CheckBox)goPanel.findViewById( R.id.star );
			String   addr = edit.getText().toString().trim();
			star.setChecked( find( addr ) >= 0 );
		}
		catch( Exception e ) {
		}
	}
	@Override
	public void beforeTextChanged( CharSequence s, int start, int count, int after ) {
	}
	@Override
	public void onTextChanged( CharSequence s, int start, int before, int count ) {
	}        
}
