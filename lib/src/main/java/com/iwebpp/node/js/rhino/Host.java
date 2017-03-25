// Copyright (c) 2014 Tom Zhou<iwebpp@gmail.com>


package com.iwebpp.node.js.rhino;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.iwebpp.SimpleDebug;
import com.iwebpp.node.NodeContext;
import com.iwebpp.node.js.JS;
import com.iwebpp.nodeandroid.Toaster;

/*
 * @description
 *   NodeJS host env implementation with Rhino, 
 *   https://developer.mozilla.org/en-US/docs/Mozilla/Projects/Rhino/Embedding_tutorial
 *   Notes:
 *     the internal nodejs module has been imported in JS standard jsscope, 
 *     just use it, like http, httpp, TCP, UDT, Dns, Url, Readable2, Writable2, etc
 *   
 * */
public abstract class Host 
extends SimpleDebug 
implements JS {

	private final static String TAG = "RhinoHost";

	private final NodeContext nodectx; // node.js native context

	private Context jsctx; // js context

	private ScriptableObject jsscope;
	
	private boolean onceRun; // have-only run once

	public Host() {
		nodectx = new NodeContext();
	}

	@Override
	public NodeContext getNodeContext() {
		return nodectx;
	}
	
	@Override
	/* 
	 * @description 
	 *   NodeJS like require
	 * */
	public Object require(String module) throws Exception {
		Object ret = Scriptable.NOT_FOUND;
		
		info(TAG, "require path: "+module);
		
		// Retrieve script source locally by file path, or remotely by URL
		// TBD...
		String modulesrc = "exports.modulepath=" + "'" + module + "';";
		
		debug(TAG, "module source: "+modulesrc);

		// Entering Module Context
		Context subctx = Context.enter();

		// Turn off optimization to make Rhino Android compatible
		// not use dex, just java bytecode
		subctx.setOptimizationLevel(-1);
		
		// Initializing standard objects
		ScriptableObject subscope = subctx.initStandardObjects();

		try {
		    // Expose host env in js as NodeHostEnv alias as NHE
		    ScriptableObject.putProperty(subscope, "NodeHostEnv", Context.javaToJS(this, subscope));

		    // Expose node-android context in js as NodeCurrentContext alias as NCC
		    ScriptableObject.putProperty(subscope, "NodeCurrentContext", Context.javaToJS(nodectx, subscope));

		    // Create exports variable
		    String exports = "var exports = {};";
		    subctx.evaluateString(subscope, exports, "ExportsVariable", 1, null);
		    
		    // Create require function
		    // - return built-in module directly with NodeCurrentContext injection
		    // - ask framework for local/remote Js module, TBD ... security/permission check
		    String require = 
		    		"var require = function(module){" +
		    		"    if (module.toLowerCase() === 'http')  return {" +
		    		"        createServer: function(requestListener) {" +
		    		"            return http.createServer(NCC, requestListener);" +
		    		"        }," +
		    		"" +
		    		"        request: function(options, responseListener) {" +
		    		"            // parse ReqOptions" +
		    		"            ReqOptions reqopt = new ReqOptions();" +
		    		"            return http.request(NCC, reqopt, responseListener);" +
		    		"        }," +
		    		"" +
		    		"        request: function(url, responseListener) {" +
		    		"            return http.request(NCC, url, responseListener);" +
		    		"        }," +
		    		"" +
		    		"        get: function(options, responseListener) {" +
		    		"            // parse ReqOptions" +
		    		"            ReqOptions reqopt = new ReqOptions();" +
		    		"            return http.get(NCC, reqopt, responseListener);" +
		    		"        }," +
		    		"" +
		    		"        get: function(url, responseListener) {" +
		    		"            return http.get(NCC, url, responseListener);" +
		    		"        }," +
		    		"    };" +
		    		"" +
		    		"    if (module.toLowerCase() === 'httpp')  return {" +
		    		"        createServer: function(requestListener) {" +
		    		"            return httpp.createServer(NCC, requestListener);" +
		    		"        }," +
		    		"" +
		    		"        request: function(options, responseListener) {" +
		    		"            // parse ReqOptions" +
		    		"            ReqOptions reqopt = new ReqOptions();" +
		    		"            return httpp.request(NCC, reqopt, responseListener);" +
		    		"        }," +
		    		"" +
		    		"        request: function(url, responseListener) {" +
		    		"            return httpp.request(NCC, url, responseListener);" +
		    		"        }," +
		    		"" +
		    		"        get: function(options, responseListener) {" +
		    		"            // parse ReqOptions" +
		    		"            ReqOptions reqopt = new ReqOptions();" +
		    		"            return http.get(NCC, reqopt, responseListener);" +
		    		"        }," +
		    		"" +
		    		"        get: function(url, responseListener) {" +
		    		"            return httpp.get(NCC, url, responseListener);" +
		    		"        }," +
		    		"    };" +
		    		"" +
		    		"    if (module.toLowerCase() === 'websocket')       return Websocket;" +
		    		"    if (module.toLowerCase() === 'websocketserver') return WebSocketServer;" +

		    		"" +
		    		"    if (module.toLowerCase() === 'net') return TCP;" +
		    		"    if (module.toLowerCase() === 'tcp') return TCP;" +
		    		"    if (module.toLowerCase() === 'udt') return UDT;" +
		    		"" +
		    		"    if (module.toLowerCase() === 'readable')    return Readable2;" +
		    		"    if (module.toLowerCase() === 'writable')    return Writable2;" +
		    		"    if (module.toLowerCase() === 'duplex')      return Duplex;" +
		    		"    if (module.toLowerCase() === 'transform')   return Transform;" +
		    		"    if (module.toLowerCase() === 'passthrough') return PassThrough;" +
		    		"" +
		    		"    if (module.toLowerCase() === 'dns') return Dns;" +
		    		"    if (module.toLowerCase() === 'url') return Url;" +

		    		"" +
		    		"    return NodeHostEnv.require(module);" +
		    		"};";
		    subctx.evaluateString(subscope, require, "RequireFunction", 1, null);

		    // Expose node-android API in js
		    String nodejs = "var NodeJS = new JavaImporter(" +
                            "com.iwebpp.node.EventEmitter2," +
                            "com.iwebpp.node.Dns," +
                            "com.iwebpp.node.Url," +
                            "com.iwebpp.node.http," +
                            "com.iwebpp.node.net," +
                            "com.iwebpp.node.stream," +
                            "com.iwebpp.wspp.WebSocket," +
                            "com.iwebpp.wspp.WebSocketServer," +
                            "android.util.Log" +
                            ");";
		    subctx.evaluateString(subscope, nodejs, "NodeJSAPI", 1, null);
		    
			// Evaluating module script source in one line
		    String modulescript = ("with(NodeJS){(function(){var NCC=NodeCurrentContext;" + modulesrc + "})();}").replace("[\r\n]+", "");

		    ///DebugLevel lvl = getDebugLevel();
		    ///setDebugLevel(DebugLevel.INFO);
		    info(TAG, "module script: \n\n"+modulescript+"\n\n");
		    ///setDebugLevel(lvl);
		    
		    subctx.evaluateString(subscope, modulescript, "ModuleContent", 1, null);
		    
		    // Retrieve exports variable
		    ret = subscope.get("exports", subscope);
		} catch (Throwable e) {			
			///e.printStackTrace();
			
			error(TAG, e.toString());
			
			throw new Exception("Rhino require exception: "+e.toString());
		} finally {
			Context.exit();
		}

		return ret;
	}
	
	@Override
	/* 
	 * @description 
	 *   NodeJS like require, TBD...
	 * */
	public void require(String module, RequireCallback cb) throws Exception {
		Object ret = Scriptable.NOT_FOUND;
		
		info(TAG, "require path: "+module);

		cb.onResponse(ret);
	}
	
	@Override
	/*
	 * @description
	 *   refer to https://developer.mozilla.org/en-US/docs/Mozilla/Projects/Rhino/Embedding_tutorial#runScript
	 * */
	public void execute() throws Exception {	
		// Check if run once
		if (onceRun) 
			return;
		else 
			onceRun = true;
		
		
		// Entering a Context
		jsctx = Context.enter();

		// Turn off optimization to make Rhino Android compatible
		jsctx.setOptimizationLevel(-1);

		try {
			// Initializing standard objects
			jsscope = jsctx.initStandardObjects();

            // Add Toaster
            jsscope.defineProperty("toast",
                     Toaster.getInstance(),
                     ScriptableObject.DONTENUM);

            // Expose host env in js as NodeHostEnv alias as NHE
		    ScriptableObject.putProperty(jsscope, "NodeHostEnv", Context.javaToJS(this, jsscope));

			// Expose node-android context in js as NodeCurrentContext alias as NCC
		    ScriptableObject.putProperty(jsscope, "NodeCurrentContext", Context.javaToJS(nodectx, jsscope));

		    // Create exports variable
		    String exports = "var exports = {};";
		    jsctx.evaluateString(jsscope, exports, "ExportsVariable", 1, null);
		    
		    // Create require function
		    String require = 
		    		"var require = function(module){" +
		    		"    if (module.toLowerCase() === 'http')  return http;" +
		    		"    if (module.toLowerCase() === 'httpp') return httpp;" +
		    		"" +
		    		"    if (module.toLowerCase() === 'websocket')       return Websocket;" +
		    		"    if (module.toLowerCase() === 'websocketserver') return WebSocketServer;" +

		    		"" +
		    		"    if (module.toLowerCase() === 'net') return TCP;" +
		    		"    if (module.toLowerCase() === 'tcp') return TCP;" +
		    		"    if (module.toLowerCase() === 'udt') return UDT;" +
		    		"" +
		    		"    if (module.toLowerCase() === 'readable')    return Readable2;" +
		    		"    if (module.toLowerCase() === 'writable')    return Writable2;" +
		    		"    if (module.toLowerCase() === 'duplex')      return Duplex;" +
		    		"    if (module.toLowerCase() === 'transform')   return Transform;" +
		    		"    if (module.toLowerCase() === 'passthrough') return PassThrough;" +
		    		"" +
		    		"    if (module.toLowerCase() === 'dns') return Dns;" +
		    		"    if (module.toLowerCase() === 'url') return Url;" +

		    		"" +
		    		"    return NodeHostEnv.require(module);" +
		    		"};";
		    jsctx.evaluateString(jsscope, require, "RequireFunction", 1, null);

		    // Expose node-android API in js
		    String nodejs = "var NodeJS = new JavaImporter(" +
                            "com.iwebpp.node.EventEmitter2," +
                            "com.iwebpp.node.Dns," +
                            "com.iwebpp.node.Url," +
                            "com.iwebpp.node.http," +
                            "com.iwebpp.node.net," +
                            "com.iwebpp.node.stream," +
                            "com.iwebpp.wspp.WebSocket," +
                            "com.iwebpp.wspp.WebSocketServer," +
                            "android.util.Log" +
                            ");";
		    jsctx.evaluateString(jsscope, nodejs, "NodeJSAPI", 1, null);
		    
			// Evaluating user authored script in one line
		    String userscript = ("with(NodeJS){(function(){var NCC=NodeCurrentContext;" + content() + "})();}").replace("[\r\n]+", "");

		    ///DebugLevel lvl = getDebugLevel();
		    ///setDebugLevel(DebugLevel.INFO);
		    info(TAG, "user script: \n\n"+userscript+"\n\n");
		    ///setDebugLevel(lvl);
		    
		    jsctx.evaluateString(jsscope, userscript, "UserContent", 1, null);

			// Run node-android loop
			nodectx.execute();
		} catch (Throwable e) {			
			///e.printStackTrace();
			
			error(TAG, e.toString());
			
			throw new Exception("Rhino runtime exception: "+e.toString());
		} finally {
			Context.exit();
		}
	}

}
