package facebook;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import freenet.node.DarknetPeerNode;

public class UserComparator implements Comparator<User> {

	// Mapping from a facebook userid to new nodes of that user
	private final Map<Long, List<DarknetPeerNode>> friendNodes;

	public UserComparator(Map<Long, List<DarknetPeerNode>> friendNodes) {
		this.friendNodes = friendNodes;
	}

	public int compare(User user0, User user1) {
		long uid0 = user0.getUid();
		long uid1 = user1.getUid();

		// If there is a new node for user0 and isn't one for user1 then user0 comes
		// before user1
		if (friendNodes.containsKey(uid0) && !friendNodes.containsKey(uid1))
			return -1;
		// The same as above but with reversed uids
		else if (!friendNodes.containsKey(uid0) && friendNodes.containsKey(uid1))
			return 1;
		// Otherwise we sort lexicographically by name
		else
			return user0.getName().compareTo(user1.getName());
	}

}
