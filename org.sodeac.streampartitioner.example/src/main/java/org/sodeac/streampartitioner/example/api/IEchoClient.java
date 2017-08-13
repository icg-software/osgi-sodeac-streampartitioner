package org.sodeac.streampartitioner.example.api;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.NoSuchPaddingException;

public interface IEchoClient
{
	public void connect() throws IOException,NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,  InvalidAlgorithmParameterException;
	public void createSubStream() throws IOException, NoSuchAlgorithmException;
	public void writeInSubStream(byte[] b, int off, int len) throws IOException;
	public void writeInSubStream(byte[] b) throws IOException;
	public void closeSubStream() throws IOException;
	public void flushBlockCipherStream() throws IOException;
	public void disconnect() throws IOException;
	public void waitUntilNothingHappenOnInpustream(long ms);
	public List<SubStreamFingerprint> getSubOutputStreamFingerprintList();
	public List<SubStreamFingerprint> getSubInputStreamFingerprintList();
}
