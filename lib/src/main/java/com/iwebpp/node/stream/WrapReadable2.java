// Copyright (c) 2014 Tom Zhou<iwebpp@gmail.com>


package com.iwebpp.node.stream;

import java.nio.ByteBuffer;

import com.iwebpp.node.EventEmitter;
import com.iwebpp.node.NodeContext;
import com.iwebpp.node.Util;

public final class WrapReadable2 extends Readable2 {
	private final static String TAG = "WrapReadable2";

	private Readable stream;

	private State state;
	boolean paused;

	public WrapReadable2(NodeContext context, Options options, Readable oldstream) throws Exception {
		super(context, options);
		
		// TODO Auto-generated constructor stub
		stream = oldstream;
		state = get_readableState();
		paused = false;
		
		final Readable2 self = this;
		stream.on("end", new EventEmitter.Listener() {
			@Override
			public void onEvent(Object data) throws Exception {
				debug(TAG, "wrapped end");

				if (state.getDecoder()!=null && !state.isEnded()) {
					/*var chunk = state.decoder.end();
			      if (chunk && chunk.length)
			        self.push(chunk);
					 */
					
					// Reset decoder anyway
					/*
					CharBuffer cbuf = CharBuffer.allocate(1024 * 1024);
					state.getDecoder().flush(cbuf);
					String chunk = cbuf.toString();

					if (chunk!=null && Util.chunkLength(chunk)>0) {
						self.push(chunk, null);
					}*/
					state.getDecoder().reset();
				}

				self.push(null, null);
			}
		});

		stream.on("data", new EventEmitter.Listener() {

			@Override
			public void onEvent(Object chunk) throws Exception {
				debug(TAG, "wrapped data");
				if (state.getDecoder() != null)
					///chunk = state.decoder.write(chunk);
					chunk = state.getDecoder().decode((ByteBuffer) chunk).toString();

				if (chunk==null || !state.isObjectMode() && Util.chunkLength(chunk)==0)
					return;

				boolean ret = self.push(chunk, null);
				if (!ret) {
					paused = true;
					stream.pause();
				}
			}
		});

		// proxy all the other methods.
		// important when wrapping filters and duplexes.
		/*for (var i in stream) {
		    if (util.isFunction(stream[i]) && util.isUndefined(this[i])) {
		      this[i] = function(method) { return function() {
		        return stream[method].apply(stream, arguments);
		      }}(i);
		    }
		  }*/

		// proxy certain important events.
		/*var events = ["error", "close", "destroy", "pause", "resume"];
		  events.forEach(function(ev) {
		    stream.on(ev, self.emit.bind(self, ev));
		  });*/
		stream.on("error", new EventEmitter.Listener() {
			@Override
			public void onEvent(Object data) throws Exception {
				self.emit("error", data);
			}
		});
		stream.on("close", new EventEmitter.Listener() {
			@Override
			public void onEvent(Object data) throws Exception {
				self.emit("close", data);
			}
		});
		stream.on("destroy", new EventEmitter.Listener() {
			@Override
			public void onEvent(Object data) throws Exception {
				self.emit("destroy", data);
			}
		});
		stream.on("pause", new EventEmitter.Listener() {
			@Override
			public void onEvent(Object data) throws Exception {
				self.emit("pause", data);
			}
		});
		stream.on("resume", new EventEmitter.Listener() {
			@Override
			public void onEvent(Object data) throws Exception {
				self.emit("resume", data);
			}
		});
		
	}

	// when we try to consume some more bytes, simply unpause the
	// underlying stream.
	@Override
	protected void _read(int n) throws Exception {
		debug(TAG, "wrapped _read "+n);
		if (paused) {
			paused = false;
			stream.resume();
		}
	}
	
}
