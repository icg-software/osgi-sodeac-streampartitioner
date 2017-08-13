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
package org.sodeac.streampartitioner.example.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;
import org.sodeac.streampartitioner.api.IStreamPartitionerFactory;

public class EchoServerConnection extends Thread
{
	private IOutputStreamPartitioner outputStreamPartitioner = null;
	private IInputStreamPartitioner inputStreamPartitioner = null;
	private Socket socket = null;
	
	public EchoServerConnection()
	{
		super();
		super.setDaemon(true);
	}
	
	public EchoServerConnection init(IStreamPartitionerFactory factory,Socket socket, SecretKeySpec keySpec) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException
	{
		Cipher decryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		decryptCipher.init(Cipher.DECRYPT_MODE, keySpec,new IvParameterSpec("................".getBytes()));
		
		Cipher encryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec,new IvParameterSpec("................".getBytes()));
		
		this.outputStreamPartitioner = factory.newOutputStreamPartitioner(new CipherOutputStream(socket.getOutputStream(),encryptCipher));
		this.inputStreamPartitioner = factory.newInputStreamPartitioner(new CipherInputStream(socket.getInputStream(),decryptCipher));
		this.socket = socket;
		return this;
	}

	@Override
	public void run()
	{
		int len;
		byte[] buffer = new byte[1080];
		
		byte[] flushBlockCipher = new byte[32];
		for(int i = 0; i < flushBlockCipher.length; i++)
		{
			flushBlockCipher[i] = '.';
		}
		flushBlockCipher[0] = '\n';
		flushBlockCipher[31] = '\n';
		
		InputStream subInputStream = null;
		OutputStream subOutputStream = null;
		try
		{
			
			while((subInputStream = inputStreamPartitioner.getNextSubInputStream()) != null)
			{
				subOutputStream = outputStreamPartitioner.createNextSubOutputStream();
				
				while((len = subInputStream.read(buffer)) > 0)
				{
					subOutputStream.write(buffer, 0, len);
				}
				
				subInputStream.close(); subInputStream = null;
				subOutputStream.flush(); subOutputStream.close(); subOutputStream = null;
				
				outputStreamPartitioner.getParentOutputStream().write(flushBlockCipher);		// force finish current clock cipher
				outputStreamPartitioner.getParentOutputStream().flush();
			}
		}
		catch (Exception e) 
		{
			if(! (e instanceof BadPaddingException))
			{
				e.printStackTrace();
			}
		}
		
		if(subInputStream != null)
		{
			try {subInputStream.close();}catch (Exception e) {}
		}
		
		if(subOutputStream != null)
		{
			try {subOutputStream.close();}catch (Exception e) {}
		}
		
		try {inputStreamPartitioner.getParentInputStream().close();}catch (Exception e) {}
		try {outputStreamPartitioner.getParentOutputStream().close();}catch (Exception e) {}
		
		try {socket.close();}catch (Exception e) {}
	}

}
