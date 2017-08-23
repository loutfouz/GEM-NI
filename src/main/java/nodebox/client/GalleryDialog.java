package nodebox.client;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.MutableComboBoxModel;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataListener;
import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import nodebox.graphics.Point;
import nodebox.node.Connection;
import nodebox.node.Node;
import nodebox.node.Port;
import nodebox.ui.Theme;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;


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
public class GalleryDialog extends JDialog implements ActionListener {

	// dimension for the previews
	public static final int PREVIEW_WIDTH = 180;
	public static final int PREVIEW_HEIGHT = PREVIEW_WIDTH;
	// use the same button size for all buttons
	public static final int BUTTON_WIDTH = 140;
	public static final int BUTTON_HEIGHT = 30;

	// use constants for text
	public static final String BUTTON_CLOSE_TEXT = "Close";
	public static final String BUTTON_TOGGLE_TEXT_1 = "Show gallery";
	public static final String BUTTON_TOGGLE_TEXT_2 = "Show configuration";
	public static final String BUTTON_CREATE_TEXT = "Create";
	public static final String BUTTON_CREATE_AND_CLOSE_TEXT = "Create + Close";
	public static final String BUTTON_PAGE_NEXT_TEXT = "Next >>";
	public static final String BUTTON_PAGE_PREVIOUS_TEXT = "<< Previous";

	public static final String TEXT_PAGES = "%num/%total";
	public static final String TEXT_PREVIEW = "<html><span>Result: <b>%total</b></span></html>";
	public static final String TEXT_MASTER_SELECTION = "Network: ";

	// reference to the application frame that called the gallery dialog
	private ApplicationFrame appFrame;
	
	// selected documents
	private ArrayList<NodeBoxDocument> documents;
	
	//the file that contains both powerSets and selectedDocuments
	private ArrayList<NodeBoxDocument> powerSetAndSelectedDocuments;
	
	// the master document is always a copy from one of the selected documents
	// in the beginning the first document will be the master but the user
	// can change the master afterwards
	private NodeBoxDocument master = null;

	// the selected nodes that are available in all documents
	// each entry in the array list means one document. for each document
	// we always save all nodes in a normal array. Many nodes will be the 
	// same but some are different. Anyway, with this structure we can 
	// easily process everything.
	private ArrayList<Node[]> nodeIntersection;
	// the selected nodes that are available in all documents and also the same 
	private ArrayList<Node[]> nodeIntersectionSame;
	// the selected nodes that are available in all documents but who are different
	private ArrayList<Node[]> nodeIntersectionDiffer;
	
	// the final Cartesian result based on the tree selection
	private ArrayList<List<CartesianNode>> cartesianProduct = new ArrayList<List<CartesianNode>>();

	// gallery panel
	private Gallery gallery;
	// gallery configurator panel (JTree)
	private GalleryConfigurator galleryConfigurator;

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
	private JButton buttonToggle;
	private JButton buttonCreate;
	private JButton buttonCreateClose;
	private JButton buttonPageNext;
	private JButton buttonPagePrevious;
	
	private JComboBox<String> comboBoxMasterSelection;
	
	private JLabel pageNumber;
	private JLabel previewNumber;
	
	private Dimension buttonSize;
	private Dimension preferredSize;
	private Dimension contentPaneSize; 
	private Dimension buttonPanelSize;
	private Dimension buttonRow1PanelSize;
	private Dimension buttonRow2PanelSize;
	
	// save tree in class because we have to access the tree from different places
	private JTree tree;
	// shows if the tree has changed (we have to reload the Cartesian product if this happened)
	private boolean treeChanged;
	
	//also do power sets on graphs
	boolean doPowerSets = false;


