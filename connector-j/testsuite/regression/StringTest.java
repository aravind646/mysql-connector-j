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
 
package testsuite.regression;

import testsuite.BaseTestCase;

/**
 * Tests for regressions of bugs in String handling
 * in the driver.
 */
public class StringTest extends BaseTestCase {

	public StringTest(String name)
	{
		super(name);
	}
	
	public static void main(String[] args) 
	{
		new StringTest("testAsciiCharConversion").run();
	}
	
	public void testAsciiCharConversion() throws Exception
	{
		byte[] buf = new byte[10];
		buf[0] = (byte)'?';
		buf[1] = (byte)'S';
		buf[2] = (byte)'t';
		buf[3] = (byte)'a';
		buf[4] = (byte)'t';
		buf[5] = (byte)'e';
		buf[6] = (byte)'-';
		buf[7] = (byte)'b';
		buf[8] = (byte)'o';
		buf[9] = (byte)'t';
		
		String testString = "?State-bot";
		
		String convertedString = 
			com.mysql.jdbc.StringUtils.toAsciiString(buf);
		
		for (int i = 0; i < convertedString.length(); i++)
		{	
			System.out.println((byte)convertedString.charAt(i));
		}
		
		assertTrue("Converted string != test string",
			testString.equals(convertedString));
	}
}