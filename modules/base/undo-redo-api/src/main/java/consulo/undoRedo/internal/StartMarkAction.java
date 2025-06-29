/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.undoRedo.internal;

import consulo.document.Document;
import consulo.document.DocumentReference;
import consulo.document.DocumentReferenceManager;
import consulo.project.Project;
import consulo.undoRedo.BasicUndoableAction;
import consulo.undoRedo.ProjectUndoManager;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * @author anna
 * @since 2011-11-08
 */
public class StartMarkAction extends BasicUndoableAction {
  private static final Map<Project, StartMarkAction> ourCurrentMarks = new HashMap<>();
  private String myCommandName;
  private boolean myGlobal;
  private Document myDocument;

  private StartMarkAction(Document document, String commandName) {
    super(DocumentReferenceManager.getInstance().create(document));
    myCommandName = commandName;
    myDocument = document;
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }

  public void setGlobal(boolean global) {
    myGlobal = global;
  }

  @Override
  public boolean isGlobal() {
    return myGlobal;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public void setCommandName(String commandName) {
    myCommandName = commandName;
  }

  public Document getDocument() {
    return myDocument;
  }

  @TestOnly
  public static void checkCleared() {
    try {
      assert ourCurrentMarks.isEmpty() : ourCurrentMarks.values();
    }
    finally {
      ourCurrentMarks.clear();
    }
  }

  public static StartMarkAction start(Document document, Project project, String commandName) throws AlreadyStartedException {
    final StartMarkAction existingMark = ourCurrentMarks.get(project);
    if (existingMark != null) {
      throw new AlreadyStartedException(existingMark.myCommandName, existingMark.myDocument, existingMark.getAffectedDocuments());
    }
    final StartMarkAction markAction = new StartMarkAction(document, commandName);
    ProjectUndoManager.getInstance(project).undoableActionPerformed(markAction);
    ourCurrentMarks.put(project, markAction);
    return markAction;
  }

  public static StartMarkAction canStart(Project project) {
    return ourCurrentMarks.get(project);
  }

  static void markFinished(Project project) {
    final StartMarkAction existingMark = ourCurrentMarks.remove(project);
    if (existingMark != null) {
      existingMark.myDocument = null;
    }
  }

  public static class AlreadyStartedException extends Exception {
    private final DocumentReference[] myAffectedDocuments;
    private Document myDocument;

    public AlreadyStartedException(String commandName, Document document, DocumentReference[] documentRefs) {
      super("Unable to start inplace refactoring:\n" + commandName + " is not finished yet.");
      myAffectedDocuments = documentRefs;
      myDocument = document;
    }

    public DocumentReference[] getAffectedDocuments() {
      return myAffectedDocuments;
    }

    public Document getDocument() {
      return myDocument;
    }
  }
}
