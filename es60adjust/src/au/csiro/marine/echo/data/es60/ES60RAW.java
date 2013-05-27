/*
    ES60RAW.java  au.csiro.marine.echo.data.ES60RAW

    Copyright 2005, CSIRO Marine Research.
    All rights reserved.
    Released under the GPL and possibly other licenses.

    $Id: ES60RAW.java 487 2012-05-22 23:14:27Z  $

    $Log: ES60RAW.java,v $
    Revision 1.6  2006/05/03 01:32:20  kei037
    Added support for viewing echograms.

    Revision 1.5  2006/04/12 06:11:23  kei037
    Initial implementation of echogram display in DataView

    Revision 1.4  2006/02/28 00:07:39  kei037
    Cosmetic changes associated with move to eclipse.

    Revision 1.3  2005/05/31 05:52:01  kei037
    ES60Adjust working with code to find triangle wave in data.

    Revision 1.2  2005/05/12 06:43:00  kei037
    Working version of ES60Adjust program to remove triangular wave errors.

    Revision 1.1  2005/05/05 05:46:11  kei037
    Added ES60Adjust to remove ES60 triangular error.


*/

package au.csiro.marine.echo.data.es60;

import java.awt.GridLayout;
import java.io.DataInput;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.SimpleTimeZone;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
    ES60RAW contains a Simrad ES60 or EK60 RAW data record (RAW0 datagram).

<pre>
	struct SampleDatagram
	{
		DatagramHeader DgHeader; // "RAW0"
		short Channel; // Channel number
		short Mode; // Datatype
		float TransducerDepth; // [m]
		float Frequency; // [Hz]
		float TransmitPower; // [W]
		float PulseLength; // [s]
		float BandWidth; // [Hz]
		float SampleInterval; // [s]
		float SoundVelocity; // [m/s]
		float AbsorptionCoefficient; // [dB/m]
		float Heave; // [m]
		float Roll; // [deg]
		float Pitch; // [deg]
		float Temperature; // [C]
		short TrawlUpperDepthValid; // None=0, expired=1, valid=2
		short TrawlOpeningValid; // None=0, expired=1, valid=2
		float TrawlUpperDepth; // [m]
		float TrawlOpening; // [m]
		long	  Offset; // First sample
		long  Count; // Number of samples
		short Power[]; // Compressed format - See below!
		short Angle[]; // See below!
	};
	
	power = Power * 10 * log10(2) / 256
	Angle - the fore-and-aft (alongship) and athwartship electrical angles
	are output as one 16-bit word. The alongship angle is the most significant byte
	while the athwartship angle is the least significant byte.
	Angle data is expressed in 2's complement format, and the resolution
	is given in steps of 180/128 electrical degrees per unit.
	Positive numbers denotes the fore and starboard directions
	</pre>
	
    @version $Id: ES60RAW.java 487 2012-05-22 23:14:27Z  $
    @author Gordon Keith
**/
public class ES60RAW extends ES60Record {

   /* ---------- Constants ---------- */

    /**
     *  length of the header block prior to data arrays.
     **/
    public static final int HEADER = 72;
   
    public static final double LN_10 = Math.log(10);
    public static final double _LN_10 = 1.0 / LN_10;
   /**
     *  Convert sample to dB.
     **/
    public static final double SAMPLE_TO_DB = 10 * Math.log(2)/ LN_10 / 256.0;

   /* ---------- Protected Members ---------- */

   /**
    *  Has this record been parsed?
    *  Parsing may be done lazily, 
    *  so that only datagrams of interest get parsed.
    **/
    protected boolean parsed_ = false;
   
    /**
     *  Has the svCorrection for this ping been calculated?
     *  svCorrection is only calculated on demand as it may be computationally expensive
     */
    protected boolean corrected_ = false;
    
    /**
     *  Ping constant component of SV calculation.
     *  This value is undefined if corrected_ is false.
     */
    protected double svCorrection_;
    
    /**
     *  Does this ping have angle data (split beam).
     *  This should be true iff mode == 1, according the manual.
     *  the data appears to believe otherwise.
     */
    protected boolean hasAngles_;
    
    /**
     *  1 / (sampleInterval_ * soundVelocity_) for performance.
     */
    protected double samplesPerM_;
    
    /**
     *  Cached Sv values for improved performance 
     */
    protected double[] sv_;
    
