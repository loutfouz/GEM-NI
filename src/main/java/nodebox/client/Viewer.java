package nodebox.client;

import com.google.common.collect.ImmutableList;

import nodebox.client.visualizer.*;
import nodebox.graphics.CanvasContext;
import nodebox.graphics.Grob;
import nodebox.graphics.IGeometry;
import nodebox.handle.Handle;
import nodebox.node.Node;
import nodebox.ui.Theme;
import nodebox.ui.Zoom;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedList;

import static com.google.common.base.Preconditions.checkNotNull;
import static nodebox.util.ListUtils.listClass;

public class Viewer extends ZoomableView implements OutputView, Zoom, MouseListener, MouseMotionListener, KeyListener {

    public static final double MIN_ZOOM = 0.01;
    public static final double MAX_ZOOM = 64.0;

    private static final ImmutableList<Visualizer> visualizers;
    private static final Visualizer DEFAULT_VISUALIZER = LastResortVisualizer.INSTANCE;

    private final JPopupMenu viewerMenu;

    private nodebox.graphics.Point lastMousePosition = nodebox.graphics.Point.ZERO;

    private Handle handle;
    private boolean showHandle = true;
    private boolean showPoints = false;
    private boolean showPointNumbers = false;
    private boolean showOrigin = false;
    private boolean viewPositioned = false;
    private boolean showDiff = true;
    private boolean showText = true;

    private java.util.List<?> outputValues;
    private Class valuesClass;
    private Visualizer currentVisualizer;
    private NodeBoxDocument document;

    static {
        visualizers = ImmutableList.of(CanvasVisualizer.INSTANCE, GrobVisualizer.INSTANCE, PointVisualizer.INSTANCE, ColorVisualizer.INSTANCE);
    }

    public Viewer(NodeBoxDocument document) {
        super(MIN_ZOOM, MAX_ZOOM);
        addMouseListener(this);
        addMouseMotionListener(this);
        setFocusable(true);
        addKeyListener(this);
        setBackground(Theme.PASSIVE_COLOR);

        viewerMenu = new JPopupMenu();
        viewerMenu.add(new ResetViewAction());
        PopupHandler popupHandler = new PopupHandler();
        addMouseListener(popupHandler);
        
        this.document = document; 
    }

    public Visualizer getCurrentVisualizer(){
    	return currentVisualizer;
    }
    public java.util.List<?> getOutputValues(){
    	return outputValues;
    }
    public void setShowText(boolean showText) {
    	this.showText = showText;
    }
    
    public boolean isViewerMenuVisible(){
    	return viewerMenu.isVisible();
    }
    public void zoom(double scaleDelta) {
        super.zoom(scaleDelta, getWidth() / 2.0, getHeight() / 2.0);
    }

    public boolean containsPoint(Point point) {
        return isVisible() && getBounds().contains(point);
    }

    public void setShowHandle(boolean showHandle) {
        this.showHandle = showHandle;
        repaint();
    }
    
    public void setShowDiff(boolean showDiff) {
        this.showDiff = showDiff;
        repaint();
    }

    public void setShowPoints(boolean showPoints) {
        this.showPoints = showPoints;
        repaint();
    }

    public void setShowPointNumbers(boolean showPointNumbers) {
        this.showPointNumbers = showPointNumbers;
        repaint();
    }

    public void setShowOrigin(boolean showOrigin) {
        this.showOrigin = showOrigin;
        repaint();
    }

    //// Handle support ////

    public Handle getHandle() {
        return handle;
    }

    public void setHandle(Handle handle) {
        this.handle = handle;
        repaint();
    }

    public void updateHandle() {
        if (handle == null) return;
        handle.update();
    }

    public boolean hasVisibleHandle() {
        if (handle == null) return false;
        if (!showHandle) return false;

        // Don't show handles for LastResortVisualizer and ColorVisualizer.
        if (currentVisualizer instanceof LastResortVisualizer) return false;
        if (currentVisualizer instanceof ColorVisualizer) return false;

        return handle.isVisible();
    }

    //// Network data events ////

