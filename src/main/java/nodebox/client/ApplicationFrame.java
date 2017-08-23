package nodebox.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.Border;

import nodebox.ui.AddressBar;
import nodebox.ui.Theme;
import nodebox.ui.Zoom;


//The Application Frame should contain the document file (panel?)
public class ApplicationFrame extends JFrame implements WindowListener{
	public static final int MAX_NO_ALT_PER_ROW = 3;
	public static final int DEFAULT_NO_ALTERNATIVES_PER_MONITOR =  1; //default
	
	private static final Insets insets = new Insets(0,0,0,2);
	public Set<String> sameUnchangedNodesNames = new HashSet<String>();
	
	private NodeBoxDocument currentDocument;
	private NodeBoxDocument referenceDocument = null;
	private ArrayList<NodeBoxDocument> documentGroup;
	private List<Zoom> zoomListeners = new ArrayList<Zoom>();
	private GlassPane glassPane;
	
	public JPanel monitorPanel;
	public JPanel[] alternativesPanel;
	public boolean[] jamMonitors = {false, false, false, false, false, false}; //max 6 monitors
	public HistoryDialog activeHistoryDialog = null;
	public ArrayList<ArrayList<NodeBoxDocument>> monitorDocumentMap;
	
    public NodeSelectionDialog nodeSelectionDialog = null;

    //globalUndoStack keeps track of synchronization of undo between different alternatives
    private Stack<Boolean[]> globalUndoStack = new Stack<Boolean[]>();
    private Stack<Boolean[]> globalRedoStack = new Stack<Boolean[]>();

    //monitor arrangement 
    private int numRows = -1;
    private int numCols = -1;
    private int numAlternatives = -1;
    private int monitorArrangement = -1;
    
    // current workspace for this application window
    private String workspacePath = null;
    
    //selectively drawing connection lines
    nodebox.node.Node singleNodeSelectedSomwhere = null;
    
    //public static final Border jamBorder = BorderFactory.createMatteBorder(Theme.jamBorderSize, Theme.jamBorderSize, Theme.jamBorderSize, Theme.jamBorderSize, Color.RED);

    //logging data

    private Logger logger;
    private FileHandler fh;
    public final static char propertyDelimiter = ';';
    public final static char attributeDelimiter = '=';
    public final static char itemDelimiter = ',';

	private ArrayList<NodeBoxDocument> minimizedDocuments = new ArrayList<NodeBoxDocument>();
	
	private NodeBoxDocument focusedDocument = null;

    public void focusAlternative(NodeBoxDocument doc){
    	if (focusedDocument == doc){ //unfocus if it was previously focused
    		focusedDocument = null;
	    	updateAlternativesPanels(true);
	    	NodeBoxDocument.centerAndZoomAllZoomableViews(doc, true);
	    	log(doc.getDocumentName(), "focus", "off");
    	}
    	else{
	    	focusedDocument = doc;
	    	log(doc.getDocumentName(), "focus", "on");
	    	updateAlternativesPanels(true);
	    	//find first alternative that isn't focusedDocument and reset all views relative to it
	    	NodeBoxDocument unfocusedDoc = null;
	    	for (NodeBoxDocument d: documentGroup){
	    		if (d != doc){
	    			unfocusedDoc = d;
	    			break;
	    		}
	    	}
	    	if (unfocusedDoc != null) //if found an unfocused doc
	    		NodeBoxDocument.centerAndZoomAllZoomableViews(unfocusedDoc, true);
    	}
    }
	public ArrayList<NodeBoxDocument> getMinimizedDocuments(){
		return minimizedDocuments;
	}
    public ArrayList<NodeBoxDocument> getLockedDocuments(){
    	ArrayList<NodeBoxDocument> lockedDocuments = new ArrayList<NodeBoxDocument>();    	
    	for (NodeBoxDocument doc : documentGroup){
    		if (!doc.getAlternativePaneHeader().isEditable()){
    			lockedDocuments.add(doc);
    		}
    	}
    	return lockedDocuments;
    }

    public GlassPane getGlassPane(){ //used to repaint from the network
    	return glassPane;
    }
    public int getNumCols(){
    	return numCols;
    }
    
    public int getNumRows(){
    	return numRows;
    }

    public Boolean[] createNewGlobalUndoEntry(){
    	return new Boolean[documentGroup.size()];
    }
    
    public Boolean[] createNewGlobalUndoEntry(int size){
    	return new Boolean[size];
    }
    
    public void addGlobalUndoEntry(Boolean[] undoEntry){
    	globalUndoStack.push(undoEntry);
    	//reset Global Redo Stack
    	globalRedoStack = new Stack<Boolean[]>();
    	
    	for (int i = 0; i < documentGroup.size(); i++){
    		documentGroup.get(i).menuBar.updateGlobalUndoRedoState();
    	}
    	
    }
    public void printGlobalUndoStack(){
		System.out.println("-------------GLOBAL UNDO STACK-------------");
    	int counter = 0;
    	for (Boolean[] undoEntry : globalUndoStack){
    		System.out.print(counter + ": ");
    		for (int i = 0; i < undoEntry.length; i++){
    			System.out.print(undoEntry[i] + (i == documentGroup.size() - 1 ? "" : ","));
    		}
    		System.out.println();
    		counter++;
    	}
    	System.out.println("-------------------------------------------");
    		
    }
    int getDocNumByDocPointer(NodeBoxDocument doc){
    	for (int i = 0; i < documentGroup.size(); i++){
    		if (documentGroup.get(i) == doc)
    			return i;
    	}
    	return -1;
    }
    public boolean canGloballyUndo(){
    	return (globalUndoStack.size() > 0);
    }
    
