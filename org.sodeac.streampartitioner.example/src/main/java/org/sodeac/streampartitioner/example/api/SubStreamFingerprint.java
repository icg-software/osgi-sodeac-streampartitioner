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
package org.sodeac.streampartitioner.example.api;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SubStreamFingerprint
{
	public SubStreamFingerprint(int number) throws NoSuchAlgorithmException
	{
		super();
		this.md5 = MessageDigest.getInstance("MD5");
		this.md5.reset();
		this.number = number;
		this.size = 0L;
	}
	
	private MessageDigest md5;
	private int number;
	private long size;
	private String MD5;
	
	public int getNumber()
	{
		return number;
	}
	public long getSize()
	{
		return size;
	}
	public String getMD5()
	{
		return MD5;
	}
	public void processBytes(byte[] b,int off, int len)
	{
		this.size +=len;
		md5.update(b,off,len);
	}
	
	public void createMD5String()
	{
		MD5 =  String.format("%032X", new BigInteger(1,  md5.digest()));
	}
	
	
}
