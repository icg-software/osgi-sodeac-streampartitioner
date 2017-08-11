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
package org.sodeac.streampartitioner.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.log.LogService;
import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;
import org.sodeac.streampartitioner.api.IStreamPartitioner;
import org.sodeac.streampartitioner.api.IStreamPartitionerFactory;

/**
 * 
 * Implementation of {@link org.sodeac.streampartitioner.api.IStreamPartitionerFactory}
 * 
 * @author Sebastian Palarus
 *
 */
@Component(name="StreamPartitionerFactory",service=IStreamPartitionerFactory.class)
public class StreamPartitionerFactoryImpl implements IStreamPartitionerFactory
{
	public StreamPartitionerFactoryImpl()
	{
		super();
		
		this.lockUnclosedInputStreamPartitioner 		= new ReentrantReadWriteLock(true)		;
		this.readLockUnclosedInputStreamPartitioner 	= this.lockUnclosedInputStreamPartitioner.readLock()	;
		this.writeLockUnclosedInputStreamPartitioner 	= this.lockUnclosedInputStreamPartitioner.writeLock()	;
		
		this.lockUnclosedOutputStreamPartitioner 		= new ReentrantReadWriteLock(true)		;
		this.readLockUnclosedOutputStreamPartitioner 	= this.lockUnclosedOutputStreamPartitioner.readLock()	;
		this.writeLockUnclosedOutputStreamPartitioner 	= this.lockUnclosedOutputStreamPartitioner.writeLock()	;
	}
	
	public static final String OFFSET_MARK = "X." + IStreamPartitioner.class.getSimpleName().toUpperCase() + "_MARK";
	public static final String END_MARK = "E_._ND";
	
	private Map<InputStreamPartitionerImpl,FindMarkInputStream> 		unclosedInputStreamPartitioner	= new HashMap<InputStreamPartitionerImpl,FindMarkInputStream>()			;
	private Map<OutputStreamPartitionerImpl,CloseDelegateOutputStream> 	unclosedOutputStreamPartitioner	= new HashMap<OutputStreamPartitionerImpl,CloseDelegateOutputStream>()	;
	
	protected ReentrantReadWriteLock 		lockUnclosedInputStreamPartitioner 					= null;
	protected ReadLock 						readLockUnclosedInputStreamPartitioner 				= null;
	protected WriteLock 					writeLockUnclosedInputStreamPartitioner 			= null;
	
	protected ReentrantReadWriteLock 		lockUnclosedOutputStreamPartitioner 				= null;
	protected ReadLock 						readLockUnclosedOutputStreamPartitioner 			= null;
	protected WriteLock 					writeLockUnclosedOutputStreamPartitioner 			= null;
	
	@Reference(cardinality=ReferenceCardinality.OPTIONAL,policy=ReferencePolicy.DYNAMIC)
	private volatile LogService logService = null;
	
	private volatile ComponentContext context = null;
	
	private AtomicLong counter = new AtomicLong();
	
	@Activate
	private void activate(ComponentContext context, Map<String, ?> properties)
	{
		this.context = context;
	}
	
	@Deactivate
	private void deactivate(ComponentContext context)
	{
		this.context = null;
	}
	
	/**
	 * 
	 * @param outputStreamPartitioner invoker
	 * @return new substream embed in parentstream
	 * @throws IOException
	 */
	protected OutputStream createOutputSubStream(OutputStreamPartitionerImpl outputStreamPartitioner) throws IOException
	{
		OutputStream os = null;
		
		try
		{
			outputStreamPartitioner.getLockCreate().lock();
			
			try
			{
				readLockUnclosedOutputStreamPartitioner.lock();
				if(unclosedOutputStreamPartitioner.containsKey(outputStreamPartitioner))
				{
					throw new IOException("partitioner has unclosed streams");
				}	
			}
			finally 
			{
				readLockUnclosedOutputStreamPartitioner.unlock();
			}
				
			
			OutputStream parentOutputStream = outputStreamPartitioner.getParentOutputStream();
			String mark = OFFSET_MARK + "_" + UUID.randomUUID().toString() + "_" + counter.incrementAndGet() + END_MARK;
			parentOutputStream.write(mark.getBytes());
			OuputStreamPackagerEndMark ospem = new OuputStreamPackagerEndMark(outputStreamPartitioner,parentOutputStream,mark);
			CloseDelegateOutputStream cdos = new CloseDelegateOutputStream(parentOutputStream,ospem);
			
			try
			{
				writeLockUnclosedOutputStreamPartitioner.lock();
				unclosedOutputStreamPartitioner.put(outputStreamPartitioner, cdos);
			}
			finally 
			{
				writeLockUnclosedOutputStreamPartitioner.unlock();
			}
			
			os = cdos;
		}
		finally 
		{
			outputStreamPartitioner.getLockCreate().unlock();
		}
		return os;
	}

