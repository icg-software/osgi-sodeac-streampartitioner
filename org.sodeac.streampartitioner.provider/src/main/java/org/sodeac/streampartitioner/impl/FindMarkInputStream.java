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

import java.io.IOException;
import java.io.InputStream;

/**
 * FindMarkInputStream extracts substreams from parentstream. Methodes read(...) only returns bytes from substreams, cut off marks and return -1 if substream is ended. 
 * If substream is ended, no more data will readed from parentstream. Inadvertently too much readed bytes will be published in bytearray carryout.
 * 
 * <p>
 * 
 * This class used by {@link CloseDelegateInputStream} as parentInputStream.
 * 
 * @author Sebastian Palarus
 *
 */
public class FindMarkInputStream extends InputStream
{
	// parent InputStream (File/Network/Pipe ...)
	protected InputStream 		parentInputStream 					= null													;
	
	// buffers
	protected boolean 			parentInputStreamIsEnded 			= false													;
	protected byte[] 			readAheadBuffer 					= new byte[BUFFER_SIZE * 2]								;
	protected byte[] 			singleByte 							= new byte[1]											;
	protected byte[] 			carryout 							= null													;
	protected byte[] 			matchBeginBuffer 					= null													;
	
	// constants
	public static final byte[] 	BEGIN_PATTERN_1 					= StreamPartitionerFactoryImpl.OFFSET_MARK.getBytes()	;
	public static final byte[] 	BEGIN_PATTERN_2 					= StreamPartitionerFactoryImpl.END_MARK.getBytes()		;
	
	public static final int 	BEGIN_MATCH_PROGRESS_NONE 			= 0														;
	public static final int 	BEGIN_MATCH_PROGRESS_OFFSET_PART 	= 1														;
	public static final int 	BEGIN_MATCH_PROGRESS_VAR_PART 		= 2														;
	public static final int 	BEGIN_MATCH_PROGRESS_END_PART 		= 3														;
	public static final int 	BEGIN_MATCH_PROGRESS_FINISHED 		= 4														;
	public static final int 	BUFFER_SIZE 						= 1080													;
	public static final int 	MARK_SIZE 							= 90													;
	
	// used area in buffer at all (payload, marks, unknown)
	protected int 				readAheadBufferOffset 				= 0														;
	protected int 				readAheadBufferLength 				= 0														;
	
	// used area for payload data (substream)
	protected int 				clientOffset 						= 0														;
	protected int 				clientLength 						= 0														;
	
	public boolean 				substreamEnds						= false													;
	
