package com.ghostsq.commander.root;

import java.io.File;

import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.LsItem;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

class ChmodDialog implements OnClickListener {
    public final static String TAG = "ChmodDialog";
    private LsItem      item;
    private Permissions p;
    private CheckBox    urc, uwc, uxc, usc, grc, gwc, gxc, gsc, orc, owc, oxc, otc;
    private EditText    ue, ge;
    private String      full_file_name;
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
                p = new Permissions( a );
                {
                    urc = (CheckBox)cdv.findViewById( R.id.UR );
                    if( p.ur ) urc.setChecked( true );
                    uwc = (CheckBox)cdv.findViewById( R.id.UW );
                    if( p.uw ) uwc.setChecked( true );
                    uxc = (CheckBox)cdv.findViewById( R.id.UX );
                    usc = (CheckBox)cdv.findViewById( R.id.US );
                    if( p.ux ) uxc.setChecked( true );
                    if( p.us ) usc.setChecked( true );
                } {
                    grc = (CheckBox)cdv.findViewById( R.id.GR );
                    if( p.gr ) grc.setChecked( true );
                    gwc = (CheckBox)cdv.findViewById( R.id.GW );
                    if( p.gw ) gwc.setChecked( true );
                    gxc = (CheckBox)cdv.findViewById( R.id.GX );
                    gsc = (CheckBox)cdv.findViewById( R.id.GS );
                    if( p.gx ) gxc.setChecked( true );
                    if( p.gs ) gsc.setChecked( true );
                } {
                    orc = (CheckBox)cdv.findViewById( R.id.OR );
                    if( p.or ) orc.setChecked( true );
                    owc = (CheckBox)cdv.findViewById( R.id.OW );
                    if( p.ow ) owc.setChecked( true );
                    oxc = (CheckBox)cdv.findViewById( R.id.OX );
                    otc = (CheckBox)cdv.findViewById( R.id.OT );
                    if( p.ox ) oxc.setChecked( true );
                    if( p.ot ) otc.setChecked( true );
                } {
                    ue = (EditText)cdv.findViewById( R.id.user_edit );
                    ge = (EditText)cdv.findViewById( R.id.group_edit );
                    ue.setText( p.user );
                    ge.setText( p.group );
                }

                new AlertDialog.Builder( c )
                    .setTitle( "chmod/chown" )
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
            Permissions np = new Permissions( null ); 
            np.ur = urc.isChecked(); 
            np.uw = uwc.isChecked(); 
            np.ux = uxc.isChecked(); 
            np.us = usc.isChecked(); 
            np.gr = grc.isChecked(); 
            np.gw = gwc.isChecked(); 
            np.gx = gxc.isChecked(); 
            np.gs = gsc.isChecked(); 
            np.or = orc.isChecked(); 
            np.ow = owc.isChecked(); 
            np.ox = oxc.isChecked(); 
            np.ot = otc.isChecked();
            np.user  = ue.getText().toString();
            np.group = ge.getText().toString();
            String cmd = null;
            StringBuilder a = p.generateChownString( np );
            if( a != null && a.length() > 0 ) {
                a.append( " '" ).append( full_file_name ).append( "'\n" );
                cmd = "chown " + a.toString();
            }
            a = p.generateChmodString( np );
            if( a != null && a.length() > 0 ) {
                a.append( " '" ).append( full_file_name ).append( "'" );
                if( cmd == null ) cmd = "";
                cmd += "busybox chmod " + a.toString();
            }
            if( cmd != null )
                owner.Execute( cmd, false );
        }
        idialog.dismiss();
    }
}

