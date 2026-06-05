package de.otto.config.core;

public interface Refreshable {
    
    public void refresh();

    public default void refreshInPlace() {
        refresh();
    }
}
