/*
   Copyright (C) 2002 MySQL AB
   
      This program is free software; you can redistribute it and/or modify
      it under the terms of the GNU General Public License as published by
      the Free Software Foundation; either version 2 of the License, or
      (at your option) any later version.
   
      This program is distributed in the hope that it will be useful,
      but WITHOUT ANY WARRANTY; without even the implied warranty of
      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
      GNU General Public License for more details.
   
      You should have received a copy of the GNU General Public License
      along with this program; if not, write to the Free Software
      Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
      
 */
package com.mysql.jdbc;

import java.io.UnsupportedEncodingException;

import java.sql.SQLException;


/**
 * Buffer contains code to read and write packets from/to the MySQL server.
 * 
 * @version $Id$
 * @author Mark Matthews
 */
class Buffer
{

    //~ Instance/static variables .............................................

    final static int 	NO_LENGTH_LIMIT 	= -1;
    static long 		NULL_LENGTH 		= -1;
    private int 		bufLength 			= 0;
    private byte[] 	byteBuffer;
    private int 		position 			= 0;
    private int 		sendLength 			= 0;
    private int 		maxLength 			= NO_LENGTH_LIMIT;

    //~ Constructors ..........................................................

    Buffer(byte[] buf)
    {
        this.byteBuffer = buf;
        setBufLength(buf.length);
    }

    Buffer(int size, int max_packet_size)
    {
        this.byteBuffer = new byte[size];
        setBufLength(this.byteBuffer.length);
        this.position = MysqlIO.HEADER_LENGTH;
        setMaxLength(max_packet_size);
    }

    Buffer(int size)
    {
        this(size, NO_LENGTH_LIMIT);
    }

    //~ Methods ...............................................................

    /**
     * Sets the array of bytes to use as a buffer to read from.
     * 
     * @param byteBuffer the array of bytes to use as a buffer
     */
    public void setByteBuffer(byte[] byteBuffer)
    {
        this.byteBuffer = byteBuffer;
    }

    /**
     * Returns the array of bytes this Buffer is using to read from.
     * 
     * @return byte array being read from
     */
    public byte[] getByteBuffer()
    {

        return this.byteBuffer;
    }

    /**
     * DOCUMENT ME!
     * 
     * @param position DOCUMENT ME!
     */
    public void setPosition(int position)
    {
        this.position = position;
    }

    /**
     * DOCUMENT ME!
     * 
     * @return DOCUMENT ME! 
     */
    public int getPosition()
    {

        return this.position;
    }

    final void setBytes(byte[] buf)
    {
        setSendLength(getBufLength());
        System.arraycopy(buf, 0, this.byteBuffer, 0, getBufLength());
    }

    //
    // Read a given-length array of bytes
    //
    final byte[] getBytes(int len)
    {

        byte[] b = new byte[len];
        System.arraycopy(this.byteBuffer, this.position, b, 0, len);
        this.position += len; // update cursor

        return b;
    }

    // 2000-06-05 Changed
    final boolean isLastDataPacket()
    {

        return ((getBufLength() <= 2) && 
               ((this.byteBuffer[0] & 0xff) == 254));
    }

    //
    // Read a null-terminated array of bytes
    //
    final byte[] getNullTerminatedBytes()
    {

        int i = this.position;
        int len = 0;

        while (this.byteBuffer[i] != 0 && i < getBufLength())
        {
            len++;
            i++;
        }

        byte[] b = new byte[len];
        System.arraycopy(this.byteBuffer, this.position, b, 0, len);
        this.position += (len + 1); // update cursor

        return b;
    }

    final void clear()
    {
        this.position = MysqlIO.HEADER_LENGTH;
    }

