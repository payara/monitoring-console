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
 * @since 1.0
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
     * Simple service locator to access abstractions within the console.
     * 
     * @param type an interface type for the service to load
     * @return the service instance
     * @throws NoSuchElementException in case no service of type is known
     */
    <T> T getService(Class<T> type) throws NoSuchElementException;
}
