package fish.payara.monitoring.adapt;

public interface MonitoringConsoleWatchConfig {

    boolean isDisabled(String name);

    void disable(String name);

    void enable(String name);

    void add(String name, String watchJson);

    void remove(String name);

    Iterable<String> list();
}
