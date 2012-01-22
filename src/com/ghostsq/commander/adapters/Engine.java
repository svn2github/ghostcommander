package com.ghostsq.commander.adapters;

import java.io.File;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapterBase;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class Engine extends Thread {
    protected final String TAG = getClass().getSimpleName();
    protected Handler thread_handler;
	protected boolean stop = false;
	protected String  errMsg = null;
	protected long    threadStartedAt = 0;
    protected int     file_exist_behaviour = Commander.UNKNOWN;

    protected Engine( Handler h ) {
        thread_handler = h; // TODO - distinct the member from the parent class
    }
    protected Engine( Handler h, Runnable r ) {
        super( r );
        thread_handler = h; 
    }
	protected void Init( String name ) {
	    setName( name == null ? getClass().getName() : name );
	}
	
    public boolean reqStop() {
        if( isAlive() ) {
            Log.i( getClass().getName(), "reqStop()" );
            if( stop )
                interrupt();
            else
                stop = true;
            return true;
        }
        else {
            Log.i( getClass().getName(), "Engine thread is not alive" );
            return false;
        }
    }
    protected boolean isStopReq() {
        return stop || isInterrupted();
    }
    protected final void sendProgress( String s, int p ) {
        sendProgress( s, p, -1, -1 );
    }
    protected final void sendProgress( String s, int p1, int p2 ) {
        sendProgress( s, p1, p2, -1 );
    }
    protected final void sendProgress( String s, int p1, int p2, int speed ) {
        //Log.v( TAG, "sendProgress: " + s );
        if( thread_handler == null ) return;
        Message msg = null;
        if( p1 < 0 )
            msg = thread_handler.obtainMessage( p1, -1, -1, s );
        else
            msg = thread_handler.obtainMessage( Commander.OPERATION_IN_PROGRESS, p1, p2, s );
        if( speed > 0 ) {
            Bundle b = new Bundle();
            b.putInt( Commander.NOTIFY_SPEED, speed );
            msg.setData( b );
        }
        thread_handler.sendMessage( msg );
    }
    protected final void sendProgress( String s, int p, String cookie ) {
        //Log.v( TAG, "sendProgress: " + s + ", cookie: " + cookie );
        if( thread_handler == null ) return;
        Message msg = null;
        if( p < 0 )
            msg = thread_handler.obtainMessage( p, -1, -1, s );
        else
            msg = thread_handler.obtainMessage( Commander.OPERATION_IN_PROGRESS, p, -1, s );
        Bundle b = new Bundle();
        b.putString( Commander.NOTIFY_COOKIE, cookie );
        msg.setData( b );
        thread_handler.sendMessage( msg );
    }
    protected final void sendReceiveReq( int rcpt_hash, String[] items ) {
        if( thread_handler == null ) return;
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
        Log.e( getClass().getSimpleName(), err == null ? "Unknown error" : err );
    	if( errMsg == null )
    		errMsg = err;
    	else
    		errMsg += "\n" + err;
    }
    protected final void sendResult( String report ) {
        if( errMsg != null )
            sendProgress( report + "\n - " + errMsg, Commander.OPERATION_FAILED );
        else {
            sendProgress( report, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED );
        }
    }
    protected final void doneReading( String msg, String cookie ) {
        if( errMsg != null )
            sendProgress( errMsg, Commander.OPERATION_FAILED, cookie );
        else {
            sendProgress( msg, Commander.OPERATION_COMPLETED, cookie );
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
