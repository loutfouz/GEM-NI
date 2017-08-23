package nodebox.client;

import nodebox.node.Node;
import nodebox.ui.Platform;
import nodebox.util.FileUtils;

import javax.swing.*;

//import javax.swing.undo.UndoManager;
import undo.UndoManager;

import org.python.google.common.collect.ImmutableList;









import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The main menu bar for the NodeBox application.
 */
public class NodeBoxMenuBar extends JMenuBar {

    private NodeBoxDocument document;
    private ArrayList<JMenu> recentFileMenus = new ArrayList<JMenu>();
    private static Preferences recentFilesPreferences = Preferences.userRoot().node("/nodebox/recent");
    private static Logger logger = Logger.getLogger("nodebox.client.NodeBoxMenuBar");
    private JMenuItem showConsoleItem;

    //private UndoManager undoManager;
    public UndoAction undoAction;
    public RedoAction redoAction;
    
    public GlobalUndoAction globalUndoAction;
    public GlobalRedoAction globalRedoAction;
    
    //alternative menu items
    private JMenuItem editableItem;
    private JMenuItem sandboxItem;
    private JMenuItem synchedNodePositionItem;
    public JMenu referenceSubmenu; //of menuItems
    private JMenuItem diffItem;
    //public JMenuItem historyItem;
    
    private JMenuItem monitorJamItem;	  //enable/disable jamming of features on the monitor
    private JMenuItem monitorEditableItem;	  //enable/disable jamming unlocking on all alternatives of the monitor
    private JMenuItem monitorNetworkViewDiffItem; //enable/disable jamming network diff visualization on all alternatives of the monitor
    
    public NodeBoxMenuBar() {
        this(null);
    }

    public NodeBoxMenuBar(NodeBoxDocument document) {
        this.document = document;
        //if (document != null)
            //this.undoManager = document.getUndoManager();
        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new NewWindowAction());
        fileMenu.add(new NewAltAction());
        fileMenu.addSeparator();
        fileMenu.add(new OpenWorkspaceAction());
        fileMenu.add(new OpenAction());
        JMenu recentFileMenu = new JMenu("Open Recent");
        recentFileMenus.add(recentFileMenu);
        buildRecentFileMenu();
        fileMenu.add(recentFileMenu);
        fileMenu.addSeparator(); 
        fileMenu.add(new SaveWorkspaceAction());
        fileMenu.add(new SaveWorkspaceAsAction());
        fileMenu.add(new SaveAsAction());
        fileMenu.add(new RevertAction());
        fileMenu.addSeparator();
        fileMenu.add(new CloseWorkSpaceAction());
        fileMenu.add(new CloseDocumentAction());
        fileMenu.addSeparator();
        fileMenu.add(new CodeLibrariesAction());
        fileMenu.add(new DocumentPropertiesAction());
        fileMenu.addSeparator();
        fileMenu.add(new ExportAction());
        fileMenu.add(new ExportRangeAction());
        fileMenu.add(new ExportMovieAction());
        fileMenu.addSeparator();
        fileMenu.add(new Quit());
        add(fileMenu);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        editMenu.add(undoAction = new UndoAction());
        editMenu.add(redoAction = new RedoAction());
        editMenu.addSeparator();
        editMenu.add(globalUndoAction = new GlobalUndoAction());
        editMenu.add(globalRedoAction = new GlobalRedoAction());
        editMenu.addSeparator();
        editMenu.add(new CutAction());
        editMenu.add(new CopyAction());
        editMenu.add(new PasteAction());
        editMenu.add(new SynchPasteAction());
        editMenu.addSeparator();
        editMenu.add(new DeleteAction());
        if (!Platform.onMac()) {
            editMenu.addSeparator();
            editMenu.add(new PreferencesAction());
        }
        add(editMenu);

        // Node menu
        JMenu nodeMenu = new JMenu("Node");
        nodeMenu.add(new NewNodeAction());
        nodeMenu.add(new ReloadAction());
        nodeMenu.add(new PlayPauseAction());
        nodeMenu.add(new RewindAction());
        //nodeMenu.add(newLibraryAction);
        add(nodeMenu);
        
