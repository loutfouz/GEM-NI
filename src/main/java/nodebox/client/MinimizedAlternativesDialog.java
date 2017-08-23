package nodebox.client;

import java.io.File;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import nodebox.ui.Theme;



/**
 * This class includes everything! A bunch of subclasses and some methods.
 * 
 * The whole gallery uses many different swing components. Some of them a 
 * highly customized for our own purpose.
 * 
 * 1. You a get a tree where you can select either range or set for special
 * port value.
 * 
 * 2. You get a gallery where you can see the actual result from the Cartesian
 * product.
 *
 */
public class MinimizedAlternativesDialog extends JDialog implements ActionListener {

	// dimension for the previews
	public static final int PREVIEW_WIDTH = 180;
	public static final int PREVIEW_HEIGHT = PREVIEW_WIDTH;
	// use the same button size for all buttons
	public static final int BUTTON_WIDTH = 140;
	public static final int BUTTON_HEIGHT = 30;

	// use constants for text
	public static final String BUTTON_CLOSE_TEXT = "Close";
	public static final String BUTTON_CREATE_TEXT = "Restore";
	public static final String BUTTON_CREATE_AND_CLOSE_TEXT = "Restore + Close";
	public static final String BUTTON_PAGE_NEXT_TEXT = "Next >>";
	public static final String BUTTON_PAGE_PREVIOUS_TEXT = "<< Previous";

	public static final String TEXT_PAGES = "%num/%total";
	public static final String TEXT_PREVIEW = "<html><span>Result: <b>%total</b></span></html>";
	
	// reference to the application frame that called the gallery dialog
	private ApplicationFrame appFrame;
	
	// gallery panel
	public Gallery gallery;
	
	// components for gallery and configurator
	private JPanel contentPanel;
	private JPanel galleryPanel;
	private JPanel galleryConfiguratorPanel;
	private JPanel buttonPanel;
	private JPanel buttonRow1Panel;
	private JPanel buttonRow2Panel;
	private JPanel buttonRow1LeftPanel;
	private JPanel buttonRow1CenterPanel;
	private JPanel buttonRow1RightPanel;
	private JPanel buttonRow2LeftPanel;
	private JPanel buttonRow2CenterPanel;
	private JPanel buttonRow2RightPanel;

	private JButton buttonClose;
	//private JButton buttonToggle;
	private JButton buttonRestore;
	private JButton buttonRestoreClose;
	private JButton buttonPageNext;
	private JButton buttonPagePrevious;
	
	//private JComboBox<String> comboBoxMasterSelection;
	
	private JLabel pageNumber;
	private JLabel previewNumber;
	
	private Dimension buttonSize;
	private Dimension preferredSize;
	private Dimension buttonPanelSize;
	private Dimension buttonRow1PanelSize;
	private Dimension buttonRow2PanelSize;
	

	public MinimizedAlternativesDialog(final ApplicationFrame appFrame) {
		super(appFrame);
		
		this.appFrame = appFrame;
		// get monitor height and width (always use the first monitor, since they are all equally sized)
		JPanel monitorPanel = (JPanel) appFrame.monitorPanel.getComponent(appFrame.getMonitorForDocument(appFrame.getDocumentGroup().get(0)));
		final int width = monitorPanel.getWidth();
		final int height = monitorPanel.getHeight();

		// set dimensions for the dialog and other components
		final Dimension minimumSize = new Dimension(600, 600);
		preferredSize = new Dimension(width - 20, height - 20);
		buttonPanelSize = new Dimension(preferredSize.width - 40, (BUTTON_HEIGHT * 3) + 13);
		buttonRow1PanelSize = new Dimension(buttonPanelSize.width, (buttonPanelSize.height / 3));
		buttonRow2PanelSize = new Dimension(buttonPanelSize.width, ((buttonPanelSize.height / 3) * 2));
		
		setLayout(new BorderLayout());

		// preferred size is half of the screen size
		setMinimumSize(minimumSize);
		setPreferredSize(preferredSize);

		// center the dialog relative to the affected monitor panel
		setLocationRelativeTo(monitorPanel);

		buttonSize = new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT);
		
		// create gallery as well as configurator. depending on the mode we hide one
		gallery = new Gallery(true);

		// create all swing components
		pageNumber = new JLabel();
		previewNumber = new JLabel();
		
