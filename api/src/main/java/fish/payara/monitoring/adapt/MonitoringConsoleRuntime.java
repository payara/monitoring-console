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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A monitored application provides its implementation of low level processing required to run the
 * {@link MonitoringConsole} when creating it using the {@link MonitoringConsoleFactory}. 
 *
 * The provided implementation is used by the monitoring implementation to run its low level work of collecting metrics
 * and connecting instances to a network of multiple data senders and a central data receiver.
 * 
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public interface MonitoringConsoleRuntime {

    /**
     * Asynchronously runs the task as done by
     * {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}.
     * 
     * @see ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);

    /**
     * Sends a "package" of monitoring data from a sender (secondary instance) to the receiver (primary instance).
     * 
     * @param snapshot the package data
     * @return true, if the message has been send, else false
     */
    boolean send(byte[] snapshot);

    /**
     * Registers the receiver {@link Consumer} for messages when those are received.
     * 
     * @param receiver the callback to call with the received message when a message is received by the underlying
     *                 implementation.
     * @return true, if the callback was installed successful, else false
     */
    boolean receive(Consumer<byte[]> receiver);

    /**
     * @return the watch configuration abstraction of this runtime
     */
    MonitoringConsoleWatchConfig getWatchConfig();

    /**
     * @return the group data repository of this runtime
     */
    GroupDataRepository getGroupData();
}
