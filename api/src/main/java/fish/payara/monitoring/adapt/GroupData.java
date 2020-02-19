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