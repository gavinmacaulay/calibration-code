/*
    ES60Adjust.java  au.csiro.marine.echo.ES60Adjust

    Copyright 2005, CSIRO Marine Research.
    All rights reserved.
    Released under the GPL and possibly other licenses.

    $Id: ES60Adjust.java 217 2009-08-26 06:17:06Z kei037 $

    $Log: ES60Adjust.java,v $
    Revision 1.7  2006/02/28 00:07:22  kei037
    Cosmetic changes associated with move to eclipse.

    Revision 1.6  2005/09/06 05:28:49  kei037
    Fixed incorrect detection of no turning point

    Revision 1.5  2005/09/06 01:28:03  kei037
    Minor corrections. "Not turning point" -> "No turning point"

    Revision 1.4  2005/08/31 00:17:08  kei037
    Changed wave from 2720 pings to 2721.
    Added splash screen.

    Revision 1.3  2005/05/31 05:51:59  kei037
    ES60Adjust working with code to find triangle wave in data.

    Revision 1.2  2005/05/12 06:42:54  kei037
    Working version of ES60Adjust program to remove triangular wave errors.

    Revision 1.1  2005/05/05 05:46:07  kei037
    Added ES60Adjust to remove ES60 triangular error.


*/

package au.csiro.marine.echo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.SimpleTimeZone;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import au.csiro.marine.echo.data.es60.ES60File;
import au.csiro.marine.echo.data.es60.ES60NMEA;
import au.csiro.marine.echo.data.es60.ES60RAW;
import au.csiro.marine.echo.data.es60.ES60Record;

/**
    ES60Adjust is a program to 
    remove the triangle error Simrad deliberately introduces to 
    the ES60 data.
<p>
    If run without command line parameters it runs as a GUI.
    If run with one command line parameter it prints a usage message.
    If run with two or more command line parameters, 
    the first is interpretted as the initial ping number and 
    subsequent parameters as file names.

    @version $Id: ES60Adjust.java 217 2009-08-26 06:17:06Z kei037 $
    @author Gordon Keith
**/
public class ES60Adjust extends JComponent {
    
	/* ---------- Constants ---------- */

	/**
	 *  Length of triangle wave.
	 **/
	public static final int WAVE = 2721;

	/**
	 *  Message displayed in About screen.
	 **/
	public static final String ABOUT = "ES60Adjust \n\n" +
	"Program to correct triangle wave error in ES60 echo sounders.\n\n" +
	"Copyright 2005 CSIRO Marine and Atmospheric Research.\n" +
	"All rights reserved.\n\n" +
	"Software is released under the GNU General Public Licence.\n" +
	"See http://www.fsf.org/licensing/licenses/gpl.txt\n" +
	"The software is available upon request from\n" +
	"CSIRO Marine and Atmospheric Research.\n\n" +
	"Under the gpl licence the software can be freely distributed\n" +
	"but it would be appreciated if CSIRO is notified if the software is\n" +
	"passed on to third parties.\n" +
	"Contact either tim.ryan@csiro.au or gordon.keith@csiro.au .\n\n" +
	"Users should satisfy themselves that the software is working as\n" +
	"they expect; CSIRO accepts no responsibility for errors\n" +
	"in the software or in any subsequent results.\n\n" +
	"Appropriate acknowledgements should be given in publications that\n" +
	"used this application to assist in the production of results.\n" +
	"Suitable references would be:\n" +
	"Ryan, T.E., Kloser, R.J. (2004) Quantification and correction of a\n" +
	"systematic error in Simrad ES60 Echosounders. ICES FAST, Gdansk.\n" + 
	"Copy available from CSIRO Marine and Atmospheric Research.\n" + 
	"GPO Box 1538, Hobart, Australia\n\n" +
	"Keith, G.J., Ryan, T.E., Kloser, R.J. 2005. ES60adjust.jar.\n" +
	"Java software utility to remove a systematic error in Simrad ES60 data.\n" +
	"CSIRO Marine and Atmospheric Research. Castray Esplanade, Hobart 7001,\n" +
	"Tasmania, Australia. \n\n" +
	"$Id: ES60Adjust.java 217 2009-08-26 06:17:06Z kei037 $\n\n" +
	"Author: Gordon Keith\n\n" +

	"";

	/**
	 *  ClassLoader used to get icon resources.
	 **/
	protected static ClassLoader cl__ = ES60Adjust.class.getClassLoader();

	/* ---------- Protected Static Members ---------- */

	/**
	 *  Current GUI, used for error and status messages during processing.
	 *  If null messages are output to System.err and System.out only.
	 **/
	protected static Progress gui__;

	/* ---------- Protected Members ---------- */

	/**
	 *  List of files currently included for processing.
	 **/
	protected DefaultListModel fileList_;

	/**
	 *  Progress status dialog.
	 **/
	protected Progress progress_;

	/**
	 *  Last directory used to select files.
	 **/
	protected File curr_ = new File(".");

	/* ----- Widgets ----- */

	/**
	 *  List of files for processing.
	 **/
	protected JList inFileList_;

	/**
	 *  Button to remove selected files from list.
	 **/
	protected JButton remove_;

	/**
	 *  Button to move selected files up in list.
	 **/
	protected JButton up_;

	/**
	 *  Button to move selected files down in list.
	 **/
	protected JButton down_;

	/**
	 *  Spinner to allow entry of initial ping number.
	 **/
	protected JSpinner pingNumber_;

	/**
	 *  Button to initiate scanning of files for initial ping number.
	 **/
	protected JButton scan_;

	/**
	 *  Field for entry of output directory.
	 **/
	protected JTextField outDir_;

	/**
	 *  Field for entry of filename suffix.
	 **/
	protected JTextField outAffix_;

	/**
	 *  Button to start processing.
	 **/
	protected JButton run_;

	/**
	 *  Button to exit the program.
	 **/
	protected JButton quit_;

	/* ---------- Constructors ---------- */