	public GalleryDialog(final ApplicationFrame appFrame, ArrayList<NodeBoxDocument> _documents) {
		super(appFrame);
		
		this.appFrame = appFrame;
		documents = _documents;
		
		if (documents.size() < 1) {
			JOptionPane.showMessageDialog(appFrame, "Please select at least one alternative.", "NodeBox", JOptionPane.ERROR_MESSAGE);
			return;
		} else if (documents.size() <= 1){ // if we only have one document use the same document twice!
			documents = new ArrayList<NodeBoxDocument>();
			documents.add(_documents.get(0));
			documents.add(_documents.get(0));
		}

		//do the power set stuff
		doPowerSets = Application.ENABLE_PRODUCT_OF_GRAPHS;
		//if only one document then we can't do power sets
		if (doPowerSets && (documents.size() == 2 && documents.get(0) == documents.get(1) || documents.size() < 2))
			doPowerSets = false;
		initiliazePowerSets();

		//if no powersets then just do calculation on selected documents only
		if (!doPowerSets)
			powerSetAndSelectedDocuments  = documents;
		
		// in the beginning set the first document as master
		setMasterDocument(0);
		
		// prepare list for combo box
		ArrayList<String> comboBoxElements = new ArrayList<String>();
		for (NodeBoxDocument d : this.powerSetAndSelectedDocuments)
			comboBoxElements.add(d.getDocumentName());
		
		// identify selected nodes that are the same in all documents
		setIntersection();
		// check if these nodes are similar in terms of port values
		setIntersectionTypes();
		
		if (nodeIntersection.size() <= 0) {
			JOptionPane.showMessageDialog(appFrame, "Please select at least one node that exists in both alternatives.", "NodeBox", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		/////
		// from now we have a lot of swing stuff!
		/////
		
		// get monitor height and width (always use the first monitor, since they are all equally sized)
		JPanel monitorPanel = (JPanel) appFrame.monitorPanel.getComponent(appFrame.getMonitorForDocument(documents.get(0)));
		final int width = monitorPanel.getWidth();
		final int height = monitorPanel.getHeight();

		// set dimensions for the dialog and other components
		final Dimension minimumSize = new Dimension(600, 600);
		preferredSize = new Dimension(width - 20, height - 20);
		contentPaneSize = new Dimension(preferredSize.width - 40, preferredSize.height - 40);
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
		gallery = new Gallery(false);
		galleryConfigurator = new GalleryConfigurator(appFrame, true);

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

		comboBoxMasterSelection = new JComboBox<String>(comboBoxElements.toArray(new String[comboBoxElements.size()]));

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
		galleryConfiguratorPanel.add(galleryConfigurator);

		// add buttons
		buttonClose = new JButton(BUTTON_CLOSE_TEXT);
		buttonToggle = new JButton(BUTTON_TOGGLE_TEXT_1);
		buttonCreate = new JButton(BUTTON_CREATE_TEXT);
		buttonCreateClose = new JButton(BUTTON_CREATE_AND_CLOSE_TEXT);
		buttonPageNext = new JButton(BUTTON_PAGE_NEXT_TEXT);
		buttonPagePrevious = new JButton(BUTTON_PAGE_PREVIOUS_TEXT);
		
		buttonCreate.setEnabled(false);
		buttonPageNext.setEnabled(false);
		buttonPagePrevious.setEnabled(false);
		
		buttonClose.addActionListener(this);
		buttonToggle.addActionListener(this);
		buttonCreate.addActionListener(this);
		buttonCreateClose.addActionListener(this);
		buttonPageNext.addActionListener(this);
		buttonPagePrevious.addActionListener(this);
		
		buttonClose.setPreferredSize(buttonSize);
		buttonToggle.setPreferredSize(buttonSize);
		buttonCreate.setPreferredSize(buttonSize);
		buttonCreateClose.setPreferredSize(buttonSize);
		buttonPageNext.setPreferredSize(buttonSize);
		buttonPagePrevious.setPreferredSize(buttonSize);
		comboBoxMasterSelection.setPreferredSize(new Dimension(((buttonSize.width * 2) + 25), buttonSize.height));

		pageNumber.setHorizontalAlignment(JLabel.CENTER);
		
		pageNumber.setVisible(false);
		buttonCreateClose.setVisible(false);
		buttonPageNext.setVisible(false);
		buttonPagePrevious.setVisible(false);

		buttonRow1LeftPanel.add(buttonToggle);
		buttonRow1CenterPanel.add(buttonPagePrevious);
		buttonRow1CenterPanel.add(pageNumber);
		buttonRow1CenterPanel.add(buttonPageNext);
		buttonRow1RightPanel.add(buttonCreate);

		buttonRow2LeftPanel.add(previewNumber);
		buttonRow2CenterPanel.add(new JLabel(TEXT_MASTER_SELECTION));
		buttonRow2CenterPanel.add(comboBoxMasterSelection);
		buttonRow2RightPanel.add(buttonClose);
		buttonRow2RightPanel.add(buttonCreateClose);

		buttonRow1Panel.add(buttonRow1LeftPanel);
		buttonRow1Panel.add(buttonRow1CenterPanel);
		buttonRow1Panel.add(buttonRow1RightPanel);
		buttonRow2Panel.add(buttonRow2LeftPanel);
		buttonRow2Panel.add(buttonRow2CenterPanel);
		buttonRow2Panel.add(buttonRow2RightPanel);

		// initial Cartesian product
		updateCartesianProduct();
		
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
				galleryConfigurator.getScrollPane().setPreferredSize(size);
				galleryConfigurator.getScrollPane().revalidate();
				galleryConfigurator.getScrollPane().repaint();
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
		
		comboBoxMasterSelection.addActionListener(new ActionListener() {
			/**
			 * Change master document!
			 */
			@Override
			public void actionPerformed(ActionEvent event) {
				// change master document
				setMasterDocument(comboBoxMasterSelection.getSelectedIndex());
				appFrame.log("Design gallery", "reference selection", String.valueOf(comboBoxMasterSelection.getSelectedIndex()), comboBoxMasterSelection.getSelectedItem().toString());
			}
		});

		add(contentPanel, BorderLayout.CENTER);
		pack();
		setVisible(true);
		
		resetViewRelativeToFirstDocumentInTheWorkspace();
	}
	
	/**
	 * The method populates power sets
	 */
	private void initiliazePowerSets(){
		if (!doPowerSets) return;
		
		Set<Node> newNodes = new HashSet<Node>();
		Set<Node> missingNodes= new HashSet<Node>();
		Set<Node> diffGroupNodes= new HashSet<Node>();
		
		//1 is compared, 0 is reference
		NodeBoxDocument referenceDocument =  NodeBoxDocument.newInstance(documents.get(0));
		referenceDocument.ungroup();

		//go through all the documents selected for Cartesian product and add all new and missing nodes in relative to hte compared document (which is the last document)
		for (int i = 1; i < documents.size(); i++){
			NodeBoxDocument comparedDocument =  NodeBoxDocument.newInstance(documents.get(i));
			comparedDocument.ungroup();
			
			Set<Node> tempNewNodes = new HashSet<Node>();
			Set<Node> tempMissingNodes = new HashSet<Node>();
			Set<Node> tempDiffGroupNodes = new HashSet<Node>();
			
			findNodeDifference(tempNewNodes, tempDiffGroupNodes, referenceDocument, comparedDocument);
			diffGroupNodes.addAll(tempDiffGroupNodes); 
			findNodeDifference(tempMissingNodes, null, comparedDocument, referenceDocument);
			
			for (Node node : tempNewNodes)
				comparedDocument.addNodesAndConnectors(referenceDocument, node);
			
			newNodes.addAll(tempNewNodes);
			missingNodes.addAll(tempMissingNodes);
		}
		referenceDocument.resetAllUndo();

		//now we gotta do the power set thingie
		//creating the set containing all names of nodes missing + new (difference nodes)
		 Set<Node> setOfDiffNodes = new HashSet<Node>();


		 for (Node node : newNodes){
			 setOfDiffNodes.add(node);
		 }
			 
		 for (Node node : missingNodes){
			 setOfDiffNodes.add(node);
		 } 
		 
		 for (Node node : diffGroupNodes){
			 setOfDiffNodes.add(node);
		 }

		 //calculate the powerSet
		 Set<Set<Node>> powerSetOfDiffNodes = powerSet(setOfDiffNodes);
		 final int powerSetSize = powerSetOfDiffNodes.size();
		 
		 //create powerSet document array and populate it
		 //documents generated from powerSets
		 ArrayList<NodeBoxDocument> powerSetDocuments = new ArrayList<NodeBoxDocument>();
		 int count = 0;
		 Iterator<Set<Node>> iter = powerSetOfDiffNodes.iterator();
		 while (iter.hasNext()){
			 Set<Node> entry = iter.next();
			 //first of all discard all entries if you find more than one network node with same uuid, e.g., [<Node network1:core/zero>, <Node network1:core/zero>]
			 //discard these entries by skipping
			 Hashtable<UUID, Integer> networkNodeOccurances = new Hashtable<UUID, Integer>();
			 for (Node node : entry){
				 if (node.getPrototype().getName().equals("network")){
					UUID uuid = node.getUUID();
					//if this node is already there
					if (networkNodeOccurances.containsKey(uuid)){
						Integer value = networkNodeOccurances.get(uuid);
						networkNodeOccurances.put(uuid, value + 1);
					}
					else//it's not there, so put a one there
						networkNodeOccurances.put(uuid, 1);
				 } 
			 }
			 //System.out.println("occurances:" + networkNodeOccurances);
			 boolean containsDuplicates = false;
			 for (Integer i : networkNodeOccurances.values()){
				 if (i > 1){
					 containsDuplicates = true;
					 break;
				 }
			 }
			 //if not all network (group) nodes are unique, don't consider this set
			 if (containsDuplicates){
				 continue;
			 }
			 
			 NodeBoxDocument doc = NodeBoxDocument.newInstance(referenceDocument);
			 doc.ungroup();
			 powerSetDocuments.add(doc);
			 
			 for (Node node : entry){
				 //Node node = doc.getNetworkView().getNodeByName(nodeName);
				 //if not a network (group) node
				 if (!node.getPrototype().getName().equals("network"))
					 doc.deleteAndReconnect(node);
				 else{ //this is a network/group node
					 //replace the network Node with the one from compared view
					Node nodeInDoc = doc.getActiveNetwork().getChild(node.getName());
					//always replace in the root "/"
					doc.getController().replaceNodeInPath(Node.path("/", nodeInDoc), node);
				 }
			 }

			 //now let's make a name for it which contains names from set differences
			 String strDocName = count + ".";
			 Set<Node> nodeSet = new HashSet<Node>();
			 Set<Node> groupNodes = new HashSet<Node>();

			 //setOfDiffNodes is all nodes that are different in across all the graphs that are part of the product
			 //entry are only those nodes that are included in current instance of powerset
			 //but setOfDiffNodes also contains groupNodes that are actually included
			 //so we need to extract group nodes from setOfDiffNodes
			 //subtract the nodes that are in the current entry (group nodes will be ignored because they are filtered in the first for loop)
			 //then we add the group nodes in the entry
			 //this is confusing as hell but, hey, it works!
			 for (Node node : setOfDiffNodes){
				 if (!node.getPrototype().getName().equals("network"))
					 nodeSet.add(node);
			 }
			 for (Node node : entry){
				 if (node.getPrototype().getName().equals("network"))
					 groupNodes.add(node);
			 }
			 
			 nodeSet.removeAll(entry);
			 nodeSet.addAll(groupNodes);
			 //if there are no groupNodes in compared docs, then add all group nodes of currentNetwork
			 if (groupNodes.isEmpty()){
				 ImmutableList<Node> nodeList = referenceDocument.getNodeLibrary().getRoot().getChildren();
				 Iterator<Node> iterator = nodeList.iterator();
				 while (iterator.hasNext()) {
		        	Node node = iterator.next();
		        	if (node.getPrototype().getName().equals("network")){
		        		nodeSet.add(node);
		        	}
				 }
			 }
			 for (Node node : nodeSet){
				 String name = node.getName();
				 //if this node is a network node, concatenate some kind of document identifier 
				 if (node.getPrototype().getName().equals("network")){
					 NodeBoxDocument d = NodeBoxDocument.getDocumentFromNode(node, documents);
					 //remove the ndbx from the identifier
					 if (d != null){
						 String identifier = d.getDocumentName().replace(".ndbx","");
						 name += identifier;
					 }
				 }
				 strDocName += name + ".";
			 }
				 
			 //give the power set document a title
			 doc.powerSetDocumentName = strDocName + "ndbx";
			 doc.resetAllUndo();
			 count++;
		 }

		 //copy documents first
		 powerSetAndSelectedDocuments = new ArrayList<NodeBoxDocument>();//[documents.length + powerSetSize];
		 powerSetAndSelectedDocuments.addAll(documents);
		 
		 //then copy powerSet documents
		 powerSetAndSelectedDocuments.addAll(powerSetDocuments);
	}
	/**
	 * Calculates powerSet based on original set
	 * @param originalSet
	 * @return
	 */
	public static <T> Set<Set<T>> powerSet(Set<T> originalSet) {
	    Set<Set<T>> sets = new HashSet<Set<T>>();
	    if (originalSet.isEmpty()) {
	    	sets.add(new HashSet<T>());
	    	return sets;
	    }
	    List<T> list = new ArrayList<T>(originalSet);
	    T head = list.get(0);
	    Set<T> rest = new HashSet<T>(list.subList(1, list.size())); 
	    for (Set<T> set : powerSet(rest)) {
	    	Set<T> newSet = new HashSet<T>();
	    	newSet.add(head);
	    	newSet.addAll(set);
	    	sets.add(newSet);
	    	sets.add(set);
	    }		
	    return sets;
	}
	/**
	 * Find nodes in comparedDocument that are missing in the referenceDocument
	 * @param newNodes Found nodes are stored here
	 * @param referenceDocument source document
	 * @param comparedDocument document against which comparison is made
	 */
	private void findNodeDifference(Set<Node> newNodes, Set<Node> diffGroupNodes,
			NodeBoxDocument referenceDocument, NodeBoxDocument comparedDocument) {
		for (Node comparedNode : comparedDocument.getNodeLibrary().getRoot().getChildren()){ //for all nodes in compared document
			boolean foundSameNode = false;
			for (Node referenceNode : referenceDocument.getNodeLibrary().getRoot().getChildren()){ //for all nodes in reference document
				if (comparedNode.getUUID().equals(referenceNode.getUUID())){
					foundSameNode = true;
					if (diffGroupNodes != null && comparedNode.getPrototype().getName().equals("network")){ //if this is a group node
						if (!NetworkView.subnetsEqual(comparedNode, referenceNode)){
							diffGroupNodes.add(comparedNode);
						}
					}
					break; //node was matched
				}
			}
			if (!foundSameNode){
				newNodes.add(comparedNode);
			}
		}
	}
	/**
	 * Changing the master means we change the active network and
	 * therefore we also have to update the gallery.
	 * 
	 * The tree will be same and also the Cartesian product since we
	 * only consider nodes that are available in all documents!
	 * 
	 * @param index Which document should be now the new master
	 */
	private void setMasterDocument(int index) {
		if (index < 0 || index >= powerSetAndSelectedDocuments.size())
			return;

		NodeBoxDocument oldMaster = null;
		if (master != null)
			oldMaster = master;
		master = NodeBoxDocument.newInstance(powerSetAndSelectedDocuments.get(index));
		master.ungroup();
		if (oldMaster != null) {
			master.setDocumentGroup(oldMaster.getDocumentGroup());
			// also update gallery if we had a previous master document
			gallery.clear();
			gallery.update(true);
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		JButton button = (JButton) e.getSource();

		if (button.equals(buttonToggle)) {
			switch (buttonToggle.getText()) {
			case BUTTON_TOGGLE_TEXT_1: // show gallery
				boolean forceUpdate = false;
				gallery.setVisible(true);
				galleryConfigurator.setVisible(false);
				pageNumber.setVisible(true);
				buttonCreateClose.setVisible(true);
				buttonPageNext.setVisible(true);
				buttonPagePrevious.setVisible(true);
				buttonClose.setVisible(false);
				if (treeChanged) {
					gallery.clear();
					treeChanged = false;
					forceUpdate = true;
				}
				gallery.update(forceUpdate);
				appFrame.log("Design gallery", BUTTON_TOGGLE_TEXT_1);
				break;
			case BUTTON_TOGGLE_TEXT_2: // show configuration
				gallery.setVisible(false);
				galleryConfigurator.setVisible(true);
				pageNumber.setVisible(false);
				buttonCreateClose.setVisible(false);
				buttonPageNext.setVisible(false);
				buttonPagePrevious.setVisible(false);
				buttonClose.setVisible(true);
				appFrame.log("Design gallery", BUTTON_TOGGLE_TEXT_2);
				break;
			}
			// toggle button text
			buttonToggle.setText(buttonToggle.getText().equals(BUTTON_TOGGLE_TEXT_1) ? BUTTON_TOGGLE_TEXT_2 : BUTTON_TOGGLE_TEXT_1);
		}
		
		if (button.equals(buttonClose)){
			this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			appFrame.log("Design gallery", buttonClose.getText(), "no creation");
		}
		
		if (button.equals(buttonPageNext)){
			gallery.nextPage();
			appFrame.log("Design gallery", buttonPageNext.getText());
		}
		
		if (button.equals(buttonPagePrevious)){
			gallery.previousPage();
			appFrame.log("Design gallery", buttonPagePrevious.getText());
		}
		
		if (button.equals(buttonCreateClose) || button.equals(buttonCreate)) {
			HashSet<NodeBoxDocument> set = new HashSet<NodeBoxDocument>(); //used for logging
			for (NodeBoxDocument document : gallery.getSelectedDocuments()) {
				// check if this network already exists, skip existing ones
				boolean exists = false;
				for (NodeBoxDocument doc : appFrame.getDocumentGroup()) {
					if (sameNetwork(doc.getActiveNetwork(), document.getActiveNetwork())) {
						exists = true;
						break;
					}
				}

				// mark document as created
				gallery.setDocumentAsCreated(document);

				if (exists)
					continue;

				// create a new instance from the document (only if it not already exists)
				document.ungroup(); // remove document from Cartesian product group
				document = NodeBoxDocument.newInstance(document); // create new instance
				document.setActiveNetwork(document.getActiveNetworkPath());
				appFrame.addNewDocument(appFrame.getCurrentMonitor(), document);

				//setRenderedNode in this newly created document to be the same node as in master
				UUID nodeInMasterUUID  = master.getActiveNetwork().getRenderedChild().getUUID();
				Node nodeInDocument = NodeBoxDocument.getNodeByUUIDfromDoc(document, nodeInMasterUUID);
				document.setRenderedNode(nodeInDocument, false);
				set.add(document);
			}
			
			// update the application after we created new alternatives
			appFrame.updateAlternativesPanels(true);
			appFrame.buildReferenceMenus();
			
			//re-set the rendered node, so that the view can be centered and zoomed
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					//center and zoom relative to all
					resetViewRelativeToFirstDocumentInTheWorkspace();
				}
			});
			
			// close dialog?
			if (button.equals(buttonCreateClose))
				this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)); 

