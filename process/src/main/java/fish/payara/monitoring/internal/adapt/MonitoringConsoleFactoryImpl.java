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
package fish.payara.monitoring.internal.adapt;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import fish.payara.monitoring.adapt.MonitoringConsole;
import fish.payara.monitoring.adapt.MonitoringConsoleFactory;
import fish.payara.monitoring.adapt.MonitoringConsoleRuntime;
import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.collect.MonitoringWatchSource;

/**
 * Implementation for the {@link MonitoringConsoleFactory} linked via Java's {@link ServiceLoader} mechanism.
 * 
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public class MonitoringConsoleFactoryImpl implements MonitoringConsoleFactory {

    /**
     * This has to be a static field since each {@link ClassLoader} or each {@link ServiceLoader} invocation does create
     * its own instance of the class yet this class is meant to give access to the instance created previously.
     */
    private final static AtomicReference<MonitoringConsole> console = new AtomicReference<>();

    @Override
    public MonitoringConsole create(String instance, boolean receiver, MonitoringConsoleRuntime runtime,
            Supplier<? extends List<MonitoringDataSource>> dataSources,
            Supplier<? extends List<MonitoringWatchSource>> watchSources) {
        return console.updateAndGet(value -> value != null 
                ? value 
                : new MonitoringConsoleImpl(instance, receiver, runtime, dataSources, watchSources));
    }

    @Override
    public MonitoringConsole getCreatedConsole() {
        MonitoringConsole res = console.get();
        if (res == null) {
            throw new IllegalStateException("Console has not yet been created.");
        }
        return res;
    }

}
