/**
 * @author VISTALL
 * @since 13/01/2022
 */
open module consulo.ide.impl {
  requires java.desktop;
  requires java.xml;
  requires java.rmi;
  requires java.management;
  requires java.prefs;
  requires java.scripting;
  requires jdk.unsupported;

  requires consulo.container.api;
  requires consulo.util.nodep;

  requires it.unimi.dsi.fastutil;

  requires consulo.bootstrap;

  requires consulo.desktop.awt.hacking;
  requires consulo.desktop.awt.eawt.wrapper;

  requires transitive consulo.undo.redo.api;
  requires transitive consulo.annotation;
  requires transitive consulo.proxy;
  requires transitive consulo.platform.api;
  requires transitive consulo.base.localize.library;
  requires transitive consulo.base.icon.library;
  requires transitive consulo.ui.api;
  requires transitive consulo.disposer.api;
  requires transitive consulo.logging.api;
  requires transitive consulo.localize.api;
  requires transitive consulo.component.api;
  requires transitive consulo.application.api;
  requires transitive consulo.application.content.api;
  requires transitive consulo.document.api;
  requires transitive consulo.virtual.file.system.api;
  requires transitive consulo.project.api;
  requires transitive consulo.module.api;
  requires transitive consulo.configurable.api;
  requires transitive consulo.file.chooser.api;
  requires transitive consulo.project.content.api;
  requires transitive consulo.module.content.api;
  requires transitive consulo.language.api;
  requires transitive consulo.language.impl;
  requires transitive consulo.index.io;
  requires transitive consulo.datacontext.api;
  requires transitive consulo.ui.ex.api;
  requires transitive consulo.project.ui.api;
  requires transitive consulo.navigation.api;
  requires transitive consulo.code.editor.api;
  requires transitive consulo.process.api;
  requires transitive consulo.execution.api;
  requires transitive consulo.execution.coverage.api;
  requires transitive consulo.compiler.api;
  requires transitive consulo.language.editor.api;
  requires transitive consulo.document.impl;
  requires transitive consulo.file.editor.api;
  requires transitive consulo.compiler.artifact.api;
  requires transitive consulo.ide.api;
  requires transitive consulo.component.impl;
  requires transitive consulo.ui.ex.awt.api;
  requires transitive consulo.language.editor.ui.api;
  requires transitive consulo.language.editor.impl;
  requires transitive consulo.code.editor.impl;
  requires transitive consulo.language.inject.impl;
  requires transitive consulo.component.store.impl;
  requires transitive consulo.application.impl;
  requires transitive consulo.file.template.api;
  requires transitive consulo.file.template.impl;
  requires transitive consulo.usage.api;
  requires transitive consulo.virtual.file.status.api;
  requires transitive consulo.virtual.file.status.impl;
  requires transitive consulo.application.content.impl;
  requires transitive consulo.module.impl;
  requires transitive consulo.util.xml.fast.reader;
  requires transitive consulo.language.copyright.api;
  requires consulo.project.impl;
  requires consulo.project.ui.impl;
  requires consulo.language.code.style.ui.api;
  requires consulo.http.impl;
  requires consulo.http.adapter.httpclient4;
  requires consulo.file.editor.impl;
  requires consulo.compiler.impl;
  requires consulo.execution.impl;
  requires consulo.execution.test.impl;
  requires consulo.builtin.web.server.impl;
  requires consulo.version.control.system.impl;
  requires consulo.diff.impl;
  requires transitive consulo.configuration.editor.api;

  requires consulo.ui.impl;

  requires consulo.xcoverage.rt;
  requires consulo.remote.servers.agent.rt;

  requires transitive consulo.util.collection;
  requires transitive consulo.util.collection.primitive;
  requires transitive consulo.util.concurrent;
  requires transitive consulo.util.dataholder;
  //requires consulo.util.interner;
  requires transitive consulo.util.io;
  requires transitive consulo.util.jdom;
  requires transitive consulo.util.lang;
  requires transitive consulo.util.netty;
  requires transitive consulo.util.rmi;
  requires transitive consulo.util.jna;
  requires transitive consulo.util.xml.serializer;

  requires consulo.build.ui.impl;
  requires consulo.remote.server.impl;
  requires consulo.compiler.artifact.impl;
  requires consulo.task.impl;
  requires consulo.language.code.style.impl;

  // TODO [VISTALL] remove this dependency
  requires gnu.trove;

  requires org.apache.httpcomponents.httpcore;
  requires org.apache.httpcomponents.httpclient;
  requires org.apache.httpcomponents.httpmime;

  requires org.apache.commons.lang3;
  requires org.apache.commons.codec;
  requires org.apache.commons.compress;
  requires velocity.engine.core;

  requires com.google.common;
  requires com.google.gson;
  requires build.serviceMessages;

  requires com.sun.jna;
  requires com.sun.jna.platform;

  requires org.lz4.java;

  requires miglayout;
  requires imgscalr.lib;
  requires args4j;
  requires winp;

  requires pty4j;
  // TODO delete, awt dependency
  requires forms.rt;

  requires transitive jakarta.inject;
  requires transitive kava.beans;
  requires transitive org.slf4j;

  provides consulo.ui.image.IconLibraryDescriptor with
    consulo.ide.impl.ui.impl.LightIconLibraryDescriptor,
    consulo.ide.impl.ui.impl.DarkIconLibraryDescriptor;