			logCreatedAlternatives(button.getText(), set);
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

	private void logCreatedAlternatives(String text,
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

		appFrame.log("Design gallery", text, newDocsNames, newDocsNumbers);
	}


	/**
	 * Identifies the selected nodes that are available in all documents.
	 * 
	 *  These nodes are our base for the Cartesian product.
	 */
	private void setIntersection() {
		// our intersection
		nodeIntersection = new ArrayList<Node[]>();

		// use documents and get selected nodes
		List<Node> nodesSelected = new ArrayList<Node>();

		// collect selected nodes from the first selected document
		/*for (Node node : documents.get(0).getNetworkView().getSelectedNodes())
			nodesSelected.add(node);

        logNodesSelected(nodesSelected); //log nodesSelected
		// no node selected? select all
		if (nodesSelected.size() <= 0)*/
		
		nodesSelected = documents.get(0).getNetworkView().getActiveNetwork().getChildren();

		// check which nodes are available in all documents
		boolean[] sameNode;
		for (Node node : nodesSelected) {
			sameNode = new boolean[documents.size()]; // one boolean for each document
			Node[] nodes = new Node[documents.size()]; // declare since we need always a new variable/reference

			for (int i=0; i<documents.size(); i++) {
				sameNode[i] = false;
				for (Node n : documents.get(i).getNetworkView().getActiveNetwork().getChildren()) {
					if (node.getUUID().equals(n.getUUID())) {
						nodes[i] = n; // add nodes, then we have a node list for later
						sameNode[i] = true;
						break;
					}
				}
			}
			
			// check if all documents have the same node
			boolean check = true;
			for (boolean b : sameNode) {
				if (!b) {
					check = false;
					break;
				}
			}

			// if node is in all documents available collect
			if (check)
				nodeIntersection.add(nodes);
		}
	}

	private void logNodesSelected(List<Node> nodesSelected) {
		String nodes = "nodes" + ApplicationFrame.attributeDelimiter;
		for (Node node : nodesSelected){
			nodes += node.getName() + ApplicationFrame.itemDelimiter;
		}
		//remove last delimiter character
		nodes = nodes.substring(0, nodes.length() - 1);
		
		String strNodesSelected = "nodes selected" + ApplicationFrame.attributeDelimiter + String.valueOf(nodesSelected.size());
        
		appFrame.log("Design gallery", strNodesSelected, nodes); //log
	}
	
	/**
	 * Identifies selected nodes that have the same port values and
	 * the one that have different port values.
	 */
	private void setIntersectionTypes() {
		nodeIntersectionSame = new ArrayList<Node[]>();
		nodeIntersectionDiffer = new ArrayList<Node[]>();
		
		for (Node[] nodes : nodeIntersection) {
			// check all nodes against each other
			boolean differs = false;
			for (int i=0; i<nodes.length; i++) {
				Node node1 = nodes[i];
				for (int j=(i + 1); j<nodes.length; j++) {
					Node node2 = nodes[j];
					// by checking each port check if at least one port differs
					for (int k=0; k<node1.getInputs().size(); k++) {
						Port port1 = node1.getInputs().get(k);
						Port port2 = node2.getInputs().get(k);

						if (!port1.equals(port2)) {
							differs = true;
							i = j = nodes.length; // force loops to stop
							break;
						}
					}
				}
			}

			if (differs)
				nodeIntersectionDiffer.add(nodes);
			else
				nodeIntersectionSame.add(nodes);
		}
	}
	
	/**
	 * Checks if the given ports are all the same.
	 * 
	 * @param ports
	 * @return boolean all ports are the same?
	 */
	private static boolean samePorts(Port[] ports) {
		for (int i=0; i<ports.length; i++)
			for (int j=(i + 1); j<ports.length; j++)
				if (!ports[i].equals(ports[j]))
					return false;
		return true;
	}
	
	/**
	 * Checks if the networks are the same. The node is the root node of the network.
	 * 
	 * @param n1 Node 1
	 * @param n2 Node 2
	 * @return
	 */
	private static boolean sameNetwork(Node n1, Node n2) {
		// same amount of nodes?
		if (n1.getChildren().size() != n2.getChildren().size())
			return false;

		// same nodes and parameters?
		HashMap<Node, Node> same = new HashMap<Node, Node>();
		for (Node node1 : n1.getChildren())
			for (Node node2: n2.getChildren())
				if (node1.getUUID().equals(node2.getUUID())) {
					// check parameters
					for (int i=0; i<node1.getInputs().size(); i++) {
						Port p1 = node1.getInputs().get(i);
						Port p2 = node2.getInputs().get(i);
						if (!p1.stringValue().equals(p2.stringValue()))
							return false;
					}
					same.put(node1, node2);
				}

		if (same.size() != n1.getChildren().size())
			return false;

		return true;
	}

	/**
	 * Gets all values from the configuration tree and depending on the concrete
	 * parameter type we have either a range or a set. Whereas a range could be seen
	 * as a set as well, that's true. But in our terminology set means the different
	 * parameter values from all selected documents. Depending on how many documents 
	 * are selected and if they have all different port values the set could be either
	 * only one value (all ports are the same in all documents) or the number of ports
	 * that are different (all documents that have a different value for the port).
	 * 
	 * @param tree
	 * @return hash map Each node that has a configuration together with the actual configuration
	 */
	private static HashMap<Node, ArrayList<TreeNodeObject>> getConfigurationFromTree(JTree tree) {
		HashMap<Node, ArrayList<TreeNodeObject>> nodePortRanges = new HashMap<Node, ArrayList<TreeNodeObject>>(); 
		ArrayList<TreeNodeObject> treeNodeObjects; 
		TreeModel treeModel = tree.getModel();

		for (int i=0; i<treeModel.getChildCount(treeModel.getRoot()); i++) {
			treeNodeObjects = new ArrayList<TreeNodeObject>();
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) treeModel.getChild(treeModel.getRoot(), i);
			Object userObject = node.getUserObject();
			TreeNodeObjectVector<TreeNodeObject> treeNodeObjectVector = (TreeNodeObjectVector<TreeNodeObject>) userObject;
			// add relevant tree node objects to a list
			if (treeNodeObjectVector.isSelected())
				for (TreeNodeObject treeNodeObject : treeNodeObjectVector)
					if (treeNodeObject.isSelected())
						if (treeNodeObject.changes())
							treeNodeObjects.add(treeNodeObject);
			if (treeNodeObjects.size() > 0)
				nodePortRanges.put(treeNodeObjectVector.getNode1(), treeNodeObjects);
		}

