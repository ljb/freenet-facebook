package facebook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.code.facebookapi.FacebookException;

import freenet.clients.http.PageMaker;
import freenet.clients.http.PageNode;
import freenet.node.DarknetPeerNode;
import freenet.node.Node;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class Facebook implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginThreadless {

    private boolean loginPage = true;
    private boolean refPublished = false;
    private PluginRespirator pr;
    private PageMaker pm;
    private Facebook2 facebook;
    private Map<Long, User> friends;
    private Map<Long, DarknetPeerNode> ownNodes;
    private Set<Long> oldFriendNodes;
    private Map<Long, List<DarknetPeerNode>> newFriendNodes;
    private static final String SELF_URI = "/plugins/facebook.Facebook";
    private Node node;

    public void runPlugin(PluginRespirator pr) {
	this.pr = pr;
	pm = pr.getPageMaker();
	node = pr.getNode();
    }

    public void terminate() {
    }

    public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
	try {
	    if (loginPage) {
		facebook = new Facebook2(node);

		return makeLoginPage();
	    } else
		return makeFriendPage();
	} catch (Exception e) {
	    loginPage = true;
	    return makeErrorPage("Error in handleHTTPGet:" + e.getMessage(), "Error");
	}
    }

    public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
	String pass = request.getPartAsString("formPassword", 32);
	if ((pass.length() == 0) || !pass.equals(pr.getNode().clientCore.formPassword))
	    return makeErrorPage("Buh! Invalid form password", "Invalid form password");
	else if (request.isPartSet("login")) {
	    try {
		loginPage = false;
		facebook.loggedIn();
		friends = facebook.getFriends();

		NodeTuple tuple = facebook.getFriendNodes();
		oldFriendNodes = tuple.getOldNodes();
		newFriendNodes = tuple.getNewNodes();
		ownNodes = facebook.getOwnNodes();

		return makeFriendPage();
	    } catch (Exception e) {
		loginPage = true;
		return makeErrorPage(e.getMessage()
			+ "\nMake sure that you have authenticated at Facebook before continuing", "Error");
	    }

	} else if (request.isPartSet("friends")) {
	    return makeStatusPage(request);
	} else if (request.isPartSet("logout")) {
	    loginPage = true;
	    return handleHTTPGet(request);
	} else {
	    loginPage = true;
	    return makeErrorPage("Error in handleHTTPPost", "Error");
	}
    }

    private String makeStatusPage(HTTPRequest request) {
	PageNode pageNode = pm.getPageNode("Facebook", null);
	HTMLNode statusNode = pm.getInfobox("infobox-information", "Notice", pageNode.content);

	// The uids of the users we want to send notifications to
	Set<Long> toNotify = new HashSet<Long>();
	// The uids of the users we want to send invites to
	Set<Long> toInvite = new HashSet<Long>();

	for (User friend : friends.values()) {
	    long uid = friend.getUid();

	    if (request.isPartSet("invite_" + uid))
		toInvite.add(uid);

	    if (request.isPartSet("accept_" + uid) && newFriendNodes.containsKey(uid))
		for (DarknetPeerNode pn : newFriendNodes.get(uid)) {
		    node.peers.addPeer(pn);
		    // We only send a notification if at least one reference
		    // was added
		    toNotify.add(uid);

		    log(statusNode, "Added reference for " + friend.getName() + " with name " + pn.getName());
		}
	}

	if (toNotify.isEmpty() && toInvite.isEmpty())
	    statusNode.addChild("#", "Nothing to do");

	if (!toNotify.isEmpty())
	    try {
		facebook
			.notify(
				toNotify,
				"I have added you as a darknet peer. If you haven't already added me you can easily do so with the Facebook plugin in <a href=\"http://127.0.0.1:8888/plugins\">FProxy</a>.");
		log(statusNode, "Sent notifications to " + toNotify.size() + " user(s)");
	    } catch (FacebookException e) {
		e.printStackTrace();
		error(statusNode, "Failed to send notifications", e.getMessage());
	    }

	if (!toInvite.isEmpty())
	    try {
		facebook
			.notify(
				toInvite,
				"You have been invited to <a href=\"http://freenetproject.org\">Freenet</a>"
					+ ", an anonymous and secure P2P-network between friends. "
					+ "If you have Freenet installed and running you can find instructions "
					+ "how how to connect to me via Facebook at this "
					+ "<a href=\"http://127.0.0.1:8888/USK@f0PP-FI59NjWpS49kq0rrK0n~0XRTaspfEc2NrvOozY,F0ITawjY891qBUmZqInNlgO2Ipa2MD8PwGZkUXUb6wQ,AQACAAE/facebook/-8/\">freesite</a>.");
		log(statusNode, "Sent invitations to " + toInvite.size() + " user(s)");
	    } catch (FacebookException e) {
		e.printStackTrace();
		error(statusNode, "Failed to send invitations", e.getMessage());
	    }

	facebook = null;
	loginPage = true;

	return pageNode.outer.generate();
    }

    private String makeLoginPage() {
	PageNode pageNode = pm.getPageNode("Facebook", null);
	HTMLNode authBox = pm.getInfobox("infobox-normal", "Authentication", pageNode.content);
	HTMLNode firstBox = pm.getInfobox("infobox-normal", "First time usage", pageNode.content);
	HTMLNode infoBox = pm.getInfobox("infobox-normal", "Notice", pageNode.content);
	HTMLNode continueBox = pm.getInfobox("infobox-normal", "Continue", pageNode.content);
	
	authBox.addChild("#", "Before doing anything else, you have to authenticate yourself at facebook(opens in a new window)");
	authBox.addChild("br");
	authBox.addChild("a", new String[] { "href", "target" }, new String[] { facebook.getAuthURL(), "_blank" },
		facebook.getAuthURL());
	
	firstBox.addChild(
			"#",
			"The first time you use this plugin you also have to give this plugin permission to create notes in your facebook profile(opens in a new window)");
	firstBox.addChild("br");
	firstBox.addChild("a", new String[] { "href", "target" }, new String[] { facebook.getCreateNoteURL(),
		"_blank" }, facebook.getCreateNoteURL());

	firstBox.addChild("br");
	firstBox.addChild("br");
	firstBox.addChild("#", "You only have to do this once.");

	infoBox.addChild("#", "Social attacks are possible. Before exchanging your "
		+ "node reference with a facebook friend " + "make sure that the friend actually is your "
		+ "friend and not an imposter.");
	infoBox.addChild("br");
	infoBox.addChild("br");
	infoBox.addChild("#",
		"This plugin communicates with the facebook server directly, that is nothing is sent over "
			+ "Freenet.");
	infoBox.addChild("br");
	infoBox.addChild("br");
	infoBox.addChild("#",
		"A note containing your reference will be created. The visibility for newly created notes can be changed "
			+ "by editing the settings of the Notes application(opens in a new window)");
	infoBox.addChild("br");
	infoBox.addChild("a", new String[] { "href", "target" },
		new String[] { "https://www.facebook.com/editapps.php", "_blank" }).addChild("#",
		"https://www.facebook.com/editapps.php");
	infoBox.addChild("br");
	infoBox.addChild("br");
	infoBox
		.addChild("#",
			"It is also possible to edit the visibility of an individual note after it has been created(opens in a new window):");
	infoBox.addChild("br");
	infoBox.addChild("a", new String[] { "href", "target" },
		new String[] { "https://www.facebook.com/notes.php", "_blank" }).addChild("#",
		"https://www.facebook.com/notes.php");

	continueBox.addChild("#", "If you have read and understood the notices above, press the button to continue");
	HTMLNode form = pr.addFormChild(continueBox, SELF_URI, "fbForm");
	form.addChild("input", new String[] { "type", "value" }, new String[] { "submit", "Continue" });
	form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "login", "1" });

	return pageNode.outer.generate();
    }

    private String makeErrorPage(String error, String header) {
	PageNode pageNode = pm.getPageNode("Facebook error", null);
	HTMLNode errorBox = pm.getInfobox("infobox-alert", header, pageNode.content);
	errorBox.addChild("#", error);
	return pageNode.outer.generate();
    }

    private String makeFriendPage() {
	PageNode pageNode = pm.getPageNode("Facebook", null);
	HTMLNode descriptionBox = pm.getInfobox("infobox-normal", "Description", pageNode.content);
	HTMLNode controlBox = pm.getInfobox("infobox-normal", "Logout", pageNode.content);
	HTMLNode friendsNode = pm.getInfobox("infobox-normal", "Facebook friends", pageNode.content);
	HTMLNode informationBox = pm.getInfobox("infobox-information", "Actions performed", pageNode.content);

	HTMLNode form = pr.addFormChild(friendsNode, SELF_URI, "fbForm");
	form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "friends", "1" });

	HTMLNode logoutForm = pr.addFormChild(controlBox, SELF_URI, "logoutForm");
	logoutForm
		.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "logout", "1" });
	logoutForm.addChild("input", new String[] { "type", "value" }, new String[] { "submit", "Logout" });

	HTMLNode table = form.addChild("table", "border", "0");

	descriptionBox.addChild("#",
		"Your reference will be published automatically if it isn't already published.");
	descriptionBox.addChild("br");
	descriptionBox.addChild("#", "A published reference can be removed by deleting it's note.");
	descriptionBox.addChild("br");
	descriptionBox.addChild("#",
		"If you run multiple nodes the published references for the other nodes will be added automatically.");
	descriptionBox.addChild("br");
	descriptionBox.addChild("#",
		"By checking the \"Accept reference\"-box all the references of your friend will be added.");
	descriptionBox.addChild("br");
	descriptionBox
		.addChild(
			"#",
			"Your friend also has to add your reference. Notifications will be sent to the users whose references you accept.");
	descriptionBox.addChild("br");
	descriptionBox
		.addChild(
			"#",
			"If you check the invite checkbox of a friend, a notification containing instructions how to install Freenet and this plugin will be send to that friend.");
	HTMLNode headerRow = table.addChild("tr");
	headerRow.addChild("th", "Picture");
	headerRow.addChild("th", "Name");
	headerRow.addChild("th", "Accept reference");
	headerRow.addChild("th", "Invite");

	// We only publish our reference if it isn't already published
	if (!isOwnRefPublished())
	    try {
		facebook.publish(getRef());
		refPublished = true;
		log(informationBox, "Published reference");
	    } catch (Exception e) {
		error(informationBox, "Failed to publish reference", e.getMessage());
	    }

	// We add our own references automatically
	for (DarknetPeerNode pn : ownNodes.values()) {
	    if (isNew(pn)) {
		node.peers.addPeer(pn);
		log(informationBox, "Added own reference with name " + pn.getName());
	    }
	}

	// We sort the friends according to the UserComparator before iterating
	// over them
	List<User> sortedFriends = new ArrayList<User>(friends.values());
	Collections.sort(sortedFriends, new UserComparator(newFriendNodes));
	for (User friend : sortedFriends) {
	    long uid = friend.getUid();
	    boolean exists = newFriendNodes.containsKey(uid);

	    HTMLNode row = table.addChild("tr");
	    row.addChild("td").addChild("a", new String[] { "href", "target" },
		    new String[] { friend.getProfileUrl(), "_blank" }).addChild("img", "src", friend.getPicUrl());
	    // If the user has a reference that we haven't accepted we make the
	    // name bold and red
	    if (newFriendNodes.containsKey(uid))
		row.addChild("td").addChild("b").addChild("font", "color", "red").addChild("#", friend.getName());
	    else
		row.addChild("td", friend.getName());
	    row.addChild("td").addChild(makeCheckbox("accept_" + uid, exists, false));
	    row.addChild("td").addChild(makeCheckbox("invite_" + uid, !exists && !oldFriendNodes.contains(uid), false));
	}

	form.addChild("tr").addChild("td", "colspan", "4").addChild("input", new String[] { "type", "value" },
		new String[] { "submit", "Continue" });

	return pageNode.outer.generate();
    }

    private boolean isOwnRefPublished() {
	if (refPublished)
	    return true;

	for (DarknetPeerNode pn : ownNodes.values())
	    if (isOwnNode(pn))
		return true;
	return false;
    }

    private String getRef() {
	    return node.exportDarknetPublicFieldSet().toString();
    }
    
    private boolean isNew(DarknetPeerNode pn) {
	return !Arrays.equals(node.getDarknetIdentity(), pn.getIdentity()) && node.peers.containsPeer(pn) == null;
    }

    private boolean isOwnNode(DarknetPeerNode peer) {
	return Arrays.equals(node.getDarknetIdentity(), peer.getIdentity());
    }
    
    public String getVersion() {
	return "0.1";
    }

    private HTMLNode makeCheckbox(String name, boolean enabled, boolean checked) {
	if (!enabled && !checked)
	    return new HTMLNode("input", new String[] { "type", "name", "disabled" }, new String[] { "checkbox", name,
		    "disabled" });
	else if (!enabled && checked)
	    return new HTMLNode("input", new String[] { "type", "name", "checked", "disabled" }, new String[] {
		    "checkbox", name, "checked", "disabled" });
	else if (enabled && !checked)
	    return new HTMLNode("input", new String[] { "type", "name" }, new String[] { "checkbox", name });
	else
	    // if (enabled && checked)
	    return new HTMLNode("input", new String[] { "type", "name", "checked" }, new String[] { "checkbox", name,
		    "checked" });
    }

    // Writes status both to a HTMLNode and the Logger
    private void log(HTMLNode node, String status) {
	node.addChild("#", status);
	node.addChild("br");
	Logger.normal(this, status);
    }

    // Writes status both to a HTMLNode and the Logger
    private void error(HTMLNode node, String message, String fullMessage) {
	node.addChild("b").addChild("font", "color", "red").addChild("#", message);
	node.addChild("br");
	Logger.error(this, message);
	if (fullMessage != null)
	    Logger.error(this, fullMessage);
    }

}
