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

/**
 * An OutputStreamWrapper, but by using invoke close() a registered {@link java.lang.Runnable} will be invoked instead close-methode of parent stream.
 * 
 * @author Sebastian Palarus
 *
 */
public class CloseDelegateOutputStream extends OutputStream
{
	private OutputStream parentInputStream = null;
	private Runnable onClose = null;
	
	/**
	 * 
	 * @param parentInputStream ParentStream
	 * @param onClose registered {@link java.lang.Runnable} called instead of close()
	 */
	public CloseDelegateOutputStream(OutputStream parentInputStream,Runnable onClose)
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
		if(this.onClose != null)
		{
			this.onClose.run();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void flush() throws IOException
	{
		this.parentInputStream.flush();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(byte[] b, int off, int len) throws IOException
	{
		this.parentInputStream.write(b, off, len);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(byte[] b) throws IOException
	{
		this.parentInputStream.write(b);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(int b) throws IOException
	{
		this.parentInputStream.write(b);
	}

}
