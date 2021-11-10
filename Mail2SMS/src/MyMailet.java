import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;

/**
 * MyMailet.java
 * 16.06.2016
 * @author Wei Tao
 * MyMailet class
 */
public class MyMailet extends GenericMailet {
	/**
	 * The service starts when a mail comes in.
	 */
	public void service(Mail mail) throws MessagingException {
		MimeMessage msg = (MimeMessage) mail.getMessage();
		System.out.println("=====================================================");
		System.out.println("A new mail has been received.");

		String str = msg.getReplyTo()[0].toString();
		String senderAddress = getEmailAddress(str);
		System.out.println("[SenderAddress]: " + senderAddress);
		try {
			ArrayList<String> user = identifyUser(senderAddress);
			ArrayList<String> segment = getSegment(msg);
			String[] encodedMessage = encodeMessage(segment.get(1), segment.get(2));
			String subject;
			if (msg.getSubject() != "") {
				subject = msg.getSubject();
			} else {
				subject = user.get(2);
			}
			String url = buildUrl(user.get(0), user.get(1), subject, getReceiverName(msg, null), encodedMessage[0], encodedMessage[1]);
			System.out.println("[URL]: " + url);
			sendUrl(url);
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Get the pure Email address without the name and less-than/ bigger-than signs.
	 * @param (str) the Email address in the field of mail header
	 * @return the Email address
	 */
	public static String getEmailAddress(String str) {
		if (str.contains("<")) {
			String emailAddress = str.substring(str.indexOf("<") + 1,str.indexOf(">"));
			return emailAddress;
		} else {
			return str;
		}
	}
	
	/**
	 * Connect to database and verify the identification of senders
	 * @param (senderAddress) the Email address of senders
	 * @return the related data of the authenticated users in the database
	 */
	public static ArrayList<String> identifyUser(String senderAddress) throws SQLException{
		ConnectDB db = new ConnectDB();
		db.connect();
		ArrayList<String> user = new ArrayList<String>();
		/* Case 1: look for the client whose Email address is completely matched
		 */
		ResultSet rset1 = db.query(String.format("SELECT * FROM Clients WHERE EmailAddress ='" + senderAddress + "' AND mail2sms = 1"));
		while (rset1.next()) {
			user.add(rset1.getString("ClientName"));
			user.add(rset1.getString("Password"));
			user.add(rset1.getString("mail2sms_sender"));
			System.out.println("[DB]: There is a exact-matched client.");
			return user;
		}
		/* Case 2: look for the client whose Email address is similar to the given one
		 */
		ResultSet rset2 = db.query(String.format("SELECT * FROM Clients WHERE '" + senderAddress + "' LIKE EmailAddress AND mail2sms = 1 ORDER BY LENGTH(EmailAddress) DESC"));
		while (rset2.next()) {
			user.add(rset1.getString("ClientName"));
			user.add(rset1.getString("Password"));
			user.add(rset1.getString("mail2sms_sender"));
			System.out.println("[DB]: There is a fuzzy-matched client.");
			return user;
		}
		/* Case 3: look for the client whose domain name of Email address is the same to the give one
		 */
		String domainName = senderAddress.substring(senderAddress.indexOf("@") + 1, senderAddress.length());
		ResultSet rset3 = db.query(String.format("SELECT * FROM Clients WHERE EmailAddress LIKE '%%" + domainName + "' AND mail2sms = 1"));
		while (rset3.next()) {
			user.add(rset1.getString("ClientName"));
			user.add(rset1.getString("Password"));
			user.add(rset1.getString("mail2sms_sender"));
			System.out.println("[DB]: There is a domainName-matched client.");
			return user;
		}
		/* Case 4: if no result is found in the first three cases, output the warning and stop this service
		 */
		System.out.println("[DB]: No client found.");
		return null;
	}
	
	/**
	 * Collect the text parts and choose a target part from them
	 * @param (part) the mail body as various parts
	 * @return the content and the encoding of the mail body
	 */
	public static ArrayList<String> getSegment(Part part) throws MessagingException, IOException {
		ArrayList<String> result = new ArrayList<String>();
		ArrayList<ArrayList<String>> collector = new ArrayList<ArrayList<String>>();
		if (part.isMimeType("text/*")) {
			ArrayList<String> segment = new ArrayList<String>();
			String contentType = part.getContentType();
			String charset = "";
			String[] contentTypeTokens = contentType.split(";");
			if (contentTypeTokens.length == 1) {
				contentType = contentTypeTokens[0];
			} else {
				contentType = contentTypeTokens[0];
				charset = contentTypeTokens[1];
				charset = charset.substring(charset.indexOf("=") + 1);
			}
			segment.add(contentType);
			segment.add(part.getContent().toString());
			segment.add(charset);
			collector.add(segment);
		} else if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			int partCount = multipart.getCount();
			for (int i = 0; i < partCount; i++) {
				BodyPart bodyPart = multipart.getBodyPart(i);
				collector.add(getSegment(bodyPart)); // use recursion to collect all the parts
			}
		}
		/* look for "text/plain" part first; if none, look for "text/html" part
		 */
		for (int i = 0; i < collector.size(); i++) {
			if (collector.get(i).get(0).toString().contains("text/plain")) {
				result = collector.get(i);
				break;
			} else if (collector.get(i).get(0).toString().contains("text/html")) {
				String content = collector.get(i).get(1);
				content = removeSpecialTags(content);
				String noHtmlContent = content.replaceAll("<[^>]*>", "");
				collector.get(i).set(1, noHtmlContent);
				result = collector.get(i);
			}
		}
		return result;
	}
	
	/**
	 * Remove the specials tags of HTML-formatted text
	 * @param (str) the HTML-formatted text in "text/html" part
	 * @return the content in a plain text
	 */
	public static String removeSpecialTags(String str) {
		int pos = 0;
		while ((pos = str.indexOf("<style", pos)) != -1) {
			int endPos = str.indexOf("</style>");
			String before = str.substring(0, pos);
			String after = str.substring(endPos + 8);
			str = before + after;
			pos += 1;
		}
		pos = 0;
		while ((pos = str.indexOf("<script", pos)) != -1) {
			int endPos = str.indexOf("</script>");
			String before = str.substring(0, pos);
			String after = str.substring(endPos + 9);
			str = before + after;
			pos += 1;
		}
		return str;
	}
	
	/**
	 * Get the Email address of recipients
	 * @param (msg, type) the message and the type of the recipients (To/Cc/Bcc)
	 * @return the Email address of recipients (could be multiple)
	 */
	public static String getReceiverName(MimeMessage msg, Message.RecipientType type) throws MessagingException {
		StringBuffer receiveAddress = new StringBuffer();
		Address[] addresss = null;
		if (type == null) {
			addresss = msg.getAllRecipients();
		} else {
			addresss = msg.getRecipients(type);
		}
		if (addresss == null || addresss.length < 1)
			throw new MessagingException("No receiver!");
		for (Address address : addresss) {
			InternetAddress internetAddress = (InternetAddress) address;
			String str = internetAddress.toUnicodeString();
			String emailAddress = getEmailAddress(str);
			System.out.println("[ReceiverAddress]: " + emailAddress);
			String receiverName = emailAddress.substring(0, emailAddress.indexOf("@"));
			receiveAddress.append(receiverName).append(";"); // if multiple recipients, use semicolon to separate them 
		}
		receiveAddress.deleteCharAt(receiveAddress.length() - 1);
		String result = receiveAddress.toString();
		return result;
	}
	
	/**
	 * Change the encoding of the content
	 * @param (msg, enc) the content and the encoding
	 * @return the content in Hex string and the encoding
	 */
	public static String[] encodeMessage(String msg, String enc) throws UnsupportedEncodingException {
		enc = enc.replace("\"", "").toUpperCase();
		if (msg != null) {
			// the first encoding "ISO-8859-1"
			String dcs = "3";
			String hex = "";
			byte[] b;
			b = new String(msg.getBytes(), enc ).getBytes("ISO-8859-1");
			hex = bytesToHex(b);
			if ((new String(b, "ISO-8859-1")).equals(msg)) {
				return new String[] { hex, dcs };
			}
			// the second encoding "UTF-16BE"
			dcs = "8";
			b = new String(msg.getBytes(), enc ).getBytes("UTF-16BE");
			hex = bytesToHex(b);
			return new String[] { hex, dcs };
		}
		return null;
	}
	
	/**
	 * convert the content from bytes to a Hex string
	 * @param (bytes) the content and the encoding
	 * @return the content in Hex string
	 */
	public static String bytesToHex(byte[] bytes) {
		final char[] hexArray = "0123456789ABCDEF".toCharArray();
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
	
	/**
	 * build an URL with all the data
	 * @param (username, password, sender, recipients, hexMessage, dcs)
	 * @return an URL string
	 */
	public static String buildUrl(String username, String password, String sender, String recipients, String hexMessage, String dcs) {
		String result = "http://213.158.112.40/smsgw/sendhex.php?";
		try {
			result += "user=" + username;
			result += "&password=" + password;
			result += "&message=" + hexMessage;
			result += "&sender=" + URLEncoder.encode(sender, "UTF-8");
			result += "&recipients=" + recipients;
			result += "&dcs=" + dcs;
			result += "&dlr=0";
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Open an connection and send an URL
	 * @param (url) an URL string
	 */
	public static void sendUrl(String url) {
		PrintWriter out = null;
		BufferedReader in = null;
		String result = "";
		try {
			URL realUrl = new URL(url);
			URLConnection conn = realUrl.openConnection();
			conn.setRequestProperty("accept", "*/*");
			conn.setRequestProperty("connection", "close");
			conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			conn.setDoOutput(true);
			conn.setDoInput(true);
			out = new PrintWriter(conn.getOutputStream());
			out.flush();
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
				if (in != null) {
					in.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		System.out.println("[Result]: " + result);
	}
}