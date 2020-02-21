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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

/**
 * Data structure for generic data related to a certain group of metrics.
 * 
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public class GroupData {

    private transient final GroupData parent;
    private final Map<String, Serializable> fields = new LinkedHashMap<>();
    private final Map<String, GroupData> children = new LinkedHashMap<>();

    public GroupData() {
        this(null);
    }

    private GroupData(GroupData parent) {
        this.parent = parent;
    }

    public Map<String, Serializable> getFields() {
        return fields;
    }

    public Map<String, GroupData> getChildren() {
        return children;
    }

    public GroupData parent() {
        return parent;
    }

    public GroupData addField(String name, Number value) {
        return addFieldInternal(name, value);
    }

    public GroupData addField(String name, String value) {
        return addFieldInternal(name, value);
    }

    public GroupData addField(String name, Boolean value) {
        return addFieldInternal(name, value);
    }

    public GroupData addField(String name, UUID value) {
        return addFieldInternal(name, value);
    }

    public GroupData addChild(String name) {
        GroupData child = new GroupData(this);
        children.put(name, child);
        return child;
    }

    private GroupData addFieldInternal(String name, Serializable value) {
        if (value != null) {
            fields.put(name, value);
        }
        return this;
    }

    public <T extends Serializable> T getField(String name, Class<T> type) {
        return type.cast(fields.get(name));
    }

    /**
     * @return A string JSON representation of this
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        toString(this, str);
        return str.toString();
    }

    private static void toString(GroupData obj, StringBuilder str) {
        str.append("{\n");
        int i = 0;
        for (Entry<String, Serializable> field : obj.fields.entrySet()) {
            if (i > 0) {
                str.append(", ");
            }
            str.append('"').append(field.getKey()).append('"').append(':');
            Serializable value = field.getValue();
            if (value == null) {
                str.append("null");
            } else if (value instanceof Boolean || value instanceof Number) {
                str.append(value.toString());
            } else { 
                str.append('"').append(value.toString()).append('"');
            }
            str.append('\n');
            i++;
        }
        i = 0;
        for (Entry<String, GroupData> child : obj.children.entrySet()) {
            if (i > 0) {
                str.append(", ");
            }
            str.append('"').append(child.getKey()).append('"').append(':');
            toString(child.getValue(), str);
            str.append('\n');
            i++;
        }
        str.append('}');
    }
}