/*******************************************************************************
 * Copyright (c) 2017, 2019 Sebastian Palarus All rights reserved. This program
 * and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html Contributors:
 * Sebastian Palarus - initial API and implementation
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
import java.io.IOException;

import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.options.ProvisionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.Bundle;

import lombok.val;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public abstract class AbstractIT
{
    public static ProvisionOption<?> reactorBundle(final String artifactId, final String version) throws IOException
    {
        // System.out.println("reactorBundle: artifactId=" + artifactId + ", version=" +
        // version);
        // System.out.println("reactorBundle: baseDir=" + PathUtils.getBaseDir());
        
        String fileName = String.format("%s/../%s/target/%s-%s.jar", PathUtils.getBaseDir(), artifactId, artifactId, version);
        if (new File(fileName).exists())
        {
            return bundle("file:" + new File(fileName).getCanonicalPath());
        }
        
        fileName = String.format("%s/../%s/target/%s-%s-SNAPSHOT.jar", PathUtils.getBaseDir(), artifactId, artifactId, version);
        if (new File(fileName).exists())
        {
            return bundle("file:" + new File(fileName).getCanonicalPath());
        }
        
        throw new IllegalStateException("Bundle jar not found for artifactId=%s, version=%s, baseDir=%s"
                                            .formatted(artifactId, version, PathUtils.getBaseDir()));
    }
    
    public static String getBundleStateName(final int state)
    {
        return switch (state)
        {
            case Bundle.UNINSTALLED -> "UNINSTALLED";
            case Bundle.INSTALLED -> "INSTALLED";
            case Bundle.RESOLVED -> "RESOLVED";
            case Bundle.STARTING -> "STARTING";
            case Bundle.STOPPING -> "STOPPING";
            case Bundle.ACTIVE -> "ACTIVE";
            default -> "State " + state;
        };
    }
    
    @Configuration
    public static Option[] config() throws IOException
    {
        
        final MavenArtifactUrlReference karafUrl = maven()
            .groupId("org.apache.karaf")
            .artifactId("apache-karaf")
            .versionAsInProject()
            .type("zip");
        
        final MavenUrlReference karafStandardRepo = maven()
            .groupId("org.apache.karaf.features")
            .artifactId("standard")
            .versionAsInProject()
            .classifier("features")
            .type("xml");
        
        val easymock = mavenBundle("org.easymock", "easymock").versionAsInProject();
        val sodeacVersion = System.getProperty("sodeac.version");
        
        // System.out.println("########################################################################################");
        // System.out.println(karafUrl);
        // System.out.println(karafStandardRepo);
        // System.out.println(easymock);
        // System.out.println("sodeacVersion=" + sodeacVersion);
        
        return new Option[] {
            // debugConfiguration("5005", true),
            karafDistributionConfiguration()
                .frameworkUrl(karafUrl)
                .unpackDirectory(new File("target", "exam"))
                .useDeployFolder(false),
            keepRuntimeFolder(),
            cleanCaches(true),
            logLevel(LogLevel.INFO),
            features(karafStandardRepo, "scr"),
            
            // necessary bundles
            easymock.start(),
            // project bundles
            reactorBundle("org.sodeac.streampartitioner.api", sodeacVersion).start(),
            reactorBundle("org.sodeac.streampartitioner.provider", sodeacVersion).start(),
            reactorBundle("org.sodeac.streampartitioner.example", sodeacVersion).start(),
        };
    }
}
