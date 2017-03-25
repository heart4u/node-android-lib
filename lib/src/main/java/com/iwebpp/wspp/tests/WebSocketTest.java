package com.iwebpp.wspp.tests;

import com.iwebpp.node.NodeContext;
import com.iwebpp.node.NodeContext.IntervalListener;
import com.iwebpp.node.stream.Writable.WriteCB;
import com.iwebpp.wspp.WebSocket;
import com.iwebpp.wspp.WebSocket.MessageEvent;
import com.iwebpp.wspp.WebSocket.OpenEvent;
import com.iwebpp.wspp.WebSocket.onmessageListener;
import com.iwebpp.wspp.WebSocket.onopenListener;

import android.util.Log;

import junit.framework.TestCase;


public final class WebSocketTest extends TestCase {
	private static final String TAG = "WebSocketTest";
	
	private NodeContext ctx;

	public void testConnect() throws Exception {

		final WebSocket ws = new WebSocket(ctx, "ws://192.188.1.100:6668", null, new WebSocket.Options());

		ws.onopen(new onopenListener(){

			@Override
			public void onOpen(OpenEvent event) throws Exception {
				Log.d(TAG, "ws opened");	

				// send message
				ctx.setInterval(new IntervalListener(){

					@Override
					public void onInterval() throws Exception {

						ws.send("Hello, node-andord", new WebSocket.SendOptions(false, true), new WriteCB(){

							@Override
							public void writeDone(String error) throws Exception {
								Log.d(TAG, "send done");
                                fail("send done:" + error);
							}

						});
						
					}
					
				}, 2000);
				ws.send("Hello, node-andoid", new WebSocket.SendOptions(false, true), new WriteCB(){

					@Override
					public void writeDone(String error) throws Exception {
						Log.d(TAG, "send done");						
					}

				});
			}

		});

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
	}

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.ctx = new NodeContext();
	}

}
