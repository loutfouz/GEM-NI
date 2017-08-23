package nodebox.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import nodebox.function.Function;
import nodebox.function.FunctionRepository;
import nodebox.graphics.ObjectsRenderer;
import nodebox.handle.Handle;
import nodebox.handle.HandleDelegate;
import nodebox.movie.Movie;
import nodebox.movie.VideoFormat;
import nodebox.node.*;
import nodebox.node.MenuItem;
import nodebox.ui.*;
import nodebox.util.FileUtils;
import nodebox.util.LoadException;
import oscP5.OscEventListener;
import oscP5.OscMessage;
import oscP5.OscP5;
import oscP5.OscStatus;

import javax.imageio.ImageIO;
import javax.swing.*;

import undo.UndoManager;












//import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.*;

/**
 * A NodeBoxDocument manages a NodeLibrary.
 */
public class NodeBoxDocument implements HandleDelegate {

    private static final Logger LOG = Logger.getLogger(NodeBoxDocument.class.getName());
    private static final String WINDOW_MODIFIED = "windowModified";

    public static String lastFilePath;
    public static String lastExportPath;

    private static NodeClipboard nodeClipboard;

    private File documentFile;
    private boolean documentChanged;
    private boolean loaded = false;

    public UndoManager undoManager = new UndoManager();
    private boolean holdEdits = false;
    private String lastEditType = null;
    private String lastEditObjectId = null;

    // State
    private NodeLibraryController controller;
    private FunctionRepository functionRepository;
    private String activeNetworkPath = "/"; //<---need to investigate that
    private String activeNodeName = "";
    private boolean restoring = false;
    private boolean invalidateFunctionRepository = false;
    // Rendering
    private final AtomicBoolean isRendering = new AtomicBoolean(false);
    private final AtomicBoolean shouldRender = new AtomicBoolean(false);
    private SwingWorker<List<?>, Node> currentRender = null;
    private Iterable<?> lastRenderResult = null;
    private Map<Node, List<?>> renderResults = ImmutableMap.of();

    // OSC
    private OscP5 oscP5;
    private Map<String, List<Object>> oscMessages = new HashMap<String, List<Object>>();

    // GUI components
    public final NodeBoxMenuBar menuBar;
    private final ViewerPane viewerPane;
    private final DataSheet dataSheet;
    private final PortView portView;
    private final PortPane portPane;
    private final NetworkPane networkPane;
    private final NetworkView networkView;
    private JPanel rootPanel;
    private ApplicationFrame appFrame;
    public boolean isReference = false;
    // used to give out names of alternatives. Doesn't correspond to alternative number in monitor
	private int documentNo = -1;
	// increment the number while creating new documents and use this value for documentNum
	private static int documentCounter = 0;
	private int monitorNo = -1;
	private CustomSplitPane splitInner;
	private CustomSplitPane splitOuter;
    
	public boolean isFirstRow = false;
	private AnimationManager animationManager;
	private final AlternativePaneHeader alternativePaneHeader;
	public String powerSetDocumentName = null; //name given to this document if it's a powerSet, null if this is not powerSetDocument
	
	// each document belongs to a group of documents. usually each application
	// frame builds its own group. if you want to separate a document from
	// the group you have to set it as stand alone (setStandalone(true))
	// by default we set the group from the actual application frame
	private ArrayList<NodeBoxDocument> documentGroup;
	
	public boolean isHistoryPreviewDocument = false;
		
	//active network (group) node doesnt exist 
	public boolean currentActiveNetworkMissing = false;
	public Node renderedNode = null;
	//used to render node properly in the viewer when loading gallery
	public boolean isGalleryDocument = false;
		
	public NodeLibraryController getController(){
		return controller;
	}
	public AnimationManager getAnimationManager(){
		return animationManager;
	}
	public void setAnimationManager(AnimationManager animationManager){
		this.animationManager = animationManager;
	}
	public String getDocumentName(){
		String str;
		try{
			str = getDocumentFile().getName();	
		}
		catch (java.lang.NullPointerException e){ //if this file is not from disk

			if (powerSetDocumentName != null)
				str = powerSetDocumentName;
			else
				str = "Untitled" + getDocumentNo() + ".ndbx";
		}		
		return str;
	}
	//when you choose "New" in the filemenu of menubar
	public void setNewDocument(){
		//replace current nodelibrary with a clean one
		controller.setNodeLibrary(createNewLibrary());
    	networkView.deselectAll();
    	networkView.repaint();
    	portView.repaint();
    	viewerPane.getViewer().repaint();
    	
		resetAllUndo();
	}
	public void resetAllUndo() {
		//reset undo manager as well and discard information in menubar
		undoManager.discardAllEdits();
		menuBar.updateUndoRedoState();
		appFrame.resetGlobalUndoRedo();
	}
	
	public ArrayList<NodeBoxDocument> getDocumentGroup() {
		return documentGroup;
	}
	
	public AlternativePaneHeader getAlternativePaneHeader(){
		return alternativePaneHeader;
	}
	
	public int getDocumentNo(){
		return documentNo;
	}
	
	public void setDocumentNo(int documentNo){
		this.documentNo = documentNo;
	}

	public int getMonitorNo() {
		return monitorNo;
	}
	
	public void setMonitorNum(int monitorNum) {
		this.monitorNo = monitorNum;
	}
	
    ViewerPane getViewerPane()
    {
    	return viewerPane;
    }
    NetworkPane getNetworkPane(){
    	return networkPane;
    }
    /*void setAdressBarGradientActive(int gradient){
    	addressBar.setGradientActive(gradient);
    	addressBar.repaint();
    }*/
    //Added by Shumon August 23, 2013
    public ApplicationFrame getAppFrame()
    {
    	return appFrame;
    }
    
    //Added by Shumon August 23, 2013
    public NetworkView getNetworkView()
    {
    	return networkView;
    }
    
    public NodeBoxMenuBar getNodeBoxMenuBar()
    {
    	return menuBar;
    }
    //frame of this document
    public ApplicationFrame getDocumentFrame()
    {
    	return appFrame;
    }
        
	public void ungroup() {
		// from now on this document builds its own document group and is no longer connected to
		// the application frame
		documentGroup.remove(this);
		documentGroup = new ArrayList<NodeBoxDocument>();
		documentGroup.add(this);
	}
	
	private void log(String... msg){
		appFrame.log(msg);
	}

	private void log(Set<String> affectedAlternatives, String... msg) {
		appFrame.log(affectedAlternatives, msg);
	}
	
	public void setDocumentGroup(ArrayList<NodeBoxDocument> documentGroup) {
		documentGroup.add(this);
		this.documentGroup = documentGroup;
	}
	
	/**
     * Static factory method to create a NodeBoxDocument from a file.
     * <p/>
     * This method can handle file upgrades.
     *
     * @param file the file to load.
     * @return A NodeBoxDocument.
     */
    public static NodeBoxDocument load(ApplicationFrame frame, File file) {
        NodeLibrary library;
        NodeBoxDocument document;
        try {
            library = NodeLibrary.load(file, Application.getInstance().getSystemRepository());
            document = frame.getCurrentDocument();
            
            if (document == null) //should not ever be true???
            {
            	document = new NodeBoxDocument(frame, library);
            	document.setDocumentFile(file);
            }
            else
            {
            	document.reloadWithLibrary(library);
            	document.requestRender();
            }
            document.getNetworkView().updateAll();
            document.getPortView().updateAll();
            frame.log("Load alternative", document.getDocumentName());
            
        } catch (OutdatedLibraryException e) {
            UpgradeResult result = NodeLibraryUpgrades.upgrade(file);
            // The file is used here as the base name for finding relative libraries.
            library = result.getLibrary(file, Application.getInstance().getSystemRepository());
            document = new NodeBoxDocument(frame, library);
            document.setDocumentFile(file);
            document.showUpgradeResult(result);
        } catch (LoadException e) {
            throw new RuntimeException("Could not load " + file, e);
        }
        lastFilePath = file.getParentFile().getAbsolutePath();
        return document;
    }

