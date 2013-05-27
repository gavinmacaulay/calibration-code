/*
    ES60Header.java  au.csiro.marine.echo.data.ES60Header

    Copyright 2002-2005, CSIRO Marine Research.
    All rights reserved.
    Released under the GPL and possibly other licenses.

    $Id: ES60Header.java 400 2011-08-18 22:54:27Z  $

    $Log: ES60Header.java,v $
    Revision 1.6  2005/05/31 05:52:01  kei037
    ES60Adjust working with code to find triangle wave in data.

    Revision 1.5  2005/05/12 06:43:00  kei037
    Working version of ES60Adjust program to remove triangular wave errors.

    Revision 1.4  2004/01/06 22:59:38  kei037
    Implemented write() and setTime() to support ES60DateFix

    Revision 1.3  2003/01/23 00:04:51  kei037
    Refactored ES60Record to contain more of the stuff that was just in ES60NMEA.

    Revision 1.2  2003/01/16 01:09:45  kei037
    Cleaned up and document ES60 processing
    Moved ES60Survey to ES60File.

    Revision 1.1  2002/08/02 07:49:59  kei037
    Added suppport for viewing ES60 GPS data

*/

package au.csiro.marine.echo.data.es60;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
    ES60Header contains the header of an ES60 data record.

    The ES60 datagram format consists of (C notation from the EK60 manual):
<pre>
    long Length;
    struct DatagramHeader
    {
        long DatagramType;
	struct {
	     long LowDateTime;
	     long HighDateTime;
	} DateTime;
    };
    < datagram content>
    long Length;
</pre>
    Length is the same value at the start and end of datagram. 
    Byte order (big endian or little endian) is determined by the 
    system writing the data. The Length tag can be used to detect order,
    if the length isn't repeated after length bytes the byte order is wrong.

    The DatagramType is a an ASCII quadruple where the first three
    bytes identify datagram type and the fourth byte is version.

    The DateTime is an 64 bit (8 byte) integer giving the number of
    100-nanosecond intervals since 1601-01-01.

    The above is the specification in the manual. 
    The actual record structure does not seem to match this.
    The DatagramType is stored in bigendian format regardless of platform, 
    ie 'NME0' is the byte string for NME0 records on little endian computers -
    the manual says this should be swapped.
    The manual specifies the date is stored as 2 x 32 bit integers, low byte first.
    This is the same as a 64 bit integer on a little endian computer.
    I'm not confident that this format (2 x 32 bit) is actually used on big endian system.
    

   @version $Id: ES60Header.java 400 2011-08-18 22:54:27Z  $
   @author Gordon Keith
**/
public class ES60Header {

   /* ---------- Static ---------- */

   /* ---------- Constants ---------- */

   /** 
    *  DATE_ORIGIN is the java time of the ES60 time origin 
    *  1/1/1601 (prior to which is legally time immemorial IIRC)
    **/ 
   public final static long DATE_ORIGIN;

   static {
      GregorianCalendar cal = new GregorianCalendar(new SimpleTimeZone(0,"GMT"));
      cal.clear();
      cal.set(1601,0,1,0,0,0);
      DATE_ORIGIN = cal.getTime().getTime();
   }

   /* ---------- Factory Methods ---------- */

   /** 
    *  Read a header from a DataInput.
    *
    *  @param in DataInput to read the header from.
    *  @param swap Is byte swapping required for this DataInput?
    *  @return The ES60Header derived from the next 12 bytes on <code>in</code>.
    **/
   public static ES60Header read(DataInput in, boolean swap) 
      throws IOException {
      int type = in.readInt();
      int datel = in.readInt();
      int dateh = in.readInt();
      if (swap) {
	 // type = ES60Record.swap(type); // required by manual but not by data.
	 datel = ES60Record.swap(datel);
	 dateh = ES60Record.swap(dateh);
      }
      long date = ((((long)dateh) & 0xffffffffL) << 32) |
	 (((long)datel) & 0xffffffffL);

      ES60Header head = new ES60Header(type, date);
      return head;
   }

   /* ---------- Protected Members ---------- */

   /**
    *  The first four bytes of the header, as a byte[].
    *  @see #typei_
    **/
   protected byte[] type_;

   /**
    *  The first four bytes of the header, as an int.
    *  @see  #type_
    **/
   protected int typei_;

