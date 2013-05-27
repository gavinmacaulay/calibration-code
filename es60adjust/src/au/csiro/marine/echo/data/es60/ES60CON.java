/*
 ES60CON.java  au.csiro.marine.echo.data.ES60CON
 
 Copyright 2005, CSIRO Marine Research.
 All rights reserved.
 Released under the GPL and possibly other licenses.
 
 $Id: ES60CON.java 386 2010-12-22 04:59:57Z kei037 $
 
 $Log: ES60CON.java,v $
 Revision 1.1  2006/04/12 06:11:22  kei037
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

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
	ES60CON contains a Simrad ES60 or EK60 configuration data record (CON0 datagram).
 
 	Each .raw file should start with a CON telegram and only contain the one CON telegram.
 
 	<pre>
 	struct ConfigurationDatagram {
 		DatagramHeader DgHeader; // "CON0"
 		ConfigurationHeader ConfigHeader;
 		ConfigurationTransducer Transducer[];
 		};	
    struct ConfigurationHeader {
    		char SurveyName[128]; // "Loch Ness"
    		char TransectName[128]; // "L0123"
    		char SounderName[128]; // "EK60"
    		char Spare[128]; // future use
    		long TransducerCount; // 1,2,3,4
    		};
     struct ConfigurationTransducer {
     	char ChanelID[128]; // Channel identification					  0
     	long BeamType; // 0=SINGLE, 1=SPLIT								128
     	float Frequency; // [Hz]											132	
     	float Gain; // [dB] - See note below								136
     	float EquivalentBeamAngle; // [dB]								140
     	float BeamWidthAlongship; // [degree]							144
     	float BeamWidthAthwartship; // [degree]							148
     	float AngleSensitivityAlongship;									152
     	float AngleSensitivityAthwartship;								156
     	float AngleOffsetAlongship; // [degree]							160
     	float AngleOffsetAthwartship; // [degree]						164
     	float PosX; // future use										168
     	float PosY; // future use										172
     	float PosZ; // future use										176
     	float DirX; // future use										180
     	float DirY; // future use										184
     	float DirZ; // future use										188
     	float PulseLengthTable[5]; // Available pulse lengths for the channel [s]	192
     	char Spare2[8]; // future use									212
     	float GainTable[5]; // Gain for each pulse length in the PulseLengthTable [dB]	220
     	char Spare3[8]; // future use									240
     	float SaCorrectionTable[5]; // Sa correction for each pulse length in the PulseLengthTable [dB]	248
     	char Spare4[52]; // future use									268
     	};
 
    Gain: - the single Gain parameter was used actively in raw data files generated with
    software version 1.3. This was before PulseTableLength, GainTable and SaCorrectionTable
    were introduced in software version 1.4 to enable gain and Sa correction parameters
    for each pulse length.
    </pre>
 
 	@version $Id: ES60CON.java 386 2010-12-22 04:59:57Z kei037 $
 	@author Gordon Keith
 **/
public class ES60CON extends ES60Record {
	
	/* ---------- Constants ---------- */
	
	/**
	 *  length of the header block prior to data arrays.
	 **/
	public static final int HEADER = 516;
	
	/**
	 *  length of the transducer structure
	 */
	public static final int CONFIG = 320;
	
	/* ---------- Protected Members ---------- */
	
	/**
	 *  Has this record been parsed?
	 *  Parsing may be done lazily, 
	 *  so that only datagrams of interest get parsed.
	 **/
	protected boolean parsed_ = false;
	
	/**
	 *  Survey name
	 */   
	protected String survey_;
	
	/**
	 *  Transect name
	 */
	protected String transect_;
	
	/**
	 *  Sounder Name
	 */
	protected String sounder_;
	
	/**
	 *  Transducer count
	 */
	protected int transducers_;
	
	/**
	 *  Chanel identification
	 */
	protected String[] channel_;
	
	/**
	 *  Beam type, 0 = single, 1 = split
	 */
	protected int[] type_;
	
