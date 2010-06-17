package gov.loc.repository.bagger.ui;

import gov.loc.repository.bagger.bag.BagInfoField;
import gov.loc.repository.bagger.bag.impl.DefaultBagInfo;
import gov.loc.repository.bagger.ui.util.ApplicationContextUtil;
import gov.loc.repository.bagger.ui.util.LayoutUtil;
import gov.loc.repository.bagit.impl.BagInfoTxtImpl;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class AddFieldPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(AddFieldPanel.class);
	
	private JCheckBox standardCheckBox;
	private JComboBox standardFieldsComboBox;
	private JTextField customFieldTextField;
	private JTextField valueField;
	
	public AddFieldPanel() {
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		setLayout(new GridBagLayout());
		int row = 0;
    	int col = 0;
		
		// standard field checkbox
		standardCheckBox = new JCheckBox("Standard");
		standardCheckBox.setSelected(true);
		standardCheckBox.addActionListener(new StandardFieldCheckBoxAction());
    	GridBagConstraints gbc = LayoutUtil.buildGridBagConstraints(col++, row, 1, 1, 0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST);
		add(standardCheckBox, gbc);
		
		// standard field dropdown menu
        List<String> listModel = getStandardBagFields();
        standardFieldsComboBox = new JComboBox();
        standardFieldsComboBox = new JComboBox(listModel.toArray());
        standardFieldsComboBox.setName(ApplicationContextUtil.getMessage("baginfo.field.fieldlist"));
        standardFieldsComboBox.setSelectedItem("");
        standardFieldsComboBox.setToolTipText(ApplicationContextUtil.getMessage("baginfo.field.fieldlist.help"));
        gbc = LayoutUtil.buildGridBagConstraints(col++, row, 1, 1, 0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST);
		add(standardFieldsComboBox, gbc);
		
		// custom field name
		customFieldTextField = new JTextField(17);
		customFieldTextField.setToolTipText(ApplicationContextUtil.getMessage("baginfo.field.name.help"));
		customFieldTextField.setVisible(false);
		gbc = LayoutUtil.buildGridBagConstraints(col++, row, 1, 1, 0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST);
		add(customFieldTextField, gbc);
		
		// field value
		gbc = LayoutUtil.buildGridBagConstraints(col++, row, 1, 1, 0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST);
		add(new JLabel(" : "), gbc);
		
		valueField = new JTextField();
		gbc = LayoutUtil.buildGridBagConstraints(col++, row, 1, 1, 1, 1, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
		add(valueField, gbc);
		
		// add field button
		JButton addFieldButton = new JButton("Add");
		gbc = LayoutUtil.buildGridBagConstraints(col++, row, 1, 1, 0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST);
		add(addFieldButton, gbc);
		addFieldButton.addActionListener(new AddFieldAction());
	}
	
	
	private class StandardFieldCheckBoxAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			JCheckBox checkbox = (JCheckBox) e.getSource();
			boolean standardFieldSelected = checkbox.isSelected();
			if (standardFieldSelected) {
				standardFieldsComboBox.setVisible(true);
				standardFieldsComboBox.requestFocus();
				customFieldTextField.setVisible(false);
			} else {
				standardFieldsComboBox.setVisible(false);
				customFieldTextField.setVisible(true);
				customFieldTextField.requestFocus();
			}
		}
	}
	
	private List<String> getStandardBagFields() {
		ArrayList<String> list = new ArrayList<String>();
		list.add("");

		// Standard Fields from BagInfoTxt
		// TODO fix it when BIL has the functionality
		Field[] fields = BagInfoTxtImpl.class.getFields();
		for (Field field : fields) {
			if (Modifier.isStatic(field.getModifiers())
					&& field.getName().startsWith("FIELD_")) {
				try {
					String standardFieldName = (String) field.get(null);
					if (!DefaultBagInfo.isOrganizationContactField(standardFieldName)) {
						log.debug("adding standard field: " + standardFieldName);
						list.add(standardFieldName);
					}
				} catch (Exception e) {
					log.error("Failed to get value for static field "
							+ field.getName());
				}
			}
		}
		return list;
	}
	
	
	public void setEnabled(boolean enabled) {
		Component[] components = getComponents();
		for (int i = 0; i < components.length; i++) {
			components[i].setEnabled(enabled);
		}
	}
	
	
	private class AddFieldAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			BagView bagView = ApplicationContextUtil.getBagView();
			
			BagInfoField field = createBagInfoField();
			
			if (field != null) {
				bagView.getBag().addField(field);
				// TODO use observer pattern
		        bagView.infoInputPane.updateInfoFormsPane(true);
	            bagView.infoInputPane.bagInfoInputPane.requestFocus();
			}
        }
    }
    
	
	private BagInfoField createBagInfoField() {
		BagView bagView = ApplicationContextUtil.getBagView();
		
		BagInfoField field = new BagInfoField();
		
		String fieldName = null;
		if (isStandardField()) {
			fieldName = (String)standardFieldsComboBox.getSelectedItem();
		} else {
			fieldName = customFieldTextField.getText();
		}
		
		if (fieldName.trim().isEmpty()) {
			bagView.showWarningErrorDialog("New Field Dialog", "Field name must be specified!");
			return null;
		}
    
		field.setName(fieldName);
		field.setLabel(fieldName);
		field.setValue(valueField.getText().trim());
		field.setComponentType(BagInfoField.TEXTFIELD_COMPONENT);
		
    	return field;
    }
	
	private boolean isStandardField() {
		return standardCheckBox.isSelected();
	}
	
    
}
