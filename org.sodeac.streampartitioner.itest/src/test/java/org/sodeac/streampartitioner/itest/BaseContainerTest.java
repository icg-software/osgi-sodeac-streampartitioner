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
package org.sodeac.streampartitioner.itest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.event.EventAdmin;
import org.sodeac.streampartitioner.api.IStreamPartitionerFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class BaseContainerTest extends AbstractTest
{
	@Inject
	private BundleContext bundleContext;
	
	@Inject
	private IStreamPartitionerFactory streamPartitionerFactory;
	
	@Inject
	private EventAdmin eventAdmin;
	
	
	@Configuration
	public Option[] config() 
	{
		return super.config();
	}
	
	@Before
	public void setUp() {}
    
	@Test
	public void testComponentInstance() 
	{
		assertNotNull("bundleContext should not be null" ,bundleContext);
		
		System.out.println("\n\n");
		Bundle[] bundles = bundleContext.getBundles();
        for (Bundle bundle : bundles) 
        {
            System.out.println("[INFO]\t\tbundle " + bundle + ": " + bundle.getHeaders().get(Constants.BUNDLE_VERSION) + " " + getBundleStateName(bundle.getState()));
        }
        System.out.println("\n");
		assertNotNull("EventAdmin should not be null" ,eventAdmin);
		assertNotNull("streamPartitionerFactory should not be null" ,streamPartitionerFactory);
	}
	
	@Test
	public void testProviderAPIClassLoaderHierarchy() 
	{
		
		assertTrue("inteface of provided streamPartitionerImplementation should use same classloader for api like consumer" ,streamPartitionerFactory instanceof IStreamPartitionerFactory);
	}
}
