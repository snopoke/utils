package org.snopoke.util;

import static org.apache.commons.lang.Validate.isTrue;
import static org.apache.commons.lang.Validate.notEmpty;
import static org.apache.commons.lang.Validate.notNull;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.URLDataSource;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;

/**
 * Utility class for sending email messages.
 * 
 * <pre>
 * {@code
 * 		Emailer m = new Emailer(host, port, username, password);
 *		m.setTLS(true);
 *		m.setDebug(true);
 *		m.setFrom("info@domain.com", "Me Myself");
 *		m.addTo("anyone@test.com", "Any Body");
 *		m.setSubject("Subject");
 *
 *		String id = UUID.randomUUID().toString();
 *		m.addAttachment(AttachmentType.RESOURCE, "classpath-image.gif", id);
 *		m.setHTMLContent("<html><body><h1>HTML</h1>\n<img src=\"cid:" + id + "\"/>\n</body></html>");
 *
 *		m.setTextContent("Text version of HTML.");
 *
 *		m.addAttachment(AttachmentType.FILE, "/Users/me/file.txt");
 *		m.send();
 *	}
 * </pre>
 * 
 */
public class Emailer {

	private final String host;
	private final int port;
	private final String username;
	private final String password;
	private Address from;
	private List<Address> to = new ArrayList<Emailer.Address>();
	private List<Address> cc = new ArrayList<Emailer.Address>();
	private List<Address> bcc = new ArrayList<Emailer.Address>();
	private List<Attachment> attachments = new ArrayList<Emailer.Attachment>();
	private String subject;
	private String textContent;
	private String htmlContent;
	private boolean useSSL = false;
	private boolean useTLS = false;
	private boolean debug = false;

	public Emailer(String host, int port, String username, String password) {
		notEmpty(host, "Host can not be empty");
		isTrue(port > 0, "Invalid port number");
		if (username != null || password != null){
			notEmpty(username, "Both username and password must be supplied or neither");
			notEmpty(password, "Both username and password must be supplied or neither");
		}
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
	}

	/**
	 * @param email
	 * @param name
	 *            can be null
	 */
	public void setFrom(String email, String name) {
		from = new Address(email, name);
	}

	/**
	 * @param email
	 * @param name
	 *            can be null
	 */
	public void addTo(String email, String name) {
		to.add(new Address(email, name));
	}
	
	/**
	 * @param email
	 * @param name
	 *            can be null
	 */
	public void addCC(String email, String name) {
		cc.add(new Address(email, name));
	}
	