        // Alternative menu
        JMenu alternativeMenu = new JMenu("Alternative");
        editableItem = alternativeMenu.add(new JCheckBoxMenuItem(new EditableAction()));
        setEditableChecked(true); //true in the beginning
        sandboxItem = alternativeMenu.add(new JCheckBoxMenuItem(new SandboxAction()));
        synchedNodePositionItem = alternativeMenu.add(new JCheckBoxMenuItem(new SynchedNodePositionAction()));
        synchedNodePositionItem.getModel().setSelected(true); //true in the beginning
        referenceSubmenu = new JMenu("Reference");
        alternativeMenu.add(referenceSubmenu);
        alternativeMenu.add(new HistoryAction());
        alternativeMenu.add(new HikerAction());
        diffItem = alternativeMenu.add(new JCheckBoxMenuItem(new DiffAction()));
        diffItem.setSelected(true);
        alternativeMenu.addSeparator();
        alternativeMenu.add(new CloneAction());
        alternativeMenu.add(new ClearAction());
        alternativeMenu.add(new MinimizeAlternativeAction());
        alternativeMenu.add(new FocusAlternativeAction());
        add(alternativeMenu);
        
        // Monitor Menu
        JMenu monitorMenu = new JMenu("Monitor");
        monitorJamItem = monitorMenu.add(new JCheckBoxMenuItem(new MonitorJamAction()));
        monitorEditableItem = monitorMenu.add(new JCheckBoxMenuItem(new MonitorEditableAction()));
        monitorNetworkViewDiffItem = monitorMenu.add(new JCheckBoxMenuItem(new MonitorNetworkViewDiffAction()));
        monitorEditableItem.setEnabled(false); 	//is disabled in the beginning
        setMonitorEditableSelected(true); 		//but selected
        monitorNetworkViewDiffItem.setEnabled(false); //is disabled in the beginning
        setMonitorNetworkViewDiffSelected(true);	//but selected
        
        add(monitorMenu);

        // Window menu
        JMenu windowMenu = new JMenu("Window");
        windowMenu.add(new MinimizeAction());
        windowMenu.add(new ZoomAction());
        showConsoleItem = windowMenu.add(new JCheckBoxMenuItem(new ShowConsoleAction()));
        setShowConsoleChecked(Application.getInstance() != null && Application.getInstance().isConsoleOpened());
        windowMenu.add(new ShowMinimizedAlternativesAction());
        windowMenu.addSeparator();
        windowMenu.add(new BringAllToFrontAction());
        // TODO Add all active windows.
        add(windowMenu);

        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(new GettingStartedAction());
        helpMenu.add(new HelpAndSupportAction());
        helpMenu.add(new ReportAnIssueAction());

