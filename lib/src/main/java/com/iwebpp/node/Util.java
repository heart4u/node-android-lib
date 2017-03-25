// Copyright (c) 2014 Tom Zhou<iwebpp@gmail.com>


package com.iwebpp.node;

import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.List;

import com.iwebpp.libuvpp.LibUV;

public final class Util {
	@SuppressWarnings("unused")
	private static final String TAG = "Util";
	
	// Buffer
    public static boolean isBuffer(Object chunk) {
    	return chunk instanceof ByteBuffer;
    } 
    
    public static boolean isString(Object chunk) {
    	return chunk instanceof String;
    } 

    public static boolean isNullOrUndefined(Object obj) {
    	return obj == null;
    }

    public static boolean isUndefined(Object obj) {
    	return obj == null;
    }

    public static boolean isNull(Object obj) {
    	return obj == null;
    }
    
    public static int chunkLength(Object chunk) {
    	if (isBuffer(chunk)) {
    		ByteBuffer bb = (ByteBuffer)chunk;
    		return bb.capacity();
    	}
    	
    	if (isString(chunk)) {
    		String s = (String)chunk;
    		return s.length();
    	}
    	
		return 0;
    } 
    
    public static int stringByteLength(String chunk, String encoding) throws UnsupportedEncodingException {
    	if (isString(chunk))
    		return chunk.getBytes(encoding).length;
    	
		return 0;
    } 
    
    public static int chunkByteLength(Object chunk, String encoding) throws UnsupportedEncodingException {
    	if (isBuffer(chunk)) {
    		ByteBuffer bb = (ByteBuffer)chunk;
    		return bb.capacity();
    	}
    	
    	if (isString(chunk)) {
    		return stringByteLength((String) chunk, encoding);
    	}
    	
		return 0;
    } 
    
    public static Object chunkSlice(Object chunk, int start, int end) {
    	if (isBuffer(chunk)) {
    		ByteBuffer bb = (ByteBuffer)chunk;
    		int opos = bb.position(); bb.position(start);
    		int olmt = bb.limit(); bb.limit(end);
    		
			ByteBuffer mb = bb.slice();
			bb.limit(olmt); bb.position(opos);
			
			///ByteBuffer rb = ByteBuffer.allocate(mb.capacity());
			///rb.put(mb); rb.flip();
			
			return mb;
    	}
    	
    	if (isString(chunk)) {
    		String s = (String)chunk;
    		
    		return s.substring(start, end);
    	}
    	
    	return null;
    }
    
    public static Object chunkSlice(Object chunk, int start) {
    	if (isBuffer(chunk)) {
    		ByteBuffer bb = (ByteBuffer)chunk;
    		int opos = bb.position(); bb.position(start);
    		
    		ByteBuffer mb = bb.slice();
			bb.position(opos);
			
			///ByteBuffer rb = ByteBuffer.allocate(mb.capacity());
			///rb.put(mb); rb.flip();
			
			return mb;
    	}
    	
    	if (isString(chunk)) {
    		String s = (String)chunk;
    		
    		return s.substring(start, s.length());
    	}
    	
    	return null;
    }

    public static boolean zeroString(String s) {
        return TextUtils.isEmpty(s);
    }
    
    public static ByteBuffer concatByteBuffer(List<Object> list, int length) throws Exception {
    	if (length <= 0) {
    		length = 0;
    		
    		for (Object b : list)
    			if (b instanceof ByteBuffer || b instanceof String)
    				length += chunkByteLength(b, "utf-8");
    			else
    				throw new Exception("MUST be ByteBuffer or String:"+b);
    	}
    		
    	if (length > 0) {
    		ByteBuffer bb = ByteBuffer.allocate(length);

    		for (Object b : list) {
    			if (b instanceof ByteBuffer) {
    				bb.put((ByteBuffer)b);

    				((ByteBuffer)b).flip();
    			} else if (b instanceof String) {
    				bb.put(chunkToBuffer(b, "utf-8"));
    			} else
    				throw new Exception("MUST be ByteBuffer or String:"+b);
    		}
    		bb.flip();
    		
    		return bb;
    	} else 
    		return null;
    }
    
    public static ByteBuffer concatByteBuffer(List<ByteBuffer> list) {
    	int length = 0;

    	for (ByteBuffer b : list) length += b.capacity();

    	if (length > 0) {
    		ByteBuffer bb = ByteBuffer.allocate(length);

    		for (ByteBuffer b : list) {
    			bb.put(b);

    			b.flip();
    		}
    		bb.flip();

    		return bb;
    	} else 
    		return null;
    }

    public static String chunkToString(Object chunk, String encoding) throws CharacterCodingException {
    	if (isString(chunk)) {			
    		return (String) chunk;			
    	} else if (isBuffer(chunk)) {
    		// decode chunk to string
    		return Charset.forName(encoding).newDecoder().decode((ByteBuffer)chunk).toString();
    	}

    	return "invalid chunk";
    }

    public static ByteBuffer chunkToBuffer(Object chunk, String encoding) throws UnsupportedEncodingException {
    	if (isString(chunk)) {			
    		String s = (String)chunk;
    		return ByteBuffer.wrap(s.getBytes(encoding));			
    	} else if (isBuffer(chunk)) {
    		return (ByteBuffer) chunk;
    	}

    	return null;
    }

    public static boolean isIPv4(String ip) {
    	return ip!=null && LibUV.isIPv4(ip);
    }

    public static boolean isIPv6(String ip) {
    	return ip!=null && LibUV.isIPv6(ip);
    }

    public static boolean isIP(String ip) {
    	return isIPv6(ip) || isIPv4(ip);
    }

    public static int ipFamily(String ip) {
    	if (isIPv4(ip)) return 4;
    	else
    	if (isIPv6(ip)) return 6;
    	else

    	return 4;///-1;
    }
    
}
