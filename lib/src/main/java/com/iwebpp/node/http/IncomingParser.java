// Copyright (c) 2014 Tom Zhou<iwebpp@gmail.com>


package com.iwebpp.node.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;


import com.iwebpp.node.HttpParser;
import com.iwebpp.node.NodeContext;
import com.iwebpp.node.net.AbstractSocket;

public abstract class IncomingParser 
extends HttpParser {
	private final static String TAG = "IncomingParser";

	protected AbstractSocket socket;
	protected IncomingMessage incoming;
	protected CharsetDecoder decoder;

	protected String [] fields_; ///[32];  // header fields
	protected String [] values_; ///[32];  // header values
	protected String url_;
	protected String status_message_;
	protected int num_fields_;
	protected int num_values_;
	protected boolean have_flushed_;
	protected ByteBuffer current_buffer_;

	protected int maxHeaderPairs;

	protected List<String> _headers;
	protected String _url;

	private NodeContext context;

	protected IncomingParser(NodeContext ctx, http_parser_type type, AbstractSocket socket) {
		super(type, socket);
		this.context = ctx;
		
		// TODO Auto-generated constructor stub
		this.decoder = Charset.forName("utf-8").newDecoder();

		this.socket = socket;
		
		this._headers = new ArrayList<String>();
		this._url = "";

		this.fields_ = new String[32];
		this.values_ = new String[32];
		this.url_ = "";
		this.status_message_ = "";
		this.num_fields_ = this.num_values_ = 0;
		this.have_flushed_ = false;

		this.current_buffer_ = null;
	}
	private IncomingParser(){super(null, null);}


	protected void Init(http_parser_type type) {
		super.reset(type);

		_headers.clear();
		_url = "";

		url_ = "";
		status_message_ = "";
		num_fields_ = 0;
		num_values_ = 0;
		have_flushed_ = false;
		
		current_buffer_ = null;
	}

	// spill headers and request path to JS land
	protected void Flush() {
		parserOnHeaders(CreateHeaders(), url_);

		///if (r.IsEmpty())
		///	got_exception_ = true;

		url_ = "";
		have_flushed_ = true;
	}

	protected List<String> CreateHeaders() {
		// num_values_ is either -1 or the entry # of the last header
		// so num_values_ == 0 means there's a single header
		List<String> headers = new ArrayList<String>();

		for (int i = 0; i < this.num_values_; i ++) {
			headers.add(this.fields_[i]);
			headers.add(this.values_[i]);
		}

		return headers;
	}

	public void Pause(boolean should_pause) {
		pause(should_pause);
	}

	public void Reinitialize(http_parser_type type) {
		Init(type);
	}

	public int Finish() throws Exception {
		int rv = execute(null);

		if (rv != 0) {
			http_errno err = HTTP_PARSER_ERRNO();

			throw new Exception(err.desc());
		}

		return rv;
	}

	// var bytesParsed = parser->execute(buffer);
	public int Execute(ByteBuffer buffer_obj) throws Exception {
		int buffer_len = buffer_obj.capacity();

		// This is a hack to get the current_buffer to the callbacks with the least
		// amount of overhead. Nothing else will run while http_parser_execute()
		// runs, therefore this pointer can be set and used for the execution.
		current_buffer_ = buffer_obj;
		
		int nparsed = execute(current_buffer_);

		// Unassign the 'buffer_' variable
		current_buffer_.clear();

		// If there was a parse error in one of the callbacks
		// TODO(bnoordhuis) What if there is an error on EOF?
		if (!isUpgrade() && nparsed != buffer_len) {
			// TBD...
			///http_errno err = HTTP_PARSER_ERRNO();
			///throw new Exception(err.desc());
			return -1;
		}

		return nparsed;
	}

	// Only called in the slow case where slow means
	// that the request headers were either fragmented
	// across multiple TCP packets or too large to be
	// processed in a single run. This method is also
	// called to process trailing http headers.
	protected void parserOnHeaders(List<String> headers, String url) {
		debug(TAG, "parserOnHeaders ");
		
		// Once we exceeded headers limit - stop collecting them
		if (this.maxHeaderPairs <= 0 ||
			this._headers.size() < this.maxHeaderPairs) {
			///this._headers = this._headers.concat(headers);
			this._headers.addAll(headers);
		}
		this._url += url != null ? url : "";
	}

	// info.headers and info.url are set only if .onHeaders()
	// has not been called for this request.
	//
	// info.url is not set for response parsers but that's not
	// applicable here since all our parsers are request parsers.
	///function parserOnHeadersComplete(info) {
	protected boolean parserOnHeadersComplete(parseInfo info) throws Exception {
		///debug('parserOnHeadersComplete', info);
		debug(TAG, "parserOnHeadersComplete "+info);

		///var parser = this;
		List<String> headers = info.headers;
		String url = info.url;

		if (null == headers || headers.isEmpty()) {
			headers = _headers;
			_headers.clear();
		}

		if (null==url || ""==url) {
			url = _url;
			_url = "";
		}

		/*parser.incoming = new IncomingMessage(parser.socket);
	  parser.incoming.httpVersionMajor = info.versionMajor;
	  parser.incoming.httpVersionMinor = info.versionMinor;
	  parser.incoming.httpVersion = info.versionMajor + '.' + info.versionMinor;
	  parser.incoming.url = url;
		 */
		// TBD...
		this.incoming = new IncomingMessage(context, (AbstractSocket)super.getData());
        this.incoming.setHttpVersionMajor(info.versionMajor);
        this.incoming.setHttpVersionMinor(info.versionMinor);
        this.incoming.httpVersion(info.versionMajor + "." + info.versionMinor);
        this.incoming.url(url);
		
		///var n = headers.length;
		int n = headers.size();

		// If parser.maxHeaderPairs <= 0 - assume that there're no limit
		if (maxHeaderPairs > 0) {
			n = Math.min(n, maxHeaderPairs);
		}

		incoming._addHeaderLines(headers, n);

		if (super.getType() == http_parser_type.HTTP_REQUEST/*isNumber(info.method)*/) {
			// server only
			incoming.setMethod(info.method.desc()) ;
		} else {
			// client only
			incoming.setStatusCode(info.statusCode);
			incoming.setStatusMessage(info.statusMessage);
		}

		incoming.setUpgrade(info.upgrade);

		boolean skipBody = false; // response to HEAD or CONNECT

		if (!info.upgrade) {
			// For upgraded connections and CONNECT method request,
			// we'll emit this after parser.execute
			// so that we can capture the first part of the new protocol
			skipBody = onIncoming(incoming, info.shouldKeepAlive);
		}

		return skipBody;
	}
	// POJO bean
	protected class parseInfo {
		public boolean shouldKeepAlive;
		public boolean upgrade;
		public http_method method;
		public String url;
		public List<String> headers;
		public int statusCode;
		public String statusMessage;
		public int versionMajor;
		public int versionMinor;
	}

	protected abstract boolean onIncoming(IncomingMessage incoming, boolean shouldKeepAlive) throws Exception;


	// XXX This is a mess.
	// TODO: http.Parser should be a Writable emits request/response events.
	///function parserOnBody(b, start, len) {
	protected void parserOnBody(ByteBuffer b) throws Exception {
		debug(TAG, "parserOnBody ");

		IncomingParser parser = this;
		IncomingMessage stream = parser.incoming;

		// if the stream has already been removed, then drop it.
		if (null==stream)
			return;

		AbstractSocket socket = stream.socket();

		int len = b == null ? 0 : b.capacity();

		// pretend this was the result of a stream._read call.
		if (len > 0 && !stream.is_dumped()) {
			///var slice = b.slice(start, start + len);
			boolean ret = stream.push(b, null);
			if (!ret)
				IncomingMessage.readStop(socket);
		}
	}

	///function parserOnMessageComplete() {
	protected void parserOnMessageComplete() throws Exception {
		debug(TAG, "parserOnMessageComplete ");

		IncomingParser parser = this;
		IncomingMessage stream = parser.incoming;

		if (stream!=null) {
			stream.setComplete(true);
			// Emit any trailing headers.
			List<String> headers = parser._headers;
			if (headers!=null && !headers.isEmpty()) {
				stream._addHeaderLines(headers, headers.size());
				_headers.clear();
				_url = "";
			}

			if (!stream.isUpgrade())
				// For upgraded connections, also emit this after parser.execute
				stream.push(null, null);
		}

		if (stream!=null && 0==stream.get_pendings().size()) {
			// For emit end event
			stream.push(null, null);
		}

		// force to read the next incoming message
		IncomingMessage.readStart(parser.socket);
	}

	@Override
	protected int on_message_begin() throws Exception {
		num_fields_ = num_values_ = 0;
		url_ = "";
		status_message_ = "";
		return 0;
	}

	@Override
	protected int on_url(ByteBuffer url) throws Exception {
		url_ = decoder.decode(url).toString();
		return 0;
	}

	@Override
	protected int on_status(ByteBuffer status) throws Exception {
		status_message_ = decoder.decode(status).toString();
		return 0;
	}

	@Override
	protected int on_header_field(ByteBuffer field) throws Exception {
		if (num_fields_ == num_values_) {
			// start of new field name
			num_fields_++;
			///if (num_fields_ == ARRAY_SIZE(fields_)) {
			if (num_fields_ == fields_.length) {
				// ran out of space - flush to javascript land
				Flush();
				num_fields_ = 1;
				num_values_ = 0;
			}
			fields_[num_fields_ - 1] = "";
		}

		///assert(num_fields_ < static_cast<int>(ARRAY_SIZE(fields_)));
		assert(num_fields_ < fields_.length);
		assert(num_fields_ == num_values_ + 1);

		fields_[num_fields_ - 1] = decoder.decode(field).toString();

		return 0;
	}

	@Override
	protected int on_header_value(ByteBuffer value) throws Exception {
		if (num_values_ != num_fields_) {
			// start of new header value
			num_values_++;
			values_[num_values_ - 1] = "";
		}

		assert(num_values_ < values_.length);
		assert(num_values_ == num_fields_);

		values_[num_values_ - 1] = decoder.decode(value).toString();

		return 0;
	}

	@Override
	protected int on_headers_complete() throws Exception {
		///Local<Object> message_info = Object::New(env()->isolate());
		parseInfo message_info = new parseInfo();

		if (have_flushed_) {
			// Slow case, flush remaining headers.
			Flush();
		} else {
			// Fast case, pass headers and URL to JS land. 
			message_info.headers = CreateHeaders();
			if (getType() == http_parser_type.HTTP_REQUEST)
				message_info.url = url_;
		}
		num_fields_ = num_values_ = 0;

		// METHOD
		if (getType() == http_parser_type.HTTP_REQUEST) {
			message_info.method = getMethod();
		}

		// STATUS
		if (getType() == http_parser_type.HTTP_RESPONSE) {		      
			message_info.statusCode = getStatus_code();
			message_info.statusMessage = status_message_;
		}

		// VERSION
		message_info.versionMajor = super.getHttp_major();
		message_info.versionMinor = super.getHttp_minor();
		
		message_info.shouldKeepAlive = super.http_should_keep_alive();
		
		message_info.upgrade = super.isUpgrade();

		return parserOnHeadersComplete(message_info) ? 1 : 0;
	}

	@Override
	protected int on_body(ByteBuffer body) throws Exception {
		parserOnBody(body);

		return 0;
	}

	@Override
	protected int on_message_complete() throws Exception {
		if (num_fields_ > 0)
			Flush();  // Flush trailing http headers.

		parserOnMessageComplete();

		return 0;
	}

	// Free the parser and also break any links that it
	// might have to any other things.
	// TODO: All parser data should be attached to a
	// single object, so that it can be easily cleaned
	// up by doing `parser.data = {}`, which should
	// be done in FreeList.free.  `parsers.free(parser)`
	// should be all that is needed.
	public static void freeParser(IncomingParser parser, Object req) {
	  if (parser != null) {
	    parser._headers.clear();
	    ///parser.onIncoming = null;
	    if (parser.socket != null)
	      parser.socket.setParser(null);
	    parser.socket = null;
	    parser.incoming = null;
	    ///parsers.free(parser);
	    parser = null;
	  }
	  if (req != null) {
		  if (req instanceof IncomingMessage)
			  ((IncomingMessage)req).setParser(null);
		  
		  if (req instanceof ClientRequest)
			  ((ClientRequest)req).setParser(null);
	  }
	}
	
}
