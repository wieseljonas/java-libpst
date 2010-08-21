/**
 * 
 */
package com.pff;
import java.io.*;
import java.util.*;

/**
 * PSTFile is the containing class that allows you to access items within a .pst file.
 * Start here, get the root of the folders and work your way down through your items.
 * @author Richard Johnson
 */
public class PSTFile {

	public static final int ENCRYPTION_TYPE_NONE = 0;
	public static final int ENCRYPTION_TYPE_COMPRESSIBLE = 1;

	private static final int MESSAGE_STORE_DESCRIPTOR_IDENTIFIER = 33;
	private static final int ROOT_FOLDER_DESCRIPTOR_IDENTIFIER = 290;

	public static final int PST_TYPE_ANSI = 14; // MS docs says this should be 15 :/
	public static final int PST_TYPE_UNICODE = 23;
	
	// Known GUIDs
	// Local IDs first
	public static final int PS_PUBLIC_STRINGS = 0;
	public static final int PSETID_Common = 1;
	public static final int PSETID_Address = 2;
	public static final int PS_INTERNET_HEADERS = 3;
	public static final int PSETID_Appointment = 4;
	public static final int PSETID_Meeting = 5;
	public static final int PSETID_Log = 6;
	public static final int PSETID_Messaging = 7;
	public static final int PSETID_Note = 8;
	public static final int PSETID_PostRss = 9;
	public static final int PSETID_Task = 10;
	public static final int PSETID_UnifiedMessaging = 11;
	public static final int PS_MAPI = 12;
	public static final int PSETID_AirSync = 13;
	public static final int PSETID_Sharing = 14;

	// Now the string guids
	private static final String guidStrings[] =
		{ "00020329-0000-0000-C000-000000000046",
		  "00062008-0000-0000-C000-000000000046",
		  "00062004-0000-0000-C000-000000000046",
		  "00020386-0000-0000-C000-000000000046",
		  "00062002-0000-0000-C000-000000000046",
		  "6ED8DA90-450B-101B-98DA-00AA003F1305",
		  "0006200A-0000-0000-C000-000000000046",
		  "41F28F13-83F4-4114-A584-EEDB5A6B0BFF",
		  "0006200E-0000-0000-C000-000000000046",
		  "00062041-0000-0000-C000-000000000046",
		  "00062003-0000-0000-C000-000000000046",
		  "4442858E-A9E3-4E80-B900-317A210CC15B",
		  "00020328-0000-0000-C000-000000000046",
		  "71035549-0739-4DCB-9163-00F0580DBBDF",
		  "00062040-0000-0000-C000-000000000046" };
	
	private HashMap<UUID, Integer> guidMap = new HashMap<UUID, Integer>();
	
	// the type of encryption the files uses.
	private int encryptionType = 0;
	
	// our all important tree.
	private LinkedHashMap<Integer, HashMap<Integer, DescriptorIndexNode>> childrenDescriptorTree = new LinkedHashMap<Integer, HashMap<Integer, DescriptorIndexNode>>();
	
	private HashMap<Long, Integer> nameToId = new HashMap<Long, Integer>();
	private static HashMap<Integer, Long> idToName = new HashMap<Integer, Long>();
	private byte[] guids = null;
	
	private int itemCount = 0;
	
	private RandomAccessFile in;
	
	/**
	 * constructor
	 * @param fileName
	 * @throws FileNotFoundException
	 * @throws PSTException
	 * @throws IOException
	 */
	public PSTFile(String fileName)
		throws FileNotFoundException, PSTException, IOException
	{
		// attempt to open the file.
		in = new RandomAccessFile(fileName, "r");

		// get the first 4 bytes, should be !BDN
		try {
			byte[] temp = new byte[4];
			in.read(temp);
			String strValue = new String(temp);
			if (!strValue.equals("!BDN")) {
				throw new PSTException("Invalid file header: "+strValue+", expected: !BDN"); 
			}
			
			// make sure we are using a supported version of a PST...
			byte[] fileTypeBytes = new byte[2];
			in.seek(10);
			in.read(fileTypeBytes);
			if (fileTypeBytes[0] != PSTFile.PST_TYPE_ANSI &&
				fileTypeBytes[0] != PSTFile.PST_TYPE_UNICODE)
			{
				throw new PSTException("Unrecognised PST File version: "+fileTypeBytes[0]);
			}
			this.pstFileType = fileTypeBytes[0];
			
			// make sure encryption is turned off at this stage...
			if (this.getPSTFileType() == PST_TYPE_ANSI) {
				in.seek(461);
			} else {
				in.seek(513);
			}
			encryptionType = in.readByte();
			if (encryptionType == 0x02) {
				throw new PSTException("Only unencrypted and compressable PST files are supported at this time"); 
			}
			
			// process the descriptor tree and create our children
			buildDescriptorTree(in);
			
			// build out name to id map.
			if (this.getPSTFileType() == PST_TYPE_ANSI) {
				// TODO: name to id maps!
				processNameToIdMap(in);
			} else {
				processNameToIdMap(in);
			}
			
		}  catch (IOException err) {
			throw new PSTException("Unable to read PST Sig", err);
		}

	}

