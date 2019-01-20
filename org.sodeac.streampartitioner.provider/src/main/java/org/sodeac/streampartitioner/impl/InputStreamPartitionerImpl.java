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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.ISubStreamListener;

/**
 * 
 * Implementation of {@link org.sodeac.streampartitioner.api.IInputStreamPartitioner}
 * 
 * @author Sebastian Palarus
 *
 */
public class InputStreamPartitionerImpl implements IInputStreamPartitioner
{
	protected StreamPartitionerFactoryImpl 			streamPartitionerFactory 			= null;
	protected InputStream 							parentInputStream 					= null;
	protected byte[] 								carryout 							= null;
	protected List<ISubStreamListener> 				payloadPartFinishedListenerList 	= null;
	protected String 								partId 								= null;
	
	protected ReentrantLock							lockCreate							= null;
	protected ReentrantReadWriteLock 				lockFinishListener 					= null;
	protected ReadLock 								readLockFinishListener 				= null;
	protected WriteLock 							writeLockFinishListener 			= null;
	
	/**
	 * 
	 * @param parentInputStream {@link java.io.InputStream} provides substreams
	 * @param streamPartitionerFactory factory creates this object
	 */
	public InputStreamPartitionerImpl(InputStream parentInputStream, StreamPartitionerFactoryImpl streamPartitionerFactory)
	{
		super();
		this.parentInputStream 			= parentInputStream						;
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
	public InputStream getParentInputStream()
	{
		return parentInputStream;
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
	public IInputStreamPartitioner setPartId(String partId)
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
	public InputStream getNextSubInputStream() throws IOException
	{
		InputStream substream = this.streamPartitionerFactory.createInputSubStream(this);
		this.partId = UUID.randomUUID().toString();
		return substream;
	}

	/**
	 * 
	 * @param carryout too much readed bytes by last substream
	 */
	public void setCarryOut(byte[] carryout)
	{
		this.carryout = carryout;
	}

	/**
	 * 
	 * @return  too much readed bytes by last substream
	 */
	public byte[] getCarryOut()
	{
		return this.carryout;
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
