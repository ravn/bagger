package gov.loc.repository.bagger.bag.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.StringTokenizer;
import java.io.File;

import gov.loc.repository.bagger.Contact;
import gov.loc.repository.bagger.Project;
import gov.loc.repository.bagger.bag.BagInfoField;
import gov.loc.repository.bagger.bag.BaggerFetch;
import gov.loc.repository.bagger.bag.BaggerOrganization;

import gov.loc.repository.bagit.Bag;
import gov.loc.repository.bagit.BagFactory;
import gov.loc.repository.bagit.BagFile;
import gov.loc.repository.bagit.BagInfoTxt;
import gov.loc.repository.bagit.BagItTxt;
import gov.loc.repository.bagit.FetchTxt;
import gov.loc.repository.bagit.Manifest;
import gov.loc.repository.bagit.PreBag;
import gov.loc.repository.bagit.ProgressListener;
import gov.loc.repository.bagit.BagFactory.Version;
import gov.loc.repository.bagit.FetchTxt.FilenameSizeUrl;
import gov.loc.repository.bagit.Manifest.Algorithm;
import gov.loc.repository.bagit.writer.Writer;
import gov.loc.repository.bagit.writer.impl.FileSystemWriter;
import gov.loc.repository.bagit.writer.impl.TarBz2Writer;
import gov.loc.repository.bagit.writer.impl.TarGzWriter;
import gov.loc.repository.bagit.writer.impl.TarWriter;
import gov.loc.repository.bagit.writer.impl.ZipWriter;
import gov.loc.repository.bagit.verify.Verifier;
import gov.loc.repository.bagit.verify.impl.CompleteVerifierImpl;
import gov.loc.repository.bagit.verify.impl.ParallelManifestChecksumVerifier;
import gov.loc.repository.bagit.verify.impl.RequiredBagInfoTxtFieldsVerifier;
import gov.loc.repository.bagit.verify.impl.ValidVerifierImpl;
import gov.loc.repository.bagit.impl.StringBagFile;
import gov.loc.repository.bagit.transformer.HolePuncher;
import gov.loc.repository.bagit.transformer.impl.DefaultCompleter;
import gov.loc.repository.bagit.transformer.impl.HolePuncherImpl;
import gov.loc.repository.bagit.utilities.SimpleResult;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.richclient.application.Application;
import org.springframework.richclient.dialog.CloseAction;
import org.springframework.richclient.dialog.ConfirmationDialog;
import org.springframework.richclient.progress.BusyIndicator;

/**
 * @author Jon Steinbach
 */
public class DefaultBag {
	private static final Log log = LogFactory.getLog(DefaultBag.class);
	public static final long KB = 1024;
	public static final long MB = 1048576;
	public static final long GB = 1073741824;
	public static final long MAX_SIZE = 104857600;  // 100 MB
	public static final short NO_MODE = 0;
	public static final short ZIP_MODE = 1;
	public static final short TAR_MODE = 2; 
	public static final short TAR_GZ_MODE = 3; 
	public static final short TAR_BZ2_MODE = 4; 
	public static final String NO_LABEL = "none";
	public static final String ZIP_LABEL = "zip";
	public static final String TAR_LABEL = "tar";
	public static final String TAR_GZ_LABEL = "tar gz";
	public static final String TAR_BZ2_LABEL = "tar bz2";

	// Bag option flags
	private boolean isHoley = false;
	private boolean isSerial = true;
	private boolean isNoProject = true;
	private boolean isEdeposit = false;
	private boolean isNdnp = false;
	private boolean isNewbag = true;
	private boolean isBuildTagManifest = true;
	private boolean isBuildPayloadManifest = true;
	private String tagManifestAlgorithm;
	private String payloadManifestAlgorithm;
	private short serialMode = NO_MODE;

	// Bag state flags
	private boolean isValidateOnSave = false;
	private boolean isComplete = false;
	private boolean isCompleteChecked = false;
	private boolean isValid = false;
	private boolean isValidChecked = false;
	private boolean isValidForms = false;
	private boolean isValidMetadata = false;
	private boolean isMetadataChecked = false;
	private boolean isSerialized = false;
	private boolean isClear = false;

	private File rootDir = null;
	private String name = new String("bag_");
	private long size;
	private File file;
	private long totalSize = 0;

	protected Bag bilBag;
	protected HolePuncher puncher;
	private ValidVerifierImpl validVerifier;
	private Bag bagToValidate;
	protected DefaultBagInfo bagInfo = null;
	protected Verifier bagStrategy;
	protected BaggerFetch fetch;
	private Project project;
	private BagFactory bagFactory = new BagFactory();
	private DefaultCompleter completer;
	private boolean includeTags = false;
	private boolean includePayloadDirectoryInUrl = false;
	private String versionString = null;
	private File bagFileName = null;

	public DefaultBag () {
        this.versionString = Version.V0_96.versionString;
		init(null);
	}
	
	public DefaultBag(String version) {
		this.versionString = version;
		init(null);
	}

	public DefaultBag(File rootDir, String version) {
		this.versionString = version;
		init(rootDir);
    }
	
	protected void display(String s) {
		//System.out.println(this.getClass().getName() + ": " + s);
		log.info(this.getClass().getName() + ": " + s);
	}

