// Copyright (c) 2014 Tom Zhou<iwebpp@gmail.com>


package com.iwebpp.node.tests;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import android.util.Log;

import com.iwebpp.node.EventEmitter.Listener;
import com.iwebpp.node.NodeContext;
import com.iwebpp.node.NodeContext.IntervalListener;
import com.iwebpp.node.Util;
import com.iwebpp.node.stream.Duplex;
import com.iwebpp.node.stream.Readable;
import com.iwebpp.node.stream.Readable2;
import com.iwebpp.node.stream.Transform;
import com.iwebpp.node.stream.Writable;
import com.iwebpp.node.stream.Writable2;
import com.iwebpp.node.stream.Writable.WriteCB;

import junit.framework.TestCase;

public final class StreamTest extends TestCase {
	private static final String TAG = "StreamTest";
	private NodeContext context;
	private static String burst;

	static {
		burst = "你好";

		for (int i = 0; i < 6; i++)
			burst += i;
	}

	public StreamTest() {
		this.context = new NodeContext();
	}
	
	private class Counting extends Readable2 {

		private int _max = 10;
		private int _index = 1;

		Counting() {
			super(context, new Readable2.Options(16, "utf8", false, "utf8", true));
			// TODO Auto-generated constructor stub

			_index = 1;
		}

		@Override
		public void _read(int size) throws Exception {
			int i = this._index++;
			if (i > this._max)
				this.push(null, null);
			else {
				for (int c = 0; c < 3; c ++) {
					String str = burst + "@大家好" + i+"/"+c+"$";

					ByteBuffer buf = ByteBuffer.wrap(str.getBytes("utf8"));
					this.push(buf, null);
					///this.push(str, "utf8");
				}
			}
		}

	}

	private class DummyWritable extends Writable2 {

		public DummyWritable() {
			super(context, new Options(-1, true, "utf8", false, true));
			// TODO Auto-generated constructor stub
		}

		@Override
		public void _write(Object chunk, String encoding, WriteCB cb) throws Exception {
			if (Util.isString(chunk)) {
				Log.d(TAG, "DummyWritable: encdoing "+encoding+":"+chunk.toString());

				if (cb != null) cb.writeDone(null);

			} else {
				Log.d(TAG, "DummyWritable: binary data "+chunk.toString());

				if (cb != null) cb.writeDone(null);

				// decode chunk to string
				String result = Charset.forName("utf8").newDecoder().decode((ByteBuffer)chunk).toString();
				Log.d(TAG, "DummyWritable: decoded string "+result);
			}
		}

	}

	private class DummyDuplex extends Duplex {
		private int _max = 20;
		private int _index = 1;

		public DummyDuplex() {
			super(context, 
				  new Duplex.Options(
						  new Readable2.Options(-1, "utf8", false, "utf8", true), 
				          new Writable2.Options(-1, true, "utf8", false, true), 
				          true));
			// TODO Auto-generated constructor stub

			_index = 1;
		}

		@Override
		public void _read(int size) throws Exception {
			int i = this._index++;
			if (i > this._max)
				this.push(null, null);
			else {
				for (int c = 0; c < 3; c ++) {
					String str = burst + "@大家好" + i+"/"+c+"$";

					ByteBuffer buf = ByteBuffer.wrap(str.getBytes("utf8"));
					if (!this.push(buf, null)) break;
					///if (!this.push(str, "utf8")) break;

					Log.d(TAG, "DummyDuplex: _read "+str);
				}
			}
		}

		@Override
		public void _write(Object chunk, String encoding, WriteCB cb) throws Exception {
			if (Util.isString(chunk)) {
				Log.d(TAG, "DummyDuplex: encdoing "+encoding+":"+chunk.toString());

				if (cb != null) cb.writeDone(null);

			} else {
				Log.d(TAG, "DummyDuplex: binary data "+chunk.toString());

				if (cb != null) cb.writeDone(null);

				// decode chunk to string
				String result = Charset.forName("utf8").newDecoder().decode((ByteBuffer)chunk).toString();
				Log.d(TAG, "DummyDuplex: decoded string "+result);
			}
		}

	}

	private class DoubleTransform extends Transform {

