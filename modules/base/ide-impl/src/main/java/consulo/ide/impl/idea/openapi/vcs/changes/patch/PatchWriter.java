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
package consulo.ide.impl.idea.openapi.vcs.changes.patch;

import consulo.ide.impl.idea.codeStyle.CodeStyleFacade;
import consulo.ide.impl.idea.openapi.diff.impl.patch.FilePatch;
import consulo.ide.impl.idea.openapi.diff.impl.patch.PatchEP;
import consulo.ide.impl.idea.openapi.diff.impl.patch.UnifiedDiffWriter;
import consulo.component.extension.Extensions;
import consulo.project.Project;
import consulo.versionControlSystem.change.CommitContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

public class PatchWriter {
    private PatchWriter() {
    }

    public static void writePatches(
        @Nonnull final Project project,
        String fileName,
        @Nullable String basePath,
        List<FilePatch> patches,
        CommitContext commitContext,
        @Nonnull Charset charset
    ) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(fileName), charset);
        try {
            final String lineSeparator = CodeStyleFacade.getInstance(project).getLineSeparator();
            UnifiedDiffWriter.write(
                project,
                basePath,
                patches,
                writer,
                lineSeparator,
                Extensions.getExtensions(PatchEP.EP_NAME, project),
                commitContext
            );
        }
        finally {
            writer.close();
        }
    }
}
