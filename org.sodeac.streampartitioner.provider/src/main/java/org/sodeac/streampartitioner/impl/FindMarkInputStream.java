/*******************************************************************************
 * Copyright (c) 2017, 2019 Sebastian Palarus All rights reserved. This program
 * and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html Contributors:
 * Sebastian Palarus - initial API and implementation
 *******************************************************************************/
package org.sodeac.streampartitioner.impl;

import java.io.IOException;
import java.io.InputStream;

/**
 * FindMarkInputStream extracts substreams from parentstream. Methodes read(...)
 * only returns bytes from substreams, cut off marks and return -1 if substream
 * is ended. If substream is ended, no more data will readed from parentstream.
 * Inadvertently too much readed bytes will be published in bytearray carryout.
 * <p>
 * <p>
 * This class used by {@link CloseDelegateInputStream} as parentInputStream.
 *
 * @author Sebastian Palarus
 */
public class FindMarkInputStream extends InputStream
{
    // parent InputStream (File/Network/Pipe ...)
    protected InputStream parentInputStream = null;
    
    // buffers
    protected boolean parentInputStreamIsEnded = false;
    protected byte[] readAheadBuffer = new byte[BUFFER_SIZE * 2];
    protected byte[] singleByte = new byte[1];
    protected byte[] carryout = null;
    protected byte[] matchBeginBuffer = null;
    
    // constants
    public static final byte[] BEGIN_PATTERN_1 = StreamPartitionerFactoryImpl.OFFSET_MARK.getBytes();
    public static final byte[] BEGIN_PATTERN_2 = StreamPartitionerFactoryImpl.END_MARK.getBytes();
    
    public static final int BEGIN_MATCH_PROGRESS_NONE = 0;
    public static final int BEGIN_MATCH_PROGRESS_OFFSET_PART = 1;
    public static final int BEGIN_MATCH_PROGRESS_VAR_PART = 2;
    public static final int BEGIN_MATCH_PROGRESS_END_PART = 3;
    public static final int BEGIN_MATCH_PROGRESS_FINISHED = 4;
    public static final int BUFFER_SIZE = 1080;
    public static final int MARK_SIZE = 90;
    
    public static final int MIN_SITE_CLUSTERED_SHIFT = 13;
    
    // used area in buffer at all (payload, marks, unknown)
    protected int readAheadBufferOffset = 0;
    protected int readAheadBufferLength = 0;
    
    // used area for payload data (substream)
    protected int clientOffset = 0;
    protected int clientLength = 0;
    
    public boolean substreamEnds = false;
    
