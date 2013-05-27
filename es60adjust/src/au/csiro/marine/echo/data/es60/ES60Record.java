/*
    ES60Record.java  au.csiro.marine.echo.data.ES60Record

    Copyright 2002-2005, CSIRO Marine Research.
    All rights reserved.
    Released under the GPL and possibly other licenses.

    $Id: ES60Record.java 334 2010-08-16 01:09:50Z kei037 $

    $Log: ES60Record.java,v $
    Revision 1.8  2006/04/12 06:11:23  kei037
    Initial implementation of echogram display in DataView

    Revision 1.7  2005/05/31 05:52:01  kei037
    ES60Adjust working with code to find triangle wave in data.

    Revision 1.6  2005/05/12 06:43:00  kei037
    Working version of ES60Adjust program to remove triangular wave errors.

    Revision 1.5  2005/05/05 05:46:11  kei037
    Added ES60Adjust to remove ES60 triangular error.

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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;

import javax.swing.*;


/**
    ES60Record contains an Simrad ES60 or EK60 data record (telegram).

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

    @version $Id: ES60Record.java 334 2010-08-16 01:09:50Z kei037 $
    @author Gordon Keith
**/
public class ES60Record {

   /* ---------- Constants ---------- */
	
	public static final int HEADER_LENGTH = 12;

   /* ----- Telegram types ----- */

   /**
    *  Telegram holding an NMEA string.
    **/
   public static final int NME0 = 'N' << 24 | 'M' << 16 | 'E' << 8 | '0' << 0;

   /**
    *  Telegram holding a configuration datagram.
    **/
   public static final int CON0 = 'C' << 24 | 'O' << 16 | 'N' << 8 | '0' << 0;

   /**
    *  Telegram holding a comment tag.
    **/
   public static final int TAG0 = 'T' << 24 | 'A' << 16 | 'G' << 8 | '0' << 0;

   /**
    *  Telegram holding a raw data datagram.
    **/
   public static final int RAW0 = 'R' << 24 | 'A' << 16 | 'W' << 8 | '0' << 0;

   /**
    *  Telegram holding a depth datagram.
    **/
   public static final int DEP0 = 'D' << 24 | 'E' << 16 | 'P' << 8 | '0' << 0;

   /* ---------- Public Static Methods ---------- */

   /* ----- Utility Methods ----- */

   /**
    *  Byte swap an integer.
    *
    *  This method is used to swap byte order if the file is little endian.
    *  
    *  @param x Integer to byte swap
    *  @return byte swapped version of x.
    **/
   public static int swap(int x) {
      return 
	 (x >> 24 & (0xff <<  0)) |
	 (x >>  8 & (0xff <<  8)) |
	 (x <<  8 & (0xff << 16))|
	 (x << 24 & (0xff << 24));
   }

   /* ----- Factory Methods ----- */
 
   /**
    *  Read a telegram from <code>in</code> and store it in an ES60Record.
    *  Associate the record with the specified esFile.
    *
    *  @param in DataInput containing the telegram data.
    *  @param esFile The ES60File (data file) that this record belongs to.
    *  @return ES60Record containing the next telegram read from <code>in</code>.
    **/
   public static ES60Record read(DataInput in, ES60File esFile) 
      throws IOException {
      return read(in, esFile, false);
   } 

