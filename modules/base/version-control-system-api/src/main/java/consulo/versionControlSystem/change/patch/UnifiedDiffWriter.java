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
package consulo.versionControlSystem.change.patch;

import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.change.CommitContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 * @since 2006-11-24
 */
public class UnifiedDiffWriter {
    private static final String INDEX_SIGNATURE = "Index: {0}{1}";
    public static final String ADDITIONAL_PREFIX = "IDEA additional info:";
    public static final String ADD_INFO_HEADER = "Subsystem: ";
    public static final String ADD_INFO_LINE_START = "<+>";
    private static final String HEADER_SEPARATOR = "===================================================================";
    public static final String NO_NEWLINE_SIGNATURE = "\\ No newline at end of file";

    private UnifiedDiffWriter() {
    }

    public static void write(
        @Nullable Project project,
        Collection<FilePatch> patches,
        Writer writer,
        String lineSeparator,
        @Nullable CommitContext commitContext
    ) throws IOException {
        PatchEP[] extensions = project == null ? new PatchEP[0] : PatchEP.EP_NAME.getExtensions(project);
        write(project, patches, writer, lineSeparator, extensions, commitContext);
    }

    public static void write(
        @Nullable Project project,
        Collection<FilePatch> patches,
        Writer writer,
        String lineSeparator,
        PatchEP[] extensions,
        CommitContext commitContext
    ) throws IOException {
        write(project, project == null ? null : project.getBasePath(), patches, writer, lineSeparator, extensions, commitContext);
    }

    public static void write(
        @Nullable Project project,
        @Nullable String basePath,
        Collection<FilePatch> patches,
        Writer writer,
        String lineSeparator,
        @Nonnull PatchEP[] extensions,
        CommitContext commitContext
    ) throws IOException {
        for (FilePatch filePatch : patches) {
            if (!(filePatch instanceof TextFilePatch patch)) {
                continue;
            }
            String path = ObjectUtil.assertNotNull(patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName());
            String pathRelatedToProjectDir =
                project == null ? path : getPathRelatedToDir(ObjectUtil.assertNotNull(project.getBasePath()), basePath, path);
            Map<String, CharSequence> additionalMap = new HashMap<>();
            for (PatchEP extension : extensions) {
                CharSequence charSequence = extension.provideContent(pathRelatedToProjectDir, commitContext);
                if (!StringUtil.isEmpty(charSequence)) {
                    additionalMap.put(extension.getName(), charSequence);
                }
            }
            writeFileHeading(patch, writer, lineSeparator, additionalMap);
            for (PatchHunk hunk : patch.getHunks()) {
                writeHunkStart(
                    writer,
                    hunk.getStartLineBefore(),
                    hunk.getEndLineBefore(),
                    hunk.getStartLineAfter(),
                    hunk.getEndLineAfter(),
                    lineSeparator
                );
                for (PatchLine line : hunk.getLines()) {
                    char prefixChar = ' ';
                    switch (line.getType()) {
                        case ADD:
                            prefixChar = '+';
                            break;
                        case REMOVE:
                            prefixChar = '-';
                            break;
                        case CONTEXT:
                            prefixChar = ' ';
                            break;
                    }
                    String text = line.getText();
                    text = StringUtil.trimEnd(text, "\n");
                    writeLine(writer, text, prefixChar);
                    if (line.isSuppressNewLine()) {
                        writer.write(lineSeparator + NO_NEWLINE_SIGNATURE + lineSeparator);
                    }
                    else {
                        writer.write(lineSeparator);
                    }
                }
            }
        }
    }

    @Nonnull
    private static String getPathRelatedToDir(@Nonnull String newBaseDir, @Nullable String basePath, @Nonnull String path) {
        if (basePath == null) {
            return path;
        }
        String result = FileUtil.getRelativePath(new File(newBaseDir), new File(basePath, path));
        return result == null ? path : result;
    }

    private static void writeFileHeading(
        FilePatch patch,
        Writer writer,
        String lineSeparator,
        Map<String, CharSequence> additionalMap
    ) throws IOException {
        writer.write(MessageFormat.format(INDEX_SIGNATURE, patch.getBeforeName(), lineSeparator));
        if (additionalMap != null && !additionalMap.isEmpty()) {
            writer.write(ADDITIONAL_PREFIX);
            writer.write(lineSeparator);
            for (Map.Entry<String, CharSequence> entry : additionalMap.entrySet()) {
                writer.write(ADD_INFO_HEADER + entry.getKey());
                writer.write(lineSeparator);
                String value = StringUtil.escapeStringCharacters(entry.getValue().toString());
                List<String> lines = StringUtil.split(value, "\n");
                for (String line : lines) {
                    writer.write(ADD_INFO_LINE_START);
                    writer.write(line);
                    writer.write(lineSeparator);
                }
            }
        }
        writer.write(HEADER_SEPARATOR + lineSeparator);
        writeRevisionHeading(writer, "---", patch.getBeforeName(), patch.getBeforeVersionId(), lineSeparator);
        writeRevisionHeading(writer, "+++", patch.getAfterName(), patch.getAfterVersionId(), lineSeparator);
    }

    private static void writeRevisionHeading(Writer writer, String prefix, String revisionPath, String revisionName, String lineSeparator)
        throws IOException {
        writer.write(prefix + " ");
        writer.write(revisionPath);
        writer.write("\t");
        if (!StringUtil.isEmptyOrSpaces(revisionName)) {
            writer.write(revisionName);
        }
        writer.write(lineSeparator);
    }

    private static void writeHunkStart(Writer writer, int startLine1, int endLine1, int startLine2, int endLine2, String lineSeparator)
        throws IOException {
        StringBuilder builder = new StringBuilder("@@ -");
        builder.append(startLine1 + 1).append(",").append(endLine1 - startLine1);
        builder.append(" +").append(startLine2 + 1).append(",").append(endLine2 - startLine2).append(" @@").append(lineSeparator);
        writer.append(builder.toString());
    }

    private static void writeLine(Writer writer, String line, char prefix) throws IOException {
        writer.write(prefix);
        writer.write(line);
    }
}
