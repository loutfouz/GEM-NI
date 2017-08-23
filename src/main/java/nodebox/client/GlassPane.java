package nodebox.client;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

import com.google.common.collect.ImmutableList;

import nodebox.node.Node;
import nodebox.ui.Platform;
import nodebox.ui.Theme;


public class GlassPane extends JPanel implements AWTEventListener {

	private final ApplicationFrame appFrame;
	private final AlternativesDragger dragger;
	private NodeDragger nodeDragger;
	
	// default: first monitor, first alternative
	private int oldMonitor = 0;
	private int oldAlternative = 0;
	private int actualMonitor = 0;
	private int actualAlternative = 0;
	private int previousMonitor = 0;
	private int previousAlternative = 0;
	
	private BufferedImage image = null;
	private Point imagePosition = null;

	private ArrayList<NodeBoxDocument> selectedDocuments;
	private boolean selectionMode = false; // detect if we are in selection mode (means control is pressed)
	
	
	public GlassPane(ApplicationFrame appFrame) {
		super(null);
		this.appFrame = appFrame;
		this.dragger = new AlternativesDragger(this, appFrame);		
		this.nodeDragger = new NodeDragger(this, appFrame);

		selectedDocuments = new ArrayList<NodeBoxDocument>();
		
		setOpaque(false);
		AWTEventListener al = (AWTEventListener) this;
        Toolkit.getDefaultToolkit().addAWTEventListener(al, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
	}

	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		////---------------------
		drawTransparentRectOverUneditableDocs(g2);
		
        drawJamBorders(g2);
				
		// draw references if necessary
		if (Application.ENABLE_SUBTRACTIVE_ENCODING && appFrame.getReferenceDocument() != null) { // only if a master document is set
			for (NodeBoxDocument doc : appFrame.getDocumentGroup()) {
				if (doc != appFrame.getReferenceDocument()) {
					NetworkView networkView = doc.getNetworkView();
					networkView.paintElephantConnections(g2);
				}
			}
		}
		
		// draw image if given
		if (image != null && imagePosition != null)
			g.drawImage(image, imagePosition.x, imagePosition.y, null);
	

        g2.dispose();
	}