	/**
	 *  Frequency
	 */
	protected float[] frequency_;
	
	/**
	 *  
	 */
	protected float[] equivalentBeamAngle_;
	
	/**
	 *  
	 */
	protected float[] beamWidthAlongship_;
	
	/**
	 *  
	 */
	protected float[] beamWidthAthwartship_;
	
	/**
	 *  Available pulse lengths for the channel [s]
	 */
	float[][] pulseLength_;
	
	/**
	 *  Gain for each pulse length in the PulseLengthTable [dB]
	 */
 	float[][] gain_; 
 	
 	/**
 	 *  Sa correction for each pulse length in the PulseLengthTable [dB]
 	 */
  	float[][] saCorrection_;
  	
	/* ---------- Factory Methods ---------- */
	
	/**
	 *  Read an ES60CON record from the DataInput.
	 *  Parsing of the record contents is not performed at this stage.
	 *
	 *  @param in   DataInput to read record from.
	 *  @param len  Number of bytes from DataInput that constitute the record.
	 *  @param swap Is byteswapping required?
	 *  @return An ES60CON record read from in. 
	 **/
	public static ES60Record read(DataInput in, int len, boolean swap)
	throws IOException {
		ES60CON retval = new ES60CON();
		retval.swap_ = swap;
		retval.data_ = new byte[len];
		in.readFully(retval.data_);
		return retval;
	}
	
	/* ---------- Public Methods ---------- */
	
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
		
		/* parse header */
		survey_ = decodeString(0, 128);
		transect_ = decodeString(128, 128);
		sounder_ = decodeString(256, 128);
		transducers_ = decodeLong(512);
		
		/* Check record length and output diagnostics if not correct */
		if (data_.length != HEADER + transducers_ * CONFIG) {
			System.out.println("Bad record length in CON0, expected: " +
					(HEADER + transducers_ * CONFIG) + 
					" got: " + data_.length);
			return;
		}
		channel_ = new String[transducers_];
		type_ = new int[transducers_];
		frequency_ = new float[transducers_];
		equivalentBeamAngle_ = new float[transducers_];
		beamWidthAlongship_ = new float[transducers_];
		beamWidthAthwartship_ = new float[transducers_];
		pulseLength_ = new float[transducers_][5];
		gain_ = new float[transducers_][5];
		saCorrection_ = new float[transducers_][5];
		
		for (int i = 0; i < transducers_; i++) {
			int start = HEADER + i * CONFIG;
			channel_[i] = decodeString(start, 128);
			type_[i] = decodeLong(start + 128);
			frequency_[i] = decodeFloat(start + 132);
			equivalentBeamAngle_[i] = decodeFloat(start + 140);
			beamWidthAlongship_[i] = decodeFloat(start + 144);
			beamWidthAthwartship_[i] = decodeFloat(start + 148);
			for (int j = 0; j < 5; j++) {
				pulseLength_[i][j] = decodeFloat(start + 192 + 4 * j);
				gain_[i][j] = decodeFloat(start + 220 + 4 * j);
				saCorrection_[i][j] = decodeFloat(start + 248 + 4 * j);
			}
		}
		
