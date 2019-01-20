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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;
import org.sodeac.streampartitioner.api.IStreamPartitioner;
import org.sodeac.streampartitioner.api.ISubStreamListener;
import org.sodeac.streampartitioner.impl.InputStreamPartitionerImpl;
import org.sodeac.streampartitioner.impl.StreamPartitionerFactoryImpl;

public class StreamPartitionerSingleObjectTest
{

	@Test
	public void testInputStreamPartitioner()
	{
		StreamPartitionerFactoryImpl impl = new StreamPartitionerFactoryImpl();
		ByteArrayInputStream is = new ByteArrayInputStream(new byte[1024]);
		InputStreamPartitionerImpl inputStreamPartitioner = new InputStreamPartitionerImpl(is,impl);
		
		// Locks
		
		assertNotNull("lockCreate should be not null"				, inputStreamPartitioner.lockCreate					);
		assertNotNull("lockFinishListener should be not null"		, inputStreamPartitioner.lockFinishListener			);
		assertNotNull("writeLockFinishListener should be not null"	, inputStreamPartitioner.writeLockFinishListener	);
		assertNotNull("readLockFinishListener should be not null"	, inputStreamPartitioner.readLockFinishListener		);
		
		// Test InputStream
		
		assertSame("inputstream should be set",is,inputStreamPartitioner.parentInputStream);
		
		// Part Id
		
		String partId = UUID.randomUUID().toString();
				
		assertNull("partid should be null", inputStreamPartitioner.partId);
				
		IInputStreamPartitioner isp = inputStreamPartitioner.setPartId(partId);
			
		assertEquals("partid should be set",partId, inputStreamPartitioner.partId);
		assertSame("setPartId(partId) should return invokes partitioner",inputStreamPartitioner, isp);
		
		// CarryOut
		
		byte[] carryout = new byte[1];
		
		assertNull("carryout should be null", inputStreamPartitioner.carryout);
		
		inputStreamPartitioner.setCarryOut(carryout);
			
		assertSame("carryout should be set",carryout, inputStreamPartitioner.carryout);
		
		AtomicLong counter = new AtomicLong(0);
		StringBuilder firedWithPartId = new StringBuilder();
		
		ISubStreamListener finishedListener = new ISubStreamListener()
		{
			
			@Override
			public void onClose(IStreamPartitioner partitioner)
			{
				counter.incrementAndGet();
				firedWithPartId.append(partitioner.getPartId());
			}
		};
		
		// Test add listener
		
		inputStreamPartitioner.addSubStreamListener(finishedListener);
		
		assertEquals("inputpackage should contains 1 finishlistener",1,inputStreamPartitioner.payloadPartFinishedListenerList.size());
		
		// Test don't add listener twice
		
		inputStreamPartitioner.addSubStreamListener(finishedListener);
		
		assertEquals("inputpackage should contains 1 finishlistener",1,inputStreamPartitioner.payloadPartFinishedListenerList.size());
		
		inputStreamPartitioner.fireSubStreamCloseEvent();
		
		// Test notify listener
		
		assertEquals("finishedListener should notify one time", 1L, counter.get());
		assertEquals("finishedListener should notify with correct partid", partId, firedWithPartId.toString());
		
		// Test removeListener
		
		inputStreamPartitioner.removeSubStreamListener(finishedListener);
		
		assertEquals("inputpackage should contains 0 finishlistener",0,inputStreamPartitioner.payloadPartFinishedListenerList.size());
		
	}
	
	@Test
	public void testOutputStreamPartitioner()
	{
		StreamPartitionerFactoryImpl impl = new StreamPartitionerFactoryImpl();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		OutputStreamPartitionerImpl outputStreamPartitioner = new OutputStreamPartitionerImpl(os,impl);
		
		// Locks
		
		assertNotNull("lockCreate should be not null"				, outputStreamPartitioner.lockCreate				);
		assertNotNull("lockFinishListener should be not null"		, outputStreamPartitioner.lockFinishListener		);
		assertNotNull("writeLockFinishListener should be not null"	, outputStreamPartitioner.writeLockFinishListener	);
		assertNotNull("readLockFinishListener should be not null"	, outputStreamPartitioner.readLockFinishListener	);
		
		// Test OutputStream
		
		assertSame("outputstream shoult be set",os,outputStreamPartitioner.parentOutputStream);
		
		// Part Id
		
		String partId = UUID.randomUUID().toString();
				
		assertNull("partid should be null", outputStreamPartitioner.partId);
				
		IOutputStreamPartitioner osp = outputStreamPartitioner.setPartId(partId);
			
		assertEquals("partid should be set",partId, outputStreamPartitioner.partId);
		assertSame("setPartId(partId) should return invokes partitioner",outputStreamPartitioner, osp);
		
		AtomicLong counter = new AtomicLong(0);
		StringBuilder firedWithPartId = new StringBuilder();
		
		ISubStreamListener finishedListener = new ISubStreamListener()
		{
			
			@Override
			public void onClose(IStreamPartitioner partitioner)
			{
				counter.incrementAndGet();
				firedWithPartId.append(partitioner.getPartId());
			}
		};
		
		// Test add listener
		
		outputStreamPartitioner.addSubStreamListener(finishedListener);
		
		assertEquals("outputpackage should contains 1 finishlistener",1,outputStreamPartitioner.payloadPartFinishedListenerList.size());
		
		// Test don't add listener twice
		
		outputStreamPartitioner.addSubStreamListener(finishedListener);
		
		assertEquals("outputpackage should contains 1 finishlistener",1,outputStreamPartitioner.payloadPartFinishedListenerList.size());
		
		outputStreamPartitioner.fireSubStreamCloseEvent();
		
		// Test notify listener
		
		assertEquals("finishedListener should notify one time", 1L, counter.get());
		assertEquals("finishedListener should notify with correct partid", partId, firedWithPartId.toString());
		
		// Test removeListener
		
		outputStreamPartitioner.removeSubStreamListener(finishedListener);
		
		assertEquals("outputpackage should contains 0 finishlistener",0,outputStreamPartitioner.payloadPartFinishedListenerList.size());
		
	}

}