  exports consulo.ide.impl.dataContext to consulo.desktop.awt.ide.impl;

  //opens consulo.ide.impl.presentationAssistant to consulo.util.xml.serializer;

  exports consulo.ide.impl;
  exports consulo.ide.impl.actionSystem.ex;
  exports consulo.ide.impl.actionSystem.impl;
  exports consulo.ide.impl.actions;
  exports consulo.ide.impl.actions.impl;
  exports consulo.ide.impl.application;
  exports consulo.ide.impl.base;
  exports consulo.ide.impl.bundle;
  exports consulo.ide.impl.codeInsight;
  exports consulo.ide.impl.codeInsight.daemon.impl;
  exports consulo.ide.impl.command;
  exports consulo.ide.impl.compiler;
  exports consulo.ide.impl.compiler.resourceCompiler;
  exports consulo.ide.impl.compiler.setting;
  exports consulo.ide.impl.configurationStore.vcs;
  exports consulo.ide.impl.desktop;
  exports consulo.ide.impl.diagram.actions;
  exports consulo.ide.impl.diagram.builder;
  exports consulo.ide.impl.diagram.builder.impl;
  exports consulo.ide.impl.diagram.provider;
  exports consulo.ide.impl.diff;
  exports consulo.ide.impl.eap;
  exports consulo.ide.impl.execution;
  exports consulo.ide.impl.fileChooser;
  exports consulo.ide.impl.fileEditor;
  exports consulo.ide.impl.fileEditor.text;
  exports consulo.ide.impl.find;
  exports consulo.ide.impl.idea.analysis;
  exports consulo.ide.impl.idea.application.options;
  exports consulo.ide.impl.idea.application.options.codeStyle;
  exports consulo.ide.impl.idea.application.options.codeStyle.arrangement.action;
  exports consulo.ide.impl.idea.application.options.codeStyle.arrangement.component;
  exports consulo.ide.impl.idea.application.options.codeStyle.excludedFiles;
  exports consulo.ide.impl.idea.application.options.colors;
  exports consulo.ide.impl.idea.application.options.colors.highlighting;
  exports consulo.ide.impl.idea.application.options.editor;
  exports consulo.ide.impl.idea.application.options.pathMacros;
  exports consulo.ide.impl.idea.build;
  exports consulo.ide.impl.idea.build.issue.quickfix;
  exports consulo.ide.impl.idea.build.output;
  exports consulo.ide.impl.idea.buildfiles;
  exports consulo.ide.impl.idea.codeEditor.printing;
  exports consulo.ide.impl.idea.codeInsight;
  exports consulo.ide.impl.idea.codeInsight.actions;
  exports consulo.ide.impl.idea.codeInsight.codeFragment;
  exports consulo.ide.impl.idea.codeInsight.completion;
  exports consulo.ide.impl.idea.codeInsight.completion.actions;
  exports consulo.ide.impl.idea.codeInsight.completion.impl;
  exports consulo.ide.impl.idea.codeInsight.daemon;
  exports consulo.ide.impl.idea.codeInsight.daemon.impl;
  exports consulo.ide.impl.idea.codeInsight.daemon.impl.actions;
  exports consulo.ide.impl.idea.codeInsight.daemon.impl.analysis;
  exports consulo.ide.impl.idea.codeInsight.daemon.impl.analysis.encoding;
  exports consulo.ide.impl.idea.codeInsight.daemon.impl.quickfix;
  exports consulo.ide.impl.idea.codeInsight.daemon.impl.tooltips;
  exports consulo.ide.impl.idea.codeInsight.documentation;
  exports consulo.ide.impl.idea.codeInsight.documentation.actions;
  exports consulo.ide.impl.idea.codeInsight.editorActions;
  exports consulo.ide.impl.idea.codeInsight.editorActions.enter;
  exports consulo.ide.impl.idea.codeInsight.editorActions.fillParagraph;
  exports consulo.ide.impl.idea.codeInsight.editorActions.moveLeftRight;
  exports consulo.ide.impl.idea.codeInsight.editorActions.moveUpDown;
  exports consulo.ide.impl.idea.codeInsight.editorActions.smartEnter;
  exports consulo.ide.impl.idea.codeInsight.editorActions.wordSelection;
  exports consulo.ide.impl.idea.codeInsight.folding;
  exports consulo.ide.impl.idea.codeInsight.folding.impl;
  exports consulo.ide.impl.idea.codeInsight.folding.impl.actions;
  exports consulo.ide.impl.idea.codeInsight.generation;
  exports consulo.ide.impl.idea.codeInsight.generation.actions;
  exports consulo.ide.impl.idea.codeInsight.generation.surroundWith;
  exports consulo.ide.impl.idea.codeInsight.highlighting;
  exports consulo.ide.impl.idea.codeInsight.highlighting.actions;
  exports consulo.ide.impl.idea.codeInsight.hint;
  exports consulo.ide.impl.idea.codeInsight.hint.actions;
  exports consulo.ide.impl.idea.codeInsight.hints;
  exports consulo.ide.impl.idea.codeInsight.hints.settings;
  exports consulo.ide.impl.idea.codeInsight.intention.actions;
  exports consulo.ide.impl.idea.codeInsight.intention.impl;
  exports consulo.ide.impl.idea.codeInsight.intention.impl.config;
  exports consulo.ide.impl.idea.codeInsight.lookup;
  exports consulo.ide.impl.idea.codeInsight.lookup.impl;
  exports consulo.ide.impl.idea.codeInsight.lookup.impl.actions;
  exports consulo.ide.impl.idea.codeInsight.navigation;
  exports consulo.ide.impl.idea.codeInsight.navigation.actions;
  exports consulo.ide.impl.idea.codeInsight.preview;
  exports consulo.ide.impl.idea.codeInsight.problems;
  exports consulo.ide.impl.idea.codeInsight.template.actions;
  exports consulo.ide.impl.idea.codeInsight.template.impl;
  exports consulo.ide.impl.idea.codeInsight.template.impl.actions;
  exports consulo.ide.impl.idea.codeInsight.template.impl.editorActions;
  exports consulo.ide.impl.idea.codeInsight.template.postfix.completion;
  exports consulo.ide.impl.idea.codeInsight.template.postfix.templates;
  exports consulo.ide.impl.idea.codeInsight.unwrap;
  exports consulo.ide.impl.idea.codeInspection;
  exports consulo.ide.impl.idea.codeInspection.actions;
  exports consulo.ide.impl.idea.codeInspection.ex;
  exports consulo.ide.impl.idea.codeInspection.export;
  exports consulo.ide.impl.idea.codeInspection.offline;
  exports consulo.ide.impl.idea.codeInspection.offlineViewer;
  exports consulo.ide.impl.idea.codeInspection.ui;
  exports consulo.ide.impl.idea.codeInspection.ui.actions;
  exports consulo.ide.impl.idea.codeInspection.util;
  exports consulo.ide.impl.idea.codeStyle;
  exports consulo.ide.impl.idea.concurrency;
  exports consulo.ide.impl.idea.designer;
  exports consulo.ide.impl.idea.designer.model;
  exports consulo.ide.impl.idea.designer.propertyTable;
  exports consulo.ide.impl.idea.designer.propertyTable.editors;
  exports consulo.ide.impl.idea.designer.propertyTable.renderers;
  exports consulo.ide.impl.idea.diagnostic;
  exports consulo.ide.impl.idea.dupLocator;
  exports consulo.ide.impl.idea.dupLocator.equivalence;
  exports consulo.ide.impl.idea.dupLocator.index;
  exports consulo.ide.impl.idea.dupLocator.iterators;
  exports consulo.ide.impl.idea.dupLocator.treeHash;
  exports consulo.ide.impl.idea.dupLocator.treeView;
  exports consulo.ide.impl.idea.dupLocator.util;
  exports consulo.ide.impl.idea.dvcs;
  exports consulo.ide.impl.idea.dvcs.actions;
  exports consulo.ide.impl.idea.dvcs.branch;
  exports consulo.ide.impl.idea.dvcs.cherrypick;
  exports consulo.ide.impl.idea.dvcs.push;
  exports consulo.ide.impl.idea.dvcs.push.ui;
  exports consulo.ide.impl.idea.dvcs.ui;
  exports consulo.ide.impl.idea.execution.actions;
  exports consulo.ide.impl.idea.execution.configurations;
  exports consulo.ide.impl.idea.execution.console;
  exports consulo.ide.impl.idea.execution.filters;
  exports consulo.ide.impl.idea.execution.filters.impl;
  exports consulo.ide.impl.idea.execution.impl;
  exports consulo.ide.impl.idea.execution.impl.statistics;
  exports consulo.ide.impl.idea.execution.lineMarker;
  exports consulo.ide.impl.idea.execution.process;
  exports consulo.ide.impl.idea.execution.rmi;
  exports consulo.ide.impl.idea.execution.testframework.sm.runner.ui.statistics;
  exports consulo.ide.impl.idea.execution.ui;
  exports consulo.ide.impl.idea.execution.ui.layout.impl;
  exports consulo.ide.impl.idea.execution.util;
  exports consulo.ide.impl.idea.execution.wsl;
  exports consulo.ide.impl.idea.find;
  exports consulo.ide.impl.idea.find.actions;
  exports consulo.ide.impl.idea.find.editorHeaderActions;
  exports consulo.ide.impl.idea.find.findInProject;
  exports consulo.ide.impl.idea.find.findUsages;
  exports consulo.ide.impl.idea.find.impl;
  exports consulo.ide.impl.idea.find.impl.livePreview;
  exports consulo.ide.impl.idea.find.ngrams;
  exports consulo.ide.impl.idea.find.replaceInProject;
  exports consulo.ide.impl.idea.framework.detection;
  exports consulo.ide.impl.idea.ide;
  exports consulo.ide.impl.idea.ide.actionMacro;
  exports consulo.ide.impl.idea.ide.actionMacro.actions;
  exports consulo.ide.impl.idea.ide.actions;
  exports consulo.ide.impl.idea.ide.actions.bigPopup;
  exports consulo.ide.impl.idea.ide.actions.exclusion;
  exports consulo.ide.impl.idea.ide.actions.runAnything;
  exports consulo.ide.impl.idea.ide.actions.runAnything.activity;
  exports consulo.ide.impl.idea.ide.actions.runAnything.commands;
  exports consulo.ide.impl.idea.ide.actions.runAnything.execution;
  exports consulo.ide.impl.idea.ide.actions.runAnything.groups;
  exports consulo.ide.impl.idea.ide.actions.runAnything.handlers;
  exports consulo.ide.impl.idea.ide.actions.runAnything.items;
  exports consulo.ide.impl.idea.ide.actions.runAnything.ui;
  exports consulo.ide.impl.idea.ide.actions.searcheverywhere;
  exports consulo.ide.impl.idea.ide.actions.tree;
  exports consulo.ide.impl.idea.ide.bookmarks.actions;
  exports consulo.ide.impl.idea.ide.caches;
  exports consulo.ide.impl.idea.ide.dnd;
  exports consulo.ide.impl.idea.ide.errorTreeView;
  exports consulo.ide.impl.idea.ide.errorTreeView.actions;
  exports consulo.ide.impl.idea.ide.errorTreeView.impl;
  exports consulo.ide.impl.idea.ide.favoritesTreeView;
  exports consulo.ide.impl.idea.ide.favoritesTreeView.actions;
  exports consulo.ide.impl.idea.ide.fileTemplates.actions;
  exports consulo.ide.impl.idea.ide.fileTemplates.impl;
  exports consulo.ide.impl.idea.ide.fileTemplates.ui;
  exports consulo.ide.impl.idea.ide.hierarchy;
  exports consulo.ide.impl.idea.ide.hierarchy.actions;
  exports consulo.ide.impl.idea.ide.highlighter;
  exports consulo.ide.impl.idea.ide.highlighter.custom;
  exports consulo.ide.impl.idea.ide.highlighter.custom.impl;
  exports consulo.ide.impl.idea.ide.impl;
  exports consulo.ide.impl.idea.ide.impl.dataRules;
  exports consulo.ide.impl.idea.ide.macro;
  exports consulo.ide.impl.idea.ide.navigationToolbar;
  exports consulo.ide.impl.idea.ide.plugins;
  exports consulo.ide.impl.idea.ide.projectView;
  exports consulo.ide.impl.idea.ide.projectView.actions;
  exports consulo.ide.impl.idea.ide.projectView.impl;
  exports consulo.ide.impl.idea.ide.projectView.impl.nodes;
  exports consulo.ide.impl.idea.ide.scopeView;
  exports consulo.ide.impl.idea.ide.scopeView.nodes;
  exports consulo.ide.impl.idea.ide.scratch;
  exports consulo.ide.impl.idea.ide.script;
  exports consulo.ide.impl.idea.ide.structureView.impl;
  exports consulo.ide.impl.idea.ide.structureView.newStructureView;
  exports consulo.ide.impl.idea.ide.todo;
  exports consulo.ide.impl.idea.ide.todo.configurable;
  exports consulo.ide.impl.idea.ide.todo.nodes;
  exports consulo.ide.impl.idea.ide.ui;
  exports consulo.ide.impl.idea.ide.ui.customization;
  exports consulo.ide.impl.idea.ide.ui.search;
  exports consulo.ide.impl.idea.ide.util;
  exports consulo.ide.impl.idea.ide.util.gotoByName;
  exports consulo.ide.impl.idea.ide.util.scopeChooser;
  exports consulo.ide.impl.idea.ide.util.treeView.smartTree;
  exports consulo.ide.impl.idea.ide.wizard;
  exports consulo.ide.impl.idea.idea;
  exports consulo.ide.impl.idea.indentation;
  exports consulo.ide.impl.idea.index;
  exports consulo.ide.impl.idea.internal;
  exports consulo.ide.impl.idea.internal.anomalies;
  exports consulo.ide.impl.idea.internal.encodings;
  exports consulo.ide.impl.idea.internal.psiView;
  exports consulo.ide.impl.idea.internal.psiView.formattingblocks;
  exports consulo.ide.impl.idea.internal.statistic;
  exports consulo.ide.impl.idea.internal.statistic.collectors.fus.actions.persistence;
  exports consulo.ide.impl.idea.internal.tree;
  exports consulo.ide.impl.idea.internal.validation;
  exports consulo.ide.impl.idea.lang;
  exports consulo.ide.impl.idea.lang.cacheBuilder;
  exports consulo.ide.impl.idea.lang.customFolding;
  exports consulo.ide.impl.idea.lang.folding;
  exports consulo.ide.impl.idea.lang.parameterInfo;
  exports consulo.ide.impl.idea.model.search.impl;
  exports consulo.ide.impl.idea.moduleDependencies;
  exports consulo.ide.impl.idea.navigation;
  exports consulo.ide.impl.idea.notification.impl;
  exports consulo.ide.impl.idea.notification.impl.actions;
  exports consulo.ide.impl.idea.notification.impl.ui;
  exports consulo.ide.impl.idea.openapi.actionSystem;
  exports consulo.ide.impl.idea.openapi.actionSystem.ex;
  exports consulo.ide.impl.idea.openapi.actionSystem.impl;
  exports consulo.ide.impl.idea.openapi.actionSystem.impl.actionholder;
  exports consulo.ide.impl.idea.openapi.application;
  exports consulo.ide.impl.idea.openapi.application.ex;
  exports consulo.ide.impl.idea.openapi.application.impl;
  exports consulo.ide.impl.idea.openapi.command.impl;
  exports consulo.ide.impl.idea.openapi.deployment;
  exports consulo.ide.impl.idea.openapi.diff.impl.patch;
  exports consulo.ide.impl.idea.openapi.diff.impl.patch.apply;
  exports consulo.ide.impl.idea.openapi.diff.impl.patch.formove;
  exports consulo.ide.impl.idea.openapi.diff.impl.settings;
  exports consulo.ide.impl.idea.openapi.editor;
  exports consulo.ide.impl.idea.openapi.editor.actionSystem;
  exports consulo.ide.impl.idea.openapi.editor.actions;
  exports consulo.ide.impl.idea.openapi.editor.ex;
  exports consulo.ide.impl.idea.openapi.editor.ex.util;
  exports consulo.ide.impl.idea.openapi.editor.highlighter;
  exports consulo.ide.impl.idea.openapi.editor.impl;
  exports consulo.ide.impl.idea.openapi.editor.impl.event;
  exports consulo.ide.impl.idea.openapi.editor.impl.softwrap;
  exports consulo.ide.impl.idea.openapi.editor.markup;
  exports consulo.ide.impl.idea.openapi.editor.richcopy;
  exports consulo.ide.impl.idea.openapi.editor.richcopy.model;
  exports consulo.ide.impl.idea.openapi.editor.richcopy.settings;
  exports consulo.ide.impl.idea.openapi.editor.richcopy.view;
  exports consulo.ide.impl.idea.openapi.file.exclude;
  exports consulo.ide.impl.idea.openapi.fileChooser;
  exports consulo.ide.impl.idea.openapi.fileChooser.actions;
  exports consulo.ide.impl.idea.openapi.fileChooser.ex;
  exports consulo.ide.impl.idea.openapi.fileChooser.impl;
  exports consulo.ide.impl.idea.openapi.fileChooser.tree;
  exports consulo.ide.impl.idea.openapi.fileEditor.impl;
  exports consulo.ide.impl.idea.openapi.fileEditor.impl.http;
  exports consulo.ide.impl.idea.openapi.fileEditor.impl.tabActions;
  exports consulo.ide.impl.idea.openapi.fileEditor.impl.text;
  exports consulo.ide.impl.idea.openapi.fileTypes;
  exports consulo.ide.impl.idea.openapi.fileTypes.ex;
  exports consulo.ide.impl.idea.openapi.fileTypes.impl;
  exports consulo.ide.impl.idea.openapi.ide;
  exports consulo.ide.impl.idea.openapi.keymap;
  exports consulo.ide.impl.idea.openapi.keymap.ex;
  exports consulo.ide.impl.idea.openapi.keymap.impl;
  exports consulo.ide.impl.idea.openapi.keymap.impl.ui;
  exports consulo.ide.impl.idea.openapi.localVcs;
  exports consulo.ide.impl.idea.openapi.module;
  exports consulo.ide.impl.idea.openapi.module.impl;
  exports consulo.ide.impl.idea.openapi.module.impl.scopes;
  exports consulo.ide.impl.idea.openapi.options;
  exports consulo.ide.impl.idea.openapi.options.binding;
  exports consulo.ide.impl.idea.openapi.options.colors.pages;
  exports consulo.ide.impl.idea.openapi.options.ex;
  exports consulo.ide.impl.idea.openapi.paths;
  exports consulo.ide.impl.idea.openapi.progress.impl;
  exports consulo.ide.impl.idea.openapi.project;
  exports consulo.ide.impl.idea.openapi.project.impl;
  exports consulo.ide.impl.idea.openapi.projectRoots.impl;
  exports consulo.ide.impl.idea.openapi.projectRoots.ui;
  exports consulo.ide.impl.idea.openapi.roots;
  exports consulo.ide.impl.idea.openapi.roots.impl;
  exports consulo.ide.impl.idea.openapi.roots.impl.libraries;
  exports consulo.ide.impl.idea.openapi.roots.libraries;
  exports consulo.ide.impl.idea.openapi.roots.libraries.ui;
  exports consulo.ide.impl.idea.openapi.roots.libraries.ui.impl;
  exports consulo.ide.impl.idea.openapi.roots.ui;
  exports consulo.ide.impl.idea.openapi.roots.ui.componentsList.components;
  exports consulo.ide.impl.idea.openapi.roots.ui.componentsList.layout;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.actions;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.actions;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.nodes;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.sourceItems;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.sourceItems.actions;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.classpath;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.dependencyAnalysis;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.libraries;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.libraries.impl;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.libraryEditor;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot;
  exports consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot.daemon;
  exports consulo.ide.impl.idea.openapi.roots.ui.util;
  exports consulo.ide.impl.idea.openapi.ui;
  exports consulo.ide.impl.idea.openapi.ui.impl;
  exports consulo.ide.impl.idea.openapi.ui.playback;
  exports consulo.ide.impl.idea.openapi.ui.playback.commands;
  exports consulo.ide.impl.idea.openapi.ui.popup.util;
  exports consulo.ide.impl.idea.openapi.util;
  exports consulo.ide.impl.idea.openapi.util.io;
  exports consulo.ide.impl.idea.openapi.util.process;
  exports consulo.ide.impl.idea.openapi.util.text;
  exports consulo.ide.impl.idea.openapi.vcs;
  exports consulo.ide.impl.idea.openapi.vcs.actions;
  exports consulo.ide.impl.idea.openapi.vcs.annotate;
  exports consulo.ide.impl.idea.openapi.vcs.changes;
  exports consulo.ide.impl.idea.openapi.vcs.changes.actions;
  exports consulo.ide.impl.idea.openapi.vcs.changes.actions.diff;
  exports consulo.ide.impl.idea.openapi.vcs.changes.committed;
  exports consulo.ide.impl.idea.openapi.vcs.changes.conflicts;
  exports consulo.ide.impl.idea.openapi.vcs.changes.issueLinks;
  exports consulo.ide.impl.idea.openapi.vcs.changes.patch;
  exports consulo.ide.impl.idea.openapi.vcs.changes.patch.tool;
  exports consulo.ide.impl.idea.openapi.vcs.changes.shelf;
  exports consulo.ide.impl.idea.openapi.vcs.changes.ui;
  exports consulo.ide.impl.idea.openapi.vcs.checkin;
  exports consulo.ide.impl.idea.openapi.vcs.checkout;
  exports consulo.ide.impl.idea.openapi.vcs.configurable;
  exports consulo.ide.impl.idea.openapi.vcs.contentAnnotation;
  exports consulo.ide.impl.idea.openapi.vcs.ex;
  exports consulo.ide.impl.idea.openapi.vcs.history;
  exports consulo.ide.impl.idea.openapi.vcs.history.impl;
  exports consulo.ide.impl.idea.openapi.vcs.impl;
  exports consulo.ide.impl.idea.openapi.vcs.impl.projectlevelman;
  exports consulo.ide.impl.idea.openapi.vcs.merge;
  exports consulo.ide.impl.idea.openapi.vcs.persistent;
  exports consulo.ide.impl.idea.openapi.vcs.readOnlyHandler;
  exports consulo.ide.impl.idea.openapi.vcs.rollback;
  exports consulo.ide.impl.idea.openapi.vcs.roots;
  exports consulo.ide.impl.idea.openapi.vcs.statistics;
  exports consulo.ide.impl.idea.openapi.vcs.ui;
  exports consulo.ide.impl.idea.openapi.vcs.update;
  exports consulo.ide.impl.idea.openapi.vcs.vfs;
  exports consulo.ide.impl.idea.openapi.vfs;
  exports consulo.ide.impl.idea.openapi.vfs.encoding;
  exports consulo.ide.impl.idea.openapi.vfs.ex.dummy;
  exports consulo.ide.impl.idea.openapi.vfs.impl.http;
  exports consulo.ide.impl.idea.openapi.vfs.impl.local;
  exports consulo.ide.impl.idea.openapi.wm;
  exports consulo.ide.impl.idea.openapi.wm.impl;
  exports consulo.ide.impl.idea.openapi.wm.impl.status;
  exports consulo.ide.impl.idea.openapi.wm.impl.status.widget;
  exports consulo.ide.impl.idea.openapi.wm.impl.welcomeScreen;
  exports consulo.ide.impl.idea.packageDependencies;
  exports consulo.ide.impl.idea.packageDependencies.actions;
  exports consulo.ide.impl.idea.packageDependencies.ui;
  exports consulo.ide.impl.idea.packaging.impl.compiler;
  exports consulo.ide.impl.idea.packaging.impl.run;
  exports consulo.ide.impl.idea.packaging.impl.ui;
  exports consulo.ide.impl.idea.packaging.impl.ui.actions;
  exports consulo.ide.impl.idea.packaging.ui;
  exports consulo.ide.impl.idea.platform;
  exports consulo.ide.impl.idea.platform.templates.github;
  exports consulo.ide.impl.idea.profile.codeInspection;
  exports consulo.ide.impl.idea.profile.codeInspection.ui;
  exports consulo.ide.impl.idea.profile.codeInspection.ui.filter;
  exports consulo.ide.impl.idea.profile.codeInspection.ui.header;
  exports consulo.ide.impl.idea.profile.codeInspection.ui.inspectionsTree;
  exports consulo.ide.impl.idea.profile.codeInspection.ui.table;
  exports consulo.ide.impl.idea.refactoring.actions;
  exports consulo.ide.impl.idea.refactoring.changeSignature;
  exports consulo.ide.impl.idea.refactoring.changeSignature.inplace;
  exports consulo.ide.impl.idea.refactoring.extractMethod;
  exports consulo.ide.impl.idea.refactoring.extractSuperclass;
  exports consulo.ide.impl.idea.refactoring.inline;
  exports consulo.ide.impl.idea.refactoring.introduce.inplace;
  exports consulo.ide.impl.idea.refactoring.lang;
  exports consulo.ide.impl.idea.refactoring.rename;
  exports consulo.ide.impl.idea.refactoring.ui;
  exports consulo.ide.impl.idea.refactoring.util;
  exports consulo.ide.impl.idea.reference;

