package com.ghostsq.commander;

import java.io.File;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

class ChmodDialog implements OnClickListener {
    public final static String TAG = "ChmodDialog";
    private LsItem   item;
    private boolean  ur,  uw,  ux,  us,  gr,  gw,  gx,  gs,  or,  ow,  ox,  ot;
    private CheckBox urc, uwc, uxc, usc, grc, gwc, gxc, gsc, orc, owc, oxc, otc;
    private String   full_file_name;
    private RootAdapter owner;
    ChmodDialog( Context c, LsItem item_, Uri uri, RootAdapter owner_ ) {
        try {
            if( uri == null || item_ == null ) return;
            owner = owner_;
            item = item_;
            String a = item.getAttr();
            if( a.length() < 10 ) return;
            full_file_name = (new File( uri.getPath(), item.getName() ) ).getAbsolutePath();                
            LayoutInflater factory = LayoutInflater.from( c );
            View cdv = factory.inflate( R.layout.chmod, null );
            if( cdv != null ) {
                TextView fnv = (TextView)cdv.findViewById( R.id.file_name );
                fnv.setText( full_file_name );
                {
                    urc = (CheckBox)cdv.findViewById( R.id.UR );
                    ur = a.charAt( 1 ) == 'r';
                    if( ur ) urc.setChecked( true );
                    uwc = (CheckBox)cdv.findViewById( R.id.UW );
                    uw = a.charAt( 2 ) == 'w';
                    if( uw ) uwc.setChecked( true );
                    uxc = (CheckBox)cdv.findViewById( R.id.UX );
                    usc = (CheckBox)cdv.findViewById( R.id.US );
                    char uxl = a.charAt( 3 );
                    if( uxl == 'x' ) {
                        ux = true;
                        us = false;
                    } else 
                    if( uxl == 's' ) {
                        ux = true;
                        us = true;
                    } else if( uxl == 'S' ) {
                        ux = false;
                        us = true;
                    } else {
                        ux = false;
                        us = false;
                    }
                    if( ux ) uxc.setChecked( true );
                    if( us ) usc.setChecked( true );
                } {
                    grc = (CheckBox)cdv.findViewById( R.id.GR );
                    gr = a.charAt( 4 ) == 'r';
                    if( gr ) grc.setChecked( true );
                    gwc = (CheckBox)cdv.findViewById( R.id.GW );
                    gw = a.charAt( 5 ) == 'w';
                    if( gw ) gwc.setChecked( true );
                    gxc = (CheckBox)cdv.findViewById( R.id.GX );
                    gsc = (CheckBox)cdv.findViewById( R.id.GS );
                    char gxl = a.charAt( 6 );
                    if( gxl == 'x' ) {
                        gx = true;
                        gs = false;
                    } else 
                    if( gxl == 's' ) {
                        gx = true;
                        gs = true;
                    } else 
                    if( gxl == 'S' ) {
                        gx = false;
                        gs = true;
                    } else {
                        gx = false;
                        gs = false;
                    }
                    if( gx ) gxc.setChecked( true );
                    if( gs ) gsc.setChecked( true );
                } {
                    orc = (CheckBox)cdv.findViewById( R.id.OR );
                    or = a.charAt( 7 ) == 'r';
                    if( or ) orc.setChecked( true );
                    owc = (CheckBox)cdv.findViewById( R.id.OW );
                    ow = a.charAt( 8 ) == 'w';
                    if( ow ) owc.setChecked( true );
                    oxc = (CheckBox)cdv.findViewById( R.id.OX );
                    otc = (CheckBox)cdv.findViewById( R.id.OT );
                    char otl = a.charAt( 9 );
                    if( otl == 'x' ) {
                        ox = true;
                        ot = false;
                    } else 
                    if( otl == 't' ) {
                        ox = true;
                        ot = true;
                    } else 
                    if( otl == 'T' ) {
                        ox = false;
                        ot = true;
                    } else {
                        ox = false;
                        ot = false;
                    }
                    if( ox ) oxc.setChecked( true );
                    if( ot ) otc.setChecked( true );
                }
                new AlertDialog.Builder( c )
                    .setTitle( "chmod" )
                    .setView( cdv )
                    .setPositiveButton( R.string.dialog_ok, this )
                    .setNegativeButton( R.string.dialog_cancel, this )
                    .show();
            }
        } catch( Exception e ) {
            Log.e( TAG, "ChmodDialog()", e );
        }
    }
    @Override
    public void onClick( DialogInterface idialog, int whichButton ) {
        if( whichButton == DialogInterface.BUTTON_POSITIVE ) {
            StringBuilder a = new StringBuilder();
            boolean nur = urc.isChecked(); 
            boolean nuw = uwc.isChecked(); 
            boolean nux = uxc.isChecked(); 
            boolean nus = usc.isChecked(); 
            boolean ngr = grc.isChecked(); 
            boolean ngw = gwc.isChecked(); 
            boolean ngx = gxc.isChecked(); 
            boolean ngs = gsc.isChecked(); 
            boolean nor = orc.isChecked(); 
            boolean now = owc.isChecked(); 
            boolean nox = oxc.isChecked(); 
            boolean not = otc.isChecked();
            
            if( nur != ur ) {
                a.append( 'u' );
                a.append( nur ? '+' : '-' );
                a.append( 'r' );  
            }
            if( nuw != uw ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'u' );
                a.append( nuw ? '+' : '-' );
                a.append( 'w' );  
            }
            if( nux != ux ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'u' );
                a.append( nux ? '+' : '-' );
                a.append( 'x' );  
            }
            if( nus != us ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'u' );
                a.append( nus ? '+' : '-' );
                a.append( 's' );  
            }
            
            if( ngr != gr ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'g' );
                a.append( ngr ? '+' : '-' );
                a.append( 'r' );  
            }
            if( ngw != gw ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'g' );
                a.append( ngw ? '+' : '-' );
                a.append( 'w' );  
            }
            if( ngx != gx ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'g' );
                a.append( ngx ? '+' : '-' );
                a.append( 'x' );  
            }
            if( ngs != gs ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'g' );
                a.append( ngs ? '+' : '-' );
                a.append( 's' );  
            }
            
            if( nor != or ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'o' );
                a.append( nor ? '+' : '-' );
                a.append( 'r' );  
            }
            if( now != ow ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'o' );
                a.append( now ? '+' : '-' );
                a.append( 'w' );  
            }
            if( nox != ox ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( 'o' );
                a.append( nox ? '+' : '-' );
                a.append( 'x' );  
            }
            if( not != ot ) {
                if( a.length() > 0 )
                    a.append( ',' );
                a.append( not ? '+' : '-' );
                a.append( 't' );  
            }
            if( a.length() > 0 ) {
                a.append( " " );
                a.append( full_file_name );
                owner.Execute( "chmod " + a.toString(), true );
            }
        }
        idialog.dismiss();
    }
}