        helpMenu.addSeparator();
        if (!Platform.onMac()) {
            helpMenu.add(new AboutAction());
        }
        helpMenu.add(new CheckForUpdatesAction());
        helpMenu.add(new NodeboxSiteAction());
        add(helpMenu);
    }

    public void updateUndoRedoState() {
        undoAction.update();
        redoAction.update();
    }
    
    public void updateGlobalUndoRedoState() {
        globalUndoAction.update();
        globalRedoAction.update();
    }


    public NodeBoxDocument getDocument() {
        return document;
    }

    public boolean hasDocument() {
        return document != null;
    }

    public  void addRecentFile(File f) {
        File canonicalFile;
        try {
            canonicalFile = f.getCanonicalFile();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not get canonical file name", e);
            return;
        }
        ArrayList<File> fileList = getRecentFiles();
        // If the recent file was already in the list, remove it and add it to the top.
        // If the list did not contain the file, the remove call does nothing.
        fileList.remove(canonicalFile);
        fileList.add(0, canonicalFile);
        writeRecentFiles(fileList);
        buildRecentFileMenu();
    }

    public static void writeRecentFiles(ArrayList<File> fileList) {
        int i = 1;
        for (File f : fileList) {
            try {
                recentFilesPreferences.put(String.valueOf(i), f.getCanonicalPath());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not get canonical file name", e);
                return;
            }
            i++;
            if (i > 10) break;
        }
        try {
            recentFilesPreferences.flush();
        } catch (BackingStoreException e) {
            logger.log(Level.WARNING, "Could not write recent files preferences", e);
        }
    }

    public static ArrayList<File> getRecentFiles() {
        ArrayList<File> fileList = new ArrayList<File>(10);
        for (int i = 1; i <= 10; i++) {
            File file = new File(recentFilesPreferences.get(String.valueOf(i), ""));
            if (file.exists()) {
                fileList.add(file);
            }
        }
        return fileList;
    }

    private  void buildRecentFileMenu() {
        for (JMenu recentFileMenu : recentFileMenus) {
            recentFileMenu.removeAll();
            for (File f : getRecentFiles()) {
                recentFileMenu.add(new OpenRecentAction(f));
            }
        }
    }

    public void setShowConsoleChecked(boolean checked) {
        showConsoleItem.getModel().setSelected(checked);
    }

    //// Actions ////

    public abstract class AbstractDocumentAction extends AbstractAction {
        @Override
        public boolean isEnabled() {
            return NodeBoxMenuBar.this.hasDocument() && super.isEnabled();
        }
    }


    public static class NewWindowAction extends AbstractAction {
        public NewWindowAction() {
            putValue(NAME, "New Workspace");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_N));
        }

        public void actionPerformed(ActionEvent e) {
        	// the new window uses the global monitor arrangement and will create a document per monitor
            Application.getInstance().createNewAppFrame(Application.GLOBAL_MONITOR_ARRANGEMENT, true);
        }
    }

    //creates new alternative
    public  class CloneAction extends AbstractAction {
        public CloneAction() {
            putValue(NAME, "Create Branch");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_B));
        }

        public void actionPerformed(ActionEvent e) {
        	//still don't know what to do here
			ApplicationFrame af = document.getAppFrame();
        	NodeBoxDocument cloneDoc = af.createNewAlternativeFromCurrentDoc();
        	//log cloning
        	af.logClone(cloneDoc);
        }

    }
    //clears document
    public  class ClearAction extends AbstractAction {
        public ClearAction() {
            putValue(NAME, "Clear Alternative");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_D));
        }

        public void actionPerformed(ActionEvent e) {
        	//still don't know what to do here
        	/*ApplicationFrame af = document.getAppFrame();
        	int oldAltNum = new Integer(af.getCurrentDocument().getAltNum());
        	document = new NodeBoxDocument(af, oldAltNum);
        	af.getDocuments()[oldAltNum] = document;
        	document.getNetworkView().repaint();
        	document.getPortView().repaint();
        	document.getViewerPane().getViewer().repaint();
        	*/

        	document.setNewDocument();
        	//log clear
        	document.getAppFrame().log("Clear alternative", document.getDocumentName(), String.valueOf(document.getDocumentNo()));


        }
    }
    
    //minimizes document
    public  class MinimizeAlternativeAction extends AbstractAction {
        public MinimizeAlternativeAction() {
            putValue(NAME, "Minimize Alternative");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_I));
        }

        public void actionPerformed(ActionEvent e) {
			document.getAppFrame().minimizeAlternative(document);
        }
    }
    public  class FocusAlternativeAction extends AbstractAction {
        public FocusAlternativeAction() {
            putValue(NAME, "Focus Alternative");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_F));
        }

        public void actionPerformed(ActionEvent e) {
			document.getAppFrame().focusAlternative(document);
        }
    }

    
    //creates new document
    public  class NewAltAction extends AbstractAction {
        public NewAltAction() {
            putValue(NAME, "New Alternative");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_T));
        }

        public void actionPerformed(ActionEvent e) {

			ApplicationFrame af = document.getAppFrame();
        	NodeBoxDocument newDoc = af.createNewAlternativeFromCurrentDoc();        	
        	newDoc.setNewDocument();
        	//log new
        	document.getAppFrame().log("New alternative", newDoc.getDocumentName(),String.valueOf(newDoc.getDocumentNo()), "menuBar");

        }
    }
    public class OpenAction extends AbstractAction {
        public OpenAction() {
        	putValue(NAME, "Open Alternative...");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_O));
        }

        public void actionPerformed(ActionEvent e) {
            File chosenFile = FileUtils.showOpenDialog(getDocument().getDocumentFrame(), NodeBoxDocument.lastFilePath, "ndbx", "NodeBox Document");
            if (chosenFile != null) {
            	//opening always to the first monitor by default
            	document.openDocument(chosenFile); //alternative one
            }
        }
    }
    
    //made more sense if this was a static class, like before I modified it
    public class OpenRecentAction extends AbstractAction {
        private File file;


        public OpenRecentAction(File file) {
            this.file = file;
            putValue(NAME, file.getName());
        }

        public void actionPerformed(ActionEvent e) {
        	System.out.println("alt num = " + document.getDocumentNo());
        	//opening always to the first monitor by default
            document.openDocument(file); //this is broken for now, opens in first alternative
        }
    }


    public class CloseWorkSpaceAction extends AbstractDocumentAction {
        public CloseWorkSpaceAction() {
            putValue(NAME, "Close Workspace");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_W));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().close();
        }
    }
    public class CloseDocumentAction extends AbstractDocumentAction {
        public CloseDocumentAction() {
            putValue(NAME, "Close Alternative");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_W,Event.SHIFT_MASK));

        }

        public void actionPerformed(ActionEvent e) {
        	if (JOptionPane.OK_OPTION == javax.swing.JOptionPane.showConfirmDialog(document.getAppFrame(), "Are you sure you want to delete this alternative?", "Are you sure you want to delete this alternative?", JOptionPane.OK_CANCEL_OPTION)){
            	document.closeDocument();
                document.getAppFrame().log("Delete alternative", document.getDocumentName(), String.valueOf(document.getDocumentNo()), "menuBar");
        	}
        }
    } 
    
    public class OpenWorkspaceAction extends AbstractDocumentAction {
        public OpenWorkspaceAction() {
            putValue(NAME, "Open Workspace ...");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_O, Event.SHIFT_MASK));
        }

        public void actionPerformed(ActionEvent e) {
        	new WorkspaceManager(null).load();
        }
    }

    public class SaveWorkspaceAction extends AbstractDocumentAction {
        public SaveWorkspaceAction() {
            putValue(NAME, "Save Workspace");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_S));
        }

        public void actionPerformed(ActionEvent e) {
        	new WorkspaceManager(document.getAppFrame()).save(false);
        }
    }
    
    public class SaveWorkspaceAsAction extends AbstractDocumentAction {
        public SaveWorkspaceAsAction() {
            putValue(NAME, "Save Workspace as ...");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_S));
        }

        public void actionPerformed(ActionEvent e) {
        	new WorkspaceManager(document.getAppFrame()).save(true);
        }
    }

    public class SaveAsAction extends AbstractDocumentAction {  	
        public SaveAsAction() {
            putValue(NAME, "Save Alternative As...");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_S, Event.SHIFT_MASK));
        }

        public void actionPerformed(ActionEvent e) {
            document.saveAs();
        }
    }
    
    
    public class RevertAction extends AbstractDocumentAction {
        public RevertAction() {
            putValue(NAME, "Revert to Saved");
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().revert();
        }
    }

    public class CodeLibrariesAction extends AbstractDocumentAction {
        public CodeLibrariesAction() {
            putValue(NAME, "Code Libraries...");
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().showCodeLibraries();
        }
    }

    public class DocumentPropertiesAction extends AbstractDocumentAction {
        public DocumentPropertiesAction() {
            putValue(NAME, "Document Properties...");
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().showDocumentProperties();
        }
    }

    public class ExportAction extends AbstractDocumentAction {
        public ExportAction() {
            putValue(NAME, "Export...");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_E));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().doExport();
        }
    }

    public class ExportRangeAction extends AbstractDocumentAction {
        public ExportRangeAction() {
            putValue(NAME, "Export Range...");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_E, Event.SHIFT_MASK));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().exportRange();
        }
    }

    public class ExportMovieAction extends AbstractDocumentAction {
        public ExportMovieAction() {
            putValue(NAME, "Export Movie...");
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().exportMovie();
        }
    }

    public static class Quit extends AbstractAction {
        public Quit() {
            putValue(NAME, "Quit");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_Q, Event.SHIFT_MASK));
        }

        public void actionPerformed(ActionEvent e) {
            Application.getInstance().quit();
        }
    }

    public class UndoAction extends AbstractDocumentAction {
        private String undoText = UIManager.getString("AbstractUndoableEdit.undoText");

        public UndoAction() {
            putValue(NAME, "Local " + undoText);
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_Z));
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
            //Component c = getDocument().getDocumentFrame().getFocusOwner();
            getDocument().undo();
            updateUndoRedoState();
            getDocument().getAppFrame().resetGlobalUndoRedo();
            NodeBoxDocument.centerAndZoomAllZoomableViews(document, true);
        }

        public void update() {
        //Component c = getDocument().getDocumentFrame().getFocusOwner();
            if (document.undoManager != null && document.undoManager.canUndo()) {
                setEnabled(true);
                putValue(Action.NAME, document.undoManager.getUndoPresentationName());
            } else {
                setEnabled(false);
                putValue(Action.NAME, undoText);
            }
            //System.out.println("setEnabled:"+ this.enabled);

        }
    }

    public class RedoAction extends AbstractDocumentAction {
        private String redoText = UIManager.getString("AbstractUndoableEdit.redoText");

        public RedoAction() {
            putValue(NAME, "Local " + redoText);
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_Z, Event.SHIFT_MASK));
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
            //Component c = getDocument().getDocumentFrame().getFocusOwner();
            getDocument().redo();
            updateUndoRedoState();
            getDocument().getAppFrame().resetGlobalUndoRedo();
            NodeBoxDocument.centerAndZoomAllZoomableViews(document, true);

            
        }

        public void update() {
            //Component c = getDocument().getDocumentFrame().getFocusOwner();
            if (document.undoManager != null && document.undoManager.canRedo()) {
                setEnabled(true);
                putValue(Action.NAME, document.undoManager.getRedoPresentationName());
            } else {
                setEnabled(false);
                putValue(Action.NAME, redoText);
            }
        }
    }
    
    public class GlobalUndoAction extends AbstractDocumentAction {
        private String globalUndoText = UIManager.getString("AbstractUndoableEdit.undoText");

        public GlobalUndoAction() {
            putValue(NAME, "Global " + globalUndoText);
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_A));
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
        	document.getAppFrame().globallyUndo();
            updateGlobalUndoRedoState();
            NodeBoxDocument.centerAndZoomAllZoomableViews(document, true);
        }

        public void update() {
        	boolean canGloballyUndo = document.getAppFrame().canGloballyUndo();
                if (canGloballyUndo) {
                    setEnabled(true);
                    putValue(Action.NAME, "Global " + globalUndoText);
                } else {
                    setEnabled(false);
                    putValue(Action.NAME, "Global " + globalUndoText);
                }
        }
    }

    public class GlobalRedoAction extends AbstractDocumentAction {
        private String globalRedoText = UIManager.getString("AbstractUndoableEdit.redoText");

        public GlobalRedoAction() {
            putValue(NAME, "Global " + globalRedoText);
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_A, Event.SHIFT_MASK));
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
        	document.getAppFrame().globallyRedo();
            updateGlobalUndoRedoState();
            NodeBoxDocument.centerAndZoomAllZoomableViews(document, true);
        }

        public void update() {
        	boolean canGloballyRedo = document.getAppFrame().canGloballyRedo();
                if (canGloballyRedo) {
                    setEnabled(true);
                    putValue(Action.NAME, "Global " + globalRedoText);
                } else {
                    setEnabled(false);
                    putValue(Action.NAME, "Global " + globalRedoText);
                }
        }
    }

    public class CutAction extends AbstractDocumentAction {
        public CutAction() {
            putValue(NAME, "Cut");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_X));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().cut();
        }
    }

    public class CopyAction extends AbstractDocumentAction {
        public CopyAction() {
            putValue(NAME, "Copy");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_C));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().copy();
        }
    }

    public class PasteAction extends AbstractDocumentAction {
        public PasteAction() {
            putValue(NAME, "Paste");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_V));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().paste();
        }
    }
    
    public class SynchPasteAction extends AbstractDocumentAction {
        public SynchPasteAction() {
            putValue(NAME, "Merge");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_M));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().merge();
        }
    }

    public class DeleteAction extends AbstractDocumentAction {
        public DeleteAction() {
            putValue(NAME, "Delete");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().getNetworkView().deleteSelection();
        }
    }

    public static class PreferencesAction extends AbstractAction {
        public PreferencesAction() {
            putValue(NAME, "Preferences");
        }

        public void actionPerformed(ActionEvent e) {
            Application.getInstance().showPreferences();
        }
    }

    public class NewNodeAction extends AbstractDocumentAction {
        public NewNodeAction() {
            putValue(NAME, "Create New Node...");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_N, Event.SHIFT_MASK));
        }

        public void actionPerformed(ActionEvent e) {
            document.showNodeSelectionDialog();
        }
    }

    public class ReloadAction extends AbstractDocumentAction {
        public ReloadAction() {
            putValue(NAME, "Reload");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_R));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().reload();
        }
    }

    public class PlayPauseAction extends AbstractDocumentAction {
        public PlayPauseAction() {
            putValue(NAME, "Play/Pause");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_P, Event.META_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            getDocument().getAnimationManager().toggleAnimation();
        }
    }

    public class RewindAction extends  AbstractDocumentAction {
        public RewindAction() {
            putValue(NAME, "Rewind");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_P, Event.META_MASK | Event.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            getDocument().getAnimationManager().doRewind();
        }
    }