    public boolean canGloballyRedo(){
    	return (globalRedoStack.size() > 0);
    }
    public void globallyUndo(){
    	Boolean[] undoEntry = globalUndoStack.pop();
    	globalRedoStack.push(undoEntry);
    	Set<String> affectedAlternatives = new HashSet<String>();
    	
    	for (int i = 0; i < documentGroup.size(); i++){
    		boolean undoable = undoEntry[i];
    		if (undoable){
				NodeBoxDocument doc = documentGroup.get(i);
				doc.undo();
		        affectedAlternatives.add(doc.getDocumentName());
    		}
    	}
        log(affectedAlternatives, "Global Undo");

    }
    
	public void globallyRedo(){
	   	Boolean[] redoEntry = globalRedoStack.pop();
		globalUndoStack.push(redoEntry);
    	Set<String> affectedAlternatives = new HashSet<String>();

		for (int i = 0; i < documentGroup.size(); i++){
			boolean redoable = redoEntry[i];
			if (redoable){
				NodeBoxDocument doc = documentGroup.get(i);
				doc.redo();
		        affectedAlternatives.add(doc.getDocumentName());
			}
		}
        log(affectedAlternatives, "Global Redo");

	}
	//everytime local undo or local redo is called this function gets called
	public void resetGlobalUndoRedo(){
		globalUndoStack = new Stack<Boolean[]>();
		globalRedoStack = new Stack<Boolean[]>();
		
		//update all global undo and redo menus in all documents so that the option gets disabled in the drop menus
		for (NodeBoxDocument doc : documentGroup){
			doc.menuBar.globalUndoAction.update();
			doc.menuBar.globalRedoAction.update();
		}

	}

