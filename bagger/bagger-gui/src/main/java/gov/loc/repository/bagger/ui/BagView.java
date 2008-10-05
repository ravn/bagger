
package gov.loc.repository.bagger.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.*;
import java.io.File;
import java.util.*;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListModel;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.binding.form.HierarchicalFormModel;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.richclient.application.PageComponentContext;
import org.springframework.richclient.application.event.LifecycleApplicationEvent;
import org.springframework.richclient.application.support.AbstractView;
import org.springframework.richclient.command.support.AbstractActionCommandExecutor;
import org.springframework.richclient.command.support.GlobalCommandIds;
import org.springframework.richclient.dialog.ApplicationDialog;
import org.springframework.richclient.dialog.CloseAction;
import org.springframework.richclient.dialog.CompositeDialogPage;
import org.springframework.richclient.dialog.FormBackedDialogPage;
import org.springframework.richclient.dialog.TabbedDialogPage;
import org.springframework.richclient.dialog.AbstractDialogPage;
import org.springframework.richclient.dialog.TitledPageApplicationDialog;
import org.springframework.richclient.form.FormModelHelper;

import gov.loc.repository.bagger.Address;
import gov.loc.repository.bagger.Contact;
import gov.loc.repository.bagger.Organization;
import gov.loc.repository.bagger.Profile;
import gov.loc.repository.bagger.Project;
import gov.loc.repository.bagger.Bagger;
import gov.loc.repository.bagger.bag.*;
import gov.loc.repository.bagger.util.RecursiveFileListIterator;
import com.ravnaandtines.ftp.*;

public class BagView extends AbstractView implements ApplicationListener {
	private static final Log log = LogFactory.getLog(BagView.class);

	private String username;
	private Bagger bagger;
    private Bag bag;
   	private JPanel bagView = null;
    private JTree bagsTree;
    private List<File> rootTree;
    private Collection<Project> userProjects;
    private File rootSrc = null;
    private JFrame rootFrame;
    private DefaultTreeModel bagsTreeModel;
    private OrganizationInfoForm bagInfoForm;
    private OrganizationGeneralForm organizationGeneralForm;
    private OrganizationContactForm organizationContactForm;

    private JScrollPane filePane;
    private JPanel filePanel;
    private JPanel mainPanel;
    private JPanel infoPanel;
    private JTabbedPane infoPane;
    private JTabbedPane compositePane;
    private JScrollPane consoleScrollPane;
    private ConsolePane consolePane;
    private BagIt bagIt = null;
    private BagItPane bagItPane;
    private JScrollPane bagItScrollPane;
    private BagInfo bagInfo = null;
    private BagInfoPane bagInfoPane;
    private JScrollPane bagInfoScrollPane;
    private Data data = null;
    private DataPane dataPane;
    private JScrollPane dataScrollPane;
    private Fetch fetch = null;
    private FetchPane fetchPane;
    private JScrollPane fetchScrollPane;
    private Manifest manifest = null;
    private ManifestPane manifestPane;
    private TagManifest tagManifest = null;
    private ManifestPane tagManifestPane;
    private JScrollPane manifestScrollPane;
    private JScrollPane tagManifestScrollPane;
    private Action openAction;
    private Action saveAction;
    private Action validateAction;
    private Action ftpAction;
    private JButton openButton;
    private JButton saveButton;
    private JButton validateButton;
    private JButton updatePropButton;
    private JButton ftpButton;
    
    private ValidateExecutor validateExecutor = new ValidateExecutor();
    private FtpExecutor ftpExecutor = new FtpExecutor();
    private FtpPropertiesExecutor ftpPropertiesExecutor = new FtpPropertiesExecutor();

    public void setBagger(Bagger bagger) {
        Assert.notNull(bagger, "The bagger property is required");
        display("BagView.setBagger: " );
        this.bagger = bagger;
    }

    public void setBag(Bag bag) {
        this.bag = bag;
    }

    private Bag getBag() {
        return this.bag;
    }
    
    public void setBagNameToDisplay(String bagName) {
        this.bag.setName(bagName);
    }

