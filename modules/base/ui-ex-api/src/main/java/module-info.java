/**
 * @author VISTALL
 * @since 29/01/2022
 */
module consulo.ui.ex.api {
  // todo obsolete dependency
  requires java.desktop;

  requires transitive consulo.application.api;
  requires transitive consulo.ui.api;
  requires transitive consulo.datacontext.api;
  requires transitive consulo.document.api;
  requires transitive kava.beans;

  exports consulo.ui.ex;
  exports consulo.ui.ex.popup;
  exports consulo.ui.ex.popup.event;
  exports consulo.ui.ex.action;
  exports consulo.ui.ex.action.event;
  exports consulo.ui.ex.action.util;
  exports consulo.ui.ex.content;
  exports consulo.ui.ex.content.event;
  exports consulo.ui.ex.util;
  exports consulo.ui.ex.event;
  exports consulo.ui.ex.update;
  exports consulo.ui.ex.concurrent;

  exports consulo.ui.ex.awt;
  exports consulo.ui.ex.awt.internal;

  uses consulo.ui.ex.awt.internal.TargetAWTFacade;
}