    /**
     * @param parentInputStream parentstream provides substreams
     * @param carryin           too much readed bytes from previews substream
     *
     * @throws IOException
     */
    public FindMarkInputStream(final InputStream parentInputStream, final byte[] carryin) throws IOException
    {
        super();
        this.parentInputStream = parentInputStream;
        
        int off = 0;
        if ((carryin != null) && (carryin.length > 0))
        {
            System.arraycopy(carryin, 0, this.readAheadBuffer, 0, carryin.length);
            off = carryin.length;
            this.readAheadBufferLength = carryin.length;
        }
        final int len;
        
        final int readToFindMark = MARK_SIZE - off;
        if (readToFindMark > 0)
        {
            if ((len = parentInputStream.read(this.readAheadBuffer, off, readToFindMark)) > 0)
            {
                off += len;
                this.readAheadBufferLength += len;
            }
            else
            {
                this.parentInputStreamIsEnded = true;
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
        return this.readAheadBufferLength - this.readAheadBufferOffset;
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
    public void mark(final int readlimit)
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
        final int len = read(this.singleByte, 0, 1);
        if (len < 0)
        {
            return -1;
        }
        return ((int) this.singleByte[0]) & 0xff;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final byte[] b, final int off, int len) throws IOException
    {
        // No StartSequence
        if (this.matchBeginBuffer == null)
        {
            return -1;
        }
        
        // invalid intern state
        if (this.clientOffset != this.readAheadBufferOffset)
        {
            throw new IOException("clientOffset != readAheadBufferOffset");
        }
        
        // return bytes in buffer I
        if (this.clientLength > 0)
        {
            if (len > this.clientLength)
            {
                len = this.clientLength;
            }
            System.arraycopy(this.readAheadBuffer, this.clientOffset, b, off, len);
            this.clientLength -= len;
            this.clientOffset += len;
            this.readAheadBufferOffset += len;
            this.readAheadBufferLength -= len;
            return len;
        }
        
        // buffer is empty and payload is done
        if (this.substreamEnds)
        {
            return -1;
        }
        
        // invalid intern state
        if (this.readAheadBufferLength < 0)
        {
            throw new IOException("readAheadBufferLength < 0 : " + this.readAheadBufferLength);
        }
        
        // shift to position 0
        if (this.readAheadBufferLength > 0)
        {
            if (this.readAheadBufferOffset >= this.readAheadBufferLength)
            {
                System.arraycopy(this.readAheadBuffer, this.readAheadBufferOffset, this.readAheadBuffer, 0, this.readAheadBufferLength);
            }
            else if ((this.readAheadBufferLength < (this.readAheadBuffer.length / 2)) && ((this.readAheadBuffer.length - (this.readAheadBufferOffset + this.readAheadBufferLength)) >= this.readAheadBufferLength))
            {
                final int tempOffset = this.readAheadBuffer.length - this.readAheadBufferLength;
                System.arraycopy(this.readAheadBuffer, this.readAheadBufferOffset, this.readAheadBuffer, tempOffset, this.readAheadBufferLength);
                System.arraycopy(this.readAheadBuffer, tempOffset, this.readAheadBuffer, 0, this.readAheadBufferLength);
            }
            else if (this.readAheadBufferOffset > MIN_SITE_CLUSTERED_SHIFT)
            {
                int tempOffset = this.readAheadBufferOffset;
                int shiftClusterSize = this.readAheadBufferOffset;
                int shiftPending = this.readAheadBufferLength;
                while (shiftPending > 0)
                {
                    if (shiftClusterSize > shiftPending)
                    {
                        shiftClusterSize = shiftPending;
                    }
                    System.arraycopy(this.readAheadBuffer, tempOffset, this.readAheadBuffer, this.readAheadBufferLength - shiftPending, shiftClusterSize);
                    shiftPending -= shiftClusterSize;
                    tempOffset += shiftClusterSize;
                }
            }
            else
            {
                for (int i = 0; i < this.readAheadBufferLength; i++)
                {
                    this.readAheadBuffer[i] = this.readAheadBuffer[i + this.readAheadBufferOffset];
                }
            }
        }
        
        // current state: everthing is normalized
        
        this.readAheadBufferOffset = 0;
        this.clientOffset = 0;
        
        byte byteToTestForEndSequence;
        int currentEndSequencePositiveMatchingLength = 0;
        int currentEndSequencePositiveMatchingOffset = -1;
        int currentEndSequenceFindingPointer = 0;
        
        // read next bytes from parentStream into buffer readAheadBuffer
        final int readNext = BUFFER_SIZE - this.readAheadBufferLength;
        if (readNext > 0)
        {
            
            final int readed = this.parentInputStream.read(this.readAheadBuffer, this.readAheadBufferOffset + this.readAheadBufferLength, readNext);
            if (readed < 0)
            {
                this.parentInputStreamIsEnded = true;
                
                if (this.readAheadBufferLength == 0)
                {
                    return -1;
                }
            }
            else
            {
                this.readAheadBufferLength += readed;
            }
            
            // current state: something is readed; offset in buffer: 0
            // (readAheadBufferOffset && clientOffset)
            
            // test for endsequence
            for (; currentEndSequenceFindingPointer < this.readAheadBufferLength; currentEndSequenceFindingPointer++)
            {
                byteToTestForEndSequence = this.readAheadBuffer[currentEndSequenceFindingPointer];
                if (byteToTestForEndSequence != this.matchBeginBuffer[currentEndSequencePositiveMatchingLength])
                {
                    // first byte of endsequence must be unique in endsequence => potential
                    // endsequence start (everytime)
                    if (byteToTestForEndSequence == this.matchBeginBuffer[0])
                    {
                        currentEndSequencePositiveMatchingOffset = currentEndSequenceFindingPointer;
                        currentEndSequencePositiveMatchingLength = 1;
                        continue;
                    }
                    // payloadbyte of substream (everytime)
                    currentEndSequencePositiveMatchingLength = 0;
                    currentEndSequencePositiveMatchingOffset = -1;
                    continue;
                }
                if (currentEndSequencePositiveMatchingLength == 0)
                {
                    currentEndSequencePositiveMatchingOffset = currentEndSequenceFindingPointer;
                }
                currentEndSequencePositiveMatchingLength++;
                
                if (currentEndSequencePositiveMatchingLength == this.matchBeginBuffer.length)
                {
                    this.substreamEnds = true;
                    
                    // write carry out by copying the leftover of readed bytes
                    if ((currentEndSequenceFindingPointer + 1) < this.readAheadBufferLength)
                    {
                        int j = currentEndSequenceFindingPointer + 1;
                        this.carryout = new byte[this.readAheadBufferLength - j];
                        for (int x = 0; j < this.readAheadBufferLength; j++, x++)
                        {
                            this.carryout[x] = this.readAheadBuffer[j];
                        }
                    }
                    this.clientLength = currentEndSequencePositiveMatchingOffset - this.clientOffset;
                    
                    // return bytes in buffer II
                    if (len > this.clientLength)
                    {
                        len = this.clientLength;
                    }
                    if (len == 0)
                    {
                        // all readed byte belongs to endsequence
                        return -1;
                    }
                    
                    System.arraycopy(this.readAheadBuffer, this.clientOffset, b, off, len);
                    this.clientLength -= len;
                    this.clientOffset += len;
                    this.readAheadBufferOffset += len;
                    this.readAheadBufferLength -= len;
                    
                    return len;
                }
            }
        }
        
        // current state: still in substream or endsequence
        
        if (currentEndSequencePositiveMatchingLength == 0)
        {
            // not in endsequence => all bytes are payload (part of substream)
            this.clientLength = this.readAheadBufferLength - this.clientOffset;
            
            // return bytes in buffer III
            if (len > this.clientLength)
            {
                len = this.clientLength;
            }
            System.arraycopy(this.readAheadBuffer, this.clientOffset, b, off, len);
            this.clientLength -= len;
            this.clientOffset += len;
            this.readAheadBufferOffset += len;
            this.readAheadBufferLength -= len;
            
            return len;
        }
        
        // current state: in endsequence
        
        // read ahead able to test current endsequence until end
        final int missingToCompleteSeq = this.matchBeginBuffer.length - currentEndSequencePositiveMatchingLength;
        int readtodo = missingToCompleteSeq;
        while (readtodo > 0)
        {
            final int readed = this.parentInputStream.read(this.readAheadBuffer, this.readAheadBufferOffset + this.readAheadBufferLength, readtodo);
            if (readed < 0)
            {
                this.parentInputStreamIsEnded = true;
                break;
            }
            this.readAheadBufferLength += readed;
            readtodo -= readed;
        }
        
        // test again readed bytes (now complete to check endsequence)
        
        for (; currentEndSequenceFindingPointer < this.readAheadBufferLength; currentEndSequenceFindingPointer++)
        {
            byteToTestForEndSequence = this.readAheadBuffer[currentEndSequenceFindingPointer];
            
            if (byteToTestForEndSequence != this.matchBeginBuffer[currentEndSequencePositiveMatchingLength])
            {
                // current state : all bytes are payload (part of substream) => returns bytes
                // until potential endPartStartPosition
                
                this.clientLength = currentEndSequencePositiveMatchingOffset - this.clientOffset;
                // return bytes in buffer II
                if (len > this.clientLength)
                {
                    len = this.clientLength;
                }
                System.arraycopy(this.readAheadBuffer, this.clientOffset, b, off, len);
                this.clientLength -= len;
                this.clientOffset += len;
                this.readAheadBufferOffset += len;
                this.readAheadBufferLength -= len;
                return len;
            }
            currentEndSequencePositiveMatchingLength++;
            if (currentEndSequencePositiveMatchingLength == this.matchBeginBuffer.length)
            {
                // state: endsequence complete
                this.substreamEnds = true;
                if ((currentEndSequenceFindingPointer + 1) < this.readAheadBufferLength)
                {
                    throw new IOException("(i+1) < readAheadBufferLength");
                }
                
                this.clientLength = currentEndSequencePositiveMatchingOffset - this.clientOffset;
                
                // return bytes in buffer IV
                if (len > this.clientLength)
                {
                    len = this.clientLength;
                }
                System.arraycopy(this.readAheadBuffer, this.clientOffset, b, off, len);
                this.clientLength -= len;
                this.clientOffset += len;
                this.readAheadBufferOffset += len;
                this.readAheadBufferLength -= len;
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
    public long skip(final long n) throws IOException
    {
        throw new IOException("skip not supported");
    }
    
    /**
     * find first seperatormark and bookmark this match as only valid endmark, to
     * close this stream if finding these sequence again
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
        
        for (int i = 0; i < this.readAheadBufferLength; i++)
        {
            b = this.readAheadBuffer[i];
            if (matchBeginProgress == BEGIN_MATCH_PROGRESS_NONE)
            {
                if (b != BEGIN_PATTERN_1[0])
                {
                    continue;
                }
                matchBeginOffset = i;
                matchBeginLength = 1;
                matchBeginProgress = BEGIN_MATCH_PROGRESS_OFFSET_PART;
                continue;
            }
            if (matchBeginProgress == BEGIN_MATCH_PROGRESS_OFFSET_PART)
            {
                if (b != BEGIN_PATTERN_1[matchBeginLength])
                {
                    if (b == BEGIN_PATTERN_1[0])
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
                if (matchBeginLength == BEGIN_PATTERN_1.length)
                {
                    matchBeginProgress = BEGIN_MATCH_PROGRESS_VAR_PART;
                }
                continue;
            }
            if (matchBeginProgress == BEGIN_MATCH_PROGRESS_VAR_PART)
            {
                if (!((b == '_') ||
                      (b == '-') ||
                      (b == BEGIN_PATTERN_2[0]) ||
                      ((b >= '0') && (b <= '9')) ||
                      ((b >= 'a') && (b <= 'f'))))
                {
                    if (b == BEGIN_PATTERN_1[0])
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
                
                if (b == BEGIN_PATTERN_2[0])
                {
                    matchBeginProgress = BEGIN_MATCH_PROGRESS_END_PART;
                    matchBeginEndpartOffset = 1;
                }
                continue;
            }
            if (matchBeginProgress == BEGIN_MATCH_PROGRESS_END_PART)
            {
                if (b != BEGIN_PATTERN_2[matchBeginEndpartOffset])
                {
                    if (b == BEGIN_PATTERN_1[0])
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
                
                if (matchBeginEndpartOffset == BEGIN_PATTERN_2.length)
                {
                    matchBeginProgress = BEGIN_MATCH_PROGRESS_FINISHED;
                    
                    this.matchBeginBuffer = new byte[matchBeginLength];
                    System.arraycopy(this.readAheadBuffer, matchBeginOffset, this.matchBeginBuffer, 0, matchBeginLength);
                    
                    this.readAheadBufferOffset = matchBeginOffset + matchBeginLength;
                    this.readAheadBufferLength -= this.readAheadBufferOffset;
                    this.clientOffset = this.readAheadBufferOffset;
                    this.clientLength = 0;
                    
                    return;
                }
            }
        }
        
        if (this.parentInputStreamIsEnded)
        {
            return;
        }
        
        byte[] carryin = null;
        if (matchBeginLength > 0)
        {
            carryin = new byte[this.readAheadBufferLength - matchBeginOffset];
            System.arraycopy(this.readAheadBuffer, matchBeginOffset, carryin, 0, carryin.length);
        }
        
        int off = 0;
        if ((carryin != null) && (carryin.length > 0))
        {
            System.arraycopy(carryin, 0, this.readAheadBuffer, 0, carryin.length);
            off = carryin.length;
            this.readAheadBufferLength = carryin.length;
        }
        else
        {
            this.readAheadBufferLength = 0;
        }
        
        final int len;
        final int readToFindMark = MARK_SIZE - off;
        if (readToFindMark > 0)
        {
            if ((len = this.parentInputStream.read(this.readAheadBuffer, off, readToFindMark)) > 0)
            {
                off += len;
                this.readAheadBufferLength += len;
            }
            else
            {
                this.parentInputStreamIsEnded = true;
            }
        }
        findBeginMark();
    }
    
    /**
     * @return inadvertently too much readed bytes
     */
    public byte[] getCarryout()
    {
        return this.carryout;
    }
    
    /**
     * @return true, if parentstream provide no more data, otherwise false
     */
    public boolean isParentInputStreamEnded()
    {
        return this.parentInputStreamIsEnded;
    }
}
