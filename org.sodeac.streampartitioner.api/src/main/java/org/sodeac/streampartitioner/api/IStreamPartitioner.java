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

/**
 * An IStreamPartitioner provides basic functionality of partitioners
 * 
 * @author Sebastian Palarus
 * @since 1.0.0
 *
 */
public interface IStreamPartitioner
{
	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @param subStreamListener register Listener for substream events
	 */
	public void addSubStreamListener(ISubStreamListener subStreamListener);
	
	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @param subStreamListener unregister a registered listener
	 */
	public void removeSubStreamListener(ISubStreamListener subStreamListener);
	
	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @param partId PartId of current substream to process in listeners
	 * @return Partitioner 
	 */
	public IStreamPartitioner setPartId(String partId);
	
	/**
	 * 
	 * @since 1.0.0
	 * 
	 * @return PartId of current substream to process in listeners
	 */
	public String getPartId();
}
