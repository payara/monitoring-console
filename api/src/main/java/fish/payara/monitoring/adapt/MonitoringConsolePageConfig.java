package fish.payara.monitoring.adapt;

import java.util.NoSuchElementException;

/**
 * Abstraction to manipulate page configuration.
 *
 * The implementation should store this configuration in a persistent manner.
 *
 * @author Jan Bernitt
 * @since 1.1 (Payara 5.202)
 */
public interface MonitoringConsolePageConfig {

    /**
     * Access the JSON definition of the named page.
     *
     * @param name name of the page to access
     * @return JSON value of the named page
     * @throws NoSuchElementException
     */
    String getPage(String name);

    /**
     * Check if a page exists
     *
     * @param name page name to check
     * @return true in case the page exists, false otherwise
     */
    default boolean existsPage(String name) {
        try {
            return getPage(name) != null;
        } catch (NoSuchElementException ex) {
            return false;
        }
    }

    /**
     * Adds a page in JSON form to the configuration.
     *
     * @param name name of the page
     * @param pageJson page configuration
     */
    void putPage(String name, String pageJson);

    /**
     * Removes a page from the configuration.
     *
     * @param name watch name
     */
    default void removePage(String name) {
        putPage(name, null);
    }

    /**
     * @return lists then name of all existing pages.
     */
    Iterable<String> listPages();
}