	public Dimension getMinimumSize() {
		return new Dimension(800, 100);
	}

	public Dimension getPreferredSize() {
		return new Dimension(1000, 800);
	}

	public void display(String s) {
		//log.debug(s);
		log.info(s);
	}
	
    protected void registerLocalCommandExecutors(PageComponentContext context) {
        context.register(GlobalCommandIds.PROPERTIES, validateExecutor);
    }

    private void resize(JFrame f) {
    	rootFrame = f;
        Dimension	sz = Toolkit.getDefaultToolkit().getScreenSize();
        int fud = 200;
        int width = sz.width-fud;
   	    int height = sz.height-fud;
   	    Dimension bd = new Dimension(width, height);
   	    display("BagView.createControl dimensions: " + bd.width + " x " + bd.height);
  	    f.setResizable(true);
  	    f.setLocation(100, 100);
  	    f.setSize( bd.width, bd.height );
    }

/* 
 * 
 * **************************************************************
 *                      ASCII Layout Here
 * **************************************************************
 * 
 */
    @Override
    protected JComponent createControl() {
    	resize(this.getActiveWindow().getControl());

        BorderLayout borderLayout = new BorderLayout();
        borderLayout.setHgap(2);
        borderLayout.setVgap(2);
    	bagView = new JPanel(borderLayout);

        JPanel buttonPanel = createButtonPanel();

    	compositePane = new JTabbedPane();
        compositePane = createBagPane();

        mainPanel = createMainPanel();

        JPanel centerGrid = new JPanel(new GridLayout(1,2,10,10));
        centerGrid.add(mainPanel);
        centerGrid.add(compositePane);
        
        bagView.add(buttonPanel, BorderLayout.NORTH);
        bagView.add(centerGrid, BorderLayout.CENTER);

        return bagView;
    }
    
    private JPanel createButtonPanel() {
    	FlowLayout layout = new FlowLayout();
    	layout.setAlignment(FlowLayout.LEFT);
    	layout.setHgap(10);
    	layout.setVgap(10);
    	JPanel panel = new JPanel(layout);
        String filename = File.separator+".";
        File selectFile = new File(filename);
        rootTree = new ArrayList<File>();
        JFrame frame = new JFrame();
        JFileChooser fc = new JFileChooser(selectFile);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                
        openAction = new OpenFileAction(frame, fc);
    	openButton = new JButton("Bag Data Chooser");
    	openButton.addActionListener(openAction);
        openButton.setMnemonic('o');

        saveAction = new SaveFileAction(frame, fc);
        saveButton = new JButton("Bag Creator");
        saveButton.addActionListener(saveAction);
        saveButton.setMnemonic('s');

        validateAction = new ValidateBagAction();
        validateButton = new JButton("Bag Validator");
        validateButton.addActionListener(validateAction);
        validateButton.setMnemonic('v');
        validateExecutor.setEnabled(true);

        ftpAction = new FtpAction();
        ftpButton = new JButton("Bag Transfer");
        ftpButton.addActionListener(ftpAction);
        ftpButton.setMnemonic('t');
        ftpExecutor.setEnabled(true);

        panel.add(openButton);
        panel.add(saveButton);
        panel.add(validateButton);
        panel.add(ftpButton);
        
        return panel;
    }
    