	public boolean getSelectionMode(){
		return selectionMode;
	}
	private void drawJamBorders(Graphics2D g2) {
		g2.setColor(Color.RED);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .8f));
        for (int i = 0;  i < appFrame.jamMonitors.length; i++){
        	boolean isJammed = appFrame.jamMonitors[i];
        	if (isJammed){
        		JPanel alternativesPanel = appFrame.alternativesPanel[i];
        		//draw border
        		//top
	            g2.fillRect(alternativesPanel.getX(),
	            		alternativesPanel.getY(), 
	            		alternativesPanel.getWidth(),
	            		Theme.jamBorderSize);
	            //bottom
	            g2.fillRect(alternativesPanel.getX(),
	            		alternativesPanel.getY() - Theme.jamBorderSize + alternativesPanel.getHeight(), 
	            		alternativesPanel.getWidth(),
	            		Theme.jamBorderSize);
	            
        		//left
	            g2.fillRect(alternativesPanel.getX(),
	            		alternativesPanel.getY(), 
	            		Theme.jamBorderSize,
	            		alternativesPanel.getHeight());

        		//right
	            g2.fillRect(alternativesPanel.getX() - Theme.jamBorderSize + alternativesPanel.getWidth(),
	            		alternativesPanel.getY(), 
	            		Theme.jamBorderSize,
	            		alternativesPanel.getHeight());

        	}
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));
		g2.setColor(Color.BLACK);
	}

	private void drawTransparentRectOverUneditableDocs(Graphics2D g2) {
		ArrayList<NodeBoxDocument> lockedDocuments = appFrame.getLockedDocuments();
		
		//g2.setColor(Theme.UNEDITABLE_ALTERNATIVE_COLOR);
		//g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .8f));
		
		int numCols = appFrame.getNumCols();
		
		for (NodeBoxDocument lockedDoc : lockedDocuments){
			if (lockedDoc.currentActiveNetworkMissing)
				g2.setColor(Color.red);
			else
				g2.setColor(Theme.IDLE_COLOR);
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .8f));
			
			//height is of the alternative pane holder is used to avoid drawing the rect over it
			int height = lockedDoc.getAlternativePaneHeader().getHeight();
			int monitorNo = lockedDoc.getMonitorNo();
			int colNo = monitorNo % numCols;
	        int numAlt = appFrame.getNumAltForDocumentInMonitor(lockedDoc);
	        //int altNo = appFrame.getAlternativeNumberRelativeToMonitor(lockedDoc);

			JPanel rootPanel = lockedDoc.getRootPanel();
			
			//find total number of alternative up to now to get the number of borders
			int totalBorderWidth = 2 * colNo; //border size is hardcoded to be 2
			
			int x = rootPanel.getX() + rootPanel.getWidth() * colNo * numAlt + totalBorderWidth + numAlt;
		
			int y = rootPanel.getY() + height;
			
			if (!lockedDoc.isFirstRow) //if second row
				y = rootPanel.getY() + rootPanel.getHeight();
			g2.fillRect(x,
					y, 
					rootPanel.getWidth(),
					rootPanel.getHeight() - height);				
		}
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));
		g2.setColor(Color.BLACK);

	}

    public void setImage(BufferedImage image) {
    	this.image = image;
    }
    
    public void setImagePosition(Point position) {
    	this.imagePosition = position;
    	repaint();
    }
    
    public void resetImage() {
    	image = null;
    	imagePosition = null;
    }
    
	/**
	 * 
	 * @param mouse event
	 */
	private void setActiveMonitorAndAlternative(Component component) {
		if (component == null)
			return;
		//bug on Windows or Ubuntu, when you click on the menubar it switches alternative, this excludes this case
		if (!Platform.onMac() && (component instanceof ApplicationFrame || component instanceof JMenu))
			return;
		
		// default
		//actualAlternative = 0;
		
		// get active monitor
		for (Component monitorPanel : appFrame.monitorPanel.getComponents()) {
			if (SwingUtilities.isDescendingFrom(component, monitorPanel)) {
				actualMonitor = Integer.valueOf(monitorPanel.getName());
				// get active alternative
				for (Component alternative : appFrame.alternativesPanel[actualMonitor].getComponents())
        			if (SwingUtilities.isDescendingFrom(component, alternative)) {
        				actualAlternative = Integer.valueOf(alternative.getName());
        				break;
        			}
				break;
			}
		}
	}
	
	/**
	 * 
	 * @param mouse event
	 */
	private void changeActiveMonitorOrAlternative() {
		// this is the old state
		previousMonitor = oldMonitor;
		previousAlternative = oldAlternative;
		
		// monitor or alternative changed
		//if (actualMonitor != oldMonitor || actualAlternative != oldAlternative) {
			//now let's check if at least one context sensitive menu in any of the alternatives is visible, same for the viewer menu
    		//if the menu is visible we don't do the switch
    		boolean atLeastOneIsVisible = false;

    		for (NodeBoxDocument doc : appFrame.getDocumentGroup())
    			atLeastOneIsVisible = atLeastOneIsVisible || doc.getNetworkView().atLeastOneNetworkMenuIsVisible() || doc.getViewerPane().getViewer().isViewerMenuVisible();

    		if (!atLeastOneIsVisible)
    			// check if alternative exists
    			if (appFrame.monitorDocumentMap.get(actualMonitor).size() >= actualAlternative){
    				appFrame.setCurrentDocument(appFrame.monitorDocumentMap.get(actualMonitor).get(actualAlternative));
    			}

			oldMonitor = actualMonitor;
			oldAlternative = actualAlternative;
		//}
	}
	
	@Override
	public void eventDispatched(AWTEvent event) {
		if (event instanceof MouseEvent) {
			MouseEvent me = (MouseEvent)event;
			
			// if we are on the frame ignore
			if (!SwingUtilities.isDescendingFrom(me.getComponent(), appFrame))
				return;
			
        	// dragging
			if (!selectionMode && me.isControlDown() && me.isAltDown() && !me.isShiftDown() || dragger.enabled()) {
        		// consume event so that dragging is the forced and only operation
        		me.consume();

        		// update current mouse position for dragging 
        		me = SwingUtilities.convertMouseEvent(me.getComponent(), me, appFrame.getGlassPane());
    			setActiveMonitorAndAlternative(findComponentUnderGlassPaneAt(me.getPoint(), appFrame.getRootPane()));

        		switch (me.getID()) {
        		case MouseEvent.MOUSE_PRESSED:
        			dragger.start(me);
        			break;
        		case MouseEvent.MOUSE_DRAGGED:
        			dragger.drag(me);
        			break;
        		case MouseEvent.MOUSE_RELEASED:
        			dragger.end();
        			repaint(); // repaint the glass pane after dragging is done
        			break;
        		}
        	}
			//SHIFT + CONTROL + CLICK to reveal common node
			else if (!selectionMode && me.isShiftDown() && me.isControlDown() && !me.isAltDown() && me.getID() == MouseEvent.MOUSE_PRESSED && me.getButton() == MouseEvent.BUTTON1){
        		me.consume();
        		// update current mouse position
        		me = SwingUtilities.convertMouseEvent(me.getComponent(), me, appFrame.getGlassPane());
				//lets reveal common nodes
				revealCommonNodesInComparedNetworkView(me);
				repaint();
				//return so that the second isAltDown() statement is not triggered (for alternative dragging)
				return;
			}
			//CONTROL + CLICK  to select reference (make sure !isMetaDown, otherwise, the action for META + CONTROL + click would not get invoked sometimes
			else if (!selectionMode && me.isControlDown() && !me.isShiftDown() && !me.isAltDown() && me.getID() == MouseEvent.MOUSE_PRESSED){// && me.getButton() == MouseEvent.BUTTON1) {
				try {
	        		// update current mouse position
	    			setActiveMonitorAndAlternative(me.getComponent());
	        		// change to active monitor and/or alternative if necessary
	        		changeActiveMonitorOrAlternative();
        		} catch (Exception e) {} // TODO: handle exception
				appFrame.switchRefencetoCurrDocument();
			}
			//SHIFT+ ALT + CLICK to make editable / unmake editable
			else if (!selectionMode && me.isShiftDown() && me.isAltDown() && me.getID() == MouseEvent.MOUSE_PRESSED && me.getButton() == MouseEvent.BUTTON1) {
				try {
	        		// update current mouse position
	    			setActiveMonitorAndAlternative(me.getComponent());
	        		
					NodeBoxDocument doc = appFrame.monitorDocumentMap.get(actualMonitor).get(actualAlternative);
					boolean state = doc.getAlternativePaneHeader().toggleEditableState();
					doc.menuBar.toggleEditableMenuItem();
					appFrame.disableAllSandboxButtons();
					//log
					appFrame.log(doc.getDocumentName(),String.valueOf(doc.getDocumentNo()),"Set Editable", String.valueOf(state), "Shortcut");
					//repaintAllViews();
        		} catch (Exception e) {} // TODO: handle exception
			}
			//ALT + CLICK  to toggle sandbox, but first select the active alternative (in case the user is trying to sandbox another alternative)
			else if (!selectionMode &&  me.isAltDown() && me.getID() == MouseEvent.MOUSE_PRESSED && me.getButton() == MouseEvent.BUTTON1) {
				try {
	        		// update current mouse position
	    			setActiveMonitorAndAlternative(me.getComponent());
	        		// change to active monitor and/or alternative if necessary
	        		changeActiveMonitorOrAlternative();
        		} catch (Exception e) {} // TODO: handle exception
				boolean enabled = appFrame.toggleSandbox();
				NodeBoxDocument currDoc = appFrame.getCurrentDocument();
				appFrame.log(currDoc.getDocumentName(), String.valueOf(currDoc.getDocumentNo()),"Sandbox", String.valueOf(enabled), "Shortcut");

			}
        	// select active alternative
			else if (me.getID() == MouseEvent.MOUSE_PRESSED && me.getButton() == MouseEvent.BUTTON1) {
        		try {
	        		// update current mouse position
	    			setActiveMonitorAndAlternative(me.getComponent());
	        		// change to active monitor and/or alternative if necessary
	    			//change only if it's editable
	    			boolean editable = appFrame.monitorDocumentMap.get(actualMonitor).get(actualAlternative).getAlternativePaneHeader().isEditable();
	    			//if (editable)
	        			changeActiveMonitorOrAlternative();
        		} catch (Exception e) {} // TODO: handle exception
        	}
		
        	


			//This would only work on Linux or Mac, but not on Windows, because Meta key is not available to Windows apps
			if (me.isMetaDown() || nodeDragger.getEnabled()){
        		//consume only if mouse is dragged, because otherwise shift select doesnt work
        		if (me.getID() == MouseEvent.MOUSE_DRAGGED)
        			me.consume();  // consume event so that dragging is the forced and only operation
        		
        		// update current mouse position for dragging 
        		me = SwingUtilities.convertMouseEvent(me.getComponent(), me, appFrame.getGlassPane());
        		switch (me.getID()) {
        		case MouseEvent.MOUSE_PRESSED:
        			nodeDragger.start(me);
        			break;
        		case MouseEvent.MOUSE_DRAGGED:
        			nodeDragger.drag(me);
        			break;
        		case MouseEvent.MOUSE_RELEASED:
        			nodeDragger.end();
        			repaint(); // repaint the glass pane after dragging is done
        			break;
        		}
        	}

			if (selectionMode && me.getID() == MouseEvent.MOUSE_RELEASED){
    			me.consume();

        		//if the user released the shift button before the ending dragging, then we need to clear the the setBorder of nodeDragger operations
        		for (NodeBoxDocument doc : appFrame.getDocumentGroup()){
        			NetworkView networkView = doc.getNetworkView();
        			networkView.setBorder(null);
        		}

        		NodeBoxDocument document = appFrame.monitorDocumentMap.get(actualMonitor).get(actualAlternative);
    			
    			if (selectedDocuments.contains(document)) {
    				selectedDocuments.remove(document);
    				document.getRootPanel().setBorder(null);
    			} else {
    				selectedDocuments.add(document);
    				document.getRootPanel().setBorder(BorderFactory.createLineBorder(Color.RED, 3));
    			}
        	}
		}
		
		if (event instanceof KeyEvent) {
			KeyEvent ke = (KeyEvent) event;
			
			switch (ke.getID()) {
			case KeyEvent.KEY_PRESSED:
				//on Windows, meta is not available, so instead use alt
				int all_down =  KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
				boolean down = ((all_down & ke.getModifiersEx()) == all_down);

				if (down){
					selectionMode = true;
				}
				break;
			case KeyEvent.KEY_RELEASED:
				//if all keys are released
				if (ke.getModifiersEx() == 0){
					selectionMode = false;
					// if the user selected documents show the gallery
					if (selectedDocuments.size() > 0) {
						//log design gallery invocation
						logDesignGalleryInvocation();
						// remove border
						for (NodeBoxDocument document : selectedDocuments)
							document.getRootPanel().setBorder(null);
						//do power sets is now determined from the preferences menu
						new GalleryDialog(appFrame, selectedDocuments); 
						selectedDocuments.clear();
						
					}
				}
				break;
			}
		}
	}

	private void logDesignGalleryInvocation() {
		//log
		//create a string list of selected Nodes
		String sourceAlternativesNames = "names" + ApplicationFrame.attributeDelimiter;
		String sourceAlternativesNumbers = "numbers" + ApplicationFrame.attributeDelimiter;
		for (NodeBoxDocument doc : selectedDocuments){
			sourceAlternativesNames += doc.getDocumentName() + ApplicationFrame.itemDelimiter;
			sourceAlternativesNumbers  += String.valueOf(doc.getDocumentNo()) + ApplicationFrame.itemDelimiter;
		}
		//remove last delimiter character
		sourceAlternativesNames = sourceAlternativesNames.substring(0, sourceAlternativesNames.length() - 1);
		sourceAlternativesNumbers = sourceAlternativesNumbers.substring(0, sourceAlternativesNumbers.length() - 1);

		appFrame.log("Design gallery", "invoke", sourceAlternativesNames, sourceAlternativesNumbers);
	}

	private void revealCommonNodesInComparedNetworkView(MouseEvent me) {
		if (appFrame.getReferenceDocument() != null){
			Component c = GlassPane.findComponentUnderGlassPaneAt(me.getPoint(), appFrame.getRootPane());
			if (c instanceof NetworkView){
				NetworkView networkView = (NetworkView) c;	
				if (networkView != appFrame.getReferenceDocument().getNetworkView()){ //if some other networkPane that referenceView networkPane
					try{
						NetworkView sourceNetworkView = appFrame.getReferenceDocument().getNetworkView();
						Iterator<String> iter = sourceNetworkView.getSelectedNodeNames().iterator();
					
						if (networkView.draggedNodesNames == null)
							networkView.draggedNodesNames = new HashSet<String>();
						String strNames = ""; //for logging
						while (iter.hasNext()){
							String nodeName = (String) iter.next();
							networkView.draggedNodesNames.add(nodeName);
							strNames += nodeName + ApplicationFrame.itemDelimiter; //for logging
						}
						
						//remove last delimiter character (for logging)
						strNames = strNames.substring(0, strNames.length() - 1);
						//logging
						appFrame.log("Reveal node", networkView.getDocument().getDocumentName(), String.valueOf(networkView.getDocument().getDocumentNo()), strNames);
					}
	            	catch (java.lang.NullPointerException e){
	            		System.err.println("reference document not specified to complete the transaction");
	            	}
				}
				else{ //clicked on the reference alternative
					//reveal in all alternatives where it exists
					for (NodeBoxDocument doc : appFrame.getDocumentGroup()){
						NetworkView view = doc.getNetworkView();
						NetworkView refNetworkView = appFrame.getReferenceDocument().getNetworkView();

						if (view != networkView){ //if this is comparedView
							if (view.draggedNodesNames == null)
								view.draggedNodesNames = new HashSet<String>();
							String strNames = ""; //for logging
							Iterator<String> iter = refNetworkView.getSelectedNodeNames().iterator();
							while (iter.hasNext()){
								String nodeName = (String) iter.next();
								view.draggedNodesNames.add(nodeName);
								strNames += nodeName + ApplicationFrame.itemDelimiter; //for logging
							}
							
							//remove last delimiter character (for logging)
							strNames = strNames.substring(0, strNames.length() - 1);
							//logging
							appFrame.log("Reveal node", view.getDocument().getDocumentName(), String.valueOf(view.getDocument().getDocumentNo()), strNames);

						}
					}
				}

			}
		}
	}

	/**
	 * If someone adds a mouseListener to the GlassPane or set a new cursor we
	 * expect that he knows what he is doing and return the super.contains(x, y)
	 * otherwise we return false to respect the cursors for the underneath
	 * components
	 */
	public boolean contains(int x, int y) {
		if (getMouseListeners().length == 0
				&& getMouseMotionListeners().length == 0
				&& getMouseWheelListeners().length == 0
				&& getCursor() == Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)) {
			return false;
		}
		return super.contains(x, y);
	}
	
	public int getActualMonitor() {
		return actualMonitor;
	}

	public int getActualAlternative() {
		return actualAlternative;
	}

	public static Component findComponentUnderGlassPaneAt(Point p, Component top) {
		Component c = null;
		if (top.isShowing()) {
		  if (top instanceof RootPaneContainer)
		    c = ((RootPaneContainer)top).getLayeredPane().findComponentAt(SwingUtilities.convertPoint(top, p, ((RootPaneContainer)top).getLayeredPane()));
		  else
		    c = ((Container)top).findComponentAt(p);
		}
		return c;
	}

	/**
	 * 
	 *
	 */
	private class AlternativesDragger {
    	
    	private NodeBoxDocument alternative;
    	private BufferedImage panelImage;
    	private JPanel panel;
    	private JPanel alternativePreview;
    	private JPanel alternativeTemp;
    	
    	// TODO: move the code to the glass pane
    	private int originalMonitor;
    	private int originalAlternative;
    	private int activeMonitor;
    	private int activeAlternative;
    	private int previousMonitor;
    	private int previousAlternative;
    	
    	private final int previewMaxWidth = 200;

    	private final GlassPane glassPane;
    	private final ApplicationFrame app;
    	
    	private boolean enabled;
    	

    	public AlternativesDragger(final GlassPane glassPane, final ApplicationFrame app) {
    		this.glassPane = glassPane;
    		this.app = app;
    		reset();
    	}
    	
    	public void reset() {
    		glassPane.resetImage();
    		alternative = null;
    		alternativePreview = null;
    		alternativeTemp = null;
    		panel = null;
    		panelImage = null;
        	originalMonitor = -1;
        	originalAlternative = -1;
        	activeMonitor = -1;
        	activeAlternative = -1;
        	previousMonitor = -1;
        	previousAlternative = -1;
        	enabled = false;
    	}
    	
    	public boolean enabled() {
    		return enabled;
    	}

    	public void start(MouseEvent me) {
    		enabled = true;
    		originalMonitor = glassPane.getActualMonitor();
			originalAlternative = glassPane.getActualAlternative();
			try {
				alternative = app.monitorDocumentMap.get(originalMonitor).get(originalAlternative);
				panel = (JPanel)app.alternativesPanel[originalMonitor].getComponent(originalAlternative);
			} catch (Exception e) {
				reset(); // disable dragging because we are not be able to get a concrete alternative to drag
				return;
			}
			// disable GridBagLayout until dragging is finished
    		app.updateAlternativesPanels(false);
			previousMonitor = activeMonitor = originalMonitor;
			previousAlternative = activeAlternative = originalAlternative;
			panelImage = createImage(panel, previewMaxWidth);
			glassPane.setImage(panelImage);
			glassPane.setImagePosition(me.getPoint());
			// create preview panel 
			alternativePreview = new JPanel();
    		alternativePreview.setBorder(BorderFactory.createRaisedBevelBorder());

    	}
    	
    	public void drag(MouseEvent me) {
    		if (!enabled)
    			return;
    		
    		// detect actual position
    		activeMonitor = glassPane.getActualMonitor();
    		activeAlternative = glassPane.getActualAlternative();

    		// update image position
    		glassPane.setImagePosition(me.getPoint());
    		
    		// show preview on new position
    		if (changed()) {
    			// remove old preview panel if necessary
    			app.alternativesPanel[previousMonitor].remove(alternativePreview);

    			// add the temporary removed alternative again
				if (alternativeTemp != null) {
    				app.alternativesPanel[previousMonitor].add(alternativeTemp, previousAlternative);
    				alternativeTemp = null;
    			}
    			
				if (oldMonitor != activeMonitor || (oldMonitor == activeMonitor && oldAlternative != activeAlternative)) {
	    			// dragging inside one monitor (alternative switching) or dragging on an empty monitor
	    			if (activeMonitor == oldMonitor || app.monitorDocumentMap.get(activeMonitor).size() <= 0) {
	    				// remove panel under position and replace it with the preview
	    				alternativeTemp = (JPanel)app.alternativesPanel[activeMonitor].getComponent(activeAlternative);
	    				app.alternativesPanel[activeMonitor].remove(activeAlternative);
	    			}
	    			
	    			// add preview
	    			app.alternativesPanel[activeMonitor].add(alternativePreview, activeAlternative);
	    			
	    			// update
	    			alternativePreview.setName(String.valueOf(activeAlternative));
				}
    			
    			app.alternativesPanel[activeMonitor].updateUI();

    			// reset position of all components temporary by setting the name
    			// updateAlternativesPanel will reset the values later to the real values again 
    			for (int i = 0; i < app.alternativesPanel[activeMonitor].getComponentCount(); i++)
    				app.alternativesPanel[activeMonitor].getComponent(i).setName(String.valueOf(i));
    			
    			// save new state
    			previousAlternative = activeAlternative;
    			previousMonitor = activeMonitor;
    		}
    	}
    	
    	public void end() {
    		if (!enabled)
    			return;
    		
			// remove alternative from old monitor and add to new monitor at new position
			// this works also if the monitor is the same and the user only wants to drag
			// alternatives inside a monitor
			app.monitorDocumentMap.get(originalMonitor).remove(alternative);
			app.monitorDocumentMap.get(activeMonitor).add(activeAlternative, alternative);
			alternative.setMonitorNum(activeMonitor);
			// update alternatives panel
			// enable GridBagLayout so that focusing can be used once again
			app.updateAlternativesPanels(true);
			//alternative was dragged, enforce it's state relative to monitor state
			app.enforceAlternativeState(alternative);
    		//log alternative moving
			app.log("Move alternative",alternative.getDocumentName(), String.valueOf(alternative.getDocumentNo()), "source=" + originalMonitor + ApplicationFrame.itemDelimiter + originalAlternative, "destination=" + activeMonitor + ApplicationFrame.itemDelimiter + activeAlternative);
    		reset();
    	}

    	private BufferedImage createImage(JPanel panel, int maxWidth) {
    		Graphics2D g;
			final int w = panel.getWidth();
			final int h = panel.getHeight();
			final double scaling = (double)maxWidth / (double)w;
			final int maxHeight = (int)(scaling * h);
			final BufferedImage originalImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			final BufferedImage resizedImage = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
			g = originalImage.createGraphics();
			panel.paint(g);
			// resize image and add transparent before return
			g = resizedImage.createGraphics();
			AffineTransform at = AffineTransform.getScaleInstance(scaling, scaling);
	        g.drawRenderedImage(originalImage, at);
			return resizedImage;
    	}
    	
    	private boolean changed() {
    		return activeMonitor != previousMonitor || activeAlternative != previousAlternative;
    	}
	}
	
	
}
