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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests that {@link SeriesAnnotations#add(SeriesAnnotation)} behaves as expected.
 *
 * @author Jan Bernitt
 * @since 5.202
 */
public class SeriesAnnotationsTest {

    private int valueCounter;

    @Test
    public void capacityLimitsSize() {
        SeriesAnnotations queue = new SeriesAnnotations(3);
        queue.add(nextAnnotation());
        queue.add(nextAnnotation());
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 1,2,3);
        for (int i = 0; i < 10; i++) {
            queue.add(nextAnnotation());
            assertAnnotationsWithValues(queue, (1+i+1), (2+i+1), (3+i+1));
        }
    }

    @Test
    public void keyedAnnotationsAreReplaceExistingAnnotationWithSameKeyValuePair() {
        SeriesAnnotations queue = new SeriesAnnotations(3);
        queue.add(nextAnnotation());
        queue.add(nextAnnotation(true));
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 1,2,3);
        queue.add(nextAnnotation(true));
        assertAnnotationsWithValues(queue, 1,3,4);
        queue.add(nextAnnotation(true));
        assertAnnotationsWithValues(queue, 1,3,5);
        queue.add(nextAnnotation(true));
        assertAnnotationsWithValues(queue, 1,3,6);
    }

    @Test
    public void permanentAnnotationsAreKeptOverNonPermanentAnnotations_Single() {
        SeriesAnnotations queue = new SeriesAnnotations(3);
        queue.add(nextAnnotation().permanent());
        queue.add(nextAnnotation());
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 1,2,3);
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 3,4,1);
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 4,1,5);
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 1,5,6);
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 6,7,1);
    }

    @Test
    public void permanentAnnotationsAreKeptOverNonPermanentAnnotations_Multiple() {
        SeriesAnnotations queue = new SeriesAnnotations(3);
        queue.add(nextAnnotation().permanent());
        queue.add(nextAnnotation().permanent());
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 1,2,3);
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 4,1,2);
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 1,2,5);
        queue.add(nextAnnotation());
        assertAnnotationsWithValues(queue, 6,1,2);
    }

    @Test
    public void capacityLimitsSizeEvenForPermanentAnnoations() {
        SeriesAnnotations queue = new SeriesAnnotations(3);
        queue.add(nextAnnotation().permanent());
        queue.add(nextAnnotation().permanent());
        queue.add(nextAnnotation().permanent());
        assertAnnotationsWithValues(queue, 1,2,3);
        queue.add(nextAnnotation().permanent());
        assertAnnotationsWithValues(queue, 4,1,2);
        queue.add(nextAnnotation().permanent());
        assertAnnotationsWithValues(queue, 5,4,1);
        queue.add(nextAnnotation().permanent());
        assertAnnotationsWithValues(queue, 6,5,4);
    }

    private static void assertAnnotationsWithValues(SeriesAnnotations actual, long... expectedValues) {
        assertEquals(expectedValues.length, actual.size());
        int i = 0;
        for (SeriesAnnotation a : actual) {
            assertEquals(expectedValues[i++], a.getValue());
        }
    }

    private SeriesAnnotation nextAnnotation() {
        return nextAnnotation(false);
    }

    private SeriesAnnotation nextAnnotation(boolean keyed) {
        long time = System.currentTimeMillis();
        return new SeriesAnnotation(time, new Series("series"), "instance", ++valueCounter, keyed, "key1", "value1", "key2", "value2");
    }
}
