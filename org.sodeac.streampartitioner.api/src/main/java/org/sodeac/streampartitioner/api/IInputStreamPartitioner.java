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
package org.sodeac.streampartitioner.api;

import java.io.IOException;
import java.io.InputStream;

/**
 * 
 * An IInputStreamPartitioner is a {@link IStreamPartitioner} to handle {@link java.io.InputStream}s.
 * 
 * @author Sebastian Palarus
 * @since 1.0.0
 *
 */
public interface IInputStreamPartitioner extends IStreamPartitioner
{
	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @return parent {@link java.io.InputStream} is parted in substreams
	 */
	public InputStream getParentInputStream();

	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @return next substream of parentstream or null if parent stream is closed
	 * @throws IOException
	 */
	public InputStream getNextSubInputStream() throws IOException;
	
	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @param partId PartId of current substream to process in listeners
	 * @return this Partitioner 
	 */
	@Override
	public IInputStreamPartitioner setPartId(String partId);
}
