package nodebox.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import nodebox.node.Node;
import nodebox.ui.AddressBar;
import nodebox.ui.PaneHeader;
import nodebox.ui.Theme;

public class AlternativePaneHeader extends PaneHeader {

	private final NodeBoxDocument document;
	private final ReferenceToggleButton referenceToggle;
	private final EditableToggleButton editableToggle;
	private final SynchedNodePositionButton synchedNodePositionToggle;
	private final SandboxButton sandboxButtonToggle;
	private final DiffToggleButton diffToggle;

	private final JPanel leftPanel;
	private final JPanel rightPanel;
	private final JButton closeButton;
	private final JButton minimizeButton;
	
    private final ProgressPanel progressPanel;
	private final AddressBar addressBar;


	private static final Image checkedImage;
	private static final Image uncheckedImage;
	private static final Image footprintsImage;
	private static final Image referenceImage;
	private static final Image historyImage;
	private static final Image sandboxImage;
	private static final Image diffImage;
	private static final Image hikerImage;


	static {
		try {
			checkedImage = ImageIO.read(AddressBar.class.getResourceAsStream("/checked.png"));
			uncheckedImage = ImageIO.read(AddressBar.class.getResourceAsStream("/unchecked.png"));
			sandboxImage = ImageIO.read(AddressBar.class.getResourceAsStream("/sandbox.png"));
			footprintsImage = ImageIO.read(AddressBar.class.getResourceAsStream("/footprints.png"));
			referenceImage = ImageIO.read(AddressBar.class.getResourceAsStream("/diff.png"));
			historyImage = ImageIO.read(AddressBar.class.getResourceAsStream("/history-clock.png"));
			diffImage = ImageIO.read(AddressBar.class.getResourceAsStream("/links.png"));
			hikerImage = ImageIO.read(AddressBar.class.getResourceAsStream("/hiker.png"));

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public ReferenceToggleButton getReferenceToggle() {
		return referenceToggle;
	}
	public void setReferenceToggleButtonSelected(boolean set){
		referenceToggle.setSelected(set);
	}

	public ProgressPanel getProgressPanel(){
		return progressPanel;
	}
	
	public AddressBar getAddressBar(){
		return addressBar;
	}

	public AlternativePaneHeader(String title, final NodeBoxDocument document) {
		super(title);
		this.document = document;

		// create left and right side
		leftPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 1));
		rightPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 5, 1));

		leftPanel.setOpaque(false);
		rightPanel.setOpaque(false);
		
        setPreferredSize(new Dimension(9999, 75));
        setMinimumSize(new Dimension(10, 75));
        setMaximumSize(new Dimension(9999, 75));
		
		setBorder(BorderFactory.createEmptyBorder());
		
		//addressBar and progressBar and bottomPanel
        progressPanel = new ProgressPanel(document);
        
        addressBar = new AddressBar();
        addressBar.setOnSegmentClickListener(new AddressBar.OnSegmentClickListener() {
            public void onSegmentClicked(String fullPath) {
                //document.setActiveNetwork(fullPath);
                document.setActiveNetworksEverywhere(fullPath);
            }
        });
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(addressBar, BorderLayout.CENTER);
		bottomPanel.add(progressPanel, BorderLayout.EAST);
		/////////////////////////////////////////////////////
        
		setLayout(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		
		gc.fill = GridBagConstraints.BOTH;
		boolean isFirstRow = document.getAppFrame().getMonitorArrangement() <= 2 || document.getMonitorNo() / document.getAppFrame().getNumCols() == 0;
		
		//if (document.isFirstRow){
			gc.weightx = 1;
			gc.weighty = 1;
			
			gc.gridx = 0;
			gc.gridy = 0;
			
			add(leftPanel,gc);
			
			gc.gridx = 1;
			gc.gridy = 0;
			
			add(rightPanel, gc);
			
			//the bottom panel is borderlayout panel which contains address bar and progress bar
			//it is added as a gridbag element on the second row which spans both columns
			gc.gridx = 0;
			gc.gridy = 1;
			gc.gridwidth = 2;
			
			add(bottomPanel, gc);
			
	        // Animation properties.
	        AnimationBar animationBar = new AnimationBar(document);
	        AnimationManager animationManager = new AnimationManager(document, animationBar);
	        document.setAnimationManager(animationManager);
			gc.gridx = 0;
			gc.gridy = 2;
			gc.gridwidth = 2;
			add(animationBar, gc);
		/*}
		//used to be reversing the order of panels for the bottom row of monitors. we are not doing this anymore for now.
		else{
			//the bottom panel is borderlayout panel which contains address bar and progress bar
			//it is added as a gridbag element on the first row which spans both columns
			
			gc.gridx = 0;
			gc.gridy = 0;
			gc.gridwidth = 2;
			
			add(bottomPanel, gc);
			
			gc.weightx = .5;
			gc.weighty = .5;
			//gc.weighty = .5;
			
			gc.gridx = 0;
			gc.gridy = 1;
			
			add(leftPanel,gc);
			
			gc.gridx = 1;
			gc.gridy = 1;
			
			add(rightPanel, gc);
		}*/
		
		// create buttons
		editableToggle = new EditableToggleButton(new ToggleEditableAction());
		editableToggle.setSelected(false);
		referenceToggle = new ReferenceToggleButton(new SwitchReferenceAction());
		synchedNodePositionToggle = new SynchedNodePositionButton(new ToggleSynchedNodePositionAction());
		synchedNodePositionToggle.setSelected(true);
		closeButton = new JButton(new ImageIcon(AddressBar.class.getResource("/cross.png")));
		closeButton.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		closeButton.setContentAreaFilled(false);
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ApplicationFrame appFrame = document.getAppFrame();
				if (JOptionPane.OK_OPTION == javax.swing.JOptionPane.showConfirmDialog(appFrame, "Are you sure you want to delete this alternative?", "Are you sure you want to delete this alternative?", JOptionPane.OK_CANCEL_OPTION)){
					//delete
					document.closeDocument();
					//log delete
					appFrame.log("Delete alternative", document.getDocumentName(), String.valueOf(document.getDocumentNo()), "mouseClicked");
				}
			}
		});
		
		minimizeButton = new JButton(new ImageIcon(AddressBar.class.getResource("/minimize.png")));
		minimizeButton.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		minimizeButton.setContentAreaFilled(false);
		minimizeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				document.getAppFrame().minimizeAlternative(document);
			}


		});

		sandboxButtonToggle = new SandboxButton(new SandboxAction());
		diffToggle = new DiffToggleButton(new DiffAction());
		// add buttons left panel
		leftPanel.add(editableToggle);
		leftPanel.add(sandboxButtonToggle);
		leftPanel.add(synchedNodePositionToggle);
		leftPanel.add(new HistoryButton(new ImageIcon(historyImage), document));
		leftPanel.add(referenceToggle);
		leftPanel.add(diffToggle);
		diffToggle.setSelected(true);
		diffToggle.setVisible(false); //in the beginning it's not visible
		leftPanel.add(new HikerButton(new ImageIcon(hikerImage), document));

        //disabled animation panel to save vertical space for alternatives


		//rightPanel.add(animationBar);

		// add buttons right panel
		rightPanel.add(minimizeButton);
		rightPanel.add(closeButton);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	

	
	public boolean diffToggleSelected(){
		return diffToggle.isSelected();
	}
	
    public void setDiffButtonToggleSelected(boolean state){
    	diffToggle.setSelected(state);
    }
    
    public void setDiffToggleVisible(boolean set){
    	diffToggle.setVisible(set);
    }
    
    public void setDiffToggleSelected(boolean set){
    	diffToggle.setSelected(set);
    }
	
	public boolean isEditable() {
		return !editableToggle.isSelected();
	}
	
	public boolean isSandboxed() {
		return sandboxButtonToggle.isSelected();
	}

	public boolean synchedNoveMoveIsEnabled() {
		return synchedNodePositionToggle.isSelected();
	}

	public boolean toggleEditableState() {
		boolean newState = editableToggle.isSelected();
		setEditableState(newState);
		return newState;
	}
	
	public boolean toggleSynchedNodePositionState() {
		boolean newState = !synchedNodePositionToggle.isSelected();
		synchedNodePositionToggle.setSelected(newState);
		return newState;
	}

	public void setEditableState(boolean b) {
		editableToggle.setSelected(!b);
		repaintAllViews();
	}
	private void repaintAllViews() {
		// repaint all views to reflect changes
		ApplicationFrame appFrame = document.getAppFrame();
		appFrame.updateGradients();
		lockCleanup();
		appFrame.repaintGlassPane();
	}
	
	public void setSynchedNodePositionState(boolean b) {
		if (synchedNodePositionToggle.isSelected() != b)
			toggleSynchedNodePositionState();
	}

	private void lockCleanup() {
		// this is for undo, to ensure once the user messed around with the
		// layer lock, the edits are no longer combined
		for (NodeBoxDocument doc : document.getDocumentGroup()) {
			doc.stopCombiningEdits();
		}

		// deselect all nodes in network view of the locked alternative (for
		// unlocked, this does nothing) and update all views
		//document.getNetworkView().deselectAll();
		document.getNetworkView().repaint();
		document.getPortView().updateAll();
		document.getViewerPane().getViewer().repaint();
	}


	// private classes
	private class ToggleEditableAction extends AbstractAction {

		private ToggleEditableAction() {
			super("Editable");
		}

		public void actionPerformed(ActionEvent e) {
			document.menuBar.toggleEditableMenuItem();
			document.getAppFrame().disableAllSandboxButtons();
			repaintAllViews();
			
			//log
			ApplicationFrame appFrame = document.getAppFrame();
			appFrame.log(document.getDocumentName(),String.valueOf(document.getDocumentNo()), "Set Editable", String.valueOf(!editableToggle.isSelected()), "Icon");
		}
	}

	private class ToggleSynchedNodePositionAction extends AbstractAction {

		private ToggleSynchedNodePositionAction() {
			super("SynchMove");
		}

		public void actionPerformed(ActionEvent e) {
			document.menuBar.toggleSynchedNodePositionItem();
		}
	}

	private class SandboxAction extends AbstractAction {

		private SandboxAction() {
			super("Sandbox");
		}

		public void actionPerformed(ActionEvent e) {
			ApplicationFrame appFrame = document.getAppFrame();
			boolean checked = sandboxButtonToggle.isSelected();
			appFrame.sandboxSetEnabled(checked);
			appFrame.log(document.getDocumentName(), String.valueOf(document.getDocumentNo()), "Sandbox", String.valueOf(checked), "Icon");
		}
	}
    public void setSandboxButtonToggleEnable(boolean state){
    	sandboxButtonToggle.setSelected(state);
    }

	private class DiffAction extends AbstractAction {

		private DiffAction() {
			super("Links");
		}

		public void actionPerformed(ActionEvent e) {
			document.menuBar.setDiffChecked(diffToggle.isSelected());
			document.getAppFrame().getGlassPane().repaint(); //update glasspane when button is toggled
		}
	}

	private class SwitchReferenceAction extends AbstractAction {

		private SwitchReferenceAction() {
			super("Reference");
		}

		public void actionPerformed(ActionEvent e) {
			ApplicationFrame appFrame = document.getAppFrame();
			int docNum = appFrame.getDocNumByDocPointer(document);
			appFrame.switchReference(docNum);
		}
	}


	private final class ReferenceToggleButton extends JToggleButton {

		private ReferenceToggleButton(Action action) {
			super(action);
			Dimension d = new Dimension(25, 25);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g;
			
			if (isSelected()) {
				g.setColor(Color.white);
				g2.fill(g.getClip());
				setBorder(Theme.INNER_SHADOW_BORDER);
			}
			else{
				setBorder(Theme.EMPTY_BORDER);
			}
			
			g.drawImage(referenceImage, 0, 0, null);
		}
	}

	private final class EditableToggleButton extends JToggleButton {

		private EditableToggleButton(Action action) {
			super(action);
			Dimension d = new Dimension(25, 25);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g;

			if (isSelected()) {
				//g.setColor(Color.green);
				//g2.fill(g.getClip());

			} else {
				g.setColor(Color.white);
				g2.fill(g.getClip());

			}

			if (!isSelected()) {
				setBorder(Theme.INNER_SHADOW_BORDER);
				g.drawImage(checkedImage, 0, 0, 25, 25, null);
			} else {
				setBorder(Theme.EMPTY_BORDER);
				g.drawImage(uncheckedImage, 0, 0, 25, 25, null);
			}
		}

	}

	private final class SynchedNodePositionButton extends JToggleButton {

		private SynchedNodePositionButton(Action action) {
			super(action);
			Dimension d = new Dimension(25, 25);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g;

			if (isSelected()) {
				g.setColor(Color.white);
				g2.fill(g.getClip());
			} else {
				
			}

			if (!isSelected()) {
				setBorder(Theme.EMPTY_BORDER);
				g.drawImage(footprintsImage, 0, 0, 25, 25, null);
			} else {
				setBorder(Theme.INNER_SHADOW_BORDER);
				g.drawImage(footprintsImage, 0, 0, 25, 25, null);
			}
		}

	}
	
	private final class SandboxButton extends JToggleButton {

		private SandboxButton(Action action) {
			super(action);
			Dimension d = new Dimension(25, 25);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g;

			if (isSelected()) {
				g.setColor(Color.white);
				g2.fill(g.getClip());
			} else {
			}

			if (!isSelected()) {
				setBorder(Theme.EMPTY_BORDER);
				g.drawImage(sandboxImage, 0, 0, 25, 25, null);
			} else {
				setBorder(Theme.INNER_SHADOW_BORDER);
				g.drawImage(sandboxImage, 0, 0, 25, 25, null);
			}
		}

	}

	private final class DiffToggleButton extends JToggleButton {

		private DiffToggleButton(Action action) {
			super(action);
			Dimension d = new Dimension(25, 25);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g;

			if (isSelected()) {
				g.setColor(Color.white);
				g2.fill(g.getClip());
			} else {
			}

			if (!isSelected()) {
				setBorder(Theme.EMPTY_BORDER);
				g.drawImage(diffImage, 0, 0, 25, 25, null);
			} else {
				setBorder(Theme.INNER_SHADOW_BORDER);
				g.drawImage(diffImage, 0, 0, 25, 25, null);
			}
		}

	}
	void showHistoryDialog(){
		ApplicationFrame af = document.getAppFrame();

		// check if there is a open history dialog (if there is one close it first)
		if (af.activeHistoryDialog instanceof JDialog)
			af.activeHistoryDialog.dispose();

		// clone document (without adding it to the panel)
		NodeBoxDocument previewDocument = NodeBoxDocument.newInstance(document);
		previewDocument.isHistoryPreviewDocument = true;

		// create history dialog
		af.activeHistoryDialog = new HistoryDialog(document.getDocumentFrame(), document, previewDocument);
		af.activeHistoryDialog.pack();
		af.activeHistoryDialog.setVisible(true);
		
		//set current document to current document:
		//the code in here changes current menubar and that seems to fix the bug of appearing empty reference list
		af.setCurrentDocument(af.getCurrentDocument());
		
		//log
		af.log("History", "show dialog");
	}
	

	private class HistoryButton extends JButton implements ActionListener {

		private NodeBoxDocument document;

		private HistoryButton(ImageIcon icon, NodeBoxDocument document) {
			super(icon);
			this.document = document;
			
			Dimension d = new Dimension(28, 25);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
			
			addActionListener(this);
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					setBorder(UIManager.getBorder("Button.border"));
				}
				@Override
				public void mouseReleased(MouseEvent e) {
					setBorder(BorderFactory.createEmptyBorder());
				}
			});
			setContentAreaFilled(false);
			setBorder(BorderFactory.createEmptyBorder());
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			showHistoryDialog();
		}
	}
	private class HikerButton extends JButton implements ActionListener {

		private NodeBoxDocument document;

		private HikerButton(ImageIcon icon, NodeBoxDocument document) {
			super(icon);
			this.document = document;
			
			Dimension d = new Dimension(28, 25);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
			
			addActionListener(this);
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					setBorder(UIManager.getBorder("Button.border"));
				}
				@Override
				public void mouseReleased(MouseEvent e) {
					setBorder(BorderFactory.createEmptyBorder());
				}
			});
			setContentAreaFilled(false);
			setBorder(BorderFactory.createEmptyBorder());
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			//no power sets if less than 1 document, i.e., if documents are identical
			ArrayList<NodeBoxDocument> docs = new ArrayList<NodeBoxDocument>();
			docs.add(document);
			docs.add(document);
			new GalleryDialog(document.getAppFrame(), docs);
		}
	}
}