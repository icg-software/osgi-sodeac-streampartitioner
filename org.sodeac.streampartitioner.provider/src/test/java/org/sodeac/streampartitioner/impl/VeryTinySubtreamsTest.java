package org.sodeac.streampartitioner.impl;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;

public class VeryTinySubtreamsTest
{
	@Test
	public void testTinySubstreams() throws IOException
	{
		StreamPartitionerFactoryImpl streamPartitionerFactory = new StreamPartitionerFactoryImpl();
		
		List<String> shortMessageListInput = new ArrayList<String>();
		
		shortMessageListInput.add("a");
		shortMessageListInput.add("b");
		shortMessageListInput.add("");
		shortMessageListInput.add("c");
		
		ByteArrayOutputStream parentOutputStream = new ByteArrayOutputStream();
		
		IOutputStreamPartitioner outputStreamPartitioner = streamPartitionerFactory.newOutputStreamPartitioner(parentOutputStream);
		
		for(String shortMessage : shortMessageListInput)
		{
			OutputStream subtream = outputStreamPartitioner.createNextSubOutputStream();
			if(! shortMessage.isEmpty())
			{
				subtream.write(shortMessage.getBytes());
			}
			subtream.close();
		}
		parentOutputStream.close();
		
		ByteArrayInputStream fileInputStream = new ByteArrayInputStream(parentOutputStream.toByteArray());
		
		List<String> shortMessageListOutput = new ArrayList<String>();
		
		IInputStreamPartitioner inputStreamPartitioner = streamPartitionerFactory.newInputStreamPartitioner(fileInputStream);
		InputStream inputStream;
		int len;
		byte[] readBuffer = new byte[1024];
		while((inputStream = inputStreamPartitioner.getNextSubInputStream()) != null)
		{
			StringBuilder content = new StringBuilder();
		    while((len = inputStream.read(readBuffer)) > 0)
		    {
		    	
		    	content.append(new String(readBuffer,0,len));
		    }
		    inputStream.close();
		    shortMessageListOutput.add(content.toString());
		}
		fileInputStream.close();
		
		assertEquals("size of shortMessageListOutput should equals to size of shortMessageListInput", shortMessageListInput.size(), shortMessageListOutput.size());
		
		for(int i = 0; i < shortMessageListInput.size(); i++)
		{
			assertEquals("shortMessageOutput " + i + " should equals to shortMessageInput " + i + "", shortMessageListInput.get(i), shortMessageListOutput.get(i));
		}
	}
}
