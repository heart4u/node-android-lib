package com.iwebpp.wspp.tests;

import java.util.Date;

import android.util.Log;

import com.iwebpp.node.NodeContext;
import com.iwebpp.node.NodeContext.IntervalListener;
import com.iwebpp.wspp.WebSocket;
import com.iwebpp.wspp.WebSocket.ErrorEvent;
import com.iwebpp.wspp.WebSocket.MessageEvent;
import com.iwebpp.wspp.WebSocket.OpenEvent;
import com.iwebpp.wspp.WebSocket.onmessageListener;
import com.iwebpp.wspp.WebSocket.onopenListener;
import com.iwebpp.wspp.WebSocketServer;

import junit.framework.TestCase;

public final class WebSocketServerTest extends TestCase {
	private static final String TAG = "WebSocketServerTest";
	
	private NodeContext ctx;

	public void testConnectPair() throws Exception {

		WebSocketServer.Options wssopt = new WebSocketServer.Options();
		wssopt.port = 6668;
		wssopt.path = "/wspp";

		WebSocketServer wss = new WebSocketServer(ctx, wssopt, new WebSocketServer.ListeningCallback() {

			@Override
			public void onListening() throws Exception {
				Log.d(TAG, "websocket server listening ...");		
				
				final WebSocket ws = new WebSocket(ctx, "ws://localhost:6668/wspp", null, new WebSocket.Options());

				ws.onmessage(new onmessageListener(){

					@Override
					public void onMessage(MessageEvent event) throws Exception {
						Log.d(TAG, "message: "+event.toString());		

						if (event.isBinary()) {
							Log.d(TAG, "binary message: "+event.getData().toString());
						} else {
							Log.d(TAG, "text message: "+(String)(event.getData()));
						}
					}

				});
				
				ws.onerror(new WebSocket.onerrorListener() {
					
					@Override
					public void onError(ErrorEvent event) throws Exception {
                        Log.d(TAG, "ws error:"+event.getCode()+",message:"+event.getError());						
                        fail("ws error:"+event.getCode()+",message:"+event.getError());
					}
					
				});

				ws.onopen(new onopenListener(){

					@Override
					public void onOpen(OpenEvent event) throws Exception {
                        ws.send("Hello, tom zhou", new WebSocket.SendOptions(false, false), null);	
                        
                        ctx.setInterval(new IntervalListener(){

							@Override
							public void onInterval() throws Exception {
		                        ws.send("Hello, tom zhou @"+new Date(), new WebSocket.SendOptions(false, true), null);									
							}
                        	
                        }, 3000);
					}
					
				});
				
			}

		});

		wss.onconnection(new WebSocketServer.onconnectionListener() {

			@Override
			public void onConnection(final WebSocket socket) throws Exception {
				Log.d(TAG, "new ws client:"+socket);	
				
				socket.onmessage(new WebSocket.onmessageListener() {
					
					@Override
					public void onMessage(MessageEvent event) throws Exception {
						Log.d(TAG, "client message: "+event.toString());		

						if (event.isBinary()) {
							Log.d(TAG, "binary message: "+event.getData().toString());
							
							socket.send(event.getData(), new WebSocket.SendOptions(true, true), null);
						} else {
							Log.d(TAG, "text message: "+(String)(event.getData()));
							
							socket.send(event.getData().toString()+"@srv", new WebSocket.SendOptions(false, false), null);
						}
					}
					
				});
				
				socket.send("Hello@srv", new WebSocket.SendOptions(false, false), null);
			}

		});
	}

	public void testConnectPairOverUDP() throws Exception {

		WebSocketServer.Options wssopt = new WebSocketServer.Options();
		wssopt.port = 6668;
		wssopt.path = "/wspp";
		wssopt.httpp = true;

		WebSocketServer wss = new WebSocketServer(ctx, wssopt, new WebSocketServer.ListeningCallback() {

			@Override
			public void onListening() throws Exception {
				Log.d(TAG, "httpp websocket server listening ...");		
				
				WebSocket.Options wsopt = new WebSocket.Options();
				wsopt.httpp = true;
				
				final WebSocket ws = new WebSocket(ctx, "ws://localhost:6668/wspp", null, wsopt);

				ws.onmessage(new onmessageListener(){

					@Override
					public void onMessage(MessageEvent event) throws Exception {
						Log.d(TAG, "httpp message: "+event.toString());		

						if (event.isBinary()) {
							Log.d(TAG, "httpp binary message: "+event.getData().toString());
						} else {
							Log.d(TAG, "httpp text message: "+(String)(event.getData()));
						}
					}

				});
				
				ws.onerror(new WebSocket.onerrorListener() {
					
					@Override
					public void onError(ErrorEvent event) throws Exception {
                        Log.d(TAG, "httpp ws error:"+event.getCode()+",message:"+event.getError());						
                        fail("httpp ws error:"+event.getCode()+",message:"+event.getError());
					}
					
				});

				ws.onopen(new onopenListener(){

					@Override
					public void onOpen(OpenEvent event) throws Exception {
                        ws.send("httpp Hello, tom zhou", new WebSocket.SendOptions(false, false), null);	
                        
                        ctx.setInterval(new IntervalListener(){

							@Override
							public void onInterval() throws Exception {
		                        ws.send("httpp Hello, tom zhou @"+new Date(), new WebSocket.SendOptions(false, true), null);									
							}
                        	
                        }, 3000);
					}
					
				});
				
			}

		});

		wss.onconnection(new WebSocketServer.onconnectionListener() {

			@Override
			public void onConnection(final WebSocket socket) throws Exception {
				Log.d(TAG, "httpp new ws client:"+socket);	
				
				socket.onmessage(new WebSocket.onmessageListener() {
					
					@Override
					public void onMessage(MessageEvent event) throws Exception {
						Log.d(TAG, "httpp client message: "+event.toString());		

						if (event.isBinary()) {
							Log.d(TAG, "httpp binary message: "+event.getData().toString());
							
							socket.send(event.getData(), new WebSocket.SendOptions(true, true), null);
						} else {
							Log.d(TAG, "httpp text message: "+(String)(event.getData()));
							
							socket.send(event.getData().toString()+"@srv httpp", new WebSocket.SendOptions(false, false), null);
						}
					}
					
				});
				
				socket.send("httpp Hello@srv", new WebSocket.SendOptions(false, false), null);
			}

		});
	}

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.ctx = new NodeContext();
	}

}
