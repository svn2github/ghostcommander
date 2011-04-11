package com.ghostsq.commander;

import java.io.File;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class Engine extends Thread {
    protected Handler thread_handler;
	protected boolean stop = false;
	protected String  errMsg = null;
	protected long    threadStartedAt = 0;
    protected int     file_exist_behaviour = Commander.UNKNOWN;

	protected Engine( Handler h ) {
	    thread_handler = h; // TODO - distinct the member from the parent class
	}
    public boolean reqStop() {
        if( isAlive() ) {
            Log.i( getClass().getName(), "reqStop()" );
            stop = true;
            interrupt();
            return true;
        }
        return false;
    }
    protected boolean isStopReq() {
        return stop || isInterrupted();
    }
    protected final void sendProgress( String s, int p ) {
    	sendProgress( s, p, -1 );
    }
    protected final void sendProgress( String s, int p1, int p2 ) {
        Message msg = thread_handler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt( CommanderAdapterBase.NOTIFY_PRG1, p1 );
        if( p2 >= 0 )
            b.putInt( CommanderAdapterBase.NOTIFY_PRG2, p2 );
        if( s != null )
            b.putString( CommanderAdapterBase.NOTIFY_STR, s );
        msg.setData( b );
        thread_handler.sendMessage( msg );
    }
    protected final void sendProgress( String s, int p, String cookie ) {
        Message msg = thread_handler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt( CommanderAdapterBase.NOTIFY_PRG1, p );
        b.putString( CommanderAdapterBase.NOTIFY_COOKIE, cookie );
        if( s != null )
            b.putString( CommanderAdapterBase.NOTIFY_STR, s );
        msg.setData( b );
        thread_handler.sendMessage( msg );
    }
    protected final void sendReceiveReq( int rcpt_hash, String[] items ) {
        Message msg = thread_handler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt( CommanderAdapterBase.NOTIFY_RECEIVER_HASH, rcpt_hash );
        b.putStringArray( CommanderAdapterBase.NOTIFY_ITEMS_TO_RECEIVE, items );
        msg.setData( b );
        thread_handler.sendMessage( msg );
    }
    protected final void sendReceiveReq( int recipient_hash, File dest_folder ) {
        File[] temp_content = dest_folder.listFiles();
        String[] paths = new String[temp_content.length];
        for( int i = 0; i < temp_content.length; i++ )
            paths[i] = temp_content[i].getAbsolutePath();
        sendReceiveReq( recipient_hash, paths );
    }    
    protected final void error( String err ) {
    	if( errMsg == null )
    		errMsg = err;
    	else
    		errMsg += "\n" + err;
    }
    protected final void sendResult( String report ) {
        if( errMsg != null )
            sendProgress( errMsg + "\n" + report, Commander.OPERATION_FAILED );
        else {
            sendProgress( report, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED );
        }
    }
    protected final boolean tooLong( int sec ) {
        if( threadStartedAt == 0 ) return false;
        boolean yes = System.currentTimeMillis() - threadStartedAt > sec * 1000;
        threadStartedAt = 0;
        return yes;
    }

    protected final int askOnFileExist( String msg, Commander commander ) throws InterruptedException {
        if( ( file_exist_behaviour & Commander.APPLY_ALL ) != 0 )
            return file_exist_behaviour & ~Commander.APPLY_ALL;
        int res;
        synchronized( commander ) {
            sendProgress( msg, Commander.OPERATION_SUSPENDED_FILE_EXIST );
            while( ( res = commander.getResolution() ) == Commander.UNKNOWN )
                commander.wait();
        }
        if( res == Commander.ABORT ) {
            error( commander.getContext().getString( R.string.interrupted ) );
            return res;
        }
        if( ( res & Commander.APPLY_ALL ) != 0 )
            file_exist_behaviour = res;
        return res & ~Commander.APPLY_ALL;
    }
}
