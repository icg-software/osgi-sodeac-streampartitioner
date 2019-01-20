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

import org.osgi.framework.BundleContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sodeac.streampartitioner.example.api.Events;
import org.sodeac.streampartitioner.example.api.IEchoClient;
import org.sodeac.streampartitioner.example.api.IEchoClientFactory;
import org.sodeac.streampartitioner.example.api.ServerNotRunningException;
import org.sodeac.streampartitioner.example.api.SubStreamFingerprint;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import static org.junit.Assert.*;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class EchoTest extends AbstractTest
{
	public static final int TCP_PORT = 13579; 
	public static final int DEFAULT_LENGTH = 1080;
	public static final int TEST_COUNT = 3;
	public static final int UNSIGNED_BYTE_LENGTH = 255;
	@Inject
	private BundleContext bundleContext;
	
	@Inject
	private EventAdmin eventAdmin;
	
	@Inject
	private IEchoClientFactory echoClientFactory;
	
	@Configuration
	public Option[] config() 
	{
		return super.config();
	}
	
	@Before
	public void setUp() 
	{
		System.out.println("");
		System.out.println("[INFO]\tStart TCP Server");
		
		Dictionary<String, Object> properties = new Hashtable<String,Object>();
		properties.put(Events.PROPERTY_TCP_PORT, EchoTest.TCP_PORT);
		Event startServerEvent = new Event(Events.TOPIC_REQUEST_START_SERVER,properties);
		eventAdmin.postEvent(startServerEvent);
	}
    
	@After
	public void tearDown()
	{
		System.out.println("[INFO]\tStop TCP Server");
		System.out.println("");
		
		Dictionary<String, Object> properties = new Hashtable<String,Object>();
		properties.put(Events.PROPERTY_TCP_PORT, EchoTest.TCP_PORT);
		Event stopServerEvent = new Event(Events.TOPIC_REQUEST_STOP_SERVER,properties);
		eventAdmin.postEvent(stopServerEvent);
	}
	
	@Test
	public void testSimpleEchoClient() throws IOException,NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException
	{
		
		assertNotNull("bundleContext should not be null" ,bundleContext);
		assertNotNull("EventAdmin should not be null" ,eventAdmin);
		assertNotNull("EchoClientFactory should not be null" ,echoClientFactory);
		
		AtomicLong publishSize = new AtomicLong();
		AtomicLong consumeSize = new AtomicLong();
		
		
		IEchoClient echoClient = null;
		
		// wait until tcpserver is started
		
		int attempts = 0;
		while(echoClient == null)
		{
			try
			{
				echoClient = echoClientFactory.createEchoClient();
				break;
			}
			catch (ServerNotRunningException e) 
			{
				if(attempts > 108)
				{
					break;
				}
				
				try {Thread.sleep(13);} catch (Exception e2) {}
			}
			
			attempts++;
		}
		
		assertNotNull("echo Client should not be null (server not started?)",echoClient);
		
		Random randomGenerator = new Random();
		byte[] buffer = new byte[DEFAULT_LENGTH];
		
		for(int i = 0; i < TEST_COUNT; i++)
		{
			System.out.println("[INFO]");
			System.out.println("[INFO]\tRun test " + i);
			System.out.println("[INFO]");
			System.out.println("[INFO]\tConnect to tcp server");
			System.out.println("[INFO]");
			echoClient.connect();
			
			for(int j = 0; j < DEFAULT_LENGTH; j++)
			{
				
				// create substream
				echoClient.createSubStream();
				
				// random length of substream
				int length = randomGenerator.nextInt(UNSIGNED_BYTE_LENGTH)+1;
				
				System.out.println("[INFO]\t\tCreate substream " + j + "/" + (DEFAULT_LENGTH -1 ) +" with length  " + (length * buffer.length));
				
				for(int k = 0; k < length; k++)
				{
					// create random content
					for(int l = 0; l < buffer.length; l++)
					{
						buffer[l] = (byte)randomGenerator.nextInt(UNSIGNED_BYTE_LENGTH);
					}
					
					// write into substream
					echoClient.writeInSubStream(buffer);
					publishSize.addAndGet(buffer.length);
				}
				
				// close substream
				echoClient.closeSubStream();
			}
			
			echoClient.flushBlockCipherStream();
			echoClient.waitUntilNothingHappenOnInpustream(DEFAULT_LENGTH);
			
			System.out.println("[INFO]");
			System.out.println("[INFO]\tDisconnect from tcp server ");
			System.out.println("[INFO]");
			echoClient.disconnect();
			
			List<SubStreamFingerprint> subInputStreamFingerprintList = echoClient.getSubInputStreamFingerprintList();
			List<SubStreamFingerprint> subOutputStreamFingerprintList = echoClient.getSubOutputStreamFingerprintList();
			
			assertEquals("subInputStream should not be empty",false,subInputStreamFingerprintList.isEmpty());
			assertEquals("size of subInputStream should equals to size of subOutputStream" , subInputStreamFingerprintList.size(), subOutputStreamFingerprintList.size());
			
			for(int j = 0 ; j < subInputStreamFingerprintList.size(); j++)
			{
				
				SubStreamFingerprint  subInputStreamFingerprint = subInputStreamFingerprintList.get(j);
				SubStreamFingerprint  subOutputStreamFingerprint = subOutputStreamFingerprintList.get(j);
				
				consumeSize.addAndGet(subInputStreamFingerprint.getSize());
				System.out.println("[INFO]\t\tTest substream (" + j + "/" + ( subInputStreamFingerprintList.size() - 1) + ") length: " + subInputStreamFingerprint.getSize() + " md5: " + subInputStreamFingerprint.getMD5());
				
				assertEquals("number of fingerprint should equals to position of fingerprintlist (input " + j + "/" + ( subInputStreamFingerprintList.size() - 1) + ")",j,subInputStreamFingerprint.getNumber());
				assertEquals("number of fingerprint should equals to position of fingerprintlist (output " + j + "/" + ( subInputStreamFingerprintList.size() - 1) + ")",j,subOutputStreamFingerprint.getNumber());
				
				assertEquals("size of outputSubStream (echo) should equals to size of respective inputSubStream (" + j + "/" + ( subInputStreamFingerprintList.size() - 1) + ")",subInputStreamFingerprint.getSize(),subOutputStreamFingerprint.getSize());
				assertEquals("checksum of outputSubStream (echo) should equals to checksum of respective inputSubStream (" + j + "/" + ( subInputStreamFingerprintList.size() - 1) + ")",subInputStreamFingerprint.getMD5(),subOutputStreamFingerprint.getMD5());
			}
		}
		
		System.out.println("[INFO]\t\t");
		System.out.println("[INFO]\t\tPublished bytes: " + publishSize.get());
		System.out.println("[INFO]\t\tConsumed bytes: " + consumeSize.get());
		System.out.println("[INFO]\t\t");
	}
	
}
