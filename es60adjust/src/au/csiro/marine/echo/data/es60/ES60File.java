/*
    ES60File.java  au.csiro.marine.echo.data.ES60File

    Copyright 2002-2005, CSIRO Marine Research.
    All rights reserved.
    Released under the GPL and possibly other licenses.

    $Id: ES60File.java 401 2011-08-19 06:05:37Z  $

    $Log: ES60File.java,v $
    Revision 1.7  2006/04/12 06:11:23  kei037
    Initial implementation of echogram display in DataView

    Revision 1.6  2006/02/28 00:07:39  kei037
    Cosmetic changes associated with move to eclipse.

    Revision 1.5  2005/05/12 06:43:00  kei037
    Working version of ES60Adjust program to remove triangular wave errors.

    Revision 1.4  2004/01/06 22:57:00  kei037
    Use a BufferedInputStream to improve performance (halved time)

    Revision 1.3  2003/07/09 00:47:01  kei037
    Added readPoint.

    Revision 1.2  2003/04/09 23:50:33  kei037
    Fixed bug in swap detection.

    Revision 1.1  2003/01/16 01:09:45  kei037
    Cleaned up and document ES60 processing
    Moved ES60Survey to ES60File.

    Revision 1.2  2002/09/02 04:14:40  kei037
    Improvements to ES60 data handling.

    Revision 1.1  2002/08/02 07:49:59  kei037
    Added suppport for viewing ES60 GPS data


*/

package au.csiro.marine.echo.data.es60;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.util.logging.Logger;

import au.csiro.marine.util.TrackPoint;

