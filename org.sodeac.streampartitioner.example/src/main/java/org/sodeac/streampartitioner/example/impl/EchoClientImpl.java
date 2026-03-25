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
package org.sodeac.streampartitioner.example.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.sodeac.streampartitioner.api.IInputStreamPartitioner;
import org.sodeac.streampartitioner.api.IOutputStreamPartitioner;
import org.sodeac.streampartitioner.api.IStreamPartitionerFactory;
import org.sodeac.streampartitioner.example.api.IEchoClient;
import org.sodeac.streampartitioner.example.api.SubStreamFingerprint;

public class EchoClientImpl implements IEchoClient
{
    // globale container Objects

    private int port = -1;
    private SecretKeySpec keySpec = null;
    private IStreamPartitionerFactory streamPartitionerFactory = null;

    // connection

    private Socket socket = null;
    private IOutputStreamPartitioner outputStreamPartitioner = null;
    private IInputStreamPartitioner inputStreamPartitioner = null;

    private List<SubStreamFingerprint> subOutputStreamFingerprintList = null;
    private List<SubStreamFingerprint> subInputStreamFingerprintList = null;

    // current sender state (sender / subOutputStream)

    private SubStreamFingerprint currentWorkSubOutputStreamFingerprint;
    private OutputStream currentWorkSubOutputStream = null;
    private int inputStreamNumber = 0;

    // current receive state (receiver / echo / subOutputStream)
    private volatile long lastInputStreamEvent;

    public EchoClientImpl(final int port, final IStreamPartitionerFactory streamPartitionerFactory, final SecretKeySpec keySpec)
    {
        super();
        this.port = port;
        this.streamPartitionerFactory = streamPartitionerFactory;
        this.keySpec = keySpec;
    }

    @Override
    public void connect() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException
    {
        this.socket = new Socket("localhost", this.port);
        this.inputStreamNumber = 0;

        final Cipher decryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decryptCipher.init(Cipher.DECRYPT_MODE, this.keySpec, new IvParameterSpec("................".getBytes()));

        final Cipher encryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, this.keySpec, new IvParameterSpec("................".getBytes()));

        this.outputStreamPartitioner = this.streamPartitionerFactory.newOutputStreamPartitioner(new CipherOutputStream(this.socket.getOutputStream(), encryptCipher));
        this.inputStreamPartitioner = this.streamPartitionerFactory.newInputStreamPartitioner(new CipherInputStream(this.socket.getInputStream(), decryptCipher));

        this.subOutputStreamFingerprintList = Collections.synchronizedList(new ArrayList<SubStreamFingerprint>());
        this.subInputStreamFingerprintList = Collections.synchronizedList(new ArrayList<SubStreamFingerprint>());

        final Thread tcpReceivingThread = new Thread()
        {

            // This thread reads the echo-(sub)streams (response from server) and calculate

            @Override
            public void run()
            {
                int len;
                final byte[] buffer = new byte[1080];

                try
                {
                    int outputStreamNumber = 0;
                    InputStream currentWorkSubInputStream = null;
                    while ((currentWorkSubInputStream = EchoClientImpl.this.inputStreamPartitioner.getNextSubInputStream()) != null)
                    {
                        EchoClientImpl.this.lastInputStreamEvent = System.currentTimeMillis();
                        final SubStreamFingerprint currentWorkSubInputStreamFingerprint = new SubStreamFingerprint(outputStreamNumber);

                        try
                        {
                            while ((len = currentWorkSubInputStream.read(buffer)) > 0)
                            {
                                EchoClientImpl.this.lastInputStreamEvent = System.currentTimeMillis();
                                currentWorkSubInputStreamFingerprint.processBytes(buffer, 0, len);
                            }
                        }
                        catch (final SocketException e) { }
                        catch (final Exception e)
                        {
                            e.printStackTrace();
                        }

                        try { currentWorkSubInputStream.close(); }catch (final Exception e) { }

                        outputStreamNumber++;
                        currentWorkSubInputStreamFingerprint.createMD5String();
                        EchoClientImpl.this.subOutputStreamFingerprintList.add(currentWorkSubInputStreamFingerprint);

                        EchoClientImpl.this.lastInputStreamEvent = System.currentTimeMillis();
                    }
                }
                catch (final SocketException e) { }
                catch (final Exception e)
                {
                    e.printStackTrace();
                }

            }
        };
        tcpReceivingThread.setDaemon(true);
        tcpReceivingThread.start();

    }

    @Override
    public void createSubStream() throws IOException, NoSuchAlgorithmException
    {
        // create new substream
        this.currentWorkSubOutputStream = this.outputStreamPartitioner.createNextSubOutputStream();
        this.currentWorkSubOutputStreamFingerprint = new SubStreamFingerprint(this.inputStreamNumber);
    }

    @Override
    public void writeInSubStream(final byte[] b) throws IOException
    {
        writeInSubStream(b, 0, b.length);
    }

    @Override
    public void writeInSubStream(final byte[] b, final int off, final int len) throws IOException
    {
        // write bytes to substream
        this.currentWorkSubOutputStream.write(b, off, len);

        // calculate informations about length and checksum
        this.currentWorkSubOutputStreamFingerprint.processBytes(b, off, len);
    }

    @Override
    public void closeSubStream() throws IOException
    {

        // close SubInputStream
        this.currentWorkSubOutputStream.flush();
        this.currentWorkSubOutputStream.close();
        this.outputStreamPartitioner.getParentOutputStream().flush();

        // writing informations about length and checksum
        this.currentWorkSubOutputStreamFingerprint.createMD5String();
        this.subInputStreamFingerprintList.add(this.currentWorkSubOutputStreamFingerprint);

        // clean work refs
        this.currentWorkSubOutputStreamFingerprint = null;
        this.currentWorkSubOutputStream = null;

        // update information about substreamsize
        this.inputStreamNumber++;
    }

    @Override public void flushBlockCipherStream() throws IOException
    {
        if(this.currentWorkSubOutputStream != null)
        {
            throw new IOException("flushstream only works without open substreams");
        }
        final byte[] flushBlockCipher = new byte[32];
        for (int i = 0; i < flushBlockCipher.length; i++)
        {
            flushBlockCipher[i] = '.';
        }
        flushBlockCipher[0] = '\n';
        flushBlockCipher[31] = '\n';
        this.outputStreamPartitioner.getParentOutputStream().write(flushBlockCipher);
        this.outputStreamPartitioner.getParentOutputStream().flush();
    }

    @Override
    public void disconnect() throws IOException
    {
        this.outputStreamPartitioner.getParentOutputStream().close();
        this.socket.close();
    }

    @Override
    public List<SubStreamFingerprint> getSubOutputStreamFingerprintList()
    {
        return this.subOutputStreamFingerprintList;
    }

    @Override
    public List<SubStreamFingerprint> getSubInputStreamFingerprintList()
    {
        return this.subInputStreamFingerprintList;
    }

    @Override
    public void waitUntilNothingHappenOnInpustream(final long ms)
    {
        final int maxWaitTime = 13;
        long current = System.currentTimeMillis();
        while (current < (this.lastInputStreamEvent + ms))
        {
            long waitTime = (this.lastInputStreamEvent + ms) - current;
            if(waitTime > maxWaitTime)
            {
                waitTime = maxWaitTime;
            }
            try
            {
                Thread.sleep(waitTime);
            }
            catch (final Exception e)
            {
                e.printStackTrace();
            }

            current = System.currentTimeMillis();
        }
    }
}