   /**
    *  Read a telegram from <code>in</code> and store it in an ES60Record.
    *  Associate the record with the specified esFile.
    *
    *  If nmeaOnly is true then the contents of telegrams other than NME0
    *  telegrams is skipped, not read. An ES60Record is still returned, 
    *  with a valid header, but the record contents are not held or parsed.
    *  This is to increase performance in cases where only the GPS data is 
    *  of interest.
    *
    *  @param in DataInput containing the telegram data.
    *  @param esFile The ES60File (data file) that this record belongs to.
    *  @param nmeaOnly Only read the contents of NME0 telegrams?
    *  @return ES60Record containing the next telegram read from <code>in</code>.
    **/
   public static ES60Record read(DataInput in, ES60File esFile, 
		   boolean nmeaOnly) 
   throws IOException {
	   boolean swap = esFile.swap();
	   
	   long filePointer = 0;
	   if (in instanceof RandomAccessFile)
		   try {
			   filePointer = ((RandomAccessFile)in).getFilePointer();
		   } catch (IOException ioe) {}
		   
	   int len = in.readInt();
	   
	   if (swap) 
		   len = swap(len);
	   
	   if (len < HEADER_LENGTH) 
		   throw new IOException("Record length is too short:" + len);
	   
	   ES60Header head = ES60Header.read(in, swap);
	   ES60Record retval;
	   switch (head.getType()) {
	   case CON0:
		   retval = ES60CON.read(in, len - HEADER_LENGTH, swap);
		   break;
		   
	   case NME0:
		   retval = ES60NMEA.read(in, len - HEADER_LENGTH, swap);
		   break;
		   
	   case RAW0:
		   if (nmeaOnly) { // read header only
			   retval = ES60RAW.read(in, ES60RAW.HEADER, swap);
			   in.skipBytes(len - HEADER_LENGTH - ES60RAW.HEADER);
		   } else 
			   retval = ES60RAW.read(in, len - HEADER_LENGTH, swap);
		   break;
		   
		   // if we don't yet support this telegram type then just read it in.
	   default:
		   retval = new ES60Record();
	   if (nmeaOnly)
		   in.skipBytes(len - HEADER_LENGTH);
	   else {
		   retval.data_ = new byte[len - HEADER_LENGTH];
		   in.readFully(retval.data_);
	   }
	   }
	   retval.header_ = head;
	   retval.esFile_ = esFile;
	   
	   //read record length at end and check it matches.
	   int len2 = in.readInt();
	   if (swap)
		   len2 = swap(len2);
	   if (len != len2)
		   throw new IOException(head.toCSVString(" ") + " Length mismatch " + len + " != " + len2);
	   
	   retval.filePointer_ = filePointer;
	   
	   return retval;
   }

   /* ---------- Constructor ---------- */
   
   protected ES60Record() {
	   
   }
   
   /* ---------- Protected Members ---------- */

   /**
    *  Record header.
    **/
   protected ES60Header header_;

   /**
    *  ES60File that this record belongs to (was read from).
    **/
   protected ES60File esFile_;

   /**
    *  Position in file that this record starts, if known
    */
   protected long filePointer_;
   
   /**
    *  Contents of the telegram as read from the file.
    *  This is the unparsed version of the telegram.
    **/
   protected byte[] data_;

   /**
    *  <code>next_</code> supports a linked list of ES60Records so that different records
    *  the same location can be collected.
    *  The list contains a series of ES60Records until a 5 second gap or an NMEA sentence type is repeated,
    *  the repeated sentence will then begin the next list.
    *  @see #link(ES60Record, boolean)
    **/
   protected ES60Record next_;

   /** <code>prev_</code> supports a doubly linked list of NMEA strings.
    *  @see #next_
    **/
   protected ES60Record prev_;

/**
 *  Is byte swapping requred to parse this record
 **/
protected boolean swap_;
   
   /* ---------- Public Methods ---------- */

   /**
    *  Get the timestamp for this telegram.
    *  @return Timestamp of the record.
    *  @see ES60Header#getTime()
    **/
   public Date getTime() {
      if (header_ == null)
	 return null;

      return header_.getTime();
   }

   /**
    *  Set the timestamp for this telegram (milliseconds precision).
    *  This method ignores the nanoseconds.
    *
    *  @param newTime New timestamp for this record.
    **/
   public void setTime(Date newTime) {
      if (header_ == null)
	 return;
      header_.setTime(newTime);
   }

   /**
    *  Return the parsed contents of the record in comma seperated format.
    *  @return String containing parsed contents of record.
    **/
   public String toCSVString() {
      return toCSVString(",", false);
   }

   /**
    *  Return the ES60File this record was read from
    *  @return Files this record was read from
    */
   public ES60File getFile() {
	   return esFile_;
   }
   
   /**
    *  Return the byte number in the file at which this record starts.
    *  @return file pointer for this record
    */
   public long getFilePointer() {
	   return filePointer_;
   }
   
