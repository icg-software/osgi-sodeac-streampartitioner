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

import lombok.val;

public class StreamPartitionerConsumerTest
{
    public static final int MODE_FILE = 0;
    public static final int MODE_MEMORY = 1;

    private final int mode = MODE_MEMORY; // better for my jenkins-tests on sdcard-driven rpi

    static IntStream parameters()
    {
        final IntStream.Builder builder = IntStream.builder();

        for (int i = 0; i <= 10800; i++)
        {
            builder.add(i);

            if(i > 50) { i += 5; }
            if(i > 100) { i += 10; }
            if(i > 1000) { i += 20; }
            if(i > 5000) { i += 30; }
        }

        return builder.build();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testProduceConsumeAndCompare(final int maxTransferLength) throws IOException, NoSuchAlgorithmException
    {
        System.out.println("[INFO]\t\tRun Consumer Test:  " + maxTransferLength);

        final StreamPartitionerFactoryImpl partitionerFactoryImpl = new StreamPartitionerFactoryImpl();
        final RandomGenerator randomGenerator = RandomGenerator.getDefault();
        final List<PartContainer> partList = new ArrayList<>();

        if(this.mode == MODE_FILE)
        {
            final File tempDir = new File(System.getProperty("java.io.tmpdir"));
            val testStreamFile = new File(tempDir, getClass().getSimpleName() + "_" + UUID.randomUUID() + ".stream");

            try (OutputStream testOutputStream = new FileOutputStream(testStreamFile))
            {
                produce(partitionerFactoryImpl, randomGenerator, partList, testOutputStream);
            }

            try (InputStream testInputStream = new FileInputStream(testStreamFile))
            {
                consume(partitionerFactoryImpl, partList, testInputStream, maxTransferLength);
            }
        }
        else
        {
            final byte[] data;

            try (ByteArrayOutputStream testOutputStream = new ByteArrayOutputStream())
            {
                produce(partitionerFactoryImpl, randomGenerator, partList, testOutputStream);
                data = testOutputStream.toByteArray();
            }

            try (InputStream testInputStream = new ByteArrayInputStream(data))
            {
                consume(partitionerFactoryImpl, partList, testInputStream, maxTransferLength);
            }
        }

    }

    private void produce(final StreamPartitionerFactoryImpl partitionerFactoryImpl,
            final RandomGenerator randomGenerator,
            final List<PartContainer> partList,
            final OutputStream testOutputStream) throws IOException, NoSuchAlgorithmException
    {
        final IOutputStreamPartitioner outputStreamPartitioner = partitionerFactoryImpl.newOutputStreamPartitioner(testOutputStream);

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

            try (OutputStream partOutputStream = outputStreamPartitioner.createNextSubOutputStream())
            {
                partOutputStream.write(part);
            }

            partList.add(partContainer);

            if(i > 50) { i += 10; }
            if(i > 100) { i += 20; }
            if(i > 1000) { i += 50; }
            if(i > 5000) { i += 100; }
        }
    }

    private void consume(final StreamPartitionerFactoryImpl partitionerFactoryImpl,
            final List<PartContainer> partList,
            final InputStream testInputStream,
            final int maxTransferLength) throws IOException, NoSuchAlgorithmException
    {
        final IInputStreamPartitioner inputStreamPartitioner = partitionerFactoryImpl.newInputStreamPartitioner(testInputStream);

        final byte[] buffer = new byte[10800];

        for (final PartContainer partContainer : partList)
        {
            final MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.reset();

            int size = 0;

            try (InputStream partInputStream = inputStreamPartitioner.getNextSubInputStream())
            {
                assertNotNull(partInputStream, "substream should not be null");

                if(maxTransferLength == 0)
                {
                    int readed;
                    final byte[] readByte = new byte[1];

                    while ((readed = partInputStream.read()) > -1)
                    {
                        readByte[0] = (byte) readed;
                        md5.update(readByte);
                        size++;

                        if(size > partContainer.size)
                        {
                            assertEquals(partContainer.size, size,
                                    "size of container should not be less than substream");
                        }
                    }
                }
                else
                {
                    int len;
                    int readLen = maxTransferLength;

                    if(readLen + size > buffer.length)
                    {
                        readLen = buffer.length - size;
                    }

                    while ((len = partInputStream.read(buffer, size, readLen)) > 0)
                    {
                        md5.update(buffer, size, len);
                        size += len;

                        if(size > partContainer.size)
                        {
                            assertEquals(partContainer.size, size,
                                    "size of container should not be less than substream");
                        }
                    }
                }
            }

            assertEquals(partContainer.size, size, "size of container should be same");
            assertEquals(partContainer.MD5, String.format("%032X", new BigInteger(1, md5.digest())),
                    "md5 of container should be same");
        }

        assertNull(inputStreamPartitioner.getNextSubInputStream(),
                "inputstreampartiioner should ends to provide partIn");
    }
}