  exports consulo.ide.impl.idea.reporting;
  exports consulo.ide.impl.idea.ssh;
  exports consulo.ide.impl.idea.tasks.actions;
  exports consulo.ide.impl.idea.tasks.actions.context;
  exports consulo.ide.impl.idea.tasks.actions.vcs;
  exports consulo.ide.impl.idea.tasks.config;
  exports consulo.ide.impl.idea.tasks.context;
  exports consulo.ide.impl.idea.tasks.doc;
  exports consulo.ide.impl.idea.tasks.impl;
  exports consulo.ide.impl.idea.tasks.timeTracking;
  exports consulo.ide.impl.idea.testIntegration;
  exports consulo.ide.impl.idea.tools;
  exports consulo.ide.impl.idea.ui;
  exports consulo.ide.impl.idea.ui.components;
  exports consulo.ide.impl.idea.ui.components.labels;
  exports consulo.ide.impl.idea.ui.components.panels;
  exports consulo.ide.impl.idea.ui.content;
  exports consulo.ide.impl.idea.ui.content.impl;
  exports consulo.ide.impl.idea.ui.content.tabs;
  exports consulo.ide.impl.idea.ui.debugger;
  exports consulo.ide.impl.idea.ui.debugger.extensions;
  exports consulo.ide.impl.idea.ui.dualView;
  exports consulo.ide.impl.idea.ui.mac;
  exports consulo.ide.impl.idea.ui.navigation;
  exports consulo.ide.impl.idea.ui.paint;
  exports consulo.ide.impl.idea.ui.popup;
  exports consulo.ide.impl.idea.ui.popup.async;
  exports consulo.ide.impl.idea.ui.popup.list;
  exports consulo.ide.impl.idea.ui.popup.mock;
  exports consulo.ide.impl.idea.ui.popup.tree;
  exports consulo.ide.impl.idea.ui.popup.util;
  exports consulo.ide.impl.idea.ui.roots;
  exports consulo.ide.impl.idea.ui.speedSearch;
  exports consulo.ide.impl.idea.ui.stripe;
  exports consulo.ide.impl.idea.ui.tabs;
  exports consulo.ide.impl.idea.ui.tabs.impl;
  exports consulo.ide.impl.idea.ui.tabs.impl.singleRow;
  exports consulo.ide.impl.idea.ui.tabs.impl.table;
  exports consulo.ide.impl.idea.ui.tree;
  exports consulo.ide.impl.idea.ui.tree.project;
  exports consulo.ide.impl.idea.ui.treeStructure.filtered;
  exports consulo.ide.impl.idea.ui.treeStructure.treetable;
  exports consulo.ide.impl.idea.ui.util;
  exports consulo.ide.impl.idea.unscramble;
  exports consulo.ide.impl.idea.usageView.impl;
  exports consulo.ide.impl.idea.usages;
  exports consulo.ide.impl.idea.usages.impl;
  exports consulo.ide.impl.idea.util;
  exports consulo.ide.impl.idea.util.concurrency;
  exports consulo.ide.impl.idea.util.containers;
  exports consulo.ide.impl.idea.util.continuation;
  exports consulo.ide.impl.idea.util.exception;
  exports consulo.ide.impl.idea.util.gist;
  exports consulo.ide.impl.idea.util.graph.impl;
  exports consulo.ide.impl.idea.util.indexing;
  exports consulo.ide.impl.idea.util.indexing.hash;
  exports consulo.ide.impl.idea.util.indexing.impl;
  exports consulo.ide.impl.idea.util.indexing.impl.forward;
  exports consulo.ide.impl.idea.util.indexing.provided;
  exports consulo.ide.impl.idea.util.io;
  exports consulo.ide.impl.idea.util.net;
  exports consulo.ide.impl.idea.util.net.ssl;
  exports consulo.ide.impl.idea.util.properties;
  exports consulo.ide.impl.idea.util.text;
  exports consulo.ide.impl.idea.util.textCompletion;
  exports consulo.ide.impl.idea.util.treeWithCheckedNodes;
  exports consulo.ide.impl.idea.util.ui;
  exports consulo.ide.impl.idea.util.ui.accessibility;
  exports consulo.ide.impl.idea.util.ui.classpath;
  exports consulo.ide.impl.idea.util.ui.table;
  exports consulo.ide.impl.idea.util.ui.tree;
  exports consulo.ide.impl.idea.util.ui.update;
  exports consulo.ide.impl.idea.vcs;
  exports consulo.ide.impl.idea.vcs.changes;
  exports consulo.ide.impl.idea.vcs.log;
  exports consulo.ide.impl.idea.vcs.log.data;
  exports consulo.ide.impl.idea.vcs.log.data.index;
  exports consulo.ide.impl.idea.vcs.log.graph;
  exports consulo.ide.impl.idea.vcs.log.graph.api;
  exports consulo.ide.impl.idea.vcs.log.graph.api.elements;
  exports consulo.ide.impl.idea.vcs.log.graph.api.permanent;
  exports consulo.ide.impl.idea.vcs.log.graph.api.printer;
  exports consulo.ide.impl.idea.vcs.log.graph.collapsing;
  exports consulo.ide.impl.idea.vcs.log.graph.impl.facade;
  exports consulo.ide.impl.idea.vcs.log.graph.impl.facade.bek;
  exports consulo.ide.impl.idea.vcs.log.graph.impl.permanent;
  exports consulo.ide.impl.idea.vcs.log.graph.impl.print;
  exports consulo.ide.impl.idea.vcs.log.graph.impl.print.elements;
  exports consulo.ide.impl.idea.vcs.log.graph.impl.visible;
  exports consulo.ide.impl.idea.vcs.log.graph.linearBek;
  exports consulo.ide.impl.idea.vcs.log.graph.utils;
  exports consulo.ide.impl.idea.vcs.log.graph.utils.impl;
  exports consulo.ide.impl.idea.vcs.log.impl;
  exports consulo.ide.impl.idea.vcs.log.paint;
  exports consulo.ide.impl.idea.vcs.log.statistics;
  exports consulo.ide.impl.idea.vcs.log.ui;
  exports consulo.ide.impl.idea.vcs.log.ui.actions;
  exports consulo.ide.impl.idea.vcs.log.ui.filter;
  exports consulo.ide.impl.idea.vcs.log.ui.frame;
  exports consulo.ide.impl.idea.vcs.log.ui.render;
  exports consulo.ide.impl.idea.vcs.log.ui.tables;
  exports consulo.ide.impl.idea.vcs.log.util;
  exports consulo.ide.impl.idea.vcsUtil;
  exports consulo.ide.impl.idea.webcore.packaging;
  exports consulo.ide.impl.idea.xml;
  exports consulo.ide.impl.idea.xml.util;
  exports consulo.ide.impl.language;
  exports consulo.ide.impl.language.codeStyle;
  exports consulo.ide.impl.language.editor;
  exports consulo.ide.impl.language.editor.refactoring;
  exports consulo.ide.impl.language.psi;
  exports consulo.ide.impl.language.psi.search.scope;
  exports consulo.ide.impl.moduleImport;
  exports consulo.ide.impl.newProject.actions;
  exports consulo.ide.impl.newProject.ui;
  exports consulo.ide.impl.packageDependencies;
  exports consulo.ide.impl.packaging.impl.run;
  exports consulo.ide.impl.project;
  exports consulo.ide.impl.projectView;
  exports consulo.ide.impl.psi.codeStyle.statusbar;
  exports consulo.ide.impl.psi.impl;
  exports consulo.ide.impl.psi.impl.cache;
  exports consulo.ide.impl.psi.impl.cache.impl;
  exports consulo.ide.impl.psi.impl.cache.impl.id;
  exports consulo.ide.impl.psi.impl.cache.impl.todo;
  exports consulo.ide.impl.psi.impl.file.impl;
  exports consulo.ide.impl.psi.impl.search;
  exports consulo.ide.impl.psi.impl.source;
  exports consulo.ide.impl.psi.impl.source.codeStyle;
  exports consulo.ide.impl.psi.impl.source.resolve.reference;
  exports consulo.ide.impl.psi.impl.source.resolve.reference.impl.providers;
  exports consulo.ide.impl.psi.search;
  exports consulo.ide.impl.psi.search.scope;
  exports consulo.ide.impl.psi.search.scope.impl;
  exports consulo.ide.impl.psi.search.scope.packageSet;
  exports consulo.ide.impl.psi.search.scope.packageSet.lexer;
  exports consulo.ide.impl.psi.statistics.impl;
  exports consulo.ide.impl.psi.stubs;
  exports consulo.ide.impl.psi.stubs.provided;
  exports consulo.ide.impl.psi.templateLanguages;
  exports consulo.ide.impl.roots.impl.property;
  exports consulo.ide.impl.roots.orderEntry;
  exports consulo.ide.impl.roots.ui.configuration;
  exports consulo.ide.impl.roots.ui.configuration.classpath;
  exports consulo.ide.impl.roots.ui.configuration.extension;
  exports consulo.ide.impl.roots.ui.configuration.impl;
  exports consulo.ide.impl.roots.ui.configuration.projectRoot.daemon;
  exports consulo.ide.impl.roots.ui.configuration.projectRoot.moduleLayerActions;
  exports consulo.ide.impl.roots.ui.configuration.session.internal;
  exports consulo.ide.impl.script;
  exports consulo.ide.impl.start;
  exports consulo.ide.impl.startup.customize;
  exports consulo.ide.impl.task;
  exports consulo.ide.impl.ui;
  exports consulo.ide.impl.ui.app;
  exports consulo.ide.impl.ui.app.impl.settings;
  exports consulo.ide.impl.ui.dialog.impl;
  exports consulo.ide.impl.ui.docking.impl;
  exports consulo.ide.impl.ui.ex.impl.util;
  exports consulo.ide.impl.ui.impl;
  exports consulo.ide.impl.ui.laf;
  exports consulo.ide.impl.ui.popup;
  exports consulo.ide.impl.ui.tree.impl;
  exports consulo.ide.impl.usage;
  exports consulo.ide.impl.vcs;
  exports consulo.ide.impl.welcomeScreen;
  exports consulo.ide.impl.wm.impl;
  exports consulo.ide.impl.wm.impl.status;
  exports consulo.ide.impl.wm.impl.welcomeScreen;

  exports consulo.ide.impl.project.ui.impl to consulo.desktop.awt.ide.impl;
  exports consulo.ide.impl.configurable to consulo.sand.language.plugin, consulo.desktop.awt.ide.impl;
  exports consulo.ide.impl.desktop.awt.editor to consulo.desktop.awt.ide.impl;
  exports consulo.ide.impl.wm.statusBar to consulo.desktop.awt.ide.impl;
  exports consulo.ide.impl.idea.diff.actions.impl to consulo.desktop.awt.ide.impl;
  exports consulo.ide.impl.idea.diff.requests to consulo.desktop.awt.ide.impl;
}