	private void init(File rootDir) {
		reset();
		this.rootDir = rootDir;
		
		display("DefaultBag.init file: " + rootDir + ", version: " + versionString);
		if (rootDir != null) {
			bilBag = bagFactory.createBag(this.rootDir);
			versionString = bilBag.getVersion().versionString;
		} else if (versionString != null) {
			Version version = Version.valueOfString(versionString);
			bilBag = bagFactory.createBag(version);
		} else {
			bilBag = bagFactory.createBag();
		}
		bagInfo = new DefaultBagInfo(this);
		initializeBagInfo();
		BagItTxt bagIt = bilBag.getBagItTxt();
		if (bagIt == null) {
			bagIt = bilBag.getBagPartFactory().createBagItTxt();
			bilBag.putBagFile(bagIt);
		}
		puncher = new HolePuncherImpl(bagFactory);
		FetchTxt fetchTxt = bilBag.getFetchTxt();
		if (fetchTxt != null && !fetchTxt.isEmpty()) {
    		String url = getBaseUrl(fetchTxt);
    		if (url != null && !url.isEmpty()) {
            	isHoley(true);
            	BaggerFetch fetch = this.getFetch();
            	fetch.setBaseURL(url);
            	this.fetch = fetch;
    		} else {
    			isHoley(false);
    		}
		}
		this.payloadManifestAlgorithm = Manifest.Algorithm.MD5.bagItAlgorithm;
		this.tagManifestAlgorithm = Manifest.Algorithm.MD5.bagItAlgorithm;
	}
	
	public void createPreBag(File data, String version) {
		PreBag preBag = bagFactory.createPreBag(data);
		if (version == null) {
			Bag bag = preBag.makeBagInPlace(BagFactory.LATEST, false);
			bilBag = bag;
		} else {
			Bag bag = preBag.makeBagInPlace(Version.valueOfString(version), false);
			bilBag = bag;
		}
	}
	
	public BagFactory getBagFactory() {
		return this.bagFactory;
	}
	
	public File getBagFileName() {
		return bagFileName;
	}
	
	public void setBagFileName(File fname) {
		this.bagFileName = fname;
	}

	public String getDataDirectory() {
		return bilBag.getBagConstants().getDataDirectory();
	}
	
	protected void reset() {
		this.isValidateOnSave = false;
		this.isComplete = false;
		this.isCompleteChecked = false;
		this.isValid = false;
		this.isValidChecked = false;
		this.isValidForms = false;
		this.isValidMetadata = false;
		this.isMetadataChecked = false;
		this.isSerialized = false;
	}

	public Bag getBag() {
		return this.bilBag;
	}
	
	public void setBag(Bag bag) {
		this.bilBag = bag;
	}

	public void setVersion(String v) {
		this.versionString = v;
	}
	
	public String getVersion() {
		return this.versionString;
	}

	public void setName(String name) {
		String[] list = name.split("\\.");
		if (list != null && list.length > 0) name = list[0];
		this.name = name;
		this.getInfo().setBagName(name);
	}

	public String getName() {
		return this.name;
	}

	public void setSize(long size) {
		this.size = size;
    	String bagSize = "";
        long fsize = size;
        bagSize += fsize + " ";
        if (fsize > DefaultBag.GB) {
        	fsize /= DefaultBag.GB;
        	bagSize = "" + fsize + " GB";
        } else if (fsize > DefaultBag.MB) {
        	fsize /= DefaultBag.MB;
        	bagSize = "" + fsize + " MB";
        } else if (fsize > DefaultBag.KB) {
        	fsize /= DefaultBag.KB;
        	bagSize = "" + fsize + " KB";
        } else {
        	bagSize += "Bytes";
        }
    	bagInfo.setBagSize(bagSize);
	}

	public long getSize() {
		return this.size;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public File getFile() {
		return this.file;
	}

	// This directory contains either the bag directory or serialized bag file
	public void setRootDir(File rootDir) {
		this.rootDir = rootDir;
	}

	public File getRootDir() {
		return this.rootDir;
	}

	public void isClear(boolean b) {
		this.isClear = b;
	}

	public boolean isClear() {
		return this.isClear;
	}

	public void isHoley(boolean b) {
		this.isHoley = b;
	}

	public boolean isHoley() {
		return this.isHoley;
	}

	public void isSerial(boolean b) {
		this.isSerial = b;
	}

	public boolean isSerial() {
		return this.isSerial;
	}
	
	public void setSerialMode(short m) {
		this.serialMode = m;
	}
	
	public short getSerialMode() {
		return this.serialMode;
	}

	public void isNoProject(boolean b) {
		this.isNoProject = b;
	}
	
	public boolean isNoProject() {
		return this.isNoProject;
	}
	
	public void isEdeposit(boolean b) {
		this.isEdeposit = b;
	}

	public boolean isEdeposit() {
		return this.isEdeposit;
	}

	public void isNdnp(boolean b) {
		this.isNdnp = b;
	}

	public boolean isNdnp() {
		return this.isNdnp;
	}
	
	public void isNewbag(boolean b) {
		this.isNewbag = b;
	}
	
	public boolean isNewbag() {
		return this.isNewbag;
	}
	
	public void isBuildTagManifest(boolean b) {
		this.isBuildTagManifest = b;
	}
	
	public boolean isBuildTagManifest() {
		return this.isBuildTagManifest;
	}

	public void isBuildPayloadManifest(boolean b) {
		this.isBuildPayloadManifest = b;
	}
	
	public boolean isBuildPayloadManifest() {
		return this.isBuildPayloadManifest;
	}

	public void setTagManifestAlgorithm(String s) {
		this.tagManifestAlgorithm = s;
	}
	
	public String getTagManifestAlgorithm() {
		return this.tagManifestAlgorithm;
	}

	public void setPayloadManifestAlgorithm(String s) {
		this.payloadManifestAlgorithm = s;
	}
	
	public String getPayloadManifestAlgorithm() {
		return this.payloadManifestAlgorithm;
	}

	public void isCompleteChecked(boolean b) {
		this.isCompleteChecked = b;
	}
	
	public boolean isCompleteChecked() {
		return this.isCompleteChecked;
	}
	
	public void isValidChecked(boolean b) {
		this.isValidChecked = b;
	}
	
	public boolean isValidChecked() {
		return this.isValidChecked;
	}
	
	public void isMetadataChecked(boolean b) {
		this.isMetadataChecked = b;
	}
	
	public boolean isMetadataChecked() {
		return this.isMetadataChecked;
	}
	
	public void isValidateOnSave(boolean b) {
		this.isValidateOnSave = b;
	}
	
	public boolean isValidateOnSave() {
		return this.isValidateOnSave;
	}
	
	public void isComplete(boolean b) {
		this.isComplete = b;
	}

	public boolean isComplete() {
		return this.isComplete;
	}

	public void isValid(boolean b) {
		this.isValid = b;
	}

	public boolean isValid() {
		return this.isValid;
	}

	public void isValidForms(boolean b) {
		this.isValidForms = b;
	}

	public boolean isValidForms() {
		return this.isValidForms;
	}

	public void isValidMetadata(boolean b) {
		this.isValidMetadata = b;
	}

	public boolean isValidMetadata() {
		return this.isValidMetadata;
	}

	public void isSerialized(boolean b) {
		this.isSerialized = b;
	}

	public boolean isSerialized() {
		return this.isSerialized;
	}

	private void initializeBagInfo() {
		BagInfoTxt bagInfoTxt = bilBag.getBagInfoTxt();
		if (bagInfoTxt == null) {
			bagInfoTxt = bilBag.getBagPartFactory().createBagInfoTxt();
			/* */
			Set<String> keys = bagInfoTxt.keySet();
			for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
				String key = (String) iter.next();
				bagInfoTxt.remove(key);
			}
			/* */
			bilBag.putBagFile(bagInfoTxt);
		}
	}
	
