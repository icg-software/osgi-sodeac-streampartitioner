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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;
import org.sodeac.streampartitioner.api.IStreamPartitioner;
import org.sodeac.streampartitioner.api.ISubStreamListener;

public class StreamPartitionerSingleObjectTest
{
    @Test
    public void testInputStreamPartitioner()
    {
        final StreamPartitionerFactoryImpl impl = new StreamPartitionerFactoryImpl();
        final ByteArrayInputStream is = new ByteArrayInputStream(new byte[1024]);
        final InputStreamPartitionerImpl inputStreamPartitioner = new InputStreamPartitionerImpl(is, impl);

        // Locks
        assertNotNull(inputStreamPartitioner.lockCreate, "lockCreate should be not null");
        assertNotNull(inputStreamPartitioner.lockFinishListener, "lockFinishListener should be not null");
        assertNotNull(inputStreamPartitioner.writeLockFinishListener, "writeLockFinishListener should be not null");
        assertNotNull(inputStreamPartitioner.readLockFinishListener, "readLockFinishListener should be not null");

        // Test InputStream
        assertSame(is, inputStreamPartitioner.parentInputStream, "inputstream should be set");

        // Part Id
        final String partId = UUID.randomUUID().toString();

        assertNull(inputStreamPartitioner.partId, "partid should be null");

        final IInputStreamPartitioner isp = inputStreamPartitioner.setPartId(partId);

        assertEquals(partId, inputStreamPartitioner.partId, "partid should be set");
        assertSame(inputStreamPartitioner, isp, "setPartId(partId) should return invokes partitioner");

        // CarryOut
        final byte[] carryout = new byte[1];

        assertNull(inputStreamPartitioner.carryout, "carryout should be null");

        inputStreamPartitioner.setCarryOut(carryout);

        assertSame(carryout, inputStreamPartitioner.carryout, "carryout should be set");

        final AtomicLong counter = new AtomicLong(0);
        final StringBuilder firedWithPartId = new StringBuilder();

        final ISubStreamListener finishedListener = new ISubStreamListener()
        {
            @Override
            public void onClose(final IStreamPartitioner partitioner)
            {
                counter.incrementAndGet();
                firedWithPartId.append(partitioner.getPartId());
            }
        };

        // Test add listener
        inputStreamPartitioner.addSubStreamListener(finishedListener);

        assertEquals(1, inputStreamPartitioner.payloadPartFinishedListenerList.size(),
                "inputpackage should contains 1 finishlistener");

        // Test don't add listener twice
        inputStreamPartitioner.addSubStreamListener(finishedListener);

        assertEquals(1, inputStreamPartitioner.payloadPartFinishedListenerList.size(),
                "inputpackage should contains 1 finishlistener");

        inputStreamPartitioner.fireSubStreamCloseEvent();

        // Test notify listener
        assertEquals(1L, counter.get(), "finishedListener should notify one time");
        assertEquals(partId, firedWithPartId.toString(), "finishedListener should notify with correct partid");

        // Test removeListener
        inputStreamPartitioner.removeSubStreamListener(finishedListener);

        assertEquals(0, inputStreamPartitioner.payloadPartFinishedListenerList.size(),
                "inputpackage should contains 0 finishlistener");
    }

    @Test
    public void testOutputStreamPartitioner()
    {
        final StreamPartitionerFactoryImpl impl = new StreamPartitionerFactoryImpl();
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final OutputStreamPartitionerImpl outputStreamPartitioner = new OutputStreamPartitionerImpl(os, impl);

        // Locks
        assertNotNull(outputStreamPartitioner.lockCreate, "lockCreate should be not null");
        assertNotNull(outputStreamPartitioner.lockFinishListener, "lockFinishListener should be not null");
        assertNotNull(outputStreamPartitioner.writeLockFinishListener, "writeLockFinishListener should be not null");
        assertNotNull(outputStreamPartitioner.readLockFinishListener, "readLockFinishListener should be not null");

        // Test OutputStream
        assertSame(os, outputStreamPartitioner.parentOutputStream, "outputstream shoult be set");

        // Part Id
        final String partId = UUID.randomUUID().toString();

        assertNull(outputStreamPartitioner.partId, "partid should be null");

        final IOutputStreamPartitioner osp = outputStreamPartitioner.setPartId(partId);

        assertEquals(partId, outputStreamPartitioner.partId, "partid should be set");
        assertSame(outputStreamPartitioner, osp, "setPartId(partId) should return invokes partitioner");

        final AtomicLong counter = new AtomicLong(0);
        final StringBuilder firedWithPartId = new StringBuilder();

        final ISubStreamListener finishedListener = new ISubStreamListener()
        {
            @Override
            public void onClose(final IStreamPartitioner partitioner)
            {
                counter.incrementAndGet();
                firedWithPartId.append(partitioner.getPartId());
            }
        };

        // Test add listener
        outputStreamPartitioner.addSubStreamListener(finishedListener);

        assertEquals(1, outputStreamPartitioner.payloadPartFinishedListenerList.size(),
                "outputpackage should contains 1 finishlistener");

        // Test don't add listener twice
        outputStreamPartitioner.addSubStreamListener(finishedListener);

        assertEquals(1, outputStreamPartitioner.payloadPartFinishedListenerList.size(),
                "outputpackage should contains 1 finishlistener");

        outputStreamPartitioner.fireSubStreamCloseEvent();

        // Test notify listener
        assertEquals(1L, counter.get(), "finishedListener should notify one time");
        assertEquals(partId, firedWithPartId.toString(), "finishedListener should notify with correct partid");

        // Test removeListener
        outputStreamPartitioner.removeSubStreamListener(finishedListener);

        assertEquals(0, outputStreamPartitioner.payloadPartFinishedListenerList.size(),
                "outputpackage should contains 0 finishlistener");
    }
}