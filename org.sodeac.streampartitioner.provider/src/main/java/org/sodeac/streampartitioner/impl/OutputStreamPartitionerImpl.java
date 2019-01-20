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
package org.sodeac.streampartitioner.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;
import org.sodeac.streampartitioner.api.ISubStreamListener;

/**
 * 
 * Implementation of {@link org.sodeac.streampartitioner.api.IOutputStreamPartitioner}
 * 
 * 
 * @author Sebastian Palarus
 *
 */
public class OutputStreamPartitionerImpl implements IOutputStreamPartitioner
{
	protected StreamPartitionerFactoryImpl 			streamPartitionerFactory 			= null;
	protected OutputStream 							parentOutputStream 					= null;
	protected List<ISubStreamListener> 				payloadPartFinishedListenerList 	= null;
	protected String 								partId 								= null;
	
	protected ReentrantLock							lockCreate							= null;
	protected ReentrantReadWriteLock 				lockFinishListener 					= null;
	protected ReadLock 								readLockFinishListener 				= null;
	protected WriteLock 							writeLockFinishListener 			= null;
	
	/**
	 * 
	 * @param parentOutputStream {@link java.io.OutputStream} shall to parted in substreams
	 * @param streamPartitionerFactory
	 */
	public OutputStreamPartitionerImpl(OutputStream parentOutputStream, StreamPartitionerFactoryImpl streamPartitionerFactory)
	{
		super();
		this.parentOutputStream			= parentOutputStream					;
		this.streamPartitionerFactory 	= streamPartitionerFactory				;
		
		this.lockCreate					= new ReentrantLock(true)				;
		this.lockFinishListener 		= new ReentrantReadWriteLock(true)		;
		this.readLockFinishListener 	= this.lockFinishListener.readLock()	;
		this.writeLockFinishListener 	= this.lockFinishListener.writeLock()	;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public OutputStream getParentOutputStream()
	{
		return parentOutputStream;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addSubStreamListener(ISubStreamListener payloadPartFinishedListener)
	{
		if(payloadPartFinishedListener == null)
		{
			return;
		}
		
		if(this.payloadPartFinishedListenerList ==  null)
		{
			try
			{
				writeLockFinishListener.lock();
				if(this.payloadPartFinishedListenerList ==  null)
				{
					this.payloadPartFinishedListenerList = new ArrayList<ISubStreamListener>();
				}
			}
			finally 
			{
				writeLockFinishListener.unlock();
			}
		}
		
		try
		{
			writeLockFinishListener.lock();
			while(this.payloadPartFinishedListenerList.remove(payloadPartFinishedListener)){}
			this.payloadPartFinishedListenerList.add(payloadPartFinishedListener);
		}
		finally 
		{
			writeLockFinishListener.unlock();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeSubStreamListener(ISubStreamListener payloadPartFinishedListener)
	{
		try
		{
			writeLockFinishListener.lock();
			if(this.payloadPartFinishedListenerList == null)
			{
				return;
			}
			while(this.payloadPartFinishedListenerList.remove(payloadPartFinishedListener)){}
		}
		finally 
		{
			writeLockFinishListener.unlock();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOutputStreamPartitioner setPartId(String partId)
	{
		this.partId = partId;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getPartId()
	{
		return this.partId;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public OutputStream createNextSubOutputStream() throws IOException
	{
		OutputStream substream = this.streamPartitionerFactory.createOutputSubStream(this);
		this.partId = UUID.randomUUID().toString();
		return substream;
	}
	
	/**
	 * Handle eventsystem of {@link org.sodeac.streampartitioner.api.ISubStreamListener}
	 */
	public void fireSubStreamCloseEvent()
	{
		List<ISubStreamListener> fireList =  null;
		try
		{
			readLockFinishListener.lock();
			if(this.payloadPartFinishedListenerList == null)
			{
				return;
			}
			if(this.payloadPartFinishedListenerList.isEmpty())
			{
				return;
			}
			fireList = new ArrayList<ISubStreamListener>();
			fireList.addAll(this.payloadPartFinishedListenerList);
		}
		finally 
		{
			readLockFinishListener.unlock();
		}
		
		for(ISubStreamListener payloadPartFinishedListener : fireList)
		{
			try
			{
				payloadPartFinishedListener.onClose(this);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}	
	}
	
	/**
	 * 
	 * @return SharedLock to synchronize activities of partitioner outside of this class 
	 */
	protected ReentrantLock getLockCreate()
	{
		return lockCreate;
	}
}