	public void copyBagToForm() {
		copyBagToFields();
		BagInfoTxt bagInfoTxt = this.bilBag.getBagInfoTxt();
		if (bagInfoTxt == null) {
			return;
		}
		BaggerOrganization baggerOrganization = new BaggerOrganization();
		Contact contact = new Contact();
		if (bagInfoTxt.getContactName() != null && !bagInfoTxt.getContactName().trim().isEmpty()) 
    		contact.setContactName(bagInfoTxt.getContactName());
		else
    		contact.setContactName("");
		if (bagInfoTxt.getContactPhone() != null && !bagInfoTxt.getContactPhone().trim().isEmpty()) 
    		contact.setTelephone(bagInfoTxt.getContactPhone());
		else
    		contact.setTelephone("");
		if (bagInfoTxt.getContactEmail() != null && !bagInfoTxt.getContactEmail().trim().isEmpty()) 
    		contact.setEmail(bagInfoTxt.getContactEmail());
		else
    		contact.setEmail("");
		baggerOrganization.setContact(contact);
		if (bagInfoTxt.getSourceOrganization() != null && !bagInfoTxt.getSourceOrganization().trim().isEmpty()) 
    		baggerOrganization.setSourceOrganization(bagInfoTxt.getSourceOrganization());
		else
    		baggerOrganization.setSourceOrganization("");
		if (bagInfoTxt.getOrganizationAddress() != null && !bagInfoTxt.getOrganizationAddress().trim().isEmpty()) 
    		baggerOrganization.setOrganizationAddress(bagInfoTxt.getOrganizationAddress());
		else
    		baggerOrganization.setOrganizationAddress("");
		this.bagInfo.setBagOrganization(baggerOrganization);
		/* */
		if (bagInfoTxt.getExternalDescription() != null && !bagInfoTxt.getExternalDescription().trim().isEmpty())
			this.bagInfo.setExternalDescription(bagInfoTxt.getExternalDescription());
		else
			this.bagInfo.setExternalDescription("");
		if (bagInfoTxt.getBaggingDate() != null && !bagInfoTxt.getBaggingDate().trim().isEmpty())
			this.bagInfo.setBaggingDate(bagInfoTxt.getBaggingDate());
		else
			this.bagInfo.setBaggingDate(DefaultBagInfo.getTodaysDate());
		if (bagInfoTxt.getExternalIdentifier() != null && !bagInfoTxt.getExternalIdentifier().trim().isEmpty())
			this.bagInfo.setExternalIdentifier(bagInfoTxt.getExternalIdentifier());
		else
			this.bagInfo.setExternalIdentifier("");
		if (bagInfoTxt.getBagSize() != null && !bagInfoTxt.getBagSize().trim().isEmpty())
			this.bagInfo.setBagSize(bagInfoTxt.getBagSize());
		else
			this.bagInfo.setBagSize("");
		if (bagInfoTxt.getPayloadOxum() != null && !bagInfoTxt.getPayloadOxum().trim().isEmpty())
			this.bagInfo.setPayloadOxum(bagInfoTxt.getPayloadOxum());
		else
			this.bagInfo.setPayloadOxum("");
		if (bagInfoTxt.getBagGroupIdentifier() != null && !bagInfoTxt.getBagGroupIdentifier().trim().isEmpty())
			this.bagInfo.setBagGroupIdentifier(bagInfoTxt.getBagGroupIdentifier());
		else
			this.bagInfo.setBagGroupIdentifier("");
		if (bagInfoTxt.getBagCount() != null && !bagInfoTxt.getBagCount().trim().isEmpty())
			this.bagInfo.setBagCount(bagInfoTxt.getBagCount());
		else
			this.bagInfo.setBagCount("");
		if (bagInfoTxt.getInternalSenderIdentifier() != null && !bagInfoTxt.getInternalSenderIdentifier().trim().isEmpty())
			this.bagInfo.setInternalSenderIdentifier(bagInfoTxt.getInternalSenderIdentifier());
		else
			this.bagInfo.setInternalSenderIdentifier("");
		if (bagInfoTxt.getInternalSenderDescription() != null && !bagInfoTxt.getInternalSenderDescription().trim().isEmpty())
			this.bagInfo.setInternalSenderDescription(bagInfoTxt.getInternalSenderDescription());
		else
			this.bagInfo.setInternalSenderDescription("");
		if (bagInfoTxt.containsKey(DefaultBagInfo.FIELD_EDEPOSIT_PUBLISHER)) {
			String publisher = bagInfoTxt.get(DefaultBagInfo.FIELD_EDEPOSIT_PUBLISHER);
			if (publisher != null && !publisher.trim().isEmpty()) {
				this.bagInfo.setPublisher(publisher);
			} else {
				this.bagInfo.setPublisher("");
			}
			this.isEdeposit(true);
		}
		if (bagInfoTxt.containsKey(DefaultBagInfo.FIELD_NDNP_AWARDEE_PHASE)) {
			String awardeePhase = bagInfoTxt.get(DefaultBagInfo.FIELD_NDNP_AWARDEE_PHASE);
			if (awardeePhase != null && !awardeePhase.trim().isEmpty()) {
				this.bagInfo.setAwardeePhase(awardeePhase);
			} else {
				this.bagInfo.setAwardeePhase("");
			}
			this.isNdnp(true);
		}
/* */
		if (bagInfoTxt.containsKey(DefaultBagInfo.FIELD_LC_PROJECT)) {
			String lcProject = bagInfoTxt.get(DefaultBagInfo.FIELD_LC_PROJECT);
			if (lcProject != null && !lcProject.trim().isEmpty()) {
				this.bagInfo.setLcProject(lcProject);
				this.isNoProject(false);
			} else {
				this.bagInfo.setLcProject("");
				this.isNoProject(true);
			}
		} else {
			this.isNoProject(true);
		}
/* */
	}
	