	/**
	 * 
	 * @param parentInputStream parentstream provides substreams
	 * @param carryin too much readed bytes from previews substream
	 * @throws IOException
	 */
	public FindMarkInputStream(InputStream parentInputStream, byte[] carryin) throws IOException
	{
		super();
		this.parentInputStream = parentInputStream;
		
		int off = 0;
		if((carryin != null) && (carryin.length > 0))
		{
			System.arraycopy(carryin, 0, readAheadBuffer, 0, carryin.length);
			off = carryin.length;
			readAheadBufferLength = carryin.length;
		}
		int len;
		
		int readToFindMark = MARK_SIZE - off;
		if(readToFindMark > 0)
		{
			if((len = parentInputStream.read(readAheadBuffer, off, readToFindMark)) > 0 )
			{
				off +=len;
				readAheadBufferLength += len;
			}
			else
			{
				parentInputStreamIsEnded = true;
			}
		}
		findBeginMark();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int available() throws IOException
	{
		return readAheadBufferLength - readAheadBufferOffset;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException
	{
		// don't close parentinputstream => do it manual
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mark(int readlimit)
	{
		this.parentInputStream.mark(readlimit);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean markSupported()
	{
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read() throws IOException
	{
		int len = read(singleByte,0,1);
		if(len < 0)
		{
			return -1;
		}
		return ((int) singleByte[0]) & 0xff;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read(byte[] b) throws IOException
	{
		return read(b,0,b.length);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int read(byte[] b, int off, int len) throws IOException
	{
		// No StartSequence
		if(matchBeginBuffer == null)
		{
			return -1;
		}
		
		// invalid intern state
		if(clientOffset < readAheadBufferOffset)
		{
			throw new IOException("clientOffset < readAheadBufferOffset");
		}
		
		// harmonize offsets
		if(clientOffset > readAheadBufferOffset)
		{
			int shift = clientOffset - readAheadBufferOffset;
			for(int i = 0; i < readAheadBufferLength; i++)
			{
				readAheadBuffer[i]= readAheadBuffer[i+shift];
			}
			readAheadBufferLength -= shift;
			clientOffset = readAheadBufferOffset;
		}
		
		// return bytes in buffer I
		if(clientLength > 0)
		{
			if(len > clientLength)
			{
				len = clientLength;
			}
			System.arraycopy(readAheadBuffer, clientOffset, b, off,len);
			clientLength -= len;
		    clientOffset +=len;
		    readAheadBufferOffset += len;
			readAheadBufferLength -= len;
			return len;
		}
		
		
		
		// buffer is empty and  payload is done
		if(substreamEnds)
		{
			return -1;
		}
		
		// shift to position 0
		if(readAheadBufferLength > 0)
		{
			if(readAheadBufferOffset > 0)
			{
				for(int i = 0; i < readAheadBufferLength; i++)
				{
					readAheadBuffer[i]= readAheadBuffer[i+readAheadBufferOffset];
				}
			}
		}
		
		// invalid intern state
		if(readAheadBufferLength < 0)
		{
			throw new IOException("readAheadBufferLength < 0 : " + readAheadBufferLength);
		}
				
		// current state: everthing is normalized
		
		readAheadBufferOffset = 0; 
		clientOffset = 0;
		
		
		byte byteToTestForEndSequence;
		int currentEndSequencePositiveMatchingLength = 0;
		int currentEndSequencePositiveMatchingOffset = -1;
		int currentEndSequenceFindingPointer = 0;
		
		// read next bytes from parentStream into buffer readAheadBuffer
		int readNext =  BUFFER_SIZE - readAheadBufferLength;
		if(readNext > 0)
		{
			
			int readed = this.parentInputStream.read(readAheadBuffer, readAheadBufferOffset+ readAheadBufferLength, readNext);
			if(readed < 0)
			{
				this.parentInputStreamIsEnded = true;
				
				if(readAheadBufferLength == 0)
				{
					return -1;
				}
			}
			else
			{
				readAheadBufferLength += readed;
			}
			
			// current state: something is readed; offset in buffer: 0 (readAheadBufferOffset && clientOffset)
			
			// test for endsequence
			for(; currentEndSequenceFindingPointer < readAheadBufferLength; currentEndSequenceFindingPointer++)
			{
				byteToTestForEndSequence = readAheadBuffer[currentEndSequenceFindingPointer];
				if(byteToTestForEndSequence != matchBeginBuffer[currentEndSequencePositiveMatchingLength])
				{
					// first byte of endsequence must be unique in endsequence => potential endsequence start (everytime)
					if(byteToTestForEndSequence == matchBeginBuffer[0])
					{
						currentEndSequencePositiveMatchingOffset= currentEndSequenceFindingPointer;
						currentEndSequencePositiveMatchingLength = 1;
						continue;
					}
					// payloadbyte of substream (everytime)
					currentEndSequencePositiveMatchingLength = 0;
					currentEndSequencePositiveMatchingOffset = -1;
					continue;
					}
				if(currentEndSequencePositiveMatchingLength == 0)
				{
					currentEndSequencePositiveMatchingOffset= currentEndSequenceFindingPointer;
				}
				currentEndSequencePositiveMatchingLength++;
				
				if(currentEndSequencePositiveMatchingLength == matchBeginBuffer.length)
				{
					substreamEnds = true;
				
					// write carry out by copying the leftover of readed bytes
					if((currentEndSequenceFindingPointer+1) < readAheadBufferLength)
					{
						int j = currentEndSequenceFindingPointer + 1;
						carryout = new byte[readAheadBufferLength -j];
						for(int x = 0; j < readAheadBufferLength ; j++,x++)
						{
							carryout[x] = readAheadBuffer[j];
						}
					}
					clientLength = currentEndSequencePositiveMatchingOffset - clientOffset;
					
					// return bytes in buffer II
					if(len > clientLength)
					{
						len = clientLength;
					}
					if(len == 0)
					{
						// all readed byte belongs to endsequence
						return -1;
					}
					
					System.arraycopy(readAheadBuffer, clientOffset, b, off,len);
					clientLength -= len;
					clientOffset +=len;
					readAheadBufferOffset += len;
					readAheadBufferLength -= len;
					
					return len;
				}
			}	
		}	
		
		
		// current state:  still in substream or endsequence
		
		if(currentEndSequencePositiveMatchingLength == 0)
		{
			// not in endsequence => all bytes are payload (part of substream)
			clientLength = readAheadBufferLength - clientOffset;
			
			// return bytes in buffer III
			if(len > clientLength)
			{
				len = clientLength;
			}
			System.arraycopy(readAheadBuffer, clientOffset, b, off,len);
			clientLength -= len;
			clientOffset +=len;
			readAheadBufferOffset += len;
			readAheadBufferLength -= len;
			
			return len;
		}
		
		// current state: in endsequence

		// read ahead able to test current endsequence until end
		int missingToCompleteSeq = matchBeginBuffer.length - currentEndSequencePositiveMatchingLength;
		int readtodo = missingToCompleteSeq;
		while(readtodo > 0)
		{
			int readed = this.parentInputStream.read(readAheadBuffer, readAheadBufferOffset + readAheadBufferLength, readtodo);
			if(readed < 0)
			{
				this.parentInputStreamIsEnded = true;
				break;
			}
			readAheadBufferLength += readed;
			readtodo -= readed;
		}
		
		// test again readed bytes (now complete to check endsequence)
		
		for(; currentEndSequenceFindingPointer < readAheadBufferLength; currentEndSequenceFindingPointer++)
		{
			byteToTestForEndSequence = readAheadBuffer[currentEndSequenceFindingPointer];
			
			if(byteToTestForEndSequence != matchBeginBuffer[currentEndSequencePositiveMatchingLength])
			{
				// current state : all bytes are payload (part of substream) => returns bytes until potential endPartStartPosition
				
				clientLength = currentEndSequencePositiveMatchingOffset - clientOffset;
				// return bytes in buffer II
				if(len > clientLength)
				{
					len = clientLength;
				}
				System.arraycopy(readAheadBuffer, clientOffset, b, off,len);
				clientLength -= len;
				clientOffset += len;
				readAheadBufferOffset += len;
				readAheadBufferLength -= len;
				return len;
			}
			currentEndSequencePositiveMatchingLength++;
			if(currentEndSequencePositiveMatchingLength == matchBeginBuffer.length)
			{
				// state: endsequence complete
				substreamEnds = true;
				if((currentEndSequenceFindingPointer+1) < readAheadBufferLength)
				{
					throw new IOException("(i+1) < readAheadBufferLength");
				}
				
				clientLength = currentEndSequencePositiveMatchingOffset - clientOffset;
				
				// return bytes in buffer IV
				if(len > clientLength)
				{
					len = clientLength;
				}
				System.arraycopy(readAheadBuffer, clientOffset, b, off,len);
				clientLength -= len;
				clientOffset +=len;
				readAheadBufferOffset += len;
				readAheadBufferLength -= len;
				return len;
			}
		}
	
		throw new IOException("invalid intern state");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void reset() throws IOException
	{
		throw new IOException("reset not supported");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long skip(long n) throws IOException
	{
		throw new IOException("skip not supported");
	}

	/**
	 * find first seperatormark and bookmark this match as only valid endmark, to close this stream if finding these sequence again
	 * 
	 * @throws IOException
	 */
	private void findBeginMark() throws IOException
	{
		int matchBeginProgress = BEGIN_MATCH_PROGRESS_NONE;
		int matchBeginOffset = 0;
		int matchBeginLength = 0;
		int matchBeginEndpartOffset = 0;
		byte b;
		
		for(int i = 0; i < this.readAheadBufferLength; i++)
		{
			b = readAheadBuffer[i];
			if(matchBeginProgress == BEGIN_MATCH_PROGRESS_NONE)
			{
				if(b != BEGIN_PATTERN_1[0])
				{
					continue;
				}
				matchBeginOffset = i;
				matchBeginLength = 1;
				matchBeginProgress = BEGIN_MATCH_PROGRESS_OFFSET_PART;
				continue;
			}
			if(matchBeginProgress == BEGIN_MATCH_PROGRESS_OFFSET_PART)
			{
				if(b != BEGIN_PATTERN_1[matchBeginLength])
				{
					if(b == BEGIN_PATTERN_1[0])
					{
						matchBeginProgress = BEGIN_MATCH_PROGRESS_OFFSET_PART;
						matchBeginOffset = i;
						matchBeginLength = 1;
					}
					else
					{
						matchBeginProgress = BEGIN_MATCH_PROGRESS_NONE;
						matchBeginLength = 0;
						matchBeginOffset = 0;
					}
					continue;
				}
				matchBeginLength++;
				if(matchBeginLength == BEGIN_PATTERN_1.length)
				{
					matchBeginProgress = BEGIN_MATCH_PROGRESS_VAR_PART;
				}
				continue;
			}
			if(matchBeginProgress == BEGIN_MATCH_PROGRESS_VAR_PART)
			{
				if
				( ! 
					(
						(b == '_') || 
						(b == '-') || 
						(b == BEGIN_PATTERN_2[0]) || 
						((b >= '0') && (b <= '9')) ||
						((b >= 'a') && (b <= 'f'))
					)
				)
				{
					if(b == BEGIN_PATTERN_1[0])
					{
						matchBeginProgress = BEGIN_MATCH_PROGRESS_OFFSET_PART;
						matchBeginOffset = i;
						matchBeginLength = 1;
					}
					else
					{
						matchBeginProgress = BEGIN_MATCH_PROGRESS_NONE;
						matchBeginLength = 0;
						matchBeginOffset = 0;
					}
					continue;
				}
				matchBeginLength++;
				
				if(b == BEGIN_PATTERN_2[0])
				{
					matchBeginProgress = BEGIN_MATCH_PROGRESS_END_PART;
					matchBeginEndpartOffset = 1;
				}
				continue;
			}
			if(matchBeginProgress == BEGIN_MATCH_PROGRESS_END_PART)
			{
				if(b != BEGIN_PATTERN_2[matchBeginEndpartOffset])
				{
					if(b == BEGIN_PATTERN_1[0])
					{
						matchBeginProgress = BEGIN_MATCH_PROGRESS_OFFSET_PART;
						matchBeginOffset = i;
						matchBeginLength = 1;
					}
					else
					{
						matchBeginProgress = BEGIN_MATCH_PROGRESS_NONE;
						matchBeginLength = 0;
						matchBeginOffset = 0;
					}
					continue;
				}
				matchBeginLength++;
				matchBeginEndpartOffset++;
				
				if(matchBeginEndpartOffset == BEGIN_PATTERN_2.length)
				{
					matchBeginProgress = BEGIN_MATCH_PROGRESS_FINISHED;
					
					this.matchBeginBuffer = new byte[matchBeginLength];
					System.arraycopy(readAheadBuffer, matchBeginOffset, this.matchBeginBuffer, 0, matchBeginLength);
					
					this.readAheadBufferOffset = matchBeginOffset + matchBeginLength;
					this.readAheadBufferLength -= this.readAheadBufferOffset;
					this.clientOffset = this.readAheadBufferOffset;
					this.clientLength = 0;
					
					return;
				}
			}
		}
			
		if(parentInputStreamIsEnded)
		{
			return;
		}
		
		byte[] carryin = null;
		if(matchBeginLength > 0)
		{
			carryin = new byte[this.readAheadBufferLength - matchBeginOffset];
			System.arraycopy(readAheadBuffer, matchBeginOffset, carryin, 0, carryin.length);
		}
		
		int off = 0;
		if((carryin != null) && (carryin.length > 0))
		{
			System.arraycopy(carryin, 0, readAheadBuffer, 0, carryin.length);
			off = carryin.length;
			readAheadBufferLength = carryin.length;
		}
		else
		{
			readAheadBufferLength = 0;
		}
		
		int len;
		int readToFindMark = MARK_SIZE - off;
		if(readToFindMark > 0)
		{
			if((len = parentInputStream.read(readAheadBuffer, off, readToFindMark)) > 0 )
			{
				off +=len;
				readAheadBufferLength += len;
			}
			else
			{
				parentInputStreamIsEnded = true;
			}
		}
		findBeginMark();
	}

	/**
	 * 
	 * @return inadvertently too much readed bytes
	 */
	public byte[] getCarryout()
	{
		return carryout;
	}

	/**
	 * 
	 * @return true, if parentstream provide no more data, otherwise false
	 */
	public boolean isParentInputStreamEnded()
	{
		return parentInputStreamIsEnded;
	}
}
