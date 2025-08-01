/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vcs.checkin;

import consulo.ide.impl.idea.ide.todo.TodoFilter;
import consulo.ide.impl.idea.ide.todo.TodoIndexPatternProvider;
import consulo.application.ApplicationManager;
import consulo.diff.old.DiffFragmentOld;
import consulo.diff.old.ComparisonPolicyOld;
import consulo.diff.old.LineFragment;
import consulo.diff.old.FragmentSide;
import consulo.diff.old.DiffCorrectionOld;
import consulo.diff.old.DiffFragmentsProcessorOld;
import consulo.diff.old.DiffPolicyOld;
import consulo.diff.old.DiffString;
import consulo.diff.old.TextDiffTypeEnum;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.application.util.function.Computable;
import consulo.ide.impl.idea.openapi.util.Getter;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiManager;
import consulo.ide.impl.psi.impl.search.LightIndexPatternSearch;
import consulo.ide.impl.psi.impl.search.TodoItemsCreator;
import consulo.language.psi.search.IndexPatternOccurrence;
import consulo.language.psi.search.PsiTodoSearchHelper;
import consulo.language.psi.search.TodoItem;
import consulo.language.psi.search.IndexPatternSearch;
import consulo.util.lang.function.PairConsumer;
import consulo.util.collection.SmartList;
import consulo.ide.impl.idea.util.containers.Convertor;
import consulo.application.util.diff.FilesTooBigForDiffException;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author irengrig
 * @since 2011-02-18
 */
public class TodoCheckinHandlerWorker {
  private final static Logger LOG = Logger.getInstance(TodoCheckinHandlerWorker.class);

  private final Collection<Change> changes;
  private final TodoFilter myTodoFilter;
  private final boolean myIncludePattern;
  private final PsiManager myPsiManager;
  private final PsiTodoSearchHelper mySearchHelper;

  private final List<TodoItem> myAddedOrEditedTodos;
  private final List<TodoItem> myInChangedTodos;
  private final List<Pair<FilePath, String>> mySkipped;
  private PsiFile myPsiFile;
  private List<TodoItem> myNewTodoItems;
  private final MyEditedFileProcessor myEditedFileProcessor;


  public TodoCheckinHandlerWorker(final Project project, final Collection<Change> changes, final TodoFilter todoFilter,
                                  final boolean includePattern) {
    this.changes = changes;
    myTodoFilter = todoFilter;
    myIncludePattern = includePattern;
    myPsiManager = PsiManager.getInstance(project);
    mySearchHelper = PsiTodoSearchHelper.getInstance(project);
    myAddedOrEditedTodos = new ArrayList<TodoItem>();
    myInChangedTodos = new ArrayList<TodoItem>();
    mySkipped = new SmartList<Pair<FilePath,String>>();
    myEditedFileProcessor = new MyEditedFileProcessor(project, new Acceptor() {
      @Override
      public void skipped(Pair<FilePath, String> pair) {
        mySkipped.add(pair);
      }

      @Override
      public void addedOrEdited(TodoItem todoItem) {
        myAddedOrEditedTodos.add(todoItem);
      }

      @Override
      public void inChanged(TodoItem todoItem) {
        myInChangedTodos.add(todoItem);
      }
    }, myTodoFilter);
  }

  public void execute() {
    for (Change change : changes) {
      ProgressManager.checkCanceled();
      if (change.getAfterRevision() == null) continue;
      final VirtualFile afterFile = getFileWithRefresh(change.getAfterRevision().getFile());
      if (afterFile == null || afterFile.isDirectory() || afterFile.getFileType().isBinary()) continue;
      myPsiFile = null;

      if (afterFile.isValid()) {
        myPsiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          @Override
          public PsiFile compute() {
            return myPsiManager.findFile(afterFile);
          }
        });
      }
      if (myPsiFile == null) {
        mySkipped.add(Pair.create(change.getAfterRevision().getFile(), ourInvalidFile));
        continue;
      }