    private JPanel createMainPanel() {
    	Dimension dimension;
    	GridLayout gridLayout = new GridLayout(2,1,10,10);
    	gridLayout.setHgap(10);
    	gridLayout.setVgap(10);
        mainPanel = new JPanel(gridLayout);
        
/* */
        HierarchicalFormModel organizationFormModel;
        BagOrganization bagOrganization = bagInfo.getBagOrganization();
        organizationFormModel = FormModelHelper.createCompoundFormModel(bagOrganization);
        organizationGeneralForm = new OrganizationGeneralForm(FormModelHelper.createChildPageFormModel(organizationFormModel, null));

        HierarchicalFormModel contactFormModel;
        Contact contact = bagInfo.getBagOrganization().getContact();
        if (contact == null) contact = new Contact();
        contactFormModel = FormModelHelper.createCompoundFormModel(contact);
        organizationContactForm = new OrganizationContactForm(FormModelHelper.createChildPageFormModel(contactFormModel, null));
        
        HierarchicalFormModel infoFormModel;
        infoFormModel = FormModelHelper.createCompoundFormModel(bagInfo);
        bagInfoForm = new OrganizationInfoForm(FormModelHelper.createChildPageFormModel(infoFormModel, null));
        dimension = bagInfoForm.getControl().getPreferredSize();
        bagInfoForm.getControl().setPreferredSize(dimension);
        
        infoPane = new JTabbedPane();
        infoPane.addTab("Information", bagInfoForm.getControl());
        infoPane.addTab("Organization", organizationGeneralForm.getControl());
        infoPane.addTab("Contact", organizationContactForm.getControl());
        infoPane.setMinimumSize(dimension);
        /* */
        Border border = new EmptyBorder(5, 5, 5, 5);
        
        Action updatePropAction = new UpdatePropertyAction();
        updatePropButton = new JButton("Save Updates");
        updatePropButton.addActionListener(updatePropAction);
        updatePropButton.setMnemonic('u');
        updatePropButton.setBorder(border);
        
        DefaultListModel listModel = new DefaultListModel();
        Object[] array = userProjects.toArray();
        for (int i=0; i < userProjects.size(); i++) listModel.addElement(((Project)array[i]).getName());
        JList projectList = new JList(listModel);
        projectList.setName("Bag Project");
        projectList.setSelectedIndex(0);
        projectList.setVisibleRowCount(1);
        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
            	JList jlist = (JList)e.getSource();
            	String selected = (String) jlist.getSelectedValue();
            	display("valueChanged: " + selected);
                Object[] project_array = userProjects.toArray();
                for (int i=0; i < userProjects.size(); i++) {
                	Project project = (Project)project_array[i];
                	if (selected.equalsIgnoreCase(project.getName())) {
                		bag.setProject(project);
                	}
                }
            	if (selected.equalsIgnoreCase("copyright")) {
            		bag.setIsCopyright(true);
            	} else {
            		bag.setIsCopyright(false);
            	}
            	log.info("BagView.setIsCopyright: " + bag.getIsCopyright());
            }
        });
        JPanel projectPane = new JPanel(new BorderLayout());
        JScrollPane listPane = new JScrollPane(projectList);
        /* */
    	String selected = (String) projectList.getSelectedValue();
    	if (selected.equalsIgnoreCase("copyright")) {
    		bag.setIsCopyright(true);
    	} else {
    		bag.setIsCopyright(false);
    	}
    	log.info("BagView.setIsCopyright: " + bag.getIsCopyright());
    	/* */
        projectPane.add(new JLabel("Bag project: "), BorderLayout.WEST);
        projectPane.add(listPane, BorderLayout.CENTER);
        
        JLabel groupLabel = new JLabel("Checksum Type: ");        
        JRadioButton md5Button = new JRadioButton("MD5");
        md5Button.setSelected(true);
        JRadioButton sha1Button = new JRadioButton("SHA1");
        sha1Button.setSelected(false);        
        ButtonGroup group = new ButtonGroup();
        group.add(md5Button);
        group.add(sha1Button);        
        JPanel groupPanel = new JPanel(new FlowLayout());
        groupPanel.add(groupLabel);
        groupPanel.add(md5Button);
        groupPanel.add(sha1Button);
        groupPanel.setBorder(border);

        JCheckBox holeyCheckbox = new JCheckBox("Holey Bag");
        holeyCheckbox.setBorder(border);
        
        GridBagLayout infoLayout = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();

        buildConstraints(gbc, 0, 0, 1, 4, 70, 0, GridBagConstraints.BOTH, GridBagConstraints.WEST);
        infoLayout.setConstraints(infoPane, gbc);

        buildConstraints(gbc, 1, 0, 1, 1, 10, 20, GridBagConstraints.NONE, GridBagConstraints.WEST);
        infoLayout.setConstraints(updatePropButton, gbc);

        buildConstraints(gbc, 1, 1, 1, 1, 10, 20, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        infoLayout.setConstraints(projectPane, gbc);

        buildConstraints(gbc, 1, 2, 1, 1, 10, 20, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        infoLayout.setConstraints(groupPanel, gbc);

        buildConstraints(gbc, 1, 3, 1, 1, 10, 20, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        infoLayout.setConstraints(holeyCheckbox, gbc);

        infoPanel = new JPanel(infoLayout);
        Border emptyBorder = new EmptyBorder(10, 10, 10, 10);
        infoPanel.setBorder(emptyBorder);
        infoPanel.add(infoPane);
        infoPanel.add(updatePropButton);
        infoPanel.add(projectPane);
        infoPanel.add(holeyCheckbox);
        infoPanel.add(groupPanel);
        
        bagsTree = new JTree();
        bagsTree.setLargeModel(true);
        bagsTree.setPreferredSize(this.getMinimumSize());
        filePane = new JScrollPane();
        filePane.setViewportView(bagsTree);
        filePanel = new JPanel(new FlowLayout());
        filePanel.setPreferredSize(this.getMinimumSize());
        filePanel.add(filePane);

        mainPanel.add(filePanel);
        mainPanel.add(infoPanel);

        return mainPanel;
    }

    private void buildConstraints(GridBagConstraints gbc,int x, int y, int w, int h, int wx, int wy, int fill, int anchor) {
    	gbc.gridx = x; // start cell in a row
    	gbc.gridy = y; // start cell in a column
    	gbc.gridwidth = w; // how many column does the control occupy in the row
    	gbc.gridheight = h; // how many column does the control occupy in the column
    	gbc.weightx = wx; // relative horizontal size
    	gbc.weighty = wy; // relative vertical size
    	gbc.fill = fill; // the way how the control fills cells
    	gbc.anchor = anchor; // alignment
    }

    private JTabbedPane createBagPane() {
    	createBag();
    	initializeBag();

    	consoleScrollPane = new JScrollPane();
    	consolePane = new ConsolePane();
    	consoleScrollPane.setViewportView(consolePane);
    	compositePane.addTab("Console", consoleScrollPane);
    	
    	manifestScrollPane = new JScrollPane();
    	manifestPane = new ManifestPane();
    	manifestScrollPane.setViewportView(manifestPane);
    	compositePane.addTab("Manifest", manifestScrollPane);

    	tagManifestScrollPane = new JScrollPane();
    	tagManifestPane = new ManifestPane();
    	tagManifestScrollPane.setViewportView(tagManifestPane);
    	compositePane.addTab("TagManifest", tagManifestScrollPane);

    	bagInfoScrollPane = new JScrollPane();
        bagInfoPane = new BagInfoPane();
        bagInfoScrollPane.setViewportView(bagInfoPane);
        compositePane.addTab("Bag It Info", bagInfoScrollPane);

        dataScrollPane = new JScrollPane();
    	dataPane = new DataPane();
    	dataScrollPane.setViewportView(dataPane);
    	compositePane.addTab("Data", dataScrollPane);
    	
    	fetchScrollPane = new JScrollPane();
        fetchPane = new FetchPane();
    	fetchScrollPane.setViewportView(fetchPane);
    	if (this.bag.getIsHoley()) {
            compositePane.addTab("Fetch", fetchScrollPane);    		
    	}

        bagItScrollPane = new JScrollPane();
        bagItPane = new BagItPane();
        bagItScrollPane.setViewportView(bagItPane);
        compositePane.addTab("Bag It", bagItScrollPane);

    	return compositePane;
    }
    
    private JTabbedPane populateBagPane(String messages) {
    	createBag();

    	consoleScrollPane = new JScrollPane();
    	consolePane = new ConsolePane(bag, messages);
    	consoleScrollPane.setViewportView(consolePane);
    	compositePane.addTab("Console", consoleScrollPane);

    	String mcontent = new String();
    	if (bag.getManifests() != null && bag.getManifests().size() > 0) {
        	List<Manifest> manifests = bag.getManifests();
    		StringBuffer sb = new StringBuffer();
        	for (int i=0; i < manifests.size(); i++) {
        		sb.append(manifests.get(i).getName());
        		sb.append('\n');
        		sb.append(manifests.get(i).toString());
        		sb.append('\n');
        	}
        	mcontent = sb.toString();
    	}
    	manifestScrollPane = new JScrollPane();
    	manifestPane = new ManifestPane(mcontent);
    	manifestScrollPane.setViewportView(manifestPane);
    	compositePane.addTab("Manifest", manifestScrollPane);

    	String tmcontent = new String();
    	if (bag.getTagManifests() != null && bag.getTagManifests().size() > 0) {
        	List<TagManifest> tagManifests = bag.getTagManifests();
    		StringBuffer sb = new StringBuffer();
        	for (int i=0; i < tagManifests.size(); i++) {
        		sb.append(tagManifests.get(i).getName());
        		sb.append('\n');
        		sb.append(tagManifests.get(i).toString());
        		sb.append('\n');
        	}
        	tmcontent = sb.toString();
    	}
    	tagManifestScrollPane = new JScrollPane();
    	tagManifestPane = new ManifestPane(tmcontent);
    	tagManifestScrollPane.setViewportView(tagManifestPane);
    	compositePane.addTab("TagManifest", tagManifestScrollPane);

    	bagInfoScrollPane = new JScrollPane();
        bagInfoPane = new BagInfoPane(bag.getInfo());
        bagInfoScrollPane.setViewportView(bagInfoPane);
        compositePane.addTab("Bag It Info", bagInfoScrollPane);

        dataScrollPane = new JScrollPane();
    	dataPane = new DataPane(bag.getData());
    	dataScrollPane.setViewportView(dataPane);
    	compositePane.addTab("Data", dataScrollPane);

    	fetchScrollPane = new JScrollPane();
        fetchPane = new FetchPane(bag.getFetch());
    	fetchScrollPane.setViewportView(fetchPane);
    	if (this.bag.getIsHoley()) {
            compositePane.addTab("Fetch", fetchScrollPane);    		
    	}

        bagItScrollPane = new JScrollPane();
        bagItPane = new BagItPane(bag.getBagIt());
        bagItScrollPane.setViewportView(bagItPane);
        compositePane.addTab("Bag It", bagItScrollPane);

    	return compositePane;
    }

    private void createBag() {
        if (bag == null) bag = new Bag();
        if (fetch == null) fetch = new Fetch();
        else fetch = bag.getFetch();
        bag.setFetch(fetch);
        if (bagInfo == null) bagInfo = new  BagInfo();
        else bagInfo = bag.getInfo();
        bag.setInfo(bagInfo);
        if (bagIt == null) bagIt = new BagIt();
        else bagIt = bag.getBagIt();
        bag.setBagIt(bagIt);
    	if (data == null) data = new Data();
    	else data = bag.getData();
    	data.setFiles(rootTree);
    	bag.setData(data);
    	if (rootSrc != null) bag.setRootSrc(rootSrc);
    	manifest = new Manifest(bag);
    	manifest.setType(ManifestType.MD5);
    	ArrayList<Manifest> mset = new ArrayList<Manifest>();
    	mset.add(manifest);
    	bag.setManifests(mset);
    	List<TagManifest> tagManifestList = bag.getTagManifests();
    	if (tagManifestList == null || tagManifestList.isEmpty()) {
        	ArrayList<TagManifest> tmset = new ArrayList<TagManifest>();
        	tagManifest = new TagManifest(bag);
        	tagManifest.setType(ManifestType.MD5);
        	tmset.add(tagManifest);    		
        	bag.setTagManifests(tmset);
    	}
    }

    private void initializeBag() {
    	Authentication a = SecurityContextHolder.getContext().getAuthentication();
    	if (a != null) {
        	this.username = a.getName();
        	display("BagView.creatControl getAuthenticationUser:: " + this.username);
        	display("BagView.createControl projects: " + bagger.getProjects());
        	Collection<Profile> profiles = bagger.findProfiles(a.getName());
        	Object[] profileArray = profiles.toArray();
        	for (int i=0; i < profileArray.length; i++) {
        		display("BagView.createControl profile:\n" + profileArray[i].toString());
        		Profile profile = (Profile) profileArray[i];
        		userProjects = bagger.findProjects(profile.getPerson().getId());
        		Organization org = profile.getPerson().getOrganization();
        		Address address = org.getAddress();
        		bagInfo = bag.getInfo();
        		BagOrganization bagOrg = bagInfo.getBagOrganization();
        		bagOrg.setContact(profile.getContact());
        		bagOrg.setOrgName(org.getName());
        		bagOrg.setOrgAddress(address.toString(true));
        		bagInfo.setBagOrganization(bagOrg);
        		bag.setInfo(bagInfo);
        	}
    	} else {
    		userProjects = bagger.getProjects();
    	}
    }
    	    
    private class OpenFileAction extends AbstractAction {
		private static final long serialVersionUID = -5915870395535673069L;
		JFrame frame;
        JFileChooser chooser;
    
        OpenFileAction(JFrame frame, JFileChooser chooser) {
            super("Open...");
            chooser.setDialogType(JFileChooser.OPEN_DIALOG);
            this.chooser = chooser;
            this.frame = frame;
        }
    
        public void actionPerformed(ActionEvent evt) {
            // Show dialog; this method does not return until dialog is closed
        	int option = chooser.showOpenDialog(frame);
    
            // Get the selected file
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                openBag(file);
            }
        }
    }
    
    private void openBag(File file) {
    	rootFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        rootSrc = file.getAbsoluteFile(); //file.getParentFile();
        display("OpenFileAction.actionPerformed filePath: " + file.getPath() + " rootPath: " + rootSrc.getPath() );
        String messages = "Adding " + file.getPath() + " to the bag.";
        updateMessages(messages);
    	/* */
        bag = getBag();
        bagInfoForm.commit();
        BagInfo newInfo = (BagInfo)bagInfoForm.getFormObject();
        bag.setInfo(newInfo);
        setBag(bag);
    	/* */
        updateTree(file);
        bag.setRootDir(rootSrc);
    	rootFrame.setCursor(Cursor.getDefaultCursor());
    }
    
 // This action creates and shows a modal save-file dialog.
    public class SaveFileAction extends AbstractAction {
		private static final long serialVersionUID = -3466819146072877868L;
		JFileChooser chooser;
        JFrame frame;
    
        SaveFileAction(JFrame frame, JFileChooser chooser) {
            super("Save As...");
            chooser.setDialogType(JFileChooser.SAVE_DIALOG);
            this.chooser = chooser;
            this.frame = frame;
        }
    
        public void actionPerformed(ActionEvent evt) {
            // Show dialog; this method does not return until dialog is closed
        	int option = chooser.showSaveDialog(frame);
    
            // Get the selected file
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                saveBag(file);
            }
        }
    }
    
    private void saveBag(File file) {
    	rootFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        display("BagView.SaveFileAction: " + file);
        String messages = "Creating the bag...";
        updateMessages(messages);

        bag = getBag();
        bagInfoForm.commit();
        BagInfo newInfo = (BagInfo)bagInfoForm.getFormObject();
        bag.setInfo(newInfo);
        setBag(bag);

    	// TODO Break this down into multiple steps so that each step can send bag progress message to the console.
        // TODO What if file already exists?  Error or message to overwrite
        messages = bag.write(file);
       	display("\nBagView.SaveFileAction: " + messages);
    	updateTabs(messages);
    	rootFrame.setCursor(Cursor.getDefaultCursor());
    }

    private class ValidateBagAction extends AbstractAction {
		private static final long serialVersionUID = 2256331268073462469L;

		ValidateBagAction() {
            super("Validate...");
        }
    
        public void actionPerformed(ActionEvent e) {
            // Show dialog; this method does not return until dialog is closed
            if (validateExecutor.isEnabled()) {
                validateExecutor.execute();
            }
        }
    }
    
    private class UpdatePropertyAction extends AbstractAction {
		private static final long serialVersionUID = 7203526831992572675L;

		UpdatePropertyAction() {
            super("Save Updated Properties...");
        }
    
        public void actionPerformed(ActionEvent e) {
            String messages = new String();

            organizationContactForm.commit();
            Contact newContact = (Contact)organizationContactForm.getFormObject();

            bagInfoForm.commit();
            BagInfo newInfo = (BagInfo)bagInfoForm.getFormObject();

            organizationGeneralForm.commit();
            BagOrganization newOrganization = (BagOrganization)organizationGeneralForm.getFormObject();

            bag = getBag();
            newOrganization.setContact(newContact);
            newInfo.setBagOrganization(newOrganization);
            bag.setInfo(newInfo);
            setBag(bag);
            messages = "Organization and Contact information has been updated.";

            updateMessages(messages);
        }
    }
    
    private class FtpAction extends AbstractAction {
		private static final long serialVersionUID = 5353357080228961994L;

		FtpAction() {
            super("Transfer...");
        }
    
        public void actionPerformed(ActionEvent e) {
            // Show dialog; this method does not return until dialog is closed
            if (ftpExecutor.isEnabled()) {
                ftpExecutor.execute();
            }
        }
    }

    /* */
    private void updateTree(File file) {
        String messages = new String();
    	if (filePane.getComponentCount() > 0) {
    		if (bagsTree != null && bagsTree.isShowing()) {
           	    bagsTree.invalidate();
    		}
    	}
        rootTree = new ArrayList<File>();
        createBagManagerTree(file);
        bagsTree.setLargeModel(true);
        bagsTree.setPreferredSize(getMinimumSize());      
        filePane.setViewportView(bagsTree);
        messages = "The files to be included have been changed.";
    	updateTabs(messages);
        
    	bagView.validate();
    	bagView.repaint();
    }

    private void updateMessages(String messages) {
    	if (compositePane.getComponentCount() > 0) {
    		compositePane.removeAll();
            compositePane.invalidate();
            consolePane.invalidate();
            consoleScrollPane.invalidate();
    	}
        /* */
    	consoleScrollPane = new JScrollPane();
    	consolePane = new ConsolePane(bag, messages);
    	consoleScrollPane.setViewportView(consolePane);
    	compositePane.addTab("Console", consoleScrollPane);
    	manifestScrollPane.setViewportView(manifestPane);
    	compositePane.addTab("Manifest", manifestScrollPane);
    	compositePane.addTab("TagManifest", tagManifestScrollPane);
        compositePane.addTab("Bag It Info", bagInfoScrollPane);
    	compositePane.addTab("Data", dataScrollPane);
    	if (this.bag.getIsHoley()) {
            compositePane.addTab("Fetch", fetchScrollPane);    		
    	}
        compositePane.addTab("Bag It", bagItScrollPane);
    	/* */
        consolePane.validate();
        consolePane.repaint();
        consoleScrollPane.validate();
        consoleScrollPane.repaint();
        compositePane.validate();
        compositePane.repaint();
    }

    private void updateTabs(String messages) {
    	if (compositePane.getComponentCount() > 0) {
    		compositePane.removeAll();
            compositePane.invalidate();
            consolePane.invalidate();
            consoleScrollPane.invalidate();
    	}
        compositePane = populateBagPane(messages);
        consolePane.validate();
        consolePane.repaint();
        consoleScrollPane.validate();
        consoleScrollPane.repaint();
        compositePane.validate();
        compositePane.repaint();
    }    

    private void createBagManagerTree(File file) { 
    	rootFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    	display("createBagManagerTree: rootTree");
    	RecursiveFileListIterator fit = new RecursiveFileListIterator(file);
    	for (Iterator<File> it=fit; it.hasNext(); ) {
            File f = it.next();
            rootTree.add(f);
            display(f.getAbsoluteFile().toString());
        }
    	display("bagsTree.getRootDir");
    	DefaultMutableTreeNode rootDir = new DefaultMutableTreeNode();
    	display("createBagManagerTree: fileTree");
		rootDir = addNodes(null, file);
		display("createBagManagerTree: files.size: " + rootDir.getChildCount());
		bagsTree = new JTree(rootDir);
		rootFrame.setCursor(Cursor.getDefaultCursor());
    }

	/** Add nodes from under "dir" into curTop. Highly recursive. */
	DefaultMutableTreeNode addNodes(DefaultMutableTreeNode curTop, File dir) {
    	//display("createBagManagerTree: addNodes: " + dir.toString());
		String curPath = dir.getPath();
		DefaultMutableTreeNode curDir = new DefaultMutableTreeNode(curPath);
		if (curTop != null) { // should only be null at root
			curTop.add(curDir);
		}
		Vector<String> ol = new Vector<String>();
		String[] tmp = dir.list();
		for (int i = 0; i < tmp.length; i++)
			ol.addElement(tmp[i]);

		Collections.sort(ol, String.CASE_INSENSITIVE_ORDER);
		File f;
		Vector<String> files = new Vector<String>();
		// Make two passes, one for Dirs and one for Files. This is #1.
		for (int i = 0; i < ol.size(); i++) {
			String thisObject = (String) ol.elementAt(i);
			String newPath;
			if (curPath.equals("."))
				newPath = thisObject;
			else
				newPath = curPath + File.separator + thisObject;
			if ((f = new File(newPath)).isDirectory())
				addNodes(curDir, f);
			else
				files.addElement(thisObject);
		}
		// Pass two: for files.
    	//display("createBagManagerTree: files.size: " + files.size());
		for (int fnum = 0; fnum < files.size(); fnum++)
			curDir.add(new DefaultMutableTreeNode(files.elementAt(fnum)));

		return curDir;
	}

	private class ValidateExecutor extends AbstractActionCommandExecutor {
        public void execute() {
            if (getBag() != null) {
            	display("ValidateExecutor");
                String messages = bag.validateAndBag();
            	display(messages);
            	updateTabs(messages);
            }
        }
    }