	public void parseBagInfoDefaults(String defaults) {
    	log.debug("DefaultBag.parseBagInfoDefaults: " + defaults);
    	ArrayList<String> tokens = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(defaults, "=");
		while (st.hasMoreTokens()) {
			  String s=st.nextToken();
			  tokens.add(s);
		}
		HashMap<String, BagInfoField> currentMap = bagInfo.getFieldMap();
		if (currentMap == null) currentMap = new HashMap<String, BagInfoField>();
		if (tokens != null && !tokens.isEmpty()) {
			String fieldKey = "";
			String fieldValue = "";
			for (int i=0; i<tokens.size(); i++) {
				String bagInfoLine = tokens.get(i);
				if (i == 0) {
					if (bagInfoLine.startsWith("{"))
						fieldKey = bagInfoLine.substring(1);
					else
						fieldKey = bagInfoLine.trim();
				} else if (i == tokens.size()-1) {
					fieldValue = bagInfoLine.substring(0, bagInfoLine.length()-1);
					BagInfoField field = new BagInfoField();
		    		field.isEnabled(true);
		    		field.setName(fieldKey.trim());
		    		field.setLabel(fieldKey.trim());
		    		field.setValue(fieldValue.trim());
		    		if (bagInfo.textAreaSet.contains(field.getLabel()) ||
		    				field.getValue().length() > BagInfoField.MAX_VALUE) {
		    			field.setComponentType(BagInfoField.TEXTAREA_COMPONENT);
		    		}
		    		log.debug("currentMap.addfield: " + field.getLabel() + "=" + field.getValue());
		    		if (currentMap.isEmpty() || !currentMap.containsKey(field.getLabel())) {
		    			if (!bagInfo.profileSet.contains(field.getLabel())) {
			    			currentMap.put(field.getLabel(), field);
		    			}
		    		}
				} else {
					int pos = bagInfoLine.lastIndexOf(", ");
					fieldValue = bagInfoLine.substring(0, pos);
					BagInfoField field = new BagInfoField();
		    		field.isEnabled(true);
		    		field.setName(fieldKey.trim());
		    		field.setLabel(fieldKey.trim());
		    		field.setValue(fieldValue.trim());
		    		if (bagInfo.textAreaSet.contains(field.getLabel()) ||
		    				field.getValue().length() > BagInfoField.MAX_VALUE) {
		    			field.setComponentType(BagInfoField.TEXTAREA_COMPONENT);
		    		}
		    		log.debug("currentMap.addfield: " + field.getLabel() + "=" + field.getValue());
		    		if (currentMap.isEmpty() || !currentMap.containsKey(field.getLabel())) {
		    			if (!bagInfo.profileSet.contains(field.getLabel())) {
			    			currentMap.put(field.getLabel(), field);
		    			}
		    		}
		    		fieldKey = bagInfoLine.substring(pos+1);
				}
			}
			bagInfo.setFieldMap(currentMap);
            this.setInfo(bagInfo);
		}
	}

