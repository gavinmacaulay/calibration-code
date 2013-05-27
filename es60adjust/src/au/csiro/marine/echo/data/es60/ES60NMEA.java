/*
    ES60NMEA.java  au.csiro.marine.echo.data.ES60NMEA

    Copyright 2002-2004, CSIRO Marine Research.
    All rights reserved.
    Released under the GPL and possibly other licenses.

    $Id: ES60NMEA.java 552 2012-12-16 23:17:00Z  $

    $Log: ES60NMEA.java,v $
    Revision 1.10  2006/02/28 00:07:39  kei037
    Cosmetic changes associated with move to eclipse.

    Revision 1.9  2005/05/31 05:52:01  kei037
    ES60Adjust working with code to find triangle wave in data.

    Revision 1.8  2005/05/12 06:43:00  kei037
    Working version of ES60Adjust program to remove triangular wave errors.

    Revision 1.7  2004/03/09 22:06:51  kei037
    Added support for GPS string including decimal timestamps

    Revision 1.6  2004/01/06 22:58:44  kei037
    Implemented lazy parsing and added getSentenceString.

    Revision 1.5  2003/04/09 23:51:01  kei037
    Added support for speed calculation.

    Revision 1.4  2003/01/23 00:04:51  kei037
    Refactored ES60Record to contain more of the stuff that was just in ES60NMEA.

    Revision 1.3  2003/01/16 01:09:45  kei037
    Cleaned up and document ES60 processing
    Moved ES60Survey to ES60File.

    Revision 1.2  2002/09/02 04:14:40  kei037
    Improvements to ES60 data handling.

    Revision 1.1  2002/08/02 07:49:59  kei037
    Added suppport for viewing ES60 GPS data


 */

package au.csiro.marine.echo.data.es60;

import java.awt.GridLayout;
import java.io.DataInput;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
    ES60NMEA contains a Simrad ES60 or EK60 NMEA data record (NME0 datagram).

    @version $Id: ES60NMEA.java 552 2012-12-16 23:17:00Z  $
    @author Gordon Keith
 **/
public class ES60NMEA extends ES60Record {
	/* ---------- Constants ---------- */

	/* ----- Supported NMEA sentences ----- */

	public static final int GGA = 0x474741; // 'G' << 16 | 'G' << 8 | 'A';
	public static final int GLL = 0x474c4c; // 'G' << 16 | 'L' << 8 | 'L';
	public static final int RMC = 0x524d43; // 'R' << 16 | 'M' << 8 | 'C';
	public static final int ZDA = 0x5a4441; // 'Z' << 16 | 'D' << 8 | 'A';
	public static final int RPY = 0x525059; // 'P' << 16 | 'R' << 8 | 'Y';

	/* ---------- Protected Members ---------- */
	/**
	 *  last ES60NMEA seen, used for calculating speed
	 **/
	protected static ES60NMEA last__;

	/**
	 *  Raw NMEA string.
	 **/
	protected String nmea_;

	/**
	 *  NMEA sentence type. 
	 *  Actually a three character (byte) string, but stored as an integer for convenience.
	 **/
	protected int sentence_;

	/**
	 *  The Time and Date parsed from the NMEA string, if any.
	 *  nmeaTime_ is null if there is no date/time information.
	 *  The date portion will not be set if there is time but no date information.
	 **/
	protected Date nmeaTime_;

	/**
	 *  The latitude parsed from the NMEA string, if any.
	 *  lat_ will be NaN if the sentence doesn't include latitude.
	 **/
	protected double lat_ = Double.NaN;

	/**
	 *  The longitude parsed from the NMEA string, if any.
	 *  lon_ will be NaN if the sentence doesn't include longitude.
	 **/
	protected double lon_ = Double.NaN;
	
	/**
	 *  The roll parsed from the NMEA string, if any.
	 *  roll_ will be NaN if the sentence doesn't include roll.
	 **/
	protected double roll_ = Double.NaN;
	
	/**
	 *  The pitch parsed from the NMEA string, if any.
	 *  pitch_ will be NaN if the sentence doesn't include pitch.
	 **/
	protected double pitch_ = Double.NaN;

	/**
	 *  Has this record been parsed?
	 *  Parsing may be done lazily, 
	 *  so that only sentences of interest get parsed.
	 **/
	protected boolean parsed_ = false;

	/* ---------- Factory Methods ---------- */

