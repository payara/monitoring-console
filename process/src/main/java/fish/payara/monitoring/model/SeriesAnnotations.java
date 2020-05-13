/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.monitoring.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * {@link SeriesAnnotations} are an concurrent size limited collection of {@link SeriesAnnotation}s.
 *
 * When adding a {@link SeriesAnnotation} exceeds the capacity the oldest {@link SeriesAnnotation} that is not
 * {@link SeriesAnnotation#isPermanent()} is removed.
 *
 * @author Jan Bernitt
 * @since 5.202
 */
public final class SeriesAnnotations implements Iterable<SeriesAnnotation> {

    private final int capacity;
    private final Queue<SeriesAnnotation> annotations = new ConcurrentLinkedQueue<>();

    public SeriesAnnotations(int capacity) {
        this.capacity = capacity;
    }

    public void add(SeriesAnnotation annotation) {
        if (annotation.isKeyed()) {
            annotations.removeIf(a -> Objects.equals(a.getKeyAttribute(), annotation.getKeyAttribute()));
        }
        annotations.add(annotation);
        if (annotations.size() > capacity) {
            SeriesAnnotation removed = annotations.poll();
            int attempts = 1;
            while (attempts < capacity && removed != null && removed.isPermanent()) {
                annotations.add(removed); // add the permanent to tail so another one gets removed
                removed = annotations.poll();
                attempts++;
            }
        }
    }

    @Override
    public Iterator<SeriesAnnotation> iterator() {
        return annotations.iterator();
    }

    public boolean isEmpty() {
        return annotations.isEmpty();
    }

    public Stream<SeriesAnnotation> stream() {
        return StreamSupport.stream(annotations.spliterator(), false);
    }

    public List<SeriesAnnotation> toList() {
        return new ArrayList<>(annotations);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SeriesAnnotations && annotations.equals(((SeriesAnnotations) obj).annotations);
    }

    @Override
    public int hashCode() {
        return annotations.hashCode();
    }

    @Override
    public String toString() {
        return annotations.toString();
    }

    public int size() {
        return annotations.size();
    }
}
