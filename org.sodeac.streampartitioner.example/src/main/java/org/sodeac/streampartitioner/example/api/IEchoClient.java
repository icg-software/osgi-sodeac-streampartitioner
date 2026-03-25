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
package org.sodeac.streampartitioner.example.api;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.NoSuchPaddingException;

public interface IEchoClient
{
    void connect() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException;

    void createSubStream() throws IOException, NoSuchAlgorithmException;

    void writeInSubStream(byte[] b, int off, int len) throws IOException;

    void writeInSubStream(byte[] b) throws IOException;

    void closeSubStream() throws IOException;

    void flushBlockCipherStream() throws IOException;

    void disconnect() throws IOException;

    void waitUntilNothingHappenOnInpustream(long ms);

    List<SubStreamFingerprint> getSubOutputStreamFingerprintList();

    List<SubStreamFingerprint> getSubInputStreamFingerprintList();
}