	/**
	 *  Creates an ES60Adjust which is a GUI for the process method.
	 *  Process can be invoked from the command line directly so does not require a GUI,
	 *  so creating a ES60Adjust object is optional.
	 **/
	public ES60Adjust() {
		/*- Input details -*/
		/* Build file list */
		fileList_ = new DefaultListModel();

		inFileList_ = new JList(fileList_);
		inFileList_.setToolTipText("List of raw files to process in order. Ping number continues from file to file.");
		inFileList_.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				selectionChanged(); // Enable/disable buttons if some files are selected
			}
		});

		/* button to add files to file list */
		JButton browse = new JButton("Add...");
		browse.setToolTipText("Add ES60 .raw files to process");
		browse.setMnemonic('A');
		browse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				addFiles();
			}
		});

		/* button to remove selected files from the list. */
		remove_ = new JButton("Remove");
		remove_.setToolTipText("Remove selected files");
		remove_.setMnemonic('V');
		remove_.setEnabled(false);
		remove_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				removeFiles();
			}
		});

		/* button to shift selected files up the file list */
		up_ = new JButton("Up");
		up_.setToolTipText("Move selected files higher up in processing order");
		up_.setMnemonic('U');
		up_.setEnabled(false);
		up_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				upFiles();
			}
		});

		/* button to shift selected files down the file list */
		down_ = new JButton("Down");
		down_.setToolTipText("Move selected files lower down in processing order");
		down_.setMnemonic('D');
		down_.setEnabled(false);
		down_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				downFiles();
			}
		});

		/* Button to select all */
		JButton selectAll = new JButton("Select All");
		selectAll.setToolTipText("Select all files");
		selectAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				inFileList_.setSelectionInterval(0, fileList_.getSize() - 1);
			}
		});

		/* button to scan data files for initial ping no. */
		scan_ = new JButton("Info");
		scan_.setToolTipText("Scan files to get file statistics including guess of first ping number");
		scan_.setMnemonic('I');
		scan_.setEnabled(false);
		scan_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				scan(ae.getModifiers());
			}
		});

		/* pane containing list manipulation buttons */
		JComponent buttonPane = new JPanel(new GridLayout(0, 1, 10, 5));
		buttonPane.add(browse);
		buttonPane.add(remove_);
		buttonPane.add(up_);
		buttonPane.add(down_);
		buttonPane.add(selectAll);
		buttonPane.add(scan_);
		JComponent buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(buttonPane, BorderLayout.NORTH);

		/* spinner to enter ping number of file */
		pingNumber_ = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
		pingNumber_.setToolTipText("Ping number of first ping of first file in wave sequence, should be between 0 and " + WAVE);

		/* attach Mnemonic P to pingNumber_ */
		JLabel pingLabel = new JLabel("");
		pingLabel.setDisplayedMnemonic('P');
		pingLabel.setLabelFor(pingNumber_);

		/* pane containing ping number */
		JComponent pingPane = new JPanel(new FlowLayout(FlowLayout.LEADING));
		pingPane.add(pingNumber_);
		pingPane.add(pingLabel);
		pingPane.setBorder(new TitledBorder("First ping number"));

		/* Layout input fields */
		JScrollPane inScroll = new JScrollPane(inFileList_);
		inScroll.setPreferredSize(new Dimension(600, 400));

		JComponent inPanel = new JPanel(new BorderLayout());
		inPanel.setBorder(new TitledBorder("Input"));
		inPanel.add(inScroll, BorderLayout.CENTER);
		inPanel.add(pingPane, BorderLayout.SOUTH);
		inPanel.add(buttonPanel, BorderLayout.EAST);

		/*- Output details -*/
		/* output directory */
		String defaultDir = ".";
		try {
			defaultDir = new File(defaultDir).getCanonicalPath();
		} catch (IOException ioe) {}
		outDir_ = new JTextField(defaultDir);
		outDir_.setToolTipText("Directory to write processed files to. May be the same directory as input files if affix is not blank.");	
		JButton browseOut = new JButton("Browse");
		browseOut.setToolTipText("Select output directory from a file browser.");
		browseOut.setMnemonic('B');
		browseOut.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				browseOutDir();
			}
		});

		/* attach Mnemonic 'O' to outDir_ */
		JLabel outDirLabel = new JLabel("");
		outDirLabel.setDisplayedMnemonic('O');
		outDirLabel.setLabelFor(outDir_);

		/* affix to add to output filename before .raw extension */
		outAffix_ = new JTextField("c");
		outAffix_.setToolTipText("Append to filename prior to .raw extension to distinguish input and output files. May be blank if in and out directories differ.");

		/* attach Mnemonic 'F' to outAffix_ */
		JLabel outAffixLabel = new JLabel("Append to filename: ");
		outAffixLabel.setDisplayedMnemonic('F');
		outAffixLabel.setLabelFor(outAffix_);

		/* Layout output fields */
		JComponent dirPane = new Box(BoxLayout.X_AXIS);
		dirPane.add(outDirLabel);
		dirPane.add(outDir_);
		dirPane.add(browseOut);

		JComponent affixPane =  new Box(BoxLayout.X_AXIS);
		affixPane.add(outAffixLabel);
		affixPane.add(outAffix_);

		JComponent outPanel = new JPanel(new GridLayout(2,1));
		outPanel.setBorder(new TitledBorder("Output to"));
		outPanel.add(dirPane);
		outPanel.add(affixPane);

		/*- Program control -*/
		/* button to start processing */
		run_ = new JButton("Run");
		run_.setToolTipText("Apply triangle wave correction to the files listed above");
		run_.setMnemonic('R');
		run_.setEnabled(false);
		run_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				go();
			}
		});

		/* button to exit the program */
		quit_ = new JButton("Quit");
		quit_.setToolTipText("Exit the program without any more processing");
		quit_.setMnemonic('Q');
		quit_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				System.exit(0);
			}
		});

		/* button for info about program */
		JButton about = new JButton("About");
		about.setToolTipText("Author and Copyright details");
		about.setMnemonic('T');
		about.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				helpAbout();
			}
		});

		/* button for help on program usage */
		JButton help = new JButton("Help");
		help.setToolTipText("Basic instructions on program usage");
		help.setMnemonic('H');
		help.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				help();
			}
		});

		/* Layout program control buttons */
		JComponent goPanel = new JPanel();
		goPanel.add(run_);
		goPanel.add(quit_);
		goPanel.add(about);
		goPanel.add(help);

		/* Layout panels */
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(inPanel);
		add(outPanel);
		add(goPanel);
	}

	/* ---------- Protected Methods ---------- */

	/**
	 *  Enables list manipulation buttons if any list elements are currently selected.
	 **/
	protected void selectionChanged() {
		boolean select = !(inFileList_.getSelectedValue() == null);
		remove_.setEnabled(select);
		up_.setEnabled(select);
		down_.setEnabled(select);
	}

	/**
	 *  Provide the user with a file chooser for .raw files only to add to the list of files.
	 **/
	protected void addFiles() {
		JFileChooser chooser = new JFileChooser(curr_);
		chooser.setMultiSelectionEnabled(true);
		chooser.addChoosableFileFilter(new javax.swing.filechooser.FileFilter() {
			public String getDescription() {
				return "ES60 raw files";
			}
			public boolean accept(File f) {
				return f.isDirectory() ||
				f.getName().toLowerCase().endsWith(".raw");
			}
		});
		chooser.setAcceptAllFileFilterUsed(false);

		int returnVal = chooser.showOpenDialog(this);
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			/* Java 5 specific code
	    List <File> files = Arrays.asList(chooser.getSelectedFiles());
	    Collections.sort(files);
	    for (File file : files)
		fileList_.addElement(file);
			 */
			File[] files = chooser.getSelectedFiles();
			for (int i = 0; i < files.length; i++)
				fileList_.addElement(files[i]);

			curr_ = files[files.length - 1];
			outDir_.setText(files[0].getParentFile().getPath());
			boolean haveFiles = fileList_.getSize() > 0;
			run_.setEnabled(haveFiles);
			scan_.setEnabled(haveFiles);
		}
	}

	/**
	 *  Remove the currently selected files from the list of files.
	 **/
	protected void removeFiles() {
		int[] remove = inFileList_.getSelectedIndices();
		for (int i = remove.length; i > 0; i--) 
			fileList_.removeElementAt(remove[i - 1]);

		boolean haveFiles = fileList_.getSize() > 0;
		run_.setEnabled(haveFiles);
		scan_.setEnabled(haveFiles);
	}

	/**
	 *  Move the currently selected files one up in the sequence of files.
	 *  Keep these files selected.
	 **/
	protected void upFiles() {
		int[] move = inFileList_.getSelectedIndices();
		Object[] files = inFileList_.getSelectedValues();
		for (int i = 0; i < move.length; i++) 
			if (move[i] > 0) {
				fileList_.removeElementAt(move[i]--);
				fileList_.insertElementAt(files[i], move[i]);
			}
		inFileList_.setSelectedIndices(move);
	}

	/**
	 *  Move the currently selected files one down in the sequence of files.
	 *  Keep these files selected.
	 **/
	protected void downFiles() {
		int[] move = inFileList_.getSelectedIndices();
		Object[] files = inFileList_.getSelectedValues();
		for (int i = move.length - 1 ; i >= 0; i--) {
			fileList_.removeElementAt(move[i]++);
			fileList_.insertElementAt(files[i], move[i]);
		}
		inFileList_.setSelectedIndices(move);
	}

	/**
	 *  Scan files to guess first ping number.
	 **/
	public void scan(int modifiers) {
		scan_.setEnabled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		final boolean getStats = 0 == (modifiers & ActionEvent.SHIFT_MASK);
		final Object[] files = fileList_.toArray();
		new Thread(new Runnable() {
			public void run() {
				Analyse a = null;
				for (int i = files.length; i > 0; ) {
					a = new Analyse(files[--i], a);
					if (!getStats)
						a.haveStats_ = true;
				}
				a.analyse();

				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						boolean haveFiles = fileList_.getSize() > 0;
						scan_.setEnabled(haveFiles);
						setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
					}
				});
			}
		}).start();
	}

	/**
	 *  Provide the user with a file chooser to select the output directory.
	 **/
	protected void browseOutDir() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal = chooser.showOpenDialog(this);
		if(returnVal == JFileChooser.APPROVE_OPTION) 
			outDir_.setText(chooser.getSelectedFile().getPath());
	}

	/**
	 *  Run the processing using the current GUI settings.
	 **/
	protected void go() {
		/* file list */
		Object[] obj = fileList_.toArray();
		final File[] files = new File[obj.length];
		for (int i=0; i < obj.length; i++)
			files[i] = (File)obj[i];

		/* initial ping number */
		final int ping = ((Number)pingNumber_.getValue()).intValue();

		/* output directory */
		String outDir = outDir_.getText();
		File out = new File(outDir);
		if (!out.isDirectory()) {
			if (outDir.trim().equals(""))
				out = new File(".");
			else {
				if (JOptionPane.OK_OPTION !=
					JOptionPane.showConfirmDialog(this,
							"Directory " + outDir + " does not exist!\nCreate it?", 
							"Directory not found",
							JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.WARNING_MESSAGE))
					return;
				if (!out.mkdirs()) {
					JOptionPane.showMessageDialog(this,
							"Could not create directory " + outDir, 
							"Directory not found",
							JOptionPane.ERROR_MESSAGE);
					return;
				}
			}
		}

		final File dir = out;

		/* filename affix */
		final String affix = outAffix_.getText();

		/* create processing thread */
		Thread thread = new Thread(new Runnable() {
			public void run() {
				process(ping, files, dir, affix);
			}
		});

		/* assign processing thread to a progress dialog and start processing */
		if (progress_ == null) 
			gui__ = progress_ = new Progress();
		progress_.setThread(thread);
	}

	/**
	 *  Display about information in a frame.
	 **/
	protected void helpAbout() {
		URL icon = cl__.getResource("images/csiro.png");
		if (null == icon)
			icon = cl__.getResource("csiro.png");

		Icon csiro = null;
		if (icon != null)
			csiro = new ImageIcon(icon);
		JOptionPane.showMessageDialog(this, ABOUT, "CSIRO Marine and Atmospheric Research",
				JOptionPane.INFORMATION_MESSAGE, csiro);
	}

	/**
	 *  Display help information
	 */
	protected void help() {
		JEditorPane info = new JEditorPane();
		info.setEditable(false);
		URL page = Thread.currentThread().getContextClassLoader().getResource("es60adjust.html");
		if (page == null) {
			info.setText("Could not find help file." );
		} else {
			try {
				info.setPage(page);
			} catch (IOException ioe) {
				ioe.printStackTrace();
				info.setText("Could not find " + page + " : " + ioe);
			}
		}

		JFrame frame = new JFrame();
		frame.setTitle("ES60 Adjust - Help");

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.getContentPane().add(new JScrollPane(info));
		frame.pack();
		frame.setSize(450, 600);
		frame.setVisible(true);
	}

	/* ---------- Inner Classes ---------- */

	/* ----- Class Progress ----- */

	/**
	 *  Progress is a Dialog which provides the user with feedback on the current
	 *  state of processing.
	 *  If modal (current state) processing must be run sequentially and a single
	 *  Progress object is used for all runs.
	 *  If not modal (change super call in constructor) the one Progress is created
	 *  for each concurrent processing thread. Progess's for finished threads will be 
	 *  re-used.
	 **/

	protected class Progress extends JDialog {

		/* ----- Progress Members ----- */
		/**
		 *  Current processing thread.
		 **/
		Thread thread_;

		/**
		 *  Linked list of Progress object supporting concurrent processing threads.
		 **/
		Progress next_;

		/* --- Widgets --- */

		/**
		 *  List of completed files.
		 **/
		JTextArea done_;

		/**
		 *  Current progress status message.
		 **/
		JTextField current_;

		/**
		 *  Percent complete of current file.
		 **/
		JProgressBar pctFile_;

		/**
		 *  Percent complete of total.
		 **/
		JProgressBar pctTotal_;

		/**
		 *  Button to interrupt current processing.
		 **/
		JButton cancel_;

		/**
		 *  Button to dismiss Progress dialog when processing complete.
		 **/
		JButton ok_;

		/* ----- Progress Constructors ----- */

		/**
		 *  Creates a Progress dialog.
		 **/
		Progress() {
			super((JFrame)ES60Adjust.this.getRootPane().getParent(), 
					"Processing progress", true);

			/* list of completed files */
			int size = fileList_.getSize() + 5;
			done_ = new JTextArea("", size, 80);
			done_.setEditable(false);

			/* current progress status message */
			current_ = new JTextField("");
			current_.setEditable(false);

			/* button to interrupt processing */
			cancel_ = new JButton("Cancel");
			cancel_.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					if (thread_ != null)
						thread_.interrupt();
					setVisible(false);
				}
			});

			/* progress bars */
			pctFile_ = new JProgressBar(0,100);
			pctTotal_ = new JProgressBar(0,100);
			pctFile_.setStringPainted(true);
			pctTotal_.setStringPainted(true);

			/* button to hide dialog */
			ok_ = new JButton("OK");
			ok_.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					setVisible(false);
				}
			});
			ok_.setEnabled(false); // only enabled when processing complete

			/* Put done_ in a scroll pane big enough to hold it */
			JScrollPane scroll = new JScrollPane(done_);
			scroll.setPreferredSize(done_.getPreferredSize());

			/* Layout buttons */
			JComponent buttonPane = new JPanel();
			buttonPane.add(cancel_);
			buttonPane.add(ok_);

			/* main Layout */
			Container pane = new Box(BoxLayout.Y_AXIS);
			pane.add(scroll);
			pane.add(current_);
			pane.add(pctFile_);
			pane.add(pctTotal_);
			pane.add(buttonPane);
			setContentPane(pane);

		}

		/* ----- Progress Methods ----- */

		/**
		 *  Setup a Progress to handle this thread and start processing.
		 **/
		protected void setThread(Thread thread) {
			/* Can this Progress handle this Thread? */
			if (thread_ == null || !thread_.isAlive()) {
				thread_ = thread;

				/* set button state */
				cancel_.setEnabled(true);
				ok_.setEnabled(false);

				/* start processing */
				thread.start();
				quit_.setEnabled(false);

				/* display dialog (if modal this blocks this thread) */
				pack();
				setVisible(true);
			} else {
				/* let the next progress handle this Thread */
				if (next_ == null)
					next_ = new Progress();
				next_.setThread(thread);
			}
		}

		/**
		 *  Update progress status with new data.
		 *  @param message Message to display.
		 *  @param pctFile Percentage complete for the current file.
		 *  @param pctTotal Percentage complete for the current run.
		 *  @param finished Record this messages in the log of finished files?
		 **/
		protected  void update(final String message, 
				final long pctFile, final long pctTotal, 
				final boolean finished) {
			/* check if this is the right Progress for this Thread */
			if (thread_ != Thread.currentThread() && next_ != null) {
				next_.update(message, pctFile, pctTotal, finished);
				return;
			}

			/* update message */
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					if (finished) {
						done_.append(message + "\n");
						done_.setCaretPosition(done_.getText().length());
					}
					current_.setText(message);
					pctFile_.setValue((int)pctFile);
					pctTotal_.setValue((int)pctTotal);
				}
			});
		}

		/**
		 *  Processing is complete.
		 **/
		protected void done() {
			/* is this is the right Progress */
			if (thread_ == Thread.currentThread() || next_ == null) {
				/* set button state */
				cancel_.setEnabled(false);
				ok_.setEnabled(true);

				/* if no processes running enable the quit button */
				for (Progress p = gui__; p != null; p = p.next_)
					if (p != this && p.thread_.isAlive())
						return;
				quit_.setEnabled(true);

			} else
				next_.done();

		}
	}

	/* ----- Class Analyse ----- */

	/** 
	 *  Analyse is a class which provides some statistics on an
	 *  ES60File, in particular it tries to identify the start position of 
	 *  the ES60 triangular wave in the file.
	 **/
	protected class Analyse extends JDialog {

		/* ----- Analyse Constants ----- */

		/**
		 *  Maximum number of channels we can handle.
		 *  I haven't found this documented anywhere yet.
		 *  Must not be greater than the number of bits in channelmask
		 *  which is an int, ie 64 bits.
		 **/
		static final int MAX_CHANNELS = 64;

		/**
		 *  List of supported algorithms for weighting deviations.
		 **/
		final String[] ALGORITHMS = {"Linear", "Square", "Square root", "Log"};

		/* ----- Analyse Members ----- */

		/**
		 *  ES60File to analyse.
		 **/
		ES60File file_;

		/**
		 *  Next ES60File to include in analysis.
		 **/
		Analyse next_;

		/**
		 *  Configuration datagram.
		 **/
		/*# should be ES60CON when written */
		ES60Record con_;

		/**
		 *  Statistics for this file have been collected.
		 **/
		boolean haveStats_;

		/**
		 *  Highest channel number in this file.
		 **/
		int maxChannel_ = MAX_CHANNELS;

		/**
		 *  Number of pings per channel
		 **/
		int[] pings_;

		/**
		 *  Output deviations to System.out?
		 **/
		boolean output_;

		/* --- Widgets --- */

		JComponent channelPane_;
		JComponent filePane_;
		JSpinner first_;
		JSpinner last_;
		JSpinner avgWindow_;
		JSpinner window_;
		JSpinner search_;
		JSpinner skip_;
		JComboBox algorithm_;

		/* ----- Analyse Constructors ----- */

		/**
		 *  Creates this Analyse and starts analysis in another thread.
		 *  The Analyse will be displayed when the analysis has finished.
		 **/
		Analyse(Object o, Analyse next) {
			this((o instanceof File) ? new ES60File((File)o) : (ES60File)o , next);
		}

		Analyse(File file, Analyse next) {
			this(new ES60File(file), next);
		}

		Analyse(ES60File file, Analyse next) {
			super((JFrame)ES60Adjust.this.getRootPane().getParent(), 
					file.toString(), false);

			file_ = file;
			haveStats_ = false;
			next_ = next;

			JComponent titlePane = new  JPanel(new GridLayout(1,1));
			titlePane.add(new JLabel(file_.getFile().getPath()));

			filePane_ = new JPanel(new GridLayout(0,2));
			filePane_.add(new JLabel("Filesize"));
			long fileLength = file.getFile().length();
			NumberFormat lengthFormat = NumberFormat.getInstance();
			filePane_.add(new JLabel(lengthFormat.format(fileLength)));
			int mag = 0;
			while (fileLength > 10240) {
				fileLength /= 1024;
				mag++;
			}
			if (mag > 0) {
				filePane_.add(new JLabel(""));
				filePane_.add(new JLabel(lengthFormat.format(fileLength) + "BKMGT".substring(mag, mag + 1)));
			}

			if (next_ != null) {
				filePane_.add(new JLabel("Next file"));
				JButton nextButton = new JButton(next_.file_.toString());
				nextButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent ae) {
						next_.setVisible(true);
					}
				});
				filePane_.add(nextButton);
			}

			channelPane_ = new JPanel(new GridLayout(0,5));
			JLabel channelLabel = new JLabel("Channel");
			channelLabel.setToolTipText("Transducer channel number, tooltip gives search parameters");
			channelPane_.add(channelLabel);

			JLabel pingsLabel = new JLabel("Pings");
			pingsLabel.setToolTipText("Number of pings for this channel in this file");
			channelPane_.add(pingsLabel);

			JLabel initialLabel = new JLabel("Initial");
			initialLabel.setToolTipText("Ping number in triangle wave of the first ping of this file. Use as \"First ping number\" if this is the first file.");
			channelPane_.add(initialLabel);

			JLabel finalLabel = new JLabel("Final");
			finalLabel.setToolTipText("Ping number in triangle wave of the last ping in this file, compare to initial ping of next file.");
			channelPane_.add(finalLabel);

			channelPane_.add(new JLabel("click for plot"));

			first_ = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
			first_.setToolTipText("First sample in data to include in calculations for triangle wave detection");
			last_ = new JSpinner(new SpinnerNumberModel(4, 0, Integer.MAX_VALUE, 1));
			last_.setToolTipText("Last sample in data to include in calculations for triangle wave detection");
			avgWindow_ = new JSpinner(new SpinnerNumberModel(1, 0, WAVE, 1));
			avgWindow_.setToolTipText("Number of pings before and after the ping to include in calculation of weighted running average ping value");
			window_ = new JSpinner(new SpinnerNumberModel(5, 1, WAVE, 2));
			window_.setToolTipText("Number of candidate values to use when searching for best fit, must be odd");
			search_ = new JSpinner(new SpinnerNumberModel(WAVE, 1, Integer.MAX_VALUE, 1));
			search_.setToolTipText("Number of pings to analyse looking for triangle wave, should be greater than " +(WAVE / 2));
			skip_ = new JSpinner(new SpinnerNumberModel(10, 0, Integer.MAX_VALUE, 1));
			skip_.setToolTipText("Number of pings to skip before looking for triangle wave");

			algorithm_ = new JComboBox(ALGORITHMS);
			algorithm_.setToolTipText("Weighting to apply to deviations from predicted values");

			JButton run = new JButton("Search");
			run.setToolTipText("Search through file, using above settings, to detect triangle wave (Shift to output deviations)"); 
			run.setMnemonic('S');
			run.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					output_ = 0 != (ae.getModifiers() & ActionEvent.SHIFT_MASK);
					final int first = ((Number)first_.getValue()).intValue();
					final int last = ((Number)last_.getValue()).intValue();
					final int avgWindow = ((Number)avgWindow_.getValue()).intValue();
					final int window = ((Number)window_.getValue()).intValue();
					final int search = ((Number)search_.getValue()).intValue();
					final int skip = ((Number)skip_.getValue()).intValue();
					final int algorithm = algorithm_.getSelectedIndex();

					new Thread(new Runnable() {
						public void run() {
							analyse(first, last, avgWindow, window, search, skip, algorithm);
						}
					}).start();
				}
			});

			JComponent runPane = new JPanel(new GridLayout(0,2));
			runPane.add(new JLabel("First sample"));
			runPane.add(first_);
			runPane.add(new JLabel("Last sample"));
			runPane.add(last_);
			runPane.add(new JLabel("Average window"));
			runPane.add(avgWindow_);
			runPane.add(new JLabel("Detection window"));
			runPane.add(window_);
			runPane.add(new JLabel("Pings"));
			runPane.add(search_);
			runPane.add(new JLabel("Skip pings"));
			runPane.add(skip_);
			runPane.add(new JLabel("Algorithm"));
			runPane.add(algorithm_);
			runPane.add(new JLabel(""));
			JComponent runButtonPane = new JPanel(new FlowLayout());
			runButtonPane.add(run);
			runPane.add(runButtonPane);


			JComponent mainPane = new Box(BoxLayout.Y_AXIS);
			mainPane.add(titlePane);
			mainPane.add(filePane_);
			mainPane.add(new JScrollPane(channelPane_, 
					ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
					ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
			mainPane.add(runPane);
			setContentPane(mainPane);
			pack();
		}

		/* ----- Analyse Methods ----- */

		/* --- Interface Runnable --- */

		/**
		 *  Perform the analysis of the file,
		 *  place the results in the fields and
		 *  display.
		 **/
		public void analyse() {
			analyse(0, 4, 5, 1, WAVE, 10, 0);
		}

		/**
		 *  Perform the analysis of the file,
		 *  place the results in the fields and
		 *  display.
		 *
		 *  Basic algorithm:
		 *
		 *  The triangle wave is a fixed wave form 2721 pings long.
		 *  This method integrates the fire pulse for up to search pings.
		 *  This data is compared with the each of the possible 2721 
		 *  candidate triangle wave forms and the total deviation of the data
		 *  from each candidate is summed.
		 *  The candidate with the smallest total deviation from the actual data
		 *  is designated the best match.
		 *
		 *  Complications:
		 *
		 *  The first and last samples to include are provided as parameters,
		 *  the best results occur when these are a subset of the fire pulse.
		 *
		 *  To remove fire pulse ring down artifacts a running mean ping value can be used
		 *  instead of the simple ping value. The running mean is calculated as the weighted
		 *  average of the ping and avgWindow pings before and after the ping. 
		 *
		 *  To ensure the best match is not just an outlier, a window of candidates is summed
		 *  and the center candidate of the lowest scoring window is the best match.
		 *  
		 *  The number of pings to search is a parameter, this should be at least half a wavelength
		 *  for definite results, the longer the more accurate but the more processing resources required.
		 *
		 *  The initial <code>skip</code> pings can be excluded from the search.
		 *  This is because at power-on the first few pings (up to about 50?) can have low values due
		 *  to electronics warming up. This can affect the calculation.
		 *
		 *  A number of algorithms can be applied to each ping-candidate wave deviation before they are summed.
		 *  These algorithms may be more or less sensitive to outliers and random noise.
		 *  Algorithm 0 (Linear) sums the absolute value of the deviations.
		 *  Algorithm 1 (Square) sums the square of the deviations.
		 *  Algorithm 2 (Square root) sum the square roots of the absolute value of the deviations.
		 *  Algorithm 3 (Log) sums the natural log of 1 plus the absolute value of the deviation.
		 *
		 *  The data is also testing against the hypothesis that there is no triangle wave.
		 *  If the deviation from the mean is less than the deviation from any candiate wave
		 *  then it is reported that there is no wave found.
		 *
		 *  If there are less than half a wavelength of data included in the search the results
		 *  may not be conclusive. 
		 *  A wave can be found if a turning point can be identified in the data.
		 *  If a turning point is identified within 32 pings of either end of the data,
		 *  it is reported as being unreliable - although it may still be correct - the user should examine the data.
		 *  If no turning poing is identified in the data the range of possible values are reported.
		 *
		 *  A plot of the data and the best matching wave is provided to the user in both thumbnail size
		 *  and in a separate window (click on the thumbnail to get the window plot).
		 *
		 *  @param first First sample to include in search for trianlge wave, 
		 *               should be within fire pulse.
		 *  @param last Last sample to include in search for triable wave,
		 *               must be greater than first and should still be within fire pulse.
		 *  @param avgWindow number of pings either side of current ping to include in weighted average of ping value.
		 *  @param window Size of window to use when looking for best sample, must be odd.
		 *  @param search Maximum number of pings to include in calculation.
		 *  @param skip Number of intial pings to skip before starting calculation
		 *  @param algorithm Weighting of deviation, 0 = linear, 1 = squared, 2 = square root.
		 **/
		/*
		 *  This code assumes equal numbers of pings for each channel.
		 */
		public void analyse(int first, int last, int avgWindow, int window, int search, int skip, int algorithm) {

			/* input sanity checks */
			if (first < 0)
				first = 0;
			if (last < first)
				last = first;
			window |= 1;
			if (search < window)
				search = WAVE;

			int n = last - first + 1;

			/* variables holding ping statistics */
			int[] pings = new int[maxChannel_];
			int[] nullpings = new int[maxChannel_];
			int[] integrate = new int[maxChannel_];

			int[][] pingVal = new int[maxChannel_][];

			/* get ping values and statistics */
			int maxPings = getStats(first, last, search, skip, pings, nullpings, integrate, pingVal);

			/*  mean value of the fire pulse for each channel */
			double[] mean = new double[maxChannel_];

			/*  Sum of deviation from mean fire pulse adjusted for triangle wave starting at ping i. */
			double[][] deviation = new double[maxChannel_][WAVE];

			/* sum of deviation from mean with no triangle wave */
			double[] zeroDev = new double[maxChannel_];

			/* calculate mean for each channel */
			for (int i = 0; i < maxChannel_; i++)
				if (pings[i] > 0)
					mean[i] = integrate[i] / (double)(pings[i] - nullpings[i]);

			/* --- calculate deviation from triangle waves --- */

			/* calculate contribution of wave to mean for each candidate wave */
			double[] adjmean = new double[WAVE];
			if ((maxPings % WAVE) != 0) {
				for (int p = 0; p < WAVE; p++) {
					for (int i=0; i < maxPings % WAVE; i++)
						adjmean[p] += wave(p + i + skip);
					adjmean[p] *= n / (double)maxPings;
				} 
			}

			/* ping number of current ping */
			int ping = 0;

			/* output column headings and parameters */
			if (output_)
				System.out.println("Channel\tPing\tsum\tn*wave\tMean\tadj\tdev[0]\t" + first + "\t" + last + "\t" + maxPings + "\t");

			/* for each ping in dataset / for each channel with data / for each candidate wave / calculate deviation */
			for (int v = 0; v < maxPings; v++) {
				for(int channel = 0; channel < maxChannel_; channel++) {
					if (v < pings[channel] && pingVal[channel][v] != 0) {
						ping = skip + v;
						for (int p = 0; p < WAVE; p++) {
							/* calculate weighted running mean of ping value */			    
							double dev = pingVal[channel][v];
							double nVals = 1;
							for (int i = 1; i < avgWindow; i++) {
								if (v - i >= 0 && pingVal[channel][v - i] != 0) {
									dev += pingVal[channel][v - i] / (1.0 + i);
									nVals += 1 / (1.0 + i);
								}
								if (v + i < pingVal[channel].length && pingVal[channel][v + i] != 0) {
									dev += pingVal[channel][v + i] / (1.0 + i);
									nVals += 1 / (1.0 + i);
								}
							}
							dev /= nVals;

							/* calculate deviation of ping from mean plus candiate wave */
							dev -= mean[channel] - adjmean[p] + n * wave(ping + p);
							if (dev < 0)
								dev = - dev;
							if (algorithm == 1)
								dev *= dev;
							if (algorithm == 2)
								dev = Math.sqrt(dev);
							if (algorithm == 3)
								dev = Math.log(1 + dev);
							deviation[channel][p] += dev;
						}

						/* calculate deviation of ping from mean with no wave */
						double zdev = mean[channel] - pingVal[channel][v];
						if (zdev <0)
							zdev = - zdev;
						if (algorithm == 1)
							zdev *= zdev;
						if (algorithm == 2)
							zdev = Math.sqrt(zdev);
						if (algorithm == 3)
							zdev = Math.log(1 + zdev);
						zeroDev[channel] += zdev;

						/* output ping details */
						if (output_)
							System.out.println(channel + "\t" + ping + "\t" + pingVal[channel][v] + "\t" + (n * wave(ping)) + "\t" + 
									mean[channel] + "\t" + adjmean[ping % WAVE] + "\t" + 
									(mean[channel] + adjmean[0] - pingVal[channel][v] + n * wave(ping)));
					}
				}
			}

			/* for each channel with data / find window with best fit - output results */
			synchronized (channelPane_) {

				/* output column headers for candidate wave data */
				if (output_)
					System.out.println("Channel\tInitialPing\tdeviation\t" + first + "\t" + last + "\t" + maxPings + "\t" + ALGORITHMS[algorithm]);

				for (int channel = 0; channel < maxChannel_; channel++)
					if (pings[channel] > 0) {

						/* --- find window with least deviation , i.e. best fit --- */
						/* ping number of first ping of best fit wave */
						int initial = 0;
						/* sum of deviation of window of best fit wave */
						double min = Double.MAX_VALUE;
						/* number of candidates that also have the best fit score */
						int count = 0;

						/* for each candidate wave */
						for (int p = 0; p < WAVE; p++) {

							/* output candidate wave data */
							if (output_)
								System.out.println(channel + "\t" + p + "\t" + deviation[channel][p]);

							/* sum window */
							double windev = 0;
							for (int w = 0; w < window; w++) 
								windev += deviation[channel][(p + w) % WAVE];

							/* if this window is an equal best */
							if (windev == min) 
								count++;

							/* if this window is the best fit so far */
							else if (windev < min) {
								min = windev;
								initial = (p + window / 2) % WAVE; // center of window
								count = 0;
							}
						}

						/* --- display result - details in tooltip --- */
						/* channel number - search parameters */
						JLabel iLabel = new JLabel("" + channel);
						String summary = "First " + first + 
						", Last " + last + 
						", Average " + avgWindow +
						", Window " + window +
						", Pings " + maxPings +
						", Skip " + skip +
						"  " + ALGORITHMS[algorithm];
						iLabel.setToolTipText(summary);
						channelPane_.add(iLabel);

						/* Number of pings for this channel in this file, if known */
						if (pings_ != null)
							channelPane_.add(new JLabel("" + pings_[channel]));
						else
							channelPane_.add(new JLabel(""));

						/* position in wave of first ping in file - initial */			
						JComponent start;

						/* position in wave of last ping in file - fin */
						JComponent end = new JLabel("");

						/* minimum distance from turning point for less than half wave length data to be considered reliable */
						int epsilon = 32;
						/* turning points */
						int turn1 = WAVE / 4 + 1;
						int turn2 = WAVE * 3 / 4 + 1;

						/* position in wave of last ping in file */
						int fin = (initial + maxPings) % WAVE;
						if (pings_ != null)
							fin = (initial + pings_[channel]) % WAVE;

						/* - Check quality of solution - */ 
						/* If flat mean fits better than any candidate wave */
						if (zeroDev[channel] * window < min) {
							start = new JLabel("None");
							start.setToolTipText("Triangle wave error not detected " +
									(zeroDev[channel] * window) + " < " + min + " @ " + initial + ":" + count);

							/* if multiple candidates */
						} else if (count > 0) {
							start = new JLabel("Unknown");
							start.setToolTipText((count + 1) + " equally ranked possible values, the first is " + initial);

							/* if start of data is too close to turning point to be reliable */
						} else if (maxPings < WAVE / 2 &&
								((turn1 - epsilon < initial && turn1 + epsilon > initial) || 
										(turn2 - epsilon < initial && turn2 + epsilon > initial))) {
							start = new JLabel(initial + " ??");
							start.setToolTipText("Detected turning point is too close to edge of data to be reliable");
							end = new JLabel(fin + "");
							end.setToolTipText("Detected turning point is too close to edge of data to be reliable");

							/* if end of data is too close to turning point to be reliable */
						} else if (maxPings < WAVE / 2 &&
								((turn1 - epsilon < fin && turn1 + epsilon > fin) || 
										(turn2 - epsilon < fin && turn2 + epsilon > fin))) {
							start = new JLabel(initial + "");
							start.setToolTipText("Detected turning point is too close to edge of data to be reliable");
							end = new JLabel(fin + " ??");
							end.setToolTipText("Detected turning point is too close to edge of data to be reliable");

							/* if no turning points are included in the data set - on down slope */
						} else if (maxPings < WAVE / 2 &&
								turn1 < initial && initial < fin && fin < turn2) {
							start = new JLabel(turn1 + " - " + (turn2 - maxPings));
							start.setToolTipText("No turning point found in the data " + turn1 + " < " + initial);
							end = new JLabel((turn1 + maxPings) + " - " + turn2);
							end.setToolTipText("No turning point found in the data " + fin + " < " + turn2);

							/* if no turning points are included in the data set - on up slope */
						} else if (maxPings < WAVE / 2 &&
								turn2 < initial && fin < turn1) {
							start = new JLabel(turn2 + " - " + ((turn1 - maxPings) % WAVE));
							start.setToolTipText("No turning point found in the data " + turn2 + "< "+  initial);
							end = new JLabel(((turn2 + maxPings) % WAVE) + " - " + turn1);
							end.setToolTipText("No turning point found in the data " + fin + "< " +turn1);

							/* the result is usable */
						} else {
							final int usePing = initial;
							JButton use = new JButton("" + initial);
							use.addActionListener(new ActionListener() {
								public void actionPerformed(ActionEvent ae) {
									pingNumber_.setValue(new Integer(usePing));
								}
							});
							start = use;
							end = new JLabel("" + fin);
						}

						channelPane_.add(start);
						channelPane_.add(end);

						/* --- plot fit to two images, one a thumbnail, the other large --- */
						/* Create two ImageIcons to be used in labels */
						final ImageIcon[] icon = new ImageIcon[2];

						/* scale and translation values, also used by mouse motion listener */
						double pingsPerPixel = 1;
						double valPerPixel = 1;
						int centre = 0;
						double edge = 0;

						/* size of plots */
						Dimension[] size = new Dimension[2];
						size[0] = new Dimension(100, 20);
						size[1] = new Dimension(800,600);

						/* Four colour images */
						IndexColorModel cm = new IndexColorModel(8, 4,
								//         white black blue red
								new byte[] { -1,   0,   0,  -1 },
								new byte[] { -1,   0,   0,   0 },
								new byte[] { -1,   0,  -1,   0 });

						/* for each image, do the same thing only the size is different */
						for (int i = 0; i < 2; i++) {
							BufferedImage image = new BufferedImage(size[i].width, size[i].height, BufferedImage.TYPE_BYTE_INDEXED, cm);

							Graphics2D g = image.createGraphics();

							/* set background to white */
							g.setColor(Color.WHITE);
							g.fillRect(0, 0, size[i].width, size[i].height);

							/* draw grid, horizonal line through mean and vertical line at initial and every WAVE / 4 */
							centre = size[i].height / 2;
							g.translate(0, centre);

							g.setColor(Color.BLACK);
							g.drawLine(0, 0,size[i].width, 0);

							pingsPerPixel = maxPings  * 1.05 / size[i].width;
							if (pingsPerPixel < 0.25)
								pingsPerPixel = 0.25; // at most 4 pixels per ping
							valPerPixel =   n * WAVE / (30.0 * size[i].height);
							double sizex = (pingsPerPixel > 1) ? pingsPerPixel : 1;
							double sizey = (valPerPixel > 1) ? valPerPixel : 1;

							g.scale(1/pingsPerPixel, 1/valPerPixel);

							edge = (size[i].width * pingsPerPixel - maxPings ) / 2;
							g.translate(edge, 0);

							for (int q = - initial - skip ; q < maxPings; q += WAVE) {
								g.draw(new Line2D.Double(q, - n * WAVE, q, n * WAVE));
								g.draw(new Line2D.Double(q + WAVE / 4 + 1, - n * WAVE, q + WAVE / 4 + 1, n * WAVE));
								g.draw(new Line2D.Double(q + WAVE / 2 + 1, - n * WAVE, q + WAVE / 2 + 1, n * WAVE));
								g.draw(new Line2D.Double(q + WAVE * 3 / 4 + 1, - n * WAVE, q + WAVE * 3 / 4 + 1, n * WAVE));
							}

							/* end of file mark */
							if (pings_ != null && maxPings > pings_[channel]) {
								g.setColor(Color.RED);
								g.draw(new Line2D.Double(pings_[channel] - skip, - n * WAVE, pings_[channel] - skip, n * WAVE));
							}

							/* draw each data point as a 1 ping x 1 val or 1 pixel x 1 pixel rectangle, whichever is larger */
							g.setColor(Color.BLUE);
							for (int v = 0; v < maxPings; v++) {
								g.draw(new Rectangle2D.Double(v, mean[channel] - adjmean[initial] - pingVal[channel][v], sizex, sizey));
								g.fill(new Rectangle2D.Double(v, mean[channel] - adjmean[initial] - pingVal[channel][v], sizex, sizey));
							}

							/* draw line of best fit calculated */
							g.setColor(Color.RED);
							for (int q = - initial - skip; q < maxPings ; q += WAVE) { 
								g.drawLine(q, 0, 
										q + WAVE / 4 + 1, - n * WAVE / 64);
								g.drawLine(q + WAVE / 4 + 1, - n * WAVE / 64,  
										q + WAVE * 3 / 4 + 1, n * WAVE / 64);
								g.drawLine(q + WAVE * 3 / 4 + 1, n * WAVE / 64,
										q + WAVE, 0);
							}

							icon[i] = new ImageIcon(image);
						}

						/* Add plots to interface */
						/* thumbnail is added to dialog, big plot is invoked by clicking on thumbnail */

						/* values needed by MouseMotionListener on large plot */
						final String title = file_ + " [" + channel + "] " + summary; 
						final double yscale = valPerPixel / n;
						final double xscale = pingsPerPixel;
						final int yoffset = (int)((mean[channel] - adjmean[initial]) / n + centre * yscale);
						final int xoffset = skip - (int)edge ;
						final NumberFormat format = NumberFormat.getInstance();
						format.setMaximumFractionDigits(2);

						/* thumbnail */
						JLabel thumbnail = new JLabel(icon[0]);
						thumbnail.setToolTipText("Click for large image");

						/* click on thumbnail to get big plot in its own dialog */
						thumbnail.addMouseListener(new MouseAdapter() {
							public void mouseClicked(MouseEvent me) {

								JDialog showImage = new JDialog((JFrame)ES60Adjust.this.getRootPane().getParent(), title);

								/* field to display current mouse position in pings and dB */
								final JTextField pingpos = new JTextField("");
								pingpos.setEditable(false);

								final JTextField clickpos = new JTextField("");
								clickpos.setEditable(false);
								clickpos.setHorizontalAlignment(JTextField.RIGHT );

								final JButton usePos = new JButton("      ");
								usePos.addActionListener(new ActionListener() {
									public void actionPerformed(ActionEvent ae) {
										if (!"".equals(usePos.getText().trim()))
											pingNumber_.setValue(new Integer(Integer.parseInt(usePos.getText())));
									}
								});


								/* big image with MouseMotionListener which displays current position */
								final JLabel imageLabel = new JLabel(icon[1]);
								imageLabel.addMouseMotionListener( new MouseMotionAdapter() {
									public void mouseMoved(MouseEvent me) {
										/* convert mouse position to pings, vals and dB. */
										int ping = xoffset + (int)(me.getX() * xscale);
										int val = yoffset - (int)(me.getY() * yscale);
										double dB = val * ES60RAW.SAMPLE_TO_DB;
										pingpos.setText("Ping " + ping + ",  Sample value " + val + ", Power " + format.format(dB) + " dB");
									}
								});
								/* Suggest starting value from mouse click */
								imageLabel.addMouseListener( new MouseAdapter() {
									public void mouseClicked(MouseEvent me) {
										int ping = xoffset + (int)(me.getX() * xscale);
										if (me.getY() < imageLabel.getSize().height / 2) {
											clickpos.setText("Maximum at " + ping + " gives first ping at ");
											usePos.setText("" + ((WAVE * 5 / 4 + 1 - ping) % WAVE)); 
										} else {
											clickpos.setText("Minimum at " + ping + " gives first ping at ");
											usePos.setText("" + ((WAVE * 7 / 4 + 1 - ping) % WAVE)); 
										}
									}
								});

								/* layout big plot dialog, plot at top, position at bottom - and display */
								JComponent clickPane = new Box(BoxLayout.X_AXIS);
								clickPane.add(clickpos);
								clickPane.add(usePos);

								JComponent posPane = new JPanel(new GridLayout(1, 0));
								posPane.add(pingpos);
								posPane.add(clickPane);

								Container content = showImage.getContentPane();
								content.add(imageLabel, BorderLayout.NORTH);
								content.add(posPane, BorderLayout.SOUTH);
								showImage.pack();
								showImage.setVisible(true);
							}
						});

						channelPane_.add(thumbnail);
					}
			}


			/* Display results */
			pack();
			setVisible(true);
		}

		/**
		 *  This method performs two operations while reading the entire ES60File:
		 *  1) it compiles statistics of the file (starting, end, min, max, counts etc) 
		 *  and displays them in filePane_;
		 *  2) it integrates the samples from the first to last (inclusive) samples of each ping
		 *  for each channel.
		 *
		 *  The files statistics are only compiled if they have not previously been compiled,
		 *  (haveStats_ is false). 
		 *  If haveStats_ is true the file will only be read until sufficient (search + skip) pings
		 *  have been read for each channel.
		 *
		 *  If there are insufficient pings in this file, but there is a next file, the remainder of the pings
		 *  are gathered from the next file.
		 *
		 *  This method does both functions as it seemed that reading the file twice, once for each function,
		 *  would be inefficient.
		 *
		 *  All parameters are related to the sample integration function.
		 *
		 *  
		 *  @param first First sample to include in search for trianlge wave, 
		 *               should be within fire pulse.
		 *  @param last Last sample to include in search for triable wave,
		 *               must be greater than first and should still be within fire pulse.
		 *  @param search Maximum number of pings to include in calculation.
		 *  @param skip Number of intial pings to skip before starting calculation.
		 *  @param pings Count of pings read for each channel. (updated by this method). 
		 *  @param nullpings Count of pings with insufficient samples for each channel. (updated by this method)
		 *  @param integrate Sum of sample range for all pings for each channel. (updated by this method)
		 *  @param pingVal Sum of sample range for each ping for each channel. (updated by this method)
		 *  @return number of pings successfully read into pingVal.
		 **/

		public int getStats(int first, int last, int search, int skip, int[] pings, int[] nullpings, int[] integrate, int[][] pingVal) {
			ES60NMEA firstPos = null;
			ES60NMEA lastPos = null;
			int minCount = Integer.MAX_VALUE;
			int maxCount = 0;
			double north = Double.NaN;
			double south = Double.NaN;
			double east = Double.NaN;
			double west = Double.NaN;

			int[] pingtotal = new int[maxChannel_];

			/*  can we skip this file? */
			if (pings_ != null && skip > 0) 
				for (int i = 0; i < pings_.length; i++) {
					if (pings_[i] > 0 && pings_[i] <= skip) {
						if (next_ == null)
							return 0;
						else
							return next_.getStats(first, last, search, skip - pings_[i], pings, nullpings, integrate, pingVal);
					}
				}

			/* read the file */
			try {
				file_.open();
				ES60Record rec = file_.read();
				con_ = rec;

				try { 
					while (true) {
						/* get next record from file */
						if (rec instanceof ES60RAW) {
							ES60RAW rrec = (ES60RAW)rec;
							int channel = rrec.getChannel();
							if (pingVal[channel] == null)
								pingVal[channel] = new int[search];

							if (++pingtotal[channel] > skip && pings[channel] < search) 
								try {
									pingVal[channel][pings[channel]]= rrec.getSum(first, last);
									integrate[channel] += pingVal[channel][pings[channel]];
									pings[channel]++;
								} catch (ES60RAW.InsufficientSamplesException ise) {
									pingVal[channel][pings[channel]] = 0;
									nullpings[channel]++;
									pings[channel]++;
								} catch (ArrayIndexOutOfBoundsException aioobe) {
									aioobe.printStackTrace();
								}

								else if (haveStats_ && pings[channel] > search)
									break;

							if (!haveStats_) {
								int count = rrec.getCount();
								if (count > maxCount)
									maxCount = count;
								if (count < minCount)
									minCount = count;
							}

							/* Get position stats if wanted */
						} else if (!haveStats_ && rec instanceof ES60NMEA) {
							ES60NMEA nrec = (ES60NMEA)rec;
							if (nrec.hasPos()) {
								lastPos = nrec;
								if (firstPos == null) {
									firstPos = nrec;
									north = south = nrec.getLatitude();
									east = west = nrec.getLongitude();
								} else {
									double lat = nrec.getLatitude();
									double lon = nrec.getLongitude();
									if (lat > north)
										north = lat;
									if (lat < south)
										south = lat;
									if (lon > east)
										east = lon;
									if (lon < west)
										west = lon;
								}
							}
						}
						rec = file_.read();
					}
				} catch (EOFException eof) {}
				file_.close();

				/* determine maximum channel number */
				int minChannel = -1;
				int maxChannel = 0;
				int maxPings = 0;
				for (int i = 0; i < pings.length; i++) {
					if (pings[i] > 0) {
						maxChannel = i + 1;
						if (minChannel < 0)
							minChannel = i;
					}
					if (pings[i] > maxPings)
						maxPings = pings[i];
				}

				if (maxPings > 0)
					maxChannel_ = maxChannel;

				/* display stats */
				if (!haveStats_) {
					SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
					dateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));
					NumberFormat degFormat = NumberFormat.getInstance();
					degFormat.setMaximumFractionDigits(6);
					degFormat.setMinimumFractionDigits(6);

					filePane_.add(new JLabel("Start"));
					filePane_.add(new JLabel(dateFormat.format(con_.getTime())));
					if (firstPos != null) {
						filePane_.add(new JLabel(""));
						JComponent startPos = new JPanel(new GridLayout(1,2));
						startPos.add(new JLabel(degFormat.format(firstPos.getLatitude())));
						startPos.add(new JLabel(degFormat.format(firstPos.getLongitude())));
						filePane_.add(startPos);
					}
					filePane_.add(new JLabel("End"));
					filePane_.add(new JLabel(dateFormat.format(rec.getTime())));
					if (lastPos != null) {
						filePane_.add(new JLabel(""));
						JComponent endPos = new JPanel(new GridLayout(1,2));
						endPos.add(new JLabel(degFormat.format(lastPos.getLatitude())));
						endPos.add(new JLabel(degFormat.format(lastPos.getLongitude())));
						filePane_.add(endPos);

						filePane_.add(new JLabel("North West"));
						JComponent nwPos = new JPanel(new GridLayout(1,2));
						nwPos.add(new JLabel(degFormat.format(north)));
						nwPos.add(new JLabel(degFormat.format(west)));
						filePane_.add(nwPos);

						filePane_.add(new JLabel("South East"));
						JComponent sePos = new JPanel(new GridLayout(1,2));
						sePos.add(new JLabel(degFormat.format(south)));
						sePos.add(new JLabel(degFormat.format(east)));
						filePane_.add(sePos);
					}

					filePane_.add(new JLabel("Min samples"));
					filePane_.add(new JLabel("" + minCount));
					filePane_.add(new JLabel("Max samples"));
					filePane_.add(new JLabel("" + maxCount));

					pings_ = new int[maxChannel_];
					for (int i = 0; i < maxChannel_; i++)
						pings_[i] = pingtotal[i];

					haveStats_ = true;
					pack();
				}

				/* Do we need the next file? */
				if (search > maxPings) {

					/* We have ping information for this file, save it if we haven't already */
					if (pings_ == null) {
						pings_ = new int[maxChannel_];
						for (int i = 0; i < maxChannel_; i++)
							pings_[i] = pingtotal[i];
					}

					/* we have all the data we can get */		    
					if (next_ == null)
						return maxPings;

					/* get more data */
					if (skip > maxPings)
						skip -= maxPings;
					else
						skip = 0;
					return next_.getStats(first, last, search, skip, pings, nullpings, integrate, pingVal);
				}

				/* We have all the data we want */
				return search;

			} catch (IOException ioe) {
				JOptionPane.showMessageDialog(ES60Adjust.this,
						"Could not analyse file " + ioe, 
						file_.toString(),
						JOptionPane.ERROR_MESSAGE);
			}
			return 0;
		}

	}

	/* ---------- Static Public Methods ---------- */

	/**
	 *  Main Program.
	 *  If no command line parameters are given a ES60Adjust GUI object is created.
	 *  If one command line paramter is given a usage message is printed.
	 *  If more than one command line parameter are given 
	 *  the first is the initial ping number and subsequent parameters are input file names.
	 *
	 *<pre>
	 *  usage: java ES60Adjust ping file.raw...
	 *    ping - initial ping no
	 *    file.raw - one or more ES60 raw files.
	 *
	 *  outputs filec.raw...
	 *</pre>
	 **/
	public static void main(String[] args) {

		/* run as a gui */
		if (args.length == 0)  {
			URL smallIcon = cl__.getResource("images/csiro_small.png");
			if (null == smallIcon)
				smallIcon = cl__.getResource("csiro_small.png");

			URL icon = cl__.getResource("images/csiro.png");
			if (null == icon)
				icon = cl__.getResource("csiro.png");

			JFrame starting = new JFrame("CSIRO Marine and Atmospheric Research - ES60Adjust");
			starting.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			JTextArea startLabel = new JTextArea(ABOUT);
			startLabel.setEditable(false);
			startLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
			startLabel.setOpaque(false);

			Container startCont = starting.getContentPane();
			startCont.add(startLabel, BorderLayout.CENTER);

			if (icon != null) 
				startCont.add(new JLabel(new ImageIcon(icon)), BorderLayout.WEST);

			ES60Adjust adjust = new ES60Adjust();
			JFrame frame = new JFrame("ES60 correct");
			frame.getContentPane().add(adjust);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			if (smallIcon != null)
				frame.setIconImage(Toolkit.getDefaultToolkit().createImage(smallIcon));

			frame.pack();
			adjust.helpAbout();
			frame.setVisible(true);
			return;
		}

		/* output usage message */
		if (args.length < 2) {
			System.err.println("usage: java ES60Adjust ping file.raw...");
			System.err.println("    ping - initial ping number");
			System.err.println("    file.raw - one or more ES60 .raw files");
			System.exit(0);
		}

		int pingNo = 0;
		try {
			pingNo = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.err.println("usage: java ES60Adjust ping file.raw...");
			System.err.println("    ping - initial ping number (non-negative integer)");
			System.err.println("    file.raw - one or more ES60 .raw files");
			System.exit(1);
		}

		/* get input files */
		File[] inFile = new File[args.length -1];
		for (int i=1; i < args.length; i++) 
			inFile[i - 1] = new File(args[i]);

		/* run processing */
		process(pingNo, inFile, null, "c");
	}

	/**
	 *  Process input files.
	 *
	 *  @param pingNo Ping number of first ping of first file in triangle wave sequence.
	 *  @param inFile Files to process, in order (ping number carries through)
	 *  @param outdir Directory to write output files to. If null current directory is used.
	 *  @param affix  String to append to output filename prior to .raw extension.
	 **/
	public static void process(int pingNo, File[] inFile, File outdir, String affix) {

		int processed = 0;	/* number of files processed */       
		int startPing = pingNo;	/* ping number of first ping of this file */
		long totalBytes = 0;	/* total size of files to process */
		long bytesDone = 0;	/* total size of files processed */

		for (int i=0; i < inFile.length; i++) 
			totalBytes += inFile[i].length();

		try {
			for (int i=0; i < inFile.length; i++) {
				if (Thread.interrupted())
					throw new InterruptedException();

				/* check filename ends with .raw */
				String filename = inFile[i].getName();
				if (!filename.toLowerCase().endsWith(".raw")) {
					error("Filename does not end in .raw - skipped: " + filename);
					continue;
				}

				/* create output file */
				String outname = filename.substring(0, filename.length() - 4) + affix + ".raw";

				File outfile = new File(outdir, outname);

				if (outfile.equals(inFile[i])) {
					error("Output file is the same as input - aborting: " + inFile[i].getPath());
					throw new InterruptedException("Output file is the same as input");
				}

				/* process this file */
				if (inFile[i].isFile()) {
					try {
						/* input file */
						long inLength = inFile[i].length();
						ES60File esFile = new ES60File(inFile[i]);
						esFile.open();
						startPing = pingNo;

						/* output file */
						FileOutputStream fos = null;
						try {
							fos = new FileOutputStream(outfile);
						} catch (IOException ioe) {
							error("Could not create file " + outfile.getPath());
							continue;
						}
						DataOutputStream out = new DataOutputStream(fos);

						/* if a second record appears for any channel increment ping number */
						int channelmask = 0;

						try {
							while (true) {
								if (Thread.interrupted())
									throw new InterruptedException();

								/* get next record from file */
								ES60Record rec = esFile.read();
								if (rec instanceof ES60RAW) {
									ES60RAW rrec = (ES60RAW)rec;

									/* check if this pingNo has been used for this channel */
									int channelflag = 1 << rrec.getChannel();
									if ((channelmask & channelflag) == 0)
										channelmask |= channelflag;
									else {
										pingNo++;
										channelmask = channelflag;
										if (pingNo % 100 == 0)
											update(filename + " pings: " + startPing + "-" + pingNo + " to " + outname, 
													outfile.length() * 100 / inLength,
													(bytesDone + outfile.length()) * 100 / totalBytes,
													false);
									}

									int adj = wave(pingNo);

									/* process this record */
									if (adj != 0)
										rrec.es60adjust(adj);
								}

								/* write record to output file */
								rec.write(out, esFile.swap());
							} // while (true)
						} catch (EOFException eof) {}

						/* finished this file */
						out.close();
						esFile.close();
						processed++;
						bytesDone += outfile.length();
						update(filename + " pings: " + startPing + "-" + pingNo + " to " + outname, 
								100L,
								bytesDone * 100 / totalBytes,
								true);
						pingNo++;

					} catch (IOException ioe) {
						ioe.printStackTrace();
					}

				} else
					error("Can't find file: " + inFile[i]);
			} // for i < inFile.length

		} catch (InterruptedException ie) {
			error("Processing interrupted");
		}

		/* all done */
		update("Processed " + processed + " files", 100L, 100L, true);
		if (gui__ != null)
			gui__.done();
	}

	/* ---------- Protected Static Methods ---------- */

	/**
	 *  Method to handle error messages from process.
	 *  Error message is always written to System.err,.
	 *  If a GUI is present it is displayed to the user in a dialog,
	 *  where the user is given the option to cancel further processing.
	 *
	 *  @param message Message to display to user.
	 **/
	protected static void error(String message) {
		System.err.println(message);

		/* display message if GUI is present */
		if (gui__ != null) {
			if (JOptionPane.CANCEL_OPTION ==
				JOptionPane.showConfirmDialog(gui__,
						message, "Error",
						JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.ERROR_MESSAGE))
				Thread.currentThread().interrupt();
		}
	}

	/**
	 *  Update processing status.
	 *  If no GUI then output is written to System.out.
	 *  If a GUI is present the Progress is used to display progress information.
	 *
	 *  @param message Message to display.
	 *  @param pctFile Percent of this file complete.
	 *  @param pctTotal Percent of total complete.
	 *  @param finished Have we finished with this file?
	 */
	protected static void update(String message, long pctFile, long pctTotal, boolean finished) {
		if (gui__ == null) {
			System.out.print("\r" + message + "  " + pctFile + "%  " + pctTotal +"%");
			if (finished)
				System.out.println();

		} else 
			gui__.update(message, pctFile, pctTotal, finished);
	}

	/**
	 *  Returns the triangle wave amount added to the data by the ES60
	 *  for this ping number.
	 *  @param ping Ping number to get adjustment for.
	 *  @return Amount that should be removed from the power level for this ping.
	 **/
	public static int wave(int ping) {
		int adj = ping % WAVE;
		if (adj > WAVE * 3 / 4)
			adj -= WAVE;
		else if (adj > WAVE / 4)
			adj = WAVE / 2 - adj;
		adj /= 16;
		return adj;
	}
}



/*
    For God did not send his Son into the world to be its judge,
    but to be its saviour.

    Whoever believes in the Son is not judged;
    but whoever does not believe has already been judged,
    because he has not believed in God's only Son.
    This is how the judgement works:
    the light has come into the world,
    but people love the darkness rather than the light, 
    because their deeds are evil.
    Anyone who does evil things hates the light,
    because he does not want his evil deeds to be shown up.
    But whoever does what is true comes to the light
    in order that the lighe may show that
    what he did was in obedience to God.
        John 3:17-21
*/