	private int pstFileType = 0;
	public int getPSTFileType() {
		return pstFileType;
	}
	
	/**
	 * read the name-to-id map from the file and load it in
	 * @param in
	 * @throws IOException
	 * @throws PSTException
	 */
	private void processNameToIdMap(RandomAccessFile in)
		throws IOException, PSTException
	{
		// Create our guid map
		for ( int i = 0; i < guidStrings.length; ++i ) {
			UUID uuid = UUID.fromString(guidStrings[i]);
			guidMap.put(uuid, i);
/*
			System.out.printf("guidMap[{%s}] = %d\n", uuid.toString(), i);
/**/
		}
		
		// process the name to id map
		DescriptorIndexNode nameToIdMapDescriptorNode = (getDescriptorIndexNode(97));
		nameToIdMapDescriptorNode.readData(this);

		// get the descriptors if we have them
		HashMap<Integer, PSTDescriptorItem> localDescriptorItems = null;
		if (nameToIdMapDescriptorNode.localDescriptorsOffsetIndexIdentifier != 0) {
			PSTDescriptor descriptor = new PSTDescriptor(this, nameToIdMapDescriptorNode.localDescriptorsOffsetIndexIdentifier);
			localDescriptorItems = descriptor.getChildren();
		}

		// process the map
		PSTTableBC bcTable = new PSTTableBC(nameToIdMapDescriptorNode.dataBlock.data, nameToIdMapDescriptorNode.dataBlock.blockOffsets);
		HashMap<Integer, PSTTableBCItem> tableItems = (bcTable.getItems());
		
		// Get the guids
		PSTTableBCItem guidEntry = tableItems.get(2);	// PidTagNameidStreamGuid
		guids = getData(guidEntry, localDescriptorItems);
		int nGuids = guids.length / 16;
		UUID[] uuidArray = new UUID[nGuids];
		int[] uuidIndexes = new int[nGuids];
		int offset = 0;
		for ( int i = 0; i < nGuids; ++i ) {
			long mostSigBits = (PSTObject.convertLittleEndianBytesToLong(guids, offset, offset+4) << 32) |
								(PSTObject.convertLittleEndianBytesToLong(guids, offset+4, offset+6) << 16) |
								PSTObject.convertLittleEndianBytesToLong(guids, offset+6, offset+8);
			long leastSigBits = PSTObject.convertBigEndianBytesToLong(guids, offset+8, offset+16);
			uuidArray[i] = new UUID(mostSigBits, leastSigBits);
			if ( guidMap.containsKey(uuidArray[i]) ) {
				uuidIndexes[i] = guidMap.get(uuidArray[i]);
			} else {
				uuidIndexes[i] = -1;	// We don't know this guid
			}
/*
			System.out.printf("uuidArray[%d] = {%s},%d\n", i, uuidArray[i].toString(), uuidIndexes[i]);
/**/
			offset += 16;
		}
		
		// if we have a reference to an internal descriptor
		PSTTableBCItem mapEntries = tableItems.get(3);	//
		byte[] nameToIdByte = getData(mapEntries, localDescriptorItems);

		// process the entries
		for (int x = 0; x+8 < nameToIdByte.length; x += 8) {
			int dwPropertyId = (int)PSTObject.convertLittleEndianBytesToLong(nameToIdByte, x, x+4);
			int wGuid = (int)PSTObject.convertLittleEndianBytesToLong(nameToIdByte, x+4, x+6);
			int wPropIdx = ((int)PSTObject.convertLittleEndianBytesToLong(nameToIdByte, x+6, x+8));
			if ( (wGuid & 0x0001) == 0 ) {
				wPropIdx += 0x8000;
				wGuid >>= 1;
				int guidIndex;
				if ( wGuid == 1 ) {
					guidIndex = PS_MAPI;
				} else if ( wGuid == 2 ) {
					guidIndex = PS_PUBLIC_STRINGS;
				} else {
					guidIndex = uuidIndexes[wGuid-3];
				}
				nameToId.put((long)dwPropertyId | ((long)guidIndex << 32), wPropIdx);
				idToName.put(wPropIdx, (long)dwPropertyId);
/*
				System.out.printf("0x%08X:%04X, 0x%08X\n", dwPropertyId, guidIndex, wPropIdx);
/**/
			}
			// else the identifier is a string
		}
	}
	
