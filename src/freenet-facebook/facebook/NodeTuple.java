package facebook;

import java.util.List;
import java.util.Map;
import java.util.Set;

import freenet.node.DarknetPeerNode;

public class NodeTuple {
	private final Set<Long> oldNodes;
	private final Map<Long, List<DarknetPeerNode>> newNodes;
	
	public NodeTuple(Set<Long> oldNodes, Map<Long, List<DarknetPeerNode>> newNodes) {
		this.oldNodes = oldNodes;
		this.newNodes = newNodes;
	}

	public Set<Long> getOldNodes() {
		return oldNodes;
	}

	public Map<Long, List<DarknetPeerNode>> getNewNodes() {
		return newNodes;
	}
	
	
}
