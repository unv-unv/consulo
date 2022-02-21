/**
 * @author VISTALL
 * @since 31/01/2022
 */
module consulo.application.ui.api {
  // TODO obsolete dependency
  requires java.desktop;

  requires transitive consulo.application.api;
  requires transitive consulo.ui.ex.api;
  requires transitive consulo.proxy;
  requires transitive kava.beans;

  exports consulo.application.ui;
  exports consulo.application.ui.event;
  exports consulo.application.ui.wm;
}