	//this one creates an empty frame
	public ApplicationFrame(int monitorArrangement, boolean createDocuments)
	{
		//first, initialize logger
        initializeLogger();  
        
		this.monitorArrangement = monitorArrangement;
		
		// Zoom in / out shortcuts.
        KeyStroke zoomInStroke1 = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() + InputEvent.SHIFT_MASK);
        KeyStroke zoomInStroke2 = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        KeyStroke zoomInStroke3 = KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        ActionListener zoomInHandler = new ZoomInHandler();
        getRootPane().registerKeyboardAction(zoomInHandler, zoomInStroke1, JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(zoomInHandler, zoomInStroke2, JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(zoomInHandler, zoomInStroke3, JComponent.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke zoomOutStroke = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        getRootPane().registerKeyboardAction(new ZoomOutHandler(), zoomOutStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationByPlatform(true);
        
        java.awt.Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize(); //get dimension of the screen
    	
        int width = screenSize.width;
    	int height = screenSize.height;
        setSize(width, height);
        
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        //this will have to be changed later
        addWindowListener(this);

    	//create document for the remaining alternatives
        documentGroup = new ArrayList<NodeBoxDocument>();

        buildMonitorArray(createDocuments);
        buildReferenceMenus();

        glassPane = new GlassPane(this);
        setGlassPane(glassPane);
        glassPane.setVisible(true);

        // by default we set the first one active if possible
        if (createDocuments)
        	setCurrentDocument(documentGroup.get(0));

        
        //global keylistener for all components of all windows (we will need to change this in the future, if we want to make it stable accross one window and not all nodebox windows, currently it's accross all windows)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
            	KeyEvent ke = (KeyEvent) e;
            	int modifiersEx = e.getModifiersEx();
    	        if (ke.getID() == KeyEvent.KEY_RELEASED ){
    	        	//if Tab is pressed, then switch active alternative
    	        	if (ke.getKeyCode() == KeyEvent.VK_TAB && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	        		setNextDocument();
    	        	}
    	        	//toggle synched for alts
    	        	/*else if (ke.getKeyCode() == KeyEvent.VK_1 && modifiersEx == KeyEvent.ALT_DOWN_MASK) //512 is the option(alt) button on mac
    	        	{
    	        		boolean res = documentGroup.get(0).getAlternativePaneHeader().toggleEditableState();
    	        		documentGroup.get(0).menuBar.setEditableChecked(res);
    	        		disableAllSandboxButtons();
    	        		
    	        	}
    	        	else if (ke.getKeyCode() == KeyEvent.VK_2 && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	        		boolean res = documentGroup.get(1).getAlternativePaneHeader().toggleEditableState();
    	        		documentGroup.get(0).menuBar.setEditableChecked(res);
    	        		disableAllSandboxButtons();

    	        	}
    	        	else if (ke.getKeyCode() == KeyEvent.VK_3 && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	        		boolean res = documentGroup.get(2).getAlternativePaneHeader().toggleEditableState();
    	        		documentGroup.get(0).menuBar.setEditableChecked(res);
    	        		disableAllSandboxButtons();
    	        	}
    	        	//toggle switch master for alts
    	        	else if (ke.getKeyCode() == KeyEvent.VK_T && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	    			int docNum = getDocNumByDocPointer(documentGroup.get(0));
    	        		switchReference(docNum);
    	        	}
    	        	else if (ke.getKeyCode() == KeyEvent.VK_Y && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	    			int docNum = getDocNumByDocPointer(documentGroup.get(1));
    	    			switchReference(docNum);

    	        	}
    	        	else if (ke.getKeyCode() == KeyEvent.VK_U && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	    			int docNum = getDocNumByDocPointer(documentGroup.get(2));
    	    			switchReference(docNum);
    	        	}*/
    	        	else if (ke.getKeyCode() == KeyEvent.VK_R && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	    			switchRefencetoCurrDocument();

    	        	}
    	        	//always sandboxes 
    	        	else if (ke.getKeyCode() == KeyEvent.VK_A && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	        		sandboxSetEnabled(true);	
    	        	}
    	        	//if sandboxed, then unsandbox, otherwise sandbox
    	        	else if (ke.getKeyCode() == KeyEvent.VK_S && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
    	        		toggleSandbox();
    	        	}

    	        	else if (ke.getKeyCode() == KeyEvent.VK_M && modifiersEx == KeyEvent.ALT_DOWN_MASK)
    	        	{
	        			boolean newState = currentDocument.getAlternativePaneHeader().toggleSynchedNodePositionState();
        				currentDocument.menuBar.setSynchedMoveChecked(newState);	        		
    	        	}
    	        }
    	        return false;
            }

        });
       /* final WorkspaceManager autoSaveWorkspaceManager = new WorkspaceManager(this);
        Thread thread = new Thread(){
            public void run(){
                while (true){
                    try{
                        Thread.sleep(30000); // 30 seconds
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    finally{
                        System.out.println("Autosaving...");
                        autoSaveWorkspaceManager.autoSave();
                    }
                }
            }
        };
        thread.start();*/
  	}
	/**
	 * Initializes the logger. One unique logger per application frame
	 */
	private void initializeLogger() {
		//initialize logger
        //using this format you can save file with date time as part of file name (because it doesn't have forbidden characters)
	    SimpleDateFormat ft = new SimpleDateFormat ("E yyyy.MM.dd 'at' hh-mm-ss a zzz");
    	Date date = new Date();
    	
    	String properDate = ft.format(date);
    	
    	//title the logger with the date as name to avoid confusion later if another workspace is created
	    logger = Logger.getLogger(properDate);
	    try {  
	        // This block configure the logger with handler and formatter	    	
		    fh = new FileHandler(this.hashCode() + "." + ft.format(date) + ".log");  
	        logger.addHandler(fh);
	        SimpleFormatter formatter = new SimpleFormatter();  
	        fh.setFormatter(formatter);	        
	    } catch (SecurityException e) {  
	        e.printStackTrace();  
	    } catch (IOException e) {  
	        e.printStackTrace();  
	    }
	    
	    log("ApplicationFrame","initliazied");
	}
	/**
	 * Logs a message as INFO
	 * @param msg
	 */
	public void log(String... msgs){
		String finalMsg = "";

		//append all strings in arguments
		for (String msg : msgs){
			finalMsg += msg + propertyDelimiter;
		}
		//remove last delimiter character
		finalMsg = finalMsg.substring(0, finalMsg.length() - 1);
		//log
		logger.info(finalMsg);
	}
	/**
	 * Logging of affected alternatives, their number and their list
	 * @param command
	 * @param affectedAlternatives
	 */
	public void log(Set<String> affectedAlternatives, String... msgs) {
		//process the message
		String finalMsg = "";
		for (String msg : msgs){
			finalMsg += msg + propertyDelimiter;

		}

		//process the alternatives		
		String strAlternatives = "alternatives" + attributeDelimiter;
		for (String alternative : affectedAlternatives){
			strAlternatives += alternative + itemDelimiter;

		}
		Integer num = affectedAlternatives.size();
		
		//remove last delimiter character
		strAlternatives = strAlternatives.substring(0, strAlternatives.length() - 1);

		finalMsg += strAlternatives;
		finalMsg = "affected alternatives" + attributeDelimiter + num.toString() + propertyDelimiter +  finalMsg;
		
		//log
		logger.info(finalMsg);
	}
	public void switchRefencetoCurrDocument() {
		int docNum = getDocNumByDocPointer(currentDocument);
		switchReference(docNum);
	}

	/**
	 * 	if sandboxed, then unsandbox, otherwise sandbox
	 */
	boolean toggleSandbox() {
		boolean enabled = currentDocument.getAlternativePaneHeader().isSandboxed();
		sandboxSetEnabled(!enabled);
		return !enabled;
}
		