   /**
    *  The telegram timestamp down to milliseconds.
    **/
   protected Date timestamp_;

   /**
    *  The remainder of the timestamp (fraction of a millisecond)
    *  in 100s of nanoseconds. (OK, so the name is wrong).
    **/
   protected long nanos_;

   /* ---------- Constructors ---------- */

   protected ES60Header(int type, long time) {
      type_ = new byte[4];
      setType(type);
      setTime(time);
   }

   /* ---------- Protected Methods ---------- */

   /**
    *  Set the telegram type for this record.
    *  Both typei_ and type_ are updated.
    *  @param type Telegram type for this record.
    **/
   protected void setType(int type) {
      typei_ = type;
      type_[0] = (byte)((type & (0xff << 24)) >> 24);
      type_[1] = (byte)((type & (0xff << 16)) >> 16);
      type_[2] = (byte)((type & (0xff <<  8)) >>  8);
      type_[3] = (byte)((type & (0xff <<  0)) >>  0);
   }

   /**
    *  Set the record timestamp from the telegram timestamp.
    *  @param time Timestamp in ES60 format.
    **/
   protected void setTime(long time) {
      nanos_ = time % 10000L;
      time /= 10000;
      time += DATE_ORIGIN;
      timestamp_ = new Date(time);
   }

   /* ---------- Public Methods ---------- */

   /**
    *  String representation of this header.
    *  Uses toCSVString(",").
    *  @return String representatiom of the header.
    *  @see #toCSVString(String)
    **/
   public String toString() {
      return toCSVString(",");
   }

   /**
    *  "Comma separate Variable" representation of this header.
    *  Although the delimited can be specified, it need not be comma.
    *  Returns string in format: yyyy-MM-dd HH:mm:ss,TYPE
    *
    *  Note timestamp is given only to second precision even though it is held
    *  in 100s of nanoseconds.
    *  
    *  @param delim Field delimited, usually either comma or tab.
    *  @return CSV representation of this header.
    **/
   public String toCSVString(String delim) {
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
      format.setTimeZone(new SimpleTimeZone(0, "GMT"));

      StringBuffer retval = new StringBuffer(format.format(timestamp_));
      retval.append(delim);
      retval.append(new String(type_));
      return retval.toString();
   }

   /**
    *  @return The telegram type for this header.
    **/
   public int getType() {
      return typei_;
   }

   /**
    *  @return The telegram type for this header as a string.
    **/
   public String getTypeString() {
      return new String(type_);
   }

   /**
    *  Timestamp of record as a java Date (millisecond precision).
    *
    *  To get the fractions of milliseconds, in nanoseconds,
    *  use (getESTime() % 10000)* 100
    *
    *  @return Timestamp of the record (telegram).
    *  @see #getESTime()
    **/
   public Date getTime() {
      return timestamp_;
   }

   /**
    *  Set the timestamp of the record (millisecond precision).
    *
    *  @param newTime New timestamp for this record.
    **/
   public void setTime(Date newTime) {
      timestamp_ = newTime;
   }

   /**
    *  Get the telegram timestamp in ES60 format from the record timestamp.
    *  @return Record timestamp in ES60 format.
    **/
   public long getESTime() {
      return (timestamp_.getTime() - DATE_ORIGIN) * 10000 + nanos_;
   }

   /**
    *  Write this header to the DataOutput.
    *
    *  @param out Where to write the record.
    *  @param swap Swap bytes when writing (write in little endian)?
    *  @throws IOException "Unimplemented method."
    **/
   public void write(DataOutput out, boolean swap) 
      throws IOException {
      int type = typei_;
      long time = timestamp_.getTime();
      time -= DATE_ORIGIN;
      time *= 10000;
      time += nanos_;
      int timel = (int)(time & 0xffffffffL);
      int timeh = (int)(time >> 32 & 0xffffffffL);
      if (swap) {
	 // type = ES60Record.swap(type); // required by manual but not by data.
	 timel = ES60Record.swap(timel);
	 timeh = ES60Record.swap(timeh);
      }
      out.writeInt(type);
      out.writeInt(timel);
      out.writeInt(timeh);
   }

}

/*
    "I am not seeking honour for myself.
    But there is one who is seeking it and
    who judges in my favour.
    I am telling you the truth:
    whoever obeys my teaching will never die."
            John 8:50-51
*/
