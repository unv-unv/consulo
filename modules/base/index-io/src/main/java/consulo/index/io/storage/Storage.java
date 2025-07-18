/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.index.io.storage;

import consulo.index.io.PagePool;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class Storage extends AbstractStorage {
  public Storage(String path) throws IOException {
    super(path);
  }

  public Storage(String path, PagePool pool) throws IOException {
    super(path, pool);
  }

  public Storage(String path, CapacityAllocationPolicy capacityAllocationPolicy) throws IOException {
    super(path, capacityAllocationPolicy);
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
    return new RecordsTable(recordsFile, pool);
  }

  public int createNewRecord() throws IOException {
    synchronized (myLock) {
      return myRecordsTable.createNewRecord();
    }
  }

  public void deleteRecord(int record) throws IOException {
    assert record > 0;
    synchronized (myLock) {
      doDeleteRecord(record);
    }
  }
}
