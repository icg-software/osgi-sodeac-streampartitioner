/*******************************************************************************
 * Copyright (c) 2017, 2019 Sebastian Palarus All rights reserved. This program
 * and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html Contributors:
 * Sebastian Palarus - initial API and implementation
 *******************************************************************************/
package org.sodeac.streampartitioner.impl;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;

public class VeryTinySubtreamsTest
{
    @Test
    public void testTinySubstreams() throws IOException
    {
        final StreamPartitionerFactoryImpl streamPartitionerFactory = new StreamPartitionerFactoryImpl();
        
        final List<String> shortMessageListInput = new ArrayList<String>();
        
        shortMessageListInput.add("a");
        shortMessageListInput.add("b");
        shortMessageListInput.add("");
        shortMessageListInput.add("c");
        
        final ByteArrayOutputStream parentOutputStream = new ByteArrayOutputStream();
        
        final IOutputStreamPartitioner outputStreamPartitioner = streamPartitionerFactory.newOutputStreamPartitioner(parentOutputStream);
        
        for (final String shortMessage : shortMessageListInput)
        {
            final OutputStream subtream = outputStreamPartitioner.createNextSubOutputStream();
            if (!shortMessage.isEmpty())
            {
                subtream.write(shortMessage.getBytes());
            }
            subtream.close();
        }
        parentOutputStream.close();
        
        final ByteArrayInputStream fileInputStream = new ByteArrayInputStream(parentOutputStream.toByteArray());
        
        final List<String> shortMessageListOutput = new ArrayList<String>();
        
        final IInputStreamPartitioner inputStreamPartitioner = streamPartitionerFactory.newInputStreamPartitioner(fileInputStream);
        InputStream inputStream;
        int len;
        final byte[] readBuffer = new byte[1024];
        while ((inputStream = inputStreamPartitioner.getNextSubInputStream()) != null)
        {
            final StringBuilder content = new StringBuilder();
            while ((len = inputStream.read(readBuffer)) > 0)
            {
                
                content.append(new String(readBuffer, 0, len));
            }
            inputStream.close();
            shortMessageListOutput.add(content.toString());
        }
        fileInputStream.close();
        
        assertEquals("size of shortMessageListOutput should equals to size of shortMessageListInput", shortMessageListInput.size(), shortMessageListOutput.size());
        
        for (int i = 0; i < shortMessageListInput.size(); i++)
        {
            assertEquals("shortMessageOutput " + i + " should equals to shortMessageInput " + i, shortMessageListInput.get(i), shortMessageListOutput.get(i));
        }
    }
}
