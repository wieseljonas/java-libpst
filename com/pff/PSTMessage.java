/**
 * 
 */
package com.pff;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

/**
 * @author toweruser
 *
 */
public class PSTMessage extends PSTObject {

	public static final int IMPORTANCE_LOW = 0;
	public static final int IMPORTANCE_NORMAL = 1;
	public static final int IMPORTANCE_HIGH = 2;

	PSTMessage(PSTFile theFile, DescriptorIndexNode descriptorIndexNode)
			throws PSTException, IOException
	{
		super(theFile, descriptorIndexNode);
	}

	PSTMessage(PSTFile theFile, DescriptorIndexNode folderIndexNode, PSTTableBC table, HashMap<Integer, PSTDescriptorItem> localDescriptorItems){
		super(theFile, folderIndexNode, table, localDescriptorItems);
	}
	

	public String getPlainText() {
		return this.getStringItem(0x1000);
	}

	public String getRTFBody()
		throws PSTException, IOException
	{
		// do we have an entry for it?
		if (this.items.containsKey(0x1009))
		{
			// is it a reference?
			PSTTableBCItem item = this.items.get(0x1009);
			if (item.data.length > 0) {
				throw new PSTException("Umm, not sure what to do with this data here, was just expecting a local descriptor node ref.");
			}
			int ref = this.getIntItem(0x1009);
			PSTDescriptorItem descItem = this.localDescriptorItems.get(ref);
			RandomAccessFile in = this.pstFile.getFileHandle();
			//get the data at the location
			OffsetIndexItem indexItem = PSTObject.getOffsetIndexNode(in, descItem.offsetIndexIdentifier);
			in.seek(indexItem.fileOffset);
			byte[] temp = new byte[indexItem.size];
			in.read(temp);
			temp = PSTObject.decode(temp);
			return (LZFu.decode(temp));
		}
		
		return "";
	}

	
	public String getBodyHTML() {
		return this.getStringItem(0x1013, PSTTableItem.VALUE_TYPE_PT_STRING8);
	}
	
	
	
	/**
	 * get the importance of the email
	 * @return IMPORTANCE_NORMAL if unknown
	 */
	public int getImportance() {
		return getIntItem(0x0017, IMPORTANCE_NORMAL);
	}
	
	/**
	 * get the message class for the email
	 * @return empty string if unknown
	 */
	public String getMessageClass() {
		return this.getStringItem(0x001a);
	}
	
	/**
	 * get the subject
	 * @return empty string if not found
	 */
	public String getSubject() {
		String subject = this.getStringItem(0x0037);
		byte[] controlCodesA = {0x01, 0x01};
		byte[] controlCodesB = {0x01, 0x05};
		byte[] controlCodesC = {0x01, 0x10};
		if (subject.startsWith(new String(controlCodesA)) ||
			subject.startsWith(new String(controlCodesB)) ||
			subject.startsWith(new String(controlCodesC)))
		{
			subject = subject.substring(2,subject.length());
		}
		return subject;
	}
	
	/**
	 * get the client submit time
	 * @returns null if not found
	 */
	public Date getClientSubmitTime() {
		return this.getDateItem(0x0039);
	}
	
	/**
	 * get received by name
	 * @returns empty string if not found
	 */
	public String getReceivedByName() {
		return this.getStringItem(0x0040);
	}
	
	/**
	 * get sent representing name
	 * @returns empty string if not found
	 */
	public String getSentRepresentingName() {
		return this.getStringItem(0x0042);
	}
	
	/**
	 * Sent representing address type
	 * Known values are SMTP, EX (Exchange) and UNKNOWN
	 * @return empty string if not found
	 */
	public String getSentRepresentingAddressType() {
		return this.getStringItem(0x0064);
	}

	/**
	 * Sent representing email address
	 * @return empty string if not found
	 */
	public String getSentRepresentingEmailAddress() {
		return this.getStringItem(0x0065);
	}
	
	/**
	 * Conversation topic
	 * This is basically the subject from which Fwd:, Re, etc. has been removed
	 * @return empty string if not found
	 */
	public String getConversationTopic() {
		return this.getStringItem(0x0070);
	}

	/**
	 * Received by address type
	 * Known values are SMTP, EX (Exchange) and UNKNOWN
	 * @return empty string if not found
	 */
	public String getReceivedByAddressType() {
		return this.getStringItem(0x0075);
	}
	
	/**
	 * Received by email address
	 * @return empty string if not found
	 */
	public String getReceivedByAddress() {
		return this.getStringItem(0x0076);
	}
	
	/**
	 * Transport message headers ASCII or Unicode string These contain the SMTP e-mail headers.
	 */
	public String getTransportMessageHeaders() {
		return this.getStringItem(0x007d, PSTTableItem.VALUE_TYPE_PT_STRING8);
	}
	