	public void switchReference(int docNum) {
		NodeBoxDocument document = documentGroup.get(docNum);
		document.isReference = !document.isReference;
		document.getAlternativePaneHeader().setReferenceToggleButtonSelected(document.isReference);
		
		//make changes in the menuBars
		int docNo = getDocNumByDocPointer(document);
		setSpecificMenuSelectedDeselectingAllOthersInMenuBars(docNo,document.isReference);

		// set Reference Document here
		if (document.isReference == true) // disable all others
		{
			log("Reference", document.getDocumentName(), String.valueOf(document.getDocumentNo()));
			setReferenceDocument(document);
			
			for (int i = 0; i < documentGroup.size(); i++) {
				NodeBoxDocument doc = documentGroup.get(i);
				
				if (document != doc) { //if this is not reference
					//doc.getAlternativePaneHeader().diffToggle.setVisible(true); 	//must be visible, but not necessarily selected
					doc.getAlternativePaneHeader().setDiffToggleVisible(true); //must be visible, but not necessarily selected
					doc.menuBar.setDiffVisible(true);				//must be visible, but not necessarily selected
					
					doc.isReference = !document.isReference;
					//doc.getAlternativePaneHeader().getReferenceToggle().setSelected(!d.isReference);
					doc.getAlternativePaneHeader().setReferenceToggleButtonSelected(!document.isReference);
				}
				else { //if this is reference
					//doc.getAlternativePaneHeader().setDiffToggleVisible(false); 	//must be not visible but enabled
					//doc.getAlternativePaneHeader().diffToggle.setVisible(false);	//must be not visible but enabled
					doc.menuBar.setDiffVisible(false);								//must be not visible but enabled
					//doc.getAlternativePaneHeader().diffToggle.setSelected(true); 	//must be selected
					doc.menuBar.setDiffChecked(true);								//must be selected
				}
				doc.getAlternativePaneHeader().setDiffToggleSelected(true); 	//must be selected

				// this is to reflect the changes in the presentation of
				// differences between properties in the portView
				doc.getNetworkView().resetReferenceViewVisualizationData(); //reset dragged nodes
				doc.getNetworkView().updateAll();
				doc.getPortView().updateAll();
				// doc.getViewerPane().updateHandle();
				doc.getViewerPane().updateUI();

			}
		} else {
			log("Reference", "null", "null");
			setReferenceDocument(null);
			updateAllPortViews(); 	//this is to un-highlight all
													// ports in all views after
													// deselecting master
			for (NodeBoxDocument doc : documentGroup) {
				//doc.getAlternativePaneHeader().diffToggle.setVisible(false);
				doc.getAlternativePaneHeader().setDiffToggleVisible(false);
				doc.menuBar.setDiffVisible(false);
				doc.getNetworkView().resetReferenceViewVisualizationData(); //reset dragged nodes
				doc.getNetworkView().updateAll();
				doc.getPortView().updateAll();
				doc.getViewerPane().updateUI();
			}
		}
		
		//makes sure if you switch reference and switch current doc same time, sandboxing is disabled
		//this fixes the previous bug when the sandbox would get stuck in true mode until you select another document, 
		//and select this document again
		if(Application.ENABLE_AUTOSANDBOX && document == currentDocument)
			sandboxSetEnabled(false);
	
		repaintGlassPane();
	}
	
	//for alternatives
	public  void updateAllPortViews()
	{    
        for (int i = 0; i < documentGroup.size(); i++){
        	documentGroup.get(i).getPortView().updateAll();
        }
	}
	/**
	 * There is still problem, it would select uneditable alternatives... should fix it at some point 
	 */
	public void setNextDocument() {
		//try next alternative, if doesnt exist
		//try next monitor, if last monitor try first monitor (hence the modular devision)
		//we try next monitor in a while loop, rather than switching the next monitor, because some monitors may not have any documents
		//if nothing works, set to null
		int k=0,l=0;
		for (int i = 0; i < monitorDocumentMap.size(); i++){
			boolean found = false;
			for (int j = 0; j < monitorDocumentMap.get(i).size(); j++)
				if (monitorDocumentMap.get(i).get(j) == currentDocument){
					k = i;
					l = j;
					found = true;
					break;
				}
			if (found)
				break;
		}
		try{
			NodeBoxDocument doc = monitorDocumentMap.get(k).get(l+1);
			setCurrentDocument(doc);
		}
		catch (Exception e1){
			int numMonitors = numRows*numCols;
			//try until you find a monitor that isnt empty or until you go through all the monitors, then set 
			//the current alternative to null
			int currMonitorNo = k;
			while (currMonitorNo < numMonitors){
				try{
					NodeBoxDocument doc = monitorDocumentMap.get((currMonitorNo + 1) % monitorDocumentMap.size()).get(0);
					setCurrentDocument(doc);
					return;
				} catch (Exception e){
					currMonitorNo++;
				}
			}
			//if nothing else works set it to null
			//setCurrentDocument(null);
		}
	}
	public void sandboxSetEnabled(boolean set) {
		for (NodeBoxDocument doc : documentGroup){
				doc.getAlternativePaneHeader().setEditableState(set ? doc == currentDocument : true);
				doc.menuBar.setEditableChecked(set ? doc == currentDocument : true);
				doc.getAlternativePaneHeader().setSandboxButtonToggleEnable(set ? doc == currentDocument : false); //false because we want to disable all others
				doc.menuBar.setSandboxChecked(set ? doc == currentDocument : false); //false because we want to disable to all others
		}
	}

	public void disableAllSandboxButtons() {
		//if unlocked is pressed, then set sandbox button state to false everywhere
         //and this one is only called from the menu and by pressing the button on the panAlt
         for (NodeBoxDocument doc : documentGroup){
         	doc.getAlternativePaneHeader().setSandboxButtonToggleEnable(false);
         }
	}
	
