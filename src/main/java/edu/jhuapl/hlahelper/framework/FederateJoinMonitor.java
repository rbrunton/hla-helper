/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

/**
 * Interface for monitoring federate join status.
 */
public interface FederateJoinMonitor {
    boolean allFederatesJoined();
    boolean timeoutReached();
}