	public boolean isRead() {
		return ((this.getIntItem(0x0e07) & 0x01) != 0);
	}
	public boolean isUnmodified() {
		return ((this.getIntItem(0x0e07) & 0x02) != 0);
	}
	public boolean isSubmitted() {
		return ((this.getIntItem(0x0e07) & 0x04) != 0);
	}
	public boolean isUnsent() {
		return ((this.getIntItem(0x0e07) & 0x08) != 0);
	}
	public boolean hasAttachments() {
		return ((this.getIntItem(0x0e07) & 0x10) != 0);
	}
	public boolean isFromMe() {
		return ((this.getIntItem(0x0e07) & 0x20) != 0);
	}
	public boolean isAssociated() {
		return ((this.getIntItem(0x0e07) & 0x40) != 0);
	}
	public boolean isResent() {
		return ((this.getIntItem(0x0e07) & 0x80) != 0);
	}
	
	
	/**
	 * Acknowledgment mode Integer 32-bit signed
	 */
	public int getAcknowledgementMode () {
		return this.getIntItem(0x0001);
	}
	/**
	 * Originator delivery report requested set if the sender wants a delivery report from all recipients 0 = false 0 != true
	 */
	public boolean getOriginatorDeliveryReportRequested () {
		return (this.getIntItem(0x0023) != 0);
	}
	// 0x0025 	0x0102 	PR_PARENT_KEY 	Parent key Binary data Contains a GUID
	/**
	 * Priority Integer 32-bit signed -1 = NonUrgent 0 = Normal 1 = Urgent
	 */
	public int getPriority () {
		return this.getIntItem(0x0026);
	}
	/**
	 * Read Receipt Requested Boolean 0 = false 0 != true
	 */
	public boolean getReadReceiptRequested () {
		return (this.getIntItem(0x0029) != 0);
	}
	/**
	 * Recipient Reassignment Prohibited Boolean 0 = false 0 != true
	 */
	public boolean getRecipientReassignmentProhibited () {
		return (this.getIntItem(0x002b) != 0);
	}
	/**
	 * Original sensitivity Integer 32-bit signed the sensitivity of the message before being replied to or forwarded 0 = None 1 = Personal 2 = Private 3 = Company Confidential
	 */
	public int getOriginalSensitivity () {
		return this.getIntItem(0x002e);
	}
	/**
	 * Sensitivity Integer 32-bit signed sender's opinion of the sensitivity of an email 0 = None 1 = Personal 2 = Private 3 = Company Confidential
	 */
	public int getSensitivity () {
		return this.getIntItem(0x0036);
	}
	//0x003f 	0x0102 	PR_RECEIVED_BY_ENTRYID (PidTagReceivedByEntr yId) 	Received by entry identifier Binary data Contains recipient/sender structure
	//0x0041 	0x0102 	PR_SENT_REPRESENTING_ENTRYID 	Sent representing entry identifier Binary data Contains recipient/sender structure
	//0x0043 	0x0102 	PR_RCVD_REPRESENTING_ENTRYID 	Received representing entry identifier Binary data Contains recipient/sender structure
	/**
	 * Received representing name ASCII or Unicode string
	 */
	public String getRcvdRepresentingName () {
		return this.getStringItem(0x0044);
	}
	/**
	 * Original subject ASCII or Unicode string
	 */
	public String getOriginalSubject () {
		return this.getStringItem(0x0049);
	}
//	0x004e 	0x0040 	PR_ORIGINAL_SUBMIT_TIME 	Original submit time Filetime
	/**
	 * Reply recipients names ASCII or Unicode string
	 */
	public String getReplyRecipientNames () {
		return this.getStringItem(0x0050);
	}
	/**
	 * My address in To field Boolean
	 */
	public boolean getMessageToMe () {
		return (this.getIntItem(0x0057) != 0);
	}
	/**
	 * My address in CC field Boolean
	 */
	public boolean getMessageCcMe () {
		return (this.getIntItem(0x0058) != 0);
	}
	/**
	 * Message addressed to me ASCII or Unicode string
	 */
	public String getMessageRecipMe () {
		return this.getStringItem(0x0059);
	}
	/**
	 * Response requested Boolean
	 */
	public boolean getResponseRequested () {
		return (this.getIntItem(0x0063) != 0);
	}
	/**
	 * Sent representing address type ASCII or Unicode string Known values are SMTP, EX (Exchange) and UNKNOWN
	 */
	public String getSentRepresentingAddrtype () {
		return this.getStringItem(0x0064);
	}
	//0x0071 	0x0102 	PR_CONVERSATION_INDEX (PidTagConversationInd ex) 	Conversation index Binary data
	/**
	 * Original display BCC ASCII or Unicode string
	 */
	public String getOriginalDisplayBcc () {
		return this.getStringItem(0x0072);
	}
	/**
	 * Original display CC ASCII or Unicode string
	 */
	public String getOriginalDisplayCc () {
		return this.getStringItem(0x0073);
	}
	/**
	 * Original display TO ASCII or Unicode string
	 */
	public String getOriginalDisplayTo () {
		return this.getStringItem(0x0074);
	}
	/**
	 * Received representing address type ASCII or Unicode string Known values are SMTP, EX (Exchange) and UNKNOWN
	 */
	public String getRcvdRepresentingAddrtype () {
		return this.getStringItem(0x0077);
	}
	/**
	 * Received representing e-mail address ASCII or Unicode string
	 */
	public String getRcvdRepresentingEmailAddress() {
		return this.getStringItem(0x0078);
	}

	
	
	
	
	
	/**
	 * attachment stuff here, not sure if these can just exist in emails or not,
	 * but a table key of 0x0671 would suggest that this is a property of the envelope
	 * rather than a specific email property
	 */
	