    /* ----- Record values ----- */
    
    /**
     *  Channel number
     **/
    protected int channel_;

    /**
     *  Datatype, 0 = power, 1 = power and angle.
     *  Data doesn't seem to match the above description,
     *  3 seems to have power and angle, 1 sometimes has no angles.
     **/
    protected int mode_;

    /**
     *  Transducer depth [m]
     **/
    protected float transducerDepth_;

    /**
     *  Frequency [Hz]
     **/
    protected float frequency_;

    /**
     *  Transmit Power [W]
     **/
    protected float transmitPower_;

    /**
     *  Pulse length [s]
     **/
    protected float pulseLength_;

    /**
     *  Band width [Hz]
     **/
    protected float bandWidth_;

    /**
     *  Sample interval [s]
     **/
    protected float sampleInterval_;

    /**
     *  Sound velocity [m/s]
     **/
    protected float soundVelocity_;

    /**
     *  Absorption coefficient [dB/m]
     **/
    protected float absorptionCoefficient_;

    /**
     *  Heave [m]
     **/
    protected float heave_;

    /**
     *  Roll [deg]
     **/
    protected float roll_;

    /**
     *  Pitch [deg]
     **/
    protected float pitch_;

    /**
     *  Temperature [C]
     **/
    protected float temperature_;

    /**
     *  Trawl upper depth valid: none=0, expired=1, valid=2
     **/
    protected int trawlUpperDepthValid_;

    /**
     *  Trawl opening valid: none=0, expired=1, valid=2
     **/
    protected int trawlOpeningValid_;

    /**
     *  Trawl upper depth [m]
     **/
    protected float trawlUpperDepth_;

    /**
     *  Trawl opening [m] 
     **/
    protected float trawlOpening_;

    /**
     *  first sample
     **/
    protected int offset_;

    /**
     *  number of samples
     **/
    protected int count_;

    /**
     *  y = x(10log(2))/256 dB
     **/
    int[] power_;

    /**
     *  Alongship electrical angle - signed 180/128 electrical degrees +ve fore
     **/ 
    byte[] alongship_;

    /**
     *  Athwartship electrical angle - signed 180/128 electrical degrees +ve starboard
     **/ 
     byte[] athwartship_;
   
   /* ---------- Factory Methods ---------- */

   /**
    *  Read an ES60RAW record from the DataInput.
    *  Parsing of the record contents is not performed at this stage.
    *
    *  @param in   DataInput to read record from.
    *  @param len  Number of bytes from DataInput that constitute the record.
    *  @param swap Is byteswapping required?
    *  @return An ES60RAW record read from in. 
    **/
   public static ES60Record read(DataInput in, int len, boolean swap)
      throws IOException {
      ES60RAW retval = new ES60RAW();
      retval.swap_ = swap;
      retval.data_ = new byte[len];
      in.readFully(retval.data_);
      return retval;
   }

   /* ---------- Protected Methods ---------- */

