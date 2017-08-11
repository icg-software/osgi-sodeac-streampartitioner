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

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides a factory to create partitioners for {@link java.io.InputStream}s and {@link java.io.OutputStream}s.
 * 
 * @author Sebastian Palarus
 * @since 1.0.0
 * 
 */
public interface IStreamPartitionerFactory
{
	/**
	 * Create a new Instance of {@link IOutputStreamPartitioner}.  
	 * 
	 * @since 1.0.0
	 * 
	 * @param parentOutputStream  The parent outputstream shall parted into multiple substreams.
	 * @return Instance of {@link IOutputStreamPartitioner}
	 */
	public IOutputStreamPartitioner newOutputStreamPartitioner(OutputStream parentOutputStream);
	
	/**
	 * 
	 * Create a new Instance of {@link IInputStreamPartitioner}. 
	 * 
	 * @since 1.0.0
	 *  
	 * @param parentInputStream  The parent inputstream contains multiple substreams.
	 * @return Instance of {@link IInputStreamPartitioner}
	 */
	public IInputStreamPartitioner newInputStreamPartitioner(InputStream parentInputStream);
	
	
}
