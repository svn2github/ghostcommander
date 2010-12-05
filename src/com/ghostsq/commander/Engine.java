package com.ghostsq.commander;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class Engine extends Thread {
	private   Handler handler;
	protected boolean stop = false;
	protected String  errMsg = null;
	protected long threadStartedAt = 0;
	protected Engine( Handler h ) {
		handler = h;
	}
    public boolean reqStop() {
        if( isAlive() ) {
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
        Message msg = handler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt( CommanderAdapterBase.NOTIFY_PRG1, p1 );
        if( p2 >= 0 )
            b.putInt( CommanderAdapterBase.NOTIFY_PRG2, p2 );
        if( s != null )
            b.putString( CommanderAdapterBase.NOTIFY_STR, s );
        msg.setData( b );
        handler.sendMessage( msg );
    }
    protected final void sendProgress( String s, int p, String cookie ) {
        Message msg = handler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt( CommanderAdapterBase.NOTIFY_PRG1, p );
        b.putString( CommanderAdapterBase.NOTIFY_COOKIE, cookie );
        if( s != null )
            b.putString( CommanderAdapterBase.NOTIFY_STR, s );
        msg.setData( b );
        handler.sendMessage( msg );
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
        else
            sendProgress( report, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED );
    }
    protected final boolean tooLong( int sec ) {
        if( threadStartedAt == 0 ) return false;
        boolean yes = System.currentTimeMillis() - threadStartedAt > sec * 1000;
        threadStartedAt = 0;
        return yes;
    }
}