	private byte [] getData(PSTTableItem item, HashMap<Integer, PSTDescriptorItem> localDescriptorItems)
		throws IOException, PSTException
	{
		if ( item.data.length != 0 ) {
			return item.data;
		}

		if ( localDescriptorItems == null ) {
			throw new PSTException("External reference but no localDescriptorItems in PSTFile.getData()");
		}
		
		if ( item.entryValueType != 0x0102 ) {
			throw new PSTException("Attempting to get non-binary data in PSTFile.getData()");
		}

		PSTDescriptorItem mapDescriptorItem = localDescriptorItems.get(item.entryValueReference);
		return mapDescriptorItem.getData();
	}
	
	int getNameToIdMapItem(int key, int propertySetIndex)
	{
		long lKey = ((long)propertySetIndex << 32) | (long)key;
		Integer i = nameToId.get(lKey);
		if ( i == null )
		{
			return -1;
		}
		return i;
	}


	static long getNameToIdMapKey(int id)
		//throws PSTException
	{
		Long i = idToName.get(id);
		if ( i == null )
		{
			//throw new PSTException("Name to Id mapping not found");
			return -1;
		}
		return i;
	}

	static private Properties propertyNames = null;
	static private boolean bFirstTime = true;
	
	static String getPropertyName(int propertyId, boolean bNamed) {
		if ( bFirstTime ) {
			bFirstTime = false;
			propertyNames = new Properties();
			try {
				InputStream propertyStream = PSTFile.class.getResourceAsStream("/PropertyNames.txt");
				if ( propertyStream != null ) {
					propertyNames.load(propertyStream);
				} else {
					propertyNames = null;
				}
			} catch (FileNotFoundException e) {
				propertyNames = null;
				e.printStackTrace();
			} catch (IOException e) {
				propertyNames = null;
				e.printStackTrace();
			}
		}

		if ( propertyNames != null ) {
			String key = String.format((bNamed ? "%08X" : "%04X"), propertyId);
			return propertyNames.getProperty(key);
		}

		return null;
	}

	static String getPropertyDescription(int entryType, int entryValueType) {
		String ret = "";
		if ( entryType < 0x8000 ) {
			String name = PSTFile.getPropertyName(entryType, false);
			if ( name != null ) {
				ret = String.format("%s:%04X: ", name, entryValueType);
			} else {
				ret = String.format("0x%04X:%04X: ", entryType, entryValueType);
			}
		} else {
			long type = PSTFile.getNameToIdMapKey(entryType);
			if ( type == -1 ) {
				ret = String.format("0xFFFF(%04X):%04X: ", entryType, entryValueType);
			} else {
				String name = PSTFile.getPropertyName((int)type, true);
				if ( name != null ) {
					ret = String.format("%s(%04X):%04X: ", name, entryType, entryValueType);
				} else {
					ret = String.format("0x%04X(%04X):%04X: ", type, entryType, entryValueType);
				}
			}
		}

		return ret;
	}

	/**
	 * destructor just closes the file handle...
	 */
	@Override
	protected void finalize()
		throws IOException
	{
		in.close();
	}
	
	/**
	 * get the type of encryption the file uses
	 * @return encryption type used in the PST File
	 */
	public int getEncryptionType() {
		return this.encryptionType;
	}
	
	/**
	 * get the handle to the file we are currently accessing
	 */
	public RandomAccessFile getFileHandle() {
		return this.in;
	}
	