	/**
	 * @param email
	 * @param name
	 *            can be null
	 */
	public void addBCC(String email, String name) {
		bcc.add(new Address(email, name));
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public void setTextContent(String textContent) {
		this.textContent = textContent;
	}

	public void setHTMLContent(String htmlContent) {
		this.htmlContent = htmlContent;
	}

	public void addAttachment(AttachmentType type, String path) {
		addAttachment(type, path, UUID.randomUUID().toString());
	}

	/**
	 * <p>Add an attachment with a specific ID. This can be used to link
	 * attachments in the content. e.g. images in HTML content</p>
	 * 
	 * {@code <img src="cid:the-attachment-id"/> }
	 * 
	 * @param path
	 * @param id
	 */
	public void addAttachment(AttachmentType type, String path, String id) {
		this.attachments.add(new Attachment(type, path, id));
	}

	/**
	 * Configure the session to use SSL.
	 * 
	 * @param useSSL
	 */
	public void setSSL(boolean useSSL) {
		this.useSSL = useSSL;
	}

	/**
	 * Configure the session to use TLS.
	 * 
	 * @param useTLS
	 */
	public void setTLS(boolean useTLS) {
		this.useTLS = useTLS;
	}

	/**
	 * Configure the mail session to print debug info. Also trust all mail
	 * certifications.
	 * 
	 * Should not be used in production for security reasons.
	 * 
	 * @param debug
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public void send() throws MessagingException {
		notNull(from, "From address can not be null");
		isTrue(!to.isEmpty() || !bcc.isEmpty(), "No TO or BCC addresses specified");

		MimeMessage message = getMessasge();

		// Cover wrap
		MimeBodyPart wrap = new MimeBodyPart();
		MimeMultipart cover = getCoverPart();
		wrap.setContent(cover);

		MimeMultipart content = new MimeMultipart("related");
		message.setContent(content);
		content.addBodyPart(wrap);

		addAttachments(content);

		Transport.send(message);
	}

	private void addAttachments(MimeMultipart content) throws MessagingException {
		for (Attachment att : attachments) {
			MimeBodyPart bodyPart = att.getBodyPart();
			if (bodyPart != null)
				content.addBodyPart(bodyPart);
		}
	}

	private MimeMultipart getCoverPart() throws MessagingException {
		// Alternative TEXT/HTML content
		MimeMultipart cover = new MimeMultipart("alternative");
		if (textContent != null && !textContent.isEmpty()) {
			MimeBodyPart text = new MimeBodyPart();
			cover.addBodyPart(text);
			text.setText(textContent);
		}

		if (htmlContent != null && !htmlContent.isEmpty()) {
			MimeBodyPart html = new MimeBodyPart();
			cover.addBodyPart(html);
			html.setContent(htmlContent, "text/html");
		}
		return cover;
	}

	/**
	 * Create the MimeMessage and set the to/from/subject
	 * 
	 * @return MimeMessaeg
	 * @throws MessagingException
	 */
	private MimeMessage getMessasge() throws MessagingException {
		MimeMessage message = new MimeMessage(getSession());
		message.setSentDate(new Date());

		for (Address toAddress : to) {
			message.addRecipient(RecipientType.TO, toAddress.getInternetAddress());
		}
		
		for (Address toAddress : cc) {
			message.addRecipient(RecipientType.CC, toAddress.getInternetAddress());
		}
		
		for (Address toAddress : bcc) {
			message.addRecipient(RecipientType.BCC, toAddress.getInternetAddress());
		}
		
		message.setFrom(from.getInternetAddress());

		if (subject != null && !subject.isEmpty())
			message.setSubject(subject, "UTF-8");
		return message;
	}

	/**
	 * Create the mail session with authentication and properties for SSL/TLS
	 * etc.
	 * 
	 * @return configured mail session
	 */
	private Session getSession() {

		Properties properties = new Properties();

		Authenticator authenticator = new Authenticator();
		if (username != null && password != null){
			properties.put("mail.smtp.submitter", authenticator.getPasswordAuthentication().getUserName());
			properties.put("mail.smtp.auth", "true");
		}

		properties.put("mail.smtp.host", host);
		properties.put("mail.smtp.port", port);
		properties.put("mail.smtp.socketFactory.port", port);
		properties.put("mail.smtp.socketFactory.fallback", "false");
		if (useSSL)
			properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		if (useTLS)
			properties.put("mail.smtp.starttls.enable", "true");

		if (debug) {
			properties.put("mail.smtp.ssl.checkserveridentity", "false");
			properties.put("mail.smtp.ssl.trust", "*");
			properties.put("mail.debug", "true");
		}

		return Session.getInstance(properties, authenticator);
	}
	
	@Override
	public String toString() {
		return "Email [host=" + host + "\nport=" + port + "\nusername=" + username + "\npassword=" + password
				+ "\nfrom=" + from + "\nto=" + to + "\nattachments=" + attachments + "\nsubject=" + subject
				+ "\ntextContent=" + textContent + "\nhtmlContent=" + htmlContent + "\nuseSSL=" + useSSL + "\nuseTLS="
				+ useTLS + "\ndebug=" + debug + "]";
	}

	/**
	 * Used to indicate how to load the attachment.
	 */
	public enum AttachmentType {
		/**
		 * Attachment is loaded from the file system. Path must be relative or
		 * absolute
		 */
		FILE,

		/**
		 * Attachment is loaded from the classpath
		 */
		RESOURCE;
	}

	private class Attachment {
		private final AttachmentType type;
		private final String path;
		private final String id;

		public Attachment(AttachmentType type, String path, String id) {
			notNull(type, "AttachmentType can not be null");
			notEmpty(path, "Attachment path can not be empty");
			
			this.type = type;
			this.path = path;
			this.id = id;
		}

		public MimeBodyPart getBodyPart() throws MessagingException {
			MimeBodyPart attachment = new MimeBodyPart();
			DataSource fds = null;
			switch (type) {
			case FILE:
				fds = new FileDataSource(path);
				attachment.setFileName(fds.getName());
				break;
			case RESOURCE:
				URL url = this.getClass().getClassLoader().getResource(path);
				if (url != null){
					fds = new URLDataSource(url);
					String[] split = path.split("[/\\\\]");
					attachment.setFileName(split[split.length - 1]);
				}
				break;
			}

			if (fds != null) {
				attachment.setDataHandler(new DataHandler(fds));
				if (id != null && !id.isEmpty())
					attachment.setHeader("Content-ID", "<" + id + ">");
				return attachment;
			} else {
				return null;
			}
		}
		
		@Override
		public String toString() {
			return String.format("Attachment[%s, path='%s', id='%s']\n", type.toString(), path, id);
		}
	}

	private class Authenticator extends javax.mail.Authenticator {
		private PasswordAuthentication authentication;

		public Authenticator() {
			authentication = new PasswordAuthentication(username, password);
		}

		protected PasswordAuthentication getPasswordAuthentication() {
			return authentication;
		}
	}

	private class Address {
		private final String email;
		private final String name;

		public Address(String email, String name) {
			notEmpty(email, "Email address can not be empty");

			this.email = email;
			this.name = name;
		}

		public InternetAddress getInternetAddress() throws MessagingException {
			try {
				if (name != null && !name.isEmpty()) {
					return new InternetAddress(email, name);
				} else {
					return new InternetAddress(email);
				}
			} catch (UnsupportedEncodingException e) {
				throw new MessagingException("Error creating email address", e);
			} catch (AddressException e) {
				throw new MessagingException("Error creating email address", e);
			}
		}
		
		@Override
		public String toString() {
			if (name != null && !name.isEmpty()) {
				return String.format("%s <%s>", name, email);
			} else {
				return email;
			}
		}
	}
}