package nodebox.client;

import static com.google.common.base.Preconditions.checkNotNull;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import nodebox.node.Connection;
import nodebox.node.InvalidNameException;
import nodebox.node.Node;
import nodebox.node.NodeLibrary;
import nodebox.node.NodePort;
import nodebox.node.NodeRepository;
import nodebox.node.Port;
import nodebox.node.TypeConversions;
import nodebox.ui.PaneView;
import nodebox.ui.Platform;
import nodebox.ui.Theme;
import nodebox.ui.Zoom;

import org.python.google.common.base.Joiner;

import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

//import java.util.Iterator;


public class NetworkView extends ZoomableView implements PaneView, Zoom {

    public static final int GRID_CELL_SIZE = 48;
    public static final int NODE_MARGIN = 6;
    public static final int NODE_PADDING = 5;
    public static final int NODE_WIDTH = GRID_CELL_SIZE * 4 - NODE_MARGIN * 2;
    public static final int NODE_HEIGHT = GRID_CELL_SIZE - NODE_MARGIN * 2;
    public static final int NODE_ICON_SIZE = 26;
    public static final int GRID_OFFSET = 6;
    public static final int PORT_WIDTH = 10;
    public static final int PORT_HEIGHT = 3;
    public static final int PORT_SPACING = 10;
    public static final Dimension NODE_DIMENSION = new Dimension(NODE_WIDTH, NODE_HEIGHT);

    public static final String SELECT_PROPERTY = "NetworkView.select";
    
    private final int horizontalOffset = 1; //for drawing elephant house connections


    private static Map<String, BufferedImage> fileImageCache = new HashMap<String, BufferedImage>();
    private static BufferedImage nodeGeneric;

    public static final float MIN_ZOOM = 0.05f;
    public static final float MAX_ZOOM = 1.0f;

    public static final Map<String, Color> PORT_COLORS = Maps.newHashMap();
    public static final Color DEFAULT_PORT_COLOR = Color.WHITE;
    public static final Color PORT_HOVER_COLOR = Color.YELLOW;
    public static final Color TOOLTIP_BACKGROUND_COLOR = new Color(254, 255, 215);
    public static final Color TOOLTIP_STROKE_COLOR = Color.DARK_GRAY;
    public static final Color TOOLTIP_TEXT_COLOR = Color.DARK_GRAY;
    public static final Color DRAG_SELECTION_COLOR = new Color(255, 255, 255, 100);
    public static final BasicStroke DRAG_SELECTION_STROKE = new BasicStroke(1f);
    public static final BasicStroke CONNECTION_STROKE = new BasicStroke(2);
    public static BasicStroke DOTTED_STROKE; //dotted stroke will be updated based on the viewScale

    private final NodeBoxDocument document;

    private JPopupMenu nodeMenu = null;
    
    private JPopupMenu networkMenu;
    private Point networkMenuLocation;

    private Point nodeMenuLocation;
    

    private LoadingCache<Node, BufferedImage> nodeImageCache;

    private Set<String> selectedNodes = new HashSet<String>();

    // Interaction state
    private boolean isDraggingNodes = false;
    private boolean isShiftPressed = false;
    private boolean isAltPressed = false;
    private boolean isDragSelecting = false;
    private ImmutableMap<String, nodebox.graphics.Point> dragPositions = ImmutableMap.of();
    private NodePort overInput;
    private Node overOutput;
    
    private NodePort oldOverInput;
    private Node oldOverOutput;

    
    private Node connectionOutput;
    private NodePort connectionInput;
    private Point2D connectionPoint;
    private boolean startDragging;
    private Point2D dragStartPoint;
    private Point2D dragCurrentPoint;
    private Component goInSubnetworkMenuItem;

    //These array lists of nodes are used later to draw elephant-house-style connections
	ArrayList<Node> newNodes; 
	Set<String> newNodesNames;
	ArrayList<Node> sameChangedNodes;
	Set<String> sameChangedNodesNames;
	ArrayList<Node> sameUnchangedNodes;
	Set<String> sameUnchangedNodesNamesInThisAlternative;
	ArrayList<Node> draggedNodesThatDidntChange = new ArrayList<Node>();
	Set<String> draggedNodesNames;
	Set<String> draggableReferenceNodesNames;


	ArrayList<Node> sameNodesInReference;
	ArrayList<Node> missingNodes;
	Set<String> missingNodesNames;
	boolean isDesignGalleryNetwork = false; //this network belong to cartesian product design gallery network
		
