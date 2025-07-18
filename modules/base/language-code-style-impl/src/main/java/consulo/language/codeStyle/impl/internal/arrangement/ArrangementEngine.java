/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.language.codeStyle.impl.internal.arrangement;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.internal.DocumentEx;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.arrangement.*;
import consulo.language.codeStyle.arrangement.match.ArrangementMatchRule;
import consulo.language.codeStyle.arrangement.match.ArrangementSectionRule;
import consulo.language.codeStyle.arrangement.match.TextAwareArrangementEntry;
import consulo.language.codeStyle.arrangement.std.ArrangementSettingsToken;
import consulo.language.codeStyle.arrangement.std.ArrangementStandardSettingsAware;
import consulo.language.codeStyle.arrangement.std.StdArrangementTokens;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.DumbService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.collection.Stack;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.*;

import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.Section.END_SECTION;
import static consulo.language.codeStyle.arrangement.std.StdArrangementTokens.Section.START_SECTION;

/**
 * Encapsulates generic functionality of arranging file elements by the predefined rules.
 * <p/>
 * I.e. the general idea is to have a language-specific rules hidden by generic arrangement API and common arrangement
 * engine which works on top of that API and performs the arrangement.
 *
 * @author Denis Zhdanov
 * @since 7/20/12 1:56 PM
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class ArrangementEngine {
  private boolean myCodeChanged;

  @Nullable
  public String getUserNotificationInfo() {
    if (myCodeChanged) {
      return "rearranged code";
    }
    return null;
  }

  /**
   * Arranges given PSI root contents that belong to the given ranges.
   * <b>Note:</b> After arrangement editor foldings we'll be preserved.
   *
   * @param editor
   * @param file   target PSI root
   * @param ranges target ranges to use within the given root
   */
  @RequiredUIAccess
  public void arrange(@Nonnull final Editor editor, @Nonnull PsiFile file, Collection<TextRange> ranges) {
    arrange(file, ranges, new RestoreFoldArrangementCallback(editor));
  }

  /**
   * Arranges given PSI root contents that belong to the given ranges.
   * <b>Note:</b> Editor foldings are not expected to be preserved.
   *
   * @param file   target PSI root
   * @param ranges target ranges to use within the given root
   */
  @RequiredUIAccess
  public void arrange(@Nonnull PsiFile file, @Nonnull Collection<TextRange> ranges) {
    arrange(file, ranges, null);
  }

  /**
   * Arranges given PSI root contents that belong to the given ranges.
   *
   * @param file   target PSI root
   * @param ranges target ranges to use within the given root
   */
  @RequiredUIAccess
  public void arrange(@Nonnull PsiFile file, @Nonnull Collection<TextRange> ranges, @Nullable final ArrangementCallback callback) {
    myCodeChanged = false;

    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return;
    }

    final Rearranger<?> rearranger = Rearranger.forLanguage(file.getLanguage());
    if (rearranger == null) {
      return;
    }

    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings();
    ArrangementSettings arrangementSettings = settings.getCommonSettings(file.getLanguage()).getArrangementSettings();
    if (arrangementSettings == null && rearranger instanceof ArrangementStandardSettingsAware) {
      arrangementSettings = ((ArrangementStandardSettingsAware)rearranger).getDefaultSettings();
    }

    if (arrangementSettings == null) {
      return;
    }

    final DocumentEx documentEx = document instanceof DocumentEx docEx && !document.isInBulkUpdate() ? docEx : null;

    final Context<? extends ArrangementEntry> context;
    DumbService.getInstance(file.getProject()).setAlternativeResolveEnabled(true);
    try {
      context = Context.from(rearranger, document, file, ranges, arrangementSettings, settings);
    }
    finally {
      DumbService.getInstance(file.getProject()).setAlternativeResolveEnabled(false);
    }

    Application.get().runWriteAction(() -> {
      if (documentEx != null) {
        //documentEx.setInBulkUpdate(true);
      }
      try {
        doArrange(context);
        if (callback != null) {
          callback.afterArrangement(context.moveInfos);
        }
      }
      finally {
        if (documentEx != null) {
          //documentEx.setInBulkUpdate(false);
        }
      }
    });
  }

  @SuppressWarnings("unchecked")
  private <E extends ArrangementEntry> void doArrange(Context<E> context) {
    // The general idea is to process entries bottom-up where every processed group belongs to the same parent. We may not bother
    // with entries text ranges then. We use a list and a stack for achieving that than.
    //
    // Example:
    //            Entry1              Entry2
    //            /    \              /    \
    //      Entry11   Entry12    Entry21  Entry22
    //
    //    --------------------------
    //    Stage 1:
    //      list: Entry1 Entry2    <-- entries to process
    //      stack: [0, 0, 2]       <-- holds current iteration info at the following format:
    //                                 (start entry index at the auxiliary list (inclusive); current index; end index (exclusive))
    //    --------------------------
    //    Stage 2:
    //      list: Entry1 Entry2 Entry11 Entry12
    //      stack: [0, 1, 2]
    //             [2, 2, 4]
    //    --------------------------
    //    Stage 3:
    //      list: Entry1 Entry2 Entry11 Entry12
    //      stack: [0, 1, 2]
    //             [2, 3, 4]
    //    --------------------------
    //    Stage 4:
    //      list: Entry1 Entry2 Entry11 Entry12
    //      stack: [0, 1, 2]
    //             [2, 4, 4]
    //    --------------------------
    //      arrange 'Entry11 Entry12'
    //    --------------------------
    //    Stage 5:
    //      list: Entry1 Entry2
    //      stack: [0, 1, 2]
    //    --------------------------
    //    Stage 6:
    //      list: Entry1 Entry2 Entry21 Entry22
    //      stack: [0, 2, 2]
    //             [2, 2, 4]
    //    --------------------------
    //    Stage 7:
    //      list: Entry1 Entry2 Entry21 Entry22
    //      stack: [0, 2, 2]
    //             [2, 3, 4]
    //    --------------------------
    //    Stage 8:
    //      list: Entry1 Entry2 Entry21 Entry22
    //      stack: [0, 2, 2]
    //             [2, 4, 4]
    //    --------------------------
    //      arrange 'Entry21 Entry22'
    //    --------------------------
    //    Stage 9:
    //      list: Entry1 Entry2
    //      stack: [0, 2, 2]
    //    --------------------------
    //      arrange 'Entry1 Entry2'

    List<ArrangementEntryWrapper<E>> entries = new ArrayList<>();
    Stack<StackEntry> stack = new Stack<>();
    entries.addAll(context.wrappers);
    stack.push(new StackEntry(0, context.wrappers.size()));
    while (!stack.isEmpty()) {
      StackEntry stackEntry = stack.peek();
      if (stackEntry.current >= stackEntry.end) {
        List<ArrangementEntryWrapper<E>> subEntries = entries.subList(stackEntry.start, stackEntry.end);
        // arrange entries even if subEntries.size() == 1, because we don't want to miss new section comments here
        doArrange(subEntries, context);
        subEntries.clear();
        stack.pop();
      }
      else {
        ArrangementEntryWrapper<E> wrapper = entries.get(stackEntry.current++);
        List<ArrangementEntryWrapper<E>> children = wrapper.getChildren();
        if (!children.isEmpty()) {
          entries.addAll(children);
          stack.push(new StackEntry(stackEntry.end, children.size()));
        }
      }
    }
  }

  /**
   * Arranges (re-orders) given entries according to the given rules.
   *
   * @param entries         entries to arrange
   * @param sectionRules    rules to use for arrangement
   * @param rulesByPriority rules sorted by priority ('public static' rule will have higher priority than 'public')
   * @param entryToSection  mapping from arrangement entry to the parent section
   * @return arranged list of the given rules
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  @Nonnull
  public static <E extends ArrangementEntry> List<E> arrange(
    @Nonnull Collection<E> entries,
    @Nonnull List<ArrangementSectionRule> sectionRules,
    @Nonnull List<? extends ArrangementMatchRule> rulesByPriority,
    @Nullable Map<E, ArrangementSectionRule> entryToSection
  ) {
    List<E> arranged = new ArrayList<>();
    Set<E> unprocessed = new LinkedHashSet<>();
    List<Pair<Set<ArrangementEntry>, E>> dependent = new ArrayList<>();
    for (E entry : entries) {
      List<? extends ArrangementEntry> dependencies = entry.getDependencies();
      if (dependencies == null) {
        unprocessed.add(entry);
      }
      else {
        if (dependencies.size() == 1 && dependencies.get(0) == entry.getParent()) {
          // Handle a situation when the entry is configured to be at the first parent's children.
          arranged.add(entry);
        }
        else {
          Set<ArrangementEntry> first = new HashSet<>(dependencies);
          dependent.add(Pair.create(first, entry));
        }
      }
    }

    Set<E> matched = new HashSet<>();

    MultiMap<ArrangementMatchRule, E> elementsByRule = new MultiMap<>();
    for (ArrangementMatchRule rule : rulesByPriority) {
      matched.clear();
      for (E entry : unprocessed) {
        if (entry.canBeMatched() && rule.getMatcher().isMatched(entry)) {
          elementsByRule.putValue(rule, entry);
          matched.add(entry);
        }
      }
      unprocessed.removeAll(matched);
    }

    for (ArrangementSectionRule sectionRule : sectionRules) {
      for (ArrangementMatchRule rule : sectionRule.getMatchRules()) {
        final Collection<E> arrangedEntries = arrangeByRule(arranged, elementsByRule, rule);

        if (entryToSection != null && arrangedEntries != null) {
          for (E entry : arrangedEntries) {
            entryToSection.put(entry, sectionRule);
          }
        }
      }
    }
    arranged.addAll(unprocessed);

    for (int i = 0; i < arranged.size() && !dependent.isEmpty(); i++) {
      E e = arranged.get(i);
      List<E> shouldBeAddedAfterCurrentElement = new ArrayList<>();

      for (Iterator<Pair<Set<ArrangementEntry>, E>> iterator = dependent.iterator(); iterator.hasNext(); ) {
        Pair<Set<ArrangementEntry>, E> pair = iterator.next();
        pair.first.remove(e);
        if (pair.first.isEmpty()) {
          iterator.remove();
          shouldBeAddedAfterCurrentElement.add(pair.second);
        }
      }

      // add dependent entries to the same section as main entry
      if (entryToSection != null && entryToSection.containsKey(e)) {
        final ArrangementSectionRule rule = entryToSection.get(e);
        for (E e1 : shouldBeAddedAfterCurrentElement) {
          entryToSection.put(e1, rule);
        }
      }
      arranged.addAll(i + 1, shouldBeAddedAfterCurrentElement);
    }

    return arranged;
  }

  @Nullable
  private static <E extends ArrangementEntry> Collection<E> arrangeByRule(@Nonnull List<E> arranged, @Nonnull MultiMap<ArrangementMatchRule, E> elementsByRule, @Nonnull ArrangementMatchRule rule) {
    if (elementsByRule.containsKey(rule)) {
      final Collection<E> arrangedEntries = elementsByRule.remove(rule);

      // Sort by name if necessary.
      if (StdArrangementTokens.Order.BY_NAME.equals(rule.getOrderType())) {
        sortByName((List<E>)arrangedEntries);
      }
      arranged.addAll(arrangedEntries);
      return arrangedEntries;
    }
    return null;
  }

  private static <E extends ArrangementEntry> void sortByName(@Nonnull List<E> entries) {
    if (entries.size() < 2) {
      return;
    }
    final ObjectIntMap<E> weights = ObjectMaps.newObjectIntHashMap();
    int i = 0;
    for (E e : entries) {
      weights.putInt(e, ++i);
    }
    ContainerUtil.sort(entries, (e1, e2) -> {
      String name1 = e1 instanceof NameAwareArrangementEntry ? ((NameAwareArrangementEntry)e1).getName() : null;
      String name2 = e2 instanceof NameAwareArrangementEntry ? ((NameAwareArrangementEntry)e2).getName() : null;
      if (name1 != null && name2 != null) {
        return name1.compareTo(name2);
      }
      else if (name1 == null && name2 == null) {
        return weights.getInt(e1) - weights.getInt(e2);
      }
      else if (name2 == null) {
        return -1;
      }
      else {
        return 1;
      }
    });
  }

  @SuppressWarnings("unchecked")
  private <E extends ArrangementEntry> void doArrange(@Nonnull List<ArrangementEntryWrapper<E>> wrappers, @Nonnull Context<E> context) {
    if (wrappers.isEmpty()) {
      return;
    }

    Map<E, ArrangementSectionRule> entryToSection = new HashMap<>();
    Map<E, ArrangementEntryWrapper<E>> map = new HashMap<>();
    List<E> arranged = new ArrayList<>();
    List<E> toArrange = new ArrayList<>();
    for (ArrangementEntryWrapper<E> wrapper : wrappers) {
      E entry = wrapper.getEntry();
      map.put(wrapper.getEntry(), wrapper);
      if (!entry.canBeMatched()) {
        // Split entries to arrange by 'can not be matched' rules.
        // See IDEA-104046 for a problem use-case example.
        if (toArrange.isEmpty()) {
          arranged.addAll(arrange(toArrange, context.sectionRules, context.rulesByPriority, entryToSection));
        }
        arranged.add(entry);
        toArrange.clear();
      }
      else {
        toArrange.add(entry);
      }
    }
    if (!toArrange.isEmpty()) {
      arranged.addAll(arrange(toArrange, context.sectionRules, context.rulesByPriority, entryToSection));
    }

    final NewSectionInfo<E> newSectionsInfo = NewSectionInfo.create(arranged, entryToSection);
    context.changer.prepare(wrappers, context);
    // We apply changes from the last position to the first position in order not to bother with offsets shifts.
    for (int i = arranged.size() - 1; i >= 0; i--) {
      ArrangementEntryWrapper<E> arrangedWrapper = map.get(arranged.get(i));
      ArrangementEntryWrapper<E> initialWrapper = wrappers.get(i);

      ArrangementEntryWrapper<E> previous = i > 0 ? map.get(arranged.get(i - 1)) : null;
      ArrangementEntryWrapper<E> previousInitial = i > 0 ? wrappers.get(i - 1) : null;

      final ArrangementEntryWrapper<E> parentWrapper = initialWrapper.getParent();
      if (arrangedWrapper.equals(initialWrapper)) {
        if (previous != null && previous.equals(previousInitial) || previous == null && previousInitial == null) {
          final int beforeOffset = arrangedWrapper.getStartOffset();
          final int afterOffset = arrangedWrapper.getEndOffset();

          boolean isInserted = context.changer.insertSection(context, arranged.get(i), newSectionsInfo, parentWrapper, beforeOffset, afterOffset);
          myCodeChanged = isInserted || myCodeChanged;
          continue;
        }
      }

      ArrangementEntryWrapper<E> next = i < arranged.size() - 1 ? map.get(arranged.get(i + 1)) : null;
      context.changer.replace(arrangedWrapper, initialWrapper, previous, next, context);
      context.changer.insertSection(context, arranged.get(i), newSectionsInfo, arrangedWrapper, initialWrapper, parentWrapper);
      myCodeChanged = true;
    }
  }

  private static class NewSectionInfo<E extends ArrangementEntry> {
    private final Map<E, String> mySectionStarts = new HashMap<>();
    private final Map<E, String> mySectionEnds = new HashMap<>();

    private static <E extends ArrangementEntry> NewSectionInfo create(
      @Nonnull List<E> arranged,
      @Nonnull Map<E, ArrangementSectionRule> entryToSection
    ) {
      final NewSectionInfo<E> info = new NewSectionInfo<>();

      boolean sectionIsOpen = false;
      ArrangementSectionRule prevSection = null;
      E prev = null;
      for (E e : arranged) {
        final ArrangementSectionRule section = entryToSection.get(e);
        if (section != prevSection) {
          closeSection(prevSection, prev, info, sectionIsOpen);
          sectionIsOpen = false;

          if (section != null) {
            final String startComment = section.getStartComment();
            if (StringUtil.isNotEmpty(startComment) && !isSectionEntry(e, startComment)) {
              sectionIsOpen = true;
              info.addSectionStart(e, startComment);
            }
          }
          prevSection = section;
        }
        prev = e;
      }

      closeSection(prevSection, prev, info, sectionIsOpen);
      return info;
    }

    public static boolean isSectionEntry(@Nonnull ArrangementEntry entry, @Nonnull String sectionText) {
      if (entry instanceof TypeAwareArrangementEntry && entry instanceof TextAwareArrangementEntry) {
        final Set<ArrangementSettingsToken> types = ((TypeAwareArrangementEntry)entry).getTypes();
        if (types.size() == 1) {
          final ArrangementSettingsToken type = types.iterator().next();
          if (type.equals(START_SECTION) || type.equals(END_SECTION)) {
            return StringUtil.equals(((TextAwareArrangementEntry)entry).getText(), sectionText);
          }
        }
      }
      return false;
    }

    private static <E extends ArrangementEntry> void closeSection(@Nullable ArrangementSectionRule section, @Nullable E entry, @Nonnull NewSectionInfo<E> info, boolean sectionIsOpen) {
      if (sectionIsOpen) {
        assert section != null && entry != null;
        if (StringUtil.isNotEmpty(section.getEndComment())) {
          info.addSectionEnd(entry, section.getEndComment());
        }
      }
    }

    private void addSectionStart(E entry, String comment) {
      mySectionStarts.put(entry, comment);
    }

    private void addSectionEnd(E entry, String comment) {
      mySectionEnds.put(entry, comment);
    }

    @Nullable
    public String getStartComment(E entry) {
      return mySectionStarts.get(entry);
    }

    @Nullable
    public String getEndComment(E entry) {
      return mySectionEnds.get(entry);
    }
  }

  private static class Context<E extends ArrangementEntry> {

    @Nonnull
    public final List<ArrangementMoveInfo> moveInfos = new ArrayList<>();

    @Nonnull
    public final Rearranger<E> rearranger;
    @Nonnull
    public final Collection<ArrangementEntryWrapper<E>> wrappers;
    @Nonnull
    public final Document document;
    @Nonnull
    public final List<? extends ArrangementMatchRule> rulesByPriority;
    @Nonnull
    public final CodeStyleSettings settings;
    @Nonnull
    public final Changer changer;
    @Nonnull
    public final List<ArrangementSectionRule> sectionRules;

    private Context(@Nonnull Rearranger<E> rearranger,
                    @Nonnull Collection<ArrangementEntryWrapper<E>> wrappers,
                    @Nonnull Document document,
                    @Nonnull List<ArrangementSectionRule> sectionRules,
                    @Nonnull List<? extends ArrangementMatchRule> rulesByPriority,
                    @Nonnull CodeStyleSettings settings,
                    @Nonnull Changer changer) {
      this.rearranger = rearranger;
      this.wrappers = wrappers;
      this.document = document;
      this.sectionRules = sectionRules;
      this.rulesByPriority = rulesByPriority;
      this.settings = settings;
      this.changer = changer;
    }

    public void addMoveInfo(int oldStart, int oldEnd, int newStart) {
      moveInfos.add(new ArrangementMoveInfo(oldStart, oldEnd, newStart));
    }

    public static <T extends ArrangementEntry> Context<T> from(
      @Nonnull Rearranger<T> rearranger,
      @Nonnull Document document,
      @Nonnull PsiElement root,
      @Nonnull Collection<TextRange> ranges,
      @Nonnull ArrangementSettings arrangementSettings,
      @Nonnull CodeStyleSettings codeStyleSettings
    ) {
      Collection<T> entries = rearranger.parse(root, document, ranges, arrangementSettings);
      Collection<ArrangementEntryWrapper<T>> wrappers = new ArrayList<>();
      ArrangementEntryWrapper<T> previous = null;
      for (T entry : entries) {
        ArrangementEntryWrapper<T> wrapper = new ArrangementEntryWrapper<>(entry);
        if (previous != null) {
          previous.setNext(wrapper);
          wrapper.setPrevious(previous);
        }
        wrappers.add(wrapper);
        previous = wrapper;
      }
      Changer changer;
      if (document instanceof DocumentEx) {
        changer = new RangeMarkerAwareChanger<T>((DocumentEx)document);
      }
      else {
        changer = new DefaultChanger();
      }
      final List<? extends ArrangementMatchRule> rulesByPriority = arrangementSettings.getRulesSortedByPriority();
      final List<ArrangementSectionRule> sectionRules = ArrangementUtil.getExtendedSectionRules(arrangementSettings);
      return new Context<>(rearranger, wrappers, document, sectionRules, rulesByPriority, codeStyleSettings, changer);
    }
  }

  private static class StackEntry {

    public int start;
    public int current;
    public int end;

    StackEntry(int start, int count) {
      this.start = start;
      current = start;
      end = start + count;
    }
  }

  private abstract static class Changer<E extends ArrangementEntry> {
    public abstract void prepare(@Nonnull List<ArrangementEntryWrapper<E>> toArrange, @Nonnull Context<E> context);

    /**
     * Replaces given 'old entry' by the given 'new entry'.
     *
     * @param newWrapper wrapper for an entry which text should replace given 'old entry' range
     * @param oldWrapper wrapper for an entry which range should be replaced by the given 'new entry'
     * @param previous   wrapper which will be previous for the entry referenced via the given 'new wrapper'
     * @param next       wrapper which will be next for the entry referenced via the given 'new wrapper'
     * @param context    current context
     */
    public abstract void replace(@Nonnull ArrangementEntryWrapper<E> newWrapper,
                                 @Nonnull ArrangementEntryWrapper<E> oldWrapper,
                                 @Nullable ArrangementEntryWrapper<E> previous,
                                 @Nullable ArrangementEntryWrapper<E> next,
                                 @Nonnull Context<E> context);

    public abstract void insert(@Nonnull Context<E> context, int startOffset, @Nonnull String text);

    public abstract void insertSection(@Nonnull Context<E> context,
                                       @Nonnull E entry,
                                       @Nonnull NewSectionInfo<E> newSectionsInfo,
                                       @Nonnull ArrangementEntryWrapper<E> arranged,
                                       @Nonnull ArrangementEntryWrapper<E> initial,
                                       @Nullable ArrangementEntryWrapper<E> parent);

    protected abstract boolean insertSection(@Nonnull Context<E> context,
                                             @Nonnull E entry,
                                             @Nonnull NewSectionInfo<E> newSectionsInfo,
                                             @Nullable ArrangementEntryWrapper<E> parent,
                                             int beforeOffset,
                                             int afterOffset);

    protected int getBlankLines(@Nonnull Context<E> context,
                                @Nullable ArrangementEntryWrapper<E> parentWrapper,
                                @Nonnull ArrangementEntryWrapper<E> targetWrapper,
                                @Nullable ArrangementEntryWrapper<E> previousWrapper,
                                @Nullable ArrangementEntryWrapper<E> nextWrapper) {
      final E target = targetWrapper.getEntry();
      final E previous = previousWrapper == null ? null : previousWrapper.getEntry();
      if (isTypeOf(target, END_SECTION) || isTypeOf(previous, START_SECTION)) {
        return 0;
      }
      final E next = nextWrapper == null ? null : nextWrapper.getEntry();
      if (next != null && isTypeOf(target, START_SECTION)) {
        return context.rearranger.getBlankLines(context.settings, parentWrapper == null ? null : parentWrapper.getEntry(), previous, next);
      }
      return context.rearranger.getBlankLines(context.settings, parentWrapper == null ? null : parentWrapper.getEntry(), previous, target);
    }

    private boolean isTypeOf(@Nullable E element, @Nonnull ArrangementSettingsToken token) {
      if (element instanceof TypeAwareArrangementEntry typeAwareArrangementEntry) {
        Set<ArrangementSettingsToken> types = typeAwareArrangementEntry.getTypes();
        return types.size() == 1 && token.equals(types.iterator().next());
      }
      return false;
    }
  }

  private static class DefaultChanger<E extends ArrangementEntry> extends Changer<E> {
    @Nonnull
    private String myParentText;
    private int myParentShift;

    @Override
    public void prepare(@Nonnull List<ArrangementEntryWrapper<E>> toArrange, @Nonnull Context<E> context) {
      ArrangementEntryWrapper<E> parent = toArrange.get(0).getParent();
      if (parent == null) {
        myParentText = context.document.getText();
        myParentShift = 0;
      }
      else {
        myParentText = context.document.getCharsSequence().subSequence(parent.getStartOffset(), parent.getEndOffset()).toString();
        myParentShift = parent.getStartOffset();
      }
    }

    @SuppressWarnings("AssignmentToForLoopParameter")
    @Override
    public void replace(@Nonnull ArrangementEntryWrapper<E> newWrapper,
                        @Nonnull ArrangementEntryWrapper<E> oldWrapper,
                        @Nullable ArrangementEntryWrapper<E> previous,
                        @Nullable ArrangementEntryWrapper<E> next,
                        @Nonnull Context<E> context) {
      // Calculate blank lines before the arrangement.
      int blankLinesBefore = 0;
      IntList lineFeedOffsets = IntLists.newArrayList();
      int oldStartLine = context.document.getLineNumber(oldWrapper.getStartOffset());
      if (oldStartLine > 0) {
        int lastLineFeed = context.document.getLineStartOffset(oldStartLine) - 1;
        lineFeedOffsets.add(lastLineFeed);
        for (int i = lastLineFeed - 1 - myParentShift; i >= 0; i--) {
          i = CharArrayUtil.shiftBackward(myParentText, i, " \t");
          if (myParentText.charAt(i) == '\n') {
            blankLinesBefore++;
            lineFeedOffsets.add(i + myParentShift);
          }
          else {
            break;
          }
        }
      }

      ArrangementEntryWrapper<E> parentWrapper = oldWrapper.getParent();
      int desiredBlankLinesNumber = getBlankLines(context, parentWrapper, newWrapper, previous, next);
      if (desiredBlankLinesNumber == blankLinesBefore && newWrapper.equals(oldWrapper)) {
        return;
      }

      String newEntryText = myParentText.substring(newWrapper.getStartOffset() - myParentShift, newWrapper.getEndOffset() - myParentShift);
      int lineFeedsDiff = desiredBlankLinesNumber - blankLinesBefore;
      if (lineFeedsDiff == 0 || desiredBlankLinesNumber < 0) {
        context.addMoveInfo(newWrapper.getStartOffset() - myParentShift, newWrapper.getEndOffset() - myParentShift, oldWrapper.getStartOffset());
        context.document.replaceString(oldWrapper.getStartOffset(), oldWrapper.getEndOffset(), newEntryText);
        return;
      }

      if (lineFeedsDiff > 0) {
        // Insert necessary number of blank lines.
        StringBuilder buffer = new StringBuilder(StringUtil.repeat("\n", lineFeedsDiff));
        buffer.append(newEntryText);
        context.document.replaceString(oldWrapper.getStartOffset(), oldWrapper.getEndOffset(), buffer);
      }
      else {
        // Cut exceeding blank lines.
        int replacementStartOffset = lineFeedOffsets.get(-lineFeedsDiff) + 1;
        context.document.replaceString(replacementStartOffset, oldWrapper.getEndOffset(), newEntryText);
      }

      // Update wrapper ranges.
      ArrangementEntryWrapper<E> parent = oldWrapper.getParent();
      if (parent == null) {
        return;
      }

      Deque<ArrangementEntryWrapper<E>> parents = new ArrayDeque<>();
      do {
        parents.add(parent);
        parent.setEndOffset(parent.getEndOffset() + lineFeedsDiff);
        parent = parent.getParent();
      }
      while (parent != null);


      while (!parents.isEmpty()) {

        for (ArrangementEntryWrapper<E> wrapper = parents.removeLast().getNext(); wrapper != null; wrapper = wrapper.getNext()) {
          wrapper.applyShift(lineFeedsDiff);
        }
      }
    }

    @Override
    public void insert(@Nonnull Context<E> context, int startOffset, @Nonnull String text) {
      context.document.insertString(startOffset, text);
    }

    @Override
    public void insertSection(@Nonnull Context<E> context,
                              @Nonnull E entry,
                              @Nonnull NewSectionInfo<E> newSectionsInfo,
                              @Nonnull ArrangementEntryWrapper<E> arrangedWrapper,
                              @Nonnull ArrangementEntryWrapper<E> initialWrapper,
                              @Nullable ArrangementEntryWrapper<E> parent) {
      final int beforeOffset = arrangedWrapper.equals(initialWrapper) ? arrangedWrapper.getStartOffset() : initialWrapper.getStartOffset();
      final int length = arrangedWrapper.getEndOffset() - arrangedWrapper.getStartOffset();
      int afterOffset = arrangedWrapper.equals(initialWrapper) ? arrangedWrapper.getEndOffset() : beforeOffset + length;

      insertSection(context, entry, newSectionsInfo, parent, beforeOffset, afterOffset);
    }

    @Override
    protected boolean insertSection(@Nonnull Context<E> context, @Nonnull E entry, @Nonnull NewSectionInfo<E> newSectionsInfo, ArrangementEntryWrapper<E> parent, int beforeOffset, int afterOffset) {
      boolean isInserted = false;
      final String afterComment = newSectionsInfo.getEndComment(entry);
      if (afterComment != null) {
        insert(context, afterOffset, "\n" + afterComment);
        isInserted = true;
      }
      final String beforeComment = newSectionsInfo.getStartComment(entry);
      if (beforeComment != null) {
        insert(context, beforeOffset, beforeComment + "\n");
        isInserted = true;
      }
      return isInserted;
    }
  }

  private static class RangeMarkerAwareChanger<E extends ArrangementEntry> extends Changer<E> {

    @Nonnull
    private final List<ArrangementEntryWrapper<E>> myWrappers = new ArrayList<>();
    @Nonnull
    private final DocumentEx myDocument;

    RangeMarkerAwareChanger(@Nonnull DocumentEx document) {
      myDocument = document;
    }

    @Override
    public void prepare(@Nonnull List<ArrangementEntryWrapper<E>> toArrange, @Nonnull Context<E> context) {
      myWrappers.clear();
      myWrappers.addAll(toArrange);
      for (ArrangementEntryWrapper<E> wrapper : toArrange) {
        wrapper.updateBlankLines(myDocument);
      }
    }

    @SuppressWarnings("AssignmentToForLoopParameter")
    @Override
    public void replace(@Nonnull ArrangementEntryWrapper<E> newWrapper,
                        @Nonnull ArrangementEntryWrapper<E> oldWrapper,
                        @Nullable ArrangementEntryWrapper<E> previous,
                        @Nullable ArrangementEntryWrapper<E> next,
                        @Nonnull Context<E> context) {
      // Calculate blank lines before the arrangement.
      int blankLinesBefore = oldWrapper.getBlankLinesBefore();

      ArrangementEntryWrapper<E> parentWrapper = oldWrapper.getParent();
      int desiredBlankLinesNumber = getBlankLines(context, parentWrapper, newWrapper, previous, next);
      if ((desiredBlankLinesNumber < 0 || desiredBlankLinesNumber == blankLinesBefore) && newWrapper.equals(oldWrapper)) {
        return;
      }

      int lineFeedsDiff = desiredBlankLinesNumber - blankLinesBefore;
      int insertionOffset = oldWrapper.getStartOffset();
      if (oldWrapper.getStartOffset() > newWrapper.getStartOffset()) {
        insertionOffset -= newWrapper.getEndOffset() - newWrapper.getStartOffset();
      }
      if (newWrapper.getStartOffset() != oldWrapper.getStartOffset() || !newWrapper.equals(oldWrapper)) {
        context.addMoveInfo(newWrapper.getStartOffset(), newWrapper.getEndOffset(), oldWrapper.getStartOffset());
        myDocument.moveText(newWrapper.getStartOffset(), newWrapper.getEndOffset(), oldWrapper.getStartOffset());
        for (int i = myWrappers.size() - 1; i >= 0; i--) {
          ArrangementEntryWrapper<E> w = myWrappers.get(i);
          if (w == newWrapper) {
            continue;
          }
          if (w.getStartOffset() >= oldWrapper.getStartOffset() && w.getStartOffset() < newWrapper.getStartOffset()) {
            w.applyShift(newWrapper.getEndOffset() - newWrapper.getStartOffset());
          }
          else if (oldWrapper != w && w.getStartOffset() <= oldWrapper.getStartOffset() && w.getStartOffset() > newWrapper.getStartOffset()) {
            w.applyShift(newWrapper.getStartOffset() - newWrapper.getEndOffset());
          }
        }
      }

      if (desiredBlankLinesNumber >= 0 && lineFeedsDiff > 0) {
        myDocument.insertString(insertionOffset, StringUtil.repeat("\n", lineFeedsDiff));
        shiftOffsets(lineFeedsDiff, insertionOffset);
      }

      if (desiredBlankLinesNumber >= 0 && lineFeedsDiff < 0) {
        // Cut exceeding blank lines.
        int replacementStartOffset = getBlankLineOffset(-lineFeedsDiff, insertionOffset);
        myDocument.deleteString(replacementStartOffset, insertionOffset);
        shiftOffsets(replacementStartOffset - insertionOffset, insertionOffset);
      }

      if (desiredBlankLinesNumber < 0) {
        return;
      }

      updateAllWrapperRanges(parentWrapper, lineFeedsDiff);
    }

    protected void updateAllWrapperRanges(@Nullable ArrangementEntryWrapper<E> parentWrapper, int lineFeedsDiff) {
      // Update wrapper ranges.
      if (lineFeedsDiff == 0 || parentWrapper == null) {
        return;
      }

      Deque<ArrangementEntryWrapper<E>> parents = new ArrayDeque<>();
      do {
        parents.add(parentWrapper);
        parentWrapper.setEndOffset(parentWrapper.getEndOffset() + lineFeedsDiff);
        parentWrapper = parentWrapper.getParent();
      }
      while (parentWrapper != null);


      while (!parents.isEmpty()) {
        for (ArrangementEntryWrapper<E> wrapper = parents.removeLast().getNext(); wrapper != null; wrapper = wrapper.getNext()) {
          wrapper.applyShift(lineFeedsDiff);
        }
      }
    }

    @Override
    public void insert(@Nonnull Context<E> context, int startOffset, @Nonnull String text) {
      myDocument.insertString(startOffset, text);
      int shift = text.length();
      for (int i = myWrappers.size() - 1; i >= 0; i--) {
        ArrangementEntryWrapper<E> wrapper = myWrappers.get(i);
        if (wrapper.getStartOffset() >= startOffset) {
          wrapper.applyShift(shift);
        }
      }
    }

    @Override
    public void insertSection(@Nonnull Context<E> context,
                              @Nonnull E entry,
                              @Nonnull NewSectionInfo<E> newSectionsInfo,
                              @Nonnull ArrangementEntryWrapper<E> arrangedWrapper,
                              @Nonnull ArrangementEntryWrapper<E> initialWrapper,
                              @Nullable ArrangementEntryWrapper<E> parent) {
      final int afterOffset = arrangedWrapper.equals(initialWrapper) ? arrangedWrapper.getEndOffset() : initialWrapper.getStartOffset();
      final int length = arrangedWrapper.getEndOffset() - arrangedWrapper.getStartOffset();
      final int beforeOffset = arrangedWrapper.equals(initialWrapper) ? arrangedWrapper.getStartOffset() : afterOffset - length;
      insertSection(context, entry, newSectionsInfo, parent, beforeOffset, afterOffset);
    }

    @Override
    protected boolean insertSection(@Nonnull Context<E> context,
                                    @Nonnull E entry,
                                    @Nonnull NewSectionInfo<E> newSectionsInfo,
                                    @Nullable ArrangementEntryWrapper<E> parent,
                                    int beforeOffset,
                                    int afterOffset) {
      boolean isInserted = false;
      int diff = 0;
      final String afterComment = newSectionsInfo.getEndComment(entry);
      if (afterComment != null) {
        insert(context, afterOffset, "\n" + afterComment);
        diff += afterComment.length() + 1;
        isInserted = true;
      }
      final String beforeComment = newSectionsInfo.getStartComment(entry);
      if (beforeComment != null) {
        insert(context, beforeOffset, beforeComment + "\n");
        diff += beforeComment.length() + 1;
        isInserted = true;
      }

      updateAllWrapperRanges(parent, diff);

      return isInserted;
    }

    /**
     * @return position <code>x</code> for which <code>myDocument.getText().substring(x, startOffset)</code> contains
     * <code>blankLinesNumber</code> line feeds and <code>myDocument.getText.charAt(x-1) == '\n'</code>
     */
    private int getBlankLineOffset(int blankLinesNumber, int startOffset) {
      int startLine = myDocument.getLineNumber(startOffset);
      if (startLine <= 0) {
        return 0;
      }
      CharSequence text = myDocument.getCharsSequence();
      for (int i = myDocument.getLineStartOffset(startLine - 1) - 1; i >= 0; i = CharArrayUtil.lastIndexOf(text, "\n", i - 1)) {
        if (--blankLinesNumber <= 0) {
          return i + 1;
        }
      }
      return 0;
    }

    private void shiftOffsets(int shift, int changeOffset) {
      for (int i = myWrappers.size() - 1; i >= 0; i--) {
        ArrangementEntryWrapper<E> wrapper = myWrappers.get(i);
        if (wrapper.getStartOffset() < changeOffset) {
          break;
        }
        wrapper.applyShift(shift);
      }
    }
  }
}
