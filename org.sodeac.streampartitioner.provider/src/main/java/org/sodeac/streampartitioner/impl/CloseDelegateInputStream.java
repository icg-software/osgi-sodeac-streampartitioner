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

/**
 * An InputStreamWrapper, but by using invoke close() a registered {@link java.lang.Runnable} will be invoked instead close-methode of parent stream.
 * 
 * @author Sebastian Palarus
 *
 */
public class CloseDelegateInputStream extends InputStream
{
	private InputStream parentInputStream = null;
	private Runnable onClose = null;

	/**
	 * 
	 * @param parentInputStream ParentStream
	 * @param onClose registered {@link java.lang.Runnable} called instead of close()
	 */
	public CloseDelegateInputStream(InputStream parentInputStream,Runnable onClose)
	{
		super();
		this.parentInputStream = parentInputStream;
		this.onClose = onClose;
	}

	/**
	 * Does not close parent stream. Invoke registered {@link java.lang.Runnable} (in same Thread)
	 */
	@Override
	public void close() throws IOException
	{
		if(onClose != null)
		{
			this.onClose.run();
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int available() throws IOException
	{
		return this.parentInputStream.available();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void mark(int readlimit)
	{
		this.parentInputStream.mark(readlimit);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean markSupported()
	{
		return this.parentInputStream.markSupported();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read() throws IOException
	{
		return this.parentInputStream.read();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read(byte[] b, int off, int len) throws IOException
	{
		return this.parentInputStream.read(b, off, len);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read(byte[] b) throws IOException
	{
		return this.parentInputStream.read(b);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void reset() throws IOException
	{
		this.parentInputStream.reset();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long skip(long n) throws IOException
	{
		return this.parentInputStream.skip(n);
	}
}