    /**
    *  Parse the RAW data to internal data structures.
    *
    *  Parsing is not automatically done when the record is read
    *  this can be a computationally expensive step that is not
    *  needed in many cases. 
    *  The allocation of arrays for power_, alongship_ and athwartship_
    *  may be particularly expensive.
    **/
   public void parse() {
	   if (data_.length < HEADER) {
		   System.out.println("Insufficient data to parse");
		   return;
	   }
	   
	   /* parse fields */
	   channel_ = decodeShort(0);
	   mode_ = decodeShort(2);
	   transducerDepth_ = decodeFloat(4);
	   frequency_ = decodeFloat(8);
	   transmitPower_ = decodeFloat(12);
	   pulseLength_ = decodeFloat(16);
	   bandWidth_ = decodeFloat(20);
	   sampleInterval_ = decodeFloat(24);
	   soundVelocity_ = decodeFloat(28);
	   absorptionCoefficient_ = decodeFloat(32);
	   heave_ = decodeFloat(36);
	   roll_ = decodeFloat(40);
	   pitch_ = decodeFloat(44);
	   temperature_ = decodeFloat(48);
	   trawlUpperDepthValid_ = decodeShort(52);
	   trawlOpeningValid_ = decodeShort(54);
	   trawlUpperDepth_ = decodeFloat(56);
	   trawlOpening_ = decodeFloat(60);
	   offset_ = decodeLong(64);
	   count_ = decodeLong(68);
	   
	   samplesPerM_ = 2 / (sampleInterval_ * soundVelocity_);
	   
	   /* Check record length and output diagnostics if not correct */
	   if (data_.length == HEADER + 2 * count_) 
		   hasAngles_ = false;
	   else if (data_.length == HEADER + 4 * count_) 
		   hasAngles_ = true;
	   else  {
		   System.out.println("Bad record length, expected: " +
				   (HEADER + 2 * (1 + mode_) * count_) +
				   " got: " + data_.length);
		   System.out.println("swap:  " + swap_);
		   System.out.println("mode:  " + mode_);
		   System.out.println("count: " + count_);
		   for (int i = 0; i < HEADER; i++) {
			   if (i % 4 == 0) System.out.print("   ");
			   if (i % 16 == 0) System.out.println("");
			   if ((data_[i] & 0xf0) == 0) System.out.print("0");
			   System.out.print(Integer.toString(data_[i] & 0xff,16)+" ");
		   }
		   return;
	   }
	   
	   /* parse data arrays. reparsing is permitted so check for old arrays. */
	   if (!parsed_ || power_.length < count_)
		   power_ = new int[count_];
	   for (int i = 0; i < count_; i++)
		   power_[i] = decodeShort(HEADER + 2 * i);
	   
	   /*# I'm no sure of the correct meaning of mode.
	    *# the EK60 manual says it can be 0 or 1, but ES60 data I've seen had 3.
	    *# if this doesn't work the record length test should have been triggered.
	    */
	   if (hasAngles_) {
		   if (!parsed_ || athwartship_.length < count_) {
			   athwartship_ = new byte[count_];
			   alongship_ = new byte[count_];
		   }
		   int start = HEADER + 2 * count_;
		   for (int i = 0; i < count_; i++) {
			   if (swap_) {
				   alongship_[i]   = data_[start + 2 * i + 1];
				   athwartship_[i] = data_[start + 2 * i];
			   } else {
				   alongship_[i]   = data_[start + 2 * i];
				   athwartship_[i] = data_[start + 2 * i + 1];
			   }
		   }
	   } else {
		   alongship_ = new byte[0];
		   athwartship_ = new byte[0];
	   }
	   
	   parsed_ = true;
   }

   /**
    *  Calculate the ping constant component of the Sv calculation.
    *  @see "http://support.echoview.com/WebHelp/Reference/Algorithms/Echosounder/Simrad/EK60_Power_to_Sv_and_TS.htm"
    */
   public double correct() {
	   if (!parsed_)
		   parse();
	   ES60CON config = esFile_.getConfig();
	   double gain = Math.pow(config.getGain(channel_, pulseLength_) / 10.0, 10);
	   float sa = config.getSaCorrection(channel_, pulseLength_);
	   double beamAngle = Math.pow(config.getBeamAngle(channel_) / 10.0, 10);
	   svCorrection_ = - 10.0 * Math.log(transmitPower_ * gain * gain * 
			   	soundVelocity_ * soundVelocity_ * soundVelocity_ * pulseLength_ * beamAngle / 
			   	(32 * Math.PI * Math.PI * frequency_ * frequency_)) * _LN_10 -
			   	2 * sa;
	   return svCorrection_;
   }
   
    /**
     *  Returns the channel number.
     **/
    public int getChannel() {
	if (!parsed_)
	    channel_ = decodeShort(0);
	return channel_;
    }

    /**
     *  Returns the number of samples.
     **/
    public int getCount() {
	if (!parsed_)
	    count_ = decodeShort(68);
	return count_;
    }
    
    /**
     * Returns the transmit power in Watts
     * @return Transmit power (W)
     */
    public float getTransmitPower() {
    	if (!parsed_)   
    		transmitPower_ = decodeFloat(12);
    	return transmitPower_;
    }

    /**
     * Returns the pulse length in s
     * @return Pulse length (s)
     */
    public float getPulseLength() {
    	if (!parsed_)
    		pulseLength_ = decodeFloat(16);
    	return pulseLength_;
    }
    
    /**
     *  Returns the sample power
     */
    public int[] getPower() {
    		if (!parsed_)
    			parse();
    		return power_;
    }
    