   /**
    *  Return the parsed contents of the record in a formatted way.
    *
    *  Fields are output as follows:
    *  Survey name, File name, telegram timestamp, latitude, longitude, NMEA date, NMEA time, raw NMEA string.
    *
    *  latitude, longitude and date and time are only ouput if they 
    *  have been parsed from the NMEA string.
    *
    *  The raw NMEA string is only included if <code>nmea</code> is true.
    *
    *  Fields are delimited by the String <code>delim</code>.
    *  This will normally be either comma or tab, but may be any string.
    *
    *  @param delim Delimiter to use to seperate fields.
    *  @param nmea Include raw NMEA string?
    *  @return String containing formatted contents of the record.
    **/
   public String toCSVString(String delim, boolean nmea) {
	   SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	   dateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));

	   // Survey and file name
	   StringBuffer retval = new StringBuffer();
	   if (esFile_ != null) {
		   if (esFile_.getSurvey() != null) {
			   retval.append(esFile_.getSurvey());
			   retval.append(delim);
		   }
		   retval.append(esFile_);
		   retval.append(delim);
	   }

	   // telegram timestamp
	   retval.append(dateFormat.format(getTime()));
	   return retval.toString();
   }
   
   /**
    * Return a header matching the data in toCSVString()
    * @param delim
    * @param nmea
    * @return	String containing header matching toCSVString()
    */
   public String getCSVHeader(String delim, boolean nmea) {
	   StringBuffer retval = new StringBuffer();
	   if (esFile_ != null) {
		   if (esFile_.getSurvey() != null) {
			   retval.append("Cruise");
			   retval.append(delim);
		   }
		   retval.append("Filename");
		   retval.append(delim);
	   }

	   retval.append("PCDateTime");
	   return retval.toString();
   }

   /**
    *  Write this record in the same format as read() reads.
    *
    *  @param out DataOutput to write telegram data.
    *  @param swap Is byte swapping required?
    *  @throws IOException If writing the the DataOutput throws an IOException.
    **/
   public void write(DataOutput out, boolean swap) 
      throws IOException {
      int len = HEADER_LENGTH + data_.length;
      if (swap)
	 len = swap(len);
      out.writeInt(len);
      header_.write(out, swap);
      out.write(data_);
      out.writeInt(len);
   }

   /* ----- Methods to be overwritten in some subclasses ----- */

   /**
    *  Add this record to the linked list of ES60Records
    *  headed by head, if it belongs to the same set.
    *
    *  This record belongs to the head set if:
    *<ul>
    *<li>the time difference between this record and the head is less than 5 seconds</li>
    *</ul>
    *
    *  Note that this method is overwritten in ES60NMEA and ES60RAW where additional tests are applied.
    *
    *  @param head head record of list
    *  @param prepend put this on head of list, else tail.
    *  @return true if this record was accepted into the list.
    *  @see ES60NMEA#link(ES60Record, boolean)
    *  @see ES60RAW#link(ES60Record, boolean)
    **/  
   public boolean link(ES60Record head, boolean prepend) {
      if (head != null) {
	 long headtime = head.getTime().getTime() ;
	 long time = getTime().getTime();

	 if (time - headtime < 5000 &&
	     headtime - time < 5000) {
	    ES60Record next = head;
	    ES60Record last = head;
	    while (next != null) {
	       last = next;
	       next = next.next_;
	    }	       
	    if (next == null) {
	       if (prepend) {
		  next_ = head;
		  if (head.prev_ == null)
		     head.prev_ = this;
		  else 
		     System.err.println("Broken chain");
	       } else {
		  prev_ = last;
		  if (last.next_ == null)
		     last.next_ = this;
		  else
		     System.err.println("Broken chain");
	       }
	       return true;
	    }
	 }
      }
      return false;
   }

   /**
    *  Returns true if the record includes an NMEA date field.
    *  This method always returns false, but may be overridden.
    *  @return Whether the record has a date.
    **/      
   public boolean hasDate() {
      return false;
   }

   /**
    *  Returns true if the record includes a NMEA time field.
    *  This method always returns false, but may be overridden.
    *  @return Whether the record has a time.
    **/
   public boolean hasTime() {
      return false;
   }

   /**
    *  Returns true if the record includes latitude and longitude.
    *  This method always returns false, but may be overridden.
    *  @return Whether the record has a position
    **/
   public boolean hasPos() {
      return false;
   }      
 
   /**
    *  Return the latitude, 
    *  or NaN if the record  doesn't contain position information.
    *  This method always returns NaN, but may be overridden.
    *  @return Latitude in decimal degrees or NaN.
    **/
   public double getLatitude() {
      return Double.NaN;
   }

   /**
    *  Return the longitude, 
    *  or NaN if the record doesn't contain position information.
    *  This method always returns NaN, but may be overridden.
    *  @return Longitude in decimal degrees or NaN.
    **/
   public double getLongitude() {
      return Double.NaN;
   }

   /**
    *  Get the next record in the list. 
    *  Will return null if this is the last record.
    *  @return Next ES60Record in the list.
    *  @see #link(ES60Record, boolean)
    **/
   public ES60Record getNext() {
      return next_;
   }

   /**
    *  Get the previous record in the list. 
    *  Will return null if this is the first record.
    *  @return Previous ES60Record in the list.
    *  @see #link(ES60Record, boolean)
    **/
   public ES60Record getPrev() {
      return prev_;
   }

   /* ---------- Inner Classes ---------- */

   /**
 *  Decode a 2 byte int (referred to as a short in the simrad manuals)
 *  from data_ starting at byte i
 **/
