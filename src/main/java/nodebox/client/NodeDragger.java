package nodebox.client;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import org.python.google.common.collect.ImmutableList;

import nodebox.node.Node;

public class NodeDragger {

	private final GlassPane glassPane;
	private final ApplicationFrame app;
	private NetworkView startNetworkView; 		//starting network view
	private NetworkView oldNetworkView = null; 	//last crossed network view
	private boolean selectionNotEmpty = false;
	private boolean enabled = false;
	
	public NodeDragger(GlassPane glassPane, ApplicationFrame app) {
		this.glassPane = glassPane;
		this.app = app;
		reset();
	}



	public void start(MouseEvent me) {
		if (app.getReferenceDocument() == null) return; //do not do dragging if not in reference view because you can't see the result
		Component c = GlassPane.findComponentUnderGlassPaneAt(me.getPoint(), app.getRootPane());
		if (c instanceof NetworkView){
			startNetworkView = (NetworkView) c;
			Iterable<String> iterable = startNetworkView.getSelectedNodeNames();
			selectionNotEmpty = iterable.iterator().hasNext();
			if (selectionNotEmpty){ 
				oldNetworkView = startNetworkView;
				enabled = true;
				
			}
		}
		
	}
	
	public void drag(MouseEvent me) {
		if (!selectionNotEmpty) return;
		Component c = GlassPane.findComponentUnderGlassPaneAt(me.getPoint(), app.getRootPane());
		if (c instanceof NetworkView){
    		NetworkView currentNetworkView = (NetworkView) c;
    		//if not previously selected view, and not the start view and if the view is unlocked
    		if (currentNetworkView != oldNetworkView && currentNetworkView != startNetworkView && currentNetworkView.getDocument().getAlternativePaneHeader().isEditable()){
				currentNetworkView.setBorder(currentNetworkView.nodeDragBorder);
				oldNetworkView.setBorder(null);
				oldNetworkView = currentNetworkView;
			}
		}
	}

	public void end() {
		if (selectionNotEmpty && startNetworkView != oldNetworkView){ //if something was selected and dragged to another network view (different from start network view)
			//find this node in the oldNetworkView
	
			if (oldNetworkView.draggedNodesNames == null)
				oldNetworkView.draggedNodesNames = new HashSet<String>();
			Iterator iter = startNetworkView.getSelectedNodeNames().iterator();
			while (iter.hasNext()){
				String nodeName = (String) iter.next();
				oldNetworkView.draggedNodesNames.add(nodeName);
			}
			//System.out.println("END:"+oldNetworkView.draggedNodesNames);
	        
			//paint dragged nodes
			oldNetworkView.repaint();
			//repaint connectors
			glassPane.repaint();
		}
    	
		if (oldNetworkView != null)
			oldNetworkView.setBorder(null);
		oldNetworkView = null;
		startNetworkView = null;
		selectionNotEmpty = false;
		enabled = false;
	}
	public boolean getEnabled(){
		return enabled;
	}

	private void reset() {			
	
	}
	
}