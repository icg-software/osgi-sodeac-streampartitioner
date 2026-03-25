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

    public EchoServerConnection init(final IStreamPartitionerFactory factory, final Socket socket, final SecretKeySpec keySpec) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException
    {
        final Cipher decryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec("................".getBytes()));

        final Cipher encryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec("................".getBytes()));

        this.outputStreamPartitioner = factory.newOutputStreamPartitioner(new CipherOutputStream(socket.getOutputStream(), encryptCipher));
        this.inputStreamPartitioner = factory.newInputStreamPartitioner(new CipherInputStream(socket.getInputStream(), decryptCipher));
        this.socket = socket;
        return this;
    }

    @Override
    public void run()
    {
        int len;
        final byte[] buffer = new byte[1080];

        final byte[] flushBlockCipher = new byte[32];
        for (int i = 0; i < flushBlockCipher.length; i++)
        {
            flushBlockCipher[i] = '.';
        }
        flushBlockCipher[0] = '\n';
        flushBlockCipher[31] = '\n';

        InputStream subInputStream = null;
        OutputStream subOutputStream = null;
        try
        {

            while ((subInputStream = this.inputStreamPartitioner.getNextSubInputStream()) != null)
            {
                subOutputStream = this.outputStreamPartitioner.createNextSubOutputStream();

                while ((len = subInputStream.read(buffer)) > 0)
                {
                    subOutputStream.write(buffer, 0, len);
                }

                subInputStream.close();
                subInputStream = null;
                subOutputStream.flush();
                subOutputStream.close();
                subOutputStream = null;

                this.outputStreamPartitioner.getParentOutputStream().write(flushBlockCipher);        // force finish current clock cipher
                this.outputStreamPartitioner.getParentOutputStream().flush();
            }
        }
        catch (final Exception e)
        {
            if(!(e instanceof BadPaddingException))
            {
                e.printStackTrace();
            }
        }

        if(subInputStream != null)
        {
            try { subInputStream.close(); }catch (final Exception e) { }
        }

        if(subOutputStream != null)
        {
            try { subOutputStream.close(); }catch (final Exception e) { }
        }

        try { this.inputStreamPartitioner.getParentInputStream().close(); }catch (final Exception e) { }
        try { this.outputStreamPartitioner.getParentOutputStream().close(); }catch (final Exception e) { }

        try { this.socket.close(); }catch (final Exception e) { }
    }

}
