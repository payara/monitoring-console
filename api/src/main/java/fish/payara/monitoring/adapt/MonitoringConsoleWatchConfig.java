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
