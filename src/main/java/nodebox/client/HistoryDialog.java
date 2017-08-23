package nodebox.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import nodebox.ui.ExceptionDialog;
import nodebox.ui.Theme;


public class HistoryDialog extends JDialog implements ActionListener, ChangeListener {

	private NodeBoxDocument originalDocument;
	private NodeBoxDocument previewDocument;

	private JButton buttonClose;
	private JButton buttonCreate;
	
	private JSlider slider;
	
	private HistoryList historyList;
	
	ApplicationFrame appFrame;

	boolean eventCameFromList = false;
	public HistoryDialog(Frame frame, NodeBoxDocument originalDocument, NodeBoxDocument previewDocument) {
		super(frame);

		this.previewDocument = previewDocument;
		this.originalDocument = originalDocument;

		historyList = new HistoryList();
		final int historySize = historyList.getModel().getSize();
		
		if (historySize <= 0) {
			JOptionPane.showMessageDialog(frame, "No history available.", "NodeBox", JOptionPane.ERROR_MESSAGE);
			this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			return;
		}
		
		// unsynchronize preview document
		previewDocument.ungroup();
		
		appFrame = originalDocument.getAppFrame();

		JPanel monitorPanel = (JPanel) appFrame.monitorPanel.getComponent(appFrame.getMonitorForDocument(originalDocument));

		// get monitor height and width (always use the first monitor, since they are all equally sized)
		final int width = monitorPanel.getWidth() / 2;
		final int height = (int)(monitorPanel.getHeight() / 1.1);

		final Dimension minimumSize = new Dimension(600, 600);
		
		// preferred size is half of the screen size
		setMinimumSize(minimumSize);
		setPreferredSize(new Dimension(width, height));

		// center the dialog relative to the affected monitor panel
		setLocationRelativeTo(monitorPanel);

		JPanel content = new JPanel();
		content.setLayout(new BorderLayout());

		JPanel bottomPanel = new JPanel(new GridLayout(2, 1));
		JPanel sliderPanel = new JPanel(new GridLayout(1, 1));
		JPanel buttonPanel = new JPanel(new GridLayout(1, 2));

		bottomPanel.add(sliderPanel);
		bottomPanel.add(buttonPanel);
		
		// the history preview document is always in the first row 
		previewDocument.setFirstRow(true);
		// just in case rebuild the root pane (maybe it was not a first row document)
		previewDocument.buildRootPanel(); 
		
		// prepare document (remove change listener etc)
		for (PropertyChangeListener listener : previewDocument.getSplitInner().getPropertyChangeListeners())
			previewDocument.getSplitInner().removePropertyChangeListener(listener);
		
		for (PropertyChangeListener listener : previewDocument.getSplitOuter().getPropertyChangeListeners())
			previewDocument.getSplitOuter().removePropertyChangeListener(listener);
		
		// set split divider position (4, 2, 4)
		previewDocument.getSplitOuter().setDividerLocation(0.4);
		previewDocument.getSplitInner().setDividerLocation(0.8);
		
		// remove alternative panel
		((JComponent) previewDocument.getSplitInner().getComponent(0)).remove(previewDocument.getAlternativePaneHeader());
		// remove option bar from viewer pane
		previewDocument.getViewerPane().remove(0);
		
		//make the preview viewer brighter
		previewDocument.getViewerPane().getViewer().setBackground(Theme.ACTIVE_COLOR);

		// split view also for history and root panel
		CustomSplitPane splitPanel = new CustomSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(historyList), previewDocument.getRootPanel());
		splitPanel.setDividerLocation(200);

