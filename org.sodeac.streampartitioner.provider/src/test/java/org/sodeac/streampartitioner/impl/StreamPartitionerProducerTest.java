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
package org.sodeac.streampartitioner.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;

public class StreamPartitionerProducerTest
{
    public static final int MODE_FILE = 0;
    public static final int MODE_MEMORY = 1;

    private final int mode = MODE_MEMORY; // better for my jenkins-tests on sdcard-driven rpi

    static IntStream parameters()
    {
        final List<Integer> parameterList = new ArrayList<>();

        for (int i = 0; i <= 10800; i++)
        {
            parameterList.add(i);

            if(i > 50) { i += 5; }
            if(i > 100) { i += 10; }
            if(i > 1000) { i += 20; }
            if(i > 5000) { i += 30; }
        }

        return parameterList.stream().mapToInt(Integer::intValue);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testProduceConsumeAndCompare(final int maxTransferLength) throws IOException, NoSuchAlgorithmException
    {
        System.out.println("[INFO]\t\tRun Producer Test: " + maxTransferLength);
        File testStreamFile = null;
        OutputStream testOutputStream = null;
        InputStream testInputStream = null;

        try
        {
            final StreamPartitionerFactoryImpl partitionerFactoryImpl = new StreamPartitionerFactoryImpl();

            final RandomGenerator randomGenerator = RandomGenerator.getDefault();
            File tempDir = null;
            testStreamFile = null;

            if(this.mode == MODE_FILE)
            {
                tempDir = new File(System.getProperty("java.io.tmpdir"));
                testStreamFile = new File(tempDir, getClass().getSimpleName() + "_" + UUID.randomUUID() + ".stream");
                testOutputStream = new FileOutputStream(testStreamFile);
            }

            if(this.mode == MODE_MEMORY)
            {
                testOutputStream = new ByteArrayOutputStream();
            }

            final List<PartContainer> partList = new ArrayList<>();

            final IOutputStreamPartitioner ouputStreamPartitioner = partitionerFactoryImpl.newOutputStreamPartitioner(testOutputStream);

            for (int i = 0; i < 10800; i++)
            {
                final MessageDigest md5 = MessageDigest.getInstance("MD5");
                md5.reset();
                final PartContainer partContainer = new PartContainer();
                partContainer.size = i;

                final byte[] part = new byte[i];
                for (int j = 0; j < part.length; j++)
                {
                    part[j] = (byte) randomGenerator.nextInt(255);
                }

                md5.update(part);
                partContainer.MD5 = String.format("%032X", new BigInteger(1, md5.digest()));

                final OutputStream partOutputStream = ouputStreamPartitioner.createNextSubOutputStream();
                if(maxTransferLength == 0)
                {
                    for (int j = 0; j < part.length; j++)
                    {
                        partOutputStream.write(part[j]);
                    }
                }
                else
                {
                    int todo = part.length;
                    int pointer = 0;
                    while (todo > 0)
                    {
                        int len = maxTransferLength;
                        if(maxTransferLength > todo)
                        {
                            len = todo;
                        }
                        partOutputStream.write(part, pointer, len);
                        todo -= len;
                        pointer += len;
                    }
                }
                partOutputStream.close();

                partList.add(partContainer);

                if(i > 50)
                {
                    i += 10;
                }

                if(i > 100)
                {
                    i += 20;
                }

                if(i > 1000)
                {
                    i += 30;
                }

                if(i > 5000)
                {
                    i += 50;
                }
            }

            if(this.mode == MODE_FILE)
            {
                testInputStream = new FileInputStream(testStreamFile);
            }

            if(this.mode == MODE_MEMORY)
            {
                testInputStream = new ByteArrayInputStream(((ByteArrayOutputStream) testOutputStream).toByteArray());
            }

            testOutputStream.close();
            testOutputStream = null;

            final IInputStreamPartitioner inputStreamPartitioner = partitionerFactoryImpl.newInputStreamPartitioner(testInputStream);

            final byte[] buffer = new byte[1080];
            int len;
            int size;

            for (final PartContainer partContainer : partList)
            {
                final MessageDigest md5 = MessageDigest.getInstance("MD5");
                md5.reset();

                size = 0;
                final InputStream partInputStream = inputStreamPartitioner.getNextSubInputStream();
                assertNotNull(partInputStream, "substream should not be null");

                while ((len = partInputStream.read(buffer, 0, buffer.length)) > 0)
                {
                    md5.update(buffer, 0, len);
                    size += len;

                    if(size > partContainer.size)
                    {
                        assertEquals(partContainer.size, size, "size of container should not be less than substream");
                    }
                }

                assertEquals(partContainer.size, size, "size of container should be same");
                assertEquals(partContainer.MD5, String.format("%032X", new BigInteger(1, md5.digest())),
                        "md5 of container should be same");

                partInputStream.close();
            }

            assertNull(inputStreamPartitioner.getNextSubInputStream(),
                    "inputstreampartiioner should ends to provide partIn");

            testInputStream.close();
            testInputStream = null;
        }
        finally
        {
            try
            {
                if(testInputStream != null)
                {
                    testInputStream.close();
                }
            }
            catch (final Exception e) { }

            try
            {
                if(testOutputStream != null)
                {
                    testOutputStream.close();
                }
            }
            catch (final Exception e) { }

            try
            {
                if(testStreamFile != null)
                {
                    if(!testStreamFile.delete())
                    {
                        testStreamFile.deleteOnExit();
                    }
                }
            }
            catch (final Exception e) { }
        }
    }
}