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
@Component(name = "StreamPartitionerFactory", service = IStreamPartitionerFactory.class)
public class StreamPartitionerFactoryImpl implements IStreamPartitionerFactory
{
    public StreamPartitionerFactoryImpl()
    {
        super();

        this.lockUnclosedInputStreamPartitioner = new ReentrantReadWriteLock(true);
        this.readLockUnclosedInputStreamPartitioner = this.lockUnclosedInputStreamPartitioner.readLock();
        this.writeLockUnclosedInputStreamPartitioner = this.lockUnclosedInputStreamPartitioner.writeLock();

        this.lockUnclosedOutputStreamPartitioner = new ReentrantReadWriteLock(true);
        this.readLockUnclosedOutputStreamPartitioner = this.lockUnclosedOutputStreamPartitioner.readLock();
        this.writeLockUnclosedOutputStreamPartitioner = this.lockUnclosedOutputStreamPartitioner.writeLock();
    }

    public static final String OFFSET_MARK = "X." + IStreamPartitioner.class.getSimpleName().toUpperCase() + "_MARK";
    public static final String END_MARK = "E_._ND";

    private final Map<InputStreamPartitionerImpl, FindMarkInputStream> unclosedInputStreamPartitioner = new HashMap<InputStreamPartitionerImpl, FindMarkInputStream>();
    private final Map<OutputStreamPartitionerImpl, CloseDelegateOutputStream> unclosedOutputStreamPartitioner = new HashMap<OutputStreamPartitionerImpl, CloseDelegateOutputStream>();

    protected ReentrantReadWriteLock lockUnclosedInputStreamPartitioner = null;
    protected ReadLock readLockUnclosedInputStreamPartitioner = null;
    protected WriteLock writeLockUnclosedInputStreamPartitioner = null;

    protected ReentrantReadWriteLock lockUnclosedOutputStreamPartitioner = null;
    protected ReadLock readLockUnclosedOutputStreamPartitioner = null;
    protected WriteLock writeLockUnclosedOutputStreamPartitioner = null;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile LogService logService = null;

    private volatile ComponentContext context = null;

    private final AtomicLong counter = new AtomicLong();

    @Activate
    private void activate(final ComponentContext context, final Map<String, ?> properties)
    {
        this.context = context;
    }

    @Deactivate
    private void deactivate(final ComponentContext context)
    {
        this.context = null;
    }

