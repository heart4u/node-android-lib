// Copyright (c) 2014 Tom Zhou<iwebpp@gmail.com>


package com.iwebpp.node.net;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;


import com.iwebpp.libuvpp.Address;
import com.iwebpp.libuvpp.cb.StreamCloseCallback;
import com.iwebpp.libuvpp.cb.StreamConnectCallback;
import com.iwebpp.libuvpp.cb.StreamReadCallback;
import com.iwebpp.libuvpp.cb.StreamShutdownCallback;
import com.iwebpp.libuvpp.cb.StreamWriteCallback;
import com.iwebpp.libuvpp.handles.LoopHandle;
import com.iwebpp.libuvpp.handles.StreamHandle;
import com.iwebpp.node.Dns;
import com.iwebpp.node.EventEmitter;
import com.iwebpp.node.NodeContext;
import com.iwebpp.node.Timers;
import com.iwebpp.node.Util;
import com.iwebpp.node.http.IncomingParser;
import com.iwebpp.node.stream.Duplex;
import com.iwebpp.node.stream.Readable2;
import com.iwebpp.node.stream.Writable2;
import com.iwebpp.node.stream.Writable2.WriteReq;

public abstract class AbstractSocket 
extends Duplex {
	private final static String TAG = "AbstractSocket";

	private boolean _connecting;
	private boolean _hadError;
	protected StreamHandle _handle;
	private Object _pendingData;
	private String _pendingEncoding;
	private boolean allowHalfOpen;
	private boolean destroyed;
	/**
	 * @return the destroyed
	 */
	public boolean isDestroyed() {
		return destroyed;
	}

	private int bytesRead;
	private int _bytesDispatched;
	private AbstractServer abstractServer;

	private Address _sockname;

	private Address _peername;

	private NodeContext context;

	private boolean _paused = false;

	private EventEmitter _httpMessage;

	private IncomingParser parser;

	public static class Options {

		public Options(
				StreamHandle handle, boolean readable,
				boolean writable, boolean allowHalfOpen) {
			super();
			this.handle = handle;
			this.readable = readable;
			this.writable = writable;
			this.allowHalfOpen = allowHalfOpen;
		}

		public StreamHandle handle;
		public long fd = -1;
		public boolean readable;
		public boolean writable;
		public boolean allowHalfOpen;

		@SuppressWarnings("unused")
		private Options(){}
	};

	public AbstractSocket(NodeContext context, Options options) throws Exception {
		// TBD...
		super(context, 
			  new Duplex.Options(new Readable2.Options(-1, null, false, "utf8", options.readable), 
				                 new Writable2.Options(-1, false, "utf8", false, options.writable), 
				                 options.allowHalfOpen)
			  );


		final AbstractSocket self = this;

		// node context
		this.context = context;

		///if (!(this instanceof AbstractSocket)) return new AbstractSocket(options);

		this._connecting = false;
		this.set_hadError(false);
		this._handle = null;
		

		/*if (Util.isNumber(options))
		    options = { fd: options }; // Legacy interface.
		  else if (Util.isUndefined(options))
		    options = {};
		 */

		///stream.Duplex.call(this, options);

		if (options.handle != null) {
			this._handle = options.handle; // private
		} /*TBD...else if (!Util.isUndefined(options.fd)) {
		    this._handle = createHandle(options.fd);

		    this._handle.open(options.fd);
		    if ((options.fd == 1 || options.fd == 2) &&
		        (this._handle instanceof Pipe) &&
		        process.platform === 'win32') {
		      // Make stdout and stderr blocking on Windows
		      var err = this._handle.setBlocking(true);
		      if (err)
		        throw errnoException(err, 'setBlocking');
		    }

		    this.readable = options.readable;
		    this.writable = options.writable;
		  } */else {
			  // these will be set once there is a connection
			  this.readable(false); this.writable(false);
		  }

		// shut down the socket when we're finished with it.

		// the user has called .end(), and all the bytes have been
		// sent out to the other side.
		// If allowHalfOpen is false, or if the readable side has
		// ended already, then destroy.
		// If allowHalfOpen is true, then we need to do a shutdown,
		// so that only the writable side will be cleaned up.
		final Listener onSocketFinish = new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				// If still connecting - defer handling 'finish' until 'connect' will happen
				if (self._connecting) {
					debug(TAG, "osF: not yet connected");
					self.once("connect", this);
					return;
				}

				debug(TAG, "onSocketFinish");
				if (!self.readable() || self.get_readableState().isEnded()) {
					debug(TAG, "oSF: ended, destroy "+self.get_readableState());
					self.destroy(null);
					return;
				}

				debug(TAG, "oSF: not ended, call shutdown()");

				// otherwise, just shutdown, or destroy() if not possible
				if (self._handle==null /*|| !self._handle.shutdown*/) {
					self.destroy(null);
					return;
				}

				/*var req = { oncomplete: afterShutdown };
				  var err = this._handle.shutdown(req);

				  if (err)
					  return this._destroy(errnoException(err, 'shutdown'));
				 */
				self._handle.setShutdownCallback(new StreamShutdownCallback(){

					@Override
					public void onShutdown(int status, Exception error)
							throws Exception {
						///var self = handle.owner;

						debug(TAG, "afterShutdown destroyed="+self.destroyed+","+
								self.get_readableState());

						// callback may come after call to destroy.
						if (self.destroyed)
							return;

						if (self.get_readableState().isEnded()) {
							debug(TAG, "readableState ended, destroying");
							self.destroy(null);
						} else {
							///self.once("_socketEnd", self.destroy);
							self.once("_socketEnd", new Listener(){

								@Override
								public void onEvent(Object data)
										throws Exception {
									self.destroy(null);
								}

							});
						}
					}

				});
				int err = self._handle.closeWrite();

				if (err!=0) {
					self._destroy("shutdown", null);
					return;
				}
			}

		};
		this.on("finish", onSocketFinish);

		// the EOF has been received, and no more bytes are coming.
		// if the writable side has ended already, then clean everything
		// up.
		Listener onSocketEnd = new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				// XXX Should not have to do as much crap in this function.
				// ended should already be true, since this is called *after*
				// the EOF errno and onread has eof'ed
				debug(TAG, "onSocketEnd "+self.get_readableState());
				self.get_readableState().setEnded(true);
				if (self.get_readableState().isEndEmitted()) {
					self.readable(false);
					maybeDestroy(self);
				} else {
					self.once("end", new Listener(){

						public void onEvent(final Object data) throws Exception {
							self.readable(false);
							maybeDestroy(self);
						}

					});

					self.read(0);
				}

				if (!self.allowHalfOpen) {
					// TBD...
					///self.write = writeAfterFIN;
					self.destroySoon();
				}
			}

		};
		this.on("_socketEnd", onSocketEnd);

		initSocketHandle(this);

		this._pendingData = null;
		this._pendingEncoding = "";

		// handle strings directly
		this._writableState.setDecodeStrings(false);

		// default to *not* allowing half open sockets
		this.allowHalfOpen = options.allowHalfOpen;

		// if we have a handle, then start the flow of data into the
		// buffer.  if not, then this will happen when we connect
		if (this._handle!=null && options.readable)
			this.read(0);

	}

	public void destroySoon() throws Exception {
		final AbstractSocket self = this;

		if (this.writable())
			this.end(null, null, null);

		if (this._writableState.isFinished())
			this.destroy(null);
		else
			this.once("finish", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					self.destroy(null);
				}

			});
	}

	// called when creating new AbstractSocket, or when re-using a closed AbstractSocket
	private static void initSocketHandle(final AbstractSocket self) {
		self.destroyed = false;
		self.bytesRead = 0;
		self._bytesDispatched = 0;

		// Handle creation may be deferred to bind() or connect() time.
		if (self._handle != null) {
			///self._handle.owner = self;

			// This function is called whenever the handle gets a
			// buffer, or when there's an error reading.
			StreamReadCallback onread = new StreamReadCallback(){

				@Override
				public void onRead(ByteBuffer buffer) throws Exception {
					int nread = (buffer == null) ? 0 : buffer.capacity();

					///var handle = this;
					///AbstractSocket self = handle.owner;
					StreamHandle handle = self._handle;
					///assert(handle === self._handle, 'handle != self._handle');

					Timers._unrefActive(self);

					///debug('onread', nread);
					debug(TAG, "onread "+nread);

					if (nread > 0) {
						///debug('got data');
						debug(TAG, "got data");

						// read success.
						// In theory (and in practice) calling readStop right now
						// will prevent this from being called again until _read() gets
						// called again.

						// if it's not enough data, we'll just call handle.readStart()
						// again right away.
						self.bytesRead += nread;

						// Optimization: emit the original buffer with end points
						boolean ret = self.push(buffer, null);

						if (/*handle.reading &&*/ !ret) {
							///handle.reading = false;
							debug(TAG, "readStop");
							handle.readStop();
							///var err = handle.readStop();
							///if (err)
							///	self._destroy(errnoException(err, "read"));
						}
						return;
					}

					// if we didn't get any bytes, that doesn't necessarily mean EOF.
					// wait for the next one.
					if (nread == 0) {
						debug(TAG, "not any data, keep waiting");
						return;
					}

					// Error, possibly EOF.
					///if (nread != uv.UV_EOF) {
					///	return self._destroy(errnoException(nread, "read"));
					///}

					debug(TAG, "EOF");

					if (self.get_readableState().getLength() == 0) {
						self.readable(false);
						maybeDestroy(self);
					}

					// push a null to signal the end of data.
					self.push(null, null);

					// internal end event so that we know that the actual socket
					// is no longer readable, and we can start the shutdown
					// procedure. No need to wait for all the data to be consumed.
					self.emit("_socketEnd");
				}

			};
			///self._handle.onread = onread;
			self._handle.setReadCallback(onread);
		}
	}

	// Call whenever we set writable=false or readable=false
	protected static void maybeDestroy(AbstractSocket abstractSocket) throws Exception {
		if (
				!abstractSocket.readable() &&
				!abstractSocket.writable() &&
				!abstractSocket.destroyed &&
				!abstractSocket._connecting &&
				abstractSocket._writableState.getLength()==0) {
			abstractSocket.destroy(null);
		}
	}

	public void destroy(String exception) throws Exception {
		debug(TAG, "destroy "+exception);
		this._destroy(exception, null);
	}

	private void _destroy(final String exception, final Listener cb) throws Exception {
		debug(TAG, "destroy");

		final AbstractSocket self = this;

		Listener fireErrorCallbacks = new Listener() {

			@Override
			public void onEvent(Object data) throws Exception {
				if (cb != null) cb.onEvent(exception);
				if (exception!=null && !self._writableState.isErrorEmitted()) {
					// TBD...
					///process.nextTick(function() {
					context.nextTick(new NodeContext.nextTickListener() {

						public void onNextTick() throws Exception {
							self.emit("error", exception);
						}

					});
					self._writableState.setErrorEmitted(true);
				}
			}

		};

		if (this.destroyed) {
			debug(TAG, "already destroyed, fire error callbacks");
			fireErrorCallbacks.onEvent(null);
			return;
		}

		self._connecting = false;

		this.readable(false);
	    this.writable(false);

		Timers.unenroll(this);

		debug(TAG, "close");
		if (this._handle != null) {
			///if (this !== process.stderr)
			///debug('close handle');
			debug(TAG, "close handle");

			final boolean isException = exception != null ? true : false;

			/*this._handle.close(function() {
		      debug('emit close');
		      self.emit('close', isException);
		    });*/
			this._handle.close(new StreamCloseCallback() {

				@Override
				public void onClose() throws Exception { 
					debug(TAG, "emit close");
					self.emit("close", isException);
				}

			});

			///this._handle.onread = noop;
			this._handle.setReadCallback(new StreamReadCallback(){

				@Override
				public void onRead(ByteBuffer data) throws Exception {

				}

			});

			this._handle = null;
		}

		// we set destroyed to true before firing error callbacks in order
		// to make it re-entrance safe in case AbstractSocket.prototype.destroy()
		// is called within callbacks
		this.destroyed = true;
		fireErrorCallbacks.onEvent(null);

		if (this.abstractServer != null) {
			// TBD...
			///COUNTER_NET_SERVER_CONNECTION_CLOSE(this);
			debug(TAG, "has abstractServer");
			this.abstractServer
					.set_connections(this.abstractServer.get_connections() - 1);
			///if (this.server._emitCloseIfDrained) {
			this.abstractServer._emitCloseIfDrained();
			///}
		}
	}

	private AbstractSocket() {super(null, null);}

	// TBD...
	/*AbstractSocket.prototype.listen = function() {
		  debug('socket.listen');
		  var self = this;
		  self.on('connection', arguments[0]);
		  listen(self, null, null, null);
		};


		AbstractSocket.prototype.setTimeout = function(msecs, callback) {
		  if (msecs > 0 && isFinite(msecs)) {
		    timers.enroll(this, msecs);
		    timers._unrefActive(this);
		    if (callback) {
		      this.once('timeout', callback);
		    }
		  } else if (msecs === 0) {
		    timers.unenroll(this);
		    if (callback) {
		      this.removeListener('timeout', callback);
		    }
		  }
		};


		AbstractSocket.prototype._onTimeout = function() {
		  debug('_onTimeout');
		  this.emit('timeout');
		};
	 */

	public void setTimeout(int msecs, Listener callback) throws Exception {
		if (msecs > 0 /*&& isFinite(msecs)*/) {
			Timers.enroll(this, msecs);
			Timers._unrefActive(this);
			if (callback != null) {
				this.once("timeout", callback);
			}
		} else if (msecs == 0) {
			Timers.unenroll(this);
			if (callback != null) {
				this.removeListener("timeout", callback);
			}
		}
	}

	public String remoteAddress() {
		return this._getpeername().getIp();
	}

	public int remotePort() {
		return this._getpeername().getPort();
	}

	public String remoteFamily() {
		return this._getpeername().getFamily();
	}

	private Address _getpeername() {
		if (null == this._handle /*|| !this._handle.getpeername*/) {
			return null;
		}
		if (null == this._peername) {
			Address out = this._getPeerName();
			if (null == out) return null;  // FIXME(bnoordhuis) Throw?
			this._peername = out;
		}
		return this._peername;
	}

	public Address address() {
		return this._getsockname();
	}

	public String localAddress() {
		return this._getsockname().getIp();
	}

	public int localPort() {
		return this._getsockname().getPort();
	}

	public String family() {
		return this._getsockname().getFamily();
	}

	private Address _getsockname() {
		if (null == this._handle /*|| !this._handle.getsockname*/) {
			return null;
		}
		if (null == this._sockname) {
			Address out = this._getSocketName();
			if (null == out) return null;  // FIXME(bnoordhuis) Throw?
			this._sockname = out;
		}
		return this._sockname;
	}

	public int bytesRead() {
		return this.bytesRead;
	}

	public int bytesWritten() throws UnsupportedEncodingException {
		int bytes = this._bytesDispatched;
		com.iwebpp.node.stream.Writable2.State state = this._writableState;
		Object data = this._pendingData;
		String encoding = this._pendingEncoding;

		/*state.buffer.forEach(function(el) {
			if (util.isBuffer(el.chunk))
				bytes += el.chunk.length;
			else
				bytes += Buffer.byteLength(el.chunk, el.encoding);
		});*/
		for (WriteReq el : state.getBuffer()) {
			if (Util.isBuffer(el.getChunk()))
				bytes += Util.chunkLength(el.getChunk());
			else
				bytes += Util.stringByteLength((String) el.getChunk(), el.getEncoding());
		}

		if (data != null) {
			if (Util.isBuffer(data))
				bytes += Util.chunkLength(data);
			else
				bytes += Util.stringByteLength((String) data, encoding);
		}

		return bytes;
	}

	@Override
	public Object read(int n) throws Exception {
		if (n == 0)
			return super.read(n);

		return super.read(n);
	}

	@Override
	public boolean write(Object chunk, String encoding, WriteCB cb) throws Exception {
		// check on writeAfterFIN 
		if (!this.allowHalfOpen && this.get_readableState().isEnded()) {
			return writeAfterFIN(chunk, encoding, cb);
		} else {
			if (!Util.isString(chunk) && !Util.isBuffer(chunk))
				throw new Exception("invalid data");
			return super.write(chunk, encoding, cb);
		}
	}
	// Provide a better error message when we call end() as a result
	// of the other side sending a FIN.  The standard 'write after end'
	// is overly vague, and makes it seem like the user's code is to blame.
	private boolean writeAfterFIN(Object chunk, String encoding, final WriteCB cb) throws Exception {
		/*if (util.isFunction(encoding)) {
	    cb = encoding;
	    encoding = null;
	  }*/

		///var er = new Error('This socket has been ended by the other party');
		///er.code = 'EPIPE';
		final String er = "This socket has been ended by the other party";
		AbstractSocket self = this;
		// TODO: defer error events consistently everywhere, not just the cb
		self.emit("error", er);
		///if (util.isFunction(cb)) {
		if (cb != null) {
			///process.nextTick(function() {
			context.nextTick(new NodeContext.nextTickListener() {

				@Override
				public void onNextTick() throws Exception {	
					cb.writeDone(er);
				}

			});
		}

		return false;
	}

	public String readyState() {
		if (this._connecting) {
			return "opening";
		} else if (this.readable() && this.writable()) {
			return "open";
		} else if (this.readable() && !this.writable()) {
			return "readOnly";
		} else if (!this.readable() && this.writable()) {
			return "writeOnly";
		} else {
			return "closed";
		}
	}

	public int bufferSize() {
		if (this._handle != null) {
			return (int) (this._handle.writeQueueSize() + this._writableState.getLength());
		}

		return 0;
	}

	// Just call handle.readStart until we have enough in the buffer
	@Override
	public void _read(final int n) throws Exception {
		final AbstractSocket self = this;

		debug(TAG, "_read");

		if (this._connecting || null==this._handle) {
			debug(TAG, "_read wait for connection");
			///this.once("connect", this._read.bind(this, n));
			this.once("connect", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					self._read(n);
				}

			});
		} else if (!this._handle.reading) {
			// not already reading, start the flow
			debug(TAG, "AbstractSocket._read readStart");
			this._handle.reading = true;
			///var err = this._handle.readStart();
			///if (err)
			///	this._destroy(errnoException(err, "read"));
			this._handle.readStart();
		}
	}

	@Override
	public boolean end(Object data, String encoding, WriteCB cb) throws Exception {
		///stream.Duplex.prototype.end.call(this, data, encoding);
		super.end(data, encoding, null);
		this.writable(false);
		///DTRACE_NET_STREAM_END(this);

		// just in case we're waiting for an EOF.
		if (this.readable() && !this.get_readableState().isEndEmitted())
			this.read(0);
		else
			maybeDestroy(this);

		return false;
	}

	/*
AbstractSocket.prototype._writev = function(chunks, cb) {
this._writeGeneric(true, chunks, '', cb);
};*/

	@Override
	public void _write(Object chunk, String encoding, WriteCB cb)
			throws Exception {
		this._writeGeneric(false, chunk, encoding, cb);
	}

	private void _writeGeneric(final boolean writev, final Object data, final String encoding,
			final WriteCB cb) throws Exception {
		final AbstractSocket self = this;

		// If we are still connecting, then buffer this for later.
		// The Writable logic will buffer up any more writes while
		// waiting for this one to be done.
		if (this._connecting) {
			this._pendingData = data;
			this._pendingEncoding = encoding;
			/*
		    this.once("connect", function() {
		      this._writeGeneric(writev, data, encoding, cb);
		    });*/
			this.once("connect", new Listener(){

				@Override
				public void onEvent(Object dummy) throws Exception {
					self._writeGeneric(writev, data, encoding, cb);
				}

			});

			return;
		}
		this._pendingData = null;
		this._pendingEncoding = "";

		Timers._unrefActive(this);

		if (null == this._handle) {
			///this._destroy("This socket is closed.", cb);
			this._destroy("This socket is closed.", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.writeDone("This socket is closed.");
				}

			});

			return;
		}

		/*
		var req = { oncomplete: afterWrite, async: false };
		var err;

		if (writev) {
		    var chunks = new Array(data.length << 1);
		    for (var i = 0; i < data.length; i++) {
		      var entry = data[i];
		      var chunk = entry.chunk;
		      var enc = entry.encoding;
		      chunks[i * 2] = chunk;
		      chunks[i * 2 + 1] = enc;
		    }
		    err = this._handle.writev(req, chunks);

		    // Retain chunks
		    if (err == 0) req._chunks = chunks;
		  } else 
		{
			String enc;
			if (Util.isBuffer(data)) {
				req.buffer = data;  // Keep reference alive.
				enc = "buffer";
			} else {
				enc = encoding;
			}
			err = createWriteReq(req, this._handle, data, enc);
		}

		if (err)
			return this._destroy(errnoException(err, "write", req.error), cb);

		this._bytesDispatched += req.bytes;

		// If it was entirely flushed, we can write some more right now.
		// However, if more is left in the queue, then wait until that clears.
		if (req.async && this._handle.writeQueueSize != 0)
			req.cb = cb;
		else
			cb();
		 */
		// afterWrite
		this._handle.setWriteCallback(new StreamWriteCallback(){

			@Override
			public void onWrite(int status, Exception err)
					throws Exception {
				///var self = handle.owner;
				///if (self !== process.stderr && self !== process.stdout)
				debug(TAG, "afterWrite "+status);

				// callback may come after call to destroy.
				if (self.destroyed) {
					debug(TAG, "afterWrite destroyed");
					return;
				}

				if (status < 0) {
					///var ex = errnoException(status, 'write', err);
					String ex = "" + status + " write " + err;
					debug(TAG, "write failure:" + ex);
					///self._destroy(ex, req.cb);
					self._destroy(ex, null);
					return;
				}

				Timers._unrefActive(self);

				///if (self !== process.stderr && self !== process.stdout)
				debug(TAG, "afterWrite call cb");

				// TBD...
				///if (req.cb)
				///	req.cb.call(self);
			}

		});

		int err = 0;
		if (Util.isBuffer(data)) {
			err = this._handle.write((ByteBuffer)data);
		} else if (Util.isString(data)) {
			err = this._handle.write((String)data, encoding);
		} else {
			this._destroy("write invalid data", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.writeDone("write invalid data");
				}

			});

			return;
		}

		if (err != 0) {
			this._destroy("write invalid data", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.writeDone("write invalid data");
				}

			});

			return;
		}

		// TBD...
		///this._bytesDispatched += req.bytes;

		// If it was entirely flushed, we can write some more right now.
		// However, if more is left in the queue, then wait until that clears.
		///if (req.async && this._handle.writeQueueSize() != 0)
		///	req.cb = cb;
		///else
		cb.writeDone(null);

		return;
	}

	public void connect(int port, final ConnectListener cb) throws Exception {
		// check handle //////////////////////
		if (this.destroyed) {
			this.get_readableState().setReading(false);
			this.get_readableState().setEnded(false);
			this.get_readableState().setEndEmitted(false);
			this._writableState.setEnded(false);
			this._writableState.setEnding(false);
			this._writableState.setFinished(false);
			this._writableState.setErrorEmitted(false);
			this.destroyed = false;
			this._handle = null;
		}

		AbstractSocket self = this;
		///var pipe = !!options.path;
		///debug('pipe', pipe, options.path);

		if (null == this._handle) {
			///this._handle = pipe ? createPipe() : createHandle(context.getLoop());
			this._handle = _createHandle(context.getLoop());
			initSocketHandle(this);
		}

		///if (util.isFunction(cb)) {
		if (cb != null) {
			self.once("connect", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.onConnect();
				}

			});
		}

		Timers._unrefActive(this);

		self._connecting = true;
		// TBD... change true to false
		///self.writable(true);
		self.writable(false);
		///////////////////////////////////////////////

		connect(4, null, port, null, -1);
	}

	public void connect(String address ,int port, final ConnectListener cb) throws Exception {
		// check handle //////////////////////
		if (this.destroyed) {
			this.get_readableState().setReading(false);
			this.get_readableState().setEnded(false);
			this.get_readableState().setEndEmitted(false);
			this._writableState.setEnded(false);
			this._writableState.setEnding(false);
			this._writableState.setFinished(false);
			this._writableState.setErrorEmitted(false);
			this.destroyed = false;
			this._handle = null;
		}

		AbstractSocket self = this;
		///var pipe = !!options.path;
		///debug('pipe', pipe, options.path);

		if (null == this._handle) {
			///this._handle = pipe ? createPipe() : createHandle(context.getLoop());
			this._handle = _createHandle(context.getLoop());
			initSocketHandle(this);
		}

		///if (util.isFunction(cb)) {
		if (cb != null) {
			self.once("connect", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.onConnect();
				}

			});
		}

		Timers._unrefActive(this);

		self._connecting = true;
		// TBD... change true to false
		///self.writable(true);
		self.writable(false);
		///////////////////////////////////////////////

		// DNS lookup
		// TBD... async
		String ip = null;
		if (Util.isIP(address)) {
			ip = address;
		} else {
			ip = Dns.lookup(address);
			if (ip == null) throw new Exception("Invalid address: "+address);
		}

		connect(Util.ipFamily(ip), ip, port, null, -1);
	}

	public void connect(
			String address ,int port,
			int localPort, final ConnectListener cb) throws Exception {
		// check handle //////////////////////
		if (this.destroyed) {
			this.get_readableState().setReading(false);
			this.get_readableState().setEnded(false);
			this.get_readableState().setEndEmitted(false);
			this._writableState.setEnded(false);
			this._writableState.setEnding(false);
			this._writableState.setFinished(false);
			this._writableState.setErrorEmitted(false);
			this.destroyed = false;
			this._handle = null;
		}

		AbstractSocket self = this;
		///var pipe = !!options.path;
		///debug('pipe', pipe, options.path);

		if (null == this._handle) {
			///this._handle = pipe ? createPipe() : createHandle(context.getLoop());
			this._handle = _createHandle(context.getLoop());
			initSocketHandle(this);
		}

		///if (util.isFunction(cb)) {
		if (cb != null) {
			self.once("connect", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.onConnect();
				}

			});
		}

		Timers._unrefActive(this);

		self._connecting = true;
		// TBD... change true to false
		///self.writable(true);
		self.writable(false);
		///////////////////////////////////////////////

		// DNS lookup
		// TBD... async
		String ip = null;
		if (Util.isIP(address)) {
			ip = address;
		} else {
			ip = Dns.lookup(address);
			if (ip == null) throw new Exception("Invalid address: "+address);
		}
		
		connect(Util.ipFamily(ip), ip, port, null, localPort);
	}

	public void connect(
			String address ,int port,
			String localAddress, final ConnectListener cb) throws Exception {
		// check handle //////////////////////
		if (this.destroyed) {
			this.get_readableState().setReading(false);
			this.get_readableState().setEnded(false);
			this.get_readableState().setEndEmitted(false);
			this._writableState.setEnded(false);
			this._writableState.setEnding(false);
			this._writableState.setFinished(false);
			this._writableState.setErrorEmitted(false);
			this.destroyed = false;
			this._handle = null;
		}

		AbstractSocket self = this;
		///var pipe = !!options.path;
		///debug('pipe', pipe, options.path);

		if (null == this._handle) {
			///this._handle = pipe ? createPipe() : createHandle(context.getLoop());
			this._handle = _createHandle(context.getLoop());
			initSocketHandle(this);
		}

		///if (util.isFunction(cb)) {
		if (cb != null) {
			self.once("connect", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.onConnect();
				}

			});
		}

		Timers._unrefActive(this);

		self._connecting = true;
		// TBD... change true to false
		///self.writable(true);
		self.writable(false);
		///////////////////////////////////////////////

		// DNS lookup
		// TBD... async
		String ip = null;
		if (Util.isIP(address)) {
			ip = address;
		} else {
			ip = Dns.lookup(address);
			if (ip == null) throw new Exception("Invalid address: "+address);
		}

		String localip = null;
		if (localAddress != null) {
			if (Util.isIP(localAddress)) {
				localip = localAddress;
			} else {
				localip = Dns.lookup(localAddress);
				if (localip == null) throw new Exception("Invalid localAddress: "+localAddress);
			}
		}
				
		connect(Util.ipFamily(ip), ip, port, localip, -1);
	}

	public void connect(
			String address ,int port, 
			String localAddress, int localPort, final ConnectListener cb) throws Exception {
		// check handle //////////////////////
		if (this.destroyed) {
			this.get_readableState().setReading(false);
			this.get_readableState().setEnded(false);
			this.get_readableState().setEndEmitted(false);
			this._writableState.setEnded(false);
			this._writableState.setEnding(false);
			this._writableState.setFinished(false);
			this._writableState.setErrorEmitted(false);
			this.destroyed = false;
			this._handle = null;
		}

		AbstractSocket self = this;
		///var pipe = !!options.path;
		///debug('pipe', pipe, options.path);

		if (null == this._handle) {
			///this._handle = pipe ? createPipe() : createHandle(context.getLoop());
			this._handle = _createHandle(context.getLoop());
			initSocketHandle(this);
		}

		///if (util.isFunction(cb)) {
		if (cb != null) {
			self.once("connect", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.onConnect();
				}

			});
		}

		Timers._unrefActive(this);

		self._connecting = true;
		// TBD... change true to false
		///self.writable(true);
		self.writable(false);
		///////////////////////////////////////////////


		// DNS lookup
		// TBD... async
		String ip = null;
		if (Util.isIP(address)) {
			ip = address;
		} else {
			ip = Dns.lookup(address);
			if (ip == null) throw new Exception("Invalid address: "+address);
		}

		String localip = null;
		if (localAddress != null) {
			if (Util.isIP(localAddress)) {
				localip = localAddress;
			} else {
				localip = Dns.lookup(localAddress);
				if (localip == null) throw new Exception("Invalid localAddress: "+localAddress);
			}
		}
		
		connect(Util.ipFamily(ip), ip, port, localip, localPort);
	}

	public void connect(int addressType, String address ,int port, 
			final ConnectListener cb) throws Exception {
		// check handle //////////////////////
		if (this.destroyed) {
			this.get_readableState().setReading(false);
			this.get_readableState().setEnded(false);
			this.get_readableState().setEndEmitted(false);
			this._writableState.setEnded(false);
			this._writableState.setEnding(false);
			this._writableState.setFinished(false);
			this._writableState.setErrorEmitted(false);
			this.destroyed = false;
			this._handle = null;
		}

		AbstractSocket self = this;
		///var pipe = !!options.path;
		///debug('pipe', pipe, options.path);

		if (null == this._handle) {
			///this._handle = pipe ? createPipe() : createHandle(context.getLoop());
			this._handle = _createHandle(context.getLoop());
			initSocketHandle(this);
		}

		///if (util.isFunction(cb)) {
		if (cb != null) {
			self.once("connect", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					cb.onConnect();
				}

			});
		}

		Timers._unrefActive(this);

		self._connecting = true;
		// TBD... change true to false
		///self.writable(true);
		self.writable(false);
		///////////////////////////////////////////////
		
		// DNS lookup
		// TBD... async
		String ip = null;
		if (Util.isIP(address)) {
			ip = address;
		} else {
			ip = Dns.lookup(address);
			if (ip == null) throw new Exception("Invalid address: "+address);
		}
				
		connect(Util.ipFamily(ip), ip, port, null, -1);
	}

	private void connect(int addressType, String ip, int port, 
			String localIP, int localPort) throws Exception {
		final AbstractSocket self = this;

		// TODO return promise from AbstractSocket.prototype.connect which
		// wraps _connectReq.

		/*assert.ok(self._connecting);

		  String err;
		  if (localAddress!=null || localPort>0) {
		    if (!Util.zeroString(localAddress) && !exports.isIP(localAddress))
		      err = new TypeError(
		          'localAddress should be a valid IP: ' + localAddress);
		    if (localPort && !util.isNumber(localPort))
		      err = new TypeError('localPort should be a number: ' + localPort);

		    var bind;

		    switch (addressType) {
		      case 4:
		        if (!localAddress)
		          localAddress = "0.0.0.0";
		        bind = self._handle.bind;
		        break;
		      case 6:
		        if (!localAddress)
		          localAddress = '::';
		        bind = self._handle.bind6;
		        break;
		      default:
		        err = new TypeError('Invalid addressType: ' + addressType);
		        break;
		    }

		    if (err) {
		      self._destroy(err);
		      return;
		    }

		    debug('binding to localAddress: %s and localPort: %d',
		          localAddress,
		          localPort);

		    bind = bind.bind(self._handle);
		    err = bind(localAddress, localPort);

		    if (err) {
		      self._destroy(errnoException(err, 'bind'));
		      return;
		    }
		  }
		 */

		// Always bind first
		// TBD... isIP
		if (Util.zeroString(localIP)) {
			localIP = (addressType == 6) ? "::" : "0.0.0.0";
		}
		if (localPort < 0 || localPort >= 65536) {
			localPort = 0;
		}

		debug(TAG, "binding to localAddress: " + localIP +
				" and localPort: " + localPort);

		int err = 0;
		if (addressType == 6) {
			err = this._bind6(localIP, localPort);  
		} else {
			err = this._bind(localIP, localPort);  
		}
		if (err != 0) {
			error(TAG, "err bind");
			///self._destroy(errnoException(err, 'bind'));
			this._destroy("err bind", null);
			return;
		}

		// Try to connect ...

		// afterConnect
		StreamConnectCallback afterConnect = new StreamConnectCallback() {

			@Override
			public void onConnect(int status, Exception error)
					throws Exception {
				///var self = handle.owner;

				// callback may come after call to destroy
				if (self.destroyed) {
					return;
				}

				///assert(handle === self._handle, 'handle != self._handle');

				///assert.ok(self._connecting);
				self._connecting = false;

				if (status >= 0) {
					self.readable(self._handle.isReadable());
					self.writable(self._handle.isWritable());
					
					Timers._unrefActive(self);
					
					self.emit("connect");
					
					// start the first read, or get an immediate EOF.
					// this doesn't actually consume any bytes, because len=0.
					if (readable())
						self.read(0);
				} else {
					error(TAG, "err connect status: "+status);

					self._connecting = false;
					///self._destroy(errnoException(status, 'connect'));
					self._destroy("err connect status: "+status, null);
				}
			}

		};
		this._handle.setConnectCallback(afterConnect);

		// TBD... IP validation
		if (Util.zeroString(ip)) {
			ip = (addressType == 6) ? "::1" : "127.0.0.1";
		}

		if (port <= 0 || port > 65535)
			throw new Exception("Port should be > 0 and < 65536");

		if (addressType == 6) {
			err = this._connect6(ip, port);
		} else {
			err = this._connect(ip, port);
		}

		if (err != 0) {
			error(TAG, "err connect");

			///self._destroy(errnoException(err, 'connect'));
			this._destroy("err connect", null);
		}
		
		debug(TAG, "connect to address: " + ip +
				" and port: " + port);
		
		/*
		  var req = { oncomplete: afterConnect };
		  if (addressType === 6 || addressType === 4) {
		    port = port | 0;
		    if (port <= 0 || port > 65535)
		      throw new RangeError('Port should be > 0 and < 65536');

		    if (addressType === 6) {
		      err = self._handle.connect6(req, address, port);
		    } else if (addressType === 4) {
		      err = self._handle.connect(req, address, port);
		    }
		  } else {
		    err = self._handle.connect(req, address, afterConnect);
		  }

		  if (err) {
		    self._destroy(errnoException(err, 'connect'));
		  }
		 */
	}

	public void ref() {
		this._handle.ref();
	}

	public void unref() {
		this._handle.unref();
	}

	/**
	 * @return the _hadError
	 */
	public boolean is_hadError() {
		return _hadError;
	}

	/**
	 * @param _hadError the _hadError to set
	 */
	public void set_hadError(boolean _hadError) {
		this._hadError = _hadError;
	}

	/**
	 * @return the _httpMessage
	 */
	public EventEmitter get_httpMessage() {
		return _httpMessage;
	}

	/**
	 * @param _httpMessage the _httpMessage to set
	 */
	public void set_httpMessage(EventEmitter _httpMessage) {
		this._httpMessage = _httpMessage;
	}

	/**
	 * @return the parser
	 */
	public IncomingParser getParser() {
		return parser;
	}

	/**
	 * @param parser the parser to set
	 */
	public void setParser(IncomingParser parser) {
		this.parser = parser;
	}

	/**
	 * @return the _paused
	 */
	public boolean is_paused() {
		return _paused;
	}

	/**
	 * @param _paused the _paused to set
	 */
	public void set_paused(boolean _paused) {
		this._paused = _paused;
	}

	// Event listeners
	public void onceConnect(final ConnectListener cb) throws Exception {
		this.once("connect", new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				cb.onConnect();					
			}

		});
	}
	public interface ConnectListener {
		public void onConnect() throws Exception;
	}
	
	
	public void onData(final DataListener cb) throws Exception {
		this.on("data", new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				cb.onData(data);					
			}

		});
	}
	public interface DataListener {
		public void onData(Object data) throws Exception;
	}
	

	public void onceEnd(final EndListener cb) throws Exception {
		this.once("end", new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				cb.onEnd();					
			}

		});
	}
	public interface EndListener {
		public void onEnd() throws Exception;
	}
	
	/* TBD...
	public void onTimeout(final TimeoutListener cb) throws Exception {
		this.on("timeout", new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				cb.onTimeout();					
			}

		});
	}
	public interface TimeoutListener {
		public void onTimeout() throws Exception;
	}*/
	
	public void onDrain(final DrainListener cb) throws Exception {
		this.on("drain", new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				cb.onDrain();					
			}

		});
	}
	public interface DrainListener {
		public void onDrain() throws Exception;
	}
	
	public void onError(final ErrorListener cb) throws Exception {
		this.on("error", new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				cb.onError(data!=null? data.toString() : "");					
			}

		});
	}
	public interface ErrorListener {
		public void onError(String error) throws Exception;
	}
	
	public void onceClose(final CloseListener cb) throws Exception {
		this.once("close", new Listener(){

			@Override
			public void onEvent(Object data) throws Exception {
				Boolean had_error = (Boolean)data;
				
				cb.onClose(had_error);					
			}

		});
	}
	public interface CloseListener {
		public void onClose(boolean had_error) throws Exception;
	}
	
	
	// Abstract socket methods
	protected abstract StreamHandle _createHandle(final LoopHandle loop);

	protected abstract int _bind(final String ip, final int port);
	protected abstract int _bind6(final String ip, final int port);

	protected abstract int _connect(final String ip, final int port);
	protected abstract int _connect6(final String ip, final int port);

	protected abstract Address _getSocketName();
	protected abstract Address _getPeerName();

    public abstract int setNoDelay(final boolean enable);
    public abstract int setKeepAlive(final boolean enable, final int delay);
	
}