		protected DoubleTransform() {
			super(context, 
				  new Duplex.Options(
						  new Readable2.Options(-1, "", false, "utf8", true), 
				          new Writable2.Options(-1, false, "utf8", false, true),
				          true));
			// TODO Auto-generated constructor stub
		}

		@Override
		protected void _transform(Object chunk, String encoding,
				afterTransformCallback cb) throws Exception {
			if (Util.isBuffer(chunk)) {
				ByteBuffer ori = (ByteBuffer)chunk;
				ByteBuffer dbl = ByteBuffer.allocate(ori.capacity()*2);

				dbl.put(ori); ori.flip();dbl.put(ori); ori.flip();dbl.flip();

				this.push(dbl, encoding);
				cb.afterTransform(null, null);
			} else if (Util.isString(chunk)) {
				String ori = (String)chunk;
				String dbl = ori + ori;
				
				this.push(dbl, encoding);
				cb.afterTransform(null, null);
			} else {
				cb.afterTransform("invalid data "+chunk, null);
			}
		}

		@Override
		protected void _flush(flushCallback cb) throws Exception {
            cb.onFlush(null);
		}

	}

    public void testTransform() {
		final DoubleTransform ts = new DoubleTransform();
		
		try {
			ts.on("readable", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					Object chunk;

					while (null != (chunk = ts.read(-1))) {
						Log.d(TAG, "\n\n\ntestTransform: "+Util.chunkToString(chunk, "utf8"));
					}
					
				}

			});
			
			context.setInterval(new IntervalListener(){

				@Override
				public void onInterval() throws Exception {
					ts.write("double string", "utf-8", null);
				}
				
			}, 2000);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    public void testRead_less() {
		final Readable rs = new Counting();

		try {
			rs.on("readable", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					Object chunk;

					Log.d(TAG, "testRead_less: start...");

					///chunk = rs.read(3);
					while (null != (chunk = rs.read(3))) {
						Log.d(TAG, "testRead_less:"+Util.chunkToString(chunk, "utf8"));
					}

					Log.d(TAG, "testRead_less: ...end");
				}

			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    public void testRead_more() {
		final Readable rs = new Counting();

		try {
			rs.on("readable", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					Object chunk;

					Log.d(TAG, "testRead_more: start...");

					///chunk = rs.read(33);
					while (null != (chunk = rs.read(28))) {
						Log.d(TAG, "testRead_more: "+Util.chunkToString(chunk, "utf8"));
					}

					Log.d(TAG, "testRead_more: ...end");
				}

			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    public void testRead_forever() {
		final Readable rs = new Counting();

		try {
			rs.on("readable", new Listener(){

				@Override
				public void onEvent(Object data) throws Exception {
					Object chunk;

					Log.d(TAG, "testRead_forever: start...");

					while (null != (chunk = rs.read(-1))) {
						Log.d(TAG, "testRead_forever: "+Util.chunkToString(chunk, "utf8"));
					}

					Log.d(TAG, "testRead_forever: ...end");
				}

			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void testPipe() {
		final Readable rs = new Counting();

		final Writable ws = new DummyWritable();

		try {
			ws.on("pipe", new Listener () {
				@Override
				public void onEvent(Object src) throws Exception {
					Log.d(TAG, "testPipe: something is piping into the writer");
					assert(rs.equals(src));
				}

			});
			ws.on("unpipe", new Listener () {
				@Override
				public void onEvent(Object src) throws Exception {
					Log.d(TAG, "testPipe: something has stopped piping into the writer");
					assert(rs.equals(src));
				}

			});

			rs.pipe(ws, true);
			rs.unpipe(ws);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void testDuplex() {
		final Duplex du = new DummyDuplex();

		try {
			du.pipe(du, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    public void testFinish() {
		final Writable ws = new DummyWritable();

		try {
			ws.on("finish", new Listener() {
				@Override
				public void onEvent(Object src) throws Exception {
					Log.d(TAG, "testFinish: all writes are now complete.");
				}
			});

			for (int i = 0; i < 100; i ++) {
				ws.write("hello, #" + i + "!\n", null, new WriteCB() {

					@Override
					public void writeDone(String error) throws Exception {
						Log.d(TAG, "testFinish: write done");
					}

				});
			}

			ws.end("this is the end\n", null, null);    		
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