		parsed_ = true;
	}
	
	/**
	 * The number of transducers (channels)
	 * @return Number of transducers
	 */
	public int getTransducerCount() {
		if (!parsed_)
			transducers_ = decodeLong(512);			
		return transducers_;
	}
	
	/**
	 *  Return list of channels
	 */
	public String[] getChannels() {
		if (!parsed_)
			parse();
		return channel_;
	}
	
	/**
	 *  Return average beamwidth for transducer.
	 */
	public double getBeamWidth(int channel) {
		return (beamWidthAlongship_[channel - 1] + beamWidthAthwartship_[channel - 1]) / 2.0;
	}
	
	public float getGain(int channel, float pulselength) {
		if (!parsed_)
			parse();
		for (int i = 0; i < 5; i++)
			if (pulseLength_[channel - 1][i] == pulselength)
				return gain_[channel -1][i];
		return Float.NaN;
	}
	
	public float getSaCorrection(int channel, float pulselength) {
		if (!parsed_)
			parse();
		for (int i = 0; i < 5; i++)
			if (pulseLength_[channel - 1][i] == pulselength)
				return saCorrection_[channel -1][i];
		return Float.NaN;
	}
	
	public float getBeamAngle(int channel) {
		if (!parsed_)
			parse();
		return equivalentBeamAngle_[channel -1];
	}
	
	public String toString() {
		if (!parsed_)
			parse();
		
		NumberFormat format = NumberFormat.getInstance();
		format.setMaximumFractionDigits(8);
		format.setMinimumFractionDigits(0);
		format.setGroupingUsed(false);
		
		StringBuffer retval = new StringBuffer();
		retval.append(format.format(transducers_)).append(',');
		for (int i = 0; i < transducers_; i++) {
			retval.append(channel_[i]).append(',');
			retval.append(format.format(type_[i])).append(',');
			retval.append(format.format(frequency_[i])).append(',');
			retval.append(format.format(equivalentBeamAngle_[i])).append(',');
			retval.append(format.format(beamWidthAlongship_[i])).append(',');
			retval.append(format.format(beamWidthAthwartship_[i])).append(',');
			for (int j = 0; j < 5; j++) {
				retval.append(format.format(pulseLength_[i][j])).append(',');
				retval.append(format.format(gain_[i][j])).append(',');
				retval.append(format.format(saCorrection_[i][j])).append(',');
			}
		}	
		retval.append(survey_).append(',');
		retval.append(transect_).append(',');
		retval.append(sounder_);
		return retval.toString();
	}

	/* ----- Class Display ----- */
	

	/**
	 ES60CON.Display is a JComponent for displaying a CON telegram.
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
		protected ES60CON current_;
		
		/* ----- Widgets ----- */
		
		protected JTextField surveyField_;
		
		protected JTextField transectField_;
		
		protected JTextField sounderField_;
		
		
		/* ---------- Display Constructors ---------- */
		
		/**
		 *  Create the display.
		 **/
		public Display() {
			
			/* ----- Widgets ----- */
			
			surveyField_ = new JTextField(" ");
			surveyField_.setEditable(false);
			surveyField_.setToolTipText("Survey");
			
			transectField_ = new JTextField(" ");
			transectField_.setEditable(false);
			transectField_.setToolTipText("Transect");
			
			sounderField_ = new JTextField(" ");
			sounderField_.setEditable(false);
			sounderField_.setToolTipText("Sounder");
			
			// Layout
			
			JPanel dataLabelPane = new JPanel(new GridLayout(0,1));
			JPanel dataValuePane = new JPanel(new GridLayout(0,1));
			
			dataLabelPane.add(new JLabel("Survey: "));
			dataValuePane.add(surveyField_);
			
			dataLabelPane.add(new JLabel("Transect: "));
			dataValuePane.add(transectField_);
			
			dataLabelPane.add(new JLabel("Sounder: "));
			dataValuePane.add(sounderField_);
			
			
			dataLabelPane.add(new JLabel(" "));
			
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			add(dataLabelPane);
			add(dataValuePane);
		}
		
		/**
		 *  Display the specified record.
		 *  Fields are set to blank if the record is null.
		 *  @param raw ES60CON to display.
		 **/
		public void display(ES60CON raw) {
			current_ = raw;
			
			if (raw != null) {
				
				if (!raw.parsed_)
					raw.parse();
				
				surveyField_.setText(raw.survey_);
				transectField_.setText(raw.transect_);
				
				sounderField_.setText(raw.sounder_);
			} else {  // raw == null
				surveyField_.setText(" ");
				transectField_.setText(" ");
				sounderField_.setText(" ");
			}
		}
	}
}

/*
 */