    /**
     *
     * @param outputStreamPartitioner invoker
     *
     * @return new substream embed in parentstream
     *
     * @throws IOException
     */
    protected OutputStream createOutputSubStream(final OutputStreamPartitionerImpl outputStreamPartitioner) throws IOException
    {
        OutputStream os = null;

        try
        {
            outputStreamPartitioner.getLockCreate().lock();

            try
            {
                this.readLockUnclosedOutputStreamPartitioner.lock();
                if(this.unclosedOutputStreamPartitioner.containsKey(outputStreamPartitioner))
                {
                    throw new IOException("partitioner has unclosed streams");
                }
            }
            finally
            {
                this.readLockUnclosedOutputStreamPartitioner.unlock();
            }

            final OutputStream parentOutputStream = outputStreamPartitioner.getParentOutputStream();
            final String mark = OFFSET_MARK + "_" + UUID.randomUUID() + "_" + this.counter.incrementAndGet() + END_MARK;
            parentOutputStream.write(mark.getBytes());
            final OuputStreamPackagerEndMark ospem = new OuputStreamPackagerEndMark(outputStreamPartitioner, parentOutputStream, mark);
            final CloseDelegateOutputStream cdos = new CloseDelegateOutputStream(parentOutputStream, ospem);

            try
            {
                this.writeLockUnclosedOutputStreamPartitioner.lock();
                this.unclosedOutputStreamPartitioner.put(outputStreamPartitioner, cdos);
            }
            finally
            {
                this.writeLockUnclosedOutputStreamPartitioner.unlock();
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
     *
     * @return new substream embed in parentstream
     *
     * @throws IOException
     */
    protected InputStream createInputSubStream(final InputStreamPartitionerImpl inputStreamPartitioner) throws IOException
    {
        try
        {
            inputStreamPartitioner.getLockCreate().lock();
            try
            {
                this.readLockUnclosedInputStreamPartitioner.lock();
                if(this.unclosedInputStreamPartitioner.containsKey(inputStreamPartitioner))
                {
                    throw new IOException("partitioner has unclosed streams");
                }
            }
            finally
            {
                this.readLockUnclosedInputStreamPartitioner.unlock();
            }

            final InputStream inputStreamParent = inputStreamPartitioner.getParentInputStream();
            final FindMarkInputStream fmis = new FindMarkInputStream(inputStreamParent, inputStreamPartitioner.getCarryOut());
            if(fmis.matchBeginBuffer == null)
            {
                fmis.close();
                return null;
            }
            final InputStreamPackagerEndMark ispem = new InputStreamPackagerEndMark(inputStreamPartitioner);
            final CloseDelegateInputStream cdis = new CloseDelegateInputStream(fmis, ispem);

            try
            {
                this.writeLockUnclosedInputStreamPartitioner.lock();
                this.unclosedInputStreamPartitioner.put(inputStreamPartitioner, fmis);
            }
            finally
            {
                this.writeLockUnclosedInputStreamPartitioner.unlock();
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

        public InputStreamPackagerEndMark(final InputStreamPartitionerImpl partitioner)
        {
            super();
            this.partitioner = partitioner;
        }

        @Override
        public void run()
        {
            try
            {
                this.partitioner.fireSubStreamCloseEvent();
                this.partitioner.setPartId(null);
            }
            catch (final Exception e)
            {
                final LogService logService = StreamPartitionerFactoryImpl.this.logService;

                if(logService != null)
                {
                    final ComponentContext context = StreamPartitionerFactoryImpl.this.context;
                    logService.log(context == null ? null : context.getServiceReference(), LogService.LOG_ERROR, "error on fire close event", e);
                }
                else
                {
                    e.printStackTrace();
                }
            }

            try
            {
                StreamPartitionerFactoryImpl.this.writeLockUnclosedInputStreamPartitioner.lock();
                final FindMarkInputStream fmis = StreamPartitionerFactoryImpl.this.unclosedInputStreamPartitioner.remove(this.partitioner);
                if(fmis != null)
                {
                    this.partitioner.setCarryOut(fmis.getCarryout());
                }
            }
            finally
            {
                StreamPartitionerFactoryImpl.this.writeLockUnclosedInputStreamPartitioner.unlock();
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

        public OuputStreamPackagerEndMark(final OutputStreamPartitionerImpl partitioner, final OutputStream outputStream, final String endMark)
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
                this.outputStream.write(this.endMark.getBytes());
            }
            catch (final Exception e)
            {
                final LogService logService = StreamPartitionerFactoryImpl.this.logService;

                if(logService != null)
                {
                    final ComponentContext context = StreamPartitionerFactoryImpl.this.context;
                    logService.log(context == null ? null : context.getServiceReference(), LogService.LOG_ERROR, "error on write endmark", e);
                }
                else
                {
                    e.printStackTrace();
                }
            }

            try
            {
                this.partitioner.fireSubStreamCloseEvent();
                this.partitioner.setPartId(null);
            }
            catch (final Exception e)
            {
                final LogService logService = StreamPartitionerFactoryImpl.this.logService;

                if(logService != null)
                {
                    final ComponentContext context = StreamPartitionerFactoryImpl.this.context;
                    logService.log(context == null ? null : context.getServiceReference(), LogService.LOG_ERROR, "error on fire close event", e);
                }
                else
                {
                    e.printStackTrace();
                }
            }

            try
            {
                StreamPartitionerFactoryImpl.this.writeLockUnclosedOutputStreamPartitioner.lock();
                StreamPartitionerFactoryImpl.this.unclosedOutputStreamPartitioner.remove(this.partitioner);
            }
            finally
            {
                StreamPartitionerFactoryImpl.this.writeLockUnclosedOutputStreamPartitioner.unlock();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IInputStreamPartitioner newInputStreamPartitioner(final InputStream parentInputStream)
    {
        return new InputStreamPartitionerImpl(parentInputStream, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IOutputStreamPartitioner newOutputStreamPartitioner(final OutputStream outputStream)
    {
        return new OutputStreamPartitionerImpl(outputStream, this);
    }

}
