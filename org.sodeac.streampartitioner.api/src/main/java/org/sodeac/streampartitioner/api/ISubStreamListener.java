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

/**
 * 
 * An interface for listener fired on close a substream
 * 
 * @author Sebastian Palarus
 * @since 1.0.0
 *
 */
public interface ISubStreamListener
{
	/**
	 * 
	 * Fired if substream is closed
	 * 
	 * @since 1.0.0
	 * 
	 * @param streamPartitioner {@link IStreamPartitioner} creates the substream
	 */
	public void onClose(IStreamPartitioner streamPartitioner);
}
