package nodebox.client;

import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferencePanel extends JDialog implements ActionListener {

    private final Application application;
    private final Preferences preferences;
    private JCheckBox enableDeviceSupportCheck;
    private JCheckBox enableNetworkSupportCheck;
    private JCheckBox enableAutosandboxCheck;
    private JCheckBox enableProductOfGraphs;
    private JCheckBox enableSubtractiveEncoding;

    
    private JRadioButton radioButtons[] = new JRadioButton[6];

    ButtonGroup buttonGroup;
    //int currentMonitorArrangement = 0; //default value

    public PreferencePanel(Application application, Window owner) {
        super(owner, "Preferences");
        this.application = application;
        preferences = Preferences.userNodeForPackage(Application.class);
        JPanel rootPanel = new JPanel(new BorderLayout(10, 10));
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        //disabled experimental features for now (Shumon November 21, 2013)
        JLabel experimental = new JLabel("Experimental Features");
        experimental.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        experimental.setMinimumSize(new Dimension(300, 20));
        experimental.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        contentPanel.add(experimental);

        enableDeviceSupportCheck = new JCheckBox("Device Support");
        enableDeviceSupportCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(enableDeviceSupportCheck);

        enableNetworkSupportCheck = new JCheckBox("Network Support");
        enableNetworkSupportCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(enableNetworkSupportCheck);

                
        //added by shumon August 10, 2014
        JLabel designGallery = new JLabel("Alternatives");
        designGallery.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        designGallery.setMinimumSize(new Dimension(300, 20));
        designGallery.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        contentPanel.add(designGallery);
        
        enableAutosandboxCheck = new JCheckBox("Autosandbox");
        enableAutosandboxCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(enableAutosandboxCheck);
        
        enableProductOfGraphs = new JCheckBox("Product of Graphs in Design Gallery");
        enableProductOfGraphs.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(enableProductOfGraphs);
        
        enableSubtractiveEncoding = new JCheckBox("Subractive Encoding in Diffrence Visualizations");
        enableSubtractiveEncoding.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(enableSubtractiveEncoding);
        //-----------------------------
        
        //added by shumon November 21, 2013
        JLabel monitorArrangement = new JLabel("Monitor Arrangement");
        monitorArrangement.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        monitorArrangement.setMinimumSize(new Dimension(300, 20));
        monitorArrangement.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        contentPanel.add(monitorArrangement);
        
        buttonGroup = new ButtonGroup();
        
        radioButtons[0] = new JRadioButton("1x1");
        radioButtons[0].setAlignmentX(Component.LEFT_ALIGNMENT);
        radioButtons[0].setSelected(true);
        buttonGroup.add(radioButtons[0]);
        contentPanel.add(radioButtons[0]);
        
        radioButtons[1] = new JRadioButton("1x2");
        radioButtons[1].setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonGroup.add(radioButtons[1]);
        contentPanel.add(radioButtons[1]);
        
        radioButtons[2] = new JRadioButton("1x3");
        radioButtons[2] .setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonGroup.add(radioButtons[2]);
        contentPanel.add(radioButtons[2]);
        
        radioButtons[3] = new JRadioButton("2x1");
        radioButtons[3].setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonGroup.add(radioButtons[3]);
        contentPanel.add(radioButtons[3]);

        radioButtons[4] = new JRadioButton("2x2");
        radioButtons[4].setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonGroup.add(radioButtons[4]);
        contentPanel.add(radioButtons[4]);

        radioButtons[5] = new JRadioButton("2x3");
        radioButtons[5].setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonGroup.add(radioButtons[5]);
        contentPanel.add(radioButtons[5]);
        
        rootPanel.add(contentPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 10, 10));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        buttonPanel.add(cancelButton);
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(this);
        buttonPanel.add(saveButton);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(saveButton);

        readPreferences();

        setContentPane(rootPanel);
        setMinimumSize(new Dimension(300, 100));
        setResizable(false);
        pack();
    }

    private boolean isDeviceSupportEnabled() {
        return Boolean.valueOf(preferences.get(Application.PREFERENCE_ENABLE_DEVICE_SUPPORT, "false"));
    }

    private void setEnableDeviceSupport(boolean enabled) {
        application.ENABLE_DEVICE_SUPPORT = enabled;
        preferences.put(Application.PREFERENCE_ENABLE_DEVICE_SUPPORT, Boolean.toString(enabled));
    }

    private boolean isNetworkSupportEnabled() {
        return Boolean.valueOf(preferences.get(Application.PREFERENCE_ENABLE_NETWORK_SUPPORT, "false"));
    }

    
    private void setEnableNetworkSupport(boolean enabled) {
        application.ENABLE_NETWORK_SUPPORT = enabled;
        preferences.put(Application.PREFERENCE_ENABLE_NETWORK_SUPPORT, Boolean.toString(enabled));
    }

    private void setMonitorArrangment(int arrangment) {
        application.GLOBAL_MONITOR_ARRANGEMENT = arrangment;
        preferences.put(Application.PREFERENCE_MONITOR_ARRANGEMENT, Integer.toString(arrangment));
    }
    
    private void setEnableAutosandbox(boolean enabled) {
        application.ENABLE_AUTOSANDBOX = enabled;
        preferences.put(Application.PREFERENCE_ENABLE_AUTOSANDBOX, Boolean.toString(enabled));
    }
    
    private void setEnableProductOfGraphs(boolean enabled) {
        application.ENABLE_PRODUCT_OF_GRAPHS = enabled;
        preferences.put(Application.PREFERENCE_ENABLE_PRODUCT_OF_GRAPHS, Boolean.toString(enabled));
    }
    
    private void setEnableSubtractiveEncoding(boolean enabled) {
        application.ENABLE_SUBTRACTIVE_ENCODING = enabled;
        preferences.put(Application.PREFERENCE_ENABLE_SUBTRACTIVE_ENCODING, Boolean.toString(enabled));
    }
    
    private boolean isAutosandboxEnabled() {
        return Boolean.valueOf(preferences.get(Application.PREFERENCE_ENABLE_AUTOSANDBOX, "false"));
    }
    
    private boolean isProductOfGraphsEnabled() {
        return Boolean.valueOf(preferences.get(Application.PREFERENCE_ENABLE_PRODUCT_OF_GRAPHS, "false"));
    }
    
    
    private boolean isSubtractiveEncodingEnabled() {
        return Boolean.valueOf(preferences.get(Application.PREFERENCE_ENABLE_SUBTRACTIVE_ENCODING, "false"));
    }
    
    private int getMonitorArrangment() {
        return Integer.valueOf(preferences.get(Application.PREFERENCE_MONITOR_ARRANGEMENT, "0"));
    }
    
    private void readPreferences() {
        enableDeviceSupportCheck.setSelected(isDeviceSupportEnabled());
        enableNetworkSupportCheck.setSelected(isNetworkSupportEnabled());
        enableAutosandboxCheck.setSelected(isAutosandboxEnabled());
        enableProductOfGraphs.setSelected(isProductOfGraphsEnabled());
        enableSubtractiveEncoding.setSelected(isSubtractiveEncodingEnabled());

    	
    	try{
        	radioButtons[getMonitorArrangment()].setSelected(true);
    	}
    	catch (Exception e){
    		System.err.println("out of bounds array");
    	}

    }

    public void actionPerformed(ActionEvent actionEvent) {
    	
        boolean changed = false;

        if (isDeviceSupportEnabled() != enableDeviceSupportCheck.isSelected()) {
            setEnableDeviceSupport(enableDeviceSupportCheck.isSelected());
            changed = true;
        }
        if (isNetworkSupportEnabled() != enableNetworkSupportCheck.isSelected()) {
            setEnableNetworkSupport(enableNetworkSupportCheck.isSelected());
            changed = true;
        }
        
        if (isAutosandboxEnabled() != enableAutosandboxCheck.isSelected()) {
        	setEnableAutosandbox(enableAutosandboxCheck.isSelected());
            changed = true;
        }
        
        if (isProductOfGraphsEnabled() != enableProductOfGraphs.isSelected()) {
        	setEnableProductOfGraphs(enableProductOfGraphs.isSelected());
            changed = true;
        }
        
        if (isSubtractiveEncodingEnabled() != enableSubtractiveEncoding.isSelected()) {
        	setEnableSubtractiveEncoding(enableSubtractiveEncoding.isSelected());
            changed = true;
        }
        
        int selectedButtonIndex = getSelectedButtonIndex();
        if (getMonitorArrangment() != selectedButtonIndex) {
        	setMonitorArrangment(selectedButtonIndex);
            changed = true;
        }
        
        if (changed) {
            try {
                preferences.flush();
                // rebuild monitor array
                Application.getInstance().getCurrentAppFrame().setMonitorArrangement(getMonitorArrangment());
                Application.getInstance().getCurrentAppFrame().buildMonitorArray(true);
            } catch (BackingStoreException e) {
                throw new RuntimeException(e);
            }
        }
        dispose();
    }

	private int getSelectedButtonIndex() {
		for (int i = 0; i < radioButtons.length; i++){
    		if (radioButtons[i].isSelected()){
    			return i;
    			
    		}
    	}
		return -1;
	}
}