	/**
	 *  Read an ES60NMEA record from the DataInput.
	 *  Parsing of the record contents is not performed at this stage.
	 *
	 *  @param in   DataInput to read record from.
	 *  @param len  Number of bytes from DataInput that constitute the record.
	 *  @param swap Is byteswapping required?
	 *  @return An ES60RNMEA record read from in. 
	 **/
	public static ES60Record read(DataInput in, int len, boolean swap)
	throws IOException {
		ES60NMEA retval = new ES60NMEA();
		retval.data_ = new byte[len];
		in.readFully(retval.data_);
		//retval.nmea_ = new String(retval.data_);
		//retval.parse();

		return retval;
	}

	/**
	 *  Construct a ES60NMEA from the output of toCSVString which has been split by by delimiter.
	 *  ie fromCSV(nmea.getFile(), nmea.toCSVString(delim, n, s).split(delim));
	 *  should return something similar to the original nmea.
	 */
	public static ES60NMEA fromCSV(ES60File file, String[] line) 
	throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		format.setTimeZone(new SimpleTimeZone(0, "GMT"));

		ES60NMEA retval = new ES60NMEA();
		retval.esFile_ = file;
		retval.header_ = new ES60Header(0, 0);
		retval.header_.setTime(format.parse(line[2]));
		retval.lat_ = java.lang.Double.parseDouble(line[3]);
		retval.lon_ = java.lang.Double.parseDouble(line[4]);

		String timestamp;
		if (line.length > 5 && !"".equals(line[5]))
			timestamp = line[5];
		else
			timestamp = "1970-01-01";
		if (line.length > 6 && !"".equals(line[6]))
			timestamp += " " + line[6];
		else
			timestamp += " 00:00:00";
		if ("1970-01-01 00:00:00".equals(timestamp))
			retval.nmeaTime_ = null;
		else
			retval.nmeaTime_ = format.parse(timestamp);

		if (line.length > 7)
			retval.nmea_ = line[7];