	public void copyBagToFields() {
		BagInfoTxt bagInfoTxt = this.bilBag.getBagInfoTxt();
		HashMap<String, BagInfoField> fieldMap = bagInfo.getFieldMap();
		if (fieldMap != null) {
			Set<String> keys = fieldMap.keySet();
			for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
				String label = (String) iter.next();
				BagInfoField field = fieldMap.get(label);
				String key = field.getLabel();
				String value = bagInfoTxt.get(key);
				field.setValue(value);
				fieldMap.put(label, field);
			}
			this.bagInfo.setFieldMap(fieldMap);
		}
		HashMap<String, BagInfoField> profileMap = bagInfo.getProfileMap();
		if (profileMap != null) {
			Set<String> pkeys = profileMap.keySet();
			for (Iterator<String> iter = pkeys.iterator(); iter.hasNext();) {
				String label = (String) iter.next();
				BagInfoField field = profileMap.get(label);
				String key = field.getLabel();
				String value = bagInfoTxt.get(key);
				field.setValue(value);
				profileMap.put(label, field);
			}
			this.bagInfo.setProfileMap(profileMap);
		}
	}

	public void createBagInfo(HashMap<String,String> map) {
		initializeBagInfo();
		BagInfoTxt bagInfoTxt = bilBag.getBagInfoTxt();
		bilBag.getBagInfoTxt().clear();
		Set<String> keys = map.keySet();
		for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
			String key = (String) iter.next();
			String value = (String) map.get(key);
			if (!value.trim().isEmpty()) {
				bagInfoTxt.put(key, value);
				copyMapToBag(key, value);
			}
		}
		bilBag.putBagFile(bagInfoTxt);
		display("DefaultBag.createBagInfo bagInfo:" + this.bilBag.getBagInfoTxt());
	}

	private void copyMapToBag(String key, String value) {
		if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_CONTACT_NAME)) {
			bagInfo.getBagOrganization().getContact().setContactName(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_CONTACT_PHONE)) {
			bagInfo.getBagOrganization().getContact().setTelephone(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_CONTACT_EMAIL)) {
			bagInfo.getBagOrganization().getContact().setEmail(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_SOURCE_ORGANIZATION)) {
			bagInfo.getBagOrganization().setSourceOrganization(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_ORGANIZATION_ADDRESS)) {
			bagInfo.getBagOrganization().setOrganizationAddress(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_EXTERNAL_DESCRIPTION)) {
			bagInfo.setExternalDescription(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_BAGGING_DATE)) {
			bagInfo.setBaggingDate(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_EXTERNAL_IDENTIFIER)) {
			bagInfo.setExternalIdentifier(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_BAG_SIZE)) {
			bagInfo.setBagSize(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_PAYLOAD_OXUM)) {
			bagInfo.setPayloadOxum(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_BAG_GROUP_IDENTIFIER)) {
			bagInfo.setBagGroupIdentifier(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_BAG_COUNT)) {
			bagInfo.setBagCount(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_INTERNAL_SENDER_IDENTIFIER)) {
			bagInfo.setInternalSenderIdentifier(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_INTERNAL_SENDER_DESCRIPTION)) {
			bagInfo.setInternalSenderDescription(value);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_EDEPOSIT_PUBLISHER)) {
			bagInfo.setPublisher(value);
			this.isEdeposit(true);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_NDNP_AWARDEE_PHASE)) {
			bagInfo.setAwardeePhase(value);
			this.isNdnp(true);
		} else if (key.equalsIgnoreCase(DefaultBagInfo.FIELD_LC_PROJECT)) {
			bagInfo.setLcProject(value);
			this.isNoProject(true);
		} 
	}

	public void copyFormToBag() {
		BaggerOrganization baggerOrganization = this.bagInfo.getBagOrganization();
		Contact contact = baggerOrganization.getContact();
		if (bilBag.getBagInfoTxt() != null) {
			if (!baggerOrganization.getSourceOrganization().trim().isEmpty()) {
				bilBag.getBagInfoTxt().setSourceOrganization(baggerOrganization.getSourceOrganization());
			}
			if (!baggerOrganization.getOrganizationAddress().trim().isEmpty()) {
				bilBag.getBagInfoTxt().setOrganizationAddress(baggerOrganization.getOrganizationAddress());
			}
			if (!contact.getContactName().trim().isEmpty()) {
				bilBag.getBagInfoTxt().setContactName(contact.getContactName());
			}
			if (!contact.getTelephone().trim().isEmpty()) {
				bilBag.getBagInfoTxt().setContactPhone(contact.getTelephone());
			}
			if (!contact.getEmail().trim().isEmpty()) {
				bilBag.getBagInfoTxt().setContactEmail(contact.getEmail());
			}
			boolean useFieldMap = false;
			if (useFieldMap) {
				if (!bagInfo.getExternalDescription().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setExternalDescription(bagInfo.getExternalDescription());
				}
				if (!bagInfo.getBaggingDate().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setBaggingDate(bagInfo.getBaggingDate());
				}
				if (!bagInfo.getExternalIdentifier().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setExternalIdentifier(bagInfo.getExternalIdentifier());
				}
				if (!bagInfo.getBagSize().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setBagSize(bagInfo.getBagSize());
				}
				if (!bagInfo.getPayloadOxum().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setPayloadOxum(bagInfo.getPayloadOxum());
				}
				if (!bagInfo.getBagGroupIdentifier().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setBagGroupIdentifier(bagInfo.getBagGroupIdentifier());
				}
				if (!bagInfo.getBagCount().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setBagCount(bagInfo.getBagCount());
				}
				if (!bagInfo.getInternalSenderIdentifier().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setInternalSenderIdentifier(bagInfo.getInternalSenderIdentifier());
				}
				if (!bagInfo.getInternalSenderDescription().trim().isEmpty()) {
					bilBag.getBagInfoTxt().setInternalSenderDescription(bagInfo.getInternalSenderDescription());			
				}
				if (this.isEdeposit() && !bagInfo.getPublisher().trim().isEmpty()) {
					bilBag.getBagInfoTxt().put(DefaultBagInfo.FIELD_EDEPOSIT_PUBLISHER, bagInfo.getPublisher());
				}
				if (this.isNdnp() && !bagInfo.getAwardeePhase().trim().isEmpty()) {
					bilBag.getBagInfoTxt().put(DefaultBagInfo.FIELD_NDNP_AWARDEE_PHASE, bagInfo.getAwardeePhase());		
				}
				if (!this.isNoProject() && !bagInfo.getLcProject().trim().isEmpty()) {
					bilBag.getBagInfoTxt().put(DefaultBagInfo.FIELD_LC_PROJECT, bagInfo.getLcProject());
				}
			}
		}
		display("DefaultBag.copyFormToBag bagInfo:" + this.bilBag.getBagInfoTxt());
	}

	public void setInfo(DefaultBagInfo bagInfo) {
        String bagName = bagInfo.getBagName();
        if (bagName == null || bagName.trim().length() == 0 || bagName.trim().equalsIgnoreCase("null")) {
        	this.setName(this.bagInfo.getName());
        } else {
            this.setName(bagName);
        }
		this.bagInfo.copy(bagInfo);
		this.copyFormToBag();
	}

	public DefaultBagInfo getInfo() {
		return this.bagInfo;
	}

	public String getBagInfoContent() {
		String bicontent = new String();
		if (this.bagInfo != null) {
			bicontent = this.bagInfo.toString();
		}		
		return bicontent;
	}

	// TODO: Bagger currently only supports one base URL location per bag
	public String getBaseUrl(FetchTxt fetchTxt) {
		String httpToken = "http:";
		String delimToken = "bagit";
		String baseUrl = "";
		if (fetchTxt != null) {
			if (!fetchTxt.isEmpty()) {
    			FilenameSizeUrl fsu = fetchTxt.get(0);
    			if (fsu != null) {
    				String url = fsu.getUrl();
    				baseUrl = url;
    				String[] list = url.split(delimToken);
    				for (int i=0; i<list.length; i++) {
    					String s = list[i];
    					if (s.trim().startsWith(httpToken)) {
    						baseUrl = s;
    					}
    				}
    			}
			}
		}
		return baseUrl;
	}

	public void setFetch(BaggerFetch fetch) {
		this.fetch = fetch;
	}

	public BaggerFetch getFetch() {
		if (this.fetch == null) this.fetch = new BaggerFetch();
		return this.fetch;
	}
	
	public List<String> getFetchPayload() {
		List<String> list = new ArrayList<String>();
		
		FetchTxt fetchTxt = this.bilBag.getFetchTxt();
		if (fetchTxt == null) return list;
		if (fetchTxt != null) {
			for (int i=0; i < fetchTxt.size(); i++) {
				FilenameSizeUrl fetch = fetchTxt.get(i);
				String s = fetch.getFilename();
	    		display("DefaultBag.getFetchPayload: " + fetch.toString());
				list.add(s);
			}
		}
		return list;
	}

	public String getDataContent() {
		totalSize = 0;
		StringBuffer dcontent = new StringBuffer();
		dcontent.append(this.getDataDirectory() + "/");
		dcontent.append('\n');
		Collection<BagFile> files = this.bilBag.getPayload();
		for (Iterator<BagFile> it=files.iterator(); it.hasNext(); ) {
        	try {
            	BagFile bf = it.next();
            	if (bf != null) {
                	totalSize += bf.getSize();
                	/* */
                	dcontent.append(bf.getFilepath());
                	dcontent.append('\n');
                	/* */
            	}
        	} catch (Exception e) {
        		log.error("DefaultBag.getDataContent: " + e.getMessage());
        	}
        }
        this.setSize(totalSize);
		return dcontent.toString();
	}
	
	public long getDataSize() {
		return this.totalSize;
	}
	
	public int getDataNumber() {
		return this.bilBag.getPayload().size();
	}
	
	public void setProject(Project project) {
		this.project = project;
	}

	public Project getProject() {
		return this.project;
	}

	public List<String> getPayloadPaths() {
		ArrayList<String> pathList = new ArrayList<String>();
		Collection<BagFile> payload = this.bilBag.getPayload();
        for (Iterator<BagFile> it=payload.iterator(); it.hasNext(); ) {
        	BagFile bf = it.next();
        	pathList.add(bf.getFilepath());
        }
		return pathList;		
	}
	
	public String addTagFile(File f) {
		String message = null;
		if (f != null) {
			try {
				bilBag.addFileAsTag(f);
			} catch (Exception e) {
				message = "Error adding file: " + f + " due to: " + e.getMessage();
			}
		}
		return message;
	}
	
	public String removeTagFile(File f) {
		String message = null;
		if (f != null) {
			try {
				bilBag.removeBagFile(f.getName());
				message = "Removed tag manifest file: " + f.getName();
			} catch (Exception e) {
				message = "Error removing file: " + f + " due to: " + e.getMessage();
			}
		}
		return message;
	}

	public String write(Writer bw) throws Exception {
		String messages = null;
		reset();
		generateManifestFiles();
		if (this.isHoley) {
			if (this.getFetch().getBaseURL() != null) {
				BagInfoTxt bagInfoTxt = bilBag.getBagInfoTxt();
				List<Manifest> manifests = bilBag.getPayloadManifests();
				List<Manifest> tags = bilBag.getTagManifests();
				includeTags = true;
				includePayloadDirectoryInUrl = true;
				bilBag = puncher.makeHoley(bilBag, this.getFetch().getBaseURL(), includePayloadDirectoryInUrl, includeTags);
				// TODO: makeHoley deletes baginfo so put back
				bilBag.putBagFile(bagInfoTxt);
				if (manifests != null) {
					for (int i=0; i<manifests.size(); i++) {
						bilBag.putBagFile(manifests.get(i));
					}
				}
				if (tags != null) {
					for (int i=0; i<tags.size(); i++) {
						bilBag.putBagFile(tags.get(i));
					}
				}
			}
		}
		try {
			messages = writeBag(bw);
		} catch (Exception e) {
			log.error("DefaultBag.write.writeBag: " + e);
			throw new RuntimeException(e);
		}
		return messages;
	}

	public String completeBag(CompleteVerifierImpl completeVerifier, Bag bag) {
		String messages = null;
		try {
			SimpleResult result = completeVerifier.verify(bag);
			if (!result.isSuccess()) {
				messages = "Bag is not complete:\n";
				messages += result.messagesToString();
			}
			this.isComplete(result.isSuccess());
			this.isCompleteChecked = true;
			if (!isNoProject()) {
				try {
					String msgs = validateMetadata();
					if (msgs != null) {
						if (messages != null) messages += msgs;
						else messages = msgs;
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					String msgs = "ERROR validating bag: \n" + ex.getMessage() + "\n";
					if (messages != null) messages += msgs;
					else messages = msgs;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (completeVerifier.isCancelled()) {
				this.isCompleteChecked = false;
				messages = "Completeness check cancelled.";
			} else {
				this.isComplete(false);
				messages = "Error - Invalid result returned from verifier: " + e.getMessage() + "\n";
			}
		}
		return messages;
	}

	public String validateMetadata() {
		String messages = null;
		try {
			updateStrategy();
			SimpleResult result = this.bilBag.verify(bagStrategy);
			if (result.messagesToString() != null && !result.isSuccess()) {
				messages = "Bag-info fields are not all present for the project selected.\n";
				messages += result.messagesToString();
			}
			this.isValidMetadata(result.isSuccess());
			this.isMetadataChecked = true;
		} catch (Exception e) {
			this.isValidMetadata(false);
			messages = "Bag-info fields are not correct: " + e.getMessage() + "\n";
			e.printStackTrace();
		}
		return messages;
	}

	public String validateBag(ValidVerifierImpl validVerifier) {
		String messages = null;
		bagToValidate = bilBag;
		this.validVerifier = validVerifier;
		try {
			SimpleResult result = validVerifier.verify(bagToValidate);
			if (!result.isSuccess()) {
				messages = "Bag is not valid:\n";
				messages += result.messagesToString();
			}
			this.isValid(result.isSuccess());
			if (this.isValid) this.isComplete(this.isValid);
			this.isCompleteChecked = true;
			this.isValidChecked = true;
			if (!isNoProject()) {
				try {
					String msgs = validateMetadata();
					if (msgs != null) {
						if (messages != null) messages += msgs;
						else messages = msgs;
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					String msgs = "ERROR validating bag: \n" + ex.getMessage() + "\n";
					if (messages != null) messages += msgs;
					else messages = msgs;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (validVerifier.isCancelled()) {
				messages = "Validation check cancelled.";
			} else {
				this.isValidChecked = false;
				this.isValid(false);
				messages = "Error - Invalid result returned from verifier: " + e.getMessage() + "\n";
			}
		}
		return messages;
	}
	
	private String fileStripSuffix(String filename) {
		StringTokenizer st = new StringTokenizer(filename, ".");
		String name = st.nextToken();
		return name;
	}
	
	public String writeBag(Writer bw) throws Exception {
		String messages = null;
		String bagName = "";
		File bagFile = null;
		File parentDir = null;
		bagName = fileStripSuffix(getRootDir().getName());
		parentDir = getRootDir().getParentFile();
		log.debug("DefaultBag.writeBag parentDir: " + parentDir + ", bagName: " + bagName );
		try {
			this.setName(bagName);
			if (this.serialMode == NO_MODE) {
				this.isSerialized(true);
				bagFile = new File(parentDir, this.getName());
			} else if (this.serialMode == ZIP_MODE) {
				this.isSerialized(true);
				String s = bagName;
			    int i = s.lastIndexOf('.');
			    if (i > 0 && i < s.length() - 1) {
			    	String sub = s.substring(i + 1);
			    	if (!sub.equalsIgnoreCase(ZIP_LABEL)) {
			    		bagName += "." + ZIP_LABEL;
			    	}
			    } else {
		    		bagName += "." + ZIP_LABEL;
			    }
				bagFile = new File(parentDir, bagName);
				long zipSize = this.getSize() / MB;
				if (zipSize > 100) {
					messages = "WARNING: You may not be able to network transfer files > 100 MB!\n";
				}
			} else if (this.serialMode == TAR_MODE) {
				this.isSerialized(true);
				String s = bagName;
			    int i = s.lastIndexOf('.');
			    if (i > 0 && i < s.length() - 1) {
				      if (!s.substring(i + 1).toLowerCase().equals(TAR_LABEL)) {
							bagName += "." + TAR_LABEL;
				      }
			    } else {
		    		bagName += "." + TAR_LABEL;
			    }
				bagFile = new File(parentDir, bagName);
				long zipSize = this.getSize() / MB;
				if (zipSize > 100) {
					messages = "WARNING: You may not be able to network transfer files > 100 MB!\n";
				}
			} else if (this.serialMode == TAR_GZ_MODE) {
				this.isSerialized(true);
				String s = bagName;
			    int i = s.lastIndexOf('.');
			    if (i > 0 && i < s.length() - 1) {
				      if (!s.substring(i + 1).toLowerCase().equals(TAR_GZ_LABEL)) {
							bagName += "." + TAR_GZ_LABEL;
				      }
			    } else {
		    		bagName += "." + TAR_GZ_LABEL;
			    }
				bagFile = new File(parentDir, bagName);
				long zipSize = this.getSize() / MB;
				if (zipSize > 100) {
					messages = "WARNING: You may not be able to network transfer files > 100 MB!\n";
				}
			} else if (this.serialMode == TAR_BZ2_MODE) {
				this.isSerialized(true);
				String s = bagName;
			    int i = s.lastIndexOf('.');
			    if (i > 0 && i < s.length() - 1) {
				      if (!s.substring(i + 1).toLowerCase().equals(TAR_BZ2_LABEL)) {
							bagName += "." + TAR_BZ2_LABEL;
				      }
			    } else {
		    		bagName += "." + TAR_BZ2_LABEL;
			    }
				bagFile = new File(parentDir, bagName);
				long zipSize = this.getSize() / MB;
				if (zipSize > 100) {
					messages = "WARNING: You may not be able to network transfer files > 100 MB!\n";
				}
			}
			setBagFileName(bagFile);
			Bag newBag = bw.write(bilBag, bagFile);
			if (newBag != null) bilBag = newBag;
			this.isNewbag(false);
/*			if (!isNoProject()) {
				try {
					String msgs = validateMetadata();
					if (msgs != null) {
						if (messages != null) messages += msgs;
						else messages = msgs;
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					String msgs = "ERROR validating bag: \n" + ex.getMessage() + "\n";
					if (messages != null) messages += msgs;
					else messages = msgs;
				}
			}
 */
		} catch (Exception e) {
			this.isSerialized(false);
			String msgs = "ERROR creating bag: " + bagFile + "\n" + e.getMessage() + "\n";
			if (messages != null) messages += msgs;
			else messages = msgs;
			throw new RuntimeException(e);
		}
		return messages;
	}
	
	public void updateStrategy() {
		bagStrategy = getBagInfoStrategy();		
	}

	protected Verifier getBagInfoStrategy() {
		List<String> rulesList = new ArrayList<String>();
		HashMap<String, BagInfoField> fieldMap = this.getInfo().getFieldMap();
		if (fieldMap != null) {
			Set<String> keys = fieldMap.keySet();
			for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
				String label = (String) iter.next();
				BagInfoField field = fieldMap.get(label);
				if (field.isRequired()) {
					rulesList.add(field.getLabel());
				}
			}
		}
		String[] rules = new String[rulesList.size()];
		for (int i=0; i< rulesList.size(); i++) rules[i] = new String(rulesList.get(i));

		Verifier strategy = new RequiredBagInfoTxtFieldsVerifier(rules);

		return strategy;
	}
	
	private void generateManifestFiles() {
		completer = new DefaultCompleter(this.bagFactory);
		if (this.isBuildPayloadManifest) {
			if (this.payloadManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.MD5.bagItAlgorithm)) {
				completer.setPayloadManifestAlgorithm(Algorithm.MD5);
			} else if (this.payloadManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.SHA1.bagItAlgorithm)) {
				completer.setPayloadManifestAlgorithm(Algorithm.SHA1);
			} else if (this.payloadManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.SHA256.bagItAlgorithm)) {
				completer.setPayloadManifestAlgorithm(Algorithm.SHA256);
			} else if (this.payloadManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.SHA512.bagItAlgorithm)) {
				completer.setPayloadManifestAlgorithm(Algorithm.SHA512);
			} else {
				completer.setPayloadManifestAlgorithm(Algorithm.MD5);
			}
			if (this.isHoley) {
				completer.setClearExistingPayloadManifests(true);
			} else {
				completer.setClearExistingPayloadManifests(true);
			}
		}
		if (this.isBuildTagManifest) {
			completer.setClearExistingTagManifests(true);
			completer.setGenerateTagManifest(true);
			if (this.tagManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.MD5.bagItAlgorithm)) {
				completer.setTagManifestAlgorithm(Algorithm.MD5);
			} else if (this.tagManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.SHA1.bagItAlgorithm)) {
				completer.setTagManifestAlgorithm(Algorithm.SHA1);
			} else if (this.tagManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.SHA256.bagItAlgorithm)) {
				completer.setTagManifestAlgorithm(Algorithm.SHA256);
			} else if (this.tagManifestAlgorithm.equalsIgnoreCase(Manifest.Algorithm.SHA512.bagItAlgorithm)) {
				completer.setTagManifestAlgorithm(Algorithm.SHA512);
			} else {
				completer.setTagManifestAlgorithm(Algorithm.MD5);
			}
		}
		if (bilBag.getBagInfoTxt() != null) completer.setGenerateBagInfoTxt(true);
		bilBag = completer.complete(bilBag);
	}

	private void confirmValidateBag() {
	    ConfirmationDialog dialog = new ConfirmationDialog() {
	        protected void onConfirm() {
	        	BusyIndicator.showAt(Application.instance().getActiveWindow().getControl());
				if (validVerifier == null) {
					SimpleResult result = bagToValidate.verifyValid();
					isValid(result.isSuccess());
				} else {
					SimpleResult result = validVerifier.verify(bilBag);
					isValid(result.isSuccess());
				}
		    	BusyIndicator.clearAt(Application.instance().getActiveWindow().getControl());
	        }
	    };

	    dialog.setCloseAction(CloseAction.DISPOSE);
	    dialog.setTitle("Validate Bag");
	    dialog.setConfirmationMessage("The contents of this bag are larger than 100 MB; this may cause performance problems.  Would you like to continue validation?");
	    dialog.showDialog();
	}
}
