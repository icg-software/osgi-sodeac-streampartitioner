/*******************************************************************************
 * Copyright (c) 2017, 2019 Sebastian Palarus
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Sebastian Palarus - initial API and implementation
 *******************************************************************************/
package org.sodeac.streampartitioner.example.impl;

import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.spec.SecretKeySpec;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.sodeac.streampartitioner.api.IStreamPartitionerFactory;
import org.sodeac.streampartitioner.example.api.Events;

@Component
(
	service=EventHandler.class,
	immediate=true,
	property=
	{
		EventConstants.EVENT_TOPIC+"=" + Events.TOPIC_REQUEST_START_SERVER,
		EventConstants.EVENT_TOPIC+"=" + Events.TOPIC_REQUEST_STOP_SERVER,
		EventConstants.EVENT_TOPIC+"=" + Events.TOPIC_REQUEST_NOTIFY_SERVER_STATE
	}
)
public class EchoServer implements EventHandler
{
	private volatile ComponentContext context = null;
	
	@Reference
	private EventAdmin eventAdmin;
	
	@Reference
	private volatile LogService logService = null;
	
	@Reference
	private volatile IStreamPartitionerFactory streamPartitionerFactory = null;
	
	private ReentrantLock lockStartStop = null;
	private volatile ServerSocket socket = null;
	private volatile boolean listen = true;
	private SecretKeySpec keySpec = null;
	
	public EchoServer()
	{
		super();
		
		this.lockStartStop	= new ReentrantLock(true);
		
		try
		{
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.reset();
			md5.update(UUID.randomUUID().toString().getBytes());
			String md5uuid = new BigInteger(1,md5.digest()).toString();
			byte[] key = md5uuid.getBytes();
			key = Arrays.copyOf(key, 16);
			
			this.keySpec = new SecretKeySpec(key,"AES");
		}
		catch (Exception e) {}
	}
	
	@Activate
	private void activate(ComponentContext context, Map<String, ?> properties)
	{
		this.context = context;
	}
	
	@Deactivate
	private void deactivate(ComponentContext context)
	{
		this.listen = false;
		try
		{
			socket.close();
		}
		catch (Exception e) {}
		socket = null;
		this.context = null;
	}
	
	@Override
	public void handleEvent(Event event)
	{
		if(event.getTopic().equals(Events.TOPIC_REQUEST_START_SERVER))
		{
			if(event.getProperty(Events.PROPERTY_TCP_PORT) == null)
			{
				return;
			}
			if(! (event.getProperty(Events.PROPERTY_TCP_PORT) instanceof Integer))
			{
				return;
			}
			
			startServer((Integer)event.getProperty(Events.PROPERTY_TCP_PORT));
		}
		if(event.getTopic().equals(Events.TOPIC_REQUEST_STOP_SERVER))
		{
			if(event.getProperty(Events.PROPERTY_TCP_PORT) == null)
			{
				return;
			}
			if(! (event.getProperty(Events.PROPERTY_TCP_PORT) instanceof Integer))
			{
				return;
			}
			
			stopServer((Integer)event.getProperty(Events.PROPERTY_TCP_PORT));
		}
		if(event.getTopic().equals(Events.TOPIC_REQUEST_NOTIFY_SERVER_STATE))
		{
			try
			{
				this.lockStartStop.lock();
				
				if((this.socket == null) || (!listen))
				{
					Event startServerEvent = new Event(Events.TOPIC_NOTIFY_STOP_SERVER,(Dictionary<String,Object>)new Hashtable<String,Object>());
					eventAdmin.postEvent(startServerEvent);
					return;
				}
				
				Dictionary<String, Object> properties = new Hashtable<String,Object>();
				properties.put(Events.PROPERTY_TCP_PORT, this.socket.getLocalPort());
				properties.put(Events.PROPERTY_KEYSPEC, this.keySpec);
				Event startServerEvent = new Event(Events.TOPIC_NOTIFY_START_SERVER,properties);
				eventAdmin.postEvent(startServerEvent);
			}
			catch (Exception e) 
			{
				logService.log(this.context.getServiceReference(), LogService.LOG_ERROR, "Error on notify server state", e);
			}
			finally 
			{
				this.lockStartStop.unlock();
			}
		}
	}
	
	private void startServer(int port)
	{
		try
		{
			this.lockStartStop.lock();
			
			if(this.socket != null)
			{
				return;
			}
			
			socket = new ServerSocket(port);
			
			listen = true;
			
			CountDownLatch latch = new CountDownLatch(1);
			Thread tcpServerThread = new Thread(getClass().getName()) 
			{
				
				@Override
				public void run()
				{
					ServerSocket serverSocket = EchoServer.this.socket;
					latch.countDown();
					try
					{
						while(EchoServer.this.listen)
						{
							new EchoServerConnection().init(EchoServer.this.streamPartitionerFactory, serverSocket.accept(),keySpec).start();
						}
						
					}
					catch (SocketException e)
					{
						// don't log
					}
					catch (Exception e) 
					{
						logService.log(EchoServer.this.context.getServiceReference(), LogService.LOG_ERROR, "Error on open tcp server socket connection", e);
					}
					finally 
					{
						if(! EchoServer.this.listen)
						{
							try{EchoServer.this.socket.close();}catch (Exception e2) {}
						}
					}
				}
				
			};
			tcpServerThread.setDaemon(true);
			tcpServerThread.start();
			
			try
			{
				if(! latch.await(13, TimeUnit.SECONDS))
				{
					socket = null;
					listen = false;
					return ;
				}
			}
			catch (Exception ie) {}
		}
		catch (Exception e) 
		{
			logService.log(this.context.getServiceReference(), LogService.LOG_ERROR, "Error on open tcp server socket", e);
		}
		finally 
		{
			this.lockStartStop.unlock();
		}
		
		Dictionary<String, Object> properties = new Hashtable<String,Object>();
		properties.put(Events.PROPERTY_TCP_PORT, port);
		properties.put(Events.PROPERTY_KEYSPEC, this.keySpec);
		Event startServerEvent = new Event(Events.TOPIC_NOTIFY_START_SERVER,properties);
		eventAdmin.postEvent(startServerEvent);
	}
	
	private void stopServer(int port)
	{
		try
		{
			this.lockStartStop.lock();
			
			if(this.socket == null)
			{
				return;
			}
			
			if(this.socket.getLocalPort() != port)
			{
				return;
			}
			
			this.listen = false;
			socket.close();
			socket = null;
		}
		catch (Exception e) 
		{
			logService.log(this.context.getServiceReference(), LogService.LOG_ERROR, "Error on close tcp server socket", e);
		}
		finally 
		{
			this.listen = false;
			socket = null;
			this.lockStartStop.unlock();
		}
		
		Dictionary<String, Object> properties = new Hashtable<String,Object>();
		properties.put(Events.PROPERTY_TCP_PORT, port);
		Event startServerEvent = new Event(Events.TOPIC_NOTIFY_STOP_SERVER,properties);
		eventAdmin.postEvent(startServerEvent);
	}
	
}
