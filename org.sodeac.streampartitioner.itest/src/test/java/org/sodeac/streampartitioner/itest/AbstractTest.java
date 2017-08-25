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
package org.sodeac.streampartitioner.itest;

import org.ops4j.pax.exam.Option;

import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
//import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
//import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.debugConfiguration;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.options.ProvisionOption;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.Bundle;

import java.io.File;

public abstract class AbstractTest
{
	public static ProvisionOption<?> reactorBundle(String artifactId, String version) 
	{
		String fileName = String.format("%s/../%s/target/%s-%s.jar", PathUtils.getBaseDir(), artifactId, artifactId,version);

		if (new File(fileName).exists()) 
		{
			try
			{
				String url = "file:" + new File(fileName).getCanonicalPath();
				return bundle(url);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			fileName = String.format("%s/../%s/target/%s-%s-SNAPSHOT.jar", PathUtils.getBaseDir(), artifactId, artifactId,version);

			if (new File(fileName).exists()) 
			{
				try
				{
					String url = "file:" + new File(fileName).getCanonicalPath();
					return bundle(url);
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
	public static String getBundleStateName(int state)
	{
		switch (state) 
		{
			case Bundle.UNINSTALLED:
					
				return "UNINSTALLED";
				
			case Bundle.INSTALLED:
				
				return "INSTALLED";
	
			case Bundle.RESOLVED:
				
				return "RESOLVED";
			
			case Bundle.STARTING:
				
				return "STARTING";
			
			case Bundle.STOPPING:
				
				return "STOPPING";
				
			case Bundle.ACTIVE:
				
				return "ACTIVE";
			default:
				
				return "State " + state;
		}
	}

	public Option[] config() 
	{
		MavenArtifactUrlReference karafUrl = maven()
			.groupId("org.apache.karaf")
			.artifactId("apache-karaf")
			.version("4.1.1")
			.type("zip");

		MavenUrlReference karafStandardRepo = maven()
			.groupId("org.apache.karaf.features")
			.artifactId("standard")
			.version("4.1.1")
			.classifier("features")
			.type("xml");
		
		return new Option[] 
		{
			//debugConfiguration("5005", true),
			karafDistributionConfiguration()
				.frameworkUrl(karafUrl)
				.unpackDirectory(new File("target", "exam"))
				.useDeployFolder(false),
			keepRuntimeFolder(),
			//configureConsole().ignoreLocalConsole(),
			cleanCaches( true ),
			logLevel(LogLevel.INFO),
			features(karafStandardRepo , "scr"),
			mavenBundle("org.easymock", "easymock", "3.4").start(),
			reactorBundle("org.sodeac.streampartitioner.api","1.0.0").start(),
			reactorBundle("org.sodeac.streampartitioner.provider","1.0.1").start(),
			reactorBundle("org.sodeac.streampartitioner.example","1.0.0").start()
		};
	}
}