	/**
	 * 
	 * @param inputStreamPartitioner invoker
	 * @return new substream embed in parentstream
	 * @throws IOException
	 */
	protected InputStream createInputSubStream(InputStreamPartitionerImpl inputStreamPartitioner) throws IOException
	{
		try
		{
			inputStreamPartitioner.getLockCreate().lock();
			try
			{
				readLockUnclosedInputStreamPartitioner.lock();
				if(unclosedInputStreamPartitioner.containsKey(inputStreamPartitioner))
				{
					throw new IOException("partitioner has unclosed streams");
				}
			}
			finally 
			{
				readLockUnclosedInputStreamPartitioner.unlock();
			}
			
			InputStream inputStreamParent = inputStreamPartitioner.getParentInputStream();
			FindMarkInputStream fmis = new FindMarkInputStream(inputStreamParent, inputStreamPartitioner.getCarryOut());
			if(fmis.matchBeginBuffer == null)
			{
				fmis.close();
				return null;
			}
			InputStreamPackagerEndMark ispem = new InputStreamPackagerEndMark(inputStreamPartitioner);
			CloseDelegateInputStream cdis = new CloseDelegateInputStream(fmis, ispem);
			
			try
			{
				writeLockUnclosedInputStreamPartitioner.lock();
				unclosedInputStreamPartitioner.put(inputStreamPartitioner, fmis);
			}
			finally 
			{
				writeLockUnclosedInputStreamPartitioner.unlock();
			}
			
			return cdis;
		}
		finally 
		{
			inputStreamPartitioner.getLockCreate().unlock();
		}
	}
	
	/**
	 * Handles necessary stuff while closing inputsubstream 
	 * 
	 * @author Sebastian Palarus
	 *
	 */
	protected class InputStreamPackagerEndMark implements Runnable
	{
		private InputStreamPartitionerImpl partitioner = null;
		
		public InputStreamPackagerEndMark(InputStreamPartitionerImpl partitioner)
		{
			super();
			this.partitioner = partitioner;
		}
		
		@Override
		public void run()
		{
			try
			{
				partitioner.fireSubStreamCloseEvent();
				partitioner.setPartId(null);
			}
			catch (Exception e) 
			{
				LogService logService = StreamPartitionerFactoryImpl.this.logService;
				
				if(logService != null)
				{
					ComponentContext context = StreamPartitionerFactoryImpl.this.context;
					logService.log(context == null ? null :context.getServiceReference(), LogService.LOG_ERROR, "error on fire close event",e);
				}
				else
				{
					e.printStackTrace();
				}
			}
			
			try
			{
				writeLockUnclosedInputStreamPartitioner.lock();
				FindMarkInputStream fmis = StreamPartitionerFactoryImpl.this.unclosedInputStreamPartitioner.remove(this.partitioner);
				if(fmis != null)
				{
					partitioner.setCarryOut(fmis.getCarryout());
				}
			}
			finally 
			{
				writeLockUnclosedInputStreamPartitioner.unlock();
			}
		}
	}
	
	/**
	 * Handles necessary stuff while closing outputsubstream 
	 * 
	 * @author Sebastian Palarus
	 *
	 */
	protected class OuputStreamPackagerEndMark implements Runnable
	{
		private OutputStreamPartitionerImpl partitioner = null;
		private OutputStream outputStream = null;
		private String endMark = null;
		
		public OuputStreamPackagerEndMark(OutputStreamPartitionerImpl partitioner, OutputStream outputStream, String endMark)
		{
			super();
			this.partitioner = partitioner;
			this.outputStream = outputStream;
			this.endMark = endMark;
		}
		
		@Override
		public void run()
		{
			try
			{
				this.outputStream.write(endMark.getBytes());
			}
			catch (Exception e) 
			{
				LogService logService = StreamPartitionerFactoryImpl.this.logService;
				
				if(logService != null)
				{
					ComponentContext context = StreamPartitionerFactoryImpl.this.context;
					logService.log(context == null ? null :context.getServiceReference(), LogService.LOG_ERROR, "error on write endmark",e);
				}
				else
				{
					e.printStackTrace();
				}
			}
			
			try
			{
				partitioner.fireSubStreamCloseEvent();
				partitioner.setPartId(null);
			}
			catch (Exception e) 
			{
				LogService logService = StreamPartitionerFactoryImpl.this.logService;
				
				if(logService != null)
				{
					ComponentContext context = StreamPartitionerFactoryImpl.this.context;
					logService.log(context == null ? null :context.getServiceReference(), LogService.LOG_ERROR, "error on fire close event",e);
				}
				else
				{
					e.printStackTrace();
				}
			}
			
			try
			{
				writeLockUnclosedOutputStreamPartitioner.lock();
				StreamPartitionerFactoryImpl.this.unclosedOutputStreamPartitioner.remove(this.partitioner);
			}
			finally 
			{
				writeLockUnclosedOutputStreamPartitioner.unlock();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IInputStreamPartitioner newInputStreamPartitioner(InputStream parentInputStream)
	{
		return new InputStreamPartitionerImpl(parentInputStream, this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IOutputStreamPartitioner newOutputStreamPartitioner(OutputStream outputStream)
	{
		return new OutputStreamPartitionerImpl(outputStream, this);
	}

}
