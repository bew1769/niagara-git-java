package com.bew1769.niagaragit.wb;

import com.bew1769.niagaragit.BGitService;
import javax.baja.nre.annotations.AgentOn;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.sys.Action;
import javax.baja.sys.BObject;
import javax.baja.sys.Context;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.ui.BAbstractButton;
import javax.baja.ui.BLabel;
import javax.baja.ui.BWidget;
import javax.baja.ui.Command;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.pane.BGridPane;
import javax.baja.util.Lexicon;
import javax.baja.workbench.view.BWbComponentView;

/**
 * Workbench view for {@link BGitService}. Buttons invoke station actions, so
 * the admin check runs on the station rather than in this view.
 */
@NiagaraType(
  agent = @AgentOn(types = "niagaraGit:GitService", requiredPermissions = "r")
)
public class BGitServiceView extends BWbComponentView {
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.bew1769.niagaragit.wb.BGitServiceView(1)1.0$ @*/
/* Hand-written to match the annotation above. Slot-o-matic overwrites this block. */

  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BGitServiceView.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  private static final Lexicon LEX = Lexicon.make("niagaraGit");
  private BGitService service;
  private final BLabel status = new BLabel();

  public BGitServiceView() {
    BEdgePane content = new BEdgePane();
    BGridPane buttons = new BGridPane();
    buttons.add("init", BAbstractButton.make(new Invoke(this, "gitView.init", BGitService.init)));
    buttons.add("commit", BAbstractButton.make(new Invoke(this, "gitView.commitSnapshot", BGitService.commitSnapshot)));
    buttons.add("pull", BAbstractButton.make(new Invoke(this, "gitView.pull", BGitService.pull)));
    buttons.add("push", BAbstractButton.make(new Invoke(this, "gitView.push", BGitService.push)));
    buttons.add("status", BAbstractButton.make(new Invoke(this, "gitView.status", BGitService.refreshStatus)));
    buttons.setColumnCount(5);
    content.setCenter(status);
    content.setBottom(buttons);
    setContent(content);
  }

  @Override
  public void doLoadValue(BObject value, Context context) {
    service = (BGitService) value;
    status.setText(service.getStatus());
  }

  class Invoke extends Command {
    private final Action action;

    Invoke(BWidget owner, String lexiconKey, Action action) {
      super(owner, LEX, lexiconKey);
      this.action = action;
    }

    public CommandArtifact doInvoke() {
      service.invoke(action, null, null);
      status.setText(service.getStatus());
      return null;
    }
  }
}