		return nodePortRanges;
	}
	
	/**
	 * Uses the tree configuration from this.getConfigurationFromTree() and builds on this
	 * configuration the actual Cartesian product. We use guava's Cartesian creation methods.
	 * 
	 * @param treeConfiguration
	 * @param original
	 * @return
	 */
	private static ArrayList<List<CartesianNode>> buildCartesianProduct(HashMap<Node, ArrayList<TreeNodeObject>> treeConfiguration, NodeBoxDocument original) {
		// Cartesian: ( Nodes * Nodes ) * ( Changed Ports * Changed Ports ) * ( Range )
		ArrayList<Set<CartesianNode>> result = new ArrayList<Set<CartesianNode>>();
		ArrayList<TreeNodeObject> portRanges;
		Set<CartesianNode> nodeResult;

		for (Node node : treeConfiguration.keySet()) {
			nodeResult = new HashSet<CartesianNode>();
			portRanges = treeConfiguration.get(node);

			// prepare data for building the Cartesian product
			ArrayList<Set<CartesianNodePort>> portResult = new ArrayList<Set<CartesianNodePort>>(); 
			
			// prepare range objects
			ArrayList<CartesianNodePort> cartesianNodePort;
			for (TreeNodeObject object : portRanges) {
				cartesianNodePort = new ArrayList<CartesianNodePort>();
				for (Object value : object.getParameterValues())
					cartesianNodePort.add(new CartesianNodePort(object.getPorts(false)[0], value));
				portResult.add(new HashSet<CartesianNodePort>(cartesianNodePort));
			}

			// building Cartesian product for this node
			for(List<CartesianNodePort> list : Sets.cartesianProduct(portResult))
				nodeResult.add(new CartesianNode(node, list));

			// some nodes have no parameters ... we have to avoid empty sets otherwise the whole
			// set will be empty
			if (nodeResult.size() > 0)
				result.add(nodeResult);
		}

		// build the final Cartesian product
		return new ArrayList<List<CartesianNode>>(Sets.cartesianProduct(result));
	}
	
	/**
	 * Updates the Cartesian product and also updates some values that are necessary to set
	 * if we want to update the gallery etc.
	 */
	private void updateCartesianProduct() {
		HashMap<Node, ArrayList<TreeNodeObject>> nodePortRanges = getConfigurationFromTree(tree);
		cartesianProduct = buildCartesianProduct(nodePortRanges, master);
		final String text = TEXT_PREVIEW.replace("%total", String.valueOf(cartesianProduct.size()));
		previewNumber.setText(text);
		treeChanged = true;
	}
	
	
	/**
	 * The Cartesian node class is used for the actual Cartesian product. We encapsulate the
	 * values from the node parameters in own objects. With the help of these objects we create
	 * the product because all objects are different. the actual parameter values are objects
	 * again. See CartesianNodePort class. 
	 */
	private static class CartesianNode {
		private Node node;
		private List<CartesianNodePort> values;
		
		public CartesianNode(Node node, List<CartesianNodePort> values) {
			this.node = node;
			this.values = values;
		}

		public Node getNode() {
			return node;
		}

		public List<CartesianNodePort> getValues() {
			return values;
		}
		
		@Override
		public String toString() {
			return node.getName() + ": " + values;
		}
	}
	
	
	/**
	 * See CartesianNode class 
	 */
	private static class CartesianNodePort {
		Port port;
		Object value;
		
		public CartesianNodePort(Port port, Object value) {
			this.port = port;
			this.value = value;
		}

		public Port getPort() {
			return port;
		}

		public Object getValue() {
			return value;
		}
		
		@Override
		public String toString() {
			return port.getName() + ": " + value;
		}
	}
	

	/**
	 * The gallery configuration panel includes a scroll pane and also the tree. The
	 * tree is inside the scroll pane. On top we have again many swing components and
	 * especially the JTree is customized and you can find many inner classes in this
	 * class.  
	 */
	private class GalleryConfigurator extends JPanel {
		private ApplicationFrame appFrame;

		// the position of each component inside the tree node (actual a tree node is a JPanel)
		public static final String COMPONENT_NAME_CHECKBOX = "checkbox";
		public static final String COMPONENT_NAME_FIELD_START = "fieldStart";
		public static final String COMPONENT_NAME_FIELD_END = "fieldEnd";
		public static final String COMPONENT_NAME_FIELD_STEP = "fieldStep";
		public static final String COMPONENT_NAME_LABEL_TO = "labelTo";
		public static final String COMPONENT_NAME_LABEL_BY = "labelBy";
		public static final String COMPONENT_NAME_COMBOBOX = "combobox";
		public static final String COMPONENT_NAME_PANEL_RANGE = "range";
		public static final String COMPONENT_NAME_PANEL_CARTESIAN = "cartesian";
		public static final String COMPONENT_NAME_LABEL_TOGGLE = "typeToggler";

		public static final String COMPONENT_TEXT_LABEL_RANGE = "Use Range";
		public static final String COMPONENT_TEXT_LABEL_CARTESIAN = "Use Set";
		
		public final Dimension COMPONENT_PANEL_VALUES_SIZE = new Dimension(300, 25);
		
		private JScrollPane scrollPane;

		private UIDefaults uidefs = UIManager.getLookAndFeelDefaults();
			
		
		/**
		 * The constructor already creates the tree and everything. The tree shows always
		 * all selected nodes that are available in all selected documents. The tree also
		 * selects already the nodes and parameters that are different. 
		 * 
		 * @param appFrame Reference
		 * @param visible Panel visible in the beginning
		 */
		public GalleryConfigurator(ApplicationFrame appFrame, boolean visible) {
			setVisible(visible);
			this.appFrame = appFrame;

			// create a tree from nodes and parameters
			boolean selected;
			String maxText = "";
			TreeNodeObjectVector<TreeNodeObject> treeNode;
			Vector<TreeNodeObjectVector<TreeNodeObject>> treeNodes = new Vector<TreeNodeObjectVector<TreeNodeObject>>();
			for (Node[] nodes : nodeIntersection) {
				selected = nodeIntersectionDiffer.contains(nodes);
				// pick the first node as reference ... it doesn't really matter which one because they are the "same"
				Node node = nodes[0];

				treeNode = new TreeNodeObjectVector<TreeNodeObject>(node.getName(), nodes);
				treeNode.setSelected(selected);
				
				// get all parameters from node
				for (Port port : node.getInputs()) {
					// collect the same port from all nodes
					Port[] ports = new Port[nodes.length];
					for (int i=0; i<nodes.length; i++)
						ports[i] = nodes[i].getInput(port.getName());
					// create new tree object for this port
					treeNode.add(new TreeNodeObject(port.getLabel(), ports, treeNode, samePorts(ports)));
					// identify longest text for setting the right width for the components
					if (port.getLabel().length() > maxText.length())
						maxText = port.getLabel();
				}

				treeNodes.add(treeNode);
			}
			
			// how long will be our longest text? We need this number for the tree to know how long our fields will be
			Font font = uidefs.getFont("Label.font");
			FontMetrics metrics = Application.getInstance().getCurrentAppFrame().getGraphics().getFontMetrics(font);
			int maxTextLength = metrics.stringWidth(maxText);
			// add some pixel for the check box
			maxTextLength += 40;

			// create the tree nodes with 
			tree = new JTree(treeNodes);

			TreeNodeRender treeNodeRenderer = new TreeNodeRender(maxTextLength);
			TreeNodeEditor treeNodeEditor = new TreeNodeEditor(tree, treeNodeRenderer, treeNodeRenderer.getAssembledTreeNode());
			TreeNodeMouseListener treeNodeMouseListener = new TreeNodeMouseListener(tree);

			tree.setEditable(true);
			tree.setCellEditor(treeNodeEditor);
			tree.setCellRenderer(treeNodeRenderer);
			tree.addMouseListener(treeNodeMouseListener);
			tree.setRowHeight(30);

			// automatically expand changed nodes and find check box with max length
			DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
			for (int i=0; i<model.getChildCount(model.getRoot()); i++) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) model.getChild(model.getRoot(), i);
				Object userObject = node.getUserObject();

				if (userObject instanceof TreeNodeObjectVector) {
					TreeNodeObjectVector<TreeNodeObject> treeNodeObjectVector = (TreeNodeObjectVector<TreeNodeObject>) userObject;
					if (treeNodeObjectVector.isSelected())
						tree.expandPath(new TreePath(node.getPath()));
					// set tree path to user object
					for (int j=0; j<node.getChildCount(); j++) {
						DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(j);
						Object childUserObject = child.getUserObject();
						if (childUserObject instanceof TreeNodeObject) {
							TreeNodeObject treeNodeObject = (TreeNodeObject) childUserObject;
							treeNodeObject.setTreePath(new TreePath(child.getPath()));
						}
					}
				}
			}

			scrollPane = new JScrollPane(tree); 
			scrollPane.setPreferredSize(contentPaneSize);
			add(scrollPane);
		}

		public JScrollPane getScrollPane() {
			return scrollPane;
		}
		
		
		/**
		 * Here are classes to customize the JTree. We did it a lot!
		 * 
		 * The following classes are inspired by:
		 *		http://www.java2s.com/Code/Java/Swing-JFC/CheckBoxNodeTreeSample.htm 
		 */
		private class TreeNodeRender extends DefaultTreeCellRenderer {
			private JComponent treeNode;
			private int checkBoxLength = 1;


			public TreeNodeRender(int checkBoxLength) {
				this.checkBoxLength = checkBoxLength;
				this.treeNode = getAssembledTreeNode();
			}
			
			public JPanel getAssembledTreeNode() {
				JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
				JCheckBox checkBox = new JCheckBox();

				JPanel panelRange = getAssembledTreeNodeRange();
				JPanel panelCartesian = getAssembledTreeNodeCartesian();

				JLabel labelToggler = new JLabel(COMPONENT_TEXT_LABEL_RANGE);
				
				panelRange.setOpaque(false);
				panelCartesian.setOpaque(false);
				checkBox.setOpaque(false);
				item.setOpaque(false);
				labelToggler.setOpaque(false);

				panelRange.setName(COMPONENT_NAME_PANEL_RANGE);
				panelCartesian.setName(COMPONENT_NAME_PANEL_CARTESIAN);
				checkBox.setName(COMPONENT_NAME_CHECKBOX);
				labelToggler.setName(COMPONENT_NAME_LABEL_TOGGLE);

				checkBox.setPreferredSize(new Dimension(checkBoxLength, 15));

				Font labelFont = uidefs.getFont("Label.font");
				labelToggler.setFont(new Font(labelFont.getFontName(), Font.PLAIN, labelFont.getSize() - 1));
				labelToggler.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
				labelToggler.setForeground(Color.BLUE);
				labelToggler.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

				item.setPreferredSize(new Dimension(1000, 25));

				item.add(checkBox);
				item.add(panelRange);
				item.add(panelCartesian);
				item.add(labelToggler);

				return item;
			}
			
			/**
			 * Each row in the tree is represented by the JPanel that is composed
			 * by the following method. JTree does not create a new object/panel 
			 * for each row it uses the same object again and again and we have to
			 * make sure that the object is always filled with the right information
			 * and that are only the components visible that should be visible.
			 * 
			 * For filling this object please see fillTreeNode().
			 * 
			 * @return JPanel 
			 */
			private JPanel getAssembledTreeNodeCartesian() {
				JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 1));
				
				JComboBox<ComboBoxPortObject> comboBox = new JComboBox<ComboBoxPortObject>();

				comboBox.setRenderer(new ComboBoxRenderer());
				comboBox.setName(COMPONENT_NAME_COMBOBOX);
				comboBox.setPreferredSize(COMPONENT_PANEL_VALUES_SIZE);
				comboBox.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if (e == null)
							return;
						final JComboBox<ComboBoxPortObject> comboBox = (JComboBox<ComboBoxPortObject>) e.getSource();
						// toggle port value
						((ComboBoxPortObject) comboBox.getModel().getSelectedItem()).togglePort();
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								comboBox.showPopup(); // stay opened
							}
						});
					}
				});
				
				panel.add(comboBox);

				return panel;
			}
			
			private JPanel getAssembledTreeNodeRange() {
				JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 1));
				
				JTextField fieldStart = new JTextField();
				JTextField fieldEnd = new JTextField();
				JTextField fieldStep = new JTextField();
				JLabel labelTo = new JLabel("-");
				JLabel labelBy = new JLabel("by");
				
				// set names to identify the components later
				fieldStart.setName(COMPONENT_NAME_FIELD_START);
				fieldEnd.setName(COMPONENT_NAME_FIELD_END);
				fieldStep.setName(COMPONENT_NAME_FIELD_STEP);
				labelTo.setName(COMPONENT_NAME_LABEL_TO);
				labelTo.setName(COMPONENT_NAME_LABEL_BY);

				fieldStart.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
				fieldEnd.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
				fieldStep.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));

				fieldStart.setPreferredSize(new Dimension(75, 25));
				fieldEnd.setPreferredSize(new Dimension(75, 25));
				fieldStep.setPreferredSize(new Dimension(75, 25));

				fieldStart.getDocument().putProperty("field", COMPONENT_NAME_FIELD_START);
				fieldEnd.getDocument().putProperty("field", COMPONENT_NAME_FIELD_END);
				fieldStep.getDocument().putProperty("field", COMPONENT_NAME_FIELD_STEP);
				
				labelTo.setPreferredSize(new Dimension(28, 25));
				labelBy.setPreferredSize(new Dimension(27, 25));
				
				labelTo.setHorizontalAlignment(JLabel.CENTER);
				labelBy.setHorizontalAlignment(JLabel.CENTER);
				
				fieldStart.setHorizontalAlignment(JTextField.RIGHT);
				fieldEnd.setHorizontalAlignment(JTextField.RIGHT);
				fieldStep.setHorizontalAlignment(JTextField.RIGHT);

				fieldStart.setOpaque(false);
				fieldStep.setOpaque(false);
				fieldEnd.setOpaque(false);
				labelTo.setOpaque(false);
				labelBy.setOpaque(false);

				panel.add(fieldStart);
				panel.add(labelTo);
				panel.add(fieldEnd);
				panel.add(labelBy);
				panel.add(fieldStep);

				return panel;
			}

			/**
			 * Fills the treeNodeComponent (usually created from getAssembledTreeNodeCartesian())
			 * with the information from the user object (usually TreeNodeObject).
			 * 
			 * @param treeNodeComponent
			 * @param userObject
			 * @return JPanel but filled with the information from the userObject
			 */
			public JComponent fillTreeNode(JComponent treeNodeComponent, Object userObject) {
				TreeNodeInterface treeNode;
				if (userObject instanceof TreeNodeInterface)
					treeNode = (TreeNodeInterface) userObject;
				else // for some reasons we got a wrong object ... stop here 
					return treeNodeComponent;

				String label = treeNode.getText();
				
				boolean enabled = treeNode.isEnabled();
				boolean selected = treeNode.isSelected();

				JPanel panelRange = new JPanel();
				JPanel panelCartesian = new JPanel();
				
				JLabel labelToggler = new JLabel();
				
				JCheckBox checkBox = new JCheckBox();
				
				for (int i=0; i<treeNodeComponent.getComponentCount(); i++) {
					JComponent component = (JComponent) treeNodeComponent.getComponent(i);
					if (!(component instanceof JLabel))
						component.setEnabled(enabled);

					if (component.getName().equals(COMPONENT_NAME_PANEL_RANGE))
						panelRange = (JPanel) component;
					if (component.getName().equals(COMPONENT_NAME_PANEL_CARTESIAN))
						panelCartesian = (JPanel) component;
					if (component.getName().equals(COMPONENT_NAME_LABEL_TOGGLE))
						labelToggler = (JLabel) component;
					if (component.getName().equals(COMPONENT_NAME_CHECKBOX)) {
						checkBox = (JCheckBox) component;
						checkBox.setText(label);
						checkBox.setSelected(false); // by default disabled
						if (enabled)
							checkBox.setSelected(selected);
					}
				}

				if (userObject instanceof TreeNodeObject) {
					TreeNodeObject treeNodeObject = (TreeNodeObject) userObject;

					switch (treeNodeObject.getMode()) {
					case TreeNodeObject.MODE_RANGE:
						fillTreeNodeFields(panelRange, treeNodeObject, checkBox);
						panelRange.setVisible(true);
						panelCartesian.setVisible(false);
						labelToggler.setText(COMPONENT_TEXT_LABEL_CARTESIAN);
						break;
					case TreeNodeObject.MODE_CARTESIAN:
						fillTreeNodeComboBox(panelCartesian, treeNodeObject, checkBox);
						panelRange.setVisible(false);
						panelCartesian.setVisible(true);
						labelToggler.setText(COMPONENT_TEXT_LABEL_RANGE);
						break;
					}
					treeNodeComponent.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
					labelToggler.setVisible(true);
				} else {
					// 1-level doesn't show these panels
					panelRange.setVisible(false);
					panelCartesian.setVisible(false);
					labelToggler.setVisible(false);
					// also add more border
					treeNodeComponent.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
				}
				
				return treeNodeComponent;
			}
			
			private void fillTreeNodeFields(JPanel panel, TreeNodeObject treeNodeObject, JCheckBox checkBox) {
				for (int i=0; i<panel.getComponentCount(); i++) {
					JComponent component = (JComponent) panel.getComponent(i);
					
					if (component instanceof JTextField) {
						JTextField textField = (JTextField) component;

						switch (textField.getName()) {
						case COMPONENT_NAME_FIELD_START:	textField.setText(treeNodeObject.getRangeStart());	break;
						case COMPONENT_NAME_FIELD_END:		textField.setText(treeNodeObject.getRangeEnd());	break;
						case COMPONENT_NAME_FIELD_STEP:		textField.setText(treeNodeObject.getStep());		break;
						}

						if (!treeNodeObject.isEnabled()) {
							textField.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
							textField.setEnabled(false);
						} else
							textField.setEnabled(true);
					}
				}
			}
			
			private void fillTreeNodeComboBox(JPanel panel, TreeNodeObject treeNodeObject, JCheckBox checkBox) {
				for (int i=0; i<panel.getComponentCount(); i++) {
					JComponent component = (JComponent) panel.getComponent(i);

					if (component instanceof JComboBox) {
						JComboBox<ComboBoxPortObject> comboBox = (JComboBox<ComboBoxPortObject>) component;
						PortComboBoxModel model = new PortComboBoxModel();
						Port[] ports = treeNodeObject.getPorts(true);
						for (Port port : ports) // get unique ports
							model.addElement(new ComboBoxPortObject(port, treeNodeObject));
						comboBox.setModel(model);
						if (ports.length <= 1) { // fully disable this combo and check box because we have to keep the value
							comboBox.setEnabled(false);
							checkBox.setEnabled(false);
						}
						else {
							comboBox.setEnabled(true);
							checkBox.setEnabled(true);
						}
					}
				}
			}
			
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
				final DefaultTreeCellRenderer ret = (DefaultTreeCellRenderer) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				if (value != null && (value instanceof DefaultMutableTreeNode))
					return fillTreeNode(treeNode, ((DefaultMutableTreeNode) value).getUserObject());
				return ret; // should never happen in our case
			}
		}
		
		
		/**
		 * Also the combo box is customized but not that heavy like the tree. 
		 */
		private class PortComboBoxModel implements MutableComboBoxModel<ComboBoxPortObject> {

			private ArrayList<ComboBoxPortObject> items = new ArrayList<ComboBoxPortObject>();
			private int index = -1;

			
			@Override
			public Object getSelectedItem() {
				if (index > -1)
					return items.get(index);
				return null;
			}

			@Override
			public ComboBoxPortObject getElementAt(int index) {
				if (index <= 0)
					return null;
				return items.get(index - 1);
			}

			@Override
			public int getSize() {
				return (items.size() + 1);
			}

			@Override
			public void addElement(ComboBoxPortObject item) {
				items.add(item);
			}
			
			@Override
			public void setSelectedItem(Object object) {
				if (object instanceof ComboBoxPortObject)
					index = items.indexOf(object);
				else
					index = -1;
			}
			//
			// not in use:
			//
			@Override
			public void insertElementAt(ComboBoxPortObject item, int index) {}
			@Override
			public void removeElement(Object port) {}
			@Override
			public void removeElementAt(int index) {}
			@Override
			public void removeListDataListener(ListDataListener l) {}
			@Override
			public void addListDataListener(ListDataListener l) {}
		}
		
		
		private class ComboBoxPortObject {
			private TreeNodeObject treeNodeObject;
			private Port port;
			
			public ComboBoxPortObject(Port port, TreeNodeObject treeNodeObject) {
				this.treeNodeObject = treeNodeObject;
				this.port = port;
			}

			public TreeNodeObject getTreeNodeObject() {
				return treeNodeObject;
			}

			public Port getPort() {
				return port;
			}

			public boolean isPortSelected() {
				return treeNodeObject.isPortSelected(port);
			}
			
			public Boolean togglePort() {
				// toggle port only if it would be not the last one
				if (treeNodeObject.getSelectedPorts().length <= 1 && isPortSelected()) {
					JOptionPane.showMessageDialog(appFrame, "At least one value has to be selected.", "NodeBox", JOptionPane.ERROR_MESSAGE);
					return null;
				}
				boolean result = treeNodeObject.togglePort(port);
				updateCartesianProduct();
				return result;
			}
		}


		private class ComboBoxRenderer extends JLabel implements ListCellRenderer<Object> {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				// first value is always null
				if (value == null) {
					JLabel label = new JLabel("Select values ...");
					label.setPreferredSize(COMPONENT_PANEL_VALUES_SIZE);
					return label;
				}

				final ComboBoxPortObject portObject = (ComboBoxPortObject) value;
				
				JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
				JLabel text = new JLabel(portObject.getPort().stringValue());
				JCheckBox checkBox = new JCheckBox();
				
				checkBox.setSelected(portObject.isPortSelected());

				panel.add(checkBox);
				panel.add(text);
				
				return panel;
			}
		}
		
		
		private class TreeNodeMouseListener extends MouseAdapter implements MouseListener {
			private JTree tree;
			
			public TreeNodeMouseListener(JTree tree) {
				this.tree = tree;
			}
			
			/**
			 * Use the mouse listener only for clicks on the first level.
			 */
			@Override
			public void mouseClicked(MouseEvent event) {
				TreePath treePath = tree.getPathForLocation(event.getX(), event.getY());
				if (treePath == null || treePath.getPathCount() > 2)
					return;
				selectPath(treePath);
			}
		}


		private class TreeNodeEditor extends AbstractCellEditor implements TreeCellEditor {
			private JTree tree;
			private JPanel treeNode;
			private TreeNodeRender treeNodeRenderer;
			private TreeNodeObject treeNodeObject;
			private TreePath lastTreePath = null;


			public TreeNodeEditor(JTree tree, TreeNodeRender treeNodeRenderer, JPanel treeNode) {
				this.tree = tree;
				this.treeNode = treeNode;
				this.treeNodeRenderer = treeNodeRenderer;

				addListenerRecursive(treeNode);
			}
			
			private void addListenerRecursive(JComponent component) {
				for (int i=0; i<component.getComponentCount(); i++) {
					Component child = component.getComponent(i);
					
					if (child instanceof JTextField) {
						JTextField textField = (JTextField) child;
						textField.getDocument().addDocumentListener(new DocumentListener() {
							@Override
							public void changedUpdate(DocumentEvent event) {
								changed(event);
							}
							@Override
							public void removeUpdate(DocumentEvent event) {
								changed(event);
							}
							@Override
							public void insertUpdate(DocumentEvent event) {
								changed(event);
							}
							private void changed(DocumentEvent event) {
								try {
									// get the new value
									String value = event.getDocument().getText(0, event.getDocument().getLength());
									// create a dummy input field with the value
									JTextField textField = new JTextField();
									textField.setText(value);
									switch ((String) event.getDocument().getProperty("field")) {
									case COMPONENT_NAME_FIELD_START:
										textField.setName(COMPONENT_NAME_FIELD_START);
										break;
									case COMPONENT_NAME_FIELD_END:
										textField.setName(COMPONENT_NAME_FIELD_END);
										break;
									case COMPONENT_NAME_FIELD_STEP:
										textField.setName(COMPONENT_NAME_FIELD_STEP);
										break;
									}
									updateTreeNodeObject((JComponent) textField, treeNodeObject);
								} catch (BadLocationException e) {} // eat
							}
						});
					} else if (child instanceof JCheckBox) {
						JCheckBox checkBox = (JCheckBox) child;
						checkBox.addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent event) {
								JComponent component = (JComponent) event.getSource();
								updateTreeNodeObject(component, treeNodeObject);
							}
						});
					} else if (child instanceof JLabel && child.getName() != null && child.getName().equals(COMPONENT_NAME_LABEL_TOGGLE)) {
						JLabel labelToggler = (JLabel) child;
						labelToggler.addMouseListener(new MouseAdapter() {
							@Override
							public void mouseClicked(MouseEvent event) {
								JLabel labelToggler = (JLabel) event.getSource();
								java.awt.Point convertedPoint = SwingUtilities.convertPoint(labelToggler, event.getPoint(), tree);
								TreePath treePath = tree.getPathForLocation(convertedPoint.x, convertedPoint.y);
								if (treePath == null)
									return;
								DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
								Object userObject = node.getUserObject();

								if (userObject instanceof TreeNodeObject) {
									TreeNodeObject treeNodeObject = (TreeNodeObject) userObject;
									switch (treeNodeObject.getMode()) {
									case TreeNodeObject.MODE_CARTESIAN:
										treeNodeObject.setMode(TreeNodeObject.MODE_RANGE);
										labelToggler.setText(COMPONENT_TEXT_LABEL_CARTESIAN);
										appFrame.log("Design gallery", COMPONENT_TEXT_LABEL_RANGE);
										break;
									case TreeNodeObject.MODE_RANGE:
										treeNodeObject.setMode(TreeNodeObject.MODE_CARTESIAN);
										labelToggler.setText(COMPONENT_TEXT_LABEL_RANGE);
										appFrame.log("Design gallery", COMPONENT_TEXT_LABEL_CARTESIAN);
										break;
									}
									
									treeNodeRenderer.fillTreeNode(treeNode, treeNodeObject);
									updateCartesianProduct();
								}
							}
						});
					}
					
					if (child instanceof JComponent)
						addListenerRecursive((JComponent) child);
				}
			}
			
			@Override
			public boolean stopCellEditing() {
				return false;
			}

			@Override
			public Object getCellEditorValue() {
				// all values are already saved here but we should update the tree model
				if (lastTreePath != null)
					checkPath(lastTreePath);
				treeNodeObject.checkRangeOrder();
				updateCartesianProduct();
				return null;
			}
			
			@Override
			public boolean isCellEditable(EventObject event) {
				if (event instanceof MouseEvent) {
					MouseEvent me = (MouseEvent) event;
					TreePath path = tree.getPathForLocation(me.getX(), me.getY());
					if (path != null) {
						Object node = path.getLastPathComponent();
						if (node != null && (node instanceof DefaultMutableTreeNode)) {
							DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
							if (treeNode.getUserObject() instanceof TreeNodeObject) {
								TreeNodeObject treeNodeObject = (TreeNodeObject) treeNode.getUserObject();
								return treeNodeObject.isEnabled();
							}
						}
					}
				}
				return false;
			}

			/**
			 * Fills our tree node object with the user object data.
			 */
			@Override
			public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
				if (!(value instanceof DefaultMutableTreeNode))
					return null;

				DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				// we need a reference after we finished editing (we assume that we only deal with TreeNodeObject's)
				treeNodeObject = (TreeNodeObject) node.getUserObject();
				treeNode = (JPanel) treeNodeRenderer.fillTreeNode(treeNode, treeNodeObject);

				return treeNode;
			}

			private boolean updateTreeNodeObject(JComponent component, TreeNodeObject treeNodeObject) {
				boolean changed = false;
				// save the tree path for updating the model after editing is done
				lastTreePath = treeNodeObject.getTreePath(); 

				if (component instanceof JTextField)
					changed = updateTreeNodeObject((JTextField) component, treeNodeObject);
				else if (component instanceof JCheckBox)
					changed = updateTreeNodeObject((JCheckBox) component, treeNodeObject);

				if (changed) {
					if (component instanceof JCheckBox)
						checkPath(treeNodeObject.getTreePath());
					else
						selectPath(treeNodeObject.getTreePath(), true, false);
					updateCartesianProduct();
				}

				return changed;
			}
			
			private boolean updateTreeNodeObject(JCheckBox checkBox, TreeNodeObject treeNodeObject) {
				boolean changed = (treeNodeObject.isSelected() != checkBox.isSelected());
				treeNodeObject.setSelected(checkBox.isSelected());
				return changed;
			}
			
			private boolean updateTreeNodeObject(JTextField textField, TreeNodeObject treeNodeObject) {
				boolean changed = false;
				String field = textField.getName();
				String value = textField.getText();

				if (field.equals(COMPONENT_NAME_FIELD_START) && !treeNodeObject.getRangeStart().equals(value))
					if (treeNodeObject.setRangeStart(value))
						changed = true;

				if (field.equals(COMPONENT_NAME_FIELD_END) && !treeNodeObject.getRangeEnd().equals(value))
					if (treeNodeObject.setRangeEnd(value))
						changed = true;

				if (field.equals(COMPONENT_NAME_FIELD_STEP) && !treeNodeObject.getStep().equals(value))
					if (treeNodeObject.setStep(value))
						changed = true;
				
				return changed;
			}
		}

		/**
		 * Selects or unselect a specific path (like toggling) ... if it's not a leaf we also toggle
		 * the children (recursive).
		 * 
		 * @param path to toggle
		 */
		public void selectPath(TreePath treePath) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
			Object userObject = node.getUserObject();

			if (userObject instanceof TreeNodeInterface) {
				TreeNodeInterface treeNode = (TreeNodeInterface) userObject;
				selectPath(treePath, !treeNode.isSelected(), true); // toggle
			}
		}
		
		/**
		 * Instead of toggling select or unselect a node.
		 * 
		 * @param treePath
		 * @param selected
		 */
		public void selectPath(TreePath treePath, boolean selected, boolean children) {
			DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
			Object userObject = node.getUserObject();
		
			if (userObject instanceof TreeNodeInterface) {
				TreeNodeInterface treeNode = (TreeNodeInterface) userObject;
				if (treeNode.isEnabled())
					treeNode.setSelected(selected);

				// recursive select or unselect children
				if (children)
					if (node.getChildCount() > 0)
						for (int i=0; i<node.getChildCount(); i++)
							selectPath(new TreePath(((DefaultMutableTreeNode) node.getChildAt(i)).getPath()), selected, children);

				model.reload(node);
			}
		}

		/**
		 * Checks if we have to check or uncheck the parent node. The way how we
		 * do it is simple by iterating over all siblings and at least one is selected
		 * we select the parent as well.
		 * 
		 * @param node child
		 */
		private void checkPath(TreePath treePath) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
			DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

			if (parent == null)
				return;
			
			boolean atLeastOneSelected = false;
			for (int i=0; i<parent.getChildCount(); i++) {
				DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
				Object userObject = child.getUserObject();

				if (userObject instanceof TreeNodeInterface) {
					TreeNodeInterface treeNode = (TreeNodeInterface) userObject;
					if (treeNode.isSelected()) {
						atLeastOneSelected = true;
						break;
					}
				}
			}

			// select or unselect the whole previous path if necessary
			while ((node = (DefaultMutableTreeNode) node.getParent()) != null) {
				if (node.isRoot())
					break;
				else
					selectPath(new TreePath(node.getPath()), atLeastOneSelected, false);
			}
		}
	}
	

	private class TreeNodeObject implements TreeNodeInterface {
		
		// for the Cartesian product we can either build the actual Cartesian
		// product by just using the values from the ports or we could create
		// a whole range from the lowest due to the highest value.
		public static final int MODE_CARTESIAN = 1;
		public static final int MODE_RANGE = 2;
		
		// use this constants in getBoundaryPort
		public static final int BOUNDARY_LOWER_END = 1;
		public static final int BOUNDARY_UPPER_END = 2;
		
		// text representation is used for the tree check box
		private String text;
		
		// does this object has any changes? only objects that have changes
		// are considered for the cartesian product
		private boolean changes;
		
		// this parameter is selected in the tree
		private boolean selected;
		
		// this parameter is selectable
		private boolean enabled = true;
		
		// reference to the parent node object
		private TreeNodeObjectVector<TreeNodeObject> nodeVector;
		
		// actual path in the tree
		private TreePath treePath;

		// save a reference to the ports
		private Port[] ports;
		
		// save which ports are used for the Cartesian product
		// the selection is not involved if we are in the Cartesian mode 
		private boolean[] selectedPorts;
		
		// save values for range
		private Object step;

		// each parameter can be also defined by a range. that means a start and end value.
		// in the beginning the start is the lowest value and the end is the highest value over 
		// all given ports. but later it could change due to the users input.
		private String[] range = new String[2];

		// by default we want to build the Cartesian product
		private int mode = MODE_CARTESIAN;
		
		// parameter type
		private String type;

		
		public TreeNodeObject(String text, Port[] ports, TreeNodeObjectVector<TreeNodeObject> nodeVector, boolean samePorts) {
			this.text = text;
			this.changes = !samePorts;
			this.selected = !samePorts;
			this.nodeVector = nodeVector;
			this.type = ports[0].getType(); // just use the first port for setting the parameter type
			
			// create copies from the ports (so we can change them if necessary)
			this.ports = new Port[ports.length];
			for (int i=0; i<ports.length; i++)
				this.ports[i] = Port.parsedPort(ports[i].getName(), ports[i].getType(), ports[i].stringValue());

			switch (type) {
			case Port.TYPE_INT: 	step = (Object) new Long(1); 		break;
			case Port.TYPE_FLOAT: 	step = (Object) new Double(1.0); 	break;
			case Port.TYPE_BOOLEAN:	step = (Object) "";					break;
			case Port.TYPE_COLOR:	step = (Object) new Integer(10);	break;
			case Port.TYPE_POINT:	step = (Object) new Double(1.0);	break;
			default:				enabled = false; // all other types are by default not supported
			}

			range[0] = getBoundaryLowerPort().stringValue();
			range[1] = getBoundaryUpperPort().stringValue();

			// set all ports as selected
			selectedPorts = new boolean[ports.length];
			Arrays.fill(selectedPorts, true);
		}

		public boolean isSelected() {
			return selected;
		}
		
		public Boolean changes() {
			switch (mode) {
			case MODE_CARTESIAN:
				if (selectedPorts.length > 1 && changes)
					return true;
				else
					return false;
			case MODE_RANGE:
				return !range[0].equals(range[1]);
			}
			return null;
		}

		public boolean isEnabled() {
			// either the parameter is enabled anyway or we check if the mode is Cartesian 
			// in Cartesian mode we allow all parameters
			return (enabled ? true : (mode == MODE_CARTESIAN));
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}

		public TreePath getTreePath() {
			return treePath;
		}

		public void setTreePath(TreePath treePath) {
			this.treePath = treePath;
		}

		public String getText() {
			return text;
		}
		
		public Port[] getPorts(boolean unique) {
			ArrayList<Port> ports = new ArrayList<Port>(Arrays.asList(this.ports));
			
			if (unique) {
				ArrayList<Port> filteredPorts = new ArrayList<Port>();
				ArrayList<String> doubleValues = new ArrayList<String>();

				for (Port p : ports)
					if (!doubleValues.contains(p.stringValue())) {
						doubleValues.add(p.stringValue());
						filteredPorts.add(p);
					}

				ports = filteredPorts;
			}
			
			// sort ports if possible
			Collections.sort(ports, new Comparator<Port>() {
				@Override
				public int compare(Port o1, Port o2) {
					try {
						Double v1 = Double.valueOf(o1.stringValue());
						Double v2 = Double.valueOf(o2.stringValue());
						return v1.compareTo(v2);
					} catch (NumberFormatException e) {
						return o1.stringValue().compareTo(o2.stringValue());
					}
				}
			});
			
			return ports.toArray(new Port[ports.size()]);
		}

		public String toString() {
			return text;
		}
		
		public void setMode(int mode) {
			this.mode = mode;
		}
		
		public int getMode() {
			return this.mode;
		}

		/**
		 * Returns the new state
		 * 
		 * @param port
		 * @return true|false
		 */
		public boolean togglePort(Port port) {
			int index = -1;
			boolean r = false;
			for (int i=0; i<ports.length; i++)
				if (port.equals(ports[i])) {
					index = i;
					break;
				}

			if (index > -1) {
				r = !selectedPorts[index];
				selectedPorts[index] = r;
			}
			return r;
		}
		
		/**
		 * We define start port by the port with the lowest value (if possible).
		 * 
		 * @return
		 */
		private Port getBoundaryLowerPort() {
			return getBoundaryPort(BOUNDARY_LOWER_END);
		}

		private Port getBoundaryUpperPort() {
			return getBoundaryPort(BOUNDARY_UPPER_END);
		}
		
		/**
		 * 
		 * @param boundary BOUNDARY_LOWER_END|BOUNDARY_UPPER_END
		 * @return boundary port
		 */
		private Port getBoundaryPort(int boundary) {
			Port port = ports[0];

			for (int i=1; i<ports.length; i++) {
				switch (boundary) {
				case BOUNDARY_LOWER_END:
					if (compareRangeObjects(ports[i].stringValue(), port.stringValue()) < 0)
						port = ports[i];
					break;
				case BOUNDARY_UPPER_END:
					if (compareRangeObjects(ports[i].stringValue(), port.stringValue()) > 0)
						port = ports[i];
					break;
				}
			}
			
			return port;
		}
		
		public String getStep() {
			return String.valueOf(step);
		}
		
		private boolean parseCheck(String value, String action) {
			try {
				switch (type) {
				case Port.TYPE_INT:		Long.valueOf(value);						break;
				case Port.TYPE_FLOAT:	Double.valueOf(value);						break;
				case Port.TYPE_BOOLEAN:	Boolean.valueOf(value);						break;
				case Port.TYPE_POINT:	
					if (action.equals("step"))
						Double.valueOf(value);
					else
						Point.valueOf(value);	break;
				case Port.TYPE_COLOR:
					if (action.equals("step"))
						Integer.valueOf(value);
					else
						nodebox.graphics.Color.parseColor(value);	break;
				}
			} catch (Exception e) {
				return false;
			}
			return true;
		}
		
		public boolean setStep(String value) {
			// only update if valid value
			if (!parseCheck(value, "step"))
				return false;
			switch (type) {
			case Port.TYPE_INT:		this.step = Long.valueOf(value);	break;
			case Port.TYPE_FLOAT:	this.step = Double.valueOf(value);	break;
			case Port.TYPE_BOOLEAN:	this.step = Boolean.valueOf(value);	break;
			case Port.TYPE_COLOR:	this.step = Integer.valueOf(value);	break;
			case Port.TYPE_POINT:	this.step = Double.valueOf(value);	break;
			default:				this.step = value; // default: string
			}
			return true;
		}
		
		public boolean setRangeStart(String value) {
			// only update if valid value
			if (!parseCheck(value, "start"))
				return false;
			range[0] = value;
			return true;
		}
		
		public boolean setRangeEnd(String value) {
			// only update if valid value
			if (!parseCheck(value, "end"))
				return false;
			range[1] = value;
			return true;
		}

		public String getRangeStart() {
			return range[0];
		}

		public String getRangeEnd() {
			return range[1];
		}

		/**
		 * Compares our range objects.
		 * 
		 * @return -1, 0, 1 if the first document is lower, equal or higher than the last object.
		 */
		private int compareRangeObjects(String value1, String value2) {
			switch (type) {
			case Port.TYPE_INT:
			case Port.TYPE_FLOAT:
				double d1 = Double.valueOf(value1);
				double d2 = Double.valueOf(value2);
				if (d1 < d2)
					return -1;
				else if (d1 > d2)
					return 1;
				break;
			case Port.TYPE_BOOLEAN:
				// define false as lower
				boolean b1 = Boolean.valueOf(value1);
				boolean b2 = Boolean.valueOf(value2);
				if (!b1 && b2)
					return -1;
				else if (b1 && !b2)
					return 1;
				break;
				
			case Port.TYPE_POINT:
				Point point1 = Point.valueOf(value1);
				Point point2 = Point.valueOf(value1);
				//we always pick the first point as bigger than the second because two points cannot be compared in any other way than equal to each other or not
				if (point1.equals(point2))
					return 1;
				break;
				
			case Port.TYPE_COLOR:
				// define 000000 as lower than ffffff
				nodebox.graphics.Color color1 = nodebox.graphics.Color.parseColor(value1);
				nodebox.graphics.Color color2 = nodebox.graphics.Color.parseColor(value2);
				double c1 = color1.getR() + color1.getG() + color1.getB();  
				double c2 = color2.getR() + color2.getG() + color2.getB();
				if (c1 < c2)
					return -1;
				else if (c1 > c2)
					return 1;
				break;
			}
			return 0;
		}
		
		public void checkRangeOrder() {
			if (compareRangeObjects(range[0], range[1]) > 0) {
				String tmp = range[0];
				range[0] = range[1];
				range[1] = tmp;
			}
		}
		
		public boolean isPortSelected(Port port) {
			int index = -1;
			
			for (int i=0; i<ports.length; i++)
				if (ports[i].equals(port)) {
					index = i;
					break;
				}

			return (index > -1) ? selectedPorts[index] : false;
		}
		
		/**
		 * Depending on the mode we return either a interpolated range or
		 * just all different port values.
		 * 
		 * @return
		 */
		public Object[] getParameterValues() {
			Object[] values = new Object[0];

			switch (mode) {
			case MODE_RANGE:
				values = getParameterRangeValues();
				break;
			case MODE_CARTESIAN:
				values = getParameterCartesianValues();
				break;
			}

			return values;
		}
		
		private Object[] getParameterRangeValues() {
			Object[] values = new Object[0];

			// do we have to switch the start and end?
			String r1 = range[0];
			String r2 = range[1];
			if (compareRangeObjects(range[0], range[1]) > 0) {
				r1 = range[1];
				r2 = range[0];
			}
			
			// we only support range the following parameter types
			switch (type) {
			case Port.TYPE_BOOLEAN:
				values = booleanRange();
				break;
			case Port.TYPE_INT:
				values = integerRange(Long.valueOf(r1), Long.valueOf(r2), (long) step);
				break;
			case Port.TYPE_FLOAT:
				values = floatRange(Double.valueOf(r1), Double.valueOf(r2), (double) step);
				break;
			case Port.TYPE_POINT:
				values = pointRange(nodebox.graphics.Point.valueOf(r2), nodebox.graphics.Point.valueOf(r1), (double) step);
				break;

			case Port.TYPE_COLOR:
				values = colorRange(nodebox.graphics.Color.parseColor(r1), nodebox.graphics.Color.parseColor(r2), (int) step);
				break;
			}

			return values;
		}
		
		private Object[] getParameterCartesianValues() {
			// in Cartesian mode we can use all data because we do not have to 
			// interpolate or anything else. just use the values that are there.
			ArrayList<Object> values = new ArrayList<Object>();
			for (Port port : getSelectedPorts()) {
				switch (type) {
				case Port.TYPE_BOOLEAN:
					values.add(port.booleanValue());
					break;
				case Port.TYPE_INT:
					values.add(port.intValue());
					break;
				case Port.TYPE_FLOAT:
					values.add(port.floatValue());
					break;
				case Port.TYPE_POINT:
					values.add(port.pointValue());
					break;
				case Port.TYPE_COLOR:
					values.add(port.colorValue());
					break;
				}
			}
			return values.toArray(new Object[values.size()]);
		}
		
		public Port[] getSelectedPorts() {
			ArrayList<Port> returnPorts = new ArrayList<Port>();
			for (int i=0; i<ports.length; i++)
				if (selectedPorts[i])
					returnPorts.add(ports[i]);
			return returnPorts.toArray(new Port[returnPorts.size()]);
		}
		
		private Object[] booleanRange() {
			return new Object[] {new Boolean(true), new Boolean(false)};
		}
		
		private Object[] integerRange(long start, long end, long step) {
			long pos = start;
			int length = (step <= 0) ? 1 : (int) Math.floor((end - start) /  step);
			length++; // one extra (last value)
			Object[] range = new Object[length];
			
			// fill array with start and end values
			range[0] = new Long(start);
			range[(length-1)] = new Long(end);
			// fill range in between
			for (int i=1; i<(length-1); i++) {
				pos += step;
				range[i] = new Long(pos);
			}
			return range;
		}
		
		private Object[] floatRange(double start, double end, double step) {
			double pos = start;
			int length = step <= 0.0 ? 1 : (int) Math.floor((end - start) /  step);
			length++; // one extra (last value)
			Object[] range = new Object[length];
			
			// fill array with start and end values
			range[0] = new Double(start);
			range[(length-1)] = new Double(end);
			// fill range in between
			for (int i=1; i<(length-1); i++) {
				pos += step;
				range[i] = new Double(pos);
			}
			return range;
		}
		
		private Object[] pointRange(Point start, Point end, double step) { //step is scalar
			int length = (int) Math.abs(Math.floor(end.x - start.x) / step);
			length++; // one extra (last value)
			Object[] range = new Object[length];
			Point firstPoint = new nodebox.graphics.Point(start.x, start.y);
			range[0] = firstPoint;
			
			double x = start.x;
			// fill range in between
			for (int i=1; i <length-1; i++) {
				x += step;
				try{
					double y = start.y + (end.y - start.y)*(x - start.x)/(end.x - start.x);
					range[i] = new nodebox.graphics.Point(x,y);
				}
				catch (Exception e){ //if devision by zero or some other problem
					return null;
				}
			}
			
			Point lastPoint = new nodebox.graphics.Point(end.x, end.y);
			range[(length-1)] = lastPoint;

			/*System.out.println("point values:");
			for (Object o : range){
				Point p = (Point)o;
				System.out.println("[" + p.x + "," + p.y + "]");
			}*/
			return range;			
		}
		
		private Object[] colorRange(nodebox.graphics.Color start, nodebox.graphics.Color end, int step) {
			Object[] range = new Object[step + 1];

			double r = start.getR();
			double g = start.getG();
			double b = start.getB();

			double diff_r = end.getR() - start.getR();
			double diff_g = end.getG() - start.getG();
			double diff_b = end.getB() - start.getB();

			double delta_r = diff_r / (double) step;
			double delta_g = diff_g / (double) step;
			double delta_b = diff_b / (double) step;

			for (int i=1; i<step; i++) {
				r += delta_r;
				g += delta_g;
				b += delta_b;
				range[i] = new nodebox.graphics.Color(r, g, b, nodebox.graphics.Color.Mode.RGB); 
			}
			
			// set start and end color to the bounds
			range[0] = start;
			range[step] = end;

			return range;
		}
	}

	
	private class TreeNodeObjectVector<T> extends Vector<T> implements TreeNodeInterface {
		private String text;
		private boolean selected = false;
		private Node[] nodes;


		public TreeNodeObjectVector(String text, Node[] nodes) {
			this.text = text;
			this.nodes = nodes;
		}

		public Node getNode1() {
			return nodes[0];
		}
		
		public Node getNode2() {
			return nodes[1];
		}
		
		public String getText() {
			return text;
		}
		
		public String toString() {
			return text;
		}

		public boolean isSelected() {
			return selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}
		
		/**
		 * Right now we have no criteria for disable a whole vector.
		 */
		public boolean isEnabled() {
			return true;
		}
	}
	
	
	private interface TreeNodeInterface {
		public boolean isSelected();
		public void setSelected(boolean selected);
		public boolean isEnabled();
		public String getText();
	}
	
	
	/**
	 * This class produces the preview of the Cartesian product. 
	 */
	private class Gallery extends JPanel {

		private int rows = 0;
		private int cols = 0;
		private int page = 1; // by default start on the first page
		private int pages = 1; // by default start on the first page
		private int perPage = 0; // how many documents per page
		private int perPageReserved = 0; // how many fields are reserved for the network view
		private boolean changed = false;
		private Dimension previewPreferredSize = new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		private GridBagConstraints constraints;

		private NodeBoxDocument[][] array; // saves which positions are already filled with which document
		
		private ArrayList<Integer> cartesianProductSelected; // saves which documents are selected
		private ArrayList<Integer> cartesianProductCreated; // saves which documents are already created
		private ArrayList<NodeBoxDocument> cartesianProductDocuments;

		private final Border borderSelected;
		private final Border borderUnselected;


		public Gallery(boolean visible) {
			setVisible(visible);
			
			constraints = new GridBagConstraints();
			cartesianProductDocuments = new ArrayList<NodeBoxDocument>();
			cartesianProductSelected = new ArrayList<Integer>();
			cartesianProductCreated = new ArrayList<Integer>();

			// padding
			constraints.insets = new Insets(1, 1, 1, 1);
			
			borderSelected = BorderFactory.createLineBorder(Color.RED, 2);
			borderUnselected = BorderFactory.createEmptyBorder(2, 2, 2, 2);
			
			setGlassPane(new GlassPane(this));
		}
		
		public void clear() {
			page = 1;
			cartesianProductDocuments.clear();
			cartesianProductSelected.clear();
			cartesianProductCreated.clear();
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
				loadNetwork();
				loadGallery();
				guessRenderNodeAndSet();
				//this is to make the viewer and network display properly at the correct zoom level, you need a delay here though
				SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					master.getNetworkView().centerAndZoom(false);
 					resetViewRelativeToFirstDocumentInTheWorkspace();
                	/*for (NodeBoxDocument doc : master.getDocumentGroup()){
                		if (doc != master)
                			doc.getViewerPane().getViewer().centerAndZoom(true);	
                		}*/
					}
				});
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

		private void loadNetwork() {
			// identify position for the network view
			// rows: start from the second last row
			// columns: divided by two and subtract one (most cases the center)
			int rowStart = rows - 1 - 1;
			int colStart = ((int) Math.ceil(cols / 2)) - 1;
			// append network view from original document
			constraints.gridx = colStart;
			constraints.gridy = rowStart;
			constraints.gridwidth = (cols % 2 == 0) ? 2 : 3;
			constraints.gridheight = 2;
			
			// calculate pages
			perPageReserved = constraints.gridwidth * constraints.gridheight;
			pages = (int) Math.floor((double) cartesianProduct.size() / (double) (perPage - perPageReserved));
			pages++;
			
			// set to last page if necessary
			if (page > pages)
				page = pages;
			
			// set size for the network
			Dimension newNetworkSize = new Dimension(previewPreferredSize.width * constraints.gridwidth, previewPreferredSize.height * constraints.gridheight);
			master.getNetworkPane().setPreferredSize(newNetworkSize);
			add(master.getNetworkPane(), constraints);

			//draw this network in a lighter color:
			master.getNetworkView().isDesignGalleryNetwork = true;
			
			// mark array positions as filled
			for (int i=rowStart; i<(rowStart+constraints.gridheight); i++)
				for (int j=colStart; j<(colStart+constraints.gridwidth); j++)
					array[i][j] = master;
			
			// reset contraints
			constraints.gridwidth = 1;
			constraints.gridheight = 1;
		}
		
		private void guessRenderNodeAndSet(){
			//Hashtable will store potentially last node in the chain, which can be rendered
			Set<Node> renderableNodes = new HashSet<Node>();
			List<Connection> connections = master.getActiveNetwork().getConnections();
			ImmutableList<Node> iList= master.getActiveNetwork().getChildren();
			Iterator<Node> iterator = iList.iterator();
			while (iterator.hasNext()) {
				Node node = iterator.next();
				//find one with an input node and no output node
				boolean hasInput = false;
				boolean hasOutput = false;
		    	for (Connection c : connections) {
		    		if (!hasInput)
		    			hasInput = c.getInputNode().equals(node.getName()); // incoming connection
		    		if (!hasOutput)
		    			hasOutput = c.getOutputNode().equals(node.getName()); // outgoing connections
		    	}
		    	if (hasInput && !hasOutput){
		    		renderableNodes.add(node);
		    	}	
			}
			//now set the rendered node, this will be arbitrarily the first node in the set
			//so we basically are guessing
			Iterator<Node> renderableNodesIterator = renderableNodes.iterator();
			if (renderableNodesIterator.hasNext()){
				Node node = renderableNodesIterator.next();
				master.setRenderedNode(node, false);
			}
		}

		private void loadGallery() {
			// load missing documents (not always invoked ... probably the documents are already loaded)
			for (int i=cartesianProductDocuments.size(); i<((perPage - perPageReserved) * page) && cartesianProductDocuments.size()<cartesianProduct.size(); i++) {
				// create new document and prepare it
				final NodeBoxDocument document = NodeBoxDocument.newInstance(master);
				document.isGalleryDocument = true;
				for (CartesianNode cartesianNode : cartesianProduct.get(i)) {
					Node node = null;
					for (int j=0; j<document.getActiveNetwork().getChildren().size(); j++) {
						Node tmp = document.getActiveNetwork().getChildren().get(j); 
						if (tmp.getName().equals(cartesianNode.node.getName())) {
							node = tmp;
							break;
						}
					}

					if (node == null)
						continue;

					// apply port changes to this document (by changing values we call also call the renderer)
					for (CartesianNodePort cartesianNodePort : cartesianNode.values) {
						document.setActiveNode(node);
						document._setValue(cartesianNodePort.port.getName(), cartesianNodePort.value);
					}
				}
				
				document.getViewerPane().getContentPanel().setPreferredSize(previewPreferredSize);
				//use color for selected, to make these views appear brighter
				document.getViewerPane().getViewer().setBackground(Theme.ACTIVE_COLOR);
				document.getViewerPane().getViewer().setShowText(false);
				cartesianProductDocuments.add(document);
			}
			
			// fill array and grid bag
			int k = (page - 1) * (perPage - perPageReserved);
			for (int i=0; i<rows; i++) {
				for (int j=0; j<cols; j++) {
					if (array[i][j] != null) // already filled (e.g. network pane)
						continue;

					// set document to position
					constraints.gridy = i;
					constraints.gridx = j;

					JPanel preview = new JPanel();
					
					if (k < cartesianProductDocuments.size()) {
						array[i][j] = cartesianProductDocuments.get(k);
						// document already created?
						if (cartesianProductCreated.indexOf(new Integer(k)) > -1)
							cartesianProductDocuments.get(k).getViewerPane().getViewer().setBackground(Theme.IDLE_COLOR);
						preview = cartesianProductDocuments.get(k).getViewerPane().getContentPanel();
						preview.setName(String.valueOf(k));
						Border border = cartesianProductSelected.contains(new Integer(k)) ? borderSelected : borderUnselected;
						preview.setBorder(border);
					}

					preview.setPreferredSize(previewPreferredSize);
					add(preview, constraints);
					++k;
				}
			}
			
			// show page numbers text
			String text = TEXT_PAGES.replace("%num", String.valueOf(page)).replace("%total", String.valueOf(pages));
			if (doPowerSets){
				int numNetworks =  powerSetAndSelectedDocuments.size();
				int currNetwork = comboBoxMasterSelection.getSelectedIndex() + 1;
				text = "N:" + currNetwork + "/" +  numNetworks + ",P:" + text;

				// (de)activate page next and previous buttons
				buttonPagePrevious.setEnabled(page > 1 || currNetwork > 1);
				buttonPageNext.setEnabled(page < pages || currNetwork < numNetworks);
			}
			else{
				// (de)activate page next and previous buttons
				buttonPagePrevious.setEnabled(page > 1);
				buttonPageNext.setEnabled(page < pages);
			}
			pageNumber.setText(text);
			galleryPanel.updateUI();
		}
		
		public void nextPage() {
			if (!doPowerSets){
				if (page < pages) {
					++page;
					update(true); // load next page into gallery
				}
			}
			else //doPowerSets, therefore we have to select next network
			{
				if (page < pages) {
					++page;
					update(true); // load next page into gallery
				}
				else{ //switch to next netowrk
					int nextNetworkIndex = comboBoxMasterSelection.getSelectedIndex() + 1;
					if (nextNetworkIndex < powerSetAndSelectedDocuments.size()){
						page = 1;
						comboBoxMasterSelection.setSelectedIndex(nextNetworkIndex);
						setMasterDocument(nextNetworkIndex);
						update(true); // load next page into gallery
					}
				}
			}
		}
		
		public void previousPage() {
			if (!doPowerSets){
				if (page > 1) {
					--page;
					update(true); // load next page into gallery
				}
			}
			else{ //do powerSets
				if (page > 1) {
					--page;
					update(true); // load next page into gallery
				}
				else{ //switch to previous network
					int nextNetworkIndex = comboBoxMasterSelection.getSelectedIndex() - 1;
					if (nextNetworkIndex >= 0){
						page = pages;
						comboBoxMasterSelection.setSelectedIndex(nextNetworkIndex);
						setMasterDocument(nextNetworkIndex);
						update(true); // load next page into gallery
					}
				}
			}
		}
		
		public ArrayList<NodeBoxDocument> getSelectedDocuments() {
			ArrayList<NodeBoxDocument> selectedDocuments = new ArrayList<NodeBoxDocument>();
			for (Integer i : cartesianProductSelected)
				if (cartesianProductCreated.indexOf(i) <= -1) // return only uncreated documents
					selectedDocuments.add(cartesianProductDocuments.get(i));
			return selectedDocuments;
		}

		public boolean setDocumentAsCreated(NodeBoxDocument document) {
			final Integer i = (Integer) cartesianProductDocuments.indexOf(document);
			if (i > -1) {
				cartesianProductCreated.add(i);
				cartesianProductSelected.remove(i);
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
						if (cartesianProductCreated.indexOf(number) > -1)
							return; // already created
						// select or unselect
						if (cartesianProductSelected.contains(number)) {
							cartesianProductSelected.remove(number);
							preview.setBorder(borderUnselected);
						} else {
							cartesianProductSelected.add(number);
							preview.setBorder(borderSelected);
						}
					}
					
					// update create button
					buttonCreate.setEnabled(cartesianProductSelected.size() > 0);
				}
			}
		}
	}
}