    final void dump()
    {

        int p = 0;
        int rows = getBufLength() / 8;

        for (int i = 0; i < rows; i++)
        {

            int ptemp = p;

            for (int j = 0; j < 8; j++)
            {

                String hexVal = Integer.toHexString(
                                        (int)this.byteBuffer[ptemp]);

                if (hexVal.length() == 1)
                {
                    hexVal = "0" + hexVal;
                }

                System.out.print(hexVal + " ");
                ptemp++;
            }

            System.out.print("    ");

            for (int j = 0; j < 8; j++)
            {

                if (this.byteBuffer[p] > 32 && this.byteBuffer[p] < 127)
                {
                    System.out.print((char)this.byteBuffer[p] + " ");
                }
                else
                {
                    System.out.print(". ");
                }

                p++;
            }

            System.out.println();
        }

        int n = 0;

        for (int i = p; i < getBufLength(); i++)
        {

            String hexVal = Integer.toHexString((int)this.byteBuffer[i]);

            if (hexVal.length() == 1)
            {
                hexVal = "0" + hexVal;
            }

            System.out.print(hexVal + " ");
            n++;
        }

        for (int i = n; i < 8; i++)
        {
            System.out.print("   ");
        }

        System.out.print("    ");

        for (int i = p; i < getBufLength(); i++)
        {

            if (this.byteBuffer[i] > 32 && this.byteBuffer[i] < 127)
            {
                System.out.print((char)this.byteBuffer[i] + " ");
            }
            else
            {
                System.out.print(". ");
            }
        }

        System.out.println();
    }

    final void ensureCapacity(int additional_data)
                       throws SQLException
    {

        if ((this.position + additional_data) > getBufLength())
        {

            int newLength = (int)(getBufLength() * 1.25);

            if (newLength < (getBufLength() + additional_data))
            {
                newLength = getBufLength() + (int)(additional_data * 1.25);
            }

            if (getMaxLength() != NO_LENGTH_LIMIT && 
                (newLength > getMaxLength()))
            {
                throw new PacketTooBigException(newLength, getMaxLength());
            }

            byte[] newBytes = new byte[newLength];
            System.arraycopy(this.byteBuffer, 0, newBytes, 0, 
                             this.byteBuffer.length);
            this.byteBuffer = newBytes;
            setBufLength(this.byteBuffer.length);
        }
    }

    // For MySQL servers > 3.22.5
    final long newReadLength()
    {

        int sw = this.byteBuffer[this.position++] & 0xff;

        switch (sw)
        {

            case 251:
                return (long)0;

            case 252:
                return (long)readInt();

            case 253:
                return (long)readLongInt();

            case 254: // changed for 64 bit lengths
                return (long)readLongLong();

            default:
                return (long)sw;
        }
    }

    final byte readByte()
    {

        return this.byteBuffer[this.position++];
    }

    // Read null-terminated string (native)
    final byte[] readByteArray()
    {

        return getNullTerminatedBytes();
    }

    final long readFieldLength()
    {

        int sw = this.byteBuffer[this.position++] & 0xff;

        switch (sw)
        {

            case 251:
                return NULL_LENGTH;

            case 252:
                return (long)readInt();

            case 253:
                return (long)readLongInt();

            case 254:
                return (long)readLong();

            default:
                return (long)sw;
        }
    }

    // 2000-06-05 Changed
    final int readInt()
    {

        byte[] b = this.byteBuffer; // a little bit optimization

        return (b[this.position++] & 0xff) | 
               ((b[this.position++] & 0xff) << 8);
    }

    // Read given-length string (native)
    final byte[] readLenByteArray()
    {

        long len = this.readFieldLength();

        if (len == NULL_LENGTH)
        {

            return null;
        }

        if (len == 0)
        {

            return new byte[0];
        }

        return getBytes((int)len);
    }

    //
    // Read given-length string
    //
    // To avoid alloc'ing a byte array that will
    // quickly be thrown away, we do this by
    // hand instead of calling getBytes()
    //
    final String readLenString()
    {

        long len = this.readFieldLength();

        if (len == NULL_LENGTH)
        {

            return null;
        }

        if (len == 0)
        {

            return "";
        }

        String S = new String(this.byteBuffer, this.position, (int)len);
        this.position += len; // update cursor

        return S;
    }

    final long readLength()
    {

        int sw = this.byteBuffer[this.position++] & 0xff;

        switch (sw)
        {

            case 251:
                return (long)0;

            case 252:
                return (long)readInt();

            case 253:
                return (long)readLongInt();

            case 254:
                return (long)readLong();

            default:
                return (long)sw;
        }
    }

    // 2000-06-05 Fixed
    final long readLong()
    {

        byte[] b = this.byteBuffer;

        return (b[this.position++] & 0xff) | 
               ((b[this.position++] & 0xff) << 8) | 
               ((b[this.position++] & 0xff) << 16) | 
               ((b[this.position++] & 0xff) << 24);
    }

    // 2000-06-05 Changed
    final int readLongInt()
    {

        byte[] b = this.byteBuffer;

        return (b[this.position++] & 0xff) | 
               ((b[this.position++] & 0xff) << 8) | 
               ((b[this.position++] & 0xff) << 16);
    }