      myNewTodoItems = new ArrayList<TodoItem>(Arrays.asList(
              ApplicationManager.getApplication().runReadAction(new Computable<TodoItem[]>() {
                @Override
                public TodoItem[] compute() {
                  return mySearchHelper.findTodoItems(myPsiFile);
                }
              })));
      applyFilterAndRemoveDuplicates(myNewTodoItems, myTodoFilter);
      if (change.getBeforeRevision() == null) {
        // take just all todos
        if (myNewTodoItems.isEmpty()) continue;
        myAddedOrEditedTodos.addAll(myNewTodoItems);
      }
      else {
        myEditedFileProcessor.process(change, myNewTodoItems);
      }
    }
  }

  @Nullable
  private static VirtualFile getFileWithRefresh(@Nonnull FilePath filePath) {
    VirtualFile file = filePath.getVirtualFile();
    if (file == null) {
      file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.getIOFile());
    }
    return file;
  }

  private static void applyFilterAndRemoveDuplicates(final List<TodoItem> todoItems, final TodoFilter filter) {
    TodoItem previous = null;
    for (Iterator<TodoItem> iterator = todoItems.iterator(); iterator.hasNext(); ) {
      final TodoItem next = iterator.next();
      if (filter != null && ! filter.contains(next.getPattern())) {
        iterator.remove();
        continue;
      }
      if (previous != null && next.getTextRange().equals(previous.getTextRange())) {
        iterator.remove();
      } else {
        previous = next;
      }
    }
  }

  private static class MyEditedFileProcessor {
    //private String myFileText;
    private String myBeforeContent;
    private String myAfterContent;
    private List<TodoItem> myOldItems;
    private LineFragment myCurrentLineFragment;
    private HashSet<String> myOldTodoTexts;
    private PsiFile myBeforeFile;
    private final PsiFileFactory myPsiFileFactory;
    private FilePath myAfterFile;
    private final Acceptor myAcceptor;
    private final TodoFilter myTodoFilter;

    private MyEditedFileProcessor(final Project project, Acceptor acceptor, final TodoFilter todoFilter) {
      myAcceptor = acceptor;
      myTodoFilter = todoFilter;
      myPsiFileFactory = PsiFileFactory.getInstance(project);
    }

    public void process(final Change change, final List<TodoItem> newTodoItems) {
      myBeforeFile = null;
      //myFileText = null;
      myOldItems = null;
      myOldTodoTexts = null;

      myAfterFile = change.getAfterRevision().getFile();
      try {
        myBeforeContent = change.getBeforeRevision().getContent();
        myAfterContent = change.getAfterRevision().getContent();
        if (myAfterContent == null) {
          myAcceptor.skipped(Pair.create(myAfterFile, ourCannotLoadCurrentRevision));
          return;
        }
        if (myBeforeContent == null) {
          myAcceptor.skipped(Pair.create(myAfterFile, ourCannotLoadPreviousRevision));
          return;
        }
        ArrayList<LineFragment> lineFragments = getLineFragments(myAfterFile.getPath(), myBeforeContent, myAfterContent);
        for (Iterator<LineFragment> iterator = lineFragments.iterator(); iterator.hasNext(); ) {
          ProgressManager.checkCanceled();
          final LineFragment next = iterator.next();
          final TextDiffTypeEnum type = next.getType();
          assert ! TextDiffTypeEnum.CONFLICT.equals(type);
          if (type == null || TextDiffTypeEnum.DELETED.equals(type) || TextDiffTypeEnum.NONE.equals(type)) {
            iterator.remove();
          }
        }
        final StepIntersection<TodoItem, LineFragment> intersection =
                new StepIntersection<TodoItem, LineFragment>(TodoItemConvertor.getInstance(), LineFragmentConvertor.getInstance(), lineFragments,
                                                             new Getter<String>() {
                                                               @Override
                                                               public String get() {
                                                                 return myAfterContent;
                                                               }
                                                             });

        intersection.process(newTodoItems, new PairConsumer<TodoItem, LineFragment>() {

          @Override
          public void consume(TodoItem todoItem, LineFragment lineFragment) {
            ProgressManager.checkCanceled();
            if (myCurrentLineFragment == null || ! myCurrentLineFragment.getRange(FragmentSide.SIDE2).equals(
                    lineFragment.getRange(FragmentSide.SIDE2))) {
              myCurrentLineFragment = lineFragment;
              myOldTodoTexts = null;
            }
            final TextDiffTypeEnum type = lineFragment.getType();
            if (TextDiffTypeEnum.INSERT.equals(type)) {
              myAcceptor.addedOrEdited(todoItem);
            } else {
              // change
              checkEditedFragment(todoItem);
            }
          }
        });
      } catch (VcsException e) {
        LOG.info(e);
        myAcceptor.skipped(Pair.create(myAfterFile, ourCannotLoadPreviousRevision));
      }
    }

    private void checkEditedFragment(TodoItem newTodoItem) {
      if (myBeforeFile == null) {
        myBeforeFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          @Override
          public PsiFile compute() {
            return myPsiFileFactory.createFileFromText("old" + myAfterFile.getName(), myAfterFile.getFileType(), myBeforeContent);
          }
        });
      }
      if (myOldItems == null)  {
        final Collection<IndexPatternOccurrence> all =
                LightIndexPatternSearch.SEARCH.createQuery(new IndexPatternSearch.SearchParameters(myBeforeFile, TodoIndexPatternProvider
                        .getInstance())).findAll();

        final TodoItemsCreator todoItemsCreator = new TodoItemsCreator();
        myOldItems = new ArrayList<TodoItem>();
        if (all.isEmpty()) {
          myAcceptor.addedOrEdited(newTodoItem);
          return;
        }
        for (IndexPatternOccurrence occurrence : all) {
          myOldItems.add(todoItemsCreator.createTodo(occurrence));
        }
        applyFilterAndRemoveDuplicates(myOldItems, myTodoFilter);
      }
      if (myOldTodoTexts == null) {
        final StepIntersection<LineFragment, TodoItem> intersection = new StepIntersection<LineFragment, TodoItem>(
                LineFragmentConvertor.getInstance(), TodoItemConvertor.getInstance(), myOldItems, new Getter<String>() {
          @Override
          public String get() {
            return myBeforeContent;
          }
        });
        myOldTodoTexts = new HashSet<String>();
        intersection.process(Collections.singletonList(myCurrentLineFragment), new PairConsumer<LineFragment, TodoItem>() {
          @Override
          public void consume(LineFragment lineFragment, TodoItem todoItem) {
            myOldTodoTexts.add(getTodoText(todoItem, myBeforeContent));
          }
        });
      }
      final String text = getTodoText(newTodoItem, myAfterContent);
      if (! myOldTodoTexts.contains(text)) {
        myAcceptor.addedOrEdited(newTodoItem);
      } else {
        myAcceptor.inChanged(newTodoItem);
      }
    }
  }

  interface Acceptor {
    void skipped(Pair<FilePath, String> pair);
    void addedOrEdited(final TodoItem todoItem);
    void inChanged(final TodoItem todoItem);
  }

  public List<TodoItem> getAddedOrEditedTodos() {
    return myAddedOrEditedTodos;
  }

  public List<TodoItem> getInChangedTodos() {
    return myInChangedTodos;
  }

  public List<Pair<FilePath, String>> getSkipped() {
    return mySkipped;
  }

  private static String getTodoText(TodoItem oldItem, final String content) {
    final String fragment = content.substring(oldItem.getTextRange().getStartOffset(), oldItem.getTextRange().getEndOffset());
    return StringUtil.join(fragment.split("\\s"), " ");
  }

  private static ArrayList<LineFragment> getLineFragments(final String fileName, String beforeContent, String afterContent) throws VcsException {
    try {
      DiffFragmentOld[] woFormattingBlocks =
              DiffPolicyOld.LINES_WO_FORMATTING.buildFragments(DiffString.create(beforeContent), DiffString.create(afterContent));
      DiffFragmentOld[] step1lineFragments =
              new DiffCorrectionOld.TrueLineBlocks(ComparisonPolicyOld.IGNORE_SPACE).correctAndNormalize(woFormattingBlocks);
      return new DiffFragmentsProcessorOld().process(step1lineFragments);
    } catch (FilesTooBigForDiffException e) {
      throw new VcsException("File " + fileName + " is too big and there are too many changes to build a diff", e);
    }
  }

  private final static String ourInvalidFile = "Invalid file (s)";
  private final static String ourCannotLoadPreviousRevision = "Can not load previous revision";
  private final static String ourCannotLoadCurrentRevision = "Can not load current revision";

  private static class TodoItemConvertor implements Convertor<TodoItem, TextRange> {
    private static final TodoItemConvertor ourInstance = new TodoItemConvertor();

    public static TodoItemConvertor getInstance() {
      return ourInstance;
    }

    @Override
    public TextRange convert(TodoItem o) {
      final TextRange textRange = o.getTextRange();
      return new TextRange(textRange.getStartOffset(), textRange.getEndOffset() - 1);
    }
  }

  private static class LineFragmentConvertor implements Convertor<LineFragment, TextRange> {
    private static final LineFragmentConvertor ourInstance = new LineFragmentConvertor();

    public static LineFragmentConvertor getInstance() {
      return ourInstance;
    }

    @Override
    public TextRange convert(LineFragment o) {
      final TextRange textRange = o.getRange(FragmentSide.SIDE2);
      return new TextRange(textRange.getStartOffset(), textRange.getEndOffset() - 1);
    }
  }

  public List<TodoItem> inOneList() {
    final List<TodoItem> list = new ArrayList<TodoItem>();
    list.addAll(getAddedOrEditedTodos());
    list.addAll(getInChangedTodos());
    return list;
  }
}
