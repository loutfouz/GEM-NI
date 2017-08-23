package nodebox.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * SaveDialog
 */
public class SynchPasteDialog extends JComponent {

    public static final Font messageFont = Theme.MESSAGE_FONT;
    public static final Font infoFont = Theme.INFO_FONT;

    private JDialog dialog;
    private int selectedValue;

    private OverrideAction overrideAction = new OverrideAction();
    private MergeAction mergeAction = new MergeAction();
    private CancelAction cancelAction = new CancelAction();

    JButton overrideButton, cancelButton, mergeButton;


    public SynchPasteDialog() {
        initInterface();
    }

    private void initInterface() {
        setLayout(new BorderLayout());
        //Icon dialogIcon = Application.getInstance().getImageIcon();
        //JLabel iconLabel = new JLabel(dialogIcon);
        //iconLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridLayout(2, 1, 10, 0));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel messageLabel = new JLabel("How do you want to synchronize selected node(s)?");
        messageLabel.setFont(messageFont);
        contentPanel.add(messageLabel);
        //JLabel infoLabel = new JLabel("If you don't save, your changes will be lost.");
        //infoLabel.setFont(infoFont);
        //contentPanel.add(infoLabel);
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        overrideButton = new JButton(overrideAction);
        cancelButton = new JButton(cancelAction);
        mergeButton = new JButton(mergeAction);
        buttonPanel.add(overrideButton);
        buttonPanel.add(mergeButton);
        buttonPanel.add(cancelButton);
        contentPanel.add(buttonPanel);

        //add(iconLabel, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        setSize(400, 250);
    }

    public int show(JFrame frame) {
        dialog = new JDialog(frame, "Synch Paste", true);
        Container contentPane = dialog.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(this, BorderLayout.CENTER);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.getRootPane().setDefaultButton(mergeButton);
        dialog.setVisible(true);
        dialog.dispose();
        return selectedValue;
    }

    public class OverrideAction extends AbstractAction {
        public OverrideAction() {
            putValue(NAME, "Override");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_O));
        }

        public void actionPerformed(ActionEvent e) {
            selectedValue = 0;
            dialog.setVisible(false);
        }
    }

    public class MergeAction extends AbstractAction {
        public MergeAction() {
            putValue(NAME, "Merge");
            putValue(ACCELERATOR_KEY, Platform.getKeyStroke(KeyEvent.VK_M));
        }

        public void actionPerformed(ActionEvent e) {
            selectedValue = 1;
            dialog.setVisible(false);
        }
    }

    public class CancelAction extends AbstractAction {
        public CancelAction() {
            putValue(NAME, "Cancel");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        }

        public void actionPerformed(ActionEvent e) {
            selectedValue = JOptionPane.CANCEL_OPTION;
            dialog.setVisible(false);
        }
    }


}