		contentPanel = new JPanel();
		galleryPanel = new JPanel();
		galleryConfiguratorPanel = new JPanel();
		buttonPanel = new JPanel();
		buttonRow1Panel = new JPanel(new GridLayout(1, 3));
		buttonRow2Panel = new JPanel(new GridLayout(1, 3));
		buttonRow1LeftPanel = new JPanel();
		buttonRow1CenterPanel = new JPanel();
		buttonRow1RightPanel = new JPanel();
		buttonRow2LeftPanel = new JPanel();
		buttonRow2CenterPanel = new JPanel();
		buttonRow2RightPanel = new JPanel();
		
		buttonRow1LeftPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		buttonRow1CenterPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		buttonRow1RightPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

		buttonRow2LeftPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		buttonRow2CenterPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		buttonRow2RightPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));


		buttonPanel.setLayout(new BorderLayout());
		buttonPanel.setPreferredSize(buttonPanelSize);
		buttonRow1Panel.setPreferredSize(buttonRow1PanelSize);
		buttonRow2Panel.setPreferredSize(buttonRow2PanelSize);
		
		buttonPanel.add(buttonRow1Panel, BorderLayout.PAGE_START);
		buttonPanel.add(buttonRow2Panel, BorderLayout.PAGE_END);
		
		contentPanel.add(galleryPanel, BorderLayout.CENTER);
		contentPanel.add(galleryConfiguratorPanel, BorderLayout.CENTER);
		contentPanel.add(buttonPanel, BorderLayout.PAGE_END);

		// add gallery and gallery configurator
		galleryPanel.add(gallery);

		// add buttons
		buttonClose = new JButton(BUTTON_CLOSE_TEXT);
		//buttonToggle = new JButton(BUTTON_TOGGLE_TEXT_1);
		buttonRestore = new JButton(BUTTON_CREATE_TEXT);
		buttonRestoreClose = new JButton(BUTTON_CREATE_AND_CLOSE_TEXT);
		buttonPageNext = new JButton(BUTTON_PAGE_NEXT_TEXT);
		buttonPagePrevious = new JButton(BUTTON_PAGE_PREVIOUS_TEXT);
		
		buttonRestore.setEnabled(false);
		buttonPageNext.setEnabled(false);
		buttonPagePrevious.setEnabled(false);
		
		buttonClose.addActionListener(this);
		//buttonToggle.addActionListener(this);
		buttonRestore.addActionListener(this);
		buttonRestoreClose.addActionListener(this);
		buttonPageNext.addActionListener(this);
		buttonPagePrevious.addActionListener(this);
		
		buttonClose.setPreferredSize(buttonSize);
		//buttonToggle.setPreferredSize(buttonSize);
		buttonRestore.setPreferredSize(buttonSize);
		buttonRestoreClose.setPreferredSize(buttonSize);
		buttonPageNext.setPreferredSize(buttonSize);
		buttonPagePrevious.setPreferredSize(buttonSize);

		pageNumber.setHorizontalAlignment(JLabel.CENTER);
		
		pageNumber.setVisible(true);
		buttonRestoreClose.setVisible(true);
		buttonPageNext.setVisible(true);
		buttonPagePrevious.setVisible(true);

		buttonRow1CenterPanel.add(buttonPagePrevious);
		buttonRow1CenterPanel.add(pageNumber);
		buttonRow1CenterPanel.add(buttonPageNext);
		buttonRow1RightPanel.add(buttonRestore);

		buttonRow2LeftPanel.add(previewNumber);

		buttonRow2RightPanel.add(buttonClose);
		buttonRow2RightPanel.add(buttonRestoreClose);

		buttonRow1Panel.add(buttonRow1LeftPanel);
		buttonRow1Panel.add(buttonRow1CenterPanel);
		buttonRow1Panel.add(buttonRow1RightPanel);
		buttonRow2Panel.add(buttonRow2LeftPanel);
		buttonRow2Panel.add(buttonRow2CenterPanel);
		buttonRow2Panel.add(buttonRow2RightPanel);
		
		// add listener for resizing
		addComponentListener(new ComponentListener() {
			/**
			 * If the user resizes the window we reset some components.
			 * 
			 * Especially if the gallery view is active we show more or less
			 * previews.
			 */
			@Override
			public void componentResized(ComponentEvent e) {
				gallery.update(false);
				// update scroll pane
				Dimension windowSize = e.getComponent().getSize();
				final Dimension size = new Dimension(windowSize.width - 40, windowSize.height - 200);

				buttonPanel.setPreferredSize(new Dimension(size.width, buttonPanel.getHeight()));
				contentPanel.setPreferredSize(size);
				contentPanel.updateUI();
			}
			@Override
			public void componentShown(ComponentEvent e) {}
			@Override
			public void componentMoved(ComponentEvent e) {}
			@Override
			public void componentHidden(ComponentEvent e) {}
		});

		add(contentPanel, BorderLayout.CENTER);
		pack();
		setVisible(true);		
		appFrame.log("Minimized Alternatives", "open dialog");

	}
	
	/**
	 * The method populates power sets
	 */
	


	@Override
	public void actionPerformed(ActionEvent e) {
		JButton button = (JButton) e.getSource();
		
		if (button.equals(buttonClose)){
			this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			appFrame.log("Minimized Alternatives", buttonClose.getText(), "close no restore");
		}
		
		if (button.equals(buttonPageNext)){
			gallery.nextPage();
			appFrame.log("Minimized Alternatives", buttonPageNext.getText());
		}
		
		if (button.equals(buttonPagePrevious)){
			gallery.previousPage();
			appFrame.log("MMinimized Alternatives", buttonPagePrevious.getText());
		}
		
		if (button.equals(buttonRestoreClose) || button.equals(buttonRestore)) {
			HashSet<NodeBoxDocument> set = new HashSet<NodeBoxDocument>(); //used for logging
			for (NodeBoxDocument document : gallery.getSelectedDocuments()) {
				// mark document as restored
				gallery.setDocumentAsRestored(document);

				// create a new instance from the document (only if it not already exists)
				gallery.minimizedDocuments.remove(document);

				//we need to mimmick as if actually restore the original document (which we cannot easily do, therefore we create a copy of existing document)
				//for that we need to match the File and Document Name (which is based on document number)
				
				File oldDocFile = document.getDocumentFile();
				int oldDocNo = document.getDocumentNo();
				NodeBoxDocument newDoc = appFrame.createNewAlternativeFromDocument(document, document.getMonitorNo());
				newDoc.setDocumentFile(oldDocFile);
				newDoc.setDocumentNo(oldDocNo);
				newDoc.setDocumentGroup(appFrame.getDocumentGroup());
				
				appFrame.log("Minimized Alternatives, restore", document.getDocumentName(), String.valueOf(document.getDocumentNo()));

				set.add(document);
			}
			
			// update the application after we created new alternatives
			appFrame.updateAlternativesPanels(true);
			appFrame.resetGlobals();
			
			//re-set the rendered node, so that the view can be centered and zoomed
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					//center and zoom relative to all
					resetViewRelativeToFirstDocumentInTheWorkspace();
				}
			});
			
			// close dialog?
			if (button.equals(buttonRestoreClose))
				this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)); 

			logRestoredAlternatives(button.getText(), set);
		}
	}

	/**
	 * Reset view relative to the first document in the workspace
	 * This is called to address the issue when updating the gallery workspace gets panned away
	 */
	private void resetViewRelativeToFirstDocumentInTheWorkspace() {
		try{
			NodeBoxDocument.centerAndZoomAllZoomableViews(appFrame.getDocumentGroup().get(0), true);
		}
		catch(Exception e2){
			
		}
	}

	
	private void logRestoredAlternatives(String text,
			HashSet<NodeBoxDocument> set) {
		//log
		//create a string list of selected Nodes
		String newDocsNames = "names" + ApplicationFrame.attributeDelimiter;
		String newDocsNumbers = "numbers" + ApplicationFrame.attributeDelimiter;
		for (NodeBoxDocument newDoc : set){
			newDocsNames += newDoc.getDocumentName() + ApplicationFrame.itemDelimiter;
			newDocsNumbers += String.valueOf(newDoc.getDocumentNo()) + ApplicationFrame.itemDelimiter;
		}
		//remove last delimiter character
		newDocsNames = newDocsNames.substring(0, newDocsNames.length() - 1);
		newDocsNumbers = newDocsNumbers.substring(0, newDocsNumbers.length() - 1);

		appFrame.log("Minimized Alternatives", text, newDocsNames, newDocsNumbers);
	}
	
	/**
	 * This class produces the preview of the Cartesian product. 
	 */
	class Gallery extends JPanel {

		private int rows = 0;
		private int cols = 0;
		private int page = 1; // by default start on the first page
		private int pages = 1; // by default start on the first page
		private int perPage = 0; // how many documents per page
		private int perPageReserved = 0; // how many fields are reserved for the network view
		private boolean changed = false;
		private Dimension previewPreferredSize = new Dimension(PREVIEW_HEIGHT, PREVIEW_HEIGHT);
		private GridBagConstraints constraints;

		private NodeBoxDocument[][] array; // saves which positions are already filled with which document
		
		private ArrayList<Integer> minimizedDocumentsSelected; // saves which documents are selected
		private ArrayList<Integer> minimizedDocumentsRestored; // saves which documents are already created
		private ArrayList<NodeBoxDocument> minimizedDocuments;

		private final Border borderSelected;
		private final Border borderUnselected;


		public Gallery(boolean visible) {
			setVisible(visible);
			
			constraints = new GridBagConstraints();
			minimizedDocuments = appFrame.getMinimizedDocuments();
			minimizedDocumentsSelected = new ArrayList<Integer>();
			minimizedDocumentsRestored = new ArrayList<Integer>();

			// padding
			constraints.insets = new Insets(1, 1, 1, 1);
			
			borderSelected = BorderFactory.createLineBorder(Color.RED, 2);
			borderUnselected = BorderFactory.createEmptyBorder(2, 2, 2, 2);
			
			setGlassPane(new GlassPane(this));
		}
		
		public void clear() {
			page = 1;
			minimizedDocumentsSelected.clear();
			minimizedDocumentsRestored.clear();
		}

		public void update(boolean force) {
			if(!isVisible())
				return;

			// initialize
			setProportions();
			if (changed || force) {
				array = new NodeBoxDocument[rows][cols];
				removeAll();
				setLayout(new GridBagLayout());
				loadGallery();				
				changed = false;
			}
		}
		
		public void setProportions() {
			int oldRows = rows;
			int oldCols = cols;
			
			// how much space do we have for our gallery and how many previews can we show?
			Dimension size = contentPanel.getSize();
			// subtract the button panel bar from height
			size.setSize(size.width, size.height - buttonPanel.getSize().height);

			// at least 3x4 (we need space for the network view as well)
			rows = (int) Math.ceil(size.height / PREVIEW_HEIGHT);
			cols = (int) Math.ceil(size.width / PREVIEW_WIDTH);
			rows = rows > 3 ? rows : 3;
			cols = cols > 4 ? cols : 4;
			perPage = rows * cols;
			
			// if rows or columns changed we need to rearrange
			if (oldRows != rows || oldCols != cols)
				changed = true;
		}

		

		private void loadGallery() {			
			// fill array and grid bag
			int k = (page - 1) * (perPage - perPageReserved);
			for (int i=0; i<rows; i++) {
				for (int j=0; j<cols; j++) {
					if (array[i][j] != null) // already filled (e.g. network pane)
						continue;

					// set document to position
					constraints.gridy = i;
					constraints.gridx = j;

					//ImagePanel preview = null;
					JPanel preview = new JPanel();
					if (k < minimizedDocuments.size()) {
						array[i][j] = minimizedDocuments.get(k);

						NodeBoxDocument minimizedDocument = minimizedDocuments.get(k);
						preview = minimizedDocument.getViewerPane().getContentPanel();
						preview.setName(String.valueOf(k));
						Border border = minimizedDocuments.contains(new Integer(k)) ? borderSelected : borderUnselected;
						preview.setBorder(border);
						
						preview.setPreferredSize(previewPreferredSize);
						add(preview, constraints);
						minimizedDocument.ungroup();
						
						//to center the preview
						minimizedDocument.getViewerPane().getViewer().resetViewTransform();
					}
					++k;
				}
			}
			
			// show page numbers text
			String text = TEXT_PAGES.replace("%num", String.valueOf(page)).replace("%total", String.valueOf(pages));
			
			// (de)activate page next and previous buttons
			buttonPagePrevious.setEnabled(page > 1);
			buttonPageNext.setEnabled(page < pages);
			
			pageNumber.setText(text);
			galleryPanel.updateUI();
		}
		
	    
	    public class ImagePanel extends JPanel{

	        private BufferedImage image;

	        public ImagePanel(JPanel panel) {
	        	//create a photo of the source panel
	            image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
	 	        panel.paint(image.getGraphics());
	 	        
	        	//rescale to image to fit the preview panel
	        	AffineTransform at = new AffineTransform();
	        	double scale =  previewPreferredSize.height / image.getHeight();
	        	at.scale(image.getHeight(), image.getHeight());
	       }

	        @Override
	        protected void paintComponent(Graphics g) {
	            super.paintComponent(g);
	            double preferredWidth = ((double) previewPreferredSize.height)/image.getHeight()*image.getWidth();
	            g.drawImage(image, 0, 0, (int) preferredWidth, previewPreferredSize.height, 0, 0, image.getWidth(), image.getHeight(), null);
	            //update preferred preview size
	            previewPreferredSize = new Dimension ((int)preferredWidth, previewPreferredSize.height);
	        }

	    }
		public void nextPage() {

			if (page < pages) {
				++page;
				update(true); // load next page into gallery
			}
			else{ //switch to next netowrk
				/*int nextNetworkIndex = comboBoxMasterSelection.getSelectedIndex() + 1;
				if (nextNetworkIndex < powerSetAndSelectedDocuments.size()){
					page = 1;
					comboBoxMasterSelection.setSelectedIndex(nextNetworkIndex);
					update(true); // load next page into gallery
				}*/
			}
		}
		
		public void previousPage() {

			if (page > 1) {
				--page;
				update(true); // load next page into gallery
			}
			else{ //switch to previous network
				//int nextNetworkIndex = comboBoxMasterSelection.getSelectedIndex() - 1;
				/*if (nextNetworkIndex >= 0){
					page = pages;
					comboBoxMasterSelection.setSelectedIndex(nextNetworkIndex);
					update(true); // load next page into gallery
				}*/
			}
		}
		
		public ArrayList<NodeBoxDocument> getSelectedDocuments() {
			ArrayList<NodeBoxDocument> selectedDocuments = new ArrayList<NodeBoxDocument>();
			for (Integer i : minimizedDocumentsSelected)
				if (minimizedDocumentsRestored.indexOf(i) <= -1) // return only uncreated documents
					selectedDocuments.add(minimizedDocuments.get(i));
			return selectedDocuments;
		}

		public boolean setDocumentAsRestored(NodeBoxDocument document) {
			final Integer i = (Integer) minimizedDocuments.indexOf(document);
			if (i > -1) {
				minimizedDocumentsRestored.add(i);
				minimizedDocumentsSelected.remove(i);
				document.getViewerPane().getViewer().setBackground(Theme.IDLE_COLOR);
				document.getViewerPane().getContentPanel().setBorder(borderUnselected);
				return true;
			}
			return false; 
		}


		/**
		 * We use the glass pane to select the preview documents from the gallery. I tried a couple
		 * of things but actually it seems to be the easiest solution to use a glass pane. 
		 */
		private class GlassPane extends JPanel implements AWTEventListener {

			private Gallery gallery;
			
			
			public GlassPane(Gallery gallery) {
				super(null);
				this.gallery = gallery;
				setOpaque(false);
				AWTEventListener al = (AWTEventListener) this;
		        Toolkit.getDefaultToolkit().addAWTEventListener(al, AWTEvent.MOUSE_EVENT_MASK);
			}

			@Override
			public void eventDispatched(AWTEvent e) {
				if (e instanceof MouseEvent) {
					MouseEvent me = (MouseEvent) e;
					JPanel preview = null;
					
					if (me.getID() == MouseEvent.MOUSE_CLICKED) {
						for (int i=0; i<gallery.getComponentCount(); i++) {
							Component component = gallery.getComponent(i);
							if (SwingUtilities.isDescendingFrom(me.getComponent(), component)) {
								preview = (JPanel) component;
								break;
							}
						}
					}
					
					if (preview != null && preview.getName() != null) {
						final Integer number = Integer.valueOf(preview.getName());
						if (minimizedDocumentsRestored.indexOf(number) > -1)
							return; // already created
						// select or unselect
						if (minimizedDocumentsSelected.contains(number)) {
							minimizedDocumentsSelected.remove(number);
							preview.setBorder(borderUnselected);
						} else {
							minimizedDocumentsSelected.add(number);
							preview.setBorder(borderSelected);
						}
					}
					
					// update create button
					buttonRestore.setEnabled(minimizedDocumentsSelected.size() > 0);
				}
			}
		}
	}
}

