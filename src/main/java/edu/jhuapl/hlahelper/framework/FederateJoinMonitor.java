/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

public interface FederateJoinMonitor {
    boolean allFederatesJoined();
    boolean timeoutReached();
}
