package com.ghostsq.commander;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.ItemComparator;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListAdapter;
import android.widget.ListView;

public class FileComparer extends AsyncTask<CommanderAdapter, Integer, Void> {
    private final static String TAG = "FileComparer";
    private ListView lv0, lv1;
    private boolean compare_content, case_ignore;
    public FileComparer( ListView... lvs ) {
        lv0 = lvs[0]; 
        lv1 = lvs[1];
    }
    
    void setOptions( boolean compare_content, boolean case_ignore ) {
        this.compare_content = compare_content;
        this.case_ignore = case_ignore;
    }
    
    @Override
    protected Void doInBackground( CommanderAdapter... aa ) {
        try {
            ListAdapter la0 = (ListAdapter)aa[0]; 
            ListAdapter la1 = (ListAdapter)aa[1];
            boolean[] matched_cache1 = new boolean[la1.getCount()]; 
            for( int i0 = 1; i0 < la0.getCount(); i0++ ) {
                CommanderAdapter.Item item0 = (CommanderAdapter.Item)la0.getItem( i0 );
                boolean found = false;
                for( int i1 = 1; i1 < la1.getCount(); i1++ ) {
                    CommanderAdapter.Item item1 = (CommanderAdapter.Item)la1.getItem( i1 );
                    int cmp_opts = ItemComparator.CMP_NOT_DATE;
                    if( this.case_ignore ) cmp_opts |= ItemComparator.CMP_IGNORE_CASE;
                    if( ItemComparator.compare( item0, item1, cmp_opts ) == 0 ) {
                        found = !item0.dir && this.compare_content ? compareContent( aa[0], i0, aa[1], i1 ) : true;
                        if( found ) {
                            matched_cache1[i1] = true;
                            break;
                        }
                    }
                }
                if( !found )
                    publishProgress( i0, null ); 
            }
            for( int i1 = 1; i1 < la1.getCount(); i1++ ) {
                if( !matched_cache1[i1] )
                    publishProgress( null, i1 );
            }
        } catch( Throwable e ) {
            Log.e( TAG, "", e );
        }
        return null;
    }
    @Override
    protected void onProgressUpdate( Integer... poss ) {
        ListView lv = null;
        int pos;
        if( poss[0] != null ) {
            lv = lv0;
            pos = poss[0];
        } else 
        if( poss[1] != null ) {
            lv = lv1;
            pos = poss[1];
        } else {
            Log.e( TAG, "Unknown side" );
            return;
        }
        lv.setItemChecked( pos, true );
    }
    @Override
    protected void onPostExecute( Void v ) {
    }
 
    
    private boolean compareContent( CommanderAdapter ca0, int i0, CommanderAdapter ca1, int i1 ) {
        InputStream is0 = null;
        InputStream is1 = null;
        try {
            Uri u0 = ca0.getItemUri( i0 );
            if( u0 == null ) return false;
            Log.d( TAG, "u0 " + u0 );
            is0 = ca0.getContent( u0 );
            if( is0 == null ) return false;

            Uri u1 = ca1.getItemUri( i1 );
            if( u1 == null ) return false;
            Log.d( TAG, "u1 " + u1 );
            is1 = ca1.getContent( u1 );
            if( is1 == null ) return false;
            return compareContent( is0, is1 );
        } catch( Exception e ) {
            Log.e( TAG, "" );
        }
        finally {
            if( is0 != null )
                ca0.closeStream( is0 );
            if( is1 != null )
                ca1.closeStream( is1 );
        }
        return false;
    }
    private boolean compareContent( InputStream is0, InputStream is1 ) {
        try {
            byte[] buf0 = new byte[8192];
            byte[] buf1 = new byte[8192];
            int hr0 = 0, hr1 = 0;
            while( true ) {
                hr0 = is0.read( buf0 );
                if( hr0 <= 0 ) break;
                hr1 = is1.read( buf1 );
                if( hr1 <= 0 ) return false;
                if( hr0 != hr1 ) return false;
                for( int i = 0; i < hr0; i++ )
                    if( buf0[i] != buf1[i] )
                        return false;
            }
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "" );
        }
        return false;
    }
    
}
