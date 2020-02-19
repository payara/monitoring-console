package fish.payara.monitoring.adapt;

import java.util.Collection;

/**
 * The {@link GroupDataRepository} gives access to external data on metrics that is related to a certain metric group.
 * This data is implementation specific.
 * 
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public interface GroupDataRepository {

    /**
     * Selects {@link GroupData} by source and group.
     * 
     * @param source identifier for the type of data to select, the context
     * @param group the name of the metric group to select.
     * @return A list of all matching entries in the form of {@link GroupData}
     */
    Collection<GroupData> selectAll(String source, String group);
}