	private PSTTable7C attachmentTable = null;

	/**
	 * find, extract and load up all of the attachments in this email
	 * necessary for the other operations.
	 * @throws PSTException
	 * @throws IOException
	 */
	private void processAttachments()
		throws PSTException, IOException
	{
		int attachmentTableKey = 0x0671;
		if (this.attachmentTable == null &&
			this.localDescriptorItems != null &&
			this.localDescriptorItems.containsKey(attachmentTableKey))
		{
			PSTDescriptorItem item = this.localDescriptorItems.get(attachmentTableKey);
			if (item.data.length > 0) {
				byte[] valuesArray = null;
				if (item.subNodeOffsetIndexIdentifier != 0) {
					// we have a sub-node!!!!
					// most likely an external values array.  Doesn't really contain any values
					// as far as I can tell, but useful for knowing how many entries we are dealing with
					if (item.subNodeDescriptorItems.size() > 1) {
						throw new PSTException("not sure how to deal with multiple value arrays in subdescriptors");
					}
					// get the first value out
					Iterator<PSTDescriptorItem> valueIterator = item.subNodeDescriptorItems.values().iterator();
					PSTDescriptorItem next = valueIterator.next();
					
					RandomAccessFile in = this.pstFile.getFileHandle();
					OffsetIndexItem offset = PSTObject.getOffsetIndexNode(in, next.offsetIndexIdentifier);
					valuesArray = new byte[offset.size];
					in.seek(offset.fileOffset);
					in.read(valuesArray);
					valuesArray = PSTObject.decode(valuesArray);
				}
				attachmentTable = new PSTTable7C(item.data, valuesArray);
			}
			
		}
	}
	
	public String displayTo() {
		return this.getStringItem(0x0e04);
	}
	
	/**
	 * get the number of attachments for this message
	 * @return
	 * @throws PSTException
	 * @throws IOException
	 */
	public int getNumberOfAttachments()
		throws PSTException, IOException
	{
		this.processAttachments();
		// still nothing? must be no attachments...
		if (this.attachmentTable == null) {
			return 0;
		}
		return this.attachmentTable.getItemCount();
	}
	
	/**
	 * get a specific attachment from this email.
	 * @param attachmentNumber
	 * @return
	 * @throws PSTException
	 * @throws IOException
	 */
	public PSTAttachment getAttachment(int attachmentNumber)
		throws PSTException, IOException
	{
		this.processAttachments();
		
		if (attachmentNumber >= this.getNumberOfAttachments()) {
			throw new PSTException("unable to fetch attachment number "+attachmentNumber+", only "+this.attachmentTable.getItemCount()+" in this email");
		}
		
		// we process the C7 table here, basically we just want the attachment local descriptor...
		HashMap<Integer, PSTTable7CItem> attachmentDetails = this.attachmentTable.getItems().get(attachmentNumber);
		PSTTable7CItem attachmentTableItem = attachmentDetails.get(0x67f2);
		int descriptorItemId = (int)attachmentTableItem.getLongValue();
		// get the local descriptor for the attachmentDetails table.
		PSTDescriptorItem descriptorItem = this.localDescriptorItems.get(descriptorItemId); 
		OffsetIndexItem attachmentOffset = PSTObject.getOffsetIndexNode(this.pstFile.getFileHandle(), descriptorItem.offsetIndexIdentifier);
		// read in the data from the attachmentOffset
		RandomAccessFile in = this.pstFile.getFileHandle();
		in.seek(attachmentOffset.fileOffset);
		byte[] attachmentData = new byte[attachmentOffset.size];
		in.read(attachmentData);
		if (this.pstFile.getEncryptionType() == PSTFile.ENCRYPTION_TYPE_COMPRESSIBLE) {
			attachmentData = PSTObject.decode(attachmentData);
		}
		
		// try and decode it
		PSTTableBC attachmentDetailsTable = new PSTTableBC(attachmentData);
		
		// create our all-precious attachment object.
		// note that all the information that was in the c7 table is repeated in the eb table in attachment data.
		// so no need to pass it...
		return new PSTAttachment(this.pstFile, attachmentDetailsTable, this.localDescriptorItems);
	}
	
	
	/**
	 * string representation of this email
	 */
	public String toString() {
		return
			"PSTEmail: "+this.getSubject()+"\n"+
			"Importance: "+this.getImportance()+"\n"+
			"Message Class: "+this.getMessageClass();
	}
	
}