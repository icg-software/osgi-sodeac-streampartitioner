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
package org.sodeac.streampartitioner.api;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 
 * An IOutputStreamPartitioner is a {@link IStreamPartitioner} to handle {@link java.io.OutputStream}s.
 * 
 * @author Sebastian Palarus
 * @since 1.0.0
 *
 */
public interface IOutputStreamPartitioner extends IStreamPartitioner
{
	/**
	 * @since 1.0.0
	 * 
	 * @return parent {@link java.io.OutputStream} feeds by substreams
	 */
	public OutputStream getParentOutputStream();

	/**
	 * Creates a new substream. This substream has to be close before before partitioner can create a new one.
	 * 
	 * @since 1.0.0
	 * 
	 * @return new substream shall embed in parent stream
	 * @throws IOException delegated from parent stream
	 */
	public OutputStream createNextSubOutputStream() throws IOException;
	

	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @param partId PartId of current substream to process in listeners
	 * @return this Partitioner 
	 */
	@Override
	public IOutputStreamPartitioner setPartId(String partId);
}
