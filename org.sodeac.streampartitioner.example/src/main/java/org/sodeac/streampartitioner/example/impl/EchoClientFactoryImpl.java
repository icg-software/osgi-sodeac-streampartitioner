/*******************************************************************************
 * Copyright (c) 2017 Sebastian Palarus
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Sebastian Palarus - initial API and implementation
 *******************************************************************************/
package org.sodeac.streampartitioner.example.impl;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

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
import org.sodeac.streampartitioner.api.IStreamPartitionerFactory;
import org.sodeac.streampartitioner.example.api.Events;
import org.sodeac.streampartitioner.example.api.IEchoClient;
import org.sodeac.streampartitioner.example.api.IEchoClientFactory;
import org.sodeac.streampartitioner.example.api.ServerNotRunningException;

@Component
(
	property={EventConstants.EVENT_TOPIC+"=" + Events.TOPIC_NOTIFY_START_SERVER,EventConstants.EVENT_TOPIC+"=" + Events.TOPIC_NOTIFY_STOP_SERVER},
	service={IEchoClientFactory.class,EventHandler.class}
)
public class EchoClientFactoryImpl implements IEchoClientFactory,EventHandler
{
	private volatile SecretKeySpec keySpec = null; 
	private volatile Integer knownPort = null;
	
	@Reference
	private EventAdmin eventAdmin;
	
	@Reference
	private volatile IStreamPartitionerFactory streamPartitionerFactory = null;
	
	@Activate
	private void activate(ComponentContext context, Map<String, ?> properties)
	{	
		Event startServerEvent = new Event(Events.TOPIC_REQUEST_NOTIFY_SERVER_STATE,(Dictionary<String,Object>)new Hashtable<String,Object>());
		eventAdmin.postEvent(startServerEvent);
	}
	
	@Deactivate
	private void deactivate(ComponentContext context)
	{
		this.knownPort = null;
		this.keySpec = null;
	}

	@Override
	public IEchoClient createEchoClient() throws ServerNotRunningException
	{
		if(this.knownPort ==  null)
		{
			throw new ServerNotRunningException();
		}
		return new EchoClientImpl(this.knownPort,streamPartitionerFactory,keySpec);
	}

	@Override
	public void handleEvent(Event event)
	{
		if(event.getTopic().equals(Events.TOPIC_NOTIFY_STOP_SERVER))
		{
			this.knownPort = null;
		}
		if(event.getTopic().equals(Events.TOPIC_NOTIFY_START_SERVER))
		{
			this.knownPort = (Integer)event.getProperty(Events.PROPERTY_TCP_PORT);
			this.keySpec = (SecretKeySpec)event.getProperty(Events.PROPERTY_KEYSPEC);
		}
	}

}