    public void setOutputValues(java.util.List<?> outputValues) {
        this.outputValues = outputValues;
        valuesClass = listClass(outputValues);
        Visualizer visualizer = getVisualizer(outputValues, valuesClass);
        if (visualizer instanceof LastResortVisualizer && outputValues.size() == 0) {
            // This scenario means likely that we're in a node that normally outputs
            // some visual type but currently outputs null (or None)
            // If we'd reset the visualizer the screen offset would change, and this would
            // lead to strange (and wrong) interactions with handles (big leaps in
            // current mouse locations).
            repaint();
            return;
        }
        if (currentVisualizer != visualizer) {
            currentVisualizer = visualizer;
            resetViewTransform();
        }
        checkNotNull(currentVisualizer);
        repaint();
    }

    public static Visualizer getVisualizer(Iterable<?> objects, Class listClass) {
        for (Visualizer visualizer : visualizers) {
            if (visualizer.accepts(objects, listClass))
                return visualizer;
        }
        return DEFAULT_VISUALIZER;
    }

    @Override
    public void resetViewTransform() {
    	Node renderedNode = document.renderedNode;
    	//if gallery document then dont bother with non geometry and non center zoom visualization
    	//because in the gallery you'd rather see non geometry nodes rendered properly
    	if (renderedNode == null || document.isGalleryDocument){
    		centerAndZoom(true);
    		return;
    	}
    	if (renderedNode.getCategory().equals("geometry")){
        	centerAndZoom(true); //center and zoom relative to all
    	}
    	else{
    		Point2D position = currentVisualizer.getOffset(outputValues, getSize());
    		setViewTransformNonGeometry(position.getX(), position.getY(), 1);
    	}
    	
		//System.out.println("inputs=" + renderedNode.getInputs());

    }

	public void centerAndZoom(boolean relativeToAll) {
		try {
	        Point2D position = currentVisualizer.getOffset(outputValues, getSize());
	        setViewTransform(position.getX(), position.getY(), 1);

	        //let's calculate zoom for centering
	        int w = getSize().width;
	        int h = getSize().height;
  	        if (w == 0 && h == 0) return;

	        double ratio = 0;
	        if (relativeToAll){
	    		ArrayList<NodeBoxDocument> docs = document.getAppFrame().getDocumentGroup();
	    		for (NodeBoxDocument doc : docs){
	    			java.util.List<?> docOutputValues = doc.getViewerPane().getViewer().getOutputValues();
	    	        Rectangle2D visualizerBounds = doc.getViewerPane().getViewer().getCurrentVisualizer().getBounds(docOutputValues);
	    	        double currRatio = Math.max(visualizerBounds.getWidth()/w, visualizerBounds.getHeight()/h);
	    	        ratio = Math.max(currRatio, ratio);
	    	        //System.out.println("currRatio:" + currRatio + ",ratio:"+ratio);
	    		}
	        }
	        else{
	        	//System.out.println("Not RELATIVE");
		        Rectangle2D visualizerBounds = currentVisualizer.getBounds(outputValues);
		        //System.out.println("vizBounds=" + visualizerBounds);
		        //System.out.println("getSize=" + getSize());
	  	        ratio = Math.max(visualizerBounds.getWidth()/w, visualizerBounds.getHeight()/h);
	  	        //System.out.println("ratio=" + ratio);
	        }
	        //System.out.println("viz ratio=" + ratio);
	        if (ratio > 0){
	        	double zoom = 1 / ratio;
	            if (zoom > 1) //max zoom is 1
	            	zoom = 1;
	            //System.out.println("zoom=" + zoom);
	            zoom(zoom, w / 2 , h / 2);
	        }
    	}
    	catch(Exception e){
    		System.err.println("Viewer is empty can't reset view");
    	}
	}

    //// Mouse events ////

    private nodebox.graphics.Point pointForEvent(MouseEvent e) {
        Point2D pt = inverseViewTransformPoint(e.getPoint());
        return new nodebox.graphics.Point(pt);
    }

    public nodebox.graphics.Point getLastMousePosition() {
        return lastMousePosition;
    }

    public void mouseClicked(MouseEvent e) {
   	 	if(!document.getAlternativePaneHeader().isEditable()) return;
    	
        // We register the mouse click as an edit since it can trigger a change to the node.
        if (e.isPopupTrigger()) return;
        if (hasVisibleHandle()) {
            //getDocument().addEdit(HANDLE_UNDO_TEXT, HANDLE_UNDO_TYPE, activeNode);
            handle.mouseClicked(pointForEvent(e));
        }
    }

