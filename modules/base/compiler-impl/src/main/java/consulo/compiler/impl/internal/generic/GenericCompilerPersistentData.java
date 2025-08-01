/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.compiler.impl.internal.generic;

import consulo.index.io.data.IOUtil;
import consulo.logging.Logger;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class GenericCompilerPersistentData {
    private static final Logger LOG = Logger.getInstance(GenericCompilerPersistentData.class);
    private static final int VERSION = 1;
    private File myFile;
    private Map<String, Integer> myTarget2Id = new HashMap<>();
    private IntSet myUsedIds = IntSets.newHashSet();
    private boolean myVersionChanged;
    private final int myCompilerVersion;

    public GenericCompilerPersistentData(File cacheStoreDirectory, int compilerVersion) throws IOException {
        myCompilerVersion = compilerVersion;
        myFile = new File(cacheStoreDirectory, "info");
        if (!myFile.exists()) {
            LOG.info("Compiler info file doesn't exists: " + myFile.getAbsolutePath());
            myVersionChanged = true;
            return;
        }

        try {
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myFile)))) {
                int dataVersion = input.readInt();
                if (dataVersion != VERSION) {
                    LOG.info("Version of compiler info file (" + myFile.getAbsolutePath() + ") changed: " + dataVersion + " -> " + VERSION);
                    myVersionChanged = true;
                    return;
                }

                int savedCompilerVersion = input.readInt();
                if (savedCompilerVersion != compilerVersion) {
                    LOG.info(
                        "Compiler caches version changed (" + myFile.getAbsolutePath() + "): " +
                            savedCompilerVersion + " -> " + compilerVersion
                    );
                    myVersionChanged = true;
                    return;
                }

                int size = input.readInt();
                while (size-- > 0) {
                    String target = IOUtil.readString(input);
                    int id = input.readInt();
                    myTarget2Id.put(target, id);
                    myUsedIds.add(id);
                }
            }
        }
        catch (IOException e) {
            FileUtil.delete(myFile);
            throw e;
        }
    }

    public boolean isVersionChanged() {
        return myVersionChanged;
    }

    public void save() throws IOException {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myFile)))) {
            output.writeInt(VERSION);
            output.writeInt(myCompilerVersion);
            output.writeInt(myTarget2Id.size());

            for (Map.Entry<String, Integer> entry : myTarget2Id.entrySet()) {
                IOUtil.writeString(entry.getKey(), output);
                output.writeInt(entry.getValue());
            }
        }
    }

    public int getId(@Nonnull String target) {
        if (myTarget2Id.containsKey(target)) {
            return myTarget2Id.get(target);
        }
        int id = 0;
        while (myUsedIds.contains(id)) {
            id++;
        }
        myTarget2Id.put(target, id);
        myUsedIds.add(id);
        return id;
    }

    public Set<String> getAllTargets() {
        return myTarget2Id.keySet();
    }

    public int removeId(String target) {
        return myTarget2Id.remove(target);
    }
}
