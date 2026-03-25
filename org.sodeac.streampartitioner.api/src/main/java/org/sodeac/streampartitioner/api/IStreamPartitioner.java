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
     * @param subStreamListener register Listener for substream events
     *
     * @since 1.0.0
     */
    void addSubStreamListener(ISubStreamListener subStreamListener);

    /**
     *
     * @param subStreamListener unregister a registered listener
     *
     * @since 1.0.0
     */
    void removeSubStreamListener(ISubStreamListener subStreamListener);

    /**
     *
     * @param partId PartId of current substream to process in listeners
     *
     * @return Partitioner
     *
     * @since 1.0.0
     */
    IStreamPartitioner setPartId(String partId);

    /**
     *
     * @return PartId of current substream to process in listeners
     *
     * @since 1.0.0
     */
    String getPartId();
}
