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

package org.b1.pack.standard.writer;

import SevenZip.Compression.LZMA.Encoder;
import org.b1.pack.api.builder.Writable;
import org.b1.pack.standard.common.RecordPointer;
import org.b1.pack.standard.common.SynchronousPipe;

import java.io.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class LzmaWriter extends ChunkWriter implements Callable<Void> {

    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private final SynchronousPipe pipe = new SynchronousPipe();
    private final InputStream pipedInputStream = pipe.inputStream;
    private final OutputStream pipedOutputStream = pipe.outputStream;
    private final LzmaMethod lzmaMethod;
    private final OutputStream outputStream;
    private final RecordPointer startPointer;
    private final Future<Void> future;
    private long count;

    public LzmaWriter(LzmaMethod lzmaMethod, BlockWriter blockWriter, ExecutorService executorService) throws IOException {
        this.lzmaMethod = lzmaMethod;
        this.outputStream = new BufferedOutputStream(blockWriter);
        this.startPointer = blockWriter.getCurrentPointer();
        this.future = executorService.submit(this);
    }

    public RecordPointer getCurrentPointer() throws IOException {
        return new RecordPointer(startPointer.volumeNumber, startPointer.blockOffset, count);
    }

    public long getCount() {
        return count;
    }

    @Override
    public void write(int b) throws IOException {
        try {
            pipedOutputStream.write(b);
            count++;
        } catch (IOException e) {
            checkEncoder();
            throw e;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            pipedOutputStream.write(b, off, len);
            count += len;
        } catch (IOException e) {
            checkEncoder();
            throw e;
        }
    }

    @Override
    public void write(Writable value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        pipedOutputStream.close();
        try {
            completionLatch.await();
            future.get();
        } catch (Exception e) {
            throw (IOException) new IOException().initCause(e);
        }
    }

    @Override
    public Void call() throws IOException {
        try {
            startLatch.countDown();
            Encoder encoder = new Encoder();
            encoder.SetEndMarkerMode(true);
            encoder.SetDictionarySize(lzmaMethod.getDictionarySize());
            encoder.SetNumFastBytes(lzmaMethod.getNumberOfFastBytes());
            encoder.WriteCoderProperties(outputStream);
            encoder.Code(pipedInputStream, outputStream, -1, -1, null);
            outputStream.flush();
            return null;
        } finally {
            completionLatch.countDown();
            pipedInputStream.close();
        }
    }

    public void cleanup() {
        await(startLatch);
        future.cancel(true);
        await(completionLatch);
    }

    private static void await(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkEncoder() throws IOException {
        if (completionLatch.getCount() == 0) {
            try {
                future.get();
            } catch (Exception e) {
                throw (IOException) new IOException().initCause(e);
            }
        }
    }
}