protected int decodeShort(int i) {
	if (swap_)
		return (data_[i + 1] << 8) |
		(data_[i] & 0xff);
	else
		return (data_[i] << 8) |
		(data_[i + 1] & 0xff);
}

/**
 *  Encode a 2 byte int (referred to as a short in the simrad manuals)
 *  into data_ starting at byte i
 **/
protected void encodeShort(int i, int val) {
	if (swap_) {
		data_[i + 1] = (byte)((val >> 8) & 0xff);
		data_[i] = (byte)(val & 0xff);
	} else {
		data_[i] = (byte)((val >> 8) & 0xff);
		data_[i + 1] = (byte)(val & 0xff);
	}
}

/**
 *  Decode an 4 byte int (refered to as long in the simrad manuals)
 *  from data_ starting at byte i
 **/
protected int decodeLong(int i) {
	if (swap_)
		return ((data_[i + 3] & 0xff) << 24) |
		((data_[i + 2] & 0xff) << 16) |
		((data_[i + 1] & 0xff) <<  8) |
		((data_[i] & 0xff) <<  0);
	else
		return ((data_[i] & 0xff) << 24) |
		((data_[i + 1] & 0xff) << 16) |
		((data_[i + 2] & 0xff) <<  8) |
		((data_[i + 3] & 0xff) <<  0);
}

/**
 *  Decode a 4 byte float from data_ starting at byte i
 **/
protected float decodeFloat(int i) {
	int bits = decodeLong(i);
	return Float.intBitsToFloat(bits);
}

/**
 *  Decode a String
 */
protected String decodeString(int start, int len) {
	int end;
	for (end = 0; end < len; end++)
		if (data_[start + end] == 0)
			break;
	return new String(data_, start, end);
}