    public void mousePressed(MouseEvent e) {
    	 if(!document.getAlternativePaneHeader().isEditable()) return;

        // We register the mouse press as an edit since it can trigger a change to the node.
        if (e.isPopupTrigger()) return;
        if (hasVisibleHandle()) {
            //getDocument().addEdit(HANDLE_UNDO_TEXT, HANDLE_UNDO_TYPE, activeNode);
            handle.mousePressed(pointForEvent(e));
        }
    }

    public void mouseReleased(MouseEvent e) {
   	 if(!document.getAlternativePaneHeader().isEditable()) return;

    	//now everything is done in glass pane, the hack method
    	//when you click on this network view change the active document
    	//document.getAppFrame().setCurrentDocument(document);
    	
    	// We register the mouse release as an edit since it can trigger a change to the node.
    	if (e.isPopupTrigger()) return;
        if (hasVisibleHandle()) {
            //getDocument().addEdit(HANDLE_UNDO_TEXT, HANDLE_UNDO_TYPE, activeNode);
            handle.mouseReleased(pointForEvent(e));
        }
    }

    public void mouseEntered(MouseEvent e) {
   	 if(!document.getAlternativePaneHeader().isEditable()) return;

        // Entering the viewer with your mouse should not change the node, so we do not register an edit.
        if (e.isPopupTrigger()) return;
        if (hasVisibleHandle()) {
            handle.mouseEntered(pointForEvent(e));
        }
    }

    public void mouseExited(MouseEvent e) {
   	 	if(!document.getAlternativePaneHeader().isEditable()) return;

        // Exiting the viewer with your mouse should not change the node, so we do not register an edit.
        if (e.isPopupTrigger()) return;
        if (hasVisibleHandle()) {
            handle.mouseExited(pointForEvent(e));
        }
    }

    public void mouseDragged(MouseEvent e) {
   	 	if(!document.getAlternativePaneHeader().isEditable()) return;

        // We register the mouse drag as an edit since it can trigger a change to the node.
        if (e.isPopupTrigger()) return;
        if (isPanning()) return;
        if (hasVisibleHandle()) {
            //getDocument().addEdit(HANDLE_UNDO_TEXT, HANDLE_UNDO_TYPE, activeNode);
            handle.mouseDragged(pointForEvent(e));
        }
        lastMousePosition = pointForEvent(e);
    }

    public void mouseMoved(MouseEvent e) {
   	 	if(!document.getAlternativePaneHeader().isEditable()) return;

        // Moving the mouse in the viewer area should not change the node, so we do not register an edit.
        if (e.isPopupTrigger()) return;
        if (hasVisibleHandle()) {
            handle.mouseMoved(pointForEvent(e));
        }
        lastMousePosition = pointForEvent(e);
    }

    public void keyTyped(KeyEvent e) {
   	 	if(!document.getAlternativePaneHeader().isEditable()) return;

        if (hasVisibleHandle())
            handle.keyTyped(e.getKeyCode(), e.getModifiersEx());
    }

    public void keyPressed(KeyEvent e) {
   	 	if(!document.getAlternativePaneHeader().isEditable()) return;

        if (hasVisibleHandle())
            handle.keyPressed(e.getKeyCode(), e.getModifiersEx());
    }

