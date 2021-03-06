/*
 * All content copyright Terracotta, Inc., unless otherwise indicated.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.statistics.archive;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author cdennis
 */
public class StatisticArchive<T> implements SampleSink<Timestamped<T>> {

  private static final Comparator<Timestamped<?>> TIMESTAMPED_COMPARATOR = Comparator.comparingLong(Timestamped::getTimestamp);

  private final SampleSink<? super Timestamped<T>> overspill;

  private volatile int size;
  private volatile CircularBuffer<Timestamped<T>> buffer;

  public StatisticArchive(int size) {
    this(size, SampleSink.devNull());
  }

  public StatisticArchive(int size, SampleSink<? super Timestamped<T>> overspill) {
    this.size = size;
    this.overspill = overspill;
  }

  public synchronized void setCapacity(int samples) {
    if (samples != size) {
      size = samples;
      if (buffer != null) {
        CircularBuffer<Timestamped<T>> newBuffer = new CircularBuffer<>(size);
        for (Timestamped<T> sample : getArchive()) {
          overspill.accept(newBuffer.insert(sample));
        }
        buffer = newBuffer;
      }
    }
  }

  @Override
  public synchronized void accept(Timestamped<T> object) {
    if (buffer == null) {
      buffer = new CircularBuffer<>(size);
    }
    overspill.accept(buffer.insert(object));
  }

  public synchronized void clear() {
    buffer = null;
  }

  @SuppressWarnings("unchecked")
  public List<Timestamped<T>> getArchive() {
    CircularBuffer<Timestamped<T>> read = buffer;
    if (read == null) {
      return Collections.emptyList();
    } else {
      return Collections.unmodifiableList(Arrays.<Timestamped<T>>asList(read.toArray(Timestamped[].class)));
    }
  }

  public List<Timestamped<T>> getArchive(long since) {
    CircularBuffer<Timestamped<T>> read = buffer;
    if (read == null) {
      return Collections.emptyList();
    } else {
      Timestamped<T> e = new StatisticSampler.Sample<>(since, null);
      @SuppressWarnings("unchecked")
      Timestamped<T>[] array = (Timestamped<T>[]) read.toArray(Timestamped[].class);
      int pos = Arrays.binarySearch(array, e, TIMESTAMPED_COMPARATOR);
      if(pos < 0) {
        pos = -pos - 1;
      }
      if(pos >= array.length) {
        return Collections.emptyList();
      } else {
        return Collections.unmodifiableList(Arrays.asList(Arrays.copyOfRange(array, pos, array.length)));
      }
    }
  }

}