	//alternative was dragged, enforce it's state relative to monitor state
	public void enforceAlternativeState(NodeBoxDocument document){
		int monitorNo = document.getMonitorNo();
		//set jam state same as other alternatives in this monitor
		ArrayList<NodeBoxDocument> monitorDocs = monitorDocumentMap.get(monitorNo);
		if (monitorDocs.size() > 1){ //if there are other documents in this monitor, then jam to the first one which isn't itself
			for (NodeBoxDocument doc : monitorDocs){
				if (doc != document){
					//System.out.println("jamming!");
					if (doc.menuBar.getMonitorJamChecked()) //jam only if there is jamming going on already
						doc.menuBar.jamAndHardEnforce();
					else
						 document.menuBar.setMonitorJamChecked(false);	//otherwise disable jamming on this newly dragged document
					
					return; //break out of loop and get out
				}
			}
		}
		else if (monitorDocs.size() == 1){
			jamMonitor(false); //if there is only one then unjam if there is something to unjam
		}
		//System.out.println("wow!");
		//System.out.println("monitorDocumentMap:" + monitorDocs.size() + " : " + monitorDocs);
	}
	public void jamMonitor(boolean checked) { //for all documents in the monitor
		int monitorNo = currentDocument.getMonitorNo();
		//draw boundaries of monitors if they are jamming 
		sethHighlightMonitor(checked, monitorNo);
		//alternativesPanel[monitorNo].setBorder(checked ? jamBorder : null);
		ArrayList<NodeBoxDocument> currentMonitorDocuments = monitorDocumentMap.get(monitorNo);
		for (NodeBoxDocument doc : currentMonitorDocuments){
				doc.menuBar.setMonitorJamChecked(checked);
				doc.menuBar.setMonitorEditableEnabled(checked);
				doc.menuBar.setMonitorNetworkViewDiffEnabled(checked);
		}
		glassPane.repaint(); //if some docs were lock or unlocked in the process then glasspane has to be redrawn to reflect that

	}
	
	public void sethHighlightMonitor(boolean checked, int monitorNo){
		//alternativesPanel[monitorNo].setBorder(checked ? jamBorder : null);
		jamMonitors[monitorNo] = checked; //keeps track of which monitors are highlighted
	}
	
	public void makeEditableMonitor(boolean checked) { //for all documents in the monitor
		int monitorNo = currentDocument.getMonitorNo();
		ArrayList<NodeBoxDocument> currentMonitorDocuments = monitorDocumentMap.get(monitorNo);
		for (NodeBoxDocument doc : currentMonitorDocuments){
				doc.menuBar.setMonitorEditableSelected(checked);
				doc.menuBar.setEditableChecked(checked);
				doc.getAlternativePaneHeader().setEditableState(checked);
				//System.out.println(doc.getDocumentName() + " is set to " + checked);
		}
		glassPane.repaint(); //if some docs were lock or unlocked in the process then glasspane has to be redrawn to reflect that

	}
	
	public void setNetworkDiffMonitor(boolean checked) { //for all documents in the monitor
		int monitorNo = currentDocument.getMonitorNo();
		ArrayList<NodeBoxDocument> currentMonitorDocuments = monitorDocumentMap.get(monitorNo);
		for (NodeBoxDocument doc : currentMonitorDocuments){
				doc.menuBar.setMonitorNetworkViewDiffSelected(checked);
				doc.menuBar.setDiffChecked(checked);
				doc.getAlternativePaneHeader().setDiffButtonToggleSelected(checked);
		}
	}


