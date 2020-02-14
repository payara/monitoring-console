package fish.payara.monitoring.adapt;

import java.util.Collection;

public interface GroupDataRepository {

    Collection<GroupData> selectAll(String source, String group);
}