	//Node dragging across alternative networks
	public final Border nodeDragBorder = BorderFactory.createMatteBorder(5, 5, 5, 5, Theme.DRAGGED_NODE_COLOR);
	
    
    static {
        try {
            nodeGeneric = ImageIO.read(NetworkView.class.getResourceAsStream("/node-generic.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        PORT_COLORS.put(Port.TYPE_INT, new Color(116, 119, 121));
        PORT_COLORS.put(Port.TYPE_FLOAT, new Color(116, 119, 121));
        PORT_COLORS.put(Port.TYPE_STRING, new Color(92, 90, 91));
        PORT_COLORS.put(Port.TYPE_BOOLEAN, new Color(92, 90, 91));
        PORT_COLORS.put(Port.TYPE_POINT, new Color(119, 154, 173));
        PORT_COLORS.put(Port.TYPE_COLOR, new Color(94, 85, 112));
        PORT_COLORS.put("geometry", new Color(20, 20, 20));
        PORT_COLORS.put("list", new Color(76, 137, 174));
        PORT_COLORS.put("data", new Color(52, 85, 129));
    }

    /**
     * Tries to find an image representation for the node.
     * The image should be located near the library, and have the same name as the library.
     * <p/>
     * If this node has no image, the prototype is searched to find its image. If no image could be found,
     * a generic image is returned.
     *
     * @param node           the node
     * @param nodeRepository the list of nodes to look for the icon
     * @return an Image object.
     */
    public static BufferedImage getImageForNode(Node node, NodeRepository nodeRepository) {
        for (NodeLibrary library : nodeRepository.getLibraries()) {
            BufferedImage img = findNodeImage(library, node);
            if (img != null) {
                return img;
            }
        }
        if (node.getPrototype() != null) {
            return getImageForNode(node.getPrototype(), nodeRepository);
        } else {
            return nodeGeneric;
        }
    }

    public static BufferedImage findNodeImage(NodeLibrary library, Node node) {
        if (node == null || node.getImage() == null || node.getImage().isEmpty()) return null;
        if (!library.getRoot().hasChild(node)) return null;

        File libraryDirectory = null;
        if (library.getFile() != null)
            libraryDirectory = library.getFile().getParentFile();
        else if (library.equals(NodeLibrary.coreLibrary))
            libraryDirectory = new File("libraries/core");

        if (libraryDirectory != null) {
            File nodeImageFile = new File(libraryDirectory, node.getImage());
            if (nodeImageFile.exists()) {
                return readNodeImage(nodeImageFile);
            }
        }
        return null;
    }

    public static BufferedImage readNodeImage(File nodeImageFile) {
        String imagePath = nodeImageFile.getAbsolutePath();
        if (fileImageCache.containsKey(imagePath)) {
            return fileImageCache.get(imagePath);
        } else {
            try {
                BufferedImage image = ImageIO.read(nodeImageFile);
                fileImageCache.put(imagePath, image);
                return image;
            } catch (IOException e) {
                return null;
            }
        }
    }

    public NetworkView(NodeBoxDocument document) {
        super(MIN_ZOOM, MAX_ZOOM);
        this.document = document;
        //if (document.getAlternativePaneHeader().isUnlocked())
        	setBackground(Theme.PASSIVE_COLOR);
        //else
        	//setBackground(Theme.NETWORK_BACKGROUND_COLOR_LOCKED);
        initEventHandlers();
        initMenus();
        nodeImageCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build(new NodeImageCacheLoader(document.getNodeRepository()));
        
        setDottedStroke(1.f); //set initial basicStroke equal to current viewscale
        
    }

    public static void setDottedStroke(double viewScale){
    	// dash = (3 / viewScale ^ 1.25)
    	// in the beginning the dash effect shouldn't as strong therefore we use a polynomial relationship instead of linear increase
    	float [] dash = { (3.f / (float) Math.pow(viewScale,1.25))};
    	DOTTED_STROKE = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0);
    	
    }
    private void initEventHandlers() {
        setFocusable(true);
        // This is disabled so we can detect the tab key.
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyHandler());
        MouseHandler mh = new MouseHandler();
        addMouseListener(mh);
        addMouseMotionListener(mh);
        addFocusListener(new FocusHandler());
    }

    public boolean atLeastOneNetworkMenuIsVisible(){
    	return networkMenu.isVisible() || (nodeMenu != null && nodeMenu.isVisible());
    }
    
    private JPopupMenu createNodeMenu(Node node) {
        JPopupMenu menu = new JPopupMenu();
        
        menu.add(new SetRenderedAction());
        menu.add(new SetReplaceAction());
        menu.add(new RenameAction());
        menu.add(new DeleteAction());
        menu.add(new DeleteAndReconnectAction());
        menu.add(new GroupIntoNetworkAction(null)); 
        menu.add(new Merge()); 
        

        if (node.isNetwork()) {
            menu.add(new GoInAction());
        }
        //this is from a newer version of nodebox
        /*if (!node.hasComment()) {
            menu.add(new AddCommentAction());
        } else {
            menu.add(new EditCommentAction());
            menu.add(new RemoveCommentAction());
        }*/

        menu.add(new HelpAction());
        return menu;
    }
    
    private void initMenus() {
        networkMenu = new JPopupMenu();
        networkMenu.add(new NewNodeAction());
        networkMenu.add(new ResetViewAction());
        networkMenu.add(new GoUpAction());
    }


	public NodeBoxDocument getDocument() {
        return document;
    }

    public Node getActiveNetwork() {
        return document.getActiveNetwork();
    }

    //// Events ////

    /**
     * Refresh the nodes and connections cache.
     */
    public void updateAll() {
        updateNodes();
        updateConnections();
    }

    public void updateNodes() {
        repaint();
    }

    public void updateConnections() {
        repaint();
    }

    public void updatePosition(Node node) {
        updateConnections();
    }

    public void checkErrorAndRepaint() {
        // TODO Check for errors in an efficient way.
    }

    public void codeChanged(Node node, boolean changed) {
        repaint();
    }

    //// Model queries ////

    private ImmutableList<Node> getNodes() {
        return getDocument().getActiveNetwork().getChildren();
    }

    private ImmutableList<Node> getNodesReversed() {
        return getNodes().reverse();
    }

    private Iterable<Connection> getConnections() {
        return getDocument().getActiveNetwork().getConnections();
    }

    public static boolean isPublished(Node network, Node childNode, Port childPort) {
        return network.hasPublishedInput(childNode.getName(), childPort.getName());
    }


    public void resetReferenceViewVisualizationData(){
    	draggedNodesNames = null;
    	document.getAppFrame().sameUnchangedNodesNames = new HashSet<String>();
    }

	private static void populateArrayWithNewOrMissingConnections(
			ArrayList<Connection> allConnections, ArrayList<Connection> connectionsWithExclusions, Iterable<Connection> iter1,
			Iterable<Connection> iter2, Set<String> exclusionNodeNameList) { //nodeList contain either new or missing nodes
		
		for (Connection connection1 : iter1){
			String input = connection1.getInputNode();
			String output = connection1.getOutputNode();
			boolean found = false;
			for (Connection connection2 : iter2){
				if (connection2.getInputNode().equals(input) &&
					connection2.getOutputNode().equals(output) //&&
								){
						found = true;
						break;
				}
			}
			if (!found){
				allConnections.add(connection1);
				if (connectionsWithExclusions != null && exclusionNodeNameList != null &&!exclusionNodeNameList.contains(input) && !exclusionNodeNameList.contains(output)){
					connectionsWithExclusions.add(connection1);
				}

			}
		}
	}
	
	private void addNodesToDraggedNodesThatDidntChangeForNewOrMissingConnections(
			ArrayList<Connection> connections) {
		for (Connection c : connections){
			String input = c.getInputNode();
			String output = c.getOutputNode();
			updateDraggedNodesArrays(input);
			updateDraggedNodesArrays(output);
			
		}
	}

	private void updateDraggedNodesArrays(String input) {
		if (document.getAppFrame().sameUnchangedNodesNames.contains(input)){
			Node node = document.getActiveNetwork().getChild(input);
			//Prevent adding if sameNodesThatChanged contain it already
			//because we want to paint either one or the other.
			//Also don't draw it if it's already there.
			if (!sameChangedNodes.contains(node) && !draggedNodesThatDidntChange.contains(node)){ 
				draggedNodesThatDidntChange.add(node);
		    	//now update draggedNodesNames to include new draggedNodesThatDidntChange
				//this is so that the reference node highlighting can be updated
				if (draggedNodesNames == null)
					draggedNodesNames = new HashSet<String>();
				draggedNodesNames.add(node.getName());
			}
		}
	}

	/**
	 * subnet1 is in the compared, subnet2 is the reference
	 */
	public static boolean subnetsEqual(Node subnet1, Node subnet2){		
		Set<String> newNodesInS1 = new HashSet<String>();
		Set<String> changedNodesInS1 = new HashSet<String>();
		Set<String> missingNodesInS1 = new HashSet<String>();
		boolean subSubnetsEqual = true; //initially we assume that subsubnets are equal
		
		//look for new and changed nodes
		for (Node s1Node : subnet1.getChildren()){ //for all nodes in this subnet1
			boolean foundSameNode = false;
			for (Node s2Node : subnet2.getChildren()){ //for all nodes in subnet2
				if (s1Node.getUUID().equals(s2Node.getUUID())){
		        	
					boolean foundSameNodeThatChanged = false;
					List<Port> ports = s1Node.getInputs();
		        	//get ports from the active node in master and in this document
		            List<Port> referenceNodePorts = s2Node.getInputs();
		            
		            for (int i = 0; i < referenceNodePorts.size(); i++)
		            {
		            	Port referenceNodePort = referenceNodePorts.get(i);
		            	Port port = ports.get(i);
		            	
		            	if (!port.equals(referenceNodePort))
		            	{
		            		foundSameNodeThatChanged = true;
		            		break;
		            	}
		            	
		            }
		            if (foundSameNodeThatChanged){
		            	changedNodesInS1.add(s1Node.getName());
		            }
		            else if(s1Node.getPrototype().getName().equals("network")){ //if this is a group node
		            	//recursive step
		            	subSubnetsEqual = subSubnetsEqual && NetworkView.subnetsEqual(s1Node,s2Node);
	            	}
		            
					foundSameNode = true;
					break; //node was matched
				}
			}
			if (!foundSameNode){
				newNodesInS1.add(s1Node.getName());
			}
		}
		
		//look for missing nodes
		for (Node s2Node : subnet2.getChildren()){ //for all nodes in reference document
			boolean found = false;
			for (Node s1Node : subnet1.getChildren()){ //for all nodes in this network view
				if (s1Node.getUUID().equals(s2Node.getUUID())){
					found = true;
					break;
				}
			}
			if (!found){
				missingNodesInS1.add(s2Node.getName());
			}
		}
		
		System.out.println("new in s1:" + newNodesInS1);
		System.out.println("changed in s1:" + changedNodesInS1);
		System.out.println("missing in s1:" + missingNodesInS1);
		
		//now compare connections
		Iterable<Connection> cons1 = subnet1.getConnections();
    	Iterable<Connection> cons2 = subnet2.getConnections();

    	ArrayList<Connection> newConnections = new ArrayList<Connection>();
    	populateArrayWithNewOrMissingConnections(newConnections, null, cons1, cons2, newNodesInS1); //and exclude connections to/from new nodes
    	
    	ArrayList<Connection> missingConnections = new ArrayList<Connection>();
    	populateArrayWithNewOrMissingConnections(missingConnections, null, cons2, cons1, missingNodesInS1); //and exclude connections to/from new nodes

		System.out.println("new connections in s1:" + newConnections);		
		System.out.println("missing connections in s1:" + missingConnections);
		
		return subSubnetsEqual && newNodesInS1.isEmpty() && changedNodesInS1.isEmpty() && missingNodesInS1.isEmpty()
				&& newConnections.isEmpty() && missingConnections.isEmpty();
			
	}
    //// Painting the nodes ////

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        ApplicationFrame appFrame = document.getAppFrame(); 
        
        // Draw background
    	if (document == appFrame.getCurrentDocument() && document.getAlternativePaneHeader().isEditable() || isDesignGalleryNetwork || document.isHistoryPreviewDocument)
            g2.setColor(Theme.ACTIVE_COLOR);
    	else 
            g2.setColor(Theme.PASSIVE_COLOR);
    	//else
    		//g2.setColor(Theme.NETWORK_BACKGROUND_COLOR_LOCKED);

        g2.fill(g.getClipBounds());
         

        // Paint the grid
        // (The grid is not really affected by the view transform)
        paintGrid(g2);

        // Set the view transform
        AffineTransform originalTransform = g2.getTransform();
        g2.transform(getViewTransform());
 
        NodeBoxDocument referenceDocument = appFrame.getReferenceDocument();
        
        if (referenceDocument == null){  //this is the reference view, paint normally, no glasspane is involved
        	paintNodes(g2);
        	paintPortTooltip(g2);
        	paintConnections(g2);
       	}
        //disabled this feature for now
        else if (referenceDocument == document){
        	if (Application.ENABLE_SUBTRACTIVE_ENCODING){
	        	//determine draggable nodes to highlight        	
	        	populateDraggableNodes(appFrame, referenceDocument);
	        	//System.out.println("draggableReferenceNodesNames:" + draggableReferenceNodesNames);
	        	paintNodesDraggable(g2);
        	}
        	else //regular diff (DARLS style, non subtractive diff)
        	{
        		paintNodes(g2);
        	}
        	
        	paintPortTooltip(g2);
        	paintConnections(g2); //maybe do the same for connection too, later?
    		
        	//set missingConnectionsInReferenceView for Reference View
        	//referenceView will be compared against currently selected view
        	
        	NetworkView currentView = appFrame.getCurrentDocument().getNetworkView();
        	if (currentView != this){
        		//compare against currently selected view and draw missing connections
        		Iterable<Connection> iter1 = getConnections();
            	Iterable<Connection> iter2 = currentView.getConnections();
            	Set<String> dummy1 = new HashSet<String>(); //need this array to call the function that is normally used in compared view
            	ArrayList<Connection> missingConnections = new ArrayList<Connection>();
            	ArrayList<Connection> dummy2 = new ArrayList<Connection>(); //here, this is a dummy too
            	populateArrayWithNewOrMissingConnections(missingConnections, dummy2, iter2, iter1, dummy1); //and exclude connections to/from new nodes
            	//visualize missing connections in referenceView
            	g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.TRANSPARENCY_LEVEL)); //set transparency
            	//GOTTA INCLUDE THESE ON CLICK ON VIZ!!!
            	try{
            		paintConnectionsSelectively(g2, missingConnections,Theme.CONNECTION_DEFAULT_COLOR, CONNECTION_STROKE);
            	}
            	catch (java.lang.NullPointerException e){
            		System.err.println("oopsie!");
            	}
            	g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f)); //reset transparency
        	}
        }
        else //this is one of the compared views against the reference
        {
         	//-----------------------------------------------------------------------------------------------------------------------
        	//Build all the diff lists
        	//-----------------------------------------------------------------------------------------------------------------------
        	populateAllPresentNodeArrayLists(referenceDocument);
        	populateMissingNodesArrayList(referenceDocument);

        	//-----------------------------------------------------------------------------------------------------------------------
        	//Draw missing, new and changed nodes
        	//-----------------------------------------------------------------------------------------------------------------------
            //Set AlphaComposite to draw missing nodes with transparency
        	if (!Application.ENABLE_SUBTRACTIVE_ENCODING) //if DARLS style diff viz
        		paintNodesSelectively(g2, sameUnchangedNodes, null, false, false);

        	paintNodesSelectively(g2, missingNodes, null, false, true); //missing nodes with reduced transparency
        	paintNodesSelectively(g2, sameChangedNodes, Theme.CHANGED_NODE_COLOR, false, false); //new changed nodes with red color
        	paintNodesSelectively(g2, newNodes, Theme.NEW_NODE_COLOR, false, false); //new nodes with green color
        	
             //-----------------------------------------------------------------------------------------------------------------------
            //Draw black connections to NewNodes
        	//-----------------------------------------------------------------------------------------------------------------------
        	/*ArrayList<Connection> blackConnectionsToNewNodes = new ArrayList<Connection>();
        	getConnectionsToNewNodesOnComparedView(blackConnectionsToNewNodes); //these are drawn in black in the compared view and connect to the new node
        	//show from new nodes to new nodes
        	paintConnectionsSelectively(g2, blackConnectionsToNewNodes,Theme.NEW_NODE_COLOR, CONNECTION_STROKE);
        	*/
        	
        	//-----------------------------------------------------------------------------------------------------------------------
        	//Draw black connections in compared views between those same nodes that don't have connections in the reference view
        	//-----------------------------------------------------------------------------------------------------------------------
        	NetworkView referenceView = referenceDocument.getNetworkView();
        	//-----------------------------------------------------------------------------------------------------------------------
        	//draw new connections that are not in reference view but are in the compared view
        	
        	Iterable<Connection> iter1 = getConnections();
        	Iterable<Connection> iter2 = referenceView.getConnections();

        	ArrayList<Connection> newConnections = new ArrayList<Connection>();
        	ArrayList<Connection> newConnectionsExcludingNewNodes = new ArrayList<Connection>();
        	populateArrayWithNewOrMissingConnections(newConnections,newConnectionsExcludingNewNodes, iter1, iter2, newNodesNames); //and exclude connections to/from new nodes
        	
        	ArrayList<Connection> missingConnections = new ArrayList<Connection>();
        	ArrayList<Connection> missingConnectionsExcludingMissingNodes = new ArrayList<Connection>();
        	populateArrayWithNewOrMissingConnections(missingConnections, missingConnectionsExcludingMissingNodes, iter2, iter1, missingNodesNames); //and exclude connections to/from new nodes

        	//-----------------------------------------------------------------------------------------------------------------------
        	//Populate Dragged Nodes:
        	//-----------------------------------------------------------------------------------------------------------------------
        	//generate dragged nodes from sameNodesThatDidnt change
        	populateDraggedNodesThatDidntChange();
        	addNodesToDraggedNodesThatDidntChangeForNewOrMissingConnections(newConnections);
        	addNodesToDraggedNodesThatDidntChangeForNewOrMissingConnections(missingConnectionsExcludingMissingNodes);
            
            //-----------------------------------------------------------------------------------------------------------------------
            //back to drawing newConnections
        	//draw new connections in compared view which are missing connections in reference view
            if (Application.ENABLE_SUBTRACTIVE_ENCODING){
            	//paint dragged nodes that didn't change
                paintNodesSelectively(g2, draggedNodesThatDidntChange, Theme.DRAGGED_NODE_COLOR, false, false);
            	
                paintConnectionsSelectively(g2, newConnections,Theme.NEW_NODE_COLOR, CONNECTION_STROKE);
            	ArrayList<Connection> connectorsBetweenSameNodes = new ArrayList<Connection>();	//between both Red and Brown nodes!
            	getConnectionsToSameNodesInComparedView(connectorsBetweenSameNodes);
            	paintConnectionsSelectively(g2, connectorsBetweenSameNodes,Theme.CONNECTION_DEFAULT_COLOR, CONNECTION_STROKE); //draw this as solid black conenectors

            }
            else{
            	//paint new connections
            	paintConnectionsSelectively(g2, newConnections,Theme.NEW_NODE_COLOR, CONNECTION_STROKE);
            	//also paint the same connections (to same changed and unchanged nodes
            	ArrayList<Connection> sameConnections = new ArrayList<Connection>();
            	//populate it
            	for (Connection c : getConnections()){
            		if (sameUnchangedNodesNamesInThisAlternative.contains(c.getInputNode()) && 
            				sameUnchangedNodesNamesInThisAlternative.contains(c.getOutputNode()))
            				sameConnections.add(c);
            		
            		//all add here connections to/from changed nodes
            		if (sameChangedNodesNames.contains(c.getInputNode()) && sameUnchangedNodesNamesInThisAlternative.contains(c.getOutputNode()) ||
            				sameChangedNodesNames.contains(c.getOutputNode()) && sameUnchangedNodesNamesInThisAlternative.contains(c.getInputNode()))
            				sameConnections.add(c);
            	}
            	paintConnectionsSelectively(g2, sameConnections,Theme.CONNECTION_DEFAULT_COLOR, CONNECTION_STROKE);

            	
            	//paintConnections(g2); //darls style draw all connections. this draws all the internal black connections
               
            
         	//also paint deleted connections
            //we do this for both: subtractive and darls vis styles
        	ArrayList<Connection> deletedConnections = new ArrayList<Connection>();
        	populateArrayWithNewOrMissingConnections(deletedConnections, null, iter2, iter1, null); //and exclude connections to/from new nodes
        	//draw deleted connections
	        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.TRANSPARENCY_LEVEL)); //set transparency
	        
	        //this is a hack. I copied all the code from paintConnection to draw connections that dont exist in compared view
	        //it refers to nodes in the reference view to draw connections in the 
	        //it's harder to do otherwise
	  		g.setColor(Theme.CONNECTION_DEFAULT_COLOR);
	        ((Graphics2D) g).setStroke(CONNECTION_STROKE);
	        for (Connection connection : deletedConnections) {
	        	Node outputNode = referenceDocument.getNetworkView().findNodeWithName(connection.getOutputNode());
		        Node inputNode = referenceDocument.getNetworkView().findNodeWithName(connection.getInputNode());
		        Port inputPort = inputNode.getInput(connection.getInputPort());
		        //g.setColor(portTypeColor(outputNode.getOutputType())); //as recommended by Wolfgang, everything is in black color
		        g.setColor(Color.black);
		        Rectangle outputRect = nodeRect(outputNode);
		        Rectangle inputRect = nodeRect(inputNode);
		        paintConnectionLine((Graphics2D) g, outputRect.x + 4, outputRect.y + outputRect.height + 1, inputRect.x + portOffset(inputNode, inputPort) + 4, inputRect.y - 4);
	        }

        	g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f)); //reset transparency
            }
	        
	        //-----------------------------------------------------------------------------------------------------------------------
	        //draw missing connections
	        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.TRANSPARENCY_LEVEL)); //set transparency
	        paintConnectionsSelectively(g2,missingConnectionsExcludingMissingNodes,Theme.CONNECTION_DEFAULT_COLOR, CONNECTION_STROKE);
        	g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f)); //reset transparency

        	
        	
        	//-----------------------------------------------------------------------------------------------------------------------
        	//Draw tooltip
            //-----------------------------------------------------------------------------------------------------------------------	        
	        //We don't want to paint the tooltip if the node is not painted in the reference view (i.e. if it's one of the sameNodesThatDidntChange)
        	//but also need to subtract from sameNodesThatDidntChange draggedNodesThatDidntChange
        	//so lets create a new array that has these features:
        	ArrayList<Node> hiddenNodes = new ArrayList<Node>();
        	for (Node node : sameUnchangedNodes)
        		if (!draggedNodesThatDidntChange.contains(node))
        			hiddenNodes.add(node);

        	paintPortTooltipSelectively(g2, hiddenNodes);        	
        }
        paintCurrentConnection(g2);
        paintDragSelection(g2);
        
        // Restore original transform
        g2.setTransform(originalTransform);
        
        //document.getCurrentFrame().repaintGlassPane();
    }

	private void populateDraggedNodesThatDidntChange() {
		draggedNodesThatDidntChange = new ArrayList<Node>();
		if (draggedNodesNames != null){
			for (Node node: sameUnchangedNodes) {
				if (draggedNodesNames.contains(node.getName()))
					draggedNodesThatDidntChange.add(node);
			}
		}
	}

	private void populateDraggableNodes(ApplicationFrame appFrame,
			NodeBoxDocument referenceDocument) {
		if (referenceDocument != getDocument()) return;
		draggableReferenceNodesNames = new HashSet<String>();
		draggableReferenceNodesNames.addAll(document.getAppFrame().sameUnchangedNodesNames);
		Set<String> intersection = null;
		//go through all the network and make intersection of all sets of draggedNodeNames
		//then calculate the difference in the end
		//this way, until all draggedNodesNames have been activated in all network views for all sets
		//the corresponding reference node will not get unhighlighted
		for (NodeBoxDocument doc : referenceDocument.getDocumentGroup()){
			if (referenceDocument != doc){
				//System.out.println(doc.getDocumentName() + ", draggedNodesNames:" + doc.getNetworkView().draggedNodesNames);

				Set<String> s = new HashSet<String>();
				if (doc.getNetworkView().draggedNodesNames != null){
					s.addAll(doc.getNetworkView().draggedNodesNames);
					//System.out.println(doc.getDocumentName() + ", adding dragged:" + doc.getNetworkView().sameNodesThatChangedNames);

				}
					//and all modified nodes too
				if (doc.getNetworkView().sameChangedNodesNames != null){
					s.addAll(doc.getNetworkView().sameChangedNodesNames);
					//System.out.println(doc.getDocumentName() + ", adding changed:" + doc.getNetworkView().sameNodesThatChangedNames);
				}
				
				if (intersection == null)
					intersection = s;
				else
					intersection.retainAll(s);
				
			}
		}
		if (intersection != null){
			draggableReferenceNodesNames.removeAll(intersection);
		}
	}

	private void populateMissingNodesArrayList(NodeBoxDocument referenceDocument) {
		//find missing nodes:
		missingNodes = new ArrayList<Node>();
		missingNodesNames = new HashSet<String>();

		for (Node referenceNode : referenceDocument.getNetworkView().getNodes()){ //for all nodes in reference document
			boolean found = false;
			for (Node node : getNodes()){ //for all nodes in this network view
				if (node.getUUID().equals(referenceNode.getUUID())){
					found = true;
					break;
				}
			}
			if (!found){
				missingNodes.add(referenceNode);
				missingNodesNames.add(referenceNode.getName());
			}
		}
	}

	private void populateAllPresentNodeArrayLists(
			NodeBoxDocument referenceDocument) {
		newNodes = new ArrayList<Node>();
		newNodesNames = new HashSet<String>();
		ArrayList<Node> sameNodes = new ArrayList<Node>(); //used later to avoid drawing connections of nodes connecting to newNoodes
		sameChangedNodes = new ArrayList<Node>();
		sameChangedNodesNames = new HashSet<String>();
		sameNodesInReference = new ArrayList<Node>(); //used later for elephant house effect
		sameUnchangedNodes = new ArrayList<Node>();
		sameUnchangedNodesNamesInThisAlternative = new HashSet<String>();
		
		
		for (Node node : getNodes()){ //for all nodes in this network view
			boolean foundSameNode = false;
			for (Node referenceNode : referenceDocument.getNetworkView().getNodes()){ //for all nodes in reference document
				if (node.getUUID().equals(referenceNode.getUUID())){
					//System.out.println("found " + node.getUUID().toString() + "," + node.getUUID().toString());
					sameNodes.add(node);
					sameNodesInReference.add(referenceNode);
		        	
					boolean foundSameNodeThatChanged = false;
					List<Port> ports = node.getInputs();
		        	//get ports from the active node in master and in this document
		            List<Port> referenceNodePorts = referenceNode.getInputs();
		            
		            for (int i = 0; i < referenceNodePorts.size(); i++)
		            {
		            	Port referenceNodePort = referenceNodePorts.get(i);
		            	Port port = ports.get(i);
		            	
		            	if (!port.equals(referenceNodePort))
		            	{
		            		foundSameNodeThatChanged = true;
		            		break;
		            	}
		            	
		            }
		            if (foundSameNodeThatChanged){
		            	sameChangedNodes.add(node);
		            	sameChangedNodesNames.add(node.getName());
		            }
		            else{
		            	if(node.getPrototype().getName().equals("network")){ //if this is a group node
		            		System.out.println("group node:"+node);
		            		//compare it with the network (group) node in the reference view recursively
		            		//and find out if there are any differences in subnet structure or parameters of nodes
		            		boolean equal = NetworkView.subnetsEqual(node,referenceNode);
		            		System.out.println("EQUAL:" + equal);
		            		//add this group nodes to same nodes that changed as well!
		            		if (!equal){
		            			sameChangedNodes.add(node);
		            			sameChangedNodesNames.add(node.getName());
		            		}
		            	}
		            	sameUnchangedNodes.add(node);
		            	sameUnchangedNodesNamesInThisAlternative.add(node.getName());
		            	document.getAppFrame().sameUnchangedNodesNames.add(node.getName());
		            }
		            
					foundSameNode = true;
					break; //node was matched
				}
			}
			if (!foundSameNode){
				newNodes.add(node);
				newNodesNames.add(node.getName());
			}
		}
	}

	private void getConnectionsToNewNodesOnComparedView(
			ArrayList<Connection> connections) {
		for (Connection connection : getConnections()){
			String input = connection.getInputNode();
			String output = connection.getOutputNode();
			
			for (Node newNode : newNodes){

				//for input of new to output of new
				getConnectionsToNewNodesFrom(connections, connection, input,
						output, newNode, newNodes);
				//checking for output of new to input of new
				getConnectionsToNewNodesFrom(connections, connection, output,
						input, newNode, newNodes);
				
				//for input of new to output of sameThatChanged
				getConnectionsToNewNodesFrom(connections, connection, input,
						output, newNode, sameChangedNodes);
				//for output of new to input of dragged node
				getConnectionsToNewNodesFrom(connections, connection, output,
						input, newNode, sameChangedNodes);
				

				//for input of new to output of draggedNodesThatDidntChange
				getConnectionsToNewNodesFrom(connections, connection, input,
						output, newNode, draggedNodesThatDidntChange);
				
				//for output of new to input of draggedNodesThatDidntChange
				getConnectionsToNewNodesFrom(connections, connection, output,
						input, newNode, draggedNodesThatDidntChange);
			}
		}
	}
	
	private void getConnectionsToSameNodesInComparedView(
			ArrayList<Connection> connections) {
		for (Connection connection : getConnections()){
			String input = connection.getInputNode();
			String output = connection.getOutputNode();
			
			for (Node changedNode : sameChangedNodes){

				//for input of changedNode to output of sameThatChanged
				getConnectionsToNewNodesFrom(connections, connection, input,
						output, changedNode, sameChangedNodes);
				//for output of changedNode to input of dragged node
				getConnectionsToNewNodesFrom(connections, connection, output,
						input, changedNode, sameChangedNodes);
				
				//for input of changedNode to output of draggedNodesThatDidntChange
				getConnectionsToNewNodesFrom(connections, connection, input,
						output, changedNode, draggedNodesThatDidntChange);
				
				//for output of changedNode to input of draggedNodesThatDidntChange
				getConnectionsToNewNodesFrom(connections, connection, output,
						input, changedNode, draggedNodesThatDidntChange);

			}
			//also for connecting unchanged nodes to each other
			for (Node unchangedNode : draggedNodesThatDidntChange){
				
				//for input of changedNode to output of draggedNodesThatDidntChange
				getConnectionsToNewNodesFrom(connections, connection, input,
						output, unchangedNode, draggedNodesThatDidntChange);
				
				//for output of changedNode to input of draggedNodesThatDidntChange
				getConnectionsToNewNodesFrom(connections, connection, output,
						input, unchangedNode, draggedNodesThatDidntChange);

			}
		}
	}


	private static void getConnectionsToNewNodesFrom(
			ArrayList<Connection> connections, Connection connection,
			String input, String output, Node newNode, ArrayList<Node> alist) {
		if (input.equals(newNode.getName())){
			for (Node newNode2 : alist){
				if (output.equals(newNode2.getName())){
					connections.add(connection);
				}
			}
		}
	}
    //called from FinalGlassPane with glassPane graphics object
    //make sure paintcomponent in networkView is called before this one because otherwise node arrays wouldn't be initialized properly
    public void paintElephantConnections(Graphics2D g){
    	if (!document.getAlternativePaneHeader().diffToggleSelected()) 
    		return; //if linksToggle disabled, then don't include this view in comparison
    	
    	if (sameNodesInReference == null || newNodes == null || sameChangedNodes == null || missingNodes == null) return;
        Color oldColor = g.getColor();
       
        g.setStroke(DOTTED_STROKE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        //this approach works because the we compared against node names and not uuids, if we were to compare against uuids, this would have to be done differently
        String strSelectedNode;
        ApplicationFrame af = document.getAppFrame();
        Node singleNodeSelectedSomewhere = document.getAppFrame().singleNodeSelectedSomwhere;
        //this is for all nodes clicked on non-reference view network
        //the reference view network case is taken care later
        if (af.getCurrentDocument() != af.getReferenceDocument()){
			if (selectedNodes.size() == 1 && singleNodeSelectedSomewhere == null){
				
				//if first time entering here
					strSelectedNode = selectedNodes.iterator().next();
					//System.out.println("selectedNodes:" + strSelectedNode);
		
					/*System.out.println(document.getDocumentName() + "-----before------------------");
					System.out.println("new:" + newNodes);
					System.out.println("same:" + sameNodesThatChanged);
					System.out.println("missing:" + missingNodes);*/
		
					
					Node n = getSelectedNodeFromArray(strSelectedNode, newNodes);
					newNodes = new ArrayList<Node>();
					if (n != null){
						newNodes.add(n);
						document.getAppFrame().singleNodeSelectedSomwhere = n;
					}
		
					n = getSelectedNodeFromArray(strSelectedNode, sameChangedNodes);
					sameChangedNodes = new ArrayList<Node>();
					if (n != null){
						sameChangedNodes.add(n);
						document.getAppFrame().singleNodeSelectedSomwhere = n;
					}
		
					n = getSelectedNodeFromArray(strSelectedNode, missingNodes);
					missingNodes = new ArrayList<Node>();
					if (n != null){
						missingNodes.add(n);
						document.getAppFrame().singleNodeSelectedSomwhere = n;
					}
					
					n = getSelectedNodeFromArray(strSelectedNode, draggedNodesThatDidntChange);
					missingNodes = new ArrayList<Node>();
					if (n != null){
						draggedNodesThatDidntChange.add(n);
						document.getAppFrame().singleNodeSelectedSomwhere = n;
					}
					
					/*System.out.println(document.getDocumentName() + "-----after------------------");
					System.out.println("new:" + newNodes);
					System.out.println("same:" + sameNodesThatChanged);
					System.out.println("missing:" + missingNodes);*/
				}
				else if (singleNodeSelectedSomewhere != null && af.getCurrentDocument() != af.getReferenceDocument()){
					Node n = getSelectedNodeFromArray(singleNodeSelectedSomewhere.getName(), newNodes);
					newNodes = new ArrayList<Node>();
					if (n != null)
						newNodes.add(n);
		
					n = getSelectedNodeFromArray(singleNodeSelectedSomewhere.getName(), sameChangedNodes);
					sameChangedNodes = new ArrayList<Node>();
					if (n != null)
						sameChangedNodes.add(n);
		
					n = getSelectedNodeFromArray(singleNodeSelectedSomewhere.getName(), missingNodes);
					missingNodes = new ArrayList<Node>();
					if (n != null)
						missingNodes.add(n);
					
					n = getSelectedNodeFromArray(singleNodeSelectedSomewhere.getName(), draggedNodesThatDidntChange);
					draggedNodesThatDidntChange = new ArrayList<Node>();
					if (n != null)
						draggedNodesThatDidntChange.add(n);
				}
        }
        /*
         * this case is taken care of now in checkInOutConnectsToSelectedNodeInReference
		else if (af.getCurrentDocument() == af.getReferenceDocument() &&  selectedNodesInRef.size() == 1){
			System.out.println("reference doc!");
		}*/
			
        
		for (Node referenceNode : sameNodesInReference){
			//if the input node of reference node exists in this alternative, but node itself doesn't then draw a connector
			String strRefNode = referenceNode.getName();
			if (!sameChangedNodesNames.contains(strRefNode) && draggedNodesNames == null || 											//if no dragged nodes, then just checked for existance in same changed nodes
				!sameChangedNodesNames.contains(strRefNode) && draggedNodesNames != null && !draggedNodesNames.contains(strRefNode)){
				
				for (Connection connection: getConnections()){
					String strInputNode = connection.getInputNode();
					String strOutputNode = connection.getOutputNode();
					if (strInputNode.equals(strRefNode)){
	    				g.setColor(Theme.CHANGED_NODE_COLOR);
	    				_paintElephantConnectionToReferenceNodeInput(g, connection,
								strInputNode, strOutputNode, sameChangedNodes);   
						//System.out.println("InNode eq RefNode:" + strRefNode);
	    				
	    				g.setColor(Theme.DRAGGED_NODE_COLOR);
	    				_paintElephantConnectionToReferenceNodeInput(g, connection,
								strInputNode, strOutputNode, draggedNodesThatDidntChange);  
					}
					
					else if (strOutputNode.equals(strRefNode)){
						//System.out.println("OutNode eq RefNode:" + strRefNode);
	    				g.setColor(Theme.CHANGED_NODE_COLOR);
	    				_paintElephantConnectionToReferenceNodeOutput(g, connection, strInputNode,
								strOutputNode, sameChangedNodes);
	    				
	    				g.setColor(Theme.DRAGGED_NODE_COLOR);
	    				_paintElephantConnectionToReferenceNodeOutput(g, connection, strInputNode,
								strOutputNode, draggedNodesThatDidntChange);
					}
					g.setColor(oldColor);

				}
			}
		}

		
        /*for (Connection connection: getConnections()){
			String strInputNode = connection.getInputNode();
			String strOutputNode = connection.getOutputNode();
			//String inoutPort = connection.getInputPort();

			for (Node referenceNode : sameNodesInReference){
				//input in newNodes
				if (strOutputNode.equals(referenceNode.getName())){ //output is in reference
    				g.setColor(Theme.NEW_NODE_COLOR);
    				_paintElephantConnectionToReferenceNodeOutput(g, connection, strInputNode,
							strOutputNode, newNodes);
				}
				//input in sameNodesThatChanged
    			if (strOutputNode.equals(referenceNode.getName())){ //output is in reference
    				g.setColor(Theme.CHANGED_NODE_COLOR);
    				_paintElephantConnectionToReferenceNodeOutput(g, connection, strInputNode,
							strOutputNode, sameChangedNodes);
				}
				//input in sameNodesThatChanged
    			if (strOutputNode.equals(referenceNode.getName())){ //output is in reference
    				g.setColor(Theme.DRAGGED_NODE_COLOR);
    				_paintElephantConnectionToReferenceNodeOutput(g, connection, strInputNode,
							strOutputNode, draggedNodesThatDidntChange);
				}
     			
    			//output in newNode
    			if (strInputNode.equals(referenceNode.getName())){ //input is in reference
    				g.setColor(Theme.NEW_NODE_COLOR);
    				_paintElephantConnectionToReferenceNodeInput(g, connection,
							strInputNode, strOutputNode, newNodes);    			
				}
    			
    			//output in sameNodesThatChanged
    			if (strInputNode.equals(referenceNode.getName())){ //input is in reference
    				g.setColor(Theme.CHANGED_NODE_COLOR);
    				_paintElephantConnectionToReferenceNodeInput(g, connection,
							strInputNode, strOutputNode, sameChangedNodes);    			
				}

    			//output in draggedNodesThatDidntChange
    			if (strInputNode.equals(referenceNode.getName())){ //input is in reference
    				g.setColor(Theme.DRAGGED_NODE_COLOR);
    				_paintElephantConnectionToReferenceNodeInput(g, connection,
							strInputNode, strOutputNode, draggedNodesThatDidntChange);    			
				}
    			
			}

			//just in case set the color back to what it was
			g.setColor(oldColor);
    	}*/
        
        //now for missing nodes
        /*ApplicationFrame appFrame = document.getAppFrame();
     	NodeBoxDocument referenceDocument = appFrame.getReferenceDocument();
        for (Connection connection: referenceDocument.getNetworkView().getConnections()){
			String strInputNode = connection.getInputNode();
			String strOutputNode = connection.getOutputNode();
			//String inoutPort = connection.getInputPort();
			
			//for (Node referenceNode : sameNodesInReference){
			for (Node referenceNode : referenceDocument.getNetworkView().getNodes()){
				//input in missingNodes
				if (strOutputNode.equals(referenceNode.getName())){ //output is in reference
					_paintElephantConnectionToReferenceNodeOutput(g, connection, strInputNode,
							strOutputNode, missingNodes);
				}
				
				//output in missingNodes
				if (strInputNode.equals(referenceNode.getName())){ //input is in reference
					_paintElephantConnectionToReferenceNodeInput(g, connection,
							strInputNode, strOutputNode, missingNodes);
					//System.out.println("missing connection from:" + strInputNode + " to " + strOutputNode);
					//System.out.println("missing nodes:" + missingNodes);

				}
			}
		} */

    }

	public  Set<String> getSelectedNodeNamesAsSet() {
		return selectedNodes;
	}

	private static Node getSelectedNodeFromArray(String strSelectedNode, ArrayList<Node> array) {
		for (Node n : array){
			if(n.getName().equals(strSelectedNode))
				return n;
		}
		return null;
	}



	private void _panZoomPainConnection(Graphics2D g, Rectangle outputRect,
			Node inputNode, Rectangle inputRect, Port inputPort,
			int outputDisplacementX, int outputDisplacementY,
			int inputDisplacementX, int inputDisplacementY) {
		int x0 = /*outputDisplacementX +*/ outputRect.x + 4;
		int y0 = /*outputDisplacementY +*/ outputRect.y + outputRect.height + 4;
		int x1 = /*inputDisplacementX +*/ inputRect.x + portOffset(inputNode, inputPort) + 8;
		int y1 = /*inputDisplacementY +*/ inputRect.y - 4;
		
		AffineTransform at = new AffineTransform();
		at.translate(getViewX(), getViewY());
		at.scale(getViewScale(), getViewScale());
		
		//create inverse
		AffineTransform iat = new AffineTransform();
		try {
			iat = at.createInverse();
		} catch (NoninvertibleTransformException e1) {
			e1.printStackTrace();
		}
		g.transform(at);
		
		//this could potentially cause division by 0
		int vx0=0,vy0=0,vx1=0,vy1=0;
		try
		{
		    vx0 = x0 + (int)(outputDisplacementX/getViewScale());
		    vy0 = y0 + (int)(outputDisplacementY/getViewScale());
			vx1 = x1 + (int)(inputDisplacementX/getViewScale());
			vy1 = y1 + (int)(inputDisplacementY/getViewScale());
		}
		catch (Exception e)
		{
			System.err.println("divison by zero");
		}
		
		paintConnectionLine(g, vx0, vy0, vx1, vy1);
		
		
		g.transform(iat); //inverse transformation in case there will be more transformations, alternatively call transformation once in the caller function
	}
	
	private void _paintElephantConnectionToReferenceNodeInput(Graphics2D g,
			Connection connection, String strInputNode, String strOutputNode, ArrayList<Node> ArrayNodeList) {
		
		//for reference view, if a single node was selected there and the connector doesnt connect to it, then don't draw it
		if (!checkInOutConnectsToSelectedNodeInReference(strInputNode))
			return;
		
		for (Node node : ArrayNodeList){
			if(strOutputNode.equals(node.getName())){ //input is in newNodes
				
				Node n = findNodeWithName(strOutputNode);
		        Node inputNode = findNodeWithName(strInputNode);
		        Rectangle outputRect;

				if (ArrayNodeList == missingNodes){
			    	//get reference view
					ApplicationFrame appFrame = document.getAppFrame();
			    	NodeBoxDocument referenceDocument = appFrame.getReferenceDocument();
			    	NetworkView referenceView = referenceDocument.getNetworkView();
			    	
					n = referenceView.findNodeWithName(strOutputNode);
					outputRect = nodeRect(n);
			        inputNode = referenceView.findNodeWithName(strInputNode);

				}
				else
					outputRect = nodeRect(n);


		        //Rectangle outputRect = nodeRect(findNodeWithName(strOutputNode));
		        //Node inputNode = findNodeWithName(strInputNode);
		        Rectangle inputRect = nodeRect(inputNode);
		        Port inputPort = inputNode.getInput(connection.getInputPort());
		        //the position of input node is the position relative to networkPane + hardcoded value position
		       
		        //monitor width + alternative width
		    	ApplicationFrame appFrame = document.getAppFrame();
		        int height = appFrame.alternativesPanel[0].getHeight() - 50;
		        
		        int outputRowNo = appFrame.getRowForDocument(document);
		        int outputColNo = appFrame.getColForDocument(document);
		        
		        int docAltNo = appFrame.getAlternativeNumberRelativeToMonitor(document);
		        int docNumAlt = appFrame.getNumAltForDocumentInMonitor(document);
		        
		        
		        NetworkPane networkPane = document.getNetworkPane();
		        int outputDisplacementX = networkPane.getX() + outputColNo*docNumAlt*networkPane.getWidth() + docAltNo*networkPane.getWidth() + horizontalOffset*(docAltNo) + horizontalOffset*outputColNo*docNumAlt;
		        int outputDisplacementY = outputRowNo*height + networkPane.getY() + 50;
		        
		        //if on Windows or Linux add 25 pixel to compenstate for the menubar
		        if (!Platform.onMac())
		        	outputDisplacementY += 25;
		        
		        //this is because PaneHeader was removed, which contains the words "NETWORK" and "NEW NODE"
		        if (document.getIsFirstRow())
		        	outputDisplacementY-=50;
		        
		        //the position of the output node is the position relative to networkPane in master
		        NodeBoxDocument referenceDocument = document.getAppFrame().getReferenceDocument();
		        NetworkPane referenceNetworkPane = referenceDocument.getNetworkPane();
		        
		        int inputRowNo = appFrame.getRowForDocument(referenceDocument);
		        int inputColNo = appFrame.getColForDocument(referenceDocument);
		        
		        int refAltNo = appFrame.getAlternativeNumberRelativeToMonitor(referenceDocument);
		        int refNumAlt = appFrame.getNumAltForDocumentInMonitor(referenceDocument);
		        
		        int inputDisplacementX = referenceNetworkPane.getX() + inputColNo*refNumAlt*referenceNetworkPane.getWidth() + horizontalOffset*(refAltNo) + horizontalOffset*inputColNo*refNumAlt + refAltNo*referenceNetworkPane.getWidth();
		        int inputDisplacementY = inputRowNo*height + referenceNetworkPane.getY() + 50;
		        
		        //if on Windows or Linux add 25 pixel to compenstate for the menubar
		        if (!Platform.onMac())
		        	inputDisplacementY += 25;

		        
		        if (referenceDocument.getIsFirstRow())
		        	inputDisplacementY-=50;
		        
		        //System.out.println("network position:" + document.getNetworkPane().getX() + "," + document.getNetworkPane().getY());
		        

		        //make connections transparent if connecting to missingNodes
		        if (ArrayNodeList == missingNodes)
		        	g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.TRANSPARENCY_LEVEL));
		        	
		        _panZoomPainConnection(g, outputRect, inputNode, inputRect,
						inputPort, outputDisplacementX, outputDisplacementY,
						inputDisplacementX, inputDisplacementY);
		        //reset transparency
	        	g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));

			}
		}
	}

	private boolean checkInOutConnectsToSelectedNodeInReference(String strInOrOutNode) {
		boolean connects = true;
		ApplicationFrame af = document.getAppFrame();
		if ((af.getReferenceDocument() == af.getCurrentDocument())){
			Set<String> selectedNodesNamesInReferenceView = document.getAppFrame().getReferenceDocument().getNetworkView().getSelectedNodeNamesAsSet();
			String strSelectedNodeInRef;
			if (selectedNodesNamesInReferenceView.size() == 1){
				//System.out.println("selectedNodesNamesInReferenceView:" + selectedNodesNamesInReferenceView);
				strSelectedNodeInRef = selectedNodesNamesInReferenceView.iterator().next();
				connects = (strSelectedNodeInRef.equals(strInOrOutNode));
			}
		}
		return connects;
	}

	private void _paintElephantConnectionToReferenceNodeOutput(Graphics2D g, Connection connection,
			String strInputNode, String strOutputNode, ArrayList<Node> ArrayNodeList) {
		//for reference view, if a single node was selected there and the connector doesnt connect to it, then don't draw it
		if (!checkInOutConnectsToSelectedNodeInReference(strOutputNode))
			return;
		
		for (Node node : ArrayNodeList){
			if(strInputNode.equals(node.getName())){ //input is in newNodes
					
				Node n = findNodeWithName(strOutputNode); 
		        Rectangle outputRect;
		        Node inputNode = findNodeWithName(strInputNode);
		        
				if (ArrayNodeList == missingNodes){					
					//get reference view
					ApplicationFrame appFrame = document.getAppFrame();
			    	NodeBoxDocument referenceDocument = appFrame.getReferenceDocument();
			    	NetworkView referenceView = referenceDocument.getNetworkView();
			    	
			        outputRect = nodeRect(referenceView.findNodeWithName(strOutputNode));
			        inputNode = referenceView.findNodeWithName(strInputNode);
				}
				else
					outputRect = nodeRect(n);

		        Rectangle inputRect = nodeRect(inputNode);
		        Port inputPort = inputNode.getInput(connection.getInputPort());
		        //the position of input node is the position relative to networkPane + hardcoded value position
		        
		    	ApplicationFrame appFrame = document.getAppFrame();
		        int height = appFrame.alternativesPanel[0].getHeight() - 50;

		        NetworkPane networkPane = document.getNetworkPane();
		        int inputRowNo = appFrame.getRowForDocument(document);
		        int inputColNo = appFrame.getColForDocument(document);
		        
		        int docAltNo = appFrame.getAlternativeNumberRelativeToMonitor(document);
		        int docNumAlt = appFrame.getNumAltForDocumentInMonitor(document);
		        
		        //here is my best attempt to explain how X position of node is calculated:
		        //X coordinate of node is initial location
		        //To add you add width times which column it is, width x col
		        //however, once you start splitting alternatives, the width gets shrinked as well
		        //thats why you need to multiply it by num of alternatives (width x col) x numAlt to even it out
		        //at last you add the displacement that the this alternative relative to the monitor creates which is width x altNo

		        
		        int inputDisplacementX = networkPane.getX() + inputColNo*docNumAlt*networkPane.getWidth() + docAltNo * networkPane.getWidth() + horizontalOffset*(docAltNo) + horizontalOffset*inputColNo*docNumAlt;
		        int inputDisplacementY = inputRowNo*height + networkPane.getY() + 50; 	//if second row for input node, add distance here
		        
		        //if on Windows or Linux add 25 pixel to compenstate for the menubar
		        if (!Platform.onMac())
		        	inputDisplacementY += 25;

		        if (document.getIsFirstRow())
		        	inputDisplacementY-=50;
		        
		        NodeBoxDocument referenceDocument = document.getAppFrame().getReferenceDocument();
		        
		        NetworkPane referenceNetworkPane = referenceDocument.getNetworkPane();
		        int outputRowNo = appFrame.getRowForDocument(referenceDocument);
		        int outputColNo = appFrame.getColForDocument(referenceDocument);
		        
		        int refAltNo = appFrame.getAlternativeNumberRelativeToMonitor(referenceDocument);
		        int refNumAlt = appFrame.getNumAltForDocumentInMonitor(referenceDocument);
		        
		        int outputDisplacementX = referenceNetworkPane.getX() + outputColNo*refNumAlt*referenceNetworkPane.getWidth() + horizontalOffset*(refAltNo) + horizontalOffset*outputColNo*refNumAlt+ refAltNo*referenceNetworkPane.getWidth();
		        int outputDisplacementY = outputRowNo*height + referenceNetworkPane.getY() + 50; //if 2nd for reference node row add height difference here here
		        
		        //if on Windows or Linux add 25 pixel to compenstate for the menubar
		        if (!Platform.onMac())
		        	outputDisplacementY += 25;
		        
		        if (referenceDocument.getIsFirstRow())
		        	outputDisplacementY-=50;
		        
		        //make connections transparent if connecting to missingNodes
		        if (ArrayNodeList == missingNodes)
		        	g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.TRANSPARENCY_LEVEL));
		        
		        _panZoomPainConnection(g, outputRect, inputNode, inputRect,
						inputPort, outputDisplacementX, outputDisplacementY,
						inputDisplacementX, inputDisplacementY);
		        
		        //reset transparency
	        	g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));
			}
		}
	}

    private void paintGrid(Graphics2D g) {
    	if (document == document.getAppFrame().getCurrentDocument() && document.getAlternativePaneHeader().isEditable() || isDesignGalleryNetwork || document.isHistoryPreviewDocument)
            g.setColor(Theme.ACTIVE_NETWORK_GRID_COLOR);
    	else
            g.setColor(Theme.NETWORK_GRID_COLOR);
    	
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .25f));

        int gridCellSize = (int) Math.round(GRID_CELL_SIZE * getViewScale());
        int gridOffset = (int) Math.round(GRID_OFFSET * getViewScale());
        if (gridCellSize < 10) return;

        int transformOffsetX = (int) (getViewX() % gridCellSize);
        int transformOffsetY = (int) (getViewY() % gridCellSize);

        for (int y = -gridCellSize; y < getHeight() + gridCellSize; y += gridCellSize) {
            g.drawLine(0, y - gridOffset + transformOffsetY, getWidth(), y - gridOffset + transformOffsetY);
        }
        for (int x = -gridCellSize; x < getWidth() + gridCellSize; x += gridCellSize) {
            g.drawLine(x - gridOffset + transformOffsetX, 0, x - gridOffset + transformOffsetX, getHeight());
        }
        
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));

    }

    private void paintConnections(Graphics2D g) {
        g.setColor(Theme.CONNECTION_DEFAULT_COLOR);
        g.setStroke(CONNECTION_STROKE);
        for (Connection connection : getConnections()) {
            paintConnection(g, connection);
        }
    }
    
    private void paintConnectionsSelectively(Graphics2D g, Iterable<Connection> connections, Color color, BasicStroke stroke) {
        g.setColor(color);
        g.setStroke(stroke);
        for (Connection connection : connections) {
            paintConnection(g, connection);
        }
    }

    private void paintConnection(Graphics2D g, Connection connection) {
        Node outputNode = findNodeWithName(connection.getOutputNode());
        Node inputNode = findNodeWithName(connection.getInputNode());
        Port inputPort = inputNode.getInput(connection.getInputPort());
        //g.setColor(portTypeColor(outputNode.getOutputType())); //as recommended by Wolfgang, everything is in black color
        Rectangle outputRect = nodeRect(outputNode);
        Rectangle inputRect = nodeRect(inputNode);
        paintConnectionLine(g, outputRect.x + 4, outputRect.y + outputRect.height + 1, inputRect.x + portOffset(inputNode, inputPort) + 4, inputRect.y - 4);

    }

    private void paintCurrentConnection(Graphics2D g) {
        g.setColor(Theme.CONNECTION_DEFAULT_COLOR);
        if (connectionOutput != null) {
        	
            Rectangle outputRect = nodeRect(connectionOutput);
            g.setColor(portTypeColor(connectionOutput.getOutputType()));
            try {
            	paintConnectionLine(g, outputRect.x + 4, outputRect.y + outputRect.height + 1, (int) connectionPoint.getX(), (int) connectionPoint.getY());
        	}
        	catch (Exception e)
        	{
        		System.err.println("connectionPoint is " + connectionPoint);
        	}
        }
    }

    private void paintConnectionLine(Graphics2D g, int x0, int y0, int x1, int y1) {
        double dy = Math.abs(y1 - y0);
        if (dy < GRID_CELL_SIZE) {
            g.drawLine(x0, y0, x1, y1);
        } else {
        	double viewScale = getViewScale();
        	//as you zoom out, you want the oscillation to get smaller relative to the distance
        	//thats why divisor is not simply half the distance between x1 and x0, but it's relative
        	//to the zoom level. However, we don't want the divisor to get too large, that's why
        	//we set a boundary at 10.
        	double divisor = Math.min(10, 3 / viewScale);
            double fracDxZoomed = Math.abs(x1 - x0) / divisor;
            Path2D.Float p = new Path2D.Float();
            //GeneralPath p = new GeneralPath();
            p.moveTo(x0, y0);
            p.curveTo(x0, y0 + fracDxZoomed, x1, y1 - fracDxZoomed, x1, y1);
            g.draw(p);
        }
    }

    private void paintNodesDraggable(Graphics2D g) {
        g.setColor(Theme.NETWORK_NODE_NAME_COLOR);
        Node renderedNode = getActiveNetwork().getRenderedChild();
        NodeBoxDocument refDoc = document.getAppFrame().getReferenceDocument();
        for (Node node : getNodes()) {
        	
        	boolean isDraggable = false;
        	if (refDoc != null){
	        	try{
	        		isDraggable = draggableReferenceNodesNames.contains(node.getName());
	        	}catch (java.lang.NullPointerException e){}
	        	//System.out.println(node.getName() + " isDraggable=" + isDraggable);
        	}
        	
            Port hoverInputPort = overInput != null && overInput.getNode().getName().equals(node.getName()) ? findNodeWithName(overInput.getNode().getName()).getInput(overInput.port) : null;
            BufferedImage icon = getCachedImageForNode(node);
            paintNode(g, getActiveNetwork(), node, icon, isSelected(node), renderedNode == node, connectionOutput, hoverInputPort, overOutput == node, null, isDraggable, false);
        }
    }
    private void paintNodes(Graphics2D g) { //this is draw outline to indicate if the nodes can be dragged or not
        g.setColor(Theme.NETWORK_NODE_NAME_COLOR);
        Node renderedNode = getActiveNetwork().getRenderedChild();
        NodeBoxDocument refDoc = document.getAppFrame().getReferenceDocument();
        for (Node node : getNodes()) {
        	
            Port hoverInputPort = overInput != null && overInput.getNode().getName().equals(node.getName()) ? findNodeWithName(overInput.getNode().getName()).getInput(overInput.port) : null;
            BufferedImage icon = getCachedImageForNode(node);
            paintNode(g, getActiveNetwork(), node, icon, isSelected(node), renderedNode == node, connectionOutput, hoverInputPort, overOutput == node, null, false, false);
        }
    }
    
    private void paintNodesSelectively(Graphics2D g, ArrayList<Node> diffNodes, Color overridedNodeColor, boolean isDraggable, boolean isMissingNode) {
        g.setColor(Theme.NETWORK_NODE_NAME_COLOR);
        Node renderedNode = getActiveNetwork().getRenderedChild();
        for (Node node : diffNodes) {
            Port hoverInputPort = overInput != null && overInput.getNode().getName().equals(node.getName()) ? findNodeWithName(overInput.getNode().getName()).getInput(overInput.port) : null;
            BufferedImage icon = getCachedImageForNode(node);
            paintNode(g, getActiveNetwork(), node, icon, isSelected(node), renderedNode == node, connectionOutput, hoverInputPort, overOutput == node, overridedNodeColor, isDraggable, isMissingNode);
        }
    }

    private BufferedImage getCachedImageForNode(Node node) {
        try {
            return nodeImageCache.get(node);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static Color portTypeColor(String type) {
        Color portColor = PORT_COLORS.get(type);
        return portColor == null ? DEFAULT_PORT_COLOR : portColor;
    }

    private static String getShortenedName(String name, int startChars) {
        nodebox.graphics.Text text = new nodebox.graphics.Text(name, nodebox.graphics.Point.ZERO);
        text.setFontName(Theme.NETWORK_FONT.getFontName());
        text.setFontSize(Theme.NETWORK_FONT.getSize());
        int cells = Math.min(Math.max(3, 1 + (int) Math.ceil(text.getMetrics().getWidth() / (GRID_CELL_SIZE - 6))), 6);
        if (cells > 4)
            return getShortenedName(name.substring(0, startChars) + "\u2026" + name.substring(name.length() - 3, name.length()), startChars - 1);
        return name;
    }

    private void paintNode(Graphics2D g, Node network, Node node, BufferedImage icon, boolean selected, boolean rendered, Node connectionOutput, Port hoverInputPort, boolean hoverOutput, Color overridedNodeColor, boolean isDraggable, boolean isMissingNode) {
        Rectangle r = nodeRect(node);
        String outputType = node.getOutputType();

        //if the node is draggable (in reference view, draw outline representing the ability
        if (isDraggable){
	        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .75f));
	        g.setColor(Theme.DRAGGED_NODE_COLOR);
	        g.fillRect(r.x - 8, r.y - 8, NODE_WIDTH + 16, NODE_HEIGHT + 16);
	        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));
        }

        
        // Draw selection ring
        if (selected) {
            g.setColor(Color.MAGENTA);
            g.fillRect(r.x-5, r.y-5, NODE_WIDTH+10, NODE_HEIGHT+10);
        }

        // Draw node
        
        if (isMissingNode){
        	//Color color = portTypeColor(outputType);
        	Color color = Color.black; //as recommended by Wolfgang everything is black now
        	drawPlusOrMinus(g, r, color, false);
        	g.setColor(color);
        }
        else if (overridedNodeColor == null){
        	//g.setColor(portTypeColor(outputType));
        	g.setColor(Color.black); //as Wolfgang recommended draw all nodes in black
        }
        else{
        	g.setColor(overridedNodeColor);
        	if (overridedNodeColor == Theme.NEW_NODE_COLOR){
        		drawPlusOrMinus(g, r, overridedNodeColor, true);
        	}
        	else if (overridedNodeColor == Theme.CHANGED_NODE_COLOR){
        		drawEqualOrNotEqual(g, r, overridedNodeColor, false);
        	}
        	else if (overridedNodeColor == Theme.DRAGGED_NODE_COLOR){
        		drawEqualOrNotEqual(g, r, overridedNodeColor, true);
        	}
        	g.setColor(overridedNodeColor);
        }
        
        if (isMissingNode)
        	g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.TRANSPARENCY_LEVEL));
        
        if (selected) {
            g.fillRect(r.x + 2, r.y + 2, NODE_WIDTH - 4, NODE_HEIGHT - 4);
        } else {
            g.fillRect(r.x, r.y, NODE_WIDTH, NODE_HEIGHT);
        }

        // Draw render flag
        if (rendered) {
            g.setColor(Color.WHITE);
            GeneralPath gp = new GeneralPath();
            gp.moveTo(r.x + NODE_WIDTH - 2, r.y + NODE_HEIGHT - 20);
            gp.lineTo(r.x + NODE_WIDTH - 2, r.y + NODE_HEIGHT - 2);
            gp.lineTo(r.x + NODE_WIDTH - 20, r.y + NODE_HEIGHT - 2);
            g.fill(gp);
        }

        // Draw input ports
        g.setColor(Color.WHITE);
        int portX = 0;
        for (Port input : node.getInputs()) {
            if (isHiddenPort(input)) {
                continue;
            }
            if (hoverInputPort == input) {
                g.setColor(PORT_HOVER_COLOR);
            } else {
                //g.setColor(portTypeColor(input.getType()));
            	 g.setColor(Color.black); //as Wolfgang recommended we make all colors black
            }
            // Highlight ports that match the dragged connection type
            int portHeight = PORT_HEIGHT;
            if (connectionOutput != null) {
                String connectionOutputType = connectionOutput.getOutputType();
                String inputType = input.getType();
                if (connectionOutputType.equals(inputType) || inputType.equals(Port.TYPE_LIST)) {
                    portHeight = PORT_HEIGHT * 2;
                } else if (TypeConversions.canBeConverted(connectionOutputType, inputType)) {
                    portHeight = PORT_HEIGHT - 1;
                } else {
                    portHeight = 1;
                }
            }

            if (isPublished(network, node, input)) {
                Point2D topLeft = inverseViewTransformPoint(new Point(4, 0));
                g.setColor(portTypeColor(input.getType()));
                g.setStroke(CONNECTION_STROKE);
                paintConnectionLine(g, (int) topLeft.getX(), (int) topLeft.getY(), r.x + portX + 4, r.y - 2);
            }

            g.fillRect(r.x + portX, r.y - portHeight, PORT_WIDTH, portHeight);


            portX += PORT_WIDTH + PORT_SPACING;
        }

        // Draw output port
        if (hoverOutput && connectionOutput == null) {
            g.setColor(PORT_HOVER_COLOR);
        } else {
            //g.setColor(portTypeColor(outputType));
            g.setColor(Color.black); //as Wolfgang recommended we make all colors black

        }
        g.fillRect(r.x, r.y + NODE_HEIGHT, PORT_WIDTH, PORT_HEIGHT);

        // Draw icon
        g.drawImage(icon, r.x + NODE_PADDING, r.y + NODE_PADDING, NODE_ICON_SIZE, NODE_ICON_SIZE, null);
        g.setColor(Color.WHITE);
        g.setFont(Theme.NETWORK_FONT);
        g.drawString(getShortenedName(node.getName(), 7), r.x + NODE_ICON_SIZE + NODE_PADDING * 2 + 2, r.y + 26);
        
        //reset transparency if it's missing node
        if (isMissingNode)
        	g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));
    }

	private static void drawPlusOrMinus(Graphics2D g, Rectangle r, Color overrideColor, boolean plus) {
		//also draw a "plus" to designate this is a new node
		
		int diameter = NODE_HEIGHT/2;
		//first draw a circle
		g.setColor(Color.black);
		g.fillOval(r.x - diameter - 3, r.y - 1, diameter + 2, diameter + 2);
		g.setColor(overrideColor);
		g.fillOval(r.x - diameter - 2, r.y,diameter, diameter);
		//then draw the plus
		
		//draw outline "-"
		g.setColor(Color.black);
		g.fillRect(r.x - diameter - 1, r.y + diameter / 2 - 3, diameter - 2, diameter / 4 + 2);
		//draw "-"
		g.setColor(Color.white);
		g.fillRect(r.x - diameter, r.y + diameter / 2 - 2, diameter - 4, diameter / 4);

		if (plus){
			//draw outline "|"
			g.setColor(Color.black);
			g.fillRect(r.x - diameter + 4, r.y + 1, diameter / 4 + 2, diameter - 2);
			
			// draw "|"
			g.setColor(Color.white);
			g.fillRect(r.x - diameter + 5, r.y + 2, diameter / 4, diameter - 4);
			//draw "-" again (to overwrite the black outline |)
			g.fillRect(r.x - diameter, r.y + diameter / 2 - 2, diameter - 4, diameter / 4);
		}
	}
	private static void drawEqualOrNotEqual(Graphics2D g, Rectangle r, Color overrideColor, boolean equal) {
		//also draw a "plus" to designate this is a new node
		
		int diameter = NODE_HEIGHT/2;
		//first draw a circle
		g.setColor(Color.black);
		g.fillOval(r.x - diameter - 3, r.y - 1, diameter + 2, diameter + 2);
		g.setColor(overrideColor);
		g.fillOval(r.x - diameter - 2, r.y,diameter, diameter);
		//then draw the plus
		
		
		//first -
		//draw outline "-"
		g.setColor(Color.black);
		g.fillRect(r.x - diameter - 1, r.y + diameter / 2 - 4, diameter - 2, diameter / 8 + 2);
		//draw "-"
		g.setColor(Color.white);
		g.fillRect(r.x - diameter, r.y + diameter / 2 - 3, diameter - 4, diameter / 8);
		
		//second -
		//draw outline "-"
		int delta = +  diameter / 6;
		g.setColor(Color.black);
		g.fillRect(r.x - diameter - 1, r.y + diameter / 2 + delta - 2, diameter - 2, diameter / 8 + 2);
		//draw "-"
		g.setColor(Color.white);
		g.fillRect(r.x - diameter, r.y + diameter / 2 + delta - 1, diameter - 4, diameter / 8);
		
		
		if (!equal){
			//g.setColor(Color.black);
			//g.drawLine(r.x - diameter / 2 + 1 , r.y + 3, r.x - diameter / 2 + 4,  r.y + 3); //first small line
			g.setColor(Color.white);
			g.drawLine(r.x - diameter / 2 + 2 , r.y + 4, r.x - diameter + 2, r.y + diameter - 3); //slanted 
			g.drawLine(r.x - diameter / 2 + 3 , r.y + 4, r.x - diameter + 3, r.y + diameter - 3); //slanted 
			g.drawLine(r.x - diameter / 2 + 4 , r.y + 4, r.x - diameter + 4, r.y + diameter - 3); //slanted
			
			g.setColor(Color.black);
			//black slanted lines
			g.drawLine(r.x - diameter / 2 + 1 , r.y + 4, r.x - diameter + 1, r.y + diameter - 3); //slanted 
			g.drawLine(r.x - diameter / 2 + 5 , r.y + 4, r.x - diameter + 5, r.y + diameter - 3); //slanted 

			//black small lines
			g.drawLine(r.x - diameter / 2 + 1 , r.y + 3, r.x - diameter / 2 + 5,  r.y + 3); //first small line
			g.drawLine(r.x - diameter + 1 , r.y + diameter - 2, r.x - diameter + 5,  r.y + diameter - 2); //first small line

						
			//redraw "-" first and second one
			g.setColor(Color.white);
			g.fillRect(r.x - diameter, r.y + diameter / 2 - 3, diameter - 4, diameter / 8);
			g.fillRect(r.x - diameter, r.y + diameter / 2 + delta - 1, diameter - 4, diameter / 8);			
		}
	}

    private void paintPortTooltip(Graphics2D g) {
        if (overInput != null) {
            Node overInputNode = findNodeWithName(overInput.getNode().getName());
            Port overInputPort = overInputNode.getInput(overInput.port);
            Rectangle r = inputPortRect(overInputNode, overInputPort);
            Point2D pt = new Point2D.Double(r.getX(), r.getY() + 11);
            String text = String.format("%s (%s)", overInput.port, overInputPort.getType());
            paintTooltip(g, pt, text);
        } else if (overOutput != null && connectionOutput == null) {
            Rectangle r = outputPortRect(overOutput);
            Point2D pt = new Point2D.Double(r.getX(), r.getY() + 11);
            String text = String.format("output (%s)", overOutput.getOutputType());
            paintTooltip(g, pt, text);
        }
    }
    
    private void paintPortTooltipSelectively(Graphics2D g, ArrayList<Node> diffNodes) {
        if (overInput != null) {
            Node overInputNode = findNodeWithName(overInput.getNode().getName());
            //if this overInputNode is hidden then don't
            if (diffNodes.contains(overInputNode)) return;
            Port overInputPort = overInputNode.getInput(overInput.port);
            Rectangle r = inputPortRect(overInputNode, overInputPort);
            Point2D pt = new Point2D.Double(r.getX(), r.getY() + 11);
            String text = String.format("%s (%s)", overInput.port, overInputPort.getType());
            paintTooltip(g, pt, text);
        } else if (overOutput != null && connectionOutput == null) {
            Rectangle r = outputPortRect(overOutput);
            Point2D pt = new Point2D.Double(r.getX(), r.getY() + 11);
            String text = String.format("output (%s)", overOutput.getOutputType());
            paintTooltip(g, pt, text);
        }
    }

    private static void paintTooltip(Graphics2D g, Point2D point, String text) {
        FontMetrics fontMetrics = g.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(text);

        int verticalOffset = 10;
        Rectangle r = new Rectangle((int) point.getX(), (int) point.getY() + verticalOffset, textWidth, fontMetrics.getHeight());
        r.grow(4, 3);
        g.setColor(TOOLTIP_STROKE_COLOR);
        g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setColor(TOOLTIP_BACKGROUND_COLOR);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);

        g.setColor(TOOLTIP_TEXT_COLOR);
        g.drawString(text, (float) point.getX(), (float) point.getY() + fontMetrics.getAscent() + verticalOffset);
    }

    private void paintDragSelection(Graphics2D g) {
        if (isDragSelecting) {
            Rectangle r = dragSelectRect();
            g.setColor(DRAG_SELECTION_COLOR);
            g.setStroke(DRAG_SELECTION_STROKE);
            g.fill(r);
            // To get a smooth line we need to subtract one from the width and height.
            g.drawRect((int) r.getX(), (int) r.getY(), (int) r.getWidth() - 1, (int) r.getHeight() - 1);
        }
    }

    private Rectangle dragSelectRect() {
        if (dragStartPoint == null || dragCurrentPoint == null) return new Rectangle();
        int x0 = (int) dragStartPoint.getX();
        int y0 = (int) dragStartPoint.getY();
        int x1 = (int) dragCurrentPoint.getX();
        int y1 = (int) dragCurrentPoint.getY();
        int x = Math.min(x0, x1);
        int y = Math.min(y0, y1);
        int w = (int) Math.abs(dragCurrentPoint.getX() - dragStartPoint.getX());
        int h = (int) Math.abs(dragCurrentPoint.getY() - dragStartPoint.getY());
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle nodeRect(Node node) {
        return new Rectangle(nodePoint(node), NODE_DIMENSION);
    }

    private static Rectangle inputPortRect(Node node, Port port) {
        if (isHiddenPort(port)) return new Rectangle();
        Point pt = nodePoint(node);
        Rectangle portRect = new Rectangle(pt.x + portOffset(node, port), pt.y - PORT_HEIGHT, PORT_WIDTH, PORT_HEIGHT);
        growHitRectangle(portRect);
        return portRect;
    }

    private static Rectangle outputPortRect(Node node) {
        Point pt = nodePoint(node);
        Rectangle portRect = new Rectangle(pt.x, pt.y + NODE_HEIGHT, PORT_WIDTH, PORT_HEIGHT);
        growHitRectangle(portRect);
        return portRect;
    }

    private static void growHitRectangle(Rectangle r) {
        r.grow(2, 2);
    }

    private static Point nodePoint(Node node) {
        int nodeX = ((int) node.getPosition().getX()) * GRID_CELL_SIZE;
        int nodeY = ((int) node.getPosition().getY()) * GRID_CELL_SIZE;
        return new Point(nodeX, nodeY);
    }

    private Point pointToGridPoint(Point e) {
        Point2D pt = getInverseViewTransform().transform(e, null);
        return new Point(
                (int) Math.floor(pt.getX() / GRID_CELL_SIZE),
                (int) Math.floor(pt.getY() / GRID_CELL_SIZE));
    }

    public Point centerGridPoint() {
        Point pt = pointToGridPoint(new Point((int) (getBounds().getWidth() / 2), (int) (getBounds().getHeight() / 2)));
        return new Point((int) pt.getX() - 1, (int) pt.getY());
    }

    private static int portOffset(Node node, Port port) {
        int portIndex = node.getInputs().indexOf(port);
        return (PORT_WIDTH + PORT_SPACING) * portIndex;
    }

    //// View queries ////

    private Node findNodeWithName(String name) {
        return getActiveNetwork().getChild(name);
    }

    public Node getNodeAt(Point2D point) {
        for (Node node : getNodesReversed()) {
            Rectangle r = nodeRect(node);
            if (r.contains(point)) {
                return node;
            }
        }
        return null;
    }
    
    public Node getNodeByName(String name) {
    	for (Node n : getNodes())
    		if (n.getName().equals(name)) 
    			return n;
    	return null;
    }

    public Node getNodeWithOutputPortAt(Point2D point) {
        for (Node node : getNodesReversed()) {
            Rectangle r = outputPortRect(node);
            if (r.contains(point)) {
                return node;
            }
        }
        return null;
    }

    public NodePort getInputPortAt(Point2D point) {
        for (Node node : getNodesReversed()) {
            for (Port port : node.getInputs()) {
                Rectangle r = inputPortRect(node, port);
                if (r.contains(point)) {
                    return NodePort.of(node, port.getName());
                }
            }
        }
        return null;
    }

    private static boolean isHiddenPort(Port port) {
        return port.getType().equals(Port.TYPE_STATE) || port.getType().equals(Port.TYPE_CONTEXT);
    }

    //// Selections ////

    public boolean isSelected(Node node) {
        return (selectedNodes.contains(node.getName()));
    }

    public void select(Node node) {
        selectedNodes.add(node.getName());

    }
    
    public void addToSelectedNodes(String strNode){
    	selectedNodes.add(strNode);
    }
    
    public void clearSelectedNodes(){
    	selectedNodes.clear();
    }

    /**
     * Select this node, and only this node.
     * <p/>
     * All other selected nodes will be deselected.
     *
     * @param node The node to select. If node is null, everything is deselected.
     */
    
    /*public void singleSelect(Node node) {
        if (selectedNodes.size() == 1 && selectedNodes.contains(node.getName())) return;
        selectedNodes.clear();
        if (node != null && getActiveNetwork().hasChild(node)) {
            selectedNodes.add(node.getName());
            firePropertyChange(SELECT_PROPERTY, null, selectedNodes);
            document.setActiveNode(node);
            //NodeBoxDocument.updateAllPortViews(); //added by shumon sept 11, 2013
            if (node.getUUID() != null)
            	System.out.println("Selected: " + node.getUUID().toString());
            else
            	System.out.println("Selected: " + null);  
            System.out.println("Selected node name: " + node.getName());

        }
        repaint();
    }*/
    
    //new by Shumon August 23, 2013
    public void singleSelect(Node node) {

    	for (int i = 0; i < document.getDocumentGroup().size(); i++){
    		NodeBoxDocument doc = document.getDocumentGroup().get(i);
    		Node node2 = NodeBoxDocument.getNodeByUUIDfromDoc(doc, node.getUUID());
    		NetworkView nw = doc.getNetworkView();
		    singleSelectSimple(node2, nw);
		    if (node2 == null){ //if this node doesn't exist in this doc
		    	NodeBoxDocument.clearNodeViews(doc);
		    }

    	}
    }

	public static void singleSelectSimple(Node node, NetworkView nw) {
		if (nw.selectedNodes.size() == 1 && node != null && nw.selectedNodes.contains(node.getName())) return;
		nw.selectedNodes.clear();
		if (node != null && nw.getActiveNetwork().hasChild(node)) {
		    nw.selectedNodes.add(node.getName());
		    nw.firePropertyChange(SELECT_PROPERTY, null, nw.selectedNodes);
		    nw.document.setActiveNode(node);
		}
		nw.repaint();

	}
	

    public void select(Iterable<Node> nodes) {
        selectedNodes.clear();
        for (Node node : nodes) {
            selectedNodes.add(node.getName());
        }
    }

    public void toggleSelection(Node node) {
        checkNotNull(node);
        if (selectedNodes.isEmpty()) {
            singleSelectSimple(node, this);

        } else {
            if (selectedNodes.contains(node.getName())) {
                selectedNodes.remove(node.getName());
            } else {
                selectedNodes.add(node.getName());
            }
            firePropertyChange(SELECT_PROPERTY, null, selectedNodes);
            repaint();
        }
    }

    public void deselectAll() {
        if (selectedNodes.isEmpty()) return;
        selectedNodes.clear();
        firePropertyChange(SELECT_PROPERTY, null, selectedNodes);
        document.setActiveNode((Node) null);
        repaint();

    }

    public Iterable<String> getSelectedNodeNames() {
        return selectedNodes;
    }

    public Iterable<Node> getSelectedNodes() {
        if (selectedNodes.isEmpty()) return ImmutableList.of();
        ImmutableList.Builder<Node> b = new ImmutableList.Builder<nodebox.node.Node>();
        for (String name : getSelectedNodeNames()) {
            b.add(findNodeWithName(name));
        }
        return b.build();
    }

    public ArrayList<Node> getSelectedNodesList(){
    	ArrayList<Node> selectedNodesList = new ArrayList<Node>();
    	for (String name : getSelectedNodeNames()) {
    		selectedNodesList.add(findNodeWithName(name));
    	}
    	return selectedNodesList;
    }
    
    public void deleteSelection() {
        document.removeNodes(getSelectedNodes());        
    }

    private void moveSelectedNodes(int dx, int dy) {
        for (Node node : getSelectedNodes()) {
            getDocument().setNodePosition(node, node.getPosition().moved(dx, dy));
        }
    }

    private void renameNode(Node node) {
        String s = JOptionPane.showInputDialog(this, "New name:", node.getName());
        if (s == null || s.length() == 0)
            return;
        try {
            document.setNodeName(node, s);
        } catch (InvalidNameException ex) {
            JOptionPane.showMessageDialog(this, "The given name is not valid.\n" + ex.getMessage(), Application.NAME, JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "An error occurred:\n" + ex.getMessage(), Application.NAME, JOptionPane.ERROR_MESSAGE);
        }
    }
    //overrided methods
    @Override
    public void setViewTransform(double viewX, double viewY, double viewScale) {
    	for (NodeBoxDocument doc : document.getDocumentGroup()){
    		NetworkView networkView = doc.getNetworkView();
    		_setViewTransform(viewX, viewY, viewScale, networkView);
    		NetworkView.setDottedStroke(viewScale);
    	}
    }

    //// Network navigation ////

    public void goUp() {
        if (getDocument().getActiveNetworkPath().equals("/")) return;
        Iterable<String> it = Splitter.on("/").split(getDocument().getActiveNetworkPath());
        int parts = Iterables.size(it);
        String path = parts - 1 > 1 ? Joiner.on("/").join(Iterables.limit(it, parts - 1)) : "/";
        //getDocument().setActiveNetwork(path);
        document.setActiveNetworksEverywhere(path);
        
    }

    //// Input Events ////

    private class KeyHandler extends KeyAdapter {

        public void keyTyped(KeyEvent e) {
            if(!getDocument().getAlternativePaneHeader().isEditable()) //this operation is not allowed if this document is locked
            	return;
            switch (e.getKeyChar()) {
                case KeyEvent.VK_ENTER:
                    if (selectedNodes.size() == 1) {
                        Node node = findNodeWithName(selectedNodes.iterator().next());
                        renameNode(node);
                    }
            }
        }

        public void keyPressed(KeyEvent e) {
            if(!getDocument().getAlternativePaneHeader().isEditable()) //this operation is not allowed if this document is locked
            	return;
            
            int keyCode = e.getKeyCode();
            if (keyCode == KeyEvent.VK_SHIFT) {
                isShiftPressed = true;
            } else if (keyCode == KeyEvent.VK_ALT) {
                isAltPressed = true;
            } else if (keyCode == KeyEvent.VK_UP) {
                moveSelectedNodes(0, -1);
            } else if (keyCode == KeyEvent.VK_RIGHT) {
                moveSelectedNodes(1, 0);
            } else if (keyCode == KeyEvent.VK_DOWN) {
                moveSelectedNodes(0, 1);
            } else if (keyCode == KeyEvent.VK_LEFT) {
                moveSelectedNodes(-1, 0);
            }
            else if (keyCode == KeyEvent.VK_G) {
	        	if (selectedNodes.size() > 0) {
	        		Node node = findNodeWithName(selectedNodes.iterator().next());
	        		nodebox.graphics.Point p = node.getPosition().moved(1, 1); 
	                        getDocument().groupIntoNetwork(p);
	        	}
            }
        	else if (keyCode == KeyEvent.VK_E) {
	        	if (selectedNodes.size() == 1){
	        		Node node = findNodeWithName(selectedNodes.iterator().next());
	        		if (node.getPrototype().getName().equals("network")){
		        		String childPath = Node.path(getDocument().getActiveNetworkPath(), node);
		        		document.setActiveNetworksEverywhere(childPath);	
	        		}
	        	}
        	}
        	else if (keyCode == KeyEvent.VK_U) {
	        	goUp();
	        	
        	}
            
        }

        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                isShiftPressed = false;
            } else if (e.getKeyCode() == KeyEvent.VK_ALT) {
                isAltPressed = false;
            }
        }

    }

    private class MouseHandler implements MouseListener, MouseMotionListener {

        public void mouseClicked(MouseEvent e) {
            if(!getDocument().getAlternativePaneHeader().isEditable()) //this operation is not allowed if this document is locked
            	return;
        	
            //release single node selected somewhere
            ApplicationFrame af = document.getAppFrame();
            if (af.getReferenceDocument() != null){
            	document.getAppFrame().singleNodeSelectedSomwhere = null;
            }

            Point2D pt = inverseViewTransformPoint(e.getPoint());
            //if meta, alt or shift were not pressed, then dont do any of these
            boolean down = ((KeyEvent.META_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK) & e.getModifiersEx()) > 0;
            
            if (e.getButton() == MouseEvent.BUTTON1 && !down) {
                if (e.getClickCount() == 1) {
                    Node clickedNode = getNodeAt(pt);
                    if (clickedNode == null) {
                		for (NodeBoxDocument doc : document.getDocumentGroup()){
                    		doc.getNetworkView().deselectAll();
                			//doc.setActiveNode((Node) null);
                		}
                    } else {
                        if (isShiftPressed) {
                            for (int i = 0; i < document.getDocumentGroup().size(); i++){
                        		NodeBoxDocument doc = document.getDocumentGroup().get(i);
                        		Node n = NodeBoxDocument.getNodeByUUIDfromDoc(doc, clickedNode.getUUID());
                        		if (n != null){
                            		NetworkView nw = doc.getNetworkView();
                        			nw.toggleSelection(n);
                        		}
                            }
                        } else {
                        	//if in reference mode and this is not the reference document, if clicked node is in the sameunchanged node then don't do any selections
                        	if (!isHiddenNodeInDiffMode(clickedNode))
                        		singleSelect(clickedNode);
                        }
                    }
                } else if (e.getClickCount() == 2) {
                    Node clickedNode = getNodeAt(pt);
                    if (clickedNode == null) {
                        Point gridPoint = pointToGridPoint(e.getPoint());
                        getDocument().showNodeSelectionDialog(gridPoint, null);
                    } else {
                    	
                    	//if this is reference view, we force setrendered node instead of simply selecting
                    	if (!isHiddenNodeInDiffMode(clickedNode)) //only if this is not a hidden node in diff vis view
                    		document.setRenderedNode(clickedNode, true);
                    	
                        if (isDesignGalleryNetwork){ //if it's design gallery always reset the view to center and zoom
                        	//also center and zoom viewer
                        	SwingUtilities.invokeLater(new Runnable() {
                				@Override
                				public void run() {
                                	for (NodeBoxDocument doc : document.getDocumentGroup()){
                                		if (doc != document)
                                			doc.getViewerPane().getViewer().centerAndZoom(false);	
                                	}
                				}
                			});
                        }
                        //repaint viewer in all other alternatives, so that the diff view is updated everywhere to reflect the relation to the newly selected renderedNode
                        if (af.getReferenceDocument() != null){
                        	for (NodeBoxDocument doc : document.getDocumentGroup()){
                        		if (doc != document) //no need to repaint current documenter viewer because it is already repainted by setRenderedNode
                        			doc.getViewerPane().getViewer().repaint();
                        	}
                        }
                    }
                }
            }
            //repaint all networks
            if (af.getReferenceDocument() != null){
            	af.getGlassPane().repaint();
            }
        }

		private boolean isHiddenNodeInDiffMode(Node clickedNode) {
        	//if in reference mode and this is not the reference document, if clicked node is in the sameunchanged node then don't do the action (selection or rendering of the node)
			return document.getAppFrame().getReferenceDocument() != null && document.getAppFrame().getReferenceDocument() != document && clickedNode != null && sameUnchangedNodes != null && sameUnchangedNodes.contains(clickedNode);
			
		}

        public void mousePressed(MouseEvent e) {
            if(!getDocument().getAlternativePaneHeader().isEditable()) //this operation is not allowed if this document is locked
            	return;
            
            if (e.isPopupTrigger()) {
                showPopup(e);
            } else if (isDragTrigger(e)) {
            } else {
                Point2D pt = inverseViewTransformPoint(e.getPoint());

                // Check if we're over an output port.
                connectionOutput = getNodeWithOutputPortAt(pt);
                if (connectionOutput != null) return;

                // Check if we're over a connected input port.
                connectionInput = getInputPortAt(pt);
                if (connectionInput != null) {
                    // We're over a port, but is it connected?
                    Connection c = getActiveNetwork().getConnection(connectionInput.getNode().getName(), connectionInput.port);
                    // Disconnect it, but start a new connection on the same node immediately.
                    if (c != null) {
                        getDocument().disconnect(c);
                        connectionOutput = getActiveNetwork().getChild(c.getOutputNode());
                        connectionPoint = pt;
                    }
                    return;
                }

                // Check if we're pressing a node.
                Node pressedNode = getNodeAt(pt);
                if (!isHiddenNodeInDiffMode(pressedNode)){ //only for not hidden nodes if in diff mode
	                if (pressedNode != null) {
	                    // Don't immediately set "isDragging."
	                    // We wait until we actually drag the first time to do the work.
	                    startDragging = true;
	                    return;
	                }
	
	                // We're creating a drag selection.
	                isDragSelecting = true;
	                dragStartPoint = pt;
                }
            }
        }

        public void mouseReleased(MouseEvent e) {
            if(!getDocument().getAlternativePaneHeader().isEditable()) //this operation is not allowed if this document is locked
            	return;
            
        	//now everything is done in glass pane, the hack method
        	//when you click on this network view change the active document
            if (e.isPopupTrigger()) {
                showPopup(e);
            } else {
                isDraggingNodes = false;
                isDragSelecting = false;
                dragStartPoint = null;
                dragCurrentPoint = null;
                if (isAltPressed)
                    getDocument().stopEditing();
                if (connectionOutput != null && connectionInput != null) {
                    getDocument().connect(connectionOutput, connectionInput.getNode(), connectionInput.port);
                    //at this point it's good to repaint glasspane if in reference view mode
                    document.getAppFrame().getGlassPane().repaint();
                }
                connectionOutput = null;
                repaint();
            }
        }

        public void mouseEntered(MouseEvent e) {
            if(!getDocument().getAlternativePaneHeader().isEditable()) return; //this operation is not allowed if this document is locked

            grabFocus();
        }

        public void mouseExited(MouseEvent e) {
        }

        public void mouseDragged(MouseEvent e) {
            if(!getDocument().getAlternativePaneHeader().isEditable()) //this operation is not allowed if this document is locked
            	return;
            
            Point2D pt = inverseViewTransformPoint(e.getPoint());
            // Panning the view has the first priority.
            if (isPanning()) return;

            if (connectionOutput != null) {
                repaint();
                connectionInput = getInputPortAt(pt);
                connectionPoint = pt;
                overOutput = getNodeWithOutputPortAt(pt);
                overInput = getInputPortAt(pt);
            }

            if (startDragging) {
                startDragging = false;
                Node pressedNode = getNodeAt(pt);
                if (pressedNode != null) {
                    if (selectedNodes.isEmpty() || !selectedNodes.contains(pressedNode.getName())) {
                        singleSelect(pressedNode);
                    }
                    if (isAltPressed) {
                        getDocument().dragCopy();
                    }
                    isDraggingNodes = true;

                    dragPositions = selectedNodePositions();
                    dragStartPoint = pt;
                } else {
                    isDraggingNodes = false;
                }
            }

            if (isDraggingNodes) {
                Point2D offset = minPoint(pt, dragStartPoint);
                int gridX = (int) Math.round(offset.getX() / GRID_CELL_SIZE);
                int gridY = (int) Math.round(offset.getY() / (float) GRID_CELL_SIZE);
                for (String name : selectedNodes) {
                    nodebox.graphics.Point originalPosition = dragPositions.get(name);
                    nodebox.graphics.Point newPosition = originalPosition.moved(gridX, gridY);
                    getDocument().setNodePosition(findNodeWithName(name), newPosition);
                }
            }

            if (isDragSelecting) {
                dragCurrentPoint = pt;
                Rectangle r = dragSelectRect();
                //selectedNodes.clear();
                //clear selectedNodes in all
                for (int i = 0; i < document.getDocumentGroup().size(); i++){
            		NodeBoxDocument doc = document.getDocumentGroup().get(i);
            		NetworkView nw = doc.getNetworkView();
            		nw.selectedNodes.clear();
                }
                for (Node node : getNodes()) {
                    if (r.intersects(nodeRect(node)) && !isHiddenNodeInDiffMode(node)) {
                        //add node to selectedNodes in all
                        for (int i = 0; i < document.getDocumentGroup().size(); i++){
                    		NodeBoxDocument doc = document.getDocumentGroup().get(i);
                    		NetworkView nw = doc.getNetworkView();
                    		nw.selectedNodes.add(node.getName());
                        }
                    }
                }
                //repaint in all
                for (int i = 0; i < document.getDocumentGroup().size(); i++){
            		NodeBoxDocument doc = document.getDocumentGroup().get(i);
            		NetworkView nw = doc.getNetworkView();
            		nw.repaint();
                }
            }
        }

        public void mouseMoved(MouseEvent e) {
            Point2D pt = inverseViewTransformPoint(e.getPoint());
            overOutput = getNodeWithOutputPortAt(pt);
            overInput = getInputPortAt(pt);
            // It is probably very inefficient to repaint the view every time the mouse moves.
            //repaint();
            
            //thats why the new way is to repaint only if there is a change in over inputs or outputs
            if (overOutput != oldOverOutput || overInput != oldOverInput)
            	repaint();
            oldOverOutput = overOutput;
            oldOverInput = overInput;
        }
    }


    public void zoom(double scaleDelta) {
        // todo: implement
    }

    public boolean containsPoint(Point point) {
        return isVisible() && getBounds().contains(point);
    }

    private void showPopup(MouseEvent e) {
        Point pt = e.getPoint();
        NodePort nodePort = getInputPortAt(inverseViewTransformPoint(pt));
        if (nodePort != null) {
            JPopupMenu pMenu = new JPopupMenu();
            pMenu.add(new PublishAction(nodePort));

            if (findNodeWithName(nodePort.getNode().getName()).hasPublishedInput(nodePort.getPort()))
                pMenu.add(new GoToPortAction(nodePort));

            pMenu.show(this, e.getX(), e.getY());
        } else {
            Node pressedNode = getNodeAt(inverseViewTransformPoint(pt));
            if (pressedNode != null) {
                //goInSubnetworkMenuItem.setVisible(pressedNode.isNetwork());
            	
            	//only do this if in diff mode and if this document isn't master
            	//boolean isAlternativeMenu = document.getAppFrame().getReferenceDocument() != null && document.isReference == false;
                
            	nodeMenu = createNodeMenu(pressedNode);
                nodeMenuLocation = pt;
                /*if (document.getAppFrame().getReferenceDocument() != null && document.isReference == false){ //only do this if in diff mode and if this document isn't master
	                alternativesNodeMenu.show(this, e.getX(), e.getY());
                }
                else{*/
	                nodeMenu.show(this, e.getX(), e.getY());
                //}
            } else {
                networkMenuLocation = pt;
                networkMenu.show(this, e.getX(), e.getY());
            }
        }
    }

    private ImmutableMap<String, nodebox.graphics.Point> selectedNodePositions() {
        ImmutableMap.Builder<String, nodebox.graphics.Point> b = ImmutableMap.builder();
        for (String nodeName : selectedNodes) {
            b.put(nodeName, findNodeWithName(nodeName).getPosition());
        }
        return b.build();
    }

    private Point2D minPoint(Point2D a, Point2D b) {
        return new Point2D.Double(a.getX() - b.getX(), a.getY() - b.getY());
    }

    private class FocusHandler extends FocusAdapter {

        @Override
        public void focusLost(FocusEvent focusEvent) {
            isShiftPressed = false;
            isAltPressed = false;
        }

    }

    private static class NodeImageCacheLoader extends CacheLoader<Node, BufferedImage> {
        private NodeRepository nodeRepository;

        private NodeImageCacheLoader(NodeRepository nodeRepository) {
            this.nodeRepository = nodeRepository;
        }

        @Override
        public BufferedImage load(Node node) throws Exception {
            for (NodeLibrary library : nodeRepository.getLibraries()) {
                BufferedImage img = findNodeImage(library, node);
                if (img != null) {
                    return img;
                }
            }
            if (node.getPrototype() != null) {
                return load(node.getPrototype());
            } else {
                return nodeGeneric;
            }
        }
    }

    private class NewNodeAction extends AbstractAction {
        private NewNodeAction() {
            super("New Node");
        }

        public void actionPerformed(ActionEvent e) {
            if(getDocument().getAlternativePaneHeader().isEditable()) //this operation is not allowed if this document is locked
            {
	            Point gridPoint = pointToGridPoint(networkMenuLocation);
	            getDocument().showNodeSelectionDialog(gridPoint, null);
            }
        }
    }

    private class ResetViewAction extends AbstractAction {
        private ResetViewAction() {
            super("Reset View");
        }

        public void actionPerformed(ActionEvent e) {
            //resetViewTransform();
            centerAndZoom(true); //centerAndZoom relative to all
        }		
    }
    public void centerAndZoom(boolean relativeToAll) {
        Rectangle bbRect = getNetworkBB(relativeToAll);

        if (bbRect == null)
        	resetViewTransform();
        else {            
            double w = getSize().getWidth();
            double h = getSize().getHeight();
            double xRatio = (double)bbRect.width / w; //divided by networkview dimension width
            double yRatio = (double)bbRect.height / h; //divided by networkview dimension height
            double ratio = Math.max(xRatio, yRatio);
            
            if (ratio > 0){
            	double zoom = 1 / ratio;
                if (zoom > 1) //max zoom is 1
                	zoom = 1;
                setViewTransform(bbRect.x, bbRect.y, 1);
                zoom(zoom*0.95, w / 2 , h / 2);
            }
            else
            	setViewTransform(bbRect.x, bbRect.y, 1);
        }
	}

    private class BBFinder{
    	int num = 0;
    	//we gonna skip all points that are between 
    	public double minX=0,minY=0,maxX=0,maxY=0;
    	public void iterate(Iterator<Node> iterator){
    		while (iterator.hasNext()) {
	    		Node node = iterator.next();
	    		//System.out.println(num + ": node position:" + node.getPosition());
	    		nodebox.graphics.Point p = node.getPosition();
	    		double x = p.getX();
	    		double y = p.getY();
	    		
	    		if (num == 0){
	    			minX = x;
	    			maxX = x;
	    			minY = y;
	    			maxY = y;
	    		}
	    		else{
	        		if (x > maxX)
	        			maxX = x;
	        		else if (x < minX)
	        			minX = x;
	        		
	        		if (y > maxY)
	        			maxY = y;
	        		else if (y < minY)
	        			minY = y;
	    		}
	    		num++;
	    	}
    	}
    }

    private Rectangle getNetworkBB(boolean relativeToAll){
    	BBFinder bbf = new BBFinder(); 
    	if (relativeToAll){
    		ArrayList<NodeBoxDocument> docs = document.getAppFrame().getDocumentGroup();	    	
    		for (NodeBoxDocument doc : docs){
    			ImmutableList<Node> iList= doc.getActiveNetwork().getChildren();    	    	
    			Iterator<Node> iterator = iList.iterator();
    			bbf.iterate(iterator);
    		}
    	}
    	else{
        	ImmutableList<Node> iList= document.getActiveNetwork().getChildren();
        	Iterator<Node> iterator = iList.iterator();
        	bbf.iterate(iterator);
    	}
    	
    	if (bbf.num == 0)
    		return null;
    	else{
            int x = -((int) (bbf.maxX + bbf.minX)) * GRID_CELL_SIZE / 2;
            int y = -((int) (bbf.maxY + bbf.minY)) * GRID_CELL_SIZE / 2;
            
            //center the center point considering clipBoundRect 
            x += getSize().getWidth() / 2;
            y += getSize().getHeight() / 2;
            
            //center the centerpoint considering the width and height of nodeRect
            x += -NODE_WIDTH / 2;
            y += -NODE_HEIGHT / 2;
            
            int width = ((int) (bbf.maxX - bbf.minX)) * GRID_CELL_SIZE + NODE_WIDTH;
            int height = ((int) (bbf.maxY - bbf.minY )) * GRID_CELL_SIZE + NODE_HEIGHT;

    		return new Rectangle(x, y, width, height);
    	}
    }
  

    private class GoUpAction extends AbstractAction {
        private GoUpAction() {
            super("Go Up");
        }

        public void actionPerformed(ActionEvent e) {
            goUp();
        }
    }

    private class PublishAction extends AbstractAction {
        private NodePort nodePort;

        private PublishAction(NodePort nodePort) {
            super(getActiveNetwork().hasPublishedInput(nodePort.getNode().getName(), nodePort.getPort()) ? "Unpublish" : "Publish");
            this.nodePort = nodePort;
        }

        public void actionPerformed(ActionEvent e) {
            if (getActiveNetwork().hasPublishedInput(nodePort.getNode().getName(), nodePort.getPort())) {
                unpublish();
            } else {
                publish();
            }
        }

        private void unpublish() {
            Port port = getActiveNetwork().getPortByChildReference(nodePort.getNode().getName(), nodePort.getPort());
            getDocument().unpublish(port.getName());
        }

        private void publish() {
            String s = JOptionPane.showInputDialog(NetworkView.this, "Publish as:", nodePort.getPort());
            if (s == null || s.length() == 0)
                return;
            getDocument().publish(nodePort.getNode().getName(), nodePort.getPort(), s);
        }
    }

    private class GoToPortAction extends AbstractAction {
        private NodePort nodePort;

        private GoToPortAction(NodePort nodePort) {
            super("Go to Port");
            this.nodePort = nodePort;
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().setActiveNetwork(Node.path(getDocument().getActiveNetworkPath(), nodePort.getNode()));

            // todo: visually indicate the origin port.
            // Node node = findNodeWithName(nodePort.getNode());
            // Port publishedPort = node.getInput(nodePort.getPort());
            // publishedPort.getChildNodeName()
            // publishedPort.getChildPortName()
        }
    }

   /*private class CopyToMasterAction extends AbstractAction {
        private CopyToMasterAction() {
            super("Copy to Master");
        }

        public void actionPerformed(ActionEvent e) {
            Node node = getNodeAt(inverseViewTransformPoint(nodeMenuLocation));
            document.copyNodeToDocument(node, document.getAppFrame().getMasterDocument());
        }
    }*/

    private class SetRenderedAction extends AbstractAction {
        private SetRenderedAction() {
            super("Set Rendered");
        }

        public void actionPerformed(ActionEvent e) {
            Node node = getNodeAt(inverseViewTransformPoint(nodeMenuLocation));
            document.setRenderedNode(node, true);
        }
    }
    
    private class SetReplaceAction extends AbstractAction {
        private SetReplaceAction() {
            super("Replace Node");
        }

        public void actionPerformed(ActionEvent e) {
        	Node node = getNodeAt(inverseViewTransformPoint(nodeMenuLocation));
            if (node != null) {
            	getDocument().showNodeSelectionDialogReplace(node);
            }
        }
    }

    private class RenameAction extends AbstractAction {
        private RenameAction() {
            super("Rename");
        }

        public void actionPerformed(ActionEvent e) {
            Node node = getNodeAt(inverseViewTransformPoint(nodeMenuLocation));
            if (node != null) {
                renameNode(node);
            }
        }
    }

    private class DeleteAction extends AbstractAction {
        private DeleteAction() {
            super("Delete");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
        }

        public void actionPerformed(ActionEvent e) {
            deleteSelection();
        }
    }
    
    private class DeleteAndReconnectAction extends AbstractAction {
        private DeleteAndReconnectAction() {
            super("Delete & Reconnect");
            //putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
        }

        public void actionPerformed(ActionEvent e) {
        	//document.deleteAndReconnect();
        }
    }
    
    private class Merge extends AbstractAction {
        private Merge() {
            super("Merge");
            //putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
        }

        public void actionPerformed(ActionEvent e) {
        	document.merge();
        }
    }

    private class GoInAction extends AbstractAction {
        private GoInAction() {
            super("Edit Children");
        }

        public void actionPerformed(ActionEvent e) {
            Node node = getNodeAt(inverseViewTransformPoint(nodeMenuLocation));
            String childPath = Node.path(getDocument().getActiveNetworkPath(), node.getName());
            //getDocument().setActiveNetwork(childPath);
            document.setActiveNetworksEverywhere(childPath);
  
        }
    }

    private class GroupIntoNetworkAction extends AbstractAction {
        private Point gridPoint;

        private GroupIntoNetworkAction(Point gridPoint) {
            super("Group into Network");
            this.gridPoint = gridPoint;
        }

        public void actionPerformed(ActionEvent e) {
            nodebox.graphics.Point position;
            if (gridPoint == null)
                position = getNodeAt(inverseViewTransformPoint(nodeMenuLocation)).getPosition();
            else
                position = new nodebox.graphics.Point(gridPoint);
            getDocument().groupIntoNetwork(position);
        }
    }

    private class HelpAction extends AbstractAction {
        private HelpAction() {
            super("Help");
        }

        public void actionPerformed(ActionEvent e) {
            Node node = getNodeAt(inverseViewTransformPoint(nodeMenuLocation));
            Node prototype = node.getPrototype();
            for (NodeLibrary library : document.getNodeRepository().getLibraries()) {
                if (library.getRoot().hasChild(prototype)) {
                    String libraryName = library.getName();
                    String nodeName = prototype.getName();
                    String nodeRef = String.format("http://nodebox.net/node/reference/%s/%s", libraryName, nodeName);
                    Platform.openURL(nodeRef);
                    return;
                }
            }
            JOptionPane.showMessageDialog(NetworkView.this, "There is no reference documentation for node " + prototype, Application.NAME, JOptionPane.WARNING_MESSAGE);
        }
    }
}
