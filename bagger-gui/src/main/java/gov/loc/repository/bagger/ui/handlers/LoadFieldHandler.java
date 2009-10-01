
package gov.loc.repository.bagger.ui.handlers;

import gov.loc.repository.bagger.bag.impl.DefaultBag;
import gov.loc.repository.bagger.ui.BagView;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

public class LoadFieldHandler extends AbstractAction {
   	private static final long serialVersionUID = 1L;
	BagView bagView;
	DefaultBag bag;

	public LoadFieldHandler(BagView bagView) {
		super();
		this.bagView = bagView;
	}

	public void actionPerformed(ActionEvent e) {
		bagView.bagProject.loadProfiles();
		bagView.infoInputPane.showTabPane(1);
	}
}