/* */
  	private class FtpExecutor extends AbstractActionCommandExecutor {
        public void execute() {
            if (getBag() != null) {
                ftpPropertiesExecutor.execute();
            }
        }
    }

    /**
     * Command to create a new Ftp transfer dialog. 
     * Pops up an ftp dialog which is reused by using the {@link CloseAction#HIDE}.
     *
     * @see ApplicationDialog
     * @see CloseAction
     */
      private class FtpPropertiesExecutor extends AbstractActionCommandExecutor {
    	  private boolean packFrame = false;
    	  private FtpFrame frame = null;

    	  public void execute() {
    		  frame = new FtpFrame();
    		  //Validate frames that have preset sizes
    		  //Pack frames that have useful preferred size info, e.g. from their layout
    		  if (packFrame)
    			  frame.pack();
    		  else
    			  frame.validate();
    		  //Center the window
    		  Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    		  Dimension frameSize = frame.getSize();
    		  if (frameSize.height > screenSize.height)
    			  frameSize.height = screenSize.height;
    		  if (frameSize.width > screenSize.width)
    			  frameSize.width = screenSize.width;
    		  frame.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
    		  frame.setVisible(true);
    	  }
    }
/* */
    public void onApplicationEvent(ApplicationEvent e) {
    	display("BagView.onApplicationEvent");
        if (e instanceof LifecycleApplicationEvent) {
        	display("BagView.onApplicationEvent.LifecycleApplicationEvent");
            LifecycleApplicationEvent le = (LifecycleApplicationEvent)e;
            if (le.getEventType() == LifecycleApplicationEvent.CREATED && le.objectIs(BagOrganization.class)) {
                if (bagsTree != null) {
                    DefaultMutableTreeNode root = (DefaultMutableTreeNode)bagsTreeModel.getRoot();
                    root.add(new DefaultMutableTreeNode(le.getObject()));
                    bagsTreeModel.nodeStructureChanged(root);
                }
            }
        } else {
        	display("BagView.onApplicationEvent.validate");
        	bagView.repaint();
        }
    }
/* */
    public void componentClosed() {
    	display("closed");
    }

    public void componentFocusGained() {
    	display("gained");
    }

    public void componentFocusLost() {
    	display("lost");
    }

    public void componentOpened() {
    	display("opened");
    }

}