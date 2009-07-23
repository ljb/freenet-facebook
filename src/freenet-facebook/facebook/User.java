package facebook;
public class User {
	private final long uid;
	private final String name;
	private final String picUrl;
	private final String profileUrl;

	public User(long uid, String name, String picUrl, String profileUrl) {
		this.uid = uid;
		this.name = name;
		this.picUrl = picUrl;
		this.profileUrl = profileUrl;
	}

	public long getUid() {
		return uid;
	}

	public String getName() {
		return name;
	}

	public String getPicUrl() {
		return picUrl;
	}

	public String getProfileUrl() {
		return profileUrl;
	}

	public String toString() {
		return "" + getName();
//		return "Uid: " + getUid() + "\nName: " + getName() + "\nPic url: "
//				+ getPicUrl() + "\nProfile url " + getProfileUrl();
	}
}
