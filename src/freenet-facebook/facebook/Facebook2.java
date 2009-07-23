package facebook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.code.facebookapi.FacebookException;
import com.google.code.facebookapi.FacebookJsonRestClient;
import com.google.code.facebookapi.ProfileField;

import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.node.DarknetPeerNode;
import freenet.node.FSParseException;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

public class Facebook2 {
	//Hopefully it isn't insecure to include the API_KEY and SECRET here
	public static final String API_KEY = "69ae3db0f9ed9286056488f82bdda1c9";
	public static final String SECRET = "ed6195b46d3f728c69e77bc3e29c99cf";
	public static final long APP_ID = 46554464137L;
	public static final String FILTER_KEY = "app_" + APP_ID;
	private final Node node;
	private final String token;
	private final String createNoteURL;
	private final String authURL;
	private Map<Long, User> friends;
	private FacebookJsonRestClient client;
	private long uid;
	private String sessionSecret;
	private String sessionKey;

	public Facebook2(Node node) throws FacebookException {
		// FacebookJaxbRestClient is nicer but it doesn't like to live in a jar
		this.node = node;
		client = new FacebookJsonRestClient(API_KEY, SECRET);
		token = client.auth_createToken();
		authURL = "https://www.facebook.com/login.php?api_key=" + API_KEY + "&v=1.0" + "&auth_token=" + token;
		createNoteURL = "https://www.facebook.com/authorize.php?api_key=" + API_KEY + "&v=1.0&ext_perm=create_note";
	}

	public String getCreateNoteURL() {
		return createNoteURL;
	}

	public String getAuthURL() {
		return authURL;
	}

	public void loggedIn() throws FacebookException, JSONException {
		client.auth_getSession(token, true);
		sessionSecret = client.getCacheSessionSecret();
		sessionKey = client.getCacheSessionKey();
		client = new FacebookJsonRestClient(API_KEY, sessionSecret, sessionKey);
		uid = client.users_getLoggedInUser();
		friends = createFriends();
	}

	public Map<Long, User> getFriends() throws FacebookException {
		return friends;
	}

	// Returns a mapping from note id to the notes of the logged in user
	public Map<Long, DarknetPeerNode> getOwnNodes() throws FacebookException {
		Map<Long, DarknetPeerNode> ownNotes = new HashMap<Long, DarknetPeerNode>();
		JSONArray notes = client.notes_get(uid);
		for (int i = 0; i < notes.length(); i++) {
			try {
				JSONObject note = notes.getJSONObject(i);
				String ref = note.getString("content");
				long note_id = note.getLong("note_id");
				DarknetPeerNode pn = createNewDarknetNode(ref, "Me");
				ownNotes.put(note_id, pn);
			} catch (FSParseException e) {
			} catch (PeerParseException e) {
			} catch (ReferenceSignatureVerificationException e) {
			} catch (IOException e) {
			} catch (JSONException e) {
			}
		}

		return ownNotes;
	}

	public NodeTuple getFriendNodes() throws FacebookException {
		// Mapping from a facebook uid to a list of new nodes.
		Map<Long, List<DarknetPeerNode>> newNodes = new HashMap<Long, List<DarknetPeerNode>>();
		// Contains the uids of the users that we already whose references we already know
		Set<Long> oldNodes = new HashSet<Long>();

		Object object = client.fql_query("SELECT uid, content FROM note WHERE uid IN (SELECT uid2 FROM friend WHERE uid1 = " + uid + ")");
		// If there are notes a JSONArray is returned, otherwise an empty
		// JSONObject is returned
		if (object instanceof JSONArray) {
			JSONArray notes = (JSONArray) object;
			for (int i = 0; i < notes.length(); i++) {
				System.out.println(i);
				try {
					JSONObject note = notes.getJSONObject(i);
					String ref = note.getString("content");
					long uid = note.getLong("uid");
					DarknetPeerNode node = createNewDarknetNode(ref, friends.get(uid).getName());
					if (isNew(node)) {
						if (!newNodes.containsKey(uid))
							newNodes.put(uid, new ArrayList<DarknetPeerNode>());
						newNodes.get(uid).add(node);
					} else
						oldNodes.add(uid);
				} catch (FSParseException e) {
				} catch (PeerParseException e) {
				} catch (ReferenceSignatureVerificationException e) {
				} catch (IOException e) {
				} catch (JSONException e) {
				}
			}
		}

		return new NodeTuple(oldNodes, newNodes);
	}

	// public void publish(String ref, String description) throws
	// FacebookException, JSONException {
	// JSONObject object = new JSONObject();
	// object.put("darknet_node_reference", ref);
	// object.put("description", description);
	// object.put("name", "Darknet reference");
	// object.put("href", "http://freenetproject.org");
	// client.stream_publish(object);
	// }

	public long publish(String ref) throws FacebookException {
		return client.notes_create("Darknet reference", ref);
	}

	public void notify(Collection<Long> uids, String message) throws FacebookException {
		client.notifications_send(uids, message);
	}

	public long getUid() {
		return uid;
	}

	private Map<Long, User> createFriends() throws FacebookException, JSONException {
		JSONArray friendUids = client.friends_get();
		// friendUids.put(client.users_getLoggedInUser());
		Map<Long, User> friends = new LinkedHashMap<Long, User>();
		Set<ProfileField> fields = EnumSet.of(ProfileField.NAME, ProfileField.PROFILE_URL, ProfileField.PIC_SQUARE,
				ProfileField.UID);
		JSONArray response = (JSONArray) client.users_getInfo(jsonToListLong(friendUids), fields);
		for (int i = 0; i < response.length(); i++) {
			JSONObject friend = response.getJSONObject(i);
			String pic = !"".equals(friend.getString("pic_square")) ? friend.getString("pic_square")
					: "http://static.ak.fbcdn.net/pics/q_silhouette.gif";
			long uid = friend.getLong("uid");
			String name = friend.getString("name");
			String profile_url = friend.getString("profile_url");
			friends.put(uid, new User(uid, name, pic, profile_url));
		}
		return friends;
	}

	private DarknetPeerNode createNewDarknetNode(String ref, String name) throws FSParseException,
			PeerParseException, ReferenceSignatureVerificationException, IOException {
		SimpleFieldSet fs = new SimpleFieldSet(ref, false, true);
		DarknetPeerNode pn = node.createNewDarknetNode(fs);
		pn.setPrivateDarknetCommentNote(name);
		return pn;
	}
	
	private boolean isNew(DarknetPeerNode pn) {
		return !Arrays.equals(node.getDarknetIdentity(), pn.getIdentity()) && node.peers.containsPeer(pn) == null;
	}

	private static List<Long> jsonToListLong(JSONArray array) {
		List<Long> list = new ArrayList<Long>();
		try {
			for (int i = 0; i < array.length(); i++)
				list.add(array.getLong(i));
		} catch (JSONException e) {
		}
		return list;
	}
}