	public void buildMonitorArray(boolean createDocuments) {
		
		// calculate rows and columns
		numRows = 1 + monitorArrangement / MAX_NO_ALT_PER_ROW;
        numCols = monitorArrangement % MAX_NO_ALT_PER_ROW + 1;
        numAlternatives = DEFAULT_NO_ALTERNATIVES_PER_MONITOR * (monitorArrangement / MAX_NO_ALT_PER_ROW == 0 ? 1 + monitorArrangement : 2 * (1 + monitorArrangement % MAX_NO_ALT_PER_ROW));
    	int monitors = numCols * numRows;

        monitorPanel = new JPanel(new GridLayout(numRows, numCols, 1, 1));
        monitorDocumentMap = new ArrayList<ArrayList<NodeBoxDocument>>();

        for (int i = 0; i < monitors; i++)
        	monitorDocumentMap.add(new ArrayList<NodeBoxDocument>());

        if (createDocuments) {
	        // only create alternatives in initialize state
	        if (documentGroup.size() <= 0) {
		        for (int i = 0 ; i < numAlternatives; i++)
		        	// create a new document in each monitor
		        	createNewDocument(i/DEFAULT_NO_ALTERNATIVES_PER_MONITOR);
	        }
	        else {
	        	// at this point we already created the monitor array with any number of
	        	// alternatives and we have to rebuild the array. If we increase the size
	        	// of the array we have nothing to do, but if we decrease the size we want
	        	// to distribute the alternatives equally over all monitors
	        	final int alternativesPerMonitor = (documentGroup.size() > monitors ? (int)Math.floor(documentGroup.size() / monitors) : 1);
	        	int currentMonitor = 1;
	        	for (int i = 1; i <= documentGroup.size(); i++) {
	        		NodeBoxDocument document = documentGroup.get(i - 1);
	        		document.setMonitorNum(currentMonitor - 1);
	        		monitorDocumentMap.get(document.getMonitorNo()).add(document);
	        		if (i % alternativesPerMonitor == 0 && currentMonitor < monitors)
	        			currentMonitor++;
	        	}
	        }
        }

        setContentPane(monitorPanel);
        updateAlternativesPanels(true);
	}
	/**
	 * Redraws alternative panels using either GridLayout or GridBagLayout
	 * during dragging of alternatives GridLayout is used. 
	 * This is to avoid the bug in Christian's code which currently relies on GridLayout rather GridBagLayout.
	 * Once dragging is over, the layout is switched back to GridBagLayout so that focusing can be used (the new feature).
	 * @param clonedDocument
	 */
	public void updateAlternativesPanels(final boolean useGridBagLayout) {
		// used to calculate a relative image size
		int panelHeight = 0;
		if (monitorPanel.getComponentCount() >= 1)
			panelHeight = monitorPanel.getComponent(0).getHeight();
		
		// set dummy height if detection fails
		if (panelHeight <= 0)
			panelHeight = 768;

		// remove all alternatives and add them again (optimization: detect changed panels and update only these)
		// in the initialize state we have no components
		monitorPanel.removeAll();

		// create alternative panel
		alternativesPanel = new JPanel[monitorDocumentMap.size()];
		for (int i = 0; i < monitorDocumentMap.size(); i++) {
			if (useGridBagLayout)
				alternativesPanel[i] = new JPanel(new GridBagLayout());
			else
				alternativesPanel[i] = new JPanel(new GridLayout(1, monitorDocumentMap.get(i).size(), 1, 1));
						
			alternativesPanel[i].setName(String.valueOf(i)); // name represents the monitor number
			monitorPanel.add(alternativesPanel[i]);

			// if we have a empty monitor show a plus sign to add a new document inside
			if (monitorDocumentMap.get(i).size() <= 0) {
				alternativesPanel[i].setBorder(BorderFactory.createRaisedBevelBorder());
				alternativesPanel[i].addMouseListener(new MouseListener() {
					@Override
					public void mouseReleased(MouseEvent e) {}
					@Override
					public void mousePressed(MouseEvent e) {}
					@Override
					public void mouseExited(MouseEvent e) {}
					@Override
					public void mouseEntered(MouseEvent e) {}
					@Override
					public void mouseClicked(MouseEvent e) {
						JPanel monitor = (JPanel)e.getSource();
						NodeBoxDocument newDoc = createNewDocument(Integer.valueOf(monitor.getName()));
			        	log("New alternative", newDoc.getDocumentName(),String.valueOf(newDoc.getDocumentNo()), "mouseClicked");
						updateAlternativesPanels(useGridBagLayout);
						resetGlobals();
					}
				});
				
				BufferedImage image = null;
				try {
					JPanel alternativeDummy = new JPanel(new BorderLayout());
					image = ImageIO.read(AddressBar.class.getResourceAsStream("/add-black.png"));
					alternativeDummy.add(new JLabel(new ImageIcon(image.getScaledInstance(panelHeight / 7, panelHeight / 7, Image.SCALE_SMOOTH))));
					alternativeDummy.setName("0"); // name respresents alternative number
					
					GridBagConstraints c = new GridBagConstraints();
					c.weightx = 1;
					c.weighty = 1;
					c.fill = GridBagConstraints.BOTH;
					//c.gridwidth = 1;
					c.gridx = 0;
					c.gridy = 0;
					if (useGridBagLayout)
						alternativesPanel[i].add(alternativeDummy, c);
					else
						alternativesPanel[i].add(alternativeDummy, BorderLayout.CENTER);
				} catch (IOException e) {}
				continue; // stop here and do next monitor
			}
			
			GridBagConstraints c = new GridBagConstraints();
			// use the name later for detecting the active monitor
			for (int j = 0; j < monitorDocumentMap.get(i).size(); j++) {
				monitorDocumentMap.get(i).get(j).buildRootPanel();
				// use the name later for detecting the active alternative
				monitorDocumentMap.get(i).get(j).getRootPanel().setName(String.valueOf(j));
				
				
				c.weighty = 1;
				c.fill = GridBagConstraints.BOTH;
				if (focusedDocument == monitorDocumentMap.get(i).get(j)){
					int numAlt = monitorDocumentMap.get(i).size();
					c.weightx = ((double) numAlt) / 60 * 100; // focused alternative takes 60% of the width
				}
				else
					c.weightx = 1;
				c.insets = insets;
				c.gridx = j;
				c.gridy = 0;
				if (useGridBagLayout)
					alternativesPanel[i].add(monitorDocumentMap.get(i).get(j).getRootPanel(),c);
				else
					alternativesPanel[i].add(monitorDocumentMap.get(i).get(j).getRootPanel());
			}
		}

		//set checked border back
		for (int i = 0; i < monitorDocumentMap.size(); i++) {
			sethHighlightMonitor(jamMonitors[i], i);
		}
		monitorPanel.updateUI();
	}
	
	//build reference menus in all menubars in all alternatives
    public  void buildReferenceMenus() {
    	int numAlt = documentGroup.size();
        for (int i = 0; i < numAlt; i++) {
        	NodeBoxMenuBar menuBar = documentGroup.get(i).menuBar;
        	JMenu menu = menuBar.referenceSubmenu;
        	menu.removeAll();
        	for (int j = 0; j < numAlt; j++) {
            	NodeBoxDocument doc = documentGroup.get(j);
        		menuBar.addReferenceMenu(j, doc.getDocumentName());
          	}
        	
        	/*System.out.println("doc:"+documents.get(i).getDocumentName());
        	for (int k = 0; k < menu.getItemCount(); k++)
        	{
        		System.out.println("item: " +  k + " " + menu.getItem(k).getLabel());
        	}*/
        }
	}
    
	public void minimizeAlternative(NodeBoxDocument doc){
		removeDocumentFromMap(doc);
		doc.ungroup();
		minimizedDocuments.add(doc);
		updateAlternativesPanels(true);
		log("Minimize Alternative", doc.getDocumentName(), String.valueOf(doc.getDocumentNo()));
	}
    public NodeBoxDocument createNewDocument(int monitorNum) {
		return addNewDocument(monitorNum, new NodeBoxDocument(this));
    }
    