    /**
     *  Returns the sample power at depth 
     */
    public int getPower(double depth) {
		if (!parsed_)
			parse();
		int d = (int)(depth * samplesPerM_ + 0.5) - offset_; 
		if (d < 0 || d >= power_.length)
			return 0;
		return power_[d];
   		
    }
    
    public double getSv(double depth) {
		if (!corrected_)
			correct();
		int d = (int)(depth * samplesPerM_ + 0.5) - offset_; 
		
		if (power_ == null || d >= power_.length || d < 0)
			return Double.NaN;
		if (sv_ == null) {
			sv_ = new double[power_.length];
			for (int i = 0; i < sv_.length; i++)
				sv_[i] = Double.NaN;
		}
		if (Double.isNaN(sv_[d]))
			sv_[d] =  power_[d] * SAMPLE_TO_DB + 
					(depth < 1 ? 0 : 20 * Math.log(depth) * _LN_10) + 
					2 * absorptionCoefficient_ * depth +
					svCorrection_;
		return sv_[d];
    }
    
    public double getDepth(int d) {
		if (!parsed_)
			parse();
		return (offset_ + d) / samplesPerM_;
    }
    
    /**
     *  
     *  @return Maximum depth of the ping.
     */
    public double getMaxRange() {
    		if (!parsed_)
    			parse();
    		return (offset_ + count_) * soundVelocity_ * sampleInterval_ / 2;
    }

    /**
     *  
     *  @return Minimum depth of the ping.
     */
    public double getMinRange() {
    		if (!parsed_)
    			parse();
    		return offset_ * soundVelocity_ * sampleInterval_ / 2;
    }

   /**
    *  Add this record to the linked list of ES60Records
    *  headed by head, if it belongs to the same set.
    *
    *  This record belongs to the head set if:
    *<ul><li>there isn't an RAW datagram for this channel already in the list</li>
    *<li>the time difference between this record and the head is less than 5 seconds</li>
    *</ul>
    *  @param head head record of list
    *  @param prepend put this on head of list, else tail.
    *  @return true if this record was accepted into the list.
    **/  
   public boolean link(ES60Record head, boolean prepend) {
      ES60Record next = head;

      getChannel();

      while (next != null) {
	 if ((next instanceof ES60RAW) && 
	     ((ES60RAW)next).getChannel() == channel_)
	    return false;
	 next = next.next_;
      }

      return super.link(head, prepend);
   }

   /**
    *  Correct ES60 power levels.
    *
    *  Simrad appears to have added a deliberate error in ES60 power data.
    *  This does not appear in EK60 data.
    *  This method removes this error given the ping number from the last
    *  hardware reset.
    *
    *  @param adjustment value to subtract from every power value in this ping
    **/
   public void es60adjust(int adjustment) {
       if (data_.length < HEADER) {
	   System.err.println("ES60RAW record too small");
	   return;
       }

       if (!parsed_)
	   count_ = decodeLong(68);
	   
       int end = HEADER + 2 * count_;
       if (data_.length < end) {
	   System.err.println("ES60RAW insufficient data in record");
	   return;
       }

       for (int i = HEADER; i < end; i += 2) {
	   int val =  decodeShort(i);
	   val -= adjustment;
	   encodeShort(i, val);
       }
       
       /* reparse adjusted data values if needed. */
       if (parsed_)
	   parse();
   }

    /**
     *  Integrates the power values for the specified range of samples.
     *  @param first First sample number to include.
     *  @param last Last sample number to include, must be >= first.
     *  @throws ArrayIndexOutOfBoundsException if last > number of available samples.
     **/
    public int getSum(int first, int last) 
	throws ArrayIndexOutOfBoundsException {
       if (data_.length < HEADER) 
	   throw new ArrayIndexOutOfBoundsException("ES60RAW record too small");

       if (!parsed_)
	   count_ = decodeLong(68);
	   
       if (count_ < last)
	   throw new InsufficientSamplesException();

       int end = HEADER + 2 * last + 2;
       if (data_.length < end) 
	   throw new ArrayIndexOutOfBoundsException("ES60RAW insufficient data in record");

       int retval = 0;
       for (int i = HEADER + 2 * first; i < end; i += 2) 
	   retval += decodeShort(i);

       return retval;	
    }

