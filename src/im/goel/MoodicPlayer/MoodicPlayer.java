/*******************************************************
 * 
 * Karan Goel, 2013
 * karan@goel.im
 * 
 * MoodicPlayer is the coolest app I've ever written!
 * nuf said!
 * 
 * It generates a last.fm playlist based on your mood.
 * 
 *******************************************************/

package im.goel.MoodicPlayer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.xml.parsers.ParserConfigurationException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.xml.sax.SAXException;

import utils.Moods;


public class MoodicPlayer implements ActionListener, PropertyChangeListener {

	private static final String key = "5b9e1ad78d831a259ce90cecae93cf05"; // Add your API key
	private static final String secret = "e7c3dfe3abfe181c574bf2f63a0a4271"; // Add your API secret

	private String token; // Token generated by last.fm API
	private String sessionKey; // generated by the program itself after getting the token
	private boolean isAuthenticated; // true if user has authenticated already

	public static void main(String[] args) throws IOException {
		new MoodicPlayer();
	}

	private List<TrackInfo> allTracks; // Stores all tracks found for all moods
	private List<String> mbids; // Stores mbids found for all moods
	private Moods moodObj = new Moods();

	private MoodicPlayer() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				isAuthenticated = isAuth();
				// Create and show GUI
				initializeFrame();
				initializeMenu();
				initializeNorth();
				frame.add(north, BorderLayout.CENTER);
				frame.setVisible(true);
			}
		});
	}

	/**
	 * @return true if user has already authenticated.
	 */
	private boolean isAuth() {
		File file = new File("user.properties");
		if (file.exists()) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(file));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			try {
				sessionKey = br.readLine();
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}

	/**
	 * Keeps track of events on GUI elements.
	 */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == go) {
			// Main button is pressed
			progress.setIndeterminate(true); // Indeterminate until num of tracks is knowns
			go.setEnabled(false); // disable button
			Task task = new Task();
			task.addPropertyChangeListener(this);
			task.execute(); // start the magic!
		} else if (e.getSource() == step1) {
			// Step 1 of authentication. Send user to auth URL
			token = getToken();
			//System.out.println(token);
			String authURL = "http://www.last.fm/api/auth/?api_key=" + key + "&token=" + token;
			// Get the default browser, take the user to the URL
			try {
				java.awt.Desktop.getDesktop().browse(new java.net.URI(authURL));
			} catch (IOException e1) {
				e1.printStackTrace();
			} catch (URISyntaxException e1) {
				e1.printStackTrace();
			}
			step1.setEnabled(false); // Done for step 1
			step2.setEnabled(true); // Enable step 2
		} else if (e.getSource() == step2) {
			// Step 2 of authentication. Generate the sessionKey
			sessionKey = getSessionKey(token);
			// Save session key and hashed signature as it has infinite lifetime 
			File file = new File("user.properties");
			BufferedWriter writer = null;
			try {
				writer = new BufferedWriter(new FileWriter(file));
				writer.write(sessionKey);
				writer.flush();
				writer.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			String message = "Thank you for authentication. Select your mood\nand click " + 
					"\"Build\" to build your playlist";
			JOptionPane.showMessageDialog(null, message, "Authentication Successful", 
					JOptionPane.INFORMATION_MESSAGE);
			go.setEnabled(true);
		} else if (e.getSource() == about) {
			String message = "Moodic Player is a free and open source playlist builder\n" + 
					"for last.fm. Simply add your account, select your mood and voil�!\n" + 
					"a new playlist will be ready for you in seconds!\n\n" + 
					"Now give beats to your moods!\n\n" + 
					"Open source project by Karan Goel (http://www.goel.im).\n" + 
					"Source code at http://github.com/thekarangoel/MoodicPlayer/";
			JOptionPane.showMessageDialog(null, message, "About Moodic Player", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/**
	 * Keeps track of the progress to notify the progress bar
	 * to change its value appropriately.
	 */
	public void propertyChange(PropertyChangeEvent e) {
		if ("progress" == e.getPropertyName()) {
			int p = (Integer) e.getNewValue();
			progress.setIndeterminate(false); // Because we know how much change we need
			progress.setValue((p * (100 / mbids.size()) + (100 % mbids.size())) / 2);
		}
	}

	/**
	 * Returns the token generated for this session.
	 * @return token generated by this session by last.fm
	 */
	public String getToken() {
		String urlToParse = "http://ws.audioscrobbler.com/2.0/?method=auth.gettoken&" + 
				"api_key=" + key + "&api_sig=" + secret;
		List<Element> list = getList(urlToParse, "auth.gettoken");
		for (int i = 0; i < list.size();) {
			Element node = (Element) list.get(i);
			return node.getText();
		}
		return null;
	}

	/**
	 * Returns the session key as per the last.fm documentation
	 * @param token
	 * @return session key as per the documentation
	 * @throws NoSuchAlgorithmException
	 */
	public String getSessionKey(String token) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		String apiSig = "api_key" + key + "methodauth.getSessiontoken" + token + secret;		
		md.update(apiSig.getBytes());
		byte byteData[] = md.digest();

		//convert the byte to hex format
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < byteData.length; i++) {
			sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
		}

		String hashedSig = sb.toString();
		String urlToParse = "http://ws.audioscrobbler.com/2.0/?method=auth.getSession&" + 
				"api_key=" + key + "&api_sig=" + hashedSig + "&token=" + token;
		List<Element> list = getList(urlToParse, "auth.getSession");
		for (int i = 0; i < list.size();) {
			Element node = (Element) list.get(i);
			return node.getChildText("key");
		}
		return null;
	}

	/**
	 * Required for JDOM. Returns a list of Element's to be parsed
	 * found in the given URL.
	 */
	private List<Element> getList(String urlToParse, String method) {
		if (urlToParse != null) {
			URL website = null;
			try {
				website = new URL(urlToParse);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			ReadableByteChannel rbc = null;
			try {
				rbc = Channels.newChannel(website.openStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream("request.xml");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			try {
				fos.getChannel().transferFrom(rbc, 0, 1 << 24);
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		File xmlFile = new File("request.xml");

		SAXBuilder builder = new SAXBuilder();
		Document document = null;
		try {
			document = (Document) builder.build(xmlFile);
		} catch (JDOMException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Element rootNode = document.getRootElement();

		if (method.equals("tag.gettoptracks")) {
			return rootNode.getChildren("toptracks");
		} else if (method.equals("track.getInfo")) {
			return rootNode.getChildren("track");
		} else if (method.equals("auth.gettoken")) {
			return rootNode.getChildren("token");
		} else if (method.equals("auth.getSession")) {
			return rootNode.getChildren("session");
		} else if (method.equals("playlist.addTrack")) {
			return rootNode.getChildren("playlists");
		} else {
			return null;
		}
	}

	//********************** BUILD GUI **********************//
	private JFrame frame;
	private JPanel north;
	private JProgressBar progress;
	private JComboBox<String> moodSelector;
	private JButton go;
	private JMenuBar menubar;
	private JMenu lastfm;
	private JMenuItem step1, step2, about;

	/**
	 * Initializes the main frame and sets its properties.
	 */
	private void initializeFrame() {
		frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(new Dimension(350, 110));
		frame.setLocation(new Point(400, 300));
		frame.setTitle("Moodic Player");
		frame.setResizable(false);
		frame.setLayout(new BorderLayout());
	}

	/**
	 * Initializes the north panel and sets its properties.
	 */
	private void initializeNorth() {
		north = new JPanel(new GridLayout(1, 3));
		moodSelector = new JComboBox<String>(moodObj.getMoods());
		go = new JButton("Build");
		go.addActionListener(this);
		if (!isAuthenticated) {
			go.setEnabled(false);
		}
		progress = new JProgressBar(0, 100);
		progress.setValue(0);
		north.add(moodSelector);
		north.add(go);
		north.add(progress);
		north.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
	}

	private void initializeMenu() {
		menubar = new JMenuBar();
		lastfm = new JMenu("last.fm Account");
		if (isAuthenticated) {
			lastfm.setEnabled(false); // Known user found
		}
		menubar.add(lastfm);
		step1 = new JMenuItem("1. Allow Access");
		step1.addActionListener(this);
		lastfm.add(step1);
		step2 = new JMenuItem("2. Proceed");
		step2.addActionListener(this);
		step2.setEnabled(false);
		lastfm.add(step2);
		about = new JMenuItem("About");
		about.addActionListener(this);
		menubar.add(lastfm);
		menubar.add(about);
		frame.setJMenuBar(menubar);
	}
	//********************** BUILD GUI **********************//


	/**
	 * This class is meant to process all things in a background
	 * thread.
	 * @author Karan Goel
	 */
	private class Task extends SwingWorker<Void, Object> {

		private static final String LIMIT = "5"; // number of tracks per tag to find

		@Override
		/**
		 * Everything in this method is done in a separate thread
		 * and not EDT!
		 */
		protected Void doInBackground() throws SAXException, IOException, ParserConfigurationException {
			//System.out.println(javax.swing.SwingUtilities.isEventDispatchThread());
			String mood = moodSelector.getSelectedItem().toString().toLowerCase();
			List<String> subMoods = moodObj.getSubMoods(mood);

			mbids = getAllMbids(subMoods); // Find all mbids for given moods

			// FOR DEBUGGING
			System.out.println("Number of MBId's = " + mbids.size());
			// FOR DEBUGGING

			int currentProgress = 0;
			int maxProgress = mbids.size(); // Important so we don't loop unnecessarily

			allTracks = new ArrayList<TrackInfo>();
			while (currentProgress < maxProgress) {
				setProgress(currentProgress);
				// Make progress.
				// Run this once for ever mbid
				getTrackInfoForMbid(mbids.get(currentProgress));
				currentProgress++;
			}

			// FOR DEBUGGING
			System.out.println("Number of tracks in allTracks = " + allTracks.size());
			// FOR DEBUGGING

			// Build a single playlist
			int playlistID = buildPlaylist();

			System.out.println(playlistID);

			// Add tracks to the playlist
			for (TrackInfo track : allTracks) {
				addAllTracks(playlistID, track);
				setProgress(currentProgress);
				currentProgress++;
			}
			
			String message = "Your playlist has been built. Access it at Last.fm";
			JOptionPane.showMessageDialog(null, message, "Success", 
					JOptionPane.INFORMATION_MESSAGE);
			
			return null;
		}

		/**
		 * This method runs in EDT after background thread is completed.
		 */
		public void done() {
			//System.out.println(javax.swing.SwingUtilities.isEventDispatchThread());
			go.setEnabled(true);

			// FOR DEBUGGING
			System.out.println("Songs found = " + allTracks.toString());
			// FOR DEBUGGING
		}

		/**
		 * Returns a list of string of mbids for upto LIMIT tracks for each
		 * tag (submood)
		 * @param subMoods - a list of moods
		 * @return list of string of mbids for upto LIMIT tracks for each
		 * tag (submood)
		 */
		private List<String> getAllMbids(List<String> subMoods) {
			// START building list of MBID
			List<String> allMbids = new ArrayList<String>();
			for (String tag : subMoods) {
				String urlToParse = "http://ws.audioscrobbler.com/2.0/?method=tag.gettoptracks&" +
						"tag=" + tag + "&api_key=" + key + "&limit=" + LIMIT;
				List<Element> list = getList(urlToParse, "tag.gettoptracks");
				for (int i = 0; i < list.size(); i++) {
					Element node = (Element) list.get(i);
					List<Element> subList = node.getChildren("track");
					for (int j = 0; j < subList.size(); j++) {
						Element subNode = (Element) subList.get(j);
						String extractedMbid = subNode.getChildText("mbid");
						if (extractedMbid.length() == 36) { // Length of mbid
							allMbids.add(extractedMbid);
						}
					}
				}
			}
			// END building list of MBID
			return allMbids;
		}

		/**
		 * For a given mbid, find the required information, and store
		 * it as a TrackInfo object in the list.
		 * @param mbid of track to find information
		 */
		private void getTrackInfoForMbid(String mbid) {
			// START building list of Info
			String urlToParse = "http://ws.audioscrobbler.com/2.0/?method=track.getInfo&" + 
					"api_key=" + key + "&mbid=" + mbid;
			List<Element> trackList = getList(urlToParse, "track.getInfo");
			for (int i = 0; i < trackList.size(); i++) {
				Element node = (Element) trackList.get(i);
				String name = node.getChildText("name");
				int playCount = Integer.parseInt(node.getChildText("playcount"));
				int listeners = Integer.parseInt(node.getChildText("listeners"));
				List<Element> artistList = node.getChildren("artist");
				for (int j = 0; j < artistList.size(); j++) {
					Element artistElement = (Element) artistList.get(j);
					allTracks.add(new TrackInfo(name, artistElement.getChildText("name"), 
							mbid, playCount, listeners));
				}
			}
		}

		/**
		 * Builds a playlist for the user based on tracks found earlier.
		 * @throws IOException 
		 * @throws SAXException 
		 * @throws ParserConfigurationException 
		 * @throws Exception
		 */
		private int buildPlaylist() {
			String mood = moodSelector.getSelectedItem().toString();
			MessageDigest md = null;
			try {
				md = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			String apiSig = "api_key" + key + "descriptionFor when you are " + mood + ". Created by MoodicPlayer." + 
					"methodplaylist.createsk" + sessionKey + "title" + mood + " " + new Date().getTime() + secret;
			md.update(apiSig.getBytes());
			byte byteData[] = md.digest();
			//convert the byte to hex format
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < byteData.length; i++) {
				sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
			}
			String hashedSig = sb.toString();

			String urlParameters = null;
			try {
				urlParameters = "method=playlist.create&api_key=" + key + "&api_sig=" + hashedSig +
						"&sk=" + sessionKey + "&title=" + mood + " " + new Date().getTime() + "&description=" + 
						URLEncoder.encode("For when you are " + mood + ". Created by MoodicPlayer.", "UTF-8");
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			}
			String request = "http://ws.audioscrobbler.com/2.0/";


			System.out.println("\n\napi_key = " + key);
			System.out.println("api_sig = " + hashedSig);
			System.out.println("Session key = " + sessionKey + "\n\n");


			URL url = null;
			try {
				url = new URL(request);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} 
			HttpURLConnection connection = null;
			try {
				connection = (HttpURLConnection) url.openConnection();
			} catch (IOException e) {
				e.printStackTrace();
			}           
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setInstanceFollowRedirects(false); 
			try {
				connection.setRequestMethod("POST");
			} catch (ProtocolException e) {
				e.printStackTrace();
			} 
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
			connection.setRequestProperty("charset", "utf-8");
			connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connection.setRequestProperty("User-Agent", "MoodicPlayer http://www.goel.im"); 
			connection.setUseCaches(false);

			DataOutputStream wr = null;
			try {
				wr = new DataOutputStream(connection.getOutputStream());
				wr.writeBytes(urlParameters);
				wr.flush();
				wr.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			InputStream is = null;
			Scanner s = null;
			String response = null;
			try {
				if (connection.getResponseCode() != 200) {
					s = new Scanner(connection.getErrorStream());
				} else {
					is = connection.getInputStream();
					s = new Scanner(is);
				}
				s.useDelimiter("\\Z");
				response = s.next();
				System.out.println("\nResponse: " + response + "\n\n");
			} catch (IOException e2) {
				e2.printStackTrace();
			}

			String[] parts = response.split("<id>");
			return Integer.parseInt(parts[1].split("</id>")[0]);
		}

		/**
		 * Adds all tracks from allTracks into the playlist built.
		 * @param playlistID
		 * @param track
		 */
		private void addAllTracks(int playlistID, TrackInfo track) {
			MessageDigest md = null;
			try {
				md = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			String apiSig = "api_key" + key + "artist" + track.getTrackArtist() + "methodplaylist.addTrackplaylistID" + 
					playlistID + "sk" + sessionKey + "track" + track.getTrackName() + secret;
			md.update(apiSig.getBytes());
			byte byteData[] = md.digest();
			//convert the byte to hex format
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < byteData.length; i++) {
				sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
			}
			String hashedSig = sb.toString();

			String urlParameters = null;
			urlParameters = "method=playlist.addTrack" + "&api_key=" + key + "&artist=" + track.getTrackArtist() + "&playlistID=" + 
					playlistID + "&sk=" + sessionKey + "&track=" + track.getTrackName() + "&api_sig=" + hashedSig;

			String request = "http://ws.audioscrobbler.com/2.0/";

			URL url = null;
			try {
				url = new URL(request);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} 
			HttpURLConnection connection = null;
			try {
				connection = (HttpURLConnection) url.openConnection();
			} catch (IOException e) {
				e.printStackTrace();
			}           
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setInstanceFollowRedirects(false); 
			try {
				connection.setRequestMethod("POST");
			} catch (ProtocolException e) {
				e.printStackTrace();
			} 
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
			connection.setRequestProperty("charset", "utf-8");
			connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
			connection.setRequestProperty("User-Agent", "MoodicPlayer http://www.goel.im"); 
			connection.setUseCaches(false);

			DataOutputStream wr = null;
			try {
				wr = new DataOutputStream(connection.getOutputStream());
				wr.writeBytes(urlParameters);
				wr.flush();
				wr.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			InputStream is = null;
			Scanner s = null;
			try {
				if (connection.getResponseCode() != 200) {
					s = new Scanner(connection.getErrorStream());
				} else {
					is = connection.getInputStream();
					s = new Scanner(is);
				}
				s.useDelimiter("\\Z");
				String response = s.next();
				System.out.println("\nResponse: " + response + "\n\n");
			} catch (IOException e2) {
				e2.printStackTrace();
			}

			connection.disconnect();
		}
	}

}