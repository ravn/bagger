package gov.loc.repository.bagger.ui.handlers;

import gov.loc.repository.bagger.bag.impl.DefaultBag;
import gov.loc.repository.bagger.ui.BagView;

import org.springframework.richclient.command.support.AbstractActionCommandExecutor;

public class ValidateExecutor extends AbstractActionCommandExecutor {
  private static final long serialVersionUID = 1L;
  BagView bagView;
  DefaultBag bag;

  public ValidateExecutor(BagView bagView) {
    super();
    this.bagView = bagView;
  }

  @Override
  public void execute() {
    bagView.validateBagHandler.validateBag();
  }

}
