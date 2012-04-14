package de.kp.wsclient.soap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.kp.wsclient.security.SecConstants;
import de.kp.wsclient.security.SecCredentialInfo;
import de.kp.wsclient.security.SecCrypto;
import de.kp.wsclient.security.SecDecryptor;
import de.kp.wsclient.security.SecEncryptor;
import de.kp.wsclient.security.SecSignature;
import de.kp.wsclient.security.SecValidator;
import de.kp.wsclient.util.UUIDGenerator;
import de.kp.wsclient.xml.XMLSerializer;

public class SOAPMessage {

	private Document xmlDoc;
	
	private Element header;
	private Element body;
	
	private String bodyId = "BE-" + UUIDGenerator.getUUID();
	
	// this constructor is used to build a new SOAP message;
	// use case: outgoing SOAP message
	
	public SOAPMessage() {
		
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    factory.setNamespaceAware(true);
	    
	    try {

	    	xmlDoc = factory.newDocumentBuilder().newDocument();
	    		    	
	    	// create SOAP envelope
	    	String envelopeName = SecConstants.SOAP_PRE + ":" + SecConstants.ELEM_ENVELOPE;
	    	Element envelope = xmlDoc.createElementNS(SecConstants.URI_SOAP12_ENV, envelopeName);
	    	
	    	xmlDoc.appendChild(envelope);

	    	// create SOAP header
	    	String headerName = SecConstants.SOAP_PRE + ":" + SecConstants.ELEM_HEADER;
	    	header = xmlDoc.createElementNS(SecConstants.URI_SOAP12_ENV, headerName);
	    	
	    	envelope.appendChild(header);
	    	
	    	// create SOAP body
	    	String bodyName = SecConstants.SOAP_PRE + ":" + SecConstants.ELEM_BODY;
	    	body = xmlDoc.createElementNS(SecConstants.URI_SOAP12_ENV, bodyName);
	    	
	    	body.setAttribute("id", bodyId);
	    	
	    	envelope.appendChild(body);
	    	
	    } catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
	    
	}

	/**
	 * Constructor SOAPMessage
	 * 
	 * @param xml
	 */
	public SOAPMessage(String xml) {
		
		InputStream is = getISFromXML(xml);
		if (is != null) setSOAPMessageFromIS(is);

	}
	
	/**
	 * Constructor SOAPMessage
	 * 
	 * @param is
	 */
	public SOAPMessage(InputStream is) {
		setSOAPMessageFromIS(is);
	}
	
	/**
	 * Constructor SOAPMessage
	 * 
	 * @param xmlDoc
	 */
	public SOAPMessage(Document xmlDoc) {

		this.xmlDoc = xmlDoc;

		this.header = getSOAPElement(xmlDoc, SecConstants.ELEM_HEADER);		    
		this.body   = getSOAPElement(xmlDoc, SecConstants.ELEM_BODY);

	}
    
	/**
	 * @param is
	 */
	private void setSOAPMessageFromIS(InputStream is) {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);

		try {
	    
			this.xmlDoc = factory.newDocumentBuilder().parse(is);
	    
			this.header = getSOAPElement(xmlDoc, SecConstants.ELEM_HEADER);		    
			this.body   = getSOAPElement(xmlDoc, SecConstants.ELEM_BODY);
	    
	   } catch (Exception e) {
		   e.printStackTrace();
	    
	   } finally {
		   try {
			   is.close();
			   
		   } catch (IOException e) {
			   e.printStackTrace();
		   }
	   }
		
	}
	
	private Element getSOAPElement(Document xmlDoc, String localName) {

	    NodeList nodes = xmlDoc.getElementsByTagNameNS(SecConstants.URI_SOAP12_ENV, localName);
	    if (nodes.getLength() == 0) return null;

        return (Element) nodes.item(0);

    }
	
	public Document getXMLDoc() {
		return this.xmlDoc;
	}
	
	// this method adds content to the SOAP body element
	
	public void setContent(Node content) throws Exception {
		
		if (this.body == null) throw new Exception("Invalid SOAP Message detected (missing body).");
		this.body.appendChild(content);
		
	}

	// returned the first child node of the body node;
	// in case of a decrypted xml document, the respective
	// node is a document fragment
	
	public Node getContent() throws Exception {
		
		if (this.body == null) throw new Exception("Invalid SOAP Message detected (missing body).");
		return this.body.getFirstChild();

	}
	
	// this method supports the signing of the SOAP message
	
	public void sign(SecCredentialInfo credentialInfo) throws Exception {
		
		SecSignature signature = new SecSignature(credentialInfo);
		this.xmlDoc = signature.sign(this.xmlDoc);
		
	}

	// this method supports encryption and signing
	// of the SOAP message; note, that encryption
	// MUST be invoked BEFORE signing is called
	
	public void encryptAndSign(SecCredentialInfo credentialInfo, SecCrypto crypto) throws Exception {

		// encrypt
		SecEncryptor encryptor = new SecEncryptor(crypto);
		this.xmlDoc = encryptor.encrypt(this.xmlDoc);
		
		// sign
		SecSignature signature = new SecSignature(credentialInfo);
		this.xmlDoc = signature.sign(this.xmlDoc);

	}
	
	// this method verifies the signature assigned with th SOAP message
	
	public void verify() throws Exception {
		
		SecValidator validator = new SecValidator();
		validator.verify(this.xmlDoc);
		
	}
	
	// this method supports verification of a signed
	// SOAP message and afterwards decryption of the
	// respective content
	
	public void verifyAndDecrypt(SecCrypto crypto) throws Exception {
		
		// verify signature of SOAP message
		SecValidator validator = new SecValidator();
		this.xmlDoc = validator.verify(this.xmlDoc);
		
		// decrypt content of SOAP message (this is
		// actually restricted to the BODY element)
		
		SecDecryptor decryptor = new SecDecryptor(crypto);
		this.xmlDoc = decryptor.decrypt(this.xmlDoc);
		
	}
	
	public String toXML() {
		return XMLSerializer.serialize(this.xmlDoc);
	}

	private InputStream getISFromXML(String xml) {
		
		byte[] bytes;
		try {

			bytes = xml.getBytes("UTF-8");
			return new ByteArrayInputStream(bytes);
		
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		return null;
		
	}
	
}
