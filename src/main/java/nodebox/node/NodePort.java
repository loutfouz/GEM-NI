package nodebox.node;

import com.google.common.base.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A combination of a node and a port.
 * <p/>
 * This is used as the key for the inputValuesMap.
 */
public final class NodePort {
    //public final String nodeName;
    public final String port;
    private Node node;

    public Node getNode()
    {
    	return node;
    }
    public static NodePort of(Node node,  String port) {
        return new NodePort(node, port);
    }

    NodePort(Node node, String port) {
        checkNotNull(node);
        checkNotNull(port);
        this.node = node;
        //this.nodeName = node.getName();
        this.port = port;
    }

/*    public String getNode() {
        return node.getName();
    }
*/
    public String getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NodePort)) return false;
        final NodePort other = (NodePort) o;
        return Objects.equal(node.getName(), other.getNode().getName())
                && Objects.equal(port, other.port);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(node.getName(), port);
    }
}
