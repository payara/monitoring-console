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

/**
 * Abstraction to manipulate watch configuration.
 *
 * The implementation should store this configuration in a persistent manner.
 *
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public interface MonitoringConsoleWatchConfig {

    /**
     * @param name watch name
     * @return true if the watch of the given name is disabled, else false
     */
    boolean isDisabled(String name);

    /**
     * Disables the watch of the given name.
     *
     * @param name watch name
     */
    void disable(String name);

    /**
     * Enables the watch of the given name.
     *
     * @param name watch name
     */
    void enable(String name);

    /**
     * Adds a custom watch in JSON form to the configuration.
     *
     * @param name name of the watch
     * @param watchJson watch configuration
     */
    void add(String name, String watchJson);

    /**
     * Removes a custom watch from the configuration.
     *
     * @param name watch name
     */
    void remove(String name);

    /**
     * @return lists then name of all custom watches {@link #add(String, String)}ed before.
     */
    Iterable<String> list();
}
