package nodebox.client;

import nodebox.ui.NButton;
import nodebox.ui.Pane;
import nodebox.ui.PaneHeader;
import nodebox.ui.PaneView;

import java.awt.*;

import javax.swing.BorderFactory;

public class PortPane extends Pane {

    private final PaneHeader paneHeader;
    private final PortView portView;
    private final NodeBoxDocument document;

    public PortPane(NodeBoxDocument document) {
        this.document = document;
        setLayout(new BorderLayout());
        paneHeader = new PaneHeader("Ports");
        NButton metadataButton = new NButton("Metadata", getClass().getResourceAsStream("/port-metadata.png"));
        metadataButton.setActionMethod(this, "editMetadata");
        paneHeader.add(metadataButton);
        portView = new PortView(this, document);
        add(paneHeader, BorderLayout.NORTH);
        add(portView, BorderLayout.CENTER);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(168, 168, 168)));
    }

    public PortView getPortView() {
        return portView;
    }

    public Pane duplicate() {
        return new PortPane(document);
    }

    public PaneHeader getPaneHeader() {
        return paneHeader;
    }

    public PaneView getPaneView() {
        return portView;
    }

    public void editMetadata() {
        document.editMetadata();
    }

    public void setHeaderTitle(String title) {
        paneHeader.setTitle(title);
    }

}