    // 2000-06-05 Fixed
    final long readLongLong()
    {

        byte[] b = this.byteBuffer;

        return (long)(b[this.position++] & 0xff) | 
               ((long)(b[this.position++] & 0xff) << 8) | 
               ((long)(b[this.position++] & 0xff) << 16) | 
               ((long)(b[this.position++] & 0xff) << 24) | 
               ((long)(b[this.position++] & 0xff) << 32) | 
               ((long)(b[this.position++] & 0xff) << 40) | 
               ((long)(b[this.position++] & 0xff) << 48) | 
               ((long)(b[this.position++] & 0xff) << 56);
    }

    //
    // Read a null-terminated string
    //
    // To avoid alloc'ing a new byte array, we
    // do this by hand, rather than calling getNullTerminatedBytes()
    //
    final String readString()
    {

        int i = this.position;
        int len = 0;

        while (this.byteBuffer[i] != 0 && i < getBufLength())
        {
            len++;
            i++;
        }

        String s = new String(this.byteBuffer, this.position, len);
        this.position += (len + 1); // update cursor

        return s;
    }

    //
    // Read a null-terminated string, but don't actually do anything with it
    // (avoiding allocation, but needed for protocol support
    //
    final void readStringNoop()
    {

        int i = this.position;
        int len = 0;

        while (this.byteBuffer[i] != 0 && i < getBufLength())
        {
            len++;
            i++;
        }

        this.position += (len + 1); // update cursor
    }

    // Read n bytes depending
    final int readnBytes()
    {

        int sw = this.byteBuffer[this.position++] & 0xff;

        switch (sw)
        {

            case 1:
                return this.byteBuffer[this.position++] & 0xff;

            case 2:
                return this.readInt();

            case 3:
                return this.readLongInt();

            case 4:
                return (int)this.readLong();

            default:
                return 255;
        }
    }

    final void writeByte(byte b)
    {
        this.byteBuffer[this.position++] = b;
    }

    // Write a byte array
    final void writeBytesNoNull(byte[] Bytes)
                         throws SQLException
    {

        int len = Bytes.length;
        ensureCapacity(len);
        System.arraycopy(Bytes, 0, this.byteBuffer, this.position, len);
        this.position += len;
    }

    // 2000-06-05 Changed
    final void writeInt(int i)
    {

        byte[] b = this.byteBuffer;
        b[this.position++] = (byte)(i & 0xff);
        b[this.position++] = (byte)(i >>> 8);
    }

    // 2000-06-05 Changed
    final void writeLong(long i)
    {

        byte[] b = this.byteBuffer;
        b[this.position++] = (byte)(i & 0xff);
        b[this.position++] = (byte)(i >>> 8);
        b[this.position++] = (byte)(i >>> 16);
        b[this.position++] = (byte)(i >>> 24);
    }

    // 2000-06-05 Changed
    final void writeLongInt(int i)
    {

        byte[] b = this.byteBuffer;
        b[this.position++] = (byte)(i & 0xff);
        b[this.position++] = (byte)(i >>> 8);
        b[this.position++] = (byte)(i >>> 16);
    }

    // Write null-terminated string
    final void writeString(String s)
                    throws SQLException
    {
        writeStringNoNull(s);
        this.byteBuffer[this.position++] = 0;
    }

    // Write string, with no termination
    final void writeStringNoNull(String s)
                          throws SQLException
    {

        int len = s.length();
        ensureCapacity(len);

        for (int i = 0; i < len; i++)
        {
            this.byteBuffer[this.position++] = (byte)s.charAt(i);
        }
    }

    // Write a String using the specified character
    // encoding
    final void writeStringNoNull(String s, String encoding)
                          throws UnsupportedEncodingException, SQLException
    {

        byte[] b = s.getBytes(encoding);
        int len = b.length;
        ensureCapacity(len);
        System.arraycopy(b, 0, this.byteBuffer, this.position, len);
        this.position += len;
    }

    void setBufLength(int bufLength)
    {
        this.bufLength = bufLength;
    }

    int getBufLength()
    {

        return bufLength;
    }

    void setMaxLength(int maxLength)
    {
        this.maxLength = maxLength;
    }

    int getMaxLength()
    {

        return maxLength;
    }

    void setSendLength(int sendLength)
    {
        this.sendLength = sendLength;
    }

    int getSendLength()
    {

        return this.sendLength;
    }
}