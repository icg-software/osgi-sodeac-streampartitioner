package org.sodeac.streampartitioner.example.api;

public interface Events
{
	public static final String PROPERTY_TCP_PORT 					= "TCP_PORT"									;
	public static final String PROPERTY_KEYSPEC 					= "KEYSPEC"										;
	public static final String TOPIC_BASE							= "org/sodeac/streampartitioner/example/"		;
	public static final String TOPIC_REQUEST_START_SERVER 			= TOPIC_BASE + "request/startserver"			;
	public static final String TOPIC_REQUEST_STOP_SERVER 			= TOPIC_BASE + "request/stoptserver"			;
	public static final String TOPIC_REQUEST_NOTIFY_SERVER_STATE 	= TOPIC_BASE + "request/notifyserverstate"		;
	public static final String TOPIC_NOTIFY_START_SERVER 			= TOPIC_BASE + "notify/startserver"				;
	public static final String TOPIC_NOTIFY_STOP_SERVER	 			= TOPIC_BASE + "notify/stoptserver"				;
}