    public NodeBoxDocument addNewDocument(int monitorNum, NodeBoxDocument document) {
		document.setMonitorNum(monitorNum);
		document.setDocumentGroup(documentGroup);
		monitorDocumentMap.get(monitorNum).add(document);
		return document;
    }
    
    public void setSpecificMenuSelectedDeselectingAllOthersInMenuBars(int docNo, boolean checked){ //docNo corresponds to docNo/referenceMenu itemNum, checked is desired select state
    	int numAlt = documentGroup.size();
        for (int i = 0; i < numAlt; i++) {
        	NodeBoxMenuBar menuBar = documentGroup.get(i).menuBar;
        	JMenu menu = menuBar.referenceSubmenu;
        	for (int j = 0; j < numAlt; j++) {
        		JMenuItem item = menu.getItem(j);
        		if (j == docNo){
        			item.setSelected(checked);
        		}
        		else
        			item.setSelected(false);
        	    }
        	}
        
    }
	public 	void repaintGlassPane(){
		if (glassPane != null)
			glassPane.repaint();
	}
	
	public int getMonitorForDocument(NodeBoxDocument doc)  {
		for (int i = 0; i < monitorDocumentMap.size(); i++)
			for (int j = 0; j < monitorDocumentMap.get(i).size(); j++)
				if (monitorDocumentMap.get(i).get(j) == doc)
					return i;
		System.err.println("Current Document not found!");
		return 0;
	}
	
	public int getNumAltForDocumentInMonitor(NodeBoxDocument doc){
		int monitorNo = getMonitorForDocument(doc);
		return monitorDocumentMap.get(monitorNo).size();
	}

	public int getAlternativeNumberRelativeToMonitor(NodeBoxDocument doc){
		int monitorNo = getMonitorForDocument(doc);
		for (int i = 0; i < monitorDocumentMap.get(monitorNo).size(); i++)
			if (monitorDocumentMap.get(monitorNo).get(i) == doc)
				return i;
		System.err.println("Current Document not found!");
		return 0;
	}
	
	public int getCurrentMonitor()  {
		return getMonitorForDocument(currentDocument);
	}
	
	public int getRowForDocument(NodeBoxDocument doc){
		int monitorNo = getMonitorForDocument(doc);
		int rowNo = monitorNo / numCols;
		return rowNo;
	}

	public int getColForDocument(NodeBoxDocument doc){
		int monitorNo = getMonitorForDocument(doc);
		int colNo = monitorNo % numCols;
		return colNo;
	}

	public boolean removeDocumentFromMap(NodeBoxDocument document) {
		boolean result = monitorDocumentMap.get(getMonitorForDocument(document)).remove(document);
		updateAlternativesPanels(true);
		return result;
	}

	public NodeBoxDocument createNewAlternativeFromCurrentDoc() {
		NodeBoxDocument doc = createNewAlternativeFromDocument(currentDocument);
		return doc;
	}

	/**
	 * log alternative cloning
	 * @param clonedDocument
	 */
	public void logClone(NodeBoxDocument clonedDocument) {
		String logSrc = "source" + attributeDelimiter + currentDocument.getDocumentName() + itemDelimiter + currentDocument.getDocumentNo();
		String logDest = "destination" + attributeDelimiter + clonedDocument.getDocumentName() + itemDelimiter + clonedDocument.getDocumentNo();
		log("Clone alternative",logSrc, logDest);
	}

	/**
	 * Creates a new alternative for a given document on the monitor from the given document
	 * at the end (right).
	 * 
	 * @param given document
	 * @return new document created from given document
	 */
	public NodeBoxDocument createNewAlternativeFromDocument(NodeBoxDocument document) {
		return createNewAlternativeFromDocument(document, -1);
	}

	/**
	 * Creates a new alternative for a given document on the given monitor from the given
	 * document at the end (right).
	 * 
	 * @param given document
	 * @param monitor number (if < 0 use the monitor from the given document)
	 * @return new document created from given document
	 */
	public NodeBoxDocument createNewAlternativeFromDocument(NodeBoxDocument document, int monitorNo) {
		return createNewAlternativeFromDocument(document, monitorNo, -1);
	}

	/**
	 * Creates a new alternative from a given document.
	 * 
	 * @param given document
	 * @param monitor number (if < 0 use the monitor from the given document)
	 * @param position (if < 0 append new alternative at the end)
	 * @return new document created from given document 
	 */
	public NodeBoxDocument createNewAlternativeFromDocument(NodeBoxDocument document, int monitorNo, int pos) {

		// try to find the right monitor for the document
		if (monitorNo < 0)
			monitorNo = getMonitorForDocument(document);

    	// negative position means appending new alternative at the end
    	if (pos < 0 || pos > monitorDocumentMap.get(monitorNo).size())
    		pos = monitorDocumentMap.get(monitorNo).size();
    	
		// create copy from given document
    	NodeBoxDocument newDoc = NodeBoxDocument.newInstance(document);
    	monitorDocumentMap.get(monitorNo).add(pos, newDoc);
    	newDoc.setMonitorNum(monitorNo);
    	newDoc.setActiveNetwork(document.getActiveNetworkPath());
    	
    	updateAlternativesPanels(true);
    	resetGlobals();

		NodeBoxDocument.centerAndZoomAllZoomableViews(document, true);

    	
    	return newDoc;
	}
	public void resetGlobals() {
		//clean up code
    	//rebuild referenceMenus
        resetGlobalUndoRedo();
        buildReferenceMenus();
	}

