package nodebox.node;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import nodebox.client.ApplicationFrame;
import nodebox.function.CoreFunctions;
import nodebox.function.FunctionLibrary;
import nodebox.function.FunctionRepository;
import nodebox.graphics.Point;
import nodebox.node.Node.Attribute;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class provides mutable access to the immutable NodeLibrary.
 * <p/>
 * This class is single-threaded. You can only access it from one concurrent thread.
 * However, the internal nodeLibrary is immutable, so you can keep looking at a NodeLibrary while the controller
 * generates a new one.
 */
public class NodeLibraryController {

    private NodeLibrary nodeLibrary;

    public static NodeLibraryController create() {
        return new NodeLibraryController(NodeLibrary.create("untitled", Node.NETWORK, NodeRepository.of(), FunctionRepository.of()));
    }

    public static NodeLibraryController create(String libraryName, NodeRepository nodeRepository, FunctionRepository functionRepository) {
        return new NodeLibraryController(NodeLibrary.create(libraryName, Node.NETWORK, nodeRepository, functionRepository));
    }

    public static NodeLibraryController withLibrary(NodeLibrary nodeLibrary) {
        return new NodeLibraryController(nodeLibrary);
    }

    public NodeLibraryController(NodeLibrary nodeLibrary) {
        this.nodeLibrary = nodeLibrary;
    }

    public NodeLibrary getNodeLibrary() {
        return nodeLibrary;
    }

    public void setNodeLibrary(NodeLibrary nodeLibrary) {
        this.nodeLibrary = nodeLibrary;
    }

    public Node getRootNode() {
        return nodeLibrary.getRoot();
    }

    public void setNodeLibraryFile(File file) {
        nodeLibrary = nodeLibrary.withFile(file);
    }

    public void setFunctionRepository(FunctionRepository functionRepository) {
        nodeLibrary = nodeLibrary.withFunctionRepository(functionRepository);
    }

    public void setProperties(Map<String, String> properties) {
        nodeLibrary = nodeLibrary.withProperties(properties);
    }

    public Node getNode(String nodePath) {
        return nodeLibrary.getNodeForPath(nodePath);
    }

/*    public void reloadFunctionLibrary(String namespace) {
        checkNotNull(namespace);
        FunctionLibrary newLibrary = nodeLibrary.getFunctionRepository().getLibrary(namespace).reload();
        functionRepository = nodeLibrary.getFunctionRepository().withLibraryAdded(newLibrary);
        nodeLibrary = nodeLibrary.withFunctionRepository(functionRepository);
    } */

    public void reloadFunctionRepository() {
        nodeLibrary.getFunctionRepository().reload();
    }

    //modified by shumon August 19, 2013
    //this is the old function modified
    public Node createNode(String parentPath, Node prototype) {
        Node parent = getNode(parentPath);
        String name = parent.uniqueName(prototype.getName());
        Node newNode = prototype.extend().withName(name);
        addNode(parentPath, newNode);
        return newNode;
    }
    
    //added by Shumon August 23, 2013
    //this is the new approach to creating node, where every unique name is determined by examining prototype names in all alternatives
    public Node createNode(String parentPath, Node prototype, String strUniqueName,  UUID uuid) {
        //Node parent = getNode(parentPath);
        String name = strUniqueName; //parent.uniqueName(prototype.getName());
        Node newNode = prototype.extend().withName(name).withUUID(uuid);
        addNode(parentPath, newNode);
        return newNode;
    }
    
  
    public void setNodeUUID(String nodePath, UUID uuid) {
    	Node newNode = getNode(nodePath).withUUID(uuid);
    	replaceNodeInPath(nodePath, newNode);
    }

