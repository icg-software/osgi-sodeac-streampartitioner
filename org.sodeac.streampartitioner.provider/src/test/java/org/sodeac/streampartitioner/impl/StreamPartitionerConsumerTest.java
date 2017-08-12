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
package org.sodeac.streampartitioner.impl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;

import static org.junit.Assert.*;

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
import java.util.Random;
import java.util.UUID;

@RunWith(Parameterized.class)
public class StreamPartitionerConsumerTest
{
	public static final int MODE_FILE = 0;
	public static final int MODE_MEMORY = 1;
	
	private int maxTransferLength = -1;
	private int mode = MODE_MEMORY; // better for my jenkins-tests on sdcard-driven rpi
	
	public StreamPartitionerConsumerTest(int maxTransferLength)
	{
		super();
		this.maxTransferLength = maxTransferLength;
	}
	
	@Parameters
	public static List<Object[]> parameters() 
	{
		List<Object[]> paramterList = new ArrayList<Object[]>();
		
		for(int i = 0; i <= 10800; i++)
		{
			paramterList.add(new Object[] {i});
			
			if( i > 50)
			{
				i += 5;
			}
			
			if(i > 100)
			{
				i += 10;
			}
			
			if(i > 1000)
			{
				i += 20;
			}
			
			if(i > 5000)
			{
				i += 30;
			}
			
		}
		return paramterList;
	}
	
	@Test
	public void testProduceConsumeAndCompare() throws IOException, NoSuchAlgorithmException
	{
		System.out.println("[INFO]\t\tRun Consumer Test:  " + maxTransferLength);
		File testStreamFile = null;
		OutputStream testOutputStream = null;
		InputStream testInputStream = null;
		
		try
		{
			StreamPartitionerFactoryImpl partitionerFactoryImpl = new StreamPartitionerFactoryImpl();
			
			Random randomGenerator = new Random();
			File tempDir = null;
			testStreamFile = null;
			
			if(mode == MODE_FILE)
			{
				tempDir = new File(System.getProperty("java.io.tmpdir"));
				testStreamFile = new File(tempDir,getClass().getSimpleName() + "_" + UUID.randomUUID().toString() + ".stream");
				testOutputStream = new FileOutputStream(testStreamFile);
			}
			
			if(mode == MODE_MEMORY)
			{
				testOutputStream = new ByteArrayOutputStream();
			}
			
			List<PartContainer> partList = new ArrayList<PartContainer>();
			
			IOutputStreamPartitioner ouputStreamPartitioner = partitionerFactoryImpl.newOutputStreamPartitioner(testOutputStream);
			
			
			for(int i = 0; i < 10800; i++)
			{
				MessageDigest md5 = MessageDigest.getInstance("MD5");
				md5.reset();
				PartContainer partContainer = new PartContainer();
				partContainer.size = i;
				
				byte[] part = new byte[i];
				for(int j = 0; j < part.length; j++)
				{
					part[j] = (byte)randomGenerator.nextInt(255);
				}
				
				md5.update(part);
				
				partContainer.MD5 =  String.format("%032X", new BigInteger(1,  md5.digest()));
				
				OutputStream partOutputStream = ouputStreamPartitioner.createNextSubOutputStream();
				partOutputStream.write(part);
				partOutputStream.close();
				
				partList.add(partContainer);
				
				if( i > 50)
				{
					i += 10;
				}
				
				if(i > 100)
				{
					i += 20;
				}
				
				if(i > 1000)
				{
					i += 50;
				}
				
				if(i > 5000)
				{
					i += 100;
				}
				
			}
			
			if(mode == MODE_FILE)
			{
				testInputStream = new FileInputStream(testStreamFile);
			}
			
			if(mode == MODE_MEMORY)
			{
				testInputStream = new ByteArrayInputStream(((ByteArrayOutputStream)testOutputStream).toByteArray());
			}
			
			testOutputStream.close();
			testOutputStream = null;
			
			IInputStreamPartitioner inputStreamPartitioner = partitionerFactoryImpl.newInputStreamPartitioner(testInputStream);
			
			byte[] buffer = new byte[10800];
			int len;
			int size;
			
			for(PartContainer partContainer : partList )
			{
				MessageDigest md5 = MessageDigest.getInstance("MD5");
				md5.reset();
				
				size = 0;
				InputStream partInputStream = inputStreamPartitioner.getNextSubInputStream();
				assertNotNull("substream should not be null",partContainer);
				
				if(maxTransferLength == 0)
				{
					//maxTransferLength = 1;
				}
				if(maxTransferLength == 0)
				{
					int readed;
					byte[] readbyte = new byte[1];
					
					while((readed = partInputStream.read()) > -1)
					{
						readbyte[0] = (byte)readed;
						
						md5.update(readbyte);
						size++;
						
						if(size > partContainer.size)
						{
							assertEquals("size of container should not be less than substream", partContainer.size, size);
						}
					}
				}
				else
				{
					int readlen = maxTransferLength;
					if(readlen + size > buffer.length)
					{
						readlen = buffer.length - size;
					}
					while((len = partInputStream.read(buffer, size,readlen)) > 0)
					{
						md5.update(buffer, size, len);
						size += len;
						
						if(size > partContainer.size)
						{
							assertEquals("size of container should not be less than substream", partContainer.size, size);
						}
					}
				}
				
				assertEquals("size of container should be same", partContainer.size, size);
				assertEquals("md5 of container should be same", partContainer.MD5, String.format("%032X", new BigInteger(1,  md5.digest())));
				
				partInputStream.close();
			}
			
			assertNull("inputstreampartiioner should ends to provide partIn", inputStreamPartitioner.getNextSubInputStream());
			
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
			catch (Exception e) {}
			
			try
			{
				if(testOutputStream != null)
				{
					testOutputStream.close();
				}
			}
			catch (Exception e) {}
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
			catch (Exception e) {}
		}
		
	}
}
