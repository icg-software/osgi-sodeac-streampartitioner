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

import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.cleanCaches;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;

import java.io.File;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.options.ProvisionOption;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.Bundle;

public abstract class AbstractTest
{
    public static ProvisionOption<?> reactorBundle(final String artifactId, final String version)
    {
        String fileName = String.format("%s/../%s/target/%s-%s.jar", PathUtils.getBaseDir(), artifactId, artifactId, version);

        if(new File(fileName).exists())
        {
            try
            {
                final String url = "file:" + new File(fileName).getCanonicalPath();
                return bundle(url);
            }
            catch (final Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            fileName = String.format("%s/../%s/target/%s-%s-SNAPSHOT.jar", PathUtils.getBaseDir(), artifactId, artifactId, version);

            if(new File(fileName).exists())
            {
                try
                {
                    final String url = "file:" + new File(fileName).getCanonicalPath();
                    return bundle(url);
                }
                catch (final Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static String getBundleStateName(final int state)
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
        final MavenArtifactUrlReference karafUrl = maven()
                .groupId("org.apache.karaf")
                .artifactId("apache-karaf")
                .version("4.2.2")
                .type("zip");

        final MavenUrlReference karafStandardRepo = maven()
                .groupId("org.apache.karaf.features")
                .artifactId("standard")
                .version("4.2.2")
                .classifier("features")
                .type("xml");

        return new Option[]
                {
                        // debugConfiguration("5005", true),
                        karafDistributionConfiguration()
                                .frameworkUrl(karafUrl)
                                .unpackDirectory(new File("target", "exam"))
                                .useDeployFolder(false),
                        keepRuntimeFolder(),
                        // configureConsole().ignoreLocalConsole(),
                        cleanCaches(true),
                        logLevel(LogLevel.INFO),
                        features(karafStandardRepo, "scr"),
                        mavenBundle("org.easymock", "easymock", "3.6").start(),
                        reactorBundle("org.sodeac.streampartitioner.api", "1.1.0").start(),
                        reactorBundle("org.sodeac.streampartitioner.provider", "1.1.0").start(),
                        reactorBundle("org.sodeac.streampartitioner.example", "1.1.0").start()
                };
    }
}
