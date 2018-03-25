package com.ghostsq.commander.adapters;

import java.util.Comparator;

import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.utils.Utils;


public class ItemComparator implements Comparator<Item> {
    int type;
    boolean case_ignore, ascending;
    public ItemComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
        type = type_;
        case_ignore = case_ignore_ && ( type_ == CommanderAdapter.SORT_EXT || 
                                        type_ == CommanderAdapter.SORT_NAME );
        ascending = ascending_;
    }
    @Override
    public int compare( Item f1, Item f2 ) {
        boolean f1IsDir = f1.dir;
        boolean f2IsDir = f2.dir;
        if( f1IsDir != f2IsDir )
            return f1IsDir ? -1 : 1;
        int ext_cmp = 0;
        switch( type ) {
        case CommanderAdapter.SORT_EXT:
            ext_cmp = case_ignore ? 
                    Utils.getFileExt( f1.name ).compareToIgnoreCase( Utils.getFileExt( f2.name ) ) :
                    Utils.getFileExt( f1.name ).compareTo( Utils.getFileExt( f2.name ) );
            break;
        case CommanderAdapter.SORT_SIZE:
            ext_cmp = f1.size == f2.size ? 0 : ( f1.size - f2.size < 0 ? -1 : 1 );
            break;
        case CommanderAdapter.SORT_DATE:
        	if( f1.date != null && f2.date != null )
	            ext_cmp = f1.date.compareTo( f2.date );
            break;
        }
        if( ext_cmp == 0 )
            ext_cmp = case_ignore ? f1.name.compareToIgnoreCase( f2.name ) : f1.name.compareTo( f2.name );
        return ascending ? ext_cmp : -ext_cmp;
    }
    
    
    public final static int CMP_NAME = 0x01, 
                            CMP_EXT  = 0x02, 
                            CMP_SIZE = 0x04, 
                            CMP_DATE = 0x08, 
                            CMP_NOT_DATE = 0x07, 
                            CMP_IGNORE_CASE = 0x10;
    
    public final static int compare( Item f1, Item f2, int type_mask ) {
        ItemComparator ic = new ItemComparator( 0, (type_mask & CMP_IGNORE_CASE) != 0, false );
        int r = 0;
        if( (type_mask & CMP_NAME) != 0 ) {
            ic.type = CommanderAdapter.SORT_NAME;
            r = ic.compare( f1, f2 );
            if( r != 0 ) return r;
        }
        if( (type_mask & CMP_EXT) != 0 ) {
            ic.type = CommanderAdapter.SORT_EXT;
            r = ic.compare( f1, f2 );
            if( r != 0 ) return r;
        }
        if( !f1.dir && (type_mask & CMP_SIZE) != 0 ) {
            ic.type = CommanderAdapter.SORT_SIZE;
            r = ic.compare( f1, f2 );
            if( r != 0 ) return r;
        }
        if( (type_mask & CMP_DATE) != 0 ) {
            ic.type = CommanderAdapter.SORT_DATE;
            r = ic.compare( f1, f2 );
        }
        return r;
    }
}