    /**
     * Display the result of upgrading in a dialog box.
     *
     * @param result The UpgradeResult.
     */
    private void showUpgradeResult(UpgradeResult result) {
        checkNotNull(result);
        if (result.getWarnings().isEmpty()) return;
        final UpgradeWarningsDialog dialog = new UpgradeWarningsDialog(result);
        dialog.setLocationRelativeTo(getDocumentFrame());
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                dialog.setVisible(true);
            }
        });
    }

    private static NodeLibrary createNewLibrary() {
        NodeRepository nodeRepository = Application.getInstance().getSystemRepository();
        Node root = Node.NETWORK.withName("root");
//        Node rectPrototype = nodeRepository.getNode("corevector.rect");
//        String name = root.uniqueName(rectPrototype.getName());
//        Node rect1 = rectPrototype.extend().withName(name).withPosition(new nodebox.graphics.Point(1, 1));
//        root = root
//                .withChildAdded(rect1)
//                .withRenderedChild(rect1);
        return NodeLibrary.create("untitled", root, nodeRepository, FunctionRepository.of());
    }


    /**
     * This constructor calls another constructor. This is called in createNewDocument in ApplicationFrame
     *
     */
    
    public NodeBoxDocument(ApplicationFrame frame) {
        this(frame, createNewLibrary());
    }

    public void reloadWithLibrary(NodeLibrary nodeLibrary)
    {
    	controller = NodeLibraryController.withLibrary(nodeLibrary);
        invalidateFunctionRepository = true;
        updateTitle();
    	
    }
    public NodeLibraryController getNodeLibraryController()
    {
    	return controller;
    }
    public PortView getPortView()
    {
    	return portView;
    }

    
    /**
     * NodeBoxDocument copy factory
     *   
     **/
    
     public static NodeBoxDocument newInstance(NodeBoxDocument sourceDocument) {
    	ApplicationFrame af = sourceDocument.getAppFrame();
    	NodeBoxDocument newDocument = new NodeBoxDocument(af, sourceDocument.getNodeLibrary());

    	newDocument.undoManager = UndoManager.newInstance(sourceDocument, newDocument); //clone undoManager, pass the entire document and extract the entire info from it
    	//updateMenuBar to reflect undo and redo states that have been cloned
    	newDocument.menuBar.undoAction.update();
    	newDocument.menuBar.redoAction.update();
    	
    	//System.out.println("UndoAction=" + newDocument.undoManager);
    	//System.out.println("CanUndo=" + newDocument.undoManager.canUndo());
    	
    	// add the new document to the document group
    	sourceDocument.documentGroup.add(newDocument);
    	newDocument.documentGroup = sourceDocument.documentGroup;
    	
		return newDocument;
    }
    
    /**
     * Creates a new instance from a document but without things like undo manager
     * or any connection to the other documents. 
     * 
     * @param original nodebox document
     * @return a new standalone nodebox document
     */
    public static NodeBoxDocument newGalleryInstance(NodeBoxDocument original) {
    	return new NodeBoxDocument(original);
    }
    
    public boolean getIsFirstRow() {
    	return isFirstRow;
    }

    public void setFirstRow(boolean isFirstRow) {
    	this.isFirstRow = isFirstRow;
    }
    
    /**
     * Main constructor
     * 
     */
    public NodeBoxDocument(ApplicationFrame appFrame, NodeLibrary nodeLibrary) {
    	// set document specific values
    	this.documentNo = ++documentCounter;
    	this.appFrame = appFrame;
    	// by default we use the document group from the given application frame
    	this.documentGroup = appFrame.getDocumentGroup();

        if (!nodeLibrary.hasProperty("canvasWidth"))
            nodeLibrary = nodeLibrary.withProperty("canvasWidth", "1000");
        if (!nodeLibrary.hasProperty("canvasHeight"))
            nodeLibrary = nodeLibrary.withProperty("canvasHeight", "1000");
        
        //animation manager is initialized inside 
        alternativePaneHeader = new AlternativePaneHeader(null, this);

        controller = NodeLibraryController.withLibrary(nodeLibrary);
        invalidateFunctionRepository = true;
        
        viewerPane = new ViewerPane(this);
        dataSheet = viewerPane.getDataSheet();
        portPane = new PortPane(this);
        portView = portPane.getPortView();
        
        networkPane = new NetworkPane(this);
        networkView = networkPane.getNetworkView();
   
        // set for the network pane and port pane the same sizes as like for viewer pane
        // otherwise we won't be able to set the divider properly
        viewerPane.setMinimumSize(new Dimension(23, 0));
        networkPane.setPreferredSize(viewerPane.getPreferredSize());
        networkPane.setMinimumSize(viewerPane.getMinimumSize());
        portPane.setPreferredSize(viewerPane.getPreferredSize());
        portPane.setMinimumSize(viewerPane.getMinimumSize());
        

        
        //JPanel addressPanel = new JPanel(new BorderLayout());
        //addressPanel.add(addressBar, BorderLayout.CENTER);
        //addressPanel.add(progressPanel, BorderLayout.EAST);
        
        buildRootPanel();
     
        //appFrame.setContentPane(rootPanel);
        updateTitle();
        menuBar = new NodeBoxMenuBar(this);
        appFrame.setJMenuBar(menuBar);

        loaded = true;

        /*if (Application.ENABLE_DEVICE_SUPPORT && getOSCPort() > 0) {
            oscP5 = new OscP5(new Object(), getOSCPort());
            oscP5.addListener(new OscEventListener() {
                @Override
                public void oscEvent(OscMessage m) {
                    ImmutableList<Object> arguments = ImmutableList.copyOf(m.arguments());
                    oscMessages.put(m.addrPattern(), arguments);
                }

                @Override
                public void oscStatus(OscStatus ignored) {
                }
            });
            addressBar.setMessage("OSC Port " + getOSCPort());
        }*/
        
        setActiveNetwork("/");
    }
    
    /**
     * Creates a new single document without any connection to the other alternatives.
     * Primarily used for the gallery.
     * 
     * @param nodeLibrary
     */
    public NodeBoxDocument(NodeBoxDocument original) {
    	controller = NodeLibraryController.withLibrary(original.getNodeLibrary());
    	controller.setNodeLibrary(original.getNodeLibrary());
    	controller.setFunctionRepository(original.getFunctionRepository());

        dataSheet = null;
        alternativePaneHeader = null;
        portPane = null;
        portView = null;
        networkPane = null;
        networkView = null;
        //addressBar = null; 		//moved to AlternativePaneHeader, Aug 6, 2014
        //progressPanel = null;		//moved to AlternativePaneHeader, Aug 6, 2014
        menuBar = null;
        viewerPane = null;
    }
    
    public void buildRootPanel() {
    	isFirstRow = appFrame.getMonitorArrangement() <= 2 || monitorNo / appFrame.getNumCols() == 0;
    	
        rootPanel = new JPanel(new BorderLayout());

        JPanel combinePanel = new JPanel(new BorderLayout());

        if (isFirstRow) {
        	combinePanel.add(alternativePaneHeader, BorderLayout.PAGE_START);
        	combinePanel.add(viewerPane, BorderLayout.CENTER);
	        splitInner = new CustomSplitPane(JSplitPane.VERTICAL_SPLIT, combinePanel, portPane);
	        splitOuter = new CustomSplitPane(JSplitPane.VERTICAL_SPLIT, splitInner, networkPane);
	        splitOuter.setResizeWeight(0.6);
	        splitInner.setResizeWeight(0.7);
        } else {
        	combinePanel.add(alternativePaneHeader, BorderLayout.PAGE_END);
        	combinePanel.add(viewerPane, BorderLayout.CENTER);
        	splitInner = new CustomSplitPane(JSplitPane.VERTICAL_SPLIT, networkPane, portPane);
	        splitOuter = new CustomSplitPane(JSplitPane.VERTICAL_SPLIT, splitInner, combinePanel);
	        splitOuter.setResizeWeight(0.6);
	        splitInner.setResizeWeight(0.7);
        }
        
        rootPanel.add(splitOuter, BorderLayout.CENTER);
        
        // register value change listener to scroll bars for synchronous resizing
        PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
				CustomSplitPane source = (CustomSplitPane)evt.getSource();
				
				int current = source.getDividerLocation();
				int old = source.getLastDividerLocation();
				
				if (old <= 0)
					return; // uninitialized
				
				int delta = current - old;
				if (delta == 0) {
					return; // nothing happened (maybe forced by setting LastDividerLocation manually)
				}
				
				// I don't know why, but I have to reduce the delta by half to receive the same
				// acceleration on the other side.
				delta /= 2; 
				
				int currentInvers = -1;
				int newPosition = -1;
				
				// change all other
				for (NodeBoxDocument doc : documentGroup) {
					if (doc == NodeBoxDocument.this)
						continue;
					
					newPosition = current;
					
					if ((NodeBoxDocument.this.isFirstRow && !doc.isFirstRow) || (!NodeBoxDocument.this.isFirstRow && doc.isFirstRow)) {
						if (currentInvers <= 0) {
							if (source.equals(splitInner))
								currentInvers = (doc.getSplitInner().getDividerLocation() - delta);
							else
								currentInvers = (doc.getSplitOuter().getDividerLocation() - delta);
						}
						newPosition = currentInvers;
					}
					
					if (source.equals(splitInner)) {
						if (newPosition > 0 && newPosition < doc.getSplitInner().getMaximumDividerLocation()) {
							doc.getSplitInner().setLastDividerLocation(newPosition); // avoid calling again
							doc.getSplitInner().setDividerLocation(newPosition);
						}
					} else {
						if (newPosition > 0 && newPosition < doc.getSplitOuter().getMaximumDividerLocation()) {
							doc.getSplitOuter().setLastDividerLocation(newPosition); // avoid calling again
							doc.getSplitOuter().setDividerLocation(newPosition);
						}
					}
				}
            }
        };

        splitInner.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, propertyChangeListener);
        splitOuter.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, propertyChangeListener);
    }
    
    public JSplitPane getSplitInner() {
    	return splitInner;
    }
    
    public JSplitPane getSplitOuter() {
    	return splitOuter;
    }
    
    private static int randomOSCPort() {
        return 1024 + (int) Math.round(Math.random() * 10000);
    }

    //// Node Library management ////

    public NodeLibrary getNodeLibrary() {
        return controller.getNodeLibrary();
    }

    public NodeRepository getNodeRepository() {
        return Application.getInstance().getSystemRepository();
    }

    public FunctionRepository getFunctionRepository() {
        if (invalidateFunctionRepository) {
            functionRepository = FunctionRepository.combine(getNodeRepository().getFunctionRepository(), getNodeLibrary().getFunctionRepository());
            invalidateFunctionRepository = false;
        }
        return functionRepository;
    }

    /**
     * Restore the node library to a different undo state.
     *
     * @param nodeLibrary The node library to restore.
     * @param networkPath The active network path.
     * @param nodeName    The active node name. Can be an empty string.
     */
    public void restoreState(NodeLibrary nodeLibrary, String networkPath, String nodeName) {
        controller.setNodeLibrary(nodeLibrary);
        invalidateFunctionRepository = true;
        restoring = true;
        setActiveNetwork(networkPath);
        setActiveNode(nodeName);
        restoring = false;
    }

    //// Node operations ////

    /**
     * Create a node in the active network.
     * This node is based on a prototype.
     *
     * @param prototype The prototype node.
     * @param pt        The initial node position.
     */
    
    //single document version
    /*public void createNode(Node prototype, nodebox.graphics.Point pt) {
        startEdits("Create Node");
        Node newNode = controller.createNode(activeNetworkPath, prototype); //uuid null for now, Aug 19, 2013, shumon
        String newNodePath = Node.path(activeNetworkPath, newNode);
        controller.setNodePosition(newNodePath, pt);
        controller.setRenderedChild(activeNetworkPath, newNode.getName());
        setActiveNode(newNode);
        stopEdits();

        Node activeNode = getActiveNode();
        networkView.updateNodes();
        networkView.singleSelect(activeNode);
        portView.updateAll();

        requestRender();
  
    }*/
    //multi-doc / alternative version
    
    public void createNode(Node prototype, Node replace) {
    	// add new node first
    	nodebox.graphics.Point pt = replace.getPosition();
    	Node[] nodes = createNode(prototype, pt); // created nodes
    	
    	if (nodes.length <= 0)
    		return; // no nodes created ... sth went wrong

    	Node node = nodes[0]; // take the first one, doesn't matter which one because they have all the same uuid
    	
    	// try to copy as many as possible parameters from the existing node
    	for (Port r : replace.getInputs()) {
	    	Port p;
    		if ((p = prototype.getInput(r.getName())) != null) {
    			try {
    				this.setValue(p.getName(), r.getValue());
    			} catch (Exception e) {} // eat
    		}
    	}
    	
    	// try to link as many as possible connections from the old to the new node
    	for (Connection c : this.getActiveNetwork().getConnections()) {
    		try {
	    		// incoming connections
	    		if (c.getInputNode().equals(replace.getName()))
	    			connect(this.getNetworkView().getNodeByName(c.getOutputNode()), node, c.getInputPort());
	    		// outgoing connections
	    		if (c.getOutputNode().equals(replace.getName()))
	    			connect(node, this.getNetworkView().getNodeByName(c.getInputNode()), c.getInputPort());
    		} catch (Exception e) {} // eat
    	}
    	
    	// remove replace node
    	ImmutableList.Builder<Node> b = new ImmutableList.Builder<nodebox.node.Node>();
    	b.add(replace);
    	removeNodes(b.build());
    }
    
    public Node[] createNode(Node prototype, nodebox.graphics.Point pt) {
    	ArrayList<Node> nodes = new ArrayList<Node>(); // return created nodes

    	//replicate creation of the node in other documents
        ArrayList<NodeBoxDocument> docs = documentGroup;
        UUID uuid = UUID.randomUUID();
        
        //new way of handling unique new names across alternatives
        String strUniqueName = Node.uniqueNameAlt(prototype.getName()); //added by Shumon August 23,2013

        Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
	
        Set<String> affectedAlternatives = new HashSet<String>();
        for (int i = 0; i < docs.size(); i++)
        {
    		NodeBoxDocument doc = docs.get(i);
    		
    		//don't create a node if alternative is not editable
    		if (!doc.alternativePaneHeader.isEditable()){
    	        globalUndoEntry[i] = false;
    			continue; 
    		}
    		doc.addEdit("Create Node"); //added by shumon August 23, 2013, now you can undo node renaming
    	    log(doc.getDocumentName(),"Create Node",strUniqueName,doc.activeNetworkPath);

 			nodes.add(_createNodeSubroute(doc, prototype, uuid, pt, strUniqueName));
	        globalUndoEntry[i] = true;
	        affectedAlternatives.add(doc.getDocumentName());
        }
        //log affected alternatives as well
        log(affectedAlternatives, "Create Node", strUniqueName, activeNetworkPath);
        
        appFrame.addGlobalUndoEntry(globalUndoEntry);
        appFrame.printGlobalUndoStack();
        appFrame.getGlassPane().repaint();
		return nodes.toArray(new Node[nodes.size()]);
    }

    private static Node _createNodeSubroute(NodeBoxDocument doc, Node prototype, UUID uuid, nodebox.graphics.Point pt, String newUniqueName)
    {
	
	  Node newNode = doc.controller.createNode(doc.activeNetworkPath, prototype, newUniqueName,uuid);     
      String newNodePath = Node.path(doc.activeNetworkPath, newNode);
      doc.controller.setNodePosition(newNodePath, pt);
      doc.controller.setRenderedChild(doc.activeNetworkPath, newNode.getName());
      doc.setActiveNode(newNode);
      Node activeNode = doc.getActiveNode();
      doc.networkView.updateNodes();
      NetworkView.singleSelectSimple(activeNode, doc.networkView);
      doc.portView.updateAll();
      doc.requestRender();
      return newNode;
   }
    
    //lets find max number for prototype name across all alternatives
  /*private String uniqueName(String path, Node prototype) {
    	
	  	//Node parent = controller.getNode(path);
	  	String prototypeName = prototype.getName();
	  	
        NodeBoxDocument[] docs = appFrame.getDocuments();

        //String namePrefix ="";
        int counter = 0;
      	for (int i = 0; i <  ApplicationFrame.NO_ALTERNATIVES; i++)
	    	{
	      		ImmutableList<Node> nList = docs[i].getNodeLibrary().getRoot().getChildren();
	      		Iterator<Node> iterator = nList.iterator();
	        	while (iterator.hasNext()) {
	        		Node node = iterator.next();
	        		String nodeName = node.getName();
	        		if (nodeName.indexOf(prototypeName) != -1)
	        		{
	        			Matcher m = nodebox.node.Node.NUMBER_AT_THE_END.matcher(nodeName);
	        	        m.find();
	        	        //namePrefix = m.group(1);
	        	        String number = m.group(2);
	        	        int temp = 0;
	        	        if (number.length() > 0) {
	        	            temp = Integer.parseInt(number);
	        	        } 
	        	        if (temp > counter)
	        	        	counter = temp;    	        
	        		}        			
	        	}
	    	}
      	
      	counter++;
      	return prototypeName + counter;
    		
    }*/


    public static Node getNodeByUUIDfromDoc(NodeBoxDocument doc, UUID uuid)
    {
    	//get the right node for this document
    	ImmutableList<Node> iList= doc.getActiveNetwork().getChildren();
    	Iterator<Node> iterator = iList.iterator();
    	while (iterator.hasNext()) {
    		Node node = iterator.next();
    		//System.out.println("This uuid: " + node.getUUID().toString() +", compared to: " + uuid.toString());
    		if (node.getUUID().equals(uuid))
    			return node;
    	}
    	return null;
    }
    
    public static NodeBoxDocument getDocumentFromNode(Node node, ArrayList<NodeBoxDocument> documents){
    	for (NodeBoxDocument d : documents){
    		ImmutableList<Node> iList= d.getNodeLibrary().getRoot().getChildren();
        	Iterator<Node> iterator = iList.iterator();
        	while (iterator.hasNext()) {
        		Node n = iterator.next();
        		//System.out.println("This uuid: " + node.getUUID().toString() +", compared to: " + uuid.toString());
        		if (node.equals(n))
        			return d;
        	}	
    	}
    	return null;
    }
        
    /**
     * Set active networks in all document when changing an active network.
     * If network doesnt exist then disable the document
     *
     */
    public void setActiveNetworksEverywhere(
			 String fullPath) {
		for (NodeBoxDocument doc : getDocumentGroup()){
			Node network = doc.setActiveNetwork(fullPath);
    		//disable this alternative, because this path doesnt exist here
			if (network == null){
				if (doc.getAlternativePaneHeader().isEditable()){ //if editable then do this, otherwise, let the state be the way it is
					doc.getAlternativePaneHeader().setEditableState(false);
					doc.currentActiveNetworkMissing = true;
				}
			}
			else{
				if (doc.currentActiveNetworkMissing){
					doc.getAlternativePaneHeader().setEditableState(true);
					doc.currentActiveNetworkMissing = false;
				}
			}
    	}
		networkView.centerAndZoom(true);
	}
    /**
     * Change the node position of the given node.
     *
     * @param nodeName  The node to move.
     * @param point The point to move to.
     */
    //single document version
    /*public void setNodePosition(Node node, nodebox.graphics.Point point) {
        checkNotNull(node);
        checkNotNull(point);
        checkArgument(getActiveNetwork().hasChild(node));
        // Note that we're passing in the parent network of the node.
        // This means that all move changes to the parent network are grouped
        // together under one edit, instead of for each node individually.
        addEdit("Move Node", "moveNode", getActiveNetworkPath());
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodePosition(nodePath, point);

        networkView.updatePosition(node);
    }*/
    //multi-doc / alternative version

	// no longer unsynched move, oct 3, 2013
	public void setNodePosition(Node node_in_curr_document, nodebox.graphics.Point point) {

		// get the node uuid
		UUID uuid = node_in_curr_document.getUUID();

		Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry(documentGroup.size());

		for (int i = 0; i < documentGroup.size(); i++) {
			NodeBoxDocument doc = documentGroup.get(i);

			if (!doc.alternativePaneHeader.synchedNoveMoveIsEnabled()
					&& doc != this)
				continue;

			Node node = NodeBoxDocument.getNodeByUUIDfromDoc(doc, uuid);
			if (node != null) {
				_setNodePositionSubroute(node, point, doc);
				globalUndoEntry[i] = true;
			} else
				globalUndoEntry[i] = false;
		}
		appFrame.addGlobalUndoEntry(globalUndoEntry);
	}
    
	private void _setNodePositionSubroute(Node node, nodebox.graphics.Point point, NodeBoxDocument doc) {
    	//duplicated from above
        checkNotNull(node);
        checkNotNull(point);
        checkArgument(doc.getActiveNetwork().hasChild(node));
        // Note that we're passing in the parent network of the node.
        // This means that all move changes to the parent network are grouped
        // together under one edit, instead of for each node individually.
        doc.addEdit("Move Node", "moveNode", doc.getActiveNetworkPath());
        String nodePath = Node.path(doc.activeNetworkPath, node);
        doc.controller.setNodePosition(nodePath, point);
        doc.networkView.updatePosition(node);
        //also redraw glasspane when released
        appFrame.getGlassPane().repaint();
	}


    /*public void setNodeName(Node node, String name) {
        checkNotNull(node);
        checkNotNull(name);
        controller.renameNode(activeNetworkPath, node.getName(), name);
        setActiveNode(name);
        networkView.updateNodes();
        networkView.singleSelect(getActiveNode());
        requestRender();
    }*/
    
    /**
     * Change the node name.
     * All nodes get affected by this operation for consistency in node naming, even if these nodes are in alternatives that are not editable 
     *
     * @param nodeName The node to rename.
     * @param name The new node name.
     */
    public void setNodeName(Node node_in_curr_document, String name) {
    	
    	Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
        //List<String> affectedAlternatives = new ArrayList<String>();

    	//Node will be renamed in all alternatives regardless of whether it's locked or not 
        for (int i = 0; i < documentGroup.size(); i++)
        {
        	NodeBoxDocument doc = documentGroup.get(i);
	    	Node node = NodeBoxDocument.getNodeByUUIDfromDoc(documentGroup.get(i), node_in_curr_document.getUUID());
	    	
	    	try{
	    		checkNotNull(node);
	    	}
	    	catch (NullPointerException e) {
	    		continue;
	    	}
	    	checkNotNull(name);	  
	    	
	        doc.addEdit("Rename Node"); //added by shumon August 23, 2013, now you can undo node renaming
	        
	        //log: document name,"Rename Node", old name, new name, active path 
    	    log(doc.getDocumentName(),"Rename Node",node.getName(), name,doc.activeNetworkPath);

	        doc.controller.renameNode(doc.activeNetworkPath, node.getName(), name);
	        doc.setActiveNode(name);
	        doc.networkView.updateNodes();
	        doc.networkView.singleSelect(doc.getActiveNode());
	        doc.requestRender();
	        globalUndoEntry[i] = true;
	        //affectedAlternatives.add(doc.getDocumentName());
        }
        //log affected alternatives
        //in this operation all nodes get affected, for the consistency (including the ones in alternatives that not editable
        //log(affectedAlternatives, "Rename Node", node_in_curr_document.getName(), name, activeNetworkPath);

        //update Global Undo
        appFrame.addGlobalUndoEntry(globalUndoEntry);
        //appFrame.printGlobalUndoStack();
    }
    

    //global undo will ignore all these Metadata Settings stuff for now, Shumon Nov 13, 2013
    /**
     * Change the category for the node. 
     *
     * @param node     The node to change.
     * @param category The new category.
     */
    public void setNodeCategory(Node node, String category) {
        checkNotNull(node);
        checkNotNull(category);
        addEdit("Set Node Category");
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodeCategory(nodePath, category);
	    log(getDocumentName(),"Set Node Category", nodePath, category);

    }

    /**
     * Change the description for the node.
     *
     * @param node        The node to change.
     * @param description The new description.
     */
    public void setNodeDescription(Node node, String description) {
        checkNotNull(node);
        checkNotNull(description);
        addEdit("Set Node Description");
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodeDescription(nodePath, description);
	    log(getDocumentName(),"Set Node Description", nodePath, description);
    }

    /**
     * Change the node image icon.
     *
     * @param node  The node to change.
     * @param image The new image icon.
     */
    public void setNodeImage(Node node, String image) {
        checkNotNull(node);
        checkNotNull(image);
        addEdit("Set Node Image");
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodeImage(nodePath, image);
        networkView.updateNodes();
	    log(getDocumentName(),"Set Node Image", nodePath, image);
    }

    /**
     * Change the output type for the node.
     *
     * @param node       The node to change.
     * @param outputType The new output type.
     */
    public void setNodeOutputType(Node node, String outputType) {
        checkNotNull(node);
        checkNotNull(outputType);
        addEdit("Set Output Type");
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodeOutputType(nodePath, outputType);
        networkView.updateNodes();
	    log(getDocumentName(),"Set Output Type", nodePath, outputType);

    }

    /**
     * Change the output range for the node.
     *
     * @param node        The node to change.
     * @param outputRange The new output range.
     */
    public void setNodeOutputRange(Node node, Port.Range outputRange) {
        checkNotNull(node);
        addEdit("Change Node Output Range");
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodeOutputRange(nodePath, outputRange);
        requestRender();
	    log(getDocumentName(),"Change Node Output Range", nodePath, outputRange.toString());

    }

    /**
     * Change the node function.
     *
     * @param node     The node to change.
     * @param function The new function.
     */
    public void setNodeFunction(Node node, String function) {
        checkNotNull(node);
        checkNotNull(function);
        addEdit("Set Node Function");
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodeFunction(nodePath, function);
        networkView.updateNodes();
        requestRender();
	    log(getDocumentName(),"Set Node Function", nodePath, function);

    }

    /**
     * Change the node handle function.
     *
     * @param node   The node to change.
     * @param handle The new handle function.
     */
    public void setNodeHandle(Node node, String handle) {
        checkNotNull(node);
        checkNotNull(handle);
        addEdit("Set Node Handle");
        String nodePath = Node.path(activeNetworkPath, node);
        controller.setNodeHandle(nodePath, handle);
        createHandleForActiveNode();
        networkView.updateNodes();
        requestRender();
	    log(getDocumentName(),"Set Node Handle", nodePath, handle);

    }

    /**
     * Set the node metadata to the given metadata.
     * Note that this method is not called when the node position or name changes.
     *
     * @param node     The node to change.
     * @param metadata A map of metadata.
     */
    public void setNodeMetadata(Node node, Object metadata) {
        // TODO: Implement
        // TODO: Make NodeAttributesEditor use this.
        // Metadata changes could mean the icon has changed.
        networkView.updateNodes();
        if (node == getActiveNode()) {
            portView.updateAll();
            // Updating the metadata could cause changes to a handle.
            viewerPane.repaint();
            dataSheet.repaint();
        }
        requestRender();
        
    }

    //for a node that is deselected
    public static void clearNodeViews(NodeBoxDocument doc){
    	doc.setActiveNode((Node) null);
    	doc.getViewerPane().getViewer().repaint();
    	doc.getPortView().updateAll();
    }
    /**
     * Change the rendered node to the given node
     *
     * @param node the node to set rendered
     */
    public void setRenderedNode(Node node, boolean doLogging) {
    	Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry(documentGroup.size());
		for (int i = 0; i < documentGroup.size(); i++)
        {
    		NodeBoxDocument doc = documentGroup.get(i);
    		try{
    	        checkNotNull(node);
    	        checkArgument(doc.getActiveNetwork().hasChild(node.getName()));	//use uuid instead in the future
    		}
    		catch (Exception e){
    			globalUndoEntry[i] = false;
    			//set activeNode to null if in reference view mode
    			clearNodeViews(doc);
    			continue;
    		}
    		doc.addEdit("Set Rendered");
    		if (doLogging)
    			log(doc.getDocumentName(),"Set Rendered", doc.activeNetworkPath, node.getName());

            doc.controller.setRenderedChild(doc.activeNetworkPath, node.getName()); //uuid in the future
            doc.renderedNode = node;
            doc.networkView.updateNodes();
            NetworkView.singleSelectSimple(node, networkView);
            doc.requestRender();
			globalUndoEntry[i] = true;
        }
		appFrame.addGlobalUndoEntry(globalUndoEntry);
		// reset the viewer when you set rendered the node
		try{
			getViewerPane().getViewer().resetViewTransform();
		}
		catch (Exception e){
			
		}
    }

    public void setNodeExported(Node node, boolean exported) {
        throw new UnsupportedOperationException("Not implemented yet.");
        //addEdit("Set Exported");
    }

    /**
     * Remove the given node from the active network.
     *
     * @param node The node to remove.
     */
    //it appears that this function isn't used, so I didnt create global undo for it
    /*public void removeNode(Node node) {
        addEdit("Remove Node");
        removeNodeImpl(node);
        networkView.updateAll();
        requestRender();
    }*/


    /*public void removeNodes(Iterable<Node> nodes) {
        addEdit("Delete Nodes");
        for (Node node : nodes) {
            removeNodeImpl(node);
        }
        networkView.updateAll();
        portView.updateAll();
        requestRender();
    }*/

    /**
     * Remove the given nodes from the active network.
     *
     * @param nodes The node to remove.
     */
    public void removeNodes(Iterable<Node> nodes_in_curr_doc) {
    	if (!this.alternativePaneHeader.isEditable()) return; //if this doc is locked, then no removing
    	
		Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
        Set<String> affectedAlternatives = new HashSet<String>();

		for (int i = 0; i < documentGroup.size(); i++)
        {
    		NodeBoxDocument doc = documentGroup.get(i);
    			
    		if (!doc.alternativePaneHeader.isEditable()){ //skip if it's disabled
    			globalUndoEntry[i] = false;
    			continue;
    		}

    		doc.addEdit("Delete Nodes");
	        for (Node node_in_curr_doc : nodes_in_curr_doc) {
  		    	Node node = NodeBoxDocument.getNodeByUUIDfromDoc(doc, node_in_curr_doc.getUUID());

  		    	if (node != null) //if node exist in the document, if it doesn't then skip deleting
  		    	{
  		    		doc.removeNodeImpl(node);
  		    		doc.portView.updateAll();
  		    		doc.networkView.updateAll();
  		    		doc.viewerPane.getViewer().repaint();
  		    		doc.setActiveNode((Node) null);
  		    		doc.requestRender();
  		    	    log(doc.getDocumentName(),"Delete Nodes", doc.activeNetworkPath, node.getName());
  		    	}
	        }
			globalUndoEntry[i] = true;
	    	affectedAlternatives.add(doc.getDocumentName());
        }
        log(affectedAlternatives, "Delete Nodes", activeNetworkPath);

		appFrame.addGlobalUndoEntry(globalUndoEntry);
		if (appFrame.getReferenceDocument() != null){ //if in referenceView Mode
			appFrame.getGlassPane().repaint();
		}
    }
    public void deleteAndReconnect(Node node){
    	List<Connection> connections = this.getActiveNetwork().getConnections();
    	// try to link as many as possible connections from the old to the new node
    	for (Connection c : connections) {
    		// incoming connections
    		if (c.getInputNode().equals(node.getName())){
    			//now do for all outgoing connectors
    			for (Connection cc : connections) {
					if (cc.getOutputNode().equals(node.getName())){
    	    			Node outputNode = this.getNetworkView().getNodeByName(c.getOutputNode());
    	    			Node inputNode = this.getNetworkView().getNodeByName(cc.getInputNode());
    	    			try {
    	    				this.controller.connect(this.activeNetworkPath,outputNode, inputNode, cc.getInputPort());
    	    				//System.out.println("1output:" + outputNode.getName() +",input:" + inputNode.getName() + ",port:" + c.getInputPort());
    	    			} catch (Exception e) {
    	    				//System.err.println("1output:" + outputNode.getName() +",input:" + inputNode.getName() + ",port:" + c.getInputPort());
    	    				} // eat
    	    		}
    			}
    		}
    		//outgoing connections
    		else if (c.getOutputNode().equals(node.getName())){
    			//now do for all incoming connectors
    			for (Connection cc : connections) {
					if (cc.getInputNode().equals(node.getName())){
    	    			Node outputNode = this.getNetworkView().getNodeByName(cc.getOutputNode());
    	    			Node inputNode = this.getNetworkView().getNodeByName(c.getInputNode());
    	    			try {
    	    				this.controller.connect(this.activeNetworkPath,outputNode, inputNode, c.getInputPort());
    	    				//System.out.println("1output:" + outputNode.getName() +",input:" + inputNode.getName() + ",port:" + c.getInputPort());
    	    			} catch (Exception e) {
    	    				//System.err.println("2output:" + outputNode.getName() +",input:" + inputNode.getName() + ",port:" + c.getInputPort());
    	    				} // eat
    	    		}
    			}
    		}
    	
    	}
    	
    	// remove replace node
        removeNodeImpl(node);

        // remove replace node
    	//ImmutableList.Builder<Node> b = new ImmutableList.Builder<nodebox.node.Node>();
    	//b.add(selectedNode);
    	//removeNodes(b.build());

    }
    /**
     * Helper method used by removeNode and removeNodes to do the removal and update the port view, if needed.
     *
     * @param node The node to remove.
     */
    private void removeNodeImpl(Node node) {
        checkNotNull(node, "Node to remove cannot be null.");
        checkArgument(getActiveNetwork().hasChild(node), "Node to remove is not in active network.");
        controller.removeNode(activeNetworkPath, node.getName());
        // If the removed node was the active one, reset the port view.
        if (node == getActiveNode()) {
            setActiveNode((Node) null);
        }
    }

    /**
     * Create a connection from the given output to the given input.
     *
     * @param outputNode The output node.
     * @param inputNode  The input node.
     * @param inputPort  The input port.
     */
    
    //this method right now ignores if the link already exists, but that seems to be taken care of 
    public void connect(Node outputNode, Node inputNode, String inputPort) {
    	if (!this.alternativePaneHeader.isEditable()) return; //if locked, no connecting (this is precaution for further undo stuff
  
		Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
    	
        Set<String> affectedAlternatives = new HashSet<String>();

        for (int i = 0; i < documentGroup.size(); i++)
        {
        	NodeBoxDocument doc = documentGroup.get(i);
        	
        	if (!doc.alternativePaneHeader.isEditable()){ //skip if it's disabled
        		globalUndoEntry[i] = false;
        		continue;
        	}
        	
        	Node inputNodeInAlternative = NodeBoxDocument.getNodeByUUIDfromDoc(documentGroup.get(i), inputNode.getUUID());
        	Node outputNodeInAlternative = NodeBoxDocument.getNodeByUUIDfromDoc(documentGroup.get(i), outputNode.getUUID());
        	
        	//if either node doesn't exist in this alternative then skip
        	if (inputNodeInAlternative == null || outputNodeInAlternative == null){
        		globalUndoEntry[i] = false;
        		continue;
        	}
        	
            doc.addEdit("Connect");
            doc.controller.connect(activeNetworkPath, outputNodeInAlternative, inputNodeInAlternative, inputPort);
            //System.out.println("out node:" + outputNodeInAlternative + ", inputNode:" + inputNodeInAlternative + ",inputPort:" + inputPort);
	    	log(doc.getDocumentName(),"Connect", activeNetworkPath, outputNodeInAlternative.getName(), inputNodeInAlternative.getName(), inputPort);


            doc.portView.updateAll();
            doc.viewerPane.updateHandle();
	    	doc.networkView.updateAll();
            doc.requestRender();
            
			globalUndoEntry[i] = true;
	        affectedAlternatives.add(doc.getDocumentName());


        }
		appFrame.addGlobalUndoEntry(globalUndoEntry);
        log(affectedAlternatives, "Connect", outputNode.getName(), inputNode.getName(), inputPort, activeNetworkPath);

		//appFrame.printGlobalUndoStack();
    }

    /**
     * Remove the given connection from the network.
     *
     * @param connection the connection to remove
     */
    public void disconnect(Connection connection) {
        
		String strInputNode = connection.getInputNode();
		//String strOutputNode = connection.getOutputNode();
		String strPort = connection.getInputPort();
		
		Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
		
        Set<String> affectedAlternatives = new HashSet<String>();

		for (int i = 0; i < documentGroup.size(); i++)
        {
        	NodeBoxDocument doc = documentGroup.get(i);
        	
        	if (!doc.alternativePaneHeader.isEditable()) //skip if it's disabled
        		continue;
        	Node inputNode = doc.getActiveNetwork().getChild(strInputNode);
        	
        	if (inputNode == null){ //don't know if this is needed, but doesnt hurt currently
    			globalUndoEntry[i] = false;
        		continue; 
        	}
        	
        	Node inputNodeInAlternative = NodeBoxDocument.getNodeByUUIDfromDoc(documentGroup.get(i), inputNode.getUUID());
        	
			Connection c = doc.getActiveNetwork().getConnection(inputNodeInAlternative.getName(), strPort);
			//System.out.println("connection="+c);
			
			if (c == null){ //if this connection doesn't exist in this alternative then continue
    			globalUndoEntry[i] = false;
				continue; 
			}
			
	        doc.addEdit("Disconnect");

	        doc.controller.disconnect(doc.activeNetworkPath, c);
	    	log(doc.getDocumentName(),"Disconnect", activeNetworkPath, connection.getInputNode(), connection.getInputPort());

	        doc.portView.updateAll();
	        doc.networkView.updateConnections();
	        doc.viewerPane.updateHandle();
	        doc.requestRender();
			globalUndoEntry[i] = true;
	        affectedAlternatives.add(doc.getDocumentName());


        }
        appFrame.addGlobalUndoEntry(globalUndoEntry);
        log(affectedAlternatives, "Disconnect", activeNetworkPath, connection.getInputNode(), connection.getInputPort(), activeNetworkPath);

		//appFrame.printGlobalUndoStack();
    }

    //Not sure what publish now, so global undo is skipped for this now Shumon Nov 13, 2013
    public void publish(String inputNode, String inputPort, String publishedName) {
        addEdit("Publish");
        controller.publish(activeNetworkPath, inputNode, inputPort, publishedName);
    	log(getDocumentName(),"Publish", activeNetworkPath,  inputNode, inputPort, publishedName);

    }

    public void unpublish(String publishedName) {
        addEdit("Unpublish");
        controller.unpublish(activeNetworkPath, publishedName);
    	log(getDocumentName(),"Unpublish", activeNetworkPath,  publishedName);

    }

    /**
     * @param node     the node on which to add the port
     * @param portName the name of the new port
     * @param portType the type of the new port
     */
    public void addPort(Node node, String portName, String portType) {
        checkArgument(getActiveNetwork().hasChild(node));
        addEdit("Add Port");
        controller.addPort(Node.path(activeNetworkPath, node), portName, portType);
    	log(getDocumentName(),"Add Port", Node.path(activeNetworkPath, node), portName, portType);
        portView.updateAll();
        networkView.updateAll();
    }

    /**
     * Remove the port from the node.
     *
     * @param node     The node on which to remove the port.
     * @param portName The name of the port
     */
    public void removePort(Node node, String portName) {
        checkArgument(getActiveNetwork().hasChild(node));
        addEdit("Remove Port");
        controller.removePort(activeNetworkPath, node.getName(), portName);
    	log(getDocumentName(),"Remove Port", activeNetworkPath, node.getName(), portName);

        if (node == getActiveNode()) {
            portView.updateAll();
            viewerPane.repaint();
            dataSheet.repaint();
        }
    }

    /**
     * Change the widget for the given port
     *
     * @param portName The name of the port to change.
     * @param widget   The new widget.
     */
    public void setPortWidget(String portName, Port.Widget widget) {
        checkValidPort(portName);
        addEdit("Change Widget");
        controller.setPortWidget(getActiveNodePath(), portName, widget);
    	log(getDocumentName(),"Change Widget", getActiveNodePath(), portName, widget.toString());
        portView.updateAll();
        requestRender();
    }

    /**
     * Change the port range of the given port
     *
     * @param portName The name of the port to change.
     * @param range    The new port range.
     */
    public void setPortRange(String portName, Port.Range range) {
        checkValidPort(portName);
        addEdit("Change Port Range");
        controller.setPortRange(getActiveNodePath(), portName, range);
    	log(getDocumentName(),"Change Port Range", getActiveNodePath(), portName, range.toString());

        requestRender();
    }

    /**
     * Change the minimum value for the given port
     *
     * @param portName     The name of the port to change.
     * @param minimumValue The new minimum value.
     */
    public void setPortMinimumValue(String portName, Double minimumValue) {
        checkValidPort(portName);
        addEdit("Change Minimum Value");
        controller.setPortMinimumValue(getActiveNodePath(), portName, minimumValue);
    	log(getDocumentName(),"Change Minimum Value", getActiveNodePath(), portName, minimumValue.toString());

        portView.updateAll();
        requestRender();
    }

    /**
     * Change the maximum value for the given port
     *
     * @param portName     The name of the port to change.
     * @param maximumValue The new maximum value.
     */
    public void setPortMaximumValue(String portName, Double maximumValue) {
        checkValidPort(portName);
        addEdit("Change Maximum Value");
        controller.setPortMaximumValue(getActiveNodePath(), portName, maximumValue);
    	log(getDocumentName(),"Change Maximum Value", getActiveNodePath(), portName, maximumValue.toString());
        portView.updateAll();
        requestRender();
    }

    /**
     * Add a new menu item for the given port's menu.
     *
     * @param portName The name of the port to add a new menu item for.
     * @param key      The key of the new menu item.
     * @param label    The label of the new menu item.
     */
    public void addPortMenuItem(String portName, String key, String label) {
        checkValidPort(portName);
        addEdit("Add Port Menu Item");

        controller.addPortMenuItem(getActiveNodePath(), portName, key, label);
    	log(getDocumentName(),"Add Port Menu Item", getActiveNodePath(), portName, key, label);


        portView.updateAll();
        requestRender();
    }

    /**
     * Remove a menu item from the given port's menu.
     *
     * @param portName The name of the port to remove the menu item from.
     * @param item     The menu item to remove
     */
    public void removePortMenuItem(String portName, MenuItem item) {
        checkValidPort(portName);
        addEdit("Remove Parameter Menu Item");

        controller.removePortMenuItem(getActiveNodePath(), portName, item);
    	log(getDocumentName(),"Remove Parameter Menu Item", getActiveNodePath(), portName, item.getKey(), item.getLabel());


        Node n = getActiveNode();
        portView.updateAll();
        requestRender();
    }

    /**
     * Move a menu item down from the given port's menu.
     *
     * @param portName  The name of the port of which to update the menu.
     * @param itemIndex The index of the menu item to move down.
     */
    public void movePortMenuItemDown(String portName, int itemIndex) {
        checkValidPort(portName);
        addEdit("Move Port Item Down");
        controller.movePortMenuItemDown(getActiveNodePath(), portName, itemIndex);
    	log(getDocumentName(),"Move Port Item Down", getActiveNodePath(), portName, String.valueOf(itemIndex));

        portView.updateAll();
    }

    /**
     * Move a menu item up from the given port's menu.
     *
     * @param portName  The name of the port of which to update the menu.
     * @param itemIndex The index of the menu item to move up.
     */
    public void movePortMenuItemUp(String portName, int itemIndex) {
        checkValidPort(portName);
        addEdit("Move Port Item Up");
        controller.movePortMenuItemUp(getActiveNodePath(), portName, itemIndex);
    	log(getDocumentName(),"Move Port Item Up", getActiveNodePath(), portName, String.valueOf(itemIndex));

        portView.updateAll();
    }

    /**
     * Change a menu item's key and label in the given port's menu.
     *
     * @param portName  The name of the port of which to update the menu.
     * @param itemIndex The index of the menu item to change.
     * @param key       The new key of the menu item.
     * @param label     The new label of the menu item.
     */
    public void updatePortMenuItem(String portName, int itemIndex, String key, String label) {
        checkValidPort(portName);
        addEdit("Update Port Menu Item");
        controller.updatePortMenuItem(getActiveNodePath(), portName, itemIndex, key, label);
    	log(getDocumentName(),"Move Port Item Up", getActiveNodePath(), portName, String.valueOf(itemIndex), key, label);
        portView.updateAll();
    }

    public Object getValue(String portName) {
        Port port = checkValidPort(portName);
        return port.getValue();
    }

    /**
     * Set the port of the active node to the given value.
     *
     * @param portName The name of the port on the active node.
     * @param value    The new value.
     */
    public void _setValue(String portName, Object value) {
        checkValidPort(portName);
        //addEdit("Change Value", "changeValue", getActiveNodePath() + "#" + portName);

        controller.setPortValue(getActiveNodePath(), portName, value);

        // TODO set variables on the root port.
//        if (port.getNode() == nodeLibrary.getRoot()) {
//            nodeLibrary.setVariable(port.getName(), port.asString());
//        }

        portView.updatePortValue(portName, value);
        // Setting a port might change enable expressions, and thus change the enabled state of a port row.
        portView.updateEnabledState();
        // Setting a port might change the enabled state of the handle.
        // viewer.setHandleEnabled(activeNode != null && activeNode.hasEnabledHandle());
        requestRender();
    }
    
    public void setValue(String portName, Object value) {
        checkValidPort(portName);
 
	    Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();

	    UUID activeNodeUUID = getActiveNode().getUUID(); //getActiveNode in this document to select it for editing in others
        Set<String> affectedAlternatives = new HashSet<String>();
	    
	    boolean ignore = false;
        for (int i = 0; i < documentGroup.size(); i++)
        {
	        NodeBoxDocument doc = documentGroup.get(i);

	        if (!doc.alternativePaneHeader.isEditable()){ //skip if it's disabled
		        globalUndoEntry[i] = false;
	        	continue;
	        }

	        Node selectableNode = NodeBoxDocument.getNodeByUUIDfromDoc(doc, activeNodeUUID);
	        if (selectableNode == null){ //active node doesn't exist in this document, so skip
		        globalUndoEntry[i] = false;
	        	continue;
	        }

	        //this does the same thing as inside the addEdit function: it ignores all the same edits
	        if (doc.lastEditType != null && doc.lastEditType.equals( "changeValue") && doc.lastEditObjectId.equals(doc.getActiveNodePath() + "#" + portName))
	        {
	        	ignore = true;
	        	System.out.println("ignore!" + doc.lastEditType );
	        }
	        else
	        	System.out.println("dont ignore!");
	        doc.addEdit("Change Value", "changeValue", doc.getActiveNodePath() + "#" + portName);
	        NetworkView.singleSelectSimple(selectableNode, doc.networkView); //you need to select the node in all other views, so that changes are visible right away, otherwise this doesnt work
            doc.controller.setPortValue(doc.getActiveNodePath(), portName, value);
	    	log(doc.getDocumentName(),"Change Value", doc.getActiveNodePath(), portName, String.valueOf(value));
            doc.portView.updatePortValue(portName, value);
            doc.portView.updateEnabledState();
            doc.requestRender();  
	        globalUndoEntry[i] = true;
	        affectedAlternatives.add(doc.getDocumentName());
        }
        
        repaintAllPortViews();
        
        if (!ignore){
	        appFrame.addGlobalUndoEntry(globalUndoEntry);
	        log(affectedAlternatives, "Change Value", portName, String.valueOf(value), getActiveNodePath());
	        //appFrame.printGlobalUndoStack();
        }
    }

	public  void repaintAllPortViews()
	{
        for (int i = 0; i < documentGroup.size(); i++)
        {
        	if (!documentGroup.get(i).isReference)
        		documentGroup.get(i).portView.repaintControlPanel();
        }
	}
 public static ApplicationFrame getCurrentFrame() {
	        return Application.getInstance().getCurrentAppFrame();
	}
	
    /*public void selectNodeInAllDocs(Node node)
    {
		    NodeBoxDocument[] docs = appFrame.getDocuments();
		    UUID uuid = node.getUUID();
		    
	        for (int i = 0; i < ApplicationFrame.NO_ALTERNATIVES; i++)
	        {
		        NodeBoxDocument doc = docs[i];
		        Node selectableNode = NodeBoxDocument.getNodeByUUIDfromDoc(doc, uuid);
	        }
    }*/

    public void revertPortToDefault(String portName) {
        Port port = checkValidPort(portName);
        addEdit("Revert Port to Default");
        controller.revertToDefaultPortValue(getActiveNodePath(), portName);
    	log(getDocumentName(),"Revert Port to Default", getActiveNodePath(),  portName);

        portView.updateAll();
        portView.updateEnabledState();
        requestRender();
    }

    public void setPortMetadata(Port port, String key, String value) {
        addEdit("Change Port Metadata");
    	log(getDocumentName(),"Change Port Metadata", "Not implemented yet.");
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private Port checkValidPort(String portName) {
        checkNotNull(portName, "Port cannot be null.");
        checkNotNull(getActiveNode(), "Active node cannot be null");
        Port port = getActiveNode().getInput(portName);
        checkArgument(port != null, "Port %s does not exist on node %s", portName, getActiveNode());
        return port;
    }

    //// Port pane callbacks ////

    public void editMetadata() {
        if (getActiveNode() == null) return;
        JDialog editorDialog = new NodeAttributesDialog(appFrame);
        editorDialog.setSize(580, 751);
        editorDialog.setLocationRelativeTo(getDocumentFrame());
        editorDialog.setVisible(true);
    }

    //// Screen shot ////

    public void takeScreenshot(File outputFile) {
    	//broken for now
        Container c = getDocumentFrame().getContentPane();
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        c.paint(g2);
        try {
            ImageIO.write(img, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //// HandleDelegate implementation ////

    public void silentSet(String portName, Object value) {
        try {
            Port port = getActiveNode().getInput(portName);
            setValue(portName, value);
        } catch (Exception ignored) {
        }
    }

    // TODO Merge stopEditing and stopCombiningEdits.

    public void stopEditing() {
        stopCombiningEdits();
    }

    public void updateHandle() {
        if (viewerPane.getHandle() != null)
            viewerPane.getHandle().update();
        // TODO Make viewer repaint more fine-grained.
        viewerPane.repaint();
    }

    //// Active network / node ////

    /**
     * Return the network that is currently "open": shown in the network view.
     *
     * @return The currently active network.
     */
    public Node getActiveNetwork() {
        // TODO This might be a potential bottleneck.
        return getNodeLibrary().getNodeForPath(activeNetworkPath);
    }

    public String getActiveNetworkPath() {
        return activeNetworkPath;
    }

    public Node setActiveNetwork(String path) {
        checkNotNull(path);
        Node network = getNodeLibrary().getNodeForPath(path);
        if (network == null) {
        	return null;
        }
        activeNetworkPath = path;


        if (!restoring) {
            if (network.getRenderedChild() != null) {
                setActiveNode(network.getRenderedChildName());
            } else if (!network.isEmpty()) {
                // Set the active node to the first child.
                setActiveNode(network.getChildren().iterator().next());
            } else {
                setActiveNode((Node) null);
            }
        }

        alternativePaneHeader.getAddressBar().setPath(activeNetworkPath);
        //viewer.setHandleEnabled(activeNode != null && activeNode.hasEnabledHandle());
        networkView.updateNodes();
        networkView.resetViewTransform();
        if (!restoring)
            //networkView.singleSelect(getActiveNode());
        	NetworkView.singleSelectSimple(getActiveNode(), networkView);
        viewerPane.repaint();
        dataSheet.repaint();

        requestRender();
        return network;
    }

    private Node getRenderedNode() {
        //return getNodeLibrary().getRoot();
        // if (viewerPane.shouldAlwaysRenderRoot()) return getNodeLibrary().getRoot();
        return getActiveNetwork();
    }

    /**
     * Set the active network to the parent network.
     */
    public void goUp() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /**
     * Return the node that is currently focused:
     * visible in the port view, and whose handles are displayed in the viewer.
     *
     * @return The active node. Can be null.
     */
    public Node getActiveNode() {
        if (activeNodeName.isEmpty()) {
            return getActiveNetwork();
        } else {
            return getNodeLibrary().getNodeForPath(getActiveNodePath());
        }
    }

    public String getActiveNodePath() {
        return Node.path(activeNetworkPath, activeNodeName);
    }

    public String getActiveNodeName() {
        return activeNodeName;
    }

    /**
     * Set the active node to the given node.
     * <p/>
     * The active node is the one whose parameters are displayed in the port pane,
     * and whose handle is displayed in the viewer.
     * <p/>
     * This will also change the active network if necessary.
     *
     * @param node the node to change to.
     */
    public void setActiveNode(Node node) {
        setActiveNode(node != null ? node.getName() : "");
    }

    public void setActiveNode(String nodeName) {
        if (!restoring && getActiveNodeName().equals(nodeName)) return;
        stopCombiningEdits();
        if (nodeName.isEmpty()) {
            activeNodeName = "";
        } else {
            checkArgument(getActiveNetwork().hasChild(nodeName));
            activeNodeName = nodeName;
        }

        Node n = getActiveNode();
        createHandleForActiveNode();
        //editorPane.setActiveNode(activeNode);
        // TODO If we draw handles again, we should repaint the viewer pane.
        //viewerPane.repaint(); // For the handle
        portView.updateAll();
        restoring = false;
        //networkView.singleSelect(n);
        NetworkView.singleSelectSimple(n, networkView);
    }

    private void createHandleForActiveNode() {
        Node activeNode = getActiveNode();
        if (activeNode != null) {
            Handle handle = null;

            if (getFunctionRepository().hasFunction(activeNode.getHandle())) {
                Function handleFunction = getFunctionRepository().getFunction(activeNode.getHandle());
                try {
                    handle = (Handle) handleFunction.invoke();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error while creating handle for " + activeNode, e);
                }
            }

            if (handle != null) {
                handle.setHandleDelegate(this);
                handle.update();
                viewerPane.setHandle(handle);
            } else {
                viewerPane.setHandle(null);
            }
        }
    }
//        if (activeNode != null) {
//            Handle handle = null;
//            try {
//                handle = activeNode.createHandle();
//                // If the handle was created successfully, remove the messages.
//                editorPane.clearMessages();
//            } catch (Exception e) {
//                editorPane.setMessages(e.toString());
//            }
//            if (handle != null) {
//                handle.setHandleDelegate(this);
//                // TODO Remove this. Find out why the handle needs access to the viewer (only repaint?) and put that in the HandleDelegate.
//                handle.setViewer(viewer);
//                viewer.setHandleEnabled(activeNode.hasEnabledHandle());
//            }
//            viewer.setHandle(handle);
//        } else {
//            viewer.setHandle(null);
//        }
//    }

    // todo: this method feels like it doesn't belong here (maybe rename it?)
    public boolean hasInput(String portName) {
        Node node = getActiveNode();
        return node.hasInput(portName);
    }

    public boolean isConnected(String portName) {
        Node network = getActiveNetwork();
        Node node = getActiveNode();
        for (Connection c : network.getConnections()) {
            if (c.getInputNode().equals(node.getName()) && c.getInputPort().equals(portName))
                return true;
        }
        return false;
    }




    //// Rendering ////

    /**
     * Request a renderNetwork operation.
     * <p/>
     * This method does a number of checks to see if the renderNetwork goes through.
     * <p/>
     * The renderer could already be running.
     * <p/>
     * If all checks pass, a renderNetwork request is made.
     */
    public void requestRender() {
        // If we're already rendering, request the next renderNetwork.
        if (isRendering.compareAndSet(false, true)) {
            // If we're not rendering, start rendering.
            render();
        } else {
            shouldRender.set(true);
        }
    }

    /**
     * Ask the document to stop the active rendering.
     */
    public synchronized void stopRendering() {
        if (currentRender != null) {
            currentRender.cancel(true);
        }
    }

    private void render() {
        checkState(SwingUtilities.isEventDispatchThread());
        checkState(currentRender == null);
        alternativePaneHeader.getProgressPanel().setInProgress(true);
        final NodeLibrary renderLibrary = getNodeLibrary();
        final Node renderNetwork = getRenderedNode();
        final ImmutableMap<String, ?> data = ImmutableMap.of(
                "mouse.position", viewerPane.getViewer().getLastMousePosition(),
                "osc.messages", oscMessages);
        final NodeContext context = new NodeContext(renderLibrary, getFunctionRepository(), animationManager.getAnimationFrame(), data, renderResults);
        currentRender = new SwingWorker<List<?>, Node>() {
            @Override
            protected List<?> doInBackground() throws Exception {
                List<?> results = context.renderNode(renderNetwork);
                context.renderAlwaysRenderedNodes(renderNetwork);
                renderResults = context.getRenderResults();
                return results;
            }

            @Override
            protected void done() {
                networkPane.clearError();
                isRendering.set(false);
                currentRender = null;
                List<?> results;
                try {
                    results = get();
                } catch (CancellationException e) {
                    results = ImmutableList.of();
                } catch (InterruptedException e) {
                    results = ImmutableList.of();
                } catch (ExecutionException e) {
                    networkPane.setError(e.getCause());
                    results = ImmutableList.of();
                }

                lastRenderResult = results;

                networkView.checkErrorAndRepaint();
                alternativePaneHeader.getProgressPanel().setInProgress(false);
                viewerPane.setOutputValues(results);

                if (shouldRender.getAndSet(false)) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            requestRender();
                        }
                    });
                }
            }
        };
        currentRender.execute();
    }

    /**
     * Returns the first output value, or null if the map of output values is empty.
     *
     * @param outputValues The map of output values.
     * @return The output value.
     */
    private Object firstOutputValue(final Map<String, Object> outputValues) {
        if (outputValues.isEmpty()) return null;
        return outputValues.values().iterator().next();
    }

    public synchronized void resetRenderResults() {
        renderResults = ImmutableMap.of();
    }

    //// Undo ////

    /**
     * Edits are no longer recorded until you call stopEdits. This allows you to batch edits.
     *
     * @param command the command name of the edit batch
     */
    public void startEdits(String command) {
        addEdit(command);
        holdEdits = true;
    }

    /**
     * Edits are recorded again.
     */
    public void stopEdits() {
        holdEdits = false;
    }

    /**
     * Add an edit to the undo manager.
     * <p/>
     * Since we don't specify the edit type or name, further edits will not be staggered.
     *
     * @param command the command name.
     */
    public void addEdit(String command) {
        if (!holdEdits) {
            markChanged();
            undoManager.addEdit(new NodeLibraryUndoableEdit(this, command));
            menuBar.updateUndoRedoState();
            stopCombiningEdits();
        }
    }

    /**
     * Add an edit to the undo manager.
     *
     * @param command  the command name.
     * @param type     the type of edit
     * @param objectId the id for the edited object. This will be compared against.
     */
    public void addEdit(String command, String type, String objectId) {
        if (!holdEdits) {
            markChanged();

            if (lastEditType != null && lastEditType.equals(type) && lastEditObjectId.equals(objectId)) {
                // If the last edit type and last edit id are the same,
                // we combine the two edits into one.
                // Since we've already saved the last state, we don't need to do anything.
            } else {
                addEdit(command);
                lastEditType = type;
                lastEditObjectId = objectId;
            }
        }
    }

    /**
     * Normally edits of the same type and object are combined into one.
     * Calling this method will ensure that you create a  new edit.
     * <p/>
     * Use this method e.g. for breaking apart overzealous edit grouping.
     */
    public void stopCombiningEdits() {
        // We just reset the last edit type and object so that addEdit will be forced to create a new edit.
        lastEditType = null;
        lastEditObjectId = null;
        stopEdits();
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public void undo() {
        if (!undoManager.canUndo()) return;
        //log undo
        appFrame.log(getDocumentName(), String.valueOf(getDocumentNo()), "Undo",  undoManager.getUndoPresentationName());
        undoManager.undo();
        menuBar.updateUndoRedoState();
    }

    public void redo() {
        if (!undoManager.canRedo()) return;
        //log undo
        appFrame.log(getDocumentName(), String.valueOf(getDocumentNo()), "Redo",  undoManager.getRedoPresentationName());
        undoManager.redo();
        menuBar.updateUndoRedoState();
    }

    //// Code editor actions ////

    public void fireCodeChanged(Node node, boolean changed) {
        networkView.codeChanged(node, changed);
    }

    //// Document actions ////

    public File getDocumentFile() {
        return documentFile;
    }

    public void setDocumentFile(File documentFile) {
        this.documentFile = documentFile;
        controller.setNodeLibraryFile(documentFile);
        updateTitle();
    }

    public boolean isChanged() {
        return documentChanged;
    }

    //we may be calling it too many times
    public boolean close() {
    	//broken for now
    	animationManager.stopAnimation();
        if (shouldClose()) {
            //Application.getInstance().removeAppFrame(getDocumentFrame());
            if (oscP5 != null) {
                oscP5.stop();
            }

            getDocumentFrame().dispose();
            // On Mac the application does not close if the last window is closed.
            if (!Platform.onMac()) {
                // If there are no more documents, exit the application.
                if (Application.getInstance().getDocumentCount() == 0) {
                    System.exit(0);
                }
            }
            return true;
        } else {
            return false;
        }
    }
    
    public void closeDocument() {


    	// check if user wants to save the project
    	if (isChanged()) {
    		// always bring the current window in the front (user needs feedback)
    		appFrame.toFront();
    		// ask if user wants to save changes
    		if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(appFrame, this.getDocumentName() + " has changed. Do you want to save this alternative?", "Do you want to save this alternative?", JOptionPane.YES_NO_OPTION))
                saveAs();
    	}
    	
    	// remove document from document list, mapping and panel
        //select next document
    	documentGroup.remove(this);
        appFrame.removeDocumentFromMap(this);
    	appFrame.setNextDocument();
    	appFrame.resetGlobals();		
    }


    public JPanel getRootPanel() {
    	return rootPanel;
    }
    
    private boolean shouldClose() {
    	//broken for now
    	if (isChanged()) {
            SaveDialog sd = new SaveDialog();
            int retVal = sd.show(getDocumentFrame());
            if (retVal == JOptionPane.YES_OPTION) {
                return save();
            } else if (retVal == JOptionPane.NO_OPTION) {
                return true;
            } else if (retVal == JOptionPane.CANCEL_OPTION) {
                return false;
            }
        }
    	return true;
    }

    public boolean save() {
        if (documentFile == null) {
            return saveAs();
        } else {
            return saveToFile(documentFile);
        }
    }

    public boolean saveAs() {
    	//broken for now
    	File chosenFile = FileUtils.showSaveDialog(getDocumentFrame(), lastFilePath, "ndbx", "NodeBox File");
        if (chosenFile != null) {
            if (!chosenFile.getAbsolutePath().endsWith(".ndbx")) {
                chosenFile = new File(chosenFile.getAbsolutePath() + ".ndbx");
                if (chosenFile.exists()) {
                    ReplaceDialog rd = new ReplaceDialog(chosenFile);
                    int retVal = rd.show(getDocumentFrame());
                    if (retVal == JOptionPane.CANCEL_OPTION)
                        return saveAs();
                }
            }
            lastFilePath = chosenFile.getParentFile().getAbsolutePath();
            setDocumentFile(chosenFile);
            menuBar.addRecentFile(documentFile);
            return saveToFile(documentFile);
        }
        return false;
    }

    public void revert() {
        // TODO: Implement revert
        JOptionPane.showMessageDialog(getDocumentFrame(), "Revert is not implemented yet.", "NodeBox", JOptionPane.ERROR_MESSAGE);
    }

    private boolean saveToFile(File file) {
        try {
            getNodeLibrary().store(file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(getDocumentFrame(), "An error occurred while saving the file.", "NodeBox", JOptionPane.ERROR_MESSAGE);
            LOG.log(Level.SEVERE, "An error occurred while saving the file.", e);
            return false;
        }
        documentChanged = false;
        updateTitle();
        appFrame.log("Save alternative", file.getAbsolutePath());
        return true;
    }

    private void markChanged() {
        if (!documentChanged && loaded) {
            documentChanged = true;
            updateTitle();
            getDocumentFrame().getRootPane().putClientProperty(WINDOW_MODIFIED, Boolean.TRUE);
        }
    }

    private void updateTitle() {
        //broken for now
    	/*String postfix = "";
        if (!Platform.onMac()) {
            postfix = (documentChanged ? " *" : "");
        } else {
            getRootPane().putClientProperty("Window.documentModified", documentChanged);
        }
        if (documentFile == null) {
            setTitle("Untitled" + postfix);
        } else {
            setTitle(documentFile.getName() + postfix);
            getRootPane().putClientProperty("Window.documentFile", documentFile);
        }*/
    }

    public void focusNetworkView() {
        networkView.requestFocus();
    }

    //// Export ////

    private ImageFormat imageFormatForFile(File file) {
        if (file.getName().toLowerCase().endsWith(".pdf"))
            return ImageFormat.PDF;
        return ImageFormat.PNG;
    }

    public void doExport() {
    	//broken for now
/*        ExportDialog d = new ExportDialog(this);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
        if (!d.isDialogSuccessful()) return;
        nodebox.ui.ImageFormat chosenFormat = d.getFormat();
        File chosenFile = FileUtils.showSaveDialog(this, lastExportPath, "png,pdf", "Image file");
        if (chosenFile == null) return;
        lastExportPath = chosenFile.getParentFile().getAbsolutePath();
        exportToFile(chosenFile, chosenFormat);*/
    }

    private void exportToFile(File file, ImageFormat format) {
        // get data from last export.
        if (lastRenderResult == null) {
            JOptionPane.showMessageDialog(getDocumentFrame(), "There is no last render result.");
        } else {
            exportToFile(file, lastRenderResult, format);
        }
    }

    private void exportToFile(File file, Iterable<?> objects, ImageFormat format) {
        file = format.ensureFileExtension(file);
        ObjectsRenderer.render(objects, file);
    }

    public boolean exportRange() {
    	//broken for now
        File exportDirectory = lastExportPath == null ? null : new File(lastExportPath);
        if (exportDirectory != null && !exportDirectory.exists())
            exportDirectory = null;
        ExportRangeDialog d = new ExportRangeDialog(getDocumentFrame(), exportDirectory);
        d.setLocationRelativeTo(getDocumentFrame());
        d.setVisible(true);
        if (!d.isDialogSuccessful()) return false;
        String exportPrefix = d.getExportPrefix();
        File directory = d.getExportDirectory();
        int fromValue = d.getFromValue();
        int toValue = d.getToValue();
        nodebox.ui.ImageFormat format = d.getFormat();
        if (directory == null) return false;
        lastExportPath = directory.getAbsolutePath();
        exportRange(exportPrefix, directory, fromValue, toValue, format);
        return true;
    }

    public void exportRange(final String exportPrefix, final File directory, final int fromValue, final int toValue, final ImageFormat format) {
        exportThreadedRange(getNodeLibrary(), fromValue, toValue, new ExportDelegate() {
            int count = 1;

            @Override
            public void frameDone(double frame, Iterable<?> results) {
                File exportFile = new File(directory, exportPrefix + "-" + String.format("%05d", count));
                exportToFile(exportFile, results, format);
                count += 1;
            }
        });
    }

    public boolean exportMovie() {
    	//broken for now
        ExportMovieDialog d = new ExportMovieDialog(getDocumentFrame(), lastExportPath == null ? null : new File(lastExportPath));
        d.setLocationRelativeTo(getDocumentFrame());
        d.setVisible(true);
        if (!d.isDialogSuccessful()) return false;
        File chosenFile = d.getExportPath();
        if (chosenFile != null) {
            lastExportPath = chosenFile.getParentFile().getAbsolutePath();
            exportToMovieFile(chosenFile, d.getVideoFormat(), d.getFromValue(), d.getToValue());
            return true;
        }
        
        return false;
    }

    private int getCanvasWidth() {
        try {
            return Integer.parseInt(getNodeLibrary().getProperty("canvasWidth", "1000"));
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    private int getCanvasHeight() {
        try {
            return Integer.parseInt(getNodeLibrary().getProperty("canvasHeight", "1000"));
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    private int getOSCPort() {
        try {
            return Integer.parseInt(getNodeLibrary().getProperty("oscPort", "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }


    private void exportToMovieFile(File file, final VideoFormat videoFormat, final int fromValue, final int toValue) {
        file = videoFormat.ensureFileExtension(file);
        final int width = getCanvasWidth();
        final int height = getCanvasHeight();
        final Movie movie = new Movie(file.getAbsolutePath(), videoFormat, width, height, false);
        exportThreadedRange(controller.getNodeLibrary(), fromValue, toValue, new ExportDelegate() {
            @Override
            public void frameDone(double frame, Iterable<?> results) {
                movie.addFrame(ObjectsRenderer.createMovieImage(results, width, height));
            }

            @Override
            void exportDone() {
                progressDialog.setTitle("Converting frames to movie...");
                progressDialog.reset();
                FramesWriter w = new FramesWriter(progressDialog);
                movie.save(w);
            }
        });
    }

    private abstract class ExportDelegate {
        protected InterruptibleProgressDialog progressDialog;

        void frameDone(double frame, Iterable<?> results) {
        }

        void exportDone() {
        }
    }

    private void exportThreadedRange(final NodeLibrary library, final int fromValue, final int toValue, final ExportDelegate exportDelegate) {
        //broken for now
    	int frameCount = toValue - fromValue;
        final InterruptibleProgressDialog d = new InterruptibleProgressDialog(getDocumentFrame(), "Exporting " + frameCount + " frames...");
        d.setTaskCount(toValue - fromValue + 1);
        d.setVisible(true);
        exportDelegate.progressDialog = d;

        final NodeLibrary exportLibrary = getNodeLibrary();
        final FunctionRepository exportFunctionRepository = getFunctionRepository();
        final Node exportNetwork = library.getRoot();
        final Viewer viewer = new Viewer(this);

        final JFrame frame = new JFrame();
        frame.setLayout(new BorderLayout());
        frame.setSize(600, 600);
        frame.setTitle("Exporting...");
        frame.add(viewer, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);

        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Map<Node, List<?>> renderResults = ImmutableMap.of();
                    for (int frame = fromValue; frame <= toValue; frame++) {
                        if (Thread.currentThread().isInterrupted())
                            break;
                        final ImmutableMap<String, ?> data = ImmutableMap.of(
                                "mouse.position", viewer.getLastMousePosition(),
                                "osc.messages", oscMessages);
                        NodeContext context = new NodeContext(exportLibrary, exportFunctionRepository, frame, data, renderResults);

                        List<?> results = context.renderNode(exportNetwork);
                        renderResults = context.getRenderResults();
                        viewer.setOutputValues((List<?>) results);
                        exportDelegate.frameDone(frame, results);

                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                d.tick();
                            }
                        });
                    }
                    exportDelegate.exportDone();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error while exporting", e);
                } finally {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            d.setVisible(false);
                            frame.setVisible(false);
                        }
                    });
                }
            }
        });
        d.setThread(t);
        t.start();
        frame.setVisible(true);
    }

    //// Copy / Paste ////

    private class NodeClipboard {
        //replace nodes with new uuids based if copied within the same document then create nodes with new uuids , if between different documents, then popup a dialog asking if you want to replace
        //nodes or create copies (keep in mind that you could copy selections of nodes with edges and stuff, not only individual nodes)
    	//if user chooses to replace then replace, if not then create nodes with new uuids just like in the case of copying within a document
        private final Node network;
        private final ImmutableList<Node> nodes;
        //add document here, thats how you would know you are in the right document
        private final NodeBoxDocument document;
        

        private NodeClipboard(NodeBoxDocument document, Node network, Iterable<Node> nodes) {
            this.document = document;
        	this.network = network;
            this.nodes = ImmutableList.copyOf(nodes);
        }


		public NodeBoxDocument getDocument() {
			return document;
		}
    }

    //We don't need cut function anymore
    public void cut() {
        copy();
        networkView.deleteSelection();
    }

    public void copy() {
        // When copying, save a reference to the nodes and the parent network.
        // Since the model is immutable, we don't need to make defensive copies.
        nodeClipboard = new NodeClipboard(this, getActiveNetwork(), networkView.getSelectedNodes());

        //log
        String strCBNodes = getStringOfNodesIterable(nodeClipboard.nodes);
		log(getDocumentName(),"Copy", activeNetworkPath, strCBNodes);
    }

    //Merge
    public void merge(){
    	//first we copy selected nodes
    	copy();
    	//if nothing to merge then return;
    	ImmutableList<Node> nodes = ImmutableList.copyOf(networkView.getSelectedNodes());
    	if (nodes.size() == 0) return;
    	//if (nodeClipboard.nodes.size() == 0) return;
    	
    	//There are two steps:
    	//in the first step the nodes that already exist are updated with overriding or merging
    	//in the second step the nodes that don't exist are added and connected
        //NodeBoxDocument nodeClipboardDocument = nodeClipboard.getDocument();
        //set the current document to the clipboard document (for consistency)
        /*if (nodeClipboardDocument != this){
        	//set clipboard document as current document
        	appFrame.setCurrentDocument(nodeClipboardDocument);
        }*/
        
        //if you ever plan to support grouping, you will have to do some work here to switch to right networks and stuff
        //for now it assumes there is only one network, no groups

        Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
        Set<String> affectedAlternatives = new HashSet<String>();
		String strCBNodes = getStringOfNodesIterable(nodeClipboard.nodes);


        for (int i = 0; i < documentGroup.size(); i++){
    		NodeBoxDocument doc = documentGroup.get(i);
    		for (Node node : nodeClipboard.nodes){
    			//if this is the nodeClipboardDocument, then simply mark it, but dont do anything 
    			if (doc == this && doc.alternativePaneHeader.isEditable()){
    				doc.startEdits("Merge");
    				globalUndoEntry[i] = true;
    			}
    			else if (doc != this && doc.alternativePaneHeader.isEditable()){
                    doc.startEdits("Merge");
        	        globalUndoEntry[i] = true;
            		addNodesAndConnectors(doc, node);
            		doc.stopEdits();
            		log(doc.getDocumentName(), "Merge","source" + ApplicationFrame.attributeDelimiter + getDocumentName(), doc.activeNetworkPath, getActiveNetwork().getName(), node.getName());
        	        affectedAlternatives.add(doc.getDocumentName());


            	}
            	else
        	        globalUndoEntry[i] = false;
            }
            
			doc.portView.updateAll();
			doc.viewerPane.updateHandle();
			doc.networkView.updateAll();
			doc.requestRender();	
        }
        boolean atLeastOneTrue = false;
        for (int i = 0; i < globalUndoEntry.length; i++)
        	atLeastOneTrue = globalUndoEntry[i] || atLeastOneTrue;
        if (atLeastOneTrue){
        	appFrame.addGlobalUndoEntry(globalUndoEntry);
        	log(affectedAlternatives, "Merge","source=" + getDocumentName(), strCBNodes, activeNetworkPath);

        }
        appFrame.printGlobalUndoStack();
    }
    
    /**
     * This is a helper function is also used in the Design Gallery. It is used to generate powersets.
     * Connections originiate from this document
     * @param nodes The node to remove.
     */
	public void addNodesAndConnectors(NodeBoxDocument doc, Node node) {
		//we only do updating in the nonclipboard document
		if (doc.getActiveNetwork().hasChild(node.getName())){
			System.out.println("Doc #" + doc.getDocumentNo() + " has child " + node.getName());
			//do merging or overriding here
			Node nodeInDoc = doc.getActiveNetwork().getChild(node.getName());
			doc.controller.replaceNodeInPath(Node.path(doc.activeNetworkPath, nodeInDoc), node);
		}
		else
		{
			_createNodeSubroute(doc, node.getPrototype(), node.getUUID(), node.getPosition(), node.getName()); //create a copy of this node in a masterdocument at the same position
			if (doc.getActiveNetwork().hasChild(node.getName())){
				Node nodeInDoc = doc.getActiveNetwork().getChild(node.getName());
				doc.controller.replaceNodeInPath(Node.path(doc.activeNetworkPath, nodeInDoc), node);
			}
		}
		//connect
		for (Connection connection : getActiveNetwork().getConnections()){
			String input = connection.getInputNode();
			String output = connection.getOutputNode();
			System.out.println("input: " + input);
			System.out.println("output: " + output);
			
			if (input.equals(node.getName())){
				System.out.println("master node input");

				//if output node exists in master view then create  a connection
				//Node masterNode = NodeBoxDocument.getNodeByUUIDfromDoc(masterDocument, getNodeLibrary().getRoot().getChild(input).getUUID());
				//simpler way
				
				Node inputNode = doc.getActiveNetwork().getChild(input); //node missing in the master
				Node outputNode = doc.getActiveNetwork().getChild(output);
				
				System.out.println("found corresponding node in master:" + inputNode.getName());
				if (outputNode != null){
					doc.controller.connect(doc.activeNetworkPath, outputNode, inputNode, connection.getInputPort());
				}
			}
			else if (output.equals(node.getName())){
				System.out.println("master node output");

				//if input node exists in master view then create  a connection
				//Node masterNode = NodeBoxDocument.getNodeByUUIDfromDoc(masterDocument, getNodeLibrary().getRoot().getChild(input).getUUID());
				//simpler way
				Node inputNode = doc.getActiveNetwork().getChild(input); //node missing in the master
				Node outputNode = doc.getActiveNetwork().getChild(output);
				
				if (inputNode != null){
					doc.controller.connect(doc.activeNetworkPath, outputNode, inputNode, connection.getInputPort());
				}
			}
		}
	}
    
    public void paste() {
        if (nodeClipboard == null) return;
        if (nodeClipboard.nodes.size() == 0) return; //only do the paste if the selection was not empty
        NodeBoxDocument nodeClipboardDocument = nodeClipboard.getDocument();
        List<Node> newNodes = null;
        
        if (nodeClipboardDocument != this){
        	//set clipboard document as current document
        	appFrame.setCurrentDocument(nodeClipboardDocument);
        }
    	         

    	//System.out.println("pasting to same document");
    	List<UUID> newUUIDs = new ArrayList<java.util.UUID>();
    	List<String> newNames = new ArrayList<String>(); //nodes created during paste in the first document

    	Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
        Set<String> affectedAlternatives = new HashSet<String>();
    	
    	////////////////////////////////////////////////////////////////
    	//used for logging of paste
		String strCBNodes = getStringOfNodesIterable(nodeClipboard.nodes);
		////////////////////////////////////////////////////////////////

        for (int i = 0 ; i < documentGroup.size(); i++){
        	NodeBoxDocument doc = documentGroup.get(i);
    		newNodes = new ArrayList<Node>();

        	//first lets paste it in the current view
            //only paste if document is synched or if this is the document belonging to the clipboard but not if clipboard generating document (current document) has synched enables but others do... yeah thats the long logic jere
        	//if ((doc.viewerPane.unlockedEnabled() || nodeClipboardDocument == doc) && !(doc.viewerPane.unlockedEnabled() && !nodeClipboardDocument.viewerPane.unlockedEnabled() && doc != nodeClipboardDocument))
    		if (doc.alternativePaneHeader.isEditable()){ 
        		
    			newNodes = doc.controller.pasteNodesAlt(doc.activeNetworkPath, nodeClipboard.network, nodeClipboard.nodes, 4, 2);
        		doc.addEdit("Paste node");
        		log(doc.getDocumentName(),"Paste node", doc.activeNetworkPath, nodeClipboard.network.getName(), strCBNodes);
    	        globalUndoEntry[i] = true;
    	        affectedAlternatives.add(doc.getDocumentName());
    	        
        		System.out.println("new nodes:" + newNodes);
	        	//generate new uuids
        		if (newUUIDs.size() == 0){
        			Iterator<Node> iterator = newNodes.iterator();
    	        	while (iterator.hasNext()) {
    	        		Node node = iterator.next();
    	        		//String nodeName = node.getName();
    	        		UUID uuid = UUID.randomUUID();
    	        		newUUIDs.add(uuid);
    	        		newNames.add(node.getName());
    	        		//we don't replace then name first time
    	        		doc.controller.replaceNodeInPath(Node.path(doc.activeNetworkPath, node), node.withUUID(uuid));

        			}
    	        	System.out.println("new names:" + newNames);
        		}
    			Iterator<Node> newNodeIterator = newNodes.iterator();
    			Iterator<UUID> uuidIterator = newUUIDs.iterator();
    			Iterator<String> nameIterator = newNames.iterator();


        		while (newNodeIterator.hasNext())
        		{
	        		Node node = newNodeIterator.next();
	        		Node newNode = node.withUUID(uuidIterator.next());
	        		//second time we replace both the uuid and the names that were generated in the first paste, because we dont need to create unique names anymore
            		doc.controller.replaceNodeInPath(Node.path(doc.activeNetworkPath, node), newNode);
	    	        doc.controller.renameNode(doc.activeNetworkPath, newNode.getName(), nameIterator.next());
        		}

            	doc.networkView.updateAll();
            //	doc.setActiveNode(newNodes.get(0));
            //	doc.networkView.select(newNodes);
        	}
    		else 
    	        globalUndoEntry[i] = false;
         }
        //don't add to stack if all pastes are false
        boolean atLeastOneTrue = false;
        for (int i = 0; i < globalUndoEntry.length; i++)
        	atLeastOneTrue = globalUndoEntry[i] || atLeastOneTrue;
        if (atLeastOneTrue){
        	appFrame.addGlobalUndoEntry(globalUndoEntry);
        	log(affectedAlternatives, "Paste node", strCBNodes, activeNetworkPath);
        }
        appFrame.printGlobalUndoStack();
    }
    /**
     * used for logging of clipboard and selected nodes
     * @return String containing names of clipboard nodes separated by 'logDelimiter'
     */
	public static String getStringOfNodesIterable(Iterable<Node> iterable) {
		//create a string list of selected Nodes
		String nodes = "nodes" + ApplicationFrame.attributeDelimiter;
		for (Node cbNode : iterable){
			nodes += cbNode.getName() + ApplicationFrame.itemDelimiter;
		}
		//remove last delimiter character
		nodes = nodes.substring(0, nodes.length() - 1);
		return nodes;
	}

    public void dragCopy() {
        Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
        Set<String> affectedAlternatives = new HashSet<String>();
    	List<UUID> newUUIDs = new ArrayList<java.util.UUID>();
    	List<String> newNames = new ArrayList<String>(); //nodes created during paste in the first document

    	////////////////////////////////////////////////////////////////
    	//used for logging of paste
		String strCBNodes = getStringOfNodesIterable(networkView.getSelectedNodes());
		////////////////////////////////////////////////////////////////

		for (int i = 0; i < documentGroup.size(); i++)
        {
	        NodeBoxDocument doc = documentGroup.get(i);
	        if (!doc.alternativePaneHeader.isEditable()){ //skip if it's disabled
		        globalUndoEntry[i] = false;
	        	continue;
	        }
	        
	        globalUndoEntry[i] = true;
	        affectedAlternatives.add(doc.getDocumentName());

	        doc.startEdits("Drag Copy");
	        																		//it could be doc.getActiveNetwork() too, not sure, change if anything
	        List<Node> newNodes = doc.controller.pasteNodes(doc.activeNetworkPath, getActiveNetwork(), networkView.getSelectedNodes(), 0, 0);
    		log(doc.getDocumentName(),"Drag Copy", doc.activeNetworkPath, getActiveNetwork().getName(), strCBNodes);

        	//generate new uuids
    		if (newUUIDs.size() == 0){
    			Iterator<Node> iterator = newNodes.iterator();
	        	while (iterator.hasNext()) {
	        		Node node = iterator.next();
	        		//String nodeName = node.getName();
	        		UUID uuid = UUID.randomUUID();
	        		newUUIDs.add(uuid);
	        		newNames.add(node.getName());
	        		//we don't replace then name first time
	        		doc.controller.replaceNodeInPath(Node.path(doc.activeNetworkPath, node), node.withUUID(uuid));

    			}
    		}
			Iterator<Node> newNodeIterator = newNodes.iterator();
			Iterator<UUID> uuidIterator = newUUIDs.iterator();
			Iterator<String> nameIterator = newNames.iterator();


    		while (newNodeIterator.hasNext())
    		{
        		Node node = newNodeIterator.next();
        		Node newNode = node.withUUID(uuidIterator.next());
        		//second time we replace both the uuid and the names that were generated in the first paste, because we dont need to create unique names anymore
        		doc.controller.replaceNodeInPath(Node.path(doc.activeNetworkPath, node), newNode);
    	        doc.controller.renameNode(doc.activeNetworkPath, newNode.getName(), nameIterator.next());
    		}

	        doc.networkView.updateAll();
	        doc.networkView.select(newNodes);
        }
    	appFrame.addGlobalUndoEntry(globalUndoEntry);
    	log(affectedAlternatives, "Paste node", strCBNodes, activeNetworkPath);
    }

    public void groupIntoNetwork(nodebox.graphics.Point pt) {
        String networkName = getActiveNetwork().uniqueName("network");
        String name = JOptionPane.showInputDialog(getDocumentFrame(), "Network name:", networkName);
        if (name == null) return;

        Boolean[] globalUndoEntry = appFrame.createNewGlobalUndoEntry();
        Set<String> affectedAlternatives = new HashSet<String>();

		UUID uuid = UUID.randomUUID();

		Iterable<Node> selectedNodesInCurrentDocument = networkView.getSelectedNodes();
        for (int i = 0; i < documentGroup.size(); i++)
        {
	        NodeBoxDocument doc = documentGroup.get(i);
	        if (!doc.alternativePaneHeader.isEditable()){ //skip if it's disabled
		        globalUndoEntry[i] = false;
	        	continue;
	        }
	        
	        doc.startEdits("Group into Network");
	        //String renderedChild = doc.getActiveNetwork().getRenderedChildName();
	        
	        Iterable<Node> selectedNodes;

	        if (doc == this){
	        	selectedNodes  = doc.networkView.getSelectedNodes();
	        }
	        else{ //for other documents you need to find matching nodes by uuids
	        	ImmutableList.Builder<Node> selectedNodesBuilder = new ImmutableList.Builder<nodebox.node.Node>();
		        for (Node node : selectedNodesInCurrentDocument) {
		        	Node foundNode = NodeBoxDocument.getNodeByUUIDfromDoc(doc, node.getUUID());
		        	if (foundNode != null) //if this node exists in this document then add it
		        		selectedNodesBuilder.add(foundNode);
		        }	
		        //overwrite selected nodes
		        selectedNodes = selectedNodesBuilder.build();
	        }
	        System.out.println("nodes to be grouped:" + selectedNodes + " in " + doc.getDocumentName());
	        Node subnet = doc.controller.groupIntoNetwork(doc.activeNetworkPath, selectedNodes, networkName, uuid);

	        doc.controller.setNodePosition(Node.path(doc.activeNetworkPath, subnet.getName()), pt);
	        /*if (renderedChild.equals(subnet.getRenderedChildName()))
	            doc.controller.setRenderedChild(doc.activeNetworkPath, subnet.getName());
	            */

	        if (!name.equals(subnet.getName())) {
	            doc.controller.renameNode(doc.activeNetworkPath, subnet.getName(), name);
	            subnet = doc.getActiveNetwork().getChild(name);
	        }

	        doc.stopEdits();
	        String strNodes = getStringOfNodesIterable(selectedNodes);
    		log(doc.getDocumentName(),"Group into Network", doc.activeNetworkPath, networkName, strNodes);


	        doc.setActiveNode(subnet);
	        doc.networkView.updateAll();
	        //doc.networkView.select(subnet);
	        doc.requestRender();
	        globalUndoEntry[i] = true;
	        affectedAlternatives.add(doc.getDocumentName());

        }
        appFrame.addGlobalUndoEntry(globalUndoEntry);
    	log(affectedAlternatives, "Group into Network", networkName, activeNetworkPath);

        //appFrame.printGlobalUndoStack();
    }

    /**
     * Start the dialog that allows a user to create a new node.
     */
    public void showNodeSelectionDialog() {
        showNodeSelectionDialog(networkView.centerGridPoint(), null);
    }
    
    /**
     * Start the dialog that allows a user to replace a existing node.
     * 
     * @param categorie Show only nodes from the given category.
     */
    public void showNodeSelectionDialogReplace(Node node) {
        showNodeSelectionDialog(networkView.centerGridPoint(), node);
    }
    
    

    /**
     * Start the dialog that allows a user to create a new node or replace
     * a existing one.
     *
     * @param pt The point in "grid space"
     * @param node If null create new node otherwise replace 
     */
    public void showNodeSelectionDialog(Point pt, Node node) {
        NodeRepository repository = getNodeRepository();
        String category = null;
        if (node != null) // filter repository by category because we want to replace a node
        	category = node.getCategory();
        appFrame.nodeSelectionDialog = new NodeSelectionDialog(getDocumentFrame(), controller.getNodeLibrary(), repository, category);
        appFrame.nodeSelectionDialog.setVisible(true);
        if (appFrame.nodeSelectionDialog.getSelectedNode() != null) {
        	if (node == null)
        		createNode(appFrame.nodeSelectionDialog.getSelectedNode(), new nodebox.graphics.Point(pt));
        	else
        		createNode(appFrame.nodeSelectionDialog.getSelectedNode(), node);
        }
    }

    public void showCodeLibraries() {
        CodeLibrariesDialog dialog = new CodeLibrariesDialog(appFrame, getNodeLibrary().getFunctionRepository());
        dialog.setVisible(true);
        FunctionRepository functionRepository = dialog.getFunctionRepository();
        if (functionRepository != null) {
            addEdit("Change function repository");
            log(getDocumentName(),"Change function repository");
            controller.setFunctionRepository(functionRepository);
            invalidateFunctionRepository = true;
            requestRender();
        }
    }

    public void showDocumentProperties() {
        DocumentPropertiesDialog dialog = new DocumentPropertiesDialog(appFrame);
        dialog.setVisible(true);
        if (dialog.isCommitted()) {
            addEdit("Change document properties");
            log(getDocumentName(),"Change document properties");

            controller.setProperties(dialog.getProperties());
            requestRender();
        }
    }

    public void reload() {
        controller.reloadFunctionRepository();
        functionRepository.invalidateFunctionCache();
        requestRender();
    }

    private class FramesWriter extends StringWriter {
        private final ProgressDialog dialog;

        public FramesWriter(ProgressDialog d) {
            super();
            dialog = d;
        }

        @Override
        public void write(String s, int n1, int n2) {
            super.write(s, n1, n2);
            if (s.startsWith("frame=")) {
                int frame = Integer.parseInt(s.substring(6, s.indexOf("fps")).trim());
                dialog.updateProgress(frame);
            }
        }
    }
    
	/*public void copyNodeToDocument(Node node, NodeBoxDocument doc) {
		// TODO Auto-generated method stub
		//NodeBoxDocument doc = appFrame.getMasterDocument();
		//_createNodeSubroute(doc, prototype, uuid, pt, strUniqueName);
		_createNodeSubroute(doc, node, node.getUUID(), node.getPosition(), node.getName()); //create a copy of this node in a masterdocument at the same position
		
		//create all adjacent ports for all nodes that exist in the master
		System.out.println("there are connections:" + getActiveNetwork().getConnections());
		for (Connection connection : getActiveNetwork().getConnections()){
			String input = connection.getInputNode();
			String output = connection.getOutputNode();
			System.out.println("input: " + input);
			System.out.println("output: " + output);
			
			if (input.equals(node.getName())){
				System.out.println("master node input");

				//if output node exists in master view then create  a connection
				//Node masterNode = NodeBoxDocument.getNodeByUUIDfromDoc(masterDocument, getNodeLibrary().getRoot().getChild(input).getUUID());
				//simpler way
				Node inputNode = doc.getNodeLibrary().getRoot().getChild(input); //node missing in the master
				Node outputNode = doc.getNodeLibrary().getRoot().getChild(output);
				
				System.out.println("found corresponding node in master:" + inputNode.getName());
				if (outputNode != null){
					doc.controller.connect(doc.activeNetworkPath, outputNode, inputNode, connection.getInputPort());
				}
			}
			else if (output.equals(node.getName())){
				System.out.println("master node output");

				//if input node exists in master view then create  a connection
				//Node masterNode = NodeBoxDocument.getNodeByUUIDfromDoc(masterDocument, getNodeLibrary().getRoot().getChild(input).getUUID());
				//simpler way
				Node inputNode = doc.getNodeLibrary().getRoot().getChild(input); //node missing in the master
				Node outputNode = doc.getNodeLibrary().getRoot().getChild(output);
				
				if (inputNode != null){
					doc.controller.connect(doc.activeNetworkPath, outputNode, inputNode, connection.getInputPort());
				}
			}
		}

		//updating everything
		for (NodeBoxDocument document : appFrame.getDocuments()){
			document.portView.updateAll();
			document.viewerPane.updateHandle();
			document.networkView.updateAll();
			document.requestRender();	        
		}
	}*/
	
    public boolean openDocument(File file) {
        // Check if the document is already open.
    	/*
       String path;
        try {
            path = file.getCanonicalPath();
            for (ApplicationFrame frame : Application.getInstance().getAppFrames()) {
                try {
                    if (frame.getDocumentFile() == null) continue;
                    if (frame.getDocumentFile().getCanonicalPath().equals(path)) {
                        // The document is already open. Bring it to the front.
                        frame.toFront();
                        frame.requestFocus();
                        NodeBoxMenuBar.addRecentFile(file);
                        return true;
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "The document " + frame.getDocumentFile() + " refers to path with errors", e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "The document " + file + " refers to path with errors", e);
        }
        */
    	try {
    		//for now just open in the first frame
            NodeBoxDocument doc = NodeBoxDocument.load(appFrame, file);
            //addDocument(doc); //disables for now
            //dont do this anymore
            //frame.setCurrentDocument(doc);
            menuBar.addRecentFile(file);
            documentFile = file;
            return true;
        } catch (RuntimeException e) {
            Application.getInstance().getLogger().log(Level.SEVERE, "Error while loading " + file, e);
            ExceptionDialog d = new ExceptionDialog(null, e);
            d.setVisible(true);
            return false;
        }
        
    }
	public static void centerAndZoomAllZoomableViews(final NodeBoxDocument doc, final boolean relativeToAll){
	    SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
        		//center and zoom model and network
            	doc.getNetworkView().centerAndZoom(relativeToAll);
            	doc.getViewerPane().getViewer().centerAndZoom(relativeToAll);
            }
        });
	}
}