	/**
	 * Build the children descriptor tree
	 * This goes through the entire descriptor B-Tree and adds every item to the childrenDescriptorTree.
	 * Was looking for an existing data structure in the PST file for this, but apparently they don't exist! 
	 * @param in
	 * @throws IOException
	 * @throws PSTException
	 */
	private void buildDescriptorTree(RandomAccessFile in)
		throws IOException, PSTException
	{
		if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
			long btreeStartOffset = this.extractLEFileOffset(188);
			processTree(in, btreeStartOffset);
		} else {
			long btreeStartOffset = this.extractLEFileOffset(224);
			processTree(in, btreeStartOffset);
		}
	}
	
	/**
	 * Recursive function for building the descriptor tree, used by buildDescriptorTree
	 * @param in
	 * @param btreeStartOffset
	 * @throws IOException
	 * @throws PSTException
	 */
	private void processTree(RandomAccessFile in, long btreeStartOffset)
		throws IOException, PSTException
	{
		byte[] temp = new byte[2];
		if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
			in.seek(btreeStartOffset+500);
		} else {
			in.seek(btreeStartOffset+496);
		}
		in.read(temp);

		if ((temp[0] == 0xffffff81 && temp[1] == 0xffffff81)) {
			
			if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
				in.seek(btreeStartOffset+496);
			} else {
				in.seek(btreeStartOffset+488);
			}

			int numberOfItems = in.read();
			in.read(); // maxNumberOfItems
			in.read(); // itemSize
			int levelsToLeaf = in.read();
			
			if (levelsToLeaf > 0) {
				
				for (int x = 0; x < numberOfItems; x++) {
					long branchNodeItemStartIndex = (btreeStartOffset + (24*x));
					if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
						branchNodeItemStartIndex = (btreeStartOffset + (12*x));
					}
					long nextLevelStartsAt =  this.extractLEFileOffset(branchNodeItemStartIndex+16);
					if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
						nextLevelStartsAt =  this.extractLEFileOffset(branchNodeItemStartIndex+8);
					}
					processTree(in, nextLevelStartsAt);
				}
			}
			else
			{
				for (int x = 0; x < numberOfItems; x++) {
					// The 64-bit descriptor index b-tree leaf node item
					// give me the offset index please!
					if (this.getPSTFileType() == PST_TYPE_ANSI) {
						in.seek(btreeStartOffset + (x * 16));
						temp = new byte[16];
						in.read(temp);
					} else {
						in.seek(btreeStartOffset + (x * 32));
						temp = new byte[32];
						in.read(temp);
					}
					
					DescriptorIndexNode tempNode = new DescriptorIndexNode(temp, this.getPSTFileType());
					
					// we don't want to be children of ourselves...
					if (tempNode.parentDescriptorIndexIdentifier == tempNode.descriptorIdentifier) {
						// skip!
					} else if (childrenDescriptorTree.containsKey(tempNode.parentDescriptorIndexIdentifier)) {
						// add this entry to the existing list of children
						LinkedHashMap<Integer, DescriptorIndexNode> children =
							(LinkedHashMap<Integer, DescriptorIndexNode>)
							childrenDescriptorTree.get(tempNode.parentDescriptorIndexIdentifier);
						children.put(tempNode.descriptorIdentifier, tempNode);
					} else {
						// create a new entry and add this one to that
						LinkedHashMap<Integer, DescriptorIndexNode> children = new LinkedHashMap<Integer, DescriptorIndexNode>();
						children.put(tempNode.descriptorIdentifier, tempNode);
						childrenDescriptorTree.put(tempNode.parentDescriptorIndexIdentifier, children);
					}
					
					this.itemCount++;

				}
			}
		}
		else
		{
			PSTObject.printHexFormatted(temp, true);
			throw new PSTException("Unable to read descriptor node, is not a descriptor");
		}
	}

	/**
	 * get the child item descriptors for a specific descriptor
	 * @return
	 */
	LinkedHashMap<Integer, DescriptorIndexNode> getChildrenDescriptors(int descriptorIdentifier)
	{
		if (!this.childrenDescriptorTree.containsKey(descriptorIdentifier)) {
			return new LinkedHashMap<Integer, DescriptorIndexNode>();
		}
		return (LinkedHashMap<Integer, DescriptorIndexNode>)this.childrenDescriptorTree.get(descriptorIdentifier);
	}
	
	/**
	 * get the message store of the PST file.
	 * Note that this doesn't really have much information, better to look under the root folder
	 * @throws PSTException
	 * @throws IOException
	 */
	public PSTMessageStore getMessageStore()
		throws PSTException, IOException
	{
		DescriptorIndexNode messageStoreDescriptor = getDescriptorIndexNode(MESSAGE_STORE_DESCRIPTOR_IDENTIFIER);
		return new PSTMessageStore(this, messageStoreDescriptor);
	}

	/**
	 * get the root folder for the PST file.
	 * You should find all of your data under here...
	 * @throws PSTException
	 * @throws IOException
	 */
	public PSTFolder getRootFolder()
		throws PSTException, IOException
	{
		DescriptorIndexNode rootFolderDescriptor = getDescriptorIndexNode(ROOT_FOLDER_DESCRIPTOR_IDENTIFIER);
		PSTFolder output = new PSTFolder(this, rootFolderDescriptor);
		return output;
	}
	
	
	static class PSTFileBlock {
		byte[]	data = null;
		int[]	blockOffsets = null;
	}
	
	public PSTFileBlock readLeaf(long bid)
		throws IOException, PSTException
	{
		PSTFileBlock ret = null;

		// get the index node for the descriptor index
		OffsetIndexItem offsetItem = getOffsetIndexNode(bid);
		boolean bInternal = (offsetItem.indexIdentifier & 0x02) != 0;

		byte[] data = new byte[offsetItem.size];
		in.seek(offsetItem.fileOffset);
		in.read(data);
		
		if ( bInternal ) {
			// All internal blocks are at least 8 bytes long...
			if ( offsetItem.size < 8 ) {
				throw new PSTException("Invalid internal block size");
			}

			if ( data[0] == 1 )
			{
				// (X)XBLOCK
				if ( data[1] == 2 ) {
					throw new PSTException("XXBLOCKS not supported yet!");
				}

				ret = this.processArray(in, data);
				
				// The resulting data isn't an internal block any more
				bInternal = false;
			}
			
			// data[0] == 2 SLBLOCK or SIBLOCK
			// let the caller deal with them
		}
		
		if ( ret == null ) {
			// non-array block
			ret = new PSTFileBlock();
			ret.data = data;
			// (Callers must be able to handle ret.blockOffsets == null)
		}

		// (Internal blocks aren't compressed)
		if ( !bInternal &&
			 encryptionType == PSTFile.ENCRYPTION_TYPE_COMPRESSIBLE)
		{
			ret.data = PSTObject.decode(ret.data);
		}
		
		return ret;
	}
	
	
	public int getLeafSize(long bid)
		throws IOException, PSTException
	{
		OffsetIndexItem offsetItem = getOffsetIndexNode(bid);

		// Internal block?
		if ( (offsetItem.indexIdentifier & 0x02) == 0 ) {
			// No, return the raw size
			return offsetItem.size;
		}
	
		// we only need the first 8 bytes
		byte[] data = new byte[8];
		in.seek(offsetItem.fileOffset);
		in.read(data);
	
		// we are an array, get the sum of the sizes...
		return (int)PSTObject.convertLittleEndianBytesToLong(data, 4, 8);
	}

	/**
	 * Read a file offset from the file
	 * PST Files have this tendency to store file offsets (pointers) in 8 little endian bytes.
	 * Convert this to a long for seeking to.
	 * @param in handle for PST file
	 * @param startOffset where to read the 8 bytes from
	 * @return long representing the read location
	 * @throws IOException
	 */
	protected long extractLEFileOffset(long startOffset)
		throws IOException
	{
		long offset = 0;
		if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
			in.seek(startOffset);
			byte[] temp = new byte[4];
			in.read(temp);
			offset |= temp[3] & 0xff;
			offset <<= 8;
			offset |= temp[2] & 0xff;
			offset <<= 8;
			offset |= temp[1] & 0xff;
			offset <<= 8;
			offset |= temp[0] & 0xff;
		} else {
			in.seek(startOffset);
			byte[] temp = new byte[8];
			in.read(temp);
			offset = temp[7] & 0xff;
			long tmpLongValue;
			for (int x = 6; x >= 0; x--) {
				offset = offset << 8;
				tmpLongValue = (long)temp[x] & 0xff;
				offset |= tmpLongValue;
			}
		}

		return offset;
	}

	/**
	 * Generic function used by getOffsetIndexNode and getDescriptorIndexNode for navigating the PST B-Trees
	 * @param in
	 * @param index
	 * @param descTree
	 * @return
	 * @throws IOException
	 * @throws PSTException
	 */
	private byte[] findBtreeItem(RandomAccessFile in, long index, boolean descTree)
		throws IOException, PSTException
	{

		long btreeStartOffset;
		// first find the starting point for the offset index
		if (this.getPSTFileType() == PST_TYPE_ANSI) {
			btreeStartOffset = this.extractLEFileOffset(196);
			if (descTree) {
				btreeStartOffset = this.extractLEFileOffset(188);
			}
		} else {
			btreeStartOffset = this.extractLEFileOffset(240);
			if (descTree) {
				btreeStartOffset = this.extractLEFileOffset(224);
			}
		}

		// okay, what we want to do is navigate the tree until you reach the bottom....
		// try and read the index b-tree
		byte[] temp = new byte[2];
		if (this.getPSTFileType() == PST_TYPE_ANSI) {
			in.seek(btreeStartOffset+500);
		} else {
			in.seek(btreeStartOffset+496);
		}
		in.read(temp);
		while	((temp[0] == 0xffffff80 && temp[1] == 0xffffff80 && !descTree) ||
				 (temp[0] == 0xffffff81 && temp[1] == 0xffffff81 && descTree))
		{

			// get the rest of the data....
			byte[] branchNodeItems;
			if (this.getPSTFileType() == PST_TYPE_ANSI) {
				branchNodeItems = new byte[496];
			} else {
				branchNodeItems = new byte[488];
			}
			in.seek(btreeStartOffset);
			in.read(branchNodeItems);

			int numberOfItems = in.read();
			in.read(); // maxNumberOfItems
			in.read(); // itemSize
			int levelsToLeaf = in.read();

			if (levelsToLeaf > 0) {
				boolean found = false;
				for (int x = 0; x < numberOfItems; x++) {
					if (this.getPSTFileType() == PST_TYPE_ANSI) {
						long indexIdOfFirstChildNode = extractLEFileOffset(btreeStartOffset + (x * 12));
						if (indexIdOfFirstChildNode > index) {
							// get the address for the child first node in this group
							btreeStartOffset = extractLEFileOffset(btreeStartOffset+((x-1) * 12)+8);
							in.seek(btreeStartOffset+500);
							in.read(temp);
							found = true;
							break;
						}
					} else {
						long indexIdOfFirstChildNode = extractLEFileOffset(btreeStartOffset + (x * 24));
						if (indexIdOfFirstChildNode > index) {
							// get the address for the child first node in this group
							btreeStartOffset = extractLEFileOffset(btreeStartOffset+((x-1) * 24)+16);
							in.seek(btreeStartOffset+496);
							in.read(temp);
							found = true;
							break;
						}
					}
				}
				if (!found) {
					// it must be in the very last branch...
					if (this.getPSTFileType() == PST_TYPE_ANSI) {
						btreeStartOffset = extractLEFileOffset(btreeStartOffset+((numberOfItems-1) * 12)+8);
						in.seek(btreeStartOffset+500);
						in.read(temp);
					} else {
						btreeStartOffset = extractLEFileOffset(btreeStartOffset+((numberOfItems-1) * 24)+16);
						in.seek(btreeStartOffset+496);
						in.read(temp);
					}
				}
			}
			else
			{
				// we are at the bottom of the tree...
				// we want to get our file offset!
				for (int x = 0; x < numberOfItems; x++) {

					if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
						if (descTree)
						{
							// The 32-bit descriptor index b-tree leaf node item
							in.seek(btreeStartOffset + (x * 16));
							temp = new byte[4];
							in.read(temp);
							if (PSTObject.convertLittleEndianBytesToLong(temp) == index) {
								// give me the offset index please!
								in.seek(btreeStartOffset + (x * 16));
								temp = new byte[16];
								in.read(temp);
								return temp;
							}
						}
						else
						{
							// The 32-bit (file) offset index item
							long indexIdOfFirstChildNode = extractLEFileOffset(btreeStartOffset + (x * 12));

							if (indexIdOfFirstChildNode == index) {
								// we found it!!!! OMG
								//System.out.println("item found as item #"+x);
								in.seek(btreeStartOffset + (x * 12));

								temp = new byte[12];
								in.read(temp);
								return temp;
							}
						}
					} else {
						if (descTree)
						{
							// The 64-bit descriptor index b-tree leaf node item
							in.seek(btreeStartOffset + (x * 32));

							temp = new byte[4];
							in.read(temp);
							if (PSTObject.convertLittleEndianBytesToLong(temp) == index) {
								// give me the offset index please!
								in.seek(btreeStartOffset + (x * 32));
								temp = new byte[32];
								in.read(temp);
								return temp;
							}
						}
						else
						{
							// The 64-bit (file) offset index item
							long indexIdOfFirstChildNode = extractLEFileOffset(btreeStartOffset + (x * 24));

							if (indexIdOfFirstChildNode == index) {
								// we found it!!!! OMG
								//System.out.println("item found as item #"+x);
								in.seek(btreeStartOffset + (x * 24));

								temp = new byte[24];
								in.read(temp);
								return temp;
							}
						}
					}
				}
				throw new PSTException("Unable to find "+index);
			}
		}

		throw new PSTException("Unable to find node: "+index);
	}

	/**
	 * navigate the internal descriptor B-Tree and find a specific item
	 * @param in
	 * @param identifier
	 * @return the descriptor node for the item
	 * @throws IOException
	 * @throws PSTException
	 */
	DescriptorIndexNode getDescriptorIndexNode(long identifier)
		throws IOException, PSTException
	{
		return new DescriptorIndexNode(findBtreeItem(in, identifier, true), this.getPSTFileType());
	}

	/**
	 * navigate the internal index B-Tree and find a specific item
	 * @param in
	 * @param identifier
	 * @return the offset index item
	 * @throws IOException
	 * @throws PSTException
	 */
	OffsetIndexItem getOffsetIndexNode(long identifier)
		throws IOException, PSTException
	{
		return new OffsetIndexItem(findBtreeItem(in, identifier, false), this.getPSTFileType());
	}

	protected PSTFileBlock processArray(RandomAccessFile in, byte[] data)
		throws IOException, PSTException
	{
		// is the data an array?
		if (!(data[0] == 1 && data[1] == 1))
		{
			throw new PSTException("Unable to process array, does not appear to be one!");
		}

		// we are an array!
		// get the array items and merge them together
		int numberOfEntries = (int)PSTObject.convertLittleEndianBytesToLong(data, 2, 4);
		int dataSize = (int)PSTObject.convertLittleEndianBytesToLong(data, 4, 8);
		PSTFileBlock dataBlock = new PSTFileBlock();
		dataBlock.data = new byte[dataSize];
		dataBlock.blockOffsets = new int[numberOfEntries];
		int blockOffset = 0;
		int tableOffset = 8;
		for (int y = 0; y < numberOfEntries; y++) {
			// get the offset identifier
			long tableOffsetIdentifierIndex;
			if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
				tableOffsetIdentifierIndex = PSTObject.convertLittleEndianBytesToLong(data, tableOffset, tableOffset+4);
			} else {
				tableOffsetIdentifierIndex = PSTObject.convertLittleEndianBytesToLong(data, tableOffset, tableOffset+8);
			}

			// clear the last bit of the identifier.  Why so hard?
			tableOffsetIdentifierIndex = (tableOffsetIdentifierIndex & 0xfffffffe);

			OffsetIndexItem tableOffsetIdentifier = this.getOffsetIndexNode(tableOffsetIdentifierIndex);

			// Paranoia...
			if ( blockOffset + tableOffsetIdentifier.size > dataBlock.data.length ) {
				throw new PSTException("Invalid XBLOCK entry!");
			}

			in.seek(tableOffsetIdentifier.fileOffset);
			in.read(dataBlock.data, blockOffset, tableOffsetIdentifier.size);
			blockOffset += tableOffsetIdentifier.size;
			dataBlock.blockOffsets[y] = blockOffset;
			if (this.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
				tableOffset += 4;
			} else {
				tableOffset += 8;
			}
		}

		return dataBlock;
	}

}
