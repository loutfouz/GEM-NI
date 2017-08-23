package nodebox.client;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import nodebox.ui.Platform;

import org.w3c.dom.Document;
import org.w3c.dom.Element;


public class WorkspaceManager {
	
	public static String WORKSPACE_FILE_NAME = "workspace";
	public static String WORKSPACE_FILE_EXTENSION = "xml";
	
	private static Logger logger = Logger.getLogger("nodebox.client.NodeBoxMenuBar");
	
	private ApplicationFrame appFrame = null;
	private JFileChooser chooser;

	
	public WorkspaceManager(ApplicationFrame appFrame) {
		chooser = new JFileChooser();
		chooser.setDialogTitle("Please choose workspace folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setMultiSelectionEnabled(false);
	    chooser.setCurrentDirectory(new java.io.File("."));
	    // by default use given work space. if we open a new workspace then
	    // this variable may change
	    this.appFrame = appFrame;
	}

	public boolean save(boolean setWorkspacePath) {
		// check if the workspace is already saved
		if (appFrame.getWorkspacePath() == null || setWorkspacePath)
			if (setWorkspacePath(JFileChooser.SAVE_DIALOG) == null) // stop if set workspace path fails
				return false;

		return save(new File(appFrame.getWorkspacePath()));
	}
	
	public void load() {
		String workspacePath;

		if ((workspacePath = setWorkspacePath(JFileChooser.OPEN_DIALOG)) == null)
			return;
		
		File workspaceFile = new File(workspacePath + "/" + WORKSPACE_FILE_NAME + "." + WORKSPACE_FILE_EXTENSION);
		load(workspaceFile, workspacePath);
	}

	private String setWorkspacePath(int dialogType) {
		int choosen = JFileChooser.CANCEL_OPTION;

		switch (dialogType) {
		case JFileChooser.SAVE_DIALOG:
			choosen = chooser.showSaveDialog(appFrame);
			break;
		case JFileChooser.OPEN_DIALOG:
			choosen = chooser.showOpenDialog(appFrame);
			break;
		}

	    if (choosen != JFileChooser.APPROVE_OPTION)
	    	return null;

	    String workspacePath = chooser.getSelectedFile().getAbsolutePath();

	    // also set workspace path to the window if possible
	    if (appFrame != null)
	    	appFrame.setWorkspacePath(workspacePath);

	    return workspacePath;
	}
	
	private void load(File workspaceFile, String workspacePath) {
		loadWorkspaceFile(workspaceFile, workspacePath);
	}
	
	public void autoSave(){
		//using this format you can save file with date time as part of file name (because it doesn't have forbidden characters)
	    SimpleDateFormat ft = new SimpleDateFormat ("E yyyy.MM.dd 'at' hh-mm-ss a zzz");
    	Date date = new Date();
    	
    	//check if autosave directory exists, create it if not
    	File autoSaveDir =  new File(System.getProperty("user.dir"), "autosave");
    	if (!autoSaveDir.exists())
    		autoSaveDir.mkdir();
    	
    	//save current autosave
    	String autoSavePath = "autosave/" + appFrame.hashCode() + "." + ft.format(date);
    	File currAutoSaveDir = new File(System.getProperty("user.dir"), autoSavePath);
    	currAutoSaveDir.mkdir();
    	save(currAutoSaveDir);
	}
	private boolean save(File workspaceFolder) {
		// create a new workspace or save an existing one?
		if (!workspaceFolder.exists()) {
			try {
				workspaceFolder.createNewFile();
			} catch (IOException e) {
				JOptionPane.showMessageDialog(appFrame, "An error occurred while saving the workspace. workspace folder could not be created.", "NodeBox", JOptionPane.ERROR_MESSAGE);
	            logger.log(Level.SEVERE, "An error occurred while saving the workspace.", e);
				return false;
			}
		}
		
		File workspaceFile = new File(workspaceFolder.getAbsoluteFile() + "/" + WORKSPACE_FILE_NAME + "." + WORKSPACE_FILE_EXTENSION);
		if (workspaceFile.exists()) {
			if(JOptionPane.showConfirmDialog(appFrame, "Overwrite existing workspace?", "Overwrite existing workspace?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				// abort saving
				return false; 
		}
		
		// always clean workspace folder
		System.out.println("cleandir=" + workspaceFolder);
		cleanDirectory(workspaceFolder);

		// create folder structure
		File[] monitors = new File[appFrame.monitorDocumentMap.size()];
		for (int i = 0; i < appFrame.monitorDocumentMap.size(); i++) {
			monitors[i] = new File(workspaceFolder.getAbsolutePath() + "/" + (i + 1));
			if (!monitors[i].mkdir()) {
				JOptionPane.showMessageDialog(appFrame, "An error occurred while saving the workspace. workspace subfolder could not be created.", "NodeBox", JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}
		
		// save alternatives in folders
		for (int i = 0; i < appFrame.monitorDocumentMap.size(); i++) {
			for (NodeBoxDocument document : appFrame.monitorDocumentMap.get(i)) {
				File newFile = new File(monitors[i].getAbsolutePath() + "/" + document.getDocumentName());
				document.setDocumentFile(newFile);
				if (!document.save())
					JOptionPane.showMessageDialog(appFrame, "An error occurred while saving the workspace. Document [" + document.getDocumentName() + "] could not be saved.", "NodeBox", JOptionPane.ERROR_MESSAGE);
			}
		}

		//save minimized alternatives if there are any
		ArrayList<NodeBoxDocument> minimizedAlternatives = appFrame.getMinimizedDocuments();
		if (minimizedAlternatives.size() > 0){
			// create minimized alternatives folder
			File minAlts = new File(workspaceFolder.getAbsolutePath() + "/" + "min");
			if (!minAlts.mkdir()) {
				JOptionPane.showMessageDialog(appFrame, "An error occurred while saving the workspace. workspace subfolder could not be created (minimized alternatives).", "NodeBox", JOptionPane.ERROR_MESSAGE);
				return false;
			}
			
			// save minimized alternatives in min alts folder
			for (NodeBoxDocument document : minimizedAlternatives) {
				File newFile = new File(minAlts + "/" + document.getDocumentName());
				document.setDocumentFile(newFile);
				if (!document.save())
					JOptionPane.showMessageDialog(appFrame, "An error occurred while saving the workspace. Minimized alternative Document [" + document.getDocumentName() + "] could not be saved.", "NodeBox", JOptionPane.ERROR_MESSAGE);
			}
		}
		
		// write XML file to save all workspace information
		saveWorkspaceFile(workspaceFile, appFrame.monitorDocumentMap);
		return true;
	}
	
	private void loadWorkspaceFile(File file, String workspacePath) {
		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        try {
        	boolean monitorsSection = false;
        	boolean propertiesSection = false;
        	int currentMonitor = 0;
        	NodeBoxDocument document = null; // reference to latest document
			XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new FileReader(file));

	        while (true) {
	            int eventType = reader.next();
	            if (eventType == XMLStreamConstants.START_ELEMENT) {
	            	String tagName = reader.getLocalName();

	                if (tagName.equals("properties"))
	                	propertiesSection = true;
	            	
	                if (tagName.equals("monitors"))
	                	monitorsSection = true;
	                
                	if (propertiesSection && tagName.equals("property")) {
                		
                		Object[] property = loadProperty(reader);
                		
                		if (property[0].equals("MONITOR_ARRANGEMENT")) {
                			// create new window with right monitor arrangement but no documents inside the monitors
                			appFrame = Application.getInstance().createNewAppFrame((Integer)property[1], false);
                			appFrame.setWorkspacePath(workspacePath);
                		}
                		
                		if (appFrame == null) {
                    		JOptionPane.showMessageDialog(appFrame, "An error occurred while loading the workspace. No monitor arrangement found.", "NodeBox", JOptionPane.ERROR_MESSAGE);
                    		return;
                    	}
                	}
                	
                	if (monitorsSection && tagName.equals("monitor"))
                		currentMonitor = Integer.valueOf(reader.getAttributeValue(null, "number"));
                	
                	if (monitorsSection && tagName.equals("document")) {
                		// load documents into array
                		String documentPath = reader.getAttributeValue(null, "path");
                		File documentFile = new File(getFolderPathFromFile(file, false) + documentPath);
                		// create a new document in the monitor
                		document = appFrame.createNewDocument(currentMonitor);
                		appFrame.setCurrentDocument(document);
                		document.openDocument(documentFile);
                	}
         	
                	// document properties
                	if (monitorsSection && propertiesSection && tagName.equals("property")) {
                		Object[] property = loadProperty(reader);
                		
                		if (property[0].equals("locked"))
                			document.getAlternativePaneHeader().setEditableState((Boolean)property[1]);
                		
                		if (property[0].equals("sandboxed"))
                			document.getAlternativePaneHeader().setSandboxButtonToggleEnable((Boolean)property[1]);
                		
                		if (property[0].equals("movement"))
                			document.getAlternativePaneHeader().setSynchedNodePositionState((Boolean)property[1]);
                	}
	            }
	            else if (eventType == XMLStreamConstants.END_ELEMENT) {
	            	String tagName = reader.getLocalName();

	                if (tagName.equals("monitors"))
	                	monitorsSection = false;
	                
	                if (tagName.equals("properties"))
	                	propertiesSection = false;
	                
	                if (tagName.equals("workspace"))
	                    break;
	            }
	        }
	        
	        try{
	        	String separator = (Platform.onWindows()) ? "\\" : "/";
		        File f = new File(workspacePath + separator + "min");
		        for (File minAlt : f.listFiles()){
		        	File documentFile = new File(getFolderPathFromFile(file, false) + separator + "min" + separator + minAlt.getName());
		        	System.out.println(getFolderPathFromFile(file, false) + separator + "min" + separator + minAlt.getName());
            		// create a new document in the monitor
            		document = appFrame.createNewDocument(currentMonitor);
            		appFrame.setCurrentDocument(document);
            		document.openDocument(documentFile);
            		appFrame.minimizeAlternative(document);
		        }
	        }
	        catch (java.lang.NullPointerException e){} //folder doesn't exist (there are no minimized alternatives)
	    	

	        
	        // finally set first document as active document and update panels and globals
	        appFrame.setCurrentDocument(appFrame.getDocumentGroup().get(0));
	        appFrame.updateAlternativesPanels(true);
	        appFrame.resetGlobals();
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
			        appFrame.getCurrentDocument().getNetworkView().centerAndZoom(true); //center and zoom network after workspace is loaded 
			        //to front and repaint
			        appFrame.toFront();
			        appFrame.repaint();
				}
			});
			
			appFrame.log("Load workspace", workspacePath);

	        
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(appFrame, "An error occurred while loading the workspace. No workspace file found.", "NodeBox", JOptionPane.ERROR_MESSAGE);
            logger.log(Level.SEVERE, "An error occurred while loading the workspace.", e);
			return;
		} catch (XMLStreamException e) {
			JOptionPane.showMessageDialog(appFrame, "An error occurred while loading the workspace.", "NodeBox", JOptionPane.ERROR_MESSAGE);
            logger.log(Level.SEVERE, "An error occurred while loading the workspace.", e);
			return;
		}
	}
	
	private void saveWorkspaceFile(File file, ArrayList<ArrayList<NodeBoxDocument>> documents) {
       try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.newDocument();

            // build the header
            Element rootElement = doc.createElement("workspace");
            doc.appendChild(rootElement);

            // build workspace properties
            Element properties = doc.createElement("properties");
            rootElement.appendChild(properties);
            createProperty(doc, properties, "MONITOR_ARRANGEMENT", (Object)appFrame.getMonitorArrangement());

            // create monitors
            Element monitors = doc.createElement("monitors");
            Element monitorsArray[] = new Element[documents.size()];
            rootElement.appendChild(monitors);
            for (int i = 0; i < documents.size(); i++) {
            	monitorsArray[i] = doc.createElement("monitor");
            	monitorsArray[i].setAttribute("number", String.valueOf(i));
            	// append documents for each monitor
            	Element documentsElement = doc.createElement("documents");
            	monitorsArray[i].appendChild(documentsElement);
            	monitors.appendChild(monitorsArray[i]);
            	for (int j = 0; j < documents.get(i).size(); j++) {
            		NodeBoxDocument document = documents.get(i).get(j);
            		Element documentElement = doc.createElement("document");
            		documentElement.setAttribute("path", getPathRelativeToFolder(document.getDocumentFile(), file.getParentFile()));
            		documentsElement.appendChild(documentElement);
            		// create document properties
            		Element documentPropertiesElement = doc.createElement("properties");
            		documentElement.appendChild(documentPropertiesElement);
            		createProperty(doc, documentPropertiesElement, "locked", (Object)document.getAlternativePaneHeader().isEditable());
            		createProperty(doc, documentPropertiesElement, "sandboxed", (Object)document.getAlternativePaneHeader().isSandboxed());
            		createProperty(doc, documentPropertiesElement, "movement", (Object)document.getAlternativePaneHeader().synchedNoveMoveIsEnabled());
            	}
            }
            
            DOMSource domSource = new DOMSource(doc);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer serializer = tf.newTransformer();
            serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            serializer.transform(domSource, new StreamResult(file));
            
			appFrame.log("Save workspace", file.getAbsolutePath());

        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
	}
	
	private void createProperty(Document doc, Element parent, String name, Object value) {
		Element child = doc.createElement("property");
		parent.appendChild(child);
		child.setAttribute("name", name);
		child.setAttribute("value", String.valueOf(value));
		child.setAttribute("class", value.getClass().getName());
	}

	private Object[] loadProperty(XMLStreamReader reader) {
		final String classname = reader.getAttributeValue(null, "class");
		Object[] result = new Object[2];
		Class<?> cls = null;
		
		try {
			cls = Class.forName(classname);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		
		result[0] = reader.getAttributeValue(null, "name");
		
		if (cls.equals(Integer.class))
			result[1] = (Object)Integer.valueOf(reader.getAttributeValue(null, "value"));
		if (cls.equals(Boolean.class))
			result[1] = (Object)Boolean.valueOf(reader.getAttributeValue(null, "value"));
		if (cls.equals(String.class))
			result[1] = (Object)String.valueOf(reader.getAttributeValue(null, "value"));
		
		return result;
	}
	
	private static void cleanDirectory(File dir) {
		if (!dir.exists())
			return;
	    for (File file: dir.listFiles()) {
	        if (file.isDirectory()) {
	        	cleanDirectory(file);
	        }
	        file.delete();
	    }
	}
	
	private static String getPathRelativeToFolder(File file, File folder) {
		String filePath = file.getAbsolutePath();
		String folderPath = folder.getAbsolutePath();
		if (filePath.startsWith(folderPath))
			return filePath.substring(folderPath.length() + 1);
		else
			return null;
	}
	
	private static String getFolderPathFromFile(File file, boolean ignoreLastSeperator) {
		final int suffix = ignoreLastSeperator ? 0 : 1;
		return (String) file.getAbsolutePath().subSequence(0, file.getAbsolutePath().lastIndexOf(File.separator) + suffix);
	}
}