    public void setNodePosition(String nodePath, Point point) {
        Node newNode = getNode(nodePath).withPosition(point);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setNodeCategory(String nodePath, String category) {
        Node newNode = getNode(nodePath).withCategory(category);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setNodeDescription(String nodePath, String description) {
        Node newNode = getNode(nodePath).withDescription(description);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setNodeImage(String nodePath, String image) {
        Node newNode = getNode(nodePath).withImage(image);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setNodeOutputType(String nodePath, String outputType) {
        Node newNode = getNode(nodePath).withOutputType(outputType);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setNodeOutputRange(String nodePath, Port.Range outputRange) {
        Node newNode = getNode(nodePath).withOutputRange(outputRange);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setNodeFunction(String nodePath, String function) {
        Node newNode = getNode(nodePath).withFunction(function);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setNodeHandle(String nodePath, String handle) {
        Node newNode = getNode(nodePath).withHandle(handle);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setRenderedChild(String parentPath, String nodeName) {
        Node newParent = getNode(parentPath).withRenderedChildName(nodeName);
        replaceNodeInPath(parentPath, newParent);
    }

    public Node addNode(String parentPath, Node node) {
        Node newParent = getNode(parentPath).withChildAdded(node);
        replaceNodeInPath(parentPath, newParent);
        // We can't return the given node argument itself because withChildAdded might have chosen a new name,
        // Instead return the child at the end of the parent's children list.
        return Iterables.getLast(newParent.getChildren());
    }

    public List<Node> pasteNodes(String parentPath, Node nodesParent, Iterable<Node> nodes) {
        return pasteNodes(parentPath, nodesParent, nodes, 4, 2);
    }

    public List<Node> pasteNodes(String parentPath, Node nodesParent, Iterable<Node> nodes, double xOffset, double yOffset) {
        Node parent = getNode(parentPath);
        Node newParent = parent.withChildrenAdded(nodesParent, nodes);
        for (Node node : Iterables.skip(newParent.getChildren(), parent.getChildren().size()))
            newParent = newParent.withChildPositionChanged(node.getName(), xOffset, yOffset);
        replaceNodeInPath(parentPath, newParent);
        return ImmutableList.copyOf(Iterables.skip(newParent.getChildren(), parent.getChildren().size()));
    }
    
    public List<Node> pasteNodesAlt(String parentPath, Node nodesParent, Iterable<Node> nodes, double xOffset, double yOffset) {
        Node parent = getNode(parentPath);
        //Node newParent = parent.withChildrenAdded(nodesParent, nodes);
        //with children added starts here
        checkArgument(parent.isNetwork(), "Node %s is not a network node.", this);
        Map<String, String> newNames = new HashMap<String, String>();
        Node newParent = parent;

        System.out.println("Nodes len:" + nodes);
        List<Node> inclusionList = new ArrayList<Node>();
        for (Node node : nodes) {
            //newParent = newParent.withChildAdded(node);
            //with child added starts here
            checkNotNull(node, "Node cannot be null.");
            checkArgument(newParent.isNetwork(), "Node %s is not a network node.", this);
            checkArgument(!node.getName().equals("root"), "A child node of a network cannot have the name 'root'.");
            //if (newParent.hasChild(node.getName())) {
                String uniqueName = Node.uniqueNameAlt(node.getName(),inclusionList); //inclusion name is necessary because newNode has not been added yet
                Node newNode = node.withName(uniqueName);//.withUUID(UUID.randomUUID());
                inclusionList.add(newNode);
                System.out.println("unique name:" + uniqueName);
           // }
            ImmutableList.Builder<Node> b = ImmutableList.builder();
            b.addAll(newParent.getChildren());
            b.add(newNode);
            newParent = newParent.newNodeWithAttribute(Attribute.CHILDREN, b.build());
            //with child added ends here
            
            //newNode = Iterables.getLast(newParent.getChildren());
            newNames.put(node.getName(), newNode.getName());
        }
        System.out.println("MAP:" + newNames);

        // TODO: Recreate published inputs?

        for (Connection c : nodesParent.getConnections()) {
            String outputNodeName = c.getOutputNode();
            String inputNodeName = c.getInputNode();

            if (newNames.containsKey(outputNodeName)) {
                outputNodeName = newNames.get(outputNodeName);
            }

            if (newParent.hasChild(outputNodeName) && newNames.containsKey(inputNodeName)) {
                inputNodeName = newNames.get(inputNodeName);

                if (newParent.hasChild(inputNodeName)) {
                    Node outputNode = newParent.getChild(outputNodeName);
                    Node inputNode = newParent.getChild(inputNodeName);
                    Port inputPort = inputNode.getInput(c.getInputPort());
                    newParent = newParent.connect(outputNode.getName(), inputNode.getName(), inputPort.getName());
                }
            }
        }        
        //with children added ends here
        
        for (Node node : Iterables.skip(newParent.getChildren(), parent.getChildren().size()))
            newParent = newParent.withChildPositionChanged(node.getName(), xOffset, yOffset);
        replaceNodeInPath(parentPath, newParent);
        return ImmutableList.copyOf(Iterables.skip(newParent.getChildren(), parent.getChildren().size()));
    }
    
    /*public List<Node> pasteNodesInAlternative(String parentPath, Node nodesParent, Iterable<Node> nodes, double xOffset, double yOffset, boolean isUniqueName) {
        Node parent = getNode(parentPath);
        //Node newParent = parent.withChildrenAdded(nodesParent, nodes);
        
        checkArgument(parent.isNetwork(), "Node %s is not a network node.", this);
        Map<String, String> newNames = new HashMap<String, String>();
        Node newParent = parent;

        for (Node node : nodes) {
            //newParent = newParent.withChildAdded(node);
            
            checkNotNull(node, "Node cannot be null.");
            checkArgument(newParent.isNetwork(), "Node %s is not a network node.", this);
            checkArgument(!node.getName().equals("root"), "A child node of a network cannot have the name 'root'.");
            if (newParent.hasChild(node.getName())) {
                System.out.println("unique name:" + Node.uniqueNameAcrossAlternatives(node.getName()) );

                String uniqueName = isUniqueName ? Node.uniqueNameAcrossAlternatives(node.getName()) : node.getName();
                node =  node.withName(uniqueName).withUUID(UUID.randomUUID());
            }
            ImmutableList.Builder<Node> b = ImmutableList.builder();
            b.addAll(newParent.getChildren());
            b.add(node);
            newParent = newParent.newNodeWithAttribute(Attribute.CHILDREN, b.build());
            
            
            Node newNode = Iterables.getLast(newParent.getChildren());
            newNames.put(node.getName(), newNode.getName());
        }

        // TODO: Recreate published inputs?

        for (Connection c : nodesParent.getConnections()) {
            String outputNodeName = c.getOutputNode();
            String inputNodeName = c.getInputNode();

            if (newNames.containsKey(outputNodeName)) {
                outputNodeName = newNames.get(outputNodeName);
            }

            if (newParent.hasChild(outputNodeName) && newNames.containsKey(inputNodeName)) {
                inputNodeName = newNames.get(inputNodeName);

                if (newParent.hasChild(inputNodeName)) {
                    Node outputNode = newParent.getChild(outputNodeName);
                    Node inputNode = newParent.getChild(inputNodeName);
                    Port inputPort = inputNode.getInput(c.getInputPort());
                    newParent = newParent.connect(outputNode.getName(), inputNode.getName(), inputPort.getName());
                }
            }
        }
        
        
        for (Node node : Iterables.skip(newParent.getChildren(), parent.getChildren().size()))
            newParent = newParent.withChildPositionChanged(node.getName(), xOffset, yOffset);
        replaceNodeInPath(parentPath, newParent);
        return ImmutableList.copyOf(Iterables.skip(newParent.getChildren(), parent.getChildren().size()));
    }*/

    public Node groupIntoNetwork(String parentPath, Iterable<Node> nodes, UUID uuid) {
        return groupIntoNetwork(parentPath, nodes, "network", uuid);
    }

    public Node groupIntoNetwork(String parentPath, Iterable<Node> nodes, String networkName, UUID uuid) {
        Node parent = getNode(parentPath);
        Node newParent = parent;
        for (Node node : nodes) {
            newParent = newParent.withChildRemoved(node.getName());
        }
        Node subnet = Node.NETWORK
                .withName(newParent.uniqueName(networkName))
                .withChildrenAdded(parent, nodes)
                .withUUID(uuid);
        List<String> nodeNames = new ArrayList<String>();

        for (Node node : subnet.getChildren()) {
            subnet = subnet.withChildReplaced(node.getName(), node.withPosition(node.getPosition().moved(-4, -2)));
            nodeNames.add(node.getName());
        }

        newParent = newParent.withChildAdded(subnet);


        Map<String, Integer> portNameOccurrences = new HashMap<String, Integer>();

        for (Connection c : parent.getConnections()) {
            if (!subnet.hasChild(c.getOutputNode()) && subnet.hasChild(c.getInputNode())) {
                Integer times = portNameOccurrences.get(c.getInputPort());
                portNameOccurrences.put(c.getInputPort(), times == null ? 1 : times + 1);
            }
        }

        // Input connections to the subnetwork.
        for (Connection c : parent.getConnections()) {
            String outputNodeName = c.getOutputNode();
            String inputNodeName = c.getInputNode();
            if (!subnet.hasChild(outputNodeName) && subnet.hasChild(inputNodeName)) {
                String portName = c.getInputPort();
                if (portNameOccurrences.get(portName) > 1)
                    portName = subnet.uniqueInputName(portName);
                subnet = subnet.publish(inputNodeName, c.getInputPort(), portName);
                newParent = newParent
                        .withChildReplaced(subnet.getName(), subnet)
                        .connect(outputNodeName, subnet.getName(), portName);
            }
        }

        // Find the most best candidate to become the subnetwork's rendered child.
        String renderedChild = findRenderedChildInSubnet(parent, subnet, parent.getRenderedChildName());

        // No suitable candidate was found, return the first found node that has an outgoing connection.
        if (renderedChild == null) {
            for (Connection c : parent.getConnections()) {
                if (subnet.hasChild(c.getOutputNode()) && !subnet.hasChild(c.getInputNode())) {
                    renderedChild = c.getOutputNode();
                    break;
                }
            }
        }

        if (renderedChild != null) {
            for (Node node : subnet.getChildren()) {
                if (node.getName().equals(renderedChild)) {
                    subnet = subnet.withRenderedChildName(node.getName());
                    newParent = newParent.withChildReplaced(subnet.getName(), subnet);
                    break;
                }
            }

            // Subnetwork output connection(s).
            for (Connection c : parent.getConnections()) {
                if (renderedChild.equals(c.getOutputNode()) && newParent.hasChild(c.getInputNode()))
                    newParent = newParent.connect(subnet.getName(), c.getInputNode(), c.getInputPort());
            }
        }

        replaceNodeInPath(parentPath, newParent);
        return getNode(Node.path(parentPath, subnet.getName()));
    }

    private String findRenderedChildInSubnet(Node parent, Node subnet, String child) {
        if (subnet.hasChild(child)) return child;
        List<String> connectedNodes = new ArrayList<String>();
        for (Connection c : parent.getConnections()) {
            if (c.getInputNode().equals(child)) {
                String outputNode = c.getOutputNode();
                if (subnet.hasChild(outputNode)) return outputNode;
                connectedNodes.add(outputNode);
            }
        }
        for (String node : connectedNodes) {
            String renderedChild = findRenderedChildInSubnet(parent, subnet, node);
            if (renderedChild != null) return renderedChild;
        }
        return null;
    }

    public void removeNode(String parentPath, String nodeName) {
        Node newParent = getNode(parentPath).withChildRemoved(nodeName);
        replaceNodeInPath(parentPath, newParent);
    }

    public void removePort(String parentPath, String nodeName, String portName) {
        Node newParent = getNode(parentPath).withChildInputRemoved(nodeName, portName);
        replaceNodeInPath(parentPath, newParent);
    }

    public void renameNode(String parentPath, String oldName, String newName) {
        Node newParent = getNode(parentPath).withChildRenamed(oldName, newName);
        replaceNodeInPath(parentPath, newParent);
    }

    public void addPort(String nodePath, String portName, String portType) {
        Node newNode = getNode(nodePath).withInputAdded(Port.portForType(portName, portType));
        replaceNodeInPath(nodePath, newNode);
    }

    public void setPortWidget(String nodePath, String portName, Port.Widget widget) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withWidget(widget);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setPortRange(String nodePath, String portName, Port.Range range) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withRange(range);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setPortMinimumValue(String nodePath, String portName, Double minimumValue) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withMinimumValue(minimumValue);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setPortMaximumValue(String nodePath, String portName, Double maximumValue) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withMaximumValue(maximumValue);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void addPortMenuItem(String nodePath, String portName, String key, String label) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withMenuItemAdded(key, label);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void removePortMenuItem(String nodePath, String portName, MenuItem menuItem) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withMenuItemRemoved(menuItem);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void movePortMenuItemUp(String nodePath, String portName, int index) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withMenuItemMovedUp(index);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void movePortMenuItemDown(String nodePath, String portName, int index) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withMenuItemMovedDown(index);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void updatePortMenuItem(String nodePath, String portName, int index, String key, String label) {
        Node node = getNode(nodePath);
        Port newPort = node.getInput(portName).withMenuItemChanged(index, key, label);
        Node newNode = node.withInputChanged(portName, newPort);
        replaceNodeInPath(nodePath, newNode);
    }

    public void setPortValue(String nodePath, String portName, Object value) {
        Node newNode = getNode(nodePath).withInputValue(portName, value);
        replaceNodeInPath(nodePath, newNode);
    }

    public void revertToDefaultPortValue(String nodePath, String portName) {
        Node node = getNode(nodePath);
        Port port = node.getPrototype().getInput(portName);
        if (port != null) {
            Node newNode = node.withInputValue(portName, port.getValue());
            replaceNodeInPath(nodePath, newNode);
        }
    }

    public void connect(String parentPath, Node outputNode, Node inputNode, Port inputPort) {
        Node newParent = getNode(parentPath).connect(outputNode.getName(), inputNode.getName(), inputPort.getName());
        replaceNodeInPath(parentPath, newParent);
    }
    
    public void connect(String parentPath, Node outputNode, Node inputNode, String inputPort) {
        Node newParent = getNode(parentPath).connect(outputNode.getName(), inputNode.getName(), inputPort);
        replaceNodeInPath(parentPath, newParent);
    }

    public void connect(String parentPath, String outputNode, String inputNode, String inputPort) {
        Node newParent = getNode(parentPath).connect(outputNode, inputNode, inputPort);
        replaceNodeInPath(parentPath, newParent);
    }

    public void disconnect(String parentPath, Connection connection) {
        Node newParent = getNode(parentPath).disconnect(connection);
        replaceNodeInPath(parentPath, newParent);
    }

    public void publish(String parentPath, String childNode, String childPort, String publishedName) {
        Node newParent = getNode(parentPath).publish(childNode, childPort, publishedName);
        replaceNodeInPath(parentPath, newParent);
    }

    public void unpublish(String parentPath, String publishedName) {
        Node newParent = getNode(parentPath).unpublish(publishedName);
        replaceNodeInPath(parentPath, newParent);
    }

    /**
     * Replace the node at the given path with the new node.
     * Afterwards, the nodeLibrary field is set to the new NodeLibrary.
     *
     * @param nodePath The node path. This path needs to exist.
     * @param node     The new node to put in place of the old node.
     */
    public void replaceNodeInPath(String nodePath, Node node) {
        checkArgument(nodePath.startsWith("/"), "Node path needs to be an absolute path, starting with '/'.");
        nodePath = nodePath.substring(1);
        Node newRoot;
        if (nodePath.isEmpty()) {
            newRoot = node;
        } else {
            newRoot = replacedInPath(nodePath, node);
        }
        nodeLibrary = nodeLibrary.withRoot(newRoot);
    }

    /**
     * Replace the node at the given path with the new node.
     * Helper function that replaces nodes recursively (i.e. deepest sublevels first,
     * then going up, until the root node has been reached).
     *
     * @param nodePath The node path. This path needs to exist.
     * @param node     The new node to put in place of the old node.
     */
    private Node replacedInPath(String nodePath, Node node) {
        if (!nodePath.contains("/"))
            return getRootNode().withChildReplaced(nodePath, node);
        List<String> parts = ImmutableList.copyOf(Splitter.on("/").split(nodePath));
        List<String> parentParts = parts.subList(0, parts.size() - 1);
        String childName = parts.get(parts.size() - 1);
        String parentPath = Joiner.on("/").join(parentParts);
        Node parent = nodeLibrary.getNodeForPath("/" + parentPath);
        Node newParent = parent.withChildReplaced(childName, node);
        return replacedInPath(parentPath, newParent);
    }

}