	public NodeBoxDocument getReferenceDocument()
	{
		return referenceDocument;
	}
	public void setReferenceDocument(NodeBoxDocument referenceDocument)
	{
		this.referenceDocument = referenceDocument;
	}
	public void setCurrentDocument(NodeBoxDocument document)
	{
		currentDocument = document;
		//autosandbox is preferred and in the diff view mode
		if (Application.ENABLE_AUTOSANDBOX && referenceDocument != null){
			 if (!currentDocument.getAlternativePaneHeader().isSandboxed())
				 sandboxSetEnabled(currentDocument != referenceDocument);
		}
		
		//set current menubar too here somewhere
    	NodeBoxMenuBar menuBar = currentDocument == null ? null : currentDocument.getNodeBoxMenuBar();
        setJMenuBar(menuBar);
		//also do some visual stuff to tell that this is the document
		//all others set false
		updateGradients();
		menuBar.updateUI(); //this fixes the bug on Windows and Linux that the menu gets stuck	

		//if in diffviz mode, hide cross alternative connectors  for other alternatives when choosing next alternative, except when switching to reference, then display all connectors
		if (referenceDocument != null){
			if (currentDocument.isReference == true){
				for (NodeBoxDocument doc: documentGroup){ //enable all
					doc.getAlternativePaneHeader().setDiffToggleSelected(true);
					}
				}
			else{
				for (NodeBoxDocument doc: documentGroup){ //enable all
					doc.getAlternativePaneHeader().setDiffToggleSelected(doc == currentDocument);					
				}
			}
				
		}
	}

	public void updateGradients() {
		for (NodeBoxDocument doc: documentGroup)
		{
			//toggle active viewer
			if (doc == currentDocument && doc.getAlternativePaneHeader().isEditable())
			{
				//toggle active address bar
				//doc.setAdressBarGradientActive(0);
				//toggle active address bar
	    		doc.getViewerPane().getViewer().setBackground(Theme.ACTIVE_COLOR);
			}
	    	else
	    	{
				//toggle active address bar
	    		//if (doc.getAlternativePaneHeader().isUnlocked()){
	    			//doc.setAdressBarGradientActive(1);
	    			doc.getViewerPane().getViewer().setBackground(Theme.PASSIVE_COLOR);
	    		//}
	    		/*else{
	    			doc.setAdressBarGradientActive(2);
	    			doc.getViewerPane().getViewer().setBackground(Theme.VIEWER_BACKGROUND_COLOR_LOCKED);
	    		}*/
	    			
						
	    	}
			doc.getViewerPane().getViewer().repaint();
			doc.getNetworkView().repaint();
			doc.getPortView().repaint();
		}
	}
	public NodeBoxDocument getCurrentDocument()
	{
		return currentDocument;
	}

	public ArrayList<NodeBoxDocument> getDocumentGroup() {
		return documentGroup;
	}
	
    //// Window events ////

    public void windowOpened(WindowEvent e) {
        //viewEditorSplit.setDividerLocation(0.5);
        //parameterNetworkSplit.setDividerLocation(0.5);
        //topSplit.setDividerLocation(0.5);
    }

    public void windowClosing(WindowEvent e) {
        quit();
        // if last window close whole application
        if (Application.getInstance().getAppFrames().size() <= 0)
        	System.exit(0);
    }

    public void windowClosed(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowActivated(WindowEvent e) {
        Application.getInstance().setCurrentFrame(this);
    }

    public void windowDeactivated(WindowEvent e) {
    }
    
    private class ZoomInHandler implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            zoomView(1.05);
        }
    }

    private class ZoomOutHandler implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            zoomView(0.95);
        }
    }
    
    public void zoomView(double scaleDelta) {
        PointerInfo a = MouseInfo.getPointerInfo();
        Point point = new Point(a.getLocation());
        for (Zoom zoomListener : zoomListeners) {
            if (zoomListener.containsPoint(point))
                zoomListener.zoom(scaleDelta);
        }
    }

    public void addZoomListener(Zoom listener) {
        zoomListeners.add(listener);
    }

    public void removeZoomListener(Zoom listener) {
        zoomListeners.remove(listener);
    }

    public String getWorkspacePath() {
		return workspacePath;
	}

	public void setWorkspacePath(String workspacePath) {
		this.workspacePath = workspacePath;
	}
	
	public int getMonitorArrangement() {
		return monitorArrangement;
	}
	
	public void setMonitorArrangement(int monitorArrangement) {
		this.monitorArrangement = monitorArrangement;
	}
	
	public boolean quit() {
		boolean changed = false;
		boolean result = true;
		
		// check if documents are changes
    	for (NodeBoxDocument document : documentGroup)
    		if (document.isChanged())
    			changed = true;

    	// check if user wants to save the project
    	if (changed) {
    		// always bring the current window in the front (user needs feedback)
    		toFront();
    		// ask if user wants to save changes
    		if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, "Do you want to save this workspace?", "Do you want to save this workspace?", JOptionPane.YES_NO_OPTION))
    			result = new WorkspaceManager(this).save(false);
    	}

    	// close window if everything is all right
    	if (result) {
    		dispose();
    		Application.getInstance().removeAppFrame(this);
    	}

    	return result;
	}
}