//    public class NewLibraryAction extends AbstractAction {
//        public NewLibraryAction() {
//            putValue(NAME, "New Library...");
//        }
//
//        public void actionPerformed(ActionEvent e) {
//            String libraryName = JOptionPane.showInputDialog(NodeBoxDocument.this, "Enter the name for the new library", "Create New Library", JOptionPane.QUESTION_MESSAGE);
//            if (libraryName == null || libraryName.trim().length() == 0) return;
//            createNewLibrary(libraryName);
//        }
//    }

    public class MinimizeAction extends AbstractDocumentAction {
        public MinimizeAction() {
            putValue(NAME, "Minimize");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.META_MASK));
        }

        public void actionPerformed(ActionEvent e) {
            getDocument().getDocumentFrame().setState(Frame.ICONIFIED);
        }
    }

    public class ZoomAction extends AbstractDocumentAction {
        public ZoomAction() {
            putValue(NAME, "Zoom");
        }

        public void actionPerformed(ActionEvent e) {
            // TODO: Implement
            Toolkit.getDefaultToolkit().beep();
        }
    }

    public class ShowConsoleAction extends AbstractDocumentAction {
        public ShowConsoleAction() {
            putValue(NAME, "Show Console");
        }

        public void actionPerformed(ActionEvent e) {
            Application instance = Application.getInstance();
            if (instance.isConsoleOpened())
                instance.hideConsole();
            else
                instance.showConsole();
        }
    }
    
    public class ShowMinimizedAlternativesAction extends AbstractDocumentAction {
        public ShowMinimizedAlternativesAction() {
            putValue(NAME, "Show Minimized Alternatives");
        }

        public void actionPerformed(ActionEvent e) {
            final MinimizedAlternativesDialog minAltDialog = new MinimizedAlternativesDialog(document.getAppFrame());

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					//center and zoom relative to all
					minAltDialog.gallery.update(true);

				}
			});
        }
    }


    public class BringAllToFrontAction extends AbstractDocumentAction {
        public BringAllToFrontAction() {
            putValue(NAME, "Bring All to Front");
        }

        public void actionPerformed(ActionEvent e) {
            // TODO: Implement
            Toolkit.getDefaultToolkit().beep();
        }
    }

    public static class AboutAction extends AbstractAction {
        public AboutAction() {
            super("About");
        }

        public void actionPerformed(ActionEvent e) {
            Application.getInstance().showAbout();
        }
    }


    public static class NodeboxSiteAction extends AbstractAction {
        public NodeboxSiteAction() {
            putValue(NAME, "NodeBox Site");
        }

        public void actionPerformed(ActionEvent e) {
            Platform.openURL("http://nodebox.net/");
        }
    }


    public static class GettingStartedAction extends AbstractAction {
        public GettingStartedAction() {
            super("Getting Started");
        }

        public void actionPerformed(ActionEvent e) {
            Platform.openURL("http://nodebox.net/node/documentation/tutorial/getting-started.html");
        }
    }

    public static class HelpAndSupportAction extends AbstractAction {
        public HelpAndSupportAction() {
            super("Help and Support");
        }

        public void actionPerformed(ActionEvent e) {
            Platform.openURL("http://nodebox.net/node/documentation/");
        }
    }

    public static class ReportAnIssueAction extends AbstractAction {
        public ReportAnIssueAction() {
            super("Report an Issue...");
        }

        public void actionPerformed(ActionEvent e) {
            Platform.openURL("https://github.com/nodebox/nodebox/issues");
        }
    }

    public static class CheckForUpdatesAction extends AbstractAction {
        public CheckForUpdatesAction() {
            super("Check for Updates...");
        }

        public void actionPerformed(ActionEvent e) {
            Application.getInstance().getUpdater().checkForUpdates();
        }
    }
    
    //-----------------------------------
    //monitor menu actions
    //-----------------------------------
    //unlock
    public class MonitorEditableAction extends AbstractDocumentAction {
        public MonitorEditableAction() {
            putValue(NAME, "Editable");
        }

        public void actionPerformed(ActionEvent e) {
            document.getAppFrame().makeEditableMonitor(monitorEditableItem.isSelected());
        
        }
    }
    
    public void setMonitorEditableSelected(boolean checked) {
        monitorEditableItem.getModel().setSelected(checked);
    }
    
    public void setMonitorEditableEnabled(boolean checked) {
        monitorEditableItem.setEnabled(checked);
    }
    
    public void toggleMonitorEditableMenuItem(){
    	setMonitorEditableSelected(!monitorEditableItem.isSelected());
    }
    
    //network diff toggle
    public class MonitorNetworkViewDiffAction extends AbstractDocumentAction {
        public MonitorNetworkViewDiffAction() {
            putValue(NAME, "Network Diffing");
        }

        public void actionPerformed(ActionEvent e) {
        	boolean checked = monitorNetworkViewDiffItem.isSelected();
        	document.getAppFrame().setNetworkDiffMonitor(checked);
			document.getAppFrame().getGlassPane().repaint(); //update glasspane when diffing is toggled
        }
    }
    
    public void setMonitorNetworkViewDiffSelected(boolean checked) {
        monitorNetworkViewDiffItem.getModel().setSelected(checked);
    }
    
    public void setMonitorNetworkViewDiffEnabled(boolean checked) {
    	monitorNetworkViewDiffItem.setEnabled(checked);
    }


    //monitor jam
    public class MonitorJamAction extends AbstractDocumentAction {
        public MonitorJamAction() {
            putValue(NAME, "Jam");
        }

        public void actionPerformed(ActionEvent e) {
        	jamAndSoftEnforce();
        }

    }
	public void jamAndSoftEnforce() {
		//have to do it for all menus in all monitor alternatives
    	boolean checked = monitorJamItem.isSelected();
    	document.getAppFrame().jamMonitor(checked);
    	//enforce the jam protocol here for every alternative in the monitor
    	//see if monitor is unlocked
    	if (checked){ //only if enabling jamming
    		document.getAppFrame().makeEditableMonitor(monitorEditableItem.isSelected());
    		document.getAppFrame().setNetworkDiffMonitor(monitorEditableItem.isSelected());
			document.getAppFrame().getGlassPane().repaint(); //update glasspane when button is toggled

    	}
	}
	public void jamAndHardEnforce() {
		//have to do it for all menus in all monitor alternatives
    	boolean checked = monitorJamItem.isSelected();
    	document.getAppFrame().jamMonitor(checked);
    	//enforce the jam protocol here for every alternative in the monitor
    	//we jam the unlock state even if jam is disabled,
    	//this is hard jam and it's used when e.g., and alternative was dragged so every state needs to be enforced
    	document.getAppFrame().makeEditableMonitor(monitorEditableItem.isSelected());
    	document.getAppFrame().setNetworkDiffMonitor(monitorEditableItem.isSelected());
		document.getAppFrame().getGlassPane().repaint(); //update glasspane when button is toggled

	}
    
    public void setMonitorJamChecked(boolean checked) {
        monitorJamItem.getModel().setSelected(checked);
    }
    
    public boolean getMonitorJamChecked() {
        return monitorJamItem.isSelected();
    }
    //alternative menu actions
    //unlock
    public class EditableAction extends AbstractDocumentAction {
        public EditableAction() {
            putValue(NAME, "Editable");
        }

        public void actionPerformed(ActionEvent e) {
            boolean state = document.getAlternativePaneHeader().toggleEditableState();
            document.getAppFrame().disableAllSandboxButtons();
            //log
            ApplicationFrame appFrame = document.getAppFrame();
			appFrame.log(document.getDocumentName(), String.valueOf(document.getDocumentNo()), "Set Editable", String.valueOf(state), "Alternative Menu");
        }
    }
    public void setEditableChecked(boolean checked) {
        editableItem.getModel().setSelected(checked);
        if (document != null && document.getAppFrame() != null)
        	document.getAppFrame().repaintGlassPane();

    }
    public void toggleEditableMenuItem(){
    	 setEditableChecked(!editableItem.isSelected());

    }
    
    public class SandboxAction extends AbstractDocumentAction {
        public SandboxAction() {
            putValue(NAME, "Sandbox");
        }

        public void actionPerformed(ActionEvent e) {
        	boolean state = sandboxItem.isSelected(); 
        	document.getAppFrame().sandboxSetEnabled(state);
        	//log
            ApplicationFrame appFrame = document.getAppFrame();
			appFrame.log(document.getDocumentName(), String.valueOf(document.getDocumentNo()), "Sandbox", String.valueOf(state), "Alternative Menu");

        }
    } 
    public void setSandboxChecked(boolean checked) {
    	sandboxItem.getModel().setSelected(checked);

    }

    //alternative diff action
    public class DiffAction extends AbstractDocumentAction {
        public DiffAction() {
            putValue(NAME, "Network Diffing");
        }

        public void actionPerformed(ActionEvent e) {
        	boolean state = diffItem.isSelected(); 
        	document.getAlternativePaneHeader().setDiffButtonToggleSelected(state);
			document.getAppFrame().getGlassPane().repaint(); //update glasspane when button is toggled

        }
    } 

    
    public void setDiffChecked(boolean checked) {
    	diffItem.getModel().setSelected(checked);

    }
    public void setDiffVisible(boolean checked) {
    	diffItem.setVisible(checked);

    }    

    //synch node position
    public class SynchedNodePositionAction extends AbstractDocumentAction {
        public SynchedNodePositionAction() {
            putValue(NAME, "Node Positions Synched");
        }

        public void actionPerformed(ActionEvent e) {
            document.getAlternativePaneHeader().toggleSynchedNodePositionState();
        }
    }
    public void setSynchedMoveChecked(boolean checked) {
    	synchedNodePositionItem.getModel().setSelected(checked);
    }
    public void toggleSynchedNodePositionItem(){
    	setSynchedMoveChecked(!synchedNodePositionItem.isSelected());
    }
    //reference
    public void addReferenceMenu(int docNum, String strDocName){
    	referenceSubmenu.add(new JCheckBoxMenuItem(new ReferenceAction(docNum, strDocName)));

    }
    public class ReferenceAction extends AbstractDocumentAction {
    	private int docNum = -1;
        public ReferenceAction(int docNum, String strDocName) {
        	this.docNum = docNum;
        	try{
            	//String strDocName = document.appFrame.getDocuments().get(docNo).getDocumentName();
                putValue(NAME, strDocName);
        	}
        	catch (Exception e)
        	{
                putValue(NAME, "empty");
        		System.err.println("Error in ReferenceAction!");
        	}
        }

        public void actionPerformed(ActionEvent e) {
           document.getAppFrame().switchReference(docNum);
        }
    }
    
    public class HistoryAction extends AbstractDocumentAction {

    	  public HistoryAction() {
              putValue(NAME, "Show History");
          }

          public void actionPerformed(ActionEvent e) {
        	  ApplicationFrame af = document.getAppFrame();
        	  System.out.println("activeHistoryDialog=" + af.activeHistoryDialog);
        	  if (af.activeHistoryDialog == null){
        		  document.getAlternativePaneHeader().showHistoryDialog();
        	  }
          }
      }
    public class HikerAction extends AbstractDocumentAction {

  	  public HikerAction() {
            putValue(NAME, "Show Design Gallery");
        }

        public void actionPerformed(ActionEvent e) {
        	//no power sets if less than 1 document, i.e., if documents are identical
			ArrayList<NodeBoxDocument> docs = new ArrayList<NodeBoxDocument>();
			docs.add(document);
			docs.add(document);
        	new GalleryDialog(document.getAppFrame(), docs); 
        }
    }
    
}