    public String toString() {
    	if (!parsed_)
    		parse();
    	
    	NumberFormat format = NumberFormat.getInstance();
    	format.setMaximumFractionDigits(8);
    	format.setMinimumFractionDigits(0);
    	format.setGroupingUsed(false);
    	
    	StringBuffer retval = new StringBuffer();
    	retval.append(format.format(channel_)).append(',');
    	retval.append(format.format(mode_)).append(',');
    	retval.append(format.format(transducerDepth_)).append(',');
    	retval.append(format.format(frequency_)).append(',') ;
    	retval.append(format.format(transmitPower_)).append(',');
    	retval.append(format.format(pulseLength_)).append(',');
    	retval.append(format.format(bandWidth_)).append(',');
    	retval.append(format.format(sampleInterval_)).append(',');
    	retval.append(format.format(soundVelocity_)).append(',') ;
    	retval.append(format.format(absorptionCoefficient_)).append(',');
    	retval.append(format.format(heave_)).append(',');
    	retval.append(format.format(roll_)).append(',');
    	retval.append(format.format(pitch_)).append(',') ;
    	retval.append(format.format(temperature_)).append(',');
    	retval.append(format.format(trawlUpperDepthValid_)).append(',');
    	retval.append(format.format(trawlOpeningValid_)).append(',');
    	retval.append(format.format(trawlUpperDepth_)).append(',');
    	retval.append(format.format(trawlOpening_)).append(',');
    	retval.append(format.format(offset_)).append(',');
    	retval.append(format.format(count_)).append(',');

	   return retval.toString();
    }
    
    /* ---------- Inner Classes ---------- */

    /* ----- Class InsufficientSamplesException ----- */
    /**
     *  InsufficientSamplesException is throw if data is requested of
     *  a range of samples, but that many samples do not exist in this record.
     **/

    public class InsufficientSamplesException extends ArrayIndexOutOfBoundsException {
    }

    /* ----- Class Display ----- */

   /**
      ES60RAW.Display is a JComponent for displaying a RAW telegram.
<p>
      The Display is a basic tool to allow a user to view the details of the 
      record. 

      This class has not yet been fully supported.

      @author Gordon Keith
    
   **/

   public static class Display extends JComponent {

      /* ---------- Display Protected Members ---------- */


      /**
       *  Record currently displayed.
       **/
      protected ES60RAW current_;

     /* ----- Widgets ----- */

      protected JTextField surveyField_;

      protected JTextField esFileField_;

      protected JTextField timeField_;


      /* ---------- Display Constructors ---------- */

      /**
       *  Create the display.
       **/
      public Display() {

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
	 
	 // Layout

	 JPanel dataLabelPane = new JPanel(new GridLayout(0,1));
	 JPanel dataValuePane = new JPanel(new GridLayout(0,1));

	 dataLabelPane.add(new JLabel("Survey: "));
	 dataValuePane.add(surveyField_);
	 
	 dataLabelPane.add(new JLabel("File: "));
	 dataValuePane.add(esFileField_);
	 
	 dataLabelPane.add(new JLabel("Time: "));
	 dataValuePane.add(timeField_);
	 

	 dataLabelPane.add(new JLabel(" "));

	 setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
	 add(dataLabelPane);
	 add(dataValuePane);
      }

      /**
       *  Display the specified record.
       *  Fields are set to blank if the record is null.
       *  @param raw ES60RAW to display.
       **/
      public void display(ES60RAW raw) {
	 current_ = raw;

	 if (raw != null) {
	    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    dateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));

	    if (!raw.parsed_)
		raw.parse();

	    if (raw.esFile_ == null) {
	       surveyField_.setText(" ");
	       esFileField_.setText(" ");
	    } else {
	       surveyField_.setText(raw.esFile_.getSurvey());
	       esFileField_.setText(raw.esFile_.toString());
	    }

	    timeField_.setText(dateFormat.format(raw.getTime()));
	 } else {  // raw == null
	    surveyField_.setText(" ");
	    esFileField_.setText(" ");
	    timeField_.setText(" ");
	 }
      }
   }
}

/*
    Philip said to him, "Lord, show us the Father;
    that is all we need."

    Jesus answered, "For a long time I have been with you all;
    yet you do not know me, Philip?
    Whoever has seen me has seen the Father. 
    Why, then, do you say, 'Show us the Father'?
    Do you not believe, Philip, that I am in the Father and
    the Father is in me?
    	John 14:8 - 10a
*/