		return retval;
	}



	/* ---------- Protected Methods ---------- */

	/**
	 *  Parse the NMEA string to internal data structures
	 **/
	protected void parse() {
		if (data_ == null)
			return;
		if (nmea_ == null)
			nmea_ = new String(data_);
		if (nmea_.length() < 6) 
			return;

		String[] fields = nmea_.split(",");
		int f = 0;
//		StringTokenizer tokens = new StringTokenizer(nmea_, ",");
		String token = fields[f++];
		if (token.length() > 5) {
			sentence_ = token.charAt(3) << 16 | token.charAt(4) << 8 | token.charAt(5);

			try {
				switch (sentence_) {
				case GGA:
					int ggatime = (int)(Double.parseDouble(fields[f++]) * 1000);
					long gtime = 
						(((ggatime / 10000000) * 60 +
								(ggatime / 100000) % 100) * 60 +
								(ggatime / 1000) % 100) * 1000 +
								(ggatime % 1000);
					nmeaTime_ = new Date(gtime);
					/* continue in GLL case to get lat and lon */

				case GLL: {
					double lat = Double.parseDouble(fields[f++]);
					lat = ((int)(lat / 100)) + (lat% 100) / 60.0;
					if (fields[f++].equals("S"))
						lat *= -1;
					double lon = Double.parseDouble(fields[f++]);
					lon = ((int)(lon / 100)) + (lon % 100) / 60.0;
					if (fields[f++].equals("W"))
						lon *= -1;
					lat_ = lat;
					lon_ = lon;
				}
				break;

				case RMC: {
					int rmctime = (int)(Double.parseDouble(fields[f++]) * 1000);

					f++; // validity

					double lat = Double.parseDouble(fields[f++]);
					lat = ((int)(lat / 100)) + (lat% 100) / 60.0;
					if (fields[f++].equals("S"))
						lat *= -1;
					double lon = Double.parseDouble(fields[f++]);
					lon = ((int)(lon / 100)) + (lon % 100) / 60.0;
					if (fields[f++].equals("W"))
						lon *= -1;
					lat_ = lat;
					lon_ = lon;

					f++; // speed (knots)
					f++; // heading (degrees true)

					int rmcdate = Integer.parseInt(fields[f++]);
					GregorianCalendar cal = new GregorianCalendar(new SimpleTimeZone(0,"GMT"));
					cal.clear();
					cal.set(2000 + (rmcdate % 100),
							(rmcdate / 100) % 100 -1,
							rmcdate / 10000,
							rmctime / 10000000,
							(rmctime / 100000) % 100,
							(rmctime / 1000) % 100);
					cal.setTimeInMillis(cal.getTimeInMillis() + (rmctime % 1000));
					nmeaTime_ = cal.getTime();

				}
				break;

				case ZDA: 
					int zdaTime = (int)(Double.parseDouble(fields[f++]) * 1000);
					int zdaDay = Integer.parseInt(fields[f++]);
					int zdaMonth = Integer.parseInt(fields[f++]);
					int zdaYear = Integer.parseInt(fields[f++]);

					GregorianCalendar zdaCal = new GregorianCalendar(new SimpleTimeZone(0,"GMT"));
					zdaCal.clear();
					zdaCal.set(zdaYear,
							zdaMonth -1,
							zdaDay,
							zdaTime / 10000000,
							(zdaTime / 100000) % 100,
							(zdaTime / 1000) % 100);
					zdaCal.setTimeInMillis(zdaCal.getTimeInMillis() + (zdaTime % 1000));
					nmeaTime_ = zdaCal.getTime();

					break;
					
				case RPY: // CSIRO Roll Pitch Yaw
					roll_ = Double.parseDouble(fields[f++]);
					pitch_ = Double.parseDouble(fields[f++]);
					
				default:
				}
			} catch (Exception e) {
				System.err.println(nmea_);
				e.printStackTrace();
			}
			parsed_ = true;
		}
	}

	/* ---------- Public Methods ---------- */

	/**
	 *  Add this record to the linked list of ES60Records
	 *  headed by head, if it belongs to the same set.
	 *
	 *  This record belongs to the head set if:
	 *<ul><li>there isn't an NMEA sentence of this type already in the list</li>
	 *<li>the time difference between this record and the head is less than 5 seconds</li>
	 *</ul>
	 *  @param head head record of list
	 *  @param prepend put this on head of list, else tail.
	 *  @return true if this record was accepted into the list.
	 **/  
	public boolean link(ES60Record head, boolean prepend) {
		ES60Record next = head;

		while (next != null) {
			if ((next instanceof ES60NMEA)
					&& ((ES60NMEA)next).getSentence() == getSentence())
				return false;
			next = next.next_;
		}

		return super.link(head, prepend);
	}

	/**
	 *  Return the raw NMEA string
	 *  @return the raw NMEA string
	 **/
	public String toString() {
		if (nmea_ == null && 
				data_ != null)
			nmea_ = new String(data_);
		return nmea_;
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
		return toCSVString(delim, nmea, false);
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
	 *  @param speed Calculate and display approximate speed.
	 *  @return String containing formatted contents of the record.
	 **/
	public String toCSVString(String delim, boolean nmea, boolean speed) {
		if (!parsed_)
			parse();

		NumberFormat latFormat = NumberFormat.getInstance();
		latFormat.setMaximumFractionDigits(6);
		latFormat.setMinimumFractionDigits(6);

		// Survey and file name
		StringBuffer retval = new StringBuffer(super.toCSVString(delim, nmea));

		// Latitude and Longitude
		if (hasPos()) {
			retval.append(delim);
			retval.append(latFormat.format(getLatitude()));
			retval.append(delim);
			retval.append(latFormat.format(getLongitude()));
		} else if (nmea) 
			retval.append(delim).append(delim);

		// NMEA date and time.
		if (hasTime()) {
			if (!nmea && !hasPos())
				retval.append(delim).append(delim);
			retval.append(delim);
			if (hasDate(true)) {
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
				format.setTimeZone(new SimpleTimeZone(0, "GMT"));
				retval.append(format.format(getNmeaTime(true)));
			}
			retval.append(delim);
			SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
			format.setTimeZone(new SimpleTimeZone(0, "GMT"));
			retval.append(format.format(getNmeaTime()));
		} else if (nmea) 
			retval.append(delim).append(delim);

		if (speed) {
			if (!hasTime() && !nmea)
				retval.append(delim).append(delim);

			boolean hasSpeed = false;
			if (hasPos()) {
				if (last__ == null) {
					retval.append(delim).append("0");
				} else {
					double time = getTime().getTime() - last__.getTime().getTime();
					if (time < 0)
						time = - time;
					if (time != 0) {
						double dist = getDistance(last__);
						// speed (kn)= (dist (m) / 1852) / (time (ms) /3600000) = dist * 3600000 / 1852 / time
						double spd = 1943.84 * dist / time;
						NumberFormat speedFormat = NumberFormat.getInstance();
						speedFormat.setMaximumFractionDigits(2);
						retval.append(delim).append(speedFormat.format(spd));
						hasSpeed = true;
					}
				}
				last__ = this;
			} 
			if (nmea && !hasSpeed)
				retval.append(delim);
		}

		// raw NMEA string
		if (nmea) {
			retval.append(delim);
			retval.append(nmea_);
		}

		return retval.toString(); 
	}

	/**
	 * Return a header matching the data in toCSVString()
	 * @param delim
	 * @param nmea
	 * @return	String containing header matching toCSVString()
	 */
	public String getCSVHeader(String delim, boolean nmea, boolean speed) {
		StringBuffer retval = new StringBuffer(super.getCSVHeader(delim, nmea));

		// Latitude and Longitude
		retval.append(delim);
		retval.append("Lat");
		retval.append(delim);
		retval.append("Lon");

		// NMEA date and time.
		retval.append(delim);
		retval.append("GPSDate");
		retval.append(delim);
		retval.append("GPSTime");

		if (speed) {
			retval.append(delim).append("Speed_kn");
		}

		// raw NMEA string
		if (nmea) {
			retval.append(delim);
			retval.append("NMEA");
		}

		return retval.toString(); 
	}

	/**
	 *  Return the date and time information from the NMEA string.
	 *  Will be null if the NMEA sentence doesn't include time information.
	 *
	 *  Note that getNmeaTime() may be very different from getTime() 
	 *  which returns the telegram timestamp, which comes from the logging system's clock.
	 *
	 *  @return Date parsed from NMEA sentence, may be null.
	 **/
	public Date getNmeaTime() {
		if (!parsed_)
			parse();
		return nmeaTime_;
	}

	/**
	 *  Return the date and time information from the NMEA string
	 *  from this or any of the associated NMEA records.
	 *  Will be null if the NMEA sentence doesn't include time information.
	 *
	 *  Note that getNmeaTime() may be very different from getTime() 
	 *  which returns the telegram timestamp, which comes from the logging system's clock.
	 *
	 *  @param extended Search associated NMEA records?
	 *  @return Date parsed from NMEA sentence, may be null.
	 **/
	public Date getNmeaTime(boolean extended) {
		if (hasDate() || ! extended)
			return nmeaTime_;
		for (ES60Record next = next_; next != null; next=next.next_) {	   
			if (next instanceof ES60NMEA && next.hasDate()) {
				return ((ES60NMEA)next).nmeaTime_;
			}
		}
		return nmeaTime_;
	}

	/**
	 *  Return the latitude from the NMEA string, 
	 *  or NaN if the record  doesn't contain position information.
	 *  @return Latitude in decimal degrees or NaN.
	 **/
	public double getLatitude() {
		if (!parsed_)
			parse();
		return lat_;
	}

	/**
	 *  Return the longitude from the NMEA string, 
	 *  or NaN if the record doesn't contain position information.
	 *  @return Longitude in decimal degrees or NaN.
	 **/
	public double getLongitude() {
		if (!parsed_)
			parse();
		return lon_;
	}

	/**
	 *  Returns true if the NMEA sentence includes a date field.
	 *  @return Whether the record has a date.
	 **/      
	public boolean hasDate() {
		if (!parsed_)
			parse();
		return nmeaTime_ != null && nmeaTime_.getTime() > 86400000L;
	}

	/**
	 *  Returns true if the NMEA sentence includes a date field.
	 *  @return Whether the record has a date.
	 **/      
	public boolean hasDate(boolean extended) {
		if (!extended)
			return hasDate();
		for (ES60Record next = this; next != null; next = next.next_) {	   
			if (next.hasDate()) {
				return true;
			}
		}
		return false;
	}

	/**
	 *  Returns true if the NMEA sentence includes a time field.
	 *  @return Whether the record has a time.
	 **/
	public boolean hasTime() {
		if (!parsed_)
			parse();
		return nmeaTime_ != null;
	}

	/**
	 *  Returns true if the NMEA sentence includes latitude and longitude.
	 *  @return Whether the record has a position
	 **/
	public boolean hasPos() {
		if (!parsed_)
			parse();
		return !Double.isNaN(lat_);
	}      

	/**
	 *  Returns true if the NMEA sentence includes pitch and roll.
	 *  @return Whether the record has a motion data
	 **/
	public boolean hasMotion() {
		if (!parsed_)
			parse();
		return !Double.isNaN(roll_) && !Double.isNaN(pitch_);
	}      

	/**
	 *  Returns the 3 character sentence identifier (eg GLL, GGA, etc)
	 *  as an integer value.
	 *  @return Integer representation of 3 byte sentence identifier.
	 **/
	public int getSentence() {
		if (sentence_ == 0 && 
				data_ != null && 
				data_.length > 5)
			sentence_ = data_[3] << 16 | data_[4] << 8 | data_[5];

		return sentence_;
	}

	/**
	 *  Returns the 3 character sentence identifier (eg GLL, GGA, etc).
	 *  @return 3 byte sentence identifier.
	 **/
	public String getSentenceString() {
		if (nmea_ != null && 
				nmea_.length() > 5) 
			return nmea_.substring(3,6);

		if (data_ != null &&
				data_.length > 5)
			return new String(data_, 3, 3);

		return null;
	}

	/**
	 *  Calculate the distance between records.
	 **/
	public double getDistance(ES60NMEA point) {
		if (point == null)
			return Double.NaN;

		if (!hasPos() || !point.hasPos())
			return Double.NaN;

		// Methods taken from http://www.auslig.gov.au/geodesy/datums/distance.htm

		final double pi_180 = Math.PI /180.0;
		double dL = point.lat_ - lat_; 
		double dG = point.lon_ - lon_;
		/*
      // Great circle on sphere
      double dist = 1852 * 60 / pi_180 *
	 Math.acos(Math.sin(lat_ * pi_180) * Math.sin(point.lat_ * pi_180) + 
		   Math.cos(lat_ * pi_180) * Math.cos(point.lat_ * pi_180) * Math.cos(dG * pi_180));
		 */

		// Spheroidal model for the earth
		double delta = 0.0000000001;
		if (dL < 0)
			dL *= -1;
		if (dG < 0)
			dG *= -1;
		double term1 = 111089.56 * (dL + delta); 
		double term2 = Math.cos((lat_ + (dL/2.0)) * pi_180); 
		double term3 = (dG + delta) / (dL + delta);
		double dist =  term1 / Math.cos(Math.atan(term2 * term3)); 

		return dist;
	}

	/* ---------- Inner Classes ---------- */

	/**
      ES60NMEA.Display is a JComponent for displaying an NMEA telegram.
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
		protected ES60NMEA current_;

		/* ----- Widgets ----- */

		protected JTextField surveyField_;

		protected JTextField esFileField_;

		protected JTextField timeField_;

		protected JTextField nmeaField_;

		protected JTextField sentenceField_;

		protected JTextField latField_;

		protected JTextField lonField_;

		protected JTextField nmeaTimeField_;

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

			// Layout

			JPanel dataLabelPane = new JPanel(new GridLayout(0,1));
			JPanel dataValuePane = new JPanel(new GridLayout(0,1));

			dataLabelPane.add(new JLabel("Survey: "));
			dataValuePane.add(surveyField_);

			dataLabelPane.add(new JLabel("File: "));
			dataValuePane.add(esFileField_);

			dataLabelPane.add(new JLabel("Time: "));
			dataValuePane.add(timeField_);

			dataLabelPane.add(new JLabel("NMEA: "));
			dataValuePane.add(nmeaField_);

			dataLabelPane.add(new JLabel("Latitude: "));
			dataValuePane.add(latField_);

			dataLabelPane.add(new JLabel("Longitude: "));
			dataValuePane.add(lonField_);

			dataLabelPane.add(new JLabel("GPS Time: "));
			dataValuePane.add(nmeaTimeField_);

			dataLabelPane.add(new JLabel(" "));

			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			add(dataLabelPane);
			add(dataValuePane);
		}

		/**
		 *  Display the specified record.
		 *  Fields are set to blank if the record is null.
		 *  @param nmea ES60NMEA to display.
		 **/
		public void display(ES60NMEA nmea) {
			current_ = nmea;
			latField_.setText(" ");
			lonField_.setText(" ");
			nmeaTimeField_.setText(" ");

			if (nmea != null) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				dateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));

				if (nmea.esFile_ == null) {
					surveyField_.setText(" ");
					esFileField_.setText(" ");
				} else {
					surveyField_.setText(nmea.esFile_.getSurvey());
					esFileField_.setText(nmea.esFile_.toString());
				}

				timeField_.setText(dateFormat.format(nmea.getTime()));
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

			} else {  // nmea == null
				surveyField_.setText(" ");
				esFileField_.setText(" ");
				timeField_.setText(" ");
				nmeaField_.setText(" ");
				sentenceField_.setText(" ");	    
			}
		}
	}
}

/*
    "Do not be worried and upset," Jesus told them.
    "Believe in God an believe also in me.
    There are may rooms in my Father's house,
    and I am going to prepare a place for you.
    I would not tell you this if it were not so.
    And after I go and prepare a place for you,
    I will come back and take youto myself,
    so that you will be where I am.
    You know the way that leads to the place 
    where I am going."

    Thomas said to him, "Lord, we do not know where you are going;
    so how can we know the way to get there?"

    Jesus answered him, "I am the way, the truth,
    and the life; no one goes to the Father except by me."

            John 14:1-6
 */
