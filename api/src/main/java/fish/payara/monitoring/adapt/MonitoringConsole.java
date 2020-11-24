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

import java.util.NoSuchElementException;

/**
 * The {@link MonitoringConsole} is the control interface for the logical processing instance running on each node in a
 * cluster. A {@link MonitoringConsole} can be one of the senders of the cluster or the single receiver of the cluster.
 *
 * The interface allows the monitored application to control the console state.
 *
 * The instance is created by the {@link MonitoringConsoleFactory}. It is implemented by the monitoring console library.
 *
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public interface MonitoringConsole {

    /**
     * When enabled the console does its data collection and sends it data to the receiver instance (as sender) or
     * stores it (as receiver).
     *
     * @param enabled true to enable the console, false to disable it
     */
    void setEnabled(boolean enabled);

    /**
     * When enabled the console aggregates collected data to build a history over time. This requires extra memory. When
     * this feature is disabled and data is still collected (updated) the history build so far is cleared and the memory
     * freed.
     *
     * @since 1.2
     *
     * @param enabled true to start recording aggregate data, false to disable and remove aggregate data
     */
    void setHistoryEnabled(boolean enabled);

    /**
     * Simple service locator to access abstractions within the console.
     *
     * @param type an interface type for the service to load
     * @return the service instance
     * @throws NoSuchElementException in case no service of type is known
     */
    <T> T getService(Class<T> type) throws NoSuchElementException;
}
