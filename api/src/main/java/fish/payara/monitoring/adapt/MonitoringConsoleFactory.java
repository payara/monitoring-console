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
package fish.payara.monitoring.adapt;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.collect.MonitoringWatchSource;

/**
 * The monitoring console library provides an implementations for this interface that is accessed using the
 * {@link ServiceLoader} mechanism, {@link #getInstance()} can be used for convenience. 
 * 
 * This allows a monitored application to be in control of creating the {@link MonitoringConsole} using the resolved
 * factory instance. This is usually needed so that the monitored application can bootstrap the
 * {@link MonitoringConsole} at the right point in its bootstrapping process.
 * 
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public interface MonitoringConsoleFactory {

    /**
     * Creates a new instance of a {@link MonitoringConsole} processing unit.
     * 
     * @param instance the name of the instance the console represent, the instance it collects data from
     * @param receiver true, if the created instance is the single receiver of the cluster (the central instance), false
     *                 if it is one of the senders of the cluster.
     * @param runtime  the runtime to use by the created instance, cannot be {@code null}
     * @return A new logical instance of the {@link MonitoringConsole}
     */
    MonitoringConsole create(String instance, boolean receiver, MonitoringConsoleRuntime runtime,
            Supplier<? extends List<MonitoringDataSource>> dataSources,
            Supplier<? extends List<MonitoringWatchSource>> watchSources);

    /**
     * @return The {@link MonitoringConsole} instance previously created using
     *         {@link #create(String, boolean, MonitoringConsoleRuntime)}
     * @throws IllegalStateException if this method is called before
     *                               {@link #create(String, boolean, MonitoringConsoleRuntime)} was
     */
    MonitoringConsole getCreatedConsole() throws IllegalStateException;

    /**
     * @return A {@link MonitoringConsoleFactory} instance or {@code null} if none is available on classpath.
     */
    static MonitoringConsoleFactory getInstance() {
        Iterator<MonitoringConsoleFactory> iter = ServiceLoader.load(MonitoringConsoleFactory.class).iterator();
        return iter.hasNext() ? iter.next() : null;
    }
}