/**
      ES60Record.Display is a JComponent for displaying an ES60Record.
<p>
      The Display is a basic tool to allow a user to view the details of the 
      record. 

      @author Gordon Keith
    
   **/

   public static class Display extends JComponent {

      /* ---------- Display Protected Members ---------- */

      /**
       *  Format for latitude and longitude
       **/
      protected NumberFormat latFormat_;

      /**
       *  Record currently displayed.
       **/
      protected ES60Record current_;

     /* ----- Widgets ----- */

      protected JTextField surveyField_;

      protected JTextField esFileField_;

      protected JTextField timeField_;

      protected JTextField datagramField_;

      protected JTextField nmeaField_;

      protected JTextField sentenceField_;

      protected JTextField latField_;

      protected JTextField lonField_;

      protected JTextField nmeaTimeField_;

      protected JButton nextButton_;

      protected JButton prevButton_;

      /* ---------- Display Constructors ---------- */

      /**
       *  Create the display.
       **/
      public Display() {

	 latFormat_ = NumberFormat.getInstance();
	 latFormat_.setMaximumFractionDigits(6);
	 
	 /* ----- Widgets ----- */
	 
	 surveyField_ = new JTextField(" ");
	 surveyField_.setEditable(false);
	 surveyField_.setToolTipText("Survey");
	 
	 esFileField_ = new JTextField(" ");
	 esFileField_.setEditable(false);
	 esFileField_.setToolTipText("File");

	 timeField_ = new JTextField(" ");
	 timeField_.setEditable(false);
	 timeField_.setToolTipText("Time");

	 datagramField_ =  new JTextField(" ");
	 datagramField_.setEditable(false);
	 datagramField_.setToolTipText("Datagram type");
	 
	 nmeaField_ = new JTextField(" ");
	 nmeaField_.setEditable(true);
	 nmeaField_.setToolTipText("Raw NMEA string from the GPS");
	 nmeaField_.setPreferredSize(nmeaField_.getPreferredSize());
	 
	 sentenceField_ = new JTextField(" ");
	 sentenceField_.setEditable(false);
	 sentenceField_.setToolTipText("NMEA sentence");
	 
	 latField_ = new JTextField(10);
	 latField_.setEditable(false);
	 latField_.setToolTipText("Latitude");
      
	 lonField_ = new JTextField(10);
	 lonField_.setEditable(false);
	 lonField_.setToolTipText("Longitude");
	 
	 nmeaTimeField_ = new JTextField(" ");
	 nmeaTimeField_.setEditable(false);
	 nmeaTimeField_.setToolTipText("NMEA time");

	 nextButton_ = new JButton("Next");
	 nextButton_.setEnabled(false);
	 nextButton_.setMnemonic('N');
	 nextButton_.setToolTipText("Next record for this location");
	 nextButton_.addActionListener(new ActionListener() {
	       public void actionPerformed(ActionEvent e) {
		  display(current_.next_);
	       }
	    });

	 prevButton_ = new JButton("Prev");
	 prevButton_.setEnabled(false);
	 prevButton_.setMnemonic('P');
	 prevButton_.setToolTipText("Previous record for this location");
	 prevButton_.addActionListener(new ActionListener() {
	       public void actionPerformed(ActionEvent e) {
		  display(current_.prev_);
	       }
	    });

	 // Layout

	 JPanel dataLabelPane = new JPanel(new GridLayout(0,1));
	 JPanel dataValuePane = new JPanel(new GridLayout(0,1));

	 dataLabelPane.add(new JLabel("Survey: "));
	 dataValuePane.add(surveyField_);
	 
	 dataLabelPane.add(new JLabel("File: "));
	 dataValuePane.add(esFileField_);
	 
	 dataLabelPane.add(new JLabel("Time: "));
	 dataValuePane.add(timeField_);
	 
	 dataLabelPane.add(new JLabel("Datagram: "));
	 dataValuePane.add(datagramField_);

	 dataLabelPane.add(new JLabel("NMEA: "));
	 dataValuePane.add(nmeaField_);
	 
	 dataLabelPane.add(new JLabel("Latitude: "));
	 dataValuePane.add(latField_);
	 
	 dataLabelPane.add(new JLabel("Longitude: "));
	 dataValuePane.add(lonField_);
	 
	 dataLabelPane.add(new JLabel("GPS Time: "));
	 dataValuePane.add(nmeaTimeField_);

	 JPanel buttonPane = new JPanel(new GridLayout(1,0));
	 buttonPane.add(prevButton_);
	 buttonPane.add(nextButton_);

	 dataLabelPane.add(new JLabel(" "));
	 dataValuePane.add(buttonPane);

	 setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
	 add(dataLabelPane);
	 add(dataValuePane);
      }

      /**
       *  Display the specified record.
       *  Fields are set to blank if the record is null.
       *  @param record ES60Record to display.
       **/
      public void display(ES60Record record) {
	 current_ = record;

	 datagramField_.setText(" ");
	 nmeaField_.setText(" ");
	 latField_.setText(" ");
	 lonField_.setText(" ");
	 nmeaTimeField_.setText(" ");
	 sentenceField_.setText(" ");	    

	 if (record != null) {
	    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    dateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));

	    if (record.esFile_ == null) {
	       surveyField_.setText(" ");
	       esFileField_.setText(" ");
	    } else {
	       surveyField_.setText(record.esFile_.getSurvey());
	       esFileField_.setText(record.esFile_.toString());
	    }

	    timeField_.setText(dateFormat.format(record.getTime()));

	    if (record.header_ != null)
	       datagramField_.setText(record.header_.getTypeString());

	    //# Should really includ ES60NMEA.display
	    if (record instanceof ES60NMEA) {
	       ES60NMEA nmea = (ES60NMEA)record;
	       nmeaField_.setText(nmea.toString());
	       sentenceField_.setText(nmea.getSentence() + "");

	       if (nmea.hasPos()) {
		  latField_.setText(latFormat_.format(nmea.getLatitude()));
		  lonField_.setText(latFormat_.format(nmea.getLongitude()));
	       }
	       
	       if (nmea.hasDate()) 
		  nmeaTimeField_.setText(dateFormat.format(nmea.getNmeaTime()));
	       else if (nmea.hasTime()) {
		  SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
		  timeFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));
		  nmeaTimeField_.setText(timeFormat.format(nmea.getNmeaTime()));
	       }
	    }

	    nextButton_.setEnabled(record.next_ != null);
	    prevButton_.setEnabled(record.prev_ != null);

	 } else {  // record == null
	    surveyField_.setText(" ");
	    esFileField_.setText(" ");
	    timeField_.setText(" ");
	    nextButton_.setEnabled(false);
	    prevButton_.setEnabled(false);
	 }
      }
   }
}
	 
/*
    Now, there are many other things that Jesus did.  
    Ifthey were all written down one by one,
    I suppose that the whole world could not hold the books
    that would be written.
            John 21:25
*/
     

