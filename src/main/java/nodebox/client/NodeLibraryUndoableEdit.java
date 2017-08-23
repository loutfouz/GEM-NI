package nodebox.client;

import undo.AbstractUndoableEdit;
import undo.CannotRedoException;
import undo.CannotUndoException;
import nodebox.node.NodeLibrary;

//import javax.swing.undo.AbstractUndoableEdit;
//import javax.swing.undo.CannotRedoException;
//import javax.swing.undo.CannotUndoException;

/**
 * An undoable edit happening to the node library.
 */
public class NodeLibraryUndoableEdit extends AbstractUndoableEdit {

    public NodeBoxDocument document;
    public String command;
    public UndoState undoState, redoState;

    /**
     * The UndoState captures the current state of the document.
     * <p/>
     * Because the NodeLibrary and all objects below it are immutable the UndoState just has to retain a reference
     * to the given NodeLibrary.
     */
    public class UndoState {
        public  NodeLibrary nodeLibrary;
        public final String activeNetworkPath;
        public final String activeNodeName;

        public UndoState(NodeLibrary nodeLibrary, String activeNetworkPath, String activeNodeName) {
            this.nodeLibrary = nodeLibrary;
            this.activeNetworkPath = activeNetworkPath;
            this.activeNodeName = activeNodeName;
        }
    }

    public NodeLibraryUndoableEdit(NodeBoxDocument document, String command) {
        this.document = document;
        this.command = command;
        undoState = saveState();
    }
    
    public NodeLibraryUndoableEdit(NodeBoxDocument document, String command, String activeNetwork, String activeNodeName) {
        this.document = document;
        this.command = command;
        undoState = copyState(activeNetwork, activeNodeName);
    }
    
    @Override
    public String getPresentationName() {
        return command;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        if (redoState == null)
            redoState = saveState();
        restoreState(undoState);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        restoreState(redoState);
    }

    public UndoState saveState() {
    	//System.out.println("Doc: " + document.getDocumentNo() +"," + document.getNodeLibrary().getName() + ","+ document.getActiveNetworkPath() + "," + document.getActiveNodeName());
        return new UndoState(document.getNodeLibrary(), document.getActiveNetworkPath(), document.getActiveNodeName());
    }
    
    public UndoState copyState(String activeNetwork, String activeNodeName) {
    	//System.out.println("Copy Doc: " + document.getDocumentNo() +"," + document.getNodeLibrary().getName() + ","+ document.getActiveNetworkPath() + "," + document.getActiveNodeName());

        return new UndoState(document.getNodeLibrary(), activeNetwork, activeNodeName);
    }

    public void restoreState(UndoState state) {
        document.restoreState(state.nodeLibrary, state.activeNetworkPath, state.activeNodeName);
    }
    
    ////added by shumon
    //these two functions are different from original in that a nodelibrary is passed with a saved state
    //this is required for undo copying, where a library from an earlier state is preserved rather than a library from current state
    public NodeLibraryUndoableEdit(NodeLibrary library, NodeBoxDocument document, String command, String activeNetwork, String activeNodeName) {
        this.document = document;
        this.command = command;
        undoState = copyState(library, activeNetwork, activeNodeName);
    }
    
    public UndoState copyState(NodeLibrary library, String activeNetwork, String activeNodeName) {
    	//System.out.println("Copy Doc: " + document.getDocumentNo() +"," + document.getNodeLibrary().getName() + ","+ document.getActiveNetworkPath() + "," + document.getActiveNodeName());

        return new UndoState(library, activeNetwork, activeNodeName);
    }
}
