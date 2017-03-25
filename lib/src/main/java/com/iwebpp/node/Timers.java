// Copyright (c) 2014 Tom Zhou<iwebpp@gmail.com>


package com.iwebpp.node;

public final class Timers extends EventEmitter2 {
	private NodeContext context;

	/**
	 * @return the context
	 */
	public NodeContext getContext() {
		return context;
	}

	public static void _unrefActive(Object item) {
		
	}

	public static void unenroll(Object item) {
		
	}

	public static void enroll(Object item, int msecs) {
		
	}
	
	public Timers(final NodeContext ctx) {
		this.context = ctx;
	}
	@SuppressWarnings("unused")
	private Timers(){}

}
