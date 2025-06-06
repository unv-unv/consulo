/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ui;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.component.extension.ExtensionPoint;
import consulo.ide.impl.idea.util.FunctionUtil;
import consulo.ide.localize.IdeLocalize;
import consulo.language.Language;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.*;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.ElementColorProvider;
import consulo.language.psi.PsiElement;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.awt.ColorChooser;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.ui.image.ImageKey;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public final class ColorLineMarkerProvider implements LineMarkerProvider, DumbAware {
    private static class MyInfo extends MergeableLineMarkerInfo<PsiElement> {
        private final ColorValue myColor;

        @RequiredReadAction
        public MyInfo(@Nonnull PsiElement element, ColorValue color, ElementColorProvider colorProvider) {
            super(
                element,
                element.getTextRange(),
                ImageEffects.colorFilled(12, 12, color),
                Pass.UPDATE_ALL,
                FunctionUtil.nullConstant(),
                new GutterIconNavigationHandler<>() {
                    @Override
                    @RequiredUIAccess
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (!elt.isWritable()) {
                            return;
                        }

                        Editor editor = PsiUtilBase.findEditor(element);
                        assert editor != null;

                        ColorChooser.chooseColor(
                            editor.getComponent(),
                            IdeLocalize.dialogTitleChooseColor().get(),
                            TargetAWT.to(color),
                            true,
                            c -> {
                                if (c != null) {
                                    CommandProcessor.getInstance().newCommand()
                                        .project(element.getProject())
                                        .inWriteAction()
                                        .run(() -> colorProvider.setColorTo(element, TargetAWT.from(c)));
                                }
                            }
                        );
                    }
                },
                GutterIconRenderer.Alignment.LEFT
            );
            myColor = color;
        }

        @Override
        public boolean canMergeWith(@Nonnull MergeableLineMarkerInfo<?> info) {
            return info instanceof MyInfo;
        }

        @Nonnull
        @Override
        public Image getCommonIcon(@Nonnull List<MergeableLineMarkerInfo> infos) {
            ImageKey colors = PlatformIconGroup.gutterColors();
            return ImageEffects.canvas(colors.getWidth(), colors.getHeight(), canvas2D -> {
                for (int i = 0; i < 4; i++) {
                    // backward
                    MyInfo info = i >= infos.size() ? null : (MyInfo) infos.get(infos.size() - i - 1);
                    if (info == null) {
                        continue;
                    }

                    int x;
                    int y;
                    switch (i) {
                        case 0:
                            x = 0;
                            y = 0;
                            break;
                        case 1:
                            x = 7;
                            y = 0;
                            break;
                        case 2:
                            x = 0;
                            y = 7;
                            break;
                        case 3:
                            x = 7;
                            y = 7;
                            break;
                        default:
                            continue;
                    }

                    canvas2D.setFillStyle(info.myColor);
                    canvas2D.fillRect(x, y, 5, 5);
                }
            });
        }

        @Nonnull
        @Override
        public Function<? super PsiElement, String> getCommonTooltip(@Nonnull List<MergeableLineMarkerInfo> infos) {
            return FunctionUtil.nullConstant();
        }
    }

    private final Application myApplication;

    @Inject
    public ColorLineMarkerProvider(Application application) {
        myApplication = application;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return Language.ANY;
    }

    @Override
    @RequiredReadAction
    public LineMarkerInfo getLineMarkerInfo(@Nonnull PsiElement element) {
        ExtensionPoint<ElementColorProvider> point = myApplication.getExtensionPoint(ElementColorProvider.class);
        Map.Entry<ElementColorProvider, ColorValue> colorInfo = point.computeSafeIfAny(it -> {
            ColorValue value = it.getColorFrom(element);
            return value != null ? Map.entry(it, value) : null;
        });

        if (colorInfo != null) {
            MyInfo info = new MyInfo(element, colorInfo.getValue(), colorInfo.getKey());
            NavigateAction.setNavigateAction(info, "Choose color", null);
            return info;
        }
        return null;
    }
}