		buttonPanel.setAlignmentX(RIGHT_ALIGNMENT);
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 0));

		// add list
		content.add(splitPanel, BorderLayout.CENTER);
		content.add(bottomPanel, BorderLayout.SOUTH);

		// add buttons
		buttonClose = new JButton("Close");
		buttonCreate = new JButton("Create Alternative");
		buttonClose.addActionListener(this);
		buttonCreate.addActionListener(this);

		buttonPanel.add(buttonCreate);
		buttonPanel.add(buttonClose);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
		
		// add slider
		slider = new JSlider(JSlider.HORIZONTAL, 0, historySize - 1, historySize - 1);
		slider.addChangeListener(this);
		
		sliderPanel.add(slider);
		
		setContentPane(content);
		
		// adding our own on close function
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we) {
				// unset current dialog instance
				appFrame.activeHistoryDialog = null;
			}
		});
		
		//when you load the history dialog center and zoom both model and preview documents
		//but it must be involved later otherwise it doesnt work
 		NodeBoxDocument.centerAndZoomAllZoomableViews(previewDocument,false);
	}


	// own JList implementation
	private class HistoryList extends JList implements ListSelectionListener {

		private DefaultListModel listModel;
		private int undoPosition;

		public HistoryList() {
			// set own renderer
			setCellRenderer(new EditsCellRenderer());

			// create list from undo manager
			listModel = new DefaultListModel();
			for (String edit : previewDocument.getUndoManager().getEditStringList())
				listModel.addElement(edit);

			setModel(listModel);
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			addListSelectionListener(this);

			// set edit position
			undoPosition = listModel.size() - 1;
			setSelectedIndex(undoPosition);
		}

		@Override
		public void valueChanged(ListSelectionEvent e) {
			// undo or redo changes (depending on previous position)
			if (getSelectedIndex() < undoPosition) {
				for (int i = undoPosition; i > getSelectedIndex(); i--)
					previewDocument.undo();
			}
			else {
				for (int i = undoPosition; i < getSelectedIndex(); i++)
					previewDocument.redo();
			}
			// save current position
			undoPosition = getSelectedIndex();
			eventCameFromList = true;
			try{
				slider.setValue(undoPosition);
			}
			catch (Exception exception){}
	 		NodeBoxDocument.centerAndZoomAllZoomableViews(previewDocument, false);
		}
	}
	
	private class EditsCellRenderer extends JLabel implements ListCellRenderer {
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			String command = (String)value;
			
			String html = "<html><div style=\"padding: 3px\"><b>" + command + "</b></div></html>";
            setText(html);
            setBorder(Theme.BOTTOM_BORDER);
            setOpaque(true);
            setFont(list.getFont());

            if (isSelected)
                setBackground(Theme.NODE_SELECTION_ACTIVE_BACKGROUND_COLOR);
            else
                setBackground(Theme.NODE_SELECTION_BACKGROUND_COLOR);

			return this;
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		JButton button = (JButton)e.getSource();

		if (button.equals(buttonClose)){
			this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			appFrame.log("History", "close dialog");
		}

		if (button.equals(buttonCreate)) {
			// creates new alternative from current preview alternative
			// create alternative from 
			NodeBoxDocument document = appFrame.createNewAlternativeFromDocument(previewDocument, originalDocument.getMonitorNo());
			// reset to the right document group
			document.setDocumentGroup(originalDocument.getDocumentGroup());
			appFrame.resetGlobals();
	 		NodeBoxDocument.centerAndZoomAllZoomableViews(originalDocument, true);

	 		//log
			String logSrc = "source" + ApplicationFrame.attributeDelimiter + originalDocument.getDocumentName() + ApplicationFrame.itemDelimiter + originalDocument.getDocumentNo();
			String logDest = "destination" + ApplicationFrame.attributeDelimiter + document.getDocumentName() + ApplicationFrame.itemDelimiter + document.getDocumentNo();
			appFrame.log("History", "create",logSrc, logDest);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		// update history list
		if (!eventCameFromList)
			historyList.setSelectedIndex(slider.getValue());
		else
			eventCameFromList = false;
 		NodeBoxDocument.centerAndZoomAllZoomableViews(previewDocument, false);
	}
}