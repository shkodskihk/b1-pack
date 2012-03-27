/*
 * Copyright 2012 b1.org                                                   
 *                                                                         
 * Licensed under the Apache License, Version 2.0 (the "License");         
 * you may not use this file except in compliance with the License.        
 * You may obtain a copy of the License at                                 
 *                                                                         
 *     http://www.apache.org/licenses/LICENSE-2.0                          
 *                                                                         
 * Unless required by applicable law or agreed to in writing, software     
 * distributed under the License is distributed on an "AS IS" BASIS,       
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and     
 * limitations under the License.
 */

package org.b1.pack.standard.reader;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import org.b1.pack.api.reader.ReaderFileVisitor;
import org.b1.pack.api.reader.ReaderFolderVisitor;
import org.b1.pack.api.reader.ReaderPackVisitor;
import org.b1.pack.standard.common.Constants;
import org.b1.pack.standard.common.Numbers;
import org.b1.pack.standard.common.RecordPointer;
import org.b1.pack.standard.explorer.RecordHeader;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class RecordReader {

    private final Map<Long, ReaderPackVisitor> visitorMap = Maps.newHashMap();
    private final List<StandardReaderContent> readerContents = Lists.newArrayList();
    private final LinkedList<ReaderFolderVisitor> folderVisitors = Lists.newLinkedList();

    public RecordReader(ReaderPackVisitor rootVisitor) {
        visitorMap.put(null, rootVisitor);
    }
    
    public void read(PackInputStream stream, Long objectTotal) throws IOException {
        readCatalog(stream, objectTotal);
        for (StandardReaderContent readerContent : readerContents) {
            readerContent.acceptVisitor();
        }
        for (ReaderFolderVisitor folderVisitor : folderVisitors) {
            folderVisitor.visitEnd();
        }
    }

    private void readCatalog(PackInputStream stream, Long totalCount) throws IOException {
        Long recordType;
        long currentCount = 0;
        while ((totalCount == null || currentCount < totalCount) && (recordType = Numbers.readLong(stream)) != null) {
            switch (Ints.checkedCast(recordType)) {
                case Constants.RECORD_POINTER:
                    stream.seek(readPointer(stream));
                    break;
                case Constants.CATALOG_FILE:
                    currentCount++;
                    readCatalogFile(stream);
                    break;
                case Constants.CATALOG_FOLDER:
                    currentCount++;
                    readCatalogFolder(stream);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid entry");
            }
        }
    }

    private void readCatalogFile(PackInputStream stream) throws IOException {
        RecordPointer pointer = readPointer(stream);
        RecordHeader header = RecordHeader.readRecordHeader(stream);
        Long size = Numbers.readLong(stream);
        ReaderPackVisitor packVisitor = visitorMap.get(header.parentId);
        if (packVisitor == null) {
            return;
        }
        ReaderFileVisitor fileVisitor = packVisitor.visitFile(createEntry(header), size);
        if (fileVisitor != null) {
            readerContents.add(new StandardReaderContent(header.id, pointer, stream, fileVisitor));
        }
    }

    private void readCatalogFolder(PackInputStream stream) throws IOException {
        readPointer(stream); // ignore for now
        RecordHeader header = RecordHeader.readRecordHeader(stream);
        ReaderPackVisitor packVisitor = visitorMap.get(header.parentId);
        if (packVisitor == null) {
            return;
        }
        ReaderFolderVisitor folderVisitor = packVisitor.visitFolder(createEntry(header));
        if (folderVisitor == null) {
            return;
        }
        folderVisitors.addFirst(folderVisitor);
        ReaderPackVisitor childrenVisitor = folderVisitor.visitChildren();
        if (childrenVisitor != null) {
            Preconditions.checkState(visitorMap.put(header.id, childrenVisitor) == null);
        }
    }

    private static StandardReaderEntry createEntry(RecordHeader header) {
        return new StandardReaderEntry(header.name, header.lastModifiedTime);
    }

    private static RecordPointer readPointer(InputStream stream) throws IOException {
        return new RecordPointer(Numbers.readLong(stream), Numbers.readLong(stream), Numbers.readLong(stream));
    }
}
