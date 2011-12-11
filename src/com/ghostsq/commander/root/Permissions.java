package com.ghostsq.commander.root;

public class Permissions {
    public boolean  ur,  uw,  ux,  us,  gr,  gw,  gx,  gs,  or,  ow,  ox,  ot;
    public String   user, group;
    Permissions( String a ) {
        if( a == null ) return;  // can we assume all the flags will be false?
        ur = a.charAt( 1 ) == 'r';
        uw = a.charAt( 2 ) == 'w';
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
        gr = a.charAt( 4 ) == 'r';
        gw = a.charAt( 5 ) == 'w';
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
        or = a.charAt( 7 ) == 'r';
        ow = a.charAt( 8 ) == 'w';
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
        String[] aa = a.substring( 10 ).split( "\\s+" );
        if( aa != null ) {
            if( aa.length >= 3 ) {
                user = aa[1];
                group = aa[2];
            } else {
                user = aa[0];
                if( aa.length > 1 )
                    group = aa[1];
            }
        }       
    }
    
    StringBuilder generateChmodString( Permissions np ) {
        StringBuilder a = new StringBuilder();
        if( np.ur != ur ) {
            a.append( 'u' ).append( np.ur ? '+' : '-' ).append( 'r' );  
        }
        if( np.uw != uw ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'u' ).append( np.uw ? '+' : '-' ).append( 'w' );  
        }
        if( np.ux != ux ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'u' ).append( np.ux ? '+' : '-' ).append( 'x' );  
        }
        if( np.us != us ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'u' ).append( np.us ? '+' : '-' ).append( 's' );  
        }
        
        if( np.gr != gr ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'g' ).append( np.gr ? '+' : '-' ).append( 'r' );  
        }
        if( np.gw != gw ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'g' ).append( np.gw ? '+' : '-' ).append( 'w' );  
        }
        if( np.gx != gx ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'g' ).append( np.gx ? '+' : '-' ).append( 'x' );  
        }
        if( np.gs != gs ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'g' ).append( np.gs ? '+' : '-' ).append( 's' );  
        }
        
        if( np.or != or ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'o' ).append( np.or ? '+' : '-' ).append( 'r' );  
        }
        if( np.ow != ow ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'o' ).append( np.ow ? '+' : '-' ).append( 'w' );  
        }
        if( np.ox != ox ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( 'o' ).append( np.ox ? '+' : '-' ).append( 'x' );  
        }
        if( np.ot != ot ) {
            if( a.length() > 0 )
                a.append( ',' );
            a.append( np.ot ? '+' : '-' );
            a.append( 't' );  
        }
        return a;
    }
    StringBuilder generateChownString( Permissions np ) {
        if( np.user  == null || np.user.length()  == 0 ) return null;
        if( np.group == null || np.group.length() == 0 ) return null;
        StringBuilder a = new StringBuilder();
        a.append( np.user ).append( "." ).append( np.group );
        return a;
    }
}