/**
    ES60File is a class representing the data held in an ES60 .raw or .out file.
    
    20060404 converting to use RandomAccessFile to allow viewing of echograms.

    @version $Id: ES60File.java 401 2011-08-19 06:05:37Z  $
    @author Gordon Keith
**/
public class ES60File 
   implements Comparable<ES60File> {

   /* ---------- Protected Members ---------- */
   /**
    *  The actual file.
    **/
   protected File file_;

   /**
    *  Is byte swapping required (is little endian)?
    **/
   protected boolean swap_;
   
   /**
    *  Survey name, either a user specified string or 
    *  the name of the directory containing the file.
    **/
   protected String survey_;

   /**
    *  DataInputStream used to read file_
    **/
   protected DataInputStream stream_;

   /**
    *  RandomAccessFile used to read file_
    */
   protected RandomAccessFile raFile_;
   
   /**
    *  DataInput used to read file_
    */
   protected DataInput in_;
   
   /**
    *  Configuration record for this file.
    *  A CON0 records should always appear as the first record of a ES60RAW file
    *  and should only occur once.
    */
   protected ES60CON config_;
   
   /**
    *  NMEA record last read from file.
    *  This buffers one ES60NMEA record between calls to readPoint().
    **/
   protected ES60NMEA lastNMEA_;
   
   /**
    *  Array of Vectors of Integers containing filePointers for records.
    *  When complete each Vector provides an index to all the RAW records for a channel.
    *  channel 0 will contain NMEA data if readPoint was used to scan the file.
    *  
    *  The index will only be correctly populated if the entire file is read sequentially once.
    *  The suggested method is the use a loop containing readPoint.
    *  
    *  Once EndOfFile is reached indices_ will be transferred to index_.
    */
   protected Vector<Long>[] indices_;
   
   /**
    *  Array of indices to the file.
    *  Each value is the file pointer of a record for that channel.
    */
   protected long[][] index_;
   
   protected Date start_;
   
   protected Date end_;
   
   
   /* ---------- Constructors ---------- */

   /**
    *  Create an ES60File for the given File.
    *
    *  The directory name of the rawFile will be used as the survey name.
    *
    *  Note: the survey name in the configuration datagram is ignored.
    *
    *  @param rawFile A .raw or .out file containing ES60 datagrams.
    **/
   public ES60File(File rawFile) {
      this(rawFile, null);
   }

   /**
    *  Create an ES60File for the given File, with the specified survey name.
    *
    *  If the survey name is null then the directory name of rawFile will be used.
    *
    *  @param rawFile A .raw or .out file containing ES60 datagrams.
    *  @param survey Name of the survey to use.
    **/
   public ES60File(File rawFile, String survey) {
	   file_ = rawFile;
	   if (survey == null ||
			   "".equals(survey)) {
		   if (rawFile != null) {
			   File parent = new File(rawFile.getAbsolutePath()).getParentFile();
			   if (parent != null)
				   survey = parent.getName();
		   }
	   }
	   survey_ = survey;
   }

   /* ---------- Public Methods ---------- */

   /**
    *  Open the file reading for reading.
    *  This method also determines if byte swapping is required.
    *  @throws IOException if it is unable to open the underlying File.
    **/
   public void open() 
   throws IOException {
	   if (file_ == null) {
		   BufferedInputStream buf = new BufferedInputStream(System.in);
		   stream_ = new DataInputStream(buf);
		   in_ = stream_;
		   
		   /*  Determine if little endian */
		   // Note that first telegram in a .raw file must be an
		   // echo sounder configuration datagram "CON0"
		   // (according to the Simrad manual) which has a size
		   // much smaller than Short.MAX_VALUE (848 bytes)
		   buf.mark(10);
		   int len = in_.readInt();
		   
		   int swaplen = ES60Record.swap(len);
		   swap_ = (len < 0) || ! (swaplen < 0 || len < swaplen);
		   buf.reset();
	   } else {
		   raFile_ = new RandomAccessFile(file_, "r");
		   in_ = raFile_;
		   int len = in_.readInt();
		   
		   int swaplen = ES60Record.swap(len);
		   swap_ = (len < 0) || ! (swaplen < 0 || len < swaplen);
		   raFile_.seek(0);
	   }
	   
   }

   /**
    *  Open the file reading for reading.
    *  This method also determines if byte swapping is required.
    *  If index is true it opens the file for indexing. 
    *  All subsequent read of RAW records from the file will be recorded in an index.
    *  @param index Open this file for indexing.
    *  @throws IOException if it is unable to open the underlying File.
    **/
   @SuppressWarnings("unchecked")
public void open(boolean index) 
   throws IOException {
	   open();
	   if (index) {
		   ES60Record config = read();
		   start_ = config.getTime();
		   if (config instanceof ES60CON) {
			   int channels = ((ES60CON)config).getChannels().length;
			   indices_ = new Vector[channels + 1];
			   for (int i = 0; i <= channels; i++)
				   indices_[i] = new Vector<Long>();
		   } else 
			   throw new IOException("CON0 record not at start of file, cannot create index");
	   }
   }

  /**
    *  Close the file.
    *  @throws IOException if it is unable to close the underlying file.
    **/
   public void close() 
   throws IOException {
	   if (stream_ != null) {
		   stream_.close();
		   stream_ = null;
	   }
	   if  (raFile_ != null) {
		   raFile_.close();
		   raFile_ = null;
	   }
   }

   /**
    *  Read a record from the file.
    *
    *  Will attempt to open the file if it is not already open.
    *
    *  @return Record containing the next datagram read from the file.
    *  @throws IOException if something goes wrong.
    **/
   public ES60Record read() 
      throws IOException {
      return read(false);
   }

   /**
    *  Read a record from the file.
    *
    *  If nmeaOnly is true then a record with only a header
    *  will be returned for datagrams other than NMEA datagrams.
    *
    *  Will attempt to open the file if it is not already open.
    *
    *  @param nmeaOnly I'm only interested in NMEA datagrams.
    *  @return Record containing the next datagram read from the file.
    *  @throws IOException if something goes wrong.
    **/
   public ES60Record read(boolean nmeaOnly) 
   throws IOException {
	   if (in_ == null)
		   open();
	   ES60Record retval = ES60Record.read(in_, this, nmeaOnly);
	   if (retval instanceof ES60CON)
		   config_ = (ES60CON)retval;
	   if (indices_ != null) {
		   if (retval instanceof ES60RAW) {
			   int channel = ((ES60RAW)retval).getChannel();
			   if (channel > 0 && channel < indices_.length) // Encountered ER60 data with one channel in config and every second ping on channel 2
				   indices_[channel].add(new Long(retval.getFilePointer()));
			   /* Ignore unknown channels.
			   else {
				   if (channel < indices_.length * 4) { // handle a reasonable number of additional channels
					   Vector<Long>[] indices = new Vector[channel + 1];
					   System.arraycopy(indices_, 0, indices, 0, indices_.length);
					   for (int i = indices_.length; i < indices.length; i++)
						   indices[i] = new Vector<Long>();
					   indices_ = indices;
					   indices_[channel].add(new Long(retval.getFilePointer()));
				   } else
				   System.err.println("Unexpected channel: " + channel);				   
			   }
			   */
		   }
		   if (retval instanceof ES60NMEA && ((ES60NMEA)retval).hasPos())
			   indices_[0].add(new Long(retval.getFilePointer()));
	   }
	   return retval;
   }

   /**
    *  Read a record from the file, starting at position pos.
    *
    *  Will attempt to open the file if it is not already open.
    *
    *  @param pos File pointer to start of record in file.
    *  @return Record containing the next datagram read from the file.
    *  @throws IOException if something goes wrong.
    **/
   public ES60Record read(long pos) 
   throws IOException {
	   if (raFile_ == null)
		   open();
	   if (raFile_ == null)
		   return null;
	   raFile_.seek(pos);
	   return read(false);
   }

    /**
    *  Returns a linked list of ES60NMEA records for the same point.
    *  Returns null at end of file.
    *
    *  The returned record is the head of a linked list of ES60NMEA records.
    *  where possible the head will have the most useful information
    *  ie RMC is preferred to GGA is preferred to GLL is preferred to others
    *
    *  @param sentences NMEA sentences of interest, 0 indicates all sentences.
    **/
   public ES60NMEA readPoint(long sentences) {
	   ES60NMEA head = lastNMEA_;
	   try {
		   while (true) { // till end of file or return when point complete
			   ES60Record dr = read(true);
			   if (dr instanceof ES60NMEA) {
				   ES60NMEA nmea = (ES60NMEA)dr;
				   if (sentences <= 0 ||
						   nmea.getSentence() == sentences) {
					   if (head == null)
						   head = nmea;
					   else {
						   // At the head we prefer a record that has position, date and time
						   // or as many of these as possible with position a priority
						   boolean prefer =(nmea.hasPos() && !head.hasPos()) ||
						   ((nmea.hasPos() || !head.hasPos()) &&
								   ((nmea.hasTime() && !head.hasTime()) ||
										   (nmea.hasDate() && !head.hasDate())));
						   
						   if (nmea.link(head, prefer)) {
							   if (prefer)
								   head = nmea;
						   } else { // doesn't link so it's a new point
							   lastNMEA_ = nmea;
							   return head;
						   }
					   }
				   }
			   }
		   }      
	   } catch (EOFException eof) {
		   if (lastNMEA_ != null) {
			   end_ = lastNMEA_.getTime();
		   }
		   if (indices_ != null) {
			   getIndex(0); // close the index
		   }
	   } catch (IOException ioe) {
		   ioe.printStackTrace();
	   }
	   lastNMEA_ = null;
	   return head;
   }

   /**
    *  Is byte swapping required for this file?
    *
    *  The value of the result is undefined until the file has been open()ed.
    *
    **/
   public boolean swap() {
      return swap_;
   }

   /** 
    *  Return the survey name for this file.
    *  @return the survey name.
    **/
   public String getSurvey() {
      return survey_;
   }

   /**
    *  Set the survey name for this file.
    *  @param survey Survey name.
    **/
   public void setSurvey(String survey) {
      survey_ = survey;
   }

   /**
    *  Returns the File for this ES60File.
    *  @return Underlying File.
    **/
   public File getFile() {
      return file_;
   }

   /**
    *  Returns the ES60CON for this ES60File, if available
    */
   public ES60CON getConfig() {
	   if (config_ == null) {
		   try {
			   read(0);
		   } catch (IOException ioe) {}
	   }
	   
	   return config_;
   }
   
   /**
    *  Returns the file name for the underlying file.
    *  @return getFile().getName();
    **/
   public String toString() {
      if (file_ == null)
	 return null;    
      return file_.getName();
   }

   /**
    *  An ES60File equals another if their underlying files are equal.
    *  This is compatible with the compare() method.
    *  @param o An object to test for equality.
    *  @return true, if o is an ES60File with the same underlying file.
    **/
   public boolean equals(Object o) {
      if (file_ == null)
	 return false;

      if (o instanceof ES60File) {
	 ES60File f = (ES60File)o;
	 return file_.equals(f.file_);
      } else
	 return false;
   }
   
   /**
    *  Does an index exist for this file?
    *  @return has an index been created.
    */
   public boolean hasIndex() {
	   return index_ != null;
   }
   
   /**
    *  Is an index being created for this file?
    *  While a file is being indexed, records should only be read
    *  sequentially.
    *  When end of file is encountered an index will be created and
    *  indexing will cease.
    *  
    *  @return true if an index of records in the file is being created. 
    */
   public boolean isIndexing() {
	   return indices_ != null;
   }
   
   /**
    *  Get the index for a particular data channel.
    *  For channel 0 the index is an array of file positions for NMEA records.
    *  For other channels the index is an array of file positions for all
    *  of the RAW records for that channel number.
    *  This method will close the index, so should only be called after the entire
    *  file has been read and indexed.
    *  For this method to successfully return an index the file must have been opened
    *  in indexing mode and read sequentially right through.
    *  @param channel 
    *  @return
    */
   public long[] getIndex(int channel) {
	   // Close the index
	   if (indices_ != null) {
		   index_ = new long[indices_.length][];
		   for (int i = 0; i < indices_.length; i++) {
			   int l = indices_[i].size();
			   index_[i] = new long[l];
			   for (int j = 0; j < l; j++)
				   index_[i][j] = ((Long)indices_[i].get(j)).longValue();
		   }
		   indices_ = null;
	   }
	   
	   if (index_ == null)
		   return null;
	   
	   return index_[channel];
   }		   

   public Date getStart() {
	   if (start_ == null) {
		   try {
			   ES60Record config = read(0);
			   start_ = config.getTime();
		   } catch (IOException ioe) {}
	   }
	   return start_;
   }

   public void setStart(Date start) {
	   start_ = start;
   }
   
   public Date getEnd() {
	   if (end_ == null && hasIndex()) {
		   long[] gps = getIndex(0);
		   if (gps.length > 0) {
			   long pos = gps[gps.length -1];
			   try {
				   ES60Record last = read(pos);
				   end_ = last.getTime();
			   } catch (IOException ioe) {}	
		   } else {
			   Logger.getLogger(ES60File.class.getName()).warning(file_ + " has no GPS data");
			   for (int i = 1; i < index_.length; i++) {
				   long[] idx = getIndex(i);
				   if (idx.length > 0) {
					   long pos = idx[idx.length -1];
					   try {
						   ES60Record last = read(pos);
						   end_ = last.getTime();
					   } catch (IOException ioe) {}		
				   }
			   }
		   }
	   }
	   if (end_ == null)
		   return start_;
	   return end_;
   }
   
   public void setEnd(Date end) {
	   end_ = end;
   }
   
   /* ----- Interface Comparable ----- */
	 
   /**
    *  Compare the full path of the underlying files.
    *
    *  @param o Object to compare
    *  @return If the object is an ES60File then the result of comparing the absolute path
    *          of the underlying files.
    **/
   public int compareTo(ES60File o) {
      if (file_ == null)
	 return -1;
      if (o == null) 
	 return -1;
      if (o instanceof ES60File)
	 return file_.getAbsolutePath().compareTo(((ES60File)o).file_.getAbsolutePath());
      return -2;
   }
   
   public void dump(PrintStream out) throws IOException {
	   ES60NMEA prev = null;
	   ES60NMEA next = null;
	   ArrayList<ES60RAW> pending = new ArrayList<ES60RAW>();
	   NumberFormat format = NumberFormat.getInstance();
	   format.setMinimumFractionDigits(0);
	   format.setMaximumFractionDigits(2);
	   format.setGroupingUsed(false);
	   
	   NumberFormat lformat = NumberFormat.getInstance();
	   lformat.setMinimumFractionDigits(0);
	   lformat.setMaximumFractionDigits(6);
	   lformat.setGroupingUsed(false);
	   
	   try {
		   out.println("longitude,latitude,time,channel,depth,power,Sv");
		   while (true) {
			   ES60Record dr = read();
			   
			   if (dr instanceof ES60RAW) {
				   pending.add((ES60RAW) dr);
			   }
			   
			   if (dr instanceof ES60NMEA && dr.hasPos()) {
				   prev = next;
				   next = (ES60NMEA) dr;
				   dumpPings(out, prev, next, pending, lformat, format);
			   }
		   }
	   } catch (EOFException e) {
		   dumpPings(out, prev, next, pending, lformat, format);
	   }
	   
   }
   
    private void dumpPings(PrintStream out, ES60NMEA prev, ES60NMEA next,
		ArrayList<ES60RAW> pending, NumberFormat lformat, NumberFormat format) {
    	if (prev != null) {
    		long pt = prev.getTime().getTime();
    		long nt = next.getTime().getTime();
    		double plong = prev.getLongitude();
    		double nlong = next.getLongitude();
    		double plat = prev.getLatitude();
    		double nlat = next.getLatitude();

    		for (ES60RAW ping : pending) {
    			long time = ping.getTime().getTime();
    			double prorata = (time - pt) / (double)(nt - pt);
    			double longitude = plong + prorata * (nlong - plong);
    			double latitude = plat + prorata * (nlat - plat);
    			int count = ping.getCount();
    			for (int i = 0 ; i < count; i++) {
    				double depth = ping.getDepth(i);
    				int power = ping.getPower(depth);
    				double sv = ping.getSv(depth);
    				out.println(lformat.format(longitude) + "," + lformat.format(latitude) + "," + time + "," + ping.getChannel() + "," +
    						format.format(depth) + "," + power + "," + format.format(sv));
    			}
    		}
    		pending.clear();
    	}
   }

  
    /* ---------- Static Methods ---------- */
    
	/**
	 * Is this file a valid ES60File?
	 * 
	 * An ES60File always starts with a CON datagram, so bytes 4-7 must be CON0.
	 * 
	 * @param file	File to test
	 * @return	This file is a valid ES60File.
	 */
	public static boolean isES60File(File file) {
		boolean retval = false;
		if (file.isFile()) {
			RandomAccessFile data = null;
			try {
				data = new RandomAccessFile(file, "r");
				@SuppressWarnings("unused")
				int len = data.readInt();
				int type = data.readInt();
				retval = type == ES60Record.CON0;
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
			} finally {
				try {
					if (data != null)
						data.close();
				} catch (IOException e) {
				}
			}
		}
		
		return retval;
	}
	

   
   public static void usage() {
	   System.err.println("usage: java " + ES60File.class.getCanonicalName() + " [-G] [-O outfile] es60file...");
	   System.err.println("       -G toggle .gps.csv format");
	   System.err.println("       -X toggle XYZS format");
	   System.err.println("       -O output to file (\"-\" for standard out - default)");
	   System.err.println("-G, -X and -O may appear multiple times and will apply to following files only");
   }
   
   public static void main(String[] args) {
	   boolean gps_csv = false;
	   boolean xyzs = false;
	   
	   PrintStream out = System.out;
	   int a = 0;
	   
	   if (args.length == 0)
		   usage();
	   
	   while (a < args.length) {
		   String arg = args[a++];
		   if (arg.startsWith("-")) {
			   String outfile = null;
			   if (arg.equalsIgnoreCase("-g")) 
				   gps_csv = !gps_csv;
			   
			   if (arg.equalsIgnoreCase("-x"))
				   xyzs = !xyzs;
			   
			   if (arg.equalsIgnoreCase("-o"))
				   outfile = args[a++];
			   
			   if (arg.startsWith("-o") || arg.startsWith("-O"))
				   outfile = arg.substring(2);
			   
			   if (outfile != null) {
				   out.close();
				   out = System.out;
				   try {
					   if (!"-".equals(outfile))
						   out = new PrintStream(outfile);
				   } catch (FileNotFoundException e) {
					   e.printStackTrace();
				   }
			   }

		   } else {
			   try {
				   File file = new File(arg);
				   ES60File es60 = new ES60File(file);

				   if (xyzs) {
					   es60.dump(out);
					   
				   } else if (gps_csv) {
					   ES60NMEA point = es60.readPoint(0);
					   while (point != null) {
						   if (point.hasPos()) {
							   out.println(new TrackPoint(point.getLongitude(), point.getLatitude(), point.getTime()).toGpsCsvString());
						   }
						   point = es60.readPoint(0);
					   }
					   
				   } else {
					   try {
						   while (true) {
							   ES60Record dr = es60.read();
							   out.println(dr.header_.toString() + "," + dr);
						   }
					   } catch (EOFException e) {
					   }
				   }
				   
				   es60.close();
			   } catch (Exception e) {
				   e.printStackTrace();
			   }
		   }
	   }
   }
}

/*
    In the fourth year that Jehoiakim son of Josiah was king of Judah,
    the Lord said to me, "Get a scroll and write on it everything
    that I have told you about Israel and Judah and all the nations.
    Write everything that I have told you from the time I first spoke to you,
    when Josiah was king, up to the present.
    Perhaps when the people of Judah hear about all the destruction
    that I intend to bring on them, they will turn from their evil ways.
    Then I will forgive their wickedness and their sins."
            Jeremiah 36:1-3
*/