    public void keyReleased(KeyEvent e) {
   	 	if(!document.getAlternativePaneHeader().isEditable()) return;

        if (hasVisibleHandle())
            handle.keyReleased(e.getKeyCode(), e.getModifiersEx());
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void paintComponent(Graphics g) {
        if (!viewPositioned) {
            setViewPosition(getWidth() / 2.0, getHeight() / 2.0);
            viewPositioned = true;
        }
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Draw background
        g2.setColor(getBackground());
        g2.fill(g.getClipBounds());

        // Set the view transform
        AffineTransform originalTransform = g2.getTransform();
        g2.transform(getViewTransform());

        
        //paint reference objects first, if in the reference view mode
        //paint reference view objects with transparency
        ApplicationFrame appFrame = document.getAppFrame();
        NodeBoxDocument referenceDocument = appFrame.getReferenceDocument();
        
        //if this is not a reference view and if diff'ing feature is enabled
        if (showDiff && referenceDocument !=  document && referenceDocument != null){
        	//System.out.println("rendered child name:" + document.controller.getRenderedChildName());
        	//enable transparency
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.TRANSPARENCY_LEVEL));
        	referenceDocument.getViewerPane().getViewer().paintObjects(g2);
        	//disable transparency
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.f));

        }
        paintObjects(g2);
        paintHandle(g2);
        paintPoints(g2);
        paintPointNumbers(g2);

        // Restore original transform
        g2.setClip(null);
        g2.setTransform(originalTransform);
        g2.setStroke(new BasicStroke(1));

        paintOrigin(g2);
        
        //draw text saying it's synched view, if it is synched
        /*if (document.getViewerPane().syncAltEnabled()){
        	g2.setColor(Color.black);
        	g2.fillRect(8, 8, 55, 15);
            g2.setColor(Color.green);
            g2.drawString("Synched",10,20);        	
        }
        else{
        	g2.setColor(Color.black);
        	g2.fillRect(8, 8, 73, 15);
        	g2.setColor(Color.red);
            g2.drawString("Unsynched",10,20);
        }*/
        
        if (showText) {
	        g2.setFont(Theme.SMALL_BOLD_FONT);
	    	g2.setColor(Color.gray);
	    	g2.drawString("M:" + document.getMonitorNo() + ",A:" + document.getDocumentName(), 10, 15);
        }
    }


    public void paintObjects(Graphics2D g) {
        if (currentVisualizer != null)
            currentVisualizer.draw(g, outputValues);
    }

    private void paintPoints(Graphics2D g) {
        if (showPoints && IGeometry.class.isAssignableFrom(valuesClass)) {
            // TODO Create a dynamic iterator that combines all output values into one flat sequence.
            LinkedList<nodebox.graphics.Point> points = new LinkedList<nodebox.graphics.Point>();
            for (Object o : outputValues) {
                IGeometry geo = (IGeometry) o;
                points.addAll(geo.getPoints());
            }
            PointVisualizer.drawPoints(g, points);
        }
    }


    private void paintPointNumbers(Graphics2D g) {
        if (!showPointNumbers) return;
        g.setFont(Theme.SMALL_MONO_FONT);
        g.setColor(Color.BLUE);
        int index = 0;

        if (IGeometry.class.isAssignableFrom(valuesClass)) {
            for (Object o : outputValues) {
                IGeometry geo = (IGeometry) o;
                for (nodebox.graphics.Point pt : geo.getPoints())
                    paintPointNumber(g, pt, index++);
            }
        } else if (nodebox.graphics.Point.class.isAssignableFrom(valuesClass)) {
            for (Object o : outputValues)
                paintPointNumber(g, (nodebox.graphics.Point) o, index++);
        }
    }

    private void paintPointNumber(Graphics2D g, nodebox.graphics.Point pt, int number) {
        if (pt.isOnCurve()) {
            g.setColor(Color.BLUE);
        } else {
            g.setColor(Color.RED);
        }
        g.drawString(number + "", (int) (pt.x + 3), (int) (pt.y - 2));
    }

    public void paintOrigin(Graphics2D g) {
        if (showOrigin) {
            int x = (int) Math.round(getViewX());
            int y = (int) Math.round(getViewY());
            g.setColor(Color.DARK_GRAY);
            g.drawLine(x, 0, x, getHeight());
            g.drawLine(0, y, getWidth(), y);
        }
    }

    public void paintHandle(Graphics2D g) {
        if (hasVisibleHandle()) {
            // Create a canvas with a transparent background.
            nodebox.graphics.Canvas canvas = new nodebox.graphics.Canvas();
            canvas.setBackground(new nodebox.graphics.Color(0, 0, 0, 0));
            CanvasContext ctx = new CanvasContext(canvas);
            try {
                handle.draw(ctx);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ctx.getCanvas().draw(g);
        }
    }

    //overrided methods
    @Override
    public void setViewTransform(double viewX, double viewY, double viewScale) {
    	for (NodeBoxDocument doc : document.getDocumentGroup()){
    		Viewer viewer = doc.getViewerPane().getViewer();
    		_setViewTransform(viewX, viewY, viewScale, viewer);
    	}
    }

    private class PopupHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
        	//this prevents popup from showing up when selecting document for cartesian product
            //if (document.getAppFrame().getGlassPane().getSelectionMode())
             //	return;

            if (e.isPopupTrigger()) {
                showPopup(e);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showPopup(e);
            }
        }

        public void showPopup(MouseEvent e) {
            if (!e.isPopupTrigger()) return;
            viewerMenu.show(Viewer.this, e.getX(), e.getY());
        }
    }


    private class ResetViewAction extends AbstractAction {
        private ResetViewAction() {
            super("Reset View");
        }

        public void actionPerformed(ActionEvent e) {
            resetViewTransform();
        }
    }

}
